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
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glassfiles.data.github.GHWebhook
import com.glassfiles.data.github.GHWebhookDelivery
import com.glassfiles.data.github.GitHubManager
import com.glassfiles.ui.theme.*
import kotlinx.coroutines.launch

private val WEBHOOK_EVENT_PRESETS = listOf(
    "push", "pull_request", "workflow_run", "issues", "release", "repository_dispatch"
)

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
    var actionInFlight by remember { mutableStateOf(false) }
    var showEditor by remember { mutableStateOf<GHWebhook?>(null) }
    var createNew by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<GHWebhook?>(null) }
    var deliveriesHook by remember { mutableStateOf<GHWebhook?>(null) }
    var query by remember { mutableStateOf("") }

    fun loadWebhooks() {
        loading = true
        scope.launch {
            webhooks = GitHubManager.getWebhooks(context, repoOwner, repoName)
            loading = false
        }
    }

    LaunchedEffect(repoOwner, repoName) { loadWebhooks() }

    deliveriesHook?.let { hook ->
        WebhookDeliveriesScreen(
            repoOwner = repoOwner,
            repoName = repoName,
            hook = hook,
            onBack = { deliveriesHook = null }
        )
        return
    }

    Column(Modifier.fillMaxSize().background(SurfaceLight)) {
        GHTopBar(
            title = "Webhooks",
            subtitle = "$repoOwner/$repoName",
            onBack = onBack,
            actions = {
                IconButton(onClick = { loadWebhooks() }, enabled = !loading) {
                    Icon(Icons.Rounded.Refresh, null, Modifier.size(20.dp), tint = Blue)
                }
                IconButton(onClick = { createNew = true }) {
                    Icon(Icons.Rounded.Add, null, Modifier.size(20.dp), tint = Blue)
                }
            }
        )

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Blue, modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
            }
        } else {
            val visibleHooks = webhooks.filter { hook ->
                query.isBlank() ||
                    hook.url.contains(query, ignoreCase = true) ||
                    hook.events.any { it.contains(query, ignoreCase = true) } ||
                    webhookLastResponseLabel(hook).contains(query, ignoreCase = true)
            }

            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item { WebhooksSummaryCard(webhooks) }
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Search URL, event or status") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Rounded.Search, null, Modifier.size(18.dp), tint = TextSecondary) }
                    )
                }

                items(visibleHooks, key = { it.id }) { hook ->
                    WebhookCard(
                        hook = hook,
                        disabled = actionInFlight,
                        onDeliveries = { deliveriesHook = hook },
                        onEdit = { showEditor = hook },
                        onPing = {
                            if (!actionInFlight) {
                                actionInFlight = true
                                scope.launch {
                                    val ok = GitHubManager.pingWebhook(context, repoOwner, repoName, hook.id)
                                    Toast.makeText(context, if (ok) "Ping sent" else "Failed to ping webhook", Toast.LENGTH_SHORT).show()
                                    webhooks = GitHubManager.getWebhooks(context, repoOwner, repoName)
                                    actionInFlight = false
                                }
                            }
                        },
                        onDelete = { deleteTarget = hook }
                    )
                }

                if (visibleHooks.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text(if (webhooks.isEmpty()) "No webhooks configured" else "No matching webhooks", fontSize = 14.sp, color = TextTertiary)
                        }
                    }
                }
            }
        }
    }

    if (createNew || showEditor != null) {
        WebhookEditorDialog(
            webhook = showEditor,
            onDismiss = {
                createNew = false
                showEditor = null
            },
            onSave = { hook, url, events, secret, active, contentType, insecureSsl ->
                if (!actionInFlight) {
                    actionInFlight = true
                    scope.launch {
                        val config = mutableMapOf(
                            "url" to url,
                            "content_type" to contentType,
                            "insecure_ssl" to if (insecureSsl) "1" else "0"
                        )
                        if (secret.isNotBlank()) config["secret"] = secret
                        val ok = if (hook == null) {
                            GitHubManager.createWebhook(context, repoOwner, repoName, config, events, active)
                        } else {
                            GitHubManager.updateWebhook(context, repoOwner, repoName, hook.id, config, events, active)
                        }
                        Toast.makeText(context, if (ok) "Webhook saved" else "Failed to save webhook", Toast.LENGTH_SHORT).show()
                        if (ok) {
                            createNew = false
                            showEditor = null
                            webhooks = GitHubManager.getWebhooks(context, repoOwner, repoName)
                        }
                        actionInFlight = false
                    }
                }
            }
        )
    }

    deleteTarget?.let { hook ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = SurfaceWhite,
            title = { Text("Delete webhook", fontWeight = FontWeight.Bold, color = TextPrimary) },
            text = { Text(hook.url.ifBlank { "Webhook #${hook.id}" }, color = TextSecondary, fontSize = 13.sp) },
            confirmButton = {
                TextButton(
                    enabled = !actionInFlight,
                    onClick = {
                        actionInFlight = true
                        scope.launch {
                            val ok = GitHubManager.deleteWebhook(context, repoOwner, repoName, hook.id)
                            Toast.makeText(context, if (ok) "Webhook deleted" else "Failed to delete webhook", Toast.LENGTH_SHORT).show()
                            if (ok) webhooks = GitHubManager.getWebhooks(context, repoOwner, repoName)
                            deleteTarget = null
                            actionInFlight = false
                        }
                    }
                ) { Text("Delete", color = Color(0xFFFF3B30)) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel", color = TextSecondary) } }
        )
    }
}

