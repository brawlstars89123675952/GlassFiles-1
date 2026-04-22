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

internal val LocalGHCompact = compositionLocalOf { false }

@Composable
fun GitHubScreen(onBack: () -> Unit, onMinimize: () -> Unit = {}, onClose: (() -> Unit)? = null, compact: Boolean = false) {
    CompositionLocalProvider(LocalGHCompact provides compact) {
    val context = LocalContext.current
    var isLoggedIn by remember { mutableStateOf(GitHubManager.isLoggedIn(context)) }
    var user by remember { mutableStateOf(GitHubManager.getCachedUser(context)) }
    var selectedRepo by remember { mutableStateOf<GHRepo?>(null) }
    var showGists by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showNotifications by remember { mutableStateOf(false) }
    var showProfile by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(isLoggedIn) { if (isLoggedIn) user = GitHubManager.getUser(context) }
    when {
        !isLoggedIn -> LoginScreen(onBack, onMinimize, onClose) { GitHubManager.saveToken(context, it); isLoggedIn = true }
        showSettings -> GitHubSettingsScreen(onBack = { showSettings = false }, onLogout = { GitHubManager.logout(context); isLoggedIn = false; user = null; showSettings = false }, onClose = onClose)
        showGists -> GistsScreen({ showGists = false }, onMinimize, onClose)
        showNotifications -> NotificationsScreen(onBack = { showNotifications = false })
        showProfile != null -> ProfileScreen(username = showProfile!!, onBack = { showProfile = null }, onRepoClick = { selectedRepo = it })
        selectedRepo != null -> RepoDetailScreen(selectedRepo!!, { selectedRepo = null }, onMinimize, onClose)
        else -> ReposScreen(user, onBack, onMinimize, onClose, { GitHubManager.logout(context); isLoggedIn = false; user = null }, { selectedRepo = it }, { showGists = true }, { showSettings = true }, { showNotifications = true }, { showProfile = it })
    }
    }
}

@Composable
internal fun GHTopBar(title: String, subtitle: String? = null, onBack: () -> Unit, onMinimize: (() -> Unit)? = null, onClose: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    val compact = LocalGHCompact.current
    val shape = if (compact) RoundedCornerShape(0.dp) else RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
    Row(Modifier.fillMaxWidth().background(SurfaceWhite, shape).padding(
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
        if (onClose != null && !compact) IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, null, Modifier.size(20.dp), tint = Color(0xFFFF3B30)) }
        actions()
    }
}
