package com.midroid.masarifi.data

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import com.midroid.masarifi.data.dup.DuplicateDetector
import com.midroid.masarifi.data.dup.SmsMatch
import com.midroid.masarifi.sms.BalanceCheck
import com.midroid.masarifi.sms.CategoryHints
import com.midroid.masarifi.sms.DefaultSmsConfig
import com.midroid.masarifi.sms.WalletTopup
import com.midroid.masarifi.sms.MerchantCategorizer
import com.midroid.masarifi.sms.ParsedSms
import com.midroid.masarifi.sms.SmsParser
import com.midroid.masarifi.sms.SmsTxnType
import com.midroid.masarifi.util.Currencies
import com.midroid.masarifi.util.Money
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class Repository(
    private val db: AppDatabase,
    private val settings: SettingsStore,
    /** Called after expenses change so budget alerts can be re-evaluated. */
    private val onExpensesChanged: () -> Unit = {},
) {
    val txnDao = db.txnDao()
    val accountDao = db.accountDao()
    val categoryDao = db.categoryDao()
    val budgetDao = db.budgetDao()
    val merchantRuleDao = db.merchantRuleDao()
    val recurringDao = db.recurringDao()
    val smsLogDao = db.smsLogDao()
    val dupDao = db.dupDao()
    val smsBalanceDao = db.smsBalanceDao()

    private val categorizer = MerchantCategorizer()
    private val smsMutex = Mutex()
    private val initMutex = Mutex()
    private val zone: ZoneId get() = ZoneId.systemDefault()

    /** Runs [block] inside one Room/SQLite transaction (all-or-nothing). */
    suspend fun <R> inTransaction(block: suspend () -> R): R = db.withTransaction(block)

    /** Notifies listeners (budget checks) after bulk changes such as imports or restores. */
    fun notifyBulkChange() = onExpensesChanged()

    val database: AppDatabase get() = db

    suspend fun ensureDefaults() = initMutex.withLock {
        if (categoryDao.count() == 0) categoryDao.insertAll(Defaults.categories)
        if (accountDao.count() == 0) {
            val ar = settings.current.arabic
            accountDao.insert(Account(name = if (ar) "نقد" else "Cash", groupType = AccountGroup.CASH, uuid = Defaults.CASH_ACCOUNT_UUID, updatedAt = 0))
        }
    }

    /**
     * Inserts an account / category created automatically (SMS, Money Manager or CSV import) with a uuid derived from its
     * name, so two synced devices that create the same one independently merge it instead of duplicating it.
     * Falls back to a random uuid if that id is already taken (e.g. the original was renamed).
     */
    suspend fun insertAutoAccount(a: Account): Long {
        val stable = a.copy(uuid = Defaults.namedUuid("acc-auto-", a.name))
        return if (db.syncDao().account(stable.uuid) == null) accountDao.insert(stable) else accountDao.insert(a)
    }

    suspend fun insertAutoCategory(c: Category): Long {
        val stable = c.copy(uuid = Defaults.namedUuid("cat-auto-${c.type}-", c.nameAr.ifBlank { c.nameEn }))
        return if (db.syncDao().category(stable.uuid) == null) categoryDao.insert(stable) else categoryDao.insert(c)
    }

    // ---------------- transactions ----------------

    /**
     * Saves a new or edited transaction.
     * [repeat] = desired repetition. For new rows null means none; for edited rows null means "leave the repeat rule as is",
     * otherwise the linked rule is created / changed / stopped to match (see [applyRepeatOnEdit]).
     */
    suspend fun saveTxn(txn: Txn, repeat: Int? = null): Long {
        val id: Long
        var catchUp = false
        if (txn.id == 0L) {
            var t = txn
            val r = repeat ?: Repeat.NONE
            if (r != Repeat.NONE) {
                val rid = recurringDao.insert(ruleFrom(t, r))
                t = t.copy(recurringId = rid)
                catchUp = true
            }
            id = txnDao.insert(t)
        } else {
            val old = txnDao.get(txn.id)
            val (t, created) = inTransaction {
                val res = if (repeat == null) txn to false else applyRepeatOnEdit(txn, repeat)
                txnDao.update(res.first)
                res
            }
            catchUp = created
            id = t.id
            // Learn from user corrections of auto-detected merchants
            if (old != null && t.merchant != null && t.categoryId != null && old.categoryId != t.categoryId) {
                learnMerchant(t.merchant, t.categoryId)
            }
        }
        if (catchUp) processRecurring()
        if (txn.type == TxnType.EXPENSE) onExpensesChanged()
        return id
    }

    private fun ruleFrom(t: Txn, frequency: Int, nextDate: Long = advance(t.dateTime, frequency)) = Recurring(
        type = t.type, amount = t.amount, accountId = t.accountId, toAccountId = t.toAccountId,
        categoryId = t.categoryId, note = t.note, frequency = frequency, nextDate = nextDate,
    )

    /** Current repetition of an existing transaction: the frequency of its (still existing) repeat rule, else none. */
    suspend fun repeatOf(txn: Txn): Int = txn.recurringId?.let { recurringDao.get(it)?.frequency } ?: Repeat.NONE

    /**
     * Makes the repeat rule linked to an edited transaction match [repeat] (call inside a transaction).
     *  - none + rule      → the rule is stopped (deleted) and its occurrences are unlinked (they stay as normal entries);
     *  - X + no rule      → a new rule is created starting after this transaction (missed occurrences are caught up);
     *  - X + rule         → the rule takes the new frequency and the edited values for future occurrences
     *                        (next date recomputed from its latest occurrence).
     * Returns the transaction to store and whether a new rule was created.
     */
    suspend fun applyRepeatOnEdit(txn: Txn, repeat: Int): Pair<Txn, Boolean> {
        val rule = txn.recurringId?.let { recurringDao.get(it) }
        return when {
            rule == null && repeat == Repeat.NONE -> txn.copy(recurringId = null) to false
            rule == null -> {
                val rid = recurringDao.insert(ruleFrom(txn, repeat))
                txn.copy(recurringId = rid) to true
            }
            repeat == Repeat.NONE -> {
                txnDao.unlinkRecurring(rule.id)
                recurringDao.delete(rule.id)
                txn.copy(recurringId = null) to false
            }
            else -> {
                val latest = maxOf(txnDao.latestOccurrence(rule.id) ?: txn.dateTime, txn.dateTime)
                val next = if (repeat != rule.frequency) advance(latest, repeat) else rule.nextDate
                recurringDao.update(
                    rule.copy(
                        type = txn.type, amount = txn.amount, accountId = txn.accountId, toAccountId = txn.toAccountId,
                        categoryId = txn.categoryId, note = txn.note, frequency = repeat, nextDate = next,
                    ),
                )
                txn to false
            }
        }
    }

    suspend fun learnMerchant(merchant: String, categoryId: Long) {
        val key = MerchantCategorizer.merchantKey(merchant)
        if (key.isNotBlank()) merchantRuleDao.upsert(MerchantRule(key, categoryId))
    }

    suspend fun deleteTxn(txn: Txn) {
        txnDao.delete(txn)
        if (txn.type == TxnType.EXPENSE) onExpensesChanged()
    }

    // ---------------- bulk delete / undo ----------------

    /** Deletes the given transactions in one DB transaction and returns them (with ids/hashes) for undo. */
    suspend fun deleteTxns(ids: Collection<Long>): List<Txn> {
        if (ids.isEmpty()) return emptyList()
        val list = ids.toList()
        val deleted = inTransaction {
            val rows = list.chunked(500).flatMap { txnDao.byIds(it) }
            list.chunked(500).forEach { txnDao.deleteByIds(it) }
            rows
        }
        if (deleted.any { it.type == TxnType.EXPENSE }) onExpensesChanged()
        return deleted
    }

    /** Restores previously deleted rows exactly (same ids, SMS hashes and import fingerprints). Returns restored count. */
    suspend fun restoreTxns(rows: List<Txn>): Int {
        val n = inTransaction { rows.chunked(500).sumOf { c -> txnDao.restore(c).count { it != -1L } } }
        if (rows.any { it.type == TxnType.EXPENSE }) onExpensesChanged()
        return n
    }

    // ---------------- duplicates ----------------

    /** Undo an SMS link: recreate the SMS as its own transaction and restore the linked one. Returns the new txn id. */
    suspend fun splitLink(linkId: Long): Long? {
        val id = inTransaction {
            val link = dupDao.link(linkId) ?: return@inTransaction null
            dupDao.deleteLink(linkId)
            val newId = txnDao.insert(DuplicateDetector.splitTxn(link))
            txnDao.get(link.txnId)?.let { t ->
                val remaining = dupDao.linksFor(t.id)
                txnDao.update(t.copy(smsLinked = remaining.isNotEmpty() && t.smsLinked, merchant = if (remaining.isEmpty()) link.prevMerchant else t.merchant))
                dupDao.dismiss(listOf(DupDismissed(DupDismissed.key(t.id, newId))))
            }
            newId
        }
        onExpensesChanged()
        return id
    }

    /** Merge a duplicate group (see [DuplicateDetector.planMerge]) in one DB transaction. Returns the kept transaction id. */
    suspend fun mergeGroup(ids: List<Long>): Long? {
        val keeperId = inTransaction {
            val group = txnDao.byIds(ids)
            if (group.size < 2) return@inTransaction null
            val plan = DuplicateDetector.planMerge(group)
            dupDao.repointLinks(plan.deleteIds, plan.keeper.id)
            txnDao.deleteByIds(plan.deleteIds)
            plan.links.forEach { l -> if (dupDao.countLinkHash(l.smsHash) == 0) dupDao.insertLink(l) }
            txnDao.update(plan.keeper)
            plan.keeper.id
        }
        onExpensesChanged()
        return keeperId
    }

    /** "Keep both": never show these transactions as duplicates of each other again. */
    suspend fun keepBoth(ids: List<Long>) = inTransaction {
        dupDao.dismiss(DuplicateDetector.pairKeys(ids).map { DupDismissed(it) })
        txnDao.byIds(ids).filter { it.possibleDupOf != null }.forEach { t ->
            val rest = t.possibleDupOf!!.split(',').mapNotNull { it.trim().toLongOrNull() }.filter { it !in ids }
            txnDao.update(t.copy(possibleDupOf = rest.joinToString(",").ifEmpty { null }))
        }
    }

    /** "دمج" from the add screen: apply the user's account/category/note to the existing SMS entry instead of adding a new one. */
    suspend fun mergeIntoExisting(existingId: Long, draft: Txn): Txn? {
        val ex = txnDao.get(existingId) ?: return null
        val updated = ex.copy(
            accountId = draft.accountId, categoryId = draft.categoryId ?: ex.categoryId,
            note = draft.note.ifBlank { ex.note }, description = draft.description.ifBlank { ex.description },
            possibleDupOf = null,
        )
        txnDao.update(updated)
        if (ex.merchant != null && draft.categoryId != null && draft.categoryId != ex.categoryId) learnMerchant(ex.merchant, draft.categoryId)
        onExpensesChanged()
        return updated
    }

    // ---------------- recurring ----------------

    fun advance(time: Long, frequency: Int): Long {
        val dt = LocalDateTime.ofInstant(Instant.ofEpochMilli(time), zone)
        val next = when (frequency) {
            Repeat.DAILY -> dt.plusDays(1)
            Repeat.WEEKLY -> dt.plusWeeks(1)
            Repeat.MONTHLY -> dt.plusMonths(1)
            Repeat.YEARLY -> dt.plusYears(1)
            else -> dt.plus(100, ChronoUnit.YEARS)
        }
        return next.atZone(zone).toInstant().toEpochMilli()
    }

    /** Creates all due occurrences of repeating transactions (catches up if the app was not opened). */
    suspend fun processRecurring(now: Long = System.currentTimeMillis()): Int = initMutex.withLock {
        var created = 0
        for (r in recurringDao.due(now)) {
            var next = r.nextDate
            var guard = 0
            while (next <= now && guard < 400) {
                // Deterministic uuid: if both synced devices materialize the same occurrence, sync collapses them into one.
                txnDao.insertIgnore(
                    Txn(
                        type = r.type, amount = r.amount, accountId = r.accountId, toAccountId = r.toAccountId,
                        categoryId = r.categoryId, note = r.note, dateTime = next, recurringId = r.id,
                        uuid = "rec-${r.uuid}-$next",
                    ),
                )
                created++
                guard++
                next = advance(next, r.frequency)
            }
            recurringDao.update(r.copy(nextDate = next))
        }
        if (created > 0) onExpensesChanged()
        created
    }

    // ---------------- accounts / categories / budgets ----------------

    suspend fun saveAccount(a: Account) = if (a.id == 0L) accountDao.insert(a) else { accountDao.update(a); a.id }

    suspend fun deleteAccount(a: Account) {
        txnDao.deleteForAccount(a.id)
        smsBalanceDao.deleteForAccount(a.id)
        accountDao.delete(a)
    }

    suspend fun saveCategory(c: Category) = if (c.id == 0L) categoryDao.insert(c) else { categoryDao.update(c); c.id }

    suspend fun deleteCategory(c: Category) {
        txnDao.clearCategory(c.id)
        budgetDao.delete(c.id)
        categoryDao.delete(c)
    }

    suspend fun setBudget(categoryId: Long, amount: Long) {
        if (amount <= 0) budgetDao.delete(categoryId) else budgetDao.upsert(Budget(categoryId, amount))
        onExpensesChanged()
    }

    // ---------------- SMS import ----------------

    fun parser(): SmsParser {
        val s = settings.current
        return SmsParser(DefaultSmsConfig.config(s.extraSenderList, s.smsAcceptUnknown, s.currency, s.walletTopupList))
    }

    /**
     * [linked] = the SMS was attached to an existing manual transaction ([txn] is that transaction, [linkId] the link to undo);
     * [flagged] = created but marked as a possible duplicate.
     */
    data class SmsImportResult(
        val txn: Txn, val categoryName: String, val accountName: String,
        val linked: Boolean = false, val linkId: Long? = null, val flagged: Boolean = false,
        /** The parsed direction was corrected from the balance printed in the SMS (see [BalanceCheck]). */
        val directionFromBalance: Boolean = false,
    )

    /**
     * Parses one SMS and records a transaction if it is a bank transaction that was not seen before.
     * Returns null if ignored or duplicate.
     */
    suspend fun importSms(sender: String, body: String, receivedAt: Long): SmsImportResult? = smsMutex.withLock {
        val parser = parser()
        val hash = parser.hash(sender, body)
        if (smsLogDao.exists(hash) > 0 || txnDao.countByHash(hash) > 0 || dupDao.countLinkHash(hash) > 0) return@withLock null
        val parsed = parser.parse(sender, body) ?: return@withLock null
        val s = settings.current
        val ar = s.arabic

        // A bank transaction id identifies the transaction even if the bank re-sends it with other wording:
        // it becomes the row's unique SMS key ("ref:<bank>:<id>") so the same transaction is never recorded twice.
        val refKey = parsed.ref?.let { "ref:${parsed.bankId ?: "sms"}:$it" }
        if (refKey != null && (txnDao.countByHash(refKey) > 0 || dupDao.countLinkHash(refKey) > 0)) {
            smsLogDao.insert(SmsLog(hash, sender, receivedAt, SmsLog.IGNORED, null))
            return@withLock null
        }
        val rowHash = refKey ?: hash

        // amount + currency conversion
        var amount = Money.fromBigDecimal(parsed.amount)
        var extraNote = ""
        if (parsed.currency != s.currency) {
            val converted = Currencies.convert(amount, parsed.currency, s.currency)
            extraNote = " (${parsed.currency} ${parsed.amount.toPlainString()})"
            if (converted != null) amount = converted
        }
        if (amount <= 0) return@withLock null

        val account = resolveAccount(parsed, ar)
        val time = resolveTime(parsed, receivedAt)

        // Balance printed in the SMS: sanity-check the direction against the previous known balance of this account.
        val balance = parsed.balance?.takeIf { parsed.currency == s.currency }
        var direction = parsed.type
        var flipped = false
        if (balance != null && parsed.topup == null) {
            val prev = smsBalanceDao.latestBefore(account.id, time)
            val r = BalanceCheck.resolve(parsed.type, parsed.amount, prev?.let { java.math.BigDecimal.valueOf(it.balance, 3) }, balance)
            direction = r.type
            flipped = r.flipped
        }
        suspend fun recordBalance() {
            if (balance != null) smsBalanceDao.insert(SmsBalance(accountId = account.id, dateTime = time, balance = Money.fromBigDecimal(balance), smsHash = rowHash))
        }

        // Own-wallet top-up (e.g. bank card → ThawaniPay Topup): a transfer to the wallet, not an expense.
        parsed.topup?.let { topup ->
            val target = resolveTopupTarget(topup, ar)
            if (target.id != account.id) {
                val t = Txn(
                    type = TxnType.TRANSFER, amount = amount, accountId = account.id, toAccountId = target.id,
                    note = topup.name(ar) + extraNote, description = body.take(500), dateTime = time,
                    isAuto = true, smsHash = rowHash, merchant = parsed.merchant,
                )
                val id = try { txnDao.insert(t) } catch (e: SQLiteConstraintException) { return@withLock null }
                smsLogDao.insert(SmsLog(hash, sender, receivedAt, SmsLog.IMPORTED, id))
                recordBalance()
                return@withLock SmsImportResult(t.copy(id = id), "", "${account.name} → ${target.name}")
            }
        }

        val type = if (direction == SmsTxnType.DEBIT) TxnType.EXPENSE else TxnType.INCOME
        val category = resolveCategory(parsed, body, type)
        val label = parsed.merchant ?: (if (ar) bankNameAr(parsed) else parsed.bankName) ?: ""

        var txn = Txn(
            type = type, amount = amount, accountId = account.id, categoryId = category?.id,
            note = label + extraNote, description = body.take(500), dateTime = time,
            isAuto = true, smsHash = rowHash, merchant = parsed.merchant,
        )

        // ---- duplicate detection against manual / imported entries ----
        val nearby = txnDao.between(time - DuplicateDetector.WEAK_WINDOW, time + DuplicateDetector.WEAK_WINDOW + 1)
        when (val m = DuplicateDetector.matchSms(type, amount, time, nearby, s.dupWindowHours)) {
            is SmsMatch.Link -> {
                val target = m.txn
                val r = inTransaction {
                    val linkId = dupDao.insertLink(
                        SmsLink(
                            txnId = target.id, smsHash = rowHash, sender = sender, type = type, amount = amount, accountId = account.id,
                            categoryId = category?.id, note = txn.note, description = txn.description, dateTime = time,
                            merchant = parsed.merchant, cardLast4 = parsed.cardLast4, prevMerchant = target.merchant,
                        ),
                    )
                    val updated = target.copy(smsLinked = true, merchant = target.merchant ?: parsed.merchant)
                    txnDao.update(updated)
                    smsLogDao.insert(SmsLog(hash, sender, receivedAt, SmsLog.IMPORTED, target.id))
                    recordBalance()
                    linkId to updated
                }
                val linkedAccount = accountDao.all().firstOrNull { it.id == r.second.accountId }?.name ?: account.name
                val linkedCat = r.second.categoryId?.let { id -> categoryDao.all().firstOrNull { it.id == id }?.name(ar) } ?: ""
                return@withLock SmsImportResult(r.second, linkedCat, linkedAccount, linked = true, linkId = r.first, directionFromBalance = flipped)
            }
            is SmsMatch.Flag -> txn = txn.copy(possibleDupOf = m.candidates.joinToString(",") { it.id.toString() })
            SmsMatch.None -> {}
        }

        val id = try { txnDao.insert(txn) } catch (e: SQLiteConstraintException) { return@withLock null }
        smsLogDao.insert(SmsLog(hash, sender, receivedAt, SmsLog.IMPORTED, id))
        recordBalance()
        if (type == TxnType.EXPENSE) onExpensesChanged()
        SmsImportResult(txn.copy(id = id), category?.name(ar) ?: "", account.name, flagged = txn.possibleDupOf != null, directionFromBalance = flipped)
    }

    private fun bankNameAr(p: ParsedSms) = DefaultSmsConfig.banks.firstOrNull { it.id == p.bankId }?.nameAr

    private fun profileOf(bankId: String?) = bankId?.let { id -> DefaultSmsConfig.banks.firstOrNull { it.id == id } }

    /**
     * Account for a parsed SMS: an account with the same card/account last 4 digits, else one named after the provider
     * ("بنك مسقط ••0099", "ثواني – بطاقة موجب ••5501", "بنك ظفار ••7001"), created if missing.
     * For wallet providers an existing wallet account without digits (e.g. created by a top-up) is adopted.
     */
    private suspend fun resolveAccount(p: ParsedSms, ar: Boolean): Account {
        val accounts = accountDao.all()
        p.cardLast4?.let { last4 -> accounts.firstOrNull { it.cardLast4 == last4 }?.let { return it } }
        val profile = profileOf(p.bankId)
        val base = profile?.accountName(ar) ?: (if (ar) "بنك" else "Bank")
        if (p.cardLast4 == null) {
            accounts.firstOrNull { it.name == base }?.let { return it }
            if (profile?.wallet == true) walletAccount(profile, accounts)?.let { return it }
        }
        val name = if (p.cardLast4 != null) "$base ••${p.cardLast4}" else base
        accounts.firstOrNull { it.name == name }?.let { return it }
        if (profile?.wallet == true && p.cardLast4 != null) {
            // e.g. "ثواني – بطاقة موجب" created by an earlier top-up → becomes "ثواني – بطاقة موجب ••5501"
            accounts.firstOrNull { it.cardLast4 == null && (it.name == profile.accountName(true) || it.name == profile.accountName(false)) }?.let { w ->
                val adopted = w.copy(name = name, cardLast4 = p.cardLast4)
                accountDao.update(adopted)
                return adopted
            }
        }
        val group = if (profile?.wallet == true) AccountGroup.CARD else AccountGroup.BANK
        val acc = Account(name = name, groupType = group, cardLast4 = p.cardLast4, sortOrder = accounts.size)
        val id = insertAutoAccount(acc)
        return accountDao.all().first { it.id == id }
    }

    /** The wallet account of [profile]: named after it (with or without "••digits"). */
    private fun walletAccount(profile: com.midroid.masarifi.sms.BankProfile, accounts: List<Account>): Account? {
        val names = listOf(profile.accountName(true), profile.accountName(false))
        return accounts.firstOrNull { a -> names.any { a.name == it || a.name.startsWith("$it ••") } }
    }

    /** Target account of an own-wallet top-up: the configured account, else the provider's wallet account (created if missing). */
    suspend fun resolveTopupTarget(t: WalletTopup, ar: Boolean): Account {
        t.targetAccountUuid?.let { u -> db.syncDao().account(u)?.let { return it } }
        val accounts = accountDao.all()
        val profile = profileOf(t.targetBankId)
        if (profile != null) {
            walletAccount(profile, accounts)?.let { return it }
            val group = if (profile.wallet) AccountGroup.CARD else AccountGroup.BANK
            val id = insertAutoAccount(Account(name = profile.accountName(ar), groupType = group, sortOrder = accounts.size))
            return accountDao.all().first { it.id == id }
        }
        val name = t.name(ar)
        accounts.firstOrNull { it.name == name }?.let { return it }
        val id = insertAutoAccount(Account(name = name, groupType = AccountGroup.CARD, sortOrder = accounts.size))
        return accountDao.all().first { it.id == id }
    }

    /** «تحويلات لأشخاص» (money sent to people), created on first use with a fixed uuid so synced phones share it. */
    private suspend fun p2pCategory(): Category {
        categoryDao.all().firstOrNull { it.key == P2P_KEY && it.type == TxnType.EXPENSE }?.let { return it }
        val c = Category(
            type = TxnType.EXPENSE, nameAr = "تحويلات لأشخاص", nameEn = "Transfers to people", emoji = "👤", key = P2P_KEY,
            sortOrder = 15, uuid = Defaults.categoryUuid(P2P_KEY), updatedAt = 0,
        )
        val id = if (db.syncDao().category(c.uuid) == null) categoryDao.insert(c) else categoryDao.insert(c.copy(uuid = newUuid()))
        return categoryDao.all().first { it.id == id }
    }

    private suspend fun resolveCategory(p: ParsedSms, body: String, type: Int): Category? {
        val cats = categoryDao.all()
        p.merchant?.let { m ->
            val rule = merchantRuleDao.get(MerchantCategorizer.merchantKey(m))
            if (rule != null) cats.firstOrNull { it.id == rule.categoryId && it.type == type }?.let { return it }
        }
        if (p.categoryHint == CategoryHints.P2P && type == TxnType.EXPENSE) return p2pCategory()
        val key = categorizer.categorize(p.merchant, body)
        if (key != null) cats.firstOrNull { it.key == key && it.type == type }?.let { return it }
        if (type == TxnType.INCOME && body.lowercase().let { it.contains("refund") || it.contains("استرداد") || it.contains("reversal") }) {
            cats.firstOrNull { it.key == "refund" }?.let { return it }
        }
        val fallbackKey = if (type == TxnType.EXPENSE) "other" else "other_income"
        return cats.firstOrNull { it.key == fallbackKey } ?: cats.firstOrNull { it.type == type }
    }

    companion object {
        const val P2P_KEY = "p2p"
    }

    private fun resolveTime(p: ParsedSms, receivedAt: Long): Long {
        val dt = p.dateTime ?: return receivedAt
        val received = LocalDateTime.ofInstant(Instant.ofEpochMilli(receivedAt), zone)
        // Same day as reception: the receive time is more precise when the SMS had no time.
        if (dt.toLocalDate() == received.toLocalDate()) {
            return if (dt.toLocalTime() == java.time.LocalTime.NOON) receivedAt else dt.atZone(zone).toInstant().toEpochMilli()
        }
        val millis = dt.atZone(zone).toInstant().toEpochMilli()
        val diffDays = (receivedAt - millis) / 86_400_000L
        return if (diffDays in 0..45) millis else receivedAt
    }
}
