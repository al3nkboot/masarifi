package com.midroid.masarifi.bills

import com.midroid.masarifi.data.Txn
import com.midroid.masarifi.data.TxnType
import com.midroid.masarifi.data.dup.DuplicateDetector
import com.midroid.masarifi.data.dup.DupReason
import com.midroid.masarifi.data.dup.SmsMatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class BillLogicTest {
    private val today = LocalDate.parse("2026-10-16")
    private fun b(id: Long, due: String, amount: Long = 1_000, type: RecurrenceType = RecurrenceType.MONTHLY, remind: Int = 3, active: Boolean = true) =
        Bill(id = id, name = "b$id", amount = amount, recurrence = type.name, anchorDay = LocalDate.parse(due).dayOfMonth, nextDue = LocalDate.parse(due).toEpochDay(),
            reminderDays = remind, active = active)

    @Test fun states() {
        assertEquals(BillState.OVERDUE, BillLogic.state(b(1, "2026-10-10"), today))
        assertEquals(BillState.DUE_TODAY, BillLogic.state(b(1, "2026-10-16"), today))
        assertEquals(BillState.SOON, BillLogic.state(b(1, "2026-10-19"), today))
        assertEquals(BillState.UPCOMING, BillLogic.state(b(1, "2026-10-30"), today))
        assertEquals(BillState.INACTIVE, BillLogic.state(b(1, "2026-10-10", active = false), today))
    }

    /** Same selection rules as Mi5G Bills' Reminders.select (without Pro extras). */
    @Test fun reminderSelection() {
        val bills = listOf(b(1, "2026-10-10"), b(2, "2026-10-16"), b(3, "2026-10-19", remind = 3),
            b(4, "2026-10-20", remind = 3), b(5, "2026-10-17", remind = 0), b(6, "2026-10-01", active = false), b(7, "2026-10-18", remind = 7))
        val r = BillLogic.reminders(bills, today, emptySet())
        assertEquals(listOf(1L, 2L, 7L, 3L), r.map { it.bill.id })
        assertEquals(listOf(ReminderKind.OVERDUE, ReminderKind.DUE_TODAY, ReminderKind.UPCOMING, ReminderKind.UPCOMING), r.map { it.kind })
        assertEquals(6, r[0].days); assertEquals(3, r[3].days)
        assertEquals(listOf(2L, 7L, 3L), BillLogic.reminders(bills, today, setOf(1L)).map { it.bill.id })
    }

    @Test fun upcomingThisMonthIncludesOverdueAndRepeats() {
        val bills = listOf(
            b(1, "2026-09-25", 10_000),                                  // overdue Sep 25 + Oct 25
            b(2, "2026-10-20", 250_000),
            b(3, "2026-10-17", 1_000, RecurrenceType.WEEKLY),             // 17, 24, 31
            b(4, "2026-11-05", 20_000),                                  // next month
            b(5, "2026-10-18", 99_000, active = false),
        )
        val cur = BillLogic.upcomingInMonth(bills, today, YearMonth.of(2026, 10))
        assertEquals(20_000L + 250_000 + 3_000, cur.sumOf { it.amount })
        assertEquals(6, cur.size)
        val nov = BillLogic.upcomingInMonth(bills, today, YearMonth.of(2026, 11))
        assertEquals(listOf(4L, 3L, 3L, 2L, 3L, 1L, 3L), nov.map { it.bill.id })
        assertTrue(BillLogic.upcomingInMonth(bills, today, YearMonth.of(2026, 9)).isEmpty())
    }

    @Test fun afterPaymentAdvancesKeepingMonthEnd() {
        val jan = b(1, "2026-01-31").copy(anchorDay = 31)
        val feb = BillLogic.afterPayment(jan, 5)
        assertEquals(LocalDate.parse("2026-02-28"), feb.dueDate)
        assertEquals(LocalDate.parse("2026-03-31"), BillLogic.afterPayment(feb, 6).dueDate)
        val once = BillLogic.afterPayment(b(2, "2026-10-01", type = RecurrenceType.ONE_TIME), 7)
        assertFalse(once.active); assertEquals(LocalDate.parse("2026-10-01"), once.dueDate)
    }

    @Test fun calendarShowsUnpaidAndPaid() {
        val bill = b(1, "2026-10-20")
        val cal = BillLogic.calendar(listOf(bill), listOf(BillPayment(billId = 1, amount = 900, dueDate = LocalDate.parse("2026-09-20").toEpochDay())), YearMonth.of(2026, 10))
        assertEquals(setOf(LocalDate.parse("2026-10-20")), cal.keys)
        val sep = BillLogic.calendar(listOf(bill), listOf(BillPayment(billId = 1, amount = 900, dueDate = LocalDate.parse("2026-09-20").toEpochDay())), YearMonth.of(2026, 9))
        assertTrue(sep.getValue(LocalDate.parse("2026-09-20")).single().paid)
        assertEquals(42, BillLogic.monthGrid(YearMonth.of(2026, 10), java.time.DayOfWeek.SATURDAY).size)
    }

    // ---- duplicate rules for bill payments ----
    private val h = 3_600_000L
    private val t0 = 1_790_000_000_000L
    private fun manual(id: Long, amount: Long, time: Long) = Txn(id = id, type = TxnType.EXPENSE, amount = amount, accountId = 1, dateTime = time)
    private fun sms(id: Long, amount: Long, time: Long) = Txn(id = id, type = TxnType.EXPENSE, amount = amount, accountId = 1, dateTime = time, isAuto = true, smsHash = "h$id")

    @Test fun smsOfABillPaymentDaysLaterIsMergedNotDoubleCounted() {
        val bill = manual(1, 25_000, t0)
        // 4 days later: outside the normal 48 h window, but it is a bill payment → link
        val m = DuplicateDetector.matchSms(TxnType.EXPENSE, 25_000, t0 + 96 * h, listOf(bill), 48, setOf(1L))
        assertEquals(SmsMatch.Link(bill), m)
        // without the bill marker it would only be a weak flag
        assertTrue(DuplicateDetector.matchSms(TxnType.EXPENSE, 25_000, t0 + 96 * h, listOf(bill), 48) is SmsMatch.Flag)
        // a bill payment plus another manual entry in window → ambiguous → flag (offer merge), never silently linked
        val other = manual(2, 25_000, t0 + 95 * h)
        val f = DuplicateDetector.matchSms(TxnType.EXPENSE, 25_000, t0 + 96 * h, listOf(bill, other), 48, setOf(1L))
        assertTrue(f is SmsMatch.Flag && f.strong && f.candidates.map { it.id }.toSet() == setOf(1L, 2L))
    }

    @Test fun scanOffersMergeForBillPaymentAndSms() {
        val txns = listOf(manual(1, 25_000, t0), sms(2, 25_000, t0 + 120 * h))
        assertTrue(DuplicateDetector.scan(txns, 48, emptySet()).isEmpty())
        val g = DuplicateDetector.scan(txns, 48, emptySet(), setOf(1L)).single()
        assertEquals(DupReason.SMS_MATCH, g.reason)
        assertEquals(1L, DuplicateDetector.planMerge(g.txns).keeper.id) // the bill payment is kept, SMS folded in
    }
}
