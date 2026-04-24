package com.glassfiles.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.glassfiles.data.Strings
import com.glassfiles.data.github.*
import com.glassfiles.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File

// Compact mode — propagates through all sub-screens automatically

internal enum class RepoTab { FILES, COMMITS, ISSUES, PULLS, RELEASES, ACTIONS, BUILDS, README, CODE_SEARCH }

@Composable
internal fun RepoDetailScreen(repo: GHRepo, onBack: () -> Unit, onMinimize: () -> Unit = {}, onClose: (() -> Unit)? = null) {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(RepoTab.FILES) }; var contents by remember { mutableStateOf<List<GHContent>>(emptyList()) }
    var currentPath by remember { mutableStateOf("") }; var commits by remember { mutableStateOf<List<GHCommit>>(emptyList()) }
    var issues by remember { mutableStateOf<List<GHIssue>>(emptyList()) }; var pulls by remember { mutableStateOf<List<GHPullRequest>>(emptyList()) }
    var releases by remember { mutableStateOf<List<GHRelease>>(emptyList()) }; var readme by remember { mutableStateOf<String?>(null) }
    var workflowRuns by remember { mutableStateOf<List<GHWorkflowRun>>(emptyList()) }; var selectedRunId by remember { mutableStateOf<Long?>(null) }
    var workflows by remember { mutableStateOf<List<GHWorkflow>>(emptyList()) }; var showDispatch by remember { mutableStateOf(false) }
    var branches by remember { mutableStateOf<List<String>>(emptyList()) }; var loading by remember { mutableStateOf(true) }
    var fileContent by remember { mutableStateOf<String?>(null) }; var openedFile by remember { mutableStateOf<GHContent?>(null) }; var editingFile by remember { mutableStateOf<GHContent?>(null) }
    var repoQuery by remember { mutableStateOf("") }
    var cloneProgress by remember { mutableStateOf<String?>(null) }; var isStarred by remember { mutableStateOf(false) }
    var isWatching by remember { mutableStateOf(false) }
    var selectedBranch by remember { mutableStateOf(repo.defaultBranch) }
    var showUpload by remember { mutableStateOf(false) }; var showCreateFile by remember { mutableStateOf(false) }
    var showCreateBranch by remember { mutableStateOf(false) }; var showCreateIssue by remember { mutableStateOf(false) }
    var showCreatePR by remember { mutableStateOf(false) }; var selectedIssue by remember { mutableStateOf<GHIssue?>(null) }
    var selectedCommitSha by remember { mutableStateOf<String?>(null) }; var deleteTarget by remember { mutableStateOf<GHContent?>(null) }
    var showBranchPicker by remember { mutableStateOf(false) }
    var selectedPRNumber by remember { mutableStateOf<Int?>(null) }
    var selectedPullNumber by remember { mutableStateOf<Int?>(null) }
    var showRepoSettings by remember { mutableStateOf(false) }
    var showBranchProtection by remember { mutableStateOf(false) }
    var showCollaborators by remember { mutableStateOf(false) }
    var showCompare by remember { mutableStateOf(false) }
    var showWebhooks by remember { mutableStateOf(false) }
    var showDiscussions by remember { mutableStateOf(false) }
    var showRulesets by remember { mutableStateOf(false) }
    var showSecurity by remember { mutableStateOf(false) }
    var languages by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }; var contributors by remember { mutableStateOf<List<GHContributor>>(emptyList()) }
    // Pagination
    var commitsPage by remember { mutableIntStateOf(1) }; var commitsHasMore by remember { mutableStateOf(true) }
    var issuesPage by remember { mutableIntStateOf(1) }; var issuesHasMore by remember { mutableStateOf(true) }
    var pullsPage by remember { mutableIntStateOf(1) }; var pullsHasMore by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) { isStarred = GitHubManager.isStarred(context, repo.owner, repo.name); isWatching = GitHubManager.isWatching(context, repo.owner, repo.name); branches = GitHubManager.getBranches(context, repo.owner, repo.name) }
    LaunchedEffect(selectedTab, currentPath, selectedBranch) { loading = true; when (selectedTab) {
        RepoTab.FILES -> contents = GitHubManager.getRepoContents(context, repo.owner, repo.name, currentPath, selectedBranch)
        RepoTab.COMMITS -> { commitsPage = 1; val r = GitHubManager.getCommits(context, repo.owner, repo.name, 1); commits = r; commitsHasMore = r.size >= 30 }
        RepoTab.ISSUES -> { issuesPage = 1; val r = GitHubManager.getIssues(context, repo.owner, repo.name, page = 1); issues = r; issuesHasMore = r.size >= 30 }
        RepoTab.PULLS -> { pullsPage = 1; val r = GitHubManager.getPullRequests(context, repo.owner, repo.name, page = 1); pulls = r; pullsHasMore = r.size >= 30 }
        RepoTab.RELEASES -> releases = GitHubManager.getReleases(context, repo.owner, repo.name)
        RepoTab.ACTIONS, RepoTab.BUILDS -> { workflowRuns = GitHubManager.getWorkflowRuns(context, repo.owner, repo.name); workflows = GitHubManager.getWorkflows(context, repo.owner, repo.name) }
        RepoTab.README -> { readme = GitHubManager.getReadme(context, repo.owner, repo.name); languages = GitHubManager.getLanguages(context, repo.owner, repo.name); contributors = GitHubManager.getContributors(context, repo.owner, repo.name) }
        RepoTab.CODE_SEARCH -> { /* searches on demand */ }
    }; loading = false }

    if (selectedIssue != null) { IssueDetailScreen(repo, selectedIssue!!.number) { selectedIssue = null }; return }
    if (selectedCommitSha != null) { 
        CommitDiffScreen(repo.owner, repo.name, selectedCommitSha!!) { selectedCommitSha = null }; 
        return 
    }
    if (selectedPRNumber != null) {
        PullRequestDiffScreen(
            repoOwner = repo.owner,
            repoName = repo.name,
            pullNumber = selectedPRNumber!!,
            onBack = { selectedPRNumber = null }
        )
        return
    }
    if (selectedPullNumber != null) {
        PullRequestDetailScreen(
            repo = repo,
            pullNumber = selectedPullNumber!!,
            onBack = { selectedPullNumber = null },
            onOpenFiles = { selectedPRNumber = it }
        )
        return
    }
    if (selectedRunId != null) { WorkflowRunDetailScreen(repo, selectedRunId!!) { selectedRunId = null }; return }
    if (showRepoSettings) { RepoSettingsScreen(repoOwner = repo.owner, repoName = repo.name, onBack = { showRepoSettings = false }, onBranchProtection = { showRepoSettings = false; showBranchProtection = true }, onCollaborators = { showRepoSettings = false; showCollaborators = true }, onWebhooks = { showRepoSettings = false; showWebhooks = true }, onDiscussions = { showRepoSettings = false; showDiscussions = true }, onRulesets = { showRepoSettings = false; showRulesets = true }, onSecurity = { showRepoSettings = false; showSecurity = true }) ; return }
    if (showBranchProtection) { BranchProtectionScreen(repoOwner = repo.owner, repoName = repo.name, branches = branches, onBack = { showBranchProtection = false }) ; return }
    if (showCollaborators) { CollaboratorsScreen(repoOwner = repo.owner, repoName = repo.name) { showCollaborators = false }; return }
    if (showCompare) { CompareCommitsScreen(repoOwner = repo.owner, repoName = repo.name, initialBase = selectedBranch) { showCompare = false }; return }
    if (showWebhooks) { WebhooksScreen(repoOwner = repo.owner, repoName = repo.name) { showWebhooks = false }; return }
    if (showDiscussions) { DiscussionsScreen(repoOwner = repo.owner, repoName = repo.name) { showDiscussions = false }; return }
    if (showRulesets) { RulesetsScreen(repoOwner = repo.owner, repoName = repo.name) { showRulesets = false }; return }
    if (showSecurity) { SecurityScreen(repoOwner = repo.owner, repoName = repo.name) { showSecurity = false }; return }
    
    // File editor screen
    val safeEditingFile = editingFile
    val safeFileContent = fileContent
    if (safeEditingFile != null && safeFileContent != null) {
        com.glassfiles.ui.screens.CodeEditorScreen(
            repoOwner = repo.owner,
            repoName = repo.name,
            file = safeEditingFile,
            branch = selectedBranch,
            initialContent = safeFileContent,
            onBack = { 
                editingFile = null
                fileContent = null
                scope.launch { contents = GitHubManager.getRepoContents(context, repo.owner, repo.name, currentPath, selectedBranch) }
            }
        )
        return
    }
    if (safeEditingFile != null) {
        LaunchedEffect(safeEditingFile.path, selectedBranch) {
            fileContent = GitHubManager.getFileContent(context, repo.owner, repo.name, safeEditingFile.path, selectedBranch)
        }
        Column(Modifier.fillMaxSize().background(SurfaceLight)) {
            GHTopBar(safeEditingFile.name, subtitle = "Loading editor", onBack = {
                editingFile = null
                fileContent = null
            })
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Blue, modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
            }
        }
        return
    }
    
    // Releases screen
    if (selectedTab == RepoTab.RELEASES) {
        com.glassfiles.ui.screens.ReleasesScreen(
            repoOwner = repo.owner,
            repoName = repo.name,
            onBack = { selectedTab = RepoTab.FILES },
            onReleaseClick = { /* optional */ }
        )
        return
    }
    
    val safeOpenedFile = openedFile
    if (safeFileContent != null && safeOpenedFile != null) {
        val ext = safeOpenedFile.name.substringAfterLast(".", "").lowercase()
        val isImage = ext in listOf("png", "jpg", "jpeg", "gif", "webp", "svg", "ico")
        val isMd = ext in listOf("md", "markdown")
        val cachedLines = remember(safeFileContent) { safeFileContent.lines() }
        Column(Modifier.fillMaxSize().background(Color(0xFF0D1117))) {
            GHTopBar(safeOpenedFile.name, onBack = {
                fileContent = null
                openedFile = null
            }) {
                IconButton(onClick = {
                    val clip = android.content.ClipData.newPlainText("code", safeFileContent)
                    (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(clip)
                    Toast.makeText(context, Strings.done, Toast.LENGTH_SHORT).show()
                }) { Icon(Icons.Rounded.ContentCopy, null, Modifier.size(20.dp), tint = Blue) }
                IconButton(onClick = {
                    scope.launch {
                        val dest = File(
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                            "GlassFiles_Git/${safeOpenedFile.name}"
                        )
                        val ok = GitHubManager.downloadFile(
                            context, repo.owner, repo.name, safeOpenedFile.path, dest, selectedBranch
                        )
                        Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                    }
                }) { Icon(Icons.Rounded.Download, null, Modifier.size(20.dp), tint = Blue) }
                IconButton(onClick = {
                    editingFile = safeOpenedFile
                }) { Icon(Icons.Rounded.Edit, null, Modifier.size(20.dp), tint = Blue) }
            }
            Row(Modifier.fillMaxWidth().background(Color(0xFF161B22)).padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${cachedLines.size} lines", fontSize = 11.sp, color = Color(0xFF8B949E))
                Text("${safeFileContent.length} chars", fontSize = 11.sp, color = Color(0xFF8B949E))
                Text(ext.uppercase(), fontSize = 11.sp, color = Color(0xFF58A6FF), fontWeight = FontWeight.SemiBold)
            }
            if (isImage) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Image preview not available\nUse Download to save", fontSize = 14.sp, color = Color(0xFF8B949E), textAlign = TextAlign.Center)
                }
            } else if (isMd) {
                LazyColumn(Modifier.fillMaxSize().padding(12.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(cachedLines.size) { idx -> MarkdownLine(cachedLines[idx]) }
                }
            } else {
                LazyColumn(Modifier.fillMaxSize().padding(start = 4.dp, end = 4.dp, top = 4.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(cachedLines.size) { idx ->
                        Row(Modifier.fillMaxWidth()) {
                            Text("${idx + 1}".padStart(4), fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF484F58), modifier = Modifier.padding(end = 10.dp))
                            Text(highlightLine(cachedLines[idx], ext), fontSize = 12.sp, fontFamily = FontFamily.Monospace, lineHeight = 18.sp)
                        }
                    }
                }
            }
        }; return
    }
    val filteredContents = remember(contents, repoQuery) {
        if (repoQuery.isBlank()) contents else contents.filter { it.name.contains(repoQuery, ignoreCase = true) || it.path.contains(repoQuery, ignoreCase = true) }
    }
    val filteredCommits = remember(commits, repoQuery) {
        if (repoQuery.isBlank()) commits else commits.filter {
            it.message.contains(repoQuery, ignoreCase = true) || it.author.contains(repoQuery, ignoreCase = true) || it.sha.contains(repoQuery, ignoreCase = true)
        }
    }
    val filteredIssues = remember(issues, repoQuery) {
        if (repoQuery.isBlank()) issues else issues.filter {
            it.title.contains(repoQuery, ignoreCase = true) || it.author.contains(repoQuery, ignoreCase = true) || it.number.toString().contains(repoQuery)
        }
    }
    val filteredPulls = remember(pulls, repoQuery) {
        if (repoQuery.isBlank()) pulls else pulls.filter {
            it.title.contains(repoQuery, ignoreCase = true) || it.author.contains(repoQuery, ignoreCase = true) || it.number.toString().contains(repoQuery)
        }
    }


    Column(Modifier.fillMaxSize().background(SurfaceLight)) {
        GHTopBar(repo.name, subtitle = if (currentPath.isNotBlank()) currentPath else repo.owner, onBack = { if (currentPath.isNotBlank() && selectedTab == RepoTab.FILES) currentPath = currentPath.substringBeforeLast("/", "") else onBack() }, onMinimize = onMinimize, onClose = onClose) {
            val ic = if (LocalGHCompact.current) 16.dp else 20.dp
            IconButton(onClick = { scope.launch { if (isStarred) GitHubManager.unstarRepo(context, repo.owner, repo.name) else GitHubManager.starRepo(context, repo.owner, repo.name); isStarred = !isStarred } }, modifier = if (LocalGHCompact.current) Modifier.size(32.dp) else Modifier) { Icon(if (isStarred) Icons.Rounded.Star else Icons.Rounded.StarBorder, null, Modifier.size(ic), tint = Color(0xFFFFCC00)) }
            IconButton(onClick = { scope.launch { if (isWatching) GitHubManager.unwatchRepo(context, repo.owner, repo.name) else GitHubManager.watchRepo(context, repo.owner, repo.name); isWatching = !isWatching } }, modifier = if (LocalGHCompact.current) Modifier.size(32.dp) else Modifier) { Icon(if (isWatching) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff, null, Modifier.size(ic), tint = if (isWatching) Blue else TextSecondary) }
            IconButton(onClick = { showRepoSettings = true }, modifier = if (LocalGHCompact.current) Modifier.size(32.dp) else Modifier) { Icon(Icons.Rounded.Settings, null, Modifier.size(ic), tint = TextSecondary) }
            IconButton(onClick = { showCompare = true }, modifier = if (LocalGHCompact.current) Modifier.size(32.dp) else Modifier) { Icon(Icons.Rounded.CompareArrows, null, Modifier.size(ic), tint = Blue) }
            IconButton(onClick = { scope.launch { val ok = GitHubManager.forkRepo(context, repo.owner, repo.name); Toast.makeText(context, if (ok) Strings.ghForked else Strings.error, Toast.LENGTH_SHORT).show() } }, modifier = if (LocalGHCompact.current) Modifier.size(32.dp) else Modifier) { Icon(Icons.Rounded.CallSplit, null, Modifier.size(ic), tint = Blue) }
            IconButton(onClick = { cloneProgress = "Starting..."; scope.launch { val dest = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "GlassFiles_Git"); val ok = GitHubManager.cloneRepo(context, repo.owner, repo.name, dest) { cloneProgress = it }; Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show(); cloneProgress = null } }, modifier = if (LocalGHCompact.current) Modifier.size(32.dp) else Modifier) { Icon(Icons.Rounded.Download, null, Modifier.size(ic), tint = Blue) }
        }
        if (cloneProgress != null) Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(RoundedCornerShape(8.dp)).background(Blue.copy(0.1f)).padding(horizontal = 12.dp, vertical = 8.dp)) { Text(cloneProgress!!, fontSize = 13.sp, color = Blue, fontWeight = FontWeight.Medium) }
        // Branch + actions
        val cmp = LocalGHCompact.current
        Row(Modifier.fillMaxWidth().background(SurfaceWhite).padding(horizontal = if (cmp) 6.dp else 12.dp, vertical = if (cmp) 3.dp else 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (cmp) 4.dp else 6.dp)) {
            Box(Modifier.clip(RoundedCornerShape(6.dp)).background(Blue.copy(0.08f)).clickable { showBranchPicker = true }.padding(horizontal = if (cmp) 6.dp else 10.dp, vertical = if (cmp) 3.dp else 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) { Icon(Icons.Rounded.AccountTree, null, Modifier.size(if (cmp) 12.dp else 14.dp), tint = Blue); Text(selectedBranch, fontSize = if (cmp) 10.sp else 12.sp, color = Blue, fontWeight = FontWeight.Medium); Icon(Icons.Rounded.ArrowDropDown, null, Modifier.size(if (cmp) 12.dp else 14.dp), tint = Blue) }
            }
            Spacer(Modifier.weight(1f))
            when (selectedTab) { RepoTab.FILES -> { SmallAction(Icons.Rounded.NoteAdd, Strings.ghCreateFile) { showCreateFile = true }; SmallAction(Icons.Rounded.Upload, Strings.ghUpload) { showUpload = true } }; RepoTab.ISSUES -> SmallAction(Icons.Rounded.Add, Strings.ghNewIssue) { showCreateIssue = true }; RepoTab.PULLS -> SmallAction(Icons.Rounded.Add, Strings.ghNewPR) { showCreatePR = true }; RepoTab.ACTIONS -> SmallAction(Icons.Rounded.PlayArrow, Strings.ghRunWorkflow) { showDispatch = true }; RepoTab.BUILDS -> SmallAction(Icons.Rounded.Build, "Builder") { selectedTab = RepoTab.BUILDS }; else -> {} }
        }
        // Tabs
        Row(Modifier.fillMaxWidth().background(SurfaceWhite).horizontalScroll(rememberScrollState()).padding(horizontal = if (cmp) 6.dp else 12.dp, vertical = if (cmp) 3.dp else 6.dp), horizontalArrangement = Arrangement.spacedBy(if (cmp) 4.dp else 6.dp)) {
            RepoTab.entries.forEach { tab -> val sel = selectedTab == tab; val label = when (tab) { RepoTab.FILES -> Strings.ghGistFiles; RepoTab.COMMITS -> Strings.ghCommits; RepoTab.ISSUES -> "Issues"; RepoTab.PULLS -> Strings.ghPulls; RepoTab.RELEASES -> Strings.ghReleases; RepoTab.ACTIONS -> Strings.ghActions; RepoTab.BUILDS -> "Сборки"; RepoTab.README -> Strings.ghReadme; RepoTab.CODE_SEARCH -> Strings.ghSearchCode }
                Box(Modifier.clip(RoundedCornerShape(6.dp)).background(if (sel) Blue.copy(0.12f) else Color.Transparent).border(1.dp, if (sel) Blue.copy(0.3f) else SeparatorColor, RoundedCornerShape(6.dp)).clickable { selectedTab = tab; repoQuery = "" }.padding(horizontal = if (cmp) 6.dp else 10.dp, vertical = if (cmp) 3.dp else 6.dp)) { Text(label, fontSize = if (cmp) 10.sp else 12.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal, color = if (sel) Blue else TextSecondary) }
            }
        }
        if (selectedTab in listOf(RepoTab.FILES, RepoTab.COMMITS, RepoTab.ISSUES, RepoTab.PULLS)) {
            Row(
                Modifier.fillMaxWidth().background(SurfaceWhite).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(SurfaceLight).padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    if (repoQuery.isEmpty()) {
                        Text(
                            when (selectedTab) {
                                RepoTab.FILES -> "Filter files"
                                RepoTab.COMMITS -> "Filter commits"
                                RepoTab.ISSUES -> "Filter issues"
                                RepoTab.PULLS -> "Filter pull requests"
                                else -> ""
                            },
                            color = TextTertiary,
                            fontSize = 13.sp
                        )
                    }
                    BasicTextField(
                        value = repoQuery,
                        onValueChange = { repoQuery = it },
                        textStyle = androidx.compose.ui.text.TextStyle(color = TextPrimary, fontSize = 13.sp),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (repoQuery.isNotBlank()) {
                    Box(
                        Modifier.clip(RoundedCornerShape(8.dp)).background(SurfaceLight).clickable { repoQuery = "" }.padding(horizontal = 10.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Close, null, Modifier.size(16.dp), tint = TextSecondary)
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(SeparatorColor))
        if (loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Blue, modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp) }
        else when (selectedTab) {
            RepoTab.FILES -> FilesTab(filteredContents, onDirClick = { currentPath = it.path }, onFileClick = { scope.launch { openedFile = it; fileContent = GitHubManager.getFileContent(context, repo.owner, repo.name, it.path, selectedBranch) } }, onEdit = { openedFile = null; fileContent = null; editingFile = it }, onDelete = { deleteTarget = it }, onDownload = { scope.launch { val dest = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "GlassFiles_Git/${it.name}"); val ok = GitHubManager.downloadFile(context, repo.owner, repo.name, it.path, dest, selectedBranch); Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show() } })
            RepoTab.COMMITS -> CommitsTab(filteredCommits, commitsHasMore, { scope.launch { commitsPage++; val r = GitHubManager.getCommits(context, repo.owner, repo.name, commitsPage); if (r.size < 30) commitsHasMore = false; commits = commits + r } }) { selectedCommitSha = it.sha }
            RepoTab.ISSUES -> IssuesTab(filteredIssues, issuesHasMore, { scope.launch { issuesPage++; val r = GitHubManager.getIssues(context, repo.owner, repo.name, page = issuesPage); if (r.size < 30) issuesHasMore = false; issues = issues + r } }) { selectedIssue = it }
            RepoTab.PULLS -> PullsTab(filteredPulls, repo, { scope.launch { pulls = GitHubManager.getPullRequests(context, repo.owner, repo.name) } }, onOpenDetail = { selectedPullNumber = it.number }) { prNumber -> selectedPRNumber = prNumber }
            RepoTab.RELEASES -> ReleasesTab(releases, repo)
            RepoTab.ACTIONS -> ActionsTab(workflowRuns, repo) { selectedRunId = it.id }
            RepoTab.BUILDS -> BuildsScreen(
                repo = repo,
                branches = branches,
                workflows = workflows,
                selectedBranch = selectedBranch
            ) { runId ->
                selectedTab = RepoTab.ACTIONS
                if (runId != null) selectedRunId = runId
                scope.launch {
                    workflowRuns = GitHubManager.getWorkflowRuns(context, repo.owner, repo.name)
                    workflows = GitHubManager.getWorkflows(context, repo.owner, repo.name)
                }
            }
            RepoTab.README -> ReadmeTab(readme, languages, contributors)
            RepoTab.CODE_SEARCH -> CodeSearchTab(repo)
        }
    }
    if (showUpload) UploadDialog(repo, currentPath, selectedBranch, { showUpload = false }) { showUpload = false; scope.launch { contents = GitHubManager.getRepoContents(context, repo.owner, repo.name, currentPath, selectedBranch) } }
    if (showCreateFile) CreateFileDialog(repo, currentPath, selectedBranch, { showCreateFile = false }) { showCreateFile = false; scope.launch { contents = GitHubManager.getRepoContents(context, repo.owner, repo.name, currentPath, selectedBranch) } }
    if (showCreateBranch) CreateBranchDialog(repo, branches, { showCreateBranch = false }) { showCreateBranch = false; scope.launch { branches = GitHubManager.getBranches(context, repo.owner, repo.name) } }
    if (showCreateIssue) CreateIssueDialog(repo, { showCreateIssue = false }) { showCreateIssue = false; scope.launch { issues = GitHubManager.getIssues(context, repo.owner, repo.name) } }
    if (showCreatePR) CreatePRDialog(repo, branches, { showCreatePR = false }) { showCreatePR = false; scope.launch { pulls = GitHubManager.getPullRequests(context, repo.owner, repo.name) } }
    if (deleteTarget != null) DeleteFileDialog(repo, deleteTarget!!, selectedBranch, { deleteTarget = null }) { deleteTarget = null; scope.launch { contents = GitHubManager.getRepoContents(context, repo.owner, repo.name, currentPath, selectedBranch) } }
    if (showBranchPicker) BranchPickerDialog(branches, selectedBranch, { selectedBranch = it; showBranchPicker = false }, { showBranchPicker = false }) { showBranchPicker = false; showCreateBranch = true }
    if (showDispatch && workflows.isNotEmpty()) DispatchWorkflowDialog(repo, workflows, branches, { showDispatch = false }) { showDispatch = false; scope.launch { workflowRuns = GitHubManager.getWorkflowRuns(context, repo.owner, repo.name) } }
}

