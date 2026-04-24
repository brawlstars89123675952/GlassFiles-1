package com.glassfiles.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glassfiles.data.github.GHCodeScanningAlert
import com.glassfiles.data.github.GHDependabotAlert
import com.glassfiles.data.github.GHRuleSuite
import com.glassfiles.data.github.GHRuleset
import com.glassfiles.data.github.GHRulesetBypassActor
import com.glassfiles.data.github.GHRulesetDetail
import com.glassfiles.data.github.GHRulesetRule
import com.glassfiles.data.github.GHSecretScanningAlert
import com.glassfiles.data.github.GitHubManager
import com.glassfiles.ui.theme.*
import kotlinx.coroutines.launch

private val RULESET_FILTERS = listOf("all", "active", "evaluate", "disabled")
private val ALERT_SEVERITIES = listOf("all", "critical", "high", "medium", "low")
private val ALERT_STATES = listOf("open", "fixed", "resolved", "dismissed", "all")
private val SECURITY_TABS = listOf("Dependabot", "Code", "Secrets")

@Composable
internal fun RulesetsScreen(
    repoOwner: String,
    repoName: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var rulesets by remember { mutableStateOf<List<GHRuleset>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    var enforcementFilter by remember { mutableStateOf("all") }
    var selectedRuleset by remember { mutableStateOf<GHRuleset?>(null) }

    fun loadRulesets() {
        loading = true
        scope.launch {
            rulesets = GitHubManager.getRulesets(context, repoOwner, repoName)
            loading = false
        }
    }

    LaunchedEffect(repoOwner, repoName) { loadRulesets() }

    selectedRuleset?.let { ruleset ->
        RulesetDetailScreen(
            repoOwner = repoOwner,
            repoName = repoName,
            ruleset = ruleset,
            onBack = { selectedRuleset = null }
        )
        return
    }

    Column(Modifier.fillMaxSize().background(SurfaceLight)) {
        GHTopBar(
            title = "Rulesets",
            subtitle = "$repoOwner/$repoName",
            onBack = onBack,
            actions = {
                IconButton(onClick = { loadRulesets() }, enabled = !loading) {
                    Icon(Icons.Rounded.Refresh, null, Modifier.size(20.dp), tint = Blue)
                }
            }
        )

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Blue, modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
            }
        } else {
            val visibleRulesets = rulesets.filter { ruleset ->
                (enforcementFilter == "all" || ruleset.enforcement.equals(enforcementFilter, ignoreCase = true)) &&
                    (query.isBlank() ||
                        ruleset.name.contains(query, ignoreCase = true) ||
                        ruleset.target.contains(query, ignoreCase = true) ||
                        ruleset.sourceType.contains(query, ignoreCase = true))
            }

            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item { RulesetsSummaryCard(rulesets) }
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Search rulesets") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Rounded.Search, null, Modifier.size(18.dp), tint = TextSecondary) }
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        RULESET_FILTERS.forEach { filter ->
                            GitHubSmallChoice(label = filter.replaceFirstChar { it.uppercase() }, selected = enforcementFilter == filter) {
                                enforcementFilter = filter
                            }
                        }
                    }
                }
                items(visibleRulesets, key = { it.id }) { ruleset ->
                    RulesetCard(ruleset, onDetails = { selectedRuleset = ruleset }) {
                        openGitHubSecurityUrl(context, ruleset.htmlUrl)
                    }
                }
                if (visibleRulesets.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text(if (rulesets.isEmpty()) "No rulesets configured" else "No matching rulesets", fontSize = 14.sp, color = TextTertiary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RulesetsSummaryCard(rulesets: List<GHRuleset>) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceWhite).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Rounded.Rule, null, Modifier.size(20.dp), tint = Blue)
            Text("Repository rules", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, modifier = Modifier.weight(1f))
            Text("${rulesets.size}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            SecurityPill("Active ${rulesets.count { it.enforcement.equals("active", true) }}", Color(0xFF34C759))
            SecurityPill("Evaluate ${rulesets.count { it.enforcement.equals("evaluate", true) }}", Color(0xFFFF9500))
            SecurityPill("Disabled ${rulesets.count { it.enforcement.equals("disabled", true) }}", TextTertiary)
        }
    }
}

