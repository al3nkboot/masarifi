package com.midroid.masarifi.ui

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.midroid.masarifi.MainActivity
import com.midroid.masarifi.app
import com.midroid.masarifi.data.Repeat
import com.midroid.masarifi.data.Txn
import com.midroid.masarifi.data.TxnType
import com.midroid.masarifi.data.sync.DeviceInfo
import com.midroid.masarifi.data.sync.SyncLogEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.LocalTime

/** 1.3.0 screenshots (synthetic test data only) → build/screenshots-1.3 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xhdpi")
class Screenshots13Test {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val dir = File("build/screenshots-1.3").apply { mkdirs() }

    private fun shot(name: String) {
        rule.waitForIdle()
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(android.graphics.Canvas(bmp)) }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun shotWithDialog(name: String) {
        rule.waitForIdle()
        val decor = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
        val wmg = Class.forName("android.view.WindowManagerGlobal")
        val inst = wmg.getMethod("getInstance").invoke(null)
        @Suppress("UNCHECKED_CAST")
        val views = wmg.getDeclaredField("mViews").apply { isAccessible = true }.get(inst) as List<android.view.View>
        val top = views.last()
        rule.runOnUiThread {
            val c = android.graphics.Canvas(bmp)
            decor.draw(c)
            c.drawColor(0x99000000.toInt())
            c.save(); c.translate((decor.width - top.width) / 2f, (decor.height - top.height) / 2f); top.draw(c); c.restore()
        }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun back() { rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }; rule.waitForIdle() }

    @Test fun render() {
        val app = rule.activity.app
        val repo = app.repository
        val today = LocalDate.now()
        fun at(d: Int, h: Int) = Fmt.toMillis(today.minusDays(d.toLong()).atTime(LocalTime.of(h, 0)))
        runBlocking {
            withContext(Dispatchers.IO) { repo.database.clearAllTables() }
            repo.ensureDefaults()
            val cash = repo.accountDao.all().first().id
            val cats = repo.categoryDao.all()
            fun cat(k: String) = cats.first { it.key == k }.id
            repo.saveTxn(Txn(type = TxnType.INCOME, amount = 850_000, accountId = cash, categoryId = cat("salary"), note = "راتب", dateTime = at(1, 9)), Repeat.MONTHLY)
            repo.saveTxn(Txn(type = TxnType.EXPENSE, amount = 25_000, accountId = cash, categoryId = cat("internet"), note = "Omantel", dateTime = at(0, 12), isAuto = true, smsHash = "demo-sms-9", merchant = "OMANTEL"), Repeat.MONTHLY)
            repo.saveTxn(Txn(type = TxnType.EXPENSE, amount = 1_500, accountId = cash, categoryId = cat("food"), note = "كرك وشاي", dateTime = at(0, 8)))
        }
        val now = System.currentTimeMillis()
        // Seed the store the ViewModel actually uses (AndroidViewModelFactory may hold an Application from an
        // earlier Robolectric test in the same JVM).
        val vmApp = ViewModelProvider(rule.activity)[AppViewModel::class.java].getApplication<com.midroid.masarifi.MasarifiApp>()
        vmApp.syncStore.update {
            it.copy(
                fileUri = "content://com.google.android.apps.docs.storage/document/acc%3D1%3Bdoc%3Dencoded%3Ddemo",
                fileLabel = "masarifi-sync.json", fileId = "3f9c2a7e-51d4-4b8e-9a10-7c2d4e6f8a01",
                deviceName = "Pixel 8 (أ)", auto = true, intervalMin = 60, lastAt = now - 120_000, lastOk = true, lastSyncAt = now - 120_000,
                devices = mapOf("dev-b" to DeviceInfo("Galaxy S24 (ب)", now - 3_600_000, "1.3.0")),
                log = listOf(
                    SyncLogEntry(now - 120_000, true, "manual", pulled = 3, pushed = 1, merged = 412, conflicts = 1),
                    SyncLogEntry(now - 1_800_000, true, "change", pulled = 0, pushed = 2, merged = 410, conflicts = 0),
                    SyncLogEntry(now - 5_400_000, false, "auto", error = "no_access"),
                    SyncLogEntry(now - 7_200_000, true, "setup", pulled = 405, pushed = 3, merged = 408, conflicts = 0),
                ),
            )
        }

        // ---- edit screens: «التكرار» shown with the current value
        rule.waitUntil(5_000) { rule.onAllNodesWithText("راتب").fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithText("راتب")[0].performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("كل شهر").fetchSemanticsNodes().isNotEmpty() }
        shot("04_edit_salary_repeat_monthly")
        back()
        rule.onNodeWithText("Omantel").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("كل شهر").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("التكرار").performScrollTo()
        shot("05_edit_sms_entry_repeat")
        back()

        // ---- sync screen
        rule.onNodeWithText("المزيد").performClick()
        rule.onNodeWithText("مزامنة الأجهزة").performScrollTo().performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("مزامنة الحين").fetchSemanticsNodes().isNotEmpty() }
        val vmS = ViewModelProvider(rule.activity)[AppViewModel::class.java]
        check(vmS.syncState.value.configured) { "sync state not applied" }
        rule.waitUntil(5_000) { rule.onAllNodesWithText("masarifi-sync.json", substring = true).fetchSemanticsNodes().size >= 3 }
        shot("01_sync_screen")
        rule.onNodeWithText("المزامنة منفصلة عن النسخ", substring = true).performScrollTo()
        shot("02_sync_screen_howto_log")
        val vm = ViewModelProvider(rule.activity)[AppViewModel::class.java]
        vm.syncUi.value = SyncUi.ConfirmOpen(android.net.Uri.parse("content://demo/masarifi-sync.json"), "masarifi-sync.json", 408, listOf("Pixel 8 (أ)"))
        rule.waitUntil(5_000) { rule.onAllNodesWithText("الدمج مع هذا الملف؟").fetchSemanticsNodes().isNotEmpty() }
        shotWithDialog("03_sync_confirm_merge")
        vm.syncDismiss()
    }
}
