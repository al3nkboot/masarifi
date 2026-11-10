package com.midroid.masarifi.ui.screens

import com.midroid.masarifi.ui.theme.Spacing

import com.midroid.masarifi.ui.icon

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.zIndex
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.midroid.masarifi.R
import com.midroid.masarifi.data.Category
import com.midroid.masarifi.data.Defaults
import com.midroid.masarifi.data.TxnType
import com.midroid.masarifi.ui.AppViewModel
import com.midroid.masarifi.ui.components.ConfirmDialog
import com.midroid.masarifi.ui.theme.AppTheme

/** Row key of a top-level category / a subcategory in the list (separate namespaces so ids never clash). */
private fun parentKey(id: Long) = "p$id"
private fun subKey(id: Long) = "s$id"

/**
 * 2.6.2 «الفئات» in Money Manager's customize style: each row has a red delete (−), the emoji and name with its
 * subcategory count, the subcategory names underneath, a pencil and a drag handle (≡). Tapping a category opens its
 * subcategories inline, which have the same controls plus «إضافة فئة فرعية». Dragging the handle reorders siblings
 * (top-level categories among themselves, subcategories inside their parent) and saves the order on release.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(vm: AppViewModel, onBack: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val cats by vm.categories.collectAsStateWithLifecycle()
    var expenseMode by rememberSaveable { mutableStateOf(true) }
    var editing by remember { mutableStateOf<Category?>(null) }
    var deleting by remember { mutableStateOf<Category?>(null) }
    var expanded by rememberSaveable { mutableStateOf(setOf<Long>()) }
    val type = if (expenseMode) TxnType.EXPENSE else TxnType.INCOME
    val c = AppTheme.colors
    val ofType = cats.filter { it.type == type }
    val byId = ofType.associateBy { it.id }
    val dbParents = ofType.filter { it.parentId == null }.map { it.id }
    val dbChildren = ofType.filter { it.parentId != null }.groupBy { it.parentId!! }.mapValues { e -> e.value.map { it.id } }

    // ---- drag state: while dragging, the sibling group being reordered is held locally ----
    val listState = rememberLazyListState()
    val drag = remember { DragState() }
    val parents = drag.resolve(null, dbParents)
    fun childrenOf(pid: Long) = drag.resolve(pid, dbChildren[pid].orEmpty())
    /** Move by the accessibility actions: shown at once, list position kept, then saved. */
    fun moveTo(group: Long?, order: List<Long>) {
        listState.requestScrollToItem(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
        drag.hold(group, order)
        vm.reorderCategories(order)
    }
    val save: (List<Long>) -> Unit = { vm.reorderCategories(it) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.categories)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                actions = {
                    IconButton(onClick = { editing = Category(type = type, nameAr = "", nameEn = "", emoji = "📦", sortOrder = 50) }) {
                        Icon(Icons.Filled.Add, stringResource(R.string.add_category))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().testTag("categories_list")) {
                // The toggle is the list's first item: LazyColumn keeps its first visible item in place, so a fixed header
                // there keeps the list still while the top rows are being reordered.
                item(key = "header") {
                    Row(Modifier.fillMaxWidth().padding(Spacing.sm), horizontalArrangement = Arrangement.Center) {
                        TypeToggle(stringResource(R.string.expense), expenseMode, c.expense) { expenseMode = true; drag.reset() }
                        Spacer(Modifier.width(Spacing.sm))
                        TypeToggle(stringResource(R.string.income), !expenseMode, c.income) { expenseMode = false; drag.reset() }
                    }
                }
                parents.forEachIndexed { pIndex, pid ->
                    val parent = byId[pid] ?: return@forEachIndexed
                    val subs = childrenOf(pid).mapNotNull { byId[it] }
                    val open = pid in expanded
                    item(key = parentKey(pid)) {
                        val key = parentKey(pid)
                        CustomizeRow(
                            cat = parent,
                            arabic = s.arabic,
                            sub = false,
                            count = subs.size,
                            subtitle = if (subs.isNotEmpty()) subs.joinToString(if (s.arabic) "، " else ", ") { it.name(s.arabic) }
                            else parent.name(!s.arabic).takeIf { it != parent.name(s.arabic) },
                            expanded = open,
                            dragging = drag.key == key,
                            dragOffset = if (drag.key == key) drag.offset else 0f,
                            modifier = if (drag.key == key) Modifier.zIndex(1f) else Modifier.animateItem(),
                            onClick = { expanded = if (open) expanded - pid else expanded + pid },
                            onDelete = { deleting = parent },
                            onEdit = { editing = parent },
                            handle = Modifier.reorderHandle(
                                drag = drag, key = key, group = null, siblings = parents, listState = listState,
                                keyOf = ::parentKey, onStart = { expanded = emptySet() }, onCommit = save,
                            ),
                            canMoveUp = pIndex > 0,
                            canMoveDown = pIndex < parents.lastIndex,
                            onMove = { delta -> moveTo(null, parents.moved(pIndex, pIndex + delta)) },
                        )
                        HorizontalDivider(color = c.divider)
                    }
                    if (open) {
                        subs.forEachIndexed { sIndex, sub ->
                            item(key = subKey(sub.id)) {
                                val key = subKey(sub.id)
                                val ids = subs.map { it.id }
                                CustomizeRow(
                                    cat = sub,
                                    arabic = s.arabic,
                                    sub = true,
                                    count = 0,
                                    subtitle = sub.name(!s.arabic).takeIf { it != sub.name(s.arabic) },
                                    expanded = false,
                                    dragging = drag.key == key,
                                    dragOffset = if (drag.key == key) drag.offset else 0f,
                                    modifier = if (drag.key == key) Modifier.zIndex(1f) else Modifier.animateItem(),
                                    onClick = { editing = sub },
                                    onDelete = { deleting = sub },
                                    onEdit = { editing = sub },
                                    handle = Modifier.reorderHandle(
                                        drag = drag, key = key, group = pid, siblings = ids, listState = listState,
                                        keyOf = ::subKey, onStart = {}, onCommit = save,
                                    ),
                                    canMoveUp = sIndex > 0,
                                    canMoveDown = sIndex < subs.lastIndex,
                                    onMove = { delta -> moveTo(pid, ids.moved(sIndex, sIndex + delta)) },
                                )
                                HorizontalDivider(color = c.divider.copy(alpha = 0.5f), modifier = Modifier.padding(start = 56.dp))
                            }
                        }
                        item(key = "add$pid") {
                            AddSubRow {
                                editing = Category(
                                    type = type, nameAr = "", nameEn = "", emoji = parent.emoji.ifBlank { "📦" },
                                    sortOrder = 50, parentId = parent.id,
                                )
                            }
                            HorizontalDivider(color = c.divider)
                        }
                    }
                }
            }
        }
    }
    editing?.let { cat ->
        val parentName = cat.parentId?.let { pid -> cats.firstOrNull { it.id == pid }?.name(s.arabic) }
        CategoryDialog(
            cat = cat,
            parentName = parentName,
            subCount = cats.count { it.parentId == cat.id && cat.id != 0L },
            onDismiss = { editing = null },
            onSave = { saved ->
                vm.saveCategory(saved)
                // A new subcategory opens its parent so the user sees where it went.
                saved.parentId?.let { expanded = expanded + it }
            },
            onDelete = { vm.deleteCategory(it) },
        )
    }
    deleting?.let { cat ->
        val n = cats.count { it.parentId == cat.id }
        ConfirmDialog(
            stringResource(R.string.delete),
            deleteMessage(cat, cat.name(s.arabic), n),
            stringResource(R.string.delete),
            { vm.deleteCategory(cat); deleting = null },
            { deleting = null },
        )
    }
}