@Composable
private fun RulesetCard(ruleset: GHRuleset, onDetails: () -> Unit, onOpen: () -> Unit) {
    val enforcementColor = rulesetColor(ruleset.enforcement)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceWhite).padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Rounded.Rule, null, Modifier.size(20.dp), tint = enforcementColor)
            Column(Modifier.weight(1f)) {
                Text(ruleset.name.ifBlank { "Ruleset #${ruleset.id}" }, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(cleanJoin(listOf(ruleset.target, ruleset.sourceType)), fontSize = 11.sp, color = TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onDetails) {
                Icon(Icons.Rounded.Article, null, Modifier.size(18.dp), tint = TextSecondary)
            }
            IconButton(onClick = onOpen, enabled = ruleset.htmlUrl.isNotBlank()) {
                Icon(Icons.Rounded.OpenInNew, null, Modifier.size(18.dp), tint = Blue)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            SecurityPill(ruleset.enforcement.ifBlank { "unknown" }, enforcementColor)
            SecurityPill("${ruleset.rulesCount} rules", TextSecondary)
            if (ruleset.updatedAt.isNotBlank()) SecurityPill("Updated ${ruleset.updatedAt.take(10)}", TextTertiary)
        }
    }
}

@Composable
private fun RulesetDetailScreen(
    repoOwner: String,
    repoName: String,
    ruleset: GHRuleset,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var detail by remember(ruleset.id) { mutableStateOf<GHRulesetDetail?>(null) }
    var suites by remember(ruleset.id) { mutableStateOf<List<GHRuleSuite>>(emptyList()) }
    var loading by remember(ruleset.id) { mutableStateOf(true) }

    fun loadDetail() {
        loading = true
        scope.launch {
            detail = GitHubManager.getRulesetDetail(context, repoOwner, repoName, ruleset.id)
            suites = GitHubManager.getRuleSuites(context, repoOwner, repoName)
            loading = false
        }
    }

    LaunchedEffect(ruleset.id) { loadDetail() }

    Column(Modifier.fillMaxSize().background(SurfaceLight)) {
        GHTopBar(
            title = ruleset.name.ifBlank { "Ruleset #${ruleset.id}" },
            subtitle = "$repoOwner/$repoName",
            onBack = onBack,
            actions = {
                IconButton(onClick = { loadDetail() }, enabled = !loading) {
                    Icon(Icons.Rounded.Refresh, null, Modifier.size(20.dp), tint = Blue)
                }
                IconButton(onClick = { openGitHubSecurityUrl(context, ruleset.htmlUrl) }, enabled = ruleset.htmlUrl.isNotBlank()) {
                    Icon(Icons.Rounded.OpenInNew, null, Modifier.size(20.dp), tint = Blue)
                }
            }
        )

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Blue, modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
            }
        } else {
            val current = detail
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (current == null) {
                    item { EmptySecurityResult(true, "Ruleset detail is unavailable", "Ruleset detail is unavailable") }
                } else {
                    item { RulesetDetailSummaryCard(current) }
                    item { RulesetConditionsCard(current) }
                    item { RulesetRulesCard(current.rules) }
                    item { RulesetBypassActorsCard(current.bypassActors) }
                    item { RuleSuitesCard(suites) }
                }
            }
        }
    }
}

@Composable
private fun RulesetDetailSummaryCard(detail: GHRulesetDetail) {
    val color = rulesetColor(detail.enforcement)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceWhite).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Rounded.Rule, null, Modifier.size(20.dp), tint = color)
            Column(Modifier.weight(1f)) {
                Text(detail.name.ifBlank { "Ruleset #${detail.id}" }, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text(cleanJoin(listOf(detail.target, detail.sourceType, detail.source)), fontSize = 11.sp, color = TextTertiary)
            }
            SecurityPill(detail.enforcement.ifBlank { "unknown" }, color)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            SecurityPill("${detail.rules.size} rules", TextSecondary)
            SecurityPill("${detail.bypassActors.size} bypass actors", TextSecondary)
            if (detail.updatedAt.isNotBlank()) SecurityPill("Updated ${detail.updatedAt.take(10)}", TextTertiary)
        }
    }
}

