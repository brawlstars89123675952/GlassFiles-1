package com.glassfiles.ui.screens

import com.glassfiles.data.Strings
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.glassfiles.data.ai.AiCostPreviewPrefs
import com.glassfiles.data.ai.AiAttachmentProcessor
import com.glassfiles.data.ai.AiKeyStore
import com.glassfiles.data.ai.AiManager
import com.glassfiles.data.ai.AiPreparedAttachment
import com.glassfiles.data.ai.ChatHistoryManager
import com.glassfiles.data.ai.ChatMessage
import com.glassfiles.data.ai.ChatSession
import com.glassfiles.data.ai.ModelRegistry
import com.glassfiles.data.ai.models.AiCapability
import com.glassfiles.data.ai.models.AiModel
import com.glassfiles.data.ai.models.AiProviderId
import com.glassfiles.data.ai.providers.AiProviders
import com.glassfiles.data.ai.usage.AiUsageAccounting
import com.glassfiles.data.ai.usage.AiUsageEstimate
import com.glassfiles.data.ai.usage.AiUsageMode
import com.glassfiles.ui.screens.ai.AiCostPreviewDialog
import com.glassfiles.ui.components.AiModuleBlinkingCursor
import com.glassfiles.ui.components.AiModuleAlertDialog
import com.glassfiles.ui.components.AiModuleCard
import com.glassfiles.ui.components.AiModuleChip
import com.glassfiles.ui.components.AiModuleCodeBlock
import com.glassfiles.ui.components.AiModuleHairline
import com.glassfiles.ui.components.AiModuleIcon
import com.glassfiles.ui.components.AiModuleIconButton
import com.glassfiles.ui.components.AiModuleListRow
import com.glassfiles.ui.components.AiModulePageBar
import com.glassfiles.ui.components.AiModulePillButton
import com.glassfiles.ui.components.AiModuleScreenScaffold
import com.glassfiles.ui.components.AiModuleSectionLabel
import com.glassfiles.ui.components.AiModuleText
import com.glassfiles.ui.theme.AiModuleSurface
import com.glassfiles.ui.theme.AiModuleTheme
import com.glassfiles.ui.theme.JetBrainsMono
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Chat / sessions screen for the AI module, terminal-themed.
 *
 * Wrapped in [AiModuleSurface] so every descendant reads the
 * same palette as the agent / hub / models screens. Outside the AI
 * module the app-level theme remains untouched — the override
 * is scoped via the surface's CompositionLocal.
 */
@Composable
fun AiChatScreen(
    onBack: () -> Unit,
    initialPrompt: String? = null,
    initialImageBase64: String? = null,
    currentFolder: String? = null,
    onRunInTerminal: ((String) -> Unit)? = null,
) {
    AiModuleSurface {
        AiChatScreenInner(
            onBack = onBack,
            initialPrompt = initialPrompt,
            initialImageBase64 = initialImageBase64,
            currentFolder = currentFolder,
            onRunInTerminal = onRunInTerminal,
        )
    }
}

@Composable
private fun AiChatScreenInner(
    onBack: () -> Unit,
    initialPrompt: String?,
    initialImageBase64: String?,
    currentFolder: String?,
    onRunInTerminal: ((String) -> Unit)?,
) {
    val context = LocalContext.current
    val historyMgr = remember { ChatHistoryManager(context) }
    var activeSessionId by remember { mutableStateOf<String?>(null) }
    var sessions by remember { mutableStateOf(historyMgr.getSessions()) }
    var consumedPrompt by remember { mutableStateOf(false) }
    var keyRefreshTick by remember { mutableIntStateOf(0) }
    val hasKey = remember(keyRefreshTick) { AiKeyStore.configuredProviders(context).isNotEmpty() }
    if (!hasKey) {
        ApiKeySetupScreen(onBack = onBack, onKeySet = { keyRefreshTick++ })
        return
    }
    LaunchedEffect(initialPrompt, initialImageBase64) {
        if (initialPrompt != null && activeSessionId == null && !consumedPrompt) {
            val s = historyMgr.createSession(loadSavedChatModelKey(context) ?: CHAT_AUTO_PROVIDER)
            historyMgr.saveSession(s)
            activeSessionId = s.id
            consumedPrompt = true
        }
    }
    fun refresh() { sessions = historyMgr.getSessions() }
    val activeId = activeSessionId
    if (activeId != null) {
        ChatView(
            sessionId = activeId,
            historyMgr = historyMgr,
            onBack = { activeSessionId = null; refresh() },
            initialPrompt = if (!consumedPrompt) initialPrompt else null,
            initialImageBase64 = if (!consumedPrompt) initialImageBase64 else null,
            currentFolder = currentFolder,
            onRunInTerminal = onRunInTerminal,
        )
    } else {
        ChatHistoryList(
            sessions = sessions,
            onNew = {
                val s = historyMgr.createSession(loadSavedChatModelKey(context) ?: CHAT_AUTO_PROVIDER)
                historyMgr.saveSession(s)
                activeSessionId = s.id
            },
            onOpen = { activeSessionId = it.id },
            onDel = { historyMgr.deleteSession(it.id); refresh() },
            onDelAll = { historyMgr.deleteAll(); refresh() },
            onBack = onBack,
        )
    }
}

// ═══════════════════════════════════
// API key setup
// ═══════════════════════════════════
@Composable
private fun ApiKeySetupScreen(onBack: () -> Unit, onKeySet: () -> Unit) {
    val context = LocalContext.current
    val keyValues = remember {
        mutableStateMapOf<AiProviderId, String>().apply {
            AiProviderId.entries.forEach { put(it, AiKeyStore.getKey(context, it)) }
        }
    }
    var proxy by remember { mutableStateOf(AiKeyStore.getGeminiProxy(context)) }
    var qwenRegion by remember { mutableStateOf(AiKeyStore.getQwenRegion(context)) }
    val colors = AiModuleTheme.colors
    AiModuleScreenScaffold(title = "AI · setup", onBack = onBack) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AiModuleText(
                "Add at least one provider key to continue.",
                color = colors.textSecondary,
                fontFamily = JetBrainsMono,
                fontSize = 13.sp,
                lineHeight = 1.4.em,
            )
            AiProviderId.entries.forEach { provider ->
                AiModuleSectionLabel("> ${provider.displayName.lowercase()}")
                TerminalMonoField(
                    value = keyValues[provider].orEmpty(),
                    onValueChange = { keyValues[provider] = it },
                    placeholder = provider.keyPlaceholder(),
                    singleLine = true,
                )
                AiModuleText(
                    provider.consoleUrl,
                    color = colors.textMuted,
                    fontFamily = JetBrainsMono,
                    fontSize = 11.sp,
                )
            }
            AiModuleHairline()
            AiModuleSectionLabel("> google proxy (optional)")
            TerminalMonoField(
                value = proxy,
                onValueChange = { proxy = it },
                placeholder = "proxy (optional)",
                singleLine = true,
            )
            AiModuleSectionLabel("> qwen region")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("intl" to "singapore", "cn" to "beijing").forEach { (code, label) ->
                    AiModulePillButton(
                        label = label,
                        onClick = { qwenRegion = code },
                        accent = qwenRegion == code,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            val ok = keyValues.values.any { it.trim().length > 10 }
            AiModulePillButton(
                label = "continue",
                onClick = {
                    AiProviderId.entries.forEach { provider ->
                        AiKeyStore.saveKey(context, provider, keyValues[provider].orEmpty())
                    }
                    AiKeyStore.saveGeminiProxy(context, proxy)
                    AiKeyStore.saveQwenRegion(context, qwenRegion)
                    onKeySet()
                },
                enabled = ok,
                accent = true,
            )
        }
    }
}

