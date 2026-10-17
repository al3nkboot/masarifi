package com.midroid.masarifi.data.sync

import com.midroid.masarifi.data.Defaults

/**
 * Keeps category references intact when two phones know the same category under different ids — e.g. after one
 * of them restored an old backup (which gave every row a new id) or created the same category independently.
 *
 *  1. Live categories with the same identity (type + default key, or type + name for user categories) are merged
 *     into one canonical record (the default "cat-<key>" id if present, else the smallest uuid); the others
 *     become tombstones.
 *  2. Every reference (transactions, repeats, SMS links, merchant rules, budgets) to a merged, deleted or unknown
 *     category is re-linked: by the merged id, else by the category name/key the record carries ("catKey",
 *     "catName", "catNameEn", "catType", written since 1.7.1), else by the key encoded in a default id
 *     ("cat-food"). If the record names a category that no longer exists anywhere it is re-created (deterministic id).
 *
 * Pure and deterministic, so both phones compute the same result. Returns the number of re-linked references.
 */
object CategoryLinks {
    private fun norm(s: String?) = s?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }

    fun identity(type: Long?, key: String?, nameAr: String?, nameEn: String?): String? =
        if (key != null) "$type|k:$key" else (norm(nameAr) ?: norm(nameEn))?.let { "$type|n:$it" }

    private fun identityOf(c: SyncRecord) = identity(c.long("type"), c.str("key"), c.str("nameAr"), c.str("nameEn"))

    fun apply(out: MutableMap<String, SyncRecord>): Int {
        val liveCats = out.values.filter { it.kind == Kind.CATEGORY && !it.deleted }
        // 1. merge duplicates
        val alias = HashMap<String, String>()
        liveCats.groupBy { identityOf(it) }.forEach { (id, group) ->
            if (id == null || group.size < 2) return@forEach
            val canon = group.firstOrNull { c -> c.str("key")?.let { c.uuid == Defaults.categoryUuid(it) } == true } ?: group.minBy { it.uuid }
            group.filter { it !== canon }.forEach { alias[it.uuid] = canon.uuid; out[it.uuid] = it.tombstone(it.updatedAt) }
        }
        val byIdentity = HashMap<String, String>()
        out.values.filter { it.kind == Kind.CATEGORY && !it.deleted }.forEach { c -> identityOf(c)?.let { byIdentity.putIfAbsent(it, c.uuid) } }
        fun live(u: String?) = u != null && out[u]?.let { it.kind == Kind.CATEGORY && !it.deleted } == true

        /** Live category for a dangling reference, or null when nothing identifies it. May create a category. */
        fun resolve(ref: String, r: SyncRecord): String? {
            alias[ref]?.let { return it }
            val type = r.long("catType") ?: r.long("type")?.takeIf { it != 2L }
            val key = r.str("catKey") ?: ref.takeIf { it.startsWith("cat-") && !it.startsWith("cat-auto-") }?.removePrefix("cat-")
            identity(type, key, r.str("catName"), r.str("catNameEn"))?.let { byIdentity[it] }?.let { return it }
            if (key != null) identity(type, null, r.str("catName"), r.str("catNameEn"))?.let { byIdentity[it] }?.let { return it }
            // re-create a user category known only by name
            val name = r.str("catName") ?: r.str("catNameEn") ?: return null
            if (type == null || r.str("catKey") != null) return null
            val uuid = Defaults.namedUuid("cat-auto-$type-", name)
            if (out[uuid]?.deleted == false) return uuid
            out[uuid] = SyncRecord.live(uuid, Kind.CATEGORY, r.updatedAt, "type" to type, "nameAr" to (r.str("catName") ?: name),
                "nameEn" to (r.str("catNameEn") ?: name), "emoji" to "📦", "key" to null, "sortOrder" to 50L)
            identity(type, null, r.str("catName"), r.str("catNameEn"))?.let { byIdentity[it] = uuid }
            return uuid
        }

        var relinked = 0
        for (r in out.values.toList()) {
            if (r.deleted) continue
            when (r.kind) {
                Kind.TXN, Kind.RECURRING, Kind.SMS_LINK, Kind.MERCHANT, Kind.BILL -> {
                    val ref = r.str("category") ?: continue
                    if (live(ref) && ref !in alias) continue
                    val to = resolve(ref, r) ?: continue
                    out[r.uuid] = r.copy(fields = r.fields + ("category" to to)); relinked++
                }
                Kind.BUDGET -> {
                    val ref = r.str("category") ?: continue
                    if (live(ref) && ref !in alias) continue
                    val to = alias[ref] ?: (ref.takeIf { it.startsWith("cat-") && !it.startsWith("cat-auto-") }?.let { u ->
                        out.values.firstOrNull { it.kind == Kind.CATEGORY && !it.deleted && it.str("key") == u.removePrefix("cat-") }?.uuid
                    }) ?: continue
                    val nu = SyncService.budgetUuid(to)
                    if (out[nu]?.deleted != false) out[nu] = SyncRecord.live(nu, Kind.BUDGET, r.updatedAt, "category" to to, "amount" to r.long("amount"))
                    out[r.uuid] = r.tombstone(r.updatedAt); relinked++
                }
            }
        }
        return relinked
    }

    /**
     * Same for accounts: live accounts with the same name and group are one account (canonical: the default
     * "acc-cash" / name-based "acc-auto-…" id, else the smallest uuid); references to merged or deleted default ids are
     * moved to the live account with that identity, so a copy of the data never loses its transactions to the
     * "account deleted" cascade only because the other phone knows the account under another id.
     */
    fun applyAccounts(out: MutableMap<String, SyncRecord>): Int {
        fun ident(a: SyncRecord) = norm(a.str("name"))?.let { "${a.long("groupType")}|$it" }
        fun stable(a: SyncRecord) = a.uuid == Defaults.CASH_ACCOUNT_UUID || a.uuid == Defaults.namedUuid("acc-auto-", a.str("name") ?: "")
        val alias = HashMap<String, String>()
        out.values.filter { it.kind == Kind.ACCOUNT && !it.deleted }.groupBy { ident(it) }.forEach { (id, g) ->
            if (id == null || g.size < 2) return@forEach
            val canon = g.firstOrNull { stable(it) } ?: g.minBy { it.uuid }
            g.filter { it !== canon }.forEach { alias[it.uuid] = canon.uuid; out[it.uuid] = it.tombstone(it.updatedAt) }
        }
        val liveAcc = out.values.filter { it.kind == Kind.ACCOUNT && !it.deleted }
        fun resolve(ref: String): String? {
            alias[ref]?.let { return it }
            if (out[ref]?.deleted == false) return null
            if (ref == Defaults.CASH_ACCOUNT_UUID)
                return liveAcc.filter { it.long("groupType") == 0L && (it.str("name") == "نقد" || it.str("name") == "Cash") }.minByOrNull { it.uuid }?.uuid
            if (ref.startsWith("acc-auto-")) return liveAcc.firstOrNull { Defaults.namedUuid("acc-auto-", it.str("name") ?: "") == ref }?.uuid
            return null
        }
        var n = 0
        for (r in out.values.toList()) {
            if (r.deleted || r.kind !in setOf(Kind.TXN, Kind.RECURRING, Kind.SMS_LINK)) continue
            var f = r.fields
            for (k in listOf("account", "toAccount")) { val ref = r.str(k) ?: continue; resolve(ref)?.let { f = f + (k to it) } }
            if (f != r.fields) { out[r.uuid] = r.copy(fields = f); n++ }
        }
        return n
    }
}