@Composable
private fun RulesetConditionsCard(detail: GHRulesetDetail) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceWhite).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Conditions", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        if (detail.refNameIncludes.isEmpty() && detail.refNameExcludes.isEmpty()) {
            Text("No ref name conditions returned", fontSize = 12.sp, color = TextTertiary)
        } else {
            if (detail.refNameIncludes.isNotEmpty()) RulesetValueList("Include", detail.refNameIncludes)
            if (detail.refNameExcludes.isNotEmpty()) RulesetValueList("Exclude", detail.refNameExcludes)
        }
    }
}

@Composable
private fun RulesetRulesCard(rules: List<GHRulesetRule>) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceWhite).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Rules", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        if (rules.isEmpty()) {
            Text("No rules returned", fontSize = 12.sp, color = TextTertiary)
        } else {
            rules.forEach { rule ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(SurfaceLight).padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(rule.type.replace('_', ' ').ifBlank { "rule" }, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                    rule.parameters.take(6).forEach { (key, value) ->
                        Text("$key: ${value.take(160)}", fontSize = 11.sp, color = TextSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                    if (rule.parameters.size > 6) Text("+${rule.parameters.size - 6} more parameters", fontSize = 11.sp, color = TextTertiary)
                }
            }
        }
    }
}

@Composable
private fun RulesetBypassActorsCard(actors: List<GHRulesetBypassActor>) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceWhite).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Bypass actors", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        if (actors.isEmpty()) {
            Text("No bypass actors", fontSize = 12.sp, color = TextTertiary)
        } else {
            actors.forEach { actor ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.Person, null, Modifier.size(16.dp), tint = TextSecondary)
                    Text(actor.actorType.ifBlank { "actor" }, fontSize = 13.sp, color = TextPrimary, modifier = Modifier.weight(1f))
                    SecurityPill(actor.bypassMode.ifBlank { "bypass" }, TextSecondary)
                    if (actor.actorId > 0) Text("#${actor.actorId}", fontSize = 11.sp, color = TextTertiary)
                }
            }
        }
    }
}

