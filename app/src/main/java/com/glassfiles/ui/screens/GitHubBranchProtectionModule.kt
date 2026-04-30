package com.glassfiles.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glassfiles.data.github.*
import com.glassfiles.ui.components.AiModulePageBar
import com.glassfiles.ui.components.AiModuleHairline
import com.glassfiles.ui.components.AiModuleSpinner
import com.glassfiles.ui.theme.AiModuleTheme
import com.glassfiles.ui.theme.*
import kotlinx.coroutines.launch

@Composable
internal fun BranchProtectionScreen(
    repoOwner: String,
    repoName: String,
    branches: List<String>,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedBranch by remember { mutableStateOf(branches.firstOrNull() ?: "main") }
    var protection by remember { mutableStateOf<GHBranchProtection?>(null) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var loadedState by remember { mutableStateOf<BranchProtectionEditState?>(null) }
    var showDisableConfirm by remember { mutableStateOf(false) }
    var disableConfirmed by remember { mutableStateOf(false) }

    // Editable fields
    var enabled by remember { mutableStateOf(false) }
    var requireStatusChecks by remember { mutableStateOf(false) }
    var statusChecksStrict by remember { mutableStateOf(false) }
    var statusCheckContexts by remember { mutableStateOf<List<String>>(emptyList()) }
    var newContext by remember { mutableStateOf("") }

    var requirePRReviews by remember { mutableStateOf(false) }
    var requiredApprovalCount by remember { mutableStateOf(1) }
    var dismissStaleReviews by remember { mutableStateOf(false) }
    var requireCodeOwnerReviews by remember { mutableStateOf(false) }

    var allowForcePushes by remember { mutableStateOf(true) }
    var allowDeletions by remember { mutableStateOf(true) }
    var requireConversationResolution by remember { mutableStateOf(false) }
    var enforceAdmins by remember { mutableStateOf(false) }

    fun loadProtection() {
        loading = true
        scope.launch {
            val p = GitHubManager.getBranchProtection(context, repoOwner, repoName, selectedBranch)
            protection = p
            if (p != null) {
                enabled = p.enabled
                requireStatusChecks = p.requiredStatusChecks != null
                statusChecksStrict = p.requiredStatusChecks?.strict ?: false
                statusCheckContexts = p.requiredStatusChecks?.contexts ?: emptyList()
                requirePRReviews = p.requiredPRReviews != null
                requiredApprovalCount = p.requiredPRReviews?.requiredApprovingReviewCount ?: 1
                dismissStaleReviews = p.requiredPRReviews?.dismissStaleReviews ?: false
                requireCodeOwnerReviews = p.requiredPRReviews?.requireCodeOwnerReviews ?: false
                allowForcePushes = p.allowForcePushes
                allowDeletions = p.allowDeletions
                requireConversationResolution = p.requiredConversationResolution
                enforceAdmins = p.enforceAdmins
            } else {
                enabled = false
                requireStatusChecks = false
                statusChecksStrict = false
                statusCheckContexts = emptyList()
                requirePRReviews = false
                requiredApprovalCount = 1
                dismissStaleReviews = false
                requireCodeOwnerReviews = false
                allowForcePushes = true
                allowDeletions = true
                requireConversationResolution = false
                enforceAdmins = false
            }
            loadedState = BranchProtectionEditState(
                enabled = enabled,
                requireStatusChecks = requireStatusChecks,
                statusChecksStrict = statusChecksStrict,
                statusCheckContexts = statusCheckContexts,
                requirePRReviews = requirePRReviews,
                requiredApprovalCount = requiredApprovalCount,
                dismissStaleReviews = dismissStaleReviews,
                requireCodeOwnerReviews = requireCodeOwnerReviews,
                allowForcePushes = allowForcePushes,
                allowDeletions = allowDeletions,
                requireConversationResolution = requireConversationResolution,
                enforceAdmins = enforceAdmins
            )
            loading = false
        }
    }

    LaunchedEffect(selectedBranch) { loadProtection() }

    val currentState = BranchProtectionEditState(
        enabled = enabled,
        requireStatusChecks = requireStatusChecks,
        statusChecksStrict = statusChecksStrict,
        statusCheckContexts = statusCheckContexts,
        requirePRReviews = requirePRReviews,
        requiredApprovalCount = requiredApprovalCount,
        dismissStaleReviews = dismissStaleReviews,
        requireCodeOwnerReviews = requireCodeOwnerReviews,
        allowForcePushes = allowForcePushes,
        allowDeletions = allowDeletions,
        requireConversationResolution = requireConversationResolution,
        enforceAdmins = enforceAdmins
    )
    val hasUnsavedChanges = loadedState != null && currentState != loadedState

    fun saveProtection() {
        if (!enabled && protection?.enabled == true && !disableConfirmed) {
            showDisableConfirm = true
            return
        }
        saving = true
        scope.launch {
            if (!enabled) {
                // Delete protection
                val ok = GitHubManager.deleteBranchProtection(context, repoOwner, repoName, selectedBranch)
                Toast.makeText(context, if (ok) "Protection removed" else "Failed", Toast.LENGTH_SHORT).show()
            } else {
                val ok = GitHubManager.updateBranchProtection(
                    context = context,
                    owner = repoOwner,
                    repo = repoName,
                    branch = selectedBranch,
                    requiredStatusChecks = if (requireStatusChecks) GHRequiredStatusChecks(
                        strict = statusChecksStrict,
                        contexts = statusCheckContexts
                    ) else null,
                    requiredPRReviews = if (requirePRReviews) GHRequiredPRReviews(
                        requiredApprovingReviewCount = requiredApprovalCount,
                        dismissStaleReviews = dismissStaleReviews,
                        requireCodeOwnerReviews = requireCodeOwnerReviews
                    ) else null,
                    restrictions = null, // Simplified for now
                    allowForcePushes = allowForcePushes,
                    allowDeletions = allowDeletions,
                    requiredConversationResolution = requireConversationResolution,
                    enforceAdmins = enforceAdmins
                )
                Toast.makeText(context, if (ok) "Protection saved" else "Failed to save", Toast.LENGTH_SHORT).show()
            }
            disableConfirmed = false
            saving = false
            loadProtection()
        }
    }

    Column(Modifier.fillMaxSize().background(AiModuleTheme.colors.background)) {
        AiModulePageBar(
            title = "> branch protection",
            subtitle = "$repoOwner/$repoName",
            onBack = onBack,
            trailing = {
                if (saving) {
                    CircularProgressIndicator(Modifier.size(16.dp), color = AiModuleTheme.colors.accent, strokeWidth = 2.dp)
                } else {
                    TextButton(onClick = { saveProtection() }, enabled = hasUnsavedChanges) {
                        Text(if (hasUnsavedChanges) "save" else "saved", color = if (hasUnsavedChanges) AiModuleTheme.colors.accent else AiModuleTheme.colors.textMuted, fontWeight = FontWeight.SemiBold)
                    }
                }
            },
        )

        // Branch selector
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            branches.forEach { branch ->
                BranchChip(
                    name = branch,
                    selected = branch == selectedBranch,
                    protected = branch == selectedBranch && enabled
                ) {
                    selectedBranch = branch
                }
            }
        }

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AiModuleTheme.colors.accent, modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    BranchProtectionSummaryCard(
                        branch = selectedBranch,
                        state = currentState,
                        hasUnsavedChanges = hasUnsavedChanges
                    )
                }

                // Enable/disable protection
                item {
                    SettingsCard {
                        Row(
                            Modifier.fillMaxWidth().clickable { enabled = !enabled }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                if (enabled) Icons.Rounded.Shield else Icons.Rounded.Shield,
                                null,
                                Modifier.size(24.dp),
                                tint = if (enabled) Color(0xFF34C759) else AiModuleTheme.colors.textSecondary
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (enabled) "Protection enabled" else "Protection disabled",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = AiModuleTheme.colors.textPrimary
                                )
                                Text(
                                    if (enabled) "Rules are enforced on this branch" else "No rules enforced",
                                    fontSize = 12.sp,
                                    color = AiModuleTheme.colors.textMuted
                                )
                            }
                            Switch(
                                checked = enabled,
                                onCheckedChange = { enabled = it },
                                colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF34C759))
                            )
                        }
                    }
                }

                if (enabled) {
                    // Status Checks
                    item { SectionHeader("Status Checks") }
                    item {
                        SettingsCard {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                ToggleRow(
                                    "Require status checks",
                                    requireStatusChecks,
                                    Icons.Rounded.CheckCircle
                                ) { requireStatusChecks = it }

                                if (requireStatusChecks) {
                                    ToggleRow(
                                        "Require branches to be up to date",
                                        statusChecksStrict,
                                        Icons.Rounded.Update
                                    ) { statusChecksStrict = it }

                                    // Contexts
                                    if (statusCheckContexts.isNotEmpty()) {
                                        Row(
                                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            statusCheckContexts.forEach { ctx ->
                                                ContextChip(ctx) {
                                                    statusCheckContexts = statusCheckContexts - ctx
                                                }
                                            }
                                        }
                                    }

                                    Row(
                                        Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = newContext,
                                            onValueChange = { newContext = it },
                                            label = { Text("Status check name") },
                                            modifier = Modifier.weight(1f),
                                            singleLine = true
                                        )
                                        Button(
                                            onClick = {
                                                if (newContext.isNotBlank() && newContext !in statusCheckContexts) {
                                                    statusCheckContexts = statusCheckContexts + newContext
                                                    newContext = ""
                                                }
                                            },
                                            enabled = newContext.isNotBlank()
                                        ) {
                                            Text("Add")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // PR Reviews
                    item { SectionHeader("Pull Request Reviews") }
                    item {
                        SettingsCard {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                ToggleRow(
                                    "Require pull request reviews",
                                    requirePRReviews,
                                    Icons.Rounded.Reviews
                                ) { requirePRReviews = it }

                                if (requirePRReviews) {
                                    // Approval count
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Text("Required approvals:", fontSize = 14.sp, color = AiModuleTheme.colors.textPrimary)
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            (1..6).forEach { count ->
                                                Box(
                                                    Modifier.size(32.dp).clip(RoundedCornerShape(8.dp))
                                                        .background(if (count == requiredApprovalCount) AiModuleTheme.colors.accent.copy(0.15f) else AiModuleTheme.colors.background)
                                                        .clickable { requiredApprovalCount = count },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        "$count",
                                                        fontSize = 14.sp,
                                                        fontWeight = if (count == requiredApprovalCount) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (count == requiredApprovalCount) AiModuleTheme.colors.accent else AiModuleTheme.colors.textPrimary
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    ToggleRow(
                                        "Dismiss stale reviews",
                                        dismissStaleReviews,
                                        Icons.Rounded.AutoDelete
                                    ) { dismissStaleReviews = it }

                                    ToggleRow(
                                        "Require code owner reviews",
                                        requireCodeOwnerReviews,
                                        Icons.Rounded.VerifiedUser
                                    ) { requireCodeOwnerReviews = it }
                                }
                            }
                        }
                    }

                    // Additional rules
                    item { SectionHeader("Additional Rules") }
                    item {
                        SettingsCard {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                ToggleRow(
                                    "Allow force pushes",
                                    allowForcePushes,
                                    Icons.Rounded.FlashOn
                                ) { allowForcePushes = it }

                                ToggleRow(
                                    "Allow deletions",
                                    allowDeletions,
                                    Icons.Rounded.Delete
                                ) { allowDeletions = it }

                                ToggleRow(
                                    "Require conversation resolution",
                                    requireConversationResolution,
                                    Icons.Rounded.MarkChatRead
                                ) { requireConversationResolution = it }

                                ToggleRow(
                                    "Enforce for admins",
                                    enforceAdmins,
                                    Icons.Rounded.AdminPanelSettings
                                ) { enforceAdmins = it }
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(32.dp)) }
            }
        }
    }

    if (showDisableConfirm) {
        AlertDialog(
            onDismissRequest = { showDisableConfirm = false },
            containerColor = AiModuleTheme.colors.surface,
            title = { Text("Disable branch protection?", fontWeight = FontWeight.Bold, color = AiModuleTheme.colors.textPrimary) },
            text = {
                Text(
                    "This removes protection rules from $selectedBranch. Required reviews, status checks, and admin enforcement will no longer apply.",
                    fontSize = 13.sp,
                    color = AiModuleTheme.colors.textSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    disableConfirmed = true
                    showDisableConfirm = false
                    saveProtection()
                }) {
                    Text("Disable", color = Color(0xFFFF3B30))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDisableConfirm = false
                    disableConfirmed = false
                    enabled = true
                }) {
                    Text("Cancel", color = AiModuleTheme.colors.textSecondary)
                }
            }
        )
    }
}

