package com.glassfiles.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Article
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.glassfiles.R
import com.glassfiles.data.Strings
import com.glassfiles.data.github.GHActionRunner
import com.glassfiles.data.github.GHActionSecret
import com.glassfiles.data.github.GHActionVariable
import com.glassfiles.data.github.GHActionsCacheEntry
import com.glassfiles.data.github.GHActionsCacheUsage
import com.glassfiles.data.github.GHActionsPermissions
import com.glassfiles.data.github.GHActionsRetention
import com.glassfiles.data.github.GHActionsUsage
import com.glassfiles.data.github.GHArtifact
import com.glassfiles.data.github.GHCheckAnnotation
import com.glassfiles.data.github.GHCheckRun
import com.glassfiles.data.github.GHJob
import com.glassfiles.data.github.GHPendingDeployment
import com.glassfiles.data.github.GHRepo
import com.glassfiles.data.github.GHStep
import com.glassfiles.data.github.GHWorkflow
import com.glassfiles.data.github.GHWorkflowDispatchInput
import com.glassfiles.data.github.GHWorkflowDispatchSchema
import com.glassfiles.data.github.GHWorkflowRun
import com.glassfiles.data.github.GHWorkflowRunReview
import com.glassfiles.data.github.GHWorkflowPermissions
import com.glassfiles.data.github.GitHubManager
import com.glassfiles.data.github.KernelErrorCatalog
import com.glassfiles.data.github.KernelErrorPatterns
import com.glassfiles.ui.theme.Blue
import com.glassfiles.ui.theme.SeparatorColor
import com.glassfiles.ui.theme.SurfaceLight
import com.glassfiles.ui.theme.SurfaceWhite
import com.glassfiles.ui.theme.TextPrimary
import com.glassfiles.ui.theme.TextSecondary
import com.glassfiles.ui.theme.TextTertiary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private enum class ActionsRunFilter { ALL, ACTIVE, QUEUED, FAILED, SUCCESS, CANCELLED, SKIPPED }

private enum class RunDetailSection { SUMMARY, JOBS, ARTIFACTS, CHECKS }

private const val ACTIONS_INPUT_PREFS = "github_actions_dispatch_inputs"

private data class ArtifactGroup(
    val label: String,
    val color: Color,
    val order: Int,
    val items: List<GHArtifact>
)

private data class MatrixJobGroup(
    val name: String,
    val jobs: List<GHJob>
)

private sealed class JobListItem {
    data class GroupHeader(val group: MatrixJobGroup, val expanded: Boolean) : JobListItem()
    data class JobRow(val job: GHJob) : JobListItem()
}

private const val ACTIONS_POLL_DELAY_MS = 5000L
private const val ACTIONS_BACKOFF_DELAY_MS = 15000L

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
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var workflows by remember { mutableStateOf<List<GHWorkflow>>(emptyList()) }
    var branches by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedWorkflowId by remember { mutableStateOf<Long?>(null) }
    var selectedBranch by remember(repo.owner, repo.name) { mutableStateOf(repo.defaultBranch) }
    var actionsNotice by remember { mutableStateOf<String?>(null) }
    var dispatchSchema by remember { mutableStateOf<GHWorkflowDispatchSchema?>(null) }
    var dispatchInputValues by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var dispatching by remember { mutableStateOf(false) }
    var showRunsHistory by remember { mutableStateOf(false) }

    suspend fun refreshOverview() {
        refreshing = true
        try {
            val fetchedWorkflows = GitHubManager.getWorkflows(context, repo.owner, repo.name)
            workflows = fetchedWorkflows
            if (selectedWorkflowId != null && fetchedWorkflows.none { it.id == selectedWorkflowId }) {
                selectedWorkflowId = null
            }
            if (selectedWorkflowId == null && fetchedWorkflows.isNotEmpty()) {
                selectedWorkflowId = fetchedWorkflows.first().id
            }
            liveRuns = GitHubManager.getWorkflowRuns(
                context = context,
                owner = repo.owner,
                repo = repo.name,
                perPage = 20,
                page = 1
            )
            actionsNotice = null
        } catch (e: Exception) {
            actionsNotice = actionsFriendlyError(e.message)
        } finally {
            refreshing = false
        }
    }

    LaunchedEffect(runs) {
        if (liveRuns.isEmpty() && runs.isNotEmpty()) liveRuns = runs
    }

    LaunchedEffect(repo.owner, repo.name) {
        refreshOverview()
        branches = GitHubManager.getBranches(context, repo.owner, repo.name)
    }

    LaunchedEffect(workflows, selectedWorkflowId, selectedBranch) {
        val workflow = workflows.firstOrNull { it.id == selectedWorkflowId }
        val schema = workflow?.let {
            GitHubManager.getWorkflowDispatchSchema(context, repo.owner, repo.name, it.path, selectedBranch)
        }
        dispatchSchema = schema
        dispatchInputValues = if (workflow != null && schema != null) {
            loadSavedDispatchInputValues(context, repo, workflow, schema)
        } else {
            emptyMap()
        }
    }

    LaunchedEffect(liveRuns) {
        while (true) {
            val hasLive = liveRuns.any { isRunActive(it) }
            nowMs = System.currentTimeMillis()
            if (hasLive) {
                delay(if (actionsNotice != null) ACTIONS_BACKOFF_DELAY_MS else ACTIONS_POLL_DELAY_MS)
                if (liveRuns.any { isRunActive(it) }) {
                    try {
                        liveRuns = GitHubManager.getWorkflowRuns(
                            context = context,
                            owner = repo.owner,
                            repo = repo.name,
                            perPage = 20,
                            page = 1
                        )
                        actionsNotice = null
                    } catch (e: Exception) {
                        actionsNotice = actionsFriendlyError(e.message)
                    }
                }
            } else {
                delay(1500)
            }
        }
    }

    if (showRunsHistory) {
        ActionsRunsHistoryScreen(
            repo = repo,
            workflows = workflows,
            branches = branches,
            initialRuns = liveRuns,
            onBack = { showRunsHistory = false },
            onRunClick = onRunClick
        )
        return
    }

    val activeCount = remember(liveRuns) { liveRuns.count { isRunActive(it) } }
    val successCount = remember(liveRuns) { liveRuns.count { it.conclusion == "success" } }
    val failedCount = remember(liveRuns) { liveRuns.count { it.conclusion == "failure" } }
    val latestRun = remember(liveRuns) { liveRuns.firstOrNull() }
    val missingRequiredInputs = remember(dispatchSchema, dispatchInputValues) {
        missingDispatchInputs(dispatchSchema, dispatchInputValues)
    }

    Column(Modifier.fillMaxSize().background(SurfaceLight)) {
        actionsNotice?.let { notice ->
            Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFFF9500).copy(alpha = 0.1f)).padding(10.dp)) {
                Text(notice, fontSize = 12.sp, color = TextSecondary)
            }
        }
        ActionsOverviewHeader(
            workflows = workflows,
            branches = branches,
            selectedWorkflowId = selectedWorkflowId,
            onSelectWorkflow = { workflowId ->
                selectedWorkflowId = workflowId
            },
            activeCount = activeCount,
            successCount = successCount,
            failedCount = failedCount,
            totalRuns = liveRuns.size,
            selectedBranch = selectedBranch,
            onBranchChange = { selectedBranch = it },
            dispatchSchema = dispatchSchema,
            dispatchInputValues = dispatchInputValues,
            missingRequiredInputs = missingRequiredInputs,
            onDispatchInputChange = { key, value -> dispatchInputValues = dispatchInputValues + (key to value) },
            onToggleWorkflowState = { workflow ->
                scope.launch {
                    val ok = if (workflow.state == "active") {
                        GitHubManager.disableWorkflow(context, repo.owner, repo.name, workflow.id)
                    } else {
                        GitHubManager.enableWorkflow(context, repo.owner, repo.name, workflow.id)
                    }
                    Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                    if (ok) refreshOverview()
                }
            },
            dispatching = dispatching,
            onDispatch = {
                val workflowId = selectedWorkflowId
                val schema = dispatchSchema
                if (workflowId == null) {
                    Toast.makeText(context, Strings.ghNoWorkflows, Toast.LENGTH_SHORT).show()
                } else if (schema == null) {
                    Toast.makeText(context, "Manual launch unavailable", Toast.LENGTH_SHORT).show()
                } else if (selectedBranch.isBlank()) {
                    Toast.makeText(context, "Branch required", Toast.LENGTH_SHORT).show()
                } else if (missingRequiredInputs.isNotEmpty()) {
                    Toast.makeText(context, "Required inputs missing: ${missingRequiredInputs.joinToString(", ")}", Toast.LENGTH_LONG).show()
                } else {
                    dispatching = true
                    scope.launch {
                        try {
                            val knownRunIds = GitHubManager
                                .getWorkflowRuns(context, repo.owner, repo.name, workflowId, perPage = 10)
                                .map { it.id }
                                .toSet()
                            val dispatchInputs = schema.inputs.associate { input ->
                                input.key to dispatchInputValue(input, dispatchInputValues)
                            }.filterValues { it.isNotBlank() }
                            val result = GitHubManager.dispatchWorkflowDetailed(
                                context = context,
                                owner = repo.owner,
                                repo = repo.name,
                                workflowId = workflowId,
                                branch = selectedBranch,
                                inputs = dispatchInputs
                            )
                            Toast.makeText(context, if (result.success) Strings.done else result.message.ifBlank { Strings.error }, Toast.LENGTH_LONG).show()
                            if (result.success) {
                                workflows.firstOrNull { it.id == workflowId }?.let { workflow ->
                                    saveDispatchInputValues(context, repo, workflow, dispatchInputs)
                                }
                                val newRun = findNewActionsDispatchRun(
                                    context = context,
                                    repo = repo,
                                    workflowId = workflowId,
                                    branch = selectedBranch,
                                    knownRunIds = knownRunIds
                                )
                                refreshOverview()
                                if (newRun != null) onRunClick(newRun)
                            }
                        } finally {
                            dispatching = false
                        }
                    }
                }
            },
            refreshing = refreshing,
            onRefresh = { scope.launch { refreshOverview() } },
            latestRun = latestRun,
            nowMs = nowMs,
            onOpenRuns = { showRunsHistory = true },
            onOpenLatestRun = { latestRun?.let(onRunClick) }
        )
    }
}

private suspend fun findNewActionsDispatchRun(
    context: android.content.Context,
    repo: GHRepo,
    workflowId: Long,
    branch: String,
    knownRunIds: Set<Long>
): GHWorkflowRun? {
    repeat(10) {
        delay(1500)
        val runs = GitHubManager.getWorkflowRuns(context, repo.owner, repo.name, workflowId, perPage = 10)
        val dispatchRuns = runs.filter { candidate ->
            val newRun = candidate.id !in knownRunIds
            val dispatchRun = candidate.event == "workflow_dispatch"
            newRun && dispatchRun
        }
        val run = dispatchRuns.firstOrNull { candidate ->
            candidate.branch.isBlank() || branch.isBlank() || candidate.branch == branch
        } ?: dispatchRuns.firstOrNull()
        if (run != null) return run
    }
    return null
}