@Composable
private fun RuleSuitesCard(suites: List<GHRuleSuite>) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceWhite).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Recent rule suites", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        if (suites.isEmpty()) {
            Text("No rule suites returned", fontSize = 12.sp, color = TextTertiary)
        } else {
            suites.take(12).forEach { suite ->
                val color = ruleSuiteColor(suite)
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(SurfaceLight).padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SecurityPill(ruleSuiteLabel(suite), color)
                        Text(suite.ref.substringAfterLast('/').ifBlank { "ref" }, fontSize = 12.sp, color = TextPrimary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (suite.createdAt.isNotBlank()) Text(suite.createdAt.take(10), fontSize = 11.sp, color = TextTertiary)
                    }
                    val meta = cleanJoin(listOf(suite.actor, suite.afterSha.take(7), suite.evaluationResult))
                    if (meta.isNotBlank()) Text(meta, fontSize = 11.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun RulesetValueList(label: String, values: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, fontSize = 12.sp, color = TextTertiary, fontWeight = FontWeight.Medium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            values.forEach { value -> SecurityPill(value, TextSecondary) }
        }
    }
}

@Composable
internal fun SecurityScreen(
    repoOwner: String,
    repoName: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var alerts by remember { mutableStateOf<List<GHDependabotAlert>>(emptyList()) }
    var codeAlerts by remember { mutableStateOf<List<GHCodeScanningAlert>>(emptyList()) }
    var secretAlerts by remember { mutableStateOf<List<GHSecretScanningAlert>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    var severityFilter by remember { mutableStateOf("all") }
    var stateFilter by remember { mutableStateOf("open") }
    var selectedTab by remember { mutableStateOf("Dependabot") }
    var selectedCodeAlert by remember { mutableStateOf<GHCodeScanningAlert?>(null) }
    var selectedSecretAlert by remember { mutableStateOf<GHSecretScanningAlert?>(null) }

    fun loadAlerts() {
        loading = true
        scope.launch {
            when (selectedTab) {
                "Code" -> codeAlerts = GitHubManager.getCodeScanningAlerts(context, repoOwner, repoName)
                "Secrets" -> secretAlerts = GitHubManager.getSecretScanningAlerts(context, repoOwner, repoName)
                else -> alerts = GitHubManager.getDependabotAlerts(context, repoOwner, repoName)
            }
            loading = false
        }
    }

    LaunchedEffect(repoOwner, repoName, selectedTab) { loadAlerts() }

    Column(Modifier.fillMaxSize().background(SurfaceLight)) {
        GHTopBar(
            title = "Security",
            subtitle = "$repoOwner/$repoName · $selectedTab",
            onBack = onBack,
            actions = {
                IconButton(onClick = { loadAlerts() }, enabled = !loading) {
                    Icon(Icons.Rounded.Refresh, null, Modifier.size(20.dp), tint = Blue)
                }
            }
        )

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Blue, modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        SECURITY_TABS.forEach { tab ->
                            GitHubSmallChoice(label = tab, selected = selectedTab == tab) {
                                selectedTab = tab
                                query = ""
                                severityFilter = "all"
                                stateFilter = "open"
                            }
                        }
                    }
                }
                item {
                    when (selectedTab) {
                        "Code" -> CodeScanningSummaryCard(codeAlerts)
                        "Secrets" -> SecretScanningSummaryCard(secretAlerts)
                        else -> SecuritySummaryCard(alerts)
                    }
                }
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text(securitySearchLabel(selectedTab)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Rounded.Search, null, Modifier.size(18.dp), tint = TextSecondary) }
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        ALERT_STATES.forEach { state ->
                            GitHubSmallChoice(label = state.replaceFirstChar { it.uppercase() }, selected = stateFilter == state) {
                                stateFilter = state
                            }
                        }
                    }
                }
                if (selectedTab != "Secrets") item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        ALERT_SEVERITIES.forEach { severity ->
                            GitHubSmallChoice(label = severity.replaceFirstChar { it.uppercase() }, selected = severityFilter == severity) {
                                severityFilter = severity
                            }
                        }
                    }
                }
                when (selectedTab) {
                    "Code" -> {
                        val visibleAlerts = codeAlerts.filter { alert ->
                            (severityFilter == "all" || alert.severity.equals(severityFilter, ignoreCase = true)) &&
                                (stateFilter == "all" || alert.state.equals(stateFilter, ignoreCase = true)) &&
                                codeAlertMatches(alert, query)
                        }
                        items(visibleAlerts, key = { it.number }) { alert ->
                            CodeScanningAlertCard(alert, onOpen = { openGitHubSecurityUrl(context, alert.htmlUrl) }, onDetails = { selectedCodeAlert = alert })
                        }
                        if (visibleAlerts.isEmpty()) {
                            item { EmptySecurityResult(codeAlerts.isEmpty(), "No code scanning alerts", "No matching code scanning alerts") }
                        }
                    }
                    "Secrets" -> {
                        val visibleAlerts = secretAlerts.filter { alert ->
                            (stateFilter == "all" || alert.state.equals(stateFilter, ignoreCase = true)) &&
                                secretAlertMatches(alert, query)
                        }
                        items(visibleAlerts, key = { it.number }) { alert ->
                            SecretScanningAlertCard(alert, onOpen = { openGitHubSecurityUrl(context, alert.htmlUrl) }, onDetails = { selectedSecretAlert = alert })
                        }
                        if (visibleAlerts.isEmpty()) {
                            item { EmptySecurityResult(secretAlerts.isEmpty(), "No secret scanning alerts", "No matching secret scanning alerts") }
                        }
                    }
                    else -> {
                        val visibleAlerts = alerts.filter { alert ->
                            (severityFilter == "all" || alert.severity.equals(severityFilter, ignoreCase = true)) &&
                                (stateFilter == "all" || alert.state.equals(stateFilter, ignoreCase = true)) &&
                                dependabotAlertMatches(alert, query)
                        }
                        items(visibleAlerts, key = { it.number }) { alert ->
                            AlertCard(alert) {
                                openGitHubSecurityUrl(context, alert.htmlUrl)
                            }
                        }
                        if (visibleAlerts.isEmpty()) {
                            item { EmptySecurityResult(alerts.isEmpty(), "No Dependabot alerts", "No matching alerts") }
                        }
                    }
                }
            }
        }
    }

    selectedCodeAlert?.let { alert ->
        CodeScanningDetailDialog(alert, onDismiss = { selectedCodeAlert = null }) {
            openGitHubSecurityUrl(context, alert.htmlUrl)
        }
    }
    selectedSecretAlert?.let { alert ->
        SecretScanningDetailDialog(alert, onDismiss = { selectedSecretAlert = null }) {
            openGitHubSecurityUrl(context, alert.htmlUrl)
        }
    }
}

