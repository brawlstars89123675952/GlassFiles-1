package com.glassfiles.data.ai.providers

import com.glassfiles.data.ai.models.AiProviderId

/** Registry of all known providers. Lookup by [AiProviderId] is the entry point. */
object AiProviders {
    private val all: Map<AiProviderId, AiProvider> = listOf(
        OpenAiProvider,
        AnthropicProvider,
        GoogleProvider,
        XaiProvider,
        MoonshotProvider,
        AlibabaProvider,
        OpenRouterProvider,
        AceMusicProvider,
    ).associateBy { it.id }

    fun get(id: AiProviderId): AiProvider = all[id]
        ?: error("No provider registered for $id")

    fun all(): List<AiProvider> = all.values.toList()
}
