package com.midroid.masarifi.ui

import com.midroid.masarifi.data.TxnType

sealed interface Route {
    data object Home : Route
    data class Edit(val txnId: Long? = null, val date: Long? = null, val type: Int = TxnType.EXPENSE) : Route
    data object Search : Route
    data class TxnList(val title: String, val categoryId: Long? = null, val accountId: Long? = null, val from: Long? = null, val to: Long? = null, val note: String? = null, val type: Int? = null) : Route
    data object Categories : Route
    data object Recurring : Route
    data object SmsSettings : Route
    data object Backup : Route
    data object ImportExport : Route
    data object Duplicates : Route
    data object Sync : Route
    data object Repair : Route
    data object Summary : Route
    data class BillEdit(val billId: Long? = null) : Route
}
