package com.midroid.masarifi.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TextButton
import com.midroid.masarifi.sms.WalletTopup
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.midroid.masarifi.R
import com.midroid.masarifi.sms.DefaultSmsConfig
import com.midroid.masarifi.sms.SmsImporter
import com.midroid.masarifi.sms.SmsTxnType
import com.midroid.masarifi.ui.AppViewModel
import com.midroid.masarifi.ui.components.SectionTitle
import com.midroid.masarifi.ui.components.SettingRow
import com.midroid.masarifi.ui.components.SwitchRow
import com.midroid.masarifi.ui.components.AppIcons
import com.midroid.masarifi.ui.theme.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsSettingsScreen(vm: AppViewModel, onBack: () -> Unit, snackbar: SnackbarHostState, onRepair: () -> Unit = {}) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val rules by vm.merchantRules.collectAsStateWithLifecycle()
    val cats by vm.categories.collectAsStateWithLifecycle()
    val imported by vm.smsImportedCount.collectAsStateWithLifecycle()
    val scanning by vm.scanning.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val c = AppTheme.colors
    val deniedMsg = stringResource(R.string.sms_permission_denied)
    val scanFmt = stringResource(R.string.scan_result)

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        val ok = res[Manifest.permission.READ_SMS] == true && res[Manifest.permission.RECEIVE_SMS] == true
        if (ok) vm.updateSettings { it.copy(smsEnabled = true) } else vm.message(deniedMsg)
    }
    var extra by rememberSaveable { mutableStateOf(s.extraSenders) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sms_import)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.sms_intro), fontSize = 13.sp, color = c.muted, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            SwitchRow(AppIcons.Sms, stringResource(R.string.sms_enable), stringResource(R.string.sms_enable_sub), s.smsEnabled) { v ->
                if (v) {
                    if (SmsImporter.hasPermission(ctx) && SmsImporter.hasPermission(ctx, Manifest.permission.RECEIVE_SMS)) vm.updateSettings { it.copy(smsEnabled = true) }
                    else permLauncher.launch(arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS))
                } else vm.updateSettings { it.copy(smsEnabled = false) }
            }
            if (!SmsImporter.hasPermission(ctx) || !SmsImporter.hasPermission(ctx, Manifest.permission.RECEIVE_SMS)) {
                SettingRow(null, stringResource(R.string.open_app_info), stringResource(R.string.open_app_info_sub), onClick = {
                    runCatching {
                        ctx.startActivity(
                            android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", ctx.packageName, null))
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                })
            }
            SwitchRow(null, stringResource(R.string.sms_notify), stringResource(R.string.sms_notify_sub), s.smsNotify) { v -> vm.updateSettings { it.copy(smsNotify = v) } }
            SwitchRow(null, stringResource(R.string.sms_unknown), stringResource(R.string.sms_unknown_sub), s.smsAcceptUnknown) { v -> vm.updateSettings { it.copy(smsAcceptUnknown = v) } }

            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    enabled = s.smsEnabled && !scanning,
                    onClick = { vm.scanInbox { r -> String.format(java.util.Locale.US, scanFmt, r.scanned, r.imported) } },
                ) {
                    if (scanning) { CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White); Spacer(Modifier.width(8.dp)) }
                    Text(stringResource(R.string.scan_inbox))
                }
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.imported_count, imported), fontSize = 13.sp, color = c.muted)
            }
            Text(stringResource(R.string.scan_inbox_sub), fontSize = 12.sp, color = c.muted, modifier = Modifier.padding(horizontal = 16.dp))
            val rescanFmt = stringResource(R.string.rescan_result)
            OutlinedButton(
                enabled = s.smsEnabled && !scanning,
                onClick = { vm.rescanInbox { r -> String.format(java.util.Locale.US, rescanFmt, r.scanned.toString(), r.imported.toString(), r.linked.toString()) } },
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
            ) { Text(stringResource(R.string.rescan_sms)) }
            Text(stringResource(R.string.rescan_sms_sub), fontSize = 12.sp, color = c.muted, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))

            SettingRow(null, stringResource(R.string.repair_title), stringResource(R.string.repair_sub), onClick = onRepair)
            HorizontalDivider(color = c.divider)

            CardMappingsSection(vm, s.arabic)

            WalletTopupsSection(vm, s.arabic, s.walletTopupList)

            SectionTitle(stringResource(R.string.sms_senders))
            Text(
                DefaultSmsConfig.banks.joinToString("\n") { b -> "• " + (if (s.arabic) b.nameAr else b.nameEn) + ": " + b.senderIds.joinToString(", ") },
                fontSize = 12.sp, color = c.muted, modifier = Modifier.padding(horizontal = 16.dp),
            )
            OutlinedTextField(
                extra, { extra = it }, Modifier.fillMaxWidth().padding(16.dp),
                label = { Text(stringResource(R.string.extra_senders)) }, supportingText = { Text(stringResource(R.string.extra_senders_hint)) },
            )
            OutlinedButton(onClick = { vm.updateSettings { it.copy(extraSenders = extra) } }, modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(stringResource(R.string.save))
            }

            SectionTitle(stringResource(R.string.parser_test))
            ParserTester(vm)

            SectionTitle(stringResource(R.string.learned_rules))
            if (rules.isEmpty()) Text(stringResource(R.string.learned_rules_empty), fontSize = 12.sp, color = c.muted, modifier = Modifier.padding(horizontal = 16.dp))
            rules.forEach { r ->
                val cat = cats.firstOrNull { it.id == r.categoryId }
                SettingRow(null, r.merchantKey, cat?.let { it.emoji + " " + it.name(s.arabic) } ?: "—") {
                    IconButton(onClick = { vm.deleteMerchantRule(r) }) { Icon(Icons.Filled.Close, stringResource(R.string.delete), tint = c.muted) }
                }
                HorizontalDivider(color = c.divider)
            }
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.sms_play_note), fontSize = 11.sp, color = c.muted, modifier = Modifier.padding(16.dp))
        }
    }
}

