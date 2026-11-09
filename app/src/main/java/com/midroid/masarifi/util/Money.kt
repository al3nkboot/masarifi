package com.midroid.masarifi.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

data class CurrencyInfo(val code: String, val symbolAr: String, val symbolEn: String, val decimals: Int, val nameAr: String, val nameEn: String) {
    fun symbol(arabic: Boolean) = if (arabic) symbolAr else symbolEn
}

object Currencies {
    /**
     * Display order: GCC first, then other Arab League currencies, then USD/EUR/INR.
     * Decimals follow ISO 4217 minor units.
     */
    val all = listOf(
        // Gulf
        CurrencyInfo("OMR", "ر.ع.", "OMR", 3, "ريال عماني", "Omani Rial"),
        CurrencyInfo("AED", "د.إ", "AED", 2, "درهم إماراتي", "UAE Dirham"),
        CurrencyInfo("SAR", "ر.س", "SAR", 2, "ريال سعودي", "Saudi Riyal"),
        CurrencyInfo("KWD", "د.ك", "KWD", 3, "دينار كويتي", "Kuwaiti Dinar"),
        CurrencyInfo("BHD", "د.ب", "BHD", 3, "دينار بحريني", "Bahraini Dinar"),
        CurrencyInfo("QAR", "ر.ق", "QAR", 2, "ريال قطري", "Qatari Riyal"),
        // Other Arab League
        CurrencyInfo("EGP", "ج.م", "EGP", 2, "جنيه مصري", "Egyptian Pound"),
        CurrencyInfo("JOD", "د.أ", "JOD", 3, "دينار أردني", "Jordanian Dinar"),
        CurrencyInfo("IQD", "د.ع", "IQD", 3, "دينار عراقي", "Iraqi Dinar"),
        CurrencyInfo("SYP", "ل.س", "SYP", 2, "ليرة سورية", "Syrian Pound"),
        CurrencyInfo("LBP", "ل.ل", "LBP", 2, "ليرة لبنانية", "Lebanese Pound"),
        CurrencyInfo("YER", "ر.ي", "YER", 2, "ريال يمني", "Yemeni Rial"),
        CurrencyInfo("LYD", "د.ل", "LYD", 3, "دينار ليبي", "Libyan Dinar"),
        CurrencyInfo("TND", "د.ت", "TND", 3, "دينار تونسي", "Tunisian Dinar"),
        CurrencyInfo("DZD", "د.ج", "DZD", 2, "دينار جزائري", "Algerian Dinar"),
        CurrencyInfo("MAD", "د.م", "MAD", 2, "درهم مغربي", "Moroccan Dirham"),
        CurrencyInfo("SDG", "ج.س", "SDG", 2, "جنيه سوداني", "Sudanese Pound"),
        CurrencyInfo("MRU", "أ.م", "MRU", 2, "أوقية موريتانية", "Mauritanian Ouguiya"),
        CurrencyInfo("SOS", "ش.ص", "SOS", 2, "شلن صومالي", "Somali Shilling"),
        CurrencyInfo("DJF", "ف.ج", "DJF", 0, "فرنك جيبوتي", "Djiboutian Franc"),
        CurrencyInfo("KMF", "ف.ق", "KMF", 0, "فرنك قمري", "Comorian Franc"),
        // International
        CurrencyInfo("USD", "$", "$", 2, "دولار أمريكي", "US Dollar"),
        CurrencyInfo("EUR", "€", "€", 2, "يورو", "Euro"),
        CurrencyInfo("INR", "₹", "₹", 2, "روبية هندية", "Indian Rupee"),
    )

    fun of(code: String) = all.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: all.first()

    /**
     * Approximate static conversion rates to OMR (GCC currencies are USD-pegged, so those are stable).
     * Used only for SMS in a foreign currency when the app currency is OMR.
     */
    private val toOmr = mapOf(
        "OMR" to 1.0, "USD" to 0.3845, "AED" to 0.1047, "SAR" to 0.1025, "QAR" to 0.1056,
        "BHD" to 1.0227, "KWD" to 1.253, "EUR" to 0.42, "GBP" to 0.49, "INR" to 0.0046,
        "EGP" to 0.0079, "JOD" to 0.542, "IQD" to 0.00029, "SYP" to 0.00003, "LBP" to 0.000004,
        "YER" to 0.0015, "LYD" to 0.079, "TND" to 0.124, "DZD" to 0.0029, "MAD" to 0.039,
        "SDG" to 0.00064, "MRU" to 0.0096, "SOS" to 0.00067, "DJF" to 0.00216, "KMF" to 0.00082,
    )

    /** Converts milli-amount between currencies using static rates; null if unknown. */
    fun convert(milli: Long, from: String, to: String): Long? {
        if (from == to) return milli
        val f = toOmr[from] ?: return null
        val t = toOmr[to] ?: return null
        return Math.round(milli * f / t)
    }
}

object Money {
    private val symbols = DecimalFormatSymbols(Locale.US)

    fun format(milli: Long, cur: CurrencyInfo, arabic: Boolean, withSymbol: Boolean = true): String {
        val pattern = if (cur.decimals == 0) "#,##0" else "#,##0." + "0".repeat(cur.decimals)
        val df = DecimalFormat(pattern, symbols)
        val value = BigDecimal.valueOf(milli).movePointLeft(3).setScale(cur.decimals, RoundingMode.HALF_UP)
        val num = df.format(value)
        if (!withSymbol) return num
        return if (arabic) "$num ${cur.symbolAr}" else "${cur.symbolEn} $num"
    }

    /** Plain number for text fields, e.g. 4300 → "4.300". */
    fun toInput(milli: Long, cur: CurrencyInfo): String {
        if (milli == 0L) return ""
        return BigDecimal.valueOf(milli).movePointLeft(3).setScale(cur.decimals, RoundingMode.HALF_UP).toPlainString()
    }

    /** Parses user input (accepts Arabic-Indic digits, "٫" and ","). Returns milli units. */
    fun parse(text: String): Long? {
        val sb = StringBuilder()
        for (ch in text.trim()) {
            when (ch) {
                in '\u0660'..'\u0669' -> sb.append('0' + (ch - '\u0660'))
                in '\u06F0'..'\u06F9' -> sb.append('0' + (ch - '\u06F0'))
                '\u066B', ',' -> sb.append('.')
                '\u066C', ' ' -> {}
                else -> sb.append(ch)
            }
        }
        val s = sb.toString()
        if (s.isEmpty()) return null
        return runCatching { BigDecimal(s).movePointRight(3).setScale(0, RoundingMode.HALF_UP).longValueExact() }.getOrNull()
            ?.takeIf { it >= 0 }
    }

    fun fromBigDecimal(v: BigDecimal): Long = v.movePointRight(3).setScale(0, RoundingMode.HALF_UP).toLong()
}
