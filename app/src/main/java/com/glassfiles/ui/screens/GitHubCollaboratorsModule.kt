package com.glassfiles.ui.screens


import android.widget.Toast
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
import androidx.compose.material3.*
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
import com.glassfiles.data.github.GitHubManager
import com.glassfiles.ui.components.AiModulePageBar
import com.glassfiles.ui.components.AiModuleHairline
import com.glassfiles.ui.components.AiModuleSpinner
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
    var loading by remember { mutableStateOf(true) }
    var newUsername by remember { mutableStateOf("") }
    var selectedPermission by remember { mutableStateOf("push") }
    var showAddDialog by remember { mutableStateOf(false) }
    var userToRemove by remember { mutableStateOf<GHCollaborator?>(null) }
    var userToEdit by remember { mutableStateOf<GHCollaborator?>(null) }
    var query by remember { mutableStateOf("") }
    var actionInFlight by remember { mutableStateOf(false) }

    fun loadCollaborators() {
        loading = true
        scope.launch {
            collaborators = GitHubManager.getCollaborators(context, repoOwner, repoName)
            loading = false
        }
    }

    LaunchedEffect(repoOwner, repoName) { loadCollaborators() }

    GitHubScreenFrame(
        title = "> collaborators",
        subtitle = "$repoOwner/$repoName",
        onBack = onBack,
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
                CircularProgressIndicator(color = AiModuleTheme.colors.accent, modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
            }
        } else {
            val visibleCollaborators = collaborators.filter {
                query.isBlank() || it.login.contains(query, ignoreCase = true) || permissionLabel(it.role).contains(query, ignoreCase = true)
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
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Search collaborators") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Rounded.Search, null, Modifier.size(18.dp), tint = AiModuleTheme.colors.textSecondary) }
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
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            containerColor = AiModuleTheme.colors.surface,
            title = { Text("Add Collaborator", fontWeight = FontWeight.Bold, color = AiModuleTheme.colors.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = newUsername,
                        onValueChange = { newUsername = it },
                        label = { Text("Username") },
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
                TextButton(
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
                    }
                ) {
                    Text("Add", color = AiModuleTheme.colors.accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel", color = AiModuleTheme.colors.textSecondary)
                }
            }
        )
    }

    // Remove confirmation
    if (userToRemove != null) {
        AlertDialog(
            onDismissRequest = { userToRemove = null },
            containerColor = AiModuleTheme.colors.surface,
            title = { Text("Remove Collaborator?", fontWeight = FontWeight.Bold, color = AiModuleTheme.colors.textPrimary) },
            text = {
                Text("Remove ${userToRemove!!.login} from this repository?", fontSize = 14.sp, color = AiModuleTheme.colors.textSecondary)
            },
            confirmButton = {
                TextButton(
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
                    }
                ) {
                    Text("Remove", color = Color(0xFFFF3B30))
                }
            },
            dismissButton = {
                TextButton(onClick = { userToRemove = null }) {
                    Text("Cancel", color = AiModuleTheme.colors.textSecondary)
                }
            }
        )
    }

    // Edit permission dialog
    if (userToEdit != null) {
        var editPermission by remember(userToEdit) { mutableStateOf(normalizeCollaboratorPermission(userToEdit!!.role)) }
        AlertDialog(
            onDismissRequest = { userToEdit = null },
            containerColor = AiModuleTheme.colors.surface,
            title = { Text("Change Permission", fontWeight = FontWeight.Bold, color = AiModuleTheme.colors.textPrimary) },
            text = {
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
                TextButton(
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
                    }
                ) {
                    Text("Save", color = AiModuleTheme.colors.accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { userToEdit = null }) {
                    Text("Cancel", color = AiModuleTheme.colors.textSecondary)
                }
            }
        )
    }
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
        IconButton(onClick = onPermissionChange) {
            Icon(Icons.Rounded.Edit, null, Modifier.size(18.dp), tint = AiModuleTheme.colors.accent)
        }
        IconButton(onClick = onRemove) {
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
