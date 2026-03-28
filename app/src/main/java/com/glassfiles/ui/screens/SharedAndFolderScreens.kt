package com.glassfiles.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.glassfiles.data.*
import com.glassfiles.ui.components.*
import com.glassfiles.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun SharedScreen(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().background(SurfaceLight)) {
        Text(Strings.shared, style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold, color = TextPrimary,
            modifier = Modifier.padding(top = 60.dp, start = 16.dp, end = 16.dp, bottom = 4.dp))
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Rounded.PeopleOutline, null, Modifier.size(64.dp), tint = TextTertiary)
                Text(Strings.shared, style = MaterialTheme.typography.titleMedium, color = TextSecondary)
            }
        }
    }
}

enum class SortMode { NAME, DATE, SIZE, TYPE; val label: String get() = when (this) { NAME -> Strings.sortName; DATE -> Strings.sortDate; SIZE -> Strings.sortSize; TYPE -> Strings.sortType } }
enum class ViewMode { GRID, LIST }

/** Stores scroll positions per folder path */
object ScrollPositionStore {
    private val listPositions = mutableMapOf<String, Pair<Int, Int>>() // path → (index, offset)
    private val gridPositions = mutableMapOf<String, Pair<Int, Int>>()

    fun saveList(path: String, index: Int, offset: Int) { listPositions[path] = index to offset }
    fun saveGrid(path: String, index: Int, offset: Int) { gridPositions[path] = index to offset }
    fun getList(path: String): Pair<Int, Int> = listPositions[path] ?: (0 to 0)
    fun getGrid(path: String): Pair<Int, Int> = gridPositions[path] ?: (0 to 0)
}

