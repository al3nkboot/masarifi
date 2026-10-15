package com.midroid.masarifi.data.mm

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.midroid.masarifi.data.AppDatabase
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

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class MoneyManagerImporterTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: Repository

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = Repository(db, SettingsStore(ctx))
        runBlocking { repo.ensureDefaults() }
    }

    @After fun tearDown() = db.close()

    private fun fixture() = MoneyManagerParser.parseFile(javaClass.classLoader!!.getResourceAsStream("mm/money_manager_sample.xlsx")!!.readBytes())

    @Test fun previewThenImportThenReimportIsSafe() = runBlocking {
        val importer = MoneyManagerImporter(repo)
        val parsed = fixture()
        val plan = importer.plan(parsed)
        assertEquals(2, plan.income); assertEquals(9, plan.expense); assertEquals(2, plan.transfer); assertEquals(0, plan.duplicates)
        // "Cash" maps to the default Arabic cash account (نقد); others are created
        assertEquals(listOf("Bank Muscat", "Visa Card", "Savings"), plan.newAccounts)
        // Food/Electronics/Trip/fuel/Gift/Salary/Bonus match defaults case-insensitively; only the typo "Grecory" is new
        assertEquals(listOf(TxnType.EXPENSE to "Grecory"), plan.newCategories)

        val r = importer.commit(parsed)
        assertEquals(13, r.imported); assertEquals(3, r.accountsCreated); assertEquals(1, r.categoriesCreated); assertEquals(2, r.skipped.size)

        val txns = repo.txnDao.all()
        assertEquals(13, txns.size)
        val accounts = repo.accountDao.all().associateBy { it.id }
        val cats = repo.categoryDao.all().associateBy { it.id }
        val transfers = txns.filter { it.type == TxnType.TRANSFER }
        assertEquals(2, transfers.size) // the Transfer-Out/Transfer-In pair is NOT doubled
        val atm = transfers.single { it.note == "ATM" }
        assertEquals("Bank Muscat", accounts[atm.accountId]!!.name)
        assertEquals("نقد", accounts[atm.toAccountId]!!.name)
        assertNull(atm.categoryId)
        val food = txns.single { it.note == "شاورما" }
        assertEquals("food", cats[food.categoryId]!!.key)
        assertEquals(1500L, food.amount)
        val phone = txns.single { it.note == "سماعة" }
        assertEquals("electronics", cats[phone.categoryId]!!.key)
        assertTrue(phone.description.contains("Phone") && phone.description.contains("Online order"))
        assertTrue(txns.single { it.note == "Hotel" }.description.contains("USD 100"))
        assertEquals("salary", cats[txns.single { it.note == "راتب مارس" }.categoryId]!!.key)

        // second import of the same file: everything is a duplicate, nothing is added
        val plan2 = importer.plan(fixture())
        assertEquals(0, plan2.toImport); assertEquals(13, plan2.duplicates)
        assertTrue(plan2.newAccounts.isEmpty() && plan2.newCategories.isEmpty())
        val r2 = importer.commit(fixture())
        assertEquals(0, r2.imported); assertEquals(13, r2.duplicates)
        assertEquals(13, repo.txnDao.all().size)
        assertEquals(4, repo.accountDao.all().size)
    }
}
