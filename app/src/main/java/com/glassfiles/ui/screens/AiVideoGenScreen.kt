package com.glassfiles.ui.screens

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.glassfiles.data.Strings
import com.glassfiles.data.ai.AiAssetHistoryStore
import com.glassfiles.data.ai.AiGallerySaver
import com.glassfiles.data.ai.AiSettingsStore
import com.glassfiles.data.ai.AiKeyStore
import com.glassfiles.data.ai.ModelRegistry
import com.glassfiles.data.ai.models.AiCapability
import com.glassfiles.data.ai.models.AiModel
import com.glassfiles.data.ai.models.AiProviderId
import com.glassfiles.data.ai.providers.AiProviders
import com.glassfiles.ui.components.AiPickerChip
import com.glassfiles.ui.theme.AiModuleSurface
import com.glassfiles.ui.theme.JetBrainsMono
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Video-generation screen.
 *
 * Mirrors [AiImageGenScreen] but for video providers (Google Veo, Alibaba
 * wan-video, …). Generation is async and may take minutes — the screen
 * surfaces the provider's status string ("queue" / "running" / "ready") and
 * lets the user cancel mid-flight.
 *
 * Each result card shows a single extracted frame as a thumbnail (no
 * ExoPlayer dependency needed) and Open / Save / Share actions; Open
 * launches the system video player.
 */
@Composable
fun AiVideoGenScreen(onBack: () -> Unit) {
    AiModuleSurface {
        AiVideoGenScreenInner(onBack)
    }
}

