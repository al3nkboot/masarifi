package com.midroid.masarifi.data.mm

import java.io.ByteArrayInputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.util.Locale

enum class MmKind { INCOME, EXPENSE, TRANSFER }

enum class MmSkipReason { BAD_DATE, BAD_AMOUNT, UNKNOWN_TYPE, NO_ACCOUNT, TRANSFER_NO_TARGET }

/** One transaction parsed from a Money Manager export (a transfer may come from two rows: Transfer-Out + Transfer-In). */
data class MmTxn(
    /** 1-based spreadsheet row numbers this transaction was built from. */
    val rows: List<Int>,
    val kind: MmKind,
    val dateTime: LocalDateTime,
    /** Amount in thousandths, always positive. */
    val amountMilli: Long,
    val account: String,
    /** Destination account for transfers. */
    val toAccount: String? = null,
    val category: String = "",
    val subcategory: String = "",
    val note: String = "",
    val description: String = "",
    /** Original currency/amount when it differs from the main currency column. */
    val originalCurrency: String? = null,
    val originalAmount: String? = null,
    /** Stable duplicate-protection hash ("mm:" + sha256), filled by the parser. */
    val hash: String = "",
) {
    fun dedupKey(): String = listOf(dateTime.toString(), amountMilli.toString(), account, toAccount ?: "", category, note).joinToString("\u001F")
}

data class MmSkipped(val row: Int, val reason: MmSkipReason, val detail: String = "")

data class MmParseResult(
    /** Number of non-blank data rows below the header. */
    val dataRows: Int,
    val txns: List<MmTxn>,
    val skipped: List<MmSkipped>,
    /** Currency code of the main amount column (e.g. "OMR"), if present. */
    val mainCurrency: String?,
    val headerRow: Int,
) {
    /** Rows consumed by parsed transactions (a paired transfer consumes 2). */
    val consumedRows: Int get() = txns.sumOf { it.rows.size }
}

class MmFormatException(val code: String, msg: String) : Exception(msg)

/** Parses Money Manager (Realbyte) exports: .xlsx, CSV/TSV, and HTML-table ".xls". Pure Kotlin, no Android APIs. */
object MoneyManagerParser {

    fun parseFile(bytes: ByteArray): MmParseResult = parseRows(readTable(bytes))

    fun readTable(bytes: ByteArray): List<List<SheetCell>> {
        if (bytes.size >= 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) {
            return XlsxReader.readFirstSheet(bytes)
        }
        if (bytes.size >= 4 && (bytes[0].toInt() and 0xFF) == 0xD0 && (bytes[1].toInt() and 0xFF) == 0xCF) {
            throw MmFormatException("legacy_xls", "Legacy binary .xls is not supported; export as .xlsx or CSV")
        }
        val text = decodeText(bytes)
        val trimmed = text.trimStart()
        if (trimmed.startsWith("<")) {
            val rows = parseHtmlTable(text)
            if (rows.isNotEmpty()) return rows
            throw MmFormatException("unknown", "Unrecognised file")
        }
        return parseDelimited(text).map { r -> r.map { SheetCell(it) } }
    }

    private fun decodeText(bytes: ByteArray): String {
        if (bytes.size >= 2 && (bytes[0].toInt() and 0xFF) == 0xFF && (bytes[1].toInt() and 0xFF) == 0xFE) return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        if (bytes.size >= 2 && (bytes[0].toInt() and 0xFF) == 0xFE && (bytes[1].toInt() and 0xFF) == 0xFF) return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        return String(bytes, Charsets.UTF_8).removePrefix("\uFEFF")
    }

