package com.glassfiles.ui.screens


import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.glassfiles.data.github.GHCollaborator
import com.glassfiles.data.github.GHRepoInvitation
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
import com.glassfiles.ui.theme.*
import com.glassfiles.ui.theme.AiModuleTheme
import com.glassfiles.ui.theme.AiModuleSurface
import com.glassfiles.ui.theme.JetBrainsMono
import kotlinx.coroutines.launch

private val COLLABORATOR_PERMISSIONS = listOf(
    "pull" to "Read",
    "triage" to "Triage",
    "push" to "Write",
    "maintain" to "Maintain",
    "admin" to "Admin"
)

@Composable
internal fun CollaboratorsScreen(
    repoOwner: String,
    repoName: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var collaborators by remember { mutableStateOf<List<GHCollaborator>>(emptyList()) }
    var invitations by remember { mutableStateOf<List<GHRepoInvitation>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var newUsername by remember { mutableStateOf("") }
    var selectedPermission by remember { mutableStateOf("push") }
    var showAddDialog by remember { mutableStateOf(false) }
    var userToRemove by remember { mutableStateOf<GHCollaborator?>(null) }
    var userToEdit by remember { mutableStateOf<GHCollaborator?>(null) }
    var invitationToCancel by remember { mutableStateOf<GHRepoInvitation?>(null) }
    var invitationToEdit by remember { mutableStateOf<GHRepoInvitation?>(null) }
    var query by remember { mutableStateOf("") }
    var actionInFlight by remember { mutableStateOf(false) }

    fun loadCollaborators() {
        loading = true
        scope.launch {
            collaborators = GitHubManager.getCollaborators(context, repoOwner, repoName)
            invitations = GitHubManager.getRepoInvitations(context, repoOwner, repoName)
            loading = false
        }
    }

    LaunchedEffect(repoOwner, repoName) { loadCollaborators() }

    fun handleCollaboratorsBack() {
        when {
            showAddDialog -> showAddDialog = false
            userToRemove != null -> userToRemove = null
            userToEdit != null -> userToEdit = null
            invitationToCancel != null -> invitationToCancel = null
            invitationToEdit != null -> invitationToEdit = null
            else -> onBack()
        }
    }

    GitHubScreenFrame(
        title = "> collaborators",
        subtitle = "$repoOwner/$repoName",
        onBack = ::handleCollaboratorsBack,
        trailing = {
            GitHubTopBarAction(
                glyph = GhGlyphs.PLUS,
                onClick = { showAddDialog = true },
                tint = AiModuleTheme.colors.accent,
                contentDescription = "add collaborator",
            )
        },
    ) {

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                AiModuleSpinner(label = "loading…")
            }
        } else {
            val visibleCollaborators = collaborators.filter {
                query.isBlank() || it.login.contains(query, ignoreCase = true) || permissionLabel(it.role).contains(query, ignoreCase = true)
            }
            val visibleInvitations = invitations.filter {
                query.isBlank() ||
                    it.invitee.contains(query, ignoreCase = true) ||
                    it.inviter.contains(query, ignoreCase = true) ||
                    permissionLabel(it.permissions).contains(query, ignoreCase = true)
            }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    CollaboratorsSummaryCard(collaborators)
                }
                item {
                    RepoInvitationsSection(
                        invitations = visibleInvitations,
                        totalInvitations = invitations.size,
                        onEdit = { invitationToEdit = it },
                        onCancel = { invitationToCancel = it }
                    )
                }
                item {
                    AiModuleTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = "Search collaborators",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        leading = { Icon(Icons.Rounded.Search, null, Modifier.size(18.dp), tint = AiModuleTheme.colors.textSecondary) }
                    )
                }
                items(visibleCollaborators) { collaborator ->
                    CollaboratorCard(
                        collaborator = collaborator,
                        onPermissionChange = { userToEdit = collaborator },
                        onRemove = { userToRemove = collaborator }
                    )
                }

                if (visibleCollaborators.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text(if (collaborators.isEmpty()) "No collaborators yet" else "No matching collaborators", fontSize = 14.sp, color = AiModuleTheme.colors.textMuted)
                        }
                    }
                }
            }
        }
    }

    // Add collaborator dialog
    if (showAddDialog) {
        AiModuleAlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = "Add Collaborator",
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AiModuleTextField(
                        value = newUsername,
                        onValueChange = { newUsername = it },
                        label = "Username",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Permission level:", fontSize = 12.sp, color = AiModuleTheme.colors.textSecondary)
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        COLLABORATOR_PERMISSIONS.forEach { (perm, label) ->
                            PermissionChip(
                                label = label,
                                selected = perm == selectedPermission
                            ) {
                                selectedPermission = perm
                            }
                        }
                    }
                }
            },
            confirmButton = {
                AiModuleTextAction(
                    label = "add",
                    enabled = !actionInFlight && newUsername.isNotBlank(),
                    onClick = {
                        if (newUsername.isNotBlank()) {
                            actionInFlight = true
                            scope.launch {
                                val ok = GitHubManager.addCollaborator(context, repoOwner, repoName, newUsername, selectedPermission)
                                Toast.makeText(context, if (ok) "Invitation sent" else "Failed", Toast.LENGTH_SHORT).show()
                                actionInFlight = false
                                if (ok) {
                                    newUsername = ""
                                    showAddDialog = false
                                    loadCollaborators()
                                }
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

    // Remove confirmation
    if (userToRemove != null) {
        AiModuleAlertDialog(
            onDismissRequest = { userToRemove = null },
            title = "Remove Collaborator?",
            content = {
                Text("Remove ${userToRemove!!.login} from this repository?", fontSize = 14.sp, color = AiModuleTheme.colors.textSecondary)
            },
            confirmButton = {
                AiModuleTextAction(
                    label = "remove",
                    enabled = !actionInFlight,
                    onClick = {
                        actionInFlight = true
                        scope.launch {
                            val ok = GitHubManager.removeCollaborator(context, repoOwner, repoName, userToRemove!!.login)
                            Toast.makeText(context, if (ok) "Removed" else "Failed", Toast.LENGTH_SHORT).show()
                            actionInFlight = false
                            userToRemove = null
                            loadCollaborators()
                        }
                    },
                    tint = Color(0xFFFF3B30),
                )
            },
            dismissButton = {
                AiModuleTextAction(label = "cancel", onClick = { userToRemove = null }, tint = AiModuleTheme.colors.textSecondary)
            }
        )
    }

    // Edit permission dialog
    if (userToEdit != null) {
        var editPermission by remember(userToEdit) { mutableStateOf(normalizeCollaboratorPermission(userToEdit!!.role)) }
        AiModuleAlertDialog(
            onDismissRequest = { userToEdit = null },
            title = "Change Permission",
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${userToEdit!!.login}", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = AiModuleTheme.colors.textPrimary)
                    Text("Select permission level:", fontSize = 12.sp, color = AiModuleTheme.colors.textSecondary)
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        COLLABORATOR_PERMISSIONS.forEach { (perm, label) ->
                            PermissionChip(
                                label = label,
                                selected = perm == editPermission
                            ) {
                                editPermission = perm
                            }
                        }
                    }
                }
            },
            confirmButton = {
                AiModuleTextAction(
                    label = "save",
                    enabled = !actionInFlight && editPermission != normalizeCollaboratorPermission(userToEdit!!.role),
                    onClick = {
                        actionInFlight = true
                        scope.launch {
                            val ok = GitHubManager.updateCollaboratorPermission(context, repoOwner, repoName, userToEdit!!.login, editPermission)
                            Toast.makeText(context, if (ok) "Updated" else "Failed", Toast.LENGTH_SHORT).show()
                            actionInFlight = false
                            userToEdit = null
                            loadCollaborators()
                        }
                    },
                )
            },
            dismissButton = {
                AiModuleTextAction(label = "cancel", onClick = { userToEdit = null }, tint = AiModuleTheme.colors.textSecondary)
            }
        )
    }

    if (invitationToEdit != null) {
        var editPermission by remember(invitationToEdit) { mutableStateOf(normalizeCollaboratorPermission(invitationToEdit!!.permissions)) }
        AiModuleAlertDialog(
            onDismissRequest = { invitationToEdit = null },
            title = "Invitation Permission",
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(invitationToEdit!!.invitee.ifBlank { "pending user" }, fontSize = 14.sp, fontFamily = JetBrainsMono, color = AiModuleTheme.colors.textPrimary)
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        COLLABORATOR_PERMISSIONS.forEach { (perm, label) ->
                            GitHubTerminalButton(
                                label = label.lowercase(),
                                onClick = { editPermission = perm },
                                color = if (perm == editPermission) AiModuleTheme.colors.accent else AiModuleTheme.colors.textSecondary,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                AiModuleTextAction(
                    label = "save",
                    enabled = !actionInFlight && editPermission != normalizeCollaboratorPermission(invitationToEdit!!.permissions),
                    onClick = {
                        val target = invitationToEdit ?: return@AiModuleTextAction
                        actionInFlight = true
                        scope.launch {
                            val ok = GitHubManager.updateRepoInvitation(context, repoOwner, repoName, target.id, editPermission)
                            Toast.makeText(context, if (ok) "Updated" else "Failed", Toast.LENGTH_SHORT).show()
                            actionInFlight = false
                            invitationToEdit = null
                            loadCollaborators()
                        }
                    },
                )
            },
            dismissButton = {
                AiModuleTextAction(label = "cancel", onClick = { invitationToEdit = null }, tint = AiModuleTheme.colors.textSecondary)
            }
        )
    }

    if (invitationToCancel != null) {
        AiModuleAlertDialog(
            onDismissRequest = { invitationToCancel = null },
            title = "Cancel Invitation?",
            content = {
                Text("Cancel pending invitation for ${invitationToCancel!!.invitee}?", fontSize = 14.sp, fontFamily = JetBrainsMono, color = AiModuleTheme.colors.textSecondary)
            },
            confirmButton = {
                AiModuleTextAction(
                    label = "cancel invite",
                    enabled = !actionInFlight,
                    tint = AiModuleTheme.colors.error,
                    onClick = {
                        val target = invitationToCancel ?: return@AiModuleTextAction
                        actionInFlight = true
                        scope.launch {
                            val ok = GitHubManager.deleteRepoInvitation(context, repoOwner, repoName, target.id)
                            Toast.makeText(context, if (ok) "Cancelled" else "Failed", Toast.LENGTH_SHORT).show()
                            actionInFlight = false
                            invitationToCancel = null
                            loadCollaborators()
                        }
                    },
                )
            },
            dismissButton = {
                AiModuleTextAction(label = "keep", onClick = { invitationToCancel = null }, tint = AiModuleTheme.colors.textSecondary)
            }
        )
    }
}

@Composable
private fun RepoInvitationsSection(
    invitations: List<GHRepoInvitation>,
    totalInvitations: Int,
    onEdit: (GHRepoInvitation) -> Unit,
    onCancel: (GHRepoInvitation) -> Unit
) {
    val palette = AiModuleTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, palette.border, RoundedCornerShape(3.dp))
            .background(palette.surface)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("pending invitations", color = palette.textSecondary, fontFamily = JetBrainsMono, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(totalInvitations.toString(), color = palette.accent, fontFamily = JetBrainsMono, fontSize = 12.sp)
        }
        if (invitations.isEmpty()) {
            Text("// no pending invitations", color = palette.textMuted, fontFamily = JetBrainsMono, fontSize = 12.sp)
        } else {
            invitations.forEach { invitation ->
                RepoInvitationRow(invitation = invitation, onEdit = { onEdit(invitation) }, onCancel = { onCancel(invitation) })
            }
        }
    }
}

