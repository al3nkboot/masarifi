package com.midroid.masarifi.ui.components

import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import com.midroid.masarifi.R
import com.midroid.masarifi.data.AppSettings
import com.midroid.masarifi.data.Txn
import com.midroid.masarifi.data.TxnType
import com.midroid.masarifi.ui.Fmt
import com.midroid.masarifi.ui.theme.AppTheme
import com.midroid.masarifi.ui.theme.Amount
import com.midroid.masarifi.ui.theme.Spacing
import com.midroid.masarifi.util.Money
import java.time.YearMonth

@Composable
fun MonthSwitcher(label: String, onPrev: () -> Unit, onNext: () -> Unit, modifier: Modifier = Modifier, onLabelClick: (() -> Unit)? = null) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrev) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.previous)) }
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.widthIn(min = 110.dp).let { if (onLabelClick != null) it.clickable { onLabelClick() } else it },
            textAlign = TextAlign.Center,
        )
        IconButton(onClick = onNext) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.next)) }
    }
}

@Composable
fun SummaryRow(income: Long, expense: Long, s: AppSettings) {
    val c = AppTheme.colors
    SummaryCard(
        listOf(
            Triple(stringResource(R.string.income), Fmt.money(income, s, false), c.income),
            Triple(stringResource(R.string.expenses), Fmt.money(expense, s, false), c.expense),
            Triple(stringResource(R.string.total), Fmt.money(income - expense, s, false), MaterialTheme.colorScheme.onSurface),
        ),
        footer = if (s.arabic) s.currencyInfo.nameAr else s.currencyInfo.nameEn,
    )
}

/** Tonal card with 2–3 labelled figures; used by Home, Search, Accounts, Bills. */
@Composable
fun SummaryCard(cells: List<Triple<String, String, Color>>, footer: String? = null, modifier: Modifier = Modifier) {
    androidx.compose.material3.Card(
        modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        shape = MaterialTheme.shapes.large,
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = Spacing.md, bottom = if (footer == null) Spacing.md else Spacing.xs, start = Spacing.sm, end = Spacing.sm)) {
            cells.forEach { (title, value, color) -> SummaryCell(title, value, color, Modifier.weight(1f)) }
        }
        if (footer != null) Text(
            footer, style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.muted,
            modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.sm), textAlign = TextAlign.Center,
        )
    }
}

/** Compact single-line info card with an optional action and close button. */
@Composable
fun InfoStrip(
    text: String, icon: ImageVector?, tint: Color, container: Color,
    onClick: (() -> Unit)? = null, action: String? = null, onDismiss: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.xs)
            .background(container, MaterialTheme.shapes.medium)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(start = Spacing.md, end = Spacing.xs).height(44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(Spacing.sm)) }
        Text(text, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (action != null && onClick != null) TextButton(onClick = onClick) { Text(action) }
        if (onDismiss != null) IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
            Icon(androidx.compose.material.icons.Icons.Filled.Close, stringResource(R.string.card_banner_dismiss), Modifier.size(18.dp))
        }
    }
}

@Composable
private fun SummaryCell(title: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = AppTheme.colors.muted)
        Spacer(Modifier.height(Spacing.xxs))
        Text(value, color = color, style = Amount, fontSize = 17.sp, maxLines = 1)
    }
}

@Composable
fun AmountText(t: Txn, s: AppSettings, modifier: Modifier = Modifier, withSymbol: Boolean = true) {
    val c = AppTheme.colors
    val color = when (t.type) { TxnType.INCOME -> c.income; TxnType.EXPENSE -> c.expense; else -> c.transfer }
    Text(Fmt.money(t.amount, s, withSymbol), color = color, style = Amount, modifier = modifier, maxLines = 1, fontSize = 15.sp)
}

