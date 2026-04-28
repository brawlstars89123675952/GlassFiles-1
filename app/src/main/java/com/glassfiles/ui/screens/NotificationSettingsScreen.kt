package com.glassfiles.ui.screens

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.glassfiles.notifications.NotificationsPreferences
import com.glassfiles.notifications.NotificationsWorker
import com.glassfiles.ui.theme.*

@Composable
fun NotificationSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(NotificationsPreferences.isEnabled(context)) }
    var interval by remember { mutableIntStateOf(NotificationsPreferences.getPollIntervalMinutes(context)) }
    var quietOn by remember { mutableStateOf(NotificationsPreferences.isQuietHoursEnabled(context)) }
    var quietStart by remember { mutableIntStateOf(NotificationsPreferences.getQuietHoursStart(context)) }
    var quietEnd by remember { mutableIntStateOf(NotificationsPreferences.getQuietHoursEnd(context)) }
    var repoMode by remember { mutableStateOf(NotificationsPreferences.getRepoFilterMode(context)) }
    var customRepos by remember { mutableStateOf(NotificationsPreferences.getCustomRepos(context)) }
    var showCustomDialog by remember { mutableStateOf(false) }
    var oemDismissed by remember { mutableStateOf(NotificationsPreferences.isOemWarningDismissed(context)) }
    var showOemHelp by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()
    val postPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(enabled) {
        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = android.Manifest.permission.POST_NOTIFICATIONS
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                postPermissionLauncher.launch(permission)
            }
        }
    }

    Column(Modifier.fillMaxSize().background(SurfaceLight)) {
        // Top bar
        Row(
            Modifier.fillMaxWidth().background(SurfaceWhite)
                .padding(top = 44.dp, start = 4.dp, end = 16.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, Modifier.size(20.dp), tint = Blue)
            }
            Text("Notifications", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 20.sp)
        }

        Column(
            Modifier.fillMaxSize().verticalScroll(scrollState).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── OEM warning ──
            if (!oemDismissed && isAggressiveOem()) {
                OemWarningCard(
                    onShowHow = { showOemHelp = true },
                    onDismiss = {
                        NotificationsPreferences.setOemWarningDismissed(context, true)
                        oemDismissed = true
                    }
                )
            }

            // ── Master toggle ──
            NSection("General", Icons.Rounded.NotificationsActive) {
                NToggle(
                    title = "Enable notifications",
                    subtitle = "Receive Android notifications for GitHub activity",
                    checked = enabled,
                    onChange = {
                        enabled = it
                        NotificationsPreferences.setEnabled(context, it)
                        if (it) NotificationsWorker.enqueue(context)
                        else NotificationsWorker.cancel(context)
                    }
                )
            }

            // The rest is dimmed when master is off.
            val dimmedAlpha = if (enabled) 1f else 0.5f
            Column(
                Modifier.fillMaxWidth().graphicsLayerOrAlpha(dimmedAlpha),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                // ── Polling frequency ──
                NSection("Polling frequency", Icons.Rounded.Schedule) {
                    NLabel("How often to check GitHub for new notifications")
                    NChips(
                        labels = listOf("Every 15 min", "Every 30 min", "Every 60 min"),
                        selected = NotificationsPreferences.ALLOWED_INTERVALS.indexOf(interval).coerceAtLeast(0),
                        enabled = enabled
                    ) { i ->
                        val v = NotificationsPreferences.ALLOWED_INTERVALS[i]
                        interval = v
                        NotificationsPreferences.setPollIntervalMinutes(context, v)
                        NotificationsWorker.enqueue(context)
                    }
                    NCaption("Android may delay polls to save battery — actual interval can be longer than configured.")
                }

                // ── Quiet hours ──
                NSection("Quiet hours", Icons.Rounded.Bedtime) {
                    NToggle(
                        title = "Mute during quiet hours",
                        subtitle = "Notifications are skipped during this time window",
                        checked = quietOn,
                    onChange = {
                        quietOn = it
                        NotificationsPreferences.setQuietHoursEnabled(context, it)
                    },
                    enabled = enabled
                )
                    if (quietOn) {
                        Spacer(Modifier.height(4.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            HourPicker(
                                modifier = Modifier.weight(1f),
                                label = "From",
                                hour = quietStart,
                                enabled = enabled,
                                onChange = {
                                    quietStart = it
                                    NotificationsPreferences.setQuietHours(context, it, quietEnd)
                                }
                            )
                            HourPicker(
                                modifier = Modifier.weight(1f),
                                label = "Until",
                                hour = quietEnd,
                                enabled = enabled,
                                onChange = {
                                    quietEnd = it
                                    NotificationsPreferences.setQuietHours(context, quietStart, it)
                                }
                            )
                        }
                    }
                }

                // ── Per-reason toggles ──
                NSection("What to notify about", Icons.Rounded.FilterList) {
                    REASON_CATEGORIES.forEach { cat ->
                        ReasonRow(context, cat, enabled = enabled)
                        if (cat != REASON_CATEGORIES.last()) {
                            HorizontalDivider(color = SeparatorColor)
                        }
                    }
                }

                // ── Repo filter ──
                NSection("Repositories", Icons.Rounded.FolderOpen) {
                    NLabel("Which repositories should send notifications")
                    NChips(
                        labels = listOf("All", "My repos", "Custom"),
                        selected = when (repoMode) {
                            NotificationsPreferences.REPO_MODE_OWNED -> 1
                            NotificationsPreferences.REPO_MODE_CUSTOM -> 2
                            else -> 0
                        },
                        enabled = enabled
                    ) { i ->
                        val mode = when (i) {
                            1 -> NotificationsPreferences.REPO_MODE_OWNED
                            2 -> NotificationsPreferences.REPO_MODE_CUSTOM
                            else -> NotificationsPreferences.REPO_MODE_ALL
                        }
                        repoMode = mode
                        NotificationsPreferences.setRepoFilterMode(context, mode)
                    }
                    if (repoMode == NotificationsPreferences.REPO_MODE_CUSTOM) {
                        Spacer(Modifier.height(4.dp))
                        NCaption(
                            if (customRepos.isEmpty()) "No repositories selected"
                            else "${customRepos.size} repositories"
                        )
                        Box(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                .background(Blue.copy(alpha = 0.10f))
                                .clickable(enabled = enabled) { showCustomDialog = true }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Manage list", color = Blue, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        }
                    }
                }

                // ── System link ──
                NSection("System settings", Icons.Rounded.Settings) {
                    NCaption("Per-channel toggles, sound, vibration and Do Not Disturb are managed by Android.")
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(Blue.copy(alpha = 0.10f))
                            .clickable(enabled = enabled) { openAppNotificationSettings(context) }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Open Android notification settings",
                            color = Blue, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    }
                }

                // ── Battery optimization ──
                BatteryOptimizationCard(context, enabled = enabled)
            }

            Spacer(Modifier.height(40.dp))
        }
    }

    if (showCustomDialog) {
        CustomReposDialog(
            initial = customRepos,
            onSave = {
                customRepos = it
                NotificationsPreferences.setCustomRepos(context, it)
                showCustomDialog = false
            },
            onDismiss = { showCustomDialog = false }
        )
    }
    if (showOemHelp) {
        OemHelpDialog(onDismiss = { showOemHelp = false })
    }
}

