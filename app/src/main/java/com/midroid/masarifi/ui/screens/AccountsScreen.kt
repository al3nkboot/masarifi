package com.midroid.masarifi.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.midroid.masarifi.R
import com.midroid.masarifi.data.Account
import com.midroid.masarifi.data.AccountGroup
import com.midroid.masarifi.data.AppSettings
import com.midroid.masarifi.data.Txn
import com.midroid.masarifi.ui.AccountBalance
import com.midroid.masarifi.ui.AppViewModel
import com.midroid.masarifi.ui.Fmt
import com.midroid.masarifi.ui.Route
import com.midroid.masarifi.ui.components.AppIcons
import com.midroid.masarifi.ui.components.ConfirmDialog
import com.midroid.masarifi.ui.components.EmptyState
import com.midroid.masarifi.ui.theme.AppTheme
import com.midroid.masarifi.ui.theme.Spacing
import com.midroid.masarifi.util.Money
import kotlinx.coroutines.launch

fun groupEmoji(g: Int): String = when (g) {
    AccountGroup.CASH -> "💵"
    AccountGroup.BANK -> "🏦"
    AccountGroup.CARD -> "💳"
    AccountGroup.SAVINGS -> "🐖"
    AccountGroup.INVESTMENT -> "📈"
    AccountGroup.LOAN -> "🧾"
    else -> "👛"
}

@Composable
fun groupLabel(g: Int): String = stringResource(
    when (g) {
        AccountGroup.CASH -> R.string.group_cash
        AccountGroup.BANK -> R.string.group_bank
        AccountGroup.CARD -> R.string.group_card
        AccountGroup.SAVINGS -> R.string.group_savings
        AccountGroup.INVESTMENT -> R.string.group_investment
        AccountGroup.LOAN -> R.string.group_loan
        else -> R.string.group_other
    },
)

private val MinusRed = Color(0xFFE5484D)

private sealed interface AccDlg {
    data class Edit(val account: Account) : AccDlg
    data class DeleteOptions(val account: Account, val count: Int) : AccDlg
    data class PickMove(val account: Account, val count: Int) : AccDlg
    data class ConfirmMove(val account: Account, val target: Account, val count: Int) : AccDlg
    data class ConfirmSoft(val account: Account) : AccDlg
    data class ConfirmWipe(val account: Account) : AccDlg
    data class PickMerge(val account: Account) : AccDlg
    data class ConfirmMerge(val source: Account, val target: Account) : AccDlg
}

