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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.glassfiles.ui.components.AiPickerSheet
import com.glassfiles.ui.theme.JetBrainsMono

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
    onAutoApproveReadsChange: (Boolean) -> Unit,
    onAutoApproveEditsChange: (Boolean) -> Unit,
    onAutoApproveWritesChange: (Boolean) -> Unit,
    onAutoApproveCommitsChange: (Boolean) -> Unit,
    onAutoApproveDestructiveChange: (Boolean) -> Unit,
    onYoloModeChange: (Boolean) -> Unit,
    onSessionTrustChange: (Boolean) -> Unit,
    onWriteLimitChange: (Int) -> Unit,
    onProtectedPathsChange: (String) -> Unit,
    onBackgroundExecutionChange: (Boolean) -> Unit,
    onKeepCpuAwakeChange: (Boolean) -> Unit,
    onWorkspaceModeChange: (Boolean) -> Unit,
    onMemoryProjectKnowledgeChange: (Boolean) -> Unit,
    onMemoryUserPreferencesChange: (Boolean) -> Unit,
    onMemoryChatSummariesChange: (Boolean) -> Unit,
    onMemorySemanticSearchChange: (Boolean) -> Unit,
    onWorkingMemoryEnabledChange: (Boolean) -> Unit,
    onWorkingMemoryRemindersChange: (Boolean) -> Unit,
    onViewWorkingMemory: () -> Unit,
    onViewMemoryFiles: () -> Unit,
    onClearMemory: () -> Unit,
    onSkillsEnabledChange: (Boolean) -> Unit,
    onSkillsAutoSuggestChange: (Boolean) -> Unit,
    onSkillsAllowUntrustedDangerousChange: (Boolean) -> Unit,
    onViewSkills: () -> Unit,
    onImportSkillPack: () -> Unit,
    onInstantRenderChange: (Boolean) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSystemPrompt: () -> Unit,
    onClearChat: () -> Unit,
    onExportChat: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = AgentTerminal.colors
    var showProtectedPaths by remember { mutableStateOf(false) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.58f))
                .padding(top = 48.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
        Column(
            Modifier
                .fillMaxWidth()
                .widthIn(max = 720.dp)
                .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                .background(colors.surfaceElevated)
                .border(
                    1.dp,
                    colors.border,
                    RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp),
                )
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .navigationBarsPadding()
                .padding(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AgentSheetHeader("AGENT SETTINGS")
            AgentSheetLabel("CHAT")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AgentSheetCommand(
                    label = "[ chat history \u2192 ]",
                    color = colors.textSecondary,
                    onClick = onOpenHistory,
                )
                AgentSheetCommand(
                    label = "[ system prompt \u2192 ]",
                    color = colors.warning,
                    onClick = onOpenSystemPrompt,
                )
            }
            AgentSheetDivider()
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
                label = "YOLO mode (no confirmations)",
                checked = state.yoloMode,
                onChange = onYoloModeChange,
            )
            AgentSheetCheckbox(
                label = "auto-approve reads",
                checked = state.autoApproveReads,
                onChange = onAutoApproveReadsChange,
            )
            AgentSheetCheckbox(
                label = "auto-approve edits",
                checked = state.autoApproveEdits,
                onChange = onAutoApproveEditsChange,
            )
            AgentSheetCheckbox(
                label = "auto-approve writes / new files",
                checked = state.autoApproveWrites,
                onChange = onAutoApproveWritesChange,
            )
            AgentSheetCheckbox(
                label = "auto-approve commits / PRs",
                checked = state.autoApproveCommits,
                onChange = onAutoApproveCommitsChange,
            )
            AgentSheetCheckbox(
                label = "destructive option (approval always required)",
                checked = state.autoApproveDestructive,
                onChange = onAutoApproveDestructiveChange,
            )
            AgentSheetCheckbox(
                label = "session trust for edits/writes",
                checked = state.sessionTrust,
                onChange = onSessionTrustChange,
            )
            AgentWriteLimitRow(
                selected = state.writeLimitPerTask,
                onSelect = onWriteLimitChange,
            )
            AgentSheetCommand(
                label = "[ protected paths: ${state.protectedPathsCount} ]",
                color = colors.warning,
                onClick = { showProtectedPaths = true },
            )
            AgentSheetDivider()
            AgentSheetLabel("BACKGROUND EXECUTION")
            AgentSheetCheckbox(
                label = "continue working when app is in background",
                checked = state.backgroundExecution,
                onChange = onBackgroundExecutionChange,
            )
            AgentSheetCheckbox(
                label = "keep CPU awake during long tasks",
                checked = state.keepCpuAwake,
                onChange = onKeepCpuAwakeChange,
            )
            AgentSheetDivider()
            AgentSheetLabel("WORKSPACE MODE")
            AgentSheetCheckbox(
                label = "use workspaces for atomic changes",
                checked = state.workspaceMode,
                onChange = onWorkspaceModeChange,
            )
            Text(
                text = if (state.workspaceMode) {
                    "edits accumulate in SQLite until review"
                } else {
                    "legacy: write tools commit immediately"
                },
                color = colors.textMuted,
                fontFamily = JetBrainsMono,
                fontSize = AgentTerminal.type.label,
                lineHeight = 1.4.em,
            )
            AgentSheetDivider()
            AgentSheetLabel("MEMORY")
            AgentSheetCheckbox(
                label = "project knowledge (project.md)",
                checked = state.memoryProjectKnowledge,
                onChange = onMemoryProjectKnowledgeChange,
            )
            AgentSheetCheckbox(
                label = "user preferences (preferences.md)",
                checked = state.memoryUserPreferences,
                onChange = onMemoryUserPreferencesChange,
            )
            AgentSheetCheckbox(
                label = "chat summaries",
                checked = state.memoryChatSummaries,
                onChange = onMemoryChatSummariesChange,
            )
            AgentSheetCheckbox(
                label = "semantic search across chats",
                checked = state.memorySemanticSearch,
                onChange = onMemorySemanticSearchChange,
            )
            AgentSheetDivider()
            AgentSheetLabel("WORKING MEMORY")
            AgentSheetCheckbox(
                label = "maintain working memory during tasks",
                checked = state.workingMemoryEnabled,
                onChange = onWorkingMemoryEnabledChange,
            )
            AgentSheetCheckbox(
                label = "auto-remind agent to update after edits",
                checked = state.workingMemoryReminders,
                onChange = onWorkingMemoryRemindersChange,
            )
            AgentSheetCommand(
                label = "[ view working memory \u2192 ]",
                color = colors.warning,
                onClick = onViewWorkingMemory,
            )
            AgentSheetDivider()
            AgentSheetLabel("SKILLS")
            AgentSheetCheckbox(
                label = "enable skills",
                checked = state.skillsEnabled,
                onChange = onSkillsEnabledChange,
            )
            AgentSheetCheckbox(
                label = "auto-suggest matching skill",
                checked = state.skillsAutoSuggest,
                onChange = onSkillsAutoSuggestChange,
            )
            AgentSheetCheckbox(
                label = "allow untrusted dangerous tools",
                checked = state.skillsAllowUntrustedDangerous,
                onChange = onSkillsAllowUntrustedDangerousChange,
            )
            AgentSheetLabel("selected skill: ${state.selectedSkillLabel}")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AgentSheetCommand(
                    label = "[ view installed skills: ${state.installedSkillsCount} ]",
                    color = colors.warning,
                    onClick = onViewSkills,
                )
                AgentSheetCommand(
                    label = "[ import skill/rules ]",
                    color = colors.accent,
                    onClick = onImportSkillPack,
                )
            }
            AgentSheetDivider()
            AgentSheetLabel("MEMORY FILES")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AgentSheetCommand(
                    label = "[ view memory files \u2192 ]",
                    color = colors.warning,
                    onClick = onViewMemoryFiles,
                )
                AgentSheetCommand(
                    label = "[ clear all memory ]",
                    color = colors.error,
                    onClick = onClearMemory,
                )
            }
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
    if (showProtectedPaths) {
        AgentProtectedPathsDialog(
            value = state.protectedPathsText,
            onChange = onProtectedPathsChange,
            onDismiss = { showProtectedPaths = false },
        )
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

@Composable
private fun AgentWriteLimitRow(
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    val colors = AgentTerminal.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        AgentSheetLabel("WRITE LIMIT PER TASK")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(20, 50, 100, 0).forEach { limit ->
                val isSelected = selected == limit
                val label = if (limit == 0) "\u221E" else limit.toString()
                Text(
                    text = if (isSelected) "[\u25A3 $label]" else "[ $label ]",
                    color = if (isSelected) colors.accent else colors.textSecondary,
                    fontFamily = JetBrainsMono,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    fontSize = AgentTerminal.type.message,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { onSelect(limit) }
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun AgentProtectedPathsDialog(
    value: String,
    onChange: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = AgentTerminal.colors
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(colors.surfaceElevated)
                .border(1.dp, colors.warning, RoundedCornerShape(8.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AgentSheetHeader("PROTECTED PATHS")
            Text(
                text = "one glob pattern per line",
                color = colors.textMuted,
                fontFamily = JetBrainsMono,
                fontSize = AgentTerminal.type.label,
            )
            BasicTextField(
                value = value,
                onValueChange = onChange,
                textStyle = TextStyle(
                    color = colors.textPrimary,
                    fontFamily = JetBrainsMono,
                    fontSize = AgentTerminal.type.toolCall,
                    lineHeight = 1.35.em,
                ),
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 220.dp, max = 360.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.border, RoundedCornerShape(6.dp))
                    .padding(10.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AgentSheetCommand(
                    label = "[ done ]",
                    color = colors.accent,
                    onClick = onDismiss,
                )
            }
        }
    }
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
        Text(
            "\u25BE",
            color = colors.textSecondary,
            fontFamily = JetBrainsMono,
            fontWeight = FontWeight.Medium,
            fontSize = AgentTerminal.type.message,
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
    val autoApproveEdits: Boolean,
    val autoApproveWrites: Boolean,
    val autoApproveCommits: Boolean,
    val autoApproveDestructive: Boolean,
    val yoloMode: Boolean,
    val sessionTrust: Boolean,
    val writeLimitPerTask: Int,
    val protectedPathsText: String,
    val protectedPathsCount: Int,
    val backgroundExecution: Boolean,
    val keepCpuAwake: Boolean,
    val workspaceMode: Boolean,
    val memoryProjectKnowledge: Boolean,
    val memoryUserPreferences: Boolean,
    val memoryChatSummaries: Boolean,
    val memorySemanticSearch: Boolean,
    /**
     * Working memory toggle (BUGS_FIX.md Section 3 — "Maintain working
     * memory during tasks"). When ON, the agent loop prepends
     * working_memory.md to the system prompt and injects auto-update
     * reminders. When OFF the file is left in place but ignored at
     * runtime.
     */
    val workingMemoryEnabled: Boolean,
    /**
     * Auto-reminder cadence toggle. Independent from
     * [workingMemoryEnabled] so power users can ship working memory to
     * the system prompt without nagging the model on every edit.
     */
    val workingMemoryReminders: Boolean,
    val skillsEnabled: Boolean,
    val skillsAutoSuggest: Boolean,
    val skillsAllowUntrustedDangerous: Boolean,
    val selectedSkillLabel: String,
    val installedSkillsCount: Int,
    val instantRender: Boolean,
)

enum class AgentMode(val label: String) {
    ECO("eco"),
    BALANCED("balanced"),
    MAX_QUALITY("max quality"),
}

data class RepoDisplay(val key: String, val title: String, val subtitle: String?)
data class ModelDisplay(val key: String, val title: String, val subtitle: String?)
