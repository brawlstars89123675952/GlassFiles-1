package com.glassfiles.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ViewColumn
import com.glassfiles.ui.components.AiModuleAlertDialog
import com.glassfiles.ui.components.AiModuleGlyph
import com.glassfiles.ui.components.AiModuleGlyphAction
import com.glassfiles.ui.components.AiModuleIcon as Icon
import com.glassfiles.ui.components.AiModuleIconButton as IconButton
import com.glassfiles.ui.components.AiModuleSearchField
import com.glassfiles.ui.components.AiModuleSpinner
import com.glassfiles.ui.components.AiModuleText as Text
import com.glassfiles.ui.components.AiModuleTextAction
import com.glassfiles.ui.components.AiModuleTextField
import com.glassfiles.ui.theme.JetBrainsMono
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glassfiles.data.github.GHProject
import com.glassfiles.ui.theme.AiModuleTheme
import com.glassfiles.data.github.GHProjectCard
import com.glassfiles.data.github.GHProjectColumn
import com.glassfiles.data.github.GHProjectV2
import com.glassfiles.data.github.GHProjectV2Detail
import com.glassfiles.data.github.GHProjectV2Field
import com.glassfiles.data.github.GHProjectV2Item
import com.glassfiles.data.github.GHProjectV2ItemFieldValue
import com.glassfiles.data.github.GHProjectV2View
import com.glassfiles.data.github.GHProjectV2Workflow
import com.glassfiles.data.github.GHRepo
import com.glassfiles.data.github.GitHubManager
import com.glassfiles.data.github.canWrite
import com.glassfiles.ui.components.AiModulePageBar
import com.glassfiles.ui.components.AiModuleHairline
import kotlinx.coroutines.launch

private enum class ProjectsKind { CLASSIC, V2 }

@Composable
internal fun ProjectsTab(repo: GHRepo) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var classicProjects by remember { mutableStateOf<List<GHProject>>(emptyList()) }
    var v2Projects by remember { mutableStateOf<List<GHProjectV2>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var selectedKind by remember { mutableStateOf(ProjectsKind.CLASSIC) }
    var query by remember { mutableStateOf("") }
    var selectedProject by remember { mutableStateOf<GHProject?>(null) }
    var selectedProjectV2 by remember { mutableStateOf<GHProjectV2?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }

    fun loadProjects() {
        loading = true
        scope.launch {
            classicProjects = GitHubManager.getRepoProjects(context, repo.owner, repo.name)
            v2Projects = GitHubManager.getRepoProjectsV2(context, repo.owner, repo.name)
            loading = false
        }
    }

    LaunchedEffect(repo.owner, repo.name) { loadProjects() }

    selectedProject?.let { project ->
        ClassicProjectDetail(
            project = project,
            onBack = { selectedProject = null },
            onDeleted = {
                selectedProject = null
                loadProjects()
            },
            onChanged = { updated ->
                selectedProject = updated
                classicProjects = classicProjects.map { if (it.id == updated.id) updated else it }
            }
        )
        return
    }

    selectedProjectV2?.let { project ->
        ProjectV2DetailScreen(project = project, onBack = { selectedProjectV2 = null })
        return
    }

    if (loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            AiModuleSpinner(label = "loading projects")
        }
        return
    }

    val visibleClassic = classicProjects.filter {
        query.isBlank() || it.name.contains(query, ignoreCase = true) || it.body.contains(query, ignoreCase = true)
    }
    val visibleV2 = v2Projects.filter {
        query.isBlank() || it.title.contains(query, ignoreCase = true) || it.shortDescription.contains(query, ignoreCase = true)
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { ProjectsSummaryCard(classicProjects, v2Projects) }
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    AiModuleSearchField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = "search projects",
                    )
                }
                if (repo.canWrite()) {
                    AiModuleGlyphAction(
                        glyph = GhGlyphs.PLUS,
                        onClick = { showCreateDialog = true },
                        tint = AiModuleTheme.colors.accent,
                        contentDescription = "new project",
                    )
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ProjectChip("Classic ${classicProjects.size}", selectedKind == ProjectsKind.CLASSIC) { selectedKind = ProjectsKind.CLASSIC }
                ProjectChip("V2 ${v2Projects.size}", selectedKind == ProjectsKind.V2) { selectedKind = ProjectsKind.V2 }
            }
        }
        when (selectedKind) {
            ProjectsKind.CLASSIC -> {
                items(visibleClassic) { project -> ClassicProjectCard(project) { selectedProject = project } }
                if (visibleClassic.isEmpty()) item { EmptyProjectsCard(if (classicProjects.isEmpty()) "No classic projects returned" else "No matching classic projects") }
            }
            ProjectsKind.V2 -> {
                items(visibleV2) { project -> ProjectV2Card(project) { selectedProjectV2 = project } }
                if (visibleV2.isEmpty()) item { EmptyProjectsCard(if (v2Projects.isEmpty()) "No Projects V2 returned" else "No matching Projects V2") }
            }
        }
    }

    if (showCreateDialog) {
        ProjectEditorDialog(
            title = "New Classic Project",
            initialName = "",
            initialBody = "",
            initialState = "open",
            showState = false,
            confirmLabel = "Create",
            onDismiss = { showCreateDialog = false },
            onSave = { name, body, _ ->
                scope.launch {
                    val project = GitHubManager.createRepoProject(context, repo.owner, repo.name, name, body)
                    Toast.makeText(context, if (project != null) "Project created" else "Failed", Toast.LENGTH_SHORT).show()
                    if (project != null) {
                        showCreateDialog = false
                        classicProjects = listOf(project) + classicProjects
                    }
                }
            }
        )
    }
}

@Composable
private fun ProjectsSummaryCard(classicProjects: List<GHProject>, v2Projects: List<GHProjectV2>) {
    val openClassic = classicProjects.count { it.state == "open" }
    val openV2 = v2Projects.count { !it.closed }
    Column(Modifier.fillMaxWidth().ghGlassCard(14.dp).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.Dashboard, null, Modifier.size(18.dp), tint = AiModuleTheme.colors.accent)
            Column(Modifier.weight(1f)) {
                Text("Projects", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AiModuleTheme.colors.textPrimary)
                Text("${classicProjects.size} classic - ${v2Projects.size} v2", fontSize = 11.sp, color = AiModuleTheme.colors.textMuted)
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CountPill("Open classic", openClassic, AiModuleTheme.colors.accent)
            CountPill("Open V2", openV2, Color(0xFF34C759))
            CountPill("Closed", classicProjects.size - openClassic + v2Projects.size - openV2, AiModuleTheme.colors.textSecondary)
        }
    }
}

