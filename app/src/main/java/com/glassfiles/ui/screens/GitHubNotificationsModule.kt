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
import com.glassfiles.data.github.GHNotification
import com.glassfiles.data.github.GitHubManager
import com.glassfiles.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun NotificationsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var notifications by remember { mutableStateOf<List<GHNotification>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var showAll by remember { mutableStateOf(false) }

    LaunchedEffect(showAll) {
        notifications = GitHubManager.getNotifications(context, showAll)
        loading = false
    }

    Column(Modifier.fillMaxSize().background(SurfaceLight)) {
        GHTopBar(
            title = "Notifications",
            onBack = onBack,
            actions = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(
                        selected = !showAll,
                        onClick = { showAll = false },
                        label = { Text("Unread", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = showAll,
                        onClick = { showAll = true },
                        label = { Text("All", fontSize = 11.sp) }
                    )
                }
                IconButton(onClick = {
                    scope.launch {
                        GitHubManager.markAllNotificationsRead(context)
                        notifications = GitHubManager.getNotifications(context, showAll)
                    }
                }) {
                    Icon(Icons.Rounded.DoneAll, null, Modifier.size(20.dp), tint = Blue)
                }
            }
        )

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Blue)
            }
            return@Column
        }

        if (notifications.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.NotificationsNone,
                title = "No notifications",
                subtitle = if (showAll) "You're all caught up!" else "No unread notifications"
            )
            return@Column
        }

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
            items(notifications.size) { index ->
                val notification = notifications[index]
                NotificationCard(notification) {
                    scope.launch {
                        GitHubManager.markNotificationRead(context, notification.id)
                        notifications = GitHubManager.getNotifications(context, showAll)
                    }
                }
                if (index < notifications.lastIndex) Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun NotificationCard(notification: GHNotification, onMarkRead: () -> Unit) {
    val icon = when (notification.type) {
        "PullRequest" -> Icons.Rounded.MergeType
        "Issue" -> Icons.Rounded.ErrorOutline
        "Release" -> Icons.Rounded.NewReleases
        "Commit" -> Icons.Rounded.Commit
        else -> Icons.Rounded.Notifications
    }

    val reasonColor = when (notification.reason) {
        "mention" -> Color(0xFFFF9500)
        "assign" -> Color(0xFF5856D6)
        "review_requested" -> Color(0xFF34C759)
        else -> TextTertiary
    }

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(SurfaceWhite)
            .clickable { onMarkRead() }
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(if (notification.unread) Blue.copy(0.1f) else SurfaceLight), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(18.dp), tint = if (notification.unread) Blue else TextSecondary)
            }
            Column(Modifier.weight(1f)) {
                Text(notification.title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(notification.repoName.substringAfter("/"), fontSize = 11.sp, color = TextSecondary)
                    Text("•", fontSize = 11.sp, color = TextTertiary)
                    Text(notification.type, fontSize = 11.sp, color = TextSecondary)
                }
            }
            if (notification.unread) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(Blue))
            }
        }
        if (notification.reason.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(notification.reason.replace("_", " "), fontSize = 11.sp, color = reasonColor, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, Modifier.size(64.dp), tint = TextTertiary)
            Spacer(Modifier.height(16.dp))
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = TextSecondary)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, fontSize = 14.sp, color = TextTertiary)
        }
    }
}
