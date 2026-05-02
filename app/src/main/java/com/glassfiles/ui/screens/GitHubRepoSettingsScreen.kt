package com.glassfiles.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Rule
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import com.glassfiles.ui.components.AiModuleAlertDialog
import com.glassfiles.ui.components.AiModuleGlyph
import com.glassfiles.ui.components.AiModuleGlyphAction
import com.glassfiles.ui.components.AiModuleIcon as Icon
import com.glassfiles.ui.components.AiModulePageBar
import com.glassfiles.ui.components.AiModulePillButton
import com.glassfiles.ui.components.AiModuleSpinner
import com.glassfiles.ui.components.AiModuleText as Text
import com.glassfiles.ui.components.AiModuleTextAction
import com.glassfiles.ui.components.AiModuleTextField
import com.glassfiles.ui.theme.JetBrainsMono
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import com.glassfiles.ui.theme.AiModuleTheme
import com.glassfiles.data.github.GitHubRepoSettingsManager
import kotlinx.coroutines.launch

private enum class RepoSettingsTab(val label: String) {
    GENERAL("General"),
    ACCESS("Access"),
    VARIABLES("Variables"),
    SECRETS("Secrets"),
    WEBHOOKS("Webhooks"),
    RULES("Rules"),
    SECURITY("Security")
}

