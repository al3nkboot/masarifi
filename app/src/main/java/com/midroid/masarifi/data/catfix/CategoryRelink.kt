package com.midroid.masarifi.data.catfix

import com.midroid.masarifi.data.Category
import com.midroid.masarifi.data.MerchantRule
import com.midroid.masarifi.data.Repository
import com.midroid.masarifi.data.Txn
import com.midroid.masarifi.data.TxnType
import com.midroid.masarifi.data.backup.BackupData
import com.midroid.masarifi.sms.MerchantCategorizer

/**
 * Re-links «بدون فئة» transactions on a phone whose categories got lost (restore / sync of 1.7.0 and earlier).
 *
 * Sources, in order of reliability:
 *  1. [backup] (a backup file of the other phone): the same transaction (uuid → SMS fingerprint → same type, amount,
 *     time ±2 min and account/merchant) gets the category it has there. Missing categories are created
 *     (with the backup's id, else matched by key / name), and missing merchant rules are added.
 *  2. Merchant rules already on this phone (the user's own earlier choices for that merchant) — SMS rows only.
 * Only rows without a (valid) category are touched; manual choices are never overwritten. Transfers have no category.
 */
class CategoryRelink(private val repo: Repository) {
    data class Plan(
        /** txn id → category to set (an existing id, or a negative placeholder for [newCategories]). */
        val fromBackup: Map<Long, Long>,
        val fromRules: Map<Long, Long>,
        val newCategories: Map<Long, Category>,
        val newRules: List<MerchantRule>,
        /** Uncategorized (non-transfer) rows before the repair. */
        val uncategorized: Int,
    ) {
        val remaining get() = uncategorized - fromBackup.size - fromRules.size
        val total get() = fromBackup.size + fromRules.size
    }

    private fun uncategorized(txns: List<Txn>, catIds: Set<Long>) =
        txns.filter { it.type != TxnType.TRANSFER && (it.categoryId == null || it.categoryId !in catIds) }

    suspend fun plan(backup: BackupData?): Plan {
        val cats = repo.categoryDao.all()
        val catIds = cats.map { it.id }.toSet()
        val txns = repo.txnDao.all()
        val todo = uncategorized(txns, catIds)
        val fromBackup = LinkedHashMap<Long, Long>()
        val newCats = LinkedHashMap<Long, Category>()
        val newRules = ArrayList<MerchantRule>()
        var placeholder = -1L

        if (backup != null) {
            val bCats = backup.categories.associateBy { it.id }
            val mapped = HashMap<Long, Long>() // backup category id → local id / placeholder
            fun localCat(bc: Category): Long = mapped.getOrPut(bc.id) {
                cats.firstOrNull { it.uuid == bc.uuid }?.id
                    ?: bc.key?.let { k -> cats.firstOrNull { it.key == k && it.type == bc.type }?.id }
                    ?: cats.firstOrNull { it.type == bc.type && it.key == null && (it.nameAr.trim() == bc.nameAr.trim() || it.nameEn.trim().equals(bc.nameEn.trim(), true)) }?.id
                    ?: newCats.entries.firstOrNull { it.value.uuid == bc.uuid }?.key
                    ?: (placeholder--).also { newCats[it] = bc.copy(id = 0) }
            }
            val accNames = repo.accountDao.all().associate { it.id to it.name }
            val bAccNames = backup.accounts.associate { it.id to it.name }
            val byUuid = backup.transactions.associateBy { it.uuid }
            val byHash = backup.transactions.filter { it.smsHash != null }.associateBy { it.smsHash!! }
            val bySlot = backup.transactions.groupBy { it.type to it.amount }
            for (t in todo) {
                val b = byUuid[t.uuid] ?: t.smsHash?.let { byHash[it] } ?: bySlot[t.type to t.amount].orEmpty().filter { b ->
                    kotlin.math.abs(b.dateTime - t.dateTime) <= 120_000 &&
                        ((t.merchant != null && b.merchant == t.merchant) || bAccNames[b.accountId] == accNames[t.accountId])
                }.singleOrNull()
                val bc = b?.categoryId?.let { bCats[it] } ?: continue
                if (bc.type != t.type) continue
                fromBackup[t.id] = localCat(bc)
            }
            val rules = repo.merchantRuleDao.all().map { it.merchantKey }.toSet()
            for (r in backup.merchantRules) {
                if (r.merchantKey in rules) continue
                val bc = bCats[r.categoryId] ?: continue
                newRules += r.copy(categoryId = localCat(bc))
            }
        }

        val fromRules = LinkedHashMap<Long, Long>()
        val rules = repo.merchantRuleDao.all().associateBy { it.merchantKey } + newRules.associateBy { it.merchantKey }
        val catType = cats.associate { it.id to it.type } + newCats.mapValues { it.value.type }
        for (t in todo) {
            if (t.id in fromBackup || !t.isAuto) continue
            val m = t.merchant ?: continue
            val r = rules[MerchantCategorizer.merchantKey(m)] ?: continue
            if (catType[r.categoryId] == t.type) fromRules[t.id] = r.categoryId
        }
        return Plan(fromBackup, fromRules, newCats, newRules, todo.size)
    }

    /** Applies [p] in one transaction; returns the number of re-linked transactions. */
    suspend fun apply(p: Plan): Int = repo.inTransaction {
        val ids = HashMap<Long, Long>()
        val existing = repo.categoryDao.all()
        for ((ph, c) in p.newCategories) {
            val clash = existing.any { it.uuid == c.uuid }
            ids[ph] = repo.categoryDao.insert(if (clash) c.copy(uuid = com.midroid.masarifi.data.newUuid()) else c)
        }
        fun real(id: Long) = if (id < 0) ids.getValue(id) else id
        p.newRules.forEach { repo.merchantRuleDao.upsert(it.copy(categoryId = real(it.categoryId))) }
        var n = 0
        for ((tid, cid) in p.fromBackup + p.fromRules) {
            val t = repo.txnDao.get(tid) ?: continue
            repo.txnDao.update(t.copy(categoryId = real(cid))); n++
        }
        n
    }.also { if (it > 0) repo.notifyBulkChange() }
}
