package com.glassfiles.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glassfiles.data.Strings
import com.glassfiles.ui.components.AiModuleAlertDialog
import com.glassfiles.ui.components.AiModuleHairline
import com.glassfiles.ui.components.AiModulePageBar
import com.glassfiles.ui.components.AiModulePillButton
import com.glassfiles.ui.components.AiModuleSpinner
import com.glassfiles.ui.components.AiModuleTextAction
import com.glassfiles.ui.components.AiModuleTextField
import com.glassfiles.data.github.GHRepoSettings
import com.glassfiles.data.github.GHTag
import com.glassfiles.data.github.GitHubManager
import com.glassfiles.ui.theme.AiModuleTheme
import com.glassfiles.ui.theme.*
import kotlinx.coroutines.launch

@Composable
internal fun RepoSettingsScreen(
    repoOwner: String,
    repoName: String,
    onBack: () -> Unit,
    onBranchProtection: () -> Unit = {},
    onCollaborators: () -> Unit = {},
    onTeams: () -> Unit = {},
    onWebhooks: () -> Unit = {},
    onDiscussions: () -> Unit = {},
    onRulesets: () -> Unit = {},
    onSecurity: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var settings by remember { mutableStateOf<GHRepoSettings?>(null) }
    var tags by remember { mutableStateOf<List<GHTag>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var showArchiveConfirm by remember { mutableStateOf<Boolean?>(null) }

    // Editable fields
    var description by remember { mutableStateOf("") }
    var homepage by remember { mutableStateOf("") }
    var hasIssues by remember { mutableStateOf(true) }
    var hasProjects by remember { mutableStateOf(true) }
    var hasWiki by remember { mutableStateOf(true) }
    var hasDiscussions by remember { mutableStateOf(false) }
    var allowForking by remember { mutableStateOf(true) }
    var isTemplate by remember { mutableStateOf(false) }
    var archived by remember { mutableStateOf(false) }
    var allowMergeCommit by remember { mutableStateOf(true) }
    var allowSquashMerge by remember { mutableStateOf(true) }
    var allowRebaseMerge by remember { mutableStateOf(true) }
    var deleteBranchOnMerge by remember { mutableStateOf(false) }
    var topics by remember { mutableStateOf<List<String>>(emptyList()) }
    var newTopic by remember { mutableStateOf("") }

    LaunchedEffect(repoOwner, repoName) {
        val s = GitHubManager.getRepoSettings(context, repoOwner, repoName)
        tags = GitHubManager.getRepoTags(context, repoOwner, repoName)
        settings = s
        if (s != null) {
            description = s.description
            homepage = s.homepage
            hasIssues = s.hasIssues
            hasProjects = s.hasProjects
            hasWiki = s.hasWiki
            hasDiscussions = s.hasDiscussions
            allowForking = s.allowForking
            isTemplate = s.isTemplate
            archived = s.archived
            allowMergeCommit = s.allowMergeCommit
            allowSquashMerge = s.allowSquashMerge
            allowRebaseMerge = s.allowRebaseMerge
            deleteBranchOnMerge = s.deleteBranchOnMerge
            topics = s.topics
        }
        loading = false
    }

    fun saveChanges() {
        saving = true
        scope.launch {
            val cleanTopics = topics.map { normalizeRepoTopic(it) }.filter { it.isNotBlank() }.distinct().take(20)
            val ok = GitHubManager.updateRepoSettings(
                context = context,
                owner = repoOwner,
                repo = repoName,
                description = description,
                homepage = homepage,
                hasIssues = hasIssues,
                hasProjects = hasProjects,
                hasWiki = hasWiki,
                hasDiscussions = hasDiscussions,
                allowForking = allowForking,
                isTemplate = isTemplate,
                archived = archived,
                topics = null,
                allowMergeCommit = allowMergeCommit,
                allowSquashMerge = allowSquashMerge,
                allowRebaseMerge = allowRebaseMerge,
                deleteBranchOnMerge = deleteBranchOnMerge
            ) && GitHubManager.replaceRepoTopics(context, repoOwner, repoName, cleanTopics)
            Toast.makeText(context, if (ok) "Settings saved" else "Failed to save", Toast.LENGTH_SHORT).show()
            saving = false
            if (ok) {
                // Refresh
                val s = GitHubManager.getRepoSettings(context, repoOwner, repoName)
                settings = s
                topics = s?.topics ?: cleanTopics
                tags = GitHubManager.getRepoTags(context, repoOwner, repoName)
            }
        }
    }

    val hasUnsavedChanges = settings?.let { s ->
        description != s.description ||
            homepage != s.homepage ||
            hasIssues != s.hasIssues ||
            hasProjects != s.hasProjects ||
            hasWiki != s.hasWiki ||
            hasDiscussions != s.hasDiscussions ||
            allowForking != s.allowForking ||
            isTemplate != s.isTemplate ||
            archived != s.archived ||
            allowMergeCommit != s.allowMergeCommit ||
            allowSquashMerge != s.allowSquashMerge ||
            allowRebaseMerge != s.allowRebaseMerge ||
            deleteBranchOnMerge != s.deleteBranchOnMerge ||
            topics.map(::normalizeRepoTopic).filter { it.isNotBlank() }.distinct() != s.topics.map(::normalizeRepoTopic).filter { it.isNotBlank() }.distinct()
    } ?: false

    GitHubScreenFrame(
        title = "> repo settings",
        subtitle = "$repoOwner/$repoName",
        onBack = onBack,
        trailing = {
                if (saving) {
                    AiModuleSpinner()
                } else {
                    GitHubTopBarTextAction(
                        label = if (hasUnsavedChanges) "save" else "saved",
                        onClick = { saveChanges() },
                        enabled = hasUnsavedChanges,
                        tint = if (hasUnsavedChanges) AiModuleTheme.colors.accent else AiModuleTheme.colors.textMuted,
                    )
                }
        },
    ) {

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
                    RepoSettingsSummaryCard(settings, tags, hasUnsavedChanges)
                }

                // General section
                item { SectionHeader("General") }

                item {
                    SettingsCard {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            // Description
                            AiModuleTextField(
                                value = description,
                                onValueChange = { description = it },
                                label = "Description",
                                maxLines = 3,
                            )

                            // Homepage
                            AiModuleTextField(
                                value = homepage,
                                onValueChange = { homepage = it },
                                label = "Homepage URL",
                            )
                        }
                    }
                }

                // Features section
                item { SectionHeader("Features") }

                item {
                    SettingsCard {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ToggleRow("Issues", hasIssues, Icons.Rounded.BugReport) { hasIssues = it }
                            ToggleRow("Projects", hasProjects, Icons.Rounded.Dashboard) { hasProjects = it }
                            ToggleRow("Wiki", hasWiki, Icons.Rounded.MenuBook) { hasWiki = it }
                            ToggleRow("Discussions", hasDiscussions, Icons.Rounded.Forum) { hasDiscussions = it }
                        }
                    }
                }

                // Merge section
                item { SectionHeader("Pull Requests") }

                item {
                    SettingsCard {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ToggleRow("Allow merge commits", allowMergeCommit, Icons.Rounded.MergeType) { allowMergeCommit = it }
                            ToggleRow("Allow squash merging", allowSquashMerge, Icons.Rounded.Compress) { allowSquashMerge = it }
                            ToggleRow("Allow rebase merging", allowRebaseMerge, Icons.Rounded.LinearScale) { allowRebaseMerge = it }
                            ToggleRow("Delete head branches on merge", deleteBranchOnMerge, Icons.Rounded.DeleteSweep) { deleteBranchOnMerge = it }
                        }
                    }
                }

                // Administration section
                item { SectionHeader("Administration") }

                item {
                    SettingsCard {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            // Branch protection button
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                    .background(AiModuleTheme.colors.background)
                                    .clickable { onBranchProtection() }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(Icons.Rounded.Shield, null, Modifier.size(22.dp), tint = AiModuleTheme.colors.accent)
                                Column(Modifier.weight(1f)) {
                                    Text("Branch protection rules", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = AiModuleTheme.colors.textPrimary)
                                    Text("Require reviews, status checks, and more", fontSize = 12.sp, color = AiModuleTheme.colors.textMuted)
                                }
                                Icon(Icons.Rounded.ChevronRight, null, Modifier.size(16.dp), tint = AiModuleTheme.colors.textMuted)
                            }

                            // Collaborators button
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                    .background(AiModuleTheme.colors.background)
                                    .clickable { onCollaborators() }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(Icons.Rounded.Group, null, Modifier.size(22.dp), tint = AiModuleTheme.colors.accent)
                                Column(Modifier.weight(1f)) {
                                    Text("Manage collaborators", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = AiModuleTheme.colors.textPrimary)
                                    Text("Add, remove, or change permissions", fontSize = 12.sp, color = AiModuleTheme.colors.textMuted)
                                }
                                Icon(Icons.Rounded.ChevronRight, null, Modifier.size(16.dp), tint = AiModuleTheme.colors.textMuted)
                            }

                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                    .background(AiModuleTheme.colors.background)
                                    .clickable { onTeams() }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(Icons.Rounded.Group, null, Modifier.size(22.dp), tint = AiModuleTheme.colors.accent)
                                Column(Modifier.weight(1f)) {
                                    Text("Manage teams", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = AiModuleTheme.colors.textPrimary)
                                    Text("Org team access and permissions", fontSize = 12.sp, color = AiModuleTheme.colors.textMuted)
                                }
                                Icon(Icons.Rounded.ChevronRight, null, Modifier.size(16.dp), tint = AiModuleTheme.colors.textMuted)
                            }

                            ToggleRow("Allow forking", allowForking, Icons.Rounded.ForkRight) { allowForking = it }
                            ToggleRow("Template repository", isTemplate, Icons.Rounded.ContentCopy) { isTemplate = it }

                            // Webhooks button
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                    .background(AiModuleTheme.colors.background)
                                    .clickable { onWebhooks() }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(Icons.Rounded.Webhook, null, Modifier.size(22.dp), tint = AiModuleTheme.colors.accent)
                                Column(Modifier.weight(1f)) {
                                    Text("Webhooks", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = AiModuleTheme.colors.textPrimary)
                                    Text("Manage repository webhooks", fontSize = 12.sp, color = AiModuleTheme.colors.textMuted)
                                }
                                Icon(Icons.Rounded.ChevronRight, null, Modifier.size(16.dp), tint = AiModuleTheme.colors.textMuted)
                            }

                            // Discussions button
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                    .background(AiModuleTheme.colors.background)
                                    .clickable { onDiscussions() }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(Icons.Rounded.Forum, null, Modifier.size(22.dp), tint = AiModuleTheme.colors.accent)
                                Column(Modifier.weight(1f)) {
                                    Text("Discussions", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = AiModuleTheme.colors.textPrimary)
                                    Text("View repository discussions", fontSize = 12.sp, color = AiModuleTheme.colors.textMuted)
                                }
                                Icon(Icons.Rounded.ChevronRight, null, Modifier.size(16.dp), tint = AiModuleTheme.colors.textMuted)
                            }

                            // Rulesets button
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                    .background(AiModuleTheme.colors.background)
                                    .clickable { onRulesets() }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(Icons.Rounded.Rule, null, Modifier.size(22.dp), tint = AiModuleTheme.colors.accent)
                                Column(Modifier.weight(1f)) {
                                    Text("Rulesets", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = AiModuleTheme.colors.textPrimary)
                                    Text("View repository rulesets", fontSize = 12.sp, color = AiModuleTheme.colors.textMuted)
                                }
                                Icon(Icons.Rounded.ChevronRight, null, Modifier.size(16.dp), tint = AiModuleTheme.colors.textMuted)
                            }

                            // Security button
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                    .background(AiModuleTheme.colors.background)
                                    .clickable { onSecurity() }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(Icons.Rounded.Security, null, Modifier.size(22.dp), tint = Color(0xFFFF3B30))
                                Column(Modifier.weight(1f)) {
                                    Text("Security", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = AiModuleTheme.colors.textPrimary)
                                    Text("Dependabot alerts", fontSize = 12.sp, color = AiModuleTheme.colors.textMuted)
                                }
                                Icon(Icons.Rounded.ChevronRight, null, Modifier.size(16.dp), tint = AiModuleTheme.colors.textMuted)
                            }

                            // Archive toggle
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                    .background(if (archived) Color(0xFFFF3B30).copy(0.1f) else AiModuleTheme.colors.background)
                                    .clickable { showArchiveConfirm = !archived }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    if (archived) Icons.Rounded.Unarchive else Icons.Rounded.Archive,
                                    null,
                                    Modifier.size(22.dp),
                                    tint = if (archived) Color(0xFFFF3B30) else AiModuleTheme.colors.textSecondary
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        if (archived) "Unarchive this repository" else "Archive this repository",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (archived) Color(0xFFFF3B30) else AiModuleTheme.colors.textPrimary
                                    )
                                    Text(
                                        if (archived) "This repository is currently archived" else "Archive makes the repository read-only",
                                        fontSize = 12.sp,
                                        color = AiModuleTheme.colors.textMuted
                                    )
                                }
                                Switch(
                                    checked = archived,
                                    onCheckedChange = { showArchiveConfirm = it },
                                    colors = SwitchDefaults.colors(
                                        checkedTrackColor = Color(0xFFFF3B30),
                                        checkedThumbColor = Color.White
                                    )
                                )
                            }
                        }
                    }
                }

                // Topics section
                item { SectionHeader("Topics") }

                item {
                    SettingsCard {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            // Topic chips
                            if (topics.isNotEmpty()) {
                                Row(
                                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    topics.forEach { topic ->
                                        TopicChip(topic) {
                                            topics = topics - topic
                                        }
                                    }
                                }
                            }

                            // Add topic
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(Modifier.weight(1f)) {
                                    AiModuleTextField(
                                        value = newTopic,
                                        onValueChange = { newTopic = normalizeRepoTopic(it) },
                                        label = "Add topic",
                                    )
                                }
                                AiModulePillButton(
                                    label = "+ add",
                                    enabled = newTopic.isNotBlank() && topics.size < 20,
                                    onClick = {
                                        val normalized = normalizeRepoTopic(newTopic)
                                        if (normalized.isNotBlank() && normalized !in topics.map(::normalizeRepoTopic) && topics.size < 20) {
                                            topics = topics + normalized
                                            newTopic = ""
                                        }
                                    },
                                )
                            }
                            Text("${topics.size}/20 topics", fontSize = 11.sp, color = AiModuleTheme.colors.textMuted)
                        }
                    }
                }

                item { SectionHeader("Tags") }

                item {
                    SettingsCard {
                        if (tags.isEmpty()) {
                            Text("No tags returned for this repository.", fontSize = 13.sp, color = AiModuleTheme.colors.textMuted)
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                tags.take(12).forEach { tag ->
                                    RepoTagRow(tag)
                                }
                                if (tags.size > 12) {
                                    Text("+${tags.size - 12} more tags", fontSize = 11.sp, color = AiModuleTheme.colors.textMuted)
                                }
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(32.dp)) }
            }
        }
    }

    val archiveTarget = showArchiveConfirm
    if (archiveTarget != null) {
        AiModuleAlertDialog(
            onDismissRequest = { showArchiveConfirm = null },
            title = if (archiveTarget) "archive repository" else "unarchive repository",
            confirmButton = {
                AiModuleTextAction(
                    label = if (archiveTarget) "archive" else "unarchive",
                    onClick = {
                        archived = archiveTarget
                        showArchiveConfirm = null
                    },
                    tint = if (archiveTarget) AiModuleTheme.colors.error else AiModuleTheme.colors.accent,
                )
            },
            dismissButton = {
                AiModuleTextAction(
                    label = Strings.cancel.lowercase(),
                    onClick = { showArchiveConfirm = null },
                    tint = AiModuleTheme.colors.textSecondary,
                )
            },
        ) {
            Text(
                if (archiveTarget) "Archiving makes the repository read-only until it is unarchived. Save settings after confirming."
                else "Unarchiving restores normal repository writes after you save settings.",
                color = AiModuleTheme.colors.textSecondary,
                fontSize = 13.sp,
                fontFamily = JetBrainsMono,
            )
        }
    }
}

