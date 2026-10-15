package com.midroid.masarifi.data.backup

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
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** A backup file written by 1.7.2 (frozen fixture, synthetic data) must restore unchanged in 2.0.0. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class BackupCompat172Test {
    private val json = javaClass.classLoader!!.getResource("compat/backup-1.7.2.json")!!.readText()

    @Test fun decodesAndReEncodesIdentically() {
        val d = BackupCodec.decode(json)
        assertEquals("1.7.2", d.appVersion); assertEquals(4, d.schemaVersion)
        assertEquals(4, d.transactions.size); assertEquals(1, d.cardMappings!!.size)
        assertEquals(org.json.JSONObject(json).toString(), org.json.JSONObject(BackupCodec.encode(d)).toString())
    }

    @Test fun restoresIntoFreshInstall() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).configure().allowMainThreadQueries().build()
        val st = SettingsStore(ctx, "compat172"); val repo = Repository(db, st); repo.ensureDefaults()
        BackupService(repo, st) { "2.0.0" }.restore(BackupCodec.decode(json))
        val cats = repo.categoryDao.all().associateBy { it.id }
        val accs = repo.accountDao.all().associateBy { it.id }
        val t = repo.txnDao.all().sortedBy { it.dateTime }
        assertEquals(listOf(650_000L, 1_500L, 2_250L, 20_000L), t.map { it.amount })
        assertEquals(listOf("Salary", "Pharmacy", "Food", null), t.map { x -> x.categoryId?.let { cats[it]?.nameEn } })
        assertEquals("Bank SAMPLE", accs[t[1].accountId]!!.name)
        assertEquals(TxnType.TRANSFER, t[3].type); assertEquals("نقد", accs[t[3].toAccountId]!!.name)
        assertEquals("${t[1].id}", t[2].possibleDupOf)
        assertEquals(t[1].accountId, repo.cardMappingDao.find("0099", "bm")!!.accountId)
        assertEquals(60_000L, repo.budgetDao.all().single().amount)
        assertEquals("sample pharmacy", repo.merchantRuleDao.all().single().merchantKey)
        db.close()
    }
}
