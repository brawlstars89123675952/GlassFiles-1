package com.glassfiles.ui.screens.ai.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.glassfiles.ui.theme.JetBrainsMono

/**
 * Compact terminal-style topbar:
 *
 *   ←   AI Agent           $0.00 / 0.00k tok      ⚙ ■
 *       repo@branch (subtitle, optional)
 *
 * Stays in a single row at the top and surfaces a tiny subtitle for
 * `repo@branch` when both are set. The cost / tokens chip is hidden
 * until the session has consumed at least some context.
 */
@Composable
fun AgentTopBar(
    title: String,
    subtitle: String? = null,
    cost: String? = null,
    tokens: String? = null,
    autoApproveIndicator: String? = null,
    autoApproveTone: AgentAutoApproveTone = AgentAutoApproveTone.NEUTRAL,
    /**
     * Number of files in `Currently editing` of working_memory.md
     * (BUGS_FIX.md Section 3 — `▸ N files` indicator). Renders as
     * `▸ {n} files` next to the cost / tokens chip when greater than
     * zero. Zero or null hides the chip entirely.
     */
    workingFiles: Int? = null,
    embedded: Boolean,
    running: Boolean,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onStop: () -> Unit,
    onClose: (() -> Unit)? = null,
) {
    val colors = AgentTerminal.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.background)
            .padding(
                start = if (embedded) 12.dp else 4.dp,
                end = 4.dp,
                top = 6.dp,
                bottom = 6.dp,
            ),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!embedded) {
                IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                    Text(
                        "\u2190",
                        color = colors.textPrimary,
                        fontFamily = JetBrainsMono,
                        fontWeight = FontWeight.Medium,
                        fontSize = AgentTerminal.type.topBarTitle,
                    )
                }
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text = title,
                color = colors.textPrimary,
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.Medium,
                fontSize = AgentTerminal.type.topBarTitle,
                lineHeight = 1.25.em,
            )
            Spacer(Modifier.width(10.dp))
            if (cost != null || tokens != null) {
                AgentCostChip(cost = cost, tokens = tokens)
                Spacer(Modifier.width(6.dp))
            }
            if (workingFiles != null && workingFiles > 0) {
                Text(
                    text = "\u25B8 $workingFiles file" + if (workingFiles == 1) "" else "s",
                    color = colors.textSecondary,
                    fontFamily = JetBrainsMono,
                    fontSize = AgentTerminal.type.label,
                    lineHeight = 1.0.em,
                )
                Spacer(Modifier.width(6.dp))
            }
            if (!autoApproveIndicator.isNullOrBlank()) {
                AgentAutoApproveChip(
                    label = autoApproveIndicator,
                    tone = autoApproveTone,
                )
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onSettings, modifier = Modifier.size(36.dp)) {
                Text(
                    "\u2699",
                    color = colors.textSecondary,
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.Medium,
                    fontSize = AgentTerminal.type.topBarTitle,
                )
            }
            if (running) {
                IconButton(onClick = onStop, modifier = Modifier.size(36.dp)) {
                    Text(
                        "\u25A0",
                        color = colors.error,
                        fontFamily = JetBrainsMono,
                        fontWeight = FontWeight.Medium,
                        fontSize = AgentTerminal.type.topBarTitle,
                    )
                }
            } else if (onClose != null) {
                IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                    Text(
                        "\u00D7",
                        color = colors.textSecondary,
                        fontFamily = JetBrainsMono,
                        fontWeight = FontWeight.Medium,
                        fontSize = AgentTerminal.type.topBarTitle,
                    )
                }
            }
        }
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                color = colors.textMuted,
                fontFamily = JetBrainsMono,
                fontSize = AgentTerminal.type.label,
                lineHeight = 1.25.em,
                modifier = Modifier.padding(start = if (embedded) 0.dp else 44.dp, top = 0.dp),
            )
        }
        Spacer(
            Modifier
                .padding(top = 6.dp)
                .height(1.dp)
                .fillMaxWidth()
                .background(colors.border),
        )
    }
}

enum class AgentAutoApproveTone {
    NEUTRAL,
    WARNING,
    ERROR,
}

@Composable
private fun AgentCostChip(cost: String?, tokens: String?) {
    val colors = AgentTerminal.colors
    Row(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!cost.isNullOrBlank()) {
            Text(
                text = cost,
                color = colors.textPrimary,
                fontFamily = JetBrainsMono,
                fontSize = AgentTerminal.type.costChip,
            )
        }
        if (!cost.isNullOrBlank() && !tokens.isNullOrBlank()) {
            Text(
                text = "/",
                color = colors.textMuted,
                fontFamily = JetBrainsMono,
                fontSize = AgentTerminal.type.costChip,
            )
        }
        if (!tokens.isNullOrBlank()) {
            Text(
                text = tokens,
                color = colors.textSecondary,
                fontFamily = JetBrainsMono,
                fontSize = AgentTerminal.type.costChip,
            )
        }
    }
}

@Composable
private fun AgentAutoApproveChip(label: String, tone: AgentAutoApproveTone) {
    val colors = AgentTerminal.colors
    val color = when (tone) {
        AgentAutoApproveTone.NEUTRAL -> colors.textSecondary
        AgentAutoApproveTone.WARNING -> colors.warning
        AgentAutoApproveTone.ERROR -> colors.error
    }
    Row(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(colors.surface)
            .border(1.dp, color.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = color,
            fontFamily = JetBrainsMono,
            fontSize = AgentTerminal.type.costChip,
        )
    }
}
