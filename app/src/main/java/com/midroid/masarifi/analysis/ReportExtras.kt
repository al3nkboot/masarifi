package com.midroid.masarifi.analysis

import com.midroid.masarifi.data.Txn
import com.midroid.masarifi.data.TxnType
import java.time.Instant
import java.time.ZoneId

data class MerchantRow(val name: String, val count: Int, val amount: Long)

object ReportExtras {
    /**
     * Expenses whose local date is in [period], grouped by merchant name.
     * Grouping is case-insensitive and keeps the first spelling encountered.
     */
    fun topMerchants(
        all: List<Txn>,
        period: SummaryPeriod,
        zone: ZoneId,
        limit: Int = 5,
    ): List<MerchantRow> {
        val groups = LinkedHashMap<String, MerchantRow>()

        for (txn in all) {
            if (txn.type != TxnType.EXPENSE) continue

            val date = Instant.ofEpochMilli(txn.dateTime).atZone(zone).toLocalDate()
            if (date < period.start || date >= period.endExclusive) continue

            val name = (txn.merchant ?: txn.note).trim()
            if (name.isBlank()) continue

            val key = name.lowercase()
            val existing = groups[key]
            groups[key] = if (existing == null) {
                MerchantRow(name, 1, txn.amount)
            } else {
                existing.copy(count = existing.count + 1, amount = existing.amount + txn.amount)
            }
        }

        return groups.values
            .sortedByDescending { it.amount }
            .take(limit)
    }

    /** Savings rate as a percentage, or null when there is no positive income. */
    fun savingsRate(income: Long, expense: Long): Double? =
        if (income <= 0) null else (income - expense) * 100.0 / income
}