@Composable
fun AccountsScreen(vm: AppViewModel, navigate: (Route) -> Unit, modifier: Modifier) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val deleted by vm.deletedAccounts.collectAsStateWithLifecycle()
    val txns by vm.txns.collectAsStateWithLifecycle()
    val c = AppTheme.colors
    val balances = remember(accounts, txns) { vm.balances(accounts, txns) }
    var editingMode by rememberSaveable { mutableStateOf(false) }
    var showHidden by rememberSaveable { mutableStateOf(false) }
    var showBin by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var dlg by remember { mutableStateOf<AccDlg?>(null) }

    val visible = balances.filter { showHidden || !it.account.hidden }
    // Hidden accounts stay in the header totals (Money Manager); includeInTotal is the flag that excludes them.
    val nw = remember(balances) { com.midroid.masarifi.data.NetWorthCalc.compute(balances.map { it.account to it.balance }) }
    val assets = nw.assets
    val liabilities = -nw.liabilities

    fun countFor(id: Long) = txns.count { it.accountId == id || it.toAccountId == id }
    fun others(except: Long) = accounts.filter { it.id != except }

    if (showBin) {
        DeletedAccountsScreen(
            vm = vm, s = s, deleted = deleted, txns = txns, onBack = { showBin = false },
            onDelete = { a -> dlg = AccDlg.DeleteOptions(a, countFor(a.id)) },
        )
        dlg?.let { d ->
            AccountFlowDialogs(d, s, others = { id -> others(id) }, countFor = ::countFor, onChange = { dlg = it }, vm = vm)
        }
        return
    }

    val listState = rememberLazyListState()
    val drag = remember { AccDragState() }
    val byGroup = visible.groupBy { it.account.groupType }.toSortedMap()

    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.tab_accounts), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 12.dp).weight(1f))
            IconButton(onClick = { editingMode = !editingMode }, modifier = Modifier.testTag("accounts_edit_mode")) {
                Icon(Icons.Outlined.Edit, stringResource(R.string.edit_mode), tint = if (editingMode) MaterialTheme.colorScheme.primary else c.muted)
            }
            IconButton(onClick = { dlg = AccDlg.Edit(Account(name = "", groupType = AccountGroup.BANK)) }) {
                Icon(Icons.Filled.Add, stringResource(R.string.add_account))
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.tab_more)) }
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.show_hidden_accounts)) },
                        onClick = { showHidden = !showHidden; menu = false },
                        trailingIcon = { if (showHidden) Icon(AppIcons.Visibility, null) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.deleted_accounts) + if (deleted.isNotEmpty()) " (${deleted.size})" else "") },
                        onClick = { showBin = true; menu = false },
                    )
                }
            }
        }
        com.midroid.masarifi.ui.components.SummaryCard(
            listOf(
                Triple(stringResource(R.string.assets), Fmt.money(assets, s, false), c.income),
                Triple(stringResource(R.string.liabilities), Fmt.money(-liabilities, s, false), c.expense),
                Triple(stringResource(R.string.net_worth), Fmt.money(nw.net, s, false), MaterialTheme.colorScheme.onSurface),
            ),
            footer = if (s.arabic) s.currencyInfo.nameAr else s.currencyInfo.nameEn,
        )
        LazyColumn(Modifier.fillMaxSize().testTag("accounts_list"), state = listState) {
            byGroup.forEach { (g, list) ->
                val dbIds = list.map { it.account.id }
                val ids = drag.resolve(g, dbIds)
                val byId = list.associateBy { it.account.id }
                val groupTotal = balances.filter { it.account.groupType == g }.sumOf { it.balance }
                item(key = "g$g") {
                    Row(
                        Modifier.fillMaxWidth().padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.md, bottom = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(groupLabel(g), style = MaterialTheme.typography.titleSmall, color = c.muted, modifier = Modifier.weight(1f))
                        Text("\u200E" + Fmt.money(groupTotal, s, false), style = com.midroid.masarifi.ui.theme.Amount, fontSize = 13.sp, color = c.muted)
                    }
                }
                ids.forEachIndexed { index, id ->
                    val ab = byId[id] ?: return@forEachIndexed
                    item(key = "a$id") {
                        val key = "a$id"
                        if (editingMode) {
                            AccountCustomizeRow(
                                ab = ab, s = s,
                                dragging = drag.key == key,
                                dragOffset = if (drag.key == key) drag.offset else 0f,
                                modifier = (if (drag.key == key) Modifier.zIndex(1f) else Modifier.animateItem()).fillMaxWidth(),
                                onClick = { navigate(Route.TxnList(ab.account.name, accountId = ab.account.id)) },
                                onDelete = { dlg = AccDlg.DeleteOptions(ab.account, countFor(ab.account.id)) },
                                onEdit = { dlg = AccDlg.Edit(ab.account) },
                                onToggleHidden = { vm.saveAccount(ab.account.copy(hidden = !ab.account.hidden)) },
                                handle = Modifier.accountReorderHandle(
                                    drag = drag, key = key, group = g, siblings = ids, listState = listState,
                                    onCommit = { vm.reorderAccounts(it) },
                                ),
                                canMoveUp = index > 0,
                                canMoveDown = index < ids.lastIndex,
                                onMove = { delta ->
                                    val next = ids.moved(index, index + delta)
                                    listState.requestScrollToItem(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
                                    drag.hold(g, next)
                                    vm.reorderAccounts(next)
                                },
                            )
                        } else {
                            AccountBrowseRow(ab, s, onOpen = { navigate(Route.TxnList(ab.account.name, accountId = ab.account.id)) }, onEdit = { dlg = AccDlg.Edit(ab.account) })
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
    dlg?.let { d ->
        AccountFlowDialogs(d, s, others = { id -> others(id) }, countFor = ::countFor, onChange = { dlg = it }, vm = vm)
    }
}

@Composable
private fun AccountBrowseRow(ab: AccountBalance, s: AppSettings, onOpen: () -> Unit, onEdit: () -> Unit) {
    val c = AppTheme.colors
    ListItem(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { com.midroid.masarifi.ui.components.CategoryBadge(groupEmoji(ab.account.groupType), MaterialTheme.colorScheme.secondary) },
        headlineContent = { Text(ab.account.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1) },
        supportingContent = {
            val bits = buildList {
                ab.account.cardLast4?.let { add("•••• $it") }
                if (ab.account.hidden) add(s.let { stringResource(R.string.account_hidden) })
            }
            if (bits.isNotEmpty()) Text(bits.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = c.muted)
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "\u200E" + Fmt.money(ab.balance, s, false), style = com.midroid.masarifi.ui.theme.Amount, fontSize = 15.sp,
                    color = if (!ab.account.includeInTotal) c.muted else if (ab.balance >= 0) c.income else c.expense,
                )
                IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, stringResource(R.string.edit_account_cd, ab.account.name), tint = c.muted, modifier = Modifier.size(20.dp)) }
            }
        },
    )
}

@Composable
private fun AccountCustomizeRow(
    ab: AccountBalance,
    s: AppSettings,
    dragging: Boolean,
    dragOffset: Float,
    modifier: Modifier,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onToggleHidden: () -> Unit,
    handle: Modifier,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMove: (Int) -> Unit,
) {
    val c = AppTheme.colors
    val name = ab.account.name
    val moveUp = stringResource(R.string.move_up)
    val moveDown = stringResource(R.string.move_down)
    val dragCd = stringResource(R.string.drag_to_reorder)
    Row(
        modifier
            .graphicsLayer { translationY = dragOffset; shadowElevation = if (dragging) 8f else 0f }
            .background(if (dragging) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.background)
            .clickable(onClick = onClick)
            .padding(start = Spacing.sm, end = 2.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val delCd = stringResource(R.string.delete_account_cd, name)
        IconButton(onClick = onDelete, modifier = Modifier.semantics { contentDescription = delCd }.testTag("delete_account_${ab.account.id}")) {
            Box(Modifier.size(22.dp).background(MinusRed, CircleShape), contentAlignment = Alignment.Center) {
                Box(Modifier.width(11.dp).height(2.5.dp).background(Color.White, RoundedCornerShape(2.dp)))
            }
        }
        Text(groupEmoji(ab.account.groupType), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            Text(
                "\u200E" + Fmt.money(ab.balance, s, false) + if (ab.account.hidden) " · ${stringResource(R.string.account_hidden)}" else "",
                style = MaterialTheme.typography.bodySmall,
                color = if (!ab.account.includeInTotal) c.muted else if (ab.balance >= 0) c.income else c.expense,
            )
        }
        val hideCd = stringResource(R.string.hide_account_cd, name)
        IconButton(onClick = onToggleHidden, modifier = Modifier.semantics { contentDescription = hideCd }) {
            Icon(
                if (ab.account.hidden) AppIcons.VisibilityOff else AppIcons.Visibility,
                null, tint = if (ab.account.hidden) MaterialTheme.colorScheme.primary else c.muted, modifier = Modifier.size(20.dp),
            )
        }
        val editCd = stringResource(R.string.edit_account_cd, name)
        IconButton(onClick = onEdit, modifier = Modifier.semantics { contentDescription = editCd }) {
            Icon(Icons.Outlined.Edit, null, tint = c.muted, modifier = Modifier.size(20.dp))
        }
        Box(
            handle.size(44.dp).semantics {
                contentDescription = dragCd
                customActions = buildList {
                    if (canMoveUp) add(CustomAccessibilityAction(moveUp) { onMove(-1); true })
                    if (canMoveDown) add(CustomAccessibilityAction(moveDown) { onMove(1); true })
                }
            },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Menu, null, tint = c.muted, modifier = Modifier.size(22.dp)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeletedAccountsScreen(
    vm: AppViewModel,
    s: AppSettings,
    deleted: List<Account>,
    txns: List<Txn>,
    onBack: () -> Unit,
    onDelete: (Account) -> Unit,
) {
    val balances = remember(deleted, txns) { vm.balances(deleted, txns) }
    val c = AppTheme.colors
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.deleted_accounts)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        if (deleted.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(stringResource(R.string.empty_deleted_accounts))
            }
        } else {
            LazyColumn(Modifier.padding(padding).fillMaxSize().testTag("deleted_accounts_list")) {
                items(balances, key = { it.account.id }) { ab ->
                    ListItem(
                        headlineContent = { Text(ab.account.name) },
                        supportingContent = { Text("\u200E" + Fmt.money(ab.balance, s, false), color = c.muted) },
                        trailingContent = {
                            Row {
                                TextButton(onClick = { vm.restoreAccount(ab.account) }) { Text(stringResource(R.string.restore_account)) }
                                TextButton(onClick = { onDelete(ab.account) }) { Text(stringResource(R.string.permanently_delete), color = MaterialTheme.colorScheme.error) }
                            }
                        },
                    )
                    HorizontalDivider(color = c.divider)
                }
            }
        }
    }
}

@Composable
private fun AccountFlowDialogs(
    dlg: AccDlg,
    s: AppSettings,
    others: (Long) -> List<Account>,
    countFor: (Long) -> Int,
    onChange: (AccDlg?) -> Unit,
    vm: AppViewModel,
) {
    when (dlg) {
        is AccDlg.Edit -> AccountDialog(
            dlg.account, s,
            onDismiss = { onChange(null) },
            onSave = { vm.saveAccount(it) },
            onDelete = { a -> onChange(AccDlg.DeleteOptions(a, countFor(a.id))) },
            onMerge = { a -> onChange(AccDlg.PickMerge(a)) },
            canDelete = others(dlg.account.id).isNotEmpty() || dlg.account.id == 0L,
            canMerge = others(dlg.account.id).isNotEmpty(),
        )
        is AccDlg.DeleteOptions -> AlertDialog(
            onDismissRequest = { onChange(null) },
            title = { Text(stringResource(R.string.delete_account_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.delete_account_options, dlg.account.name))
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.txn_count, dlg.count), style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.muted)
                }
            },
            confirmButton = {},
            dismissButton = {
                Column(horizontalAlignment = Alignment.End) {
                    val dests = others(dlg.account.id)
                    TextButton(
                        onClick = { if (dests.isEmpty()) return@TextButton else onChange(AccDlg.PickMove(dlg.account, dlg.count)) },
                        enabled = dests.isNotEmpty(),
                    ) { Text(if (dests.isEmpty()) stringResource(R.string.no_other_accounts) else stringResource(R.string.move_txns_to)) }
                    TextButton(onClick = { onChange(AccDlg.ConfirmSoft(dlg.account)) }) { Text(stringResource(R.string.move_to_deleted_bin)) }
                    TextButton(onClick = { onChange(AccDlg.ConfirmWipe(dlg.account)) }) {
                        Text(stringResource(R.string.delete_txns_too), color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { onChange(null) }) { Text(stringResource(R.string.cancel)) }
                }
            },
        )
        is AccDlg.PickMove -> AccountPickerDialog(
            title = stringResource(R.string.move_txns_to),
            accounts = others(dlg.account.id),
            onPick = { onChange(AccDlg.ConfirmMove(dlg.account, it, dlg.count)) },
            onDismiss = { onChange(AccDlg.DeleteOptions(dlg.account, dlg.count)) },
        )
        is AccDlg.ConfirmMove -> ConfirmDialog(
            stringResource(R.string.delete_account_title),
            stringResource(R.string.confirm_move_account, dlg.count, dlg.target.name, dlg.account.name),
            stringResource(R.string.delete),
            { vm.moveAccount(dlg.account, dlg.target.id); onChange(null) },
            { onChange(null) },
        )
        is AccDlg.ConfirmSoft -> ConfirmDialog(
            stringResource(R.string.delete_account_title),
            stringResource(R.string.confirm_soft_delete, dlg.account.name),
            stringResource(R.string.move_to_deleted_bin),
            { vm.softDeleteAccount(dlg.account); onChange(null) },
            { onChange(null) },
        )
        is AccDlg.ConfirmWipe -> ConfirmDialog(
            stringResource(R.string.delete_account_title),
            stringResource(R.string.confirm_wipe_account),
            stringResource(R.string.delete),
            { vm.wipeAccount(dlg.account); onChange(null) },
            { onChange(null) },
        )
        is AccDlg.PickMerge -> AccountPickerDialog(
            title = stringResource(R.string.merge_with),
            accounts = others(dlg.account.id),
            onPick = { onChange(AccDlg.ConfirmMerge(dlg.account, it)) },
            onDismiss = { onChange(AccDlg.Edit(dlg.account)) },
        )
        is AccDlg.ConfirmMerge -> ConfirmDialog(
            stringResource(R.string.merge_accounts),
            stringResource(R.string.confirm_merge, dlg.source.name, dlg.target.name),
            stringResource(R.string.merge_accounts),
            { vm.mergeAccounts(dlg.source, dlg.target); onChange(null) },
            { onChange(null) },
        )
    }
}

