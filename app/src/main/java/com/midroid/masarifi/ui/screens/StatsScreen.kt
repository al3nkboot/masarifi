package com.midroid.masarifi.ui.screens

import com.midroid.masarifi.ui.icon

import com.midroid.masarifi.ui.theme.Spacing
import androidx.compose.material3.SegmentedButton
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.midroid.masarifi.R
import com.midroid.masarifi.analysis.BudgetPace
import com.midroid.masarifi.analysis.CategoryComparison
import com.midroid.masarifi.analysis.Insights
import com.midroid.masarifi.data.AppSettings
import com.midroid.masarifi.data.Category
import com.midroid.masarifi.data.Txn
import com.midroid.masarifi.data.TxnType
import com.midroid.masarifi.ui.AppViewModel
import com.midroid.masarifi.ui.Fmt
import com.midroid.masarifi.ui.Route
import com.midroid.masarifi.ui.components.AmountInputDialog
import com.midroid.masarifi.ui.components.ChartLegend
import com.midroid.masarifi.ui.components.ChartPalette
import com.midroid.masarifi.ui.components.DonutChart
import com.midroid.masarifi.ui.components.EmptyState
import com.midroid.masarifi.ui.components.MonthSwitcher
import com.midroid.masarifi.ui.components.ProgressBar
import com.midroid.masarifi.ui.components.TrendBars
import com.midroid.masarifi.ui.theme.AppTheme
import com.midroid.masarifi.ui.theme.Brand
import java.time.LocalDate
import java.time.YearMonth

data class CatSlice(val categoryId: Long?, val label: String, val amount: Long, val color: Color)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(vm: AppViewModel, navigate: (Route) -> Unit, modifier: Modifier) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val all by vm.txns.collectAsStateWithLifecycle()
    val cats by vm.categories.collectAsStateWithLifecycle()
    val ym by vm.month.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Column(modifier.fillMaxSize()) {
        Text(stringResource(R.string.tab_stats), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md))
        // compact entry point: «الملخص المالي»
        androidx.compose.material3.Card(
            onClick = { navigate(Route.Summary) },
            shape = MaterialTheme.shapes.large,
            colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.xs),
        ) {
            androidx.compose.material3.ListItem(
                colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = { com.midroid.masarifi.ui.components.CategoryBadge("📊", MaterialTheme.colorScheme.primary) },
                headlineContent = { Text(stringResource(R.string.sum_title), style = MaterialTheme.typography.titleMedium, maxLines = 1) },
                supportingContent = { Text(stringResource(R.string.sum_entry_sub), style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.muted, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                trailingContent = { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = AppTheme.colors.muted) },
            )
        }
        MonthSwitcher(
            Fmt.monthTitle(ym, s.arabic), { vm.month.value = ym.minusMonths(1) }, { vm.month.value = ym.plusMonths(1) },
            Modifier.align(Alignment.CenterHorizontally), onLabelClick = { vm.month.value = YearMonth.now() },
        )
        val tabs = listOf(R.string.stats, R.string.trend, R.string.budget, R.string.tips)
        androidx.compose.material3.PrimaryTabRow(selectedTabIndex = tab, containerColor = Color.Transparent, divider = { HorizontalDivider(color = AppTheme.colors.divider) }) {
            tabs.forEachIndexed { i, res ->
                Tab(
                    selected = tab == i, onClick = { tab = i }, text = { Text(stringResource(res), maxLines = 1) },
                    selectedContentColor = MaterialTheme.colorScheme.primary, unselectedContentColor = AppTheme.colors.muted,
                )
            }
        }
        when (tab) {
            0 -> PieTab(all, cats, ym, s, navigate)
            1 -> TrendTab(all, cats, ym, s)
            2 -> BudgetTab(vm, all, cats, ym, s)
            else -> TipsTab(vm, all, cats, ym, s)
        }
    }
}

fun monthTxns(all: List<Txn>, ym: YearMonth): List<Txn> { val r = Fmt.monthRange(ym); return all.filter { it.dateTime in r } }

