package com.midroid.masarifi.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure merge rules (no Android): divergent snapshots, tombstones, LWW, ties, uniqueness, cascades, convergence. */
class SyncMergeTest {
    private val T0 = 1_790_000_000_000L
    private fun s(sec: Long) = T0 + sec * 1000

    private fun acc(uuid: String, at: Long, name: String = uuid) = SyncRecord.live(uuid, Kind.ACCOUNT, at, "name" to name, "groupType" to 0)
    private fun txn(uuid: String, at: Long, amount: Long, note: String = "", account: String = "acc", hash: String? = null) =
        SyncRecord.live(uuid, Kind.TXN, at, "type" to 1, "amount" to amount, "account" to account, "note" to note, "smsHash" to hash)
    private fun tomb(uuid: String, kind: String, at: Long) = SyncRecord(uuid, kind, at, at)
    private fun snap(vararg r: SyncRecord) = SyncSnapshot(r.associateBy { it.uuid })

    private val base = listOf(acc("acc", s(0)), txn("t1", s(0), 1_000, "قهوة"), txn("t2", s(0), 2_000, "غداء"), txn("t3", s(0), 3_000, "بنزين"))

    @Test fun divergentSnapshots_addOnA_editOnB_deleteOnA() {
        // A: adds t4, deletes t3. B: edits t1.
        val a = snap(*(base.filter { it.uuid != "t3" } + txn("t4", s(10), 4_000, "جديد") + tomb("t3", Kind.TXN, s(12))).toTypedArray())
        val b = snap(*(base.filter { it.uuid != "t1" } + txn("t1", s(11), 1_250, "قهوة كبيرة")).toTypedArray())

        // A syncs first (file = B's state, as if B pushed), then B syncs with A's result
        val onA = SyncMerge.merge(local = a, remote = b, lastSyncAt = s(5))
        val m = onA.merged.records
        assertEquals(1_250L, m["t1"]!!.long("amount"))          // B's edit arrives
        assertEquals("قهوة كبيرة", m["t1"]!!.str("note"))
        assertTrue(m["t3"]!!.deleted)                           // A's delete wins over B's untouched copy
        assertFalse(m["t4"]!!.deleted)                          // A's add is kept
        assertEquals(2_000L, m["t2"]!!.long("amount"))
        assertEquals(1, onA.stats.pulled)                       // t1
        assertEquals(2, onA.stats.pushed)                       // t3 delete, t4 add
        assertEquals(4, onA.stats.merged)                       // acc, t1, t2, t4
        assertEquals(0, onA.stats.conflicts)

        val onB = SyncMerge.merge(local = b, remote = onA.merged, lastSyncAt = s(5))
        assertEquals(onA.merged.records, onB.merged.records)    // both devices converge
        assertEquals(2, onB.stats.pulled)
        assertEquals(0, onB.stats.pushed)
        assertTrue(onB.toApply.map { it.uuid }.containsAll(listOf("t3", "t4")))
    }

    @Test fun mergeIsSymmetricWithoutTies() {
        val a = snap(acc("acc", s(0)), txn("t1", s(3), 1), txn("t2", s(9), 2), tomb("t3", Kind.TXN, s(7)))
        val b = snap(acc("acc", s(1), "Bank"), txn("t1", s(4), 10), txn("t2", s(8), 20), txn("t3", s(6), 30), txn("t5", s(2), 5))
        assertEquals(SyncMerge.merge(a, b).merged.records, SyncMerge.merge(b, a).merged.records)
    }

    @Test fun tombstoneWinsOverOlderEdit_andOnTie() {
        val local = snap(acc("acc", s(0)), txn("t1", s(30), 999, "edited"))
        val newerDelete = snap(acc("acc", s(0)), tomb("t1", Kind.TXN, s(40)))
        assertTrue(SyncMerge.merge(local, newerDelete).merged.records["t1"]!!.deleted)
        // same second: delete still wins, from either side (never resurrect on a tie)
        val tie = snap(acc("acc", s(0)), tomb("t1", Kind.TXN, s(30) + 400))
        assertTrue(SyncMerge.merge(local, tie).merged.records["t1"]!!.deleted)
        assertTrue(SyncMerge.merge(tie, local).merged.records["t1"]!!.deleted)
    }

    @Test fun deletedItemOnlyResurrectsWhenEditIsNewer() {
        val deleted = snap(acc("acc", s(0)), tomb("t1", Kind.TXN, s(20)))
        val olderEdit = snap(acc("acc", s(0)), txn("t1", s(19), 5))
        val newerEdit = snap(acc("acc", s(0)), txn("t1", s(21), 5))
        assertTrue(SyncMerge.merge(deleted, olderEdit).merged.records["t1"]!!.deleted)
        val r = SyncMerge.merge(deleted, newerEdit)
        assertFalse(r.merged.records["t1"]!!.deleted)
        assertEquals(1, r.stats.pulled)
    }

    @Test fun lastWriteWins_newerSideWinsEitherWay() {
        val older = snap(acc("acc", s(0)), txn("t1", s(10), 100, "old"))
        val newer = snap(acc("acc", s(0)), txn("t1", s(11), 200, "new"))
        assertEquals("new", SyncMerge.merge(older, newer).merged.records["t1"]!!.str("note"))
        assertEquals("new", SyncMerge.merge(newer, older).merged.records["t1"]!!.str("note"))
    }

