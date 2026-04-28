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
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.glassfiles.data.*
import com.glassfiles.data.drive.GoogleDriveManager
import com.glassfiles.notifications.AppNotificationEvent
import com.glassfiles.notifications.AppNotificationPreferences
import com.glassfiles.notifications.AppNotificationTarget
import com.glassfiles.notifications.AppNotifications
import com.glassfiles.notifications.GitHubNotificationTarget
import com.glassfiles.ui.components.*
import com.glassfiles.ui.screens.*
import com.glassfiles.ui.theme.*
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.launch
import java.io.File

enum class AppScreen { MAIN, TERMINAL, SEARCH, TRASH, STORAGE, AI_HUB, AI_CHAT, AI_CODING, AI_IMAGE, AI_VIDEO, AI_KEYS, AI_MODELS, AI_SETTINGS, SETTINGS, DUPLICATES, QR_SCANNER, OCR, TAGGED_FILES, DEVICE_INFO, APP_MANAGER, BOOKMARKS, DIFF, NOTES, CONTENT_SEARCH, SHIZUKU, FTP, DUAL_PANE, THEME, GITHUB }

@Composable
fun GlassFilesApp(
    hasPermission: Boolean = false,
    onRequestPermission: () -> Unit = {},
    appSettings: com.glassfiles.data.AppSettings? = null,
    githubNotificationTarget: GitHubNotificationTarget? = null,
    onGitHubNotificationTargetConsumed: () -> Unit = {},
    appNotificationTarget: AppNotificationTarget? = null,
    onAppNotificationTargetConsumed: () -> Unit = {}
) {
    if (!hasPermission) { PermissionScreen(onRequestPermission); return }

    val context = LocalContext.current
    val settings = appSettings ?: remember { com.glassfiles.data.AppSettings(context) }

    com.glassfiles.ui.theme.ThemeState.folderStyle = settings.folderIconStyle
    com.glassfiles.ui.theme.ThemeState.fileFontSize = settings.fileFontSize

    var selectedTab by remember { mutableIntStateOf(2) }
    var folderStack by remember { mutableStateOf(listOf<Pair<String, String>>()) }
    var driveSignedIn by remember { mutableStateOf(false) }
    val browseScrollState = rememberLazyListState()
    var activeScreen by remember { mutableStateOf(AppScreen.MAIN) }
    var previousScreen by remember { mutableStateOf(AppScreen.MAIN) }
    var terminalDir by remember { mutableStateOf<String?>(null) }
    var terminalWasOpened by remember { mutableStateOf(false) }
    var aiInitialPrompt by remember { mutableStateOf<String?>(null) }
    var aiInitialImage by remember { mutableStateOf<String?>(null) }
    var selectedTagName by remember { mutableStateOf("") }

    var githubWasOpened by remember { mutableStateOf(false) }
    var githubMiniMode by remember { mutableStateOf(false) }
    var githubBubbleMode by remember { mutableStateOf(false) }
    var ghWinW by remember { mutableFloatStateOf(-1f) }
    var ghWinH by remember { mutableFloatStateOf(-1f) }
    var ghWinX by remember { mutableFloatStateOf(-1f) }
    var ghWinY by remember { mutableFloatStateOf(-1f) }
    var githubUploadFile by remember { mutableStateOf<FileItem?>(null) }
    var githubCommitFiles by remember { mutableStateOf<List<String>?>(null) }
    var pendingGitHubTarget by remember { mutableStateOf<GitHubNotificationTarget?>(null) }
    val githubAvatarUrl = remember { com.glassfiles.data.github.GitHubManager.getCachedUser(context)?.avatarUrl }

    fun navigateTo(screen: AppScreen) {
        if (screen == AppScreen.GITHUB) {
            githubWasOpened = true
            githubMiniMode = false
            selectedTab = 2
        }
        previousScreen = activeScreen
        activeScreen = screen
    }

    val backdrop = rememberLayerBackdrop()
    val trashManager = remember { TrashManager(context) }

    LaunchedEffect(githubNotificationTarget) {
        val target = githubNotificationTarget ?: return@LaunchedEffect
        pendingGitHubTarget = target
        navigateTo(AppScreen.GITHUB)
    }

    LaunchedEffect(appNotificationTarget) {
        val target = appNotificationTarget ?: return@LaunchedEffect
        when (target.destination) {
            AppNotificationTarget.DEST_STORAGE -> navigateTo(AppScreen.STORAGE)
            AppNotificationTarget.DEST_SETTINGS,
            AppNotificationTarget.DEST_NOTIFICATIONS -> navigateTo(AppScreen.SETTINGS)
            AppNotificationTarget.DEST_TERMINAL -> {
                terminalWasOpened = true
                navigateTo(AppScreen.TERMINAL)
            }
            AppNotificationTarget.DEST_GITHUB -> navigateTo(AppScreen.GITHUB)
            AppNotificationTarget.DEST_PATH -> {
                val file = File(target.path)
                val folder = if (file.isDirectory) file else file.parentFile
                if (folder != null && folder.exists()) {
                    folderStack = listOf(folder.name to folder.absolutePath)
                }
                previousScreen = activeScreen
                activeScreen = AppScreen.MAIN
            }
            else -> activeScreen = AppScreen.MAIN
        }
        onAppNotificationTargetConsumed()
    }

    LaunchedEffect(Unit) {
        driveSignedIn = GoogleDriveManager.isSignedIn(context)
        trashManager.autoClean(30)
    }

    val signInLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) GoogleSignIn.getSignedInAccountFromIntent(result.data).addOnSuccessListener { driveSignedIn = true }
    }

    val tabs = listOf(TabItem(Icons.Outlined.Schedule, Strings.recents), TabItem(Icons.Outlined.People, Strings.shared), TabItem(Icons.Outlined.Folder, Strings.browse), TabItem(Icons.Outlined.AccountCircle, Strings.github, imageUrl = githubAvatarUrl))

    fun openFileExternal(path: String) {
        try {
            val file = File(path); if (!file.exists()) return
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension) ?: "*/*"
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW).apply { setDataAndType(uri, mime); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, "Open"))
        } catch (_: Exception) {}
    }

    fun goBack() {
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

    fun closeGitHubFully() {
        githubMiniMode = false
        githubBubbleMode = false
        githubWasOpened = false
        activeScreen = AppScreen.MAIN
        previousScreen = AppScreen.MAIN
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
        if (terminalWasOpened) {
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = if (activeScreen == AppScreen.TERMINAL) 1f else 0f }) {
                TerminalScreen(initialDir = terminalDir, onBackClick = { goBack() }, onOpenFile = { openFileExternal(it) })
            }
        }

        if (githubWasOpened) {
            if (!githubMiniMode && activeScreen == AppScreen.GITHUB) {
                Box(Modifier.fillMaxSize()) {
                    GitHubScreen(
                        initialTarget = pendingGitHubTarget,
                        onInitialTargetConsumed = {
                            pendingGitHubTarget = null
                            onGitHubNotificationTargetConsumed()
                        },
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
                        },
                        onClose = { closeGitHubFully() }
                    )
                }
            }
        }

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
                AppScreen.AI_HUB -> Box(Modifier.fillMaxSize().background(SurfaceLight)) {
                    AiHubScreen(
                        onBack = { goBack() },
                        onChat = { navigateTo(AppScreen.AI_CHAT) },
                        onCoding = { navigateTo(AppScreen.AI_CODING) },
                        onImage = { navigateTo(AppScreen.AI_IMAGE) },
                        onVideo = { navigateTo(AppScreen.AI_VIDEO) },
                        onModels = { navigateTo(AppScreen.AI_MODELS) },
                        onKeys = { navigateTo(AppScreen.AI_KEYS) },
                        onSettings = { navigateTo(AppScreen.AI_SETTINGS) },
                    )
                }
                AppScreen.AI_SETTINGS -> Box(Modifier.fillMaxSize().background(SurfaceLight)) {
                    AiSettingsScreen(onBack = { goBack() })
                }
                AppScreen.AI_CHAT -> Box(Modifier.fillMaxSize().background(SurfaceLight)) {
                    AiChatScreen(onBack = { goBack(); aiInitialPrompt = null; aiInitialImage = null }, initialPrompt = aiInitialPrompt, initialImageBase64 = aiInitialImage)
                }
                AppScreen.AI_CODING -> Box(Modifier.fillMaxSize().background(SurfaceLight)) {
                    AiCodingScreen(onBack = { goBack() })
                }
                AppScreen.AI_IMAGE -> Box(Modifier.fillMaxSize().background(SurfaceLight)) {
                    AiImageGenScreen(onBack = { goBack() })
                }
                AppScreen.AI_VIDEO -> Box(Modifier.fillMaxSize().background(SurfaceLight)) {
                    AiVideoGenScreen(onBack = { goBack() })
                }
                AppScreen.AI_KEYS -> Box(Modifier.fillMaxSize().background(SurfaceLight)) {
                    AiKeysScreen(onBack = { goBack() })
                }
                AppScreen.AI_MODELS -> Box(Modifier.fillMaxSize().background(SurfaceLight)) {
                    AiModelsScreen(onBack = { goBack() })
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
                AppScreen.BOOKMARKS -> BookmarksScreen(onBack = { goBack() }, onNavigate = { path -> folderStack = listOf(File(path).name to path); activeScreen = AppScreen.MAIN })
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
                AppScreen.GITHUB -> Box(Modifier.fillMaxSize())
                AppScreen.MAIN -> {
                    Box(Modifier.fillMaxSize().background(SurfaceLight).layerBackdrop(backdrop)) {
                        AnimatedContent(targetState = folderStack, transitionSpec = {
                            if (targetState.size > initialState.size) {
                                (fadeIn(tween(200)) + slideInHorizontally(tween(300)) { it / 3 }) togetherWith
                                (fadeOut(tween(150)) + slideOutHorizontally(tween(200)) { -it / 5 })
                            } else {
                                (fadeIn(tween(200)) + slideInHorizontally(tween(300)) { -it / 3 }) togetherWith
                                (fadeOut(tween(150)) + slideOutHorizontally(tween(200)) { it / 5 })
                            }
                        }, label = "folder") { stack ->
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
                                            if (r.error.isNotEmpty()) {
                                                errorMsg = "${r.error}\n\n${r.debug}"
                                                AppNotifications.post(
                                                    context,
                                                    AppNotificationEvent(
                                                        source = AppNotificationPreferences.SOURCE_DRIVE,
                                                        type = "drive_error",
                                                        title = "Google Drive error",
                                                        body = r.error,
                                                        externalId = "drive:${path}:${r.error}",
                                                        target = AppNotificationTarget(AppNotificationTarget.DEST_HOME),
                                                        important = true
                                                    )
                                                )
                                            }
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
                                                val shizukuError = ShizukuManager.getLastError(realPath)
                                                errorMsg = shizukuError.ifBlank { Strings.shEmptyOrNoAccess }
                                                if (shizukuError.isNotBlank()) {
                                                    AppNotifications.post(
                                                        context,
                                                        AppNotificationEvent(
                                                            source = AppNotificationPreferences.SOURCE_SHIZUKU,
                                                            type = "shizuku_access",
                                                            title = "Shizuku access warning",
                                                            body = shizukuError.take(180),
                                                            externalId = "shizuku:$realPath:$shizukuError",
                                                            target = AppNotificationTarget(AppNotificationTarget.DEST_PATH, path),
                                                            important = true
                                                        )
                                                    )
                                                }
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
                                    onGitHubCommit = { paths -> githubCommitFiles = paths },
                                    appSettings = settings)
                            } else {
                                AnimatedContent(selectedTab, transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) }, label = "tab") { tab ->
                                    when (tab) {
                                        0 -> RecentsScreen(context = context, onFileClick = { file -> if (file.isDirectory) folderStack = listOf(file.name to file.path) }, onAiAction = { prompt, image -> aiInitialPrompt = prompt; aiInitialImage = image; navigateTo(AppScreen.AI_CHAT) })
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
                                            onSettings = { navigateTo(AppScreen.SETTINGS) },
                                            onTagClick = { tag -> selectedTagName = tag; navigateTo(AppScreen.TAGGED_FILES) },
                                            scrollState = browseScrollState)
                                        3 -> { LaunchedEffect(Unit) { navigateTo(AppScreen.GITHUB) }; Box(Modifier.fillMaxSize().background(SurfaceLight)) }
                                    }
                                }
                            }
                        }
                    }

                    AnimatedVisibility(folderStack.isEmpty(), enter = fadeIn(tween(300)) + scaleIn(tween(300)), exit = fadeOut(tween(200)) + scaleOut(tween(200)), modifier = Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize()) {
                            Box(Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 148.dp).clickable { navigateTo(AppScreen.AI_HUB) }) {
                                GlassFab(backdrop, Icons.Rounded.AutoAwesome, iconTint = Color.White, tintColor = Color(0x66238636))
                            }
                            Box(Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 96.dp).clickable { terminalWasOpened = true; navigateTo(AppScreen.TERMINAL) }) {
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

        if (githubWasOpened && githubMiniMode && !githubBubbleMode) {
            GitHubFloatingWindow(
                onExpand = {
                    githubMiniMode = false
                    previousScreen = activeScreen
                    activeScreen = AppScreen.GITHUB
                },
                onClose = { closeGitHubFully() },
                onCollapse = { githubBubbleMode = true },
                winW = ghWinW, winH = ghWinH, winX = ghWinX, winY = ghWinY,
                onGeometryChange = { w, h, x, y -> ghWinW = w; ghWinH = h; ghWinX = x; ghWinY = y }
            )
        }

        if (githubWasOpened && githubBubbleMode) {
            GitHubBubble(onExpand = { githubBubbleMode = false; githubMiniMode = true }, onClose = { closeGitHubFully() })
        }

        if (githubUploadFile != null) {
            GitHubUploadFromDeviceDialog(file = githubUploadFile!!, onDismiss = { githubUploadFile = null }, onDone = { githubUploadFile = null })
        }

        if (githubCommitFiles != null) {
            GitHubCommitDialog(filePaths = githubCommitFiles!!, onDismiss = { githubCommitFiles = null }, onDone = { githubCommitFiles = null })
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

@Composable
private fun GitHubFloatingWindow(
    onExpand: () -> Unit,
    onClose: () -> Unit,
    onCollapse: () -> Unit,
    winW: Float, winH: Float, winX: Float, winY: Float,
    onGeometryChange: (Float, Float, Float, Float) -> Unit
) {
    val density = LocalDensity.current

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screenW = constraints.maxWidth.toFloat()
        val screenH = constraints.maxHeight.toFloat()

        val minW = with(density) { 240.dp.toPx() }
        val minH = with(density) { 320.dp.toPx() }
        val maxW = screenW * 0.95f
        val maxH = screenH * 0.90f

        var windowW by remember { mutableFloatStateOf(if (winW > 0) winW else screenW * 0.60f) }
        var windowH by remember { mutableFloatStateOf(if (winH > 0) winH else screenH * 0.55f) }
        var offsetX by remember { mutableFloatStateOf(if (winX >= 0) winX else screenW * 0.35f) }
        var offsetY by remember { mutableFloatStateOf(if (winY >= 0) winY else screenH * 0.35f) }

        fun clamp() {
            val m = with(density) { 8.dp.toPx() }
            offsetX = offsetX.coerceIn(-m, screenW - windowW + m)
            offsetY = offsetY.coerceIn(with(density) { 32.dp.toPx() }, screenH - windowH + m)
        }

        LaunchedEffect(windowW, windowH, offsetX, offsetY) { onGeometryChange(windowW, windowH, offsetX, offsetY) }

        Box(
            Modifier
                .offset { IntOffset(offsetX.toInt(), offsetY.toInt()) }
                .size(width = with(density) { windowW.toDp() }, height = with(density) { windowH.toDp() })
                .shadow(16.dp, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceLight)
                .border(0.5.dp, SeparatorColor, RoundedCornerShape(16.dp))
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().background(SurfaceWhite)
                        .pointerInput(Unit) { detectDragGestures { _, d -> offsetX += d.x; offsetY += d.y; clamp() } }
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.width(28.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(TextTertiary))
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Rounded.Code, null, Modifier.size(14.dp), tint = Blue)
                    Spacer(Modifier.width(4.dp))
                    Text("GitHub", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, modifier = Modifier.weight(1f))
                    Box(Modifier.size(26.dp).clip(CircleShape).background(Color(0xFFFF9500).copy(0.1f)).clickable { onCollapse() }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.RemoveCircleOutline, null, Modifier.size(13.dp), tint = Color(0xFFFF9500))
                    }
                    Spacer(Modifier.width(4.dp))
                    Box(Modifier.size(26.dp).clip(CircleShape).background(Blue.copy(0.1f)).clickable { onExpand() }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.OpenInFull, null, Modifier.size(13.dp), tint = Blue)
                    }
                    Spacer(Modifier.width(4.dp))
                    Box(Modifier.size(26.dp).clip(CircleShape).background(Color(0xFFFF3B30).copy(0.1f)).clickable { onClose() }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Close, null, Modifier.size(13.dp), tint = Color(0xFFFF3B30))
                    }
                }
                Box(Modifier.fillMaxWidth().height(0.5.dp).background(SeparatorColor))
                Box(Modifier.fillMaxSize().weight(1f)) {
                    GitHubScreen(onBack = { onCollapse() }, onMinimize = { onCollapse() }, compact = true)
                }
            }
            Box(Modifier.align(Alignment.BottomEnd).size(28.dp).pointerInput(Unit) { detectDragGestures { _, d -> windowW = (windowW + d.x).coerceIn(minW, maxW); windowH = (windowH + d.y).coerceIn(minH, maxH); clamp() } }.padding(4.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.DragHandle, null, Modifier.size(14.dp).graphicsLayer { rotationZ = -45f }, tint = TextTertiary)
            }
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth(0.4f).height(12.dp).pointerInput(Unit) { detectDragGestures { _, d -> windowH = (windowH + d.y).coerceIn(minH, maxH); clamp() } }, contentAlignment = Alignment.Center) {
                Box(Modifier.width(40.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(TextTertiary.copy(0.5f)))
            }
            Box(Modifier.align(Alignment.CenterEnd).width(12.dp).fillMaxHeight(0.4f).pointerInput(Unit) { detectDragGestures { _, d -> windowW = (windowW + d.x).coerceIn(minW, maxW); clamp() } }, contentAlignment = Alignment.Center) {
                Box(Modifier.width(3.dp).height(40.dp).clip(RoundedCornerShape(2.dp)).background(TextTertiary.copy(0.5f)))
            }
        }
    }
}

