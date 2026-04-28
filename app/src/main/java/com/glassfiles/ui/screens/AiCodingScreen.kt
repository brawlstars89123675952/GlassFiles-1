package com.glassfiles.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glassfiles.data.Strings
import com.glassfiles.data.ai.AiKeyStore
import com.glassfiles.data.ai.ModelRegistry
import com.glassfiles.data.ai.SystemPrompts
import com.glassfiles.data.ai.models.AiCapability
import com.glassfiles.data.ai.models.AiMessage
import com.glassfiles.data.ai.models.AiModel
import com.glassfiles.data.ai.models.AiProviderId
import com.glassfiles.data.ai.providers.AiProviders
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Coding-focused chat. Distinct from [AiChatScreen]:
 *  - Only models classified as [AiCapability.CODING] are picker-eligible.
 *  - The assistant runs with [SystemPrompts.CODING] regardless of provider.
 *  - The transcript renders code fences as monospace cards with a "Copy" action.
 */
@Composable
fun AiCodingScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()

    val configured by remember { derivedStateOf { AiKeyStore.configuredProviders(context) } }
    var provider by remember(configured) { mutableStateOf(loadSavedProvider(context, configured)) }
    val codingModels = remember { mutableStateListOf<AiModel>() }
    var modelId by remember { mutableStateOf("") }
    var loadingModels by remember { mutableStateOf(false) }
    var modelLoadError by remember { mutableStateOf<String?>(null) }

    val transcript = remember { mutableStateListOf<CodingMessage>() }
    var draft by remember { mutableStateOf(TextFieldValue("")) }
    var streaming by remember { mutableStateOf(false) }
    var streamJob by remember { mutableStateOf<Job?>(null) }

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
            // Persisted modelId may belong to a different provider; reset if so.
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

    fun send() {
        val p = provider ?: return
        val mid = modelId.takeIf { it.isNotBlank() } ?: return
        val text = draft.text.trim()
        if (text.isBlank() || streaming) return
        val key = AiKeyStore.getKey(context, p)
        if (key.isBlank()) {
            transcript += CodingMessage("assistant", Strings.aiCodingNeedKey, isError = true)
            return
        }
        transcript += CodingMessage("user", text)
        transcript += CodingMessage("assistant", "")
        draft = TextFieldValue("")
        streaming = true

        streamJob = scope.launch {
            try {
                val msgs = buildList {
                    add(AiMessage("system", SystemPrompts.CODING))
                    addAll(
                        transcript
                            .filter { !it.isError && (it.role == "user" || it.role == "assistant") }
                            .dropLast(1) // drop the empty placeholder we just appended
                            .map { AiMessage(it.role, it.content) },
                    )
                }
                val full = AiProviders.get(p).chat(
                    context = context,
                    modelId = mid,
                    messages = msgs,
                    apiKey = key,
                    onChunk = { chunk ->
                        val last = transcript.lastIndex
                        if (last >= 0) {
                            transcript[last] = transcript[last].copy(content = transcript[last].content + chunk)
                        }
                    },
                )
                val last = transcript.lastIndex
                if (last >= 0 && transcript[last].content.isBlank()) {
                    transcript[last] = transcript[last].copy(content = full)
                }
            } catch (e: Exception) {
                val last = transcript.lastIndex
                val err = "${e.javaClass.simpleName}: ${e.message ?: ""}"
                if (last >= 0) {
                    transcript[last] = transcript[last].copy(content = err, isError = true)
                } else {
                    transcript += CodingMessage("assistant", err, isError = true)
                }
            } finally {
                streaming = false
                streamJob = null
            }
        }
    }

    fun stop() {
        streamJob?.cancel()
        streamJob = null
        streaming = false
    }

    Column(Modifier.fillMaxSize().background(colors.surface)) {
        // ── Top bar ─────────────────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth().padding(top = 44.dp, start = 4.dp, end = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, Modifier.size(20.dp), tint = colors.onSurface)
            }
            Text(
                Strings.aiCoding,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (transcript.isNotEmpty()) {
                IconButton(onClick = { transcript.clear() }) {
                    Icon(
                        Icons.Rounded.DeleteSweep,
                        null,
                        Modifier.size(20.dp),
                        tint = colors.onSurfaceVariant,
                    )
                }
            }
        }

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

        // ── Transcript ──────────────────────────────────────────────────
        if (transcript.isEmpty()) {
            Box(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    Strings.aiCodingHint,
                    fontSize = 13.sp,
                    color = colors.onSurfaceVariant,
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
                        Row(
                            Modifier.fillMaxWidth().padding(start = 12.dp, top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                color = colors.primary,
                                strokeWidth = 1.dp,
                            )
                        }
                    }
                }
            }
        }

        // ── Input bar ───────────────────────────────────────────────────
        InputBar(
            value = draft,
            onValueChange = { draft = it },
            enabled = provider != null && modelId.isNotBlank(),
            streaming = streaming,
            onSend = ::send,
            onStop = ::stop,
        )
    }
}

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
    val colors = MaterialTheme.colorScheme

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
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PickerChip(
            label = Strings.aiCodingPickProvider,
            value = provider?.displayName ?: "—",
            onSelect = { onProviderChange(it) },
            options = configured,
            optionLabel = { it.displayName },
            modifier = Modifier.weight(1f),
        )
        PickerChip(
            label = Strings.aiCodingPickModel,
            value = when {
                loadingModels -> Strings.aiRefreshing
                modelLoadError != null -> Strings.error
                modelId.isNotBlank() -> codingModels.firstOrNull { it.id == modelId }?.displayName ?: modelId
                codingModels.isEmpty() -> Strings.aiCodingNoCoding
                else -> "—"
            },
            onSelect = { onModelChange(it.id) },
            options = codingModels,
            optionLabel = { it.displayName },
            modifier = Modifier.weight(1.5f),
        )
    }
}

