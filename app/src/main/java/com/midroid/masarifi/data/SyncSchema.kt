package com.midroid.masarifi.data

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * SQL used by merge sync. Triggers keep sync metadata correct no matter which code path changes data:
 *  - AFTER UPDATE: if the writer did not change `updatedAt`, bump it to "now" (every edit becomes a newer version);
 *  - AFTER DELETE: record a tombstone in `sync_tombstones` so the delete propagates to the other device;
 *  - AFTER INSERT of a uuid that has a tombstone (e.g. undo delete / restore): drop the tombstone and bump `updatedAt`
 *    so the re-created row wins over the delete that may already have been synced.
 * Sync itself writes remote values with their own `updatedAt` (different from the old one), so it never triggers a bump.
 */
object SyncSchema {
    /** Current time in epoch millis, computed by SQLite. */
    const val NOW = "CAST((julianday('now') - 2440587.5) * 86400000 AS INTEGER)"

    /** SQL expression that yields a random RFC-4122 v4 UUID string (evaluated per row). */
    const val UUID_SQL = "lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-4' || substr(lower(hex(randomblob(2))), 2) || '-' || " +
        "substr('89ab', 1 + (abs(random()) % 4), 1) || substr(lower(hex(randomblob(2))), 2) || '-' || lower(hex(randomblob(6)))"

    /** Synced tables with an integer `id` + `uuid` + `updatedAt`, and their entity kind in the sync file. */
    val UUID_TABLES = linkedMapOf(
        "categories" to "category", "accounts" to "account", "recurring" to "recurring",
        "transactions" to "txn", "sms_links" to "smslink",
    )

    const val CREATE_TOMBSTONES =
        "CREATE TABLE IF NOT EXISTS `sync_tombstones` (`uuid` TEXT NOT NULL, `entity` TEXT NOT NULL, `deletedAt` INTEGER NOT NULL, PRIMARY KEY(`uuid`))"

    private const val BUDGET_KEY_OLD =
        "'b:' || COALESCE((SELECT uuid FROM categories WHERE id = OLD.categoryId), CASE WHEN OLD.categoryId = 0 THEN 'overall' ELSE 'id' || OLD.categoryId END)"
    private const val BUDGET_KEY_NEW =
        "'b:' || COALESCE((SELECT uuid FROM categories WHERE id = NEW.categoryId), CASE WHEN NEW.categoryId = 0 THEN 'overall' ELSE 'id' || NEW.categoryId END)"

    fun triggers(): List<String> = buildList {
        for ((t, kind) in UUID_TABLES) {
            add("CREATE TRIGGER IF NOT EXISTS `sync_upd_$t` AFTER UPDATE ON `$t` WHEN NEW.updatedAt = OLD.updatedAt BEGIN " +
                "UPDATE `$t` SET updatedAt = MAX($NOW, OLD.updatedAt + 1) WHERE id = NEW.id; END")
            add("CREATE TRIGGER IF NOT EXISTS `sync_del_$t` AFTER DELETE ON `$t` BEGIN " +
                "INSERT OR REPLACE INTO sync_tombstones(uuid, entity, deletedAt) VALUES (OLD.uuid, '$kind', $NOW); END")
            add("CREATE TRIGGER IF NOT EXISTS `sync_ins_$t` AFTER INSERT ON `$t` WHEN EXISTS (SELECT 1 FROM sync_tombstones WHERE uuid = NEW.uuid) BEGIN " +
                "UPDATE `$t` SET updatedAt = MAX($NOW, NEW.updatedAt) WHERE id = NEW.id; DELETE FROM sync_tombstones WHERE uuid = NEW.uuid; END")
        }
        // budgets: identity = category uuid
        add("CREATE TRIGGER IF NOT EXISTS `sync_upd_budgets` AFTER UPDATE ON `budgets` WHEN NEW.updatedAt = OLD.updatedAt BEGIN " +
            "UPDATE `budgets` SET updatedAt = MAX($NOW, OLD.updatedAt + 1) WHERE categoryId = NEW.categoryId; END")
        add("CREATE TRIGGER IF NOT EXISTS `sync_del_budgets` AFTER DELETE ON `budgets` BEGIN " +
            "INSERT OR REPLACE INTO sync_tombstones(uuid, entity, deletedAt) VALUES ($BUDGET_KEY_OLD, 'budget', $NOW); END")
        add("CREATE TRIGGER IF NOT EXISTS `sync_ins_budgets` AFTER INSERT ON `budgets` WHEN EXISTS (SELECT 1 FROM sync_tombstones WHERE uuid = $BUDGET_KEY_NEW) BEGIN " +
            "UPDATE `budgets` SET updatedAt = MAX($NOW, NEW.updatedAt) WHERE categoryId = NEW.categoryId; DELETE FROM sync_tombstones WHERE uuid = $BUDGET_KEY_NEW; END")
        // merchant rules: identity = merchantKey (updatedAt is already maintained by the app)
        add("CREATE TRIGGER IF NOT EXISTS `sync_del_merchant_rules` AFTER DELETE ON `merchant_rules` BEGIN " +
            "INSERT OR REPLACE INTO sync_tombstones(uuid, entity, deletedAt) VALUES ('m:' || OLD.merchantKey, 'merchant', $NOW); END")
        add("CREATE TRIGGER IF NOT EXISTS `sync_ins_merchant_rules` AFTER INSERT ON `merchant_rules` BEGIN " +
            "DELETE FROM sync_tombstones WHERE uuid = 'm:' || NEW.merchantKey; END")
    }

    fun createTriggers(db: SupportSQLiteDatabase) = triggers().forEach { db.execSQL(it) }

    /** Installs the triggers on fresh databases (migrations install them for upgraded ones). */
    val callback = object : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) = createTriggers(db)
        override fun onOpen(db: SupportSQLiteDatabase) = createTriggers(db) // IF NOT EXISTS: no-op normally
    }
}