fun slices(txns: List<Txn>, cats: List<Category>, type: Int, s: AppSettings, noCat: String): List<CatSlice> {
    val byCat = cats.associateBy { it.id }
    return txns.filter { it.type == type }.groupBy { it.categoryId }
        .map { (id, l) -> id to l.sumOf { it.amount } }
        .sortedByDescending { it.second }
        .mapIndexed { i, (id, amt) ->
            val c = id?.let { byCat[it] }
            CatSlice(id, c?.let { it.icon + " " + it.name(s.arabic) } ?: noCat, amt, ChartPalette[i % ChartPalette.size])
        }
}

@Composable
private fun PieTab(all: List<Txn>, cats: List<Category>, ym: YearMonth, s: AppSettings, navigate: (Route) -> Unit) {
    val c = AppTheme.colors
    var expenseMode by rememberSaveable { mutableStateOf(true) }
    val type = if (expenseMode) TxnType.EXPENSE else TxnType.INCOME
    val noCat = stringResource(R.string.no_category)
    val txns = remember(all, ym) { monthTxns(all, ym) }
    val data = remember(txns, cats, type, s) { slices(txns, cats, type, s, noCat) }
    val total = data.sumOf { it.amount }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            val incSum = txns.filter { it.type == TxnType.INCOME }.sumOf { it.amount }
            val expSum = txns.filter { it.type == TxnType.EXPENSE }.sumOf { it.amount }
            androidx.compose.material3.SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm)) {
                listOf(true to Triple(R.string.expense, expSum, c.expense), false to Triple(R.string.income, incSum, c.income)).forEachIndexed { i, (exp, d) ->
                    SegmentedButton(
                        selected = expenseMode == exp, onClick = { expenseMode = exp },
                        shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(i, 2),
                        icon = {},
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(d.first), style = MaterialTheme.typography.labelMedium, maxLines = 1)
                            Text(Fmt.money(d.second, s, false), style = com.midroid.masarifi.ui.theme.Amount, fontSize = 14.sp, color = d.third, maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
        }
        item {
            Box(Modifier.fillMaxWidth().padding(vertical = Spacing.md), contentAlignment = Alignment.Center) {
                DonutChart(data.map { it.amount }, data.map { it.color }, size = 210.dp) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(if (expenseMode) R.string.expenses else R.string.income), color = c.muted, style = MaterialTheme.typography.bodySmall)
                        Text(Fmt.money(total, s), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
        if (data.isEmpty()) item { EmptyState(stringResource(R.string.empty_month)) }
        items(data) { sl ->
            val pct = if (total > 0) sl.amount * 100.0 / total else 0.0
            androidx.compose.material3.ListItem(
                modifier = Modifier.fillMaxWidth().clickable {
                    val r = Fmt.monthRange(ym)
                    navigate(Route.TxnList(sl.label, categoryId = sl.categoryId ?: -1L, from = r.first, to = r.last + 1, type = type))
                },
                colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = {
                    Box(Modifier.widthIn(min = 52.dp).background(sl.color.copy(alpha = 0.2f), MaterialTheme.shapes.small).padding(horizontal = Spacing.sm, vertical = Spacing.xs), contentAlignment = Alignment.Center) {
                        Text("\u200E" + Fmt.percent(pct).replace(".0%", "%"), color = sl.color, style = MaterialTheme.typography.labelMedium)
                    }
                },
                headlineContent = { Text(sl.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                trailingContent = { Text(Fmt.money(sl.amount, s, false), style = com.midroid.masarifi.ui.theme.Amount, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface) },
            )
        }
        item { Spacer(Modifier.height(Spacing.xl)) }
    }
}

@Composable
private fun TrendTab(all: List<Txn>, cats: List<Category>, ym: YearMonth, s: AppSettings) {
    val c = AppTheme.colors
    val months = (5 downTo 0).map { ym.minusMonths(it.toLong()) }
    val series = remember(all, ym) {
        months.map { m -> val l = monthTxns(all, m); Triple(m, l.filter { it.type == TxnType.INCOME }.sumOf { it.amount }, l.filter { it.type == TxnType.EXPENSE }.sumOf { it.amount }) }
    }
    val cur = remember(all, ym) { monthTxns(all, ym).filter { it.type == TxnType.EXPENSE } }
    val prev = remember(all, ym) { monthTxns(all, ym.minusMonths(1)).filter { it.type == TxnType.EXPENSE } }
    val byCat = cats.associateBy { it.id }
    val noCat = stringResource(R.string.no_category)
    val compare = remember(cur, prev, cats, s) {
        val ids = (cur.map { it.categoryId } + prev.map { it.categoryId }).toSet()
        ids.map { id ->
            Triple(id?.let { byCat[it] }?.let { it.icon + " " + it.name(s.arabic) } ?: noCat, cur.filter { it.categoryId == id }.sumOf { it.amount }, prev.filter { it.categoryId == id }.sumOf { it.amount })
        }.sortedByDescending { maxOf(it.second, it.third) }
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = Spacing.md)) {
        item {
            InfoCard(stringResource(R.string.monthly_trend)) {
                TrendBars(series.map { Triple(Fmt.monthShort(it.first, s.arabic).take(if (s.arabic) 6 else 3), it.second, it.third) })
                Spacer(Modifier.height(Spacing.sm))
                ChartLegend(listOf(stringResource(R.string.income) to c.income, stringResource(R.string.expenses) to c.expense))
                val avg = series.dropLast(1).map { it.third }.filter { it > 0 }.let { if (it.isEmpty()) 0L else it.sum() / it.size }
                if (avg > 0) {
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.avg_monthly_expense, Fmt.money(avg, s)), style = MaterialTheme.typography.bodySmall, color = c.muted)
                }
            }
        }
        item {
            InfoCard(stringResource(R.string.compare_last_month)) {
                val curT = cur.sumOf { it.amount }; val prevT = prev.sumOf { it.amount }
                Row { Text(Fmt.monthTitle(ym.minusMonths(1), s.arabic), Modifier.weight(1f), color = c.muted); Text(Fmt.money(prevT, s)) }
                Row { Text(Fmt.monthTitle(ym, s.arabic), Modifier.weight(1f)); Text(Fmt.money(curT, s), color = c.expense) }
                Insights.percentChange(curT, prevT)?.let { p -> ChangeText(p) }
            }
        }
        if (compare.isNotEmpty()) {
            item {
                Row(Modifier.padding(horizontal = Spacing.sm, vertical = 6.dp)) {
                    Text(stringResource(R.string.category), Modifier.weight(1f), color = c.muted, style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.last_month), Modifier.width(90.dp), color = c.muted, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
                    Text(stringResource(R.string.this_month), Modifier.width(90.dp), color = c.muted, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
                    Spacer(Modifier.width(52.dp))
                }
            }
        }
        items(compare) { (label, curA, prevA) ->
            Row(Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.weight(1f), maxLines = 1, style = MaterialTheme.typography.bodyMedium)
                Text(Fmt.money(prevA, s, false), Modifier.width(90.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.bodyMedium, color = c.muted)
                Text(Fmt.money(curA, s, false), Modifier.width(90.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.bodyMedium)
                Box(Modifier.width(52.dp), contentAlignment = Alignment.CenterEnd) {
                    Insights.percentChange(curA, prevA)?.let { p ->
                        Text((if (p > 0) "▲" else if (p < 0) "▼" else "") + "${kotlin.math.abs(p)}%", style = MaterialTheme.typography.labelSmall, color = if (p > 0) c.expense else c.income)
                    } ?: Text(if (curA > 0) "new" else "", style = MaterialTheme.typography.labelSmall, color = c.muted)
                }
            }
            HorizontalDivider(color = c.divider)
        }
        item { Spacer(Modifier.height(Spacing.xl)) }
    }
}

@Composable
private fun ChangeText(p: Int) {
    val c = AppTheme.colors
    Spacer(Modifier.height(Spacing.xs))
    Text(
        if (p >= 0) stringResource(R.string.more_than_last, "$p%") else stringResource(R.string.less_than_last, "${-p}%"),
        color = if (p > 0) c.expense else c.income, fontSize = 13.sp,
    )
}

@Composable
private fun BudgetTab(vm: AppViewModel, all: List<Txn>, cats: List<Category>, ym: YearMonth, s: AppSettings) {
    val c = AppTheme.colors
    val budgets by vm.budgets.collectAsStateWithLifecycle()
    val bmap = budgets.associate { it.categoryId to it.amount }
    val exp = remember(all, ym) { monthTxns(all, ym).filter { it.type == TxnType.EXPENSE } }
    var editing by remember { mutableStateOf<Pair<Long, String>?>(null) }
    val overallLabel = stringResource(R.string.overall_budget)
    val isCurrent = ym == YearMonth.now()
    val bills by vm.bills.collectAsStateWithLifecycle()
    val upcoming = remember(bills, ym) { com.midroid.masarifi.bills.BillLogic.upcomingInMonth(bills, vm.today(), ym) }

    // 2.8: Compute total budget, total spent, days left and pace ratio
    val totalSpent = exp.sumOf { it.amount }
    val totalBudget = remember(bmap) { BudgetPace.totalBudget(bmap) }
    val daysLeft = remember(ym) { BudgetPace.daysLeft(ym) }
    val paceRatio = remember(ym) { BudgetPace.paceRatio(ym) }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = Spacing.md).then(Modifier.testTag("budget_list"))) {
        item {
            if (totalBudget > 0L) {
                // 2.8: Prominent spending pace overview card with gauge and 3 summary figures
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = Spacing.xs, vertical = Spacing.sm - Spacing.xxs),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                    Column(Modifier.padding(Spacing.lg)) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                if (bmap[0L]?.let { it > 0 } == true) overallLabel else stringResource(R.string.monthly_budget),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { editing = 0L to overallLabel }) {
                                Text(stringResource(if (bmap[0L]?.let { it > 0 } == true) R.string.edit else R.string.set))
                            }
                        }

                        // 2.8: Prominent gauge showing how much is left to spend (or over-limit in Coral)
                        val ratio = BudgetPace.spentRatio(totalBudget, totalSpent)
                        val gaugeColor = when {
                            ratio >= 1.0 -> Brand.Coral
                            ratio >= 0.8 -> Brand.Orange
                            else -> Brand.Blue
                        }
                        val isOver = totalSpent > totalBudget
                        val remaining = BudgetPace.remaining(totalBudget, totalSpent)
                        val over = BudgetPace.overAmount(totalBudget, totalSpent)

                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm),
                            contentAlignment = Alignment.Center,
                        ) {
                            val trackColor = c.chip
                            Canvas(Modifier.size(190.dp)) {
                                val strokeWidth = 14.dp.toPx()
                                val inset = strokeWidth / 2f
                                val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                                // 2.8: Mirror the arc in RTL so fill grows from the start edge.
                                val sx = if (layoutDirection == androidx.compose.ui.unit.LayoutDirection.Rtl) -1f else 1f
                                scale(scaleX = sx, scaleY = 1f) {
                                    drawArc(
                                        color = trackColor,
                                        startAngle = 150f,
                                        sweepAngle = 240f,
                                        useCenter = false,
                                        topLeft = Offset(inset, inset),
                                        size = arcSize,
                                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                                    )
                                    val progressSweep = (ratio.coerceIn(0.0, 1.0) * 240.0).toFloat()
                                    if (progressSweep > 0f) {
                                        drawArc(
                                            color = gaugeColor,
                                            startAngle = 150f,
                                            sweepAngle = progressSweep,
                                            useCenter = false,
                                            topLeft = Offset(inset, inset),
                                            size = arcSize,
                                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                                        )
                                    }
                                }
                            }
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(horizontal = Spacing.md),
                            ) {
                                Text(
                                    text = if (isOver) stringResource(R.string.budget_over) else stringResource(R.string.budget_left_to_spend),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isOver) AppTheme.colors.expense else AppTheme.colors.muted,
                                    maxLines = 1,
                                )
                                Spacer(Modifier.height(Spacing.xxs))
                                Text(
                                    text = if (isOver) Fmt.money(over, s) else Fmt.money(remaining, s),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isOver) AppTheme.colors.expense else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                )
                                if (isCurrent && remaining > 0 && daysLeft > 0) {
                                    Spacer(Modifier.height(Spacing.xxs))
                                    Text(
                                        text = stringResource(R.string.per_day_x, Fmt.money(remaining / daysLeft, s)),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = AppTheme.colors.muted,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }

                        // 2.8: Three summary figures: total budget, total spent, days left
                        Spacer(Modifier.height(Spacing.xs))
                        HorizontalDivider(color = c.divider.copy(alpha = 0.5f))
                        Spacer(Modifier.height(Spacing.sm))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.budget), style = MaterialTheme.typography.labelSmall, color = c.muted)
                                Spacer(Modifier.height(Spacing.xxs))
                                Text(Fmt.money(totalBudget, s, false), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.expenses), style = MaterialTheme.typography.labelSmall, color = c.muted)
                                Spacer(Modifier.height(Spacing.xxs))
                                Text(Fmt.money(totalSpent, s, false), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = if (isOver) c.expense else MaterialTheme.colorScheme.onSurface, maxLines = 1)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.days_left_title), style = MaterialTheme.typography.labelSmall, color = c.muted)
                                Spacer(Modifier.height(Spacing.xxs))
                                val daysText = if (daysLeft == 1) stringResource(R.string.day_left) else stringResource(R.string.days_left, daysLeft)
                                Text(daysText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                            }
                        }
                    }
                }
            } else {
                // 2.8: Empty CTA card when no budget is set
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = Spacing.xs, vertical = Spacing.sm - Spacing.xxs),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                    Column(Modifier.padding(Spacing.lg)) {
                        Text(overallLabel, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(Spacing.sm))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.no_budget_hint), Modifier.weight(1f), color = c.muted, style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = { editing = 0L to overallLabel }) { Text(stringResource(R.string.set)) }
                        }
                    }
                }
            }

            if (isCurrent) ForecastCard(vm, all, upcoming.sumOf { it.amount }, ym, s)
            if (upcoming.isNotEmpty()) InfoCard("🧾 " + stringResource(R.string.bill_budget_card)) {
                val due = upcoming.sumOf { it.amount }
                Text(stringResource(R.string.bill_budget_line, Fmt.money(due, s), upcoming.size), style = MaterialTheme.typography.bodyMedium)
                val projected = totalSpent + due
                val b = bmap[0L] ?: 0L
                Text(
                    stringResource(R.string.bill_budget_projected, Fmt.money(projected, s)) + if (b > 0) " / ${Fmt.money(b, s)}" else "",
                    fontSize = 12.sp, color = if (b > 0 && projected > b) c.expense else c.muted,
                )
                if (b > 0) { Spacer(Modifier.height(6.dp)); ProgressBar(projected.toDouble() / b) }
                Spacer(Modifier.height(6.dp))
                upcoming.take(5).forEach { o ->
                    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.xxs)) {
                        Text(o.bill.name + " • " + com.midroid.masarifi.ui.bills.dateLabel(o.date, s.arabic), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = c.muted, maxLines = 1)
                        Text(Fmt.money(o.amount, s), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Text(stringResource(R.string.budget_hint), style = MaterialTheme.typography.bodySmall, color = c.muted, modifier = Modifier.padding(Spacing.sm))
        }
        items(cats.filter { it.type == TxnType.EXPENSE }) { cat ->
            val spent = exp.filter { it.categoryId == cat.id }.sumOf { it.amount }
            val b = bmap[cat.id] ?: 0L
            Column(Modifier.fillMaxWidth().clickable { editing = cat.id to cat.name(s.arabic) }.padding(horizontal = Spacing.sm, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(cat.icon + " " + cat.name(s.arabic), Modifier.weight(1f))
                    if (b > 0) Text("${Fmt.money(spent, s, false)} / ${Fmt.money(b, s)}", style = MaterialTheme.typography.bodyMedium)
                    else Text(Fmt.money(spent, s), style = MaterialTheme.typography.bodyMedium, color = c.muted)
                }
                if (b > 0) {
                    Spacer(Modifier.height(6.dp))
                    // 2.8: Paced progress bar with RTL-safe Today pace marker
                    PacedProgressBar(ratio = spent.toDouble() / b, paceRatio = paceRatio)
                    Spacer(Modifier.height(Spacing.xxs))
                    val left = b - spent
                    if (left >= 0) {
                        Text(
                            stringResource(R.string.remaining_x, Fmt.money(left, s)),
                            fontSize = 11.sp, color = c.muted,
                        )
                    } else {
                        // 2.8: More prominent over-limit display in red
                        Text(
                            "⚠️ " + stringResource(R.string.over_by_x, Fmt.money(-left, s)),
                            fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c.expense,
                        )
                    }
                }
            }
            HorizontalDivider(color = c.divider)
        }
        item { Spacer(Modifier.height(Spacing.xl)) }
    }
    editing?.let { (id, name) ->
        AmountInputDialog(stringResource(R.string.set_budget_for, name), bmap[id] ?: 0L, s, onDismiss = { editing = null }, onSave = { vm.setBudget(id, it) })
    }
}

