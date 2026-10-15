package com.midroid.masarifi.data.sync

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.midroid.masarifi.data.Account
import com.midroid.masarifi.data.Category
import com.midroid.masarifi.data.Recurring
import com.midroid.masarifi.data.SmsLink
import com.midroid.masarifi.data.SyncTombstone
import com.midroid.masarifi.data.Txn

@Dao
interface SyncDao {
    @Query("SELECT * FROM sync_tombstones") suspend fun tombstones(): List<SyncTombstone>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putTombstone(t: SyncTombstone)
    @Query("DELETE FROM sync_tombstones WHERE uuid = :uuid") suspend fun dropTombstone(uuid: String)

    @Query("SELECT * FROM transactions WHERE uuid = :uuid") suspend fun txn(uuid: String): Txn?
    @Query("SELECT * FROM accounts WHERE uuid = :uuid") suspend fun account(uuid: String): Account?
    @Query("SELECT * FROM categories WHERE uuid = :uuid") suspend fun category(uuid: String): Category?
    @Query("SELECT * FROM recurring WHERE uuid = :uuid") suspend fun recurring(uuid: String): Recurring?
    @Query("SELECT * FROM sms_links WHERE uuid = :uuid") suspend fun smsLink(uuid: String): SmsLink?

    @Query("DELETE FROM transactions WHERE uuid = :uuid") suspend fun deleteTxn(uuid: String)
    @Query("DELETE FROM accounts WHERE uuid = :uuid") suspend fun deleteAccount(uuid: String)
    @Query("DELETE FROM categories WHERE uuid = :uuid") suspend fun deleteCategory(uuid: String)
    @Query("DELETE FROM recurring WHERE uuid = :uuid") suspend fun deleteRecurring(uuid: String)
    @Query("DELETE FROM sms_links WHERE uuid = :uuid") suspend fun deleteSmsLink(uuid: String)

    @Query("UPDATE transactions SET categoryId = NULL WHERE categoryId = :id") suspend fun clearTxnCategory(id: Long)
    @Query("UPDATE recurring SET categoryId = NULL WHERE categoryId = :id") suspend fun clearRecurringCategory(id: Long)
    @Query("UPDATE transactions SET recurringId = NULL WHERE recurringId = :id") suspend fun clearTxnRecurring(id: Long)

    @Insert suspend fun insertTxn(t: Txn): Long
    @Insert suspend fun insertAccount(a: Account): Long
    @Insert suspend fun insertCategory(c: Category): Long
    @Insert suspend fun insertRecurring(r: Recurring): Long
    @Insert suspend fun insertSmsLink(l: SmsLink): Long
    @androidx.room.Update suspend fun updateTxn(t: Txn)
    @androidx.room.Update suspend fun updateAccount(a: Account)
    @androidx.room.Update suspend fun updateCategory(c: Category)
    @androidx.room.Update suspend fun updateRecurring(r: Recurring)
    @androidx.room.Update suspend fun updateSmsLink(l: SmsLink)

    /** Latest local change time (any synced table or deletion); used to skip auto-syncs when nothing changed. */
    @Query(
        "SELECT MAX(m) FROM (SELECT MAX(updatedAt) AS m FROM transactions UNION ALL SELECT MAX(updatedAt) FROM accounts " +
            "UNION ALL SELECT MAX(updatedAt) FROM categories UNION ALL SELECT MAX(updatedAt) FROM recurring " +
            "UNION ALL SELECT MAX(updatedAt) FROM sms_links UNION ALL SELECT MAX(updatedAt) FROM budgets " +
            "UNION ALL SELECT MAX(updatedAt) FROM merchant_rules UNION ALL SELECT MAX(deletedAt) FROM sync_tombstones)",
    )
    suspend fun localMark(): Long?

    @Query("SELECT COUNT(*) FROM sms_log") suspend fun smsLogCount(): Int
    @Query("SELECT COUNT(*) FROM dup_dismissed") suspend fun dismissedCount(): Int
}
