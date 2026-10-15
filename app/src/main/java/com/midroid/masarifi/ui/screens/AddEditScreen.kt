package com.midroid.masarifi.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.midroid.masarifi.R
import com.midroid.masarifi.data.Account
import com.midroid.masarifi.data.Category
import com.midroid.masarifi.data.Repeat
import com.midroid.masarifi.data.Txn
import com.midroid.masarifi.data.TxnType
import com.midroid.masarifi.ui.AppViewModel
import com.midroid.masarifi.ui.Fmt
import com.midroid.masarifi.ui.Route
import com.midroid.masarifi.ui.components.Chip
import com.midroid.masarifi.ui.components.ConfirmDialog
import com.midroid.masarifi.ui.theme.AppTheme
import com.midroid.masarifi.util.Money
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun AddEditScreen(vm: AppViewModel, route: Route.Edit, onBack: () -> Unit, onEditCategories: () -> Unit, onOpenTxn: (Long) -> Unit = {}) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val txns by vm.txns.collectAsStateWithLifecycle()
    val c = AppTheme.colors

    val existing: Txn? = remember(route.txnId, txns) { route.txnId?.let { id -> txns.firstOrNull { it.id == id } } }
    var loaded by rememberSaveable { mutableStateOf(route.txnId == null) }

    var type by rememberSaveable { mutableIntStateOf(route.type) }
    var dateTime by rememberSaveable { mutableLongStateOf(route.date ?: System.currentTimeMillis()) }
    var accountId by rememberSaveable { mutableStateOf<Long?>(null) }
    var toAccountId by rememberSaveable { mutableStateOf<Long?>(null) }
    var categoryId by rememberSaveable { mutableStateOf<Long?>(null) }
    var amountText by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var repeat by rememberSaveable { mutableIntStateOf(Repeat.NONE) }
    /** For existing rows: the current repetition is read from the linked repeat rule before it can be changed. */
    var repeatLoaded by rememberSaveable { mutableStateOf(route.txnId == null) }
    LaunchedEffect(existing?.id) {
        if (!repeatLoaded && existing != null) { repeat = vm.repeatOf(existing); repeatLoaded = true }
    }

    LaunchedEffect(existing) {
        if (!loaded && existing != null) {
            type = existing.type; dateTime = existing.dateTime; accountId = existing.accountId; toAccountId = existing.toAccountId
            categoryId = existing.categoryId; amountText = Money.toInput(existing.amount, s.currencyInfo); note = existing.note
            description = existing.description; loaded = true
        }
    }
    LaunchedEffect(accounts) { if (accountId == null && route.txnId == null) accountId = accounts.firstOrNull()?.id }

    var picker by remember { mutableStateOf<String?>(null) } // "date","time","account","to","category"
    var confirmDelete by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    /** Possible duplicate found when saving a new transaction: (draft, existing matches, "continue" mode). */
    var dupWarning by remember { mutableStateOf<Triple<Txn, List<Txn>, Boolean>?>(null) }
    val labels = rememberLabels(vm)
    val splitDone = stringResource(R.string.dup_split_done)

    val typeColor = when (type) { TxnType.INCOME -> c.income; TxnType.EXPENSE -> c.expense; else -> c.transfer }
    val accountName = accounts.firstOrNull { it.id == accountId }?.name ?: ""
    val toAccountName = accounts.firstOrNull { it.id == toAccountId }?.name ?: ""
    val cat = categories.firstOrNull { it.id == categoryId }
    val errAmount = stringResource(R.string.err_amount)
    val errAccount = stringResource(R.string.err_account)
    val errSame = stringResource(R.string.err_same_account)

    fun buildTxn(): Txn? {
        val amount = Money.parse(amountText)
        if (amount == null || amount <= 0) { error = errAmount; return null }
        val acc = accountId ?: run { error = errAccount; return null }
        if (type == TxnType.TRANSFER) {
            if (toAccountId == null) { error = errAccount; return null }
            if (toAccountId == acc) { error = errSame; return null }
        }
        val base = existing ?: Txn(type = type, amount = amount, accountId = acc, dateTime = dateTime)
        return base.copy(
            type = type, amount = amount, accountId = acc,
            toAccountId = if (type == TxnType.TRANSFER) toAccountId else null,
            categoryId = if (type == TxnType.TRANSFER) null else categoryId?.takeIf { id -> categories.any { it.id == id && it.type == type } },
            note = note.trim(), description = description.trim(), dateTime = dateTime,
        )
    }

    fun resetForNext() { amountText = ""; note = ""; description = ""; error = null; repeat = Repeat.NONE; dupWarning = null }
    fun doSave(t: Txn, continueMode: Boolean) {
        vm.saveTxn(t, if (repeatLoaded) repeat else null)
        if (continueMode) resetForNext() else onBack()
    }
    fun attemptSave(continueMode: Boolean) {
        val t = buildTxn() ?: return
        if (existing == null) {
            val conflicts = vm.conflictsFor(t)
            if (conflicts.isNotEmpty()) { dupWarning = Triple(t, conflicts, continueMode); return }
        }
        doSave(t, continueMode)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(when (type) { TxnType.INCOME -> R.string.income; TxnType.EXPENSE -> R.string.expense; else -> R.string.transfer }),
                    )
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                actions = {
                    if (existing != null) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, stringResource(R.string.delete)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(TxnType.INCOME to R.string.income, TxnType.EXPENSE to R.string.expense, TxnType.TRANSFER to R.string.transfer).forEach { (t, label) ->
                    val col = when (t) { TxnType.INCOME -> c.income; TxnType.EXPENSE -> c.expense; else -> c.transfer }
                    Box(
                        Modifier.weight(1f)
                            .border(1.dp, if (type == t) col else c.divider, RoundedCornerShape(6.dp))
                            .clickable { type = t; if (categories.none { it.id == categoryId && it.type == t }) categoryId = null }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(stringResource(label), color = if (type == t) col else c.muted, fontWeight = FontWeight.Medium) }
                }
            }

            FieldRow(stringResource(R.string.date)) {
                Text(Fmt.dateTimeLabel(dateTime, s.arabic), Modifier.clickable { picker = "date" }.padding(vertical = 4.dp))
            }
            if (type == TxnType.TRANSFER) {
                FieldRow(stringResource(R.string.from)) { ValueText(accountName) { picker = "account" } }
                FieldRow(stringResource(R.string.to)) { ValueText(toAccountName) { picker = "to" } }
            } else {
                FieldRow(stringResource(R.string.account)) { ValueText(accountName) { picker = "account" } }
                FieldRow(stringResource(R.string.category)) { ValueText(cat?.let { it.emoji + " " + it.name(s.arabic) } ?: "") { picker = "category" } }
            }
            FieldRow(stringResource(R.string.amount)) {
                PlainField(amountText, { amountText = it }, KeyboardType.Decimal, suffix = s.currencyInfo.symbol(s.arabic), color = typeColor)
            }
            FieldRow(stringResource(R.string.note)) { PlainField(note, { note = it }, KeyboardType.Text) }
            FieldRow(stringResource(R.string.description)) { PlainField(description, { description = it }, KeyboardType.Text, singleLine = false) }
            // Always shown — new, edited, SMS-linked and imported rows alike (1.3.0 fix: it was hidden when editing).
            run {
                FieldRow(stringResource(R.string.repeat)) {
                    var open by remember { mutableStateOf(false) }
                    Box {
                        ValueText(repeatLabel(repeat)) { open = true }
                        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                            Repeat.all.forEach { r -> DropdownMenuItem(text = { Text(repeatLabel(r)) }, onClick = { repeat = r; open = false }) }
                        }
                    }
                }
                if (existing?.recurringId != null && repeat != Repeat.NONE) {
                    Text(stringResource(R.string.part_of_repeat), color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                }
            }
            if (existing?.isAuto == true) {
                Column(Modifier.padding(16.dp).fillMaxWidth().background(c.card, RoundedCornerShape(8.dp)).padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Chip("SMS"); Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.auto_recorded_info), fontSize = 12.sp, color = c.muted)
                    }
                }
            }
            if (existing?.smsLinked == true) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().background(c.card, RoundedCornerShape(8.dp)).padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Chip("SMS"); Spacer(Modifier.width(6.dp))
                        Icon(com.midroid.masarifi.ui.components.AppIcons.Link, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.dup_linked_info), fontSize = 12.sp, color = c.muted)
                    }
                    TextButton(onClick = { vm.splitLinked(existing.id, splitDone); onBack() }) { Text(stringResource(R.string.dup_split)) }
                }
            }
            if (existing?.possibleDupOf != null) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Chip(stringResource(R.string.dup_possible_badge), bg = com.midroid.masarifi.ui.theme.Brand.Orange.copy(alpha = 0.25f))
                    Spacer(Modifier.width(8.dp))
                    existing.possibleDupOf.split(',').firstNotNullOfOrNull { it.trim().toLongOrNull() }?.let { other ->
                        TextButton(onClick = { onOpenTxn(other) }) { Text(stringResource(R.string.dup_open_existing)) }
                    }
                }
            }
            dupWarning?.let { (draft, conflicts, continueMode) ->
                val first = conflicts.first()
                val smsMatch = conflicts.firstOrNull { com.midroid.masarifi.data.dup.DuplicateDetector.isSms(it) }
                Column(
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()
                        .border(1.dp, com.midroid.masarifi.ui.theme.Brand.Orange, RoundedCornerShape(8.dp))
                        .background(com.midroid.masarifi.ui.theme.Brand.Orange.copy(alpha = 0.10f), RoundedCornerShape(8.dp)).padding(12.dp),
                ) {
                    Text(
                        "⚠️ " + stringResource(R.string.dup_warn_title, Fmt.dateTimeLabel(first.dateTime, s.arabic).let { d -> if (s.arabic) "\u2067$d\u2069" else ltr(d) }),
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(4.dp))
                    com.midroid.masarifi.ui.components.TxnRow(first, labels.category(first), labels.account(first), s, onClick = { onOpenTxn(first.id) }, showDate = true)
                    if (conflicts.size > 1) Text(stringResource(R.string.dup_warn_more, (conflicts.size - 1).toString()), fontSize = 12.sp, color = c.muted)
                    if (smsMatch != null) Text(stringResource(R.string.dup_merge_hint), fontSize = 12.sp, color = c.muted)
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (smsMatch != null) {
                            Button(onClick = {
                                vm.mergeIntoExisting(smsMatch.id, draft)
                                if (continueMode) resetForNext() else onBack()
                            }) { Text(stringResource(R.string.dup_merge)) }
                        }
                        OutlinedButton(onClick = { dupWarning = null; doSave(draft, continueMode) }) { Text(stringResource(R.string.dup_save_anyway)) }
                        TextButton(onClick = { onOpenTxn(first.id) }) { Text(stringResource(R.string.dup_open_existing)) }
                        TextButton(onClick = { dupWarning = null }) { Text(stringResource(R.string.cancel)) }
                    }
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { attemptSave(continueMode = false) },
                    modifier = Modifier.weight(2f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(8.dp),
                ) { Text(stringResource(R.string.save), color = Color.White) }
                if (existing == null) {
                    OutlinedButton(
                        onClick = { attemptSave(continueMode = true) },
                        modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(8.dp),
                    ) { Text(stringResource(R.string.continue_add)) }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    when (picker) {
        "date" -> {
            val initialUtc = Fmt.toDate(dateTime).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
            val state = rememberDatePickerState(initialSelectedDateMillis = initialUtc)
            DatePickerDialog(
                onDismissRequest = { picker = null },
                confirmButton = {
                    TextButton(onClick = {
                        state.selectedDateMillis?.let { sel ->
                            val d = Instant.ofEpochMilli(sel).atZone(ZoneOffset.UTC).toLocalDate()
                            dateTime = Fmt.toMillis(LocalDateTime.of(d, Fmt.toDateTime(dateTime).toLocalTime()))
                        }
                        picker = "time"
                    }) { Text(stringResource(R.string.ok)) }
                },
                dismissButton = { TextButton(onClick = { picker = null }) { Text(stringResource(R.string.cancel)) } },
            ) { DatePicker(state = state) }
        }
        "time" -> {
            val cur = Fmt.toDateTime(dateTime)
            val state = rememberTimePickerState(initialHour = cur.hour, initialMinute = cur.minute, is24Hour = true)
            AlertDialog(
                onDismissRequest = { picker = null },
                confirmButton = {
                    TextButton(onClick = {
                        dateTime = Fmt.toMillis(LocalDateTime.of(cur.toLocalDate(), LocalTime.of(state.hour, state.minute)))
                        picker = null
                    }) { Text(stringResource(R.string.ok)) }
                },
                dismissButton = { TextButton(onClick = { picker = null }) { Text(stringResource(R.string.cancel)) } },
                text = { TimePicker(state = state) },
            )
        }
        "account", "to" -> {
            val isTo = picker == "to"
            PickerSheet(stringResource(R.string.account), onDismiss = { picker = null }) {
                LazyVerticalGrid(GridCells.Fixed(3), Modifier.heightIn(max = 420.dp)) {
                    items(accounts) { a ->
                        GridCell(a.name, selected = (if (isTo) toAccountId else accountId) == a.id) {
                            if (isTo) toAccountId = a.id else accountId = a.id
                            picker = null
                        }
                    }
                }
            }
        }
        "category" -> {
            val list = categories.filter { it.type == type }
            PickerSheet(stringResource(R.string.category), onDismiss = { picker = null }, action = {
                TextButton(onClick = { picker = null; onEditCategories() }) {
                    Icon(Icons.Filled.Edit, null); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.edit))
                }
            }) {
                LazyVerticalGrid(GridCells.Fixed(3), Modifier.heightIn(max = 460.dp)) {
                    items(list) { cg ->
                        GridCell(cg.emoji + "\n" + cg.name(s.arabic), selected = categoryId == cg.id) { categoryId = cg.id; picker = null }
                    }
                }
            }
        }
    }

    if (confirmDelete && existing != null) {
        ConfirmDialog(
            stringResource(R.string.delete), stringResource(R.string.confirm_delete_txn), stringResource(R.string.delete),
            onConfirm = { vm.deleteTxn(existing); onBack() }, onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
fun repeatLabel(r: Int): String = stringResource(
    when (r) {
        Repeat.DAILY -> R.string.repeat_daily
        Repeat.WEEKLY -> R.string.repeat_weekly
        Repeat.MONTHLY -> R.string.repeat_monthly
        Repeat.YEARLY -> R.string.repeat_yearly
        else -> R.string.repeat_none
    },
)

@Composable
private fun FieldRow(label: String, content: @Composable () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = AppTheme.colors.muted, modifier = Modifier.width(90.dp), fontSize = 14.sp)
            Box(Modifier.weight(1f)) { content() }
        }
        HorizontalDivider(Modifier.padding(start = 16.dp, end = 16.dp), color = AppTheme.colors.divider)
    }
}

@Composable
private fun ValueText(text: String, onClick: () -> Unit) {
    Text(
        text.ifBlank { " " }, Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp), fontSize = 16.sp,
    )
}

@Composable
private fun PlainField(value: String, onChange: (String) -> Unit, keyboard: KeyboardType, suffix: String? = null, color: Color = Color.Unspecified, singleLine: Boolean = true) {
    TextField(
        value = value, onValueChange = onChange, singleLine = singleLine,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = if (color == Color.Unspecified) MaterialTheme.colorScheme.onBackground else color, fontWeight = if (suffix != null) FontWeight.SemiBold else FontWeight.Normal),
        suffix = suffix?.let { { Text(it) } },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = MaterialTheme.colorScheme.primary, unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickerSheet(title: String, onDismiss: () -> Unit, action: (@Composable () -> Unit)? = null, content: @Composable () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            action?.invoke()
        }
        Spacer(Modifier.height(8.dp))
        content()
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun GridCell(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(0.5.dp)
            .border(0.5.dp, AppTheme.colors.divider)
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent)
            .clickable(onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, textAlign = TextAlign.Center, fontSize = 14.sp, maxLines = 3) }
}
