package com.midroid.masarifi.ui.debts

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.res.stringResource
import com.midroid.masarifi.R
import com.midroid.masarifi.data.Account
import com.midroid.masarifi.data.AppSettings
import com.midroid.masarifi.data.Debt
import com.midroid.masarifi.data.DebtDirection
import com.midroid.masarifi.data.DebtLogic
import com.midroid.masarifi.ui.Fmt
import com.midroid.masarifi.util.Money

@Composable
fun DebtEditDialog(initial: Debt, s: AppSettings, onDismiss: () -> Unit, onSave: (Debt) -> Unit) {
    var person by remember(initial.id) { mutableStateOf(initial.person) }
    var dir by remember(initial.id) { mutableStateOf(initial.direction) }
    var amount by remember(initial.id) {
        mutableStateOf(if (initial.amount > 0) Money.toInput(initial.amount, s.currencyInfo) else "")
    }
    var note by remember(initial.id) { mutableStateOf(initial.note) }
    val parsed = Money.parse(amount)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (initial.id == 0L) R.string.debt_add else R.string.debt_edit))
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(person, { person = it }, label = { Text(stringResource(R.string.debt_person)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth()) {
                    FilterChip(dir == DebtDirection.LENT, { dir = DebtDirection.LENT }, label = { Text(stringResource(R.string.debt_lent)) })
                    FilterChip(dir == DebtDirection.OWE, { dir = DebtDirection.OWE }, label = { Text(stringResource(R.string.debt_owe)) })
                }
                OutlinedTextField(amount, { amount = it }, label = { Text(stringResource(R.string.debt_amount)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(note, { note = it }, label = { Text(stringResource(R.string.debt_note)) }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(initial.copy(person = person.trim(), direction = dir, amount = parsed ?: 0L, note = note.trim())) },
                enabled = person.isNotBlank() && parsed != null && parsed > 0,
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
@Composable
fun SettleDialog(debt: Debt, remaining: Long, accounts: List<Account>, s: AppSettings, onDismiss: () -> Unit, onSettle: (Long, Long?) -> Unit) {
    var amount by remember(debt.id, remaining) {
        mutableStateOf(Money.toInput(remaining, s.currencyInfo))
    }
    var record by remember { mutableStateOf(false) }
    var selectedId by remember(accounts) { mutableStateOf(accounts.firstOrNull()?.id) }
    val parsed = Money.parse(amount)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.debt_settle) + " · " + debt.person) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.debt_remaining, Fmt.money(remaining, s)))
                OutlinedTextField(amount, { amount = it }, label = { Text(stringResource(R.string.debt_settle_amount)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = { amount = Money.toInput(remaining, s.currencyInfo) }) {
                    Text(stringResource(R.string.debt_settle_full))
                }
                Row(Modifier.fillMaxWidth()) {
                    Checkbox(record, { record = it })
                    Text(stringResource(R.string.debt_record_txn))
                }
                if (record) {
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        accounts.forEach { account ->
                            FilterChip(selectedId == account.id, { selectedId = account.id }, label = { Text(account.name) })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSettle((parsed ?: 0L).coerceAtMost(remaining), if (record) selectedId else null) },
                enabled = parsed != null && parsed > 0,
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
@Composable
fun SplitDialog(accounts: List<Account>, s: AppSettings, onDismiss: () -> Unit, onSplit: (Long, List<String>, String, Long?) -> Unit) {
    var totalText by remember { mutableStateOf("") }
    var namesText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var record by remember { mutableStateOf(false) }
    var selectedId by remember(accounts) { mutableStateOf(accounts.firstOrNull()?.id) }
    val total = Money.parse(totalText) ?: 0L
    val names = namesText.split(',', '،').map { it.trim() }.filter { it.isNotEmpty() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.debt_split)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(totalText, { totalText = it }, label = { Text(stringResource(R.string.debt_split_total)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(namesText, { namesText = it }, label = { Text(stringResource(R.string.debt_split_people)) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(note, { note = it }, label = { Text(stringResource(R.string.debt_note)) }, modifier = Modifier.fillMaxWidth())
                if (total > 0 && names.isNotEmpty()) {
                    Text(stringResource(R.string.debt_split_share, Fmt.money(DebtLogic.split(total, names.size + 1)[1], s)))
                }
                Row(Modifier.fillMaxWidth()) {
                    Checkbox(checked = record, onCheckedChange = { record = it })
                    Text(stringResource(R.string.debt_split_create_expense))
                }
                if (record) {
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        accounts.forEach { account ->
                            FilterChip(selectedId == account.id, { selectedId = account.id }, label = { Text(account.name) })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSplit(total, names, note.trim(), if (record) selectedId else null) },
                enabled = total > 0 && names.isNotEmpty(),
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
