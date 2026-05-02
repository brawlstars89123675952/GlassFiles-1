package com.glassfiles.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glassfiles.data.github.GHOrgTeam
import com.glassfiles.ui.theme.AiModuleTheme
import com.glassfiles.data.github.GHRepoTeam
import com.glassfiles.data.github.GitHubManager
import com.glassfiles.ui.components.AiModuleAlertDialog
import com.glassfiles.ui.components.AiModuleIcon as Icon
import com.glassfiles.ui.components.AiModuleIconButton as IconButton
import com.glassfiles.ui.components.AiModulePageBar
import com.glassfiles.ui.components.AiModuleHairline
import com.glassfiles.ui.components.AiModuleSpinner
import com.glassfiles.ui.components.AiModuleText as Text
import com.glassfiles.ui.components.AiModuleTextAction
import com.glassfiles.ui.components.AiModuleTextField
import kotlinx.coroutines.launch

private val TEAM_PERMISSIONS = listOf(
    "pull" to "Read",
    "triage" to "Triage",
    "push" to "Write",
    "maintain" to "Maintain",
    "admin" to "Admin"
)

@Composable
internal fun RepoTeamsScreen(
    repoOwner: String,
    repoName: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var repoTeams by remember { mutableStateOf<List<GHRepoTeam>>(emptyList()) }
    var orgTeams by remember { mutableStateOf<List<GHOrgTeam>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedTeamSlug by remember { mutableStateOf("") }
    var selectedPermission by remember { mutableStateOf("push") }
    var teamToEdit by remember { mutableStateOf<GHRepoTeam?>(null) }
    var teamToRemove by remember { mutableStateOf<GHRepoTeam?>(null) }
    var actionInFlight by remember { mutableStateOf(false) }

    fun loadTeams() {
        loading = true
        scope.launch {
            val loadedRepoTeams = GitHubManager.getRepoTeams(context, repoOwner, repoName)
            val loadedOrgTeams = GitHubManager.getOrgTeams(context, repoOwner)
            repoTeams = loadedRepoTeams
            orgTeams = loadedOrgTeams
            if (selectedTeamSlug.isBlank()) {
                selectedTeamSlug = loadedOrgTeams.firstOrNull { orgTeam -> loadedRepoTeams.none { it.slug == orgTeam.slug } }?.slug.orEmpty()
            }
            loading = false
        }
    }

    LaunchedEffect(repoOwner, repoName) { loadTeams() }

    GitHubScreenFrame(
        title = "> teams",
        subtitle = "$repoOwner/$repoName",
        onBack = onBack,
        trailing = {
            GitHubTopBarAction(
                glyph = GhGlyphs.PLUS,
                onClick = { showAddDialog = true },
                tint = AiModuleTheme.colors.accent,
                contentDescription = "add team",
            )
        },
    ) {

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                AiModuleSpinner(label = "loading…")
            }
        } else {
            val visibleTeams = repoTeams.filter {
                query.isBlank() ||
                    it.name.contains(query, ignoreCase = true) ||
                    it.slug.contains(query, ignoreCase = true) ||
                    teamPermissionLabel(it.permission).contains(query, ignoreCase = true)
            }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { RepoTeamsSummaryCard(repoTeams, orgTeams) }
                item {
                    AiModuleTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = "Search teams",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        leading = { Icon(Icons.Rounded.Search, null, Modifier.size(18.dp), tint = AiModuleTheme.colors.textSecondary) }
                    )
                }
                items(visibleTeams) { team ->
                    RepoTeamCard(
                        team = team,
                        onPermissionChange = { teamToEdit = team },
                        onRemove = { teamToRemove = team }
                    )
                }
                if (visibleTeams.isEmpty()) {
                    item {
                        EmptyTeamsCard(
                            message = if (repoTeams.isEmpty()) "No teams have repository access yet" else "No matching teams"
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        val availableTeams = orgTeams.filter { orgTeam -> repoTeams.none { it.slug == orgTeam.slug } }
        val selectedAvailableTeam = availableTeams.firstOrNull { it.slug == selectedTeamSlug }
        AiModuleAlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = "Add Team",
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (availableTeams.isEmpty()) {
                        Text(
                            if (orgTeams.isEmpty()) "No organization teams were returned for $repoOwner."
                            else "All organization teams already have access to this repository.",
                            fontSize = 13.sp,
                            color = AiModuleTheme.colors.textSecondary
                        )
                    } else {
                        Text("Team", fontSize = 12.sp, color = AiModuleTheme.colors.textSecondary)
                        Column(
                            Modifier.height(260.dp).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            availableTeams.forEach { team ->
                                SelectableTeamRow(
                                    team = team,
                                    selected = team.slug == selectedTeamSlug,
                                    onClick = { selectedTeamSlug = team.slug }
                                )
                            }
                        }
                        Text("Permission level", fontSize = 12.sp, color = AiModuleTheme.colors.textSecondary)
                        PermissionChipRow(selectedPermission) { selectedPermission = it }
                    }
                }
            },
            confirmButton = {
                AiModuleTextAction(
                    label = "add",
                    enabled = !actionInFlight && selectedAvailableTeam != null,
                    onClick = {
                        actionInFlight = true
                        scope.launch {
                            val ok = GitHubManager.addRepoTeam(context, repoOwner, selectedTeamSlug, repoOwner, repoName, selectedPermission)
                            Toast.makeText(context, if (ok) "Team added" else "Failed", Toast.LENGTH_SHORT).show()
                            actionInFlight = false
                            if (ok) {
                                showAddDialog = false
                                selectedTeamSlug = ""
                                loadTeams()
                            }
                        }
                    },
                )
            },
            dismissButton = {
                AiModuleTextAction(label = "cancel", onClick = { showAddDialog = false }, tint = AiModuleTheme.colors.textSecondary)
            }
        )
    }

    if (teamToEdit != null) {
        var editPermission by remember(teamToEdit?.slug) { mutableStateOf(normalizeTeamPermission(teamToEdit!!.permission)) }
        AiModuleAlertDialog(
            onDismissRequest = { teamToEdit = null },
            title = "Change Team Permission",
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(teamToEdit!!.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = AiModuleTheme.colors.textPrimary)
                    Text("@${teamToEdit!!.slug}", fontSize = 12.sp, color = AiModuleTheme.colors.textMuted)
                    PermissionChipRow(editPermission) { editPermission = it }
                }
            },
            confirmButton = {
                AiModuleTextAction(
                    label = "save",
                    enabled = !actionInFlight && editPermission != normalizeTeamPermission(teamToEdit!!.permission),
                    onClick = {
                        val team = teamToEdit ?: return@AiModuleTextAction
                        actionInFlight = true
                        scope.launch {
                            val ok = GitHubManager.updateRepoTeamPermission(context, repoOwner, team.slug, repoOwner, repoName, editPermission)
                            Toast.makeText(context, if (ok) "Updated" else "Failed", Toast.LENGTH_SHORT).show()
                            actionInFlight = false
                            teamToEdit = null
                            loadTeams()
                        }
                    },
                )
            },
            dismissButton = {
                AiModuleTextAction(label = "cancel", onClick = { teamToEdit = null }, tint = AiModuleTheme.colors.textSecondary)
            }
        )
    }

    if (teamToRemove != null) {
        AiModuleAlertDialog(
            onDismissRequest = { teamToRemove = null },
            title = "Remove Team?",
            content = {
                Text("Remove ${teamToRemove!!.name} from this repository?", fontSize = 14.sp, color = AiModuleTheme.colors.textSecondary)
            },
            confirmButton = {
                AiModuleTextAction(
                    label = "remove",
                    enabled = !actionInFlight,
                    onClick = {
                        val team = teamToRemove ?: return@AiModuleTextAction
                        actionInFlight = true
                        scope.launch {
                            val ok = GitHubManager.removeRepoTeam(context, repoOwner, team.slug, repoOwner, repoName)
                            Toast.makeText(context, if (ok) "Removed" else "Failed", Toast.LENGTH_SHORT).show()
                            actionInFlight = false
                            teamToRemove = null
                            loadTeams()
                        }
                    },
                    tint = Color(0xFFFF3B30),
                )
            },
            dismissButton = {
                AiModuleTextAction(label = "cancel", onClick = { teamToRemove = null }, tint = AiModuleTheme.colors.textSecondary)
            }
        )
    }
}