// ─────────────────────────────────────────────────────────────────────
// Reasons
// ─────────────────────────────────────────────────────────────────────

private data class ReasonCategory(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val reasons: List<String>
)

private val REASON_CATEGORIES = listOf(
    ReasonCategory("CI builds", "Workflow runs (success / failure)",
        Icons.Rounded.PlayCircle, listOf("ci_activity")),
    ReasonCategory("Mentions", "@mentions and team mentions",
        Icons.Rounded.AlternateEmail, listOf("mention", "team_mention")),
    ReasonCategory("Review requests", "Pull request reviews assigned to you",
        Icons.Rounded.RateReview, listOf("review_requested")),
    ReasonCategory("Issues & PRs", "Comments, state changes, assignments",
        Icons.Rounded.BugReport, listOf("comment", "state_change", "assign", "author")),
    ReasonCategory("Security alerts", "Vulnerability advisories",
        Icons.Rounded.Security, listOf("security_alert")),
    ReasonCategory("Subscribed repos", "Activity from repos you follow",
        Icons.Rounded.Visibility, listOf("subscribed", "manual")),
    ReasonCategory("Releases", "New releases in watched repositories",
        Icons.Rounded.NewReleases, listOf("release")),
    ReasonCategory("Invitations", "Repository and team invitations",
        Icons.Rounded.MailOutline, listOf("invitation"))
)

