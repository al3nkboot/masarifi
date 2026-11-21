package com.midroid.masarifi.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.midroid.masarifi.MainActivity
import com.midroid.masarifi.R
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
import java.time.YearMonth
import java.time.ZoneId

/** 2.8.0 screenshots → build/screenshots/v280: budget gauge + transactions header expanded/collapsed. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xhdpi")
class V280ScreenshotTest {
    @get:Rule val rule = createEmptyComposeRule()

    @Test fun darkAr() = shots("ar", "dark")
    @Test fun lightAr() = shots("ar", "light")
    @Test fun darkEn() = shots("en", "dark")

    private lateinit var sc: ActivityScenario<MainActivity>
    private lateinit var act: MainActivity

    private fun open(lang: String, theme: String) {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ctx.getSharedPreferences("masarifi_settings", Context.MODE_PRIVATE)
            .edit().putString("language", lang).putString("theme", theme).putBoolean("carryOver", true).commit()
        sc = ActivityScenario.launch(MainActivity::class.java)
        sc.onActivity { act = it }
        val vm = androidx.lifecycle.ViewModelProvider(act)[AppViewModel::class.java]
        sc.onActivity { vm.updateSettings { it.copy(language = lang, theme = theme, carryOver = true) } }
        runBlocking {
            val repo = act.app.repository
            repo.ensureDefaults()
            repo.txnDao.all().forEach { repo.txnDao.delete(it) }
            repo.accountDao.all().filter { it.groupType != AccountGroup.CASH }.forEach { repo.deleteAccount(it) }
            val cash = repo.accountDao.all().first { it.groupType == AccountGroup.CASH }
            repo.saveAccount(cash.copy(initialBalance = 50_000, hidden = false, deletedAt = null, includeInTotal = true))
            val bankId = repo.saveAccount(
                Account(
                    name = if (lang == "ar") "بنك مسقط" else "Bank Muscat",
                    groupType = AccountGroup.BANK,
                    initialBalance = 200_000,
                    sortOrder = 1,
                ),
            )

            val cats = repo.categoryDao.all()
            fun cat(key: String) = cats.first { it.key == key }.id
            val food = cat("food")
            val grocery = cat("grocery")
            val fuel = cat("fuel")
            val rent = cat("rent")
            repo.setBudget(0, 400_000)
            repo.setBudget(food, 60_000)
            repo.setBudget(grocery, 100_000)
            repo.setBudget(fuel, 30_000)
            repo.setBudget(rent, 150_000)

            val ym = YearMonth.now()
            val zone = ZoneId.systemDefault()
            fun millis(day: Int) = ym.atDay(minOf(day, ym.lengthOfMonth())).atStartOfDay(zone).toInstant().toEpochMilli()
            repo.txnDao.insert(Txn(type = TxnType.INCOME, amount = 900_000, accountId = bankId, note = if (lang == "ar") "راتب" else "Salary", dateTime = millis(1)))
            repo.txnDao.insert(Txn(type = TxnType.EXPENSE, amount = 150_000, accountId = bankId, categoryId = rent, merchant = if (lang == "ar") "إيجار" else "Rent", dateTime = millis(1)))
            repo.txnDao.insert(Txn(type = TxnType.EXPENSE, amount = 45_500, accountId = bankId, categoryId = grocery, merchant = "LuLu", dateTime = millis(2)))
            repo.txnDao.insert(Txn(type = TxnType.EXPENSE, amount = 12_000, accountId = bankId, categoryId = food, merchant = "Talabat", dateTime = millis(3)))
            repo.txnDao.insert(Txn(type = TxnType.EXPENSE, amount = 30_000, accountId = bankId, categoryId = grocery, merchant = "LuLu", dateTime = millis(4)))
            repo.txnDao.insert(Txn(type = TxnType.TRANSFER, amount = 20_000, accountId = bankId, toAccountId = cash.id, dateTime = millis(4)))
            repo.txnDao.insert(Txn(type = TxnType.EXPENSE, amount = 8_000, accountId = bankId, categoryId = fuel, merchant = "Shell", dateTime = millis(5)))
            repo.txnDao.insert(Txn(type = TxnType.EXPENSE, amount = 18_000, accountId = bankId, categoryId = food, merchant = "McDonald's", dateTime = millis(6)))
            repo.txnDao.insert(Txn(type = TxnType.EXPENSE, amount = 25_000, accountId = bankId, categoryId = food, merchant = "KFC", dateTime = millis(7)))
            repo.txnDao.insert(Txn(type = TxnType.EXPENSE, amount = 9_500, accountId = bankId, categoryId = fuel, merchant = "Shell", dateTime = millis(8)))
            repo.txnDao.insert(Txn(type = TxnType.EXPENSE, amount = 14_000, accountId = bankId, categoryId = grocery, merchant = "Carrefour", dateTime = millis(9)))
            repo.txnDao.insert(Txn(type = TxnType.EXPENSE, amount = 11_000, accountId = bankId, categoryId = food, merchant = "Talabat", dateTime = millis(10)))
            repo.txnDao.insert(Txn(type = TxnType.EXPENSE, amount = 7_500, accountId = bankId, categoryId = fuel, merchant = "Oman Oil", dateTime = millis(11)))
            repo.txnDao.insert(Txn(type = TxnType.EXPENSE, amount = 16_000, accountId = bankId, categoryId = grocery, merchant = "LuLu", dateTime = millis(12)))
        }
        rule.waitUntil(8_000) { rule.onAllNodesWithText(s(R.string.tab_accounts)).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun s(id: Int, vararg a: Any) = act.getString(id, *a)

    private fun shot(name: String, lang: String, theme: String) {
        rule.waitForIdle(); Thread.sleep(350)
        sc.onActivity { a ->
            val decor = a.window.decorView
            val bmp = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bmp)
            decor.draw(canvas)
            val dir = File("build/screenshots/v280").apply { mkdirs() }
            File(dir, "${name}_${lang}_$theme.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun clickTab(label: String) {
        rule.onNode(hasText(label) and hasClickAction()).performClick()
    }

    private fun shots(lang: String, theme: String) {
        open(lang, theme)
        rule.waitUntil(8_000) { rule.onAllNodesWithTag("home_overview").fetchSemanticsNodes().isNotEmpty() }
        shot("01_trans_expanded", lang, theme)

        rule.waitUntil(5_000) { rule.onAllNodesWithTag("trans_daily_list").fetchSemanticsNodes().isNotEmpty() }
        repeat(3) {
            rule.onNodeWithTag("trans_daily_list").performTouchInput { swipeUp() }
            rule.waitForIdle()
        }
        shot("02_trans_collapsed", lang, theme)

        clickTab(s(R.string.tab_stats))
        rule.onNode(hasText(s(R.string.budget)) and hasClickAction()).performClick()
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText(s(R.string.budget_left_to_spend)).fetchSemanticsNodes().isNotEmpty() ||
                rule.onAllNodesWithText(s(R.string.budget_over)).fetchSemanticsNodes().isNotEmpty()
        }
        shot("03_budget", lang, theme)
    }
}
