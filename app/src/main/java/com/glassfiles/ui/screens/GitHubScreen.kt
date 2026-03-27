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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.glassfiles.data.Strings
import com.glassfiles.data.github.*
import com.glassfiles.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun GitHubScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isLoggedIn by remember { mutableStateOf(GitHubManager.isLoggedIn(context)) }
    var user by remember { mutableStateOf(GitHubManager.getCachedUser(context)) }
    var selectedRepo by remember { mutableStateOf<GHRepo?>(null) }

    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) { user = GitHubManager.getUser(context) }
    }

    // Login screen
    if (!isLoggedIn) {
        LoginScreen(onBack = onBack, onLogin = { token ->
            GitHubManager.saveToken(context, token)
            isLoggedIn = true
        })
        return
    }

    // Repo detail
    if (selectedRepo != null) {
        RepoDetailScreen(repo = selectedRepo!!, onBack = { selectedRepo = null })
        return
    }

    // Main — repos list
    ReposScreen(user = user, onBack = {
        onBack()
    }, onLogout = {
        GitHubManager.logout(context)
        isLoggedIn = false; user = null
    }, onRepoClick = { selectedRepo = it })
}

// ═══════════════════════════════════
// Login
// ═══════════════════════════════════

@Composable
private fun LoginScreen(onBack: () -> Unit, onLogin: (String) -> Unit) {
    var token by remember { mutableStateOf("") }
    var testing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().background(SurfaceLight)) {
        Row(Modifier.fillMaxWidth().background(SurfaceWhite).padding(top = 44.dp, start = 4.dp, end = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, Modifier.size(20.dp), tint = Blue) }
            Text("GitHub", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 20.sp)
        }

        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            // GitHub icon
            Box(Modifier.size(80.dp).clip(CircleShape).background(TextPrimary), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Code, null, Modifier.size(40.dp), tint = SurfaceLight)
            }
            Spacer(Modifier.height(24.dp))
            Text("GitHub", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(8.dp))
            Text(Strings.ghLoginDesc, fontSize = 14.sp, color = TextSecondary,
                modifier = Modifier.padding(horizontal = 16.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.height(24.dp))

            // Token input
            OutlinedTextField(token, { token = it; error = "" },
                label = { Text("Personal Access Token") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                isError = error.isNotBlank()
            )
            if (error.isNotBlank()) {
                Text(error, color = Color(0xFFFF3B30), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }

            Spacer(Modifier.height(8.dp))
            Text(Strings.ghTokenHint, fontSize = 11.sp, color = TextTertiary, modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(20.dp))

            // Login button
            Button(onClick = {
                if (token.isBlank()) { error = "Token required"; return@Button }
                testing = true; error = ""
                scope.launch {
                    GitHubManager.saveToken(context, token)
                    val user = GitHubManager.getUser(context)
                    if (user != null) { onLogin(token) }
                    else { error = "Invalid token"; GitHubManager.logout(context) }
                    testing = false
                }
            }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TextPrimary), enabled = !testing) {
                if (testing) CircularProgressIndicator(Modifier.size(20.dp), color = SurfaceLight, strokeWidth = 2.dp)
                else Text(Strings.ghSignIn, color = SurfaceLight, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ═══════════════════════════════════
// Repos list + Profile
// ═══════════════════════════════════

@Composable
private fun ReposScreen(user: GHUser?, onBack: () -> Unit, onLogout: () -> Unit, onRepoClick: (GHRepo) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var repos by remember { mutableStateOf<List<GHRepo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    var showCreate by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { repos = GitHubManager.getRepos(context); loading = false }

    val filtered = remember(repos, query) {
        if (query.isNotBlank()) repos.filter { it.name.contains(query, true) || it.description.contains(query, true) }
        else repos
    }

    Column(Modifier.fillMaxSize().background(SurfaceLight)) {
        Row(Modifier.fillMaxWidth().background(SurfaceWhite).padding(top = 44.dp, start = 4.dp, end = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, Modifier.size(20.dp), tint = Blue) }
            Text("GitHub", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 20.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = { showCreate = true }) { Icon(Icons.Rounded.Add, null, Modifier.size(22.dp), tint = Blue) }
            IconButton(onClick = onLogout) { Icon(Icons.Rounded.Logout, null, Modifier.size(20.dp), tint = Color(0xFFFF3B30)) }
        }

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 100.dp)) {
            // Profile card
            if (user != null) {
                item {
                    Box(Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(16.dp)).background(SurfaceWhite).padding(16.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(user.avatarUrl, user.login, Modifier.size(56.dp).clip(CircleShape))
                            Column(Modifier.weight(1f)) {
                                Text(user.name.ifBlank { user.login }, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                Text("@${user.login}", fontSize = 13.sp, color = TextSecondary)
                                if (user.bio.isNotBlank()) Text(user.bio, fontSize = 12.sp, color = TextTertiary, maxLines = 2)
                            }
                        }
                    }
                }
                // Stats
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatBox(Strings.ghRepos, "${user.publicRepos + user.privateRepos}", Modifier.weight(1f))
                        StatBox(Strings.ghFollowers, "${user.followers}", Modifier.weight(1f))
                        StatBox(Strings.ghFollowing, "${user.following}", Modifier.weight(1f))
                    }
                }
            }

            // Search
            item {
                Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(10.dp))
                    .background(SurfaceWhite).padding(horizontal = 12.dp, vertical = 10.dp)) {
                    if (query.isEmpty()) Text(Strings.ghSearchRepos, color = TextTertiary, fontSize = 14.sp)
                    BasicTextField(query, { query = it }, textStyle = androidx.compose.ui.text.TextStyle(color = TextPrimary, fontSize = 14.sp),
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            }

            if (loading) {
                item { Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Blue, modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp) } }
            } else {
                items(filtered) { repo -> RepoCard(repo) { onRepoClick(repo) } }
            }
        }
    }

    // Create repo dialog
    if (showCreate) {
        CreateRepoDialog(onDismiss = { showCreate = false }, onCreate = { name, desc, priv ->
            scope.launch {
                val ok = GitHubManager.createRepo(context, name, desc, priv)
                Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                if (ok) { repos = GitHubManager.getRepos(context) }
                showCreate = false
            }
        })
    }
}

