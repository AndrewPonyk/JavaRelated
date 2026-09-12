package com.example.a4_fitness_tracker_app.ui.screens.workout

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.a4_fitness_tracker_app.domain.model.IntensityLevel
import com.example.a4_fitness_tracker_app.domain.model.WorkoutType
import com.example.a4_fitness_tracker_app.ui.components.SpecialRoutineMagicButton
import com.example.a4_fitness_tracker_app.ui.components.WorkoutCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutLoggingScreen(
    viewModel: WorkoutViewModel,
    onWorkoutLogged: (() -> Unit)? = null,
    onLogSpecialRoutine: ((locationTag: String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val formState by viewModel.formState.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val todaySpecialCount by viewModel.todaySpecialRoutineCount.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState) {
        when (uiState) {
            is WorkoutUiState.Success -> {
                snackbarHostState.showSnackbar((uiState as WorkoutUiState.Success).message)
                viewModel.resetUiState()
            }
            is WorkoutUiState.Error -> {
                snackbarHostState.showSnackbar((uiState as WorkoutUiState.Error).errorMessage)
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
                        text = "Log Workout",
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
            // Magic 1-Tap Special Routine Button
            item {
                SpecialRoutineMagicButton(
                    todayAttemptsCount = todaySpecialCount,
                    onLogRoutine = { location ->
                        if (onLogSpecialRoutine != null) {
                            onLogSpecialRoutine(location)
                        } else {
                            viewModel.logSpecialRoutine("user_default_1", location)
                        }
                    }
                )
            }

            // Activity Type Selection Chips
            item {
                Text(
                    text = "Or Custom Workout",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(WorkoutType.values()) { type ->
                        FilterChip(
                            selected = formState.selectedType == type,
                            onClick = { viewModel.onTypeSelected(type) },
                            label = { Text(type.name.replace("_", " ")) },
                            leadingIcon = if (formState.selectedType == type) {
                                { Icon(Icons.Default.Check, contentDescription = null) }
                            } else null
                        )
                    }
                }
            }

            // Input Fields Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = formState.durationText,
                            onValueChange = { viewModel.onDurationChanged(it) },
                            label = { Text("Duration (minutes)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = formState.caloriesText,
                            onValueChange = { viewModel.onCaloriesChanged(it) },
                            label = { Text("Calories Burned (kcal)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = formState.distanceText,
                            onValueChange = { viewModel.onDistanceChanged(it) },
                            label = { Text("Distance (km, optional)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Intensity Selection
                        Text(
                            text = "Intensity Level",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            IntensityLevel.values().forEach { intensity ->
                                FilterChip(
                                    selected = formState.selectedIntensity == intensity,
                                    onClick = { viewModel.onIntensitySelected(intensity) },
                                    label = { Text(intensity.name, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }

                        OutlinedTextField(
                            value = formState.notesText,
                            onValueChange = { viewModel.onNotesChanged(it) },
                            label = { Text("Notes (optional)") },
                            maxLines = 3,
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (formState.validationError != null) {
                            Text(
                                text = formState.validationError!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Button(
                            onClick = {
                                if (onWorkoutLogged != null) {
                                    onWorkoutLogged()
                                } else {
                                    viewModel.submitWorkout()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            enabled = uiState !is WorkoutUiState.Submitting,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            if (uiState is WorkoutUiState.Submitting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Save Workout",
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Recent Workouts Section
            item {
                Text(
                    text = "Recent Workouts",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            items(formState.recentWorkouts) { workout ->
                WorkoutCard(workout = workout)
            }
        }
    }
}
