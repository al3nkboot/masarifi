package com.midroid.masarifi.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TxnDao {
    @Query("SELECT * FROM transactions ORDER BY dateTime DESC, id DESC")
    fun observeAll(): Flow<List<Txn>>

    @Query("SELECT * FROM transactions ORDER BY dateTime ASC, id ASC")
    suspend fun all(): List<Txn>

    @Query("SELECT * FROM transactions WHERE dateTime >= :from AND dateTime < :to ORDER BY dateTime DESC")
    suspend fun between(from: Long, to: Long): List<Txn>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun get(id: Long): Txn?

    @Query("SELECT smsHash FROM transactions WHERE smsHash LIKE :prefix || '%'")
    suspend fun hashesWithPrefix(prefix: String): List<String>

    @Query("SELECT COUNT(*) FROM transactions WHERE smsHash = :hash")
    suspend fun countByHash(hash: String): Int

    @Query("SELECT COUNT(*) FROM transactions WHERE accountId = :accountId OR toAccountId = :accountId")
    suspend fun countForAccount(accountId: Long): Int

    @Query("DELETE FROM transactions WHERE accountId = :accountId OR toAccountId = :accountId")
    suspend fun deleteForAccount(accountId: Long)

    @Query("UPDATE transactions SET categoryId = NULL WHERE categoryId = :categoryId")
    suspend fun clearCategory(categoryId: Long)

    @Query("UPDATE transactions SET recurringId = NULL WHERE recurringId = :recurringId")
    suspend fun unlinkRecurring(recurringId: Long)

    @Query("SELECT MAX(dateTime) FROM transactions WHERE recurringId = :recurringId")
    suspend fun latestOccurrence(recurringId: Long): Long?

    @Query("SELECT * FROM transactions WHERE id IN (:ids)")
    suspend fun byIds(ids: List<Long>): List<Txn>

    @Query("DELETE FROM transactions WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>): Int

    /** Re-inserts rows with their original ids (undo delete). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun restore(txns: List<Txn>): List<Long>

    @Insert
    suspend fun insert(txn: Txn): Long

    @Insert
    suspend fun insertAll(txns: List<Txn>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(txn: Txn): Long

    @Update
    suspend fun update(txn: Txn)

    @Delete
    suspend fun delete(txn: Txn)
}

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<Account>>

    @Query("SELECT * FROM accounts ORDER BY sortOrder, id")
    suspend fun all(): List<Account>

    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun count(): Int

    @Insert
    suspend fun insert(a: Account): Long

    @Update
    suspend fun update(a: Account)

    @Delete
    suspend fun delete(a: Account)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY type, sortOrder, id")
    fun observeAll(): Flow<List<Category>>

    @Query("SELECT * FROM categories ORDER BY type, sortOrder, id")
    suspend fun all(): List<Category>

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Insert
    suspend fun insert(c: Category): Long

    @Insert
    suspend fun insertAll(c: List<Category>)

    @Update
    suspend fun update(c: Category)

    @Delete
    suspend fun delete(c: Category)
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets")
    fun observeAll(): Flow<List<Budget>>

    @Query("SELECT * FROM budgets")
    suspend fun all(): List<Budget>

    @Upsert
    suspend fun upsert(b: Budget)

    @Query("DELETE FROM budgets WHERE categoryId = :categoryId")
    suspend fun delete(categoryId: Long)
}

@Dao
interface MerchantRuleDao {
    @Query("SELECT * FROM merchant_rules WHERE merchantKey = :key")
    suspend fun get(key: String): MerchantRule?

    @Query("SELECT * FROM merchant_rules ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<MerchantRule>>

    @Upsert
    suspend fun upsert(r: MerchantRule)

    @Query("DELETE FROM merchant_rules WHERE merchantKey = :key")
    suspend fun delete(key: String)
}

@Dao
interface RecurringDao {
    @Query("SELECT * FROM recurring ORDER BY nextDate")
    fun observeAll(): Flow<List<Recurring>>

    @Query("SELECT * FROM recurring WHERE active = 1 AND nextDate <= :now")
    suspend fun due(now: Long): List<Recurring>

    @Query("SELECT * FROM recurring WHERE id = :id")
    suspend fun get(id: Long): Recurring?

    @Insert
    suspend fun insert(r: Recurring): Long

    @Update
    suspend fun update(r: Recurring)

    @Query("DELETE FROM recurring WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface SmsLogDao {
    @Query("SELECT COUNT(*) FROM sms_log WHERE hash = :hash")
    suspend fun exists(hash: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(log: SmsLog): Long

    @Query("SELECT COUNT(*) FROM sms_log WHERE status = 1")
    fun observeImportedCount(): Flow<Int>
}

@Dao
interface DupDao {
    @Query("SELECT * FROM sms_links WHERE txnId = :txnId ORDER BY id DESC")
    suspend fun linksFor(txnId: Long): List<SmsLink>

    @Query("SELECT * FROM sms_links WHERE id = :id")
    suspend fun link(id: Long): SmsLink?

    @Query("SELECT COUNT(*) FROM sms_links WHERE smsHash = :hash")
    suspend fun countLinkHash(hash: String): Int

    @Insert
    suspend fun insertLink(l: SmsLink): Long

    @Query("DELETE FROM sms_links WHERE id = :id")
    suspend fun deleteLink(id: Long)

    @Query("UPDATE sms_links SET txnId = :keeper WHERE txnId IN (:ids)")
    suspend fun repointLinks(ids: List<Long>, keeper: Long)

    @Query("SELECT pairKey FROM dup_dismissed")
    fun observeDismissed(): Flow<List<String>>

    @Query("SELECT pairKey FROM dup_dismissed")
    suspend fun dismissed(): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun dismiss(d: List<DupDismissed>)
}

@Dao
interface SmsBalanceDao {
    @Insert
    suspend fun insert(b: SmsBalance): Long

    /** Latest balance of [accountId] reported at or before [time]. */
    @Query("SELECT * FROM sms_balances WHERE accountId = :accountId AND dateTime <= :time ORDER BY dateTime DESC, id DESC LIMIT 1")
    suspend fun latestBefore(accountId: Long, time: Long): SmsBalance?

    @Query("SELECT * FROM sms_balances WHERE accountId = :accountId ORDER BY dateTime DESC, id DESC LIMIT 1")
    suspend fun latest(accountId: Long): SmsBalance?

    @Query("SELECT b.* FROM sms_balances b WHERE b.id = (SELECT b2.id FROM sms_balances b2 WHERE b2.accountId = b.accountId ORDER BY b2.dateTime DESC, b2.id DESC LIMIT 1)")
    fun observeLatest(): Flow<List<SmsBalance>>

    @Query("DELETE FROM sms_balances WHERE accountId = :accountId")
    suspend fun deleteForAccount(accountId: Long)

    @Query("DELETE FROM sms_balances")
    suspend fun clear()
}
