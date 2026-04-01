package com.glassfiles.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glassfiles.data.AppSettings
import com.glassfiles.data.FileItem
import com.glassfiles.data.Strings
import com.glassfiles.data.github.GHRepo
import com.glassfiles.data.github.GitHubManager
import com.glassfiles.ui.theme.Blue
import com.glassfiles.ui.theme.SurfaceLight
import com.glassfiles.ui.theme.SurfaceWhite
import com.glassfiles.ui.theme.TextPrimary
import com.glassfiles.ui.theme.TextSecondary
import com.glassfiles.ui.theme.TextTertiary
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun GitHubUploadFromDeviceDialog(
    file: FileItem,
    onDismiss: () -> Unit,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var repos by remember { mutableStateOf<List<GHRepo>>(emptyList()) }
    var branches by remember { mutableStateOf<List<String>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var uploading by remember { mutableStateOf(false) }

    var selectedRepo by remember { mutableStateOf<GHRepo?>(null) }
    var selectedBranch by remember { mutableStateOf("") }
    var repoPath by remember { mutableStateOf(file.name) }
    var commitMsg by remember { mutableStateOf("Add ${file.name}") }
    var step by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        repos = GitHubManager.getRepos(context)
        loading = false
    }

    LaunchedEffect(selectedRepo) {
        if (selectedRepo != null) {
            branches = GitHubManager.getBranches(context, selectedRepo!!.owner, selectedRepo!!.name)
            selectedBranch = selectedRepo!!.defaultBranch
            step = 1
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceWhite,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.Cloud, null, Modifier.size(22.dp), tint = Color(0xFF238636))
                Text(Strings.ghUploadToGitHub, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 18.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    Modifier.fillMaxWidth().background(SurfaceLight, RoundedCornerShape(10.dp)).padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Rounded.InsertDriveFile, null, Modifier.size(20.dp), tint = TextSecondary)
                    Column(Modifier.weight(1f)) {
                        Text(file.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary, maxLines = 1)
                        Text(fmtUploadSizeDialog(file.size), fontSize = 11.sp, color = TextTertiary)
                    }
                }

                if (loading) {
                    Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Blue, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                } else if (step == 0) {
                    Text(Strings.ghSelectRepo, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
                    Column(
                        Modifier.fillMaxWidth().heightIn(max = 300.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        repos.forEach { repo ->
                            Row(
                                Modifier.fillMaxWidth().background(
                                    if (selectedRepo == repo) Blue.copy(0.1f) else Color.Transparent,
                                    RoundedCornerShape(8.dp)
                                ).clickable { selectedRepo = repo }.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    if (repo.isPrivate) Icons.Rounded.Lock else Icons.Rounded.FolderOpen,
                                    null,
                                    Modifier.size(16.dp),
                                    tint = if (repo.isPrivate) Color(0xFFFF9F0A) else Blue
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(repo.name, fontSize = 13.sp, color = TextPrimary, maxLines = 1)
                                    Text(repo.owner, fontSize = 10.sp, color = TextTertiary)
                                }
                            }
                        }
                    }
                } else {
                    Text("→ ${selectedRepo!!.owner}/${selectedRepo!!.name}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Blue)

                    Text(Strings.ghPickBranch, fontSize = 12.sp, color = TextSecondary)
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        branches.forEach { b ->
                            Box(
                                Modifier.background(if (b == selectedBranch) Blue.copy(0.15f) else SurfaceLight, RoundedCornerShape(6.dp))
                                    .clickable { selectedBranch = b }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(b, fontSize = 12.sp, color = if (b == selectedBranch) Blue else TextSecondary)
                            }
                        }
                    }

                    OutlinedTextField(
                        value = repoPath,
                        onValueChange = { repoPath = it },
                        label = { Text(Strings.ghFilePath) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = commitMsg,
                        onValueChange = { commitMsg = it },
                        label = { Text(Strings.ghCommitMsg) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (uploading) {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(Modifier.size(16.dp), color = Blue, strokeWidth = 2.dp)
                            Text(Strings.ghUploadingFile, fontSize = 12.sp, color = Blue)
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (step == 1 && selectedRepo != null) {
                TextButton(
                    onClick = {
                        if (repoPath.isBlank() || uploading) return@TextButton
                        uploading = true
                        scope.launch {
                            val ok = GitHubManager.uploadFileFromPath(
                                context,
                                selectedRepo!!.owner,
                                selectedRepo!!.name,
                                repoPath,
                                file.path,
                                commitMsg,
                                selectedBranch
                            )
                            android.widget.Toast.makeText(
                                context,
                                if (ok) Strings.ghUploadSuccess else Strings.error,
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                            uploading = false
                            if (ok) onDone()
                        }
                    },
                    enabled = !uploading
                ) {
                    Text(Strings.ghUpload, color = if (uploading) TextTertiary else Color(0xFF238636), fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            Row {
                if (step == 1) {
                    TextButton(onClick = { step = 0; selectedRepo = null }) {
                        Text(Strings.ghSelectRepo, color = TextSecondary, fontSize = 12.sp)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(Strings.cancel, color = TextSecondary)
                }
            }
        }
    )
}

@Composable
fun GitHubCommitDialog(
    filePaths: List<String>,
    onDismiss: () -> Unit,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var repos by remember { mutableStateOf<List<GHRepo>>(emptyList()) }
    var branches by remember { mutableStateOf<List<String>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var committing by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0) }
    var total by remember { mutableStateOf(0) }

    var selectedRepo by remember { mutableStateOf<GHRepo?>(null) }
    var selectedBranch by remember { mutableStateOf("") }
    var repoBasePath by remember { mutableStateOf("") }
    var commitMsg by remember { mutableStateOf("Add ${filePaths.size} files") }
    var step by remember { mutableIntStateOf(0) }

    val files = remember { filePaths.map { File(it) }.filter { it.exists() } }
    val totalSize = remember { files.sumOf { if (it.isFile) it.length() else 0L } }

    LaunchedEffect(Unit) {
        repos = GitHubManager.getRepos(context)
        loading = false
    }

    LaunchedEffect(selectedRepo) {
        if (selectedRepo != null) {
            branches = GitHubManager.getBranches(context, selectedRepo!!.owner, selectedRepo!!.name)
            selectedBranch = selectedRepo!!.defaultBranch
            step = 1
        }
    }

    AlertDialog(
        onDismissRequest = { if (!committing) onDismiss() },
        containerColor = SurfaceWhite,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.CloudUpload, null, Modifier.size(22.dp), tint = Color(0xFF238636))
                Text(Strings.ghCommitToGitHub, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 18.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    Modifier.fillMaxWidth().background(SurfaceLight, RoundedCornerShape(10.dp)).padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Rounded.Folder, null, Modifier.size(20.dp), tint = Blue)
                    Column(Modifier.weight(1f)) {
                        Text("${files.size} ${Strings.ghCommitFiles.lowercase()}", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                        Text(fmtUploadSizeDialog(totalSize), fontSize = 11.sp, color = TextTertiary)
                    }
                }

                Column(
                    Modifier.fillMaxWidth().background(SurfaceLight, RoundedCornerShape(8.dp)).padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    files.take(5).forEach { f ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (f.isDirectory) Icons.Rounded.Folder else Icons.Rounded.InsertDriveFile, null, Modifier.size(14.dp), tint = if (f.isDirectory) Blue else TextSecondary)
                            Text(f.name, fontSize = 12.sp, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (files.size > 5) {
                        Text("+${files.size - 5} more", fontSize = 11.sp, color = TextTertiary)
                    }
                }

                if (loading) {
                    Box(Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Blue, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                } else if (step == 0) {
                    Text(Strings.ghSelectRepo, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
                    Column(
                        Modifier.fillMaxWidth().heightIn(max = 250.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        repos.forEach { repo ->
                            Row(
                                Modifier.fillMaxWidth().background(
                                    if (selectedRepo == repo) Blue.copy(0.1f) else Color.Transparent,
                                    RoundedCornerShape(8.dp)
                                ).clickable { selectedRepo = repo }.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    if (repo.isPrivate) Icons.Rounded.Lock else Icons.Rounded.FolderOpen,
                                    null,
                                    Modifier.size(16.dp),
                                    tint = if (repo.isPrivate) Color(0xFFFF9F0A) else Blue
                                )
                                Text(repo.name, fontSize = 13.sp, color = TextPrimary, maxLines = 1, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                } else {
                    Text("→ ${selectedRepo!!.owner}/${selectedRepo!!.name}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Blue)

                    Text(Strings.ghPickBranch, fontSize = 12.sp, color = TextSecondary)
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        branches.forEach { b ->
                            Box(
                                Modifier.background(if (b == selectedBranch) Blue.copy(0.15f) else SurfaceLight, RoundedCornerShape(6.dp))
                                    .clickable { selectedBranch = b }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(b, fontSize = 12.sp, color = if (b == selectedBranch) Blue else TextSecondary)
                            }
                        }
                    }

                    OutlinedTextField(
                        value = repoBasePath,
                        onValueChange = { repoBasePath = it },
                        label = { Text(Strings.ghRepoPath) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("folder/subfolder") }
                    )

                    OutlinedTextField(
                        value = commitMsg,
                        onValueChange = { commitMsg = it },
                        label = { Text(Strings.ghCommitMsg) },
                        singleLine = false,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (committing) {
                        Column(Modifier.fillMaxWidth()) {
                            LinearProgressIndicator(
                                progress = { if (total > 0) progress.toFloat() / total else 0f },
                                modifier = Modifier.fillMaxWidth(),
                                color = Color(0xFF238636)
                            )
                            Spacer(Modifier.height(4.dp))
                            Text("${Strings.ghCommitting} $progress / $total", fontSize = 12.sp, color = Blue)
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (step == 1 && selectedRepo != null) {
                TextButton(
                    onClick = {
                        if (commitMsg.isBlank() || committing) return@TextButton
                        committing = true
                        scope.launch {
                            val fileData = mutableListOf<Pair<String, ByteArray>>()
                            files.forEach { f ->
                                if (f.isDirectory) collectFilesRecursiveDialog(f, f, repoBasePath, fileData)
                                else {
                                    val rp = if (repoBasePath.isNotBlank()) "$repoBasePath/${f.name}" else f.name
                                    try { fileData.add(rp to f.readBytes()) } catch (_: Exception) {}
                                }
                            }
                            total = fileData.size

                            val ok = GitHubManager.uploadMultipleFiles(
                                context,
                                selectedRepo!!.owner,
                                selectedRepo!!.name,
                                selectedBranch,
                                fileData,
                                commitMsg
                            ) { done, t -> progress = done; total = t }

                            android.widget.Toast.makeText(
                                context,
                                if (ok) Strings.ghCommitSuccess else Strings.ghCommitFailed,
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                            committing = false
                            if (ok) onDone()
                        }
                    },
                    enabled = !committing
                ) {
                    Text(Strings.ghCommitToGitHub, color = if (committing) TextTertiary else Color(0xFF238636), fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            if (!committing) {
                Row {
                    if (step == 1) {
                        TextButton(onClick = { step = 0; selectedRepo = null }) {
                            Text("← ${Strings.ghSelectRepo}", color = TextSecondary, fontSize = 12.sp)
                        }
                    }
                    TextButton(onClick = onDismiss) {
                        Text(Strings.cancel, color = TextSecondary)
                    }
                }
            }
        }
    )
}

private fun collectFilesRecursiveDialog(root: File, current: File, basePath: String, result: MutableList<Pair<String, ByteArray>>) {
    current.listFiles()?.forEach { f ->
        val rel = if (basePath.isNotBlank()) "$basePath/${f.relativeTo(root).path}" else f.relativeTo(root).path
        if (f.isDirectory) collectFilesRecursiveDialog(root, f, basePath, result)
        else if (f.length() < 50 * 1024 * 1024) {
            try { result.add(rel to f.readBytes()) } catch (_: Exception) {}
        }
    }
}

private fun fmtUploadSizeDialog(b: Long): String = when {
    b < 1024 -> "$b B"
    b < 1024L * 1024 -> "%.1f KB".format(b / 1024.0)
    else -> "%.1f MB".format(b / (1024.0 * 1024))
}