@Composable
private fun ActionsRunsHistoryScreen(
    repo: GHRepo,
    workflows: List<GHWorkflow>,
    branches: List<String>,
    initialRuns: List<GHWorkflowRun>,
    onBack: () -> Unit,
    onRunClick: (GHWorkflowRun) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var runs by remember(initialRuns) { mutableStateOf(initialRuns) }
    var query by remember { mutableStateOf(TextFieldValue("")) }
    var filter by remember { mutableStateOf(ActionsRunFilter.ALL) }
    var selectedWorkflowId by remember { mutableStateOf<Long?>(null) }
    var selectedBranch by remember { mutableStateOf<String?>(null) }
    var selectedEvent by remember { mutableStateOf<String?>(null) }
    var onlyMine by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf(1) }
    var hasMore by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var pullDistance by remember { mutableStateOf(0f) }
    val listState = rememberLazyListState()
    val currentLogin = remember { GitHubManager.getCachedUser(context)?.login.orEmpty() }

    suspend fun load(reset: Boolean = true) {
        refreshing = true
        try {
            val nextPage = if (reset) 1 else page + 1
            val fetched = GitHubManager.getWorkflowRuns(
                context = context,
                owner = repo.owner,
                repo = repo.name,
                workflowId = selectedWorkflowId,
                perPage = 30,
                page = nextPage,
                branch = selectedBranch,
                event = selectedEvent,
                status = githubStatusFilter(filter)
            )
            page = nextPage
            hasMore = fetched.size >= 30
            runs = if (reset) fetched else (runs + fetched).distinctBy { it.id }
        } finally {
            refreshing = false
        }
    }

    LaunchedEffect(repo.owner, repo.name, selectedWorkflowId, selectedBranch, selectedEvent, filter) {
        load(reset = true)
    }

    LaunchedEffect(runs) {
        while (true) {
            val hasLive = runs.any { isRunActive(it) }
            nowMs = System.currentTimeMillis()
            if (hasLive) {
                delay(5000)
                if (runs.any { isRunActive(it) }) load(reset = true)
            } else {
                delay(1500)
            }
        }
    }

    val visibleRuns = remember(runs, query.text, filter, onlyMine, currentLogin) {
        val q = query.text.trim()
        runs.filter { run ->
            val passesFilter = when (filter) {
                ActionsRunFilter.ALL -> true
                ActionsRunFilter.ACTIVE -> isRunActive(run)
                ActionsRunFilter.QUEUED -> run.status in setOf("queued", "pending", "waiting", "requested")
                ActionsRunFilter.FAILED -> run.conclusion == "failure"
                ActionsRunFilter.SUCCESS -> run.conclusion == "success"
                ActionsRunFilter.CANCELLED -> run.conclusion == "cancelled"
                ActionsRunFilter.SKIPPED -> run.conclusion == "skipped"
            }
            val passesQuery = q.isBlank() ||
                run.name.contains(q, true) ||
                run.displayTitle.contains(q, true) ||
                run.branch.contains(q, true) ||
                run.event.contains(q, true) ||
                run.actor.contains(q, true) ||
                run.headSha.contains(q, true) ||
                run.runNumber.toString().contains(q)
            val passesMine = !onlyMine || currentLogin.isNotBlank() && run.actor.equals(currentLogin, ignoreCase = true)
            passesFilter && passesQuery && passesMine
        }
    }

    Column(Modifier.fillMaxSize().background(SurfaceLight)) {
        GHTopBar("Build history", subtitle = "${visibleRuns.size} workflow runs", onBack = onBack) {
            IconButton(onClick = { scope.launch { load(reset = true) } }) {
                if (refreshing) CircularProgressIndicator(Modifier.size(18.dp), color = Blue, strokeWidth = 2.dp)
                else Icon(Icons.Rounded.Refresh, null, tint = Blue)
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(SurfaceWhite)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                if (query.text.isEmpty()) Text("Search runs", color = TextTertiary, fontSize = 14.sp)
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    textStyle = androidx.compose.ui.text.TextStyle(color = TextPrimary, fontSize = 14.sp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Icon(Icons.Rounded.Search, null, tint = Blue)
        }

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ActionsFilterChip("All", filter == ActionsRunFilter.ALL) { filter = ActionsRunFilter.ALL }
            ActionsFilterChip("Active", filter == ActionsRunFilter.ACTIVE) { filter = ActionsRunFilter.ACTIVE }
            ActionsFilterChip("Queued", filter == ActionsRunFilter.QUEUED) { filter = ActionsRunFilter.QUEUED }
            ActionsFilterChip("Failed", filter == ActionsRunFilter.FAILED) { filter = ActionsRunFilter.FAILED }
            ActionsFilterChip("Success", filter == ActionsRunFilter.SUCCESS) { filter = ActionsRunFilter.SUCCESS }
            ActionsFilterChip("Cancelled", filter == ActionsRunFilter.CANCELLED) { filter = ActionsRunFilter.CANCELLED }
            ActionsFilterChip("Skipped", filter == ActionsRunFilter.SKIPPED) { filter = ActionsRunFilter.SKIPPED }
            if (currentLogin.isNotBlank()) {
                ActionsFilterChip("Mine", onlyMine) { onlyMine = !onlyMine }
            }
        }

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ActionsFilterChip("All workflows", selectedWorkflowId == null) { selectedWorkflowId = null }
            workflows.forEach { workflow ->
                ActionsFilterChip(workflow.name.ifBlank { workflow.path.substringAfterLast('/') }, selectedWorkflowId == workflow.id) {
                    selectedWorkflowId = workflow.id
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ActionsFilterChip("All branches", selectedBranch == null) { selectedBranch = null }
            branches.forEach { branch -> ActionsFilterChip(branch, selectedBranch == branch) { selectedBranch = branch } }
            ActionsFilterChip("All events", selectedEvent == null) { selectedEvent = null }
            listOf("workflow_dispatch", "push", "pull_request", "schedule").forEach { event ->
                ActionsFilterChip(event, selectedEvent == event) { selectedEvent = event }
            }
        }

        if (visibleRuns.isEmpty() && !refreshing) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No workflow runs", color = TextTertiary, fontSize = 14.sp)
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().pointerInput(refreshing, listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (pullDistance > 140f && !refreshing) {
                                scope.launch { load(reset = true) }
                            }
                            pullDistance = 0f
                        },
                        onDragCancel = { pullDistance = 0f },
                        onVerticalDrag = { _, dragAmount ->
                            if (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 && dragAmount > 0) {
                                pullDistance += dragAmount
                            }
                        }
                    )
                },
                state = listState,
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 16.dp)
            ) {
                if (pullDistance > 28f || refreshing) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(bottom = 8.dp), contentAlignment = Alignment.Center) {
                            Text(if (refreshing) "Refreshing..." else "Release to refresh", fontSize = 11.sp, color = TextTertiary)
                        }
                    }
                }
                items(visibleRuns) { run ->
                    ModernRunCard(
                        run = run,
                        nowMs = nowMs,
                        onRunClick = { onRunClick(run) },
                        onCancel = {
                            scope.launch {
                                val ok = GitHubManager.cancelWorkflowRun(context, repo.owner, repo.name, run.id)
                                Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                                load(reset = true)
                            }
                        },
                        onRerun = {
                            scope.launch {
                                val ok = GitHubManager.rerunWorkflow(context, repo.owner, repo.name, run.id)
                                Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                                load(reset = true)
                            }
                        }
                    )
                }
                if (hasMore) {
                    item {
                        TextButton(
                            onClick = { scope.launch { load(reset = false) } },
                            enabled = !refreshing,
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        ) {
                            if (refreshing) CircularProgressIndicator(Modifier.size(16.dp), color = Blue, strokeWidth = 2.dp)
                            else Text("Load more runs", color = Blue)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionsOverviewHeader(
    workflows: List<GHWorkflow>,
    branches: List<String>,
    selectedWorkflowId: Long?,
    onSelectWorkflow: (Long?) -> Unit,
    activeCount: Int,
    successCount: Int,
    failedCount: Int,
    totalRuns: Int,
    selectedBranch: String,
    onBranchChange: (String) -> Unit,
    dispatchSchema: GHWorkflowDispatchSchema?,
    dispatchInputValues: Map<String, String>,
    missingRequiredInputs: List<String>,
    onDispatchInputChange: (String, String) -> Unit,
    onToggleWorkflowState: (GHWorkflow) -> Unit,
    dispatching: Boolean,
    onDispatch: () -> Unit,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    latestRun: GHWorkflowRun?,
    nowMs: Long,
    onOpenRuns: () -> Unit,
    onOpenLatestRun: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(SurfaceLight)
            .padding(top = 4.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            StatCard("Total", totalRuns.toString(), Icons.Rounded.Timeline, Blue)
            StatCard("Active", activeCount.toString(), Icons.Rounded.FlashOn, Color(0xFF58A6FF))
            StatCard("Success", successCount.toString(), Icons.Rounded.CheckCircle, Color(0xFF34C759))
            StatCard("Failed", failedCount.toString(), Icons.Rounded.Error, Color(0xFFFF3B30))
        }

        Spacer(Modifier.height(4.dp))

        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceWhite)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = Blue, modifier = Modifier.size(16.dp))
                    Text("Workflow control", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                }
                IconButton(onClick = onRefresh) {
                    if (refreshing) CircularProgressIndicator(Modifier.size(16.dp), color = Blue, strokeWidth = 2.dp)
                    else Icon(Icons.Rounded.Refresh, null, tint = Blue, modifier = Modifier.size(18.dp))
                }
            }

            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (workflows.isEmpty()) {
                    ActionsFilterChip(Strings.ghNoWorkflows, false) {}
                } else {
                    workflows.forEach { workflow ->
                        val selected = workflow.id == selectedWorkflowId
                        ActionsFilterChip(
                            workflow.name.ifBlank { workflow.path.substringAfterLast('/') },
                            selected
                        ) { onSelectWorkflow(workflow.id) }
                    }
                }
            }

            workflows.firstOrNull { it.id == selectedWorkflowId }?.let { selectedWorkflow ->
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MiniActionsBadge(selectedWorkflow.state.ifBlank { "unknown" }, if (selectedWorkflow.state == "active") Color(0xFF34C759) else TextSecondary)
                    Text(selectedWorkflow.path, fontSize = 11.sp, color = TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    TextButton(onClick = { onToggleWorkflowState(selectedWorkflow) }) {
                        Text(if (selectedWorkflow.state == "active") "Disable" else "Enable", color = if (selectedWorkflow.state == "active") Color(0xFFFF3B30) else Blue, fontSize = 12.sp)
                    }
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    buildKindBadges("${selectedWorkflow.name} ${selectedWorkflow.path}").forEach { (label, color) ->
                        MiniActionsBadge(label, color)
                    }
                }
            }

            OutlinedTextField(
                value = selectedBranch,
                onValueChange = onBranchChange,
                label = { Text("Branch / ref") },
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            if (branches.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    branches.take(20).forEach { branch ->
                        ActionsFilterChip(branch, branch == selectedBranch) { onBranchChange(branch) }
                    }
                }
            }

            DynamicDispatchInputs(
                schema = dispatchSchema,
                values = dispatchInputValues,
                missingRequiredInputs = missingRequiredInputs,
                onValueChange = onDispatchInputChange
            )

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    Chip(Icons.Rounded.Timeline, "Open Runs") { onOpenRuns() }
                    if (latestRun != null) {
                        Chip(Icons.Rounded.Article, "Latest #${latestRun.runNumber}") { onOpenLatestRun() }
                    }
                }
                TextButton(onClick = onDispatch, enabled = !dispatching && workflows.isNotEmpty() && dispatchSchema != null && missingRequiredInputs.isEmpty()) {
                    if (dispatching) {
                        CircularProgressIndicator(Modifier.size(16.dp), color = Blue, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Rounded.PlayArrow, null, tint = Blue)
                        Spacer(Modifier.width(6.dp))
                        Text(Strings.ghRunWorkflow, color = Blue)
                    }
                }
            }

            latestRun?.let { run ->
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiniActionsBadge("Latest ${displayRunStatus(run)}", runStatusColor(run))
                    if (run.branch.isNotBlank()) MiniActionsBadge(run.branch, Blue)
                    if (run.event.isNotBlank()) MiniActionsBadge(run.event, Color(0xFFBF5AF2))
                    val elapsed = calcRunDuration(run, nowMs)
                    if (elapsed.isNotBlank()) MiniActionsBadge(elapsed, TextSecondary)
                }
            }
        }

        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun StatCard(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color) {
    Column(
        Modifier
            .width(96.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceWhite)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
            Text(label, fontSize = 11.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(value, fontSize = 18.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RepositoryArtifactsPanel(repo: GHRepo) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var artifacts by remember { mutableStateOf<List<GHArtifact>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var page by remember { mutableStateOf(1) }
    var hasMore by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var busyArtifact by remember { mutableStateOf<Long?>(null) }

    suspend fun load(reset: Boolean = true) {
        loading = true
        val nextPage = if (reset) 1 else page + 1
        val fetched = GitHubManager.getRepositoryArtifacts(context, repo.owner, repo.name, nextPage, query)
        page = nextPage
        hasMore = fetched.size >= 100
        artifacts = if (reset) fetched else (artifacts + fetched).distinctBy { it.id }
        loading = false
    }

    LaunchedEffect(repo.owner, repo.name) { load(true) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            ActionsPanelHeader(
                title = "Repository artifacts",
                subtitle = "All workflow artifacts across runs, with download and delete actions.",
                loading = loading,
                onRefresh = { scope.launch { load(true) } }
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Artifact name filter") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { scope.launch { load(true) } }, enabled = !loading) { Text("Apply", color = Blue) }
            }
        }
        if (artifacts.isEmpty() && !loading) {
            item { EmptyActionsText("No artifacts found") }
        }
        items(artifacts) { artifact ->
            ArtifactRow(
                artifact = artifact,
                busy = busyArtifact == artifact.id,
                onDownload = {
                    busyArtifact = artifact.id
                    scope.launch {
                        val dest = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "GlassFiles_Git/${safeArtifactZipName(artifact)}")
                        val ok = GitHubManager.downloadArtifact(context, repo.owner, repo.name, artifact.id, dest)
                        Toast.makeText(context, if (ok) "${Strings.done}: ${dest.name}" else Strings.error, Toast.LENGTH_SHORT).show()
                        busyArtifact = null
                    }
                },
                onDelete = {
                    busyArtifact = artifact.id
                    scope.launch {
                        val ok = GitHubManager.deleteArtifact(context, repo.owner, repo.name, artifact.id)
                        Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                        busyArtifact = null
                        if (ok) load(true)
                    }
                }
            )
        }
        if (hasMore) {
            item {
                TextButton(onClick = { scope.launch { load(false) } }, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
                    Text("Load more artifacts", color = Blue)
                }
            }
        }
    }
}

@Composable
private fun ActionsCachesPanel(repo: GHRepo) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var usage by remember { mutableStateOf<GHActionsCacheUsage?>(null) }
    var caches by remember { mutableStateOf<List<GHActionsCacheEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Long?>(null) }

    suspend fun load() {
        loading = true
        usage = GitHubManager.getActionsCacheUsage(context, repo.owner, repo.name)
        caches = GitHubManager.getActionsCaches(context, repo.owner, repo.name)
        loading = false
    }

    LaunchedEffect(repo.owner, repo.name) { load() }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            ActionsPanelHeader("Actions caches", "Repository cache usage and cache entries.", loading) { scope.launch { load() } }
            usage?.let {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard("Caches", it.activeCachesCount.toString(), Icons.Rounded.Timeline, Blue)
                    StatCard("Size", formatArtifactSize(it.activeCachesSizeInBytes), Icons.Rounded.Article, Color(0xFF34C759))
                }
            }
        }
        if (caches.isEmpty() && !loading) item { EmptyActionsText("No caches found") }
        items(caches) { cache ->
            ActionInfoCard(
                title = cache.key,
                subtitle = "${formatArtifactSize(cache.sizeInBytes)} • ${cache.ref}",
                meta = listOf("Created ${cache.createdAt.take(10)}", "Last used ${cache.lastAccessedAt.take(10)}", cache.version.take(12)),
                actionLabel = if (deleting == cache.id) "Deleting" else "Delete",
                actionTint = Color(0xFFFF3B30),
                onAction = {
                    deleting = cache.id
                    scope.launch {
                        val ok = GitHubManager.deleteActionsCache(context, repo.owner, repo.name, cache.id)
                        Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                        deleting = null
                        if (ok) load()
                    }
                }
            )
        }
    }
}

