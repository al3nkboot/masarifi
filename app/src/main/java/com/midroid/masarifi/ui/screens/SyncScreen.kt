package com.midroid.masarifi.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.midroid.masarifi.R
import com.midroid.masarifi.data.sync.SyncLogEntry
import com.midroid.masarifi.data.sync.SyncService
import com.midroid.masarifi.data.sync.SyncStats
import com.midroid.masarifi.data.sync.SyncStore
import com.midroid.masarifi.ui.AppViewModel
import com.midroid.masarifi.ui.Fmt
import com.midroid.masarifi.ui.SyncUi
import com.midroid.masarifi.ui.components.AppIcons
import com.midroid.masarifi.ui.components.SectionTitle
import com.midroid.masarifi.ui.components.SettingRow
import com.midroid.masarifi.ui.components.SwitchRow
import com.midroid.masarifi.ui.theme.AppTheme
import java.time.format.DateTimeFormatter
import java.util.Locale

private val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.US)
private fun fmtTime(ms: Long) = ltr(Fmt.toDateTime(ms).format(stamp))
private val Warn = Color(0xFFE6A23C)

/** Short, comparable form of the file's sync id (shown on both devices). */
fun shortId(id: String?) = id?.replace("-", "")?.take(8)?.uppercase(Locale.US)?.chunked(4)?.joinToString("-")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen(vm: AppViewModel, onBack: () -> Unit, snackbar: SnackbarHostState) {
    val st by vm.syncState.collectAsStateWithLifecycle()
    val ui by vm.syncUi.collectAsStateWithLifecycle()
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    val doneFmt = stringResource(R.string.sync_done)
    val failed = stringResource(R.string.operation_failed)
    val done: (SyncStats?) -> String = { s -> if (s == null) failed else String.format(Locale.US, doneFmt, s.pulled.toString(), s.pushed.toString(), s.conflicts.toString()) }

    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> if (uri != null) vm.syncCreate(uri, done) }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.syncOpen(uri) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.device_sync)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            // ---- sync now + status
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Button(onClick = { vm.syncNow(done) }, enabled = st.configured && ui == null, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.sync_now))
                }
            }
            val lastText = when {
                st.lastAt == 0L -> stringResource(R.string.sync_never)
                st.lastOk -> fmtTime(st.lastAt)
                else -> stringResource(R.string.sync_last_failed, fmtTime(st.lastAt), errorText(st.lastError))
            }
            SettingRow(Icons.Filled.Info, stringResource(R.string.sync_last), lastText)
            SettingRow(Icons.Filled.Phone, stringResource(R.string.sync_device_name), st.deviceName, onClick = { dialog = "name" })
            if (st.devices.isNotEmpty()) {
                Text(stringResource(R.string.sync_other_devices), fontSize = 13.sp, color = AppTheme.colors.muted, modifier = Modifier.padding(start = 56.dp, end = 16.dp, top = 4.dp))
                st.devices.values.sortedByDescending { it.lastSyncAt }.forEach { d ->
                    Text("• ${d.name} — " + stringResource(R.string.sync_device_seen, fmtTime(d.lastSyncAt)), fontSize = 13.sp, modifier = Modifier.padding(start = 56.dp, end = 16.dp, top = 2.dp))
                }
            }

            // ---- file
            SectionTitle(stringResource(R.string.sync_file))
            SettingRow(
                AppIcons.Link, stringResource(R.string.sync_file), st.fileLabel?.let { ltr(it) } ?: stringResource(R.string.sync_file_none),
                trailing = if (st.configured) {
                    { IconButton(onClick = vm::syncDisconnect) { Icon(Icons.Filled.Close, stringResource(R.string.sync_disconnect)) } }
                } else null,
            )
            if (st.fileId != null) {
                Text(stringResource(R.string.sync_file_id, ltr(shortId(st.fileId)!!)), fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 56.dp, end = 16.dp))
            }
            if (st.configured && !st.persistable) Hint(stringResource(R.string.sync_no_persist), warn = true)
            Hint(stringResource(R.string.sync_warn_same_file), warn = true)
            SettingRow(AppIcons.Upload, stringResource(R.string.sync_create), stringResource(R.string.sync_create_sub), onClick = {
                if (ui == null) create.launch(SyncService.FILE_NAME)
            })
            SettingRow(AppIcons.Download, stringResource(R.string.sync_open), stringResource(R.string.sync_open_sub), onClick = {
                if (ui == null) open.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*"))
            })

            // ---- auto
            SectionTitle(stringResource(R.string.sync_auto))
            SwitchRow(AppIcons.Repeat, stringResource(R.string.sync_auto), stringResource(R.string.sync_auto_sub, st.intervalMin.toString()), st.auto) { v -> vm.setSyncAuto(v) }
            if (st.auto) SettingRow(AppIcons.Calendar, stringResource(R.string.sync_interval), stringResource(R.string.sync_interval_value, st.intervalMin.toString()), onClick = { dialog = "interval" })
            Text(stringResource(R.string.sync_not_realtime), fontSize = 12.sp, color = AppTheme.colors.muted, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))

            // ---- how-to
            SectionTitle(stringResource(R.string.sync_how_title))
            Text(stringResource(R.string.sync_how_body), fontSize = 13.sp, lineHeight = 20.sp, modifier = Modifier.padding(horizontal = 16.dp))
            Text(stringResource(R.string.sync_shared_hint), fontSize = 12.sp, color = AppTheme.colors.muted, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))

            // ---- log
            SectionTitle(stringResource(R.string.sync_log))
            if (st.log.isEmpty()) Text(stringResource(R.string.sync_log_empty), color = AppTheme.colors.muted, modifier = Modifier.padding(horizontal = 16.dp))
            st.log.take(15).forEach { LogRow(it) }
            Text(stringResource(R.string.sync_separate_from_backup), fontSize = 12.sp, color = AppTheme.colors.muted, modifier = Modifier.padding(16.dp))
        }
    }

    when (dialog) {
        "interval" -> ChoiceDialog(
            stringResource(R.string.sync_interval), SyncStore.INTERVALS.map { it.toString() to stringResource(R.string.sync_interval_value, it.toString()) },
            st.intervalMin.toString(), onDismiss = { dialog = null },
        ) { v -> vm.setSyncInterval(v.toInt()) }
        "name" -> {
            var name by remember { mutableStateOf(st.deviceName) }
            AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text(stringResource(R.string.sync_device_name)) },
                text = { OutlinedTextField(name, { name = it }, singleLine = true) },
                confirmButton = { TextButton(onClick = { vm.setDeviceName(name); dialog = null }) { Text(stringResource(R.string.ok)) } },
                dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.cancel)) } },
            )
        }
    }

    when (val u = ui) {
        null -> {}
        SyncUi.Busy -> BusyDialog(stringResource(R.string.working))
        is SyncUi.ConfirmOpen -> AlertDialog(
            onDismissRequest = vm::syncDismiss,
            title = { Text(stringResource(R.string.sync_first_title)) },
            text = { Text(ltr(u.label) + "\n\n" + stringResource(R.string.sync_first_body, u.items.toString(), u.devices.joinToString("، ").ifEmpty { "—" })) },
            confirmButton = { TextButton(onClick = { vm.syncConfirmOpen(done) }) { Text(stringResource(R.string.sync_merge)) } },
            dismissButton = { TextButton(onClick = vm::syncDismiss) { Text(stringResource(R.string.cancel)) } },
        )
        is SyncUi.Mismatch -> AlertDialog(
            onDismissRequest = vm::syncDismiss,
            title = { Text(stringResource(R.string.sync_other_file_title)) },
            text = { Text(stringResource(R.string.sync_other_file_body, ltr(shortId(u.newId)!!), ltr(shortId(u.oldId) ?: "?"))) },
            confirmButton = { TextButton(onClick = { vm.syncDismiss(); vm.syncNow(done, acceptOther = true) }) { Text(stringResource(R.string.sync_merge_anyway), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = vm::syncDismiss) { Text(stringResource(R.string.cancel)) } },
        )
        is SyncUi.Error -> AlertDialog(
            onDismissRequest = vm::syncDismiss,
            title = { Text(stringResource(R.string.device_sync)) },
            text = { Text(errorText(u.code)) },
            confirmButton = { TextButton(onClick = vm::syncDismiss) { Text(stringResource(R.string.ok)) } },
        )
    }
}