@Composable
private fun ClassicProjectCard(project: GHProject, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().ghGlassCard(14.dp).clickable(onClick = onClick).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Rounded.ViewColumn, null, Modifier.size(20.dp), tint = if (project.state == "open") AiModuleTheme.colors.accent else AiModuleTheme.colors.textSecondary)
            Text(project.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = AiModuleTheme.colors.textPrimary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            CountPill(project.state, 0, if (project.state == "open") Color(0xFF34C759) else AiModuleTheme.colors.textSecondary, showCount = false)
        }
        if (project.body.isNotBlank()) Text(project.body, fontSize = 12.sp, color = AiModuleTheme.colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("#${project.number}", fontSize = 11.sp, color = AiModuleTheme.colors.textMuted)
            Text(project.updatedAt.take(10), fontSize = 11.sp, color = AiModuleTheme.colors.textMuted)
            if (project.creator.isNotBlank()) Text(project.creator, fontSize = 11.sp, color = AiModuleTheme.colors.accent)
        }
    }
}

@Composable
private fun ProjectV2Card(project: GHProjectV2, onClick: () -> Unit) {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxWidth().ghGlassCard(14.dp).clickable(onClick = onClick).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Rounded.Dashboard, null, Modifier.size(20.dp), tint = if (!project.closed) AiModuleTheme.colors.accent else AiModuleTheme.colors.textSecondary)
            Text(project.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = AiModuleTheme.colors.textPrimary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (project.url.isNotBlank()) {
                IconButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(project.url))) }) {
                    Icon(Icons.Rounded.Language, null, Modifier.size(18.dp), tint = AiModuleTheme.colors.textSecondary)
                }
            }
        }
        if (project.shortDescription.isNotBlank()) Text(project.shortDescription, fontSize = 12.sp, color = AiModuleTheme.colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CountPill("${project.itemsCount} items", 0, AiModuleTheme.colors.accent, showCount = false)
            CountPill(if (project.closed) "Closed" else "Open", 0, if (project.closed) AiModuleTheme.colors.textSecondary else Color(0xFF34C759), showCount = false)
            CountPill(if (project.isPublic) "Public" else "Private", 0, AiModuleTheme.colors.textSecondary, showCount = false)
        }
    }
}