@Composable
private fun ActionsVariablesPanel(repo: GHRepo) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var variables by remember { mutableStateOf<List<GHActionVariable>>(emptyList()) }
    var name by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    suspend fun load() {
        loading = true
        variables = GitHubManager.getRepoActionsVariables(context, repo.owner, repo.name)
        loading = false
    }

    LaunchedEffect(repo.owner, repo.name) { load() }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            ActionsPanelHeader("Actions variables", "Create, update and delete repository variables.", loading) { scope.launch { load() } }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceWhite).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value, { value = it }, label = { Text("Value") }, modifier = Modifier.fillMaxWidth(), minLines = 1, maxLines = 3)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = {
                        if (name.isBlank()) return@TextButton
                        scope.launch {
                            val existing = variables.any { it.name == name }
                            val ok = if (existing) GitHubManager.updateRepoActionsVariable(context, repo.owner, repo.name, name, value)
                            else GitHubManager.createRepoActionsVariable(context, repo.owner, repo.name, name, value)
                            Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                            if (ok) { name = ""; value = ""; load() }
                        }
                    }) { Text("Save variable", color = Blue) }
                }
            }
        }
        if (variables.isEmpty() && !loading) item { EmptyActionsText("No variables found") }
        items(variables) { variable ->
            ActionInfoCard(
                title = variable.name,
                subtitle = variable.value,
                meta = listOf("Updated ${variable.updatedAt.take(10)}"),
                actionLabel = "Delete",
                actionTint = Color(0xFFFF3B30),
                onAction = {
                    scope.launch {
                        val ok = GitHubManager.deleteRepoActionsVariable(context, repo.owner, repo.name, variable.name)
                        Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                        if (ok) load()
                    }
                }
            )
        }
    }
}

@Composable
private fun ActionsSecretsPanel(repo: GHRepo) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var secrets by remember { mutableStateOf<List<GHActionSecret>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    suspend fun load() {
        loading = true
        secrets = GitHubManager.getRepoActionsSecrets(context, repo.owner, repo.name)
        loading = false
    }

    LaunchedEffect(repo.owner, repo.name) { load() }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            ActionsPanelHeader("Actions secrets", "Create, update and delete repository secrets.", loading || saving) { scope.launch { load() } }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceWhite).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.trim() },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Secret name") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Secret value") },
                    visualTransformation = PasswordVisualTransformation()
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "GitHub never returns stored secret values. Saving an existing name updates it.",
                        fontSize = 11.sp,
                        color = TextTertiary,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(enabled = !saving && name.isNotBlank() && value.isNotBlank(), onClick = {
                        scope.launch {
                            saving = true
                            val ok = GitHubManager.createOrUpdateRepoActionsSecret(context, repo.owner, repo.name, name, value)
                            saving = false
                            Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                            if (ok) { name = ""; value = ""; load() }
                        }
                    }) { Text("Save secret", color = Blue) }
                }
            }
        }
        if (secrets.isEmpty() && !loading) item { EmptyActionsText("No secrets found") }
        items(secrets) { secret ->
            ActionInfoCard(
                title = secret.name,
                subtitle = "Secret value is never returned by GitHub",
                meta = listOf("Updated ${secret.updatedAt.take(10)}"),
                actionLabel = "Delete",
                actionTint = Color(0xFFFF3B30),
                onAction = {
                    scope.launch {
                        val ok = GitHubManager.deleteRepoActionsSecret(context, repo.owner, repo.name, secret.name)
                        Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                        if (ok) load()
                    }
                }
            )
        }
    }
}

@Composable
private fun ActionsRunnersPanel(repo: GHRepo) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var runners by remember { mutableStateOf<List<GHActionRunner>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var runnerToken by remember { mutableStateOf("") }

    suspend fun load() {
        loading = true
        runners = GitHubManager.getRepoSelfHostedRunners(context, repo.owner, repo.name)
        loading = false
    }

    LaunchedEffect(repo.owner, repo.name) { load() }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            ActionsPanelHeader("Self-hosted runners", "Repository self-hosted runner status, labels and registration tokens.", loading) { scope.launch { load() } }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceWhite).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(Icons.Rounded.PlayArrow, "Registration token") {
                        scope.launch {
                            val token = GitHubManager.createRepoRunnerRegistrationToken(context, repo.owner, repo.name)
                            runnerToken = token?.let { "registration: ${it.token}\nexpires: ${it.expiresAt}" }.orEmpty()
                            Toast.makeText(context, if (token != null) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                        }
                    }
                    Chip(Icons.Rounded.Delete, "Remove token", Color(0xFFFF9500)) {
                        scope.launch {
                            val token = GitHubManager.createRepoRunnerRemoveToken(context, repo.owner, repo.name)
                            runnerToken = token?.let { "remove: ${it.token}\nexpires: ${it.expiresAt}" }.orEmpty()
                            Toast.makeText(context, if (token != null) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                if (runnerToken.isNotBlank()) {
                    Text(runnerToken, fontSize = 10.sp, color = TextSecondary, fontFamily = FontFamily.Monospace)
                }
            }
        }
        if (runners.isEmpty() && !loading) item { EmptyActionsText("No self-hosted runners found") }
        items(runners) { runner ->
            ActionInfoCard(
                title = runner.name,
                subtitle = "${runner.os} • ${runner.status}${if (runner.busy) " • busy" else ""}",
                meta = runner.labels,
                actionLabel = "Delete",
                actionTint = Color(0xFFFF3B30),
                onAction = {
                    scope.launch {
                        val ok = GitHubManager.deleteRepoSelfHostedRunner(context, repo.owner, repo.name, runner.id)
                        Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                        if (ok) load()
                    }
                }
            )
        }
    }
}

@Composable
private fun ActionsSettingsPanel(repo: GHRepo) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var permissions by remember { mutableStateOf<GHActionsPermissions?>(null) }
    var workflowPermissions by remember { mutableStateOf<GHWorkflowPermissions?>(null) }
    var retention by remember { mutableStateOf<GHActionsRetention?>(null) }
    var loading by remember { mutableStateOf(false) }

    suspend fun load() {
        loading = true
        permissions = GitHubManager.getRepoActionsPermissions(context, repo.owner, repo.name)
        workflowPermissions = GitHubManager.getRepoActionsWorkflowPermissions(context, repo.owner, repo.name)
        retention = GitHubManager.getRepoActionsRetention(context, repo.owner, repo.name)
        loading = false
    }

    LaunchedEffect(repo.owner, repo.name) { load() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ActionsPanelHeader("Actions settings", "Repository Actions permissions, workflow token policy and retention.", loading) { scope.launch { load() } }
        ActionInfoCard(
            title = "Actions permissions",
            subtitle = if (permissions?.enabled == true) "Enabled" else "Disabled or unavailable",
            meta = listOf("Allowed actions: ${permissions?.allowedActions.orEmpty().ifBlank { "unknown" }}"),
            actionLabel = null,
            onAction = {}
        )
        ActionInfoCard(
            title = "Workflow permissions",
            subtitle = workflowPermissions?.defaultWorkflowPermissions.orEmpty().ifBlank { "Unavailable" },
            meta = listOf("Approve PR reviews: ${workflowPermissions?.canApprovePullRequestReviews ?: false}"),
            actionLabel = null,
            onAction = {}
        )
        ActionInfoCard(
            title = "Artifact and log retention",
            subtitle = retention?.days?.takeIf { it > 0 }?.let { "$it days" } ?: "Unavailable",
            meta = emptyList(),
            actionLabel = null,
            onAction = {}
        )
    }
}

@Composable
private fun ActionsPanelHeader(title: String, subtitle: String, loading: Boolean, onRefresh: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceWhite).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(Icons.Rounded.Timeline, null, tint = Blue, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(subtitle, fontSize = 12.sp, color = TextSecondary, lineHeight = 16.sp)
        }
        IconButton(onClick = onRefresh) {
            if (loading) CircularProgressIndicator(Modifier.size(16.dp), color = Blue, strokeWidth = 2.dp)
            else Icon(Icons.Rounded.Refresh, null, tint = Blue)
        }
    }
}

@Composable
private fun ArtifactRow(artifact: GHArtifact, busy: Boolean, onDownload: () -> Unit, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceWhite).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(Icons.Rounded.Article, null, tint = if (artifact.expired) TextTertiary else Blue, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(artifact.name, fontSize = 14.sp, color = if (artifact.expired) TextTertiary else TextPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                artifactKindBadges(artifact).forEach { (label, color) -> MiniActionsBadge(label, color) }
                MiniActionsBadge(formatArtifactSize(artifact.sizeInBytes), TextSecondary)
                if (artifact.expired) MiniActionsBadge("expired", Color(0xFFFF3B30))
                if (artifact.workflowRunId > 0) MiniActionsBadge("#${artifact.workflowRunId}", Blue)
                if (artifact.workflowRunBranch.isNotBlank()) MiniActionsBadge(artifact.workflowRunBranch, Blue)
                if (artifact.workflowRunSha.length >= 7) MiniActionsBadge(artifact.workflowRunSha.take(7), TextSecondary)
            }
            Text("Created ${artifact.createdAt.take(10)} • Expires ${artifact.expiresAt.take(10)}", fontSize = 11.sp, color = TextTertiary)
            if (artifact.digest.isNotBlank()) Text(artifact.digest, fontSize = 10.sp, color = TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (busy) CircularProgressIndicator(Modifier.size(16.dp), color = Blue, strokeWidth = 2.dp)
        else {
            IconButton(onClick = onDownload, enabled = !artifact.expired) { Icon(Icons.Rounded.Article, null, tint = if (artifact.expired) TextTertiary else Blue) }
            IconButton(onClick = onDelete) { Icon(Icons.Rounded.Delete, null, tint = Color(0xFFFF3B30)) }
        }
    }
}

@Composable
private fun ArtifactRunRow(
    artifact: GHArtifact,
    downloading: Boolean,
    deleting: Boolean,
    onCopyName: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(SurfaceWhite).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            Modifier.weight(1f).clickable(enabled = !artifact.expired && !downloading, onClick = onDownload),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Rounded.Article, null, Modifier.size(20.dp), tint = if (artifact.expired) TextTertiary else Blue)
            Column(Modifier.weight(1f)) {
                Text(artifact.name, fontSize = 14.sp, color = if (artifact.expired) TextTertiary else TextPrimary, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    artifactKindBadges(artifact).forEach { (label, color) -> MiniActionsBadge(label, color) }
                    MiniActionsBadge(formatArtifactSize(artifact.sizeInBytes), TextSecondary)
                    if (artifact.sizeInBytes <= 0L) MiniActionsBadge("suspect", Color(0xFFFF9500))
                    if (artifact.expired) MiniActionsBadge(Strings.ghExpired, Color(0xFFFF3B30))
                    else MiniActionsBadge(artifact.createdAt.take(10), TextTertiary)
                }
            }
        }
        if (!artifact.expired) {
            IconButton(onClick = onCopyName) {
                Icon(Icons.Rounded.ContentCopy, null, tint = TextSecondary)
            }
            IconButton(onClick = onDelete) {
                if (deleting) CircularProgressIndicator(Modifier.size(16.dp), color = Color(0xFFFF3B30), strokeWidth = 2.dp)
                else Icon(Icons.Rounded.Delete, null, tint = Color(0xFFFF3B30))
            }
        }
        if (downloading) CircularProgressIndicator(Modifier.size(18.dp), color = Blue, strokeWidth = 2.dp)
    }
}