private data class BranchProtectionEditState(
    val enabled: Boolean,
    val requireStatusChecks: Boolean,
    val statusChecksStrict: Boolean,
    val statusCheckContexts: List<String>,
    val requirePRReviews: Boolean,
    val requiredApprovalCount: Int,
    val dismissStaleReviews: Boolean,
    val requireCodeOwnerReviews: Boolean,
    val allowForcePushes: Boolean,
    val allowDeletions: Boolean,
    val requireConversationResolution: Boolean,
    val enforceAdmins: Boolean
)

@Composable
private fun BranchProtectionSummaryCard(branch: String, state: BranchProtectionEditState, hasUnsavedChanges: Boolean) {
    SettingsCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Rounded.Shield, null, Modifier.size(22.dp), tint = if (state.enabled) Color(0xFF34C759) else AiModuleTheme.colors.textSecondary)
            Column(Modifier.weight(1f)) {
                Text(branch, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = AiModuleTheme.colors.textPrimary)
                Text(
                    if (state.enabled) "${state.statusCheckContexts.size} checks · ${if (state.requirePRReviews) "${state.requiredApprovalCount} approvals" else "no review requirement"}"
                    else "No branch protection enabled",
                    fontSize = 11.sp,
                    color = AiModuleTheme.colors.textSecondary
                )
            }
            if (hasUnsavedChanges) {
                Text(
                    "Unsaved",
                    fontSize = 10.sp,
                    color = Color(0xFFFF9500),
                    modifier = Modifier.clip(RoundedCornerShape(5.dp)).background(Color(0xFFFF9500).copy(0.12f)).padding(horizontal = 7.dp, vertical = 3.dp)
                )
            }
        }
        if (state.enabled) {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (state.requireStatusChecks) MiniProtectionBadge("Status checks", AiModuleTheme.colors.accent)
                if (state.requirePRReviews) MiniProtectionBadge("Reviews", Color(0xFF34C759))
                if (state.requireConversationResolution) MiniProtectionBadge("Conversations", Color(0xFFFF9500))
                if (state.enforceAdmins) MiniProtectionBadge("Admins", Color(0xFFFF3B30))
            }
        }
    }
}

