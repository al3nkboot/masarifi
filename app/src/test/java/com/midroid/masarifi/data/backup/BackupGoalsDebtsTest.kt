package com.midroid.masarifi.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.midroid.masarifi.data.Account
import com.midroid.masarifi.data.AccountGroup
import com.midroid.masarifi.data.AppDatabase
import com.midroid.masarifi.data.AppSettings
import com.midroid.masarifi.data.Category
import com.midroid.masarifi.data.Debt
import com.midroid.masarifi.data.DebtDirection
import com.midroid.masarifi.data.DebtSettlement
import com.midroid.masarifi.data.GoalContribution
import com.midroid.masarifi.data.Repository
import com.midroid.masarifi.data.SavingsGoal
import com.midroid.masarifi.data.SettingsStore
import com.midroid.masarifi.data.Txn
import com.midroid.masarifi.data.TxnType
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class BackupGoalsDebtsTest {
    private val sample = BackupData(
        createdAt = 1_000L,
        appVersion = "test",
        settings = AppSettings(),
        accounts = listOf(
            Account(1, "Cash", AccountGroup.CASH, 100),
            Account(2, "Bank", AccountGroup.BANK, 200),
        ),
        categories = listOf(
            Category(1, TxnType.EXPENSE, "طعام", "Food", "🍜", "food", 1),
        ),
        transactions = listOf(
            Txn(10, TxnType.EXPENSE, 25, 1, null, 1, "note 10", "desc 10", 10L, createdAt = 5L),
            Txn(11, TxnType.EXPENSE, 30, 2, null, 1, "note 11", "desc 11", 11L, createdAt = 5L),
        ),
        recurring = emptyList(), budgets = emptyList(), merchantRules = emptyList(), smsLog = emptyList(),
        goals = listOf(
            SavingsGoal(1, "Emergency", 500, 20_500L, 1, "goal note", 100L, "g1", 101L),
            SavingsGoal(2, "سيارة", 1_000, null, null, "", 200L, "g2", 201L),
        ),
        goalContributions = listOf(
            GoalContribution(1, 1, 100, 1L, "first", 300L, "gc1", 301L),
            GoalContribution(2, 1, 50, 2L, "second", 302L, "gc2", 303L),
            GoalContribution(3, 2, -25, 3L, "correction", 304L, "gc3", 305L),
        ),
        debts = listOf(
            Debt(1, "Alice", DebtDirection.OWE, 75, 1L, "owe note", 10L, 400L, "d1", 401L),
            Debt(2, "Bob", DebtDirection.LENT, 125, 2L, "line one\nline \"two\"", null, 402L, "d2", 403L),
        ),
        debtSettlements = listOf(
            DebtSettlement(1, 1, 25, 1L, 11L, 500L, "ds1", 501L),
            DebtSettlement(2, 2, 25, 2L, null, 502L, "ds2", 503L),
        ),
    )

    @Test
    fun encodeDecodeRoundTripKeepsGoalsAndDebts() {
        val encoded = BackupCodec.encode(sample)
        val decoded = BackupCodec.decode(encoded)

        assertEquals(sample, decoded)
        assertEquals(encoded, BackupCodec.encode(decoded))

        val json = JSONObject(encoded)
        assertEquals(2, json.getJSONArray("goals").length())
        assertEquals(3, json.getJSONArray("goalContributions").length())
        assertEquals(2, json.getJSONArray("debts").length())
        assertEquals(2, json.getJSONArray("debtSettlements").length())
        assertEquals(4, json.getInt("schemaVersion"))
    }

    @Test
    fun restoreRoundTripInDb() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val settings = SettingsStore(ctx)
        val repo = Repository(db, settings)
        val svc = BackupService(repo, settings) { "test" }

        try {
            runBlocking {
                repo.ensureDefaults()
                svc.restore(BackupCodec.decode(BackupCodec.encode(sample)))

                val snap = svc.snapshot(now = 1L)
                assertEquals(
                    sample.goals.map { it.copy(updatedAt = 0L) }.sortedBy { it.id },
                    snap.goals.map { it.copy(updatedAt = 0L) }.sortedBy { it.id },
                )
                assertEquals(
                    sample.goalContributions.map { it.copy(updatedAt = 0L) }.sortedBy { it.id },
                    snap.goalContributions.map { it.copy(updatedAt = 0L) }.sortedBy { it.id },
                )
                assertEquals(
                    sample.debts.map { it.copy(updatedAt = 0L) }.sortedBy { it.id },
                    snap.debts.map { it.copy(updatedAt = 0L) }.sortedBy { it.id },
                )
                assertEquals(
                    sample.debtSettlements.map { it.copy(updatedAt = 0L) }.sortedBy { it.id },
                    snap.debtSettlements.map { it.copy(updatedAt = 0L) }.sortedBy { it.id },
                )

                svc.restore(BackupCodec.decode(BackupCodec.encode(sample)))
                val again = svc.snapshot(now = 2L)
                assertEquals(2, again.goals.size)
                assertEquals(3, again.goalContributions.size)
                assertEquals(2, again.debts.size)
                assertEquals(2, again.debtSettlements.size)
            }
        } finally {
            db.close()
            runBlocking { SettingsStore(ctx).update { AppSettings() } }
        }
    }

    @Test
    fun restoreDropsDanglingReferences() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val settings = SettingsStore(ctx)
        val repo = Repository(db, settings)
        val svc = BackupService(repo, settings) { "test" }

        val goalWithMissingAccount = sample.goals.first().copy(accountId = 99L)
        val debtWithMissingSplit = sample.debts.first().copy(splitTxnId = 77L)
        val settlementWithMissingTxn = sample.debtSettlements.first().copy(txnId = 88L)
        val orphanContribution = sample.goalContributions.first().copy(id = 99L, goalId = 500L)
        val orphanSettlement = sample.debtSettlements.first().copy(id = 99L, debtId = 600L)
        val invalid = sample.copy(
            goals = listOf(goalWithMissingAccount) + sample.goals.drop(1),
            goalContributions = sample.goalContributions + orphanContribution,
            debts = listOf(debtWithMissingSplit) + sample.debts.drop(1),
            debtSettlements = listOf(settlementWithMissingTxn) + sample.debtSettlements.drop(1) +
                orphanSettlement,
        )

        try {
            runBlocking {
                repo.ensureDefaults()
                svc.restore(BackupCodec.decode(BackupCodec.encode(invalid)))
                val snap = svc.snapshot(now = 1L)

                assertNull(snap.goals.first { it.id == 1L }.accountId)
                assertNull(snap.debts.first { it.id == 1L }.splitTxnId)
                assertNull(snap.debtSettlements.first { it.id == 1L }.txnId)
                assertTrue(snap.goalContributions.none { it.goalId == 500L })
                assertTrue(snap.debtSettlements.none { it.debtId == 600L })
            }
        } finally {
            db.close()
            runBlocking { SettingsStore(ctx).update { AppSettings() } }
        }
    }

    @Test
    fun restoresOld263BackupWithoutNewSections() {
        val oldJson = JSONObject(BackupCodec.encode(sample)).apply {
            remove("goals")
            remove("goalContributions")
            remove("debts")
            remove("debtSettlements")
            put("appVersion", "2.6.3")
        }
        val decoded = BackupCodec.decode(oldJson.toString())

        assertTrue(decoded.goals.isEmpty())
        assertTrue(decoded.goalContributions.isEmpty())
        assertTrue(decoded.debts.isEmpty())
        assertTrue(decoded.debtSettlements.isEmpty())
        assertEquals(2, decoded.transactions.size)

        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val settings = SettingsStore(ctx)
        val repo = Repository(db, settings)
        val svc = BackupService(repo, settings) { "test" }

        try {
            runBlocking {
                repo.ensureDefaults()
                db.goalDao().insertGoal(SavingsGoal(name = "x", target = 1))
                db.debtDao().insertDebt(
                    Debt(
                        person = "p",
                        direction = DebtDirection.LENT,
                        amount = 1,
                        dateTime = 1,
                    ),
                )

                svc.restore(decoded)

                assertTrue(db.goalDao().allGoals().isEmpty())
                assertTrue(db.goalDao().allContributions().isEmpty())
                assertTrue(db.debtDao().allDebts().isEmpty())
                assertTrue(db.debtDao().allSettlements().isEmpty())
                assertEquals(2, svc.snapshot().transactions.size)
            }
        } finally {
            db.close()
            runBlocking { SettingsStore(ctx).update { AppSettings() } }
        }
    }
}
