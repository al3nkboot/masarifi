package com.midroid.masarifi.work

import android.content.Context
import android.net.Uri
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.midroid.masarifi.app
import com.midroid.masarifi.data.sync.SafSyncFile
import com.midroid.masarifi.data.sync.SyncFormatException
import com.midroid.masarifi.data.sync.SyncMismatchException
import java.util.concurrent.TimeUnit

/**
 * Background merge sync with the shared SAF file (only when the user enabled auto-sync).
 * Triggers: periodic (every N minutes, network required), app resume, and 30 s after local changes.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext.app
        val st = app.syncStore.current
        val uri = st.fileUri ?: return Result.success()
        if (!st.auto) return Result.success()
        val trigger = inputData.getString(KEY_TRIGGER) ?: "auto"
        // A change-triggered run is skipped when nothing changed since the last sync (also breaks the
        // "sync writes → invalidation → sync again" loop).
        if (trigger == T_CHANGE && app.syncService.localMark() <= st.lastMark) return Result.success()
        return try {
            app.syncService.sync(SafSyncFile(applicationContext.contentResolver, Uri.parse(uri)), trigger)
            Result.success()
        } catch (e: SyncFormatException) {
            Result.success()
        } catch (e: SyncMismatchException) {
            Result.success()
        } catch (e: SecurityException) {
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 2) Result.retry() else Result.success()
        }
    }

    companion object {
        const val KEY_TRIGGER = "trigger"
        const val T_PERIODIC = "auto"
        const val T_RESUME = "resume"
        const val T_CHANGE = "change"
        const val DEBOUNCE_SECONDS = 30L
        private const val PERIODIC = "sync_periodic"
        private const val ONCE = "sync_once"
        private const val CHANGE = "sync_change"

        private val network get() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** Applies the current settings: schedules or cancels the periodic sync. */
        fun schedule(context: Context) = runCatching {
            val st = context.app.syncStore.current
            val wm = WorkManager.getInstance(context)
            if (st.configured && st.auto) {
                val req = PeriodicWorkRequestBuilder<SyncWorker>(st.intervalMin.toLong().coerceAtLeast(15), TimeUnit.MINUTES)
                    .setConstraints(network).setInputData(workDataOf(KEY_TRIGGER to T_PERIODIC)).build()
                wm.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, req)
            } else {
                wm.cancelUniqueWork(PERIODIC); wm.cancelUniqueWork(CHANGE)
            }
            Unit
        }

        /** Sync when the app comes to the foreground (auto mode only; throttled to once a minute). */
        fun onResume(context: Context) = runCatching {
            val st = context.app.syncStore.current
            if (!st.configured || !st.auto || System.currentTimeMillis() - st.lastAt < 60_000) return@runCatching
            val req = OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(network).setInputData(workDataOf(KEY_TRIGGER to T_RESUME)).build()
            WorkManager.getInstance(context).enqueueUniqueWork(ONCE, ExistingWorkPolicy.KEEP, req)
            Unit
        }

        /** Debounced sync 30 s after the last local change (each change pushes the run back). */
        fun onDataChanged(context: Context) = runCatching {
            val st = context.app.syncStore.current
            if (!st.configured || !st.auto) return@runCatching
            val req = OneTimeWorkRequestBuilder<SyncWorker>().setInitialDelay(DEBOUNCE_SECONDS, TimeUnit.SECONDS)
                .setConstraints(network).setInputData(workDataOf(KEY_TRIGGER to T_CHANGE)).build()
            WorkManager.getInstance(context).enqueueUniqueWork(CHANGE, ExistingWorkPolicy.REPLACE, req)
            Unit
        }
    }
}
