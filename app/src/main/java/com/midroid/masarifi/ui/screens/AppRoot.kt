package com.midroid.masarifi.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
    }
}

@Composable
private fun HomeScaffold(vm: AppViewModel, navigate: (Route) -> Unit, snackbar: SnackbarHostState, onLanguageChanged: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (tab == 0 && !vm.selectionActive) {
                FloatingActionButton(
                    onClick = { navigate(Route.Edit(date = vm.selectedDayForAdd())) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                ) { Icon(Icons.Filled.Add, stringResource(R.string.add)) }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                val items = listOf(
                    R.string.tab_trans to AppIcons.Book,
                    R.string.tab_stats to AppIcons.PieChart,
                    R.string.tab_accounts to AppIcons.Wallet,
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
                            indicatorColor = Color.Transparent,
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
            if (missing.isNotEmpty()) CardSetupBanner(missing.joinToString("، ") { it.name }, onOpen = { navigate(Route.SmsSettings) }, onDismiss = { vm.dismissCardBanner() })
            val m = Modifier.weight(1f)
            when (tab) {
                0 -> TransScreen(vm, navigate, m)
                1 -> StatsScreen(vm, navigate, m)
                2 -> AccountsScreen(vm, navigate, m)
                else -> MoreScreen(vm, navigate, m, onLanguageChanged)
            }
        }
    }
}

/** One-time banner after the 1.5.0 upgrade when some SMS account has no card digits (dismiss = never shown again). */
@Composable
private fun CardSetupBanner(names: String, onOpen: () -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        androidx.compose.foundation.layout.Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(stringResource(R.string.card_banner_text, names), fontSize = 13.sp)
            androidx.compose.foundation.layout.Row {
                androidx.compose.material3.TextButton(onClick = onOpen) { Text(stringResource(R.string.card_banner_open)) }
                androidx.compose.material3.TextButton(onClick = onDismiss) { Text(stringResource(R.string.card_banner_dismiss)) }
            }
        }
    }
}
