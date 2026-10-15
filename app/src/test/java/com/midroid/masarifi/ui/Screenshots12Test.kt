package com.midroid.masarifi.ui

import android.graphics.Bitmap
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.midroid.masarifi.MainActivity
import com.midroid.masarifi.app
import com.midroid.masarifi.data.Account
import com.midroid.masarifi.data.AccountGroup
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

/** 1.2.0 screenshots (synthetic test data only) → build/screenshots-1.2 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xhdpi")
class Screenshots12Test {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val dir = File("build/screenshots-1.2").apply { mkdirs() }

    private fun shot(name: String) {
        rule.waitForIdle()
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(android.graphics.Canvas(bmp)) }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Dialogs live in their own window: draw activity + top window composited. */
    private fun shotWithDialog(name: String) {
        rule.waitForIdle()
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
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun render() {
        val app = rule.activity.app
        runBlocking {
            val repo = app.repository
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { repo.database.clearAllTables() } // DB singleton is shared across tests in this JVM
            repo.ensureDefaults()
            val cash = repo.accountDao.all().first().id
            val bank = repo.accountDao.insert(Account(name = "Bank Muscat ••0099", groupType = AccountGroup.BANK, cardLast4 = "0099"))
            val cats = repo.categoryDao.all()
            fun cat(k: String) = cats.first { it.key == k }.id
            val today = LocalDate.now()
            fun at(d: Int, h: Int, m: Int = 0) = Fmt.toMillis(today.minusDays(d.toLong()).atTime(LocalTime.of(h, m)))
            fun add(t: Txn) = runBlocking { repo.txnDao.insert(t) }
            add(Txn(type = TxnType.EXPENSE, amount = 4_300, accountId = bank, categoryId = cat("grocery"), note = "LULU HYPERMARKET", dateTime = at(0, 10, 5), isAuto = true, smsHash = "demo-sms-1", merchant = "LULU HYPERMARKET"))
            add(Txn(type = TxnType.EXPENSE, amount = 12_750, accountId = cash, categoryId = cat("food"), note = "مطعم الأرز", dateTime = at(0, 13), smsLinked = true, merchant = "مطعم الأرز الذهبي"))
            add(Txn(type = TxnType.EXPENSE, amount = 500, accountId = cash, categoryId = cat("food"), note = "كرك", dateTime = at(0, 8, 0)))
            add(Txn(type = TxnType.EXPENSE, amount = 500, accountId = cash, categoryId = cat("food"), note = "كرك", dateTime = at(0, 8, 4)))
            val fuel = add(Txn(type = TxnType.EXPENSE, amount = 7_200, accountId = cash, categoryId = cat("fuel"), note = "Shell", dateTime = at(1, 18)))
            add(Txn(type = TxnType.EXPENSE, amount = 7_200, accountId = bank, categoryId = cat("fuel"), note = "SHELL OMAN", dateTime = at(1, 9), isAuto = true, smsHash = "demo-sms-2", merchant = "SHELL OMAN", possibleDupOf = "$fuel"))
            add(Txn(type = TxnType.INCOME, amount = 850_000, accountId = bank, categoryId = cat("salary"), note = "راتب", dateTime = at(2, 9), smsHash = "mm:demo"))
        }
        rule.waitUntil(5_000) { rule.onAllNodesWithText("كرك").fetchSemanticsNodes().size >= 2 }
        shot("00_trans_badges_banner")

        // multi-select
        rule.onAllNodesWithText("كرك")[0].performTouchInput { longClick() }
        rule.onAllNodesWithText("كرك")[1].performClick()
        rule.onNodeWithText("مطعم الأرز").performClick()
        shot("01_selection_mode")
        rule.onNodeWithContentDescription("حذف").performClick()
        shotWithDialog("02_delete_confirmation")
        rule.onNodeWithText("إلغاء").performClick()
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()

        // duplicate warning on the add screen (same amount as the LULU SMS entry)
        rule.onNodeWithContentDescription("إضافة").performClick()
        rule.onAllNodes(hasSetTextAction())[0].performTextInput("4.3")
        rule.onAllNodes(hasSetTextAction())[1].performTextInput("بقالة")
        rule.onNodeWithText("حفظ").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("حفظ على أي حال").fetchSemanticsNodes().isNotEmpty() }
        shot("03_add_duplicate_warning")
        rule.onNodeWithText("إلغاء").performClick()
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()

        // review screen via the banner
        rule.onNodeWithText("من المكررات المحتملة", substring = true).performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("دمج").fetchSemanticsNodes().isNotEmpty() }
        shot("04_duplicates_review")
    }
}
