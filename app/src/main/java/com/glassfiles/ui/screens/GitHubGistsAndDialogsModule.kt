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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.glassfiles.ui.components.AiModuleCard
import com.glassfiles.ui.components.AiModulePillButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
                AiModulePageBar(
                    title = "> ${current.description.ifBlank { "gist" }.lowercase()}",
                    subtitle = "${current.files.size} file${if (current.files.size == 1) "" else "s"}",
                    onBack = { viewingGist = null; gistContent = emptyMap() },
                    trailing = {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    GitHubManager.deleteGist(context, current.id)
                                    gists = GitHubManager.getGists(context)
                                    viewingGist = null
                                    gistContent = emptyMap()
                                }
                            },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(Icons.Rounded.Delete, null, Modifier.size(18.dp), tint = palette.error)
                        }
                        if (onClose != null) {
                            IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Rounded.Close, null, Modifier.size(18.dp), tint = palette.error)
                            }
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
            AiModulePageBar(
                title = "> gists",
                subtitle = if (loading) "loading…" else "${gists.size} gist${if (gists.size == 1) "" else "s"}",
                onBack = onBack,
                trailing = {
                    IconButton(onClick = { showCreate = true }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Rounded.Add, null, Modifier.size(18.dp), tint = palette.accent)
                    }
                    IconButton(onClick = onMinimize, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Rounded.PictureInPictureAlt, null, Modifier.size(18.dp), tint = palette.textSecondary)
                    }
                    if (onClose != null) {
                        IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Rounded.Close, null, Modifier.size(18.dp), tint = palette.error)
                        }
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
// Kept on Material3 AlertDialog chrome but themed via AiModuleTheme.colors so
// they read as part of the GitHub module without rewriting the M3 form fields.
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
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        title = { MonoLabel("> ${Strings.ghNewRepo.lowercase()}", palette.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(n, { n = it }, label = { Text(Strings.ghRepoName) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(d, { d = it }, label = { Text(Strings.ghRepoDesc) }, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Switch(
                        checked = p,
                        onCheckedChange = { p = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = palette.accent),
                    )
                    Text(Strings.ghPrivate, fontSize = 13.sp, color = palette.textPrimary, fontFamily = JetBrainsMono)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (n.isNotBlank()) onCreate(n, d, p) }) {
                MonoLabel(Strings.create, palette.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { MonoLabel(Strings.cancel, palette.textSecondary) }
        },
    )
}

@Composable
internal fun UploadDialog(repo: GHRepo, curPath: String, branch: String, onDismiss: () -> Unit, onDone: () -> Unit) {
    var fn by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("Add file") }
    var up by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val s = rememberCoroutineScope()
    val palette = AiModuleTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        title = { MonoLabel("> ${Strings.ghUpload.lowercase()}", palette.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${Strings.ghPickBranch}: $branch", fontSize = 11.sp, color = palette.textSecondary, fontFamily = JetBrainsMono)
                OutlinedTextField(fn, { fn = it }, label = { Text(Strings.ghFilePath) }, singleLine = true, modifier = Modifier.fillMaxWidth(), placeholder = { Text("example.txt") })
                if (curPath.isNotBlank()) Text("\u2192 $curPath/$fn", fontSize = 10.sp, color = palette.textMuted, fontFamily = JetBrainsMono)
                OutlinedTextField(msg, { msg = it }, label = { Text(Strings.ghCommitMsg) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (up) LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = palette.accent)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (fn.isBlank() || up) return@TextButton
                    up = true
                    val p = if (curPath.isNotBlank()) "$curPath/$fn" else fn
                    s.launch {
                        val ok = GitHubManager.uploadFile(ctx, repo.owner, repo.name, p, "".toByteArray(), msg, branch)
                        Toast.makeText(ctx, if (ok) Strings.ghUploaded else Strings.error, Toast.LENGTH_SHORT).show()
                        if (ok) onDone() else up = false
                    }
                },
                enabled = !up,
            ) { MonoLabel(Strings.ghUpload, palette.accent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { MonoLabel(Strings.cancel, palette.textSecondary) }
        },
    )
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
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        title = { MonoLabel("> ${Strings.ghCreateFile.lowercase()}", palette.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(fn, { fn = it }, label = { Text(Strings.ghFilePath) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(ct, { ct = it }, label = { Text(Strings.ghFileContent) }, modifier = Modifier.fillMaxWidth().height(120.dp), maxLines = 8)
                OutlinedTextField(msg, { msg = it }, label = { Text(Strings.ghCommitMsg) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (fn.isBlank() || cr) return@TextButton
                    cr = true
                    val p = if (curPath.isNotBlank()) "$curPath/$fn" else fn
                    s.launch {
                        val ok = GitHubManager.uploadFile(ctx, repo.owner, repo.name, p, ct.toByteArray(), msg, branch)
                        Toast.makeText(ctx, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                        if (ok) onDone() else cr = false
                    }
                },
                enabled = !cr,
            ) { MonoLabel(Strings.create, palette.accent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { MonoLabel(Strings.cancel, palette.textSecondary) }
        },
    )
}

@Composable
internal fun DeleteFileDialog(repo: GHRepo, file: GHContent, branch: String, onDismiss: () -> Unit, onDone: () -> Unit) {
    var msg by remember { mutableStateOf("Delete ${file.name}") }
    val ctx = LocalContext.current
    val s = rememberCoroutineScope()
    val palette = AiModuleTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        title = { MonoLabel("> ${Strings.ghDeleteFile.lowercase()}", palette.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${file.name}?", fontSize = 13.sp, color = palette.textPrimary, fontFamily = JetBrainsMono)
                OutlinedTextField(msg, { msg = it }, label = { Text(Strings.ghCommitMsg) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                s.launch {
                    val ok = GitHubManager.deleteFile(ctx, repo.owner, repo.name, file.path, msg, file.sha, branch)
                    Toast.makeText(ctx, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                    if (ok) onDone()
                }
            }) { MonoLabel(Strings.ghDeleteFile, palette.error) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { MonoLabel(Strings.cancel, palette.textSecondary) }
        },
    )
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
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        title = { MonoLabel("> ${Strings.ghNewGist.lowercase()}", palette.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(desc, { desc = it }, label = { Text(Strings.ghRepoDesc) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(fname, { fname = it }, label = { Text(Strings.ghFilePath) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(content, { content = it }, label = { Text(Strings.ghFileContent) }, modifier = Modifier.fillMaxWidth().height(140.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Switch(
                        checked = isPublic,
                        onCheckedChange = { isPublic = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = palette.accent),
                    )
                    Text(if (isPublic) Strings.ghPublic else Strings.ghPrivate, color = palette.textPrimary, fontFamily = JetBrainsMono, fontSize = 12.sp)
                }
                if (creating) LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = palette.accent)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (fname.isBlank() || content.isBlank() || creating) return@TextButton
                    creating = true
                    s.launch {
                        val ok = GitHubManager.createGist(ctx, desc, isPublic, mapOf(fname to content))
                        Toast.makeText(ctx, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                        if (ok) onCreated() else creating = false
                    }
                },
                enabled = !creating,
            ) { MonoLabel(Strings.create, palette.accent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { MonoLabel(Strings.cancel, palette.textSecondary) }
        },
    )
}

@Composable
internal fun CreateBranchDialog(repo: GHRepo, branches: List<String>, onDismiss: () -> Unit, onDone: () -> Unit) {
    var nm by remember { mutableStateOf("") }
    var fr by remember { mutableStateOf(repo.defaultBranch) }
    val ctx = LocalContext.current
    val s = rememberCoroutineScope()
    val palette = AiModuleTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        title = { MonoLabel("> ${Strings.ghNewBranch.lowercase()}", palette.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(nm, { nm = it }, label = { Text(Strings.ghBranchName) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text(Strings.ghFromBranch, fontSize = 11.sp, color = palette.textSecondary, fontFamily = JetBrainsMono)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    branches.forEach { b -> BC(b, b == fr) { fr = b } }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (nm.isBlank()) return@TextButton
                s.launch {
                    val ok = GitHubManager.createBranch(ctx, repo.owner, repo.name, nm, fr)
                    Toast.makeText(ctx, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                    if (ok) onDone()
                }
            }) { MonoLabel(Strings.create, palette.accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { MonoLabel(Strings.cancel, palette.textSecondary) } },
    )
}

@Composable
internal fun CreateIssueDialog(repo: GHRepo, onDismiss: () -> Unit, onDone: () -> Unit) {
    var t by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    val ctx = LocalContext.current
    val s = rememberCoroutineScope()
    val palette = AiModuleTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        title = { MonoLabel("> ${Strings.ghNewIssue.lowercase()}", palette.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(t, { t = it }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(b, { b = it }, label = { Text(Strings.ghRepoDesc) }, modifier = Modifier.fillMaxWidth().height(100.dp), maxLines = 6)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (t.isBlank()) return@TextButton
                s.launch {
                    val ok = GitHubManager.createIssue(ctx, repo.owner, repo.name, t, b)
                    Toast.makeText(ctx, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                    if (ok) onDone()
                }
            }) { MonoLabel(Strings.create, palette.accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { MonoLabel(Strings.cancel, palette.textSecondary) } },
    )
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
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        title = { MonoLabel("> ${Strings.ghNewPR.lowercase()}", palette.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(t, { t = it }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(b, { b = it }, label = { Text(Strings.ghRepoDesc) }, modifier = Modifier.fillMaxWidth().height(80.dp), maxLines = 4)
                Text(Strings.ghHead, fontSize = 11.sp, color = palette.textSecondary, fontFamily = JetBrainsMono)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    branches.forEach { br -> BC(br, br == head) { head = br } }
                }
                Text(Strings.ghBase, fontSize = 11.sp, color = palette.textSecondary, fontFamily = JetBrainsMono)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    branches.forEach { br -> BC(br, br == base) { base = br } }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (t.isBlank() || head == base) return@TextButton
                s.launch {
                    val ok = GitHubManager.createPullRequest(ctx, repo.owner, repo.name, t, b, head, base)
                    Toast.makeText(ctx, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                    if (ok) onDone()
                }
            }) { MonoLabel(Strings.create, palette.accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { MonoLabel(Strings.cancel, palette.textSecondary) } },
    )
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

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.PlayArrow, null, Modifier.size(18.dp), tint = palette.accent)
                MonoLabel("> ${Strings.ghRunWorkflow.lowercase()}", palette.textPrimary)
            }
        },
        text = {
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
                            Icon(
                                Icons.Rounded.Settings,
                                null,
                                Modifier.size(14.dp),
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
                                Icon(Icons.Rounded.Check, null, Modifier.size(12.dp), tint = palette.accent)
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
                                OutlinedTextField(
                                    value = inputValues[input.key].orEmpty(),
                                    onValueChange = { inputValues = inputValues + (input.key to it) },
                                    label = { Text(input.key) },
                                    textStyle = TextStyle(fontSize = 12.sp),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = input.type.lowercase() != "environment",
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
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(Modifier.size(14.dp), color = palette.accent, strokeWidth = 2.dp)
                        Text(Strings.ghRunning, fontSize = 11.sp, color = palette.accent, fontFamily = JetBrainsMono)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (selectedWf == null || dispatching) return@TextButton
                    dispatching = true
                    s.launch {
                        val ok = GitHubManager.dispatchWorkflow(ctx, repo.owner, repo.name, selectedWf!!.id, selectedBranch, inputValues.filterValues { it.isNotBlank() })
                        Toast.makeText(ctx, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                        dispatching = false
                        if (ok) onDone()
                    }
                },
                enabled = !dispatching && schema != null,
            ) {
                MonoLabel(
                    Strings.ghRunWorkflow,
                    if (dispatching || schema == null) palette.textMuted else palette.accent,
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { MonoLabel(Strings.cancel, palette.textSecondary) } },
    )
}

private fun dialogDispatchInputChoices(input: GHWorkflowDispatchInput): List<String> = when {
    input.options.isNotEmpty() -> input.options
    input.type.equals("boolean", ignoreCase = true) -> listOf("true", "false")
    else -> emptyList()
}
