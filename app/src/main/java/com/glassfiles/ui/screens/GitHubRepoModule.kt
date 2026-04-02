package com.glassfiles.ui.screens

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

internal enum class RepoTab { FILES, COMMITS, ISSUES, PULLS, RELEASES, ACTIONS, README, CODE_SEARCH }

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
    var fileContent by remember { mutableStateOf<String?>(null) }; var editingFile by remember { mutableStateOf<GHContent?>(null) }
    var cloneProgress by remember { mutableStateOf<String?>(null) }; var isStarred by remember { mutableStateOf(false) }
    var isWatching by remember { mutableStateOf(false) }
    var selectedBranch by remember { mutableStateOf(repo.defaultBranch) }
    var showUpload by remember { mutableStateOf(false) }; var showCreateFile by remember { mutableStateOf(false) }
    var showCreateBranch by remember { mutableStateOf(false) }; var showCreateIssue by remember { mutableStateOf(false) }
    var showCreatePR by remember { mutableStateOf(false) }; var selectedIssue by remember { mutableStateOf<GHIssue?>(null) }
    var selectedCommitSha by remember { mutableStateOf<String?>(null) }; var deleteTarget by remember { mutableStateOf<GHContent?>(null) }
    var showBranchPicker by remember { mutableStateOf(false) }
    var languages by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }; var contributors by remember { mutableStateOf<List<GHContributor>>(emptyList()) }
    // Pagination
    var commitsPage by remember { mutableIntStateOf(1) }; var commitsHasMore by remember { mutableStateOf(true) }
    var issuesPage by remember { mutableIntStateOf(1) }; var issuesHasMore by remember { mutableStateOf(true) }
    var pullsPage by remember { mutableIntStateOf(1) }; var pullsHasMore by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) { isStarred = GitHubManager.isStarred(context, repo.owner, repo.name); isWatching = GitHubManager.isWatching(context, repo.owner, repo.name); branches = GitHubManager.getBranches(context, repo.owner, repo.name) }
    LaunchedEffect(selectedTab, currentPath, selectedBranch) { loading = true; when (selectedTab) {
        RepoTab.FILES -> contents = GitHubManager.getRepoContents(context, repo.owner, repo.name, currentPath)
        RepoTab.COMMITS -> { commitsPage = 1; val r = GitHubManager.getCommits(context, repo.owner, repo.name, 1); commits = r; commitsHasMore = r.size >= 30 }
        RepoTab.ISSUES -> { issuesPage = 1; val r = GitHubManager.getIssues(context, repo.owner, repo.name, page = 1); issues = r; issuesHasMore = r.size >= 30 }
        RepoTab.PULLS -> { pullsPage = 1; val r = GitHubManager.getPullRequests(context, repo.owner, repo.name, page = 1); pulls = r; pullsHasMore = r.size >= 30 }
        RepoTab.RELEASES -> releases = GitHubManager.getReleases(context, repo.owner, repo.name)
        RepoTab.ACTIONS -> { workflowRuns = GitHubManager.getWorkflowRuns(context, repo.owner, repo.name); workflows = GitHubManager.getWorkflows(context, repo.owner, repo.name) }
        RepoTab.README -> { readme = GitHubManager.getReadme(context, repo.owner, repo.name); languages = GitHubManager.getLanguages(context, repo.owner, repo.name); contributors = GitHubManager.getContributors(context, repo.owner, repo.name) }
        RepoTab.CODE_SEARCH -> { /* searches on demand */ }
    }; loading = false }

    if (selectedIssue != null) { IssueDetailScreen(repo, selectedIssue!!.number) { selectedIssue = null }; return }
    if (selectedCommitSha != null) { CommitDiffScreen(repo, selectedCommitSha!!) { selectedCommitSha = null }; return }
    if (selectedRunId != null) { WorkflowRunDetailScreen(repo, selectedRunId!!) { selectedRunId = null }; return }
    val safeFileContent = fileContent
    if (safeFileContent != null) {
        val ext = currentPath.substringAfterLast(".", "").lowercase()
        val isImage = ext in listOf("png", "jpg", "jpeg", "gif", "webp", "svg", "ico")
        val isMd = ext in listOf("md", "markdown")
        val cachedLines = remember(safeFileContent) { safeFileContent.lines() }
        Column(Modifier.fillMaxSize().background(Color(0xFF0D1117))) {
            GHTopBar(currentPath.substringAfterLast("/"), onBack = { fileContent = null }) {
                // Copy all
                IconButton(onClick = {
                    val clip = android.content.ClipData.newPlainText("code", safeFileContent)
                    (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(clip)
                    Toast.makeText(context, Strings.done, Toast.LENGTH_SHORT).show()
                }) { Icon(Icons.Rounded.ContentCopy, null, Modifier.size(20.dp), tint = Blue) }
                // Download
                IconButton(onClick = { scope.launch { val dest = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "GlassFiles_Git/${currentPath.substringAfterLast("/")}"); val ok = GitHubManager.downloadFile(context, repo.owner, repo.name, currentPath, dest); Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show() } }) { Icon(Icons.Rounded.Download, null, Modifier.size(20.dp), tint = Blue) }
                // Edit
                IconButton(onClick = { val fc = contents.find { it.path == currentPath }; if (fc != null) { editingFile = fc; fileContent = null } }) { Icon(Icons.Rounded.Edit, null, Modifier.size(20.dp), tint = Blue) }
            }
            // File info bar
            Row(Modifier.fillMaxWidth().background(Color(0xFF161B22)).padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${cachedLines.size} lines", fontSize = 11.sp, color = Color(0xFF8B949E))
                Text("${safeFileContent.length} chars", fontSize = 11.sp, color = Color(0xFF8B949E))
                Text(ext.uppercase(), fontSize = 11.sp, color = Color(0xFF58A6FF), fontWeight = FontWeight.SemiBold)
            }
            // Content
            if (isImage) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Image preview not available\nUse Download to save", fontSize = 14.sp, color = Color(0xFF8B949E), textAlign = TextAlign.Center)
                }
            } else if (isMd) {
                LazyColumn(Modifier.fillMaxSize().padding(12.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(cachedLines.size) { idx ->
                        MarkdownLine(cachedLines[idx])
                    }
                }
            } else {
                // Code with syntax highlighting
                LazyColumn(Modifier.fillMaxSize().padding(start = 4.dp, end = 4.dp, top = 4.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(cachedLines.size) { idx ->
                        Row(Modifier.fillMaxWidth()) {
                            Text(
                                "${idx + 1}".padStart(4),
                                fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                                color = Color(0xFF484F58), modifier = Modifier.padding(end = 10.dp)
                            )
                            Text(
                                highlightLine(cachedLines[idx], ext),
                                fontSize = 12.sp, fontFamily = FontFamily.Monospace, lineHeight = 18.sp
                            )
                        }
                    }
                }
            }
        }; return
    }
    if (editingFile != null) { EditFileScreen(repo, editingFile!!, selectedBranch, { editingFile = null }) { editingFile = null; scope.launch { contents = GitHubManager.getRepoContents(context, repo.owner, repo.name, currentPath) } }; return }

    Column(Modifier.fillMaxSize().background(SurfaceLight)) {
        GHTopBar(repo.name, subtitle = if (currentPath.isNotBlank()) currentPath else repo.owner, onBack = { if (currentPath.isNotBlank() && selectedTab == RepoTab.FILES) currentPath = currentPath.substringBeforeLast("/", "") else onBack() }, onMinimize = onMinimize, onClose = onClose) {
            val ic = if (LocalGHCompact.current) 16.dp else 20.dp
            IconButton(onClick = { scope.launch { if (isStarred) GitHubManager.unstarRepo(context, repo.owner, repo.name) else GitHubManager.starRepo(context, repo.owner, repo.name); isStarred = !isStarred } }, modifier = if (LocalGHCompact.current) Modifier.size(32.dp) else Modifier) { Icon(if (isStarred) Icons.Rounded.Star else Icons.Rounded.StarBorder, null, Modifier.size(ic), tint = Color(0xFFFFCC00)) }
            IconButton(onClick = { scope.launch { if (isWatching) GitHubManager.unwatchRepo(context, repo.owner, repo.name) else GitHubManager.watchRepo(context, repo.owner, repo.name); isWatching = !isWatching } }, modifier = if (LocalGHCompact.current) Modifier.size(32.dp) else Modifier) { Icon(if (isWatching) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff, null, Modifier.size(ic), tint = if (isWatching) Blue else TextSecondary) }
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
            when (selectedTab) { RepoTab.FILES -> { SmallAction(Icons.Rounded.NoteAdd, Strings.ghCreateFile) { showCreateFile = true }; SmallAction(Icons.Rounded.Upload, Strings.ghUpload) { showUpload = true } }; RepoTab.ISSUES -> SmallAction(Icons.Rounded.Add, Strings.ghNewIssue) { showCreateIssue = true }; RepoTab.PULLS -> SmallAction(Icons.Rounded.Add, Strings.ghNewPR) { showCreatePR = true }; RepoTab.ACTIONS -> SmallAction(Icons.Rounded.PlayArrow, Strings.ghRunWorkflow) { showDispatch = true }; else -> {} }
        }
        // Tabs
        Row(Modifier.fillMaxWidth().background(SurfaceWhite).horizontalScroll(rememberScrollState()).padding(horizontal = if (cmp) 6.dp else 12.dp, vertical = if (cmp) 3.dp else 6.dp), horizontalArrangement = Arrangement.spacedBy(if (cmp) 4.dp else 6.dp)) {
            RepoTab.entries.forEach { tab -> val sel = selectedTab == tab; val label = when (tab) { RepoTab.FILES -> Strings.ghGistFiles; RepoTab.COMMITS -> Strings.ghCommits; RepoTab.ISSUES -> "Issues"; RepoTab.PULLS -> Strings.ghPulls; RepoTab.RELEASES -> Strings.ghReleases; RepoTab.ACTIONS -> Strings.ghActions; RepoTab.README -> Strings.ghReadme; RepoTab.CODE_SEARCH -> Strings.ghSearchCode }
                Box(Modifier.clip(RoundedCornerShape(6.dp)).background(if (sel) Blue.copy(0.12f) else Color.Transparent).border(1.dp, if (sel) Blue.copy(0.3f) else SeparatorColor, RoundedCornerShape(6.dp)).clickable { selectedTab = tab }.padding(horizontal = if (cmp) 6.dp else 10.dp, vertical = if (cmp) 3.dp else 6.dp)) { Text(label, fontSize = if (cmp) 10.sp else 12.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal, color = if (sel) Blue else TextSecondary) }
            }
        }
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(SeparatorColor))
        if (loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Blue, modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp) }
        else when (selectedTab) {
            RepoTab.FILES -> FilesTab(contents, onDirClick = { currentPath = it.path }, onFileClick = { scope.launch { fileContent = GitHubManager.getFileContent(context, repo.owner, repo.name, it.path); currentPath = it.path } }, onEdit = { editingFile = it }, onDelete = { deleteTarget = it }, onDownload = { scope.launch { val dest = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "GlassFiles_Git/${it.name}"); val ok = GitHubManager.downloadFile(context, repo.owner, repo.name, it.path, dest); Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show() } })
            RepoTab.COMMITS -> CommitsTab(commits, commitsHasMore, { scope.launch { commitsPage++; val r = GitHubManager.getCommits(context, repo.owner, repo.name, commitsPage); if (r.size < 30) commitsHasMore = false; commits = commits + r } }) { selectedCommitSha = it.sha }
            RepoTab.ISSUES -> IssuesTab(issues, issuesHasMore, { scope.launch { issuesPage++; val r = GitHubManager.getIssues(context, repo.owner, repo.name, page = issuesPage); if (r.size < 30) issuesHasMore = false; issues = issues + r } }) { selectedIssue = it }
            RepoTab.PULLS -> PullsTab(pulls, repo) { scope.launch { pulls = GitHubManager.getPullRequests(context, repo.owner, repo.name) } }
            RepoTab.RELEASES -> ReleasesTab(releases, repo)
            RepoTab.ACTIONS -> ActionsTab(workflowRuns, repo) { selectedRunId = it.id }
            RepoTab.README -> ReadmeTab(readme, languages, contributors)
            RepoTab.CODE_SEARCH -> CodeSearchTab(repo)
        }
    }
    if (showUpload) UploadDialog(repo, currentPath, selectedBranch, { showUpload = false }) { showUpload = false; scope.launch { contents = GitHubManager.getRepoContents(context, repo.owner, repo.name, currentPath) } }
    if (showCreateFile) CreateFileDialog(repo, currentPath, selectedBranch, { showCreateFile = false }) { showCreateFile = false; scope.launch { contents = GitHubManager.getRepoContents(context, repo.owner, repo.name, currentPath) } }
    if (showCreateBranch) CreateBranchDialog(repo, branches, { showCreateBranch = false }) { showCreateBranch = false; scope.launch { branches = GitHubManager.getBranches(context, repo.owner, repo.name) } }
    if (showCreateIssue) CreateIssueDialog(repo, { showCreateIssue = false }) { showCreateIssue = false; scope.launch { issues = GitHubManager.getIssues(context, repo.owner, repo.name) } }
    if (showCreatePR) CreatePRDialog(repo, branches, { showCreatePR = false }) { showCreatePR = false; scope.launch { pulls = GitHubManager.getPullRequests(context, repo.owner, repo.name) } }
    if (deleteTarget != null) DeleteFileDialog(repo, deleteTarget!!, selectedBranch, { deleteTarget = null }) { deleteTarget = null; scope.launch { contents = GitHubManager.getRepoContents(context, repo.owner, repo.name, currentPath) } }
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

