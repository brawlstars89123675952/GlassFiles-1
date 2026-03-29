package com.glassfiles.ui.screens
import com.glassfiles.data.Strings

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glassfiles.data.ai.*
import com.glassfiles.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

// Colors
private val BG = Color(0xFF09090B)
private val Card = Color(0xFF111113)
private val Card2 = Color(0xFF18181B)
private val Border = Color(0xFF27272A)
private val T1 = Color(0xFFE4E4E7)
private val T2 = Color(0xFF71717A)
private val T3 = Color(0xFF52525B)
private val Accent = Color(0xFF22C55E)
private val Bl = Color(0xFF3B82F6)

// ═══════════════════════════════════
// Main entry
// ═══════════════════════════════════

@Composable
fun AiChatScreen(onBack: () -> Unit, initialPrompt: String? = null, initialImageBase64: String? = null) {
    val context = LocalContext.current
    val historyMgr = remember { ChatHistoryManager(context) }
    var activeSessionId by remember { mutableStateOf<String?>(null) }
    var sessions by remember { mutableStateOf(historyMgr.getSessions()) }
    var consumedPrompt by remember { mutableStateOf(false) }

    // If no API key — show setup first
    var hasKey by remember { mutableStateOf(GeminiKeyStore.hasKey(context)) }
    if (!hasKey) {
        ApiKeySetupScreen(onBack = onBack, onKeySet = { hasKey = true })
        return
    }

    LaunchedEffect(initialPrompt, initialImageBase64) {
        if (initialPrompt != null && activeSessionId == null && !consumedPrompt) {
            val s = historyMgr.createSession(AiProvider.GEMINI_FLASH)
            historyMgr.saveSession(s)
            activeSessionId = s.id
            consumedPrompt = true
        }
    }

    fun refresh() { sessions = historyMgr.getSessions() }

    if (activeSessionId != null) {
        ChatView(
            sessionId = activeSessionId!!, historyMgr = historyMgr,
            onBack = { activeSessionId = null; refresh() },
            initialPrompt = if (!consumedPrompt) initialPrompt else null,
            initialImageBase64 = if (!consumedPrompt) initialImageBase64 else null
        )
    } else {
        ChatHistoryList(
            sessions = sessions,
            onNewChat = { val s = historyMgr.createSession(AiProvider.GEMINI_FLASH); historyMgr.saveSession(s); activeSessionId = s.id },
            onOpenChat = { activeSessionId = it.id },
            onDeleteChat = { historyMgr.deleteSession(it.id); refresh() },
            onDeleteAll = { historyMgr.deleteAll(); refresh() },
            onBack = onBack
        )
    }
}

// ═══════════════════════════════════
// API Key Setup — shown on first launch
// ═══════════════════════════════════

@Composable
private fun ApiKeySetupScreen(onBack: () -> Unit, onKeySet: () -> Unit) {
    val context = LocalContext.current
    var key by remember { mutableStateOf("") }
    var proxy by remember { mutableStateOf(GeminiKeyStore.getProxy(context)) }

    Column(Modifier.fillMaxSize().background(BG).padding(24.dp), verticalArrangement = Arrangement.Center) {
        Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(48.dp), tint = Accent)
        Spacer(Modifier.height(16.dp))
        Text("Gemini AI", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = T1)
        Text("Enter your API key to get started", fontSize = 14.sp, color = T2, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(32.dp))

        Text("API Key", fontSize = 13.sp, color = T2, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        BasicTextField(key, { key = it },
            Modifier.fillMaxWidth().background(Card, RoundedCornerShape(10.dp)).border(1.dp, Border, RoundedCornerShape(10.dp)).padding(14.dp),
            textStyle = TextStyle(T1, 14.sp, fontFamily = FontFamily.Monospace),
            cursorBrush = SolidColor(Accent), singleLine = true,
            decorationBox = { inner -> if (key.isEmpty()) Text("AIza...", color = T3, fontSize = 14.sp, fontFamily = FontFamily.Monospace); inner() })

        Spacer(Modifier.height(8.dp))
        Text("Get your key at aistudio.google.com/apikey", fontSize = 12.sp, color = T3)

        Spacer(Modifier.height(24.dp))
        Text("Proxy (optional)", fontSize = 13.sp, color = T2, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        BasicTextField(proxy, { proxy = it },
            Modifier.fillMaxWidth().background(Card, RoundedCornerShape(10.dp)).border(1.dp, Border, RoundedCornerShape(10.dp)).padding(14.dp),
            textStyle = TextStyle(T1, 14.sp), cursorBrush = SolidColor(Accent), singleLine = true,
            decorationBox = { inner -> if (proxy.isEmpty()) Text("https://proxy.example.com/v1beta/models", color = T3, fontSize = 13.sp); inner() })
        Spacer(Modifier.height(8.dp))
        Text("Use if Gemini is blocked in your region", fontSize = 12.sp, color = T3)

        Spacer(Modifier.height(32.dp))
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (key.length > 10) Accent else Border)
            .clickable(enabled = key.length > 10) {
                GeminiKeyStore.saveKey(context, key)
                if (proxy.isNotBlank()) GeminiKeyStore.saveProxy(context, proxy)
                onKeySet()
            }.padding(14.dp), contentAlignment = Alignment.Center) {
            Text("Continue", color = if (key.length > 10) Color.Black else T3, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().clickable { onBack() }.padding(8.dp), contentAlignment = Alignment.Center) {
            Text("Back", color = T2, fontSize = 14.sp)
        }
    }
}

