package com.midroid.masarifi.sms

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.midroid.masarifi.app
import com.midroid.masarifi.work.Notifier
import kotlinx.coroutines.launch

/** Handles the "undo" action of the "bank SMS linked to an existing transaction" notification: splits it back out. */
class DupActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SPLIT) return
        val linkId = intent.getLongExtra(EXTRA_LINK_ID, -1)
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, 0)
        if (linkId <= 0) return
        val pending = goAsync()
        context.app.appScope.launch {
            try {
                context.app.repository.splitLink(linkId)
                Notifier.cancel(context, notifId)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_SPLIT = "com.midroid.masarifi.action.SPLIT_SMS_LINK"
        const val EXTRA_LINK_ID = "linkId"
        const val EXTRA_NOTIF_ID = "notifId"

        fun splitIntent(context: Context, linkId: Long, notifId: Int): PendingIntent = PendingIntent.getBroadcast(
            context, notifId,
            Intent(context, DupActionReceiver::class.java).setAction(ACTION_SPLIT).putExtra(EXTRA_LINK_ID, linkId).putExtra(EXTRA_NOTIF_ID, notifId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
