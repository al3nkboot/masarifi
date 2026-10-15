package com.midroid.masarifi.ui.components

import androidx.compose.material.icons.filled.CheckCircle
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
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        SummaryCell(stringResource(R.string.income), Fmt.money(income, s, false), c.income, Modifier.weight(1f))
        SummaryCell(stringResource(R.string.expenses), Fmt.money(expense, s, false), c.expense, Modifier.weight(1f))
        SummaryCell(stringResource(R.string.total), Fmt.money(income - expense, s, false), MaterialTheme.colorScheme.onBackground, Modifier.weight(1f))
    }
    HorizontalDivider(color = c.divider)
}

@Composable
private fun SummaryCell(title: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = AppTheme.colors.muted)
        Text(value, color = color, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1)
    }
}

@Composable
fun AmountText(t: Txn, s: AppSettings, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    val color = when (t.type) { TxnType.INCOME -> c.income; TxnType.EXPENSE -> c.expense; else -> c.transfer }
    Text(Fmt.money(t.amount, s), color = color, fontWeight = FontWeight.Medium, modifier = modifier, maxLines = 1, fontSize = 14.sp)
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
) {
    val c = AppTheme.colors
    Row(
        Modifier.fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectionMode) {
            Icon(
                if (selected) Icons.Filled.CheckCircle else AppIcons.CircleOutline,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else c.muted,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(
            categoryLabel, color = c.muted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(if (selectionMode) 80.dp else 96.dp),
        )
        Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    t.note.ifBlank { categoryLabel.substringAfter(' ') },
                    fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                )
                if (t.isAuto || t.smsLinked) { Spacer(Modifier.width(6.dp)); Chip("SMS") }
                if (t.smsLinked) {
                    Spacer(Modifier.width(4.dp))
                    Icon(AppIcons.Link, stringResource(R.string.dup_linked_badge), Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                }
                if (t.recurringId != null) { Spacer(Modifier.width(4.dp)); Icon(AppIcons.Repeat, null, Modifier.size(14.dp), tint = c.muted) }
            }
            if (t.possibleDupOf != null) {
                Chip(stringResource(R.string.dup_possible_badge), bg = com.midroid.masarifi.ui.theme.Brand.Orange.copy(alpha = 0.25f))
            }
            Text(
                (if (showDate) Fmt.dateLabel(t.dateTime, s.arabic) + " • " else "") + accountLabel,
                color = c.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        AmountText(t, s)
    }
}

@Composable
fun Chip(text: String, bg: Color = AppTheme.colors.chip, fg: Color = MaterialTheme.colorScheme.onSurface) {
    Box(Modifier.background(bg, RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 1.dp)) {
        Text(text, fontSize = 10.sp, color = fg, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp),
    )
}

@Composable
fun SettingRow(icon: ImageVector?, title: String, subtitle: String? = null, onClick: (() -> Unit)? = null, trailing: @Composable (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it }.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp)
            if (subtitle != null) Text(subtitle, fontSize = 13.sp, color = AppTheme.colors.muted)
        }
        trailing?.invoke()
    }
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
fun ProgressBar(ratio: Double, modifier: Modifier = Modifier) {
    val color = when {
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
