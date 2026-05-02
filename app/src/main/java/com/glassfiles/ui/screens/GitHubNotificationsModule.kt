package com.glassfiles.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Commit
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.automirrored.rounded.MergeType
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.glassfiles.data.github.GHNotification
import com.glassfiles.data.github.GHThreadSubscription
import com.glassfiles.data.github.GitHubManager
import com.glassfiles.ui.components.AiModuleAlertDialog
import com.glassfiles.ui.components.AiModuleHairline
import com.glassfiles.ui.components.AiModuleIcon as Icon
import com.glassfiles.ui.components.AiModuleIconButton as IconButton
import com.glassfiles.ui.components.AiModulePillButton
import com.glassfiles.ui.components.AiModuleSpinner
import com.glassfiles.ui.components.AiModuleText as Text
import com.glassfiles.ui.components.AiModuleTextAction
import com.glassfiles.ui.theme.AiModuleTheme
import com.glassfiles.ui.theme.JetBrainsMono
import kotlinx.coroutines.launch

@Composable
fun NotificationsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var notifications by remember { mutableStateOf<List<GHNotification>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var showAll by remember { mutableStateOf(false) }
    var selectedSubscription by remember { mutableStateOf<GHNotification?>(null) }

    LaunchedEffect(showAll) {
        loading = true
        notifications = GitHubManager.getNotifications(context, showAll)
        loading = false
    }

    val palette = AiModuleTheme.colors

    GitHubScreenFrame(
        title = "> notifications",
        onBack = onBack,
        subtitle = if (showAll) "scope: all" else "scope: unread",
        trailing = {
            GitHubTopBarAction(
                glyph = GhGlyphs.CHECK,
                onClick = {
                    scope.launch {
                        GitHubManager.markAllNotificationsRead(context)
                        notifications = GitHubManager.getNotifications(context, showAll)
                    }
                },
                tint = palette.accent,
                contentDescription = "mark all read",
            )
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AiModulePillButton(
                    label = "unread",
                    onClick = { showAll = false },
                    accent = !showAll,
                )
                AiModulePillButton(
                    label = "all",
                    onClick = { showAll = true },
                    accent = showAll,
                )
            }
            AiModuleHairline()

            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    AiModuleSpinner(label = "loading inbox…")
                }
                notifications.isEmpty() -> GitHubMonoEmpty(
                    title = if (showAll) "inbox empty" else "no unread notifications",
                    subtitle = if (showAll) "you're all caught up" else "switch to all to see read items",
                )
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
                ) {
                    items(notifications, key = { it.id }) { notification ->
                        NotificationRow(
                            notification = notification,
                            onMarkRead = {
                                scope.launch {
                                    GitHubManager.markNotificationRead(context, notification.id)
                                    notifications = GitHubManager.getNotifications(context, showAll)
                                }
                            },
                            onSubscription = { selectedSubscription = notification },
                        )
                        AiModuleHairline()
                    }
                }
            }
        }
    }

    selectedSubscription?.let { notification ->
        NotificationSubscriptionDialog(
            notification = notification,
            onDismiss = { selectedSubscription = null },
        )
    }
}

@Composable
private fun NotificationRow(
    notification: GHNotification,
    onMarkRead: () -> Unit,
    onSubscription: () -> Unit,
) {
    val palette = AiModuleTheme.colors
    val icon: ImageVector = when (notification.type) {
        "PullRequest" -> Icons.AutoMirrored.Rounded.MergeType
        "Issue" -> Icons.Rounded.ErrorOutline
        "Release" -> Icons.Rounded.NewReleases
        "Commit" -> Icons.Rounded.Commit
        else -> Icons.Rounded.Notifications
    }
    val typeGlyph = when (notification.type) {
        "PullRequest" -> "PR"
        "Issue" -> "IS"
        "Release" -> "RL"
        "Commit" -> "CT"
        else -> "··"
    }
    val reasonColor = when (notification.reason) {
        "mention" -> palette.warning
        "assign" -> palette.accent
        "review_requested" -> palette.accent
        else -> palette.textMuted
    }

    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onMarkRead() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (notification.unread) "●" else "·",
            color = if (notification.unread) palette.accent else palette.textMuted,
            fontFamily = JetBrainsMono,
            fontSize = 14.sp,
            modifier = Modifier.width(14.dp),
        )
        Text(
            text = typeGlyph,
            color = if (notification.unread) palette.accent else palette.textSecondary,
            fontFamily = JetBrainsMono,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            modifier = Modifier.width(24.dp),
        )
        Spacer(Modifier.width(2.dp))
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = if (notification.unread) palette.accent else palette.textSecondary,
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                notification.title,
                color = palette.textPrimary,
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                lineHeight = 1.3.em,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    notification.repoName.substringAfter("/"),
                    color = palette.textSecondary,
                    fontFamily = JetBrainsMono,
                    fontSize = 11.sp,
                )
                if (notification.reason.isNotBlank()) {
                    Text(
                        " · ${notification.reason.replace("_", " ")}",
                        color = reasonColor,
                        fontFamily = JetBrainsMono,
                        fontSize = 11.sp,
                    )
                }
            }
        }
        IconButton(onClick = onSubscription, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Rounded.Tune,
                contentDescription = "subscription",
                modifier = Modifier.size(16.dp),
                tint = palette.textSecondary,
            )
        }
    }
}