@Composable
private fun StatBox(label: String, value: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(10.dp)).background(SurfaceWhite).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Text(label, fontSize = 11.sp, color = TextSecondary)
    }
}

@Composable
private fun RepoCard(repo: GHRepo, onClick: () -> Unit) {
    val langColor = when (repo.language.lowercase()) {
        "kotlin" -> Color(0xFFA97BFF); "java" -> Color(0xFFB07219); "python" -> Color(0xFF3572A5)
        "javascript" -> Color(0xFFF1E05A); "typescript" -> Color(0xFF3178C6); "c" -> Color(0xFF555555)
        "c++" -> Color(0xFFF34B7D); "swift" -> Color(0xFFFFAC45); "go" -> Color(0xFF00ADD8)
        "rust" -> Color(0xFFDEA584); "dart" -> Color(0xFF00B4AB); else -> TextTertiary
    }

    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(36.dp).background((if (repo.isPrivate) Color(0xFFFF9F0A) else Blue).copy(0.1f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center) {
            Icon(if (repo.isPrivate) Icons.Rounded.Lock else Icons.Rounded.FolderOpen, null, Modifier.size(20.dp),
                tint = if (repo.isPrivate) Color(0xFFFF9F0A) else Blue)
        }
        Column(Modifier.weight(1f)) {
            Text(repo.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (repo.description.isNotBlank()) Text(repo.description, fontSize = 12.sp, color = TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 4.dp)) {
                if (repo.language.isNotBlank()) Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(langColor))
                    Text(repo.language, fontSize = 11.sp, color = TextSecondary)
                }
                if (repo.stars > 0) Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Star, null, Modifier.size(12.dp), tint = Color(0xFFFFCC00))
                    Text("${repo.stars}", fontSize = 11.sp, color = TextSecondary)
                }
                if (repo.forks > 0) Text("⑂ ${repo.forks}", fontSize = 11.sp, color = TextSecondary)
                if (repo.isFork) Text("fork", fontSize = 10.sp, color = TextTertiary)
            }
        }
    }
}

// ═══════════════════════════════════
// Repo Detail
// ═══════════════════════════════════

private enum class RepoTab { FILES, COMMITS, ISSUES, BRANCHES }

