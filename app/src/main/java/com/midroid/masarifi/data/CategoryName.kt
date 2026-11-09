package com.midroid.masarifi.data

import java.text.BreakIterator

/**
 * Splits a Money Manager (or user) category label that starts with an emoji, e.g. "💸مال" → emoji "💸", name "مال".
 * Used on import and as a one-shot cleanup for already-imported rows so the name is not repeated under a generic 📦.
 */
object CategoryName {
    data class Parts(val emoji: String?, val name: String)

    fun split(raw: String): Parts {
        val t = raw.trim()
        if (t.isEmpty()) return Parts(null, "")
        val it = BreakIterator.getCharacterInstance().also { it.setText(t) }
        val end = it.next()
        if (end == BreakIterator.DONE || end <= 0) return Parts(null, t)
        val first = t.substring(0, end)
        if (!isEmojiGrapheme(first)) return Parts(null, t)
        return Parts(first, t.substring(end).trim())
    }

    /** True when [g] is a single emoji grapheme (symbols / emoji presentation), not Arabic letters or digits. */
    fun isEmojiGrapheme(g: String): Boolean {
        if (g.isEmpty()) return false
        val cp = g.codePointAt(0)
        // Digits, Arabic, Latin letters are never the category icon.
        if (Character.isLetterOrDigit(cp)) return false
        val type = Character.getType(cp)
        if (type == Character.OTHER_SYMBOL.toInt() || type == Character.MODIFIER_SYMBOL.toInt() ||
            type == Character.MATH_SYMBOL.toInt() || type == Character.CURRENCY_SYMBOL.toInt()
        ) return true
        // Regional-indicator / emoji-surrogate ranges commonly used as icons
        if (cp in 0x1F300..0x1FAFF || cp in 0x2600..0x27BF || cp in 0x2300..0x23FF || cp in 0x1F1E6..0x1F1FF) return true
        // Variation selector / ZWJ sequences still start with a symbol we already accepted; lone VS is not an emoji.
        return false
    }

    /**
     * If [name] still starts with an emoji and [emoji] is blank/generic (or equal to that leading emoji),
     * returns cleaned (emoji, name); otherwise null (no change).
     */
    fun cleanup(nameAr: String, nameEn: String, emoji: String): Triple<String, String, String>? {
        val generic = emoji.isBlank() || emoji == "📦"
        fun one(n: String): Pair<String, String>? {
            val p = split(n)
            if (p.emoji == null || p.name.isEmpty()) return null
            if (!generic && emoji != p.emoji) return null
            return p.emoji to p.name
        }
        val a = one(nameAr)
        val e = one(nameEn)
        if (a == null && e == null) return null
        val newEmoji = a?.first ?: e!!.first
        val newAr = a?.second ?: nameAr
        val newEn = e?.second ?: nameEn
        if (newAr == nameAr && newEn == nameEn && newEmoji == emoji) return null
        return Triple(newAr, newEn, newEmoji)
    }
}
