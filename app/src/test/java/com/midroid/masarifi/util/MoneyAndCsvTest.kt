package com.midroid.masarifi.util

import com.midroid.masarifi.data.CsvBackup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyAndCsvTest {
    private val omr = Currencies.of("OMR")

    @Test fun formatOmrThreeDecimals() {
        assertEquals("4.300 ر.ع.", Money.format(4300, omr, arabic = true))
        assertEquals("OMR 1,234.500", Money.format(1_234_500, omr, arabic = false))
        assertEquals("-2.000 ر.ع.", Money.format(-2000, omr, arabic = true))
    }

    @Test fun parseInput() {
        assertEquals(4300L, Money.parse("4.3"))
        assertEquals(4300L, Money.parse("٤٫٣"))
        assertEquals(12000L, Money.parse("12"))
        assertNull(Money.parse("abc"))
        assertEquals("4.300", Money.toInput(4300, omr))
    }

    @Test fun pegConversion() {
        assertEquals(6148L, Currencies.convert(15_990, "USD", "OMR"))
        assertEquals(5000L, Currencies.convert(5000, "OMR", "OMR"))
    }

    @Test fun csvParserHandlesQuotesAndArabic() {
        val rows = CsvBackup.parse("Date,Note\r\n2025-01-10 14:35,\"لولو, بوشر\"\n2025-01-11 09:00,\"say \"\"hi\"\"\"\n")
        assertEquals(3, rows.size)
        assertEquals("لولو, بوشر", rows[1][1])
        assertEquals("say \"hi\"", rows[2][1])
    }
}