@Composable
private fun RepoSettingsSummaryCard(settings: GHRepoSettings?, tags: List<GHTag>, hasUnsavedChanges: Boolean) {
    SettingsCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Rounded.Settings, null, Modifier.size(22.dp), tint = AiModuleTheme.colors.accent)
            Column(Modifier.weight(1f)) {
                Text(settings?.name ?: "Repository", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = AiModuleTheme.colors.textPrimary)
                Text(
                    buildString {
                        append(settings?.defaultBranch ?: "default branch")
                        append(" · ")
                        append(if (settings?.isPrivate == true) "private" else "public")
                        append(" · ")
                        append("${settings?.topics?.size ?: 0} topics")
                        append(" · ")
                        append("${tags.size} tags")
                    },
                    fontSize = 11.sp,
                    color = AiModuleTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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
        if (settings?.archived == true) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Repository is archived and read-only.",
                fontSize = 12.sp,
                color = Color(0xFFFF3B30),
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFFFF3B30).copy(0.10f)).padding(horizontal = 10.dp, vertical = 7.dp)
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String, color: Color = AiModuleTheme.colors.textPrimary) {
    Text(
        title,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(AiModuleTheme.colors.surface).padding(14.dp)
    ) {
        content()
    }
}

@Composable
private fun RepoTagRow(tag: GHTag) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(AiModuleTheme.colors.background).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Rounded.Label, null, Modifier.size(16.dp), tint = AiModuleTheme.colors.accent)
        Column(Modifier.weight(1f)) {
            Text(tag.name, fontSize = 13.sp, color = AiModuleTheme.colors.textPrimary, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (tag.commitSha.isNotBlank()) {
                Text(tag.commitSha.take(7), fontSize = 10.sp, color = AiModuleTheme.colors.textMuted)
            }
        }
        Icon(Icons.Rounded.ChevronRight, null, Modifier.size(15.dp), tint = AiModuleTheme.colors.textMuted)
    }
}

@Composable
internal fun ToggleRow(
    label: String,
    checked: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onToggle: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .clickable { onToggle(!checked) }
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = if (checked) AiModuleTheme.colors.accent else AiModuleTheme.colors.textSecondary)
        Text(label, fontSize = 14.sp, color = AiModuleTheme.colors.textPrimary, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(checkedTrackColor = AiModuleTheme.colors.accent)
        )
    }
}

private fun normalizeRepoTopic(value: String): String =
    value.lowercase()
        .replace(Regex("""[^a-z0-9-]+"""), "-")
        .replace(Regex("""-+"""), "-")
        .trim('-')
        .take(50)

@Composable
private fun TopicChip(topic: String, onRemove: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).background(AiModuleTheme.colors.accent.copy(0.1f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(topic, fontSize = 12.sp, color = AiModuleTheme.colors.accent, fontWeight = FontWeight.Medium)
        Icon(
            Icons.Rounded.Close,
            null,
            Modifier.size(14.dp).clickable { onRemove() },
            tint = AiModuleTheme.colors.accent
        )
    }
}