/**
 * 2.8: Progress bar for category budget with spending pace marker.
 * Uses layout-direction-aware Alignment.CenterStart/CenterEnd and fillMaxWidth
 * to position the "Today" marker safely in both LTR and Arabic RTL.
 */
@Composable
fun PacedProgressBar(
    ratio: Double,
    paceRatio: Double?,
    modifier: Modifier = Modifier,
) {
    val color = when {
        ratio >= 1.0 -> Brand.Coral
        ratio >= 0.8 -> Brand.Orange
        else -> Brand.Blue
    }
    Column(modifier.fillMaxWidth()) {
        if (paceRatio != null) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 2.dp),
            ) {
                Box(
                    Modifier.fillMaxWidth(paceRatio.coerceIn(0.01, 1.0).toFloat()),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Text(
                        stringResource(R.string.today_label),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        color = AppTheme.colors.muted,
                        maxLines = 1,
                    )
                }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            // Background track and filled progress
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .background(AppTheme.colors.chip, RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.CenterStart,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(ratio.coerceIn(0.0, 1.0).toFloat())
                        .height(8.dp)
                        .background(color, RoundedCornerShape(4.dp)),
                )
            }
            // Vertical marker at pace ratio
            if (paceRatio != null) {
                Box(
                    Modifier.fillMaxWidth(paceRatio.coerceIn(0.01, 1.0).toFloat()),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Box(
                        Modifier
                            .width(2.dp)
                            .height(14.dp)
                            .background(MaterialTheme.colorScheme.onSurface, RoundedCornerShape(1.dp)),
                    )
                }
            }
        }
    }
}

