package com.glassfiles.ui.screens

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.glassfiles.data.Strings
import com.glassfiles.data.ai.AiAssetHistoryStore
import com.glassfiles.data.ai.AiGallerySaver
import com.glassfiles.data.ai.AiKeyStore
import com.glassfiles.data.ai.AiSettingsStore
import com.glassfiles.data.ai.ModelRegistry
import com.glassfiles.data.ai.models.AiCapability
import com.glassfiles.data.ai.models.AiModel
import com.glassfiles.data.ai.models.AiProviderId
import com.glassfiles.data.ai.providers.AiMusicRequest
import com.glassfiles.data.ai.providers.AiMusicResult
import com.glassfiles.data.ai.providers.AiProviders
import com.glassfiles.data.ai.usage.AiUsageMode
import com.glassfiles.data.ai.usage.AiUsageRecord
import com.glassfiles.data.ai.usage.AiUsageStore
import com.glassfiles.ui.screens.ai.terminal.AgentTerminal
import com.glassfiles.ui.screens.ai.terminal.TerminalCard
import com.glassfiles.ui.screens.ai.terminal.TerminalCheckRow
import com.glassfiles.ui.screens.ai.terminal.TerminalChip
import com.glassfiles.ui.screens.ai.terminal.TerminalHairline
import com.glassfiles.ui.screens.ai.terminal.TerminalPillButton
import com.glassfiles.ui.screens.ai.terminal.TerminalScreenScaffold
import com.glassfiles.ui.screens.ai.terminal.TerminalSectionLabel
import com.glassfiles.ui.screens.ai.terminal.Text
import com.glassfiles.ui.theme.JetBrainsMono
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun AiMusicGenScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val providerId = AiProviderId.ACEMUSIC

    val models = remember { mutableStateListOf<AiModel>() }
    val results = remember { mutableStateListOf<MusicResultItem>() }
    val playback = remember { MusicPlaybackController() }
    var selected by remember { mutableStateOf<AiModel?>(null) }
    var modelsLoading by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var historyLoaded by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }

    var prompt by remember { mutableStateOf("") }
    var lyrics by remember { mutableStateOf("") }
    var sampleMode by remember { mutableStateOf(false) }
    var duration by remember { mutableStateOf("60") }
    var bpm by remember { mutableStateOf("") }
    var keyScale by remember { mutableStateOf("") }
    var timeSignature by remember { mutableStateOf("4") }
    var language by remember { mutableStateOf("en") }
    var format by remember { mutableStateOf("mp3") }
    var batch by remember { mutableStateOf(1) }
    var thinking by remember { mutableStateOf(true) }
    var useFormat by remember { mutableStateOf(false) }
    var randomSeed by remember { mutableStateOf(true) }
    var seed by remember { mutableStateOf("") }
    var inferenceSteps by remember { mutableStateOf("8") }
    var guidanceScale by remember { mutableStateOf("7") }
    var shift by remember { mutableStateOf("3") }
    var inferMethod by remember { mutableStateOf("ode") }
    var timesteps by remember { mutableStateOf("") }
    var useAdg by remember { mutableStateOf(false) }
    var cfgStart by remember { mutableStateOf("") }
    var cfgEnd by remember { mutableStateOf("") }
    var useCotCaption by remember { mutableStateOf(true) }
    var useCotLanguage by remember { mutableStateOf(false) }
    var constrainedDecoding by remember { mutableStateOf(true) }
    var allowLmBatch by remember { mutableStateOf(true) }
    var lmTemperature by remember { mutableStateOf("0.85") }
    var lmCfgScale by remember { mutableStateOf("2.5") }
    var lmNegativePrompt by remember { mutableStateOf("") }
    var lmTopK by remember { mutableStateOf("") }
    var lmTopP by remember { mutableStateOf("0.9") }
    var lmRepetitionPenalty by remember { mutableStateOf("1") }
    var advanced by remember { mutableStateOf(false) }

    var generating by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var genError by remember { mutableStateOf<String?>(null) }
    var generationJob by remember { mutableStateOf<Job?>(null) }

    DisposableEffect(Unit) {
        onDispose { playback.release() }
    }

    LaunchedEffect(playback.isPlaying, playback.playingPath) {
        while (playback.isPlaying) {
            playback.refreshPosition()
            delay(500)
        }
    }

    fun loadModels(force: Boolean = false) {
        if (modelsLoading) return
        modelsLoading = true
        loadError = null
        scope.launch {
            try {
                val key = AiKeyStore.getKey(context, providerId)
                val list = if (force) {
                    ModelRegistry.refreshOrThrow(context, providerId, key)
                } else {
                    ModelRegistry.getModels(context, providerId, key, force = false)
                }
                    .filter { AiCapability.MUSIC_GEN in it.capabilities }
                models.clear()
                models.addAll(list)
                selected = selected?.let { current -> list.firstOrNull { it.id == current.id } } ?: list.firstOrNull()
            } catch (e: Exception) {
                loadError = e.message ?: e.javaClass.simpleName
            } finally {
                modelsLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!historyLoaded) {
            AiAssetHistoryStore.list(context, AiAssetHistoryStore.MODE_MUSIC).forEach { rec ->
                val file = File(rec.filePath)
                if (!file.exists()) return@forEach
                val provider = runCatching { AiProviderId.valueOf(rec.providerId) }.getOrNull() ?: providerId
                results.add(
                    MusicResultItem(
                        cacheFile = file,
                        model = AiModel(provider, rec.modelId, rec.modelDisplay, setOf(AiCapability.MUSIC_GEN)),
                        prompt = rec.prompt,
                        meta = rec.size,
                        historyId = rec.id,
                        savedTo = rec.savedToGalleryUri,
                    ),
                )
            }
            historyLoaded = true
        }
        loadModels(force = false)
    }

    fun generate() {
        val model = selected ?: return
        val key = AiKeyStore.getKey(context, providerId)
        if (key.isBlank()) {
            genError = Strings.aiNoKey
            return
        }
        val cleanPrompt = prompt.trim()
        val cleanLyrics = lyrics.trim()
        if (cleanPrompt.isBlank() && cleanLyrics.isBlank()) return
        generating = true
        genError = null
        status = "submit"
        val request = AiMusicRequest(
            prompt = if (sampleMode) "" else cleanPrompt,
            lyrics = if (sampleMode) "" else cleanLyrics,
            sampleMode = sampleMode,
            sampleQuery = if (sampleMode) cleanPrompt else "",
            useFormat = useFormat,
            thinking = thinking,
            vocalLanguage = language,
            audioFormat = format,
            durationSec = duration.toFloatOrNull(),
            bpm = bpm.toIntOrNull(),
            keyScale = keyScale.trim(),
            timeSignature = timeSignature.trim(),
            inferenceSteps = inferenceSteps.toIntOrNull() ?: 8,
            guidanceScale = guidanceScale.toFloatOrNull() ?: 7f,
            useRandomSeed = randomSeed,
            seed = seed.toIntOrNull(),
            batchSize = batch,
            shift = shift.toFloatOrNull() ?: 3f,
            inferMethod = inferMethod,
            timesteps = timesteps.trim(),
            useAdg = useAdg,
            cfgIntervalStart = cfgStart.toFloatOrNull(),
            cfgIntervalEnd = cfgEnd.toFloatOrNull(),
            useCotCaption = useCotCaption,
            useCotLanguage = useCotLanguage,
            constrainedDecoding = constrainedDecoding,
            allowLmBatch = allowLmBatch,
            lmTemperature = lmTemperature.toFloatOrNull(),
            lmCfgScale = lmCfgScale.toFloatOrNull(),
            lmNegativePrompt = lmNegativePrompt.trim(),
            lmTopK = lmTopK.toIntOrNull(),
            lmTopP = lmTopP.toFloatOrNull(),
            lmRepetitionPenalty = lmRepetitionPenalty.toFloatOrNull(),
        )
        generationJob = scope.launch {
            try {
                val generated = AiProviders.get(providerId).generateMusic(
                    context = context,
                    modelId = model.id,
                    request = request,
                    apiKey = key,
                    onProgress = { status = it },
                )
                generated.forEach { item ->
                    val cacheFile = File(item.filePath)
                    var savedUri: String? = null
                    if (AiSettingsStore.isAutoSaveGallery(context)) {
                        runCatching {
                            AiGallerySaver.saveAudio(
                                context = context,
                                cacheFile = cacheFile,
                                displayName = "AI_${System.currentTimeMillis()}_${cacheFile.name}",
                                mimeType = mimeFor(cacheFile.extension),
                            )
                        }.onSuccess { savedUri = it }
                    }
                    val meta = item.metaSummary(format)
                    val record = AiAssetHistoryStore.Record(
                        id = System.currentTimeMillis() + (0..999).random(),
                        mode = AiAssetHistoryStore.MODE_MUSIC,
                        providerId = model.providerId.name,
                        modelId = model.id,
                        modelDisplay = model.displayName,
                        prompt = cleanPrompt.ifBlank { item.prompt },
                        size = meta,
                        filePath = cacheFile.absolutePath,
                        savedToGalleryUri = savedUri,
                        createdAt = System.currentTimeMillis(),
                    )
                    AiAssetHistoryStore.add(context, record)
                    results.add(
                        0,
                        MusicResultItem(
                            cacheFile = cacheFile,
                            model = model,
                            prompt = cleanPrompt.ifBlank { item.prompt },
                            meta = meta,
                            historyId = record.id,
                            savedTo = savedUri,
                        ),
                    )
                }
                runCatching {
                    AiUsageStore.append(
                        context,
                        AiUsageRecord(
                            providerId = providerId.name,
                            modelId = model.id,
                            mode = AiUsageMode.MUSIC,
                            estimatedInputChars = request.prompt.length + request.lyrics.length + request.sampleQuery.length,
                            estimatedOutputChars = 0,
                            estimated = true,
                        ),
                    )
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    genError = e.message ?: e.javaClass.simpleName
                }
            } finally {
                generating = false
                status = ""
                generationJob = null
            }
        }
    }

    fun cancel() {
        generationJob?.cancel()
    }

    TerminalScreenScaffold(
        title = "> ai/music",
        onBack = onBack,
        subtitle = "ACEMusic · api.acemusic.ai",
        trailing = {
            TerminalPillButton(
                label = "history",
                onClick = { showHistory = true },
                accent = false,
                enabled = results.isNotEmpty(),
            )
        },
    ) {
        val colors = AgentTerminal.colors
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(colors.background),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                TerminalCard {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TerminalSectionLabel("> provider")
                            Spacer(Modifier.weight(1f))
                            TerminalChip("ACEMUSIC", color = colors.accent)
                        }
                        if (AiKeyStore.getKey(context, providerId).isBlank()) {
                            Text(
                                "${Strings.aiNoKey}: AI -> API-ключи -> ACEMusic",
                                color = colors.warning,
                                fontFamily = JetBrainsMono,
                                fontSize = 12.sp,
                            )
                        }
                        if (loadError != null) {
                            Text(loadError!!, color = colors.warning, fontFamily = JetBrainsMono, fontSize = 12.sp)
                        }
                        ModelPicker(
                            models = models,
                            selected = selected,
                            loading = modelsLoading,
                            onSelect = { selected = it },
                            onRefresh = { loadModels(force = true) },
                        )
                    }
                }
            }

            item {
                TerminalCard {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TerminalPillButton("caption", onClick = { sampleMode = false }, accent = !sampleMode)
                            TerminalPillButton("sample", onClick = { sampleMode = true }, accent = sampleMode)
                            Spacer(Modifier.weight(1f))
                            TerminalChip(if (sampleMode) "sample_query" else "prompt+lyrics", color = colors.textSecondary)
                        }
                        TerminalField(
                            label = if (sampleMode) Strings.aiMusicPrompt else Strings.aiMusicPrompt,
                            value = prompt,
                            onValueChange = { prompt = it },
                            placeholder = Strings.aiMusicPromptHint,
                            minHeight = 74.dp,
                        )
                        if (!sampleMode) {
                            TerminalField(
                                label = Strings.aiMusicLyrics,
                                value = lyrics,
                                onValueChange = { lyrics = it },
                                placeholder = Strings.aiMusicLyricsHint,
                                minHeight = 118.dp,
                            )
                        }
                    }
                }
            }

            item {
                TerminalCard {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        TerminalSectionLabel("> controls")
                        TerminalOptionRow(Strings.aiMusicDuration, duration, listOf("30", "60", "120", "180", "240")) { duration = it }
                        TerminalOptionRow(Strings.aiMusicLanguage, language, listOf("en", "zh", "ja", "ko", "es", "fr", "de", "ru")) { language = it }
                        TerminalOptionRow(Strings.aiMusicFormat, format, listOf("mp3", "wav", "wav32", "flac", "aac", "opus")) { format = it }
                        TerminalOptionRow(Strings.aiMusicBatch, batch.toString(), listOf("1", "2", "3", "4")) { batch = it.toInt() }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TerminalSmallField(Strings.aiMusicBpm, bpm, { bpm = it.filter(Char::isDigit).take(3) }, "auto", Modifier.weight(1f))
                            TerminalSmallField(Strings.aiMusicKey, keyScale, { keyScale = it }, "C Major", Modifier.weight(1f))
                            TerminalSmallField(Strings.aiMusicTimeSignature, timeSignature, { timeSignature = it.filter(Char::isDigit).take(1) }, "4", Modifier.weight(0.7f))
                        }
                        TerminalHairline()
                        TerminalPillButton(
                            label = if (advanced) "hide ${Strings.aiMusicAdvanced}" else Strings.aiMusicAdvanced,
                            onClick = { advanced = !advanced },
                            accent = advanced,
                        )
                        if (advanced) {
                            TerminalCheckRow(Strings.aiMusicThinking, thinking, { thinking = !thinking }, description = "5Hz LM planning")
                            TerminalCheckRow(Strings.aiMusicFormatInput, useFormat, { useFormat = !useFormat }, description = "LLM prompt/lyrics enhancement")
                            TerminalCheckRow(Strings.aiMusicRandomSeed, randomSeed, { randomSeed = !randomSeed })
                            TerminalCheckRow("cot caption", useCotCaption, { useCotCaption = !useCotCaption })
                            TerminalCheckRow("cot language", useCotLanguage, { useCotLanguage = !useCotLanguage })
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TerminalSmallField("steps", inferenceSteps, { inferenceSteps = it.filter(Char::isDigit).take(3) }, "8", Modifier.weight(1f))
                                TerminalSmallField("guidance", guidanceScale, { guidanceScale = it.filterFloat() }, "7", Modifier.weight(1f))
                                TerminalSmallField("shift", shift, { shift = it.filterFloat() }, "3", Modifier.weight(1f))
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TerminalSmallField("seed", seed, { seed = it.filter { ch -> ch.isDigit() || ch == '-' }.take(10) }, "auto", Modifier.weight(1f))
                                TerminalOptionRow("method", inferMethod, listOf("ode", "sde"), Modifier.weight(1f)) { inferMethod = it }
                            }
                            TerminalSmallField(
                                label = "timesteps",
                                value = timesteps,
                                onValueChange = { timesteps = it.filterTimesteps() },
                                placeholder = "0.97,0.76,0.615...",
                            )
                            TerminalCheckRow("adaptive dual guidance", useAdg, { useAdg = !useAdg }, description = "base model only")
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TerminalSmallField("cfg start", cfgStart, { cfgStart = it.filterFloat() }, "0.0", Modifier.weight(1f))
                                TerminalSmallField("cfg end", cfgEnd, { cfgEnd = it.filterFloat() }, "1.0", Modifier.weight(1f))
                            }
                            TerminalHairline()
                            TerminalSectionLabel("> lm")
                            TerminalCheckRow("constrained decoding", constrainedDecoding, { constrainedDecoding = !constrainedDecoding })
                            TerminalCheckRow("allow lm batch", allowLmBatch, { allowLmBatch = !allowLmBatch })
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TerminalSmallField("lm temp", lmTemperature, { lmTemperature = it.filterFloat() }, "0.85", Modifier.weight(1f))
                                TerminalSmallField("lm cfg", lmCfgScale, { lmCfgScale = it.filterFloat() }, "2.5", Modifier.weight(1f))
                                TerminalSmallField("top p", lmTopP, { lmTopP = it.filterFloat() }, "0.9", Modifier.weight(1f))
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TerminalSmallField("top k", lmTopK, { lmTopK = it.filter(Char::isDigit).take(3) }, "off", Modifier.weight(1f))
                                TerminalSmallField("repeat", lmRepetitionPenalty, { lmRepetitionPenalty = it.filterFloat() }, "1.0", Modifier.weight(1f))
                            }
                            TerminalSmallField(
                                label = "lm negative",
                                value = lmNegativePrompt,
                                onValueChange = { lmNegativePrompt = it.take(160) },
                                placeholder = "NO USER INPUT",
                            )
                        }
                    }
                }
            }

            item {
                TerminalCard(elevated = true) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                when {
                                    genError != null -> "! $genError"
                                    generating && status.isNotBlank() -> "${Strings.aiMusicStatus}: $status"
                                    else -> "ready"
                                },
                                color = when {
                                    genError != null -> colors.warning
                                    generating -> colors.accent
                                    else -> colors.textSecondary
                                },
                                fontFamily = JetBrainsMono,
                                fontSize = 12.sp,
                            )
                        }
                        if (generating) {
                            TerminalPillButton(Strings.aiMusicCancel, onClick = ::cancel, destructive = true)
                        } else {
                            TerminalPillButton(
                                label = Strings.aiMusicGenerate,
                                onClick = ::generate,
                                enabled = selected != null && (prompt.isNotBlank() || lyrics.isNotBlank()),
                            )
                        }
                    }
                }
            }

            if (results.isEmpty() && !generating) {
                item {
                    Box(Modifier.fillMaxWidth().padding(26.dp), contentAlignment = Alignment.Center) {
                        Text(Strings.aiMusicEmpty, color = colors.textMuted, fontFamily = JetBrainsMono, fontSize = 13.sp)
                    }
                }
            } else {
                if (generating) {
                    item {
                        Text("... ${Strings.aiMusicGenerating}", color = colors.accent, fontFamily = JetBrainsMono, fontSize = 13.sp)
                    }
                }
                items(results, key = { it.historyId }) { item ->
                    MusicResultCard(
                        item = item,
                        context = context,
                        playback = playback,
                        onDelete = {
                            playback.stopIf(item.cacheFile)
                            AiAssetHistoryStore.remove(context, AiAssetHistoryStore.MODE_MUSIC, item.historyId)
                            runCatching { item.cacheFile.delete() }
                            results.remove(item)
                        },
                        onSaved = { uri ->
                            AiAssetHistoryStore.list(context, AiAssetHistoryStore.MODE_MUSIC)
                                .firstOrNull { it.id == item.historyId }
                                ?.let { AiAssetHistoryStore.update(context, it.copy(savedToGalleryUri = uri)) }
                        },
                    )
                }
            }
        }
        if (showHistory) {
            MusicHistoryDialog(
                items = results.toList(),
                onOpen = {
                    playback.play(it)
                    showHistory = false
                },
                onDelete = { id ->
                    AiAssetHistoryStore.remove(context, AiAssetHistoryStore.MODE_MUSIC, id)
                    results.firstOrNull { it.historyId == id }?.let { item ->
                        playback.stopIf(item.cacheFile)
                        runCatching { item.cacheFile.delete() }
                        results.remove(item)
                    }
                },
                onClearAll = {
                    playback.stop()
                    results.forEach { runCatching { it.cacheFile.delete() } }
                    AiAssetHistoryStore.clear(context, AiAssetHistoryStore.MODE_MUSIC)
                    results.clear()
                    showHistory = false
                },
                onDismiss = { showHistory = false },
            )
        }
    }
}

