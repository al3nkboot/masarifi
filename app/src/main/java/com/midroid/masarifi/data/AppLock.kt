package com.midroid.masarifi.data

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Optional app lock (2.9.0, device-local, never synced or backed up in the app's own backup file).
 * The PIN is kept only as a salted SHA-256; fingerprint / screen lock is offered on top of it by the lock screen.
 */
class AppLock(private val prefs: SharedPreferences) {
    constructor(context: Context) : this(context.getSharedPreferences("masarifi_lock", Context.MODE_PRIVATE))

    val enabled: Boolean get() = prefs.getString("pinHash", null) != null

    fun enable(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        prefs.edit().putString("salt", salt).putString("pinHash", hash(salt, pin)).apply()
    }

    fun disable() = prefs.edit().remove("salt").remove("pinHash").apply()

    fun check(pin: String): Boolean = enabled && hash(prefs.getString("salt", "") ?: "", pin) == prefs.getString("pinHash", null)

    /** Lock again when the app was in the background for at least [TIMEOUT_MS] (0 = never backgrounded = locked on launch). */
    fun shouldLock(backgroundedAt: Long, now: Long): Boolean = enabled && now - backgroundedAt >= TIMEOUT_MS

    private fun hash(salt: String, pin: String) =
        MessageDigest.getInstance("SHA-256").digest((salt + pin).toByteArray()).joinToString("") { "%02x".format(it) }

    companion object {
        const val TIMEOUT_MS = 30_000L
        fun validPin(pin: String) = pin.length in 4..8 && pin.all { it.isDigit() }
    }
}
