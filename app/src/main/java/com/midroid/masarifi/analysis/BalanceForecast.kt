package com.midroid.masarifi.analysis

import com.midroid.masarifi.data.Recurring
import com.midroid.masarifi.data.TxnType

/** End-of-month balance: today's balance + repeating income still to come − repeating expenses still to come − unpaid bills. */
object BalanceForecast {
    data class Forecast(val current: Long, val income: Long, val expense: Long) { val projected: Long get() = current + income - expense }

    /** Counts each occurrence of an active income / expense rule in (now, monthEnd]; transfers move money between own accounts and are ignored. */
    fun compute(current: Long, rules: List<Recurring>, unpaidBills: Long, now: Long, monthEnd: Long, advance: (Long, Int) -> Long): Forecast {
        var income = 0L
        var expense = unpaidBills
        rules.filter { it.active && (it.type == TxnType.INCOME || it.type == TxnType.EXPENSE) }.forEach { r ->
            var t = r.nextDate
            var guard = 0
            while (t <= monthEnd && guard++ < 400) {
                if (t > now) if (r.type == TxnType.INCOME) income += r.amount else expense += r.amount
                t = advance(t, r.frequency)
            }
        }
        return Forecast(current, income, expense)
    }
}
