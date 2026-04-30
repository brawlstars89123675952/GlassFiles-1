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
import com.glassfiles.ui.components.AiModuleAlertDialog
import com.glassfiles.ui.components.AiModuleHairline
import com.glassfiles.ui.components.AiModulePageBar
import com.glassfiles.ui.components.AiModuleSearchField
import com.glassfiles.ui.components.AiModuleSpinner
import com.glassfiles.ui.components.AiModuleTextAction
import com.glassfiles.ui.components.AiModuleTextField
import com.glassfiles.data.github.GHWebhookConfig
import com.glassfiles.data.github.GHWebhookDelivery
import com.glassfiles.data.github.GitHubManager
import com.glassfiles.ui.theme.AiModuleTheme
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
    var configHook by remember { mutableStateOf<GHWebhook?>(null) }
    var detailHook by remember { mutableStateOf<GHWebhook?>(null) }
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

    Column(Modifier.fillMaxSize().background(AiModuleTheme.colors.background)) {
        AiModulePageBar(
            title = "> webhooks",
            subtitle = "$repoOwner/$repoName",
            onBack = onBack,
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { loadWebhooks() }, enabled = !loading, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp), tint = AiModuleTheme.colors.accent)
                    }
                    IconButton(onClick = { createNew = true }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Rounded.Add, null, Modifier.size(18.dp), tint = AiModuleTheme.colors.accent)
                    }
                }
            },
        )

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AiModuleTheme.colors.accent, modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
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
                    AiModuleSearchField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = "search URL, event or status",
                    )
                }

                items(visibleHooks, key = { it.id }) { hook ->
                    WebhookCard(
                        hook = hook,
                        disabled = actionInFlight,
                        onOpen = {
                            detailHook = hook
                            scope.launch {
                                detailHook = GitHubManager.getWebhook(context, repoOwner, repoName, hook.id) ?: hook
                            }
                        },
                        onDeliveries = { deliveriesHook = hook },
                        onConfig = { configHook = hook },
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
                        onTest = {
                            if (!actionInFlight) {
                                actionInFlight = true
                                scope.launch {
                                    val ok = GitHubManager.testWebhook(context, repoOwner, repoName, hook.id)
                                    Toast.makeText(context, if (ok) "Test delivery queued" else "Failed to test webhook", Toast.LENGTH_SHORT).show()
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
                            Text(if (webhooks.isEmpty()) "No webhooks configured" else "No matching webhooks", fontSize = 14.sp, color = AiModuleTheme.colors.textMuted)
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

    detailHook?.let { hook ->
        WebhookDetailDialog(
            hook = hook,
            onDismiss = { detailHook = null },
            onDeliveries = {
                detailHook = null
                deliveriesHook = hook
            },
            onConfig = {
                detailHook = null
                configHook = hook
            }
        )
    }

    configHook?.let { hook ->
        WebhookConfigDialog(
            repoOwner = repoOwner,
            repoName = repoName,
            webhook = hook,
            onDismiss = { configHook = null },
            onSaved = {
                configHook = null
                webhooks = it
            }
        )
    }

    deleteTarget?.let { hook ->
        AiModuleAlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = "delete webhook",
            confirmButton = {
                AiModuleTextAction(
                    label = "delete",
                    enabled = !actionInFlight,
                    tint = AiModuleTheme.colors.error,
                    onClick = {
                        actionInFlight = true
                        scope.launch {
                            val ok = GitHubManager.deleteWebhook(context, repoOwner, repoName, hook.id)
                            Toast.makeText(context, if (ok) "Webhook deleted" else "Failed to delete webhook", Toast.LENGTH_SHORT).show()
                            if (ok) webhooks = GitHubManager.getWebhooks(context, repoOwner, repoName)
                            deleteTarget = null
                            actionInFlight = false
                        }
                    },
                )
            },
            dismissButton = {
                AiModuleTextAction(label = "cancel", onClick = { deleteTarget = null }, tint = AiModuleTheme.colors.textSecondary)
            },
        ) {
            Text(hook.url.ifBlank { "Webhook #${hook.id}" }, color = AiModuleTheme.colors.textSecondary, fontSize = 13.sp, fontFamily = JetBrainsMono)
        }
    }
}

@Composable
private fun WebhooksSummaryCard(webhooks: List<GHWebhook>) {
    val active = webhooks.count { it.active }
    val inactive = webhooks.size - active
    val failing = webhooks.count { it.lastResponseCode >= 400 || it.lastResponseStatus.equals("failed", ignoreCase = true) }

    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(AiModuleTheme.colors.surface).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Rounded.Webhook, null, Modifier.size(20.dp), tint = AiModuleTheme.colors.accent)
            Text("Delivery endpoints", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = AiModuleTheme.colors.textPrimary, modifier = Modifier.weight(1f))
            Text("${webhooks.size}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AiModuleTheme.colors.textPrimary)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            WebhookPill("Active $active", Color(0xFF34C759))
            WebhookPill("Inactive $inactive", AiModuleTheme.colors.textMuted)
            WebhookPill("Failing $failing", if (failing > 0) Color(0xFFFF3B30) else AiModuleTheme.colors.textMuted)
        }
    }
}

@Composable
private fun WebhookCard(
    hook: GHWebhook,
    disabled: Boolean,
    onOpen: () -> Unit,
    onDeliveries: () -> Unit,
    onConfig: () -> Unit,
    onEdit: () -> Unit,
    onPing: () -> Unit,
    onTest: () -> Unit,
    onDelete: () -> Unit
) {
    val responseColor = webhookResponseColor(hook)
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AiModuleTheme.colors.surface)
            .clickable(enabled = !disabled, onClick = onOpen)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(
                if (hook.active) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                null,
                Modifier.size(18.dp),
                tint = if (hook.active) Color(0xFF34C759) else AiModuleTheme.colors.textMuted
            )
            Column(Modifier.weight(1f)) {
                Text(hook.url.ifBlank { "Webhook #${hook.id}" }, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AiModuleTheme.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${hook.contentType.ifBlank { "json" }} · ${if (hook.active) "Active" else "Inactive"}", fontSize = 11.sp, color = AiModuleTheme.colors.textMuted)
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            WebhookIconAction(Icons.Rounded.Send, AiModuleTheme.colors.accent, disabled, onPing)
            WebhookIconAction(Icons.Rounded.PlayArrow, Color(0xFFFF9500), disabled, onTest)
            WebhookIconAction(Icons.Rounded.History, AiModuleTheme.colors.accent, disabled, onDeliveries)
            WebhookIconAction(Icons.Rounded.Settings, AiModuleTheme.colors.textSecondary, disabled, onConfig)
            WebhookIconAction(Icons.Rounded.Edit, AiModuleTheme.colors.textSecondary, disabled, onEdit)
            WebhookIconAction(Icons.Rounded.Delete, Color(0xFFFF3B30), disabled, onDelete)
        }

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            hook.events.take(8).forEach { event -> WebhookPill(event, AiModuleTheme.colors.textSecondary) }
            if (hook.events.size > 8) WebhookPill("+${hook.events.size - 8}", AiModuleTheme.colors.textMuted)
            if (hook.insecureSsl == "1") WebhookPill("SSL off", Color(0xFFFF9500))
        }

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(RoundedCornerShape(8.dp)).background(responseColor))
            Text(webhookLastResponseLabel(hook), fontSize = 12.sp, color = responseColor, fontWeight = FontWeight.Medium)
            if (hook.updatedAt.isNotBlank()) Text("Updated ${hook.updatedAt.take(10)}", fontSize = 11.sp, color = AiModuleTheme.colors.textMuted)
        }
        if (hook.lastResponseMessage.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(hook.lastResponseMessage, fontSize = 11.sp, color = AiModuleTheme.colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun WebhookIconAction(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, disabled: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        enabled = !disabled,
        modifier = Modifier.size(36.dp)
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = tint)
    }
}

