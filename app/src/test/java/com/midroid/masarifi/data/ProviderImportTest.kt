package com.midroid.masarifi.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.midroid.masarifi.sms.DefaultSmsConfig
import com.midroid.masarifi.sms.ProviderSamplesTest.Companion.BD
import com.midroid.masarifi.sms.ProviderSamplesTest.Companion.BD_CREDIT_2
import com.midroid.masarifi.sms.ProviderSamplesTest.Companion.BD_CREDIT_200
import com.midroid.masarifi.sms.ProviderSamplesTest.Companion.BD_SUSPENDED
import com.midroid.masarifi.sms.ProviderSamplesTest.Companion.BM
import com.midroid.masarifi.sms.ProviderSamplesTest.Companion.BM_OTP
import com.midroid.masarifi.sms.ProviderSamplesTest.Companion.BM_SENT
import com.midroid.masarifi.sms.ProviderSamplesTest.Companion.BM_TOPUP
import com.midroid.masarifi.sms.ProviderSamplesTest.Companion.T1
import com.midroid.masarifi.sms.ProviderSamplesTest.Companion.T2
import com.midroid.masarifi.sms.ProviderSamplesTest.Companion.T3
import com.midroid.masarifi.sms.ProviderSamplesTest.Companion.THAWANI
import com.midroid.masarifi.sms.WalletTopup
import com.midroid.masarifi.sms.WalletTopupCodec
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.time.ZoneId