@Composable
private fun TipsTab(vm: AppViewModel, all: List<Txn>, cats: List<Category>, ym: YearMonth, s: AppSettings) {
    val c = AppTheme.colors
    val budgets by vm.budgets.collectAsStateWithLifecycle()
    val noCat = stringResource(R.string.no_category)
    val tips = remember(all, cats, ym, s, budgets) { computeTips(all, cats, ym, s, budgets.firstOrNull { it.categoryId == 0L }?.amount ?: 0L, noCat) }
    val spikes = remember(all, ym) { unusualTxns(all, ym) }
    val byCat = cats.associateBy { it.id }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = Spacing.md)) {
        if (tips.isEmpty() && spikes.isEmpty()) item { EmptyState(stringResource(R.string.no_tips)) }
        items(tips) { t ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 5.dp).background(c.card, RoundedCornerShape(10.dp)).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("💡", fontSize = 20.sp); Spacer(Modifier.width(10.dp)); Text(t, fontSize = 14.sp)
            }
        }
        if (spikes.isNotEmpty()) {
            item { Text(stringResource(R.string.unusual_spending), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = Spacing.lg, bottom = 6.dp)) }
            items(spikes) { t ->
                Row(Modifier.fillMaxWidth().padding(vertical = Spacing.xs).background(c.card, RoundedCornerShape(10.dp)).padding(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                    Text("⚠️", style = MaterialTheme.typography.titleMedium); Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t.note.ifBlank { t.categoryId?.let { byCat[it]?.name(s.arabic) } ?: noCat }, style = MaterialTheme.typography.bodyMedium)
                        Text(Fmt.dateLabel(t.dateTime, s.arabic) + " • " + (t.categoryId?.let { byCat[it]?.name(s.arabic) } ?: noCat), style = MaterialTheme.typography.bodySmall, color = c.muted)
                    }
                    Text(Fmt.money(t.amount, s), color = c.expense)
                }
            }
        }
        item { Spacer(Modifier.height(Spacing.xl)) }
    }
}

