package com.glassfiles.ui.screens

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Environment
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glassfiles.data.Strings
import com.glassfiles.data.ai.AiCapability
import com.glassfiles.data.ai.AiConfigStore
import com.glassfiles.data.ai.AiManager
import com.glassfiles.data.ai.AiModelCatalog
import com.glassfiles.data.ai.AiModelSpec
import com.glassfiles.data.ai.AiProvider
import com.glassfiles.data.ai.AiProviderConfig
import com.glassfiles.data.ai.AiProviderType
import com.glassfiles.data.ai.ChatHistoryManager
import com.glassfiles.data.ai.ChatMessage
import com.glassfiles.data.ai.ChatSession
import com.glassfiles.data.ai.GeminiKeyStore
import com.glassfiles.data.ai.MessageAttachment
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val BG = Color(0xFF09090B)
private val Card = Color(0xFF111113)
private val Card2 = Color(0xFF18181B)
private val Border = Color(0xFF27272A)
private val T1 = Color(0xFFE4E4E7)
private val T2 = Color(0xFF71717A)
private val T3 = Color(0xFF52525B)
private val Accent = Color(0xFF22C55E)
private val Bl = Color(0xFF3B82F6)
private val QwenColor = Color(0xFF6366F1)
private val OpenAiColor = Color(0xFF10A37F)
private val XAiColor = Color(0xFFFF4D6D)
private val KimiColor = Color(0xFF8B5CF6)
private val CodeBg = Color(0xFF0D1117)
private val CodeBorder = Color(0xFF30363D)
private val CodeKw = Color(0xFFFF7B72)
private val CodeStr = Color(0xFFA5D6A7)
private val CodeCmt = Color(0xFF8B949E)
private val CodeNum = Color(0xFF79C0FF)
private val CodeTy = Color(0xFFFFA657)
private val CodeFn = Color(0xFFD2A8FF)
private val CodeDef = Color(0xFFE6EDF3)
private val kwSet = setOf("fun","val","var","class","object","interface","enum","when","if","else","for","while","return","import","package","private","public","protected","internal","override","suspend","data","sealed","abstract","open","companion","init","try","catch","throw","finally","is","as","in","by","null","true","false","this","super","it","break","continue","const","lateinit","function","let","def","self","None","True","False","async","await","yield","from","with","lambda","elif","except","raise","static","final","void","int","String","boolean","float","double","long","new","delete","typeof","instanceof","export","default","switch","case","struct","impl","fn","pub","mut","use","mod","func","go","chan","select","defer","range","type","map","make","println","print")

enum class AiConversationMode { CHAT, IMAGE, VIDEO }

@Composable
fun AiChatScreen(onBack: () -> Unit, initialPrompt: String? = null, initialImageBase64: String? = null, currentFolder: String? = null, onRunInTerminal: ((String) -> Unit)? = null) {
    val context = LocalContext.current
    val historyMgr = remember { ChatHistoryManager(context) }
    var activeSessionId by remember { mutableStateOf<String?>(null) }
    var sessions by remember { mutableStateOf(historyMgr.getSessions()) }
    var consumedPrompt by remember { mutableStateOf(false) }
    val hasAnyKey = remember {
        { AiProviderType.entries.any { AiConfigStore.hasApiKey(context, it) } }
    }

    if (!hasAnyKey()) {
        AiProviderSetupScreen(onBack = onBack, onSaved = { sessions = historyMgr.getSessions() })
        return
    }

    LaunchedEffect(initialPrompt, initialImageBase64) {
        if (initialPrompt != null && activeSessionId == null && !consumedPrompt) {
            val defaultProvider = defaultChatProvider(context)
            val session = historyMgr.createSession(defaultProvider)
            historyMgr.saveSession(session)
            activeSessionId = session.id
            consumedPrompt = true
        }
    }

    fun refreshSessions() {
        sessions = historyMgr.getSessions()
    }

    if (activeSessionId != null) {
        AiConversationScreen(
            sessionId = activeSessionId!!,
            historyMgr = historyMgr,
            onBack = { activeSessionId = null; refreshSessions() },
            initialPrompt = if (!consumedPrompt) initialPrompt else null,
            initialImageBase64 = if (!consumedPrompt) initialImageBase64 else null,
            currentFolder = currentFolder,
            onRunInTerminal = onRunInTerminal
        )
    } else {
        AiSessionList(
            sessions = sessions,
            onNew = {
                val session = historyMgr.createSession(defaultChatProvider(context))
                historyMgr.saveSession(session)
                activeSessionId = session.id
            },
            onOpen = { activeSessionId = it.id },
            onDelete = { historyMgr.deleteSession(it.id); refreshSessions() },
            onDeleteAll = { historyMgr.deleteAll(); refreshSessions() },
            onBack = onBack
        )
    }
}

private fun defaultChatProvider(context: Context): AiProvider {
    val configured = AiProviderType.entries.firstOrNull { AiConfigStore.hasApiKey(context, it) } ?: AiProviderType.GEMINI
    val preferredModel = AiConfigStore.getConfig(context, configured).defaultChatModelId
    return AiProvider.fromModelId(preferredModel)
        ?: AiProvider.entries.firstOrNull { it.providerType == configured }
        ?: AiProvider.GEMINI_FLASH
}

@Composable
private fun AiProviderSetupScreen(onBack: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val configs = remember {
        AiProviderType.entries.associateWith { mutableStateOf(AiConfigStore.getConfig(context, it)) }
    }

    Column(Modifier.fillMaxSize().background(BG).verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.height(20.dp))
        Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(48.dp), tint = Accent)
        Text(Strings.aiProvidersTitle, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = T1)
        Text(Strings.aiProvidersSubtitle, fontSize = 14.sp, color = T2)
        AiProviderType.entries.forEach { providerType ->
            val state = configs.getValue(providerType)
            ProviderSetupCard(providerType = providerType, config = state.value, onChange = { state.value = it })
        }
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Accent).clickable {
                configs.values.forEach { state -> AiConfigStore.saveConfig(context, state.value) }
                onSaved()
            }.padding(14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(Strings.done, color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        Box(Modifier.fillMaxWidth().clickable { onBack() }.padding(8.dp), contentAlignment = Alignment.Center) {
            Text(Strings.back, color = T2, fontSize = 14.sp)
        }
    }
}

