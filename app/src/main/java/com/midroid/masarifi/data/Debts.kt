package com.midroid.masarifi.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import kotlin.math.abs

object DebtDirection {
    const val LENT = 0 // they owe me
    const val OWE = 1 // I owe them
}

@Entity(
    tableName = "debts",
    indices = [Index("person"), Index(value = ["uuid"], unique = true)]
)
data class Debt(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val person: String,
    val direction: Int,
    val amount: Long,
    val dateTime: Long, // millis
    val note: String = "",
    val splitTxnId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "") val uuid: String = newUuid(),
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "debt_settlements",
    indices = [Index("debtId"), Index(value = ["uuid"], unique = true)]
)
data class DebtSettlement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val debtId: Long,
    val amount: Long,
    val dateTime: Long,
    val txnId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "") val uuid: String = newUuid(),
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = System.currentTimeMillis()
)

@Dao
interface DebtDao {
    @Query("SELECT * FROM debts ORDER BY dateTime DESC, id DESC")
    fun observeDebts(): Flow<List<Debt>>

    @Query("SELECT * FROM debt_settlements ORDER BY dateTime DESC, id DESC")
    fun observeSettlements(): Flow<List<DebtSettlement>>

    @Insert
    suspend fun insertDebt(d: Debt): Long

    @Update
    suspend fun updateDebt(d: Debt)

    @Query("DELETE FROM debts WHERE id = :id")
    suspend fun deleteDebt(id: Long)

    @Query("DELETE FROM debt_settlements WHERE debtId = :debtId")
    suspend fun deleteSettlementsOf(debtId: Long)

    @Insert
    suspend fun insertSettlement(s: DebtSettlement): Long

    @Query("DELETE FROM debt_settlements WHERE id = :id")
    suspend fun deleteSettlement(id: Long)

    @Query("SELECT * FROM debts")
    suspend fun allDebts(): List<Debt>

    @Query("SELECT * FROM debt_settlements")
    suspend fun allSettlements(): List<DebtSettlement>
}

data class DebtTotals(val owedToMe: Long, val iOwe: Long) {
    val net: Long get() = owedToMe - iOwe
}

object DebtLogic {
    fun settled(debtId: Long, s: List<DebtSettlement>): Long =
        s.filter { it.debtId == debtId }.sumOf { it.amount }

    fun remaining(d: Debt, s: List<DebtSettlement>): Long =
        (d.amount - settled(d.id, s)).coerceAtLeast(0)

    fun isSettled(d: Debt, s: List<DebtSettlement>): Boolean =
        remaining(d, s) == 0L

    fun totals(debts: List<Debt>, s: List<DebtSettlement>): DebtTotals {
        var owedToMe = 0L
        var iOwe = 0L
        debts.forEach { debt ->
            val amount = remaining(debt, s)
            when (debt.direction) {
                DebtDirection.LENT -> owedToMe += amount
                DebtDirection.OWE -> iOwe += amount
            }
        }
        return DebtTotals(owedToMe, iOwe)
    }

    fun split(total: Long, people: Int): List<Long> {
        require(people >= 1) { "people must be at least 1" }
        val share = total / people
        val remainder = total % people
        return List(people) { index ->
            if (index == 0) share + remainder else share
        }
    }

    fun byPerson(debts: List<Debt>, s: List<DebtSettlement>): List<Pair<String, Long>> {
        val totalsByPerson = mutableMapOf<String, Long>()
        debts.forEach { debt ->
            val person = debt.person.trim()
            if (person.isNotEmpty()) {
                val amount = remaining(debt, s)
                val signed = if (debt.direction == DebtDirection.LENT) amount else -amount
                totalsByPerson[person] = (totalsByPerson[person] ?: 0L) + signed
            }
        }
        return totalsByPerson
            .filter { it.value != 0L }
            .map { it.key to it.value }
            .sortedByDescending { abs(it.second) }
    }
}
