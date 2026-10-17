package com.midroid.masarifi.bills

import com.midroid.masarifi.bills.Recurrence
import com.midroid.masarifi.bills.RecurrenceType.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class RecurrenceTest {
    private fun d(s: String) = LocalDate.parse(s)

    @Test fun monthEndKeepsAnchorNonLeap() {
        val a = 31
        val feb = Recurrence.next(d("2026-01-31"), MONTHLY, 1, a)!!
        assertEquals(d("2026-02-28"), feb)
        val mar = Recurrence.next(feb, MONTHLY, 1, a)!!
        assertEquals(d("2026-03-31"), mar)
        assertEquals(d("2026-04-30"), Recurrence.next(mar, MONTHLY, 1, a))
    }
    @Test fun monthEndLeapYear() {
        val feb = Recurrence.next(d("2028-01-31"), MONTHLY, 1, 31)!!
        assertEquals(d("2028-02-29"), feb)
        assertEquals(d("2028-03-31"), Recurrence.next(feb, MONTHLY, 1, 31))
    }
    @Test fun anchor30And29() {
        assertEquals(d("2026-02-28"), Recurrence.next(d("2026-01-30"), MONTHLY, 1, 30))
        assertEquals(d("2026-03-30"), Recurrence.next(d("2026-02-28"), MONTHLY, 1, 30))
        assertEquals(d("2026-03-29"), Recurrence.next(d("2026-02-28"), MONTHLY, 1, 29))
    }
    @Test fun normalMonthly() = assertEquals(d("2026-11-15"), Recurrence.next(d("2026-10-15"), MONTHLY))
    @Test fun decemberRollsYear() = assertEquals(d("2027-01-31"), Recurrence.next(d("2026-12-31"), MONTHLY, 1, 31))
    @Test fun quarterly() {
        assertEquals(d("2026-04-30"), Recurrence.next(d("2026-01-31"), QUARTERLY, 1, 31))
        assertEquals(d("2026-07-31"), Recurrence.next(d("2026-04-30"), QUARTERLY, 1, 31))
        assertEquals(d("2027-02-28"), Recurrence.next(d("2026-11-30"), QUARTERLY, 1, 30))
    }
    @Test fun yearlyFeb29() {
        var x = d("2024-02-29")
        val expect = listOf("2025-02-28", "2026-02-28", "2027-02-28", "2028-02-29", "2029-02-28")
        for (e in expect) { x = Recurrence.next(x, YEARLY, 1, 29)!!; assertEquals(d(e), x) }
    }
    @Test fun weekly() = assertEquals(d("2026-03-03"), Recurrence.next(d("2026-02-24"), WEEKLY))
    @Test fun customDays() {
        assertEquals(d("2026-03-02"), Recurrence.next(d("2026-02-20"), CUSTOM_DAYS, 10))
        assertEquals(d("2029-02-28"), Recurrence.next(d("2028-02-29"), CUSTOM_DAYS, 365))
    }
    @Test fun customMonths() {
        assertEquals(d("2026-08-31"), Recurrence.next(d("2026-02-28"), CUSTOM_MONTHS, 6, 31))
        assertEquals(d("2027-02-28"), Recurrence.next(d("2026-08-31"), CUSTOM_MONTHS, 6, 31))
        assertEquals(d("2028-02-29"), Recurrence.next(d("2026-08-31"), CUSTOM_MONTHS, 18, 31))
    }
    @Test fun customZeroIntervalTreatedAsOne() = assertEquals(d("2026-01-02"), Recurrence.next(d("2026-01-01"), CUSTOM_DAYS, 0))
    @Test fun oneTimeHasNoNext() {
        assertNull(Recurrence.next(d("2026-01-01"), ONE_TIME))
        assertNull(Recurrence.nextAfter(d("2026-01-01"), d("2026-02-01"), ONE_TIME))
        assertEquals(d("2026-03-01"), Recurrence.nextAfter(d("2026-03-01"), d("2026-02-01"), ONE_TIME))
    }
    @Test fun overdueCatchUpMonthly() {
        val today = d("2026-10-16")
        assertEquals(d("2026-10-31"), Recurrence.nextAfter(d("2026-01-31"), today, MONTHLY, 1, 31))
        assertEquals(9, Recurrence.missedCount(d("2026-01-31"), today, MONTHLY, 1, 31))
        assertEquals(d("2026-11-16"), Recurrence.nextAfter(d("2026-08-16"), today, MONTHLY, 1, 16)) // due today counts as handled
    }
    @Test fun overdueCatchUpWeeklyAndYearly() {
        assertEquals(d("2026-10-20"), Recurrence.nextAfter(d("2026-09-01"), d("2026-10-16"), WEEKLY))
        assertEquals(d("2028-02-29"), Recurrence.nextAfter(d("2024-02-29"), d("2027-03-01"), YEARLY, 1, 29))
    }
    @Test fun notOverdueUnchanged() = assertEquals(d("2026-12-01"), Recurrence.nextAfter(d("2026-12-01"), d("2026-10-16"), MONTHLY))
}
