package com.midroid.masarifi.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/** 1.6.0: explicit income wording, ATM detection, card/account style, transfer wording. Generic sample values only. */
class IncomeAtmParserTest {
    private val parser = SmsParser()
    private fun parse(sender: String, body: String) = parser.parse(sender, body) ?: throw AssertionError("not parsed: $body")

    @Test fun istalamt_isIncome() {
        val p = parse("BankMuscat", "لقد استلمت OMR 150.000 من SAMPLE PERSON")
        assertEquals(SmsTxnType.CREDIT, p.type)
        assertEquals(0, p.amount.compareTo(BigDecimal("150")))
        assertTrue(p.incomeWording)
    }

    @Test fun istalamt_withTransferWords_stillIncome() {
        // "تحويل"/"payment" in the text must not turn it into an expense
        val p = parse("BankMuscat", "لقد استلمت OMR 25.500 من SAMPLE PERSON عبر خدمة التحويل الفوري mobile payment")
        assertEquals(SmsTxnType.CREDIT, p.type)
    }

    @Test fun receivedCreditedOoda3_areIncome() {
        assertEquals(SmsTxnType.CREDIT, parse("BankMuscat", "You have received OMR 20.000 from SAMPLE PERSON on 16/01/2025.").type)
        assertEquals(SmsTxnType.CREDIT, parse("NBO", "أودع مبلغ 30.000 ر.ع في حسابك XXXX0099 بتاريخ 16/01/2025").type)
        assertEquals(SmsTxnType.CREDIT, parse("NBO", "تم إيداع OMR 12.000 في حسابك XXXX0099").type)
        assertEquals(SmsTxnType.CREDIT, parse("Thawani Pay", "Dear Customer, Your Mojab Card 4000XXXXXXXX5501 has been credited with 100.000 OMR on 16/01/2025.").type)
    }

    @Test fun debitsStayDebits() {
        assertEquals(SmsTxnType.DEBIT, parse("BankMuscat", "Dear Customer, You have sent OMR 30.000 to SAMPLE PERSON from your a/c 0100XXXXXXXX0099 on 16/01/2025 14:30:00 using Mobile Payment services.").type)
        // debit wording first, "credited to beneficiary" later → still a debit
        assertEquals(SmsTxnType.DEBIT, parse("BankMuscat", "OMR 10.000 debited from your a/c XXXX0099 and credited to SAMPLE PERSON").type)
    }

    @Test fun atm_detected() {
        val p = parse("BankMuscat", "ATM cash withdrawal of OMR 150.000 from card ending 3030 at BM ATM DEMO on 16/01/2025 11:05")
        assertTrue(p.atm)
        assertTrue(parse("BankDhofar", "عملية سحب نقدي بمبلغ 5.000 ر.ع من الصراف الآلي باستخدام البطاقة رقم XXXX6610 يوم 16/01/2025").atm)
        assertFalse(parse("BankMuscat", "Purchase of OMR 4.300 at DEMO MARKET on 16/01/2025").atm)
    }

    @Test fun style_cardVsAccount() {
        assertEquals(SmsStyle.ACCOUNT, parse("BankDhofar", "حسابكم رقم 0200XXX0007001 قد خصم منه مبلغ OMR 10.000 بتاريخ 2025-01-12. رصيدكم الحالي هو OMR 90.000").style)
        assertEquals(SmsStyle.CARD, parse("BankDhofar", "تم خصم مبلغ OMR 10.000 من بطاقتك رقم XXXX6610 لدى ThawaniPay Topup بتاريخ 2025-01-12").style)
        assertEquals(SmsStyle.CARD, parse("bank muscat", ProviderSamplesTest.BM_TOPUP).style)
    }

    @Test fun transferWording_andMentionedDigits() {
        val p = parse("BankMuscat", "Dear Customer, You have sent OMR 50.000 to card 4000XXXXXXXX5501 from your a/c 0100XXXXXXXX0099 on 16/01/2025 14:30:00 using Mobile Payment services.")
        assertTrue(p.transferLike)
        assertEquals("0099", p.cardLast4)
        assertTrue("5501" in p.mentionedLast4)
        assertFalse(parse("Thawani Pay", ProviderSamplesTest.T3).transferLike)
    }
}