@Composable private fun SmallAction(icon: ImageVector, label: String, onClick: () -> Unit) { val c = LocalGHCompact.current; Row(Modifier.clip(RoundedCornerShape(if (c) 6.dp else 8.dp)).background(SurfaceWhite).border(0.5.dp, SeparatorColor, RoundedCornerShape(if (c) 6.dp else 8.dp)).clickable(onClick = onClick).padding(horizontal = if (c) 5.dp else 8.dp, vertical = if (c) 3.dp else 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (c) 2.dp else 4.dp)) { Icon(icon, null, Modifier.size(if (c) 11.dp else 14.dp), tint = Blue); Text(label, fontSize = if (c) 9.sp else 11.sp, color = Blue) } }

@Composable
internal fun FilesTab(contents: List<GHContent>, onDirClick: (GHContent) -> Unit, onFileClick: (GHContent) -> Unit, onEdit: (GHContent) -> Unit, onDelete: (GHContent) -> Unit, onDownload: (GHContent) -> Unit) {
    var expanded by remember { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) { items(contents) { item -> Column {
        Row(Modifier.fillMaxWidth().clickable { if (item.type == "dir") onDirClick(item) else expanded = if (expanded == item.path) null else item.path }.padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (item.type == "dir") Icons.Rounded.Folder else Icons.Rounded.InsertDriveFile, null, Modifier.size(22.dp), tint = if (item.type == "dir") FolderBlue else TextSecondary)
            Text(item.name, fontSize = 14.sp, color = TextPrimary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (item.type != "dir" && item.size > 0) Text(ghFmtSize(item.size), fontSize = 11.sp, color = TextTertiary)
        }
        AnimatedVisibility(expanded == item.path && item.type != "dir") { Row(Modifier.fillMaxWidth().padding(start = 50.dp, end = 16.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(Icons.Rounded.Visibility, "View") { onFileClick(item) }; Chip(Icons.Rounded.Edit, Strings.ghEditFile) { onEdit(item) }; Chip(Icons.Rounded.Download, Strings.ghDownloadFile) { onDownload(item) }; Chip(Icons.Rounded.Delete, Strings.ghDeleteFile, Color(0xFFFF3B30)) { onDelete(item) }
        } }
        Box(Modifier.fillMaxWidth().padding(start = 50.dp).height(0.5.dp).background(SeparatorColor))
    } } }
}

@Composable internal fun Chip(icon: ImageVector, label: String, tint: Color = Blue, onClick: () -> Unit) { Row(Modifier.clip(RoundedCornerShape(6.dp)).background(tint.copy(0.08f)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) { Icon(icon, null, Modifier.size(12.dp), tint = tint); Text(label, fontSize = 10.sp, color = tint, fontWeight = FontWeight.Medium) } }

@Composable
internal fun CommitsTab(commits: List<GHCommit>, hasMore: Boolean, onLoadMore: () -> Unit, onClick: (GHCommit) -> Unit) { LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) { items(commits) { c ->
    Row(Modifier.fillMaxWidth().clickable { onClick(c) }.padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(Blue.copy(0.1f)), contentAlignment = Alignment.Center) { Text(c.sha.take(2), fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = Blue) }
        Column(Modifier.weight(1f)) { Text(c.message.lines().first(), fontSize = 14.sp, color = TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Text(c.author, fontSize = 11.sp, color = Blue); Text(c.sha, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = TextTertiary); Text(c.date.take(10), fontSize = 11.sp, color = TextTertiary) } }
        Icon(Icons.Rounded.ChevronRight, null, Modifier.size(16.dp), tint = TextTertiary)
    }; Box(Modifier.fillMaxWidth().padding(start = 58.dp).height(0.5.dp).background(SeparatorColor))
}; if (hasMore) item { Box(Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF21262D)).clickable { onLoadMore() }.padding(12.dp), contentAlignment = Alignment.Center) { Text("Load more", color = Blue, fontSize = 14.sp, fontWeight = FontWeight.Medium) } } } }