@Composable
private fun ModelPicker(
    models: List<AiModel>,
    selected: AiModel?,
    loading: Boolean,
    onSelect: (AiModel) -> Unit,
    onRefresh: () -> Unit,
) {
    val colors = AgentTerminal.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("model", color = colors.textSecondary, fontFamily = JetBrainsMono, fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            TerminalPillButton(if (loading) "refreshing" else "refresh", onClick = onRefresh, enabled = !loading, accent = false)
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (models.isEmpty()) {
                Text(Strings.aiMusicNoModels, color = colors.textMuted, fontFamily = JetBrainsMono, fontSize = 12.sp)
            } else {
                models.forEach { model ->
                    TerminalPillButton(
                        label = model.displayName,
                        onClick = { onSelect(model) },
                        accent = model.id == selected?.id,
                    )
                }
            }
        }
    }
}

@Composable
private fun TerminalField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    minHeight: androidx.compose.ui.unit.Dp,
) {
    val colors = AgentTerminal.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label.lowercase(), color = colors.textSecondary, fontFamily = JetBrainsMono, fontSize = 12.sp)
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight)
                .border(1.dp, colors.border, RoundedCornerShape(4.dp))
                .background(colors.surface)
                .padding(10.dp),
        ) {
            if (value.isBlank()) {
                Text(placeholder, color = colors.textMuted, fontFamily = JetBrainsMono, fontSize = 13.sp, lineHeight = 1.3.em)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(color = colors.textPrimary, fontFamily = JetBrainsMono, fontSize = 13.sp, lineHeight = 1.35.em),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Default),
            )
        }
    }
}

