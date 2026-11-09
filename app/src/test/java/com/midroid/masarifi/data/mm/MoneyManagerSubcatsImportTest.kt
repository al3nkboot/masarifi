package com.midroid.masarifi.data.mm

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.midroid.masarifi.data.AppDatabase
import com.midroid.masarifi.data.AppDatabase.Companion.configure
import com.midroid.masarifi.data.Repository
import com.midroid.masarifi.data.SettingsStore
import com.midroid.masarifi.data.TxnType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Synthetic Money Manager CSV with emoji-prefixed names + subcategories (the @almnor5 report). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class MoneyManagerSubcatsImportTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: Repository

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).configure().allowMainThreadQueries().build()
        repo = Repository(db, SettingsStore(ctx))
        runBlocking { repo.ensureDefaults() }
    }

    @After fun tearDown() = db.close()

    private fun fixture() = MoneyManagerParser.parseFile(
        javaClass.classLoader!!.getResourceAsStream("mm/money_manager_subcats.csv")!!.readBytes(),
    )

    @Test fun importsSubcategoriesUnderParentAndStripsLeadingEmoji() = runBlocking {
        val parsed = fixture()
        assertEquals(5, parsed.txns.size)
        val phone = parsed.txns.single { it.note == "سماعة بلوتوث" }
        assertEquals("📱إلكترونيات", phone.category)
        assertEquals("🎧سماعة", phone.subcategory)

        val importer = MoneyManagerImporter(repo)
        val plan = importer.plan(parsed)
        assertTrue(plan.newCategories.any { it.second.contains("سماعة") || it.second.contains("إلكترونيات") })

        val r = importer.commit(parsed)
        assertEquals(5, r.imported)

        val cats = repo.categoryDao.all()
        fun byName(n: String) = cats.filter { it.nameAr == n || it.nameEn == n }

        // Emoji stripped out of the name into the emoji field
        val money = byName("مال").single()
        assertEquals("💸", money.emoji)
        assertNull(money.parentId)
        assertTrue(cats.none { it.nameAr == "💸مال" || it.nameEn == "💸مال" })

        val food = byName("أكل").single()
        assertEquals("🍜", food.emoji)
        assertNull(food.parentId)

        val fast = byName("وجبات سريعة").single()
        assertEquals(food.id, fast.parentId)
        assertEquals("🍕", fast.emoji)

        val coffee = byName("قهوة").single()
        assertEquals(food.id, coffee.parentId)

        // Electronics: either matched default "إلكترونيات"/"Electronics" or created with stripped emoji
        val electronics = cats.first { it.parentId == null && (it.key == "electronics" || it.nameAr == "إلكترونيات" || it.nameEn == "Electronics" || it.nameAr == "إلكترونيات") }
        val headset = byName("سماعة").single()
        assertEquals(electronics.id, headset.parentId)
        assertEquals("🎧", headset.emoji)

        // Transactions point at the subcategory when one was set
        val txns = repo.txnDao.all()
        assertEquals(headset.id, txns.single { it.note == "سماعة بلوتوث" }.categoryId)
        assertEquals(fast.id, txns.single { it.note == "شاورما" }.categoryId)
        assertEquals(coffee.id, txns.single { it.note == "كرك" }.categoryId)
        assertEquals(money.id, txns.single { it.note == "راتب" }.categoryId)

        // Description no longer carries the subcategory name
        val phoneTxn = txns.single { it.note == "سماعة بلوتوث" }
        assertEquals("Online order", phoneTxn.description)
        assertTrue(!phoneTxn.description.contains("سماعة") || phoneTxn.description == "Online order")

        // Trip › Hotel under default Trip
        val trip = cats.first { it.key == "trip" }
        val hotel = byName("Hotel").single()
        assertEquals(trip.id, hotel.parentId)
        assertEquals(hotel.id, txns.single { it.note == "فندق" }.categoryId)
    }

    @Test fun normalizeCategoryEmojisCleansAlreadyImportedRows() = runBlocking {
        val id = repo.categoryDao.insert(
            com.midroid.masarifi.data.Category(
                type = TxnType.EXPENSE, nameAr = "💸مال", nameEn = "💸مال", emoji = "📦", sortOrder = 80,
            ),
        )
        val n = repo.normalizeCategoryEmojis()
        assertTrue(n >= 1)
        val c = repo.categoryDao.all().single { it.id == id }
        assertEquals("مال", c.nameAr)
        assertEquals("مال", c.nameEn)
        assertEquals("💸", c.emoji)
    }
}
