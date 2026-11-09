package com.midroid.masarifi.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.midroid.masarifi.MainActivity
import com.midroid.masarifi.R
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

/** 2.6.0 activity shots → build/screenshots/v260. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xhdpi")
class V260ScreenshotTest {
    @get:Rule val rule = createEmptyComposeRule()

    @Test fun dark() = run("ar", "dark")

    private fun run(lang: String, theme: String) {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ctx.getSharedPreferences("masarifi_settings", Context.MODE_PRIVATE).edit().putString("language", lang).putString("theme", theme).commit()
        val sc = ActivityScenario.launch(MainActivity::class.java)
        lateinit var act: MainActivity
        sc.onActivity { act = it }
        val app = act.app
        val vm = androidx.lifecycle.ViewModelProvider(act)[AppViewModel::class.java]
        sc.onActivity { vm.updateSettings { it.copy(language = lang, theme = theme) } }
        runBlocking {
            val repo = app.repository
            repo.ensureDefaults()
            if (repo.categoryDao.all().any { it.nameEn == "water" }) return@runBlocking
            val cats = repo.categoryDao.all()
            val cash = repo.accountDao.all().first().id
            fun cat(key: String) = cats.first { it.key == key }.id
            val today = LocalDate.now()
            fun at(d: Int, h: Int) = Fmt.toMillis(today.minusDays(d.toLong()).atTime(LocalTime.of(h, 15)))
            repo.saveTxn(Txn(type = TxnType.EXPENSE, amount = 4300, accountId = cash, categoryId = cat("grocery"), note = "LuLu", dateTime = at(0, 10)))
            repo.saveTxn(Txn(type = TxnType.INCOME, amount = 850000, accountId = cash, categoryId = cat("salary"), note = "Salary", dateTime = at(2, 9)))
        }
        rule.waitUntil(8_000) { rule.onAllNodesWithText("LuLu").fetchSemanticsNodes().isNotEmpty() }
        fun shot(name: String) {
            rule.waitForIdle()
            sc.onActivity { a ->
                val v = a.window.decorView
                val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
                v.draw(android.graphics.Canvas(bmp))
                val dir = File("build/screenshots/v260").apply { mkdirs() }
                File(dir, "${name}_${lang}_$theme.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
        fun s(id: Int) = act.getString(id)
        rule.onNodeWithText(s(R.string.tab_more)).performClick()
        rule.waitForIdle(); Thread.sleep(200); shot("01_more")
        rule.onNodeWithText(s(R.string.check_updates)).performScrollTo()
        rule.waitForIdle(); Thread.sleep(200); shot("04_more_about")
        sc.close()
    }
}
