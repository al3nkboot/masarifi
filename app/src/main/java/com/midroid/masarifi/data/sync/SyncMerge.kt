package com.midroid.masarifi.data.sync

/**
 * Pure (Android-free) merge of two sync snapshots.
 *
 * Rules:
 *  1. Union of entities by uuid.
 *  2. Last-write-wins per entity on its version timestamp (`deletedAt` for tombstones, else `updatedAt`),
 *     compared at one-second resolution (clocks of two phones are never perfectly aligned).
 *  3. Ties (same second): a tombstone beats a live version (a delete is never undone by an equally old edit);
 *     otherwise the LOCAL version wins. The kept local version is re-stamped to the start of the next second, so the
 *     other device sees it as strictly newer and adopts it (without this, two phones would each keep their own copy
 *     forever). Identical content is never a conflict: the later stamp is kept as-is.
 *  4. Tombstones propagate; a deleted entity only comes back if the other side edited it strictly later.
 *  5. Uniqueness: two live transactions (or SMS links) with the same SMS hash / import fingerprint are collapsed:
 *     the one with the smaller uuid is kept, the other becomes a tombstone (deterministic on both devices).
 *  6. Cascade: a live transaction / repeat whose account is gone, or an SMS link whose transaction is gone,
 *     becomes a tombstone (deletes of accounts already delete their transactions on the device that did it).
 *  7. sms_log and "keep both" pairs are grow-only sets (union).
 */
object SyncMerge {
    data class Result(
        val merged: SyncSnapshot,
        /** Records whose merged state differs from local (to be applied to the local DB). */
        val toApply: List<SyncRecord>,
        val stats: SyncStats,
    )

    fun secs(ms: Long) = Math.floorDiv(ms, 1000L)

    /** Returns true when [local] should be kept over [remote]. */
    fun localWins(local: SyncRecord, remote: SyncRecord): Boolean {
        val l = secs(local.ts); val r = secs(remote.ts)
        if (l != r) return l > r
        if (local.deleted != remote.deleted) return local.deleted
        return true
    }

    fun merge(local: SyncSnapshot, remote: SyncSnapshot, lastSyncAt: Long = 0): Result {
        val out = LinkedHashMap<String, SyncRecord>()
        var conflicts = 0
        for (uuid in (local.records.keys + remote.records.keys)) {
            val l = local.records[uuid]; val r = remote.records[uuid]
            out[uuid] = when {
                l == null -> r!!
                r == null -> l
                l == r -> l
                sameContent(l, r) -> if (compareValues(l.ts, r.ts).let { it > 0 || (it == 0 && l.updatedAt >= r.updatedAt) }) l else r
                else -> {
                    if (l.ts > lastSyncAt && r.ts > lastSyncAt) conflicts++
                    when {
                        !localWins(l, r) -> r
                        secs(l.ts) == secs(r.ts) && !l.deleted -> l.copy(updatedAt = (secs(l.ts) + 1) * 1000)
                        else -> l
                    }
                }
            }
        }

        // 4b. categories: one identity per (type, key / name) across devices; references re-linked (see [CategoryLinks])
        val catStats = CategoryLinks.apply(out) + CategoryLinks.applyAccounts(out)

        // 5. unique SMS hashes / import fingerprints: keep the best copy (see [dupRank]), the other becomes a tombstone
        var dropped = 0
        for (kind in listOf(Kind.TXN, Kind.SMS_LINK)) {
            out.values.filter { it.kind == kind && !it.deleted && it.str("smsHash") != null }
                .groupBy { it.str("smsHash")!! }
                .values.filter { it.size > 1 }
                .forEach { group ->
                    val keep = group.sortedWith(dupRank(out)).first()
                    group.filter { it !== keep }.forEach { out[it.uuid] = it.tombstone(it.updatedAt); dropped++ }
                }
        }

        // 6. cascade on missing required references (iterate: txn deletion can orphan SMS links)
        fun gone(ref: String?) = ref == null || out[ref]?.deleted != false
        fun refTs(ref: String?) = ref?.let { out[it]?.deletedAt } ?: 0L
        for (pass in 0..1) {
            for (rec in out.values.toList()) {
                if (rec.deleted) continue
                val ref = when (rec.kind) {
                    Kind.TXN, Kind.RECURRING -> rec.str("account")
                    Kind.SMS_LINK -> rec.str("txn")
                    Kind.BILL_PAY -> rec.str("bill")
                    else -> continue
                }
                if (gone(ref)) out[rec.uuid] = rec.tombstone(maxOf(rec.updatedAt, refTs(ref)))
            }
        }

        val merged = SyncSnapshot(
            records = out,
            smsLog = remote.smsLog + local.smsLog,
            dismissed = local.dismissed + remote.dismissed,
            devices = remote.devices + local.devices.filterKeys { it !in remote.devices },
            fileId = remote.fileId ?: local.fileId,
            updatedAt = maxOf(local.updatedAt, remote.updatedAt),
        )
        val toApply = out.values.filter { it != local.records[it.uuid] }
        // a real change of an entity's content or existence (re-stamps alone are not counted)
        fun changesLive(m: SyncRecord, other: SyncRecord?) =
            if (other == null) !m.deleted else !sameContent(m, other) && (!m.deleted || !other.deleted)
        val stats = SyncStats(
            pulled = out.values.count { changesLive(it, local.records[it.uuid]) },
            pushed = out.values.count { changesLive(it, remote.records[it.uuid]) },
            merged = out.values.count { !it.deleted },
            conflicts = conflicts + dropped,
            categoriesRelinked = catStats,
            duplicatesDropped = dropped,
        )
        return Result(merged, toApply, stats)
    }

    /**
     * Order for copies of one SMS recorded on two phones (each imported it itself): the copy whose account and
     * category still exist, then one with a real category (not empty / «أخرى»), then the one recorded first
     * (the phone that had it originally, with the user's edits), then the uuid. Deterministic on both devices.
     */
    fun dupRank(out: Map<String, SyncRecord>): Comparator<SyncRecord> {
        fun live(ref: String?) = ref != null && out[ref]?.deleted == false
        fun catOk(r: SyncRecord) = r.str("category").let { it == null || live(it) }
        fun specific(r: SyncRecord) = r.str("category")?.let { out[it] }?.takeIf { !it.deleted }?.str("key").let { k ->
            r.str("category") != null && live(r.str("category")) && k != "other" && k != "other_income"
        }
        return compareByDescending<SyncRecord> { live(it.str("account") ?: it.str("txn")) && catOk(it) }
            .thenByDescending { specific(it) }
            .thenBy { it.long("createdAt") ?: Long.MAX_VALUE }
            .thenBy { it.uuid }
    }

    /** Fields derived from other records (category names carried for re-linking); not part of an entity's own content. */
    val DERIVED = setOf("catKey", "catName", "catNameEn", "catType")

    private fun sameContent(a: SyncRecord, b: SyncRecord) = a.deleted == b.deleted && a.fields - DERIVED == b.fields - DERIVED

    /** True when the merged snapshot contains something the remote file does not (so it must be rewritten). */
    fun needsPush(merged: SyncSnapshot, remote: SyncSnapshot): Boolean =
        merged.records != remote.records || merged.smsLog.keys != remote.smsLog.keys || merged.dismissed != remote.dismissed
}