@Composable
internal fun IssuesTab(issues: List<GHIssue>, hasMore: Boolean, onLoadMore: () -> Unit, onClick: (GHIssue) -> Unit) { LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) { items(issues) { issue ->
    Row(Modifier.fillMaxWidth().clickable { onClick(issue) }.padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Icon(if (issue.isPR) Icons.Rounded.CallMerge else if (issue.state == "open") Icons.Rounded.RadioButtonUnchecked else Icons.Rounded.CheckCircle, null, Modifier.size(20.dp), tint = if (issue.state == "open") Color(0xFF34C759) else Color(0xFF8E8E93))
        Column(Modifier.weight(1f)) { Text(issue.title, fontSize = 14.sp, color = TextPrimary, maxLines = 2); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Text("#${issue.number}", fontSize = 11.sp, color = TextTertiary); Text(issue.author, fontSize = 11.sp, color = Blue); if (issue.comments > 0) Text("${issue.comments} comments", fontSize = 11.sp, color = TextTertiary) } }
        Icon(Icons.Rounded.ChevronRight, null, Modifier.size(16.dp), tint = TextTertiary)
    }; Box(Modifier.fillMaxWidth().padding(start = 46.dp).height(0.5.dp).background(SeparatorColor))
}; if (hasMore) item { Box(Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF21262D)).clickable { onLoadMore() }.padding(12.dp), contentAlignment = Alignment.Center) { Text("Load more", color = Blue, fontSize = 14.sp, fontWeight = FontWeight.Medium) } } } }

