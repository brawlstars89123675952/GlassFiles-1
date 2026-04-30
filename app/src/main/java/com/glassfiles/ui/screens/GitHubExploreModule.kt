package com.glassfiles.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.glassfiles.data.Strings
import com.glassfiles.data.github.GHCodeResult
import com.glassfiles.data.github.GHOrg
import com.glassfiles.data.github.GHRepo
import com.glassfiles.data.github.GitHubManager
import com.glassfiles.ui.components.AiModuleHairline
import com.glassfiles.ui.components.AiModuleScreenScaffold
import com.glassfiles.ui.components.AiModuleSpinner
import com.glassfiles.ui.theme.AiModuleTheme
import com.glassfiles.ui.theme.JetBrainsMono
import kotlinx.coroutines.launch

// ═══════════════════════════════════
// Code Search Tab (used inside RepoDetailScreen — keeps its own
// header so it can be embedded as a tab pane, not a full screen)
// ═══════════════════════════════════

@Composable
internal fun CodeSearchTab(repo: GHRepo) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<GHCodeResult>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    val palette = AiModuleTheme.colors

    Column(Modifier.fillMaxSize().background(palette.background)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .border(1.dp, palette.border, RoundedCornerShape(6.dp))
                    .background(palette.surface)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = Strings.ghSearchCodeHint,
                        color = palette.textMuted,
                        fontFamily = JetBrainsMono,
                        fontSize = 13.sp,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    textStyle = TextStyle(
                        color = palette.textPrimary,
                        fontFamily = JetBrainsMono,
                        fontSize = 13.sp,
                    ),
                    cursorBrush = SolidColor(palette.accent),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (query.length >= 2) palette.accent else palette.surface)
                    .border(1.dp, palette.accent, RoundedCornerShape(6.dp))
                    .clickable(enabled = query.length >= 2 && !searching) {
                        searching = true
                        searched = true
                        scope.launch {
                            results = GitHubManager.searchCode(context, query, repo.owner, repo.name)
                            searching = false
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (searching) {
                    AiModuleSpinner()
                } else {
                    Icon(
                        Icons.Rounded.Search,
                        contentDescription = "search",
                        modifier = Modifier.size(16.dp),
                        tint = if (query.length >= 2) palette.background else palette.textSecondary,
                    )
                }
            }
        }
        AiModuleHairline()

        when {
            searching -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                AiModuleSpinner(label = "searching code…")
            }
            searched && results.isEmpty() -> GitHubMonoEmpty(
                title = Strings.ghNoResults.lowercase(),
                subtitle = "try another query or scope",
            )
            results.isNotEmpty() -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp),
            ) {
                items(results) { r -> CodeResultRow(r); AiModuleHairline() }
            }
        }
    }
}

@Composable
private fun CodeResultRow(r: GHCodeResult) {
    val palette = AiModuleTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = ">",
            color = palette.accent,
            fontFamily = JetBrainsMono,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            modifier = Modifier.width(14.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                r.name,
                color = palette.textPrimary,
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                lineHeight = 1.3.em,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                r.path,
                color = palette.textMuted,
                fontFamily = JetBrainsMono,
                fontSize = 11.sp,
                lineHeight = 1.3.em,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ═══════════════════════════════════
// Starred Repos Screen
// ═══════════════════════════════════

@Composable
internal fun StarredScreen(onBack: () -> Unit, onRepoClick: (GHRepo) -> Unit) {
    val context = LocalContext.current
    var repos by remember { mutableStateOf<List<GHRepo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        repos = GitHubManager.getStarredRepos(context)
        loading = false
    }

    AiModuleScreenScaffold(
        title = "> ${Strings.ghStarredRepos.lowercase()}",
        onBack = onBack,
        subtitle = if (loading) "loading…" else "${repos.size} starred",
    ) {
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                AiModuleSpinner(label = "loading stars…")
            }
            repos.isEmpty() -> GitHubMonoEmpty(
                title = "no starred repositories",
                subtitle = "stars will appear here once you star a repo",
            )
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp),
            ) {
                items(repos, key = { "${it.owner}/${it.name}" }) { repo ->
                    RepoCard(repo, onClick = { onRepoClick(repo) }, showStats = true)
                    AiModuleHairline()
                }
            }
        }
    }
}

// ═══════════════════════════════════
// Organizations Screen
// ═══════════════════════════════════

@Composable
internal fun OrgsScreen(onBack: () -> Unit, onRepoClick: (GHRepo) -> Unit) {
    val context = LocalContext.current
    var orgs by remember { mutableStateOf<List<GHOrg>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var selectedOrg by remember { mutableStateOf<GHOrg?>(null) }
    var orgRepos by remember { mutableStateOf<List<GHRepo>>(emptyList()) }
    var loadingRepos by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        orgs = GitHubManager.getOrganizations(context)
        loading = false
    }
    LaunchedEffect(selectedOrg) {
        if (selectedOrg != null) {
            loadingRepos = true
            orgRepos = GitHubManager.getOrgRepos(context, selectedOrg!!.login)
            loadingRepos = false
        }
    }

    if (selectedOrg != null) {
        AiModuleScreenScaffold(
            title = "@${selectedOrg!!.login}",
            onBack = { selectedOrg = null; orgRepos = emptyList() },
            subtitle = if (loadingRepos) "loading repos…" else "${orgRepos.size} repositories",
        ) {
            when {
                loadingRepos -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    AiModuleSpinner(label = "loading repos…")
                }
                orgRepos.isEmpty() -> GitHubMonoEmpty(
                    title = "no repositories",
                    subtitle = "this organization has no public repos",
                )
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp),
                ) {
                    items(orgRepos, key = { "${it.owner}/${it.name}" }) { repo ->
                        RepoCard(repo, onClick = { onRepoClick(repo) })
                        AiModuleHairline()
                    }
                }
            }
        }
        return
    }

    AiModuleScreenScaffold(
        title = "> ${Strings.ghOrganizations.lowercase()}",
        onBack = onBack,
        subtitle = if (loading) "loading…" else "${orgs.size} orgs",
    ) {
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                AiModuleSpinner(label = "loading orgs…")
            }
            orgs.isEmpty() -> GitHubMonoEmpty(
                title = Strings.ghNoResults.lowercase(),
                subtitle = "you are not a member of any organisation",
            )
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp),
            ) {
                items(orgs, key = { it.login }) { org ->
                    OrgRow(org, onClick = { selectedOrg = org })
                    AiModuleHairline()
                }
            }
        }
    }
}

@Composable
private fun OrgRow(org: GHOrg, onClick: () -> Unit) {
    val palette = AiModuleTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            org.avatarUrl,
            org.login,
            Modifier.size(28.dp).clip(RoundedCornerShape(4.dp)),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "@${org.login}",
                color = palette.textPrimary,
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (org.description.isNotBlank()) {
                Text(
                    org.description,
                    color = palette.textMuted,
                    fontFamily = JetBrainsMono,
                    fontSize = 11.sp,
                    lineHeight = 1.3.em,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(
            Icons.AutoMirrored.Rounded.ArrowForward,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = palette.textSecondary,
        )
    }
}
