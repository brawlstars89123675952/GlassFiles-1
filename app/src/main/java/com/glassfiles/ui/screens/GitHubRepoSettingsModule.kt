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
import com.glassfiles.data.github.GHRepoSettings
import com.glassfiles.data.github.GitHubManager
import com.glassfiles.ui.theme.*
import kotlinx.coroutines.launch

@Composable
internal fun RepoSettingsScreen(
    repoOwner: String,
    repoName: String,
    onBack: () -> Unit,
    onBranchProtection: () -> Unit = {},
    onCollaborators: () -> Unit = {},
    onWebhooks: () -> Unit = {},
    onDiscussions: () -> Unit = {},
    onRulesets: () -> Unit = {},
    onSecurity: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var settings by remember { mutableStateOf<GHRepoSettings?>(null) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }

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
                topics = topics,
                allowMergeCommit = allowMergeCommit,
                allowSquashMerge = allowSquashMerge,
                allowRebaseMerge = allowRebaseMerge,
                deleteBranchOnMerge = deleteBranchOnMerge
            )
            Toast.makeText(context, if (ok) "Settings saved" else "Failed to save", Toast.LENGTH_SHORT).show()
            saving = false
            if (ok) {
                // Refresh
                val s = GitHubManager.getRepoSettings(context, repoOwner, repoName)
                settings = s
            }
        }
    }

    Column(Modifier.fillMaxSize().background(SurfaceLight)) {
        GHTopBar(
            title = "Repository Settings",
            subtitle = "$repoOwner/$repoName",
            onBack = onBack,
            actions = {
                if (saving) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = Blue, strokeWidth = 2.dp)
                } else {
                    TextButton(onClick = { saveChanges() }) {
                        Text("Save", color = Blue, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        )

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
                // General section
                item { SectionHeader("General") }

                item {
                    SettingsCard {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            // Description
                            OutlinedTextField(
                                value = description,
                                onValueChange = { description = it },
                                label = { Text("Description") },
                                modifier = Modifier.fillMaxWidth(),
                                maxLines = 3
                            )

                            // Homepage
                            OutlinedTextField(
                                value = homepage,
                                onValueChange = { homepage = it },
                                label = { Text("Homepage URL") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
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

                // Danger zone
                item { SectionHeader("Danger Zone", Color(0xFFFF3B30)) }

                item {
                    SettingsCard {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            // Branch protection button
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                    .background(SurfaceLight)
                                    .clickable { onBranchProtection() }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(Icons.Rounded.Shield, null, Modifier.size(22.dp), tint = Blue)
                                Column(Modifier.weight(1f)) {
                                    Text("Branch protection rules", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                                    Text("Require reviews, status checks, and more", fontSize = 12.sp, color = TextTertiary)
                                }
                                Icon(Icons.Rounded.ChevronRight, null, Modifier.size(16.dp), tint = TextTertiary)
                            }

                            // Collaborators button
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                    .background(SurfaceLight)
                                    .clickable { onCollaborators() }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(Icons.Rounded.Group, null, Modifier.size(22.dp), tint = Blue)
                                Column(Modifier.weight(1f)) {
                                    Text("Manage collaborators", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                                    Text("Add, remove, or change permissions", fontSize = 12.sp, color = TextTertiary)
                                }
                                Icon(Icons.Rounded.ChevronRight, null, Modifier.size(16.dp), tint = TextTertiary)
                            }

                            ToggleRow("Allow forking", allowForking, Icons.Rounded.ForkRight) { allowForking = it }
                            ToggleRow("Template repository", isTemplate, Icons.Rounded.ContentCopy) { isTemplate = it }

                            // Webhooks button
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                    .background(SurfaceLight)
                                    .clickable { onWebhooks() }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(Icons.Rounded.Webhook, null, Modifier.size(22.dp), tint = Blue)
                                Column(Modifier.weight(1f)) {
                                    Text("Webhooks", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                                    Text("Manage repository webhooks", fontSize = 12.sp, color = TextTertiary)
                                }
                                Icon(Icons.Rounded.ChevronRight, null, Modifier.size(16.dp), tint = TextTertiary)
                            }

                            // Discussions button
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                    .background(SurfaceLight)
                                    .clickable { onDiscussions() }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(Icons.Rounded.Forum, null, Modifier.size(22.dp), tint = Blue)
                                Column(Modifier.weight(1f)) {
                                    Text("Discussions", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                                    Text("View repository discussions", fontSize = 12.sp, color = TextTertiary)
                                }
                                Icon(Icons.Rounded.ChevronRight, null, Modifier.size(16.dp), tint = TextTertiary)
                            }

                            // Rulesets button
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                    .background(SurfaceLight)
                                    .clickable { onRulesets() }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(Icons.Rounded.Rule, null, Modifier.size(22.dp), tint = Blue)
                                Column(Modifier.weight(1f)) {
                                    Text("Rulesets", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                                    Text("View repository rulesets", fontSize = 12.sp, color = TextTertiary)
                                }
                                Icon(Icons.Rounded.ChevronRight, null, Modifier.size(16.dp), tint = TextTertiary)
                            }

                            // Security button
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                    .background(SurfaceLight)
                                    .clickable { onSecurity() }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(Icons.Rounded.Security, null, Modifier.size(22.dp), tint = Color(0xFFFF3B30))
                                Column(Modifier.weight(1f)) {
                                    Text("Security", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                                    Text("Dependabot alerts", fontSize = 12.sp, color = TextTertiary)
                                }
                                Icon(Icons.Rounded.ChevronRight, null, Modifier.size(16.dp), tint = TextTertiary)
                            }

                            // Archive toggle
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                    .background(if (archived) Color(0xFFFF3B30).copy(0.1f) else SurfaceLight)
                                    .clickable { archived = !archived }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    if (archived) Icons.Rounded.Unarchive else Icons.Rounded.Archive,
                                    null,
                                    Modifier.size(22.dp),
                                    tint = if (archived) Color(0xFFFF3B30) else TextSecondary
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        if (archived) "Unarchive this repository" else "Archive this repository",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (archived) Color(0xFFFF3B30) else TextPrimary
                                    )
                                    Text(
                                        if (archived) "This repository is currently archived" else "Archive makes the repository read-only",
                                        fontSize = 12.sp,
                                        color = TextTertiary
                                    )
                                }
                                Switch(
                                    checked = archived,
                                    onCheckedChange = { archived = it },
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
                                OutlinedTextField(
                                    value = newTopic,
                                    onValueChange = { newTopic = it.lowercase().replace(" ", "-") },
                                    label = { Text("Add topic") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                Button(
                                    onClick = {
                                        if (newTopic.isNotBlank() && newTopic !in topics) {
                                            topics = topics + newTopic
                                            newTopic = ""
                                        }
                                    },
                                    enabled = newTopic.isNotBlank()
                                ) {
                                    Text("Add")
                                }
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
private fun SectionHeader(title: String, color: Color = TextPrimary) {
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
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceWhite).padding(14.dp)
    ) {
        content()
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
        Icon(icon, null, Modifier.size(20.dp), tint = if (checked) Blue else TextSecondary)
        Text(label, fontSize = 14.sp, color = TextPrimary, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(checkedTrackColor = Blue)
        )
    }
}

@Composable
private fun TopicChip(topic: String, onRemove: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).background(Blue.copy(0.1f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(topic, fontSize = 12.sp, color = Blue, fontWeight = FontWeight.Medium)
        Icon(
            Icons.Rounded.Close,
            null,
            Modifier.size(14.dp).clickable { onRemove() },
            tint = Blue
        )
    }
}
