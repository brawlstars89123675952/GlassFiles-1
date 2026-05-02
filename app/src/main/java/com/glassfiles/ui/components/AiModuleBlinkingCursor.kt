package com.glassfiles.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import com.glassfiles.ui.theme.AiModuleTheme
import com.glassfiles.ui.theme.JetBrainsMono

/**
 * AI-module twin of [com.glassfiles.ui.screens.ai.terminal.AgentBlinkingCursor].
 * Uses [AiModuleTheme] palette/typography so chat / image / video / usage
 * screens do not have to reach into the agent-terminal namespace.
 */
@Composable
fun AiModuleBlinkingCursor(modifier: Modifier = Modifier) {
    val infinite = rememberInfiniteTransition(label = "ai-module-cursor")
    val alpha by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "ai-module-cursor-alpha",
    )
    AiModuleText(
        text = "\u258B", // ▋
        color = AiModuleTheme.colors.accent,
        fontFamily = JetBrainsMono,
        fontSize = AiModuleTheme.type.message,
        modifier = modifier.alpha(alpha),
    )
}
