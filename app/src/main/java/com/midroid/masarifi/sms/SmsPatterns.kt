package com.midroid.masarifi.sms

/** Shared regex fragments used by the parser and the default configuration. */
object SmsPatterns {
    /** Currency tokens (English + Arabic). Order matters: longer first. */
    const val CURRENCY =
        "O\\.M\\.R\\.?|OMR|(?<![A-Za-z])R\\.O\\.?|(?<![A-Za-z])RO(?![A-Za-z])|ريال\\s*عماني|(?<![\\u0621-\\u064A])ر\\s?\\.\\s?ع\\s?\\.?|(?<![\\u0621-\\u064A])ر\\s?ع(?![\\u0621-\\u064A])|ريال|" +
            "US\\$|USD|\\$|AED|درهم|SAR|EUR|€|GBP|£|QAR|KWD|BHD|INR"

    /** Amount: 1,234.567 or 1234.5 or 12 or .225 (Thawani writes amounts below 1 without the leading zero). */
    const val NUMBER = "\\d{1,3}(?:,\\d{3})+(?:\\.\\d{1,3})?|\\d+(?:\\.\\d{1,3})?|(?<![\\d.])\\.\\d{1,3}(?![\\d.])"

    const val DATE =
        "\\d{1,2}[/\\-.]\\d{1,2}[/\\-.]\\d{2,4}|\\d{4}-\\d{1,2}-\\d{1,2}|\\d{1,2}[\\s\\-]?(?:JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)[A-Za-z]*[\\s\\-,]*\\d{2,4}"

    fun expand(pattern: String): String = pattern
        .replace("{CURX}", "(?:$CURRENCY)")
        .replace("{CUR}", "(?<currency>$CURRENCY)")
        .replace("{AMT}", "(?<amount>$NUMBER)")
        .replace("{CARD}", "(?:[xX*#•.\\-]*\\d*[xX*#•.\\-]+)?\\d*?(?<card>\\d{3,4})(?!\\d)")
        .replace("{MERCHANT}", "(?<merchant>[^\\n]+?)")
        .replace("{DATE}", "(?<date>$DATE)")
        .replace("{ANY}", "[\\s\\S]*?")

    /**
     * Compiles a pattern that may contain named groups into a Regex with plain numbered groups plus a
     * name→index map. (Named-group lookup through kotlin.text is not available on all Android versions.)
     */
    fun compile(pattern: String): NamedRegex {
        val expanded = expand(pattern)
        val out = StringBuilder()
        val names = HashMap<String, Int>()
        var groupIndex = 0
        var i = 0
        var inClass = false
        while (i < expanded.length) {
            val c = expanded[i]
            if (c == '\\') { out.append(c); if (i + 1 < expanded.length) out.append(expanded[i + 1]); i += 2; continue }
            if (inClass) { if (c == ']') inClass = false; out.append(c); i++; continue }
            if (c == '[') { inClass = true; out.append(c); i++; continue }
            if (c == '(') {
                val rest = expanded.substring(i + 1)
                if (rest.startsWith("?<") && !rest.startsWith("?<=") && !rest.startsWith("?<!")) {
                    val end = rest.indexOf('>')
                    val name = rest.substring(2, end)
                    groupIndex++
                    names[name] = groupIndex
                    out.append('(')
                    i += 1 + end + 1
                    continue
                } else if (!rest.startsWith("?")) {
                    groupIndex++
                }
            }
            out.append(c)
            i++
        }
        return NamedRegex(Regex(out.toString(), RegexOption.IGNORE_CASE), names)
    }

    /** Maps a raw currency token to an ISO code. */
    fun currencyCode(raw: String?, default: String): String {
        if (raw == null) return default
        val t = raw.replace(" ", "").replace(".", "").uppercase()
        return when {
            t == "OMR" || t == "RO" || t.startsWith("ريال") && t.contains("عماني") -> "OMR"
            t == "رع" -> "OMR"
            t == "ريال" -> default
            t == "US$" || t == "USD" || t == "$" -> "USD"
            t == "AED" || t == "درهم" -> "AED"
            t == "SAR" -> "SAR"
            t == "EUR" || t == "€" -> "EUR"
            t == "GBP" || t == "£" -> "GBP"
            t == "QAR" -> "QAR"
            t == "KWD" -> "KWD"
            t == "BHD" -> "BHD"
            t == "INR" -> "INR"
            else -> default
        }
    }
}

class NamedRegex(val regex: Regex, private val names: Map<String, Int>) {
    fun find(input: CharSequence): MatchResult? = regex.find(input)
    fun findAll(input: CharSequence): Sequence<MatchResult> = regex.findAll(input)
    fun group(m: MatchResult, name: String): String? {
        val idx = names[name] ?: return null
        return m.groups[idx]?.value?.takeIf { it.isNotBlank() }
    }
}
