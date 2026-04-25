package com.glassfiles.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.glassfiles.data.github.GHRepo
import com.glassfiles.data.github.GHUserProfile
import com.glassfiles.data.github.GitHubManager
import com.glassfiles.ui.theme.*
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

    LaunchedEffect(username) {
        profile = GitHubManager.getUserProfile(context, username)
        repos = GitHubManager.getUserRepos(context, username)
        isFollowing = GitHubManager.isFollowing(context, username)
        loading = false
    }

    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().background(colors.background)) {
        GHTopBar(
            title = username,
            onBack = onBack,
            actions = {
                if (profile != null && profile!!.login != GitHubManager.getCachedUser(context)?.login) {
                    IconButton(onClick = {
                        scope.launch {
                            if (isFollowing) {
                                GitHubManager.unfollowUser(context, username)
                            } else {
                                GitHubManager.followUser(context, username)
                            }
                            isFollowing = !isFollowing
                        }
                    }) {
                        Icon(
                            if (isFollowing) Icons.Rounded.PersonRemove else Icons.Rounded.PersonAdd,
                            null,
                            Modifier.size(20.dp),
                            tint = colors.primary
                        )
                    }
                }
            }
        )

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.primary)
            }
            return@Column
        }

        if (profile == null) {
            EmptyState(
                icon = Icons.Rounded.PersonOff,
                title = "User not found",
                subtitle = "@$username doesn't exist or is private"
            )
            return@Column
        }

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
            item {
                Column(
                    Modifier.fillMaxWidth().ghGlassCard(20.dp).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AsyncImage(
                        profile!!.avatarUrl,
                        profile!!.login,
                        Modifier.size(100.dp).clip(CircleShape)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        profile!!.name.ifBlank { profile!!.login },
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface
                    )
                    Text("@${profile!!.login}", fontSize = 14.sp, color = colors.onSurfaceVariant)
                    if (profile!!.bio.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(profile!!.bio, fontSize = 13.sp, color = colors.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (profile!!.company.isNotBlank()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Business, null, Modifier.size(14.dp), tint = colors.onSurfaceVariant)
                                Text(profile!!.company, fontSize = 12.sp, color = colors.onSurfaceVariant)
                            }
                        }
                        if (profile!!.location.isNotBlank()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.LocationOn, null, Modifier.size(14.dp), tint = colors.onSurfaceVariant)
                                Text(profile!!.location, fontSize = 12.sp, color = colors.onSurfaceVariant)
                            }
                        }
                    }
                    if (profile!!.blog.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Link, null, Modifier.size(14.dp), tint = colors.onSurfaceVariant)
                            Text(profile!!.blog, fontSize = 12.sp, color = colors.primary)
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard("Repositories", formatGitHubNumber(profile!!.publicRepos), Modifier.weight(1f))
                    StatCard("Followers", formatGitHubNumber(profile!!.followers), Modifier.weight(1f))
                    StatCard("Following", formatGitHubNumber(profile!!.following), Modifier.weight(1f))
                }
            }

            item {
                Spacer(Modifier.height(16.dp))
                Text("Repositories", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
                Spacer(Modifier.height(8.dp))
            }

            if (repos.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                        Text("No public repositories", color = colors.onSurfaceVariant, fontSize = 14.sp)
                    }
                }
            } else {
                items(repos.size) { index ->
                    RepoCard(repos[index]) { onRepoClick(repos[index]) }
                    if (index < repos.lastIndex) Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun StatCard(title: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.ghGlassCard(14.dp).padding(horizontal = 12.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, fontSize = 28.sp, fontWeight = FontWeight.Light, color = MaterialTheme.colorScheme.onSurface, lineHeight = 30.sp)
        Text(title.uppercase(), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium, letterSpacing = 0.7.sp, maxLines = 1)
    }
}