@Composable
private fun WebhookDetailDialog(
    hook: GHWebhook,
    onDismiss: () -> Unit,
    onDeliveries: () -> Unit,
    onConfig: () -> Unit
) {
    AiModuleAlertDialog(
        onDismissRequest = onDismiss,
        title = "webhook #${hook.id}",
        confirmButton = {
            AiModuleTextAction(label = "config", onClick = onConfig, tint = AiModuleTheme.colors.accent)
        },
        dismissButton = {
            Row {
                AiModuleTextAction(label = "deliveries", onClick = onDeliveries, tint = AiModuleTheme.colors.textSecondary)
                AiModuleTextAction(label = "close", onClick = onDismiss, tint = AiModuleTheme.colors.textSecondary)
            }
        },
    ) {
        Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                WebhookPill(if (hook.active) "Active" else "Inactive", if (hook.active) Color(0xFF34C759) else AiModuleTheme.colors.textMuted)
                WebhookPill(hook.contentType.ifBlank { "json" }, AiModuleTheme.colors.textSecondary)
                if (hook.insecureSsl == "1") WebhookPill("SSL off", Color(0xFFFF9500))
                WebhookPill(webhookLastResponseLabel(hook), webhookResponseColor(hook))
            }
            WebhookDetailRow("Payload URL", hook.url)
            WebhookDetailRow("Name", hook.name.ifBlank { "web" })
            WebhookDetailRow("Events", hook.events.joinToString(", ").ifBlank { "No events" })
            WebhookDetailRow("Created", hook.createdAt.ifBlank { "-" })
            WebhookDetailRow("Updated", hook.updatedAt.ifBlank { "-" })
            if (hook.lastResponseMessage.isNotBlank()) {
                DeliveryBlock("Last response", hook.lastResponseMessage)
            }
        }
    }
}