@Composable
private fun SecuritySummaryCard(alerts: List<GHDependabotAlert>) {
    val open = alerts.count { it.state.equals("open", true) }
    val criticalHigh = alerts.count { it.severity.equals("critical", true) || it.severity.equals("high", true) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceWhite).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Rounded.Security, null, Modifier.size(20.dp), tint = if (criticalHigh > 0) Color(0xFFFF3B30) else Blue)
            Text("Dependabot alerts", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, modifier = Modifier.weight(1f))
            Text("$open open", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            SecurityPill("Critical ${alerts.count { it.severity.equals("critical", true) }}", Color(0xFFFF3B30))
            SecurityPill("High ${alerts.count { it.severity.equals("high", true) }}", Color(0xFFFF3B30))
            SecurityPill("Medium ${alerts.count { it.severity.equals("medium", true) }}", Color(0xFFFF9500))
            SecurityPill("Low ${alerts.count { it.severity.equals("low", true) }}", Color(0xFF34C759))
        }
    }
}

@Composable
private fun CodeScanningSummaryCard(alerts: List<GHCodeScanningAlert>) {
    val open = alerts.count { it.state.equals("open", true) }
    val highRisk = alerts.count { it.severity.equals("critical", true) || it.severity.equals("high", true) || it.severity.equals("error", true) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceWhite).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Rounded.BugReport, null, Modifier.size(20.dp), tint = if (highRisk > 0) Color(0xFFFF3B30) else Blue)
            Text("Code scanning alerts", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, modifier = Modifier.weight(1f))
            Text("$open open", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            SecurityPill("Critical ${alerts.count { it.severity.equals("critical", true) }}", Color(0xFFFF3B30))
            SecurityPill("High ${alerts.count { it.severity.equals("high", true) || it.severity.equals("error", true) }}", Color(0xFFFF3B30))
            SecurityPill("Medium ${alerts.count { it.severity.equals("medium", true) || it.severity.equals("warning", true) }}", Color(0xFFFF9500))
            SecurityPill("Fixed ${alerts.count { it.state.equals("fixed", true) }}", Color(0xFF34C759))
        }
    }
}

@Composable
private fun SecretScanningSummaryCard(alerts: List<GHSecretScanningAlert>) {
    val open = alerts.count { it.state.equals("open", true) }
    val public = alerts.count { it.public }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceWhite).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Rounded.VpnKey, null, Modifier.size(20.dp), tint = if (open > 0) Color(0xFFFF3B30) else Blue)
            Text("Secret scanning alerts", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, modifier = Modifier.weight(1f))
            Text("$open open", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            SecurityPill("Public $public", if (public > 0) Color(0xFFFF3B30) else TextTertiary)
            SecurityPill("Resolved ${alerts.count { it.state.equals("resolved", true) }}", Color(0xFF34C759))
            SecurityPill("Bypassed ${alerts.count { it.pushProtectionBypassed }}", Color(0xFFFF9500))
        }
    }
}

