package com.midroid.masarifi.sms

import com.midroid.masarifi.sms.SmsTxnType.CREDIT
import com.midroid.masarifi.sms.SmsTxnType.DEBIT

/**
 * Default, editable configuration of known Omani bank sender IDs and regex rules.
 * Bank-specific rules are tried first, then generic rules, then a keyword heuristic.
 */
object DefaultSmsConfig {

    val banks: List<BankProfile> = listOf(
        BankProfile(
            id = "bankmuscat", nameEn = "Bank Muscat", nameAr = "بنك مسقط",
            senderIds = listOf("bank muscat", "BankMuscat", "BANKMUSCAT", "Bank-Muscat", "BMUSCAT", "BM", "MeethaqIB", "Meethaq"),
            bodyNames = listOf("bank muscat", "bankmuscat", "بنك مسقط", "meethaq", "ميثاق"),
            rules = listOf(
                // "Card of a/c 0100XXXXXXXX0099 used for OMR 50.000 at ThawaniPay Topup DEMO01 on 15/01/2025 10:00:00. Avl Bal OMR 500.000."
                SmsRule("bm_card_used_en", "card\\s+of\\s+a/?c\\s*{CARD}\\s+used\\s+for\\s+{CUR}\\s?{AMT}\\s+at\\s+{MERCHANT}\\s+on\\s+{DATE}", DEBIT),
                // "Dear Customer, You have sent OMR 30.000 to ABCD######E XY from your a/c 0100XXXXXXXX0099 on 16/01/2025 14:30:00 using Mobile Payment services. Txn Id … Avl Bal OMR …"
                SmsRule("bm_mobile_sent_en", "(?:you\\s+have\\s+)?sent\\s+{CUR}\\s?{AMT}\\s+to\\s+{MERCHANT}\\s+from\\s+your\\s+a/?c\\s*{CARD}\\s+on\\s+{DATE}", DEBIT, CategoryHints.P2P),
                SmsRule("bm_card_purchase_en", "(?:purchase|txn|transaction) of {CUR}\\s?{AMT}{ANY}card{ANY}{CARD}{ANY}\\bat\\s+{MERCHANT}(?=\\s+on\\s|\\s+dated|\\.\\s|\\.?$|\\s*,|\\s+avl|\\s+bal)", DEBIT),
                SmsRule("bm_acct_debit_en", "a/?c{ANY}{CARD}{ANY}(?:debited|deducted){ANY}{CUR}\\s?{AMT}", DEBIT),
                SmsRule("bm_acct_credit_en", "a/?c{ANY}{CARD}{ANY}credited{ANY}{CUR}\\s?{AMT}", CREDIT),
            ),
        ),
        BankProfile(
            id = "thawani", nameEn = "Thawani Pay", nameAr = "ثواني",
            senderIds = listOf("Thawani Pay", "ThawaniPay", "Thawani", "THAWANI", "Thawani-Pay"),
            bodyNames = listOf("thawani", "mojab", "ثواني", "موجب"),
            rules = listOf(
                // "Dear Customer, Your Mojab Card 4000XXXXXXXX5501 used successfully for .250 OMR at DEMO BAKERY  , OMN on 15/01/2025 . Your available balance is 48.250 OMR."
                SmsRule(
                    "thawani_card_used_en",
                    "card\\s*{CARD}\\s+used\\s+successfully\\s+for\\s+{AMT}\\s*{CUR}\\s+at\\s+{MERCHANT}\\s*(?:,\\s*[a-z]{2,3})?\\s+on\\s+{DATE}",
                    DEBIT,
                ),
                SmsRule("thawani_card_used_cur_first_en", "card\\s*{CARD}\\s+used\\s+successfully\\s+for\\s+{CUR}\\s?{AMT}\\s+at\\s+{MERCHANT}\\s*(?:,\\s*[a-z]{2,3})?\\s+on\\s+{DATE}", DEBIT),
            ),
            accountNameAr = "ثواني – بطاقة موجب", accountNameEn = "Thawani – Mojab card", wallet = true,
        ),
        BankProfile(
            id = "nbo", nameEn = "National Bank of Oman", nameAr = "البنك الوطني العماني",
            senderIds = listOf("NBO", "NBOOman", "NBO Oman", "NBO-Oman", "Muzn", "MuznIB"),
            bodyNames = listOf("nbo", "national bank of oman", "البنك الوطني العماني", "مزن"),
        ),
        BankProfile(
            id = "sohar", nameEn = "Sohar International", nameAr = "صحار الدولي",
            senderIds = listOf("SoharIntl", "Sohar Intl", "SOHARINTL", "SoharBank", "Sohar", "SoharIslamic"),
            bodyNames = listOf("sohar international", "صحار الدولي", "بنك صحار", "sohar islamic"),
        ),
        BankProfile(
            id = "dhofar", nameEn = "Bank Dhofar", nameAr = "بنك ظفار",
            senderIds = listOf("BankDhofar", "Bank Dhofar", "BKDHOFAR", "BDHOFAR", "Dhofar", "MaisarahIB", "Maisarah"),
            bodyNames = listOf("bank dhofar", "بنك ظفار", "maisarah", "ميسرة"),
            rules = listOf(
                // "حسابكم رقم 0200XXX0007001 قد أدخل فيه مبلغ OMR 2.000 بتاريخ 2025-01-10. رصيدكم الحالي هو OMR 5.000"
                SmsRule("bd_credit_ar", "حساب(?:كم|ك)?\\s+رقم\\s+\\S+\\s+(?:قد\\s+)?(?:تم\\s+)?[أا]دخل\\s+(?:فيه|إليه|اليه)\\s+مبلغ\\s*{CUR}\\s*{AMT}", CREDIT),
                // debit variant (generic wording): "… قد خصم منه / سحب منه مبلغ OMR 5.000 …"
                SmsRule("bd_debit_ar", "حساب(?:كم|ك)?\\s+رقم\\s+\\S+\\s+(?:قد\\s+)?(?:تم\\s+)?(?:خصم|سحب|صرف)\\s+(?:منه\\s+)?مبلغ\\s*{CUR}\\s*{AMT}", DEBIT),
            ),
        ),
        BankProfile(
            id = "oab", nameEn = "Oman Arab Bank", nameAr = "بنك عمان العربي",
            senderIds = listOf("OAB", "OABOman", "OAB Oman", "OmanArabBank", "AlYusr", "Al Yusr"),
            bodyNames = listOf("oman arab bank", "oab", "بنك عمان العربي", "اليسر"),
        ),
        BankProfile(
            id = "hsbc", nameEn = "HSBC Oman", nameAr = "إتش إس بي سي عُمان",
            senderIds = listOf("HSBC", "HSBCOM", "HSBC Oman", "HSBCOman"),
            bodyNames = listOf("hsbc"),
        ),
        BankProfile(
            id = "ahli", nameEn = "Ahli Bank", nameAr = "البنك الأهلي",
            senderIds = listOf("ahlibank", "Ahli Bank", "AhliBank", "AlHilal"),
            bodyNames = listOf("ahli bank", "ahlibank", "البنك الأهلي"),
        ),
        BankProfile(
            id = "nizwa", nameEn = "Bank Nizwa", nameAr = "بنك نزوى",
            senderIds = listOf("BankNizwa", "Bank Nizwa", "BNIZWA"),
            bodyNames = listOf("bank nizwa", "بنك نزوى"),
        ),
        BankProfile(
            id = "alizz", nameEn = "Alizz Islamic Bank", nameAr = "العز الإسلامي",
            senderIds = listOf("alizz", "AlizzIslamic", "Alizz Bank"),
            bodyNames = listOf("alizz", "العز الإسلامي"),
        ),
    )