@Composable
private fun WebhookDetailRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, fontSize = 11.sp, color = AiModuleTheme.colors.textMuted, fontWeight = FontWeight.Medium)
        Text(value.ifBlank { "-" }, fontSize = 13.sp, color = AiModuleTheme.colors.textPrimary)
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

    AiModuleAlertDialog(
        onDismissRequest = onDismiss,
        title = if (webhook == null) "add webhook" else "edit webhook",
        confirmButton = {
            AiModuleTextAction(
                label = "save",
                enabled = canSave,
                onClick = {
                    if (!canSave) return@AiModuleTextAction
                    onSave(webhook, url.trim(), events.ifEmpty { listOf("push") }, secret, active, contentType, insecureSsl)
                },
                tint = AiModuleTheme.colors.accent,
            )
        },
        dismissButton = {
            AiModuleTextAction(label = "cancel", onClick = onDismiss, tint = AiModuleTheme.colors.textSecondary)
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AiModuleTextField(value = url, onValueChange = { url = it }, label = "Payload URL")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                listOf("json", "form").forEach { type ->
                    WebhookChoiceChip(type, selected = contentType == type) { contentType = type }
                }
            }
            AiModuleTextField(value = eventsRaw, onValueChange = { eventsRaw = it }, label = "Events")
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
            AiModuleTextField(value = secret, onValueChange = { secret = it }, label = if (webhook == null) "Secret (optional)" else "New secret (leave blank to keep)")
            Text("GitHub never returns existing webhook secrets.", fontSize = 11.sp, color = AiModuleTheme.colors.textMuted, fontFamily = JetBrainsMono)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Switch(checked = active, onCheckedChange = { active = it }, colors = SwitchDefaults.colors(checkedThumbColor = AiModuleTheme.colors.accent))
                Text("Active", fontSize = 13.sp, color = AiModuleTheme.colors.textPrimary, fontFamily = JetBrainsMono)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Checkbox(checked = insecureSsl, onCheckedChange = { insecureSsl = it })
                Text("Disable SSL verification", fontSize = 13.sp, color = AiModuleTheme.colors.textPrimary, fontFamily = JetBrainsMono)
            }
        }
    }
}