@Composable
fun FolderDetailScreen(
    folderName: String, files: List<FileItem>, loading: Boolean = false, subtitle: String = "",
    onFileClick: (FileItem) -> Unit = {}, onBackClick: () -> Unit = {},
    onOpenTerminal: (() -> Unit)? = null, onAiAction: ((String, String?) -> Unit)? = null,
    onGitHubUpload: ((FileItem) -> Unit)? = null,
    onGitHubCommit: ((List<String>) -> Unit)? = null,
    appSettings: com.glassfiles.data.AppSettings? = null,
    folderPath: String = "",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    val trashMgr = remember { TrashManager(context) }; val favMgr = remember { FavoritesManager(context) }
    var searchQuery by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf(
        when (appSettings?.defaultSort) {
            com.glassfiles.data.DefaultSort.DATE -> SortMode.DATE
            com.glassfiles.data.DefaultSort.SIZE -> SortMode.SIZE
            com.glassfiles.data.DefaultSort.TYPE -> SortMode.TYPE
            else -> SortMode.NAME
        }
    ) }
    var sortAsc by remember { mutableStateOf(true) }
    var viewMode by remember { mutableStateOf(
        if (appSettings?.defaultView == com.glassfiles.data.DefaultView.LIST) ViewMode.LIST else ViewMode.GRID
    ) }
    var showSortMenu by remember { mutableStateOf(false) }
    var contextFile by remember { mutableStateOf<FileItem?>(null) }
    var renamingFile by remember { mutableStateOf<FileItem?>(null) }
    var pendingDeleteFile by remember { mutableStateOf<FileItem?>(null) }
    var renameText by remember { mutableStateOf("") }
    var viewingFile by remember { mutableStateOf<String?>(null) }
    var propertiesFile by remember { mutableStateOf<FileItem?>(null) }
    var clipFile by remember { mutableStateOf<Pair<FileItem, Boolean>?>(null) }
    var showProgress by remember { mutableStateOf(false) }
    var progressVal by remember { mutableFloatStateOf(0f) }
    var refreshKey by remember { mutableIntStateOf(0) }

    // Multi-select
    var selectMode by remember { mutableStateOf(false) }
    var selectedPaths by remember { mutableStateOf(setOf<String>()) }
    var convertFile by remember { mutableStateOf<FileItem?>(null) }
    var tagFile by remember { mutableStateOf<FileItem?>(null) }
    val tagManager = remember { TagManager(context) }
    var encryptFile by remember { mutableStateOf<FileItem?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showBatchRename by remember { mutableStateOf(false) }
    var audioFile by remember { mutableStateOf<FileItem?>(null) }
    fun toggleSelect(item: FileItem) {
        selectedPaths = if (item.path in selectedPaths) selectedPaths - item.path else selectedPaths + item.path
        if (selectedPaths.isEmpty()) selectMode = false
    }

    // ZIP viewer
    var zipViewFile by remember { mutableStateOf<String?>(null) }

    // Scroll position — restore from store
    val savedList = remember(folderPath) { ScrollPositionStore.getList(folderPath) }
    val savedGrid = remember(folderPath) { ScrollPositionStore.getGrid(folderPath) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = savedList.first, initialFirstVisibleItemScrollOffset = savedList.second)
    val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = savedGrid.first, initialFirstVisibleItemScrollOffset = savedGrid.second)

    // Save scroll position when leaving this screen
    DisposableEffect(folderPath) {
        onDispose {
            if (folderPath.isNotBlank()) {
                ScrollPositionStore.saveList(folderPath, listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
                ScrollPositionStore.saveGrid(folderPath, gridState.firstVisibleItemIndex, gridState.firstVisibleItemScrollOffset)
            }
        }
    }

    val sorted = remember(files, sortMode, sortAsc, searchQuery, refreshKey) {
        val f = if (searchQuery.isEmpty()) files else files.filter { it.name.contains(searchQuery, true) }
        val c = when (sortMode) {
            SortMode.NAME -> compareBy<FileItem> { !it.isDirectory }.thenBy { it.name.lowercase() }
            SortMode.DATE -> compareBy<FileItem> { !it.isDirectory }.thenByDescending { it.lastModified }
            SortMode.SIZE -> compareBy<FileItem> { !it.isDirectory }.thenByDescending { it.size }
            SortMode.TYPE -> compareBy<FileItem> { !it.isDirectory }.thenBy { it.extension }
        }
        if (sortAsc) f.sortedWith(c) else f.sortedWith(c.reversed())
    }

    // File viewer overlay with animation
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
                ArchiveHelper.decompress(File(zipViewFile!!)) { progressVal = it }
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
                        e in listOf("mp3","wav","ogg","flac","aac","m4a","wma","opus") -> audioFile = file
                        else -> openFileExternal(context, File(file.path))
                    }
                }
            }
            FileAction.OPEN_WITH -> openFileExternal(context, File(file.path))
            FileAction.COPY -> { clipFile = Pair(file, true); Toast.makeText(context, "${Strings.copied}: ${file.name}", Toast.LENGTH_SHORT).show() }
            FileAction.MOVE -> { clipFile = Pair(file, false); Toast.makeText(context, "${Strings.cutFile}: ${file.name}", Toast.LENGTH_SHORT).show() }
            FileAction.RENAME -> { renamingFile = file; renameText = file.name }
            FileAction.DELETE -> scope.launch { FileOperations.delete(File(file.path)); refreshKey++; Toast.makeText(context, Strings.deleted, Toast.LENGTH_SHORT).show() }
            FileAction.TRASH -> {
                if (appSettings?.confirmDelete != false) pendingDeleteFile = file
                else scope.launch { trashMgr.moveToTrash(File(file.path)); refreshKey++; Toast.makeText(context, Strings.toTrash, Toast.LENGTH_SHORT).show() }
            }
            FileAction.SHARE -> { try { val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(file.path)); val m = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension) ?: "*/*"
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = m; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, Strings.share)) } catch (_: Exception) {} }
            FileAction.COMPRESS -> scope.launch { showProgress = true; val r = ArchiveHelper.compress(File(file.path)) { progressVal = it }; showProgress = false; refreshKey++
                Toast.makeText(context, if (r != null) "Compressed: ${r.name}" else Strings.error, Toast.LENGTH_SHORT).show() }
            FileAction.DECOMPRESS -> scope.launch { showProgress = true; ArchiveHelper.decompress(File(file.path)) { progressVal = it }; showProgress = false; refreshKey++
                Toast.makeText(context, Strings.done, Toast.LENGTH_SHORT).show() }
            FileAction.FAVORITE -> favMgr.toggle(file.path, file.name, file.isDirectory)
            FileAction.INSTALL_APK -> installApk(context, File(file.path))
            FileAction.OPEN_IN_TERMINAL -> onOpenTerminal?.invoke()
            FileAction.COPY_PATH -> { (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("path", file.path))
                Toast.makeText(context, Strings.pathCopied, Toast.LENGTH_SHORT).show() }
            FileAction.PROPERTIES -> propertiesFile = file
            FileAction.CONVERT_IMAGE -> convertFile = file
            FileAction.TAG -> tagFile = file
            FileAction.ENCRYPT -> encryptFile = file
            FileAction.BATCH_RENAME -> { /* triggered from multi-select bar */ }
            FileAction.UPLOAD_GITHUB -> onGitHubUpload?.invoke(file)
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
        }
    }

    Column(modifier.fillMaxSize().background(SurfaceLight)) {
        // iOS-style top bar
        val topBg = if (ThemeState.isDark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)
        val circleBg = if (ThemeState.isDark) Color(0xFF3A3A3C) else Color(0xFFE5E5EA)
        Row(Modifier.fillMaxWidth().background(topBg).padding(top = 52.dp, start = 8.dp, end = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(circleBg).clickable(onClick = onBackClick),
                contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, Modifier.size(18.dp), tint = if (ThemeState.isDark) Color.White else TextPrimary)
            }
            Spacer(Modifier.weight(1f))
            Text(folderName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(Modifier.weight(1f))
            if (onOpenTerminal != null) {
                Spacer(Modifier.width(6.dp))
                Box(Modifier.size(36.dp).clip(CircleShape).background(circleBg).clickable(onClick = onOpenTerminal),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Terminal, null, Modifier.size(20.dp), tint = Color(0xFF00E676))
                }
            }
            // Paste button inline
            if (clipFile != null) { Spacer(Modifier.width(6.dp)); Box(Modifier.clip(RoundedCornerShape(8.dp)).background(Blue.copy(0.1f)).clickable {
                val (src, isCopy) = clipFile!!; val curPath = files.firstOrNull()?.path?.let { File(it).parent } ?: return@clickable
                scope.launch { showProgress = true
                    val op = if (isCopy) FileOperations.copy(File(src.path), File(curPath)) { progressVal = it } else FileOperations.move(File(src.path), File(curPath)) { progressVal = it }
                    showProgress = false; clipFile = null; refreshKey++; Toast.makeText(context, op.message, Toast.LENGTH_SHORT).show() }
            }.padding(horizontal = 8.dp, vertical = 6.dp)) { Icon(Icons.Rounded.ContentPaste, null, Modifier.size(18.dp), tint = Blue) } }
            // Create new file/folder
            Spacer(Modifier.width(6.dp))
            Box(Modifier.size(36.dp).clip(CircleShape).background(circleBg).clickable { showCreateDialog = true },
                contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Add, null, Modifier.size(20.dp), tint = if (ThemeState.isDark) Color.White else TextPrimary)
            }
            // "..." menu
            Spacer(Modifier.width(6.dp))
            Box {
                Box(Modifier.size(36.dp).clip(CircleShape).background(circleBg).clickable { showSortMenu = true },
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.MoreHoriz, null, Modifier.size(20.dp), tint = if (ThemeState.isDark) Color.White else TextPrimary)
                }
                DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                    // Select mode
                    DropdownMenuItem(text = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.CheckCircle, null, Modifier.size(20.dp), tint = Blue)
                        Text(if (selectMode) Strings.cancelSelect else Strings.selectMode) }
                    }, onClick = { selectMode = !selectMode; if (!selectMode) selectedPaths = emptySet(); showSortMenu = false })
                    HorizontalDivider()
                    // View mode
                    DropdownMenuItem(text = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(if (viewMode == ViewMode.GRID) Icons.Rounded.GridView else Icons.Rounded.ViewList, null, Modifier.size(20.dp), tint = Blue)
                        Text(if (viewMode == ViewMode.GRID) Strings.grid else Strings.list) }
                    }, onClick = { viewMode = if (viewMode == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID; showSortMenu = false })
                    HorizontalDivider()
                    // Sort options
                    SortMode.entries.forEach { m ->
                        DropdownMenuItem(text = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (sortMode == m) Icon(Icons.Rounded.Check, null, Modifier.size(18.dp), tint = Blue) else Spacer(Modifier.size(18.dp))
                            Text(m.label)
                            if (sortMode == m) Text(if (sortAsc) "↑" else "↓", color = TextSecondary)
                        } }, onClick = { if (sortMode == m) sortAsc = !sortAsc else { sortMode = m; sortAsc = true }; showSortMenu = false })
                    }
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("${sorted.size} ${Strings.objects}", color = TextSecondary) }, onClick = {})
                }
            }
        }
        // Search bar — dark in dark mode
        val searchBg = if (ThemeState.isDark) Color(0xFF2C2C2E) else Color(0xFFE9E9EB)
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).height(36.dp)
            .clip(RoundedCornerShape(10.dp)).background(searchBg).padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Rounded.Search, null, Modifier.size(18.dp), tint = TextSecondary)
                Text(if (searchQuery.isEmpty()) Strings.search else searchQuery, fontSize = 15.sp, color = if (searchQuery.isEmpty()) TextSecondary else TextPrimary)
            }
        }

        if (showProgress) LinearProgressIndicator(progress = { progressVal }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), color = Blue)

        // Multi-select toolbar
        if (selectMode && selectedPaths.isNotEmpty()) {
            val selBg = if (ThemeState.isDark) Color(0xFF2C2C2E) else Color(0xFFF2F2F7)
            Row(Modifier.fillMaxWidth().background(selBg).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${selectedPaths.size} выбрано", color = TextPrimary, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                IconButton(onClick = { selectedPaths.forEach { p -> scope.launch { trashMgr.moveToTrash(File(p)) } }; selectedPaths = emptySet(); selectMode = false; refreshKey++ }) {
                    Icon(Icons.Rounded.Delete, null, tint = Red) }
                IconButton(onClick = { /* copy first selected */ selectedPaths.firstOrNull()?.let { p -> clipFile = files.find { it.path == p }?.let { it to true } }; selectedPaths = emptySet(); selectMode = false }) {
                    Icon(Icons.Rounded.ContentCopy, null, tint = Blue) }
                IconButton(onClick = { selectedPaths.firstOrNull()?.let { p -> clipFile = files.find { it.path == p }?.let { it to false } }; selectedPaths = emptySet(); selectMode = false }) {
                    Icon(Icons.Rounded.DriveFileMove, null, tint = Blue) }
                IconButton(onClick = { showBatchRename = true }) {
                    Icon(Icons.Rounded.DriveFileRenameOutline, null, tint = Blue) }
                if (com.glassfiles.data.github.GitHubManager.isLoggedIn(context)) {
                    IconButton(onClick = { onGitHubCommit?.invoke(selectedPaths.toList()); selectedPaths = emptySet(); selectMode = false }) {
                        Icon(Icons.Rounded.Cloud, null, tint = Color(0xFF238636)) }
                }
                IconButton(onClick = { selectedPaths = emptySet(); selectMode = false }) {
                    Icon(Icons.Rounded.Close, null, tint = TextSecondary) }
            }
        }

        if (loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Blue, modifier = Modifier.size(36.dp), strokeWidth = 3.dp) }
        else if (sorted.isEmpty()) Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally) { Spacer(Modifier.height(80.dp))
            Icon(Icons.Rounded.FolderOpen, null, Modifier.size(48.dp), tint = TextTertiary)
            if (subtitle.isNotEmpty()) Text(subtitle, color = TextSecondary, fontSize = 12.sp) else Text(if (searchQuery.isEmpty()) Strings.folderEmpty else Strings.nothingFound, color = TextSecondary) }
        else AnimatedContent(viewMode, transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(200)) }, label = "v") { mode ->
            when (mode) {
                ViewMode.GRID -> LazyVerticalGrid(GridCells.Fixed(3), Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    state = gridState,
                    verticalArrangement = Arrangement.spacedBy(16.dp), horizontalArrangement = Arrangement.SpaceEvenly,
                    contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp)) {
                    items(sorted, key = { it.path }) { file ->
                        Box(Modifier.animateItem(fadeInSpec = tween(300), fadeOutSpec = tween(200))) {
                            val isSel = file.path in selectedPaths
                            if (file.isDirectory) FolderGridItem(file, selected = isSel,
                                onClick = { if (selectMode) toggleSelect(file) else act(FileAction.OPEN, file) },
                                onLongClick = { if (selectMode) toggleSelect(file) else contextFile = file })
                            else FileGridItem(file, selected = isSel,
                                onClick = { if (selectMode) toggleSelect(file) else act(FileAction.OPEN, file) },
                                onLongClick = { if (selectMode) toggleSelect(file) else contextFile = file })
                        }
                    }
                }
                ViewMode.LIST -> LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 100.dp)) {
                    items(sorted, key = { it.path }) { file ->
                        val isSel = file.path in selectedPaths
                        Column(Modifier.animateItem(fadeInSpec = tween(300), fadeOutSpec = tween(200))) {
                            FileListItem(file, selected = isSel,
                                onClick = { if (selectMode) toggleSelect(file) else act(FileAction.OPEN, file) },
                                onLongClick = { if (selectMode) toggleSelect(file) else contextFile = file })
                            IosStyleDivider()
                        }
                    }
                }
            }
        }
    }

    // Context menu
    FileContextMenu(file = contextFile, context = context, scope = scope, trashManager = trashMgr, favoritesManager = favMgr,
        onDismiss = { contextFile = null }, onAction = { a, f -> act(a, f) })
    // Convert image dialog
    if (convertFile != null) {
        val cf = convertFile!!
        val currentExt = cf.extension.lowercase()
        val formats = com.glassfiles.data.ImageFormat.entries.filter { it.extension != currentExt }
        AlertDialog(
            onDismissRequest = { convertFile = null },
            title = { Text(Strings.convert) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${cf.name}", color = TextSecondary, fontSize = 13.sp)
                formats.forEach { fmt ->
                    val fmtBg = if (ThemeState.isDark) Color(0xFF3A3A3C) else Color(0xFFE5E5EA)
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(fmtBg)
                        .clickable {
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                val result = com.glassfiles.data.FileConverter.convertImage(cf.path, fmt)
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                                    if (result.success) refreshKey++
                                }
                            }
                            convertFile = null
                        }.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Rounded.Transform, null, Modifier.size(20.dp), tint = Blue)
                            Text("→ ${fmt.label} (.${fmt.extension})", color = TextPrimary)
                        }
                    }
                }
            } },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { convertFile = null }) { Text(Strings.cancel) } }
        )
    }
    // Tag selector dialog
    if (tagFile != null) {
        TagSelectorDialog(filePath = tagFile!!.path, tagManager = tagManager, onDismiss = { tagFile = null })
    }
    if (renamingFile != null) AlertDialog(onDismissRequest = { renamingFile = null }, title = { Text(Strings.rename) },
        text = { OutlinedTextField(renameText, { renameText = it }, singleLine = true) },
        confirmButton = { TextButton(onClick = { scope.launch { FileOperations.rename(File(renamingFile!!.path), renameText); renamingFile = null; refreshKey++ } }) { Text("OK") } },
        dismissButton = { TextButton(onClick = { renamingFile = null }) { Text(Strings.cancel) } })
    // Confirm delete dialog
    if (pendingDeleteFile != null) AlertDialog(
        onDismissRequest = { pendingDeleteFile = null },
        title = { Text(Strings.confirmDeleteTitle) },
        text = { Text("\"${pendingDeleteFile?.name}\" будет перемещён в корзину") },
        confirmButton = { TextButton(onClick = { val f = pendingDeleteFile!!; pendingDeleteFile = null
            scope.launch { trashMgr.moveToTrash(File(f.path)); refreshKey++; Toast.makeText(context, Strings.toTrash, Toast.LENGTH_SHORT).show() }
        }) { Text(Strings.delete, color = Red) } },
        dismissButton = { TextButton(onClick = { pendingDeleteFile = null }) { Text(Strings.cancel) } })
    FilePropertiesSheet(file = propertiesFile, onDismiss = { propertiesFile = null })
    // Encrypt dialog
    if (encryptFile != null) EncryptDialog(File(encryptFile!!.path), onDismiss = { encryptFile = null }, onDone = { encryptFile = null; refreshKey++ })
    // Audio player
    if (audioFile != null) AudioPlayerDialog(audioFile!!.path, onDismiss = { audioFile = null })
    // Create file/folder
    val curPath = files.firstOrNull()?.path?.let { File(it).parent } ?: ""
    if (showCreateDialog) CreateItemDialog(curPath, onDismiss = { showCreateDialog = false }, onCreated = { showCreateDialog = false; refreshKey++ })
    // Batch rename
    if (showBatchRename) {
        val selectedFilesList = selectedPaths.mapNotNull { p -> File(p).takeIf { it.exists() } }
        if (selectedFilesList.isNotEmpty()) BatchRenameDialog(selectedFilesList, onDismiss = { showBatchRename = false }, onDone = { showBatchRename = false; selectedPaths = emptySet(); selectMode = false; refreshKey++ })
    }
}

