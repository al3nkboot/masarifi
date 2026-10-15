package com.midroid.masarifi.ui.summary

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.midroid.masarifi.R
import com.midroid.masarifi.analysis.FinancialSummary
import com.midroid.masarifi.analysis.SummaryCalc
import com.midroid.masarifi.analysis.SummaryPeriod
import com.midroid.masarifi.data.Category
import com.midroid.masarifi.ui.AppViewModel
import com.midroid.masarifi.ui.Fmt
import com.midroid.masarifi.ui.Route
import com.midroid.masarifi.ui.components.SectionTitle
import com.midroid.masarifi.ui.components.TrendBars
import com.midroid.masarifi.ui.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

/** UI period choice. */
sealed interface PeriodChoice {
    data object ThisMonth : PeriodChoice
    data object LastMonth : PeriodChoice
    data class Quarter(val year: Int, val q: Int) : PeriodChoice
    data class Custom(val from: LocalDate, val to: LocalDate) : PeriodChoice

    fun period(today: LocalDate): SummaryPeriod = when (this) {
        ThisMonth -> SummaryPeriod.month(YearMonth.from(today))
        LastMonth -> SummaryPeriod.month(YearMonth.from(today).minusMonths(1))
        is Quarter -> SummaryPeriod.quarter(year, q)
        is Custom -> SummaryPeriod.custom(from, to)
    }
}