@Composable
private fun MiniProtectionBadge(label: String, color: Color) {
    Text(
        label,
        fontSize = 10.sp,
        color = color,
        modifier = Modifier.clip(RoundedCornerShape(5.dp)).background(color.copy(0.10f)).padding(horizontal = 7.dp, vertical = 3.dp)
    )
}

@Composable
private fun BranchChip(name: String, selected: Boolean, protected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(8.dp))
            .background(if (selected) AiModuleTheme.colors.accent.copy(0.15f) else AiModuleTheme.colors.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            Icons.Rounded.AccountTree,
            null,
            Modifier.size(14.dp),
            tint = if (selected) AiModuleTheme.colors.accent else AiModuleTheme.colors.textSecondary
        )
        Text(name, fontSize = 13.sp, color = if (selected) AiModuleTheme.colors.accent else AiModuleTheme.colors.textPrimary)
        if (protected) {
            Icon(
                Icons.Rounded.Shield,
                null,
                Modifier.size(12.dp),
                tint = Color(0xFF34C759)
            )
        }
    }
}

@Composable
private fun ContextChip(name: String, onRemove: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).background(AiModuleTheme.colors.accent.copy(0.1f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(name, fontSize = 12.sp, color = AiModuleTheme.colors.accent, fontWeight = FontWeight.Medium)
        Icon(
            Icons.Rounded.Close,
            null,
            Modifier.size(14.dp).clickable { onRemove() },
            tint = AiModuleTheme.colors.accent
        )
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(AiModuleTheme.colors.surface).padding(16.dp)
    ) { content() }
}

@Composable
private fun SectionHeader(title: String, color: Color = AiModuleTheme.colors.textPrimary) {
    Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = color, modifier = Modifier.padding(bottom = 8.dp))
}
