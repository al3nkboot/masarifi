package com.midroid.masarifi.bills

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

enum class BillState { OVERDUE, DUE_TODAY, SOON, UPCOMING, INACTIVE }
enum class ReminderKind { UPCOMING, DUE_TODAY, OVERDUE }
data class Reminder(val bill: Bill, val kind: ReminderKind, val days: Int)

/** One (possibly future) occurrence of a bill; [paid] when it comes from the payment history. */
data class BillOccurrence(val bill: Bill, val date: LocalDate, val amount: Long, val paid: Boolean)

/** Pure bill rules (no Android). */
object BillLogic {
    fun daysLeft(b: Bill, today: LocalDate) = ChronoUnit.DAYS.between(today, b.dueDate).toInt()

    fun state(b: Bill, today: LocalDate): BillState {
        if (!b.active) return BillState.INACTIVE
        val d = daysLeft(b, today)
        return when {
            d < 0 -> BillState.OVERDUE
            d == 0 -> BillState.DUE_TODAY
            d <= maxOf(b.reminderDays, 3) -> BillState.SOON
            else -> BillState.UPCOMING
        }
    }

    /** Bills to notify about today (reminder N days before, due today, overdue); skips inactive / already notified. */
    fun reminders(bills: List<Bill>, today: LocalDate, alreadyNotified: Set<Long>): List<Reminder> =
        bills.asSequence().filter { it.active && it.id !in alreadyNotified }.mapNotNull { b ->
            val days = daysLeft(b, today)
            when {
                days < 0 -> Reminder(b, ReminderKind.OVERDUE, -days)
                days == 0 -> Reminder(b, ReminderKind.DUE_TODAY, 0)
                days <= b.reminderDays -> Reminder(b, ReminderKind.UPCOMING, days)
                else -> null
            }
        }.sortedBy { it.bill.nextDue }.toList()

    /** Unpaid occurrences of active bills in [from, to] (starting at each bill's next due date, so overdue ones count). */
    fun unpaid(bills: List<Bill>, from: LocalDate, to: LocalDate): List<BillOccurrence> = buildList {
        bills.filter { it.active }.forEach { b ->
            var d: LocalDate? = b.dueDate; var guard = 0
            while (d != null && !d.isAfter(to) && guard++ < 2000) {
                if (!d.isBefore(from)) add(BillOccurrence(b, d, b.amount, false))
                d = b.nextAfter(d)
            }
        }
    }.sortedWith(compareBy({ it.date }, { it.bill.name }))

    /**
     * Bills still to pay in month [ym] as seen on [today]: for the current month every unpaid occurrence up to the
     * month end including overdue ones from earlier months; for a future month its occurrences; past months: none.
     */
    fun upcomingInMonth(bills: List<Bill>, today: LocalDate, ym: YearMonth): List<BillOccurrence> {
        val cur = YearMonth.from(today)
        return when {
            ym.isBefore(cur) -> emptyList()
            ym == cur -> unpaid(bills, LocalDate.of(1970, 1, 1), ym.atEndOfMonth())
            else -> unpaid(bills, ym.atDay(1), ym.atEndOfMonth())
        }
    }

    /** Calendar items per day: unpaid occurrences + payments by the due date they covered. */
    fun calendar(bills: List<Bill>, payments: List<BillPayment>, ym: YearMonth): Map<LocalDate, List<BillOccurrence>> {
        val from = ym.atDay(1); val to = ym.atEndOfMonth()
        val byId = bills.associateBy { it.id }
        val out = unpaid(bills, from, to).toMutableList()
        payments.forEach { p ->
            val d = LocalDate.ofEpochDay(p.dueDate)
            if (!d.isBefore(from) && !d.isAfter(to)) byId[p.billId]?.let { out += BillOccurrence(it, d, p.amount, true) }
        }
        return out.groupBy { it.date }.mapValues { (_, l) -> l.sortedWith(compareBy({ it.paid }, { it.bill.name })) }
    }

    /** Month grid cells (null = padding), length a multiple of 7. */
    fun monthGrid(ym: YearMonth, first: DayOfWeek): List<LocalDate?> {
        val lead = (ym.atDay(1).dayOfWeek.value - first.value + 7) % 7
        val cells = MutableList<LocalDate?>(lead) { null }
        for (d in 1..ym.lengthOfMonth()) cells += ym.atDay(d)
        while (cells.size % 7 != 0) cells += null
        return cells
    }

    /** State of a bill after paying its current occurrence: next due date, or inactive for one-time bills. */
    fun afterPayment(b: Bill, now: Long): Bill {
        val next = b.nextAfter(b.dueDate)
        return if (next == null) b.copy(active = false, updatedAt = now) else b.copy(nextDue = next.toEpochDay(), updatedAt = now)
    }

    /** Widget rows: the next [n] active bills by due date. */
    fun nextBills(bills: List<Bill>, n: Int = 3) = bills.filter { it.active }.sortedWith(compareBy({ it.nextDue }, { it.name })).take(n)
}
