package com.glassfiles.ui.screens

import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Share
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.glassfiles.data.Strings
import com.glassfiles.data.ai.AiAssetHistoryStore
import com.glassfiles.data.ai.AiGallerySaver
import com.glassfiles.data.ai.AiKeyStore
import com.glassfiles.data.ai.AiSettingsStore
import com.glassfiles.data.ai.ModelRegistry
import com.glassfiles.data.ai.models.AiCapability
import com.glassfiles.data.ai.models.AiModel
import com.glassfiles.data.ai.models.AiProviderId
import com.glassfiles.data.ai.providers.AiProviders
import com.glassfiles.ui.components.AiImageViewer
import com.glassfiles.ui.components.AiPickerChip
import com.glassfiles.ui.components.AiModuleIcon
import com.glassfiles.ui.components.AiModuleIconButton
import com.glassfiles.ui.components.AiModuleText
import com.glassfiles.ui.theme.AiModuleSurface
import com.glassfiles.ui.theme.AiModuleTheme
import com.glassfiles.ui.theme.JetBrainsMono
import kotlinx.coroutines.launch
import java.io.File

/**
 * Image-generation screen.
 *
 * - Lists IMAGE_GEN-capable models from every provider that has a key
 *   configured (per `ModelRegistry`).
 * - User picks model, size, count, types a prompt, hits Generate.
 * - Provider returns absolute paths in `cacheDir/ai_images/`. We render them
 *   inline via Coil's `AsyncImage` and offer "Save to gallery" / "Open" /
 *   "Share" per result.
 */
@Composable
fun AiImageGenScreen(onBack: () -> Unit) {
    AiModuleSurface {
        AiImageGenScreenInner(onBack)
    }
}

