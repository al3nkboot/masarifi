package com.midroid.masarifi.data.backup

/** What a file picked under «استعادة» is, from its content (names and MIME types from providers are unreliable). */
enum class RestoreFileKind { APP_BACKUP, SYNC_FILE, APP_CSV, MONEY_MANAGER, UNKNOWN }

object RestoreFileDetector {
    private val APP_CSV_HEADER = listOf("date", "type", "account", "to account", "category", "amount")

    fun detect(bytes: ByteArray, name: String? = null): RestoreFileKind {
        if (bytes.size >= 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) {
            // zip: an .xlsx workbook (Money Manager export); the app never writes zip backups
            return if (String(bytes, Charsets.ISO_8859_1).contains("xl/")) RestoreFileKind.MONEY_MANAGER else RestoreFileKind.UNKNOWN
        }
        if (bytes.size >= 8 && bytes[0] == 0xD0.toByte() && bytes[1] == 0xCF.toByte()) return RestoreFileKind.MONEY_MANAGER // legacy .xls
        val head = String(bytes, 0, minOf(bytes.size, 64 * 1024), Charsets.UTF_8).removePrefix("\uFEFF").trimStart()
        if (head.startsWith("{")) {
            val fmt = Regex("\"format\"\\s*:\\s*\"([^\"]+)\"").find(head)?.groupValues?.get(1)
                ?: runCatching { org.json.JSONObject(String(bytes, Charsets.UTF_8).removePrefix("\uFEFF")).optString("format") }.getOrNull()
            return when (fmt) {
                BackupCodec.FORMAT -> RestoreFileKind.APP_BACKUP
                "masarifi-sync" -> RestoreFileKind.SYNC_FILE
                else -> RestoreFileKind.UNKNOWN
            }
        }
        if (head.startsWith("<")) return if (head.contains("<table", true)) RestoreFileKind.MONEY_MANAGER else RestoreFileKind.UNKNOWN // MM "xls" = HTML
        val first = head.lineSequence().firstOrNull()?.lowercase()?.trim() ?: return RestoreFileKind.UNKNOWN
        val cells = first.split(',', ';', '\t').map { it.trim().trim('"') }
        if (cells.take(APP_CSV_HEADER.size) == APP_CSV_HEADER) return RestoreFileKind.APP_CSV
        if (cells.any { it == "period" || it == "income/expense" || it == "inc./exp." || it.startsWith("income/expense") }) return RestoreFileKind.MONEY_MANAGER
        if (name?.endsWith(".xlsx", true) == true || name?.endsWith(".xls", true) == true) return RestoreFileKind.MONEY_MANAGER
        return RestoreFileKind.UNKNOWN
    }
}
