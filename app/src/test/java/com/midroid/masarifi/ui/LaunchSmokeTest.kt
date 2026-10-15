package com.midroid.masarifi.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.midroid.masarifi.MainActivity
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * JVM (Robolectric) smoke test: launches the real MainActivity with Room, navigates all
 * bottom tabs, adds an expense and checks it is listed with OMR formatting.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class LaunchSmokeTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun launchNavigateAndAddExpense() {
        // Arabic UI by default
        rule.waitUntil(5_000) { rule.onAllNodesWithText("المعاملات").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("يومي").assertIsDisplayed()
        rule.onNodeWithText("التقويم").performClick()
        rule.onNodeWithText("شهري").performClick()
        rule.onAllNodesWithText("الإجمالي")[0].assertExists()

        rule.onNodeWithText("التحليل").performClick()
        rule.onNodeWithText("الإحصائيات").assertIsDisplayed()
        rule.onNodeWithText("الميزانية").performClick()
        rule.onNodeWithText("نصائح").performClick()

        rule.onNodeWithText("الحسابات").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("نقد").fetchSemanticsNodes().isNotEmpty() }

        rule.onNodeWithText("المزيد").performClick()
        rule.onNodeWithText("الاستيراد الذكي من الرسائل").assertIsDisplayed()

        // Add an expense
        rule.onNodeWithText("المعاملات").performClick()
        rule.onNodeWithContentDescription("إضافة").performClick()
        rule.onNodeWithText("حفظ").assertIsDisplayed()
        val fields = rule.onAllNodes(androidx.compose.ui.test.hasSetTextAction()).fetchSemanticsNodes()
        assertTrue("expected text fields", fields.size >= 2)
        rule.onAllNodes(androidx.compose.ui.test.hasSetTextAction())[0].performTextInput("4.3")
        rule.onAllNodes(androidx.compose.ui.test.hasSetTextAction())[1].performTextInput("لولو")
        rule.onNodeWithText("حفظ").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("لولو").fetchSemanticsNodes().isNotEmpty() }
        rule.waitUntil(5_000) { rule.onAllNodesWithText("4.300 ر.ع.", substring = true).fetchSemanticsNodes().isNotEmpty() }
    }
}
