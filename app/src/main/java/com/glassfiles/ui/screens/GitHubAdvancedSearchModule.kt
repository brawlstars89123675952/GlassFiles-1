package com.glassfiles.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Commit
import androidx.compose.material.icons.rounded.Label
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.glassfiles.data.github.GHRepo
import com.glassfiles.ui.components.AiModulePageBar
import com.glassfiles.ui.components.AiModuleHairline
import com.glassfiles.ui.components.AiModuleSpinner
import com.glassfiles.ui.theme.AiModuleTheme
import com.glassfiles.data.github.GHLabelSearchResult
import com.glassfiles.data.github.GHSearchCommitResult
import com.glassfiles.data.github.GHSearchIssueResult
import com.glassfiles.data.github.GHTopicSearchResult
import com.glassfiles.data.github.GHUser
import com.glassfiles.data.github.GitHubManager
import kotlinx.coroutines.launch

private enum class AdvancedSearchKind(val label: String) {
    REPOS("Repos"),
    ISSUES("Issues"),
    COMMITS("Commits"),
    TOPICS("Topics"),
    LABELS("Labels"),
    USERS("Users")
}

private val GhRepoSearchResultsSaver = listSaver<MutableState<List<GHRepo>>, Any>(
    save = { state ->
        state.value.flatMap { repo ->
            listOf(
                repo.name,
                repo.fullName,
                repo.description,
                repo.language,
                repo.stars,
                repo.forks,
                repo.isPrivate,
                repo.isFork,
                repo.defaultBranch,
                repo.updatedAt,
                repo.owner,
                repo.htmlUrl,
                repo.isArchived,
                repo.isTemplate
            )
        }
    },
    restore = { saved ->
        mutableStateOf(
            saved.chunked(14).mapNotNull { row ->
                if (row.size < 14) null else GHRepo(
                    name = row[0] as String,
                    fullName = row[1] as String,
                    description = row[2] as String,
                    language = row[3] as String,
                    stars = row[4] as Int,
                    forks = row[5] as Int,
                    isPrivate = row[6] as Boolean,
                    isFork = row[7] as Boolean,
                    defaultBranch = row[8] as String,
                    updatedAt = row[9] as String,
                    owner = row[10] as String,
                    htmlUrl = row[11] as String,
                    isArchived = row[12] as Boolean,
                    isTemplate = row[13] as Boolean
                )
            }
        )
    }
)

