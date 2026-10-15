package com.midroid.masarifi.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/**
 * Builds a real database with the exported 1.1.0 schema (schemas/.../1.json), fills it with data, then opens it with the
 * current Room database + MIGRATION_1_2. Room validates the migrated schema against the v2 entities on open.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class MigrationTest {
    private val schemaDir = File("schemas/com.midroid.masarifi.data.AppDatabase")

    private fun createV1(file: File) = createFrom(file, 1)

    /** Creates an empty database exactly as Room created it for schema [version] (from the exported JSON). */
    private fun createSchema(file: File, version: Int): SQLiteDatabase {
        val json = JSONObject(File(schemaDir, "$version.json").readText()).getJSONObject("database")
        assertEquals(version, json.getInt("version"))
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        val entities = json.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val e = entities.getJSONObject(i)
            val table = e.getString("tableName")
            db.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", table))
            val idx = e.optJSONArray("indices")
            if (idx != null) for (j in 0 until idx.length()) db.execSQL(idx.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
        }
        val setup = json.getJSONArray("setupQueries")
        for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
        return db
    }

    private fun createFrom(file: File, version: Int) {
        val db = createSchema(file, version)
        val uuidTables = listOf("transactions", "accounts", "categories", "recurring", "sms_links")
        // v3+: unique uuid indices → fill uuids after inserting the fixture rows
        if (version >= 3) uuidTables.forEach { db.execSQL("DROP INDEX IF EXISTS `index_${it}_uuid`") }
        // 1.1.0 data: an account, a category, a manual txn, an imported (mm:) txn and an SMS txn
        db.execSQL("INSERT INTO accounts (id, name, groupType, initialBalance, cardLast4, includeInTotal, sortOrder) VALUES (1, 'نقد', 0, 0, NULL, 1, 0)")
        db.execSQL("INSERT INTO categories (id, type, nameAr, nameEn, emoji, `key`, sortOrder) VALUES (1, 1, 'طعام', 'Food', '🍜', 'food', 1)")
        db.execSQL("INSERT INTO transactions (id, type, amount, accountId, toAccountId, categoryId, note, description, dateTime, isAuto, smsHash, merchant, recurringId, createdAt) VALUES (1, 1, 4300, 1, NULL, 1, 'غداء', '', 1790000000000, 0, NULL, NULL, NULL, 1)")
        db.execSQL("INSERT INTO transactions (id, type, amount, accountId, toAccountId, categoryId, note, description, dateTime, isAuto, smsHash, merchant, recurringId, createdAt) VALUES (2, 1, 1500, 1, NULL, 1, '', '', 1790000100000, 0, 'mm:abc', NULL, NULL, 2)")
        db.execSQL("INSERT INTO transactions (id, type, amount, accountId, toAccountId, categoryId, note, description, dateTime, isAuto, smsHash, merchant, recurringId, createdAt) VALUES (3, 1, 7000, 1, NULL, 1, 'LULU', 'sms body', 1790000200000, 1, 'h3', 'LULU', NULL, 3)")
        db.execSQL("INSERT INTO sms_log (hash, sender, receivedAt, status, txnId) VALUES ('h3', 'BankMuscat', 1790000200000, 1, 3)")
        db.execSQL("INSERT INTO budgets (categoryId, amount) VALUES (0, 300000)")
        if (version >= 2) {
            db.execSQL("UPDATE transactions SET smsLinked = 1 WHERE id = 1")
            db.execSQL("INSERT INTO sms_links (id, txnId, smsHash, sender, type, amount, accountId, categoryId, note, description, dateTime, merchant, cardLast4, prevMerchant, createdAt) VALUES (1, 1, 'h1', 'BankMuscat', 1, 4300, 1, 1, 'n', 'b', 1790000000000, 'X', '0099', NULL, 5)")
            db.execSQL("INSERT INTO categories (id, type, nameAr, nameEn, emoji, `key`, sortOrder) VALUES (2, 1, 'مخصص', 'Custom', '📦', NULL, 50)")
            db.execSQL("INSERT INTO recurring (id, type, amount, accountId, toAccountId, categoryId, note, frequency, nextDate, active) VALUES (1, 0, 850000, 1, NULL, NULL, 'راتب', 3, 1795000000000, 1)")
            db.execSQL("INSERT INTO dup_dismissed (pairKey, createdAt) VALUES ('1-3', 9)")
        }
        if (version >= 3) uuidTables.forEach { t ->
            db.execSQL("UPDATE `$t` SET uuid = lower(hex(randomblob(16))) WHERE uuid = ''")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_${t}_uuid` ON `$t` (`uuid`)")
        }
        db.version = version
        db.close()
    }

    @Test fun migrate1To2KeepsAllData() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-test.db"
        val file = ctx.getDatabasePath(name).apply { parentFile?.mkdirs(); delete() }
        createV1(file)

        val db = Room.databaseBuilder(ctx, AppDatabase::class.java, name).addMigrations(*Migrations.ALL).allowMainThreadQueries().build()
        try {
            val txns = db.txnDao().all()
            assertEquals(3, txns.size)
            assertEquals("غداء", txns[0].note)
            assertEquals("mm:abc", txns[1].smsHash)
            assertEquals("h3", txns[2].smsHash)
            assertTrue(txns[2].isAuto)
            assertTrue(txns.none { it.smsLinked })
            assertTrue(txns.all { it.possibleDupOf == null })
            assertEquals(1, db.accountDao().count())
            assertEquals(300000L, db.budgetDao().all().single().amount)
            assertEquals(1, db.smsLogDao().exists("h3"))
            // new tables are usable
            val linkId = db.dupDao().insertLink(SmsLink(txnId = 1, smsHash = "h9", type = 1, amount = 4300, accountId = 1, dateTime = 1))
            assertTrue(linkId > 0)
            db.dupDao().dismiss(listOf(DupDismissed(DupDismissed.key(1, 3))))
            assertEquals(listOf("1-3"), db.dupDao().dismissed())
            assertEquals(4, db.openHelper.readableDatabase.version)
        } finally {
            db.close()
        }
    }

    @Test fun exportedV2SchemaMatchesMigrationSql() {
        val v2 = JSONObject(File(schemaDir, "2.json").readText()).getJSONObject("database")
        assertEquals(2, v2.getInt("version"))
        val tables = (0 until v2.getJSONArray("entities").length()).map { v2.getJSONArray("entities").getJSONObject(it).getString("tableName") }
        assertTrue(tables.containsAll(listOf("sms_links", "dup_dismissed")))
        assertFalse(v2.toString().contains("fallbackToDestructive"))
        assertNull(null)
    }

    @Test fun migrate2To3AssignsUuidsAndInstallsSyncTriggers() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-test-v2.db"
        val file = ctx.getDatabasePath(name).apply { parentFile?.mkdirs(); delete() }
        createFrom(file, 2)

        val db = Room.databaseBuilder(ctx, AppDatabase::class.java, name).addMigrations(*Migrations.ALL).allowMainThreadQueries().build()
        try {
            // all data kept
            val txns = db.txnDao().all()
            assertEquals(listOf(4300L, 1500L, 7000L), txns.map { it.amount })
            assertEquals(listOf(null, "mm:abc", "h3"), txns.map { it.smsHash })
            assertTrue(txns[0].smsLinked)
            assertEquals(1, db.dupDao().linksFor(1).size)
            assertEquals(listOf("1-3"), db.dupDao().dismissed())
            assertEquals(850000L, db.recurringDao().get(1)!!.amount)
            // every synced row got a uuid; uuids are unique and v4-shaped
            val uuidRe = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")
            val all = txns.map { it.uuid } + db.backupDao().smsLinks().map { it.uuid } + db.backupDao().recurring().map { it.uuid }
            assertTrue(all.all { uuidRe.matches(it) })
            assertEquals(all.size, all.toSet().size)
            // deterministic ids for defaults so two independently set-up phones merge them
            val cats = db.categoryDao().all()
            assertEquals("cat-food", cats.first { it.key == "food" }.uuid)
            assertTrue(uuidRe.matches(cats.first { it.key == null }.uuid))
            assertEquals(Defaults.CASH_ACCOUNT_UUID, db.accountDao().all().single().uuid)
            // versions: rows with a creation time use it, others start at 0
            assertEquals(listOf(1L, 2L, 3L), txns.map { it.updatedAt })
            assertEquals(0L, cats.first().updatedAt)
            // triggers: update bumps updatedAt, delete leaves a tombstone
            val sql = db.openHelper.writableDatabase
            sql.query("SELECT COUNT(*) FROM sqlite_master WHERE type = 'trigger' AND name LIKE 'sync_%'").use { c -> c.moveToFirst(); assertEquals(SyncSchema.triggers().size, c.getInt(0)) }
            db.txnDao().update(txns[1].copy(note = "x"))
            assertTrue(db.txnDao().get(2)!!.updatedAt > 1_000_000_000_000L)
            db.txnDao().delete(txns[2])
            assertEquals(listOf(txns[2].uuid), db.syncDao().tombstones().map { it.uuid })
            // new rows get uuids from the entity defaults
            val id = db.txnDao().insert(Txn(type = 1, amount = 1, accountId = 1, dateTime = 1))
            assertTrue(uuidRe.matches(db.txnDao().get(id)!!.uuid))
            assertEquals(4, sql.version)
        } finally {
            db.close()
        }
    }

    @Test fun exportedV3SchemaHasSyncColumns() {
        val v3 = JSONObject(File(schemaDir, "3.json").readText()).getJSONObject("database")
        assertEquals(3, v3.getInt("version"))
        val text = v3.toString()
        assertTrue(text.contains("sync_tombstones"))
        assertTrue(text.contains("index_transactions_uuid"))
    }

    @Test fun migrate3To4AddsSmsBalances() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-test-v3.db"
        val file = ctx.getDatabasePath(name).apply { parentFile?.mkdirs(); delete() }
        createFrom(file, 3)
        val db = Room.databaseBuilder(ctx, AppDatabase::class.java, name).addMigrations(*Migrations.ALL).allowMainThreadQueries().build()
        try {
            val txns = db.txnDao().all()
            assertEquals(listOf(4300L, 1500L, 7000L), txns.map { it.amount })
            assertEquals(1, db.dupDao().linksFor(1).size)
            assertEquals(850000L, db.recurringDao().get(1)!!.amount)
            assertTrue(db.smsBalanceDao().latest(1) == null)
            db.smsBalanceDao().insert(SmsBalance(accountId = 1, dateTime = 10, balance = 5_000, smsHash = "h"))
            db.smsBalanceDao().insert(SmsBalance(accountId = 1, dateTime = 20, balance = 205_000, smsHash = "h2"))
            assertEquals(5_000L, db.smsBalanceDao().latestBefore(1, 15)!!.balance)
            assertEquals(205_000L, db.smsBalanceDao().latest(1)!!.balance)
            assertEquals(4, db.openHelper.readableDatabase.version)
        } finally {
            db.close()
        }
    }

    @Test fun exportedV4SchemaHasSmsBalances() {
        val v4 = JSONObject(File(schemaDir, "4.json").readText()).getJSONObject("database")
        assertEquals(4, v4.getInt("version"))
        assertTrue(v4.toString().contains("sms_balances"))
        assertTrue(v4.toString().contains("sync_tombstones"))
    }
}