fun computeTips(all: List<Txn>, cats: List<Category>, ym: YearMonth, s: AppSettings, overallBudget: Long, noCat: String): List<String> {
    val today = LocalDate.now()
    val isCurrent = ym == YearMonth.from(today)
    val day = if (isCurrent) today.dayOfMonth else ym.lengthOfMonth()
    val cur = monthTxns(all, ym)
    // Compare month-to-date with the same period of last month for a fair comparison
    val prevYm = ym.minusMonths(1)
    val prevEnd = Fmt.startOf(prevYm.atDay(minOf(day, prevYm.lengthOfMonth())).plusDays(1))
    val prev = all.filter { it.dateTime >= Fmt.startOf(prevYm.atDay(1)) && it.dateTime < (if (isCurrent) prevEnd else Fmt.monthRange(prevYm).last + 1) }
    val curExp = cur.filter { it.type == TxnType.EXPENSE }
    val prevExp = prev.filter { it.type == TxnType.EXPENSE }
    val byCat = cats.associateBy { it.id }
    val ids = (curExp.map { it.categoryId } + prevExp.map { it.categoryId }).toSet()
    val comps = ids.map { id ->
        CategoryComparison(id ?: -1, id?.let { byCat[it]?.name(s.arabic) } ?: noCat, curExp.filter { it.categoryId == id }.sumOf { it.amount }, prevExp.filter { it.categoryId == id }.sumOf { it.amount })
    }
    return Insights.tips(
        comps, cur.filter { it.type == TxnType.INCOME }.sumOf { it.amount }, curExp.sumOf { it.amount }, prevExp.sumOf { it.amount },
        overallBudget, day, ym.lengthOfMonth(), isCurrent, s.arabic,
    ) { Fmt.money(it, s) }
}

