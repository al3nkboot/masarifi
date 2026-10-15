package com.midroid.masarifi.sms

import java.math.BigDecimal

/**
 * Direction sanity check from the balance printed in the SMS.
 * Some wordings are ambiguous (e.g. Bank Dhofar «قد أدخل فيه مبلغ» = "an amount was entered into it"), so when the
 * previous known balance of the same account is available:
 *   new == previous − amount → DEBIT,   new == previous + amount → CREDIT,   otherwise the parsed direction is kept.
 */
object BalanceCheck {
    private val TOLERANCE = BigDecimal("0.0005")

    data class Result(val type: SmsTxnType, val flipped: Boolean)

    fun resolve(parsed: SmsTxnType, amount: BigDecimal, previous: BigDecimal?, current: BigDecimal?): Result {
        if (previous == null || current == null || amount.signum() <= 0) return Result(parsed, false)
        val debit = current.subtract(previous.subtract(amount)).abs() <= TOLERANCE
        val credit = current.subtract(previous.add(amount)).abs() <= TOLERANCE
        val type = when {
            debit && !credit -> SmsTxnType.DEBIT
            credit && !debit -> SmsTxnType.CREDIT
            else -> parsed
        }
        return Result(type, type != parsed)
    }
}