@Composable
private fun RepoTeamsSummaryCard(repoTeams: List<GHRepoTeam>, orgTeams: List<GHOrgTeam>) {
    val grouped = repoTeams.groupingBy { normalizeTeamPermission(it.permission) }.eachCount()
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(AiModuleTheme.colors.surface).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.Group, null, Modifier.size(18.dp), tint = AiModuleTheme.colors.accent)
            Column(Modifier.weight(1f)) {
                Text("${repoTeams.size} repository teams", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AiModuleTheme.colors.textPrimary)
                Text("${orgTeams.size} organization teams available", fontSize = 11.sp, color = AiModuleTheme.colors.textMuted)
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TEAM_PERMISSIONS.forEach { (permission, label) ->
                val count = grouped[permission] ?: 0
                if (count > 0) TeamPermissionCountChip(label, count, teamPermissionColor(permission))
            }
        }
    }
}

@Composable
private fun RepoTeamCard(
    team: GHRepoTeam,
    onPermissionChange: () -> Unit,
    onRemove: () -> Unit
) {
    val permission = normalizeTeamPermission(team.permission)
    val color = teamPermissionColor(permission)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(AiModuleTheme.colors.surface).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(color.copy(0.12f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Group, null, Modifier.size(22.dp), tint = color)
        }
        Column(Modifier.weight(1f)) {
            Text(team.name.ifBlank { team.slug }, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = AiModuleTheme.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("@${team.slug}", fontSize = 11.sp, color = AiModuleTheme.colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Box(Modifier.size(5.dp).clip(CircleShape).background(AiModuleTheme.colors.textMuted.copy(0.5f)))
                Icon(if (team.privacy == "closed" || team.privacy == "secret") Icons.Rounded.Lock else Icons.Rounded.Group, null, Modifier.size(12.dp), tint = AiModuleTheme.colors.textMuted)
                Text(team.privacy.ifBlank { "team" }, fontSize = 11.sp, color = AiModuleTheme.colors.textMuted)
            }
            if (team.description.isNotBlank()) {
                Text(team.description, fontSize = 12.sp, color = AiModuleTheme.colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(4.dp))
            Text(teamPermissionLabel(permission), fontSize = 12.sp, color = color, fontWeight = FontWeight.Medium)
        }
        IconButton(onClick = onPermissionChange, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Rounded.Edit, null, Modifier.size(18.dp), tint = AiModuleTheme.colors.accent)
        }
        IconButton(onClick = onRemove, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Rounded.Delete, null, Modifier.size(18.dp), tint = Color(0xFFFF3B30))
        }
    }
}

