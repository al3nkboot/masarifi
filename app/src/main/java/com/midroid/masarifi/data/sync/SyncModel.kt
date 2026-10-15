package com.midroid.masarifi.data.sync

/**
 * One synced entity in the shared file. [uuid] is globally unique across kinds (budgets use "b:<categoryUuid>",
 * merchant rules "m:<merchantKey>", synced settings "settings"). References to other entities are stored as uuids.
 * Field values are normalised to String / Long / Boolean / null so records compare equal after a JSON round trip.
 * A tombstone has [deletedAt] set and no fields.
 */
data class SyncRecord(
    val uuid: String,
    val kind: String,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val fields: Map<String, Any?> = emptyMap(),
) {
    val deleted get() = deletedAt != null
    /** Version timestamp used for last-write-wins. */
    val ts get() = deletedAt ?: updatedAt

    fun str(k: String) = fields[k] as String?
    fun long(k: String) = (fields[k] as Number?)?.toLong()
    fun bool(k: String) = fields[k] as Boolean?

    fun tombstone(at: Long) = SyncRecord(uuid, kind, updatedAt = maxOf(updatedAt, at), deletedAt = at)

    companion object {
        fun live(uuid: String, kind: String, updatedAt: Long, vararg f: Pair<String, Any?>) =
            SyncRecord(uuid, kind, updatedAt, null, normalize(mapOf(*f)))

        fun normalize(m: Map<String, Any?>): Map<String, Any?> = m.mapValues { (_, v) ->
            when (v) {
                is Int -> v.toLong()
                is Short -> v.toLong()
                is Byte -> v.toLong()
                else -> v
            }
        }
    }
}

object Kind {
    const val CATEGORY = "category"
    const val ACCOUNT = "account"
    const val RECURRING = "recurring"
    const val TXN = "txn"
    const val SMS_LINK = "smslink"
    const val BUDGET = "budget"
    const val MERCHANT = "merchant"
    const val SETTINGS = "settings"

    /** Order in which live records are applied (referenced entities first). Deletes are applied in reverse. */
    val APPLY_ORDER = listOf(SETTINGS, CATEGORY, ACCOUNT, RECURRING, TXN, SMS_LINK, BUDGET, MERCHANT)
}

/** Grow-only set entry: an SMS that was already processed on some device (prevents re-importing it). */
data class SmsLogEntry(val sender: String, val receivedAt: Long, val status: Int, val txnUuid: String?)

data class DeviceInfo(val name: String, val lastSyncAt: Long, val appVersion: String = "")

data class SyncSnapshot(
    val records: Map<String, SyncRecord> = emptyMap(),
    /** hash → entry (grow-only union). */
    val smsLog: Map<String, SmsLogEntry> = emptyMap(),
    /** "uuidA|uuidB" (sorted) pairs the user marked as "not duplicates" (grow-only union). */
    val dismissed: Set<String> = emptySet(),
    val devices: Map<String, DeviceInfo> = emptyMap(),
    /** Random id written when the sync file is created; lets devices detect that they use different files. */
    val fileId: String? = null,
    val updatedAt: Long = 0,
) {
    val live get() = records.values.filter { !it.deleted }

    companion object {
        fun pairKey(a: String, b: String) = if (a < b) "$a|$b" else "$b|$a"
    }
}

data class SyncStats(
    /** Changes applied to this device (inserted / updated / deleted rows). */
    val pulled: Int = 0,
    /** Changes written to the shared file that it did not have yet. */
    val pushed: Int = 0,
    /** Live entities in the merged result. */
    val merged: Int = 0,
    /** Entities changed on both sides since the last sync, plus SMS/import duplicates resolved by keeping one. */
    val conflicts: Int = 0,
    /** Of [conflicts]: duplicate SMS hashes / import fingerprints dropped. */
    val duplicatesDropped: Int = 0,
)
