package com.midroid.masarifi.warranty

import org.json.JSONObject

object WarrantyJson {
    fun encode(w: Warranty): JSONObject = JSONObject().put("id", w.id).put("name", w.name).put("category", w.category).put("brand", w.brand)
        .put("store", w.store).put("purchaseDate", w.purchaseDate).put("durationValue", w.durationValue).put("durationUnit", w.durationUnit)
        .put("expiryDate", w.expiryDate).putOpt("price", w.price).put("notes", w.notes).put("images", w.images).put("createdAt", w.createdAt)
        .put("uuid", w.uuid).put("updatedAt", w.updatedAt)

    fun decode(o: JSONObject) = Warranty(
        id = o.optLong("id"), name = o.getString("name"), category = o.optInt("category", 1), brand = o.optString("brand"), store = o.optString("store"),
        purchaseDate = o.getLong("purchaseDate"), durationValue = o.optInt("durationValue", 1), durationUnit = o.optInt("durationUnit", 1),
        expiryDate = o.getLong("expiryDate"), price = if (o.has("price") && !o.isNull("price")) o.getLong("price") else null, notes = o.optString("notes"),
        images = o.optString("images", "[]"), createdAt = o.optLong("createdAt"), uuid = o.optString("uuid").ifBlank { com.midroid.masarifi.data.newUuid() },
        updatedAt = o.optLong("updatedAt"),
    )
}
