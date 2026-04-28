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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BubbleChart
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glassfiles.data.Strings

/** Top-level entry for the AI module. Routes to chat / coding / image / video / models / keys. */
@Composable
fun AiHubScreen(
    onBack: () -> Unit,
    onChat: () -> Unit,
    onCoding: () -> Unit,
    onImage: () -> Unit,
    onVideo: () -> Unit,
    onModels: () -> Unit,
    onKeys: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme

    Column(Modifier.fillMaxSize().background(colors.surface)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 44.dp, start = 4.dp, end = 16.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, Modifier.size(20.dp), tint = colors.onSurface)
            }
            Text(
                Strings.aiHub,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
            )
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(
                listOf(
                    AiHubItem(Icons.Rounded.AutoAwesome, Strings.aiChat, Strings.aiHubSubtitle, soon = false, onChat),
                    AiHubItem(Icons.Rounded.Code, Strings.aiCoding, Strings.aiCodingSubtitle, soon = false, onCoding),
                    AiHubItem(Icons.Rounded.Image, Strings.aiImageGen, Strings.aiImageGenSubtitle, soon = false, onImage),
                    AiHubItem(Icons.Rounded.Movie, Strings.aiVideoGen, Strings.aiVideoGenSubtitle, soon = true, onVideo),
                    AiHubItem(Icons.Rounded.BubbleChart, Strings.aiModels, Strings.aiModelsSubtitle, soon = false, onModels),
                    AiHubItem(Icons.Rounded.VpnKey, Strings.aiKeys, Strings.aiKeysSubtitle, soon = false, onKeys),
                ),
            ) { item -> AiHubRow(item) }
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
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceVariant.copy(alpha = 0.5f))
            .clickable(enabled = !item.soon) { item.onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(colors.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(item.icon, null, Modifier.size(20.dp), tint = colors.primary)
        }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                )
                if (item.soon) {
                    Spacer(Modifier.size(8.dp))
                    AiHubSoonPill()
                }
            }
            Text(
                item.subtitle,
                fontSize = 12.sp,
                color = colors.onSurfaceVariant,
            )
        }
        if (!item.soon) {
            Icon(
                Icons.Rounded.ChevronRight,
                null,
                Modifier.size(20.dp),
                tint = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AiHubSoonPill() {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(colors.tertiary.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            Strings.aiSoon.uppercase(),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
            color = colors.tertiary,
        )
    }
}
