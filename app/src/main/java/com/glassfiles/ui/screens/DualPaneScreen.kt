package com.glassfiles.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glassfiles.data.*
import com.glassfiles.ui.components.*
import com.glassfiles.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun DualPaneScreen(onBack: () -> Unit, appSettings: AppSettings? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val rootPath = "/storage/emulated/0"

    // Left pane state
    var leftPath by remember { mutableStateOf(rootPath) }
    var leftFiles by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var leftLoading by remember { mutableStateOf(true) }
    var leftSelected by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Right pane state
    var rightPath by remember { mutableStateOf("$rootPath/Download") }
    var rightFiles by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var rightLoading by remember { mutableStateOf(true) }
    var rightSelected by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Active pane (for copy/move target)
    var activePane by remember { mutableIntStateOf(0) } // 0=left, 1=right

    fun loadFiles(path: String, onResult: (List<FileItem>) -> Unit, onLoading: (Boolean) -> Unit) {
        onLoading(true)
        scope.launch {
            val files = withContext(Dispatchers.IO) {
                FileManager.listFiles(path, appSettings?.showHiddenFiles ?: false)
            }
            onResult(files)
            onLoading(false)
        }
    }

    // Load initial
    LaunchedEffect(leftPath) { loadFiles(leftPath, { leftFiles = it }, { leftLoading = it }) }
    LaunchedEffect(rightPath) { loadFiles(rightPath, { rightFiles = it }, { rightLoading = it }) }

    Column(Modifier.fillMaxSize().background(SurfaceLight)) {
        // Top bar
        Row(Modifier.fillMaxWidth().background(SurfaceWhite).padding(top = 44.dp, start = 4.dp, end = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, Modifier.size(20.dp), tint = Blue) }
            Text(Strings.dualPane, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 20.sp, modifier = Modifier.weight(1f))
            // Copy selected to other pane
            val hasSelection = leftSelected.isNotEmpty() || rightSelected.isNotEmpty()
            if (hasSelection) {
                IconButton(onClick = {
                    scope.launch {
                        val (srcFiles, destPath) = if (leftSelected.isNotEmpty()) leftSelected to rightPath else rightSelected to leftPath
                        withContext(Dispatchers.IO) {
                            srcFiles.forEach { path ->
                                val src = File(path); val dest = File(destPath, src.name)
                                try { src.copyRecursively(dest, overwrite = true) } catch (_: Exception) {}
                            }
                        }
                        leftSelected = emptySet(); rightSelected = emptySet()
                        loadFiles(leftPath, { leftFiles = it }, { leftLoading = it })
                        loadFiles(rightPath, { rightFiles = it }, { rightLoading = it })
                        Toast.makeText(context, Strings.copied, Toast.LENGTH_SHORT).show()
                    }
                }) { Icon(Icons.Rounded.ContentCopy, null, Modifier.size(20.dp), tint = Blue) }

                IconButton(onClick = {
                    scope.launch {
                        val (srcFiles, destPath) = if (leftSelected.isNotEmpty()) leftSelected to rightPath else rightSelected to leftPath
                        withContext(Dispatchers.IO) {
                            srcFiles.forEach { path ->
                                val src = File(path); val dest = File(destPath, src.name)
                                try { src.copyRecursively(dest, overwrite = true); src.deleteRecursively() } catch (_: Exception) {}
                            }
                        }
                        leftSelected = emptySet(); rightSelected = emptySet()
                        loadFiles(leftPath, { leftFiles = it }, { leftLoading = it })
                        loadFiles(rightPath, { rightFiles = it }, { rightLoading = it })
                        Toast.makeText(context, Strings.done, Toast.LENGTH_SHORT).show()
                    }
                }) { Icon(Icons.Rounded.DriveFileMove, null, Modifier.size(20.dp), tint = Color(0xFFFF9F0A)) }

                Text("${leftSelected.size + rightSelected.size}", fontSize = 13.sp, color = Blue, fontWeight = FontWeight.Bold)
            }
        }

        // Two panes
        Row(Modifier.fillMaxSize().weight(1f)) {
            // Left pane
            PaneView(
                path = leftPath,
                files = leftFiles,
                loading = leftLoading,
                selected = leftSelected,
                isActive = activePane == 0,
                onPathChange = { leftPath = it; leftSelected = emptySet() },
                onSelect = { path ->
                    leftSelected = if (path in leftSelected) leftSelected - path else leftSelected + path
                },
                onActivate = { activePane = 0 },
                modifier = Modifier.weight(1f)
            )

            // Divider
            Box(Modifier.fillMaxHeight().width(1.dp).background(SeparatorColor))

            // Right pane
            PaneView(
                path = rightPath,
                files = rightFiles,
                loading = rightLoading,
                selected = rightSelected,
                isActive = activePane == 1,
                onPathChange = { rightPath = it; rightSelected = emptySet() },
                onSelect = { path ->
                    rightSelected = if (path in rightSelected) rightSelected - path else rightSelected + path
                },
                onActivate = { activePane = 1 },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun PaneView(
    path: String, files: List<FileItem>, loading: Boolean, selected: Set<String>,
    isActive: Boolean, onPathChange: (String) -> Unit, onSelect: (String) -> Unit,
    onActivate: () -> Unit, modifier: Modifier = Modifier
) {
    val displayName = File(path).name.ifBlank { "Root" }

    Column(modifier.fillMaxHeight().background(SurfaceLight).clickable { onActivate() }) {
        // Path header
        Row(Modifier.fillMaxWidth().background(if (isActive) Blue.copy(0.08f) else SurfaceWhite)
            .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            // Back
            if (path != "/storage/emulated/0") {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, Modifier.size(16.dp).clickable {
                    val parent = File(path).parent ?: "/storage/emulated/0"
                    onPathChange(parent)
                }, tint = Blue)
            }
            Text(displayName, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (isActive) Blue else TextPrimary,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (selected.isNotEmpty()) {
                Text("${selected.size}", fontSize = 11.sp, color = Blue, fontWeight = FontWeight.Bold,
                    modifier = Modifier.background(Blue.copy(0.1f), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 1.dp))
            }
        }
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(if (isActive) Blue.copy(0.3f) else SeparatorColor))

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Blue, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        } else if (files.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(Strings.folderEmpty, color = TextTertiary, fontSize = 12.sp)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(files.sortedWith(compareBy<FileItem> { !it.isDirectory }.thenBy { it.name.lowercase() }), key = { it.path }) { file ->
                    val isSel = file.path in selected
                    Row(Modifier.fillMaxWidth()
                        .background(if (isSel) Blue.copy(0.1f) else Color.Transparent)
                        .clickable {
                            if (selected.isNotEmpty()) onSelect(file.path)
                            else if (file.isDirectory) onPathChange(file.path)
                        }
                        .combinedClickable(
                            onClick = {
                                if (selected.isNotEmpty()) onSelect(file.path)
                                else if (file.isDirectory) onPathChange(file.path)
                            },
                            onLongClick = { onSelect(file.path) }
                        )
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {

                        if (isSel) {
                            Icon(Icons.Rounded.CheckCircle, null, Modifier.size(18.dp), tint = Blue)
                        } else {
                            Icon(
                                if (file.isDirectory) Icons.Rounded.Folder else Icons.Rounded.InsertDriveFile,
                                null, Modifier.size(18.dp),
                                tint = if (file.isDirectory) FolderBlue else TextSecondary
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text(file.name, fontSize = 12.sp, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (!file.isDirectory && file.size > 0) {
                                Text(file.formattedSize, fontSize = 10.sp, color = TextTertiary)
                            }
                        }
                    }
                }
            }
        }
    }
}
