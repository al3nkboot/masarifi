package com.midroid.masarifi.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryNameTest {
    @Test fun stripsLeadingEmojiFromArabicName() {
        val p = CategoryName.split("💸مال")
        assertEquals("💸", p.emoji)
        assertEquals("مال", p.name)
    }

    @Test fun stripsLeadingEmojiFromFood() {
        val p = CategoryName.split("🍜أكل")
        assertEquals("🍜", p.emoji)
        assertEquals("أكل", p.name)
    }

    @Test fun plainNameUnchanged() {
        val p = CategoryName.split("Trip")
        assertNull(p.emoji)
        assertEquals("Trip", p.name)
    }

    @Test fun cleanupMovesEmojiOutOfDuplicateName() {
        val r = CategoryName.cleanup("💸مال", "💸مال", "📦")!!
        assertEquals("مال", r.first)
        assertEquals("مال", r.second)
        assertEquals("💸", r.third)
    }

    @Test fun cleanupSkipsWhenEmojiAlreadySetDifferently() {
        assertNull(CategoryName.cleanup("مال", "Money", "💰"))
    }

    @Test fun cleanupIdempotentOnCleanNames() {
        assertNull(CategoryName.cleanup("مال", "Money", "💸"))
    }

    @Test fun emojiGraphemeRejectsLetters() {
        assertTrue(!CategoryName.isEmojiGrapheme("م"))
        assertTrue(CategoryName.isEmojiGrapheme("💸"))
    }
}
