package com.midroid.masarifi.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp

import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.midroid.masarifi.MainActivity
import com.midroid.masarifi.R
import com.midroid.masarifi.app
import com.midroid.masarifi.bills.Bill
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

/** 2.3.0 redesign: dark theme screenshots in Arabic and English → build/screenshots/v23. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xhdpi")
class V231ScreenshotTest {
    @get:Rule val rule = createEmptyComposeRule()

    @Test fun arabic() = run("ar")

    private fun run(lang: String) {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ctx.getSharedPreferences("masarifi_settings", Context.MODE_PRIVATE).edit().putString("language", lang).putString("theme", "dark").commit()
        val sc = ActivityScenario.launch(MainActivity::class.java)
        lateinit var act: MainActivity
        sc.onActivity { act = it }
        val app = act.app
        val vm = androidx.lifecycle.ViewModelProvider(act)[AppViewModel::class.java]
        sc.onActivity { vm.updateSettings { it.copy(language = lang, theme = "dark") } }
        runBlocking {
            val repo = app.repository
            repo.ensureDefaults()
            val cats = repo.categoryDao.all()
            val cash = repo.accountDao.all().first().id
            fun cat(key: String) = cats.first { it.key == key }.id
            val today = LocalDate.now()
            fun at(d: Int, h: Int) = Fmt.toMillis(today.minusDays(d.toLong()).atTime(LocalTime.of(h, 15)))
            repo.saveTxn(Txn(type = TxnType.EXPENSE, amount = 4300, accountId = cash, categoryId = cat("grocery"), note = "LuLu", dateTime = at(0, 10)))
            repo.saveTxn(Txn(type = TxnType.EXPENSE, amount = 1500, accountId = cash, categoryId = cat("food"), note = "Karak", dateTime = at(0, 8)))
            repo.saveTxn(Txn(type = TxnType.EXPENSE, amount = 7200, accountId = cash, categoryId = cat("fuel"), note = "Shell", dateTime = at(1, 18), isAuto = true))
            repo.saveTxn(Txn(type = TxnType.INCOME, amount = 850000, accountId = cash, categoryId = cat("salary"), note = "Salary", dateTime = at(2, 9)))
            repo.saveTxn(Txn(type = TxnType.EXPENSE, amount = 25000, accountId = cash, categoryId = cat("internet"), note = "Omantel", dateTime = at(2, 12)))
            repo.saveTxn(Txn(type = TxnType.EXPENSE, amount = 12500, accountId = cash, categoryId = cat("food"), note = "Dinner", dateTime = at(3, 20)))
            repo.saveTxn(Txn(type = TxnType.EXPENSE, amount = 3000, accountId = cash, categoryId = cat("grocery"), note = "Bakery", dateTime = at(4, 7)))
            val names = listOf("water", "Electricity", "Talabat", "X Premium", "Anghami", "SuperGrok")
            val ids = names.mapIndexed { i, n -> repo.categoryDao.insert(com.midroid.masarifi.data.Category(type = TxnType.EXPENSE, nameAr = n, nameEn = n, emoji = "📦", sortOrder = 60 + i)) }
            val amounts = listOf(9500L, 18750L, 6400L, 3200L, 1800L, 11500L)
            names.forEachIndexed { i, n ->
                repo.saveTxn(Txn(type = TxnType.EXPENSE, amount = amounts[i], accountId = cash, categoryId = ids[i], note = n, dateTime = at(i % 4, 14)))
                repo.bills.save(Bill(name = n, amount = amounts[i], categoryId = ids[i], anchorDay = today.plusDays(i.toLong() - 2).dayOfMonth, nextDue = today.plusDays(i.toLong() - 2).toEpochDay()))
            }
            repo.bills.save(Bill(name = "Nama Electricity", amount = 18750, anchorDay = today.plusDays(12).dayOfMonth, nextDue = today.plusDays(12).toEpochDay()))
            repo.bills.save(Bill(name = "Netflix", amount = 4200, anchorDay = today.plusDays(20).dayOfMonth, nextDue = today.plusDays(20).toEpochDay()))
        }
        rule.waitUntil(8_000) { rule.onAllNodesWithText("LuLu").fetchSemanticsNodes().isNotEmpty() }
        fun shot(name: String) {
            rule.waitForIdle()
            sc.onActivity { a ->
                val v = a.window.decorView
                val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
                v.draw(android.graphics.Canvas(bmp))
                val dir = File("build/screenshots/v231").apply { mkdirs() }
                File(dir, "${name}_$lang.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
        fun s(id: Int) = act.getString(id)
        rule.onNodeWithText(s(R.string.tab_stats)).performClick(); shot("01_stats")
        rule.onNodeWithText(s(R.string.tab_bills)).performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("water").fetchSemanticsNodes().isNotEmpty() }
        shot("02_bills_list_top")
        repeat(6) { rule.onAllNodes(androidx.compose.ui.test.hasScrollAction())[0].performTouchInput { swipeUp() } }
        shot("03_bills_list_end")
        rule.onNodeWithText(s(R.string.bill_tab_calendar)).performClick()
        repeat(6) { rule.onAllNodes(androidx.compose.ui.test.hasScrollAction())[0].performTouchInput { swipeUp() } }
        shot("04_bills_calendar_end")
        rule.onNodeWithText(s(R.string.bill_tab_history)).performClick(); shot("05_bills_history")
        sc.close()
    }
}
