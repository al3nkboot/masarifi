package com.midroid.masarifi.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.midroid.masarifi.R
import com.midroid.masarifi.ui.AppViewModel
import com.midroid.masarifi.ui.Route
import com.midroid.masarifi.ui.components.AppIcons
import com.midroid.masarifi.ui.theme.AppTheme

@Composable
fun AppRoot(vm: AppViewModel, openTxnId: Long?, onOpenTxnConsumed: () -> Unit, onLanguageChanged: () -> Unit) {
    val stack = remember { mutableStateListOf<Route>(Route.Home) }
    val snackbar = remember { SnackbarHostState() }
    val navigate: (Route) -> Unit = { stack.add(it) }
    val holder = rememberSaveableStateHolder()
    fun keyOf(i: Int) = "$i:${stack[i]}"
    val back: () -> Unit = {
        if (stack.size > 1) {
            holder.removeState(keyOf(stack.lastIndex))
            stack.removeAt(stack.lastIndex)
        }
    }

    LaunchedEffect(openTxnId) {
        if (openTxnId != null) { stack.add(Route.Edit(txnId = openTxnId)); onOpenTxnConsumed() }
    }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    val undoLabel = androidx.compose.ui.res.stringResource(R.string.undo)
    LaunchedEffect(undoLabel) {
        vm.undoEvents.collect { ev ->
            val r = snackbar.showSnackbar(ev.message, actionLabel = undoLabel, duration = androidx.compose.material3.SnackbarDuration.Long)
            if (r == androidx.compose.material3.SnackbarResult.ActionPerformed) vm.undoDelete(ev.rows)
        }
    }

    LaunchedEffect(undoLabel) {
        vm.billUndo.collect { ev ->
            val r = snackbar.showSnackbar(ev.message, actionLabel = undoLabel, duration = androidx.compose.material3.SnackbarDuration.Long)
            if (r == androidx.compose.material3.SnackbarResult.ActionPerformed) vm.undoBillPayment(ev.result)
        }
    }

    BackHandler(enabled = stack.size > 1) { back() }

    val r = stack.last()
    val key = keyOf(stack.lastIndex)
    holder.SaveableStateProvider(key) { RouteContent(r, vm, navigate, back, snackbar, onLanguageChanged) }
}

@Composable
private fun RouteContent(
    r: Route, vm: AppViewModel, navigate: (Route) -> Unit, back: () -> Unit,
    snackbar: SnackbarHostState, onLanguageChanged: () -> Unit,
) {
    when (r) {
        Route.Home -> HomeScaffold(vm, navigate, snackbar, onLanguageChanged)
        is Route.Edit -> AddEditScreen(vm, r, back, { navigate(Route.Categories) }, onOpenTxn = { id -> back(); navigate(Route.Edit(txnId = id)) })
        Route.Search -> SearchScreen(vm, null, navigate, back, snackbar)
        is Route.TxnList -> SearchScreen(vm, r, navigate, back, snackbar)
        Route.Duplicates -> DuplicatesScreen(vm, navigate, back, snackbar)
        Route.Categories -> CategoriesScreen(vm, back)
        Route.Recurring -> RecurringScreen(vm, back)
        Route.SmsSettings -> SmsSettingsScreen(vm, back, snackbar, onRepair = { navigate(Route.Repair) })
        Route.Repair -> RepairScreen(vm, back, snackbar)
        Route.Summary -> com.midroid.masarifi.ui.summary.SummaryScreen(vm, navigate, back, snackbar)
        Route.Backup -> BackupScreen(vm, back, snackbar, onLanguageChanged)
        Route.ImportExport -> ImportExportScreen(vm, back, snackbar)
        Route.Sync -> SyncScreen(vm, back, snackbar)
        is Route.BillEdit -> com.midroid.masarifi.ui.bills.BillEditScreen(vm, r.billId, back)
        Route.Warranties -> com.midroid.masarifi.ui.warranty.WarrantyListScreen(vm, navigate, back)
        is Route.WarrantyEdit -> com.midroid.masarifi.ui.warranty.WarrantyEditScreen(vm, r.id, back)
        is Route.WarrantyDetail -> com.midroid.masarifi.ui.warranty.WarrantyDetailScreen(vm, r.id, navigate, back)
        Route.Goals -> com.midroid.masarifi.ui.goals.GoalsScreen(vm, back)
        Route.Debts -> com.midroid.masarifi.ui.debts.DebtsScreen(vm, back)
    }
}

