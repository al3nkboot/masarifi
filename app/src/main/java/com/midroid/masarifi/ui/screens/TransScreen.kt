package com.midroid.masarifi.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.midroid.masarifi.R
import com.midroid.masarifi.data.Account
import com.midroid.masarifi.data.AppSettings
import com.midroid.masarifi.data.Category
import com.midroid.masarifi.data.Txn
import com.midroid.masarifi.data.TxnType
import com.midroid.masarifi.ui.AppViewModel
import com.midroid.masarifi.ui.Fmt
import com.midroid.masarifi.ui.Route
import com.midroid.masarifi.ui.components.EmptyState
import com.midroid.masarifi.ui.components.MonthSwitcher
import com.midroid.masarifi.ui.components.ProgressBar
import com.midroid.masarifi.ui.components.SummaryRow
import com.midroid.masarifi.ui.components.TxnRow
import com.midroid.masarifi.ui.components.TxnSelection
import com.midroid.masarifi.ui.components.SelectionTopBar
import com.midroid.masarifi.ui.components.SelectionEffects
import com.midroid.masarifi.ui.components.rememberTxnSelection
import com.midroid.masarifi.ui.components.click
import com.midroid.masarifi.ui.theme.AppTheme
import com.midroid.masarifi.ui.theme.Brand
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** Helper that resolves labels for a transaction. */
class Labels(val categories: Map<Long, Category>, val accounts: Map<Long, Account>, val s: AppSettings, private val transferLabel: String, private val noCategory: String) {
    fun category(t: Txn): String = when {
        t.type == TxnType.TRANSFER -> transferLabel
        t.categoryId == null -> noCategory
        else -> categories[t.categoryId]?.let { it.emoji + " " + it.name(s.arabic) } ?: noCategory
    }
    fun account(t: Txn): String {
        val from = accounts[t.accountId]?.name ?: "?"
        return if (t.type == TxnType.TRANSFER) "$from → ${accounts[t.toAccountId]?.name ?: "?"}" else from
    }
}

@Composable
fun rememberLabels(vm: AppViewModel): Labels {
    val cats by vm.categories.collectAsStateWithLifecycle()
    val accs by vm.accounts.collectAsStateWithLifecycle()
    val s by vm.settings.collectAsStateWithLifecycle()
    val tl = stringResource(R.string.transfer)
    val nc = stringResource(R.string.no_category)
    return remember(cats, accs, s, tl) { Labels(cats.associateBy { it.id }, accs.associateBy { it.id }, s, tl, nc) }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun TransScreen(vm: AppViewModel, navigate: (Route) -> Unit, modifier: Modifier) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val all by vm.txns.collectAsStateWithLifecycle()
    val ym by vm.month.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val labels = rememberLabels(vm)

    val isYearTab = tab == 2
    val range = Fmt.monthRange(ym)
    val monthTxns = remember(all, ym) { all.filter { it.dateTime in range } }
    val yearTxns = remember(all, ym.year) {
        val r = Fmt.startOf(LocalDate.of(ym.year, 1, 1)) until Fmt.startOf(LocalDate.of(ym.year + 1, 1, 1))
        all.filter { it.dateTime in r }
    }
    val shown = if (isYearTab) yearTxns else monthTxns
    val sel = rememberTxnSelection()
    val dupGroups by vm.dupGroups.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(all) { sel.retain(all.mapTo(HashSet()) { it.id }) }
    androidx.compose.runtime.LaunchedEffect(tab, ym) { sel.clear() }
    SelectionEffects(sel, vm)
    androidx.compose.runtime.LaunchedEffect(sel.active) { vm.selectionActive = sel.active }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { vm.selectionActive = false } }

    Column(modifier.fillMaxSize()) {
        // Top bar (selection mode replaces it)
        if (sel.active) {
            SelectionTopBar(sel, visibleIds = {
                when (tab) {
                    0 -> monthTxns.map { it.id }
                    1 -> vm.selectedDay?.let { d -> monthTxns.filter { Fmt.toDate(it.dateTime) == d }.map { it.id } }.orEmpty()
                    else -> emptyList()
                }
            })
        } else Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.tab_trans), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 12.dp))
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { navigate(Route.Search) }) { Icon(Icons.Filled.Search, stringResource(R.string.search)) }
        }
        if (dupGroups.isNotEmpty() && !sel.active) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)
                    .background(Brand.Orange.copy(alpha = 0.18f), RoundedCornerShape(8.dp))
                    .clickable { navigate(Route.Duplicates) }.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(com.midroid.masarifi.ui.components.AppIcons.Copy, null, tint = Brand.Orange, modifier = Modifier.padding(end = 8.dp))
                Text(stringResource(R.string.dup_banner, dupGroups.size.toString()), fontSize = 13.sp, modifier = Modifier.weight(1f))
            }
        }
        MonthSwitcher(
            label = if (isYearTab) ym.year.toString() else Fmt.monthTitle(ym, s.arabic),
            onPrev = { vm.month.value = if (isYearTab) ym.minusYears(1) else ym.minusMonths(1) },
            onNext = { vm.month.value = if (isYearTab) ym.plusYears(1) else ym.plusMonths(1) },
            onLabelClick = { vm.month.value = YearMonth.now() },
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        val tabs = listOf(R.string.daily, R.string.calendar, R.string.monthly, R.string.total, R.string.note)
        SecondaryTabRow(
            selectedTabIndex = tab,
            containerColor = Color.Transparent,
            indicator = {
                TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(tab), color = MaterialTheme.colorScheme.primary)
            },
        ) {
            tabs.forEachIndexed { i, res ->
                Tab(
                    selected = tab == i, onClick = { tab = i },
                    text = { Text(stringResource(res), fontSize = 13.sp, maxLines = 1) },
                    selectedContentColor = MaterialTheme.colorScheme.onBackground,
                    unselectedContentColor = AppTheme.colors.muted,
                )
            }
        }
        val income = shown.filter { it.type == TxnType.INCOME }.sumOf { it.amount }
        val expense = shown.filter { it.type == TxnType.EXPENSE }.sumOf { it.amount }
        SummaryRow(income, expense, s)

        when (tab) {
            0 -> DailyList(monthTxns, labels, s, navigate, sel)
            1 -> CalendarView(vm, ym, monthTxns, labels, s, navigate, sel)
            2 -> MonthlyList(yearTxns, ym.year, s) { m -> vm.month.value = m; tab = 0 }
            3 -> TotalView(vm, ym, all, monthTxns, s)
            else -> NoteView(monthTxns, labels, s, navigate, ym)
        }
    }
}

