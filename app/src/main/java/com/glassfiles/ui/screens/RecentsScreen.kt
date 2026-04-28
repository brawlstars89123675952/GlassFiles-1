package com.glassfiles.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.glassfiles.data.*
import com.glassfiles.notifications.AppNotifications
import com.glassfiles.ui.components.*
import com.glassfiles.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun RecentsScreen(
    context: Context,
    onFileClick: (FileItem) -> Unit = {},
    onAiAction: ((String, String?) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val trashMgr = remember { TrashManager(context) }
    val favMgr = remember { FavoritesManager(context) }

    var searchQuery by remember { mutableStateOf("") }
    var recentFiles by remember { mutableStateOf<List<FileItem>?>(null) }
    var contextFile by remember { mutableStateOf<FileItem?>(null) }
    var renamingFile by remember { mutableStateOf<FileItem?>(null) }
    var renameText by remember { mutableStateOf("") }
    var propertiesFile by remember { mutableStateOf<FileItem?>(null) }
    var viewingFile by remember { mutableStateOf<String?>(null) }
    var zipViewFile by remember { mutableStateOf<String?>(null) }
    var showProgress by remember { mutableStateOf(false) }
    var progressVal by remember { mutableFloatStateOf(0f) }
    var refreshKey by remember { mutableIntStateOf(0) }

    // Load on IO thread — no UI blocking
    LaunchedEffect(Unit, refreshKey) {
        recentFiles = withContext(Dispatchers.IO) {
            FileManager.getRecentFiles(context, 50)
        }
    }

    // File viewer overlay
    AnimatedVisibility(viewingFile != null, enter = fadeIn(tween(200)) + slideInVertically(tween(300)) { it / 4 },
        exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { it / 4 }) {
        viewingFile?.let { FileViewerScreen(filePath = it, onBack = { viewingFile = null }) }
    }
    if (viewingFile != null) return

    // ZIP viewer
    if (zipViewFile != null) {
        ZipViewerScreen(zipPath = zipViewFile!!, onBack = { zipViewFile = null }, onExtract = {
            scope.launch {
                showProgress = true
                val archivePath = zipViewFile!!
                val result = ArchiveHelper.decompress(File(archivePath)) { progressVal = it }
                AppNotifications.notifyFileOperation(context, "Extract", result != null, File(archivePath).name, result?.absolutePath ?: archivePath)
                showProgress = false; refreshKey++; zipViewFile = null
                Toast.makeText(context, Strings.done, Toast.LENGTH_SHORT).show()
            }
        })
        return
    }

    fun act(action: FileAction, file: FileItem) {
        when (action) {
            FileAction.OPEN -> {
                if (file.isDirectory) onFileClick(file)
                else {
                    val e = file.extension.lowercase()
                    when {
                        e in listOf("zip","tar","gz","tgz","tar.gz","7z") -> zipViewFile = file.path
                        e in listOf("jpg","jpeg","png","gif","webp","heic","bmp","svg",
                            "txt","md","log","json","xml","kt","java","py","js","ts","tsx","jsx","html","css","c","cpp","h","hpp",
                            "sh","bash","yaml","yml","toml","cfg","ini","conf","properties","gradle","pro",
                            "rb","go","rs","swift","php","sql","csv","tsv","env","bat","ps1","lua","r","dart","scala",
                            "makefile","dockerfile","gitignore","editorconfig") -> viewingFile = file.path
                        e == "apk" -> installApk(context, File(file.path))
                        else -> openFileExternal(context, File(file.path))
                    }
                }
            }
            FileAction.OPEN_WITH -> openFileExternal(context, File(file.path))
            FileAction.COPY -> Toast.makeText(context, "${Strings.copied}: ${file.name}", Toast.LENGTH_SHORT).show()
            FileAction.MOVE -> Toast.makeText(context, "${Strings.cutFile}: ${file.name}", Toast.LENGTH_SHORT).show()
            FileAction.RENAME -> { renamingFile = file; renameText = file.name }
            FileAction.DELETE -> scope.launch {
                val op = FileOperations.delete(File(file.path))
                AppNotifications.notifyFileOperation(context, "Delete", op.success, file.name, file.path)
                refreshKey++
                Toast.makeText(context, Strings.deleted, Toast.LENGTH_SHORT).show()
            }
            FileAction.TRASH -> scope.launch {
                val moved = trashMgr.moveToTrash(File(file.path))
                AppNotifications.notifyFileOperation(context, "Move to trash", moved, file.name, file.path)
                refreshKey++
                Toast.makeText(context, Strings.toTrash, Toast.LENGTH_SHORT).show()
            }
            FileAction.SHARE -> { try { val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(file.path)); val m = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension) ?: "*/*"
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = m; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, Strings.share)) } catch (_: Exception) {} }
            FileAction.COMPRESS -> scope.launch { showProgress = true; val r = ArchiveHelper.compress(File(file.path)) { progressVal = it }; showProgress = false; refreshKey++
                AppNotifications.notifyFileOperation(context, "Compress", r != null, file.name, r?.absolutePath ?: file.path)
                Toast.makeText(context, if (r != null) "Compressed: ${r.name}" else Strings.error, Toast.LENGTH_SHORT).show() }
            FileAction.DECOMPRESS -> scope.launch { showProgress = true; val r = ArchiveHelper.decompress(File(file.path)) { progressVal = it }; showProgress = false; refreshKey++
                AppNotifications.notifyFileOperation(context, "Extract", r != null, file.name, r?.absolutePath ?: file.path)
                Toast.makeText(context, Strings.done, Toast.LENGTH_SHORT).show() }
            FileAction.FAVORITE -> favMgr.toggle(file.path, file.name, file.isDirectory)
            FileAction.INSTALL_APK -> installApk(context, File(file.path))
            FileAction.OPEN_IN_TERMINAL -> { /* no terminal access from recents */ }
            FileAction.COPY_PATH -> { (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("path", file.path))
                Toast.makeText(context, Strings.pathCopied, Toast.LENGTH_SHORT).show() }
            FileAction.PROPERTIES -> propertiesFile = file
            FileAction.AI_SUMMARIZE -> {
                scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    val content = com.glassfiles.data.ai.AiManager.readFileForAi(File(file.path))
                    if (content != null) {
                        val prompt = if (Strings.lang == com.glassfiles.data.AppLanguage.RUSSIAN) "Сделай краткое резюме этого файла" else "Summarize this file (${file.name}):\n\n$content"
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onAiAction?.invoke(prompt, null) }
                    } else {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { Toast.makeText(context, "Cannot read file", Toast.LENGTH_SHORT).show() }
                    }
                }
            }
            FileAction.AI_DESCRIBE -> {
                scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    val base64 = com.glassfiles.data.ai.AiManager.encodeImage(File(file.path))
                    if (base64 != null) {
                        val prompt = if (Strings.lang == com.glassfiles.data.AppLanguage.RUSSIAN) "Подробно опиши что на этом изображении" else "Describe this image in detail (${file.name})"
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onAiAction?.invoke(prompt, base64) }
                    } else {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { Toast.makeText(context, "Cannot load photo", Toast.LENGTH_SHORT).show() }
                    }
                }
            }
            FileAction.CONVERT_IMAGE -> { /* handled in FolderDetailScreen */ }
            FileAction.TAG -> { /* handled in FolderDetailScreen */ }
            FileAction.ENCRYPT -> { /* handled in FolderDetailScreen */ }
            FileAction.BATCH_RENAME -> { /* handled in FolderDetailScreen */ }
            FileAction.UPLOAD_GITHUB -> { /* handled in FolderDetailScreen */ }
            FileAction.SELECT -> { /* handled in FolderDetailScreen */ }
        }
    }

    Column(modifier.fillMaxSize().background(SurfaceLight)) {
        Text(
            Strings.recents,
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            modifier = Modifier.padding(top = 60.dp, start = 16.dp, end = 16.dp, bottom = 4.dp)
        )

        GlassSearchBar(searchQuery, { searchQuery = it },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

        if (showProgress) LinearProgressIndicator(progress = { progressVal }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), color = Blue)

        val files = recentFiles

        if (files == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Blue, modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
            }
        } else {
            val filtered = if (searchQuery.isEmpty()) files
            else files.filter { it.name.contains(searchQuery, true) }

            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.SearchOff, null, Modifier.size(48.dp), tint = TextTertiary)
                        Text(
                            if (searchQuery.isEmpty()) "No recent files" else Strings.nothingFound,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    GridCells.Fixed(3),
                    Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp)
                ) {
                    items(filtered, key = { it.path }) { file ->
                        Box(Modifier.animateItem(fadeInSpec = tween(300), fadeOutSpec = tween(200))) {
                            if (file.isDirectory) FolderGridItem(file, onClick = { act(FileAction.OPEN, file) }, onLongClick = { contextFile = file })
                            else FileGridItem(file, onClick = { act(FileAction.OPEN, file) }, onLongClick = { contextFile = file })
                        }
                    }
                }
            }
        }
    }

    // Context menu
    FileContextMenu(file = contextFile, context = context, scope = scope, trashManager = trashMgr, favoritesManager = favMgr,
        onDismiss = { contextFile = null }, onAction = { a, f -> act(a, f) })

    // Rename dialog
    if (renamingFile != null) AlertDialog(onDismissRequest = { renamingFile = null }, title = { Text(Strings.rename) },
        text = { OutlinedTextField(renameText, { renameText = it }, singleLine = true) },
        confirmButton = { TextButton(onClick = { scope.launch {
            val target = renamingFile ?: return@launch
            val op = FileOperations.rename(File(target.path), renameText)
            AppNotifications.notifyFileOperation(context, "Rename", op.success, target.name, op.destPath.ifBlank { target.path })
            renamingFile = null
            refreshKey++
        } }) { Text("OK") } },
        dismissButton = { TextButton(onClick = { renamingFile = null }) { Text(Strings.cancel) } })

    // Properties sheet
    FilePropertiesSheet(file = propertiesFile, onDismiss = { propertiesFile = null })
}
