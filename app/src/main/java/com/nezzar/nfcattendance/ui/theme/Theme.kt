package com.nezzar.nfcattendance.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** Which palette the app paints with. Stored in settings.json under "theme". */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

private val DarkScheme = darkColorScheme(
    primary = BrandPrimary,
    onPrimary = BrandOnPrimary,
    primaryContainer = BrandPrimaryPressed,
    onPrimaryContainer = BrandOnPrimary,
    secondary = BrandPrimary,
    onSecondary = BrandOnPrimary,
    secondaryContainer = BrandSurfaceHigh,
    onSecondaryContainer = BrandTextPrimary,
    tertiary = BrandPrimary,
    onTertiary = BrandOnPrimary,
    background = BrandBackground,
    onBackground = BrandTextPrimary,
    surface = BrandSurface,
    onSurface = BrandTextPrimary,
    surfaceVariant = BrandSurfaceRaised,
    onSurfaceVariant = BrandTextSecondary,
    surfaceContainer = BrandSurface,
    surfaceContainerHigh = BrandSurfaceRaised,
    surfaceContainerHighest = BrandSurfaceHigh,
    surfaceContainerLow = BrandBackground,
    surfaceContainerLowest = BrandBackground,
    // A boundary a finger can see (>= 3:1) versus a hairline that is only decoration.
    outline = BrandOutline,
    outlineVariant = BrandHairline,
    error = BrandError,
    onError = BrandBackground,
    scrim = BrandBackground,
)

private val LightScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryPressed,
    onPrimaryContainer = LightOnPrimary,
    secondary = LightPrimary,
    onSecondary = LightOnPrimary,
    secondaryContainer = LightSurfaceHigh,
    onSecondaryContainer = LightTextPrimary,
    tertiary = LightPrimary,
    onTertiary = LightOnPrimary,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceRaised,
    onSurfaceVariant = LightTextSecondary,
    surfaceContainer = LightSurface,
    surfaceContainerHigh = LightSurfaceRaised,
    surfaceContainerHighest = LightSurfaceHigh,
    surfaceContainerLow = LightBackground,
    surfaceContainerLowest = LightSurface,
    outline = LightOutline,
    outlineVariant = LightHairline,
    error = LightError,
    onError = LightOnPrimary,
    scrim = LightTextPrimary,
)

@Composable
fun NFCAttendanceTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    // The window icons have to follow the palette, not the system setting.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(
        colorScheme = if (dark) DarkScheme else LightScheme,
        typography = Typography,
        content = content,
    )
}
