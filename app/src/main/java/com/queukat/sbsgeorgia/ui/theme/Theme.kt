package com.queukat.sbsgeorgia.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.queukat.sbsgeorgia.domain.model.ThemeMode

private val LightColors =
    lightColorScheme(
        primary = TealPrimary,
        onPrimary = LightSurface,
        primaryContainer = TealPrimaryContainer,
        onPrimaryContainer = LightOnSurface,
        secondary = SecondaryTeal,
        onSecondary = LightSurface,
        secondaryContainer = SecondaryContainer,
        onSecondaryContainer = LightOnSurface,
        tertiary = AccentBlue,
        onTertiary = LightSurface,
        tertiaryContainer = AccentBlueContainer,
        onTertiaryContainer = LightOnSurface,
        background = LightBackground,
        onBackground = LightOnSurface,
        surface = LightSurface,
        onSurface = LightOnSurface,
        surfaceVariant = LightSurfaceVariant,
        onSurfaceVariant = LightOnSurfaceVariant,
        surfaceContainer = LightSurfaceContainer,
        surfaceContainerLow = LightSurface,
        surfaceContainerHigh = LightSurfaceContainerHigh,
        outline = LightOutline,
        outlineVariant = LightOutlineVariant,
        surfaceTint = TealPrimary
    )

private val DarkColors =
    darkColorScheme(
        primary = TealPrimaryDark,
        onPrimary = DarkBackground,
        primaryContainer = TealPrimaryContainerDark,
        onPrimaryContainer = DarkOnSurface,
        secondary = SecondaryTealDark,
        onSecondary = DarkBackground,
        secondaryContainer = SecondaryContainerDark,
        onSecondaryContainer = DarkOnSurface,
        tertiary = AccentBlueDark,
        onTertiary = DarkBackground,
        tertiaryContainer = AccentBlueContainerDark,
        onTertiaryContainer = DarkOnSurface,
        background = DarkBackground,
        onBackground = DarkOnSurface,
        surface = DarkSurface,
        onSurface = DarkOnSurface,
        surfaceVariant = DarkSurfaceVariant,
        onSurfaceVariant = DarkOnSurfaceVariant,
        surfaceContainer = DarkSurfaceContainer,
        surfaceContainerLow = DarkSurface,
        surfaceContainerHigh = DarkSurfaceContainerHigh,
        outline = DarkOutline,
        outlineVariant = DarkOutlineVariant,
        surfaceTint = TealPrimaryDark
    )

@Composable
fun SbsGeorgiaTheme(themeMode: ThemeMode, content: @Composable () -> Unit) {
    val darkTheme =
        when (themeMode) {
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }

    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content
    )
}
