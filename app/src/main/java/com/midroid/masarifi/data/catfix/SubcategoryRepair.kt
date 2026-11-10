package com.midroid.masarifi.data.catfix

import com.midroid.masarifi.data.Category
import com.midroid.masarifi.data.CategoryName
import com.midroid.masarifi.data.Txn
import com.midroid.masarifi.data.TxnType
import java.util.Locale

/**
 * 2.6.2 one-time repair of Money Manager rows imported before 2.6.1.
 *
 * Before 2.6.1 the importer put the transaction on the PARENT category and prepended the Money Manager
 * subcategory to the description: `description = "<subcategory> • <description> • <CUR amount>"`.
 * 2.6.1 imports into real subcategories, but already-imported rows were skipped as duplicates and stayed
 * on the parent.
 *
 * Conservative rule — a row is moved only when ALL hold:
 *  1. it was imported from Money Manager (`smsHash` starts with "mm:") and is income/expense (not a transfer);
 *  2. its category exists and is top-level (no parent);
 *  3. the first " • " segment of its description, with a leading emoji removed and compared trim + case-insensitive,
 *     equals the Arabic or English name of EXACTLY ONE existing subcategory of that same category and type.
 * Then the row moves to that subcategory and the matched segment is removed from the description (the rest is kept
 * verbatim). No category is created and nothing else is touched; rows that are ambiguous stay as they are.
 */
object SubcategoryRepair {
    const val SEP = " • "

    data class Change(val before: Txn, val after: Txn)

    fun key(s: String): String {
        val p = CategoryName.split(s)
        return p.name.ifBlank { s.trim() }.trim().lowercase(Locale.ROOT)
    }

    /** First description segment and the remainder (null when there is no usable first segment). */
    fun splitHead(description: String): Pair<String, String>? {
        if (description.isBlank()) return null
        val idx = description.indexOf(SEP)
        val head = (if (idx < 0) description else description.substring(0, idx)).trim()
        val rest = if (idx < 0) "" else description.substring(idx + SEP.length)
        return if (head.isEmpty()) null else head to rest
    }

    fun plan(txns: List<Txn>, cats: List<Category>): List<Change> {
        val byId = cats.associateBy { it.id }
        val subsByParent = cats.filter { it.parentId != null }.groupBy { it.parentId!! }
        val out = ArrayList<Change>()
        for (t in txns) {
            if (t.smsHash?.startsWith("mm:") != true) continue
            if (t.type == TxnType.TRANSFER) continue
            val parent = t.categoryId?.let { byId[it] } ?: continue
            if (parent.parentId != null) continue
            val (head, rest) = splitHead(t.description) ?: continue
            val k = key(head)
            if (k.isEmpty()) continue
            val matches = subsByParent[parent.id].orEmpty().filter {
                it.type == t.type && (key(it.nameAr) == k || key(it.nameEn) == k)
            }
            if (matches.size != 1) continue
            out += Change(t, t.copy(categoryId = matches[0].id, description = rest.trim()))
        }
        return out
    }
}
