package com.midroid.masarifi.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.midroid.masarifi.R
import com.midroid.masarifi.data.Recurring
import com.midroid.masarifi.data.TxnType
import com.midroid.masarifi.ui.AppViewModel
import com.midroid.masarifi.ui.Fmt
import com.midroid.masarifi.ui.components.ConfirmDialog
import com.midroid.masarifi.ui.components.EmptyState
import com.midroid.masarifi.ui.theme.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringScreen(vm: AppViewModel, onBack: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val list by vm.recurring.collectAsStateWithLifecycle()
    val cats by vm.categories.collectAsStateWithLifecycle()
    val accs by vm.accounts.collectAsStateWithLifecycle()
    val c = AppTheme.colors
    var deleting by remember { mutableStateOf<Recurring?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.repeating)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            if (list.isEmpty()) item { EmptyState(stringResource(R.string.no_repeating)) }
            items(list, key = { it.id }) { r ->
                val cat = cats.firstOrNull { it.id == r.categoryId }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(r.note.ifBlank { cat?.let { it.emoji + " " + it.name(s.arabic) } ?: stringResource(R.string.transfer) }, fontSize = 16.sp)
                        Text(
                            repeatLabel(r.frequency) + " • " + (accs.firstOrNull { it.id == r.accountId }?.name ?: "") + " • " +
                                stringResource(R.string.next_on, Fmt.dateLabel(r.nextDate, s.arabic)),
                            fontSize = 12.sp, color = c.muted,
                        )
                        Text(
                            Fmt.money(r.amount, s), fontSize = 14.sp,
                            color = when (r.type) { TxnType.INCOME -> c.income; TxnType.EXPENSE -> c.expense; else -> c.transfer },
                        )
                    }
                    Switch(checked = r.active, onCheckedChange = { vm.setRecurringActive(r, it) })
                    IconButton(onClick = { deleting = r }) { Icon(Icons.Filled.Delete, stringResource(R.string.delete), tint = c.muted) }
                }
                HorizontalDivider(color = c.divider)
            }
        }
    }
    deleting?.let { r ->
        ConfirmDialog(stringResource(R.string.delete), stringResource(R.string.confirm_delete_repeat), stringResource(R.string.delete), { vm.deleteRecurring(r) }, { deleting = null })
    }
}