    /** Generic rules that work for many banks (EN + AR). Tried after bank-specific rules. */
    val genericRules: List<SmsRule> = listOf(
        // English: "OMR 4.300 has been debited from your card XXXX1234 at LULU ..."
        SmsRule("gen_amt_first_debit_en", "{CUR}\\s?{AMT}\\s+(?:has been|was|is)?\\s*(?:debited|deducted|spent|charged|withdrawn|paid)", DEBIT),
        SmsRule("gen_amt_first_credit_en", "{CUR}\\s?{AMT}\\s+(?:has been|was|is)?\\s*(?:credited|deposited|received|refunded)", CREDIT),
        // English: "Your a/c XX1234 has been debited with/by/for OMR 12.500"
        SmsRule("gen_debited_with_en", "(?:debited|deducted|charged)\\s+(?:with|by|for|of)?\\s*{CUR}\\s?{AMT}", DEBIT),
        SmsRule("gen_credited_with_en", "(?:credited|deposited)\\s+(?:with|by|for|of)?\\s*{CUR}\\s?{AMT}", CREDIT),
        // English: "Purchase of OMR 4.300 at ...", "POS transaction of RO 3.000", "ATM withdrawal of OMR 20"
        SmsRule("gen_purchase_en", "(?:purchase|pos(?: transaction| txn| purchase)?|e-?commerce(?: transaction)?|online payment|payment|withdrawal|cash withdrawal|transfer)\\s+(?:of|for)\\s+{CUR}\\s?{AMT}", DEBIT),
        SmsRule("gen_salary_en", "(?:salary|refund|deposit|transfer|remittance)\\s+(?:of|for)\\s+{CUR}\\s?{AMT}\\s+(?:has been |was |is )?(?:credited|received|deposited)", CREDIT),
        SmsRule("gen_you_spent_en", "(?:you(?: have)? (?:spent|paid))\\s+{CUR}\\s?{AMT}", DEBIT),
        // English: "You have sent OMR 30.000 to NAME from your a/c …" (mobile payment to a person)
        SmsRule("gen_sent_to_en", "(?:you(?:\\s+have)?\\s+)?sent\\s+{CUR}\\s?{AMT}\\s+to\\s+{MERCHANT}(?=\\s+from\\s|\\s+on\\s|\\s+using\\s|\\.\\s|\\.?$)", DEBIT, CategoryHints.P2P),
        // English: "Your … card XXXX1234 used (successfully) for 4.300 OMR at MERCHANT, OMN on …"
        SmsRule("gen_card_used_en", "used\\s+(?:successfully\\s+)?for\\s+{AMT}\\s*{CUR}\\s+at\\s+{MERCHANT}\\s*(?:,\\s*[a-z]{2,3})?(?=\\s+on\\s|\\.\\s|\\.?$)", DEBIT),
        SmsRule("gen_card_used_cur_first_en", "used\\s+(?:successfully\\s+)?for\\s+{CUR}\\s?{AMT}\\s+at\\s+{MERCHANT}\\s*(?:,\\s*[a-z]{2,3})?(?=\\s+on\\s|\\.\\s|\\.?$)", DEBIT),
        SmsRule("gen_you_received_en", "(?:you(?: have)? received)\\s+{CUR}\\s?{AMT}", CREDIT),
        // Arabic: "تم خصم مبلغ 4.300 ر.ع. من بطاقتك ..." / "تم خصم ر.ع 4.300"
        SmsRule("gen_khasm_ar", "(?:تم\\s+)?(?:خصم|سحب|دفع|شراء)(?:\\s+مبلغ)?\\s*{AMT}\\s*{CUR}", DEBIT),
        SmsRule("gen_khasm_cur_first_ar", "(?:تم\\s+)?(?:خصم|سحب|دفع|شراء)(?:\\s+مبلغ)?\\s*{CUR}\\s*{AMT}", DEBIT),
        SmsRule("gen_purchase_ar", "عملية\\s+(?:شراء|سحب|دفع)(?:\\s+(?:بمبلغ|بقيمة|مبلغ))?\\s*(?:{CURX}\\s*)?{AMT}\\s*{CUR}?", DEBIT),
        SmsRule("gen_ida3_ar", "(?:تم\\s+)?(?:إيداع|ايداع|استلام|إضافة|اضافة|تحويل\\s+وارد|حوالة\\s+واردة)(?:\\s+مبلغ)?\\s*{AMT}\\s*{CUR}", CREDIT),
        SmsRule("gen_ida3_cur_first_ar", "(?:تم\\s+)?(?:إيداع|ايداع|استلام|إضافة|اضافة|تحويل\\s+وارد|حوالة\\s+واردة)(?:\\s+مبلغ)?\\s*{CUR}\\s*{AMT}", CREDIT),
        // Arabic (Bank Dhofar style): "قد أدخل فيه مبلغ OMR 2.000" (credit) / "قد خصم منه مبلغ OMR 2.000" (debit)
        SmsRule("gen_adkhal_ar", "(?:قد\\s+)?(?:تم\\s+)?[أا]دخل\\s+(?:فيه|إليه|اليه|في\\s+حساب\\S*)\\s+مبلغ\\s*{CUR}\\s*{AMT}", CREDIT),
        SmsRule("gen_adkhal_amt_first_ar", "(?:قد\\s+)?(?:تم\\s+)?[أا]دخل\\s+(?:فيه|إليه|اليه|في\\s+حساب\\S*)\\s+مبلغ\\s*{AMT}\\s*{CUR}", CREDIT),
        SmsRule("gen_khasm_minhu_ar", "(?:قد\\s+)?(?:تم\\s+)?(?:خصم|سحب|صرف)\\s+منه\\s+مبلغ\\s*{CUR}\\s*{AMT}", DEBIT),
        SmsRule("gen_khasm_minhu_amt_first_ar", "(?:قد\\s+)?(?:تم\\s+)?(?:خصم|سحب|صرف)\\s+منه\\s+مبلغ\\s*{AMT}\\s*{CUR}", DEBIT),
        SmsRule("gen_madin_ar", "(?:مدين|خصم|سحب)\\s*(?:بمبلغ|بقيمة|مبلغ)?\\s*[:：]?\\s*(?:{CURX}\\s*)?{AMT}\\s*{CUR}", DEBIT),
        SmsRule("gen_daen_ar", "(?:دائن|إيداع|ايداع)\\s*(?:بمبلغ|بقيمة|مبلغ)?\\s*[:：]?\\s*(?:{CURX}\\s*)?{AMT}\\s*{CUR}", CREDIT),
    )