@Composable
private fun WebhooksSummaryCard(webhooks: List<GHWebhook>) {
    val active = webhooks.count { it.active }
    val inactive = webhooks.size - active
    val failing = webhooks.count { it.lastResponseCode >= 400 || it.lastResponseStatus.equals("failed", ignoreCase = true) }

    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceWhite).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Rounded.Webhook, null, Modifier.size(20.dp), tint = Blue)
            Text("Delivery endpoints", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, modifier = Modifier.weight(1f))
            Text("${webhooks.size}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            WebhookPill("Active $active", Color(0xFF34C759))
            WebhookPill("Inactive $inactive", TextTertiary)
            WebhookPill("Failing $failing", if (failing > 0) Color(0xFFFF3B30) else TextTertiary)
        }
    }
}

@Composable
private fun WebhookCard(
    hook: GHWebhook,
    disabled: Boolean,
    onDeliveries: () -> Unit,
    onEdit: () -> Unit,
    onPing: () -> Unit,
    onDelete: () -> Unit
) {
    val responseColor = webhookResponseColor(hook)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceWhite).padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(
                if (hook.active) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                null,
                Modifier.size(18.dp),
                tint = if (hook.active) Color(0xFF34C759) else TextTertiary
            )
            Column(Modifier.weight(1f)) {
                Text(hook.url.ifBlank { "Webhook #${hook.id}" }, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${hook.contentType.ifBlank { "json" }} · ${if (hook.active) "Active" else "Inactive"}", fontSize = 11.sp, color = TextTertiary)
            }
            IconButton(onClick = onPing, enabled = !disabled) {
                Icon(Icons.Rounded.Send, null, Modifier.size(18.dp), tint = Blue)
            }
            IconButton(onClick = onDeliveries, enabled = !disabled) {
                Icon(Icons.Rounded.History, null, Modifier.size(18.dp), tint = Blue)
            }
            IconButton(onClick = onEdit, enabled = !disabled) {
                Icon(Icons.Rounded.Edit, null, Modifier.size(18.dp), tint = TextSecondary)
            }
            IconButton(onClick = onDelete, enabled = !disabled) {
                Icon(Icons.Rounded.Delete, null, Modifier.size(18.dp), tint = Color(0xFFFF3B30))
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            hook.events.take(8).forEach { event -> WebhookPill(event, TextSecondary) }
            if (hook.events.size > 8) WebhookPill("+${hook.events.size - 8}", TextTertiary)
            if (hook.insecureSsl == "1") WebhookPill("SSL off", Color(0xFFFF9500))
        }

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(RoundedCornerShape(8.dp)).background(responseColor))
            Text(webhookLastResponseLabel(hook), fontSize = 12.sp, color = responseColor, fontWeight = FontWeight.Medium)
            if (hook.updatedAt.isNotBlank()) Text("Updated ${hook.updatedAt.take(10)}", fontSize = 11.sp, color = TextTertiary)
        }
        if (hook.lastResponseMessage.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(hook.lastResponseMessage, fontSize = 11.sp, color = TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun WebhookEditorDialog(
    webhook: GHWebhook?,
    onDismiss: () -> Unit,
    onSave: (webhook: GHWebhook?, url: String, events: List<String>, secret: String, active: Boolean, contentType: String, insecureSsl: Boolean) -> Unit
) {
    var url by remember(webhook) { mutableStateOf(webhook?.url ?: "") }
    var eventsRaw by remember(webhook) { mutableStateOf((webhook?.events ?: listOf("push")).joinToString(",")) }
    var secret by remember(webhook) { mutableStateOf("") }
    var active by remember(webhook) { mutableStateOf(webhook?.active ?: true) }
    var contentType by remember(webhook) { mutableStateOf(webhook?.contentType?.takeIf { it.isNotBlank() } ?: "json") }
    var insecureSsl by remember(webhook) { mutableStateOf(webhook?.insecureSsl == "1") }
    val events = eventsRaw.split(",").map { it.trim() }.filter { it.isNotBlank() }.distinct()
    val canSave = url.startsWith("http://") || url.startsWith("https://")

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceWhite,
        title = { Text(if (webhook == null) "Add webhook" else "Edit webhook", fontWeight = FontWeight.Bold, color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Payload URL") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    listOf("json", "form").forEach { type ->
                        WebhookChoiceChip(type, selected = contentType == type) { contentType = type }
                    }
                }
                OutlinedTextField(
                    value = eventsRaw,
                    onValueChange = { eventsRaw = it },
                    label = { Text("Events") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    WEBHOOK_EVENT_PRESETS.forEach { event ->
                        WebhookChoiceChip(event, selected = event in events) {
                            eventsRaw = if (event in events) {
                                events.filterNot { it == event }.joinToString(",")
                            } else {
                                (events + event).distinct().joinToString(",")
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = secret,
                    onValueChange = { secret = it },
                    label = { Text(if (webhook == null) "Secret (optional)" else "New secret (leave blank to keep)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Text("GitHub never returns existing webhook secrets.", fontSize = 11.sp, color = TextTertiary)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Switch(checked = active, onCheckedChange = { active = it }, colors = SwitchDefaults.colors(checkedThumbColor = Blue))
                    Text("Active", fontSize = 13.sp, color = TextPrimary)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Checkbox(checked = insecureSsl, onCheckedChange = { insecureSsl = it })
                    Text("Disable SSL verification", fontSize = 13.sp, color = TextPrimary)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (!canSave) return@TextButton
                    onSave(webhook, url.trim(), events.ifEmpty { listOf("push") }, secret, active, contentType, insecureSsl)
                },
                enabled = canSave
            ) { Text("Save", color = Blue) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) } }
    )
}

@Composable
private fun WebhookDeliveriesScreen(
    repoOwner: String,
    repoName: String,
    hook: GHWebhook,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var deliveries by remember(hook.id) { mutableStateOf<List<GHWebhookDelivery>>(emptyList()) }
    var loading by remember(hook.id) { mutableStateOf(true) }
    var actionInFlight by remember { mutableStateOf(false) }
    var selectedDelivery by remember { mutableStateOf<GHWebhookDelivery?>(null) }
    var query by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("all") }

    fun loadDeliveries() {
        loading = true
        scope.launch {
            deliveries = GitHubManager.getWebhookDeliveries(context, repoOwner, repoName, hook.id)
            loading = false
        }
    }

    LaunchedEffect(hook.id) { loadDeliveries() }

    Column(Modifier.fillMaxSize().background(SurfaceLight)) {
        GHTopBar(
            title = "Webhook deliveries",
            subtitle = hook.url.ifBlank { "$repoOwner/$repoName" },
            onBack = onBack,
            actions = {
                IconButton(onClick = { loadDeliveries() }, enabled = !loading) {
                    Icon(Icons.Rounded.Refresh, null, Modifier.size(20.dp), tint = Blue)
                }
            }
        )

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Blue, modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
            }
        } else {
            val visibleDeliveries = deliveries.filter { delivery ->
                val statusOk = when (statusFilter) {
                    "success" -> delivery.statusCode in 200..299
                    "failed" -> delivery.statusCode >= 300 || delivery.status.equals("failed", ignoreCase = true)
                    "redelivery" -> delivery.redelivery
                    else -> true
                }
                val q = query.trim()
                statusOk && (
                    q.isBlank() ||
                        delivery.event.contains(q, ignoreCase = true) ||
                        delivery.action.contains(q, ignoreCase = true) ||
                        delivery.guid.contains(q, ignoreCase = true) ||
                        delivery.status.contains(q, ignoreCase = true) ||
                        delivery.statusCode.toString().contains(q)
                    )
            }

            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item { DeliverySummaryCard(deliveries) }
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Search event, guid or status") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Rounded.Search, null, Modifier.size(18.dp), tint = TextSecondary) }
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        listOf("all", "success", "failed", "redelivery").forEach { filter ->
                            WebhookChoiceChip(filter.replaceFirstChar { it.uppercase() }, selected = statusFilter == filter) {
                                statusFilter = filter
                            }
                        }
                    }
                }
                items(visibleDeliveries, key = { it.id }) { delivery ->
                    DeliveryCard(
                        delivery = delivery,
                        disabled = actionInFlight,
                        onOpen = {
                            scope.launch {
                                selectedDelivery = GitHubManager.getWebhookDelivery(context, repoOwner, repoName, hook.id, delivery.id) ?: delivery
                            }
                        },
                        onRedeliver = {
                            if (actionInFlight) return@DeliveryCard
                            actionInFlight = true
                            scope.launch {
                                val ok = GitHubManager.redeliverWebhookDelivery(context, repoOwner, repoName, hook.id, delivery.id)
                                Toast.makeText(context, if (ok) "Redelivery queued" else "Failed to redeliver", Toast.LENGTH_SHORT).show()
                                deliveries = GitHubManager.getWebhookDeliveries(context, repoOwner, repoName, hook.id)
                                actionInFlight = false
                            }
                        }
                    )
                }
                if (visibleDeliveries.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text(if (deliveries.isEmpty()) "No deliveries found" else "No matching deliveries", fontSize = 14.sp, color = TextTertiary)
                        }
                    }
                }
            }
        }
    }

    selectedDelivery?.let { delivery ->
        DeliveryDetailDialog(
            delivery = delivery,
            onDismiss = { selectedDelivery = null },
            onRedeliver = {
                if (actionInFlight) return@DeliveryDetailDialog
                actionInFlight = true
                scope.launch {
                    val ok = GitHubManager.redeliverWebhookDelivery(context, repoOwner, repoName, hook.id, delivery.id)
                    Toast.makeText(context, if (ok) "Redelivery queued" else "Failed to redeliver", Toast.LENGTH_SHORT).show()
                    deliveries = GitHubManager.getWebhookDeliveries(context, repoOwner, repoName, hook.id)
                    actionInFlight = false
                }
            }
        )
    }
}