@Composable
private fun ProviderSetupCard(providerType: AiProviderType, config: AiProviderConfig, onChange: (AiProviderConfig) -> Unit) {
    val accent = providerColor(providerType)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Card).border(1.dp, Border, RoundedCornerShape(14.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(providerType.label, color = accent, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        OutlinedTextField(
            value = config.apiKey,
            onValueChange = { onChange(config.copy(apiKey = it, enabled = it.isNotBlank())) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text(Strings.providerApiKey, color = T3) },
            colors = providerTextFieldColors(accent)
        )
        if (providerType.supportsProxy) {
            OutlinedTextField(
                value = config.baseUrl,
                onValueChange = { onChange(config.copy(baseUrl = it)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text(Strings.providerBaseUrlOptional, color = T3) },
                colors = providerTextFieldColors(accent)
            )
        }
        if (providerType.supportsRegion) {
            OutlinedTextField(
                value = config.region,
                onValueChange = { onChange(config.copy(region = it)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text(Strings.providerRegion, color = T3) },
                colors = providerTextFieldColors(accent)
            )
        }
        Text(Strings.aiDefaultModelHint, color = T3, fontSize = 11.sp)
    }
}

@Composable
private fun AiSessionList(sessions: List<ChatSession>, onNew: () -> Unit, onOpen: (ChatSession) -> Unit, onDelete: (ChatSession) -> Unit, onDeleteAll: () -> Unit, onBack: () -> Unit) {
    val sdf = remember { SimpleDateFormat("dd.MM.yy HH:mm", Locale.getDefault()) }
    var search by remember { mutableStateOf("") }
    val filtered = if (search.isBlank()) sessions else sessions.filter { it.title.contains(search, true) || it.messages.any { msg -> msg.content.contains(search, true) } }

    Column(Modifier.fillMaxSize().background(BG)) {
        Row(Modifier.fillMaxWidth().padding(top = 48.dp, start = 4.dp, end = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = T1) }
            Text(Strings.aiChatTitle, color = T1, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.weight(1f))
            if (sessions.isNotEmpty()) IconButton(onClick = onDeleteAll) { Icon(Icons.Rounded.Delete, null, tint = T2) }
        }
        BasicTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).background(Card, RoundedCornerShape(10.dp)).border(1.dp, Border, RoundedCornerShape(10.dp)).padding(12.dp),
            textStyle = TextStyle(T1, 14.sp),
            cursorBrush = SolidColor(Accent),
            singleLine = true,
            decorationBox = { inner ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Chat, null, Modifier.size(16.dp), tint = T3)
                    Spacer(Modifier.width(8.dp))
                    Box {
                        if (search.isBlank()) Text(Strings.searchChats, color = T3, fontSize = 14.sp)
                        inner()
                    }
                }
            }
        )
        if (filtered.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(48.dp), tint = Accent)
                    Text(if (search.isNotBlank()) Strings.nothingFound else Strings.noChats, color = T2, fontSize = 16.sp)
                }
            }
        } else {
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(filtered) { session ->
                    val color = session.providerType?.let { providerColor(it) } ?: Accent
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Card).clickable { onOpen(session) }.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(Modifier.size(40.dp).background(Card2, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Chat, null, tint = color)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(session.title, color = T1, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(sdf.format(Date(session.updatedAt)), color = T3, fontSize = 11.sp)
                                Text("${session.messages.size} ${Strings.messages}", color = T3, fontSize = 11.sp)
                                Text(session.modelLabel ?: session.provider, color = color, fontSize = 11.sp)
                            }
                        }
                        IconButton(onClick = { onDelete(session) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Rounded.Close, null, Modifier.size(16.dp), tint = T3)
                        }
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(14.dp)).background(Accent).clickable(onClick = onNew).padding(14.dp), contentAlignment = Alignment.Center) {
            Text(Strings.newChat, color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun AiConversationScreen(sessionId: String, historyMgr: ChatHistoryManager, onBack: () -> Unit, initialPrompt: String? = null, initialImageBase64: String? = null, currentFolder: String? = null, onRunInTerminal: ((String) -> Unit)? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val session = remember { historyMgr.getSession(sessionId) }
    var messages by remember { mutableStateOf(session?.messages ?: emptyList()) }
    var input by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var currentResponse by remember { mutableStateOf("") }
    var selectedProviderType by remember { mutableStateOf(session?.providerType?.let { runCatching { AiProviderType.valueOf(it.uppercase()) }.getOrNull() } ?: defaultChatProvider(context).providerType) }
    var mode by remember { mutableStateOf(AiConversationMode.CHAT) }
    var showSettings by remember { mutableStateOf(false) }
    var showModelPicker by remember { mutableStateOf(false) }
    var attachedImage by remember { mutableStateOf<String?>(initialImageBase64) }
    var attachedFile by remember { mutableStateOf<Pair<String, String>?>(null) }
    var attachedZip by remember { mutableStateOf<String?>(null) }
    var autoSent by remember { mutableStateOf(false) }
    var currentJob by remember { mutableStateOf<Job?>(null) }
    var editIdx by remember { mutableIntStateOf(-1) }
    var editTxt by remember { mutableStateOf("") }
    var speakingIdx by remember { mutableIntStateOf(-1) }
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var ttsReady by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val providerConfigs = rememberProviderConfigs(context)
    var currentConfig by remember { mutableStateOf(providerConfigs[selectedProviderType] ?: AiConfigStore.getConfig(context, selectedProviderType)) }
    var selectedProvider by remember { mutableStateOf(resolveSelectedProvider(selectedProviderType, currentConfig, mode)) }

    LaunchedEffect(selectedProviderType, mode, currentConfig.defaultChatModelId, currentConfig.defaultImageModelId, currentConfig.defaultVideoModelId) {
        currentConfig = providerConfigs[selectedProviderType] ?: AiConfigStore.getConfig(context, selectedProviderType)
        selectedProvider = resolveSelectedProvider(selectedProviderType, currentConfig, mode)
    }

    DisposableEffect(Unit) {
        tts = TextToSpeech(context) { status -> ttsReady = status == TextToSpeech.SUCCESS }
        onDispose { tts?.shutdown() }
    }

    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (text != null) input = if (input.isBlank()) text else "$input $text"
        }
    }
    val cameraFile = remember { File(context.cacheDir, "ai_camera_${System.currentTimeMillis()}.jpg") }
    val cameraUri = remember { androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", cameraFile) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success -> if (success) attachedImage = AiManager.encodeImage(cameraFile) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        try {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            val name = cursor?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) c.getString(idx) else null
                } else null
            } ?: uri.lastPathSegment ?: "file"
            val ext = name.substringAfterLast('.', "").lowercase()
            when {
                listOf("png", "jpg", "jpeg", "gif", "webp", "bmp").contains(ext) -> {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val target = File(context.cacheDir, "ai_img_${System.currentTimeMillis()}.jpg")
                        target.outputStream().use { output -> stream.copyTo(output) }
                        attachedImage = AiManager.encodeImage(target)
                    }
                }
                ext in listOf("zip", "jar") -> {
                    val tmp = File(context.cacheDir, "ai_zip_${System.currentTimeMillis()}.$ext")
                    context.contentResolver.openInputStream(uri)?.use { stream -> tmp.outputStream().use { output -> stream.copyTo(output) } }
                    val entries = AiManager.extractZipForAi(tmp.absolutePath, context)
                    attachedZip = AiManager.formatZipContents(entries)
                    attachedFile = name to "${entries.size} files extracted"
                    tmp.delete()
                }
                else -> {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val text = stream.bufferedReader().readText()
                        attachedFile = name to if (text.length > 10000) text.take(10000) + "\n...[truncated]" else text
                    }
                }
            }
        } catch (e: Exception) {
            Toast.makeText(context, "${Strings.error}: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    val folderFiles = remember(currentFolder) {
        if (currentFolder != null) {
            try { File(currentFolder).listFiles()?.map { "${if (it.isDirectory) "[DIR]" else "[${it.extension}]"} ${it.name}" }?.take(30)?.joinToString("\n") } catch (_: Exception) { null }
        } else null
    }

    fun saveSession(updatedMessages: List<ChatMessage>) {
        historyMgr.saveSession(
            ChatSession(
                id = sessionId,
                title = historyMgr.generateTitle(updatedMessages),
                provider = selectedProvider.name,
                messages = updatedMessages,
                createdAt = session?.createdAt ?: System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                providerType = selectedProvider.providerType.storageKey,
                vendor = selectedProvider.vendor.label,
                modelId = selectedProvider.modelId,
                modelLabel = selectedProvider.label
            )
        )
    }

    fun copy(text: String) {
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("ai", text))
        Toast.makeText(context, Strings.copied, Toast.LENGTH_SHORT).show()
    }

    fun exportChat() {
        val sb = StringBuilder("# AI Chat\n**${selectedProvider.label}** — ${SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())}\n\n---\n\n")
        messages.forEach { message ->
            sb.append(if (message.role == "user") "**You:**\n" else "**AI:**\n")
            sb.append(message.content)
            sb.append("\n\n")
        }
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "GlassFiles_AI")
        dir.mkdirs()
        val file = File(dir, "chat_${System.currentTimeMillis()}.md")
        file.writeText(sb.toString())
        Toast.makeText(context, "${Strings.savedFileToast} ${file.name}", Toast.LENGTH_SHORT).show()
    }

    fun saveCode(code: String, lang: String) {
        val ext = when (lang) {
            "kotlin", "kt" -> "kt"
            "java" -> "java"
            "python", "py" -> "py"
            "javascript", "js" -> "js"
            "typescript", "ts" -> "ts"
            "html" -> "html"
            "css" -> "css"
            "xml" -> "xml"
            "json" -> "json"
            "yaml", "yml" -> "yml"
            "bash", "sh" -> "sh"
            "sql" -> "sql"
            "go" -> "go"
            "rust", "rs" -> "rs"
            else -> "txt"
        }
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "GlassFiles_AI")
        dir.mkdirs()
        val file = File(dir, "code_${System.currentTimeMillis()}.$ext")
        file.writeText(code)
        Toast.makeText(context, "${Strings.savedFileToast} ${file.name}", Toast.LENGTH_SHORT).show()
    }

    fun speak(text: String, idx: Int) {
        if (!ttsReady || tts == null) return
        if (speakingIdx == idx) {
            tts?.stop()
            speakingIdx = -1
            return
        }
        val clean = text.replace(Regex("```[\\s\\S]*?```"), "code block").replace(Regex("[*_`#>]"), "")
        tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "msg_$idx")
        speakingIdx = idx
    }

    fun voiceInput() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, Strings.speakPrompt)
        }
        try { voiceLauncher.launch(intent) } catch (_: Exception) { Toast.makeText(context, Strings.voiceNotAvailable, Toast.LENGTH_SHORT).show() }
    }

    fun appendAssistantMessage(content: String = "", attachments: List<MessageAttachment> = emptyList()) {
        messages = messages + ChatMessage(
            role = "assistant",
            content = content,
            attachments = attachments,
            providerType = selectedProvider.providerType.storageKey,
            vendor = selectedProvider.vendor.label,
            modelId = selectedProvider.modelId,
            modelLabel = selectedProvider.label,
            createdAt = System.currentTimeMillis()
        )
        saveSession(messages)
    }

    fun send() {
        if (isLoading) return
        var text = input.trim()
        val attachmentText = when {
            attachedZip != null -> attachedZip
            attachedFile != null -> "File: ${attachedFile!!.first}\n```\n${attachedFile!!.second}\n```"
            else -> null
        }
        val imageToSend = attachedImage
        if (text.isBlank() && imageToSend != null) text = if (mode == AiConversationMode.IMAGE) "Generate image inspired by this photo" else "What is in this image?"
        if (text.isBlank() && attachmentText != null) text = "Analyze this file"
        if (text.isBlank()) return

        val fullText = if (folderFiles != null && messages.isEmpty()) {
            "Current folder: $currentFolder\nFiles:\n$folderFiles\n\nUser: $text"
        } else text

        val outgoing = ChatMessage.legacy(
            role = "user",
            content = text,
            imageBase64 = imageToSend,
            fileContent = attachmentText,
            providerType = selectedProvider.providerType.storageKey,
            vendor = selectedProvider.vendor.label,
            modelId = selectedProvider.modelId,
            modelLabel = selectedProvider.label,
            createdAt = System.currentTimeMillis()
        )
        input = ""
        attachedImage = null
        attachedFile = null
        attachedZip = null
        messages = messages + outgoing
        saveSession(messages)

        if (mode == AiConversationMode.IMAGE) {
            appendAssistantMessage(
                content = "${Strings.generatedImageMessage} $text",
                attachments = listOf(AiManager.createGeneratedImageAttachment(text, selectedProvider.providerType, selectedProvider.modelId))
            )
            return
        }
        if (mode == AiConversationMode.VIDEO) {
            appendAssistantMessage(
                content = "${Strings.generatedVideoMessage} $text",
                attachments = listOf(AiManager.createGeneratedVideoAttachment(text, selectedProvider.providerType, selectedProvider.modelId))
            )
            return
        }

        isLoading = true
        currentResponse = ""
        val apiMessages = if (folderFiles != null && messages.size == 1) {
            listOf(ChatMessage.legacy(
                role = "user",
                content = fullText,
                imageBase64 = imageToSend,
                fileContent = attachmentText,
                providerType = selectedProvider.providerType.storageKey,
                vendor = selectedProvider.vendor.label,
                modelId = selectedProvider.modelId,
                modelLabel = selectedProvider.label,
                createdAt = System.currentTimeMillis()
            ))
        } else messages

        currentJob = scope.launch {
            try {
                val response = AiManager.chat(
                    provider = selectedProvider,
                    messages = apiMessages,
                    geminiKey = providerConfigs[AiProviderType.GEMINI]?.apiKey.orEmpty(),
                    openRouterKey = providerConfigs[selectedProvider.providerType]?.apiKey.orEmpty(),
                    proxyUrl = providerConfigs[AiProviderType.KIMI]?.apiKey ?: providerConfigs[AiProviderType.GEMINI]?.baseUrl.orEmpty(),
                    qwenKey = providerConfigs[AiProviderType.XAI]?.apiKey ?: providerConfigs[AiProviderType.QWEN]?.apiKey.orEmpty(),
                    qwenRegion = providerConfigs[AiProviderType.QWEN]?.region ?: GeminiKeyStore.getQwenRegion(context)
                ) { currentResponse += it }
                appendAssistantMessage(content = response)
                currentResponse = ""
            } catch (e: Exception) {
                val content = if (e is kotlinx.coroutines.CancellationException && currentResponse.isNotBlank()) currentResponse + "\n\n*(stopped)*" else "Error: ${e.message}"
                appendAssistantMessage(content = content)
                currentResponse = ""
            }
            isLoading = false
            currentJob = null
        }
    }

    fun regenerate() {
        if (messages.isEmpty() || isLoading) return
        val updated = messages.toMutableList()
        if (updated.last().role == "assistant") updated.removeLast()
        messages = updated
        saveSession(messages)
        input = messages.lastOrNull { it.role == "user" }?.content.orEmpty()
        send()
    }

    fun confirmEdit() {
        if (editIdx < 0) return
        val original = messages[editIdx]
        messages = messages.toMutableList().also {
            it[editIdx] = original.copy(content = editTxt)
            while (it.lastOrNull()?.role == "assistant") it.removeLast()
        }
        saveSession(messages)
        editIdx = -1
        editTxt = ""
        regenerate()
    }

    LaunchedEffect(messages.size, currentResponse) {
        val total = messages.size + if (currentResponse.isNotEmpty()) 1 else 0
        if (total > 0) listState.animateScrollToItem(total - 1)
    }
    LaunchedEffect(initialPrompt) {
        if (initialPrompt != null && !autoSent && messages.isEmpty()) {
            autoSent = true
            input = initialPrompt
            send()
        }
    }

    if (editIdx >= 0) {
        AlertDialog(
            onDismissRequest = { editIdx = -1 },
            containerColor = Card,
            title = { Text(Strings.editMessage, fontWeight = FontWeight.Bold, color = T1) },
            text = {
                OutlinedTextField(
                    value = editTxt,
                    onValueChange = { editTxt = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                    maxLines = 8,
                    colors = providerTextFieldColors(Accent)
                )
            },
            confirmButton = { TextButton(onClick = { confirmEdit() }) { Text(Strings.message.removeSuffix("...").ifBlank { Strings.done }, color = Accent) } },
            dismissButton = { TextButton(onClick = { editIdx = -1 }) { Text(Strings.cancel, color = T2) } }
        )
    }

    if (showSettings) {
        AiSettingsDialog(
            providerConfigs = providerConfigs,
            onDismiss = { showSettings = false },
            onApply = { updated ->
                updated.forEach { (providerType, config) -> AiConfigStore.saveConfig(context, config) }
                showSettings = false
                currentConfig = updated[selectedProviderType] ?: currentConfig
                selectedProvider = resolveSelectedProvider(selectedProviderType, currentConfig, mode)
                Toast.makeText(context, Strings.done, Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showModelPicker) {
        AiModelPickerDialog(
            currentProviderType = selectedProviderType,
            currentProvider = selectedProvider,
            currentMode = mode,
            providerConfigs = providerConfigs,
            onDismiss = { showModelPicker = false },
            onSelect = { providerType, provider ->
                selectedProviderType = providerType
                selectedProvider = provider
                val existing = providerConfigs[providerType] ?: AiConfigStore.getConfig(context, providerType)
                val updated = when (mode) {
                    AiConversationMode.CHAT -> existing.copy(defaultChatModelId = provider.modelId)
                    AiConversationMode.IMAGE -> existing.copy(defaultImageModelId = provider.modelId)
                    AiConversationMode.VIDEO -> existing.copy(defaultVideoModelId = provider.modelId)
                }
                providerConfigs[providerType] = updated
                AiConfigStore.saveConfig(context, updated)
                currentConfig = updated
                showModelPicker = false
            }
        )
    }

    Column(Modifier.fillMaxSize().background(BG).imePadding()) {
        Row(Modifier.fillMaxWidth().background(Card).padding(top = 48.dp, start = 4.dp, end = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { saveSession(messages); onBack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = T1) }
            Column(Modifier.weight(1f)) {
                Text(Strings.aiChatTitle, color = T1, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(selectedProvider.label, color = providerColor(selectedProvider.providerType), fontSize = 11.sp, maxLines = 1)
            }
            IconButton(onClick = { exportChat() }) { Icon(Icons.Rounded.FileDownload, null, tint = T2) }
            Row(Modifier.clip(RoundedCornerShape(8.dp)).background(Card2).border(1.dp, Border, RoundedCornerShape(8.dp)).clickable { showModelPicker = true }.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(providerColor(selectedProvider.providerType)))
                Text(selectedProvider.label.take(18), color = T1, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Icon(Icons.Rounded.KeyboardArrowDown, null, tint = T2, modifier = Modifier.size(14.dp))
            }
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = { showSettings = true }) { Icon(Icons.Rounded.Settings, null, tint = T2) }
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ModeChip(Strings.chatMode, mode == AiConversationMode.CHAT) { mode = AiConversationMode.CHAT; selectedProvider = resolveSelectedProvider(selectedProviderType, providerConfigs[selectedProviderType]!!, mode) }
            ModeChip(Strings.imageMode, mode == AiConversationMode.IMAGE) { mode = AiConversationMode.IMAGE; selectedProvider = resolveSelectedProvider(selectedProviderType, providerConfigs[selectedProviderType]!!, mode) }
            ModeChip(Strings.videoMode, mode == AiConversationMode.VIDEO) { mode = AiConversationMode.VIDEO; selectedProvider = resolveSelectedProvider(selectedProviderType, providerConfigs[selectedProviderType]!!, mode) }
        }
        Spacer(Modifier.height(8.dp))

        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (messages.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(48.dp), tint = providerColor(selectedProvider.providerType))
                        Text(selectedProvider.label, color = T1, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(selectedProvider.description, color = T3, fontSize = 13.sp)
                        if (currentFolder != null) Text("${Strings.aiContextLabel}: ${currentFolder.substringAfterLast("/")}", color = Accent, fontSize = 12.sp)
                        Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val suggestions = mutableListOf("Explain this code", "Analyze ZIP archive", if (mode == AiConversationMode.IMAGE) "Generate wallpaper" else if (mode == AiConversationMode.VIDEO) "Create short product teaser" else "What's in this image?")
                            if (currentFolder != null) {
                                suggestions.add(0, "What files are in this folder?")
                                suggestions.add(1, "What takes the most space here?")
                            }
                            suggestions.take(5).forEach { suggestion ->
                                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Card).border(1.dp, Border, RoundedCornerShape(10.dp)).clickable { input = suggestion }.padding(12.dp)) {
                                    Text(suggestion, color = T2, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }
            items(messages.size) { idx ->
                val message = messages[idx]
                val isLast = idx == messages.lastIndex
                Bubble(
                    msg = message,
                    provider = selectedProvider,
                    msgIdx = idx,
                    speakingIdx = speakingIdx,
                    onCopy = { copy(message.content) },
                    onEdit = if (message.role == "user") { { editIdx = idx; editTxt = message.content } } else null,
                    onDelete = {
                        val updated = messages.toMutableList().also { it.removeAt(idx) }
                        messages = updated
                        saveSession(messages)
                    },
                    onSaveCode = { code, lang -> saveCode(code, lang) },
                    onRegenerate = if (isLast && message.role == "assistant" && !isLoading && mode == AiConversationMode.CHAT) { { regenerate() } } else null,
                    onSpeak = { speak(message.content, idx) },
                    onRunScript = if (message.role == "assistant" && onRunInTerminal != null) { { onRunInTerminal(message.content) } } else null
                )
            }
            if (currentResponse.isNotEmpty()) {
                item {
                    Bubble(
                        msg = ChatMessage(role = "assistant", content = currentResponse + "\u2588", providerType = selectedProvider.providerType.storageKey, vendor = selectedProvider.vendor.label, modelId = selectedProvider.modelId, modelLabel = selectedProvider.label),
                        provider = selectedProvider,
                        msgIdx = -1,
                        speakingIdx = speakingIdx,
                        onCopy = {},
                        onEdit = null,
                        onDelete = {},
                        onSaveCode = { _, _ -> },
                        onRegenerate = null,
                        onSpeak = {},
                        onRunScript = null
                    )
                }
            }
            if (isLoading && currentResponse.isEmpty()) {
                item {
                    Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(Modifier.size(16.dp), color = providerColor(selectedProvider.providerType), strokeWidth = 2.dp)
                        Text(if (mode == AiConversationMode.CHAT) Strings.thinking else Strings.preparing, color = T2, fontSize = 13.sp)
                    }
                }
            }
        }

        if (attachedImage != null || attachedFile != null) {
            Row(Modifier.fillMaxWidth().background(Card).padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (attachedImage != null) {
                    Icon(Icons.Rounded.Image, null, tint = Accent, modifier = Modifier.size(18.dp))
                    Text(Strings.photo, color = T2, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = { attachedImage = null }, modifier = Modifier.size(24.dp)) { Icon(Icons.Rounded.Close, null, tint = T2, modifier = Modifier.size(14.dp)) }
                }
                if (attachedFile != null) {
                    Icon(if (attachedZip != null) Icons.Rounded.FolderZip else Icons.Rounded.AttachFile, null, tint = Bl, modifier = Modifier.size(18.dp))
                    Column(Modifier.weight(1f)) {
                        Text(attachedFile!!.first, color = T2, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (attachedZip != null) Text(attachedFile!!.second, color = T3, fontSize = 10.sp)
                    }
                    IconButton(onClick = { attachedFile = null; attachedZip = null }, modifier = Modifier.size(24.dp)) { Icon(Icons.Rounded.Close, null, tint = T2, modifier = Modifier.size(14.dp)) }
                }
            }
        }

        Row(Modifier.fillMaxWidth().background(Card).padding(8.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onClick = { filePicker.launch("*/*") }, modifier = Modifier.size(40.dp)) { Icon(Icons.Rounded.AttachFile, null, tint = T2) }
            IconButton(onClick = { try { cameraLauncher.launch(cameraUri) } catch (_: Exception) { Toast.makeText(context, Strings.cameraNotAvailable, Toast.LENGTH_SHORT).show() } }, modifier = Modifier.size(40.dp)) { Icon(Icons.Rounded.CameraAlt, null, tint = T2) }
            BasicTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f).background(Card2, RoundedCornerShape(20.dp)).border(1.dp, Border, RoundedCornerShape(20.dp)).padding(horizontal = 14.dp, vertical = 11.dp),
                textStyle = TextStyle(T1, 15.sp),
                cursorBrush = SolidColor(providerColor(selectedProvider.providerType)),
                decorationBox = { inner ->
                    Box {
                        if (input.isBlank()) Text(if (mode == AiConversationMode.CHAT) Strings.message else if (mode == AiConversationMode.IMAGE) Strings.describeImagePrompt else Strings.describeVideoPrompt, color = T3, fontSize = 15.sp)
                        inner()
                    }
                }
            )
            IconButton(onClick = { voiceInput() }, modifier = Modifier.size(40.dp)) { Icon(Icons.Rounded.Mic, null, tint = T2) }
            if (isLoading) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0xFFFF3B30)).clickable { currentJob?.cancel(); currentJob = null }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Stop, null, tint = Color.White)
                }
            } else {
                val canSend = input.isNotBlank() || attachedImage != null || attachedFile != null
                Box(Modifier.size(40.dp).clip(CircleShape).background(if (canSend) providerColor(selectedProvider.providerType) else Card2).clickable(enabled = canSend) { send() }, contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Rounded.Send, null, tint = if (canSend) Color.White else T3)
                }
            }
        }
    }
}

