package com.midroid.masarifi.analysis

import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.roundToLong

/**
 * 2.8: Pure math for budget pace, remaining/over amounts, days left and daily spending pace.
 * No Android/Compose dependencies so it can be easily unit tested.
 */
object BudgetPace {

    /**
     * 2.8: Total budget selection:
     * If an overall budget (categoryId == 0) is set and > 0, use that;
     * otherwise use the sum of per-category budgets (categoryId != 0).
     */
    fun totalBudget(overallBudget: Long, categoryBudgets: Collection<Long>): Long =
        if (overallBudget > 0L) overallBudget else categoryBudgets.filter { it > 0L }.sum()

    /** 2.8: Overload accepting a map of categoryId -> budgetAmount (0L is overall). */
    fun totalBudget(budgets: Map<Long, Long>): Long =
        totalBudget(budgets[0L] ?: 0L, budgets.filterKeys { it != 0L }.values)

    /**
     * 2.8: Remaining budget: budget - spent.
     * Can be negative when spending exceeds the budget.
     */
    fun remaining(budget: Long, spent: Long): Long = budget - spent

    /**
     * 2.8: Over-limit amount when spending exceeds budget, or 0 if within budget.
     */
    fun overAmount(budget: Long, spent: Long): Long = maxOf(0L, spent - budget)

    /**
     * 2.8: Days left until month end:
     * - Current month: days in month - dayOfMonth + 1 (inclusive of today).
     * - Past month: 0.
     * - Future month: full length of that month.
     */
    fun daysLeft(selectedMonth: YearMonth, today: LocalDate = LocalDate.now()): Int {
        val currentYm = YearMonth.from(today)
        return when {
            selectedMonth < currentYm -> 0
            selectedMonth > currentYm -> selectedMonth.lengthOfMonth()
            else -> selectedMonth.lengthOfMonth() - today.dayOfMonth + 1
        }
    }

    /** 2.8: Helper for days left given day of month and total days in month. */
    fun daysLeft(dayOfMonth: Int, daysInMonth: Int): Int =
        (daysInMonth - dayOfMonth + 1).coerceAtLeast(0)

    /**
     * 2.8: Expected spending pace ratio:
     * - Current month: dayOfMonth / daysInMonth (1.0 on last day).
     * - Past month: 1.0 (end).
     * - Future month: null (marker hidden).
     */
    fun paceRatio(selectedMonth: YearMonth, today: LocalDate = LocalDate.now()): Double? {
        val currentYm = YearMonth.from(today)
        return when {
            selectedMonth < currentYm -> 1.0
            selectedMonth > currentYm -> null
            else -> paceRatio(today.dayOfMonth, selectedMonth.lengthOfMonth())
        }
    }

    /** 2.8: Direct dayOfMonth / daysInMonth pace ratio. */
    fun paceRatio(dayOfMonth: Int, daysInMonth: Int): Double {
        if (daysInMonth <= 0) return 0.0
        return (dayOfMonth.toDouble() / daysInMonth).coerceIn(0.0, 1.0)
    }

    /**
     * 2.8: Expected spending at the current pace: budget * paceRatio.
     */
    fun expectedSpent(budget: Long, paceRatio: Double): Long =
        (budget * paceRatio.coerceIn(0.0, 1.0)).roundToLong()

    fun expectedSpent(budget: Long, dayOfMonth: Int, daysInMonth: Int): Long =
        expectedSpent(budget, paceRatio(dayOfMonth, daysInMonth))

    /**
     * 2.8: Spending ratio: spent / budget (or 0.0 if budget <= 0).
     */
    fun spentRatio(budget: Long, spent: Long): Double =
        if (budget <= 0L) 0.0 else spent.toDouble() / budget
}
