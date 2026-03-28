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
private val LocalGHCompact = compositionLocalOf { false }

@Composable
fun GitHubScreen(onBack: () -> Unit, onMinimize: () -> Unit = {}, compact: Boolean = false) {
    CompositionLocalProvider(LocalGHCompact provides compact) {
    val context = LocalContext.current
    var isLoggedIn by remember { mutableStateOf(GitHubManager.isLoggedIn(context)) }
    var user by remember { mutableStateOf(GitHubManager.getCachedUser(context)) }
    var selectedRepo by remember { mutableStateOf<GHRepo?>(null) }
    var showGists by remember { mutableStateOf(false) }
    LaunchedEffect(isLoggedIn) { if (isLoggedIn) user = GitHubManager.getUser(context) }
    when {
        !isLoggedIn -> LoginScreen(onBack, onMinimize) { GitHubManager.saveToken(context, it); isLoggedIn = true }
        showGists -> GistsScreen({ showGists = false }, onMinimize)
        selectedRepo != null -> RepoDetailScreen(selectedRepo!!, { selectedRepo = null }, onMinimize)
        else -> ReposScreen(user, onBack, onMinimize, { GitHubManager.logout(context); isLoggedIn = false; user = null }, { selectedRepo = it }, { showGists = true })
    }
    }
}

@Composable
private fun GHTopBar(title: String, subtitle: String? = null, onBack: () -> Unit, onMinimize: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    val compact = LocalGHCompact.current
    Row(Modifier.fillMaxWidth().background(SurfaceWhite).padding(
        top = if (compact) 4.dp else 48.dp, start = if (compact) 2.dp else 4.dp,
        end = if (compact) 4.dp else 8.dp, bottom = if (compact) 4.dp else 14.dp
    ), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, modifier = Modifier.size(if (compact) 32.dp else 48.dp)) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, Modifier.size(if (compact) 16.dp else 22.dp), tint = Blue)
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = if (compact) 15.sp else 24.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null && !compact) Text(subtitle, fontSize = 13.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (onMinimize != null && !compact) IconButton(onClick = onMinimize) { Icon(Icons.Rounded.PictureInPictureAlt, null, Modifier.size(20.dp), tint = Blue) }
        actions()
    }
}

@Composable
private fun LoginScreen(onBack: () -> Unit, onMinimize: () -> Unit, onLogin: (String) -> Unit) {
    var token by remember { mutableStateOf("") }; var testing by remember { mutableStateOf(false) }; var error by remember { mutableStateOf("") }
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().background(SurfaceLight)) {
        GHTopBar("GitHub", onBack = onBack, onMinimize = onMinimize)
        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(Modifier.size(80.dp).clip(CircleShape).background(TextPrimary), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Code, null, Modifier.size(40.dp), tint = SurfaceLight) }
            Spacer(Modifier.height(24.dp)); Text("GitHub", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(8.dp)); Text(Strings.ghLoginDesc, fontSize = 14.sp, color = TextSecondary, modifier = Modifier.padding(horizontal = 16.dp), textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(token, { token = it; error = "" }, label = { Text("Personal Access Token") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), isError = error.isNotBlank())
            if (error.isNotBlank()) Text(error, color = Color(0xFFFF3B30), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(8.dp)); Text(Strings.ghTokenHint, fontSize = 11.sp, color = TextTertiary, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(20.dp))
            Button(onClick = { if (token.isBlank()) { error = "Token required"; return@Button }; testing = true; error = ""
                scope.launch { GitHubManager.saveToken(context, token); val u = GitHubManager.getUser(context); if (u != null) onLogin(token) else { error = "Invalid token"; GitHubManager.logout(context) }; testing = false }
            }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = TextPrimary), enabled = !testing) {
                if (testing) CircularProgressIndicator(Modifier.size(20.dp), color = SurfaceLight, strokeWidth = 2.dp) else Text(Strings.ghSignIn, color = SurfaceLight, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ReposScreen(user: GHUser?, onBack: () -> Unit, onMinimize: () -> Unit, onLogout: () -> Unit, onRepoClick: (GHRepo) -> Unit, onGists: () -> Unit) {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    var repos by remember { mutableStateOf<List<GHRepo>>(emptyList()) }; var loading by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }; var showCreate by remember { mutableStateOf(false) }
    var searchPublic by remember { mutableStateOf(false) }; var publicResults by remember { mutableStateOf<List<GHRepo>>(emptyList()) }
    var showNotifications by remember { mutableStateOf(false) }
    var showStarred by remember { mutableStateOf(false) }
    var showOrgs by remember { mutableStateOf(false) }
    var viewProfile by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { repos = GitHubManager.getRepos(context); loading = false }
    LaunchedEffect(query, searchPublic) { if (searchPublic && query.length >= 2) publicResults = GitHubManager.searchRepos(context, query) }
    val filtered = remember(repos, query, searchPublic) {
        if (searchPublic) publicResults else if (query.isNotBlank()) repos.filter { it.name.contains(query, true) || it.description.contains(query, true) } else repos
    }
    if (showNotifications) { NotificationsScreen(onBack = { showNotifications = false }); return }
    if (showStarred) { StarredScreen(onBack = { showStarred = false }, onRepoClick = { showStarred = false; onRepoClick(it) }); return }
    if (showOrgs) { OrgsScreen(onBack = { showOrgs = false }, onRepoClick = { showOrgs = false; onRepoClick(it) }); return }
    if (viewProfile != null) { ProfileScreen(username = viewProfile!!, onBack = { viewProfile = null }, onRepoClick = { viewProfile = null; onRepoClick(it) }); return }
    Column(Modifier.fillMaxSize().background(SurfaceLight)) {
        GHTopBar("GitHub", onBack = onBack, onMinimize = onMinimize) {
            IconButton(onClick = { showNotifications = true }) { Icon(Icons.Rounded.Notifications, null, Modifier.size(20.dp), tint = Blue) }
            IconButton(onClick = onGists) { Icon(Icons.Rounded.Description, null, Modifier.size(20.dp), tint = Blue) }
            IconButton(onClick = { showCreate = true }) { Icon(Icons.Rounded.Add, null, Modifier.size(22.dp), tint = Blue) }
            IconButton(onClick = onLogout) { Icon(Icons.Rounded.Logout, null, Modifier.size(20.dp), tint = Color(0xFFFF3B30)) }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            if (user != null) {
                item { Box(Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(16.dp)).background(SurfaceWhite).padding(16.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(user.avatarUrl, user.login, Modifier.size(56.dp).clip(CircleShape))
                        Column(Modifier.weight(1f)) { Text(user.name.ifBlank { user.login }, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary); Text("@${user.login}", fontSize = 13.sp, color = TextSecondary); if (user.bio.isNotBlank()) Text(user.bio, fontSize = 12.sp, color = TextTertiary, maxLines = 2) }
                    }
                } }
                item { Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatBox(Strings.ghRepos, "${user.publicRepos + user.privateRepos}", Modifier.weight(1f)); StatBox(Strings.ghFollowers, "${user.followers}", Modifier.weight(1f)); StatBox(Strings.ghFollowing, "${user.following}", Modifier.weight(1f))
                } }
            }
            // Quick actions row
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    QuickChip(Icons.Rounded.Star, Strings.ghStarredRepos) { showStarred = true }
                    QuickChip(Icons.Rounded.Business, Strings.ghOrganizations) { showOrgs = true }
                    QuickChip(Icons.Rounded.Person, Strings.ghProfile) { if (user != null) viewProfile = user.login }
                }
            }
            item { Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(SurfaceWhite).padding(horizontal = 12.dp, vertical = 10.dp)) {
                    if (query.isEmpty()) Text(if (searchPublic) Strings.ghSearchPublic else Strings.ghSearchRepos, color = TextTertiary, fontSize = 14.sp)
                    BasicTextField(query, { query = it }, textStyle = androidx.compose.ui.text.TextStyle(color = TextPrimary, fontSize = 14.sp), singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(if (searchPublic) Blue.copy(0.15f) else SurfaceWhite).clickable { searchPublic = !searchPublic; query = "" }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Public, null, Modifier.size(18.dp), tint = if (searchPublic) Blue else TextSecondary)
                }
            } }
            if (loading) { item { Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Blue, modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp) } } }
            else items(filtered) { repo -> RepoCard(repo) { onRepoClick(repo) } }
        }
    }
    if (showCreate) CreateRepoDialog({ showCreate = false }) { n, d, p -> scope.launch { val ok = GitHubManager.createRepo(context, n, d, p); Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show(); if (ok) repos = GitHubManager.getRepos(context); showCreate = false } }
}

