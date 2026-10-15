package com.midroid.masarifi.data.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.midroid.masarifi.data.Account
import com.midroid.masarifi.data.AccountGroup
import com.midroid.masarifi.data.AppDatabase
import com.midroid.masarifi.data.AppDatabase.Companion.configure
import com.midroid.masarifi.data.Defaults
import com.midroid.masarifi.data.Repeat
import com.midroid.masarifi.data.Repository
import com.midroid.masarifi.data.SettingsStore
import com.midroid.masarifi.data.SmsLink
import com.midroid.masarifi.data.Txn
import com.midroid.masarifi.data.TxnType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Two simulated devices (separate DBs + prefs) syncing through one in-memory "Drive file". Synthetic data only. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SyncServiceTest {
    class MemFile(var text: String? = null) : SyncFile {
        var writes = 0
        override fun read() = text
        override fun write(text: String) { this.text = text; writes++ }
    }

    inner class Dev(val name: String) {
        private val ctx = ApplicationProvider.getApplicationContext<Context>()
        val db: AppDatabase = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).configure().allowMainThreadQueries().build()
        val settings = SettingsStore(ctx, "settings_$name")
        val repo = Repository(db, settings)
        val store = SyncStore(ctx, name, "sync_$name")
        val svc = SyncService(repo, settings, store) { "1.3.0" }
        fun sync(f: SyncFile, accept: Boolean = false) = runBlocking { svc.sync(f, "manual", accept) }
        fun records() = runBlocking { svc.exportLocal().records }
        fun txns() = runBlocking { repo.txnDao.all() }
        fun cash() = runBlocking { repo.accountDao.all().first { it.uuid == Defaults.CASH_ACCOUNT_UUID }.id }
        fun cat(key: String) = runBlocking { repo.categoryDao.all().first { it.key == key }.id }
    }

    private val devs = ArrayList<Dev>()
    private fun dev(n: String) = Dev(n).also { devs += it }
    @After fun close() = devs.forEach { it.db.close() }

    private val now = System.currentTimeMillis()
    private val hourAgo = now - 3_600_000
    private val halfHourAgo = now - 1_800_000

    @Test fun triggersKeepUpdatedAtAndTombstones() = runBlocking {
        val a = dev("a"); a.repo.ensureDefaults()
        val id = a.repo.txnDao.insert(Txn(type = TxnType.EXPENSE, amount = 1_000, accountId = a.cash(), dateTime = hourAgo, updatedAt = hourAgo))
        val t = a.repo.txnDao.get(id)!!
        a.repo.txnDao.update(t.copy(note = "edited"))                 // writer did not touch updatedAt
        val edited = a.repo.txnDao.get(id)!!
        assertTrue(edited.updatedAt >= now)                            // → bumped to "now" by the trigger
        a.repo.deleteTxn(edited)
        val tomb = a.db.syncDao().tombstones().single()
        assertEquals(t.uuid, tomb.uuid); assertEquals("txn", tomb.entity)
        a.repo.restoreTxns(listOf(edited))                             // undo
        assertTrue(a.db.syncDao().tombstones().isEmpty())
        assertTrue(a.repo.txnDao.get(id)!!.updatedAt >= tomb.deletedAt)
        a.repo.setBudget(0, 5_000); a.repo.setBudget(0, 0)
        assertEquals("b:overall", a.db.syncDao().tombstones().single().uuid)
    }

    @Test fun twoDevicesMergeDivergentChangesAndConverge() = runBlocking {
        val a = dev("a"); val b = dev("b"); val file = MemFile()
        // ---- device A has data
        a.repo.ensureDefaults()
        val bank = a.repo.saveAccount(Account(name = "Bank Muscat ••0099", groupType = AccountGroup.BANK, cardLast4 = "0099", updatedAt = hourAgo))
        fun ta(note: String, amount: Long, acc: Long = a.cash(), cat: String = "food") =
            Txn(type = TxnType.EXPENSE, amount = amount, accountId = acc, categoryId = a.cat(cat), note = note, dateTime = hourAgo, updatedAt = hourAgo, createdAt = hourAgo)
        val t1 = a.repo.txnDao.insert(ta("قهوة", 1_200))
        a.repo.txnDao.insert(ta("غداء", 3_500, bank))
        val t3 = a.repo.txnDao.insert(ta("بنزين", 7_000, bank, "fuel"))
        a.repo.dupDao.insertLink(SmsLink(txnId = t1, smsHash = "sms-x", type = TxnType.EXPENSE, amount = 1_200, accountId = a.cash(), dateTime = hourAgo, updatedAt = hourAgo))
        a.repo.setBudget(0, 300_000)
        a.repo.learnMerchant("LULU HYPERMARKET", a.cat("grocery"))
        val r1 = a.sync(file)
        assertTrue(r1.wrote); assertTrue(r1.stats.pushed > 0)

        // ---- device B (fresh install, own defaults) joins
        b.repo.ensureDefaults()
        val r2 = b.sync(file)
        assertEquals(3, b.txns().size)
        assertEquals(Defaults.categories.size, b.repo.categoryDao.count())      // defaults merged, not duplicated
        assertEquals(2, b.repo.accountDao.count())                              // one cash + the bank
        assertEquals(300_000L, b.repo.budgetDao.all().single().amount)
        assertEquals(b.cat("grocery"), b.repo.merchantRuleDao.get(com.midroid.masarifi.sms.MerchantCategorizer.merchantKey("LULU HYPERMARKET"))!!.categoryId)
        val bt1 = b.txns().first { it.note == "قهوة" }
        assertEquals(bt1.id, b.repo.dupDao.linksFor(bt1.id).single().txnId)
        assertEquals(0, r2.stats.conflicts)

        // ---- divergent edits: A adds t4 + deletes t3, B edits t1
        a.repo.txnDao.insert(ta("جديد", 4_000).copy(updatedAt = halfHourAgo))
        a.repo.deleteTxn(a.repo.txnDao.get(t3)!!)
        b.repo.saveTxn(bt1.copy(note = "قهوة كبيرة", amount = 1_500, updatedAt = halfHourAgo))

        val ra = a.sync(file)            // pushes add + delete
        assertEquals(2, ra.stats.pushed)
        val rb = b.sync(file)            // pulls add + delete, pushes edit
        assertEquals(2, rb.stats.pulled); assertEquals(1, rb.stats.pushed)
        val ra2 = a.sync(file)           // pulls edit
        assertEquals(1, ra2.stats.pulled); assertEquals(0, ra2.stats.pushed)
        val rb2 = b.sync(file)           // nothing left
        assertEquals(0, rb2.stats.pulled); assertEquals(0, rb2.stats.pushed); assertTrue(!rb2.wrote)

        // ---- both devices hold exactly the same entities (uuids, versions, fields, tombstones)
        assertEquals(a.records(), b.records())
        for (d in listOf(a, b)) {
            val notes = d.txns().map { it.note }.sorted()
            assertEquals(listOf("جديد", "غداء", "قهوة كبيرة"), notes)
            assertEquals(1_500L, d.txns().first { it.note == "قهوة كبيرة" }.amount)
        }
        assertEquals(a.store.current.fileId, b.store.current.fileId)
        assertEquals(setOf("b"), a.store.current.devices.values.map { it.name }.toSet())
        assertEquals(3, a.store.current.log.size)
    }

    @Test fun sameSmsImportedOnBothPhonesKeepsOne() = runBlocking {
        val a = dev("a"); val b = dev("b"); val file = MemFile()
        a.repo.ensureDefaults(); b.repo.ensureDefaults()
        a.sync(file); b.sync(file)
        val body = "Dear Customer, Purchase of OMR 4.300 was made using your Debit Card XXXX0099 at LULU HYPERMARKET BAUSHER on 06/10/2026 14:40. Avl Bal OMR 812.450"
        val ia = a.repo.importSms("BankMuscat", body, now)!!
        val ib = b.repo.importSms("BankMuscat", body, now)!!
        assertNotEquals(ia.txn.uuid, ib.txn.uuid)
        a.sync(file); val rb = b.sync(file); a.sync(file)
        assertEquals(1, rb.stats.duplicatesDropped)
        for (d in listOf(a, b)) {
            assertEquals(1, d.txns().count { it.smsHash == ia.txn.smsHash })
            assertEquals(1, d.repo.accountDao.all().count { it.cardLast4 == "0099" })   // auto-created bank account merged by name
        }
        assertEquals(a.records(), b.records())
    }

    @Test fun settingsAndRepeatingEntriesSyncWithoutDuplicates() = runBlocking {
        val a = dev("a"); val b = dev("b"); val file = MemFile()
        a.repo.ensureDefaults(); b.repo.ensureDefaults()
        val day = 86_400_000L
        a.repo.saveTxn(Txn(type = TxnType.INCOME, amount = 850_000, accountId = a.cash(), categoryId = a.cat("salary"), note = "راتب", dateTime = now - 10 * day), Repeat.MONTHLY)
        a.sync(file); b.sync(file)
        b.settings.update { it.copy(currency = "AED", dupWindowHours = 72) }
        // both phones materialize the next salary on their own before syncing again
        a.repo.processRecurring(now + 25 * day); b.repo.processRecurring(now + 25 * day)
        a.sync(file); b.sync(file); a.sync(file)
        assertEquals("AED", a.settings.current.currency)
        assertEquals(72, a.settings.current.dupWindowHours)
        for (d in listOf(a, b)) assertEquals(2, d.txns().count { it.note == "راتب" })
        assertEquals(a.records(), b.records())
    }

    @Test fun neverOverwritesAForeignFile_andDetectsADifferentSyncFile() = runBlocking {
        val a = dev("a"); a.repo.ensureDefaults()
        val backup = MemFile("""{"format":"masarifi-backup","schemaVersion":3}""")
        assertThrows(SyncFormatException::class.java) { a.sync(backup) }
        assertEquals("""{"format":"masarifi-backup","schemaVersion":3}""", backup.text)
        assertEquals(0, backup.writes)
        assertEquals(false, a.store.current.lastOk)
        assertEquals("not_sync", a.store.current.log.first().error)

        val f1 = MemFile(); a.sync(f1)
        val b = dev("b"); b.repo.ensureDefaults(); val f2 = MemFile(); b.sync(f2)  // B created its own, different file
        assertThrows(SyncMismatchException::class.java) { a.sync(f2) }
        a.sync(f2, accept = true)
        assertEquals(b.store.current.fileId, a.store.current.fileId)
        assertNull(null)
    }
}
