package com.glassfiles.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
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
import com.glassfiles.ui.components.AiModuleAlertDialog
import com.glassfiles.ui.components.AiModuleHairline
import com.glassfiles.ui.components.AiModulePageBar
import com.glassfiles.ui.components.AiModuleSearchField
import com.glassfiles.ui.components.AiModuleSpinner
import com.glassfiles.ui.components.AiModuleTextAction
import com.glassfiles.ui.components.AiModuleTextField
import com.glassfiles.data.github.GHCommunityProfile
import com.glassfiles.data.github.GHDependabotAlert
import com.glassfiles.data.github.GHRepositorySecurityAdvisory
import com.glassfiles.data.github.GHRepositorySecuritySettings
import com.glassfiles.data.github.GHRuleSuite
import com.glassfiles.data.github.GHRuleset
import com.glassfiles.data.github.GHRulesetBypassActor
import com.glassfiles.data.github.GHRulesetDetail
import com.glassfiles.data.github.GHRulesetRule
import com.glassfiles.data.github.GHSecretScanningAlert
import com.glassfiles.data.github.GitHubManager
import com.glassfiles.ui.theme.AiModuleTheme
import com.glassfiles.ui.theme.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private val RULESET_FILTERS = listOf("all", "active", "evaluate", "disabled")
private val ALERT_SEVERITIES = listOf("all", "critical", "high", "medium", "low")
private val ALERT_STATES = listOf("open", "fixed", "resolved", "dismissed", "published", "draft", "closed", "all")
private val SECURITY_TABS = listOf("Dependabot", "Code", "Secrets", "Advisories", "Community", "Settings")

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
    var showCreateRuleset by remember { mutableStateOf(false) }
    var actionInFlight by remember { mutableStateOf(false) }

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
            onBack = { selectedRuleset = null },
            onChanged = {
                selectedRuleset = null
                loadRulesets()
            },
            onDeleted = {
                selectedRuleset = null
                loadRulesets()
            }
        )
        return
    }

    Column(Modifier.fillMaxSize().background(AiModuleTheme.colors.background)) {
        AiModulePageBar(
            title = "> rulesets",
            subtitle = "$repoOwner/$repoName",
            onBack = onBack,
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { loadRulesets() }, enabled = !loading, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp), tint = AiModuleTheme.colors.accent)
                    }
                    IconButton(onClick = { showCreateRuleset = true }, enabled = !loading, modifier = Modifier.size(36.dp)) {
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
                    AiModuleSearchField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = "search rulesets",
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
                            Text(if (rulesets.isEmpty()) "No rulesets configured" else "No matching rulesets", fontSize = 14.sp, color = AiModuleTheme.colors.textMuted)
                        }
                    }
                }
            }
        }
    }

    if (showCreateRuleset) {
        RulesetEditorDialog(
            title = "New Ruleset",
            initialName = "",
            initialTarget = "branch",
            initialEnforcement = "evaluate",
            initialInclude = "~DEFAULT_BRANCH",
            initialExclude = "",
            initialRulesJson = "[{\"type\":\"non_fast_forward\"}]",
            confirmLabel = "Create",
            onDismiss = { showCreateRuleset = false },
            onSave = { name, target, enforcement, includeRefs, excludeRefs, rulesJson ->
                actionInFlight = true
                scope.launch {
                    val created = GitHubManager.createRuleset(context, repoOwner, repoName, name, target, enforcement, includeRefs, excludeRefs, rulesJson)
                    actionInFlight = false
                    if (created != null) {
                        showCreateRuleset = false
                        loadRulesets()
                    }
                }
            },
            enabled = !actionInFlight
        )
    }
}

@Composable
private fun RulesetsSummaryCard(rulesets: List<GHRuleset>) {
    Column(Modifier.fillMaxWidth().ghGlassCard(14.dp).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Rounded.Rule, null, Modifier.size(20.dp), tint = AiModuleTheme.colors.accent)
            Text("Repository rules", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = AiModuleTheme.colors.textPrimary, modifier = Modifier.weight(1f))
            Text("${rulesets.size}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AiModuleTheme.colors.textPrimary)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            SecurityPill("Active ${rulesets.count { it.enforcement.equals("active", true) }}", Color(0xFF34C759))
            SecurityPill("Evaluate ${rulesets.count { it.enforcement.equals("evaluate", true) }}", Color(0xFFFF9500))
            SecurityPill("Disabled ${rulesets.count { it.enforcement.equals("disabled", true) }}", AiModuleTheme.colors.textMuted)
        }
    }
}

