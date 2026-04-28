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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Share
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.glassfiles.data.Strings
import com.glassfiles.data.ai.AiGallerySaver
import com.glassfiles.data.ai.AiKeyStore
import com.glassfiles.data.ai.ModelRegistry
import com.glassfiles.data.ai.models.AiCapability
import com.glassfiles.data.ai.models.AiModel
import com.glassfiles.data.ai.models.AiProviderId
import com.glassfiles.data.ai.providers.AiProviders
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
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
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

    LaunchedEffect(configured) {
        modelsLoading = true
        loadError = null
        imageModels.clear()
        try {
            for (p in configured) {
                val list = ModelRegistry.getModels(
                    context = context,
                    provider = p,
                    apiKey = AiKeyStore.getKey(context, p),
                    force = false,
                ).filter { AiCapability.IMAGE_GEN in it.capabilities }
                imageModels.addAll(list)
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
                paths.forEach { results.add(0, ImageResult(File(it), model)) }
            } catch (e: Exception) {
                genError = e.message ?: e.javaClass.simpleName
            } finally {
                generating = false
            }
        }
    }

    Column(Modifier.fillMaxSize().background(colors.surface)) {
        // ── Top bar
        Row(
            Modifier.fillMaxWidth().padding(top = 44.dp, start = 4.dp, end = 16.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, Modifier.size(20.dp), tint = colors.onSurface)
            }
            Text(
                Strings.aiImageGen,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
            )
        }

        if (configured.isEmpty() || (!modelsLoading && imageModels.isEmpty() && loadError == null)) {
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    Strings.aiImageNoModels,
                    fontSize = 13.sp,
                    color = colors.onSurfaceVariant,
                )
            }
            return@Column
        }

        // ── Controls (model / size / count) ─────────────────────────────
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DropdownChip(
                label = "MODEL",
                value = selected?.displayName ?: Strings.aiRefreshing,
                modifier = Modifier.weight(2f),
                options = imageModels,
                optionLabel = { it.displayName },
                onSelect = { selected = it },
            )
            DropdownChip(
                label = "SIZE",
                value = size,
                modifier = Modifier.weight(1f),
                options = sizeOptionsFor(selected),
                optionLabel = { it },
                onSelect = { size = it },
            )
            DropdownChip(
                label = "N",
                value = count.toString(),
                modifier = Modifier.weight(0.6f),
                options = listOf(1, 2, 3, 4),
                optionLabel = { it.toString() },
                onSelect = { count = it },
            )
        }

        // ── Prompt input ─────────────────────────────────────────────────
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
                Strings.aiImagePrompt.uppercase(),
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
                            Strings.aiImagePromptHint,
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

        // ── Results
        if (results.isEmpty() && !generating) {
            Box(
                Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    Strings.aiImageEmpty,
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
                                Strings.aiImageGenerating,
                                fontSize = 13.sp,
                                color = colors.onSurfaceVariant,
                            )
                        }
                    }
                }
                androidx.compose.foundation.lazy.items(results) { item ->
                    ImageResultCard(item, context)
                }
            }
        }
    }
}

@Composable
private fun GenerateButton(enabled: Boolean, generating: Boolean, onClick: () -> Unit) {
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
        if (generating) {
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                color = fg,
                strokeWidth = 1.dp,
            )
            Spacer(Modifier.size(8.dp))
        }
        Text(
            (if (generating) Strings.aiImageGenerating else Strings.aiImageGenerate),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = fg,
        )
    }
}

@Composable
private fun <T> DropdownChip(
    label: String,
    value: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
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
                    label,
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
            Icon(Icons.Rounded.ExpandMore, null, Modifier.size(16.dp), tint = colors.onSurfaceVariant)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(optionLabel(opt), fontSize = 13.sp, color = colors.onSurface) },
                    onClick = {
                        onSelect(opt); expanded = false
                    },
                )
            }
        }
    }
}

private data class ImageResult(
    val cacheFile: File,
    val model: AiModel,
    var savedTo: String? = null,
)

@Composable
private fun ImageResultCard(item: ImageResult, context: Context) {
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var saved by remember { mutableStateOf(item.savedTo != null) }
    var savingError by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceVariant.copy(alpha = 0.5f))
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
                .background(colors.surface),
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Image, null, Modifier.size(14.dp), tint = colors.onSurfaceVariant)
            Spacer(Modifier.size(6.dp))
            Text(
                item.model.displayName,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = colors.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (savingError != null) {
                Text(
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
                            AiGallerySaver.saveImage(context, item.cacheFile, name)
                            saved = true
                            savingError = null
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
    val colors = MaterialTheme.colorScheme
    val bg = if (primary) colors.primary else colors.surface
    val fg = if (primary) colors.onPrimary else colors.onSurface
    Row(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, Modifier.size(14.dp), tint = fg)
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = fg)
    }
}

private fun sizeOptionsFor(model: AiModel?): List<String> {
    val id = model?.id?.lowercase().orEmpty()
    return when {
        id.contains("dall-e-2") || id.contains("dalle-2") -> listOf("256x256", "512x512", "1024x1024")
        id.contains("dall-e-3") || id.contains("dalle-3") -> listOf("1024x1024", "1792x1024", "1024x1792")
        id.contains("imagen") -> listOf("1024x1024", "1408x768", "768x1408")
        id.contains("grok") || id.contains("imagine") -> listOf("1024x1024", "1024x768", "768x1024")
        else -> listOf("512x512", "1024x1024", "1024x1792", "1792x1024")
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
