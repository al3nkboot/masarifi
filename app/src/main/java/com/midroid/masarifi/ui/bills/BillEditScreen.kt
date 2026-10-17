package com.midroid.masarifi.ui.bills

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.midroid.masarifi.R
import com.midroid.masarifi.bills.Bill
import com.midroid.masarifi.bills.BillPayment
import com.midroid.masarifi.bills.RecurrenceType
import com.midroid.masarifi.data.Account
import com.midroid.masarifi.data.Category
import com.midroid.masarifi.data.Txn
import com.midroid.masarifi.data.TxnType
import com.midroid.masarifi.ui.AppViewModel
import com.midroid.masarifi.ui.Fmt
import com.midroid.masarifi.ui.components.ConfirmDialog
import com.midroid.masarifi.ui.components.SectionTitle
import com.midroid.masarifi.ui.theme.AppTheme
import com.midroid.masarifi.util.Money
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun recurrenceLabel(b: Bill): String = recurrenceLabel(b.type, b.recurInterval)

@Composable
fun recurrenceLabel(t: RecurrenceType, n: Int): String = when (t) {
    RecurrenceType.ONE_TIME -> stringResource(R.string.bill_rec_once)
    RecurrenceType.WEEKLY -> stringResource(R.string.bill_rec_weekly)
    RecurrenceType.MONTHLY -> stringResource(R.string.bill_rec_monthly)
    RecurrenceType.QUARTERLY -> stringResource(R.string.bill_rec_quarterly)
    RecurrenceType.YEARLY -> stringResource(R.string.bill_rec_yearly)
    RecurrenceType.CUSTOM_DAYS -> stringResource(R.string.bill_rec_every_days, n)
    RecurrenceType.CUSTOM_MONTHS -> stringResource(R.string.bill_rec_every_months, n)
}