/** Expenses this month that are ≥3× the category's median over the previous 6 months. */
fun unusualTxns(all: List<Txn>, ym: YearMonth): List<Txn> {
    val cur = monthTxns(all, ym).filter { it.type == TxnType.EXPENSE }
    val histFrom = Fmt.startOf(ym.minusMonths(6).atDay(1))
    val histTo = Fmt.startOf(ym.atDay(1))
    val hist = all.filter { it.type == TxnType.EXPENSE && it.dateTime >= histFrom && it.dateTime < histTo }.groupBy { it.categoryId }
    return cur.filter { t -> Insights.isUnusualTransaction(hist[t.categoryId].orEmpty().map { it.amount }, t.amount) }.sortedByDescending { it.amount }.take(5)
}

/** 2.9.0: projected balance at the end of the current month (see [com.midroid.masarifi.analysis.BalanceForecast]). */
@Composable
private fun ForecastCard(vm: AppViewModel, all: List<Txn>, unpaidBills: Long, ym: YearMonth, s: AppSettings) {
    val c = AppTheme.colors
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val rules by vm.recurring.collectAsStateWithLifecycle()
    val f = remember(accounts, all, rules, unpaidBills, ym) {
        val current = com.midroid.masarifi.data.NetWorthCalc.compute(vm.balances(accounts, all).map { it.account to it.balance }).net
        val end = ym.atEndOfMonth().atTime(java.time.LocalTime.MAX).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        com.midroid.masarifi.analysis.BalanceForecast.compute(current, rules, unpaidBills, System.currentTimeMillis(), end, vm::advance)
    }
    InfoCard("📈 " + stringResource(R.string.forecast_title)) {
        Text(
            "\u200E" + Fmt.money(f.projected, s), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold,
            color = if (f.projected >= 0) c.income else c.expense,
        )
        Text(stringResource(R.string.forecast_projected), style = MaterialTheme.typography.bodySmall, color = c.muted)
        Spacer(Modifier.height(Spacing.sm))
        listOf(
            Triple(R.string.forecast_current, f.current, MaterialTheme.colorScheme.onSurface),
            Triple(R.string.forecast_income, f.income, c.income),
            Triple(R.string.forecast_expense, -f.expense, c.expense),
        ).forEach { (label, v, col) ->
            Row(Modifier.fillMaxWidth().padding(vertical = Spacing.xxs)) {
                Text(stringResource(label), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Text("\u200E" + Fmt.money(v, s), style = MaterialTheme.typography.bodyMedium, color = col)
            }
        }
        Text(stringResource(R.string.forecast_hint), style = MaterialTheme.typography.bodySmall, color = c.muted, modifier = Modifier.padding(top = Spacing.xs))
    }
}
