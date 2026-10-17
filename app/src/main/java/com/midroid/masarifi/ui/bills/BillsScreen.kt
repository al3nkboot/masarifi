package com.midroid.masarifi.ui.bills

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.midroid.masarifi.R
import com.midroid.masarifi.bills.Bill
import com.midroid.masarifi.bills.BillLogic
import com.midroid.masarifi.bills.BillPayment
import com.midroid.masarifi.bills.BillState
import com.midroid.masarifi.data.AppSettings
import com.midroid.masarifi.data.Category
import com.midroid.masarifi.ui.AppViewModel
import com.midroid.masarifi.ui.Fmt
import com.midroid.masarifi.ui.Route
import com.midroid.masarifi.ui.components.ConfirmDialog
import com.midroid.masarifi.ui.components.EmptyState
import com.midroid.masarifi.ui.components.MonthSwitcher
import com.midroid.masarifi.ui.components.SectionTitle
import com.midroid.masarifi.ui.theme.AppTheme
import com.midroid.masarifi.ui.theme.Brand
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
/** «الفواتير» tab: list (overdue / soon / upcoming), calendar and payment history. */
@Composable
fun BillsScreen(vm: AppViewModel, navigate: (Route) -> Unit, modifier: Modifier) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val bills by vm.bills.collectAsStateWithLifecycle()
    val payments by vm.billPayments.collectAsStateWithLifecycle()
    val cats by vm.categories.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var paying by remember { mutableStateOf<Bill?>(null) }
    var menu by remember { mutableStateOf(false) }
    val today = vm.today()
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.importMi5g(uri) }

    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.tab_bills), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.bill_menu)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.bill_import_mi5g)) }, onClick = { menu = false; importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) })
                }
            }
        }
        val month = remember(bills, today) { BillLogic.upcomingInMonth(bills, today, YearMonth.from(today)) }
        val overdue = month.filter { it.date.isBefore(today) }
        BillsSummaryCard(month.sumOf { it.amount }, month.size, overdue.sumOf { it.amount }, overdue.size, s)
        val tabs = listOf(R.string.bill_tab_list, R.string.bill_tab_calendar, R.string.bill_tab_history)
        SecondaryTabRow(
            selectedTabIndex = tab, containerColor = Color.Transparent,
            indicator = { TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(tab), color = MaterialTheme.colorScheme.primary) },
        ) {
            tabs.forEachIndexed { i, res ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(stringResource(res), maxLines = 1) },
                    selectedContentColor = MaterialTheme.colorScheme.onBackground, unselectedContentColor = AppTheme.colors.muted)
            }
        }
        when (tab) {
            0 -> BillList(bills, cats, s, today, onOpen = { navigate(Route.BillEdit(it.id)) }, onPay = { paying = it })
            1 -> BillCalendar(bills, payments, s, today, onOpen = { navigate(Route.BillEdit(it.id)) }, onPay = { paying = it })
            else -> PaymentHistory(vm, bills, payments, s)
        }
    }
    paying?.let { b -> PayBillDialog(vm, b, onDismiss = { paying = null }) }
}

@Composable
private fun BillsSummaryCard(monthTotal: Long, monthCount: Int, overdue: Long, overdueCount: Int, s: AppSettings) {
    val c = AppTheme.colors
    Card(colors = CardDefaults.cardColors(containerColor = c.card), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(Modifier.padding(14.dp)) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.bill_due_this_month), fontSize = 12.sp, color = c.muted)
                Text(Fmt.money(monthTotal, s), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = c.expense)
                Text(stringResource(R.string.bill_n_bills, monthCount), fontSize = 11.sp, color = c.muted)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text(stringResource(R.string.bill_overdue), fontSize = 12.sp, color = c.muted)
                Text(Fmt.money(overdue, s), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if (overdueCount > 0) Brand.Coral else c.muted)
                Text(stringResource(R.string.bill_n_bills, overdueCount), fontSize = 11.sp, color = c.muted)
            }
        }
    }
}

