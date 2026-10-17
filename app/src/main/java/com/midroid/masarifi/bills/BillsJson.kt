package com.midroid.masarifi.bills

import org.json.JSONArray
import org.json.JSONObject

/** JSON form of bills / payments inside a Masarifi backup ("bills", "billPayments"; optional arrays added in app 2.1.0). */
object BillsJson {
    fun bill(b: Bill): JSONObject = JSONObject().put("id", b.id).put("name", b.name).put("amount", b.amount).putOpt("categoryId", b.categoryId)
        .putOpt("accountId", b.accountId).put("recurrence", b.recurrence).put("recurInterval", b.recurInterval).put("anchorDay", b.anchorDay)
        .put("nextDue", b.nextDue).put("reminderDays", b.reminderDays).put("notes", b.notes).put("active", b.active).put("createdAt", b.createdAt)
        .put("uuid", b.uuid).put("updatedAt", b.updatedAt)

    fun payment(p: BillPayment): JSONObject = JSONObject().put("id", p.id).put("billId", p.billId).put("amount", p.amount).put("dueDate", p.dueDate)
        .put("paidAt", p.paidAt).putOpt("txnUuid", p.txnUuid).put("ownTxn", p.ownTxn).put("uuid", p.uuid).put("updatedAt", p.updatedAt)

    private fun JSONObject.optLongOrNull(k: String): Long? = if (has(k) && !isNull(k)) getLong(k) else null
    private fun JSONObject.optStringOrNull(k: String): String? = if (has(k) && !isNull(k)) getString(k) else null

    fun bill(o: JSONObject): Bill {
        val due = o.getLong("nextDue")
        return Bill(
            id = o.getLong("id"), name = o.optString("name"), amount = o.optLong("amount"), categoryId = o.optLongOrNull("categoryId"),
            accountId = o.optLongOrNull("accountId"),
            recurrence = o.optString("recurrence", RecurrenceType.MONTHLY.name).let { r -> if (RecurrenceType.entries.any { it.name == r }) r else RecurrenceType.MONTHLY.name },
            recurInterval = o.optInt("recurInterval", 1).coerceAtLeast(1),
            anchorDay = o.optInt("anchorDay", java.time.LocalDate.ofEpochDay(due).dayOfMonth).coerceIn(1, 31), nextDue = due,
            reminderDays = o.optInt("reminderDays", 3), notes = o.optString("notes"), active = o.optBoolean("active", true),
            createdAt = o.optLong("createdAt"), uuid = o.optString("uuid").ifEmpty { com.midroid.masarifi.data.newUuid() }, updatedAt = o.optLong("updatedAt"),
        )
    }

    fun payment(o: JSONObject) = BillPayment(
        id = o.getLong("id"), billId = o.getLong("billId"), amount = o.optLong("amount"), dueDate = o.getLong("dueDate"), paidAt = o.optLong("paidAt"),
        txnUuid = o.optStringOrNull("txnUuid"), ownTxn = o.optBoolean("ownTxn"), uuid = o.optString("uuid").ifEmpty { com.midroid.masarifi.data.newUuid() },
        updatedAt = o.optLong("updatedAt"),
    )

    fun bills(a: JSONArray?) = a?.let { (0 until it.length()).map { i -> bill(it.getJSONObject(i)) } }
    fun payments(a: JSONArray?) = a?.let { (0 until it.length()).map { i -> payment(it.getJSONObject(i)) } }
}

class Mi5gImportException(val code: String) : Exception(code)

/**
 * Reads a Mi5G Bills backup file ({"app":"mi5g-bills","format":1,"bills":[..],"payments":[..]}; plain, not encrypted).
 * Categories map to Masarifi default category keys; payment history is imported without transactions.
 */
object Mi5gImport {
    data class Parsed(val bills: List<Bill>, val payments: List<Pair<String, BillPayment>>, val currency: Map<String, String>)

    fun categoryKey(cat: String): String = when (cat) {
        "rent" -> "rent"
        "internet", "telecom" -> "internet"
        "car" -> "car"
        "insurance" -> "car_insurance"
        else -> "other"
    }

    fun uuidFor(createdAt: Long, id: Long) = "mi5g-bill-$createdAt-$id"

    /** [catIdForKey] resolves a Masarifi category key to its id. Returns bills (id 0) and payments keyed by their bill uuid. */
    fun parse(text: String, catIdForKey: (String) -> Long?): Parsed {
        val o = try { JSONObject(text.removePrefix("\uFEFF")) } catch (e: Exception) { throw Mi5gImportException("not_json") }
        if (o.optString("app") != "mi5g-bills") throw Mi5gImportException("not_mi5g")
        if (o.optBoolean("encrypted", false)) throw Mi5gImportException("encrypted")
        val bills = o.optJSONArray("bills") ?: JSONArray()
        val pays = o.optJSONArray("payments") ?: JSONArray()
        val uuidById = HashMap<Long, String>()
        val currency = HashMap<String, String>()
        val outBills = (0 until bills.length()).map { i ->
            val b = bills.getJSONObject(i)
            val due = java.time.LocalDate.parse(b.getString("nextDueDate"))
            val createdAt = b.optLong("createdAt", 0); val id = b.getLong("id")
            val uuid = uuidFor(createdAt, id); uuidById[id] = uuid
            currency[uuid] = b.optString("currency", "OMR")
            Bill(
                name = b.getString("name"), amount = b.getLong("amountMilli"), categoryId = catIdForKey(categoryKey(b.optString("category", "other"))),
                recurrence = b.optString("recurrenceType", "MONTHLY").let { r -> if (RecurrenceType.entries.any { it.name == r }) r else "MONTHLY" },
                recurInterval = b.optInt("recurrenceInterval", 1).coerceAtLeast(1), anchorDay = b.optInt("anchorDay", due.dayOfMonth).coerceIn(1, 31),
                nextDue = due.toEpochDay(), reminderDays = b.optInt("reminderDaysBefore", 3).coerceIn(0, 60), notes = b.optString("notes", ""),
                active = b.optBoolean("active", true), createdAt = createdAt.takeIf { it > 0 } ?: System.currentTimeMillis(), uuid = uuid,
            )
        }
        val outPays = (0 until pays.length()).mapNotNull { i ->
            val p = pays.getJSONObject(i)
            val bu = uuidById[p.getLong("billId")] ?: return@mapNotNull null
            bu to BillPayment(billId = 0, amount = p.getLong("amountMilli"), dueDate = java.time.LocalDate.parse(p.getString("dueDate")).toEpochDay(),
                paidAt = p.optLong("paidAt", 0), uuid = "mi5g-pay-$bu-${p.optLong("id", i.toLong())}")
        }
        return Parsed(outBills, outPays, currency)
    }
}
