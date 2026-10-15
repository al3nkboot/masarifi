package com.midroid.masarifi.data.backup

import com.midroid.masarifi.data.Account
import com.midroid.masarifi.data.AppSettings
import com.midroid.masarifi.data.Budget
import com.midroid.masarifi.data.Category
import com.midroid.masarifi.data.MerchantRule
import com.midroid.masarifi.data.Recurring
import com.midroid.masarifi.data.SmsLog
import com.midroid.masarifi.data.SmsLink
import com.midroid.masarifi.data.DupDismissed
import com.midroid.masarifi.data.Txn
import com.midroid.masarifi.data.newUuid
import org.json.JSONArray
import org.json.JSONObject

/** Complete snapshot of the app's data. */
data class BackupData(
    val schemaVersion: Int = BackupCodec.SCHEMA_VERSION,
    val createdAt: Long,
    val appVersion: String,
    val settings: AppSettings?,
    val accounts: List<Account>,
    val categories: List<Category>,
    val transactions: List<Txn>,
    val recurring: List<Recurring>,
    val budgets: List<Budget>,
    val merchantRules: List<MerchantRule>,
    val smsLog: List<SmsLog>,
    val smsLinks: List<SmsLink> = emptyList(),
    val dupDismissed: List<DupDismissed> = emptyList(),
)

class BackupFormatException(val code: String, msg: String) : Exception(msg)

/**
 * Versioned JSON backup format:
 * { "format": "masarifi-backup", "schemaVersion": 1, "createdAt": millis, "appVersion": "...",
 *   "settings": {...}, "accounts": [...], "categories": [...], "transactions": [...], "recurring": [...],
 *   "budgets": [...], "merchantRules": [...], "smsLog": [...] }
 * v2 (app 1.2.0) adds transactions[].smsLinked / possibleDupOf, "smsLinks" and "dupDismissed", plus settings.dupWindowHours.
 * v3 (app 1.3.0) adds uuid + updatedAt to accounts, categories, transactions, recurring and smsLinks, and budgets[].updatedAt
 * (needed so a restored device keeps the same identities for merge sync). Older files get fresh uuids.
 * Readers ignore unknown fields; older schema versions are upgraded in [decode] (missing fields get defaults).
 */
object BackupCodec {
    const val FORMAT = "masarifi-backup"
    const val SCHEMA_VERSION = 3