@Composable
private fun WalletTopupsSection(vm: AppViewModel, arabic: Boolean, list: List<WalletTopup>) {
    val c = AppTheme.colors
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Pair<Int, WalletTopup>?>(null) }
    SectionTitle(stringResource(R.string.wallet_topups))
    Text(stringResource(R.string.wallet_topups_sub), fontSize = 12.sp, color = c.muted, modifier = Modifier.padding(horizontal = 16.dp))
    list.forEachIndexed { i, t ->
        SettingRow(
            null, t.name(arabic).ifBlank { t.patterns.firstOrNull() ?: "—" },
            ltr(t.patterns.joinToString(", ")) + "\n→ " + vm.topupTargetName(t, accounts, arabic),
            onClick = { editing = i to t },
        ) {
            IconButton(onClick = { vm.setWalletTopups(list.filterIndexed { j, _ -> j != i }) }) { Icon(Icons.Filled.Close, stringResource(R.string.delete), tint = c.muted) }
        }
        HorizontalDivider(color = c.divider)
    }
    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        OutlinedButton(onClick = { editing = -1 to WalletTopup("w" + System.currentTimeMillis(), "", "", emptyList()) }) { Text(stringResource(R.string.wallet_topup_add)) }
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = { vm.setWalletTopups(null) }) { Text(stringResource(R.string.wallet_topup_reset)) }
    }
    editing?.let { (index, t) ->
        TopupDialog(t, accounts, arabic, autoName = { vm.topupTargetName(it, accounts, arabic) }, onDismiss = { editing = null }) { edited ->
            vm.setWalletTopups(if (index < 0) list + edited else list.mapIndexed { j, x -> if (j == index) edited else x })
            editing = null
        }
    }
}

