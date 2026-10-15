package com.midroid.masarifi.data

object Defaults {
    /** Default categories (Money Manager style). key is used by SMS keyword rules. */
    val categories: List<Category> = listOf(
        Category(type = TxnType.EXPENSE, nameAr = "طعام", nameEn = "Food", emoji = "🍜", key = "food", sortOrder = 1),
        Category(type = TxnType.EXPENSE, nameAr = "حياة اجتماعية", nameEn = "Social Life", emoji = "👫", key = "social", sortOrder = 2),
        Category(type = TxnType.EXPENSE, nameAr = "تجميل", nameEn = "Beauty", emoji = "💄", key = "beauty", sortOrder = 3),
        Category(type = TxnType.EXPENSE, nameAr = "صحة", nameEn = "Health", emoji = "🧘", key = "health", sortOrder = 4),
        Category(type = TxnType.EXPENSE, nameAr = "هدايا", nameEn = "Gift", emoji = "🎁", key = "gift", sortOrder = 5),
        Category(type = TxnType.EXPENSE, nameAr = "إيجار", nameEn = "Rent", emoji = "🏠", key = "rent", sortOrder = 6),
        Category(type = TxnType.EXPENSE, nameAr = "إنترنت", nameEn = "Internet", emoji = "🌐", key = "internet", sortOrder = 7),
        Category(type = TxnType.EXPENSE, nameAr = "ملابس", nameEn = "Clothes", emoji = "👕", key = "clothes", sortOrder = 8),
        Category(type = TxnType.EXPENSE, nameAr = "وقود", nameEn = "Fuel", emoji = "⛽", key = "fuel", sortOrder = 9),
        Category(type = TxnType.EXPENSE, nameAr = "سيارة", nameEn = "Car", emoji = "🚗", key = "car", sortOrder = 10),
        Category(type = TxnType.EXPENSE, nameAr = "رحلات", nameEn = "Trip", emoji = "✈️", key = "trip", sortOrder = 11),
        Category(type = TxnType.EXPENSE, nameAr = "إلكترونيات", nameEn = "Electronics", emoji = "📱", key = "electronics", sortOrder = 12),
        Category(type = TxnType.EXPENSE, nameAr = "تأمين السيارة", nameEn = "Car insurance", emoji = "🛡️", key = "car_insurance", sortOrder = 13),
        Category(type = TxnType.EXPENSE, nameAr = "بقالة", nameEn = "Grocery", emoji = "🛒", key = "grocery", sortOrder = 14),
        Category(type = TxnType.EXPENSE, nameAr = "أخرى", nameEn = "Other", emoji = "📦", key = "other", sortOrder = 99),
        Category(type = TxnType.INCOME, nameAr = "راتب", nameEn = "Salary", emoji = "💰", key = "salary", sortOrder = 1),
        Category(type = TxnType.INCOME, nameAr = "مكافأة", nameEn = "Bonus", emoji = "🏅", key = "bonus", sortOrder = 2),
        Category(type = TxnType.INCOME, nameAr = "مصروف", nameEn = "Allowance", emoji = "🤑", key = "allowance", sortOrder = 3),
        Category(type = TxnType.INCOME, nameAr = "نقد صغير", nameEn = "Petty cash", emoji = "💵", key = "petty_cash", sortOrder = 4),
        Category(type = TxnType.INCOME, nameAr = "استرداد", nameEn = "Refund", emoji = "↩️", key = "refund", sortOrder = 5),
        Category(type = TxnType.INCOME, nameAr = "أخرى", nameEn = "Other", emoji = "📦", key = "other_income", sortOrder = 99),
    ).map { it.copy(uuid = categoryUuid(it.key!!), updatedAt = 0) }

    /**
     * Default rows get deterministic uuids and updatedAt = 0 so that two devices that were set up independently
     * merge their defaults instead of duplicating them (and never override a real edit made on the other device).
     */
    fun categoryUuid(key: String) = "cat-$key"
    const val CASH_ACCOUNT_UUID = "acc-cash"

    /** Name-based uuid (same name → same id on every device). */
    fun namedUuid(prefix: String, name: String) =
        prefix + java.util.UUID.nameUUIDFromBytes(name.trim().lowercase(java.util.Locale.ROOT).toByteArray(Charsets.UTF_8))

    val emojiChoices = listOf(
        "🍜", "🍔", "☕", "🛒", "🏠", "🌐", "👕", "⛽", "🚗", "✈️", "📱", "🛡️", "💄", "🧘", "🎁", "👫", "📦", "💰", "🏅", "💵",
        "🎓", "👶", "🐾", "🎮", "🎬", "📚", "💊", "🏥", "💡", "💧", "📞", "🧾", "🕌", "🤲", "🏋️", "🧹", "🔧", "🚕", "🅿️", "🎉",
    )
}