@Composable
private fun AiVideoGenScreenInner(onBack: () -> Unit) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()

    val configured by remember { derivedStateOf { AiKeyStore.configuredProviders(context) } }
    val videoModels = remember { mutableStateListOf<AiModel>() }
    var modelsLoading by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }

    var selected by remember { mutableStateOf<AiModel?>(null) }
    var prompt by remember { mutableStateOf(TextFieldValue("")) }
    var aspect by remember { mutableStateOf("16:9") }
    var duration by remember { mutableStateOf(5) }

    var generating by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var genError by remember { mutableStateOf<String?>(null) }
    var generationJob by remember { mutableStateOf<Job?>(null) }
    val results = remember { mutableStateListOf<VideoResult>() }
    var historyLoaded by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (historyLoaded) return@LaunchedEffect
        AiAssetHistoryStore.list(context, AiAssetHistoryStore.MODE_VIDEO).forEach { rec ->
            val file = File(rec.filePath)
            if (!file.exists()) return@forEach
            val provider = runCatching { AiProviderId.valueOf(rec.providerId) }.getOrNull() ?: return@forEach
            val stubModel = AiModel(
                providerId = provider,
                id = rec.modelId,
                displayName = rec.modelDisplay,
                capabilities = setOf(AiCapability.VIDEO_GEN),
            )
            results.add(
                VideoResult(
                    cacheFile = file,
                    model = stubModel,
                    prompt = rec.prompt,
                    historyId = rec.id,
                    savedTo = rec.savedToGalleryUri,
                ),
            )
        }
        historyLoaded = true
    }

    LaunchedEffect(configured) {
        modelsLoading = true
        loadError = null
        videoModels.clear()
        try {
            // Two-pass load: serve cached lists first, then force-refresh providers
            // whose cache predates the fallback model ids (Qwen wan-video / Grok
            // imagine-video are appended client-side, so a cache filled before that
            // change won't surface them otherwise).
            for (p in configured) {
                val list = ModelRegistry.getModels(
                    context = context,
                    provider = p,
                    apiKey = AiKeyStore.getKey(context, p),
                    force = false,
                ).filter { AiCapability.VIDEO_GEN in it.capabilities }
                videoModels.addAll(list)
            }
            if (videoModels.isEmpty()) {
                for (p in configured) {
                    val key = AiKeyStore.getKey(context, p)
                    if (key.isBlank()) continue
                    val list = ModelRegistry.getModels(
                        context = context,
                        provider = p,
                        apiKey = key,
                        force = true,
                    ).filter { AiCapability.VIDEO_GEN in it.capabilities }
                    videoModels.addAll(list)
                }
            }
            if (selected == null) selected = videoModels.firstOrNull()
        } catch (e: Exception) {
            loadError = e.message ?: e.javaClass.simpleName
        } finally {
            modelsLoading = false
        }
    }

    fun generate() {
        val model = selected ?: return
        val text = prompt.text.trim()
        if (text.isBlank() || generating) return
        val key = AiKeyStore.getKey(context, model.providerId)
        if (key.isBlank()) {
            genError = Strings.aiNoKey
            return
        }
        generating = true
        genError = null
        status = ""
        generationJob = scope.launch {
            try {
                val path = AiProviders.get(model.providerId).generateVideo(
                    context = context,
                    modelId = model.id,
                    prompt = text,
                    apiKey = key,
                    durationSec = duration,
                    aspectRatio = aspect,
                    onProgress = { status = it },
                )
                val cacheFile = File(path)
                var savedUri: String? = null
                if (AiSettingsStore.isAutoSaveGallery(context)) {
                    runCatching {
                        val name = "ai_${System.currentTimeMillis()}.mp4"
                        AiGallerySaver.saveVideo(context, cacheFile, name)
                    }.onSuccess { savedUri = it }
                }
                val record = AiAssetHistoryStore.Record(
                    id = System.currentTimeMillis() + (0..999).random(),
                    mode = AiAssetHistoryStore.MODE_VIDEO,
                    providerId = model.providerId.name,
                    modelId = model.id,
                    modelDisplay = model.displayName,
                    prompt = text,
                    size = aspect,
                    filePath = cacheFile.absolutePath,
                    savedToGalleryUri = savedUri,
                    createdAt = System.currentTimeMillis(),
                )
                AiAssetHistoryStore.add(context, record)
                results.add(
                    0,
                    VideoResult(
                        cacheFile = cacheFile,
                        model = model,
                        prompt = text,
                        historyId = record.id,
                    ),
                )
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    genError = e.message ?: e.javaClass.simpleName
                }
            } finally {
                generating = false
                status = ""
                runCatching {
                    com.glassfiles.data.ai.usage.AiUsageStore.append(
                        context,
                        com.glassfiles.data.ai.usage.AiUsageRecord(
                            providerId = model.providerId.name,
                            modelId = model.id,
                            mode = com.glassfiles.data.ai.usage.AiUsageMode.VIDEO,
                            estimatedInputChars = text.length,
                            estimatedOutputChars = 0,
                            estimated = true,
                        ),
                    )
                }
                generationJob = null
            }
        }
    }

    fun cancel() {
        generationJob?.cancel()
    }

    Column(Modifier.fillMaxSize().background(colors.surface)) {
        // ── Top bar
        Row(
            Modifier.fillMaxWidth().padding(top = 44.dp, start = 4.dp, end = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, Modifier.size(20.dp), tint = colors.onSurface)
            }
            Text(
                Strings.aiVideoGen,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { showHistory = true }) {
                Icon(
                    Icons.Rounded.History,
                    null,
                    Modifier.size(20.dp),
                    tint = colors.onSurfaceVariant,
                )
            }
        }

        if (configured.isEmpty() || (!modelsLoading && videoModels.isEmpty() && loadError == null)) {
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    Strings.aiVideoNoModels,
                    fontSize = 13.sp,
                    color = colors.onSurfaceVariant,
                )
            }
            return@Column
        }

        // ── Controls ────────────────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AiPickerChip(
                label = "MODEL",
                value = selected?.displayName ?: Strings.aiRefreshing,
                title = "Model",
                options = videoModels,
                optionLabel = { it.displayName },
                optionSubtitle = { m ->
                    val caps = m.capabilities.joinToString(" · ") { it.name.lowercase() }
                    if (caps.isBlank()) m.id else "$caps · ${m.id}"
                },
                selected = selected,
                onSelect = { selected = it },
                modifier = Modifier.weight(2f),
            )
            AiPickerChip(
                label = "ASPECT",
                value = aspect,
                title = "Aspect ratio",
                options = aspectOptions,
                optionLabel = { it },
                selected = aspect,
                onSelect = { aspect = it },
                modifier = Modifier.weight(1f),
            )
            AiPickerChip(
                label = "SEC",
                value = duration.toString(),
                title = "Duration (s)",
                options = listOf(2, 3, 4, 5, 6, 8),
                optionLabel = { it.toString() },
                selected = duration,
                onSelect = { duration = it },
                modifier = Modifier.weight(0.7f),
            )
        }

        // ── Prompt input ────────────────────────────────────────────────
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surfaceVariant.copy(alpha = 0.5f))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                Strings.aiVideoPrompt.uppercase(),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp,
                color = colors.onSurfaceVariant,
            )
            BasicTextField(
                value = prompt,
                onValueChange = { prompt = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                textStyle = LocalTextStyle.current.merge(
                    TextStyle(color = colors.onSurface, fontSize = 14.sp),
                ),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.primary),
                decorationBox = { inner ->
                    if (prompt.text.isEmpty()) {
                        Text(
                            Strings.aiVideoPromptHint,
                            fontSize = 14.sp,
                            color = colors.onSurfaceVariant,
                        )
                    }
                    inner()
                },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Default,
                ),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (genError != null) {
                    Text(
                        genError!!,
                        fontSize = 12.sp,
                        color = colors.error,
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                    )
                } else if (generating && status.isNotBlank()) {
                    Text(
                        "${Strings.aiVideoStatus.uppercase()}: $status",
                        fontSize = 11.sp,
                        fontFamily = JetBrainsMono,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                if (generating) {
                    CancelChip(onClick = ::cancel)
                } else {
                    GenerateVideoButton(
                        enabled = selected != null && prompt.text.isNotBlank(),
                        onClick = ::generate,
                    )
                }
            }
        }

        // ── Results ─────────────────────────────────────────────────────
        if (results.isEmpty() && !generating) {
            Box(
                Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    Strings.aiVideoEmpty,
                    fontSize = 13.sp,
                    color = colors.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (generating) {
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = colors.primary,
                                strokeWidth = 1.5.dp,
                            )
                            Spacer(Modifier.size(8.dp))
                            Text(
                                Strings.aiVideoGenerating,
                                fontSize = 13.sp,
                                color = colors.onSurfaceVariant,
                            )
                        }
                    }
                }
                items(results, key = { it.historyId }) { item ->
                    VideoResultCard(
                        item = item,
                        context = context,
                        onDelete = {
                            AiAssetHistoryStore.remove(
                                context,
                                AiAssetHistoryStore.MODE_VIDEO,
                                item.historyId,
                            )
                            runCatching { item.cacheFile.delete() }
                            results.remove(item)
                        },
                        onSavedToGallery = { uri ->
                            AiAssetHistoryStore.list(context, AiAssetHistoryStore.MODE_VIDEO)
                                .firstOrNull { it.id == item.historyId }
                                ?.let { rec ->
                                    AiAssetHistoryStore.update(
                                        context,
                                        rec.copy(savedToGalleryUri = uri),
                                    )
                                }
                        },
                    )
                }
            }
        }
    }

    if (showHistory) {
        VideoHistorySheet(
            items = results.toList(),
            onOpen = { file ->
                openVideo(context, file)
                showHistory = false
            },
            onDelete = { id ->
                AiAssetHistoryStore.remove(context, AiAssetHistoryStore.MODE_VIDEO, id)
                results.firstOrNull { it.historyId == id }?.let { item ->
                    runCatching { item.cacheFile.delete() }
                    results.remove(item)
                }
            },
            onClearAll = {
                results.forEach { runCatching { it.cacheFile.delete() } }
                AiAssetHistoryStore.clear(context, AiAssetHistoryStore.MODE_VIDEO)
                results.clear()
                showHistory = false
            },
            onDismiss = { showHistory = false },
        )
    }
}

