package com.glassfiles.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.glassfiles.data.Strings
import com.glassfiles.data.ai.usage.AiUsageBucket
import com.glassfiles.data.ai.usage.AiUsageStore
import com.glassfiles.data.ai.usage.AiUsageWindow
import com.glassfiles.data.ai.usage.summarise
import com.glassfiles.ui.components.AiModuleHairline
import com.glassfiles.ui.components.AiModuleKeyValueRow
import com.glassfiles.ui.components.AiModulePillButton
import com.glassfiles.ui.components.AiModuleScreenScaffold
import com.glassfiles.ui.components.AiModuleSectionLabel
import com.glassfiles.ui.theme.AiModuleTheme
import com.glassfiles.ui.theme.JetBrainsMono

/**
 * Local AI usage breakdown screen. Reads [AiUsageStore], computes
 * [com.glassfiles.data.ai.usage.AiUsageSummary] for the selected
 * [AiUsageWindow], and renders a flat list of metric / bucket rows in
 * the terminal palette so the screen feels like a `usage --window` CLI
 * report.
 */
@Composable
fun AiUsageScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    var window by remember { mutableStateOf(AiUsageWindow.TODAY) }
    var refreshTick by remember { mutableStateOf(0) }
    var confirmClear by remember { mutableStateOf(false) }
    val records = remember(refreshTick) { AiUsageStore.list(context) }
    val summary = remember(records, window) { summarise(records, window) }

    AiModuleScreenScaffold(
        title = Strings.aiUsageTitle,
        onBack = onBack,
        subtitle = "local · " + when (window) {
            AiUsageWindow.TODAY -> "1d"
            AiUsageWindow.WEEK -> "7d"
            AiUsageWindow.MONTH -> "30d"
        },
        trailing = {
            val colors = AiModuleTheme.colors
            IconButton(
                onClick = { confirmClear = true },
                enabled = records.isNotEmpty(),
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Rounded.DeleteOutline,
                    null,
                    Modifier.size(18.dp),
                    tint = if (records.isNotEmpty()) colors.warning else colors.textMuted,
                )
            }
        },
    ) {
        val colors = AiModuleTheme.colors
        Column(Modifier.fillMaxSize()) {
            // Window selector — terminal flag style: `[1d] [7d] [30d]`.
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "--window",
                    color = colors.textMuted,
                    fontFamily = JetBrainsMono,
                    fontSize = 12.sp,
                )
                Box(Modifier.size(8.dp))
                listOf(
                    AiUsageWindow.TODAY to "1d",
                    AiUsageWindow.WEEK to "7d",
                    AiUsageWindow.MONTH to "30d",
                ).forEach { (w, flag) ->
                    val active = w == window
                    WindowFlag(label = flag, active = active, onClick = { window = w })
                }
            }
            AiModuleHairline()

            if (summary.recordCount == 0) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 24.dp),
                ) {
                    Text(
                        "// " + Strings.aiUsageEmpty,
                        fontSize = 13.sp,
                        fontFamily = JetBrainsMono,
                        color = colors.textMuted,
                        lineHeight = 1.4.em,
                    )
                }
                return@Column
            }

            LazyColumn(
                contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                item {
                    AiModuleKeyValueRow(Strings.aiUsageRecords, summary.recordCount.toString())
                    AiModuleKeyValueRow(
                        Strings.aiUsageTokens,
                        if (summary.totalTokens > 0) summary.totalTokens.toString()
                        else Strings.aiUsageTokensEstimateOnly,
                    )
                    AiModuleKeyValueRow(Strings.aiUsageChars, summary.totalChars.toString())
                    AiModuleKeyValueRow(Strings.aiUsageToolCalls, summary.toolCallsCount.toString())
                    AiModuleKeyValueRow(Strings.aiUsageFilesRead, summary.filesReadCount.toString())
                    AiModuleKeyValueRow(
                        Strings.aiUsageFilesWritten,
                        summary.filesWrittenCount.toString(),
                    )
                    if (summary.estimatedRecordCount > 0) {
                        AiModuleKeyValueRow(
                            Strings.aiUsageEstimated,
                            Strings.aiUsageEstimatedFmt
                                .replace("{n}", summary.estimatedRecordCount.toString())
                                .replace("{total}", summary.recordCount.toString()),
                            valueColor = colors.warning,
                        )
                    }
                }
                item { UsageSectionHeader(Strings.aiUsageByProvider) }
                items(summary.byProvider) { BucketRow(it) }
                item { UsageSectionHeader(Strings.aiUsageByModel) }
                items(summary.byModel) { BucketRow(it) }
                item { UsageSectionHeader(Strings.aiUsageByMode) }
                items(summary.byMode) { BucketRow(it) }
                item {
                    Text(
                        text = "// " + Strings.aiUsageDisclaimer,
                        fontSize = 11.sp,
                        color = colors.textMuted,
                        fontFamily = JetBrainsMono,
                        lineHeight = 1.4.em,
                        modifier = Modifier.padding(horizontal = 12.dp).padding(top = 16.dp, bottom = 24.dp),
                    )
                }
            }
        }
    }

    if (confirmClear) {
        ClearConfirmDialog(
            onConfirm = {
                AiUsageStore.clear(context)
                refreshTick += 1
                confirmClear = false
            },
            onDismiss = { confirmClear = false },
        )
    }
}

