package com.midroid.masarifi.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.midroid.masarifi.data.AppDatabase.Companion.configure
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

/** 1.3.0 fix: the repetition of an existing transaction can be read and changed (create / change / stop the rule). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class RepeatEditRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: Repository
    private var cash = 0L
    private val day = 86_400_000L
    private val now = System.currentTimeMillis()

    @Before fun setUp() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).configure().allowMainThreadQueries().build()
        repo = Repository(db, SettingsStore(ctx, "repeat_test"))
        repo.ensureDefaults()
        cash = repo.accountDao.all().first().id
    }
    @After fun tearDown() = db.close()

    private suspend fun rules() = db.backupDao().recurring()

    @Test fun monthlyIsReadBack_changed_andStopped() = runBlocking {
        val id = repo.saveTxn(Txn(type = TxnType.INCOME, amount = 850_000, accountId = cash, note = "راتب", dateTime = now - day), Repeat.MONTHLY)
        val t = repo.txnDao.get(id)!!
        assertNotNull(t.recurringId)
        assertEquals(Repeat.MONTHLY, repo.repeatOf(t))

        // edit without touching the repeat (null) keeps the rule as is
        repo.saveTxn(t.copy(note = "راتب أكتوبر"), null)
        assertEquals(Repeat.MONTHLY, rules().single().frequency)

        // monthly → weekly: same rule, new frequency, next date recomputed from the latest occurrence, new values used
        repo.saveTxn(repo.txnDao.get(id)!!.copy(amount = 900_000), Repeat.WEEKLY)
        val r = rules().single()
        assertEquals(Repeat.WEEKLY, r.frequency)
        assertEquals(900_000L, r.amount)
        assertEquals(repo.advance(t.dateTime, Repeat.WEEKLY), r.nextDate)
        assertEquals(Repeat.WEEKLY, repo.repeatOf(repo.txnDao.get(id)!!))

        // → none: the rule is stopped and no transaction points to it any more
        repo.saveTxn(repo.txnDao.get(id)!!, Repeat.NONE)
        assertTrue(rules().isEmpty())
        assertTrue(repo.txnDao.all().all { it.recurringId == null })
        assertEquals(Repeat.NONE, repo.repeatOf(repo.txnDao.get(id)!!))
    }

    @Test fun repeatCanBeAddedToSmsLinkedAndImportedRows() = runBlocking {
        val sms = repo.txnDao.insert(Txn(type = TxnType.EXPENSE, amount = 25_000, accountId = cash, note = "Omantel", dateTime = now - 2 * day, isAuto = true, smsHash = "h-sms", smsLinked = true))
        val imported = repo.txnDao.insert(Txn(type = TxnType.EXPENSE, amount = 150_000, accountId = cash, note = "إيجار", dateTime = now - 3 * day, smsHash = "mm:fp"))
        repo.saveTxn(repo.txnDao.get(sms)!!, Repeat.MONTHLY)
        repo.saveTxn(repo.txnDao.get(imported)!!, Repeat.YEARLY)
        assertEquals(Repeat.MONTHLY, repo.repeatOf(repo.txnDao.get(sms)!!))
        assertEquals(Repeat.YEARLY, repo.repeatOf(repo.txnDao.get(imported)!!))
        assertEquals(setOf(Repeat.MONTHLY, Repeat.YEARLY), rules().map { it.frequency }.toSet())
        // SMS hash / import fingerprint untouched
        assertEquals("h-sms", repo.txnDao.get(sms)!!.smsHash)
        assertEquals("mm:fp", repo.txnDao.get(imported)!!.smsHash)
        assertEquals(2, repo.txnDao.all().size) // next occurrences are in the future → nothing generated yet
    }

    @Test fun deletedRuleReadsAsNone() = runBlocking {
        val id = repo.saveTxn(Txn(type = TxnType.EXPENSE, amount = 1, accountId = cash, dateTime = now), Repeat.DAILY)
        val t = repo.txnDao.get(id)!!
        repo.recurringDao.delete(t.recurringId!!)  // deleted from «المعاملات المتكررة»
        assertEquals(Repeat.NONE, repo.repeatOf(t))
        repo.saveTxn(t, Repeat.NONE)
        assertNull(repo.txnDao.get(id)!!.recurringId)
    }
}
