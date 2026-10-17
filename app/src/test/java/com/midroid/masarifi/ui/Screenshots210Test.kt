package com.midroid.masarifi.ui

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.printToLog
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.midroid.masarifi.MainActivity
import com.midroid.masarifi.app
import com.midroid.masarifi.bills.Bill
import com.midroid.masarifi.bills.RecurrenceType
import com.midroid.masarifi.data.Txn
import com.midroid.masarifi.data.TxnType
import com.midroid.masarifi.util.Money
import com.midroid.masarifi.widget.BillsWidgetModel
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/** 2.1.0 «الفواتير»: list, pay dialog with SMS merge offer, calendar, history, edit, budget card, widget → build/screenshots-2.1.0 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xhdpi")
class Screenshots210Test {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val dir = File(System.getProperty("masarifi.shots") ?: "build/screenshots-2.1.0").apply { mkdirs() }
    private fun save(bmp: Bitmap, name: String) = File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    private fun shot(name: String) {
        rule.waitForIdle()
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(android.graphics.Canvas(bmp)) }
        save(bmp, name)
    }
    /** Dialogs live in their own window: draw activity + top window composited (as in Screenshots12Test). */
    private fun shotWithDialog(name: String) {
        val decor = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
        val wmg = Class.forName("android.view.WindowManagerGlobal")
        val inst = wmg.getMethod("getInstance").invoke(null)
        @Suppress("UNCHECKED_CAST")
        val views = wmg.getDeclaredField("mViews").apply { isAccessible = true }.get(inst) as List<android.view.View>
        val top = views.last()
        rule.runOnUiThread {
            val c = android.graphics.Canvas(bmp)
            decor.draw(c)
            c.drawColor(0x99000000.toInt())
            c.save(); c.translate((decor.width - top.width) / 2f, (decor.height - top.height) / 2f); top.draw(c); c.restore()
        }
        save(bmp, name)
    }
    /** With a text field in a dialog the cursor blinks forever: drive the clock by hand until [t] shows. */
    private fun advanceUntil(t: String) {
        repeat(300) {
            rule.mainClock.advanceTimeBy(100)
            if (rule.onAllNodesWithText(t, substring = true).fetchSemanticsNodes().isNotEmpty()) return
            Thread.sleep(50)
        }
        error("not shown: $t")
    }
    private fun back() { rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }; rule.waitForIdle() }
    private fun waitText(t: String) = rule.waitUntil(5_000) { rule.onAllNodesWithText(t, substring = true).fetchSemanticsNodes().isNotEmpty() }

    @Test fun render() {
        val repo = rule.activity.app.repository
        val today = LocalDate.now()
        runBlocking {
            repo.ensureDefaults()
            val cats = repo.categoryDao.all()
            fun cat(k: String) = cats.first { it.key == k }.id
            val acc = repo.accountDao.all().first().id
            fun bill(name: String, amount: Long, key: String, due: LocalDate, type: RecurrenceType = RecurrenceType.MONTHLY) = Bill(
                name = name, amount = amount, categoryId = cat(key), accountId = acc, recurrence = type.name, anchorDay = due.dayOfMonth, nextDue = due.toEpochDay())
            repo.bills.save(bill("إيجار الشقة", 250_000, "rent", today.plusDays(2)))
            val fibre = repo.bills.save(bill("عمانتل فايبر", 25_000, "internet", today.minusDays(3)))
            repo.bills.save(bill("تأمين السيارة", 120_000, "car_insurance", today.plusDays(20), RecurrenceType.YEARLY))
            repo.bills.save(bill("أوريدو موبايل", 12_500, "internet", today.plusDays(9)))
            repo.bills.save(bill("كهرباء", 18_750, "other", today.plusDays(5), RecurrenceType.CUSTOM_MONTHS).copy(recurInterval = 2))
            // paid last month + an SMS for the fibre bill that arrived two days ago
            val pay = repo.bills.pay(fibre, 25_000, acc, cat("internet"), System.currentTimeMillis() - 31L * 86_400_000)!!
            repo.billDao.get(fibre)!!.let { repo.billDao.update(it.copy(nextDue = today.minusDays(3).toEpochDay())) }
            repo.billDao.payment(pay.paymentId)!!.let { repo.billDao.updatePayment(it.copy(dueDate = today.minusDays(3).minusMonths(1).toEpochDay())) }
            repo.txnDao.insert(Txn(type = TxnType.EXPENSE, amount = 25_000, accountId = acc, dateTime = System.currentTimeMillis() - 2 * 86_400_000L,
                isAuto = true, smsHash = "shot-sms-1", merchant = "OMANTEL", note = "OMANTEL"))
            repo.budgetDao.upsert(com.midroid.masarifi.data.Budget(0, 600_000))
        }
        rule.waitUntil(5_000) { rule.onAllNodesWithText("الفواتير").fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithText("الفواتير").onFirst().performClick()
        waitText("عمانتل فايبر")
        shot("01_bills_list")
        rule.mainClock.autoAdvance = false
        rule.onAllNodesWithText("تم الدفع").onFirst().performClick()
        advanceUntil("دمج مع")
        rule.mainClock.advanceTimeBy(600)
        shotWithDialog("02_pay_dialog_merge_offer")
        // dialog confirm is the last «تم الدفع»
        rule.onAllNodesWithText("تم الدفع")[rule.onAllNodesWithText("تم الدفع").fetchSemanticsNodes().size - 1].performClick()
        rule.mainClock.advanceTimeBy(500)
        rule.mainClock.autoAdvance = true
        waitText("دُمج مع الرسالة")
        shot("03_paid_merged_undo_snackbar")
        rule.onNodeWithText("التقويم").performClick()
        shot("04_bills_calendar")
        rule.onNodeWithText("السجل").performClick()
        waitText("دُفعت")
        shot("05_payment_history")
        rule.onNodeWithText("القائمة").performClick()
        rule.onNodeWithText("إيجار الشقة").performClick()
        waitText("سجل المدفوعات")
        shot("06_edit_bill")
        back()
        rule.onNodeWithText("التحليل").performClick()
        rule.onNodeWithText("الميزانية").performClick()
        waitText("فواتير قادمة هذا الشهر")
        shot("07_budget_upcoming_bills")
        // home-screen widget (RemoteViews inflated and drawn off-screen)
        rule.runOnUiThread {
            val ctx = rule.activity
            val s = ctx.app.settings.current
            val data = BillsWidgetModel.build(ctx, runBlocking { ctx.app.repository.billDao.all() }, LocalDate.now()) { Money.format(it, s.currencyInfo, s.arabic) }
            val v = android.view.LayoutInflater.from(ctx).inflate(com.midroid.masarifi.R.layout.widget_bills, null)
            val ids = listOf(
                Triple(com.midroid.masarifi.R.id.w_name1, com.midroid.masarifi.R.id.w_when1, com.midroid.masarifi.R.id.w_amount1),
                Triple(com.midroid.masarifi.R.id.w_name2, com.midroid.masarifi.R.id.w_when2, com.midroid.masarifi.R.id.w_amount2),
                Triple(com.midroid.masarifi.R.id.w_name3, com.midroid.masarifi.R.id.w_when3, com.midroid.masarifi.R.id.w_amount3),
            )
            data.rows.forEachIndexed { i, r ->
                v.findViewById<android.widget.TextView>(ids[i].first).text = r.name
                v.findViewById<android.widget.TextView>(ids[i].second).apply { text = r.when_; if (r.overdue) setTextColor(0xFFF25C54.toInt()) }
                v.findViewById<android.widget.TextView>(ids[i].third).text = r.amount
            }
            v.findViewById<android.widget.TextView>(com.midroid.masarifi.R.id.w_total).text = data.monthTotal
            val w = (250 * ctx.resources.displayMetrics.density).toInt(); val h = (210 * ctx.resources.displayMetrics.density).toInt()
            v.measure(android.view.View.MeasureSpec.makeMeasureSpec(w, android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(h, android.view.View.MeasureSpec.EXACTLY))
            v.layout(0, 0, w, h)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            android.graphics.Canvas(bmp).apply { drawColor(0xFF3A5A7A.toInt()) }.also { v.draw(it) }
            save(bmp, "08_home_widget")
        }
    }
}