@Composable
private fun NotificationSubscriptionDialog(notification: GHNotification, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loading by remember(notification.id) { mutableStateOf(true) }
    var actionInFlight by remember { mutableStateOf(false) }
    var subscription by remember(notification.id) { mutableStateOf<GHThreadSubscription?>(null) }

    fun loadSubscription() {
        loading = true
        scope.launch {
            subscription = GitHubManager.getThreadSubscription(context, notification.id)
            loading = false
        }
    }

    LaunchedEffect(notification.id) { loadSubscription() }

    val palette = AiModuleTheme.colors
    AiModuleAlertDialog(
        onDismissRequest = onDismiss,
        title = "subscription",
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    notification.title,
                    color = palette.textSecondary,
                    fontFamily = JetBrainsMono,
                    fontSize = 12.sp,
                    lineHeight = 1.3.em,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (loading) {
                    Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
                        AiModuleSpinner(label = "loading…")
                    }
                } else {
                    val current = subscription
                    val status = when {
                        current?.ignored == true -> "ignored"
                        current?.subscribed == true -> "subscribed"
                        else -> "default"
                    }
                    val statusColor = when {
                        current?.ignored == true -> palette.error
                        current?.subscribed == true -> palette.accent
                        else -> palette.textSecondary
                    }
                    Text(
                        text = "status · $status",
                        color = statusColor,
                        fontFamily = JetBrainsMono,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                    )
                    val reason = current?.reason
                    if (!reason.isNullOrBlank()) {
                        Text(
                            reason,
                            color = palette.textMuted,
                            fontFamily = JetBrainsMono,
                            fontSize = 11.sp,
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    ) {
                        AiModulePillButton(
                            label = "subscribe",
                            enabled = !actionInFlight,
                            onClick = {
                                actionInFlight = true
                                scope.launch {
                                    GitHubManager.setThreadSubscription(context, notification.id, subscribed = true, ignored = false)
                                    actionInFlight = false
                                    loadSubscription()
                                }
                            },
                        )
                        AiModulePillButton(
                            label = "ignore",
                            enabled = !actionInFlight,
                            destructive = true,
                            onClick = {
                                actionInFlight = true
                                scope.launch {
                                    GitHubManager.setThreadSubscription(context, notification.id, subscribed = false, ignored = true)
                                    actionInFlight = false
                                    loadSubscription()
                                }
                            },
                        )
                        AiModulePillButton(
                            label = "default",
                            enabled = !actionInFlight,
                            accent = false,
                            onClick = {
                                actionInFlight = true
                                scope.launch {
                                    GitHubManager.deleteThreadSubscription(context, notification.id)
                                    actionInFlight = false
                                    loadSubscription()
                                }
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            AiModuleTextAction(label = "[ close ]", onClick = onDismiss)
        },
    )
}

/**
 * Centered fullscreen empty placeholder kept for callers that still
 * pass an [ImageVector]; new call sites should use [GitHubMonoEmpty].
 * Style is unified with the AI module mono empty state.
 */
@Composable
fun EmptyState(icon: ImageVector, title: String, subtitle: String) {
    val palette = AiModuleTheme.colors
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, Modifier.size(40.dp), tint = palette.textMuted)
            Spacer(Modifier.height(12.dp))
            Text(
                title,
                color = palette.textSecondary,
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                color = palette.textMuted,
                fontFamily = JetBrainsMono,
                fontSize = 11.sp,
            )
        }
    }
}
