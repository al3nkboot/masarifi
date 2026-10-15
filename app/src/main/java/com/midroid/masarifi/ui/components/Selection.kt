package com.midroid.masarifi.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.midroid.masarifi.R
import com.midroid.masarifi.ui.AppViewModel
import java.util.Locale

/** Multi-select state for transaction lists (long-press to start, tap to toggle). */
@Stable
class TxnSelection {
    var ids by mutableStateOf<Set<Long>>(emptySet())
        private set
    var confirmDelete by mutableStateOf(false)
    val active get() = ids.isNotEmpty()
    fun isSelected(id: Long) = id in ids
    fun toggle(id: Long) { ids = if (id in ids) ids - id else ids + id }
    fun start(id: Long) { ids = ids + id }
    /** Selects all of [list]; if all are already selected, unselects them. */
    fun toggleAll(list: Collection<Long>) { ids = if (list.isNotEmpty() && ids.containsAll(list)) ids - list.toSet() else ids + list }
    fun selectAll(list: Collection<Long>) { ids = ids + list }
    fun clear() { ids = emptySet(); confirmDelete = false }
    /** Drops ids that are no longer present (e.g. deleted elsewhere). */
    fun retain(existing: Set<Long>) { if (!existing.containsAll(ids)) ids = ids.intersect(existing) }
}

@Composable
fun rememberTxnSelection() = remember { TxnSelection() }

/** Click handler for a row: toggles in selection mode, otherwise opens. */
fun TxnSelection.click(id: Long, open: () -> Unit) { if (active) toggle(id) else open() }

/** Top bar shown in selection mode: close, "N selected", select all, delete. */
@Composable
fun SelectionTopBar(sel: TxnSelection, visibleIds: () -> Collection<Long>, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = sel::clear) { Icon(Icons.Filled.Close, stringResource(R.string.close_selection)) }
        Text(stringResource(R.string.selected_n, sel.ids.size.toString()), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.weight(1f))
        TextButton(onClick = { sel.selectAll(visibleIds()) }) { Text(stringResource(R.string.select_all)) }
        IconButton(onClick = { sel.confirmDelete = true }) { Icon(Icons.Filled.Delete, stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error) }
    }
}

/** Back exits selection mode; shows the delete confirmation and performs delete-with-undo. */
@Composable
fun SelectionEffects(sel: TxnSelection, vm: AppViewModel) {
    BackHandler(enabled = sel.active) { sel.clear() }
    val deletedFmt = stringResource(R.string.deleted_n)
    if (sel.confirmDelete && sel.active) {
        val n = sel.ids.size
        AlertDialog(
            onDismissRequest = { sel.confirmDelete = false },
            title = { Text(stringResource(R.string.confirm_delete_n, n.toString())) },
            confirmButton = {
                TextButton(onClick = {
                    val ids = sel.ids
                    sel.clear()
                    vm.deleteTxns(ids) { count -> String.format(Locale.US, deletedFmt, count.toString()) }
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { sel.confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
