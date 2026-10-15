package com.midroid.masarifi.ui

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.midroid.masarifi.MainActivity
import com.midroid.masarifi.app
import com.midroid.masarifi.data.Repeat
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Bug fix 1.3.0: «التكرار» must be visible (with the current value) when reopening a saved transaction. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xhdpi")
class RepeatFieldEditTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private val repo by lazy { rule.activity.app.repository } // resolved on the test (main) thread
    private fun rules() = runBlocking { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { repo.database.backupDao().recurring() } }
    private fun txns() = runBlocking { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { repo.txnDao.all() } }

    @Test fun monthlyRepeatIsShownOnReopen_andNoneStopsTheRule() {
        repo.hashCode()
        runBlocking { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { repo.database.clearAllTables() }; repo.ensureDefaults() }

        // new expense with a monthly repeat
        rule.onNodeWithContentDescription("إضافة").performClick()
        rule.onAllNodes(hasSetTextAction())[0].performTextInput("150")
        rule.onAllNodes(hasSetTextAction())[1].performTextInput("إيجار الشقة")
        rule.onNodeWithText("التكرار").assertExists()
        rule.onNodeWithText("بدون تكرار").performClick()
        rule.onNodeWithText("كل شهر").performClick()
        rule.onNodeWithText("حفظ").performClick()
        rule.waitUntil(5_000) { rules().size == 1 }
        assertEquals(Repeat.MONTHLY, rules().single().frequency)

        // reopen: the field is there with the current value
        rule.waitUntil(5_000) { rule.onAllNodesWithText("إيجار الشقة").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("إيجار الشقة").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("كل شهر").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("التكرار").assertExists()
        rule.onNodeWithText("كل شهر").assertExists()

        // change to none → save → no repeat rule left
        rule.onNodeWithText("كل شهر").performClick()
        rule.onNodeWithText("بدون تكرار").performClick()
        rule.onNodeWithText("حفظ").performClick()
        rule.waitUntil(5_000) { rules().isEmpty() }
        assertNull(txns().single().recurringId)

        // reopen once more: still visible, now «بدون تكرار»
        rule.waitUntil(5_000) { rule.onAllNodesWithText("إيجار الشقة").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("إيجار الشقة").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("بدون تكرار").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("التكرار").assertExists()
    }
}
