package com.midroid.masarifi.data.mm

import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.time.LocalDateTime
import java.util.zip.ZipInputStream
import javax.xml.parsers.SAXParserFactory

/** One spreadsheet cell. [raw] is the text as stored (numbers keep their exact decimal text). */
data class SheetCell(val raw: String, val isNumber: Boolean = false, val isDate: Boolean = false) {
    val text: String get() = raw
    companion object { val EMPTY = SheetCell("") }
}

/**
 * Minimal dependency-free .xlsx reader (zip + SpreadsheetML via SAX, available on Android and the JVM).
 * Reads the first worksheet: shared strings, inline strings, numbers, booleans and date-formatted numbers.
 */
object XlsxReader {
    private const val MAX_ENTRY = 64L * 1024 * 1024

    class XlsxException(msg: String) : Exception(msg)

    fun readFirstSheet(input: InputStream): List<List<SheetCell>> = readFirstSheet(input.readBytes())

    fun readFirstSheet(bytes: ByteArray): List<List<SheetCell>> {
        val wanted = { name: String -> name.startsWith("xl/") || name == "[Content_Types].xml" }
        // Central-directory reader first: some exporters (incl. Money Manager) write local headers with
        // zero sizes + data descriptors that java.util.zip.ZipInputStream rejects.
        val entries = runCatching { readZipCentral(bytes, wanted) }.getOrNull()?.takeIf { it.isNotEmpty() } ?: readZipStream(bytes, wanted)
        if (entries.isEmpty()) throw XlsxException("Not an xlsx file")
        val shared = entries["xl/sharedStrings.xml"]?.let { parseSharedStrings(it) } ?: emptyList()
        val dateStyles = entries["xl/styles.xml"]?.let { parseDateStyles(it) } ?: emptySet()
        val date1904 = entries["xl/workbook.xml"]?.let { isDate1904(it) } ?: false
        val sheetPath = firstSheetPath(entries) ?: throw XlsxException("No worksheet found")
        val sheet = entries[sheetPath] ?: throw XlsxException("Missing $sheetPath")
        return parseSheet(sheet, shared, dateStyles, date1904)
    }

