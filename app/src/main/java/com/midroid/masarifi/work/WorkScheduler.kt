package com.midroid.masarifi.work

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

object WorkScheduler {
    fun scheduleDaily(context: Context) {
        val now = LocalDateTime.now()
        var target = now.toLocalDate().atTime(20, 0)
        if (!target.isAfter(now)) target = target.plusDays(1)
        val delay = Duration.between(now, target).toMinutes()
        val req = PeriodicWorkRequestBuilder<DailyCheckWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delay, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("daily_check", ExistingPeriodicWorkPolicy.KEEP, req)
    }

    /** Bill reminders («الفواتير»): once a day at 09:00 local time. */
    fun scheduleBills(context: Context) {
        val now = LocalDateTime.now()
        var target = now.toLocalDate().atTime(9, 0)
        if (!target.isAfter(now)) target = target.plusDays(1)
        val req = PeriodicWorkRequestBuilder<BillReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(Duration.between(now, target).toMinutes(), TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("bill_reminders", ExistingPeriodicWorkPolicy.KEEP, req)
    }

    /** Re-evaluate budgets shortly after expenses change (debounced by REPLACE). */
    fun checkSoon(context: Context) = runCatching {
        val req = OneTimeWorkRequestBuilder<DailyCheckWorker>()
            .setInitialDelay(5, TimeUnit.SECONDS)
            .addTag(DailyCheckWorker.TAG_QUICK)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("budget_check_now", ExistingWorkPolicy.REPLACE, req)
        Unit
    }
}
