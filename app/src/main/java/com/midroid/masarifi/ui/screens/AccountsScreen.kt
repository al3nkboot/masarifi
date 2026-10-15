package com.midroid.masarifi.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.midroid.masarifi.R
import com.midroid.masarifi.data.Account
import com.midroid.masarifi.data.AccountGroup
import com.midroid.masarifi.data.AppSettings
import com.midroid.masarifi.ui.AppViewModel
import com.midroid.masarifi.ui.Fmt
import com.midroid.masarifi.ui.Route
import com.midroid.masarifi.ui.components.ConfirmDialog
import com.midroid.masarifi.ui.theme.AppTheme
import com.midroid.masarifi.util.Money
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi

@Composable
fun groupLabel(g: Int): String = stringResource(
    when (g) {
        AccountGroup.CASH -> R.string.group_cash
        AccountGroup.BANK -> R.string.group_bank
        AccountGroup.CARD -> R.string.group_card
        AccountGroup.SAVINGS -> R.string.group_savings
        else -> R.string.group_other
    },
)

@Composable
fun AccountsScreen(vm: AppViewModel, navigate: (Route) -> Unit, modifier: Modifier) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val txns by vm.txns.collectAsStateWithLifecycle()
    val c = AppTheme.colors
    val balances = remember(accounts, txns) { vm.balances(accounts, txns) }
    var editing by remember { mutableStateOf<Account?>(null) }

    val included = balances.filter { it.account.includeInTotal }
    val assets = included.filter { it.balance > 0 }.sumOf { it.balance }
    val liabilities = included.filter { it.balance < 0 }.sumOf { it.balance }

    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.tab_accounts), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 12.dp).weight(1f))
            IconButton(onClick = { editing = Account(name = "", groupType = AccountGroup.BANK) }) { Icon(Icons.Filled.Add, stringResource(R.string.add_account)) }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            Cell(stringResource(R.string.assets), Fmt.money(assets, s, false), c.income)
            Cell(stringResource(R.string.liabilities), Fmt.money(-liabilities, s, false), c.expense)
            Cell(stringResource(R.string.total), Fmt.money(assets + liabilities, s, false), MaterialTheme.colorScheme.onBackground)
        }
        HorizontalDivider(color = c.divider)
        LazyColumn(Modifier.fillMaxSize()) {
            balances.groupBy { it.account.groupType }.toSortedMap().forEach { (g, list) ->
                item(key = "g$g") {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Text(groupLabel(g), color = c.muted, modifier = Modifier.weight(1f), fontSize = 13.sp)
                        Text(Fmt.money(list.sumOf { it.balance }, s), color = c.muted, fontSize = 13.sp)
                    }
                }
                items(list, key = { it.account.id }) { ab ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            navigate(Route.TxnList(ab.account.name, accountId = ab.account.id))
                        }.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(ab.account.name, fontSize = 16.sp)
                            if (ab.account.cardLast4 != null) Text("•••• ${ab.account.cardLast4}", fontSize = 12.sp, color = c.muted)
                        }
                        Text(Fmt.money(ab.balance, s), color = if (ab.balance >= 0) c.income else c.expense, fontWeight = FontWeight.Medium)
                        TextButton(onClick = { editing = ab.account }) { Text(stringResource(R.string.edit), fontSize = 12.sp) }
                    }
                    HorizontalDivider(color = c.divider)
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
    editing?.let { a -> AccountDialog(a, s, onDismiss = { editing = null }, onSave = { vm.saveAccount(it) }, onDelete = { vm.deleteAccount(it) }, canDelete = accounts.size > 1) }
}

@Composable
private fun Cell(title: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, fontSize = 12.sp, color = AppTheme.colors.muted)
        Text(value, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccountDialog(a: Account, s: AppSettings, onDismiss: () -> Unit, onSave: (Account) -> Unit, onDelete: (Account) -> Unit, canDelete: Boolean) {
    var name by remember { mutableStateOf(a.name) }
    var group by remember { mutableIntStateOf(a.groupType) }
    var balance by remember { mutableStateOf(if (a.initialBalance < 0) "-" + Money.toInput(-a.initialBalance, s.currencyInfo) else Money.toInput(a.initialBalance, s.currencyInfo)) }
    var last4 by remember { mutableStateOf(a.cardLast4 ?: "") }
    var include by remember { mutableStateOf(a.includeInTotal) }
    var confirm by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (a.id == 0L) R.string.add_account else R.string.edit_account)) },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.name)) }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AccountGroup.all.forEach { g -> FilterChip(selected = group == g, onClick = { group = g }, label = { Text(groupLabel(g)) }) }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    balance, { balance = it }, label = { Text(stringResource(R.string.initial_balance)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    last4, { if (it.length <= 4 && it.all(Char::isDigit)) last4 = it }, label = { Text(stringResource(R.string.card_last4)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = { Text(stringResource(R.string.card_last4_hint)) },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(include, { include = it }); Text(stringResource(R.string.include_in_total))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) return@TextButton
                val neg = balance.trim().startsWith("-")
                val v = Money.parse(balance.trim().removePrefix("-")) ?: 0L
                onSave(a.copy(name = name.trim(), groupType = group, initialBalance = if (neg) -v else v, cardLast4 = last4.ifBlank { null }, includeInTotal = include))
                onDismiss()
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            Row {
                if (a.id != 0L && canDelete) TextButton(onClick = { confirm = true }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
    if (confirm) ConfirmDialog(stringResource(R.string.delete), stringResource(R.string.confirm_delete_account), stringResource(R.string.delete), { onDelete(a); onDismiss() }, { confirm = false })
}
