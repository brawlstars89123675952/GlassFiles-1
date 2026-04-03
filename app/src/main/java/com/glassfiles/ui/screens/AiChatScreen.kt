package com.glassfiles.ui.screens
import com.glassfiles.data.Strings
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Environment
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glassfiles.data.ai.*
import com.glassfiles.ui.theme.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

private val BG = Color(0xFF09090B); private val Card = Color(0xFF111113); private val Card2 = Color(0xFF18181B)
private val Border = Color(0xFF27272A); private val T1 = Color(0xFFE4E4E7); private val T2 = Color(0xFF71717A); private val T3 = Color(0xFF52525B)
private val Accent = Color(0xFF22C55E); private val Bl = Color(0xFF3B82F6); private val QwenColor = Color(0xFF6366F1)
private val CodeBg = Color(0xFF0D1117); private val CodeBorder = Color(0xFF30363D); private val CodeKw = Color(0xFFFF7B72)
private val CodeStr = Color(0xFFA5D6A7); private val CodeCmt = Color(0xFF8B949E); private val CodeNum = Color(0xFF79C0FF)
private val CodeTy = Color(0xFFFFA657); private val CodeFn = Color(0xFFD2A8FF); private val CodeDef = Color(0xFFE6EDF3)
private val kwSet = setOf("fun","val","var","class","object","interface","enum","when","if","else","for","while","return","import","package","private","public","protected","internal","override","suspend","data","sealed","abstract","open","companion","init","try","catch","throw","finally","is","as","in","by","null","true","false","this","super","it","break","continue","const","lateinit","function","let","def","self","None","True","False","async","await","yield","from","with","lambda","elif","except","raise","static","final","void","int","String","boolean","float","double","long","new","delete","typeof","instanceof","export","default","switch","case","struct","impl","fn","pub","mut","use","mod","func","go","chan","select","defer","range","type","map","make","println","print")

@Composable
fun AiChatScreen(onBack: () -> Unit, initialPrompt: String? = null, initialImageBase64: String? = null) {
    val context = LocalContext.current; val historyMgr = remember { ChatHistoryManager(context) }
    var activeSessionId by remember { mutableStateOf<String?>(null) }; var sessions by remember { mutableStateOf(historyMgr.getSessions()) }
    var consumedPrompt by remember { mutableStateOf(false) }
    var hasKey by remember { mutableStateOf(GeminiKeyStore.hasKey(context) || GeminiKeyStore.hasQwenKey(context)) }
    if (!hasKey) { ApiKeySetupScreen(onBack = onBack, onKeySet = { hasKey = true }); return }
    LaunchedEffect(initialPrompt, initialImageBase64) {
        if (initialPrompt != null && activeSessionId == null && !consumedPrompt) {
            val dp = if (GeminiKeyStore.hasKey(context)) AiProvider.GEMINI_FLASH else AiProvider.QWEN_PLUS
            val s = historyMgr.createSession(dp); historyMgr.saveSession(s); activeSessionId = s.id; consumedPrompt = true
        }
    }
    fun refresh() { sessions = historyMgr.getSessions() }
    if (activeSessionId != null) {
        ChatView(sessionId = activeSessionId!!, historyMgr = historyMgr, onBack = { activeSessionId = null; refresh() },
            initialPrompt = if (!consumedPrompt) initialPrompt else null, initialImageBase64 = if (!consumedPrompt) initialImageBase64 else null)
    } else {
        val dp = if (GeminiKeyStore.hasKey(context)) AiProvider.GEMINI_FLASH else AiProvider.QWEN_PLUS
        ChatHistoryList(sessions, { val s = historyMgr.createSession(dp); historyMgr.saveSession(s); activeSessionId = s.id },
            { activeSessionId = it.id }, { historyMgr.deleteSession(it.id); refresh() }, { historyMgr.deleteAll(); refresh() }, onBack)
    }
}

