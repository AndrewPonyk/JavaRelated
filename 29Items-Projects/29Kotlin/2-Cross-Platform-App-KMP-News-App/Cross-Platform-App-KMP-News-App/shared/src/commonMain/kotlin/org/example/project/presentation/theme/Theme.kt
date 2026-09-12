package org.example.project.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = UkraineBlue,
    onPrimary = ColorSchemeDefaults.LightWhite,
    primaryContainer = PrimaryDark,
    secondary = UkraineYellow,
    background = SurfaceDark,
    surface = CardDark,
    onBackground = TextPrimaryDark,
    onSurface = TextPrimaryDark
)

private val LightColorScheme = lightColorScheme(
    primary = UkraineBlue,
    onPrimary = ColorSchemeDefaults.LightWhite,
    primaryContainer = UkraineBlueLight,
    secondary = UkraineYellow,
    background = SurfaceLight,
    surface = CardLight,
    onBackground = TextPrimaryLight,
    onSurface = TextPrimaryLight
)

object ColorSchemeDefaults {
    val LightWhite = androidx.compose.ui.graphics.Color(0xFFFFFFFF)
}

@Composable
fun NewsAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