@Composable
private fun TerminalSmallField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val colors = AgentTerminal.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label.lowercase(), color = colors.textSecondary, fontFamily = JetBrainsMono, fontSize = 11.sp)
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 38.dp)
                .border(1.dp, colors.border, RoundedCornerShape(4.dp))
                .background(colors.surface)
                .padding(horizontal = 9.dp, vertical = 8.dp),
        ) {
            if (value.isBlank()) {
                Text(placeholder, color = colors.textMuted, fontFamily = JetBrainsMono, fontSize = 12.sp)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(color = colors.textPrimary, fontFamily = JetBrainsMono, fontSize = 12.sp),
                cursorBrush = SolidColor(colors.accent),
            )
        }
    }
}

@Composable
private fun TerminalOptionRow(
    label: String,
    value: String,
    options: List<String>,
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit,
) {
    val colors = AgentTerminal.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label.lowercase(), color = colors.textSecondary, fontFamily = JetBrainsMono, fontSize = 12.sp)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            options.forEach { option ->
                TerminalPillButton(option, onClick = { onSelect(option) }, accent = option == value)
            }
        }
    }
}

private data class MusicResultItem(
    val cacheFile: File,
    val model: AiModel,
    val prompt: String,
    val meta: String,
    val historyId: Long,
    var savedTo: String? = null,
)