@Composable
private fun errorText(code: String?): String = when (code) {
    "not_sync", "not_json" -> stringResource(R.string.sync_err_not_sync)
    "newer" -> stringResource(R.string.sync_err_newer)
    "no_access" -> stringResource(R.string.sync_err_access)
    "other_file" -> stringResource(R.string.sync_other_file_title)
    null -> "?"
    else -> code
}

@Composable
private fun Hint(text: String, warn: Boolean = false) {
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth()
            .background(if (warn) Warn.copy(alpha = 0.12f) else Color.Transparent, RoundedCornerShape(10.dp))
            .border(1.dp, if (warn) Warn.copy(alpha = 0.6f) else Color.Transparent, RoundedCornerShape(10.dp))
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (warn) Icon(Icons.Filled.Warning, null, tint = Warn, modifier = Modifier.size(18.dp))
        Text(text, fontSize = 12.sp, lineHeight = 18.sp)
    }
}

@Composable
private fun LogRow(e: SyncLogEntry) {
    val trig = when (e.trigger) {
        "manual" -> R.string.sync_trigger_manual
        "resume" -> R.string.sync_trigger_resume
        "change" -> R.string.sync_trigger_change
        "setup" -> R.string.sync_trigger_setup
        else -> R.string.sync_trigger_auto
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Text(if (e.ok) "✓" else "✗", color = if (e.ok) AppTheme.colors.income else MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, modifier = Modifier.width(22.dp))
        Column {
            Text(fmtTime(e.at) + " • " + stringResource(trig), fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(
                if (e.ok) stringResource(R.string.sync_log_line, e.pulled.toString(), e.pushed.toString(), e.merged.toString(), e.conflicts.toString())
                else errorText(e.error),
                fontSize = 12.sp, color = if (e.ok) AppTheme.colors.muted else MaterialTheme.colorScheme.error,
            )
        }
    }
}