@Composable
private fun RepoInvitationRow(
    invitation: GHRepoInvitation,
    onEdit: () -> Unit,
    onCancel: () -> Unit
) {
    val palette = AiModuleTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, palette.border.copy(alpha = 0.75f), RoundedCornerShape(2.dp))
            .background(palette.background.copy(alpha = 0.35f))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("@${invitation.invitee.ifBlank { "unknown" }}", color = palette.textPrimary, fontFamily = JetBrainsMono, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            InvitationPermissionBadge(invitation.permissions)
            if (invitation.expired) Text("expired", color = palette.error, fontFamily = JetBrainsMono, fontSize = 10.sp)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (invitation.inviter.isNotBlank()) {
                Text("from:${invitation.inviter}", color = palette.textMuted, fontFamily = JetBrainsMono, fontSize = 10.sp)
            }
            if (invitation.createdAt.isNotBlank()) {
                Text(invitation.createdAt.take(10), color = palette.textMuted, fontFamily = JetBrainsMono, fontSize = 10.sp)
            }
            Spacer(Modifier.width(4.dp))
            GitHubTerminalButton("permission", onClick = onEdit, color = palette.textSecondary)
            GitHubTerminalButton("cancel", onClick = onCancel, color = palette.error)
        }
    }
}

@Composable
private fun InvitationPermissionBadge(permission: String) {
    val color = collaboratorRoleColor(permission)
    Text(
        permissionLabel(permission).lowercase(),
        color = color,
        fontFamily = JetBrainsMono,
        fontSize = 10.sp,
        modifier = Modifier
            .border(1.dp, color.copy(alpha = 0.55f), RoundedCornerShape(2.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp),
    )
}

@Composable
private fun CollaboratorsSummaryCard(collaborators: List<GHCollaborator>) {
    val grouped = collaborators.groupingBy { normalizeCollaboratorPermission(it.role) }.eachCount()
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(AiModuleTheme.colors.surface).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.Group, null, Modifier.size(18.dp), tint = AiModuleTheme.colors.accent)
            Text("${collaborators.size} collaborators", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AiModuleTheme.colors.textPrimary)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            COLLABORATOR_PERMISSIONS.forEach { (permission, label) ->
                val count = grouped[permission] ?: 0
                if (count > 0) PermissionCountChip(label, count, collaboratorRoleColor(permission))
            }
        }
    }
}

