package com.midroid.masarifi.bills

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.midroid.masarifi.data.AppDatabase
import com.midroid.masarifi.data.AppDatabase.Companion.configure
import com.midroid.masarifi.data.AppSettings
import com.midroid.masarifi.data.Repository
import com.midroid.masarifi.data.SettingsStore
import com.midroid.masarifi.data.Txn
import com.midroid.masarifi.data.TxnType
import com.midroid.masarifi.data.backup.BackupCodec
import com.midroid.masarifi.data.backup.BackupService
import com.midroid.masarifi.data.sync.SyncFile
import com.midroid.masarifi.data.sync.SyncService
import com.midroid.masarifi.data.sync.SyncStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

/** Bills end-to-end through Repository + Room: pay / undo / SMS merge, backup, sync and Mi5G import. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class BillsDataTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val dbs = ArrayList<AppDatabase>()
    private fun newRepo(name: String): Repository {
        val db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).configure().allowMainThreadQueries().build().also { dbs += it }
        val s = SettingsStore(ctx, "bills_$name").apply { update { AppSettings() } }
        return Repository(db, s).also { runBlocking { it.ensureDefaults() } }
    }
    @After fun close() = dbs.forEach { it.close() }

    private suspend fun Repository.cat(key: String) = categoryDao.all().first { it.key == key }.id
    private suspend fun Repository.acc() = accountDao.all().first().id
    private suspend fun Repository.internet(due: String = "2026-10-20") = bills.save(
        Bill(name = "Omantel fibre", amount = 25_000, categoryId = cat("internet"), accountId = acc(), anchorDay = LocalDate.parse(due).dayOfMonth, nextDue = LocalDate.parse(due).toEpochDay()))

    @Test fun payRecordsExpenseAdvancesAndUndoRestores() = runBlocking {
        val repo = newRepo("pay")
        val id = repo.internet()
        val now = System.currentTimeMillis()
        val r = repo.bills.pay(id, 24_500, repo.acc(), repo.cat("internet"), now)!!
        val t = repo.txnDao.get(r.txnId!!)!!
        assertEquals(TxnType.EXPENSE, t.type); assertEquals(24_500L, t.amount); assertEquals("Omantel fibre", t.note); assertEquals(repo.cat("internet"), t.categoryId)
        assertEquals(LocalDate.parse("2026-11-20"), repo.billDao.get(id)!!.dueDate)
        val p = repo.billDao.payments().single()
        assertEquals(LocalDate.parse("2026-10-20").toEpochDay(), p.dueDate); assertEquals(t.uuid, p.txnUuid); assertTrue(p.ownTxn)
        assertEquals(listOf(t.id), repo.billDao.billTxnIds())
        repo.bills.undoPay(r)
        assertTrue(repo.txnDao.all().isEmpty())
        assertTrue(repo.billDao.payments().isEmpty())
        assertEquals(LocalDate.parse("2026-10-20"), repo.billDao.get(id)!!.dueDate)
    }

    @Test fun payMergesWithExistingSmsAndFlagsWhenRecordedNew() = runBlocking {
        val repo = newRepo("merge")
        val id = repo.internet()
        val now = System.currentTimeMillis()
        val smsTxn = Txn(type = TxnType.EXPENSE, amount = 25_000, accountId = repo.acc(), dateTime = now - 2 * 86_400_000L, isAuto = true, smsHash = "h1", merchant = "OMANTEL")
        val smsId = repo.txnDao.insert(smsTxn)
        assertEquals(listOf(smsId), repo.bills.smsMatches(25_000, now).map { it.id })
        // choose «دمج»: no new transaction
        val r = repo.bills.pay(id, 25_000, repo.acc(), null, now, linkTxnId = smsId)!!
        assertFalse(r.createdTxn)
        assertEquals(1, repo.txnDao.all().size)
        assertTrue(repo.bills.smsMatches(25_000, now).isEmpty()) // already used by a payment
        repo.bills.undoPay(r)
        assertEquals(1, repo.txnDao.all().size) // the SMS transaction is never deleted by undo
        // choose «تسجيل جديد»: created and flagged as a possible duplicate of the SMS one
        val r2 = repo.bills.pay(id, 25_000, repo.acc(), null, now)!!
        assertTrue(r2.flaggedDup)
        assertEquals("$smsId", repo.txnDao.get(r2.txnId!!)!!.possibleDupOf)
    }

    @Test fun deletingLatestPaymentRollsBackDueDate() = runBlocking {
        val repo = newRepo("del")
        val id = repo.internet()
        repo.bills.pay(id, 25_000, repo.acc(), null, 1_000)
        repo.bills.pay(id, 25_000, repo.acc(), null, 2_000)
        assertEquals(LocalDate.parse("2026-12-20"), repo.billDao.get(id)!!.dueDate)
        val latest = repo.billDao.paymentsFor(id).first()
        repo.bills.deletePayment(latest.id)
        assertEquals(LocalDate.parse("2026-11-20"), repo.billDao.get(id)!!.dueDate)
        assertEquals(1, repo.txnDao.all().size)
        // deleting a bill keeps recorded expenses
        repo.bills.delete(id)
        assertEquals(1, repo.txnDao.all().size); assertTrue(repo.billDao.payments().isEmpty())
    }

    @Test fun backupRoundTripAndOldBackupKeepsBills() = runBlocking {
        val repo = newRepo("bk")
        val id = repo.internet()
        repo.bills.pay(id, 25_000, repo.acc(), repo.cat("internet"), 5_000)
        val svc = BackupService(repo, SettingsStore(ctx, "bills_bk")) { "2.1.0" }
        val (json, _) = svc.encodeCurrent()
        val d = BackupCodec.decode(json)
        assertEquals(4, d.schemaVersion)
        assertEquals(1, d.bills!!.size); assertEquals(1, d.billPayments!!.size)
        // restore into another phone
        val repo2 = newRepo("bk2")
        BackupService(repo2, SettingsStore(ctx, "bills_bk2")) { "2.1.0" }.restore(d)
        val b2 = repo2.billDao.all().single()
        assertEquals("Omantel fibre", b2.name); assertEquals(repo2.cat("internet"), b2.categoryId)
        assertEquals(repo2.txnDao.all().single().uuid, repo2.billDao.payments().single().txnUuid)
        // a 2.0.0 backup (no "bills") restores and keeps this phone's bills
        val old = org.json.JSONObject(json).apply { remove("bills"); remove("billPayments") }.toString()
        val dOld = BackupCodec.decode(old)
        assertNull(dOld.bills)
        BackupService(repo2, SettingsStore(ctx, "bills_bk2")) { "2.1.0" }.restore(dOld)
        assertEquals("Omantel fibre", repo2.billDao.all().single().name)
        assertEquals(repo2.cat("internet"), repo2.billDao.all().single().categoryId)
    }

    @Test fun billsSyncBetweenDevices() = runBlocking {
        class Mem(var text: String? = null) : SyncFile { override fun read() = text; override fun write(text: String) { this.text = text } }
        val f = Mem()
        val a = newRepo("sa"); val b = newRepo("sb")
        val sa = SyncService(a, SettingsStore(ctx, "bills_sa"), SyncStore(ctx, "A", "billsync_a")) { "2.1.0" }
        val sb = SyncService(b, SettingsStore(ctx, "bills_sb"), SyncStore(ctx, "B", "billsync_b")) { "2.1.0" }
        val id = a.internet()
        a.bills.pay(id, 25_000, a.acc(), a.cat("internet"), System.currentTimeMillis() - 60_000)
        sa.sync(f, "manual"); sb.sync(f, "manual")
        val bb = b.billDao.all().single()
        assertEquals("Omantel fibre", bb.name); assertEquals(LocalDate.parse("2026-11-20"), bb.dueDate); assertEquals(b.cat("internet"), bb.categoryId)
        assertEquals(b.txnDao.all().single().uuid, b.billDao.payments().single().txnUuid)
        // delete on B propagates to A
        b.bills.delete(bb.id)
        sb.sync(f, "manual"); sa.sync(f, "manual")
        assertTrue(a.billDao.all().isEmpty()); assertTrue(a.billDao.payments().isEmpty())
        assertEquals(1, a.txnDao.all().size)
    }

    @Test fun importsMi5gBillsBackup() = runBlocking {
        val repo = newRepo("mi5g")
        val json = """{"app":"mi5g-bills","format":1,"schemaVersion":3,"exportedAt":1,
            "bills":[{"id":1,"name":"Rent","amountMilli":250000,"currency":"OMR","category":"rent","recurrenceType":"MONTHLY","recurrenceInterval":1,"anchorDay":31,"nextDueDate":"2026-10-31","reminderDaysBefore":5,"notes":"","active":true,"createdAt":100,"extraReminders":""},
                     {"id":2,"name":"Netflix","amountMilli":5000,"currency":"USD","category":"subscription","recurrenceType":"MONTHLY","recurrenceInterval":1,"anchorDay":2,"nextDueDate":"2026-11-02","reminderDaysBefore":3,"notes":"","active":true,"createdAt":101},
                     {"id":3,"name":"Water","amountMilli":8000,"currency":"OMR","category":"utilities","recurrenceType":"CUSTOM_MONTHS","recurrenceInterval":2,"anchorDay":10,"nextDueDate":"2026-11-10","reminderDaysBefore":3,"notes":"n","active":true,"createdAt":102}],
            "payments":[{"id":1,"billId":1,"amountMilli":250000,"currency":"OMR","dueDate":"2026-09-30","paidAt":5}]}"""
        assertEquals(2 to 1, repo.bills.importMi5g(json, "OMR"))
        val bills = repo.billDao.all().associateBy { it.name }
        assertEquals(repo.cat("rent"), bills.getValue("Rent").categoryId)
        assertEquals(31, bills.getValue("Rent").anchorDay)
        assertEquals(RecurrenceType.CUSTOM_MONTHS, bills.getValue("Water").type); assertEquals(repo.cat("other"), bills.getValue("Water").categoryId)
        val p = repo.billDao.payments().single()
        assertEquals(bills.getValue("Rent").id, p.billId); assertNull(p.txnUuid)
        assertTrue(repo.txnDao.all().isEmpty())
        assertEquals(0 to 3, repo.bills.importMi5g(json, "OMR")) // idempotent
        try { repo.bills.importMi5g("""{"app":"mi5g-bills","encrypted":true}""", "OMR"); error("expected") } catch (e: Mi5gImportException) { assertEquals("encrypted", e.code) }
    }
}
