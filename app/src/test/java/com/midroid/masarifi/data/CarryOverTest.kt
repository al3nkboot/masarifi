package com.midroid.masarifi.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

class CarryOverTest {
    private val zone = ZoneOffset.UTC

    private fun at(year: Int, month: Int, day: Int = 5, id: Long = 0, type: Int, amount: Long) = Txn(
        id = id, type = type, amount = amount, accountId = 1,
        dateTime = LocalDate.of(year, month, day).atStartOfDay().toInstant(zone).toEpochMilli(),
    )

    @Test fun firstMonthHasZeroCarry() {
        val jan = listOf(
            at(2026, 1, 3, 1, TxnType.INCOME, 1000),
            at(2026, 1, 10, 2, TxnType.EXPENSE, 400),
        )
        assertEquals(0L, CarryOver.carryIn(YearMonth.of(2026, 1), jan, enabled = true, zone))
        val tot = CarryOver.totals(YearMonth.of(2026, 1), jan, enabled = true, zone)
        assertEquals(1000L, tot.income)
        assertEquals(400L, tot.expense)
        assertEquals(0L, tot.carry)
        assertEquals(600L, tot.net)
    }

    @Test fun deficitCarriesAsNegative() {
        val txns = listOf(
            at(2026, 1, 1, 1, TxnType.INCOME, 500),
            at(2026, 1, 20, 2, TxnType.EXPENSE, 800),
            at(2026, 2, 4, 3, TxnType.INCOME, 200),
        )
        val feb = CarryOver.totals(YearMonth.of(2026, 2), txns, enabled = true, zone)
        assertEquals(-300L, feb.carry)
        assertEquals(200L, feb.income)
        assertEquals(200L, feb.displayIncome)
        assertEquals(300L, feb.displayExpense)
        assertEquals(-100L, feb.net)
    }

    @Test fun stackedSurplusCarriesThroughEmptyMonth() {
        val txns = listOf(
            at(2026, 1, 2, 1, TxnType.INCOME, 1000),
            at(2026, 1, 15, 2, TxnType.EXPENSE, 400), // remainder 600
            // February is empty — remainder stays 600
            at(2026, 3, 1, 3, TxnType.EXPENSE, 100),
        )
        assertEquals(600L, CarryOver.carryIn(YearMonth.of(2026, 2), txns, enabled = true, zone))
        assertEquals(600L, CarryOver.carryIn(YearMonth.of(2026, 3), txns, enabled = true, zone))
        val mar = CarryOver.totals(YearMonth.of(2026, 3), txns, enabled = true, zone)
        assertEquals(600L, mar.carry)
        assertEquals(500L, mar.net) // 0 - 100 + 600
        // April stacks the unused March remainder: 600 - 100 = 500
        assertEquals(500L, CarryOver.carryIn(YearMonth.of(2026, 4), txns, enabled = true, zone))
    }

    @Test fun monthWithNoDataStillReceivesCarry() {
        val txns = listOf(at(2026, 1, 1, 1, TxnType.INCOME, 250))
        val feb = CarryOver.totals(YearMonth.of(2026, 2), txns, enabled = true, zone)
        assertEquals(250L, feb.carry)
        assertEquals(0L, feb.income)
        assertEquals(0L, feb.expense)
        assertEquals(250L, feb.displayIncome)
        assertEquals(250L, feb.net)
    }

    @Test fun transfersDoNotAffectCarry() {
        val txns = listOf(
            at(2026, 1, 1, 1, TxnType.INCOME, 100),
            Txn(id = 2, type = TxnType.TRANSFER, amount = 80, accountId = 1, toAccountId = 2,
                dateTime = LocalDate.of(2026, 1, 2).atStartOfDay().toInstant(zone).toEpochMilli()),
        )
        assertEquals(100L, CarryOver.carryIn(YearMonth.of(2026, 2), txns, enabled = true, zone))
    }

    @Test fun disabledSettingIsAlwaysZero() {
        val txns = listOf(at(2026, 1, 1, 1, TxnType.INCOME, 900))
        assertEquals(0L, CarryOver.carryIn(YearMonth.of(2026, 2), txns, enabled = false, zone))
        assertEquals(0L, CarryOver.totals(YearMonth.of(2026, 2), txns, enabled = false, zone).carry)
    }
}
