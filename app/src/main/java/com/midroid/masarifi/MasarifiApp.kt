package com.midroid.masarifi

import android.app.Application
import androidx.room.InvalidationTracker
import com.midroid.masarifi.data.AppDatabase
import com.midroid.masarifi.data.Repository
import com.midroid.masarifi.data.SettingsStore
import com.midroid.masarifi.data.backup.BackupService
import com.midroid.masarifi.data.backup.BackupStore
import com.midroid.masarifi.work.BackupWorker
import com.midroid.masarifi.work.Notifier
import com.midroid.masarifi.work.WorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MasarifiApp : Application() {
    lateinit var settings: SettingsStore
        private set
    lateinit var repository: Repository
        private set
    lateinit var backupStore: BackupStore
        private set
    lateinit var backupService: BackupService
        private set
    lateinit var syncStore: com.midroid.masarifi.data.sync.SyncStore
        private set
    lateinit var syncService: com.midroid.masarifi.data.sync.SyncService
        private set
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
        repository = Repository(AppDatabase.get(this), settings) { WorkScheduler.checkSoon(this) }
        backupStore = BackupStore(this)
        backupService = BackupService(repository, settings) { BuildConfigInfo.versionName(this) }
        syncStore = com.midroid.masarifi.data.sync.SyncStore(this)
        syncService = com.midroid.masarifi.data.sync.SyncService(repository, settings, syncStore) { BuildConfigInfo.versionName(this) }
        Notifier.createChannels(this)
        // Debounced automatic backup after data changes (only active when a backup folder is set and enabled).
        runCatching {
            AppDatabase.get(this).invalidationTracker.addObserver(object : InvalidationTracker.Observer(
                arrayOf("transactions", "accounts", "categories", "budgets", "recurring", "merchant_rules", "sms_links", "sync_tombstones", "bills", "bill_payments"),
            ) {
                override fun onInvalidated(tables: Set<String>) {
                    BackupWorker.onDataChanged(this@MasarifiApp)
                    com.midroid.masarifi.work.SyncWorker.onDataChanged(this@MasarifiApp)
                }
            })
        }
        try {
            WorkScheduler.scheduleDaily(this)
            WorkScheduler.scheduleBills(this)
            BackupWorker.schedule(this)
            com.midroid.masarifi.work.SyncWorker.schedule(this)
        } catch (e: IllegalStateException) {
            android.util.Log.w("Masarifi", "WorkManager not available", e)
        }
        appScope.launch {
            runCatching {
                repository.ensureDefaults()
                repository.processRecurring()
            }
        }
    }
}

val android.content.Context.app: MasarifiApp get() = applicationContext as MasarifiApp
