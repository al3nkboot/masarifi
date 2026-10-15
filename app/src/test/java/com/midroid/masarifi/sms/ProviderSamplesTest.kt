package com.midroid.masarifi.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/** Real (bank-masked) SMS samples from the user's providers, used verbatim. */
class ProviderSamplesTest {
    private val parser = SmsParser()

    companion object {
        const val THAWANI = "Thawani Pay"
        const val BM = "bank muscat"
        const val BD = "BankDhofar"

        const val T1 = "Dear Customer, Your Mojab Card 4000XXXXXXXX5501 used successfully for 25.000 OMR at SAMPLE CLINIC      , OMN on 15/01/2025 . Your available balance is 50.000 OMR."
        const val T2 = "Dear Customer, Your Mojab Card 4000XXXXXXXX5501 used successfully for 1.500 OMR at SAMPLE PHARMACY, OMN on 15/01/2025 . Your available balance is 48.500 OMR."
        const val T3 = "Dear Customer, Your Mojab Card 4000XXXXXXXX5501 used successfully for .250 OMR at DEMO BAKERY  , OMN on 15/01/2025 . Your available balance is 48.250 OMR."
        const val BM_TOPUP = "Card of a/c 0100XXXXXXXX0099 used for OMR 50.000 at ThawaniPay Topup DEMO01 on 15/01/2025 10:00:00. Avl Bal OMR 500.000."
        const val BM_OTP = "[#] OTP 9999. Do not share it with anyone. If you didn't request this call 24795555. AbCdEfGhIjK"
        const val BM_SENT = "Dear Customer, You have sent OMR 30.000 to ABCD######E XY from your a/c 0100XXXXXXXX0099 on 16/01/2025 14:30:00 using Mobile Payment services. Txn Id BMCT010000000114. Avl Bal OMR 470.000."
        const val BD_SUSPENDED = "زبوننا الكريم، تم إيقاف بطاقتك رقم XXXX6610 للمزيد من المعلومات يرجى الاتصال على الرقم 24791111. بنك ظفار - بنكك المفضل."
        const val BD_CREDIT_2 = "حسابكم رقم 0200XXX0007001 قد أدخل فيه مبلغ OMR 2.000 بتاريخ 2025-01-10. رصيدكم الحالي هو OMR 5.000"
        const val BD_CREDIT_200 = "حسابكم رقم 0200XXX0007001 قد أدخل فيه مبلغ OMR 200.000 بتاريخ 2025-01-10. رصيدكم الحالي هو OMR 205.000"
    }

    private fun parse(sender: String, body: String): ParsedSms = parser.parse(sender, body) ?: throw AssertionError("not parsed: $body")
    private fun bd(s: String) = BigDecimal(s)

    private fun assertThawani(body: String, amount: String, merchant: String, balance: String) {
        val p = parse(THAWANI, body)
        assertEquals(SmsTxnType.DEBIT, p.type)
        assertEquals(0, p.amount.compareTo(bd(amount)))
        assertEquals("OMR", p.currency)
        assertEquals(merchant, p.merchant)
        assertEquals("5501", p.cardLast4)
        assertEquals(LocalDate.of(2025, 1, 15), p.dateTime!!.toLocalDate())
        assertEquals(0, p.balance!!.compareTo(bd(balance)))
        assertEquals("thawani", p.bankId)
        assertNull(p.topup)
    }

    @Test fun thawani_sampleClinic() = assertThawani(T1, "25.000", "SAMPLE CLINIC", "50.000")
    @Test fun thawani_pharmacy() = assertThawani(T2, "1.500", "SAMPLE PHARMACY", "48.500")
    @Test fun thawani_leadingDotAmount_bakery() = assertThawani(T3, "0.250", "DEMO BAKERY", "48.250")

    @Test fun thawani_categories() {
        val c = MerchantCategorizer()
        assertEquals("health", c.categorize(parse(THAWANI, T1).merchant, T1))
        assertEquals("health", c.categorize(parse(THAWANI, T2).merchant, T2))
        assertEquals("food", c.categorize(parse(THAWANI, T3).merchant, T3))
    }

    @Test fun bankMuscat_thawaniTopup_isTransfer() {
        val p = parse(BM, BM_TOPUP)
        assertEquals("bankmuscat", p.bankId)
        assertEquals(SmsTxnType.DEBIT, p.type)
        assertEquals(0, p.amount.compareTo(bd("50.000")))
        assertEquals("0099", p.cardLast4)
        assertEquals("ThawaniPay Topup DEMO01", p.merchant)
        assertEquals(LocalDateTime.of(2025, 1, 15, 10, 0, 0), p.dateTime)
        assertEquals(0, p.balance!!.compareTo(bd("500.000")))
        assertEquals("thawani", p.topup?.id)
        assertEquals("thawani", p.topup?.targetBankId)
    }

    @Test fun bankMuscat_otp_ignored() = assertNull(parser.parse(BM, BM_OTP))

    @Test fun bankMuscat_sentToPerson() {
        val p = parse(BM, BM_SENT)
        assertEquals(SmsTxnType.DEBIT, p.type)
        assertEquals(0, p.amount.compareTo(bd("30.000")))
        assertEquals("ABCD######E XY", p.merchant)
        assertEquals("0099", p.cardLast4)
        assertEquals(LocalDateTime.of(2025, 1, 16, 14, 30, 0), p.dateTime)
        assertEquals("BMCT010000000114", p.ref)
        assertEquals(CategoryHints.P2P, p.categoryHint)
        assertEquals(0, p.balance!!.compareTo(bd("470.000")))
        assertNull(p.topup)
    }

