package com.glassfiles.ui

import android.app.Activity
import android.content.Intent
import android.webkit.MimeTypeMap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.glassfiles.data.*
import com.glassfiles.data.drive.GoogleDriveManager
import com.glassfiles.ui.components.*
import com.glassfiles.ui.screens.*
import com.glassfiles.ui.theme.*
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import java.io.File

enum class AppScreen { MAIN, TERMINAL, SEARCH, TRASH, STORAGE, AI_CHAT, SETTINGS, DUPLICATES, QR_SCANNER, OCR, TAGGED_FILES, DEVICE_INFO, APP_MANAGER, BOOKMARKS, DIFF, NOTES, CONTENT_SEARCH, SHIZUKU, FTP, DUAL_PANE, THEME, GITHUB }

@Composable
fun GlassFilesApp(hasPermission: Boolean = false, onRequestPermission: () -> Unit = {}, appSettings: com.glassfiles.data.AppSettings? = null) {
    if (!hasPermission) { PermissionScreen(onRequestPermission); return }

    val context = LocalContext.current
    val settings = appSettings ?: remember { com.glassfiles.data.AppSettings(context) }

    var selectedTab by remember { mutableIntStateOf(2) }
    var folderStack by remember { mutableStateOf(listOf<Pair<String, String>>()) }
    var driveSignedIn by remember { mutableStateOf(false) }
    var activeScreen by remember { mutableStateOf(AppScreen.MAIN) }
    var previousScreen by remember { mutableStateOf(AppScreen.MAIN) }
    var terminalDir by remember { mutableStateOf<String?>(null) }
    var terminalWasOpened by remember { mutableStateOf(false) }
    var aiInitialPrompt by remember { mutableStateOf<String?>(null) }
    var aiInitialImage by remember { mutableStateOf<String?>(null) }
    var selectedTagName by remember { mutableStateOf("") }

    // GitHub persistent state — like terminal, stays alive when minimized
    var githubWasOpened by remember { mutableStateOf(false) }
    var githubMiniMode by remember { mutableStateOf(false) } // true = floating mini window
    var githubUploadFile by remember { mutableStateOf<FileItem?>(null) } // file pending upload to GitHub

    fun navigateTo(screen: AppScreen) {
        if (screen == AppScreen.GITHUB) {
            githubWasOpened = true
            githubMiniMode = false
            selectedTab = 2 // Reset to Browse so GitHub tab doesn't re-trigger
        }
        previousScreen = activeScreen
        activeScreen = screen
    }

    val backdrop = rememberLayerBackdrop()
    val trashManager = remember { TrashManager(context) }

    LaunchedEffect(Unit) {
        driveSignedIn = GoogleDriveManager.isSignedIn(context)
        // Auto-clean trash (30 days)
        trashManager.autoClean(30)
    }

    val signInLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) GoogleSignIn.getSignedInAccountFromIntent(result.data).addOnSuccessListener { driveSignedIn = true }
    }

    val tabs = listOf(TabItem(Icons.Outlined.Schedule, Strings.recents), TabItem(Icons.Outlined.People, Strings.shared), TabItem(Icons.Outlined.Folder, Strings.browse), TabItem(Icons.Outlined.Code, Strings.github))

    fun openFileExternal(path: String) {
        try {
            val file = File(path); if (!file.exists()) return
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension) ?: "*/*"
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW).apply { setDataAndType(uri, mime); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, "Open"))
        } catch (_: Exception) {}
    }

    fun goBack() {
        // When leaving GitHub fullscreen, minimize to mini-window instead of destroying
        if (activeScreen == AppScreen.GITHUB && githubWasOpened) {
            githubMiniMode = true
            activeScreen = previousScreen
            previousScreen = AppScreen.MAIN
            return
        }
        val prev = previousScreen
        previousScreen = AppScreen.MAIN
        activeScreen = prev
    }

    BackHandler(enabled = true) {
        when {
            activeScreen == AppScreen.GITHUB && githubWasOpened -> {
                githubMiniMode = true
                activeScreen = previousScreen
                previousScreen = AppScreen.MAIN
            }
            activeScreen != AppScreen.MAIN -> goBack()
            folderStack.isNotEmpty() -> {
                folderStack = folderStack.dropLast(1)
                if (folderStack.isEmpty() && previousScreen != AppScreen.MAIN) {
                    val prev = previousScreen; previousScreen = AppScreen.MAIN; activeScreen = prev
                }
            }
            else -> (context as? Activity)?.moveTaskToBack(true)
        }
    }

    Box(Modifier.fillMaxSize().background(SurfaceLight)) {
        // Terminal layer — always alive but hidden behind opaque content when not active
        if (terminalWasOpened) {
            Box(Modifier.fillMaxSize()
                .graphicsLayer {
                    alpha = if (activeScreen == AppScreen.TERMINAL) 1f else 0f
                }
            ) {
                TerminalScreen(initialDir = terminalDir, onBackClick = { goBack() }, onOpenFile = { openFileExternal(it) })
            }
        }

        // GitHub — single persistent instance, switches between fullscreen and floating window
        if (githubWasOpened) {
            if (!githubMiniMode && activeScreen == AppScreen.GITHUB) {
                // Fullscreen mode
                Box(Modifier.fillMaxSize()) {
                    GitHubScreen(
                        onBack = {
                            githubMiniMode = true
                            val prev = previousScreen
                            activeScreen = prev
                            previousScreen = AppScreen.MAIN
                        },
                        onMinimize = {
                            githubMiniMode = true
                            val prev = previousScreen
                            activeScreen = prev
                            previousScreen = AppScreen.MAIN
                        }
                    )
                }
            }
        }

        // Animated screen transitions — each branch has opaque background
        AnimatedContent(
            targetState = activeScreen,
            transitionSpec = {
                if (targetState == AppScreen.MAIN) {
                    (fadeIn(tween(250)) + slideInVertically(tween(300)) { -it / 8 }) togetherWith
                    (fadeOut(tween(200)) + slideOutVertically(tween(250)) { it / 6 })
                } else {
                    (fadeIn(tween(250)) + slideInVertically(tween(300)) { it / 6 }) togetherWith
                    (fadeOut(tween(200)) + slideOutVertically(tween(250)) { -it / 8 })
                }
            },
            label = "screen"
        ) { screen ->
            when (screen) {
                AppScreen.TERMINAL -> Box(Modifier.fillMaxSize())
                AppScreen.SEARCH -> Box(Modifier.fillMaxSize().background(SurfaceLight)) {
                    GlobalSearchScreen(onBack = { goBack() }, onFileClick = { p ->
                        val f = File(p); if (f.isDirectory) { previousScreen = AppScreen.SEARCH; folderStack = listOf(f.name to f.absolutePath); activeScreen = AppScreen.MAIN } else openFileExternal(p)
                    })
                }
                AppScreen.TRASH -> Box(Modifier.fillMaxSize().background(SurfaceLight)) { TrashScreen(trashManager, onBack = { goBack() }) }
                AppScreen.STORAGE -> Box(Modifier.fillMaxSize().background(SurfaceLight)) { StorageAnalyzerScreen(onBack = { goBack() }) }
                AppScreen.AI_CHAT -> Box(Modifier.fillMaxSize().background(SurfaceLight)) {
                    AiChatScreen(onBack = { goBack(); aiInitialPrompt = null; aiInitialImage = null }, initialPrompt = aiInitialPrompt, initialImageBase64 = aiInitialImage)
                }
                AppScreen.SETTINGS -> Box(Modifier.fillMaxSize().background(SurfaceLight)) { SettingsScreen(settings = settings, onBack = { goBack() }) }
                AppScreen.DUPLICATES -> Box(Modifier.fillMaxSize().background(SurfaceLight)) { DuplicatesScreen(onBack = { goBack() }) }
                AppScreen.QR_SCANNER -> Box(Modifier.fillMaxSize().background(SurfaceLight)) { QrScannerScreen(onBack = { goBack() }) }
                AppScreen.OCR -> Box(Modifier.fillMaxSize().background(SurfaceLight)) { OcrScreen(onBack = { goBack() }) }
                AppScreen.TAGGED_FILES -> Box(Modifier.fillMaxSize().background(SurfaceLight)) {
                    TaggedFilesScreen(tagName = selectedTagName, onBack = { goBack() },
                        onFileClick = { path -> val f = java.io.File(path); if (f.isDirectory) { folderStack = listOf(f.name to f.absolutePath); activeScreen = AppScreen.MAIN } else openFileExternal(path) })
                }
                AppScreen.DEVICE_INFO -> DeviceInfoScreen(onBack = { goBack() })
                AppScreen.APP_MANAGER -> AppManagerScreen(onBack = { goBack() })
                AppScreen.BOOKMARKS -> BookmarksScreen(onBack = { goBack() },
                    onNavigate = { path -> folderStack = listOf(File(path).name to path); activeScreen = AppScreen.MAIN })
                AppScreen.DIFF -> Box(Modifier.fillMaxSize().background(SurfaceLight)) { DiffScreen(onBack = { goBack() }) }
                AppScreen.NOTES -> Box(Modifier.fillMaxSize().background(SurfaceLight)) { NotesScreen(onBack = { goBack() }) }
                AppScreen.CONTENT_SEARCH -> Box(Modifier.fillMaxSize().background(SurfaceLight)) {
                    ContentSearchScreen(onBack = { goBack() }, onFileClick = { p ->
                        val f = File(p); if (f.isDirectory) { previousScreen = AppScreen.CONTENT_SEARCH; folderStack = listOf(f.name to f.absolutePath); activeScreen = AppScreen.MAIN } else openFileExternal(p)
                    })
                }
                AppScreen.SHIZUKU -> Box(Modifier.fillMaxSize().background(SurfaceLight)) {
                    ShizukuScreen(onBack = { goBack() }, onBrowseRestricted = { path ->
                        previousScreen = AppScreen.SHIZUKU
                        val displayName = path.removePrefix("shizuku://").substringAfterLast("/")
                        folderStack = listOf(displayName to path)
                        activeScreen = AppScreen.MAIN
                    })
                }
                AppScreen.FTP -> Box(Modifier.fillMaxSize().background(SurfaceLight)) { FtpScreen(onBack = { goBack() }) }
                AppScreen.DUAL_PANE -> Box(Modifier.fillMaxSize().background(SurfaceLight)) { DualPaneScreen(onBack = { goBack() }, appSettings = settings) }
                AppScreen.THEME -> Box(Modifier.fillMaxSize().background(SurfaceLight)) { ThemeScreen(settings = settings, onBack = { goBack() }) }
                AppScreen.GITHUB -> Box(Modifier.fillMaxSize()) // Content in persistent layer above
                AppScreen.MAIN -> {
                    Box(Modifier.fillMaxSize().background(SurfaceLight).layerBackdrop(backdrop)) {
                        AnimatedContent(
                            targetState = folderStack,
                            transitionSpec = {
                                if (targetState.size > initialState.size) {
                                    (fadeIn(tween(200)) + slideInHorizontally(tween(300)) { it / 3 }) togetherWith
                                    (fadeOut(tween(150)) + slideOutHorizontally(tween(200)) { -it / 5 })
                                } else {
                                    (fadeIn(tween(200)) + slideInHorizontally(tween(300)) { -it / 3 }) togetherWith
                                    (fadeOut(tween(150)) + slideOutHorizontally(tween(200)) { it / 5 })
                                }
                            },
                            label = "folder"
                        ) { stack ->
                            if (stack.isNotEmpty()) {
                                val (name, path) = stack.last()
                                val isDrive = path.startsWith("gdrive://")
                                val isShizuku = path.startsWith("shizuku://")
                                var files by remember(path) { mutableStateOf<List<FileItem>>(emptyList()) }
                                var loading by remember(path) { mutableStateOf(true) }
                                var errorMsg by remember(path) { mutableStateOf("") }

                                LaunchedEffect(path) {
                                    loading = true; errorMsg = ""
                                    try {
                                        if (isDrive) {
                                            val r = GoogleDriveManager.listFilesDebug(context, path.removePrefix("gdrive://"))
                                            files = r.files
                                            if (r.error.isNotEmpty()) errorMsg = "${r.error}\n\n${r.debug}"
                                            else if (files.isEmpty()) errorMsg = "0 файлов\n\n${r.debug}"
                                        } else if (isShizuku) {
                                            val realPath = path.removePrefix("shizuku://")
                                            val shizukuFiles = ShizukuManager.listRestrictedDir(realPath)
                                            files = shizukuFiles.map { sf ->
                                                val ext = sf.name.substringAfterLast('.', "").lowercase()
                                                FileItem(
                                                    name = sf.name,
                                                    path = "shizuku://${sf.path}",
                                                    size = sf.size,
                                                    isDirectory = sf.isDirectory,
                                                    type = if (sf.isDirectory) FileType.FOLDER else getFileType(ext),
                                                    extension = if (sf.isDirectory) "" else ext
                                                )
                                            }
                                            if (files.isEmpty()) {
                                                errorMsg = ShizukuManager.getLastError(realPath).ifBlank { Strings.shEmptyOrNoAccess }
                                            }
                                        } else {
                                            files = FileManager.listFiles(path, settings.showHiddenFiles)
                                        }
                                    } catch (e: Exception) { errorMsg = "${e.javaClass.simpleName}: ${e.message}" }
                                    loading = false
                                }

                                FolderDetailScreen(folderName = name, files = files, loading = loading, subtitle = errorMsg,
                                    folderPath = path,
                                    onFileClick = { if (it.isDirectory) folderStack = folderStack + (it.name to it.path) },
                                    onBackClick = {
                                        folderStack = folderStack.dropLast(1)
                                        if (folderStack.isEmpty() && previousScreen != AppScreen.MAIN) {
                                            val prev = previousScreen; previousScreen = AppScreen.MAIN; activeScreen = prev
                                        }
                                    },
                                    onOpenTerminal = if (!isDrive && !isShizuku) {{ terminalDir = path; terminalWasOpened = true; navigateTo(AppScreen.TERMINAL) }} else null,
                                    onAiAction = { prompt, image -> aiInitialPrompt = prompt; aiInitialImage = image; navigateTo(AppScreen.AI_CHAT) },
                                    onGitHubUpload = { file -> githubUploadFile = file },
                                    appSettings = settings)
                            } else {
                                AnimatedContent(selectedTab, transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) }, label = "tab") { tab ->
                                    when (tab) {
                                        0 -> RecentsScreen(context = context, onFileClick = { file ->
                                            if (file.isDirectory) folderStack = listOf(file.name to file.path)
                                        }, onAiAction = { prompt, image -> aiInitialPrompt = prompt; aiInitialImage = image; navigateTo(AppScreen.AI_CHAT) })
                                        1 -> SharedScreen()
                                        2 -> BrowseScreen(driveSignedIn = driveSignedIn,
                                            onFolderClick = { folderStack = listOf(it.name to it.path) },
                                            onLocationClick = { folderStack = listOf(it.name to it.path) },
                                            onDriveSignIn = { signInLauncher.launch(GoogleDriveManager.getSignInIntent(context)) },
                                            onDriveOpen = { folderStack = listOf("Google Drive" to "gdrive://root") },
                                            onSearch = { navigateTo(AppScreen.SEARCH) },
                                            onTrash = { navigateTo(AppScreen.TRASH) },
                                            onStorage = { navigateTo(AppScreen.STORAGE) },
                                            onDuplicates = { navigateTo(AppScreen.DUPLICATES) },
                                            onQrScan = { navigateTo(AppScreen.QR_SCANNER) },
                                            onOcr = { navigateTo(AppScreen.OCR) },
                                            onDeviceInfo = { navigateTo(AppScreen.DEVICE_INFO) },
                                            onAppManager = { navigateTo(AppScreen.APP_MANAGER) },
                                            onBookmarks = { navigateTo(AppScreen.BOOKMARKS) },
                                            onDiff = { navigateTo(AppScreen.DIFF) },
                                            onNotes = { navigateTo(AppScreen.NOTES) },
                                            onContentSearch = { navigateTo(AppScreen.CONTENT_SEARCH) },
                                            onShizuku = { navigateTo(AppScreen.SHIZUKU) },
                                            onFtp = { navigateTo(AppScreen.FTP) },
                                            onDualPane = { navigateTo(AppScreen.DUAL_PANE) },
                                            onTheme = { navigateTo(AppScreen.THEME) },
                                            onGitHub = { navigateTo(AppScreen.GITHUB) },
                                            onTagClick = { tag -> selectedTagName = tag; navigateTo(AppScreen.TAGGED_FILES) })
                                        3 -> { LaunchedEffect(Unit) { navigateTo(AppScreen.GITHUB) }; Box(Modifier.fillMaxSize().background(SurfaceLight)) }
                                    }
                                }
                            }
                        }
                    }

                    // FABs with glass effect
                    AnimatedVisibility(folderStack.isEmpty(), enter = fadeIn(tween(300)) + scaleIn(tween(300)),
                        exit = fadeOut(tween(200)) + scaleOut(tween(200)), modifier = Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize()) {
                            Box(Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 168.dp).clickable { navigateTo(AppScreen.AI_CHAT) }) {
                                GlassFab(backdrop, Icons.Rounded.AutoAwesome, iconTint = Color.White, tintColor = Color(0x66238636))
                            }
                            Box(Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 112.dp).clickable { terminalWasOpened = true; navigateTo(AppScreen.TERMINAL) }) {
                                GlassFab(backdrop, Icons.Rounded.Terminal, iconTint = Color(0xFF00E676), tintColor = Color(0x441A1A2E))
                                if (terminalWasOpened) Box(Modifier.align(Alignment.TopEnd).size(12.dp).background(Color(0xFF00E676), CircleShape))
                            }
                            Box(Modifier.align(Alignment.BottomCenter)) {
                                GlassBottomTabBar(backdrop, selectedTab, { selectedTab = it }, tabs)
                            }
                        }
                    }
                }
            }
        }

        // GitHub floating resizable window — contains actual GitHubScreen
        if (githubWasOpened && githubMiniMode) {
            GitHubFloatingWindow(
                onExpand = {
                    githubMiniMode = false
                    previousScreen = activeScreen
                    activeScreen = AppScreen.GITHUB
                },
                onClose = {
                    githubMiniMode = false
                    githubWasOpened = false
                }
            )
        }

        // GitHub upload from file manager dialog
        if (githubUploadFile != null) {
            GitHubUploadFromDeviceDialog(
                file = githubUploadFile!!,
                onDismiss = { githubUploadFile = null },
                onDone = { githubUploadFile = null }
            )
        }
    }
}

