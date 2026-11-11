package com.midroid.masarifi.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoalsDebtsLogicTest {

    @Test
    fun goalSavedOnlyUsesMatchingContributionsAndNeverGoesBelowZero() {
        val contributions = listOf(
            GoalContribution(id = 1, goalId = 10, amount = 100, date = 0),
            GoalContribution(id = 2, goalId = 11, amount = 900, date = 0),
            GoalContribution(id = 3, goalId = 10, amount = -300, date = 0)
        )

        assertEquals(0L, GoalLogic.saved(10, contributions))
        assertEquals(900L, GoalLogic.saved(11, contributions))
    }

    @Test
    fun goalProgressIsCappedAndHandlesZeroTarget() {
        assertEquals(1.0, GoalLogic.progress(1200, 1000), 0.0)
        assertEquals(0.0, GoalLogic.progress(100, 0), 0.0)
        assertEquals(25, GoalLogic.percent(250, 1000))
    }

    @Test
    fun monthlyNeededUsesWholeMonthsAndRequiresTargetDate() {
        assertEquals(
            100L,
            GoalLogic.monthlyNeeded(
                saved = 0,
                target = 1200,
                targetDate = LocalDate.of(2027, 1, 1).toEpochDay(),
                today = LocalDate.of(2026, 1, 1)
            )
        )
        assertNull(
            GoalLogic.monthlyNeeded(
                saved = 0,
                target = 1200,
                targetDate = null,
                today = LocalDate.of(2026, 1, 1)
            )
        )
    }

    @Test
    fun topGoalPicksIncompleteGoalWithHighestProgress() {
        val goals = listOf(
            SavingsGoal(id = 1, name = "low progress", target = 1000),
            SavingsGoal(id = 2, name = "high progress", target = 500),
            SavingsGoal(id = 3, name = "complete", target = 100)
        )
        val contributions = listOf(
            GoalContribution(id = 1, goalId = 1, amount = 100, date = 0),
            GoalContribution(id = 2, goalId = 2, amount = 300, date = 0),
            GoalContribution(id = 3, goalId = 3, amount = 100, date = 0)
        )

        assertEquals(2L, GoalLogic.topGoal(goals, contributions)?.id)
        assertNull(GoalLogic.topGoal(emptyList(), contributions))
    }

    @Test
    fun debtSplitDistributesRemainderWithoutLosingTotal() {
        val split = DebtLogic.split(1000, 3)

        assertEquals(listOf(334L, 333L, 333L), split)
        assertEquals(1000L, split.sum())
        assertEquals(listOf(1000L), DebtLogic.split(1000, 1))
    }

    @Test
    fun debtRemainingNeverBecomesNegative() {
        val debt = Debt(
            id = 1,
            person = "Ali",
            direction = DebtDirection.LENT,
            amount = 100,
            dateTime = 0
        )
        val settlements = listOf(
            DebtSettlement(id = 1, debtId = debt.id, amount = 200, dateTime = 0)
        )

        assertEquals(0L, DebtLogic.remaining(debt, settlements))
    }

    @Test
    fun debtTotalsUseRemainingAmounts() {
        val debts = listOf(
            Debt(
                id = 1,
                person = "Ali",
                direction = DebtDirection.LENT,
                amount = 5000,
                dateTime = 0
            ),
            Debt(
                id = 2,
                person = "Sara",
                direction = DebtDirection.OWE,
                amount = 3000,
                dateTime = 0
            )
        )
        val settlements = listOf(
            DebtSettlement(id = 1, debtId = 1, amount = 2000, dateTime = 0)
        )

        val totals = DebtLogic.totals(debts, settlements)

        assertEquals(3000L, totals.owedToMe)
        assertEquals(3000L, totals.iOwe)
        assertEquals(0L, totals.net)
    }

    @Test
    fun debtByPersonGroupsTrimmedNamesTogether() {
        val debts = listOf(
            Debt(
                id = 1,
                person = " Ali",
                direction = DebtDirection.LENT,
                amount = 1000,
                dateTime = 0
            ),
            Debt(
                id = 2,
                person = "Ali",
                direction = DebtDirection.OWE,
                amount = 500,
                dateTime = 0
            )
        )

        val byPerson = DebtLogic.byPerson(debts, emptyList())

        assertEquals(1, byPerson.size)
        assertEquals("Ali", byPerson.first().first)
        assertEquals(500L, byPerson.first().second)
    }

    @Test
    fun netWorthClassifiesBalancesAndIgnoresExcludedAccounts() {
        val accounts = listOf(
            Account(id = 1, name = "a", groupType = AccountGroup.BANK) to 10_000L,
            Account(id = 2, name = "c", groupType = AccountGroup.CARD) to -2_000L,
            Account(id = 3, name = "l", groupType = AccountGroup.LOAN) to 5_000L,
            Account(
                id = 4,
                name = "x",
                groupType = AccountGroup.INVESTMENT,
                includeInTotal = false
            ) to 99_000L
        )

        val result = NetWorthCalc.compute(accounts)

        assertEquals(10_000L, result.assets)
        assertEquals(7_000L, result.liabilities)
        assertEquals(3_000L, result.net)
        assertTrue(result.net == result.assets - result.liabilities)
    }
}