@Composable
private fun ActionInfoCard(
    title: String,
    subtitle: String,
    meta: List<String>,
    actionLabel: String?,
    actionTint: Color = Blue,
    onAction: () -> Unit
) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceWhite).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(Icons.Rounded.Article, null, tint = Blue, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) Text(subtitle, fontSize = 12.sp, color = TextSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (meta.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    meta.filter { it.isNotBlank() }.forEach { MiniActionsBadge(it, TextSecondary) }
                }
            }
        }
        if (actionLabel != null) {
            TextButton(onClick = onAction) { Text(actionLabel, color = actionTint, fontSize = 12.sp) }
        }
    }
}

@Composable
private fun EmptyActionsText(text: String) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceWhite).padding(18.dp), contentAlignment = Alignment.Center) {
        Text(text, fontSize = 13.sp, color = TextTertiary)
    }
}

@Composable
private fun LoadingActionsText(text: String) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceWhite).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(Modifier.size(16.dp), color = Blue, strokeWidth = 2.dp)
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 13.sp, color = TextTertiary)
    }
}

@Composable
private fun DynamicDispatchInputs(
    schema: GHWorkflowDispatchSchema?,
    values: Map<String, String>,
    missingRequiredInputs: List<String>,
    onValueChange: (String, String) -> Unit
) {
    val inputs = schema?.inputs.orEmpty()
    if (schema == null) {
        Text("This workflow has no workflow_dispatch trigger", fontSize = 11.sp, color = TextTertiary)
        return
    }
    if (inputs.isEmpty()) {
        Text("This workflow has no workflow_dispatch inputs", fontSize = 11.sp, color = TextTertiary)
        return
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (missingRequiredInputs.isNotEmpty()) {
            Text(
                "Required inputs missing: ${missingRequiredInputs.joinToString(", ")}",
                fontSize = 11.sp,
                color = Color(0xFFFF9500)
            )
        }
        inputs.forEach { input ->
            WorkflowDispatchInputField(
                input = input,
                value = values[input.key].orEmpty(),
                onValueChange = { onValueChange(input.key, it) }
            )
        }
    }
}

@Composable
private fun WorkflowDispatchInputField(
    input: GHWorkflowDispatchInput,
    value: String,
    onValueChange: (String) -> Unit
) {
    val choices = dispatchInputChoices(input)
    val missingRequired = input.required && dispatchInputValue(input, mapOf(input.key to value)).isBlank()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(input.key, fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold)
            if (input.required) MiniActionsBadge("required", Color(0xFFFF9500))
            if (input.type.isNotBlank()) MiniActionsBadge(input.type, TextSecondary)
        }
        if (input.description.isNotBlank()) {
            Text(input.description, fontSize = 10.sp, color = TextTertiary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (choices.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                choices.forEach { option ->
                    ActionsFilterChip(option, value == option) { onValueChange(option) }
                }
            }
        } else {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text(input.key) },
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
                modifier = Modifier.fillMaxWidth(),
                singleLine = input.type.lowercase() != "environment"
            )
        }
        if (missingRequired) {
            Text("Required value", fontSize = 10.sp, color = Color(0xFFFF9500))
        }
    }
}

private fun dispatchInputChoices(input: GHWorkflowDispatchInput): List<String> = when {
    input.options.isNotEmpty() -> input.options
    input.type.equals("boolean", ignoreCase = true) -> listOf("true", "false")
    else -> emptyList()
}

private fun missingDispatchInputs(schema: GHWorkflowDispatchSchema?, values: Map<String, String>): List<String> =
    schema?.inputs.orEmpty()
        .filter { it.required && dispatchInputValue(it, values).isBlank() }
        .map { it.key }

private fun dispatchInputValue(input: GHWorkflowDispatchInput, values: Map<String, String>): String =
    values[input.key].orEmpty().ifBlank { input.defaultValue }.trim()

private fun loadSavedDispatchInputValues(
    context: Context,
    repo: GHRepo,
    workflow: GHWorkflow,
    schema: GHWorkflowDispatchSchema
): Map<String, String> {
    val prefs = context.getSharedPreferences(ACTIONS_INPUT_PREFS, Context.MODE_PRIVATE)
    return schema.inputs.associate { input ->
        input.key to (prefs.getString(dispatchInputPrefKey(repo, workflow, input.key), null) ?: input.defaultValue)
    }
}

private fun saveDispatchInputValues(
    context: Context,
    repo: GHRepo,
    workflow: GHWorkflow,
    values: Map<String, String>
) {
    val editor = context.getSharedPreferences(ACTIONS_INPUT_PREFS, Context.MODE_PRIVATE).edit()
    values.forEach { (key, value) ->
        editor.putString(dispatchInputPrefKey(repo, workflow, key), value)
    }
    editor.apply()
}

private fun dispatchInputPrefKey(repo: GHRepo, workflow: GHWorkflow, inputKey: String): String =
    "${repo.owner}/${repo.name}/${workflow.id}/$inputKey"

