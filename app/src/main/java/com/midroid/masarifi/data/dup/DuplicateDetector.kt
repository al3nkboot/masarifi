package com.midroid.masarifi.data.dup

import com.midroid.masarifi.data.DupDismissed
import com.midroid.masarifi.data.SmsLink
import com.midroid.masarifi.data.Txn
import com.midroid.masarifi.data.TxnType
import kotlin.math.abs

/** Result of matching a freshly parsed bank SMS against existing transactions. */
sealed interface SmsMatch {
    data object None : SmsMatch
    /** Exactly one manual transaction matches inside the window → attach the SMS to it. */
    data class Link(val txn: Txn) : SmsMatch
    /** Ambiguous (several in window) or weak (same amount, outside window but ≤ 7 days) → create + flag. */
    data class Flag(val candidates: List<Txn>, val strong: Boolean) : SmsMatch
}

enum class DupReason { SMS_MATCH, EXACT }

data class DupGroup(val txns: List<Txn>, val reason: DupReason) {
    val ids: List<Long> get() = txns.map { it.id }
    val key: String get() = ids.sorted().joinToString(",")
}

/** What a "merge" does: keep [keeper] (updated), delete [deleteIds], attach [links] (SMS metadata of deleted SMS rows). */
data class MergePlan(val keeper: Txn, val deleteIds: List<Long>, val links: List<SmsLink>)

/**
 * Pure duplicate-detection rules (no Android/Room), shared by SMS import, the add screen and the review screen.
 * "Manual" = anything not created from an SMS (Money Manager imports and CSV restores count as manual).
 */
object DuplicateDetector {
    const val HOUR = 3_600_000L
    const val WEAK_WINDOW = 7 * 24 * HOUR
    const val EXACT_WINDOW = 10 * 60_000L
    val WINDOW_CHOICES = listOf(24, 48, 72)

    /** An entry that came from (or absorbed) a bank SMS. */
    fun isSms(t: Txn): Boolean = t.isAuto || t.smsLinked || (t.smsHash != null && !t.smsHash.startsWith("mm:"))

    private fun dist(a: Long, b: Long) = abs(a - b)

    /** Matching rule for an incoming SMS (type, amount in base-currency thousandths, time). Transfers never match. */
    fun matchSms(type: Int, amount: Long, time: Long, existing: List<Txn>, windowHours: Int, billTxnIds: Set<Long> = emptySet()): SmsMatch {
        if (type == TxnType.TRANSFER || amount <= 0) return SmsMatch.None
        val pool = existing.filter { !isSms(it) && it.type == type && it.amount == amount }
        val strong = pool.filter { dist(it.dateTime, time) <= windowHours * HOUR }.sortedBy { dist(it.dateTime, time) }
        // A bill marked «تم الدفع» is matched by the bank SMS of that payment over the whole weak window (banks often
        // debit days before / after): a single bill payment with no other manual candidate is merged (no double count,
        // undo = split); otherwise the SMS is flagged so the duplicates screen offers the merge.
        val bill = pool.filter { it.id in billTxnIds && dist(it.dateTime, time) <= WEAK_WINDOW }
        if (bill.size == 1 && strong.none { it.id !in billTxnIds }) return SmsMatch.Link(bill.first())
        if (bill.isNotEmpty()) return SmsMatch.Flag((strong + bill).distinct().sortedBy { dist(it.dateTime, time) }, strong = true)
        if (strong.size == 1) return SmsMatch.Link(strong.first())
        if (strong.size > 1) return SmsMatch.Flag(strong, strong = true)
        val weak = pool.filter { dist(it.dateTime, time) <= WEAK_WINDOW }.sortedBy { dist(it.dateTime, time) }
        return if (weak.isNotEmpty()) SmsMatch.Flag(weak, strong = false) else SmsMatch.None
    }

    /**
     * Existing transactions that look like the one being added manually: same type + amount within the window.
     * Transfers only match transfers between the same two accounts.
     */
    fun manualConflicts(draft: Txn, existing: List<Txn>, windowHours: Int): List<Txn> =
        existing.filter {
            it.id != draft.id && it.type == draft.type && it.amount == draft.amount && draft.amount > 0 &&
                dist(it.dateTime, draft.dateTime) <= windowHours * HOUR &&
                (draft.type != TxnType.TRANSFER || (it.accountId == draft.accountId && it.toAccountId == draft.toAccountId))
        }.sortedWith(compareByDescending<Txn> { isSms(it) }.thenBy { dist(it.dateTime, draft.dateTime) })

    private fun exactSame(a: Txn, b: Txn) =
        a.type == b.type && a.amount == b.amount && a.accountId == b.accountId && a.toAccountId == b.toAccountId &&
            a.categoryId == b.categoryId && a.note.trim() == b.note.trim() && dist(a.dateTime, b.dateTime) <= EXACT_WINDOW

