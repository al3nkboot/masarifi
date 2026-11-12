package com.midroid.masarifi.data

import com.midroid.masarifi.util.Money
import java.io.InputStream
import java.io.OutputStream
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * CSV export (UTF-8 with BOM so Excel shows Arabic correctly) and re-import of the same format.
 * Savings goals, goal contributions, debts, and debt settlements are included as tagged rows after
 * the transaction rows so older app versions safely ignore them.
 */
object CsvBackup {
    private val HEADER = listOf("Date", "Type", "Account", "To Account", "Category", "Amount", "Currency", "Note", "Merchant", "Auto", "Description")
    private val EXTRA_KINDS = setOf("goal", "goal_contribution", "debt", "debt_settlement")
    private val dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.US)

    suspend fun export(repo: Repository, out: OutputStream, s: AppSettings): Int {
        val txns = repo.txnDao.all()
        val accounts = repo.accountDao.all().associateBy { it.id }
        val cats = repo.categoryDao.all().associateBy { it.id }
        val goals = repo.database.goalDao().allGoals()
        val contributions = repo.database.goalDao().allContributions()
        val debts = repo.database.debtDao().allDebts()
        val settlements = repo.database.debtDao().allSettlements()
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
        if (goals.isNotEmpty() || contributions.isNotEmpty() || debts.isNotEmpty() || settlements.isNotEmpty()) {
            w.write("\r\n")
            w.write("# goals & debts (masarifi 2.7.0)\r\n")
            for (g in goals) {
                val row = listOf(
                    "goal",
                    g.uuid,
                    g.name,
                    Money.toInput(g.target, s.currencyInfo).ifEmpty { "0" },
                    g.targetDate?.let { LocalDate.ofEpochDay(it).toString() } ?: "",
                    g.accountId?.let { accounts[it]?.name } ?: "",
                    g.note,
                )
                w.write(row.joinToString(",") { esc(it) } + "\r\n")
            }
            val goalsById = goals.associateBy { it.id }
            for (c in contributions) {
                val goal = goalsById[c.goalId] ?: continue
                val row = listOf(
                    "goal_contribution",
                    c.uuid,
                    goal.uuid,
                    Money.toInput(c.amount, s.currencyInfo).ifEmpty { "0" },
                    LocalDate.ofEpochDay(c.date).toString(),
                    c.note,
                )
                w.write(row.joinToString(",") { esc(it) } + "\r\n")
            }
            for (d in debts) {
                val row = listOf(
                    "debt",
                    d.uuid,
                    d.person,
                    if (d.direction == DebtDirection.OWE) "owe" else "lent",
                    Money.toInput(d.amount, s.currencyInfo).ifEmpty { "0" },
                    LocalDateTime.ofInstant(Instant.ofEpochMilli(d.dateTime), ZoneId.systemDefault()).format(dtf),
                    d.note,
                )
                w.write(row.joinToString(",") { esc(it) } + "\r\n")
            }
            val debtsById = debts.associateBy { it.id }
            for (settlement in settlements) {
                val debt = debtsById[settlement.debtId] ?: continue
                val row = listOf(
                    "debt_settlement",
                    settlement.uuid,
                    debt.uuid,
                    Money.toInput(settlement.amount, s.currencyInfo).ifEmpty { "0" },
                    LocalDateTime.ofInstant(Instant.ofEpochMilli(settlement.dateTime), ZoneId.systemDefault()).format(dtf),
                )
                w.write(row.joinToString(",") { esc(it) } + "\r\n")
            }
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
            val firstColumn = col(0).trim()
            if (firstColumn.startsWith("#") || firstColumn in EXTRA_KINDS) continue
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

        val goalDao = repo.database.goalDao()
        val debtDao = repo.database.debtDao()
        for (r in rows.drop(1)) {
            fun col(i: Int) = if (i >= 0 && i < r.size) r[i] else ""
            when (col(0).trim()) {
                "goal" -> {
                    val uuid = col(1).trim()
                    if (uuid.isBlank() || goalDao.goalByUuid(uuid) != null) continue
                    val target = runCatching {
                        Money.fromBigDecimal(BigDecimal(col(3).trim()))
                    }.getOrNull() ?: continue
                    val targetDate = col(4).trim().takeIf { it.isNotEmpty() }?.let {
                        runCatching { LocalDate.parse(it).toEpochDay() }.getOrNull()
                    }
                    val accountId = accounts.firstOrNull { it.name == col(5) }?.id
                    goalDao.insertGoal(
                        SavingsGoal(
                            uuid = uuid,
                            name = col(2),
                            target = target,
                            targetDate = targetDate,
                            accountId = accountId,
                            note = col(6),
                        ),
                    )
                }
                "goal_contribution" -> {
                    val uuid = col(1).trim()
                    val goal = goalDao.goalByUuid(col(2).trim()) ?: continue
                    val amount = runCatching {
                        Money.fromBigDecimal(BigDecimal(col(3).trim()))
                    }.getOrNull() ?: continue
                    val date = runCatching {
                        LocalDate.parse(col(4).trim()).toEpochDay()
                    }.getOrNull() ?: continue
                    if (uuid.isBlank() || goalDao.contributionByUuid(uuid) != null) continue
                    goalDao.insertContribution(
                        GoalContribution(
                            uuid = uuid,
                            goalId = goal.id,
                            amount = amount,
                            date = date,
                            note = col(5),
                        ),
                    )
                }
                "debt" -> {
                    val uuid = col(1).trim()
                    if (uuid.isBlank() || debtDao.debtByUuid(uuid) != null) continue
                    val amount = runCatching {
                        Money.fromBigDecimal(BigDecimal(col(4).trim()))
                    }.getOrNull() ?: continue
                    val dateTime = runCatching {
                        LocalDateTime.parse(col(5).trim(), dtf)
                            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    }.getOrNull() ?: continue
                    debtDao.insertDebt(
                        Debt(
                            uuid = uuid,
                            person = col(2),
                            direction = if (col(3).trim() == "owe") DebtDirection.OWE else DebtDirection.LENT,
                            amount = amount,
                            dateTime = dateTime,
                            note = col(6),
                            splitTxnId = null,
                        ),
                    )
                }
                "debt_settlement" -> {
                    val uuid = col(1).trim()
                    val debt = debtDao.debtByUuid(col(2).trim()) ?: continue
                    val amount = runCatching {
                        Money.fromBigDecimal(BigDecimal(col(3).trim()))
                    }.getOrNull() ?: continue
                    val dateTime = runCatching {
                        LocalDateTime.parse(col(4).trim(), dtf)
                            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    }.getOrNull() ?: continue
                    if (uuid.isBlank() || debtDao.settlementByUuid(uuid) != null) continue
                    debtDao.insertSettlement(
                        DebtSettlement(
                            uuid = uuid,
                            debtId = debt.id,
                            amount = amount,
                            dateTime = dateTime,
                            txnId = null,
                        ),
                    )
                }
            }
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
