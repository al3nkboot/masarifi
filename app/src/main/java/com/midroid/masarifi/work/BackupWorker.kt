package com.midroid.masarifi.work

import android.content.Context
import android.net.Uri
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.midroid.masarifi.app
import com.midroid.masarifi.data.backup.BackupService
import java.util.concurrent.TimeUnit

/** Writes a full backup into the user-chosen SAF folder (e.g. Google Drive) and keeps the latest 10. */
class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext.app
        val st = app.backupStore.current
        val tree = st.treeUri ?: return Result.success()
        val onChange = tags.contains(TAG_CHANGE)
        if (onChange && !st.onChange) return Result.success()
        if (!onChange && !st.autoDaily) return Result.success()
        return try {
            val (json, _) = app.backupService.encodeCurrent()
            val name = BackupService.writeToFolder(applicationContext.contentResolver, Uri.parse(tree), json)
            app.backupStore.update { it.copy(lastAt = System.currentTimeMillis(), lastOk = true, lastError = null, lastFile = name) }
            Result.success()
        } catch (e: Exception) {
            app.backupStore.update { it.copy(lastAt = System.currentTimeMillis(), lastOk = false, lastError = e.message ?: e.javaClass.simpleName) }
            if (runAttemptCount < 2 && e is java.io.IOException) Result.retry() else Result.success()
        }
    }

    companion object {
        const val TAG_CHANGE = "backup_on_change"
        private const val DAILY = "auto_backup_daily"
        private const val CHANGE = "auto_backup_change"

        /** Applies the current settings: schedules or cancels the daily backup. */
        fun schedule(context: Context) = runCatching {
            val st = context.app.backupStore.current
            val wm = WorkManager.getInstance(context)
            if (st.treeUri != null && st.autoDaily) {
                val req = PeriodicWorkRequestBuilder<BackupWorker>(1, TimeUnit.DAYS)
                    .setInitialDelay(1, TimeUnit.HOURS)
                    .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                    .build()
                wm.enqueueUniquePeriodicWork(DAILY, ExistingPeriodicWorkPolicy.KEEP, req)
            } else wm.cancelUniqueWork(DAILY)
            Unit
        }

        /** Debounced backup after data changes (each change pushes the run back). */
        fun onDataChanged(context: Context) = runCatching {
            val st = context.app.backupStore.current
            if (st.treeUri == null || !st.onChange) return@runCatching
            val req = OneTimeWorkRequestBuilder<BackupWorker>().setInitialDelay(3, TimeUnit.MINUTES).addTag(TAG_CHANGE).build()
            WorkManager.getInstance(context).enqueueUniqueWork(CHANGE, ExistingWorkPolicy.REPLACE, req)
            Unit
        }
    }
}