@Composable
internal fun PullsTab(
    pulls: List<GHPullRequest>,
    repo: GHRepo,
    onRefresh: () -> Unit,
    onOpenDetail: (GHPullRequest) -> Unit = {},
    onFilesClick: (Int) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var reviewTarget by remember { mutableStateOf<GHPullRequest?>(null) }
    var checkRunTarget by remember { mutableStateOf<GHPullRequest?>(null) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        items(pulls) { pr ->
            Column(Modifier.fillMaxWidth().clickable { onOpenDetail(pr) }.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                    Icon(
                        Icons.Rounded.CallMerge, null, Modifier.size(20.dp),
                        tint = when {
                            pr.merged -> Color(0xFF8957E5)
                            pr.state == "open" -> Color(0xFF34C759)
                            else -> Color(0xFF8E8E93)
                        }
                    )
                    Column(Modifier.weight(1f)) {
                        Text(pr.title, fontSize = 14.sp, color = TextPrimary, maxLines = 2)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("#${pr.number}", fontSize = 11.sp, color = TextTertiary)
                            Text("${pr.head} → ${pr.base}", fontSize = 11.sp, color = Blue)
                            Text(pr.author, fontSize = 11.sp, color = TextSecondary)
                            if (pr.draft) Text("Draft", fontSize = 11.sp, color = TextTertiary)
                            if (pr.reviewComments > 0) Text("${pr.reviewComments} review comments", fontSize = 11.sp, color = TextTertiary)
                        }
                        if (pr.body.isNotBlank()) {
                            Text(
                                pr.body.replace("\n", " ").take(140),
                                fontSize = 11.sp,
                                color = TextTertiary,
                                maxLines = 2,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Chip(Icons.Rounded.Visibility, "Details", Blue) { onOpenDetail(pr) }
                            Chip(Icons.Rounded.Article, "Files") { onFilesClick(pr.number) }
                            Chip(Icons.Rounded.RateReview, "Review") { reviewTarget = pr }
                            Chip(Icons.Rounded.FactCheck, "Checks") { checkRunTarget = pr }
                            if (pr.state == "open" && !pr.merged && !pr.draft) {
                                Chip(Icons.Rounded.CallMerge, Strings.ghMerge, Color(0xFF34C759)) {
                                    scope.launch {
                                        val ok = GitHubManager.mergePullRequest(context, repo.owner, repo.name, pr.number)
                                        Toast.makeText(context, if (ok) Strings.ghMerged else Strings.error, Toast.LENGTH_SHORT).show()
                                        if (ok) onRefresh()
                                    }
                                }
                            }
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().padding(top = 10.dp).height(0.5.dp).background(SeparatorColor))
            }
        }
    }

    if (reviewTarget != null) {
        PullReviewDialog(
            repo = repo,
            pr = reviewTarget!!,
            onDismiss = { reviewTarget = null },
            onDone = {
                reviewTarget = null
                onRefresh()
            }
        )
    }

    if (checkRunTarget != null) {
        CheckRunsScreen(
            repoOwner = repo.owner,
            repoName = repo.name,
            ref = checkRunTarget!!.headSha.ifBlank { checkRunTarget!!.head },
            onBack = { checkRunTarget = null }
        )
    }
}

@Composable
private fun PullRequestDetailScreen(
    repo: GHRepo,
    pullNumber: Int,
    onBack: () -> Unit,
    onOpenFiles: (Int) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pr by remember { mutableStateOf<GHPullRequest?>(null) }
    var files by remember { mutableStateOf<List<GHPullFile>>(emptyList()) }
    var comments by remember { mutableStateOf<List<GHReviewComment>>(emptyList()) }
    var checks by remember { mutableStateOf<List<GHCheckRun>>(emptyList()) }
    var reviews by remember { mutableStateOf<List<GHPullReview>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var showReview by remember { mutableStateOf(false) }
    var showChecks by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }
    var showReviewers by remember { mutableStateOf(false) }
    var showReviews by remember { mutableStateOf(false) }
    var showMerge by remember { mutableStateOf(false) }
    var merging by remember { mutableStateOf(false) }

    suspend fun refreshPull() {
        loading = true
        val detail = GitHubManager.getPullRequestDetail(context, repo.owner, repo.name, pullNumber)
        pr = detail
        files = GitHubManager.getPullRequestFiles(context, repo.owner, repo.name, pullNumber)
        comments = GitHubManager.getPullRequestReviewComments(context, repo.owner, repo.name, pullNumber)
        reviews = GitHubManager.getPullRequestReviews(context, repo.owner, repo.name, pullNumber)
        checks = detail?.let { GitHubManager.getPullRequestCheckRuns(context, repo.owner, repo.name, it.headSha.ifBlank { it.head }) }.orEmpty()
        loading = false
    }

    LaunchedEffect(pullNumber) { refreshPull() }

    val current = pr
    val currentHtmlUrl = current?.htmlUrl.orEmpty()
    if (showChecks && current != null) {
        CheckRunsScreen(
            repoOwner = repo.owner,
            repoName = repo.name,
            ref = current.headSha.ifBlank { current.head },
            onBack = { showChecks = false }
        )
        return
    }

    Column(Modifier.fillMaxSize().background(SurfaceLight)) {
        GHTopBar(
            title = "Pull request #$pullNumber",
            subtitle = repo.name,
            onBack = onBack,
            actions = {
                IconButton(onClick = { scope.launch { refreshPull() } }) {
                    Icon(Icons.Rounded.Refresh, null, Modifier.size(20.dp), tint = Blue)
                }
                if (currentHtmlUrl.isNotBlank()) {
                    IconButton(onClick = {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(currentHtmlUrl)))
                        } catch (_: Exception) {
                            Toast.makeText(context, Strings.error, Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Rounded.OpenInNew, null, Modifier.size(20.dp), tint = Blue)
                    }
                }
            }
        )

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Blue, modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
            }
            return@Column
        }

        if (current == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Failed to load pull request", color = TextTertiary, fontSize = 14.sp)
            }
            return@Column
        }

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceWhite).padding(14.dp)) {
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Rounded.CallMerge, null, Modifier.size(22.dp), tint = pullStateColor(current))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(current.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary, lineHeight = 20.sp)
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                PullBadge(pullStateLabel(current), pullStateColor(current))
                                if (current.draft) PullBadge("Draft", TextTertiary)
                                PullBadge("${current.head} -> ${current.base}", Blue)
                            }
                            Text("Opened by ${current.author}", fontSize = 11.sp, color = TextSecondary)
                        }
                    }
                    if (current.body.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text(current.body, fontSize = 12.sp, color = TextSecondary, maxLines = 8, overflow = TextOverflow.Ellipsis)
                    }
                    if (current.requestedReviewers.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            current.requestedReviewers.forEach { reviewer -> PullBadge("review: $reviewer", TextSecondary) }
                        }
                    }
                }
            }

            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PullMetric("${current.commits.takeIf { it > 0 } ?: "?"}", "commits", Blue)
                    PullMetric("${current.changedFiles.takeIf { it > 0 } ?: files.size}", "files", TextSecondary)
                    PullMetric("+${current.additions.takeIf { it > 0 } ?: files.sumOf { f -> f.additions }}", "added", Color(0xFF34C759))
                    PullMetric("-${current.deletions.takeIf { it > 0 } ?: files.sumOf { f -> f.deletions }}", "deleted", Color(0xFFFF3B30))
                    PullMetric("${comments.size}", "review comments", TextSecondary)
                    PullMetric("${reviews.size}", "reviews", TextSecondary)
                }
            }

            item {
                PullMergeabilityCard(current, checks)
            }

            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(Icons.Rounded.Edit, "Edit", TextSecondary) { showEdit = true }
                    Chip(Icons.Rounded.Article, "Files", Blue) { onOpenFiles(pullNumber) }
                    Chip(Icons.Rounded.RateReview, "Review", Blue) { showReview = true }
                    Chip(Icons.Rounded.Group, "Reviewers", Blue) { showReviewers = true }
                    Chip(Icons.Rounded.History, "Reviews", TextSecondary) { showReviews = true }
                    Chip(Icons.Rounded.FactCheck, "Checks", Blue) { showChecks = true }
                    if (current.state == "open" && !current.merged && !current.draft) {
                        Chip(Icons.Rounded.CallMerge, if (merging) "Merging..." else Strings.ghMerge, Color(0xFF34C759)) {
                            if (!merging) showMerge = true
                        }
                    }
                }
            }

            item {
                Text("Changed files", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            }
            if (files.isEmpty()) {
                item { Text("No files loaded", fontSize = 13.sp, color = TextTertiary) }
            } else {
                items(files.take(12)) { file ->
                    PullFileSummaryRow(file)
                }
                if (files.size > 12) {
                    item {
                        Text("+${files.size - 12} more files", fontSize = 11.sp, color = TextTertiary)
                    }
                }
            }
        }
    }

    if (showReview && current != null) {
        PullReviewDialog(
            repo = repo,
            pr = current,
            onDismiss = { showReview = false },
            onDone = {
                showReview = false
                scope.launch { refreshPull() }
            }
        )
    }
    if (showEdit && current != null) {
        PullEditDialog(
            repo = repo,
            pr = current,
            onDismiss = { showEdit = false },
            onDone = {
                showEdit = false
                scope.launch { refreshPull() }
            }
        )
    }
    if (showReviewers && current != null) {
        PullReviewersDialog(
            repo = repo,
            pr = current,
            onDismiss = { showReviewers = false },
            onDone = {
                showReviewers = false
                scope.launch { refreshPull() }
            }
        )
    }
    if (showReviews && current != null) {
        PullReviewHistoryDialog(
            reviews = reviews,
            onDismiss = { showReviews = false }
        )
    }
    if (showMerge && current != null) {
        PullMergeDialog(
            pr = current,
            merging = merging,
            onDismiss = { showMerge = false },
            onMerge = { method, title, message ->
                if (!merging) {
                    merging = true
                    scope.launch {
                        val ok = GitHubManager.mergePullRequest(context, repo.owner, repo.name, pullNumber, message = message, method = method, title = title)
                        merging = false
                        showMerge = false
                        Toast.makeText(context, if (ok) Strings.ghMerged else Strings.error, Toast.LENGTH_SHORT).show()
                        if (ok) refreshPull()
                    }
                }
            }
        )
    }
}

@Composable
private fun PullBadge(text: String, color: Color) {
    Text(
        text,
        fontSize = 10.sp,
        color = color,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.clip(RoundedCornerShape(5.dp)).background(color.copy(0.1f)).padding(horizontal = 7.dp, vertical = 3.dp)
    )
}

@Composable
private fun PullMetric(value: String, label: String, color: Color) {
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).background(SurfaceWhite).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(value, fontSize = 13.sp, color = color, fontWeight = FontWeight.Bold)
        Text(label, fontSize = 11.sp, color = TextSecondary)
    }
}

@Composable
private fun PullMergeabilityCard(pr: GHPullRequest, checks: List<GHCheckRun>) {
    val failedChecks = checks.count { it.conclusion in listOf("failure", "cancelled", "timed_out", "action_required") }
    val activeChecks = checks.count { it.status != "completed" }
    val successChecks = checks.count { it.conclusion == "success" }
    val mergeColor = pullMergeColor(pr)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceWhite).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.Verified, null, Modifier.size(18.dp), tint = mergeColor)
            Text(pullMergeText(pr), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, modifier = Modifier.weight(1f))
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PullBadge("$successChecks successful", Color(0xFF34C759))
            if (activeChecks > 0) PullBadge("$activeChecks active", Blue)
            if (failedChecks > 0) PullBadge("$failedChecks failed", Color(0xFFFF3B30))
            if (checks.isEmpty()) PullBadge("No checks", TextTertiary)
        }
        if (pr.mergeableState.isNotBlank()) {
            Text("Merge state: ${pr.mergeableState}", fontSize = 11.sp, color = TextTertiary)
        }
    }
}