@Composable
private fun PermissionScreen(onRequest: () -> Unit) {
    Box(Modifier.fillMaxSize().background(SurfaceLight), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(32.dp)) {
            Icon(Icons.Rounded.Folder, null, Modifier.size(72.dp), tint = Blue)
            Text("Glass Files", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(Strings.permissionNeeded, fontSize = 16.sp, color = TextSecondary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Button(onClick = onRequest, colors = ButtonDefaults.buttonColors(containerColor = Blue)) { Text(Strings.grantAccess, color = Color.White, fontSize = 16.sp) }
        }
    }
}

// ═══════════════════════════════════════════════
// GitHub Floating Window — resizable, draggable
// Contains actual GitHubScreen inside
// ═══════════════════════════════════════════════

@Composable
private fun GitHubFloatingWindow(
    onExpand: () -> Unit,
    onClose: () -> Unit
) {
    val density = LocalDensity.current

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screenW = constraints.maxWidth.toFloat()
        val screenH = constraints.maxHeight.toFloat()

        val minW = with(density) { 240.dp.toPx() }
        val minH = with(density) { 320.dp.toPx() }
        val maxW = screenW * 0.95f
        val maxH = screenH * 0.90f

        // Window size
        var windowW by remember { mutableFloatStateOf(screenW * 0.60f) }
        var windowH by remember { mutableFloatStateOf(screenH * 0.55f) }

        // Window position
        var offsetX by remember { mutableFloatStateOf(screenW * 0.35f) }
        var offsetY by remember { mutableFloatStateOf(screenH * 0.35f) }

        fun clamp() {
            val m = with(density) { 8.dp.toPx() }
            offsetX = offsetX.coerceIn(-m, screenW - windowW + m)
            offsetY = offsetY.coerceIn(with(density) { 32.dp.toPx() }, screenH - windowH + m)
        }

        val titleBarH = with(density) { 40.dp.toPx() }

        // Semi-transparent scrim
        Box(
            Modifier.fillMaxSize()
                .background(Color.Black.copy(alpha = 0.15f))
                .clickable(
                    indication = null,
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                ) { /* block taps through */ }
        )

        // Floating window
        Box(
            Modifier
                .offset { IntOffset(offsetX.toInt(), offsetY.toInt()) }
                .size(
                    width = with(density) { windowW.toDp() },
                    height = with(density) { windowH.toDp() }
                )
                .shadow(16.dp, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceLight)
                .border(0.5.dp, SeparatorColor, RoundedCornerShape(16.dp))
        ) {
            Column(Modifier.fillMaxSize()) {
                // ─── Title bar — draggable ───
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(SurfaceWhite)
                        .pointerInput(Unit) {
                            detectDragGestures { _, dragAmount ->
                                offsetX += dragAmount.x
                                offsetY += dragAmount.y
                                clamp()
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Drag handle
                    Box(
                        Modifier.width(32.dp).height(4.dp)
                            .clip(RoundedCornerShape(2.dp)).background(TextTertiary)
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Rounded.Code, null, Modifier.size(16.dp), tint = Blue)
                    Spacer(Modifier.width(6.dp))
                    Text("GitHub", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, modifier = Modifier.weight(1f))

                    // Expand button
                    Box(
                        Modifier.size(28.dp).clip(CircleShape)
                            .background(Blue.copy(0.1f)).clickable { onExpand() },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Rounded.OpenInFull, null, Modifier.size(14.dp), tint = Blue) }
                    Spacer(Modifier.width(6.dp))

                    // Close button
                    Box(
                        Modifier.size(28.dp).clip(CircleShape)
                            .background(Color(0xFFFF3B30).copy(0.1f)).clickable { onClose() },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Rounded.Close, null, Modifier.size(14.dp), tint = Color(0xFFFF3B30)) }
                }

                Box(Modifier.fillMaxWidth().height(0.5.dp).background(SeparatorColor))

                // ─── GitHub content — clipped, scrollable inside ───
                Box(Modifier.fillMaxSize().weight(1f)) {
                    GitHubScreen(
                        onBack = { onClose() },
                        onMinimize = { /* already mini */ }
                    )
                }
            }

            // ─── Resize handle — bottom-right corner ───
            Box(
                Modifier.align(Alignment.BottomEnd).size(28.dp)
                    .pointerInput(Unit) {
                        detectDragGestures { _, dragAmount ->
                            windowW = (windowW + dragAmount.x).coerceIn(minW, maxW)
                            windowH = (windowH + dragAmount.y).coerceIn(minH, maxH)
                            clamp()
                        }
                    }.padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.DragHandle, null,
                    Modifier.size(14.dp).graphicsLayer { rotationZ = -45f }, tint = TextTertiary)
            }

            // ─── Resize handle — bottom edge ───
            Box(
                Modifier.align(Alignment.BottomCenter)
                    .fillMaxWidth(0.4f).height(12.dp)
                    .pointerInput(Unit) {
                        detectDragGestures { _, dragAmount ->
                            windowH = (windowH + dragAmount.y).coerceIn(minH, maxH)
                            clamp()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.width(40.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(TextTertiary.copy(0.5f)))
            }

            // ─── Resize handle — right edge ───
            Box(
                Modifier.align(Alignment.CenterEnd)
                    .width(12.dp).fillMaxHeight(0.4f)
                    .pointerInput(Unit) {
                        detectDragGestures { _, dragAmount ->
                            windowW = (windowW + dragAmount.x).coerceIn(minW, maxW)
                            clamp()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.width(3.dp).height(40.dp).clip(RoundedCornerShape(2.dp)).background(TextTertiary.copy(0.5f)))
            }
        }
    }
}

// ═══════════════════════════════════
// GitHub Upload from File Manager
// Pick repo → branch → path → commit
// ═══════════════════════════════════

@Composable
private fun GitHubUploadFromDeviceDialog(
    file: FileItem,
    onDismiss: () -> Unit,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var repos by remember { mutableStateOf<List<com.glassfiles.data.github.GHRepo>>(emptyList()) }
    var branches by remember { mutableStateOf<List<String>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var uploading by remember { mutableStateOf(false) }

    var selectedRepo by remember { mutableStateOf<com.glassfiles.data.github.GHRepo?>(null) }
    var selectedBranch by remember { mutableStateOf("") }
    var repoPath by remember { mutableStateOf(file.name) }
    var commitMsg by remember { mutableStateOf("Add ${file.name}") }
    var step by remember { mutableIntStateOf(0) } // 0=pick repo, 1=configure

    LaunchedEffect(Unit) {
        repos = com.glassfiles.data.github.GitHubManager.getRepos(context)
        loading = false
    }

    LaunchedEffect(selectedRepo) {
        if (selectedRepo != null) {
            branches = com.glassfiles.data.github.GitHubManager.getBranches(context, selectedRepo!!.owner, selectedRepo!!.name)
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
                // File info
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(SurfaceLight).padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Rounded.InsertDriveFile, null, Modifier.size(20.dp), tint = TextSecondary)
                    Column(Modifier.weight(1f)) {
                        Text(file.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary, maxLines = 1)
                        Text(fmtUploadSize(file.size), fontSize = 11.sp, color = TextTertiary)
                    }
                }

                if (loading) {
                    Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Blue, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                } else if (step == 0) {
                    // Step 1: Pick repo
                    Text(Strings.ghSelectRepo, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
                    Column(
                        Modifier.fillMaxWidth().heightIn(max = 300.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        repos.forEach { repo ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                    .background(if (selectedRepo == repo) Blue.copy(0.1f) else Color.Transparent)
                                    .clickable { selectedRepo = repo }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    if (repo.isPrivate) Icons.Rounded.Lock else Icons.Rounded.FolderOpen,
                                    null, Modifier.size(16.dp),
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
                    // Step 2: Configure upload
                    Text("→ ${selectedRepo!!.owner}/${selectedRepo!!.name}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Blue)

                    // Branch picker
                    Text(Strings.ghPickBranch, fontSize = 12.sp, color = TextSecondary)
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        branches.forEach { b ->
                            Box(
                                Modifier.clip(RoundedCornerShape(6.dp))
                                    .background(if (b == selectedBranch) Blue.copy(0.15f) else SurfaceLight)
                                    .clickable { selectedBranch = b }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) { Text(b, fontSize = 12.sp, color = if (b == selectedBranch) Blue else TextSecondary) }
                        }
                    }

                    OutlinedTextField(
                        repoPath, { repoPath = it },
                        label = { Text(Strings.ghFilePath) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                    )

                    OutlinedTextField(
                        commitMsg, { commitMsg = it },
                        label = { Text(Strings.ghCommitMsg) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
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
                            val ok = com.glassfiles.data.github.GitHubManager.uploadFileFromPath(
                                context, selectedRepo!!.owner, selectedRepo!!.name,
                                repoPath, file.path, commitMsg, selectedBranch
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
            if (step == 1) {
                TextButton(onClick = { step = 0; selectedRepo = null }) {
                    Text(Strings.ghSelectRepo, color = TextSecondary, fontSize = 12.sp)
                }
            }
            TextButton(onClick = onDismiss) { Text(Strings.cancel, color = TextSecondary) }
        }
    )
}

private fun fmtUploadSize(b: Long): String = when {
    b < 1024 -> "$b B"
    b < 1024L * 1024 -> "%.1f KB".format(b / 1024.0)
    else -> "%.1f MB".format(b / (1024.0 * 1024))
}