@Composable
private fun ProjectV2DetailScreen(project: GHProjectV2, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var detail by remember(project.id) { mutableStateOf<GHProjectV2Detail?>(null) }
    var loading by remember(project.id) { mutableStateOf(true) }
    var showEditProject by remember { mutableStateOf(false) }
    var showAddDraft by remember { mutableStateOf(false) }
    var showAddField by remember { mutableStateOf(false) }
    var editSchemaField by remember { mutableStateOf<GHProjectV2Field?>(null) }
    var deleteSchemaField by remember { mutableStateOf<GHProjectV2Field?>(null) }
    var editDraft by remember { mutableStateOf<GHProjectV2Item?>(null) }
    var editField by remember { mutableStateOf<GHProjectV2Item?>(null) }
    var deleteItem by remember { mutableStateOf<GHProjectV2Item?>(null) }
    var actionInFlight by remember { mutableStateOf(false) }

    fun loadDetail() {
        loading = true
        scope.launch {
            detail = GitHubManager.getProjectV2Detail(context, project.id)
            loading = false
        }
    }

    LaunchedEffect(project.id) { loadDetail() }

    GitHubScreenFrame(
        title = "> ${(detail?.title ?: project.title).lowercase()}",
        subtitle = "project v2 #${project.number}",
        onBack = onBack,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GitHubTopBarAction(
                    glyph = GhGlyphs.REFRESH,
                    onClick = { loadDetail() },
                    tint = AiModuleTheme.colors.accent,
                    contentDescription = "refresh project",
                )
                detail?.url?.takeIf { it.isNotBlank() }?.let { url ->
                    GitHubTopBarAction(
                        glyph = GhGlyphs.OPEN_NEW,
                        onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
                        tint = AiModuleTheme.colors.textSecondary,
                        contentDescription = "open project",
                    )
                }
                GitHubTopBarAction(
                    glyph = GhGlyphs.EDIT,
                    onClick = { showEditProject = true },
                    enabled = detail != null,
                    tint = AiModuleTheme.colors.accent,
                    contentDescription = "edit project",
                )
                GitHubTopBarAction(
                    glyph = GhGlyphs.PLUS,
                    onClick = { showAddDraft = true },
                    enabled = detail != null,
                    tint = AiModuleTheme.colors.accent,
                    contentDescription = "add draft",
                )
            }
        },
    ) {

        val current = detail
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                AiModuleSpinner(label = "loading project")
            }
        } else if (current == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyProjectsCard("Project V2 detail unavailable")
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { ProjectV2Summary(current) }
                item {
                    GitHubTerminalButton("add draft item", onClick = { showAddDraft = true }, color = AiModuleTheme.colors.accent, modifier = Modifier.fillMaxWidth())
                }
                item {
                    ProjectV2FieldsCard(
                        fields = current.fields,
                        onAdd = { showAddField = true },
                        onEdit = { editSchemaField = it },
                        onDelete = { deleteSchemaField = it }
                    )
                }
                item { ProjectV2ViewsCard(current.views) }
                item { ProjectV2WorkflowsCard(current.workflows) }
                items(current.items) { item ->
                    ProjectV2ItemCard(
                        item = item,
                        fields = current.fields,
                        onOpen = {
                            if (item.url.isNotBlank()) context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.url)))
                        },
                        onEditDraft = { editDraft = item },
                        onEditField = { editField = item },
                        onArchive = {
                            actionInFlight = true
                            scope.launch {
                                val ok = GitHubManager.archiveProjectV2Item(context, current.id, item.id, !item.archived)
                                Toast.makeText(context, if (ok) "Item updated" else "Failed", Toast.LENGTH_SHORT).show()
                                actionInFlight = false
                                if (ok) loadDetail()
                            }
                        },
                        onMoveTop = {
                            actionInFlight = true
                            scope.launch {
                                val ok = GitHubManager.moveProjectV2Item(context, current.id, item.id, null)
                                Toast.makeText(context, if (ok) "Item moved" else "Failed", Toast.LENGTH_SHORT).show()
                                actionInFlight = false
                                if (ok) loadDetail()
                            }
                        },
                        onDelete = { deleteItem = item }
                    )
                }
                if (current.items.isEmpty()) item { EmptyProjectsCard("No Project V2 items yet") }
            }
        }
    }

    detail?.let { current ->
        if (showEditProject) {
            ProjectV2EditorDialog(
                project = current,
                onDismiss = { showEditProject = false },
                onSave = { title, description, readme, closed, isPublic ->
                    actionInFlight = true
                    scope.launch {
                        val ok = GitHubManager.updateProjectV2(context, current.id, title, description, readme, closed, isPublic)
                        Toast.makeText(context, if (ok) "Project updated" else "Failed", Toast.LENGTH_SHORT).show()
                        actionInFlight = false
                        if (ok) {
                            showEditProject = false
                            loadDetail()
                        }
                    }
                }
            )
        }
        if (showAddDraft) {
            DraftIssueDialog(
                title = "New Draft Item",
                initialTitle = "",
                initialBody = "",
                confirmLabel = "Create",
                onDismiss = { showAddDraft = false },
                onSave = { title, body ->
                    actionInFlight = true
                    scope.launch {
                        val item = GitHubManager.addProjectV2DraftIssue(context, current.id, title, body)
                        Toast.makeText(context, if (item != null) "Draft added" else "Failed", Toast.LENGTH_SHORT).show()
                        actionInFlight = false
                        if (item != null) {
                            showAddDraft = false
                            loadDetail()
                        }
                    }
                }
            )
        }
        if (showAddField) {
            ProjectV2SchemaFieldDialog(
                title = "New Field",
                initialField = null,
                confirmLabel = "Create",
                onDismiss = { showAddField = false },
                onSave = { name, dataType, options ->
                    actionInFlight = true
                    scope.launch {
                        val field = GitHubManager.createProjectV2Field(context, current.id, name, dataType, options)
                        Toast.makeText(context, if (field != null) "Field created" else "Failed", Toast.LENGTH_SHORT).show()
                        actionInFlight = false
                        if (field != null) {
                            showAddField = false
                            loadDetail()
                        }
                    }
                }
            )
        }
        editSchemaField?.let { field ->
            ProjectV2SchemaFieldDialog(
                title = "Edit Field",
                initialField = field,
                confirmLabel = "Save",
                onDismiss = { editSchemaField = null },
                onSave = { name, _, options ->
                    actionInFlight = true
                    scope.launch {
                        val updated = GitHubManager.updateProjectV2Field(context, field, name, options)
                        Toast.makeText(context, if (updated != null) "Field updated" else "Failed", Toast.LENGTH_SHORT).show()
                        actionInFlight = false
                        if (updated != null) {
                            editSchemaField = null
                            loadDetail()
                        }
                    }
                }
            )
        }
        deleteSchemaField?.let { field ->
            AiModuleAlertDialog(
                onDismissRequest = { deleteSchemaField = null },
                title = "delete field?",
                confirmButton = {
                    AiModuleTextAction(
                        label = "delete",
                        enabled = !actionInFlight,
                        tint = AiModuleTheme.colors.error,
                        onClick = {
                            actionInFlight = true
                            scope.launch {
                                val ok = GitHubManager.deleteProjectV2Field(context, field.id)
                                Toast.makeText(context, if (ok) "Field deleted" else "Failed", Toast.LENGTH_SHORT).show()
                                actionInFlight = false
                                deleteSchemaField = null
                                if (ok) loadDetail()
                            }
                        },
                    )
                },
                dismissButton = {
                    AiModuleTextAction(
                        label = "cancel",
                        onClick = { deleteSchemaField = null },
                        tint = AiModuleTheme.colors.textSecondary,
                    )
                },
            ) {
                Text(
                    "Delete ${field.name}? Existing values for this field will be removed from the project.",
                    fontSize = 13.sp,
                    color = AiModuleTheme.colors.textSecondary,
                    fontFamily = JetBrainsMono,
                )
            }
        }
        editDraft?.let { item ->
            DraftIssueDialog(
                title = "Edit Draft Item",
                initialTitle = item.title,
                initialBody = item.body,
                confirmLabel = "Save",
                onDismiss = { editDraft = null },
                onSave = { title, body ->
                    actionInFlight = true
                    scope.launch {
                        val ok = GitHubManager.updateProjectV2DraftIssue(context, item.contentId, title, body)
                        Toast.makeText(context, if (ok) "Draft updated" else "Failed", Toast.LENGTH_SHORT).show()
                        actionInFlight = false
                        if (ok) {
                            editDraft = null
                            loadDetail()
                        }
                    }
                }
            )
        }
        editField?.let { item ->
            ProjectV2FieldDialog(
                item = item,
                fields = current.fields,
                onDismiss = { editField = null },
                onSave = { field, value ->
                    actionInFlight = true
                    scope.launch {
                        val ok = GitHubManager.updateProjectV2ItemFieldValue(context, current.id, item.id, field, value)
                        Toast.makeText(context, if (ok) "Field updated" else "Failed", Toast.LENGTH_SHORT).show()
                        actionInFlight = false
                        if (ok) {
                            editField = null
                            loadDetail()
                        }
                    }
                }
            )
        }
        deleteItem?.let { item ->
            AiModuleAlertDialog(
                onDismissRequest = { deleteItem = null },
                title = "delete item?",
                confirmButton = {
                    AiModuleTextAction(
                        label = "delete",
                        enabled = !actionInFlight,
                        tint = AiModuleTheme.colors.error,
                        onClick = {
                            actionInFlight = true
                            scope.launch {
                                val ok = GitHubManager.deleteProjectV2Item(context, current.id, item.id)
                                Toast.makeText(context, if (ok) "Item deleted" else "Failed", Toast.LENGTH_SHORT).show()
                                actionInFlight = false
                                deleteItem = null
                                if (ok) loadDetail()
                            }
                        },
                    )
                },
                dismissButton = {
                    AiModuleTextAction(
                        label = "cancel",
                        onClick = { deleteItem = null },
                        tint = AiModuleTheme.colors.textSecondary,
                    )
                },
            ) {
                Text(
                    "Remove ${item.title.ifBlank { "this item" }} from the project?",
                    fontSize = 13.sp,
                    color = AiModuleTheme.colors.textSecondary,
                    fontFamily = JetBrainsMono,
                )
            }
        }
    }
}

