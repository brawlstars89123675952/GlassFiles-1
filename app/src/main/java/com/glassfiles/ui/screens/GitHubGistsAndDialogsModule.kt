package com.glassfiles.ui.screens

import android.os.Environment
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.glassfiles.data.Strings
import com.glassfiles.data.github.*
import com.glassfiles.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File

// Compact mode — propagates through all sub-screens automatically

@Composable
internal fun GistsScreen(onBack: () -> Unit, onMinimize: () -> Unit, onClose: (() -> Unit)? = null) { val context = LocalContext.current; val scope = rememberCoroutineScope()
    var gists by remember { mutableStateOf<List<GHGist>>(emptyList()) }; var loading by remember { mutableStateOf(true) }; var showCreate by remember { mutableStateOf(false) }
    var viewingGist by remember { mutableStateOf<GHGist?>(null) }; var gistContent by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    LaunchedEffect(Unit) { gists = GitHubManager.getGists(context); loading = false }
    if (viewingGist != null) { Column(Modifier.fillMaxSize().background(SurfaceLight)) { GHTopBar(viewingGist!!.description.ifBlank { "Gist" }, onBack = { viewingGist = null; gistContent = emptyMap() }, onClose = onClose) { IconButton(onClick = { scope.launch { GitHubManager.deleteGist(context, viewingGist!!.id); gists = GitHubManager.getGists(context); viewingGist = null; gistContent = emptyMap() } }) { Icon(Icons.Rounded.Delete, null, Modifier.size(20.dp), tint = Color(0xFFFF3B30)) } }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) { gistContent.forEach { (n, t) -> item { Text(n, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Blue); Spacer(Modifier.height(4.dp)); Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFF1E1E22)).horizontalScroll(rememberScrollState()).padding(10.dp)) { Text(t, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = Color(0xFFD4D4D4), lineHeight = 18.sp) }; Spacer(Modifier.height(12.dp)) } } } }; return }
    Column(Modifier.fillMaxSize().background(SurfaceLight)) { GHTopBar("Gists", onBack = onBack, onMinimize = onMinimize, onClose = onClose) { IconButton(onClick = { showCreate = true }) { Icon(Icons.Rounded.Add, null, Modifier.size(22.dp), tint = Blue) } }
        if (loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Blue, modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp) }
        else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) { items(gists) { g -> Row(Modifier.fillMaxWidth().clickable { scope.launch { gistContent = GitHubManager.getGistContent(context, g.id); viewingGist = g } }.padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Rounded.Description, null, Modifier.size(20.dp), tint = Blue); Column(Modifier.weight(1f)) { Text(g.description.ifBlank { g.files.firstOrNull() ?: "Gist" }, fontSize = 14.sp, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Text("${g.files.size} files", fontSize = 11.sp, color = TextSecondary); Text(if (g.isPublic) Strings.ghPublic else Strings.ghPrivate, fontSize = 11.sp, color = TextTertiary); Text(g.updatedAt.take(10), fontSize = 11.sp, color = TextTertiary) } }
        }; Box(Modifier.fillMaxWidth().padding(start = 46.dp).height(0.5.dp).background(SeparatorColor)) } }
    }
    if (showCreate) CreateGistDialog({ showCreate = false }) { showCreate = false; scope.launch { gists = GitHubManager.getGists(context) } }
}

// Dialogs

@Composable private fun CreateRepoDialog(onDismiss: () -> Unit, onCreate: (String, String, Boolean) -> Unit) { var n by remember { mutableStateOf("") }; var d by remember { mutableStateOf("") }; var p by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, containerColor = SurfaceWhite, title = { Text(Strings.ghNewRepo, fontWeight = FontWeight.Bold, color = TextPrimary) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { OutlinedTextField(n, { n = it }, label = { Text(Strings.ghRepoName) }, singleLine = true, modifier = Modifier.fillMaxWidth()); OutlinedTextField(d, { d = it }, label = { Text(Strings.ghRepoDesc) }, modifier = Modifier.fillMaxWidth()); Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Switch(checked = p, onCheckedChange = { p = it }, colors = SwitchDefaults.colors(checkedTrackColor = Blue)); Text(Strings.ghPrivate, fontSize = 14.sp, color = TextPrimary) } } },
        confirmButton = { TextButton(onClick = { if (n.isNotBlank()) onCreate(n, d, p) }) { Text(Strings.create, color = Blue) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.cancel, color = TextSecondary) } }) }