@Composable
private fun DeliverySummaryCard(deliveries: List<GHWebhookDelivery>) {
    val success = deliveries.count { it.statusCode in 200..299 }
    val failed = deliveries.count { it.statusCode >= 300 || it.status.equals("failed", ignoreCase = true) }
    val redeliveries = deliveries.count { it.redelivery }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceWhite).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Rounded.History, null, Modifier.size(20.dp), tint = Blue)
            Text("Recent deliveries", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, modifier = Modifier.weight(1f))
            Text("${deliveries.size}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            WebhookPill("Success $success", Color(0xFF34C759))
            WebhookPill("Failed $failed", if (failed > 0) Color(0xFFFF3B30) else TextTertiary)
            WebhookPill("Redelivered $redeliveries", TextSecondary)
        }
    }
}

@Composable
private fun DeliveryCard(
    delivery: GHWebhookDelivery,
    disabled: Boolean,
    onOpen: () -> Unit,
    onRedeliver: () -> Unit
) {
    val color = deliveryStatusColor(delivery)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceWhite).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(if (delivery.statusCode in 200..299) Icons.Rounded.CheckCircle else Icons.Rounded.Error, null, Modifier.size(18.dp), tint = color)
            Column(Modifier.weight(1f)) {
                Text(delivery.event.ifBlank { "Delivery ${delivery.id}" }, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(delivery.guid.ifBlank { delivery.deliveredAt.take(19).replace('T', ' ') }, fontSize = 11.sp, color = TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onOpen, enabled = !disabled) {
                Icon(Icons.Rounded.Article, null, Modifier.size(18.dp), tint = Blue)
            }
            IconButton(onClick = onRedeliver, enabled = !disabled) {
                Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp), tint = Color(0xFFFF9500))
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            WebhookPill(deliveryStatusLabel(delivery), color)
            if (delivery.action.isNotBlank()) WebhookPill(delivery.action, TextSecondary)
            if (delivery.redelivery) WebhookPill("redelivery", Color(0xFFFF9500))
            if (delivery.duration > 0.0) WebhookPill("${"%.2f".format(delivery.duration)}s", TextTertiary)
            if (delivery.deliveredAt.isNotBlank()) WebhookPill(delivery.deliveredAt.take(10), TextTertiary)
        }
    }
}

