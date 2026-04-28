package com.glassfiles.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glassfiles.data.Strings
import com.glassfiles.data.ai.AiKeyStore
import com.glassfiles.data.ai.ModelRegistry
import com.glassfiles.data.ai.models.AiCapability
import com.glassfiles.data.ai.models.AiModel
import com.glassfiles.data.ai.models.AiProviderId
import kotlinx.coroutines.launch

/**
 * Browse the model catalog of every configured provider. One tab per provider;
 * the catalog is populated by [ModelRegistry] which fetches `/models` lazily and
 * caches for 24h. The "Refresh" button forces a re-fetch.
 */
@Composable
fun AiModelsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()

    val configured by remember {
        derivedStateOf { AiKeyStore.configuredProviders(context) }
    }
    // Always show all 7 tabs so the user can see the empty state and a "set
    // your key" hint, but mark the unconfigured ones as such.
    val tabs = remember { AiProviderId.entries.toList() }
    var selected by remember { mutableStateOf(tabs.first()) }
    val models = remember { mutableStateMapOf<AiProviderId, List<AiModel>>() }
    val loading = remember { mutableStateMapOf<AiProviderId, Boolean>() }
    val errors = remember { mutableStateMapOf<AiProviderId, String?>() }

    fun load(provider: AiProviderId, force: Boolean) {
        if (loading[provider] == true) return
        loading[provider] = true
        errors[provider] = null
        scope.launch {
            try {
                val list = ModelRegistry.getModels(
                    context = context,
                    provider = provider,
                    apiKey = AiKeyStore.getKey(context, provider),
                    force = force,
                )
                models[provider] = list
            } catch (e: Exception) {
                errors[provider] = e.message ?: e.javaClass.simpleName
            } finally {
                loading[provider] = false
            }
        }
    }

    LaunchedEffect(selected) {
        if (models[selected].isNullOrEmpty()) load(selected, force = false)
    }

    Column(Modifier.fillMaxSize().background(colors.surface)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 44.dp, start = 4.dp, end = 16.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, Modifier.size(20.dp), tint = colors.onSurface)
            }
            Text(
                Strings.aiModels,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { load(selected, force = true) }) {
                Icon(
                    Icons.Rounded.Refresh,
                    null,
                    Modifier.size(20.dp),
                    tint = colors.primary,
                )
            }
        }

        // Provider tabs
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { provider ->
                val isSelected = provider == selected
                val isConfigured = provider in configured
                Box(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isSelected) colors.primary.copy(alpha = 0.16f)
                            else Color.Transparent,
                        )
                        .clickable { selected = provider }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(if (isConfigured) colors.tertiary else colors.outline),
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(
                            provider.displayName,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (isSelected) colors.primary else colors.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        ModelsBody(
            provider = selected,
            list = models[selected].orEmpty(),
            loading = loading[selected] == true,
            error = errors[selected],
            isConfigured = selected in configured,
        )
    }
}

@Composable
private fun ModelsBody(
    provider: AiProviderId,
    list: List<AiModel>,
    loading: Boolean,
    error: String?,
    isConfigured: Boolean,
) {
    val colors = MaterialTheme.colorScheme

    when {
        loading && list.isEmpty() -> Box(
            Modifier.fillMaxSize().padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = colors.primary, strokeWidth = 2.dp)
        }
        error != null -> Box(
            Modifier.fillMaxSize().padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    error,
                    fontSize = 13.sp,
                    color = colors.error,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    if (!isConfigured) "${Strings.aiNoKey} — ${provider.displayName}" else "",
                    fontSize = 12.sp,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        list.isEmpty() -> Box(
            Modifier.fillMaxSize().padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                Strings.aiNoModels,
                fontSize = 13.sp,
                color = colors.onSurfaceVariant,
            )
        }
        else -> LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(list) { model -> ModelRow(model) }
        }
    }
}

@Composable
private fun ModelRow(model: AiModel) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceVariant)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                model.displayName,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (model.contextWindow != null) {
                Text(
                    "${(model.contextWindow!! / 1000)}K",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
        Text(
            model.id,
            fontSize = 11.sp,
            color = colors.onSurfaceVariant,
            fontFamily = FontFamily.Monospace,
        )
        if (model.capabilities.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                model.capabilities.sortedBy { it.ordinal }.forEach { CapabilityPill(it) }
            }
        }
    }
}

@Composable
private fun CapabilityPill(cap: AiCapability) {
    val colors = MaterialTheme.colorScheme
    val (label, tint) = when (cap) {
        AiCapability.TEXT -> "TEXT" to colors.onSurfaceVariant
        AiCapability.VISION -> "VIS" to colors.primary
        AiCapability.IMAGE_GEN -> "IMG" to colors.tertiary
        AiCapability.VIDEO_GEN -> "VID" to colors.tertiary
        AiCapability.CODING -> "CODE" to colors.primary
        AiCapability.REASONING -> "REASON" to colors.primary
        AiCapability.EMBEDDING -> "EMB" to colors.onSurfaceVariant
        AiCapability.AUDIO -> "AUD" to colors.tertiary
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(tint.copy(alpha = 0.14f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.4.sp,
            color = tint,
            fontFamily = FontFamily.Monospace,
        )
    }
}