// ═══════════════════════════════════
// Chat History
// ═══════════════════════════════════

@Composable
private fun ChatHistoryList(
    sessions: List<ChatSession>, onNewChat: () -> Unit, onOpenChat: (ChatSession) -> Unit,
    onDeleteChat: (ChatSession) -> Unit, onDeleteAll: () -> Unit, onBack: () -> Unit
) {
    val sdf = remember { SimpleDateFormat("dd.MM.yy HH:mm", Locale.getDefault()) }
    Column(Modifier.fillMaxSize().background(BG)) {
        // Top bar
        Row(Modifier.fillMaxWidth().padding(top = 48.dp, start = 4.dp, end = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, Modifier.size(20.dp), tint = T1) }
            Text("AI", color = T1, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.weight(1f))
            if (sessions.isNotEmpty()) IconButton(onClick = onDeleteAll) { Icon(Icons.Rounded.DeleteSweep, null, Modifier.size(20.dp), tint = T2) }
        }

        if (sessions.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(48.dp), tint = Accent)
                    Text("No chats yet", color = T2, fontSize = 16.sp)
                    Text("Start a conversation with Gemini", color = T3, fontSize = 14.sp)
                }
            }
        } else {
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(sessions) { s ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Card)
                        .clickable { onOpenChat(s) }.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(40.dp).background(Card2, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Chat, null, Modifier.size(20.dp), tint = Accent)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(s.title, color = T1, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(sdf.format(Date(s.updatedAt)), color = T3, fontSize = 11.sp)
                                Text("${s.messages.size} msgs", color = T3, fontSize = 11.sp)
                                Text(try { AiProvider.valueOf(s.provider).label } catch (_: Exception) { s.provider }, color = Accent, fontSize = 11.sp)
                            }
                        }
                        IconButton(onClick = { onDeleteChat(s) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Rounded.Close, null, Modifier.size(16.dp), tint = T3)
                        }
                    }
                }
            }
        }

        Box(Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(14.dp)).background(Accent)
            .clickable(onClick = onNewChat).padding(14.dp), contentAlignment = Alignment.Center) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Add, null, Modifier.size(20.dp), tint = Color.Black)
                Text("New Chat", color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ═══════════════════════════════════
// Chat View
// ═══════════════════════════════════

@Composable
private fun ChatView(sessionId: String, historyMgr: ChatHistoryManager, onBack: () -> Unit,
    initialPrompt: String? = null, initialImageBase64: String? = null) {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    val session = remember { historyMgr.getSession(sessionId) }

    var messages by remember { mutableStateOf(session?.messages ?: emptyList()) }
    var input by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var currentResponse by remember { mutableStateOf("") }
    var provider by remember { mutableStateOf(try { AiProvider.valueOf(session?.provider ?: "GEMINI_FLASH") } catch (_: Exception) { AiProvider.GEMINI_FLASH }) }
    var showModelPicker by remember { mutableStateOf(false) }
    var attachedImage by remember { mutableStateOf<String?>(null) }
    var attachedFile by remember { mutableStateOf<Pair<String, String>?>(null) }
    val listState = rememberLazyListState()
    var geminiKey by remember { mutableStateOf(GeminiKeyStore.getKey(context)) }
    var proxyUrl by remember { mutableStateOf(GeminiKeyStore.getProxy(context)) }
    var showSettings by remember { mutableStateOf(false) }
    var autoSent by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        try {
            val mime = context.contentResolver.getType(uri) ?: ""
            val isImage = mime.startsWith("image/")
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            val name = cursor?.use { c -> if (c.moveToFirst()) { val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME); if (idx >= 0) c.getString(idx) else null } else null } ?: uri.lastPathSegment ?: "file"
            if (isImage) {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val file = File(context.cacheDir, "ai_img.jpg"); file.outputStream().use { out -> stream.copyTo(out) }
                    attachedImage = AiManager.encodeImage(file)
                }
            } else {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val text = stream.bufferedReader().readText()
                    attachedFile = Pair(name, if (text.length > 8000) text.take(8000) + "\n...[truncated]" else text)
                }
            }
        } catch (e: Exception) { Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show() }
    }

    fun save(msgs: List<ChatMessage>) {
        val title = historyMgr.generateTitle(msgs)
        historyMgr.saveSession(ChatSession(id = sessionId, title = title, provider = provider.name,
            messages = msgs, createdAt = session?.createdAt ?: System.currentTimeMillis(), updatedAt = System.currentTimeMillis()))
    }

    LaunchedEffect(messages.size, currentResponse) {
        val total = messages.size + if (currentResponse.isNotEmpty()) 1 else 0
        if (total > 0) listState.animateScrollToItem(total - 1)
    }

    LaunchedEffect(initialPrompt) {
        if (initialPrompt != null && !autoSent && messages.isEmpty()) {
            autoSent = true
            val userMsg = ChatMessage("user", initialPrompt, initialImageBase64)
            messages = messages + userMsg; isLoading = true
            try {
                val response = AiManager.chat(provider, listOf(userMsg), geminiKey, "", proxyUrl) { chunk -> currentResponse += chunk }
                messages = messages + ChatMessage("assistant", response); currentResponse = ""; save(messages)
            } catch (e: Exception) { messages = messages + ChatMessage("assistant", "Error: ${e.message}") }
            isLoading = false
        }
    }

    fun send() {
        var text = input.trim()
        if (text.isEmpty() && attachedImage == null && attachedFile == null) return
        if (isLoading) return
        if (attachedFile != null) text = "File: ${attachedFile!!.first}\n```\n${attachedFile!!.second}\n```\n\n$text"
        if (text.isEmpty()) text = "What is in this image?"
        val userMsg = ChatMessage("user", text, attachedImage)
        input = ""; currentResponse = ""; attachedImage = null; attachedFile = null
        messages = messages + userMsg; isLoading = true
        scope.launch {
            try {
                val response = AiManager.chat(provider, messages, geminiKey, "", proxyUrl) { chunk -> currentResponse += chunk }
                messages = messages + ChatMessage("assistant", response); currentResponse = ""; save(messages)
            } catch (e: Exception) { messages = messages + ChatMessage("assistant", "Error: ${e.message}"); currentResponse = ""; save(messages) }
            isLoading = false
        }
    }

    fun copyText(text: String) {
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("ai", text))
        Toast.makeText(context, Strings.copied, Toast.LENGTH_SHORT).show()
    }

    Column(Modifier.fillMaxSize().background(BG).imePadding()) {
        // Top bar
        Row(Modifier.fillMaxWidth().background(Card).padding(top = 48.dp, start = 4.dp, end = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { save(messages); onBack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, Modifier.size(20.dp), tint = T1) }
            Text("AI", color = T1, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))

            // Model picker button
            Row(Modifier.clip(RoundedCornerShape(8.dp)).background(Card2).border(1.dp, Border, RoundedCornerShape(8.dp))
                .clickable { showModelPicker = true }.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(Accent))
                Text(provider.label.replace("Gemini ", ""), color = T1, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Icon(Icons.Rounded.KeyboardArrowDown, null, Modifier.size(14.dp), tint = T2)
            }
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = { showSettings = true }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Rounded.Settings, null, Modifier.size(18.dp), tint = T2)
            }
        }

        // Messages
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (messages.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(48.dp), tint = Accent)
                        Text("Gemini AI", color = T1, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("${provider.label} — ${provider.desc}", color = T3, fontSize = 13.sp)
                        Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Explain this code", "Help with terminal", "Analyze file structure").forEach { q ->
                                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Card).border(1.dp, Border, RoundedCornerShape(10.dp))
                                    .clickable { input = q }.padding(12.dp)) {
                                    Text(q, color = T2, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }
            items(messages) { msg -> Bubble(msg) { copyText(msg.content) } }
            if (currentResponse.isNotEmpty()) { item { Bubble(ChatMessage("assistant", currentResponse + "\u2588")) {} } }
            if (isLoading && currentResponse.isEmpty()) {
                item {
                    Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(Modifier.size(16.dp), color = Accent, strokeWidth = 2.dp)
                        Text("Thinking...", color = T2, fontSize = 13.sp)
                    }
                }
            }
        }

        // Attachments
        if (attachedImage != null || attachedFile != null) {
            Row(Modifier.fillMaxWidth().background(Card).padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (attachedImage != null) {
                    Icon(Icons.Rounded.Image, null, Modifier.size(18.dp), tint = Accent)
                    Text("Photo attached", color = T2, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = { attachedImage = null }, modifier = Modifier.size(24.dp)) { Icon(Icons.Rounded.Close, null, Modifier.size(14.dp), tint = T2) }
                }
                if (attachedFile != null) {
                    Icon(Icons.Rounded.Description, null, Modifier.size(18.dp), tint = Bl)
                    Text(attachedFile!!.first, color = T2, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    IconButton(onClick = { attachedFile = null }, modifier = Modifier.size(24.dp)) { Icon(Icons.Rounded.Close, null, Modifier.size(14.dp), tint = T2) }
                }
            }
        }

        // Input bar
        Row(Modifier.fillMaxWidth().background(Card).padding(8.dp),
            verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            IconButton(onClick = { filePicker.launch("*/*") }, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Rounded.AttachFile, null, Modifier.size(20.dp), tint = T2)
            }
            BasicTextField(input, { input = it },
                Modifier.weight(1f).background(Card2, RoundedCornerShape(20.dp)).border(1.dp, Border, RoundedCornerShape(20.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
                textStyle = TextStyle(T1, 15.sp), cursorBrush = SolidColor(Accent),
                decorationBox = { inner -> if (input.isEmpty()) Text("Message...", color = T3, fontSize = 15.sp); inner() })
            val canSend = (input.isNotBlank() || attachedImage != null || attachedFile != null) && !isLoading
            Box(Modifier.size(44.dp).clip(CircleShape).background(if (canSend) Accent else Card2)
                .clickable(enabled = canSend) { send() }, contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Rounded.Send, null, Modifier.size(20.dp), tint = if (canSend) Color.Black else T3)
            }
        }
    }

    // Model Picker
    if (showModelPicker) {
        AlertDialog(onDismissRequest = { showModelPicker = false }, containerColor = Card,
            title = { Text("Select Model", fontWeight = FontWeight.Bold, color = T1, fontSize = 18.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AiProvider.entries.forEach { p ->
                        val sel = provider == p
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(if (sel) Accent.copy(0.1f) else Color.Transparent)
                            .border(1.dp, if (sel) Accent.copy(0.3f) else Border, RoundedCornerShape(10.dp))
                            .clickable { provider = p; showModelPicker = false }.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(Modifier.size(36.dp).background(if (sel) Accent.copy(0.15f) else Card2, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(18.dp), tint = if (sel) Accent else T2)
                            }
                            Column(Modifier.weight(1f)) {
                                Text(p.label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = if (sel) Accent else T1)
                                Text(p.desc, fontSize = 11.sp, color = T3)
                            }
                            if (p.supportsVision) {
                                Box(Modifier.background(Card2, RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 2.dp)) {
                                    Text("Vision", fontSize = 9.sp, color = T2)
                                }
                            }
                            if (sel) Icon(Icons.Rounded.Check, null, Modifier.size(16.dp), tint = Accent)
                        }
                    }
                }
            }, confirmButton = {})
    }

    // Settings
    if (showSettings) {
        var keyInput by remember { mutableStateOf(geminiKey) }
        var proxyInput by remember { mutableStateOf(proxyUrl) }
        AlertDialog(onDismissRequest = { showSettings = false }, containerColor = Card,
            title = { Text("AI Settings", fontWeight = FontWeight.Bold, color = T1) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Gemini API Key", color = T2, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    OutlinedTextField(keyInput, { keyInput = it }, singleLine = true, placeholder = { Text("AIza...", color = T3) },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = T1, unfocusedTextColor = T1,
                            focusedBorderColor = Accent, unfocusedBorderColor = Border, cursorColor = Accent),
                        modifier = Modifier.fillMaxWidth())
                    Text("Get key: aistudio.google.com/apikey", color = T3, fontSize = 11.sp)

                    HorizontalDivider(color = Border)

                    Text("Proxy URL (optional)", color = T2, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    OutlinedTextField(proxyInput, { proxyInput = it }, singleLine = true,
                        placeholder = { Text("https://proxy/v1beta/models", color = T3, fontSize = 13.sp) },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = T1, unfocusedTextColor = T1,
                            focusedBorderColor = Accent, unfocusedBorderColor = Border, cursorColor = Accent),
                        modifier = Modifier.fillMaxWidth())
                    if (proxyUrl.isNotBlank()) Text("Active: ${proxyUrl.take(35)}...", color = Accent, fontSize = 11.sp)
                }
            },
            confirmButton = { TextButton(onClick = {
                GeminiKeyStore.saveKey(context, keyInput); geminiKey = keyInput.trim()
                GeminiKeyStore.saveProxy(context, proxyInput); proxyUrl = proxyInput.trim()
                showSettings = false
                Toast.makeText(context, Strings.done, Toast.LENGTH_SHORT).show()
            }) { Text("Save", color = Accent) } },
            dismissButton = { TextButton(onClick = { showSettings = false }) { Text("Cancel", color = T2) } })
    }
}

