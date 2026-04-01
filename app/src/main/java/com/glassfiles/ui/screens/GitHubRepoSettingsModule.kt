package com.glassfiles.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Policy
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SettingsEthernet
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Webhook
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.glassfiles.data.github.GHRepo
import com.glassfiles.data.github.GitHubRepoSettingsManager
import com.glassfiles.ui.theme.Blue
import com.glassfiles.ui.theme.SeparatorColor
import com.glassfiles.ui.theme.SurfaceLight
import com.glassfiles.ui.theme.SurfaceWhite
import com.glassfiles.ui.theme.TextPrimary
import com.glassfiles.ui.theme.TextSecondary
import com.glassfiles.ui.theme.TextTertiary
import kotlinx.coroutines.launch

private enum class RepoSettingsTab { GENERAL, ACCESS, VARIABLES, WEBHOOKS, SECURITY, RULES, SECRETS }

@Composable
fun GitHubRepoSettingsScreen(repo: GHRepo, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableStateOf(RepoSettingsTab.GENERAL) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }

    var general by remember { mutableStateOf<GitHubRepoSettingsManager.RepoGeneralSettings?>(null) }
    var collaborators by remember { mutableStateOf<List<GitHubRepoSettingsManager.RepoCollaborator>>(emptyList()) }
    var variables by remember { mutableStateOf<List<GitHubRepoSettingsManager.RepoVariableMeta>>(emptyList()) }
    var webhooks by remember { mutableStateOf<List<GitHubRepoSettingsManager.RepoWebhook>>(emptyList()) }
    var security by remember { mutableStateOf<GitHubRepoSettingsManager.RepoSecuritySettings?>(null) }
    var rulesets by remember { mutableStateOf<List<GitHubRepoSettingsManager.RepoRulesetSummary>>(emptyList()) }
    var branchRules by remember { mutableStateOf<List<String>>(emptyList()) }
    var protection by remember { mutableStateOf<GitHubRepoSettingsManager.RepoBranchProtectionSummary?>(null) }
    var secrets by remember { mutableStateOf<List<GitHubRepoSettingsManager.RepoSecretMeta>>(emptyList()) }

    var showCollaboratorDialog by remember { mutableStateOf(false) }
    var showVariableDialog by remember { mutableStateOf(false) }
    var variableDialogName by remember { mutableStateOf("") }
    var variableDialogValue by remember { mutableStateOf("") }
    var editingVariable by remember { mutableStateOf(false) }
    var showWebhookDialog by remember { mutableStateOf(false) }
    var editingWebhook by remember { mutableStateOf<GitHubRepoSettingsManager.RepoWebhook?>(null) }

    suspend fun loadCurrentTab() {
        loading = true
        error = ""
        try {
            when (selectedTab) {
                RepoSettingsTab.GENERAL -> general = GitHubRepoSettingsManager.getGeneral(context, repo.owner, repo.name)
                RepoSettingsTab.ACCESS -> collaborators = GitHubRepoSettingsManager.listCollaborators(context, repo.owner, repo.name)
                RepoSettingsTab.VARIABLES -> variables = GitHubRepoSettingsManager.listVariables(context, repo.owner, repo.name)
                RepoSettingsTab.WEBHOOKS -> webhooks = GitHubRepoSettingsManager.listWebhooks(context, repo.owner, repo.name)
                RepoSettingsTab.SECURITY -> security = GitHubRepoSettingsManager.getSecuritySettings(context, repo.owner, repo.name)
                RepoSettingsTab.RULES -> {
                    val g = general ?: GitHubRepoSettingsManager.getGeneral(context, repo.owner, repo.name)
                    general = g
                    rulesets = GitHubRepoSettingsManager.listRulesets(context, repo.owner, repo.name)
                    val branch = g?.defaultBranch ?: repo.defaultBranch
                    branchRules = GitHubRepoSettingsManager.getRulesForBranch(context, repo.owner, repo.name, branch)
                    protection = GitHubRepoSettingsManager.getBranchProtection(context, repo.owner, repo.name, branch)
                }
                RepoSettingsTab.SECRETS -> secrets = GitHubRepoSettingsManager.listSecrets(context, repo.owner, repo.name)
            }
        } catch (e: Exception) {
            error = e.message ?: "Failed to load settings"
        }
        loading = false
    }

    LaunchedEffect(selectedTab) { loadCurrentTab() }

    Column(Modifier.fillMaxSize().background(SurfaceLight)) {
        RepoSettingsTopBar(repo = repo, onBack = onBack, onRefresh = { scope.launch { loadCurrentTab() } })
        TabRow(selectedTabIndex = selectedTab.ordinal, containerColor = SurfaceWhite) {
            RepoSettingsTab.entries.forEach { tab ->
                Tab(selected = selectedTab == tab, onClick = { selectedTab = tab }, text = {
                    Text(
                        when (tab) {
                            RepoSettingsTab.GENERAL -> "General"
                            RepoSettingsTab.ACCESS -> "Access"
                            RepoSettingsTab.VARIABLES -> "Variables"
                            RepoSettingsTab.WEBHOOKS -> "Webhooks"
                            RepoSettingsTab.SECURITY -> "Security"
                            RepoSettingsTab.RULES -> "Rules"
                            RepoSettingsTab.SECRETS -> "Secrets"
                        },
                        maxLines = 1,
                        fontSize = 11.sp
                    )
                })
            }
        }

        if (error.isNotBlank()) {
            Box(Modifier.fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFFF3B30).copy(0.1f)).padding(12.dp)) {
                Text(error, color = Color(0xFFFF3B30), fontSize = 12.sp)
            }
        }

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Blue, modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
            }
        } else {
            when (selectedTab) {
                RepoSettingsTab.GENERAL -> GeneralTab(
                    general = general,
                    onSave = { updated ->
                        scope.launch {
                            val ok = GitHubRepoSettingsManager.updateGeneral(context, repo.owner, repo.name, updated)
                            Toast.makeText(context, if (ok) "Saved" else "Failed", Toast.LENGTH_SHORT).show()
                            if (ok) general = GitHubRepoSettingsManager.getGeneral(context, repo.owner, repo.name)
                        }
                    }
                )
                RepoSettingsTab.ACCESS -> AccessTab(
                    collaborators = collaborators,
                    onAdd = { showCollaboratorDialog = true },
                    onRemove = { login ->
                        scope.launch {
                            val ok = GitHubRepoSettingsManager.removeCollaborator(context, repo.owner, repo.name, login)
                            Toast.makeText(context, if (ok) "Removed" else "Failed", Toast.LENGTH_SHORT).show()
                            if (ok) collaborators = GitHubRepoSettingsManager.listCollaborators(context, repo.owner, repo.name)
                        }
                    }
                )
                RepoSettingsTab.VARIABLES -> VariablesTab(
                    variables = variables,
                    onAdd = {
                        editingVariable = false
                        variableDialogName = ""
                        variableDialogValue = ""
                        showVariableDialog = true
                    },
                    onEdit = { item ->
                        scope.launch {
                            val full = GitHubRepoSettingsManager.getVariable(context, repo.owner, repo.name, item.name)
                            editingVariable = true
                            variableDialogName = item.name
                            variableDialogValue = full?.value ?: ""
                            showVariableDialog = true
                        }
                    },
                    onDelete = { name ->
                        scope.launch {
                            val ok = GitHubRepoSettingsManager.deleteVariable(context, repo.owner, repo.name, name)
                            Toast.makeText(context, if (ok) "Deleted" else "Failed", Toast.LENGTH_SHORT).show()
                            if (ok) variables = GitHubRepoSettingsManager.listVariables(context, repo.owner, repo.name)
                        }
                    }
                )
                RepoSettingsTab.WEBHOOKS -> WebhooksTab(
                    webhooks = webhooks,
                    onAdd = { editingWebhook = null; showWebhookDialog = true },
                    onEdit = { editingWebhook = it; showWebhookDialog = true },
                    onPing = { hook ->
                        scope.launch {
                            val ok = GitHubRepoSettingsManager.pingWebhook(context, repo.owner, repo.name, hook.id)
                            Toast.makeText(context, if (ok) "Ping sent" else "Failed", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onDelete = { hook ->
                        scope.launch {
                            val ok = GitHubRepoSettingsManager.deleteWebhook(context, repo.owner, repo.name, hook.id)
                            Toast.makeText(context, if (ok) "Deleted" else "Failed", Toast.LENGTH_SHORT).show()
                            if (ok) webhooks = GitHubRepoSettingsManager.listWebhooks(context, repo.owner, repo.name)
                        }
                    }
                )
                RepoSettingsTab.SECURITY -> SecurityTab(
                    security = security,
                    onToggleAutoFixes = { enabled ->
                        scope.launch {
                            val ok = GitHubRepoSettingsManager.setAutomatedSecurityFixes(context, repo.owner, repo.name, enabled)
                            Toast.makeText(context, if (ok) "Updated" else "Failed", Toast.LENGTH_SHORT).show()
                            if (ok) security = GitHubRepoSettingsManager.getSecuritySettings(context, repo.owner, repo.name)
                        }
                    },
                    onToggleVulnAlerts = { enabled ->
                        scope.launch {
                            val ok = GitHubRepoSettingsManager.setVulnerabilityAlerts(context, repo.owner, repo.name, enabled)
                            Toast.makeText(context, if (ok) "Updated" else "Failed", Toast.LENGTH_SHORT).show()
                            if (ok) security = GitHubRepoSettingsManager.getSecuritySettings(context, repo.owner, repo.name)
                        }
                    },
                    onTogglePrivateVuln = { enabled ->
                        scope.launch {
                            val ok = GitHubRepoSettingsManager.setPrivateVulnerabilityReporting(context, repo.owner, repo.name, enabled)
                            Toast.makeText(context, if (ok) "Updated" else "Failed", Toast.LENGTH_SHORT).show()
                            if (ok) security = GitHubRepoSettingsManager.getSecuritySettings(context, repo.owner, repo.name)
                        }
                    }
                )
                RepoSettingsTab.RULES -> RulesTab(rulesets = rulesets, branchRules = branchRules, protection = protection, branchName = general?.defaultBranch ?: repo.defaultBranch)
                RepoSettingsTab.SECRETS -> SecretsTab(
                    secrets = secrets,
                    onDelete = { name ->
                        scope.launch {
                            val ok = GitHubRepoSettingsManager.deleteSecret(context, repo.owner, repo.name, name)
                            Toast.makeText(context, if (ok) "Deleted" else "Failed", Toast.LENGTH_SHORT).show()
                            if (ok) secrets = GitHubRepoSettingsManager.listSecrets(context, repo.owner, repo.name)
                        }
                    }
                )
            }
        }
    }

    if (showCollaboratorDialog) {
        CollaboratorDialog(
            onDismiss = { showCollaboratorDialog = false },
            onConfirm = { username, permission ->
                scope.launch {
                    val ok = GitHubRepoSettingsManager.addCollaborator(context, repo.owner, repo.name, username, permission)
                    Toast.makeText(context, if (ok) "Added" else "Failed", Toast.LENGTH_SHORT).show()
                    if (ok) collaborators = GitHubRepoSettingsManager.listCollaborators(context, repo.owner, repo.name)
                    showCollaboratorDialog = false
                }
            }
        )
    }

    if (showVariableDialog) {
        VariableDialog(
            name = variableDialogName,
            value = variableDialogValue,
            editing = editingVariable,
            onDismiss = { showVariableDialog = false },
            onConfirm = { name, value ->
                scope.launch {
                    val ok = if (editingVariable) {
                        GitHubRepoSettingsManager.updateVariable(context, repo.owner, repo.name, name, value)
                    } else {
                        GitHubRepoSettingsManager.createVariable(context, repo.owner, repo.name, name, value)
                    }
                    Toast.makeText(context, if (ok) "Saved" else "Failed", Toast.LENGTH_SHORT).show()
                    if (ok) variables = GitHubRepoSettingsManager.listVariables(context, repo.owner, repo.name)
                    showVariableDialog = false
                }
            }
        )
    }

    if (showWebhookDialog) {
        WebhookDialog(
            existing = editingWebhook,
            onDismiss = { showWebhookDialog = false },
            onConfirm = { url, events, secret, active ->
                scope.launch {
                    val ok = if (editingWebhook == null) {
                        GitHubRepoSettingsManager.createWebhook(context, repo.owner, repo.name, url, events, secret, active)
                    } else {
                        GitHubRepoSettingsManager.updateWebhook(context, repo.owner, repo.name, editingWebhook!!.id, url, events, secret, active)
                    }
                    Toast.makeText(context, if (ok) "Saved" else "Failed", Toast.LENGTH_SHORT).show()
                    if (ok) webhooks = GitHubRepoSettingsManager.listWebhooks(context, repo.owner, repo.name)
                    showWebhookDialog = false
                }
            }
        )
    }
}

@Composable
private fun RepoSettingsTopBar(repo: GHRepo, onBack: () -> Unit, onRefresh: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(SurfaceWhite).padding(top = 48.dp, start = 4.dp, end = 8.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, Modifier.size(22.dp), tint = Blue) }
        Column(Modifier.weight(1f)) {
            Text("Repository Settings", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 24.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${repo.owner}/${repo.name}", fontSize = 13.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onRefresh) { Icon(Icons.Rounded.Refresh, null, Modifier.size(20.dp), tint = Blue) }
    }
}

@Composable
private fun GeneralTab(
    general: GitHubRepoSettingsManager.RepoGeneralSettings?,
    onSave: (GitHubRepoSettingsManager.RepoGeneralSettings) -> Unit
) {
    if (general == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Failed to load general settings", color = TextTertiary) }
        return
    }
    var name by remember(general) { mutableStateOf(general.name) }
    var description by remember(general) { mutableStateOf(general.description) }
    var homepage by remember(general) { mutableStateOf(general.homepage) }
    var defaultBranch by remember(general) { mutableStateOf(general.defaultBranch) }
    var archived by remember(general) { mutableStateOf(general.archived) }
    var hasIssues by remember(general) { mutableStateOf(general.hasIssues) }
    var hasProjects by remember(general) { mutableStateOf(general.hasProjects) }
    var hasWiki by remember(general) { mutableStateOf(general.hasWiki) }
    var hasDiscussions by remember(general) { mutableStateOf(general.hasDiscussions) }
    var allowForking by remember(general) { mutableStateOf(general.allowForking) }
    var signoff by remember(general) { mutableStateOf(general.webCommitSignoffRequired) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            SettingsCard {
                LabeledValue("Visibility", general.visibility)
                Field("Repository name", name) { name = it }
                Field("Description", description, singleLine = false) { description = it }
                Field("Homepage", homepage) { homepage = it }
                Field("Default branch", defaultBranch) { defaultBranch = it }
            }
        }
        item {
            SettingsCard {
                ToggleRow("Issues", hasIssues) { hasIssues = it }
                DividerRow()
                ToggleRow("Projects", hasProjects) { hasProjects = it }
                DividerRow()
                ToggleRow("Wiki", hasWiki) { hasWiki = it }
                DividerRow()
                ToggleRow("Discussions", hasDiscussions) { hasDiscussions = it }
                DividerRow()
                ToggleRow("Allow forking", allowForking) { allowForking = it }
                DividerRow()
                ToggleRow("Web commit signoff", signoff) { signoff = it }
                DividerRow()
                ToggleRow("Archived", archived) { archived = it }
            }
        }
        item {
            Button(
                onClick = {
                    onSave(
                        general.copy(
                            name = name,
                            description = description,
                            homepage = homepage,
                            defaultBranch = defaultBranch,
                            archived = archived,
                            hasIssues = hasIssues,
                            hasProjects = hasProjects,
                            hasWiki = hasWiki,
                            hasDiscussions = hasDiscussions,
                            allowForking = allowForking,
                            webCommitSignoffRequired = signoff
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Blue)
            ) {
                Icon(Icons.Rounded.Save, null, Modifier.size(18.dp), tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("Save General Settings", color = Color.White)
            }
        }
    }
}

@Composable
private fun AccessTab(
    collaborators: List<GitHubRepoSettingsManager.RepoCollaborator>,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Button(onClick = onAdd, colors = ButtonDefaults.buttonColors(containerColor = Blue)) {
                Icon(Icons.Rounded.Add, null, Modifier.size(18.dp), tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("Add collaborator", color = Color.White)
            }
        }
        items(collaborators) { item ->
            SettingsCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AsyncImage(item.avatarUrl, item.login, Modifier.size(38.dp).clip(CircleShape))
                    Column(Modifier.weight(1f)) {
                        Text(item.login, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text("${item.roleName} • ${item.permissionSummary}", color = TextSecondary, fontSize = 11.sp)
                    }
                    TextButton(onClick = { onRemove(item.login) }) {
                        Text("Remove", color = Color(0xFFFF3B30))
                    }
                }
            }
        }
    }
}

@Composable
private fun VariablesTab(
    variables: List<GitHubRepoSettingsManager.RepoVariableMeta>,
    onAdd: () -> Unit,
    onEdit: (GitHubRepoSettingsManager.RepoVariableMeta) -> Unit,
    onDelete: (String) -> Unit
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Button(onClick = onAdd, colors = ButtonDefaults.buttonColors(containerColor = Blue)) {
                Icon(Icons.Rounded.Add, null, Modifier.size(18.dp), tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("Add variable", color = Color.White)
            }
        }
        items(variables) { item ->
            SettingsCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.Tune, null, Modifier.size(18.dp), tint = Blue)
                    Column(Modifier.weight(1f)) {
                        Text(item.name, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(item.updatedAt.take(10), color = TextTertiary, fontSize = 11.sp)
                    }
                    IconButton(onClick = { onEdit(item) }) { Icon(Icons.Rounded.Edit, null, Modifier.size(18.dp), tint = Blue) }
                    IconButton(onClick = { onDelete(item.name) }) { Icon(Icons.Rounded.Delete, null, Modifier.size(18.dp), tint = Color(0xFFFF3B30)) }
                }
            }
        }
    }
}

