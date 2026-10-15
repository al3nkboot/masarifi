package com.midroid.masarifi.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

class SmsParserTest {

    private val parser = SmsParser()

    private fun amt(s: String) = BigDecimal(s)

    private fun parseOk(sender: String, body: String): ParsedSms {
        val r = parser.parse(sender, body)
        assertNotNull("Expected a transaction for: $body", r)
        return r!!
    }

    // ---------------- Bank Muscat ----------------

    @Test fun bankMuscat_cardPurchase_english() {
        val r = parseOk(
            "BankMuscat",
            "Dear Customer, Purchase of OMR 4.300 was made using your Debit Card XXXX1234 at LULU HYPERMARKET BAUSHER on 06/10/2026 14:35. Avl Bal OMR 812.450",
        )
        assertEquals(SmsTxnType.DEBIT, r.type)
        assertEquals(0, amt("4.300").compareTo(r.amount))
        assertEquals("OMR", r.currency)
        assertEquals("1234", r.cardLast4)
        assertEquals("LULU HYPERMARKET BAUSHER", r.merchant)
        assertEquals(LocalDateTime.of(2026, 10, 6, 14, 35), r.dateTime)
        assertEquals(0, amt("812.450").compareTo(r.balance))
        assertEquals("bankmuscat", r.bankId)
    }

    @Test fun bankMuscat_accountDebited() {
        val r = parseOk("BANKMUSCAT", "Your a/c xxxxxx5678 has been debited with OMR 25.000 on 05-10-26. Available balance OMR 300.100")
        assertEquals(SmsTxnType.DEBIT, r.type)
        assertEquals(0, amt("25").compareTo(r.amount))
        assertEquals("5678", r.cardLast4)
        assertEquals(2026, r.dateTime!!.year)
        assertEquals(10, r.dateTime!!.monthValue)
        assertEquals(5, r.dateTime!!.dayOfMonth)
    }

    @Test fun bankMuscat_salaryCredit() {
        val r = parseOk("BankMuscat", "Your a/c XXXX5678 has been credited with OMR 1,250.000 SALARY on 25/09/2026. Avl Bal OMR 1,562.340")
        assertEquals(SmsTxnType.CREDIT, r.type)
        assertEquals(0, amt("1250").compareTo(r.amount))
        assertEquals(0, amt("1562.340").compareTo(r.balance))
    }

    @Test fun bankMuscat_arabicPurchase() {
        val r = parseOk(
            "BankMuscat",
            "تم خصم مبلغ 12.750 ر.ع. من بطاقتك المنتهية بـ 4321 لدى مطعم الأرز الذهبي بتاريخ 03/10/2026 الساعة 21:10. الرصيد المتاح 400.000 ر.ع.",
        )
        assertEquals(SmsTxnType.DEBIT, r.type)
        assertEquals(0, amt("12.750").compareTo(r.amount))
        assertEquals("OMR", r.currency)
        assertEquals("4321", r.cardLast4)
        assertEquals("مطعم الأرز الذهبي", r.merchant)
        assertEquals(LocalDateTime.of(2026, 10, 3, 21, 10), r.dateTime)
        assertEquals(0, amt("400").compareTo(r.balance))
    }

    // ---------------- NBO ----------------

    @Test fun nbo_posTransaction() {
        val r = parseOk("NBO", "NBO: POS transaction of RO 7.200 at SHELL AL KHUWAIR on 04-Oct-2026 08:12 using card ending 9876. Bal RO 210.000")
        assertEquals(SmsTxnType.DEBIT, r.type)
        assertEquals(0, amt("7.2").compareTo(r.amount))
        assertEquals("OMR", r.currency)
        assertEquals("SHELL AL KHUWAIR", r.merchant)
        assertEquals("9876", r.cardLast4)
        assertEquals(LocalDateTime.of(2026, 10, 4, 8, 12), r.dateTime)
    }

    @Test fun nbo_arabicDeposit_arabicIndicDigits() {
        val r = parseOk("NBOOman", "تم إيداع مبلغ ٣٥٠٫٠٠٠ ر.ع في حسابك رقم XXXX٤٤٥٥ بتاريخ ٠١/١٠/٢٠٢٦. الرصيد ٩٠٠٫٥٠٠ ر.ع")
        assertEquals(SmsTxnType.CREDIT, r.type)
        assertEquals(0, amt("350").compareTo(r.amount))
        assertEquals("4455", r.cardLast4)
        assertEquals(1, r.dateTime!!.dayOfMonth)
        assertEquals(0, amt("900.5").compareTo(r.balance))
    }

    // ---------------- Sohar International ----------------

    @Test fun sohar_ecommerceUsd() {
        val r = parseOk("SoharIntl", "Sohar International: E-commerce transaction of USD 15.99 at NETFLIX.COM on 02/10/2026 with card **3344 was successful.")
        assertEquals(SmsTxnType.DEBIT, r.type)
        assertEquals("USD", r.currency)
        assertEquals(0, amt("15.99").compareTo(r.amount))
        assertEquals("NETFLIX.COM", r.merchant)
        assertEquals("3344", r.cardLast4)
    }

