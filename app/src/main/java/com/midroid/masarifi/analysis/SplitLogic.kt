package com.midroid.masarifi.analysis

/** Rules for splitting one purchase across categories (2.9.0). Amounts in thousandths like everywhere else. */
object SplitLogic {
    /** What is still unassigned (negative = parts exceed the total). Blank / invalid amounts count as 0. */
    fun remaining(total: Long, parts: List<Long?>): Long = total - parts.sumOf { it ?: 0L }

    /** At least two parts, each with a category and a positive amount, summing exactly to [total]. */
    fun isValid(total: Long, parts: List<Pair<Long?, Long?>>): Boolean =
        total > 0 && parts.size >= 2 && parts.all { (cat, amt) -> cat != null && amt != null && amt > 0 } &&
            remaining(total, parts.map { it.second }) == 0L
}
