package com.midroid.masarifi.data.sync

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class SyncLogEntry(
    val at: Long,
    val ok: Boolean,
    val trigger: String,
    val pulled: Int = 0,
    val pushed: Int = 0,
    val merged: Int = 0,
    val conflicts: Int = 0,
    val error: String? = null,
)

/** Device-local sync configuration & status (never written to the sync file except deviceId/deviceName). */
data class SyncState(
    val fileUri: String? = null,
    val fileLabel: String? = null,
    /** fileId of the sync file this device last synced with (detects picking a different file / a copy). */
    val fileId: String? = null,
    val deviceId: String = "",
    val deviceName: String = "",
    val auto: Boolean = false,
    val intervalMin: Int = 60,
    val lastAt: Long = 0,
    val lastOk: Boolean = true,
    val lastError: String? = null,
    /** Time of the last successful sync (used to count conflicts). */
    val lastSyncAt: Long = 0,
    /** Local change mark after the last successful sync (auto-sync skips when nothing changed). */
    val lastMark: Long = 0,
    val log: List<SyncLogEntry> = emptyList(),
    /** Other devices seen in the sync file. */
    val devices: Map<String, DeviceInfo> = emptyMap(),
    val persistable: Boolean = true,
) {
    val configured get() = fileUri != null
}

class SyncStore(context: Context, defaultDeviceName: String = android.os.Build.MODEL ?: "Android", prefsName: String = "masarifi_sync") {
    private val prefs: SharedPreferences = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    private val _state: MutableStateFlow<SyncState>
    val state: StateFlow<SyncState>
    val current get() = _state.value

    init {
        if (!prefs.contains("deviceId")) prefs.edit().putString("deviceId", java.util.UUID.randomUUID().toString()).apply()
        _state = MutableStateFlow(read(defaultDeviceName))
        state = _state.asStateFlow()
    }

    private fun read(defName: String) = SyncState(
        fileUri = prefs.getString("fileUri", null), fileLabel = prefs.getString("fileLabel", null), fileId = prefs.getString("fileId", null),
        deviceId = prefs.getString("deviceId", "")!!, deviceName = prefs.getString("deviceName", null) ?: defName,
        auto = prefs.getBoolean("auto", false), intervalMin = prefs.getInt("intervalMin", 60),
        lastAt = prefs.getLong("lastAt", 0), lastOk = prefs.getBoolean("lastOk", true), lastError = prefs.getString("lastError", null),
        lastSyncAt = prefs.getLong("lastSyncAt", 0), lastMark = prefs.getLong("lastMark", 0),
        log = decodeLog(prefs.getString("log", null)), devices = decodeDevices(prefs.getString("devices", null)),
        persistable = prefs.getBoolean("persistable", true),
    )

    @Synchronized
    fun update(block: (SyncState) -> SyncState) {
        val n = block(_state.value)
        prefs.edit().putString("fileUri", n.fileUri).putString("fileLabel", n.fileLabel).putString("fileId", n.fileId)
            .putString("deviceName", n.deviceName).putBoolean("auto", n.auto).putInt("intervalMin", n.intervalMin)
            .putLong("lastAt", n.lastAt).putBoolean("lastOk", n.lastOk).putString("lastError", n.lastError)
            .putLong("lastSyncAt", n.lastSyncAt).putLong("lastMark", n.lastMark)
            .putString("log", encodeLog(n.log)).putString("devices", encodeDevices(n.devices)).putBoolean("persistable", n.persistable)
            .apply()
        _state.value = n
    }

    fun addLog(e: SyncLogEntry) = update { it.copy(log = (listOf(e) + it.log).take(MAX_LOG)) }

    companion object {
        const val MAX_LOG = 30
        val INTERVALS = listOf(15, 30, 60, 180, 360)

        fun encodeLog(l: List<SyncLogEntry>) = JSONArray().apply {
            l.forEach { put(JSONObject().put("at", it.at).put("ok", it.ok).put("t", it.trigger).put("pl", it.pulled).put("ps", it.pushed).put("m", it.merged).put("c", it.conflicts).putOpt("e", it.error)) }
        }.toString()

        fun decodeLog(s: String?): List<SyncLogEntry> = runCatching {
            val a = JSONArray(s ?: return emptyList())
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                SyncLogEntry(o.getLong("at"), o.getBoolean("ok"), o.optString("t"), o.optInt("pl"), o.optInt("ps"), o.optInt("m"), o.optInt("c"), if (o.isNull("e")) null else o.optString("e"))
            }
        }.getOrDefault(emptyList())

        fun encodeDevices(d: Map<String, DeviceInfo>) = JSONObject().apply {
            d.forEach { (k, v) -> put(k, JSONObject().put("name", v.name).put("lastSyncAt", v.lastSyncAt).put("appVersion", v.appVersion)) }
        }.toString()

        fun decodeDevices(s: String?): Map<String, DeviceInfo> = runCatching {
            val o = JSONObject(s ?: return emptyMap())
            o.keys().asSequence().associateWith { k -> o.getJSONObject(k).let { DeviceInfo(it.optString("name"), it.optLong("lastSyncAt"), it.optString("appVersion")) } }
        }.getOrDefault(emptyMap())
    }
}
