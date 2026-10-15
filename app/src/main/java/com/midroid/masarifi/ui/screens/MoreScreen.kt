package com.midroid.masarifi.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.midroid.masarifi.BuildConfigInfo
import com.midroid.masarifi.R
import com.midroid.masarifi.ui.AppViewModel
import com.midroid.masarifi.ui.Route
import com.midroid.masarifi.ui.components.AppIcons
import com.midroid.masarifi.ui.components.SectionTitle
import com.midroid.masarifi.ui.components.SettingRow
import com.midroid.masarifi.ui.components.SwitchRow
import com.midroid.masarifi.ui.theme.AppTheme
import com.midroid.masarifi.util.Currencies
import java.time.LocalDate

@Composable
fun MoreScreen(vm: AppViewModel, navigate: (Route) -> Unit, modifier: Modifier, onLanguageChanged: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var dialog by remember { mutableStateOf<String?>(null) }
    val exportedFmt = stringResource(R.string.exported_n)
    val importedFmt = stringResource(R.string.imported_n)
    val failed = stringResource(R.string.operation_failed)

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) vm.exportCsv(uri) { n -> if (n < 0) failed else String.format(java.util.Locale.US, exportedFmt, n) }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importCsv(uri) { n -> if (n < 0) failed else String.format(java.util.Locale.US, importedFmt, n) }
    }

    val mmLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.mmPick(uri)
    }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(stringResource(R.string.tab_more), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp))

        SectionTitle(stringResource(R.string.section_general))
        val cur = s.currencyInfo
        SettingRow(AppIcons.Money, stringResource(R.string.currency), "${cur.symbol(s.arabic)} — ${if (s.arabic) cur.nameAr else cur.nameEn}", onClick = { dialog = "currency" })
        SettingRow(AppIcons.Language, stringResource(R.string.language), if (s.arabic) "العربية" else "English", onClick = { dialog = "language" })
        SettingRow(AppIcons.Palette, stringResource(R.string.theme), themeLabel(s.theme), onClick = { dialog = "theme" })
        SwitchRow(AppIcons.Calendar, stringResource(R.string.week_starts_saturday), null, s.weekStartsSaturday) { v -> vm.updateSettings { it.copy(weekStartsSaturday = v) } }

        SectionTitle(stringResource(R.string.section_data))
        SettingRow(AppIcons.Category, stringResource(R.string.categories), stringResource(R.string.categories_sub), onClick = { navigate(Route.Categories) })
        SettingRow(AppIcons.Repeat, stringResource(R.string.repeating), stringResource(R.string.repeating_sub), onClick = { navigate(Route.Recurring) })
        val dupCount = vm.dupGroups.collectAsStateWithLifecycle().value.size
        SettingRow(AppIcons.Copy, stringResource(R.string.dup_review), stringResource(R.string.dup_review_sub) + if (dupCount > 0) " ($dupCount)" else "", onClick = { navigate(Route.Duplicates) })
        SettingRow(AppIcons.Calendar, stringResource(R.string.dup_window), stringResource(R.string.dup_window_value, s.dupWindowHours.toString()), onClick = { dialog = "dupwindow" })
        SettingRow(AppIcons.Sms, stringResource(R.string.sms_import), stringResource(if (s.smsEnabled) R.string.enabled else R.string.disabled), onClick = { navigate(Route.SmsSettings) })

        SectionTitle(stringResource(R.string.section_alerts))
        SwitchRow(Icons.Filled.Notifications, stringResource(R.string.budget_alerts), stringResource(R.string.budget_alerts_sub), s.budgetAlerts) { v -> vm.updateSettings { it.copy(budgetAlerts = v) } }
        SwitchRow(Icons.Filled.Notifications, stringResource(R.string.spike_alerts), stringResource(R.string.spike_alerts_sub), s.spikeAlerts) { v -> vm.updateSettings { it.copy(spikeAlerts = v) } }

        SectionTitle(stringResource(R.string.section_backup))
        SettingRow(AppIcons.Download, stringResource(R.string.export_csv), stringResource(R.string.export_csv_sub), onClick = { exportLauncher.launch("masarifi-${LocalDate.now()}.csv") })
        SettingRow(AppIcons.Upload, stringResource(R.string.drive_backup), stringResource(R.string.drive_backup_sub), onClick = { navigate(Route.Backup) })
        SettingRow(Icons.Filled.Refresh, stringResource(R.string.device_sync), stringResource(R.string.device_sync_sub), onClick = { navigate(Route.Sync) })
        SettingRow(AppIcons.Book, stringResource(R.string.mm_import), stringResource(R.string.mm_import_sub), onClick = {
            mmLauncher.launch(
                arrayOf(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/vnd.ms-excel",
                    "text/csv", "text/comma-separated-values", "text/tab-separated-values", "application/csv", "text/plain",
                    "application/octet-stream", "application/zip",
                ),
            )
        })
        SettingRow(AppIcons.Upload, stringResource(R.string.import_csv), stringResource(R.string.import_csv_sub), onClick = { importLauncher.launch(arrayOf("text/*", "text/csv", "text/comma-separated-values", "application/csv", "application/vnd.ms-excel")) })

        SectionTitle(stringResource(R.string.about))
        SettingRow(Icons.Filled.Info, stringResource(R.string.app_name), "${stringResource(R.string.version)} ${BuildConfigInfo.versionName(ctx)} • com.midroid.masarifi")
        HorizontalDivider(color = AppTheme.colors.divider)
        androidx.compose.foundation.layout.Spacer(Modifier.padding(24.dp))
    }

    MmImportDialogs(vm)

    when (dialog) {
        "currency" -> ChoiceDialog(
            stringResource(R.string.currency), Currencies.all.map { it.code to "${it.symbol(s.arabic)}  ${if (s.arabic) it.nameAr else it.nameEn} (${it.code})" }, s.currency,
            onDismiss = { dialog = null },
        ) { code -> vm.updateSettings { it.copy(currency = code) } }
        "language" -> ChoiceDialog(stringResource(R.string.language), listOf("ar" to "العربية", "en" to "English"), s.language, onDismiss = { dialog = null }) { lang ->
            if (lang != s.language) { vm.updateSettings { it.copy(language = lang) }; onLanguageChanged() }
        }
        "dupwindow" -> ChoiceDialog(
            stringResource(R.string.dup_window),
            com.midroid.masarifi.data.dup.DuplicateDetector.WINDOW_CHOICES.map { it.toString() to stringResource(R.string.dup_window_value, it.toString()) },
            s.dupWindowHours.toString(), onDismiss = { dialog = null },
        ) { v -> vm.updateSettings { it.copy(dupWindowHours = v.toInt()) } }
        "theme" -> ChoiceDialog(
            stringResource(R.string.theme), listOf("dark" to themeLabel("dark"), "light" to themeLabel("light"), "system" to themeLabel("system")), s.theme,
            onDismiss = { dialog = null },
        ) { t -> vm.updateSettings { it.copy(theme = t) } }
    }
}

@Composable
private fun themeLabel(t: String) = stringResource(when (t) { "light" -> R.string.theme_light; "system" -> R.string.theme_system; else -> R.string.theme_dark })

@Composable
fun ChoiceDialog(title: String, options: List<Pair<String, String>>, selected: String, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { (k, label) ->
                    Row(Modifier.fillMaxWidth().clickable { onSelect(k); onDismiss() }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = k == selected, onClick = { onSelect(k); onDismiss() })
                        Text(label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
