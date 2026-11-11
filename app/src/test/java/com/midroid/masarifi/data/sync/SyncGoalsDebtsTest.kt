package com.midroid.masarifi.data.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.midroid.masarifi.data.AppDatabase
import com.midroid.masarifi.data.AppDatabase.Companion.configure
import com.midroid.masarifi.data.Defaults
import com.midroid.masarifi.data.Repository
import com.midroid.masarifi.data.SettingsStore
import com.midroid.masarifi.data.SavingsGoal
import com.midroid.masarifi.data.GoalContribution
import com.midroid.masarifi.data.Debt
import com.midroid.masarifi.data.DebtSettlement
import com.midroid.masarifi.data.DebtDirection
import com.midroid.masarifi.data.Txn
import com.midroid.masarifi.data.TxnType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SyncGoalsDebtsTest {
    class MemFile(var text: String? = null) : SyncFile { override fun read() = text; override fun write(text: String) { this.text = text } }
    inner class Dev(val name: String) {
        private val ctx = ApplicationProvider.getApplicationContext<Context>()
        val db: AppDatabase = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).configure().allowMainThreadQueries().build()
        val settings = SettingsStore(ctx, "settings_$name")
        val repo = Repository(db, settings)
        val store = SyncStore(ctx, name, "sync_$name")
        val svc = SyncService(repo, settings, store) { "2.7.0" }
        fun sync(f: SyncFile) = runBlocking { svc.sync(f, "manual", false) }
        fun cash() = runBlocking { repo.accountDao.all().first { it.uuid == Defaults.CASH_ACCOUNT_UUID }.id }
    }
    private val devs = ArrayList<Dev>()
    private fun dev(n: String) = Dev(n).also { devs += it }
    @After fun close() = devs.forEach { it.db.close() }

    @Test
    fun goalsAndDebtsTravelBetweenDevices() = runBlocking {
        val a = dev("a")
        val b = dev("b")
        a.repo.ensureDefaults()
        b.repo.ensureDefaults()

        val cash = a.cash()
        val txnId = a.repo.txnDao.insert(
            Txn(type = TxnType.EXPENSE, amount = 6_000, accountId = cash, dateTime = 1_000)
        )
        val txnUuid = a.repo.txnDao.get(txnId)!!.uuid
        val goalId = a.db.goalDao().insertGoal(SavingsGoal(name = "هدف", target = 10_000, accountId = cash))
        a.db.goalDao().insertContribution(GoalContribution(goalId = goalId, amount = 2_000, date = 1_000))
        a.db.goalDao().insertContribution(GoalContribution(goalId = goalId, amount = 3_000, date = 2_000))
        val debtId = a.db.debtDao().insertDebt(
            Debt(person = "أحمد", direction = DebtDirection.OWE, amount = 6_000, dateTime = 1_000, splitTxnId = txnId)
        )
        a.db.debtDao().insertSettlement(
            DebtSettlement(debtId = debtId, amount = 1_000, dateTime = 2_000, txnId = txnId)
        )

        val f = MemFile()
        a.sync(f)
        b.sync(f)

        val goals = b.db.goalDao().allGoals()
        assertEquals(1, goals.size)
        assertEquals(txnUuid, b.repo.txnDao.get(b.repo.txnDao.all().first { it.uuid == txnUuid }.id)!!.uuid)
        assertEquals("هدف", goals[0].name)
        assertEquals(10_000L, goals[0].target)
        assertEquals(b.cash(), goals[0].accountId)

        val contributions = b.db.goalDao().allContributions()
        assertEquals(2, contributions.size)
        assertTrue(contributions.all { it.goalId == goals[0].id })
        assertEquals(setOf(2_000L, 3_000L), contributions.map { it.amount }.toSet())

        val debts = b.db.debtDao().allDebts()
        assertEquals(1, debts.size)
        val bTxn = b.repo.txnDao.all().first { it.uuid == txnUuid }
        assertEquals(bTxn.id, debts[0].splitTxnId)

        val settlements = b.db.debtDao().allSettlements()
        assertEquals(1, settlements.size)
        assertEquals(debts[0].id, settlements[0].debtId)
        assertEquals(bTxn.id, settlements[0].txnId)
    }

    @Test
    fun deletingGoalOnOneDevicePropagates() = runBlocking {
        val a = dev("a")
        val b = dev("b")
        a.repo.ensureDefaults()
        b.repo.ensureDefaults()

        val cash = a.cash()
        val txnId = a.repo.txnDao.insert(
            Txn(type = TxnType.EXPENSE, amount = 6_000, accountId = cash, dateTime = 1_000)
        )
        val goalId = a.db.goalDao().insertGoal(SavingsGoal(name = "هدف", target = 10_000, accountId = cash))
        a.db.goalDao().insertContribution(GoalContribution(goalId = goalId, amount = 2_000, date = 1_000))
        val debtId = a.db.debtDao().insertDebt(
            Debt(person = "أحمد", direction = DebtDirection.OWE, amount = 6_000, dateTime = 1_000, splitTxnId = txnId)
        )
        a.db.debtDao().insertSettlement(
            DebtSettlement(debtId = debtId, amount = 1_000, dateTime = 2_000, txnId = txnId)
        )

        val f = MemFile()
        a.sync(f)
        b.sync(f)

        a.db.goalDao().deleteContributionsOf(goalId)
        a.db.goalDao().deleteGoal(goalId)
        a.sync(f)
        b.sync(f)

        assertEquals(0, b.db.goalDao().allGoals().size)
        assertEquals(0, b.db.goalDao().allContributions().size)
        assertEquals(1, b.db.debtDao().allDebts().size)
    }

    @Test
    fun editOnBReachesA() = runBlocking {
        val a = dev("a")
        val b = dev("b")
        a.repo.ensureDefaults()
        b.repo.ensureDefaults()

        a.db.goalDao().insertGoal(SavingsGoal(name = "هدف", target = 10_000, accountId = a.cash()))

        val f = MemFile()
        a.sync(f)
        b.sync(f)

        val g = b.db.goalDao().allGoals().single()
        b.db.goalDao().updateGoal(g.copy(name = "سفر", target = 9_000))
        b.sync(f)
        a.sync(f)

        val updated = a.db.goalDao().allGoals().single()
        assertEquals("سفر", updated.name)
        assertEquals(9_000L, updated.target)
    }
}
