package com.glassfiles.ui.screens

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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.glassfiles.data.github.GHRepo
import com.glassfiles.data.github.GHUserProfile
import com.glassfiles.data.github.GitHubManager
import com.glassfiles.ui.components.AiModuleHairline
import com.glassfiles.ui.components.AiModuleKeyValueRow
import com.glassfiles.ui.components.AiModuleScreenScaffold
import com.glassfiles.ui.components.AiModuleSectionLabel
import com.glassfiles.ui.components.AiModuleSpinner
import com.glassfiles.ui.theme.AiModuleTheme
import com.glassfiles.ui.theme.JetBrainsMono
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    username: String,
    onBack: () -> Unit,
    onRepoClick: (GHRepo) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var profile by remember { mutableStateOf<GHUserProfile?>(null) }
    var repos by remember { mutableStateOf<List<GHRepo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var isFollowing by remember { mutableStateOf(false) }
    val listState = rememberSaveable(username, saver = LazyListState.Saver) { LazyListState(0, 0) }

    LaunchedEffect(username) {
        profile = GitHubManager.getUserProfile(context, username)
        repos = GitHubManager.getUserRepos(context, username)
        isFollowing = GitHubManager.isFollowing(context, username)
        loading = false
    }

    val palette = AiModuleTheme.colors
    val cachedSelf = GitHubManager.getCachedUser(context)?.login

    AiModuleScreenScaffold(
        title = "@$username",
        onBack = onBack,
        subtitle = profile?.let { "profile · ${formatGitHubNumber(it.publicRepos)} repos" }
            ?: "profile",
        trailing = {
            val me = profile
            if (me != null && me.login != cachedSelf) {
                IconButton(
                    onClick = {
                        scope.launch {
                            if (isFollowing) {
                                GitHubManager.unfollowUser(context, username)
                            } else {
                                GitHubManager.followUser(context, username)
                            }
                            isFollowing = !isFollowing
                        }
                    },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        if (isFollowing) Icons.Rounded.PersonRemove else Icons.Rounded.PersonAdd,
                        contentDescription = if (isFollowing) "unfollow" else "follow",
                        modifier = Modifier.size(18.dp),
                        tint = if (isFollowing) palette.textSecondary else palette.accent,
                    )
                }
            }
        },
    ) {
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                AiModuleSpinner(label = "loading profile…")
            }
            profile == null -> GitHubMonoEmpty(
                title = "user not found",
                subtitle = "@$username does not exist or is private",
            )
            else -> {
                val p = profile!!
                LazyColumn(
                    Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
                ) {
                    item {
                        ProfileHeader(p)
                        AiModuleHairline()
                    }
                    item {
                        ProfileMetaBlock(p)
                        AiModuleHairline()
                    }
                    item {
                        ProfileStatsRow(p)
                        AiModuleHairline()
                    }
                    item {
                        AiModuleSectionLabel(
                            "repositories",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        )
                    }
                    if (repos.isEmpty()) {
                        item {
                            Box(
                                Modifier.fillMaxWidth().padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "no public repositories",
                                    color = palette.textMuted,
                                    fontFamily = JetBrainsMono,
                                    fontSize = 12.sp,
                                )
                            }
                        }
                    } else {
                        items(repos, key = { "${it.owner}/${it.name}" }) { repo ->
                            RepoCard(repo, onClick = { onRepoClick(repo) })
                            AiModuleHairline()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileHeader(profile: GHUserProfile) {
    val palette = AiModuleTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            profile.avatarUrl,
            profile.login,
            Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = profile.name.ifBlank { profile.login },
                color = palette.textPrimary,
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                lineHeight = 1.25.em,
            )
            Text(
                text = "@${profile.login}",
                color = palette.textSecondary,
                fontFamily = JetBrainsMono,
                fontSize = 12.sp,
            )
            if (profile.bio.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = profile.bio,
                    color = palette.textSecondary,
                    fontFamily = JetBrainsMono,
                    fontSize = 12.sp,
                    lineHeight = 1.4.em,
                )
            }
        }
    }
}

@Composable
private fun ProfileMetaBlock(profile: GHUserProfile) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        if (profile.company.isNotBlank()) {
            AiModuleKeyValueRow(key = "company", value = profile.company)
        }
        if (profile.location.isNotBlank()) {
            AiModuleKeyValueRow(key = "location", value = profile.location)
        }
        if (profile.blog.isNotBlank()) {
            AiModuleKeyValueRow(
                key = "blog",
                value = profile.blog,
                valueColor = AiModuleTheme.colors.accent,
            )
        }
    }
}

@Composable
private fun ProfileStatsRow(profile: GHUserProfile) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        StatCell("repos", formatGitHubNumber(profile.publicRepos), Modifier.weight(1f))
        StatCell("followers", formatGitHubNumber(profile.followers), Modifier.weight(1f))
        StatCell("following", formatGitHubNumber(profile.following), Modifier.weight(1f))
    }
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
    val palette = AiModuleTheme.colors
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            color = palette.textPrimary,
            fontFamily = JetBrainsMono,
            fontWeight = FontWeight.Medium,
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            color = palette.textMuted,
            fontFamily = JetBrainsMono,
            fontSize = 11.sp,
            letterSpacing = 0.4.sp,
            textAlign = TextAlign.Center,
        )
    }
}