@Composable
fun stateLabel(b: Bill, today: LocalDate): Pair<String, Color> {
    val d = BillLogic.daysLeft(b, today)
    val c = AppTheme.colors
    return when (BillLogic.state(b, today)) {
        BillState.OVERDUE -> stringResource(R.string.bill_overdue_days, -d) to Brand.Coral
        BillState.DUE_TODAY -> stringResource(R.string.bill_due_today) to Brand.Orange
        BillState.SOON -> stringResource(R.string.bill_in_days, d) to Brand.Orange
        BillState.UPCOMING -> stringResource(R.string.bill_in_days, d) to c.muted
        BillState.INACTIVE -> stringResource(R.string.bill_inactive) to c.muted
    }
}

@Composable
private fun BillList(bills: List<Bill>, cats: List<Category>, s: AppSettings, today: LocalDate, onOpen: (Bill) -> Unit, onPay: (Bill) -> Unit) {
    val groups = remember(bills, today) {
        bills.groupBy { BillLogic.state(it, today).let { st -> if (st == BillState.DUE_TODAY) BillState.SOON else st } }
    }
    LazyColumn(Modifier.fillMaxSize()) {
        if (bills.isEmpty()) item { EmptyState(stringResource(R.string.bill_empty)) }
        listOf(BillState.OVERDUE to R.string.bill_sec_overdue, BillState.SOON to R.string.bill_sec_soon, BillState.UPCOMING to R.string.bill_sec_upcoming, BillState.INACTIVE to R.string.bill_sec_inactive)
            .forEach { (st, title) ->
                val l = groups[st].orEmpty()
                if (l.isNotEmpty()) {
                    item(key = "h$st") { SectionTitle(stringResource(title) + " (${l.size})") }
                    items(l, key = { it.id }) { b -> BillRow(b, cats.firstOrNull { it.id == b.categoryId }, s, today, { onOpen(b) }, { onPay(b) }) }
                }
            }
        item { Spacer(Modifier.height(88.dp)) }
    }
}

@Composable
fun BillRow(b: Bill, cat: Category?, s: AppSettings, today: LocalDate, onClick: () -> Unit, onPay: () -> Unit) {
    val c = AppTheme.colors
    val (label, color) = stateLabel(b, today)
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).background(c.chip, CircleShape), contentAlignment = Alignment.Center) { Text(cat?.emoji ?: "🧾", fontSize = 18.sp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(b.name, fontSize = 16.sp, maxLines = 1)
            Text(
                (cat?.name(s.arabic)?.let { "$it • " } ?: "") + recurrenceLabel(b) + " • " + dateLabel(b.dueDate, s.arabic),
                fontSize = 12.sp, color = c.muted, maxLines = 1,
            )
            Text(label, fontSize = 12.sp, color = color, fontWeight = FontWeight.Medium)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(Fmt.money(b.amount, s), fontSize = 15.sp, color = c.expense, fontWeight = FontWeight.Medium)
            if (b.active) Button(
                onClick = onPay, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier.height(32.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.White),
            ) { Text(stringResource(R.string.bill_paid_btn), fontSize = 12.sp) }
        }
    }
    HorizontalDivider(color = c.divider)
}

fun dateLabel(d: LocalDate, ar: Boolean): String = d.format(DateTimeFormatter.ofPattern("d MMM yyyy", Fmt.locale(ar)))

