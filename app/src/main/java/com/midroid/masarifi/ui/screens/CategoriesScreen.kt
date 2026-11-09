package com.midroid.masarifi.ui.screens

import com.midroid.masarifi.ui.theme.Spacing

import com.midroid.masarifi.ui.icon

import androidx.compose.foundation.background
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(vm: AppViewModel, onBack: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val cats by vm.categories.collectAsStateWithLifecycle()
    var expenseMode by rememberSaveable { mutableStateOf(true) }
    var editing by remember { mutableStateOf<Category?>(null) }
    val type = if (expenseMode) TxnType.EXPENSE else TxnType.INCOME
    val c = AppTheme.colors
    val ofType = cats.filter { it.type == type }
    val parents = ofType.filter { it.parentId == null }
    val childrenOf = ofType.filter { it.parentId != null }.groupBy { it.parentId }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.categories)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                actions = {
                    IconButton(onClick = { editing = Category(type = type, nameAr = "", nameEn = "", emoji = "📦", sortOrder = 50) }) {
                        Icon(Icons.Filled.Add, stringResource(R.string.add))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(Spacing.sm), horizontalArrangement = Arrangement.Center) {
                TypeToggle(stringResource(R.string.expense), expenseMode, c.expense) { expenseMode = true }
                Spacer(Modifier.width(Spacing.sm))
                TypeToggle(stringResource(R.string.income), !expenseMode, c.income) { expenseMode = false }
            }
            LazyColumn {
                parents.forEach { parent ->
                    item(key = parent.id) {
                        CategoryRow(
                            cat = parent,
                            arabic = s.arabic,
                            indented = false,
                            onClick = { editing = parent },
                            onAddSub = {
                                editing = Category(
                                    type = type, nameAr = "", nameEn = "", emoji = parent.emoji.ifBlank { "📦" },
                                    sortOrder = 50, parentId = parent.id,
                                )
                            },
                        )
                        HorizontalDivider(color = c.divider)
                    }
                    items(childrenOf[parent.id].orEmpty(), key = { it.id }) { sub ->
                        CategoryRow(
                            cat = sub,
                            arabic = s.arabic,
                            indented = true,
                            onClick = { editing = sub },
                            onAddSub = null,
                        )
                        HorizontalDivider(color = c.divider)
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
            onDismiss = { editing = null },
            onSave = { vm.saveCategory(it) },
            onDelete = { vm.deleteCategory(it) },
        )
    }
}

@Composable
private fun CategoryRow(
    cat: Category,
    arabic: Boolean,
    indented: Boolean,
    onClick: () -> Unit,
    onAddSub: (() -> Unit)?,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(start = if (indented) 36.dp else Spacing.lg, end = Spacing.sm, top = Spacing.md, bottom = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(cat.icon, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(cat.name(arabic), style = MaterialTheme.typography.bodyLarge)
            Text(cat.name(!arabic), style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.muted)
        }
        if (onAddSub != null) {
            IconButton(onClick = onAddSub) {
                Icon(Icons.Filled.Add, stringResource(R.string.add_subcategory))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryDialog(
    cat: Category,
    parentName: String?,
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
        stringResource(if (isSub) R.string.confirm_delete_category else R.string.confirm_delete_category),
        stringResource(R.string.delete),
        { onDelete(cat); onDismiss() },
        { confirm = false },
    )
}
