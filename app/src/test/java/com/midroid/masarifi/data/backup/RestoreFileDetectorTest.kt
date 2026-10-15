package com.midroid.masarifi.data.backup

import com.midroid.masarifi.data.backup.RestoreFileKind.APP_BACKUP
import com.midroid.masarifi.data.backup.RestoreFileKind.APP_CSV
import com.midroid.masarifi.data.backup.RestoreFileKind.MONEY_MANAGER
import com.midroid.masarifi.data.backup.RestoreFileKind.SYNC_FILE
import com.midroid.masarifi.data.backup.RestoreFileKind.UNKNOWN
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RestoreFileDetectorTest {
    private fun d(s: String, name: String? = null) = RestoreFileDetector.detect(s.toByteArray(), name)

    @Test fun appBackupJson() {
        assertEquals(APP_BACKUP, d("""{"format":"masarifi-backup","schemaVersion":4,"accounts":[]}"""))
        assertEquals(APP_BACKUP, d("\uFEFF  \n{ \"schemaVersion\": 1, \"format\" : \"masarifi-backup\" }"))
        // format key far beyond the sniffed head is still found
        assertEquals(APP_BACKUP, d("{\"pad\":\"" + "x".repeat(70_000) + "\",\"format\":\"masarifi-backup\"}"))
    }

    @Test fun realEncodedBackupIsDetected() {
        val json = BackupCodec.encode(BackupData(createdAt = 1, appVersion = "1.7.2", settings = null, accounts = emptyList(), categories = emptyList(),
            transactions = emptyList(), recurring = emptyList(), budgets = emptyList(), merchantRules = emptyList(), smsLog = emptyList()))
        assertEquals(APP_BACKUP, d(json))
    }

    @Test fun syncFileAndOtherJson() {
        assertEquals(SYNC_FILE, d("""{"format":"masarifi-sync","records":[]}"""))
        assertEquals(UNKNOWN, d("""{"hello":"world"}"""))
        assertEquals(UNKNOWN, d("not json {"))
    }

    @Test fun appCsvExport() {
        assertEquals(APP_CSV, d("\uFEFFDate,Type,Account,To Account,Category,Amount,Currency,Note,Merchant,Auto,Description\r\n2026-01-01 10:00,Expense,Cash,,Food,1.500,OMR,,,,\r\n"))
        assertEquals(APP_CSV, d("\"Date\",\"Type\",\"Account\",\"To Account\",\"Category\",\"Amount\"\n"))
    }

    @Test fun moneyManagerFiles() {
        assertEquals(MONEY_MANAGER, d("Period\tAccounts\tCategory\tSubcategory\tNote\tOMR\tIncome/Expense\tDescription\tAmount\tCurrency\n"))
        assertEquals(MONEY_MANAGER, d("Date,Account,Category,Subcategory,Note,OMR,Income/Expense,Description,Amount,Currency\n"))
        assertEquals(MONEY_MANAGER, d("<html><body><table><tr><td>Period</td></tr></table></body></html>"))
        val xlsx = byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 3, 4) + "....xl/workbook.xml....".toByteArray()
        assertEquals(MONEY_MANAGER, RestoreFileDetector.detect(xlsx))
        assertEquals(UNKNOWN, RestoreFileDetector.detect(byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 3, 4, 1, 2, 3)))
    }

    @Test fun unknownText() {
        assertEquals(UNKNOWN, d("hello world"))
        assertEquals(UNKNOWN, d(""))
    }
}