@Composable
private fun ApiKeySetupScreen(onBack: () -> Unit, onKeySet: () -> Unit) {
    val context = LocalContext.current; var geminiKey by remember { mutableStateOf("") }; var qwenKey by remember { mutableStateOf("") }; var proxy by remember { mutableStateOf(GeminiKeyStore.getProxy(context)) }
    Column(Modifier.fillMaxSize().background(BG).verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.Center) {
        Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(48.dp), tint = Accent); Spacer(Modifier.height(16.dp))
        Text("AI Assistant", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = T1)
        Text("Enter at least one API key", fontSize = 14.sp, color = T2, modifier = Modifier.padding(top = 4.dp)); Spacer(Modifier.height(32.dp))
        SectionLabel("Gemini", Accent); Spacer(Modifier.height(6.dp)); InputField(geminiKey, { geminiKey = it }, "AIza...", mono = true)
        Text("aistudio.google.com/apikey", fontSize = 12.sp, color = T3, modifier = Modifier.padding(top = 4.dp)); Spacer(Modifier.height(6.dp))
        InputField(proxy, { proxy = it }, "Proxy (optional)"); Spacer(Modifier.height(24.dp))
        SectionLabel("Qwen (Alibaba Cloud)", QwenColor); Spacer(Modifier.height(6.dp)); InputField(qwenKey, { qwenKey = it }, "sk-...", mono = true)
        Text("bailian.console.alibabacloud.com", fontSize = 12.sp, color = T3, modifier = Modifier.padding(top = 4.dp)); Spacer(Modifier.height(32.dp))
        val ok = geminiKey.length > 10 || qwenKey.length > 10
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (ok) Accent else Border).clickable(enabled = ok) {
            if (geminiKey.length > 10) GeminiKeyStore.saveKey(context, geminiKey); if (proxy.isNotBlank()) GeminiKeyStore.saveProxy(context, proxy)
            if (qwenKey.length > 10) GeminiKeyStore.saveQwenKey(context, qwenKey); onKeySet()
        }.padding(14.dp), contentAlignment = Alignment.Center) { Text("Continue", color = if (ok) Color.Black else T3, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
        Spacer(Modifier.height(12.dp)); Box(Modifier.fillMaxWidth().clickable { onBack() }.padding(8.dp), contentAlignment = Alignment.Center) { Text("Back", color = T2, fontSize = 14.sp) }
    }
}

@Composable
private fun ChatHistoryList(sessions: List<ChatSession>, onNew: () -> Unit, onOpen: (ChatSession) -> Unit, onDel: (ChatSession) -> Unit, onDelAll: () -> Unit, onBack: () -> Unit) {
    val sdf = remember { SimpleDateFormat("dd.MM.yy HH:mm", Locale.getDefault()) }
    Column(Modifier.fillMaxSize().background(BG)) {
        Row(Modifier.fillMaxWidth().padding(top = 48.dp, start = 4.dp, end = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, Modifier.size(20.dp), tint = T1) }
            Text("AI", color = T1, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.weight(1f))
            if (sessions.isNotEmpty()) IconButton(onClick = onDelAll) { Icon(Icons.Rounded.DeleteSweep, null, Modifier.size(20.dp), tint = T2) }
        }
        if (sessions.isEmpty()) { Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(48.dp), tint = Accent); Text("No chats yet", color = T2, fontSize = 16.sp); Text("Gemini + Qwen AI", color = T3, fontSize = 14.sp)
            } }
        } else { LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(sessions) { s -> val pc = try { val p = AiProvider.valueOf(s.provider); if (p.isQwen) QwenColor else Accent } catch (_: Exception) { Accent }
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Card).clickable { onOpen(s) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(40.dp).background(Card2, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Chat, null, Modifier.size(20.dp), tint = pc) }
                    Column(Modifier.weight(1f)) { Text(s.title, color = T1, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Text(sdf.format(Date(s.updatedAt)), color = T3, fontSize = 11.sp); Text("${s.messages.size} msgs", color = T3, fontSize = 11.sp)
                            Text(try { AiProvider.valueOf(s.provider).label } catch (_: Exception) { s.provider }, color = pc, fontSize = 11.sp) } }
                    IconButton(onClick = { onDel(s) }, modifier = Modifier.size(32.dp)) { Icon(Icons.Rounded.Close, null, Modifier.size(16.dp), tint = T3) }
                } } } }
        Box(Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(14.dp)).background(Accent).clickable(onClick = onNew).padding(14.dp), contentAlignment = Alignment.Center) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.Add, null, Modifier.size(20.dp), tint = Color.Black); Text("New Chat", color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) } }
    }
}