@Composable
fun GitHubRepoSettingsScreen(
    repo: GHRepo,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableStateOf(RepoSettingsTab.GENERAL) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }

    var general by remember { mutableStateOf<GitHubRepoSettingsManager.RepoGeneralSettings?>(null) }
    var collaborators by remember { mutableStateOf(emptyList<GitHubRepoSettingsManager.RepoCollaborator>()) }
    var variables by remember { mutableStateOf(emptyList<GitHubRepoSettingsManager.RepoVariableMeta>()) }
    var secrets by remember { mutableStateOf(emptyList<GitHubRepoSettingsManager.RepoSecretMeta>()) }
    var webhooks by remember { mutableStateOf(emptyList<GitHubRepoSettingsManager.RepoWebhook>()) }
    var rulesets by remember { mutableStateOf(emptyList<GitHubRepoSettingsManager.RepoRulesetSummary>()) }
    var branchRules by remember { mutableStateOf(emptyList<String>()) }
    var branchProtection by remember {
        mutableStateOf(
            GitHubRepoSettingsManager.RepoBranchProtectionSummary(
                isProtected = false,
                allowForcePushes = false,
                allowDeletions = false,
                requiredLinearHistory = false,
                requiredConversationResolution = false,
                requiredApprovingReviews = 0,
                requiredStatusChecksCount = 0
            )
        )
    }
    var security by remember {
        mutableStateOf(
            GitHubRepoSettingsManager.RepoSecuritySettings(
                automatedSecurityFixes = false,
                vulnerabilityAlerts = false,
                privateVulnerabilityReporting = false
            )
        )
    }

    var rulesBranch by remember { mutableStateOf(repo.defaultBranch) }

    var showAddCollaborator by remember { mutableStateOf(false) }
    var showVariableDialog by remember { mutableStateOf(false) }
    var editingVariableName by remember { mutableStateOf<String?>(null) }
    var editingVariableValue by remember { mutableStateOf("") }

    var showWebhookDialog by remember { mutableStateOf(false) }
    var editingWebhook by remember { mutableStateOf<GitHubRepoSettingsManager.RepoWebhook?>(null) }

    suspend fun refreshCurrentTab() {
        loading = true
        error = ""
        try {
            when (selectedTab) {
                RepoSettingsTab.GENERAL -> {
                    general = GitHubRepoSettingsManager.getGeneral(context, repo.owner, repo.name)
                }
                RepoSettingsTab.ACCESS -> {
                    collaborators = GitHubRepoSettingsManager.listCollaborators(context, repo.owner, repo.name)
                }
                RepoSettingsTab.VARIABLES -> {
                    variables = GitHubRepoSettingsManager.listVariables(context, repo.owner, repo.name)
                }
                RepoSettingsTab.SECRETS -> {
                    secrets = GitHubRepoSettingsManager.listSecrets(context, repo.owner, repo.name)
                }
                RepoSettingsTab.WEBHOOKS -> {
                    webhooks = GitHubRepoSettingsManager.listWebhooks(context, repo.owner, repo.name)
                }
                RepoSettingsTab.RULES -> {
                    rulesets = GitHubRepoSettingsManager.listRulesets(context, repo.owner, repo.name)
                    branchRules = GitHubRepoSettingsManager.getRulesForBranch(context, repo.owner, repo.name, rulesBranch)
                    branchProtection = GitHubRepoSettingsManager.getBranchProtection(context, repo.owner, repo.name, rulesBranch)
                }
                RepoSettingsTab.SECURITY -> {
                    security = GitHubRepoSettingsManager.getSecuritySettings(context, repo.owner, repo.name)
                }
            }
        } catch (t: Throwable) {
            error = t.message ?: "Failed to load settings"
        }
        loading = false
    }

    LaunchedEffect(selectedTab, rulesBranch) {
        refreshCurrentTab()
    }

    GitHubScreenFrame(
        title = "> repository settings",
        subtitle = repo.fullName,
        onBack = onBack,
        trailing = {
            AiModuleGlyphAction(
                glyph = GhGlyphs.REFRESH,
                onClick = { scope.launch { refreshCurrentTab() } },
                tint = AiModuleTheme.colors.accent,
                contentDescription = "refresh",
            )
        },
    ) {

        Row(
            Modifier
                .fillMaxWidth()
                .background(AiModuleTheme.colors.surface)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RepoSettingsTab.values().forEach { tab ->
                val selected = selectedTab == tab
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) AiModuleTheme.colors.accent.copy(0.12f) else Color.Transparent)
                        .border(
                            1.dp,
                            if (selected) AiModuleTheme.colors.accent.copy(0.35f) else AiModuleTheme.colors.border,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { selectedTab = tab }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        tab.label,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) AiModuleTheme.colors.accent else AiModuleTheme.colors.textSecondary
                    )
                }
            }
        }

        if (error.isNotBlank()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFFF3B30).copy(0.10f))
                    .padding(12.dp)
            ) {
                Text(error, color = Color(0xFFFF3B30), fontSize = 12.sp)
            }
        }

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                AiModuleSpinner(label = "loading settings")
            }
        } else {
            when (selectedTab) {
                RepoSettingsTab.GENERAL -> GeneralTab(
                    general = general,
                    onSave = { updated ->
                        scope.launch {
                            val ok = GitHubRepoSettingsManager.updateGeneral(context, repo.owner, repo.name, updated)
                            Toast.makeText(context, if (ok) "Saved" else "Save failed", Toast.LENGTH_SHORT).show()
                            refreshCurrentTab()
                        }
                    }
                )
                RepoSettingsTab.ACCESS -> AccessTab(
                    collaborators = collaborators,
                    onAdd = { showAddCollaborator = true },
                    onRemove = { login ->
                        scope.launch {
                            val ok = GitHubRepoSettingsManager.removeCollaborator(context, repo.owner, repo.name, login)
                            Toast.makeText(context, if (ok) "Removed" else "Remove failed", Toast.LENGTH_SHORT).show()
                            refreshCurrentTab()
                        }
                    }
                )
                RepoSettingsTab.VARIABLES -> VariablesTab(
                    variables = variables,
                    onAdd = {
                        editingVariableName = null
                        editingVariableValue = ""
                        showVariableDialog = true
                    },
                    onEdit = { item ->
                        scope.launch {
                            val full = GitHubRepoSettingsManager.getVariable(context, repo.owner, repo.name, item.name)
                            editingVariableName = item.name
                            editingVariableValue = full?.value ?: ""
                            showVariableDialog = true
                        }
                    },
                    onDelete = { item ->
                        scope.launch {
                            val ok = GitHubRepoSettingsManager.deleteVariable(context, repo.owner, repo.name, item.name)
                            Toast.makeText(context, if (ok) "Deleted" else "Delete failed", Toast.LENGTH_SHORT).show()
                            refreshCurrentTab()
                        }
                    }
                )
                RepoSettingsTab.SECRETS -> SecretsTab(
                    secrets = secrets,
                    onDelete = { item ->
                        scope.launch {
                            val ok = GitHubRepoSettingsManager.deleteSecret(context, repo.owner, repo.name, item.name)
                            Toast.makeText(context, if (ok) "Deleted" else "Delete failed", Toast.LENGTH_SHORT).show()
                            refreshCurrentTab()
                        }
                    }
                )
                RepoSettingsTab.WEBHOOKS -> WebhooksTab(
                    hooks = webhooks,
                    onAdd = {
                        editingWebhook = null
                        showWebhookDialog = true
                    },
                    onEdit = {
                        editingWebhook = it
                        showWebhookDialog = true
                    },
                    onPing = { hook ->
                        scope.launch {
                            val ok = GitHubRepoSettingsManager.pingWebhook(context, repo.owner, repo.name, hook.id)
                            Toast.makeText(context, if (ok) "Ping sent" else "Ping failed", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onDelete = { hook ->
                        scope.launch {
                            val ok = GitHubRepoSettingsManager.deleteWebhook(context, repo.owner, repo.name, hook.id)
                            Toast.makeText(context, if (ok) "Deleted" else "Delete failed", Toast.LENGTH_SHORT).show()
                            refreshCurrentTab()
                        }
                    }
                )
                RepoSettingsTab.RULES -> RulesTab(
                    rulesBranch = rulesBranch,
                    onBranchChange = { rulesBranch = it },
                    rulesets = rulesets,
                    branchRules = branchRules,
                    protection = branchProtection
                )
                RepoSettingsTab.SECURITY -> SecurityTab(
                    security = security,
                    onToggleFixes = { enabled ->
                        scope.launch {
                            val ok = GitHubRepoSettingsManager.setAutomatedSecurityFixes(context, repo.owner, repo.name, enabled)
                            Toast.makeText(context, if (ok) "Updated" else "Update failed", Toast.LENGTH_SHORT).show()
                            refreshCurrentTab()
                        }
                    },
                    onToggleAlerts = { enabled ->
                        scope.launch {
                            val ok = GitHubRepoSettingsManager.setVulnerabilityAlerts(context, repo.owner, repo.name, enabled)
                            Toast.makeText(context, if (ok) "Updated" else "Update failed", Toast.LENGTH_SHORT).show()
                            refreshCurrentTab()
                        }
                    },
                    onTogglePrivateReporting = { enabled ->
                        scope.launch {
                            val ok = GitHubRepoSettingsManager.setPrivateVulnerabilityReporting(context, repo.owner, repo.name, enabled)
                            Toast.makeText(context, if (ok) "Updated" else "Update failed", Toast.LENGTH_SHORT).show()
                            refreshCurrentTab()
                        }
                    }
                )
            }
        }
    }

    if (showAddCollaborator) {
        AddCollaboratorDialog(
            onDismiss = { showAddCollaborator = false },
            onConfirm = { username, permission ->
                scope.launch {
                    val ok = GitHubRepoSettingsManager.addCollaborator(context, repo.owner, repo.name, username, permission)
                    Toast.makeText(context, if (ok) "Invitation sent" else "Add failed", Toast.LENGTH_SHORT).show()
                    showAddCollaborator = false
                    refreshCurrentTab()
                }
            }
        )
    }

    if (showVariableDialog) {
        VariableDialog(
            initialName = editingVariableName,
            initialValue = editingVariableValue,
            onDismiss = { showVariableDialog = false },
            onSave = { name, value ->
                scope.launch {
                    val ok = if (editingVariableName == null) {
                        GitHubRepoSettingsManager.createVariable(context, repo.owner, repo.name, name, value)
                    } else {
                        GitHubRepoSettingsManager.updateVariable(context, repo.owner, repo.name, name, value)
                    }
                    Toast.makeText(context, if (ok) "Saved" else "Save failed", Toast.LENGTH_SHORT).show()
                    showVariableDialog = false
                    refreshCurrentTab()
                }
            }
        )
    }

    if (showWebhookDialog) {
        WebhookDialog(
            webhook = editingWebhook,
            onDismiss = { showWebhookDialog = false },
            onSave = { url, events, secret, active ->
                scope.launch {
                    val ok = if (editingWebhook == null) {
                        GitHubRepoSettingsManager.createWebhook(context, repo.owner, repo.name, url, events, secret, active)
                    } else {
                        GitHubRepoSettingsManager.updateWebhook(context, repo.owner, repo.name, editingWebhook!!.id, url, events, secret, active)
                    }
                    Toast.makeText(context, if (ok) "Saved" else "Save failed", Toast.LENGTH_SHORT).show()
                    showWebhookDialog = false
                    refreshCurrentTab()
                }
            }
        )
    }
}

@Composable
private fun RepoSettingsTopBar(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    onRefresh: () -> Unit
) {
    GitHubPageBar(
        title = title,
        subtitle = subtitle.ifBlank { null },
        onBack = onBack,
    ) {
        AiModuleGlyphAction(
            glyph = GhGlyphs.REFRESH,
            onClick = onRefresh,
            tint = AiModuleTheme.colors.accent,
            contentDescription = "refresh",
        )
    }
}

@Composable
private fun GeneralTab(
    general: GitHubRepoSettingsManager.RepoGeneralSettings?,
    onSave: (GitHubRepoSettingsManager.RepoGeneralSettings) -> Unit
) {
    if (general == null) {
        EmptySettingsState("No access to repository general settings.")
        return
    }

    var name by remember(general) { mutableStateOf(general.name) }
    var description by remember(general) { mutableStateOf(settingsBlankNull(general.description)) }
    var homepage by remember(general) { mutableStateOf(settingsBlankNull(general.homepage)) }
    var defaultBranch by remember(general) { mutableStateOf(general.defaultBranch) }
    var archived by remember(general) { mutableStateOf(general.archived) }
    var hasIssues by remember(general) { mutableStateOf(general.hasIssues) }
    var hasProjects by remember(general) { mutableStateOf(general.hasProjects) }
    var hasWiki by remember(general) { mutableStateOf(general.hasWiki) }
    var hasDiscussions by remember(general) { mutableStateOf(general.hasDiscussions) }
    var allowForking by remember(general) { mutableStateOf(general.allowForking) }
    var webCommitSignoffRequired by remember(general) { mutableStateOf(general.webCommitSignoffRequired) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SettingsCard {
                Text("> ${general.name}", color = AiModuleTheme.colors.textPrimary, fontFamily = JetBrainsMono, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text("${general.defaultBranch} · ${general.visibility.ifBlank { "unknown" }} · 0 topics · 0 tags", color = AiModuleTheme.colors.textMuted, fontFamily = JetBrainsMono, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                SettingsInfo("owner", general.owner)
                SettingsInfo("visibility", general.visibility)
                Spacer(Modifier.height(6.dp))
                Text("repository name", color = AiModuleTheme.colors.textSecondary, fontFamily = JetBrainsMono, fontSize = 12.sp)
                GitHubTerminalTextField(name, { name = it }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                Text("description", color = AiModuleTheme.colors.textSecondary, fontFamily = JetBrainsMono, fontSize = 12.sp)
                GitHubTerminalTextField(description, { description = it }, placeholder = "empty", maxLines = 4)
                Spacer(Modifier.height(8.dp))
                Text("homepage url", color = AiModuleTheme.colors.textSecondary, fontFamily = JetBrainsMono, fontSize = 12.sp)
                GitHubTerminalTextField(homepage, { homepage = it }, placeholder = "empty", singleLine = true)
                Spacer(Modifier.height(8.dp))
                Text("default branch", color = AiModuleTheme.colors.textSecondary, fontFamily = JetBrainsMono, fontSize = 12.sp)
                GitHubTerminalTextField(defaultBranch, { defaultBranch = it }, singleLine = true)
            }
        }
        item {
            SettingsCard {
                Text("features", color = AiModuleTheme.colors.textPrimary, fontFamily = JetBrainsMono, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                SettingsSwitchRow("issues", hasIssues) { hasIssues = it }
                DividerMini()
                SettingsSwitchRow("projects", hasProjects) { hasProjects = it }
                DividerMini()
                SettingsSwitchRow("wiki", hasWiki) { hasWiki = it }
                DividerMini()
                SettingsSwitchRow("discussions", hasDiscussions) { hasDiscussions = it }
                DividerMini()
                SettingsSwitchRow("allow forking", allowForking) { allowForking = it }
                DividerMini()
                SettingsSwitchRow("require signoff on web commits", webCommitSignoffRequired) { webCommitSignoffRequired = it }
                DividerMini()
                SettingsSwitchRow("archived", archived) { archived = it }
            }
        }
        item {
            GitHubTerminalButton(
                label = "save general settings",
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
                            webCommitSignoffRequired = webCommitSignoffRequired,
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun AccessTab(
    collaborators: List<GitHubRepoSettingsManager.RepoCollaborator>,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            GitHubTerminalButton("add collaborator", onClick = onAdd, color = AiModuleTheme.colors.accent, modifier = Modifier.fillMaxWidth())
        }
        if (collaborators.isEmpty()) {
            item { EmptyCard("No collaborators returned or token has no access.") }
        } else {
            items(collaborators) { item ->
                SettingsCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AsyncImage(item.avatarUrl, item.login, Modifier.size(38.dp).clip(CircleShape))
                        Column(Modifier.weight(1f)) {
                            Text(item.login, color = AiModuleTheme.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(item.roleName.ifBlank { item.permissionSummary }, color = AiModuleTheme.colors.textSecondary, fontSize = 11.sp)
                            if (item.permissionSummary.isNotBlank()) {
                                Text(item.permissionSummary, color = AiModuleTheme.colors.textMuted, fontSize = 10.sp)
                            }
                        }
                        AiModuleTextAction(
                            label = "remove",
                            onClick = { onRemove(item.login) },
                            tint = AiModuleTheme.colors.error,
                        )
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
    onDelete: (GitHubRepoSettingsManager.RepoVariableMeta) -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            GitHubTerminalButton("add variable", onClick = onAdd, color = AiModuleTheme.colors.accent, modifier = Modifier.fillMaxWidth())
        }
        if (variables.isEmpty()) {
            item { EmptyCard("No repository variables found.") }
        } else {
            items(variables) { item ->
                SettingsCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(item.name, color = AiModuleTheme.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text("Updated ${dateLabel(item.updatedAt)}", color = AiModuleTheme.colors.textMuted, fontSize = 11.sp)
                        }
                        AiModuleGlyphAction(
                            glyph = GhGlyphs.EDIT,
                            onClick = { onEdit(item) },
                            tint = AiModuleTheme.colors.accent,
                            contentDescription = "edit",
                        )
                        AiModuleGlyphAction(
                            glyph = GhGlyphs.DELETE,
                            onClick = { onDelete(item) },
                            tint = AiModuleTheme.colors.error,
                            contentDescription = "delete",
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SecretsTab(
    secrets: List<GitHubRepoSettingsManager.RepoSecretMeta>,
    onDelete: (GitHubRepoSettingsManager.RepoSecretMeta) -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            SettingsCard {
                Text("Secrets", color = AiModuleTheme.colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(
                    "This build lists and deletes repository secrets. Creating or updating encrypted secrets needs an extra public-key flow, so I left that for the next step.",
                    color = AiModuleTheme.colors.textSecondary,
                    fontSize = 12.sp
                )
            }
        }
        if (secrets.isEmpty()) {
            item { EmptyCard("No repository secrets found or token cannot read them.") }
        } else {
            items(secrets) { item ->
                SettingsCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(item.name, color = AiModuleTheme.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text("Updated ${dateLabel(item.updatedAt)}", color = AiModuleTheme.colors.textMuted, fontSize = 11.sp)
                        }
                        AiModuleGlyphAction(
                            glyph = GhGlyphs.DELETE,
                            onClick = { onDelete(item) },
                            tint = AiModuleTheme.colors.error,
                            contentDescription = "delete",
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WebhooksTab(
    hooks: List<GitHubRepoSettingsManager.RepoWebhook>,
    onAdd: () -> Unit,
    onEdit: (GitHubRepoSettingsManager.RepoWebhook) -> Unit,
    onPing: (GitHubRepoSettingsManager.RepoWebhook) -> Unit,
    onDelete: (GitHubRepoSettingsManager.RepoWebhook) -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            GitHubTerminalButton("add webhook", onClick = onAdd, color = AiModuleTheme.colors.accent, modifier = Modifier.fillMaxWidth())
        }
        if (hooks.isEmpty()) {
            item { EmptyCard("No repository webhooks found.") }
        } else {
            items(hooks) { hook ->
                SettingsCard {
                    Text(hook.url.ifBlank { "Webhook #${hook.id}" }, color = AiModuleTheme.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    Text("Events: ${hook.events.joinToString(", ")}", color = AiModuleTheme.colors.textSecondary, fontSize = 11.sp)
                    Text("Content type: ${hook.contentType} • active=${hook.active}", color = AiModuleTheme.colors.textMuted, fontSize = 11.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmallOutlineButton("edit", AiModuleTheme.colors.accent) { onEdit(hook) }
                        SmallOutlineButton("ping", GitHubSuccessGreen) { onPing(hook) }
                        SmallOutlineButton("× delete", GitHubErrorRed) { onDelete(hook) }
                    }
                }
            }
        }
    }
}

@Composable
private fun RulesTab(
    rulesBranch: String,
    onBranchChange: (String) -> Unit,
    rulesets: List<GitHubRepoSettingsManager.RepoRulesetSummary>,
    branchRules: List<String>,
    protection: GitHubRepoSettingsManager.RepoBranchProtectionSummary
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SettingsCard {
                Text("Branch to inspect", color = AiModuleTheme.colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                AiModuleTextField(
                    value = rulesBranch,
                    onValueChange = onBranchChange,
                    label = "Branch name",
                )
            }
        }
        item {
            SettingsCard {
                Text("Protection summary", color = AiModuleTheme.colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                SettingsInfo("Protected", yesNo(protection.isProtected))
                SettingsInfo("Force pushes", yesNo(protection.allowForcePushes))
                SettingsInfo("Deletions", yesNo(protection.allowDeletions))
                SettingsInfo("Linear history", yesNo(protection.requiredLinearHistory))
                SettingsInfo("Conversation resolution", yesNo(protection.requiredConversationResolution))
                SettingsInfo("Required approvals", protection.requiredApprovingReviews.toString())
                SettingsInfo("Required status checks", protection.requiredStatusChecksCount.toString())
            }
        }
        item {
            SettingsCard {
                Text("Active rules for branch", color = AiModuleTheme.colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                if (branchRules.isEmpty()) {
                    Text("No active branch rules returned.", color = AiModuleTheme.colors.textSecondary, fontSize = 12.sp)
                } else {
                    branchRules.forEach { rule ->
                        MiniTag(rule)
                    }
                }
            }
        }
        item {
            SettingsCard {
                Text("Rulesets", color = AiModuleTheme.colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                if (rulesets.isEmpty()) {
                    Text("No repository rulesets returned.", color = AiModuleTheme.colors.textSecondary, fontSize = 12.sp)
                } else {
                    rulesets.forEach { ruleset ->
                        Column(Modifier.padding(vertical = 6.dp)) {
                            Text(ruleset.name, color = AiModuleTheme.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text("${ruleset.target} • ${ruleset.enforcement} • ${ruleset.sourceType}", color = AiModuleTheme.colors.textMuted, fontSize = 11.sp)
                            DividerMini()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SecurityTab(
    security: GitHubRepoSettingsManager.RepoSecuritySettings,
    onToggleFixes: (Boolean) -> Unit,
    onToggleAlerts: (Boolean) -> Unit,
    onTogglePrivateReporting: (Boolean) -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SettingsCard {
                Text("Repository security", color = AiModuleTheme.colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                SettingsSwitchRow("Automated security fixes", security.automatedSecurityFixes, onToggleFixes)
                DividerMini()
                SettingsSwitchRow("Vulnerability alerts", security.vulnerabilityAlerts, onToggleAlerts)
                DividerMini()
                SettingsSwitchRow("Private vulnerability reporting", security.privateVulnerabilityReporting, onTogglePrivateReporting)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Some security toggles depend on repository type and token permissions. Public-repo/private-reporting support can differ from private repositories and from weaker tokens.",
                    color = AiModuleTheme.colors.textSecondary,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, AiModuleTheme.colors.border)
            .background(AiModuleTheme.colors.surface)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}

@Composable
private fun SettingsInfo(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = AiModuleTheme.colors.textSecondary, fontFamily = JetBrainsMono, fontSize = 12.sp, modifier = Modifier.widthIn(min = 120.dp))
        Text(settingsBlankNull(value).ifBlank { "—" }, color = AiModuleTheme.colors.textPrimary, fontFamily = JetBrainsMono, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SettingsSwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    GitHubTerminalCheckbox(
        label = label,
        checked = checked,
        onToggle = { onCheckedChange(!checked) },
    )
}

private fun settingsBlankNull(value: String): String =
    value.trim().takeUnless { it.equals("null", ignoreCase = true) }.orEmpty()

@Composable
private fun DividerMini() {
    Box(Modifier.fillMaxWidth().height(0.5.dp).background(AiModuleTheme.colors.border))
}

@Composable
private fun EmptySettingsState(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = AiModuleTheme.colors.textMuted, fontSize = 13.sp)
    }
}

@Composable
private fun EmptyCard(text: String) {
    SettingsCard {
        Text(text, color = AiModuleTheme.colors.textSecondary, fontSize = 12.sp)
    }
}

@Composable
private fun SmallOutlineButton(label: String, color: Color, onClick: () -> Unit) {
    GitHubTerminalButton(label, onClick = onClick, color = color)
}

@Composable
private fun MiniTag(label: String) {
    Box(
        Modifier
            .padding(top = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(AiModuleTheme.colors.accent.copy(0.10f))
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) {
        Text(label, color = AiModuleTheme.colors.accent, fontSize = 11.sp)
    }
}

@Composable
private fun AddCollaboratorDialog(
    onDismiss: () -> Unit,
    onConfirm: (username: String, permission: String) -> Unit
) {
    var username by remember { mutableStateOf("") }
    var permission by remember { mutableStateOf("push") }
    val options = listOf("pull", "triage", "push", "maintain", "admin")

    AiModuleAlertDialog(
        onDismissRequest = onDismiss,
        title = "add collaborator",
        confirmButton = {
            AiModuleTextAction(
                label = "add",
                onClick = { if (username.isNotBlank()) onConfirm(username.trim(), permission) },
                tint = AiModuleTheme.colors.accent,
            )
        },
        dismissButton = {
            AiModuleTextAction(label = "cancel", onClick = onDismiss, tint = AiModuleTheme.colors.textSecondary)
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AiModuleTextField(username, { username = it }, label = "GitHub username")
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                options.forEach { item ->
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (permission == item) AiModuleTheme.colors.accent.copy(0.12f) else AiModuleTheme.colors.background)
                            .border(1.dp, if (permission == item) AiModuleTheme.colors.accent.copy(0.35f) else AiModuleTheme.colors.border, RoundedCornerShape(8.dp))
                            .clickable { permission = item }
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                    ) {
                        Text(
                            item,
                            color = if (permission == item) AiModuleTheme.colors.accent else AiModuleTheme.colors.textSecondary,
                            fontSize = 11.sp,
                            fontFamily = JetBrainsMono,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VariableDialog(
    initialName: String?,
    initialValue: String,
    onDismiss: () -> Unit,
    onSave: (name: String, value: String) -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName ?: "") }
    var value by remember(initialName, initialValue) { mutableStateOf(initialValue) }

    AiModuleAlertDialog(
        onDismissRequest = onDismiss,
        title = if (initialName == null) "add variable" else "edit variable",
        confirmButton = {
            AiModuleTextAction(
                label = "save",
                onClick = { if (name.isNotBlank()) onSave(name.trim(), value) },
                tint = AiModuleTheme.colors.accent,
            )
        },
        dismissButton = {
            AiModuleTextAction(label = "cancel", onClick = onDismiss, tint = AiModuleTheme.colors.textSecondary)
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AiModuleTextField(
                name,
                { if (initialName == null) name = it.uppercase() },
                enabled = initialName == null,
                label = "Name",
            )
            AiModuleTextField(value, { value = it }, label = "Value", maxLines = 5)
        }
    }
}

@Composable
private fun WebhookDialog(
    webhook: GitHubRepoSettingsManager.RepoWebhook?,
    onDismiss: () -> Unit,
    onSave: (url: String, events: List<String>, secret: String, active: Boolean) -> Unit
) {
    var url by remember(webhook) { mutableStateOf(webhook?.url ?: "") }
    var eventsRaw by remember(webhook) { mutableStateOf((webhook?.events ?: listOf("push")).joinToString(",")) }
    var secret by remember { mutableStateOf("") }
    var active by remember(webhook) { mutableStateOf(webhook?.active ?: true) }

    AiModuleAlertDialog(
        onDismissRequest = onDismiss,
        title = if (webhook == null) "add webhook" else "edit webhook",
        confirmButton = {
            AiModuleTextAction(
                label = "save",
                onClick = {
                    val events = eventsRaw.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    if (url.isNotBlank() && events.isNotEmpty()) {
                        onSave(url.trim(), events, secret, active)
                    }
                },
                tint = AiModuleTheme.colors.accent,
            )
        },
        dismissButton = {
            AiModuleTextAction(label = "cancel", onClick = onDismiss, tint = AiModuleTheme.colors.textSecondary)
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AiModuleTextField(url, { url = it }, label = "Webhook URL")
            AiModuleTextField(eventsRaw, { eventsRaw = it }, label = "Events (comma separated)", maxLines = 3)
            AiModuleTextField(secret, { secret = it }, label = "Secret (optional)")
            SettingsSwitchRow("Active", active) { active = it }
        }
    }
}

private fun yesNo(value: Boolean): String = if (value) "Yes" else "No"

private fun dateLabel(raw: String): String = raw.take(10).ifBlank { "—" }