/** Tonal circle holding the category emoji (or a fallback glyph). */
@Composable
fun CategoryBadge(emoji: String, tint: Color) {
    Box(
        Modifier.size(40.dp).background(tint.copy(alpha = 0.16f), androidx.compose.foundation.shape.CircleShape),
        contentAlignment = Alignment.Center,
    ) { Text(emoji, style = MaterialTheme.typography.titleMedium) }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TxnRow(
    t: Txn,
    categoryLabel: String,
    accountLabel: String,
    s: AppSettings,
    onClick: () -> Unit,
    showDate: Boolean = false,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    showCurrency: Boolean = true,
    runningBalance: Long? = null,
) {
    val c = AppTheme.colors
    val first = categoryLabel.substringBefore(' ')
    val hasEmoji = categoryLabel.contains(' ') && first.isNotEmpty() && !first.first().isLetterOrDigit()
    val emoji = if (hasEmoji) first else if (t.type == TxnType.TRANSFER) "⇄" else "•"
    val catName = if (hasEmoji) categoryLabel.substringAfter(' ') else categoryLabel
    val typeColor = when (t.type) { TxnType.INCOME -> c.income; TxnType.EXPENSE -> c.expense; else -> c.transfer }
    androidx.compose.material3.ListItem(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = androidx.compose.material3.ListItemDefaults.colors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) else Color.Transparent,
        ),
        leadingContent = {
            if (selectionMode) {
                Icon(
                    if (selected) Icons.Filled.CheckCircle else AppIcons.CircleOutline,
                    contentDescription = null,
                    tint = if (selected) MaterialTheme.colorScheme.primary else c.muted,
                    modifier = Modifier.size(40.dp).padding(Spacing.sm),
                )
            } else CategoryBadge(emoji, typeColor)
        },
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    t.note.ifBlank { catName }, style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                )
                if (t.isAuto || t.smsLinked) { Spacer(Modifier.width(Spacing.xs + Spacing.xxs)); Chip("SMS") }
                if (t.smsLinked) {
                    Spacer(Modifier.width(Spacing.xs))
                    Icon(AppIcons.Link, stringResource(R.string.dup_linked_badge), Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                }
                if (t.recurringId != null) { Spacer(Modifier.width(Spacing.xs)); Icon(AppIcons.Repeat, null, Modifier.size(14.dp), tint = c.muted) }
            }
        },
        supportingContent = {
            Column {
                if (t.possibleDupOf != null) Chip(stringResource(R.string.dup_possible_badge), bg = c.warning.copy(alpha = 0.25f))
                Text(
                    (if (showDate) Fmt.dateLabel(t.dateTime, s.arabic) + " • " else "") +
                        (if (t.note.isNotBlank() && t.type != TxnType.TRANSFER) "$catName • " else "") + accountLabel,
                    style = MaterialTheme.typography.bodySmall, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                AmountText(t, s, withSymbol = showCurrency)
                if (runningBalance != null) {
                    Text(
                        "\u200E" + Fmt.money(runningBalance, s, false),
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTheme.colors.muted,
                    )
                }
            }
        },
    )
}

@Composable
fun Chip(text: String, bg: Color = AppTheme.colors.chip, fg: Color = MaterialTheme.colorScheme.onSurface) {
    Box(Modifier.background(bg, RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 1.dp)) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = fg, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.lg, bottom = Spacing.xs),
    )
}

@Composable
fun SettingRow(icon: ImageVector?, title: String, subtitle: String? = null, onClick: (() -> Unit)? = null, trailing: @Composable (() -> Unit)? = null) {
    androidx.compose.material3.ListItem(
        modifier = Modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it },
        colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = icon?.let { ic -> {
            Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f), androidx.compose.foundation.shape.CircleShape), contentAlignment = Alignment.Center) {
                Icon(ic, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
        } },
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
        supportingContent = subtitle?.let { st -> { Text(st, style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.muted) } },
        trailingContent = trailing,
    )
}

@Composable
fun SwitchRow(icon: ImageVector?, title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    SettingRow(icon, title, subtitle, onClick = { onChange(!checked) }) {
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirm, color = MaterialTheme.colorScheme.primary) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun AmountInputDialog(title: String, initial: Long, s: AppSettings, onDismiss: () -> Unit, onSave: (Long) -> Unit, allowClear: Boolean = true) {
    var text by remember { mutableStateOf(Money.toInput(initial, s.currencyInfo)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                suffix = { Text(s.currencyInfo.symbol(s.arabic)) },
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(Money.parse(text) ?: 0); onDismiss() }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            Row {
                if (allowClear && initial > 0) TextButton(onClick = { onSave(0); onDismiss() }) { Text(stringResource(R.string.remove)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
        Text(text, color = AppTheme.colors.muted, textAlign = TextAlign.Center)
    }
}

@Composable
fun ProgressBar(ratio: Double, modifier: Modifier = Modifier, goal: Boolean = false) {
    val color = if (goal) { if (ratio >= 1.0) AppTheme.colors.positive else com.midroid.masarifi.ui.theme.Brand.Blue } else when {
        ratio >= 1.0 -> com.midroid.masarifi.ui.theme.Brand.Coral
        ratio >= 0.8 -> com.midroid.masarifi.ui.theme.Brand.Orange
        else -> com.midroid.masarifi.ui.theme.Brand.Blue
    }
    Box(modifier.fillMaxWidth().height(8.dp).background(AppTheme.colors.chip, RoundedCornerShape(4.dp))) {
        Box(
            Modifier.fillMaxWidth(ratio.coerceIn(0.0, 1.0).toFloat()).height(8.dp).background(color, RoundedCornerShape(4.dp)),
        )
    }
}

fun YearMonth.label(ar: Boolean) = Fmt.monthTitle(this, ar)
