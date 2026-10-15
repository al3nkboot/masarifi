package com.midroid.masarifi.data.sync

import org.json.JSONArray
import org.json.JSONObject

class SyncFormatException(val code: String, msg: String) : Exception(msg)

/**
 * The shared sync file (`masarifi-sync.json`):
 * { "format": "masarifi-sync", "version": 1, "fileId": "...", "updatedAt": millis,
 *   "devices": { "<deviceId>": {"name", "lastSyncAt", "appVersion"} },
 *   "entities": { "txn": [ {"uuid", "updatedAt", "deletedAt"?, ...fields} ], "account": [...], ... },
 *   "smsLog": [ {"hash", "sender", "receivedAt", "status", "txn"} ], "dismissed": ["uuidA|uuidB", ...] }
 * Tombstones carry only uuid / updatedAt / deletedAt. Unknown kinds and fields are preserved on re-write.
 */
object SyncCodec {
    const val FORMAT = "masarifi-sync"
    const val VERSION = 1
    private val META = setOf("uuid", "updatedAt", "deletedAt")

    fun encode(s: SyncSnapshot): String {
        val o = JSONObject()
        o.put("format", FORMAT).put("version", VERSION).putOpt("fileId", s.fileId).put("updatedAt", s.updatedAt)
        o.put("devices", JSONObject().apply {
            s.devices.toSortedMap().forEach { (id, d) -> put(id, JSONObject().put("name", d.name).put("lastSyncAt", d.lastSyncAt).put("appVersion", d.appVersion)) }
        })
        val ents = JSONObject()
        s.records.values.groupBy { it.kind }.toSortedMap().forEach { (kind, list) ->
            ents.put(kind, JSONArray().apply {
                list.sortedBy { it.uuid }.forEach { r ->
                    val j = JSONObject().put("uuid", r.uuid).put("updatedAt", r.updatedAt)
                    if (r.deletedAt != null) j.put("deletedAt", r.deletedAt)
                    else r.fields.toSortedMap().forEach { (k, v) -> j.put(k, v ?: JSONObject.NULL) }
                    put(j)
                }
            })
        }
        o.put("entities", ents)
        o.put("smsLog", JSONArray().apply {
            s.smsLog.toSortedMap().forEach { (h, e) ->
                put(JSONObject().put("hash", h).put("sender", e.sender).put("receivedAt", e.receivedAt).put("status", e.status).putOpt("txn", e.txnUuid))
            }
        })
        o.put("dismissed", JSONArray().apply { s.dismissed.sorted().forEach { put(it) } })
        return o.toString()
    }

    private fun value(v: Any?): Any? = when (v) {
        null, JSONObject.NULL -> null
        is Number -> v.toLong()
        is Boolean, is String -> v
        else -> v.toString()
    }

    /** Blank / empty file = new sync file (nothing to merge). */
    fun decode(text: String?): SyncSnapshot {
        if (text.isNullOrBlank()) return SyncSnapshot()
        val o = try { JSONObject(text.removePrefix("\uFEFF")) } catch (e: Exception) { throw SyncFormatException("not_json", "Not a sync file") }
        if (o.optString("format") != FORMAT) throw SyncFormatException("not_sync", "Not a Masarifi sync file")
        val v = o.optInt("version", 0)
        if (v < 1) throw SyncFormatException("not_sync", "Missing version")
        if (v > VERSION) throw SyncFormatException("newer", "Sync file written by a newer app version ($v)")
        val records = LinkedHashMap<String, SyncRecord>()
        o.optJSONObject("entities")?.let { ents ->
            for (kind in ents.keys()) {
                val arr = ents.getJSONArray(kind)
                for (i in 0 until arr.length()) {
                    val j = arr.getJSONObject(i)
                    val uuid = j.getString("uuid")
                    val deletedAt = if (j.has("deletedAt") && !j.isNull("deletedAt")) j.getLong("deletedAt") else null
                    val fields = if (deletedAt != null) emptyMap() else j.keys().asSequence().filter { it !in META }.associateWith { value(j.opt(it)) }
                    records[uuid] = SyncRecord(uuid, kind, j.optLong("updatedAt"), deletedAt, fields)
                }
            }
        }
        val smsLog = LinkedHashMap<String, SmsLogEntry>()
        o.optJSONArray("smsLog")?.let { a ->
            for (i in 0 until a.length()) {
                val j = a.getJSONObject(i)
                smsLog[j.getString("hash")] = SmsLogEntry(j.optString("sender"), j.optLong("receivedAt"), j.optInt("status"), if (j.isNull("txn")) null else j.optString("txn"))
            }
        }
        val dismissed = o.optJSONArray("dismissed")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: emptySet()
        val devices = LinkedHashMap<String, DeviceInfo>()
        o.optJSONObject("devices")?.let { d ->
            for (id in d.keys()) { val j = d.getJSONObject(id); devices[id] = DeviceInfo(j.optString("name"), j.optLong("lastSyncAt"), j.optString("appVersion")) }
        }
        return SyncSnapshot(records, smsLog, dismissed, devices, if (o.isNull("fileId")) null else o.optString("fileId").ifEmpty { null }, o.optLong("updatedAt"))
    }
}