@Composable
private fun RulesetCard(ruleset: GHRuleset, onDetails: () -> Unit, onOpen: () -> Unit) {
    val enforcementColor = rulesetColor(ruleset.enforcement)
    Column(
        Modifier.fillMaxWidth().ghGlassCard(14.dp).padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Rounded.Rule, null, Modifier.size(20.dp), tint = enforcementColor)
            Column(Modifier.weight(1f)) {
                Text(ruleset.name.ifBlank { "Ruleset #${ruleset.id}" }, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AiModuleTheme.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(cleanJoin(listOf(ruleset.target, ruleset.sourceType)), fontSize = 11.sp, color = AiModuleTheme.colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onDetails) {
                Icon(Icons.Rounded.Article, null, Modifier.size(18.dp), tint = AiModuleTheme.colors.textSecondary)
            }
            IconButton(onClick = onOpen, enabled = ruleset.htmlUrl.isNotBlank()) {
                Icon(Icons.Rounded.OpenInNew, null, Modifier.size(18.dp), tint = AiModuleTheme.colors.accent)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            SecurityPill(ruleset.enforcement.ifBlank { "unknown" }, enforcementColor)
            SecurityPill("${ruleset.rulesCount} rules", AiModuleTheme.colors.textSecondary)
            if (ruleset.updatedAt.isNotBlank()) SecurityPill("Updated ${ruleset.updatedAt.take(10)}", AiModuleTheme.colors.textMuted)
        }
    }
}

@Composable
private fun RulesetDetailScreen(
    repoOwner: String,
    repoName: String,
    ruleset: GHRuleset,
    onBack: () -> Unit,
    onChanged: () -> Unit,
    onDeleted: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var detail by remember(ruleset.id) { mutableStateOf<GHRulesetDetail?>(null) }
    var suites by remember(ruleset.id) { mutableStateOf<List<GHRuleSuite>>(emptyList()) }
    var loading by remember(ruleset.id) { mutableStateOf(true) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var selectedSuite by remember { mutableStateOf<GHRuleSuite?>(null) }
    var actionInFlight by remember { mutableStateOf(false) }

    fun loadDetail() {
        loading = true
        scope.launch {
            detail = GitHubManager.getRulesetDetail(context, repoOwner, repoName, ruleset.id)
            suites = GitHubManager.getRuleSuites(context, repoOwner, repoName)
            loading = false
        }
    }

    LaunchedEffect(ruleset.id) { loadDetail() }

    Column(Modifier.fillMaxSize().background(AiModuleTheme.colors.background)) {
        AiModulePageBar(
            title = "> ${ruleset.name.ifBlank { "ruleset #${ruleset.id}" }.lowercase()}",
            subtitle = "$repoOwner/$repoName",
            onBack = onBack,
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { loadDetail() }, enabled = !loading, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp), tint = AiModuleTheme.colors.accent)
                    }
                    IconButton(onClick = { showEditDialog = true }, enabled = detail != null, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Rounded.Edit, null, Modifier.size(18.dp), tint = AiModuleTheme.colors.accent)
                    }
                    IconButton(onClick = { showDeleteDialog = true }, enabled = !actionInFlight, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Rounded.Delete, null, Modifier.size(18.dp), tint = AiModuleTheme.colors.error)
                    }
                    IconButton(onClick = { openGitHubSecurityUrl(context, ruleset.htmlUrl) }, enabled = ruleset.htmlUrl.isNotBlank(), modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Rounded.OpenInNew, null, Modifier.size(18.dp), tint = AiModuleTheme.colors.accent)
                    }
                }
            },
        )

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AiModuleTheme.colors.accent, modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
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
                    item {
                        RuleSuitesCard(suites) { suite ->
                            selectedSuite = suite
                            scope.launch {
                                selectedSuite = GitHubManager.getRuleSuite(context, repoOwner, repoName, suite.id) ?: suite
                            }
                        }
                    }
                }
            }
        }
    }

    detail?.let { current ->
        if (showEditDialog) {
            RulesetEditorDialog(
                title = "Edit Ruleset",
                initialName = current.name,
                initialTarget = current.target.ifBlank { "branch" },
                initialEnforcement = current.enforcement.ifBlank { "evaluate" },
                initialInclude = current.refNameIncludes.joinToString("\n"),
                initialExclude = current.refNameExcludes.joinToString("\n"),
                initialRulesJson = rulesetRulesJson(current.rules),
                confirmLabel = "Save",
                onDismiss = { showEditDialog = false },
                onSave = { name, target, enforcement, includeRefs, excludeRefs, rulesJson ->
                    actionInFlight = true
                    scope.launch {
                        val updated = GitHubManager.updateRuleset(context, repoOwner, repoName, current.id, name, target, enforcement, includeRefs, excludeRefs, rulesJson)
                        actionInFlight = false
                        if (updated != null) {
                            showEditDialog = false
                            detail = updated
                            onChanged()
                        }
                    }
                },
                enabled = !actionInFlight
            )
        }
        if (showDeleteDialog) {
            AiModuleAlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = "delete ruleset?",
                confirmButton = {
                    AiModuleTextAction(
                        label = "delete",
                        enabled = !actionInFlight,
                        tint = AiModuleTheme.colors.error,
                        onClick = {
                            actionInFlight = true
                            scope.launch {
                                val ok = GitHubManager.deleteRuleset(context, repoOwner, repoName, current.id)
                                actionInFlight = false
                                showDeleteDialog = false
                                if (ok) onDeleted()
                            }
                        },
                    )
                },
                dismissButton = {
                    AiModuleTextAction(label = "cancel", onClick = { showDeleteDialog = false }, tint = AiModuleTheme.colors.textSecondary)
                },
            ) {
                Text(
                    "Delete ${current.name.ifBlank { "ruleset #${current.id}" }}?",
                    fontSize = 13.sp,
                    color = AiModuleTheme.colors.textSecondary,
                    fontFamily = JetBrainsMono,
                )
            }
        }
    }

    selectedSuite?.let { suite ->
        RuleSuiteDetailDialog(
            suite = suite,
            onDismiss = { selectedSuite = null }
        )
    }
}

@Composable
private fun RulesetEditorDialog(
    title: String,
    initialName: String,
    initialTarget: String,
    initialEnforcement: String,
    initialInclude: String,
    initialExclude: String,
    initialRulesJson: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onSave: (String, String, String, List<String>, List<String>, String) -> Unit,
    enabled: Boolean
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var target by remember(initialTarget) { mutableStateOf(initialTarget.ifBlank { "branch" }) }
    var enforcement by remember(initialEnforcement) { mutableStateOf(initialEnforcement.ifBlank { "evaluate" }) }
    var includeRefs by remember(initialInclude) { mutableStateOf(initialInclude.ifBlank { "~DEFAULT_BRANCH" }) }
    var excludeRefs by remember(initialExclude) { mutableStateOf(initialExclude) }
    var rulesJson by remember(initialRulesJson) { mutableStateOf(initialRulesJson.ifBlank { "[{\"type\":\"non_fast_forward\"}]" }) }
    val rulesJsonValid = remember(rulesJson) { isJsonArray(rulesJson) }

    AiModuleAlertDialog(
        onDismissRequest = onDismiss,
        title = title.lowercase(),
        confirmButton = {
            AiModuleTextAction(
                label = confirmLabel.lowercase(),
                enabled = enabled && name.isNotBlank() && rulesJsonValid,
                onClick = { onSave(name, target, enforcement, refList(includeRefs), refList(excludeRefs), rulesJson) },
                tint = AiModuleTheme.colors.accent,
            )
        },
        dismissButton = {
            AiModuleTextAction(label = "cancel", onClick = onDismiss, tint = AiModuleTheme.colors.textSecondary)
        },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AiModuleTextField(name, { name = it }, label = "Name")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                listOf("branch", "tag", "push").forEach { value ->
                    GitHubSmallChoice(value, target == value) { target = value }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                listOf("active", "evaluate", "disabled").forEach { value ->
                    GitHubSmallChoice(value, enforcement == value) { enforcement = value }
                }
            }
            AiModuleTextField(includeRefs, { includeRefs = it }, label = "Include refs", minLines = 2, maxLines = 4)
            AiModuleTextField(excludeRefs, { excludeRefs = it }, label = "Exclude refs", minLines = 1, maxLines = 3)
            AiModuleTextField(rulesJson, { rulesJson = it }, label = "Rules JSON array", minLines = 5, maxLines = 10)
            if (!rulesJsonValid) Text("Rules must be a JSON array", fontSize = 12.sp, color = AiModuleTheme.colors.error, fontFamily = JetBrainsMono)
        }
    }
}

@Composable
private fun RulesetDetailSummaryCard(detail: GHRulesetDetail) {
    val color = rulesetColor(detail.enforcement)
    Column(Modifier.fillMaxWidth().ghGlassCard(14.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Rounded.Rule, null, Modifier.size(20.dp), tint = color)
            Column(Modifier.weight(1f)) {
                Text(detail.name.ifBlank { "Ruleset #${detail.id}" }, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = AiModuleTheme.colors.textPrimary)
                Text(cleanJoin(listOf(detail.target, detail.sourceType, detail.source)), fontSize = 11.sp, color = AiModuleTheme.colors.textMuted)
            }
            SecurityPill(detail.enforcement.ifBlank { "unknown" }, color)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            SecurityPill("${detail.rules.size} rules", AiModuleTheme.colors.textSecondary)
            SecurityPill("${detail.bypassActors.size} bypass actors", AiModuleTheme.colors.textSecondary)
            if (detail.updatedAt.isNotBlank()) SecurityPill("Updated ${detail.updatedAt.take(10)}", AiModuleTheme.colors.textMuted)
        }
    }
}