@Composable
private fun DeliveryDetailDialog(
    delivery: GHWebhookDelivery,
    onDismiss: () -> Unit,
    onRedeliver: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceWhite,
        title = { Text("Delivery ${delivery.id}", fontWeight = FontWeight.Bold, color = TextPrimary) },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    WebhookPill(delivery.event.ifBlank { "event" }, TextSecondary)
                    WebhookPill(deliveryStatusLabel(delivery), deliveryStatusColor(delivery))
                    if (delivery.redelivery) WebhookPill("redelivery", Color(0xFFFF9500))
                }
                DeliveryBlock("Request headers", formatHeaders(delivery.requestHeaders))
                DeliveryBlock("Request payload", prettyPayload(delivery.requestPayload))
                DeliveryBlock("Response headers", formatHeaders(delivery.responseHeaders))
                DeliveryBlock("Response payload", prettyPayload(delivery.responsePayload))
            }
        },
        confirmButton = {
            TextButton(onClick = onRedeliver) { Text("Redeliver", color = Color(0xFFFF9500)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close", color = TextSecondary) } }
    )
}

@Composable
private fun DeliveryBlock(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(SurfaceLight).padding(8.dp)) {
            Text(body.ifBlank { "No data" }, fontSize = 10.sp, color = TextSecondary, lineHeight = 14.sp)
        }
    }
}