@Composable
private fun ChatView(sessionId: String, historyMgr: ChatHistoryManager, onBack: () -> Unit, initialPrompt: String? = null, initialImageBase64: String? = null) {
    val context = LocalContext.current; val scope = rememberCoroutineScope(); val session = remember { historyMgr.getSession(sessionId) }
    var messages by remember { mutableStateOf(session?.messages ?: emptyList()) }; var input by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }; var currentResponse by remember { mutableStateOf("") }
    var provider by remember { mutableStateOf(try { AiProvider.valueOf(session?.provider ?: "GEMINI_FLASH") } catch (_: Exception) { AiProvider.GEMINI_FLASH }) }
    var showModelPicker by remember { mutableStateOf(false) }; var attachedImage by remember { mutableStateOf<String?>(null) }
    var attachedFile by remember { mutableStateOf<Pair<String, String>?>(null) }; var attachedZip by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState(); var geminiKey by remember { mutableStateOf(GeminiKeyStore.getKey(context)) }
    var qwenKey by remember { mutableStateOf(GeminiKeyStore.getQwenKey(context)) }; var qwenRegion by remember { mutableStateOf(GeminiKeyStore.getQwenRegion(context)) }
    var proxyUrl by remember { mutableStateOf(GeminiKeyStore.getProxy(context)) }; var showSettings by remember { mutableStateOf(false) }
    var autoSent by remember { mutableStateOf(false) }; var currentJob by remember { mutableStateOf<Job?>(null) }
    var editIdx by remember { mutableIntStateOf(-1) }; var editTxt by remember { mutableStateOf("") }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        try { val mime = context.contentResolver.getType(uri) ?: ""; val cursor = context.contentResolver.query(uri, null, null, null, null)
            val name = cursor?.use { c -> if (c.moveToFirst()) { val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME); if (idx >= 0) c.getString(idx) else null } else null } ?: uri.lastPathSegment ?: "file"
            val ext = name.substringAfterLast(".", "").lowercase()
            when { mime.startsWith("image/") -> { context.contentResolver.openInputStream(uri)?.use { s -> val f = File(context.cacheDir, "ai_img.jpg"); f.outputStream().use { o -> s.copyTo(o) }; attachedImage = AiManager.encodeImage(f) } }
                ext in listOf("zip", "jar") -> { val tmp = File(context.cacheDir, "ai_zip_${System.currentTimeMillis()}.$ext"); context.contentResolver.openInputStream(uri)?.use { s -> tmp.outputStream().use { o -> s.copyTo(o) } }
                    val entries = AiManager.extractZipForAi(tmp.absolutePath, context); attachedZip = AiManager.formatZipContents(entries); attachedFile = Pair(name, "${entries.size} files extracted"); tmp.delete() }
                else -> { context.contentResolver.openInputStream(uri)?.use { s -> val t = s.bufferedReader().readText(); attachedFile = Pair(name, if (t.length > 10000) t.take(10000) + "\n...[truncated]" else t) } } }
        } catch (e: Exception) { Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show() } }

    fun save(msgs: List<ChatMessage>) { historyMgr.saveSession(ChatSession(id = sessionId, title = historyMgr.generateTitle(msgs), provider = provider.name, messages = msgs, createdAt = session?.createdAt ?: System.currentTimeMillis(), updatedAt = System.currentTimeMillis())) }
    fun clip(t: String) { (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("ai", t)); Toast.makeText(context, Strings.copied, Toast.LENGTH_SHORT).show() }
    fun exportChat() { val sb = StringBuilder("# AI Chat\n**${provider.label}** — ${SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())}\n\n---\n\n")
        messages.forEach { m -> sb.append(if (m.role == "user") "**You:**\n" else "**AI:**\n").append(m.content).append("\n\n") }
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "GlassFiles_AI"); dir.mkdirs()
        val f = File(dir, "chat_${System.currentTimeMillis()}.md"); f.writeText(sb.toString()); Toast.makeText(context, "Saved: ${f.name}", Toast.LENGTH_SHORT).show() }
    fun saveCode(code: String, lang: String) { val ext = when (lang) { "kotlin","kt" -> "kt"; "java" -> "java"; "python","py" -> "py"; "javascript","js" -> "js"; "typescript","ts" -> "ts"; "html" -> "html"; "css" -> "css"; "xml" -> "xml"; "json" -> "json"; "yaml","yml" -> "yml"; "bash","sh" -> "sh"; "sql" -> "sql"; "go" -> "go"; "rust","rs" -> "rs"; "swift" -> "swift"; "c" -> "c"; "cpp" -> "cpp"; else -> "txt" }
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "GlassFiles_AI"); dir.mkdirs()
        val f = File(dir, "code_${System.currentTimeMillis()}.$ext"); f.writeText(code); Toast.makeText(context, "Saved: ${f.name}", Toast.LENGTH_SHORT).show() }

    fun doSend(text: String, image: String? = null, fc: String? = null) { if (isLoading) return; val um = ChatMessage("user", text, image, fc); currentResponse = ""; messages = messages + um; isLoading = true
        currentJob = scope.launch { try { val r = AiManager.chat(provider, messages, geminiKey, "", proxyUrl, qwenKey, qwenRegion) { currentResponse += it }; messages = messages + ChatMessage("assistant", r); currentResponse = ""; save(messages)
        } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException && currentResponse.isNotBlank()) { messages = messages + ChatMessage("assistant", currentResponse + "\n\n*(stopped)*"); currentResponse = "" } else { messages = messages + ChatMessage("assistant", "Error: ${e.message}"); currentResponse = "" }; save(messages) }; isLoading = false; currentJob = null } }

    fun send() { var t = input.trim(); if (t.isEmpty() && attachedImage == null && attachedFile == null && attachedZip == null) return
        val fc = when { attachedZip != null -> attachedZip; attachedFile != null -> "File: ${attachedFile!!.first}\n```\n${attachedFile!!.second}\n```"; else -> null }
        if (t.isEmpty() && attachedImage != null) t = "What is in this image?"; if (t.isEmpty() && fc != null) t = "Analyze this file"
        input = ""; val img = attachedImage; val fcc = fc; attachedImage = null; attachedFile = null; attachedZip = null; doSend(t, img, fcc) }

    fun regenerate() { if (messages.isEmpty() || isLoading) return; val m = messages.toMutableList(); if (m.last().role == "assistant") m.removeLast(); messages = m; isLoading = true; currentResponse = ""
        currentJob = scope.launch { try { val r = AiManager.chat(provider, messages, geminiKey, "", proxyUrl, qwenKey, qwenRegion) { currentResponse += it }; messages = messages + ChatMessage("assistant", r); currentResponse = ""; save(messages)
        } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException && currentResponse.isNotBlank()) { messages = messages + ChatMessage("assistant", currentResponse + "\n\n*(stopped)*") } else { messages = messages + ChatMessage("assistant", "Error: ${e.message}") }; currentResponse = ""; save(messages) }; isLoading = false; currentJob = null } }

    fun confirmEdit() { if (editIdx < 0) return; val nm = messages.take(editIdx) + ChatMessage("user", editTxt, messages[editIdx].imageBase64, messages[editIdx].fileContent); messages = nm; editIdx = -1; editTxt = ""
        isLoading = true; currentResponse = ""; currentJob = scope.launch { try { val r = AiManager.chat(provider, messages, geminiKey, "", proxyUrl, qwenKey, qwenRegion) { currentResponse += it }; messages = messages + ChatMessage("assistant", r); currentResponse = ""; save(messages) } catch (e: Exception) { messages = messages + ChatMessage("assistant", "Error: ${e.message}"); currentResponse = ""; save(messages) }; isLoading = false; currentJob = null } }

    LaunchedEffect(messages.size, currentResponse) { val total = messages.size + if (currentResponse.isNotEmpty()) 1 else 0; if (total > 0) listState.animateScrollToItem(total - 1) }
    LaunchedEffect(initialPrompt) { if (initialPrompt != null && !autoSent && messages.isEmpty()) { autoSent = true; doSend(initialPrompt, initialImageBase64, null) } }

    if (editIdx >= 0) AlertDialog(onDismissRequest = { editIdx = -1 }, containerColor = Card,
        title = { Text("Edit message", fontWeight = FontWeight.Bold, color = T1) },
        text = { OutlinedTextField(editTxt, { editTxt = it }, modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp), maxLines = 8,
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = T1, unfocusedTextColor = T1, focusedBorderColor = Accent, unfocusedBorderColor = Border, cursorColor = Accent)) },
        confirmButton = { TextButton(onClick = { confirmEdit() }) { Text("Send", color = Accent) } },
        dismissButton = { TextButton(onClick = { editIdx = -1 }) { Text("Cancel", color = T2) } })

    Column(Modifier.fillMaxSize().background(BG).imePadding()) {
        // Top bar
        Row(Modifier.fillMaxWidth().background(Card).padding(top = 48.dp, start = 4.dp, end = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { save(messages); onBack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, Modifier.size(20.dp), tint = T1) }
            Text("AI", color = T1, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = { exportChat() }, modifier = Modifier.size(36.dp)) { Icon(Icons.Rounded.FileDownload, null, Modifier.size(18.dp), tint = T2) }
            val pc = if (provider.isQwen) QwenColor else Accent
            Row(Modifier.clip(RoundedCornerShape(8.dp)).background(Card2).border(1.dp, Border, RoundedCornerShape(8.dp)).clickable { showModelPicker = true }.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(pc)); Text(provider.label.removePrefix("Gemini ").removePrefix("Qwen "), color = T1, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Icon(Icons.Rounded.KeyboardArrowDown, null, Modifier.size(14.dp), tint = T2) }
            Spacer(Modifier.width(4.dp)); IconButton(onClick = { showSettings = true }, modifier = Modifier.size(36.dp)) { Icon(Icons.Rounded.Settings, null, Modifier.size(18.dp), tint = T2) }
        }
        // Messages
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (messages.isEmpty()) { item { Column(Modifier.fillMaxWidth().padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(48.dp), tint = if (provider.isQwen) QwenColor else Accent)
                Text(provider.label, color = T1, fontSize = 20.sp, fontWeight = FontWeight.Bold); Text(provider.desc, color = T3, fontSize = 13.sp)
                if (provider.supportsFiles) Text("Supports: files, ZIP, images", color = T3, fontSize = 12.sp)
                Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { listOf("Explain this code", "Analyze ZIP archive", "What's in this image?").forEach { q ->
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Card).border(1.dp, Border, RoundedCornerShape(10.dp)).clickable { input = q }.padding(12.dp)) { Text(q, color = T2, fontSize = 13.sp) } } }
            } } }
            items(messages.size) { idx -> val msg = messages[idx]; val isLast = idx == messages.lastIndex
                Bubble(msg, provider, onCopy = { clip(msg.content) }, onEdit = if (msg.role == "user") {{ editIdx = idx; editTxt = msg.content }} else null,
                    onDelete = { val m = messages.toMutableList(); m.removeAt(idx); messages = m; save(messages) }, onSaveCode = { c, l -> saveCode(c, l) },
                    onRegenerate = if (isLast && msg.role == "assistant" && !isLoading) {{ regenerate() }} else null) }
            if (currentResponse.isNotEmpty()) { item { Bubble(ChatMessage("assistant", currentResponse + "\u2588"), provider, {}, null, {}, { _, _ -> }, null) } }
            if (isLoading && currentResponse.isEmpty()) { item { Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(Modifier.size(16.dp), color = if (provider.isQwen) QwenColor else Accent, strokeWidth = 2.dp); Text("Thinking...", color = T2, fontSize = 13.sp) } } }
        }
        // Attachments
        if (attachedImage != null || attachedFile != null) { Row(Modifier.fillMaxWidth().background(Card).padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (attachedImage != null) { Icon(Icons.Rounded.Image, null, Modifier.size(18.dp), tint = Accent); Text("Photo", color = T2, fontSize = 12.sp, modifier = Modifier.weight(1f)); IconButton(onClick = { attachedImage = null }, modifier = Modifier.size(24.dp)) { Icon(Icons.Rounded.Close, null, Modifier.size(14.dp), tint = T2) } }
            if (attachedFile != null) { Icon(if (attachedZip != null) Icons.Rounded.FolderZip else Icons.Rounded.Description, null, Modifier.size(18.dp), tint = if (attachedZip != null) QwenColor else Bl)
                Column(Modifier.weight(1f)) { Text(attachedFile!!.first, color = T2, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis); if (attachedZip != null) Text(attachedFile!!.second, color = T3, fontSize = 10.sp) }
                IconButton(onClick = { attachedFile = null; attachedZip = null }, modifier = Modifier.size(24.dp)) { Icon(Icons.Rounded.Close, null, Modifier.size(14.dp), tint = T2) } } } }
        // Input
        Row(Modifier.fillMaxWidth().background(Card).padding(8.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            IconButton(onClick = { filePicker.launch("*/*") }, modifier = Modifier.size(44.dp)) { Icon(Icons.Rounded.AttachFile, null, Modifier.size(20.dp), tint = T2) }
            BasicTextField(input, { input = it }, Modifier.weight(1f).background(Card2, RoundedCornerShape(20.dp)).border(1.dp, Border, RoundedCornerShape(20.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
                textStyle = TextStyle(T1, 15.sp), cursorBrush = SolidColor(if (provider.isQwen) QwenColor else Accent), decorationBox = { i -> if (input.isEmpty()) Text("Message...", color = T3, fontSize = 15.sp); i() })
            val sc = if (provider.isQwen) QwenColor else Accent
            if (isLoading) { Box(Modifier.size(44.dp).clip(CircleShape).background(Color(0xFFFF3B30)).clickable { currentJob?.cancel(); currentJob = null }, contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Stop, null, Modifier.size(20.dp), tint = Color.White) } }
            else { val cs = input.isNotBlank() || attachedImage != null || attachedFile != null
                Box(Modifier.size(44.dp).clip(CircleShape).background(if (cs) sc else Card2).clickable(enabled = cs) { send() }, contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Rounded.Send, null, Modifier.size(20.dp), tint = if (cs) Color.White else T3) } } }
    }
    // Model picker
    if (showModelPicker) { val hG = geminiKey.isNotBlank(); val hQ = qwenKey.isNotBlank()
        AlertDialog(onDismissRequest = { showModelPicker = false }, containerColor = Card, title = { Text("Select Model", fontWeight = FontWeight.Bold, color = T1, fontSize = 18.sp) },
            text = { LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.heightIn(max = 500.dp)) {
                val av = AiProvider.entries.filter { (it.isGemini && hG) || (it.isQwen && hQ) }; val cats = av.groupBy { it.category }.toList()
                cats.forEach { (cat, models) -> val cc = if (models.first().isQwen) QwenColor else Accent; item { Text(cat, fontSize = 12.sp, color = cc, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)) }
                    items(models) { p -> ModelCard(p, provider == p, cc) { provider = p; showModelPicker = false } } }
                if (!hG) { item { Text("Add Gemini key in Settings", color = T3, fontSize = 12.sp, modifier = Modifier.padding(8.dp)) } }
                if (!hQ) { item { Text("Add Qwen key in Settings", color = T3, fontSize = 12.sp, modifier = Modifier.padding(8.dp)) } }
            } }, confirmButton = {}) }
    // Settings
    if (showSettings) { var gK by remember { mutableStateOf(geminiKey) }; var pU by remember { mutableStateOf(proxyUrl) }; var qK by remember { mutableStateOf(qwenKey) }; var rI by remember { mutableStateOf(qwenRegion) }
        AlertDialog(onDismissRequest = { showSettings = false }, containerColor = Card, title = { Text("AI Settings", fontWeight = FontWeight.Bold, color = T1) },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionLabel("Gemini API Key", Accent); OutlinedTextField(gK, { gK = it }, singleLine = true, placeholder = { Text("AIza...", color = T3) }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = T1, unfocusedTextColor = T1, focusedBorderColor = Accent, unfocusedBorderColor = Border, cursorColor = Accent), modifier = Modifier.fillMaxWidth())
                SectionLabel("Proxy (optional)", T2); OutlinedTextField(pU, { pU = it }, singleLine = true, placeholder = { Text("https://proxy/v1beta/models", color = T3, fontSize = 13.sp) }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = T1, unfocusedTextColor = T1, focusedBorderColor = Accent, unfocusedBorderColor = Border, cursorColor = Accent), modifier = Modifier.fillMaxWidth())
                HorizontalDivider(color = Border); SectionLabel("Qwen API Key", QwenColor)
                OutlinedTextField(qK, { qK = it }, singleLine = true, placeholder = { Text("sk-...", color = T3) }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = T1, unfocusedTextColor = T1, focusedBorderColor = QwenColor, unfocusedBorderColor = Border, cursorColor = QwenColor), modifier = Modifier.fillMaxWidth())
                SectionLabel("Region", T2); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("intl" to "Singapore", "cn" to "Beijing").forEach { (c, l) -> val s = rI == c
                    Box(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(if (s) QwenColor.copy(0.15f) else Card2).border(1.dp, if (s) QwenColor else Border, RoundedCornerShape(8.dp)).clickable { rI = c }.padding(10.dp), contentAlignment = Alignment.Center) { Text(l, fontSize = 13.sp, color = if (s) QwenColor else T2, fontWeight = if (s) FontWeight.SemiBold else FontWeight.Normal) } } }
            } }, confirmButton = { TextButton(onClick = { GeminiKeyStore.saveKey(context, gK); geminiKey = gK.trim(); GeminiKeyStore.saveProxy(context, pU); proxyUrl = pU.trim()
                GeminiKeyStore.saveQwenKey(context, qK); qwenKey = qK.trim(); GeminiKeyStore.saveQwenRegion(context, rI); qwenRegion = rI; showSettings = false; Toast.makeText(context, Strings.done, Toast.LENGTH_SHORT).show()
            }) { Text("Save", color = Accent) } }, dismissButton = { TextButton(onClick = { showSettings = false }) { Text("Cancel", color = T2) } }) }
}

