package com.midroid.masarifi.data

import kotlin.math.absoluteValue

data class NetWorth(val assets: Long, val liabilities: Long) { val net: Long get() = assets - liabilities }

object NetWorthCalc {
    /** [balances] = account to its current balance. Accounts with includeInTotal=false or deletedAt != null are ignored.
     *  LOAN accounts always count as a liability of abs(balance). Other accounts: positive balance = asset, negative = liability of -balance. */
    fun compute(balances: List<Pair<Account, Long>>): NetWorth {
        var assets = 0L
        var liabilities = 0L
        balances.forEach { (account, balance) ->
            if (!account.includeInTotal || account.deletedAt != null) return@forEach
            if (isLiability(account.groupType)) {
                liabilities += balance.absoluteValue
            } else if (balance >= 0) {
                assets += balance
            } else {
                liabilities += -balance
            }
        }
        return NetWorth(assets, liabilities)
    }

    fun isLiability(groupType: Int): Boolean = groupType == AccountGroup.LOAN
}