@Composable
private fun RulesetConditionsCard(detail: GHRulesetDetail) {
    Column(Modifier.fillMaxWidth().ghGlassCard(14.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Conditions", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = AiModuleTheme.colors.textPrimary)
        if (detail.refNameIncludes.isEmpty() && detail.refNameExcludes.isEmpty()) {
            Text("No ref name conditions returned", fontSize = 12.sp, color = AiModuleTheme.colors.textMuted)
        } else {
            if (detail.refNameIncludes.isNotEmpty()) RulesetValueList("Include", detail.refNameIncludes)
            if (detail.refNameExcludes.isNotEmpty()) RulesetValueList("Exclude", detail.refNameExcludes)
        }
    }
}

@Composable
private fun RulesetRulesCard(rules: List<GHRulesetRule>) {
    Column(Modifier.fillMaxWidth().ghGlassCard(14.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Rules", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = AiModuleTheme.colors.textPrimary)
        if (rules.isEmpty()) {
            Text("No rules returned", fontSize = 12.sp, color = AiModuleTheme.colors.textMuted)
        } else {
            rules.forEach { rule ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(AiModuleTheme.colors.background).padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(rule.type.replace('_', ' ').ifBlank { "rule" }, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = AiModuleTheme.colors.textPrimary)
                    rule.parameters.take(6).forEach { (key, value) ->
                        Text("$key: ${value.take(160)}", fontSize = 11.sp, color = AiModuleTheme.colors.textSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                    if (rule.parameters.size > 6) Text("+${rule.parameters.size - 6} more parameters", fontSize = 11.sp, color = AiModuleTheme.colors.textMuted)
                }
            }
        }
    }
}

@Composable
private fun RulesetBypassActorsCard(actors: List<GHRulesetBypassActor>) {
    Column(Modifier.fillMaxWidth().ghGlassCard(14.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Bypass actors", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = AiModuleTheme.colors.textPrimary)
        if (actors.isEmpty()) {
            Text("No bypass actors", fontSize = 12.sp, color = AiModuleTheme.colors.textMuted)
        } else {
            actors.forEach { actor ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.Person, null, Modifier.size(16.dp), tint = AiModuleTheme.colors.textSecondary)
                    Text(actor.actorType.ifBlank { "actor" }, fontSize = 13.sp, color = AiModuleTheme.colors.textPrimary, modifier = Modifier.weight(1f))
                    SecurityPill(actor.bypassMode.ifBlank { "bypass" }, AiModuleTheme.colors.textSecondary)
                    if (actor.actorId > 0) Text("#${actor.actorId}", fontSize = 11.sp, color = AiModuleTheme.colors.textMuted)
                }
            }
        }
    }
}

@Composable
private fun RuleSuitesCard(suites: List<GHRuleSuite>, onOpen: (GHRuleSuite) -> Unit) {
    Column(Modifier.fillMaxWidth().ghGlassCard(14.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Recent rule suites", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = AiModuleTheme.colors.textPrimary)
        if (suites.isEmpty()) {
            Text("No rule suites returned", fontSize = 12.sp, color = AiModuleTheme.colors.textMuted)
        } else {
            suites.take(12).forEach { suite ->
                val color = ruleSuiteColor(suite)
                Column(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(AiModuleTheme.colors.background)
                        .clickable { onOpen(suite) }
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SecurityPill(ruleSuiteLabel(suite), color)
                        Text(suite.ref.substringAfterLast('/').ifBlank { "ref" }, fontSize = 12.sp, color = AiModuleTheme.colors.textPrimary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (suite.createdAt.isNotBlank()) Text(suite.createdAt.take(10), fontSize = 11.sp, color = AiModuleTheme.colors.textMuted)
                    }
                    val meta = cleanJoin(listOf(suite.actor, suite.afterSha.take(7), suite.evaluationResult))
                    if (meta.isNotBlank()) Text(meta, fontSize = 11.sp, color = AiModuleTheme.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun RuleSuiteDetailDialog(suite: GHRuleSuite, onDismiss: () -> Unit) {
    AiModuleAlertDialog(
        onDismissRequest = onDismiss,
        title = "rule suite #${suite.id}",
        confirmButton = {
            AiModuleTextAction(label = "close", onClick = onDismiss, tint = AiModuleTheme.colors.accent)
        },
        dismissButton = {},
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                SecurityPill(ruleSuiteLabel(suite), ruleSuiteColor(suite))
                suite.status.takeIf { it.isNotBlank() }?.let { SecurityPill(it, AiModuleTheme.colors.textSecondary) }
                suite.evaluationResult.takeIf { it.isNotBlank() }?.let { SecurityPill(it, AiModuleTheme.colors.textSecondary) }
            }
            SecurityDetailLine("Actor", suite.actor)
            SecurityDetailLine("Ref", suite.ref)
            SecurityDetailLine("Before SHA", suite.beforeSha)
            SecurityDetailLine("After SHA", suite.afterSha)
            SecurityDetailLine("Status", suite.status)
            SecurityDetailLine("Result", suite.result)
            SecurityDetailLine("Evaluation", suite.evaluationResult)
            SecurityDetailLine("Created", suite.createdAt.take(19).replace('T', ' '))
            SecurityDetailLine("Updated", suite.updatedAt.take(19).replace('T', ' '))
        }
    }
}

@Composable
private fun RulesetValueList(label: String, values: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, fontSize = 12.sp, color = AiModuleTheme.colors.textMuted, fontWeight = FontWeight.Medium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            values.forEach { value -> SecurityPill(value, AiModuleTheme.colors.textSecondary) }
        }
    }
}

private fun refList(value: String): List<String> =
    value.split('\n', ',').map { it.trim() }.filter { it.isNotBlank() }

private fun isJsonArray(value: String): Boolean =
    try {
        JSONArray(value)
        true
    } catch (_: Exception) {
        false
    }

private fun rulesetRulesJson(rules: List<GHRulesetRule>): String {
    val arr = JSONArray()
    rules.forEach { rule ->
        arr.put(JSONObject().apply {
            put("type", rule.type)
            if (rule.parameters.isNotEmpty()) {
                put("parameters", JSONObject().apply {
                    rule.parameters.forEach { (key, value) -> put(key, parseRulesetParameterValue(value)) }
                })
            }
        })
    }
    return arr.toString(2)
}

private fun parseRulesetParameterValue(value: String): Any =
    try {
        JSONObject(value)
    } catch (_: Exception) {
        try {
            JSONArray(value)
        } catch (_: Exception) {
            when {
                value.equals("true", ignoreCase = true) -> true
                value.equals("false", ignoreCase = true) -> false
                value.toLongOrNull() != null -> value.toLong()
                value.toDoubleOrNull() != null -> value.toDouble()
                else -> value
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
    var advisories by remember { mutableStateOf<List<GHRepositorySecurityAdvisory>>(emptyList()) }
    var communityProfile by remember { mutableStateOf<GHCommunityProfile?>(null) }
    var settings by remember { mutableStateOf<GHRepositorySecuritySettings?>(null) }
    var loading by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    var severityFilter by remember { mutableStateOf("all") }
    var stateFilter by remember { mutableStateOf("open") }
    var selectedTab by remember { mutableStateOf("Dependabot") }
    var selectedDependabotAlert by remember { mutableStateOf<GHDependabotAlert?>(null) }
    var selectedCodeAlert by remember { mutableStateOf<GHCodeScanningAlert?>(null) }
    var selectedSecretAlert by remember { mutableStateOf<GHSecretScanningAlert?>(null) }
    var selectedAdvisory by remember { mutableStateOf<GHRepositorySecurityAdvisory?>(null) }
    var settingsActionInFlight by remember { mutableStateOf(false) }

    fun loadAlerts() {
        loading = true
        scope.launch {
            when (selectedTab) {
                "Code" -> codeAlerts = GitHubManager.getCodeScanningAlerts(context, repoOwner, repoName)
                "Secrets" -> secretAlerts = GitHubManager.getSecretScanningAlerts(context, repoOwner, repoName)
                "Advisories" -> advisories = GitHubManager.getRepositorySecurityAdvisories(context, repoOwner, repoName)
                "Community" -> communityProfile = GitHubManager.getCommunityProfile(context, repoOwner, repoName)
                "Settings" -> settings = GitHubManager.getRepositorySecuritySettings(context, repoOwner, repoName)
                else -> alerts = GitHubManager.getDependabotAlerts(context, repoOwner, repoName)
            }
            loading = false
        }
    }

    LaunchedEffect(repoOwner, repoName, selectedTab) { loadAlerts() }

    Column(Modifier.fillMaxSize().background(AiModuleTheme.colors.background)) {
        AiModulePageBar(
            title = "> security",
            subtitle = "$repoOwner/$repoName - ${selectedTab.lowercase()}",
            onBack = onBack,
            trailing = {
                IconButton(onClick = { loadAlerts() }, enabled = !loading, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp), tint = AiModuleTheme.colors.accent)
                }
            },
        )

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AiModuleTheme.colors.accent, modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
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
                        "Advisories" -> AdvisorySummaryCard(advisories)
                        "Community" -> CommunityProfileCard(communityProfile) {
                            openGitHubSecurityUrl(context, communityProfile?.documentationUrl.orEmpty())
                        }
                        "Settings" -> SecuritySettingsCard(
                            settings = settings,
                            actionInFlight = settingsActionInFlight,
                            onToggleAutomated = { enabled ->
                                settingsActionInFlight = true
                                scope.launch {
                                    GitHubManager.setAutomatedSecurityFixes(context, repoOwner, repoName, enabled)
                                    settings = GitHubManager.getRepositorySecuritySettings(context, repoOwner, repoName)
                                    settingsActionInFlight = false
                                }
                            },
                            onToggleVulnerabilityAlerts = { enabled ->
                                settingsActionInFlight = true
                                scope.launch {
                                    GitHubManager.setVulnerabilityAlerts(context, repoOwner, repoName, enabled)
                                    settings = GitHubManager.getRepositorySecuritySettings(context, repoOwner, repoName)
                                    settingsActionInFlight = false
                                }
                            },
                            onTogglePrivateReporting = { enabled ->
                                settingsActionInFlight = true
                                scope.launch {
                                    GitHubManager.setPrivateVulnerabilityReporting(context, repoOwner, repoName, enabled)
                                    settings = GitHubManager.getRepositorySecuritySettings(context, repoOwner, repoName)
                                    settingsActionInFlight = false
                                }
                            }
                        )
                        else -> SecuritySummaryCard(alerts)
                    }
                }
                val searchableTab = selectedTab != "Settings" && selectedTab != "Community"
                if (searchableTab) item {
                    AiModuleSearchField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = securitySearchLabel(selectedTab),
                    )
                }
                if (searchableTab) item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        ALERT_STATES.forEach { state ->
                            GitHubSmallChoice(label = state.replaceFirstChar { it.uppercase() }, selected = stateFilter == state) {
                                stateFilter = state
                            }
                        }
                    }
                }
                if (selectedTab != "Secrets" && selectedTab != "Settings" && selectedTab != "Community") item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        ALERT_SEVERITIES.forEach { severity ->
                            GitHubSmallChoice(label = severity.replaceFirstChar { it.uppercase() }, selected = severityFilter == severity) {
                                severityFilter = severity
                            }
                        }
                    }
                }
                when (selectedTab) {
                    "Settings" -> {
                        item { EmptySecurityResult(false, "", "Security settings changes require repository admin permissions") }
                    }
                    "Community" -> {
                        item { CommunityChecklistCard(communityProfile) }
                    }
                    "Advisories" -> {
                        val visibleAdvisories = advisories.filter { advisory ->
                            (severityFilter == "all" || advisory.severity.equals(severityFilter, ignoreCase = true)) &&
                                (stateFilter == "all" || advisory.state.equals(stateFilter, ignoreCase = true)) &&
                                advisoryMatches(advisory, query)
                        }
                        items(visibleAdvisories, key = { it.ghsaId.ifBlank { it.url } }) { advisory ->
                            RepositoryAdvisoryCard(
                                advisory = advisory,
                                onDetails = {
                                    selectedAdvisory = advisory
                                    if (advisory.ghsaId.isNotBlank()) {
                                        scope.launch {
                                            selectedAdvisory = GitHubManager.getRepositorySecurityAdvisory(context, repoOwner, repoName, advisory.ghsaId) ?: advisory
                                        }
                                    }
                                },
                                onOpen = { openGitHubSecurityUrl(context, advisory.htmlUrl.ifBlank { advisory.url }) }
                            )
                        }
                        if (visibleAdvisories.isEmpty()) {
                            item { EmptySecurityResult(advisories.isEmpty(), "No repository security advisories", "No matching advisories") }
                        }
                    }
                    "Code" -> {
                        val visibleAlerts = codeAlerts.filter { alert ->
                            (severityFilter == "all" || alert.severity.equals(severityFilter, ignoreCase = true)) &&
                                (stateFilter == "all" || alert.state.equals(stateFilter, ignoreCase = true)) &&
                                codeAlertMatches(alert, query)
                        }
                        items(visibleAlerts, key = { it.number }) { alert ->
                            CodeScanningAlertCard(
                                alert,
                                onOpen = { openGitHubSecurityUrl(context, alert.htmlUrl) },
                                onDetails = {
                                    selectedCodeAlert = alert
                                    scope.launch {
                                        selectedCodeAlert = GitHubManager.getCodeScanningAlert(context, repoOwner, repoName, alert.number) ?: alert
                                    }
                                }
                            )
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
                            SecretScanningAlertCard(
                                alert,
                                onOpen = { openGitHubSecurityUrl(context, alert.htmlUrl) },
                                onDetails = {
                                    selectedSecretAlert = alert
                                    scope.launch {
                                        selectedSecretAlert = GitHubManager.getSecretScanningAlert(context, repoOwner, repoName, alert.number) ?: alert
                                    }
                                }
                            )
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
                            AlertCard(
                                alert = alert,
                                onDetails = {
                                    selectedDependabotAlert = alert
                                    scope.launch {
                                        selectedDependabotAlert = GitHubManager.getDependabotAlert(context, repoOwner, repoName, alert.number) ?: alert
                                    }
                                },
                                onOpen = { openGitHubSecurityUrl(context, alert.htmlUrl) }
                            )
                        }
                        if (visibleAlerts.isEmpty()) {
                            item { EmptySecurityResult(alerts.isEmpty(), "No Dependabot alerts", "No matching alerts") }
                        }
                    }
                }
            }
        }
    }

    selectedDependabotAlert?.let { alert ->
        DependabotDetailDialog(alert, onDismiss = { selectedDependabotAlert = null }) {
            openGitHubSecurityUrl(context, alert.htmlUrl)
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
    selectedAdvisory?.let { advisory ->
        RepositoryAdvisoryDetailDialog(advisory, onDismiss = { selectedAdvisory = null }) {
            openGitHubSecurityUrl(context, advisory.htmlUrl.ifBlank { advisory.url })
        }
    }
}

@Composable
private fun SecuritySummaryCard(alerts: List<GHDependabotAlert>) {
    val open = alerts.count { it.state.equals("open", true) }
    val criticalHigh = alerts.count { it.severity.equals("critical", true) || it.severity.equals("high", true) }
    Column(Modifier.fillMaxWidth().ghGlassCard(14.dp).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Rounded.Security, null, Modifier.size(20.dp), tint = if (criticalHigh > 0) Color(0xFFFF3B30) else AiModuleTheme.colors.accent)
            Text("Dependabot alerts", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = AiModuleTheme.colors.textPrimary, modifier = Modifier.weight(1f))
            Text("$open open", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AiModuleTheme.colors.textPrimary)
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
    Column(Modifier.fillMaxWidth().ghGlassCard(14.dp).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Rounded.BugReport, null, Modifier.size(20.dp), tint = if (highRisk > 0) Color(0xFFFF3B30) else AiModuleTheme.colors.accent)
            Text("Code scanning alerts", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = AiModuleTheme.colors.textPrimary, modifier = Modifier.weight(1f))
            Text("$open open", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AiModuleTheme.colors.textPrimary)
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
    Column(Modifier.fillMaxWidth().ghGlassCard(14.dp).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Rounded.VpnKey, null, Modifier.size(20.dp), tint = if (open > 0) Color(0xFFFF3B30) else AiModuleTheme.colors.accent)
            Text("Secret scanning alerts", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = AiModuleTheme.colors.textPrimary, modifier = Modifier.weight(1f))
            Text("$open open", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AiModuleTheme.colors.textPrimary)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            SecurityPill("Public $public", if (public > 0) Color(0xFFFF3B30) else AiModuleTheme.colors.textMuted)
            SecurityPill("Resolved ${alerts.count { it.state.equals("resolved", true) }}", Color(0xFF34C759))
            SecurityPill("Bypassed ${alerts.count { it.pushProtectionBypassed }}", Color(0xFFFF9500))
        }
    }
}