    /** RFC-4180 style parser with auto-detected delimiter (tab, comma or semicolon). */
    fun parseDelimited(text: String): List<List<String>> {
        val firstLine = text.lineSequence().firstOrNull { it.isNotBlank() } ?: return emptyList()
        val delim = listOf('\t', ',', ';').maxByOrNull { d -> firstLine.count { it == d } } ?: ','
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
                '"' -> if (field.isEmpty()) inQuotes = true else field.append(c)
                delim -> { row.add(field.toString()); field.clear() }
                '\r' -> {}
                '\n' -> { row.add(field.toString()); field.clear(); rows.add(row); row = mutableListOf() }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) { row.add(field.toString()); rows.add(row) }
        return rows
    }

    private fun parseHtmlTable(html: String): List<List<SheetCell>> {
        val rows = mutableListOf<List<SheetCell>>()
        val tr = Regex("<tr[^>]*>(.*?)</tr>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val td = Regex("<t[dh][^>]*>(.*?)</t[dh]>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        for (m in tr.findAll(html)) {
            rows += td.findAll(m.groupValues[1]).map { c ->
                SheetCell(
                    c.groupValues[1].replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n").replace(Regex("<[^>]+>"), "")
                        .replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&").trim(),
                )
            }.toList()
        }
        return rows
    }

    // ---------------- header / columns ----------------

    private fun norm(s: String) = s.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")

    private val DATE_H = setOf("date", "period", "time", "التاريخ", "الفترة")
    private val ACCOUNT_H = setOf("account", "accounts", "asset", "assets", "الحساب", "الحسابات")
    private val CATEGORY_H = setOf("category", "الفئة", "التصنيف")
    private val SUBCAT_H = setOf("subcategory", "sub category", "sub-category", "الفئة الفرعية")
    private val NOTE_H = setOf("note", "notes", "contents", "memo", "ملاحظة", "الملاحظة", "ملاحظات")
    private val TYPE_H = setOf("income/expense", "income/expenses", "type", "inc./exp.", "in/ex", "النوع", "دخل/مصروف")
    private val DESC_H = setOf("description", "الوصف")
    private val AMOUNT_H = setOf("amount", "المبلغ")
    private val CURRENCY_H = setOf("currency", "العملة")
    private val CODE = Regex("^[A-Z]{3}$")

    private class Cols(
        val date: Int, val account: Int, val category: Int, val subcategory: Int, val note: Int, val type: Int,
        val description: Int, val main: Int, val amount: Int, val currency: Int, val mainCurrency: String?,
    )

    private fun findColumns(header: List<SheetCell>): Cols? {
        val h = header.map { norm(it.text) }
        fun find(names: Set<String>) = h.indexOfFirst { it in names }
        val date = find(DATE_H)
        val type = find(TYPE_H)
        if (date < 0 || type < 0) return null
        val main = header.indexOfFirst { CODE.matches(it.text.trim()) }
        return Cols(
            date = date, account = find(ACCOUNT_H), category = find(CATEGORY_H), subcategory = find(SUBCAT_H), note = find(NOTE_H),
            type = type, description = find(DESC_H), main = main, amount = find(AMOUNT_H), currency = find(CURRENCY_H),
            mainCurrency = if (main >= 0) header[main].text.trim() else null,
        )
    }

    // ---------------- values ----------------

    private enum class RowType { INCOME, EXPENSE, TRANSFER, TRANSFER_OUT, TRANSFER_IN }

    private fun rowType(s: String): RowType? {
        val t = norm(s).replace('_', '-')
        return when (t) {
            "income", "inc", "inc.", "دخل", "إيراد", "ايراد" -> RowType.INCOME
            "expense", "expenses", "exp", "exp.", "مصروف", "مصاريف", "نفقة", "نفقات" -> RowType.EXPENSE
            "transfer-out", "transfer out", "transferout", "trans-out", "تحويل-صادر", "تحويل صادر" -> RowType.TRANSFER_OUT
            "transfer-in", "transfer in", "transferin", "trans-in", "تحويل-وارد", "تحويل وارد" -> RowType.TRANSFER_IN
            "transfer", "trans", "تحويل" -> RowType.TRANSFER
            else -> null
        }
    }

    private val datePatterns: List<DateTimeFormatter> = listOf(
        "M/d/yyyy H:mm:ss", "M/d/yyyy H:mm", "M/d/yyyy", "yyyy-MM-dd H:mm:ss", "yyyy-MM-dd H:mm", "yyyy-MM-dd",
        "yyyy/MM/dd H:mm:ss", "yyyy/MM/dd H:mm", "yyyy/MM/dd", "d.M.yyyy H:mm:ss", "d.M.yyyy H:mm", "d.M.yyyy",
        "M/d/yyyy h:mm:ss a", "M/d/yyyy h:mm a",
    ).map { p ->
        DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(p)
            .optionalStart().appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true).optionalEnd()
            .toFormatter(Locale.US)
    }

    internal fun parseDate(cell: SheetCell): LocalDateTime? {
        val raw = cell.text.trim()
        if (raw.isEmpty()) return null
        if (cell.isDate) {
            runCatching { return LocalDateTime.parse(raw) }
            runCatching { return LocalDate.parse(raw).atStartOfDay() }
        }
        if (cell.isNumber) raw.toDoubleOrNull()?.let { if (it in 1.0..2_958_465.0) return XlsxReader.serialToDateTime(it) }
        val s = toLatinDigits(raw)
        runCatching { return LocalDateTime.parse(s) }
        for (f in datePatterns) {
            val r = runCatching { f.parseBest(s, LocalDateTime::from, LocalDate::from) }.getOrNull() ?: continue
            return when (r) { is LocalDateTime -> r; is LocalDate -> r.atStartOfDay(); else -> null }
        }
        return null
    }

    private fun toLatinDigits(s: String): String = buildString {
        for (ch in s) when (ch) {
            in '\u0660'..'\u0669' -> append('0' + (ch - '\u0660'))
            in '\u06F0'..'\u06F9' -> append('0' + (ch - '\u06F0'))
            '\u066B' -> append('.')
            '\u066C' -> append(',')
            else -> append(ch)
        }
    }

    /** Parses an amount cell; returns a signed BigDecimal or null. */
    internal fun parseAmount(cell: SheetCell?): BigDecimal? {
        if (cell == null) return null
        var s = toLatinDigits(cell.text.trim())
        if (s.isEmpty()) return null
        if (cell.isNumber) return runCatching { BigDecimal(s) }.getOrNull()
        var negative = false
        if (s.startsWith("(") && s.endsWith(")")) { negative = true; s = s.substring(1, s.length - 1) }
        s = s.replace(Regex("[^0-9.,\\-]"), "")
        if (s.startsWith("-")) { negative = !negative; s = s.substring(1) }
        if (s.isEmpty()) return null
        // "1.234,56" (comma decimal) vs "1,234.56"
        s = if (s.contains(',') && s.contains('.')) {
            if (s.lastIndexOf(',') > s.lastIndexOf('.')) s.replace(".", "").replace(',', '.') else s.replace(",", "")
        } else if (s.count { it == ',' } == 1 && s.substringAfter(',').length != 3) s.replace(',', '.') else s.replace(",", "")
        val v = runCatching { BigDecimal(s) }.getOrNull() ?: return null
        return if (negative) v.negate() else v
    }

    private fun toMilli(v: BigDecimal): Long = v.abs().movePointRight(3).setScale(0, RoundingMode.HALF_UP).longValueExact()

    // ---------------- rows → transactions ----------------

    private class Pending(val row: Int, val type: RowType, val txn: MmTxn)

    fun parseRows(rows: List<List<SheetCell>>): MmParseResult {
        var headerIdx = -1
        var cols: Cols? = null
        for (i in 0 until minOf(rows.size, 30)) {
            val c = findColumns(rows[i])
            if (c != null) { headerIdx = i; cols = c; break }
        }
        if (cols == null) throw MmFormatException("no_header", "Money Manager header row (Date … Income/Expense) not found")

        val skipped = mutableListOf<MmSkipped>()
        val pending = mutableListOf<Pending>()
        var dataRows = 0
        for (i in headerIdx + 1 until rows.size) {
            val r = rows[i]
            if (r.all { it.text.isBlank() }) continue
            dataRows++
            val rowNo = i + 1
            fun cell(idx: Int) = if (idx >= 0 && idx < r.size) r[idx] else null
            fun str(idx: Int) = cell(idx)?.text?.trim() ?: ""

            val dt = cell(cols.date)?.let { parseDate(it) }
            if (dt == null) { skipped += MmSkipped(rowNo, MmSkipReason.BAD_DATE, str(cols.date)); continue }
            val type = rowType(str(cols.type))
            if (type == null) { skipped += MmSkipped(rowNo, MmSkipReason.UNKNOWN_TYPE, str(cols.type)); continue }
            val amount = parseAmount(cell(cols.main))?.takeIf { cols.main >= 0 } ?: parseAmount(cell(cols.amount))
            if (amount == null) { skipped += MmSkipped(rowNo, MmSkipReason.BAD_AMOUNT); continue }
            val milli = runCatching { toMilli(amount) }.getOrNull()
            if (milli == null) { skipped += MmSkipped(rowNo, MmSkipReason.BAD_AMOUNT); continue }
            // Zero-amount rows exist in real exports (placeholders); they are imported as-is so nothing is lost.
            val account = str(cols.account)
            if (account.isEmpty()) { skipped += MmSkipped(rowNo, MmSkipReason.NO_ACCOUNT); continue }

            val rowCurrency = str(cols.currency).uppercase(Locale.ROOT)
            val foreign = cols.mainCurrency != null && rowCurrency.isNotEmpty() && rowCurrency != cols.mainCurrency
            pending += Pending(
                rowNo, type,
                MmTxn(
                    rows = listOf(rowNo),
                    kind = when (type) { RowType.INCOME -> MmKind.INCOME; RowType.EXPENSE -> MmKind.EXPENSE; else -> MmKind.TRANSFER },
                    dateTime = dt, amountMilli = milli, account = account,
                    category = str(cols.category), subcategory = str(cols.subcategory),
                    note = str(cols.note), description = str(cols.description),
                    originalCurrency = if (foreign) rowCurrency else null,
                    originalAmount = if (foreign) str(cols.amount) else null,
                ),
            )
        }

        // ---- transfers: pair Transfer-Out with matching Transfer-In so each transfer is recorded once ----
        val outs = pending.filter { it.type == RowType.TRANSFER_OUT }
        val ins = pending.filter { it.type == RowType.TRANSFER_IN }.toMutableList()
        val result = mutableListOf<Pair<Int, MmTxn>>()
        for (p in pending) {
            if (p.type == RowType.INCOME || p.type == RowType.EXPENSE) result += p.row to p.txn
        }
        fun close(a: LocalDateTime, b: LocalDateTime) = kotlin.math.abs(java.time.Duration.between(a, b).seconds) <= 60
        for (o in outs) {
            val t = o.txn
            val match = ins.firstOrNull { i ->
                i.txn.amountMilli == t.amountMilli && close(i.txn.dateTime, t.dateTime) &&
                    (t.category.isEmpty() || i.txn.account.equals(t.category, true)) &&
                    (i.txn.category.isEmpty() || i.txn.category.equals(t.account, true))
            }
            val target = match?.txn?.account ?: t.category
            if (match != null) ins.remove(match)
            if (target.isEmpty()) { skipped += MmSkipped(o.row, MmSkipReason.TRANSFER_NO_TARGET); continue }
            result += o.row to t.copy(
                rows = listOfNotNull(o.row, match?.row), toAccount = target, category = "",
                note = t.note.ifEmpty { match?.txn?.note ?: "" },
                description = t.description.ifEmpty { match?.txn?.description ?: "" },
            )
        }
        for (i in ins) {
            val t = i.txn
            if (t.category.isEmpty()) { skipped += MmSkipped(i.row, MmSkipReason.TRANSFER_NO_TARGET); continue }
            result += i.row to t.copy(account = t.category, toAccount = t.account, category = "")
        }
        for (p in pending.filter { it.type == RowType.TRANSFER }) {
            val t = p.txn
            if (t.category.isEmpty()) { skipped += MmSkipped(p.row, MmSkipReason.TRANSFER_NO_TARGET); continue }
            result += p.row to t.copy(toAccount = t.category, category = "")
        }

        // ---- stable hashes (occurrence index keeps genuinely identical rows distinct but re-import safe) ----
        val seen = HashMap<String, Int>()
        val txns = result.sortedBy { it.first }.map { (_, t) ->
            val key = t.dedupKey()
            val n = seen.merge(key, 1, Int::plus)!!
            t.copy(hash = hashOf(key, n))
        }
        return MmParseResult(dataRows, txns, skipped.sortedBy { it.row }, cols.mainCurrency, headerIdx + 1)
    }

    fun hashOf(key: String, occurrence: Int): String {
        val md = MessageDigest.getInstance("SHA-256").digest("$key#$occurrence".toByteArray(Charsets.UTF_8))
        return "mm:" + md.joinToString("") { "%02x".format(it) }.take(40)
    }
}
