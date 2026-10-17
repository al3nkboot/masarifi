package com.midroid.masarifi

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
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

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()
    private var pendingTxnId by mutableStateOf<Long?>(null)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocalizedRes.wrap(newBase, SettingsStore.language(newBase)))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingTxnId = intent?.getLongExtra(EXTRA_TXN_ID, -1L)?.takeIf { it > 0 }
        if (intent?.getBooleanExtra(EXTRA_OPEN_BILLS, false) == true) vm.openBills.value = true
        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            MasarifiTheme(settings.theme) {
                NotificationPermissionOnce()
                AppRoot(
                    vm = vm,
                    openTxnId = pendingTxnId,
                    onOpenTxnConsumed = { pendingTxnId = null },
                    onLanguageChanged = { recreate() },
                )
            }
        }
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
    }
}