    @Test fun sohar_transferOut_arabic() {
        val r = parseOk("Sohar Intl", "تم تحويل 50.000 ر.ع من حسابك XXXX1100 إلى أحمد سالم بتاريخ 30/09/2026")
        assertEquals(SmsTxnType.DEBIT, r.type)
        assertEquals(0, amt("50").compareTo(r.amount))
        assertEquals("أحمد سالم", r.merchant)
        assertEquals("1100", r.cardLast4)
    }

    // ---------------- Bank Dhofar ----------------

    @Test fun dhofar_amountFirstDebit() {
        val r = parseOk("BankDhofar", "OMR 3.500 has been debited from your card ending with 2020 at STARBUCKS MOE on 06/10/2026 09:41. Available balance: OMR 95.250")
        assertEquals(SmsTxnType.DEBIT, r.type)
        assertEquals(0, amt("3.5").compareTo(r.amount))
        assertEquals("STARBUCKS MOE", r.merchant)
        assertEquals("2020", r.cardLast4)
        assertEquals(0, amt("95.25").compareTo(r.balance))
    }

    @Test fun dhofar_atmWithdrawal_arabic() {
        val r = parseOk("BankDhofar", "عملية سحب نقدي بمبلغ 40.000 ر.ع من الصراف الآلي باستخدام البطاقة رقم XXXX7788 يوم 05/10/2026")
        assertEquals(SmsTxnType.DEBIT, r.type)
        assertEquals(0, amt("40").compareTo(r.amount))
        assertEquals("7788", r.cardLast4)
    }

    // ---------------- OAB ----------------

    @Test fun oab_refundCredit() {
        val r = parseOk("OAB", "Refund of OMR 9.900 from H&M MALL OF OMAN has been credited to your card XXXX6655 on 01/10/2026")
        assertEquals(SmsTxnType.CREDIT, r.type)
        assertEquals(0, amt("9.9").compareTo(r.amount))
        assertEquals("H&M MALL OF OMAN", r.merchant)
    }

    @Test fun oab_debitCardWordDoesNotFlipCredit() {
        val r = parseOk("OABOman", "Your debit card XXXX6655 received a reversal: RO 2.000 credited to your account on 02/10/2026")
        assertEquals(SmsTxnType.CREDIT, r.type)
        assertEquals(0, amt("2").compareTo(r.amount))
    }

    // ---------------- HSBC Oman ----------------

    @Test fun hsbc_youSpent() {
        val r = parseOk("HSBC", "HSBC: You have spent OMR 22.400 on your credit card ending 1111 at CARREFOUR MOO on 06OCT26.")
        assertEquals(SmsTxnType.DEBIT, r.type)
        assertEquals(0, amt("22.4").compareTo(r.amount))
        assertEquals("CARREFOUR MOO", r.merchant)
        assertEquals("1111", r.cardLast4)
        assertEquals(6, r.dateTime!!.dayOfMonth)
        assertEquals(10, r.dateTime!!.monthValue)
    }

    // ---------------- Generic / edge cases ----------------

    @Test fun generic_arabicCurrencyBeforeAmount() {
        val r = parseOk("BankNizwa", "تم خصم ر.ع. 6.000 من حسابك XXX123 لدى OOREDOO بتاريخ 2026-10-04 10:00")
        assertEquals(SmsTxnType.DEBIT, r.type)
        assertEquals(0, amt("6").compareTo(r.amount))
        assertEquals("OOREDOO", r.merchant)
        assertEquals(LocalDateTime.of(2026, 10, 4, 10, 0), r.dateTime)
    }

    @Test fun otp_isIgnored() {
        assertNull(parser.parse("BankMuscat", "Your OTP for purchase of OMR 10.000 at AMAZON is 482913. Do not share it with anyone."))
        assertNull(parser.parse("NBO", "رمز التحقق الخاص بك هو 1234 لعملية شراء بمبلغ 5.000 ر.ع"))
    }

    @Test fun declined_isIgnored() {
        assertNull(parser.parse("BankDhofar", "Your transaction of OMR 55.000 at XCITE was declined due to insufficient balance."))
        assertNull(parser.parse("SoharIntl", "عملية الشراء بمبلغ 20.000 ر.ع لدى نون مرفوضة"))
    }

    @Test fun promo_isIgnored() {
        assertNull(parser.parse("BankMuscat", "Special offer! Get 20% cashback up to OMR 10 on dining this weekend."))
    }

    @Test fun unknownSender_isIgnoredByDefault() {
        assertNull(parser.parse("+96899123456", "Purchase of OMR 4.300 at LULU on 06/10/2026"))
    }