@Composable private fun StatBox(label: String, value: String, modifier: Modifier) { Column(modifier.clip(RoundedCornerShape(10.dp)).background(SurfaceWhite).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary); Text(label, fontSize = 11.sp, color = TextSecondary) } }

@Composable
private fun RepoCard(repo: GHRepo, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(36.dp).background((if (repo.isPrivate) Color(0xFFFF9F0A) else Blue).copy(0.1f), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
            Icon(if (repo.isPrivate) Icons.Rounded.Lock else Icons.Rounded.FolderOpen, null, Modifier.size(20.dp), tint = if (repo.isPrivate) Color(0xFFFF9F0A) else Blue)
        }
        Column(Modifier.weight(1f)) {
            Text(repo.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (repo.description.isNotBlank()) Text(repo.description, fontSize = 12.sp, color = TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 4.dp)) {
                if (repo.language.isNotBlank()) Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(8.dp).clip(CircleShape).background(langColor(repo.language))); Text(repo.language, fontSize = 11.sp, color = TextSecondary) }
                if (repo.stars > 0) Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.Star, null, Modifier.size(12.dp), tint = Color(0xFFFFCC00)); Text("${repo.stars}", fontSize = 11.sp, color = TextSecondary) }
                if (repo.forks > 0) Text("\u2491 ${repo.forks}", fontSize = 11.sp, color = TextSecondary)
                if (repo.isFork) Text("fork", fontSize = 10.sp, color = TextTertiary)
            }
        }
    }
}

private enum class RepoTab { FILES, COMMITS, ISSUES, PULLS, RELEASES, ACTIONS, README, CODE_SEARCH }

@Composable
private fun RepoDetailScreen(repo: GHRepo, onBack: () -> Unit, onMinimize: () -> Unit = {}) {
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

    LaunchedEffect(Unit) { isStarred = GitHubManager.isStarred(context, repo.owner, repo.name); isWatching = GitHubManager.isWatching(context, repo.owner, repo.name); branches = GitHubManager.getBranches(context, repo.owner, repo.name) }
    LaunchedEffect(selectedTab, currentPath, selectedBranch) { loading = true; when (selectedTab) {
        RepoTab.FILES -> contents = GitHubManager.getRepoContents(context, repo.owner, repo.name, currentPath)
        RepoTab.COMMITS -> commits = GitHubManager.getCommits(context, repo.owner, repo.name)
        RepoTab.ISSUES -> issues = GitHubManager.getIssues(context, repo.owner, repo.name)
        RepoTab.PULLS -> pulls = GitHubManager.getPullRequests(context, repo.owner, repo.name)
        RepoTab.RELEASES -> releases = GitHubManager.getReleases(context, repo.owner, repo.name)
        RepoTab.ACTIONS -> { workflowRuns = GitHubManager.getWorkflowRuns(context, repo.owner, repo.name); workflows = GitHubManager.getWorkflows(context, repo.owner, repo.name) }
        RepoTab.README -> { readme = GitHubManager.getReadme(context, repo.owner, repo.name); languages = GitHubManager.getLanguages(context, repo.owner, repo.name); contributors = GitHubManager.getContributors(context, repo.owner, repo.name) }
        RepoTab.CODE_SEARCH -> { /* searches on demand */ }
    }; loading = false }

    if (selectedIssue != null) { IssueDetailScreen(repo, selectedIssue!!.number) { selectedIssue = null }; return }
    if (selectedCommitSha != null) { CommitDiffScreen(repo, selectedCommitSha!!) { selectedCommitSha = null }; return }
    if (selectedRunId != null) { WorkflowRunDetailScreen(repo, selectedRunId!!) { selectedRunId = null }; return }
    if (fileContent != null) {
        Column(Modifier.fillMaxSize().background(Color(0xFF1C1C1E))) {
            GHTopBar(currentPath.substringAfterLast("/"), onBack = { fileContent = null }) {
                IconButton(onClick = { scope.launch { val dest = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "GlassFiles_Git/${currentPath.substringAfterLast("/")}"); val ok = GitHubManager.downloadFile(context, repo.owner, repo.name, currentPath, dest); Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show() } }) { Icon(Icons.Rounded.Download, null, Modifier.size(20.dp), tint = Blue) }
            }
            val ext = currentPath.substringAfterLast(".", "")
            Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()).padding(10.dp)) {
                SyntaxHighlightedText(fileContent!!, ext)
            }
        }; return
    }
    if (editingFile != null) { EditFileScreen(repo, editingFile!!, selectedBranch, { editingFile = null }) { editingFile = null; scope.launch { contents = GitHubManager.getRepoContents(context, repo.owner, repo.name, currentPath) } }; return }

    Column(Modifier.fillMaxSize().background(SurfaceLight)) {
        GHTopBar(repo.name, subtitle = if (currentPath.isNotBlank()) currentPath else repo.owner, onBack = { if (currentPath.isNotBlank() && selectedTab == RepoTab.FILES) currentPath = currentPath.substringBeforeLast("/", "") else onBack() }, onMinimize = onMinimize) {
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
            RepoTab.COMMITS -> CommitsTab(commits) { selectedCommitSha = it.sha }
            RepoTab.ISSUES -> IssuesTab(issues) { selectedIssue = it }
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
private fun FilesTab(contents: List<GHContent>, onDirClick: (GHContent) -> Unit, onFileClick: (GHContent) -> Unit, onEdit: (GHContent) -> Unit, onDelete: (GHContent) -> Unit, onDownload: (GHContent) -> Unit) {
    var expanded by remember { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) { items(contents) { item -> Column {
        Row(Modifier.fillMaxWidth().clickable { if (item.type == "dir") onDirClick(item) else expanded = if (expanded == item.path) null else item.path }.padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (item.type == "dir") Icons.Rounded.Folder else Icons.Rounded.InsertDriveFile, null, Modifier.size(22.dp), tint = if (item.type == "dir") FolderBlue else TextSecondary)
            Text(item.name, fontSize = 14.sp, color = TextPrimary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (item.type != "dir" && item.size > 0) Text(fmtSize(item.size), fontSize = 11.sp, color = TextTertiary)
        }
        AnimatedVisibility(expanded == item.path && item.type != "dir") { Row(Modifier.fillMaxWidth().padding(start = 50.dp, end = 16.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(Icons.Rounded.Visibility, "View") { onFileClick(item) }; Chip(Icons.Rounded.Edit, Strings.ghEditFile) { onEdit(item) }; Chip(Icons.Rounded.Download, Strings.ghDownloadFile) { onDownload(item) }; Chip(Icons.Rounded.Delete, Strings.ghDeleteFile, Color(0xFFFF3B30)) { onDelete(item) }
        } }
        Box(Modifier.fillMaxWidth().padding(start = 50.dp).height(0.5.dp).background(SeparatorColor))
    } } }
}

@Composable private fun Chip(icon: ImageVector, label: String, tint: Color = Blue, onClick: () -> Unit) { Row(Modifier.clip(RoundedCornerShape(6.dp)).background(tint.copy(0.08f)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) { Icon(icon, null, Modifier.size(12.dp), tint = tint); Text(label, fontSize = 10.sp, color = tint, fontWeight = FontWeight.Medium) } }

@Composable
private fun CommitsTab(commits: List<GHCommit>, onClick: (GHCommit) -> Unit) { LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) { items(commits) { c ->
    Row(Modifier.fillMaxWidth().clickable { onClick(c) }.padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(Blue.copy(0.1f)), contentAlignment = Alignment.Center) { Text(c.sha.take(2), fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = Blue) }
        Column(Modifier.weight(1f)) { Text(c.message.lines().first(), fontSize = 14.sp, color = TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Text(c.author, fontSize = 11.sp, color = Blue); Text(c.sha, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = TextTertiary); Text(c.date.take(10), fontSize = 11.sp, color = TextTertiary) } }
        Icon(Icons.Rounded.ChevronRight, null, Modifier.size(16.dp), tint = TextTertiary)
    }; Box(Modifier.fillMaxWidth().padding(start = 58.dp).height(0.5.dp).background(SeparatorColor))
} } }

@Composable
private fun IssuesTab(issues: List<GHIssue>, onClick: (GHIssue) -> Unit) { LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) { items(issues) { issue ->
    Row(Modifier.fillMaxWidth().clickable { onClick(issue) }.padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Icon(if (issue.isPR) Icons.Rounded.CallMerge else if (issue.state == "open") Icons.Rounded.RadioButtonUnchecked else Icons.Rounded.CheckCircle, null, Modifier.size(20.dp), tint = if (issue.state == "open") Color(0xFF34C759) else Color(0xFF8E8E93))
        Column(Modifier.weight(1f)) { Text(issue.title, fontSize = 14.sp, color = TextPrimary, maxLines = 2); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Text("#${issue.number}", fontSize = 11.sp, color = TextTertiary); Text(issue.author, fontSize = 11.sp, color = Blue); if (issue.comments > 0) Text("\uD83D\uDCAC ${issue.comments}", fontSize = 11.sp, color = TextTertiary) } }
        Icon(Icons.Rounded.ChevronRight, null, Modifier.size(16.dp), tint = TextTertiary)
    }; Box(Modifier.fillMaxWidth().padding(start = 46.dp).height(0.5.dp).background(SeparatorColor))
} } }

