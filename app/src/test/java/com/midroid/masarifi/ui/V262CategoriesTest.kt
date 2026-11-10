package com.midroid.masarifi.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.geometry.Offset
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.midroid.masarifi.MainActivity
import com.midroid.masarifi.R
import com.midroid.masarifi.app
import com.midroid.masarifi.data.Category
import com.midroid.masarifi.data.TxnType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** 2.6.2 Money Manager-style categories: screenshots → build/screenshots/v262, plus drag / a11y reorder and delete. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xhdpi")
class V262CategoriesTest {
    @get:Rule val rule = createEmptyComposeRule()

    @Test fun darkAr() = shots("ar", "dark")
    @Test fun lightAr() = shots("ar", "light")
    @Test fun darkEn() = shots("en", "dark")
    @Test fun dragReordersAndA11yMoves() = interact()
    @Test fun dragWhenScrolled() = scrolledDrag()

    private lateinit var sc: ActivityScenario<MainActivity>
    private lateinit var act: MainActivity

    private fun open(lang: String, theme: String) {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ctx.getSharedPreferences("masarifi_settings", Context.MODE_PRIVATE).edit().putString("language", lang).putString("theme", theme).commit()
        sc = ActivityScenario.launch(MainActivity::class.java)
        sc.onActivity { act = it }
        val vm = androidx.lifecycle.ViewModelProvider(act)[AppViewModel::class.java]
        sc.onActivity { vm.updateSettings { it.copy(language = lang, theme = theme) } }
        runBlocking {
            val repo = act.app.repository
            repo.ensureDefaults()
            val all = repo.categoryDao.all()
            val food = all.first { it.key == "food" }
            if (all.none { it.parentId == food.id }) {
                for ((ar, en, e) in listOf(Triple("وجبات سريعة", "Fast food", "🍕"), Triple("قهوة", "Coffee", "☕"), Triple("مطاعم", "Restaurants", "🍽️"), Triple("بقالة", "Groceries", "🛒"))) {
                    repo.saveCategory(Category(type = TxnType.EXPENSE, nameAr = ar, nameEn = en, emoji = e, parentId = food.id))
                }
                val tr = all.first { it.key == "car" }
                for ((ar, en, e) in listOf(Triple("وقود", "Fuel", "⛽"), Triple("تاكسي", "Taxi", "🚕"))) {
                    repo.saveCategory(Category(type = TxnType.EXPENSE, nameAr = ar, nameEn = en, emoji = e, parentId = tr.id))
                }
            }
        }
        rule.waitUntil(8_000) { rule.onAllNodesWithText(s(R.string.tab_more)).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText(s(R.string.tab_more)).performClick()
        rule.waitForIdle(); Thread.sleep(200)
        rule.onNodeWithText(s(R.string.categories)).performScrollTo().performClick()
        rule.waitUntil(8_000) { rule.onAllNodesWithContentDescription(s(R.string.drag_to_reorder)).fetchSemanticsNodes().isNotEmpty() }
        rule.waitForIdle(); Thread.sleep(300)
    }

    private fun s(id: Int, vararg a: Any) = act.getString(id, *a)

    private fun shot(name: String, lang: String, theme: String) {
        rule.waitForIdle(); Thread.sleep(250)
        sc.onActivity { a ->
            val v = a.window.decorView
            val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
            v.draw(android.graphics.Canvas(bmp))
            val dir = File("build/screenshots/v262").apply { mkdirs() }
            File(dir, "${name}_${lang}_$theme.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun foodName(lang: String) = if (lang == "ar") "وجبات سريعة" else "Fast food"

    private fun shots(lang: String, theme: String) {
        open(lang, theme)
        shot("01_customize", lang, theme)
        // tap the Food row → its subcategories open inline
        val food = runBlocking { act.app.repository.categoryDao.all().first { it.key == "food" } }
        rule.onAllNodesWithText(food.name(lang == "ar"), substring = true)[0].performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText(s(R.string.add_subcategory)).fetchSemanticsNodes().isNotEmpty() }
        shot("02_expanded", lang, theme)
        // red minus on a subcategory → confirmation
        rule.onNodeWithContentDescription(s(R.string.delete_category_cd, foodName(lang))).performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText(s(R.string.confirm_delete_subcategory)).fetchSemanticsNodes().isNotEmpty() }
        shot("03_delete_confirm", lang, theme)
        sc.close()
    }

    private fun parentOrder() = runBlocking {
        act.app.repository.categoryDao.all().filter { it.type == TxnType.EXPENSE && it.parentId == null }.map { it.id }
    }

    private fun interact() {
        open("ar", "dark")
        val before = parentOrder()
        // 1) drag the first handle down past two rows
        val handle = rule.onAllNodesWithContentDescription(s(R.string.drag_to_reorder), useUnmergedTree = true)[0]
        handle.performTouchInput {
            down(center)
            repeat(30) { moveBy(Offset(0f, 12f)) }
            up()
        }
        rule.waitForIdle()
        rule.waitUntil(5_000) { parentOrder() != before }
        val afterDrag = parentOrder()
        assertNotEquals(before, afterDrag)
        assertEquals(before.toSet(), afterDrag.toSet())
        assertNotEquals(before[0], afterDrag[0])
        assertEquals(before[1], afterDrag[0]) // the 2nd category is now first
        assertEquals(before[0], afterDrag[3]) // dragged ~3 rows (120dp rows, 360px drag) → 4th place
        // 2) accessibility «تحريك للأسفل» on the (new) first row's handle
        rule.waitForIdle(); Thread.sleep(300)
        // the row (merged node) of the category that is now first carries the handle's custom actions
        val firstCat = runBlocking { act.app.repository.categoryDao.all().single { it.id == afterDrag[0] } }
        shot("04_after_drag", "ar", "dark")
        val first = rule.onAllNodes(hasText(firstCat.nameAr, substring = true) or hasText(firstCat.nameEn, substring = true))[0]
        val actions = first.fetchSemanticsNode().config[SemanticsActions.CustomActions]
        actions.first { it.label == s(R.string.move_down) }.action()
        rule.waitUntil(5_000) { parentOrder() != afterDrag }
        val afterA11y = parentOrder()
        assertEquals(afterDrag[1], afterA11y[0]); assertEquals(afterDrag[0], afterA11y[1])
        // 3) order survives leaving and re-opening the screen (persisted, not just local state)
        sc.close()
        open("ar", "dark")
        assertEquals(afterA11y, parentOrder())
        sc.close()
    }

    /** Header scrolled away: the dragged row is the list's first visible item (anchor case). */
    private fun scrolledDrag() {
        open("ar", "dark")
        rule.onNodeWithTag("categories_list").performTouchInput { swipeUp(startY = centerY + 200f, endY = centerY - 200f + 40f) }
        rule.waitForIdle(); Thread.sleep(300)
        val before = parentOrder()
        val handles = rule.onAllNodesWithContentDescription(s(R.string.drag_to_reorder), useUnmergedTree = true).fetchSemanticsNodes()
        val topNode = handles.filter { it.boundsInRoot.top >= 0f }.minBy { it.boundsInRoot.top }
        val idx = handles.indexOf(topNode)
        val draggedIndex = handles.count { it.boundsInRoot.top < topNode.boundsInRoot.top }
        shot("05a_scrolled_before", "ar", "dark")
        rule.onAllNodesWithContentDescription(s(R.string.drag_to_reorder), useUnmergedTree = true)[idx].performTouchInput {
            down(center)
            repeat(14) { moveBy(Offset(0f, 12f)); }
            up()
        }
        rule.waitForIdle()
        rule.waitUntil(5_000) { parentOrder() != before }
        val after = parentOrder()
        assertEquals(before.toSet(), after.toSet())
        shot("05b_scrolled_after_drag", "ar", "dark")
        sc.close()
    }
}
