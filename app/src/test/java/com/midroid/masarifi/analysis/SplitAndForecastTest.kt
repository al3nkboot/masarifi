package com.midroid.masarifi.analysis

import com.midroid.masarifi.data.Recurring
import com.midroid.masarifi.data.Repeat
import com.midroid.masarifi.data.TxnType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitAndForecastTest {
    @Test fun splitMustSumToTotalWithCategories() {
        assertTrue(SplitLogic.isValid(10_000, listOf(1L to 6_000L, 2L to 4_000L)))
        assertFalse("sum short", SplitLogic.isValid(10_000, listOf(1L to 6_000L, 2L to 3_999L)))
        assertFalse("sum over", SplitLogic.isValid(10_000, listOf(1L to 6_000L, 2L to 4_001L)))
        assertFalse("missing category", SplitLogic.isValid(10_000, listOf(1L to 6_000L, null to 4_000L)))
        assertFalse("blank amount", SplitLogic.isValid(10_000, listOf(1L to 10_000L, 2L to null)))
        assertFalse("zero part", SplitLogic.isValid(10_000, listOf(1L to 10_000L, 2L to 0L)))
        assertFalse("one part is not a split", SplitLogic.isValid(10_000, listOf(1L to 10_000L)))
        assertEquals(2_500L, SplitLogic.remaining(10_000, listOf(7_500L, null)))
        assertEquals(-1L, SplitLogic.remaining(10_000, listOf(7_500L, 2_501L)))
    }

    private val day = 86_400_000L
    private val advance: (Long, Int) -> Long = { t, f -> t + if (f == Repeat.DAILY) day else 30 * day }
    private fun rule(type: Int, amount: Long, freq: Int, next: Long, active: Boolean = true) =
        Recurring(type = type, amount = amount, accountId = 1, frequency = freq, nextDate = next, active = active)

    @Test fun forecastCountsUpcomingOccurrencesAndBills() {
        val now = 10 * day; val end = 15 * day
        val f = BalanceForecast.compute(
            current = 100_000,
            rules = listOf(
                rule(TxnType.INCOME, 500_000, Repeat.MONTHLY, 12 * day), // once in the window
                rule(TxnType.EXPENSE, 1_000, Repeat.DAILY, 9 * day), // day 9 is past: 10 counts? no (<= now), then 11..15 = 5
                rule(TxnType.EXPENSE, 9_999, Repeat.DAILY, 11 * day, active = false), // inactive
                rule(TxnType.TRANSFER, 7_777, Repeat.DAILY, 11 * day), // transfers ignored
                rule(TxnType.INCOME, 1, Repeat.MONTHLY, 16 * day), // after month end
            ),
            unpaidBills = 20_000, now = now, monthEnd = end, advance = advance,
        )
        assertEquals(500_000L, f.income)
        assertEquals(5 * 1_000L + 20_000L, f.expense)
        assertEquals(100_000L + 500_000L - 25_000L, f.projected)
    }

    @Test fun forecastWithNothingUpcomingIsTodaysBalance() {
        val f = BalanceForecast.compute(-3_000, emptyList(), 0, now = day, monthEnd = 2 * day, advance = advance)
        assertEquals(-3_000L, f.projected)
    }
}
