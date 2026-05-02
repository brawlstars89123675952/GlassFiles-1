package com.glassfiles.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.glassfiles.data.Strings
import com.glassfiles.data.ai.AiChatSessionStore
import com.glassfiles.data.ai.AiAttachmentProcessor
import com.glassfiles.data.ai.AiCostPreviewPrefs
import com.glassfiles.data.ai.AiKeyStore
import com.glassfiles.data.ai.AiSettingsStore
import com.glassfiles.data.ai.ChatHistoryStore
import com.glassfiles.data.ai.ModelRegistry
import com.glassfiles.data.ai.AiPreparedAttachment
import com.glassfiles.data.ai.SystemPrompts
import com.glassfiles.data.ai.models.AiCapability
import com.glassfiles.data.ai.models.AiMessage
import com.glassfiles.data.ai.models.AiModel
import com.glassfiles.data.ai.models.AiProviderId
import com.glassfiles.data.ai.providers.AiProviders
import com.glassfiles.data.ai.usage.AiUsageAccounting
import com.glassfiles.data.ai.usage.AiUsageEstimate
import com.glassfiles.data.ai.usage.AiUsageMode
import com.glassfiles.ui.components.AiPickerChip
import com.glassfiles.ui.screens.ai.AiCostPreviewDialog
import com.glassfiles.ui.screens.ai.terminal.AgentMessageRow
import com.glassfiles.ui.screens.ai.terminal.AgentRole
import com.glassfiles.ui.screens.ai.terminal.AgentTerminal
import com.glassfiles.ui.screens.ai.terminal.AgentTerminalCodeBlock
import com.glassfiles.ui.screens.ai.terminal.Icon
import com.glassfiles.ui.screens.ai.terminal.IconButton
import com.glassfiles.ui.screens.ai.terminal.TerminalHairline
import com.glassfiles.ui.screens.ai.terminal.TerminalPageBar
import com.glassfiles.ui.screens.ai.terminal.TerminalPillButton
import com.glassfiles.ui.screens.ai.terminal.Text
import com.glassfiles.ui.theme.JetBrainsMono
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val HISTORY_MODE = "coding"
private const val SESSION_MODE = AiChatSessionStore.MODE_CODING

/**
 * Coding-focused chat. Distinct from [AiChatScreen]:
 *  - Only models classified as [AiCapability.CODING] are picker-eligible.
 *  - The assistant runs with [SystemPrompts.CODING] regardless of provider.
 *  - The transcript renders code fences as monospace cards with syntax
 *    highlighting and a "Copy" action.
 *  - Conversations are organised into named sessions (see
 *    [AiChatSessionStore]). Older single-conversation transcripts left
 *    over from [ChatHistoryStore] are migrated to a session on first
 *    launch.
 *  - User can attach a screenshot when reporting a bug.
 *  - Right-aligned user bubbles, left-aligned assistant bubbles.
 *  - Input bar rises with the keyboard via `Modifier.imePadding()`.
 */
@Composable
fun AiCodingScreen(onBack: () -> Unit) {
    com.glassfiles.ui.screens.ai.terminal.AgentTerminalSurface {
        com.glassfiles.ui.screens.ai.terminal.AiTerminalContentBridge {
            AiCodingScreenInner(onBack)
        }
    }
}

@Composable
private fun AiCodingScreenInner(onBack: () -> Unit) {
    val context = LocalContext.current
    var sessions by remember {
        mutableStateOf(loadSessionsWithMigration(context))
    }
    var activeSessionId by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        sessions = AiChatSessionStore.list(context, SESSION_MODE)
    }

    val active = activeSessionId?.let { id -> sessions.firstOrNull { it.id == id } }
    if (active != null) {
        CodingChatView(
            initialSession = active,
            onBack = {
                activeSessionId = null
                refresh()
            },
        )
        return
    }

    CodingSessionsList(
        sessions = sessions,
        onOpen = { activeSessionId = it.id },
        onNew = {
            val now = System.currentTimeMillis()
            val newSession = AiChatSessionStore.Session(
                id = "coding_${now}",
                mode = SESSION_MODE,
                title = AiChatSessionStore.deriveTitle(emptyList()),
                providerId = "",
                modelId = "",
                messages = emptyList(),
                createdAt = now,
                updatedAt = now,
            )
            AiChatSessionStore.upsert(context, newSession)
            refresh()
            activeSessionId = newSession.id
        },
        onDelete = { s ->
            AiChatSessionStore.delete(context, SESSION_MODE, s.id)
            refresh()
        },
        onDeleteAll = {
            AiChatSessionStore.clear(context, SESSION_MODE)
            refresh()
        },
        onBack = onBack,
    )
}