@Composable
private fun AdvisorySummaryCard(advisories: List<GHRepositorySecurityAdvisory>) {
    val open = advisories.count { it.state.equals("draft", true) || it.state.equals("published", true) }
    val highRisk = advisories.count { it.severity.equals("critical", true) || it.severity.equals("high", true) }
    Column(Modifier.fillMaxWidth().ghGlassCard(14.dp).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Rounded.GppMaybe, null, Modifier.size(20.dp), tint = if (highRisk > 0) Color(0xFFFF3B30) else AiModuleTheme.colors.accent)
            Text("Security advisories", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = AiModuleTheme.colors.textPrimary, modifier = Modifier.weight(1f))
            Text("$open active", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AiModuleTheme.colors.textPrimary)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            SecurityPill("Critical ${advisories.count { it.severity.equals("critical", true) }}", Color(0xFFFF3B30))
            SecurityPill("High ${advisories.count { it.severity.equals("high", true) }}", Color(0xFFFF3B30))
            SecurityPill("Published ${advisories.count { it.state.equals("published", true) }}", Color(0xFF34C759))
            SecurityPill("Draft ${advisories.count { it.state.equals("draft", true) }}", Color(0xFFFF9500))
        }
    }
}

@Composable
private fun SecuritySettingsCard(
    settings: GHRepositorySecuritySettings?,
    actionInFlight: Boolean,
    onToggleAutomated: (Boolean) -> Unit,
    onToggleVulnerabilityAlerts: (Boolean) -> Unit,
    onTogglePrivateReporting: (Boolean) -> Unit
) {
    Column(Modifier.fillMaxWidth().ghGlassCard(14.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Rounded.AdminPanelSettings, null, Modifier.size(20.dp), tint = AiModuleTheme.colors.accent)
            Column(Modifier.weight(1f)) {
                Text("Security controls", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = AiModuleTheme.colors.textPrimary)
                Text("Repository admin permissions required", fontSize = 11.sp, color = AiModuleTheme.colors.textMuted)
            }
        }
        if (settings == null) {
            Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AiModuleTheme.colors.accent, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        } else {
            SecurityToggleRow(
                title = "Dependency graph alerts",
                subtitle = "Enable vulnerable dependency alerts",
                checked = settings.vulnerabilityAlerts,
                enabled = !actionInFlight,
                onToggle = onToggleVulnerabilityAlerts
            )
            SecurityToggleRow(
                title = "Dependabot security updates",
                subtitle = if (settings.automatedSecurityFixesPaused) "Enabled but paused" else "Automatic security update pull requests",
                checked = settings.automatedSecurityFixes,
                enabled = !actionInFlight,
                onToggle = onToggleAutomated
            )
            SecurityToggleRow(
                title = "Private vulnerability reporting",
                subtitle = "Allow private vulnerability reports",
                checked = settings.privateVulnerabilityReporting,
                enabled = !actionInFlight,
                onToggle = onTogglePrivateReporting
            )
        }
    }
}

