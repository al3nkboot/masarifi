package com.midroid.masarifi.analysis

import com.midroid.masarifi.data.Category
import com.midroid.masarifi.data.Txn
import com.midroid.masarifi.data.TxnType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class SummaryCalcTest {
    private val zone = ZoneId.of("Asia/Muscat")
    private val cats = listOf(
        Category(1, TxnType.EXPENSE, "طعام", "Food", "🍔", "food"),
        Category(2, TxnType.EXPENSE, "إنترنت", "Internet", "🌐", "internet"),
        Category(3, TxnType.EXPENSE, "سيارة", "Car", "🚗", "car"),
        Category(4, TxnType.EXPENSE, "أخرى", "Other", "📦", "other"),
        Category(5, TxnType.INCOME, "راتب", "Salary", "💰", "salary"),
    )
    private val texts = SummaryCalc.Texts(money = { "%.3f".format(it / 1000.0) }, month = { it.toString() }, catName = { id -> cats.firstOrNull { it.id == id }?.nameAr ?: "?" })
    private var nextId = 1L
    private fun t(type: Int, omr: Double, date: String, cat: Long?, note: String = "", auto: Boolean = false, merchant: String? = null) =
        Txn(id = nextId++, type = type, amount = Math.round(omr * 1000), accountId = 1, toAccountId = if (type == TxnType.TRANSFER) 2 else null,
            categoryId = cat, note = note, dateTime = LocalDate.parse(date).atTime(12, 0).atZone(zone).toInstant().toEpochMilli(), isAuto = auto, merchant = merchant)

    private val data = listOf(
        t(TxnType.INCOME, 500.0, "2026-07-01", 5),
        t(TxnType.INCOME, 500.0, "2026-08-01", 5),
        t(TxnType.INCOME, 500.0, "2026-09-01", 5),
        t(TxnType.EXPENSE, 10.0, "2026-07-05", 1), t(TxnType.EXPENSE, 20.0, "2026-07-15", 1),
        t(TxnType.EXPENSE, 10.0, "2026-08-05", 1), t(TxnType.EXPENSE, 10.0, "2026-08-20", 1),
        t(TxnType.EXPENSE, 30.0, "2026-09-05", 1),
        t(TxnType.EXPENSE, 15.0, "2026-07-10", 2, "OpenAI ChatGPT"), t(TxnType.EXPENSE, 7.0, "2026-08-10", 2, "Claude"),
        t(TxnType.EXPENSE, 12.0, "2026-07-20", 3), t(TxnType.EXPENSE, 600.0, "2026-09-12", 3, "Gearbox"),
        t(TxnType.EXPENSE, 4.0, "2026-08-25", 4, auto = true, merchant = "SAMPLE SHOP"),
        t(TxnType.EXPENSE, 6.0, "2026-08-26", 4, note = "هدية", auto = true, merchant = "SAMPLE SHOP"),
        t(TxnType.TRANSFER, 200.0, "2026-08-02", null), // own transfer / ATM to cash: excluded
        t(TxnType.EXPENSE, 99.0, "2026-10-02", 1), // outside period
    )
    private val q3 = SummaryPeriod.quarter(2026, 3)
    private val s = SummaryCalc.compute(data, cats, q3, zone, LocalDate.parse("2026-10-15"), texts)

    @Test fun excludesTransfersAndOutOfPeriod() {
        assertEquals(724_000L, s.totalExpense)
        assertEquals(1_500_000L, s.totalIncome)
        assertEquals(11, s.expenseCount)
        assertEquals(14, s.count)
        assertEquals(776_000L, s.net)
    }

    @Test fun categoryTableSortedWithPercentAndAverage() {
        assertEquals(listOf(3L, 1L, 2L, 4L), s.categories.map { it.categoryId })
        val car = s.categories[0]
        assertEquals(612_000L, car.amount); assertEquals(306_000L, car.average)
        assertEquals(612.0 * 100 / 724, car.percent, 1e-9)
        assertEquals(100.0, s.categories.sumOf { it.percent }, 1e-9)
    }

    @Test fun monthOverMonthChange() {
        assertEquals(listOf(YearMonth.of(2026, 7), YearMonth.of(2026, 8), YearMonth.of(2026, 9)), s.months.map { it.month })
        assertNull(s.months[0].changePercent)
        assertEquals(57_000L, s.months[0].expense); assertEquals(37_000L, s.months[1].expense); assertEquals(630_000L, s.months[2].expense)
        assertEquals((37.0 - 57) * 100 / 57, s.months[1].changePercent!!, 1e-9)
        assertEquals((630.0 - 37) * 100 / 37, s.months[2].changePercent!!, 1e-9)
        assertEquals(-130_000L, s.months[2].net)
    }

    @Test fun oneOffDetection() {
        assertEquals(listOf(600_000L), s.oneOffs.map { it.amount })
        // 3x rule: 25 OMR among items averaging 10 → one-off; 25 among items averaging 20 → not
        assertEquals(1, SummaryCalc.oneOffs(listOf(t(1, 10.0, "2026-07-01", 1), t(1, 10.0, "2026-07-02", 1), t(1, 30.0, "2026-07-03", 1))).size)
        assertEquals(0, SummaryCalc.oneOffs(listOf(t(1, 20.0, "2026-07-01", 1), t(1, 30.0, "2026-07-03", 1))).size)
        // below minimum never a one-off
        assertEquals(0, SummaryCalc.oneOffs(listOf(t(1, 1.0, "2026-07-01", 1), t(1, 19.0, "2026-07-03", 1))).size)
    }

    @Test fun baselineAndBudgetExcludeOneOffs() {
        val months = s.days / 30.4375
        assertEquals(92, s.days)
        assertEquals(Math.round(124_000 / months), s.baselineMonthly)
        val food = s.budgets.first { it.categoryId == 1L }
        assertEquals(Math.round(80_000 / months), food.baselineMonthly)
        assertEquals(30_000L, food.suggested) // rounded up to 5 OMR
        assertTrue(s.budgets.none { it.categoryId == 4L }) // < 5 OMR per month
    }

    @Test fun topAndReview() {
        assertEquals(600_000L, s.topExpenses.first().amount)
        assertEquals(10, s.topExpenses.size)
        assertEquals(listOf(4_000L), s.needsReview.map { it.amount })
    }

    @Test fun insightTexts() {
        val byKind = s.insights.associateBy { it.kind }
        val g = byKind.getValue(InsightKind.GROWTH).text
        assertTrue(g, g.contains("2026-09") && g.contains("سيارة") && g.contains("Gearbox 600.000"))
        assertTrue(byKind.getValue(InsightKind.FOOD_DAILY).text.contains("0.869")) // 80 / 92 days
        val sub = byKind.getValue(InsightKind.SUBSCRIPTIONS).text
        assertTrue(sub, sub.contains("22.000") && sub.contains("ذكاء اصطناعي"))
        assertTrue(byKind.getValue(InsightKind.CAR_FUND).text.contains("صندوق طوارئ"))
        assertTrue(byKind.getValue(InsightKind.REVIEW).text.startsWith("1 "))
        assertTrue(byKind.getValue(InsightKind.DEFICIT).text.contains("2026-09 (130.000)"))
    }

    @Test fun singleAiSubscriptionNoTip() {
        val one = SummaryCalc.compute(data.filter { it.note != "Claude" }, cats, q3, zone, LocalDate.parse("2026-10-15"), texts)
        assertFalse(one.insights.first { it.kind == InsightKind.SUBSCRIPTIONS }.text.contains("ذكاء اصطناعي"))
    }

    @Test fun periodsAndPercentText() {
        assertEquals(LocalDate.parse("2026-04-01"), SummaryPeriod.quarter(2026, 2).start)
        assertEquals(LocalDate.parse("2026-07-01"), SummaryPeriod.quarter(2026, 2).endExclusive)
        assertEquals(LocalDate.parse("2026-02-01"), SummaryPeriod.month(YearMonth.of(2026, 1)).endExclusive)
        assertEquals(LocalDate.parse("2026-03-11"), SummaryPeriod.custom(LocalDate.parse("2026-03-01"), LocalDate.parse("2026-03-10")).endExclusive)
        assertEquals("12%", SummaryCalc.pctText(12.4)); assertEquals("3.5%", SummaryCalc.pctText(3.46)); assertEquals("5%", SummaryCalc.pctText(5.0))
        // ongoing month: days counted up to today only
        val cur = SummaryCalc.compute(emptyList(), cats, SummaryPeriod.month(YearMonth.of(2026, 10)), zone, LocalDate.parse("2026-10-15"), texts)
        assertEquals(15, cur.days); assertEquals(0L, cur.totalExpense)
    }
}