    @Test fun sameSecondTiePrefersLocal_andStillConverges() {
        val local = snap(acc("acc", s(0)), txn("t1", s(10) + 100, 100, "local"))
        val remote = snap(acc("acc", s(0)), txn("t1", s(10) + 900, 200, "remote")) // 0.8 s later, same second
        val r = SyncMerge.merge(local, remote, lastSyncAt = s(5))
        val kept = r.merged.records["t1"]!!
        assertEquals("local", kept.str("note"))
        assertEquals(s(11), kept.updatedAt)          // re-stamped to the next second
        assertEquals(0, r.stats.pulled)
        assertEquals(1, r.stats.pushed)
        assertEquals(1, r.stats.conflicts)           // both changed since the last sync
        // the other phone (which still has "remote") now adopts the kept version instead of keeping its own
        val other = SyncMerge.merge(remote, r.merged, lastSyncAt = s(5))
        assertEquals("local", other.merged.records["t1"]!!.str("note"))
        assertEquals(r.merged.records, other.merged.records)
        assertTrue(SyncMerge.merge(r.merged, other.merged).toApply.isEmpty())
    }

    @Test fun identicalContentIsNotAConflict() {
        val a = snap(acc("acc", s(0)), txn("t1", s(10) + 100, 100, "x"))
        val b = snap(acc("acc", s(0)), txn("t1", s(10) + 900, 100, "x"))
        val r = SyncMerge.merge(a, b, lastSyncAt = 0)
        assertEquals(0, r.stats.conflicts); assertEquals(0, r.stats.pulled); assertEquals(0, r.stats.pushed)
        assertEquals(r.merged.records, SyncMerge.merge(b, a).merged.records)
    }

    @Test fun conflictsOnlyCountedWhenBothSidesChangedSinceLastSync() {
        val local = snap(acc("acc", s(0)), txn("t1", s(3), 1))
        val remote = snap(acc("acc", s(0)), txn("t1", s(20), 2))
        assertEquals(0, SyncMerge.merge(local, remote, lastSyncAt = s(10)).stats.conflicts)
        assertEquals(1, SyncMerge.merge(local, remote, lastSyncAt = s(1)).stats.conflicts)
    }

    @Test fun duplicateSmsHashOrImportFingerprintKeepsOne() {
        // the same bank SMS imported on both phones before they synced
        val a = snap(acc("acc", s(0)), txn("aaaa", s(5), 4_300, "LULU", hash = "sms-h1"), txn("m1", s(1), 10, hash = "mm:fp1"))
        val b = snap(acc("acc", s(0)), txn("bbbb", s(6), 4_300, "LULU", hash = "sms-h1"), txn("m2", s(2), 10, hash = "mm:fp1"))
        val onA = SyncMerge.merge(a, b)
        val onB = SyncMerge.merge(b, a)
        for (r in listOf(onA, onB)) {
            val live = r.merged.live.filter { it.kind == Kind.TXN }
            assertEquals(1, live.count { it.str("smsHash") == "sms-h1" })
            assertEquals(1, live.count { it.str("smsHash") == "mm:fp1" })
            assertEquals(2, r.stats.duplicatesDropped)
        }
        assertEquals(onA.merged.records, onB.merged.records)
        assertFalse(onA.merged.records["aaaa"]!!.deleted)
        assertTrue(onA.merged.records["bbbb"]!!.deleted)
        // stable: merging again changes nothing
        val again = SyncMerge.merge(onA.merged, onB.merged)
        assertTrue(again.toApply.isEmpty())
    }

    @Test fun deletedAccountCascadesToItsTransactionsAndLinks() {
        val a = snap(tomb("acc", Kind.ACCOUNT, s(50)))
        val b = snap(acc("acc", s(0)), txn("t9", s(40), 1),
            SyncRecord.live("l1", Kind.SMS_LINK, s(41), "txn" to "t9", "smsHash" to "h-link"))
        val m = SyncMerge.merge(b, a).merged.records
        assertTrue(m["acc"]!!.deleted)
        assertTrue(m["t9"]!!.deleted)
        assertTrue(m["l1"]!!.deleted)
    }

    @Test fun mergingTheResultAgainIsANoOp() {
        val a = snap(acc("acc", s(0)), txn("t1", s(3), 1), tomb("t2", Kind.TXN, s(4)))
        val b = snap(acc("acc", s(1), "Bank"), txn("t3", s(5), 3))
        val m = SyncMerge.merge(a, b).merged
        val again = SyncMerge.merge(m, m)
        assertTrue(again.toApply.isEmpty())
        assertEquals(0, again.stats.pulled)
        assertEquals(0, again.stats.pushed)
        assertFalse(SyncMerge.needsPush(m, m))
    }

    @Test fun growOnlySetsAreUnioned() {
        val a = SyncSnapshot(smsLog = mapOf("h1" to SmsLogEntry("BankMuscat", 1, 1, "t1")), dismissed = setOf(SyncSnapshot.pairKey("t1", "t2")))
        val b = SyncSnapshot(smsLog = mapOf("h2" to SmsLogEntry("NBO", 2, 0, null)), dismissed = setOf(SyncSnapshot.pairKey("t4", "t3")))
        val m = SyncMerge.merge(a, b).merged
        assertEquals(setOf("h1", "h2"), m.smsLog.keys)
        assertEquals(setOf("t1|t2", "t3|t4"), m.dismissed)
        assertTrue(SyncMerge.needsPush(m, b))
        assertNull(SyncMerge.merge(SyncSnapshot(), SyncSnapshot()).merged.fileId)
    }
}