@Composable
private fun PullsTab(pulls: List<GHPullRequest>, repo: GHRepo, onRefresh: () -> Unit) { val context = LocalContext.current; val scope = rememberCoroutineScope()
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
private fun ReleasesTab(releases: List<GHRelease>, repo: GHRepo) { val context = LocalContext.current; val scope = rememberCoroutineScope()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) { items(releases) { r -> Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Icon(Icons.Rounded.NewReleases, null, Modifier.size(20.dp), tint = if (r.prerelease) Color(0xFFFF9500) else Blue); Text(r.name.ifBlank { r.tag }, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary); if (r.prerelease) Text(Strings.ghPrerelease, fontSize = 10.sp, color = Color(0xFFFF9500), modifier = Modifier.background(Color(0xFFFF9500).copy(0.1f), RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 1.dp)) }
        Text(r.tag, fontSize = 12.sp, color = TextSecondary)
        if (r.body.isNotBlank()) Text(r.body.take(200), fontSize = 12.sp, color = TextTertiary, modifier = Modifier.padding(top = 4.dp), maxLines = 4)
        r.assets.forEach { a -> Spacer(Modifier.height(4.dp)); Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(SurfaceWhite).clickable { scope.launch { val dest = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "GlassFiles_Git/${a.name}"); GitHubManager.downloadFile(context, repo.owner, repo.name, a.downloadUrl, dest) } }.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.Archive, null, Modifier.size(16.dp), tint = Blue); Column(Modifier.weight(1f)) { Text(a.name, fontSize = 12.sp, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis); Text("${fmtSize(a.size)} \u00B7 ${a.downloadCount} dl", fontSize = 10.sp, color = TextTertiary) }; Icon(Icons.Rounded.Download, null, Modifier.size(16.dp), tint = Blue) } }
    }; Box(Modifier.fillMaxWidth().height(0.5.dp).background(SeparatorColor)) } }
}

@Composable
private fun ReadmeTab(readme: String?, languages: Map<String, Long>, contributors: List<GHContributor>) { val total = languages.values.sum().toFloat().coerceAtLeast(1f)
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
private fun IssueDetailScreen(repo: GHRepo, issueNumber: Int, onBack: () -> Unit) { val context = LocalContext.current; val scope = rememberCoroutineScope()
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
private fun CommitDiffScreen(repo: GHRepo, sha: String, onBack: () -> Unit) { val context = LocalContext.current; var detail by remember { mutableStateOf<GHCommitDetail?>(null) }; var loading by remember { mutableStateOf(true) }
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
private fun EditFileScreen(repo: GHRepo, file: GHContent, branch: String, onBack: () -> Unit, onSaved: () -> Unit) { val context = LocalContext.current; val scope = rememberCoroutineScope()
    var content by remember { mutableStateOf("") }; var commitMsg by remember { mutableStateOf("Update ${file.name}") }; var loading by remember { mutableStateOf(true) }; var saving by remember { mutableStateOf(false) }
    LaunchedEffect(file) { content = GitHubManager.getFileContent(context, repo.owner, repo.name, file.path); loading = false }
    Column(Modifier.fillMaxSize().background(SurfaceLight)) { GHTopBar(file.name, subtitle = Strings.ghEditFile, onBack = onBack) {
        IconButton(onClick = { if (saving) return@IconButton; saving = true; scope.launch { val ok = GitHubManager.uploadFile(context, repo.owner, repo.name, file.path, content.toByteArray(), commitMsg, branch, file.sha); Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show(); if (ok) onSaved(); saving = false } }, enabled = !saving) { if (saving) CircularProgressIndicator(Modifier.size(18.dp), color = Blue, strokeWidth = 2.dp) else Icon(Icons.Rounded.Save, null, Modifier.size(20.dp), tint = Blue) } }
        if (loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Blue, modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp) }
        else { OutlinedTextField(commitMsg, { commitMsg = it }, label = { Text(Strings.ghCommitMsg) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp))
            BasicTextField(content, { content = it }, textStyle = androidx.compose.ui.text.TextStyle(color = TextPrimary, fontSize = 12.sp, fontFamily = FontFamily.Monospace, lineHeight = 18.sp), modifier = Modifier.fillMaxSize().padding(10.dp).verticalScroll(rememberScrollState())) }
    }
}

@Composable
private fun GistsScreen(onBack: () -> Unit, onMinimize: () -> Unit) { val context = LocalContext.current; val scope = rememberCoroutineScope()
    var gists by remember { mutableStateOf<List<GHGist>>(emptyList()) }; var loading by remember { mutableStateOf(true) }; var showCreate by remember { mutableStateOf(false) }
    var viewingGist by remember { mutableStateOf<GHGist?>(null) }; var gistContent by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    LaunchedEffect(Unit) { gists = GitHubManager.getGists(context); loading = false }
    if (viewingGist != null) { Column(Modifier.fillMaxSize().background(SurfaceLight)) { GHTopBar(viewingGist!!.description.ifBlank { "Gist" }, onBack = { viewingGist = null; gistContent = emptyMap() }) { IconButton(onClick = { scope.launch { GitHubManager.deleteGist(context, viewingGist!!.id); gists = GitHubManager.getGists(context); viewingGist = null; gistContent = emptyMap() } }) { Icon(Icons.Rounded.Delete, null, Modifier.size(20.dp), tint = Color(0xFFFF3B30)) } }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) { gistContent.forEach { (n, t) -> item { Text(n, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Blue); Spacer(Modifier.height(4.dp)); Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFF1E1E22)).horizontalScroll(rememberScrollState()).padding(10.dp)) { Text(t, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = Color(0xFFD4D4D4), lineHeight = 18.sp) }; Spacer(Modifier.height(12.dp)) } } } }; return }
    Column(Modifier.fillMaxSize().background(SurfaceLight)) { GHTopBar("Gists", onBack = onBack, onMinimize = onMinimize) { IconButton(onClick = { showCreate = true }) { Icon(Icons.Rounded.Add, null, Modifier.size(22.dp), tint = Blue) } }
        if (loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Blue, modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp) }
        else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) { items(gists) { g -> Row(Modifier.fillMaxWidth().clickable { scope.launch { gistContent = GitHubManager.getGistContent(context, g.id); viewingGist = g } }.padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Rounded.Description, null, Modifier.size(20.dp), tint = Blue); Column(Modifier.weight(1f)) { Text(g.description.ifBlank { g.files.firstOrNull() ?: "Gist" }, fontSize = 14.sp, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Text("${g.files.size} files", fontSize = 11.sp, color = TextSecondary); Text(if (g.isPublic) Strings.ghPublic else Strings.ghPrivate, fontSize = 11.sp, color = TextTertiary); Text(g.updatedAt.take(10), fontSize = 11.sp, color = TextTertiary) } }
        }; Box(Modifier.fillMaxWidth().padding(start = 46.dp).height(0.5.dp).background(SeparatorColor)) } }
    }
    if (showCreate) CreateGistDialog({ showCreate = false }) { showCreate = false; scope.launch { gists = GitHubManager.getGists(context) } }
}