@Composable private fun Chip(icon: ImageVector, label: String, tint: Color = Blue, onClick: () -> Unit) { Row(Modifier.clip(RoundedCornerShape(6.dp)).background(tint.copy(0.08f)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) { Icon(icon, null, Modifier.size(12.dp), tint = tint); Text(label, fontSize = 10.sp, color = tint, fontWeight = FontWeight.Medium) } }

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
internal fun PullsTab(pulls: List<GHPullRequest>, repo: GHRepo, onRefresh: () -> Unit) { val context = LocalContext.current; val scope = rememberCoroutineScope()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) { items(pulls) { pr ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Rounded.CallMerge, null, Modifier.size(20.dp), tint = when { pr.merged -> Color(0xFF8957E5); pr.state == "open" -> Color(0xFF34C759); else -> Color(0xFF8E8E93) })
            Column(Modifier.weight(1f)) { Text(pr.title, fontSize = 14.sp, color = TextPrimary, maxLines = 2); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Text("#${pr.number}", fontSize = 11.sp, color = TextTertiary); Text("${pr.head} \u2192 ${pr.base}", fontSize = 11.sp, color = Blue); Text(pr.author, fontSize = 11.sp, color = TextSecondary) }
                if (pr.state == "open" && !pr.merged) { Spacer(Modifier.height(6.dp)); Box(Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFF34C759).copy(0.1f)).clickable { scope.launch { val ok = GitHubManager.mergePullRequest(context, repo.owner, repo.name, pr.number); Toast.makeText(context, if (ok) Strings.ghMerged else Strings.error, Toast.LENGTH_SHORT).show(); if (ok) onRefresh() } }.padding(horizontal = 10.dp, vertical = 4.dp)) { Text(Strings.ghMerge, fontSize = 11.sp, color = Color(0xFF34C759), fontWeight = FontWeight.SemiBold) } }
            }
        }; Box(Modifier.fillMaxWidth().padding(start = 46.dp).height(0.5.dp).background(SeparatorColor))
    } }
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
internal fun IssueDetailScreen(repo: GHRepo, issueNumber: Int, onBack: () -> Unit) { val context = LocalContext.current; val scope = rememberCoroutineScope()
    var detail by remember { mutableStateOf<GHIssueDetail?>(null) }; var comments by remember { mutableStateOf<List<GHComment>>(emptyList()) }; var loading by remember { mutableStateOf(true) }; var newComment by remember { mutableStateOf("") }; var sending by remember { mutableStateOf(false) }
    LaunchedEffect(issueNumber) { loading = true; detail = GitHubManager.getIssueDetail(context, repo.owner, repo.name, issueNumber); comments = GitHubManager.getIssueComments(context, repo.owner, repo.name, issueNumber); loading = false }
    Column(Modifier.fillMaxSize().background(SurfaceLight)) {
        GHTopBar("#$issueNumber", subtitle = detail?.title, onBack = onBack) { if (detail != null) { val isOpen = detail!!.state == "open"
            IconButton(onClick = { scope.launch { val ok = if (isOpen) GitHubManager.closeIssue(context, repo.owner, repo.name, issueNumber) else GitHubManager.reopenIssue(context, repo.owner, repo.name, issueNumber); if (ok) detail = GitHubManager.getIssueDetail(context, repo.owner, repo.name, issueNumber) } }) { Icon(if (isOpen) Icons.Rounded.Close else Icons.Rounded.Refresh, null, Modifier.size(20.dp), tint = if (isOpen) Color(0xFFFF3B30) else Color(0xFF34C759)) } } }
        if (loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Blue, modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp) }
        else { LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp)) {
            if (detail != null) item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { AsyncImage(detail!!.avatarUrl, null, Modifier.size(28.dp).clip(CircleShape)); Text(detail!!.author, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Blue); Text(detail!!.createdAt.take(10), fontSize = 11.sp, color = TextTertiary)
                    Box(Modifier.background(if (detail!!.state == "open") Color(0xFF34C759).copy(0.1f) else Color(0xFF8E8E93).copy(0.1f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) { Text(if (detail!!.state == "open") Strings.ghOpen else Strings.ghClosed, fontSize = 10.sp, color = if (detail!!.state == "open") Color(0xFF34C759) else Color(0xFF8E8E93)) } }
                if (detail!!.labels.isNotEmpty()) { Spacer(Modifier.height(6.dp)); Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { detail!!.labels.forEach { Text(it, fontSize = 10.sp, color = Blue, modifier = Modifier.background(Blue.copy(0.1f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) } } }
                if (detail!!.body.isNotBlank()) { Spacer(Modifier.height(10.dp)); Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(SurfaceWhite).padding(12.dp)) { Text(detail!!.body, fontSize = 13.sp, color = TextPrimary, lineHeight = 20.sp) } }
                Spacer(Modifier.height(16.dp)); Box(Modifier.fillMaxWidth().height(0.5.dp).background(SeparatorColor)); Spacer(Modifier.height(12.dp))
            }
            if (comments.isEmpty()) item { Text(Strings.ghNoComments, fontSize = 13.sp, color = TextTertiary) }
            items(comments) { c -> Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) { AsyncImage(c.avatarUrl, null, Modifier.size(24.dp).clip(CircleShape)); Column(Modifier.weight(1f)) { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Text(c.author, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Blue); Text(c.createdAt.take(10), fontSize = 10.sp, color = TextTertiary) }; Text(c.body, fontSize = 13.sp, color = TextPrimary, modifier = Modifier.padding(top = 2.dp)) } }; Box(Modifier.fillMaxWidth().padding(start = 32.dp).height(0.5.dp).background(SeparatorColor)) }
        }
        Row(Modifier.fillMaxWidth().background(SurfaceWhite).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(SurfaceLight).padding(horizontal = 12.dp, vertical = 8.dp)) { if (newComment.isEmpty()) Text(Strings.ghAddComment, color = TextTertiary, fontSize = 14.sp); BasicTextField(newComment, { newComment = it }, textStyle = androidx.compose.ui.text.TextStyle(color = TextPrimary, fontSize = 14.sp), modifier = Modifier.fillMaxWidth()) }
            IconButton(onClick = { if (newComment.isBlank() || sending) return@IconButton; sending = true; scope.launch { GitHubManager.addComment(context, repo.owner, repo.name, issueNumber, newComment); comments = GitHubManager.getIssueComments(context, repo.owner, repo.name, issueNumber); newComment = ""; sending = false } }, enabled = !sending) { Icon(Icons.Rounded.Send, null, Modifier.size(20.dp), tint = if (sending) TextTertiary else Blue) }
        } }
    }
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

