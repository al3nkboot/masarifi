package com.midroid.masarifi.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.midroid.masarifi.MainActivity
import com.midroid.masarifi.R
import com.midroid.masarifi.app
import com.midroid.masarifi.data.Account
import com.midroid.masarifi.data.AccountGroup
import com.midroid.masarifi.data.Debt
import com.midroid.masarifi.data.DebtDirection
import com.midroid.masarifi.data.DebtSettlement
import com.midroid.masarifi.data.GoalContribution
import com.midroid.masarifi.data.SavingsGoal
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
import java.time.YearMonth
import java.time.ZoneId

/** 2.7.0 screenshots → build/screenshots/v270. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xhdpi")
class V270ScreenshotTest {
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
            // Reset so each theme/lang shot has a clean, language-correct fixture
            // even when tests share one process and the same Room database.
            repo.txnDao.all().forEach { repo.txnDao.delete(it) }
            repo.accountDao.all().filter { it.groupType != AccountGroup.CASH }.forEach { repo.deleteAccount(it) }
            val cash = repo.accountDao.all().first { it.groupType == AccountGroup.CASH }
            repo.saveAccount(cash.copy(initialBalance = 50_000, hidden = false, deletedAt = null, includeInTotal = true))
            val bankId = repo.saveAccount(Account(name = if (lang == "ar") "بنك مسقط" else "Bank Muscat", groupType = AccountGroup.BANK, initialBalance = 200_000, sortOrder = 1))
            repo.saveAccount(Account(name = if (lang == "ar") "بطاقة مدى" else "Debit card", groupType = AccountGroup.CARD, cardLast4 = "0099", sortOrder = 2))
            repo.saveAccount(Account(name = if (lang == "ar") "محفظة أسهم" else "Stocks", groupType = AccountGroup.INVESTMENT, initialBalance = 300_000, sortOrder = 3))
            repo.saveAccount(Account(name = if (lang == "ar") "قرض السيارة" else "Car loan", groupType = AccountGroup.LOAN, initialBalance = -150_000, sortOrder = 4))

            val ym = YearMonth.now()
            val zone = ZoneId.systemDefault()
            fun millis(day: Int) = ym.atDay(minOf(day, ym.lengthOfMonth())).atStartOfDay(zone).toInstant().toEpochMilli()
            repo.txnDao.insert(Txn(type = TxnType.INCOME, amount = 900_000, accountId = bankId, note = if (lang == "ar") "راتب" else "Salary", dateTime = millis(1)))
            repo.txnDao.insert(Txn(type = TxnType.EXPENSE, amount = 45_500, accountId = bankId, merchant = "LuLu", note = "LuLu", dateTime = millis(2)))
            repo.txnDao.insert(Txn(type = TxnType.EXPENSE, amount = 12_000, accountId = bankId, merchant = "Talabat", note = "Talabat", dateTime = millis(3)))
            repo.txnDao.insert(Txn(type = TxnType.EXPENSE, amount = 30_000, accountId = bankId, merchant = "LuLu", note = "LuLu", dateTime = millis(4)))
            repo.txnDao.insert(Txn(type = TxnType.TRANSFER, amount = 20_000, accountId = bankId, toAccountId = cash.id, dateTime = millis(4)))
            repo.txnDao.insert(Txn(type = TxnType.EXPENSE, amount = 8_000, accountId = bankId, merchant = "Shell", note = "Shell", dateTime = millis(5)))

            val gd = act.app.repository.database.goalDao()
            gd.allGoals().forEach { gd.deleteContributionsOf(it.id); gd.deleteGoal(it.id) }
            val today = LocalDate.now().toEpochDay()
            val summerId = gd.insertGoal(SavingsGoal(name = if (lang == "ar") "سفرة الصيف" else "Summer trip", target = 1_200_000, targetDate = LocalDate.now().plusMonths(6).toEpochDay()))
            gd.insertContribution(GoalContribution(goalId = summerId, amount = 300_000, date = today))
            gd.insertContribution(GoalContribution(goalId = summerId, amount = 150_000, date = today))
            val emergencyId = gd.insertGoal(SavingsGoal(name = if (lang == "ar") "صندوق الطوارئ" else "Emergency fund", target = 3_000_000))
            gd.insertContribution(GoalContribution(goalId = emergencyId, amount = 600_000, date = today))
            val laptopId = gd.insertGoal(SavingsGoal(name = if (lang == "ar") "لابتوب جديد" else "New laptop", target = 400_000))
            gd.insertContribution(GoalContribution(goalId = laptopId, amount = 400_000, date = today))

            val dd = act.app.repository.database.debtDao()
            dd.allDebts().forEach { dd.deleteSettlementsOf(it.id); dd.deleteDebt(it.id) }
            val now = System.currentTimeMillis()
            val ahmedId = dd.insertDebt(Debt(person = if (lang == "ar") "أحمد" else "Ahmed", direction = DebtDirection.LENT, amount = 50_000, dateTime = now, note = if (lang == "ar") "عشاء" else "Dinner"))
            dd.insertSettlement(DebtSettlement(debtId = ahmedId, amount = 20_000, dateTime = now))
            dd.insertDebt(Debt(person = if (lang == "ar") "سالم" else "Salem", direction = DebtDirection.OWE, amount = 35_000, dateTime = now))
            dd.insertDebt(Debt(person = if (lang == "ar") "خالد" else "Khalid", direction = DebtDirection.LENT, amount = 15_000, dateTime = now, note = if (lang == "ar") "تقسيم فاتورة" else "Bill split"))
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
            // AlertDialogs live in a separate window, so compositing them is required.
            runCatching {
                val wmg = Class.forName("android.view.WindowManagerGlobal")
                val inst = wmg.getMethod("getInstance").invoke(null)
                @Suppress("UNCHECKED_CAST")
                val views = wmg.getDeclaredField("mViews").apply { isAccessible = true }.get(inst) as List<android.view.View>
                val extra = views.filter { it !== decor && it.width > 0 && it.height > 0 }
                extra.forEach { v ->
                    if (v.width < decor.width || v.height < decor.height) {
                        canvas.drawColor(0x99000000.toInt())
                        canvas.save()
                        canvas.translate((decor.width - v.width) / 2f, (decor.height - v.height) / 2f)
                        v.draw(canvas)
                        canvas.restore()
                    } else {
                        v.draw(canvas)
                    }
                }
            }
            val dir = File("build/screenshots/v270").apply { mkdirs() }
            File(dir, "${name}_${lang}_$theme.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun clickTab(label: String) {
        rule.onNode(hasText(label) and hasClickAction()).performClick()
    }

    private fun shots(lang: String, theme: String) {
        open(lang, theme)
        rule.waitUntil(8_000) { rule.onAllNodesWithTag("home_overview").fetchSemanticsNodes().isNotEmpty() }
        shot("01_home_networth", lang, theme)

        rule.onNode(hasText(s(R.string.expense)) and hasClickAction() and hasAnyAncestor(hasTestTag("type_filter_chips"))).performClick()
        shot("02_trans_filter_expense", lang, theme)
        rule.onNode(hasText(s(R.string.filter_all)) and hasClickAction() and hasAnyAncestor(hasTestTag("type_filter_chips"))).performClick()

        clickTab(s(R.string.tab_more))
        rule.onNodeWithText(s(R.string.goals)).performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("goal_card").fetchSemanticsNodes().isNotEmpty() }
        shot("03_goals", lang, theme)
        sc.onActivity { it.onBackPressedDispatcher.onBackPressed() }

        rule.onNodeWithText(s(R.string.debts)).performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("debt_row").fetchSemanticsNodes().isNotEmpty() }
        shot("04_debts", lang, theme)
        sc.onActivity { it.onBackPressedDispatcher.onBackPressed() }

        clickTab(s(R.string.tab_accounts))
        shot("05_accounts_networth", lang, theme)

        clickTab(s(R.string.tab_stats))
        rule.onNodeWithText(s(R.string.sum_title)).performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("period_chips").fetchSemanticsNodes().isNotEmpty() }
        shot("06_summary_periods", lang, theme)
        rule.onNodeWithTag("summaryList").performScrollToKey("merchants")
        shot("07_summary_merchants", lang, theme)
    }
}
