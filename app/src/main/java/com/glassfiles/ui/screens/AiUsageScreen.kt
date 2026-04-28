package com.glassfiles.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glassfiles.data.Strings
import com.glassfiles.data.ai.usage.AiUsageBucket
import com.glassfiles.data.ai.usage.AiUsageStore
import com.glassfiles.data.ai.usage.AiUsageSummary
import com.glassfiles.data.ai.usage.AiUsageWindow
import com.glassfiles.data.ai.usage.summarise

/**
 * Local AI usage breakdown screen. Reads [AiUsageStore], computes
 * [AiUsageSummary] for the selected [AiUsageWindow], and renders a
 * flat list of metric / bucket rows.
 *
 * Style intent (matches the rest of the AI module):
 *  - no gradients / glassmorphism / shadows
 *  - colours via MaterialTheme.colorScheme.* only
 *  - numbers in monospace so digits line up across rows
 *  - 1dp hairline dividers between sections, no card chrome
 *  - empty state is a single short paragraph, no illustration
 */
@Composable
fun AiUsageScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme

    var window by remember { mutableStateOf(AiUsageWindow.TODAY) }
    var refreshTick by remember { mutableStateOf(0) }
    var confirmClear by remember { mutableStateOf(false) }
    val records = remember(refreshTick) { AiUsageStore.list(context) }
    val summary = remember(records, window) { summarise(records, window) }

    Column(Modifier.fillMaxSize().background(colors.surface)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 44.dp, start = 4.dp, end = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, Modifier.size(20.dp), tint = colors.onSurface)
            }
            Text(
                Strings.aiUsageTitle,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { confirmClear = true }, enabled = records.isNotEmpty()) {
                Icon(
                    Icons.Rounded.DeleteOutline,
                    null,
                    Modifier.size(20.dp),
                    tint = if (records.isNotEmpty()) colors.onSurface else colors.onSurfaceVariant,
                )
            }
        }

        // Window selector. Three text-only segments; the active one is
        // bold + underlined via colour, not via a pill background.
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            listOf(
                AiUsageWindow.TODAY to Strings.aiUsageWindowToday,
                AiUsageWindow.WEEK to Strings.aiUsageWindowWeek,
                AiUsageWindow.MONTH to Strings.aiUsageWindowMonth,
            ).forEach { (w, label) ->
                val active = w == window
                Text(
                    label,
                    fontSize = 13.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (active) colors.onSurface else colors.onSurfaceVariant,
                    modifier = Modifier.clickable { window = w },
                )
            }
        }

        Hairline()

        if (summary.recordCount == 0) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 24.dp),
            ) {
                Text(
                    Strings.aiUsageEmpty,
                    fontSize = 13.sp,
                    color = colors.onSurfaceVariant,
                )
            }
            return@Column
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item {
                MetricRow(Strings.aiUsageRecords, summary.recordCount.toString())
                MetricRow(
                    Strings.aiUsageTokens,
                    if (summary.totalTokens > 0) summary.totalTokens.toString()
                    else Strings.aiUsageTokensEstimateOnly,
                )
                MetricRow(Strings.aiUsageChars, summary.totalChars.toString())
                MetricRow(Strings.aiUsageToolCalls, summary.toolCallsCount.toString())
                MetricRow(Strings.aiUsageFilesRead, summary.filesReadCount.toString())
                MetricRow(Strings.aiUsageFilesWritten, summary.filesWrittenCount.toString())
                if (summary.estimatedRecordCount > 0) {
                    MetricRow(
                        Strings.aiUsageEstimated,
                        Strings.aiUsageEstimatedFmt
                            .replace("{n}", summary.estimatedRecordCount.toString())
                            .replace("{total}", summary.recordCount.toString()),
                    )
                }
            }
            item { SectionHeader(Strings.aiUsageByProvider) }
            items(summary.byProvider) { BucketRow(it) }
            item { SectionHeader(Strings.aiUsageByModel) }
            items(summary.byModel) { BucketRow(it) }
            item { SectionHeader(Strings.aiUsageByMode) }
            items(summary.byMode) { BucketRow(it) }
            item {
                Text(
                    Strings.aiUsageDisclaimer,
                    fontSize = 11.sp,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp, bottom = 24.dp),
                )
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(Strings.aiUsageClearTitle, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = { Text(Strings.aiUsageClearBody, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = {
                    AiUsageStore.clear(context)
                    refreshTick += 1
                    confirmClear = false
                }) { Text(Strings.aiUsageClearConfirm) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(Strings.cancel)
                }
            },
        )
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            fontSize = 13.sp,
            color = colors.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            color = colors.onSurface,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun SectionHeader(label: String) {
    val colors = MaterialTheme.colorScheme
    Column {
        Box(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 6.dp)) {
            Text(
                label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurfaceVariant,
                letterSpacing = 0.6.sp,
            )
        }
        Hairline()
    }
}

@Composable
private fun BucketRow(bucket: AiUsageBucket) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                bucket.key,
                fontSize = 13.sp,
                color = colors.onSurface,
            )
            Text(
                Strings.aiUsageBucketSubtitle
                    .replace("{n}", bucket.recordCount.toString())
                    .replace("{chars}", bucket.totalChars.toString()),
                fontSize = 11.sp,
                color = colors.onSurfaceVariant,
            )
        }
        Text(
            if (bucket.totalTokens > 0) bucket.totalTokens.toString() else "—",
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            color = colors.onSurface,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun Hairline() {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(colors.outlineVariant.copy(alpha = 0.4f)),
    )
}
