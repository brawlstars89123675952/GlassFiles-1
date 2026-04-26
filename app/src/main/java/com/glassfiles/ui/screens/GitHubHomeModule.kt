package com.glassfiles.ui.screens

import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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

@Composable
internal fun LoginScreen(onBack: () -> Unit, onMinimize: () -> Unit, onClose: (() -> Unit)? = null, onLogin: (String) -> Unit) {
    var token by remember { mutableStateOf("") }; var testing by remember { mutableStateOf(false) }; var error by remember { mutableStateOf("") }
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().background(colors.background)) {
        GHTopBar("GitHub", onBack = onBack, onMinimize = onMinimize, onClose = onClose)
        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(Modifier.size(80.dp).clip(CircleShape).background(colors.onSurface), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Code, null, Modifier.size(40.dp), tint = colors.surface) }
            Spacer(Modifier.height(24.dp)); Text("GitHub", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
            Spacer(Modifier.height(8.dp)); Text(Strings.ghLoginDesc, fontSize = 14.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp), textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(token, { token = it; error = "" }, label = { Text("Personal Access Token") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), isError = error.isNotBlank())
            if (error.isNotBlank()) Text(error, color = GitHubErrorRed, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(8.dp)); Text(Strings.ghTokenHint, fontSize = 11.sp, color = colors.onSurfaceVariant, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(20.dp))
            Button(onClick = { if (token.isBlank()) { error = "Token required"; return@Button }; testing = true; error = ""
                scope.launch { GitHubManager.saveToken(context, token); val u = GitHubManager.getUser(context); if (u != null) onLogin(token) else { error = "Invalid token"; GitHubManager.logout(context) }; testing = false }
            }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = colors.primary), enabled = !testing) {
                if (testing) CircularProgressIndicator(Modifier.size(20.dp), color = colors.onPrimary, strokeWidth = 2.dp) else Text(Strings.ghSignIn, color = colors.onPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
internal fun ReposScreen(user: GHUser?, onBack: () -> Unit, onMinimize: () -> Unit, onClose: (() -> Unit)? = null, onLogout: () -> Unit, onRepoClick: (GHRepo) -> Unit, onGists: () -> Unit, onSettings: () -> Unit, onNotifications: () -> Unit = {}, onProfile: (String) -> Unit = {}) {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    var repos by remember { mutableStateOf<List<GHRepo>>(emptyList()) }; var loading by remember { mutableStateOf(true) }
    var query by rememberSaveable { mutableStateOf("") }; var showCreate by rememberSaveable { mutableStateOf(false) }
    var searchPublic by rememberSaveable { mutableStateOf(false) }; var publicResults by remember { mutableStateOf<List<GHRepo>>(emptyList()) }
    var showStarred by rememberSaveable { mutableStateOf(false) }
    var showOrgs by rememberSaveable { mutableStateOf(false) }
    var showPackages by rememberSaveable { mutableStateOf(false) }
    var showAdvancedSearch by rememberSaveable { mutableStateOf(false) }
    var reposPage by rememberSaveable { mutableIntStateOf(1) }; var reposHasMore by rememberSaveable { mutableStateOf(true) }
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState(0, 0) }
    BackHandler(enabled = showStarred || showOrgs || showPackages || showAdvancedSearch || showCreate) {
        when {
            showCreate -> showCreate = false
            showStarred -> showStarred = false
            showOrgs -> showOrgs = false
            showPackages -> showPackages = false
            showAdvancedSearch -> showAdvancedSearch = false
        }
    }
    LaunchedEffect(Unit) { val r = GitHubManager.getRepos(context, 1); repos = r; reposHasMore = r.size >= 30; loading = false }
    LaunchedEffect(query, searchPublic) { if (searchPublic && query.length >= 2) publicResults = GitHubManager.searchRepos(context, query) }
    val filtered = remember(repos, query, searchPublic) {
        if (searchPublic) publicResults else if (query.isNotBlank()) repos.filter { it.name.contains(query, true) || it.description.contains(query, true) } else repos
    }
    if (showStarred) { StarredScreen(onBack = { showStarred = false }, onRepoClick = { showStarred = false; onRepoClick(it) }); return }
    if (showOrgs) { OrgsScreen(onBack = { showOrgs = false }, onRepoClick = { showOrgs = false; onRepoClick(it) }); return }
    if (showPackages && user != null) { PackagesScreen(userLogin = user.login, onBack = { showPackages = false }); return }
    if (showAdvancedSearch) { AdvancedSearchScreen(onBack = { showAdvancedSearch = false }, onRepoClick = onRepoClick, onProfile = onProfile); return }
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().background(colors.background)) {
        GHTopBar("GitHub", onBack = onBack, onMinimize = onMinimize, onClose = onClose) {
            IconButton(onClick = onNotifications) { Icon(Icons.Rounded.Notifications, null, Modifier.size(20.dp), tint = colors.primary) }
            IconButton(onClick = onGists) { Icon(Icons.Rounded.Description, null, Modifier.size(20.dp), tint = colors.primary) }
            IconButton(onClick = { showCreate = true }) { Icon(Icons.Rounded.Add, null, Modifier.size(22.dp), tint = colors.primary) }
            IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, null, Modifier.size(20.dp), tint = colors.onSurfaceVariant) }
        }
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
            if (user != null) {
                item { Box(Modifier.fillMaxWidth().padding(vertical = 8.dp).clip(RoundedCornerShape(16.dp)).background(colors.surface).padding(16.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(user.avatarUrl, user.login, Modifier.size(56.dp).clip(CircleShape))
                        Column(Modifier.weight(1f)) { Text(user.name.ifBlank { user.login }, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.onSurface); Text("@${user.login}", fontSize = 13.sp, color = colors.onSurfaceVariant); if (user.bio.isNotBlank()) Text(user.bio, fontSize = 12.sp, color = colors.onSurfaceVariant, maxLines = 2) }
                    }
                } }
                item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatBox(Strings.ghRepos, formatGitHubNumber(user.publicRepos + user.privateRepos), Modifier.weight(1f)); StatBox(Strings.ghFollowers, formatGitHubNumber(user.followers), Modifier.weight(1f)); StatBox(Strings.ghFollowing, formatGitHubNumber(user.following), Modifier.weight(1f))
                } }
            }
            // Quick actions row
            item {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuickChip(Icons.Rounded.Star, Strings.ghStarredRepos) { showStarred = true }
                    QuickChip(Icons.Rounded.Business, Strings.ghOrganizations) { showOrgs = true }
                    QuickChip(Icons.Rounded.Search, "Search") { showAdvancedSearch = true }
                    QuickChip(Icons.Rounded.Archive, "Packages") { showPackages = true }
                    QuickChip(Icons.Rounded.Person, Strings.ghProfile) { if (user != null) onProfile(user.login) }
                }
            }
            item { Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(colors.surface).padding(horizontal = 12.dp, vertical = 10.dp)) {
                    if (query.isEmpty()) Text(if (searchPublic) Strings.ghSearchPublic else Strings.ghSearchRepos, color = colors.onSurfaceVariant, fontSize = 14.sp)
                    BasicTextField(query, { query = it }, textStyle = androidx.compose.ui.text.TextStyle(color = colors.onSurface, fontSize = 14.sp), singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(if (searchPublic) colors.primary.copy(0.15f) else colors.surface).clickable { searchPublic = !searchPublic; query = "" }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Public, null, Modifier.size(18.dp), tint = if (searchPublic) colors.primary else colors.onSurfaceVariant)
                }
            } }
            if (loading) { item { Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp) } } }
            else { items(filtered) { repo -> RepoCard(repo, onClick = { onRepoClick(repo) }) }
                if (!searchPublic && query.isBlank() && reposHasMore) item { Box(Modifier.fillMaxWidth().padding(vertical = 8.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surface).clickable { scope.launch { reposPage++; val r = GitHubManager.getRepos(context, reposPage); if (r.size < 30) reposHasMore = false; repos = repos + r } }.padding(12.dp), contentAlignment = Alignment.Center) { Text("Load more", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp, fontWeight = FontWeight.Medium) } }
            }
        }
    }
    if (showCreate) CreateRepoDialog({ showCreate = false }) { n, d, p -> scope.launch { val ok = GitHubManager.createRepo(context, n, d, p); Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show(); if (ok) { reposPage = 1; repos = GitHubManager.getRepos(context, 1); reposHasMore = repos.size >= 30 }; showCreate = false } }
}

@Composable private fun QuickChip(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(Modifier.height(40.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surface).border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
        .clickable(onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

// ═══════════════════════════════════
// Code Search Tab
// ═══════════════════════════════════
