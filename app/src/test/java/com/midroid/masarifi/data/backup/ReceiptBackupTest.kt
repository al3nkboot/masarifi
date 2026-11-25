package com.midroid.masarifi.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.midroid.masarifi.data.AppDatabase
import com.midroid.masarifi.data.AppLock
import com.midroid.masarifi.data.AppSettings
import com.midroid.masarifi.data.Repository
import com.midroid.masarifi.data.SettingsStore
import com.midroid.masarifi.data.Txn
import com.midroid.masarifi.data.TxnType
import com.midroid.masarifi.warranty.WarrantyImages
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/** 2.9.0: receipt photos and split groups survive backup → restore; backups from older versions still restore. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ReceiptBackupTest {
    @Test fun receiptsAndSplitGroupRoundTripAndOldFilesStillRestore() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val settings = SettingsStore(ctx)
            val repo = Repository(db, settings)
            repo.ensureDefaults()
            val acc = db.accountDao().all().first().id
            val cats = db.categoryDao().all().filter { it.type == TxnType.EXPENSE }.take(2)
            val receipts = WarrantyImages(File(ctx.filesDir, "rc_${System.nanoTime()}"))
            val a = Txn(type = TxnType.EXPENSE, amount = 6_000, accountId = acc, categoryId = cats[0].id, dateTime = 1_790_000_000_000, splitGroup = "g1")
            val b = a.copy(amount = 4_000, categoryId = cats[1].id, uuid = com.midroid.masarifi.data.newUuid())
            repo.saveTxn(a); repo.saveTxn(b)
            receipts.write(receiptName(a.uuid), byteArrayOf(9, 8, 7))
            receipts.write("orphan.jpg", byteArrayOf(1))
            val svc = BackupService(repo, settings, null, receipts) { "2.9.0" }
            val json = BackupCodec.encode(svc.snapshot())
            assertFalse("orphans are not backed up", json.contains("orphan.jpg"))

            // wipe, then restore the 2.9.0 file
            receipts.delete(listOf(receiptName(a.uuid)))
            svc.restore(BackupCodec.decode(json))
            assertArrayEquals(byteArrayOf(9, 8, 7), receipts.read(receiptName(a.uuid)))
            assertNull("orphan pruned on restore", receipts.read("orphan.jpg"))
            val back = db.txnDao().all().filter { it.splitGroup == "g1" }
            assertEquals(setOf(6_000L, 4_000L), back.map { it.amount }.toSet())
            assertEquals(setOf(cats[0].id, cats[1].id), back.map { it.categoryId }.toSet())

            // a 2.8.0-style file (no "receipts", no splitGroup) restores fine and keeps the photo of a transaction it still has
            val old = org.json.JSONObject(json).apply { remove("receipts") }
            val txs = old.getJSONArray("transactions")
            for (i in 0 until txs.length()) txs.getJSONObject(i).remove("splitGroup")
            svc.restore(BackupCodec.decode(old.toString()))
            assertTrue(db.txnDao().all().all { it.splitGroup == null })
            assertArrayEquals(byteArrayOf(9, 8, 7), receipts.read(receiptName(a.uuid)))

            // and the oldest compat fixture (1.7.2) still restores with a receipt store attached
            val compat = javaClass.classLoader!!.getResource("compat/backup-1.7.2.json").readText()
            svc.restore(BackupCodec.decode(compat))
            assertNull("photos of transactions that are gone are dropped", receipts.read(receiptName(a.uuid)))
        } finally { db.close(); SettingsStore(ctx).update { AppSettings() } }
    }

    @Test fun appLockPin() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val lock = AppLock(ctx.getSharedPreferences("lock_test", Context.MODE_PRIVATE))
        assertFalse(lock.enabled); assertFalse(lock.check("1234"))
        assertFalse(AppLock.validPin("12")); assertFalse(AppLock.validPin("12a4")); assertTrue(AppLock.validPin("123456"))
        lock.enable("1234")
        assertTrue(lock.enabled); assertTrue(lock.check("1234")); assertFalse(lock.check("4321"))
        assertFalse(lock.shouldLock(1_000, 1_000 + AppLock.TIMEOUT_MS - 1)); assertTrue(lock.shouldLock(1_000, 1_000 + AppLock.TIMEOUT_MS))
        lock.disable()
        assertFalse(lock.enabled); assertFalse(lock.shouldLock(0, Long.MAX_VALUE))
    }
}
