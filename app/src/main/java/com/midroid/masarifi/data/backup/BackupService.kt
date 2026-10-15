package com.midroid.masarifi.data.backup

import android.content.ContentResolver
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.provider.DocumentsContract
import com.midroid.masarifi.data.Repository
import com.midroid.masarifi.data.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Device-local backup configuration & status (never included in the backup itself). */
data class BackupState(
    val treeUri: String? = null,
    val folderLabel: String? = null,
    val autoDaily: Boolean = true,
    val onChange: Boolean = false,
    val lastAt: Long = 0,
    val lastOk: Boolean = true,
    val lastError: String? = null,
    val lastFile: String? = null,
)

class BackupStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("masarifi_backup", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<BackupState> = _state.asStateFlow()
    val current get() = _state.value

    private fun read() = BackupState(
        treeUri = prefs.getString("treeUri", null), folderLabel = prefs.getString("folderLabel", null),
        autoDaily = prefs.getBoolean("autoDaily", true), onChange = prefs.getBoolean("onChange", false),
        lastAt = prefs.getLong("lastAt", 0), lastOk = prefs.getBoolean("lastOk", true),
        lastError = prefs.getString("lastError", null), lastFile = prefs.getString("lastFile", null),
    )

    @Synchronized
    fun update(block: (BackupState) -> BackupState) {
        val n = block(_state.value)
        prefs.edit().putString("treeUri", n.treeUri).putString("folderLabel", n.folderLabel).putBoolean("autoDaily", n.autoDaily)
            .putBoolean("onChange", n.onChange).putLong("lastAt", n.lastAt).putBoolean("lastOk", n.lastOk)
            .putString("lastError", n.lastError).putString("lastFile", n.lastFile).apply()
        _state.value = n
    }
}

/** Builds/restores full backups and writes them via the Storage Access Framework (works with Google Drive, no OAuth). */
class BackupService(
    private val repo: Repository,
    private val settings: SettingsStore,
    private val appVersion: () -> String,
) {
    private val dao get() = repo.database.backupDao()

    suspend fun snapshot(now: Long = System.currentTimeMillis()): BackupData = repo.inTransaction {
        BackupData(
            createdAt = now, appVersion = appVersion(), settings = settings.current,
            accounts = dao.accounts(), categories = dao.categories(), transactions = dao.transactions(),
            recurring = dao.recurring(), budgets = dao.budgets(), merchantRules = dao.merchantRules(), smsLog = dao.smsLog(),
            smsLinks = dao.smsLinks(), dupDismissed = dao.dupDismissed(),
        )
    }

    /** Replaces ALL data with the backup in a single transaction (rolled back entirely on error). */
    suspend fun restore(d: BackupData) {
        repo.inTransaction {
            dao.clearTransactions(); dao.clearRecurring(); dao.clearBudgets(); dao.clearMerchantRules(); dao.clearSmsLog()
            dao.clearSmsLinks(); dao.clearDupDismissed(); repo.database.smsBalanceDao().clear()
            dao.clearAccounts(); dao.clearCategories()
            dao.insertAccounts(d.accounts); dao.insertCategories(d.categories)
            d.transactions.chunked(500).forEach { dao.insertTransactions(it) }
            dao.insertRecurring(d.recurring); dao.insertBudgets(d.budgets); dao.insertMerchantRules(d.merchantRules)
            d.smsLog.chunked(500).forEach { dao.insertSmsLog(it) }
            d.smsLinks.chunked(500).forEach { dao.insertSmsLinks(it) }
            dao.insertDupDismissed(d.dupDismissed)
        }
        d.settings?.let { s -> settings.update { s } }
        repo.ensureDefaults()
        repo.notifyBulkChange()
    }

    suspend fun encodeCurrent(): Pair<String, BackupData> { val d = snapshot(); return BackupCodec.encode(d) to d }

    companion object {
        const val KEEP = 10
        const val MIME = "application/json"
        private val NAME_FMT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.US)
        val NAME_RE = Regex("^masarifi-backup-\\d{8}-\\d{4,6}.*\\.json$")

        fun fileName(now: LocalDateTime = LocalDateTime.now()) = "masarifi-backup-${now.format(NAME_FMT)}.json"

        /** Given backup file names in a folder, returns those to delete so only the newest [keep] remain. */
        fun selectForPruning(names: List<String>, keep: Int = KEEP): List<String> =
            names.filter { NAME_RE.matches(it) }.sortedDescending().drop(keep)

        fun writeTo(cr: ContentResolver, uri: Uri, json: String) {
            val out = runCatching { cr.openOutputStream(uri, "wt") }.getOrNull() ?: cr.openOutputStream(uri, "w")
            ?: throw java.io.IOException("Cannot open output")
            out.use { it.write(json.toByteArray(Charsets.UTF_8)); it.flush() }
        }

        fun readFrom(cr: ContentResolver, uri: Uri, maxBytes: Int = 100 * 1024 * 1024): String {
            val bytes = (cr.openInputStream(uri) ?: throw java.io.IOException("Cannot open input")).use { it.readBytes() }
            if (bytes.size > maxBytes) throw java.io.IOException("File too large")
            return String(bytes, Charsets.UTF_8)
        }

        /** Creates a new backup file in the SAF tree folder and prunes old ones. Returns the file name. */
        fun writeToFolder(cr: ContentResolver, tree: Uri, json: String, now: LocalDateTime = LocalDateTime.now()): String {
            val docId = DocumentsContract.getTreeDocumentId(tree)
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, docId)
            val name = fileName(now)
            val doc = DocumentsContract.createDocument(cr, parent, MIME, name) ?: throw java.io.IOException("Provider refused to create a file")
            try {
                writeTo(cr, doc, json)
            } catch (e: Exception) {
                runCatching { DocumentsContract.deleteDocument(cr, doc) }
                throw e
            }
            runCatching { prune(cr, tree) }
            return name
        }

        fun prune(cr: ContentResolver, tree: Uri, keep: Int = KEEP): Int {
            val docId = DocumentsContract.getTreeDocumentId(tree)
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
            val byName = HashMap<String, String>()
            cr.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
                while (c.moveToNext()) { val id = c.getString(0); val n = c.getString(1); if (id != null && n != null) byName[n] = id }
            }
            var deleted = 0
            for (n in selectForPruning(byName.keys.toList(), keep)) {
                val id = byName[n] ?: continue
                if (runCatching { DocumentsContract.deleteDocument(cr, DocumentsContract.buildDocumentUriUsingTree(tree, id)) }.getOrDefault(false)) deleted++
            }
            return deleted
        }

        fun displayName(cr: ContentResolver, uri: Uri): String? = runCatching {
            cr.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull()

        fun folderLabel(cr: ContentResolver, tree: Uri): String {
            val name = runCatching {
                val doc = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
                cr.query(doc, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
            }.getOrNull() ?: tree.lastPathSegment ?: "?"
            val provider = when {
                tree.authority?.contains("google.android.apps.docs") == true -> "Google Drive"
                tree.authority == "com.android.externalstorage.documents" -> "Storage"
                else -> null
            }
            return if (provider != null) "$provider › $name" else name
        }
    }
}