private fun resolveSelectedProvider(providerType: AiProviderType, config: AiProviderConfig, mode: AiConversationMode): AiProvider {
    val preferredId = when (mode) {
        AiConversationMode.CHAT -> config.defaultChatModelId
        AiConversationMode.IMAGE -> config.defaultImageModelId
        AiConversationMode.VIDEO -> config.defaultVideoModelId
    }
    val exact = AiProvider.fromModelId(preferredId)
    if (exact != null) return exact
    val fallback = AiProvider.entries.filter { it.providerType == providerType }.firstOrNull {
        when (mode) {
            AiConversationMode.CHAT -> true
            AiConversationMode.IMAGE -> it.spec.supportsImageGeneration || AiCapability.IMAGE_INPUT in it.spec.capabilities
            AiConversationMode.VIDEO -> it.spec.supportsVideoGeneration || AiCapability.VIDEO_OUTPUT in it.spec.capabilities || AiCapability.CODING in it.spec.capabilities
        }
    }
    return fallback ?: defaultChatProviderProvider(providerType)
}

private fun defaultChatProviderProvider(providerType: AiProviderType): AiProvider =
    AiProvider.entries.firstOrNull { it.providerType == providerType } ?: AiProvider.GEMINI_FLASH

@Composable
private fun rememberProviderConfigs(context: Context): MutableMap<AiProviderType, AiProviderConfig> = remember {
    AiProviderType.entries.associateWithTo(linkedMapOf()) { AiConfigStore.getConfig(context, it) }
}

