package com.glassfiles.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.glassfiles.ui.components.AiModuleAlertDialog
import com.glassfiles.ui.components.AiModuleCard
import com.glassfiles.ui.components.AiModuleCheckRow
import com.glassfiles.ui.components.AiModuleGlyph
import com.glassfiles.ui.components.AiModuleGlyphAction
import com.glassfiles.ui.components.AiModuleIcon as Icon
import com.glassfiles.ui.components.AiModulePillButton
import com.glassfiles.ui.components.AiModuleText as Text
import com.glassfiles.ui.components.AiModuleTextAction
import com.glassfiles.ui.components.AiModuleTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glassfiles.data.Strings
import com.glassfiles.data.github.*
import com.glassfiles.ui.components.AiModuleHairline
import com.glassfiles.ui.components.AiModulePageBar
import com.glassfiles.ui.components.AiModuleSpinner
import com.glassfiles.ui.theme.AiModuleSurface
import com.glassfiles.ui.theme.AiModuleTheme
import com.glassfiles.ui.theme.JetBrainsMono
import kotlinx.coroutines.launch

@Composable
internal fun GistsScreen(
    onBack: () -> Unit,
    onMinimize: () -> Unit,
    onClose: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var gists by remember { mutableStateOf<List<GHGist>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var showCreate by remember { mutableStateOf(false) }
    var viewingGist by remember { mutableStateOf<GHGist?>(null) }
    var gistContent by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    val palette = AiModuleTheme.colors

    LaunchedEffect(Unit) {
        gists = GitHubManager.getGists(context)
        loading = false
    }

    AiModuleSurface {
        if (viewingGist != null) {
            val current = viewingGist!!
            Column(Modifier.fillMaxSize().background(palette.background)) {
                GitHubPageBar(
                    title = "> ${current.description.ifBlank { "gist" }.lowercase()}",
                    subtitle = "${current.files.size} file${if (current.files.size == 1) "" else "s"}",
                    onBack = { viewingGist = null; gistContent = emptyMap() },
                    trailing = {
                        GitHubTopBarAction(
                            glyph = GhGlyphs.DELETE,
                            onClick = {
                                scope.launch {
                                    GitHubManager.deleteGist(context, current.id)
                                    gists = GitHubManager.getGists(context)
                                    viewingGist = null
                                    gistContent = emptyMap()
                                }
                            },
                            tint = palette.error,
                            contentDescription = "delete gist",
                        )
                        if (onClose != null) {
                            GitHubTopBarAction(GhGlyphs.CLOSE, onClose, palette.error, contentDescription = "close")
                        }
                    },
                )
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                ) {
                    gistContent.forEach { (n, t) ->
                        item {
                            Text(
                                text = n,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = palette.accent,
                                fontFamily = JetBrainsMono,
                            )
                            Spacer(Modifier.height(4.dp))
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .border(1.dp, palette.border, RoundedCornerShape(6.dp))
                                    .background(palette.surface)
                                    .horizontalScroll(rememberScrollState())
                                    .padding(10.dp),
                            ) {
                                Text(
                                    text = t,
                                    fontSize = 11.sp,
                                    fontFamily = JetBrainsMono,
                                    color = palette.textPrimary,
                                    lineHeight = 16.sp,
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }
            }
            return@AiModuleSurface
        }

        Column(Modifier.fillMaxSize().background(palette.background)) {
            GitHubPageBar(
                title = "> gists",
                subtitle = if (loading) "loading…" else "${gists.size} gist${if (gists.size == 1) "" else "s"}",
                onBack = onBack,
                trailing = {
                    GitHubTopBarAction(GhGlyphs.PLUS, { showCreate = true }, palette.accent, contentDescription = "create gist")
                    GitHubTopBarAction(GhGlyphs.PIP, onMinimize, palette.textSecondary, contentDescription = "minimize")
                    if (onClose != null) {
                        GitHubTopBarAction(GhGlyphs.CLOSE, onClose, palette.error, contentDescription = "close")
                    }
                },
            )
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    AiModuleSpinner(label = "loading gists…")
                }
                gists.isEmpty() -> GitHubMonoEmpty(
                    title = "no gists yet",
                    subtitle = "create one with the [+] button",
                )
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(gists) { g ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch {
                                        gistContent = GitHubManager.getGistContent(context, g.id)
                                        viewingGist = g
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Rounded.Description,
                                null,
                                Modifier.size(18.dp),
                                tint = palette.accent,
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = g.description.ifBlank { g.files.firstOrNull() ?: "Gist" },
                                    fontSize = 13.sp,
                                    fontFamily = JetBrainsMono,
                                    color = palette.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(2.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        "${g.files.size} files",
                                        fontSize = 10.sp,
                                        fontFamily = JetBrainsMono,
                                        color = palette.textMuted,
                                    )
                                    Text(
                                        if (g.isPublic) Strings.ghPublic.lowercase() else Strings.ghPrivate.lowercase(),
                                        fontSize = 10.sp,
                                        fontFamily = JetBrainsMono,
                                        color = palette.textMuted,
                                    )
                                    Text(
                                        g.updatedAt.take(10),
                                        fontSize = 10.sp,
                                        fontFamily = JetBrainsMono,
                                        color = palette.textMuted,
                                    )
                                }
                            }
                        }
                        AiModuleHairline(modifier = Modifier.padding(start = 42.dp))
                    }
                }
            }
        }
    }

    if (showCreate) {
        CreateGistDialog({ showCreate = false }) {
            showCreate = false
            scope.launch { gists = GitHubManager.getGists(context) }
        }
    }
}

