package com.glassfiles.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CompareArrows
import androidx.compose.material.icons.rounded.Description
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.glassfiles.data.Strings
import com.glassfiles.ui.theme.AiModuleTheme
import com.glassfiles.data.github.GHCommit
import com.glassfiles.data.github.GHCompareResult
import com.glassfiles.data.github.GHDiffFile
import com.glassfiles.data.github.GitHubManager
import com.glassfiles.ui.components.AiModuleAlertDialog
import com.glassfiles.ui.components.AiModuleIcon as Icon
import com.glassfiles.ui.components.AiModulePillButton
import com.glassfiles.ui.components.AiModuleText as Text
import com.glassfiles.ui.components.AiModuleTextAction
import com.glassfiles.ui.components.AiModuleTextField
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
internal fun CompareCommitsScreen(
    repoOwner: String,
    repoName: String,
    initialBase: String = "",
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var baseBranch by remember { mutableStateOf(initialBase) }
    var headBranch by remember { mutableStateOf("") }
    var branches by remember { mutableStateOf<List<String>>(emptyList()) }
    var compareResult by remember { mutableStateOf<GHCompareResult?>(null) }
    var loading by remember { mutableStateOf(false) }
    var showDiff by remember { mutableStateOf(false) }
    var showCreatePr by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        branches = GitHubManager.getBranches(context, repoOwner, repoName)
    }

    LaunchedEffect(branches, initialBase) {
        if (branches.isEmpty()) return@LaunchedEffect
        if (baseBranch.isBlank()) baseBranch = initialBase.takeIf { it in branches } ?: branches.first()
        if (headBranch.isBlank()) headBranch = branches.firstOrNull { it != baseBranch }.orEmpty()
    }

    fun handleCompareBack() {
        when {
            showCreatePr -> showCreatePr = false
            showDiff -> showDiff = false
            else -> onBack()
        }
    }

    val result = compareResult
    if (showDiff && result != null) {
        DiffViewerScreen(
            title = "$headBranch into $baseBranch",
            subtitle = "${result.files.size} files changed",
            files = result.files,
            totalAdditions = result.files.sumOf { it.additions },
            totalDeletions = result.files.sumOf { it.deletions },
            onBack = ::handleCompareBack
        )
        return
    }

    GitHubScreenFrame(
        title = "> compare",
        subtitle = "$repoOwner/$repoName",
        onBack = ::handleCompareBack,
        trailing = {
            GitHubTopBarAction(
                glyph = GhGlyphs.COMPARE,
                onClick = {
                    val oldBase = baseBranch
                    baseBranch = headBranch
                    headBranch = oldBase
                    compareResult = null
                },
                enabled = baseBranch.isNotBlank() && headBranch.isNotBlank(),
                tint = AiModuleTheme.colors.accent,
                contentDescription = "swap branches",
            )
        },
    ) {

        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CompareSelectorCard(
                branches = branches,
                baseBranch = baseBranch,
                headBranch = headBranch,
                loading = loading,
                onBaseChange = {
                    baseBranch = it
                    compareResult = null
                    if (headBranch == it) headBranch = branches.firstOrNull { branch -> branch != it }.orEmpty()
                },
                onHeadChange = {
                    headBranch = it
                    compareResult = null
                },
                onCompare = {
                    if (baseBranch.isNotBlank() && headBranch.isNotBlank() && baseBranch != headBranch) {
                        loading = true
                        scope.launch {
                            compareResult = GitHubManager.compareCommits(context, repoOwner, repoName, baseBranch, headBranch)
                            loading = false
                            if (compareResult == null) Toast.makeText(context, Strings.error, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )

            if (result == null) {
                CompareEmptyState()
            } else {
                CompareResultPanel(
                    result = result,
                    baseBranch = baseBranch,
                    headBranch = headBranch,
                    onOpenDiff = { showDiff = true },
                    onCreatePr = { showCreatePr = true },
                    onOpenWeb = {
                        if (result.htmlUrl.isNotBlank()) {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(result.htmlUrl)))
                            } catch (_: Exception) {
                                Toast.makeText(context, Strings.error, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
            }
        }
    }

    if (showCreatePr && result != null) {
        CreateComparePRDialog(
            repoOwner = repoOwner,
            repoName = repoName,
            baseBranch = baseBranch,
            headBranch = headBranch,
            result = result,
            onDismiss = { showCreatePr = false },
            onCreated = {
                showCreatePr = false
                Toast.makeText(context, Strings.done, Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
private fun CompareSelectorCard(
    branches: List<String>,
    baseBranch: String,
    headBranch: String,
    loading: Boolean,
    onBaseChange: (String) -> Unit,
    onHeadChange: (String) -> Unit,
    onCompare: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(AiModuleTheme.colors.surface).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Compare branches", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = AiModuleTheme.colors.textPrimary)
        BranchSelectorDropdown(branches, baseBranch, onBaseChange, "Base")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Icon(Icons.Rounded.ArrowForward, null, Modifier.size(18.dp), tint = AiModuleTheme.colors.textMuted)
        }
        BranchSelectorDropdown(branches, headBranch, onHeadChange, "Compare")
        AiModulePillButton(
            label = if (loading) "comparing" else "compare",
            onClick = onCompare,
            enabled = branches.isNotEmpty() && baseBranch.isNotBlank() && headBranch.isNotBlank() && baseBranch != headBranch && !loading,
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = Icons.Rounded.CompareArrows,
        )
    }
}

@Composable
private fun CompareEmptyState() {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(AiModuleTheme.colors.surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("Choose two branches", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = AiModuleTheme.colors.textPrimary)
        Text("Use compare before creating a PR to see commits and changed files.", fontSize = 12.sp, color = AiModuleTheme.colors.textSecondary)
    }
}

@Composable
private fun CompareResultPanel(
    result: GHCompareResult,
    baseBranch: String,
    headBranch: String,
    onOpenDiff: () -> Unit,
    onCreatePr: () -> Unit,
    onOpenWeb: () -> Unit
) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(AiModuleTheme.colors.surface).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CompareMetric("${result.aheadBy}", "ahead", Color(0xFF34C759))
                CompareMetric("${result.behindBy}", "behind", Color(0xFFFF3B30))
                CompareMetric("${result.totalCommits}", "commits", AiModuleTheme.colors.accent)
                CompareMetric("${result.files.size}", "files", AiModuleTheme.colors.textSecondary)
            }
            Text(
                compareStatusText(result.status, baseBranch, headBranch),
                fontSize = 12.sp,
                color = AiModuleTheme.colors.textSecondary,
                lineHeight = 16.sp
            )
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GitHubTerminalButton("view diff", onClick = onOpenDiff, enabled = result.files.isNotEmpty(), color = AiModuleTheme.colors.accent)
                GitHubTerminalButton("create pr", onClick = onCreatePr, enabled = result.aheadBy > 0, color = Color(0xFF34C759))
                GitHubTerminalButton("github", onClick = onOpenWeb, enabled = result.htmlUrl.isNotBlank(), color = AiModuleTheme.colors.textSecondary)
            }
        }

        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            if (result.commits.isNotEmpty()) {
                item {
                    Text("Commits", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AiModuleTheme.colors.textPrimary)
                }
                items(result.commits.take(8)) { commit ->
                    CompareCommitRow(commit)
                }
                if (result.commits.size > 8) {
                    item {
                        Text("+${result.commits.size - 8} more commits", fontSize = 11.sp, color = AiModuleTheme.colors.textMuted, modifier = Modifier.padding(horizontal = 4.dp))
                    }
                }
            }

            item {
                Text("Changed files", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AiModuleTheme.colors.textPrimary)
            }
            if (result.files.isEmpty()) {
                item {
                    Text("No file changes", fontSize = 13.sp, color = AiModuleTheme.colors.textMuted, modifier = Modifier.padding(4.dp))
                }
            } else {
                items(result.files) { file ->
                    CompareFileCard(file)
                }
            }
        }
    }
}

@Composable
private fun CompareMetric(value: String, label: String, color: Color) {
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.1f)).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(value, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(label, color = color, fontSize = 11.sp)
    }
}

