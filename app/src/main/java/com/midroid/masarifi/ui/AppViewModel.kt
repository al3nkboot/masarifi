package com.midroid.masarifi.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.midroid.masarifi.app
import com.midroid.masarifi.data.Account
import com.midroid.masarifi.data.AppSettings
import com.midroid.masarifi.data.Budget
import com.midroid.masarifi.data.Category
import com.midroid.masarifi.data.CsvBackup
import com.midroid.masarifi.data.MerchantRule
import com.midroid.masarifi.data.Recurring
import com.midroid.masarifi.data.Txn
import com.midroid.masarifi.data.TxnType
import com.midroid.masarifi.data.backup.BackupCodec
import com.midroid.masarifi.data.backup.BackupData
import com.midroid.masarifi.data.backup.BackupFormatException
import com.midroid.masarifi.data.backup.BackupService
import com.midroid.masarifi.data.backup.BackupState
import com.midroid.masarifi.data.mm.MmFormatException
import com.midroid.masarifi.data.mm.MmImportResult
import com.midroid.masarifi.data.mm.MmPlan
import com.midroid.masarifi.data.mm.MoneyManagerImporter
import com.midroid.masarifi.data.mm.MoneyManagerParser
import com.midroid.masarifi.data.dup.DupGroup
import com.midroid.masarifi.data.dup.DuplicateDetector
import com.midroid.masarifi.sms.SmsImporter
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import com.midroid.masarifi.work.BackupWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.YearMonth

/** Money Manager import dialog state. */
sealed interface MmUi {
    data object Reading : MmUi
    data class Preview(val plan: MmPlan) : MmUi
    data object Importing : MmUi
    data class Done(val result: MmImportResult) : MmUi
    /** code: legacy_xls | no_header | generic */
    data class Error(val code: String) : MmUi
}

/** Device-sync dialog state. */
sealed interface SyncUi {
    data object Busy : SyncUi
    data class ConfirmOpen(val uri: Uri, val label: String, val items: Int, val devices: List<String>) : SyncUi
    data class Mismatch(val newId: String, val oldId: String) : SyncUi
    data class Error(val code: String) : SyncUi
}

/** Restore-from-backup dialog state. */
sealed interface RestoreUi {
    data object Reading : RestoreUi
    data class Preview(val data: BackupData) : RestoreUi
    data object Restoring : RestoreUi
    /** code: newer | invalid | generic */
    data class Error(val code: String) : RestoreUi
    /** App CSV export picked under «استعادة»: merged into the current data (duplicates skipped). */
    data class CsvConfirm(val uri: Uri, val rows: Int) : RestoreUi
}

/** Snackbar with an "undo" action after a bulk delete. */
data class UndoEvent(val message: String, val rows: List<Txn>)

