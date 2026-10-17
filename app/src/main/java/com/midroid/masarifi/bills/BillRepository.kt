package com.midroid.masarifi.bills

import com.midroid.masarifi.data.Repository
import com.midroid.masarifi.data.Txn
import com.midroid.masarifi.data.TxnType
import com.midroid.masarifi.data.dup.DuplicateDetector

/** Everything needed to undo a «تم الدفع». */
data class PayResult(val paymentId: Long, val txnId: Long?, val createdTxn: Boolean, val before: Bill, val flaggedDup: Boolean)

/** Bill operations on top of the Masarifi [Repository] (same DB, same transactions). */
class BillRepository(private val repo: Repository) {
    val dao: BillDao get() = repo.database.billDao()

    suspend fun save(b: Bill): Long = if (b.id == 0L) dao.insert(b) else { dao.update(b.copy(updatedAt = System.currentTimeMillis())); b.id }

    /** Deletes the bill and its payment history; expense transactions already recorded stay in Masarifi. */
    suspend fun delete(id: Long) = repo.inTransaction { dao.deletePaymentsFor(id); dao.delete(id) }

    /**
     * Bank-SMS expenses of the same amount within 7 days of [time] that no bill payment uses yet: offered as
     * «دمج» (record the payment on that transaction instead of creating a second one).
     */
    suspend fun smsMatches(amount: Long, time: Long): List<Txn> {
        val used = dao.billTxnIds().toSet()
        return repo.txnDao.between(time - DuplicateDetector.WEAK_WINDOW, time + DuplicateDetector.WEAK_WINDOW + 1)
            .filter { it.type == TxnType.EXPENSE && it.amount == amount && DuplicateDetector.isSms(it) && it.id !in used }
            .sortedBy { kotlin.math.abs(it.dateTime - time) }
    }

    /**
     * «تم الدفع»: records the current occurrence as paid, advances the bill and either creates an expense transaction
     * (account / category chosen by the user) or attaches the payment to [linkTxnId] (an existing bank-SMS expense).
     * A new transaction that looks like an existing SMS one is flagged so the duplicates screen offers a merge.
     */
    suspend fun pay(billId: Long, amount: Long, accountId: Long, categoryId: Long?, paidAt: Long, linkTxnId: Long? = null, now: Long = System.currentTimeMillis()): PayResult? =
        repo.inTransaction {
            val b = dao.get(billId) ?: return@inTransaction null
            var flagged = false
            val txn: Txn = if (linkTxnId != null) {
                repo.txnDao.get(linkTxnId) ?: return@inTransaction null
            } else {
                val dups = smsMatches(amount, paidAt)
                flagged = dups.isNotEmpty()
                val t = Txn(type = TxnType.EXPENSE, amount = amount, accountId = accountId, categoryId = categoryId, note = b.name, dateTime = paidAt,
                    possibleDupOf = dups.takeIf { it.isNotEmpty() }?.joinToString(",") { it.id.toString() })
                t.copy(id = repo.txnDao.insert(t))
            }
            val pid = dao.insertPayment(BillPayment(billId = b.id, amount = amount, dueDate = b.nextDue, paidAt = paidAt, txnUuid = txn.uuid, ownTxn = linkTxnId == null, updatedAt = now))
            dao.update(BillLogic.afterPayment(b, now))
            PayResult(pid, txn.id, linkTxnId == null, b, flagged)
        }.also { if (it?.createdTxn == true) repo.notifyBulkChange() }

    /** Undo of [pay]: removes the payment (and the transaction it created) and restores the bill's due date. */
    suspend fun undoPay(r: PayResult) = repo.inTransaction {
        dao.deletePayment(r.paymentId)
        if (r.createdTxn && r.txnId != null) repo.txnDao.deleteByIds(listOf(r.txnId))
        dao.get(r.before.id)?.let { cur -> dao.update(r.before.copy(updatedAt = maxOf(System.currentTimeMillis(), cur.updatedAt + 1))) }
    }.also { repo.notifyBulkChange() }

    /**
     * Deletes one payment from the history (and its transaction when the payment created it). When it was the
     * latest payment of the bill, the bill goes back to that due date.
     */
    suspend fun deletePayment(id: Long, deleteTxn: Boolean = true) = repo.inTransaction {
        val p = dao.payment(id) ?: return@inTransaction
        dao.deletePayment(id)
        if (deleteTxn && p.ownTxn && p.txnUuid != null) repo.database.syncDao().deleteTxn(p.txnUuid)
        val b = dao.get(p.billId) ?: return@inTransaction
        val latest = dao.paymentsFor(b.id).maxOfOrNull { it.dueDate }
        if (latest == null || latest < p.dueDate) {
            val after = b.nextAfter(java.time.LocalDate.ofEpochDay(p.dueDate))?.toEpochDay()
            if ((after != null && after == b.nextDue) || (after == null && !b.active)) dao.update(b.copy(nextDue = p.dueDate, active = true, updatedAt = System.currentTimeMillis()))
        }
    }.also { repo.notifyBulkChange() }

    /**
     * Imports bills (and payment history, without transactions) from a Mi5G Bills backup. Bills already imported
     * (same uuid) and bills in another currency are skipped. Returns (imported, skipped).
     */
    suspend fun importMi5g(text: String, currency: String): Pair<Int, Int> {
        val cats = repo.categoryDao.all().filter { it.type == TxnType.EXPENSE }
        val parsed = Mi5gImport.parse(text) { key -> cats.firstOrNull { it.key == key }?.id }
        var added = 0; var skipped = 0
        repo.inTransaction {
            parsed.bills.forEach { b ->
                if (dao.byUuid(b.uuid) != null || parsed.currency[b.uuid] != currency) { skipped++; return@forEach }
                val id = dao.insert(b)
                parsed.payments.filter { it.first == b.uuid }.forEach { (_, p) -> if (dao.paymentByUuid(p.uuid) == null) dao.insertPayment(p.copy(billId = id)) }
                added++
            }
        }
        return added to skipped
    }
}