// Dialogs
@Composable private fun CreateRepoDialog(onDismiss: () -> Unit, onCreate: (String, String, Boolean) -> Unit) { var n by remember { mutableStateOf("") }; var d by remember { mutableStateOf("") }; var p by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, containerColor = SurfaceWhite, title = { Text(Strings.ghNewRepo, fontWeight = FontWeight.Bold, color = TextPrimary) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { OutlinedTextField(n, { n = it }, label = { Text(Strings.ghRepoName) }, singleLine = true, modifier = Modifier.fillMaxWidth()); OutlinedTextField(d, { d = it }, label = { Text(Strings.ghRepoDesc) }, modifier = Modifier.fillMaxWidth()); Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Switch(checked = p, onCheckedChange = { p = it }, colors = SwitchDefaults.colors(checkedTrackColor = Blue)); Text(Strings.ghPrivate, fontSize = 14.sp, color = TextPrimary) } } },
        confirmButton = { TextButton(onClick = { if (n.isNotBlank()) onCreate(n, d, p) }) { Text(Strings.create, color = Blue) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.cancel, color = TextSecondary) } }) }

@Composable private fun UploadDialog(repo: GHRepo, curPath: String, branch: String, onDismiss: () -> Unit, onDone: () -> Unit) { var fn by remember { mutableStateOf("") }; var msg by remember { mutableStateOf("Add file") }; var up by remember { mutableStateOf(false) }; val ctx = LocalContext.current; val s = rememberCoroutineScope()
    AlertDialog(onDismissRequest = onDismiss, containerColor = SurfaceWhite, title = { Text(Strings.ghUpload, fontWeight = FontWeight.Bold, color = TextPrimary) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("${Strings.ghPickBranch}: $branch", fontSize = 12.sp, color = TextSecondary); OutlinedTextField(fn, { fn = it }, label = { Text(Strings.ghFilePath) }, singleLine = true, modifier = Modifier.fillMaxWidth(), placeholder = { Text("example.txt") }); if (curPath.isNotBlank()) Text("\u2192 $curPath/$fn", fontSize = 11.sp, color = TextTertiary); OutlinedTextField(msg, { msg = it }, label = { Text(Strings.ghCommitMsg) }, singleLine = true, modifier = Modifier.fillMaxWidth()); if (up) LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Blue) } },
        confirmButton = { TextButton(onClick = { if (fn.isBlank()||up) return@TextButton; up = true; val p = if (curPath.isNotBlank()) "$curPath/$fn" else fn; s.launch { val ok = GitHubManager.uploadFile(ctx, repo.owner, repo.name, p, "".toByteArray(), msg, branch); Toast.makeText(ctx, if (ok) Strings.ghUploaded else Strings.error, Toast.LENGTH_SHORT).show(); if (ok) onDone() else up = false } }, enabled = !up) { Text(Strings.ghUpload, color = Blue) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.cancel, color = TextSecondary) } }) }

@Composable private fun CreateFileDialog(repo: GHRepo, curPath: String, branch: String, onDismiss: () -> Unit, onDone: () -> Unit) { var fn by remember { mutableStateOf("") }; var ct by remember { mutableStateOf("") }; var msg by remember { mutableStateOf("Create file") }; var cr by remember { mutableStateOf(false) }; val ctx = LocalContext.current; val s = rememberCoroutineScope()
    AlertDialog(onDismissRequest = onDismiss, containerColor = SurfaceWhite, title = { Text(Strings.ghCreateFile, fontWeight = FontWeight.Bold, color = TextPrimary) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(fn, { fn = it }, label = { Text(Strings.ghFilePath) }, singleLine = true, modifier = Modifier.fillMaxWidth()); OutlinedTextField(ct, { ct = it }, label = { Text(Strings.ghFileContent) }, modifier = Modifier.fillMaxWidth().height(120.dp), maxLines = 8); OutlinedTextField(msg, { msg = it }, label = { Text(Strings.ghCommitMsg) }, singleLine = true, modifier = Modifier.fillMaxWidth()) } },
        confirmButton = { TextButton(onClick = { if (fn.isBlank()||cr) return@TextButton; cr = true; val p = if (curPath.isNotBlank()) "$curPath/$fn" else fn; s.launch { val ok = GitHubManager.uploadFile(ctx, repo.owner, repo.name, p, ct.toByteArray(), msg, branch); Toast.makeText(ctx, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show(); if (ok) onDone() else cr = false } }, enabled = !cr) { Text(Strings.create, color = Blue) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.cancel, color = TextSecondary) } }) }

@Composable private fun DeleteFileDialog(repo: GHRepo, file: GHContent, branch: String, onDismiss: () -> Unit, onDone: () -> Unit) { var msg by remember { mutableStateOf("Delete ${file.name}") }; val ctx = LocalContext.current; val s = rememberCoroutineScope()
    AlertDialog(onDismissRequest = onDismiss, containerColor = SurfaceWhite, title = { Text(Strings.ghConfirmDelete, fontWeight = FontWeight.Bold, color = TextPrimary) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(file.path, fontSize = 14.sp, color = TextSecondary); OutlinedTextField(msg, { msg = it }, label = { Text(Strings.ghCommitMsg) }, singleLine = true, modifier = Modifier.fillMaxWidth()) } },
        confirmButton = { TextButton(onClick = { s.launch { val ok = GitHubManager.deleteFile(ctx, repo.owner, repo.name, file.path, file.sha, msg, branch); Toast.makeText(ctx, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show(); if (ok) onDone() } }) { Text(Strings.delete, color = Color(0xFFFF3B30)) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.cancel, color = TextSecondary) } }) }

@Composable private fun CreateBranchDialog(repo: GHRepo, branches: List<String>, onDismiss: () -> Unit, onDone: () -> Unit) { var nm by remember { mutableStateOf("") }; var fr by remember { mutableStateOf(repo.defaultBranch) }; val ctx = LocalContext.current; val s = rememberCoroutineScope()
    AlertDialog(onDismissRequest = onDismiss, containerColor = SurfaceWhite, title = { Text(Strings.ghNewBranch, fontWeight = FontWeight.Bold, color = TextPrimary) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(nm, { nm = it }, label = { Text(Strings.ghBranchName) }, singleLine = true, modifier = Modifier.fillMaxWidth()); Text(Strings.ghFromBranch, fontSize = 12.sp, color = TextSecondary)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { branches.forEach { b -> Box(Modifier.clip(RoundedCornerShape(6.dp)).background(if (b == fr) Blue.copy(0.15f) else SurfaceLight).clickable { fr = b }.padding(horizontal = 8.dp, vertical = 4.dp)) { Text(b, fontSize = 12.sp, color = if (b == fr) Blue else TextSecondary) } } } } },
        confirmButton = { TextButton(onClick = { if (nm.isBlank()) return@TextButton; s.launch { val ok = GitHubManager.createBranch(ctx, repo.owner, repo.name, nm, fr); Toast.makeText(ctx, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show(); if (ok) onDone() } }) { Text(Strings.create, color = Blue) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.cancel, color = TextSecondary) } }) }

@Composable private fun CreateIssueDialog(repo: GHRepo, onDismiss: () -> Unit, onDone: () -> Unit) { var t by remember { mutableStateOf("") }; var b by remember { mutableStateOf("") }; val ctx = LocalContext.current; val s = rememberCoroutineScope()
    AlertDialog(onDismissRequest = onDismiss, containerColor = SurfaceWhite, title = { Text(Strings.ghNewIssue, fontWeight = FontWeight.Bold, color = TextPrimary) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(t, { t = it }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth()); OutlinedTextField(b, { b = it }, label = { Text(Strings.ghRepoDesc) }, modifier = Modifier.fillMaxWidth().height(100.dp), maxLines = 6) } },
        confirmButton = { TextButton(onClick = { if (t.isBlank()) return@TextButton; s.launch { val ok = GitHubManager.createIssue(ctx, repo.owner, repo.name, t, b); Toast.makeText(ctx, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show(); if (ok) onDone() } }) { Text(Strings.create, color = Blue) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.cancel, color = TextSecondary) } }) }