    private fun readZipStream(bytes: ByteArray, wanted: (String) -> Boolean): Map<String, ByteArray> {
        val entries = HashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                val name = e.name.removePrefix("/")
                if (!e.isDirectory && wanted(name)) entries[name] = zip.readBytes()
            }
        }
        return entries
    }

    private fun u16(b: ByteArray, o: Int) = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)
    private fun u32(b: ByteArray, o: Int) = (u16(b, o).toLong() or (u16(b, o + 2).toLong() shl 16))

    /** Minimal ZIP reader using the central directory (stored + deflated entries). */
    internal fun readZipCentral(b: ByteArray, wanted: (String) -> Boolean): Map<String, ByteArray> {
        var eocd = -1
        var i = b.size - 22
        val stop = maxOf(0, b.size - 22 - 65_557)
        while (i >= stop) { if (u32(b, i) == 0x06054b50L) { eocd = i; break }; i-- }
        if (eocd < 0) throw XlsxException("Not a zip file")
        val count = u16(b, eocd + 10)
        var p = u32(b, eocd + 16).toInt()
        val out = HashMap<String, ByteArray>()
        repeat(count) {
            if (u32(b, p) != 0x02014b50L) throw XlsxException("Bad central directory")
            val method = u16(b, p + 10)
            val compSize = u32(b, p + 20).toInt()
            val size = u32(b, p + 24)
            val nameLen = u16(b, p + 28); val extraLen = u16(b, p + 30); val commentLen = u16(b, p + 32)
            val localOff = u32(b, p + 42).toInt()
            val name = String(b, p + 46, nameLen, Charsets.UTF_8).removePrefix("/")
            p += 46 + nameLen + extraLen + commentLen
            if (name.endsWith("/") || !wanted(name)) return@repeat
            if (size > MAX_ENTRY) throw XlsxException("Entry too large: $name")
            if (u32(b, localOff) != 0x04034b50L) throw XlsxException("Bad local header")
            val dataOff = localOff + 30 + u16(b, localOff + 26) + u16(b, localOff + 28)
            out[name] = when (method) {
                0 -> b.copyOfRange(dataOff, dataOff + compSize)
                8 -> {
                    val inf = java.util.zip.Inflater(true)
                    try {
                        inf.setInput(b, dataOff, compSize)
                        val res = java.io.ByteArrayOutputStream(maxOf(32, size.toInt()))
                        val buf = ByteArray(64 * 1024)
                        while (!inf.finished()) {
                            val n = inf.inflate(buf)
                            if (n == 0 && (inf.needsInput() || inf.needsDictionary())) break
                            res.write(buf, 0, n)
                            if (res.size() > MAX_ENTRY) throw XlsxException("Entry too large: $name")
                        }
                        res.toByteArray()
                    } finally { inf.end() }
                }
                else -> throw XlsxException("Unsupported zip method $method")
            }
        }
        return out
    }

    /** Converts an Excel serial date to a LocalDateTime (millisecond precision). */
    fun serialToDateTime(serial: Double, date1904: Boolean = false): LocalDateTime {
        val base = if (date1904) LocalDateTime.of(1904, 1, 1, 0, 0) else LocalDateTime.of(1899, 12, 30, 0, 0)
        val adjusted = if (!date1904 && serial < 61) serial + 1 else serial // Excel's fake 1900-02-29
        val millis = Math.round(adjusted * 86_400_000.0)
        return base.plusSeconds(Math.floorDiv(millis, 1000L)).plusNanos(Math.floorMod(millis, 1000L) * 1_000_000L)
    }

    // ---------- parts ----------

    private fun local(localName: String?, qName: String?): String {
        val n = if (!localName.isNullOrEmpty()) localName else qName ?: ""
        val i = n.indexOf(':')
        return if (i >= 0) n.substring(i + 1) else n
    }

    private fun attr(a: Attributes, name: String): String? {
        for (i in 0 until a.length) {
            if (local(a.getLocalName(i), a.getQName(i)) == name) return a.getValue(i)
        }
        return null
    }

    private fun parse(bytes: ByteArray, handler: DefaultHandler) {
        val f = SAXParserFactory.newInstance()
        f.isNamespaceAware = false
        runCatching { f.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { f.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        runCatching { f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        f.newSAXParser().parse(InputSource(ByteArrayInputStream(bytes)), handler)
    }

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        val out = ArrayList<String>()
        parse(bytes, object : DefaultHandler() {
            val sb = StringBuilder(); var inSi = false; var inT = false; var phonetic = 0
            override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes) {
                when (local(localName, qName)) {
                    "si" -> { inSi = true; sb.setLength(0) }
                    "t" -> if (inSi && phonetic == 0) inT = true
                    "rPh" -> phonetic++
                }
            }
            override fun endElement(uri: String?, localName: String?, qName: String?) {
                when (local(localName, qName)) {
                    "si" -> { out.add(sb.toString()); inSi = false }
                    "t" -> inT = false
                    "rPh" -> phonetic--
                }
            }
            override fun characters(ch: CharArray, start: Int, length: Int) { if (inT) sb.appendRange(ch, start, start + length) }
        })
        return out
    }

    private val builtInDateFormats = (14..22).toSet() + setOf(27, 30, 36, 45, 46, 47, 50, 57)

    internal fun isDateFormatCode(code: String): Boolean {
        val stripped = code.replace(Regex("\"[^\"]*\""), "").replace(Regex("\\[[^]]*]"), "").replace(Regex("\\\\."), "")
        if (stripped.trim().equals("general", ignoreCase = true)) return false
        return stripped.any { it in "dDmMyYhHsS" }
    }

    /** Returns indices of cellXfs that use a date/time number format. */
    private fun parseDateStyles(bytes: ByteArray): Set<Int> {
        val custom = HashMap<Int, String>()
        val xfFmt = ArrayList<Int>()
        parse(bytes, object : DefaultHandler() {
            var inCellXfs = false
            override fun startElement(uri: String?, localName: String?, qName: String?, a: Attributes) {
                when (local(localName, qName)) {
                    "numFmt" -> { val id = attr(a, "numFmtId")?.toIntOrNull(); val c = attr(a, "formatCode"); if (id != null && c != null) custom[id] = c }
                    "cellXfs" -> inCellXfs = true
                    "xf" -> if (inCellXfs) xfFmt.add(attr(a, "numFmtId")?.toIntOrNull() ?: 0)
                }
            }
            override fun endElement(uri: String?, localName: String?, qName: String?) { if (local(localName, qName) == "cellXfs") inCellXfs = false }
        })
        val out = HashSet<Int>()
        xfFmt.forEachIndexed { i, id -> if (id in builtInDateFormats || custom[id]?.let { isDateFormatCode(it) } == true) out.add(i) }
        return out
    }

    private fun isDate1904(bytes: ByteArray): Boolean {
        var r = false
        parse(bytes, object : DefaultHandler() {
            override fun startElement(uri: String?, localName: String?, qName: String?, a: Attributes) {
                if (local(localName, qName) == "workbookPr") attr(a, "date1904")?.let { r = it == "1" || it.equals("true", true) }
            }
        })
        return r
    }

    private fun firstSheetPath(entries: Map<String, ByteArray>): String? {
        var rid: String? = null
        entries["xl/workbook.xml"]?.let { wb ->
            parse(wb, object : DefaultHandler() {
                override fun startElement(uri: String?, localName: String?, qName: String?, a: Attributes) {
                    if (rid == null && local(localName, qName) == "sheet") {
                        for (i in 0 until a.length) if ((a.getQName(i) ?: "").let { it.endsWith(":id") }) rid = a.getValue(i)
                    }
                }
            })
        }
        var target: String? = null
        if (rid != null) entries["xl/_rels/workbook.xml.rels"]?.let { rels ->
            parse(rels, object : DefaultHandler() {
                override fun startElement(uri: String?, localName: String?, qName: String?, a: Attributes) {
                    if (local(localName, qName) == "Relationship") {
                        var id: String? = null; var t: String? = null
                        for (i in 0 until a.length) when (local(a.getLocalName(i), a.getQName(i))) { "Id" -> id = a.getValue(i); "Target" -> t = a.getValue(i) }
                        if (id == rid) target = t
                    }
                }
            })
        }
        target?.let { t ->
            val p = if (t.startsWith("/")) t.removePrefix("/") else "xl/" + t.removePrefix("./")
            if (entries.containsKey(p)) return p
        }
        return entries.keys.filter { it.startsWith("xl/worksheets/") && it.endsWith(".xml") }.minOrNull()
    }

    private fun colIndex(ref: String): Int {
        var n = 0
        for (ch in ref) { if (ch in 'A'..'Z') n = n * 26 + (ch - 'A' + 1) else if (ch in 'a'..'z') n = n * 26 + (ch - 'a' + 1) else break }
        return n - 1
    }

    private fun parseSheet(bytes: ByteArray, shared: List<String>, dateStyles: Set<Int>, date1904: Boolean): List<List<SheetCell>> {
        val rows = ArrayList<List<SheetCell>>()
        parse(bytes, object : DefaultHandler() {
            var cur: ArrayList<SheetCell>? = null
            var rowIndex = -1
            var col = -1; var type = "n"; var style = 0
            val v = StringBuilder(); var inV = false; var inIs = false; var inT = false
            override fun startElement(uri: String?, localName: String?, qName: String?, a: Attributes) {
                when (local(localName, qName)) {
                    "row" -> {
                        val r = attr(a, "r")?.toIntOrNull()?.minus(1) ?: (rowIndex + 1)
                        while (rows.size < r) rows.add(emptyList()) // keep row numbering for skipped empty rows
                        rowIndex = r; cur = ArrayList()
                    }
                    "c" -> {
                        val ref = attr(a, "r")
                        col = if (ref != null) colIndex(ref) else (cur?.size ?: 0)
                        type = attr(a, "t") ?: "n"; style = attr(a, "s")?.toIntOrNull() ?: 0
                        v.setLength(0)
                    }
                    "v" -> inV = true
                    "is" -> inIs = true
                    "t" -> if (inIs) inT = true
                }
            }
            override fun endElement(uri: String?, localName: String?, qName: String?) {
                when (local(localName, qName)) {
                    "v" -> inV = false
                    "t" -> inT = false
                    "is" -> inIs = false
                    "c" -> {
                        val row = cur ?: return
                        val raw = v.toString()
                        val cell = when (type) {
                            "s" -> SheetCell(raw.trim().toIntOrNull()?.let { shared.getOrNull(it) } ?: "")
                            "inlineStr", "str" -> SheetCell(raw)
                            "b" -> SheetCell(if (raw.trim() == "1") "TRUE" else "FALSE")
                            "e" -> SheetCell("")
                            "d" -> SheetCell(raw, isDate = true) // ISO 8601 date cell
                            else -> if (raw.isBlank()) SheetCell.EMPTY else {
                                val isDate = style in dateStyles
                                if (isDate) {
                                    val d = raw.trim().toDoubleOrNull()
                                    if (d != null) SheetCell(serialToDateTime(d, date1904).toString(), isNumber = false, isDate = true) else SheetCell(raw)
                                } else SheetCell(raw.trim(), isNumber = true)
                            }
                        }
                        while (row.size < col) row.add(SheetCell.EMPTY)
                        if (col < row.size) row[col] = cell else row.add(cell)
                    }
                    "row" -> { cur?.let { rows.add(it) }; cur = null }
                }
            }
            override fun characters(ch: CharArray, start: Int, length: Int) { if (inV || inT) v.appendRange(ch, start, start + length) }
        })
        return rows
    }
}
