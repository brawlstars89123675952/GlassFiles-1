package com.glassfiles.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.glassfiles.data.AppThemeMode

private val FallbackAccent = Color(0xFF007AFF)

private val LightScheme = lightColorScheme(
    primary = FallbackAccent,
    onPrimary = Color.White,
    secondary = FallbackAccent,
    tertiary = FallbackAccent,
    background = Color(0xFFF2F2F7),
    onBackground = Color(0xFF1C1C1E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C1C1E),
    onSurfaceVariant = Color(0xFF8E8E93),
    outline = Color(0x33000000),
)

private val DarkScheme = darkColorScheme(
    primary = FallbackAccent,
    onPrimary = Color.White,
    secondary = FallbackAccent,
    tertiary = FallbackAccent,
    background = Color(0xFF1C1C1E),
    onBackground = Color(0xFFE5E5EA),
    surface = Color(0xFF2C2C2E),
    onSurface = Color(0xFFE5E5EA),
    onSurfaceVariant = Color(0xFF98989D),
    outline = Color(0x33FFFFFF),
)

private val AmoledScheme = darkColorScheme(
    primary = FallbackAccent,
    onPrimary = Color.White,
    secondary = FallbackAccent,
    tertiary = FallbackAccent,
    background = Color.Black,
    onBackground = Color(0xFFE5E5EA),
    surface = Color(0xFF0A0A0A),
    onSurface = Color(0xFFE5E5EA),
    onSurfaceVariant = Color(0xFF98989D),
    outline = Color(0x22FFFFFF),
)

val GlassTypography = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 41.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = (-0.41).sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = (-0.41).sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = (-0.24).sp),
    bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 13.sp),
)

@Composable
fun GlassFilesTheme(themeMode: AppThemeMode = AppThemeMode.LIGHT, accentColor: androidx.compose.ui.graphics.Color? = null, content: @Composable () -> Unit) {
    val resolvedDark = when (themeMode) {
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK, AppThemeMode.AMOLED -> true
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val resolvedMode = when {
        themeMode == AppThemeMode.AMOLED -> AppThemeMode.AMOLED
        themeMode == AppThemeMode.SYSTEM && resolvedDark -> AppThemeMode.DARK
        themeMode == AppThemeMode.SYSTEM -> AppThemeMode.LIGHT
        else -> themeMode
    }

    val effectiveAccent = accentColor ?: ThemeState.accent

    // Update global ThemeState so Color.kt properties react
    ThemeState.mode = resolvedMode
    ThemeState.accent = effectiveAccent

    val colorScheme = when (resolvedMode) {
        AppThemeMode.AMOLED -> AmoledScheme.copy(primary = effectiveAccent, secondary = effectiveAccent, tertiary = effectiveAccent)
        AppThemeMode.DARK -> DarkScheme.copy(primary = effectiveAccent, secondary = effectiveAccent, tertiary = effectiveAccent)
        else -> LightScheme.copy(primary = effectiveAccent, secondary = effectiveAccent, tertiary = effectiveAccent)
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !resolvedDark
                isAppearanceLightNavigationBars = !resolvedDark
            }
        }
    }
    MaterialTheme(colorScheme = colorScheme, typography = GlassTypography, content = content)
}