@Composable private fun ModelCard(p: AiProvider, sel: Boolean, ac: Color, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (sel) ac.copy(0.1f) else Color.Transparent).border(1.dp, if (sel) ac.copy(0.3f) else Border, RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(36.dp).background(if (sel) ac.copy(0.15f) else Card2, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(18.dp), tint = if (sel) ac else T2) }
        Column(Modifier.weight(1f)) { Text(p.label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = if (sel) ac else T1); Text(p.desc, fontSize = 11.sp, color = T3) }
        if (p.supportsVision) Bdg("Vision", T2); if (p.supportsFiles) Bdg("Files", ac); if (sel) Icon(Icons.Rounded.Check, null, Modifier.size(16.dp), tint = ac)
    } }
@Composable private fun Bdg(t: String, c: Color) { Box(Modifier.background(c.copy(0.1f), RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 2.dp)) { Text(t, fontSize = 9.sp, color = c, fontWeight = FontWeight.SemiBold) } }

// ═══════════════════════════════════
// Syntax Highlighting
// ═══════════════════════════════════
@Composable private fun CodeBlock(code: String, lang: String, onCopy: () -> Unit, onSave: () -> Unit) {
    val lines = code.lines()
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).border(1.dp, CodeBorder, RoundedCornerShape(8.dp))) {
        Row(Modifier.fillMaxWidth().background(Color(0xFF1C2128)).padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(lang.ifBlank { "code" }, fontSize = 11.sp, color = CodeCmt, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Box(Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xFF30363D)).clickable { onSave() }.padding(horizontal = 8.dp, vertical = 3.dp)) { Text("Save", fontSize = 10.sp, color = CodeDef) }
            Spacer(Modifier.width(6.dp))
            Box(Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xFF30363D)).clickable { onCopy() }.padding(horizontal = 8.dp, vertical = 3.dp)) { Text("Copy", fontSize = 10.sp, color = CodeDef) }
        }
        Column(Modifier.background(CodeBg).horizontalScroll(rememberScrollState()).padding(8.dp)) {
            lines.forEachIndexed { idx, line -> Row { Text("${idx + 1}".padStart(3), fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF484F58), modifier = Modifier.padding(end = 10.dp)); Text(hlLine(line, lang), fontSize = 12.sp, fontFamily = FontFamily.Monospace, lineHeight = 18.sp) } } } } }