@Composable
private fun HomeScaffold(vm: AppViewModel, navigate: (Route) -> Unit, snackbar: SnackbarHostState, onLanguageChanged: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val openBills by vm.openBills.collectAsStateWithLifecycle()
    LaunchedEffect(openBills) { if (openBills) { tab = 3; vm.openBills.value = false } }
    val fabExpanded = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(true) }
    LaunchedEffect(tab) { fabExpanded.value = true }
    androidx.compose.runtime.CompositionLocalProvider(com.midroid.masarifi.ui.components.LocalFabExpanded provides fabExpanded) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        // transactions: rows keep the name on the start side and the amount on the end side, so a centred FAB sits in the empty middle
        floatingActionButtonPosition = if (tab == 0) androidx.compose.material3.FabPosition.Center else androidx.compose.material3.FabPosition.End,
        floatingActionButton = {
            if ((tab == 0 && !vm.selectionActive) || tab == 3) {
                androidx.compose.material3.ExtendedFloatingActionButton(
                    onClick = { if (tab == 3) navigate(Route.BillEdit()) else navigate(Route.Edit(date = vm.selectedDayForAdd())) },
                    icon = { Icon(Icons.Filled.Add, stringResource(R.string.add)) },
                    text = { Text(stringResource(R.string.add)) },
                    expanded = fabExpanded.value,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                val items = listOf(
                    R.string.tab_trans to AppIcons.Book,
                    R.string.tab_stats to AppIcons.PieChart,
                    R.string.tab_accounts to AppIcons.Wallet,
                    R.string.tab_bills to AppIcons.Receipt,
                    R.string.tab_more to AppIcons.MoreHoriz,
                )
                items.forEachIndexed { i, (label, icon) ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(icon, null) },
                        label = { Text(stringResource(label)) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = AppTheme.colors.muted,
                            unselectedTextColor = AppTheme.colors.muted,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        val missing by vm.cardSetupNeeded.collectAsStateWithLifecycle()
        androidx.compose.foundation.layout.Column(Modifier.padding(padding)) {
            if (com.midroid.masarifi.BuildConfig.SMS_ENABLED && missing.isNotEmpty()) CardSetupBanner(missing.joinToString("، ") { it.name }, onOpen = { navigate(Route.SmsSettings) }, onDismiss = { vm.dismissCardBanner() })
            val m = Modifier.weight(1f)
            when (tab) {
                0 -> TransScreen(vm, navigate, m)
                1 -> StatsScreen(vm, navigate, m)
                2 -> AccountsScreen(vm, navigate, m)
                3 -> com.midroid.masarifi.ui.bills.BillsScreen(vm, navigate, m)
                else -> MoreScreen(vm, navigate, m, onLanguageChanged)
            }
        }
    }
    }
}

/** One-time banner after the 1.5.0 upgrade when some SMS account has no card digits (dismiss = never shown again). */
@Composable
private fun CardSetupBanner(names: String, onOpen: () -> Unit, onDismiss: () -> Unit) {
    com.midroid.masarifi.ui.components.InfoStrip(
        text = stringResource(R.string.card_banner_text, names), icon = AppIcons.Wallet,
        tint = MaterialTheme.colorScheme.onSecondaryContainer, container = MaterialTheme.colorScheme.secondaryContainer,
        onClick = onOpen, action = stringResource(R.string.card_banner_open), onDismiss = onDismiss,
    )
}