@Composable
private fun BillCalendar(bills: List<Bill>, payments: List<BillPayment>, s: AppSettings, today: LocalDate, onOpen: (Bill) -> Unit, onPay: (Bill) -> Unit) {
    val c = AppTheme.colors
    var ym by rememberSaveable { mutableStateOf(YearMonth.from(today)) }
    var sel by rememberSaveable { mutableStateOf(today) }
    val items = remember(bills, payments, ym) { BillLogic.calendar(bills, payments, ym) }
    val first = if (s.weekStartsSaturday) DayOfWeek.SATURDAY else DayOfWeek.SUNDAY
    val cells = remember(ym, first) { BillLogic.monthGrid(ym, first) }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            MonthSwitcher(Fmt.monthTitle(ym, s.arabic), { ym = ym.minusMonths(1) }, { ym = ym.plusMonths(1) }, Modifier.fillMaxWidth(), onLabelClick = { ym = YearMonth.from(today) })
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                (0L until 7L).map { first.plus(it) }.forEach { d ->
                    Text(Fmt.weekdayNarrow(d, s.arabic), Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 11.sp, color = c.muted, maxLines = 1)
                }
            }
            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                    week.forEach { d ->
                        Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp)) {
                            if (d != null) {
                                val list = items[d].orEmpty()
                                val unpaid = list.filter { !it.paid }
                                val bg = when {
                                    d == sel -> MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                                    d == today -> c.chip
                                    else -> Color.Transparent
                                }
                                Column(
                                    Modifier.fillMaxSize().background(bg, RoundedCornerShape(8.dp)).clickable { sel = d },
                                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                                ) {
                                    Text(d.dayOfMonth.toString(), fontSize = 13.sp)
                                    Row {
                                        if (unpaid.isNotEmpty()) Dot(if (d.isBefore(today)) Brand.Coral else Brand.Orange)
                                        if (list.any { it.paid }) Dot(Brand.Green)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Dot(Brand.Orange); Text(" " + stringResource(R.string.bill_legend_due) + "   ", fontSize = 11.sp, color = c.muted)
                Dot(Brand.Coral); Text(" " + stringResource(R.string.bill_overdue) + "   ", fontSize = 11.sp, color = c.muted)
                Dot(Brand.Green); Text(" " + stringResource(R.string.bill_legend_paid), fontSize = 11.sp, color = c.muted)
            }
            HorizontalDivider(color = c.divider)
            SectionTitle(dateLabel(sel, s.arabic))
        }
        val day = items[sel].orEmpty()
        if (day.isEmpty()) item { EmptyState(stringResource(R.string.bill_none_day)) }
        items(day, key = { "${it.bill.id}-${it.date}-${it.paid}" }) { o ->
            Row(Modifier.fillMaxWidth().clickable { onOpen(o.bill) }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(o.bill.name, Modifier.weight(1f))
                Text(Fmt.money(o.amount, s), color = if (o.paid) Brand.Green else c.expense)
                Spacer(Modifier.width(8.dp))
                if (o.paid) Text(stringResource(R.string.bill_legend_paid), fontSize = 12.sp, color = Brand.Green)
                else if (o.date == o.bill.dueDate && o.bill.active) TextButton(onClick = { onPay(o.bill) }) { Text(stringResource(R.string.bill_paid_btn)) }
            }
            HorizontalDivider(color = c.divider)
        }
        item { Spacer(Modifier.height(88.dp)) }
    }
}

@Composable
private fun Dot(color: Color) = Box(Modifier.padding(1.dp).size(6.dp).background(color, CircleShape))

@Composable
private fun PaymentHistory(vm: AppViewModel, bills: List<Bill>, payments: List<BillPayment>, s: AppSettings) {
    val c = AppTheme.colors
    val byId = bills.associateBy { it.id }
    var deleting by remember { mutableStateOf<BillPayment?>(null) }
    LazyColumn(Modifier.fillMaxSize()) {
        if (payments.isEmpty()) item { EmptyState(stringResource(R.string.bill_no_history)) }
        payments.groupBy { YearMonth.from(Fmt.toDate(it.paidAt)) }.forEach { (ym, list) ->
            item(key = "m$ym") { SectionTitle(Fmt.monthTitle(ym, s.arabic) + " • " + Fmt.money(list.sumOf { it.amount }, s)) }
            items(list, key = { it.id }) { p -> PaymentRow(p, byId[p.billId]?.name ?: "—", s) { deleting = p } }
        }
        item { Spacer(Modifier.height(88.dp)) }
    }
    deleting?.let { p ->
        ConfirmDialog(stringResource(R.string.delete), stringResource(R.string.bill_confirm_delete_payment), stringResource(R.string.delete), { vm.deleteBillPayment(p) }, { deleting = null })
    }
}

@Composable
fun PaymentRow(p: BillPayment, name: String, s: AppSettings, onDelete: () -> Unit) {
    val c = AppTheme.colors
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("✅", fontSize = 16.sp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(name, fontSize = 15.sp)
            Text(
                stringResource(R.string.bill_paid_on, Fmt.dateLabel(p.paidAt, s.arabic)) + " • " + stringResource(R.string.bill_for_due, dateLabel(LocalDate.ofEpochDay(p.dueDate), s.arabic)) +
                    if (p.txnUuid == null) " • " + stringResource(R.string.bill_no_txn) else "",
                fontSize = 11.sp, color = c.muted,
            )
        }
        Text(Fmt.money(p.amount, s), color = c.expense, fontSize = 14.sp)
        IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, stringResource(R.string.delete), tint = c.muted) }
    }
    HorizontalDivider(color = c.divider)
}
