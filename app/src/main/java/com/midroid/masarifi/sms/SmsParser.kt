package com.midroid.masarifi.sms

import java.math.BigDecimal
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Rule-based parser for bank transaction SMS (Arabic + English).
 *
 * Pipeline: normalize → identify bank by sender (or body) → reject OTP/declined/promo →
 * bank rules → generic rules → keyword heuristic → enrich (card, merchant, date, balance).
 *
 * Pure Kotlin/JVM: no Android dependencies so it can be unit-tested on the JVM.
 */
class SmsParser(private val config: SmsParserConfig = DefaultSmsConfig.config()) {

    private val amountNamed = SmsPatterns.compile(
        "(?<cur1>${SmsPatterns.CURRENCY})\\s?(?<amt1>${SmsPatterns.NUMBER})|(?<amt2>${SmsPatterns.NUMBER})\\s?(?<cur2>${SmsPatterns.CURRENCY})",
    )
    private val amountRegex = amountNamed.regex
    /** Masked card/account: 4000XXXXXXXX5501 → 5501, XXXX6610 → 6610, 0200XXX0007001 → 7001 (last 4 digits). */
    private val maskedCardRegex = Regex("(?:\\d{0,6})[xX*#•]{2,}[\\-\\s]?(\\d{3,})(?!\\d)")
    private val refRegex = Regex(
        "(?:txn|transaction|trx)\\s*(?:id|ref(?:erence)?|no\\.?)\\s*[:#.]?\\s*([A-Za-z0-9]{6,})|(?:ref(?:erence)?\\s*(?:no\\.?|number|#)|رقم\\s+(?:العملية|المرجع|المعاملة))\\s*[:#.]?\\s*([A-Za-z0-9]{6,})",
        RegexOption.IGNORE_CASE,
    )
    private val endingCardRegex = Regex(
        "(?:ending(?:\\s+(?:with|in))?|ends\\s+with|المنتهية\\s+ب|المنتهي\\s+ب|ينتهي\\s+ب|تنتهي\\s+ب|المنتهية|المنتهي)\\s*ـ?\\s*[:：]?\\s*[xX*#•]*(\\d{3,4})(?!\\d)",
        RegexOption.IGNORE_CASE,
    )
    private val labelCardRegex = Regex(
        "(?:card|crd|a/c|acct|account|بطاقة|بطاقتك|البطاقة|حساب|حسابك|الحساب)\\s*(?:no\\.?|number|رقم)?\\s*[:：]?\\s*(\\d{4})(?!\\d)",
        RegexOption.IGNORE_CASE,
    )
    private val numericDateRegex = Regex("(?<![\\d.])(\\d{1,2})[/\\-.](\\d{1,2})[/\\-.](\\d{2,4})(?![\\d])")
    private val isoDateRegex = Regex("(?<!\\d)(\\d{4})-(\\d{1,2})-(\\d{1,2})(?!\\d)")
    private val textDateRegex = Regex(
        "(?<!\\d)(\\d{1,2})[\\s\\-]?(JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)[A-Za-z]*[\\s\\-,]*(\\d{2,4})(?!\\d)",
        RegexOption.IGNORE_CASE,
    )
    private val timeRegex = Regex("(?<![\\d.:])(\\d{1,2}):(\\d{2})(?::(\\d{2}))?\\s*(AM|PM|ص|م)?(?![\\d])", RegexOption.IGNORE_CASE)

    private val merchantPatterns: List<Regex> = listOf(
        "(?:merchant|التاجر|المتجر|الجهة|المستفيد|beneficiary)\\s*[:：]\\s*([^\\n,،]+)",
        "\\bat\\s+([^\\n]+?)" + END_EN,
        "$AR_NB(?:لدى|عند|لصالح)\\s+([^\\n]+?)" + END_AR,
        "\\b(?:to|from)\\s+([^\\n]+?)" + END_EN,
        "$AR_NB(?:إلى|الى|من)\\s+([^\\n]+?)" + END_AR,
        "\\bin\\s+([^\\n]+?)" + END_EN,
        "${AR_NB}في\\s+([^\\n]+?)" + END_AR,
    ).map { Regex(it, RegexOption.IGNORE_CASE) }

    private val balanceMarkers = listOf("bal", "balance", "avl", "available", "الرصيد", "رصيد", "limit", "الحد", "المتاح", "متاح")

