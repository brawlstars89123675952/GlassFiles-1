package com.glassfiles.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.glassfiles.data.Strings
import com.glassfiles.data.ai.AiKeyStore
import com.glassfiles.data.ai.ModelRegistry
import com.glassfiles.data.ai.models.AiCapability
import com.glassfiles.data.ai.models.AiModel
import com.glassfiles.data.ai.models.AiProviderId
import com.glassfiles.ui.components.AiModuleChip
import com.glassfiles.ui.components.AiModuleHairline
import com.glassfiles.ui.components.AiModulePageBar
import com.glassfiles.ui.components.AiModuleIcon
import com.glassfiles.ui.components.AiModuleIconButton
import com.glassfiles.ui.components.AiModuleText
import com.glassfiles.ui.theme.AiModuleSurface
import com.glassfiles.ui.theme.AiModuleTheme
import com.glassfiles.ui.theme.JetBrainsMono
import kotlinx.coroutines.launch

/**
 * Browse the model catalog of every configured provider. One tab per provider;
 * the catalog is populated by [ModelRegistry] which fetches `/models` lazily and
 * caches for 24h. The "Refresh" button forces a re-fetch.
 */
@Composable
fun AiModelsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val configured by remember {
        derivedStateOf { AiKeyStore.configuredProviders(context) }
    }
    // Always show all provider tabs so the user can see the empty state and a "set
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
                val key = AiKeyStore.getKey(context, provider)
                val list = if (force) {
                    // The user explicitly asked to refresh — propagate auth /
                    // network errors so they see the real failure instead of an
                    // empty list. ModelRegistry.getModels() silently falls back
                    // to stale cache on failure, which makes the error UI here
                    // unreachable, so we use refreshOrThrow() for force-refresh.
                    ModelRegistry.refreshOrThrow(context, provider, key)
                } else {
                    ModelRegistry.getModels(context, provider, key, force = false)
                }
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

    AiModuleSurface {
        Column(Modifier.fillMaxSize()) {
            AiModulePageBar(
                title = Strings.aiModels,
                onBack = onBack,
                subtitle = "$selected · catalog",
                trailing = {
                    val colors = AiModuleTheme.colors
                    AiModuleIconButton(
                        onClick = { load(selected, force = true) },
                        modifier = Modifier.size(36.dp),
                    ) {
                        AiModuleIcon(
                            Icons.Rounded.Refresh,
                            null,
                            Modifier.size(18.dp),
                            tint = colors.accent,
                        )
                    }
                },
            )

            // Provider tabs — terminal-style breadcrumbs.
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tabs.forEach { provider ->
                    ProviderTab(
                        provider = provider,
                        selected = provider == selected,
                        configured = provider in configured,
                        onClick = { selected = provider },
                    )
                }
            }
            AiModuleHairline()

            ModelsBody(
                provider = selected,
                list = models[selected].orEmpty(),
                loading = loading[selected] == true,
                error = errors[selected],
                isConfigured = selected in configured,
            )
        }
    }
}

@Composable
private fun ProviderTab(
    provider: AiProviderId,
    selected: Boolean,
    configured: Boolean,
    onClick: () -> Unit,
) {
    val colors = AiModuleTheme.colors
    val borderColor = when {
        selected -> colors.accent
        configured -> colors.border
        else -> colors.border
    }
    val labelColor = when {
        selected -> colors.accent
        configured -> colors.textPrimary
        else -> colors.textMuted
    }
    Row(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) colors.surface else androidx.compose.ui.graphics.Color.Transparent)
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(if (configured) colors.accent else colors.background)
                .border(1.dp, if (configured) colors.accent else colors.border, CircleShape),
        )
        Spacer(Modifier.width(8.dp))
        AiModuleText(
            provider.displayName,
            fontSize = 12.sp,
            fontFamily = JetBrainsMono,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = labelColor,
            lineHeight = 1.2.em,
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
    val colors = AiModuleTheme.colors
    when {
        loading && list.isEmpty() -> Box(
            Modifier.fillMaxSize().padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            AiModuleText(
                "loading...",
                color = colors.textSecondary,
                fontFamily = JetBrainsMono,
                fontSize = 13.sp,
            )
        }
        error != null -> Box(
            Modifier.fillMaxSize().padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AiModuleText(
                    "! $error",
                    fontSize = 13.sp,
                    color = colors.error,
                    fontFamily = JetBrainsMono,
                    lineHeight = 1.3.em,
                )
                Spacer(Modifier.size(6.dp))
                if (!isConfigured) {
                    AiModuleText(
                        "${Strings.aiNoKey} — ${provider.displayName}",
                        fontSize = 12.sp,
                        color = colors.textMuted,
                        fontFamily = JetBrainsMono,
                    )
                }
            }
        }
        list.isEmpty() -> Box(
            Modifier.fillMaxSize().padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            AiModuleText(
                Strings.aiNoModels,
                fontSize = 13.sp,
                color = colors.textMuted,
                fontFamily = JetBrainsMono,
            )
        }
        else -> LazyColumn(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            items(list) { model ->
                ModelRow(model)
                AiModuleHairline()
            }
        }
    }
}

@Composable
private fun ModelRow(model: AiModel) {
    val colors = AiModuleTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AiModuleText(
                "▸ ",
                color = colors.accentDim,
                fontFamily = JetBrainsMono,
                fontSize = 13.sp,
            )
            AiModuleText(
                model.displayName,
                fontSize = 14.sp,
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.Medium,
                color = colors.textPrimary,
                lineHeight = 1.3.em,
                modifier = Modifier.weight(1f),
            )
            if (model.contextWindow != null) {
                AiModuleText(
                    "${(model.contextWindow / 1000)}K",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.warning,
                    fontFamily = JetBrainsMono,
                )
            }
        }
        AiModuleText(
            model.id,
            fontSize = 11.sp,
            color = colors.textMuted,
            fontFamily = JetBrainsMono,
            modifier = Modifier.padding(start = 14.dp),
        )
        if (model.capabilities.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(start = 14.dp, top = 2.dp),
            ) {
                model.capabilities.sortedBy { it.ordinal }.forEach { cap ->
                    val (label, color) = capabilityStyle(cap)
                    AiModuleChip(label = label, color = color)
                }
            }
        }
    }
}

@Composable
private fun capabilityStyle(cap: AiCapability): Pair<String, androidx.compose.ui.graphics.Color> {
    val colors = AiModuleTheme.colors
    return when (cap) {
        AiCapability.TEXT -> "TEXT" to colors.textSecondary
        AiCapability.VISION -> "VIS" to colors.accent
        AiCapability.IMAGE_GEN -> "IMG" to colors.warning
        AiCapability.VIDEO_GEN -> "VID" to colors.warning
        AiCapability.CODING -> "CODE" to colors.accent
        AiCapability.REASONING -> "REASON" to colors.accentDim
        AiCapability.EMBEDDING -> "EMB" to colors.textMuted
        AiCapability.AUDIO -> "AUD" to colors.warning
    }
}
