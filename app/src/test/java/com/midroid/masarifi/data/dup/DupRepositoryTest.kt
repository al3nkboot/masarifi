package com.midroid.masarifi.data.dup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.midroid.masarifi.data.AppDatabase
import com.midroid.masarifi.data.AppSettings
import com.midroid.masarifi.data.Repository
import com.midroid.masarifi.data.SettingsStore
import com.midroid.masarifi.data.Txn
import com.midroid.masarifi.data.TxnType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.time.ZoneId

/** SMS ↔ manual duplicate handling end-to-end through Repository + Room (in-memory). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class DupRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: Repository
    private lateinit var settings: SettingsStore
    private val zone = ZoneId.systemDefault()
    private fun at(d: Int, h: Int, m: Int = 0) = LocalDateTime.of(2026, 10, d, h, m).atZone(zone).toInstant().toEpochMilli()
    private val sender = "BankMuscat"
    private fun body(amount: String, card: String = "1234", time: String = "06/10/2026 14:35") =
        "Dear Customer, Purchase of OMR $amount was made using your Debit Card XXXX$card at LULU HYPERMARKET BAUSHER on $time. Avl Bal OMR 812.450"
    private val smsTime get() = at(6, 14, 35)

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        settings = SettingsStore(ctx).apply { update { AppSettings() } }
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = Repository(db, settings)
        runBlocking { repo.ensureDefaults() }
    }

    @After fun tearDown() = db.close()

    private suspend fun manual(amount: Long, time: Long, note: String = "غداء", categoryKey: String = "food"): Txn {
        val cat = repo.categoryDao.all().first { it.key == categoryKey }.id
        val acc = repo.accountDao.all().first().id
        val t = Txn(type = TxnType.EXPENSE, amount = amount, accountId = acc, categoryId = cat, note = note, dateTime = time)
        return t.copy(id = repo.txnDao.insert(t))
    }

    private suspend fun scan() = DuplicateDetector.scan(repo.txnDao.all(), settings.current.dupWindowHours, repo.dupDao.dismissed().toSet())

    @Test fun linkOnSingleMatch_thenUndoSplit() = runBlocking {
        val m = manual(4_300, at(6, 12, 0))
        val r = repo.importSms(sender, body("4.300"), smsTime)!!
        assertTrue(r.linked)
        assertEquals(m.id, r.txn.id)
        val all = repo.txnDao.all()
        assertEquals(1, all.size) // no second transaction
        val linked = all.single()
        assertTrue(linked.smsLinked)
        assertEquals("غداء", linked.note)                 // user's data kept
        assertEquals(m.categoryId, linked.categoryId)
        assertEquals("LULU HYPERMARKET BAUSHER", linked.merchant)
        val link = repo.dupDao.linksFor(m.id).single()
        assertEquals("1234", link.cardLast4)
        // the same SMS again is ignored
        assertNull(repo.importSms(sender, body("4.300"), smsTime))
        assertEquals(1, repo.txnDao.all().size)
        assertTrue(scan().isEmpty())

        // undo → separate SMS transaction, original restored, pair suppressed
        val newId = repo.splitLink(r.linkId!!)!!
        val after = repo.txnDao.all()
        assertEquals(2, after.size)
        val split = after.single { it.id == newId }
        assertTrue(split.isAuto)
        assertEquals(4_300L, split.amount)
        assertEquals(link.smsHash, split.smsHash)
        val orig = after.single { it.id == m.id }
        assertFalse(orig.smsLinked)
        assertNull(orig.merchant)
        assertTrue(repo.dupDao.linksFor(m.id).isEmpty())
        assertTrue(scan().isEmpty()) // user said they're different: never shown as duplicates
        // still deduplicated
        assertNull(repo.importSms(sender, body("4.300"), smsTime))
    }

    @Test fun flagOnMultipleMatches() = runBlocking {
        val a = manual(4_300, at(6, 9))
        val b = manual(4_300, at(5, 20))
        val r = repo.importSms(sender, body("4.300"), smsTime)!!
        assertFalse(r.linked)
        assertTrue(r.flagged)
        assertEquals(setOf(a.id, b.id), r.txn.possibleDupOf!!.split(',').map { it.toLong() }.toSet())
        assertEquals(3, repo.txnDao.all().size)
        assertEquals(1, scan().size)
    }

    @Test fun weakMatchFlagged_farMatchIgnored_amountMustBeExact() = runBlocking {
        manual(4_300, at(3, 10))   // 3+ days before: outside ±48h, inside 7 days → flag
        manual(9_999, at(6, 14))   // different amount
        manual(4_299, at(6, 14))   // 0.001 less → never matches
        val r = repo.importSms(sender, body("4.300"), smsTime)!!
        assertFalse(r.linked)
        assertTrue(r.flagged)
        assertEquals(1, r.txn.possibleDupOf!!.split(',').size)

        val far = manual(12_000, at(6, 14) - 8L * 24 * DuplicateDetector.HOUR)
        val r2 = repo.importSms(sender, body("12.000", time = "06/10/2026 14:40"), at(6, 14, 40))!!
        assertFalse(r2.linked)
        assertFalse(r2.flagged)
        assertNotNull(far)
    }

    @Test fun keepBothSuppressionPersists() = runBlocking {
        manual(4_300, at(6, 9)); manual(4_300, at(5, 20))
        repo.importSms(sender, body("4.300"), smsTime)!!
        val g = scan().single()
        repo.keepBoth(g.ids)
        assertTrue(scan().isEmpty())
        assertTrue(repo.txnDao.all().none { it.possibleDupOf != null })
        // persisted in the DB (survives a new Repository instance / app restart)
        val repo2 = Repository(db, settings)
        assertTrue(DuplicateDetector.scan(repo2.txnDao.all(), 48, repo2.dupDao.dismissed().toSet()).isEmpty())
    }

    @Test fun mergeFromReviewKeepsManualAndCanBeSplit() = runBlocking {
        // an SMS that arrived before the manual entry (no automatic link) → review → merge
        val r = repo.importSms(sender, body("4.300"), smsTime)!!
        val m = manual(4_300, at(6, 18), note = "عشاء", categoryKey = "grocery")
        val g = scan().single()
        assertEquals(DupReason.SMS_MATCH, g.reason)
        val keeper = repo.mergeGroup(g.ids)!!
        assertEquals(m.id, keeper)
        val all = repo.txnDao.all()
        assertEquals(1, all.size)
        assertEquals("عشاء", all.single().note)
        assertTrue(all.single().smsLinked)
        assertNull(repo.importSms(sender, body("4.300"), smsTime)) // SMS hash still protected
        val link = repo.dupDao.linksFor(m.id).single()
        assertEquals(r.txn.smsHash, link.smsHash)
        repo.splitLink(link.id)
        assertEquals(2, repo.txnDao.all().size)
    }

    @Test fun bulkDeleteAndExactRestore() = runBlocking {
        manual(1_000, at(1, 10))
        val imported = Txn(type = TxnType.EXPENSE, amount = 2_000, accountId = 1, dateTime = at(2, 10), smsHash = "mm:fingerprint")
        repo.txnDao.insert(imported)
        repo.importSms(sender, body("7.000"), smsTime)
        val before = repo.txnDao.all()
        val deleted = repo.deleteTxns(before.map { it.id })
        assertEquals(3, deleted.size)
        assertTrue(repo.txnDao.all().isEmpty())
        assertEquals(3, repo.restoreTxns(deleted))
        assertEquals(before, repo.txnDao.all())
    }

    @Test fun mergeIntoExistingSmsEntry() = runBlocking {
        val r = repo.importSms(sender, body("4.300"), smsTime)!!
        val cat = repo.categoryDao.all().first { it.key == "grocery" }.id
        val draft = Txn(type = TxnType.EXPENSE, amount = 4_300, accountId = 1, categoryId = cat, note = "بقالة الأسبوع", dateTime = smsTime)
        repo.mergeIntoExisting(r.txn.id, draft)
        val t = repo.txnDao.all().single()
        assertEquals(cat, t.categoryId)
        assertEquals("بقالة الأسبوع", t.note)
        assertEquals(r.txn.smsHash, t.smsHash)
        assertEquals(1L, t.accountId)
    }
}
