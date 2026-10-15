package com.midroid.masarifi.sms

/**
 * Keyword rules mapping a merchant / SMS text to a default category key.
 * Learned user corrections (merchantKey → categoryId) are stored in the DB and take precedence.
 */
class MerchantCategorizer(private val rules: List<Pair<String, List<String>>> = DEFAULT_RULES) {

    /** Returns a category key such as "food" or null if nothing matched. */
    fun categorize(merchant: String?, body: String = ""): String? {
        // Merchant hits first. Body is only used when no merchant was extracted (avoids false hits
        // on generic words in the bank text), except salary which is usually only in the body.
        if (!merchant.isNullOrBlank()) {
            match(merchant.lowercase())?.let { return it }
            val lowerBody = body.lowercase()
            return if (rules.first { it.first == "salary" }.second.any { SmsParser.containsWord(lowerBody, it) }) "salary" else null
        }
        return match(body.lowercase())
    }

    private fun match(text: String): String? {
        for ((key, words) in rules) {
            if (words.any { SmsParser.containsWord(text, it.lowercase()) }) return key
        }
        return null
    }

    companion object {
        /** Normalized merchant key used for learning: lowercase, no digits/punctuation, single spaces. */
        fun merchantKey(merchant: String): String =
            merchant.lowercase()
                .replace(Regex("[0-9]+"), " ")
                .replace(Regex("[^\\p{L} ]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
                .split(" ").filter { it.isNotBlank() }.take(4).joinToString(" ")

        val DEFAULT_RULES: List<Pair<String, List<String>>> = listOf(
            "salary" to listOf("salary", "payroll", "راتب", "الراتب", "رواتب"),
            "car_insurance" to listOf("insurance", "takaful", "تأمين", "تامين", "تكافل"),
            "fuel" to listOf("shell", "oman oil", "omanoil", "al maha", "almaha", "petrol", "fuel", "filling station", "بترول", "وقود", "المها", "نفط عمان", "شل"),
            "grocery" to listOf(
                "lulu", "carrefour", "nesto", "al meera", "almeera", "sultan center", "sultan centre", "hypermarket", "supermarket",
                "grocery", "spar", "safeer", "al fair", "khimji mart", "mart", "بقالة", "هايبر", "سوبرماركت", "لولو", "كارفور", "الميرة", "نستو",
            ),
            "food" to listOf(
                "restaurant", "cafe", "café", "coffee", "kfc", "mcdonald", "mcdonalds", "pizza", "burger", "shawarma", "talabat",
                "starbucks", "tim hortons", "costa", "dunkin", "subway", "hardees", "papa john", "domino", "bakery", "kitchen", "grill",
                "مطعم", "مقهى", "كافيه", "قهوة", "مشويات", "شاورما", "طلبات", "مخبز",
            ),
            "internet" to listOf("omantel", "ooredoo", "vodafone", "awasr", "redtone", "internet", "broadband", "عمانتل", "أوريدو", "اوريدو", "فودافون", "أواصر", "انترنت", "إنترنت"),
            "health" to listOf("pharmacy", "hospital", "clinic", "medical", "dental", "muscat pharmacy", "aster", "صيدلية", "مستشفى", "عيادة", "طبي"),
            "clothes" to listOf("h&m", "zara", "max fashion", "splash", "centrepoint", "centerpoint", "lc waikiki", "shoe", "fashion", "ملابس", "أزياء"),
            "electronics" to listOf("extra", "sharaf dg", "jarir", "apple", "samsung", "emax", "electronics", "اكسترا", "جرير", "الكترونيات", "إلكترونيات"),
            "trip" to listOf("oman air", "omanair", "salam air", "salamair", "airbnb", "booking.com", "hotel", "airline", "airways", "expedia", "طيران", "فندق", "سفر"),
            "beauty" to listOf("salon", "sephora", "faces", "spa", "barber", "صالون", "حلاق", "تجميل"),
            "social" to listOf("cinema", "vox", "novo", "netflix", "shahid", "spotify", "playstation", "سينما", "نتفليكس", "شاهد"),
            "car" to listOf("car wash", "garage", "tyre", "tire", "auto", "rop", "parking", "mawaqif", "service center", "مغسلة", "كراج", "إطارات", "مواقف", "شرطة عمان"),
            "rent" to listOf("rent", "إيجار", "ايجار"),
            "gift" to listOf("gift", "flowers", "هدية", "هدايا", "زهور"),
        )
    }
}