@Composable
private fun ProjectV2Summary(project: GHProjectV2Detail) {
    Column(Modifier.fillMaxWidth().ghGlassCard(14.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Rounded.Dashboard, null, Modifier.size(20.dp), tint = AiModuleTheme.colors.accent)
            Column(Modifier.weight(1f)) {
                Text(project.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = AiModuleTheme.colors.textPrimary)
                Text(project.updatedAt.take(10), fontSize = 11.sp, color = AiModuleTheme.colors.textMuted)
            }
            CountPill(if (project.closed) "Closed" else "Open", 0, if (project.closed) AiModuleTheme.colors.textSecondary else Color(0xFF34C759), showCount = false)
        }
        if (project.shortDescription.isNotBlank()) Text(project.shortDescription, fontSize = 13.sp, color = AiModuleTheme.colors.textSecondary)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CountPill("Items", project.itemsCount, AiModuleTheme.colors.accent)
            CountPill("Fields", project.fields.size, Color(0xFF34C759))
            CountPill("Views", project.views.size, AiModuleTheme.colors.textSecondary)
            CountPill("Workflows", project.workflows.size, AiModuleTheme.colors.textSecondary)
            CountPill(if (project.isPublic) "Public" else "Private", 0, AiModuleTheme.colors.textSecondary, showCount = false)
        }
    }
}

