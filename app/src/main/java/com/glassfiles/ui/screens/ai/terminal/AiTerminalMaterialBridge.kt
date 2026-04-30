package com.glassfiles.ui.screens.ai.terminal

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Bridges the global Material theme to the AI module terminal palette so
 * existing screens that read [MaterialTheme.colorScheme] directly
 * (Coding, Image-gen, Video-gen, Chat) render in the same black /
 * salad-green / mono surface as the agent and hub. Outside the AI
 * module the global theme remains untouched — the bridge is only
 * applied inside an [AgentTerminalSurface].
 *
 * Use this as a wrapper around AI screens that have not been fully
 * rewritten in the terminal primitives but still need to inherit the
 * AI palette.
 */
@Composable
fun AiTerminalMaterialBridge(content: @Composable () -> Unit) {
    val terminal = AgentTerminalDarkColors
    val terminalScheme = darkColorScheme(
        primary = terminal.accent,
        onPrimary = terminal.background,
        primaryContainer = terminal.surfaceElevated,
        onPrimaryContainer = terminal.accent,
        secondary = terminal.accentDim,
        onSecondary = terminal.background,
        tertiary = terminal.accent,
        onTertiary = terminal.background,
        background = terminal.background,
        onBackground = terminal.textPrimary,
        surface = terminal.background,
        onSurface = terminal.textPrimary,
        surfaceVariant = terminal.surface,
        onSurfaceVariant = terminal.textSecondary,
        surfaceContainer = terminal.surface,
        surfaceContainerHigh = terminal.surfaceElevated,
        surfaceContainerHighest = terminal.surfaceElevated,
        outline = terminal.border,
        outlineVariant = terminal.border,
        error = terminal.error,
        onError = terminal.background,
        errorContainer = terminal.surface,
        onErrorContainer = terminal.error,
    )
    val baseTypography = MaterialTheme.typography
    val terminalTypography = remember(baseTypography) {
        baseTypography.copy(
            displayLarge = baseTypography.displayLarge.copy(fontFamily = JetBrainsMono),
            displayMedium = baseTypography.displayMedium.copy(fontFamily = JetBrainsMono),
            displaySmall = baseTypography.displaySmall.copy(fontFamily = JetBrainsMono),
            headlineLarge = baseTypography.headlineLarge.copy(fontFamily = JetBrainsMono),
            headlineMedium = baseTypography.headlineMedium.copy(fontFamily = JetBrainsMono),
            headlineSmall = baseTypography.headlineSmall.copy(fontFamily = JetBrainsMono),
            titleLarge = baseTypography.titleLarge.copy(fontFamily = JetBrainsMono),
            titleMedium = baseTypography.titleMedium.copy(fontFamily = JetBrainsMono),
            titleSmall = baseTypography.titleSmall.copy(fontFamily = JetBrainsMono),
            bodyLarge = baseTypography.bodyLarge.copy(fontFamily = JetBrainsMono),
            bodyMedium = baseTypography.bodyMedium.copy(fontFamily = JetBrainsMono),
            bodySmall = baseTypography.bodySmall.copy(fontFamily = JetBrainsMono),
            labelLarge = baseTypography.labelLarge.copy(fontFamily = JetBrainsMono),
            labelMedium = baseTypography.labelMedium.copy(fontFamily = JetBrainsMono),
            labelSmall = baseTypography.labelSmall.copy(fontFamily = JetBrainsMono),
        )
    }
    MaterialTheme(
        colorScheme = terminalScheme,
        typography = terminalTypography,
        content = content,
    )
}