@Composable private fun CreatePRDialog(repo: GHRepo, branches: List<String>, onDismiss: () -> Unit, onDone: () -> Unit) { var t by remember { mutableStateOf("") }; var b by remember { mutableStateOf("") }
    var head by remember { mutableStateOf(branches.firstOrNull { it != repo.defaultBranch } ?: "") }; var base by remember { mutableStateOf(repo.defaultBranch) }; val ctx = LocalContext.current; val s = rememberCoroutineScope()
    AlertDialog(onDismissRequest = onDismiss, containerColor = SurfaceWhite, title = { Text(Strings.ghNewPR, fontWeight = FontWeight.Bold, color = TextPrimary) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(t, { t = it }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth()); OutlinedTextField(b, { b = it }, label = { Text(Strings.ghRepoDesc) }, modifier = Modifier.fillMaxWidth().height(80.dp), maxLines = 4)
            Text(Strings.ghHead, fontSize = 12.sp, color = TextSecondary); Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { branches.forEach { br -> BC(br, br == head) { head = br } } }
            Text(Strings.ghBase, fontSize = 12.sp, color = TextSecondary); Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { branches.forEach { br -> BC(br, br == base) { base = br } } } } },
        confirmButton = { TextButton(onClick = { if (t.isBlank()||head==base) return@TextButton; s.launch { val ok = GitHubManager.createPullRequest(ctx, repo.owner, repo.name, t, b, head, base); Toast.makeText(ctx, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show(); if (ok) onDone() } }) { Text(Strings.create, color = Blue) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.cancel, color = TextSecondary) } }) }

@Composable private fun BC(name: String, sel: Boolean, onClick: () -> Unit) { Box(Modifier.clip(RoundedCornerShape(6.dp)).background(if (sel) Blue.copy(0.15f) else SurfaceLight).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 4.dp)) { Text(name, fontSize = 12.sp, color = if (sel) Blue else TextSecondary) } }

@Composable private fun BranchPickerDialog(branches: List<String>, current: String, onSelect: (String) -> Unit, onDismiss: () -> Unit, onCreateBranch: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, containerColor = SurfaceWhite, title = { Text(Strings.ghBranches, fontWeight = FontWeight.Bold, color = TextPrimary) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { branches.forEach { b -> Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(if (b == current) Blue.copy(0.1f) else Color.Transparent).clickable { onSelect(b) }.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Icon(Icons.Rounded.AccountTree, null, Modifier.size(16.dp), tint = if (b == current) Blue else TextSecondary); Text(b, fontSize = 14.sp, color = if (b == current) Blue else TextPrimary, modifier = Modifier.weight(1f)); if (b == current) Icon(Icons.Rounded.Check, null, Modifier.size(16.dp), tint = Blue) } }
            Spacer(Modifier.height(4.dp)); Box(Modifier.fillMaxWidth().height(0.5.dp).background(SeparatorColor)); Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onCreateBranch() }.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Icon(Icons.Rounded.Add, null, Modifier.size(16.dp), tint = Blue); Text(Strings.ghNewBranch, fontSize = 14.sp, color = Blue) } } }, confirmButton = {}) }

@Composable private fun CreateGistDialog(onDismiss: () -> Unit, onDone: () -> Unit) { var d by remember { mutableStateOf("") }; var fn by remember { mutableStateOf("file.txt") }; var ct by remember { mutableStateOf("") }; var pub by remember { mutableStateOf(true) }; val ctx = LocalContext.current; val s = rememberCoroutineScope()
    AlertDialog(onDismissRequest = onDismiss, containerColor = SurfaceWhite, title = { Text(Strings.ghNewGist, fontWeight = FontWeight.Bold, color = TextPrimary) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(d, { d = it }, label = { Text(Strings.ghRepoDesc) }, singleLine = true, modifier = Modifier.fillMaxWidth()); OutlinedTextField(fn, { fn = it }, label = { Text(Strings.ghFilePath) }, singleLine = true, modifier = Modifier.fillMaxWidth()); OutlinedTextField(ct, { ct = it }, label = { Text(Strings.ghFileContent) }, modifier = Modifier.fillMaxWidth().height(120.dp), maxLines = 8); Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Switch(checked = pub, onCheckedChange = { pub = it }, colors = SwitchDefaults.colors(checkedTrackColor = Blue)); Text(Strings.ghPublic, fontSize = 14.sp, color = TextPrimary) } } },
        confirmButton = { TextButton(onClick = { if (fn.isBlank()) return@TextButton; s.launch { val ok = GitHubManager.createGist(ctx, d, pub, mapOf(fn to ct)); Toast.makeText(ctx, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show(); if (ok) onDone() } }) { Text(Strings.create, color = Blue) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.cancel, color = TextSecondary) } }) }

@Composable
private fun DispatchWorkflowDialog(repo: GHRepo, workflows: List<GHWorkflow>, branches: List<String>, onDismiss: () -> Unit, onDone: () -> Unit) {
    var selectedWf by remember { mutableStateOf(workflows.firstOrNull()) }
    var selectedBranch by remember { mutableStateOf(repo.defaultBranch) }
    var dispatching by remember { mutableStateOf(false) }
    val ctx = LocalContext.current; val s = rememberCoroutineScope()

    AlertDialog(onDismissRequest = onDismiss, containerColor = SurfaceWhite,
        title = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.PlayArrow, null, Modifier.size(22.dp), tint = Color(0xFF34C759))
            Text(Strings.ghRunWorkflow, fontWeight = FontWeight.Bold, color = TextPrimary)
        } },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Workflow picker
            Text(Strings.ghWorkflows, fontSize = 12.sp, color = TextSecondary)
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                workflows.forEach { wf ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                        .background(if (selectedWf == wf) Blue.copy(0.1f) else Color.Transparent)
                        .clickable { selectedWf = wf }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.Settings, null, Modifier.size(16.dp), tint = if (selectedWf == wf) Blue else TextSecondary)
                        Column(Modifier.weight(1f)) {
                            Text(wf.name, fontSize = 13.sp, color = if (selectedWf == wf) Blue else TextPrimary, fontWeight = FontWeight.Medium)
                            Text(wf.path, fontSize = 10.sp, color = TextTertiary)
                        }
                        if (selectedWf == wf) Icon(Icons.Rounded.Check, null, Modifier.size(14.dp), tint = Blue)
                    }
                }
            }
            // Branch picker
            Text(Strings.ghPickBranch, fontSize = 12.sp, color = TextSecondary)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                branches.forEach { b -> BC(b, b == selectedBranch) { selectedBranch = b } }
            }
            if (dispatching) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(Modifier.size(16.dp), color = Blue, strokeWidth = 2.dp)
                    Text(Strings.ghRunning, fontSize = 12.sp, color = Blue)
                }
            }
        } },
        confirmButton = { TextButton(onClick = {
            if (selectedWf == null || dispatching) return@TextButton
            dispatching = true
            s.launch {
                val ok = GitHubManager.dispatchWorkflow(ctx, repo.owner, repo.name, selectedWf!!.id, selectedBranch)
                Toast.makeText(ctx, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                dispatching = false
                if (ok) onDone()
            }
        }, enabled = !dispatching) { Text(Strings.ghRunWorkflow, color = if (dispatching) TextTertiary else Color(0xFF34C759), fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.cancel, color = TextSecondary) } })
}

@Composable private fun QuickChip(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(10.dp)).background(SurfaceWhite).border(0.5.dp, SeparatorColor, RoundedCornerShape(10.dp))
        .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, null, Modifier.size(16.dp), tint = Blue)
        Text(label, fontSize = 13.sp, color = TextPrimary)
    }
}

// ═══════════════════════════════════
// Code Search Tab
// ═══════════════════════════════════

