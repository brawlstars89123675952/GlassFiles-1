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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em

/**
 * Compact terminal-style topbar:
 *
 *   ←   AI Agent           $0.00 / 0.00k tok   ⚙ + ⌬
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
    embedded: Boolean,
    running: Boolean,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onNewChat: () -> Unit,
    onSystemPrompt: () -> Unit,
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
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "back",
                        modifier = Modifier.size(18.dp),
                        tint = colors.textPrimary,
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
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onSettings, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Rounded.Settings,
                    contentDescription = "settings",
                    modifier = Modifier.size(18.dp),
                    tint = colors.textSecondary,
                )
            }
            IconButton(onClick = onNewChat, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = "new chat",
                    modifier = Modifier.size(18.dp),
                    tint = colors.textSecondary,
                )
            }
            IconButton(onClick = onSystemPrompt, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Rounded.Tune,
                    contentDescription = "system prompt",
                    modifier = Modifier.size(18.dp),
                    tint = colors.textSecondary,
                )
            }
            if (running) {
                IconButton(onClick = onStop, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Rounded.Stop,
                        contentDescription = "stop",
                        modifier = Modifier.size(18.dp),
                        tint = colors.error,
                    )
                }
            }
            if (onClose != null) {
                IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "close",
                        modifier = Modifier.size(18.dp),
                        tint = colors.textSecondary,
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
