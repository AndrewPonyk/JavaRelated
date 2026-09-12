package com.example.a4_fitness_tracker_app.ui.screens.goals

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.a4_fitness_tracker_app.domain.model.GoalType
import com.example.a4_fitness_tracker_app.ui.components.EmptyStateView
import com.example.a4_fitness_tracker_app.ui.components.GoalCard
import com.example.a4_fitness_tracker_app.ui.components.LoadingView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsScreen(
    viewModel: GoalsViewModel,
    modifier: Modifier = Modifier
) {
    val screenState by viewModel.screenState.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState) {
        when (uiState) {
            is GoalsUiState.Success -> {
                snackbarHostState.showSnackbar((uiState as GoalsUiState.Success).message)
                viewModel.resetUiState()
            }
            is GoalsUiState.Error -> {
                snackbarHostState.showSnackbar((uiState as GoalsUiState.Error).errorMessage)
                viewModel.resetUiState()
            }
            else -> {}
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Fitness Goals",
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
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Create New Goal Section
            item {
                Text(
                    text = "Set a New Goal",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Select Goal Type", style = MaterialTheme.typography.bodyMedium)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(GoalType.values()) { type ->
                                FilterChip(
                                    selected = screenState.selectedType == type,
                                    onClick = { viewModel.onTypeSelected(type) },
                                    label = { Text(type.name.replace("_", " ")) }
                                )
                            }
                        }

                        OutlinedTextField(
                            value = screenState.targetValueText,
                            onValueChange = { viewModel.onTargetValueChanged(it) },
                            label = { Text("Target Value (e.g. 10000 steps, 2500 kcal)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (screenState.validationError != null) {
                            Text(
                                text = screenState.validationError!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Button(
                            onClick = { viewModel.createGoal() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Add Goal", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Active Goals Section
            item {
                Text(
                    text = "Active Goals",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            when {
                screenState.isLoading && screenState.activeGoals.isEmpty() -> {
                    item { LoadingView(message = "Loading goals...") }
                }
                screenState.activeGoals.isEmpty() -> {
                    item {
                        EmptyStateView(
                            message = "No active goals set. Create a new target above to keep yourself motivated!"
                        )
                    }
                }
                else -> {
                    items(screenState.activeGoals) { goal ->
                        GoalCard(
                            goal = goal,
                            onProgressAdd = { amount ->
                                viewModel.addProgressToGoal(goal.id, amount)
                            }
                        )
                    }
                }
            }
        }
    }
}
