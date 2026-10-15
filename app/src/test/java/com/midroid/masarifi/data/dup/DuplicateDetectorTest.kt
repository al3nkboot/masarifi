package com.midroid.masarifi.data.dup

import com.midroid.masarifi.data.DupDismissed
import com.midroid.masarifi.data.Txn
import com.midroid.masarifi.data.TxnType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateDetectorTest {
    private val h = DuplicateDetector.HOUR
    private val t0 = 1_790_000_000_000L
    private fun manual(id: Long, amount: Long, at: Long, type: Int = TxnType.EXPENSE, cat: Long? = 1, note: String = "", acc: Long = 1, to: Long? = null) =
        Txn(id = id, type = type, amount = amount, accountId = acc, toAccountId = to, categoryId = cat, note = note, dateTime = at, uuid = "u$id", createdAt = 0, updatedAt = 0)
    private fun sms(id: Long, amount: Long, at: Long) =
        Txn(id = id, type = TxnType.EXPENSE, amount = amount, accountId = 2, categoryId = 9, note = "LULU", dateTime = at, isAuto = true, smsHash = "h$id", merchant = "LULU", uuid = "u$id", createdAt = 0, updatedAt = 0)

    @Test fun linksOnExactSingleMatch() {
        val m = DuplicateDetector.matchSms(TxnType.EXPENSE, 4_300, t0, listOf(manual(1, 4_300, t0 - 30 * h), manual(2, 9_000, t0)), 48)
        assertEquals(SmsMatch.Link(manual(1, 4_300, t0 - 30 * h)), m)
    }

    @Test fun flagsWhenSeveralMatchInWindow() {
        val m = DuplicateDetector.matchSms(TxnType.EXPENSE, 4_300, t0, listOf(manual(1, 4_300, t0 - 30 * h), manual(2, 4_300, t0 + 2 * h)), 48)
        assertTrue(m is SmsMatch.Flag && m.strong)
        assertEquals(listOf(2L, 1L), (m as SmsMatch.Flag).candidates.map { it.id }) // closest first
    }

    @Test fun windowIsConfigurable() {
        val existing = listOf(manual(1, 4_300, t0 - 30 * h))
        assertTrue(DuplicateDetector.matchSms(TxnType.EXPENSE, 4_300, t0, existing, 48) is SmsMatch.Link)
        // 24h window: same amount 30h away is only a weak match → flagged, not linked
        val weak = DuplicateDetector.matchSms(TxnType.EXPENSE, 4_300, t0, existing, 24)
        assertTrue(weak is SmsMatch.Flag && !weak.strong)
    }

    @Test fun noMatchOutsideSevenDays() {
        assertEquals(SmsMatch.None, DuplicateDetector.matchSms(TxnType.EXPENSE, 4_300, t0, listOf(manual(1, 4_300, t0 - 8 * 24 * h)), 72))
    }

    @Test fun amountsDifferingByOneBaisaDoNotMatch() {
        val existing = listOf(manual(1, 4_299, t0), manual(2, 4_301, t0))
        assertEquals(SmsMatch.None, DuplicateDetector.matchSms(TxnType.EXPENSE, 4_300, t0, existing, 48))
        assertTrue(DuplicateDetector.manualConflicts(manual(0, 4_300, t0), existing, 48).isEmpty())
    }

    @Test fun typeMustMatchAndTransfersAreHandled() {
        // an income with the same amount is not a match for an expense SMS
        assertEquals(SmsMatch.None, DuplicateDetector.matchSms(TxnType.EXPENSE, 4_300, t0, listOf(manual(1, 4_300, t0, TxnType.INCOME)), 48))
        // SMS never links to transfers / transfers never get SMS links
        assertEquals(SmsMatch.None, DuplicateDetector.matchSms(TxnType.TRANSFER, 50_000, t0, listOf(manual(1, 50_000, t0, TxnType.TRANSFER, null, acc = 1, to = 2)), 48))
        assertEquals(SmsMatch.None, DuplicateDetector.matchSms(TxnType.EXPENSE, 50_000, t0, listOf(manual(1, 50_000, t0, TxnType.TRANSFER, null, acc = 1, to = 2)), 48))
        // manual transfers only conflict with transfers between the same accounts
        val tr = manual(1, 50_000, t0, TxnType.TRANSFER, null, acc = 1, to = 2)
        assertEquals(listOf(1L), DuplicateDetector.manualConflicts(manual(0, 50_000, t0 + h, TxnType.TRANSFER, null, acc = 1, to = 2), listOf(tr), 48).map { it.id })
        assertTrue(DuplicateDetector.manualConflicts(manual(0, 50_000, t0 + h, TxnType.TRANSFER, null, acc = 3, to = 2), listOf(tr), 48).isEmpty())
        // scan: SMS vs transfer is never paired
        assertTrue(DuplicateDetector.scan(listOf(tr, sms(5, 50_000, t0)), 48, emptySet()).isEmpty())
    }

    @Test fun smsEntriesAndLinkedOnesAreNotLinkTargets_importsCountAsManual() {
        val imported = manual(1, 4_300, t0).copy(smsHash = "mm:abc")
        assertTrue(DuplicateDetector.matchSms(TxnType.EXPENSE, 4_300, t0, listOf(imported), 48) is SmsMatch.Link)
        val alreadyLinked = manual(2, 4_300, t0).copy(smsLinked = true)
        assertEquals(SmsMatch.None, DuplicateDetector.matchSms(TxnType.EXPENSE, 4_300, t0, listOf(alreadyLinked, sms(3, 4_300, t0)), 48))
    }

    @Test fun manualConflictsPreferSmsEntries() {
        val list = listOf(manual(1, 4_300, t0 + h), sms(2, 4_300, t0 + 5 * h))
        assertEquals(listOf(2L, 1L), DuplicateDetector.manualConflicts(manual(0, 4_300, t0), list, 48).map { it.id })
    }

    @Test fun scanFindsSmsPairsExactDuplicatesAndRespectsDismissed() {
        val txns = listOf(
            manual(1, 4_300, t0), sms(2, 4_300, t0 + 3 * h),                                // SMS ↔ manual
            manual(3, 1_500, t0, note = "كرك"), manual(4, 1_500, t0 + 5 * 60_000, note = "كرك"), // exact duplicates
            manual(5, 1_500, t0 + 2 * h, note = "كرك"),                                        // >10 min: not exact
            sms(6, 7_000, t0), sms(7, 7_000, t0 + h),                                          // two SMS, different hashes: not paired
        )
        val groups = DuplicateDetector.scan(txns, 48, emptySet())
        assertEquals(setOf(listOf(1L, 2L), listOf(3L, 4L)), groups.map { it.ids.sorted() }.toSet())
        assertEquals(DupReason.SMS_MATCH, groups.single { 2L in it.ids }.reason)
        assertEquals(DupReason.EXACT, groups.single { 3L in it.ids }.reason)
        val after = DuplicateDetector.scan(txns, 48, setOf(DupDismissed.key(2, 1)))
        assertEquals(listOf(listOf(3L, 4L)), after.map { it.ids.sorted() })
    }

    @Test fun flaggedPairsAppearInScan() {
        val txns = listOf(manual(1, 4_300, t0 - 5 * 24 * h), sms(2, 4_300, t0).copy(possibleDupOf = "1,99"))
        assertEquals(listOf(listOf(1L, 2L)), DuplicateDetector.scan(txns, 48, emptySet()).map { it.ids.sorted() })
    }

    @Test fun mergeKeepsManualDataAndSmsMetadata() {
        val m = manual(1, 4_300, t0, cat = 3, note = "غداء")
        val s = sms(2, 4_300, t0 + h)
        val plan = DuplicateDetector.planMerge(listOf(s, m), now = 5)
        assertEquals(1L, plan.keeper.id)
        assertEquals(3L, plan.keeper.categoryId)
        assertEquals("غداء", plan.keeper.note)
        assertEquals("LULU", plan.keeper.merchant)
        assertTrue(plan.keeper.smsLinked)
        assertEquals(listOf(2L), plan.deleteIds)
        assertEquals("h2", plan.links.single().smsHash)
        // split recreates the SMS transaction exactly (hash, amount, time, account, category)
        val back = DuplicateDetector.splitTxn(plan.links.single())
        assertEquals(s.copy(id = 0, createdAt = back.createdAt, uuid = back.uuid, updatedAt = back.updatedAt), back)
    }
}