@Composable
private fun <T> PickerChip(
    label: String,
    value: String,
    onSelect: (T) -> Unit,
    options: List<T>,
    optionLabel: (T) -> String,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    var expanded by remember { mutableStateOf(false) }

    Box(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(colors.surfaceVariant.copy(alpha = 0.5f))
                .clickable(enabled = options.isNotEmpty()) { expanded = true }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    label.uppercase(),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp,
                    color = colors.onSurfaceVariant,
                )
                Text(
                    value,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                    maxLines = 1,
                )
            }
            Icon(
                Icons.Rounded.ExpandMore,
                null,
                Modifier.size(16.dp),
                tint = colors.onSurfaceVariant,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option), fontSize = 13.sp, color = colors.onSurface) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Transcript renderer
// ─────────────────────────────────────────────────────────────────────────

private data class CodingMessage(
    val role: String,
    val content: String,
    val isError: Boolean = false,
)

@Composable
private fun CodingMessageRow(message: CodingMessage, context: Context) {
    val colors = MaterialTheme.colorScheme
    val isUser = message.role == "user"
    val container = when {
        message.isError -> colors.error.copy(alpha = 0.10f)
        isUser -> colors.primary.copy(alpha = 0.10f)
        else -> colors.surfaceVariant.copy(alpha = 0.5f)
    }
    val onContainer = when {
        message.isError -> colors.error
        else -> colors.onSurface
    }

    Box(
        Modifier
            .fillMaxWidth()
            .padding(
                start = if (isUser) 32.dp else 0.dp,
                end = if (isUser) 0.dp else 32.dp,
            ),
    ) {
        Column(
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(container)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            renderMessageBody(message.content, onContainer, context)
        }
    }
}

@Composable
private fun renderMessageBody(content: String, textColor: androidx.compose.ui.graphics.Color, context: Context) {
    val colors = MaterialTheme.colorScheme
    val parts = splitOnFences(content)
    parts.forEach { part ->
        if (part.isFence) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.surface)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        part.lang.ifBlank { "code" }.uppercase(),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp,
                        color = colors.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f),
                    )
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("code", part.text))
                            }
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Rounded.ContentCopy,
                            null,
                            Modifier.size(12.dp),
                            tint = colors.primary,
                        )
                        Spacer(Modifier.size(4.dp))
                        Text(
                            Strings.aiCodingCopy,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.primary,
                        )
                    }
                }
                Text(
                    part.text,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = colors.onSurface,
                )
            }
        } else if (part.text.isNotBlank()) {
            Text(
                part.text,
                fontSize = 14.sp,
                color = textColor,
            )
        }
    }
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
// Input bar
// ─────────────────────────────────────────────────────────────────────────

@Composable
private fun InputBar(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    enabled: Boolean,
    streaming: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(20.dp))
                .background(colors.surfaceVariant.copy(alpha = 0.5f))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().widthIn(min = 0.dp),
                textStyle = LocalTextStyle.current.merge(
                    TextStyle(
                        color = colors.onSurface,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                    ),
                ),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.primary),
                decorationBox = { inner ->
                    if (value.text.isEmpty()) {
                        Text(
                            Strings.aiCodingPlaceholder,
                            fontSize = 14.sp,
                            color = colors.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace,
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
        Spacer(Modifier.size(8.dp))
        IconButton(
            onClick = if (streaming) onStop else onSend,
            enabled = streaming || (enabled && value.text.isNotBlank()),
        ) {
            Icon(
                if (streaming) Icons.Rounded.Stop else Icons.AutoMirrored.Rounded.Send,
                null,
                Modifier.size(20.dp),
                tint = if (streaming) colors.error else colors.primary,
            )
        }
    }
}

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
