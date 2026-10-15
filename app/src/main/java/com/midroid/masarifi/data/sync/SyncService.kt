package com.midroid.masarifi.data.sync

import android.content.ContentResolver
import android.database.sqlite.SQLiteConstraintException
import android.net.Uri
import com.midroid.masarifi.data.Account
import com.midroid.masarifi.data.Budget
import com.midroid.masarifi.data.Category
import com.midroid.masarifi.data.DupDismissed
import com.midroid.masarifi.data.MerchantRule
import com.midroid.masarifi.data.Recurring
import com.midroid.masarifi.data.Repository
import com.midroid.masarifi.data.SettingsStore
import com.midroid.masarifi.data.SmsLink
import com.midroid.masarifi.data.SmsLog
import com.midroid.masarifi.data.SyncTombstone
import com.midroid.masarifi.data.Txn
import com.midroid.masarifi.data.backup.BackupService
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The shared sync file. */
interface SyncFile {
    /** Current content, or null/blank for a new empty file. */
    fun read(): String?
    fun write(text: String)
}

/** A sync file picked with the Storage Access Framework (e.g. on Google Drive). */
class SafSyncFile(private val cr: ContentResolver, private val uri: Uri) : SyncFile {
    override fun read(): String? = BackupService.readFrom(cr, uri)
    override fun write(text: String) = BackupService.writeTo(cr, uri, text)
}

class SyncMismatchException(val fileId: String) : Exception("Different sync file")

data class SyncReport(val stats: SyncStats, val wrote: Boolean, val fileId: String, val devices: Map<String, DeviceInfo>)

/**
 * Pull → merge → apply → push. The merge is computed and applied inside one DB transaction; the merged
 * snapshot is then written back to the same file (only when it gained something).
 */