    /**
     * Scans all data for possible duplicates:
     *  - SMS entry vs manual entry: same type + amount within the window,
     *  - generic exact duplicates: same type, amount, account(s), category and note within 10 minutes,
     *  - pairs flagged at SMS time ([Txn.possibleDupOf]).
     * Pairs the user dismissed ("keep both") are skipped. Overlapping pairs are joined into groups.
     */
    fun scan(txns: List<Txn>, windowHours: Int, dismissed: Set<String>, billTxnIds: Set<Long> = emptySet()): List<DupGroup> {
        val byId = txns.associateBy { it.id }
        val pairs = LinkedHashMap<String, Pair<Long, Long>>()
        val smsPairs = HashSet<String>()
        fun add(a: Long, b: Long, sms: Boolean) {
            if (a == b) return
            val k = DupDismissed.key(a, b)
            if (k in dismissed) return
            pairs[k] = minOf(a, b) to maxOf(a, b)
            if (sms) smsPairs += k
        }
        val window = windowHours * HOUR
        for ((_, bucket) in txns.groupBy { it.type to it.amount }) {
            if (bucket.size < 2) continue
            val sorted = bucket.sortedBy { it.dateTime }
            for (i in sorted.indices) {
                val a = sorted[i]
                var j = i + 1
                while (j < sorted.size && sorted[j].dateTime - a.dateTime <= window) {
                    val b = sorted[j]
                    if (a.type != TxnType.TRANSFER && isSms(a) != isSms(b)) add(a.id, b.id, sms = true)
                    if (exactSame(a, b)) add(a.id, b.id, sms = false)
                    j++
                }
            }
        }
        // bill payments vs bank SMS of the same amount within the weak window (see [matchSms])
        if (billTxnIds.isNotEmpty()) {
            val bills = txns.filter { it.id in billTxnIds && !isSms(it) && it.type != TxnType.TRANSFER }
            for (b in bills) for (o in txns) {
                if (o.id != b.id && isSms(o) && o.type == b.type && o.amount == b.amount && dist(o.dateTime, b.dateTime) <= WEAK_WINDOW) add(b.id, o.id, sms = true)
            }
        }
        for (t in txns) {
            t.possibleDupOf?.split(',')?.mapNotNull { it.trim().toLongOrNull() }?.forEach { other -> if (byId.containsKey(other)) add(t.id, other, sms = true) }
        }
        // union-find
        val parent = HashMap<Long, Long>()
        fun find(x: Long): Long { var r = x; while (parent[r] != null && parent[r] != r) r = parent[r]!!; parent[x] = r; return r }
        for ((a, b) in pairs.values) { parent.putIfAbsent(a, a); parent.putIfAbsent(b, b); val ra = find(a); val rb = find(b); if (ra != rb) parent[ra] = rb }
        val groups = parent.keys.groupBy { find(it) }.values
        return groups.map { ids ->
            val members = ids.mapNotNull { byId[it] }.sortedBy { it.dateTime }
            val sms = pairs.keys.any { k -> k in smsPairs && k.split('-').all { it.toLong() in ids } }
            DupGroup(members, if (sms) DupReason.SMS_MATCH else DupReason.EXACT)
        }.filter { it.txns.size >= 2 }.sortedByDescending { g -> g.txns.maxOf { it.dateTime } }
    }

    /** All pair keys inside a group (used for "keep both"). */
    fun pairKeys(ids: List<Long>): List<String> = buildList { for (i in ids.indices) for (j in i + 1 until ids.size) add(DupDismissed.key(ids[i], ids[j])) }

    /** Builds an SmsLink record (to keep the SMS metadata + allow undo) from an SMS transaction being folded into [keeper]. */
    fun linkFrom(sms: Txn, keeper: Txn, now: Long = System.currentTimeMillis()) = SmsLink(
        txnId = keeper.id, smsHash = sms.smsHash ?: "merged:${sms.id}:${sms.dateTime}", type = sms.type, amount = sms.amount,
        accountId = sms.accountId, categoryId = sms.categoryId, note = sms.note, description = sms.description, dateTime = sms.dateTime,
        merchant = sms.merchant, prevMerchant = keeper.merchant, createdAt = now,
    )

    /**
     * Merge: keep the manual entry (category/note/account/date), take the SMS metadata (merchant + link record) and delete
     * the rest. Without a manual entry the oldest one is kept.
     */
    fun planMerge(group: List<Txn>, now: Long = System.currentTimeMillis()): MergePlan {
        require(group.size >= 2)
        val manual = group.filter { !isSms(it) }
        val keeper0 = manual.minByOrNull { it.id } ?: group.minBy { it.id }
        val others = group.filter { it.id != keeper0.id }
        val smsOthers = others.filter { isSms(it) && it.smsHash != null && !it.smsHash.startsWith("mm:") }
        val links = smsOthers.map { linkFrom(it, keeper0, now) }
        val keeper = keeper0.copy(
            smsLinked = keeper0.smsLinked || (links.isNotEmpty() && !keeper0.isAuto),
            merchant = keeper0.merchant ?: smsOthers.firstNotNullOfOrNull { it.merchant },
            possibleDupOf = null,
        )
        return MergePlan(keeper, others.map { it.id }, links)
    }

    /** Recreates the separate SMS transaction from a link (undo / split). */
    fun splitTxn(link: SmsLink) = Txn(
        type = link.type, amount = link.amount, accountId = link.accountId, categoryId = link.categoryId, note = link.note,
        description = link.description, dateTime = link.dateTime, isAuto = true,
        smsHash = link.smsHash.takeUnless { it.startsWith("merged:") }, merchant = link.merchant,
    )
}
