package com.glassfiles.ui.screens

import android.content.Context
import android.os.Environment
import android.widget.Toast
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glassfiles.data.Strings
import com.glassfiles.data.github.GHApiDiagnosticCheck
import com.glassfiles.data.github.GHApiDiagnostics
import com.glassfiles.data.github.GHApiErrorLogEntry
import com.glassfiles.data.github.GitHubManager
import com.glassfiles.ui.components.AiModuleSpinner
import com.glassfiles.ui.components.AiModuleText as Text
import com.glassfiles.ui.theme.AiModuleTheme
import com.glassfiles.ui.theme.JetBrainsMono
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun GitHubDiagnosticsScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val palette = AiModuleTheme.colors
    var owner by rememberSaveable { mutableStateOf("") }
    var repo by rememberSaveable { mutableStateOf("") }
    var org by rememberSaveable { mutableStateOf("") }
    var enterprise by rememberSaveable { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<GHApiDiagnostics?>(null) }
    var errorLog by remember { mutableStateOf<List<GHApiErrorLogEntry>>(emptyList()) }
    var notice by remember { mutableStateOf("") }

    fun runChecks() {
        if (loading) return
        loading = true
        notice = ""
        scope.launch {
            report = GitHubManager.runApiDiagnostics(context, owner, repo, org, enterprise)
            errorLog = GitHubManager.getApiErrorLog(context)
            notice = "checks=${report?.checks?.size ?: 0}"
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        loading = true
        report = GitHubManager.runApiDiagnostics(context)
        errorLog = GitHubManager.getApiErrorLog(context)
        notice = "basic checks ready"
        loading = false
    }

    GitHubScreenFrame(
        title = "> api diagnostics",
        subtitle = "token / scopes / rate limit / permissions",
        onBack = onBack,
        trailing = {
            GitHubTopBarAction(
                glyph = GhGlyphs.REFRESH,
                onClick = { runChecks() },
                tint = palette.accent,
                enabled = !loading,
                contentDescription = "run diagnostics",
            )
        },
    ) {
        LazyColumn(
            Modifier.fillMaxSize().background(palette.background),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                GitHubDiagnosticPanel {
                    Text(
                        "Read-only diagnostics. These checks do not mutate repositories or organization settings.",
                        color = palette.textMuted,
                        fontFamily = JetBrainsMono,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GitHubDiagnosticInput("owner", owner, { owner = it }, "octocat", Modifier.weight(1f))
                        GitHubDiagnosticInput("repo", repo, { repo = it }, "hello-world", Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GitHubDiagnosticInput("org", org, { org = it }, "optional", Modifier.weight(1f))
                        GitHubDiagnosticInput("enterprise", enterprise, { enterprise = it }, "optional", Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        GitHubTerminalButton(if (loading) "checking..." else "run checks", onClick = { runChecks() }, color = palette.accent, enabled = !loading)
                        GitHubTerminalButton("clear target", onClick = {
                            owner = ""
                            repo = ""
                            org = ""
                            enterprise = ""
                        }, color = palette.textSecondary, enabled = !loading)
                        if (loading) AiModuleSpinner(label = "polling GitHub")
                        report?.let { current ->
                            GitHubTerminalButton("export txt", onClick = {
                                val file = saveGitHubDiagnosticsExport(context, current, errorLog, "txt")
                                Toast.makeText(context, if (file != null) "${Strings.done}: ${file.name}" else Strings.error, Toast.LENGTH_SHORT).show()
                            }, color = palette.textSecondary)
                            GitHubTerminalButton("export json", onClick = {
                                val file = saveGitHubDiagnosticsExport(context, current, errorLog, "json")
                                Toast.makeText(context, if (file != null) "${Strings.done}: ${file.name}" else Strings.error, Toast.LENGTH_SHORT).show()
                            }, color = palette.textSecondary)
                        }
                        if (notice.isNotBlank()) {
                            Text(notice, color = palette.textMuted, fontFamily = JetBrainsMono, fontSize = 11.sp)
                        }
                    }
                }
            }

            report?.let { current ->
                item { GitHubDiagnosticsSummary(current) }
                item {
                    GitHubApiErrorLogPanel(
                        errors = errorLog,
                        onClear = {
                            GitHubManager.clearApiErrorLog(context)
                            errorLog = emptyList()
                            notice = "api error log cleared"
                        },
                        onRefresh = {
                            errorLog = GitHubManager.getApiErrorLog(context)
                            notice = "api error log refreshed"
                        },
                    )
                }
                items(current.checks, key = { "${it.title}:${it.endpoint}" }) { check ->
                    GitHubDiagnosticCheckRow(check)
                }
            }

            if (report == null && !loading) {
                item {
                    Text(
                        "No diagnostics report yet.",
                        color = palette.textMuted,
                        fontFamily = JetBrainsMono,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun GitHubApiErrorLogPanel(
    errors: List<GHApiErrorLogEntry>,
    onClear: () -> Unit,
    onRefresh: () -> Unit,
) {
    val palette = AiModuleTheme.colors
    GitHubDiagnosticPanel {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(
                    "recent api errors",
                    color = palette.accent,
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                )
                Text(
                    if (errors.isEmpty()) "no stored errors" else "stored=${errors.size} · newest first",
                    color = palette.textMuted,
                    fontFamily = JetBrainsMono,
                    fontSize = 11.sp,
                )
            }
            GitHubTerminalButton("refresh", onClick = onRefresh, color = palette.textSecondary)
            GitHubTerminalButton("clear", onClick = onClear, color = palette.error, enabled = errors.isNotEmpty())
        }
        if (errors.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                errors.take(8).forEach { item ->
                    GitHubApiErrorRow(item)
                }
            }
        }
    }
}

@Composable
private fun GitHubApiErrorRow(item: GHApiErrorLogEntry) {
    val palette = AiModuleTheme.colors
    val color = when (item.statusCode) {
        401, 403 -> GitHubWarningAmber()
        404 -> palette.textMuted
        in 500..599 -> GitHubErrorRed
        else -> palette.error
    }
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, palette.border, RoundedCornerShape(3.dp))
            .background(palette.background, RoundedCornerShape(3.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${item.method} HTTP ${item.statusCode}", color = color, fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium, fontSize = 11.sp)
            Text(githubDiagnosticsTime(item.timestamp), color = palette.textMuted, fontFamily = JetBrainsMono, fontSize = 10.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(item.endpoint, color = palette.textSecondary, fontFamily = JetBrainsMono, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(item.message.ifBlank { item.body.ifBlank { "no error body" } }, color = color, fontFamily = JetBrainsMono, fontSize = 11.sp, lineHeight = 16.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        val meta = listOfNotNull(
            item.requestId.takeIf { it.isNotBlank() }?.let { "request=$it" },
            item.rateRemaining.takeIf { it.isNotBlank() }?.let { "remaining=$it" },
        ).joinToString(" · ")
        if (meta.isNotBlank()) {
            Text(meta, color = palette.textMuted, fontFamily = JetBrainsMono, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun GitHubDiagnosticsSummary(report: GHApiDiagnostics) {
    val palette = AiModuleTheme.colors
    val updated = remember(report.generatedAt) { githubDiagnosticsTime(report.generatedAt) }
    GitHubDiagnosticPanel {
        Text(
            "summary",
            color = palette.accent,
            fontFamily = JetBrainsMono,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GitHubRateBox("core", "${report.rate.coreRemaining}/${report.rate.coreLimit}")
            GitHubRateBox("search", "${report.rate.searchRemaining}/${report.rate.searchLimit}")
            GitHubRateBox("graphql", "${report.rate.graphqlRemaining}/${report.rate.graphqlLimit}")
            GitHubRateBox("reset", report.rate.resetEpoch.takeIf { it > 0L }?.toString() ?: "n/a")
        }
        Spacer(Modifier.height(8.dp))
        GitHubDiagnosticKV("updated", updated)
        GitHubDiagnosticKV("token scopes", report.scopes.ifBlank { "empty or fine-grained token" })
        GitHubDiagnosticKV("accepted scopes", report.acceptedScopes.ifBlank { "not returned" })
    }
}

@Composable
private fun GitHubDiagnosticCheckRow(check: GHApiDiagnosticCheck) {
    val palette = AiModuleTheme.colors
    val color = when (check.status) {
        "ok" -> GitHubSuccessGreen
        "warn" -> GitHubWarningAmber()
        "skip" -> palette.textMuted
        else -> GitHubErrorRed
    }
    GitHubDiagnosticPanel {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GitHubDiagnosticStatus(check.status, color)
            Column(Modifier.weight(1f)) {
                Text(
                    check.title,
                    color = palette.textPrimary,
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    check.endpoint,
                    color = palette.textMuted,
                    fontFamily = JetBrainsMono,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val codeText = if (check.statusCode > 0) "HTTP ${check.statusCode}" else "skip"
            Text(codeText, color = color, fontFamily = JetBrainsMono, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            check.message,
            color = if (check.status == "ok") palette.textSecondary else color,
            fontFamily = JetBrainsMono,
            fontSize = 12.sp,
            lineHeight = 17.sp,
        )
        if (check.hint.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                check.hint,
                color = palette.textMuted,
                fontFamily = JetBrainsMono,
                fontSize = 11.sp,
                lineHeight = 16.sp,
            )
        }
    }
}

@Composable
private fun GitHubDiagnosticInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val palette = AiModuleTheme.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = palette.textMuted, fontFamily = JetBrainsMono, fontSize = 11.sp)
        GitHubTerminalTextField(value, onValueChange, placeholder = placeholder, singleLine = true, minHeight = 38.dp)
    }
}

@Composable
private fun GitHubDiagnosticPanel(content: @Composable () -> Unit) {
    val palette = AiModuleTheme.colors
    Box(
        Modifier
            .fillMaxWidth()
            .border(1.dp, palette.border, RoundedCornerShape(4.dp))
            .background(palette.surface.copy(alpha = 0.72f), RoundedCornerShape(4.dp))
            .padding(12.dp),
    ) {
        Column(Modifier.fillMaxWidth()) { content() }
    }
}

@Composable
private fun GitHubRateBox(label: String, value: String) {
    val palette = AiModuleTheme.colors
    Column(
        Modifier
            .widthIn(min = 92.dp)
            .border(1.dp, palette.border, RoundedCornerShape(3.dp))
            .background(palette.background, RoundedCornerShape(3.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(label, color = palette.textMuted, fontFamily = JetBrainsMono, fontSize = 10.sp)
        Text(value, color = palette.textPrimary, fontFamily = JetBrainsMono, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun GitHubDiagnosticStatus(status: String, color: Color) {
    Box(
        Modifier
            .width(54.dp)
            .border(1.dp, color, RoundedCornerShape(2.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(status, color = color, fontFamily = JetBrainsMono, fontSize = 10.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun GitHubDiagnosticKV(label: String, value: String) {
    val palette = AiModuleTheme.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = palette.textMuted, fontFamily = JetBrainsMono, fontSize = 11.sp, modifier = Modifier.width(118.dp))
        Text(value, color = palette.textSecondary, fontFamily = JetBrainsMono, fontSize = 11.sp, modifier = Modifier.weight(1f), lineHeight = 16.sp)
    }
}

private fun githubDiagnosticsTime(epochMillis: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(epochMillis))

private fun githubDiagnosticsFileStamp(epochMillis: Long): String =
    SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(epochMillis))

private fun saveGitHubDiagnosticsExport(
    context: Context,
    report: GHApiDiagnostics,
    errors: List<GHApiErrorLogEntry>,
    format: String,
): File? =
    try {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "GlassFiles_Git")
        dir.mkdirs()
        val extension = if (format == "json") "json" else "txt"
        val file = File(dir, "github_diagnostics_${githubDiagnosticsFileStamp(report.generatedAt)}.$extension")
        val content = if (extension == "json") {
            githubDiagnosticsJson(report, errors).toString(2)
        } else {
            githubDiagnosticsText(report, errors)
        }
        file.writeText(content)
        file
    } catch (_: Exception) {
        null
    }

private fun githubDiagnosticsText(report: GHApiDiagnostics, errors: List<GHApiErrorLogEntry>): String =
    buildString {
        appendLine("GlassFiles GitHub API Diagnostics")
        appendLine("generated: ${githubDiagnosticsTime(report.generatedAt)}")
        appendLine()
        appendLine("[rate]")
        appendLine("core: ${report.rate.coreRemaining}/${report.rate.coreLimit}")
        appendLine("search: ${report.rate.searchRemaining}/${report.rate.searchLimit}")
        appendLine("graphql: ${report.rate.graphqlRemaining}/${report.rate.graphqlLimit}")
        appendLine("reset_epoch: ${report.rate.resetEpoch}")
        appendLine()
        appendLine("[scopes]")
        appendLine("token_scopes: ${report.scopes.ifBlank { "empty or fine-grained token" }}")
        appendLine("accepted_scopes: ${report.acceptedScopes.ifBlank { "not returned" }}")
        appendLine()
        appendLine("[checks]")
        report.checks.forEach { check ->
            appendLine("- ${check.status.uppercase(Locale.US)} ${check.title} · ${if (check.statusCode > 0) "HTTP ${check.statusCode}" else "skip"}")
            appendLine("  endpoint: ${check.endpoint}")
            appendLine("  message: ${check.message}")
            if (check.hint.isNotBlank()) appendLine("  hint: ${check.hint}")
        }
        appendLine()
        appendLine("[recent_api_errors]")
        if (errors.isEmpty()) {
            appendLine("none")
        } else {
            errors.forEach { item ->
                appendLine("- ${githubDiagnosticsTime(item.timestamp)} ${item.method} HTTP ${item.statusCode}")
                appendLine("  endpoint: ${item.endpoint}")
                appendLine("  message: ${item.message.ifBlank { item.body.ifBlank { "no error body" } }}")
                if (item.requestId.isNotBlank()) appendLine("  request_id: ${item.requestId}")
                if (item.rateRemaining.isNotBlank()) appendLine("  rate_remaining: ${item.rateRemaining}")
            }
        }
    }

private fun githubDiagnosticsJson(report: GHApiDiagnostics, errors: List<GHApiErrorLogEntry>): JSONObject =
    JSONObject().apply {
        put("generated_at", report.generatedAt)
        put("generated_at_text", githubDiagnosticsTime(report.generatedAt))
        put("scopes", report.scopes)
        put("accepted_scopes", report.acceptedScopes)
        put("rate", JSONObject().apply {
            put("core_limit", report.rate.coreLimit)
            put("core_remaining", report.rate.coreRemaining)
            put("search_limit", report.rate.searchLimit)
            put("search_remaining", report.rate.searchRemaining)
            put("graphql_limit", report.rate.graphqlLimit)
            put("graphql_remaining", report.rate.graphqlRemaining)
            put("reset_epoch", report.rate.resetEpoch)
        })
        put("checks", JSONArray().apply {
            report.checks.forEach { check ->
                put(JSONObject().apply {
                    put("title", check.title)
                    put("endpoint", check.endpoint)
                    put("status_code", check.statusCode)
                    put("status", check.status)
                    put("message", check.message)
                    put("hint", check.hint)
                })
            }
        })
        put("recent_api_errors", JSONArray().apply {
            errors.forEach { item ->
                put(JSONObject().apply {
                    put("timestamp", item.timestamp)
                    put("timestamp_text", githubDiagnosticsTime(item.timestamp))
                    put("method", item.method)
                    put("endpoint", item.endpoint)
                    put("status_code", item.statusCode)
                    put("message", item.message)
                    put("body", item.body)
                    put("request_id", item.requestId)
                    put("rate_remaining", item.rateRemaining)
                })
            }
        })
    }
