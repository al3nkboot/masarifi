package com.midroid.masarifi.data.backup

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.midroid.masarifi.data.Account
import com.midroid.masarifi.data.Budget
import com.midroid.masarifi.data.Category
import com.midroid.masarifi.data.MerchantRule
import com.midroid.masarifi.data.Recurring
import com.midroid.masarifi.data.SmsLog
import com.midroid.masarifi.data.SmsLink
import com.midroid.masarifi.data.DupDismissed
import com.midroid.masarifi.data.Txn

/** Bulk read/replace of every table, used by full backup & restore. */
@Dao
interface BackupDao {
    @Query("SELECT * FROM accounts") suspend fun accounts(): List<Account>
    @Query("SELECT * FROM categories") suspend fun categories(): List<Category>
    @Query("SELECT * FROM transactions") suspend fun transactions(): List<Txn>
    @Query("SELECT * FROM recurring") suspend fun recurring(): List<Recurring>
    @Query("SELECT * FROM budgets") suspend fun budgets(): List<Budget>
    @Query("SELECT * FROM merchant_rules") suspend fun merchantRules(): List<MerchantRule>
    @Query("SELECT * FROM sms_log") suspend fun smsLog(): List<SmsLog>

    @Query("SELECT * FROM sms_links") suspend fun smsLinks(): List<SmsLink>
    @Query("SELECT * FROM dup_dismissed") suspend fun dupDismissed(): List<DupDismissed>
    @Query("DELETE FROM sms_links") suspend fun clearSmsLinks()
    @Query("DELETE FROM dup_dismissed") suspend fun clearDupDismissed()
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertSmsLinks(l: List<SmsLink>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertDupDismissed(l: List<DupDismissed>)

    @Query("SELECT * FROM card_mappings ORDER BY id") suspend fun cardMappings(): List<com.midroid.masarifi.data.CardMapping>
    @Query("DELETE FROM card_mappings") suspend fun clearCardMappings()
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertCardMappings(l: List<com.midroid.masarifi.data.CardMapping>)
    @Query("DELETE FROM repair_batches") suspend fun clearRepairBatches()
    @Query("DELETE FROM transactions") suspend fun clearTransactions()
    @Query("DELETE FROM accounts") suspend fun clearAccounts()
    @Query("DELETE FROM categories") suspend fun clearCategories()
    @Query("DELETE FROM recurring") suspend fun clearRecurring()
    @Query("DELETE FROM budgets") suspend fun clearBudgets()
    @Query("DELETE FROM merchant_rules") suspend fun clearMerchantRules()
    @Query("DELETE FROM sms_log") suspend fun clearSmsLog()

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAccounts(l: List<Account>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertCategories(l: List<Category>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertTransactions(l: List<Txn>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertRecurring(l: List<Recurring>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertBudgets(l: List<Budget>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertMerchantRules(l: List<MerchantRule>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertSmsLog(l: List<SmsLog>)
}