@Composable
private fun CommunityProfileCard(profile: GHCommunityProfile?, onOpenDocs: () -> Unit) {
    val health = profile?.healthPercentage ?: 0
    val healthColor = when {
        health >= 80 -> Color(0xFF34C759)
        health >= 50 -> Color(0xFFFF9500)
        else -> Color(0xFFFF3B30)
    }
    Column(Modifier.fillMaxWidth().ghGlassCard(14.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Rounded.FactCheck, null, Modifier.size(20.dp), tint = healthColor)
            Column(Modifier.weight(1f)) {
                Text("Community profile", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = AiModuleTheme.colors.textPrimary)
                Text(profile?.description?.takeIf { it.isNotBlank() } ?: "Repository health and community files", fontSize = 11.sp, color = AiModuleTheme.colors.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text("$health%", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = healthColor)
        }
        LinearProgressIndicator(
            progress = { (health.coerceIn(0, 100) / 100f) },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(6.dp)),
            color = healthColor,
            trackColor = AiModuleTheme.colors.background
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            SecurityPill("Present ${profile?.files?.count { it.present } ?: 0}", Color(0xFF34C759))
            SecurityPill("Missing ${profile?.files?.count { !it.present } ?: 0}", AiModuleTheme.colors.textMuted)
            profile?.updatedAt?.takeIf { it.isNotBlank() }?.take(10)?.let { SecurityPill("Updated $it", AiModuleTheme.colors.textSecondary) }
        }
        if (!profile?.documentationUrl.isNullOrBlank()) {
            TextButton(onClick = onOpenDocs, contentPadding = PaddingValues(horizontal = 0.dp)) {
                Icon(Icons.Rounded.OpenInNew, null, Modifier.size(16.dp), tint = AiModuleTheme.colors.accent)
                Spacer(Modifier.width(6.dp))
                Text("Open documentation", color = AiModuleTheme.colors.accent, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun CommunityChecklistCard(profile: GHCommunityProfile?) {
    Column(Modifier.fillMaxWidth().ghGlassCard(14.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Community checklist", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = AiModuleTheme.colors.textPrimary)
        val files = profile?.files.orEmpty()
        if (profile == null) {
            Text("Community profile is unavailable", fontSize = 12.sp, color = AiModuleTheme.colors.textMuted)
        } else if (files.isEmpty()) {
            Text("No community file metadata returned", fontSize = 12.sp, color = AiModuleTheme.colors.textMuted)
        } else {
            files.sortedBy { !it.present }.forEach { file ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(AiModuleTheme.colors.background).padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(if (file.present) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null, Modifier.size(18.dp), tint = if (file.present) Color(0xFF34C759) else AiModuleTheme.colors.textMuted)
                    Column(Modifier.weight(1f)) {
                        Text(file.name.replace('_', ' '), fontSize = 13.sp, color = AiModuleTheme.colors.textPrimary, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(file.key, fontSize = 11.sp, color = AiModuleTheme.colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    SecurityPill(if (file.present) "present" else "missing", if (file.present) Color(0xFF34C759) else AiModuleTheme.colors.textMuted)
                }
            }
        }
    }
}

@Composable
private fun SecurityToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(AiModuleTheme.colors.background).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(if (checked) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null, Modifier.size(20.dp), tint = if (checked) Color(0xFF34C759) else AiModuleTheme.colors.textSecondary)
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = AiModuleTheme.colors.textPrimary)
            Text(subtitle, fontSize = 12.sp, color = AiModuleTheme.colors.textMuted)
        }
        Switch(checked = checked, onCheckedChange = onToggle, enabled = enabled, colors = SwitchDefaults.colors(checkedTrackColor = AiModuleTheme.colors.accent))
    }
}

@Composable
private fun AlertCard(alert: GHDependabotAlert, onDetails: () -> Unit, onOpen: () -> Unit) {
    val severityColor = alertSeverityColor(alert.severity)
    Column(
        Modifier.fillMaxWidth().ghGlassCard(14.dp).padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Rounded.Security, null, Modifier.size(20.dp), tint = severityColor)
            Column(Modifier.weight(1f)) {
                Text(alert.packageName.ifBlank { "Dependency alert #${alert.number}" }, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AiModuleTheme.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(cleanJoin(listOf(alert.ecosystem, alert.manifestPath)), fontSize = 11.sp, color = AiModuleTheme.colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onDetails) {
                Icon(Icons.Rounded.Article, null, Modifier.size(18.dp), tint = AiModuleTheme.colors.textSecondary)
            }
            IconButton(onClick = onOpen, enabled = alert.htmlUrl.isNotBlank()) {
                Icon(Icons.Rounded.OpenInNew, null, Modifier.size(18.dp), tint = AiModuleTheme.colors.accent)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            SecurityPill(alert.severity.ifBlank { "unknown" }, severityColor)
            SecurityPill(alert.state.ifBlank { "unknown" }, alertStateColor(alert.state))
            alert.ghsaId.takeIf { it.isNotBlank() }?.let { SecurityPill(it, AiModuleTheme.colors.textSecondary) }
            alert.cveId.takeIf { it.isNotBlank() }?.let { SecurityPill(it, AiModuleTheme.colors.textSecondary) }
        }
        if (alert.summary.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(alert.summary, fontSize = 13.sp, color = AiModuleTheme.colors.textPrimary, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        val detailLines = listOfNotNull(
            alert.vulnerableRequirements.takeIf { it.isNotBlank() }?.let { "Requires $it" },
            alert.fixedIn.takeIf { it.isNotEmpty() }?.joinToString(", ")?.let { "Fixed in $it" },
            alert.updatedAt.takeIf { it.isNotBlank() }?.take(10)?.let { "Updated $it" }
        )
        if (detailLines.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(detailLines.joinToString(" - "), fontSize = 11.sp, color = AiModuleTheme.colors.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun RepositoryAdvisoryCard(advisory: GHRepositorySecurityAdvisory, onDetails: () -> Unit, onOpen: () -> Unit) {
    val severityColor = alertSeverityColor(advisory.severity)
    Column(
        Modifier.fillMaxWidth().ghGlassCard(14.dp).padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Rounded.GppMaybe, null, Modifier.size(20.dp), tint = severityColor)
            Column(Modifier.weight(1f)) {
                Text(advisory.summary.ifBlank { advisory.ghsaId.ifBlank { "Repository advisory" } }, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AiModuleTheme.colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(cleanJoin(listOf(advisory.ghsaId, advisory.cveId)), fontSize = 11.sp, color = AiModuleTheme.colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onDetails) {
                Icon(Icons.Rounded.Article, null, Modifier.size(18.dp), tint = AiModuleTheme.colors.textSecondary)
            }
            IconButton(onClick = onOpen, enabled = advisory.htmlUrl.isNotBlank() || advisory.url.isNotBlank()) {
                Icon(Icons.Rounded.OpenInNew, null, Modifier.size(18.dp), tint = AiModuleTheme.colors.accent)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            SecurityPill(advisory.severity.ifBlank { "unknown" }, severityColor)
            SecurityPill(advisory.state.ifBlank { "unknown" }, alertStateColor(advisory.state))
            if (advisory.cvssScore > 0.0) SecurityPill("CVSS ${"%.1f".format(advisory.cvssScore)}", severityColor)
            advisory.cweIds.take(2).forEach { SecurityPill(it, AiModuleTheme.colors.textSecondary) }
        }
        if (advisory.description.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(advisory.description, fontSize = 13.sp, color = AiModuleTheme.colors.textPrimary, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        if (advisory.vulnerabilities.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            advisory.vulnerabilities.take(3).forEach { vulnerability ->
                Text(
                    cleanJoin(listOf(vulnerability.ecosystem, vulnerability.packageName, vulnerability.vulnerableRange, vulnerability.patchedVersions.takeIf { it.isNotBlank() }?.let { "patched $it" } ?: "")),
                    fontSize = 11.sp,
                    color = AiModuleTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun CodeScanningAlertCard(alert: GHCodeScanningAlert, onOpen: () -> Unit, onDetails: () -> Unit) {
    val severityColor = alertSeverityColor(alert.severity)
    Column(
        Modifier.fillMaxWidth().ghGlassCard(14.dp).padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Rounded.BugReport, null, Modifier.size(20.dp), tint = severityColor)
            Column(Modifier.weight(1f)) {
                Text(alert.ruleName.ifBlank { alert.ruleId.ifBlank { "Code alert #${alert.number}" } }, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AiModuleTheme.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(cleanJoin(listOf(alert.toolName, alert.pathWithLine())), fontSize = 11.sp, color = AiModuleTheme.colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onDetails) {
                Icon(Icons.Rounded.Article, null, Modifier.size(18.dp), tint = AiModuleTheme.colors.textSecondary)
            }
            IconButton(onClick = onOpen, enabled = alert.htmlUrl.isNotBlank()) {
                Icon(Icons.Rounded.OpenInNew, null, Modifier.size(18.dp), tint = AiModuleTheme.colors.accent)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            SecurityPill(alert.severity.ifBlank { "unknown" }, severityColor)
            SecurityPill(alert.state.ifBlank { "unknown" }, alertStateColor(alert.state))
            alert.category.takeIf { it.isNotBlank() }?.let { SecurityPill(it, AiModuleTheme.colors.textSecondary) }
            alert.ref.takeIf { it.isNotBlank() }?.substringAfterLast('/')?.let { SecurityPill(it, AiModuleTheme.colors.accent) }
        }
        val body = alert.message.ifBlank { alert.description }
        if (body.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(body, fontSize = 13.sp, color = AiModuleTheme.colors.textPrimary, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (alert.createdAt.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text("Created ${alert.createdAt.take(10)}", fontSize = 11.sp, color = AiModuleTheme.colors.textMuted)
        }
    }
}

@Composable
private fun SecretScanningAlertCard(alert: GHSecretScanningAlert, onOpen: () -> Unit, onDetails: () -> Unit) {
    val stateColor = alertStateColor(alert.state)
    Column(
        Modifier.fillMaxWidth().ghGlassCard(14.dp).padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Rounded.VpnKey, null, Modifier.size(20.dp), tint = stateColor)
            Column(Modifier.weight(1f)) {
                Text(alert.secretTypeDisplayName.ifBlank { alert.secretType.ifBlank { "Secret alert #${alert.number}" } }, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AiModuleTheme.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(maskSecret(alert.secret), fontSize = 11.sp, color = AiModuleTheme.colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onDetails) {
                Icon(Icons.Rounded.Article, null, Modifier.size(18.dp), tint = AiModuleTheme.colors.textSecondary)
            }
            IconButton(onClick = onOpen, enabled = alert.htmlUrl.isNotBlank()) {
                Icon(Icons.Rounded.OpenInNew, null, Modifier.size(18.dp), tint = AiModuleTheme.colors.accent)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            SecurityPill(alert.state.ifBlank { "unknown" }, stateColor)
            alert.validity.takeIf { it.isNotBlank() && it != "null" }?.let { SecurityPill(it, if (it == "active") Color(0xFFFF3B30) else AiModuleTheme.colors.textSecondary) }
            if (alert.public) SecurityPill("public", Color(0xFFFF3B30))
            if (alert.pushProtectionBypassed) SecurityPill("bypassed", Color(0xFFFF9500))
            alert.resolution.takeIf { it.isNotBlank() && it != "null" }?.let { SecurityPill(it, AiModuleTheme.colors.textSecondary) }
        }
        val dates = listOfNotNull(
            alert.createdAt.takeIf { it.isNotBlank() }?.take(10)?.let { "Created $it" },
            alert.resolvedAt.takeIf { it.isNotBlank() }?.take(10)?.let { "Resolved $it" }
        )
        if (dates.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(dates.joinToString(" - "), fontSize = 11.sp, color = AiModuleTheme.colors.textMuted)
        }
    }
}

@Composable
private fun CodeScanningDetailDialog(alert: GHCodeScanningAlert, onDismiss: () -> Unit, onOpen: () -> Unit) {
    AiModuleAlertDialog(
        onDismissRequest = onDismiss,
        title = "code alert #${alert.number}",
        confirmButton = {
            AiModuleTextAction(label = "open", enabled = alert.htmlUrl.isNotBlank(), onClick = onOpen, tint = AiModuleTheme.colors.accent)
        },
        dismissButton = {
            AiModuleTextAction(label = "close", onClick = onDismiss, tint = AiModuleTheme.colors.textSecondary)
        },
    ) {
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
    }
}

@Composable
private fun SecretScanningDetailDialog(alert: GHSecretScanningAlert, onDismiss: () -> Unit, onOpen: () -> Unit) {
    AiModuleAlertDialog(
        onDismissRequest = onDismiss,
        title = "secret alert #${alert.number}",
        confirmButton = {
            AiModuleTextAction(label = "open", enabled = alert.htmlUrl.isNotBlank(), onClick = onOpen, tint = AiModuleTheme.colors.accent)
        },
        dismissButton = {
            AiModuleTextAction(label = "close", onClick = onDismiss, tint = AiModuleTheme.colors.textSecondary)
        },
    ) {
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
    }
}

@Composable
private fun DependabotDetailDialog(alert: GHDependabotAlert, onDismiss: () -> Unit, onOpen: () -> Unit) {
    AiModuleAlertDialog(
        onDismissRequest = onDismiss,
        title = "dependabot alert #${alert.number}",
        confirmButton = {
            AiModuleTextAction(label = "open", enabled = alert.htmlUrl.isNotBlank(), onClick = onOpen, tint = AiModuleTheme.colors.accent)
        },
        dismissButton = {
            AiModuleTextAction(label = "close", onClick = onDismiss, tint = AiModuleTheme.colors.textSecondary)
        },
    ) {
        Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SecurityDetailLine("Package", cleanJoin(listOf(alert.ecosystem, alert.packageName)))
            SecurityDetailLine("Manifest", alert.manifestPath)
            SecurityDetailLine("Status", cleanJoin(listOf(alert.state, alert.severity)))
            SecurityDetailLine("Advisory", cleanJoin(listOf(alert.ghsaId, alert.cveId)))
            SecurityDetailLine("Summary", alert.summary)
            SecurityDetailLine("Description", alert.description)
            SecurityDetailLine("Vulnerable requirements", alert.vulnerableRequirements)
            SecurityDetailLine("Fixed in", alert.fixedIn.joinToString(", "))
            SecurityDetailLine("Created", alert.createdAt.take(19).replace('T', ' '))
            SecurityDetailLine("Updated", alert.updatedAt.take(19).replace('T', ' '))
        }
    }
}

@Composable
private fun RepositoryAdvisoryDetailDialog(advisory: GHRepositorySecurityAdvisory, onDismiss: () -> Unit, onOpen: () -> Unit) {
    AiModuleAlertDialog(
        onDismissRequest = onDismiss,
        title = advisory.ghsaId.ifBlank { "repository advisory" },
        confirmButton = {
            AiModuleTextAction(label = "open", enabled = advisory.htmlUrl.isNotBlank() || advisory.url.isNotBlank(), onClick = onOpen, tint = AiModuleTheme.colors.accent)
        },
        dismissButton = {
            AiModuleTextAction(label = "close", onClick = onDismiss, tint = AiModuleTheme.colors.textSecondary)
        },
    ) {
        Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SecurityDetailLine("Status", cleanJoin(listOf(advisory.state, advisory.severity)))
                SecurityDetailLine("CVE", advisory.cveId)
                SecurityDetailLine("Summary", advisory.summary)
                SecurityDetailLine("Description", advisory.description)
                SecurityDetailLine("CVSS", advisory.cvssScore.takeIf { it > 0.0 }?.let { "%.1f".format(it) } ?: "")
                SecurityDetailLine("CWE", advisory.cweIds.joinToString(", "))
                SecurityDetailLine("Published", advisory.publishedAt.take(19).replace('T', ' '))
                SecurityDetailLine("Updated", advisory.updatedAt.take(19).replace('T', ' '))
                SecurityDetailLine("Withdrawn", advisory.withdrawnAt.take(19).replace('T', ' '))
            if (advisory.vulnerabilities.isNotEmpty()) {
                Text("Vulnerabilities", fontSize = 12.sp, color = AiModuleTheme.colors.textMuted, fontWeight = FontWeight.Medium, fontFamily = JetBrainsMono)
                advisory.vulnerabilities.forEach { vulnerability ->
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(AiModuleTheme.colors.background).padding(8.dp)) {
                        Text(
                            cleanJoin(listOf(vulnerability.ecosystem, vulnerability.packageName, vulnerability.vulnerableRange, vulnerability.patchedVersions.takeIf { it.isNotBlank() }?.let { "patched $it" } ?: "")),
                            fontSize = 12.sp,
                            color = AiModuleTheme.colors.textPrimary,
                            fontFamily = JetBrainsMono,
                            lineHeight = 16.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SecurityDetailLine(label: String, value: String) {
    val cleanValue = value.trim().takeUnless { it.isBlank() || it.equals("null", true) } ?: return
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, fontSize = 11.sp, color = AiModuleTheme.colors.textMuted, fontWeight = FontWeight.Medium)
        Text(cleanValue, fontSize = 12.sp, color = AiModuleTheme.colors.textPrimary, lineHeight = 16.sp)
    }
}

@Composable
private fun EmptySecurityResult(emptySource: Boolean, emptyText: String, noMatchText: String) {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(if (emptySource) emptyText else noMatchText, fontSize = 14.sp, color = AiModuleTheme.colors.textMuted)
    }
}

@Composable
private fun GitHubSmallChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(8.dp))
            .background(if (selected) AiModuleTheme.colors.accent.copy(alpha = 0.14f) else AiModuleTheme.colors.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(label, fontSize = 12.sp, color = if (selected) AiModuleTheme.colors.accent else AiModuleTheme.colors.textSecondary, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
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

@Composable
private fun rulesetColor(enforcement: String): Color = when (enforcement.lowercase()) {
    "active" -> Color(0xFF34C759)
    "evaluate" -> Color(0xFFFF9500)
    "disabled" -> AiModuleTheme.colors.textMuted
    else -> AiModuleTheme.colors.textSecondary
}

private fun ruleSuiteLabel(suite: GHRuleSuite): String =
    suite.result.ifBlank { suite.status.ifBlank { suite.evaluationResult.ifBlank { "unknown" } } }

@Composable
private fun ruleSuiteColor(suite: GHRuleSuite): Color = when (ruleSuiteLabel(suite).lowercase()) {
    "pass", "passed", "success" -> Color(0xFF34C759)
    "fail", "failed", "failure", "error" -> Color(0xFFFF3B30)
    "bypass", "bypassed" -> Color(0xFFFF9500)
    "evaluate" -> Color(0xFFFF9500)
    else -> AiModuleTheme.colors.textSecondary
}

@Composable
private fun alertSeverityColor(severity: String): Color = when (severity.lowercase()) {
    "critical", "high", "error" -> Color(0xFFFF3B30)
    "medium", "warning" -> Color(0xFFFF9500)
    "low" -> Color(0xFF34C759)
    else -> AiModuleTheme.colors.textSecondary
}

@Composable
private fun alertStateColor(state: String): Color = when (state.lowercase()) {
    "open" -> Color(0xFFFF3B30)
    "fixed", "resolved" -> Color(0xFF34C759)
    "dismissed", "closed" -> AiModuleTheme.colors.textMuted
    else -> AiModuleTheme.colors.textSecondary
}

private fun cleanJoin(values: List<String>): String =
    values.filter { it.isNotBlank() && it != "null" }.joinToString(" - ").ifBlank { "Repository" }

private fun securitySearchLabel(tab: String): String = when (tab) {
    "Code" -> "Search rule, tool, path or ref"
    "Secrets" -> "Search secret type, state or resolution"
    "Advisories" -> "Search GHSA, CVE, summary or package"
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

private fun advisoryMatches(advisory: GHRepositorySecurityAdvisory, query: String): Boolean {
    val q = query.trim()
    return q.isBlank() ||
        advisory.ghsaId.contains(q, ignoreCase = true) ||
        advisory.cveId.contains(q, ignoreCase = true) ||
        advisory.summary.contains(q, ignoreCase = true) ||
        advisory.description.contains(q, ignoreCase = true) ||
        advisory.cweIds.any { it.contains(q, ignoreCase = true) } ||
        advisory.vulnerabilities.any {
            it.packageName.contains(q, ignoreCase = true) ||
                it.ecosystem.contains(q, ignoreCase = true) ||
                it.vulnerableRange.contains(q, ignoreCase = true) ||
                it.patchedVersions.contains(q, ignoreCase = true)
        }
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
