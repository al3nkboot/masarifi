package com.midroid.masarifi.data

import org.json.JSONArray
import org.json.JSONObject

object GoalsJson {
    fun goal(g: SavingsGoal): JSONObject = JSONObject()
        .put("id", g.id)
        .put("name", g.name)
        .put("target", g.target)
        .putNullableLong("targetDate", g.targetDate)
        .putNullableLong("accountId", g.accountId)
        .put("note", g.note)
        .put("createdAt", g.createdAt)
        .put("uuid", g.uuid)
        .put("updatedAt", g.updatedAt)

    fun contribution(c: GoalContribution): JSONObject = JSONObject()
        .put("id", c.id)
        .put("goalId", c.goalId)
        .put("amount", c.amount)
        .put("date", c.date)
        .put("note", c.note)
        .put("createdAt", c.createdAt)
        .put("uuid", c.uuid)
        .put("updatedAt", c.updatedAt)

    fun debt(d: Debt): JSONObject = JSONObject()
        .put("id", d.id)
        .put("person", d.person)
        .put("direction", d.direction)
        .put("amount", d.amount)
        .put("dateTime", d.dateTime)
        .put("note", d.note)
        .putNullableLong("splitTxnId", d.splitTxnId)
        .put("createdAt", d.createdAt)
        .put("uuid", d.uuid)
        .put("updatedAt", d.updatedAt)

    fun settlement(s: DebtSettlement): JSONObject = JSONObject()
        .put("id", s.id)
        .put("debtId", s.debtId)
        .put("amount", s.amount)
        .put("dateTime", s.dateTime)
        .putNullableLong("txnId", s.txnId)
        .put("createdAt", s.createdAt)
        .put("uuid", s.uuid)
        .put("updatedAt", s.updatedAt)

    fun goals(a: JSONArray?): List<SavingsGoal> {
        if (a == null) return emptyList()
        return List(a.length()) { index ->
            val o = a.getJSONObject(index)
            SavingsGoal(
                id = o.getLong("id"),
                name = o.getString("name"),
                target = o.getLong("target"),
                targetDate = o.optionalLong("targetDate"),
                accountId = o.optionalLong("accountId"),
                note = o.optString("note"),
                createdAt = o.optLong("createdAt"),
                uuid = o.optString("uuid").ifEmpty { newUuid() },
                updatedAt = o.optLong("updatedAt")
            )
        }
    }

    fun contributions(a: JSONArray?): List<GoalContribution> {
        if (a == null) return emptyList()
        return List(a.length()) { index ->
            val o = a.getJSONObject(index)
            GoalContribution(
                id = o.getLong("id"),
                goalId = o.getLong("goalId"),
                amount = o.getLong("amount"),
                date = o.getLong("date"),
                note = o.optString("note"),
                createdAt = o.optLong("createdAt"),
                uuid = o.optString("uuid").ifEmpty { newUuid() },
                updatedAt = o.optLong("updatedAt")
            )
        }
    }

    fun debts(a: JSONArray?): List<Debt> {
        if (a == null) return emptyList()
        return List(a.length()) { index ->
            val o = a.getJSONObject(index)
            Debt(
                id = o.getLong("id"),
                person = o.getString("person"),
                direction = o.optInt("direction"),
                amount = o.getLong("amount"),
                dateTime = o.getLong("dateTime"),
                note = o.optString("note"),
                splitTxnId = o.optionalLong("splitTxnId"),
                createdAt = o.optLong("createdAt"),
                uuid = o.optString("uuid").ifEmpty { newUuid() },
                updatedAt = o.optLong("updatedAt")
            )
        }
    }

    fun settlements(a: JSONArray?): List<DebtSettlement> {
        if (a == null) return emptyList()
        return List(a.length()) { index ->
            val o = a.getJSONObject(index)
            DebtSettlement(
                id = o.getLong("id"),
                debtId = o.getLong("debtId"),
                amount = o.getLong("amount"),
                dateTime = o.getLong("dateTime"),
                txnId = o.optionalLong("txnId"),
                createdAt = o.optLong("createdAt"),
                uuid = o.optString("uuid").ifEmpty { newUuid() },
                updatedAt = o.optLong("updatedAt")
            )
        }
    }

    private fun JSONObject.putNullableLong(key: String, value: Long?): JSONObject {
        if (value != null) putOpt(key, value)
        return this
    }

    private fun JSONObject.optionalLong(key: String): Long? =
        if (has(key) && !isNull(key)) getLong(key) else null
}
