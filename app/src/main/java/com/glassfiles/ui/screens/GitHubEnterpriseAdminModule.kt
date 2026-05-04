package com.glassfiles.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glassfiles.data.github.GHActionRunner
import com.glassfiles.data.github.GHAuditLogEntry
import com.glassfiles.data.github.GHScimUser
import com.glassfiles.data.github.GHScimUsersPage
import com.glassfiles.data.github.GitHubManager
import com.glassfiles.ui.components.AiModuleSpinner
import com.glassfiles.ui.components.AiModuleText as Text
import com.glassfiles.ui.theme.AiModuleTheme
import com.glassfiles.ui.theme.GitHubErrorRed
import com.glassfiles.ui.theme.JetBrainsMono
import kotlinx.coroutines.launch

private enum class GitHubAdminTab { ENTERPRISE_RUNNERS, AUDIT_LOG, SCIM_USERS }

@Composable
internal fun GitHubEnterpriseAdminScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val palette = AiModuleTheme.colors
    var tab by remember { mutableStateOf(GitHubAdminTab.ENTERPRISE_RUNNERS) }
    var enterprise by rememberSaveable { mutableStateOf("") }
    var org by rememberSaveable { mutableStateOf("") }
    var auditPhrase by rememberSaveable { mutableStateOf("") }
    var scimStart by rememberSaveable { mutableIntStateOf(1) }
    var loading by remember { mutableStateOf(false) }
    var enterpriseRunners by remember { mutableStateOf<List<GHActionRunner>>(emptyList()) }
    var auditLog by remember { mutableStateOf<List<GHAuditLogEntry>>(emptyList()) }
    var scimPage by remember { mutableStateOf(GHScimUsersPage()) }
    var notice by remember { mutableStateOf("") }

    fun loadCurrent() {
        loading = true
        notice = ""
        scope.launch {
            when (tab) {
                GitHubAdminTab.ENTERPRISE_RUNNERS -> {
                    enterpriseRunners = GitHubManager.getEnterpriseRunners(context, enterprise)
                    notice = if (enterpriseRunners.isEmpty()) "no enterprise runners returned" else "enterprise runners=${enterpriseRunners.size}"
                }
                GitHubAdminTab.AUDIT_LOG -> {
                    auditLog = GitHubManager.getOrgAuditLog(context, org, auditPhrase)
                    notice = if (auditLog.isEmpty()) "no audit log entries returned" else "audit entries=${auditLog.size}"
                }
                GitHubAdminTab.SCIM_USERS -> {
                    scimPage = GitHubManager.getOrgScimUsers(context, org, startIndex = scimStart)
                    notice = scimPage.error.ifBlank { "scim users=${scimPage.users.size}/${scimPage.totalResults}" }
                }
            }
            loading = false
        }
    }

    GitHubScreenFrame(
        title = "> enterprise api",
        subtitle = "admin/enterprise endpoints",
        onBack = onBack,
        trailing = {
            GitHubTopBarAction(
                glyph = GhGlyphs.REFRESH,
                onClick = { loadCurrent() },
                tint = palette.accent,
                enabled = !loading,
                contentDescription = "load enterprise admin data",
            )
        },
    ) {
        Column(Modifier.fillMaxSize().background(palette.background)) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GitHubAdminTab.entries.forEach { item ->
                    GitHubTerminalTab(item.name.lowercase().replace('_', ' '), tab == item) { tab = item }
                }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GitHubAdminInput(
                    label = "enterprise",
                    value = enterprise,
                    onValueChange = { enterprise = it },
                    placeholder = "enterprise slug",
                    enabled = tab == GitHubAdminTab.ENTERPRISE_RUNNERS,
                )
                GitHubAdminInput(
                    label = "org",
                    value = org,
                    onValueChange = { org = it },
                    placeholder = "organization login",
                    enabled = tab != GitHubAdminTab.ENTERPRISE_RUNNERS,
                )
                if (tab == GitHubAdminTab.AUDIT_LOG) {
                    GitHubAdminInput("phrase", auditPhrase, { auditPhrase = it }, "optional audit phrase")
                }
                if (tab == GitHubAdminTab.SCIM_USERS) {
                    GitHubAdminInput("start index", scimStart.toString(), { scimStart = it.filter { ch -> ch.isDigit() }.toIntOrNull() ?: 1 }, "1")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    GitHubTerminalButton(if (loading) "loading..." else "load", onClick = { loadCurrent() }, color = palette.accent, enabled = !loading)
                    if (notice.isNotBlank()) Text(notice, color = palette.textMuted, fontFamily = JetBrainsMono, fontSize = 11.sp)
                }
                Text(
                    "These endpoints require matching admin/enterprise scopes. Empty results usually mean the token is not eligible.",
                    color = palette.textMuted,
                    fontFamily = JetBrainsMono,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                )
            }
            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { AiModuleSpinner(label = "loading admin api...") }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    when (tab) {
                        GitHubAdminTab.ENTERPRISE_RUNNERS -> {
                            if (enterpriseRunners.isEmpty()) item { GitHubAdminEmpty("no enterprise runners loaded") }
                            items(enterpriseRunners) { runner -> EnterpriseRunnerCard(runner) }
                        }
                        GitHubAdminTab.AUDIT_LOG -> {
                            if (auditLog.isEmpty()) item { GitHubAdminEmpty("no audit log entries loaded") }
                            items(auditLog) { entry -> AuditLogCard(entry) }
                        }
                        GitHubAdminTab.SCIM_USERS -> {
                            if (scimPage.error.isNotBlank()) item { GitHubAdminEmpty(scimPage.error.take(220), error = true) }
                            if (scimPage.users.isEmpty() && scimPage.error.isBlank()) item { GitHubAdminEmpty("no scim users loaded") }
                            items(scimPage.users) { user -> ScimUserCard(user) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GitHubAdminInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    enabled: Boolean = true,
) {
    val palette = AiModuleTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = if (enabled) palette.textMuted else palette.textMuted.copy(alpha = 0.45f), fontFamily = JetBrainsMono, fontSize = 11.sp)
        GitHubTerminalTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder,
            singleLine = true,
            minHeight = 38.dp,
            modifier = if (enabled) Modifier else Modifier.background(palette.surface.copy(alpha = 0.35f)),
        )
    }
}