@Composable
private fun PullFileSummaryRow(file: GHPullFile) {
    val color = when (file.status) {
        "added" -> Color(0xFF34C759)
        "removed" -> Color(0xFFFF3B30)
        "renamed" -> Color(0xFF5856D6)
        else -> Color(0xFFFF9500)
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(SurfaceWhite).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(file.filename, fontSize = 12.sp, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text("+${file.additions}", fontSize = 11.sp, color = Color(0xFF34C759))
        Text("-${file.deletions}", fontSize = 11.sp, color = Color(0xFFFF3B30))
    }
}

private fun pullStateLabel(pr: GHPullRequest): String = when {
    pr.merged -> "Merged"
    pr.state == "closed" -> "Closed"
    pr.state == "open" -> "Open"
    else -> pr.state.replaceFirstChar { it.uppercase() }
}

private fun pullStateColor(pr: GHPullRequest): Color = when {
    pr.merged -> Color(0xFF8957E5)
    pr.state == "open" -> Color(0xFF34C759)
    else -> Color(0xFF8E8E93)
}

private fun pullMergeColor(pr: GHPullRequest): Color = when {
    pr.draft -> TextTertiary
    pr.mergeable == true && pr.mergeableState in listOf("clean", "has_hooks", "unstable") -> Color(0xFF34C759)
    pr.mergeable == false || pr.mergeableState in listOf("dirty", "blocked") -> Color(0xFFFF3B30)
    else -> Blue
}

private fun pullMergeText(pr: GHPullRequest): String = when {
    pr.draft -> "Draft pull request cannot be merged yet"
    pr.merged -> "Pull request has been merged"
    pr.state == "closed" -> "Pull request is closed"
    pr.mergeable == true && pr.mergeableState == "clean" -> "Ready to merge"
    pr.mergeable == true && pr.mergeableState == "unstable" -> "Mergeable, but checks need attention"
    pr.mergeableState == "blocked" -> "Blocked by required checks or reviews"
    pr.mergeable == false || pr.mergeableState == "dirty" -> "Cannot merge cleanly"
    else -> "Mergeability is being calculated"
}

@Composable
internal fun ReleasesTab(releases: List<GHRelease>, repo: GHRepo) { val context = LocalContext.current; val scope = rememberCoroutineScope()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) { items(releases) { r -> Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Icon(Icons.Rounded.NewReleases, null, Modifier.size(20.dp), tint = if (r.prerelease) Color(0xFFFF9500) else Blue); Text(r.name.ifBlank { r.tag }, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary); if (r.prerelease) Text(Strings.ghPrerelease, fontSize = 10.sp, color = Color(0xFFFF9500), modifier = Modifier.background(Color(0xFFFF9500).copy(0.1f), RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 1.dp)) }
        Text(r.tag, fontSize = 12.sp, color = TextSecondary)
        if (r.body.isNotBlank()) Text(r.body.take(200), fontSize = 12.sp, color = TextTertiary, modifier = Modifier.padding(top = 4.dp), maxLines = 4)
        r.assets.forEach { a -> Spacer(Modifier.height(4.dp)); Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(SurfaceWhite).clickable { scope.launch { val dest = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "GlassFiles_Git/${a.name}"); GitHubManager.downloadFile(context, repo.owner, repo.name, a.downloadUrl, dest) } }.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.Archive, null, Modifier.size(16.dp), tint = Blue); Column(Modifier.weight(1f)) { Text(a.name, fontSize = 12.sp, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis); Text("${ghFmtSize(a.size)} \u00B7 ${a.downloadCount} dl", fontSize = 10.sp, color = TextTertiary) }; Icon(Icons.Rounded.Download, null, Modifier.size(16.dp), tint = Blue) } }
    }; Box(Modifier.fillMaxWidth().height(0.5.dp).background(SeparatorColor)) } }
}

@Composable
internal fun ReadmeTab(readme: String?, languages: Map<String, Long>, contributors: List<GHContributor>) { val total = languages.values.sum().toFloat().coerceAtLeast(1f)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        if (languages.isNotEmpty()) item { Text(Strings.ghLanguages, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary); Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))) { languages.forEach { (l, b) -> Box(Modifier.weight(b / total).fillMaxHeight().background(langColor(l))) } }; Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) { languages.forEach { (l, b) -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) { Box(Modifier.size(8.dp).clip(CircleShape).background(langColor(l))); Text("$l ${"%.1f".format(b / total * 100)}%", fontSize = 11.sp, color = TextSecondary) } } }; Spacer(Modifier.height(16.dp)) }
        if (contributors.isNotEmpty()) item { Text(Strings.ghContributors, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary); Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { contributors.forEach { c -> Column(horizontalAlignment = Alignment.CenterHorizontally) { AsyncImage(c.avatarUrl, c.login, Modifier.size(36.dp).clip(CircleShape)); Text(c.login, fontSize = 10.sp, color = TextSecondary, maxLines = 1); Text("${c.contributions}", fontSize = 9.sp, color = TextTertiary) } } }; Spacer(Modifier.height(16.dp)) }
        item { Text(Strings.ghReadme, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary); Spacer(Modifier.height(8.dp))
            if (readme.isNullOrBlank()) Text(Strings.ghNoReadme, fontSize = 14.sp, color = TextTertiary)
            else Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceWhite).padding(12.dp)) { Text(readme, fontSize = 13.sp, fontFamily = FontFamily.Monospace, color = TextPrimary, lineHeight = 20.sp) }
        }
    }
}