@Composable
private fun TopupDialog(
    t: WalletTopup, accounts: List<com.midroid.masarifi.data.Account>, arabic: Boolean, autoName: (WalletTopup) -> String,
    onDismiss: () -> Unit, onSave: (WalletTopup) -> Unit,
) {
    var name by remember { mutableStateOf(t.name(arabic)) }
    var patterns by remember { mutableStateOf(t.patterns.joinToString(", ")) }
    var target by remember { mutableStateOf(t.targetAccountUuid) }
    val autoTarget = autoName(t.copy(targetAccountUuid = null))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.wallet_topup_edit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.wallet_topup_name)) }, singleLine = true)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    patterns, { patterns = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.wallet_topup_patterns)) },
                    supportingText = { Text(stringResource(R.string.wallet_topup_patterns_hint)) },
                )
                Text(stringResource(R.string.wallet_topup_target), fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                val options = (if (t.targetBankId != null) listOf<String?>(null) else emptyList()) + accounts.map { it.uuid }
                options.forEach { u ->
                    Row(Modifier.fillMaxWidth().clickable { target = u }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = target == u || (target == null && u == null), onClick = { target = u })
                        Text(if (u == null) stringResource(R.string.wallet_topup_target_auto, autoTarget) else accounts.first { it.uuid == u }.name, fontSize = 14.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = patterns.isNotBlank() && (target != null || t.targetBankId != null),
                onClick = {
                    val n = name.trim()
                    onSave(
                        t.copy(
                            nameAr = if (arabic) n else t.nameAr.ifBlank { n }, nameEn = if (arabic) t.nameEn.ifBlank { n } else n,
                            patterns = patterns.split(',', '\n', '،').map { it.trim() }.filter { it.isNotEmpty() },
                            targetAccountUuid = target,
                        ),
                    )
                },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun ParserTester(vm: AppViewModel) {
    val c = AppTheme.colors
    var sender by rememberSaveable { mutableStateOf("BankMuscat") }
    var body by rememberSaveable { mutableStateOf("") }
    val result = remember(sender, body) { if (body.isBlank()) null else runCatching { vm.repo.parser().parse(sender, body) }.getOrNull() }
    Column(Modifier.padding(horizontal = 16.dp)) {
        OutlinedTextField(sender, { sender = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.sender)) }, singleLine = true)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(body, { body = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.sms_text)) }, minLines = 3)
        Spacer(Modifier.height(6.dp))
        if (body.isNotBlank()) {
            Column(Modifier.fillMaxWidth().background(c.card, RoundedCornerShape(8.dp)).padding(12.dp)) {
                if (result == null) {
                    Text(stringResource(R.string.parser_no_match), color = c.muted, fontSize = 13.sp)
                    Text(stringResource(R.string.parser_ignored_hint), color = c.muted, fontSize = 12.sp)
                }
                else {
                    val amountText = ltr("${result.currency} ${result.amount.toPlainString()}")
                    val topup = result.topup
                    if (topup != null) {
                        val accounts by vm.accounts.collectAsStateWithLifecycle()
                        val ar = vm.settings.collectAsStateWithLifecycle().value.arabic
                        Text(stringResource(R.string.parser_transfer_to, vm.topupTargetName(topup, accounts, ar)), color = c.transfer)
                        Text(amountText, color = c.transfer)
                    } else {
                        Text(
                            (if (result.type == SmsTxnType.DEBIT) stringResource(R.string.expense) else stringResource(R.string.income)) + ": $amountText",
                            color = if (result.type == SmsTxnType.DEBIT) c.expense else c.income,
                        )
                    }
                    Text("${stringResource(R.string.merchant)}: ${result.merchant?.let(::ltr) ?: "—"}", fontSize = 13.sp)
                    if (result.categoryHint == com.midroid.masarifi.sms.CategoryHints.P2P) Text(stringResource(R.string.parser_category_p2p), fontSize = 13.sp)
                    Text("${stringResource(R.string.card)}: ${result.cardLast4?.let { ltr("••$it") } ?: "—"}", fontSize = 13.sp)
                    Text("${stringResource(R.string.date)}: ${result.dateTime?.let { d -> ltr(if (d.toLocalTime() == java.time.LocalTime.NOON) d.toLocalDate().toString() else d.toString().replace('T', ' ')) } ?: "—"}", fontSize = 13.sp)
                    Text("${stringResource(R.string.parser_balance)}: ${result.balance?.let { ltr(it.toPlainString()) } ?: "—"}", fontSize = 13.sp)
                    result.ref?.let { Text("${stringResource(R.string.parser_ref)}: ${ltr(it)}", fontSize = 13.sp) }
                    Text("${stringResource(R.string.bank)}: ${result.bankName ?: "—"}  •  rule: ${result.ruleId}", fontSize = 12.sp, color = c.muted)
                }
            }
        }
    }
}