@Composable
private fun deleteMessage(cat: Category, name: String, subCount: Int): String = when {
    cat.parentId != null -> stringResource(R.string.confirm_delete_subcategory)
    subCount > 0 -> stringResource(R.string.confirm_delete_parent_category, name, subCount)
    else -> stringResource(R.string.confirm_delete_category)
}

/** Returns a copy with the element at [from] moved to [to]. */
internal fun <T> List<T>.moved(from: Int, to: Int): List<T> {
    if (from !in indices || to !in indices || from == to) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}

/** Drag-and-drop state for one sibling group at a time. */
@Stable
private class DragState {
    var key by mutableStateOf<String?>(null)
    var offset by mutableFloatStateOf(0f)
    /** Parent id of the dragged group (null = top-level), and the live order of that group. */
    var group: Long? = null
    var order by mutableStateOf<List<Long>?>(null)

    /** True while a swap waits for the list to re-anchor its scroll position. */
    var busy = false
    /** Order of the group when the drag started (to detect a real change). */
    var start: List<Long> = emptyList()

    /** A saved order not yet reflected by the database (avoids a one-frame flash of the old order). */
    var pendingGroup: Long? = null
    var pending by mutableStateOf<List<Long>?>(null)

    /** Order to show for group [g]: the live drag order, else a pending save, else the database order [db]. */
    fun resolve(g: Long?, db: List<Long>): List<Long> {
        if (key != null && group == g) order?.let { return it }
        val p = pending
        if (p != null && pendingGroup == g) {
            if (p == db || p.toSet() != db.toSet()) pending = null else return p
        }
        return db
    }
    fun reset() { key = null; offset = 0f; order = null; group = null; start = emptyList() }
    fun hold(g: Long?, o: List<Long>) { pendingGroup = g; pending = o }
    fun finish(commit: (List<Long>) -> Unit) {
        val o = order
        if (o != null && o != start) { hold(group, o); commit(o) }
        reset()
    }
}

