package com.glassfiles.ui.screens

import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Article
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glassfiles.data.Strings
import com.glassfiles.data.github.GHArtifact
import com.glassfiles.data.github.GHJob
import com.glassfiles.data.github.GHRepo
import com.glassfiles.data.github.GHStep
import com.glassfiles.data.github.GHWorkflowRun
import com.glassfiles.data.github.GitHubManager
import com.glassfiles.ui.theme.Blue
import com.glassfiles.ui.theme.SeparatorColor
import com.glassfiles.ui.theme.SurfaceLight
import com.glassfiles.ui.theme.SurfaceWhite
import com.glassfiles.ui.theme.TextPrimary
import com.glassfiles.ui.theme.TextSecondary
import com.glassfiles.ui.theme.TextTertiary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private enum class ActionsRunFilter { ALL, ACTIVE, FAILED, SUCCESS, CANCELLED }

@Composable
internal fun ActionsTab(
    runs: List<GHWorkflowRun>,
    repo: GHRepo,
    onRunClick: (GHWorkflowRun) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var liveRuns by remember(runs) { mutableStateOf(runs) }
    var refreshing by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf(TextFieldValue("")) }
    var onlyMine by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf(ActionsRunFilter.ALL) }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    suspend fun refreshRuns() {
        refreshing = true
        liveRuns = GitHubManager.getWorkflowRuns(context, repo.owner, repo.name)
        refreshing = false
    }

    LaunchedEffect(runs) { liveRuns = runs }

    LaunchedEffect(liveRuns) {
        while (true) {
            val hasLive = liveRuns.any { it.status == "queued" || it.status == "in_progress" }
            nowMs = System.currentTimeMillis()
            if (hasLive) {
                delay(1000)
                if (nowMs % 5000L < 1200L) {
                    liveRuns = GitHubManager.getWorkflowRuns(context, repo.owner, repo.name)
                }
            } else {
                delay(1500)
            }
        }
    }

    val currentLogin = remember { GitHubManager.getCachedUser(context)?.login.orEmpty() }

    val filteredRuns = remember(liveRuns, query.text, onlyMine, filter, currentLogin) {
        liveRuns.filter { run ->
            val passesMine = !onlyMine || run.actor.equals(currentLogin, ignoreCase = true)
            val passesFilter = when (filter) {
                ActionsRunFilter.ALL -> true
                ActionsRunFilter.ACTIVE -> run.status == "queued" || run.status == "in_progress"
                ActionsRunFilter.FAILED -> run.conclusion == "failure"
                ActionsRunFilter.SUCCESS -> run.conclusion == "success"
                ActionsRunFilter.CANCELLED -> run.conclusion == "cancelled"
            }
            val q = query.text.trim()
            val passesQuery = q.isBlank() ||
                run.name.contains(q, ignoreCase = true) ||
                run.branch.contains(q, ignoreCase = true) ||
                run.actor.contains(q, ignoreCase = true) ||
                run.event.contains(q, ignoreCase = true) ||
                run.runNumber.toString().contains(q)
            passesMine && passesFilter && passesQuery
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(SurfaceWhite)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                if (query.text.isEmpty()) {
                    Text("Search workflow runs", color = TextTertiary, fontSize = 14.sp)
                }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    textStyle = androidx.compose.ui.text.TextStyle(color = TextPrimary, fontSize = 14.sp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            IconButton(onClick = { scope.launch { refreshRuns() } }) {
                if (refreshing) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = Blue, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Rounded.Refresh, null, tint = Blue)
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ActionsFilterChip("All", filter == ActionsRunFilter.ALL) { filter = ActionsRunFilter.ALL }
            ActionsFilterChip("Active", filter == ActionsRunFilter.ACTIVE) { filter = ActionsRunFilter.ACTIVE }
            ActionsFilterChip("Failed", filter == ActionsRunFilter.FAILED) { filter = ActionsRunFilter.FAILED }
            ActionsFilterChip("Success", filter == ActionsRunFilter.SUCCESS) { filter = ActionsRunFilter.SUCCESS }
            ActionsFilterChip("Cancelled", filter == ActionsRunFilter.CANCELLED) { filter = ActionsRunFilter.CANCELLED }
            ActionsFilterChip("Only mine", onlyMine) { onlyMine = !onlyMine }
        }

        Spacer(Modifier.height(8.dp))

        if (filteredRuns.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No workflow runs", color = TextTertiary, fontSize = 14.sp)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)) {
                items(filteredRuns) { run ->
                    val statusColor = when {
                        run.status == "queued" -> Color(0xFFFF9500)
                        run.status == "in_progress" -> Blue
                        run.conclusion == "success" -> Color(0xFF34C759)
                        run.conclusion == "failure" -> Color(0xFFFF3B30)
                        run.conclusion == "cancelled" -> Color(0xFF8E8E93)
                        else -> TextTertiary
                    }
                    val live = run.status == "queued" || run.status == "in_progress"
                    val elapsed = calcRunDuration(run, nowMs)
                    Row(
                        Modifier.fillMaxWidth().clickable { onRunClick(run) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            when {
                                run.status == "queued" -> Icons.Rounded.Schedule
                                run.status == "in_progress" -> Icons.Rounded.Refresh
                                run.conclusion == "success" -> Icons.Rounded.CheckCircle
                                run.conclusion == "failure" -> Icons.Rounded.Error
                                run.conclusion == "cancelled" -> Icons.Rounded.Cancel
                                else -> Icons.Rounded.Article
                            },
                            null,
                            Modifier.size(20.dp),
                            tint = statusColor
                        )
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(run.name, fontSize = 14.sp, color = TextPrimary, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (live) {
                                    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(statusColor.copy(alpha = 0.12f)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                        Text("LIVE", color = statusColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("#${run.runNumber}", fontSize = 11.sp, color = TextTertiary)
                                Text(run.branch, fontSize = 11.sp, color = Blue)
                                Text(run.event, fontSize = 11.sp, color = TextSecondary)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(run.actor, fontSize = 10.sp, color = TextSecondary)
                                Text(if (elapsed.isBlank()) run.updatedAt.take(19).replace("T", " ") else elapsed, fontSize = 10.sp, color = if (live) Blue else TextTertiary)
                            }
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (run.status == "in_progress" || run.status == "queued") {
                                    Chip(Icons.Rounded.Cancel, Strings.cancel, Color(0xFFFF3B30)) {
                                        scope.launch {
                                            val ok = GitHubManager.cancelWorkflowRun(context, repo.owner, repo.name, run.id)
                                            Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                                            refreshRuns()
                                        }
                                    }
                                }
                                if (run.status == "completed") {
                                    Chip(Icons.Rounded.Refresh, Strings.ghRerun) {
                                        scope.launch {
                                            val ok = GitHubManager.rerunWorkflow(context, repo.owner, repo.name, run.id)
                                            Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                                            refreshRuns()
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Box(Modifier.fillMaxWidth().padding(start = 46.dp).height(0.5.dp).background(SeparatorColor))
                }
            }
        }
    }
}

@Composable
internal fun WorkflowRunDetailScreen(repo: GHRepo, runId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var jobs by remember { mutableStateOf<List<GHJob>>(emptyList()) }
    var artifacts by remember { mutableStateOf<List<GHArtifact>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var downloading by remember { mutableStateOf<Long?>(null) }
    var deletingArtifactId by remember { mutableStateOf<Long?>(null) }
    val jobLogs = remember { mutableStateMapOf<Long, String>() }
    val jobStepLogs = remember { mutableStateMapOf<Long, Map<Int, String>>() }
    var loadingJobId by remember { mutableStateOf<Long?>(null) }
    var expandedJobId by remember { mutableStateOf<Long?>(null) }
    var expandedStepKey by remember { mutableStateOf<String?>(null) }
    var onlyFailedJobs by remember { mutableStateOf(false) }
    var onlyActiveJobs by remember { mutableStateOf(false) }
    var loadedLogsFilter by remember { mutableStateOf(TextFieldValue("")) }
    var refreshing by remember { mutableStateOf(false) }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    suspend fun refreshAll() {
        refreshing = true
        jobs = GitHubManager.getWorkflowRunJobs(context, repo.owner, repo.name, runId)
        artifacts = GitHubManager.getRunArtifacts(context, repo.owner, repo.name, runId)
        refreshing = false
        loading = false
    }

    LaunchedEffect(runId) { refreshAll() }

    LaunchedEffect(jobs, expandedJobId, expandedStepKey) {
        while (true) {
            val hasLive = jobs.any { it.status == "queued" || it.status == "in_progress" }
            nowMs = System.currentTimeMillis()
            if (hasLive) {
                delay(1000)
                if (nowMs % 3000L < 1100L) {
                    refreshAll()
                    val expandedLiveJob = jobs.firstOrNull {
                        (it.id == expandedJobId || expandedStepKey?.startsWith("${it.id}:") == true) &&
                            (it.status == "queued" || it.status == "in_progress")
                    }
                    if (expandedLiveJob != null) {
                        refreshJobLogsNow(context, repo, expandedLiveJob, jobLogs, jobStepLogs)
                    }
                }
            } else {
                delay(1500)
            }
        }
    }

    val filteredJobs = remember(jobs, onlyFailedJobs, onlyActiveJobs, loadedLogsFilter.text, jobLogs) {
        jobs.filter { job ->
            val failedOk = !onlyFailedJobs || job.conclusion == "failure"
            val activeOk = !onlyActiveJobs || job.status == "queued" || job.status == "in_progress"
            val q = loadedLogsFilter.text.trim()
            val searchOk = q.isBlank() || job.name.contains(q, true) || (jobLogs[job.id]?.contains(q, true) == true)
            failedOk && activeOk && searchOk
        }
    }

    Column(Modifier.fillMaxSize().background(SurfaceLight)) {
        GHTopBar("Run #$runId", onBack = onBack) {
            IconButton(onClick = { scope.launch { refreshAll() } }) {
                if (refreshing) CircularProgressIndicator(Modifier.size(18.dp), color = Blue, strokeWidth = 2.dp)
                else Icon(Icons.Rounded.Refresh, null, tint = Blue)
            }
            IconButton(onClick = {
                scope.launch {
                    val ok = GitHubManager.rerunWorkflow(context, repo.owner, repo.name, runId)
                    Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                    refreshAll()
                }
            }) { Icon(Icons.Rounded.PlayArrow, null, tint = Blue) }
            IconButton(onClick = {
                scope.launch {
                    val ok = GitHubManager.cancelWorkflowRun(context, repo.owner, repo.name, runId)
                    Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                    refreshAll()
                }
            }) { Icon(Icons.Rounded.Cancel, null, tint = Color(0xFFFF3B30)) }
        }

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Blue, modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
            }
        } else {
            val successCount = jobs.count { it.conclusion == "success" }
            val failedCount = jobs.count { it.conclusion == "failure" }
            val runningCount = jobs.count { it.status == "queued" || it.status == "in_progress" }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActionsFilterChip("Success $successCount", false) {}
                ActionsFilterChip("Failed $failedCount", onlyFailedJobs) { onlyFailedJobs = !onlyFailedJobs }
                ActionsFilterChip("Running $runningCount", onlyActiveJobs) { onlyActiveJobs = !onlyActiveJobs }
                ActionsFilterChip("Failed logs", false) {
                    val failed = jobs.firstOrNull { it.conclusion == "failure" }
                    if (failed != null) {
                        expandedJobId = failed.id
                        ensureJobLogsLoaded(scope, context, repo, failed, jobLogs, jobStepLogs, force = true) { loadingJobId = it }
                    }
                }
                ActionsFilterChip("Running logs", false) {
                    val running = jobs.firstOrNull { it.status == "queued" || it.status == "in_progress" }
                    if (running != null) {
                        expandedJobId = running.id
                        ensureJobLogsLoaded(scope, context, repo, running, jobLogs, jobStepLogs, force = true) { loadingJobId = it }
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(SurfaceWhite).padding(horizontal = 12.dp, vertical = 10.dp)) {
                    if (loadedLogsFilter.text.isEmpty()) {
                        Text("Filter loaded logs", color = TextTertiary, fontSize = 14.sp)
                    }
                    BasicTextField(
                        value = loadedLogsFilter,
                        onValueChange = { loadedLogsFilter = it },
                        textStyle = androidx.compose.ui.text.TextStyle(color = TextPrimary, fontSize = 14.sp),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Icon(Icons.Rounded.Search, null, tint = Blue)
            }

            LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
                items(filteredJobs) { job ->
                    val jColor = when {
                        job.status == "queued" || job.status == "in_progress" -> Blue
                        job.conclusion == "success" -> Color(0xFF34C759)
                        job.conclusion == "failure" -> Color(0xFFFF3B30)
                        job.conclusion == "cancelled" -> Color(0xFF8E8E93)
                        else -> Color(0xFFFF9500)
                    }
                    val jobElapsed = calcJobDuration(job, nowMs)
                    Column(
                        Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(12.dp)).background(SurfaceWhite).padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(
                                when {
                                    job.status == "queued" -> Icons.Rounded.Schedule
                                    job.status == "in_progress" -> Icons.Rounded.Refresh
                                    job.conclusion == "success" -> Icons.Rounded.CheckCircle
                                    job.conclusion == "failure" -> Icons.Rounded.Error
                                    job.conclusion == "cancelled" -> Icons.Rounded.Cancel
                                    else -> Icons.Rounded.Warning
                                },
                                null,
                                Modifier.size(18.dp),
                                tint = jColor
                            )
                            Text(job.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, modifier = Modifier.weight(1f))
                            Text(jobElapsed, fontSize = 10.sp, color = if (job.status == "in_progress") Blue else TextTertiary)
                        }

                        Spacer(Modifier.height(6.dp))
                        job.steps.forEach { step ->
                            val sColor = stepStatusColor(step)
                            val stepKey = "${job.id}:${step.number}"
                            val stepLog = jobStepLogs[job.id]?.get(step.number)
                            Column(Modifier.fillMaxWidth()) {
                                Row(
                                    Modifier.fillMaxWidth().clickable {
                                        expandedStepKey = if (expandedStepKey == stepKey) null else stepKey
                                        if (jobLogs[job.id] == null) {
                                            ensureJobLogsLoaded(scope, context, repo, job, jobLogs, jobStepLogs, force = job.status == "queued" || job.status == "in_progress") { loadingJobId = it }
                                        }
                                    }.padding(start = 8.dp, top = 3.dp, bottom = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Rounded.Check, null, Modifier.size(12.dp), tint = sColor)
                                    Text(step.name, fontSize = 12.sp, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    Text(displayStepStatus(step), fontSize = 10.sp, color = stepStatusColor(step))
                                }
                                if (expandedStepKey == stepKey) {
                                    val liveMessage = when (displayStepStatus(step)) {
                                        "queued" -> "Log not available yet."
                                        "running" -> "Waiting for live log..."
                                        else -> "No step log captured."
                                    }
                                    val shownStepLog = compactLogForDisplay(stepLog ?: liveMessage)
                                    Box(
                                        Modifier.fillMaxWidth().padding(start = 24.dp, top = 4.dp, bottom = 6.dp)
                                            .clip(RoundedCornerShape(8.dp)).background(Color(0xFF0D1117)).padding(8.dp)
                                    ) {
                                        if (jobLogs[job.id] == null || loadingJobId == job.id) {
                                            CircularProgressIndicator(Modifier.size(16.dp), color = Blue, strokeWidth = 2.dp)
                                        } else {
                                            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 220.dp)) {
                                                item {
                                                    Text(
                                                        shownStepLog,
                                                        fontSize = 9.sp,
                                                        fontFamily = FontFamily.Monospace,
                                                        color = Color(0xFFC9D1D9),
                                                        lineHeight = 13.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Chip(
                                if (expandedJobId == job.id) Icons.Rounded.FilterList else Icons.Rounded.Article,
                                if (expandedJobId == job.id) "Hide full log" else "Show full log"
                            ) {
                                if (expandedJobId == job.id) {
                                    expandedJobId = null
                                } else {
                                    expandedJobId = job.id
                                    ensureJobLogsLoaded(scope, context, repo, job, jobLogs, jobStepLogs, force = job.status == "queued" || job.status == "in_progress") { loadingJobId = it }
                                }
                            }
                            if (jobLogs[job.id] != null) {
                                Chip(Icons.Rounded.ContentCopy, "Copy full log") {
                                    val clip = android.content.ClipData.newPlainText("logs", jobLogs[job.id])
                                    (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(clip)
                                    Toast.makeText(context, Strings.done, Toast.LENGTH_SHORT).show()
                                }
                            }
                            if (loadingJobId == job.id) {
                                CircularProgressIndicator(Modifier.size(16.dp), color = Blue, strokeWidth = 2.dp)
                            }
                        }

                        if (expandedJobId == job.id && jobLogs[job.id] != null) {
                            Spacer(Modifier.height(8.dp))
                            Box(
                                Modifier.fillMaxWidth().heightIn(max = 420.dp).clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF0D1117)).padding(10.dp)
                            ) {
                                LazyColumn(Modifier.fillMaxWidth()) {
                                    item {
                                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                                            Text(
                                                compactLogForDisplay(jobLogs[job.id]!!),
                                                fontSize = 9.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = Color(0xFFC9D1D9),
                                                lineHeight = 13.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (artifacts.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(8.dp))
                        Text(Strings.ghArtifacts, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Spacer(Modifier.height(8.dp))
                    }
                    items(artifacts) { artifact ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(SurfaceWhite)
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                Modifier.weight(1f).clickable(enabled = !artifact.expired && downloading != artifact.id) {
                                    downloading = artifact.id
                                    scope.launch {
                                        val dest = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "GlassFiles_Git/${artifact.name}.zip")
                                        val ok = GitHubManager.downloadArtifact(context, repo.owner, repo.name, artifact.id, dest)
                                        Toast.makeText(context, if (ok) "${Strings.done}: ${dest.name}" else Strings.error, Toast.LENGTH_SHORT).show()
                                        downloading = null
                                    }
                                },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Rounded.Article, null, Modifier.size(20.dp), tint = if (artifact.expired) TextTertiary else Blue)
                                Column(Modifier.weight(1f)) {
                                    Text(artifact.name, fontSize = 14.sp, color = if (artifact.expired) TextTertiary else TextPrimary, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(ghFmtSize(artifact.sizeInBytes), fontSize = 11.sp, color = TextSecondary)
                                        if (artifact.expired) Text(Strings.ghExpired, fontSize = 11.sp, color = Color(0xFFFF3B30))
                                        else Text(artifact.createdAt.take(10), fontSize = 11.sp, color = TextTertiary)
                                    }
                                }
                            }
                            if (!artifact.expired) {
                                IconButton(onClick = {
                                    deletingArtifactId = artifact.id
                                    scope.launch {
                                        val ok = deleteArtifactDirect(context, repo.owner, repo.name, artifact.id)
                                        Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                                        deletingArtifactId = null
                                        refreshAll()
                                    }
                                }) {
                                    if (deletingArtifactId == artifact.id) CircularProgressIndicator(Modifier.size(16.dp), color = Color(0xFFFF3B30), strokeWidth = 2.dp)
                                    else Icon(Icons.Rounded.Delete, null, tint = Color(0xFFFF3B30))
                                }
                            }
                            if (downloading == artifact.id) CircularProgressIndicator(Modifier.size(18.dp), color = Blue, strokeWidth = 2.dp)
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
        }
    }
}

private fun ensureJobLogsLoaded(
    scope: kotlinx.coroutines.CoroutineScope,
    context: android.content.Context,
    repo: GHRepo,
    job: GHJob,
    jobLogs: MutableMap<Long, String>,
    jobStepLogs: MutableMap<Long, Map<Int, String>>,
    force: Boolean = false,
    setLoading: (Long?) -> Unit
) {
    if (!force && jobLogs[job.id] != null) return
    scope.launch {
        setLoading(job.id)
        val log = GitHubManager.getJobLogs(context, repo.owner, repo.name, job.id)
        jobLogs[job.id] = log
        jobStepLogs[job.id] = splitLogsBySteps(job, log)
        setLoading(null)
    }
}

private suspend fun refreshJobLogsNow(
    context: android.content.Context,
    repo: GHRepo,
    job: GHJob,
    jobLogs: MutableMap<Long, String>,
    jobStepLogs: MutableMap<Long, Map<Int, String>>
) {
    val log = GitHubManager.getJobLogs(context, repo.owner, repo.name, job.id)
    jobLogs[job.id] = log
    jobStepLogs[job.id] = splitLogsBySteps(job, log)
}

private fun splitLogsBySteps(job: GHJob, raw: String): Map<Int, String> {
    if (raw.isBlank()) return emptyMap()
    val lines = raw.lines()
    val stepStarts = lines.mapIndexedNotNull { index, line ->
        val n = normalizeLogLine(line)
        if (looksLikeStepBoundary(n)) index else null
    }
    val result = linkedMapOf<Int, String>()
    val steps = job.steps
    if (steps.isEmpty()) return emptyMap()

    if (stepStarts.isEmpty()) {
        // Fallback: assign entire log only to current running/completed step nearest to the end.
        val target = steps.lastOrNull { displayStepStatus(it) !in setOf("queued", "pending") } ?: steps.first()
        result[target.number] = raw.trim()
        return result
    }

    val preamble = lines.subList(0, stepStarts.first()).joinToString("\n").trim()
    var sectionOffset = 0
    if (preamble.isNotBlank()) {
        result[steps.first().number] = preamble
        sectionOffset = 1
    }

    val sections = mutableListOf<String>()
    for (i in stepStarts.indices) {
        val s = stepStarts[i]
        val e = if (i < stepStarts.lastIndex) stepStarts[i + 1] else lines.size
        sections += lines.subList(s, e).joinToString("\n").trim()
    }

    var sectionIndex = 0
    for (stepIndex in sectionOffset until steps.size) {
        if (sectionIndex >= sections.size) break
        val step = steps[stepIndex]
        val currentSection = sections[sectionIndex]
        val currentBoundary = normalizeLogLine(currentSection.lineSequence().firstOrNull().orEmpty())
        val matchedByName = stepBoundaryMatchesStep(currentBoundary, step.name)
        if (matchedByName || stepIndex == sectionOffset) {
            result[step.number] = currentSection
            sectionIndex++
        } else {
            // Sequence fallback
            result[step.number] = currentSection
            sectionIndex++
        }
    }

    // If there are extra sections, append them to the last non-queued step.
    if (sectionIndex < sections.size) {
        val lastStepNum = steps.lastOrNull { result.containsKey(it.number) }?.number ?: steps.last().number
        val extra = sections.drop(sectionIndex).joinToString("\n\n")
        result[lastStepNum] = listOfNotNull(result[lastStepNum], extra).joinToString("\n\n").trim()
    }

    return result.mapValues { (_, value) -> value.trim() }.filterValues { it.isNotBlank() }
}

private fun normalizeLogLine(line: String): String {
    var out = line.trim()
    out = out.replace(Regex("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?Z\\s*"), "")
    return out
}

private fun looksLikeStepBoundary(normalized: String): Boolean {
    return normalized.startsWith("##[group]Run ") ||
        normalized.startsWith("##[group]Post ") ||
        normalized.startsWith("##[group]Complete job")
}

private fun stepBoundaryMatchesStep(boundary: String, stepName: String): Boolean {
    val s = stepName.lowercase()
    val b = boundary.lowercase()
    return when {
        s == "checkout" -> "checkout" in b
        s.startsWith("set up jdk") -> "setup-java" in b || "jdk" in b || "java" in b
        s.contains("android sdk") -> "setup-android" in b || "android-actions" in b || "android sdk" in b
        s.contains("ndk") || s.contains("cmake") -> "ndk" in b || "cmake" in b
        s.contains("cache gradle") -> "cache" in b && "gradle" in b
        s.contains("create debug keystore") -> "keystore" in b
        s.contains("build debug apk") -> "assembledebug" in b || "debug apk" in b
        s.contains("build release apk") -> "assemblerelease" in b || "release apk" in b
        s.contains("upload debug apk") -> "upload-artifact" in b || "debug apk" in b
        s.contains("upload release apk") -> "upload-artifact" in b || "release apk" in b
        s.startsWith("post ") -> b.startsWith("##[group]post ")
        s == "complete job" -> "complete job" in b
        else -> s.isNotBlank() && b.contains(s)
    }
}

private fun compactLogForDisplay(raw: String): String {
    return raw.lineSequence().map { line ->
        val m = Regex("^(\\d{4}-\\d{2}-\\d{2})T(\\d{2}:\\d{2}:\\d{2})(?:\\.\\d+)?Z\\s*(.*)$").find(line)
        val cleaned = if (m != null) {
            "${m.groupValues[2]}  ${m.groupValues[3]}"
        } else line
        cleaned
            .replace("##[group]", "")
            .replace("##[endgroup]", "")
            .replace("##[warning]", "warning: ")
            .replace("##[error]", "error: ")
            .trimEnd()
    }.joinToString("\n").trim()
}

private suspend fun deleteArtifactDirect(context: android.content.Context, owner: String, repo: String, artifactId: Long): Boolean =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val token = GitHubManager.getToken(context)
            val conn = (URL("https://api.github.com/repos/$owner/$repo/actions/artifacts/$artifactId").openConnection() as HttpURLConnection).apply {
                requestMethod = "DELETE"
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Accept", "application/vnd.github+json")
                connectTimeout = 15000
                readTimeout = 15000
            }
            val code = conn.responseCode
            conn.disconnect()
            code == 204 || code == 200
        } catch (_: Exception) {
            false
        }
    }

@Composable
private fun ActionsFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(8.dp))
            .background(if (selected) Blue.copy(alpha = 0.14f) else SurfaceWhite)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(label, fontSize = 12.sp, color = if (selected) Blue else TextSecondary, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

private fun calcRunDuration(run: GHWorkflowRun, nowMs: Long): String {
    val start = parseIsoMs(run.createdAt) ?: return ""
    val end = if (run.status == "completed") parseIsoMs(run.updatedAt) ?: nowMs else nowMs
    return formatDuration((end - start).coerceAtLeast(0L))
}

private fun calcJobDuration(job: GHJob, nowMs: Long): String {
    val start = parseIsoMs(job.startedAt) ?: return ""
    val end = if (job.status == "completed") parseIsoMs(job.completedAt) ?: nowMs else nowMs
    return formatDuration((end - start).coerceAtLeast(0L))
}

private fun parseIsoMs(value: String): Long? = try {
    if (value.isBlank()) null else ISO_FMT.parse(value)?.time
} catch (_: Exception) { null }

private fun formatDuration(ms: Long): String {
    val sec = ms / 1000
    return when {
        sec < 60 -> "${sec}s"
        sec < 3600 -> "${sec / 60}m ${sec % 60}s"
        else -> "${sec / 3600}h ${(sec % 3600) / 60}m ${(sec % 60)}s"
    }
}



private fun displayStepStatus(step: GHStep): String {
    val c = step.conclusion.trim().lowercase()
    val s = step.status.trim().lowercase()
    return when {
        c == "success" -> "success"
        c == "failure" -> "failed"
        c == "cancelled" -> "cancelled"
        c == "skipped" -> "skipped"
        c == "neutral" -> "neutral"
        c == "timed_out" -> "timed out"
        c == "action_required" -> "action required"
        c == "startup_failure" -> "startup failure"
        c == "stale" -> "stale"
        c.isNotBlank() && c != "null" -> c
        s == "in_progress" -> "running"
        s == "queued" -> "queued"
        s == "completed" -> "completed"
        s.isNotBlank() && s != "null" -> s
        else -> "pending"
    }
}

private fun stepStatusColor(step: GHStep): Color {
    return when (displayStepStatus(step)) {
        "success" -> Color(0xFF34C759)
        "failed" -> Color(0xFFFF3B30)
        "cancelled" -> Color(0xFF8E8E93)
        "skipped" -> Color(0xFF8E8E93)
        "running" -> Blue
        "queued" -> Color(0xFFFF9500)
        "completed" -> Color(0xFF8E8E93)
        else -> Color(0xFFFF9500)
    }
}

private val ISO_FMT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}