@Composable
internal fun AdvancedSearchScreen(
    onBack: () -> Unit,
    onRepoClick: (GHRepo) -> Unit,
    onProfile: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var query by rememberSaveable { mutableStateOf("") }
    var labelRepository by rememberSaveable { mutableStateOf("") }
    var selectedKind by rememberSaveable { mutableStateOf(AdvancedSearchKind.REPOS) }
    var searching by remember { mutableStateOf(false) }
    var searched by rememberSaveable { mutableStateOf(false) }
    var page by rememberSaveable { mutableIntStateOf(1) }
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState(0, 0) }

    var repos by rememberSaveable(saver = GhRepoSearchResultsSaver) { mutableStateOf<List<GHRepo>>(emptyList()) }
    var issues by remember { mutableStateOf<List<GHSearchIssueResult>>(emptyList()) }
    var commits by remember { mutableStateOf<List<GHSearchCommitResult>>(emptyList()) }
    var topics by remember { mutableStateOf<List<GHTopicSearchResult>>(emptyList()) }
    var labels by remember { mutableStateOf<List<GHLabelSearchResult>>(emptyList()) }
    var users by remember { mutableStateOf<List<GHUser>>(emptyList()) }

    fun runSearch(nextPage: Int = 1) {
        if (query.length < 2 || searching) return
        searching = true
        searched = true
        page = nextPage
        scope.launch {
            when (selectedKind) {
                AdvancedSearchKind.REPOS -> {
                    val result = GitHubManager.searchRepos(context, query)
                    repos = if (nextPage == 1) result else repos + result
                }
                AdvancedSearchKind.ISSUES -> {
                    val result = GitHubManager.searchIssuesAdvanced(context, query, nextPage)
                    issues = if (nextPage == 1) result else issues + result
                }
                AdvancedSearchKind.COMMITS -> {
                    val result = GitHubManager.searchCommitsAdvanced(context, query, nextPage)
                    commits = if (nextPage == 1) result else commits + result
                }
                AdvancedSearchKind.TOPICS -> {
                    val result = GitHubManager.searchTopics(context, query, nextPage)
                    topics = if (nextPage == 1) result else topics + result
                }
                AdvancedSearchKind.LABELS -> {
                    val result = GitHubManager.searchLabels(context, labelRepository, query, nextPage)
                    labels = if (nextPage == 1) result else labels + result
                }
                AdvancedSearchKind.USERS -> {
                    val result = GitHubManager.searchUsers(context, query)
                    users = if (nextPage == 1) result else users + result
                }
            }
            searching = false
        }
    }

    fun resetSearchView() {
        page = 1
        searched = false
    }

    val resultCount = when (selectedKind) {
        AdvancedSearchKind.REPOS -> repos.size
        AdvancedSearchKind.ISSUES -> issues.size
        AdvancedSearchKind.COMMITS -> commits.size
        AdvancedSearchKind.TOPICS -> topics.size
        AdvancedSearchKind.LABELS -> labels.size
        AdvancedSearchKind.USERS -> users.size
    }

    Column(Modifier.fillMaxSize().background(AiModuleTheme.colors.background)) {
        AiModulePageBar(title = "> advanced search", onBack = onBack)
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(AiModuleTheme.colors.surface).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text(searchHint(selectedKind)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Rounded.Search, null, Modifier.size(18.dp), tint = AiModuleTheme.colors.textSecondary) }
                    )
                    if (selectedKind == AdvancedSearchKind.LABELS) {
                        OutlinedTextField(
                            value = labelRepository,
                            onValueChange = { labelRepository = it },
                            label = { Text("Repository owner/name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = { Icon(Icons.Rounded.Code, null, Modifier.size(18.dp), tint = AiModuleTheme.colors.textSecondary) }
                        )
                    }
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AdvancedSearchKind.entries.forEach { kind ->
                            SearchKindChip(kind, selectedKind == kind) {
                                selectedKind = kind
                                resetSearchView()
                            }
                        }
                    }
                    Button(onClick = { runSearch(1) }, enabled = query.length >= 2 && !searching && (selectedKind != AdvancedSearchKind.LABELS || labelRepository.contains("/")), modifier = Modifier.fillMaxWidth()) {
                        if (searching) {
                            CircularProgressIndicator(Modifier.size(18.dp), color = AiModuleTheme.colors.background, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Rounded.Search, null, Modifier.size(18.dp))
                            Text("Search")
                        }
                    }
                }
            }
            item { SearchSummary(selectedKind, resultCount, searched, searching) }
            if (searching && resultCount == 0) {
                item {
                    Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AiModuleTheme.colors.accent, modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
                    }
                }
            } else {
                when (selectedKind) {
                    AdvancedSearchKind.REPOS -> items(repos) { repo -> RepoCard(repo, onClick = { onRepoClick(repo) }, showStats = true) }
                    AdvancedSearchKind.ISSUES -> items(issues) { issue -> SearchIssueCard(issue) { context.openUrl(issue.htmlUrl) } }
                    AdvancedSearchKind.COMMITS -> items(commits) { commit -> SearchCommitCard(commit) { context.openUrl(commit.htmlUrl) } }
                    AdvancedSearchKind.TOPICS -> items(topics) { topic -> TopicSearchCard(topic) }
                    AdvancedSearchKind.LABELS -> items(labels) { label -> LabelSearchCard(label) }
                    AdvancedSearchKind.USERS -> items(users) { user -> SearchUserCard(user) { onProfile(user.login) } }
                }
                if (searched && resultCount == 0) item { EmptySearchCard("No ${selectedKind.label.lowercase()} found") }
                if (searched && resultCount > 0 && selectedKind !in listOf(AdvancedSearchKind.REPOS, AdvancedSearchKind.USERS)) {
                    item {
                        Box(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(AiModuleTheme.colors.surface).clickable { runSearch(page + 1) }.padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Load more", color = AiModuleTheme.colors.accent, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchSummary(kind: AdvancedSearchKind, count: Int, searched: Boolean, searching: Boolean) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(AiModuleTheme.colors.surface).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(searchKindIcon(kind), null, Modifier.size(18.dp), tint = AiModuleTheme.colors.accent)
        Column(Modifier.weight(1f)) {
            Text(kind.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AiModuleTheme.colors.textPrimary)
            Text(if (!searched) "Ready" else if (searching) "Searching..." else "${formatGitHubNumber(count)} results", fontSize = 11.sp, color = AiModuleTheme.colors.textSecondary)
        }
    }
}

@Composable
private fun SearchIssueCard(issue: GHSearchIssueResult, onOpen: () -> Unit) {
    SearchCard(icon = if (issue.isPullRequest) Icons.Rounded.AccountTree else Icons.Rounded.BugReport, tint = if (issue.isPullRequest) AiModuleTheme.colors.textSecondary else GitHubWarningAmber()) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(issue.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AiModuleTheme.colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${issue.repository} #${issue.number} - ${issue.state} - ${issue.updatedAt.take(10)}", fontSize = 11.sp, color = AiModuleTheme.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (issue.body.isNotBlank()) Text(issue.body, fontSize = 12.sp, color = AiModuleTheme.colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                issue.labels.take(6).forEach { label -> SearchPill(label, AiModuleTheme.colors.accent) }
                if (issue.comments > 0) SearchPill("${formatGitHubNumber(issue.comments)} comments", AiModuleTheme.colors.textSecondary)
            }
        }
        IconButton(onClick = onOpen) { Icon(Icons.Rounded.OpenInNew, null, Modifier.size(18.dp), tint = AiModuleTheme.colors.textSecondary) }
    }
}

