package com.glassfiles.ui.screens.ai.terminal

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em

/**
 * Terminal-style chat input. Layout left → right:
 *   [📷] [ > description with caret▋ ] [ ⏎ ]
 *
 * The `>` prefix is fixed (never edited, never selected) and the caret is
 * a phosphor-green block ▋ rendered next to the text — Compose's native
 * caret stays a thin vertical line, so this is overlay-only and
 * blinks alongside the platform caret. Border switches to accent on
 * focus and stays muted otherwise.
 */
@Composable
fun AgentInput(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onSend: () -> Unit,
    onPickImage: () -> Unit,
    canSend: Boolean,
    enabled: Boolean,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val colors = AgentTerminal.colors
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        IconButton(
            onClick = onPickImage,
            enabled = enabled,
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                Icons.Rounded.AddPhotoAlternate,
                contentDescription = "attach image",
                modifier = Modifier.size(18.dp),
                tint = if (enabled) colors.textSecondary else colors.textMuted,
            )
        }
        Box(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(colors.surface)
                .border(
                    width = 1.dp,
                    color = if (focused) colors.accent else colors.border,
                    shape = RoundedCornerShape(8.dp),
                )
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = ">",
                    color = colors.accent,
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.Bold,
                    fontSize = AgentTerminal.type.input,
                    lineHeight = 1.4.em,
                )
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        enabled = enabled,
                        textStyle = TextStyle(
                            color = colors.textPrimary,
                            fontSize = AgentTerminal.type.input,
                            fontFamily = JetBrainsMono,
                            lineHeight = 1.4.em,
                        ),
                        cursorBrush = SolidColor(colors.accent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 22.dp, max = 140.dp)
                            .onFocusChanged { focused = it.isFocused },
                    )
                    if (value.text.isEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = placeholder,
                                color = colors.textMuted,
                                fontFamily = JetBrainsMono,
                                fontSize = AgentTerminal.type.input,
                            )
                            Spacer(Modifier.width(4.dp))
                            BlinkingPlaceholderCaret()
                        }
                    }
                }
            }
        }
        IconButton(
            onClick = { if (canSend) onSend() },
            enabled = canSend,
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                Icons.AutoMirrored.Rounded.Send,
                contentDescription = "send",
                modifier = Modifier.size(18.dp),
                tint = if (canSend) colors.accent else colors.textMuted,
            )
        }
    }
}

@Composable
private fun BlinkingPlaceholderCaret() {
    val infinite = rememberInfiniteTransition(label = "agent-input-caret")
    val alpha by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "agent-input-caret-alpha",
    )
    Text(
        text = "\u258B", // ▋
        color = AgentTerminal.colors.accent,
        fontFamily = JetBrainsMono,
        fontSize = AgentTerminal.type.input,
        modifier = Modifier.alpha(alpha),
    )
}
