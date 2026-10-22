package com.midroid.masarifi.sms

import com.midroid.masarifi.sms.ProviderSamplesTest.Companion.BD
import com.midroid.masarifi.sms.ProviderSamplesTest.Companion.BD_CREDIT_2
import com.midroid.masarifi.sms.ProviderSamplesTest.Companion.BM
import com.midroid.masarifi.sms.ProviderSamplesTest.Companion.BM_SENT
import com.midroid.masarifi.sms.ProviderSamplesTest.Companion.BM_TOPUP
import com.midroid.masarifi.sms.ProviderSamplesTest.Companion.T3
import com.midroid.masarifi.sms.ProviderSamplesTest.Companion.THAWANI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.math.BigDecimal

/** The bank table in [DefaultSmsConfig.banks]: every entry is recognised and parses its real (masked) samples. */
class BankTableTest {
    private val parser = SmsParser()
    private val banks = DefaultSmsConfig.banks

    private fun assertSample(sender: String, body: String, bankId: String, ruleId: String, type: SmsTxnType, amount: String, card: String?) {
        val p = parser.parse(sender, body) ?: throw AssertionError("not parsed: $body")
        assertEquals(bankId, p.bankId)
        assertEquals(ruleId, p.ruleId)
        assertEquals(type, p.type)
        assertEquals(0, p.amount.compareTo(BigDecimal(amount)))
        assertEquals("OMR", p.currency)
        assertEquals(card, p.cardLast4)
    }

    @Test fun bankIdsAreUnique() = assertEquals(banks.size, banks.map { it.id }.toSet().size)

    @Test fun everyRuleCompiles() {
        for (b in banks) for (r in b.rules) assertNotNull(r.id, r.compiled.regex)
    }

    @Test fun everySenderIdIdentifiesItsBank() {
        for (b in banks) for (s in b.senderIds) assertEquals("${b.id}: $s", b.id, parser.identifyBank(s)?.id)
    }

    @Test fun bankMuscat_cardUsed() = assertSample(BM, BM_TOPUP, "bankmuscat", "bm_card_used_en", SmsTxnType.DEBIT, "50.000", "0099")
    @Test fun bankMuscat_mobileSent() = assertSample(BM, BM_SENT, "bankmuscat", "bm_mobile_sent_en", SmsTxnType.DEBIT, "30.000", "0099")
    @Test fun thawani_cardUsed() = assertSample(THAWANI, T3, "thawani", "thawani_card_used_en", SmsTxnType.DEBIT, "0.250", "5501")
    @Test fun bankDhofar_credit() = assertSample(BD, BD_CREDIT_2, "dhofar", "bd_credit_ar", SmsTxnType.CREDIT, "2.000", "7001")
}
