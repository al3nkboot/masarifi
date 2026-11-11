package com.midroid.masarifi.analysis

import com.midroid.masarifi.data.Txn
import com.midroid.masarifi.data.TxnType
import com.midroid.masarifi.ui.summary.PeriodChoice
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReportExtrasTest {

    @Test
    fun savingsRate_returnsPercentageOrNull() {
        assertEquals(25.0, ReportExtras.savingsRate(1000, 750)!!, 0.0)
        assertNull(ReportExtras.savingsRate(0, 10))
    }

    @Test
    fun topMerchants_groupsExpensesCaseInsensitivelyAndRespectsPeriod() {
        val period = SummaryPeriod(
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 2, 1),
        )
        val insideMillis = Instant.parse("2026-01-15T00:00:00Z").toEpochMilli()
        val outsideMillis = Instant.parse("2026-02-03T00:00:00Z").toEpochMilli()

        val rows = ReportExtras.topMerchants(
            all = listOf(
                Txn(
                    type = TxnType.EXPENSE,
                    amount = 1000,
                    accountId = 1,
                    note = "",
                    merchant = "LuLu",
                    dateTime = insideMillis,
                ),
                Txn(
                    type = TxnType.EXPENSE,
                    amount = 500,
                    accountId = 1,
                    note = "",
                    merchant = "lulu ",
                    dateTime = insideMillis,
                ),
                Txn(
                    type = TxnType.EXPENSE,
                    amount = 900,
                    accountId = 1,
                    note = "",
                    merchant = "Other",
                    dateTime = insideMillis,
                ),
                Txn(
                    type = TxnType.EXPENSE,
                    amount = 400,
                    accountId = 1,
                    note = "",
                    merchant = "  ",
                    dateTime = insideMillis,
                ),
                Txn(
                    type = TxnType.INCOME,
                    amount = 100_000,
                    accountId = 1,
                    note = "",
                    merchant = "LuLu",
                    dateTime = insideMillis,
                ),
                Txn(
                    type = TxnType.EXPENSE,
                    amount = 7_000,
                    accountId = 1,
                    note = "",
                    merchant = "LuLu",
                    dateTime = outsideMillis,
                ),
            ),
            period = period,
            zone = ZoneOffset.UTC,
        )

        assertEquals(2, rows.size)
        assertEquals("LuLu", rows[0].name)
        assertEquals(2, rows[0].count)
        assertEquals(1500, rows[0].amount)
        assertEquals("Other", rows[1].name)
        assertEquals(1, rows[1].count)
        assertEquals(900, rows[1].amount)
    }

    @Test
    fun periodChoices_calculateExpectedPeriods() {
        val today = LocalDate.of(2026, 11, 11)

        assertEquals(
            SummaryPeriod(LocalDate.of(2026, 11, 7), LocalDate.of(2026, 11, 14)),
            PeriodChoice.ThisWeek.period(today),
        )
        assertEquals(
            SummaryPeriod(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 12, 1)),
            PeriodChoice.Last3Months.period(today),
        )
        assertEquals(
            SummaryPeriod(LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1)),
            PeriodChoice.ThisYear.period(today),
        )
    }
}
