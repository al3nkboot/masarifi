package com.midroid.masarifi.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.midroid.masarifi.R
import com.midroid.masarifi.data.backup.BackupService
import com.midroid.masarifi.ui.AppViewModel
import com.midroid.masarifi.ui.Fmt
import com.midroid.masarifi.ui.RestoreUi
import com.midroid.masarifi.ui.components.AppIcons
import com.midroid.masarifi.ui.components.SectionTitle
import com.midroid.masarifi.ui.components.SettingRow
import com.midroid.masarifi.ui.components.SwitchRow
import com.midroid.masarifi.ui.theme.AppTheme
import java.time.format.DateTimeFormatter
import java.util.Locale

private val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.US)
private fun fmtTime(ms: Long) = ltr(Fmt.toDateTime(ms).format(stamp))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(vm: AppViewModel, onBack: () -> Unit, snackbar: SnackbarHostState, onLanguageChanged: () -> Unit) {
    val st by vm.backupState.collectAsStateWithLifecycle()
    val busy by vm.backupBusy.collectAsStateWithLifecycle()
    val restore by vm.restoreUi.collectAsStateWithLifecycle()
    val failed = stringResource(R.string.operation_failed)
    val savedFmt = stringResource(R.string.backup_saved)
    val folderOk = stringResource(R.string.backup_folder_set)
    val folderBad = stringResource(R.string.backup_folder_unsupported)
    val restoredFmt = stringResource(R.string.restore_done)

    val createDoc = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BackupService.MIME)) { uri ->
        if (uri != null) vm.backupNow(uri) { n -> if (n < 0) failed else String.format(Locale.US, savedFmt, n) }
    }
    val openTree = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.chooseBackupFolder(uri, folderOk, folderBad)
    }
    val openDoc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.restorePick(uri)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.drive_backup)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.backup_drive_hint), fontSize = 13.sp, color = AppTheme.colors.muted, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

            SettingRow(AppIcons.Upload, stringResource(R.string.backup_now), stringResource(R.string.backup_now_sub), onClick = {
                if (!busy) createDoc.launch(BackupService.fileName())
            })
            val lastText = when {
                st.lastAt == 0L -> stringResource(R.string.backup_never)
                st.lastOk -> fmtTime(st.lastAt) + (st.lastFile?.let { " • " + ltr(it.substringAfterLast('/').substringAfterLast(':')) } ?: "")
                else -> stringResource(R.string.backup_last_failed, fmtTime(st.lastAt), st.lastError ?: "?")
            }
            SettingRow(Icons.Filled.Info, stringResource(R.string.backup_last), lastText)

            SectionTitle(stringResource(R.string.backup_folder))
            SettingRow(
                AppIcons.Download, stringResource(R.string.backup_folder), st.folderLabel ?: stringResource(R.string.backup_folder_none),
                onClick = { if (!busy) openTree.launch(null) },
                trailing = if (st.treeUri != null) {
                    { IconButton(onClick = vm::clearBackupFolder) { Icon(Icons.Filled.Close, stringResource(R.string.backup_folder_remove)) } }
                } else null,
            )
            if (st.treeUri != null) {
                SwitchRow(AppIcons.Calendar, stringResource(R.string.backup_auto_daily), stringResource(R.string.backup_auto_daily_sub), st.autoDaily, vm::setAutoDaily)
                SwitchRow(AppIcons.Repeat, stringResource(R.string.backup_on_change), stringResource(R.string.backup_on_change_sub), st.onChange, vm::setBackupOnChange)
            }

            SectionTitle(stringResource(R.string.backup_restore))
            SettingRow(AppIcons.Download, stringResource(R.string.backup_restore), stringResource(R.string.backup_restore_sub), onClick = {
                openDoc.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*"))
            })
        }
    }

    if (busy) BusyDialog(stringResource(R.string.working))
    when (val r = restore) {
        null -> {}
        RestoreUi.Reading, RestoreUi.Restoring -> BusyDialog(stringResource(R.string.working))
        is RestoreUi.Error -> AlertDialog(
            onDismissRequest = vm::restoreDismiss,
            title = { Text(stringResource(R.string.backup_restore)) },
            text = { Text(stringResource(when (r.code) { "newer" -> R.string.restore_error_newer; "invalid" -> R.string.restore_error_invalid; else -> R.string.operation_failed })) },
            confirmButton = { TextButton(onClick = vm::restoreDismiss) { Text(stringResource(R.string.ok)) } },
        )
        is RestoreUi.Preview -> {
            val d = r.data
            AlertDialog(
                onDismissRequest = vm::restoreDismiss,
                title = { Text(stringResource(R.string.restore_preview_title)) },
                text = {
                    Text(
                        stringResource(
                            R.string.restore_preview_body, if (d.createdAt > 0) fmtTime(d.createdAt) else "?", d.appVersion.ifBlank { "?" },
                            d.accounts.size, d.categories.size, d.transactions.size, d.recurring.size, d.budgets.size,
                        ),
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        vm.restoreConfirm({ n -> if (n < 0) failed else String.format(Locale.US, restoredFmt, n) }, onLanguageChanged)
                    }) { Text(stringResource(R.string.restore_confirm), color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton(onClick = vm::restoreDismiss) { Text(stringResource(R.string.cancel)) } },
            )
        }
    }
}