private fun hlLine(line: String, lang: String): AnnotatedString = buildAnnotatedString {
    var i = 0; while (i < line.length) { when {
        i < line.length - 1 && line[i] == '/' && line[i + 1] == '/' -> { withStyle(SpanStyle(color = CodeCmt)) { append(line.substring(i)) }; i = line.length }
        line[i] == '#' && lang in listOf("python","py","bash","sh","yaml","yml","rb") -> { withStyle(SpanStyle(color = CodeCmt)) { append(line.substring(i)) }; i = line.length }
        line[i] == '"' -> { val e = line.indexOf('"', i + 1).let { if (it < 0) line.length - 1 else it }; withStyle(SpanStyle(color = CodeStr)) { append(line.substring(i, e + 1)) }; i = e + 1 }
        line[i] == '\'' -> { val e = line.indexOf('\'', i + 1).let { if (it < 0) line.length - 1 else it }; withStyle(SpanStyle(color = CodeStr)) { append(line.substring(i, e + 1)) }; i = e + 1 }
        line[i] == '@' -> { var j = i + 1; while (j < line.length && (line[j].isLetterOrDigit() || line[j] == '.')) j++; withStyle(SpanStyle(color = CodeTy)) { append(line.substring(i, j)) }; i = j }
        line[i].isDigit() && (i == 0 || !line[i - 1].isLetterOrDigit()) -> { var j = i; while (j < line.length && (line[j].isDigit() || line[j] == '.' || line[j] == 'f' || line[j] == 'L')) j++; withStyle(SpanStyle(color = CodeNum)) { append(line.substring(i, j)) }; i = j }
        line[i].isLetter() || line[i] == '_' -> { var j = i; while (j < line.length && (line[j].isLetterOrDigit() || line[j] == '_')) j++; val w = line.substring(i, j)
            val c = when { w in kwSet -> CodeKw; w.first().isUpperCase() -> CodeTy; j < line.length && line[j] == '(' -> CodeFn; else -> CodeDef }; withStyle(SpanStyle(color = c)) { append(w) }; i = j }
        else -> { withStyle(SpanStyle(color = CodeDef)) { append(line[i].toString()) }; i++ } } } }