@Composable
private fun WebhooksTab(
    webhooks: List<GitHubRepoSettingsManager.RepoWebhook>,
    onAdd: () -> Unit,
    onEdit: (GitHubRepoSettingsManager.RepoWebhook) -> Unit,
    onPing: (GitHubRepoSettingsManager.RepoWebhook) -> Unit,
    onDelete: (GitHubRepoSettingsManager.RepoWebhook) -> Unit
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Button(onClick = onAdd, colors = ButtonDefaults.buttonColors(containerColor = Blue)) {
                Icon(Icons.Rounded.Add, null, Modifier.size(18.dp), tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("Add webhook", color = Color.White)
            }
        }
        items(webhooks) { hook ->
            SettingsCard {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.Webhook, null, Modifier.size(18.dp), tint = Blue)
                        Text(hook.url, color = TextPrimary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    }
                    Text(hook.events.joinToString(", "), color = TextSecondary, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MiniAction("Ping", Blue) { onPing(hook) }
                        MiniAction("Edit", Blue) { onEdit(hook) }
                        MiniAction("Delete", Color(0xFFFF3B30)) { onDelete(hook) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SecurityTab(
    security: GitHubRepoSettingsManager.RepoSecuritySettings?,
    onToggleAutoFixes: (Boolean) -> Unit,
    onToggleVulnAlerts: (Boolean) -> Unit,
    onTogglePrivateVuln: (Boolean) -> Unit
) {
    if (security == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Failed to load security settings", color = TextTertiary) }
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            SettingsCard {
                ToggleActionRow("Automated security fixes", security.automatedSecurityFixes, onToggleAutoFixes)
                DividerRow()
                ToggleActionRow("Vulnerability alerts", security.vulnerabilityAlerts, onToggleVulnAlerts)
                DividerRow()
                ToggleActionRow("Private vulnerability reporting", security.privateVulnerabilityReporting, onTogglePrivateVuln)
            }
        }
        item {
            Text("These toggles depend on repository plan, visibility and token permissions.", fontSize = 12.sp, color = TextTertiary)
        }
    }
}

@Composable
private fun RulesTab(
    rulesets: List<GitHubRepoSettingsManager.RepoRulesetSummary>,
    branchRules: List<String>,
    protection: GitHubRepoSettingsManager.RepoBranchProtectionSummary?,
    branchName: String
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            SettingsCard {
                Text("Branch checked: $branchName", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                if (protection != null) {
                    Spacer(Modifier.height(8.dp))
                    LabeledValue("Protected", protection.isProtected.toString())
                    LabeledValue("Force pushes", protection.allowForcePushes.toString())
                    LabeledValue("Deletions", protection.allowDeletions.toString())
                    LabeledValue("Linear history", protection.requiredLinearHistory.toString())
                    LabeledValue("Conversation resolution", protection.requiredConversationResolution.toString())
                    LabeledValue("Approving reviews", protection.requiredApprovingReviews.toString())
                    LabeledValue("Status checks", protection.requiredStatusChecksCount.toString())
                }
            }
        }
        item {
            SettingsCard {
                Text("Active branch rules", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                if (branchRules.isEmpty()) Text("No active branch rules returned", color = TextTertiary, fontSize = 12.sp)
                else branchRules.forEachIndexed { index, rule ->
                    if (index > 0) DividerRow()
                    Text(rule, color = TextSecondary, fontSize = 12.sp)
                }
            }
        }
        item {
            SettingsCard {
                Text("Rulesets", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                if (rulesets.isEmpty()) Text("No rulesets found", color = TextTertiary, fontSize = 12.sp)
                else rulesets.forEachIndexed { index, item ->
                    if (index > 0) DividerRow()
                    Column {
                        Text(item.name, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text("${item.target} • ${item.enforcement} • ${item.sourceType}", color = TextSecondary, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SecretsTab(
    secrets: List<GitHubRepoSettingsManager.RepoSecretMeta>,
    onDelete: (String) -> Unit
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            SettingsCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.Info, null, Modifier.size(18.dp), tint = Blue)
                    Text("List/delete is wired. Create/update requires encrypted-value flow and is intentionally left out of this pass.", color = TextSecondary, fontSize = 12.sp)
                }
            }
        }
        items(secrets) { secret ->
            SettingsCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.Key, null, Modifier.size(18.dp), tint = Blue)
                    Column(Modifier.weight(1f)) {
                        Text(secret.name, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(secret.updatedAt.take(10), color = TextTertiary, fontSize = 11.sp)
                    }
                    IconButton(onClick = { onDelete(secret.name) }) { Icon(Icons.Rounded.Delete, null, Modifier.size(18.dp), tint = Color(0xFFFF3B30)) }
                }
            }
        }
    }
}

@Composable
private fun CollaboratorDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var username by remember { mutableStateOf("") }
    var permission by remember { mutableStateOf("push") }
    val permissions = listOf("pull", "triage", "push", "maintain", "admin")
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceWhite,
        title = { Text("Add collaborator", color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("GitHub username", username) { username = it }
                Text("Permission", color = TextSecondary, fontSize = 12.sp)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    permissions.forEach { item ->
                        MiniChoice(item, item == permission) { permission = item }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { if (username.isNotBlank()) onConfirm(username, permission) }) { Text("Add", color = Blue) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) } }
    )
}

@Composable
private fun VariableDialog(name: String, value: String, editing: Boolean, onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var localName by remember(name) { mutableStateOf(name) }
    var localValue by remember(value) { mutableStateOf(value) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceWhite,
        title = { Text(if (editing) "Edit variable" else "Add variable", color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("Name", localName, enabled = !editing) { localName = it }
                Field("Value", localValue, singleLine = false) { localValue = it }
            }
        },
        confirmButton = { TextButton(onClick = { if (localName.isNotBlank()) onConfirm(localName, localValue) }) { Text("Save", color = Blue) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) } }
    )
}

@Composable
private fun WebhookDialog(existing: GitHubRepoSettingsManager.RepoWebhook?, onDismiss: () -> Unit, onConfirm: (String, List<String>, String, Boolean) -> Unit) {
    var url by remember(existing) { mutableStateOf(existing?.url ?: "") }
    var events by remember(existing) { mutableStateOf(existing?.events?.joinToString(",") ?: "push") }
    var secret by remember { mutableStateOf("") }
    var active by remember(existing) { mutableStateOf(existing?.active ?: true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceWhite,
        title = { Text(if (existing == null) "Add webhook" else "Edit webhook", color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("URL", url) { url = it }
                Field("Events (comma separated)", events) { events = it }
                Field("Secret (optional)", secret) { secret = it }
                ToggleRow("Active", active) { active = it }
            }
        },
        confirmButton = { TextButton(onClick = { if (url.isNotBlank()) onConfirm(url, events.split(',').map { it.trim() }.filter { it.isNotBlank() }, secret, active) }) { Text("Save", color = Blue) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) } }
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SurfaceWhite).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}

@Composable
private fun Field(label: String, value: String, singleLine: Boolean = true, enabled: Boolean = true, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = singleLine,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = Blue))
    }
}

@Composable
private fun ToggleActionRow(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(label, color = TextPrimary, fontSize = 14.sp)
            Text(if (checked) "Enabled" else "Disabled", color = if (checked) Blue else TextTertiary, fontSize = 11.sp)
        }
        Switch(checked = checked, onCheckedChange = onToggle, colors = SwitchDefaults.colors(checkedTrackColor = Blue))
    }
}

@Composable
private fun LabeledValue(label: String, value: String) {
    Column {
        Text(label, color = TextTertiary, fontSize = 11.sp)
        Text(value.ifBlank { "—" }, color = TextPrimary, fontSize = 13.sp)
    }
}

@Composable
private fun DividerRow() {
    Box(Modifier.fillMaxWidth().height(0.5.dp).background(SeparatorColor))
}

@Composable
private fun MiniAction(label: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(0.08f)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun MiniChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(6.dp)).background(if (selected) Blue.copy(0.15f) else SurfaceLight).border(1.dp, if (selected) Blue.copy(0.25f) else SeparatorColor, RoundedCornerShape(6.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(label, color = if (selected) Blue else TextSecondary, fontSize = 11.sp)
    }
}
