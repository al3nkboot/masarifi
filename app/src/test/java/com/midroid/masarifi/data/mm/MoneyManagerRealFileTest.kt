package com.midroid.masarifi.data.mm

import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * LOCAL-ONLY verification against a real Money Manager export. Skipped unless MM_XLSX points to an existing file.
 * Optional MM_EXPECTED: JSON with {"rows": n, "monthly": {"YYYY-MM|Income": milli, ...}} computed independently (pandas).
 * Prints only aggregate pass/fail information, never individual transactions.
 */
class MoneyManagerRealFileTest {
    @Test fun realExport() {
        val path = System.getenv("MM_XLSX")
        assumeTrue("MM_XLSX not set", !path.isNullOrBlank() && File(path).isFile)
        val r = MoneyManagerParser.parseFile(File(path!!).readBytes())
        assertEquals("rows consumed + skipped must equal data rows", r.dataRows, r.consumedRows + r.skipped.size)

        val monthly = HashMap<String, Long>()
        for (t in r.txns) {
            if (t.kind == MmKind.TRANSFER) continue
            val k = "%04d-%02d|%s".format(t.dateTime.year, t.dateTime.monthValue, if (t.kind == MmKind.INCOME) "Income" else "Expense")
            monthly[k] = (monthly[k] ?: 0L) + t.amountMilli
        }
        val expPath = System.getenv("MM_EXPECTED")
        if (!expPath.isNullOrBlank() && File(expPath).isFile) {
            val json = File(expPath).readText()
            val rows = Regex("\"rows\":\\s*(\\d+)").find(json)!!.groupValues[1].toInt()
            assertEquals("data rows vs pandas", rows, r.dataRows)
            val expected = Regex("\"(\\d{4}-\\d{2}\\|(?:Income|Expense))\":\\s*(-?\\d+)").findAll(json)
                .associate { it.groupValues[1] to it.groupValues[2].toLong() }
            assertEquals("month/type keys", expected.keys.sorted(), monthly.keys.sorted())
            var mismatches = 0
            for ((k, v) in expected) if (monthly[k] != v) mismatches++
            assertEquals("monthly totals mismatching pandas", 0, mismatches)
            println("MM_VERIFY rows=${r.dataRows} parsed=${r.txns.size} skipped=${r.skipped.size} monthKeys=${expected.size} monthlyTotals=PASS")
        } else {
            println("MM_VERIFY rows=${r.dataRows} parsed=${r.txns.size} skipped=${r.skipped.size} (no expected file)")
        }
        println("MM_VERIFY kinds=" + r.txns.groupingBy { it.kind }.eachCount() + " skippedReasons=" + r.skipped.groupingBy { it.reason }.eachCount() +
            " accounts=" + r.txns.flatMap { listOfNotNull(it.account, it.toAccount) }.toSet().size +
            " categories=" + r.txns.map { it.category }.filter { it.isNotBlank() }.toSet().size +
            " range=" + r.txns.minOfOrNull { it.dateTime.toLocalDate() } + ".." + r.txns.maxOfOrNull { it.dateTime.toLocalDate() })
    }
}
