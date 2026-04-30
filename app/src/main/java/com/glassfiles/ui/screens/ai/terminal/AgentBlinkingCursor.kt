package com.glassfiles.ui.screens.ai.terminal

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import com.glassfiles.ui.theme.JetBrainsMono

/**
 * Phosphor-green block cursor used to mark in-flight assistant
 * streams. Period is 1 s with a hard 50% duty cycle (LinearEasing) — no
 * ease-in/ease-out, that reads as glitchy on dark backgrounds.
 */
@Composable
fun AgentBlinkingCursor(modifier: Modifier = Modifier) {
    val infinite = rememberInfiniteTransition(label = "agent-cursor")
    val alpha by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "agent-cursor-alpha",
    )
    Text(
        text = "\u258B", // ▋
        color = AgentTerminal.colors.accent,
        fontFamily = JetBrainsMono,
        fontSize = AgentTerminal.type.message,
        modifier = modifier.alpha(alpha),
    )
}
