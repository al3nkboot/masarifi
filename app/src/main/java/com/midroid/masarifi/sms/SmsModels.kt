package com.midroid.masarifi.sms

import java.math.BigDecimal
import java.time.LocalDateTime

/** Direction of money detected in a bank SMS. */
enum class SmsTxnType { DEBIT, CREDIT }

/** Result of parsing one bank SMS. Pure data, no Android dependencies. */
data class ParsedSms(
    val type: SmsTxnType,
    val amount: BigDecimal,
    val currency: String,
    val merchant: String?,
    val cardLast4: String?,
    val dateTime: LocalDateTime?,
    val balance: BigDecimal?,
    val bankId: String?,
    val bankName: String?,
    val ruleId: String,
    /** Bank transaction reference (e.g. "Txn Id BMCT…"), used to de-duplicate re-sent / re-worded messages. */
    val ref: String? = null,
    /** Category hint from the rule, e.g. [CategoryHints.P2P] for money sent to a person. */
    val categoryHint: String? = null,
    /** Set when the merchant is one of the user's own wallets (top-up) → recorded as a transfer, not an expense. */
    val topup: WalletTopup? = null,
    /** ATM / cash withdrawal (recorded as a transfer to the Cash account, not an expense). */
    val atm: Boolean = false,
    /** Every masked card/account suffix (last 4) found in the body, e.g. source and destination of a transfer. */
    val mentionedLast4: List<String> = emptyList(),
    /** "card" vs "account" wording of the SMS (banks such as Bank Dhofar send one of each for the same operation). */
    val style: SmsStyle = SmsStyle.UNKNOWN,
    /** Money moved to another account/person (sent / transfer / top-up wording). */
    val transferLike: Boolean = false,
    /** Direction comes from explicit income wording («استلمت», «أودع», "received" …), see [SmsParser.strongCredit]. */
    val incomeWording: Boolean = false,
)

enum class SmsStyle { CARD, ACCOUNT, UNKNOWN }

object CategoryHints {
    /** Money sent to a person (mobile payment / transfer) → «تحويلات لأشخاص». */
    const val P2P = "p2p"
}

/**
 * "Own wallet top-up" rule: card/account payments whose merchant matches one of [patterns] are top-ups of the user's
 * own wallet and are recorded as a TRANSFER from the paying account to the wallet account.
 * Patterns are plain text, matched case-insensitively ignoring spaces/punctuation ("ThawaniPay Topup" = "thawani pay top-up").
 * Target: the account with [targetAccountUuid] if set, else the wallet account of provider [targetBankId] (created if missing).
 */
data class WalletTopup(
    val id: String,
    val nameAr: String,
    val nameEn: String,
    val patterns: List<String>,
    val targetBankId: String? = null,
    val targetAccountUuid: String? = null,
) {
    fun name(arabic: Boolean) = if (arabic) nameAr else nameEn

    fun matches(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val t = key(text)
        return patterns.any { val k = key(it); k.length >= 3 && t.contains(k) }
    }

    companion object {
        fun key(s: String) = s.filter { it.isLetterOrDigit() }.lowercase()
    }
}

/**
 * A configurable regex rule. The pattern may use these readable tokens which are expanded to
 * named groups before compiling:
 *  {CUR} currency, {AMT} amount, {CARD} masked card/account digits, {MERCHANT} merchant (lazy),
 *  {DATE} a date, {ANY} lazy anything.
 * Raw named groups (?<amount>..), (?<currency>..), (?<merchant>..), (?<card>..), (?<date>..) are also accepted.
 */
data class SmsRule(
    val id: String,
    val pattern: String,
    val type: SmsTxnType,
    val categoryHint: String? = null,
) {
    val compiled: NamedRegex by lazy { SmsPatterns.compile(pattern) }
}

data class BankProfile(
    val id: String,
    val nameEn: String,
    val nameAr: String,
    /** Sender IDs as they appear on the phone (case/space-insensitive). */
    val senderIds: List<String>,
    /** Words in the body that identify the bank when the sender is unknown. */
    val bodyNames: List<String> = emptyList(),
    val rules: List<SmsRule> = emptyList(),
    /** Extra bank-specific debit/credit words for the keyword heuristic (added to the global lists). */
    val debitKeywords: List<String> = emptyList(),
    val creditKeywords: List<String> = emptyList(),
    /** Name of auto-created accounts (default: bank name), e.g. "ثواني – بطاقة موجب" → "ثواني – بطاقة موجب ••5501". */
    val accountNameAr: String? = null,
    val accountNameEn: String? = null,
    /** Prepaid card / e-wallet provider (accounts are created as cards; one wallet account is reused). */
    val wallet: Boolean = false,
) {
    fun accountName(arabic: Boolean) = if (arabic) accountNameAr ?: nameAr else accountNameEn ?: nameEn
}

data class SmsParserConfig(
    val banks: List<BankProfile>,
    val genericRules: List<SmsRule>,
    val ignoreKeywords: List<String>,
    val debitKeywords: List<String>,
    val creditKeywords: List<String>,
    /** Extra sender IDs added by the user in settings. */
    val extraSenderIds: List<String> = emptyList(),
    /** If true, messages from unknown senders are parsed when they look like a bank transaction. */
    val acceptUnknownSenders: Boolean = false,
    val defaultCurrency: String = "OMR",
    /** Own-wallet top-up rules (merchant → transfer to wallet). */
    val walletTopups: List<WalletTopup> = emptyList(),
)
