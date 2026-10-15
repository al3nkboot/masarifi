package com.midroid.masarifi.ui

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.midroid.masarifi.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** 1.7.2 settings: consolidated backup section + «النسخ الاحتياطي والاستعادة» screen → build/screenshots-1.7.2 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xhdpi")
class Screenshots172Test {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val dir = File("build/screenshots-1.7.2").apply { mkdirs() }
    private fun shot(name: String) {
        rule.waitForIdle()
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(android.graphics.Canvas(bmp)) }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun back() { rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }; rule.waitForIdle() }

    @Test fun render() {
        rule.waitUntil(5_000) { rule.onAllNodesWithText("المزيد").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("المزيد").performClick()
        rule.onNodeWithText("استيراد وتصدير").performScrollTo()
        check(rule.onAllNodesWithText("إصلاح الفئات المفقودة").fetchSemanticsNodes().isEmpty()) // hidden: nothing to repair
        shot("01_settings_backup_section")
        rule.onNodeWithText("النسخ الاحتياطي والاستعادة").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("استعادة").fetchSemanticsNodes().isNotEmpty() }
        shot("02_backup_restore_screen")
        back()
        rule.onNodeWithText("استيراد وتصدير").performScrollTo().performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("تصدير", substring = true).fetchSemanticsNodes().isNotEmpty() }
        shot("03_import_export_screen")
    }
}