@Composable
private fun EnterpriseRunnerCard(runner: GHActionRunner) {
    GitHubAdminCard(runner.name.ifBlank { "runner #${runner.id}" }) {
        GitHubAdminKv("status", "${runner.status}${if (runner.busy) " busy" else ""}")
        GitHubAdminKv("os", runner.os)
        GitHubAdminKv("labels", runner.labels.joinToString(", "))
    }
}

@Composable
private fun AuditLogCard(entry: GHAuditLogEntry) {
    GitHubAdminCard(entry.action.ifBlank { "audit event" }) {
        GitHubAdminKv("actor", entry.actor)
        GitHubAdminKv("created", entry.createdAt)
        GitHubAdminKv("org", entry.org)
        GitHubAdminKv("repo", entry.repo)
        GitHubAdminKv("user", entry.user)
        GitHubAdminKv("operation", entry.operationType)
        GitHubAdminKv("transport", entry.transportProtocol)
        GitHubAdminKv("id", entry.id)
    }
}

@Composable
private fun ScimUserCard(user: GHScimUser) {
    GitHubAdminCard(user.userName.ifBlank { user.displayName.ifBlank { user.id } }) {
        GitHubAdminKv("name", listOf(user.givenName, user.familyName).filter { it.isNotBlank() }.joinToString(" "))
        GitHubAdminKv("display", user.displayName)
        GitHubAdminKv("active", user.active.toString())
        GitHubAdminKv("external", user.externalId)
        GitHubAdminKv("emails", user.emails.joinToString(", "))
        GitHubAdminKv("id", user.id)
    }
}

@Composable
private fun GitHubAdminCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    val palette = AiModuleTheme.colors
    Column(
        Modifier.fillMaxWidth().border(1.dp, palette.border, RoundedCornerShape(4.dp)).background(palette.surface).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(title, color = palette.textPrimary, fontFamily = JetBrainsMono, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        content()
    }
}

@Composable
private fun GitHubAdminKv(label: String, value: String) {
    if (value.isBlank()) return
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = AiModuleTheme.colors.textMuted, fontFamily = JetBrainsMono, fontSize = 10.sp, modifier = Modifier.width(82.dp))
        Text(value, color = AiModuleTheme.colors.textSecondary, fontFamily = JetBrainsMono, fontSize = 11.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun GitHubAdminEmpty(text: String, error: Boolean = false) {
    val palette = AiModuleTheme.colors
    Box(
        Modifier.fillMaxWidth().heightIn(min = 76.dp).border(1.dp, if (error) GitHubErrorRed else palette.border, RoundedCornerShape(4.dp)).background(palette.surface).padding(14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            if (error) "! $text" else "// $text",
            color = if (error) GitHubErrorRed else palette.textMuted,
            fontFamily = JetBrainsMono,
            fontSize = 12.sp,
        )
    }
}
