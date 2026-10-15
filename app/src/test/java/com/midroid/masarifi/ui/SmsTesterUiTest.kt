package com.midroid.masarifi.ui

import android.graphics.Bitmap
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.midroid.masarifi.MainActivity
import com.midroid.masarifi.sms.ProviderSamplesTest.Companion.BD
import com.midroid.masarifi.sms.ProviderSamplesTest.Companion.BD_CREDIT_200
import com.midroid.masarifi.sms.ProviderSamplesTest.Companion.BM
import com.midroid.masarifi.sms.ProviderSamplesTest.Companion.BM_OTP
import com.midroid.masarifi.sms.ProviderSamplesTest.Companion.BM_SENT
import com.midroid.masarifi.sms.ProviderSamplesTest.Companion.BM_TOPUP
import com.midroid.masarifi.sms.ProviderSamplesTest.Companion.T3
import com.midroid.masarifi.sms.ProviderSamplesTest.Companion.THAWANI
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** «تجربة رسالة» box in SMS settings parses the user's samples correctly; screenshots → build/screenshots-1.4 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xhdpi")
class SmsTesterUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val dir = File("build/screenshots-1.4").apply { mkdirs() }

    private fun shot(name: String) {
        rule.waitForIdle()
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(android.graphics.Canvas(bmp)) }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** A result line (not the editable input that contains the SMS text itself). */
    private fun result(text: String) = hasText(text, substring = true) and !hasSetTextAction()

    private fun exists(text: String) =
        rule.waitUntil(5_000) { rule.onAllNodes(result(text)).fetchSemanticsNodes().isNotEmpty() }

    private fun scrollTo(text: String) { rule.onAllNodes(result(text))[0].performScrollTo() }

    private fun test(sender: String, body: String) {
        val fields = rule.onAllNodes(hasSetTextAction())
        fields[1].performScrollTo().performTextReplacement(sender)
        fields[2].performScrollTo().performTextReplacement(body)
        rule.waitForIdle()
    }

    @Test fun samplesParseInTesterBox() {
        rule.onNodeWithText("المزيد").performClick()
        rule.onNodeWithText("الاستيراد الذكي من الرسائل").performScrollTo().performClick()
        exists("إعادة فحص الرسائل")
        scrollTo("إعادة فحص الرسائل")
        shot("01_sms_settings_rescan")
        scrollTo("استعادة الافتراضي")
        shot("02_sms_settings_wallet_topups")

        test(THAWANI, T3)
        exists("مصروف"); exists("OMR 0.250"); exists("DEMO BAKERY"); exists("5501"); exists("48.250")
        scrollTo("48.250")
        shot("03_tester_thawani_leading_dot")

        test(BM, BM_TOPUP)
        exists("تحويل (شحن)"); exists("OMR 50.000"); exists("ThawaniPay Topup DEMO01"); exists("500.000")
        scrollTo("500.000")
        shot("04_tester_bankmuscat_topup")

        test("BANKMUSCAT", BM_SENT)
        exists("OMR 30.000"); exists("ABCD######E XY"); exists("تحويلات لأشخاص"); exists("BMCT010000000114")
        scrollTo("BMCT010000000114")
        shot("05_tester_bankmuscat_sent")

        test(BD, BD_CREDIT_200)
        exists("الدخل"); exists("OMR 200.000"); exists("7001"); exists("205.000"); exists("2025-01-10")
        scrollTo("205.000")
        shot("06_tester_bankdhofar_credit")

        test(BM, BM_OTP)
        exists("لم يتم التعرف عليها")
    }
}