@Composable
private fun Modifier.reorderHandle(
    drag: DragState,
    key: String,
    group: Long?,
    siblings: List<Long>,
    listState: LazyListState,
    keyOf: (Long) -> String,
    onStart: () -> Unit,
    onCommit: (List<Long>) -> Unit,
): Modifier {
    val currentSiblings by rememberUpdatedState(siblings)
    val currentStart by rememberUpdatedState(onStart)
    val currentCommit by rememberUpdatedState(onCommit)
    val scope = rememberCoroutineScope()
    return pointerInput(key) {
    detectDragGestures(
        onDragStart = {
            currentStart()
            drag.group = group
            drag.start = currentSiblings
            drag.order = currentSiblings
            drag.offset = 0f
            drag.key = key
        },
        onDrag = { change, amount ->
            change.consume()
            drag.offset += amount.y
            val info = listState.layoutInfo
            val me = info.visibleItemsInfo.firstOrNull { it.key == key } ?: return@detectDragGestures
            val order = drag.order ?: return@detectDragGestures
            val myId = order.firstOrNull { keyOf(it) == key } ?: return@detectDragGestures
            val center = me.offset + drag.offset + me.size / 2f
            if (drag.busy) return@detectDragGestures
            val target = info.visibleItemsInfo.firstOrNull { item ->
                item.key != key && center >= item.offset && center <= item.offset + item.size &&
                    order.any { keyOf(it) == item.key }
            }
            if (target != null) {
                val from = order.indexOf(myId)
                val to = order.indexOfFirst { keyOf(it) == target.key }
                val newOrder = order.moved(from, to)
                // Keep the row under the finger: it will be laid out where the target was.
                val newOffset = if (to > from) me.offset + target.size else target.offset
                // LazyColumn keeps its first visible item (by key) in place. When that item takes part in the swap (only
                // possible when the header is scrolled away), first scroll so the OTHER row is the anchor, then reorder
                // (same trick as Compose's drag-and-drop demo).
                val first = listState.firstVisibleItemIndex
                val anchor = when (first) {
                    target.index -> me.index
                    me.index -> target.index
                    else -> null
                }
                if (anchor != null) {
                    drag.busy = true
                    scope.launch {
                        listState.scrollToItem(anchor, listState.firstVisibleItemScrollOffset)
                        drag.order = newOrder
                        drag.offset -= (newOffset - me.offset)
                        drag.busy = false
                    }
                } else {
                    drag.order = newOrder
                    drag.offset -= (newOffset - me.offset)
                }
            }
            // Auto-scroll when the dragged row reaches the top / bottom edge.
            val top = me.offset + drag.offset
            val bottom = top + me.size
            val edge = me.size / 2f
            val scroll = when {
                amount.y > 0 && bottom > info.viewportEndOffset - edge -> (bottom - (info.viewportEndOffset - edge)).coerceAtMost(40f)
                amount.y < 0 && top < info.viewportStartOffset + edge -> (top - (info.viewportStartOffset + edge)).coerceAtLeast(-40f)
                else -> 0f
            }
            if (scroll != 0f) {
                val consumed = listState.dispatchRawDelta(scroll)
                drag.offset += consumed
            }
        },
        onDragEnd = { drag.finish(currentCommit) },
        onDragCancel = { drag.finish(currentCommit) },
    )
    }
}