// ═══════════════════════════════════
// Sessions list
// ═══════════════════════════════════
@Composable
private fun ChatHistoryList(
    sessions: List<ChatSession>,
    onNew: () -> Unit,
    onOpen: (ChatSession) -> Unit,
    onDel: (ChatSession) -> Unit,
    onDelAll: () -> Unit,
    onBack: () -> Unit,
) {
    val sdf = remember { SimpleDateFormat("dd.MM.yy HH:mm", Locale.getDefault()) }
    var searchQ by remember { mutableStateOf("") }
    val filtered = if (searchQ.isBlank()) sessions
    else sessions.filter { it.title.contains(searchQ, true) || it.messages.any { m -> m.content.contains(searchQ, true) } }
    val colors = AiModuleTheme.colors
    AiModuleScreenScaffold(
        title = "AI · chat",
        onBack = onBack,
        subtitle = if (sessions.isNotEmpty()) "${sessions.size} session${if (sessions.size == 1) "" else "s"}" else null,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AiModulePillButton(
                    label = "new",
                    onClick = onNew,
                    leadingIcon = Icons.Rounded.Add,
                    accent = true,
                )
                if (sessions.isNotEmpty()) {
                    AiModuleIconButton(onClick = onDelAll, modifier = Modifier.size(36.dp)) {
                        AiModuleIcon(
                            Icons.Rounded.DeleteSweep,
                            contentDescription = "clear all",
                            modifier = Modifier.size(18.dp),
                            tint = colors.warning,
                        )
                    }
                }
            }
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            if (sessions.size > 2) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(colors.surface)
                        .border(1.dp, colors.border, RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AiModuleIcon(Icons.Rounded.Search, null, Modifier.size(14.dp), tint = colors.textMuted)
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.weight(1f)) {
                            BasicTextField(
                                value = searchQ,
                                onValueChange = { searchQ = it },
                                textStyle = TextStyle(
                                    color = colors.textPrimary,
                                    fontFamily = JetBrainsMono,
                                    fontSize = 13.sp,
                                ),
                                cursorBrush = SolidColor(colors.accent),
                                singleLine = true,
                            )
                            if (searchQ.isEmpty()) {
                                AiModuleText(
                                    "search chats…",
                                    color = colors.textMuted,
                                    fontFamily = JetBrainsMono,
                                    fontSize = 13.sp,
                                )
                            }
                        }
                    }
                }
            }
            if (filtered.isEmpty()) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    AiModuleText(
                        text = if (searchQ.isNotBlank()) "no matches" else "> no sessions yet",
                        color = colors.textSecondary,
                        fontFamily = JetBrainsMono,
                        fontSize = 14.sp,
                    )
                    if (searchQ.isBlank()) {
                        Spacer(Modifier.height(6.dp))
                        AiModuleText(
                            text = "tap [ new ] above to start.",
                            color = colors.textMuted,
                            fontFamily = JetBrainsMono,
                            fontSize = 12.sp,
                        )
                    }
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    items(filtered) { s ->
                        val providerId = chatProviderIdFromSession(s.provider)
                        val pc = if (providerId == AiProviderId.ALIBABA) colors.warning else colors.accent
                        val providerLabel = chatSessionLabel(s.provider)
                        AiModuleListRow(
                            title = s.title,
                            prefix = "▸",
                            prefixColor = pc,
                            subtitle = "${sdf.format(Date(s.updatedAt))}  ·  ${s.messages.size} msg" +
                                (providerLabel?.let { "  ·  $it" } ?: ""),
                            onClick = { onOpen(s) },
                            trailing = {
                                AiModuleIconButton(onClick = { onDel(s) }, modifier = Modifier.size(28.dp)) {
                                    AiModuleIcon(
                                        Icons.Rounded.Close,
                                        contentDescription = "delete",
                                        modifier = Modifier.size(14.dp),
                                        tint = colors.textMuted,
                                    )
                                }
                            },
                        )
                        AiModuleHairline(modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════
// Chat view (single conversation)
// ═══════════════════════════════════
@Composable
private fun ChatView(
    sessionId: String,
    historyMgr: ChatHistoryManager,
    onBack: () -> Unit,
    initialPrompt: String? = null,
    initialImageBase64: String? = null,
    currentFolder: String? = null,
    onRunInTerminal: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val session = remember { historyMgr.getSession(sessionId) }
    var messages by remember { mutableStateOf(session?.messages ?: emptyList()) }
    var input by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var currentResponse by remember { mutableStateOf("") }
    val sessionModelKey = remember(session?.provider) { parseChatModelKey(session?.provider) }
    var selectedProvider by remember {
        mutableStateOf(
            sessionModelKey?.providerId
                ?: loadSavedChatProvider(context, AiKeyStore.configuredProviders(context)),
        )
    }
    var modelId by remember { mutableStateOf(sessionModelKey?.modelId ?: loadSavedChatModelId(context).orEmpty()) }
    val chatModels = remember { mutableStateListOf<AiModel>() }
    var loadingModels by remember { mutableStateOf(false) }
    var modelLoadError by remember { mutableStateOf<String?>(null) }
    var keyRefreshTick by remember { mutableIntStateOf(0) }
    var showModelPicker by remember { mutableStateOf(false) }
    var attachedImage by remember { mutableStateOf<String?>(null) }
    var attachedFile by remember { mutableStateOf<AiPreparedAttachment?>(null) }
    val listState = rememberLazyListState()
    var showSettings by remember { mutableStateOf(false) }
    var autoSent by remember { mutableStateOf(false) }
    var currentJob by remember { mutableStateOf<Job?>(null) }
    var editIdx by remember { mutableIntStateOf(-1) }
    var editTxt by remember { mutableStateOf("") }
    val colors = AiModuleTheme.colors
    val configuredProviders = remember(keyRefreshTick) { AiKeyStore.configuredProviders(context) }
    val currentModel = chatModels.firstOrNull { it.providerId == selectedProvider && it.id == modelId }
    val pendingInputChars = input.length +
        (attachedFile?.promptContent?.length ?: 0) +
        ((attachedImage?.length ?: 0) / 8)
    val usageEstimate = remember(messages, currentResponse, selectedProvider, modelId, pendingInputChars) {
        AiUsageAccounting.estimate(
            providerId = selectedProvider?.name.orEmpty(),
            modelId = modelId,
            inputChars = chatInputChars(messages) + pendingInputChars,
            outputChars = chatOutputChars(messages) + currentResponse.length,
        )
    }

    // TTS
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var ttsReady by remember { mutableStateOf(false) }
    var speakingIdx by remember { mutableIntStateOf(-1) }
    DisposableEffect(Unit) {
        tts = TextToSpeech(context) { status -> ttsReady = status == TextToSpeech.SUCCESS }
        onDispose { tts?.shutdown() }
    }
    // Voice
    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (text != null) input = if (input.isBlank()) text else "$input $text"
        }
    }
    // Camera
    val cameraFile = remember { File(context.cacheDir, "ai_camera_${System.currentTimeMillis()}.jpg") }
    val cameraUri = remember { androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", cameraFile) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            attachedImage = AiManager.encodeImage(cameraFile)
            attachedFile = null
        }
    }
    // File picker
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        try {
            val mime = context.contentResolver.getType(uri) ?: ""
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            val name = cursor?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) c.getString(idx) else null
                } else null
            } ?: uri.lastPathSegment ?: "file"
            when {
                AiAttachmentProcessor.isImage(mime, name) -> {
                    context.contentResolver.openInputStream(uri)?.use { s ->
                        val f = File(context.cacheDir, "ai_img.jpg")
                        f.outputStream().use { o -> s.copyTo(o) }
                        attachedImage = AiManager.encodeImage(f)
                        attachedFile = null
                    }
                }
                else -> {
                    scope.launch {
                        val prepared = runCatching { AiAttachmentProcessor.prepare(context, uri) }
                            .getOrElse { error ->
                                AiPreparedAttachment(
                                    name = name,
                                    mimeType = mime,
                                    extension = AiAttachmentProcessor.extensionFor(name),
                                    tempPath = "",
                                    isArchive = false,
                                    promptContent = "Attached file: $name\n[read error: ${error.message ?: error.javaClass.simpleName}]",
                                    previewContent = null,
                                    summary = "$name · read error",
                                )
                            }
                        attachedFile = prepared
                        attachedImage = null
                    }
                }
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(keyRefreshTick) {
        loadingModels = true
        modelLoadError = null
        chatModels.clear()
        try {
            configuredProviders.forEach { providerId ->
                val list = ModelRegistry.getModels(
                    context = context,
                    provider = providerId,
                    apiKey = AiKeyStore.getKey(context, providerId),
                    force = false,
                )
                chatModels.addAll(list.filter { it.isChatEligible() })
            }

            val sessionMatch = sessionModelKey?.let { key ->
                chatModels.firstOrNull { it.providerId == key.providerId && it.id == key.modelId }
            }
            val savedProvider = loadSavedChatProvider(context, configuredProviders)
            val savedModelId = loadSavedChatModelId(context)
            val savedMatch = if (savedProvider != null && savedModelId != null) {
                chatModels.firstOrNull { it.providerId == savedProvider && it.id == savedModelId }
            } else {
                null
            }
            val currentMatch = selectedProvider?.let { p ->
                chatModels.firstOrNull { it.providerId == p && it.id == modelId }
            }
            val chosen = sessionMatch ?: currentMatch ?: savedMatch ?: chatModels.firstOrNull()
            selectedProvider = chosen?.providerId
            modelId = chosen?.id.orEmpty()
            chosen?.let { saveChatSelection(context, it.providerId, it.id) }
        } catch (e: Exception) {
            modelLoadError = e.message ?: e.javaClass.simpleName
        } finally {
            loadingModels = false
        }
    }

    val folderFiles = remember(currentFolder) {
        if (currentFolder != null) try {
            File(currentFolder).listFiles()
                ?.map { "${if (it.isDirectory) "[DIR]" else "[${it.extension}]"} ${it.name}" }
                ?.take(30)?.joinToString("\n")
        } catch (_: Exception) { null } else null
    }

    fun save(msgs: List<ChatMessage>) {
        historyMgr.saveSession(
            ChatSession(
                id = sessionId,
                title = historyMgr.generateTitle(msgs),
                provider = currentModel?.uniqueKey
                    ?: selectedProvider?.takeIf { modelId.isNotBlank() }?.let { providerId -> chatModelKey(providerId, modelId) }
                    ?: session?.provider
                    ?: CHAT_AUTO_PROVIDER,
                messages = msgs,
                createdAt = session?.createdAt ?: System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
            )
        )
    }
    fun clip(t: String) {
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText("ai", t))
        Toast.makeText(context, Strings.copied, Toast.LENGTH_SHORT).show()
    }
    fun exportChat() {
        val modelLabel = currentModel?.displayName
            ?: modelId.takeIf { it.isNotBlank() }
            ?: selectedProvider?.displayName
            ?: "AI"
        val sb = StringBuilder("# AI Chat\n**$modelLabel** — ${SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())}\n\n---\n\n")
        messages.forEach { m -> sb.append(if (m.role == "user") "**You:**\n" else "**AI:**\n").append(m.content).append("\n\n") }
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "GlassFiles_AI")
        dir.mkdirs()
        val f = File(dir, "chat_${System.currentTimeMillis()}.md")
        f.writeText(sb.toString())
        Toast.makeText(context, "Saved: ${f.name}", Toast.LENGTH_SHORT).show()
    }
    fun saveCode(code: String, lang: String) {
        val ext = when (lang) {
            "kotlin", "kt" -> "kt"; "java" -> "java"; "python", "py" -> "py"; "javascript", "js" -> "js"
            "typescript", "ts" -> "ts"; "html" -> "html"; "css" -> "css"; "xml" -> "xml"; "json" -> "json"
            "yaml", "yml" -> "yml"; "bash", "sh" -> "sh"; "sql" -> "sql"; "go" -> "go"; "rust", "rs" -> "rs"
            "swift" -> "swift"; "c" -> "c"; "cpp" -> "cpp"; else -> "txt"
        }
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "GlassFiles_AI")
        dir.mkdirs()
        val f = File(dir, "code_${System.currentTimeMillis()}.$ext")
        f.writeText(code)
        Toast.makeText(context, "Saved: ${f.name}", Toast.LENGTH_SHORT).show()
    }
    fun speak(text: String, idx: Int) {
        if (!ttsReady || tts == null) return
        if (speakingIdx == idx) { tts?.stop(); speakingIdx = -1; return }
        val clean = text.replace(Regex("```[\\s\\S]*?```"), "code block").replace(Regex("[*_`#>]"), "")
        tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "msg_$idx")
        speakingIdx = idx
    }
    fun voiceInput() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak...")
        }
        try { voiceLauncher.launch(intent) } catch (_: Exception) {
            Toast.makeText(context, "Voice not available", Toast.LENGTH_SHORT).show()
        }
    }
    fun recordUsage(requestProvider: AiProviderId, requestModelId: String, requestMessages: List<ChatMessage>, output: String) {
        AiUsageAccounting.appendEstimated(
            context = context,
            providerId = requestProvider.name,
            modelId = requestModelId,
            mode = AiUsageMode.CHAT,
            messages = requestMessages,
            output = output,
        )
    }

    /**
     * Pending send args captured while the pre-request cost preview
     * dialog is on screen. Non-null means a `doSend(...)` call has
     * been deferred — confirming the dialog re-invokes [actuallyDoSend]
     * with the same args, dismissing it discards the request.
     */
    var pendingSend by remember {
        mutableStateOf<PendingChatSend?>(null)
    }
    fun actuallyDoSend(text: String, image: String?, fc: String?) {
        val fullText = if (folderFiles != null && messages.isEmpty()) "Current folder: $currentFolder\nFiles:\n$folderFiles\n\nUser: $text" else text
        val um = ChatMessage("user", text, image, fc)
        currentResponse = ""
        messages = messages + um
        isLoading = true
        val msgsForApi = if (folderFiles != null && messages.size == 1) listOf(ChatMessage("user", fullText, image, fc)) else messages
        val requestProvider = selectedProvider
        val requestModelId = modelId
        val requestModel = currentModel
        currentJob = scope.launch {
            try {
                if (requestProvider == null || requestModelId.isBlank()) {
                    throw IllegalStateException("Select an API model first")
                }
                val apiKey = AiKeyStore.getKey(context, requestProvider)
                if (apiKey.isBlank()) {
                    throw IllegalStateException("Enter the ${requestProvider.displayName} API key in AI settings")
                }
                val requestMessages = safeChatMessagesForModel(msgsForApi, requestModel)
                val r = AiProviders.get(requestProvider).chat(
                    context = context,
                    modelId = requestModelId,
                    messages = requestMessages,
                    apiKey = apiKey,
                ) { currentResponse += it }
                recordUsage(requestProvider, requestModelId, requestMessages, r)
                messages = messages + ChatMessage("assistant", r)
                currentResponse = ""
                save(messages)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException && currentResponse.isNotBlank()) {
                    val partial = currentResponse
                    requestProvider?.takeIf { requestModelId.isNotBlank() }?.let {
                        recordUsage(it, requestModelId, msgsForApi, partial)
                    }
                    messages = messages + ChatMessage("assistant", partial + "\n\n*(stopped)*")
                    currentResponse = ""
                } else {
                    messages = messages + ChatMessage("assistant", "Error: ${e.message}")
                    currentResponse = ""
                }
                save(messages)
            }
            isLoading = false
            currentJob = null
        }
    }
    fun doSend(text: String, image: String? = null, fc: String? = null) {
        if (isLoading) return
        // Pre-request cost preview: if the user enabled the feature
        // (default on) and the projected cost is above their threshold
        // (default $0.10), park the args and let the confirm dialog
        // decide whether to actually fire the request. The estimate
        // re-uses the same `usageEstimate` rendered in the top-bar
        // chip, so the dialog and the chip always agree.
        val cost = usageEstimate.costUsd
        if (cost != null && AiCostPreviewPrefs.shouldPreview(context, cost)) {
            pendingSend = PendingChatSend(
                text = text,
                image = image,
                fileContent = fc,
                estimatedCostUsd = cost,
                estimatedTokens = usageEstimate.totalTokens,
            )
            return
        }
        actuallyDoSend(text, image, fc)
    }
    fun send() {
        var t = input.trim()
        if (t.isEmpty() && attachedImage == null && attachedFile == null) return
        val file = attachedFile
        val fc = file?.promptContent
        if (t.isEmpty() && attachedImage != null) t = "What is in this image?"
        if (t.isEmpty() && fc != null) t = if (file?.isArchive == true) "Analyze this archive" else "Analyze this file"
        input = ""
        val img = attachedImage
        val fcc = fc
        attachedImage = null
        attachedFile = null
        doSend(t, img, fcc)
    }
    fun regenerate() {
        if (messages.isEmpty() || isLoading) return
        val m = messages.toMutableList()
        if (m.last().role == "assistant") m.removeLast()
        messages = m
        isLoading = true
        currentResponse = ""
        val requestProvider = selectedProvider
        val requestModelId = modelId
        val requestModel = currentModel
        val requestMessages = messages
        currentJob = scope.launch {
            try {
                if (requestProvider == null || requestModelId.isBlank()) {
                    throw IllegalStateException("Select an API model first")
                }
                val apiKey = AiKeyStore.getKey(context, requestProvider)
                if (apiKey.isBlank()) {
                    throw IllegalStateException("Enter the ${requestProvider.displayName} API key in AI settings")
                }
                val apiMessages = safeChatMessagesForModel(requestMessages, requestModel)
                val r = AiProviders.get(requestProvider).chat(
                    context = context,
                    modelId = requestModelId,
                    messages = apiMessages,
                    apiKey = apiKey,
                ) { currentResponse += it }
                recordUsage(requestProvider, requestModelId, apiMessages, r)
                messages = messages + ChatMessage("assistant", r)
                currentResponse = ""
                save(messages)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException && currentResponse.isNotBlank()) {
                    val partial = currentResponse
                    requestProvider?.takeIf { requestModelId.isNotBlank() }?.let {
                        recordUsage(it, requestModelId, requestMessages, partial)
                    }
                    messages = messages + ChatMessage("assistant", partial + "\n\n*(stopped)*")
                } else {
                    messages = messages + ChatMessage("assistant", "Error: ${e.message}")
                }
                currentResponse = ""
                save(messages)
            }
            isLoading = false
            currentJob = null
        }
    }
    fun quickAction(action: String) {
        val lastAi = messages.lastOrNull { it.role == "assistant" }?.content ?: return
        val prompt = when (action) {
            "shorter" -> "Make this shorter:\n$lastAi"
            "detail" -> "Explain in more detail:\n$lastAi"
            "translate_ru" -> "Translate to Russian:\n$lastAi"
            "translate_en" -> "Translate to English:\n$lastAi"
            "script" -> "Convert this into a bash script that can be run in terminal:\n$lastAi"
            "fix" -> "Fix any errors in this code:\n$lastAi"
            "explain" -> "Explain this code step by step:\n$lastAi"
            else -> action
        }
        doSend(prompt)
    }
    fun confirmEdit() {
        if (editIdx < 0) return
        val nm = messages.take(editIdx) + ChatMessage("user", editTxt, messages[editIdx].imageBase64, messages[editIdx].fileContent)
        messages = nm
        editIdx = -1
        editTxt = ""
        isLoading = true
        currentResponse = ""
        val requestProvider = selectedProvider
        val requestModelId = modelId
        val requestModel = currentModel
        val requestMessages = messages
        currentJob = scope.launch {
            try {
                if (requestProvider == null || requestModelId.isBlank()) {
                    throw IllegalStateException("Select an API model first")
                }
                val apiKey = AiKeyStore.getKey(context, requestProvider)
                if (apiKey.isBlank()) {
                    throw IllegalStateException("Enter the ${requestProvider.displayName} API key in AI settings")
                }
                val apiMessages = safeChatMessagesForModel(requestMessages, requestModel)
                val r = AiProviders.get(requestProvider).chat(
                    context = context,
                    modelId = requestModelId,
                    messages = apiMessages,
                    apiKey = apiKey,
                ) { currentResponse += it }
                recordUsage(requestProvider, requestModelId, apiMessages, r)
                messages = messages + ChatMessage("assistant", r)
                currentResponse = ""
                save(messages)
            } catch (e: Exception) {
                messages = messages + ChatMessage("assistant", "Error: ${e.message}")
                currentResponse = ""
                save(messages)
            }
            isLoading = false
            currentJob = null
        }
    }

    LaunchedEffect(messages.size, currentResponse) {
        val total = messages.size + if (currentResponse.isNotEmpty()) 1 else 0
        if (total > 0) listState.animateScrollToItem(total - 1)
    }
    LaunchedEffect(initialPrompt) {
        if (initialPrompt != null && !autoSent && messages.isEmpty()) {
            autoSent = true
            doSend(initialPrompt, initialImageBase64, null)
        }
    }

    if (editIdx >= 0) {
        AiModuleAlertDialog(
            onDismissRequest = { editIdx = -1 },
            title = "edit message",
            confirmButton = {
                AiModulePillButton(label = "send", onClick = { confirmEdit() }, accent = true)
            },
            dismissButton = {
                AiModulePillButton(label = "cancel", onClick = { editIdx = -1 }, accent = false)
            },
        ) {
                TerminalMonoField(
                    value = editTxt,
                    onValueChange = { editTxt = it },
                    placeholder = "edit…",
                    singleLine = false,
                    minHeight = 90.dp,
                )
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .imePadding(),
    ) {
        // Topbar
        AiModulePageBar(
            title = "AI · chat",
            onBack = { save(messages); onBack() },
            subtitle = currentFolder?.let { "ctx: ${it.substringAfterLast("/")}" },
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .border(1.dp, colors.border, RoundedCornerShape(6.dp))
                            .clickable { showModelPicker = true }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        val pc = if (selectedProvider == AiProviderId.ALIBABA) colors.warning else colors.accent
                        Box(Modifier.size(6.dp).clip(RoundedCornerShape(50)).background(pc))
                        AiModuleText(
                            when {
                                loadingModels -> "loading"
                                modelLoadError != null -> "model err"
                                currentModel != null -> currentModel.displayName
                                modelId.isNotBlank() -> modelId
                                else -> "model"
                            },
                            color = colors.textPrimary,
                            fontFamily = JetBrainsMono,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 138.dp),
                        )
                        AiModuleIcon(
                            Icons.Rounded.KeyboardArrowDown,
                            null,
                            Modifier.size(12.dp),
                            tint = colors.textMuted,
                        )
                    }
                    AiChatUsageChip(usageEstimate)
                    AiModuleIconButton(onClick = { exportChat() }, modifier = Modifier.size(32.dp)) {
                        AiModuleIcon(
                            Icons.Rounded.FileDownload,
                            null,
                            Modifier.size(16.dp),
                            tint = colors.textSecondary,
                        )
                    }
                    AiModuleIconButton(onClick = { showSettings = true }, modifier = Modifier.size(32.dp)) {
                        AiModuleIcon(
                            Icons.Rounded.Settings,
                            null,
                            Modifier.size(16.dp),
                            tint = colors.textSecondary,
                        )
                    }
                }
            },
        )

        // Messages
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (messages.isEmpty()) {
                item {
                    EmptyChatBanner(
                        model = currentModel,
                        provider = selectedProvider,
                        modelLoadError = modelLoadError,
                        currentFolder = currentFolder,
                        onSuggestion = { input = it },
                    )
                }
            }
            items(messages.size) { idx ->
                val msg = messages[idx]
                val isLast = idx == messages.lastIndex
                TerminalChatMessage(
                    msg = msg,
                    msgIdx = idx,
                    speakingIdx = speakingIdx,
                    onCopy = { clip(msg.content) },
                    onEdit = if (msg.role == "user") {
                        { editIdx = idx; editTxt = msg.content }
                    } else null,
                    onDelete = {
                        val m = messages.toMutableList(); m.removeAt(idx); messages = m; save(messages)
                    },
                    onSaveCode = { c, l -> saveCode(c, l) },
                    onRegenerate = if (isLast && msg.role == "assistant" && !isLoading) {
                        { regenerate() }
                    } else null,
                    onSpeak = { speak(msg.content, idx) },
                    onRunScript = if (msg.role == "assistant" && onRunInTerminal != null) {
                        { onRunInTerminal(msg.content) }
                    } else null,
                )
            }
            if (currentResponse.isNotEmpty()) {
                item {
                    TerminalChatMessage(
                        msg = ChatMessage("assistant", currentResponse),
                        msgIdx = -1,
                        speakingIdx = speakingIdx,
                        streaming = true,
                        onCopy = {},
                        onEdit = null,
                        onDelete = {},
                        onSaveCode = { _, _ -> },
                        onRegenerate = null,
                        onSpeak = {},
                        onRunScript = null,
                    )
                }
            }
            if (isLoading && currentResponse.isEmpty()) {
                item {
                    Row(
                        Modifier.padding(start = 24.dp, top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AiModuleText(
                            "⠋ thinking...",
                            color = colors.textMuted,
                            fontFamily = JetBrainsMono,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
            if (!isLoading && messages.isNotEmpty() && messages.last().role == "assistant") {
                item {
                    LazyRow(
                        Modifier.fillMaxWidth().padding(start = 24.dp, top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        val actions = mutableListOf(
                            "shorter" to "shorter",
                            "detail" to "detail",
                            "translate_ru" to "ru",
                            "translate_en" to "en",
                            "explain" to "explain",
                            "fix" to "fix",
                        )
                        if (onRunInTerminal != null) actions.add("script" to "script")
                        items(actions) { (key, label) ->
                            AiModulePillButton(
                                label = label,
                                onClick = { quickAction(key) },
                                accent = false,
                            )
                        }
                    }
                }
            }
        }

        // Attachments banner
        if (attachedImage != null || attachedFile != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(colors.surface)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (attachedImage != null) {
                    AiModuleIcon(Icons.Rounded.AddPhotoAlternate, null, Modifier.size(14.dp), tint = colors.accent)
                    AiModuleText(
                        "image attached",
                        color = colors.textSecondary,
                        fontFamily = JetBrainsMono,
                        fontSize = 11.sp,
                        modifier = Modifier.weight(1f),
                    )
                    AiModuleIconButton(onClick = { attachedImage = null }, modifier = Modifier.size(20.dp)) {
                        AiModuleIcon(Icons.Rounded.Close, null, Modifier.size(12.dp), tint = colors.textMuted)
                    }
                }
                if (attachedFile != null) {
                    AiModuleIcon(
                        if (attachedFile!!.isArchive) Icons.Rounded.FolderZip else Icons.Rounded.Description,
                        null,
                        Modifier.size(14.dp),
                        tint = if (attachedFile!!.isArchive) colors.warning else colors.accent,
                    )
                    Column(Modifier.weight(1f)) {
                        AiModuleText(
                            attachedFile!!.name,
                            color = colors.textSecondary,
                            fontFamily = JetBrainsMono,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        AiModuleText(
                            attachedFile!!.summary,
                            color = colors.textMuted,
                            fontFamily = JetBrainsMono,
                            fontSize = 10.sp,
                            maxLines = 2,
                        )
                    }
                    AiModuleIconButton(
                        onClick = { attachedFile = null },
                        modifier = Modifier.size(20.dp),
                    ) {
                        AiModuleIcon(Icons.Rounded.Close, null, Modifier.size(12.dp), tint = colors.textMuted)
                    }
                }
            }
        }

        // Input
        TerminalChatInput(
            value = input,
            onValueChange = { input = it },
            onSend = ::send,
            onPickFile = { filePicker.launch("*/*") },
            onPickCamera = {
                try { cameraLauncher.launch(cameraUri) } catch (_: Exception) {
                    Toast.makeText(context, "Camera not available", Toast.LENGTH_SHORT).show()
                }
            },
            onVoice = ::voiceInput,
            onStop = { currentJob?.cancel(); currentJob = null },
            isLoading = isLoading,
            canSend = input.isNotBlank() || attachedImage != null || attachedFile != null,
        )
    }

    if (showModelPicker) {
        TerminalModelPicker(
            current = currentModel,
            models = chatModels,
            loading = loadingModels,
            error = modelLoadError,
            configuredProviders = configuredProviders,
            onSelect = {
                selectedProvider = it.providerId
                modelId = it.id
                saveChatSelection(context, it.providerId, it.id)
                showModelPicker = false
            },
            onDismiss = { showModelPicker = false },
        )
    }

    if (showSettings) {
        TerminalKeysDialog(
            initialKeys = AiProviderId.entries.associateWith { AiKeyStore.getKey(context, it) },
            initialProxy = AiKeyStore.getGeminiProxy(context),
            initialRegion = AiKeyStore.getQwenRegion(context),
            onSave = { keys, proxyUrl, region ->
                keys.forEach { (providerId, key) -> AiKeyStore.saveKey(context, providerId, key) }
                AiKeyStore.saveGeminiProxy(context, proxyUrl)
                AiKeyStore.saveQwenRegion(context, region)
                keyRefreshTick++
                showSettings = false
                Toast.makeText(context, Strings.done, Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showSettings = false },
        )
    }

    pendingSend?.let { pending ->
        AiCostPreviewDialog(
            estimatedCostUsd = pending.estimatedCostUsd,
            estimatedTokens = pending.estimatedTokens,
            thresholdUsd = AiCostPreviewPrefs.getThresholdUsd(context),
            onConfirm = {
                pendingSend = null
                actuallyDoSend(pending.text, pending.image, pending.fileContent)
            },
            onDismiss = { pendingSend = null },
        )
    }
}

/**
 * Args captured while the pre-request cost preview dialog is on
 * screen. See [AiChatScreen]'s `doSend` / `actuallyDoSend` split.
 */
private data class PendingChatSend(
    val text: String,
    val image: String?,
    val fileContent: String?,
    val estimatedCostUsd: Double,
    val estimatedTokens: Int,
)

private const val CHAT_AUTO_PROVIDER = "AUTO"
private const val CHAT_PREFS = "ai_chat_screen"
private const val CHAT_PREF_PROVIDER = "provider"
private const val CHAT_PREF_MODEL = "model"

private data class ChatModelKey(
    val providerId: AiProviderId,
    val modelId: String,
)

private fun AiModel.isChatEligible(): Boolean =
    !deprecated && (
        AiCapability.TEXT in capabilities ||
            AiCapability.CODING in capabilities ||
            AiCapability.REASONING in capabilities ||
            AiCapability.VISION in capabilities
        )

private fun safeChatMessagesForModel(messages: List<ChatMessage>, model: AiModel?): List<ChatMessage> {
    if (model == null || AiCapability.VISION in model.capabilities) return messages
    return messages.map { message ->
        if (message.imageBase64 == null) message else message.copy(imageBase64 = null)
    }
}

private fun chatModelKey(providerId: AiProviderId, modelId: String): String = "${providerId.name}:$modelId"

private fun parseChatModelKey(raw: String?): ChatModelKey? {
    val value = raw?.takeIf { it.isNotBlank() && it != CHAT_AUTO_PROVIDER } ?: return null
    val parts = value.split(":", limit = 2)
    if (parts.size == 2) {
        val providerId = runCatching { AiProviderId.valueOf(parts[0]) }.getOrNull()
        if (providerId != null && parts[1].isNotBlank()) return ChatModelKey(providerId, parts[1])
    }
    return when {
        value.startsWith("GEMINI") -> ChatModelKey(AiProviderId.GOOGLE, legacyChatModelId(value))
        value.startsWith("QWEN") || value.startsWith("QWQ") -> ChatModelKey(AiProviderId.ALIBABA, legacyChatModelId(value))
        else -> null
    }
}

private fun legacyChatModelId(value: String): String =
    when (value) {
        "GEMINI_FLASH" -> "gemini-2.5-flash"
        "GEMINI_PRO" -> "gemini-2.5-pro"
        "GEMINI_FLASH_LITE" -> "gemini-2.5-flash-lite"
        "GEMINI_3_FLASH" -> "gemini-3-flash-preview"
        "GEMINI_31_PRO" -> "gemini-3.1-pro-preview"
        "QWEN3_MAX" -> "qwen3-max"
        "QWEN35_PLUS" -> "qwen3.5-plus"
        "QWEN_PLUS" -> "qwen-plus"
        "QWEN35_FLASH" -> "qwen3.5-flash"
        "QWEN_FLASH" -> "qwen-flash"
        "QWEN_TURBO" -> "qwen-turbo"
        "QWEN_LONG" -> "qwen-long"
        "QWEN3_CODER_PLUS" -> "qwen3-coder-plus"
        "QWEN3_CODER_FLASH" -> "qwen3-coder-flash"
        "QWEN3_VL_PLUS" -> "qwen3-vl-plus"
        "QWEN3_VL_FLASH" -> "qwen3-vl-flash"
        "QWEN_VL_PLUS" -> "qwen-vl-plus"
        "QWEN_VL_MAX" -> "qwen-vl-max"
        "QWEN_OCR" -> "qwen-ocr-latest"
        "QWQ_PLUS" -> "qwq-plus"
        "QWEN_MATH_PLUS" -> "qwen-math-plus"
        "QWEN_MATH_TURBO" -> "qwen-math-turbo"
        "QWEN3_235B" -> "qwen3-235b-a22b"
        "QWEN3_32B" -> "qwen3-32b"
        "QWEN3_14B" -> "qwen3-14b"
        "QWEN3_8B" -> "qwen3-8b"
        "QWEN3_4B" -> "qwen3-4b"
        "QWEN25_72B" -> "qwen2.5-72b-instruct"
        "QWEN25_32B" -> "qwen2.5-32b-instruct"
        else -> value.lowercase().replace('_', '-')
    }

private fun chatProviderIdFromSession(raw: String): AiProviderId? =
    parseChatModelKey(raw)?.providerId ?: runCatching { AiProviderId.valueOf(raw) }.getOrNull()

private fun chatSessionLabel(raw: String): String? {
    val key = parseChatModelKey(raw)
    if (key != null) return "${key.providerId.displayName} · ${key.modelId}"
    return runCatching { AiProviderId.valueOf(raw).displayName }.getOrNull()
}

private fun loadSavedChatProvider(context: Context, configured: List<AiProviderId>): AiProviderId? {
    val saved = context.getSharedPreferences(CHAT_PREFS, Context.MODE_PRIVATE)
        .getString(CHAT_PREF_PROVIDER, null)
        ?.let { runCatching { AiProviderId.valueOf(it) }.getOrNull() }
    return saved?.takeIf { it in configured } ?: configured.firstOrNull()
}

private fun loadSavedChatModelId(context: Context): String? =
    context.getSharedPreferences(CHAT_PREFS, Context.MODE_PRIVATE)
        .getString(CHAT_PREF_MODEL, null)
        ?.takeIf { it.isNotBlank() }

private fun loadSavedChatModelKey(context: Context): String? {
    val configured = AiKeyStore.configuredProviders(context)
    val provider = loadSavedChatProvider(context, configured) ?: return null
    val model = loadSavedChatModelId(context) ?: return null
    return chatModelKey(provider, model)
}

private fun saveChatSelection(context: Context, providerId: AiProviderId, modelId: String) {
    context.getSharedPreferences(CHAT_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(CHAT_PREF_PROVIDER, providerId.name)
        .putString(CHAT_PREF_MODEL, modelId)
        .apply()
}

private fun AiProviderId.keyPlaceholder(): String =
    when (this) {
        AiProviderId.GOOGLE -> "AIza..."
        AiProviderId.ALIBABA -> "sk-..."
        else -> "api key"
    }

@Composable
private fun AiChatUsageChip(estimate: AiUsageEstimate) {
    val colors = AiModuleTheme.colors
    val text = buildString {
        append(AiUsageAccounting.formatTokens(estimate.totalTokens, estimated = estimate.estimated))
        append(" tok")
        estimate.costUsd?.let {
            append(" · ")
            append(AiUsageAccounting.formatUsd(it, estimated = estimate.estimated))
        }
    }
    AiModuleText(
        text = text,
        color = colors.textMuted,
        fontFamily = JetBrainsMono,
        fontSize = 11.sp,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .border(1.dp, colors.border, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 4.dp),
    )
}

private fun chatInputChars(messages: List<ChatMessage>): Int =
    messages.sumOf { message ->
        if (message.role == "assistant") {
            0
        } else {
            message.content.length +
                (message.fileContent?.length ?: 0) +
                ((message.imageBase64?.length ?: 0) / 8)
        }
    }

private fun chatOutputChars(messages: List<ChatMessage>): Int =
    messages.sumOf { message -> if (message.role == "assistant") message.content.length else 0 }

// ═══════════════════════════════════
// Empty banner
// ═══════════════════════════════════
@Composable
private fun EmptyChatBanner(
    model: AiModel?,
    provider: AiProviderId?,
    modelLoadError: String?,
    currentFolder: String?,
    onSuggestion: (String) -> Unit,
) {
    val colors = AiModuleTheme.colors
    val providerColor = if (provider == AiProviderId.ALIBABA) colors.warning else colors.accent
    val providerName = provider?.name?.lowercase() ?: "api"
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 24.dp, start = 4.dp, end = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AiModuleText(
            "> ai/$providerName ready",
            color = providerColor,
            fontFamily = JetBrainsMono,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
        AiModuleText(
            when {
                modelLoadError != null -> "model load failed: $modelLoadError"
                model != null -> "${model.providerId.displayName} · ${model.displayName}"
                provider != null -> "Select a chat-capable API model."
                else -> "Add an API key and select a chat-capable model."
            },
            color = colors.textMuted,
            fontFamily = JetBrainsMono,
            fontSize = 12.sp,
            lineHeight = 1.4.em,
        )
        if (currentFolder != null) {
            AiModuleText(
                "context: ${currentFolder.substringAfterLast("/")}",
                color = colors.textSecondary,
                fontFamily = JetBrainsMono,
                fontSize = 11.sp,
            )
        }
        Spacer(Modifier.height(8.dp))
        AiModuleSectionLabel("> suggested")
        val suggestions = mutableListOf("Explain this code", "Analyze ZIP archive", "What's in this image?")
        if (currentFolder != null) {
            suggestions.add(0, "What files are in this folder?")
            suggestions.add(1, "What takes the most space here?")
        }
        suggestions.take(5).forEach { q ->
            AiModuleListRow(
                title = q,
                prefix = "▸",
                onClick = { onSuggestion(q) },
                paddingVertical = 8.dp,
            )
        }
    }
}

// ═══════════════════════════════════
// Message rendering
// ═══════════════════════════════════
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TerminalChatMessage(
    msg: ChatMessage,
    msgIdx: Int,
    speakingIdx: Int,
    streaming: Boolean = false,
    onCopy: () -> Unit,
    onEdit: (() -> Unit)?,
    onDelete: () -> Unit,
    onSaveCode: (String, String) -> Unit,
    onRegenerate: (() -> Unit)?,
    onSpeak: () -> Unit,
    onRunScript: (() -> Unit)?,
) {
    val isUser = msg.role == "user"
    val colors = AiModuleTheme.colors
    val context = LocalContext.current
    val glyph = if (isUser) ">" else "\u25A0" // ■
    val glyphColor = colors.accent

    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.width(20.dp).padding(top = 2.dp), contentAlignment = Alignment.TopStart) {
            AiModuleText(
                glyph,
                color = glyphColor,
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                lineHeight = 1.45.em,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(
            Modifier.weight(1f).combinedClickable(onClick = {}, onLongClick = onCopy),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (msg.imageBase64 != null) {
                val bmp = remember(msg.imageBase64) {
                    try {
                        val b = android.util.Base64.decode(msg.imageBase64, android.util.Base64.NO_WRAP)
                        android.graphics.BitmapFactory.decodeByteArray(b, 0, b.size)
                    } catch (_: Exception) { null }
                }
                if (bmp != null) {
                    androidx.compose.foundation.Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .widthIn(max = 240.dp)
                            .heightIn(max = 180.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, colors.border, RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
            if (msg.fileContent != null && isUser) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(colors.surface)
                        .border(1.dp, colors.border, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AiModuleIcon(Icons.Rounded.AttachFile, null, Modifier.size(10.dp), tint = colors.textMuted)
                    AiModuleText(
                        "file attached",
                        color = colors.textMuted,
                        fontFamily = JetBrainsMono,
                        fontSize = 10.sp,
                    )
                }
            }

            val content = msg.content
            if (!isUser && content.contains("```")) {
                val parts = content.split("```")
                parts.forEachIndexed { i, part ->
                    if (i % 2 == 0) {
                        if (part.isNotBlank()) {
                            TerminalMarkdownText(part.trim())
                        }
                    } else {
                        val lines = part.lines()
                        val lang = if (lines.isNotEmpty() && lines.first().matches(Regex("^[a-zA-Z0-9_+#.-]+$"))) lines.first().lowercase() else ""
                        val code = if (lang.isNotBlank()) lines.drop(1).joinToString("\n") else part
                        Box(
                            Modifier.combinedClickable(
                                onClick = {},
                                onLongClick = {
                                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                                        .setPrimaryClip(ClipData.newPlainText("code", code))
                                    Toast.makeText(context, Strings.copied, Toast.LENGTH_SHORT).show()
                                },
                            ),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                AiModuleCodeBlock(
                                    text = code.trimEnd(),
                                    lang = lang,
                                    context = context,
                                )
                                AiModulePillButton(
                                    label = "save",
                                    onClick = { onSaveCode(code.trimEnd(), lang) },
                                    accent = false,
                                )
                            }
                        }
                    }
                }
            } else if (!isUser) {
                TerminalMarkdownText(content)
            } else {
                AiModuleText(
                    text = content,
                    color = colors.accent,
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    lineHeight = 1.45.em,
                )
            }

            if (streaming) {
                AiModuleBlinkingCursor()
            }

            // Action row
            Row(
                Modifier
                    .padding(top = 2.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                MsgActionButton("copy", onCopy)
                if (onEdit != null) MsgActionButton("edit", onEdit)
                MsgActionButton("delete", onDelete, destructive = true)
                if (!isUser) {
                    MsgActionButton(if (speakingIdx == msgIdx) "stop" else "speak", onSpeak)
                }
                if (onRegenerate != null) MsgActionButton("regen", onRegenerate)
                if (onRunScript != null) MsgActionButton("term", onRunScript)
            }
        }
    }
}

@Composable
private fun MsgActionButton(label: String, onClick: () -> Unit, destructive: Boolean = false) {
    val colors = AiModuleTheme.colors
    val tint = if (destructive) colors.error else colors.textMuted
    Box(
        Modifier
            .clip(RoundedCornerShape(4.dp))
            .border(1.dp, colors.border, RoundedCornerShape(4.dp))
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 3.dp),
    ) {
        AiModuleText(
            text = "[ $label ]",
            color = tint,
            fontFamily = JetBrainsMono,
            fontSize = 10.sp,
            maxLines = 1,
        )
    }
}

// ═══════════════════════════════════
// Markdown
// ═══════════════════════════════════
@Composable
private fun TerminalMarkdownText(text: String) {
    val colors = AiModuleTheme.colors
    val lines = text.lines()
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for (l in lines) {
            when {
                l.startsWith("### ") -> AiModuleText(
                    l.removePrefix("### "),
                    color = colors.textPrimary,
                    fontFamily = JetBrainsMono,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 1.35.em,
                )
                l.startsWith("## ") -> AiModuleText(
                    l.removePrefix("## "),
                    color = colors.textPrimary,
                    fontFamily = JetBrainsMono,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 1.35.em,
                )
                l.startsWith("# ") -> AiModuleText(
                    l.removePrefix("# "),
                    color = colors.textPrimary,
                    fontFamily = JetBrainsMono,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 1.35.em,
                )
                l.startsWith("- ") || l.startsWith("* ") -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AiModuleText(
                        "•",
                        color = colors.accent,
                        fontFamily = JetBrainsMono,
                        fontSize = 14.sp,
                    )
                    AiModuleText(
                        text = inlineMd(l.drop(2), colors.textPrimary, colors.error, colors.surface),
                        fontFamily = JetBrainsMono,
                        fontSize = 14.sp,
                        lineHeight = 1.45.em,
                    )
                }
                l.matches(Regex("^\\d+\\.\\s.*")) -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AiModuleText(
                        l.substringBefore(".") + ".",
                        color = colors.accent,
                        fontFamily = JetBrainsMono,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    AiModuleText(
                        text = inlineMd(l.substringAfter(". "), colors.textPrimary, colors.error, colors.surface),
                        fontFamily = JetBrainsMono,
                        fontSize = 14.sp,
                        lineHeight = 1.45.em,
                    )
                }
                l.startsWith("> ") -> Row {
                    Box(Modifier.width(2.dp).height(20.dp).background(colors.accentDim))
                    Spacer(Modifier.width(8.dp))
                    AiModuleText(
                        text = inlineMd(l.removePrefix("> "), colors.textSecondary, colors.error, colors.surface),
                        fontFamily = JetBrainsMono,
                        fontSize = 13.sp,
                        fontStyle = FontStyle.Italic,
                        lineHeight = 1.45.em,
                    )
                }
                l.isBlank() -> Spacer(Modifier.height(4.dp))
                else -> AiModuleText(
                    text = inlineMd(l, colors.textPrimary, colors.error, colors.surface),
                    fontFamily = JetBrainsMono,
                    fontSize = 14.sp,
                    lineHeight = 1.45.em,
                )
            }
        }
    }
}

private fun inlineMd(t: String, primary: Color, codeFg: Color, codeBg: Color): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < t.length) {
        when {
            i < t.length - 1 && t[i] == '*' && t[i + 1] == '*' -> {
                val e = t.indexOf("**", i + 2)
                if (e > 0) {
                    withStyle(SpanStyle(color = primary, fontWeight = FontWeight.Bold)) { append(t.substring(i + 2, e)) }
                    i = e + 2
                } else {
                    withStyle(SpanStyle(color = primary)) { append(t[i].toString()) }; i++
                }
            }
            t[i] == '*' -> {
                val e = t.indexOf('*', i + 1)
                if (e > 0) {
                    withStyle(SpanStyle(color = primary, fontStyle = FontStyle.Italic)) { append(t.substring(i + 1, e)) }
                    i = e + 1
                } else {
                    withStyle(SpanStyle(color = primary)) { append(t[i].toString()) }; i++
                }
            }
            t[i] == '`' -> {
                val e = t.indexOf('`', i + 1)
                if (e > 0) {
                    withStyle(SpanStyle(color = codeFg, fontFamily = JetBrainsMono, background = codeBg)) {
                        append(t.substring(i + 1, e))
                    }
                    i = e + 1
                } else {
                    withStyle(SpanStyle(color = primary)) { append(t[i].toString()) }; i++
                }
            }
            else -> {
                withStyle(SpanStyle(color = primary)) { append(t[i].toString()) }; i++
            }
        }
    }
}

// ═══════════════════════════════════
// Input bar
// ═══════════════════════════════════
@Composable
private fun TerminalChatInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onPickFile: () -> Unit,
    onPickCamera: () -> Unit,
    onVoice: () -> Unit,
    onStop: () -> Unit,
    isLoading: Boolean,
    canSend: Boolean,
) {
    val colors = AiModuleTheme.colors
    Column(Modifier.fillMaxWidth().background(colors.background)) {
        AiModuleHairline()
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            AiModuleIconButton(onClick = onPickFile, modifier = Modifier.size(36.dp)) {
                AiModuleIcon(Icons.Rounded.AttachFile, null, Modifier.size(16.dp), tint = colors.textSecondary)
            }
            AiModuleIconButton(onClick = onPickCamera, modifier = Modifier.size(36.dp)) {
                AiModuleIcon(Icons.Rounded.CameraAlt, null, Modifier.size(16.dp), tint = colors.textSecondary)
            }
            AiModuleIconButton(onClick = onVoice, modifier = Modifier.size(36.dp)) {
                AiModuleIcon(Icons.Rounded.Mic, null, Modifier.size(16.dp), tint = colors.textSecondary)
            }
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.border, RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    AiModuleText(
                        ">",
                        color = colors.accent,
                        fontFamily = JetBrainsMono,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        lineHeight = 1.4.em,
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f)) {
                        BasicTextField(
                            value = value,
                            onValueChange = onValueChange,
                            textStyle = TextStyle(
                                color = colors.accent,
                                fontFamily = JetBrainsMono,
                                fontSize = 14.sp,
                                lineHeight = 1.4.em,
                            ),
                            cursorBrush = SolidColor(colors.accent),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 22.dp, max = 140.dp),
                        )
                        if (value.isEmpty()) {
                            AiModuleText(
                                "message…",
                                color = colors.textMuted,
                                fontFamily = JetBrainsMono,
                                fontSize = 14.sp,
                            )
                        }
                    }
                }
            }
            if (isLoading) {
                AiModuleIconButton(onClick = onStop, modifier = Modifier.size(36.dp)) {
                    AiModuleIcon(Icons.Rounded.Stop, null, Modifier.size(18.dp), tint = colors.warning)
                }
            } else {
                AiModuleIconButton(
                    onClick = { if (canSend) onSend() },
                    enabled = canSend,
                    modifier = Modifier.size(36.dp),
                ) {
                    AiModuleIcon(
                        Icons.AutoMirrored.Rounded.Send,
                        null,
                        Modifier.size(16.dp),
                        tint = if (canSend) colors.accent else colors.textMuted,
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════
// Model picker dialog
// ═══════════════════════════════════
@Composable
private fun TerminalModelPicker(
    current: AiModel?,
    models: List<AiModel>,
    loading: Boolean,
    error: String?,
    configuredProviders: List<AiProviderId>,
    onSelect: (AiModel) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = AiModuleTheme.colors
    AiModuleAlertDialog(
        onDismissRequest = onDismiss,
        title = "select api model",
        confirmButton = {
            AiModulePillButton(label = "close", onClick = onDismiss, accent = false)
        },
    ) {
            LazyColumn(
                Modifier.heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                when {
                    loading -> item {
                        AiModuleText(
                            "loading model catalog...",
                            color = colors.textMuted,
                            fontFamily = JetBrainsMono,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                    error != null -> item {
                        AiModuleText(
                            "model load failed: $error",
                            color = colors.error,
                            fontFamily = JetBrainsMono,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                    configuredProviders.isEmpty() -> item {
                        AiModuleText(
                            "add an API key in [ settings ]",
                            color = colors.textMuted,
                            fontFamily = JetBrainsMono,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                    models.isEmpty() -> item {
                        AiModuleText(
                            "no chat-capable models found for configured providers",
                            color = colors.textMuted,
                            fontFamily = JetBrainsMono,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                }
                val cats = models.groupBy { it.providerId }.toList()
                cats.forEach { (providerId, providerModels) ->
                    val cc = if (providerId == AiProviderId.ALIBABA) colors.warning else colors.accent
                    item {
                        AiModuleText(
                            providerId.displayName.uppercase(),
                            color = cc,
                            fontFamily = JetBrainsMono,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                        )
                    }
                    items(providerModels) { model ->
                        val selected = model.uniqueKey == current?.uniqueKey
                        AiModuleListRow(
                            title = model.displayName,
                            subtitle = model.id,
                            prefix = if (selected) "▣" else "▸",
                            prefixColor = cc,
                            titleColor = if (selected) cc else colors.textPrimary,
                            onClick = { onSelect(model) },
                            paddingVertical = 8.dp,
                            trailing = {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (AiCapability.VISION in model.capabilities) AiModuleChip("vision", color = colors.textMuted)
                                    if (AiCapability.CODING in model.capabilities) AiModuleChip("code", color = cc)
                                    if (AiCapability.REASONING in model.capabilities) AiModuleChip("think", color = colors.textMuted)
                                    if (selected) AiModuleIcon(
                                        Icons.Rounded.Check,
                                        null,
                                        Modifier.size(14.dp),
                                        tint = cc,
                                    )
                                }
                            },
                        )
                    }
                }
            }
    }
}

// ═══════════════════════════════════
// Keys / settings dialog
// ═══════════════════════════════════
@Composable
private fun TerminalKeysDialog(
    initialKeys: Map<AiProviderId, String>,
    initialProxy: String,
    initialRegion: String,
    onSave: (Map<AiProviderId, String>, String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = AiModuleTheme.colors
    val keys = remember {
        mutableStateMapOf<AiProviderId, String>().apply {
            AiProviderId.entries.forEach { put(it, initialKeys[it].orEmpty()) }
        }
    }
    var pU by remember { mutableStateOf(initialProxy) }
    var rI by remember { mutableStateOf(initialRegion) }
    AiModuleAlertDialog(
        onDismissRequest = onDismiss,
        title = "ai · keys",
        confirmButton = {
            AiModulePillButton(label = "save", onClick = { onSave(keys.toMap(), pU, rI) }, accent = true)
        },
        dismissButton = {
            AiModulePillButton(label = "cancel", onClick = onDismiss, accent = false)
        },
    ) {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AiProviderId.entries.forEach { provider ->
                    AiModuleSectionLabel("> ${provider.displayName.lowercase()}")
                    TerminalMonoField(
                        value = keys[provider].orEmpty(),
                        onValueChange = { keys[provider] = it },
                        placeholder = provider.keyPlaceholder(),
                    )
                }
                AiModuleSectionLabel("> proxy (optional)")
                TerminalMonoField(value = pU, onValueChange = { pU = it }, placeholder = "https://proxy/v1beta/models")
                AiModuleHairline()
                AiModuleSectionLabel("> region")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("intl" to "singapore", "cn" to "beijing").forEach { (c, l) ->
                        AiModulePillButton(
                            label = l,
                            onClick = { rI = c },
                            accent = rI == c,
                        )
                    }
                }
            }
    }
}

// ═══════════════════════════════════
// Mono input field (shared by setup + dialogs)
// ═══════════════════════════════════
@Composable
private fun TerminalMonoField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    singleLine: Boolean = true,
    minHeight: androidx.compose.ui.unit.Dp = 0.dp,
) {
    val colors = AiModuleTheme.colors
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 10.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(
                color = colors.textPrimary,
                fontFamily = JetBrainsMono,
                fontSize = 13.sp,
                lineHeight = 1.4.em,
            ),
            cursorBrush = SolidColor(colors.accent),
            singleLine = singleLine,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight),
        )
        if (value.isEmpty()) {
            AiModuleText(
                placeholder,
                color = colors.textMuted,
                fontFamily = JetBrainsMono,
                fontSize = 13.sp,
            )
        }
    }
}
