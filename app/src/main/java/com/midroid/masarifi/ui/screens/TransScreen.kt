package com.midroid.masarifi.ui.screens

import com.midroid.masarifi.ui.icon

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.testTag
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
import com.midroid.masarifi.data.CarryOver
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
import com.midroid.masarifi.ui.theme.Spacing
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** Helper that resolves labels for a transaction. */
class Labels(val categories: Map<Long, Category>, val accounts: Map<Long, Account>, val s: AppSettings, private val transferLabel: String, private val noCategory: String) {
    fun category(t: Txn): String = when {
        t.type == TxnType.TRANSFER -> transferLabel
        t.categoryId == null -> noCategory
        else -> categories[t.categoryId]?.let { it.icon + " " + it.name(s.arabic) } ?: noCategory
    }
    fun account(t: Txn): String {
        val from = accounts[t.accountId]?.name ?: "?"
        return if (t.type == TxnType.TRANSFER) "$from → ${accounts[t.toAccountId]?.name ?: "?"}" else from
    }
}

@Composable
fun rememberLabels(vm: AppViewModel): Labels {
    val cats by vm.categories.collectAsStateWithLifecycle()
    val accs by vm.allAccounts.collectAsStateWithLifecycle()
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
    var typeFilter by rememberSaveable { mutableStateOf<Int?>(null) }
    val labels = rememberLabels(vm)

    val isYearTab = tab == 2
    val range = Fmt.monthRange(ym)
    val monthTxns = remember(all, ym) { all.filter { it.dateTime in range } }
    val monthShown = remember(monthTxns, typeFilter) {
        if (typeFilter == null) monthTxns else monthTxns.filter { it.type == typeFilter }
    }
    val yearTxns = remember(all, ym.year) {
        val r = Fmt.startOf(LocalDate.of(ym.year, 1, 1)) until Fmt.startOf(LocalDate.of(ym.year + 1, 1, 1))
        all.filter { it.dateTime in r }
    }
    val shown = if (isYearTab) yearTxns else monthTxns
    val sel = rememberTxnSelection()
    val dupGroups by vm.dupGroups.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val goals by vm.goals.collectAsStateWithLifecycle()
    val contributions by vm.goalContributions.collectAsStateWithLifecycle()
    val netWorth = remember(accounts, all) {
        com.midroid.masarifi.data.NetWorthCalc.compute(
            vm.balances(accounts, all).map { it.account to it.balance },
        )
    }
    val topGoal = remember(goals, contributions) {
        com.midroid.masarifi.data.GoalLogic.topGoal(goals, contributions)
    }
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
                    0 -> monthShown.map { it.id }
                    1 -> vm.selectedDay?.let { d -> monthShown.filter { Fmt.toDate(it.dateTime) == d }.map { it.id } }.orEmpty()
                    else -> emptyList()
                }
            })
        } else Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.xs, vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.tab_trans), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = Spacing.md))
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { navigate(Route.Search) }) { Icon(Icons.Filled.Search, stringResource(R.string.search)) }
        }
        if (dupGroups.isNotEmpty() && !sel.active) {
            com.midroid.masarifi.ui.components.InfoStrip(
                text = stringResource(R.string.dup_banner, dupGroups.size.toString()), icon = com.midroid.masarifi.ui.components.AppIcons.Copy,
                tint = AppTheme.colors.warning, container = AppTheme.colors.warning.copy(alpha = 0.14f),
                onClick = { navigate(Route.Duplicates) },
            )
        }
        if (!sel.active) {
            HomeOverview(
                netWorth = netWorth,
                topGoal = topGoal,
                saved = topGoal?.let {
                    com.midroid.masarifi.data.GoalLogic.saved(it.id, contributions)
                } ?: 0L,
                s = s,
                onGoals = { navigate(Route.Goals) },
            )
        }
        MonthSwitcher(
            label = if (isYearTab) ym.year.toString() else Fmt.monthTitle(ym, s.arabic),
            onPrev = { vm.month.value = if (isYearTab) ym.minusYears(1) else ym.minusMonths(1) },
            onNext = { vm.month.value = if (isYearTab) ym.plusYears(1) else ym.plusMonths(1) },
            onLabelClick = { vm.month.value = YearMonth.now() },
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        val tabs = listOf(R.string.daily, R.string.tab_calendar_short, R.string.monthly, R.string.tab_total_short, R.string.tab_note_short)
        androidx.compose.material3.PrimaryTabRow(
            selectedTabIndex = tab,
            containerColor = Color.Transparent,
            divider = { HorizontalDivider(color = AppTheme.colors.divider) },
        ) {
            tabs.forEachIndexed { i, res ->
                Tab(
                    selected = tab == i, onClick = { tab = i },
                    text = { Text(stringResource(res), style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Clip) },
                    selectedContentColor = MaterialTheme.colorScheme.primary,
                    unselectedContentColor = AppTheme.colors.muted,
                )
            }
        }
        val period = if (isYearTab) {
            val (inc, exp) = CarryOver.incomeExpense(yearTxns)
            CarryOver.PeriodTotals(inc, exp, CarryOver.carryIn(YearMonth.of(ym.year, 1), all, s.carryOver))
        } else CarryOver.totals(ym, all, s.carryOver)
        SummaryRow(period.displayIncome, period.displayExpense, s)
        if (tab == 0 || tab == 1) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.md, vertical = Spacing.xs)
                    .testTag("type_filter_chips"),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                val filters = listOf<Pair<Int, Int?>>(
                    R.string.filter_all to null,
                    R.string.expense to TxnType.EXPENSE,
                    R.string.income to TxnType.INCOME,
                    R.string.transfer to TxnType.TRANSFER,
                )
                filters.forEach { (labelRes, value) ->
                    FilterChip(
                        selected = typeFilter == value,
                        onClick = { typeFilter = value },
                        label = { Text(stringResource(labelRes)) },
                    )
                }
            }
        }

        when (tab) {
            0 -> DailyList(monthShown, labels, s, navigate, sel, carry = period.carry, month = ym)
            1 -> CalendarView(vm, ym, monthShown, labels, s, navigate, sel, carry = period.carry)
            2 -> MonthlyList(yearTxns, all, ym.year, s, s.carryOver) { m -> vm.month.value = m; tab = 0 }
            3 -> TotalView(vm, ym, all, monthTxns, s, carry = period.carry)
            else -> NoteView(monthShown, labels, s, navigate, ym)
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun DailyList(txns: List<Txn>, labels: Labels, s: AppSettings, navigate: (Route) -> Unit, sel: TxnSelection? = null, carry: Long = 0, month: YearMonth = YearMonth.now()) {
    if (txns.isEmpty() && carry == 0L) { EmptyState(stringResource(R.string.empty_month)); return }
    val groups = remember(txns) { txns.groupBy { Fmt.toDate(it.dateTime) }.toSortedMap(compareByDescending { it }) }
    val first = month.atDay(1)
    LazyColumn(Modifier.fillMaxSize(), state = com.midroid.masarifi.ui.components.rememberFabAwareListState(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = Spacing.fabClearance)) {
        val dates = (groups.keys + if (carry != 0L) setOf(first) else emptySet()).sortedDescending()
        dates.forEach { date ->
            val list = groups[date].orEmpty()
            stickyHeader(key = "h$date") {
                DayHeader(date, list, s, onClick = if (sel?.active == true) { { sel.toggleAll(list.map { it.id }) } } else null, carry = if (date == first) carry else 0L)
            }
            if (date == first && carry != 0L) item(key = "carry") { CarryOverRow(carry, s) }
            items(list, key = { it.id }) { t -> SelectableTxnRow(t, labels, s, navigate, sel, showCurrency = false) }
        }
    }
}

/** Transaction row wired to multi-select: long-press starts selection, tap toggles while selecting. */
@Composable
fun SelectableTxnRow(t: Txn, labels: Labels, s: AppSettings, navigate: (Route) -> Unit, sel: TxnSelection?, showDate: Boolean = false, showCurrency: Boolean = true, runningBalance: Long? = null) {
    TxnRow(
        t, labels.category(t), labels.account(t), s,
        onClick = { if (sel != null) sel.click(t.id) { navigate(Route.Edit(txnId = t.id)) } else navigate(Route.Edit(txnId = t.id)) },
        showDate = showDate,
        selectionMode = sel?.active == true, selected = sel?.isSelected(t.id) == true,
        onLongClick = sel?.let { { it.start(t.id) } },
        showCurrency = showCurrency,
        runningBalance = runningBalance,
    )
}

@Composable
fun DayHeader(date: LocalDate, list: List<Txn>, s: AppSettings, onClick: (() -> Unit)? = null, carry: Long = 0L) {
    val c = AppTheme.colors
    val inc = list.filter { it.type == TxnType.INCOME }.sumOf { it.amount } + maxOf(carry, 0L)
    val exp = list.filter { it.type == TxnType.EXPENSE }.sumOf { it.amount } + maxOf(-carry, 0L)
    val net = inc - exp
    val today = LocalDate.now()
    val title = when (date) {
        today -> stringResource(R.string.today_label)
        today.minusDays(1) -> stringResource(R.string.yesterday_label)
        else -> "${Fmt.weekdayShort(date.dayOfWeek, s.arabic)} ${date.dayOfMonth}"
    }
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.md, bottom = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = c.muted, modifier = Modifier.weight(1f))
        Text(
            "\u200E" + (if (net > 0) "+" else "") + Fmt.money(net, s, false), style = com.midroid.masarifi.ui.theme.Amount,
            fontSize = 13.sp, color = if (net >= 0) c.income else c.muted,
        )
    }
}