    val ignoreKeywords: List<String> = listOf(
        // OTP / security
        "otp", "one time password", "one-time password", "verification code", "passcode", "pin", "do not share",
        "رمز التحقق", "رمز التفعيل", "الرمز السري", "كلمة المرور", "كلمة السر", "لا تشارك", "رمز",
        // failed / declined / future
        "declined", "failed", "unsuccessful", "not successful", "insufficient", "rejected",
        "will be debited", "will be deducted", "is due", "due date", "due on", "reminder", "minimum payment", "statement",
        // card / account status notices
        "suspended", "blocked", "has been stopped", "تم إيقاف", "تم ايقاف", "إيقاف بطاقت", "ايقاف بطاقت", "تم حظر", "تم تجميد",
        "مرفوضة", "مرفوض", "رفض", "لم تتم", "لم تنجح", "غير ناجحة", "فشلت", "فشل", "تذكير", "مستحق", "سيتم خصم",
        // marketing
        "offer", "win ", "promo", "عرض", "اربح", "فرصة", "مسابقة",
    )

    val debitKeywords: List<String> = listOf(
        "debited", "debit", "deducted", "purchase", "purchased", "spent", "withdrawn", "withdrawal", "paid", "payment",
        "pos", "charged", "transfer to", "transferred to", "sent to", "you have sent", "used for", "used successfully", "e-commerce", "ecommerce",
        "خصم منه", "سحب منه",
        "تم خصم", "خصم", "مدين", "شراء", "مشتريات", "سحب", "دفع", "تحويل صادر", "حوالة صادرة", "تم تحويل",
    )

