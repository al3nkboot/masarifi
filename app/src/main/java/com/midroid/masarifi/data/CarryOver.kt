package com.midroid.masarifi.data

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * Computed (never persisted) month-to-month remainder for the Transactions tab.
 * Remainder of month M-1 is added to month M's Trans totals when the setting is on.
 * Stats / Insights never include this amount. Account balances are already cumulative and stay unchanged.
 */
object CarryOver {
    data class PeriodTotals(val income: Long, val expense: Long, val carry: Long) {
        val displayIncome: Long get() = income + maxOf(carry, 0L)
        val displayExpense: Long get() = expense + maxOf(-carry, 0L)
        val net: Long get() = income - expense + carry
    }

    fun dateOf(millis: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    fun monthOf(millis: Long, zone: ZoneId = ZoneId.systemDefault()): YearMonth = YearMonth.from(dateOf(millis, zone))

    fun incomeExpense(txns: List<Txn>): Pair<Long, Long> {
        var income = 0L
        var expense = 0L
        for (t in txns) when (t.type) {
            TxnType.INCOME -> income += t.amount
            TxnType.EXPENSE -> expense += t.amount
        }
        return income to expense
    }

    /**
     * Signed remainder chained from the first month that has an income/expense up to (but not including) [month].
     * Empty months in between keep the previous remainder (a surplus or deficit stacks).
     * Returns 0 when the setting is off, when there is no earlier data, or when [month] is the first month.
     */
    fun carryIn(month: YearMonth, txns: List<Txn>, enabled: Boolean, zone: ZoneId = ZoneId.systemDefault()): Long {
        if (!enabled) return 0L
        val relevant = txns.filter { it.type == TxnType.INCOME || it.type == TxnType.EXPENSE }
        if (relevant.isEmpty()) return 0L
        val first = relevant.minOf { monthOf(it.dateTime, zone) }
        if (!month.isAfter(first)) return 0L
        val byMonth = relevant.groupBy { monthOf(it.dateTime, zone) }
        var carry = 0L
        var m = first
        while (m.isBefore(month)) {
            val (income, expense) = incomeExpense(byMonth[m].orEmpty())
            carry = carry + income - expense
            m = m.plusMonths(1)
        }
        return carry
    }

    fun totals(month: YearMonth, txns: List<Txn>, enabled: Boolean, zone: ZoneId = ZoneId.systemDefault()): PeriodTotals {
        val inMonth = txns.filter { monthOf(it.dateTime, zone) == month }
        val (income, expense) = incomeExpense(inMonth)
        return PeriodTotals(income, expense, carryIn(month, txns, enabled, zone))
    }

    /** Running balance after each transaction of [account], keyed by txn id (oldest first). */
    fun runningBalances(account: Account, txns: List<Txn>): Map<Long, Long> {
        var bal = account.initialBalance
        val map = LinkedHashMap<Long, Long>()
        for (t in txns.sortedWith(compareBy({ it.dateTime }, { it.id }))) {
            when (t.type) {
                TxnType.INCOME -> if (t.accountId == account.id) bal += t.amount
                TxnType.EXPENSE -> if (t.accountId == account.id) bal -= t.amount
                TxnType.TRANSFER -> {
                    if (t.accountId == account.id) bal -= t.amount
                    if (t.toAccountId == account.id) bal += t.amount
                }
            }
            map[t.id] = bal
        }
        return map
    }
}
