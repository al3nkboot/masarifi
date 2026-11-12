package com.midroid.masarifi.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class CsvGoalsDebtsTest {
    private fun newRepo(): Pair<AppDatabase, Repository> {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = Repository(db, SettingsStore(ctx))
        runBlocking { repo.ensureDefaults() }
        return db to repo
    }

    @Test
    fun exportImportRoundTripKeepsGoalsAndDebts() = runBlocking {
        val (dbA, repoA) = newRepo()
        val (dbB, repoB) = newRepo()
        try {
            val accountA = repoA.accountDao.all().first()
            repoA.txnDao.insert(
                Txn(
                    type = TxnType.EXPENSE,
                    amount = 1_500,
                    accountId = accountA.id,
                    dateTime = 1_759_999_980_000,
                    note = "x",
                ),
            )

            val goalId = dbA.goalDao().insertGoal(
                SavingsGoal(
                    name = "سيارة",
                    target = 5_000_000,
                    targetDate = 20_600L,
                    accountId = accountA.id,
                    note = "n,1",
                ),
            )
            dbA.goalDao().insertContribution(
                GoalContribution(
                    goalId = goalId,
                    amount = 250_000,
                    date = 20_400L,
                    note = "",
                ),
            )
            dbA.goalDao().insertContribution(
                GoalContribution(
                    goalId = goalId,
                    amount = -10_500,
                    date = 20_401L,
                    note = "",
                ),
            )

            val lentDebtId = dbA.debtDao().insertDebt(
                Debt(
                    person = "أحمد",
                    direction = DebtDirection.LENT,
                    amount = 30_000,
                    dateTime = 1_759_999_980_000,
                    note = "line \"q\"",
                ),
            )
            dbA.debtDao().insertDebt(
                Debt(
                    person = "Sara",
                    direction = DebtDirection.OWE,
                    amount = 7_000,
                    dateTime = 1_759_999_980_000,
                    note = "",
                ),
            )
            dbA.debtDao().insertSettlement(
                DebtSettlement(
                    debtId = lentDebtId,
                    amount = 10_000,
                    dateTime = 1_759_999_980_000,
                ),
            )

            val settings = AppSettings(currency = "OMR")
            val output = ByteArrayOutputStream()
            assertEquals(1, CsvBackup.export(repoA, output, settings))
            val bytes = output.toByteArray()

            assertEquals(1, CsvBackup.import(repoB, ByteArrayInputStream(bytes), settings))
            assertEquals(1, repoB.txnDao.all().size)

            val goalA = dbA.goalDao().allGoals().single()
            val goalB = dbB.goalDao().allGoals().single()
            assertEquals(goalA.name, goalB.name)
            assertEquals(goalA.target, goalB.target)
            assertEquals(goalA.targetDate, goalB.targetDate)
            assertEquals(goalA.note, goalB.note)
            assertEquals(goalA.uuid, goalB.uuid)
            val accountB = repoB.accountDao.all().first { it.name == accountA.name }
            assertEquals(accountB.id, goalB.accountId)

            val contributionsB = dbB.goalDao().allContributions()
            assertEquals(2, contributionsB.size)
            assertEquals(setOf(250_000L, -10_500L), contributionsB.map { it.amount }.toSet())
            assertEquals(setOf(20_400L, 20_401L), contributionsB.map { it.date }.toSet())
            assertEquals(setOf(goalB.id), contributionsB.map { it.goalId }.toSet())

            val debtsB = dbB.debtDao().allDebts()
            assertEquals(2, debtsB.size)
            val lentB = debtsB.single { it.person == "أحمد" }
            val oweB = debtsB.single { it.person == "Sara" }
            val lentA = dbA.debtDao().allDebts().single { it.person == "أحمد" }
            val oweA = dbA.debtDao().allDebts().single { it.person == "Sara" }
            assertEquals(lentA.direction, lentB.direction)
            assertEquals(lentA.amount, lentB.amount)
            assertEquals(lentA.dateTime, lentB.dateTime)
            assertEquals(lentA.note, lentB.note)
            assertEquals(oweA.direction, oweB.direction)
            assertEquals(oweA.amount, oweB.amount)
            assertEquals(oweA.dateTime, oweB.dateTime)
            assertEquals(oweA.note, oweB.note)

            val settlementsB = dbB.debtDao().allSettlements()
            assertEquals(1, settlementsB.size)
            assertEquals(10_000L, settlementsB.single().amount)
            assertEquals(lentB.id, settlementsB.single().debtId)

            assertEquals(0, CsvBackup.import(repoB, ByteArrayInputStream(bytes), settings))
            assertEquals(1, repoB.txnDao.all().size)
            assertEquals(1, dbB.goalDao().allGoals().size)
            assertEquals(2, dbB.goalDao().allContributions().size)
            assertEquals(2, dbB.debtDao().allDebts().size)
            assertEquals(1, dbB.debtDao().allSettlements().size)
        } finally {
            dbA.close()
            dbB.close()
        }
    }

    @Test
    fun old263CsvWithoutExtrasImports() = runBlocking {
        val (db, repo) = newRepo()
        try {
            val csv = "\uFEFFDate,Type,Account,To Account,Category,Amount,Currency,Note,Merchant,Auto,Description\r\n" +
                "2026-01-05 09:30,Expense,Cash,,Food,1.500,OMR,tea,,0,\r\n"

            assertEquals(1, CsvBackup.import(repo, ByteArrayInputStream(csv.toByteArray()), AppSettings(currency = "OMR")))
            assertEquals(1, repo.txnDao.all().size)
            assertEquals(0, db.goalDao().allGoals().size)
            assertEquals(0, db.goalDao().allContributions().size)
            assertEquals(0, db.debtDao().allDebts().size)
            assertEquals(0, db.debtDao().allSettlements().size)
        } finally {
            db.close()
        }
    }

    @Test
    fun malformedExtraRowsAreSkipped() = runBlocking {
        val (db, repo) = newRepo()
        try {
            val csv = "\uFEFFDate,Type,Account,To Account,Category,Amount,Currency,Note,Merchant,Auto,Description\r\n" +
                "2026-01-05 09:30,Expense,Cash,,Food,1.500,OMR,tea,,0,\r\n" +
                "goal,u1,Car,notanumber,,,\r\n" +
                "goal_contribution,u2,missing-goal,1.000,2026-01-01,\r\n" +
                "debt,u3,Ali,lent,5.000,bad-date,\r\n"

            assertEquals(1, CsvBackup.import(repo, ByteArrayInputStream(csv.toByteArray()), AppSettings(currency = "OMR")))
            assertEquals(1, repo.txnDao.all().size)
            assertEquals(0, db.goalDao().allGoals().size)
            assertEquals(0, db.goalDao().allContributions().size)
            assertEquals(0, db.debtDao().allDebts().size)
            assertEquals(0, db.debtDao().allSettlements().size)
        } finally {
            db.close()
        }
    }
}
