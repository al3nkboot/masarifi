package com.midroid.masarifi.data.mm

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.midroid.masarifi.data.AppDatabase
import com.midroid.masarifi.data.AppDatabase.Companion.configure
import com.midroid.masarifi.data.Category
import com.midroid.masarifi.data.Repository
import com.midroid.masarifi.data.SettingsStore
import com.midroid.masarifi.data.Txn
import com.midroid.masarifi.data.TxnType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * 2.6.2: rows imported by ≤2.6.0 (parent category + "<sub> • <description>") are moved onto subcategories,
 * once at start-up (existing subcategories only) and exactly on re-import of the Money Manager file.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SubcategoryRepairDbTest {
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

    /** Imports with today's importer, then rewrites rows to the ≤2.6.0 format (what users have on their phones). */
    private suspend fun importOldFormat(dropSubcategories: Boolean): Map<String, Txn> {
        val parsed = fixture()
        MoneyManagerImporter(repo).commit(parsed)
        val cats = repo.categoryDao.all().associateBy { it.id }
        for (t in repo.txnDao.all()) {
            val sub = cats[t.categoryId] ?: continue
            val pid = sub.parentId ?: continue
            val raw = parsed.txns.single { it.hash == t.smsHash }.subcategory
            val desc = listOf(raw, t.description).filter { it.isNotBlank() }.joinToString(" • ")
            repo.txnDao.update(t.copy(categoryId = pid, description = desc))
        }
        if (dropSubcategories) cats.values.filter { it.parentId != null }.forEach { repo.categoryDao.delete(it) }
        return repo.txnDao.all().associateBy { it.note }
    }

    private suspend fun cat(name: String) = repo.categoryDao.all().single { it.nameAr == name || it.nameEn == name }

    @Test fun oneTimeRepairMovesRowsAndIsUndoable() = runBlocking {
        val before = importOldFormat(dropSubcategories = false)
        assertEquals("🎧سماعة • Online order", before.getValue("سماعة بلوتوث").description)
        val manual = Txn(type = TxnType.EXPENSE, amount = 500, accountId = 1, categoryId = cat("أكل").id, description = "قهوة", dateTime = 1)
        val manualId = repo.txnDao.insert(manual)

        assertEquals(4, repo.repairImportedSubcategoriesOnce())
        val after = repo.txnDao.all().associateBy { it.note }
        assertEquals(cat("سماعة").id, after.getValue("سماعة بلوتوث").categoryId)
        assertEquals("Online order", after.getValue("سماعة بلوتوث").description)
        assertEquals(cat("وجبات سريعة").id, after.getValue("شاورما").categoryId)
        assertEquals("", after.getValue("شاورما").description)
        assertEquals(cat("قهوة").id, after.getValue("كرك").categoryId)
        assertEquals(cat("Hotel").id, after.getValue("فندق").categoryId)
        // manual row untouched even though its description equals a subcategory name
        assertEquals(cat("أكل").id, repo.txnDao.get(manualId)!!.categoryId)
        // runs once only
        assertEquals(0, repo.repairImportedSubcategoriesOnce())

        // the run is in the repair log → existing «undo last repair» restores the old rows
        val batch = repo.repairDao.observeLatest().first()
        assertNotNull(batch)
        assertEquals(4, batch!!.changes)
        assertTrue(repo.undoRepair(batch.id))
        val undone = repo.txnDao.all().associateBy { it.note }
        for (n in listOf("سماعة بلوتوث", "شاورما", "كرك", "فندق")) {
            assertEquals(before.getValue(n).categoryId, undone.getValue(n).categoryId)
            assertEquals(before.getValue(n).description, undone.getValue(n).description)
        }
    }

    @Test fun oneTimeRepairNeverCreatesCategories() = runBlocking {
        importOldFormat(dropSubcategories = true)
        val catCount = repo.categoryDao.count()
        val rows = repo.txnDao.all()
        assertEquals(0, repo.repairImportedSubcategoriesOnce())
        assertEquals(catCount, repo.categoryDao.count())
        assertEquals(rows, repo.txnDao.all())
    }

    @Test fun reimportOfTheSameFileRepairsExactlyAndCreatesMissingSubcategories() = runBlocking {
        importOldFormat(dropSubcategories = true)
        val r = MoneyManagerImporter(repo).commit(fixture())
        assertEquals(0, r.imported)
        assertEquals(5, r.duplicates)
        assertEquals(4, r.subcategoriesFixed)
        val t = repo.txnDao.all().associateBy { it.note }
        val headset = cat("سماعة")
        assertEquals("🎧", headset.emoji)
        assertEquals(headset.id, t.getValue("سماعة بلوتوث").categoryId)
        assertEquals("Online order", t.getValue("سماعة بلوتوث").description)
        assertEquals(cat("Hotel").id, t.getValue("فندق").categoryId)
        assertEquals(cat("أكل").id, cat("قهوة").parentId)
        // income row without subcategory untouched
        assertEquals(cat("مال").id, t.getValue("راتب").categoryId)
        // a second re-import changes nothing
        assertEquals(0, MoneyManagerImporter(repo).commit(fixture()).subcategoriesFixed)
    }

    @Test fun reimportSkipsRowsTheUserRecategorised() = runBlocking {
        importOldFormat(dropSubcategories = true)
        val shawarma = repo.txnDao.all().single { it.note == "شاورما" }
        val trip = repo.categoryDao.all().first { it.key == "trip" }
        repo.txnDao.update(shawarma.copy(categoryId = trip.id))
        val r = MoneyManagerImporter(repo).commit(fixture())
        assertEquals(3, r.subcategoriesFixed)
        val now = repo.txnDao.get(shawarma.id)!!
        assertEquals(trip.id, now.categoryId)
        assertEquals("🍕وجبات سريعة", now.description)
    }

    @Test fun reorderSaveAndDeleteSubcategory() = runBlocking {
        val exp = repo.categoryDao.all().filter { it.type == TxnType.EXPENSE && it.parentId == null }
        val reversed = exp.map { it.id }.reversed()
        repo.reorderCategories(reversed)
        assertEquals(reversed, repo.categoryDao.all().filter { it.type == TxnType.EXPENSE && it.parentId == null }.map { it.id })

        val food = repo.categoryDao.all().first { it.key == "food" }
        val a = repo.saveCategory(Category(type = TxnType.EXPENSE, nameAr = "أ", nameEn = "A", emoji = "🅰️", parentId = food.id, sortOrder = 50))
        val b = repo.saveCategory(Category(type = TxnType.EXPENSE, nameAr = "ب", nameEn = "B", emoji = "🅱️", parentId = food.id, sortOrder = 50))
        assertEquals(listOf(a, b), repo.categoryDao.all().filter { it.parentId == food.id }.map { it.id })
        repo.reorderCategories(listOf(b, a))
        assertEquals(listOf(b, a), repo.categoryDao.all().filter { it.parentId == food.id }.map { it.id })

        // deleting a subcategory keeps its transactions on the parent
        val tid = repo.txnDao.insert(Txn(type = TxnType.EXPENSE, amount = 1, accountId = 1, categoryId = a, dateTime = 1))
        repo.deleteCategory(repo.categoryDao.all().single { it.id == a })
        assertEquals(food.id, repo.txnDao.get(tid)!!.categoryId)
        // deleting the parent removes its subcategories and uncategorizes rows (unchanged behaviour)
        repo.deleteCategory(repo.categoryDao.all().single { it.id == food.id })
        assertTrue(repo.categoryDao.all().none { it.id == b })
        assertNull(repo.txnDao.get(tid)!!.categoryId)
    }
}