@Composable
private fun SearchCommitCard(commit: GHSearchCommitResult, onOpen: () -> Unit) {
    SearchCard(icon = Icons.Rounded.Commit, tint = AiModuleTheme.colors.textSecondary) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(commit.message.lineSequence().firstOrNull().orEmpty().ifBlank { commit.sha.take(7) }, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AiModuleTheme.colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${commit.repository} - ${commit.sha.take(7)} - ${commit.date.take(10)}", fontSize = 11.sp, color = AiModuleTheme.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (commit.author.isNotBlank()) Text(commit.author, fontSize = 12.sp, color = AiModuleTheme.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onOpen) { Icon(Icons.Rounded.OpenInNew, null, Modifier.size(18.dp), tint = AiModuleTheme.colors.textSecondary) }
    }
}

@Composable
private fun TopicSearchCard(topic: GHTopicSearchResult) {
    SearchCard(icon = Icons.Rounded.Label, tint = GitHubSuccessGreen) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(topic.displayName.ifBlank { topic.name }, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AiModuleTheme.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOf(topic.name, topic.released).filter { it.isNotBlank() }.joinToString(" - "), fontSize = 11.sp, color = AiModuleTheme.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val desc = topic.shortDescription.ifBlank { topic.description }
            if (desc.isNotBlank()) Text(desc, fontSize = 12.sp, color = AiModuleTheme.colors.textSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (topic.featured) SearchPill("Featured", GitHubWarningAmber())
                if (topic.curated) SearchPill("Curated", AiModuleTheme.colors.accent)
                topic.aliases.take(4).forEach { SearchPill(it, AiModuleTheme.colors.textSecondary) }
            }
        }
    }
}

@Composable
private fun LabelSearchCard(label: GHLabelSearchResult) {
    val color = parseLabelColor(label.color) ?: AiModuleTheme.colors.textSecondary
    SearchCard(icon = Icons.Rounded.Label, tint = color) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(label.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AiModuleTheme.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(label.repository, fontSize = 11.sp, color = AiModuleTheme.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (label.description.isNotBlank()) Text(label.description, fontSize = 12.sp, color = AiModuleTheme.colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            SearchPill("#${label.color.ifBlank { "label" }}", color)
        }
    }
}

@Composable
private fun SearchUserCard(user: GHUser, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(AiModuleTheme.colors.surface).clickable(onClick = onClick).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(user.avatarUrl, user.login, Modifier.size(42.dp).clip(CircleShape))
        Column(Modifier.weight(1f)) {
            Text(user.name.ifBlank { user.login }, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AiModuleTheme.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("@${user.login}", fontSize = 12.sp, color = AiModuleTheme.colors.textSecondary)
        }
        Icon(Icons.Rounded.ChevronRight, null, Modifier.size(18.dp), tint = AiModuleTheme.colors.textSecondary)
    }
}

@Composable
private fun SearchCard(icon: ImageVector, tint: Color, content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(AiModuleTheme.colors.surface).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = tint)
        content()
    }
}

@Composable
private fun SearchKindChip(kind: AdvancedSearchKind, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(999.dp))
            .background(if (selected) AiModuleTheme.colors.accent.copy(alpha = 0.14f) else AiModuleTheme.colors.surfaceElevated)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(searchKindIcon(kind), null, Modifier.size(14.dp), tint = if (selected) AiModuleTheme.colors.accent else AiModuleTheme.colors.textSecondary)
        Text(kind.label, fontSize = 12.sp, color = if (selected) AiModuleTheme.colors.accent else AiModuleTheme.colors.textPrimary, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun SearchPill(label: String, color: Color) {
    Box(Modifier.clip(RoundedCornerShape(999.dp)).background(color.copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 4.dp)) {
        Text(label, fontSize = 11.sp, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun EmptySearchCard(message: String) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(AiModuleTheme.colors.surface).padding(28.dp), contentAlignment = Alignment.Center) {
        Text(message, fontSize = 14.sp, color = AiModuleTheme.colors.textSecondary)
    }
}

private fun searchKindIcon(kind: AdvancedSearchKind): ImageVector = when (kind) {
    AdvancedSearchKind.REPOS -> Icons.Rounded.Code
    AdvancedSearchKind.ISSUES -> Icons.Rounded.BugReport
    AdvancedSearchKind.COMMITS -> Icons.Rounded.Commit
    AdvancedSearchKind.TOPICS -> Icons.Rounded.Label
    AdvancedSearchKind.LABELS -> Icons.Rounded.Label
    AdvancedSearchKind.USERS -> Icons.Rounded.Person
}

private fun searchHint(kind: AdvancedSearchKind): String = when (kind) {
    AdvancedSearchKind.REPOS -> "Search repositories"
    AdvancedSearchKind.ISSUES -> "Search issues and pull requests"
    AdvancedSearchKind.COMMITS -> "Search commits"
    AdvancedSearchKind.TOPICS -> "Search topics"
    AdvancedSearchKind.LABELS -> "Search labels"
    AdvancedSearchKind.USERS -> "Search users"
}

private fun android.content.Context.openUrl(url: String) {
    if (url.isNotBlank()) startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}

private fun parseLabelColor(value: String): Color? =
    try {
        Color(("FF" + value.removePrefix("#")).toLong(16))
    } catch (_: Exception) {
        null
    }