@Composable
internal fun IssueDetailScreen(repo: GHRepo, issueNumber: Int, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var detail by remember { mutableStateOf<GHIssueDetail?>(null) }
    var comments by remember { mutableStateOf<List<GHComment>>(emptyList()) }
    var issueReactions by remember { mutableStateOf<List<GHReaction>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var newComment by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var previewComment by remember { mutableStateOf(false) }
    var showMetaDialog by remember { mutableStateOf(false) }
    var showReactions by remember { mutableStateOf(false) }
    var showTimeline by remember { mutableStateOf(false) }
    var commentReactionTarget by remember { mutableStateOf<GHComment?>(null) }
    var editingComment by remember { mutableStateOf<GHComment?>(null) }
    var deleteCommentTarget by remember { mutableStateOf<GHComment?>(null) }
    var showLockDialog by remember { mutableStateOf(false) }

    suspend fun refreshIssueDetail(showLoader: Boolean = false) {
        if (showLoader) loading = true
        detail = GitHubManager.getIssueDetail(context, repo.owner, repo.name, issueNumber)
        comments = GitHubManager.getIssueComments(context, repo.owner, repo.name, issueNumber)
        issueReactions = GitHubManager.getIssueReactions(context, repo.owner, repo.name, issueNumber)
        loading = false
    }

    LaunchedEffect(issueNumber) {
        refreshIssueDetail(showLoader = true)
    }

    Column(Modifier.fillMaxSize().background(SurfaceLight)) {
        GHTopBar("#$issueNumber", subtitle = detail?.title, onBack = onBack) {
            if (detail != null) {
                IconButton(onClick = { showReactions = true }) {
                    Icon(Icons.Rounded.EmojiEmotions, null, Modifier.size(20.dp), tint = Blue)
                }
                IconButton(onClick = { showTimeline = true }) {
                    Icon(Icons.Rounded.Timeline, null, Modifier.size(20.dp), tint = Blue)
                }
                IconButton(onClick = { showMetaDialog = true }) {
                    Icon(Icons.Rounded.Tune, null, Modifier.size(20.dp), tint = Blue)
                }
                IconButton(onClick = { showLockDialog = true }) {
                    Icon(
                        if (detail!!.locked) Icons.Rounded.LockOpen else Icons.Rounded.Lock,
                        null,
                        Modifier.size(20.dp),
                        tint = if (detail!!.locked) Color(0xFF34C759) else Color(0xFFFF9500)
                    )
                }
                val isOpen = detail!!.state == "open"
                IconButton(onClick = {
                    scope.launch {
                        val ok = if (isOpen) {
                            GitHubManager.closeIssue(context, repo.owner, repo.name, issueNumber)
                        } else {
                            GitHubManager.reopenIssue(context, repo.owner, repo.name, issueNumber)
                        }
                        if (ok) refreshIssueDetail()
                        Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Icon(
                        if (isOpen) Icons.Rounded.Close else Icons.Rounded.Refresh,
                        null,
                        Modifier.size(20.dp),
                        tint = if (isOpen) Color(0xFFFF3B30) else Color(0xFF34C759)
                    )
                }
            }
        }

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Blue, modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
            }
        } else {
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                detail?.let { issue ->
                    item {
                        IssueHeaderCard(
                            detail = issue,
                            reactions = issueReactions,
                            onMeta = { showMetaDialog = true },
                            onReactions = { showReactions = true },
                            onTimeline = { showTimeline = true }
                        )
                    }
                    item {
                        IssueMetaCard(issue) { showMetaDialog = true }
                    }
                    item {
                        IssueBodyCard(issue.body)
                    }
                    item {
                        Text("${comments.size} comments", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    }
                }
                if (comments.isEmpty()) item {
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceWhite).padding(16.dp)) {
                        Text(Strings.ghNoComments, fontSize = 13.sp, color = TextTertiary)
                    }
                }
                items(comments) { c ->
                    IssueCommentCard(
                        comment = c,
                        onReactions = { commentReactionTarget = c },
                        onEdit = { editingComment = c },
                        onDelete = { deleteCommentTarget = c }
                    )
                }
            }
            Column(
                Modifier.fillMaxWidth().background(SurfaceWhite).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { previewComment = false }) {
                        Text("Write", fontSize = 12.sp, color = if (!previewComment) Blue else TextSecondary)
                    }
                    TextButton(onClick = { previewComment = true }) {
                        Text("Preview", fontSize = 12.sp, color = if (previewComment) Blue else TextSecondary)
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = {
                        if (newComment.isBlank() || sending) return@IconButton
                        sending = true
                        scope.launch {
                            val ok = GitHubManager.addComment(context, repo.owner, repo.name, issueNumber, newComment)
                            if (ok) {
                                comments = GitHubManager.getIssueComments(context, repo.owner, repo.name, issueNumber)
                                newComment = ""
                                previewComment = false
                            }
                            sending = false
                            Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                        }
                    }, enabled = !sending && newComment.isNotBlank()) {
                        Icon(Icons.Rounded.Send, null, Modifier.size(20.dp), tint = if (sending || newComment.isBlank()) TextTertiary else Blue)
                    }
                }
                if (previewComment) {
                    Box(Modifier.fillMaxWidth().heightIn(min = 82.dp, max = 180.dp).clip(RoundedCornerShape(10.dp)).background(SurfaceLight).padding(12.dp).verticalScroll(rememberScrollState())) {
                        if (newComment.isBlank()) Text(Strings.ghAddComment, color = TextTertiary, fontSize = 14.sp)
                        else IssueMarkdownBlock(newComment)
                    }
                } else {
                    Box(Modifier.fillMaxWidth().heightIn(min = 82.dp, max = 180.dp).clip(RoundedCornerShape(10.dp)).background(SurfaceLight).padding(horizontal = 12.dp, vertical = 10.dp)) {
                        if (newComment.isEmpty()) Text(Strings.ghAddComment, color = TextTertiary, fontSize = 14.sp)
                        BasicTextField(
                            newComment,
                            { newComment = it },
                            textStyle = androidx.compose.ui.text.TextStyle(color = TextPrimary, fontSize = 14.sp, lineHeight = 19.sp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }

    if (showMetaDialog && detail != null) {
        IssueMetaDialog(
            repo = repo,
            detail = detail!!,
            onDismiss = { showMetaDialog = false },
            onDone = {
                showMetaDialog = false
                scope.launch { refreshIssueDetail() }
            }
        )
    }

    if (showReactions) {
        IssueReactionsDialog(
            repo = repo,
            issueNumber = issueNumber,
            onDismiss = { showReactions = false },
            onChanged = { scope.launch { issueReactions = GitHubManager.getIssueReactions(context, repo.owner, repo.name, issueNumber) } }
        )
    }

    commentReactionTarget?.let { comment ->
        IssueCommentReactionsDialog(
            repo = repo,
            comment = comment,
            onDismiss = { commentReactionTarget = null }
        )
    }

    if (showTimeline) {
        IssueTimelineDialog(
            repo = repo,
            issueNumber = issueNumber,
            onDismiss = { showTimeline = false }
        )
    }

    if (showLockDialog && detail != null) {
        IssueLockDialog(
            repo = repo,
            detail = detail!!,
            onDismiss = { showLockDialog = false },
            onDone = {
                showLockDialog = false
                scope.launch { refreshIssueDetail() }
            }
        )
    }

    editingComment?.let { comment ->
        IssueCommentEditDialog(
            repo = repo,
            comment = comment,
            onDismiss = { editingComment = null },
            onDone = {
                editingComment = null
                scope.launch { comments = GitHubManager.getIssueComments(context, repo.owner, repo.name, issueNumber) }
            }
        )
    }

    deleteCommentTarget?.let { comment ->
        AlertDialog(
            onDismissRequest = { deleteCommentTarget = null },
            containerColor = SurfaceWhite,
            title = { Text("Delete comment", fontWeight = FontWeight.Bold, color = TextPrimary) },
            text = { Text(comment.body.lineSequence().firstOrNull().orEmpty().take(160), color = TextSecondary, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val ok = GitHubManager.deleteIssueComment(context, repo.owner, repo.name, comment.id)
                        Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                        if (ok) comments = GitHubManager.getIssueComments(context, repo.owner, repo.name, issueNumber)
                        deleteCommentTarget = null
                    }
                }) { Text("Delete", color = Color(0xFFFF3B30)) }
            },
            dismissButton = { TextButton(onClick = { deleteCommentTarget = null }) { Text(Strings.cancel, color = TextSecondary) } }
        )
    }
}


@Composable
private fun IssueHeaderCard(
    detail: GHIssueDetail,
    reactions: List<GHReaction>,
    onMeta: () -> Unit,
    onReactions: () -> Unit,
    onTimeline: () -> Unit
) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceWhite).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
            AsyncImage(detail.avatarUrl, null, Modifier.size(34.dp).clip(CircleShape))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(detail.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary, lineHeight = 20.sp)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    PullBadge(if (detail.state == "open") Strings.ghOpen else Strings.ghClosed, if (detail.state == "open") Color(0xFF34C759) else Color(0xFF8E8E93))
                    Text(detail.author, fontSize = 11.sp, color = Blue, fontWeight = FontWeight.Medium)
                    Text(detail.createdAt.take(10), fontSize = 11.sp, color = TextTertiary)
                }
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(Icons.Rounded.Label, "Meta", Blue, onMeta)
            Chip(Icons.Rounded.EmojiEmotions, "Reactions ${reactions.size}", Blue, onReactions)
            Chip(Icons.Rounded.Timeline, "Timeline", Blue, onTimeline)
            if (detail.locked) PullBadge("Locked ${detail.activeLockReason}".trim(), Color(0xFFFF9500))
        }
        if (reactions.isNotEmpty()) {
            IssueReactionSummary(reactions)
        }
    }
}

@Composable
private fun IssueMetaCard(detail: GHIssueDetail, onEdit: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceWhite).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Metadata", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, modifier = Modifier.weight(1f))
            TextButton(onClick = onEdit) { Text("Edit", fontSize = 12.sp, color = Blue) }
        }
        if (detail.labels.isEmpty() && detail.assignee.isBlank() && detail.milestoneTitle.isBlank()) {
            Text("No labels, assignee, or milestone", fontSize = 12.sp, color = TextTertiary)
            return@Column
        }
        if (detail.labels.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                detail.labels.forEach { label ->
                    Text(label, fontSize = 11.sp, color = Blue, modifier = Modifier.clip(RoundedCornerShape(5.dp)).background(Blue.copy(0.1f)).padding(horizontal = 7.dp, vertical = 3.dp))
                }
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (detail.assignee.isNotBlank()) PullBadge("Assignee: ${detail.assignee}", TextSecondary)
            if (detail.milestoneTitle.isNotBlank()) PullBadge("Milestone: ${detail.milestoneTitle}", TextSecondary)
        }
    }
}

@Composable
private fun IssueBodyCard(body: String) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceWhite).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Description", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        if (body.isBlank()) {
            Text("No description provided.", fontSize = 13.sp, color = TextTertiary)
        } else {
            IssueMarkdownBlock(body)
        }
    }
}

@Composable
private fun IssueCommentCard(comment: GHComment, onReactions: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        AsyncImage(comment.avatarUrl, null, Modifier.size(28.dp).clip(CircleShape))
        Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(SurfaceWhite).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(comment.author, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Blue)
                Text(comment.createdAt.take(10), fontSize = 10.sp, color = TextTertiary)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onReactions, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Rounded.EmojiEmotions, null, Modifier.size(16.dp), tint = TextSecondary)
                }
                IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Rounded.Edit, null, Modifier.size(16.dp), tint = TextSecondary)
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Rounded.Delete, null, Modifier.size(16.dp), tint = Color(0xFFFF3B30))
                }
            }
            IssueMarkdownBlock(comment.body)
        }
    }
}