@Composable
private fun CompareCommitRow(commit: GHCommit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(AiModuleTheme.colors.surface).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(commit.message.lineSequence().firstOrNull().orEmpty(), fontSize = 13.sp, color = AiModuleTheme.colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(commit.sha.take(7), fontSize = 11.sp, color = AiModuleTheme.colors.accent, fontWeight = FontWeight.Medium)
            if (commit.author.isNotBlank()) Text(commit.author, fontSize = 11.sp, color = AiModuleTheme.colors.textSecondary)
        }
    }
}

@Composable
private fun CompareFileCard(file: GHDiffFile) {
    val statusColor = diffStatusColor(file.status)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(AiModuleTheme.colors.surface).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(statusColor))
            Text(file.filename, fontSize = 13.sp, color = AiModuleTheme.colors.textPrimary, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(file.status.replaceFirstChar { it.uppercase() }, fontSize = 11.sp, color = statusColor, fontWeight = FontWeight.Medium)
            Text("+${file.additions}", fontSize = 11.sp, color = Color(0xFF34C759))
            Text("-${file.deletions}", fontSize = 11.sp, color = Color(0xFFFF3B30))
            if (file.patch.isBlank()) {
                Icon(Icons.Rounded.Description, null, Modifier.size(12.dp), tint = AiModuleTheme.colors.textMuted)
                Text("No patch preview", fontSize = 11.sp, color = AiModuleTheme.colors.textMuted)
            }
        }
    }
}