// ═══════════════════════════════════
// ZIP Viewer — in-app archive browser
// ═══════════════════════════════════
@Composable
fun ZipViewerScreen(zipPath: String, onBack: () -> Unit, onExtract: () -> Unit) {
    val entries = remember { ArchiveHelper.listZipContents(File(zipPath)) }
    val fileName = remember { File(zipPath).name }

    Column(Modifier.fillMaxSize().background(SurfaceLight)) {
        GlassTopBar {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, Modifier.size(20.dp), tint = Blue) }
            Column(Modifier.weight(1f)) {
                Text(fileName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text("${entries.size} файлов в архиве", fontSize = 12.sp, color = TextSecondary)
            }
            Box(Modifier.clip(RoundedCornerShape(8.dp)).background(Blue.copy(0.1f)).clickable(onClick = onExtract).padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(Strings.decompress, color = Blue, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        HorizontalDivider(color = Color(0xFFE8E8E8))
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp)) {
            items(entries) { entry ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(
                        if (entry.endsWith("/")) Icons.Rounded.Folder else Icons.Rounded.InsertDriveFile,
                        null, Modifier.size(24.dp), tint = if (entry.endsWith("/")) FolderBlue else TextSecondary
                    )
                    Text(entry, fontSize = 14.sp, color = TextPrimary)
                }
                HorizontalDivider(Modifier.padding(start = 52.dp), color = Color(0xFFF0F0F0))
            }
        }
    }
}