@Composable
private fun ProjectV2FieldsCard(
    fields: List<GHProjectV2Field>,
    onAdd: () -> Unit,
    onEdit: (GHProjectV2Field) -> Unit,
    onDelete: (GHProjectV2Field) -> Unit
) {
    Column(Modifier.fillMaxWidth().ghGlassCard(14.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.ViewColumn, null, Modifier.size(18.dp), tint = AiModuleTheme.colors.accent)
            Text("Fields", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = AiModuleTheme.colors.textPrimary, modifier = Modifier.weight(1f))
            IconButton(onClick = onAdd, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Rounded.Add, null, Modifier.size(18.dp), tint = AiModuleTheme.colors.accent)
            }
        }
        if (fields.isEmpty()) {
            Text("No Project V2 fields returned", fontSize = 12.sp, color = AiModuleTheme.colors.textMuted)
        } else {
            fields.forEach { field ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(AiModuleTheme.colors.background).padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(field.name, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = AiModuleTheme.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(projectV2FieldMeta(field), fontSize = 11.sp, color = AiModuleTheme.colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { onEdit(field) }, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Rounded.Edit, null, Modifier.size(17.dp), tint = AiModuleTheme.colors.accent)
                    }
                    IconButton(onClick = { onDelete(field) }, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Rounded.Delete, null, Modifier.size(17.dp), tint = Color(0xFFFF3B30))
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectV2ViewsCard(views: List<GHProjectV2View>) {
    Column(Modifier.fillMaxWidth().ghGlassCard(14.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.Dashboard, null, Modifier.size(18.dp), tint = AiModuleTheme.colors.accent)
            Text("Views", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = AiModuleTheme.colors.textPrimary, modifier = Modifier.weight(1f))
            CountPill("${views.size}", 0, AiModuleTheme.colors.textSecondary, showCount = false)
        }
        if (views.isEmpty()) {
            Text("No Project V2 views returned", fontSize = 12.sp, color = AiModuleTheme.colors.textMuted)
        } else {
            views.forEach { view ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(AiModuleTheme.colors.background).padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(view.name.ifBlank { "View #${view.number}" }, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = AiModuleTheme.colors.textPrimary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        CountPill(view.layout.ifBlank { "layout" }, 0, AiModuleTheme.colors.accent, showCount = false)
                    }
                    val meta = cleanProjectText(listOf(view.filter, view.updatedAt.take(10)))
                    if (meta.isNotBlank()) Text(meta, fontSize = 11.sp, color = AiModuleTheme.colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (view.fields.isNotEmpty()) {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            view.fields.take(8).forEach { CountPill(it, 0, AiModuleTheme.colors.textSecondary, showCount = false) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectV2WorkflowsCard(workflows: List<GHProjectV2Workflow>) {
    Column(Modifier.fillMaxWidth().ghGlassCard(14.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.ArrowForward, null, Modifier.size(18.dp), tint = AiModuleTheme.colors.accent)
            Text("Workflows", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = AiModuleTheme.colors.textPrimary, modifier = Modifier.weight(1f))
            CountPill("${workflows.count { it.enabled }} enabled", 0, Color(0xFF34C759), showCount = false)
        }
        if (workflows.isEmpty()) {
            Text("No Project V2 workflows returned", fontSize = 12.sp, color = AiModuleTheme.colors.textMuted)
        } else {
            workflows.forEach { workflow ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(AiModuleTheme.colors.background).padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(Modifier.size(9.dp).clip(CircleShape).background(if (workflow.enabled) Color(0xFF34C759) else AiModuleTheme.colors.textMuted))
                    Column(Modifier.weight(1f)) {
                        Text(workflow.name.ifBlank { "Workflow #${workflow.number}" }, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = AiModuleTheme.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(workflow.updatedAt.take(10), fontSize = 11.sp, color = AiModuleTheme.colors.textMuted)
                    }
                    CountPill(if (workflow.enabled) "enabled" else "disabled", 0, if (workflow.enabled) Color(0xFF34C759) else AiModuleTheme.colors.textSecondary, showCount = false)
                }
            }
        }
    }
}

@Composable
private fun ProjectV2ItemCard(
    item: GHProjectV2Item,
    fields: List<GHProjectV2Field>,
    onOpen: () -> Unit,
    onEditDraft: () -> Unit,
    onEditField: () -> Unit,
    onArchive: () -> Unit,
    onMoveTop: () -> Unit,
    onDelete: () -> Unit
) {
    Column(Modifier.fillMaxWidth().ghGlassCard(14.dp).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(if (item.archived) AiModuleTheme.colors.textMuted else AiModuleTheme.colors.accent))
            Column(Modifier.weight(1f)) {
                Text(item.title.ifBlank { item.type }, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AiModuleTheme.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(projectV2ItemSubtitle(item), fontSize = 11.sp, color = AiModuleTheme.colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (item.url.isNotBlank()) IconButton(onClick = onOpen) { Icon(Icons.Rounded.Language, null, Modifier.size(18.dp), tint = AiModuleTheme.colors.textSecondary) }
            if (item.contentType == "DraftIssue" && item.contentId.isNotBlank()) IconButton(onClick = onEditDraft) { Icon(Icons.Rounded.Edit, null, Modifier.size(18.dp), tint = AiModuleTheme.colors.accent) }
        }
        if (item.body.isNotBlank()) Text(item.body, fontSize = 12.sp, color = AiModuleTheme.colors.textSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            item.fieldValues.take(8).forEach { value -> FieldValuePill(value) }
            if (item.fieldValues.isEmpty()) CountPill("No field values", 0, AiModuleTheme.colors.textMuted, showCount = false)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ProjectChip("Fields", false, onEditField)
            ProjectChip(if (item.archived) "Unarchive" else "Archive", false, onArchive)
            ProjectChip("Move top", false, onMoveTop)
            if (fields.isNotEmpty()) ProjectChip("${fields.size} fields", false) {}
            ProjectChip("Delete", false, onDelete)
        }
    }
}

@Composable
private fun ProjectV2EditorDialog(
    project: GHProjectV2Detail,
    onDismiss: () -> Unit,
    onSave: (String, String, String, Boolean, Boolean) -> Unit
) {
    var title by remember(project.id) { mutableStateOf(project.title) }
    var description by remember(project.id) { mutableStateOf(project.shortDescription) }
    var readme by remember(project.id) { mutableStateOf(project.readme) }
    var closed by remember(project.id) { mutableStateOf(project.closed) }
    var isPublic by remember(project.id) { mutableStateOf(project.isPublic) }
    AiModuleAlertDialog(
        onDismissRequest = onDismiss,
        title = "edit project v2",
        confirmButton = {
            AiModuleTextAction(
                label = "save",
                enabled = title.isNotBlank(),
                onClick = { onSave(title, description, readme, closed, isPublic) },
                tint = AiModuleTheme.colors.accent,
            )
        },
        dismissButton = {
            AiModuleTextAction(
                label = "cancel",
                onClick = onDismiss,
                tint = AiModuleTheme.colors.textSecondary,
            )
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AiModuleTextField(title, { title = it }, label = "Title")
            AiModuleTextField(description, { description = it }, label = "Description", minLines = 2, maxLines = 4)
            AiModuleTextField(readme, { readme = it }, label = "Readme", minLines = 3, maxLines = 6)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ProjectChip("Open", !closed) { closed = false }
                ProjectChip("Closed", closed) { closed = true }
                ProjectChip("Public", isPublic) { isPublic = !isPublic }
            }
        }
    }
}

@Composable
private fun DraftIssueDialog(
    title: String,
    initialTitle: String,
    initialBody: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var itemTitle by remember(initialTitle) { mutableStateOf(initialTitle) }
    var body by remember(initialBody) { mutableStateOf(initialBody) }
    AiModuleAlertDialog(
        onDismissRequest = onDismiss,
        title = title.lowercase(),
        confirmButton = {
            AiModuleTextAction(
                label = confirmLabel.lowercase(),
                enabled = itemTitle.isNotBlank(),
                onClick = { onSave(itemTitle, body) },
                tint = AiModuleTheme.colors.accent,
            )
        },
        dismissButton = {
            AiModuleTextAction(label = "cancel", onClick = onDismiss, tint = AiModuleTheme.colors.textSecondary)
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AiModuleTextField(itemTitle, { itemTitle = it }, label = "Title")
            AiModuleTextField(body, { body = it }, label = "Body", minLines = 4, maxLines = 8)
        }
    }
}

@Composable
private fun ProjectV2FieldDialog(
    item: GHProjectV2Item,
    fields: List<GHProjectV2Field>,
    onDismiss: () -> Unit,
    onSave: (GHProjectV2Field, String) -> Unit
) {
    val editableFields = fields.filter { it.dataType in listOf("TEXT", "NUMBER", "DATE", "SINGLE_SELECT") }
    var selectedField by remember(fields) { mutableStateOf(editableFields.firstOrNull()) }
    var value by remember(item.id, selectedField?.id) {
        mutableStateOf(item.fieldValues.firstOrNull { it.fieldId == selectedField?.id }?.value.orEmpty())
    }
    AiModuleAlertDialog(
        onDismissRequest = onDismiss,
        title = "edit field",
        confirmButton = {
            AiModuleTextAction(
                label = "save",
                enabled = selectedField != null,
                onClick = { selectedField?.let { onSave(it, value) } },
                tint = AiModuleTheme.colors.accent,
            )
        },
        dismissButton = {
            AiModuleTextAction(label = "cancel", onClick = onDismiss, tint = AiModuleTheme.colors.textSecondary)
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (editableFields.isEmpty()) {
                Text(
                    "No editable Project V2 fields returned",
                    fontSize = 13.sp,
                    color = AiModuleTheme.colors.textSecondary,
                    fontFamily = JetBrainsMono,
                )
            } else {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    editableFields.forEach { field ->
                        ProjectChip(field.name, selectedField?.id == field.id) {
                            selectedField = field
                            value = item.fieldValues.firstOrNull { it.fieldId == field.id }?.value.orEmpty()
                        }
                    }
                }
                selectedField?.let { field ->
                    if (field.dataType == "SINGLE_SELECT") {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            field.options.forEach { option ->
                                ProjectChip(option.name, value.equals(option.name, ignoreCase = true) || value == option.id) { value = option.name }
                            }
                        }
                    } else {
                        AiModuleTextField(
                            value = value,
                            onValueChange = { value = it },
                            label = fieldValueLabel(field),
                            minLines = if (field.dataType == "TEXT") 3 else 1,
                            maxLines = if (field.dataType == "TEXT") 6 else 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectV2SchemaFieldDialog(
    title: String,
    initialField: GHProjectV2Field?,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onSave: (String, String, List<String>) -> Unit
) {
    var name by remember(initialField?.id) { mutableStateOf(initialField?.name ?: "") }
    var dataType by remember(initialField?.id) { mutableStateOf(initialField?.dataType ?: "TEXT") }
    var optionsRaw by remember(initialField?.id) { mutableStateOf(initialField?.options?.joinToString("\n") { it.name } ?: "Todo\nIn progress\nDone") }
    val optionList = remember(optionsRaw) {
        optionsRaw.split('\n', ',').map { it.trim() }.filter { it.isNotBlank() }.distinct()
    }
    val canSave = name.isNotBlank() && (dataType != "SINGLE_SELECT" || optionList.isNotEmpty())
    AiModuleAlertDialog(
        onDismissRequest = onDismiss,
        title = title.lowercase(),
        confirmButton = {
            AiModuleTextAction(
                label = confirmLabel.lowercase(),
                enabled = canSave,
                onClick = { onSave(name.trim(), dataType, optionList) },
                tint = AiModuleTheme.colors.accent,
            )
        },
        dismissButton = {
            AiModuleTextAction(label = "cancel", onClick = onDismiss, tint = AiModuleTheme.colors.textSecondary)
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AiModuleTextField(name, { name = it }, label = "Field name")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("TEXT", "NUMBER", "DATE", "SINGLE_SELECT").forEach { type ->
                    ProjectChip(type.replace('_', ' '), dataType == type, enabled = initialField == null) { dataType = type }
                }
            }
            if (dataType == "SINGLE_SELECT") {
                AiModuleTextField(
                    value = optionsRaw,
                    onValueChange = { optionsRaw = it },
                    label = "Single-select options",
                    minLines = 4,
                    maxLines = 8,
                )
                Text(
                    "One option per line. Saving replaces the current option set.",
                    fontSize = 11.sp,
                    color = AiModuleTheme.colors.textMuted,
                    fontFamily = JetBrainsMono,
                )
            } else if (initialField != null) {
                Text(
                    "GitHub does not allow changing a field data type after creation.",
                    fontSize = 11.sp,
                    color = AiModuleTheme.colors.textMuted,
                    fontFamily = JetBrainsMono,
                )
            }
        }
    }
}

@Composable
private fun FieldValuePill(value: GHProjectV2ItemFieldValue) {
    CountPill("${value.fieldName}: ${value.value.ifBlank { "-" }}", 0, AiModuleTheme.colors.textSecondary, showCount = false)
}

private fun projectV2FieldMeta(field: GHProjectV2Field): String {
    val options = if (field.options.isNotEmpty()) " - ${field.options.size} options" else ""
    return "${field.dataType.ifBlank { "FIELD" }}$options"
}

private fun cleanProjectText(values: List<String>): String =
    values.filter { it.isNotBlank() && it != "null" }.joinToString(" - ")

private fun projectV2ItemSubtitle(item: GHProjectV2Item): String {
    val number = if (item.number > 0) " #${item.number}" else ""
    val state = item.state.takeIf { it.isNotBlank() }?.let { " - $it" }.orEmpty()
    val updated = item.updatedAt.take(10).takeIf { it.isNotBlank() }?.let { " - $it" }.orEmpty()
    return "${item.contentType.ifBlank { item.type }}$number$state$updated"
}

private fun fieldValueLabel(field: GHProjectV2Field): String = when (field.dataType) {
    "NUMBER" -> "${field.name} (number)"
    "DATE" -> "${field.name} (YYYY-MM-DD)"
    else -> field.name
}

@Composable
private fun ClassicProjectDetail(
    project: GHProject,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    onChanged: (GHProject) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentProject by remember(project.id) { mutableStateOf(project) }
    var columns by remember(project.id) { mutableStateOf<List<GHProjectColumn>>(emptyList()) }
    val cardsByColumn = remember(project.id) { mutableStateMapOf<Long, List<GHProjectCard>>() }
    var loading by remember(project.id) { mutableStateOf(true) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showColumnDialog by remember { mutableStateOf(false) }
    var cardTargetColumn by remember { mutableStateOf<GHProjectColumn?>(null) }
    var moveTarget by remember { mutableStateOf<Pair<GHProjectCard, GHProjectColumn>?>(null) }
    var actionInFlight by remember { mutableStateOf(false) }

    fun loadProject() {
        loading = true
        scope.launch {
            GitHubManager.getProject(context, currentProject.id)?.let {
                currentProject = it
                onChanged(it)
            }
            columns = GitHubManager.getProjectColumns(context, currentProject.id)
            cardsByColumn.clear()
            columns.forEach { column ->
                cardsByColumn[column.id] = GitHubManager.getProjectCards(context, column.id)
            }
            loading = false
        }
    }

    LaunchedEffect(project.id) { loadProject() }

    GitHubScreenFrame(
        title = "> ${currentProject.name.lowercase()}",
        subtitle = "classic project #${currentProject.number}",
        onBack = onBack,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (currentProject.htmlUrl.isNotBlank()) {
                    GitHubTopBarAction(
                        glyph = GhGlyphs.OPEN_NEW,
                        onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(currentProject.htmlUrl))) },
                        tint = AiModuleTheme.colors.textSecondary,
                        contentDescription = "open project",
                    )
                }
                GitHubTopBarAction(
                    glyph = GhGlyphs.EDIT,
                    onClick = { showEditDialog = true },
                    tint = AiModuleTheme.colors.accent,
                    contentDescription = "edit project",
                )
                GitHubTopBarAction(
                    glyph = GhGlyphs.DELETE,
                    onClick = { showDeleteDialog = true },
                    tint = AiModuleTheme.colors.error,
                    contentDescription = "delete project",
                )
            }
        },
    ) {

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                AiModuleSpinner(label = "loading project")
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { ProjectDetailSummary(currentProject, columns, cardsByColumn.values.sumOf { it.size }) }
                item {
                    GitHubTerminalButton("add column", onClick = { showColumnDialog = true }, color = AiModuleTheme.colors.accent, modifier = Modifier.fillMaxWidth())
                }
                items(columns) { column ->
                    ProjectColumnCard(
                        column = column,
                        cards = cardsByColumn[column.id].orEmpty(),
                        allColumns = columns,
                        onAddCard = { cardTargetColumn = column },
                        onMoveCard = { card -> moveTarget = card to column },
                        onDeleteCard = { card ->
                            actionInFlight = true
                            scope.launch {
                                val ok = GitHubManager.deleteProjectCard(context, card.id)
                                Toast.makeText(context, if (ok) "Card deleted" else "Failed", Toast.LENGTH_SHORT).show()
                                actionInFlight = false
                                if (ok) loadProject()
                            }
                        }
                    )
                }
                if (columns.isEmpty()) item { EmptyProjectsCard("No columns yet") }
            }
        }
    }

    if (showEditDialog) {
        ProjectEditorDialog(
            title = "Edit Project",
            initialName = currentProject.name,
            initialBody = currentProject.body,
            initialState = currentProject.state,
            showState = true,
            confirmLabel = "Save",
            onDismiss = { showEditDialog = false },
            onSave = { name, body, state ->
                actionInFlight = true
                scope.launch {
                    val ok = GitHubManager.updateProject(context, currentProject.id, name, body, state)
                    Toast.makeText(context, if (ok) "Project updated" else "Failed", Toast.LENGTH_SHORT).show()
                    actionInFlight = false
                    if (ok) {
                        showEditDialog = false
                        loadProject()
                    }
                }
            }
        )
    }

    if (showDeleteDialog) {
        AiModuleAlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = "delete project?",
            confirmButton = {
                AiModuleTextAction(
                    label = "delete",
                    enabled = !actionInFlight,
                    tint = AiModuleTheme.colors.error,
                    onClick = {
                        actionInFlight = true
                        scope.launch {
                            val ok = GitHubManager.deleteProject(context, currentProject.id)
                            Toast.makeText(context, if (ok) "Deleted" else "Failed", Toast.LENGTH_SHORT).show()
                            actionInFlight = false
                            showDeleteDialog = false
                            if (ok) onDeleted()
                        }
                    },
                )
            },
            dismissButton = {
                AiModuleTextAction(
                    label = "cancel",
                    onClick = { showDeleteDialog = false },
                    tint = AiModuleTheme.colors.textSecondary,
                )
            },
        ) {
            Text(
                "Delete ${currentProject.name} and its cards?",
                fontSize = 13.sp,
                color = AiModuleTheme.colors.textSecondary,
                fontFamily = JetBrainsMono,
            )
        }
    }

    if (showColumnDialog) {
        TextInputDialog(
            title = "New Column",
            label = "Column name",
            confirmLabel = "Create",
            onDismiss = { showColumnDialog = false },
            onConfirm = { name ->
                scope.launch {
                    val column = GitHubManager.createProjectColumn(context, currentProject.id, name)
                    Toast.makeText(context, if (column != null) "Column created" else "Failed", Toast.LENGTH_SHORT).show()
                    if (column != null) {
                        showColumnDialog = false
                        loadProject()
                    }
                }
            }
        )
    }

    cardTargetColumn?.let { column ->
        TextInputDialog(
            title = "New Card",
            label = "Note",
            confirmLabel = "Create",
            minLines = 4,
            onDismiss = { cardTargetColumn = null },
            onConfirm = { note ->
                scope.launch {
                    val card = GitHubManager.createProjectCard(context, column.id, note)
                    Toast.makeText(context, if (card != null) "Card created" else "Failed", Toast.LENGTH_SHORT).show()
                    if (card != null) {
                        cardTargetColumn = null
                        loadProject()
                    }
                }
            }
        )
    }

    moveTarget?.let { (card, fromColumn) ->
        MoveCardDialog(
            card = card,
            fromColumn = fromColumn,
            columns = columns,
            onDismiss = { moveTarget = null },
            onMove = { targetColumn ->
                scope.launch {
                    val ok = GitHubManager.moveProjectCard(context, card.id, "bottom", targetColumn.id)
                    Toast.makeText(context, if (ok) "Card moved" else "Failed", Toast.LENGTH_SHORT).show()
                    if (ok) {
                        moveTarget = null
                        loadProject()
                    }
                }
            }
        )
    }
}

@Composable
private fun ProjectDetailSummary(project: GHProject, columns: List<GHProjectColumn>, cards: Int) {
    Column(Modifier.fillMaxWidth().ghGlassCard(14.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Rounded.ViewColumn, null, Modifier.size(20.dp), tint = AiModuleTheme.colors.accent)
            Column(Modifier.weight(1f)) {
                Text(project.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = AiModuleTheme.colors.textPrimary)
                Text(project.updatedAt.take(10), fontSize = 11.sp, color = AiModuleTheme.colors.textMuted)
            }
            CountPill(project.state, 0, if (project.state == "open") Color(0xFF34C759) else AiModuleTheme.colors.textSecondary, showCount = false)
        }
        if (project.body.isNotBlank()) Text(project.body, fontSize = 13.sp, color = AiModuleTheme.colors.textSecondary)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CountPill("Columns", columns.size, AiModuleTheme.colors.accent)
            CountPill("Cards", cards, Color(0xFF34C759))
        }
    }
}

@Composable
private fun ProjectColumnCard(
    column: GHProjectColumn,
    cards: List<GHProjectCard>,
    allColumns: List<GHProjectColumn>,
    onAddCard: () -> Unit,
    onMoveCard: (GHProjectCard) -> Unit,
    onDeleteCard: (GHProjectCard) -> Unit
) {
    Column(Modifier.fillMaxWidth().ghGlassCard(14.dp).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.ViewColumn, null, Modifier.size(18.dp), tint = AiModuleTheme.colors.accent)
            Text(column.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AiModuleTheme.colors.textPrimary, modifier = Modifier.weight(1f))
            CountPill("Cards", cards.size, AiModuleTheme.colors.textSecondary)
            IconButton(onClick = onAddCard) { Icon(Icons.Rounded.Add, null, Modifier.size(18.dp), tint = AiModuleTheme.colors.accent) }
        }
        if (cards.isEmpty()) {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(AiModuleTheme.colors.background).padding(16.dp), contentAlignment = Alignment.Center) {
                Text("No cards", fontSize = 12.sp, color = AiModuleTheme.colors.textMuted)
            }
        } else {
            cards.forEach { card ->
                ProjectCardRow(card, canMove = allColumns.size > 1, onMove = { onMoveCard(card) }, onDelete = { onDeleteCard(card) })
            }
        }
    }
}