private class MusicPlaybackController {
    private var player: MediaPlayer? = null

    var playingPath by mutableStateOf<String?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var positionMs by mutableStateOf(0)
        private set
    var durationMs by mutableStateOf(0)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun toggle(file: File) {
        if (playingPath == file.absolutePath) {
            if (isPlaying) pause() else resume()
        } else {
            play(file)
        }
    }

    fun play(file: File) {
        releaseCurrent()
        error = null
        runCatching {
            val mp = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnCompletionListener {
                    isPlaying = false
                    positionMs = durationMs
                }
                prepare()
                start()
            }
            player = mp
            playingPath = file.absolutePath
            durationMs = mp.duration.coerceAtLeast(0)
            positionMs = 0
            isPlaying = true
        }.onFailure {
            error = it.message ?: it.javaClass.simpleName
            releaseCurrent()
        }
    }

    fun stop() {
        releaseCurrent()
    }

    fun stopIf(file: File) {
        if (playingPath == file.absolutePath) stop()
    }

    fun refreshPosition() {
        val mp = player ?: return
        runCatching {
            positionMs = mp.currentPosition.coerceAtLeast(0)
            durationMs = mp.duration.coerceAtLeast(durationMs)
            isPlaying = mp.isPlaying
        }.onFailure {
            error = it.message ?: it.javaClass.simpleName
            releaseCurrent()
        }
    }

    fun release() {
        releaseCurrent()
    }

    private fun pause() {
        runCatching { player?.pause() }
        isPlaying = false
        refreshPosition()
    }

    private fun resume() {
        val mp = player ?: return
        runCatching {
            mp.start()
            isPlaying = true
        }.onFailure {
            error = it.message ?: it.javaClass.simpleName
            releaseCurrent()
        }
    }

    private fun releaseCurrent() {
        runCatching { player?.release() }
        player = null
        playingPath = null
        isPlaying = false
        positionMs = 0
        durationMs = 0
    }
}