@Composable
private fun IssueReactionSummary(reactions: List<GHReaction>) {
    val emojiMap = issueEmojiMap()
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        reactions.groupBy { it.content }.forEach { (content, items) ->
            Row(
                Modifier.clip(RoundedCornerShape(8.dp)).background(SurfaceLight).padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(emojiMap[content] ?: content, fontSize = 14.sp)
                Text("${items.size}", fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun IssueMarkdownBlock(text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        text.lines().forEach { raw ->
            val line = raw.trimEnd()
            val trimmed = line.trimStart()
            when {
                trimmed.startsWith("### ") -> Text(trimmed.removePrefix("### "), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, modifier = Modifier.padding(top = 4.dp))
                trimmed.startsWith("## ") -> Text(trimmed.removePrefix("## "), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.padding(top = 5.dp))
                trimmed.startsWith("# ") -> Text(trimmed.removePrefix("# "), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.padding(top = 6.dp))
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> Row {
                    Text("• ", fontSize = 13.sp, color = TextSecondary)
                    Text(trimmed.drop(2), fontSize = 13.sp, color = TextPrimary, lineHeight = 18.sp)
                }
                trimmed.startsWith("> ") -> Row(Modifier.padding(vertical = 2.dp)) {
                    Box(Modifier.width(3.dp).height(18.dp).background(SeparatorColor))
                    Text(trimmed.drop(2), fontSize = 13.sp, color = TextSecondary, modifier = Modifier.padding(start = 8.dp))
                }
                trimmed.startsWith("```") -> Box(Modifier.fillMaxWidth().height(1.dp).background(SeparatorColor))
                trimmed.isBlank() -> Spacer(Modifier.height(5.dp))
                else -> Text(trimmed, fontSize = 13.sp, color = TextPrimary, lineHeight = 19.sp)
            }
        }
    }
}

@Composable
private fun IssueLockDialog(repo: GHRepo, detail: GHIssueDetail, onDismiss: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var reason by remember(detail.number) { mutableStateOf(detail.activeLockReason.ifBlank { "resolved" }) }
    var saving by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceWhite,
        title = { Text(if (detail.locked) "Unlock issue" else "Lock issue", fontWeight = FontWeight.Bold, color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(detail.title, fontSize = 13.sp, color = TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (!detail.locked) {
                    Text("Reason", fontSize = 12.sp, color = TextSecondary)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("resolved", "off-topic", "too heated", "spam").forEach { value ->
                            val selected = reason == value
                            Box(
                                Modifier.clip(RoundedCornerShape(6.dp))
                                    .background(if (selected) Blue.copy(0.15f) else SurfaceLight)
                                    .clickable { reason = value }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(value, fontSize = 12.sp, color = if (selected) Blue else TextSecondary)
                            }
                        }
                    }
                } else if (detail.activeLockReason.isNotBlank()) {
                    PullBadge("Reason: ${detail.activeLockReason}", Color(0xFFFF9500))
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving,
                onClick = {
                    saving = true
                    scope.launch {
                        val ok = if (detail.locked) {
                            GitHubManager.unlockIssue(context, repo.owner, repo.name, detail.number)
                        } else {
                            GitHubManager.lockIssue(context, repo.owner, repo.name, detail.number, reason)
                        }
                        saving = false
                        Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                        if (ok) onDone()
                    }
                }
            ) {
                if (saving) {
                    CircularProgressIndicator(Modifier.size(14.dp), color = Blue, strokeWidth = 2.dp)
                } else {
                    Text(if (detail.locked) "Unlock" else "Lock", color = if (detail.locked) Color(0xFF34C759) else Color(0xFFFF9500))
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.cancel, color = TextSecondary) } }
    )
}

@Composable
private fun IssueCommentEditDialog(repo: GHRepo, comment: GHComment, onDismiss: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var body by remember(comment.id) { mutableStateOf(comment.body) }
    var saving by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceWhite,
        title = { Text("Edit comment", fontWeight = FontWeight.Bold, color = TextPrimary) },
        text = {
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = { Text("Comment") },
                minLines = 6,
                maxLines = 10,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                enabled = !saving && body.isNotBlank(),
                onClick = {
                    saving = true
                    scope.launch {
                        val ok = GitHubManager.updateIssueComment(context, repo.owner, repo.name, comment.id, body)
                        saving = false
                        Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                        if (ok) onDone()
                    }
                }
            ) {
                if (saving) {
                    CircularProgressIndicator(Modifier.size(14.dp), color = Blue, strokeWidth = 2.dp)
                } else {
                    Text("Save", color = Blue)
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.cancel, color = TextSecondary) } }
    )
}

private fun issueEmojiMap(): Map<String, String> = mapOf(
    "+1" to "👍",
    "-1" to "👎",
    "laugh" to "😄",
    "confused" to "😕",
    "heart" to "❤️",
    "hooray" to "🎉",
    "eyes" to "👀",
    "rocket" to "🚀"
)

@Composable
private fun IssueMetaDialog(repo: GHRepo, detail: GHIssueDetail, onDismiss: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var labels by remember { mutableStateOf<List<GHLabel>>(emptyList()) }
    var milestones by remember { mutableStateOf<List<GHMilestone>>(emptyList()) }
    var assignees by remember { mutableStateOf<List<GHUserLite>>(emptyList()) }
    val selectedLabels = remember(detail.number) { mutableStateListOf<String>().apply { addAll(detail.labels) } }
    var selectedAssignee by remember(detail.number) { mutableStateOf(detail.assignee) }
    var selectedMilestone by remember(detail.number) { mutableStateOf(detail.milestoneTitle) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(detail.number) {
        loading = true
        labels = GitHubManager.getLabels(context, repo.owner, repo.name)
        milestones = GitHubManager.getMilestones(context, repo.owner, repo.name)
        assignees = GitHubManager.getAssignees(context, repo.owner, repo.name)
        loading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceWhite,
        title = { Text("Issue metadata", fontWeight = FontWeight.Bold, color = TextPrimary) },
        text = {
            if (loading) {
                Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Blue, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Labels", fontSize = 12.sp, color = TextSecondary)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        labels.forEach { label ->
                            val selected = label.name in selectedLabels
                            Box(
                                Modifier.clip(RoundedCornerShape(6.dp))
                                    .background(if (selected) Blue.copy(0.15f) else SurfaceLight)
                                    .clickable {
                                        if (selected) selectedLabels.remove(label.name) else selectedLabels.add(label.name)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(label.name, fontSize = 12.sp, color = if (selected) Blue else TextSecondary)
                            }
                        }
                    }

                    Text("Assignee", fontSize = 12.sp, color = TextSecondary)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.clip(RoundedCornerShape(6.dp)).background(if (selectedAssignee.isBlank()) Blue.copy(0.15f) else SurfaceLight).clickable { selectedAssignee = "" }.padding(horizontal = 8.dp, vertical = 4.dp)) {
                            Text("None", fontSize = 12.sp, color = if (selectedAssignee.isBlank()) Blue else TextSecondary)
                        }
                        assignees.forEach { user ->
                            val selected = selectedAssignee == user.login
                            Box(
                                Modifier.clip(RoundedCornerShape(6.dp))
                                    .background(if (selected) Blue.copy(0.15f) else SurfaceLight)
                                    .clickable { selectedAssignee = user.login }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(user.login, fontSize = 12.sp, color = if (selected) Blue else TextSecondary)
                            }
                        }
                    }

                    Text("Milestone", fontSize = 12.sp, color = TextSecondary)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.clip(RoundedCornerShape(6.dp)).background(if (selectedMilestone.isBlank()) Blue.copy(0.15f) else SurfaceLight).clickable { selectedMilestone = "" }.padding(horizontal = 8.dp, vertical = 4.dp)) {
                            Text("None", fontSize = 12.sp, color = if (selectedMilestone.isBlank()) Blue else TextSecondary)
                        }
                        milestones.forEach { milestone ->
                            val selected = selectedMilestone == milestone.title
                            Box(
                                Modifier.clip(RoundedCornerShape(6.dp))
                                    .background(if (selected) Blue.copy(0.15f) else SurfaceLight)
                                    .clickable { selectedMilestone = milestone.title }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(milestone.title, fontSize = 12.sp, color = if (selected) Blue else TextSecondary)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (saving) return@TextButton
                    saving = true
                    scope.launch {
                        val milestoneNumber = milestones.firstOrNull { it.title == selectedMilestone }?.number
                        val ok = GitHubManager.updateIssueMeta(
                            context = context,
                            owner = repo.owner,
                            repo = repo.name,
                            issueNumber = detail.number,
                            labels = selectedLabels.toList(),
                            assignees = if (selectedAssignee.isBlank()) emptyList() else listOf(selectedAssignee),
                            milestoneNumber = milestoneNumber,
                            clearMilestone = selectedMilestone.isBlank()
                        )
                        Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                        saving = false
                        if (ok) onDone()
                    }
                },
                enabled = !loading && !saving
            ) { Text("Save", color = Blue) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.cancel, color = TextSecondary) } }
    )
}

@Composable
private fun PullEditDialog(repo: GHRepo, pr: GHPullRequest, onDismiss: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var title by remember(pr.number) { mutableStateOf(pr.title) }
    var body by remember(pr.number) { mutableStateOf(pr.body) }
    var base by remember(pr.number) { mutableStateOf(pr.base) }
    var state by remember(pr.number) { mutableStateOf(pr.state) }
    var saving by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceWhite,
        title = { Text("Edit PR #${pr.number}", fontWeight = FontWeight.Bold, color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(body, { body = it }, label = { Text("Body") }, minLines = 4, maxLines = 8, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(base, { base = it.trim() }, label = { Text("Base branch") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("open" to "Open", "closed" to "Closed").forEach { (value, label) ->
                        val selected = state == value
                        Box(
                            Modifier.clip(RoundedCornerShape(6.dp))
                                .background(if (selected) Blue.copy(0.15f) else SurfaceLight)
                                .clickable { state = value }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(label, fontSize = 12.sp, color = if (selected) Blue else TextSecondary)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving && title.isNotBlank() && base.isNotBlank(),
                onClick = {
                    if (saving) return@TextButton
                    saving = true
                    scope.launch {
                        val ok = GitHubManager.updatePullRequest(
                            context = context,
                            owner = repo.owner,
                            repo = repo.name,
                            number = pr.number,
                            title = title.trim(),
                            body = body,
                            base = base.trim(),
                            state = state
                        )
                        saving = false
                        Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                        if (ok) onDone()
                    }
                }
            ) {
                if (saving) CircularProgressIndicator(Modifier.size(14.dp), color = Blue, strokeWidth = 2.dp)
                else Text("Save", color = Blue)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.cancel, color = TextSecondary) } }
    )
}

@Composable
private fun PullReviewersDialog(repo: GHRepo, pr: GHPullRequest, onDismiss: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var reviewersRaw by remember { mutableStateOf(pr.requestedReviewers.joinToString(",")) }
    var saving by remember { mutableStateOf(false) }
    val reviewers = reviewersRaw.split(",").map { it.trim().removePrefix("@") }.filter { it.isNotBlank() }.distinct()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceWhite,
        title = { Text("Reviewers #${pr.number}", fontWeight = FontWeight.Bold, color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (pr.requestedReviewers.isNotEmpty()) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        pr.requestedReviewers.forEach { reviewer -> PullBadge(reviewer, TextSecondary) }
                    }
                } else {
                    Text("No requested reviewers", fontSize = 12.sp, color = TextTertiary)
                }
                OutlinedTextField(
                    value = reviewersRaw,
                    onValueChange = { reviewersRaw = it },
                    label = { Text("Usernames, comma-separated") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Request adds reviewers; remove removes the typed usernames.", fontSize = 11.sp, color = TextTertiary)
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    enabled = !saving && reviewers.isNotEmpty(),
                    onClick = {
                        saving = true
                        scope.launch {
                            val ok = GitHubManager.removePullRequestReviewers(context, repo.owner, repo.name, pr.number, reviewers)
                            saving = false
                            Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                            if (ok) onDone()
                        }
                    }
                ) { Text("Remove", color = Color(0xFFFF3B30)) }
                TextButton(
                    enabled = !saving && reviewers.isNotEmpty(),
                    onClick = {
                        saving = true
                        scope.launch {
                            val ok = GitHubManager.requestPullRequestReviewers(context, repo.owner, repo.name, pr.number, reviewers)
                            saving = false
                            Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                            if (ok) onDone()
                        }
                    }
                ) {
                    if (saving) CircularProgressIndicator(Modifier.size(14.dp), color = Blue, strokeWidth = 2.dp)
                    else Text("Request", color = Blue)
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.cancel, color = TextSecondary) } }
    )
}

@Composable
private fun PullReviewHistoryDialog(reviews: List<GHPullReview>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceWhite,
        title = { Text("Review history", fontWeight = FontWeight.Bold, color = TextPrimary) },
        text = {
            if (reviews.isEmpty()) {
                Text("No reviews yet", fontSize = 13.sp, color = TextTertiary)
            } else {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(reviews) { review ->
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(SurfaceLight).padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PullBadge(review.state.ifBlank { "review" }, reviewStateColor(review.state))
                                Text(review.user.ifBlank { "GitHub" }, fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                                if (review.submittedAt.isNotBlank()) Text(review.submittedAt.take(10), fontSize = 10.sp, color = TextTertiary)
                            }
                            if (review.body.isNotBlank()) Text(review.body, fontSize = 11.sp, color = TextSecondary, maxLines = 4, overflow = TextOverflow.Ellipsis)
                            if (review.commitId.length >= 7) Text(review.commitId.take(7), fontSize = 10.sp, color = TextTertiary)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = Blue) } }
    )
}