@Composable
private fun ProjectCardRow(card: GHProjectCard, canMove: Boolean, onMove: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(AiModuleTheme.colors.background).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (card.archived) AiModuleTheme.colors.textMuted else AiModuleTheme.colors.accent))
        Column(Modifier.weight(1f)) {
            Text(card.note.ifBlank { card.contentUrl.ifBlank { "Linked card" } }, fontSize = 13.sp, color = AiModuleTheme.colors.textPrimary, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(card.updatedAt.take(10), fontSize = 10.sp, color = AiModuleTheme.colors.textMuted)
        }
        if (canMove) IconButton(onClick = onMove) { Icon(Icons.Rounded.ArrowForward, null, Modifier.size(18.dp), tint = AiModuleTheme.colors.accent) }
        IconButton(onClick = onDelete) { Icon(Icons.Rounded.Close, null, Modifier.size(18.dp), tint = Color(0xFFFF3B30)) }
    }
}

@Composable
private fun ProjectEditorDialog(
    title: String,
    initialName: String,
    initialBody: String,
    initialState: String,
    showState: Boolean,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var body by remember(initialBody) { mutableStateOf(initialBody) }
    var state by remember(initialState) { mutableStateOf(initialState.ifBlank { "open" }) }
    AiModuleAlertDialog(
        onDismissRequest = onDismiss,
        title = title.lowercase(),
        confirmButton = {
            AiModuleTextAction(
                label = confirmLabel.lowercase(),
                enabled = name.isNotBlank(),
                onClick = { onSave(name, body, state) },
                tint = AiModuleTheme.colors.accent,
            )
        },
        dismissButton = {
            AiModuleTextAction(label = "cancel", onClick = onDismiss, tint = AiModuleTheme.colors.textSecondary)
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AiModuleTextField(name, { name = it }, label = "Name")
            AiModuleTextField(body, { body = it }, label = "Description", minLines = 3, maxLines = 6)
            if (showState) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ProjectChip("Open", state == "open") { state = "open" }
                    ProjectChip("Closed", state == "closed") { state = "closed" }
                }
            }
        }
    }
}

