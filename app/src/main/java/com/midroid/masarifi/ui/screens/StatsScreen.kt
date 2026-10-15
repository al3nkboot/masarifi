package com.midroid.masarifi.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.midroid.masarifi.R
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
        Text(stringResource(R.string.tab_stats), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
        // prominent entry point: «الملخص المالي»
        androidx.compose.material3.Card(
            onClick = { navigate(Route.Summary) },
            colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            Column(Modifier.padding(14.dp)) {
                Text("📊 " + stringResource(R.string.sum_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.sum_entry_sub), style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.muted)
            }
        }
        MonthSwitcher(
            Fmt.monthTitle(ym, s.arabic), { vm.month.value = ym.minusMonths(1) }, { vm.month.value = ym.plusMonths(1) },
            Modifier.align(Alignment.CenterHorizontally), onLabelClick = { vm.month.value = YearMonth.now() },
        )
        val tabs = listOf(R.string.stats, R.string.trend, R.string.budget, R.string.tips)
        SecondaryTabRow(
            selectedTabIndex = tab, containerColor = Color.Transparent,
            indicator = { TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(tab), color = MaterialTheme.colorScheme.primary) },
        ) {
            tabs.forEachIndexed { i, res ->
                Tab(
                    selected = tab == i, onClick = { tab = i }, text = { Text(stringResource(res), maxLines = 1) },
                    selectedContentColor = MaterialTheme.colorScheme.onBackground, unselectedContentColor = AppTheme.colors.muted,
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
            CatSlice(id, c?.let { it.emoji + " " + it.name(s.arabic) } ?: noCat, amt, ChartPalette[i % ChartPalette.size])
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
            Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.Center) {
                TypeToggle("${stringResource(R.string.income)}  ${Fmt.money(txns.filter { it.type == TxnType.INCOME }.sumOf { it.amount }, s)}", !expenseMode, c.income) { expenseMode = false }
                Spacer(Modifier.width(8.dp))
                TypeToggle("${stringResource(R.string.expense)}  ${Fmt.money(txns.filter { it.type == TxnType.EXPENSE }.sumOf { it.amount }, s)}", expenseMode, c.expense) { expenseMode = true }
            }
        }
        item {
            Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                DonutChart(data.map { it.amount }, data.map { it.color }, size = 210.dp) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(if (expenseMode) R.string.expenses else R.string.income), color = c.muted, fontSize = 12.sp)
                        Text(Fmt.money(total, s), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }
        if (data.isEmpty()) item { EmptyState(stringResource(R.string.empty_month)) }
        items(data) { sl ->
            val pct = if (total > 0) sl.amount * 100.0 / total else 0.0
            Row(
                Modifier.fillMaxWidth().clickable {
                    val r = Fmt.monthRange(ym)
                    navigate(Route.TxnList(sl.label, categoryId = sl.categoryId ?: -1L, from = r.first, to = r.last + 1, type = type))
                }.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.widthIn(min = 54.dp).background(sl.color, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 3.dp), contentAlignment = Alignment.Center) {
                    Text(Fmt.percent(pct).replace(".0%", "%"), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.width(12.dp))
                Text(sl.label, Modifier.weight(1f), maxLines = 1)
                Text(Fmt.money(sl.amount, s))
            }
            HorizontalDivider(color = c.divider)
        }
        item { Spacer(Modifier.height(24.dp)) }
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
            Triple(id?.let { byCat[it] }?.let { it.emoji + " " + it.name(s.arabic) } ?: noCat, cur.filter { it.categoryId == id }.sumOf { it.amount }, prev.filter { it.categoryId == id }.sumOf { it.amount })
        }.sortedByDescending { maxOf(it.second, it.third) }
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        item {
            InfoCard(stringResource(R.string.monthly_trend)) {
                TrendBars(series.map { Triple(Fmt.monthShort(it.first, s.arabic).take(if (s.arabic) 6 else 3), it.second, it.third) })
                Spacer(Modifier.height(8.dp))
                ChartLegend(listOf(stringResource(R.string.income) to c.income, stringResource(R.string.expenses) to c.expense))
                val avg = series.dropLast(1).map { it.third }.filter { it > 0 }.let { if (it.isEmpty()) 0L else it.sum() / it.size }
                if (avg > 0) {
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.avg_monthly_expense, Fmt.money(avg, s)), fontSize = 12.sp, color = c.muted)
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
                Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                    Text(stringResource(R.string.category), Modifier.weight(1f), color = c.muted, fontSize = 12.sp)
                    Text(stringResource(R.string.last_month), Modifier.width(90.dp), color = c.muted, fontSize = 12.sp, textAlign = TextAlign.End)
                    Text(stringResource(R.string.this_month), Modifier.width(90.dp), color = c.muted, fontSize = 12.sp, textAlign = TextAlign.End)
                    Spacer(Modifier.width(52.dp))
                }
            }
        }
        items(compare) { (label, curA, prevA) ->
            Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.weight(1f), maxLines = 1, fontSize = 14.sp)
                Text(Fmt.money(prevA, s, false), Modifier.width(90.dp), textAlign = TextAlign.End, fontSize = 13.sp, color = c.muted)
                Text(Fmt.money(curA, s, false), Modifier.width(90.dp), textAlign = TextAlign.End, fontSize = 13.sp)
                Box(Modifier.width(52.dp), contentAlignment = Alignment.CenterEnd) {
                    Insights.percentChange(curA, prevA)?.let { p ->
                        Text((if (p > 0) "▲" else if (p < 0) "▼" else "") + "${kotlin.math.abs(p)}%", fontSize = 11.sp, color = if (p > 0) c.expense else c.income)
                    } ?: Text(if (curA > 0) "new" else "", fontSize = 11.sp, color = c.muted)
                }
            }
            HorizontalDivider(color = c.divider)
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun ChangeText(p: Int) {
    val c = AppTheme.colors
    Spacer(Modifier.height(4.dp))
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
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        item {
            val spent = exp.sumOf { it.amount }
            val b = bmap[0L] ?: 0L
            InfoCard(overallLabel) {
                BudgetLine(spent, b, s, isCurrent, ym) { editing = 0L to overallLabel }
            }
            Text(stringResource(R.string.budget_hint), fontSize = 12.sp, color = c.muted, modifier = Modifier.padding(8.dp))
        }
        items(cats.filter { it.type == TxnType.EXPENSE }) { cat ->
            val spent = exp.filter { it.categoryId == cat.id }.sumOf { it.amount }
            val b = bmap[cat.id] ?: 0L
            Column(Modifier.fillMaxWidth().clickable { editing = cat.id to cat.name(s.arabic) }.padding(horizontal = 8.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(cat.emoji + " " + cat.name(s.arabic), Modifier.weight(1f))
                    if (b > 0) Text("${Fmt.money(spent, s, false)} / ${Fmt.money(b, s)}", fontSize = 13.sp)
                    else Text(Fmt.money(spent, s), fontSize = 13.sp, color = c.muted)
                }
                if (b > 0) {
                    Spacer(Modifier.height(6.dp))
                    ProgressBar(spent.toDouble() / b)
                    Spacer(Modifier.height(2.dp))
                    val left = b - spent
                    Text(
                        if (left >= 0) stringResource(R.string.remaining_x, Fmt.money(left, s)) else stringResource(R.string.over_by_x, Fmt.money(-left, s)),
                        fontSize = 11.sp, color = if (left >= 0) c.muted else c.expense,
                    )
                }
            }
            HorizontalDivider(color = c.divider)
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
    editing?.let { (id, name) ->
        AmountInputDialog(stringResource(R.string.set_budget_for, name), bmap[id] ?: 0L, s, onDismiss = { editing = null }, onSave = { vm.setBudget(id, it) })
    }
}

@Composable
private fun BudgetLine(spent: Long, budget: Long, s: AppSettings, isCurrent: Boolean, ym: YearMonth, onEdit: () -> Unit) {
    val c = AppTheme.colors
    if (budget <= 0) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.no_budget_hint), Modifier.weight(1f), color = c.muted, fontSize = 13.sp)
            TextButton(onClick = onEdit) { Text(stringResource(R.string.set)) }
        }
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("${Fmt.money(spent, s)} / ${Fmt.money(budget, s)}", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
        TextButton(onClick = onEdit) { Text(stringResource(R.string.edit)) }
    }
    ProgressBar(spent.toDouble() / budget)
    Spacer(Modifier.height(4.dp))
    val left = budget - spent
    Text(
        if (left >= 0) stringResource(R.string.remaining_x, Fmt.money(left, s)) else stringResource(R.string.over_by_x, Fmt.money(-left, s)),
        fontSize = 12.sp, color = if (left >= 0) c.muted else c.expense,
    )
    if (isCurrent && left > 0) {
        val daysLeft = ym.lengthOfMonth() - LocalDate.now().dayOfMonth + 1
        Text(stringResource(R.string.per_day_x, Fmt.money(left / daysLeft, s)), fontSize = 12.sp, color = c.muted)
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
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
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
            item { Text(stringResource(R.string.unusual_spending), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp)) }
            items(spikes) { t ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).background(c.card, RoundedCornerShape(10.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("⚠️", fontSize = 18.sp); Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t.note.ifBlank { t.categoryId?.let { byCat[it]?.name(s.arabic) } ?: noCat }, fontSize = 14.sp)
                        Text(Fmt.dateLabel(t.dateTime, s.arabic) + " • " + (t.categoryId?.let { byCat[it]?.name(s.arabic) } ?: noCat), fontSize = 12.sp, color = c.muted)
                    }
                    Text(Fmt.money(t.amount, s), color = c.expense)
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
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