// ---------------------------------------------------------------------------
// Shared dialogs (used across multiple GitHub screens).
// Terminal-style dialogs shared by the repository screens.
// ---------------------------------------------------------------------------

@Composable
private fun MonoLabel(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(text, fontFamily = JetBrainsMono, color = color, fontSize = 13.sp)
}

@Composable
internal fun CreateRepoDialog(onDismiss: () -> Unit, onCreate: (String, String, Boolean) -> Unit) {
    var n by remember { mutableStateOf("") }
    var d by remember { mutableStateOf("") }
    var p by remember { mutableStateOf(false) }
    val palette = AiModuleTheme.colors
    AiModuleAlertDialog(
        onDismissRequest = onDismiss,
        title = Strings.ghNewRepo.lowercase(),
        confirmButton = {
            AiModuleTextAction(
                label = Strings.create,
                onClick = { if (n.isNotBlank()) onCreate(n, d, p) },
                tint = palette.accent,
            )
        },
        dismissButton = {
            AiModuleTextAction(label = Strings.cancel, onClick = onDismiss, tint = palette.textSecondary)
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AiModuleTextField(n, { n = it }, label = Strings.ghRepoName)
            AiModuleTextField(d, { d = it }, label = Strings.ghRepoDesc, maxLines = 3)
            AiModuleCheckRow(label = Strings.ghPrivate, checked = p, onToggle = { p = !p })
        }
    }
}

@Composable
internal fun UploadDialog(repo: GHRepo, curPath: String, branch: String, onDismiss: () -> Unit, onDone: () -> Unit) {
    var fn by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("Add file") }
    var up by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val s = rememberCoroutineScope()
    val palette = AiModuleTheme.colors
    AiModuleAlertDialog(
        onDismissRequest = onDismiss,
        title = Strings.ghUpload.lowercase(),
        confirmButton = {
            AiModuleTextAction(
                label = Strings.ghUpload,
                enabled = !up,
                onClick = {
                    if (fn.isBlank() || up) return@AiModuleTextAction
                    up = true
                    val p = if (curPath.isNotBlank()) "$curPath/$fn" else fn
                    s.launch {
                        val ok = GitHubManager.uploadFile(ctx, repo.owner, repo.name, p, "".toByteArray(), msg, branch)
                        Toast.makeText(ctx, if (ok) Strings.ghUploaded else Strings.error, Toast.LENGTH_SHORT).show()
                        if (ok) onDone() else up = false
                    }
                },
                tint = palette.accent,
            )
        },
        dismissButton = {
            AiModuleTextAction(label = Strings.cancel, onClick = onDismiss, tint = palette.textSecondary)
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${Strings.ghPickBranch}: $branch", fontSize = 11.sp, color = palette.textSecondary, fontFamily = JetBrainsMono)
            AiModuleTextField(fn, { fn = it }, label = Strings.ghFilePath, placeholder = "example.txt")
            if (curPath.isNotBlank()) Text("\u2192 $curPath/$fn", fontSize = 10.sp, color = palette.textMuted, fontFamily = JetBrainsMono)
            AiModuleTextField(msg, { msg = it }, label = Strings.ghCommitMsg)
            if (up) AiModuleSpinner(label = "uploading")
        }
    }
}

