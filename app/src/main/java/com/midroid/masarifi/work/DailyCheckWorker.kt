package com.midroid.masarifi.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.midroid.masarifi.analysis.BudgetStatus
import com.midroid.masarifi.analysis.Insights
import com.midroid.masarifi.app
import com.midroid.masarifi.data.TxnType
import com.midroid.masarifi.util.Money
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * Daily (and on-change) check: repeating transactions, budget 80%/100% alerts, spending spikes,
 * and one smart tip per day.
 */
class DailyCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val app = ctx.app
        val repo = app.repository
        val settings = app.settings
        val s = settings.current
        val ar = s.arabic
        val res = LocalizedRes.of(ctx, ar)
        val cur = s.currencyInfo
        val fmt: (Long) -> String = { Money.format(it, cur, ar) }
        val isQuick = tags.contains(TAG_QUICK)

        runCatching { repo.ensureDefaults(); if (!isQuick) repo.processRecurring() }

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val month = YearMonth.from(today)
        val monthStart = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val monthEnd = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val expenses = repo.txnDao.between(monthStart, monthEnd).filter { it.type == TxnType.EXPENSE }
        val cats = repo.categoryDao.all().associateBy { it.id }

        // ---- budgets ----
        if (s.budgetAlerts) {
            val budgets = repo.budgetDao.all()
            for (b in budgets) {
                val spent = if (b.categoryId == 0L) expenses.sumOf { it.amount } else expenses.filter { it.categoryId == b.categoryId }.sumOf { it.amount }
                val name = if (b.categoryId == 0L) res.getString(com.midroid.masarifi.R.string.overall_budget) else cats[b.categoryId]?.name(ar) ?: continue
                val status = BudgetStatus(b.categoryId, name, b.amount, spent)
                val threshold = Insights.budgetThreshold(status) ?: continue
                val key = "budget_${month}_${b.categoryId}_$threshold"
                if (settings.flag(key)) continue
                settings.setFlag(key)
                if (threshold == 100) settings.setFlag("budget_${month}_${b.categoryId}_80")
                val title = res.getString(if (threshold == 100) com.midroid.masarifi.R.string.budget_exceeded_title else com.midroid.masarifi.R.string.budget_80_title)
                val text = res.getString(com.midroid.masarifi.R.string.budget_alert_text, name, fmt(spent), fmt(b.amount), (status.ratio * 100).toInt())
                Notifier.show(ctx, Notifier.CH_BUDGET, (2000 + b.categoryId * 2 + if (threshold == 100) 1 else 0).toInt(), title, text)
            }
        }

        // ---- spending spike (once per day) ----
        if (s.spikeAlerts && settings.lastSpikeCheckDay != today.toString()) {
            val from = today.minusDays(30).atStartOfDay(zone).toInstant().toEpochMilli()
            val todayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
            val tomorrowStart = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val recent = repo.txnDao.between(from, tomorrowStart).filter { it.type == TxnType.EXPENSE }
            val byDay = recent.filter { it.dateTime < todayStart }.groupBy {
                java.time.Instant.ofEpochMilli(it.dateTime).atZone(zone).toLocalDate()
            }
            val daily = (1..30).map { d -> byDay[today.minusDays(d.toLong())]?.sumOf { it.amount } ?: 0L }
            val todayTotal = recent.filter { it.dateTime >= todayStart }.sumOf { it.amount }
            if (recent.size >= 10 && Insights.isSpike(daily, todayTotal)) {
                settings.lastSpikeCheckDay = today.toString()
                Notifier.show(
                    ctx, Notifier.CH_INSIGHTS, 3001,
                    res.getString(com.midroid.masarifi.R.string.spike_title),
                    res.getString(com.midroid.masarifi.R.string.spike_text, fmt(todayTotal), fmt(daily.average().toLong())),
                )
            }
        }
        return Result.success()
    }

    companion object {
        const val TAG_QUICK = "quick_check"
    }
}
