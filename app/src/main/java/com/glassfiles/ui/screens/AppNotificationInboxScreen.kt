package com.glassfiles.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.glassfiles.notifications.AppNotificationInboxStore
import com.glassfiles.notifications.AppNotificationPreferences
import com.glassfiles.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AppNotificationInboxScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var history by remember { mutableStateOf(AppNotificationInboxStore.list(context)) }

    Column(Modifier.fillMaxSize().background(SurfaceLight)) {
        Row(
            Modifier.fillMaxWidth().background(SurfaceWhite)
                .padding(top = 44.dp, start = 4.dp, end = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, Modifier.size(20.dp), tint = Blue)
            }
            Text(
                "Notification history",
                modifier = Modifier.weight(1f),
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                fontSize = 20.sp
            )
            IconButton(onClick = {
                AppNotificationInboxStore.markAllRead(context)
                history = AppNotificationInboxStore.list(context)
            }) {
                Icon(Icons.Rounded.DoneAll, null, Modifier.size(20.dp), tint = Blue)
            }
            IconButton(onClick = {
                AppNotificationInboxStore.clear(context)
                history = emptyList()
            }) {
                Icon(Icons.Rounded.DeleteSweep, null, Modifier.size(20.dp), tint = Red)
            }
        }

        if (history.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Rounded.Notifications, null, Modifier.size(48.dp), tint = TextTertiary)
                    Text("No notification history", color = TextSecondary, fontSize = 15.sp)
                }
            }
            return@Column
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(history, key = { it.id }) { item ->
                NotificationHistoryRow(item) {
                    AppNotificationInboxStore.markRead(context, item.id)
                    history = AppNotificationInboxStore.list(context)
                }
            }
        }
    }
}

@Composable
private fun NotificationHistoryRow(
    item: AppNotificationInboxStore.InboxItem,
    onMarkRead: () -> Unit
) {
    val date = remember(item.createdAt) {
        SimpleDateFormat("MMM d, HH:mm", Locale.US).format(Date(item.createdAt))
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(SurfaceWhite)
            .padding(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            Modifier.padding(top = 4.dp).size(9.dp).clip(CircleShape)
                .background(if (item.read) SeparatorColor else Blue)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    AppNotificationPreferences.displayName(item.source),
                    color = Blue,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.width(8.dp))
                Text(date, color = TextTertiary, fontSize = 11.sp)
            }
            Spacer(Modifier.height(3.dp))
            Text(item.title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            if (item.body.isNotBlank()) {
                Text(item.body, color = TextSecondary, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
        if (!item.read) {
            TextButton(onClick = onMarkRead) {
                Text("Read", color = Blue, fontSize = 12.sp)
            }
        }
    }
}