@Composable
internal fun CreateFileDialog(repo: GHRepo, curPath: String, branch: String, onDismiss: () -> Unit, onDone: () -> Unit) {
    var fn by remember { mutableStateOf("") }
    var ct by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("Create file") }
    var cr by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val s = rememberCoroutineScope()
    val palette = AiModuleTheme.colors
    AiModuleAlertDialog(
        onDismissRequest = onDismiss,
        title = Strings.ghCreateFile.lowercase(),
        confirmButton = {
            AiModuleTextAction(
                label = Strings.create,
                enabled = !cr,
                onClick = {
                    if (fn.isBlank() || cr) return@AiModuleTextAction
                    cr = true
                    val p = if (curPath.isNotBlank()) "$curPath/$fn" else fn
                    s.launch {
                        val ok = GitHubManager.uploadFile(ctx, repo.owner, repo.name, p, ct.toByteArray(), msg, branch)
                        Toast.makeText(ctx, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                        if (ok) onDone() else cr = false
                    }
                },
                tint = palette.accent,
            )
        },
        dismissButton = {
            AiModuleTextAction(label = Strings.cancel, onClick = onDismiss, tint = palette.textSecondary)
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AiModuleTextField(fn, { fn = it }, label = Strings.ghFilePath)
            AiModuleTextField(ct, { ct = it }, label = Strings.ghFileContent, minLines = 4, maxLines = 8)
            AiModuleTextField(msg, { msg = it }, label = Strings.ghCommitMsg)
        }
    }
}

