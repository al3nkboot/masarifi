package com.midroid.masarifi.warranty

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.midroid.masarifi.data.AppDatabase
import com.midroid.masarifi.data.AppSettings
import com.midroid.masarifi.data.Repository
import com.midroid.masarifi.data.SettingsStore
import com.midroid.masarifi.data.backup.BackupCodec
import com.midroid.masarifi.data.backup.BackupService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class WarrantyTest {
    private val today = LocalDate.of(2026, 11, 8)
    private fun w(daysLeft: Long, id: Long = 0) = Warranty(id = id, name = "TV", purchaseDate = today.minusYears(1).toEpochDay(), expiryDate = today.plusDays(daysLeft).toEpochDay())

    @Test fun expiryAndStatus() {
        assertEquals(LocalDate.of(2027, 5, 8), WarrantyLogic.expiry(today, 6, Warranty.UNIT_MONTHS))
        assertEquals(LocalDate.of(2028, 11, 8), WarrantyLogic.expiry(today, 2, Warranty.UNIT_YEARS))
        assertEquals(WarrantyStatus.ACTIVE, WarrantyLogic.status(w(31), today))
        assertEquals(WarrantyStatus.EXPIRING, WarrantyLogic.status(w(30), today))
        assertEquals(WarrantyStatus.EXPIRING, WarrantyLogic.status(w(0), today))
        assertEquals(WarrantyStatus.EXPIRED, WarrantyLogic.status(w(-1), today))
    }

    @Test fun remindersAt30And7Once() {
        val sent = HashSet<String>()
        fun run(l: List<Warranty>) = WarrantyLogic.reminders(l, today) { x, t -> "${x.id}_$t" in sent }.onEach { (x, t) -> sent += "${x.id}_$t" }.map { it.first.id to it.second }
        assertEquals(listOf(1L to 30, 2L to 7, 3L to 7), run(listOf(w(30, 1), w(7, 2), w(3, 3), w(40, 4), w(-2, 5))))
        assertTrue(run(listOf(w(30, 1), w(7, 2), w(3, 3))).isEmpty())
    }

    @Test fun backupRoundTripWithImages() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val settings = SettingsStore(ctx)
            val repo = Repository(db, settings)
            repo.ensureDefaults()
            val imgs = WarrantyImages(File(ctx.filesDir, "wt_${System.nanoTime()}"))
            imgs.write("inv_a.jpg", byteArrayOf(1, 2, 3))
            val id = repo.warrantyDao.insert(w(100).withImages(listOf("inv_a.jpg")).copy(price = 129_500, brand = "LG", store = "Sharaf DG"))
            val svc = BackupService(repo, settings, imgs) { "t" }
            val json = BackupCodec.encode(svc.snapshot())
            repo.warrantyDao.delete(id); imgs.delete(listOf("inv_a.jpg"))
            svc.restore(BackupCodec.decode(json))
            val back = repo.warrantyDao.all().single()
            assertEquals("Sharaf DG", back.store); assertEquals(129_500L, back.price); assertEquals(listOf("inv_a.jpg"), back.imageList)
            assertArrayEquals(byteArrayOf(1, 2, 3), imgs.read("inv_a.jpg"))
            // a file without warranties keeps the device's warranties
            val old = BackupCodec.decode(json).copy(warranties = null, warrantyImages = null)
            svc.restore(old)
            assertEquals(1, repo.warrantyDao.all().size)
        } finally { db.close(); SettingsStore(ctx).update { AppSettings() } }
    }
}
