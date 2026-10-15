package com.midroid.masarifi.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.midroid.masarifi.MainActivity
import com.midroid.masarifi.app
import com.midroid.masarifi.data.Txn
import com.midroid.masarifi.data.TxnType
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.LocalTime

/** Renders key screens to PNG (build/screenshots) for visual review. Test data lives only in the test DB. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xhdpi")
class ScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun shot(name: String) {
        rule.waitForIdle()
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(android.graphics.Canvas(bmp)) }
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Draws the top-most window (e.g. a dialog) – dialogs are separate windows not included in the activity decor view. */
    private fun shotTopWindow(name: String) {
        runCatching {
            val wmg = Class.forName("android.view.WindowManagerGlobal")
            val inst = wmg.getMethod("getInstance").invoke(null)
            val f = wmg.getDeclaredField("mViews").apply { isAccessible = true }
            @Suppress("UNCHECKED_CAST")
            val views = f.get(inst) as List<android.view.View>
            val v = views.last()
            val bmp = Bitmap.createBitmap(v.width.coerceAtLeast(1), v.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            rule.runOnUiThread { android.graphics.Canvas(bmp).also { it.drawColor(android.graphics.Color.BLACK); v.draw(it) } }
            val dir = File("build/screenshots").apply { mkdirs() }
            File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test fun screens() {
        val app = rule.activity.app
        runBlocking {
            val repo = app.repository
            repo.ensureDefaults()
            val cats = repo.categoryDao.all()
            val cash = repo.accountDao.all().first().id
            fun cat(key: String) = cats.first { it.key == key }.id
            val today = LocalDate.now()
            fun at(d: Int, h: Int) = com.midroid.masarifi.ui.Fmt.toMillis(today.minusDays(d.toLong()).atTime(LocalTime.of(h, 15)))
            repo.saveTxn(Txn(type = TxnType.EXPENSE, amount = 4300, accountId = cash, categoryId = cat("grocery"), note = "LuLu", dateTime = at(0, 10)))
            repo.saveTxn(Txn(type = TxnType.EXPENSE, amount = 1500, accountId = cash, categoryId = cat("food"), note = "كرك وشاي", dateTime = at(0, 8)))
            repo.saveTxn(Txn(type = TxnType.EXPENSE, amount = 7200, accountId = cash, categoryId = cat("fuel"), note = "Shell", dateTime = at(1, 18), isAuto = true))
            repo.saveTxn(Txn(type = TxnType.INCOME, amount = 850000, accountId = cash, categoryId = cat("salary"), note = "راتب", dateTime = at(2, 9)))
            repo.saveTxn(Txn(type = TxnType.EXPENSE, amount = 25000, accountId = cash, categoryId = cat("internet"), note = "Omantel", dateTime = at(2, 12)))
            repo.setBudget(0, 300000)
        }
        rule.waitUntil(5_000) { rule.onAllNodesWithText("LuLu").fetchSemanticsNodes().isNotEmpty() }
        shot("01_trans_daily")
        rule.onNodeWithText("التقويم").performClick(); shot("02_trans_calendar")
        rule.onNodeWithText("التحليل").performClick(); shot("03_stats_pie")
        rule.onNodeWithText("الميزانية").performClick(); shot("04_stats_budget")
        rule.onNodeWithText("نصائح").performClick(); shot("05_stats_tips")
        rule.onNodeWithText("الحسابات").performClick(); shot("06_accounts")
        rule.onNodeWithText("المزيد").performClick(); shot("07_more")
        // Money Manager import preview (synthetic fixture)
        val vm = androidx.lifecycle.ViewModelProvider(rule.activity)[AppViewModel::class.java]
        runBlocking {
            val bytes = javaClass.classLoader!!.getResourceAsStream("mm/money_manager_sample.xlsx")!!.readBytes()
            val plan = com.midroid.masarifi.data.mm.MoneyManagerImporter(app.repository).plan(com.midroid.masarifi.data.mm.MoneyManagerParser.parseFile(bytes))
            vm.mmUi.value = MmUi.Preview(plan)
        }
        rule.waitForIdle()
        rule.onNodeWithText("معاينة الاستيراد").assertExists()
        shotTopWindow("09_mm_preview")
        rule.runOnUiThread { vm.mmDismiss() }
        rule.onNodeWithText("النسخ الاحتياطي على Google Drive").performScrollTo().performClick(); shot("10_backup")
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        rule.onNodeWithText("المعاملات").performClick()
        rule.onNodeWithContentDescription("إضافة").performClick(); shot("08_add")
    }
}
