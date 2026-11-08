package com.midroid.masarifi.ui

import com.midroid.masarifi.data.Category
import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryIconsTest {
    private fun cat(n: String, e: String = "📦", key: String? = null) = Category(type = 0, nameAr = n, nameEn = n, emoji = e, key = key)

    @Test fun guessesFromNames() {
        mapOf(
            "water" to "💧", "مياه" to "💧", "Electricity" to "⚡", "كهرباء" to "⚡", "Talabat" to "🍔", "طلبات" to "🍔",
            "X Premium" to "🐦", "Anghami" to "🎵", "SuperGrok" to "🤖", "Netflix" to "📺", "إيجار" to "🏠",
            "Carrefour" to "🛒", "سيارة" to "🚗", "Omantel" to "📶", "وقود" to "⛽",
        ).forEach { (n, e) -> assertEquals(n, e, cat(n).icon) }
    }

    @Test fun keepsUserEmojiAndOther() {
        assertEquals("🎯", cat("water", "🎯").icon)
        assertEquals("📦", cat("Other", key = "other").icon)
        assertEquals("📦", cat("Misc stuff").icon)
    }
}
