package com.glassfiles.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glassfiles.data.Strings
import com.glassfiles.data.github.GHRepo
import com.glassfiles.data.github.GHWorkflow
import com.glassfiles.data.github.GHWorkflowDispatchInput
import com.glassfiles.data.github.GHWorkflowDispatchSchema
import com.glassfiles.data.github.GitHubManager
import com.glassfiles.ui.theme.Blue
import com.glassfiles.ui.theme.SurfaceLight
import com.glassfiles.ui.theme.SurfaceWhite
import com.glassfiles.ui.theme.TextPrimary
import com.glassfiles.ui.theme.TextSecondary
import com.glassfiles.ui.theme.TextTertiary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal data class DynamicBuildItem(
    val category: String,
    val title: String,
    val subtitle: String,
    val workflow: GHWorkflow,
    val schema: GHWorkflowDispatchSchema,
    val icon: ImageVector
)

@Composable
internal fun BuildsScreen(
    repo: GHRepo,
    branches: List<String>,
    workflows: List<GHWorkflow>,
    selectedBranch: String,
    onBuildStarted: (Long?) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val schemas = remember { mutableStateMapOf<String, GHWorkflowDispatchSchema>() }
    var loading by remember { mutableStateOf(true) }
    var selectedBuild by remember { mutableStateOf<DynamicBuildItem?>(null) }
    var launching by remember { mutableStateOf(false) }

    LaunchedEffect(workflows, selectedBranch) {
        loading = true
        schemas.clear()
        workflows.forEach { workflow ->
            val schema = GitHubManager.getWorkflowDispatchSchema(context, repo.owner, repo.name, workflow.path, selectedBranch)
            if (schema != null) schemas[workflow.path] = schema
        }
        loading = false
    }

    val dynamicBuilds = workflows.mapNotNull { workflow ->
        val schema = schemas[workflow.path] ?: return@mapNotNull null
        mapWorkflowToBuildItem(workflow, schema)
    }.sortedWith(compareBy<DynamicBuildItem> { it.category }.thenBy { it.title })

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(SurfaceWhite)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.Build, null, tint = Blue)
                    Text("Сборщик", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                }
                Text(
                    "Workflows and inputs are loaded dynamically from GitHub Actions workflow_dispatch configuration.",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    lineHeight = 18.sp
                )
                Text("Branch source: GitHub API", fontSize = 11.sp, color = TextTertiary)
            }
        }

        if (loading) {
            item {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Blue, modifier = Modifier.size(26.dp), strokeWidth = 2.dp)
                }
            }
        } else if (dynamicBuilds.isEmpty()) {
            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceWhite)
                        .padding(16.dp)
                ) {
                    Text(
                        "No workflow_dispatch workflows found in this repository.",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            var lastCategory = ""
            dynamicBuilds.forEach { build ->
                if (build.category != lastCategory) {
                    item {
                        Text(
                            build.category,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            modifier = Modifier.padding(top = 4.dp, start = 2.dp)
                        )
                    }
                    lastCategory = build.category
                }
                item {
                    DynamicBuildCard(build = build, onClick = { selectedBuild = build })
                }
            }
        }
    }

    if (selectedBuild != null) {
        DynamicBuildWorkflowDialog(
            build = selectedBuild!!,
            branches = branches.filter { it.isNotBlank() },
            defaultBranch = selectedBranch.ifBlank { repo.defaultBranch },
            launching = launching,
            onDismiss = { if (!launching) selectedBuild = null },
            onLaunch = { branch, inputs ->
                launching = true
                scope.launch {
                    val build = selectedBuild!!
                    val knownRunIds = GitHubManager
                        .getWorkflowRuns(context, repo.owner, repo.name, build.workflow.id, perPage = 10)
                        .map { it.id }
                        .toSet()
                    val ok = GitHubManager.dispatchWorkflow(
                        context = context,
                        owner = repo.owner,
                        repo = repo.name,
                        workflowId = build.workflow.path.substringAfterLast('/'),
                        ref = branch,
                        inputs = inputs
                    )
                    val newRunId = if (ok) {
                        findNewWorkflowDispatchRun(
                            repo = repo,
                            workflow = build.workflow,
                            branch = branch,
                            knownRunIds = knownRunIds,
                            context = context
                        )
                    } else {
                        null
                    }
                    launching = false
                    if (ok) {
                        Toast.makeText(context, Strings.done, Toast.LENGTH_SHORT).show()
                        selectedBuild = null
                        onBuildStarted(newRunId)
                    } else {
                        Toast.makeText(context, Strings.error, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }
}

private fun mapWorkflowToBuildItem(
    workflow: GHWorkflow,
    schema: GHWorkflowDispatchSchema
): DynamicBuildItem {
    return DynamicBuildItem(
        category = "workflow_dispatch",
        title = workflow.name.ifBlank { workflow.path.substringAfterLast('/') },
        subtitle = workflow.path,
        workflow = workflow,
        schema = schema,
        icon = Icons.Rounded.Storage
    )
}

private suspend fun findNewWorkflowDispatchRun(
    repo: GHRepo,
    workflow: GHWorkflow,
    branch: String,
    knownRunIds: Set<Long>,
    context: android.content.Context
): Long? {
    repeat(10) {
        delay(1500)
        val runs = GitHubManager.getWorkflowRuns(context, repo.owner, repo.name, workflow.id, perPage = 10)
        val run = runs.firstOrNull { candidate ->
            val newRun = candidate.id !in knownRunIds
            val dispatchRun = candidate.event == "workflow_dispatch"
            val branchMatches = candidate.branch.isBlank() || branch.isBlank() || candidate.branch == branch
            newRun && dispatchRun && branchMatches
        }
        if (run != null) return run.id
    }
    return null
}

@Composable
private fun DynamicBuildCard(
    build: DynamicBuildItem,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceWhite)
            .clickable(onClick = onClick)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Blue.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(build.icon, null, tint = Blue)
        }
        Column(Modifier.weight(1f)) {
            Text(build.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(build.subtitle, fontSize = 11.sp, color = TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${build.schema.inputs.size} inputs", fontSize = 11.sp, color = TextTertiary)
        }
        Box(
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF16A34A).copy(alpha = 0.12f))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text("Run", color = Color(0xFF16A34A), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun DynamicBuildWorkflowDialog(
    build: DynamicBuildItem,
    branches: List<String>,
    defaultBranch: String,
    launching: Boolean,
    onDismiss: () -> Unit,
    onLaunch: (String, Map<String, String>) -> Unit
) {
    val branchOptions = remember(branches, defaultBranch) {
        branches.distinct().filter { it.isNotBlank() }
    }
    var selectedBranch by remember(build.workflow.path, branchOptions) {
        mutableStateOf(branchOptions.firstOrNull { it == defaultBranch } ?: branchOptions.firstOrNull().orEmpty())
    }
    val inputValues = remember(build.workflow.path) { mutableStateMapOf<String, String>() }
    build.schema.inputs.forEach { input -> if (!inputValues.containsKey(input.key)) inputValues[input.key] = input.defaultValue }
    var branchMenu by remember { mutableStateOf(false) }
    var choiceTarget by remember { mutableStateOf<GHWorkflowDispatchInput?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(build.title, fontWeight = FontWeight.Bold)
                Text("Workflow build settings", fontSize = 12.sp, color = TextSecondary)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                BuildDropdownField("Branch", selectedBranch, { branchMenu = true })
                build.schema.inputs.forEach { input ->
                    if (input.options.isNotEmpty()) {
                        BuildDropdownField(
                            label = inputLabel(input.key),
                            value = inputValues[input.key].orEmpty().ifBlank { input.defaultValue },
                            onClick = { choiceTarget = input }
                        )
                    } else {
                        OutlinedTextField(
                            value = inputValues[input.key].orEmpty(),
                            onValueChange = { inputValues[input.key] = it },
                            label = { Text(inputLabel(input.key)) },
                            placeholder = { if (input.defaultValue.isNotBlank()) Text(input.defaultValue) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (input.description.isNotBlank()) {
                        Text(input.description, fontSize = 11.sp, color = TextTertiary)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val inputs = build.schema.inputs.associate { input ->
                        input.key to (inputValues[input.key].orEmpty().ifBlank { input.defaultValue })
                    }
                    onLaunch(selectedBranch, inputs)
                },
                enabled = !launching && selectedBranch.isNotBlank()
            ) {
                if (launching) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Blue)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Rounded.PlayArrow, null, tint = Color(0xFF16A34A))
                        Text("Run workflow", color = Color(0xFF16A34A), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !launching) {
                Text(Strings.cancel)
            }
        }
    )

    if (branchMenu) {
        SimpleChoiceDialog("Branch", branchOptions, selectedBranch, { selectedBranch = it; branchMenu = false }) { branchMenu = false }
    }
    if (choiceTarget != null) {
        val target = choiceTarget!!
        SimpleChoiceDialog(
            inputLabel(target.key),
            target.options,
            inputValues[target.key].orEmpty().ifBlank { target.defaultValue },
            { picked -> inputValues[target.key] = picked; choiceTarget = null },
            { choiceTarget = null }
        )
    }
}

private fun inputLabel(key: String): String = when (key) {
    "release_type" -> "Release Type"
    "kernel_version" -> "Kernel Version to Build"
    "feature_set" -> "Feature Set"
    "ksu_commit" -> "KSU Commit"
    else -> key.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

@Composable
private fun BuildDropdownField(
    label: String,
    value: String,
    onClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, fontSize = 12.sp, color = TextSecondary)
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceLight)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(value, color = TextPrimary, fontSize = 14.sp)
            Icon(Icons.Rounded.ArrowDropDown, null, tint = Blue)
        }
    }
}

@Composable
private fun SimpleChoiceDialog(
    title: String,
    options: List<String>,
    selected: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                options.forEach { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (option == selected) Blue.copy(alpha = 0.12f) else SurfaceWhite)
                            .clickable { onPick(option) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(option, color = if (option == selected) Blue else TextPrimary)
                        if (option == selected) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(Blue))
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(Strings.cancel) }
        }
    )
}
