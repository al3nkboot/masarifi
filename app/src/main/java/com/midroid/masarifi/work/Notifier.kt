package com.midroid.masarifi.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.midroid.masarifi.MainActivity
import com.midroid.masarifi.R

object Notifier {
    const val CH_AUTO = "auto_txn"
    const val CH_BUDGET = "budget"
    const val CH_INSIGHTS = "insights"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(CH_AUTO, context.getString(R.string.channel_auto), NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CH_BUDGET, context.getString(R.string.channel_budget), NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel(CH_INSIGHTS, context.getString(R.string.channel_insights), NotificationManager.IMPORTANCE_DEFAULT))
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Optional notification action button (e.g. "undo"). */
    data class Action(val title: String, val intent: PendingIntent)

    fun cancel(context: Context, id: Int) = runCatching { NotificationManagerCompat.from(context).cancel(id) }

    fun show(context: Context, channel: String, id: Int, title: String, text: String, openTxnId: Long? = null, action: Action? = null) {
        if (!canNotify(context)) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (openTxnId != null) putExtra(MainActivity.EXTRA_TXN_ID, openTxnId)
        }
        val pi = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setColor(0xFFF25C54.toInt())
            .apply { if (action != null) addAction(0, action.title, action.intent) }
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, n)
        } catch (_: SecurityException) {
        }
    }
}
