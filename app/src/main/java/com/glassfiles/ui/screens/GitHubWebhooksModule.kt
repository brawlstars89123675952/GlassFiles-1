package com.glassfiles.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glassfiles.data.github.GHWebhook
import com.glassfiles.data.github.GitHubManager
import com.glassfiles.ui.theme.*
import kotlinx.coroutines.launch

@Composable
internal fun WebhooksScreen(
    repoOwner: String,
    repoName: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var webhooks by remember { mutableStateOf<List<GHWebhook>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var showCreate by remember { mutableStateOf(false) }

    LaunchedEffect(repoOwner, repoName) {
        webhooks = GitHubManager.getWebhooks(context, repoOwner, repoName)
        loading = false
    }

    Column(Modifier.fillMaxSize().background(SurfaceLight)) {
        GHTopBar(
            title = "Webhooks",
            subtitle = "$repoOwner/$repoName",
            onBack = onBack,
            actions = {
                IconButton(onClick = { showCreate = true }) {
                    Icon(Icons.Rounded.Add, null, Modifier.size(20.dp), tint = Blue)
                }
            }
        )

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Blue, modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
            }
        } else if (webhooks.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No webhooks configured", fontSize = 14.sp, color = TextTertiary)
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(webhooks) { hook ->
                    WebhookCard(hook, repoOwner, repoName) {
                        scope.launch {
                            GitHubManager.deleteWebhook(context, repoOwner, repoName, hook.id)
                            webhooks = GitHubManager.getWebhooks(context, repoOwner, repoName)
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        CreateWebhookDialog(repoOwner, repoName, { showCreate = false }) {
            showCreate = false
            scope.launch { webhooks = GitHubManager.getWebhooks(context, repoOwner, repoName) }
        }
    }
}

@Composable
private fun WebhookCard(hook: GHWebhook, owner: String, repo: String, onDelete: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceWhite).padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(
                if (hook.active) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                null, Modifier.size(18.dp),
                tint = if (hook.active) Color(0xFF34C759) else TextTertiary
            )
            Text(hook.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, modifier = Modifier.weight(1f))
            IconButton(onClick = onDelete) {
                Icon(Icons.Rounded.Delete, null, Modifier.size(18.dp), tint = Color(0xFFFF3B30))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(hook.url, fontSize = 12.sp, color = Blue, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        if (hook.events.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                hook.events.take(5).forEach { event ->
                    Text(event, fontSize = 10.sp, color = TextSecondary, modifier = Modifier.background(SurfaceLight, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
                }
                if (hook.events.size > 5) {
                    Text("+${hook.events.size - 5}", fontSize = 10.sp, color = TextTertiary)
                }
            }
        }
    }
}

@Composable
private fun CreateWebhookDialog(owner: String, repo: String, onDismiss: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("") }
    var events by remember { mutableStateOf("push,pull_request") }
    var saving by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceWhite,
        title = { Text("Create Webhook", fontWeight = FontWeight.Bold, color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Payload URL") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = events,
                    onValueChange = { events = it },
                    label = { Text("Events (comma-separated)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Text("Common: push, pull_request, issues, release", fontSize = 11.sp, color = TextTertiary)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (url.isBlank() || saving) return@TextButton
                    saving = true
                    scope.launch {
                        val ok = GitHubManager.createWebhook(
                            context, owner, repo,
                            config = mapOf("url" to url, "content_type" to "json"),
                            events = events.split(",").map { it.trim() }.filter { it.isNotBlank() }
                        )
                        Toast.makeText(context, if (ok) "Webhook created" else "Failed", Toast.LENGTH_SHORT).show()
                        saving = false
                        if (ok) onDone()
                    }
                },
                enabled = !saving
            ) { Text("Create", color = Blue) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) } }
    )
}