@Composable
fun CalendarView(vm: AppViewModel, ym: YearMonth, txns: List<Txn>, labels: Labels, s: AppSettings, navigate: (Route) -> Unit, selection: TxnSelection? = null, carry: Long = 0L) {
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

    LazyColumn(Modifier.fillMaxSize(), state = com.midroid.masarifi.ui.components.rememberFabAwareListState()) {
        item {
            Row(Modifier.fillMaxWidth()) {
                days.forEach { d ->
                    Text(
                        Fmt.weekdayNarrow(d, s.arabic), Modifier.weight(1f).padding(vertical = Spacing.xs), textAlign = TextAlign.Center, fontSize = 11.sp,
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
                    val dayCarry = if (date != null && date.dayOfMonth == 1) carry else 0L
                    val inc = list.filter { it.type == TxnType.INCOME }.sumOf { it.amount } + maxOf(dayCarry, 0L)
                    val exp = list.filter { it.type == TxnType.EXPENSE }.sumOf { it.amount } + maxOf(-dayCarry, 0L)
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
                            if (inc > 0) Text(Fmt.money(inc, s, false), color = c.income, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                            if (exp > 0) Text(Fmt.money(exp, s, false), color = c.expense, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                    }
                }
            }
        }
        val sel = selected
        if (sel != null) {
            val list = byDay[sel].orEmpty().sortedByDescending { it.dateTime }
            val dayCarry = if (sel.dayOfMonth == 1) carry else 0L
            item { DayHeader(sel, list, s, onClick = if (selection?.active == true) { { selection.toggleAll(list.map { it.id }) } } else null, carry = dayCarry) }
            if (dayCarry != 0L) item { CarryOverRow(dayCarry, s) }
            if (list.isEmpty() && dayCarry == 0L) item { EmptyState(stringResource(R.string.no_transactions_day)) }
            items(list, key = { it.id }) { t -> SelectableTxnRow(t, labels, s, navigate, selection) }
        }
        item { Spacer(Modifier.height(Spacing.fabClearance)) }
    }
}

@Composable
fun MonthlyList(yearTxns: List<Txn>, all: List<Txn>, year: Int, s: AppSettings, carryEnabled: Boolean, onOpen: (YearMonth) -> Unit) {
    val c = AppTheme.colors
    val byMonth = remember(yearTxns) { yearTxns.groupBy { YearMonth.from(Fmt.toDate(it.dateTime)) } }
    val months = (12 downTo 1).map { YearMonth.of(year, it) }.filter { it <= YearMonth.now() || byMonth.containsKey(it) }
    LazyColumn(Modifier.fillMaxSize()) {
        items(months) { m ->
            val tot = CarryOver.totals(m, all, carryEnabled)
            val inc = tot.displayIncome
            val exp = tot.displayExpense
            Row(
                Modifier.fillMaxWidth().clickable { onOpen(m) }.padding(horizontal = Spacing.lg, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(Fmt.monthShort(m, s.arabic), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                    Text("1.${m.monthValue} ~ ${m.lengthOfMonth()}.${m.monthValue}", style = MaterialTheme.typography.bodySmall, color = c.muted)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(Fmt.money(inc, s), color = c.income, style = MaterialTheme.typography.bodyMedium)
                    Text(Fmt.money(exp, s), color = c.expense, style = MaterialTheme.typography.bodyMedium)
                    Text(Fmt.money(inc - exp, s), style = MaterialTheme.typography.bodySmall, color = c.muted)
                }
            }
            HorizontalDivider(color = c.divider)
        }
        item { Spacer(Modifier.height(Spacing.fabClearance)) }
    }
}

@Composable
fun TotalView(vm: AppViewModel, ym: YearMonth, all: List<Txn>, monthTxns: List<Txn>, s: AppSettings, carry: Long = 0L) {
    val c = AppTheme.colors
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val budgets by vm.budgets.collectAsStateWithLifecycle()
    val prevRange = Fmt.monthRange(ym.minusMonths(1))
    val prevExp = all.filter { it.dateTime in prevRange && it.type == TxnType.EXPENSE }.sumOf { it.amount }
    val exp = monthTxns.filter { it.type == TxnType.EXPENSE }.sumOf { it.amount } + maxOf(-carry, 0L)
    val inc = monthTxns.filter { it.type == TxnType.INCOME }.sumOf { it.amount } + maxOf(carry, 0L)
    val balances = vm.balances(accounts, all)
    val overall = budgets.firstOrNull { it.categoryId == 0L }?.amount ?: 0L

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = Spacing.md)) {
        item {
            InfoCard(stringResource(R.string.budget)) {
                if (overall > 0) {
                    Row { Text(stringResource(R.string.overall_budget), Modifier.weight(1f)); Text("${Fmt.money(exp, s)} / ${Fmt.money(overall, s)}", style = MaterialTheme.typography.bodyMedium) }
                    Spacer(Modifier.height(6.dp))
                    ProgressBar(exp.toDouble() / overall)
                    Spacer(Modifier.height(Spacing.xs))
                    Text(stringResource(R.string.remaining_x, Fmt.money(overall - exp, s)), style = MaterialTheme.typography.bodySmall, color = c.muted)
                } else Text(stringResource(R.string.no_budget_hint), color = c.muted, style = MaterialTheme.typography.bodyMedium)
            }
        }
        item {
            InfoCard(stringResource(R.string.accounts_summary)) {
                val total = balances.filter { it.account.includeInTotal }.sumOf { it.balance }
                Row { Text(stringResource(R.string.total_balance), Modifier.weight(1f)); Text(Fmt.money(total, s), fontWeight = FontWeight.SemiBold) }
                Spacer(Modifier.height(Spacing.xs))
                Row { Text(stringResource(R.string.net_this_month), Modifier.weight(1f), color = c.muted); Text(Fmt.money(inc - exp, s), color = if (inc - exp >= 0) c.income else c.expense) }
            }
        }
        item {
            InfoCard(stringResource(R.string.compare_last_month)) {
                Row { Text(Fmt.monthTitle(ym.minusMonths(1), s.arabic), Modifier.weight(1f), color = c.muted); Text(Fmt.money(prevExp, s)) }
                Row { Text(Fmt.monthTitle(ym, s.arabic), Modifier.weight(1f)); Text(Fmt.money(exp, s), color = c.expense) }
                if (prevExp > 0) {
                    val p = ((exp - prevExp) * 100.0 / prevExp)
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        if (p >= 0) stringResource(R.string.more_than_last, Fmt.percent(p)) else stringResource(R.string.less_than_last, Fmt.percent(-p)),
                        color = if (p > 0) c.expense else c.income, fontSize = 13.sp,
                    )
                }
            }
        }
        item { Spacer(Modifier.height(Spacing.fabClearance)) }
    }
}