@Composable
private fun WindowFlag(label: String, active: Boolean, onClick: () -> Unit) {
    val colors = AgentTerminal.colors
    Row(
        Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (active) colors.surface else Color.Transparent)
            .border(1.dp, if (active) colors.accent else colors.border, RoundedCornerShape(4.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = label,
            color = if (active) colors.accent else colors.textSecondary,
            fontFamily = JetBrainsMono,
            fontSize = 12.sp,
            fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

@Composable
private fun UsageSectionHeader(label: String) {
    val colors = AgentTerminal.colors
    Column(Modifier.padding(top = 14.dp, bottom = 4.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "> ",
                color = colors.accent,
                fontFamily = JetBrainsMono,
                fontSize = 12.sp,
            )
            TerminalSectionLabel(text = label)
        }
        TerminalHairline(Modifier.padding(horizontal = 12.dp))
    }
}

@Composable
private fun BucketRow(bucket: AiUsageBucket) {
    val colors = AgentTerminal.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                bucket.key,
                fontSize = 13.sp,
                fontFamily = JetBrainsMono,
                color = colors.textPrimary,
                lineHeight = 1.3.em,
            )
            Text(
                Strings.aiUsageBucketSubtitle
                    .replace("{n}", bucket.recordCount.toString())
                    .replace("{chars}", bucket.totalChars.toString()),
                fontSize = 11.sp,
                fontFamily = JetBrainsMono,
                color = colors.textMuted,
                lineHeight = 1.3.em,
            )
        }
        Text(
            if (bucket.totalTokens > 0) bucket.totalTokens.toString() else "—",
            fontSize = 13.sp,
            fontFamily = JetBrainsMono,
            color = colors.textPrimary,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun ClearConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val colors = AgentTerminal.colors
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Box(
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(colors.surfaceElevated)
                .border(1.dp, colors.warning, RoundedCornerShape(10.dp))
                .padding(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "⚠  " + Strings.aiUsageClearTitle.uppercase(),
                    fontSize = 14.sp,
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.Medium,
                    color = colors.warning,
                )
                Text(
                    text = Strings.aiUsageClearBody,
                    fontSize = 13.sp,
                    fontFamily = JetBrainsMono,
                    color = colors.textPrimary,
                    lineHeight = 1.4.em,
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                ) {
                    TerminalPillButton(
                        label = "n · " + Strings.cancel.lowercase(),
                        onClick = onDismiss,
                        accent = false,
                    )
                    TerminalPillButton(
                        label = "y · " + Strings.aiUsageClearConfirm.lowercase(),
                        onClick = onConfirm,
                        destructive = true,
                        accent = false,
                    )
                }
            }
        }
    }
}
