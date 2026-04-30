package com.glassfiles.ui.screens.ai.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em

/**
 * Inline approval prompt rendered straight in the chat — never a Material
 * AlertDialog. The "ascii frame" look is faked with a 1dp warning-coloured
 * border on a flat surface so the block reads as part of the transcript
 * rather than a system overlay.
 *
 * @param tool   The fully-qualified tool name (e.g. `commit_changes`).
 * @param fields Aligned `key: value` rows displayed under the title.
 * @param destructive Switches the title between WARNING and DESTRUCTIVE
 *                    ACTION; the latter is rendered in error colour.
 */
@Composable
fun AgentApprovalBlock(
    tool: String,
    fields: List<Pair<String, String>>,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    destructive: Boolean = false,
    approveEnabled: Boolean = true,
    extra: (@Composable () -> Unit)? = null,
) {
    val colors = AgentTerminal.colors
    val frameColor = if (destructive) colors.error else colors.warning
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(colors.surface)
            .border(1.dp, frameColor, RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "\u26A0", // ⚠
                color = frameColor,
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.Bold,
                fontSize = AgentTerminal.type.message,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (destructive) "DESTRUCTIVE ACTION" else "APPROVAL REQUIRED",
                color = frameColor,
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.Bold,
                fontSize = AgentTerminal.type.message,
                lineHeight = 1.3.em,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            AgentField("tool", tool, colors.textPrimary)
            fields.forEach { (k, v) -> AgentField(k, v, colors.textSecondary) }
        }
        if (extra != null) {
            Spacer(Modifier.height(4.dp))
            extra()
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AgentTextButton(
                label = "[ y \u00B7 approve ]",
                color = if (approveEnabled) colors.accent else colors.textMuted,
                enabled = approveEnabled,
                onClick = onApprove,
            )
            AgentTextButton(
                label = "[ n \u00B7 reject ]",
                color = colors.textSecondary,
                enabled = true,
                onClick = onReject,
            )
        }
    }
}

@Composable
private fun AgentField(key: String, value: String, valueColor: Color) {
    Row {
        Text(
            text = key.padEnd(8) + ": ",
            color = AgentTerminal.colors.textMuted,
            fontFamily = JetBrainsMono,
            fontSize = AgentTerminal.type.toolCall,
            lineHeight = 1.4.em,
        )
        Text(
            text = value,
            color = valueColor,
            fontFamily = JetBrainsMono,
            fontSize = AgentTerminal.type.toolCall,
            lineHeight = 1.4.em,
        )
    }
}

@Composable
fun AgentTextButton(
    label: String,
    color: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        color = color,
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight.Medium,
        fontSize = AgentTerminal.type.toolCall,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
    )
}