/** Simple dropdown field used by the bill forms. */
@Composable
fun <T> PickField(label: String, value: String, options: List<T>, text: @Composable (T) -> String, onPick: (T) -> Unit, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    var open by remember { mutableStateOf(false) }
    // plain row (not a text field): text fields inside dialogs keep Robolectric screenshot tests from idling
    Box(modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.fillMaxWidth().clickable { if (onClick != null) onClick() else open = true }.padding(vertical = 6.dp, horizontal = 4.dp)) {
            Text(label, fontSize = 12.sp, color = AppTheme.colors.muted)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(value, fontSize = 16.sp, modifier = Modifier.weight(1f))
                Text("▾", color = AppTheme.colors.muted)
            }
            Spacer(Modifier.height(4.dp))
            androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { o -> DropdownMenuItem(text = { Text(text(o)) }, onClick = { onPick(o); open = false }) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillEditScreen(vm: AppViewModel, billId: Long?, onBack: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val bills by vm.bills.collectAsStateWithLifecycle()
    val allPayments by vm.billPayments.collectAsStateWithLifecycle()
    val cats by vm.categories.collectAsStateWithLifecycle()
    val accs by vm.accounts.collectAsStateWithLifecycle()
    val existing = billId?.let { id -> bills.firstOrNull { it.id == id } }
    val today = vm.today()
    var loaded by remember { mutableStateOf(billId == null) }
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var catId by remember { mutableStateOf<Long?>(null) }
    var accId by remember { mutableStateOf<Long?>(null) }
    var due by remember { mutableStateOf(today) }
    var type by remember { mutableStateOf(RecurrenceType.MONTHLY) }
    var interval by remember { mutableStateOf("1") }
    var remind by remember { mutableStateOf("3") }
    var notes by remember { mutableStateOf("") }
    var active by remember { mutableStateOf(true) }
    var picking by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var deletingPay by remember { mutableStateOf<BillPayment?>(null) }
    var paying by remember { mutableStateOf(false) }
    LaunchedEffect(existing?.id) {
        if (existing != null && !loaded) {
            name = existing.name; amount = Money.toInput(existing.amount, s.currencyInfo); catId = existing.categoryId; accId = existing.accountId
            due = existing.dueDate; type = existing.type; interval = existing.recurInterval.toString(); remind = existing.reminderDays.toString()
            notes = existing.notes; active = existing.active; loaded = true
        }
    }
    val expCats = cats.filter { it.type == TxnType.EXPENSE }
    val parsed = Money.parse(amount)
    val canSave = name.isNotBlank() && parsed != null && parsed > 0
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (billId == null) R.string.bill_add else R.string.bill_edit)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                actions = { if (existing != null) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, stringResource(R.string.delete)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.bill_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
            OutlinedTextField(amount, { amount = it }, label = { Text(stringResource(R.string.bill_amount)) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), suffix = { Text(s.currencyInfo.symbol(s.arabic)) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
            PickField(stringResource(R.string.bill_category), cats.firstOrNull { it.id == catId }?.let { it.emoji + " " + it.name(s.arabic) } ?: "—",
                listOf<Category?>(null) + expCats, { c -> c?.let { it.emoji + " " + it.name(s.arabic) } ?: "—" }, { catId = it?.id })
            PickField(stringResource(R.string.bill_account), accs.firstOrNull { it.id == accId }?.name ?: stringResource(R.string.bill_account_ask),
                listOf<Account?>(null) + accs, { a -> a?.name ?: "—" }, { accId = it?.id })
            Box(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                OutlinedTextField(dateLabel(due, s.arabic), {}, readOnly = true, enabled = false, label = { Text(stringResource(R.string.bill_due_date)) }, modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(disabledTextColor = MaterialTheme.colorScheme.onSurface, disabledLabelColor = AppTheme.colors.muted, disabledBorderColor = MaterialTheme.colorScheme.outline))
                Box(Modifier.matchParentSize().clickable { picking = true })
            }
            PickField(stringResource(R.string.bill_recurrence), recurrenceLabel(type, interval.toIntOrNull() ?: 1), RecurrenceType.entries.toList(), { recurrenceLabel(it, interval.toIntOrNull() ?: 1) }, { type = it })
            if (type == RecurrenceType.CUSTOM_DAYS || type == RecurrenceType.CUSTOM_MONTHS) {
                OutlinedTextField(interval, { interval = it.filter(Char::isDigit).take(3) }, label = { Text(stringResource(R.string.bill_interval)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
            }
            OutlinedTextField(remind, { remind = it.filter(Char::isDigit).take(2) }, label = { Text(stringResource(R.string.bill_remind_days)) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
            OutlinedTextField(notes, { notes = it }, label = { Text(stringResource(R.string.bill_notes)) }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                Text(stringResource(R.string.bill_active), Modifier.weight(1f)); Switch(active, { active = it })
            }
            Row {
                Button(
                    enabled = canSave,
                    onClick = {
                        val base = existing ?: Bill(name = "", amount = 0, anchorDay = due.dayOfMonth, nextDue = due.toEpochDay())
                        val anchor = if (existing == null || existing.nextDue != due.toEpochDay()) due.dayOfMonth else existing.anchorDay
                        vm.saveBill(base.copy(name = name.trim(), amount = parsed!!, categoryId = catId, accountId = accId, recurrence = type.name,
                            recurInterval = (interval.toIntOrNull() ?: 1).coerceIn(1, 999), anchorDay = anchor, nextDue = due.toEpochDay(),
                            reminderDays = (remind.toIntOrNull() ?: 3).coerceIn(0, 60), notes = notes.trim(), active = active))
                        onBack()
                    },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.save)) }
                if (existing != null && existing.active) {
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { paying = true }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.bill_paid_btn)) }
                }
            }
            if (existing != null) {
                val hist = allPayments.filter { it.billId == existing.id }
                SectionTitle(stringResource(R.string.bill_history_title) + " (${hist.size})", Modifier.padding(top = 8.dp))
                if (hist.isEmpty()) Text(stringResource(R.string.bill_no_history), color = AppTheme.colors.muted, fontSize = 13.sp)
                hist.forEach { p -> PaymentRow(p, existing.name, s) { deletingPay = p } }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
    if (picking) {
        val st = rememberDatePickerState(initialSelectedDateMillis = due.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(onDismissRequest = { picking = false }, confirmButton = {
            TextButton(onClick = { st.selectedDateMillis?.let { due = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }; picking = false }) { Text(stringResource(R.string.ok)) }
        }) { DatePicker(st) }
    }
    if (confirmDelete && existing != null) {
        ConfirmDialog(stringResource(R.string.delete), stringResource(R.string.bill_confirm_delete), stringResource(R.string.delete), { vm.deleteBill(existing); onBack() }, { confirmDelete = false })
    }
    deletingPay?.let { p -> ConfirmDialog(stringResource(R.string.delete), stringResource(R.string.bill_confirm_delete_payment), stringResource(R.string.delete), { vm.deleteBillPayment(p) }, { deletingPay = null }) }
    if (paying && existing != null) PayBillDialog(vm, existing) { paying = false }
}

/**
 * «تم الدفع»: amount, account and category of the expense to record. When a bank SMS expense of the same amount was
 * already recorded (±7 days), offers to merge with it (no double counting) instead of creating a new expense.
 */
@Composable
fun PayBillDialog(vm: AppViewModel, b: Bill, onDismiss: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val cats by vm.categories.collectAsStateWithLifecycle()
    val accs by vm.accounts.collectAsStateWithLifecycle()
    var amountMilli by remember { mutableStateOf(b.amount) }
    var editAmount by remember { mutableStateOf(false) }
    var accId by remember { mutableStateOf(b.accountId?.takeIf { id -> accs.any { it.id == id } } ?: accs.firstOrNull()?.id) }
    var catId by remember { mutableStateOf(b.categoryId) }
    var linkTo by remember { mutableStateOf<Long?>(null) }
    val parsed: Long? = amountMilli
    // looked up before the dialog appears so it opens at its final size (no jump when the SMS offer arrives)
    val loaded by produceState<List<Txn>?>(null, parsed) {
        val m = if (parsed != null && parsed > 0) vm.billSmsMatches(parsed) else emptyList()
        linkTo = m.firstOrNull()?.id
        value = m
    }
    val matches = loaded ?: return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.bill_pay_title, b.name)) },
        text = {
            Column {
                Text(stringResource(R.string.bill_for_due, dateLabel(b.dueDate, s.arabic)), fontSize = 13.sp, color = AppTheme.colors.muted)
                // amount as a tappable row (edited in the shared amount dialog) so this dialog holds no text field
                PickField(stringResource(R.string.bill_amount), Fmt.money(parsed ?: 0, s), emptyList<Unit>(), { "" }, {}, onClick = { editAmount = true })
                if (linkTo == null) {
                    PickField(stringResource(R.string.bill_account), accs.firstOrNull { it.id == accId }?.name ?: "—", accs, { it.name }, { accId = it.id })
                    PickField(stringResource(R.string.bill_category), cats.firstOrNull { it.id == catId }?.let { it.emoji + " " + it.name(s.arabic) } ?: "—",
                        cats.filter { it.type == TxnType.EXPENSE }, { it.emoji + " " + it.name(s.arabic) }, { catId = it.id })
                }
                if (matches.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.bill_sms_found), fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                    matches.take(3).forEach { t ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { linkTo = t.id }) {
                            RadioButton(linkTo == t.id, { linkTo = t.id })
                            Text(stringResource(R.string.bill_merge_with, (t.merchant ?: t.note).ifBlank { "SMS" }, Fmt.dateLabel(t.dateTime, s.arabic)), fontSize = 13.sp)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { linkTo = null }) {
                        RadioButton(linkTo == null, { linkTo = null })
                        Text(stringResource(R.string.bill_record_new), fontSize = 13.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = parsed != null && parsed > 0 && (linkTo != null || accId != null), onClick = {
                vm.payBill(b, parsed!!, accId ?: 0, catId, linkTo); onDismiss()
            }) { Text(stringResource(R.string.bill_paid_btn)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
    if (editAmount) com.midroid.masarifi.ui.components.AmountInputDialog(stringResource(R.string.bill_amount), amountMilli, s, onDismiss = { editAmount = false },
        onSave = { if (it > 0) amountMilli = it }, allowClear = false)
}
