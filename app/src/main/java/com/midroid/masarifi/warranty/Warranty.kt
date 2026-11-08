package com.midroid.masarifi.warranty

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import com.midroid.masarifi.data.newUuid
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * A product warranty («الضمان», 2.5.0, schema v8). Dates are epoch days. [price] in thousandths like all amounts.
 * [images] = JSON array of invoice photo file names inside app-private `files/warranty_images/`.
 */
@Entity(tableName = "warranties", indices = [Index("expiryDate"), Index(value = ["uuid"], unique = true)])
data class Warranty(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** [WarrantyCategory] ordinal. */
    val category: Int = WarrantyCategory.ELECTRONICS.ordinal,
    val brand: String = "",
    val store: String = "",
    val purchaseDate: Long,
    val durationValue: Int = 1,
    /** 0 = months, 1 = years. */
    val durationUnit: Int = UNIT_YEARS,
    val expiryDate: Long,
    val price: Long? = null,
    val notes: String = "",
    val images: String = "[]",
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "") val uuid: String = newUuid(),
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = System.currentTimeMillis(),
) {
    val cat: WarrantyCategory get() = WarrantyCategory.entries.getOrElse(category) { WarrantyCategory.OTHER }
    val imageList: List<String> get() = runCatching { JSONArray(images).let { a -> (0 until a.length()).map { a.getString(it) } } }.getOrDefault(emptyList())
    fun withImages(l: List<String>) = copy(images = JSONArray(l).toString())

    companion object {
        const val UNIT_MONTHS = 0
        const val UNIT_YEARS = 1
    }
}

enum class WarrantyCategory { APPLIANCES, ELECTRONICS, OTHER }
enum class WarrantyStatus { ACTIVE, EXPIRING, EXPIRED }

object WarrantyLogic {
    const val SOON_DAYS = 30
    val REMIND_AT = listOf(30, 7)

    fun expiry(purchase: LocalDate, value: Int, unit: Int): LocalDate =
        if (unit == Warranty.UNIT_MONTHS) purchase.plusMonths(value.toLong()) else purchase.plusYears(value.toLong())

    fun daysLeft(w: Warranty, today: LocalDate): Long = ChronoUnit.DAYS.between(today, LocalDate.ofEpochDay(w.expiryDate))

    fun status(w: Warranty, today: LocalDate): WarrantyStatus {
        val d = daysLeft(w, today)
        return when { d < 0 -> WarrantyStatus.EXPIRED; d <= SOON_DAYS -> WarrantyStatus.EXPIRING; else -> WarrantyStatus.ACTIVE }
    }

    /** Warranties to notify about today: exactly 30 or 7 days left (or the first day inside a window that was missed, once). */
    fun reminders(all: List<Warranty>, today: LocalDate, alreadySent: (Warranty, Int) -> Boolean): List<Pair<Warranty, Int>> =
        all.mapNotNull { w ->
            val d = daysLeft(w, today)
            if (d < 0) return@mapNotNull null
            // smallest threshold the item is inside; covers days the worker did not run
            val t = REMIND_AT.sorted().firstOrNull { d <= it } ?: return@mapNotNull null
            if (alreadySent(w, t)) null else w to t
        }

    /** Sort: expiring first, then active by expiry, expired last (most recent first). */
    fun sorted(all: List<Warranty>, today: LocalDate): List<Warranty> = all.sortedWith(
        compareBy<Warranty> { when (status(it, today)) { WarrantyStatus.EXPIRING -> 0; WarrantyStatus.ACTIVE -> 1; WarrantyStatus.EXPIRED -> 2 } }
            .thenBy { if (status(it, today) == WarrantyStatus.EXPIRED) -it.expiryDate else it.expiryDate },
    )
}

@Dao
interface WarrantyDao {
    @Query("SELECT * FROM warranties ORDER BY expiryDate, id") fun observeAll(): Flow<List<Warranty>>
    @Query("SELECT * FROM warranties ORDER BY expiryDate, id") suspend fun all(): List<Warranty>
    @Query("SELECT * FROM warranties WHERE id = :id") suspend fun get(id: Long): Warranty?
    @Insert suspend fun insert(w: Warranty): Long
    @Update suspend fun update(w: Warranty)
    @Query("DELETE FROM warranties WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM warranties") suspend fun clear()
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(l: List<Warranty>)
}
