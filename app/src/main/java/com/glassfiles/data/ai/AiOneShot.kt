package com.glassfiles.data.ai

import android.content.Context
import com.glassfiles.data.ai.models.AiCapability
import com.glassfiles.data.ai.models.AiMessage
import com.glassfiles.data.ai.models.AiModel
import com.glassfiles.data.ai.providers.AiProviders
import com.glassfiles.data.ai.usage.AiUsageMode
import com.glassfiles.data.ai.usage.AiUsageRecord
import com.glassfiles.data.ai.usage.AiUsageStore

/**
 * Convenience wrapper around a single non-streaming, non-tool-use chat
 * call. Used by the GitHub-module's "AI summary" / "Generate commit
 * message" / etc. one-shot buttons that do not need the full agent
 * loop and just want a string of text from whatever model the user
 * has configured.
 *
 * The picker prefers the same flagship-tier models the agent does,
 * but it does not require tool support — a one-shot summary works
 * fine on a cheap chat-only model. Returns `null` when the user has
 * no configured providers, otherwise returns the generated text or
 * throws on HTTP error.
 */
object AiOneShot {

    /**
     * Pick the "best" model the user has a key for, biased toward
     * flagship tiers. Returns null when no provider is configured.
     */
    suspend fun pickModel(context: Context): AiModel? {
        val all = ModelRegistry.getAllModels(context).values.flatten()
        if (all.isEmpty()) return null
        val rank: (AiModel) -> Int = { m ->
            val id = m.id.lowercase()
            when {
                id.contains("gpt-4.1") || id.contains("gpt-4o") -> 0
                id.contains("claude-3.5") || id.contains("claude-sonnet") || id.contains("claude-opus") -> 1
                id.contains("gemini-2.5") || id.contains("gemini-1.5-pro") -> 2
                id.contains("gpt-4") -> 3
                id.contains("grok") -> 4
                id.contains("claude") -> 5
                else -> 9
            }
        }
        // Filter out vision-only / image-gen models — we want a text
        // chat model that actually handles a long prompt.
        return all
            .filter { AiCapability.TEXT in it.capabilities }
            .filter { AiCapability.IMAGE_GEN !in it.capabilities }
            .filter { AiCapability.VIDEO_GEN !in it.capabilities }
            .filter { !it.deprecated }
            .minByOrNull(rank)
    }

    /**
     * One-shot chat call. [systemPrompt] is sent as a `system` role
     * (where the provider supports it; falls back to a prefix on the
     * user message otherwise). [userPrompt] is the actual instruction
     * + payload. The returned string is the model's full text response
     * with no streaming.
     *
     * Throws [IllegalStateException] when no provider is configured,
     * and rethrows the underlying provider exception for HTTP errors.
     */
    suspend fun complete(
        context: Context,
        systemPrompt: String,
        userPrompt: String,
        modelOverride: AiModel? = null,
    ): String {
        val model = modelOverride ?: pickModel(context)
            ?: throw IllegalStateException("No AI provider configured")
        val key = AiKeyStore.getKey(context, model.providerId)
        if (key.isBlank()) throw IllegalStateException("Missing API key for ${model.providerId}")
        val provider = AiProviders.get(model.providerId)
        val messages = buildList {
            if (systemPrompt.isNotBlank()) add(AiMessage(role = "system", content = systemPrompt))
            add(AiMessage(role = "user", content = userPrompt))
        }
        val out = StringBuilder()
        val inputChars = messages.sumOf { it.content.length }
        provider.chat(
            context = context,
            modelId = model.id,
            messages = messages,
            apiKey = key,
            onChunk = { out.append(it) },
        )
        val text = out.toString().trim()
        // Best-effort local usage record. Provider chat() doesn't
        // surface real token usage from the streaming API yet, so
        // we mark this as estimated. Flip [estimated] to false once
        // a provider returns its own input/output token counts.
        runCatching {
            AiUsageStore.append(
                context,
                AiUsageRecord(
                    providerId = model.providerId.name,
                    modelId = model.id,
                    mode = AiUsageMode.CODING,
                    estimatedInputChars = inputChars,
                    estimatedOutputChars = text.length,
                    estimated = true,
                ),
            )
        }
        return text
    }
}
