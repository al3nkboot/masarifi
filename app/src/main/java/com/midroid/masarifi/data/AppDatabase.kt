package com.midroid.masarifi.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Txn::class, Account::class, Category::class, Budget::class, MerchantRule::class, Recurring::class, SmsLog::class, SmsLink::class, DupDismissed::class, SyncTombstone::class, SmsBalance::class],
    version = 4,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun txnDao(): TxnDao
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun budgetDao(): BudgetDao
    abstract fun merchantRuleDao(): MerchantRuleDao
    abstract fun recurringDao(): RecurringDao
    abstract fun smsLogDao(): SmsLogDao
    abstract fun backupDao(): com.midroid.masarifi.data.backup.BackupDao
    abstract fun dupDao(): DupDao
    abstract fun syncDao(): com.midroid.masarifi.data.sync.SyncDao
    abstract fun smsBalanceDao(): SmsBalanceDao

    companion object {
        @Volatile private var instance: AppDatabase? = null
        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "masarifi.db")
                .configure()
                .build().also { instance = it }
        }

        /** Migrations + sync triggers; also used by tests that build in-memory databases. */
        fun RoomDatabase.Builder<AppDatabase>.configure(): RoomDatabase.Builder<AppDatabase> =
            addMigrations(*Migrations.ALL).addCallback(SyncSchema.callback)
    }
}