@Composable
private fun CodeSearchTab(repo: GHRepo) {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<GHCodeResult>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        // Search bar
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(SurfaceWhite).padding(horizontal = 12.dp, vertical = 10.dp)) {
                if (query.isEmpty()) Text(Strings.ghSearchCodeHint, color = TextTertiary, fontSize = 14.sp)
                BasicTextField(query, { query = it }, textStyle = androidx.compose.ui.text.TextStyle(color = TextPrimary, fontSize = 14.sp), singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(Blue).clickable {
                if (query.length >= 2 && !searching) { searching = true; searched = true
                    scope.launch { results = GitHubManager.searchCode(context, query, repo.owner, repo.name); searching = false }
                }
            }, contentAlignment = Alignment.Center) {
                if (searching) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                else Icon(Icons.Rounded.Search, null, Modifier.size(18.dp), tint = Color.White)
            }
        }

        if (searching) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Blue, modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp) }
        } else if (searched && results.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(Strings.ghNoResults, fontSize = 14.sp, color = TextTertiary) }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                items(results) { r ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Code, null, Modifier.size(18.dp), tint = Blue)
                        Column(Modifier.weight(1f)) {
                            Text(r.name, fontSize = 14.sp, color = TextPrimary, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(r.path, fontSize = 11.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Box(Modifier.fillMaxWidth().padding(start = 44.dp).height(0.5.dp).background(SeparatorColor))
                }
            }
        }
    }
}

// ═══════════════════════════════════
// Starred Repos Screen
// ═══════════════════════════════════

@Composable
private fun StarredScreen(onBack: () -> Unit, onRepoClick: (GHRepo) -> Unit) {
    val context = LocalContext.current
    var repos by remember { mutableStateOf<List<GHRepo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { repos = GitHubManager.getStarredRepos(context); loading = false }

    Column(Modifier.fillMaxSize().background(SurfaceLight)) {
        GHTopBar(Strings.ghStarredRepos, onBack = onBack)
        if (loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Blue, modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp) }
        else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(repos) { repo -> RepoCard(repo) { onRepoClick(repo) } }
        }
    }
}

// ═══════════════════════════════════
// Organizations Screen
// ═══════════════════════════════════

@Composable
private fun OrgsScreen(onBack: () -> Unit, onRepoClick: (GHRepo) -> Unit) {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    var orgs by remember { mutableStateOf<List<GHOrg>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var selectedOrg by remember { mutableStateOf<GHOrg?>(null) }
    var orgRepos by remember { mutableStateOf<List<GHRepo>>(emptyList()) }
    var loadingRepos by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { orgs = GitHubManager.getOrganizations(context); loading = false }
    LaunchedEffect(selectedOrg) {
        if (selectedOrg != null) { loadingRepos = true; orgRepos = GitHubManager.getOrgRepos(context, selectedOrg!!.login); loadingRepos = false }
    }

    // Org repos view
    if (selectedOrg != null) {
        Column(Modifier.fillMaxSize().background(SurfaceLight)) {
            GHTopBar(selectedOrg!!.login, onBack = { selectedOrg = null; orgRepos = emptyList() })
            if (loadingRepos) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Blue, modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp) }
            else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                items(orgRepos) { repo -> RepoCard(repo) { onRepoClick(repo) } }
            }
        }
        return
    }

    Column(Modifier.fillMaxSize().background(SurfaceLight)) {
        GHTopBar(Strings.ghOrganizations, onBack = onBack)
        if (loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Blue, modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp) }
        else if (orgs.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(Strings.ghNoResults, fontSize = 14.sp, color = TextTertiary) }
        else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(orgs) { org ->
                Row(Modifier.fillMaxWidth().clickable { selectedOrg = org }.padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(org.avatarUrl, org.login, Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)))
                    Column(Modifier.weight(1f)) {
                        Text(org.login, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                        if (org.description.isNotBlank()) Text(org.description, fontSize = 12.sp, color = TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(Icons.Rounded.ChevronRight, null, Modifier.size(16.dp), tint = TextTertiary)
                }
                Box(Modifier.fillMaxWidth().padding(start = 68.dp).height(0.5.dp).background(SeparatorColor))
            }
        }
    }
}

// ═══════════════════════════════════
// User Profile Screen
// ═══════════════════════════════════

@Composable
private fun ProfileScreen(username: String, onBack: () -> Unit, onRepoClick: (GHRepo) -> Unit) {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    var profile by remember { mutableStateOf<GHUserProfile?>(null) }
    var repos by remember { mutableStateOf<List<GHRepo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var isFollowing by remember { mutableStateOf(false) }

    LaunchedEffect(username) {
        profile = GitHubManager.getUserProfile(context, username)
        repos = GitHubManager.getUserRepos(context, username)
        isFollowing = GitHubManager.isFollowing(context, username)
        loading = false
    }

    Column(Modifier.fillMaxSize().background(SurfaceLight)) {
        GHTopBar(username, onBack = onBack) {
            // Follow/Unfollow
            Box(Modifier.clip(RoundedCornerShape(8.dp))
                .background(if (isFollowing) Blue.copy(0.12f) else Blue)
                .clickable { scope.launch {
                    if (isFollowing) GitHubManager.unfollowUser(context, username) else GitHubManager.followUser(context, username)
                    isFollowing = !isFollowing
                } }.padding(horizontal = 12.dp, vertical = 6.dp)) {
                Text(if (isFollowing) Strings.ghUnfollow else Strings.ghFollow,
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    color = if (isFollowing) Blue else Color.White)
            }
        }

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Blue, modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp) }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                // Profile card
                if (profile != null) {
                    item {
                        Column(Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(16.dp)).background(SurfaceWhite).padding(16.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                AsyncImage(profile!!.avatarUrl, username, Modifier.size(64.dp).clip(CircleShape))
                                Column {
                                    Text(profile!!.name.ifBlank { username }, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                    Text("@$username", fontSize = 13.sp, color = TextSecondary)
                                    if (profile!!.bio.isNotBlank()) Text(profile!!.bio, fontSize = 12.sp, color = TextTertiary, modifier = Modifier.padding(top = 4.dp))
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            // Info rows
                            if (profile!!.company.isNotBlank()) InfoRow(Icons.Rounded.Business, profile!!.company)
                            if (profile!!.location.isNotBlank()) InfoRow(Icons.Rounded.LocationOn, profile!!.location)
                            if (profile!!.blog.isNotBlank()) InfoRow(Icons.Rounded.Link, profile!!.blog)
                            InfoRow(Icons.Rounded.CalendarToday, "${Strings.ghJoined} ${profile!!.createdAt.take(10)}")
                        }
                    }
                    // Stats
                    item {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatBox(Strings.ghRepos, "${profile!!.publicRepos}", Modifier.weight(1f))
                            StatBox(Strings.ghFollowers, "${profile!!.followers}", Modifier.weight(1f))
                            StatBox(Strings.ghFollowing, "${profile!!.following}", Modifier.weight(1f))
                        }
                    }
                }
                // Repos
                item { Text(Strings.ghRepos, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
                items(repos) { repo -> RepoCard(repo) { onRepoClick(repo) } }
            }
        }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, text: String) {
    Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, Modifier.size(14.dp), tint = TextSecondary)
        Text(text, fontSize = 12.sp, color = TextSecondary)
    }
}

private fun langColor(lang: String): Color = when (lang.lowercase()) { "kotlin" -> Color(0xFFA97BFF); "java" -> Color(0xFFB07219); "python" -> Color(0xFF3572A5); "javascript" -> Color(0xFFF1E05A); "typescript" -> Color(0xFF3178C6); "c" -> Color(0xFF555555); "c++" -> Color(0xFFF34B7D); "swift" -> Color(0xFFFFAC45); "go" -> Color(0xFF00ADD8); "rust" -> Color(0xFFDEA584); "dart" -> Color(0xFF00B4AB); "ruby" -> Color(0xFF701516); "php" -> Color(0xFF4F5D95); "c#" -> Color(0xFF178600); "shell" -> Color(0xFF89E051); "html" -> Color(0xFFE34C26); "css" -> Color(0xFF563D7C); "vue" -> Color(0xFF41B883); else -> Color(0xFF8E8E93) }
private fun fmtSize(b: Long): String = when { b < 1024 -> "$b B"; b < 1024L * 1024 -> "%.1f KB".format(b / 1024.0); else -> "%.1f MB".format(b / (1024.0 * 1024)) }

// ═══════════════════════════════════
// GitHub Actions Tab
// ═══════════════════════════════════