/** The user's real (bank-masked) SMS samples end-to-end through Repository + Room (in-memory). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ProviderImportTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: Repository
    private lateinit var settings: SettingsStore
    private val zone = ZoneId.systemDefault()
    private fun at(d: Int, h: Int, m: Int = 0, s: Int = 0) = LocalDateTime.of(2025, 1, d, h, m, s).atZone(zone).toInstant().toEpochMilli()

    private val thawaniAccount = "ثواني – بطاقة موجب ••5501"
    private val bmAccount = "بنك مسقط ••0099"
    private val bdAccount = "بنك ظفار ••7001"

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        settings = SettingsStore(ctx).apply { update { AppSettings() } }
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = Repository(db, settings)
        runBlocking { repo.ensureDefaults() }
    }

    @After fun tearDown() = db.close()

    private suspend fun account(name: String) = repo.accountDao.all().single { it.name == name }
    private suspend fun catKey(t: Txn) = repo.categoryDao.all().first { it.id == t.categoryId }.key

    @Test fun thawaniPurchases_walletCardAccount_categories_balances() = runBlocking {
        val r1 = repo.importSms(THAWANI, T1, at(15, 9))!!
        val r2 = repo.importSms("ThawaniPay", T2, at(15, 10))!!
        val r3 = repo.importSms("THAWANI", T3, at(15, 11))!!
        val txns = listOf(r1, r2, r3).map { it.txn }
        assertTrue(txns.all { it.type == TxnType.EXPENSE && it.isAuto })
        assertEquals(listOf(25_000L, 1_500L, 250L), txns.map { it.amount })
        assertEquals(listOf("SAMPLE CLINIC", "SAMPLE PHARMACY", "DEMO BAKERY"), txns.map { it.merchant })
        assertEquals(listOf("health", "health", "food"), txns.map { catKey(it) })
        val acc = account(thawaniAccount)
        assertEquals(AccountGroup.CARD, acc.groupType)
        assertEquals("5501", acc.cardLast4)
        assertTrue(txns.all { it.accountId == acc.id })
        assertEquals(48_250L, repo.smsBalanceDao.latest(acc.id)!!.balance)
        assertEquals(2, repo.accountDao.all().size) // cash + Thawani
    }

    @Test fun bankMuscatTopup_isTransferToThawaniAccount_notExpense() = runBlocking {
        repo.importSms(THAWANI, T1, at(15, 9))!!
        val r = repo.importSms(BM, BM_TOPUP, at(15, 10, 0, 30))!!
        val t = r.txn
        assertEquals(TxnType.TRANSFER, t.type)
        assertEquals(50_000L, t.amount)
        assertEquals(account(bmAccount).id, t.accountId)
        assertEquals(AccountGroup.BANK, account(bmAccount).groupType)
        assertEquals(account(thawaniAccount).id, t.toAccountId)
        assertNull(t.categoryId)
        assertEquals(at(15, 10, 0, 0), t.dateTime)
        assertEquals(1, repo.txnDao.all().count { it.type == TxnType.EXPENSE })
        assertEquals(500_000L, repo.smsBalanceDao.latest(account(bmAccount).id)!!.balance)
    }

    @Test fun topupFirst_createsWalletAccount_adoptedByLaterCardSms() = runBlocking {
        val r = repo.importSms(BM, BM_TOPUP, at(15, 10, 1))!!
        val wallet = repo.accountDao.all().single { it.id == r.txn.toAccountId }
        assertEquals("ثواني – بطاقة موجب", wallet.name)
        assertEquals(AccountGroup.CARD, wallet.groupType)
        assertNull(wallet.cardLast4)
        val p = repo.importSms(THAWANI, T2, at(15, 12))!!
        assertEquals(wallet.id, p.txn.accountId)                 // same account, not a second one
        val adopted = account(thawaniAccount)
        assertEquals(wallet.id, adopted.id)
        assertEquals("5501", adopted.cardLast4)
        assertEquals(3, repo.accountDao.all().size)              // cash, Bank Muscat ••0099, Thawani
    }

    @Test fun topupRule_configurableTargetAccount() = runBlocking {
        val mine = repo.accountDao.insert(Account(name = "محفظتي", groupType = AccountGroup.CARD))
        val uuid = repo.accountDao.all().first { it.id == mine }.uuid
        settings.update { it.copy(walletTopups = WalletTopupCodec.encode(listOf(WalletTopup("w", "محفظتي", "My wallet", listOf("thawanipay topup"), targetAccountUuid = uuid)))) }
        val t = repo.importSms(BM, BM_TOPUP, at(15, 10, 1))!!.txn
        assertEquals(TxnType.TRANSFER, t.type)
        assertEquals(mine, t.toAccountId)
        // removing all rules → the same SMS (from another sender variant) would be an expense again
        settings.update { it.copy(walletTopups = WalletTopupCodec.encode(emptyList())) }
        assertTrue(settings.current.walletTopupList.isEmpty())
        settings.update { it.copy(walletTopups = "") }
        assertEquals(DefaultSmsConfig.walletTopups, settings.current.walletTopupList)
    }

    @Test fun otpAndCardSuspended_ignored() = runBlocking {
        assertNull(repo.importSms(BM, BM_OTP, at(15, 12)))
        assertNull(repo.importSms(BD, BD_SUSPENDED, at(15, 12)))
        assertTrue(repo.txnDao.all().isEmpty())
    }

    @Test fun sentToPerson_p2pCategory_txnIdDedupe_learning() = runBlocking {
        val r = repo.importSms(BM, BM_SENT, at(16, 14, 31))!!
        val t = r.txn
        assertEquals(TxnType.EXPENSE, t.type)
        assertEquals(30_000L, t.amount)
        assertEquals("ABCD######E XY", t.merchant)
        assertEquals(at(16, 14, 30, 0), t.dateTime)
        assertEquals(account(bmAccount).id, t.accountId)
        assertEquals("ref:bankmuscat:BMCT010000000114", t.smsHash)
        val cat = repo.categoryDao.all().first { it.id == t.categoryId }
        assertEquals("تحويلات لأشخاص", cat.nameAr)
        assertEquals("cat-p2p", cat.uuid)
        assertEquals(470_000L, repo.smsBalanceDao.latest(t.accountId)!!.balance)
        // the bank re-sends the same Txn Id with different wording → not recorded twice
        assertNull(repo.importSms("BANKMUSCAT", BM_SENT.replace("Dear Customer, ", "Dear Valued Customer, "), at(16, 14, 35)))
        assertEquals(1, repo.txnDao.all().size)
        // user recategorizes → app learns the recipient
        val gift = repo.categoryDao.all().first { it.key == "gift" }
        repo.saveTxn(repo.txnDao.get(t.id)!!.copy(categoryId = gift.id))
        val next = repo.importSms(
            BM,
            "Dear Customer, You have sent OMR 12.000 to ABCD######E XY from your a/c 0100XXXXXXXX0099 on 16/01/2025 15:00:00 using Mobile Payment services. Txn Id BMCT010000000999. Avl Bal OMR 458.000.",
            at(16, 15, 1),
        )!!
        assertEquals(gift.id, next.txn.categoryId)
        assertEquals(1, repo.categoryDao.all().count { it.key == Repository.P2P_KEY })
    }

    @Test fun dhofarCredits_otherIncome_accountByLastDigits() = runBlocking {
        val a = repo.importSms(BD, BD_CREDIT_2, at(10, 9))!!
        val b = repo.importSms("BKDHOFAR", BD_CREDIT_200, at(10, 10))!!
        for (r in listOf(a, b)) {
            assertEquals(TxnType.INCOME, r.txn.type)
            assertEquals("other_income", catKey(r.txn))
            assertFalse(r.directionFromBalance)
        }
        assertEquals(listOf(2_000L, 200_000L), listOf(a.txn.amount, b.txn.amount))
        val acc = account(bdAccount)
        assertEquals(AccountGroup.BANK, acc.groupType)
        assertEquals("7001", acc.cardLast4)
        assertEquals(205_000L, repo.smsBalanceDao.latest(acc.id)!!.balance)
    }

    @Test fun balanceDelta_flipsAmbiguousCreditToDebit() = runBlocking {
        repo.importSms(BD, BD_CREDIT_200, at(10, 9))!!                       // balance 205.000
        val r = repo.importSms(BD, "حسابكم رقم 0200XXX0007001 قد أدخل فيه مبلغ OMR 200.000 بتاريخ 2025-01-10. رصيدكم الحالي هو OMR 5.000", at(10, 10))!!
        assertTrue(r.directionFromBalance)
        assertEquals(TxnType.EXPENSE, r.txn.type)                            // 205.000 − 200.000 = 5.000
        assertEquals("other", catKey(r.txn))
    }

    @Test fun rescan_sameMessagesTwice_noDoubles_andManualEntryLinked() = runBlocking {
        val cash = repo.accountDao.all().first().id
        val health = repo.categoryDao.all().first { it.key == "health" }.id
        val manualId = repo.txnDao.insert(Txn(type = TxnType.EXPENSE, amount = 25_000, accountId = cash, categoryId = health, note = "فحص طبي", dateTime = at(15, 8)))
        val all = listOf(
            Triple(THAWANI, T1, at(15, 9)), Triple(THAWANI, T2, at(15, 10)), Triple(THAWANI, T3, at(15, 10, 30)),
            Triple(BM, BM_TOPUP, at(15, 10, 1)), Triple(BM, BM_OTP, at(15, 12)), Triple(BM, BM_SENT, at(16, 14, 31)),
            Triple(BD, BD_SUSPENDED, at(9, 9)), Triple(BD, BD_CREDIT_2, at(10, 9)), Triple(BD, BD_CREDIT_200, at(10, 10)),
        )
        val first = all.map { (s, b, t) -> repo.importSms(s, b, t) }
        assertTrue(first[0]!!.linked)                                       // T1 linked to the manual 25.000 entry
        assertEquals(manualId, first[0]!!.txn.id)
        assertEquals(listOf(true, true, true, true, false, true, false, true, true), first.map { it != null })
        val count = repo.txnDao.all().size
        assertEquals(7, count) // manual(+T1 link), T2, T3, top-up, sent, 2 Dhofar credits
        // re-scan: everything is already known
        assertTrue(all.map { (s, b, t) -> repo.importSms(s, b, t) }.all { it == null })
        assertEquals(count, repo.txnDao.all().size)
    }
}
