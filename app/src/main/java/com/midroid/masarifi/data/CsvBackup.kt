package com.midroid.masarifi.data

import com.midroid.masarifi.util.Money
import java.io.InputStream
import java.io.OutputStream
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** CSV export (UTF-8 with BOM so Excel shows Arabic correctly) and re-import of the same format. */
object CsvBackup {
    private val HEADER = listOf("Date", "Type", "Account", "To Account", "Category", "Amount", "Currency", "Note", "Merchant", "Auto", "Description")
    private val dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.US)

    suspend fun export(repo: Repository, out: OutputStream, s: AppSettings): Int {
        val txns = repo.txnDao.all()
        val accounts = repo.accountDao.all().associateBy { it.id }
        val cats = repo.categoryDao.all().associateBy { it.id }
        val w = out.bufferedWriter(Charsets.UTF_8)
        w.write("\uFEFF")
        w.write(HEADER.joinToString(",") + "\r\n")
        for (t in txns) {
            val row = listOf(
                LocalDateTime.ofInstant(Instant.ofEpochMilli(t.dateTime), ZoneId.systemDefault()).format(dtf),
                when (t.type) { TxnType.INCOME -> "Income"; TxnType.EXPENSE -> "Expense"; else -> "Transfer" },
                accounts[t.accountId]?.name ?: "",
                t.toAccountId?.let { accounts[it]?.name } ?: "",
                t.categoryId?.let { cats[it]?.name(s.arabic) } ?: "",
                Money.toInput(t.amount, s.currencyInfo).ifEmpty { "0" },
                s.currency,
                t.note,
                t.merchant ?: "",
                if (t.isAuto) "1" else "0",
                t.description,
            )
            w.write(row.joinToString(",") { esc(it) } + "\r\n")
        }
        w.flush()
        return txns.size
    }

    suspend fun import(repo: Repository, input: InputStream, s: AppSettings): Int {
        val text = input.bufferedReader(Charsets.UTF_8).readText().removePrefix("\uFEFF")
        val rows = parse(text)
        if (rows.isEmpty()) return 0
        val header = rows.first().map { it.trim().lowercase() }
        fun idx(name: String) = header.indexOf(name.lowercase())
        val iDate = idx("Date"); val iType = idx("Type"); val iAcc = idx("Account"); val iTo = idx("To Account")
        val iCat = idx("Category"); val iAmt = idx("Amount"); val iNote = idx("Note"); val iMer = idx("Merchant")
        val iAuto = idx("Auto"); val iDesc = idx("Description")
        if (iDate < 0 || iType < 0 || iAmt < 0) return -1

        val accounts = repo.accountDao.all().toMutableList()
        val cats = repo.categoryDao.all().toMutableList()
        val existing = repo.txnDao.all().map { Triple(it.dateTime, it.amount, it.note) }.toHashSet()
        var count = 0
        suspend fun account(name: String): Long {
            if (name.isBlank()) return accounts.first().id
            accounts.firstOrNull { it.name == name }?.let { return it.id }
            val a = Account(name = name, groupType = AccountGroup.OTHER, sortOrder = accounts.size)
            val id = repo.insertAutoAccount(a)
            accounts += a.copy(id = id)
            return id
        }
        suspend fun category(name: String, type: Int): Long? {
            if (name.isBlank() || type == TxnType.TRANSFER) return null
            cats.firstOrNull { it.type == type && (it.nameAr == name || it.nameEn.equals(name, true)) }?.let { return it.id }
            val c = Category(type = type, nameAr = name, nameEn = name, emoji = "📦", sortOrder = 50)
            val id = repo.insertAutoCategory(c)
            cats += c.copy(id = id)
            return id
        }
        for (r in rows.drop(1)) {
            fun col(i: Int) = if (i >= 0 && i < r.size) r[i] else ""
            val dt = runCatching { LocalDateTime.parse(col(iDate).trim(), dtf) }.getOrNull() ?: continue
            val type = when (col(iType).trim().lowercase()) { "income" -> TxnType.INCOME; "expense" -> TxnType.EXPENSE; "transfer" -> TxnType.TRANSFER; else -> continue }
            val amount = runCatching { Money.fromBigDecimal(BigDecimal(col(iAmt).trim())) }.getOrNull() ?: continue
            val millis = dt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val note = col(iNote)
            if (!existing.add(Triple(millis, amount, note))) continue
            repo.txnDao.insert(
                Txn(
                    type = type, amount = amount, accountId = account(col(iAcc)),
                    toAccountId = if (type == TxnType.TRANSFER) account(col(iTo)) else null,
                    categoryId = category(col(iCat), type), note = note, description = col(iDesc),
                    dateTime = millis, isAuto = col(iAuto) == "1", merchant = col(iMer).ifBlank { null },
                ),
            )
            count++
        }
        return count
    }

    private fun esc(v: String): String =
        if (v.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + v.replace("\"", "\"\"") + "\"" else v

    /** Minimal RFC-4180 CSV parser. */
    fun parse(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') { field.append('"'); i++ } else inQuotes = false
                } else field.append(c)
            } else when (c) {
                '"' -> inQuotes = true
                ',' -> { row.add(field.toString()); field.clear() }
                '\r' -> {}
                '\n' -> { row.add(field.toString()); field.clear(); rows.add(row); row = mutableListOf() }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) { row.add(field.toString()); rows.add(row) }
        return rows.filter { r -> r.any { it.isNotBlank() } }
    }
}
