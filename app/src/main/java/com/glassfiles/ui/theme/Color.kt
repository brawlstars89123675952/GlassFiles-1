package com.glassfiles.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.glassfiles.data.AppThemeMode

object ThemeState {
    var mode by mutableStateOf(AppThemeMode.LIGHT)
    var accent by mutableStateOf(Color(0xFF007AFF))
    val isDark: Boolean get() = mode == AppThemeMode.DARK || mode == AppThemeMode.AMOLED
    val isAmoled: Boolean get() = mode == AppThemeMode.AMOLED
}

val Blue: Color get() = ThemeState.accent
val Green = Color(0xFF34C759)
val Orange = Color(0xFFFF9500)
val Red = Color(0xFFFF3B30)
val Purple = Color(0xFFAF52DE)
val Teal = Color(0xFF5AC8FA)
val Yellow = Color(0xFFFFCC00)
val Pink = Color(0xFFFF2D55)
val Indigo = Color(0xFF5856D6)

val FolderBlue = Color(0xFF56A0F5)
val FolderGreen = Color(0xFF5EC45A)
val FolderOrange = Color(0xFFF5A623)
val FolderRed = Color(0xFFF25C54)
val FolderPurple = Color(0xFFB47AE8)
val FolderYellow = Color(0xFFEDD44A)

val SurfaceLight: Color get() = when {
    ThemeState.isAmoled -> Color(0xFF000000)
    ThemeState.isDark -> Color(0xFF1C1C1E)
    else -> Color(0xFFF2F2F7)
}
val SurfaceWhite: Color get() = when {
    ThemeState.isAmoled -> Color(0xFF000000)
    ThemeState.isDark -> Color(0xFF2C2C2E)
    else -> Color(0xFFFFFFFF)
}
val TextPrimary: Color get() = when {
    ThemeState.isDark -> Color(0xFFE5E5EA)
    else -> Color(0xFF1C1C1E)
}
val TextSecondary: Color get() = when {
    ThemeState.isDark -> Color(0xFF98989D)
    else -> Color(0xFF8E8E93)
}
val TextTertiary: Color get() = when {
    ThemeState.isDark -> Color(0xFF48484A)
    else -> Color(0xFFC7C7CC)
}
val SeparatorColor: Color get() = when {
    ThemeState.isDark -> Color(0x33FFFFFF)
    else -> Color(0x33000000)
}
val TabBarInactiveColor: Color get() = when {
    ThemeState.isDark -> Color(0xFF636366)
    else -> Color(0xFF999999)
}
val CardBackground: Color get() = when {
    ThemeState.isAmoled -> Color(0xFF0A0A0A)
    ThemeState.isDark -> Color(0xFF2C2C2E)
    else -> Color(0xFFFFFFFF)
}
val CardBorder: Color get() = when {
    ThemeState.isDark -> Color(0x22FFFFFF)
    else -> Color(0x22000000)
}
