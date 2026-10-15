package com.midroid.masarifi.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.midroid.masarifi.data.Account
import com.midroid.masarifi.data.AccountGroup
import com.midroid.masarifi.data.AppDatabase
import com.midroid.masarifi.data.AppSettings
import com.midroid.masarifi.data.Budget
import com.midroid.masarifi.data.Category
import com.midroid.masarifi.data.MerchantRule
import com.midroid.masarifi.data.Recurring
import com.midroid.masarifi.data.Repeat
import com.midroid.masarifi.data.Repository
import com.midroid.masarifi.data.SettingsStore
import com.midroid.masarifi.data.SmsLog
import com.midroid.masarifi.data.Txn
import com.midroid.masarifi.data.TxnType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class BackupRoundTripTest {
    private val sample = BackupData(
        createdAt = 1_760_000_000_000, appVersion = "1.1.0",
        settings = AppSettings(currency = "OMR", language = "ar", theme = "light", smsEnabled = true, extraSenders = "BankX, \"Q\"", weekStartsSaturday = true),
        accounts = listOf(
            Account(1, "نقد", AccountGroup.CASH, 5_000), Account(2, "Bank, \"Main\"", AccountGroup.BANK, 0, "1234", includeInTotal = false, sortOrder = 1),
        ),
        categories = listOf(Category(1, TxnType.EXPENSE, "طعام", "Food", "🍜", "food", 1), Category(2, TxnType.INCOME, "راتب", "Salary", "💰", null, 2)),
        transactions = listOf(
            Txn(10, TxnType.EXPENSE, 1_500, 1, null, 1, "كرك\nوشاي", "line1\nline2", 1_759_000_000_123, isAuto = true, smsHash = "h1", merchant = "Café", recurringId = 3, createdAt = 5),
            Txn(11, TxnType.TRANSFER, 50_000, 2, 1, null, "", "", 1_759_100_000_000, createdAt = 6),
            Txn(12, TxnType.INCOME, 800_000, 2, null, 2, "", "", 1_759_200_000_000, smsHash = "mm:abc", createdAt = 7, smsLinked = true, possibleDupOf = "10,11"),
        ),
        recurring = listOf(Recurring(3, TxnType.EXPENSE, 1_500, 1, null, 1, "كرك", Repeat.DAILY, 1_760_100_000_000, active = false)),
        budgets = listOf(Budget(0, 300_000), Budget(1, 50_000)),
        merchantRules = listOf(MerchantRule("cafe", 1, 99)),
        smsLog = listOf(SmsLog("h1", "BankMuscat", 1_759_000_000_000, SmsLog.IMPORTED, 10), SmsLog("h2", "X", 1, SmsLog.IGNORED, null)),
        smsLinks = listOf(com.midroid.masarifi.data.SmsLink(1, 12, "h7", "BankMuscat", TxnType.INCOME, 800_000, 2, 2, "n", "body", 1_759_200_000_500, "ACME", "0099", null, 9)),
        dupDismissed = listOf(com.midroid.masarifi.data.DupDismissed("10-11", 3)),
    )

    @Test fun decodesV1BackupFrom110() {
        val v1 = BackupCodec.encode(sample).replace("\"schemaVersion\":4", "\"schemaVersion\":1")
        val o = org.json.JSONObject(v1)
        o.remove("smsLinks"); o.remove("dupDismissed")
        val txs = o.getJSONArray("transactions")
        for (i in 0 until txs.length()) { txs.getJSONObject(i).remove("smsLinked"); txs.getJSONObject(i).remove("possibleDupOf") }
        val d = BackupCodec.decode(o.toString())
        assertEquals(1, d.schemaVersion)
        assertEquals(3, d.transactions.size)
        assertEquals(false, d.transactions[2].smsLinked)
        assertEquals(null, d.transactions[2].possibleDupOf)
        assertEquals(emptyList<Any>(), d.smsLinks)
    }

    @Test fun encodeDecodeRoundTrip() {
        val json = BackupCodec.encode(sample)
        val back = BackupCodec.decode(json)
        assertEquals(sample, back)
        assertEquals(json, BackupCodec.encode(back))
        assertEquals(4, back.schemaVersion)
    }

    @Test fun rejectsNewerSchemaAndGarbage() {
        val newer = BackupCodec.encode(sample).replace("\"schemaVersion\":4", "\"schemaVersion\":99")
        assertEquals("newer", assertThrows(BackupFormatException::class.java) { BackupCodec.decode(newer) }.code)
        assertEquals("not_json", assertThrows(BackupFormatException::class.java) { BackupCodec.decode("PK\u0003\u0004") }.code)
        assertEquals("not_backup", assertThrows(BackupFormatException::class.java) { BackupCodec.decode("{\"a\":1}") }.code)
    }

    @Test fun pruningKeepsNewestTen() {
        val names = (1..13).map { "masarifi-backup-202601%02d-0930.json".format(it) } + listOf("notes.txt", "masarifi-backup-x.json")
        val del = BackupService.selectForPruning(names.shuffled(), 10)
        assertEquals(listOf("masarifi-backup-20260103-0930.json", "masarifi-backup-20260102-0930.json", "masarifi-backup-20260101-0930.json"), del)
        assertEquals("masarifi-backup-20261006-213000.json", BackupService.fileName(java.time.LocalDateTime.of(2026, 10, 6, 21, 30)))
    }

    @Test fun snapshotRestoreReplacesAllDataInDb() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val settings = SettingsStore(ctx)
            val repo = Repository(db, settings)
            repo.ensureDefaults()
            repo.txnDao.insert(Txn(type = TxnType.EXPENSE, amount = 1, accountId = 1, dateTime = 1))
            val svc = BackupService(repo, settings) { "test" }
            svc.restore(BackupCodec.decode(BackupCodec.encode(sample)))
            val snap = svc.snapshot(now = sample.createdAt)
            assertEquals(sample.accounts, snap.accounts.sortedBy { it.id })
            assertEquals(sample.categories, snap.categories.sortedBy { it.id })
            assertEquals(sample.transactions, snap.transactions.sortedBy { it.id })
            assertEquals(sample.recurring, snap.recurring)
            assertEquals(sample.budgets, snap.budgets.sortedBy { it.categoryId })
            assertEquals(sample.merchantRules, snap.merchantRules)
            assertEquals(sample.smsLog, snap.smsLog.sortedBy { it.hash })
            assertEquals(sample.smsLinks, snap.smsLinks)
            assertEquals(sample.dupDismissed, snap.dupDismissed)
            assertEquals(sample.settings, settings.current)
        } finally {
            db.close()
            SettingsStore(ctx).update { AppSettings() }
        }
    }
}