class SyncService(
    private val repo: Repository,
    private val settings: SettingsStore,
    private val store: SyncStore,
    private val appVersion: () -> String,
) {
    private val db get() = repo.database
    private val dao get() = db.syncDao()
    private val mutex = Mutex()

    suspend fun localMark(): Long = dao.localMark() ?: 0L

    suspend fun sync(file: SyncFile, trigger: String, acceptOtherFile: Boolean = false, now: () -> Long = System::currentTimeMillis): SyncReport =
        mutex.withLock {
            val st = store.current
            try {
                val remote = SyncCodec.decode(file.read())
                if (!acceptOtherFile && st.fileId != null && remote.fileId != null && remote.fileId != st.fileId) {
                    throw SyncMismatchException(remote.fileId)
                }
                val result = repo.inTransaction {
                    val local = exportLocal()
                    val r = SyncMerge.merge(local, remote, st.lastSyncAt)
                    applyLocal(r.toApply, remote)
                    r
                }
                val t = now()
                val fileId = result.merged.fileId ?: java.util.UUID.randomUUID().toString()
                val me = DeviceInfo(st.deviceName, t, appVersion())
                val prevMe = remote.devices[st.deviceId]
                val out = result.merged.copy(fileId = fileId, devices = result.merged.devices + (st.deviceId to me), updatedAt = t)
                val write = remote.fileId == null || SyncMerge.needsPush(result.merged, remote) || prevMe?.name != st.deviceName
                if (write) file.write(SyncCodec.encode(out))
                val mark = localMark()
                val stats = result.stats
                store.update {
                    it.copy(
                        fileId = fileId, lastAt = t, lastOk = true, lastError = null, lastSyncAt = t, lastMark = mark,
                        devices = (if (write) out.devices else remote.devices).filterKeys { k -> k != st.deviceId },
                    )
                }
                store.addLog(SyncLogEntry(t, true, trigger, stats.pulled, if (write) stats.pushed else 0, stats.merged, stats.conflicts))
                if (stats.pulled > 0) repo.notifyBulkChange()
                SyncReport(stats, write, fileId, out.devices)
            } catch (e: Exception) {
                val t = now()
                val msg = when (e) {
                    is SyncFormatException -> e.code
                    is SyncMismatchException -> "other_file"
                    is SecurityException -> "no_access"
                    else -> e.message ?: e.javaClass.simpleName
                }
                store.update { it.copy(lastAt = t, lastOk = false, lastError = msg) }
                store.addLog(SyncLogEntry(t, false, trigger, error = msg))
                throw e
            }
        }

    // ---------------------------------------------------------------- local → snapshot

    suspend fun exportLocal(): SyncSnapshot {
        val b = db.backupDao()
        val cats = b.categories(); val accs = b.accounts(); val recs = b.recurring(); val txns = b.transactions()
        val links = b.smsLinks(); val budgets = b.budgets(); val rules = b.merchantRules()
        val catU = cats.associate { it.id to it.uuid }
        val catById = cats.associateBy { it.id }
        /** Category name/key carried with a reference so the other phone can re-link it (see [CategoryLinks]). */
        fun catInfo(id: Long?): Array<Pair<String, Any?>> = catById[id]?.let { c ->
            arrayOf("catKey" to c.key, "catName" to c.nameAr, "catNameEn" to c.nameEn, "catType" to c.type)
        } ?: emptyArray()
        val accU = accs.associate { it.id to it.uuid }
        val recU = recs.associate { it.id to it.uuid }
        val txnU = txns.associate { it.id to it.uuid }
        fun acc(id: Long?) = id?.let { accU[it] ?: "missing-account-$it" }
        val out = LinkedHashMap<String, SyncRecord>()
        fun add(r: SyncRecord) { out[r.uuid] = r }

        cats.forEach { c ->
            add(SyncRecord.live(c.uuid, Kind.CATEGORY, c.updatedAt, "type" to c.type, "nameAr" to c.nameAr, "nameEn" to c.nameEn,
                "emoji" to c.emoji, "key" to c.key, "sortOrder" to c.sortOrder))
        }
        accs.forEach { a ->
            add(SyncRecord.live(a.uuid, Kind.ACCOUNT, a.updatedAt, "name" to a.name, "groupType" to a.groupType, "initialBalance" to a.initialBalance,
                "cardLast4" to a.cardLast4, "includeInTotal" to a.includeInTotal, "sortOrder" to a.sortOrder))
        }
        recs.forEach { r ->
            add(SyncRecord.live(r.uuid, Kind.RECURRING, r.updatedAt, "type" to r.type, "amount" to r.amount, "account" to acc(r.accountId),
                "toAccount" to acc(r.toAccountId), "category" to r.categoryId?.let { catU[it] }, "note" to r.note, "frequency" to r.frequency,
                "nextDate" to r.nextDate, "active" to r.active, *catInfo(r.categoryId)))
        }
        txns.forEach { t ->
            add(SyncRecord.live(t.uuid, Kind.TXN, t.updatedAt, "type" to t.type, "amount" to t.amount, "account" to acc(t.accountId),
                "toAccount" to acc(t.toAccountId), "category" to t.categoryId?.let { catU[it] }, "note" to t.note, "description" to t.description,
                "dateTime" to t.dateTime, "isAuto" to t.isAuto, "smsHash" to t.smsHash, "merchant" to t.merchant,
                "recurring" to t.recurringId?.let { recU[it] }, "createdAt" to t.createdAt, "smsLinked" to t.smsLinked,
                "possibleDupOf" to t.possibleDupOf?.split(',')?.mapNotNull { it.trim().toLongOrNull()?.let { id -> txnU[id] } }?.joinToString(",")?.ifEmpty { null }, *catInfo(t.categoryId)))
        }
        links.forEach { l ->
            add(SyncRecord.live(l.uuid, Kind.SMS_LINK, l.updatedAt, "txn" to (txnU[l.txnId] ?: "missing-txn-${l.txnId}"), "smsHash" to l.smsHash,
                "sender" to l.sender, "type" to l.type, "amount" to l.amount, "account" to acc(l.accountId), "category" to l.categoryId?.let { catU[it] },
                "note" to l.note, "description" to l.description, "dateTime" to l.dateTime, "merchant" to l.merchant, "cardLast4" to l.cardLast4,
                "prevMerchant" to l.prevMerchant, "createdAt" to l.createdAt, *catInfo(l.categoryId)))
        }
        budgets.forEach { bu ->
            val cu = if (bu.categoryId == 0L) null else catU[bu.categoryId] ?: return@forEach
            add(SyncRecord.live(budgetUuid(cu), Kind.BUDGET, bu.updatedAt, "category" to cu, "amount" to bu.amount))
        }
        rules.forEach { m ->
            val cu = catU[m.categoryId] ?: return@forEach
            add(SyncRecord.live("m:${m.merchantKey}", Kind.MERCHANT, m.updatedAt, "merchantKey" to m.merchantKey, "category" to cu, *catInfo(m.categoryId)))
        }
        val s = settings.current
        add(SyncRecord.live(SETTINGS_UUID, Kind.SETTINGS, settings.syncedUpdatedAt, "currency" to s.currency, "weekStartsSaturday" to s.weekStartsSaturday,
            "dupWindowHours" to s.dupWindowHours, "budgetAlerts" to s.budgetAlerts, "spikeAlerts" to s.spikeAlerts))
        dao.tombstones().forEach { t -> if (t.uuid !in out) add(SyncRecord(t.uuid, t.entity, t.deletedAt, t.deletedAt)) }

        val smsLog = b.smsLog().associate { it.hash to SmsLogEntry(it.sender, it.receivedAt, it.status, it.txnId?.let { id -> txnU[id] }) }
        val dismissed = b.dupDismissed().mapNotNull { d ->
            val (a, c) = d.pairKey.split('-').mapNotNull { it.toLongOrNull() }.takeIf { it.size == 2 } ?: return@mapNotNull null
            val ua = txnU[a] ?: return@mapNotNull null; val uc = txnU[c] ?: return@mapNotNull null
            SyncSnapshot.pairKey(ua, uc)
        }.toSet()
        return SyncSnapshot(out, smsLog, dismissed)
    }

    // ---------------------------------------------------------------- merged → local

    /** Applies merged records to the local DB (call inside a transaction). Returns the number of skipped records. */
    suspend fun applyLocal(records: List<SyncRecord>, remote: SyncSnapshot): Int {
        var skipped = 0
        val byKind = records.groupBy { it.kind }
        for (kind in Kind.APPLY_ORDER.reversed()) byKind[kind].orEmpty().filter { it.deleted }.forEach { applyDelete(it) }
        val pendingDup = ArrayList<Pair<Long, String>>()
        for (kind in Kind.APPLY_ORDER) {
            byKind[kind].orEmpty().filter { !it.deleted }.forEach { r ->
                val ok = try { applyLive(r, pendingDup) } catch (e: SQLiteConstraintException) { false }
                if (!ok) skipped++
            }
        }
        // possibleDupOf references other transactions → resolve after all inserts
        for ((id, uuids) in pendingDup) {
            val t = repo.txnDao.get(id) ?: continue
            val ids = uuids.split(',').mapNotNull { dao.txn(it.trim())?.id }.joinToString(",").ifEmpty { null }
            if (ids != t.possibleDupOf) {
                // two writes with distinct updatedAt values so the UPDATE trigger keeps the merged timestamp
                dao.updateTxn(t.copy(possibleDupOf = ids, updatedAt = t.updatedAt + 1))
                dao.updateTxn(t.copy(possibleDupOf = ids))
            }
        }
        // grow-only sets
        for ((hash, e) in remote.smsLog) {
            if (repo.smsLogDao.exists(hash) == 0) repo.smsLogDao.insert(SmsLog(hash, e.sender, e.receivedAt, e.status, e.txnUuid?.let { dao.txn(it)?.id }))
        }
        val pairs = remote.dismissed.mapNotNull { k ->
            val (a, b) = k.split('|').takeIf { it.size == 2 } ?: return@mapNotNull null
            val ia = dao.txn(a)?.id ?: return@mapNotNull null; val ib = dao.txn(b)?.id ?: return@mapNotNull null
            DupDismissed(DupDismissed.key(ia, ib))
        }
        if (pairs.isNotEmpty()) repo.dupDao.dismiss(pairs)
        return skipped
    }

    private suspend fun categoryIdFor(budgetOrRuleUuid: String, prefix: String): Long? {
        val cu = budgetOrRuleUuid.removePrefix(prefix)
        return if (cu == "overall") 0L else dao.category(cu)?.id
    }

    private suspend fun applyDelete(r: SyncRecord) {
        when (r.kind) {
            Kind.TXN -> dao.deleteTxn(r.uuid)
            Kind.SMS_LINK -> dao.deleteSmsLink(r.uuid)
            Kind.RECURRING -> dao.recurring(r.uuid)?.let { dao.clearTxnRecurring(it.id); dao.deleteRecurring(r.uuid) }
            Kind.ACCOUNT -> dao.deleteAccount(r.uuid)
            Kind.CATEGORY -> dao.category(r.uuid)?.let { c ->
                dao.clearTxnCategory(c.id); dao.clearRecurringCategory(c.id); repo.budgetDao.delete(c.id); dao.deleteCategory(r.uuid)
            }
            Kind.BUDGET -> categoryIdFor(r.uuid, "b:")?.let { repo.budgetDao.delete(it) }
            Kind.MERCHANT -> repo.merchantRuleDao.delete(r.uuid.removePrefix("m:"))
        }
        // keep the original deletion time (the delete trigger wrote "now")
        dao.putTombstone(SyncTombstone(r.uuid, r.kind, r.deletedAt!!))
    }

    private suspend fun applyLive(r: SyncRecord, pendingDup: MutableList<Pair<Long, String>>): Boolean {
        dao.dropTombstone(r.uuid)
        suspend fun accId(k: String) = r.str(k)?.let { dao.account(it)?.id }
        suspend fun catId(k: String = "category"): Long? {
            val ref = r.str(k) ?: return null
            dao.category(ref)?.let { return it.id }
            // unknown id: same category by key / name (an older peer may not have re-linked it)
            val type = (r.long("catType") ?: r.long("type"))?.toInt()
            val all = repo.categoryDao.all().filter { type == null || it.type == type }
            val key = r.str("catKey") ?: ref.takeIf { it.startsWith("cat-") && !it.startsWith("cat-auto-") }?.removePrefix("cat-")
            key?.let { kk -> all.firstOrNull { it.key == kk } }?.let { return it.id }
            val n = r.str("catName")?.trim(); val ne = r.str("catNameEn")?.trim()
            return all.firstOrNull { c -> (n != null && c.nameAr.trim() == n) || (ne != null && c.nameEn.trim().equals(ne, true)) }?.id
        }
        fun int(k: String) = (r.long(k) ?: 0L).toInt()
        when (r.kind) {
            Kind.CATEGORY -> {
                val ex = dao.category(r.uuid)
                val c = Category(ex?.id ?: 0, int("type"), r.str("nameAr") ?: "", r.str("nameEn") ?: "", r.str("emoji") ?: "📦", r.str("key"), int("sortOrder"), r.uuid, r.updatedAt)
                if (ex != null) dao.updateCategory(c) else dao.insertCategory(c)
            }
            Kind.ACCOUNT -> {
                val ex = dao.account(r.uuid)
                val a = Account(ex?.id ?: 0, r.str("name") ?: "", int("groupType"), r.long("initialBalance") ?: 0, r.str("cardLast4"),
                    r.bool("includeInTotal") ?: true, int("sortOrder"), r.uuid, r.updatedAt)
                if (ex != null) dao.updateAccount(a) else dao.insertAccount(a)
            }
            Kind.RECURRING -> {
                val acc = accId("account") ?: return false
                val ex = dao.recurring(r.uuid)
                val x = Recurring(ex?.id ?: 0, int("type"), r.long("amount") ?: 0, acc, accId("toAccount"), catId(), r.str("note") ?: "",
                    int("frequency"), r.long("nextDate") ?: 0, r.bool("active") ?: true, r.uuid, r.updatedAt)
                if (ex != null) dao.updateRecurring(x) else dao.insertRecurring(x)
            }
            Kind.TXN -> {
                val acc = accId("account") ?: return false
                val ex = dao.txn(r.uuid)
                val t = Txn(
                    id = ex?.id ?: 0, type = int("type"), amount = r.long("amount") ?: 0, accountId = acc, toAccountId = accId("toAccount"),
                    categoryId = catId(), note = r.str("note") ?: "", description = r.str("description") ?: "", dateTime = r.long("dateTime") ?: 0,
                    isAuto = r.bool("isAuto") ?: false, smsHash = r.str("smsHash"), merchant = r.str("merchant"),
                    recurringId = r.str("recurring")?.let { dao.recurring(it)?.id }, createdAt = r.long("createdAt") ?: r.updatedAt,
                    smsLinked = r.bool("smsLinked") ?: false, possibleDupOf = ex?.possibleDupOf, uuid = r.uuid, updatedAt = r.updatedAt,
                )
                val id = if (ex != null) { dao.updateTxn(t); ex.id } else dao.insertTxn(t)
                val dup = r.str("possibleDupOf")
                if (dup != null || ex?.possibleDupOf != null) pendingDup += id to (dup ?: "")
            }
            Kind.SMS_LINK -> {
                val txnId = r.str("txn")?.let { dao.txn(it)?.id } ?: return false
                val ex = dao.smsLink(r.uuid)
                val l = SmsLink(ex?.id ?: 0, txnId, r.str("smsHash") ?: return false, r.str("sender") ?: "", int("type"), r.long("amount") ?: 0,
                    accId("account") ?: 0, catId(), r.str("note") ?: "", r.str("description") ?: "", r.long("dateTime") ?: 0, r.str("merchant"),
                    r.str("cardLast4"), r.str("prevMerchant"), r.long("createdAt") ?: r.updatedAt, r.uuid, r.updatedAt)
                if (ex != null) dao.updateSmsLink(l) else dao.insertSmsLink(l)
            }
            Kind.BUDGET -> {
                val cid = categoryIdFor(r.uuid, "b:") ?: return false
                repo.budgetDao.upsert(Budget(cid, r.long("amount") ?: 0, r.updatedAt))
            }
            Kind.MERCHANT -> {
                val cid = catId() ?: return false
                repo.merchantRuleDao.upsert(MerchantRule(r.str("merchantKey") ?: r.uuid.removePrefix("m:"), cid, r.updatedAt))
            }
            Kind.SETTINGS -> settings.applySynced(r.updatedAt) { s ->
                s.copy(
                    currency = r.str("currency") ?: s.currency, weekStartsSaturday = r.bool("weekStartsSaturday") ?: s.weekStartsSaturday,
                    dupWindowHours = r.long("dupWindowHours")?.toInt() ?: s.dupWindowHours, budgetAlerts = r.bool("budgetAlerts") ?: s.budgetAlerts,
                    spikeAlerts = r.bool("spikeAlerts") ?: s.spikeAlerts,
                )
            }
            else -> return false // unknown kind from a newer app version: kept in the file, ignored locally
        }
        return true
    }

    companion object {
        const val SETTINGS_UUID = "settings"
        const val FILE_NAME = "masarifi-sync.json"
        fun budgetUuid(categoryUuid: String?) = "b:" + (categoryUuid ?: "overall")
    }
}