/**
 * If sessions are empty but the legacy single-conversation store has
 * messages from before the multi-session refactor, migrate those into a
 * new "Imported" session. Idempotent.
 */
private fun loadSessionsWithMigration(
    context: android.content.Context,
): List<AiChatSessionStore.Session> {
    val existing = AiChatSessionStore.list(context, SESSION_MODE)
    if (existing.isNotEmpty()) return existing
    val legacy = ChatHistoryStore.load(context, HISTORY_MODE)
    if (legacy.isEmpty()) return emptyList()
    val now = System.currentTimeMillis()
    val migrated = AiChatSessionStore.Session(
        id = "coding_imported_${now}",
        mode = SESSION_MODE,
        title = AiChatSessionStore.deriveTitle(
            legacy.map {
                AiChatSessionStore.Message(
                    role = it.role,
                    content = it.content,
                    imageBase64 = it.imageBase64,
                    isError = it.isError,
                )
            },
        ),
        providerId = "",
        modelId = "",
        messages = legacy.map {
            AiChatSessionStore.Message(
                role = it.role,
                content = it.content,
                imageBase64 = it.imageBase64,
                isError = it.isError,
            )
        },
        createdAt = now,
        updatedAt = now,
    )
    AiChatSessionStore.upsert(context, migrated)
    ChatHistoryStore.clear(context, HISTORY_MODE)
    return AiChatSessionStore.list(context, SESSION_MODE)
}

