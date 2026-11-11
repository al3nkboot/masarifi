package com.midroid.masarifi.ui.goals

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.midroid.masarifi.R
import com.midroid.masarifi.data.AppSettings
import com.midroid.masarifi.data.GoalContribution
import com.midroid.masarifi.data.GoalLogic
import com.midroid.masarifi.data.SavingsGoal
import com.midroid.masarifi.ui.AppViewModel
import com.midroid.masarifi.ui.Fmt
import com.midroid.masarifi.ui.components.EmptyState
import com.midroid.masarifi.ui.components.ProgressBar
import com.midroid.masarifi.ui.theme.AppTheme
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val goals by vm.goals.collectAsStateWithLifecycle()
    val contributions by vm.goalContributions.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<SavingsGoal?>(null) }
    var contributing by remember { mutableStateOf<Pair<SavingsGoal, Boolean>?>(null) }
    var expanded by remember { mutableStateOf<Long?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.goals)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = SavingsGoal(name = "", target = 0) }) {
                Icon(Icons.Filled.Add, stringResource(R.string.goal_add))
            }
        },
    ) { padding ->
        if (goals.isEmpty()) {
            EmptyState(stringResource(R.string.goal_empty), Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(bottom = 96.dp),
            ) {
                items(goals, key = { it.id }) { goal ->
                    GoalCard(
                        goal, contributions, s, expanded == goal.id,
                        onToggle = { expanded = if (expanded == goal.id) null else goal.id },
                        onEdit = { editing = goal },
                        onContribute = { contributing = goal to false },
                        onWithdraw = { contributing = goal to true },
                        onDeleteContribution = vm::deleteContribution,
                    )
                }
            }
        }
    }
    editing?.let { goal ->
        GoalEditDialog(
            initial = goal, accounts = accounts, s = s,
            onDismiss = { editing = null },
            onSave = { vm.saveGoal(it); editing = null },
            onDelete = if (goal.id != 0L) ({ vm.deleteGoal(goal); editing = null }) else null,
        )
    }
    contributing?.let { (goal, withdraw) ->
        ContributionDialog(
            title = stringResource(if (withdraw) R.string.goal_withdraw else R.string.goal_contribute),
            s = s, onDismiss = { contributing = null },
        ) { amount, note ->
            vm.addContribution(goal.id, if (withdraw) -amount else amount, note)
            contributing = null
        }
    }
}

@Composable
private fun GoalCard(
    goal: SavingsGoal, contributions: List<GoalContribution>, s: AppSettings, expanded: Boolean,
    onToggle: () -> Unit, onEdit: () -> Unit, onContribute: () -> Unit, onWithdraw: () -> Unit,
    onDeleteContribution: (GoalContribution) -> Unit,
) {
    val saved = GoalLogic.saved(goal.id, contributions)
    val progress = GoalLogic.progress(saved, goal.target)
    val percent = GoalLogic.percent(saved, goal.target)
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("goal_card").clickable { onToggle() },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row {
                Text(goal.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.goal_percent, percent),
                    style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.size(8.dp))
            ProgressBar(progress)
            Spacer(Modifier.size(6.dp))
            Text(stringResource(R.string.goal_saved_of, Fmt.money(saved, s), Fmt.money(goal.target, s)),
                style = MaterialTheme.typography.bodyMedium)
            if (saved >= goal.target && goal.target > 0) {
                Text(stringResource(R.string.goal_done), color = AppTheme.colors.income)
            } else {
                GoalLogic.monthlyNeeded(saved, goal.target, goal.targetDate, LocalDate.now())?.let {
                    Text(stringResource(R.string.goal_monthly_needed, Fmt.money(it, s)),
                        style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.muted)
                }
            }
            goal.targetDate?.let {
                Text(stringResource(R.string.goal_target_date) + ": " + LocalDate.ofEpochDay(it),
                    style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.muted)
            }
            Row {
                TextButton(onClick = onContribute) { Text(stringResource(R.string.goal_contribute)) }
                TextButton(onClick = onWithdraw) { Text(stringResource(R.string.goal_withdraw)) }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, stringResource(R.string.goal_edit)) }
            }
            if (expanded) {
                Text(stringResource(R.string.goal_history), style = MaterialTheme.typography.labelLarge)
                contributions.filter { it.goalId == goal.id }.forEach { contribution ->
                    Row {
                        Text(LocalDate.ofEpochDay(contribution.date).toString(),
                            modifier = Modifier.weight(1f), color = AppTheme.colors.muted)
                        Text("\u200E" + (if (contribution.amount > 0) "+" else "") + Fmt.money(contribution.amount, s),
                            color = if (contribution.amount > 0) AppTheme.colors.income else AppTheme.colors.expense)
                        IconButton(onClick = { onDeleteContribution(contribution) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Delete, stringResource(R.string.delete), modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}
