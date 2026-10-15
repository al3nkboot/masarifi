package com.midroid.masarifi.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.midroid.masarifi.data.repair.RepairKind
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.time.ZoneId

/** 1.6.0 import fixes + repair tool, end-to-end through Repository + Room. Generic sample values only. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SmsReconcileImportTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: Repository
    private val zone = ZoneId.systemDefault()
    private fun at(d: Int, h: Int, m: Int = 0) = LocalDateTime.of(2025, 1, d, h, m).atZone(zone).toInstant().toEpochMilli()

    private val BD = "BankDhofar"; private val BM = "bank muscat"; private val TH = "Thawani Pay"
    private fun bdAccount(amount: String, bal: String) = "حسابكم رقم 0200XXX0007001 قد خصم منه مبلغ OMR $amount بتاريخ 2025-01-12. رصيدكم الحالي هو OMR $bal"
    private fun bdCardTopup(amount: String) = "تم خصم مبلغ OMR $amount من بطاقتك رقم XXXX6610 لدى ThawaniPay Topup بتاريخ 2025-01-12"
    private fun bmSent(amount: String, ref: String) = "Dear Customer, You have sent OMR $amount to SAMPLE PERSON from your a/c 0100XXXXXXXX0099 on 16/01/2025 14:30:00 using Mobile Payment services. Txn Id BMCT01000000$ref."
    private fun thCredit(amount: String) = "Dear Customer, Your Mojab Card 4000XXXXXXXX5501 has been credited with $amount OMR on 16/01/2025."

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val settings = SettingsStore(ctx, "reconcile-test").apply { update { AppSettings() } }
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = Repository(db, settings)
        runBlocking { repo.ensureDefaults() }
    }

    @After fun tearDown() = db.close()

    private suspend fun all() = repo.txnDao.all()

    // ---- 1. Bank Dhofar double SMS ----

    @Test fun dhofarDouble_cardThenAccount_recordedOnce_asTopupTransfer() = runBlocking {
        val first = repo.importSms(BD, bdCardTopup("10.000"), at(12, 9, 0))!!
        assertEquals(TxnType.TRANSFER, first.txn.type)
        val second = repo.importSms(BD, bdAccount("10.000", "90.000"), at(12, 9, 2))!!
        assertTrue(second.linked)
        assertEquals(first.txn.id, second.txn.id)
        assertEquals(1, all().size)
        assertEquals(1, repo.dupDao.linksFor(first.txn.id).size)          // second SMS text kept, attached
    }

    @Test fun dhofarDouble_accountThenCard_becomesOneTransfer() = runBlocking {
        val first = repo.importSms(BD, bdAccount("65.000", "35.000"), at(12, 10, 0))!!
        assertEquals(TxnType.EXPENSE, first.txn.type)
        val second = repo.importSms(BD, bdCardTopup("65.000"), at(12, 10, 1))!!
        assertTrue(second.linked)
        val t = all().single()
        assertEquals(TxnType.TRANSFER, t.type)                            // upgraded to the top-up transfer
        assertNotNull(t.toAccountId)
    }

    @Test fun sameAmountFarApart_notMerged() = runBlocking {
        repo.importSms(BD, bdAccount("10.000", "90.000"), at(12, 9))!!
        repo.importSms(BD, bdAccount("10.000", "80.000"), at(12, 11))!!
        assertEquals(2, all().size)
        assertTrue(all().all { it.possibleDupOf == null })
    }

    @Test fun sameStyleWithinMinutes_flaggedNotMerged() = runBlocking {
        val a = repo.importSms(BD, bdAccount("40.000", "60.000"), at(12, 9))!!
        val b = repo.importSms(BD, bdAccount("40.000", "20.000"), at(12, 9, 3))!!
        assertEquals(2, all().size)
        assertEquals(a.txn.id.toString(), b.txn.possibleDupOf)              // shown in «possible duplicates»
    }

    // ---- 2. own transfers Bank Muscat → Mojab (Thawani) ----

    @Test fun ownTransfer_debitThenCredit_oneTransfer() = runBlocking {
        val d = repo.importSms(BM, bmSent("100.000", "0501"), at(16, 14, 31))!!
        assertEquals(TxnType.EXPENSE, d.txn.type)
        val c = repo.importSms(TH, thCredit("100.000"), at(16, 14, 40))!!
        assertTrue(c.linked)
        val t = all().single()
        assertEquals(TxnType.TRANSFER, t.type)
        assertEquals(d.txn.accountId, t.accountId)
        assertTrue(repo.accountDao.all().first { it.id == t.toAccountId }.name.contains("5501"))
    }

    @Test fun ownTransfer_creditThenDebit_oneTransfer() = runBlocking {
        val c = repo.importSms(TH, thCredit("57.000"), at(16, 14, 20))!!
        assertEquals(TxnType.INCOME, c.txn.type)
        val d = repo.importSms(BM, bmSent("57.000", "0502"), at(16, 14, 31))!!
        assertTrue(d.linked)
        val t = all().single()
        assertEquals(TxnType.TRANSFER, t.type)
        assertEquals(c.txn.accountId, t.toAccountId)
    }

    @Test fun ownTransfer_onlyOneSide_toMappedOwnAccount() = runBlocking {
        val wallet = repo.accountDao.insert(Account(name = "محفظتي", groupType = AccountGroup.CARD))
        repo.cardMappingDao.insert(CardMapping(bankId = "thawani", last4 = "5501", accountId = wallet))
        val r = repo.importSms(
            BM, "Dear Customer, You have sent OMR 50.000 to card 4000XXXXXXXX5501 from your a/c 0100XXXXXXXX0099 on 16/01/2025 14:30:00 using Mobile Payment services. Txn Id BMCT010000000503.",
            at(16, 14, 31),
        )!!
        assertEquals(TxnType.TRANSFER, r.txn.type)
        assertEquals(wallet, r.txn.toAccountId)
    }

    @Test fun sentToPerson_notOwn_staysExpense() = runBlocking {
        repo.importSms(BM, bmSent("30.000", "0504"), at(16, 14, 31))!!
        assertEquals(TxnType.EXPENSE, all().single().type)
    }

    // ---- 3. income wording ----

    @Test fun istalamt_recordedAsIncome() = runBlocking {
        val r = repo.importSms(BM, "لقد استلمت OMR 150.000 من SAMPLE PERSON", at(16, 9))!!
        assertEquals(TxnType.INCOME, r.txn.type)
    }

    // ---- 4. ATM → Cash ----

    @Test fun atmWithdrawal_isTransferToCash() = runBlocking {
        val r = repo.importSms(BM, "ATM cash withdrawal of OMR 150.000 from card ending 3030 at BM ATM DEMO on 16/01/2025 11:05", at(16, 11, 6))!!
        assertEquals(TxnType.TRANSFER, r.txn.type)
        val cash = repo.accountDao.all().first { it.id == r.txn.toAccountId }
        assertEquals(AccountGroup.CASH, cash.groupType)
        val r2 = repo.importSms(BM, "ATM cash withdrawal of OMR 5.000 from card ending 3030 at BM ATM DEMO on 16/01/2025 18:00", at(16, 18, 1))!!
        assertEquals(cash.id, r2.txn.toAccountId)
        assertEquals(1, repo.accountDao.all().count { it.groupType == AccountGroup.CASH })
    }

    // ---- 5. repair tool on data recorded by older versions ----

    private suspend fun legacy(type: Int, amount: Long, accountId: Long, body: String, sender: String, time: Long, hash: String): Long {
        val id = repo.txnDao.insert(Txn(type = type, amount = amount, accountId = accountId, description = body, dateTime = time, isAuto = true, smsHash = hash, note = "old"))
        repo.smsLogDao.insert(SmsLog(hash, sender, time, SmsLog.IMPORTED, id))
        return id
    }

    @Test fun repair_previewApplyUndo_neverTouchesManual() = runBlocking {
        val bm = repo.accountDao.insert(Account(name = "بنك مسقط ••0099", groupType = AccountGroup.BANK, cardLast4 = "0099"))
        val th = repo.accountDao.insert(Account(name = "ثواني – بطاقة موجب ••5501", groupType = AccountGroup.CARD, cardLast4 = "5501"))
        val bdAcc = repo.accountDao.insert(Account(name = "بنك ظفار ••7001", groupType = AccountGroup.BANK, cardLast4 = "7001"))
        val bdCard = repo.accountDao.insert(Account(name = "بنك ظفار ••6610", groupType = AccountGroup.BANK, cardLast4 = "6610"))
        // what 1.5.0 recorded
        legacy(TxnType.EXPENSE, 100_000, bm, bmSent("100.000", "0601"), BM, at(16, 14, 30), "h1")
        legacy(TxnType.INCOME, 100_000, th, thCredit("100.000"), TH, at(16, 14, 38), "h2")
        legacy(TxnType.EXPENSE, 150_000, bm, "لقد استلمت OMR 150.000 من SAMPLE PERSON", BM, at(16, 9), "h3")
        legacy(TxnType.EXPENSE, 150_000, bm, "ATM cash withdrawal of OMR 150.000 from card ending 3030 at BM ATM DEMO on 16/01/2025 11:05", BM, at(16, 11, 5), "h4")
        legacy(TxnType.EXPENSE, 10_000, bdAcc, bdAccount("10.000", "90.000"), BD, at(12, 9), "h5")
        legacy(TxnType.EXPENSE, 10_000, bdCard, "تم خصم مبلغ OMR 10.000 من بطاقتك رقم XXXX6610 بتاريخ 2025-01-12", BD, at(12, 9, 1), "h6")
        val manual = repo.txnDao.insert(Txn(type = TxnType.EXPENSE, amount = 150_000, accountId = bm, note = "يدوي", dateTime = at(16, 9)))
        val before = all()

        val plan = repo.planRepair()
        assertEquals(mapOf(RepairKind.OWN_TRANSFER to 1, RepairKind.FLIP_INCOME to 1, RepairKind.ATM_CASH to 1, RepairKind.MERGE_DOUBLE to 1), plan.groupingBy { it.kind }.eachCount())
        assertTrue(plan.none { it.primary.id == manual || it.secondary?.id == manual })

        val batch = repo.applyRepair(plan)!!
        val after = all()
        assertEquals(5, after.size)                                           // 7 − merged own-transfer income − merged double
        assertEquals(TxnType.TRANSFER, after.first { it.smsHash == "h1" }.type)
        assertEquals(th, after.first { it.smsHash == "h1" }.toAccountId)
        assertEquals(TxnType.INCOME, after.first { it.smsHash == "h3" }.type)
        val atm = after.first { it.smsHash == "h4" }
        assertEquals(TxnType.TRANSFER, atm.type)
        assertEquals(AccountGroup.CASH, repo.accountDao.all().first { it.id == atm.toAccountId }.groupType)
        assertEquals(before.first { it.id == manual }, after.first { it.id == manual })
        assertTrue(repo.planRepair().isEmpty())                               // idempotent

        assertTrue(repo.undoRepair(batch))
        val restored = all()
        assertEquals(before.map { Triple(it.id, it.type, it.toAccountId) }.sortedBy { it.first }, restored.map { Triple(it.id, it.type, it.toAccountId) }.sortedBy { it.first })
        assertNull(db.repairDao().get(batch)!!.takeIf { !it.undone })
    }
}