// ─────────────────────────────────────────────────────────────────────────
// Sessions list
// ─────────────────────────────────────────────────────────────────────────
@Composable
private fun CodingSessionsList(
    sessions: List<AiChatSessionStore.Session>,
    onOpen: (AiChatSessionStore.Session) -> Unit,
    onNew: () -> Unit,
    onDelete: (AiChatSessionStore.Session) -> Unit,
    onDeleteAll: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = AgentTerminal.colors
    val sdf = remember { SimpleDateFormat("dd.MM.yy HH:mm", Locale.getDefault()) }
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, sessions) {
        if (query.isBlank()) sessions
        else sessions.filter { s ->
            s.title.contains(query, ignoreCase = true) ||
                s.messages.any { it.content.contains(query, ignoreCase = true) }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.surface)
            .statusBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    null,
                    Modifier.size(20.dp),
                    tint = colors.textPrimary,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    Strings.aiCoding.uppercase(),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = colors.textPrimary,
                    fontFamily = JetBrainsMono,
                )
                Text(
                    "${sessions.size} sessions",
                    fontSize = 10.sp,
                    color = colors.textSecondary,
                )
            }
            if (sessions.isNotEmpty()) {
                IconButton(onClick = onDeleteAll) {
                    Icon(
                        Icons.Rounded.DeleteSweep,
                        null,
                        Modifier.size(20.dp),
                        tint = colors.textSecondary,
                    )
                }
            }
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border.copy(alpha = 0.12f)))

        if (sessions.size > 2) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surfaceElevated.copy(alpha = 0.5f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.Search,
                    null,
                    Modifier.size(16.dp),
                    tint = colors.textSecondary,
                )
                Spacer(Modifier.size(8.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            "Search…",
                            color = colors.textSecondary,
                            fontSize = 13.sp,
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        textStyle = TextStyle(
                            color = colors.textPrimary,
                            fontSize = 13.sp,
                        ),
                        cursorBrush = SolidColor(colors.accent),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        if (filtered.isEmpty()) {
            Box(
                Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (sessions.isEmpty()) "No chats yet" else "Nothing found",
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                )
            }
        } else {
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(filtered, key = { it.id }) { s ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.surfaceElevated.copy(alpha = 0.5f))
                            .clickable { onOpen(s) }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            Icons.Rounded.Chat,
                            null,
                            Modifier.size(20.dp),
                            tint = colors.textSecondary,
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                s.title,
                                color = colors.textPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                            )
                            Spacer(Modifier.size(2.dp))
                            Text(
                                "${sdf.format(Date(s.updatedAt))} · ${s.messages.size} msgs",
                                color = colors.textSecondary,
                                fontSize = 11.sp,
                                fontFamily = JetBrainsMono,
                                maxLines = 1,
                            )
                        }
                        IconButton(
                            onClick = { onDelete(s) },
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                Icons.Rounded.Close,
                                null,
                                Modifier.size(16.dp),
                                tint = colors.textSecondary,
                            )
                        }
                    }
                }
            }
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border.copy(alpha = 0.12f)))

        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.accent)
                .clickable { onNew() }
                .padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.Add,
                null,
                Modifier.size(20.dp),
                tint = colors.background,
            )
            Spacer(Modifier.size(8.dp))
            Text(
                "New chat",
                color = colors.background,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun CodingChatView(
    initialSession: AiChatSessionStore.Session,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val colors = AgentTerminal.colors
    val scope = rememberCoroutineScope()
    val sessionId = initialSession.id
    val sessionCreatedAt = initialSession.createdAt

    val configured by remember { derivedStateOf { AiKeyStore.configuredProviders(context) } }
    var provider by remember(configured) {
        mutableStateOf(
            initialSession.providerId.takeIf { it.isNotBlank() }
                ?.let { runCatching { AiProviderId.valueOf(it) }.getOrNull() }
                ?.takeIf { it in configured }
                ?: loadSavedProvider(context, configured),
        )
    }
    val codingModels = remember { mutableStateListOf<AiModel>() }
    var modelId by remember { mutableStateOf(initialSession.modelId) }
    var loadingModels by remember { mutableStateOf(false) }
    var modelLoadError by remember { mutableStateOf<String?>(null) }

    val transcript = remember {
        mutableStateListOf<CodingMessage>().apply {
            addAll(
                initialSession.messages.map {
                    CodingMessage(
                        role = it.role,
                        content = it.content,
                        imageBase64 = it.imageBase64,
                        fileContent = it.fileContent,
                        isError = it.isError,
                    )
                },
            )
        }
    }
    var draft by remember { mutableStateOf(TextFieldValue("")) }
    var pendingImage by remember { mutableStateOf<String?>(null) }
    var pendingFile by remember { mutableStateOf<AiPreparedAttachment?>(null) }
    var streaming by remember { mutableStateOf(false) }
    var streamJob by remember { mutableStateOf<Job?>(null) }
    val usageFingerprint = transcript.joinToString("|") {
        "${it.role}:${it.content.length}:${it.fileContent?.length ?: 0}:${it.imageBase64?.length ?: 0}:${it.isError}"
    }
    val usageEstimate = remember(usageFingerprint, provider, modelId) {
        AiUsageAccounting.estimate(
            providerId = provider?.name.orEmpty(),
            modelId = modelId,
            inputChars = codingInputChars(transcript),
            outputChars = codingOutputChars(transcript),
        )
    }

    val listState = rememberLazyListState()

    // Refresh model list when provider changes.
    LaunchedEffect(provider) {
        val p = provider ?: return@LaunchedEffect
        loadingModels = true
        modelLoadError = null
        codingModels.clear()
        try {
            val all = ModelRegistry.getModels(
                context = context,
                provider = p,
                apiKey = AiKeyStore.getKey(context, p),
                force = false,
            )
            val coding = all.filter { AiCapability.CODING in it.capabilities }
            codingModels.addAll(coding)
            val saved = loadSavedModel(context)
            modelId = when {
                saved != null && coding.any { it.id == saved } -> saved
                else -> coding.firstOrNull()?.id ?: ""
            }
            if (modelId.isNotEmpty()) saveSelection(context, p, modelId)
        } catch (e: Exception) {
            modelLoadError = e.message ?: e.javaClass.simpleName
        } finally {
            loadingModels = false
        }
    }

    // Auto-scroll to bottom while streaming.
    LaunchedEffect(transcript.size) {
        if (transcript.isNotEmpty()) listState.animateScrollToItem(transcript.lastIndex)
    }
    LaunchedEffect(streaming) {
        if (streaming) {
            snapshotFlow { transcript.lastOrNull()?.content?.length }.collectLatest {
                if (transcript.isNotEmpty()) listState.scrollToItem(transcript.lastIndex)
            }
        }
    }

    fun persist() {
        val msgs = transcript.map {
            AiChatSessionStore.Message(
                role = it.role,
                content = it.content,
                imageBase64 = it.imageBase64,
                fileContent = it.fileContent,
                isError = it.isError,
            )
        }
        AiChatSessionStore.upsert(
            context = context,
            session = AiChatSessionStore.Session(
                id = sessionId,
                mode = SESSION_MODE,
                title = AiChatSessionStore.deriveTitle(msgs),
                providerId = provider?.name.orEmpty(),
                modelId = modelId,
                messages = msgs,
                createdAt = sessionCreatedAt,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    val attachmentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val mime = context.contentResolver.getType(uri).orEmpty()
                val name = uri.lastPathSegment.orEmpty()
                if (AiAttachmentProcessor.isImage(mime, name)) {
                    pendingImage = withContext(Dispatchers.IO) { encodeImage(context, uri) }
                    pendingFile = null
                } else {
                    pendingFile = runCatching { AiAttachmentProcessor.prepare(context, uri) }
                        .getOrElse { e ->
                            AiPreparedAttachment(
                                name = name.ifBlank { "attachment" },
                                mimeType = mime,
                                extension = "",
                                tempPath = "",
                                isArchive = false,
                                promptContent = "[attachment error: ${e.message ?: e.javaClass.simpleName}]",
                                previewContent = null,
                                summary = "attachment error",
                            )
                        }
                    pendingImage = null
                }
            }
        }
    }

    val currentModel = codingModels.firstOrNull { it.id == modelId }
    val visionAvailable = currentModel?.let { AiCapability.VISION in it.capabilities } == true

    fun sendInternal(messages: List<AiMessage>) {
        val p = provider ?: return
        val mid = modelId.takeIf { it.isNotBlank() } ?: return
        val key = AiKeyStore.getKey(context, p)
        if (key.isBlank()) {
            transcript += CodingMessage("assistant", Strings.aiCodingNeedKey, isError = true)
            persist()
            return
        }
        transcript += CodingMessage("assistant", "")
        persist()
        streaming = true

        streamJob = scope.launch {
            try {
                val full = AiProviders.get(p).chat(
                    context = context,
                    modelId = mid,
                    messages = messages,
                    apiKey = key,
                    onChunk = { chunk ->
                        val last = transcript.lastIndex
                        if (last >= 0) {
                            transcript[last] = transcript[last].copy(
                                content = transcript[last].content + chunk,
                            )
                        }
                    },
                )
                val last = transcript.lastIndex
                if (last >= 0 && transcript[last].content.isBlank()) {
                    transcript[last] = transcript[last].copy(content = full)
                }
                AiUsageAccounting.appendEstimated(
                    context = context,
                    providerId = p.name,
                    modelId = mid,
                    mode = AiUsageMode.CODING,
                    messages = messages,
                    output = full,
                )
                persist()
            } catch (e: Exception) {
                val last = transcript.lastIndex
                val err = "${e.javaClass.simpleName}: ${e.message ?: ""}"
                if (last >= 0) {
                    transcript[last] = transcript[last].copy(content = err, isError = true)
                } else {
                    transcript += CodingMessage("assistant", err, isError = true)
                }
                persist()
            } finally {
                streaming = false
                streamJob = null
            }
        }
    }

    /**
     * Pending send args parked while the pre-request cost preview
     * dialog is on screen — see [AiCostPreviewPrefs.shouldPreview].
     * Confirming the dialog re-runs the send via [actuallyDoSend];
     * dismissing it discards the pending args and the user keeps
     * editing.
     */
    var pendingSend by remember { mutableStateOf<PendingCodingSend?>(null) }

    fun actuallyDoSend(text: String, image: String?, file: AiPreparedAttachment?) {
        val displayText = text.ifBlank {
            when {
                file != null -> "Analyze ${file.name}"
                image != null -> "Analyze this image"
                else -> text
            }
        }
        transcript += CodingMessage("user", displayText, imageBase64 = image, fileContent = file?.promptContent)
        draft = TextFieldValue("")
        pendingImage = null
        pendingFile = null
        persist()

        val msgs = buildList {
            add(AiMessage("system", SystemPrompts.CODING))
            addAll(
                transcript
                    .filter { !it.isError && (it.role == "user" || it.role == "assistant") }
                    .map { AiMessage(it.role, it.content, imageBase64 = it.imageBase64, fileContent = it.fileContent) },
            )
        }
        sendInternal(msgs)
    }

    fun send() {
        provider ?: return
        modelId.takeIf { it.isNotBlank() } ?: return
        val text = draft.text.trim()
        if ((text.isBlank() && pendingImage == null && pendingFile == null) || streaming) return

        val image = pendingImage?.takeIf { visionAvailable }
        val file = pendingFile
        // Pre-request cost preview: if the projected cost is over the
        // threshold (default $0.10) park the user's text + image and
        // let the confirm dialog decide. Same `usageEstimate` as the
        // chip in the top bar — what the user sees is what gets
        // checked.
        val cost = usageEstimate.costUsd
        if (cost != null && AiCostPreviewPrefs.shouldPreview(context, cost)) {
            pendingSend = PendingCodingSend(
                text = text,
                image = image,
                file = file,
                estimatedCostUsd = cost,
                estimatedTokens = usageEstimate.totalTokens,
            )
            return
        }
        actuallyDoSend(text, image, file)
    }

    fun stop() {
        streamJob?.cancel()
        streamJob = null
        streaming = false
    }

    fun regenerate() {
        if (streaming || transcript.isEmpty()) return
        // Drop the trailing assistant turn (if any) and re-run with the same user input.
        if (transcript.last().role == "assistant") transcript.removeAt(transcript.lastIndex)
        if (transcript.isEmpty() || transcript.last().role != "user") return
        persist()
        val msgs = buildList {
            add(AiMessage("system", SystemPrompts.CODING))
            addAll(
                transcript
                    .filter { !it.isError && (it.role == "user" || it.role == "assistant") }
                    .map { AiMessage(it.role, it.content, imageBase64 = it.imageBase64, fileContent = it.fileContent) },
            )
        }
        sendInternal(msgs)
    }

    fun clearAll() {
        transcript.clear()
        AiChatSessionStore.delete(context, SESSION_MODE, sessionId)
        onBack()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        TerminalPageBar(
            title = "> coding",
            subtitle = Strings.aiCodingHint,
            onBack = onBack,
            trailing = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    CodingUsageChip(usageEstimate)
                    if (transcript.isNotEmpty()) {
                        TerminalPillButton(
                            label = "regen",
                            onClick = ::regenerate,
                            enabled = !streaming,
                            accent = false,
                        )
                        TerminalPillButton(
                            label = "clear",
                            onClick = ::clearAll,
                            destructive = true,
                        )
                    }
                }
            },
        )

        // ── Model picker bar ────────────────────────────────────────────
        ModelPickerBar(
            configured = configured,
            provider = provider,
            onProviderChange = {
                provider = it
                saveSelection(context, it, "")
            },
            codingModels = codingModels,
            modelId = modelId,
            onModelChange = {
                modelId = it
                provider?.let { p -> saveSelection(context, p, it) }
            },
            loadingModels = loadingModels,
            modelLoadError = modelLoadError,
        )

        TerminalHairline()

        // ── Transcript ──────────────────────────────────────────────────
        if (transcript.isEmpty()) {
            Box(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    Strings.aiCodingHint,
                    fontSize = 13.sp,
                    color = colors.textMuted,
                    fontFamily = JetBrainsMono,
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(transcript) { message -> CodingMessageRow(message, context) }
                if (streaming) {
                    item {
                        Text(
                            text = "⠋ thinking...",
                            color = colors.textMuted,
                            fontFamily = JetBrainsMono,
                            fontSize = 12.sp,
                            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, top = 4.dp),
                        )
                    }
                }
            }
        }

        // ── Pending attachment preview ──────────────────────────────────
        if (pendingImage != null) {
            AttachmentPreview(
                base64 = pendingImage!!,
                visionAvailable = visionAvailable,
                onRemove = { pendingImage = null },
            )
        }
        pendingFile?.let { file ->
            FileAttachmentPreview(
                attachment = file,
                onRemove = { pendingFile = null },
            )
        }

        // ── Input bar ───────────────────────────────────────────────────
        InputBar(
            value = draft,
            onValueChange = { draft = it },
            enabled = provider != null && modelId.isNotBlank(),
            streaming = streaming,
            hasAttachment = pendingImage != null || pendingFile != null,
            onSend = ::send,
            onStop = ::stop,
            onPickImage = { attachmentPicker.launch("*/*") },
            modifier = Modifier.imePadding().navigationBarsPadding(),
        )
    }

    pendingSend?.let { pending ->
        AiCostPreviewDialog(
            estimatedCostUsd = pending.estimatedCostUsd,
            estimatedTokens = pending.estimatedTokens,
            thresholdUsd = AiCostPreviewPrefs.getThresholdUsd(context),
            onConfirm = {
                pendingSend = null
                actuallyDoSend(pending.text, pending.image, pending.file)
            },
            onDismiss = { pendingSend = null },
        )
    }
}