@Composable
private fun WebhookConfigDialog(
    repoOwner: String,
    repoName: String,
    webhook: GHWebhook,
    onDismiss: () -> Unit,
    onSaved: (List<GHWebhook>) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var loading by remember(webhook.id) { mutableStateOf(true) }
    var saving by remember(webhook.id) { mutableStateOf(false) }
    var config by remember(webhook.id) { mutableStateOf<GHWebhookConfig?>(null) }
    var url by remember(webhook.id) { mutableStateOf(webhook.url) }
    var contentType by remember(webhook.id) { mutableStateOf(webhook.contentType.ifBlank { "json" }) }
    var insecureSsl by remember(webhook.id) { mutableStateOf(webhook.insecureSsl == "1") }
    var secret by remember(webhook.id) { mutableStateOf("") }
    val canSave = !loading && !saving && (url.startsWith("http://") || url.startsWith("https://"))

    LaunchedEffect(webhook.id) {
        loading = true
        val loaded = GitHubManager.getWebhookConfig(context, repoOwner, repoName, webhook.id)
        config = loaded
        loaded?.let {
            url = it.url.ifBlank { webhook.url }
            contentType = it.contentType.ifBlank { webhook.contentType.ifBlank { "json" } }
            insecureSsl = it.insecureSsl == "1"
        }
        loading = false
    }

    AiModuleAlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = "webhook config",
        confirmButton = {
            AiModuleTextAction(
                label = if (saving) "saving" else "save config",
                enabled = canSave,
                onClick = {
                    if (!canSave) return@AiModuleTextAction
                    saving = true
                    scope.launch {
                        val payload = mutableMapOf(
                            "url" to url.trim(),
                            "content_type" to contentType,
                            "insecure_ssl" to if (insecureSsl) "1" else "0",
                        )
                        if (secret.isNotBlank()) payload["secret"] = secret
                        val ok = GitHubManager.updateWebhookConfig(context, repoOwner, repoName, webhook.id, payload)
                        Toast.makeText(context, if (ok) "Webhook config updated" else "Failed to update config", Toast.LENGTH_SHORT).show()
                        if (ok) onSaved(GitHubManager.getWebhooks(context, repoOwner, repoName))
                        saving = false
                    }
                },
                tint = AiModuleTheme.colors.accent,
            )
        },
        dismissButton = {
            AiModuleTextAction(label = "cancel", enabled = !saving, onClick = onDismiss, tint = AiModuleTheme.colors.textSecondary)
        },
    ) {
        Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (loading) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(color = AiModuleTheme.colors.accent, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text("Loading config", fontSize = 13.sp, color = AiModuleTheme.colors.textSecondary, fontFamily = JetBrainsMono)
                }
            }
            AiModuleTextField(value = url, onValueChange = { url = it }, label = "Payload URL")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                listOf("json", "form").forEach { type ->
                    WebhookChoiceChip(type, selected = contentType == type) { contentType = type }
                }
            }
            AiModuleTextField(value = secret, onValueChange = { secret = it }, label = "New secret (leave blank to keep)")
            val remoteSecret = config?.secret.orEmpty()
            Text(
                if (remoteSecret.isNotBlank()) "Existing secret is write-only and stays unchanged unless replaced." else "GitHub does not expose existing webhook secrets.",
                fontSize = 11.sp,
                color = AiModuleTheme.colors.textMuted,
                fontFamily = JetBrainsMono,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Checkbox(checked = insecureSsl, onCheckedChange = { insecureSsl = it })
                Text("Disable SSL verification", fontSize = 13.sp, color = AiModuleTheme.colors.textPrimary, fontFamily = JetBrainsMono)
            }
        }
    }
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

    Column(Modifier.fillMaxSize().background(AiModuleTheme.colors.background)) {
        AiModulePageBar(
            title = "> webhook deliveries",
            subtitle = hook.url.ifBlank { "$repoOwner/$repoName" },
            onBack = onBack,
            trailing = {
                IconButton(onClick = { loadDeliveries() }, enabled = !loading, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp), tint = AiModuleTheme.colors.accent)
                }
            },
        )

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AiModuleTheme.colors.accent, modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
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
                    AiModuleSearchField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = "search event, guid or status",
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
                            Text(if (deliveries.isEmpty()) "No deliveries found" else "No matching deliveries", fontSize = 14.sp, color = AiModuleTheme.colors.textMuted)
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
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(AiModuleTheme.colors.surface).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Rounded.History, null, Modifier.size(20.dp), tint = AiModuleTheme.colors.accent)
            Text("Recent deliveries", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = AiModuleTheme.colors.textPrimary, modifier = Modifier.weight(1f))
            Text("${deliveries.size}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AiModuleTheme.colors.textPrimary)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            WebhookPill("Success $success", Color(0xFF34C759))
            WebhookPill("Failed $failed", if (failed > 0) Color(0xFFFF3B30) else AiModuleTheme.colors.textMuted)
            WebhookPill("Redelivered $redeliveries", AiModuleTheme.colors.textSecondary)
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
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(AiModuleTheme.colors.surface).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(if (delivery.statusCode in 200..299) Icons.Rounded.CheckCircle else Icons.Rounded.Error, null, Modifier.size(18.dp), tint = color)
            Column(Modifier.weight(1f)) {
                Text(delivery.event.ifBlank { "Delivery ${delivery.id}" }, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AiModuleTheme.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(delivery.guid.ifBlank { delivery.deliveredAt.take(19).replace('T', ' ') }, fontSize = 11.sp, color = AiModuleTheme.colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onOpen, enabled = !disabled) {
                Icon(Icons.Rounded.Article, null, Modifier.size(18.dp), tint = AiModuleTheme.colors.accent)
            }
            IconButton(onClick = onRedeliver, enabled = !disabled) {
                Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp), tint = Color(0xFFFF9500))
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            WebhookPill(deliveryStatusLabel(delivery), color)
            if (delivery.action.isNotBlank()) WebhookPill(delivery.action, AiModuleTheme.colors.textSecondary)
            if (delivery.redelivery) WebhookPill("redelivery", Color(0xFFFF9500))
            if (delivery.duration > 0.0) WebhookPill("${"%.2f".format(delivery.duration)}s", AiModuleTheme.colors.textMuted)
            if (delivery.deliveredAt.isNotBlank()) WebhookPill(delivery.deliveredAt.take(10), AiModuleTheme.colors.textMuted)
        }
    }
}

