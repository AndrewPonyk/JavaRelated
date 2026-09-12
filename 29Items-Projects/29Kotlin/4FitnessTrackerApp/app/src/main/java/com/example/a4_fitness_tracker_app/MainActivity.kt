package com.example.a4_fitness_tracker_app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.a4_fitness_tracker_app.ui.components.UserSelectionDialog
import com.example.a4_fitness_tracker_app.ui.screens.dashboard.DashboardScreen
import com.example.a4_fitness_tracker_app.ui.screens.dashboard.DashboardViewModel
import com.example.a4_fitness_tracker_app.ui.screens.goals.GoalsScreen
import com.example.a4_fitness_tracker_app.ui.screens.goals.GoalsViewModel
import com.example.a4_fitness_tracker_app.ui.screens.recommendations.RecommendationScreen
import com.example.a4_fitness_tracker_app.ui.screens.recommendations.RecommendationViewModel
import com.example.a4_fitness_tracker_app.ui.screens.settings.ServerSettingsScreen
import com.example.a4_fitness_tracker_app.ui.screens.settings.ServerSettingsViewModel
import com.example.a4_fitness_tracker_app.ui.screens.workout.WorkoutLoggingScreen
import com.example.a4_fitness_tracker_app.ui.screens.workout.WorkoutViewModel
import com.example.a4_fitness_tracker_app.ui.theme.FitnessTrackerTheme

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    data object Dashboard : Screen("dashboard", "Dashboard", Icons.Default.Dashboard)
    data object LogWorkout : Screen("log_workout", "Log Workout", Icons.Default.AddCircle)
    data object Goals : Screen("goals", "Goals", Icons.Default.Flag)
    data object Recommendations : Screen("recommendations", "AI Coach", Icons.Default.AutoAwesome)
    data object Settings : Screen("settings", "Database Config", Icons.Default.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as FitnessTrackerApp
        val userSessionManager = app.userSessionManager
        val databaseConfigManager = app.databaseConfigManager

        val workoutViewModel = WorkoutViewModel(
            logWorkoutUseCase = app.logWorkoutUseCase,
            repository = app.workoutRepository
        )
        val dashboardViewModel = DashboardViewModel(
            repository = app.workoutRepository
        )
        val goalsViewModel = GoalsViewModel(
            getGoalsUseCase = app.getGoalsUseCase,
            updateGoalProgressUseCase = app.updateGoalProgressUseCase
        )
        val recommendationViewModel = RecommendationViewModel(
            getRecommendationsUseCase = app.getRecommendationsUseCase
        )
        val serverSettingsViewModel = ServerSettingsViewModel(
            configManager = databaseConfigManager,
            workoutRepository = app.workoutRepository,
            directMySqlManager = app.directMySqlManager
        )

        setContent {
            FitnessTrackerTheme {
                val currentNickname by userSessionManager.currentNickname.collectAsState()
                var showUserDialog by remember { mutableStateOf(currentNickname == null) }

                val navController = rememberNavController()
                val items = listOf(
                    Screen.Dashboard,
                    Screen.LogWorkout,
                    Screen.Goals,
                    Screen.Recommendations
                )

                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                // User Onboarding / Profile Selection Dialog
                if (showUserDialog || currentNickname == null) {
                    UserSelectionDialog(
                        currentNickname = currentNickname,
                        recentUsers = userSessionManager.getRecentUsers(),
                        onSelectUser = { selectedNickname ->
                            userSessionManager.setActiveNickname(selectedNickname)
                            showUserDialog = false
                            // Refresh ViewModels with new user profile context
                            val userId = userSessionManager.getActiveUserId()
                            recommendationViewModel.refreshRecommendations(userId)
                            dashboardViewModel.loadDashboardData()
                        },
                        onDismissRequest = {
                            if (currentNickname != null) {
                                showUserDialog = false
                            }
                        }
                    )
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    text = "Fitness Tracker",
                                    style = MaterialTheme.typography.titleLarge
                                )
                            },
                            actions = {
                                // Active user profile switcher chip
                                InputChip(
                                    selected = true,
                                    onClick = { showUserDialog = true },
                                    label = { Text(currentNickname ?: "Select User") },
                                    avatar = {
                                        Icon(
                                            imageVector = Icons.Default.AccountCircle,
                                            contentDescription = "Switch Profile",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    },
                                    colors = InputChipDefaults.inputChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        selectedLabelColor = MaterialTheme.colorScheme.onSurface
                                    )
                                )

                                // Database & Server Settings Icon
                                IconButton(onClick = { navController.navigate(Screen.Settings.route) }) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = "Database Config",
                                        tint = if (currentRoute == Screen.Settings.route) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.background
                            )
                        )
                    },
                    bottomBar = {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            items.forEach { screen ->
                                NavigationBarItem(
                                    icon = { Icon(screen.icon, contentDescription = screen.title) },
                                    label = { Text(screen.title) },
                                    selected = currentRoute == screen.route,
                                    onClick = {
                                        navController.navigate(screen.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                    )
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = Screen.Dashboard.route,
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable(Screen.Dashboard.route) {
                            val activeUserId = userSessionManager.getActiveUserId()
                            DashboardScreen(
                                viewModel = dashboardViewModel,
                                onNavigateToLogWorkout = {
                                    navController.navigate(Screen.LogWorkout.route)
                                },
                                onLogSpecialRoutine = { locationTag ->
                                    workoutViewModel.logSpecialRoutine(activeUserId, locationTag)
                                }
                            )
                        }
                        composable(Screen.LogWorkout.route) {
                            val activeUserId = userSessionManager.getActiveUserId()
                            WorkoutLoggingScreen(
                                viewModel = workoutViewModel,
                                onWorkoutLogged = {
                                    workoutViewModel.submitWorkout(activeUserId)
                                },
                                onLogSpecialRoutine = { locationTag ->
                                    workoutViewModel.logSpecialRoutine(activeUserId, locationTag)
                                }
                            )
                        }
                        composable(Screen.Goals.route) {
                            GoalsScreen(viewModel = goalsViewModel)
                        }
                        composable(Screen.Recommendations.route) {
                            val activeUserId = userSessionManager.getActiveUserId()
                            RecommendationScreen(
                                viewModel = recommendationViewModel,
                                onAcceptRecommendation = { rec ->
                                    workoutViewModel.onTypeSelected(rec.suggestedType)
                                    workoutViewModel.onDurationChanged(rec.suggestedDurationMinutes.toString())
                                    workoutViewModel.onIntensitySelected(rec.suggestedIntensity)
                                    navController.navigate(Screen.LogWorkout.route)
                                },
                                onRefreshRequested = {
                                    recommendationViewModel.refreshRecommendations(activeUserId)
                                }
                            )
                        }
                        composable(Screen.Settings.route) {
                            ServerSettingsScreen(
                                viewModel = serverSettingsViewModel,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}