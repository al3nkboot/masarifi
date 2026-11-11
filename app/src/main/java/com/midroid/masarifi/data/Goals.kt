package com.midroid.masarifi.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Entity(
    tableName = "savings_goals",
    indices = [Index(value = ["uuid"], unique = true)]
)
data class SavingsGoal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val target: Long, /*thousandths*/
    val targetDate: Long? = null, /*epoch day*/
    val accountId: Long? = null,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "") val uuid: String = newUuid(),
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "goal_contributions",
    indices = [Index("goalId"), Index(value = ["uuid"], unique = true)]
)
data class GoalContribution(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val goalId: Long,
    val amount: Long, /*negative = withdrawal*/
    val date: Long, /*epoch day*/
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "") val uuid: String = newUuid(),
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = System.currentTimeMillis()
)

@Dao
interface GoalDao {
    @Query("SELECT * FROM savings_goals WHERE uuid = :uuid")
    suspend fun goalByUuid(uuid: String): SavingsGoal?

    @Query("SELECT * FROM goal_contributions WHERE uuid = :uuid")
    suspend fun contributionByUuid(uuid: String): GoalContribution?

    @Query("SELECT * FROM savings_goals ORDER BY id")
    fun observeGoals(): Flow<List<SavingsGoal>>

    @Query("SELECT * FROM goal_contributions ORDER BY date DESC, id DESC")
    fun observeContributions(): Flow<List<GoalContribution>>

    @Insert
    suspend fun insertGoal(g: SavingsGoal): Long

    @Update
    suspend fun updateGoal(g: SavingsGoal)

    @Update
    suspend fun updateContribution(c: GoalContribution)

    @Query("DELETE FROM savings_goals WHERE id = :id")
    suspend fun deleteGoal(id: Long)

    @Query("DELETE FROM goal_contributions WHERE goalId = :goalId")
    suspend fun deleteContributionsOf(goalId: Long)

    @Insert
    suspend fun insertContribution(c: GoalContribution): Long

    @Query("DELETE FROM goal_contributions WHERE id = :id")
    suspend fun deleteContribution(id: Long)

    @Query("DELETE FROM goal_contributions WHERE uuid = :uuid")
    suspend fun deleteContributionByUuid(uuid: String)

    @Query("UPDATE savings_goals SET accountId = NULL WHERE accountId = :id")
    suspend fun clearAccount(id: Long)

    @Query("SELECT * FROM savings_goals ORDER BY id")
    suspend fun allGoals(): List<SavingsGoal>

    @Query("SELECT * FROM goal_contributions ORDER BY date DESC, id DESC")
    suspend fun allContributions(): List<GoalContribution>

    @Query("DELETE FROM goal_contributions")
    suspend fun clearContributions()

    @Query("DELETE FROM savings_goals")
    suspend fun clearGoals()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGoals(l: List<SavingsGoal>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContributions(l: List<GoalContribution>)
}

object GoalLogic {
    fun saved(goalId: Long, contributions: List<GoalContribution>): Long =
        contributions.filter { it.goalId == goalId }
            .sumOf { it.amount }
            .coerceAtLeast(0)

    fun progress(saved: Long, target: Long): Double =
        if (target <= 0) 0.0 else (saved.toDouble() / target).coerceIn(0.0, 1.0)

    fun percent(saved: Long, target: Long): Int =
        (progress(saved, target) * 100).toInt()

    fun monthlyNeeded(
        saved: Long,
        target: Long,
        targetDate: Long?,
        today: LocalDate
    ): Long? {
        if (targetDate == null || saved >= target) return null

        val months = ChronoUnit.MONTHS.between(today, LocalDate.ofEpochDay(targetDate))
            .coerceAtLeast(1)
        val remaining = target - saved
        return remaining / months + if (remaining % months == 0L) 0 else 1
    }

    fun topGoal(
        goals: List<SavingsGoal>,
        contributions: List<GoalContribution>
    ): SavingsGoal? {
        if (goals.isEmpty()) return null

        val incomplete = goals.filter { saved(it.id, contributions) < it.target }
        if (incomplete.isEmpty()) return goals.first()

        return incomplete.maxByOrNull {
            progress(saved(it.id, contributions), it.target)
        }
    }
}
