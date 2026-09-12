package com.example.a4_fitness_tracker_app.ui.screens.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.a4_fitness_tracker_app.ui.components.*
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToLogWorkout: () -> Unit,
    onLogSpecialRoutine: ((locationTag: String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    val startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val todaySpecialCount = uiState.recentWorkouts.count {
        it.timestamp >= startOfDay && it.notes?.contains("15 Push ups + 40sec Plank + 15 StepUps") == true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Fitness Dashboard",
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { paddingValues ->
        when {
            uiState.isLoading -> {
                LoadingView(
                    message = "Calculating fitness progress...",
                    modifier = modifier.padding(paddingValues)
                )
            }
            uiState.errorMessage != null -> {
                ErrorView(
                    errorMessage = uiState.errorMessage!!,
                    onRetry = { viewModel.loadDashboardData() },
                    modifier = modifier.padding(paddingValues)
                )
            }
            else -> {
                LazyColumn(
                    modifier = modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Quick Magic Routine Card
                    if (onLogSpecialRoutine != null) {
                        item {
                            SpecialRoutineMagicButton(
                                todayAttemptsCount = todaySpecialCount,
                                onLogRoutine = onLogSpecialRoutine
                            )
                        }
                    }

                    // Daily Goals Progress Section
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Daily Calorie Burn Target",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(16.dp))

                                val calorieProgress = if (uiState.targetCalories > 0) {
                                    uiState.todayCalories.toFloat() / uiState.targetCalories.toFloat()
                                } else 0f

                                ProgressRing(
                                    progress = calorieProgress,
                                    currentValueText = "${uiState.todayCalories}",
                                    targetValueText = "${uiState.targetCalories} kcal",
                                    progressColor = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }
                    }

                    // Key Metric Cards
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            MetricSummaryCard(
                                title = "Workouts",
                                value = "${uiState.weeklyWorkoutsCount}",
                                icon = Icons.Default.FitnessCenter,
                                modifier = Modifier.weight(1f)
                            )
                            MetricSummaryCard(
                                title = "Active Time",
                                value = "${uiState.totalActiveMinutes}m",
                                icon = Icons.Default.Timer,
                                modifier = Modifier.weight(1f)
                            )
                            MetricSummaryCard(
                                title = "Total Burn",
                                value = "${uiState.todayCalories}",
                                icon = Icons.Default.LocalFireDepartment,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Recent Activity Header
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Recent Activity",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            TextButton(onClick = onNavigateToLogWorkout) {
                                Text(text = "+ Log New", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }

                    if (uiState.recentWorkouts.isEmpty()) {
                        item {
                            EmptyStateView(
                                message = "No recent activity found. Log your first workout to see analytics!",
                                onActionClick = onNavigateToLogWorkout
                            )
                        }
                    } else {
                        items(uiState.recentWorkouts) { workout ->
                            WorkoutCard(workout = workout)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MetricSummaryCard(
    title: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