@Composable
private fun MusicResultCard(
    item: MusicResultItem,
    context: Context,
    playback: MusicPlaybackController,
    onDelete: () -> Unit,
    onSaved: (String) -> Unit,
) {
    val colors = AgentTerminal.colors
    val scope = rememberCoroutineScope()
    var saved by remember(item.historyId) { mutableStateOf(item.savedTo != null) }
    var saveError by remember(item.historyId) { mutableStateOf<String?>(null) }
    val isActive = playback.playingPath == item.cacheFile.absolutePath
    TerminalCard(elevated = true) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("♪", color = colors.accent, fontFamily = JetBrainsMono, fontSize = 20.sp, modifier = Modifier.width(28.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.cacheFile.name, color = colors.textPrimary, fontFamily = JetBrainsMono, fontSize = 13.sp, maxLines = 1)
                    Text(
                        listOf(item.model.displayName, item.meta).filter { it.isNotBlank() }.joinToString(" · "),
                        color = colors.textMuted,
                        fontFamily = JetBrainsMono,
                        fontSize = 11.sp,
                        maxLines = 1,
                    )
                }
                TerminalChip(item.cacheFile.extension.ifBlank { "audio" }.uppercase(), color = colors.textSecondary)
            }
            if (item.prompt.isNotBlank()) {
                Text(item.prompt, color = colors.textSecondary, fontFamily = JetBrainsMono, fontSize = 12.sp, maxLines = 2)
            }
            if (saveError != null) {
                Text(saveError!!, color = colors.warning, fontFamily = JetBrainsMono, fontSize = 11.sp)
            }
            if (isActive) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "player ${formatTime(playback.positionMs)} / ${formatTime(playback.durationMs)}",
                        color = colors.accent,
                        fontFamily = JetBrainsMono,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f),
                    )
                    TerminalPillButton("stop", onClick = { playback.stop() }, accent = false)
                }
            } else {
                Text(
                    "local file · ${formatBytes(item.cacheFile.length())}",
                    color = colors.textMuted,
                    fontFamily = JetBrainsMono,
                    fontSize = 11.sp,
                )
            }
            if (playback.error != null && isActive) {
                Text(playback.error!!, color = colors.warning, fontFamily = JetBrainsMono, fontSize = 11.sp)
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TerminalPillButton(
                    label = when {
                        isActive && playback.isPlaying -> Strings.aiMusicPause
                        isActive -> Strings.aiMusicResume
                        else -> Strings.aiMusicPlay
                    },
                    onClick = { playback.toggle(item.cacheFile) },
                    accent = true,
                )
                TerminalPillButton(
                    label = if (saved) Strings.aiMusicSaved else Strings.aiMusicDownload,
                    onClick = {
                        if (saved) return@TerminalPillButton
                        scope.launch {
                            runCatching {
                                AiGallerySaver.saveAudio(
                                    context = context,
                                    cacheFile = item.cacheFile,
                                    displayName = "AI_${System.currentTimeMillis()}_${item.cacheFile.name}",
                                    mimeType = mimeFor(item.cacheFile.extension),
                                )
                            }.onSuccess {
                                item.savedTo = it
                                saved = true
                                saveError = null
                                onSaved(it)
                            }.onFailure {
                                saveError = it.message ?: it.javaClass.simpleName
                            }
                        }
                    },
                    accent = !saved,
                )
                TerminalPillButton(Strings.aiMusicShareFile, onClick = { shareAudio(context, item.cacheFile) }, accent = false)
                TerminalPillButton(Strings.aiHistoryDelete, onClick = onDelete, destructive = true)
            }
        }
    }
}

