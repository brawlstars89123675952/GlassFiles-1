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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.glassfiles.ui.components.AiPickerSheet
import com.glassfiles.ui.theme.JetBrainsMono

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentSettingsBottomSheet(
    state: AgentSettingsState,
    repos: AgentSettingsOptions<RepoDisplay>,
    branches: AgentSettingsOptions<String>,
    models: AgentSettingsOptions<ModelDisplay>,
    onRepoSelected: (RepoDisplay) -> Unit,
    onBranchSelected: (String) -> Unit,
    onModelSelected: (ModelDisplay) -> Unit,
    onModeChange: (AgentMode) -> Unit,
    onAutoApproveChange: (Boolean) -> Unit,
    onInstantRenderChange: (Boolean) -> Unit,
    onClearChat: () -> Unit,
    onExportChat: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colors = AgentTerminal.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surfaceElevated,
        contentColor = colors.textPrimary,
        scrimColor = Color.Black.copy(alpha = 0.6f),
        dragHandle = {
            Spacer(
                Modifier
                    .padding(top = 8.dp, bottom = 4.dp)
                    .height(3.dp)
                    .width(36.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.border),
            )
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AgentSheetHeader("AGENT SETTINGS")
            AgentTerminalPickerRow(
                label = "REPO",
                value = state.repoLabel,
                title = "Select repository",
                options = repos,
                onSelect = onRepoSelected,
            )
            AgentTerminalPickerRow(
                label = "BRANCH",
                value = state.branchLabel,
                title = "Select branch",
                options = branches,
                onSelect = onBranchSelected,
            )
            AgentTerminalPickerRow(
                label = "MODEL",
                value = state.modelLabel,
                title = "Select model",
                options = models,
                onSelect = onModelSelected,
            )
            AgentSheetDivider()
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AgentSheetLabel("MODE")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AgentMode.values().forEach { mode ->
                        val selected = mode == state.mode
                        Text(
                            text = if (selected) "[\u25A3 ${mode.label}]" else "[ ${mode.label} ]",
                            color = if (selected) colors.accent else colors.textSecondary,
                            fontFamily = JetBrainsMono,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = AgentTerminal.type.message,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { onModeChange(mode) }
                                .padding(horizontal = 4.dp, vertical = 4.dp),
                        )
                    }
                }
                Text(
                    text = state.modeHint,
                    color = colors.textMuted,
                    fontFamily = JetBrainsMono,
                    fontSize = AgentTerminal.type.label,
                    lineHeight = 1.4.em,
                )
            }
            AgentSheetDivider()
            AgentSheetCheckbox(
                label = "auto-approve reads",
                checked = state.autoApproveReads,
                onChange = onAutoApproveChange,
            )
            AgentSheetCheckbox(
                label = "instant render (no streaming animation)",
                checked = state.instantRender,
                onChange = onInstantRenderChange,
            )
            AgentSheetDivider()
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AgentSheetCommand(
                    label = "[ clear chat ]",
                    color = colors.error,
                    onClick = onClearChat,
                )
                AgentSheetCommand(
                    label = "[ export chat ]",
                    color = colors.textSecondary,
                    onClick = onExportChat,
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun AgentSheetHeader(text: String) {
    val colors = AgentTerminal.colors
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Spacer(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.border),
        )
        Text(
            text = text,
            color = colors.textPrimary,
            fontFamily = JetBrainsMono,
            fontWeight = FontWeight.Bold,
            fontSize = AgentTerminal.type.message,
            lineHeight = 1.4.em,
        )
        Spacer(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.border),
        )
    }
}

@Composable
private fun AgentSheetDivider() {
    Spacer(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(AgentTerminal.colors.border),
    )
}

@Composable
private fun AgentSheetLabel(text: String) {
    Text(
        text = text,
        color = AgentTerminal.colors.textSecondary,
        fontFamily = JetBrainsMono,
        fontSize = AgentTerminal.type.label,
        lineHeight = 1.4.em,
    )
}

@Composable
private fun AgentSheetCheckbox(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val colors = AgentTerminal.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .clickable { onChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (checked) "[\u2713]" else "[ ]",
            color = if (checked) colors.accent else colors.textSecondary,
            fontFamily = JetBrainsMono,
            fontWeight = FontWeight.Bold,
            fontSize = AgentTerminal.type.message,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            color = colors.textPrimary,
            fontFamily = JetBrainsMono,
            fontSize = AgentTerminal.type.message,
        )
    }
}

@Composable
private fun AgentSheetCommand(
    label: String,
    color: Color,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        color = color,
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight.Medium,
        fontSize = AgentTerminal.type.message,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

/**
 * Terminal-flavoured wrapper around [AiPickerSheet]: a labelled chip
 * that opens the existing search-able list when tapped. Handles its
 * own open-state internally.
 */
@Composable
private fun <T> AgentTerminalPickerRow(
    label: String,
    value: String,
    title: String,
    options: AgentSettingsOptions<T>,
    onSelect: (T) -> Unit,
) {
    val colors = AgentTerminal.colors
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(6.dp))
            .clickable(enabled = options.enabled && options.items.isNotEmpty()) { open = true }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                color = colors.textSecondary,
                fontFamily = JetBrainsMono,
                fontSize = AgentTerminal.type.label,
            )
            Text(
                text = value.ifBlank { "\u2014" },
                color = colors.textPrimary,
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.Medium,
                fontSize = AgentTerminal.type.message,
                maxLines = 1,
            )
        }
        Icon(
            Icons.Rounded.ExpandMore,
            null,
            Modifier.size(16.dp),
            tint = colors.textSecondary,
        )
    }
    if (open) {
        AiPickerSheet(
            title = title,
            options = options.items,
            optionLabel = options.label,
            optionSubtitle = options.subtitle,
            selected = options.selected,
            onDismiss = { open = false },
            onSelect = { picked ->
                onSelect(picked)
                open = false
            },
        )
    }
}

data class AgentSettingsOptions<T>(
    val items: List<T>,
    val selected: T?,
    val label: (T) -> String,
    val subtitle: (T) -> String? = { null },
    val enabled: Boolean = true,
)

data class AgentSettingsState(
    val repoLabel: String,
    val branchLabel: String,
    val modelLabel: String,
    val mode: AgentMode,
    val modeHint: String,
    val autoApproveReads: Boolean,
    val instantRender: Boolean,
)

enum class AgentMode(val label: String) {
    ECO("eco"),
    BALANCED("balanced"),
    MAX_QUALITY("max quality"),
}

data class RepoDisplay(val key: String, val title: String, val subtitle: String?)
data class ModelDisplay(val key: String, val title: String, val subtitle: String?)
