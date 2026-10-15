package com.midroid.masarifi.analysis

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** One category's spending in two periods (amounts in milli-units). */
data class CategoryComparison(val categoryId: Long, val name: String, val current: Long, val previous: Long)

data class BudgetStatus(val categoryId: Long, val name: String, val budget: Long, val spent: Long) {
    val ratio: Double get() = if (budget <= 0) 0.0 else spent.toDouble() / budget
}

/**
 * Rule-based analysis: tips text, spending-spike detection and budget thresholds.
 * Pure Kotlin so it can be unit-tested.
 */
object Insights {

    /** Minimum absolute change (milli) for a category tip, default 3.000 currency units. */
    const val MIN_CHANGE = 3_000L

    fun percentChange(current: Long, previous: Long): Int? =
        if (previous <= 0) null else (((current - previous).toDouble() / previous) * 100).roundToInt()

    fun tips(
        categories: List<CategoryComparison>,
        totalIncome: Long,
        totalExpense: Long,
        previousExpense: Long,
        overallBudget: Long,
        dayOfMonth: Int,
        daysInMonth: Int,
        isCurrentMonth: Boolean,
        arabic: Boolean,
        fmt: (Long) -> String,
    ): List<String> {
        val out = mutableListOf<String>()
        // 1) category increases / decreases vs last month
        val changes = categories.mapNotNull { c ->
            val p = percentChange(c.current, c.previous) ?: return@mapNotNull null
            if (abs(c.current - c.previous) < MIN_CHANGE) null else c to p
        }
        changes.filter { it.second >= 20 }.sortedByDescending { it.first.current - it.first.previous }.take(3).forEach { (c, p) ->
            out += if (arabic) "مصروفك على ${c.name} ارتفع $p% عن الشهر الماضي" else "Your spending on ${c.name} rose $p% compared to last month"
        }
        changes.filter { it.second <= -20 }.sortedBy { it.first.current - it.first.previous }.take(2).forEach { (c, p) ->
            out += if (arabic) "أحسنت! مصروفك على ${c.name} انخفض ${-p}% عن الشهر الماضي" else "Well done! Spending on ${c.name} dropped ${-p}% vs last month"
        }
        // 2) new categories with notable spending
        categories.filter { it.previous == 0L && it.current >= MIN_CHANGE * 3 }.take(1).forEach { c ->
            out += if (arabic) "صرفت ${fmt(c.current)} على ${c.name} هذا الشهر ولم تصرف عليها الشهر الماضي" else "You spent ${fmt(c.current)} on ${c.name} this month vs nothing last month"
        }
        // 3) top category share
        if (totalExpense > 0) {
            categories.maxByOrNull { it.current }?.let { top ->
                val share = (top.current * 100.0 / totalExpense).roundToInt()
                if (share >= 40 && categories.count { it.current > 0 } > 1) {
                    out += if (arabic) "${top.name} تمثل $share% من مصروفاتك هذا الشهر" else "${top.name} makes up $share% of your spending this month"
                }
            }
        }
        // 4) projection against overall budget
        if (isCurrentMonth && overallBudget > 0 && dayOfMonth in 3 until daysInMonth && totalExpense > 0) {
            val projected = totalExpense * daysInMonth / dayOfMonth
            if (projected > overallBudget) {
                out += if (arabic) "بهذا المعدل ستصل مصروفاتك إلى ${fmt(projected)} وتتجاوز ميزانيتك بـ ${fmt(projected - overallBudget)}"
                else "At this pace you'll spend ${fmt(projected)}, exceeding your budget by ${fmt(projected - overallBudget)}"
            } else {
                val dailyAllowance = (overallBudget - totalExpense).coerceAtLeast(0) / (daysInMonth - dayOfMonth + 1)
                out += if (arabic) "يمكنك صرف ${fmt(dailyAllowance)} يومياً لبقية الشهر للبقاء ضمن الميزانية"
                else "You can spend ${fmt(dailyAllowance)} per day for the rest of the month and stay on budget"
            }
        }
        // 5) income vs expense
        if (totalIncome > 0) {
            if (totalExpense > totalIncome) {
                out += if (arabic) "مصروفاتك تجاوزت دخلك هذا الشهر بـ ${fmt(totalExpense - totalIncome)}" else "Your expenses exceeded your income by ${fmt(totalExpense - totalIncome)}"
            } else {
                val rate = ((totalIncome - totalExpense) * 100.0 / totalIncome).roundToInt()
                if (rate >= 10) out += if (arabic) "ادّخرت $rate% من دخلك هذا الشهر 👏" else "You saved $rate% of your income this month 👏"
            }
        }
        // 6) total vs last month
        percentChange(totalExpense, previousExpense)?.let { p ->
            if (p >= 15 && totalExpense - previousExpense >= MIN_CHANGE) {
                out += if (arabic) "إجمالي مصروفاتك أعلى بـ $p% من الشهر الماضي" else "Total spending is $p% higher than last month"
            }
        }
        return out
    }

    /**
     * A spending spike: today's total is above mean + 2·stddev of the previous days AND at least
     * 2× the mean AND above a minimum amount.
     */
    fun isSpike(previousDays: List<Long>, today: Long, minAmount: Long = 10_000L): Boolean {
        if (today < minAmount || previousDays.size < 7) return false
        val mean = previousDays.average()
        val variance = previousDays.map { (it - mean) * (it - mean) }.average()
        val sd = sqrt(variance)
        return today > mean + 2 * sd && today >= 2 * mean
    }

    /** A single transaction is unusually large compared to the category history (3× median). */
    fun isUnusualTransaction(history: List<Long>, amount: Long, minAmount: Long = 10_000L): Boolean {
        if (amount < minAmount || history.size < 5) return false
        val sorted = history.sorted()
        val median = sorted[sorted.size / 2]
        return median > 0 && amount >= 3 * median
    }

    /** Returns the highest threshold (80 or 100) reached, or null. */
    fun budgetThreshold(status: BudgetStatus): Int? = when {
        status.budget <= 0 -> null
        status.ratio >= 1.0 -> 100
        status.ratio >= 0.8 -> 80
        else -> null
    }
}