fun summaryTexts(cats: List<Category>, money: (Long) -> String, ar: Boolean, noCat: String) = SummaryCalc.Texts(
    money = money,
    month = { Fmt.monthTitle(it, ar) },
    catName = { id -> cats.firstOrNull { it.id == id }?.let { it.emoji + " " + it.name(ar) } ?: noCat },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryScreen(vm: AppViewModel, navigate: (Route) -> Unit, onBack: () -> Unit, snackbar: SnackbarHostState) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val all by vm.txns.collectAsStateWithLifecycle()
    val cats by vm.categories.collectAsStateWithLifecycle()
    val choice by vm.summaryPeriod.collectAsStateWithLifecycle()
    val c = AppTheme.colors
    val ctx = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val today = LocalDate.now()
    val noCat = stringResource(R.string.sum_no_cat)
    val money: (Long) -> String = { Fmt.money(it, s) }
    val texts = remember(cats, s) { summaryTexts(cats, money, s.arabic, noCat) }
    val period = choice.period(today)
    val sum = remember(all, cats, period, s) { SummaryCalc.compute(all, cats, period, Fmt.zone, today, texts) }
    val labels = reportLabels()
    var showQuarter by remember { mutableStateOf(false) }
    var showCustom by remember { mutableStateOf(false) }
    var confirmBudgets by remember { mutableStateOf(false) }
    val appliedMsg = stringResource(R.string.sum_budgets_applied)
    val title = stringResource(R.string.sum_title)

    fun share(pdf: Boolean) = scope.launch {
        val lines = buildReportLines(sum, texts, labels, periodLabel(period, s.arabic))
        val file = withContext(Dispatchers.IO) {
            val dir = ReportExport.exportDir(ctx)
            if (pdf) File(dir, "masarifi-summary.pdf").also { ReportExport.renderPdf(lines, it) }
            else File(dir, "masarifi-summary.png").also { f -> f.outputStream().use { ReportExport.renderBitmap(lines).compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
        }
        ReportExport.shareFile(ctx, file, if (pdf) "application/pdf" else "image/png", title)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                actions = {
                    TextButton(onClick = { share(false) }) { Icon(Icons.Filled.Share, null); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.sum_share_image)) }
                    TextButton(onClick = { share(true) }) { Text("PDF") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize().testTag("summaryList")) {
            item("period") {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(choice == PeriodChoice.ThisMonth, { vm.summaryPeriod.value = PeriodChoice.ThisMonth }, { Text(stringResource(R.string.sum_this_month)) })
                    FilterChip(choice == PeriodChoice.LastMonth, { vm.summaryPeriod.value = PeriodChoice.LastMonth }, { Text(stringResource(R.string.sum_last_month)) })
                    Column {
                        FilterChip(choice is PeriodChoice.Quarter, { showQuarter = true }, {
                            Text((choice as? PeriodChoice.Quarter)?.let { "Q${it.q} ${it.year}" } ?: stringResource(R.string.sum_quarter))
                        })
                        DropdownMenu(showQuarter, { showQuarter = false }) {
                            for (y in listOf(today.year, today.year - 1)) for (q in 4 downTo 1) {
                                if (y == today.year && q > (today.monthValue - 1) / 3 + 1) continue
                                DropdownMenuItem({ Text("Q$q $y") }, { vm.summaryPeriod.value = PeriodChoice.Quarter(y, q); showQuarter = false })
                            }
                        }
                    }
                    FilterChip(choice is PeriodChoice.Custom, { showCustom = true }, {
                        Text((choice as? PeriodChoice.Custom)?.let { "${it.from} – ${it.to}" } ?: stringResource(R.string.sum_custom))
                    })
                }
                Text(periodLabel(period, s.arabic), color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
            item("overview") {
                SectionTitle(stringResource(R.string.sum_overview))
                Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard(stringResource(R.string.sum_total_expense), money(sum.totalExpense), c.expense)
                    StatCard(stringResource(R.string.sum_total_income), money(sum.totalIncome), c.income)
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard(stringResource(R.string.sum_count), sum.count.toString(), MaterialTheme.colorScheme.onBackground)
                    StatCard(
                        stringResource(if (sum.net >= 0) R.string.sum_surplus else R.string.sum_deficit), money(kotlin.math.abs(sum.net)),
                        if (sum.net >= 0) Color(0xFF4CAF50) else Color(0xFFEF5350),
                    )
                }
                Text(stringResource(R.string.sum_excluded), fontSize = 11.sp, color = c.muted, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                if (sum.count == 0) Text(stringResource(R.string.sum_empty), modifier = Modifier.padding(16.dp))
            }
            if (sum.categories.isNotEmpty()) item("cats") {
                SectionTitle(stringResource(R.string.sum_categories))
                TableRow(listOf(labels.colCat, labels.colCount, labels.colAmount, labels.colPct, labels.colAvg), CAT_W, header = true)
                sum.categories.forEach { r ->
                    TableRow(listOf(texts.catName(r.categoryId), r.count.toString(), money(r.amount), SummaryCalc.pctText(r.percent), money(r.average)), CAT_W)
                }
                TableRow(listOf(labels.total, sum.expenseCount.toString(), money(sum.totalExpense), "100%", if (sum.expenseCount > 0) money(sum.totalExpense / sum.expenseCount) else "—"), CAT_W, header = true)
            }
            if (sum.months.size > 1) item("months") {
                SectionTitle(stringResource(R.string.sum_months))
                TableRow(listOf(labels.colMonth, labels.colIncome, labels.colExpense, labels.net, labels.colPct, labels.colChange), MON_W, header = true)
                sum.months.forEach { m ->
                    TableRow(
                        listOf(Fmt.monthShort(m.month, s.arabic), money(m.income), money(m.expense), money(m.net), SummaryCalc.pctText(m.percentOfPeriod), m.changePercent?.let { (if (it > 0) "\u200E+" else "\u200E") + SummaryCalc.pctText(it) } ?: "—"),
                        MON_W, lastColor = m.changePercent?.let { if (it > 0) c.expense else Color(0xFF4CAF50) },
                    )
                }
                TrendBars(sum.months.map { Triple(Fmt.monthShort(it.month, s.arabic), it.income, it.expense) }, Modifier.padding(16.dp), highlightLast = false)
            }
            if (sum.topExpenses.isNotEmpty()) item("top") {
                SectionTitle(stringResource(R.string.sum_top))
                sum.topExpenses.forEach { x ->
                    Row(Modifier.fillMaxWidth().clickable { navigate(Route.Edit(txnId = x.id)) }.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(texts.catName(x.categoryId) + (x.note.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""), fontSize = 14.sp, maxLines = 1)
                            Text(Fmt.dateLabel(x.dateTime, s.arabic), fontSize = 12.sp, color = c.muted)
                        }
                        Text(money(x.amount), color = c.expense, fontWeight = FontWeight.SemiBold)
                    }
                    HorizontalDivider(color = c.divider)
                }
            }
            if (sum.insights.isNotEmpty()) item("insights") {
                SectionTitle(stringResource(R.string.sum_insights))
                sum.insights.forEach { i ->
                    Text(
                        "• " + i.text, fontSize = 14.sp,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).background(c.card, RoundedCornerShape(10.dp)).padding(12.dp),
                    )
                }
            }
            if (sum.needsReview.isNotEmpty()) item("review") {
                SectionTitle(stringResource(R.string.sum_review) + " (" + sum.needsReview.size + ")")
                sum.needsReview.take(30).forEach { x ->
                    Row(Modifier.fillMaxWidth().clickable { navigate(Route.Edit(txnId = x.id)) }.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(x.merchant ?: x.note.ifBlank { "—" }, fontSize = 14.sp, maxLines = 1)
                            Text(Fmt.dateLabel(x.dateTime, s.arabic), fontSize = 12.sp, color = c.muted)
                        }
                        Text(money(x.amount))
                    }
                    HorizontalDivider(color = c.divider)
                }
            }
            if (sum.budgets.isNotEmpty()) item("budget") {
                SectionTitle(stringResource(R.string.sum_budget))
                Text(stringResource(R.string.sum_baseline, money(sum.baselineMonthly)), fontSize = 13.sp, color = c.muted, modifier = Modifier.padding(horizontal = 16.dp))
                sum.budgets.forEach { b -> TableRow(listOf(texts.catName(b.categoryId), money(b.baselineMonthly), money(b.suggested)), listOf(3f, 2f, 2f)) }
                Button(onClick = { confirmBudgets = true }, modifier = Modifier.padding(16.dp)) { Text(stringResource(R.string.sum_apply_budgets)) }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showCustom) {
        val st = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { showCustom = false },
            confirmButton = {
                TextButton(onClick = {
                    val a = st.selectedStartDateMillis; val b = st.selectedEndDateMillis ?: a
                    if (a != null && b != null) {
                        fun d(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        vm.summaryPeriod.value = PeriodChoice.Custom(d(a), d(b))
                    }
                    showCustom = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { showCustom = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DateRangePicker(st, Modifier.weight(1f)) }
    }
    if (confirmBudgets) {
        AlertDialog(
            onDismissRequest = { confirmBudgets = false },
            text = { Text(stringResource(R.string.sum_apply_confirm, sum.budgets.size)) },
            confirmButton = {
                TextButton(onClick = { sum.budgets.forEach { vm.setBudget(it.categoryId, it.suggested) }; confirmBudgets = false; vm.message(appliedMsg) }) { Text(stringResource(R.string.sum_apply_budgets)) }
            },
            dismissButton = { TextButton(onClick = { confirmBudgets = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

private val CAT_W = listOf(3f, 1.2f, 2.4f, 1.4f, 2f)
private val MON_W = listOf(1.8f, 2.2f, 2.2f, 2.2f, 1.2f, 1.4f)

@Composable
private fun RowScope.StatCard(label: String, value: String, color: Color) {
    Column(Modifier.weight(1f).background(AppTheme.colors.card, RoundedCornerShape(12.dp)).padding(12.dp)) {
        Text(label, fontSize = 12.sp, color = AppTheme.colors.muted)
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun TableRow(cells: List<String>, weights: List<Float>, header: Boolean = false, lastColor: Color? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        cells.forEachIndexed { i, t ->
            Text(
                t, Modifier.weight(weights[i]), fontSize = 12.sp, maxLines = 2,
                fontWeight = if (header) FontWeight.Bold else FontWeight.Normal,
                color = if (i == cells.lastIndex && lastColor != null) lastColor else if (header) AppTheme.colors.muted else MaterialTheme.colorScheme.onBackground,
                textAlign = if (i == 0) TextAlign.Start else TextAlign.End,
            )
        }
    }
    HorizontalDivider(color = AppTheme.colors.divider)
}

data class ReportLabels(
    val title: String, val overview: String, val expense: String, val income: String, val count: String, val net: String,
    val surplus: String, val deficit: String, val excluded: String, val cats: String, val colCat: String, val colCount: String,
    val colAmount: String, val colPct: String, val colAvg: String, val total: String, val months: String, val colMonth: String,
    val colIncome: String, val colExpense: String, val colChange: String, val top: String, val insights: String, val budget: String, val baseline: String,
)

@Composable
fun reportLabels() = ReportLabels(
    stringResource(R.string.sum_title), stringResource(R.string.sum_overview), stringResource(R.string.sum_total_expense),
    stringResource(R.string.sum_total_income), stringResource(R.string.sum_count), stringResource(R.string.sum_net),
    stringResource(R.string.sum_surplus), stringResource(R.string.sum_deficit), stringResource(R.string.sum_excluded),
    stringResource(R.string.sum_categories), stringResource(R.string.sum_col_cat), stringResource(R.string.sum_col_count),
    stringResource(R.string.sum_col_amount), stringResource(R.string.sum_col_pct), stringResource(R.string.sum_col_avg),
    stringResource(R.string.sum_total), stringResource(R.string.sum_months), stringResource(R.string.sum_col_month),
    stringResource(R.string.sum_col_income), stringResource(R.string.sum_col_expense), stringResource(R.string.sum_col_change),
    stringResource(R.string.sum_top), stringResource(R.string.sum_insights), stringResource(R.string.sum_budget), stringResource(R.string.sum_baseline),
)

fun periodLabel(p: SummaryPeriod, ar: Boolean): String {
    val last = p.endExclusive.minusDays(1)
    return if (ar) "من ${p.start} إلى $last" else "${p.start} – $last"
}

/** The same content as the screen, for the PNG / PDF export. */
fun buildReportLines(sum: FinancialSummary, t: SummaryCalc.Texts, l: ReportLabels, periodText: String): List<ReportLine> = buildList {
    add(ReportLine.Title("${l.title} — مصاريفي"))
    add(ReportLine.Para(periodText))
    add(ReportLine.Header(l.overview))
    add(ReportLine.Row(listOf(l.expense, t.money(sum.totalExpense)), listOf(3f, 2f)))
    add(ReportLine.Row(listOf(l.count, sum.count.toString()), listOf(3f, 2f)))
    add(ReportLine.Row(listOf(l.income, t.money(sum.totalIncome)), listOf(3f, 2f)))
    add(ReportLine.Row(listOf(if (sum.net >= 0) l.surplus else l.deficit, t.money(kotlin.math.abs(sum.net))), listOf(3f, 2f), bold = true, color = if (sum.net >= 0) ReportExport.GREEN else ReportExport.RED))
    add(ReportLine.Para(l.excluded))
    if (sum.categories.isNotEmpty()) {
        add(ReportLine.Header(l.cats))
        add(ReportLine.Row(listOf(l.colCat, l.colCount, l.colAmount, l.colPct, l.colAvg), CAT_W, bold = true))
        sum.categories.forEach { r -> add(ReportLine.Row(listOf(t.catName(r.categoryId), r.count.toString(), t.money(r.amount), SummaryCalc.pctText(r.percent), t.money(r.average)), CAT_W)) }
        add(ReportLine.Row(listOf(l.total, sum.expenseCount.toString(), t.money(sum.totalExpense), "100%", if (sum.expenseCount > 0) t.money(sum.totalExpense / sum.expenseCount) else "—"), CAT_W, bold = true))
    }
    if (sum.months.size > 1) {
        add(ReportLine.Header(l.months))
        add(ReportLine.Row(listOf(l.colMonth, l.colIncome, l.colExpense, l.net, l.colPct, l.colChange), MON_W, bold = true))
        sum.months.forEach { m ->
            add(ReportLine.Row(listOf(t.month(m.month), t.money(m.income), t.money(m.expense), t.money(m.net), SummaryCalc.pctText(m.percentOfPeriod), m.changePercent?.let { (if (it > 0) "\u200E+" else "\u200E") + SummaryCalc.pctText(it) } ?: "—"), MON_W))
        }
        add(ReportLine.Bars(sum.months.map { Triple(t.month(it.month).substringBefore(' '), it.income, it.expense) }))
    }
    if (sum.topExpenses.isNotEmpty()) {
        add(ReportLine.Header(l.top))
        sum.topExpenses.forEach { x -> add(ReportLine.Row(listOf(java.time.Instant.ofEpochMilli(x.dateTime).atZone(Fmt.zone).toLocalDate().toString(), t.catName(x.categoryId), x.note, t.money(x.amount)), listOf(2f, 2.4f, 3f, 2f))) }
    }
    if (sum.insights.isNotEmpty()) {
        add(ReportLine.Header(l.insights))
        sum.insights.forEach { add(ReportLine.Para("• " + it.text)) }
    }
    if (sum.budgets.isNotEmpty()) {
        add(ReportLine.Header(l.budget))
        add(ReportLine.Para(String.format(l.baseline, t.money(sum.baselineMonthly))))
        sum.budgets.forEach { b -> add(ReportLine.Row(listOf(t.catName(b.categoryId), t.money(b.baselineMonthly), t.money(b.suggested)), listOf(3f, 2f, 2f))) }
    }
}