@Composable
private fun AiSettingsDialog(providerConfigs: Map<AiProviderType, AiProviderConfig>, onDismiss: () -> Unit, onApply: (Map<AiProviderType, AiProviderConfig>) -> Unit) {
    val editable = remember(providerConfigs) { providerConfigs.toMutableMap() }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Card,
        title = { Text(Strings.aiSettingsTitle, color = T1, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AiProviderType.entries.forEach { providerType ->
                    val config = editable[providerType] ?: AiProviderConfig(providerType = providerType)
                    ProviderSettingsCard(providerType, config) { editable[providerType] = it }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onApply(editable) }) { Text(Strings.save, color = Accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.cancel, color = T2) } }
    )
}

@Composable
private fun ProviderSettingsCard(providerType: AiProviderType, config: AiProviderConfig, onChange: (AiProviderConfig) -> Unit) {
    val accent = providerColor(providerType)
    val models = AiModelCatalog.byProvider(providerType)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Card2).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(providerType.label, color = accent, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        OutlinedTextField(value = config.apiKey, onValueChange = { onChange(config.copy(apiKey = it, enabled = it.isNotBlank())) }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text(Strings.providerApiKey, color = T3) }, colors = providerTextFieldColors(accent))
        if (providerType.supportsProxy) {
            OutlinedTextField(value = config.baseUrl, onValueChange = { onChange(config.copy(baseUrl = it)) }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text(Strings.providerBaseUrl, color = T3) }, colors = providerTextFieldColors(accent))
        }
        if (providerType.supportsRegion) {
            OutlinedTextField(value = config.region, onValueChange = { onChange(config.copy(region = it)) }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text(Strings.providerRegion, color = T3) }, colors = providerTextFieldColors(accent))
        }
        ModelPreferenceRow(Strings.chatModelTitle, models, config.defaultChatModelId) { onChange(config.copy(defaultChatModelId = it)) }
        ModelPreferenceRow(Strings.codingModelTitle, models.filter { AiCapability.CODING in it.capabilities }, config.defaultCodingModelId) { onChange(config.copy(defaultCodingModelId = it)) }
        ModelPreferenceRow(Strings.imageModelTitle, models.filter { it.supportsImageGeneration || AiCapability.IMAGE_INPUT in it.capabilities }, config.defaultImageModelId) { onChange(config.copy(defaultImageModelId = it)) }
        ModelPreferenceRow(Strings.videoModelTitle, models.filter { it.supportsVideoGeneration || AiCapability.VIDEO_OUTPUT in it.capabilities || AiCapability.CODING in it.capabilities }, config.defaultVideoModelId) { onChange(config.copy(defaultVideoModelId = it)) }
    }
}