@Composable
private fun ReasonRow(context: Context, cat: ReasonCategory, enabled: Boolean) {
    // The whole category is on if any of the reasons inside it is on.
    var checked by remember(cat.reasons.joinToString(",")) {
        mutableStateOf(cat.reasons.any { NotificationsPreferences.isTypeEnabled(context, it) })
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(cat.icon, null, Modifier.size(20.dp), tint = TextSecondary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(cat.title, fontSize = 15.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
            Text(cat.subtitle, fontSize = 12.sp, color = TextSecondary)
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = {
                checked = it
                cat.reasons.forEach { r -> NotificationsPreferences.setTypeEnabled(context, r, it) }
            },
            colors = SwitchDefaults.colors(checkedTrackColor = Blue, checkedThumbColor = Color.White)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────
// OEM warning + help
// ─────────────────────────────────────────────────────────────────────

private fun isAggressiveOem(): Boolean {
    val mfg = (Build.MANUFACTURER ?: "").lowercase()
    return listOf("xiaomi", "redmi", "poco", "huawei", "honor", "oppo", "vivo", "realme", "iqoo")
        .any { it in mfg }
}

@Composable
private fun OemWarningCard(onShowHow: () -> Unit, onDismiss: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(Orange.copy(alpha = 0.10f))
            .border(1.dp, Orange.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.Warning, null, Modifier.size(20.dp), tint = Orange)
            Text("Background restrictions detected",
                fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = TextPrimary)
        }
        Text(
            "Some manufacturers (Xiaomi, Huawei, Honor, Oppo, Vivo) aggressively kill " +
            "background apps. To get reliable notifications:\n" +
            " • Allow autostart for GlassFiles in system settings\n" +
            " • Disable battery optimization\n" +
            " • Lock the app in recents (long-press the app card)",
            fontSize = 13.sp, color = TextSecondary
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                    .background(Orange.copy(alpha = 0.20f))
                    .clickable(onClick = onShowHow).padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) { Text("Show me how", color = Orange, fontWeight = FontWeight.Medium, fontSize = 13.sp) }
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                    .background(SurfaceWhite)
                    .clickable(onClick = onDismiss).padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) { Text("Dismiss", color = TextSecondary, fontWeight = FontWeight.Medium, fontSize = 13.sp) }
        }
    }
}

@Composable
private fun OemHelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceWhite,
        title = { Text("Background activity setup", fontWeight = FontWeight.Bold, color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OemHelpItem("Xiaomi / Redmi / POCO",
                    "Settings → Apps → Manage apps → GlassFiles → Battery saver → No restrictions. " +
                    "Also: Autostart → enable.")
                OemHelpItem("Honor / Huawei",
                    "Settings → Apps → GlassFiles → App launch → enable Manual launch → " +
                    "allow Auto-launch, Secondary launch, Run in background.")
                OemHelpItem("Oppo / Realme",
                    "Settings → Battery → App battery management → GlassFiles → Allow background activity.")
                OemHelpItem("Vivo / iQOO",
                    "Settings → Battery → Background power consumption manager → allow GlassFiles.")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Got it", color = Blue) }
        }
    )
}

@Composable
private fun OemHelpItem(title: String, body: String) {
    Column {
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextPrimary)
        Text(body, fontSize = 12.sp, color = TextSecondary)
    }
}

// ─────────────────────────────────────────────────────────────────────
// Battery optimization
// ─────────────────────────────────────────────────────────────────────

@Composable
private fun BatteryOptimizationCard(context: Context, enabled: Boolean) {
    var ignored by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }
    NSection("Battery optimization",
        if (ignored) Icons.Rounded.BatteryFull else Icons.Rounded.BatteryAlert) {
        if (ignored) {
            NCaption("GlassFiles is exempt from battery optimization. Polls run on schedule.")
        } else {
            NCaption("Android may delay or skip background polls when battery optimization is on.")
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(Orange.copy(alpha = 0.12f))
                    .clickable(enabled = enabled) {
                        requestIgnoreBatteryOptimizations(context)
                        // Re-check on next composition.
                        ignored = isIgnoringBatteryOptimizations(context)
                    }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Disable battery optimization",
                    color = Orange, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            }
        }
    }
}

private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    return try {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        pm.isIgnoringBatteryOptimizations(context.packageName)
    } catch (_: Throwable) { false }
}

private fun requestIgnoreBatteryOptimizations(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (_: Throwable) {
        try {
            val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(fallback)
        } catch (_: Throwable) {}
    }
}