// ═══════════════════════════════════
// Markdown
// ═══════════════════════════════════
@Composable private fun MdText(text: String) { val lines = text.lines()
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) { for (l in lines) { when {
        l.startsWith("### ") -> Text(l.removePrefix("### "), color = T1, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        l.startsWith("## ") -> Text(l.removePrefix("## "), color = T1, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        l.startsWith("# ") -> Text(l.removePrefix("# "), color = T1, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        l.startsWith("- ") || l.startsWith("* ") -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Text("•", color = Accent, fontSize = 14.sp); Text(inlineMd(l.drop(2)), fontSize = 14.sp, lineHeight = 20.sp) }
        l.matches(Regex("^\\d+\\.\\s.*")) -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Text(l.substringBefore(".") + ".", color = Accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold); Text(inlineMd(l.substringAfter(". ")), fontSize = 14.sp, lineHeight = 20.sp) }
        l.startsWith("> ") -> Row { Box(Modifier.width(3.dp).height(20.dp).background(Accent, RoundedCornerShape(2.dp))); Spacer(Modifier.width(8.dp)); Text(inlineMd(l.removePrefix("> ")), fontSize = 14.sp, color = T2, fontStyle = FontStyle.Italic, lineHeight = 20.sp) }
        l.isBlank() -> Spacer(Modifier.height(4.dp))
        else -> Text(inlineMd(l), fontSize = 14.sp, lineHeight = 20.sp)
    } } } }