@Composable
private fun ModelPreferenceRow(label: String, models: List<AiModelSpec>, selectedModelId: String, onSelect: (String) -> Unit) {
    if (models.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = T2, fontSize = 11.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            models.take(8).forEach { model ->
                val selected = selectedModelId == model.modelId
                Box(Modifier.clip(RoundedCornerShape(8.dp)).background(if (selected) Accent.copy(0.15f) else BG).border(1.dp, if (selected) Accent else Border, RoundedCornerShape(8.dp)).clickable { onSelect(model.modelId) }.padding(horizontal = 8.dp, vertical = 6.dp)) {
                    Text(model.label, color = if (selected) Accent else T2, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun AiModelPickerDialog(currentProviderType: AiProviderType, currentProvider: AiProvider, currentMode: AiConversationMode, providerConfigs: Map<AiProviderType, AiProviderConfig>, onDismiss: () -> Unit, onSelect: (AiProviderType, AiProvider) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Card,
        title = { Text(Strings.selectModel, color = T1, fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.heightIn(max = 500.dp)) {
                AiProviderType.entries.forEach { providerType ->
                    val config = providerConfigs[providerType]
                    if (config?.apiKey.isNullOrBlank()) {
                        item {
                            Text("${providerType.label}: ${Strings.aiAddApiKeyHint}", color = T3, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp))
                        }
                    } else {
                        val accent = providerColor(providerType)
                        val models = AiProvider.entries.filter { it.providerType == providerType }.filter {
                            when (currentMode) {
                                AiConversationMode.CHAT -> true
                                AiConversationMode.IMAGE -> it.spec.supportsImageGeneration || AiCapability.IMAGE_INPUT in it.spec.capabilities
                                AiConversationMode.VIDEO -> it.spec.supportsVideoGeneration || AiCapability.VIDEO_OUTPUT in it.spec.capabilities || AiCapability.CODING in it.spec.capabilities
                            }
                        }
                        item { Text(providerType.label, color = accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)) }
                        items(models) { provider ->
                            ModelCard(provider, provider == currentProvider && providerType == currentProviderType, accent) { onSelect(providerType, provider) }
                        }
                    }
                }
            }
        },
        confirmButton = {}
    )
}

@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(if (selected) Accent.copy(alpha = 0.14f) else Card).border(1.dp, if (selected) Accent else Border, RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp)) {
        Text(label, fontSize = 12.sp, color = if (selected) Accent else T2, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun ModelCard(provider: AiProvider, selected: Boolean, accent: Color, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (selected) accent.copy(0.1f) else Color.Transparent).border(1.dp, if (selected) accent.copy(0.3f) else Border, RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(36.dp).background(if (selected) accent.copy(0.15f) else Card2, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.AutoAwesome, null, tint = if (selected) accent else T2)
        }
        Column(Modifier.weight(1f)) {
            Text(provider.label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = if (selected) accent else T1)
            Text(provider.description, fontSize = 11.sp, color = T3)
        }
        if (provider.supportsVision) Bdg(Strings.visionBadge, T2)
        if (provider.supportsFiles) Bdg(Strings.filesBadge, accent)
        if (provider.supportsCoding) Bdg(Strings.codeBadge, accent)
        if (selected) Icon(Icons.Rounded.Check, null, tint = accent, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun Bdg(text: String, color: Color) {
    Box(Modifier.background(color.copy(alpha = 0.1f), RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 2.dp)) {
        Text(text, fontSize = 9.sp, color = color, fontWeight = FontWeight.SemiBold)
    }
}

private fun providerColor(providerType: AiProviderType): Color = when (providerType) {
    AiProviderType.GEMINI -> Accent
    AiProviderType.QWEN -> QwenColor
    AiProviderType.OPENAI -> OpenAiColor
    AiProviderType.XAI -> XAiColor
    AiProviderType.KIMI -> KimiColor
}

@Composable
private fun providerTextFieldColors(accent: Color) = OutlinedTextFieldDefaults.colors(
    focusedTextColor = T1,
    unfocusedTextColor = T1,
    focusedBorderColor = accent,
    unfocusedBorderColor = Border,
    cursorColor = accent
)

@Composable
private fun renderAttachmentPreview(attachment: MessageAttachment) {
    when (attachment.kind) {
        "image", "generated_image" -> {
            val bmp = remember(attachment.base64Data) {
                try {
                    val bytes = Base64.decode(attachment.base64Data, Base64.NO_WRAP)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                } catch (_: Exception) { null }
            }
            if (bmp != null) {
                Image(bitmap = bmp.asImageBitmap(), contentDescription = null, modifier = Modifier.widthIn(max = 260.dp).heightIn(max = 220.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
            } else {
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Card2).padding(12.dp)) {
                    Text(Strings.generatedImageFallback, color = T2, fontSize = 12.sp)
                }
            }
        }
        "generated_video" -> {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Card2).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Rounded.Movie, null, tint = Bl)
                Column(Modifier.weight(1f)) {
                    Text(attachment.name ?: "Generated video", color = T1, fontSize = 13.sp)
                    Text(attachment.metadata["prompt"] ?: attachment.textContent.orEmpty(), color = T3, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun CodeBlock(code: String, lang: String, onCopy: () -> Unit, onSave: () -> Unit) {
    val lines = code.lines()
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).border(1.dp, CodeBorder, RoundedCornerShape(8.dp))) {
        Row(Modifier.fillMaxWidth().background(Color(0xFF1C2128)).padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(lang.ifBlank { "code" }, fontSize = 11.sp, color = CodeCmt, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Box(Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xFF30363D)).clickable { onSave() }.padding(horizontal = 8.dp, vertical = 3.dp)) { Text("Save", fontSize = 10.sp, color = CodeDef) }
            Spacer(Modifier.width(6.dp))
            Box(Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xFF30363D)).clickable { onCopy() }.padding(horizontal = 8.dp, vertical = 3.dp)) { Text("Copy", fontSize = 10.sp, color = CodeDef) }
        }
        Column(Modifier.background(CodeBg).horizontalScroll(rememberScrollState()).padding(8.dp)) {
            lines.forEachIndexed { idx, line ->
                Row {
                    Text("${idx + 1}".padStart(3), fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF484F58), modifier = Modifier.padding(end = 10.dp))
                    Text(hlLine(line, lang), fontSize = 12.sp, fontFamily = FontFamily.Monospace, lineHeight = 18.sp)
                }
            }
        }
    }
}

private fun hlLine(line: String, lang: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < line.length) {
        when {
            i < line.length - 1 && line[i] == '/' && line[i + 1] == '/' -> { withStyle(SpanStyle(color = CodeCmt)) { append(line.substring(i)) }; i = line.length }
            line[i] == '#' && lang in listOf("python", "py", "bash", "sh", "yaml", "yml", "rb") -> { withStyle(SpanStyle(color = CodeCmt)) { append(line.substring(i)) }; i = line.length }
            line[i] == '"' -> { val e = line.indexOf('"', i + 1).let { if (it < 0) line.length - 1 else it }; withStyle(SpanStyle(color = CodeStr)) { append(line.substring(i, e + 1)) }; i = e + 1 }
            line[i] == '\'' -> { val e = line.indexOf('\'', i + 1).let { if (it < 0) line.length - 1 else it }; withStyle(SpanStyle(color = CodeStr)) { append(line.substring(i, e + 1)) }; i = e + 1 }
            line[i] == '@' -> { var j = i + 1; while (j < line.length && (line[j].isLetterOrDigit() || line[j] == '.')) j++; withStyle(SpanStyle(color = CodeTy)) { append(line.substring(i, j)) }; i = j }
            line[i].isDigit() && (i == 0 || !line[i - 1].isLetterOrDigit()) -> { var j = i; while (j < line.length && (line[j].isDigit() || line[j] == '.' || line[j] == 'f' || line[j] == 'L')) j++; withStyle(SpanStyle(color = CodeNum)) { append(line.substring(i, j)) }; i = j }
            line[i].isLetter() || line[i] == '_' -> {
                var j = i
                while (j < line.length && (line[j].isLetterOrDigit() || line[j] == '_')) j++
                val word = line.substring(i, j)
                val color = when {
                    word in kwSet -> CodeKw
                    word.first().isUpperCase() -> CodeTy
                    j < line.length && line[j] == '(' -> CodeFn
                    else -> CodeDef
                }
                withStyle(SpanStyle(color = color)) { append(word) }
                i = j
            }
            else -> { withStyle(SpanStyle(color = CodeDef)) { append(line[i].toString()) }; i++ }
        }
    }
}

