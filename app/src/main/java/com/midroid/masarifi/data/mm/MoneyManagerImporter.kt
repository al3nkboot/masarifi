package com.midroid.masarifi.data.mm

import com.midroid.masarifi.data.Account
import com.midroid.masarifi.data.AccountGroup
import com.midroid.masarifi.data.Category
import com.midroid.masarifi.data.Repository
import com.midroid.masarifi.data.Txn
import com.midroid.masarifi.data.TxnType
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

/** What an import would do, shown to the user before committing. */
data class MmPlan(
    val parse: MmParseResult,
    val income: Int,
    val expense: Int,
    val transfer: Int,
    /** Transactions already imported earlier (same hash) – will be skipped. */
    val duplicates: Int,
    val from: LocalDateTime?,
    val to: LocalDateTime?,
    val newAccounts: List<String>,
    /** Pairs of (type, name). */
    val newCategories: List<Pair<Int, String>>,
) {
    val toImport get() = income + expense + transfer
}

data class MmImportResult(
    val income: Int, val expense: Int, val transfer: Int, val duplicates: Int,
    val accountsCreated: Int, val categoriesCreated: Int, val skipped: List<MmSkipped>,
) {
    val imported get() = income + expense + transfer
}

/** Maps parsed Money Manager rows to app entities and writes them in one DB transaction. */
class MoneyManagerImporter(private val repo: Repository, private val zone: ZoneId = ZoneId.systemDefault()) {

    private fun key(s: String) = s.trim().lowercase(Locale.ROOT)

    private val cashAliases = setOf("cash", "نقد", "نقدي", "كاش")

    private fun findAccount(accounts: List<Account>, name: String): Account? {
        val k = key(name)
        return accounts.firstOrNull { key(it.name) == k }
            ?: if (k in cashAliases) accounts.firstOrNull { key(it.name) in cashAliases } else null
    }

    private fun findCategory(cats: List<Category>, type: Int, name: String): Category? {
        val k = key(name)
        return cats.firstOrNull { it.type == type && (key(it.nameEn) == k || key(it.nameAr) == k) }
    }

    private fun txnType(k: MmKind) = when (k) { MmKind.INCOME -> TxnType.INCOME; MmKind.EXPENSE -> TxnType.EXPENSE; MmKind.TRANSFER -> TxnType.TRANSFER }

    fun guessGroup(name: String): Int {
        val n = key(name)
        return when {
            n in cashAliases || n.contains("cash") || n.contains("نقد") -> AccountGroup.CASH
            n.contains("card") || n.contains("بطاقة") || n.contains("visa") || n.contains("credit") -> AccountGroup.CARD
            n.contains("saving") || n.contains("توفير") || n.contains("ادخار") -> AccountGroup.SAVINGS
            n.contains("bank") || n.contains("بنك") || n.contains("مصرف") -> AccountGroup.BANK
            else -> AccountGroup.OTHER
        }
    }

    suspend fun plan(parse: MmParseResult): MmPlan {
        val existing = repo.txnDao.hashesWithPrefix("mm:").toHashSet()
        val fresh = parse.txns.filter { it.hash !in existing }
        val accounts = repo.accountDao.all()
        val cats = repo.categoryDao.all()
        val newAccounts = LinkedHashMap<String, String>()
        val newCats = LinkedHashMap<Pair<Int, String>, Pair<Int, String>>()
        for (t in fresh) {
            for (a in listOfNotNull(t.account, t.toAccount)) {
                if (findAccount(accounts, a) == null) newAccounts.putIfAbsent(key(a), a.trim())
            }
            if (t.kind != MmKind.TRANSFER && t.category.isNotBlank()) {
                val type = txnType(t.kind)
                if (findCategory(cats, type, t.category) == null) newCats.putIfAbsent(type to key(t.category), type to t.category.trim())
            }
        }
        return MmPlan(
            parse = parse,
            income = fresh.count { it.kind == MmKind.INCOME },
            expense = fresh.count { it.kind == MmKind.EXPENSE },
            transfer = fresh.count { it.kind == MmKind.TRANSFER },
            duplicates = parse.txns.size - fresh.size,
            from = parse.txns.minOfOrNull { it.dateTime },
            to = parse.txns.maxOfOrNull { it.dateTime },
            newAccounts = newAccounts.values.toList(),
            newCategories = newCats.values.toList(),
        )
    }

    /** Converts one parsed row into a [Txn] (ids resolved by the caller). */
    fun toTxn(t: MmTxn, accountId: Long, toAccountId: Long?, categoryId: Long?): Txn {
        val desc = buildList {
            if (t.subcategory.isNotBlank()) add(t.subcategory)
            if (t.description.isNotBlank()) add(t.description)
            if (t.originalCurrency != null) add("${t.originalCurrency} ${t.originalAmount ?: ""}".trim())
        }.joinToString(" • ")
        return Txn(
            type = txnType(t.kind), amount = t.amountMilli, accountId = accountId, toAccountId = toAccountId,
            categoryId = categoryId, note = t.note, description = desc,
            dateTime = t.dateTime.atZone(zone).toInstant().toEpochMilli(), smsHash = t.hash,
        )
    }

    suspend fun commit(parse: MmParseResult): MmImportResult {
        val r = repo.inTransaction {
            val existing = repo.txnDao.hashesWithPrefix("mm:").toHashSet()
            val accounts = repo.accountDao.all().toMutableList()
            val cats = repo.categoryDao.all().toMutableList()
            var accCreated = 0
            var catCreated = 0
            suspend fun accountId(name: String): Long {
                findAccount(accounts, name)?.let { return it.id }
                val a = Account(name = name.trim(), groupType = guessGroup(name), sortOrder = accounts.size)
                val id = repo.insertAutoAccount(a)
                accounts += a.copy(id = id); accCreated++
                return id
            }
            suspend fun categoryId(type: Int, name: String): Long? {
                if (name.isBlank() || type == TxnType.TRANSFER) return null
                findCategory(cats, type, name)?.let { return it.id }
                val c = Category(type = type, nameAr = name.trim(), nameEn = name.trim(), emoji = "📦", sortOrder = 50 + cats.size)
                val id = repo.insertAutoCategory(c)
                cats += c.copy(id = id); catCreated++
                return id
            }
            var inc = 0; var exp = 0; var tr = 0; var dup = 0
            val batch = ArrayList<Txn>()
            for (t in parse.txns) {
                if (!existing.add(t.hash)) { dup++; continue }
                val type = txnType(t.kind)
                batch += toTxn(t, accountId(t.account), t.toAccount?.let { accountId(it) }, categoryId(type, t.category))
                when (t.kind) { MmKind.INCOME -> inc++; MmKind.EXPENSE -> exp++; MmKind.TRANSFER -> tr++ }
            }
            batch.chunked(500).forEach { repo.txnDao.insertAll(it) }
            MmImportResult(inc, exp, tr, dup, accCreated, catCreated, parse.skipped)
        }
        if (r.imported > 0) repo.notifyBulkChange()
        return r
    }
}
