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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

enum class AppScreen { MAIN, TERMINAL, SEARCH, TRASH, STORAGE, AI_CHAT, SETTINGS, DUPLICATES, QR_SCANNER, OCR, TAGGED_FILES, DEVICE_INFO, APP_MANAGER, BOOKMARKS, DIFF, NOTES, CONTENT_SEARCH, SHIZUKU, FTP }

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

    fun navigateTo(screen: AppScreen) {
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

    val tabs = listOf(TabItem(Icons.Outlined.Schedule, Strings.recents), TabItem(Icons.Outlined.People, Strings.shared), TabItem(Icons.Outlined.Folder, Strings.browse))

    fun openFileExternal(path: String) {
        try {
            val file = File(path); if (!file.exists()) return
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension) ?: "*/*"
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW).apply { setDataAndType(uri, mime); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, "Open"))
        } catch (_: Exception) {}
    }

    fun goBack() {
        val prev = previousScreen
        previousScreen = AppScreen.MAIN
        activeScreen = prev
    }

    BackHandler(enabled = true) {
        when {
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
                                            onTagClick = { tag -> selectedTagName = tag; navigateTo(AppScreen.TAGGED_FILES) })
                                    }
                                }
                            }
                        }
                    }

                    // FABs with glass effect
                    AnimatedVisibility(folderStack.isEmpty(), enter = fadeIn(tween(300)) + scaleIn(tween(300)),
                        exit = fadeOut(tween(200)) + scaleOut(tween(200)), modifier = Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize()) {
                            Box(Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 152.dp).clickable { navigateTo(AppScreen.AI_CHAT) }) {
                                GlassFab(backdrop, Icons.Rounded.AutoAwesome, iconTint = Color.White, tintColor = Color(0x66238636))
                            }
                            Box(Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 96.dp).clickable { terminalWasOpened = true; navigateTo(AppScreen.TERMINAL) }) {
                                GlassFab(backdrop, Icons.Rounded.Terminal, iconTint = Color(0xFF00E676), tintColor = Color(0x441A1A2E))
                                if (terminalWasOpened) Box(Modifier.align(Alignment.TopEnd).size(12.dp).background(Color(0xFF00E676), CircleShape))
                            }
                            Box(Modifier.align(Alignment.BottomCenter)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    GlassBottomTabBar(backdrop, selectedTab, { selectedTab = it }, tabs)
                                    Spacer(Modifier.width(8.dp))
                                    Box(Modifier.size(44.dp).background(CardBackground.copy(0.85f), CircleShape)
                                        .clickable { navigateTo(AppScreen.SETTINGS) },
                                        contentAlignment = Alignment.Center) {
                                        Icon(Icons.Rounded.Settings, null, Modifier.size(22.dp), tint = TextSecondary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
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