@Composable
private fun AiImageGenScreenInner(onBack: () -> Unit) {
    val context = LocalContext.current
    val colors = AiModuleTheme.colors
    val scope = rememberCoroutineScope()

    val configured by remember { derivedStateOf { AiKeyStore.configuredProviders(context) } }
    val imageModels = remember { mutableStateListOf<AiModel>() }
    var modelsLoading by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }

    var selected by remember { mutableStateOf<AiModel?>(null) }
    var prompt by remember { mutableStateOf(TextFieldValue("")) }
    var size by remember { mutableStateOf("1024x1024") }
    var count by remember { mutableStateOf(1) }

    var generating by remember { mutableStateOf(false) }
    var genError by remember { mutableStateOf<String?>(null) }
    val results = remember { mutableStateListOf<ImageResult>() }
    var promptExpanded by remember { mutableStateOf(true) }
    var viewerFile by remember { mutableStateOf<File?>(null) }
    var historyLoaded by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }

    // Restore previous generations from disk so the user can scroll through
    // older outputs after returning to the screen.
    LaunchedEffect(Unit) {
        if (historyLoaded) return@LaunchedEffect
        AiAssetHistoryStore.list(context, AiAssetHistoryStore.MODE_IMAGE).forEach { rec ->
            val file = File(rec.filePath)
            if (!file.exists()) return@forEach
            val provider = runCatching { AiProviderId.valueOf(rec.providerId) }.getOrNull() ?: return@forEach
            val stubModel = AiModel(
                providerId = provider,
                id = rec.modelId,
                displayName = rec.modelDisplay,
                capabilities = setOf(AiCapability.IMAGE_GEN),
            )
            results.add(
                ImageResult(
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

    LaunchedEffect(selected) {
        // When model changes, snap size to a value the new model accepts.
        val opts = sizeOptionsFor(selected)
        if (opts.isNotEmpty() && size !in opts) size = opts.first()
    }

    LaunchedEffect(configured) {
        modelsLoading = true
        loadError = null
        imageModels.clear()
        try {
            // Two-pass: cached first, then force-refresh if nothing turned up.
            // Some providers (xAI, Alibaba) only surface image-gen models via a
            // client-side fallback list, so a stale cache filled before that
            // fallback was added would otherwise hide them indefinitely.
            for (p in configured) {
                val list = ModelRegistry.getModels(
                    context = context,
                    provider = p,
                    apiKey = AiKeyStore.getKey(context, p),
                    force = false,
                ).filter { AiCapability.IMAGE_GEN in it.capabilities }
                imageModels.addAll(list)
            }
            if (imageModels.isEmpty()) {
                for (p in configured) {
                    val key = AiKeyStore.getKey(context, p)
                    if (key.isBlank()) continue
                    val list = ModelRegistry.getModels(
                        context = context,
                        provider = p,
                        apiKey = key,
                        force = true,
                    ).filter { AiCapability.IMAGE_GEN in it.capabilities }
                    imageModels.addAll(list)
                }
            }
            if (selected == null) selected = imageModels.firstOrNull()
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
        promptExpanded = false
        generating = true
        genError = null
        scope.launch {
            try {
                val paths = AiProviders.get(model.providerId).generateImage(
                    context = context,
                    modelId = model.id,
                    prompt = text,
                    apiKey = key,
                    size = size,
                    n = count,
                )
                val autoSave = AiSettingsStore.isAutoSaveGallery(context)
                paths.forEach { path ->
                    val cacheFile = File(path)
                    var savedUri: String? = null
                    if (autoSave) {
                        runCatching {
                            val name = "ai_${System.currentTimeMillis()}.png"
                            AiGallerySaver.saveImage(context, cacheFile, name)
                        }.onSuccess { savedUri = it }
                    }
                    val record = AiAssetHistoryStore.Record(
                        id = System.currentTimeMillis() + (0..999).random(),
                        mode = AiAssetHistoryStore.MODE_IMAGE,
                        providerId = model.providerId.name,
                        modelId = model.id,
                        modelDisplay = model.displayName,
                        prompt = text,
                        size = size,
                        filePath = cacheFile.absolutePath,
                        savedToGalleryUri = savedUri,
                        createdAt = System.currentTimeMillis(),
                    )
                    AiAssetHistoryStore.add(context, record)
                    results.add(
                        0,
                        ImageResult(
                            cacheFile = cacheFile,
                            model = model,
                            prompt = text,
                            historyId = record.id,
                        ),
                    )
                }
            } catch (e: Exception) {
                genError = e.message ?: e.javaClass.simpleName
            } finally {
                generating = false
                runCatching {
                    com.glassfiles.data.ai.usage.AiUsageStore.append(
                        context,
                        com.glassfiles.data.ai.usage.AiUsageRecord(
                            providerId = model.providerId.name,
                            modelId = model.id,
                            mode = com.glassfiles.data.ai.usage.AiUsageMode.IMAGE,
                            estimatedInputChars = text.length,
                            estimatedOutputChars = 0,
                            estimated = true,
                        ),
                    )
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().background(colors.surface)) {
        // ── Top bar
        Row(
            Modifier.fillMaxWidth().padding(top = 44.dp, start = 4.dp, end = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AiModuleIconButton(onClick = onBack) {
                AiModuleIcon(Icons.AutoMirrored.Rounded.ArrowBack, null, Modifier.size(20.dp), tint = colors.textPrimary)
            }
            AiModuleText(
                Strings.aiImageGen,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
            )
            Spacer(Modifier.weight(1f))
            AiModuleIconButton(onClick = { showHistory = true }) {
                AiModuleIcon(
                    Icons.Rounded.History,
                    null,
                    Modifier.size(20.dp),
                    tint = colors.textSecondary,
                )
            }
        }

        if (configured.isEmpty() || (!modelsLoading && imageModels.isEmpty() && loadError == null)) {
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                AiModuleText(
                    Strings.aiImageNoModels,
                    fontSize = 13.sp,
                    color = colors.textSecondary,
                )
            }
            return@Column
        }

        // ── Controls (model / size / count) ─────────────────────────────
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AiPickerChip(
                label = "MODEL",
                value = selected?.displayName ?: Strings.aiRefreshing,
                title = "Model",
                options = imageModels,
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
                label = "SIZE",
                value = size,
                title = "Size",
                options = sizeOptionsFor(selected),
                optionLabel = { it },
                selected = size,
                onSelect = { size = it },
                modifier = Modifier.weight(1f),
            )
            AiPickerChip(
                label = "N",
                value = count.toString(),
                title = "Count",
                options = listOf(1, 2, 3, 4),
                optionLabel = { it.toString() },
                selected = count,
                onSelect = { count = it },
                modifier = Modifier.weight(0.6f),
            )
        }

        // ── Prompt input ─────────────────────────────────────────────────
        if (promptExpanded) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surfaceElevated.copy(alpha = 0.5f))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AiModuleText(
                    Strings.aiImagePrompt.uppercase(),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp,
                    color = colors.textSecondary,
                )
                BasicTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    textStyle = TextStyle(color = colors.textPrimary, fontSize = 14.sp),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.accent),
                    decorationBox = { inner ->
                        if (prompt.text.isEmpty()) {
                            AiModuleText(
                                Strings.aiImagePromptHint,
                                fontSize = 14.sp,
                                color = colors.textSecondary,
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
                        AiModuleText(
                            genError!!,
                            fontSize = 12.sp,
                            color = colors.error,
                            modifier = Modifier.weight(1f).padding(end = 8.dp),
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    GenerateButton(
                        enabled = !generating && selected != null && prompt.text.isNotBlank(),
                        generating = generating,
                        onClick = ::generate,
                    )
                }
            }
        } else {
            // Collapsed: one-line preview that re-expands on tap.
            CollapsedPromptBar(
                prompt = prompt.text,
                generating = generating,
                onExpand = { promptExpanded = true },
                onGenerate = ::generate,
                generateEnabled = !generating && selected != null && prompt.text.isNotBlank(),
            )
        }

        // ── Results
        if (results.isEmpty() && !generating) {
            Box(
                Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                AiModuleText(
                    Strings.aiImageEmpty,
                    fontSize = 13.sp,
                    color = colors.textSecondary,
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
                            AiModuleText("...", color = colors.accent, fontFamily = JetBrainsMono, fontSize = 13.sp)
                            Spacer(Modifier.size(8.dp))
                            AiModuleText(
                                Strings.aiImageGenerating,
                                fontSize = 13.sp,
                                color = colors.textSecondary,
                            )
                        }
                    }
                }
                items(results, key = { it.historyId }) { item ->
                    ImageResultCard(
                        item = item,
                        context = context,
                        onOpenViewer = { viewerFile = it },
                        onDelete = {
                            AiAssetHistoryStore.remove(
                                context,
                                AiAssetHistoryStore.MODE_IMAGE,
                                item.historyId,
                            )
                            runCatching { item.cacheFile.delete() }
                            results.remove(item)
                        },
                        onSavedToGallery = { uri ->
                            // Persist the gallery uri so the saved
                            // checkmark survives a screen rotation / restart.
                            AiAssetHistoryStore.list(context, AiAssetHistoryStore.MODE_IMAGE)
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

    val vf = viewerFile
    if (vf != null) {
        AiImageViewer(file = vf, onDismiss = { viewerFile = null })
    }
    if (showHistory) {
        ImageHistorySheet(
            items = results.toList(),
            onOpen = { file ->
                viewerFile = file
                showHistory = false
            },
            onDelete = { id ->
                AiAssetHistoryStore.remove(context, AiAssetHistoryStore.MODE_IMAGE, id)
                results.firstOrNull { it.historyId == id }?.let { item ->
                    runCatching { item.cacheFile.delete() }
                    results.remove(item)
                }
            },
            onClearAll = {
                results.forEach { runCatching { it.cacheFile.delete() } }
                AiAssetHistoryStore.clear(context, AiAssetHistoryStore.MODE_IMAGE)
                results.clear()
                showHistory = false
            },
            onDismiss = { showHistory = false },
        )
    }
}

@Composable
private fun ImageHistorySheet(
    items: List<ImageResult>,
    onOpen: (File) -> Unit,
    onDelete: (Long) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = AiModuleTheme.colors
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
                    AiModuleText(
                        Strings.aiHistoryImageTitle,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                    )
                    AiModuleText(
                        "${items.size} ${Strings.aiHistoryCount}",
                        fontSize = 11.sp,
                        color = colors.textSecondary,
                    )
                }
                if (items.isNotEmpty()) {
                    AiModuleIconButton(onClick = onClearAll) {
                        AiModuleIcon(Icons.Rounded.DeleteSweep, null, Modifier.size(20.dp), tint = colors.textSecondary)
                    }
                }
                AiModuleIconButton(onClick = onDismiss) {
                    AiModuleIcon(Icons.Rounded.Close, null, Modifier.size(20.dp), tint = colors.textSecondary)
                }
            }
            Spacer(Modifier.height(8.dp))
            if (items.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                    AiModuleText(Strings.aiHistoryEmpty, fontSize = 13.sp, color = colors.textSecondary)
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxWidth().heightIn(max = 420.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(items, key = { it.historyId }) { item ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.surfaceElevated.copy(alpha = 0.5f))
                                .clickable { onOpen(item.cacheFile) }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            AsyncImage(
                                model = item.cacheFile,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.surface),
                            )
                            Column(Modifier.weight(1f)) {
                                AiModuleText(
                                    item.prompt.takeIf { it.isNotBlank() } ?: item.model.displayName,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.textPrimary,
                                    maxLines = 2,
                                )
                                AiModuleText(
                                    item.model.displayName,
                                    fontSize = 11.sp,
                                    color = colors.textSecondary,
                                    maxLines = 1,
                                )
                                AiModuleText(
                                    sdf.format(java.util.Date(item.historyId)),
                                    fontSize = 10.sp,
                                    color = colors.textSecondary,
                                    fontFamily = JetBrainsMono,
                                    maxLines = 1,
                                )
                            }
                            AiModuleIconButton(
                                onClick = { onDelete(item.historyId) },
                                modifier = Modifier.size(28.dp),
                            ) {
                                AiModuleIcon(Icons.Rounded.Close, null, Modifier.size(16.dp), tint = colors.textSecondary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CollapsedPromptBar(
    prompt: String,
    generating: Boolean,
    generateEnabled: Boolean,
    onExpand: () -> Unit,
    onGenerate: () -> Unit,
) {
    val colors = AiModuleTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceElevated.copy(alpha = 0.5f))
            .clickable(enabled = !generating) { onExpand() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AiModuleIcon(
            Icons.Rounded.Edit,
            null,
            Modifier.size(14.dp),
            tint = colors.textSecondary,
        )
        Spacer(Modifier.size(8.dp))
        AiModuleText(
            text = prompt.ifBlank { Strings.aiImagePromptHint },
            fontSize = 13.sp,
            color = if (prompt.isBlank()) colors.textSecondary else colors.textPrimary,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.size(8.dp))
        GenerateButton(
            enabled = generateEnabled,
            generating = generating,
            onClick = onGenerate,
        )
    }
}

@Composable
private fun GenerateButton(enabled: Boolean, generating: Boolean, onClick: () -> Unit) {
    val colors = AiModuleTheme.colors
    val bg = if (enabled) colors.accent else colors.surfaceElevated.copy(alpha = 0.5f)
    val fg = if (enabled) colors.background else colors.textSecondary
    Row(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (generating) {
            AiModuleText("...", color = fg, fontFamily = JetBrainsMono, fontSize = 12.sp)
            Spacer(Modifier.size(8.dp))
        }
        AiModuleText(
            (if (generating) Strings.aiImageGenerating else Strings.aiImageGenerate),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = fg,
        )
    }
}

private data class ImageResult(
    val cacheFile: File,
    val model: AiModel,
    val prompt: String = "",
    val historyId: Long = 0L,
    var savedTo: String? = null,
)

@Composable
private fun ImageResultCard(
    item: ImageResult,
    context: Context,
    onOpenViewer: (File) -> Unit,
    onDelete: () -> Unit,
    onSavedToGallery: (String) -> Unit,
) {
    val colors = AiModuleTheme.colors
    val scope = rememberCoroutineScope()
    var saved by remember(item.historyId) { mutableStateOf(item.savedTo != null) }
    var savingError by remember(item.historyId) { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceElevated.copy(alpha = 0.5f))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AsyncImage(
            model = item.cacheFile,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 200.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.surface)
                .clickable { onOpenViewer(item.cacheFile) },
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AiModuleIcon(Icons.Rounded.Image, null, Modifier.size(14.dp), tint = colors.textSecondary)
            Spacer(Modifier.size(6.dp))
            AiModuleText(
                item.model.displayName,
                fontSize = 11.sp,
                fontFamily = JetBrainsMono,
                color = colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            if (savingError != null) {
                AiModuleText(
                    savingError!!.take(40),
                    fontSize = 10.sp,
                    color = colors.error,
                    modifier = Modifier.padding(end = 4.dp),
                )
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ActionPill(
                icon = if (saved) Icons.Rounded.Check else Icons.Rounded.Download,
                label = if (saved) Strings.aiImageSaved else Strings.aiImageSaveToGallery,
                primary = !saved,
                onClick = {
                    if (saved) return@ActionPill
                    scope.launch {
                        try {
                            val name = "GlassFiles_${System.currentTimeMillis()}.${item.cacheFile.extension}"
                            val uri = AiGallerySaver.saveImage(context, item.cacheFile, name)
                            saved = true
                            savingError = null
                            onSavedToGallery(uri)
                        } catch (e: Exception) {
                            savingError = e.message
                        }
                    }
                },
                modifier = Modifier.weight(1f),
            )
            ActionPill(
                icon = Icons.Rounded.OpenInNew,
                label = Strings.aiImageOpen,
                primary = false,
                onClick = { openInViewer(context, item.cacheFile) },
            )
            ActionPill(
                icon = Icons.Rounded.Share,
                label = Strings.aiImageShare,
                primary = false,
                onClick = { shareFile(context, item.cacheFile) },
            )
            ActionPill(
                icon = Icons.Rounded.Delete,
                label = Strings.aiHistoryDelete,
                primary = false,
                onClick = onDelete,
            )
        }
        if (item.prompt.isNotBlank()) {
            AiModuleText(
                item.prompt,
                fontSize = 11.sp,
                color = colors.textSecondary,
                maxLines = 2,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun ActionPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AiModuleTheme.colors
    val bg = if (primary) colors.accent else colors.surface
    val fg = if (primary) colors.background else colors.textPrimary
    Row(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AiModuleIcon(icon, null, Modifier.size(14.dp), tint = fg)
        AiModuleText(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = fg)
    }
}

private fun sizeOptionsFor(model: AiModel?): List<String> {
    val id = model?.id?.lowercase().orEmpty()
    return when {
        // gpt-image-1 supports "auto" natively — model picks the resolution itself.
        id.contains("gpt-image") -> listOf("auto", "1024x1024", "1024x1536", "1536x1024")
        id.contains("dall-e-2") || id.contains("dalle-2") -> listOf("auto", "256x256", "512x512", "1024x1024")
        id.contains("dall-e-3") || id.contains("dalle-3") -> listOf("auto", "1024x1024", "1792x1024", "1024x1792")
        id.contains("imagen") -> listOf("auto", "1024x1024", "1408x768", "768x1408")
        // xAI `grok-2-image` only generates at 1024x768 and rejects the `size`
        // field entirely. Auto is the only sensible option here.
        id.contains("grok") || id.contains("imagine") -> listOf("auto")
        // Alibaba wanx — square + a few common ratios; auto skips the size field
        // so the model uses its default (typically 1024x1024).
        id.contains("wanx") || id.contains("-t2i-") -> listOf("auto", "1024x1024", "1280x720", "720x1280")
        else -> listOf("auto", "512x512", "1024x1024", "1024x1792", "1792x1024")
    }
}

private fun openInViewer(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "image/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, Strings.aiImageOpen))
}

private fun shareFile(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/*"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, Strings.aiImageShare))
}
