package com.midroid.masarifi.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** 2.6.3 accounts page + carry-over: screenshots → build/screenshots/v263. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xhdpi")
class V263AccountsTest {
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
            repo.saveAccount(Account(name = if (lang == "ar") "وديعة" else "Deposit", groupType = AccountGroup.SAVINGS, initialBalance = 100_000, includeInTotal = false, hidden = true, sortOrder = 3))
            val ym = YearMonth.now()
            val zone = ZoneId.systemDefault()
            fun millis(month: YearMonth, day: Int) = month.atDay(day).atStartOfDay(zone).toInstant().toEpochMilli()
            val prev = ym.minusMonths(1)
            repo.txnDao.insert(Txn(type = TxnType.INCOME, amount = 400_000, accountId = bankId, note = if (lang == "ar") "راتب" else "Salary", dateTime = millis(prev, 3)))
            repo.txnDao.insert(Txn(type = TxnType.EXPENSE, amount = 120_000, accountId = bankId, note = if (lang == "ar") "إيجار" else "Rent", dateTime = millis(prev, 8)))
            repo.txnDao.insert(Txn(type = TxnType.EXPENSE, amount = 15_000, accountId = cash.id, note = "LuLu", dateTime = millis(ym, minOf(5, ym.lengthOfMonth()))))
            repo.txnDao.insert(Txn(type = TxnType.TRANSFER, amount = 20_000, accountId = bankId, toAccountId = cash.id, note = "", dateTime = millis(ym, minOf(6, ym.lengthOfMonth()))))
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
            val dir = File("build/screenshots/v263").apply { mkdirs() }
            File(dir, "${name}_${lang}_$theme.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun clickTab(label: String) {
        rule.onNode(hasText(label) and hasClickAction()).performClick()
    }

    private fun shots(lang: String, theme: String) {
        open(lang, theme)
        clickTab(s(R.string.tab_accounts))
        rule.waitUntil(8_000) { rule.onAllNodesWithText(s(R.string.assets)).fetchSemanticsNodes().isNotEmpty() }
        rule.waitForIdle(); Thread.sleep(200)
        shot("01_accounts", lang, theme)

        rule.onNodeWithTag("accounts_edit_mode").performClick()
        rule.waitUntil(8_000) { rule.onAllNodesWithContentDescription(s(R.string.drag_to_reorder)).fetchSemanticsNodes().isNotEmpty() }
        shot("02_edit_mode", lang, theme)

        val bankName = if (lang == "ar") "بنك مسقط" else "Bank Muscat"
        rule.onNodeWithContentDescription(s(R.string.delete_account_cd, bankName)).performClick()
        rule.waitUntil(8_000) { rule.onAllNodesWithText(s(R.string.move_txns_to)).fetchSemanticsNodes().isNotEmpty() }
        shot("03_delete_options", lang, theme)
        rule.onNodeWithText(s(R.string.cancel)).performClick()
        rule.waitForIdle()

        // leave edit mode and open account detail
        rule.onNodeWithTag("accounts_edit_mode").performClick()
        rule.onNodeWithText(bankName).performClick()
        rule.waitUntil(8_000) { rule.onAllNodesWithText(s(R.string.current_balance)).fetchSemanticsNodes().isNotEmpty() }
        shot("04_account_detail", lang, theme)
        sc.close()

        // carry-over on the Transactions tab (new activity so the month is "now")
        open(lang, theme)
        clickTab(s(R.string.tab_trans))
        rule.waitUntil(8_000) { rule.onAllNodesWithText(s(R.string.carry_over_line)).fetchSemanticsNodes().isNotEmpty() }
        shot("05_carry_over", lang, theme)
        sc.close()
    }
}
