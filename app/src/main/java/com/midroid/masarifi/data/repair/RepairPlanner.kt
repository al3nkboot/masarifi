package com.midroid.masarifi.data.repair

import com.midroid.masarifi.data.Txn
import com.midroid.masarifi.data.TxnType
import com.midroid.masarifi.sms.ParsedSms
import com.midroid.masarifi.sms.SmsTxnType
import kotlin.math.abs

enum class RepairKind { MERGE_DOUBLE, OWN_TRANSFER, FLIP_INCOME, ATM_CASH }

/**
 * One proposed change to SMS-created transactions. [after] replaces [primary]; [secondary] (if any) is folded into it
 * (deleted, its SMS kept as a link). [after].toAccountId == [CASH_PLACEHOLDER] = the Cash account (created on apply).
 */
data class RepairItem(val kind: RepairKind, val primary: Txn, val after: Txn, val secondary: Txn? = null) {
    val key: String get() = "${kind.name}:${primary.id}:${secondary?.id ?: 0}"
    companion object { const val CASH_PLACEHOLDER = -1L }
}

/** An SMS transaction with its re-parsed SMS (null if the stored text no longer parses). */
data class AutoRow(val txn: Txn, val parsed: ParsedSms?)

/**
 * Pure planner of the «إصلاح العمليات التلقائية» tool. Only rows created from SMS ([Txn.isAuto]) are considered; manual
 * entries are never touched. Each transaction takes part in at most one proposal.
 */
object RepairPlanner {
    fun plan(rows: List<AutoRow>, accountIds: List<Long>, digitsOf: (Long) -> Set<String>, isWallet: (Long) -> Boolean, cashId: Long?): List<RepairItem> {
        val auto = rows.filter { it.txn.isAuto }.sortedBy { it.txn.dateTime }
        val used = HashSet<Long>()
        val out = ArrayList<RepairItem>()

        // 1) double SMS of one operation (card + account message of the same bank)
        for ((i, a) in auto.withIndex()) {
            if (a.txn.id in used) continue
            for (j in i + 1 until auto.size) {
                val b = auto[j]
                if (b.txn.dateTime - a.txn.dateTime > SmsReconcile.DOUBLE_WINDOW) break
                if (b.txn.id in used || b.parsed == null) continue
                val d = SmsReconcile.double(a.txn, a.parsed, b.parsed, b.txn.amount, b.txn.dateTime, SmsReconcile.directionOf(b.txn))
                if (d != SmsReconcile.Double.MERGE) continue
                // keep the transfer if one of them is a (top-up) transfer, else the earlier one
                val (keep, drop) = if (b.txn.type == TxnType.TRANSFER && a.txn.type != TxnType.TRANSFER) b to a else a to b
                out += RepairItem(RepairKind.MERGE_DOUBLE, keep.txn, keep.txn.copy(possibleDupOf = null), drop.txn)
                used += a.txn.id; used += b.txn.id
                break
            }
        }

        // 2) own transfers: outgoing (expense / transfer) + incoming (income) on another own account
        val incomes = auto.filter { it.txn.type == TxnType.INCOME && it.txn.id !in used }
        for (d in auto) {
            val dt = d.txn
            if (dt.id in used || d.parsed == null) continue
            if (dt.type == TxnType.TRANSFER) {
                // the incoming SMS of a transfer that is already recorded (top-up) → counted twice
                val c = incomes.firstOrNull { it.txn.id !in used && it.txn.accountId == dt.toAccountId && it.txn.amount == dt.amount && abs(it.txn.dateTime - dt.dateTime) <= SmsReconcile.SELF_WINDOW }
                if (c != null) { out += RepairItem(RepairKind.OWN_TRANSFER, dt, dt, c.txn); used += dt.id; used += c.txn.id }
                continue
            }
            if (dt.type != TxnType.EXPENSE) continue
            val c = incomes.filter { it.txn.id !in used && it.txn.amount == dt.amount && abs(it.txn.dateTime - dt.dateTime) <= SmsReconcile.SELF_WINDOW }
                .firstOrNull { SmsReconcile.ownTransfer(d.parsed, dt.accountId, it.txn.accountId, digitsOf(it.txn.accountId), isWallet(it.txn.accountId)) }
            if (c != null) {
                out += RepairItem(RepairKind.OWN_TRANSFER, dt, toTransfer(dt, c.txn.accountId), c.txn)
                used += dt.id; used += c.txn.id
            }
        }

        // 3) single rows: own transfer with only one side, wrong direction, ATM
        for (r in auto) {
            val t = r.txn; val p = r.parsed ?: continue
            if (t.id in used || t.type != TxnType.EXPENSE) continue
            val ownDest = SmsReconcile.destinationDigits(p).takeIf { p.transferLike }?.let { ds ->
                accountIds.firstOrNull { it != t.accountId && digitsOf(it).any { d -> d in ds } }
            }
            val item = when {
                p.type == SmsTxnType.CREDIT && p.incomeWording -> RepairItem(RepairKind.FLIP_INCOME, t, t.copy(type = TxnType.INCOME, categoryId = null))
                p.atm -> RepairItem(RepairKind.ATM_CASH, t, toTransfer(t, cashId ?: RepairItem.CASH_PLACEHOLDER))
                ownDest != null -> RepairItem(RepairKind.OWN_TRANSFER, t, toTransfer(t, ownDest))
                else -> null
            } ?: continue
            if (item.after.type == TxnType.TRANSFER && item.after.toAccountId == t.accountId) continue
            out += item; used += t.id
        }
        return out
    }

    private fun toTransfer(t: Txn, to: Long) = t.copy(type = TxnType.TRANSFER, toAccountId = to, categoryId = null, possibleDupOf = null)
}