/**
 * Args captured while the coding-screen pre-request cost preview
 * dialog is on screen. Mirrors `PendingChatSend` in [AiChatScreen].
 */
private data class PendingCodingSend(
    val text: String,
    val image: String?,
    val file: AiPreparedAttachment?,
    val estimatedCostUsd: Double,
    val estimatedTokens: Int,
)

// ─────────────────────────────────────────────────────────────────────────
// Model picker
// ─────────────────────────────────────────────────────────────────────────

@Composable
private fun ModelPickerBar(
    configured: List<AiProviderId>,
    provider: AiProviderId?,
    onProviderChange: (AiProviderId) -> Unit,
    codingModels: List<AiModel>,
    modelId: String,
    onModelChange: (String) -> Unit,
    loadingModels: Boolean,
    modelLoadError: String?,
) {
    val colors = AgentTerminal.colors

    if (configured.isEmpty()) {
        Box(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                Strings.aiCodingNoProvider,
                fontSize = 12.sp,
                color = colors.error,
            )
        }
        return
    }

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AiPickerChip(
            label = Strings.aiCodingPickProvider,
            value = provider?.displayName ?: "—",
            title = Strings.aiCodingPickProvider,
            options = configured,
            optionLabel = { it.displayName },
            selected = provider,
            onSelect = { onProviderChange(it) },
            modifier = Modifier.weight(1f),
        )
        AiPickerChip(
            label = Strings.aiCodingPickModel,
            value = when {
                loadingModels -> Strings.aiRefreshing
                modelLoadError != null -> Strings.error
                modelId.isNotBlank() -> codingModels.firstOrNull { it.id == modelId }?.displayName ?: modelId
                codingModels.isEmpty() -> Strings.aiCodingNoCoding
                else -> "—"
            },
            title = Strings.aiCodingPickModel,
            options = codingModels,
            optionLabel = { it.displayName },
            optionSubtitle = { m ->
                val caps = m.capabilities
                    .filter { it != AiCapability.CODING }
                    .joinToString(" · ") { it.name.lowercase() }
                if (caps.isBlank()) m.id else "$caps · ${m.id}"
            },
            selected = codingModels.firstOrNull { it.id == modelId },
            onSelect = { onModelChange(it.id) },
            modifier = Modifier.weight(1.5f),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Transcript renderer
// ─────────────────────────────────────────────────────────────────────────

private data class CodingMessage(
    val role: String,
    val content: String,
    val imageBase64: String? = null,
    val fileContent: String? = null,
    val isError: Boolean = false,
)

@Composable
private fun CodingUsageChip(estimate: AiUsageEstimate) {
    val colors = AgentTerminal.colors
    val text = buildString {
        append(AiUsageAccounting.formatTokens(estimate.totalTokens, estimated = estimate.estimated))
        append(" tok")
        estimate.costUsd?.let {
            append(" · ")
            append(AiUsageAccounting.formatUsd(it, estimated = estimate.estimated))
        }
    }
    Text(
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

private fun codingInputChars(messages: List<CodingMessage>): Int =
    messages.sumOf { message ->
        if (message.role == "assistant" || message.isError) {
            0
        } else {
            message.content.length + (message.fileContent?.length ?: 0) + ((message.imageBase64?.length ?: 0) / 8)
        }
    }

private fun codingOutputChars(messages: List<CodingMessage>): Int =
    messages.sumOf { message ->
        if (message.role == "assistant" && !message.isError) message.content.length else 0
    }

@Composable
private fun CodingMessageRow(message: CodingMessage, context: Context) {
    val colors = AgentTerminal.colors
    val isUser = message.role == "user"
    AgentMessageRow(
        role = when {
            message.isError -> AgentRole.ERROR
            isUser -> AgentRole.USER
            else -> AgentRole.ASSISTANT
        },
    ) {
        if (message.imageBase64 != null) {
            MessageImage(message.imageBase64)
        }
        if (message.fileContent != null && isUser) {
            FileContentSummary(message.fileContent)
        }
        if (message.content.isNotBlank()) {
            renderMessageBody(
                content = message.content,
                textColor = when {
                    message.isError -> colors.error
                    isUser -> colors.accent
                    else -> colors.textPrimary
                },
                context = context,
            )
        }
    }
}

@Composable
private fun MessageImage(base64: String) {
    val colors = AgentTerminal.colors
    val bitmap = remember(base64) { decodeBitmap(base64) }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 200.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, colors.border, RoundedCornerShape(8.dp)),
        )
    }
}

