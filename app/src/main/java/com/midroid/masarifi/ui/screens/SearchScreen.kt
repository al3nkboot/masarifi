package com.midroid.masarifi.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.midroid.masarifi.R
import com.midroid.masarifi.data.TxnType
import com.midroid.masarifi.ui.AppViewModel
import com.midroid.masarifi.ui.Route
import com.midroid.masarifi.ui.components.EmptyState
import com.midroid.masarifi.ui.components.SummaryRow
import com.midroid.masarifi.ui.components.TxnRow
import com.midroid.masarifi.util.Money

/** Search screen (filter == null) or a filtered transaction list (category / account / note drill-down). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(vm: AppViewModel, filter: Route.TxnList?, navigate: (Route) -> Unit, onBack: () -> Unit, snackbar: androidx.compose.material3.SnackbarHostState? = null) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val all by vm.txns.collectAsStateWithLifecycle()
    val labels = rememberLabels(vm)
    var query by rememberSaveable { mutableStateOf("") }

    val base = remember(all, filter) {
        if (filter == null) all else all.filter { t ->
            (filter.categoryId == null || (if (filter.categoryId == -1L) t.categoryId == null else t.categoryId == filter.categoryId)) &&
                (filter.accountId == null || t.accountId == filter.accountId || t.toAccountId == filter.accountId) &&
                (filter.from == null || t.dateTime >= filter.from) && (filter.to == null || t.dateTime < filter.to) &&
                (filter.note == null || t.note.trim() == filter.note) && (filter.type == null || t.type == filter.type)
        }
    }
    val results = remember(base, query, labels) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) base else {
            val amount = Money.parse(q)
            base.filter { t ->
                t.note.lowercase().contains(q) || t.description.lowercase().contains(q) || (t.merchant?.lowercase()?.contains(q) == true) ||
                    labels.category(t).lowercase().contains(q) || labels.account(t).lowercase().contains(q) ||
                    (amount != null && amount > 0 && t.amount == amount)
            }
        }
    }

    val sel = com.midroid.masarifi.ui.components.rememberTxnSelection()
    androidx.compose.runtime.LaunchedEffect(all) { sel.retain(all.mapTo(HashSet()) { it.id }) }
    com.midroid.masarifi.ui.components.SelectionEffects(sel, vm)

    Scaffold(
        snackbarHost = { if (snackbar != null) androidx.compose.material3.SnackbarHost(snackbar) },
        topBar = {
            if (sel.active) com.midroid.masarifi.ui.components.SelectionTopBar(sel, visibleIds = { results.map { it.id } }, Modifier.statusBarsPadding())
            else TopAppBar(
                title = { Text(filter?.title ?: stringResource(R.string.search)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                placeholder = { Text(stringResource(R.string.search_hint)) }, singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, null) },
            )
            SummaryRow(results.filter { it.type == TxnType.INCOME }.sumOf { it.amount }, results.filter { it.type == TxnType.EXPENSE }.sumOf { it.amount }, s)
            if (results.isEmpty()) EmptyState(stringResource(R.string.no_results))
            LazyColumn(Modifier.fillMaxSize()) {
                items(results, key = { it.id }) { t ->
                    SelectableTxnRow(t, labels, s, navigate, sel, showDate = true)
                    HorizontalDivider(color = com.midroid.masarifi.ui.theme.AppTheme.colors.divider)
                }
            }
        }
    }
}
