package com.midroid.masarifi.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.midroid.masarifi.MainActivity
import com.midroid.masarifi.R
import com.midroid.masarifi.app
import com.midroid.masarifi.bills.Bill
import com.midroid.masarifi.bills.BillLogic
import com.midroid.masarifi.util.Money
import com.midroid.masarifi.work.LocalizedRes
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

/** Pure widget content (testable). */
data class WidgetRow(val name: String, val amount: String, val when_: String, val overdue: Boolean)
data class WidgetData(val rows: List<WidgetRow>, val monthTotal: String, val empty: Boolean)

object BillsWidgetModel {
    fun build(ctx: Context, bills: List<Bill>, today: LocalDate, fmt: (Long) -> String): WidgetData {
        val rows = BillLogic.nextBills(bills, 3).map { b ->
            val d = BillLogic.daysLeft(b, today)
            val w = when {
                d < 0 -> ctx.getString(R.string.bill_overdue_days, -d)
                d == 0 -> ctx.getString(R.string.bill_due_today)
                else -> ctx.getString(R.string.bill_in_days, d)
            }
            WidgetRow(b.name, fmt(b.amount), w, d < 0)
        }
        val total = BillLogic.upcomingInMonth(bills, today, YearMonth.from(today)).sumOf { it.amount }
        return WidgetData(rows, ctx.getString(R.string.widget_month_total, fmt(total)), rows.isEmpty())
    }
}

/** Home-screen widget «الفواتير القادمة» (plain RemoteViews, no extra libraries). */
class BillsWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        context.app.appScope.launch {
            try { update(context, manager, ids) } finally { pending.finish() }
        }
    }

    companion object {
        private val ROW_NAME = intArrayOf(R.id.w_name1, R.id.w_name2, R.id.w_name3)
        private val ROW_WHEN = intArrayOf(R.id.w_when1, R.id.w_when2, R.id.w_when3)
        private val ROW_AMOUNT = intArrayOf(R.id.w_amount1, R.id.w_amount2, R.id.w_amount3)
        private val ROW = intArrayOf(R.id.w_row1, R.id.w_row2, R.id.w_row3)

        suspend fun update(context: Context, manager: AppWidgetManager, ids: IntArray) {
            if (ids.isEmpty()) return
            val app = context.app
            val s = app.settings.current
            val res = LocalizedRes.of(context, s.arabic)
            val data = BillsWidgetModel.build(res, app.repository.billDao.all(), LocalDate.now()) { Money.format(it, s.currencyInfo, s.arabic) }
            val open = PendingIntent.getActivity(context, 7, Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_BILLS, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            for (id in ids) {
                val v = RemoteViews(context.packageName, R.layout.widget_bills)
                v.setTextViewText(R.id.w_title, res.getString(R.string.widget_title))
                v.setTextViewText(R.id.w_total, if (data.empty) res.getString(R.string.bill_empty_short) else data.monthTotal)
                for (i in 0 until 3) {
                    val r = data.rows.getOrNull(i)
                    v.setViewVisibility(ROW[i], if (r == null) android.view.View.GONE else android.view.View.VISIBLE)
                    if (r != null) {
                        v.setTextViewText(ROW_NAME[i], r.name); v.setTextViewText(ROW_AMOUNT[i], r.amount); v.setTextViewText(ROW_WHEN[i], r.when_)
                        v.setTextColor(ROW_WHEN[i], if (r.overdue) 0xFFF25C54.toInt() else 0xFF8E8F95.toInt())
                    }
                }
                v.setOnClickPendingIntent(R.id.w_root, open)
                manager.updateAppWidget(id, v)
            }
        }

        /** Re-renders all placed widgets (after bill changes / daily). */
        fun refresh(context: Context) {
            runCatching {
                val m = AppWidgetManager.getInstance(context) ?: return
                val ids = m.getAppWidgetIds(ComponentName(context, BillsWidget::class.java))
                if (ids.isNotEmpty()) context.app.appScope.launch { runCatching { update(context, m, ids) } }
            }
        }
    }
}
