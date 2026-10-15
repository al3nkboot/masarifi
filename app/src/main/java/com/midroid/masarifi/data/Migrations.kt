package com.midroid.masarifi.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Non-destructive schema migrations. Schemas are exported to app/schemas and verified by MigrationTest. */
object Migrations {
    /** 1.1.0 (v1) → 1.2.0 (v2): SMS↔manual linking + duplicate review. */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `transactions` ADD COLUMN `smsLinked` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `transactions` ADD COLUMN `possibleDupOf` TEXT")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `sms_links` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `txnId` INTEGER NOT NULL, " +
                    "`smsHash` TEXT NOT NULL, `sender` TEXT NOT NULL, `type` INTEGER NOT NULL, `amount` INTEGER NOT NULL, `accountId` INTEGER NOT NULL, " +
                    "`categoryId` INTEGER, `note` TEXT NOT NULL, `description` TEXT NOT NULL, `dateTime` INTEGER NOT NULL, `merchant` TEXT, " +
                    "`cardLast4` TEXT, `prevMerchant` TEXT, `createdAt` INTEGER NOT NULL)",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_sms_links_txnId` ON `sms_links` (`txnId`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_sms_links_smsHash` ON `sms_links` (`smsHash`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `dup_dismissed` (`pairKey` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`pairKey`))")
        }
    }

    /**
     * 1.2.0 (v2) → 1.3.0 (v3): merge sync. Additive only: adds `uuid` + `updatedAt` columns, assigns a random uuid to
     * every existing row (deterministic ids for the default categories and the default cash account so two devices
     * merge them instead of duplicating), creates `sync_tombstones` and the sync triggers.
     */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            for (t in SyncSchema.UUID_TABLES.keys) {
                db.execSQL("ALTER TABLE `$t` ADD COLUMN `uuid` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `$t` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")
            }
            db.execSQL("ALTER TABLE `budgets` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")
            // Default categories: "cat-<key>" (first row per key only, in case of duplicates)
            db.execSQL("UPDATE categories SET uuid = 'cat-' || `key` WHERE `key` IS NOT NULL AND id = (SELECT MIN(c2.id) FROM categories c2 WHERE c2.`key` = categories.`key`)")
            // Default cash account created by the app on first launch
            db.execSQL("UPDATE accounts SET uuid = '${Defaults.CASH_ACCOUNT_UUID}' WHERE id = (SELECT MIN(id) FROM accounts WHERE groupType = 0 AND name IN ('نقد', 'Cash'))")
            for (t in SyncSchema.UUID_TABLES.keys) {
                db.execSQL("UPDATE `$t` SET uuid = ${SyncSchema.UUID_SQL} WHERE uuid = ''")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_${t}_uuid` ON `$t` (`uuid`)")
            }
            // Best known modification time for rows that have a creation time; others stay 0 ("older than any edit").
            db.execSQL("UPDATE transactions SET updatedAt = createdAt")
            db.execSQL("UPDATE sms_links SET updatedAt = createdAt")
            db.execSQL(SyncSchema.CREATE_TOMBSTONES)
            SyncSchema.createTriggers(db)
        }
    }

    /** 1.3.0 (v3) → 1.4.0 (v4): additive, a local table of account balances reported by bank SMS. */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `sms_balances` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `accountId` INTEGER NOT NULL, " +
                    "`dateTime` INTEGER NOT NULL, `balance` INTEGER NOT NULL, `smsHash` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_sms_balances_accountId_dateTime` ON `sms_balances` (`accountId`, `dateTime`)")
        }
    }

    val ALL = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
}
