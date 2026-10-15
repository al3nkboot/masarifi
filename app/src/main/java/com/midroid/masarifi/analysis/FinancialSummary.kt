package com.midroid.masarifi.analysis

import com.midroid.masarifi.data.Category
import com.midroid.masarifi.data.Txn
import com.midroid.masarifi.data.TxnType
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.roundToLong

/** Report period [start, endExclusive). */
data class SummaryPeriod(val start: LocalDate, val endExclusive: LocalDate) {
    val months: List<YearMonth>
        get() = generateSequence(YearMonth.from(start)) { it.plusMonths(1) }.takeWhile { !it.atDay(1).isAfter(endExclusive.minusDays(1)) }.toList()

    companion object {
        fun month(ym: YearMonth) = SummaryPeriod(ym.atDay(1), ym.plusMonths(1).atDay(1))
        fun quarter(year: Int, q: Int): SummaryPeriod {
            require(q in 1..4)
            val s = LocalDate.of(year, (q - 1) * 3 + 1, 1)
            return SummaryPeriod(s, s.plusMonths(3))
        }
        fun custom(from: LocalDate, toInclusive: LocalDate) = SummaryPeriod(minOf(from, toInclusive), maxOf(from, toInclusive).plusDays(1))
    }
}

data class CategoryRow(val categoryId: Long?, val count: Int, val amount: Long, val percent: Double, val average: Long)
data class MonthRow(
    val month: YearMonth, val income: Long, val expense: Long, val percentOfPeriod: Double,
    /** Expense change vs the previous month of the period in % (null for the first month / previous month without expenses). */
    val changePercent: Double?,
) { val net: Long get() = income - expense }

data class BudgetSuggestion(val categoryId: Long, val baselineMonthly: Long, val suggested: Long)

enum class InsightKind { GROWTH, FOOD_DAILY, SUBSCRIPTIONS, CAR_FUND, REVIEW, DEFICIT, ONE_OFF }

/** A rule-based observation; [txnIds] are the transactions it refers to (tap → open). */
data class SummaryInsight(val kind: InsightKind, val text: String, val txnIds: List<Long> = emptyList())

data class FinancialSummary(
    val period: SummaryPeriod,
    val totalExpense: Long,
    val expenseCount: Int,
    val totalIncome: Long,
    val incomeCount: Int,
    val categories: List<CategoryRow>,
    val months: List<MonthRow>,
    val topExpenses: List<Txn>,
    val oneOffs: List<Txn>,
    val needsReview: List<Txn>,
    val baselineMonthly: Long,
    val budgets: List<BudgetSuggestion>,
    val insights: List<SummaryInsight>,
    /** Days of the period up to today (used for daily averages). */
    val days: Int,
) {
    val net: Long get() = totalIncome - totalExpense
    val count: Int get() = expenseCount + incomeCount
}

/**
 * Local, rule-based financial summary (no network). Real expenses/income only: transfers (own-account transfers, ATM → Cash,
 * wallet top-ups) are excluded, and duplicate SMS that were linked/merged are not separate transactions anymore.
 * Amounts are thousandths of the currency (OMR 3 decimals).
 */
object SummaryCalc {
    /** One-off: at least [ONE_OFF_MIN] and ≥ [ONE_OFF_FACTOR] × the average of the other expenses of its category. */
    const val ONE_OFF_MIN = 20_000L
    const val ONE_OFF_FACTOR = 3.0
    /** Budget suggestions are rounded up to this step (5 OMR) and only made for categories with at least this baseline. */
    const val BUDGET_STEP = 5_000L

    val FOOD_KEYS = setOf("food", "grocery")
    val CAR_KEYS = setOf("car", "car_insurance")
    private val CAR_WORDS = listOf("صيانة", "maintenance", "سيارة", "car ")
    private val SUB_WORDS = listOf("ai", "ذكاء", "اشتراك", "subscription", "internet", "إنترنت")
    private val AI_SERVICES = listOf("openai", "chatgpt", "claude", "anthropic", "gemini", "google one", "midjourney", "perplexity", "copilot", "poe", "grok", "x premium", "cursor")

    data class Texts(
        val money: (Long) -> String,
        val month: (YearMonth) -> String,
        val catName: (Long?) -> String,
    )