    fun encode(d: BackupData): String {
        val o = JSONObject()
        o.put("format", FORMAT)
        o.put("schemaVersion", SCHEMA_VERSION)
        o.put("createdAt", d.createdAt)
        o.put("appVersion", d.appVersion)
        d.settings?.let { s ->
            o.put("settings", JSONObject().apply {
                put("currency", s.currency); put("language", s.language); put("theme", s.theme)
                put("smsEnabled", s.smsEnabled); put("smsNotify", s.smsNotify); put("smsAcceptUnknown", s.smsAcceptUnknown)
                put("extraSenders", s.extraSenders); put("budgetAlerts", s.budgetAlerts); put("spikeAlerts", s.spikeAlerts)
                put("weekStartsSaturday", s.weekStartsSaturday)
                put("dupWindowHours", s.dupWindowHours)
            })
        }
        o.put("accounts", JSONArray().apply {
            d.accounts.forEach { a ->
                put(JSONObject().put("id", a.id).put("name", a.name).put("groupType", a.groupType).put("initialBalance", a.initialBalance)
                    .putOpt("cardLast4", a.cardLast4).put("includeInTotal", a.includeInTotal).put("sortOrder", a.sortOrder).sync(a.uuid, a.updatedAt))
            }
        })
        o.put("categories", JSONArray().apply {
            d.categories.forEach { c ->
                put(JSONObject().put("id", c.id).put("type", c.type).put("nameAr", c.nameAr).put("nameEn", c.nameEn).put("emoji", c.emoji)
                    .putOpt("key", c.key).put("sortOrder", c.sortOrder).sync(c.uuid, c.updatedAt))
            }
        })
        o.put("transactions", JSONArray().apply {
            d.transactions.forEach { t ->
                put(JSONObject().put("id", t.id).put("type", t.type).put("amount", t.amount).put("accountId", t.accountId)
                    .putOpt("toAccountId", t.toAccountId).putOpt("categoryId", t.categoryId).put("note", t.note).put("description", t.description)
                    .put("dateTime", t.dateTime).put("isAuto", t.isAuto).putOpt("smsHash", t.smsHash).putOpt("merchant", t.merchant)
                    .putOpt("recurringId", t.recurringId).put("createdAt", t.createdAt)
                    .put("smsLinked", t.smsLinked).putOpt("possibleDupOf", t.possibleDupOf).sync(t.uuid, t.updatedAt))
            }
        })
        o.put("recurring", JSONArray().apply {
            d.recurring.forEach { r ->
                put(JSONObject().put("id", r.id).put("type", r.type).put("amount", r.amount).put("accountId", r.accountId)
                    .putOpt("toAccountId", r.toAccountId).putOpt("categoryId", r.categoryId).put("note", r.note)
                    .put("frequency", r.frequency).put("nextDate", r.nextDate).put("active", r.active).sync(r.uuid, r.updatedAt))
            }
        })
        o.put("budgets", JSONArray().apply { d.budgets.forEach { put(JSONObject().put("categoryId", it.categoryId).put("amount", it.amount).put("updatedAt", it.updatedAt)) } })
        o.put("merchantRules", JSONArray().apply {
            d.merchantRules.forEach { put(JSONObject().put("merchantKey", it.merchantKey).put("categoryId", it.categoryId).put("updatedAt", it.updatedAt)) }
        })
        o.put("smsLog", JSONArray().apply {
            d.smsLog.forEach {
                put(JSONObject().put("hash", it.hash).put("sender", it.sender).put("receivedAt", it.receivedAt).put("status", it.status).putOpt("txnId", it.txnId))
            }
        })
        o.put("smsLinks", JSONArray().apply {
            d.smsLinks.forEach { l ->
                put(JSONObject().put("id", l.id).put("txnId", l.txnId).put("smsHash", l.smsHash).put("sender", l.sender).put("type", l.type)
                    .put("amount", l.amount).put("accountId", l.accountId).putOpt("categoryId", l.categoryId).put("note", l.note)
                    .put("description", l.description).put("dateTime", l.dateTime).putOpt("merchant", l.merchant).putOpt("cardLast4", l.cardLast4)
                    .putOpt("prevMerchant", l.prevMerchant).put("createdAt", l.createdAt).sync(l.uuid, l.updatedAt))
            }
        })
        o.put("dupDismissed", JSONArray().apply { d.dupDismissed.forEach { put(JSONObject().put("pairKey", it.pairKey).put("createdAt", it.createdAt)) } })
        return o.toString()
    }

    private fun JSONObject.sync(uuid: String, updatedAt: Long): JSONObject = put("uuid", uuid).put("updatedAt", updatedAt)
    private fun JSONObject.uuid(): String = optString("uuid").ifEmpty { newUuid() }

    private fun JSONObject.optLongOrNull(k: String): Long? = if (has(k) && !isNull(k)) getLong(k) else null
    private fun JSONObject.optStringOrNull(k: String): String? = if (has(k) && !isNull(k)) getString(k) else null
    private inline fun <T> JSONObject.list(k: String, f: (JSONObject) -> T): List<T> {
        val a = optJSONArray(k) ?: return emptyList()
        return (0 until a.length()).map { f(a.getJSONObject(it)) }
    }