@Composable
private fun FileContentSummary(fileContent: String) {
    val colors = AgentTerminal.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.AttachFile, null, Modifier.size(14.dp), tint = colors.textSecondary)
        Spacer(Modifier.size(8.dp))
        Text(
            fileContent.lineSequence().firstOrNull().orEmpty().ifBlank { "Attached file" },
            color = colors.textSecondary,
            fontSize = 11.sp,
            fontFamily = JetBrainsMono,
            maxLines = 2,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun renderMessageBody(content: String, textColor: Color, context: Context) {
    val chatFontSize = remember { AiSettingsStore.getChatFontSize(context) }
    splitOnFences(content).forEach { part ->
        if (part.isFence) {
            AgentTerminalCodeBlock(
                text = part.text,
                lang = part.lang,
                context = context,
            )
        } else if (part.text.isNotBlank()) {
            AgentMessageTextBlock(part.text, textColor, chatFontSize)
        }
    }
}

@Composable
private fun AgentMessageTextBlock(text: String, color: Color, fontSizeSp: Int) {
    Text(
        text = text,
        color = color,
        fontSize = fontSizeSp.sp,
        fontFamily = JetBrainsMono,
        lineHeight = 1.45.em,
    )
}

private data class MsgPart(val text: String, val lang: String, val isFence: Boolean)

private fun splitOnFences(text: String): List<MsgPart> {
    val out = mutableListOf<MsgPart>()
    val regex = Regex("```([a-zA-Z0-9_+\\-]*)\\n([\\s\\S]*?)```", RegexOption.MULTILINE)
    var last = 0
    regex.findAll(text).forEach { m ->
        if (m.range.first > last) {
            out += MsgPart(text.substring(last, m.range.first).trim('\n'), "", false)
        }
        val lang = m.groupValues[1]
        val body = m.groupValues[2].trimEnd('\n')
        out += MsgPart(body, lang, true)
        last = m.range.last + 1
    }
    if (last < text.length) {
        val tail = text.substring(last).trim('\n')
        if (tail.isNotEmpty()) out += MsgPart(tail, "", false)
    }
    if (out.isEmpty()) out += MsgPart(text, "", false)
    return out
}

// ─────────────────────────────────────────────────────────────────────────
// Attachment preview (above the input bar)
// ─────────────────────────────────────────────────────────────────────────

@Composable
private fun AttachmentPreview(base64: String, visionAvailable: Boolean, onRemove: () -> Unit) {
    val colors = AgentTerminal.colors
    val bitmap = remember(base64) { decodeBitmap(base64) }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(6.dp))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(4.dp)),
            )
        }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                Strings.aiCodingAttachImage,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
                fontFamily = JetBrainsMono,
            )
            Text(
                if (visionAvailable) Strings.aiCodingScreenshotHint else Strings.aiCodingNoVision,
                fontSize = 10.sp,
                color = if (visionAvailable) colors.textMuted else colors.error,
                maxLines = 2,
                fontFamily = JetBrainsMono,
            )
        }
        IconButton(onClick = onRemove) {
            Icon(
                Icons.Rounded.Close,
                null,
                Modifier.size(18.dp),
                tint = colors.textMuted,
            )
        }
    }
}

