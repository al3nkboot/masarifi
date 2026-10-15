package com.midroid.masarifi.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MerchantCategorizerTest {
    private val c = MerchantCategorizer()

    @Test fun mapsCommonOmaniMerchants() {
        assertEquals("grocery", c.categorize("LULU HYPERMARKET BAUSHER"))
        assertEquals("fuel", c.categorize("SHELL AL KHUWAIR"))
        assertEquals("fuel", c.categorize("OMAN OIL MARKETING 112"))
        assertEquals("food", c.categorize("STARBUCKS MOE"))
        assertEquals("food", c.categorize("مطعم الأرز الذهبي"))
        assertEquals("internet", c.categorize("OOREDOO"))
        assertEquals("trip", c.categorize("OMAN AIR WEB"))
        assertEquals("health", c.categorize("MUSCAT PHARMACY"))
        assertEquals("social", c.categorize("NETFLIX.COM"))
    }

    @Test fun salaryFromBody() {
        assertEquals("salary", c.categorize(null, "Your a/c has been credited with OMR 1,250.000 SALARY"))
        assertEquals("salary", c.categorize(null, "تم إيداع راتب شهر أكتوبر"))
    }

    @Test fun unknownMerchant() {
        assertNull(c.categorize("XYZ TRADING LLC"))
    }

    @Test fun merchantKeyNormalization() {
        assertEquals("lulu hypermarket bausher", MerchantCategorizer.merchantKey("LULU HYPERMARKET-BAUSHER 0123"))
        assertEquals(MerchantCategorizer.merchantKey("Talabat*Order 5512"), MerchantCategorizer.merchantKey("TALABAT ORDER"))
    }
}