@Composable
private fun CardMappingsSection(vm: AppViewModel, arabic: Boolean) {
    val c = AppTheme.colors
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val maps by vm.cardMappings.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<com.midroid.masarifi.data.CardMapping?>(null) }
    val anyBank = stringResource(R.string.card_map_any_bank)
    fun bankName(id: String?) = DefaultSmsConfig.banks.firstOrNull { it.id == id }?.let { if (arabic) it.nameAr else it.nameEn } ?: anyBank
    SectionTitle(stringResource(R.string.card_maps))
    Text(stringResource(R.string.card_maps_sub), fontSize = 12.sp, color = c.muted, modifier = Modifier.padding(horizontal = 16.dp))
    if (maps.isEmpty()) Text(stringResource(R.string.card_maps_empty), fontSize = 12.sp, color = c.muted, modifier = Modifier.padding(16.dp))
    maps.forEach { m ->
        val acc = accounts.firstOrNull { it.id == m.accountId }?.name ?: "—"
        SettingRow(null, bankName(m.bankId) + "  " + ltr("••" + m.last4) + (if (m.label.isNotBlank()) " · " + m.label else ""), "→ $acc", onClick = { editing = m }) {
            IconButton(onClick = { vm.deleteCardMapping(m) }) { Icon(Icons.Filled.Close, stringResource(R.string.delete), tint = c.muted) }
        }
        HorizontalDivider(color = c.divider)
    }
    OutlinedButton(onClick = { editing = com.midroid.masarifi.data.CardMapping(last4 = "") }, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(stringResource(R.string.card_map_add))
    }
    editing?.let { m ->
        CardMappingDialog(m, accounts, ::bankName, onDismiss = { editing = null }) { vm.saveCardMapping(it); editing = null }
    }
}

@Composable
private fun CardMappingDialog(
    m: com.midroid.masarifi.data.CardMapping, accounts: List<com.midroid.masarifi.data.Account>, bankName: (String?) -> String,
    onDismiss: () -> Unit, onSave: (com.midroid.masarifi.data.CardMapping) -> Unit,
) {
    var bank by remember { mutableStateOf(m.bankId) }
    var last4 by remember { mutableStateOf(m.last4) }
    var label by remember { mutableStateOf(m.label) }
    var account by remember { mutableStateOf(m.accountId ?: accounts.firstOrNull()?.id) }
    val valid = last4.length == 4 && last4.all { it in '0'..'9' }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.card_map_edit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    last4, { v -> last4 = v.filter { it.isDigit() }.map { ch -> if (ch in '٠'..'٩') '0' + (ch - '٠') else ch }.joinToString("").take(4) },
                    Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.card_last4)) }, singleLine = true,
                    isError = last4.isNotEmpty() && !valid,
                    supportingText = { Text(stringResource(if (last4.isNotEmpty() && !valid) R.string.card_map_last4_error else R.string.card_last4_hint)) },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                )
                OutlinedTextField(label, { label = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.card_map_label)) }, singleLine = true)
                Text(stringResource(R.string.card_map_bank), fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                (listOf<String?>(null) + DefaultSmsConfig.banks.map { it.id }).forEach { id ->
                    Row(Modifier.fillMaxWidth().clickable { bank = id }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = bank == id, onClick = { bank = id }); Text(bankName(id), fontSize = 14.sp)
                    }
                }
                Text(stringResource(R.string.card_map_account), fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                accounts.forEach { a ->
                    Row(Modifier.fillMaxWidth().clickable { account = a.id }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = account == a.id, onClick = { account = a.id }); Text(a.name, fontSize = 14.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid && account != null, onClick = { onSave(m.copy(bankId = bank, last4 = last4, label = label.trim(), accountId = account)) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