    fun compute(all: List<Txn>, cats: List<Category>, period: SummaryPeriod, zone: ZoneId, today: LocalDate, t: Texts): FinancialSummary {
        val from = period.start.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = period.endExclusive.atStartOfDay(zone).toInstant().toEpochMilli()
        val inPeriod = all.filter { it.dateTime in from until to }
        val expenses = inPeriod.filter { it.type == TxnType.EXPENSE }
        val incomes = inPeriod.filter { it.type == TxnType.INCOME }
        val totalExp = expenses.sumOf { it.amount }
        val totalInc = incomes.sumOf { it.amount }
        fun dateOf(x: Txn) = Instant.ofEpochMilli(x.dateTime).atZone(zone).toLocalDate()
        val catById = cats.associateBy { it.id }

        val categories = expenses.groupBy { it.categoryId }.map { (id, list) ->
            val sum = list.sumOf { it.amount }
            CategoryRow(id, list.size, sum, pct(sum, totalExp), sum / list.size)
        }.sortedWith(compareByDescending<CategoryRow> { it.amount }.thenBy { it.categoryId ?: Long.MAX_VALUE })

        var prev: Long? = null
        val months = period.months.map { ym ->
            val e = expenses.filter { YearMonth.from(dateOf(it)) == ym }.sumOf { it.amount }
            val i = incomes.filter { YearMonth.from(dateOf(it)) == ym }.sumOf { it.amount }
            val change = prev?.takeIf { it > 0 }?.let { (e - it) * 100.0 / it }
            prev = e
            MonthRow(ym, i, e, pct(e, totalExp), change)
        }

        val top = expenses.sortedWith(compareByDescending<Txn> { it.amount }.thenBy { it.dateTime }).take(10)
        val oneOffs = oneOffs(expenses)
        val oneOffIds = oneOffs.map { it.id }.toSet()
        val review = inPeriod.filter { needsReview(it, catById) }.sortedByDescending { it.dateTime }

        val endForDays = minOf(period.endExclusive, today.plusDays(1))
        val days = ChronoUnit.DAYS.between(period.start, endForDays).toInt().coerceAtLeast(1)
        val monthsSpan = (days / 30.4375).coerceAtLeast(1.0)

        // budget baseline: monthly average without one-off large items
        val regular = expenses.filter { it.id !in oneOffIds }
        val baseline = (regular.sumOf { it.amount } / monthsSpan).roundToLong()
        val budgets = regular.filter { it.categoryId != null }.groupBy { it.categoryId!! }.mapNotNull { (id, list) ->
            val m = (list.sumOf { it.amount } / monthsSpan).roundToLong()
            if (m < BUDGET_STEP) null else BudgetSuggestion(id, m, roundUp(m, BUDGET_STEP))
        }.sortedByDescending { it.baselineMonthly }

        val s0 = FinancialSummary(period, totalExp, expenses.size, totalInc, incomes.size, categories, months, top, oneOffs, review, baseline, budgets, emptyList(), days)
        return s0.copy(insights = insights(s0, expenses, catById, monthsSpan, t, zone))
    }

    fun pct(part: Long, total: Long): Double = if (total <= 0) 0.0 else part * 100.0 / total

    fun roundUp(v: Long, step: Long): Long = if (v <= 0) 0 else ceil(v.toDouble() / step).toLong() * step

    /** Large one-off expenses (see [ONE_OFF_MIN], [ONE_OFF_FACTOR]); a category with a single item uses the minimum only. */
    fun oneOffs(expenses: List<Txn>): List<Txn> = expenses.groupBy { it.categoryId }.flatMap { (_, list) ->
        list.filter { x ->
            if (x.amount < ONE_OFF_MIN) return@filter false
            val others = list.filter { it.id != x.id }
            others.isEmpty() || x.amount >= ONE_OFF_FACTOR * (others.sumOf { it.amount }.toDouble() / others.size)
        }
    }.sortedByDescending { it.amount }

    /** SMS transaction left in «أخرى»/no category without a note of the user's own. */
    fun needsReview(x: Txn, cats: Map<Long, Category>): Boolean {
        if (!x.isAuto || x.type == TxnType.TRANSFER) return false
        val key = x.categoryId?.let { cats[it]?.key }
        if (x.categoryId != null && key != "other" && key != "other_income") return false
        val note = x.note.trim()
        return note.isEmpty() || x.merchant == null || note == x.merchant.trim()
    }

    private fun catKeyOrName(c: Category?) = ((c?.key ?: "") + " " + (c?.nameAr ?: "") + " " + (c?.nameEn ?: "")).lowercase()

