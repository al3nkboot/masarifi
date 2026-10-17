package com.midroid.masarifi.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.midroid.masarifi.R
import com.midroid.masarifi.app
import com.midroid.masarifi.bills.BillLogic
import com.midroid.masarifi.bills.ReminderKind
import com.midroid.masarifi.util.Money
import com.midroid.masarifi.widget.BillsWidget
import java.time.LocalDate

/** Daily bill reminders: N days before (per bill), due today, overdue. One notification per bill per day. */
class BillReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val app = ctx.app
        val s = app.settings.current
        val res = LocalizedRes.of(ctx, s.arabic)
        val today = LocalDate.now()
        val bills = app.repository.billDao.all()
        val done = bills.filter { app.settings.flag("bill_${it.uuid}_$today") }.map { it.id }.toSet()
        for (r in BillLogic.reminders(bills, today, done)) {
            app.settings.setFlag("bill_${r.bill.uuid}_$today")
            val amount = Money.format(r.bill.amount, s.currencyInfo, s.arabic)
            val text = when (r.kind) {
                ReminderKind.OVERDUE -> res.getString(R.string.bill_notif_overdue, r.bill.name, amount, r.days)
                ReminderKind.DUE_TODAY -> res.getString(R.string.bill_notif_today, r.bill.name, amount)
                ReminderKind.UPCOMING -> res.getString(R.string.bill_notif_upcoming, r.bill.name, amount, r.days)
            }
            Notifier.show(ctx, Notifier.CH_BILLS, (5000 + r.bill.id % 100_000).toInt(), res.getString(R.string.bill_notif_title), text, openBills = true)
        }
        BillsWidget.refresh(ctx)
        return Result.success()
    }
}
