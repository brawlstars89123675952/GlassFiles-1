package com.glassfiles.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BubbleChart
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.glassfiles.data.Strings
import com.glassfiles.ui.screens.ai.terminal.AgentTerminal
import com.glassfiles.ui.screens.ai.terminal.JetBrainsMono
import com.glassfiles.ui.screens.ai.terminal.TerminalChip
import com.glassfiles.ui.screens.ai.terminal.TerminalHairline
import com.glassfiles.ui.screens.ai.terminal.TerminalListRow
import com.glassfiles.ui.screens.ai.terminal.TerminalScreenScaffold
import com.glassfiles.ui.screens.ai.terminal.TerminalSectionLabel

/**
 * Top-level entry for the AI module. Reads as a man-page index of
 * available AI surfaces; each row is a `▸ name` route.
 *
 * Renders inside the AI module terminal palette so the user gets the
 * consistent "engineering surface" feel from the moment they tap into
 * AI from the rest of the app.
 */
@Composable
fun AiHubScreen(
    onBack: () -> Unit,
    onChat: () -> Unit,
    onAgent: () -> Unit,
    onCoding: () -> Unit,
    onImage: () -> Unit,
    onVideo: () -> Unit,
    onModels: () -> Unit,
    onKeys: () -> Unit,
    onSettings: () -> Unit,
    onUsage: () -> Unit,
) {
    val sections = listOf(
        AiHubSection(
            label = Strings.aiSectionWorkspaces,
            items = listOf(
                AiHubItem(Icons.Rounded.AutoAwesome, Strings.aiChat, Strings.aiHubSubtitle, onChat),
                AiHubItem(Icons.Rounded.Code, Strings.aiCoding, Strings.aiCodingSubtitle, onCoding),
                AiHubItem(Icons.Rounded.Build, Strings.aiAgent, Strings.aiAgentSubtitle, onAgent),
            ),
        ),
        AiHubSection(
            label = Strings.aiSectionGeneration,
            items = listOf(
                AiHubItem(Icons.Rounded.Image, Strings.aiImageGen, Strings.aiImageGenSubtitle, onImage),
                AiHubItem(Icons.Rounded.Movie, Strings.aiVideoGen, Strings.aiVideoGenSubtitle, onVideo),
            ),
        ),
        AiHubSection(
            label = Strings.aiSectionConfig,
            items = listOf(
                AiHubItem(Icons.Rounded.BubbleChart, Strings.aiModels, Strings.aiModelsSubtitle, onModels),
                AiHubItem(Icons.Rounded.VpnKey, Strings.aiKeys, Strings.aiKeysSubtitle, onKeys),
                AiHubItem(Icons.Rounded.Insights, Strings.aiUsageTitle, Strings.aiUsageSubtitle, onUsage),
                AiHubItem(Icons.Rounded.Tune, Strings.aiSettings, Strings.aiSettingsSubtitle, onSettings),
            ),
        ),
    )

    TerminalScreenScaffold(
        title = Strings.aiHub,
        onBack = onBack,
        subtitle = "ai · multi-provider toolkit",
    ) {
        LazyColumn(
            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            sections.forEachIndexed { sectionIndex, section ->
                item(key = "section-${section.label}") {
                    Spacer(Modifier.height(if (sectionIndex == 0) 0.dp else 14.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "> ",
                            color = AgentTerminal.colors.accent,
                            fontFamily = JetBrainsMono,
                            fontSize = 12.sp,
                        )
                        TerminalSectionLabel(text = section.label)
                    }
                    TerminalHairline(Modifier.padding(horizontal = 12.dp))
                }
                items(section.items, key = { it.title }) { entry ->
                    AiHubRow(entry)
                }
            }
        }
    }
}

private data class AiHubSection(val label: String, val items: List<AiHubItem>)

private data class AiHubItem(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val onClick: () -> Unit,
)

@Composable
private fun AiHubRow(item: AiHubItem) {
    val colors = AgentTerminal.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.background),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TerminalListRow(
            title = item.title,
            subtitle = item.subtitle,
            onClick = item.onClick,
            prefix = "▸",
            leadingIcon = item.icon,
            leadingTint = colors.accentDim,
            trailing = {
                Text(
                    text = "↗",
                    color = colors.textMuted,
                    fontFamily = JetBrainsMono,
                    fontSize = 14.sp,
                    lineHeight = 1.0.em,
                )
            },
            paddingVertical = 12.dp,
        )
    }
}