@Composable
private fun CreateComparePRDialog(
    repoOwner: String,
    repoName: String,
    baseBranch: String,
    headBranch: String,
    result: GHCompareResult,
    onDismiss: () -> Unit,
    onCreated: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("Merge $headBranch into $baseBranch") }
    var body by remember {
        mutableStateOf(
            buildString {
                appendLine("Compare: $headBranch into $baseBranch")
                appendLine()
                appendLine("${result.totalCommits} commits, ${result.files.size} files changed")
                result.commits.take(10).forEach { appendLine("- ${it.message.lineSequence().firstOrNull().orEmpty()} (${it.sha.take(7)})") }
            }.trim()
        )
    }
    var creating by remember { mutableStateOf(false) }

    AiModuleAlertDialog(
        onDismissRequest = { if (!creating) onDismiss() },
        title = "Create pull request",
        content = {
            Column(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("$headBranch -> $baseBranch", fontSize = 12.sp, color = AiModuleTheme.colors.textSecondary)
                AiModuleTextField(title, { title = it }, label = "Title", singleLine = true, modifier = Modifier.fillMaxWidth())
                AiModuleTextField(body, { body = it }, label = "Description", minLines = 6, maxLines = 10, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            AiModuleTextAction(
                label = if (creating) "creating" else "create",
                enabled = !creating && title.isNotBlank(),
                onClick = {
                    creating = true
                    scope.launch {
                        val ok = GitHubManager.createPullRequest(context, repoOwner, repoName, title, body, headBranch, baseBranch)
                        creating = false
                        if (ok) onCreated() else Toast.makeText(context, Strings.error, Toast.LENGTH_SHORT).show()
                    }
                },
            )
        },
        dismissButton = {
            AiModuleTextAction(label = Strings.cancel.lowercase(), onClick = onDismiss, enabled = !creating, tint = AiModuleTheme.colors.textSecondary)
        }
    )
}

@Composable
private fun BranchSelectorDropdown(
    branches: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    placeholder: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(placeholder, fontSize = 11.sp, color = AiModuleTheme.colors.textSecondary)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            branches.forEach { branch ->
                val active = branch == selected
                Box(
                    Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .background(if (active) AiModuleTheme.colors.accent.copy(alpha = 0.15f) else AiModuleTheme.colors.background)
                        .clickable { onSelect(branch) }
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                ) {
                    Text(
                        branch,
                        fontSize = 12.sp,
                        color = if (active) AiModuleTheme.colors.accent else AiModuleTheme.colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun diffStatusColor(status: String): Color = when (status.lowercase(Locale.US)) {
    "added" -> Color(0xFF34C759)
    "removed" -> Color(0xFFFF3B30)
    "modified" -> Color(0xFFFF9500)
    "renamed" -> Color(0xFF5856D6)
    else -> AiModuleTheme.colors.textSecondary
}

private fun compareStatusText(status: String, baseBranch: String, headBranch: String): String = when (status) {
    "identical" -> "$baseBranch and $headBranch are identical."
    "ahead" -> "$headBranch has commits that can be merged into $baseBranch."
    "behind" -> "$headBranch is behind $baseBranch."
    "diverged" -> "$baseBranch and $headBranch have diverged."
    else -> status.ifBlank { "Comparison loaded." }
}