    @Test fun bankDhofar_cardSuspended_ignored() = assertNull(parser.parse(BD, BD_SUSPENDED))

    private fun assertDhofarCredit(body: String, amount: String, balance: String) {
        val p = parse(BD, body)
        assertEquals("dhofar", p.bankId)
        assertEquals(SmsTxnType.CREDIT, p.type)
        assertEquals(0, p.amount.compareTo(bd(amount)))
        assertEquals("7001", p.cardLast4)
        assertEquals(LocalDate.of(2025, 1, 10), p.dateTime!!.toLocalDate())
        assertEquals(0, p.balance!!.compareTo(bd(balance)))
        assertNull(p.merchant)
    }

    @Test fun bankDhofar_credit_2() = assertDhofarCredit(BD_CREDIT_2, "2.000", "5.000")
    @Test fun bankDhofar_credit_200() = assertDhofarCredit(BD_CREDIT_200, "200.000", "205.000")

    @Test fun bankDhofar_debitVariants() {
        val a = parse(BD, "حسابكم رقم 0200XXX0007001 قد خصم منه مبلغ OMR 5.000 بتاريخ 2025-01-11. رصيدكم الحالي هو OMR 200.000")
        assertEquals(SmsTxnType.DEBIT, a.type); assertEquals(0, a.amount.compareTo(bd("5"))); assertEquals("7001", a.cardLast4)
        val b = parse(BD, "تم سحب مبلغ OMR 20.000 من حسابكم رقم 0200XXX0007001 بتاريخ 2025-01-11. رصيدكم الحالي هو OMR 180.000")
        assertEquals(SmsTxnType.DEBIT, b.type); assertEquals(0, b.amount.compareTo(bd("20")))
        val c = parse(BD, "تم خصم مبلغ 3.500 ر.ع. من حسابكم رقم 0200XXX0007001")
        assertEquals(SmsTxnType.DEBIT, c.type)
    }

    @Test fun senderIds_caseSpacesHyphens() {
        for (s in listOf("bank muscat", "BankMuscat", "BANKMUSCAT", "Bank-Muscat", "BANK MUSCAT")) assertEquals(s, "bankmuscat", parser.identifyBank(s)?.id)
        for (s in listOf("Thawani Pay", "ThawaniPay", "Thawani", "THAWANI PAY", "Thawani-Pay")) assertEquals(s, "thawani", parser.identifyBank(s)?.id)
        for (s in listOf("BankDhofar", "BKDHOFAR", "Bank Dhofar", "bank-dhofar")) assertEquals(s, "dhofar", parser.identifyBank(s)?.id)
        // defaults list them as shown in Google Messages
        val ids = DefaultSmsConfig.banks.flatMap { it.senderIds }
        assertTrue(ids.containsAll(listOf("bank muscat", "Thawani Pay", "BankDhofar")))
    }

    @Test fun unknownSender_notParsed() = assertNull(parser.parse("Random", T1))

    @Test fun walletTopup_configurable() {
        val custom = SmsParser(DefaultSmsConfig.config(walletTopups = listOf(WalletTopup("w", "محفظتي", "My wallet", listOf("ACME WALLET LOAD"), targetAccountUuid = "acc-x"))))
        val p = custom.parse(BM, "Card of a/c 0100XXXXXXXX0099 used for OMR 10.000 at Acme-Wallet Load 77 on 15/01/2025 10:00:00. Avl Bal OMR 490.000.")!!
        assertEquals("w", p.topup?.id)
        assertEquals("acc-x", p.topup?.targetAccountUuid)
        // the default Thawani rule is not part of that custom list
        assertNull(custom.parse(BM, BM_TOPUP)!!.topup)
        // matching ignores case, spaces and punctuation
        val t = DefaultSmsConfig.walletTopups.first()
        assertTrue(t.matches("THAWANIPAY TOPUP DEMO01")); assertTrue(t.matches("Thawani top-up")); assertFalse(t.matches("Thawani Restaurant"))
    }

    @Test fun balanceDelta_flipsDirection() {
        // previous 205.000, "entered" 200.000, now 5.000 → it was a debit
        assertEquals(BalanceCheck.Result(SmsTxnType.DEBIT, true), BalanceCheck.resolve(SmsTxnType.CREDIT, bd("200.000"), bd("205.000"), bd("5.000")))
        // previous 5.000 + 200.000 = 205.000 → credit confirmed
        assertEquals(BalanceCheck.Result(SmsTxnType.CREDIT, false), BalanceCheck.resolve(SmsTxnType.CREDIT, bd("200.000"), bd("5.000"), bd("205.000")))
        // unknown previous balance / inconsistent numbers → parsed direction kept
        assertEquals(BalanceCheck.Result(SmsTxnType.CREDIT, false), BalanceCheck.resolve(SmsTxnType.CREDIT, bd("2.000"), null, bd("5.000")))
        assertEquals(BalanceCheck.Result(SmsTxnType.CREDIT, false), BalanceCheck.resolve(SmsTxnType.CREDIT, bd("2.000"), bd("50.000"), bd("5.000")))
        // debit detected but balance went up by the amount → credit
        assertEquals(SmsTxnType.CREDIT, BalanceCheck.resolve(SmsTxnType.DEBIT, bd("5"), bd("10"), bd("15")).type)
    }

    @Test fun leadingDotAmount_notConfusedWithDates() {
        assertEquals(0, parser.findAmount("Purchase of OMR .500 at X on 15/01/2025 .")!!.first.compareTo(bd("0.5")))
        assertNull(parser.findRef("Your OTP is 123456"))
    }
}