    fun decode(text: String): BackupData {
        val o = try { JSONObject(text.removePrefix("\uFEFF")) } catch (e: Exception) { throw BackupFormatException("not_json", "Not a backup file") }
        if (o.optString("format") != FORMAT) throw BackupFormatException("not_backup", "Not a Masarifi backup")
        val v = o.optInt("schemaVersion", 0)
        if (v < 1) throw BackupFormatException("not_backup", "Missing schema version")
        if (v > SCHEMA_VERSION) throw BackupFormatException("newer", "Backup made by a newer app version (schema $v)")
        val def = AppSettings()
        val settings = o.optJSONObject("settings")?.let { s ->
            AppSettings(
                currency = s.optString("currency", def.currency), language = s.optString("language", def.language), theme = s.optString("theme", def.theme),
                smsEnabled = s.optBoolean("smsEnabled", def.smsEnabled), smsNotify = s.optBoolean("smsNotify", def.smsNotify),
                smsAcceptUnknown = s.optBoolean("smsAcceptUnknown", def.smsAcceptUnknown), extraSenders = s.optString("extraSenders", def.extraSenders),
                budgetAlerts = s.optBoolean("budgetAlerts", def.budgetAlerts), spikeAlerts = s.optBoolean("spikeAlerts", def.spikeAlerts),
                weekStartsSaturday = s.optBoolean("weekStartsSaturday", def.weekStartsSaturday),
                dupWindowHours = s.optInt("dupWindowHours", def.dupWindowHours),
            )
        }
        return BackupData(
            schemaVersion = v,
            createdAt = o.optLong("createdAt", 0),
            appVersion = o.optString("appVersion", ""),
            settings = settings,
            accounts = o.list("accounts") { a ->
                Account(a.getLong("id"), a.getString("name"), a.optInt("groupType"), a.optLong("initialBalance"), a.optStringOrNull("cardLast4"),
                    a.optBoolean("includeInTotal", true), a.optInt("sortOrder"), a.uuid(), a.optLong("updatedAt"))
            },
            categories = o.list("categories") { c ->
                Category(c.getLong("id"), c.getInt("type"), c.optString("nameAr"), c.optString("nameEn"), c.optString("emoji", "📦"), c.optStringOrNull("key"), c.optInt("sortOrder"), c.uuid(), c.optLong("updatedAt"))
            },
            transactions = o.list("transactions") { t ->
                Txn(
                    id = t.getLong("id"), type = t.getInt("type"), amount = t.getLong("amount"), accountId = t.getLong("accountId"),
                    toAccountId = t.optLongOrNull("toAccountId"), categoryId = t.optLongOrNull("categoryId"), note = t.optString("note"),
                    description = t.optString("description"), dateTime = t.getLong("dateTime"), isAuto = t.optBoolean("isAuto"),
                    smsHash = t.optStringOrNull("smsHash"), merchant = t.optStringOrNull("merchant"), recurringId = t.optLongOrNull("recurringId"),
                    createdAt = t.optLong("createdAt", t.getLong("dateTime")),
                    smsLinked = t.optBoolean("smsLinked", false), possibleDupOf = t.optStringOrNull("possibleDupOf"),
                    uuid = t.uuid(), updatedAt = t.optLong("updatedAt", t.optLong("createdAt", 0)),
                )
            },
            recurring = o.list("recurring") { r ->
                Recurring(r.getLong("id"), r.getInt("type"), r.getLong("amount"), r.getLong("accountId"), r.optLongOrNull("toAccountId"),
                    r.optLongOrNull("categoryId"), r.optString("note"), r.getInt("frequency"), r.getLong("nextDate"), r.optBoolean("active", true), r.uuid(), r.optLong("updatedAt"))
            },
            budgets = o.list("budgets") { Budget(it.getLong("categoryId"), it.getLong("amount"), it.optLong("updatedAt")) },
            merchantRules = o.list("merchantRules") { MerchantRule(it.getString("merchantKey"), it.getLong("categoryId"), it.optLong("updatedAt")) },
            smsLog = o.list("smsLog") { SmsLog(it.getString("hash"), it.optString("sender"), it.optLong("receivedAt"), it.optInt("status"), it.optLongOrNull("txnId")) },
            smsLinks = o.list("smsLinks") { l ->
                SmsLink(l.getLong("id"), l.getLong("txnId"), l.getString("smsHash"), l.optString("sender"), l.getInt("type"), l.getLong("amount"),
                    l.getLong("accountId"), l.optLongOrNull("categoryId"), l.optString("note"), l.optString("description"), l.getLong("dateTime"),
                    l.optStringOrNull("merchant"), l.optStringOrNull("cardLast4"), l.optStringOrNull("prevMerchant"), l.optLong("createdAt"), l.uuid(), l.optLong("updatedAt", l.optLong("createdAt")))
            },
            dupDismissed = o.list("dupDismissed") { DupDismissed(it.getString("pairKey"), it.optLong("createdAt")) },
        )
    }
}
