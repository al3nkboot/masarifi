package com.midroid.masarifi.data.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Sync file serialization round trip (org.json needs Robolectric). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SyncCodecTest {
    private val sample = SyncSnapshot(
        records = listOf(
            SyncRecord.live("acc-cash", Kind.ACCOUNT, 0, "name" to "نقد", "groupType" to 0, "initialBalance" to 0L, "cardLast4" to null, "includeInTotal" to true, "sortOrder" to 0),
            SyncRecord.live("t1", Kind.TXN, 1_790_000_000_123, "type" to 1, "amount" to 4_300L, "account" to "acc-cash", "category" to "cat-food",
                "note" to "غداء \"خاص\"", "smsHash" to null, "isAuto" to false, "dateTime" to 1_790_000_000_000L),
            SyncRecord("t2", Kind.TXN, 1_790_000_100_000, 1_790_000_100_000),
            SyncRecord.live("b:overall", Kind.BUDGET, 5, "category" to null, "amount" to 300_000L),
            SyncRecord.live("settings", Kind.SETTINGS, 9, "currency" to "OMR", "dupWindowHours" to 48, "weekStartsSaturday" to true),
        ).associateBy { it.uuid },
        smsLog = mapOf("h1" to SmsLogEntry("BankMuscat", 1_790_000_000_000, 1, "t1"), "h2" to SmsLogEntry("NBO", 2, 0, null)),
        dismissed = setOf("t1|t9"),
        devices = mapOf("dev-a" to DeviceInfo("Pixel 8", 1_790_000_200_000, "1.3.0")),
        fileId = "f1e2d3c4-0000-4000-8000-000000000001",
        updatedAt = 1_790_000_200_000,
    )

    @Test fun roundTripIsLossless() {
        val json = SyncCodec.encode(sample)
        val back = SyncCodec.decode(json)
        assertEquals(sample, back)
        assertEquals(json, SyncCodec.encode(back)) // stable output (sorted) → no spurious rewrites
    }

    @Test fun blankFileIsAnEmptySnapshot() {
        assertEquals(SyncSnapshot(), SyncCodec.decode(""))
        assertEquals(SyncSnapshot(), SyncCodec.decode(null))
    }

    @Test fun rejectsOtherFilesAndNewerVersions() {
        val e1 = assertThrows(SyncFormatException::class.java) { SyncCodec.decode("""{"format":"masarifi-backup","schemaVersion":3}""") }
        assertEquals("not_sync", e1.code)
        val e2 = assertThrows(SyncFormatException::class.java) { SyncCodec.decode("hello") }
        assertEquals("not_json", e2.code)
        val e3 = assertThrows(SyncFormatException::class.java) { SyncCodec.decode(SyncCodec.encode(sample).replace("\"version\":1", "\"version\":7")) }
        assertEquals("newer", e3.code)
    }

    @Test fun unknownKindsAreKeptOnRewrite() {
        val withFuture = SyncCodec.encode(sample).replace("\"entities\":{", "\"entities\":{\"zz_future\":[{\"uuid\":\"x1\",\"updatedAt\":3,\"foo\":\"bar\"}],")
        val d = SyncCodec.decode(withFuture)
        assertEquals("bar", d.records["x1"]!!.str("foo"))
        assertTrue(SyncCodec.encode(d).contains("zz_future"))
    }
}