private fun inlineMd(t: String): AnnotatedString = buildAnnotatedString { var i = 0; while (i < t.length) { when {
    i < t.length - 1 && t[i] == '*' && t[i + 1] == '*' -> { val e = t.indexOf("**", i + 2); if (e > 0) { withStyle(SpanStyle(color = T1, fontWeight = FontWeight.Bold)) { append(t.substring(i + 2, e)) }; i = e + 2 } else { withStyle(SpanStyle(color = T1)) { append(t[i].toString()) }; i++ } }
    t[i] == '*' -> { val e = t.indexOf('*', i + 1); if (e > 0) { withStyle(SpanStyle(color = T1, fontStyle = FontStyle.Italic)) { append(t.substring(i + 1, e)) }; i = e + 1 } else { withStyle(SpanStyle(color = T1)) { append(t[i].toString()) }; i++ } }
    t[i] == '`' -> { val e = t.indexOf('`', i + 1); if (e > 0) { withStyle(SpanStyle(color = Color(0xFFE06C75), fontFamily = FontFamily.Monospace, background = Color(0xFF1E1E2E))) { append(t.substring(i + 1, e)) }; i = e + 1 } else { withStyle(SpanStyle(color = T1)) { append(t[i].toString()) }; i++ } }
    else -> { withStyle(SpanStyle(color = T1)) { append(t[i].toString()) }; i++ }
} } }