@Composable
private fun CustomizeRow(
    cat: Category,
    arabic: Boolean,
    sub: Boolean,
    count: Int,
    subtitle: String?,
    expanded: Boolean,
    dragging: Boolean,
    dragOffset: Float,
    modifier: Modifier,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    handle: Modifier,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMove: (Int) -> Unit,
) {
    val c = AppTheme.colors
    val name = cat.name(arabic)
    val moveUp = stringResource(R.string.move_up)
    val dragCd = stringResource(R.string.drag_to_reorder)
    val moveDown = stringResource(R.string.move_down)
    Row(
        modifier
            .graphicsLayer { translationY = dragOffset; shadowElevation = if (dragging) 8f else 0f }
            .background(if (dragging) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.background)
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = if (sub) 28.dp else Spacing.sm, end = 2.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val delCd = stringResource(R.string.delete_category_cd, name)
        IconButton(onClick = onDelete, modifier = Modifier.semantics { contentDescription = delCd }) {
            Box(Modifier.size(22.dp).background(MinusRed, CircleShape), contentAlignment = Alignment.Center) {
                Box(Modifier.width(11.dp).height(2.5.dp).background(Color.White, RoundedCornerShape(2.dp)))
            }
        }
        Text(cat.icon, style = if (sub) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (count > 0) "$name ($count)" else name,
                style = if (sub) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                fontWeight = if (sub) FontWeight.Normal else FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall, color = c.muted,
                    maxLines = if (expanded) 1 else 2, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        val editCd = stringResource(R.string.edit_category_cd, name)
        IconButton(onClick = onEdit, modifier = Modifier.semantics { contentDescription = editCd }) {
            Icon(Icons.Outlined.Edit, null, tint = c.muted, modifier = Modifier.size(20.dp))
        }
        Box(
            handle
                .size(44.dp)
                .semantics {
                    contentDescription = dragCd
                    customActions = buildList {
                        if (canMoveUp) add(CustomAccessibilityAction(moveUp) { onMove(-1); true })
                        if (canMoveDown) add(CustomAccessibilityAction(moveDown) { onMove(1); true })
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Menu, null, tint = c.muted, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun AddSubRow(onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 40.dp, end = Spacing.lg, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Add, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(stringResource(R.string.add_subcategory), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
    }
}

private val MinusRed = Color(0xFFE5484D)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryDialog(
    cat: Category,
    parentName: String?,
    subCount: Int,
    onDismiss: () -> Unit,
    onSave: (Category) -> Unit,
    onDelete: (Category) -> Unit,
) {
    var ar by remember { mutableStateOf(cat.nameAr) }
    var en by remember { mutableStateOf(cat.nameEn) }
    var emoji by remember { mutableStateOf(cat.emoji) }
    var confirm by remember { mutableStateOf(false) }
    val isSub = cat.parentId != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    when {
                        cat.id == 0L && isSub -> R.string.add_subcategory
                        cat.id == 0L -> R.string.add_category
                        else -> R.string.edit_category
                    },
                ),
            )
        },
        text = {
            Column {
                if (parentName != null) {
                    Text(
                        stringResource(R.string.subcategory_of, parentName),
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppTheme.colors.muted,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(ar, { ar = it }, label = { Text(stringResource(R.string.name_ar)) }, singleLine = true)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(en, { en = it }, label = { Text(stringResource(R.string.name_en)) }, singleLine = true)
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.icon), style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.muted)
                FlowRow {
                    Defaults.emojiChoices.forEach { e ->
                        Box(
                            Modifier.size(40.dp).background(if (e == emoji) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else Color.Transparent, RoundedCornerShape(8.dp))
                                .clickable { emoji = e },
                            contentAlignment = Alignment.Center,
                        ) { Text(e, style = MaterialTheme.typography.titleLarge) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (ar.isBlank() && en.isBlank()) return@TextButton
                onSave(cat.copy(nameAr = ar.trim(), nameEn = en.trim(), emoji = emoji)); onDismiss()
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            Row {
                if (cat.id != 0L) TextButton(onClick = { confirm = true }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
    if (confirm) ConfirmDialog(
        stringResource(R.string.delete),
        deleteMessage(cat, ar.ifBlank { en }, subCount),
        stringResource(R.string.delete),
        { onDelete(cat); onDismiss() },
        { confirm = false },
    )
}
