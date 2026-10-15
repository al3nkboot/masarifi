package com.midroid.masarifi.sms

import org.json.JSONArray
import org.json.JSONObject

/** Stores the user's own-wallet top-up rules in settings. */
object WalletTopupCodec {
    fun encode(list: List<WalletTopup>): String = JSONArray().apply {
        list.forEach { t ->
            put(
                JSONObject().put("id", t.id).put("nameAr", t.nameAr).put("nameEn", t.nameEn)
                    .put("patterns", JSONArray(t.patterns)).putOpt("bank", t.targetBankId).putOpt("account", t.targetAccountUuid),
            )
        }
    }.toString()

    fun decode(s: String): List<WalletTopup> = runCatching {
        val a = JSONArray(s)
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            val p = o.optJSONArray("patterns") ?: JSONArray()
            WalletTopup(
                id = o.optString("id").ifBlank { "w$i" },
                nameAr = o.optString("nameAr"), nameEn = o.optString("nameEn"),
                patterns = (0 until p.length()).map { p.getString(it) }.filter { it.isNotBlank() },
                targetBankId = if (o.isNull("bank")) null else o.optString("bank").ifBlank { null },
                targetAccountUuid = if (o.isNull("account")) null else o.optString("account").ifBlank { null },
            )
        }
    }.getOrDefault(DefaultSmsConfig.walletTopups)
}
