package com.midroid.masarifi.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.midroid.masarifi.MainActivity
import com.midroid.masarifi.R
import com.midroid.masarifi.app
import com.midroid.masarifi.data.Account
import com.midroid.masarifi.data.AccountGroup
import com.midroid.masarifi.data.AppLock
import com.midroid.masarifi.data.Recurring
import com.midroid.masarifi.data.Repeat
import com.midroid.masarifi.data.Txn
import com.midroid.masarifi.data.TxnType
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.YearMonth
import java.time.ZoneId

/** 2.9.0 screenshots → build/screenshots/v290: lock screen, receipt, split editor, calendar, forecast (Arabic). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xhdpi")
class V290ScreenshotTest {
    @get:Rule val rule = createEmptyComposeRule()

    @Test fun darkAr() = shots("ar", "dark")
    @Test fun lightAr() = shots("ar", "light")

    private lateinit var sc: ActivityScenario<MainActivity>
    private lateinit var act: MainActivity
    private val lang = "ar"

    private fun s(id: Int, vararg a: Any) = act.getString(id, *a)

    private fun shot(name: String, theme: String) {
        rule.waitForIdle(); Thread.sleep(350)
        sc.onActivity { a ->
            val decor = a.window.decorView
            val bmp = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
            decor.draw(android.graphics.Canvas(bmp))
            val dir = File("build/screenshots/v290").apply { mkdirs() }
            File(dir, "${name}_${lang}_$theme.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun back() { sc.onActivity { it.onBackPressedDispatcher.onBackPressed() }; rule.waitForIdle() }
    private fun click(text: String) = rule.onAllNodes(hasText(text, substring = true) and hasClickAction()).onFirst().performClick()

    /** A plain drawn receipt (no real store's artwork). */
    private fun receiptJpeg(f: File) {
        val bmp = Bitmap.createBitmap(600, 900, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp); c.drawColor(Color.WHITE)
        val p = Paint().apply { color = Color.DKGRAY; textSize = 34f; isAntiAlias = true }
        c.drawText("RECEIPT", 210f, 80f, p)
        p.textSize = 26f
        listOf("Rice 5kg      3.200", "Milk x2       1.100", "Detergent     2.450", "Tissues       0.950", "", "TOTAL         7.700").forEachIndexed { i, l -> c.drawText(l, 60f, 180f + i * 60, p) }
        f.parentFile?.mkdirs(); f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
    }

    private fun shots(lng: String, theme: String) {
        com.midroid.masarifi.ui.warranty.imageDecodeDispatcher = kotlinx.coroutines.Dispatchers.Main
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ctx.getSharedPreferences("masarifi_settings", Context.MODE_PRIVATE).edit().putString("language", lng).putString("theme", theme).commit()
        AppLock(ctx).enable("1234")
        sc = ActivityScenario.launch(MainActivity::class.java)
        sc.onActivity { act = it }
        val vm = androidx.lifecycle.ViewModelProvider(act)[AppViewModel::class.java]
        sc.onActivity { vm.updateSettings { it.copy(language = lng, theme = theme) } }
        var receiptUuid = ""
        runBlocking {
            val repo = act.app.repository
            repo.ensureDefaults()
            repo.txnDao.all().forEach { repo.txnDao.delete(it) }
            val cash = repo.accountDao.all().first { it.groupType == AccountGroup.CASH }
            repo.saveAccount(cash.copy(initialBalance = 50_000, hidden = false, deletedAt = null, includeInTotal = true))
            val bank = repo.saveAccount(Account(name = "بنك مسقط", groupType = AccountGroup.BANK, initialBalance = 200_000, sortOrder = 1))
            val cats = repo.categoryDao.all()
            fun cat(key: String) = cats.first { it.key == key }.id
            val ym = YearMonth.now(); val zone = ZoneId.systemDefault()
            fun millis(day: Int) = ym.atDay(minOf(day, ym.lengthOfMonth())).atStartOfDay(zone).plusHours(12).toInstant().toEpochMilli()
            repo.txnDao.insert(Txn(type = TxnType.INCOME, amount = 900_000, accountId = bank, note = "راتب", dateTime = millis(1)))
            repo.txnDao.insert(Txn(type = TxnType.EXPENSE, amount = 150_000, accountId = bank, categoryId = cat("rent"), dateTime = millis(1)))
            repo.txnDao.insert(Txn(type = TxnType.EXPENSE, amount = 12_000, accountId = bank, categoryId = cat("food"), note = "مطعم", dateTime = millis(3)))
            repo.txnDao.insert(Txn(type = TxnType.EXPENSE, amount = 8_000, accountId = bank, categoryId = cat("fuel"), dateTime = millis(6)))
            repo.txnDao.insert(Txn(type = TxnType.EXPENSE, amount = 25_000, accountId = bank, categoryId = cat("food"), dateTime = millis(9)))
            // a split purchase (grocery + household) with its receipt
            val g = com.midroid.masarifi.data.newUuid()
            val first = Txn(type = TxnType.EXPENSE, amount = 4_300, accountId = bank, categoryId = cat("grocery"), note = "سوبرماركت", dateTime = millis(12), splitGroup = g)
            repo.txnDao.insert(first)
            repo.txnDao.insert(first.copy(amount = 3_400, categoryId = cats.first { it.type == TxnType.EXPENSE && it.id != cat("grocery") && it.parentId == null && it.key != "food" }.id, uuid = com.midroid.masarifi.data.newUuid()))
            receiptUuid = first.uuid
            receiptJpeg(vm.receiptFile(first.uuid))
            // what the forecast looks ahead at: a repeating income and a repeating expense later this month
            val last = ym.lengthOfMonth()
            repo.recurringDao.insert(Recurring(type = TxnType.INCOME, amount = 120_000, accountId = bank, note = "إيجار محل", frequency = Repeat.MONTHLY, nextDate = millis(last)))
            repo.recurringDao.insert(Recurring(type = TxnType.EXPENSE, amount = 35_000, accountId = bank, categoryId = cat("fuel"), frequency = Repeat.MONTHLY, nextDate = millis(last)))
        }
        sc.onActivity { vm.receiptVersion.value++ }

        // 1) lock screen
        rule.waitUntil(8_000) { rule.onAllNodesWithTag("lock_screen").fetchSemanticsNodes().isNotEmpty() }
        shot("01_lock", theme)
        rule.onNode(hasSetTextAction()).performTextInput("1234")
        click(s(R.string.lock_unlock))
        rule.waitUntil(8_000) { rule.onAllNodesWithTag("lock_screen").fetchSemanticsNodes().isEmpty() }

        // 2) transaction with its receipt
        rule.waitUntil(8_000) { rule.onAllNodesWithText("سوبرماركت", substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithText("سوبرماركت", substring = true, useUnmergedTree = true)[1].performClick()
        rule.waitUntil(8_000) { rule.onAllNodesWithTag("receipt_thumb").fetchSemanticsNodes().isNotEmpty() }
        shot("02_receipt", theme)
        back()

        // 3) split editor on a new expense
        rule.onAllNodes((hasText(s(R.string.add)) or androidx.compose.ui.test.hasContentDescription(s(R.string.add))) and hasClickAction()).onFirst().performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodes(hasSetTextAction()).onFirst().performTextInput("12.500")
        click(s(R.string.split))
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("split_editor").fetchSemanticsNodes().isNotEmpty() }
        // categories for the two parts through the normal picker
        click(s(R.string.category)); rule.waitForIdle(); click("طعام")
        click(s(R.string.category)); rule.waitForIdle(); click("وقود")
        // first part was pre-filled with the whole amount: make it 8.000 and the second 4.500
        rule.onAllNodes(hasSetTextAction())[1].performTextClearance()
        rule.onAllNodes(hasSetTextAction())[1].performTextInput("8.000")
        rule.onAllNodes(hasSetTextAction())[2].performTextInput("4.500")
        shot("03_split", theme)
        back()

        // 4) month calendar (existing «تقويم» tab)
        click(s(R.string.tab_calendar_short))
        rule.waitForIdle()
        shot("04_calendar", theme)

        // 5) forecast card on the budget tab
        click(s(R.string.tab_stats))
        rule.onNode(hasText(s(R.string.budget)) and hasClickAction()).performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("budget_list").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("budget_list").performScrollToNode(hasText(s(R.string.forecast_hint)))
        shot("05_forecast", theme)
        sc.close()
    }
}
