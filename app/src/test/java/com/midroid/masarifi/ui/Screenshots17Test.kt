package com.midroid.masarifi.ui

import android.graphics.Bitmap
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.midroid.masarifi.MainActivity
import com.midroid.masarifi.data.Txn
import com.midroid.masarifi.data.TxnType
import com.midroid.masarifi.ui.summary.PeriodChoice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.LocalTime

/** 1.7.0 «الملخص المالي» screenshots (synthetic data only) → build/screenshots-1.7 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xhdpi")
class Screenshots17Test {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val dir = File("build/screenshots-1.7").apply { mkdirs() }

    private fun shot(name: String) {
        rule.waitForIdle()
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(android.graphics.Canvas(bmp)) }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun render() {
        val vm = ViewModelProvider(rule.activity)[AppViewModel::class.java]
        val repo = vm.getApplication<com.midroid.masarifi.MasarifiApp>().repository
        runBlocking {
            withContext(Dispatchers.IO) { repo.database.clearAllTables() }
            repo.ensureDefaults()
            val acc = repo.accountDao.all().first().id
            val cats = repo.categoryDao.all()
            fun cat(k: String) = cats.first { it.key == k }.id
            fun at(d: String) = Fmt.toMillis(LocalDate.parse(d).atTime(LocalTime.NOON))
            suspend fun e(omr: Int, d: String, k: String, note: String, auto: Boolean = false, merchant: String? = null) =
                repo.saveTxn(Txn(type = TxnType.EXPENSE, amount = omr * 1000L, accountId = acc, categoryId = cat(k), note = note, dateTime = at(d), isAuto = auto, merchant = merchant))
            for (m in 7..9) {
                val mm = "2026-0$m"
                repo.saveTxn(Txn(type = TxnType.INCOME, amount = 650_000, accountId = acc, categoryId = cat("salary"), note = "راتب", dateTime = at("$mm-01")))
                e(18, "$mm-03", "grocery", "بقالة الأسبوع"); e(22, "$mm-12", "grocery", "بقالة"); e(9, "$mm-08", "food", "مطعم")
                e(6, "$mm-15", "food", "كرك وشاي"); e(25, "$mm-10", "internet", "Internet SAMPLE")
                e(14, "$mm-20", "fuel", "وقود"); e(8, "$mm-06", "internet", "ChatGPT Plus")
            }
            e(9, "2026-08-06", "internet", "Claude Pro")
            e(420, "2026-09-14", "car", "تغيير جير السيارة"); e(35, "2026-08-22", "car", "تغيير زيت")
            e(4, "2026-09-25", "other", "", auto = true, merchant = "SAMPLE STORE"); e(12, "2026-09-26", "other", "", auto = true, merchant = "SAMPLE MART")
        }
        vm.summaryPeriod.value = PeriodChoice.Quarter(2026, 3)
        rule.waitUntil(5_000) { rule.onAllNodesWithText("التحليل").fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithText("التحليل")[0].performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("الملخص المالي", substring = true).fetchSemanticsNodes().isNotEmpty() }
        shot("01_stats_entry_card")
        rule.onNodeWithText("الملخص المالي", substring = true).performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("المصروفات حسب الفئة").fetchSemanticsNodes().isNotEmpty() }
        shot("02_summary_overview_categories")
        rule.onNodeWithTag("summaryList").performScrollToNode(hasText("أكبر 10 مصروفات"))
        shot("03_summary_months_chart")
        rule.onNodeWithTag("summaryList").performScrollToNode(hasText("ملاحظات تلقائية"))
        shot("04_summary_insights")
        rule.onNodeWithTag("summaryList").performScrollToNode(hasText("تطبيق كميزانيات"))
        shot("05_summary_budget")
        // export path: PNG + PDF render with RTL text
        val ctx = rule.activity
        val lines = listOf(
            com.midroid.masarifi.ui.summary.ReportLine.Title("الملخص المالي — مصاريفي"),
            com.midroid.masarifi.ui.summary.ReportLine.Header("نظرة عامة"),
            com.midroid.masarifi.ui.summary.ReportLine.Row(listOf("إجمالي المصروفات الفعلية", "786.000 ر.ع."), listOf(3f, 2f)),
            com.midroid.masarifi.ui.summary.ReportLine.Para("• متوسط إنفاقك اليومي على الطعام 1.793 ر.ع."),
            com.midroid.masarifi.ui.summary.ReportLine.Bars(listOf(Triple("يوليو", 650_000L, 102_000L), Triple("أغسطس", 650_000L, 146_000L))),
        )
        val bmp = com.midroid.masarifi.ui.summary.ReportExport.renderBitmap(lines)
        check(bmp.width == 1080 && bmp.height > 200)
        File(dir, "06_export_image.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        // PdfDocument has no native backend under Robolectric; the PDF path is exercised on device only.
    }
}
