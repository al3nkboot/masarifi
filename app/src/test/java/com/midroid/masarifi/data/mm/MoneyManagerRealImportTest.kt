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
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.time.Instant
import java.time.ZoneId

/** LOCAL-ONLY end-to-end import of a real export (MM_XLSX) into an in-memory DB; skipped when absent. Prints aggregates only. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class MoneyManagerRealImportTest {
    @Test fun importRealFileTwice() = runBlocking {
        val path = System.getenv("MM_XLSX")
        assumeTrue(!path.isNullOrBlank() && File(path).isFile)
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val repo = Repository(db, SettingsStore(ctx))
            repo.ensureDefaults()
            val parsed = MoneyManagerParser.parseFile(File(path!!).readBytes())
            val importer = MoneyManagerImporter(repo)
            val plan = importer.plan(parsed)
            val r = importer.commit(parsed)
            assertEquals(parsed.txns.size, r.imported)
            val txns = repo.txnDao.all()
            assertEquals(parsed.txns.size, txns.size)
            // Monthly totals from the DB equal the parsed totals (which the parser test checks against pandas)
            val zone = ZoneId.systemDefault()
            fun monthly(list: List<Pair<String, Long>>) = list.groupBy({ it.first }, { it.second }).mapValues { it.value.sum() }
            val fromDb = monthly(txns.filter { it.type != TxnType.TRANSFER }.map {
                val d = Instant.ofEpochMilli(it.dateTime).atZone(zone).toLocalDate()
                "%04d-%02d|%d".format(d.year, d.monthValue, it.type) to it.amount
            })
            val fromParse = monthly(parsed.txns.filter { it.kind != MmKind.TRANSFER }.map {
                "%04d-%02d|%d".format(it.dateTime.year, it.dateTime.monthValue, if (it.kind == MmKind.INCOME) TxnType.INCOME else TxnType.EXPENSE) to it.amountMilli
            })
            assertEquals(fromParse, fromDb)
            val r2 = importer.commit(MoneyManagerParser.parseFile(File(path).readBytes()))
            assertEquals(0, r2.imported)
            assertEquals(parsed.txns.size, r2.duplicates)
            assertEquals(parsed.txns.size, repo.txnDao.all().size)
            println("MM_IMPORT inserted=${r.imported} income=${r.income} expense=${r.expense} transfer=${r.transfer} accountsCreated=${r.accountsCreated} " +
                "categoriesCreated=${r.categoriesCreated} newAccountsPlanned=${plan.newAccounts.size} newCategoriesPlanned=${plan.newCategories.size} " +
                "matchedDefaultCategories=${parsed.txns.map { it.category.lowercase() }.toSet().size - plan.newCategories.size} reimportInserted=${r2.imported} reimportDuplicates=${r2.duplicates} dbMonthlyTotals=PASS")
        } finally { db.close() }
    }
}
