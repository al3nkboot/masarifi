package com.midroid.masarifi.ui.debts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.midroid.masarifi.R
import com.midroid.masarifi.data.*
import com.midroid.masarifi.ui.AppViewModel
import com.midroid.masarifi.ui.Fmt
import com.midroid.masarifi.ui.components.*
import com.midroid.masarifi.ui.theme.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebtsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val debts by vm.debts.collectAsStateWithLifecycle()
    val settlements by vm.debtSettlements.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    var showSettled by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Debt?>(null) }
    var acting by remember { mutableStateOf<Debt?>(null) }
    var settling by remember { mutableStateOf<Debt?>(null) }
    var deleting by remember { mutableStateOf<Debt?>(null) }
    var splitting by remember { mutableStateOf(false) }
    val totals = remember(debts, settlements) { DebtLogic.totals(debts, settlements) }
    val shown = debts.filter { DebtLogic.isSettled(it, settlements) == showSettled }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.debts)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    TextButton(onClick = { splitting = true }) {
                        Text(stringResource(R.string.debt_split))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editing = Debt(person = "", direction = DebtDirection.LENT, amount = 0, dateTime = System.currentTimeMillis())
            }) {
                Icon(Icons.Filled.Add, stringResource(R.string.debt_add))
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            item {
                SummaryCard(listOf(
                    Triple(stringResource(R.string.debt_owed_to_me), Fmt.money(totals.owedToMe, s, false), AppTheme.colors.income),
                    Triple(stringResource(R.string.debt_i_owe), Fmt.money(totals.iOwe, s, false), AppTheme.colors.expense),
                    Triple(stringResource(R.string.debt_net), Fmt.money(totals.net, s, false), MaterialTheme.colorScheme.onSurface),
                ))
            }
            item {
                Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(!showSettled, { showSettled = false }, { Text(stringResource(R.string.debt_tab_open)) })
                    FilterChip(showSettled, { showSettled = true }, { Text(stringResource(R.string.debt_tab_settled)) })
                }
            }
            if (shown.isEmpty()) item { EmptyState(stringResource(R.string.debt_empty)) }
            items(shown, key = { it.id }) { d ->
                val rem = DebtLogic.remaining(d, settlements)
                ListItem(
                    modifier = Modifier.testTag("debt_row").clickable { acting = d },
                    leadingContent = {
                        Text(
                            if (d.direction == DebtDirection.LENT) "↗" else "↘",
                            color = if (d.direction == DebtDirection.LENT) AppTheme.colors.income else AppTheme.colors.expense,
                            style = MaterialTheme.typography.titleLarge,
                        )
                    },
                    headlineContent = { Text(d.person) },
                    supportingContent = {
                        Text(
                            listOf(d.note, Fmt.dateLabel(d.dateTime, s.arabic)).filter { it.isNotBlank() }.joinToString(" · "),
                            maxLines = 1,
                        )
                    },
                    trailingContent = {
                        Text(
                            if (rem == 0L) stringResource(R.string.debt_settled) else "‎" + Fmt.money(rem, s),
                            color = when {
                                rem == 0L -> AppTheme.colors.muted
                                d.direction == DebtDirection.LENT -> AppTheme.colors.income
                                else -> AppTheme.colors.expense
                            },
                        )
                    },
                )
                HorizontalDivider()
            }
            if (!showSettled) {
                val people = DebtLogic.byPerson(debts, settlements)
                if (people.isNotEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.debt_by_person),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                    items(people) { (name, amt) ->
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                            Text(name, Modifier.weight(1f))
                            Text(
                                "‎" + Fmt.money(amt, s),
                                color = if (amt > 0) AppTheme.colors.income else AppTheme.colors.expense,
                            )
                        }
                    }
                }
            }
        }
    }

    acting?.let { d ->
        AlertDialog(
            onDismissRequest = { acting = null },
            title = { Text(d.person) },
            text = {
                Column {
                    TextButton(
                        onClick = { settling = d; acting = null },
                        enabled = !DebtLogic.isSettled(d, settlements),
                    ) { Text(stringResource(R.string.debt_settle)) }
                    TextButton(onClick = { editing = d; acting = null }) { Text(stringResource(R.string.debt_edit)) }
                    TextButton(onClick = { deleting = d; acting = null }) { Text(stringResource(R.string.delete)) }
                }
            },
            confirmButton = {
                TextButton(onClick = { acting = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
    editing?.let {
        DebtEditDialog(it, s, { editing = null }) { d -> vm.saveDebt(d); editing = null }
    }
    settling?.let { d ->
        SettleDialog(d, DebtLogic.remaining(d, settlements), accounts, s, { settling = null }) { amt, acc ->
            vm.settleDebt(d, amt, acc)
            settling = null
        }
    }
    deleting?.let { d ->
        ConfirmDialog(
            stringResource(R.string.debt_delete_title), stringResource(R.string.debt_delete_text),
            stringResource(R.string.delete),
            onConfirm = { vm.deleteDebt(d); deleting = null }, onDismiss = { deleting = null },
        )
    }
    if (splitting) {
        SplitDialog(accounts, s, { splitting = false }) { total, names, note, acc ->
            vm.splitExpense(total, names, note, acc, null)
            splitting = false
        }
    }
}
