package com.glassfiles.ui.screens

import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
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
import com.glassfiles.notifications.GitHubNotificationTarget
import com.glassfiles.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File

// Compact mode — propagates through all sub-screens automatically

internal val LocalGHCompact = compositionLocalOf { false }

@Composable
fun GitHubScreen(
    onBack: () -> Unit,
    onMinimize: () -> Unit = {},
    onClose: (() -> Unit)? = null,
    compact: Boolean = false,
    initialTarget: GitHubNotificationTarget? = null,
    onInitialTargetConsumed: () -> Unit = {},
    onOpenAiAgent: ((repoFullName: String, branch: String?, prompt: String?) -> Unit)? = null
) {
    CompositionLocalProvider(LocalGHCompact provides compact) {
    val context = LocalContext.current
    var isLoggedIn by remember { mutableStateOf(GitHubManager.isLoggedIn(context)) }
    var user by remember { mutableStateOf(GitHubManager.getCachedUser(context)) }
    var selectedRepo by remember { mutableStateOf<GHRepo?>(null) }
    var showGists by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showNotifications by rememberSaveable { mutableStateOf(false) }
    var showProfile by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingTarget by remember { mutableStateOf(initialTarget) }
    val saveableStateHolder = rememberSaveableStateHolder()
    
    LaunchedEffect(isLoggedIn) { if (isLoggedIn) user = GitHubManager.getUser(context) }
    LaunchedEffect(initialTarget) {
        if (initialTarget != null) pendingTarget = initialTarget
    }
    LaunchedEffect(isLoggedIn, pendingTarget) {
        val target = pendingTarget ?: return@LaunchedEffect
        if (!isLoggedIn) return@LaunchedEffect
        showSettings = false
        showGists = false
        showProfile = null
        showNotifications = false
        val repo = GitHubManager.getRepo(context, target.owner, target.repo)
        if (repo != null) {
            selectedRepo = repo
        } else {
            showNotifications = true
            pendingTarget = null
            onInitialTargetConsumed()
        }
    }
    BackHandler(enabled = isLoggedIn) {
        when {
            selectedRepo != null -> selectedRepo = null
            showProfile != null -> showProfile = null
            showNotifications -> showNotifications = false
            showSettings -> showSettings = false
            showGists -> showGists = false
            else -> onBack()
        }
    }
    when {
        !isLoggedIn -> LoginScreen(onBack, onMinimize, onClose) { GitHubManager.saveToken(context, it); isLoggedIn = true }
        showSettings -> saveableStateHolder.SaveableStateProvider("settings") { GitHubSettingsScreen(onBack = { showSettings = false }, onLogout = { GitHubManager.logout(context); isLoggedIn = false; user = null; showSettings = false }, onClose = onClose) }
        showGists -> saveableStateHolder.SaveableStateProvider("gists") { GistsScreen({ showGists = false }, onMinimize, onClose) }
        showNotifications -> saveableStateHolder.SaveableStateProvider("notifications") { NotificationsScreen(onBack = { showNotifications = false }) }
        selectedRepo != null -> saveableStateHolder.SaveableStateProvider("repo:${selectedRepo!!.fullName}") {
            RepoDetailScreen(
                selectedRepo!!,
                { selectedRepo = null },
                onMinimize,
                onClose,
                initialTarget = pendingTarget?.takeIf { it.repoFullName == selectedRepo!!.fullName },
                onInitialTargetConsumed = {
                    pendingTarget = null
                    onInitialTargetConsumed()
                },
                onOpenAiAgent = onOpenAiAgent
            )
        }
        showProfile != null -> saveableStateHolder.SaveableStateProvider("profile:${showProfile!!}") { ProfileScreen(username = showProfile!!, onBack = { showProfile = null }, onRepoClick = { selectedRepo = it }) }
        else -> saveableStateHolder.SaveableStateProvider("home") { ReposScreen(user, onBack, onMinimize, onClose, { GitHubManager.logout(context); isLoggedIn = false; user = null }, { selectedRepo = it }, { showGists = true }, { showSettings = true }, { showNotifications = true }, { showProfile = it }) }
    }
    }
}

@Composable
internal fun GHTopBar(title: String, subtitle: String? = null, onBack: () -> Unit, onMinimize: (() -> Unit)? = null, onClose: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    val compact = LocalGHCompact.current
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.surface)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(
                    top = if (compact) 4.dp else 48.dp,
                    start = if (compact) 8.dp else 16.dp,
                    end = if (compact) 8.dp else 16.dp,
                    bottom = if (compact) 4.dp else 14.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(if (compact) 32.dp else 48.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, Modifier.size(if (compact) 16.dp else 22.dp), tint = Blue)
            }
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = if (compact) 15.sp else 24.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null && !compact) Text(subtitle, fontSize = 13.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (onMinimize != null && !compact) IconButton(onClick = onMinimize) { Icon(Icons.Rounded.PictureInPictureAlt, null, Modifier.size(20.dp), tint = Blue) }
            if (onClose != null && !compact) IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, null, Modifier.size(20.dp), tint = Color(0xFFFF3B30)) }
            actions()
        }
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(colors.outlineVariant.copy(alpha = 0.08f)))
    }
}