@Composable
private fun MdText(text: String) {
    val lines = text.lines()
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for (line in lines) {
            when {
                line.startsWith("### ") -> Text(line.removePrefix("### "), color = T1, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                line.startsWith("## ") -> Text(line.removePrefix("## "), color = T1, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                line.startsWith("# ") -> Text(line.removePrefix("# "), color = T1, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                line.startsWith("- ") || line.startsWith("* ") -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Text("•", color = Accent, fontSize = 14.sp); Text(inlineMd(line.drop(2)), fontSize = 14.sp, lineHeight = 20.sp) }
                line.matches(Regex("^\\d+\\.\\s.*")) -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Text(line.substringBefore(".") + ".", color = Accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold); Text(inlineMd(line.substringAfter(". ")), fontSize = 14.sp, lineHeight = 20.sp) }
                line.startsWith("> ") -> Row { Box(Modifier.width(3.dp).height(20.dp).background(Accent, RoundedCornerShape(2.dp))); Spacer(Modifier.width(8.dp)); Text(inlineMd(line.removePrefix("> ")), fontSize = 14.sp, color = T2, fontStyle = FontStyle.Italic, lineHeight = 20.sp) }
                line.isBlank() -> Spacer(Modifier.height(4.dp))
                else -> Text(inlineMd(line), fontSize = 14.sp, lineHeight = 20.sp)
            }
        }
    }
}