@Composable
private fun RepoDetailScreen(repo: GHRepo, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(RepoTab.FILES) }
    var contents by remember { mutableStateOf<List<GHContent>>(emptyList()) }
    var currentPath by remember { mutableStateOf("") }
    var commits by remember { mutableStateOf<List<GHCommit>>(emptyList()) }
    var issues by remember { mutableStateOf<List<GHIssue>>(emptyList()) }
    var branches by remember { mutableStateOf<List<String>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var fileContent by remember { mutableStateOf<String?>(null) }
    var cloneProgress by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(selectedTab, currentPath) {
        loading = true
        when (selectedTab) {
            RepoTab.FILES -> contents = GitHubManager.getRepoContents(context, repo.owner, repo.name, currentPath)
            RepoTab.COMMITS -> commits = GitHubManager.getCommits(context, repo.owner, repo.name)
            RepoTab.ISSUES -> issues = GitHubManager.getIssues(context, repo.owner, repo.name)
            RepoTab.BRANCHES -> branches = GitHubManager.getBranches(context, repo.owner, repo.name)
        }
        loading = false
    }

    // File viewer overlay
    if (fileContent != null) {
        Column(Modifier.fillMaxSize().background(Color(0xFF1C1C1E))) {
            Row(Modifier.fillMaxWidth().background(Color(0xFF252526)).padding(top = 44.dp, start = 4.dp, end = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { fileContent = null }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, Modifier.size(20.dp), tint = Color.White) }
                Text(currentPath.substringAfterLast("/"), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
            Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()).padding(10.dp)) {
                Text(fileContent!!, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = Color(0xFFD4D4D4), lineHeight = 18.sp)
            }
        }
        return
    }

    Column(Modifier.fillMaxSize().background(SurfaceLight)) {
        // Top bar
        Row(Modifier.fillMaxWidth().background(SurfaceWhite).padding(top = 44.dp, start = 4.dp, end = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                if (currentPath.isNotBlank() && selectedTab == RepoTab.FILES) {
                    currentPath = currentPath.substringBeforeLast("/", "")
                } else onBack()
            }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, Modifier.size(20.dp), tint = Blue) }
            Column(Modifier.weight(1f)) {
                Text(repo.name, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 17.sp)
                Text(if (currentPath.isNotBlank()) currentPath else repo.owner, fontSize = 12.sp, color = TextSecondary)
            }
            // Clone
            IconButton(onClick = {
                cloneProgress = "Starting..."
                scope.launch {
                    val dest = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "GlassFiles_Git")
                    val ok = GitHubManager.cloneRepo(context, repo.owner, repo.name, dest) { cloneProgress = it }
                    Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                    cloneProgress = null
                }
            }) { Icon(Icons.Rounded.Download, null, Modifier.size(20.dp), tint = Blue) }
        }

        if (cloneProgress != null) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(RoundedCornerShape(8.dp))
                .background(Blue.copy(0.1f)).padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(cloneProgress!!, fontSize = 13.sp, color = Blue, fontWeight = FontWeight.Medium)
            }
        }

        // Tabs
        Row(Modifier.fillMaxWidth().background(SurfaceWhite).horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RepoTab.entries.forEach { tab ->
                val sel = selectedTab == tab
                val label = when (tab) { RepoTab.FILES -> Strings.tools; RepoTab.COMMITS -> Strings.ghCommits; RepoTab.ISSUES -> "Issues"; RepoTab.BRANCHES -> Strings.ghBranches }
                Box(Modifier.clip(RoundedCornerShape(8.dp)).background(if (sel) Blue.copy(0.12f) else Color.Transparent)
                    .border(1.dp, if (sel) Blue.copy(0.3f) else SeparatorColor, RoundedCornerShape(8.dp))
                    .clickable { selectedTab = tab }.padding(horizontal = 12.dp, vertical = 7.dp)) {
                    Text(label, fontSize = 13.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal, color = if (sel) Blue else TextSecondary)
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(SeparatorColor))

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Blue, modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
            }
        } else {
            when (selectedTab) {
                RepoTab.FILES -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp)) {
                    items(contents) { item ->
                        Row(Modifier.fillMaxWidth().clickable {
                            if (item.type == "dir") currentPath = item.path
                            else scope.launch { fileContent = GitHubManager.getFileContent(context, repo.owner, repo.name, item.path); currentPath = item.path }
                        }.padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (item.type == "dir") Icons.Rounded.Folder else Icons.Rounded.InsertDriveFile, null, Modifier.size(22.dp),
                                tint = if (item.type == "dir") FolderBlue else TextSecondary)
                            Text(item.name, fontSize = 14.sp, color = TextPrimary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (item.type != "dir" && item.size > 0) Text(fmtSize(item.size), fontSize = 11.sp, color = TextTertiary)
                        }
                        Box(Modifier.fillMaxWidth().padding(start = 50.dp).height(0.5.dp).background(SeparatorColor))
                    }
                }
                RepoTab.COMMITS -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp)) {
                    items(commits) { commit ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                            Box(Modifier.size(32.dp).clip(CircleShape).background(Blue.copy(0.1f)), contentAlignment = Alignment.Center) {
                                Text(commit.sha.take(2), fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = Blue)
                            }
                            Column(Modifier.weight(1f)) {
                                Text(commit.message.lines().first(), fontSize = 14.sp, color = TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(commit.author, fontSize = 11.sp, color = Blue)
                                    Text(commit.sha, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = TextTertiary)
                                    Text(commit.date.take(10), fontSize = 11.sp, color = TextTertiary)
                                }
                            }
                        }
                        Box(Modifier.fillMaxWidth().padding(start = 58.dp).height(0.5.dp).background(SeparatorColor))
                    }
                }
                RepoTab.ISSUES -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp)) {
                    items(issues) { issue ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                            Icon(if (issue.isPR) Icons.Rounded.CallMerge else if (issue.state == "open") Icons.Rounded.RadioButtonUnchecked else Icons.Rounded.CheckCircle,
                                null, Modifier.size(20.dp), tint = if (issue.state == "open") Color(0xFF34C759) else Color(0xFF8E8E93))
                            Column(Modifier.weight(1f)) {
                                Text(issue.title, fontSize = 14.sp, color = TextPrimary, maxLines = 2)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("#${issue.number}", fontSize = 11.sp, color = TextTertiary)
                                    Text(issue.author, fontSize = 11.sp, color = Blue)
                                    if (issue.comments > 0) Text("💬 ${issue.comments}", fontSize = 11.sp, color = TextTertiary)
                                }
                            }
                        }
                        Box(Modifier.fillMaxWidth().padding(start = 46.dp).height(0.5.dp).background(SeparatorColor))
                    }
                }
                RepoTab.BRANCHES -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp)) {
                    items(branches) { branch ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.AccountTree, null, Modifier.size(20.dp),
                                tint = if (branch == repo.defaultBranch) Blue else TextSecondary)
                            Text(branch, fontSize = 14.sp, color = TextPrimary, modifier = Modifier.weight(1f))
                            if (branch == repo.defaultBranch) {
                                Text("default", fontSize = 11.sp, color = Blue, fontWeight = FontWeight.Medium,
                                    modifier = Modifier.background(Blue.copy(0.1f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                        Box(Modifier.fillMaxWidth().padding(start = 46.dp).height(0.5.dp).background(SeparatorColor))
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════
// Create Repo Dialog
// ═══════════════════════════════════

@Composable
private fun CreateRepoDialog(onDismiss: () -> Unit, onCreate: (String, String, Boolean) -> Unit) {
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var priv by remember { mutableStateOf(false) }

    AlertDialog(onDismissRequest = onDismiss, containerColor = SurfaceWhite,
        title = { Text(Strings.ghNewRepo, fontWeight = FontWeight.Bold, color = TextPrimary) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text(Strings.ghRepoName) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(desc, { desc = it }, label = { Text(Strings.ghRepoDesc) }, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Switch(checked = priv, onCheckedChange = { priv = it }, colors = SwitchDefaults.colors(checkedTrackColor = Blue))
                Text(Strings.ghPrivate, fontSize = 14.sp, color = TextPrimary)
            }
        } },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onCreate(name, desc, priv) }) { Text(Strings.create, color = Blue) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.cancel, color = TextSecondary) } }
    )
}

private fun fmtSize(b: Long): String = when {
    b < 1024 -> "$b B"
    b < 1024L * 1024 -> "%.1f KB".format(b / 1024.0)
    else -> "%.1f MB".format(b / (1024.0 * 1024))
}