@Composable private fun UploadDialog(repo: GHRepo, curPath: String, branch: String, onDismiss: () -> Unit, onDone: () -> Unit) { var fn by remember { mutableStateOf("") }; var msg by remember { mutableStateOf("Add file") }; var up by remember { mutableStateOf(false) }; val ctx = LocalContext.current; val s = rememberCoroutineScope()
    AlertDialog(onDismissRequest = onDismiss, containerColor = SurfaceWhite, title = { Text(Strings.ghUpload, fontWeight = FontWeight.Bold, color = TextPrimary) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("${Strings.ghPickBranch}: $branch", fontSize = 12.sp, color = TextSecondary); OutlinedTextField(fn, { fn = it }, label = { Text(Strings.ghFilePath) }, singleLine = true, modifier = Modifier.fillMaxWidth(), placeholder = { Text("example.txt") }); if (curPath.isNotBlank()) Text("\u2192 $curPath/$fn", fontSize = 11.sp, color = TextTertiary); OutlinedTextField(msg, { msg = it }, label = { Text(Strings.ghCommitMsg) }, singleLine = true, modifier = Modifier.fillMaxWidth()); if (up) LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Blue) } },
        confirmButton = { TextButton(onClick = { if (fn.isBlank()||up) return@TextButton; up = true; val p = if (curPath.isNotBlank()) "$curPath/$fn" else fn; s.launch { val ok = GitHubManager.uploadFile(ctx, repo.owner, repo.name, p, "".toByteArray(), msg, branch); Toast.makeText(ctx, if (ok) Strings.ghUploaded else Strings.error, Toast.LENGTH_SHORT).show(); if (ok) onDone() else up = false } }, enabled = !up) { Text(Strings.ghUpload, color = Blue) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.cancel, color = TextSecondary) } }) }

@Composable private fun CreateFileDialog(repo: GHRepo, curPath: String, branch: String, onDismiss: () -> Unit, onDone: () -> Unit) { var fn by remember { mutableStateOf("") }; var ct by remember { mutableStateOf("") }; var msg by remember { mutableStateOf("Create file") }; var cr by remember { mutableStateOf(false) }; val ctx = LocalContext.current; val s = rememberCoroutineScope()
    AlertDialog(onDismissRequest = onDismiss, containerColor = SurfaceWhite, title = { Text(Strings.ghCreateFile, fontWeight = FontWeight.Bold, color = TextPrimary) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(fn, { fn = it }, label = { Text(Strings.ghFilePath) }, singleLine = true, modifier = Modifier.fillMaxWidth()); OutlinedTextField(ct, { ct = it }, label = { Text(Strings.ghFileContent) }, modifier = Modifier.fillMaxWidth().height(120.dp), maxLines = 8); OutlinedTextField(msg, { msg = it }, label = { Text(Strings.ghCommitMsg) }, singleLine = true, modifier = Modifier.fillMaxWidth()) } },
        confirmButton = { TextButton(onClick = { if (fn.isBlank()||cr) return@TextButton; cr = true; val p = if (curPath.isNotBlank()) "$curPath/$fn" else fn; s.launch { val ok = GitHubManager.uploadFile(ctx, repo.owner, repo.name, p, ct.toByteArray(), msg, branch); Toast.makeText(ctx, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show(); if (ok) onDone() else cr = false } }, enabled = !cr) { Text(Strings.create, color = Blue) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.cancel, color = TextSecondary) } }) }

@Composable private fun DeleteFileDialog(repo: GHRepo, file: GHContent, branch: String, onDismiss: () -> Unit, onDone: () -> Unit) { var msg by remember { mutableStateOf("Delete ${file.name}") }; val ctx = LocalContext.current; val s = rememberCoroutineScope()
    AlertDialog(onDismissRequest = onDismiss, containerColor = SurfaceWhite, title = { Text(Strings.ghConfirmDelete, fontWeight = FontWeight.Bold, color = TextPrimary) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(file.path, fontSize = 14.sp, color = TextSecondary); OutlinedTextField(msg, { msg = it }, label = { Text(Strings.ghCommitMsg) }, singleLine = true, modifier = Modifier.fillMaxWidth()) } },
        confirmButton = { TextButton(onClick = { s.launch { val ok = GitHubManager.deleteFile(ctx, repo.owner, repo.name, file.path, file.sha, msg, branch); Toast.makeText(ctx, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show(); if (ok) onDone() } }) { Text(Strings.delete, color = Color(0xFFFF3B30)) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.cancel, color = TextSecondary) } }) }