@Composable
private fun PermissionCountChip(label: String, count: Int, color: Color) {
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
private fun CollaboratorCard(
    collaborator: GHCollaborator,
    onPermissionChange: () -> Unit,
    onRemove: () -> Unit
) {
    val permission = normalizeCollaboratorPermission(collaborator.role)
    val roleColor = collaboratorRoleColor(permission)

    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(AiModuleTheme.colors.surface)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AsyncImage(
            collaborator.avatarUrl,
            collaborator.login,
            Modifier.size(40.dp).clip(CircleShape)
        )
        Column(Modifier.weight(1f)) {
            Text(collaborator.login, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = AiModuleTheme.colors.textPrimary)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(roleColor))
                Text(
                    permissionLabel(permission),
                    fontSize = 12.sp,
                    color = roleColor,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        IconButton(onClick = onPermissionChange, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Rounded.Edit, null, Modifier.size(18.dp), tint = AiModuleTheme.colors.accent)
        }
        IconButton(onClick = onRemove, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Rounded.PersonRemove, null, Modifier.size(18.dp), tint = Color(0xFFFF3B30))
        }
    }
}

private fun normalizeCollaboratorPermission(role: String): String = when (role) {
    "read" -> "pull"
    "write" -> "push"
    "pull", "triage", "push", "maintain", "admin" -> role
    else -> "pull"
}

private fun permissionLabel(permission: String): String =
    COLLABORATOR_PERMISSIONS.firstOrNull { it.first == normalizeCollaboratorPermission(permission) }?.second ?: permission.replaceFirstChar { it.uppercase() }

@Composable
private fun collaboratorRoleColor(permission: String): Color = when (normalizeCollaboratorPermission(permission)) {
    "admin" -> Color(0xFFFF3B30)
    "maintain" -> Color(0xFFFF9F0A)
    "push" -> Color(0xFF34C759)
    "triage" -> AiModuleTheme.colors.accent
    else -> AiModuleTheme.colors.textSecondary
}

@Composable
private fun PermissionChip(label: String, selected: Boolean, onClick: () -> Unit) {
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