    fun parse(sender: String, rawBody: String): ParsedSms? {
        val body = normalize(rawBody)
        if (body.isBlank()) return null

        val bank = identifyBank(sender, body)
        val senderKnown = bank != null || isExtraSender(sender)
        if (!senderKnown && !config.acceptUnknownSenders) return null
        if (containsAny(body, config.ignoreKeywords)) return null

        val rules = (bank?.rules ?: emptyList()) + config.genericRules
        var result: ParsedSms? = null
        for (rule in rules) {
            val rx = rule.compiled
            val m = rx.find(body) ?: continue
            val amount = rx.group(m, "amount")?.let(::toAmount) ?: continue
            if (amount.signum() <= 0) continue
            result = ParsedSms(
                type = rule.type,
                amount = amount,
                currency = SmsPatterns.currencyCode(rx.group(m, "currency") ?: findCurrencyNear(body), config.defaultCurrency),
                merchant = rx.group(m, "merchant")?.let(::cleanMerchant),
                cardLast4 = rx.group(m, "card"),
                dateTime = rx.group(m, "date")?.let { parseDate(it) }?.let { d -> LocalDateTime.of(d, findTime(body) ?: LocalTime.NOON) },
                balance = null,
                bankId = bank?.id, bankName = bank?.nameEn,
                ruleId = rule.id,
                categoryHint = rule.categoryHint,
            )
            break
        }
        if (result == null) result = heuristic(body, bank) ?: return null

        // Explicit income wording ("لقد استلمت OMR …", "received", "أودع") wins over a generic debit match / heuristic.
        if (result.type == SmsTxnType.DEBIT && result.ruleId.let { it == "heuristic" || it.startsWith("gen_") } && strongCredit(body)) {
            result = result.copy(type = SmsTxnType.CREDIT, categoryHint = null, incomeWording = true)
        } else if (result.ruleId in INCOME_RULES) result = result.copy(incomeWording = true)
        val merchant = result.merchant ?: findMerchant(body)
        val topup = if (result.type == SmsTxnType.DEBIT) config.walletTopups.firstOrNull { it.matches(merchant) || it.matches(body) && topupWording(body) } else null
        val lower = body.lowercase()
        return result.copy(
            atm = result.type == SmsTxnType.DEBIT && topup == null && isAtm(lower),
            mentionedLast4 = allCards(body),
            style = styleOf(lower),
            transferLike = result.type == SmsTxnType.DEBIT && (result.categoryHint == CategoryHints.P2P || topup != null || TRANSFER_WORDS.any { containsWord(lower, it) }),
            merchant = merchant,
            cardLast4 = result.cardLast4 ?: findCard(body),
            dateTime = result.dateTime ?: findDateTime(body),
            balance = findBalance(body),
            ref = findRef(body),
            topup = topup,
        )
    }

    /** Strong income wording that is not preceded by an explicit debit wording. */
    fun strongCredit(body: String): Boolean {
        val lower = body.lowercase()
        val c = STRONG_CREDIT.map { indexOfWord(lower, it) }.filter { it >= 0 }.minOrNull() ?: return false
        val d = STRONG_DEBIT.map { indexOfWord(lower, it) }.filter { it >= 0 }.minOrNull()
        return d == null || c < d
    }

    private fun topupWording(body: String) = Regex("top\\s*-?\\s*up|شحن", RegexOption.IGNORE_CASE).containsMatchIn(body)

    fun isAtm(lower: String): Boolean = ATM_WORDS.any { containsWord(lower, it) }

    /** Card-style vs account-style wording: whichever word comes first ("Card of a/c …" = card, "حسابكم رقم …" = account). */
    fun styleOf(lower: String): SmsStyle {
        val card = Regex("\\bcard\\b|بطاق").find(lower)?.range?.first
        val acct = Regex("\\ba/?c\\b|\\baccount\\b|\\bacct\\b|حساب").find(lower)?.range?.first
        return when {
            card == null && acct == null -> SmsStyle.UNKNOWN
            acct == null -> SmsStyle.CARD
            card == null -> SmsStyle.ACCOUNT
            card < acct -> SmsStyle.CARD
            else -> SmsStyle.ACCOUNT
        }
    }

    /** All masked card/account suffixes in the body (last 4 digits), in order, without duplicates. */
    fun allCards(body: String): List<String> =
        (maskedCardRegex.findAll(body).map { it.groupValues[1].takeLast(4) } + endingCardRegex.findAll(body).map { it.groupValues[1] })
            .distinct().toList()

