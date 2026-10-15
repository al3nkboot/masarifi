package com.midroid.masarifi.data

import android.content.Context
import android.content.SharedPreferences
import com.midroid.masarifi.util.Currencies
import com.midroid.masarifi.util.CurrencyInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettings(
    val currency: String = "OMR",
    val language: String = "ar",
    val theme: String = "dark",
    val smsEnabled: Boolean = false,
    val smsNotify: Boolean = true,
    val smsAcceptUnknown: Boolean = false,
    val extraSenders: String = "",
    val budgetAlerts: Boolean = true,
    val spikeAlerts: Boolean = true,
    val weekStartsSaturday: Boolean = false,
    /** Time window (hours, ±) for SMS ↔ manual duplicate detection: 24, 48 or 72. */
    val dupWindowHours: Int = 48,
    /** Own-wallet top-up rules as JSON (blank = the built-in defaults, see [DefaultSmsConfig.walletTopups]). */
    val walletTopups: String = "",
) {
    val walletTopupList: List<com.midroid.masarifi.sms.WalletTopup>
        get() = if (walletTopups.isBlank()) com.midroid.masarifi.sms.DefaultSmsConfig.walletTopups
        else com.midroid.masarifi.sms.WalletTopupCodec.decode(walletTopups)
    val arabic get() = language == "ar"
    val currencyInfo: CurrencyInfo get() = Currencies.of(currency)
    val extraSenderList get() = extraSenders.split(',', '\n', ';').map { it.trim() }.filter { it.isNotEmpty() }
}

class SettingsStore(context: Context, prefsName: String = "masarifi_settings") {
    private val prefs: SharedPreferences = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<AppSettings> = _state.asStateFlow()
    val current: AppSettings get() = _state.value

    private fun read() = AppSettings(
        currency = prefs.getString("currency", "OMR") ?: "OMR",
        language = prefs.getString("language", "ar") ?: "ar",
        theme = prefs.getString("theme", "dark") ?: "dark",
        smsEnabled = prefs.getBoolean("smsEnabled", false),
        smsNotify = prefs.getBoolean("smsNotify", true),
        smsAcceptUnknown = prefs.getBoolean("smsAcceptUnknown", false),
        extraSenders = prefs.getString("extraSenders", "") ?: "",
        budgetAlerts = prefs.getBoolean("budgetAlerts", true),
        spikeAlerts = prefs.getBoolean("spikeAlerts", true),
        weekStartsSaturday = prefs.getBoolean("weekStartsSaturday", false),
        dupWindowHours = prefs.getInt("dupWindowHours", 48),
        walletTopups = prefs.getString("walletTopups", "") ?: "",
    )

    /** When the settings that are shared between synced devices last changed (0 = never changed on this device). */
    val syncedUpdatedAt: Long get() = prefs.getLong("syncedSettingsAt", 0)

    fun update(block: (AppSettings) -> AppSettings) = write(block(_state.value), null)

    /** Applies settings received from the other device (keeps its timestamp instead of "now"). */
    fun applySynced(updatedAt: Long, block: (AppSettings) -> AppSettings) = write(block(_state.value), updatedAt)

    @Synchronized
    private fun write(n: AppSettings, syncedAt: Long?) {
        val old = _state.value
        val e = prefs.edit()
        if (syncedAt != null) e.putLong("syncedSettingsAt", syncedAt)
        else if (synced(old) != synced(n)) e.putLong("syncedSettingsAt", System.currentTimeMillis())
        e
            .putString("currency", n.currency)
            .putString("language", n.language)
            .putString("theme", n.theme)
            .putBoolean("smsEnabled", n.smsEnabled)
            .putBoolean("smsNotify", n.smsNotify)
            .putBoolean("smsAcceptUnknown", n.smsAcceptUnknown)
            .putString("extraSenders", n.extraSenders)
            .putBoolean("budgetAlerts", n.budgetAlerts)
            .putBoolean("spikeAlerts", n.spikeAlerts)
            .putBoolean("weekStartsSaturday", n.weekStartsSaturday)
            .putInt("dupWindowHours", n.dupWindowHours)
            .putString("walletTopups", n.walletTopups)
            .apply()
        _state.value = n
    }

    /** One-shot flags (e.g. "alert sent for budget X in month Y"). */
    fun flag(key: String): Boolean = prefs.getBoolean("flag_$key", false)
    fun setFlag(key: String) = prefs.edit().putBoolean("flag_$key", true).apply()

    var lastSpikeCheckDay: String
        get() = prefs.getString("lastSpikeDay", "") ?: ""
        set(v) = prefs.edit().putString("lastSpikeDay", v).apply()

    companion object {
        /** The subset of settings that is shared between synced devices (language/theme/SMS options stay per device). */
        fun synced(s: AppSettings) = listOf(s.currency, s.weekStartsSaturday, s.dupWindowHours, s.budgetAlerts, s.spikeAlerts)

        /** Read language synchronously (used in attachBaseContext before the app container exists). */
        fun language(context: Context): String =
            context.getSharedPreferences("masarifi_settings", Context.MODE_PRIVATE).getString("language", "ar") ?: "ar"
    }
}