@Composable
internal fun DeleteFileDialog(repo: GHRepo, file: GHContent, branch: String, onDismiss: () -> Unit, onDone: () -> Unit) {
    var msg by remember { mutableStateOf("Delete ${file.name}") }
    val ctx = LocalContext.current
    val s = rememberCoroutineScope()
    val palette = AiModuleTheme.colors
    AiModuleAlertDialog(
        onDismissRequest = onDismiss,
        title = Strings.ghDeleteFile.lowercase(),
        confirmButton = {
            AiModuleTextAction(
                label = Strings.ghDeleteFile,
                onClick = {
                    s.launch {
                        val ok = GitHubManager.deleteFile(ctx, repo.owner, repo.name, file.path, msg, file.sha, branch)
                        Toast.makeText(ctx, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                        if (ok) onDone()
                    }
                },
                tint = palette.error,
            )
        },
        dismissButton = {
            AiModuleTextAction(label = Strings.cancel, onClick = onDismiss, tint = palette.textSecondary)
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${file.name}?", fontSize = 13.sp, color = palette.textPrimary, fontFamily = JetBrainsMono)
            AiModuleTextField(msg, { msg = it }, label = Strings.ghCommitMsg)
        }
    }
}

@Composable
private fun CreateGistDialog(onDismiss: () -> Unit, onCreated: () -> Unit) {
    var desc by remember { mutableStateOf("") }
    var fname by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var isPublic by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val s = rememberCoroutineScope()
    val palette = AiModuleTheme.colors
    AiModuleAlertDialog(
        onDismissRequest = onDismiss,
        title = Strings.ghNewGist.lowercase(),
        confirmButton = {
            AiModuleTextAction(
                label = Strings.create,
                enabled = !creating,
                onClick = {
                    if (fname.isBlank() || content.isBlank() || creating) return@AiModuleTextAction
                    creating = true
                    s.launch {
                        val ok = GitHubManager.createGist(ctx, desc, isPublic, mapOf(fname to content))
                        Toast.makeText(ctx, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                        if (ok) onCreated() else creating = false
                    }
                },
                tint = palette.accent,
            )
        },
        dismissButton = {
            AiModuleTextAction(label = Strings.cancel, onClick = onDismiss, tint = palette.textSecondary)
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AiModuleTextField(desc, { desc = it }, label = Strings.ghRepoDesc)
            AiModuleTextField(fname, { fname = it }, label = Strings.ghFilePath)
            AiModuleTextField(content, { content = it }, label = Strings.ghFileContent, minLines = 4, maxLines = 10)
            AiModuleCheckRow(
                label = if (isPublic) Strings.ghPublic else Strings.ghPrivate,
                checked = isPublic,
                onToggle = { isPublic = !isPublic },
            )
            if (creating) AiModuleSpinner(label = "creating")
        }
    }
}

@Composable
internal fun CreateBranchDialog(repo: GHRepo, branches: List<String>, onDismiss: () -> Unit, onDone: () -> Unit) {
    var nm by remember { mutableStateOf("") }
    var fr by remember { mutableStateOf(repo.defaultBranch) }
    val ctx = LocalContext.current
    val s = rememberCoroutineScope()
    val palette = AiModuleTheme.colors
    AiModuleAlertDialog(
        onDismissRequest = onDismiss,
        title = Strings.ghNewBranch.lowercase(),
        confirmButton = {
            AiModuleTextAction(
                label = Strings.create,
                onClick = {
                    if (nm.isBlank()) return@AiModuleTextAction
                    s.launch {
                        val ok = GitHubManager.createBranch(ctx, repo.owner, repo.name, nm, fr)
                        Toast.makeText(ctx, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                        if (ok) onDone()
                    }
                },
                tint = palette.accent,
            )
        },
        dismissButton = {
            AiModuleTextAction(label = Strings.cancel, onClick = onDismiss, tint = palette.textSecondary)
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AiModuleTextField(nm, { nm = it }, label = Strings.ghBranchName)
            Text(Strings.ghFromBranch, fontSize = 11.sp, color = palette.textSecondary, fontFamily = JetBrainsMono)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                branches.forEach { b -> BC(b, b == fr) { fr = b } }
            }
        }
    }
}

@Composable
internal fun CreateIssueDialog(repo: GHRepo, onDismiss: () -> Unit, onDone: () -> Unit) {
    var t by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    val ctx = LocalContext.current
    val s = rememberCoroutineScope()
    val palette = AiModuleTheme.colors
    AiModuleAlertDialog(
        onDismissRequest = onDismiss,
        title = Strings.ghNewIssue.lowercase(),
        confirmButton = {
            AiModuleTextAction(
                label = Strings.create,
                onClick = {
                    if (t.isBlank()) return@AiModuleTextAction
                    s.launch {
                        val ok = GitHubManager.createIssue(ctx, repo.owner, repo.name, t, b)
                        Toast.makeText(ctx, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                        if (ok) onDone()
                    }
                },
                tint = palette.accent,
            )
        },
        dismissButton = {
            AiModuleTextAction(label = Strings.cancel, onClick = onDismiss, tint = palette.textSecondary)
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AiModuleTextField(t, { t = it }, label = "Title")
            AiModuleTextField(b, { b = it }, label = Strings.ghRepoDesc, minLines = 4, maxLines = 6)
        }
    }
}

@Composable
internal fun CreatePRDialog(repo: GHRepo, branches: List<String>, onDismiss: () -> Unit, onDone: () -> Unit) {
    var t by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    var head by remember { mutableStateOf(branches.firstOrNull { it != repo.defaultBranch } ?: "") }
    var base by remember { mutableStateOf(repo.defaultBranch) }
    val ctx = LocalContext.current
    val s = rememberCoroutineScope()
    val palette = AiModuleTheme.colors
    AiModuleAlertDialog(
        onDismissRequest = onDismiss,
        title = Strings.ghNewPR.lowercase(),
        confirmButton = {
            AiModuleTextAction(
                label = Strings.create,
                onClick = {
                    if (t.isBlank() || head == base) return@AiModuleTextAction
                    s.launch {
                        val ok = GitHubManager.createPullRequest(ctx, repo.owner, repo.name, t, b, head, base)
                        Toast.makeText(ctx, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                        if (ok) onDone()
                    }
                },
                tint = palette.accent,
            )
        },
        dismissButton = {
            AiModuleTextAction(label = Strings.cancel, onClick = onDismiss, tint = palette.textSecondary)
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AiModuleTextField(t, { t = it }, label = "Title")
            AiModuleTextField(b, { b = it }, label = Strings.ghRepoDesc, minLines = 3, maxLines = 4)
            Text(Strings.ghHead, fontSize = 11.sp, color = palette.textSecondary, fontFamily = JetBrainsMono)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                branches.forEach { br -> BC(br, br == head) { head = br } }
            }
            Text(Strings.ghBase, fontSize = 11.sp, color = palette.textSecondary, fontFamily = JetBrainsMono)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                branches.forEach { br -> BC(br, br == base) { base = br } }
            }
        }
    }
}

@Composable
private fun BC(name: String, sel: Boolean, onClick: () -> Unit) {
    val palette = AiModuleTheme.colors
    Box(
        Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (sel) palette.accent.copy(alpha = 0.15f) else palette.surface)
            .border(1.dp, if (sel) palette.accent else palette.border, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            name,
            fontSize = 11.sp,
            fontFamily = JetBrainsMono,
            color = if (sel) palette.accent else palette.textSecondary,
        )
    }
}

@Composable
internal fun BranchPickerDialog(
    branches: List<String>,
    current: String,
    canWrite: Boolean = true,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    onCreateBranch: () -> Unit,
) {
    val palette = AiModuleTheme.colors
    val branchGlyph = "\u2442"
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = true),
    ) {
        AiModuleCard(elevated = true) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "> branches",
                    color = palette.textPrimary,
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(4.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(palette.border))
                Spacer(Modifier.height(2.dp))
                Column(
                    Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    branches.forEach { b ->
                        val isCurrent = b == current
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isCurrent) palette.accent.copy(alpha = 0.10f) else Color.Transparent)
                                .clickable { onSelect(b) }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                branchGlyph,
                                color = if (isCurrent) palette.accent else palette.textSecondary,
                                fontFamily = JetBrainsMono,
                                fontSize = 13.sp,
                            )
                            Text(
                                b,
                                color = if (isCurrent) palette.accent else palette.textPrimary,
                                fontFamily = JetBrainsMono,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (isCurrent) {
                                Text(
                                    "[\u2713]",
                                    color = palette.accent,
                                    fontFamily = JetBrainsMono,
                                    fontSize = 12.sp,
                                )
                            }
                        }
                    }
                }
                if (canWrite) {
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(palette.border))
                    Spacer(Modifier.height(8.dp))
                    AiModulePillButton(
                        label = "+ new branch",
                        onClick = onCreateBranch,
                    )
                }
            }
        }
    }
}