@Composable
private fun TextInputDialog(
    title: String,
    label: String,
    confirmLabel: String,
    minLines: Int = 1,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by remember { mutableStateOf("") }
    AiModuleAlertDialog(
        onDismissRequest = onDismiss,
        title = title.lowercase(),
        confirmButton = {
            AiModuleTextAction(
                label = confirmLabel.lowercase(),
                enabled = value.isNotBlank(),
                onClick = { onConfirm(value) },
                tint = AiModuleTheme.colors.accent,
            )
        },
        dismissButton = {
            AiModuleTextAction(label = "cancel", onClick = onDismiss, tint = AiModuleTheme.colors.textSecondary)
        },
    ) {
        AiModuleTextField(
            value = value,
            onValueChange = { value = it },
            label = label,
            minLines = minLines,
            maxLines = if (minLines > 1) 8 else 1,
        )
    }
}

@Composable
private fun MoveCardDialog(
    card: GHProjectCard,
    fromColumn: GHProjectColumn,
    columns: List<GHProjectColumn>,
    onDismiss: () -> Unit,
    onMove: (GHProjectColumn) -> Unit
) {
    AiModuleAlertDialog(
        onDismissRequest = onDismiss,
        title = "move card",
        confirmButton = {},
        dismissButton = {
            AiModuleTextAction(label = "cancel", onClick = onDismiss, tint = AiModuleTheme.colors.textSecondary)
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                card.note.ifBlank { "Linked card" },
                fontSize = 13.sp,
                color = AiModuleTheme.colors.textSecondary,
                fontFamily = JetBrainsMono,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            columns.filter { it.id != fromColumn.id }.forEach { column ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(AiModuleTheme.colors.background).clickable { onMove(column) }.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AiModuleGlyph(GhGlyphs.ARROW_RIGHT, tint = AiModuleTheme.colors.accent)
                    Text(column.name, fontSize = 13.sp, color = AiModuleTheme.colors.textPrimary, fontFamily = JetBrainsMono)
                }
            }
        }
    }
}

@Composable
private fun ProjectChip(label: String, selected: Boolean, onClick: () -> Unit) {
    ProjectChip(label = label, selected = selected, enabled = true, onClick = onClick)
}

@Composable
private fun ProjectChip(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(8.dp))
            .background(if (selected) AiModuleTheme.colors.accent.copy(0.15f) else AiModuleTheme.colors.surface)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Text(label, fontSize = 12.sp, color = if (!enabled) AiModuleTheme.colors.textMuted else if (selected) AiModuleTheme.colors.accent else AiModuleTheme.colors.textPrimary, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun CountPill(label: String, count: Int, color: Color, showCount: Boolean = true) {
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).background(color.copy(0.10f)).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(label, fontSize = 11.sp, color = color, fontWeight = FontWeight.Medium)
        if (showCount) Text("$count", fontSize = 11.sp, color = color)
    }
}

@Composable
private fun EmptyProjectsCard(message: String) {
    Box(
        Modifier.fillMaxWidth().ghGlassCard(14.dp).padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(message, fontSize = 14.sp, color = AiModuleTheme.colors.textMuted)
    }
}