private fun inlineMd(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        when {
            i < text.length - 1 && text[i] == '*' && text[i + 1] == '*' -> {
                val e = text.indexOf("**", i + 2)
                if (e > 0) {
                    withStyle(SpanStyle(color = T1, fontWeight = FontWeight.Bold)) { append(text.substring(i + 2, e)) }
                    i = e + 2
                } else {
                    withStyle(SpanStyle(color = T1)) { append(text[i].toString()) }
                    i++
                }
            }
            text[i] == '*' -> {
                val e = text.indexOf('*', i + 1)
                if (e > 0) {
                    withStyle(SpanStyle(color = T1, fontStyle = FontStyle.Italic)) { append(text.substring(i + 1, e)) }
                    i = e + 1
                } else {
                    withStyle(SpanStyle(color = T1)) { append(text[i].toString()) }
                    i++
                }
            }
            text[i] == '`' -> {
                val e = text.indexOf('`', i + 1)
                if (e > 0) {
                    withStyle(SpanStyle(color = Color(0xFFE06C75), fontFamily = FontFamily.Monospace, background = Color(0xFF1E1E2E))) { append(text.substring(i + 1, e)) }
                    i = e + 1
                } else {
                    withStyle(SpanStyle(color = T1)) { append(text[i].toString()) }
                    i++
                }
            }
            else -> {
                withStyle(SpanStyle(color = T1)) { append(text[i].toString()) }
                i++
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Bubble(msg: ChatMessage, provider: AiProvider, msgIdx: Int, speakingIdx: Int, onCopy: () -> Unit, onEdit: (() -> Unit)?, onDelete: () -> Unit, onSaveCode: (String, String) -> Unit, onRegenerate: (() -> Unit)?, onSpeak: () -> Unit, onRunScript: (() -> Unit)?) {
    val isUser = msg.role == "user"
    val context = LocalContext.current
    val accent = providerColor(provider.providerType)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start, verticalAlignment = Alignment.Top) {
        if (!isUser) {
            Box(Modifier.padding(top = 4.dp).size(28.dp).clip(CircleShape).background(accent.copy(0.15f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.AutoAwesome, null, tint = accent, modifier = Modifier.size(14.dp))
            }
            Spacer(Modifier.width(6.dp))
        }
        Column(Modifier.weight(1f, fill = false), horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
            msg.attachments.forEach { attachment ->
                if (attachment.kind in setOf("image", "generated_image", "generated_video")) {
                    renderAttachmentPreview(attachment)
                    Spacer(Modifier.height(4.dp))
                }
            }
            if (msg.attachments.any { it.kind == "file" || it.kind == "archive" } && isUser) {
                Row(Modifier.padding(bottom = 4.dp).clip(RoundedCornerShape(8.dp)).background(Card2).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AttachFile, null, tint = T2, modifier = Modifier.size(12.dp))
                    Text(Strings.fileAttachedLabel, color = T2, fontSize = 10.sp)
                }
            }
            if (isUser) {
                Box(Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)).background(Accent).combinedClickable(onClick = {}, onLongClick = onCopy).padding(12.dp)) {
                    Text(msg.content, color = Color.Black, fontSize = 14.sp, lineHeight = 20.sp)
                }
            } else {
                val content = msg.content
                if (content.contains("```")) {
                    Column(Modifier.widthIn(max = 340.dp).combinedClickable(onClick = {}, onLongClick = onCopy), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val parts = content.split("```")
                        parts.forEachIndexed { index, part ->
                            if (index % 2 == 0) {
                                if (part.isNotBlank()) Box(Modifier.clip(RoundedCornerShape(12.dp)).background(Card).padding(12.dp)) { MdText(part.trim()) }
                            } else {
                                val lines = part.lines()
                                val lang = if (lines.isNotEmpty() && lines.first().matches(Regex("^[a-zA-Z0-9_+#.-]+$"))) lines.first().lowercase() else ""
                                val code = if (lang.isNotBlank()) lines.drop(1).joinToString("\n") else part
                                CodeBlock(code.trimEnd(), lang, onCopy = {
                                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("code", code))
                                    Toast.makeText(context, Strings.copied, Toast.LENGTH_SHORT).show()
                                }, onSave = { onSaveCode(code, lang) })
                            }
                        }
                    }
                } else {
                    Box(Modifier.widthIn(max = 320.dp).clip(RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)).background(Card).combinedClickable(onClick = {}, onLongClick = onCopy).padding(12.dp)) {
                        MdText(content)
                    }
                }
            }
            Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                SBtn(Icons.Rounded.ContentCopy) { onCopy() }
                if (onEdit != null) SBtn(Icons.Rounded.Edit) { onEdit() }
                SBtn(Icons.Rounded.Delete) { onDelete() }
                if (!isUser) SBtn(if (speakingIdx == msgIdx) Icons.Rounded.StopCircle else Icons.Rounded.VolumeUp) { onSpeak() }
                if (onRegenerate != null) SBtn(Icons.Rounded.Refresh) { onRegenerate() }
                if (onRunScript != null) SBtn(Icons.Rounded.Terminal) { onRunScript() }
            }
        }
        if (isUser) {
            Spacer(Modifier.width(6.dp))
            Box(Modifier.padding(top = 4.dp).size(28.dp).clip(CircleShape).background(Accent.copy(0.15f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Chat, null, tint = Accent, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun SBtn(icon: ImageVector, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(28.dp)) { Icon(icon, null, Modifier.size(13.dp), tint = T3) }
}