@Composable
internal fun DispatchWorkflowDialog(
    repo: GHRepo,
    workflows: List<GHWorkflow>,
    branches: List<String>,
    onDismiss: () -> Unit,
    onDone: () -> Unit,
) {
    var selectedWf by remember { mutableStateOf(workflows.firstOrNull()) }
    var selectedBranch by remember { mutableStateOf(repo.defaultBranch) }
    var schema by remember { mutableStateOf<GHWorkflowDispatchSchema?>(null) }
    var inputValues by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var dispatching by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val s = rememberCoroutineScope()
    val palette = AiModuleTheme.colors

    LaunchedEffect(selectedWf?.path, selectedBranch) {
        schema = selectedWf?.let { GitHubManager.getWorkflowDispatchSchema(ctx, repo.owner, repo.name, it.path, selectedBranch) }
        inputValues = schema?.inputs.orEmpty().associate { it.key to it.defaultValue }
    }

    AiModuleAlertDialog(
        onDismissRequest = onDismiss,
        title = "\u25B8  ${Strings.ghRunWorkflow.lowercase()}",
        confirmButton = {
            AiModuleTextAction(
                label = Strings.ghRunWorkflow,
                enabled = !dispatching && schema != null,
                onClick = {
                    if (selectedWf == null || dispatching) return@AiModuleTextAction
                    dispatching = true
                    s.launch {
                        val ok = GitHubManager.dispatchWorkflow(ctx, repo.owner, repo.name, selectedWf!!.id, selectedBranch, inputValues.filterValues { it.isNotBlank() })
                        Toast.makeText(ctx, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                        dispatching = false
                        if (ok) onDone()
                    }
                },
                tint = if (dispatching || schema == null) palette.textMuted else palette.accent,
            )
        },
        dismissButton = {
            AiModuleTextAction(label = Strings.cancel, onClick = onDismiss, tint = palette.textSecondary)
        },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(Strings.ghWorkflows, fontSize = 11.sp, color = palette.textSecondary, fontFamily = JetBrainsMono)
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    workflows.forEach { wf ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
                                .background(if (selectedWf == wf) palette.accent.copy(alpha = 0.10f) else Color.Transparent)
                                .clickable { selectedWf = wf }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            AiModuleGlyph(
                                glyph = GhGlyphs.SETTINGS,
                                tint = if (selectedWf == wf) palette.accent else palette.textSecondary,
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    wf.name,
                                    fontSize = 12.sp,
                                    fontFamily = JetBrainsMono,
                                    color = if (selectedWf == wf) palette.accent else palette.textPrimary,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    wf.path,
                                    fontSize = 10.sp,
                                    fontFamily = JetBrainsMono,
                                    color = palette.textMuted,
                                )
                            }
                            if (selectedWf == wf) {
                                AiModuleGlyph(glyph = GhGlyphs.CHECK, tint = palette.accent)
                            }
                        }
                    }
                }
                Text(Strings.ghPickBranch, fontSize = 11.sp, color = palette.textSecondary, fontFamily = JetBrainsMono)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    branches.forEach { b -> BC(b, b == selectedBranch) { selectedBranch = b } }
                }
                val inputs = schema?.inputs.orEmpty()
                if (schema != null && inputs.isNotEmpty()) {
                    Text("Inputs", fontSize = 11.sp, color = palette.textSecondary, fontFamily = JetBrainsMono)
                    inputs.forEach { input ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    input.key,
                                    fontSize = 12.sp,
                                    color = palette.textPrimary,
                                    fontFamily = JetBrainsMono,
                                    fontWeight = FontWeight.Medium,
                                )
                                if (input.required) {
                                    Text(
                                        "required",
                                        fontSize = 10.sp,
                                        color = palette.warning,
                                        fontFamily = JetBrainsMono,
                                    )
                                }
                            }
                            if (input.description.isNotBlank()) {
                                Text(
                                    input.description,
                                    fontSize = 10.sp,
                                    color = palette.textMuted,
                                    fontFamily = JetBrainsMono,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            val choices = dialogDispatchInputChoices(input)
                            if (choices.isNotEmpty()) {
                                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    choices.forEach { option -> BC(option, inputValues[input.key] == option) { inputValues = inputValues + (input.key to option) } }
                                }
                            } else {
                                AiModuleTextField(
                                    value = inputValues[input.key].orEmpty(),
                                    onValueChange = { inputValues = inputValues + (input.key to it) },
                                    label = input.key,
                                    maxLines = if (input.type.lowercase() == "environment") 4 else 1,
                                )
                            }
                        }
                    }
                } else if (schema != null) {
                    Text("This workflow has no workflow_dispatch inputs", fontSize = 11.sp, color = palette.textMuted, fontFamily = JetBrainsMono)
                } else {
                    Text("This workflow has no workflow_dispatch trigger", fontSize = 11.sp, color = palette.textMuted, fontFamily = JetBrainsMono)
                }
                if (dispatching) {
                    AiModuleSpinner(label = Strings.ghRunning)
                }
        }
    }
}

private fun dialogDispatchInputChoices(input: GHWorkflowDispatchInput): List<String> = when {
    input.options.isNotEmpty() -> input.options
    input.type.equals("boolean", ignoreCase = true) -> listOf("true", "false")
    else -> emptyList()
}