private fun openAppNotificationSettings(context: Context) {
    try {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        }
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    } catch (_: Throwable) {}
}

// ─────────────────────────────────────────────────────────────────────
// Custom repos dialog
// ─────────────────────────────────────────────────────────────────────

@Composable
private fun CustomReposDialog(
    initial: Set<String>,
    onSave: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var repos by remember { mutableStateOf(initial.toList().sorted()) }
    var input by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceWhite,
        title = { Text("Custom repositories", fontWeight = FontWeight.Bold, color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Add repositories in owner/name format. Notifications will only be shown for these.",
                    fontSize = 12.sp, color = TextSecondary)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = { Text("owner/repo", color = TextTertiary, fontSize = 13.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                            focusedBorderColor = Blue, unfocusedBorderColor = SeparatorColor,
                            cursorColor = Blue
                        )
                    )
                    Box(
                        Modifier.clip(RoundedCornerShape(10.dp))
                            .background(Blue.copy(alpha = if (input.contains('/')) 0.15f else 0.05f))
                            .clickable(enabled = input.contains('/')) {
                                val name = input.trim()
                                if (name.isNotBlank() && name !in repos) {
                                    repos = (repos + name).sorted()
                                }
                                input = ""
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Icon(Icons.Rounded.Add, null, tint = Blue, modifier = Modifier.size(20.dp))
                    }
                }
                if (repos.isEmpty()) {
                    Text("No repositories yet.", fontSize = 12.sp, color = TextTertiary)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        repos.forEach { r ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                    .background(SurfaceLight).padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(r, fontSize = 13.sp, color = TextPrimary, modifier = Modifier.weight(1f))
                                IconButton(onClick = { repos = repos - r }) {
                                    Icon(Icons.Rounded.Close, null, Modifier.size(16.dp), tint = TextSecondary)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(repos.toSet()) }) {
                Text("Save", color = Blue)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
        }
    )
}

// ─────────────────────────────────────────────────────────────────────
// Hour picker (very simple — number stepper, no platform TimePicker)
// ─────────────────────────────────────────────────────────────────────

@Composable
private fun HourPicker(
    modifier: Modifier = Modifier,
    label: String,
    hour: Int,
    enabled: Boolean,
    onChange: (Int) -> Unit
) {
    Column(modifier) {
        Text(label, fontSize = 12.sp, color = TextSecondary)
        Spacer(Modifier.height(4.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                .background(SurfaceLight).padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                enabled = enabled,
                onClick = { onChange(((hour - 1) + 24) % 24) }
            ) {
                Icon(Icons.Rounded.Remove, null, Modifier.size(16.dp), tint = Blue)
            }
            Text(
                String.format("%02d:00", hour),
                modifier = Modifier.weight(1f),
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                color = TextPrimary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            IconButton(
                enabled = enabled,
                onClick = { onChange((hour + 1) % 24) }
            ) {
                Icon(Icons.Rounded.Add, null, Modifier.size(16.dp), tint = Blue)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
// Building blocks (mirror SettingsScreen styling)
// ─────────────────────────────────────────────────────────────────────

@Composable
private fun NSection(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(SurfaceWhite).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, Modifier.size(20.dp), tint = Blue)
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = TextPrimary)
        }
        content()
    }
}

@Composable
private fun NLabel(text: String) {
    Text(text, fontSize = 13.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
}

@Composable
private fun NCaption(text: String) {
    Text(text, fontSize = 12.sp, color = TextTertiary)
}

@Composable
private fun NToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.5f), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, color = TextPrimary)
            Text(subtitle, fontSize = 12.sp, color = TextSecondary)
        }
        Switch(checked, onChange, enabled = enabled,
            colors = SwitchDefaults.colors(checkedTrackColor = Blue, checkedThumbColor = Color.White))
    }
}

@Composable
private fun NChips(labels: List<String>, selected: Int, enabled: Boolean, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { i, label ->
            val sel = i == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                    .background(if (sel) Blue.copy(alpha = 0.12f) else SurfaceLight)
                    .border(1.dp, if (sel) Blue.copy(alpha = 0.3f) else Color.Transparent, RoundedCornerShape(8.dp))
                    .clickable(enabled = enabled) { onSelect(i) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label, fontSize = 12.sp,
                    color = if (sel) Blue else TextSecondary,
                    fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}

private fun Modifier.graphicsLayerOrAlpha(a: Float): Modifier = this.alpha(a)