@Composable
private fun AccountPickerDialog(title: String, accounts: List<Account>, onPick: (Account) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                accounts.forEach { a ->
                    Text(
                        a.name,
                        modifier = Modifier.fillMaxWidth().clickable { onPick(a) }.padding(vertical = 12.dp).testTag("pick_account_${a.id}"),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccountDialog(
    a: Account,
    s: AppSettings,
    onDismiss: () -> Unit,
    onSave: (Account) -> Unit,
    onDelete: (Account) -> Unit,
    onMerge: (Account) -> Unit = {},
    canDelete: Boolean,
    canMerge: Boolean = false,
) {
    var name by remember { mutableStateOf(a.name) }
    var group by remember { mutableIntStateOf(a.groupType) }
    var balance by remember { mutableStateOf(if (a.initialBalance < 0) "-" + Money.toInput(-a.initialBalance, s.currencyInfo) else Money.toInput(a.initialBalance, s.currencyInfo)) }
    var neg by remember { mutableStateOf(a.initialBalance < 0) }
    var last4 by remember { mutableStateOf(a.cardLast4 ?: "") }
    var include by remember { mutableStateOf(a.includeInTotal) }
    var hidden by remember { mutableStateOf(a.hidden) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (a.id == 0L) R.string.add_account else R.string.edit_account)) },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.name)) }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AccountGroup.all.forEach { g ->
                        FilterChip(
                            selected = group == g,
                            onClick = { group = g; if (g == AccountGroup.LOAN) neg = true },
                            label = { Text(groupLabel(g)) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    balance, { balance = it; neg = it.trim().startsWith("-") }, label = { Text(stringResource(R.string.initial_balance)) }, singleLine = true,
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
                if (a.id != 0L) Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(hidden, { hidden = it }); Text(stringResource(R.string.hide_account))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) return@TextButton
                val v = Money.parse(balance.trim().removePrefix("-")) ?: 0L
                onSave(a.copy(name = name.trim(), groupType = group, initialBalance = if (neg) -v else v, cardLast4 = last4.ifBlank { null }, includeInTotal = include, hidden = hidden))
                onDismiss()
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            Row {
                if (a.id != 0L && canDelete) TextButton(onClick = { onDelete(a) }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
                if (a.id != 0L && canMerge) TextButton(onClick = { onMerge(a) }) { Text(stringResource(R.string.merge_with)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

@Stable
private class AccDragState {
    var key by mutableStateOf<String?>(null)
    var offset by mutableFloatStateOf(0f)
    var group: Int? = null
    var order by mutableStateOf<List<Long>?>(null)
    var busy = false
    var start: List<Long> = emptyList()
    var pendingGroup: Int? = null
    var pending by mutableStateOf<List<Long>?>(null)

    fun resolve(g: Int, db: List<Long>): List<Long> {
        if (key != null && group == g) order?.let { return it }
        val p = pending
        if (p != null && pendingGroup == g) {
            if (p == db || p.toSet() != db.toSet()) pending = null else return p
        }
        return db
    }
    fun reset() { key = null; offset = 0f; order = null; group = null; start = emptyList() }
    fun hold(g: Int, o: List<Long>) { pendingGroup = g; pending = o }
    fun finish(commit: (List<Long>) -> Unit) {
        val o = order
        if (o != null && o != start) { hold(group ?: return, o); commit(o) }
        reset()
    }
}

@Composable
private fun Modifier.accountReorderHandle(
    drag: AccDragState,
    key: String,
    group: Int,
    siblings: List<Long>,
    listState: LazyListState,
    onCommit: (List<Long>) -> Unit,
): Modifier {
    val currentSiblings by rememberUpdatedState(siblings)
    val currentCommit by rememberUpdatedState(onCommit)
    val scope = rememberCoroutineScope()
    return pointerInput(key) {
        detectDragGestures(
            onDragStart = {
                drag.group = group
                drag.start = currentSiblings
                drag.order = currentSiblings
                drag.offset = 0f
                drag.key = key
            },
            onDrag = { change, amount ->
                change.consume()
                drag.offset += amount.y
                val info = listState.layoutInfo
                val me = info.visibleItemsInfo.firstOrNull { it.key == key } ?: return@detectDragGestures
                val order = drag.order ?: return@detectDragGestures
                val myId = order.firstOrNull { "a$it" == key } ?: return@detectDragGestures
                val center = me.offset + drag.offset + me.size / 2f
                if (drag.busy) return@detectDragGestures
                val target = info.visibleItemsInfo.firstOrNull { item ->
                    item.key != key && center >= item.offset && center <= item.offset + item.size &&
                        order.any { "a$it" == item.key }
                }
                if (target != null) {
                    val from = order.indexOf(myId)
                    val to = order.indexOfFirst { "a$it" == target.key }
                    val newOrder = order.moved(from, to)
                    val newOffset = if (to > from) me.offset + target.size else target.offset
                    val first = listState.firstVisibleItemIndex
                    val anchor = when (first) {
                        target.index -> me.index
                        me.index -> target.index
                        else -> null
                    }
                    if (anchor != null) {
                        drag.busy = true
                        scope.launch {
                            listState.scrollToItem(anchor, listState.firstVisibleItemScrollOffset)
                            drag.order = newOrder
                            drag.offset -= (newOffset - me.offset)
                            drag.busy = false
                        }
                    } else {
                        drag.order = newOrder
                        drag.offset -= (newOffset - me.offset)
                    }
                }
                val top = me.offset + drag.offset
                val bottom = top + me.size
                val edge = me.size / 2f
                val scroll = when {
                    amount.y > 0 && bottom > info.viewportEndOffset - edge -> (bottom - (info.viewportEndOffset - edge)).coerceAtMost(40f)
                    amount.y < 0 && top < info.viewportStartOffset + edge -> (top - (info.viewportStartOffset + edge)).coerceAtLeast(-40f)
                    else -> 0f
                }
                if (scroll != 0f) {
                    val consumed = listState.dispatchRawDelta(scroll)
                    drag.offset += consumed
                }
            },
            onDragEnd = { drag.finish(currentCommit) },
            onDragCancel = { drag.finish(currentCommit) },
        )
    }
}
