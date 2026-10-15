package com.midroid.masarifi.data.catfix

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.midroid.masarifi.data.AppDatabase
import com.midroid.masarifi.data.AppDatabase.Companion.configure
import com.midroid.masarifi.data.Category
import com.midroid.masarifi.data.MerchantRule
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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Phone A → backup / sync → phone B with its own seeded defaults: every category reference must resolve. Synthetic data. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class CrossDeviceCategoryTest {
    class MemFile(var text: String? = null) : SyncFile { override fun read() = text; override fun write(text: String) { this.text = text } }
    inner class Dev(n: String) {
        val name = n + (seq++)
        private val ctx = ApplicationProvider.getApplicationContext<Context>()
        val db: AppDatabase = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).configure().allowMainThreadQueries().build()
        val settings = SettingsStore(ctx, "settings_x$name")
        val repo = Repository(db, settings)
        val backup = BackupService(repo, settings) { "1.7.0" }
        val sync = SyncService(repo, settings, SyncStore(ctx, name, "sync_x$name")) { "1.7.0" }
        fun cash() = runBlocking { repo.accountDao.all().first().id }
        fun named(): List<Pair<Long, String?>> = runBlocking {
            val cats = repo.categoryDao.all().associateBy { it.id }
            repo.txnDao.all().sortedBy { it.dateTime }.map { it.amount to it.categoryId?.let { id -> cats[id]?.nameEn } }
        }
    }
    private var seq = 0
    private val devs = ArrayList<Dev>()
    private fun dev(n: String) = Dev(n).also { devs += it }
    @After fun close() = devs.forEach { it.db.close() }

    /** Phone A: defaults + 2 custom categories, SMS + manual transactions, a merchant rule. */
    private fun seedA(a: Dev) = runBlocking {
        a.repo.ensureDefaults()
        val pharmacy = a.repo.categoryDao.insert(Category(type = TxnType.EXPENSE, nameAr = "صيدلية", nameEn = "Pharmacy", emoji = "💊"))
        val gifts = a.repo.categoryDao.insert(Category(type = TxnType.EXPENSE, nameAr = "هدايا", nameEn = "Gifts", emoji = "🎁"))
        val food = a.repo.categoryDao.all().first { it.key == "food" }.id
        val acc = a.cash()
        a.repo.txnDao.insert(Txn(type = TxnType.EXPENSE, amount = 3_100, accountId = acc, categoryId = pharmacy, dateTime = 1_000_000, isAuto = true, smsHash = "s1", merchant = "SAMPLE PHARMACY"))
        a.repo.txnDao.insert(Txn(type = TxnType.EXPENSE, amount = 1_200, accountId = acc, categoryId = food, dateTime = 2_000_000, isAuto = true, smsHash = "s2", merchant = "SAMPLE BAKERY"))
        a.repo.txnDao.insert(Txn(type = TxnType.EXPENSE, amount = 9_000, accountId = acc, categoryId = gifts, dateTime = 3_000_000, isAuto = true, smsHash = "s3", merchant = "SAMPLE GIFT"))
        a.repo.merchantRuleDao.upsert(MerchantRule("sample pharmacy", pharmacy, 5))
    }
    private val expected = listOf(3_100L to "Pharmacy", 1_200L to "Food", 9_000L to "Gifts")

    @Test fun backupRestoreIntoFreshDeviceKeepsCategories() = runBlocking {
        val a = dev("a"); seedA(a)
        val json = BackupCodec.encode(a.backup.snapshot())
        val b = dev("b"); b.repo.ensureDefaults()
        b.repo.categoryDao.insert(Category(type = TxnType.EXPENSE, nameAr = "خاص", nameEn = "B only", emoji = "📦"))
        b.backup.restore(BackupCodec.decode(json))
        assertEquals(expected, b.named())
    }

    @Test fun syncIntoFreshDeviceKeepsCategories() {
        val a = dev("a"); seedA(a); val f = MemFile()
        runBlocking { a.sync.sync(f, "manual") }
        val b = dev("b"); runBlocking { b.repo.ensureDefaults(); b.sync.sync(f, "manual") }
        assertEquals(expected, b.named())
    }

    @Test fun restoreThenSyncKeepsCategories() {
        val a = dev("a"); seedA(a); val f = MemFile()
        runBlocking { a.sync.sync(f, "manual") }
        val json = runBlocking { BackupCodec.encode(a.backup.snapshot()) }
        val b = dev("b"); runBlocking { b.repo.ensureDefaults(); b.sync.sync(f, "manual"); b.backup.restore(BackupCodec.decode(json)); b.sync.sync(f, "manual") }
        assertEquals(expected, b.named())
        runBlocking { a.sync.sync(f, "manual") }
        assertEquals(expected, a.named())
    }

    @Test fun oldSchemaBackupRestoreThenSync() {
        val a = dev("a"); seedA(a); val f = MemFile()
        runBlocking { a.sync.sync(f, "manual") }
        val o = org.json.JSONObject(runBlocking { BackupCodec.encode(a.backup.snapshot()) }).put("schemaVersion", 2)
        for (k in listOf("categories", "accounts", "transactions")) { val arr = o.getJSONArray(k); for (i in 0 until arr.length()) arr.getJSONObject(i).remove("uuid") }
        val b = dev("b"); runBlocking { b.repo.ensureDefaults(); b.sync.sync(f, "manual"); b.backup.restore(BackupCodec.decode(o.toString())); b.sync.sync(f, "manual") }
        assertEquals(expected, b.named())
        runBlocking { a.sync.sync(f, "manual") }
        assertEquals(expected, a.named())
    }

    private val TH = "Thawani Pay"
    private val SMS1 = "Dear Customer, Your Mojab Card 4000XXXXXXXX5501 used successfully for 1.500 OMR at SAMPLE PHARMACY, OMN on 15/01/2025 . Your available balance is 48.500 OMR."
    private val SMS2 = "Dear Customer, Your Mojab Card 4000XXXXXXXX5501 used successfully for .250 OMR at DEMO BAKERY  , OMN on 15/01/2025 . Your available balance is 48.250 OMR."
    private val t0 = java.time.LocalDateTime.of(2025, 1, 15, 10, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** Phone A recategorized its SMS rows by hand; phone B scanned the same SMS from its inbox before the first sync. */
    private fun smsBothPhones(oldUuidsOnA: Boolean): Pair<Dev, Dev> {
        val a = dev("a"); val b = dev("b"); val f = MemFile()
        runBlocking {
            a.repo.ensureDefaults()
            val pharmacy = a.repo.categoryDao.insert(Category(type = TxnType.EXPENSE, nameAr = "صيدلية", nameEn = "Pharmacy", emoji = "💊"))
            a.repo.importSms(TH, SMS1, t0)!!; a.repo.importSms(TH, SMS2, t0 + 60_000)!!
            val food = a.repo.categoryDao.all().first { it.key == "food" }.id
            a.repo.txnDao.all().forEach { t -> a.repo.txnDao.update(t.copy(categoryId = if (t.amount == 1_500L) pharmacy else food)) }
            if (oldUuidsOnA) {
                val o = org.json.JSONObject(BackupCodec.encode(a.backup.snapshot())).put("schemaVersion", 2)
                for (k in listOf("categories", "accounts", "transactions")) { val arr = o.getJSONArray(k); for (i in 0 until arr.length()) arr.getJSONObject(i).remove("uuid") }
                a.backup.restore(BackupCodec.decode(o.toString()))
            }
            a.sync.sync(f, "manual")
            b.repo.ensureDefaults()
            b.repo.importSms(TH, SMS1, t0)!!; b.repo.importSms(TH, SMS2, t0 + 60_000)!!
            b.sync.sync(f, "manual"); a.sync.sync(f, "manual")
        }
        return a to b
    }
    private val smsExpected = listOf(1_500L to "Pharmacy", 250L to "Food")

    // uuids are random: repeat so both orders of the two copies' ids are exercised
    @Test fun inboxScanOnBThenSync_repeated() = repeat(6) { devs.forEach { it.db.close() }; devs.clear(); val (a, b) = smsBothPhones(false); assertEquals(smsExpected, b.named()); assertEquals(smsExpected, a.named()) }
    @Test fun inboxScanOnBThenSync() { val (a, b) = smsBothPhones(false); assertEquals(smsExpected, b.named()); assertEquals(smsExpected, a.named()) }
    @Test fun inboxScanOnBThenSync_oldUuidsOnA() { val (a, b) = smsBothPhones(true); assertEquals(smsExpected, b.named()); assertEquals(smsExpected, a.named()) }

    /** 1.7.0 already damaged phone B: categories gone; the repair re-links from A's backup file. */
    @Test fun relinkFromBackupOnDamagedDevice() = runBlocking {
        val a = dev("a"); seedA(a)
        val backup = BackupCodec.decode(BackupCodec.encode(a.backup.snapshot()))
        val b = dev("b"); b.backup.restore(backup)
        // simulate the damage: custom categories removed, references cleared (one row also lost its uuid link)
        b.db.openHelper.writableDatabase.execSQL("UPDATE transactions SET categoryId = NULL")
        b.db.openHelper.writableDatabase.execSQL("DELETE FROM categories WHERE `key` IS NULL")
        b.db.openHelper.writableDatabase.execSQL("UPDATE transactions SET uuid = 'other-id', smsHash = NULL WHERE amount = 9000")
        b.db.openHelper.writableDatabase.execSQL("DELETE FROM merchant_rules")
        assertEquals(listOf(3_100L to null, 1_200L to null, 9_000L to null), b.named())
        val r = CategoryRelink(b.repo)
        val plan = r.plan(backup)
        assertEquals(3, plan.uncategorized); assertEquals(3, plan.fromBackup.size); assertEquals(2, plan.newCategories.size); assertEquals(0, plan.remaining)
        assertEquals(3, r.apply(plan))
        assertEquals(expected, b.named())
        assertEquals(1, b.repo.merchantRuleDao.all().size)
        assertEquals(0, r.plan(backup).uncategorized) // idempotent
    }

    @Test fun relinkWithoutFileUsesMerchantRulesOnlyAndNeverTouchesCategorized() = runBlocking {
        val a = dev("a"); seedA(a)
        a.db.openHelper.writableDatabase.execSQL("UPDATE transactions SET categoryId = NULL WHERE amount IN (3100, 9000)")
        val plan = CategoryRelink(a.repo).plan(null)
        assertEquals(2, plan.uncategorized); assertEquals(1, plan.fromRules.size); assertEquals(1, plan.remaining)
        CategoryRelink(a.repo).apply(plan)
        assertEquals(listOf(3_100L to "Pharmacy", 1_200L to "Food", 9_000L to null), a.named())
    }

    @Test fun oldBackupGetsStableIdsAndCardMappingsTravel() = runBlocking {
        val a = dev("a"); seedA(a)
        val acc = a.repo.accountDao.insert(com.midroid.masarifi.data.Account(name = "Bank SAMPLE", groupType = 1))
        a.repo.cardMappingDao.insert(com.midroid.masarifi.data.CardMapping(bankId = "bm", last4 = "0099", accountId = acc))
        val json = BackupCodec.encode(a.backup.snapshot())
        val d = BackupCodec.decode(json)
        assertEquals(4, d.schemaVersion); assertEquals(listOf("0099"), d.cardMappings!!.map { it.last4 })
        val b = dev("b"); b.repo.ensureDefaults(); b.backup.restore(d)
        assertEquals(acc, b.repo.cardMappingDao.find("0099", "bm")!!.accountId)
        val old = org.json.JSONObject(json).put("schemaVersion", 2)
        for (k in listOf("categories", "accounts")) { val arr = old.getJSONArray(k); for (i in 0 until arr.length()) arr.getJSONObject(i).remove("uuid") }
        val od = BackupCodec.decode(old.toString())
        assertEquals("cat-food", od.categories.first { it.key == "food" }.uuid)
        assertEquals(com.midroid.masarifi.data.Defaults.CASH_ACCOUNT_UUID, od.accounts.first { it.groupType == 0 }.uuid)
        assertEquals(od.categories.first { it.nameEn == "Pharmacy" }.uuid, BackupCodec.decode(old.toString()).categories.first { it.nameEn == "Pharmacy" }.uuid)
    }

    /** A peer that still has the category under another id: merged by name, references follow. */
    @Test fun sameCustomCategoryOnBothPhonesIsMerged() {
        val a = dev("a"); seedA(a); val f = MemFile()
        val b = dev("b")
        runBlocking {
            b.repo.ensureDefaults()
            val ph = b.repo.categoryDao.insert(Category(type = TxnType.EXPENSE, nameAr = "صيدلية", nameEn = "Pharmacy", emoji = "💊"))
            b.repo.txnDao.insert(Txn(type = TxnType.EXPENSE, amount = 700, accountId = b.cash(), categoryId = ph, dateTime = 4_000_000))
            a.sync.sync(f, "manual"); b.sync.sync(f, "manual"); a.sync.sync(f, "manual")
        }
        val exp = expected + (700L to "Pharmacy")
        assertEquals(exp, a.named()); assertEquals(exp, b.named())
        assertEquals(1, runBlocking { a.repo.categoryDao.all().count { it.nameEn == "Pharmacy" } })
        assertEquals(1, runBlocking { b.repo.categoryDao.all().count { it.nameEn == "Pharmacy" } })
    }
}