@Composable
private fun FileAttachmentPreview(attachment: AiPreparedAttachment, onRemove: () -> Unit) {
    val colors = AgentTerminal.colors
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(6.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AttachFile, null, Modifier.size(18.dp), tint = colors.textSecondary)
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    attachment.name,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                    fontFamily = JetBrainsMono,
                    maxLines = 1,
                )
                Text(
                    attachment.summary,
                    fontSize = 10.sp,
                    color = colors.textMuted,
                    fontFamily = JetBrainsMono,
                    maxLines = 2,
                )
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Rounded.Close, null, Modifier.size(18.dp), tint = colors.textMuted)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Input bar
// ─────────────────────────────────────────────────────────────────────────

@Composable
private fun InputBar(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    enabled: Boolean,
    streaming: Boolean,
    hasAttachment: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onPickImage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AgentTerminal.colors
    Column(modifier.fillMaxWidth().background(colors.background)) {
        TerminalHairline()
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onPickImage,
                enabled = enabled && !streaming,
            ) {
                Icon(
                    Icons.Rounded.AttachFile,
                    null,
                    Modifier.size(22.dp),
                    tint = if (enabled && !streaming) colors.textSecondary else colors.textMuted,
                )
            }
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.border, RoundedCornerShape(6.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth().widthIn(min = 0.dp),
                    textStyle = TextStyle(
                            color = colors.accent,
                            fontSize = 14.sp,
                            fontFamily = JetBrainsMono,
                        ),
                    cursorBrush = SolidColor(colors.accent),
                    decorationBox = { inner ->
                        if (value.text.isEmpty()) {
                            Text(
                                Strings.aiCodingPlaceholder,
                                fontSize = 14.sp,
                                color = colors.textMuted,
                                fontFamily = JetBrainsMono,
                            )
                        }
                        inner()
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Default,
                    ),
                )
            }
            Spacer(Modifier.size(4.dp))
            IconButton(
                onClick = if (streaming) onStop else onSend,
                enabled = streaming || (enabled && (value.text.isNotBlank() || hasAttachment)),
            ) {
                Icon(
                    if (streaming) Icons.Rounded.Stop else Icons.AutoMirrored.Rounded.Send,
                    null,
                    Modifier.size(22.dp),
                    tint = if (streaming) colors.error else colors.accent,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Image helpers
// ─────────────────────────────────────────────────────────────────────────

/**
 * Read a content URI, downscale to ~1024px on the long edge and JPEG-encode
 * to base64 so it fits comfortably in a chat payload (vision providers cap
 * at ~5 MB; ~80% JPEG quality at 1024px is well under that).
 */
private fun encodeImage(context: Context, uri: Uri): String? {
    return runCatching {
        val source = context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input)
        } ?: return@runCatching null
        val maxEdge = 1024
        val scale = (maxEdge.toFloat() / maxOf(source.width, source.height)).coerceAtMost(1f)
        val w = (source.width * scale).toInt().coerceAtLeast(1)
        val h = (source.height * scale).toInt().coerceAtLeast(1)
        val scaled = if (scale < 1f) Bitmap.createScaledBitmap(source, w, h, true) else source
        val baos = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 80, baos)
        Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
    }.getOrNull()
}

private fun decodeBitmap(base64: String): Bitmap? = runCatching {
    val bytes = Base64.decode(base64, Base64.DEFAULT)
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}.getOrNull()

// ─────────────────────────────────────────────────────────────────────────
// Selection persistence
// ─────────────────────────────────────────────────────────────────────────

private const val PREFS = "ai_coding_prefs"
private const val KEY_PROVIDER = "coding_provider"
private const val KEY_MODEL = "coding_model"

private fun loadSavedProvider(context: Context, configured: List<AiProviderId>): AiProviderId? {
    val name = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_PROVIDER, null)
    val saved = name?.let { runCatching { AiProviderId.valueOf(it) }.getOrNull() }
    return saved?.takeIf { it in configured } ?: configured.firstOrNull()
}

private fun loadSavedModel(context: Context): String? =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_MODEL, null)?.takeIf { it.isNotBlank() }

private fun saveSelection(context: Context, provider: AiProviderId, modelId: String) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        .putString(KEY_PROVIDER, provider.name)
        .putString(KEY_MODEL, modelId)
        .apply()
}