@Composable
private fun ModernRunCard(
    run: GHWorkflowRun,
    nowMs: Long,
    onRunClick: () -> Unit,
    onCancel: () -> Unit,
    onRerun: () -> Unit
) {
    val statusColor = runStatusColor(run)
    val live = isRunActive(run)
    val elapsed = calcRunDuration(run, nowMs)

    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceWhite)
            .clickable(onClick = onRunClick)
            .padding(14.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
            if (run.actorAvatar.isNotBlank()) {
                AsyncImage(
                    model = run.actorAvatar,
                    contentDescription = run.actor,
                    modifier = Modifier.size(36.dp).clip(CircleShape)
                )
            } else {
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(statusColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(runStatusIcon(run), null, tint = statusColor, modifier = Modifier.size(18.dp))
                }
            }

            Column(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(run.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (live) MiniActionsBadge("LIVE", statusColor)
                }
                if (run.displayTitle.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(run.displayTitle, fontSize = 12.sp, color = TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(4.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    buildKindBadges("${run.name} ${run.displayTitle}").forEach { (label, color) ->
                        MiniActionsBadge(label, color)
                    }
                    MiniActionsBadge("#${run.runNumber}", TextSecondary)
                    if (run.runAttempt > 1) MiniActionsBadge("attempt ${run.runAttempt}", Color(0xFFFF9500))
                    MiniActionsBadge(run.branch.ifBlank { "unknown" }, Blue)
                    MiniActionsBadge(run.event.ifBlank { "event" }, Color(0xFFBF5AF2))
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    buildRunSummary(run, elapsed),
                    fontSize = 12.sp,
                    color = TextSecondary,
                    lineHeight = 17.sp
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MiniActionsBadge(displayRunStatus(run), statusColor)
            if (elapsed.isNotBlank()) MiniActionsBadge(elapsed, if (live) Blue else TextSecondary)
            if (run.actor.isNotBlank()) MiniActionsBadge(run.actor, TextSecondary)
            if (run.headSha.length >= 7) MiniActionsBadge(run.headSha.take(7), TextSecondary)
        }

        Spacer(Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (live) {
                Chip(Icons.Rounded.Cancel, Strings.cancel, Color(0xFFFF3B30)) { onCancel() }
            } else {
                Chip(Icons.Rounded.Refresh, Strings.ghRerun) { onRerun() }
            }
            Chip(Icons.Rounded.Article, "Open") { onRunClick() }
        }
    }
}

private fun githubStatusFilter(filter: ActionsRunFilter): String? = when (filter) {
    ActionsRunFilter.ALL -> null
    ActionsRunFilter.ACTIVE -> null
    ActionsRunFilter.QUEUED -> "queued"
    ActionsRunFilter.FAILED -> "failure"
    ActionsRunFilter.SUCCESS -> "success"
    ActionsRunFilter.CANCELLED -> "cancelled"
    ActionsRunFilter.SKIPPED -> "skipped"
}

private fun isRunActive(run: GHWorkflowRun): Boolean =
    run.status in setOf("queued", "pending", "waiting", "requested", "in_progress")

private fun isJobActive(job: GHJob): Boolean =
    job.status in setOf("queued", "pending", "waiting", "requested", "in_progress")

private fun displayRunStatus(run: GHWorkflowRun): String {
    return when {
        run.status == "queued" -> "queued"
        run.status == "pending" -> "pending"
        run.status == "waiting" -> "waiting"
        run.status == "requested" -> "queued"
        run.status == "in_progress" -> "running"
        run.conclusion == "success" -> "success"
        run.conclusion == "failure" -> "failed"
        run.conclusion == "cancelled" -> "cancelled"
        run.conclusion == "skipped" -> "skipped"
        run.conclusion == "neutral" -> "neutral"
        run.conclusion == "timed_out" -> "timed out"
        run.status == "completed" -> "completed"
        else -> run.status.ifBlank { "unknown" }
    }
}

private fun buildRunSummary(run: GHWorkflowRun, elapsed: String): String {
    val parts = mutableListOf<String>()
    if (run.actor.isNotBlank()) parts += "by ${run.actor}"
    if (run.updatedAt.isNotBlank()) parts += run.updatedAt.take(19).replace('T', ' ')
    if (elapsed.isNotBlank()) parts += elapsed
    return parts.joinToString(" • ")
}

private fun runStatusColor(run: GHWorkflowRun): Color = when {
    run.status in setOf("queued", "pending", "waiting", "requested") -> Color(0xFFFF9500)
    run.status == "in_progress" -> Blue
    run.conclusion == "success" -> Color(0xFF34C759)
    run.conclusion == "failure" -> Color(0xFFFF3B30)
    run.conclusion == "cancelled" -> Color(0xFF8E8E93)
    run.conclusion == "skipped" || run.conclusion == "neutral" -> Color(0xFF8E8E93)
    run.conclusion == "timed_out" -> Color(0xFFFF9500)
    else -> TextTertiary
}

private fun runStatusIcon(run: GHWorkflowRun) = when {
    run.status in setOf("queued", "pending", "waiting", "requested") -> Icons.Rounded.Schedule
    run.status == "in_progress" -> Icons.Rounded.Refresh
    run.conclusion == "success" -> Icons.Rounded.CheckCircle
    run.conclusion == "failure" -> Icons.Rounded.Error
    run.conclusion == "cancelled" -> Icons.Rounded.Cancel
    run.conclusion == "skipped" || run.conclusion == "neutral" -> Icons.Rounded.Warning
    run.conclusion == "timed_out" -> Icons.Rounded.Schedule
    else -> Icons.Rounded.Warning
}

@Composable
private fun MiniActionsBadge(text: String, color: Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(text, fontSize = 11.sp, color = color, fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun WorkflowRunDetailScreen(repo: GHRepo, runId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val jobListState = rememberLazyListState()
    var run by remember { mutableStateOf<GHWorkflowRun?>(null) }
    var jobs by remember { mutableStateOf<List<GHJob>>(emptyList()) }
    var artifacts by remember { mutableStateOf<List<GHArtifact>>(emptyList()) }
    var checkRuns by remember { mutableStateOf<List<GHCheckRun>>(emptyList()) }
    var pendingDeployments by remember { mutableStateOf<List<GHPendingDeployment>>(emptyList()) }
    var reviewHistory by remember { mutableStateOf<List<GHWorkflowRunReview>>(emptyList()) }
    var usage by remember { mutableStateOf<GHActionsUsage?>(null) }
    var selectedAttempt by remember { mutableStateOf<Int?>(null) }
    var maxAttempt by remember { mutableStateOf(1) }
    var loading by remember { mutableStateOf(true) }
    var downloading by remember { mutableStateOf<Long?>(null) }
    var deletingArtifactId by remember { mutableStateOf<Long?>(null) }
    val jobLogs = remember { mutableStateMapOf<Long, String>() }
    val jobStepLogs = remember { mutableStateMapOf<Long, Map<Int, String>>() }
    val checkAnnotations = remember { mutableStateMapOf<Long, List<GHCheckAnnotation>>() }
    var loadingJobId by remember { mutableStateOf<Long?>(null) }
    var expandedJobId by remember { mutableStateOf<Long?>(null) }
    var expandedStepKey by remember { mutableStateOf<String?>(null) }
    val expandedMatrixGroups = remember(runId) { mutableStateMapOf<String, Boolean>() }
    var onlyFailedJobs by remember { mutableStateOf(false) }
    var onlyActiveJobs by remember { mutableStateOf(false) }
    var loadedLogsFilter by remember { mutableStateOf(TextFieldValue("")) }
    var selectedSection by remember { mutableStateOf(RunDetailSection.JOBS) }
    var refreshing by remember { mutableStateOf(false) }
    var metadataLoaded by remember { mutableStateOf(false) }
    var artifactsLoaded by remember { mutableStateOf(false) }
    var checksLoaded by remember { mutableStateOf(false) }
    var loadingMetadata by remember { mutableStateOf(false) }
    var loadingArtifacts by remember { mutableStateOf(false) }
    var loadingChecks by remember { mutableStateOf(false) }
    var downloadingAllArtifacts by remember { mutableStateOf(false) }
    var showPublishRelease by remember { mutableStateOf(false) }
    var detailNotice by remember { mutableStateOf<String?>(null) }
    var kernelErrorCatalog by remember { mutableStateOf<KernelErrorCatalog?>(null) }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    suspend fun loadMetadata(force: Boolean = false) {
        if (metadataLoaded && !force) return
        loadingMetadata = true
        try {
            pendingDeployments = GitHubManager.getPendingDeployments(context, repo.owner, repo.name, runId)
            reviewHistory = GitHubManager.getWorkflowRunReviewHistory(context, repo.owner, repo.name, runId)
            usage = GitHubManager.getWorkflowRunUsage(context, repo.owner, repo.name, runId)
            metadataLoaded = true
        } finally {
            loadingMetadata = false
        }
    }

    suspend fun loadArtifacts(force: Boolean = false) {
        if (artifactsLoaded && !force) return
        loadingArtifacts = true
        try {
            artifacts = GitHubManager.getRunArtifacts(context, repo.owner, repo.name, runId)
            artifactsLoaded = true
        } finally {
            loadingArtifacts = false
        }
    }

    suspend fun loadChecks(force: Boolean = false) {
        if (checksLoaded && !force) return
        loadingChecks = true
        try {
            val currentRun = run ?: GitHubManager.getWorkflowRun(context, repo.owner, repo.name, runId)
            checkRuns = currentRun?.headSha?.takeIf { it.isNotBlank() }?.let {
                GitHubManager.getCheckRunsForRef(context, repo.owner, repo.name, it)
            } ?: emptyList()
            checksLoaded = true
        } finally {
            loadingChecks = false
        }
    }

    suspend fun refreshAll(refreshSection: Boolean = true) {
        refreshing = true
        try {
            val latestRun = GitHubManager.getWorkflowRun(context, repo.owner, repo.name, runId)
            maxAttempt = latestRun?.runAttempt?.coerceAtLeast(1) ?: 1
            val attempt = selectedAttempt
            run = if (attempt != null) GitHubManager.getWorkflowRunAttempt(context, repo.owner, repo.name, runId, attempt) ?: latestRun else latestRun
            jobs = if (attempt != null) GitHubManager.getWorkflowRunAttemptJobs(context, repo.owner, repo.name, runId, attempt)
                else GitHubManager.getWorkflowRunJobs(context, repo.owner, repo.name, runId)
            if (refreshSection) {
                when (selectedSection) {
                    RunDetailSection.SUMMARY -> loadMetadata(force = true)
                    RunDetailSection.ARTIFACTS -> loadArtifacts(force = true)
                    RunDetailSection.CHECKS -> loadChecks(force = true)
                    RunDetailSection.JOBS -> {}
                }
            }
            detailNotice = null
        } catch (e: Exception) {
            detailNotice = actionsFriendlyError(e.message)
        }
        refreshing = false
        loading = false
    }

    LaunchedEffect(runId) { refreshAll(refreshSection = false) }

    LaunchedEffect(Unit) {
        kernelErrorCatalog = KernelErrorPatterns.load(context)
    }

    LaunchedEffect(selectedSection, loading, run?.headSha) {
        if (!loading) {
            when (selectedSection) {
                RunDetailSection.SUMMARY -> loadMetadata()
                RunDetailSection.ARTIFACTS -> loadArtifacts()
                RunDetailSection.CHECKS -> loadChecks()
                RunDetailSection.JOBS -> {}
            }
        }
    }

    LaunchedEffect(jobs, expandedJobId, expandedStepKey) {
        while (true) {
            val hasLive = jobs.any { isJobActive(it) }
            nowMs = System.currentTimeMillis()
            if (hasLive) {
                delay(if (detailNotice != null) ACTIONS_BACKOFF_DELAY_MS else 1000L)
                if (nowMs % 3000L < 1100L) {
                    refreshAll(refreshSection = false)
                    val expandedLiveJob = jobs.firstOrNull {
                        (it.id == expandedJobId || expandedStepKey?.startsWith("${it.id}:") == true) &&
                            isJobActive(it)
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
            val activeOk = !onlyActiveJobs || isJobActive(job)
            val q = loadedLogsFilter.text.trim()
            val searchOk = q.isBlank() || job.name.contains(q, true) || (jobLogs[job.id]?.contains(q, true) == true)
            failedOk && activeOk && searchOk
        }
    }
    val firstFailedJob = remember(jobs) { jobs.firstOrNull { it.conclusion == "failure" } }
    val firstFailedStep = remember(firstFailedJob) { firstFailedJob?.steps?.firstOrNull { isFailedStep(it) } }
    val firstFailedLog = firstFailedJob?.let { jobLogs[it.id] }.orEmpty()
    val failureDiagnostics = remember(firstFailedJob?.id, firstFailedStep?.number, firstFailedLog, kernelErrorCatalog) {
        buildFailureDiagnostics(context, firstFailedJob, firstFailedStep, firstFailedLog, kernelErrorCatalog)
    }
    val patternInfo = kernelErrorCatalog?.let { context.getString(R.string.actions_kernel_patterns_info, it.version, it.source.label) }
    val groupedJobItems = remember(filteredJobs, jobs.size, expandedMatrixGroups.toMap(), nowMs) {
        buildJobListItems(
            jobs = filteredJobs,
            totalJobCount = jobs.size,
            expandedGroups = expandedMatrixGroups
        )
    }
    val failedJobItemIndexes = remember(groupedJobItems) {
        groupedJobItems.mapIndexedNotNull { index, item ->
            when (item) {
                is JobListItem.JobRow -> index.takeIf { item.job.conclusion == "failure" }
                is JobListItem.GroupHeader -> index.takeIf { item.group.jobs.any { job -> job.conclusion == "failure" } && !item.expanded }
            }
        }
    }

    Column(Modifier.fillMaxSize().background(SurfaceLight)) {
        GHTopBar(run?.let { "${it.name} #${it.runNumber}" } ?: "Run #$runId", onBack = onBack) {
            IconButton(onClick = { scope.launch { refreshAll() } }) {
                if (refreshing) CircularProgressIndicator(Modifier.size(18.dp), color = Blue, strokeWidth = 2.dp)
                else Icon(Icons.Rounded.Refresh, null, tint = Blue)
            }
            run?.htmlUrl?.takeIf { it.isNotBlank() }?.let { url ->
                IconButton(onClick = { openExternalUrl(context, url) }) {
                    Icon(Icons.Rounded.Article, null, tint = Blue)
                }
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
                    val ok = GitHubManager.rerunFailedJobs(context, repo.owner, repo.name, runId)
                    Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                    refreshAll()
                }
            }) { Icon(Icons.Rounded.Error, null, tint = Color(0xFFFF9500)) }
            IconButton(onClick = {
                scope.launch {
                    val ok = GitHubManager.cancelWorkflowRun(context, repo.owner, repo.name, runId)
                    Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                    refreshAll()
                }
            }) { Icon(Icons.Rounded.Cancel, null, tint = Color(0xFFFF3B30)) }
            IconButton(onClick = {
                scope.launch {
                    val ok = GitHubManager.forceCancelWorkflowRun(context, repo.owner, repo.name, runId)
                    Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                    refreshAll()
                }
            }) { Icon(Icons.Rounded.Warning, null, tint = Color(0xFFFF3B30)) }
        }

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Blue, modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
            }
        } else {
            val successCount = jobs.count { it.conclusion == "success" }
            val failedCount = jobs.count { it.conclusion == "failure" }
            val runningCount = jobs.count { isJobActive(it) }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActionsFilterChip("Jobs", selectedSection == RunDetailSection.JOBS) { selectedSection = RunDetailSection.JOBS }
                ActionsFilterChip("Summary", selectedSection == RunDetailSection.SUMMARY) { selectedSection = RunDetailSection.SUMMARY }
                ActionsFilterChip(
                    if (artifactsLoaded) "Artifacts ${artifacts.size}" else "Artifacts",
                    selectedSection == RunDetailSection.ARTIFACTS
                ) { selectedSection = RunDetailSection.ARTIFACTS }
                ActionsFilterChip(
                    if (checksLoaded) "Checks ${checkRuns.size}" else "Checks",
                    selectedSection == RunDetailSection.CHECKS
                ) { selectedSection = RunDetailSection.CHECKS }
            }

            detailNotice?.let { notice ->
                Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFFF9500).copy(alpha = 0.1f)).padding(10.dp)) {
                    Text(notice, fontSize = 12.sp, color = TextSecondary)
                }
            }

            if (selectedSection == RunDetailSection.JOBS) {
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
                        failed.steps.firstOrNull { isFailedStep(it) }?.let { step ->
                            expandedStepKey = "${failed.id}:${step.number}"
                        }
                        ensureJobLogsLoaded(scope, context, repo, failed, jobLogs, jobStepLogs, force = true) { loadingJobId = it }
                    }
                }
                ActionsFilterChip("Running logs", false) {
                    val running = jobs.firstOrNull { isJobActive(it) }
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
            }

            Box(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize(), state = jobListState, contentPadding = PaddingValues(12.dp)) {
                run?.let { currentRun ->
                    item {
                        WorkflowRunDetailHeader(currentRun, nowMs)
                        Spacer(Modifier.height(10.dp))
                    }
                }
                if (maxAttempt > 1) {
                    item {
                        AttemptSelector(
                            maxAttempt = maxAttempt,
                            selectedAttempt = selectedAttempt ?: maxAttempt,
                            onSelect = {
                                selectedAttempt = it
                                scope.launch { refreshAll() }
                            }
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
                firstFailedJob?.let { failedJob ->
                    item {
                        FailureDiagnosisCard(
                            job = failedJob,
                            step = firstFailedStep,
                            diagnostics = failureDiagnostics,
                            patternInfo = patternInfo,
                            logLoaded = jobLogs[failedJob.id] != null,
                            loading = loadingJobId == failedJob.id,
                            onCopySummary = {
                                val summary = failureSummaryText(failedJob, firstFailedStep, failureDiagnostics)
                                val clip = android.content.ClipData.newPlainText("failure-summary", summary)
                                (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(clip)
                                Toast.makeText(context, Strings.done, Toast.LENGTH_SHORT).show()
                            },
                            onShareSummary = run?.let { currentRun ->
                                {
                                    shareFailureSummary(
                                        context = context,
                                        repo = repo,
                                        run = currentRun,
                                        job = failedJob,
                                        step = firstFailedStep,
                                        diagnostics = failureDiagnostics
                                    )
                                }
                            },
                            onOpenFailedLog = {
                                expandedJobId = failedJob.id
                                firstFailedStep?.let { step -> expandedStepKey = "${failedJob.id}:${step.number}" }
                                ensureJobLogsLoaded(scope, context, repo, failedJob, jobLogs, jobStepLogs, force = true) { loadingJobId = it }
                            }
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
                if (selectedSection == RunDetailSection.SUMMARY) {
                    if (loadingMetadata) {
                        item { LoadingActionsText("Loading run metadata...") }
                    }
                usage?.let { runUsage ->
                    item {
                        WorkflowUsageCard(runUsage)
                        Spacer(Modifier.height(8.dp))
                    }
                }
                item {
                    RunDangerActionsCard(
                        onDeleteLogs = {
                            scope.launch {
                                val ok = GitHubManager.deleteWorkflowRunLogs(context, repo.owner, repo.name, runId)
                                Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                                refreshAll()
                            }
                        },
                        onDeleteRun = {
                            scope.launch {
                                val ok = GitHubManager.deleteWorkflowRun(context, repo.owner, repo.name, runId)
                                Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                                if (ok) onBack() else refreshAll()
                            }
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                }
                if (pendingDeployments.isNotEmpty()) {
                    item {
                        PendingDeploymentsCard(
                            deployments = pendingDeployments,
                            onReview = { deployment, approve ->
                                scope.launch {
                                    val ok = GitHubManager.reviewPendingDeployments(
                                        context = context,
                                        owner = repo.owner,
                                        repo = repo.name,
                                        runId = runId,
                                        environmentIds = listOf(deployment.environmentId),
                                        approve = approve,
                                        comment = if (approve) "Approved from GlassFiles" else "Rejected from GlassFiles"
                                    )
                                    Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                                    refreshAll()
                                }
                            }
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
                if (reviewHistory.isNotEmpty()) {
                    item {
                        ReviewHistoryCard(reviewHistory)
                        Spacer(Modifier.height(8.dp))
                    }
                }
                }

                if (selectedSection == RunDetailSection.CHECKS) {
                    if (loadingChecks) {
                        item { LoadingActionsText("Loading checks...") }
                    } else if (checkRuns.isEmpty()) {
                        item { EmptyActionsText("No checks found") }
                    } else {
                    item {
                        CheckRunsCard(
                            checkRuns = checkRuns.filter { checkRun ->
                                checkRun.id != 0L && (
                                    checkRun.name.isNotBlank() ||
                                        checkRun.title.isNotBlank() ||
                                        checkRun.summary.isNotBlank() ||
                                        checkRun.annotationsCount > 0
                                    )
                            },
                            annotations = checkAnnotations,
                            onLoadAnnotations = { checkRun ->
                                scope.launch {
                                    checkAnnotations[checkRun.id] = GitHubManager.getCheckRunAnnotations(context, repo.owner, repo.name, checkRun.id)
                                        .filter { annotation ->
                                            annotation.message.isNotBlank() ||
                                                annotation.title.isNotBlank() ||
                                                annotation.path.isNotBlank()
                                        }
                                }
                            }
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    }
                }

                if (selectedSection == RunDetailSection.JOBS) {
                    items(groupedJobItems, key = { item ->
                        when (item) {
                            is JobListItem.GroupHeader -> "group:${item.group.name}"
                            is JobListItem.JobRow -> "job:${item.job.id}"
                        }
                    }) { item ->
                        when (item) {
                            is JobListItem.GroupHeader -> {
                                MatrixJobGroupHeader(
                                    group = item.group,
                                    expanded = item.expanded,
                                    nowMs = nowMs,
                                    onToggle = { expandedMatrixGroups[item.group.name] = !item.expanded }
                                )
                            }
                            is JobListItem.JobRow -> {
                                WorkflowJobCard(
                                    job = item.job,
                                    nowMs = nowMs,
                                    repo = repo,
                                    context = context,
                                    scope = scope,
                                    jobLogs = jobLogs,
                                    jobStepLogs = jobStepLogs,
                                    loadingJobId = loadingJobId,
                                    expandedJobId = expandedJobId,
                                    expandedStepKey = expandedStepKey,
                                    onExpandedJobChange = { expandedJobId = it },
                                    onExpandedStepChange = { expandedStepKey = it },
                                    setLoadingJobId = { loadingJobId = it },
                                    onRefreshRun = { scope.launch { refreshAll() } }
                                )
                            }
                        }
                    }
                }

                if (selectedSection == RunDetailSection.ARTIFACTS) {
                    if (loadingArtifacts) {
                        item { LoadingActionsText("Loading artifacts...") }
                    } else if (artifacts.isEmpty()) {
                        item { EmptyActionsText("No artifacts found") }
                    } else {
                        item {
                            Spacer(Modifier.height(8.dp))
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(Strings.ghArtifacts, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                TextButton(
                                    enabled = !downloadingAllArtifacts && artifacts.any { !it.expired },
                                    onClick = {
                                        downloadingAllArtifacts = true
                                        scope.launch {
                                            var count = 0
                                            artifacts.filter { !it.expired }.forEach { artifact ->
                                                val dest = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "GlassFiles_Git/${safeArtifactZipName(artifact)}")
                                                if (GitHubManager.downloadArtifact(context, repo.owner, repo.name, artifact.id, dest)) count++
                                            }
                                            downloadingAllArtifacts = false
                                            Toast.makeText(context, "${Strings.done}: $count/${artifacts.count { !it.expired }}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                ) {
                                    if (downloadingAllArtifacts) CircularProgressIndicator(Modifier.size(14.dp), color = Blue, strokeWidth = 2.dp)
                                    else Text("Download all", color = Blue, fontSize = 12.sp)
                                }
                                TextButton(
                                    enabled = artifacts.any { !it.expired },
                                    onClick = { showPublishRelease = true }
                                ) {
                                    Text("Publish release", color = Color(0xFF34C759), fontSize = 12.sp)
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                        groupedArtifacts(artifacts).forEach { group ->
                            item {
                                Row(
                                    Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    MiniActionsBadge(group.label, group.color)
                                    Text("${group.items.size} artifacts", fontSize = 11.sp, color = TextTertiary)
                                }
                            }
                            items(group.items) { artifact ->
                                ArtifactRunRow(
                                    artifact = artifact,
                                    downloading = downloading == artifact.id,
                                    deleting = deletingArtifactId == artifact.id,
                                    onCopyName = {
                                        val clip = android.content.ClipData.newPlainText("artifact-name", artifact.name)
                                        (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(clip)
                                        Toast.makeText(context, Strings.done, Toast.LENGTH_SHORT).show()
                                    },
                                    onDownload = {
                                        downloading = artifact.id
                                        scope.launch {
                                            val dest = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "GlassFiles_Git/${safeArtifactZipName(artifact)}")
                                            val ok = GitHubManager.downloadArtifact(context, repo.owner, repo.name, artifact.id, dest)
                                            Toast.makeText(context, if (ok) "${Strings.done}: ${dest.name}" else Strings.error, Toast.LENGTH_SHORT).show()
                                            downloading = null
                                        }
                                    },
                                    onDelete = {
                                        deletingArtifactId = artifact.id
                                        scope.launch {
                                            val ok = GitHubManager.deleteArtifact(context, repo.owner, repo.name, artifact.id)
                                            Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                                            deletingArtifactId = null
                                            refreshAll()
                                        }
                                    }
                                )
                                Spacer(Modifier.height(6.dp))
                            }
                        }
                    }
                }
            }
            if (selectedSection == RunDetailSection.JOBS && failedJobItemIndexes.isNotEmpty()) {
                FloatingActionButton(
                    onClick = {
                        val currentIndex = jobListState.firstVisibleItemIndex
                        val targetIndex = failedJobItemIndexes.firstOrNull { it > currentIndex } ?: failedJobItemIndexes.first()
                        (groupedJobItems.getOrNull(targetIndex) as? JobListItem.GroupHeader)?.let { header ->
                            if (!header.expanded) expandedMatrixGroups[header.group.name] = true
                        }
                        scope.launch { jobListState.animateScrollToItem(targetIndex) }
                    },
                    containerColor = Color(0xFFFF3B30),
                    contentColor = Color.White,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(18.dp).size(48.dp)
                ) {
                    Icon(Icons.Rounded.Error, null, Modifier.size(22.dp))
                }
            }
            }
        }
    }

    if (showPublishRelease && run != null) {
        PublishArtifactsReleaseDialog(
            repo = repo,
            run = run!!,
            artifacts = artifacts,
            onDismiss = { showPublishRelease = false },
            onPublished = {
                showPublishRelease = false
                selectedSection = RunDetailSection.SUMMARY
            }
        )
    }
}

@Composable
private fun WorkflowRunDetailHeader(run: GHWorkflowRun, nowMs: Long) {
    val statusColor = runStatusColor(run)
    val elapsed = calcRunDuration(run, nowMs)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SurfaceWhite).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(runStatusIcon(run), null, tint = statusColor, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(run.displayTitle.ifBlank { run.name }, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    buildRunSummary(run, elapsed),
                    fontSize = 12.sp,
                    color = TextSecondary,
                    lineHeight = 17.sp
                )
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniActionsBadge(displayRunStatus(run), statusColor)
            MiniActionsBadge("#${run.runNumber}", TextSecondary)
            if (run.runAttempt > 1) MiniActionsBadge("attempt ${run.runAttempt}", Color(0xFFFF9500))
            if (run.branch.isNotBlank()) MiniActionsBadge(run.branch, Blue)
            if (run.event.isNotBlank()) MiniActionsBadge(run.event, Color(0xFFBF5AF2))
            if (run.headSha.length >= 7) MiniActionsBadge(run.headSha.take(7), TextSecondary)
            if (run.headRepository.isNotBlank()) MiniActionsBadge(run.headRepository, TextSecondary)
        }
    }
}

@Composable
private fun FailureDiagnosisCard(
    job: GHJob,
    step: GHStep?,
    diagnostics: List<String>,
    patternInfo: String?,
    logLoaded: Boolean,
    loading: Boolean,
    onCopySummary: () -> Unit,
    onShareSummary: (() -> Unit)?,
    onOpenFailedLog: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFFF3B30).copy(alpha = 0.08f)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.Error, null, tint = Color(0xFFFF3B30), modifier = Modifier.size(18.dp))
            Column(Modifier.weight(1f)) {
                Text("Failed build", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(job.name, fontSize = 12.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            TextButton(onClick = onOpenFailedLog, enabled = !loading) {
                if (loading) CircularProgressIndicator(Modifier.size(14.dp), color = Color(0xFFFF3B30), strokeWidth = 2.dp)
                else Text("Open failed log", color = Color(0xFFFF3B30), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        step?.let {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MiniActionsBadge("step ${it.number}", Color(0xFFFF3B30))
                MiniActionsBadge(it.name, TextSecondary)
            }
        }
        if (!logLoaded) {
            Text("Load the failed log to see likely causes.", fontSize = 11.sp, color = TextTertiary)
        } else if (diagnostics.isEmpty()) {
            Text("No known pattern detected. Check the failed step output.", fontSize = 11.sp, color = TextTertiary)
        } else {
            diagnostics.take(3).forEach { message ->
                Text(message, fontSize = 11.sp, color = TextSecondary, lineHeight = 15.sp)
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(Icons.Rounded.ContentCopy, stringResource(R.string.actions_copy_failure_summary), Color(0xFFFF3B30), onCopySummary)
            if (onShareSummary != null) {
                Chip(Icons.Rounded.Share, stringResource(R.string.actions_share), Color(0xFFFF3B30), onShareSummary)
            }
            Chip(Icons.Rounded.Article, "Open failed log", Color(0xFFFF3B30), onOpenFailedLog)
        }
        if (!patternInfo.isNullOrBlank()) {
            Text(patternInfo, fontSize = 10.sp, color = TextTertiary)
        }
    }
}

@Composable
private fun PublishArtifactsReleaseDialog(
    repo: GHRepo,
    run: GHWorkflowRun,
    artifacts: List<GHArtifact>,
    onDismiss: () -> Unit,
    onPublished: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val publishableArtifacts = remember(artifacts) { artifacts.filter { !it.expired } }
    var tag by remember(run.id) { mutableStateOf(defaultReleaseTag(run)) }
    var name by remember(run.id) { mutableStateOf(defaultReleaseName(run)) }
    var body by remember(run.id) { mutableStateOf(defaultReleaseBody(run, publishableArtifacts)) }
    var draft by remember { mutableStateOf(true) }
    var prerelease by remember { mutableStateOf(false) }
    var publishing by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!publishing) onDismiss() },
        title = { Text("Publish artifacts") },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${publishableArtifacts.size} artifacts from run #${run.runNumber}", fontSize = 12.sp, color = TextSecondary)
                OutlinedTextField(tag, { tag = it.trim() }, label = { Text("Tag") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(name, { name = it }, label = { Text("Release title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(body, { body = it }, label = { Text("Release notes") }, minLines = 5, maxLines = 8, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(draft, { draft = it })
                    Text("Draft", fontSize = 13.sp, color = TextPrimary)
                    Spacer(Modifier.width(12.dp))
                    Checkbox(prerelease, { prerelease = it })
                    Text("Pre-release", fontSize = 13.sp, color = TextPrimary)
                }
                if (progress.isNotBlank()) Text(progress, fontSize = 11.sp, color = TextTertiary)
            }
        },
        confirmButton = {
            TextButton(
                enabled = !publishing && tag.isNotBlank() && publishableArtifacts.isNotEmpty(),
                onClick = {
                    publishing = true
                    progress = "Creating release..."
                    scope.launch {
                        val release = GitHubManager.createReleaseDetailed(
                            context = context,
                            owner = repo.owner,
                            repo = repo.name,
                            tag = tag,
                            name = name.ifBlank { tag },
                            body = body,
                            prerelease = prerelease,
                            draft = draft,
                            targetCommitish = run.branch
                        ) ?: GitHubManager.getReleaseByTag(context, repo.owner, repo.name, tag)

                        if (release == null || release.id == 0L) {
                            publishing = false
                            progress = ""
                            Toast.makeText(context, "Failed to create release", Toast.LENGTH_SHORT).show()
                            return@launch
                        }

                        val dir = File(context.cacheDir, "release-artifacts/${run.id}").apply { mkdirs() }
                        var uploaded = 0
                        publishableArtifacts.forEachIndexed { index, artifact ->
                            progress = "Uploading ${index + 1}/${publishableArtifacts.size}: ${artifact.name}"
                            val file = File(dir, safeArtifactZipName(artifact))
                            val downloaded = GitHubManager.downloadArtifact(context, repo.owner, repo.name, artifact.id, file)
                            if (downloaded && GitHubManager.uploadReleaseAssetDetailed(context, repo.owner, repo.name, release.id, file) != null) {
                                uploaded++
                            }
                        }
                        publishing = false
                        Toast.makeText(context, "Uploaded $uploaded/${publishableArtifacts.size}", Toast.LENGTH_SHORT).show()
                        onPublished()
                    }
                }
            ) {
                if (publishing) CircularProgressIndicator(Modifier.size(16.dp), color = Blue, strokeWidth = 2.dp)
                else Text("Publish", color = Color(0xFF34C759))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !publishing) { Text(Strings.cancel) }
        }
    )
}

private fun defaultReleaseTag(run: GHWorkflowRun): String {
    val base = run.name.ifBlank { "build" }
        .replace(Regex("""[^A-Za-z0-9._-]+"""), "-")
        .trim('-')
        .lowercase()
        .ifBlank { "build" }
    return "$base-${run.runNumber}"
}

private fun defaultReleaseName(run: GHWorkflowRun): String =
    "${run.name.ifBlank { "Build" }} #${run.runNumber}"

private fun defaultReleaseBody(run: GHWorkflowRun, artifacts: List<GHArtifact>): String {
    val lines = mutableListOf<String>()
    lines += "Published from GitHub Actions run #${run.runNumber}."
    if (run.branch.isNotBlank()) lines += "Branch: ${run.branch}"
    if (run.headSha.length >= 7) lines += "Commit: ${run.headSha.take(7)}"
    if (artifacts.isNotEmpty()) {
        lines += ""
        lines += "Artifacts:"
        artifacts.forEach { artifact ->
            lines += "- ${artifact.name} (${formatArtifactSize(artifact.sizeInBytes)})"
        }
    }
    return lines.joinToString("\n")
}

@Composable
private fun WorkflowUsageCard(usage: GHActionsUsage) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceWhite).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.Timeline, null, tint = Blue, modifier = Modifier.size(18.dp))
            Text("Usage", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniActionsBadge("duration ${formatDuration(usage.runDurationMs)}", Blue)
            usage.billableMs.forEach { (os, ms) ->
                MiniActionsBadge("$os ${formatDuration(ms)}", TextSecondary)
            }
        }
    }
}

@Composable
private fun AttemptSelector(maxAttempt: Int, selectedAttempt: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceWhite).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Attempts", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..maxAttempt).forEach { attempt ->
                ActionsFilterChip("Attempt $attempt", selectedAttempt == attempt) { onSelect(attempt) }
            }
        }
    }
}

@Composable
private fun RunDangerActionsCard(onDeleteLogs: () -> Unit, onDeleteRun: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceWhite).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Run management", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(Icons.Rounded.Delete, "Delete logs", Color(0xFFFF9500)) { onDeleteLogs() }
            Chip(Icons.Rounded.Delete, "Delete run", Color(0xFFFF3B30)) { onDeleteRun() }
        }
    }
}

@Composable
private fun PendingDeploymentsCard(
    deployments: List<GHPendingDeployment>,
    onReview: (GHPendingDeployment, Boolean) -> Unit
) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceWhite).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.Warning, null, tint = Color(0xFFFF9500), modifier = Modifier.size(18.dp))
            Text("Pending deployments", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        }
        deployments.forEach { deployment ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(deployment.environmentName.ifBlank { "Environment ${deployment.environmentId}" }, fontSize = 13.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                    val reviewers = deployment.reviewers.joinToString(", ").ifBlank { "No reviewers listed" }
                    Text(reviewers, fontSize = 11.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (deployment.currentUserCanApprove) {
                    TextButton(onClick = { onReview(deployment, true) }) { Text("Approve", color = Color(0xFF34C759), fontSize = 12.sp) }
                    TextButton(onClick = { onReview(deployment, false) }) { Text("Reject", color = Color(0xFFFF3B30), fontSize = 12.sp) }
                } else {
                    MiniActionsBadge("waiting", Color(0xFFFF9500))
                }
            }
        }
    }
}

@Composable
private fun ReviewHistoryCard(reviews: List<GHWorkflowRunReview>) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceWhite).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.CheckCircle, null, tint = Blue, modifier = Modifier.size(18.dp))
            Text("Deployment review history", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        }
        reviews.forEach { review ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(SurfaceLight).padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    MiniActionsBadge(review.state.ifBlank { "reviewed" }, if (review.state == "approved") Color(0xFF34C759) else Color(0xFFFF9500))
                    Text(review.user.ifBlank { "GitHub" }, fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                }
                if (review.environments.isNotEmpty()) Text(review.environments.joinToString(", "), fontSize = 11.sp, color = TextSecondary)
                if (review.comment.isNotBlank()) Text(review.comment, fontSize = 11.sp, color = TextSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun CheckRunsCard(
    checkRuns: List<GHCheckRun>,
    annotations: Map<Long, List<GHCheckAnnotation>>,
    onLoadAnnotations: (GHCheckRun) -> Unit
) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceWhite).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.CheckCircle, null, tint = Blue, modifier = Modifier.size(18.dp))
            Text("Checks and annotations", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        }
        checkRuns.forEach { checkRun ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(SurfaceLight).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val checkName = cleanGithubText(checkRun.name)
                    val checkTitle = cleanGithubText(checkRun.title)
                    MiniActionsBadge(displayCheckStatus(checkRun), checkStatusColor(checkRun))
                    Text(checkName.ifBlank { checkTitle.ifBlank { "Check run" } }, fontSize = 13.sp, color = TextPrimary, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (checkRun.annotationsCount > 0 && annotations[checkRun.id] == null) {
                        TextButton(onClick = { onLoadAnnotations(checkRun) }) { Text("Annotations ${checkRun.annotationsCount}", color = Blue, fontSize = 11.sp) }
                    }
                }
                cleanGithubText(checkRun.title).takeIf { it.isNotBlank() }?.let { title ->
                    Text(title, fontSize = 12.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                annotations[checkRun.id].orEmpty().filter { annotation ->
                    cleanGithubText(annotation.message).isNotBlank() ||
                        cleanGithubText(annotation.title).isNotBlank() ||
                        cleanGithubText(annotation.path).isNotBlank()
                }.take(10).forEach { annotation ->
                    Column(Modifier.fillMaxWidth().padding(start = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        val location = buildAnnotationLocation(annotation)
                        if (location.isNotBlank()) Text(location, fontSize = 11.sp, color = TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val body = cleanGithubText(annotation.message).ifBlank { cleanGithubText(annotation.title) }
                        if (body.isNotBlank()) Text(body, fontSize = 11.sp, color = if (annotation.annotationLevel == "failure") Color(0xFFFF3B30) else TextSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

private fun buildAnnotationLocation(annotation: GHCheckAnnotation): String {
    val path = cleanGithubText(annotation.path).ifBlank { return "" }
    return if (annotation.startLine > 0) "$path:${annotation.startLine}" else path
}

@Composable
private fun MatrixJobGroupHeader(
    group: MatrixJobGroup,
    expanded: Boolean,
    nowMs: Long,
    onToggle: () -> Unit
) {
    val status = aggregateJobStatus(group.jobs)
    val color = jobStatusColor(status)
    Column(
        Modifier.fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceWhite)
            .clickable(onClick = onToggle)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(jobStatusIcon(status), null, Modifier.size(18.dp), tint = color)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(group.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${group.jobs.size} jobs · ${matrixGroupDuration(group.jobs, nowMs)}", fontSize = 11.sp, color = TextTertiary)
            }
            MiniActionsBadge(status, color)
            Icon(
                if (expanded) Icons.Rounded.FilterList else Icons.Rounded.Article,
                null,
                Modifier.size(16.dp),
                tint = TextSecondary
            )
        }
    }
}

@Composable
private fun WorkflowJobCard(
    job: GHJob,
    nowMs: Long,
    repo: GHRepo,
    context: Context,
    scope: CoroutineScope,
    jobLogs: MutableMap<Long, String>,
    jobStepLogs: MutableMap<Long, Map<Int, String>>,
    loadingJobId: Long?,
    expandedJobId: Long?,
    expandedStepKey: String?,
    onExpandedJobChange: (Long?) -> Unit,
    onExpandedStepChange: (String?) -> Unit,
    setLoadingJobId: (Long?) -> Unit,
    onRefreshRun: () -> Unit
) {
    val status = displayJobStatus(job)
    val jColor = jobStatusColor(status)
    val jobElapsed = calcJobDuration(job, nowMs)
    Column(
        Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(12.dp)).background(SurfaceWhite).padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(jobStatusIcon(status), null, Modifier.size(18.dp), tint = jColor)
            Text(job.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(jobElapsed, fontSize = 10.sp, color = if (isJobActive(job)) Blue else TextTertiary)
        }

        Spacer(Modifier.height(6.dp))
        job.steps.forEach { step ->
            val sColor = stepStatusColor(step)
            val stepKey = "${job.id}:${step.number}"
            val stepLog = jobStepLogs[job.id]?.get(step.number)
            Column(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().clickable {
                        onExpandedStepChange(if (expandedStepKey == stepKey) null else stepKey)
                        if (jobLogs[job.id] == null) {
                            ensureJobLogsLoaded(scope, context, repo, job, jobLogs, jobStepLogs, force = isJobActive(job), setLoading = setLoadingJobId)
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
                        else -> "No log output for this step."
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            Chip(
                if (expandedJobId == job.id) Icons.Rounded.FilterList else Icons.Rounded.Article,
                if (expandedJobId == job.id) "Hide full log" else "Show full log"
            ) {
                if (expandedJobId == job.id) {
                    onExpandedJobChange(null)
                } else {
                    onExpandedJobChange(job.id)
                    ensureJobLogsLoaded(scope, context, repo, job, jobLogs, jobStepLogs, force = isJobActive(job), setLoading = setLoadingJobId)
                }
            }
            if (jobLogs[job.id] != null) {
                Chip(Icons.Rounded.ContentCopy, "Copy full log") {
                    val clip = android.content.ClipData.newPlainText("logs", jobLogs[job.id])
                    (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(clip)
                    Toast.makeText(context, Strings.done, Toast.LENGTH_SHORT).show()
                }
                Chip(Icons.Rounded.Article, "Save log") {
                    val dest = File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                        "GlassFiles_Git/${safeLogFileName(job)}.log"
                    )
                    val ok = saveTextFile(dest, jobLogs[job.id].orEmpty())
                    Toast.makeText(context, if (ok) "${Strings.done}: ${dest.name}" else Strings.error, Toast.LENGTH_SHORT).show()
                }
            }
            Chip(Icons.Rounded.Refresh, "Rerun job") {
                scope.launch {
                    val ok = GitHubManager.rerunJob(context, repo.owner, repo.name, job.id)
                    Toast.makeText(context, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                    onRefreshRun()
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

private fun displayCheckStatus(checkRun: GHCheckRun): String {
    val status = cleanGithubText(checkRun.status)
    val conclusion = cleanGithubText(checkRun.conclusion)
    return when {
        status == "in_progress" -> "running"
        status == "queued" -> "queued"
        conclusion.isNotBlank() -> conclusion
        else -> status.ifBlank { "unknown" }
    }
}

private fun buildJobListItems(
    jobs: List<GHJob>,
    totalJobCount: Int,
    expandedGroups: MutableMap<String, Boolean>
): List<JobListItem> {
    if (jobs.size <= 10) return jobs.map { JobListItem.JobRow(it) }
    val defaultExpanded = totalJobCount <= 20
    val groups = jobs.groupBy { matrixJobGroupName(it.name) }
    return groups.flatMap { entry ->
        val groupName = entry.key
        val groupJobs = entry.value
        val shouldGroup = groupJobs.size > 1 || groupJobs.any { it.name.contains(" / ") }
        if (!shouldGroup) {
            groupJobs.map<JobListItem> { JobListItem.JobRow(it) }
        } else {
            val expanded = expandedGroups.getOrPut(groupName) { defaultExpanded }
            buildList<JobListItem> {
                add(JobListItem.GroupHeader(MatrixJobGroup(groupName, groupJobs), expanded))
                if (expanded) addAll(groupJobs.map { JobListItem.JobRow(it) })
            }
        }
    }
}

private fun matrixJobGroupName(jobName: String): String =
    jobName.substringBefore(" / ").trim().ifBlank { jobName }

private fun aggregateJobStatus(jobs: List<GHJob>): String = when {
    jobs.any { it.conclusion == "failure" || it.conclusion == "timed_out" || it.conclusion == "action_required" } -> "failed"
    jobs.any { isJobActive(it) } -> "running"
    jobs.any { it.status in setOf("queued", "pending", "waiting", "requested") } -> "queued"
    jobs.any { it.conclusion == "cancelled" } -> "cancelled"
    jobs.isNotEmpty() && jobs.all { it.conclusion == "success" } -> "success"
    jobs.isNotEmpty() && jobs.all { it.conclusion == "skipped" } -> "skipped"
    else -> "unknown"
}

private fun displayJobStatus(job: GHJob): String = when {
    isJobActive(job) -> "running"
    job.status in setOf("queued", "pending", "waiting", "requested") -> "queued"
    job.conclusion == "success" -> "success"
    job.conclusion == "failure" -> "failed"
    job.conclusion == "cancelled" -> "cancelled"
    job.conclusion == "skipped" -> "skipped"
    job.conclusion.isNotBlank() && job.conclusion != "null" -> job.conclusion
    job.status.isNotBlank() && job.status != "null" -> job.status
    else -> "unknown"
}

private fun jobStatusIcon(status: String) = when (status) {
    "queued", "pending", "waiting", "requested" -> Icons.Rounded.Schedule
    "running", "in_progress" -> Icons.Rounded.Refresh
    "success" -> Icons.Rounded.CheckCircle
    "failed", "failure", "timed_out", "action_required" -> Icons.Rounded.Error
    "cancelled", "skipped" -> Icons.Rounded.Cancel
    else -> Icons.Rounded.Warning
}

private fun jobStatusColor(status: String): Color = when (status) {
    "running", "in_progress" -> Blue
    "success" -> Color(0xFF34C759)
    "failed", "failure", "timed_out", "action_required" -> Color(0xFFFF3B30)
    "cancelled", "skipped" -> Color(0xFF8E8E93)
    else -> Color(0xFFFF9500)
}

private fun matrixGroupDuration(jobs: List<GHJob>, nowMs: Long): String {
    val durations = jobs.mapNotNull { job ->
        val start = parseIsoMs(job.startedAt) ?: return@mapNotNull null
        val end = if (job.status == "completed") parseIsoMs(job.completedAt) ?: nowMs else nowMs
        (end - start).coerceAtLeast(0L)
    }
    return if (durations.isEmpty()) "" else formatDuration(durations.maxOrNull() ?: 0L)
}

private fun cleanGithubText(value: String): String =
    value.trim().takeUnless { it.equals("null", ignoreCase = true) }.orEmpty()

private fun checkStatusColor(checkRun: GHCheckRun): Color = when (displayCheckStatus(checkRun)) {
    "success" -> Color(0xFF34C759)
    "failure", "timed_out", "action_required" -> Color(0xFFFF3B30)
    "running", "in_progress" -> Blue
    "queued" -> Color(0xFFFF9500)
    else -> TextSecondary
}

private fun ensureJobLogsLoaded(
    scope: CoroutineScope,
    context: Context,
    repo: GHRepo,
    job: GHJob,
    jobLogs: MutableMap<Long, String>,
    jobStepLogs: MutableMap<Long, Map<Int, String>>,
    force: Boolean = false,
    setLoading: (Long?) -> Unit
) {
    if (!force && jobLogs.containsKey(job.id)) return
    scope.launch {
        setLoading(job.id)
        try {
            val log = GitHubManager.getJobLogs(context, repo.owner, repo.name, job.id)
            when {
                !log.startsWith("Error: ") -> {
                    jobLogs[job.id] = log
                    jobStepLogs[job.id] = splitLogsBySteps(job, log)
                }
                isTemporaryLiveLogUnavailable(job, log) -> {
                    jobLogs[job.id] = liveLogPlaceholder(job)
                    jobStepLogs[job.id] = emptyMap()
                }
                else -> {
                    jobLogs[job.id] = log
                    jobStepLogs[job.id] = emptyMap()
                }
            }
        } finally {
            setLoading(null)
        }
    }
}

private suspend fun refreshJobLogsNow(
    context: Context,
    repo: GHRepo,
    job: GHJob,
    jobLogs: MutableMap<Long, String>,
    jobStepLogs: MutableMap<Long, Map<Int, String>>
) {
    val log = GitHubManager.getJobLogs(context, repo.owner, repo.name, job.id)
    when {
        !log.startsWith("Error: ") -> {
            jobLogs[job.id] = log
            jobStepLogs[job.id] = splitLogsBySteps(job, log)
        }
        isTemporaryLiveLogUnavailable(job, log) -> {
            jobLogs[job.id] = liveLogPlaceholder(job)
            jobStepLogs[job.id] = emptyMap()
        }
        else -> {
            jobLogs[job.id] = log
            jobStepLogs[job.id] = emptyMap()
        }
    }
}

private fun liveLogPlaceholder(job: GHJob): String = when (job.status) {
    "queued", "pending", "waiting", "requested" -> "Log is not available yet. The job has not started writing output."
    "in_progress" -> "Waiting for live log output. GitHub may return logs after the current step publishes output."
    else -> "No log output was captured for this job."
}

private fun isTemporaryLiveLogUnavailable(job: GHJob, log: String): Boolean {
    if (!isJobActive(job)) return false
    val normalized = log.trim().lowercase()
    return normalized == "error: http 404" ||
        "no step log captured" in normalized ||
        "not found" in normalized
}

private fun isFailedStep(step: GHStep): Boolean =
    displayStepStatus(step) in setOf("failed", "timed out", "startup failure", "action required")

private fun buildFailureDiagnostics(
    context: Context,
    job: GHJob?,
    step: GHStep?,
    log: String,
    catalog: KernelErrorCatalog?
): List<String> {
    if (job == null) return emptyList()
    val messages = linkedSetOf<String>()
    messages += KernelErrorPatterns.diagnose(context, catalog, log)

    if (messages.isEmpty() && step != null) {
        messages += "Failed step: ${step.name}. Open the log and inspect the last error block."
    }
    return messages.toList()
}

private fun failureSummaryText(job: GHJob, step: GHStep?, diagnostics: List<String>): String {
    val lines = mutableListOf("Failed job: ${job.name}")
    if (step != null) lines += "Failed step: ${step.name} (#${step.number})"
    diagnostics.takeIf { it.isNotEmpty() }?.let { items ->
        lines += "Likely causes:"
        lines += items.map { "- $it" }
    }
    return lines.joinToString("\n")
}

private fun shareFailureSummary(
    context: Context,
    repo: GHRepo,
    run: GHWorkflowRun,
    job: GHJob,
    step: GHStep?,
    diagnostics: List<String>
) {
    val summary = failureSummaryText(job, step, diagnostics)
    val stepName = step?.name ?: "unknown"
    val stepNumber = step?.number ?: 0
    val branch = run.branch.ifBlank { "unknown" }
    val body = context.getString(
        R.string.actions_share_failure_body,
        run.name.ifBlank { "Workflow" },
        run.runNumber,
        repo.owner,
        repo.name,
        branch,
        stepName,
        stepNumber,
        job.name,
        summary,
        run.htmlUrl.ifBlank { "n/a" }
    )
    val subject = context.getString(R.string.actions_share_failure_subject, run.name.ifBlank { "Workflow" }, run.runNumber)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
    }
    try {
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.actions_share_failure_summary)))
    } catch (_: Exception) {
        Toast.makeText(context, Strings.error, Toast.LENGTH_SHORT).show()
    }
}

private fun openExternalUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: Exception) {
        Toast.makeText(context, "Cannot open link", Toast.LENGTH_SHORT).show()
    }
}

private fun actionsFriendlyError(message: String?): String {
    val raw = message.orEmpty()
    val lower = raw.lowercase()
    return when {
        "403" in lower || "forbidden" in lower -> "GitHub API permission or rate limit issue. Polling slowed down."
        "401" in lower || "unauthorized" in lower -> "GitHub token is missing or expired."
        "404" in lower || "not found" in lower -> "GitHub resource is not available yet or token has no access."
        "422" in lower || "validation failed" in lower -> "GitHub rejected the request. Check workflow inputs and ref."
        "timeout" in lower || "failed to connect" in lower -> "Network issue. Polling slowed down."
        raw.isNotBlank() -> raw.take(180)
        else -> "GitHub request failed. Polling slowed down."
    }
}

private fun safeLogFileName(job: GHJob): String =
    "job-${job.id}-${job.name.replace(Regex("""[\\/:*?"<>|]+"""), "_").trim().ifBlank { "log" }}"

private fun saveTextFile(file: File, text: String): Boolean = try {
    file.parentFile?.mkdirs()
    file.writeText(text)
    true
} catch (_: Exception) {
    false
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
            result[step.number] = currentSection
            sectionIndex++
        }
    }

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
        s in setOf("queued", "pending", "waiting", "requested") -> s
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
        "skipped", "neutral" -> Color(0xFF8E8E93)
        "running" -> Blue
        "queued", "pending", "waiting", "requested" -> Color(0xFFFF9500)
        "completed" -> Color(0xFF8E8E93)
        else -> Color(0xFFFF9500)
    }
}

private fun formatArtifactSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val kb = 1024.0
    val mb = kb * 1024.0
    return when {
        bytes >= mb -> String.format(Locale.US, "%.1f MB", bytes / mb)
        bytes >= kb -> String.format(Locale.US, "%.1f KB", bytes / kb)
        else -> "$bytes B"
    }
}

private fun safeArtifactZipName(artifact: GHArtifact): String {
    val safeName = artifact.name
        .replace(Regex("""[\\/:*?"<>|]+"""), "_")
        .trim()
        .ifBlank { "artifact-${artifact.id}" }
    return "$safeName.zip"
}

private fun artifactKindBadges(artifact: GHArtifact): List<Pair<String, Color>> =
    buildKindBadges(artifact.name)

private fun groupedArtifacts(artifacts: List<GHArtifact>): List<ArtifactGroup> =
    artifacts.groupBy { artifactKindGroup(it) }
        .map { (meta, items) -> meta.copy(items = items.sortedBy { it.name.lowercase() }) }
        .sortedWith(compareBy<ArtifactGroup> { it.order }.thenBy { it.label })

private fun artifactKindGroup(artifact: GHArtifact): ArtifactGroup {
    val name = artifact.name.lowercase()
    return when {
        kernelPatterns.any { it in name } -> ArtifactGroup("Kernel / IMG", Color(0xFF34C759), 0, emptyList())
        magiskPatterns.any { it in name } -> ArtifactGroup("Magisk / KSU", Color(0xFFFF9500), 1, emptyList())
        driverPatterns.any { it in name } -> ArtifactGroup("Turnip / Adreno", Color(0xFFBF5AF2), 2, emptyList())
        androidPatterns.any { it in name } -> ArtifactGroup("Android app", Blue, 3, emptyList())
        iosPatterns.any { it in name } -> ArtifactGroup("iOS app", Color(0xFF5AC8FA), 4, emptyList())
        windowsPatterns.any { it in name } -> ArtifactGroup("Windows", Color(0xFF0078D4), 5, emptyList())
        linuxPatterns.any { it in name } -> ArtifactGroup("Linux", Color(0xFF8E8E93), 6, emptyList())
        else -> ArtifactGroup("Other", TextSecondary, 99, emptyList())
    }
}

private fun buildKindBadges(text: String): List<Pair<String, Color>> {
    val name = text.lowercase()
    val labels = mutableListOf<Pair<String, Color>>()
    if (kernelPatterns.any { it in name }) {
        labels += "Kernel" to Color(0xFF34C759)
    }
    if (magiskPatterns.any { it in name }) {
        labels += "Magisk" to Color(0xFFFF9500)
    }
    if (driverPatterns.any { it in name }) {
        labels += "Driver" to Color(0xFFBF5AF2)
    }
    if (androidPatterns.any { it in name }) {
        labels += "Android" to Blue
    }
    if (iosPatterns.any { it in name }) {
        labels += "iOS" to Color(0xFF5AC8FA)
    }
    if (windowsPatterns.any { it in name }) {
        labels += "Windows" to Color(0xFF0078D4)
    }
    if (linuxPatterns.any { it in name }) {
        labels += "Linux" to Color(0xFF8E8E93)
    }
    return labels
}

private val kernelPatterns = listOf(
    "kernel", "anykernel", "anykernel3", "boot", "boot.img", "vendor_boot", "vendor_boot.img",
    "dtbo", "dtbo.img", "dtb", "image.gz", "image.gz-dtb", "ak3", "zimage"
)
private val magiskPatterns = listOf("magisk", "magisk-module", "magisk_module", "ksu", "kernelsu", "apatch")
private val driverPatterns = listOf("driver", "turnip", "adreno", "freedreno", "vulkan", "mesa", "kgsl")
private val androidPatterns = listOf("apk", "aab", "android")
private val iosPatterns = listOf("ipa", "ios", "xcarchive")
private val windowsPatterns = listOf("exe", "msi", "windows", "win64", "win32")
private val linuxPatterns = listOf("appimage", ".deb", ".rpm", "linux")

private val ISO_FMT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}
