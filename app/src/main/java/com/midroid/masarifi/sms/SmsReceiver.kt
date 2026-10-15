package com.midroid.masarifi.sms

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.midroid.masarifi.app
import kotlinx.coroutines.launch

/** Receives incoming SMS and auto-records bank transactions when the feature is enabled. */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val app = context.app
        if (!app.settings.current.smsEnabled) return
        val messages = runCatching { Telephony.Sms.Intents.getMessagesFromIntent(intent) }.getOrNull() ?: return
        if (messages.isEmpty()) return
        // Multipart SMS: join parts per sender
        val bySender = messages.filterNotNull().groupBy { it.displayOriginatingAddress ?: it.originatingAddress ?: "" }
        val pending = goAsync()
        app.appScope.launch {
            try {
                for ((sender, parts) in bySender) {
                    val body = parts.joinToString("") { it.displayMessageBody ?: it.messageBody ?: "" }
                    val time = parts.first().timestampMillis.takeIf { it > 0 } ?: System.currentTimeMillis()
                    SmsImporter.importOne(context, sender, body, time, notify = true)
                }
            } finally {
                pending.finish()
            }
        }
    }
}

object SmsImporter {
    fun hasPermission(context: Context, permission: String = Manifest.permission.READ_SMS) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    suspend fun importOne(context: Context, sender: String, body: String, time: Long, notify: Boolean): Boolean =
        importOneResult(context, sender, body, time, notify) != null

    suspend fun importOneResult(context: Context, sender: String, body: String, time: Long, notify: Boolean): com.midroid.masarifi.data.Repository.SmsImportResult? {
        val app = context.app
        val result = runCatching { app.repository.importSms(sender, body, time) }.getOrNull() ?: return null
        val s = app.settings.current
        if (notify && s.smsNotify) {
            val ar = s.arabic
            val res = com.midroid.masarifi.work.LocalizedRes.of(context, ar)
            val amount = com.midroid.masarifi.util.Money.format(result.txn.amount, s.currencyInfo, ar)
            val isExpense = result.txn.type == com.midroid.masarifi.data.TxnType.EXPENSE
            val text = listOf(amount, result.txn.note.ifBlank { null }, result.categoryName.ifBlank { null }, result.accountName)
                .filterNotNull().joinToString(" • ")
            if (result.linked && result.linkId != null) {
                val notifId = (result.linkId % 100000).toInt() + 200000
                com.midroid.masarifi.work.Notifier.show(
                    context, com.midroid.masarifi.work.Notifier.CH_AUTO, notifId, res.getString(com.midroid.masarifi.R.string.dup_linked_title),
                    text + "\n" + res.getString(com.midroid.masarifi.R.string.dup_linked_text), result.txn.id,
                    com.midroid.masarifi.work.Notifier.Action(
                        res.getString(com.midroid.masarifi.R.string.dup_undo_split), DupActionReceiver.splitIntent(context, result.linkId, notifId),
                    ),
                )
            } else {
                val title = res.getString(
                    when (result.txn.type) {
                        com.midroid.masarifi.data.TxnType.TRANSFER -> com.midroid.masarifi.R.string.auto_transfer_title
                        com.midroid.masarifi.data.TxnType.EXPENSE -> com.midroid.masarifi.R.string.auto_expense_title
                        else -> com.midroid.masarifi.R.string.auto_income_title
                    },
                )
                val extra = if (result.flagged) "\n" + res.getString(com.midroid.masarifi.R.string.dup_possible_notif) else ""
                com.midroid.masarifi.work.Notifier.show(
                    context, com.midroid.masarifi.work.Notifier.CH_AUTO, (result.txn.id % 100000).toInt() + 10000, title,
                    text + extra + "\n" + res.getString(com.midroid.masarifi.R.string.auto_tap_to_edit), result.txn.id,
                )
            }
        }
        return result
    }

    data class ScanResult(val scanned: Int, val imported: Int, val linked: Int = 0)

    /** One-time scan of the SMS inbox (last [days] days). */
    suspend fun scanInbox(context: Context, days: Int = 90): ScanResult {
        if (!hasPermission(context)) return ScanResult(0, 0)
        val since = System.currentTimeMillis() - days * 86_400_000L
        var scanned = 0
        var imported = 0
        val cursor = context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            "${Telephony.Sms.DATE} >= ?", arrayOf(since.toString()),
            "${Telephony.Sms.DATE} ASC",
        ) ?: return ScanResult(0, 0)
        cursor.use { c ->
            val iA = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val iB = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val iD = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
            while (c.moveToNext()) {
                val sender = c.getString(iA) ?: continue
                val body = c.getString(iB) ?: continue
                val date = c.getLong(iD)
                scanned++
                if (importOne(context, sender, body, date, notify = false)) imported++
            }
        }
        return ScanResult(scanned, imported)
    }

    /**
     * «إعادة فحص الرسائل»: re-reads the last [days] days of the inbox, only messages from known bank/wallet senders
     * (built-in list + the user's extra senders), and imports those that were missed before (e.g. formats the parser did
     * not understand yet). The normal de-duplication applies (same SMS hash, same bank Txn Id, SMS ↔ manual linking),
     * so already-recorded transactions are never doubled.
     */
    suspend fun rescanInbox(context: Context, days: Int = 90): ScanResult {
        if (!hasPermission(context)) return ScanResult(0, 0)
        val parser = context.app.repository.parser()
        val extra = context.app.settings.current.extraSenderList.map { SmsParser.normalizeSender(it) }.toSet()
        val since = System.currentTimeMillis() - days * 86_400_000L
        var scanned = 0
        var imported = 0
        var linked = 0
        val cursor = context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            "${Telephony.Sms.DATE} >= ?", arrayOf(since.toString()),
            "${Telephony.Sms.DATE} ASC",
        ) ?: return ScanResult(0, 0)
        cursor.use { c ->
            val iA = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val iB = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val iD = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
            while (c.moveToNext()) {
                val sender = c.getString(iA) ?: continue
                val body = c.getString(iB) ?: continue
                if (parser.identifyBank(sender) == null && SmsParser.normalizeSender(sender) !in extra) continue
                scanned++
                val r = importOneResult(context, sender, body, c.getLong(iD), notify = false) ?: continue
                if (r.linked) linked++ else imported++
            }
        }
        return ScanResult(scanned, imported, linked)
    }
}
