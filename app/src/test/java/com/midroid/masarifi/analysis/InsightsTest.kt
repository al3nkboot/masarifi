package com.midroid.masarifi.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InsightsTest {
    private val fmt: (Long) -> String = { "%.3f".format(it / 1000.0) }

    @Test fun restaurantIncreaseTipArabic() {
        val tips = Insights.tips(
            categories = listOf(CategoryComparison(1, "المطاعم", 67_500, 50_000), CategoryComparison(2, "الوقود", 20_000, 21_000)),
            totalIncome = 0, totalExpense = 87_500, previousExpense = 71_000, overallBudget = 0,
            dayOfMonth = 20, daysInMonth = 31, isCurrentMonth = false, arabic = true, fmt = fmt,
        )
        assertTrue(tips.toString(), tips.contains("مصروفك على المطاعم ارتفع 35% عن الشهر الماضي"))
    }

    @Test fun projectionOverBudget() {
        val tips = Insights.tips(emptyList(), 0, 100_000, 0, 150_000, 10, 30, true, false, fmt)
        assertTrue(tips.any { it.startsWith("At this pace") })
    }

    @Test fun spikeDetection() {
        val normal = listOf(5_000L, 6_000, 4_000, 5_500, 7_000, 3_000, 6_500, 5_000, 4_500, 6_000)
        assertTrue(Insights.isSpike(normal, 30_000))
        assertFalse(Insights.isSpike(normal, 8_000))
        assertFalse(Insights.isSpike(normal.take(3), 30_000))
    }

    @Test fun budgetThresholds() {
        assertNull(Insights.budgetThreshold(BudgetStatus(0, "x", 100_000, 50_000)))
        assertEquals(80, Insights.budgetThreshold(BudgetStatus(0, "x", 100_000, 80_000)))
        assertEquals(100, Insights.budgetThreshold(BudgetStatus(0, "x", 100_000, 120_000)))
    }
}
