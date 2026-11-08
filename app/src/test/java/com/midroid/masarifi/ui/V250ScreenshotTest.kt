package com.midroid.masarifi.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.swipeDown

import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.midroid.masarifi.MainActivity
import com.midroid.masarifi.R
import com.midroid.masarifi.app
import com.midroid.masarifi.bills.Bill
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

/** 2.5.0: warranties + Daily-tab FAB → build/screenshots/v250. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xhdpi")
class V250ScreenshotTest {
    @get:Rule val rule = createEmptyComposeRule()

    @Test fun dark() = run("ar", "dark")
    @Test fun light() = run("ar", "light")

    private fun run(lang: String, theme: String) {
        com.midroid.masarifi.ui.warranty.imageDecodeDispatcher = kotlinx.coroutines.Dispatchers.Main
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
            repo.saveTxn(Txn(type = TxnType.EXPENSE, amount = 1500, accountId = cash, categoryId = cat("food"), note = "Karak", dateTime = at(0, 8)))
            repo.saveTxn(Txn(type = TxnType.EXPENSE, amount = 7200, accountId = cash, categoryId = cat("fuel"), note = "Shell", dateTime = at(1, 18), isAuto = true))
            repo.saveTxn(Txn(type = TxnType.INCOME, amount = 850000, accountId = cash, categoryId = cat("salary"), note = "Salary", dateTime = at(2, 9)))
            repo.saveTxn(Txn(type = TxnType.EXPENSE, amount = 25000, accountId = cash, categoryId = cat("internet"), note = "Omantel", dateTime = at(2, 12)))
            repo.saveTxn(Txn(type = TxnType.EXPENSE, amount = 12500, accountId = cash, categoryId = cat("food"), note = "Dinner", dateTime = at(3, 20)))
            repo.saveTxn(Txn(type = TxnType.EXPENSE, amount = 3000, accountId = cash, categoryId = cat("grocery"), note = "Bakery", dateTime = at(4, 7)))
            val names = listOf("water", "Electricity", "Talabat", "X Premium", "Anghami", "SuperGrok")
            val ids = names.mapIndexed { i, n -> repo.categoryDao.insert(com.midroid.masarifi.data.Category(type = TxnType.EXPENSE, nameAr = n, nameEn = n, emoji = "📦", sortOrder = 60 + i)) }
            val amounts = listOf(9500L, 18750L, 6400L, 3200L, 1800L, 11500L)
            names.forEachIndexed { i, n ->
                repo.saveTxn(Txn(type = TxnType.EXPENSE, amount = amounts[i], accountId = cash, categoryId = ids[i], note = n, dateTime = at(i % 4, 14)))
                repo.bills.save(Bill(name = n, amount = amounts[i], categoryId = ids[i], anchorDay = today.plusDays(i.toLong() - 2).dayOfMonth, nextDue = today.plusDays(i.toLong() - 2).toEpochDay()))
            }
            repo.bills.save(Bill(name = "Nama Electricity", amount = 18750, anchorDay = today.plusDays(12).dayOfMonth, nextDue = today.plusDays(12).toEpochDay()))
            repo.bills.save(Bill(name = "Netflix", amount = 4200, anchorDay = today.plusDays(20).dayOfMonth, nextDue = today.plusDays(20).toEpochDay()))
            // warranties (sample data, test only) with generated "invoice" images
            fun invoice(name: String, title: String): String {
                val b = Bitmap.createBitmap(900, 1200, Bitmap.Config.ARGB_8888)
                val c = android.graphics.Canvas(b); c.drawColor(android.graphics.Color.WHITE)
                val p = android.graphics.Paint().apply { color = android.graphics.Color.DKGRAY; textSize = 54f; isAntiAlias = true }
                c.drawText(title, 60f, 120f, p); p.textSize = 34f
                listOf("Item ........ 1", "VAT 5%", "TOTAL  OMR 129.500", "Warranty: 2 years").forEachIndexed { i, t -> c.drawText(t, 60f, 260f + i * 80f, p) }
                p.color = android.graphics.Color.LTGRAY; for (i in 0..12) c.drawRect(60f, 640f + i * 40f, 840f, 650f + i * 40f, p)
                app.warrantyImages.file(name).apply { parentFile?.mkdirs() }.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
                return name
            }
            val wd = app.repository.warrantyDao
            fun ed(d: LocalDate) = d.toEpochDay()
            wd.insert(com.midroid.masarifi.warranty.Warranty(name = "LG OLED TV 65", category = 1, brand = "LG", store = "Sharaf DG", purchaseDate = ed(today.minusMonths(10)), durationValue = 2, durationUnit = 1, expiryDate = ed(today.minusMonths(10).plusYears(2)), price = 649_000, notes = "Serial 12345").withImages(listOf(invoice("inv_s1.jpg", "SHARAF DG"), invoice("inv_s2.jpg", "LG Service"))))
            wd.insert(com.midroid.masarifi.warranty.Warranty(name = "Samsung Washer", category = 0, brand = "Samsung", store = "Lulu", purchaseDate = ed(today.minusMonths(12).plusDays(18)), durationValue = 1, durationUnit = 1, expiryDate = ed(today.plusDays(18)), price = 189_900).withImages(listOf(invoice("inv_s3.jpg", "LULU HYPERMARKET"))))
            wd.insert(com.midroid.masarifi.warranty.Warranty(name = "iPhone 15", category = 1, brand = "Apple", store = "Omantel", purchaseDate = ed(today.minusMonths(13)), durationValue = 12, durationUnit = 0, expiryDate = ed(today.minusMonths(1))))
            wd.insert(com.midroid.masarifi.warranty.Warranty(name = "مكيف جري", category = 0, brand = "Gree", purchaseDate = ed(today.minusMonths(3)), durationValue = 5, durationUnit = 1, expiryDate = ed(today.minusMonths(3).plusYears(5))))
        }
        rule.waitUntil(8_000) { rule.onAllNodesWithText("LuLu").fetchSemanticsNodes().isNotEmpty() }
        fun shot(name: String) {
            rule.waitForIdle()
            sc.onActivity { a ->
                val v = a.window.decorView
                val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
                v.draw(android.graphics.Canvas(bmp))
                val dir = File("build/screenshots/v250").apply { mkdirs() }
                File(dir, "${name}_${lang}_$theme.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
        fun s(id: Int) = act.getString(id)
        rule.onNodeWithText(s(R.string.daily)).performClick(); shot("05_daily_top_fab")
        rule.onNodeWithText(s(R.string.tab_more)).performClick(); shot("04_more")
        rule.onNodeWithText(s(R.string.warranties)).performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("LG OLED TV 65").fetchSemanticsNodes().isNotEmpty() }
        Thread.sleep(300); shot("01_warranty_list")
        rule.onNodeWithText("LG OLED TV 65").performClick(); rule.waitForIdle(); Thread.sleep(300); shot("03_warranty_detail")
        rule.onNodeWithContentDescription(s(R.string.warranty_edit)).performClick(); rule.waitForIdle(); Thread.sleep(300); shot("02_warranty_edit")
        sc.close()
    }
}