/** Per-account computed balance. */
data class AccountBalance(val account: Account, val balance: Long)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application.app
    val repo = app.repository
    private val settingsStore = app.settings

    val settings: StateFlow<AppSettings> = settingsStore.state

    val txns: StateFlow<List<Txn>> = repo.txnDao.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val accounts: StateFlow<List<Account>> = repo.accountDao.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val categories: StateFlow<List<Category>> = repo.categoryDao.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val budgets: StateFlow<List<Budget>> = repo.budgetDao.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val recurring: StateFlow<List<Recurring>> = repo.recurringDao.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val merchantRules: StateFlow<List<MerchantRule>> = repo.merchantRuleDao.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val smsImportedCount: StateFlow<Int> = repo.smsLogDao.observeImportedCount().stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val month = MutableStateFlow(YearMonth.now())

    // ---------------- bills («الفواتير», 2.1.0) ----------------

    /** Set by the widget / a bill notification: switch to the bills tab. */
    val openBills = MutableStateFlow(false)

    val bills: StateFlow<List<com.midroid.masarifi.bills.Bill>> = repo.billDao.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val billPayments: StateFlow<List<com.midroid.masarifi.bills.BillPayment>> = repo.billDao.observePayments().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** "Today" for bill states (overridable by screenshot tests). */
    var todayOverride: java.time.LocalDate? = null
    fun today(): java.time.LocalDate = todayOverride ?: java.time.LocalDate.now()

    data class BillUndo(val message: String, val result: com.midroid.masarifi.bills.PayResult)
    private val _billUndo = MutableSharedFlow<BillUndo>(extraBufferCapacity = 4)
    val billUndo: SharedFlow<BillUndo> = _billUndo

    private fun billsChanged() { com.midroid.masarifi.widget.BillsWidget.refresh(getApplication()) }

    fun saveBill(b: com.midroid.masarifi.bills.Bill) = viewModelScope.launch(Dispatchers.IO) { repo.bills.save(b); billsChanged() }
    fun deleteBill(b: com.midroid.masarifi.bills.Bill) = viewModelScope.launch(Dispatchers.IO) { repo.bills.delete(b.id); billsChanged() }
    suspend fun billSmsMatches(amount: Long): List<Txn> = withContext(Dispatchers.IO) { repo.bills.smsMatches(amount, System.currentTimeMillis()) }

    fun payBill(b: com.midroid.masarifi.bills.Bill, amount: Long, accountId: Long, categoryId: Long?, linkTxnId: Long?) = viewModelScope.launch(Dispatchers.IO) {
        val r = repo.bills.pay(b.id, amount, accountId, categoryId, System.currentTimeMillis(), linkTxnId) ?: return@launch
        val res = com.midroid.masarifi.work.LocalizedRes.of(getApplication(), settings.value.arabic)
        val msg = when {
            !r.createdTxn -> res.getString(com.midroid.masarifi.R.string.bill_paid_merged, b.name)
            r.flaggedDup -> res.getString(com.midroid.masarifi.R.string.bill_paid_flagged, b.name)
            else -> res.getString(com.midroid.masarifi.R.string.bill_paid_done, b.name)
        }
        _billUndo.tryEmit(BillUndo(msg, r))
        billsChanged()
    }

    fun undoBillPayment(r: com.midroid.masarifi.bills.PayResult) = viewModelScope.launch(Dispatchers.IO) { repo.bills.undoPay(r); billsChanged() }
    fun deleteBillPayment(p: com.midroid.masarifi.bills.BillPayment) = viewModelScope.launch(Dispatchers.IO) { repo.bills.deletePayment(p.id); billsChanged() }

    fun importMi5g(uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
        val res = com.midroid.masarifi.work.LocalizedRes.of(getApplication(), settings.value.arabic)
        val msg = try {
            val text = BackupService.readFrom(getApplication<Application>().contentResolver, uri)
            val r = repo.bills.importMi5g(text, settings.value.currency)
            res.getString(com.midroid.masarifi.R.string.bill_import_done, r.first, r.second)
        } catch (e: com.midroid.masarifi.bills.Mi5gImportException) {
            res.getString(if (e.code == "encrypted") com.midroid.masarifi.R.string.bill_import_encrypted else com.midroid.masarifi.R.string.bill_import_invalid)
        } catch (e: Exception) {
            res.getString(com.midroid.masarifi.R.string.bill_import_invalid)
        }
        message(msg)
        billsChanged()
    }

    /** Day selected in the calendar view (used as default date when adding). */
    /** True while a transaction list is in multi-select mode (hides the add FAB). */
    var selectionActive by androidx.compose.runtime.mutableStateOf(false)
    var selectedDay: java.time.LocalDate? = null

    fun selectedDayForAdd(): Long {
        val now = java.time.LocalDateTime.now()
        val ym = month.value
        val day = selectedDay?.takeIf { YearMonth.from(it) == ym }
            ?: if (ym == YearMonth.from(now)) now.toLocalDate() else ym.atDay(1)
        return Fmt.toMillis(day.atTime(now.toLocalTime().withSecond(0).withNano(0)))
    }

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    val scanning = MutableStateFlow(false)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            repo.ensureDefaults()
            repo.processRecurring()
        }
    }

    fun message(text: String) { _messages.tryEmit(text) }

    fun updateSettings(block: (AppSettings) -> AppSettings) = settingsStore.update(block)

    /** [repeat] null = keep the existing repeat rule unchanged. */
    fun saveTxn(txn: Txn, repeat: Int?) = viewModelScope.launch(Dispatchers.IO) { repo.saveTxn(txn, repeat) }
    suspend fun repeatOf(txn: Txn): Int = withContext(Dispatchers.IO) { repo.repeatOf(txn) }
    fun deleteTxn(txn: Txn) = viewModelScope.launch(Dispatchers.IO) { repo.deleteTxn(txn) }
    fun saveAccount(a: Account) = viewModelScope.launch(Dispatchers.IO) { repo.saveAccount(a) }
    fun deleteAccount(a: Account) = viewModelScope.launch(Dispatchers.IO) { repo.deleteAccount(a) }
    fun saveCategory(c: Category) = viewModelScope.launch(Dispatchers.IO) { repo.saveCategory(c) }
    fun deleteCategory(c: Category) = viewModelScope.launch(Dispatchers.IO) { repo.deleteCategory(c) }
    fun setBudget(categoryId: Long, amount: Long) = viewModelScope.launch(Dispatchers.IO) { repo.setBudget(categoryId, amount) }
    fun setRecurringActive(r: Recurring, active: Boolean) = viewModelScope.launch(Dispatchers.IO) { repo.recurringDao.update(r.copy(active = active)) }
    fun deleteRecurring(r: Recurring) = viewModelScope.launch(Dispatchers.IO) { repo.recurringDao.delete(r.id) }
    fun deleteMerchantRule(r: MerchantRule) = viewModelScope.launch(Dispatchers.IO) { repo.merchantRuleDao.delete(r.merchantKey) }

    // ---------------- Money Manager import ----------------

    val mmUi = MutableStateFlow<MmUi?>(null)

    fun mmPick(uri: Uri) {
        mmUi.value = MmUi.Reading
        viewModelScope.launch(Dispatchers.IO) {
            mmUi.value = try {
                val bytes = getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                    val b = input.readBytes()
                    if (b.size > 50 * 1024 * 1024) throw java.io.IOException("too large")
                    b
                } ?: throw java.io.IOException("no input")
                val parsed = MoneyManagerParser.parseFile(bytes)
                MmUi.Preview(MoneyManagerImporter(repo).plan(parsed))
            } catch (e: MmFormatException) {
                MmUi.Error(if (e.code == "legacy_xls" || e.code == "no_header") e.code else "generic")
            } catch (e: Exception) {
                android.util.Log.w("Masarifi", "Money Manager import failed", e)
                MmUi.Error("generic")
            }
        }
    }

    fun mmConfirm() {
        val p = (mmUi.value as? MmUi.Preview)?.plan ?: return
        mmUi.value = MmUi.Importing
        viewModelScope.launch(Dispatchers.IO) {
            mmUi.value = try { MmUi.Done(MoneyManagerImporter(repo).commit(p.parse)) } catch (e: Exception) {
                android.util.Log.w("Masarifi", "Money Manager import commit failed", e)
                MmUi.Error("generic")
            }
        }
    }

    fun mmDismiss() { mmUi.value = null }

    // ---------------- Device sync (shared SAF file on Google Drive, merge / last-write-wins) ----------------

    private val syncStore = app.syncStore
    private val syncService = app.syncService
    val syncState: StateFlow<com.midroid.masarifi.data.sync.SyncState> = syncStore.state
    val syncUi = MutableStateFlow<SyncUi?>(null)
    private val rwFlags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION

    private fun takePermission(uri: Uri): Boolean = runCatching {
        getApplication<Application>().contentResolver.takePersistableUriPermission(uri, rwFlags); true
    }.getOrDefault(false)

    private fun releaseOld(newUri: Uri?) {
        val old = syncStore.current.fileUri ?: return
        if (old != newUri?.toString()) runCatching { getApplication<Application>().contentResolver.releasePersistableUriPermission(Uri.parse(old), rwFlags) }
    }

    /** Device A: a new (empty) file was created with ACTION_CREATE_DOCUMENT → write the initial snapshot. */
    fun syncCreate(uri: Uri, done: (com.midroid.masarifi.data.sync.SyncStats?) -> String) {
        val persist = takePermission(uri)
        releaseOld(uri)
        val cr = getApplication<Application>().contentResolver
        syncStore.update { it.copy(fileUri = uri.toString(), fileLabel = BackupService.displayName(cr, uri) ?: uri.lastPathSegment, fileId = null, devices = emptyMap(), persistable = persist) }
        runSync("setup", acceptOther = true, done = done)
    }

    /** Device B: an existing file was picked with ACTION_OPEN_DOCUMENT → preview, then confirm the merge. */
    fun syncOpen(uri: Uri) {
        syncUi.value = SyncUi.Busy
        viewModelScope.launch(Dispatchers.IO) {
            val cr = getApplication<Application>().contentResolver
            syncUi.value = try {
                val snap = com.midroid.masarifi.data.sync.SyncCodec.decode(BackupService.readFrom(cr, uri))
                SyncUi.ConfirmOpen(uri, BackupService.displayName(cr, uri) ?: uri.lastPathSegment ?: "?", snap.live.size, snap.devices.values.map { it.name })
            } catch (e: com.midroid.masarifi.data.sync.SyncFormatException) {
                SyncUi.Error(e.code)
            } catch (e: Exception) {
                SyncUi.Error("io")
            }
        }
    }

    fun syncConfirmOpen(done: (com.midroid.masarifi.data.sync.SyncStats?) -> String) {
        val c = syncUi.value as? SyncUi.ConfirmOpen ?: return
        val persist = takePermission(c.uri)
        releaseOld(c.uri)
        syncStore.update { it.copy(fileUri = c.uri.toString(), fileLabel = c.label, fileId = null, devices = emptyMap(), persistable = persist) }
        runSync("setup", acceptOther = true, done = done)
    }

    fun syncNow(done: (com.midroid.masarifi.data.sync.SyncStats?) -> String, acceptOther: Boolean = false) = runSync("manual", acceptOther, done)

    private fun runSync(trigger: String, acceptOther: Boolean, done: (com.midroid.masarifi.data.sync.SyncStats?) -> String) {
        val uri = syncStore.current.fileUri ?: return
        syncUi.value = SyncUi.Busy
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val r = syncService.sync(com.midroid.masarifi.data.sync.SafSyncFile(getApplication<Application>().contentResolver, Uri.parse(uri)), trigger, acceptOther)
                    Result.success(r)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
            com.midroid.masarifi.work.SyncWorker.schedule(getApplication())
            result.fold(
                onSuccess = { syncUi.value = null; message(done(it.stats)) },
                onFailure = { e ->
                    syncUi.value = when (e) {
                        is com.midroid.masarifi.data.sync.SyncMismatchException -> SyncUi.Mismatch(e.fileId, syncStore.current.fileId ?: "?")
                        is com.midroid.masarifi.data.sync.SyncFormatException -> SyncUi.Error(e.code)
                        is SecurityException -> SyncUi.Error("no_access")
                        else -> { message(done(null)); null }
                    }
                },
            )
        }
    }

    fun syncDisconnect() {
        releaseOld(null)
        syncStore.update { it.copy(fileUri = null, fileLabel = null, fileId = null, devices = emptyMap()) }
        com.midroid.masarifi.work.SyncWorker.schedule(getApplication())
    }

    fun syncDismiss() { syncUi.value = null }
    fun setSyncAuto(v: Boolean) { syncStore.update { it.copy(auto = v) }; com.midroid.masarifi.work.SyncWorker.schedule(getApplication()) }
    fun setSyncInterval(min: Int) { syncStore.update { it.copy(intervalMin = min) }; com.midroid.masarifi.work.SyncWorker.schedule(getApplication()) }
    fun setDeviceName(name: String) { if (name.isNotBlank()) syncStore.update { it.copy(deviceName = name.trim().take(40)) } }

    // ---------------- Backup / restore (Storage Access Framework → Google Drive, no OAuth) ----------------

    private val backupStore = app.backupStore
    private val backupService = app.backupService
    val backupState: StateFlow<BackupState> = backupStore.state
    val backupBusy = MutableStateFlow(false)
    val restoreUi = MutableStateFlow<RestoreUi?>(null)

    fun backupNow(uri: Uri, done: (Int) -> String) {
        backupBusy.value = true
        viewModelScope.launch {
            val n = withContext(Dispatchers.IO) {
                try {
                    val (json, d) = backupService.encodeCurrent()
                    BackupService.writeTo(getApplication<Application>().contentResolver, uri, json)
                    backupStore.update { it.copy(lastAt = System.currentTimeMillis(), lastOk = true, lastError = null, lastFile = BackupService.displayName(getApplication<Application>().contentResolver, uri)) }
                    d.transactions.size
                } catch (e: Exception) {
                    backupStore.update { it.copy(lastAt = System.currentTimeMillis(), lastOk = false, lastError = e.message ?: e.javaClass.simpleName) }
                    -1
                }
            }
            backupBusy.value = false
            message(done(n))
        }
    }

    /** Called with the folder picked via ACTION_OPEN_DOCUMENT_TREE. Writes a first backup to verify the folder works. */
    fun chooseBackupFolder(uri: Uri, ok: String, unsupported: String) {
        backupBusy.value = true
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                val cr = getApplication<Application>().contentResolver
                val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                try {
                    cr.takePersistableUriPermission(uri, flags)
                    val (json, _) = backupService.encodeCurrent()
                    val name = BackupService.writeToFolder(cr, uri, json)
                    val old = backupStore.current.treeUri
                    if (old != null && old != uri.toString()) runCatching { cr.releasePersistableUriPermission(Uri.parse(old), flags) }
                    backupStore.update {
                        it.copy(treeUri = uri.toString(), folderLabel = BackupService.folderLabel(cr, uri), lastAt = System.currentTimeMillis(),
                            lastOk = true, lastError = null, lastFile = name)
                    }
                    true
                } catch (e: Exception) {
                    android.util.Log.w("Masarifi", "Backup folder not usable", e)
                    runCatching { cr.releasePersistableUriPermission(uri, flags) }
                    false
                }
            }
            if (success) BackupWorker.schedule(getApplication())
            backupBusy.value = false
            message(if (success) ok else unsupported)
        }
    }

    fun clearBackupFolder() {
        val old = backupStore.current.treeUri
        if (old != null) runCatching {
            getApplication<Application>().contentResolver.releasePersistableUriPermission(
                Uri.parse(old), android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        backupStore.update { it.copy(treeUri = null, folderLabel = null) }
        BackupWorker.schedule(getApplication())
    }

    fun setAutoDaily(v: Boolean) { backupStore.update { it.copy(autoDaily = v) }; BackupWorker.schedule(getApplication()) }
    fun setBackupOnChange(v: Boolean) { backupStore.update { it.copy(onChange = v) } }

    fun restorePick(uri: Uri) {
        restoreUi.value = RestoreUi.Reading
        viewModelScope.launch(Dispatchers.IO) {
            restoreUi.value = try {
                RestoreUi.Preview(BackupCodec.decode(BackupService.readFrom(getApplication<Application>().contentResolver, uri)))
            } catch (e: BackupFormatException) {
                RestoreUi.Error(if (e.code == "newer") "newer" else "invalid")
            } catch (e: Exception) {
                RestoreUi.Error("generic")
            }
        }
    }

    fun restoreConfirm(done: (Int) -> String, onLanguageChanged: () -> Unit) {
        val d = (restoreUi.value as? RestoreUi.Preview)?.data ?: return
        restoreUi.value = RestoreUi.Restoring
        val oldLang = settings.value.language
        viewModelScope.launch {
            val n = withContext(Dispatchers.IO) {
                try {
                    // Safety copy of the current data inside the app before replacing it.
                    runCatching {
                        val dir = java.io.File(getApplication<Application>().filesDir, "backups").apply { mkdirs() }
                        java.io.File(dir, "pre-restore.json").writeText(backupService.encodeCurrent().first)
                    }
                    backupService.restore(d)
                    d.transactions.size
                } catch (e: Exception) {
                    android.util.Log.w("Masarifi", "Restore failed", e)
                    -1
                }
            }
            restoreUi.value = null
            message(done(n))
            if (n >= 0 && settings.value.language != oldLang) onLanguageChanged()
        }
    }

    fun restoreDismiss() { restoreUi.value = null }

    /** Single «استعادة» picker: detects the file type and routes to full restore / CSV merge / Money Manager import. */
    fun restoreAny(uri: Uri) {
        restoreUi.value = RestoreUi.Reading
        viewModelScope.launch(Dispatchers.IO) {
            val cr = getApplication<Application>().contentResolver
            val bytes = runCatching { cr.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            if (bytes == null) { restoreUi.value = RestoreUi.Error("generic"); return@launch }
            when (com.midroid.masarifi.data.backup.RestoreFileDetector.detect(bytes, BackupService.displayName(cr, uri))) {
                com.midroid.masarifi.data.backup.RestoreFileKind.APP_BACKUP -> restoreUi.value = try {
                    RestoreUi.Preview(BackupCodec.decode(String(bytes, Charsets.UTF_8)))
                } catch (e: BackupFormatException) { RestoreUi.Error(if (e.code == "newer") "newer" else "invalid") }
                com.midroid.masarifi.data.backup.RestoreFileKind.APP_CSV ->
                    restoreUi.value = RestoreUi.CsvConfirm(uri, (CsvBackup.parse(String(bytes, Charsets.UTF_8).removePrefix("\uFEFF")).size - 1).coerceAtLeast(0))
                com.midroid.masarifi.data.backup.RestoreFileKind.MONEY_MANAGER -> { restoreUi.value = null; withContext(Dispatchers.Main) { mmPick(uri) } }
                com.midroid.masarifi.data.backup.RestoreFileKind.SYNC_FILE -> restoreUi.value = RestoreUi.Error("sync_file")
                com.midroid.masarifi.data.backup.RestoreFileKind.UNKNOWN -> restoreUi.value = RestoreUi.Error("invalid")
            }
        }
    }

    fun restoreCsvConfirm(done: (Int) -> String) {
        val r = restoreUi.value as? RestoreUi.CsvConfirm ?: return
        restoreUi.value = null
        importCsv(r.uri, done)
    }

    // ---- repair lost categories (1.7.1) ----
    val relinkUi = MutableStateFlow<com.midroid.masarifi.data.catfix.CategoryRelink.Plan?>(null)
    val relinkError = MutableStateFlow(false)
    val uncategorizedCount: StateFlow<Int> = combine(txns, categories) { t, c ->
        val ids = c.map { it.id }.toSet()
        t.count { it.type != TxnType.TRANSFER && ((it.categoryId != null && it.categoryId !in ids) || (it.categoryId == null && it.isAuto)) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    fun relinkPlan(uri: Uri?) = viewModelScope.launch(Dispatchers.IO) {
        relinkError.value = false
        try {
            val backup = uri?.let { BackupCodec.decode(BackupService.readFrom(getApplication<Application>().contentResolver, it)) }
            relinkUi.value = com.midroid.masarifi.data.catfix.CategoryRelink(repo).plan(backup)
        } catch (e: Exception) { relinkError.value = true }
    }
    fun relinkApply(done: (Int) -> String) {
        val p = relinkUi.value ?: return
        relinkUi.value = null
        viewModelScope.launch { val n = withContext(Dispatchers.IO) { com.midroid.masarifi.data.catfix.CategoryRelink(repo).apply(p) }; message(done(n)) }
    }
    fun relinkDismiss() { relinkUi.value = null; relinkError.value = false }

    // ---------------- bulk delete with undo ----------------

    private val _undoEvents = MutableSharedFlow<UndoEvent>(extraBufferCapacity = 4)
    val undoEvents: SharedFlow<UndoEvent> = _undoEvents

    /** Deletes [ids] in one DB transaction, then offers undo via [undoEvents]. [message] formats the count. */
    fun deleteTxns(ids: Collection<Long>, message: (Int) -> String) = viewModelScope.launch {
        val rows = withContext(Dispatchers.IO) { repo.deleteTxns(ids) }
        if (rows.isNotEmpty()) _undoEvents.tryEmit(UndoEvent(message(rows.size), rows))
    }

    fun undoDelete(rows: List<Txn>) = viewModelScope.launch(Dispatchers.IO) { repo.restoreTxns(rows) }

    // ---------------- duplicates ----------------

    val dismissedPairs: StateFlow<List<String>> = repo.dupDao.observeDismissed().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val dupGroups: StateFlow<List<DupGroup>> = combine(txns, dismissedPairs, settings, billPayments) { t, d, s, p ->
        val billUuids = p.mapNotNull { it.txnUuid }.toHashSet()
        DuplicateDetector.scan(t, s.dupWindowHours, d.toHashSet(), t.filter { it.uuid in billUuids }.map { it.id }.toHashSet())
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Existing transactions that look like [draft] (same type + amount within the window). */
    fun conflictsFor(draft: Txn): List<Txn> = DuplicateDetector.manualConflicts(draft, txns.value, settings.value.dupWindowHours)

    fun mergeGroup(ids: List<Long>, done: String) = viewModelScope.launch {
        withContext(Dispatchers.IO) { repo.mergeGroup(ids) }
        message(done)
    }

    fun keepBoth(ids: List<Long>, done: String) = viewModelScope.launch {
        withContext(Dispatchers.IO) { repo.keepBoth(ids) }
        message(done)
    }

    fun mergeIntoExisting(existingId: Long, draft: Txn) = viewModelScope.launch(Dispatchers.IO) { repo.mergeIntoExisting(existingId, draft) }

    /** Splits the most recent SMS linked to [txnId] back into its own transaction. */
    fun splitLinked(txnId: Long, done: String) = viewModelScope.launch {
        val ok = withContext(Dispatchers.IO) { repo.dupDao.linksFor(txnId).firstOrNull()?.let { repo.splitLink(it.id) } != null }
        if (ok) message(done)
    }

    fun balances(accounts: List<Account>, txns: List<Txn>): List<AccountBalance> {
        val delta = HashMap<Long, Long>()
        for (t in txns) {
            when (t.type) {
                TxnType.INCOME -> delta[t.accountId] = (delta[t.accountId] ?: 0) + t.amount
                TxnType.EXPENSE -> delta[t.accountId] = (delta[t.accountId] ?: 0) - t.amount
                TxnType.TRANSFER -> {
                    delta[t.accountId] = (delta[t.accountId] ?: 0) - t.amount
                    t.toAccountId?.let { delta[it] = (delta[it] ?: 0) + t.amount }
                }
            }
        }
        return accounts.map { AccountBalance(it, it.initialBalance + (delta[it.id] ?: 0)) }
    }

    fun scanInbox(doneText: (SmsImporter.ScanResult) -> String) {
        if (scanning.value) return
        scanning.value = true
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { SmsImporter.scanInbox(getApplication()) }.getOrDefault(SmsImporter.ScanResult(0, 0)) }
            scanning.value = false
            message(doneText(r))
        }
    }

    fun rescanInbox(doneText: (SmsImporter.ScanResult) -> String) {
        if (scanning.value) return
        scanning.value = true
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { SmsImporter.rescanInbox(getApplication()) }.getOrDefault(SmsImporter.ScanResult(0, 0)) }
            scanning.value = false
            message(doneText(r))
        }
    }

    /** Own-wallet top-up rules (empty list is stored as "[]"; [reset] restores the defaults). */
    /** Period of «الملخص المالي». */
    val summaryPeriod = MutableStateFlow<com.midroid.masarifi.ui.summary.PeriodChoice>(com.midroid.masarifi.ui.summary.PeriodChoice.ThisMonth)

    // ---- SMS repair tool ----
    val repairPlan = MutableStateFlow<List<com.midroid.masarifi.data.repair.RepairItem>?>(null)
    val repairBusy = MutableStateFlow(false)
    val lastRepair: StateFlow<com.midroid.masarifi.data.RepairBatch?> = repo.repairDao.observeLatest().stateIn(viewModelScope, SharingStarted.Eagerly, null)
    fun scanRepair() = viewModelScope.launch {
        repairBusy.value = true
        repairPlan.value = withContext(Dispatchers.IO) { repo.planRepair() }
        repairBusy.value = false
    }
    fun applyRepair(items: List<com.midroid.masarifi.data.repair.RepairItem>, done: (Int) -> String) = viewModelScope.launch {
        repairBusy.value = true
        withContext(Dispatchers.IO) { repo.applyRepair(items) }
        repairPlan.value = null
        repairBusy.value = false
        message(done(items.size))
    }
    fun undoRepair(id: Long, done: String) = viewModelScope.launch {
        withContext(Dispatchers.IO) { repo.undoRepair(id) }
        repairPlan.value = null
        message(done)
    }

    // ---- Card / account mappings (SMS last 4 digits → account), stored on the device only ----
    val cardMappings: StateFlow<List<com.midroid.masarifi.data.CardMapping>> =
        repo.cardMappingDao.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    fun saveCardMapping(m: com.midroid.masarifi.data.CardMapping) = viewModelScope.launch(Dispatchers.IO) {
        if (m.id == 0L) repo.cardMappingDao.insert(m.copy(source = 0)) else repo.cardMappingDao.update(m.copy(source = 0))
    }
    fun deleteCardMapping(m: com.midroid.masarifi.data.CardMapping) = viewModelScope.launch(Dispatchers.IO) { repo.cardMappingDao.delete(m.id) }

    private val cardBannerDismissed = MutableStateFlow(settingsStore.flag(CARD_BANNER_FLAG))
    /** Accounts with SMS transactions but no card digits configured → one-time banner (empty = no banner). */
    val cardSetupNeeded: StateFlow<List<Account>> = combine(txns, accounts, cardMappings, cardBannerDismissed) { t, a, m, dismissed ->
        if (dismissed) emptyList() else {
            val mapped = m.mapNotNull { it.accountId }.toSet()
            val sms = t.filter { it.isAuto }.map { it.accountId }.toSet()
            a.filter { it.id in sms && it.id !in mapped && it.cardLast4.isNullOrBlank() }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    fun dismissCardBanner() { settingsStore.setFlag(CARD_BANNER_FLAG); cardBannerDismissed.value = true }

    fun setWalletTopups(list: List<com.midroid.masarifi.sms.WalletTopup>?) =
        updateSettings { it.copy(walletTopups = list?.let { l -> com.midroid.masarifi.sms.WalletTopupCodec.encode(l) } ?: "") }

    /** The account a top-up rule currently resolves to (without creating it). */
    fun topupTargetName(t: com.midroid.masarifi.sms.WalletTopup, accounts: List<Account>, arabic: Boolean): String {
        t.targetAccountUuid?.let { u -> accounts.firstOrNull { it.uuid == u }?.let { return it.name } }
        val p = com.midroid.masarifi.sms.DefaultSmsConfig.banks.firstOrNull { it.id == t.targetBankId }
        if (p != null) {
            val names = listOf(p.accountName(true), p.accountName(false))
            return accounts.firstOrNull { a -> names.any { a.name == it || a.name.startsWith("$it ••") } }?.name ?: p.accountName(arabic)
        }
        return t.name(arabic)
    }

    fun exportCsv(uri: Uri, done: (Int) -> String) = viewModelScope.launch {
        val n = withContext(Dispatchers.IO) {
            runCatching {
                getApplication<Application>().contentResolver.openOutputStream(uri)?.use { CsvBackup.export(repo, it, settings.value) } ?: -1
            }.getOrDefault(-1)
        }
        message(done(n))
    }

    fun importCsv(uri: Uri, done: (Int) -> String) = viewModelScope.launch {
        val n = withContext(Dispatchers.IO) {
            runCatching {
                getApplication<Application>().contentResolver.openInputStream(uri)?.use { CsvBackup.import(repo, it, settings.value) } ?: -1
            }.getOrDefault(-1)
        }
        message(done(n))
    }

    private companion object { const val CARD_BANNER_FLAG = "card_banner_v5_dismissed" }
}