@Composable
private fun PullMergeDialog(
    pr: GHPullRequest,
    merging: Boolean,
    onDismiss: () -> Unit,
    onMerge: (method: String, title: String, message: String) -> Unit
) {
    var method by remember { mutableStateOf("merge") }
    var title by remember { mutableStateOf("${pr.title} (#${pr.number})") }
    var message by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!merging) onDismiss() },
        containerColor = SurfaceWhite,
        title = { Text("Merge PR #${pr.number}", fontWeight = FontWeight.Bold, color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("merge" to "Merge", "squash" to "Squash", "rebase" to "Rebase").forEach { (value, label) ->
                        val selected = method == value
                        Box(
                            Modifier.clip(RoundedCornerShape(6.dp))
                                .background(if (selected) Blue.copy(0.15f) else SurfaceLight)
                                .clickable { method = value }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(label, fontSize = 12.sp, color = if (selected) Blue else TextSecondary)
                        }
                    }
                }
                OutlinedTextField(title, { title = it }, label = { Text("Commit title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(message, { message = it }, label = { Text("Commit message") }, minLines = 3, maxLines = 5, modifier = Modifier.fillMaxWidth())
                Text("${pr.head} -> ${pr.base}", fontSize = 11.sp, color = TextTertiary)
            }
        },
        confirmButton = {
            TextButton(enabled = !merging && title.isNotBlank(), onClick = { onMerge(method, title, message) }) {
                if (merging) CircularProgressIndicator(Modifier.size(14.dp), color = Color(0xFF34C759), strokeWidth = 2.dp)
                else Text(Strings.ghMerge, color = Color(0xFF34C759))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !merging) { Text(Strings.cancel, color = TextSecondary) } }
    )
}

private fun reviewStateColor(state: String): Color = when (state.lowercase()) {
    "approved" -> Color(0xFF34C759)
    "changes_requested" -> Color(0xFFFF3B30)
    "commented" -> Blue
    "dismissed" -> TextTertiary
    else -> TextSecondary
}

@Composable
private fun PullReviewDialog(repo: GHRepo, pr: GHPullRequest, onDismiss: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var body by remember { mutableStateOf("") }
    var event by remember { mutableStateOf("COMMENT") }
    var sending by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceWhite,
        title = { Text("Review #${pr.number}", fontWeight = FontWeight.Bold, color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("COMMENT" to "Comment", "APPROVE" to "Approve", "REQUEST_CHANGES" to "Request changes").forEach { (value, label) ->
                        val selected = event == value
                        Box(
                            Modifier.clip(RoundedCornerShape(6.dp))
                                .background(if (selected) Blue.copy(0.15f) else SurfaceLight)
                                .clickable { event = value }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(label, fontSize = 12.sp, color = if (selected) Blue else TextSecondary)
                        }
                    }
                }
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text("Review message") },
                    modifier = Modifier.fillMaxWidth().height(140.dp),
                    maxLines = 8
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (sending) return@TextButton
                sending = true
                scope.launch {
                    val ok = GitHubManager.submitPullRequestReview(context, repo.owner, repo.name, pr.number, event, body)
                    Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                    sending = false
                    if (ok) onDone()
                }
            }) { Text("Submit", color = Blue) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.cancel, color = TextSecondary) } }
    )
}

@Composable
private fun PullFilesDialog(repo: GHRepo, pr: GHPullRequest, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var files by remember { mutableStateOf<List<GHPullFile>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(pr.number) {
        loading = true
        files = GitHubManager.getPullRequestFiles(context, repo.owner, repo.name, pr.number)
        loading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceWhite,
        title = { Text("Files #${pr.number}", fontWeight = FontWeight.Bold, color = TextPrimary) },
        text = {
            if (loading) {
                Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Blue, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            } else {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(files) { file ->
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(SurfaceLight).padding(10.dp)
                        ) {
                            Text(file.filename, fontSize = 13.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                            Text("${file.status}  +${file.additions}  -${file.deletions}", fontSize = 10.sp, color = TextSecondary)
                            if (file.patch.isNotBlank()) {
                                Spacer(Modifier.height(6.dp))
                                Box(Modifier.fillMaxWidth().background(Color(0xFF1E1E22), RoundedCornerShape(6.dp)).padding(8.dp)) {
                                    Text(file.patch.take(1200), fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = Color(0xFFD4D4D4))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = Blue) } }
    )
}

@Composable
internal fun CommitDiffScreen(repo: GHRepo, sha: String, onBack: () -> Unit) { val context = LocalContext.current; var detail by remember { mutableStateOf<GHCommitDetail?>(null) }; var loading by remember { mutableStateOf(true) }
    LaunchedEffect(sha) { detail = GitHubManager.getCommitDiff(context, repo.owner, repo.name, sha); loading = false }
    Column(Modifier.fillMaxSize().background(SurfaceLight)) { GHTopBar(sha.take(7), subtitle = detail?.message?.lines()?.firstOrNull(), onBack = onBack)
        if (loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Blue, modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp) }
        else if (detail != null) { Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) { Text("+${detail!!.totalAdditions}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF34C759)); Text("-${detail!!.totalDeletions}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF3B30)); Text("${detail!!.files.size} files", fontSize = 13.sp, color = TextSecondary) }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) { items(detail!!.files) { f -> Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) { Box(Modifier.size(8.dp).clip(CircleShape).background(when (f.status) { "added" -> Color(0xFF34C759); "removed" -> Color(0xFFFF3B30); else -> Color(0xFFFF9500) })); Text(f.filename, fontSize = 13.sp, color = TextPrimary, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)); Text("+${f.additions}", fontSize = 11.sp, color = Color(0xFF34C759)); Text("-${f.deletions}", fontSize = 11.sp, color = Color(0xFFFF3B30)) }
                if (f.patch.isNotBlank()) { Spacer(Modifier.height(4.dp)); Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFF1E1E22)).horizontalScroll(rememberScrollState()).padding(8.dp)) { Text(f.patch.take(2000), fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color(0xFFD4D4D4), lineHeight = 16.sp) } }
            }; Box(Modifier.fillMaxWidth().height(0.5.dp).background(SeparatorColor)) } }
        }
    }
}

// ═══════════════════════════════════
// Issue Reactions Dialog
// ═══════════════════════════════════

@Composable
private fun IssueReactionsDialog(repo: GHRepo, issueNumber: Int, onDismiss: () -> Unit, onChanged: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var reactions by remember { mutableStateOf<List<GHReaction>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(issueNumber) {
        reactions = GitHubManager.getIssueReactions(context, repo.owner, repo.name, issueNumber)
        loading = false
    }

    val emojiMap = issueEmojiMap()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceWhite,
        title = { Text("Reactions", fontWeight = FontWeight.Bold, color = TextPrimary) },
        text = {
            Column {
                // Add reaction row
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                    emojiMap.forEach { (key, emoji) ->
                        Box(
                            Modifier.clip(RoundedCornerShape(8.dp))
                                .background(SurfaceLight)
                                .clickable {
                                    scope.launch {
                                        val ok = GitHubManager.addIssueReaction(context, repo.owner, repo.name, issueNumber, key)
                                        reactions = GitHubManager.getIssueReactions(context, repo.owner, repo.name, issueNumber)
                                        if (ok) onChanged()
                                    }
                                }
                                .padding(8.dp)
                        ) {
                            Text(emoji, fontSize = 20.sp)
                        }
                    }
                }

                if (loading) {
                    Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Blue, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                } else if (reactions.isEmpty()) {
                    Text("No reactions yet", fontSize = 13.sp, color = TextTertiary)
                } else {
                    // Group by reaction type
                    val grouped = reactions.groupBy { it.content }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        grouped.forEach { (content, items) ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(emojiMap[content] ?: content, fontSize = 16.sp)
                                Text("${items.size}", fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                                Text(items.joinToString(", ") { it.user }, fontSize = 11.sp, color = TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = Blue) } }
    )
}

@Composable
private fun IssueCommentReactionsDialog(repo: GHRepo, comment: GHComment, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var reactions by remember { mutableStateOf<List<GHReaction>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val emojiMap = issueEmojiMap()

    LaunchedEffect(comment.id) {
        reactions = GitHubManager.getIssueCommentReactions(context, repo.owner, repo.name, comment.id)
        loading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceWhite,
        title = { Text("Comment reactions", fontWeight = FontWeight.Bold, color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(comment.body.lineSequence().firstOrNull().orEmpty().take(120), fontSize = 12.sp, color = TextSecondary, maxLines = 2)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    emojiMap.forEach { (key, emoji) ->
                        Box(
                            Modifier.clip(RoundedCornerShape(8.dp))
                                .background(SurfaceLight)
                                .clickable {
                                    scope.launch {
                                        GitHubManager.addIssueCommentReaction(context, repo.owner, repo.name, comment.id, key)
                                        reactions = GitHubManager.getIssueCommentReactions(context, repo.owner, repo.name, comment.id)
                                    }
                                }
                                .padding(8.dp)
                        ) {
                            Text(emoji, fontSize = 20.sp)
                        }
                    }
                }
                if (loading) {
                    Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Blue, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                } else if (reactions.isEmpty()) {
                    Text("No reactions yet", fontSize = 13.sp, color = TextTertiary)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        reactions.groupBy { it.content }.forEach { (content, items) ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(emojiMap[content] ?: content, fontSize = 16.sp)
                                Text("${items.size}", fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                                Text(items.joinToString(", ") { it.user }, fontSize = 11.sp, color = TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = Blue) } }
    )
}

// ═══════════════════════════════════
// Issue Timeline Dialog
// ═══════════════════════════════════

@Composable
private fun IssueTimelineDialog(repo: GHRepo, issueNumber: Int, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var events by remember { mutableStateOf<List<GHTimelineEvent>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(issueNumber) {
        events = GitHubManager.getIssueTimeline(context, repo.owner, repo.name, issueNumber)
        loading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceWhite,
        title = { Text("Timeline", fontWeight = FontWeight.Bold, color = TextPrimary) },
        text = {
            if (loading) {
                Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Blue, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            } else if (events.isEmpty()) {
                Text("No timeline events", fontSize = 13.sp, color = TextTertiary)
            } else {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 400.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(events) { event ->
                        val eventText = when (event.event) {
                            "labeled" -> "added label \"${event.label}\""
                            "unlabeled" -> "removed label \"${event.label}\""
                            "milestoned" -> "added to milestone \"${event.milestone}\""
                            "demilestoned" -> "removed from milestone \"${event.milestone}\""
                            "assigned" -> "assigned to ${event.assignee}"
                            "unassigned" -> "unassigned ${event.assignee}"
                            "closed" -> "closed this"
                            "reopened" -> "reopened this"
                            "cross-referenced" -> "referenced this"
                            "commented" -> "commented"
                            "committed" -> "committed"
                            "reviewed" -> "reviewed"
                            else -> event.event
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(Blue))
                            Column {
                                Text("${event.actor} $eventText", fontSize = 12.sp, color = TextPrimary)
                                Text(event.createdAt.take(10), fontSize = 10.sp, color = TextTertiary)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = Blue) } }
    )
}