    /** Bank transaction id ("Txn Id BMCT010000000114", "Ref No. 12345678", "رقم العملية …"); must contain a digit. */
    fun findRef(body: String): String? = refRegex.findAll(body)
        .mapNotNull { m -> (m.groups[1] ?: m.groups[2])?.value }
        .firstOrNull { r -> r.any { it.isDigit() } }
        ?.uppercase()

    /** Stable hash for de-duplication (same SMS from receiver and from inbox scan). */
    fun hash(sender: String, body: String): String {
        val key = sender.filter { it.isLetterOrDigit() }.lowercase() + "|" + normalize(body)
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
        val hex = "0123456789abcdef"
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) { val v = b.toInt() and 0xff; sb.append(hex[v shr 4]).append(hex[v and 0x0f]) }
        return sb.toString()
    }

    fun identifyBank(sender: String, body: String = ""): BankProfile? {
        val s = normalizeSender(sender)
        if (s.isNotEmpty()) {
            config.banks.firstOrNull { b -> b.senderIds.any { normalizeSender(it) == s } }?.let { return it }
            config.banks.firstOrNull { b -> b.senderIds.any { val id = normalizeSender(it); id.length >= 3 && s.contains(id) } }
                ?.let { return it }
        }
        // Sender could be a short code / extra sender: try the body
        if (isExtraSender(sender) || config.acceptUnknownSenders) {
            val lower = body.lowercase()
            return config.banks.firstOrNull { b -> b.bodyNames.any { containsWord(lower, it.lowercase()) } }
        }
        return null
    }

    private fun isExtraSender(sender: String): Boolean {
        val s = normalizeSender(sender)
        return s.isNotEmpty() && config.extraSenderIds.any { normalizeSender(it) == s }
    }

    private fun heuristic(body: String, bank: BankProfile?): ParsedSms? {
        val type = detectType(body) ?: return null
        val amt = findAmount(body) ?: return null
        return ParsedSms(
            type = type, amount = amt.first, currency = amt.second,
            merchant = null, cardLast4 = null, dateTime = null, balance = null,
            bankId = bank?.id, bankName = bank?.nameEn, ruleId = "heuristic",
        )
    }

    /** Earliest debit/credit keyword wins. "debit card"/"credit card" are not direction words. */
    fun detectType(body: String): SmsTxnType? {
        val text = body.lowercase()
            .replace(Regex("(debit|credit)\\s+card"), "card")
            .replace(Regex("بطاقة\\s+(?:ال)?(?:خصم|ائتمان|الائتمان|مدين)"), "بطاقة")
        val d = firstIndex(text, config.debitKeywords)
        val c = firstIndex(text, config.creditKeywords)
        return when {
            d < 0 && c < 0 -> null
            d < 0 -> SmsTxnType.CREDIT
            c < 0 -> SmsTxnType.DEBIT
            d <= c -> SmsTxnType.DEBIT
            else -> SmsTxnType.CREDIT
        }
    }

    /** First amount that is not a balance/limit. Returns amount + ISO currency. */
    fun findAmount(body: String): Pair<BigDecimal, String>? {
        for (m in amountRegex.findAll(body)) {
            if (isBalanceContext(body, m.range.first)) continue
            val num = amountNamed.group(m, "amt1") ?: amountNamed.group(m, "amt2") ?: continue
            val cur = amountNamed.group(m, "cur1") ?: amountNamed.group(m, "cur2")
            val amount = toAmount(num) ?: continue
            if (amount.signum() <= 0) continue
            return amount to SmsPatterns.currencyCode(cur, config.defaultCurrency)
        }
        return null
    }

    fun findBalance(body: String): BigDecimal? {
        for (m in amountRegex.findAll(body)) {
            if (!isBalanceContext(body, m.range.first)) continue
            val num = amountNamed.group(m, "amt1") ?: amountNamed.group(m, "amt2") ?: continue
            return toAmount(num)
        }
        return null
    }

    private fun findCurrencyNear(body: String): String? =
        amountRegex.find(body)?.let { amountNamed.group(it, "cur1") ?: amountNamed.group(it, "cur2") }

    private fun isBalanceContext(body: String, start: Int): Boolean {
        val before = body.substring((start - 28).coerceAtLeast(0), start).lowercase()
        return balanceMarkers.any { before.contains(it) }
    }

    fun findCard(body: String): String? =
        maskedCardRegex.find(body)?.groupValues?.get(1)?.takeLast(4)
            ?: endingCardRegex.find(body)?.groupValues?.get(1)
            ?: labelCardRegex.find(body)?.groupValues?.get(1)

    fun findMerchant(body: String): String? {
        for (p in merchantPatterns) {
            var start = 0
            while (start < body.length) {
                val m = p.find(body, start) ?: break
                cleanMerchant(m.groupValues[1])?.let { return it }
                start = m.range.first + 1
            }
        }
        return null
    }

    private fun cleanMerchant(raw: String): String? {
        var s = raw.trim()
        // cut at common trailers
        val cut = Regex(
            "(\\s+(?:to|from|in|into)\\s+(?:your|a/c|acc|card)|\\s+(?:إلى|الى|من|في|على)\\s+(?:حساب|بطاق|ال?حساب)|\\s+(?:has|have|was|is)\\s|\\s+on\\s+|\\s+dated|\\s+date\\b|\\s+at\\s+\\d|\\s+using|\\s+via|\\s+with\\s|\\s+avl|\\s+available|\\s+bal\\b|\\s+balance|\\s+ref\\b|\\s+card\\b|" +
                "\\s+بتاريخ|\\s+يوم|\\s+الساعة|\\s+الرصيد|\\s+رصيد|\\s+المتاح|\\s+عبر|\\s+باستخدام|\\s+ببطاقة|\\s+بطاقة|\\s+رقم|\\s+في\\s+\\d|" +
                "\\s+\\d{1,2}[/\\-.]\\d{1,2}[/\\-.]\\d{2,4}|\\s+\\d{1,2}:\\d{2}|[.,،;!؛]\\s|[.,،;!؛]$)",
            RegexOption.IGNORE_CASE,
        ).find(s)
        if (cut != null) s = s.substring(0, cut.range.first)
        s = s.replace(Regex("\\s+"), " ")
            // trailing country code: "DEMO BAKERY , OMN"
            .replace(Regex("\\s*,\\s*(?:OMN|OMAN|OM)\\s*$", RegexOption.IGNORE_CASE), "")
        s = s.trim().trim('.', ',', '،', ':', '-', '*', ' ')
        if (s.length < 2) return null
        val lower = s.lowercase()
        val rejectStarts = listOf(
            "your", "you", "a/c", "ac ", "acc", "account", "card", "the account", "xx", "**", "my ", "atm card",
            "حساب", "بطاق", "رصيد", "الرصيد", "تاريخ", "الساعة", "خلال", "مبلغ",
        )
        if (rejectStarts.any { lower.startsWith(it) }) return null
        if (s.first().isDigit()) return null
        if (amountRegex.matches(s)) return null
        if (Regex("^(?:${SmsPatterns.CURRENCY})", RegexOption.IGNORE_CASE).containsMatchIn(s)) return null
        if (s.length > 40) s = s.substring(0, 40).trim()
        return s
    }

    fun findDateTime(body: String): LocalDateTime? {
        val date = findDate(body) ?: return null
        return LocalDateTime.of(date, findTime(body) ?: LocalTime.NOON)
    }

    private fun findDate(body: String): LocalDate? {
        isoDateRegex.find(body)?.let { m ->
            val (y, mo, d) = m.destructured
            safeDate(y.toInt(), mo.toInt(), d.toInt())?.let { return it }
        }
        numericDateRegex.find(body)?.let { m ->
            val (d, mo, y) = m.destructured
            safeDate(fullYear(y.toInt()), mo.toInt(), d.toInt())?.let { return it }
        }
        textDateRegex.find(body)?.let { m ->
            val (d, mon, y) = m.destructured
            safeDate(fullYear(y.toInt()), MONTHS.indexOf(mon.uppercase().take(3)) + 1, d.toInt())?.let { return it }
        }
        return null
    }

    private fun parseDate(s: String): LocalDate? = findDate(s)

    private fun findTime(body: String): LocalTime? {
        val m = timeRegex.find(body) ?: return null
        var h = m.groupValues[1].toInt()
        val min = m.groupValues[2].toInt()
        val sec = m.groupValues[3].toIntOrNull() ?: 0
        val ap = m.groupValues[4].uppercase()
        if (ap == "PM" || ap == "م") { if (h < 12) h += 12 }
        if (ap == "AM" || ap == "ص") { if (h == 12) h = 0 }
        if (h !in 0..23 || min !in 0..59 || sec !in 0..59) return null
        return LocalTime.of(h, min, sec)
    }

    private fun fullYear(y: Int) = if (y < 100) 2000 + y else y

    private fun safeDate(y: Int, m: Int, d: Int): LocalDate? =
        if (y in 2000..2100 && m in 1..12 && d in 1..31) runCatching { LocalDate.of(y, m, d) }.getOrNull() else null

    private fun toAmount(s: String): BigDecimal? = runCatching { BigDecimal(s.replace(",", "")) }.getOrNull()

    private fun containsAny(body: String, keywords: List<String>): Boolean {
        val lower = body.lowercase()
        return keywords.any { containsWord(lower, it.lowercase().trim()) }
    }

    private fun firstIndex(lower: String, keywords: List<String>): Int {
        var best = -1
        for (k in keywords) {
            val i = indexOfWord(lower, k.lowercase().trim())
            if (i >= 0 && (best < 0 || i < best)) best = i
        }
        return best
    }

    companion object {
        private const val AR_NB = "(?<![\\u0621-\\u064A])"
        private const val END_EN =
            "(?=\\s+(?:has|have|was|is)\\s|\\s+on\\s|\\s+dated|\\s+for\\s|\\s+using|\\s+via|\\s+avl|\\s+available|\\s+bal\\b|\\s+card\\b|\\s+ref\\b|[.,]\\s|[.,]?$|\\n|$)"
        private const val END_AR =
            "(?=\\s+بتاريخ|\\s+يوم|\\s+الساعة|\\s+الرصيد|\\s+رصيد|\\s+عبر|\\s+في\\s+\\d|[.،,]\\s|[.،,]?$|\\n|$)"
        private val STRONG_CREDIT = listOf(
            "لقد استلمت", "استلمت", "تم استلام", "received", "you have received", "credited", "deposited",
            "أودع", "اودع", "أُودع", "تم إيداع", "تم ايداع", "إيداع", "ايداع",
        )
        private val INCOME_RULES = setOf("gen_istalamt_ar", "gen_istalamt_amt_first_ar", "gen_ooda3_ar", "gen_ooda3_amt_first_ar", "gen_received_first_en")
        private val STRONG_DEBIT = listOf(
            "debited", "deducted", "you have sent", "sent", "spent", "paid", "purchase", "withdrawn", "withdrawal",
            "تم خصم", "خصم", "سحب", "دفع", "شراء", "أرسلت", "ارسلت", "تحويل صادر", "تم تحويل",
        )
        private val ATM_WORDS = listOf("atm", "cash withdrawal", "الصراف", "صراف آلي", "الصراف الآلي", "سحب نقدي", "سحب نقود")
        private val TRANSFER_WORDS = listOf(
            "transfer", "transferred", "sent", "you have sent", "topup", "top up", "top-up", "remittance",
            "تحويل", "حوالة", "أرسلت", "ارسلت", "شحن",
        )
        private val MONTHS = listOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")

        fun normalizeSender(s: String): String = s.filter { it.isLetterOrDigit() }.lowercase()

        /** Arabic-Indic digits → ASCII, Arabic separators → ASCII, strip bidi marks, collapse spaces. */
        fun normalize(input: String): String {
            val sb = StringBuilder(input.length)
            for (ch in input) {
                when (ch) {
                    in '\u0660'..'\u0669' -> sb.append('0' + (ch - '\u0660'))
                    in '\u06F0'..'\u06F9' -> sb.append('0' + (ch - '\u06F0'))
                    '\u066B' -> sb.append('.')
                    '\u066C' -> sb.append(',')
                    '\u200E', '\u200F', '\u202A', '\u202B', '\u202C', '\u202D', '\u202E', '\u2066', '\u2067', '\u2068', '\u2069', '\u0640' -> {}
                    '\u00A0', '\t', '\r' -> sb.append(' ')
                    else -> sb.append(ch)
                }
            }
            return sb.toString()
                .replace(Regex("[ ]{2,}"), " ")
                .replace(Regex(" *\\n+ *"), "\n")
                .trim()
        }

        /** Word-boundary aware search for ASCII keywords; plain contains for Arabic. */
        fun indexOfWord(lower: String, kw: String): Int {
            if (kw.isEmpty()) return -1
            val ascii = kw.all { it.code < 128 }
            if (!ascii) return lower.indexOf(kw)
            var from = 0
            while (true) {
                val i = lower.indexOf(kw, from)
                if (i < 0) return -1
                val beforeOk = i == 0 || !lower[i - 1].isLetter()
                val afterIdx = i + kw.length
                val afterOk = afterIdx >= lower.length || !lower[afterIdx].isLetter()
                if (beforeOk && afterOk) return i
                from = i + 1
            }
        }

        fun containsWord(lower: String, kw: String) = indexOfWord(lower, kw) >= 0
    }
}

