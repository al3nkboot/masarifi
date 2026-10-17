package com.midroid.masarifi.bills

import java.time.LocalDate
import java.time.YearMonth

enum class RecurrenceType { ONE_TIME, WEEKLY, MONTHLY, QUARTERLY, YEARLY, CUSTOM_DAYS, CUSTOM_MONTHS }

/**
 * Pure recurrence math. Month-based rules keep an [anchorDay] (1..31) so that
 * Jan 31 -> Feb 28/29 -> Mar 31 instead of drifting to the 28th.
 */
object Recurrence {
    /** Months per step for month-based rules, or null for day-based / one-time. */
    fun monthStep(type: RecurrenceType, interval: Int): Int? = when (type) {
        RecurrenceType.MONTHLY -> 1
        RecurrenceType.QUARTERLY -> 3
        RecurrenceType.YEARLY -> 12
        RecurrenceType.CUSTOM_MONTHS -> interval.coerceAtLeast(1)
        else -> null
    }

    fun dayStep(type: RecurrenceType, interval: Int): Int? = when (type) {
        RecurrenceType.WEEKLY -> 7
        RecurrenceType.CUSTOM_DAYS -> interval.coerceAtLeast(1)
        else -> null
    }

    fun withAnchor(ym: YearMonth, anchorDay: Int): LocalDate =
        ym.atDay(anchorDay.coerceIn(1, 31).coerceAtMost(ym.lengthOfMonth()))

    /** The occurrence after [current]. Returns null for one-time bills. */
    fun next(current: LocalDate, type: RecurrenceType, interval: Int = 1, anchorDay: Int = current.dayOfMonth): LocalDate? {
        dayStep(type, interval)?.let { return current.plusDays(it.toLong()) }
        val m = monthStep(type, interval) ?: return null
        return withAnchor(YearMonth.from(current).plusMonths(m.toLong()), anchorDay)
    }

    /**
     * Overdue catch-up: first occurrence strictly after [today] (skipping missed periods).
     * If [current] is already after today it is returned unchanged. One-time -> null when past.
     */
    fun nextAfter(current: LocalDate, today: LocalDate, type: RecurrenceType, interval: Int = 1, anchorDay: Int = current.dayOfMonth): LocalDate? {
        var d = current
        var guard = 0
        while (!d.isAfter(today)) {
            d = next(d, type, interval, anchorDay) ?: return null
            if (++guard > 100_000) error("recurrence did not advance")
        }
        return d
    }

    /** Number of occurrences in [current, today] that are missed (due on or before today). */
    fun missedCount(current: LocalDate, today: LocalDate, type: RecurrenceType, interval: Int = 1, anchorDay: Int = current.dayOfMonth): Int {
        var d: LocalDate? = current; var n = 0
        while (d != null && !d.isAfter(today)) { n++; d = next(d, type, interval, anchorDay) }
        return n
    }
}
