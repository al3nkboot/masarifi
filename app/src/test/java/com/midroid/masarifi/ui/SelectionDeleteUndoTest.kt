package com.midroid.masarifi.ui

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.midroid.masarifi.MainActivity
import com.midroid.masarifi.app
import com.midroid.masarifi.data.Txn
import com.midroid.masarifi.data.TxnType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalTime

/** Long-press → multi-select → delete (one DB transaction) → snackbar undo restores rows exactly. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xhdpi")
class SelectionDeleteUndoTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun seed(): List<Txn> = runBlocking {
        val repo = rule.activity.app.repository
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { repo.database.clearAllTables() } // DB singleton is shared across tests in this JVM
        repo.ensureDefaults()
        val cash = repo.accountDao.all().first().id
        val food = repo.categoryDao.all().first { it.key == "food" }.id
        val today = LocalDate.now()
        fun at(h: Int) = Fmt.toMillis(today.atTime(LocalTime.of(h, 10)))
        listOf(
            Txn(type = TxnType.EXPENSE, amount = 1_200, accountId = cash, categoryId = food, note = "قهوة", dateTime = at(8)),
            Txn(type = TxnType.EXPENSE, amount = 3_500, accountId = cash, categoryId = food, note = "غداء", dateTime = at(13), smsHash = "mm:fp-1"),
            Txn(type = TxnType.EXPENSE, amount = 7_250, accountId = cash, categoryId = food, note = "LULU", dateTime = at(18), isAuto = true, smsHash = "sms-hash-1", merchant = "LULU"),
            Txn(type = TxnType.INCOME, amount = 50_000, accountId = cash, note = "مكافأة", dateTime = at(9)),
        ).forEach { repo.txnDao.insert(it) }
        repo.txnDao.all()
    }

    private fun dbRows() = runBlocking { rule.activity.app.repository.txnDao.all() }

    @Test fun selectDeleteUndo() {
        val original = seed()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("قهوة").fetchSemanticsNodes().isNotEmpty() }

        rule.onNodeWithText("قهوة").performTouchInput { longClick() }
        rule.onNodeWithText("1 محدد").assertExists()
        rule.onNodeWithText("غداء").performClick()
        rule.onNodeWithText("LULU").performClick()
        rule.onNodeWithText("3 محدد").assertExists()
        rule.onNodeWithText("قهوة").performClick() // toggle off
        rule.onNodeWithText("2 محدد").assertExists()

        rule.onNodeWithContentDescription("حذف").performClick()
        rule.onNodeWithText("حذف 2 عمليات؟").assertExists()
        rule.onNodeWithText("حذف").performClick()
        rule.waitUntil(5_000) { dbRows().size == 2 }
        rule.waitUntil(5_000) { rule.onAllNodesWithText("غداء").fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithText("تم حذف 2 عمليات").assertExists()

        rule.onNodeWithText("تراجع").performClick()
        rule.waitUntil(5_000) { dbRows().size == 4 }
        // exact restore: same ids, SMS hash and Money Manager fingerprint
        // ids, SMS hash and import fingerprint are identical; only the sync timestamp moves forward so the undo
        // also wins over a delete that may already have been synced (1.3.0)
        val restored = dbRows()
        assertEquals(original.map { it.copy(updatedAt = 0) }, restored.map { it.copy(updatedAt = 0) })
        assertEquals(original.map { it.uuid }, restored.map { it.uuid })
        rule.waitUntil(5_000) { rule.onAllNodesWithText("غداء").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun selectAllAndBackExitsSelection() {
        seed()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("قهوة").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("مكافأة").performTouchInput { longClick() }
        rule.onNodeWithText("تحديد الكل").performClick()
        rule.onNodeWithText("4 محدد").assertExists()
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        assertEquals(0, rule.onAllNodesWithText("4 محدد").fetchSemanticsNodes().size)
        assertEquals(2, rule.onAllNodesWithText("المعاملات").fetchSemanticsNodes().size) // normal title bar is back (title + bottom nav)
        assertEquals(4, dbRows().size)
    }
}
