package com.glassfiles.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glassfiles.data.github.GHActionsPermissions
import com.glassfiles.data.github.GHApiErrorLogEntry
import com.glassfiles.data.github.GHJob
import com.glassfiles.data.github.GHRepo
import com.glassfiles.data.github.GHWorkflow
import com.glassfiles.data.github.GHWorkflowPermissions
import com.glassfiles.data.github.GHWorkflowRun
import com.glassfiles.data.github.GitHubManager
import com.glassfiles.data.github.canWrite
import com.glassfiles.ui.components.AiModuleSpinner
import com.glassfiles.ui.components.AiModuleText as Text
import com.glassfiles.ui.theme.AiModuleTheme
import com.glassfiles.ui.theme.JetBrainsMono
import kotlinx.coroutines.launch
import java.util.Locale

private data class ActionsTroubleFinding(
    val level: String,
    val title: String,
    val detail: String,
)

@Composable
internal fun GitHubActionsTroubleshootScreen(
    repo: GHRepo,
    onBack: () -> Unit,
    onOpenRun: (Long) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val palette = AiModuleTheme.colors
    var loading by remember { mutableStateOf(false) }
    var workflows by remember { mutableStateOf<List<GHWorkflow>>(emptyList()) }
    var runs by remember { mutableStateOf<List<GHWorkflowRun>>(emptyList()) }
    var actionsPermissions by remember { mutableStateOf<GHActionsPermissions?>(null) }
    var workflowPermissions by remember { mutableStateOf<GHWorkflowPermissions?>(null) }
    var failedJobsByRun by remember { mutableStateOf<Map<Long, List<GHJob>>>(emptyMap()) }
    var apiErrors by remember { mutableStateOf<List<GHApiErrorLogEntry>>(emptyList()) }
    var rateSummary by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf("") }

    fun load() {
        if (loading) return
        loading = true
        notice = ""
        scope.launch {
            workflows = GitHubManager.getWorkflows(context, repo.owner, repo.name)
            actionsPermissions = GitHubManager.getRepoActionsPermissions(context, repo.owner, repo.name)
            workflowPermissions = GitHubManager.getRepoActionsWorkflowPermissions(context, repo.owner, repo.name)
            runs = GitHubManager.getWorkflowRuns(context, repo.owner, repo.name, perPage = 30)
            rateSummary = GitHubManager.getRateLimitSummaryNative(context)
            val problemRuns = runs.filter { it.isProblemRun() }.take(5)
            failedJobsByRun = problemRuns.associate { run ->
                run.id to GitHubManager.getWorkflowRunJobs(context, repo.owner, repo.name, run.id)
            }
            val actionEndpointPrefix = "/repos/${repo.owner}/${repo.name}/actions"
            apiErrors = GitHubManager.getApiErrorLog(context)
                .filter { it.endpoint.contains(actionEndpointPrefix, ignoreCase = true) }
                .take(8)
            notice = "runs=${runs.size} workflows=${workflows.size}"
            loading = false
        }
    }

    LaunchedEffect(repo.owner, repo.name) { load() }

    val findings = remember(repo, workflows, runs, actionsPermissions, workflowPermissions, apiErrors, rateSummary) {
        buildActionsTroubleFindings(repo, workflows, runs, actionsPermissions, workflowPermissions, apiErrors, rateSummary)
    }
    val problemRuns = remember(runs) { runs.filter { it.isProblemRun() }.take(8) }
    val activeRuns = remember(runs) { runs.count { it.status in setOf("queued", "waiting", "requested", "pending", "in_progress") } }

    GitHubScreenFrame(
        title = "> actions troubleshoot",
        subtitle = repo.fullName,
        onBack = onBack,
        trailing = {
            GitHubTopBarAction(
                glyph = GhGlyphs.REFRESH,
                onClick = { load() },
                tint = palette.accent,
                enabled = !loading,
                contentDescription = "refresh actions troubleshoot",
            )
        },
    ) {
        LazyColumn(
            Modifier.fillMaxSize().background(palette.background),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                GitHubTroublePanel {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("actions health", color = palette.accent, fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                            Text(
                                if (loading) "checking repository actions..." else notice.ifBlank { "read-only actions diagnostics" },
                                color = palette.textMuted,
                                fontFamily = JetBrainsMono,
                                fontSize = 11.sp,
                            )
                        }
                        if (loading) AiModuleSpinner(label = "checking")
                        GitHubTerminalButton("refresh", onClick = { load() }, color = palette.textSecondary, enabled = !loading)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GitHubTroubleMetric("workflows", workflows.size.toString(), if (workflows.isEmpty()) GitHubWarningAmber() else GitHubSuccessGreen)
                        GitHubTroubleMetric("runs", runs.size.toString(), palette.textSecondary)
                        GitHubTroubleMetric("active", activeRuns.toString(), if (activeRuns > 0) palette.accent else palette.textMuted)
                        GitHubTroubleMetric("problem", problemRuns.size.toString(), if (problemRuns.isEmpty()) GitHubSuccessGreen else GitHubErrorRed)
                        GitHubTroubleMetric("api errors", apiErrors.size.toString(), if (apiErrors.isEmpty()) GitHubSuccessGreen else GitHubWarningAmber())
                    }
                }
            }

            item {
                GitHubActionsPermissionPanel(repo, actionsPermissions, workflowPermissions, rateSummary)
            }

            item {
                GitHubTroublePanel {
                    Text("findings", color = palette.accent, fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    if (findings.isEmpty()) {
                        Text("No obvious Actions blockers found in the latest snapshot.", color = palette.textSecondary, fontFamily = JetBrainsMono, fontSize = 12.sp)
                    } else {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            findings.forEach { GitHubTroubleFindingRow(it) }
                        }
                    }
                }
            }

            item {
                GitHubTroublePanel {
                    Text("problem runs", color = palette.accent, fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    if (problemRuns.isEmpty()) {
                        Text("No failed, cancelled, timed out, or action-required runs in the latest 30.", color = palette.textMuted, fontFamily = JetBrainsMono, fontSize = 12.sp)
                    }
                }
            }
            items(problemRuns, key = { it.id }) { run ->
                GitHubProblemRunCard(run, failedJobsByRun[run.id].orEmpty(), onOpenRun = { onOpenRun(run.id) })
            }

            if (apiErrors.isNotEmpty()) {
                item {
                    GitHubTroublePanel {
                        Text("recent Actions API errors", color = palette.accent, fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                        Spacer(Modifier.height(8.dp))
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            apiErrors.forEach { GitHubTroubleApiErrorRow(it) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GitHubActionsPermissionPanel(
    repo: GHRepo,
    actions: GHActionsPermissions?,
    workflow: GHWorkflowPermissions?,
    rateSummary: String,
) {
    val palette = AiModuleTheme.colors
    GitHubTroublePanel {
        Text("permissions", color = palette.accent, fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GitHubTroubleMetric("repo", githubRepoPermissionLabel(repo), if (repo.canWrite()) GitHubSuccessGreen else GitHubWarningAmber())
            GitHubTroubleMetric("actions", actions?.enabled?.let { if (it) "enabled" else "disabled" } ?: "unknown", if (actions?.enabled == true) GitHubSuccessGreen else GitHubWarningAmber())
            GitHubTroubleMetric("allowed", actions?.allowedActions?.ifBlank { "default" } ?: "unknown", palette.textSecondary)
            GitHubTroubleMetric("token", workflow?.defaultWorkflowPermissions?.ifBlank { "unknown" } ?: "unknown", if (workflow?.defaultWorkflowPermissions == "write") GitHubSuccessGreen else GitHubWarningAmber())
        }
        Spacer(Modifier.height(8.dp))
        GitHubTroubleKV("approve PR reviews", workflow?.canApprovePullRequestReviews?.toString() ?: "unknown")
        GitHubTroubleKV("rate limit", rateSummary.ifBlank { "unavailable" })
        if (!repo.canWrite()) {
            Spacer(Modifier.height(6.dp))
            GitHubPermissionHint("write permission required for dispatch/rerun/cancel")
        }
    }
}

@Composable
private fun GitHubProblemRunCard(run: GHWorkflowRun, jobs: List<GHJob>, onOpenRun: () -> Unit) {
    val palette = AiModuleTheme.colors
    val color = run.actionsTroubleColor()
    GitHubTroublePanel {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(run.displayTitle.ifBlank { run.name.ifBlank { "workflow run #${run.runNumber}" } }, color = palette.textPrimary, fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("#${run.runNumber} · ${run.branch.ifBlank { "no branch" }} · ${run.event} · ${run.actor}", color = palette.textMuted, fontFamily = JetBrainsMono, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(run.conclusion.ifBlank { run.status }, color = color, fontFamily = JetBrainsMono, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            GitHubTerminalButton("open", onClick = onOpenRun, color = palette.accent)
        }
        val failedJobs = jobs.filter { it.conclusion in setOf("failure", "timed_out", "cancelled", "action_required") }
        if (failedJobs.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                failedJobs.take(4).forEach { job ->
                    val failedSteps = job.steps.filter { it.conclusion in setOf("failure", "timed_out", "cancelled", "action_required") }
                    Text(
                        "job: ${job.name} · ${job.conclusion.ifBlank { job.status }}",
                        color = color,
                        fontFamily = JetBrainsMono,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    failedSteps.take(3).forEach { step ->
                        Text(
                            "  step ${step.number}: ${step.name} · ${step.conclusion.ifBlank { step.status }}",
                            color = palette.textMuted,
                            fontFamily = JetBrainsMono,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        } else if (jobs.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("No failed job was returned for this run; open details for logs and artifacts.", color = palette.textMuted, fontFamily = JetBrainsMono, fontSize = 11.sp)
        }
    }
}

@Composable
private fun GitHubTroubleFindingRow(finding: ActionsTroubleFinding) {
    val palette = AiModuleTheme.colors
    val color = when (finding.level) {
        "ok" -> GitHubSuccessGreen
        "warn" -> GitHubWarningAmber()
        else -> GitHubErrorRed
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        GitHubTroubleBadge(finding.level, color)
        Column(Modifier.weight(1f)) {
            Text(finding.title, color = palette.textPrimary, fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium, fontSize = 12.sp)
            Text(finding.detail, color = palette.textMuted, fontFamily = JetBrainsMono, fontSize = 11.sp, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun GitHubTroubleApiErrorRow(error: GHApiErrorLogEntry) {
    val palette = AiModuleTheme.colors
    Column(
        Modifier.fillMaxWidth().border(1.dp, palette.border, RoundedCornerShape(3.dp)).background(palette.background, RoundedCornerShape(3.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("${error.method} HTTP ${error.statusCode}", color = GitHubWarningAmber(), fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium, fontSize = 11.sp)
        Text(error.endpoint, color = palette.textSecondary, fontFamily = JetBrainsMono, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(error.message.ifBlank { error.body.ifBlank { "no error body" } }, color = palette.textMuted, fontFamily = JetBrainsMono, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun GitHubTroublePanel(content: @Composable () -> Unit) {
    val palette = AiModuleTheme.colors
    Box(
        Modifier.fillMaxWidth().border(1.dp, palette.border, RoundedCornerShape(4.dp)).background(palette.surface.copy(alpha = 0.72f), RoundedCornerShape(4.dp)).padding(12.dp),
    ) {
        Column(Modifier.fillMaxWidth()) { content() }
    }
}

@Composable
private fun GitHubTroubleMetric(label: String, value: String, color: Color) {
    val palette = AiModuleTheme.colors
    Column(
        Modifier.widthIn(min = 88.dp).border(1.dp, color.copy(alpha = 0.55f), RoundedCornerShape(3.dp)).background(palette.background, RoundedCornerShape(3.dp)).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(label, color = palette.textMuted, fontFamily = JetBrainsMono, fontSize = 10.sp)
        Text(value, color = color, fontFamily = JetBrainsMono, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun GitHubTroubleBadge(label: String, color: Color) {
    Box(Modifier.border(1.dp, color, RoundedCornerShape(2.dp)).padding(horizontal = 7.dp, vertical = 3.dp), contentAlignment = Alignment.Center) {
        Text(label.uppercase(Locale.US), color = color, fontFamily = JetBrainsMono, fontSize = 10.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun GitHubTroubleKV(label: String, value: String) {
    val palette = AiModuleTheme.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = palette.textMuted, fontFamily = JetBrainsMono, fontSize = 11.sp, modifier = Modifier.widthIn(min = 132.dp))
        Text(value, color = palette.textSecondary, fontFamily = JetBrainsMono, fontSize = 11.sp, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

private fun buildActionsTroubleFindings(
    repo: GHRepo,
    workflows: List<GHWorkflow>,
    runs: List<GHWorkflowRun>,
    actions: GHActionsPermissions?,
    workflow: GHWorkflowPermissions?,
    apiErrors: List<GHApiErrorLogEntry>,
    rateSummary: String,
): List<ActionsTroubleFinding> {
    val findings = mutableListOf<ActionsTroubleFinding>()
    if (!repo.canWrite()) {
        findings += ActionsTroubleFinding("warn", "Repository token is read-only", "Dispatch, rerun, cancel, secrets and workflow settings need write/admin permission.")
    }
    when {
        actions == null -> findings += ActionsTroubleFinding("warn", "Actions permissions unavailable", "The token may lack admin access to read repository Actions policy.")
        !actions.enabled -> findings += ActionsTroubleFinding("fail", "Actions are disabled", "Repository Actions policy reports enabled=false.")
        actions.allowedActions.isNotBlank() && actions.allowedActions != "all" -> findings += ActionsTroubleFinding("warn", "Allowed actions are restricted", "allowed_actions=${actions.allowedActions}; third-party actions may be blocked.")
    }
    if (workflow == null) {
        findings += ActionsTroubleFinding("warn", "Workflow token policy unavailable", "Could not read default GITHUB_TOKEN permissions.")
    } else if (workflow.defaultWorkflowPermissions == "read") {
        findings += ActionsTroubleFinding("warn", "Workflow token is read-only", "Workflows that push commits, create releases, upload packages or comment on PRs may fail.")
    }
    if (workflows.isEmpty()) {
        findings += ActionsTroubleFinding("warn", "No workflows returned", "The repository may not have workflows, or the token cannot read Actions workflows.")
    } else {
        val disabled = workflows.count { it.state != "active" }
        if (disabled > 0) findings += ActionsTroubleFinding("warn", "Inactive workflows", "$disabled workflow(s) are not active.")
    }
    if (runs.isEmpty()) {
        findings += ActionsTroubleFinding("warn", "No recent runs returned", "There are no recent runs, or the token cannot read run history.")
    } else {
        val failed = runs.count { it.conclusion == "failure" }
        val cancelled = runs.count { it.conclusion == "cancelled" }
        val blocked = runs.count { it.conclusion in setOf("action_required", "startup_failure", "timed_out") }
        if (failed > 0) findings += ActionsTroubleFinding("fail", "Recent failed runs", "$failed of the latest ${runs.size} runs concluded with failure.")
        if (cancelled > 0) findings += ActionsTroubleFinding("warn", "Recent cancelled runs", "$cancelled run(s) were cancelled.")
        if (blocked > 0) findings += ActionsTroubleFinding("fail", "Blocked or timed out runs", "$blocked run(s) need attention or exceeded runtime.")
    }
    if (apiErrors.isNotEmpty()) {
        findings += ActionsTroubleFinding("warn", "Recent Actions API errors", "${apiErrors.size} stored API error(s) for this repository Actions surface.")
    }
    if (rateSummary.startsWith("0 /")) {
        findings += ActionsTroubleFinding("fail", "Rate limit exhausted", rateSummary)
    }
    return findings
}

private fun GHWorkflowRun.isProblemRun(): Boolean =
    conclusion in setOf("failure", "cancelled", "timed_out", "action_required", "startup_failure")

@Composable
private fun GHWorkflowRun.actionsTroubleColor(): Color =
    when (conclusion) {
        "failure", "timed_out", "startup_failure" -> GitHubErrorRed
        "cancelled", "action_required" -> GitHubWarningAmber()
        else -> AiModuleTheme.colors.textSecondary
    }
