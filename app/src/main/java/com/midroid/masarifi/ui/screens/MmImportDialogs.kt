package com.midroid.masarifi.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.midroid.masarifi.R
import com.midroid.masarifi.data.TxnType
import com.midroid.masarifi.data.mm.MmSkipReason
import com.midroid.masarifi.data.mm.MmSkipped
import com.midroid.masarifi.ui.AppViewModel
import com.midroid.masarifi.ui.MmUi
import com.midroid.masarifi.ui.theme.AppTheme
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Isolates left-to-right text (dates, numbers) so it isn't reordered inside Arabic sentences. */
fun ltr(s: String) = "\u2066$s\u2069"

@Composable
fun BusyDialog(text: String) {
    AlertDialog(
        onDismissRequest = {},
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                Spacer(Modifier.width(16.dp))
                Text(text)
            }
        },
        confirmButton = {},
    )
}

@Composable
private fun reasonText(s: MmSkipped): String = stringResource(
    R.string.mm_row_reason, s.row,
    stringResource(
        when (s.reason) {
            MmSkipReason.BAD_DATE -> R.string.mm_reason_bad_date
            MmSkipReason.BAD_AMOUNT -> R.string.mm_reason_bad_amount
            MmSkipReason.UNKNOWN_TYPE -> R.string.mm_reason_unknown_type
            MmSkipReason.NO_ACCOUNT -> R.string.mm_reason_no_account
            MmSkipReason.TRANSFER_NO_TARGET -> R.string.mm_reason_transfer_no_target
        },
    ) + if (s.detail.isNotBlank()) " (${s.detail.take(30)})" else "",
)

@Composable
private fun SkippedList(skipped: List<MmSkipped>) {
    if (skipped.isEmpty()) return
    Text(stringResource(R.string.mm_skipped_rows, skipped.size), color = MaterialTheme.colorScheme.error)
    skipped.take(20).forEach { Text("• " + reasonText(it), fontSize = 12.sp, color = AppTheme.colors.muted) }
    if (skipped.size > 20) Text("…", fontSize = 12.sp, color = AppTheme.colors.muted)
}

/** Preview → import → result dialogs for "Import from Money Manager". */
@Composable
fun MmImportDialogs(vm: AppViewModel) {
    val ui by vm.mmUi.collectAsStateWithLifecycle()
    val s by vm.settings.collectAsStateWithLifecycle()
    val df = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)
    when (val u = ui) {
        null -> {}
        MmUi.Reading -> BusyDialog(stringResource(R.string.mm_reading))
        MmUi.Importing -> BusyDialog(stringResource(R.string.mm_importing))
        is MmUi.Error -> AlertDialog(
            onDismissRequest = vm::mmDismiss,
            title = { Text(stringResource(R.string.mm_import)) },
            text = {
                Text(stringResource(when (u.code) { "legacy_xls" -> R.string.mm_error_legacy_xls; "no_header" -> R.string.mm_error_no_header; else -> R.string.mm_error_generic }))
            },
            confirmButton = { TextButton(onClick = vm::mmDismiss) { Text(stringResource(R.string.ok)) } },
        )
        is MmUi.Preview -> {
            val p = u.plan
            AlertDialog(
                onDismissRequest = vm::mmDismiss,
                title = { Text(stringResource(R.string.mm_preview_title)) },
                text = {
                    Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.mm_rows, p.parse.dataRows))
                        Text(stringResource(R.string.mm_counts, p.income, p.expense, p.transfer), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        if (p.from != null && p.to != null) Text(stringResource(R.string.mm_range, ltr(p.from.format(df)), ltr(p.to.format(df))))
                        if (p.duplicates > 0) Text(stringResource(R.string.mm_duplicates, p.duplicates))
                        if (p.newAccounts.isNotEmpty()) Text(stringResource(R.string.mm_new_accounts, p.newAccounts.size, p.newAccounts.joinToString("، ")))
                        if (p.newCategories.isNotEmpty()) {
                            Text(
                                stringResource(
                                    R.string.mm_new_categories, p.newCategories.size,
                                    p.newCategories.joinToString("، ") { (type, name) -> name + if (type == TxnType.INCOME) " (+)" else "" },
                                ),
                            )
                        }
                        val cur = p.parse.mainCurrency
                        if (cur != null && cur != s.currency) Text(stringResource(R.string.mm_currency_warning, cur, s.currency), color = MaterialTheme.colorScheme.error)
                        SkippedList(p.parse.skipped)
                        if (p.toImport == 0) Text(stringResource(R.string.mm_nothing))
                    }
                },
                confirmButton = {
                    TextButton(onClick = vm::mmConfirm, enabled = p.toImport > 0) { Text(stringResource(R.string.mm_import_btn, p.toImport)) }
                },
                dismissButton = { TextButton(onClick = vm::mmDismiss) { Text(stringResource(R.string.cancel)) } },
            )
        }
        is MmUi.Done -> {
            val r = u.result
            AlertDialog(
                onDismissRequest = vm::mmDismiss,
                title = { Text(stringResource(R.string.mm_done_title)) },
                text = {
                    Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.mm_done_body, r.imported, r.income, r.expense, r.transfer, r.duplicates, r.accountsCreated, r.categoriesCreated))
                        SkippedList(r.skipped)
                    }
                },
                confirmButton = { TextButton(onClick = vm::mmDismiss) { Text(stringResource(R.string.ok)) } },
            )
        }
    }
}