@Composable
internal fun EditFileScreen(repo: GHRepo, file: GHContent, branch: String, onBack: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    var textFieldValue by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue("")) }
    var commitMsg by remember { mutableStateOf("Update ${file.name}") }
    var loading by remember { mutableStateOf(true) }; var saving by remember { mutableStateOf(false) }
    val ext = file.name.substringAfterLast(".", "").lowercase()
    val clipMgr = remember { context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager }

    LaunchedEffect(file) {
        val raw = GitHubManager.getFileContent(context, repo.owner, repo.name, file.path)
        textFieldValue = androidx.compose.ui.text.input.TextFieldValue(raw)
        loading = false
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF0D1117))) {
        GHTopBar(file.name, subtitle = Strings.ghEditFile, onBack = onBack) {
            // Select All — actually selects text in the field
            IconButton(onClick = {
                textFieldValue = textFieldValue.copy(
                    selection = androidx.compose.ui.text.TextRange(0, textFieldValue.text.length)
                )
            }) { Icon(Icons.Rounded.SelectAll, null, Modifier.size(20.dp), tint = Blue) }
            // Copy All
            IconButton(onClick = {
                val clip = android.content.ClipData.newPlainText("code", textFieldValue.text)
                clipMgr.setPrimaryClip(clip)
                Toast.makeText(context, "Copied ${textFieldValue.text.lines().size} lines", Toast.LENGTH_SHORT).show()
            }) { Icon(Icons.Rounded.ContentCopy, null, Modifier.size(20.dp), tint = Blue) }
            // Paste — replaces selected text or appends
            IconButton(onClick = {
                val clip = clipMgr.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    val paste = clip.getItemAt(0).text?.toString() ?: return@IconButton
                    val sel = textFieldValue.selection
                    val newText = textFieldValue.text.replaceRange(sel.min, sel.max, paste)
                    val newCursor = sel.min + paste.length
                    textFieldValue = androidx.compose.ui.text.input.TextFieldValue(
                        text = newText,
                        selection = androidx.compose.ui.text.TextRange(newCursor)
                    )
                }
            }) { Icon(Icons.Rounded.ContentPaste, null, Modifier.size(20.dp), tint = Blue) }
            // Save
            IconButton(onClick = {
                if (saving) return@IconButton; saving = true
                scope.launch {
                    val ok = GitHubManager.uploadFile(context, repo.owner, repo.name, file.path, textFieldValue.text.toByteArray(), commitMsg, branch, file.sha)
                    Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                    if (ok) onSaved(); saving = false
                }
            }, enabled = !saving) {
                if (saving) CircularProgressIndicator(Modifier.size(18.dp), color = Blue, strokeWidth = 2.dp)
                else Icon(Icons.Rounded.Save, null, Modifier.size(20.dp), tint = Color(0xFF34C759))
            }
        }
        // Commit message
        OutlinedTextField(commitMsg, { commitMsg = it }, label = { Text(Strings.ghCommitMsg) }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = Color(0xFFC9D1D9)),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Blue, unfocusedBorderColor = Color(0xFF30363D),
                cursorColor = Blue, focusedLabelColor = Blue, unfocusedLabelColor = Color(0xFF8B949E)
            ))
        // File info
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("${textFieldValue.text.lines().size} lines", fontSize = 11.sp, color = Color(0xFF8B949E))
            Text("${textFieldValue.text.length} chars", fontSize = 11.sp, color = Color(0xFF8B949E))
            Text(ext.uppercase(), fontSize = 11.sp, color = Color(0xFF58A6FF), fontWeight = FontWeight.SemiBold)
            if (textFieldValue.selection.length > 0) {
                Text("${textFieldValue.selection.length} selected", fontSize = 11.sp, color = Color(0xFF34C759), fontWeight = FontWeight.SemiBold)
            }
        }
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(Color(0xFF30363D)))
        // Editor
        if (loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Blue, modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp) }
        else {
            Row(Modifier.fillMaxSize()) {
                // Line numbers column
                val lineCount = textFieldValue.text.lines().size
                val scrollState = rememberScrollState()
                Column(Modifier.width(40.dp).verticalScroll(scrollState).background(Color(0xFF0D1117)).padding(top = 8.dp, end = 4.dp)) {
                    for (i in 1..lineCount) {
                        Text("$i", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF484F58),
                            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End)
                    }
                }
                Box(Modifier.width(0.5.dp).fillMaxHeight().background(Color(0xFF30363D)))
                // Text editor with real selection support
                BasicTextField(
                    textFieldValue, { textFieldValue = it },
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = Color(0xFFC9D1D9), fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace, lineHeight = 18.sp
                    ),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(Blue),
                    modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(start = 8.dp, top = 8.dp, end = 8.dp)
                )
            }
        }
    }
}