@Composable
private fun MusicHistoryDialog(
    items: List<MusicResultItem>,
    onOpen: (File) -> Unit,
    onDelete: (Long) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = AgentTerminal.colors
    val sdf = remember { java.text.SimpleDateFormat("dd.MM.yy HH:mm", java.util.Locale.getDefault()) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(colors.surface)
                .border(1.dp, colors.border, RoundedCornerShape(8.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(Strings.aiMusicHistoryTitle, color = colors.textPrimary, fontFamily = JetBrainsMono, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text("${items.size} ${Strings.aiHistoryCount}", color = colors.textMuted, fontFamily = JetBrainsMono, fontSize = 11.sp)
                }
                if (items.isNotEmpty()) TerminalPillButton("clear", onClick = onClearAll, destructive = true)
                Spacer(Modifier.width(6.dp))
                TerminalPillButton("x", onClick = onDismiss, accent = false)
            }
            TerminalHairline()
            if (items.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    Text(Strings.aiHistoryEmpty, color = colors.textMuted, fontFamily = JetBrainsMono, fontSize = 13.sp)
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxWidth().heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(items, key = { it.historyId }) { item ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { onOpen(item.cacheFile) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("♪", color = colors.accent, fontFamily = JetBrainsMono, fontSize = 16.sp, modifier = Modifier.width(24.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.prompt.ifBlank { item.cacheFile.name }, color = colors.textPrimary, fontFamily = JetBrainsMono, fontSize = 12.sp, maxLines = 1)
                                Text(sdf.format(java.util.Date(item.historyId)), color = colors.textMuted, fontFamily = JetBrainsMono, fontSize = 10.sp)
                            }
                            TerminalPillButton("del", onClick = { onDelete(item.historyId) }, destructive = true)
                        }
                    }
                }
            }
        }
    }
}

