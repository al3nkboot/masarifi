package com.midroid.masarifi.data.catfix

import com.midroid.masarifi.data.Category
import com.midroid.masarifi.data.Txn
import com.midroid.masarifi.data.TxnType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure tests of the 2.6.2 conservative heuristic. */
class SubcategoryRepairTest {
    private val food = Category(id = 1, type = TxnType.EXPENSE, nameAr = "أكل", nameEn = "Food", emoji = "🍜")
    private val fast = Category(id = 2, type = TxnType.EXPENSE, nameAr = "وجبات سريعة", nameEn = "Fast food", emoji = "🍕", parentId = 1)
    private val coffee = Category(id = 3, type = TxnType.EXPENSE, nameAr = "قهوة", nameEn = "قهوة", emoji = "☕", parentId = 1)
    private val trip = Category(id = 4, type = TxnType.EXPENSE, nameAr = "سفر", nameEn = "Trip", emoji = "✈️")
    private val tripCoffee = Category(id = 5, type = TxnType.EXPENSE, nameAr = "قهوة", nameEn = "قهوة", emoji = "☕", parentId = 4)
    private val salary = Category(id = 6, type = TxnType.INCOME, nameAr = "مال", nameEn = "مال", emoji = "💸")
    private val cats = listOf(food, fast, coffee, trip, tripCoffee, salary)

    private fun mm(id: Long, cat: Long?, desc: String, type: Int = TxnType.EXPENSE, hash: String? = "mm:h$id") =
        Txn(id = id, type = type, amount = 1000, accountId = 1, categoryId = cat, description = desc, dateTime = 0, smsHash = hash)

    @Test fun movesOnlyUniqueExactMatchUnderSameParent() {
        val txns = listOf(
            mm(1, 1, "🍕وجبات سريعة • Online order • USD 2.5"), // emoji prefix + rest kept
            mm(2, 1, "قهوة"),                                   // only segment
            mm(3, 1, "FAST FOOD"),                              // English name, case-insensitive
            mm(4, 4, "قهوة • كرك"),                             // same name, other parent → its own sub
        )
        val ch = SubcategoryRepair.plan(txns, cats).associateBy { it.before.id }
        assertEquals(4, ch.size)
        assertEquals(2L, ch[1]!!.after.categoryId); assertEquals("Online order • USD 2.5", ch[1]!!.after.description)
        assertEquals(3L, ch[2]!!.after.categoryId); assertEquals("", ch[2]!!.after.description)
        assertEquals(2L, ch[3]!!.after.categoryId)
        assertEquals(5L, ch[4]!!.after.categoryId); assertEquals("كرك", ch[4]!!.after.description)
        // nothing but categoryId/description changes
        assertEquals(txns[0].copy(categoryId = 2, description = "Online order • USD 2.5"), ch[1]!!.after)
    }

    @Test fun leavesEverythingElseAlone() {
        val txns = listOf(
            mm(10, 1, "قهوة", hash = null),                   // manual entry
            mm(11, 1, "قهوة", hash = "sms:abc"),              // bank SMS
            mm(12, 3, "قهوة"),                                // already on a subcategory
            mm(13, 1, "شاورما • قهوة"),                       // first segment is not a subcategory
            mm(14, 1, "قهوة مختصة"),                          // partial match only
            mm(15, 1, ""),                                    // no description
            mm(16, null, "قهوة"),                             // uncategorized
            mm(17, 1, "قهوة", type = TxnType.TRANSFER),       // transfer
            mm(18, 6, "قهوة", type = TxnType.INCOME),         // income parent without such sub
            mm(19, 99, "قهوة"),                               // category no longer exists
        )
        assertTrue(SubcategoryRepair.plan(txns, cats).isEmpty())
    }

    @Test fun ambiguousNamesAreSkipped() {
        // Two subcategories of the same parent answer to "Coffee" (one by Arabic, one by English name).
        val a = Category(id = 20, type = TxnType.EXPENSE, nameAr = "Coffee", nameEn = "قهوة عربية", emoji = "☕", parentId = 1)
        val b = Category(id = 21, type = TxnType.EXPENSE, nameAr = "قهوة باردة", nameEn = "coffee", emoji = "🧋", parentId = 1)
        assertTrue(SubcategoryRepair.plan(listOf(mm(1, 1, "Coffee")), listOf(food, a, b)).isEmpty())
    }

    @Test fun incomeSubcategoryOfOtherTypeIsNotUsed() {
        val expSubUnderIncome = Category(id = 30, type = TxnType.EXPENSE, nameAr = "راتب", nameEn = "راتب", emoji = "💼", parentId = 6)
        assertTrue(SubcategoryRepair.plan(listOf(mm(1, 6, "راتب", type = TxnType.INCOME)), listOf(salary, expSubUnderIncome)).isEmpty())
    }
}
