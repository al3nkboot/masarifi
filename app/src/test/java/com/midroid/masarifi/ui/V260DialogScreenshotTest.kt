package com.midroid.masarifi.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.midroid.masarifi.ui.theme.MasarifiTheme
import com.midroid.masarifi.ui.theme.Spacing
import com.midroid.masarifi.util.Currencies
import com.midroid.masarifi.util.UpdateChecker
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xhdpi")
class V260DialogScreenshotTest {

    private fun save(name: String, bmp: Bitmap) {
        val dir = File("build/screenshots/v260").apply { mkdirs() }
        File(dir, "${name}_ar_dark.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun render(name: String, content: @Composable () -> Unit) {
        val sc = ActivityScenario.launch(ComponentActivity::class.java)
        lateinit var bmp: Bitmap
        sc.onActivity { act ->
            act.setContent { MasarifiTheme("dark") { content() } }
            val v = act.window.decorView
            v.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(786, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(1704, android.view.View.MeasureSpec.EXACTLY),
            )
            v.layout(0, 0, 786, 1704)
            Thread.sleep(350)
            bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
            v.draw(Canvas(bmp))
        }
        save(name, bmp)
        sc.close()
    }

    @Test fun currencyDialog() {
        render("02_currency_dialog") {
            val arabic = true
            val options = Currencies.all.map { it.code to "${it.symbol(arabic)}  ${if (arabic) it.nameAr else it.nameEn} (${it.code})" }
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                Surface(shape = RoundedCornerShape(28.dp), tonalElevation = 6.dp, modifier = Modifier.fillMaxWidth(0.92f).padding(Spacing.md)) {
                    Column(Modifier.padding(Spacing.lg)) {
                        Text("العملة", style = MaterialTheme.typography.headlineSmall)
                        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()).padding(top = Spacing.md)) {
                            options.forEach { (k, label) ->
                                Row(Modifier.fillMaxWidth().padding(vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = k == "OMR", onClick = {})
                                    Text(label)
                                }
                            }
                        }
                        TextButton(onClick = {}, modifier = Modifier.align(Alignment.End)) { Text("إلغاء") }
                    }
                }
            }
        }
    }

    @Test fun updateUpToDate() {
        val release = UpdateChecker.Release("v2.5.1", "2.5.1", "2.5.1", "- notes", UpdateChecker.RELEASES_PAGE, emptyList())
        val result = UpdateChecker.evaluate("2.6.0", release, true) as UpdateChecker.CheckResult.UpToDate
        render("03_update_check") {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                Surface(shape = RoundedCornerShape(28.dp), tonalElevation = 6.dp, modifier = Modifier.fillMaxWidth(0.92f).padding(Spacing.md)) {
                    Column(Modifier.padding(Spacing.lg)) {
                        Text("التحقق من التحديثات", style = MaterialTheme.typography.headlineSmall)
                        Text("أنت على أحدث إصدار (2.6.0).", modifier = Modifier.padding(top = Spacing.md))
                        TextButton(onClick = {}, modifier = Modifier.align(Alignment.End).padding(top = Spacing.sm)) { Text("حسناً") }
                    }
                }
            }
        }
    }

    @Test fun updateAvailable() {
        val release = UpdateChecker.Release(
            "v2.7.0", "2.7.0", "2.7.0", "- currencies\n- updates", UpdateChecker.RELEASES_PAGE,
            listOf(
                UpdateChecker.Asset("masarifi-v2.7.0.apk", "https://example.com/full"),
                UpdateChecker.Asset("masarifi-v2.7.0-nosms.apk", "https://example.com/nosms"),
            ),
        )
        val result = UpdateChecker.evaluate("2.6.0", release, true) as UpdateChecker.CheckResult.UpdateAvailable
        val notes = UpdateChecker.notesSnippet(result.release.body)
        render("05_update_available") {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                Surface(shape = RoundedCornerShape(28.dp), tonalElevation = 6.dp, modifier = Modifier.fillMaxWidth(0.92f).padding(Spacing.md)) {
                    Column(Modifier.padding(Spacing.lg)) {
                        Text("التحقق من التحديثات", style = MaterialTheme.typography.headlineSmall)
                        Text("يتوفر إصدار جديد ${result.release.version} (نسختك ${result.current}).", modifier = Modifier.padding(top = Spacing.md))
                        if (notes.isNotBlank()) Text(notes, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = Spacing.sm))
                        Row(Modifier.align(Alignment.End).padding(top = Spacing.sm)) {
                            TextButton(onClick = {}) { Text("فتح صفحة الإصدار") }
                            TextButton(onClick = {}) { Text("تحميل التحديث") }
                        }
                    }
                }
            }
        }
    }
}