@Composable
private fun GitHubBubble(onExpand: () -> Unit, onClose: () -> Unit) {
    val density = LocalDensity.current
    val context = LocalContext.current
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var initialized by remember { mutableStateOf(false) }
    val avatarUrl = remember { com.glassfiles.data.github.GitHubManager.getCachedUser(context)?.avatarUrl }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val maxW = constraints.maxWidth.toFloat()
        val maxH = constraints.maxHeight.toFloat()
        val bubbleSize = with(density) { 52.dp.toPx() }

        if (!initialized) {
            offsetX = maxW - bubbleSize - with(density) { 16.dp.toPx() }
            offsetY = maxH - bubbleSize - with(density) { 120.dp.toPx() }
            initialized = true
        }

        Box(
            Modifier
                .offset { IntOffset(offsetX.toInt(), offsetY.toInt()) }
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        offsetX = (offsetX + dragAmount.x).coerceIn(0f, maxW - bubbleSize)
                        offsetY = (offsetY + dragAmount.y).coerceIn(0f, maxH - bubbleSize)
                    }
                }
                .size(52.dp)
                .shadow(8.dp, CircleShape)
                .clip(CircleShape)
                .background(SurfaceWhite)
                .border(1.dp, Blue.copy(0.3f), CircleShape)
                .clickable { onExpand() },
            contentAlignment = Alignment.Center
        ) {
            if (avatarUrl != null) coil.compose.AsyncImage(avatarUrl, "GitHub", Modifier.size(52.dp).clip(CircleShape))
            else Icon(Icons.Rounded.Code, null, Modifier.size(24.dp), tint = Blue)
        }
    }
}

private fun collectFilesRecursive(root: java.io.File, current: java.io.File, basePath: String, result: MutableList<Pair<String, ByteArray>>) {
    current.listFiles()?.forEach { f ->
        val rel = if (basePath.isNotBlank()) "$basePath/${f.relativeTo(root).path}" else f.relativeTo(root).path
        if (f.isDirectory) collectFilesRecursive(root, f, basePath, result)
        else if (f.length() < 50 * 1024 * 1024) {
            try { result.add(rel to f.readBytes()) } catch (_: Exception) {}
        }
    }
}

private fun fmtUploadSize(b: Long): String = when {
    b < 1024 -> "$b B"
    b < 1024L * 1024 -> "%.1f KB".format(b / 1024.0)
    else -> "%.1f MB".format(b / (1024.0 * 1024))
}