@Composable
fun InfoCard(title: String, content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.xs, vertical = Spacing.sm - Spacing.xxs),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(Spacing.lg)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(Spacing.sm))
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
        Row(Modifier.fillMaxWidth().padding(Spacing.sm), horizontalArrangement = Arrangement.Center) {
            TypeToggle(stringResource(R.string.expense), expenseMode, c.expense) { expenseMode = true }
            Spacer(Modifier.width(Spacing.sm))
            TypeToggle(stringResource(R.string.income), !expenseMode, c.income) { expenseMode = false }
        }
        if (groups.isEmpty()) EmptyState(stringResource(R.string.empty_month))
        LazyColumn(Modifier.fillMaxSize()) {
            items(groups) { (note, count, sum) ->
                Row(
                    Modifier.fillMaxWidth().clickable {
                        val r = Fmt.monthRange(ym)
                        navigate(Route.TxnList(title = note.ifBlank { emptyNote }, from = r.first, to = r.last + 1, note = note, type = type))
                    }.padding(horizontal = Spacing.lg, vertical = Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(note.ifBlank { emptyNote }, Modifier.weight(1f), maxLines = 1)
                    Text("$count", color = c.muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = Spacing.md))
                    Text(Fmt.money(sum, s), color = if (expenseMode) c.expense else c.income)
                }
                HorizontalDivider(color = c.divider)
            }
            item { Spacer(Modifier.height(Spacing.fabClearance)) }
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
            .padding(horizontal = 18.dp, vertical = Spacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (selected) color else AppTheme.colors.muted, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
fun CarryOverRow(amount: Long, s: AppSettings) {
    val c = AppTheme.colors
    val color = if (amount >= 0) c.income else c.expense
    androidx.compose.material3.ListItem(
        modifier = Modifier.fillMaxWidth().testTag("carry_over_row"),
        colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { com.midroid.masarifi.ui.components.CategoryBadge("↩", color) },
        headlineContent = { Text(stringResource(R.string.carry_over_line), style = MaterialTheme.typography.bodyLarge) },
        trailingContent = {
            Text(
                "\u200E" + (if (amount > 0) "+" else "") + Fmt.money(amount, s, false),
                style = com.midroid.masarifi.ui.theme.Amount, color = color,
            )
        },
    )
}

@Composable
fun HomeOverview(
    netWorth: com.midroid.masarifi.data.NetWorth,
    topGoal: com.midroid.masarifi.data.SavingsGoal?,
    saved: Long,
    s: AppSettings,
    onGoals: () -> Unit,
) {
    Card(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.xs)
            .testTag("home_overview"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Row(Modifier.padding(Spacing.md)) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.net_worth),
                    style = MaterialTheme.typography.labelMedium,
                    color = AppTheme.colors.muted,
                )
                Text(
                    "\u200E" + Fmt.money(netWorth.net, s),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (netWorth.net >= 0) AppTheme.colors.income else AppTheme.colors.expense,
                )
            }
            if (topGoal != null) {
                Column(
                    Modifier
                        .weight(1f)
                        .clickable(onClick = onGoals),
                ) {
                    Text(
                        topGoal.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = AppTheme.colors.muted,
                        maxLines = 1,
                    )
                    ProgressBar(
                        com.midroid.masarifi.data.GoalLogic.progress(saved, topGoal.target),
                    )
                    Text(
                        stringResource(
                            R.string.goal_percent,
                            com.midroid.masarifi.data.GoalLogic.percent(saved, topGoal.target),
                        ),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            } else {
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.End,
                ) {
                    TextButton(onClick = onGoals) {
                        Text(stringResource(R.string.goal_add))
                    }
                }
            }
        }
    }
}
