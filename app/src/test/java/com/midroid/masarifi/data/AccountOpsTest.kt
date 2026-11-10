package com.midroid.masarifi.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.midroid.masarifi.bills.Bill
import com.midroid.masarifi.data.AppDatabase.Companion.configure
import com.midroid.masarifi.data.backup.BackupCodec
import com.midroid.masarifi.data.backup.BackupData
import com.midroid.masarifi.ui.AccountBalance
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class AccountOpsTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: Repository

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).configure().allowMainThreadQueries().build()
        repo = Repository(db, SettingsStore(ctx, "account-ops"))
        runBlocking { repo.ensureDefaults() }
    }

    @After fun tearDown() = db.close()

    private suspend fun bank(name: String = "بنك", initial: Long = 0): Account {
        repo.accountDao.all().firstOrNull { it.name == name }?.let { return it }
        val id = repo.saveAccount(Account(name = name, groupType = AccountGroup.BANK, initialBalance = initial))
        return repo.accountDao.all().first { it.id == id }
    }

    @Test fun moveOnDeleteReassignsTxnsTransfersRecurringMappingsAndBills() = runBlocking {
        val src = bank("مصدر", initial = 1_000)
        val dst = bank("وجهة", initial = 5_000)
        val exp = repo.txnDao.insert(Txn(type = TxnType.EXPENSE, amount = 100, accountId = src.id, dateTime = 10))
        val transferOut = repo.txnDao.insert(Txn(type = TxnType.TRANSFER, amount = 50, accountId = src.id, toAccountId = dst.id, dateTime = 11))
        val transferIn = repo.txnDao.insert(Txn(type = TxnType.TRANSFER, amount = 20, accountId = dst.id, toAccountId = src.id, dateTime = 12))
        val recId = repo.recurringDao.insert(Recurring(type = TxnType.EXPENSE, amount = 30, accountId = src.id, frequency = Repeat.MONTHLY, nextDate = 99, note = "فاتورة"))
        repo.cardMappingDao.insert(CardMapping(last4 = "1234", accountId = src.id, label = "visa"))
        val billId = repo.billDao.insert(Bill(name = "إيجار", amount = 200, accountId = src.id, anchorDay = 1, nextDue = 20_000))

        repo.removeAccount(src, Repository.AccountRemoval.MOVE, dst.id)

        assertNull(repo.accountDao.all().firstOrNull { it.id == src.id })
        assertEquals(dst.id, repo.txnDao.get(exp)!!.accountId)
        assertEquals(dst.id, repo.txnDao.get(transferOut)!!.accountId)
        assertEquals(dst.id, repo.txnDao.get(transferIn)!!.toAccountId)
        assertEquals(dst.id, repo.recurringDao.get(recId)!!.accountId)
        assertEquals(dst.id, repo.cardMappingDao.all().single().accountId)
        assertEquals(dst.id, repo.billDao.get(billId)!!.accountId)
        // target opening balance is unchanged
        assertEquals(5_000L, repo.accountDao.all().single { it.id == dst.id }.initialBalance)
        assertEquals(0, repo.txnDao.countForAccount(src.id))
    }

    @Test fun mergeKeepsTargetInitialBalanceAndRemovesSource() = runBlocking {
        val src = bank("قديم", initial = 9_000)
        val dst = bank("جديد", initial = 2_000)
        repo.txnDao.insert(Txn(type = TxnType.INCOME, amount = 400, accountId = src.id, dateTime = 5))
        repo.mergeAccounts(src, dst)
        val left = repo.accountDao.all().filter { it.deletedAt == null }
        assertTrue(left.none { it.id == src.id })
        assertEquals(2_000L, left.single { it.id == dst.id }.initialBalance)
        assertEquals(dst.id, repo.txnDao.all().single { it.amount == 400L }.accountId)
    }

    @Test fun softDeleteKeepsTxnsAndRestoreBringsAccountBack() = runBlocking {
        val a = bank("مؤقت")
        val id = repo.txnDao.insert(Txn(type = TxnType.EXPENSE, amount = 70, accountId = a.id, dateTime = 8))
        repo.removeAccount(a, Repository.AccountRemoval.SOFT)
        val gone = repo.accountDao.all().single { it.id == a.id }
        assertNotNull(gone.deletedAt)
        assertEquals(a.id, repo.txnDao.get(id)!!.accountId)
        repo.restoreAccount(gone)
        assertNull(repo.accountDao.all().single { it.id == a.id }.deletedAt)
        assertEquals(1, repo.txnDao.countForAccount(a.id))
    }

    @Test fun wipeDeletesTransactionsAfterExplicitCall() = runBlocking {
        val a = bank("يمسح")
        repo.txnDao.insert(Txn(type = TxnType.EXPENSE, amount = 15, accountId = a.id, dateTime = 3))
        repo.removeAccount(a, Repository.AccountRemoval.WIPE)
        assertTrue(repo.accountDao.all().none { it.id == a.id })
        assertEquals(0, repo.txnDao.all().count { it.accountId == a.id })
    }

    @Test fun hiddenAccountStillCountsInTotalsWhenIncluded() {
        val cash = Account(1, "نقد", includeInTotal = true, hidden = false)
        val hidden = Account(2, "مخفي", includeInTotal = true, hidden = true)
        val excluded = Account(3, "وديعة", includeInTotal = false, hidden = false)
        val rows = listOf(AccountBalance(cash, 100), AccountBalance(hidden, 50), AccountBalance(excluded, 20))
        val total = rows.filter { it.account.includeInTotal }.sumOf { it.balance }
        assertEquals(150L, total)
        val visible = rows.filter { !it.account.hidden }
        assertEquals(setOf(1L, 3L), visible.map { it.account.id }.toSet())
        // toggling "show hidden" must not change the header total
        assertEquals(total, rows.filter { it.account.includeInTotal }.sumOf { it.balance })
    }

    @Test fun backupRoundTripKeepsHiddenDeletedAndCarryOver() {
        val data = BackupData(
            createdAt = 1, appVersion = "2.6.3",
            settings = AppSettings(carryOver = true),
            accounts = listOf(
                Account(1, "نقد", hidden = true, deletedAt = 1_700L),
                Account(2, "بنك", sortOrder = 1),
            ),
            categories = emptyList(), transactions = emptyList(), recurring = emptyList(),
            budgets = emptyList(), merchantRules = emptyList(), smsLog = emptyList(),
        )
        val back = BackupCodec.decode(BackupCodec.encode(data))
        assertTrue(back.settings!!.carryOver)
        assertTrue(back.accounts[0].hidden)
        assertEquals(1_700L, back.accounts[0].deletedAt)
        assertFalse(back.accounts[1].hidden)
        assertNull(back.accounts[1].deletedAt)
    }
}