@Composable
private fun SelectableTeamRow(team: GHOrgTeam, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp))
            .background(if (selected) AiModuleTheme.colors.accent.copy(0.12f) else AiModuleTheme.colors.background)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Rounded.Group, null, Modifier.size(18.dp), tint = if (selected) AiModuleTheme.colors.accent else AiModuleTheme.colors.textSecondary)
        Column(Modifier.weight(1f)) {
            Text(team.name.ifBlank { team.slug }, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = AiModuleTheme.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${team.membersCount} members - ${team.reposCount} repos", fontSize = 11.sp, color = AiModuleTheme.colors.textMuted)
        }
    }
}

@Composable
private fun PermissionChipRow(selectedPermission: String, onSelect: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        TEAM_PERMISSIONS.forEach { (permission, label) ->
            TeamPermissionChip(
                label = label,
                selected = permission == selectedPermission,
                onClick = { onSelect(permission) }
            )
        }
    }
}

@Composable
private fun TeamPermissionChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(8.dp))
            .background(if (selected) AiModuleTheme.colors.accent.copy(0.15f) else AiModuleTheme.colors.background)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) AiModuleTheme.colors.accent else AiModuleTheme.colors.textPrimary
        )
    }
}

@Composable
private fun TeamPermissionCountChip(label: String, count: Int, color: Color) {
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).background(color.copy(0.10f)).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(label, fontSize = 11.sp, color = color, fontWeight = FontWeight.Medium)
        Text("$count", fontSize = 11.sp, color = color)
    }
}

@Composable
private fun EmptyTeamsCard(message: String) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(AiModuleTheme.colors.surface).padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(message, fontSize = 14.sp, color = AiModuleTheme.colors.textMuted)
    }
}

private fun normalizeTeamPermission(permission: String): String = when (permission.lowercase()) {
    "read" -> "pull"
    "write" -> "push"
    "pull", "triage", "push", "maintain", "admin" -> permission.lowercase()
    else -> "pull"
}

private fun teamPermissionLabel(permission: String): String =
    TEAM_PERMISSIONS.firstOrNull { it.first == normalizeTeamPermission(permission) }?.second ?: permission.replaceFirstChar { it.uppercase() }

@Composable
private fun teamPermissionColor(permission: String): Color = when (normalizeTeamPermission(permission)) {
    "admin" -> Color(0xFFFF3B30)
    "maintain" -> Color(0xFFFF9F0A)
    "push" -> Color(0xFF34C759)
    "triage" -> AiModuleTheme.colors.accent
    else -> AiModuleTheme.colors.textSecondary
}