@Composable
private fun ActionsTab(runs: List<GHWorkflowRun>, repo: GHRepo, onRunClick: (GHWorkflowRun) -> Unit) {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    if (runs.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(Strings.ghNoWorkflows, fontSize = 14.sp, color = TextTertiary) }
    } else {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(runs) { run ->
                Row(Modifier.fillMaxWidth().clickable { onRunClick(run) }.padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                    // Status icon
                    val (statusIcon, statusColor) = when {
                        run.status == "in_progress" -> Icons.Rounded.Refresh to Color(0xFFFF9500)
                        run.conclusion == "success" -> Icons.Rounded.CheckCircle to Color(0xFF34C759)
                        run.conclusion == "failure" -> Icons.Rounded.Cancel to Color(0xFFFF3B30)
                        run.conclusion == "cancelled" -> Icons.Rounded.RemoveCircle to Color(0xFF8E8E93)
                        run.status == "queued" -> Icons.Rounded.Schedule to Color(0xFFFF9500)
                        else -> Icons.Rounded.Circle to TextTertiary
                    }
                    Icon(statusIcon, null, Modifier.size(20.dp), tint = statusColor)
                    Column(Modifier.weight(1f)) {
                        Text(run.name, fontSize = 14.sp, color = TextPrimary, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("#${run.runNumber}", fontSize = 11.sp, color = TextTertiary)
                            Text(run.branch, fontSize = 11.sp, color = Blue)
                            Text(run.event, fontSize = 11.sp, color = TextTertiary)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 2.dp)) {
                            Text(run.actor, fontSize = 10.sp, color = TextSecondary)
                            Text(run.createdAt.take(10), fontSize = 10.sp, color = TextTertiary)
                        }
                        // Rerun / Cancel for in_progress
                        if (run.status == "in_progress" || run.conclusion == "failure") {
                            Spacer(Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Chip(Icons.Rounded.Refresh, Strings.ghRerun) {
                                    scope.launch {
                                        val ok = GitHubManager.rerunWorkflow(context, repo.owner, repo.name, run.id)
                                        Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                                    }
                                }
                                if (run.status == "in_progress") {
                                    Chip(Icons.Rounded.Cancel, Strings.cancel, Color(0xFFFF3B30)) {
                                        scope.launch {
                                            val ok = GitHubManager.cancelWorkflowRun(context, repo.owner, repo.name, run.id)
                                            Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Icon(Icons.Rounded.ChevronRight, null, Modifier.size(16.dp), tint = TextTertiary)
                }
                Box(Modifier.fillMaxWidth().padding(start = 46.dp).height(0.5.dp).background(SeparatorColor))
            }
        }
    }
}

// ═══════════════════════════════════
// Workflow Run Detail — jobs + steps
// ═══════════════════════════════════

@Composable
private fun WorkflowRunDetailScreen(repo: GHRepo, runId: Long, onBack: () -> Unit) {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    var jobs by remember { mutableStateOf<List<GHJob>>(emptyList()) }
    var artifacts by remember { mutableStateOf<List<GHArtifact>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var logsText by remember { mutableStateOf<String?>(null) }
    var downloading by remember { mutableStateOf<Long?>(null) } // artifact id being downloaded

    LaunchedEffect(runId) {
        jobs = GitHubManager.getWorkflowRunJobs(context, repo.owner, repo.name, runId)
        artifacts = GitHubManager.getRunArtifacts(context, repo.owner, repo.name, runId)
        loading = false
    }

    Column(Modifier.fillMaxSize().background(SurfaceLight)) {
        GHTopBar("Run #$runId", onBack = onBack) {
            IconButton(onClick = {
                scope.launch {
                    logsText = GitHubManager.getWorkflowRunLogs(context, repo.owner, repo.name, runId)
                }
            }) { Icon(Icons.Rounded.Article, null, Modifier.size(20.dp), tint = Blue) }
            IconButton(onClick = {
                scope.launch {
                    val ok = GitHubManager.rerunWorkflow(context, repo.owner, repo.name, runId)
                    Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                }
            }) { Icon(Icons.Rounded.Refresh, null, Modifier.size(20.dp), tint = Blue) }
        }

        // Logs overlay
        if (logsText != null) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1E1E22)).clickable { logsText = null }.padding(10.dp)) {
                Text(logsText!!, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color(0xFFD4D4D4), maxLines = 5)
            }
        }

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Blue, modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp) }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
                items(jobs) { job ->
                    // Job header
                    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val jColor = when (job.conclusion) { "success" -> Color(0xFF34C759); "failure" -> Color(0xFFFF3B30); else -> Color(0xFFFF9500) }
                            Icon(when (job.conclusion) { "success" -> Icons.Rounded.CheckCircle; "failure" -> Icons.Rounded.Cancel; else -> Icons.Rounded.Refresh }, null, Modifier.size(18.dp), tint = jColor)
                            Text(job.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        }
                        // Duration
                        if (job.startedAt.isNotBlank() && job.completedAt.isNotBlank()) {
                            Text("${job.startedAt.take(19)} → ${job.completedAt.take(19)}", fontSize = 10.sp, color = TextTertiary, modifier = Modifier.padding(start = 26.dp))
                        }

                        // Steps
                        Spacer(Modifier.height(6.dp))
                        job.steps.forEach { step ->
                            Row(Modifier.fillMaxWidth().padding(start = 26.dp, top = 3.dp, bottom = 3.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val sColor = when (step.conclusion) { "success" -> Color(0xFF34C759); "failure" -> Color(0xFFFF3B30); "skipped" -> Color(0xFF8E8E93); else -> Color(0xFFFF9500) }
                                val sIcon = when (step.conclusion) { "success" -> Icons.Rounded.Check; "failure" -> Icons.Rounded.Close; "skipped" -> Icons.Rounded.Remove; else -> Icons.Rounded.Refresh }
                                Icon(sIcon, null, Modifier.size(14.dp), tint = sColor)
                                Text(step.name, fontSize = 12.sp, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        Box(Modifier.fillMaxWidth().height(0.5.dp).background(SeparatorColor))
                        Spacer(Modifier.height(8.dp))
                    }
                }

                // ─── Artifacts section ───
                if (artifacts.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(8.dp))
                        Text(Strings.ghArtifacts, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Spacer(Modifier.height(8.dp))
                    }
                    items(artifacts) { artifact ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(SurfaceWhite)
                                .clickable(enabled = !artifact.expired && downloading != artifact.id) {
                                    downloading = artifact.id
                                    scope.launch {
                                        val dest = File(
                                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                                            "GlassFiles_Git/${artifact.name}.zip"
                                        )
                                        val ok = GitHubManager.downloadArtifact(context, repo.owner, repo.name, artifact.id, dest)
                                        Toast.makeText(context, if (ok) "${Strings.done}: ${dest.name}" else Strings.error, Toast.LENGTH_SHORT).show()
                                        downloading = null
                                    }
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Archive, null, Modifier.size(24.dp),
                                tint = if (artifact.expired) TextTertiary else Blue
                            )
                            Column(Modifier.weight(1f)) {
                                Text(artifact.name, fontSize = 14.sp, color = if (artifact.expired) TextTertiary else TextPrimary,
                                    fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(fmtSize(artifact.sizeInBytes), fontSize = 11.sp, color = TextSecondary)
                                    if (artifact.expired) {
                                        Text(Strings.ghExpired, fontSize = 11.sp, color = Color(0xFFFF3B30))
                                    } else {
                                        Text(artifact.createdAt.take(10), fontSize = 11.sp, color = TextTertiary)
                                    }
                                }
                            }
                            if (downloading == artifact.id) {
                                CircularProgressIndicator(Modifier.size(18.dp), color = Blue, strokeWidth = 2.dp)
                            } else if (!artifact.expired) {
                                Icon(Icons.Rounded.Download, null, Modifier.size(18.dp), tint = Blue)
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════
// Notifications Screen
// ═══════════════════════════════════

@Composable
private fun NotificationsScreen(onBack: () -> Unit) {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    var notifications by remember { mutableStateOf<List<GHNotification>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var showAll by remember { mutableStateOf(false) }

    LaunchedEffect(showAll) {
        loading = true
        notifications = GitHubManager.getNotifications(context, all = showAll)
        loading = false
    }

    Column(Modifier.fillMaxSize().background(SurfaceLight)) {
        GHTopBar(Strings.ghNotifications, onBack = onBack) {
            // Toggle all/unread
            Box(Modifier.clip(RoundedCornerShape(6.dp)).background(if (showAll) Blue.copy(0.15f) else SurfaceLight)
                .clickable { showAll = !showAll }.padding(horizontal = 8.dp, vertical = 4.dp)) {
                Text(if (showAll) "All" else Strings.ghUnread, fontSize = 11.sp, color = if (showAll) Blue else TextSecondary)
            }
            // Mark all read
            IconButton(onClick = {
                scope.launch {
                    GitHubManager.markAllNotificationsRead(context)
                    notifications = GitHubManager.getNotifications(context, all = showAll)
                }
            }) { Icon(Icons.Rounded.DoneAll, null, Modifier.size(20.dp), tint = Blue) }
        }

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Blue, modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp) }
        } else if (notifications.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.NotificationsNone, null, Modifier.size(48.dp), tint = TextTertiary)
                    Spacer(Modifier.height(8.dp))
                    Text(Strings.ghNoNotifications, fontSize = 14.sp, color = TextTertiary)
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                items(notifications) { n ->
                    Row(Modifier.fillMaxWidth()
                        .background(if (n.unread) Blue.copy(0.04f) else Color.Transparent)
                        .clickable {
                            if (n.unread) scope.launch {
                                GitHubManager.markNotificationRead(context, n.id)
                                notifications = GitHubManager.getNotifications(context, all = showAll)
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                        // Type icon
                        val (icon, iconColor) = when (n.type) {
                            "Issue" -> Icons.Rounded.RadioButtonUnchecked to Color(0xFF34C759)
                            "PullRequest" -> Icons.Rounded.CallMerge to Color(0xFF8957E5)
                            "Release" -> Icons.Rounded.NewReleases to Blue
                            else -> Icons.Rounded.Notifications to TextSecondary
                        }
                        Icon(icon, null, Modifier.size(20.dp), tint = iconColor)
                        Column(Modifier.weight(1f)) {
                            Text(n.title, fontSize = 14.sp, color = TextPrimary, maxLines = 2,
                                fontWeight = if (n.unread) FontWeight.SemiBold else FontWeight.Normal)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(n.repoName.substringAfter("/"), fontSize = 11.sp, color = Blue, maxLines = 1)
                                Text(n.reason, fontSize = 10.sp, color = TextTertiary)
                                Text(n.updatedAt.take(10), fontSize = 10.sp, color = TextTertiary)
                            }
                        }
                        if (n.unread) Box(Modifier.size(8.dp).clip(CircleShape).background(Blue))
                    }
                    Box(Modifier.fillMaxWidth().padding(start = 46.dp).height(0.5.dp).background(SeparatorColor))
                }
            }
        }
    }
}

// ═══════════════════════════════════
// Syntax Highlighted Text
// ═══════════════════════════════════

@Composable
private fun SyntaxHighlightedText(code: String, extension: String) {
    val lines = code.lines()
    val isCode = extension.lowercase() in listOf(
        "kt", "java", "py", "js", "ts", "tsx", "jsx", "c", "cpp", "h", "hpp", "cs",
        "go", "rs", "swift", "rb", "php", "dart", "scala", "sh", "bash", "lua", "r",
        "html", "xml", "css", "scss", "json", "yaml", "yml", "toml", "sql", "gradle"
    )
    if (!isCode) {
        Text(code, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = Color(0xFFD4D4D4), lineHeight = 18.sp)
        return
    }

    // Keyword sets
    val keywords = setOf(
        "fun", "val", "var", "class", "object", "interface", "enum", "data", "sealed", "abstract",
        "override", "private", "public", "protected", "internal", "open", "final", "companion",
        "import", "package", "return", "if", "else", "when", "for", "while", "do", "break", "continue",
        "try", "catch", "finally", "throw", "is", "as", "in", "by", "init", "constructor", "suspend",
        "function", "const", "let", "def", "self", "this", "super", "new", "delete", "typeof", "instanceof",
        "static", "void", "int", "long", "float", "double", "boolean", "char", "string", "byte",
        "true", "false", "null", "nil", "None", "True", "False",
        "struct", "impl", "trait", "pub", "fn", "mut", "use", "mod", "crate", "extern",
        "from", "with", "yield", "async", "await", "lambda", "raise", "except", "pass",
        "switch", "case", "default", "goto", "volatile", "register", "typedef", "sizeof",
        "#include", "#define", "#ifdef", "#endif", "#import"
    )

    val kwColor = Color(0xFFC586C0)       // purple — keywords
    val strColor = Color(0xFFCE9178)      // orange — strings
    val commentColor = Color(0xFF6A9955)  // green — comments
    val numColor = Color(0xFFB5CEA8)      // light green — numbers
    val typeColor = Color(0xFF4EC9B0)     // teal — types
    val defaultColor = Color(0xFFD4D4D4)
    val lineNumColor = Color(0xFF5A5A5A)

    Column {
        lines.forEachIndexed { idx, line ->
            Row {
                // Line number
                Text(
                    "${idx + 1}".padStart(4),
                    fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                    color = lineNumColor, modifier = Modifier.padding(end = 12.dp)
                )
                // Highlighted line
                Text(
                    highlightLine(line, keywords, kwColor, strColor, commentColor, numColor, typeColor, defaultColor),
                    fontSize = 12.sp, fontFamily = FontFamily.Monospace, lineHeight = 18.sp
                )
            }
        }
    }
}

private fun highlightLine(
    line: String, keywords: Set<String>,
    kwColor: Color, strColor: Color, commentColor: Color, numColor: Color, typeColor: Color, defaultColor: Color
): androidx.compose.ui.text.AnnotatedString {
    return androidx.compose.ui.text.buildAnnotatedString {
        var i = 0
        val len = line.length

        // Check for line comment
        val commentIdx = findCommentStart(line)
        val commentStart = commentIdx

        while (i < len) {
            // Comment from this point
            if (commentStart >= 0 && i >= commentStart) {
                pushStyle(androidx.compose.ui.text.SpanStyle(color = commentColor))
                append(line.substring(i))
                pop()
                break
            }
            // String literal
            if (line[i] == '"' || line[i] == '\'') {
                val quote = line[i]
                val start = i; i++
                while (i < len && line[i] != quote) { if (line[i] == '\\') i++; i++ }
                if (i < len) i++ // closing quote
                pushStyle(androidx.compose.ui.text.SpanStyle(color = strColor))
                append(line.substring(start, i))
                pop()
                continue
            }
            // Number
            if (line[i].isDigit() && (i == 0 || !line[i - 1].isLetterOrDigit())) {
                val start = i
                while (i < len && (line[i].isDigit() || line[i] == '.' || line[i] == 'x' || line[i] == 'f' || line[i] == 'L')) i++
                pushStyle(androidx.compose.ui.text.SpanStyle(color = numColor))
                append(line.substring(start, i))
                pop()
                continue
            }
            // Word
            if (line[i].isLetter() || line[i] == '_' || line[i] == '#' || line[i] == '@') {
                val start = i
                while (i < len && (line[i].isLetterOrDigit() || line[i] == '_')) i++
                val word = line.substring(start, i)
                val color = when {
                    word in keywords -> kwColor
                    word.first().isUpperCase() && word.length > 1 -> typeColor
                    word.startsWith("@") -> Color(0xFFDCDCAA) // annotation
                    else -> defaultColor
                }
                pushStyle(androidx.compose.ui.text.SpanStyle(color = color))
                append(word)
                pop()
                continue
            }
            // Other chars
            pushStyle(androidx.compose.ui.text.SpanStyle(color = defaultColor))
            append(line[i])
            pop()
            i++
        }
    }
}

private fun findCommentStart(line: String): Int {
    val trimmed = line.trimStart()
    val offset = line.length - trimmed.length
    return when {
        trimmed.startsWith("//") -> offset
        trimmed.startsWith("#") && !trimmed.startsWith("#include") && !trimmed.startsWith("#define") && !trimmed.startsWith("#import") -> offset
        trimmed.startsWith("--") -> offset
        else -> {
            // Find // not inside string
            var i = 0; var inStr = false; var q = ' '
            while (i < line.length - 1) {
                if (!inStr && (line[i] == '"' || line[i] == '\'')) { inStr = true; q = line[i] }
                else if (inStr && line[i] == q && (i == 0 || line[i-1] != '\\')) inStr = false
                else if (!inStr && line[i] == '/' && line[i+1] == '/') return i
                i++
            }
            -1
        }
    }
}