@Composable private fun CreateBranchDialog(repo: GHRepo, branches: List<String>, onDismiss: () -> Unit, onDone: () -> Unit) { var nm by remember { mutableStateOf("") }; var fr by remember { mutableStateOf(repo.defaultBranch) }; val ctx = LocalContext.current; val s = rememberCoroutineScope()
    AlertDialog(onDismissRequest = onDismiss, containerColor = SurfaceWhite, title = { Text(Strings.ghNewBranch, fontWeight = FontWeight.Bold, color = TextPrimary) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(nm, { nm = it }, label = { Text(Strings.ghBranchName) }, singleLine = true, modifier = Modifier.fillMaxWidth()); Text(Strings.ghFromBranch, fontSize = 12.sp, color = TextSecondary)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { branches.forEach { b -> Box(Modifier.clip(RoundedCornerShape(6.dp)).background(if (b == fr) Blue.copy(0.15f) else SurfaceLight).clickable { fr = b }.padding(horizontal = 8.dp, vertical = 4.dp)) { Text(b, fontSize = 12.sp, color = if (b == fr) Blue else TextSecondary) } } } } },
        confirmButton = { TextButton(onClick = { if (nm.isBlank()) return@TextButton; s.launch { val ok = GitHubManager.createBranch(ctx, repo.owner, repo.name, nm, fr); Toast.makeText(ctx, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show(); if (ok) onDone() } }) { Text(Strings.create, color = Blue) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.cancel, color = TextSecondary) } }) }

@Composable private fun CreateIssueDialog(repo: GHRepo, onDismiss: () -> Unit, onDone: () -> Unit) { var t by remember { mutableStateOf("") }; var b by remember { mutableStateOf("") }; val ctx = LocalContext.current; val s = rememberCoroutineScope()
    AlertDialog(onDismissRequest = onDismiss, containerColor = SurfaceWhite, title = { Text(Strings.ghNewIssue, fontWeight = FontWeight.Bold, color = TextPrimary) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(t, { t = it }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth()); OutlinedTextField(b, { b = it }, label = { Text(Strings.ghRepoDesc) }, modifier = Modifier.fillMaxWidth().height(100.dp), maxLines = 6) } },
        confirmButton = { TextButton(onClick = { if (t.isBlank()) return@TextButton; s.launch { val ok = GitHubManager.createIssue(ctx, repo.owner, repo.name, t, b); Toast.makeText(ctx, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show(); if (ok) onDone() } }) { Text(Strings.create, color = Blue) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.cancel, color = TextSecondary) } }) }

@Composable private fun CreatePRDialog(repo: GHRepo, branches: List<String>, onDismiss: () -> Unit, onDone: () -> Unit) { var t by remember { mutableStateOf("") }; var b by remember { mutableStateOf("") }
    var head by remember { mutableStateOf(branches.firstOrNull { it != repo.defaultBranch } ?: "") }; var base by remember { mutableStateOf(repo.defaultBranch) }; val ctx = LocalContext.current; val s = rememberCoroutineScope()
    AlertDialog(onDismissRequest = onDismiss, containerColor = SurfaceWhite, title = { Text(Strings.ghNewPR, fontWeight = FontWeight.Bold, color = TextPrimary) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(t, { t = it }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth()); OutlinedTextField(b, { b = it }, label = { Text(Strings.ghRepoDesc) }, modifier = Modifier.fillMaxWidth().height(80.dp), maxLines = 4)
            Text(Strings.ghHead, fontSize = 12.sp, color = TextSecondary); Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { branches.forEach { br -> BC(br, br == head) { head = br } } }
            Text(Strings.ghBase, fontSize = 12.sp, color = TextSecondary); Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { branches.forEach { br -> BC(br, br == base) { base = br } } } } },
        confirmButton = { TextButton(onClick = { if (t.isBlank()||head==base) return@TextButton; s.launch { val ok = GitHubManager.createPullRequest(ctx, repo.owner, repo.name, t, b, head, base); Toast.makeText(ctx, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show(); if (ok) onDone() } }) { Text(Strings.create, color = Blue) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.cancel, color = TextSecondary) } }) }

@Composable private fun BC(name: String, sel: Boolean, onClick: () -> Unit) { Box(Modifier.clip(RoundedCornerShape(6.dp)).background(if (sel) Blue.copy(0.15f) else SurfaceLight).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 4.dp)) { Text(name, fontSize = 12.sp, color = if (sel) Blue else TextSecondary) } }

@Composable private fun BranchPickerDialog(branches: List<String>, current: String, onSelect: (String) -> Unit, onDismiss: () -> Unit, onCreateBranch: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, containerColor = SurfaceWhite, title = { Text(Strings.ghBranches, fontWeight = FontWeight.Bold, color = TextPrimary) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { branches.forEach { b -> Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(if (b == current) Blue.copy(0.1f) else Color.Transparent).clickable { onSelect(b) }.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Icon(Icons.Rounded.AccountTree, null, Modifier.size(16.dp), tint = if (b == current) Blue else TextSecondary); Text(b, fontSize = 14.sp, color = if (b == current) Blue else TextPrimary, modifier = Modifier.weight(1f)); if (b == current) Icon(Icons.Rounded.Check, null, Modifier.size(16.dp), tint = Blue) } }
            Spacer(Modifier.height(4.dp)); Box(Modifier.fillMaxWidth().height(0.5.dp).background(SeparatorColor)); Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onCreateBranch() }.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Icon(Icons.Rounded.Add, null, Modifier.size(16.dp), tint = Blue); Text(Strings.ghNewBranch, fontSize = 14.sp, color = Blue) } } }, confirmButton = {}) }