@Composable
fun DailyList(txns: List<Txn>, labels: Labels, s: AppSettings, navigate: (Route) -> Unit, sel: TxnSelection? = null) {
    if (txns.isEmpty()) { EmptyState(stringResource(R.string.empty_month)); return }
    val groups = remember(txns) { txns.groupBy { Fmt.toDate(it.dateTime) }.toSortedMap(compareByDescending { it }) }
    LazyColumn(Modifier.fillMaxSize()) {
        groups.forEach { (date, list) ->
            item(key = "h$date") {
                DayHeader(date, list, s, onClick = if (sel?.active == true) { { sel.toggleAll(list.map { it.id }) } } else null)
            }
            items(list, key = { it.id }) { t -> SelectableTxnRow(t, labels, s, navigate, sel) }
            item(key = "d$date") { Spacer(Modifier.height(8.dp)) }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

/** Transaction row wired to multi-select: long-press starts selection, tap toggles while selecting. */
@Composable
fun SelectableTxnRow(t: Txn, labels: Labels, s: AppSettings, navigate: (Route) -> Unit, sel: TxnSelection?, showDate: Boolean = false) {
    TxnRow(
        t, labels.category(t), labels.account(t), s,
        onClick = { if (sel != null) sel.click(t.id) { navigate(Route.Edit(txnId = t.id)) } else navigate(Route.Edit(txnId = t.id)) },
        showDate = showDate,
        selectionMode = sel?.active == true, selected = sel?.isSelected(t.id) == true,
        onLongClick = sel?.let { { it.start(t.id) } },
    )
}

@Composable
fun DayHeader(date: LocalDate, list: List<Txn>, s: AppSettings, onClick: (() -> Unit)? = null) {
    val c = AppTheme.colors
    val inc = list.filter { it.type == TxnType.INCOME }.sumOf { it.amount }
    val exp = list.filter { it.type == TxnType.EXPENSE }.sumOf { it.amount }
    val chipColor = when (date.dayOfWeek) {
        DayOfWeek.FRIDAY -> Brand.Coral
        DayOfWeek.SATURDAY -> Brand.Blue
        else -> c.chip
    }
    Column(Modifier.background(MaterialTheme.colorScheme.surfaceContainer).let { if (onClick != null) it.clickable(onClick = onClick) else it }) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(date.dayOfMonth.toString(), fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.background(chipColor, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                Text(Fmt.weekdayShort(date.dayOfWeek, s.arabic), fontSize = 11.sp, color = Color.White)
            }
            Spacer(Modifier.width(8.dp))
            Text("%02d.%d".format(java.util.Locale.US, date.monthValue, date.year), fontSize = 12.sp, color = c.muted)
            Spacer(Modifier.weight(1f))
            if (inc > 0) Text(Fmt.money(inc, s), color = c.income, fontSize = 13.sp)
            Spacer(Modifier.width(12.dp))
            if (exp > 0) Text(Fmt.money(exp, s), color = c.expense, fontSize = 13.sp)
        }
        HorizontalDivider(color = c.divider)
    }
}

@Composable
fun CalendarView(vm: AppViewModel, ym: YearMonth, txns: List<Txn>, labels: Labels, s: AppSettings, navigate: (Route) -> Unit, selection: TxnSelection? = null) {
    val c = AppTheme.colors
    var selected by remember(ym) { mutableStateOf(vm.selectedDay?.takeIf { YearMonth.from(it) == ym }) }
    val byDay = remember(txns) { txns.groupBy { Fmt.toDate(it.dateTime) } }
    val firstDow = if (s.weekStartsSaturday) DayOfWeek.SATURDAY else DayOfWeek.SUNDAY
    val days = (0 until 7).map { firstDow.plus(it.toLong()) }
    val first = ym.atDay(1)
    val offset = ((first.dayOfWeek.value - firstDow.value) + 7) % 7
    val cells = offset + ym.lengthOfMonth()
    val rows = (cells + 6) / 7
    val today = LocalDate.now()

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(Modifier.fillMaxWidth()) {
                days.forEach { d ->
                    Text(
                        Fmt.weekdayNarrow(d, s.arabic), Modifier.weight(1f).padding(vertical = 4.dp), textAlign = TextAlign.Center, fontSize = 11.sp,
                        color = when (d) { DayOfWeek.FRIDAY -> Brand.Coral; DayOfWeek.SATURDAY -> Brand.Blue; else -> c.muted },
                    )
                }
            }
        }
        items(rows) { r ->
            Row(Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val idx = r * 7 + col - offset + 1
                    val date = if (idx in 1..ym.lengthOfMonth()) ym.atDay(idx) else null
                    val list = date?.let { byDay[it] }.orEmpty()
                    val inc = list.filter { it.type == TxnType.INCOME }.sumOf { it.amount }
                    val exp = list.filter { it.type == TxnType.EXPENSE }.sumOf { it.amount }
                    Column(
                        Modifier.weight(1f).aspectRatio(0.8f)
                            .border(0.5.dp, c.divider)
                            .background(if (date != null && date == selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent)
                            .clickable(enabled = date != null) { selected = date; vm.selectedDay = date }
                            .padding(3.dp),
                    ) {
                        if (date != null) {
                            Text(
                                idx.toString(), fontSize = 12.sp,
                                fontWeight = if (date == today) FontWeight.Bold else FontWeight.Normal,
                                color = if (date == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                            )
                            Spacer(Modifier.weight(1f))
                            if (inc > 0) Text(Fmt.money(inc, s, false), color = c.income, fontSize = 9.sp, maxLines = 1)
                            if (exp > 0) Text(Fmt.money(exp, s, false), color = c.expense, fontSize = 9.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
        val sel = selected
        if (sel != null) {
            val list = byDay[sel].orEmpty().sortedByDescending { it.dateTime }
            item { DayHeader(sel, list, s, onClick = if (selection?.active == true) { { selection.toggleAll(list.map { it.id }) } } else null) }
            if (list.isEmpty()) item { EmptyState(stringResource(R.string.no_transactions_day)) }
            items(list, key = { it.id }) { t -> SelectableTxnRow(t, labels, s, navigate, selection) }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
fun MonthlyList(yearTxns: List<Txn>, year: Int, s: AppSettings, onOpen: (YearMonth) -> Unit) {
    val c = AppTheme.colors
    val byMonth = remember(yearTxns) { yearTxns.groupBy { YearMonth.from(Fmt.toDate(it.dateTime)) } }
    val months = (12 downTo 1).map { YearMonth.of(year, it) }.filter { it <= YearMonth.now() || byMonth.containsKey(it) }
    LazyColumn(Modifier.fillMaxSize()) {
        items(months) { m ->
            val list = byMonth[m].orEmpty()
            val inc = list.filter { it.type == TxnType.INCOME }.sumOf { it.amount }
            val exp = list.filter { it.type == TxnType.EXPENSE }.sumOf { it.amount }
            Row(
                Modifier.fillMaxWidth().clickable { onOpen(m) }.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(Fmt.monthShort(m, s.arabic), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Text("1.${m.monthValue} ~ ${m.lengthOfMonth()}.${m.monthValue}", fontSize = 12.sp, color = c.muted)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(Fmt.money(inc, s), color = c.income, fontSize = 13.sp)
                    Text(Fmt.money(exp, s), color = c.expense, fontSize = 13.sp)
                    Text(Fmt.money(inc - exp, s), fontSize = 12.sp, color = c.muted)
                }
            }
            HorizontalDivider(color = c.divider)
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
fun TotalView(vm: AppViewModel, ym: YearMonth, all: List<Txn>, monthTxns: List<Txn>, s: AppSettings) {
    val c = AppTheme.colors
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val budgets by vm.budgets.collectAsStateWithLifecycle()
    val prevRange = Fmt.monthRange(ym.minusMonths(1))
    val prevExp = all.filter { it.dateTime in prevRange && it.type == TxnType.EXPENSE }.sumOf { it.amount }
    val exp = monthTxns.filter { it.type == TxnType.EXPENSE }.sumOf { it.amount }
    val inc = monthTxns.filter { it.type == TxnType.INCOME }.sumOf { it.amount }
    val balances = vm.balances(accounts, all)
    val overall = budgets.firstOrNull { it.categoryId == 0L }?.amount ?: 0L

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        item {
            InfoCard(stringResource(R.string.budget)) {
                if (overall > 0) {
                    Row { Text(stringResource(R.string.overall_budget), Modifier.weight(1f)); Text("${Fmt.money(exp, s)} / ${Fmt.money(overall, s)}", fontSize = 13.sp) }
                    Spacer(Modifier.height(6.dp))
                    ProgressBar(exp.toDouble() / overall)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.remaining_x, Fmt.money(overall - exp, s)), fontSize = 12.sp, color = c.muted)
                } else Text(stringResource(R.string.no_budget_hint), color = c.muted, fontSize = 13.sp)
            }
        }
        item {
            InfoCard(stringResource(R.string.accounts_summary)) {
                val total = balances.filter { it.account.includeInTotal }.sumOf { it.balance }
                Row { Text(stringResource(R.string.total_balance), Modifier.weight(1f)); Text(Fmt.money(total, s), fontWeight = FontWeight.SemiBold) }
                Spacer(Modifier.height(4.dp))
                Row { Text(stringResource(R.string.net_this_month), Modifier.weight(1f), color = c.muted); Text(Fmt.money(inc - exp, s), color = if (inc - exp >= 0) c.income else c.expense) }
            }
        }
        item {
            InfoCard(stringResource(R.string.compare_last_month)) {
                Row { Text(Fmt.monthTitle(ym.minusMonths(1), s.arabic), Modifier.weight(1f), color = c.muted); Text(Fmt.money(prevExp, s)) }
                Row { Text(Fmt.monthTitle(ym, s.arabic), Modifier.weight(1f)); Text(Fmt.money(exp, s), color = c.expense) }
                if (prevExp > 0) {
                    val p = ((exp - prevExp) * 100.0 / prevExp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (p >= 0) stringResource(R.string.more_than_last, Fmt.percent(p)) else stringResource(R.string.less_than_last, Fmt.percent(-p)),
                        color = if (p > 0) c.expense else c.income, fontSize = 13.sp,
                    )
                }
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
fun InfoCard(title: String, content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = AppTheme.colors.card),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
fun NoteView(txns: List<Txn>, labels: Labels, s: AppSettings, navigate: (Route) -> Unit, ym: YearMonth) {
    val c = AppTheme.colors
    var expenseMode by rememberSaveable { mutableStateOf(true) }
    val type = if (expenseMode) TxnType.EXPENSE else TxnType.INCOME
    val emptyNote = stringResource(R.string.no_note)
    val groups = remember(txns, type) {
        txns.filter { it.type == type }.groupBy { it.note.trim() }.map { (k, v) -> Triple(k, v.size, v.sumOf { it.amount }) }.sortedByDescending { it.third }
    }
    Column {
        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.Center) {
            TypeToggle(stringResource(R.string.expense), expenseMode, c.expense) { expenseMode = true }
            Spacer(Modifier.width(8.dp))
            TypeToggle(stringResource(R.string.income), !expenseMode, c.income) { expenseMode = false }
        }
        if (groups.isEmpty()) EmptyState(stringResource(R.string.empty_month))
        LazyColumn(Modifier.fillMaxSize()) {
            items(groups) { (note, count, sum) ->
                Row(
                    Modifier.fillMaxWidth().clickable {
                        val r = Fmt.monthRange(ym)
                        navigate(Route.TxnList(title = note.ifBlank { emptyNote }, from = r.first, to = r.last + 1, note = note, type = type))
                    }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(note.ifBlank { emptyNote }, Modifier.weight(1f), maxLines = 1)
                    Text("$count", color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 12.dp))
                    Text(Fmt.money(sum, s), color = if (expenseMode) c.expense else c.income)
                }
                HorizontalDivider(color = c.divider)
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
fun TypeToggle(text: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .border(1.dp, if (selected) color else AppTheme.colors.divider, RoundedCornerShape(6.dp))
            .background(if (selected) color.copy(alpha = 0.12f) else Color.Transparent, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (selected) color else AppTheme.colors.muted, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}
