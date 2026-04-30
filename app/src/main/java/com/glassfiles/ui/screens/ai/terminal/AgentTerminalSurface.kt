package com.glassfiles.ui.screens.ai.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier

/**
 * Wraps the entire AI Agent screen in the terminal theme without
 * touching the global [androidx.compose.material3.MaterialTheme]. Any
 * descendant can access the palette via [AgentTerminal.colors] and the
 * typography via [AgentTerminal.type].
 */
@Composable
fun AgentTerminalSurface(
    modifier: Modifier = Modifier,
    colors: AgentTerminalColors = AgentTerminalDarkColors,
    typography: AgentTerminalTypography = AgentTerminalDefaultTypography,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalAgentTerminalColors provides colors,
        LocalAgentTerminalTypography provides typography,
    ) {
        Box(modifier.fillMaxSize().background(colors.background)) {
            content()
        }
    }
}
