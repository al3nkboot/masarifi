package com.midroid.masarifi.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

object TxnType {
    const val INCOME = 0
    const val EXPENSE = 1
    const val TRANSFER = 2
}

object AccountGroup {
    const val CASH = 0
    const val BANK = 1
    const val CARD = 2
    const val SAVINGS = 3
    const val OTHER = 4
    val all = listOf(CASH, BANK, CARD, SAVINGS, OTHER)
}

object Repeat {
    const val NONE = 0
    const val DAILY = 1
    const val WEEKLY = 2
    const val MONTHLY = 3
    const val YEARLY = 4
    val all = listOf(NONE, DAILY, WEEKLY, MONTHLY, YEARLY)
}

/** Amounts are stored as Long in thousandths of the currency unit (e.g. 4.300 OMR = 4300). */
@Entity(
    tableName = "transactions",
    indices = [Index("dateTime"), Index("accountId"), Index("categoryId"), Index(value = ["smsHash"], unique = true), Index(value = ["uuid"], unique = true)],
)
data class Txn(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: Int,
    val amount: Long,
    val accountId: Long,
    val toAccountId: Long? = null,
    val categoryId: Long? = null,
    val note: String = "",
    val description: String = "",
    val dateTime: Long,
    val isAuto: Boolean = false,
    val smsHash: String? = null,
    val merchant: String? = null,
    val recurringId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    /** True when a bank SMS was linked to this (manual/imported) transaction instead of creating a new one (see [SmsLink]). */
    @ColumnInfo(defaultValue = "0") val smsLinked: Boolean = false,
    /** Comma-separated ids of transactions this one may duplicate (set when an SMS matched ambiguously). */
    val possibleDupOf: String? = null,
    /** Stable cross-device id used by merge sync (assigned to existing rows by MIGRATION_2_3). */
    @ColumnInfo(defaultValue = "") val uuid: String = newUuid(),
    /** Last modification time (millis). Also bumped automatically by a DB trigger on every UPDATE (see [SyncSchema]). */
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = System.currentTimeMillis(),
)

/**
 * A bank SMS that was attached to an existing transaction instead of creating its own row.
 * Keeps everything needed to undo ("split") it back into a separate transaction.
 */
@Entity(tableName = "sms_links", indices = [Index("txnId"), Index(value = ["smsHash"], unique = true), Index(value = ["uuid"], unique = true)])
data class SmsLink(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val txnId: Long,
    val smsHash: String,
    val sender: String = "",
    val type: Int,
    val amount: Long,
    val accountId: Long,
    val categoryId: Long? = null,
    val note: String = "",
    val description: String = "",
    val dateTime: Long,
    val merchant: String? = null,
    val cardLast4: String? = null,
    /** The linked transaction's merchant before linking (restored on split). */
    val prevMerchant: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    /** Stable cross-device id used by merge sync (assigned to existing rows by MIGRATION_2_3). */
    @ColumnInfo(defaultValue = "") val uuid: String = newUuid(),
    /** Last modification time (millis). Also bumped automatically by a DB trigger on every UPDATE (see [SyncSchema]). */
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = System.currentTimeMillis(),
)

/** A pair of transactions the user marked as "not a duplicate" (key = "minId-maxId"). */
@Entity(tableName = "dup_dismissed")
data class DupDismissed(
    @PrimaryKey val pairKey: String,
    val createdAt: Long = System.currentTimeMillis(),
) {
    companion object {
        fun key(a: Long, b: Long) = if (a < b) "$a-$b" else "$b-$a"
    }
}

@Entity(tableName = "accounts", indices = [Index(value = ["uuid"], unique = true)])
data class Account(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val groupType: Int = AccountGroup.CASH,
    val initialBalance: Long = 0,
    val cardLast4: String? = null,
    val includeInTotal: Boolean = true,
    val sortOrder: Int = 0,
    /** Stable cross-device id used by merge sync (assigned to existing rows by MIGRATION_2_3). */
    @ColumnInfo(defaultValue = "") val uuid: String = newUuid(),
    /** Last modification time (millis). Also bumped automatically by a DB trigger on every UPDATE (see [SyncSchema]). */
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "categories", indices = [Index(value = ["uuid"], unique = true)])
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: Int,
    val nameAr: String,
    val nameEn: String,
    val emoji: String,
    /** Stable key for default categories (used by SMS keyword rules); null for user categories. */
    val key: String? = null,
    val sortOrder: Int = 0,
    /** Stable cross-device id used by merge sync (assigned to existing rows by MIGRATION_2_3). */
    @ColumnInfo(defaultValue = "") val uuid: String = newUuid(),
    /** Last modification time (millis). Also bumped automatically by a DB trigger on every UPDATE (see [SyncSchema]). */
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = System.currentTimeMillis(),
) {
    fun name(arabic: Boolean) = if (arabic) nameAr.ifBlank { nameEn } else nameEn.ifBlank { nameAr }
}

/** Monthly budget. categoryId = 0 means the overall budget. */
@Entity(tableName = "budgets")
data class Budget(
    @PrimaryKey val categoryId: Long,
    val amount: Long,
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = System.currentTimeMillis(),
)

/** Learned merchant → category mapping from user corrections. */
@Entity(tableName = "merchant_rules")
data class MerchantRule(
    @PrimaryKey val merchantKey: String,
    val categoryId: Long,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "recurring", indices = [Index(value = ["uuid"], unique = true)])
data class Recurring(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: Int,
    val amount: Long,
    val accountId: Long,
    val toAccountId: Long? = null,
    val categoryId: Long? = null,
    val note: String = "",
    val frequency: Int,
    val nextDate: Long,
    val active: Boolean = true,
    /** Stable cross-device id used by merge sync (assigned to existing rows by MIGRATION_2_3). */
    @ColumnInfo(defaultValue = "") val uuid: String = newUuid(),
    /** Last modification time (millis). Also bumped automatically by a DB trigger on every UPDATE (see [SyncSchema]). */
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = System.currentTimeMillis(),
)

/** Every processed SMS (imported or ignored) so it is never processed twice. */
@Entity(tableName = "sms_log")
data class SmsLog(
    @PrimaryKey val hash: String,
    val sender: String,
    val receivedAt: Long,
    val status: Int,
    val txnId: Long? = null,
) {
    companion object {
        const val IMPORTED = 1
        const val IGNORED = 0
    }
}

/**
 * Local record of a deleted synced row (filled by DB triggers, see [SyncSchema]) so the deletion can propagate
 * to other devices as a tombstone. [uuid] is the entity uuid, or "b:<categoryUuid>" for budgets and "m:<merchantKey>" for merchant rules.
 */
@Entity(tableName = "sync_tombstones")
data class SyncTombstone(
    @PrimaryKey val uuid: String,
    val entity: String,
    val deletedAt: Long,
)

fun newUuid(): String = java.util.UUID.randomUUID().toString()

/**
 * Account balance printed in a bank SMS (local only, not synced or backed up). Used to sanity-check the direction of
 * ambiguous messages (see [com.midroid.masarifi.sms.BalanceCheck]) and to show the last known bank balance.
 * [balance] is in thousandths of the currency unit like all amounts.
 */
@Entity(tableName = "sms_balances", indices = [Index(value = ["accountId", "dateTime"])])
data class SmsBalance(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val dateTime: Long,
    val balance: Long,
    val smsHash: String,
    val createdAt: Long = System.currentTimeMillis(),
)