    private fun insights(s: FinancialSummary, expenses: List<Txn>, cats: Map<Long, Category>, monthsSpan: Double, t: Texts, zone: ZoneId): List<SummaryInsight> {
        val out = ArrayList<SummaryInsight>()
        fun ymOf(x: Txn) = YearMonth.from(Instant.ofEpochMilli(x.dateTime).atZone(zone))

        // biggest growth month + drivers
        val growth = s.months.withIndex().filter { (it.value.changePercent ?: 0.0) > 0 }.maxByOrNull { it.value.changePercent!! }
        if (growth != null) {
            val m = growth.value; val p = s.months[growth.index - 1]
            val now = expenses.filter { ymOf(it) == m.month }; val before = expenses.filter { ymOf(it) == p.month }
            val drivers = now.groupBy { it.categoryId }.map { (id, l) -> id to (l.sumOf { it.amount } - before.filter { it.categoryId == id }.sumOf { it.amount }) }
                .filter { it.second > 0 }.sortedByDescending { it.second }.take(2)
            val big = s.oneOffs.filter { ymOf(it) == m.month }.take(3)
            val sb = StringBuilder("أكبر زيادة في المصروفات كانت في ${t.month(m.month)}: ${t.money(m.expense)} مقابل ${t.money(p.expense)} في ${t.month(p.month)} (+${pctText(m.changePercent!!)}).")
            if (drivers.isNotEmpty()) sb.append(" أبرز الفئات المسببة: ").append(drivers.joinToString("، ") { "${t.catName(it.first)} (+${t.money(it.second)})" }).append(".")
            if (big.isNotEmpty()) sb.append(" مصروفات كبيرة لمرة واحدة: ").append(big.joinToString("، ") { "${it.note.ifBlank { t.catName(it.categoryId) }} ${t.money(it.amount)}" }).append(".")
            out += SummaryInsight(InsightKind.GROWTH, sb.toString(), big.map { it.id })
        } else if (s.oneOffs.isNotEmpty()) {
            val big = s.oneOffs.take(3)
            out += SummaryInsight(InsightKind.ONE_OFF, "مصروفات كبيرة لمرة واحدة في الفترة: " + big.joinToString("، ") { "${it.note.ifBlank { t.catName(it.categoryId) }} ${t.money(it.amount)}" } + ".", big.map { it.id })
        }

        // food daily average
        val food = expenses.filter { cats[it.categoryId]?.key in FOOD_KEYS }
        if (food.isNotEmpty()) {
            val sum = food.sumOf { it.amount }
            out += SummaryInsight(InsightKind.FOOD_DAILY, "متوسط إنفاقك اليومي على الطعام والبقالة ${t.money(sum / s.days)} (${t.money(sum)} خلال ${s.days} يوماً).")
        }

        // subscriptions (internet / AI)
        val subs = expenses.filter { x -> val k = catKeyOrName(cats[x.categoryId]); SUB_WORDS.any { w -> Regex("(^|[^a-z])${Regex.escape(w)}([^a-z]|$)").containsMatchIn(k) } }
        if (subs.isNotEmpty()) {
            val ai = subs.mapNotNull { x -> val text = ((x.merchant ?: "") + " " + x.note).lowercase(); AI_SERVICES.firstOrNull { text.contains(it) } }.distinct()
            val sb = StringBuilder("مجموع الاشتراكات (الإنترنت والخدمات الرقمية): ${t.money(subs.sumOf { it.amount })} في ${subs.size} عملية.")
            if (ai.size > 1) sb.append(" لديك ${ai.size} اشتراكات ذكاء اصطناعي (${ai.joinToString("، ")})؛ راجع المتداخل منها واحتفظ بما تستخدمه فعلاً.")
            out += SummaryInsight(InsightKind.SUBSCRIPTIONS, sb.toString(), subs.map { it.id })
        }

        // car / maintenance
        val car = expenses.filter { x -> val c = cats[x.categoryId]; c?.key in CAR_KEYS || CAR_WORDS.any { catKeyOrName(c).contains(it) } }
        if (car.isNotEmpty()) {
            val sum = car.sumOf { it.amount }
            val monthly = (sum / monthsSpan).roundToLong()
            out += SummaryInsight(InsightKind.CAR_FUND, "مصاريف السيارة والصيانة: ${t.money(sum)}. اقتراح: خصّص ${t.money(roundUp(monthly, 1_000))} شهرياً كصندوق طوارئ للسيارة (متوسط إنفاقك الشهري عليها).", car.map { it.id })
        }

        // SMS transactions needing review
        if (s.needsReview.isNotEmpty()) {
            out += SummaryInsight(InsightKind.REVIEW, "${s.needsReview.size} عملية مسجلة تلقائياً من الرسائل في «أخرى» بدون ملاحظة (${t.money(s.needsReview.sumOf { it.amount })}) تحتاج مراجعة وتصنيف.", s.needsReview.map { it.id })
        }

        // deficit months
        val deficit = s.months.filter { it.net < 0 && (it.income > 0 || it.expense > 0) }
        if (deficit.isNotEmpty()) {
            out += SummaryInsight(InsightKind.DEFICIT, "أشهر بعجز (المصروف أكبر من الدخل): " + deficit.joinToString("، ") { "${t.month(it.month)} (${t.money(-it.net)})" } + ".")
        }
        return out.map { it.copy(text = it.text.replace("..", ".")) }
    }

    fun pctText(p: Double): String = if (p >= 10 || p == Math.floor(p)) "${p.roundToLong()}%" else String.format(java.util.Locale.US, "%.1f%%", p)
}