@Composable
private fun DeliveryDetailDialog(
    delivery: GHWebhookDelivery,
    onDismiss: () -> Unit,
    onRedeliver: () -> Unit
) {
    AiModuleAlertDialog(
        onDismissRequest = onDismiss,
        title = "delivery ${delivery.id}",
        confirmButton = {
            AiModuleTextAction(label = "redeliver", onClick = onRedeliver, tint = AiModuleTheme.colors.warning)
        },
        dismissButton = {
            AiModuleTextAction(label = "close", onClick = onDismiss, tint = AiModuleTheme.colors.textSecondary)
        },
    ) {
        Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                WebhookPill(delivery.event.ifBlank { "event" }, AiModuleTheme.colors.textSecondary)
                WebhookPill(deliveryStatusLabel(delivery), deliveryStatusColor(delivery))
                if (delivery.redelivery) WebhookPill("redelivery", Color(0xFFFF9500))
            }
            DeliveryBlock("Request headers", formatHeaders(delivery.requestHeaders))
            DeliveryBlock("Request payload", prettyPayload(delivery.requestPayload))
            DeliveryBlock("Response headers", formatHeaders(delivery.responseHeaders))
            DeliveryBlock("Response payload", prettyPayload(delivery.responsePayload))
        }
    }
}

@Composable
private fun DeliveryBlock(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AiModuleTheme.colors.textPrimary)
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(AiModuleTheme.colors.background).padding(8.dp)) {
            Text(body.ifBlank { "No data" }, fontSize = 10.sp, color = AiModuleTheme.colors.textSecondary, lineHeight = 14.sp)
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
            .background(if (selected) AiModuleTheme.colors.accent.copy(alpha = 0.14f) else AiModuleTheme.colors.background)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(label, fontSize = 12.sp, color = if (selected) AiModuleTheme.colors.accent else AiModuleTheme.colors.textSecondary, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

private fun webhookLastResponseLabel(hook: GHWebhook): String {
    val status = hook.lastResponseStatus.takeIf { it.isNotBlank() && it != "null" }
    val code = hook.lastResponseCode.takeIf { it > 0 }?.toString()
    return listOfNotNull(status, code).joinToString(" · ").ifBlank { "No delivery response yet" }
}

@Composable
private fun webhookResponseColor(hook: GHWebhook): Color = when {
    hook.lastResponseCode in 200..299 || hook.lastResponseStatus.equals("ok", ignoreCase = true) -> Color(0xFF34C759)
    hook.lastResponseCode >= 400 || hook.lastResponseStatus.equals("failed", ignoreCase = true) -> Color(0xFFFF3B30)
    else -> AiModuleTheme.colors.textMuted
}

private fun deliveryStatusLabel(delivery: GHWebhookDelivery): String {
    val status = delivery.status.takeIf { it.isNotBlank() && it != "null" }
    val code = delivery.statusCode.takeIf { it > 0 }?.toString()
    return listOfNotNull(status, code).joinToString(" · ").ifBlank { "No response" }
}

@Composable
private fun deliveryStatusColor(delivery: GHWebhookDelivery): Color = when {
    delivery.statusCode in 200..299 -> Color(0xFF34C759)
    delivery.statusCode >= 300 || delivery.status.equals("failed", ignoreCase = true) -> Color(0xFFFF3B30)
    else -> AiModuleTheme.colors.textMuted
}

private fun formatHeaders(headers: List<Pair<String, String>>): String =
    headers.joinToString("\n") { (key, value) -> "$key: $value" }

private fun prettyPayload(payload: String): String {
    if (payload.isBlank() || payload == "null") return ""
    return payload.take(12000)
}