@Composable
private fun WebhookPill(label: String, color: Color) {
    Text(
        label,
        fontSize = 11.sp,
        color = color,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.background(color.copy(alpha = 0.1f), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

@Composable
private fun WebhookChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(8.dp))
            .background(if (selected) Blue.copy(alpha = 0.14f) else SurfaceLight)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(label, fontSize = 12.sp, color = if (selected) Blue else TextSecondary, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

private fun webhookLastResponseLabel(hook: GHWebhook): String {
    val status = hook.lastResponseStatus.takeIf { it.isNotBlank() && it != "null" }
    val code = hook.lastResponseCode.takeIf { it > 0 }?.toString()
    return listOfNotNull(status, code).joinToString(" · ").ifBlank { "No delivery response yet" }
}

private fun webhookResponseColor(hook: GHWebhook): Color = when {
    hook.lastResponseCode in 200..299 || hook.lastResponseStatus.equals("ok", ignoreCase = true) -> Color(0xFF34C759)
    hook.lastResponseCode >= 400 || hook.lastResponseStatus.equals("failed", ignoreCase = true) -> Color(0xFFFF3B30)
    else -> TextTertiary
}

private fun deliveryStatusLabel(delivery: GHWebhookDelivery): String {
    val status = delivery.status.takeIf { it.isNotBlank() && it != "null" }
    val code = delivery.statusCode.takeIf { it > 0 }?.toString()
    return listOfNotNull(status, code).joinToString(" · ").ifBlank { "No response" }
}

private fun deliveryStatusColor(delivery: GHWebhookDelivery): Color = when {
    delivery.statusCode in 200..299 -> Color(0xFF34C759)
    delivery.statusCode >= 300 || delivery.status.equals("failed", ignoreCase = true) -> Color(0xFFFF3B30)
    else -> TextTertiary
}

private fun formatHeaders(headers: List<Pair<String, String>>): String =
    headers.joinToString("\n") { (key, value) -> "$key: $value" }

private fun prettyPayload(payload: String): String {
    if (payload.isBlank() || payload == "null") return ""
    return payload.take(12000)
}
