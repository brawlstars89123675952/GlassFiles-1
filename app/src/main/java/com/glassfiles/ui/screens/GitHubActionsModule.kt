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

@Composable
private fun ActionsTab(runs: List<GHWorkflowRun>, repo: GHRepo, onRunClick: (GHWorkflowRun) -> Unit) {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    if (runs.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(Strings.ghNoWorkflows, fontSize = 14.sp, color = TextTertiary) }
    } else {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(runs) { run ->
                Row(Modifier.fillMaxWidth().clickable { onRunClick(run) }.padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                    // Status icon
                    val (statusIcon, statusColor) = when {
                        run.status == "in_progress" -> Icons.Rounded.Refresh to Color(0xFFFF9500)
                        run.conclusion == "success" -> Icons.Rounded.CheckCircle to Color(0xFF34C759)
                        run.conclusion == "failure" -> Icons.Rounded.Cancel to Color(0xFFFF3B30)
                        run.conclusion == "cancelled" -> Icons.Rounded.RemoveCircle to Color(0xFF8E8E93)
                        run.status == "queued" -> Icons.Rounded.Schedule to Color(0xFFFF9500)
                        else -> Icons.Rounded.Circle to TextTertiary
                    }
                    Icon(statusIcon, null, Modifier.size(20.dp), tint = statusColor)
                    Column(Modifier.weight(1f)) {
                        Text(run.name, fontSize = 14.sp, color = TextPrimary, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("#${run.runNumber}", fontSize = 11.sp, color = TextTertiary)
                            Text(run.branch, fontSize = 11.sp, color = Blue)
                            Text(run.event, fontSize = 11.sp, color = TextTertiary)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 2.dp)) {
                            Text(run.actor, fontSize = 10.sp, color = TextSecondary)
                            Text(run.createdAt.take(10), fontSize = 10.sp, color = TextTertiary)
                        }
                        // Rerun / Cancel for in_progress
                        if (run.status == "in_progress" || run.conclusion == "failure") {
                            Spacer(Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Chip(Icons.Rounded.Refresh, Strings.ghRerun) {
                                    scope.launch {
                                        val ok = GitHubManager.rerunWorkflow(context, repo.owner, repo.name, run.id)
                                        Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                                    }
                                }
                                if (run.status == "in_progress") {
                                    Chip(Icons.Rounded.Cancel, Strings.cancel, Color(0xFFFF3B30)) {
                                        scope.launch {
                                            val ok = GitHubManager.cancelWorkflowRun(context, repo.owner, repo.name, run.id)
                                            Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Icon(Icons.Rounded.ChevronRight, null, Modifier.size(16.dp), tint = TextTertiary)
                }
                Box(Modifier.fillMaxWidth().padding(start = 46.dp).height(0.5.dp).background(SeparatorColor))
            }
        }
    }
}

// ═══════════════════════════════════
// Workflow Run Detail — jobs + steps
// ═══════════════════════════════════

@Composable
private fun WorkflowRunDetailScreen(repo: GHRepo, runId: Long, onBack: () -> Unit) {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    var jobs by remember { mutableStateOf<List<GHJob>>(emptyList()) }
    var artifacts by remember { mutableStateOf<List<GHArtifact>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var downloading by remember { mutableStateOf<Long?>(null) }
    // Logs: jobId -> log text
    var jobLogs by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }
    var loadingJobId by remember { mutableStateOf<Long?>(null) }
    var expandedJobId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(runId) {
        jobs = GitHubManager.getWorkflowRunJobs(context, repo.owner, repo.name, runId)
        artifacts = GitHubManager.getRunArtifacts(context, repo.owner, repo.name, runId)
        loading = false
    }

    Column(Modifier.fillMaxSize().background(SurfaceLight)) {
        GHTopBar("Run #$runId", onBack = onBack) {
            IconButton(onClick = {
                scope.launch {
                    val ok = GitHubManager.rerunWorkflow(context, repo.owner, repo.name, runId)
                    Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                }
            }) { Icon(Icons.Rounded.Refresh, null, Modifier.size(20.dp), tint = Blue) }
        }

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Blue, modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp) }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp)) {
                items(jobs) { job ->
                    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(12.dp)).background(SurfaceWhite).padding(12.dp)) {
                        // Job header
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val jColor = when (job.conclusion) { "success" -> Color(0xFF34C759); "failure" -> Color(0xFFFF3B30); else -> Color(0xFFFF9500) }
                            Icon(when (job.conclusion) { "success" -> Icons.Rounded.CheckCircle; "failure" -> Icons.Rounded.Cancel; else -> Icons.Rounded.Refresh }, null, Modifier.size(18.dp), tint = jColor)
                            Text(job.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, modifier = Modifier.weight(1f))
                            // Duration
                            val dur = calcDuration(job.startedAt, job.completedAt)
                            if (dur.isNotEmpty()) Text(dur, fontSize = 10.sp, color = TextTertiary)
                        }

                        // Steps with expand
                        Spacer(Modifier.height(6.dp))
                        job.steps.forEach { step ->
                            val sColor = when (step.conclusion) { "success" -> Color(0xFF34C759); "failure" -> Color(0xFFFF3B30); "skipped" -> Color(0xFF8E8E93); else -> Color(0xFFFF9500) }
                            val sIcon = when (step.conclusion) { "success" -> Icons.Rounded.Check; "failure" -> Icons.Rounded.Close; "skipped" -> Icons.Rounded.Remove; else -> Icons.Rounded.Refresh }
                            Row(Modifier.fillMaxWidth().padding(start = 10.dp, top = 3.dp, bottom = 3.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(sIcon, null, Modifier.size(14.dp), tint = sColor)
                                Text(step.name, fontSize = 12.sp, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                if (step.conclusion == "failure") Icon(Icons.Rounded.Warning, null, Modifier.size(12.dp), tint = Color(0xFFFF3B30))
                            }
                        }

                        // Log button
                        Spacer(Modifier.height(8.dp))
                        Box(Modifier.fillMaxWidth().height(0.5.dp).background(SeparatorColor))
                        Spacer(Modifier.height(6.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // View logs button
                            Box(Modifier.clip(RoundedCornerShape(8.dp)).background(Blue.copy(0.08f))
                                .clickable {
                                    if (expandedJobId == job.id) { expandedJobId = null; return@clickable }
                                    expandedJobId = job.id
                                    if (jobLogs[job.id] == null) {
                                        loadingJobId = job.id
                                        scope.launch {
                                            val log = GitHubManager.getJobLogs(context, repo.owner, repo.name, job.id)
                                            jobLogs = jobLogs + (job.id to log)
                                            loadingJobId = null
                                        }
                                    }
                                }.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (loadingJobId == job.id) CircularProgressIndicator(Modifier.size(12.dp), color = Blue, strokeWidth = 1.5.dp)
                                    else Icon(if (expandedJobId == job.id) Icons.Rounded.ExpandLess else Icons.Rounded.Article, null, Modifier.size(14.dp), tint = Blue)
                                    Text(if (expandedJobId == job.id) "Hide logs" else "View logs", fontSize = 11.sp, color = Blue)
                                }
                            }

                            // Timestamps
                            if (job.startedAt.isNotBlank()) {
                                Text(job.startedAt.replace("T", " ").take(19), fontSize = 9.sp, color = TextTertiary, modifier = Modifier.align(Alignment.CenterVertically))
                            }
                        }

                        // Expanded logs
                        if (expandedJobId == job.id && jobLogs[job.id] != null) {
                            Spacer(Modifier.height(8.dp))
                            androidx.compose.foundation.text.selection.SelectionContainer {
                                Box(Modifier.fillMaxWidth().heightIn(max = 400.dp).clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF0D1117)).verticalScroll(rememberScrollState())
                                    .horizontalScroll(rememberScrollState()).padding(10.dp)) {
                                    Text(
                                        jobLogs[job.id]!!,
                                        fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                                        color = Color(0xFFC9D1D9), lineHeight = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // ─── Artifacts section ───
                if (artifacts.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(8.dp))
                        Text(Strings.ghArtifacts, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Spacer(Modifier.height(8.dp))
                    }
                    items(artifacts) { artifact ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(SurfaceWhite)
                                .clickable(enabled = !artifact.expired && downloading != artifact.id) {
                                    downloading = artifact.id
                                    scope.launch {
                                        val dest = File(
                                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                                            "GlassFiles_Git/${artifact.name}.zip"
                                        )
                                        val ok = GitHubManager.downloadArtifact(context, repo.owner, repo.name, artifact.id, dest)
                                        Toast.makeText(context, if (ok) "${Strings.done}: ${dest.name}" else Strings.error, Toast.LENGTH_SHORT).show()
                                        downloading = null
                                    }
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Rounded.Archive, null, Modifier.size(24.dp), tint = if (artifact.expired) TextTertiary else Blue)
                            Column(Modifier.weight(1f)) {
                                Text(artifact.name, fontSize = 14.sp, color = if (artifact.expired) TextTertiary else TextPrimary,
                                    fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(fmtSize(artifact.sizeInBytes), fontSize = 11.sp, color = TextSecondary)
                                    if (artifact.expired) Text(Strings.ghExpired, fontSize = 11.sp, color = Color(0xFFFF3B30))
                                    else Text(artifact.createdAt.take(10), fontSize = 11.sp, color = TextTertiary)
                                }
                            }
                            if (downloading == artifact.id) CircularProgressIndicator(Modifier.size(18.dp), color = Blue, strokeWidth = 2.dp)
                            else if (!artifact.expired) Icon(Icons.Rounded.Download, null, Modifier.size(18.dp), tint = Blue)
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
        }
    }
}

private fun calcDuration(start: String, end: String): String {
    if (start.isBlank() || end.isBlank()) return ""
    return try {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
        fmt.timeZone = java.util.TimeZone.getTimeZone("UTC")
        val s = fmt.parse(start)!!; val e = fmt.parse(end)!!
        val diff = (e.time - s.time) / 1000
        when { diff < 60 -> "${diff}s"; diff < 3600 -> "${diff / 60}m ${diff % 60}s"; else -> "${diff / 3600}h ${(diff % 3600) / 60}m" }
    } catch (_: Exception) { "" }
}

// ═══════════════════════════════════
// Notifications Screen
// ═══════════════════════════════════

@Composable
private fun NotificationsScreen(onBack: () -> Unit) {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    var notifications by remember { mutableStateOf<List<GHNotification>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var showAll by remember { mutableStateOf(false) }

    LaunchedEffect(showAll) {
        loading = true
        notifications = GitHubManager.getNotifications(context, all = showAll)
        loading = false
    }

    Column(Modifier.fillMaxSize().background(SurfaceLight)) {
        GHTopBar(Strings.ghNotifications, onBack = onBack) {
            // Toggle all/unread
            Box(Modifier.clip(RoundedCornerShape(6.dp)).background(if (showAll) Blue.copy(0.15f) else SurfaceLight)
                .clickable { showAll = !showAll }.padding(horizontal = 8.dp, vertical = 4.dp)) {
                Text(if (showAll) "All" else Strings.ghUnread, fontSize = 11.sp, color = if (showAll) Blue else TextSecondary)
            }
            // Mark all read
            IconButton(onClick = {
                scope.launch {
                    GitHubManager.markAllNotificationsRead(context)
                    notifications = GitHubManager.getNotifications(context, all = showAll)
                }
            }) { Icon(Icons.Rounded.DoneAll, null, Modifier.size(20.dp), tint = Blue) }
        }

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Blue, modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp) }
        } else if (notifications.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.NotificationsNone, null, Modifier.size(48.dp), tint = TextTertiary)
                    Spacer(Modifier.height(8.dp))
                    Text(Strings.ghNoNotifications, fontSize = 14.sp, color = TextTertiary)
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                items(notifications) { n ->
                    Row(Modifier.fillMaxWidth()
                        .background(if (n.unread) Blue.copy(0.04f) else Color.Transparent)
                        .clickable {
                            if (n.unread) scope.launch {
                                GitHubManager.markNotificationRead(context, n.id)
                                notifications = GitHubManager.getNotifications(context, all = showAll)
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                        // Type icon
                        val (icon, iconColor) = when (n.type) {
                            "Issue" -> Icons.Rounded.RadioButtonUnchecked to Color(0xFF34C759)
                            "PullRequest" -> Icons.Rounded.CallMerge to Color(0xFF8957E5)
                            "Release" -> Icons.Rounded.NewReleases to Blue
                            else -> Icons.Rounded.Notifications to TextSecondary
                        }
                        Icon(icon, null, Modifier.size(20.dp), tint = iconColor)
                        Column(Modifier.weight(1f)) {
                            Text(n.title, fontSize = 14.sp, color = TextPrimary, maxLines = 2,
                                fontWeight = if (n.unread) FontWeight.SemiBold else FontWeight.Normal)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(n.repoName.substringAfter("/"), fontSize = 11.sp, color = Blue, maxLines = 1)
                                Text(n.reason, fontSize = 10.sp, color = TextTertiary)
                                Text(n.updatedAt.take(10), fontSize = 10.sp, color = TextTertiary)
                            }
                        }
                        if (n.unread) Box(Modifier.size(8.dp).clip(CircleShape).background(Blue))
                    }
                    Box(Modifier.fillMaxWidth().padding(start = 46.dp).height(0.5.dp).background(SeparatorColor))
                }
            }
        }
    }
}

// ═══════════════════════════════════
// Markdown Basic Renderer
// ═══════════════════════════════════