// ═══════════════════════════════════
// Bubble
// ═══════════════════════════════════
@OptIn(ExperimentalFoundationApi::class) @Composable
private fun Bubble(msg: ChatMessage, prov: AiProvider, onCopy: () -> Unit, onEdit: (() -> Unit)?, onDelete: () -> Unit, onSaveCode: (String, String) -> Unit, onRegenerate: (() -> Unit)?) {
    val isUser = msg.role == "user"; val context = LocalContext.current; val pc = if (prov.isQwen) QwenColor else Accent
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start, verticalAlignment = Alignment.Top) {
        if (!isUser) { Box(Modifier.padding(top = 4.dp).size(28.dp).clip(CircleShape).background(pc.copy(0.15f)), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(14.dp), tint = pc) }; Spacer(Modifier.width(6.dp)) }
        Column(Modifier.weight(1f, fill = false), horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
            if (msg.imageBase64 != null) { val bmp = remember(msg.imageBase64) { try { val b = android.util.Base64.decode(msg.imageBase64, android.util.Base64.NO_WRAP); android.graphics.BitmapFactory.decodeByteArray(b, 0, b.size) } catch (_: Exception) { null } }
                if (bmp != null) { androidx.compose.foundation.Image(bitmap = bmp.asImageBitmap(), contentDescription = null, modifier = Modifier.widthIn(max = 240.dp).heightIn(max = 180.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop); Spacer(Modifier.height(4.dp)) } }
            if (msg.fileContent != null && isUser) Row(Modifier.padding(bottom = 4.dp).clip(RoundedCornerShape(8.dp)).background(Card2).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.AttachFile, null, Modifier.size(12.dp), tint = T2); Text("File attached", color = T2, fontSize = 10.sp) }
            if (isUser) { Box(Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)).background(Accent).combinedClickable(onClick = {}, onLongClick = onCopy).padding(12.dp)) { Text(msg.content, color = Color.Black, fontSize = 14.sp, lineHeight = 20.sp) }
            } else { val content = msg.content
                if (content.contains("```")) { Column(Modifier.widthIn(max = 340.dp).combinedClickable(onClick = {}, onLongClick = onCopy), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val parts = content.split("```"); parts.forEachIndexed { i, part -> if (i % 2 == 0) { if (part.isNotBlank()) Box(Modifier.clip(RoundedCornerShape(12.dp)).background(Card).padding(12.dp)) { MdText(part.trim()) } }
                        else { val lines = part.lines(); val lang = if (lines.isNotEmpty() && lines.first().matches(Regex("^[a-zA-Z0-9_+#.-]+$"))) lines.first().lowercase() else ""
                            val code = if (lang.isNotBlank()) lines.drop(1).joinToString("\n") else part
                            CodeBlock(code.trimEnd(), lang, onCopy = { (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("code", code)); Toast.makeText(context, Strings.copied, Toast.LENGTH_SHORT).show() }, onSave = { onSaveCode(code, lang) }) } } } }
                else Box(Modifier.widthIn(max = 320.dp).clip(RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)).background(Card).combinedClickable(onClick = {}, onLongClick = onCopy).padding(12.dp)) { MdText(content) } }
            // Actions
            Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                SBtn(Icons.Rounded.ContentCopy) { onCopy() }; if (onEdit != null) SBtn(Icons.Rounded.Edit) { onEdit() }; SBtn(Icons.Rounded.Delete) { onDelete() }
                if (onRegenerate != null) SBtn(Icons.Rounded.Refresh) { onRegenerate() } }
        }
        if (isUser) { Spacer(Modifier.width(6.dp)); Box(Modifier.padding(top = 4.dp).size(28.dp).clip(CircleShape).background(Accent.copy(0.15f)), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Person, null, Modifier.size(14.dp), tint = Accent) } }
    } }
@Composable private fun SBtn(icon: ImageVector, onClick: () -> Unit) { IconButton(onClick = onClick, modifier = Modifier.size(28.dp)) { Icon(icon, null, Modifier.size(13.dp), tint = T3) } }

@Composable private fun SectionLabel(t: String, c: Color) { Text(t, fontSize = 13.sp, color = c, fontWeight = FontWeight.Medium) }
@Composable private fun InputField(v: String, ch: (String) -> Unit, h: String, mono: Boolean = false) {
    BasicTextField(v, ch, Modifier.fillMaxWidth().background(Card, RoundedCornerShape(10.dp)).border(1.dp, Border, RoundedCornerShape(10.dp)).padding(14.dp),
        textStyle = TextStyle(T1, 14.sp, fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default), cursorBrush = SolidColor(Accent), singleLine = true,
        decorationBox = { inner -> if (v.isEmpty()) Text(h, color = T3, fontSize = 14.sp); inner() }) }
