package com.midroid.masarifi.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.midroid.masarifi.R
import com.midroid.masarifi.data.TxnType
import com.midroid.masarifi.data.repair.RepairItem
import com.midroid.masarifi.data.repair.RepairKind
import com.midroid.masarifi.ui.AppViewModel
import com.midroid.masarifi.ui.Fmt
import com.midroid.masarifi.ui.components.SectionTitle
import com.midroid.masarifi.ui.theme.AppTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** «إصلاح العمليات التلقائية»: preview proposed fixes of SMS transactions, apply the checked ones, undo the last run. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepairScreen(vm: AppViewModel, onBack: () -> Unit, snackbar: SnackbarHostState) {
    val plan by vm.repairPlan.collectAsStateWithLifecycle()
    val busy by vm.repairBusy.collectAsStateWithLifecycle()
    val last by vm.lastRepair.collectAsStateWithLifecycle()
    val s by vm.settings.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val c = AppTheme.colors
    var unchecked by remember(plan) { mutableStateOf(emptySet<String>()) }
    val appliedFmt = stringResource(R.string.repair_applied)
    val undoneMsg = stringResource(R.string.repair_undone)
    LaunchedEffect(Unit) { if (plan == null) vm.scanRepair() }
    val fmt = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm") }
    fun accName(id: Long?) = when (id) {
        null -> ""
        RepairItem.CASH_PLACEHOLDER -> if (s.arabic) "نقد" else "Cash"
        else -> accounts.firstOrNull { it.id == id }?.name ?: "—"
    }
    fun describe(i: RepairItem): String {
        val t = i.after
        val date = Instant.ofEpochMilli(t.dateTime).atZone(ZoneId.systemDefault()).format(fmt)
        val what = when (t.type) {
            TxnType.TRANSFER -> accName(t.accountId) + " → " + accName(t.toAccountId)
            else -> accName(t.accountId)
        }
        return "${Fmt.money(t.amount, s)} · $what · $date" + (i.primary.merchant?.let { "\n$it" } ?: "")
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.repair_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Text(stringResource(R.string.repair_intro), fontSize = 13.sp, color = c.muted, modifier = Modifier.padding(16.dp))
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(enabled = !busy, onClick = { vm.scanRepair() }) { Text(stringResource(R.string.repair_scan)) }
                Spacer(Modifier.width(8.dp))
                last?.let { b -> TextButton(onClick = { vm.undoRepair(b.id, undoneMsg) }) { Text(stringResource(R.string.repair_undo, b.changes)) } }
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            }
            val items = plan
            if (items != null && items.isEmpty()) Text(stringResource(R.string.repair_none), modifier = Modifier.padding(16.dp))
            if (!items.isNullOrEmpty()) {
                val selected = items.filter { it.key !in unchecked }
                Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { unchecked = emptySet() }) { Text(stringResource(R.string.repair_select_all)) }
                    TextButton(onClick = { unchecked = items.map { it.key }.toSet() }) { Text(stringResource(R.string.repair_select_none)) }
                    Spacer(Modifier.weight(1f))
                    Button(enabled = selected.isNotEmpty() && !busy, onClick = { vm.applyRepair(selected) { n -> String.format(Fmt.locale(s.arabic), appliedFmt, n) } }) {
                        Text(stringResource(R.string.repair_apply, selected.size))
                    }
                }
                LazyColumn(Modifier.weight(1f)) {
                    for (kind in RepairKind.entries) {
                        val group = items.filter { it.kind == kind }
                        if (group.isEmpty()) continue
                        item(kind.name) { SectionTitle(kindTitle(kind) + " (" + group.size + ")") }
                        items(group, key = { it.key }) { i ->
                            val on = i.key !in unchecked
                            Row(
                                Modifier.fillMaxWidth().clickable { unchecked = if (on) unchecked + i.key else unchecked - i.key }.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked = on, onCheckedChange = { unchecked = if (on) unchecked + i.key else unchecked - i.key })
                                Text(describe(i), fontSize = 13.sp)
                            }
                            HorizontalDivider(color = c.divider)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun kindTitle(k: RepairKind) = stringResource(
    when (k) {
        RepairKind.MERGE_DOUBLE -> R.string.repair_kind_merge
        RepairKind.OWN_TRANSFER -> R.string.repair_kind_own
        RepairKind.FLIP_INCOME -> R.string.repair_kind_flip
        RepairKind.ATM_CASH -> R.string.repair_kind_atm
    },
)
