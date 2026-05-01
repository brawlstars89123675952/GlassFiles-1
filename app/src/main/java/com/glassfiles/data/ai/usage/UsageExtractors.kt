package com.glassfiles.data.ai.usage

import com.glassfiles.data.ai.providers.AiTokenUsage
import org.json.JSONObject

data class UsageReport(
    val inputTokens: Int?,
    val outputTokens: Int?,
    val cacheCreationTokens: Int? = null,
    val cacheReadTokens: Int? = null,
    val isComplete: Boolean = true,
) {
    fun toTokenUsage(): AiTokenUsage? {
        val input = inputTokens ?: 0
        val output = outputTokens ?: 0
        val cacheCreate = cacheCreationTokens ?: 0
        val cacheRead = cacheReadTokens ?: 0
        if (input <= 0 && output <= 0 && cacheCreate <= 0 && cacheRead <= 0) return null
        return AiTokenUsage(
            inputTokens = input,
            outputTokens = output,
            cacheCreationTokens = cacheCreate,
            cacheReadTokens = cacheRead,
            isComplete = isComplete,
        )
    }
}

interface UsageExtractor {
    fun extract(response: JSONObject?): UsageReport?
}

object OpenAiUsageExtractor : UsageExtractor {
    override fun extract(response: JSONObject?): UsageReport? {
        val usage = response?.optJSONObject("usage") ?: response ?: return null
        val input = usage.optNullableInt("prompt_tokens") ?: usage.optNullableInt("input_tokens")
        val output = usage.optNullableInt("completion_tokens") ?: usage.optNullableInt("output_tokens")
        return UsageReport(inputTokens = input, outputTokens = output).takeIf { it.toTokenUsage() != null }
    }
}

object AnthropicUsageExtractor : UsageExtractor {
    override fun extract(response: JSONObject?): UsageReport? {
        val usage = response?.optJSONObject("usage") ?: response ?: return null
        return UsageReport(
            inputTokens = usage.optNullableInt("input_tokens"),
            outputTokens = usage.optNullableInt("output_tokens"),
            cacheCreationTokens = usage.optNullableInt("cache_creation_input_tokens"),
            cacheReadTokens = usage.optNullableInt("cache_read_input_tokens"),
        ).takeIf { it.toTokenUsage() != null }
    }
}

object GeminiUsageExtractor : UsageExtractor {
    override fun extract(response: JSONObject?): UsageReport? {
        val usage = response?.optJSONObject("usageMetadata") ?: response ?: return null
        return UsageReport(
            inputTokens = usage.optNullableInt("promptTokenCount"),
            outputTokens = usage.optNullableInt("candidatesTokenCount"),
        ).takeIf { it.toTokenUsage() != null }
    }
}

object NullUsageExtractor : UsageExtractor {
    override fun extract(response: JSONObject?): UsageReport? = null
}

private fun JSONObject.optNullableInt(name: String): Int? =
    if (has(name) && !isNull(name)) optInt(name).takeIf { it >= 0 } else null
