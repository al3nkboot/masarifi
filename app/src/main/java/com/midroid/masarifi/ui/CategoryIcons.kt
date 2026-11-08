package com.midroid.masarifi.ui

import com.midroid.masarifi.data.Category

/**
 * Display-only icon guess for categories still using the generic 📦 (user-created / imported rows).
 * Never written back to the DB; a user-chosen emoji always wins.
 */
object CategoryIcons {
    const val GENERIC = "📦"

    private val rules: List<Pair<List<String>, String>> = listOf(
        listOf("supergrok", "grok", "chatgpt", "openai", "claude", "gemini", "ذكاء") to "🤖",
        listOf("anghami", "أنغامي", "انغامي", "spotify", "سبوتيفاي", "music", "موسيقى", "deezer") to "🎵",
        listOf("netflix", "نتفليكس", "shahid", "شاهد", "osn", "youtube", "يوتيوب", "disney", "prime video", "tod", "starzplay") to "📺",
        listOf("x premium", "twitter", "تويتر", "إكس") to "🐦",
        listOf("icloud", "google one", "apple", "subscription", "اشتراك") to "🔁",
        listOf("water", "ماء", "مياه", "نماء للمياه") to "💧",
        listOf("electric", "كهرباء", "كهربا", "nama", "نماء") to "⚡",
        listOf("gas", "غاز") to "🔥",
        listOf("fuel", "petrol", "وقود", "بنزين", "shell", "omanoil", "al maha", "المها") to "⛽",
        listOf("talabat", "طلبات", "food", "restaurant", "مطعم", "مطاعم", "أكل", "اكل", "طعام", "careem food", "deliveroo", "keeta") to "🍔",
        listOf("coffee", "قهوة", "كافيه", "cafe", "starbucks", "كرك") to "☕",
        listOf("grocery", "بقالة", "lulu", "لولو", "carrefour", "كارفور", "supermarket", "سوبرماركت", "hypermarket") to "🛒",
        listOf("car", "سيارة", "سياره", "garage", "كراج", "ورشة") to "🚗",
        listOf("internet", "انترنت", "إنترنت", "wifi", "fiber", "فايبر", "omantel", "عمانتل", "ooredoo", "أوريدو", "vodafone", "فودافون", "phone", "هاتف", "جوال", "mobile", "اتصالات", "رصيد") to "📶",
        listOf("rent", "إيجار", "ايجار", "سكن", "housing", "home", "بيت") to "🏠",
        listOf("school", "مدرسة", "education", "تعليم", "course", "دورة", "university", "جامعة") to "🎓",
        listOf("health", "صحة", "doctor", "طبيب", "hospital", "مستشفى", "pharmacy", "صيدلية", "clinic", "عيادة") to "💊",
        listOf("gym", "نادي", "sport", "رياضة") to "🏋️",
        listOf("insurance", "تأمين") to "🛡️",
        listOf("loan", "قرض", "installment", "قسط", "أقساط", "bank", "بنك") to "🏦",
        listOf("travel", "trip", "سفر", "رحلة", "flight", "طيران", "hotel", "فندق") to "✈️",
        listOf("clothes", "ملابس", "fashion", "أزياء") to "👕",
        listOf("gift", "هدية", "هدايا") to "🎁",
        listOf("kids", "أطفال", "اطفال", "baby") to "🧸",
        listOf("charity", "صدقة", "زكاة", "تبرع") to "🤲",
        listOf("salary", "راتب") to "💰",
        listOf("game", "ألعاب", "العاب", "playstation", "xbox", "steam") to "🎮",
    )

    /** Guess an emoji from free text (category or bill name); null when nothing matches. */
    fun guess(text: String): String? {
        val t = text.lowercase()
        if (t.isBlank()) return null
        return rules.firstOrNull { (keys, _) -> keys.any { k -> if (k.length <= 2) t.split(' ', '-', '_').contains(k) else t.contains(k) } }?.second
    }

    fun forCategory(c: Category): String {
        if (c.emoji.isNotBlank() && c.emoji != GENERIC) return c.emoji
        if (c.key == "other" || c.key == "other_income") return c.emoji
        return guess(c.nameEn + " " + c.nameAr) ?: c.emoji.ifBlank { GENERIC }
    }
}

/** Emoji to show for a category (smart guess for generic 📦 rows; DB untouched). */
val Category.icon: String get() = CategoryIcons.forCategory(this)