@Composable private fun CreateGistDialog(onDismiss: () -> Unit, onDone: () -> Unit) { var d by remember { mutableStateOf("") }; var fn by remember { mutableStateOf("file.txt") }; var ct by remember { mutableStateOf("") }; var pub by remember { mutableStateOf(true) }; val ctx = LocalContext.current; val s = rememberCoroutineScope()
    AlertDialog(onDismissRequest = onDismiss, containerColor = SurfaceWhite, title = { Text(Strings.ghNewGist, fontWeight = FontWeight.Bold, color = TextPrimary) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(d, { d = it }, label = { Text(Strings.ghRepoDesc) }, singleLine = true, modifier = Modifier.fillMaxWidth()); OutlinedTextField(fn, { fn = it }, label = { Text(Strings.ghFilePath) }, singleLine = true, modifier = Modifier.fillMaxWidth()); OutlinedTextField(ct, { ct = it }, label = { Text(Strings.ghFileContent) }, modifier = Modifier.fillMaxWidth().height(120.dp), maxLines = 8); Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Switch(checked = pub, onCheckedChange = { pub = it }, colors = SwitchDefaults.colors(checkedTrackColor = Blue)); Text(Strings.ghPublic, fontSize = 14.sp, color = TextPrimary) } } },
        confirmButton = { TextButton(onClick = { if (fn.isBlank()) return@TextButton; s.launch { val ok = GitHubManager.createGist(ctx, d, pub, mapOf(fn to ct)); Toast.makeText(ctx, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show(); if (ok) onDone() } }) { Text(Strings.create, color = Blue) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.cancel, color = TextSecondary) } }) }

@Composable
internal fun DispatchWorkflowDialog(repo: GHRepo, workflows: List<GHWorkflow>, branches: List<String>, onDismiss: () -> Unit, onDone: () -> Unit) {
    var selectedWf by remember { mutableStateOf(workflows.firstOrNull()) }
    var selectedBranch by remember { mutableStateOf(repo.defaultBranch) }
    var dispatching by remember { mutableStateOf(false) }
    val ctx = LocalContext.current; val s = rememberCoroutineScope()

    AlertDialog(onDismissRequest = onDismiss, containerColor = SurfaceWhite,
        title = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.PlayArrow, null, Modifier.size(22.dp), tint = Color(0xFF34C759))
            Text(Strings.ghRunWorkflow, fontWeight = FontWeight.Bold, color = TextPrimary)
        } },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Workflow picker
            Text(Strings.ghWorkflows, fontSize = 12.sp, color = TextSecondary)
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                workflows.forEach { wf ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                        .background(if (selectedWf == wf) Blue.copy(0.1f) else Color.Transparent)
                        .clickable { selectedWf = wf }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.Settings, null, Modifier.size(16.dp), tint = if (selectedWf == wf) Blue else TextSecondary)
                        Column(Modifier.weight(1f)) {
                            Text(wf.name, fontSize = 13.sp, color = if (selectedWf == wf) Blue else TextPrimary, fontWeight = FontWeight.Medium)
                            Text(wf.path, fontSize = 10.sp, color = TextTertiary)
                        }
                        if (selectedWf == wf) Icon(Icons.Rounded.Check, null, Modifier.size(14.dp), tint = Blue)
                    }
                }
            }
            // Branch picker
            Text(Strings.ghPickBranch, fontSize = 12.sp, color = TextSecondary)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                branches.forEach { b -> BC(b, b == selectedBranch) { selectedBranch = b } }
            }
            if (dispatching) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(Modifier.size(16.dp), color = Blue, strokeWidth = 2.dp)
                    Text(Strings.ghRunning, fontSize = 12.sp, color = Blue)
                }
            }
        } },
        confirmButton = { TextButton(onClick = {
            if (selectedWf == null || dispatching) return@TextButton
            dispatching = true
            s.launch {
                val ok = GitHubManager.dispatchWorkflow(ctx, repo.owner, repo.name, selectedWf!!.id, selectedBranch)
                Toast.makeText(ctx, if (ok) Strings.done else Strings.error, Toast.LENGTH_SHORT).show()
                dispatching = false
                if (ok) onDone()
            }
        }, enabled = !dispatching) { Text(Strings.ghRunWorkflow, color = if (dispatching) TextTertiary else Color(0xFF34C759), fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.cancel, color = TextSecondary) } })
}

// ═══════════════════════════════════
// GitHub Settings Screen
// ═══════════════════════════════════
