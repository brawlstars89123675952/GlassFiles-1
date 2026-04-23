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
            loading = false
        }
    }

    LaunchedEffect(selectedBranch) { loadProtection() }

    fun saveProtection() {
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
            saving = false
            loadProtection()
        }
    }

    Column(Modifier.fillMaxSize().background(SurfaceLight)) {
        GHTopBar(
            title = "Branch Protection",
            subtitle = "$repoOwner/$repoName",
            onBack = onBack,
            actions = {
                if (saving) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = Blue, strokeWidth = 2.dp)
                } else {
                    TextButton(onClick = { saveProtection() }) {
                        Text("Save", color = Blue, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
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
                CircularProgressIndicator(color = Blue, modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
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
                                tint = if (enabled) Color(0xFF34C759) else TextSecondary
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (enabled) "Protection enabled" else "Protection disabled",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextPrimary
                                )
                                Text(
                                    if (enabled) "Rules are enforced on this branch" else "No rules enforced",
                                    fontSize = 12.sp,
                                    color = TextTertiary
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
                                        Text("Required approvals:", fontSize = 14.sp, color = TextPrimary)
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            (1..6).forEach { count ->
                                                Box(
                                                    Modifier.size(32.dp).clip(RoundedCornerShape(8.dp))
                                                        .background(if (count == requiredApprovalCount) Blue.copy(0.15f) else SurfaceLight)
                                                        .clickable { requiredApprovalCount = count },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        "$count",
                                                        fontSize = 14.sp,
                                                        fontWeight = if (count == requiredApprovalCount) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (count == requiredApprovalCount) Blue else TextPrimary
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
}

@Composable
private fun BranchChip(name: String, selected: Boolean, protected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(8.dp))
            .background(if (selected) Blue.copy(0.15f) else SurfaceWhite)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            Icons.Rounded.AccountTree,
            null,
            Modifier.size(14.dp),
            tint = if (selected) Blue else TextSecondary
        )
        Text(name, fontSize = 13.sp, color = if (selected) Blue else TextPrimary)
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
        Modifier.clip(RoundedCornerShape(8.dp)).background(Blue.copy(0.1f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(name, fontSize = 12.sp, color = Blue, fontWeight = FontWeight.Medium)
        Icon(
            Icons.Rounded.Close,
            null,
            Modifier.size(14.dp).clickable { onRemove() },
            tint = Blue
        )
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceWhite).padding(16.dp)
    ) { content() }
}

@Composable
private fun SectionHeader(title: String, color: Color = TextPrimary) {
    Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = color, modifier = Modifier.padding(bottom = 8.dp))
}


