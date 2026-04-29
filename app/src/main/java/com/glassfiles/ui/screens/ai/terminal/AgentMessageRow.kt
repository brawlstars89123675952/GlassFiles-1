package com.glassfiles.ui.screens.ai.terminal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em

/**
 * One transcript row, terminal-style: a fixed-width role glyph in the
 * gutter + the message body. No bubbles, no avatars — vertical rhythm
 * comes from a 12dp gap between rows in the parent LazyColumn.
 */
enum class AgentRole { USER, ASSISTANT, TOOL, SYSTEM, ERROR }

@Composable
fun AgentMessageRow(
    role: AgentRole,
    modifier: Modifier = Modifier,
    streaming: Boolean = false,
    body: @Composable () -> Unit,
) {
    val colors = AgentTerminal.colors
    val (glyph, glyphColor) = when (role) {
        AgentRole.USER -> ">" to colors.accent
        AgentRole.ASSISTANT -> "\u25A0" to colors.accent // ■
        AgentRole.TOOL -> "$" to colors.accentDim
        AgentRole.SYSTEM -> "!" to colors.warning
        AgentRole.ERROR -> "!" to colors.error
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        AgentGutterGlyph(glyph = glyph, color = glyphColor)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            body()
            if (streaming) AgentBlinkingCursor()
        }
    }
}

@Composable
fun AgentGutterGlyph(glyph: String, color: Color) {
    Box(
        Modifier
            .width(16.dp)
            .padding(top = 1.dp),
        contentAlignment = Alignment.TopStart,
    ) {
        Text(
            text = glyph,
            color = color,
            fontFamily = JetBrainsMono,
            fontWeight = FontWeight.Bold,
            fontSize = AgentTerminal.type.message,
            lineHeight = 1.45.em,
        )
    }
}

/** Plain text body for [AgentMessageRow]. */
@Composable
fun AgentMessageText(
    text: String,
    color: Color = AgentTerminal.colors.textPrimary,
) {
    Text(
        text = cleanAgentText(text),
        color = color,
        fontFamily = JetBrainsMono,
        fontSize = AgentTerminal.type.message,
        lineHeight = 1.45.em,
    )
}
