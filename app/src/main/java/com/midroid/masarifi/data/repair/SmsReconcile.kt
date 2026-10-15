package com.midroid.masarifi.data.repair

import com.midroid.masarifi.data.Txn
import com.midroid.masarifi.data.TxnType
import com.midroid.masarifi.sms.ParsedSms
import com.midroid.masarifi.sms.SmsStyle
import com.midroid.masarifi.sms.SmsTxnType
import kotlin.math.abs

/**
 * Pure rules shared by SMS import and the repair tool:
 *  - double SMS: the same bank sends one "card" and one "account" message for one operation (Bank Dhofar),
 *  - own transfers: money sent from one own account to another (e.g. Bank Muscat → Thawani Mojab card),
 *  - ATM withdrawals → transfer to Cash.
 */
object SmsReconcile {
    /** Max distance between the two messages of one operation (double SMS). */
    const val DOUBLE_WINDOW = 10 * 60_000L
    /** Max distance between the outgoing and the incoming side of an own transfer. */
    const val SELF_WINDOW = 30 * 60_000L

    enum class Double { NONE, MERGE, FLAG }

    /** Direction of an existing SMS transaction for [accountId]: expense/transfer-out = debit, income/transfer-in = credit. */
    fun directionOf(t: Txn): SmsTxnType = if (t.type == TxnType.INCOME) SmsTxnType.CREDIT else SmsTxnType.DEBIT

    /**
     * Is ([b], amount, time, direction) the second message of the operation already recorded as [a] (parsed [pa])?
     * MERGE: same bank + amount + direction within [DOUBLE_WINDOW], one card-style and one account-style message.
     * FLAG: same bank/amount/direction/window but the wording does not prove it (shown in «possible duplicates»).
     */
    fun double(a: Txn, pa: ParsedSms?, b: ParsedSms, amount: Long, time: Long, direction: SmsTxnType): Double {
        if (pa == null || a.amount != amount || abs(a.dateTime - time) > DOUBLE_WINDOW) return Double.NONE
        if (directionOf(a) != direction || pa.type != direction) return Double.NONE
        if (pa.bankId == null || pa.bankId != b.bankId) return Double.NONE
        val known = pa.style != SmsStyle.UNKNOWN && b.style != SmsStyle.UNKNOWN
        return if (known && pa.style != b.style) Double.MERGE else Double.FLAG
    }

    /** Destination digits named in a debit SMS (all masked suffixes except the paying card/account). */
    fun destinationDigits(p: ParsedSms): Set<String> = (p.mentionedLast4 - setOfNotNull(p.cardLast4)).toSet()

    /**
     * Outgoing SMS [debit] (from account [fromId]) → incoming side on own account [toId]?
     * Requires transfer wording and that the destination is the user's own: a wallet account (Thawani/Mojab) or an
     * account whose configured card digits are named in the debit SMS.
     */
    fun ownTransfer(debit: ParsedSms, fromId: Long, toId: Long, toDigits: Set<String>, toIsWallet: Boolean): Boolean =
        debit.type == SmsTxnType.DEBIT && fromId != toId && debit.transferLike &&
            (toIsWallet || destinationDigits(debit).any { it in toDigits })
}
