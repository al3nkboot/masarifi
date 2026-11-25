package com.midroid.masarifi

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.midroid.masarifi.data.SettingsStore
import com.midroid.masarifi.ui.AppViewModel
import com.midroid.masarifi.ui.Route
import com.midroid.masarifi.ui.screens.AppRoot
import com.midroid.masarifi.ui.theme.MasarifiTheme
import com.midroid.masarifi.work.LocalizedRes

class MainActivity : androidx.fragment.app.FragmentActivity() {
    private val vm: AppViewModel by viewModels()
    private var pendingTxnId by mutableStateOf<Long?>(null)
    private val lock by lazy { com.midroid.masarifi.data.AppLock(this) }
    private var locked by mutableStateOf(false)
    /** When the app last went to the background (0 = not since the last unlock). */
    private var backgroundedAt = 0L

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocalizedRes.wrap(newBase, SettingsStore.language(newBase)))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // a recreate (language change, rotation) while unlocked must not lock again
        backgroundedAt = savedInstanceState?.getLong(STATE_BG_AT) ?: 0L
        locked = lock.enabled && (savedInstanceState?.getBoolean(STATE_UNLOCKED) != true || (backgroundedAt != 0L && lock.shouldLock(backgroundedAt, System.currentTimeMillis())))
        pendingTxnId = intent?.getLongExtra(EXTRA_TXN_ID, -1L)?.takeIf { it > 0 }
        if (intent?.getBooleanExtra(EXTRA_OPEN_BILLS, false) == true) vm.openBills.value = true
        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            MasarifiTheme(settings.theme) {
                NotificationPermissionOnce()
                androidx.compose.foundation.layout.Box {
                    AppRoot(
                        vm = vm,
                        openTxnId = pendingTxnId,
                        onOpenTxnConsumed = { pendingTxnId = null },
                        onLanguageChanged = { recreate() },
                    )
                    if (locked) {
                        val bio = biometricAvailable()
                        LaunchedEffect(Unit) { if (bio) promptBiometric() }
                        androidx.activity.compose.BackHandler { moveTaskToBack(true) }
                        com.midroid.masarifi.ui.screens.LockScreen(lock::check, if (bio) ::promptBiometric else null) { locked = false }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (backgroundedAt != 0L && lock.shouldLock(backgroundedAt, System.currentTimeMillis())) locked = true
        backgroundedAt = 0L
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) backgroundedAt = System.currentTimeMillis()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_UNLOCKED, !locked)
        outState.putLong(STATE_BG_AT, backgroundedAt)
    }

    private fun biometricAvailable() =
        androidx.biometric.BiometricManager.from(this).canAuthenticate(BIOMETRIC_AUTH) == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS

    private fun promptBiometric() {
        val cb = object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: androidx.biometric.BiometricPrompt.AuthenticationResult) { locked = false }
        }
        val info = androidx.biometric.BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.lock_locked)).setAllowedAuthenticators(BIOMETRIC_AUTH).build()
        runCatching { androidx.biometric.BiometricPrompt(this, androidx.core.content.ContextCompat.getMainExecutor(this), cb).authenticate(info) }
    }

    override fun onResume() {
        super.onResume()
        com.midroid.masarifi.work.SyncWorker.onResume(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingTxnId = intent.getLongExtra(EXTRA_TXN_ID, -1L).takeIf { it > 0 }
        if (intent.getBooleanExtra(EXTRA_OPEN_BILLS, false)) vm.openBills.value = true
    }

    @Composable
    private fun NotificationPermissionOnce() {
        if (Build.VERSION.SDK_INT < 33) return
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
        LaunchedEffect(Unit) {
            val prefs = getSharedPreferences("masarifi_settings", MODE_PRIVATE)
            if (!prefs.getBoolean("asked_notif", false)) {
                prefs.edit().putBoolean("asked_notif", true).apply()
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    companion object {
        const val EXTRA_TXN_ID = "txn_id"
        const val EXTRA_OPEN_BILLS = "open_bills"
        private const val STATE_UNLOCKED = "lock_unlocked"
        private const val STATE_BG_AT = "lock_bg_at"
        /** Fingerprint / face, or the phone's own screen lock (PIN / pattern) as the system fallback. */
        private const val BIOMETRIC_AUTH = androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK or
            androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
    }
}