    @Test fun extraSender_andUnknownSenderOption() {
        val p = SmsParser(DefaultSmsConfig.config(extraSenderIds = listOf("MyBank")))
        assertNotNull(p.parse("MyBank", "Purchase of OMR 4.300 at LULU on 06/10/2026"))
        val p2 = SmsParser(DefaultSmsConfig.config(acceptUnknownSenders = true))
        val r = p2.parse("90012", "Bank Muscat: Purchase of OMR 4.300 at LULU on 06/10/2026")
        assertNotNull(r)
        assertEquals("bankmuscat", r!!.bankId)
    }

    @Test fun balanceOnly_isNotATransaction() {
        assertNull(parser.parse("BankMuscat", "Your available balance as of 06/10/2026 is OMR 512.000"))
    }

    @Test fun senderMatchingIsCaseAndSpaceInsensitive() {
        assertNotNull(parser.identifyBank("bank muscat"))
        assertNotNull(parser.identifyBank("BANK-DHOFAR"))
        assertEquals("hsbc", parser.identifyBank("HSBC OMAN")!!.id)
    }

    @Test fun customRuleFromConfig() {
        val cfg = DefaultSmsConfig.config().copy(
            genericRules = listOf(SmsRule("custom", "Paid {AMT} {CUR} to {MERCHANT} ref", SmsTxnType.DEBIT)) + DefaultSmsConfig.genericRules,
        )
        val r = SmsParser(cfg).parse("BankMuscat", "Paid 1.500 OMR to MAWAQIF ref 99812")
        assertNotNull(r)
        assertEquals("custom", r!!.ruleId)
        assertEquals("MAWAQIF", r.merchant)
    }

    @Test fun hashIsStableAndDistinct() {
        val a = parser.hash("BankMuscat", "Purchase of OMR 4.300 at LULU")
        val b = parser.hash("Bank Muscat", "Purchase  of OMR 4.300 at LULU")
        val c = parser.hash("BankMuscat", "Purchase of OMR 4.400 at LULU")
        assertEquals(a, b)
        assertNotEquals(a, c)
    }

    // ---------------- More realistic variants ----------------

    @Test fun balanceBeforeAmount_picksTransactionAmount() {
        val r = parseOk("NBO", "Avl Bal OMR 100.000. Your card XXXX1234 was debited OMR 5.500 at KFC SEEB on 06/10/2026")
        assertEquals(0, amt("5.5").compareTo(r.amount))
        assertEquals(0, amt("100").compareTo(r.balance))
        assertEquals("KFC SEEB", r.merchant)
    }

    @Test fun arabic_riyalOmani_andMerchant() {
        val r = parseOk("BankDhofar", "تم خصم 15.000 ريال عماني من حسابك رقم ****9090 لدى نفط عمان - الخوض في 06/10/2026")
        assertEquals(SmsTxnType.DEBIT, r.type)
        assertEquals("OMR", r.currency)
        assertEquals(0, amt("15").compareTo(r.amount))
        assertEquals("9090", r.cardLast4)
        assertEquals("fuel", MerchantCategorizer().categorize(r.merchant, ""))
    }

    @Test fun lowercaseRo() {
        val r = parseOk("OAB", "Purchase of ro 2.5 at Talabat on 05/10/2026")
        assertEquals(0, amt("2.5").compareTo(r.amount))
        assertEquals("OMR", r.currency)
        assertEquals("Talabat", r.merchant)
    }

    @Test fun atmWithdrawal_pmTime() {
        val r = parseOk("BankMuscat", "ATM cash withdrawal of OMR 60.000 from card ending 3030 at BM ATM QURUM on 06/10/2026 11:05 PM")
        assertEquals(SmsTxnType.DEBIT, r.type)
        assertEquals(0, amt("60").compareTo(r.amount))
        assertEquals("3030", r.cardLast4)
        assertEquals(LocalDateTime.of(2026, 10, 6, 23, 5), r.dateTime)
    }

    @Test fun creditCardStatement_isIgnored() {
        assertNull(parser.parse("HSBC", "Your credit card statement is ready. Minimum payment OMR 25.000 due on 15/10/2026"))
    }

    @Test fun salaryCredited_english() {
        val r = parseOk("SoharIntl", "Salary of OMR 900.000 has been credited to your account XXXX2211 on 24/09/2026.")
        assertEquals(SmsTxnType.CREDIT, r.type)
        assertEquals(0, amt("900").compareTo(r.amount))
        assertEquals("2211", r.cardLast4)
        assertEquals("salary", MerchantCategorizer().categorize(r.merchant, "Salary of OMR 900.000 has been credited"))
    }

    @Test fun arabic_incomingTransfer() {
        val r = parseOk("BankMuscat", "تحويل وارد بمبلغ 120.000 ر.ع من سالم الحارثي إلى حسابك XXXX5678 بتاريخ 02/10/2026")
        assertEquals(SmsTxnType.CREDIT, r.type)
        assertEquals(0, amt("120").compareTo(r.amount))
        assertEquals("سالم الحارثي", r.merchant)
    }
}