    val creditKeywords: List<String> = listOf(
        "credited", "deposited", "deposit", "received", "salary", "refund", "refunded", "reversal", "cashback", "cash back",
        "transfer from", "transferred from",
        "قد أدخل فيه", "قد ادخل فيه", "أدخل فيه", "ادخل فيه",
        "تم إيداع", "تم ايداع", "إيداع", "ايداع", "دائن", "تم استلام", "استلام", "وارد", "حوالة واردة", "راتب",
        "استرداد", "مسترد", "تمت إضافة", "تمت اضافة",
    )

    /** Default own-wallet top-ups: Thawani Pay (Mojab card) topped up from a bank card / account. */
    val walletTopups: List<WalletTopup> = listOf(
        WalletTopup(
            id = "thawani", nameAr = "شحن محفظة ثواني", nameEn = "Thawani wallet top-up",
            patterns = listOf("ThawaniPay Topup", "Thawani Topup", "Thawani Top-up", "Topup Thawani", "Thawani Pay Top up", "شحن ثواني"),
            targetBankId = "thawani",
        ),
    )

    fun config(
        extraSenderIds: List<String> = emptyList(),
        acceptUnknownSenders: Boolean = false,
        defaultCurrency: String = "OMR",
        walletTopups: List<WalletTopup> = this.walletTopups,
    ) = SmsParserConfig(
        banks = banks,
        genericRules = genericRules,
        ignoreKeywords = ignoreKeywords,
        debitKeywords = debitKeywords,
        creditKeywords = creditKeywords,
        extraSenderIds = extraSenderIds,
        acceptUnknownSenders = acceptUnknownSenders,
        defaultCurrency = defaultCurrency,
        walletTopups = walletTopups,
    )
}
