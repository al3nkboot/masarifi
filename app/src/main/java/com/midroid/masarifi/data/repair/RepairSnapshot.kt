package com.midroid.masarifi.data.repair

import com.midroid.masarifi.data.Txn
import org.json.JSONArray
import org.json.JSONObject

/** Undo data of one repair run: original rows, links created / moved and sms_log rows re-pointed. */
data class RepairSnapshot(
    val rows: List<Txn>,
    val createdLinkIds: List<Long>,
    /** link id → original txn id (links of a folded transaction moved to the keeper). */
    val movedLinks: Map<Long, Long>,
    /** sms_log hash → original txn id. */
    val logTxn: Map<String, Long?>,
    /** Accounts created by the run (Cash), deleted again on undo when unused. */
    val createdAccountIds: List<Long> = emptyList(),
) {
    fun encode(): String = JSONObject()
        .put("rows", JSONArray().apply { rows.forEach { put(txnJson(it)) } })
        .put("createdLinks", JSONArray(createdLinkIds))
        .put("movedLinks", JSONObject().apply { movedLinks.forEach { (k, v) -> put(k.toString(), v) } })
        .put("log", JSONObject().apply { logTxn.forEach { (k, v) -> put(k, v ?: JSONObject.NULL) } })
        .put("createdAccounts", JSONArray(createdAccountIds))
        .toString()

    companion object {
        fun decode(s: String): RepairSnapshot {
            val o = JSONObject(s)
            val rows = o.getJSONArray("rows").let { a -> (0 until a.length()).map { txnFrom(a.getJSONObject(it)) } }
            val links = o.getJSONArray("createdLinks").let { a -> (0 until a.length()).map { a.getLong(it) } }
            val moved = o.getJSONObject("movedLinks").let { m -> m.keys().asSequence().associate { it.toLong() to m.getLong(it) } }
            val log = o.getJSONObject("log").let { m -> m.keys().asSequence().associate { it to (if (m.isNull(it)) null else m.getLong(it)) } }
            val accs = o.optJSONArray("createdAccounts")?.let { a -> (0 until a.length()).map { a.getLong(it) } } ?: emptyList()
            return RepairSnapshot(rows, links, moved, log, accs)
        }

        fun txnJson(t: Txn): JSONObject = JSONObject()
            .put("id", t.id).put("type", t.type).put("amount", t.amount).put("accountId", t.accountId)
            .put("toAccountId", t.toAccountId ?: JSONObject.NULL).put("categoryId", t.categoryId ?: JSONObject.NULL)
            .put("note", t.note).put("description", t.description).put("dateTime", t.dateTime).put("isAuto", t.isAuto)
            .put("smsHash", t.smsHash ?: JSONObject.NULL).put("merchant", t.merchant ?: JSONObject.NULL)
            .put("recurringId", t.recurringId ?: JSONObject.NULL).put("createdAt", t.createdAt).put("smsLinked", t.smsLinked)
            .put("possibleDupOf", t.possibleDupOf ?: JSONObject.NULL).put("uuid", t.uuid).put("updatedAt", t.updatedAt)

        private fun JSONObject.longOrNull(k: String) = if (isNull(k)) null else getLong(k)
        private fun JSONObject.strOrNull(k: String) = if (isNull(k)) null else getString(k)

        fun txnFrom(o: JSONObject) = Txn(
            id = o.getLong("id"), type = o.getInt("type"), amount = o.getLong("amount"), accountId = o.getLong("accountId"),
            toAccountId = o.longOrNull("toAccountId"), categoryId = o.longOrNull("categoryId"), note = o.getString("note"),
            description = o.getString("description"), dateTime = o.getLong("dateTime"), isAuto = o.getBoolean("isAuto"),
            smsHash = o.strOrNull("smsHash"), merchant = o.strOrNull("merchant"), recurringId = o.longOrNull("recurringId"),
            createdAt = o.getLong("createdAt"), smsLinked = o.getBoolean("smsLinked"), possibleDupOf = o.strOrNull("possibleDupOf"),
            uuid = o.getString("uuid"), updatedAt = o.getLong("updatedAt"),
        )
    }
}