@Composable
private fun AlertCard(alert: GHDependabotAlert, onOpen: () -> Unit) {
    val severityColor = alertSeverityColor(alert.severity)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceWhite).padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Rounded.Security, null, Modifier.size(20.dp), tint = severityColor)
            Column(Modifier.weight(1f)) {
                Text(alert.packageName.ifBlank { "Dependency alert #${alert.number}" }, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(cleanJoin(listOf(alert.ecosystem, alert.manifestPath)), fontSize = 11.sp, color = TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onOpen, enabled = alert.htmlUrl.isNotBlank()) {
                Icon(Icons.Rounded.OpenInNew, null, Modifier.size(18.dp), tint = Blue)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            SecurityPill(alert.severity.ifBlank { "unknown" }, severityColor)
            SecurityPill(alert.state.ifBlank { "unknown" }, alertStateColor(alert.state))
            alert.ghsaId.takeIf { it.isNotBlank() }?.let { SecurityPill(it, TextSecondary) }
            alert.cveId.takeIf { it.isNotBlank() }?.let { SecurityPill(it, TextSecondary) }
        }
        if (alert.summary.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(alert.summary, fontSize = 13.sp, color = TextPrimary, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        val detailLines = listOfNotNull(
            alert.vulnerableRequirements.takeIf { it.isNotBlank() }?.let { "Requires $it" },
            alert.fixedIn.takeIf { it.isNotEmpty() }?.joinToString(", ")?.let { "Fixed in $it" },
            alert.updatedAt.takeIf { it.isNotBlank() }?.take(10)?.let { "Updated $it" }
        )
        if (detailLines.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(detailLines.joinToString(" · "), fontSize = 11.sp, color = TextTertiary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun CodeScanningAlertCard(alert: GHCodeScanningAlert, onOpen: () -> Unit, onDetails: () -> Unit) {
    val severityColor = alertSeverityColor(alert.severity)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceWhite).padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Rounded.BugReport, null, Modifier.size(20.dp), tint = severityColor)
            Column(Modifier.weight(1f)) {
                Text(alert.ruleName.ifBlank { alert.ruleId.ifBlank { "Code alert #${alert.number}" } }, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(cleanJoin(listOf(alert.toolName, alert.pathWithLine())), fontSize = 11.sp, color = TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onDetails) {
                Icon(Icons.Rounded.Article, null, Modifier.size(18.dp), tint = TextSecondary)
            }
            IconButton(onClick = onOpen, enabled = alert.htmlUrl.isNotBlank()) {
                Icon(Icons.Rounded.OpenInNew, null, Modifier.size(18.dp), tint = Blue)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            SecurityPill(alert.severity.ifBlank { "unknown" }, severityColor)
            SecurityPill(alert.state.ifBlank { "unknown" }, alertStateColor(alert.state))
            alert.category.takeIf { it.isNotBlank() }?.let { SecurityPill(it, TextSecondary) }
            alert.ref.takeIf { it.isNotBlank() }?.substringAfterLast('/')?.let { SecurityPill(it, Blue) }
        }
        val body = alert.message.ifBlank { alert.description }
        if (body.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(body, fontSize = 13.sp, color = TextPrimary, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (alert.createdAt.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text("Created ${alert.createdAt.take(10)}", fontSize = 11.sp, color = TextTertiary)
        }
    }
}

@Composable
private fun SecretScanningAlertCard(alert: GHSecretScanningAlert, onOpen: () -> Unit, onDetails: () -> Unit) {
    val stateColor = alertStateColor(alert.state)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceWhite).padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Rounded.VpnKey, null, Modifier.size(20.dp), tint = stateColor)
            Column(Modifier.weight(1f)) {
                Text(alert.secretTypeDisplayName.ifBlank { alert.secretType.ifBlank { "Secret alert #${alert.number}" } }, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(maskSecret(alert.secret), fontSize = 11.sp, color = TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onDetails) {
                Icon(Icons.Rounded.Article, null, Modifier.size(18.dp), tint = TextSecondary)
            }
            IconButton(onClick = onOpen, enabled = alert.htmlUrl.isNotBlank()) {
                Icon(Icons.Rounded.OpenInNew, null, Modifier.size(18.dp), tint = Blue)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            SecurityPill(alert.state.ifBlank { "unknown" }, stateColor)
            alert.validity.takeIf { it.isNotBlank() && it != "null" }?.let { SecurityPill(it, if (it == "active") Color(0xFFFF3B30) else TextSecondary) }
            if (alert.public) SecurityPill("public", Color(0xFFFF3B30))
            if (alert.pushProtectionBypassed) SecurityPill("bypassed", Color(0xFFFF9500))
            alert.resolution.takeIf { it.isNotBlank() && it != "null" }?.let { SecurityPill(it, TextSecondary) }
        }
        val dates = listOfNotNull(
            alert.createdAt.takeIf { it.isNotBlank() }?.take(10)?.let { "Created $it" },
            alert.resolvedAt.takeIf { it.isNotBlank() }?.take(10)?.let { "Resolved $it" }
        )
        if (dates.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(dates.joinToString(" · "), fontSize = 11.sp, color = TextTertiary)
        }
    }
}

@Composable
private fun CodeScanningDetailDialog(alert: GHCodeScanningAlert, onDismiss: () -> Unit, onOpen: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceWhite,
        title = { Text("Code alert #${alert.number}", fontWeight = FontWeight.Bold, color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SecurityDetailLine("Rule", cleanJoin(listOf(alert.ruleName, alert.ruleId)))
                SecurityDetailLine("Tool", alert.toolName)
                SecurityDetailLine("Location", alert.pathWithLine())
                SecurityDetailLine("Ref", alert.ref)
                SecurityDetailLine("Category", alert.category)
                SecurityDetailLine("Status", cleanJoin(listOf(alert.state, alert.severity)))
                SecurityDetailLine("Message", alert.message.ifBlank { alert.description })
                SecurityDetailLine("Created", alert.createdAt.take(19).replace('T', ' '))
                SecurityDetailLine("Fixed", alert.fixedAt.take(19).replace('T', ' '))
                SecurityDetailLine("Dismissed", cleanJoin(listOf(alert.dismissedAt.take(19).replace('T', ' '), alert.dismissedReason)))
            }
        },
        confirmButton = { TextButton(onClick = onOpen, enabled = alert.htmlUrl.isNotBlank()) { Text("Open", color = Blue) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close", color = TextSecondary) } }
    )
}

@Composable
private fun SecretScanningDetailDialog(alert: GHSecretScanningAlert, onDismiss: () -> Unit, onOpen: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceWhite,
        title = { Text("Secret alert #${alert.number}", fontWeight = FontWeight.Bold, color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SecurityDetailLine("Type", alert.secretTypeDisplayName.ifBlank { alert.secretType })
                SecurityDetailLine("Secret", maskSecret(alert.secret))
                SecurityDetailLine("State", alert.state)
                SecurityDetailLine("Resolution", alert.resolution)
                SecurityDetailLine("Validity", alert.validity)
                SecurityDetailLine("Public", if (alert.public) "Yes" else "No")
                SecurityDetailLine("Push protection bypassed", if (alert.pushProtectionBypassed) "Yes" else "No")
                SecurityDetailLine("Created", alert.createdAt.take(19).replace('T', ' '))
                SecurityDetailLine("Resolved", alert.resolvedAt.take(19).replace('T', ' '))
            }
        },
        confirmButton = { TextButton(onClick = onOpen, enabled = alert.htmlUrl.isNotBlank()) { Text("Open", color = Blue) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close", color = TextSecondary) } }
    )
}

@Composable
private fun SecurityDetailLine(label: String, value: String) {
    val cleanValue = value.trim().takeUnless { it.isBlank() || it.equals("null", true) } ?: return
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, fontSize = 11.sp, color = TextTertiary, fontWeight = FontWeight.Medium)
        Text(cleanValue, fontSize = 12.sp, color = TextPrimary, lineHeight = 16.sp)
    }
}

@Composable
private fun EmptySecurityResult(emptySource: Boolean, emptyText: String, noMatchText: String) {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(if (emptySource) emptyText else noMatchText, fontSize = 14.sp, color = TextTertiary)
    }
}

@Composable
private fun GitHubSmallChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(8.dp))
            .background(if (selected) Blue.copy(alpha = 0.14f) else SurfaceWhite)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(label, fontSize = 12.sp, color = if (selected) Blue else TextSecondary, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun SecurityPill(label: String, color: Color) {
    Text(
        label,
        fontSize = 11.sp,
        color = color,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.background(color.copy(alpha = 0.1f), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

private fun rulesetColor(enforcement: String): Color = when (enforcement.lowercase()) {
    "active" -> Color(0xFF34C759)
    "evaluate" -> Color(0xFFFF9500)
    "disabled" -> TextTertiary
    else -> TextSecondary
}

private fun ruleSuiteLabel(suite: GHRuleSuite): String =
    suite.result.ifBlank { suite.status.ifBlank { suite.evaluationResult.ifBlank { "unknown" } } }

private fun ruleSuiteColor(suite: GHRuleSuite): Color = when (ruleSuiteLabel(suite).lowercase()) {
    "pass", "passed", "success" -> Color(0xFF34C759)
    "fail", "failed", "failure", "error" -> Color(0xFFFF3B30)
    "bypass", "bypassed" -> Color(0xFFFF9500)
    "evaluate" -> Color(0xFFFF9500)
    else -> TextSecondary
}

private fun alertSeverityColor(severity: String): Color = when (severity.lowercase()) {
    "critical", "high", "error" -> Color(0xFFFF3B30)
    "medium", "warning" -> Color(0xFFFF9500)
    "low" -> Color(0xFF34C759)
    else -> TextSecondary
}

private fun alertStateColor(state: String): Color = when (state.lowercase()) {
    "open" -> Color(0xFFFF3B30)
    "fixed", "resolved" -> Color(0xFF34C759)
    "dismissed", "closed" -> TextTertiary
    else -> TextSecondary
}

private fun cleanJoin(values: List<String>): String =
    values.filter { it.isNotBlank() && it != "null" }.joinToString(" · ").ifBlank { "Repository" }

private fun securitySearchLabel(tab: String): String = when (tab) {
    "Code" -> "Search rule, tool, path or ref"
    "Secrets" -> "Search secret type, state or resolution"
    else -> "Search package, advisory or manifest"
}

private fun dependabotAlertMatches(alert: GHDependabotAlert, query: String): Boolean {
    val q = query.trim()
    return q.isBlank() ||
        alert.packageName.contains(q, ignoreCase = true) ||
        alert.summary.contains(q, ignoreCase = true) ||
        alert.ecosystem.contains(q, ignoreCase = true) ||
        alert.manifestPath.contains(q, ignoreCase = true) ||
        alert.ghsaId.contains(q, ignoreCase = true) ||
        alert.cveId.contains(q, ignoreCase = true)
}

private fun codeAlertMatches(alert: GHCodeScanningAlert, query: String): Boolean {
    val q = query.trim()
    return q.isBlank() ||
        alert.ruleName.contains(q, ignoreCase = true) ||
        alert.ruleId.contains(q, ignoreCase = true) ||
        alert.toolName.contains(q, ignoreCase = true) ||
        alert.path.contains(q, ignoreCase = true) ||
        alert.ref.contains(q, ignoreCase = true) ||
        alert.message.contains(q, ignoreCase = true) ||
        alert.category.contains(q, ignoreCase = true)
}

private fun secretAlertMatches(alert: GHSecretScanningAlert, query: String): Boolean {
    val q = query.trim()
    return q.isBlank() ||
        alert.secretType.contains(q, ignoreCase = true) ||
        alert.secretTypeDisplayName.contains(q, ignoreCase = true) ||
        alert.state.contains(q, ignoreCase = true) ||
        alert.resolution.contains(q, ignoreCase = true) ||
        alert.validity.contains(q, ignoreCase = true)
}

private fun GHCodeScanningAlert.pathWithLine(): String =
    if (path.isBlank()) "" else if (startLine > 0) "$path:$startLine" else path

private fun maskSecret(secret: String): String {
    val clean = secret.trim().takeUnless { it.isBlank() || it.equals("null", true) } ?: return "Secret value hidden"
    return if (clean.length <= 8) "********" else "${clean.take(4)}...${clean.takeLast(4)}"
}

private fun openGitHubSecurityUrl(context: Context, url: String) {
    if (url.isBlank()) return
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
    } catch (_: Exception) {
    }
}
