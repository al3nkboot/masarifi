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
import com.midroid.masarifi.data.Category
import com.midroid.masarifi.data.TxnType
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** 2.6.1 category hierarchy shots → build/screenshots/v261. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xhdpi")
class V261ScreenshotTest {
    @get:Rule val rule = createEmptyComposeRule()

    @Test fun darkAr() = run("ar", "dark")
    @Test fun lightAr() = run("ar", "light")

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
            val food = repo.categoryDao.all().first { it.key == "food" }
            if (repo.categoryDao.all().none { it.parentId == food.id }) {
                repo.saveCategory(Category(type = TxnType.EXPENSE, nameAr = "وجبات سريعة", nameEn = "Fast food", emoji = "🍕", parentId = food.id, sortOrder = 1))
                repo.saveCategory(Category(type = TxnType.EXPENSE, nameAr = "قهوة", nameEn = "Coffee", emoji = "☕", parentId = food.id, sortOrder = 2))
            }
            val electronics = repo.categoryDao.all().first { it.key == "electronics" }
            if (repo.categoryDao.all().none { it.parentId == electronics.id }) {
                repo.saveCategory(Category(type = TxnType.EXPENSE, nameAr = "سماعة", nameEn = "Headset", emoji = "🎧", parentId = electronics.id, sortOrder = 1))
            }
        }
        fun s(id: Int) = act.getString(id)
        rule.waitUntil(8_000) { rule.onAllNodesWithText(s(R.string.tab_more)).fetchSemanticsNodes().isNotEmpty() }
        fun shot(name: String) {
            rule.waitForIdle()
            sc.onActivity { a ->
                val v = a.window.decorView
                val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
                v.draw(android.graphics.Canvas(bmp))
                val dir = File("build/screenshots/v261").apply { mkdirs() }
                File(dir, "${name}_${lang}_$theme.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
        rule.onNodeWithText(s(R.string.tab_more)).performClick()
        rule.waitForIdle(); Thread.sleep(200)
        rule.onNodeWithText(s(R.string.categories)).performScrollTo().performClick()
        rule.waitUntil(8_000) { rule.onAllNodesWithText("وجبات سريعة", substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.waitForIdle(); Thread.sleep(300); shot("01_categories_subcats")
        sc.close()
    }
}
