package com.example.a4_fitness_tracker_app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = FitnessPrimary,
    onPrimary = FitnessOnPrimary,
    primaryContainer = FitnessPrimaryContainer,
    onPrimaryContainer = FitnessOnPrimaryContainer,
    secondary = FitnessSecondary,
    onSecondary = FitnessOnSecondary,
    secondaryContainer = FitnessSecondaryContainer,
    onSecondaryContainer = FitnessOnSecondaryContainer,
    tertiary = FitnessTertiary,
    onTertiary = FitnessOnTertiary,
    background = FitnessBackgroundDark,
    surface = FitnessSurfaceDark,
    surfaceVariant = FitnessSurfaceVariantDark,
    onSurface = FitnessOnSurfaceDark,
    onSurfaceVariant = FitnessOnSurfaceVariantDark,
    error = FitnessError,
    onError = FitnessOnError
)

@Composable
fun FitnessTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = DarkColorScheme // Default to sleek Dark Mode for Fitness aesthetic
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            window?.let {
                it.statusBarColor = colorScheme.background.toArgb()
                it.navigationBarColor = colorScheme.surface.toArgb()
                WindowCompat.getInsetsController(it, view).isAppearanceLightStatusBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = FitnessTypography,
        content = content
    )
}
