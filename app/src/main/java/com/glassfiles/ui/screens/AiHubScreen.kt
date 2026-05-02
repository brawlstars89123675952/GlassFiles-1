package com.glassfiles.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BubbleChart
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glassfiles.data.Strings
import com.glassfiles.ui.components.AiModuleChip
import com.glassfiles.ui.components.AiModuleHairline
import com.glassfiles.ui.components.AiModuleScreenScaffold
import com.glassfiles.ui.components.AiModuleIcon
import com.glassfiles.ui.components.AiModuleText
import com.glassfiles.ui.theme.AiModuleTheme
import com.glassfiles.ui.theme.JetBrainsMono

/** Top-level entry for the AI module. Routes to chat / coding / image / video / models / keys / settings. */
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
    AiModuleScreenScaffold(
        title = Strings.aiHub,
        onBack = onBack,
        subtitle = "glassfiles.ai",
    ) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            items(
                listOf(
                    AiHubItem(Icons.Rounded.AutoAwesome, Strings.aiChat, Strings.aiHubSubtitle, soon = false, onChat),
                    AiHubItem(Icons.Rounded.Code, Strings.aiCoding, Strings.aiCodingSubtitle, soon = false, onCoding),
                    AiHubItem(Icons.Rounded.Build, Strings.aiAgent, Strings.aiAgentSubtitle, soon = false, onAgent),
                    AiHubItem(Icons.Rounded.Image, Strings.aiImageGen, Strings.aiImageGenSubtitle, soon = false, onImage),
                    AiHubItem(Icons.Rounded.Movie, Strings.aiVideoGen, Strings.aiVideoGenSubtitle, soon = false, onVideo),
                    AiHubItem(Icons.Rounded.BubbleChart, Strings.aiModels, Strings.aiModelsSubtitle, soon = false, onModels),
                    AiHubItem(Icons.Rounded.VpnKey, Strings.aiKeys, Strings.aiKeysSubtitle, soon = false, onKeys),
                    AiHubItem(Icons.Rounded.Insights, Strings.aiUsageTitle, Strings.aiUsageSubtitle, soon = false, onUsage),
                    AiHubItem(Icons.Rounded.Tune, Strings.aiSettings, Strings.aiSettingsSubtitle, soon = false, onSettings),
                ),
            ) { item ->
                AiHubRow(item)
                AiModuleHairline()
            }
        }
    }
}

private data class AiHubItem(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val soon: Boolean,
    val onClick: () -> Unit,
)

@Composable
private fun AiHubRow(item: AiHubItem) {
    val colors = AiModuleTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(colors.surface)
            .clickable(enabled = !item.soon) { item.onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(colors.surfaceElevated),
            contentAlignment = Alignment.Center,
        ) {
            AiModuleIcon(item.icon, null, Modifier.size(17.dp), tint = colors.accent)
        }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AiModuleText(
                    item.title,
                    fontSize = 14.sp,
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.Medium,
                    color = colors.textPrimary,
                )
                if (item.soon) {
                    Spacer(Modifier.size(8.dp))
                    AiHubSoonPill()
                }
            }
            AiModuleText(
                item.subtitle,
                fontSize = 11.sp,
                fontFamily = JetBrainsMono,
                color = colors.textMuted,
            )
        }
        if (!item.soon) {
            AiModuleIcon(
                Icons.Rounded.ChevronRight,
                null,
                Modifier.size(18.dp),
                tint = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun AiHubSoonPill() {
    AiModuleChip(
        label = Strings.aiSoon.uppercase(),
        color = AiModuleTheme.colors.warning,
    )
}
