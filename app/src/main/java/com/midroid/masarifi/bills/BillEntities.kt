package com.midroid.masarifi.bills

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
import java.time.LocalDate

/**
 * A bill («فاتورة») merged in from Mi5G Bills (2.1.0, schema v7). [amount] in thousandths like all Masarifi amounts.
 * [nextDue] / dates are epoch days (local calendar dates). Recurrence rules are the ones of Mi5G Bills ([Recurrence]).
 * [categoryId] / [accountId] are the Masarifi category / account used when the bill is marked as paid.
 */
@Entity(tableName = "bills", indices = [Index("nextDue"), Index(value = ["uuid"], unique = true)])
data class Bill(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val amount: Long,
    val categoryId: Long? = null,
    val accountId: Long? = null,
    /** [RecurrenceType] name. */
    val recurrence: String = RecurrenceType.MONTHLY.name,
    /** N for CUSTOM_DAYS / CUSTOM_MONTHS, otherwise 1. */
    val recurInterval: Int = 1,
    /** Day of month kept by month-based rules (Jan 31 → Feb 28 → Mar 31). */
    val anchorDay: Int,
    val nextDue: Long,
    val reminderDays: Int = 3,
    val notes: String = "",
    val active: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "") val uuid: String = newUuid(),
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = System.currentTimeMillis(),
) {
    val type: RecurrenceType get() = runCatching { RecurrenceType.valueOf(recurrence) }.getOrDefault(RecurrenceType.MONTHLY)
    val dueDate: LocalDate get() = LocalDate.ofEpochDay(nextDue)
    fun nextAfter(d: LocalDate): LocalDate? = Recurrence.next(d, type, recurInterval, anchorDay)
}

/**
 * One payment of a bill. [txnUuid] = the Masarifi expense transaction that records it (created by «تم الدفع» when
 * [ownTxn], or an existing bank-SMS transaction it was merged with). Imported Mi5G history has no transaction.
 */
@Entity(tableName = "bill_payments", indices = [Index("billId"), Index("txnUuid"), Index(value = ["uuid"], unique = true)])
data class BillPayment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val billId: Long,
    val amount: Long,
    /** The due date (epoch day) this payment covered. */
    val dueDate: Long,
    val paidAt: Long = System.currentTimeMillis(),
    val txnUuid: String? = null,
    val ownTxn: Boolean = false,
    @ColumnInfo(defaultValue = "") val uuid: String = newUuid(),
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = System.currentTimeMillis(),
)

@Dao
interface BillDao {
    @Query("SELECT * FROM bills ORDER BY nextDue, id") fun observeAll(): Flow<List<Bill>>
    @Query("SELECT * FROM bills ORDER BY nextDue, id") suspend fun all(): List<Bill>
    @Query("SELECT * FROM bills WHERE id = :id") suspend fun get(id: Long): Bill?
    @Query("SELECT * FROM bills WHERE uuid = :uuid") suspend fun byUuid(uuid: String): Bill?
    @Insert suspend fun insert(b: Bill): Long
    @Update suspend fun update(b: Bill)
    @Query("DELETE FROM bills WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM bills WHERE uuid = :uuid") suspend fun deleteByUuid(uuid: String)
    @Query("UPDATE bills SET accountId = NULL WHERE accountId = :id") suspend fun clearAccount(id: Long)
    @Query("UPDATE bills SET categoryId = NULL WHERE categoryId = :id") suspend fun clearCategory(id: Long)

    @Query("SELECT * FROM bill_payments ORDER BY paidAt DESC, id DESC") fun observePayments(): Flow<List<BillPayment>>
    @Query("SELECT * FROM bill_payments ORDER BY paidAt DESC, id DESC") suspend fun payments(): List<BillPayment>
    @Query("SELECT * FROM bill_payments WHERE billId = :billId ORDER BY dueDate DESC, id DESC") suspend fun paymentsFor(billId: Long): List<BillPayment>
    @Query("SELECT * FROM bill_payments WHERE id = :id") suspend fun payment(id: Long): BillPayment?
    @Query("SELECT * FROM bill_payments WHERE uuid = :uuid") suspend fun paymentByUuid(uuid: String): BillPayment?
    @Insert suspend fun insertPayment(p: BillPayment): Long
    @Update suspend fun updatePayment(p: BillPayment)
    @Query("DELETE FROM bill_payments WHERE id = :id") suspend fun deletePayment(id: Long)
    @Query("DELETE FROM bill_payments WHERE uuid = :uuid") suspend fun deletePaymentByUuid(uuid: String)
    @Query("DELETE FROM bill_payments WHERE billId = :billId") suspend fun deletePaymentsFor(billId: Long)

    /** Ids of transactions that record a bill payment (used by SMS duplicate matching). */
    @Query("SELECT t.id FROM transactions t JOIN bill_payments p ON p.txnUuid = t.uuid") suspend fun billTxnIds(): List<Long>

    @Query("DELETE FROM bills") suspend fun clearBills()
    @Query("DELETE FROM bill_payments") suspend fun clearPayments()
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertBills(l: List<Bill>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertPayments(l: List<BillPayment>)
}