// ═══════════════════════════════════
// Message Bubble
// ═══════════════════════════════════

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Bubble(msg: ChatMessage, onCopy: () -> Unit) {
    val isUser = msg.role == "user"
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
        if (msg.imageBase64 != null) {
            val bmp = remember(msg.imageBase64) {
                try { val bytes = android.util.Base64.decode(msg.imageBase64, android.util.Base64.NO_WRAP)
                    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) } catch (_: Exception) { null }
            }
            if (bmp != null) {
                androidx.compose.foundation.Image(bitmap = bmp.asImageBitmap(), contentDescription = null,
                    modifier = Modifier.widthIn(max = 240.dp).heightIn(max = 180.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                Spacer(Modifier.height(4.dp))
            }
        }

        Box(Modifier.widthIn(max = 320.dp)
            .clip(RoundedCornerShape(16.dp, 16.dp, if (isUser) 4.dp else 16.dp, if (isUser) 16.dp else 4.dp))
            .background(if (isUser) Accent else Card)
            .combinedClickable(onClick = {}, onLongClick = onCopy).padding(12.dp)) {
            val content = msg.content
            if (content.contains("```")) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    content.split("```").forEachIndexed { i, part ->
                        if (i % 2 == 0) { if (part.isNotBlank()) Text(part.trim(), color = if (isUser) Color.Black else T1, fontSize = 14.sp, lineHeight = 20.sp) }
                        else {
                            val code = part.lines().let { l -> if (l.isNotEmpty() && l.first().matches(Regex("^[a-z]+$"))) l.drop(1) else l }.joinToString("\n")
                            Box(Modifier.fillMaxWidth().background(BG, RoundedCornerShape(6.dp)).padding(8.dp)) {
                                Text(code, color = Color(0xFFA5D6FF), fontSize = 12.sp, fontFamily = FontFamily.Monospace, lineHeight = 18.sp)
                            }
                        }
                    }
                }
            } else Text(content, color = if (isUser) Color.Black else T1, fontSize = 14.sp, lineHeight = 20.sp)
        }
    }
}
