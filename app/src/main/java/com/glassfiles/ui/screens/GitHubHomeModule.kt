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
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
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
import com.glassfiles.ui.components.AiModulePageBar
import com.glassfiles.ui.components.AiModuleSecondaryButton
import com.glassfiles.ui.components.AiModuleSpinner
import com.glassfiles.ui.components.AiModuleText as Text
import com.glassfiles.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File

// Compact mode — propagates through all sub-screens automatically

@Composable
internal fun LoginScreen(onBack: () -> Unit, onMinimize: () -> Unit, onClose: (() -> Unit)? = null, onLogin: (String) -> Unit) {
    var token by remember { mutableStateOf("") }
    var testing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val palette = AiModuleTheme.colors

    AiModuleSurface {
        Column(Modifier.fillMaxSize()) {
            GitHubPageBar(
                title = "> github",
                subtitle = "sign in",
                onBack = onBack,
                trailing = {
                    GitHubTopBarAction(
                        glyph = GhGlyphs.PIP,
                        onClick = onMinimize,
                        tint = palette.textSecondary,
                        contentDescription = "minimize",
                    )
                    if (onClose != null) {
                        GitHubTopBarAction(
                            glyph = GhGlyphs.CLOSE,
                            onClick = onClose,
                            tint = palette.error,
                            contentDescription = "close",
                        )
                    }
                },
            )
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "[ github ]",
                    color = palette.accent,
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.Medium,
                    fontSize = 22.sp,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    Strings.ghLoginDesc,
                    fontSize = 12.sp,
                    color = palette.textMuted,
                    fontFamily = JetBrainsMono,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    "personal access token",
                    color = palette.textSecondary,
                    fontFamily = JetBrainsMono,
                    fontSize = 11.sp,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .border(1.dp, if (error.isNotBlank()) palette.error else palette.border, RoundedCornerShape(6.dp))
                        .background(palette.surface)
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                ) {
                    if (token.isEmpty()) {
                        Text(
                            "ghp_…",
                            color = palette.textMuted,
                            fontFamily = JetBrainsMono,
                            fontSize = 13.sp,
                        )
                    }
                    BasicTextField(
                        value = token,
                        onValueChange = { token = it; error = "" },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = palette.textPrimary,
                            fontFamily = JetBrainsMono,
                            fontSize = 13.sp,
                        ),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(palette.accent),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (error.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        error,
                        color = palette.error,
                        fontFamily = JetBrainsMono,
                        fontSize = 11.sp,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    Strings.ghTokenHint,
                    fontSize = 10.sp,
                    color = palette.textMuted,
                    fontFamily = JetBrainsMono,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(20.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .border(1.dp, palette.accent, RoundedCornerShape(6.dp))
                        .background(if (!testing) palette.accent else palette.surface)
                        .clickable(enabled = !testing) {
                            if (token.isBlank()) {
                                error = "Token required"
                                return@clickable
                            }
                            testing = true
                            error = ""
                            scope.launch {
                                GitHubManager.saveToken(context, token)
                                val u = GitHubManager.getUser(context)
                                if (u != null) onLogin(token)
                                else {
                                    error = "Invalid token"
                                    GitHubManager.logout(context)
                                }
                                testing = false
                            }
                        }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (testing) {
                        AiModuleSpinner(label = "verifying…")
                    } else {
                        Text(
                            "[ ${Strings.ghSignIn.lowercase()} ]",
                            color = palette.background,
                            fontFamily = JetBrainsMono,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp,
                        )
                    }
                }
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
    var showApps by rememberSaveable { mutableStateOf(false) }
    var showEnterpriseAdmin by rememberSaveable { mutableStateOf(false) }
    var showAdvancedSearch by rememberSaveable { mutableStateOf(false) }
    var reposPage by rememberSaveable { mutableIntStateOf(1) }; var reposHasMore by rememberSaveable { mutableStateOf(true) }
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState(0, 0) }
    BackHandler(enabled = showStarred || showOrgs || showPackages || showApps || showEnterpriseAdmin || showAdvancedSearch || showCreate) {
        when {
            showCreate -> showCreate = false
            showStarred -> showStarred = false
            showOrgs -> showOrgs = false
            showPackages -> showPackages = false
            showApps -> showApps = false
            showEnterpriseAdmin -> showEnterpriseAdmin = false
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
    if (showApps) { GitHubAppsScreen(onBack = { showApps = false }, onRepoClick = { showApps = false; onRepoClick(it) }); return }
    if (showEnterpriseAdmin) { GitHubEnterpriseAdminScreen(onBack = { showEnterpriseAdmin = false }); return }
    if (showAdvancedSearch) { AdvancedSearchScreen(onBack = { showAdvancedSearch = false }, onRepoClick = onRepoClick, onProfile = onProfile); return }
    AiModuleSurface {
    val palette = AiModuleTheme.colors
    Column(Modifier.fillMaxSize().background(palette.background)) {
        GitHubPageBar(
            title = "> github",
            onBack = onBack,
            trailing = {
                GitHubTopBarAction(GhGlyphs.NOTIFY, onNotifications, palette.accent, contentDescription = "notifications")
                GitHubTopBarAction(GhGlyphs.FILE, onGists, palette.accent, contentDescription = "gists")
                GitHubTopBarAction(GhGlyphs.PLUS, { showCreate = true }, palette.accent, contentDescription = "create repository")
                GitHubTopBarAction(GhGlyphs.SETTINGS, onSettings, palette.textSecondary, contentDescription = "settings")
                GitHubTopBarAction(GhGlyphs.PIP, onMinimize, palette.textSecondary, contentDescription = "minimize")
                if (onClose != null) {
                    GitHubTopBarAction(GhGlyphs.CLOSE, onClose, palette.error, contentDescription = "close")
                }
            },
        )
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 12.dp),
        ) {
            if (user != null) {
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsyncImage(user.avatarUrl, user.login, Modifier.size(36.dp).clip(CircleShape))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(
                                "@${user.login}",
                                color = palette.accent,
                                fontFamily = JetBrainsMono,
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp,
                            )
                            if (user.name.isNotBlank() && user.name != user.login) {
                                Text(
                                    user.name,
                                    color = palette.textSecondary,
                                    fontFamily = JetBrainsMono,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (user.bio.isNotBlank()) {
                                Text(
                                    user.bio,
                                    color = palette.textMuted,
                                    fontFamily = JetBrainsMono,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
                item {
                    Text(
                        text = "repos: ${formatGitHubNumber(user.publicRepos + user.privateRepos)}   followers: ${formatGitHubNumber(user.followers)}   following: ${formatGitHubNumber(user.following)}",
                        color = palette.textSecondary,
                        fontFamily = JetBrainsMono,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }
            // Quick actions row
            item {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    TerminalQuickChip(Strings.ghStarredRepos) { showStarred = true }
                    TerminalQuickChip(Strings.ghOrganizations) { showOrgs = true }
                    TerminalQuickChip("Search") { showAdvancedSearch = true }
                    TerminalQuickChip("Packages") { showPackages = true }
                    TerminalQuickChip("Apps") { showApps = true }
                    TerminalQuickChip("Admin API") { showEnterpriseAdmin = true }
                    TerminalQuickChip(Strings.ghProfile) { if (user != null) onProfile(user.login) }
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        if (searchPublic) "search public:" else "search:",
                        color = palette.textMuted,
                        fontFamily = JetBrainsMono,
                        fontSize = 11.sp,
                    )
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(palette.surface)
                            .border(1.dp, palette.border, RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    ) {
                        if (query.isEmpty()) {
                            Text(
                                if (searchPublic) "name / desc / public…" else "name / desc",
                                color = palette.textMuted,
                                fontFamily = JetBrainsMono,
                                fontSize = 12.sp,
                            )
                        }
                        BasicTextField(
                            query,
                            { query = it },
                            textStyle = TextStyle(
                                color = palette.textPrimary,
                                fontSize = 12.sp,
                                fontFamily = JetBrainsMono,
                            ),
                            singleLine = true,
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(palette.accent),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (searchPublic) palette.accent.copy(alpha = 0.10f) else palette.surface)
                            .border(1.dp, if (searchPublic) palette.accent.copy(alpha = 0.55f) else palette.border, RoundedCornerShape(4.dp))
                            .clickable { searchPublic = !searchPublic; query = "" }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    ) {
                        Text(
                            "public",
                            color = if (searchPublic) palette.accent else palette.textSecondary,
                            fontFamily = JetBrainsMono,
                            fontSize = 11.sp,
                        )
                    }
                }
            }
            if (loading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                        AiModuleSpinner(label = "loading repos\u2026")
                    }
                }
            } else if (filtered.isEmpty()) {
                item {
                    Text(
                        "no repositories",
                        color = palette.textMuted,
                        fontFamily = JetBrainsMono,
                        fontSize = 12.sp,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                items(filtered) { repo -> RepoCard(repo, onClick = { onRepoClick(repo) }) }
                if (!searchPublic && query.isBlank() && reposHasMore) {
                    item {
                        Box(
                            Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            AiModuleSecondaryButton(
                                label = "load more \u2192",
                                onClick = {
                                    scope.launch {
                                        reposPage++
                                        val r = GitHubManager.getRepos(context, reposPage)
                                        if (r.size < 30) reposHasMore = false
                                        repos = repos + r
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
    if (showCreate) CreateRepoDialog({ showCreate = false }) { n, d, p -> scope.launch { val ok = GitHubManager.createRepo(context, n, d, p); Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show(); if (ok) { reposPage = 1; repos = GitHubManager.getRepos(context, 1); reposHasMore = repos.size >= 30 }; showCreate = false } }
    }
}

@Composable
private fun TerminalQuickChip(label: String, onClick: () -> Unit) {
    val palette = AiModuleTheme.colors
    Box(
        Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(palette.surface)
            .border(1.dp, palette.border, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            label.lowercase(java.util.Locale.US),
            color = palette.textSecondary,
            fontFamily = JetBrainsMono,
            fontSize = 11.sp,
        )
    }
}

// ═══════════════════════════════════
// Code Search Tab
// ═══════════════════════════════════