private fun AiMusicResult.metaSummary(format: String): String =
    buildList {
        durationSec?.let { add("${it.toInt()}s") }
        bpm?.let { add("${it}bpm") }
        keyScale.takeIf { it.isNotBlank() }?.let { add(it) }
        timeSignature.takeIf { it.isNotBlank() }?.let { add(timeSignatureLabel(it)) }
        add(format)
    }.joinToString(" · ")

private fun timeSignatureLabel(value: String): String = when (value.trim()) {
    "2" -> "2/4"
    "3" -> "3/4"
    "4" -> "4/4"
    "6" -> "6/8"
    else -> value
}

private fun String.filterFloat(): String =
    filter { it.isDigit() || it == '.' }.take(6)

private fun String.filterTimesteps(): String =
    filter { it.isDigit() || it == '.' || it == ',' }.take(120)

private fun formatTime(ms: Int): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%d:%02d".format(min, sec)
}

private fun formatBytes(bytes: Long): String {
    val kb = bytes / 1024.0
    return if (kb < 1024) {
        "${kb.toInt().coerceAtLeast(1)} KB"
    } else {
        "%.1f MB".format(kb / 1024.0)
    }
}

private fun mimeFor(ext: String): String = when (ext.lowercase()) {
    "wav", "wav32" -> "audio/wav"
    "flac" -> "audio/flac"
    "aac" -> "audio/aac"
    "opus" -> "audio/opus"
    else -> "audio/mpeg"
}

private fun shareAudio(context: Context, file: File) {
    runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND)
                    .setType(mimeFor(file.extension))
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                file.name,
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