@Composable
private fun VideoHistorySheet(
    items: List<VideoResult>,
    onOpen: (File) -> Unit,
    onDelete: (Long) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val sdf = remember { java.text.SimpleDateFormat("dd.MM.yy HH:mm", java.util.Locale.getDefault()) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(colors.surface)
                .padding(vertical = 12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        Strings.aiHistoryVideoTitle,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface,
                    )
                    Text(
                        "${items.size} ${Strings.aiHistoryCount}",
                        fontSize = 11.sp,
                        color = colors.onSurfaceVariant,
                    )
                }
                if (items.isNotEmpty()) {
                    IconButton(onClick = onClearAll) {
                        Icon(Icons.Rounded.DeleteSweep, null, Modifier.size(20.dp), tint = colors.onSurfaceVariant)
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, null, Modifier.size(20.dp), tint = colors.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(8.dp))
            if (items.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                    Text(Strings.aiHistoryEmpty, fontSize = 13.sp, color = colors.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxWidth().heightIn(max = 420.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(items, key = { it.historyId }) { item ->
                        VideoHistoryRow(
                            item = item,
                            sdf = sdf,
                            onOpen = { onOpen(item.cacheFile) },
                            onDelete = { onDelete(item.historyId) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoHistoryRow(
    item: VideoResult,
    sdf: java.text.SimpleDateFormat,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var thumb by remember(item.cacheFile.absolutePath) {
        mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
    }
    LaunchedEffect(item.cacheFile.absolutePath) {
        thumb = withContext(Dispatchers.IO) { extractThumbnail(item.cacheFile) }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surfaceVariant.copy(alpha = 0.5f))
            .clickable(onClick = onOpen)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(colors.surface),
            contentAlignment = Alignment.Center,
        ) {
            val frame = thumb
            if (frame != null) {
                Image(
                    bitmap = frame,
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Icon(
                Icons.Rounded.PlayArrow,
                null,
                Modifier.size(20.dp),
                tint = colors.onSurface.copy(alpha = if (frame != null) 0.85f else 0.5f),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                item.prompt.takeIf { it.isNotBlank() } ?: item.model.displayName,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
                maxLines = 2,
            )
            Text(
                item.model.displayName,
                fontSize = 11.sp,
                color = colors.onSurfaceVariant,
                maxLines = 1,
            )
            Text(
                sdf.format(java.util.Date(item.historyId)),
                fontSize = 10.sp,
                color = colors.onSurfaceVariant,
                fontFamily = JetBrainsMono,
                maxLines = 1,
            )
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Rounded.Close, null, Modifier.size(16.dp), tint = colors.onSurfaceVariant)
        }
    }
}

private val aspectOptions = listOf("16:9", "9:16", "1:1", "4:3", "3:4")

private data class VideoResult(
    val cacheFile: File,
    val model: AiModel,
    val prompt: String = "",
    val historyId: Long = 0L,
    var savedTo: String? = null,
    var saveError: String? = null,
)

@Composable
private fun VideoResultCard(
    item: VideoResult,
    context: Context,
    onDelete: () -> Unit,
    onSavedToGallery: (String) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var saved by remember(item.historyId) { mutableStateOf(item.savedTo != null) }
    var saving by remember(item.historyId) { mutableStateOf(false) }
    var saveError by remember(item.historyId) { mutableStateOf(item.saveError) }
    var thumb by remember(item.cacheFile.absolutePath) {
        mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
    }

    LaunchedEffect(item.cacheFile.absolutePath) {
        thumb = withContext(Dispatchers.IO) { extractThumbnail(item.cacheFile) }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceVariant.copy(alpha = 0.5f))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.surface)
                .clickable { openVideo(context, item.cacheFile) },
            contentAlignment = Alignment.Center,
        ) {
            val bm = thumb
            if (bm != null) {
                Image(
                    bitmap = bm,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(50))
                    .background(colors.surface.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.PlayArrow,
                    null,
                    tint = colors.onSurface,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        Text(
            item.model.id,
            fontSize = 11.sp,
            fontFamily = JetBrainsMono,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            VideoActionChip(
                icon = if (saved) Icons.Rounded.Check else Icons.Rounded.Download,
                label = if (saved) Strings.aiVideoSaved else Strings.aiVideoSaveToGallery,
                primary = !saved,
                onClick = {
                    if (saved || saving) return@VideoActionChip
                    saving = true
                    scope.launch {
                        try {
                            val path = withContext(Dispatchers.IO) {
                                AiGallerySaver.saveVideo(
                                    context = context,
                                    cacheFile = item.cacheFile,
                                    displayName = "AI_${System.currentTimeMillis()}_${item.cacheFile.nameWithoutExtension}.mp4",
                                )
                            }
                            item.savedTo = path
                            saved = true
                            saveError = null
                            onSavedToGallery(path)
                        } catch (e: Exception) {
                            saveError = e.message ?: e.javaClass.simpleName
                            item.saveError = saveError
                        } finally {
                            saving = false
                        }
                    }
                },
            )
            VideoActionChip(
                icon = Icons.Rounded.OpenInNew,
                label = Strings.aiVideoOpen,
                onClick = { openVideo(context, item.cacheFile) },
            )
            VideoActionChip(
                icon = Icons.Rounded.Share,
                label = Strings.aiVideoShare,
                onClick = { shareVideo(context, item.cacheFile) },
            )
            VideoActionChip(
                icon = Icons.Rounded.Delete,
                label = Strings.aiHistoryDelete,
                onClick = onDelete,
            )
        }
        if (item.prompt.isNotBlank()) {
            Text(
                item.prompt,
                fontSize = 11.sp,
                color = colors.onSurfaceVariant,
                maxLines = 2,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        if (saveError != null) {
            Text(
                saveError!!,
                fontSize = 11.sp,
                color = colors.error,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun GenerateVideoButton(enabled: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val bg = if (enabled) colors.primary else colors.surfaceVariant.copy(alpha = 0.5f)
    val fg = if (enabled) colors.onPrimary else colors.onSurfaceVariant
    Row(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            Strings.aiVideoGenerate,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = fg,
        )
    }
}

@Composable
private fun CancelChip(onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(colors.errorContainer.copy(alpha = 0.6f))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            Strings.aiVideoCancel,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.onErrorContainer,
        )
    }
}

@Composable
private fun VideoActionChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    primary: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val bg = if (primary) colors.primary else colors.surfaceVariant.copy(alpha = 0.5f)
    val fg = if (primary) colors.onPrimary else colors.onSurface
    Row(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(14.dp), tint = fg)
        Spacer(Modifier.size(6.dp))
        Text(label, fontSize = 12.sp, color = fg)
    }
}

private fun extractThumbnail(file: File): androidx.compose.ui.graphics.ImageBitmap? = try {
    MediaMetadataRetriever().use { mmr ->
        mmr.setDataSource(file.absolutePath)
        mmr.getFrameAtTime(0)?.asImageBitmap()
    }
} catch (_: Exception) {
    null
}

private fun openVideo(context: Context, file: File) {
    val uri: Uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "video/mp4")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(intent) }
}

private fun shareVideo(context: Context, file: File) {
    val uri: Uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "video/mp4"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, Strings.aiVideoShare))
    }
}
