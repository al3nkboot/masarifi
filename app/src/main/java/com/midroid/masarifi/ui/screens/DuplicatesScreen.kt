package com.midroid.masarifi.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.midroid.masarifi.R
import com.midroid.masarifi.data.dup.DupReason
import com.midroid.masarifi.ui.AppViewModel
import com.midroid.masarifi.ui.Route
import com.midroid.masarifi.ui.components.EmptyState
import com.midroid.masarifi.ui.components.TxnRow
import com.midroid.masarifi.ui.theme.AppTheme
import java.util.Locale

/** Review groups of possible duplicates: merge, keep all, or delete one entry. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuplicatesScreen(vm: AppViewModel, navigate: (Route) -> Unit, onBack: () -> Unit, snackbar: SnackbarHostState) {
    val groups by vm.dupGroups.collectAsStateWithLifecycle()
    val s by vm.settings.collectAsStateWithLifecycle()
    val labels = rememberLabels(vm)
    val c = AppTheme.colors
    val merged = stringResource(R.string.dup_merged)
    val kept = stringResource(R.string.dup_kept)
    val deletedFmt = stringResource(R.string.deleted_n)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.dup_review)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        if (groups.isEmpty()) {
            EmptyState(stringResource(R.string.dup_none), Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(Modifier.padding(padding).fillMaxSize().padding(horizontal = 10.dp)) {
            item {
                Text(
                    stringResource(R.string.dup_window) + ": " + stringResource(R.string.dup_window_value, s.dupWindowHours.toString()),
                    fontSize = 12.sp, color = c.muted, modifier = Modifier.padding(6.dp),
                )
            }
            items(groups, key = { it.key }) { g ->
                Card(Modifier.fillMaxWidth().padding(vertical = 6.dp), colors = CardDefaults.cardColors(containerColor = c.card)) {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        Text(
                            stringResource(if (g.reason == DupReason.SMS_MATCH) R.string.dup_reason_sms else R.string.dup_reason_exact),
                            style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                        g.txns.forEach { t ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    TxnRow(t, labels.category(t), labels.account(t), s, onClick = { navigate(Route.Edit(txnId = t.id)) }, showDate = true)
                                }
                                IconButton(onClick = {
                                    vm.deleteTxns(listOf(t.id)) { n -> String.format(Locale.US, deletedFmt, n.toString()) }
                                }) { Icon(Icons.Filled.Delete, stringResource(R.string.delete), tint = c.muted) }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { vm.mergeGroup(g.ids, merged) }) { Text(stringResource(R.string.dup_merge)) }
                            OutlinedButton(onClick = { vm.keepBoth(g.ids, kept) }) { Text(stringResource(R.string.dup_keep_both)) }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(40.dp).width(1.dp)) }
        }
    }
}
