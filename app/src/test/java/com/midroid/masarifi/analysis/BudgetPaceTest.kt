package com.midroid.masarifi.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * 2.8: Unit tests for BudgetPace pure calculations.
 */
class BudgetPaceTest {

    @Test
    fun totalBudgetOverallVsCategoryFallback() {
        // Overall budget set (> 0) overrides category budgets
        val overallOnly = BudgetPace.totalBudget(overallBudget = 250_000L, categoryBudgets = listOf(50_000L, 80_000L))
        assertEquals(250_000L, overallOnly)

        // When overall is 0, fall back to sum of category budgets
        val fallbackSum = BudgetPace.totalBudget(overallBudget = 0L, categoryBudgets = listOf(50_000L, 80_000L, 20_000L))
        assertEquals(150_000L, fallbackSum)

        // Map overload with 0L overall
        val mapWithOverall = BudgetPace.totalBudget(mapOf(0L to 300_000L, 1L to 40_000L, 2L to 60_000L))
        assertEquals(300_000L, mapWithOverall)

        val mapFallback = BudgetPace.totalBudget(mapOf(0L to 0L, 1L to 40_000L, 2L to 60_000L))
        assertEquals(100_000L, mapFallback)

        // Neither set
        assertEquals(0L, BudgetPace.totalBudget(overallBudget = 0L, categoryBudgets = emptyList()))
        assertEquals(0L, BudgetPace.totalBudget(mapOf(0L to 0L)))
    }

    @Test
    fun remainingAndOverAmounts() {
        val budget = 100_000L

        // Under budget
        val spentUnder = 65_000L
        assertEquals(35_000L, BudgetPace.remaining(budget, spentUnder))
        assertEquals(0L, BudgetPace.overAmount(budget, spentUnder))

        // Exactly at budget
        val spentExact = 100_000L
        assertEquals(0L, BudgetPace.remaining(budget, spentExact))
        assertEquals(0L, BudgetPace.overAmount(budget, spentExact))

        // Over budget
        val spentOver = 125_000L
        assertEquals(-25_000L, BudgetPace.remaining(budget, spentOver))
        assertEquals(25_000L, BudgetPace.overAmount(budget, spentOver))
    }

    @Test
    fun daysLeftCurrentMonthFirstMidLastDay() {
        val nov = YearMonth.of(2026, 11) // 30 days

        // Day 1: inclusive of today -> 30 days left
        val day1 = LocalDate.of(2026, 11, 1)
        assertEquals(30, BudgetPace.daysLeft(nov, day1))

        // Day 15: 30 - 15 + 1 = 16 days left
        val day15 = LocalDate.of(2026, 11, 15)
        assertEquals(16, BudgetPace.daysLeft(nov, day15))

        // Day 30: 30 - 30 + 1 = 1 day left (today)
        val day30 = LocalDate.of(2026, 11, 30)
        assertEquals(1, BudgetPace.daysLeft(nov, day30))

        // Helper overload
        assertEquals(30, BudgetPace.daysLeft(1, 30))
        assertEquals(16, BudgetPace.daysLeft(15, 30))
        assertEquals(1, BudgetPace.daysLeft(30, 30))
    }

    @Test
    fun daysLeftPastAndFutureMonths() {
        val today = LocalDate.of(2026, 11, 15)

        // Past month -> 0 days left
        val past = YearMonth.of(2026, 10)
        assertEquals(0, BudgetPace.daysLeft(past, today))

        // Future month -> full month length (Dec has 31 days)
        val futureDec = YearMonth.of(2026, 12)
        assertEquals(31, BudgetPace.daysLeft(futureDec, today))

        // Future month (Feb leap year check: 2028 has 29 days)
        val futureFeb = YearMonth.of(2028, 2)
        assertEquals(29, BudgetPace.daysLeft(futureFeb, today))
    }

    @Test
    fun paceRatioDay1MidLast() {
        val daysInMonth = 30

        // Day 1
        assertEquals(1.0 / 30.0, BudgetPace.paceRatio(1, daysInMonth), 0.0001)

        // Mid day (15)
        assertEquals(0.5, BudgetPace.paceRatio(15, daysInMonth), 0.0001)

        // Last day (30)
        assertEquals(1.0, BudgetPace.paceRatio(30, daysInMonth), 0.0001)
    }

    @Test
    fun paceRatioPastAndFutureMonths() {
        val today = LocalDate.of(2026, 11, 15)

        // Past month: pace is at the end (1.0)
        assertEquals(1.0, BudgetPace.paceRatio(YearMonth.of(2026, 10), today)!!, 0.0001)

        // Current month: 15 / 30 = 0.5
        assertEquals(0.5, BudgetPace.paceRatio(YearMonth.of(2026, 11), today)!!, 0.0001)

        // Future month: null (marker hidden)
        assertNull(BudgetPace.paceRatio(YearMonth.of(2026, 12), today))
    }

    @Test
    fun expectedSpentAndSpentRatio() {
        val budget = 200_000L

        // Halfway through month at pace 0.5
        assertEquals(100_000L, BudgetPace.expectedSpent(budget, 0.5))

        // Day 10 of 30 days -> 1/3 of budget
        assertEquals(66_667L, BudgetPace.expectedSpent(budget, 10, 30))

        // Spent ratio
        assertEquals(0.5, BudgetPace.spentRatio(budget, 100_000L), 0.0001)
        assertEquals(1.25, BudgetPace.spentRatio(budget, 250_000L), 0.0001)
        assertEquals(0.0, BudgetPace.spentRatio(0L, 50_000L), 0.0001)
    }
}
