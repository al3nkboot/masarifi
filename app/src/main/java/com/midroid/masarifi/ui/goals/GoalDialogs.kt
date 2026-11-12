package com.midroid.masarifi.ui.goals
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.unit.dp
import com.midroid.masarifi.R
import com.midroid.masarifi.data.*
import com.midroid.masarifi.ui.components.ConfirmDialog
import com.midroid.masarifi.util.Money
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GoalEditDialog(initial: SavingsGoal, accounts: List<Account>, s: AppSettings, onDismiss: () -> Unit,
    onSave: (SavingsGoal) -> Unit, onDelete: (() -> Unit)?) {
    var name by remember { mutableStateOf(initial.name) }
    var target by remember { mutableStateOf(if (initial.target > 0) Money.toInput(initial.target, s.currencyInfo) else "") }
    var targetDate by remember { mutableStateOf(initial.targetDate) }
    var accountId by remember { mutableStateOf(initial.accountId) }
    var note by remember { mutableStateOf(initial.note) }
    var showDatePicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val parsed = Money.parse(target)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial.id == 0L) R.string.goal_add else R.string.goal_edit)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.goal_name)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(target, { target = it }, label = { Text(stringResource(R.string.goal_target)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { showDatePicker = true }) {
                        Text(targetDate?.let { LocalDate.ofEpochDay(it).toString() } ?: stringResource(R.string.goal_target_date))
                    }
                    TextButton(onClick = { targetDate = null }) {
                        Text(stringResource(R.string.goal_no_date))
                    }
                }
                Text(stringResource(R.string.goal_account))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    FilterChip(
                        selected = accountId == null,
                        onClick = { accountId = null },
                        label = { Text(stringResource(R.string.goal_none)) }
                    )
                    accounts.forEach { account ->
                        FilterChip(
                            selected = accountId == account.id,
                            onClick = { accountId = account.id },
                            label = { Text(account.name) }
                        )
                    }
                }
                OutlinedTextField(note, { note = it }, label = { Text(stringResource(R.string.goal_note)) },
                    modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && parsed?.let { it > 0 } == true,
                onClick = {
                    onSave(initial.copy(
                        name = name.trim(),
                        target = parsed ?: 0L,
                        targetDate = targetDate,
                        accountId = accountId,
                        note = note.trim()
                    ))
                }
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = { confirmDelete = true }) { Text(stringResource(R.string.delete)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        }
    )
    if (showDatePicker) {
        val pickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        targetDate = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.cancel)) }
            }
        ) { DatePicker(state = pickerState) }
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.goal_delete_title),
            text = stringResource(R.string.goal_delete_text),
            confirm = stringResource(R.string.delete),
            onConfirm = { onDelete?.invoke() },
            onDismiss = { confirmDelete = false }
        )
    }
}
@Composable
fun ContributionDialog(title: String, s: AppSettings, onDismiss: () -> Unit, onSave: (Long, String) -> Unit) {
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val parsed = Money.parse(amount)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(amount, { amount = it }, label = { Text(stringResource(R.string.goal_amount)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                OutlinedTextField(note, { note = it }, label = { Text(stringResource(R.string.goal_note)) },
                    modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(
                enabled = parsed?.let { it > 0 } == true,
                onClick = { onSave(parsed ?: 0L, note.trim()) }
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
