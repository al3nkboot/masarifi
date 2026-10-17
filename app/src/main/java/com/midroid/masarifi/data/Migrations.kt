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

    /**
     * 1.4.0 (v4) → 1.5.0 (v5): additive, `card_mappings` (SMS card/account last 4 → account), seeded from what the
     * device already knows: accounts with card digits, then digits recorded on linked SMS. Bank-specific rows and
     * mappings from older auto transactions are added after open by [Repository.seedCardMappings].
     */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(CREATE_CARD_MAPPINGS)
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_card_mappings_last4` ON `card_mappings` (`last4`)")
            val now = System.currentTimeMillis()
            db.execSQL(
                "INSERT INTO card_mappings (bankId, last4, accountId, label, source, createdAt) " +
                    "SELECT NULL, trim(cardLast4), id, name, 1, $now FROM accounts WHERE cardLast4 IS NOT NULL AND trim(cardLast4) <> '' ORDER BY id",
            )
            db.execSQL(
                "INSERT INTO card_mappings (bankId, last4, accountId, label, source, createdAt) " +
                    "SELECT NULL, trim(l.cardLast4), l.accountId, '', 1, $now FROM sms_links l " +
                    "WHERE l.cardLast4 IS NOT NULL AND trim(l.cardLast4) <> '' AND l.accountId IN (SELECT id FROM accounts) " +
                    "AND NOT EXISTS (SELECT 1 FROM card_mappings m WHERE m.last4 = trim(l.cardLast4)) " +
                    "AND l.id = (SELECT MAX(l2.id) FROM sms_links l2 WHERE trim(l2.cardLast4) = trim(l.cardLast4))",
            )
        }
    }

    const val CREATE_CARD_MAPPINGS = "CREATE TABLE IF NOT EXISTS `card_mappings` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `bankId` TEXT, " +
        "`last4` TEXT NOT NULL, `accountId` INTEGER, `label` TEXT NOT NULL, `source` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)"

    /** 1.5.0 (v5) → 1.6.0 (v6): additive, `repair_batches` (undo snapshots of the SMS repair tool). */
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `repair_batches` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `createdAt` INTEGER NOT NULL, " +
                    "`changes` INTEGER NOT NULL, `snapshot` TEXT NOT NULL, `undone` INTEGER NOT NULL)",
            )
        }
    }

    const val CREATE_BILLS = "CREATE TABLE IF NOT EXISTS `bills` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `amount` INTEGER NOT NULL, " +
        "`categoryId` INTEGER, `accountId` INTEGER, `recurrence` TEXT NOT NULL, `recurInterval` INTEGER NOT NULL, `anchorDay` INTEGER NOT NULL, " +
        "`nextDue` INTEGER NOT NULL, `reminderDays` INTEGER NOT NULL, `notes` TEXT NOT NULL, `active` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, " +
        "`uuid` TEXT NOT NULL DEFAULT '', `updatedAt` INTEGER NOT NULL DEFAULT 0)"
    const val CREATE_BILL_PAYMENTS = "CREATE TABLE IF NOT EXISTS `bill_payments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `billId` INTEGER NOT NULL, " +
        "`amount` INTEGER NOT NULL, `dueDate` INTEGER NOT NULL, `paidAt` INTEGER NOT NULL, `txnUuid` TEXT, `ownTxn` INTEGER NOT NULL, " +
        "`uuid` TEXT NOT NULL DEFAULT '', `updatedAt` INTEGER NOT NULL DEFAULT 0)"

    /**
     * 2.0.0 (v6) → 2.1.0 (v7): additive only. New `bills` + `bill_payments` tables (bills merged in from Mi5G Bills),
     * their indices and sync triggers. No existing table or row is touched.
     */
    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(CREATE_BILLS)
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_bills_nextDue` ON `bills` (`nextDue`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_bills_uuid` ON `bills` (`uuid`)")
            db.execSQL(CREATE_BILL_PAYMENTS)
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_bill_payments_billId` ON `bill_payments` (`billId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_bill_payments_txnUuid` ON `bill_payments` (`txnUuid`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_bill_payments_uuid` ON `bill_payments` (`uuid`)")
            SyncSchema.createBillTriggers(db)
        }
    }

    val ALL = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
}
