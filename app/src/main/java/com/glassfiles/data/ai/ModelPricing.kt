package com.glassfiles.data.ai

import com.glassfiles.data.ai.models.AiModel
import com.glassfiles.data.ai.models.AiProviderId
import com.glassfiles.data.ai.providers.AiTokenUsage
import com.glassfiles.data.ai.usage.TokenizerRegistry

/** Best-effort USD pricing for chat completions, per 1M tokens. */
object ModelPricing {
    data class Rate(
        val inputUsdPerM: Double,
        val outputUsdPerM: Double,
        val cacheCreationUsdPerM: Double? = null,
        val cacheReadUsdPerM: Double? = null,
    )

    private val openAi = mapOf(
        "gpt-5" to Rate(2.50, 10.0),
        "gpt-4.1-mini" to Rate(0.40, 1.60),
        "gpt-4.1" to Rate(2.00, 8.00),
        "gpt-4o-mini" to Rate(0.15, 0.60),
        "gpt-4o" to Rate(2.50, 10.0),
        "o4-mini" to Rate(1.10, 4.40),
        "o3-mini" to Rate(1.10, 4.40),
        "o1-mini" to Rate(1.10, 4.40),
        "o1" to Rate(15.00, 60.00),
    )

    private val anthropic = mapOf(
        "claude-opus-4" to Rate(15.0, 75.0, cacheCreationUsdPerM = 18.75, cacheReadUsdPerM = 1.50),
        "claude-sonnet-4" to Rate(3.0, 15.0, cacheCreationUsdPerM = 3.75, cacheReadUsdPerM = 0.30),
        "claude-3-7-sonnet" to Rate(3.0, 15.0, cacheCreationUsdPerM = 3.75, cacheReadUsdPerM = 0.30),
        "claude-3-5-sonnet" to Rate(3.0, 15.0, cacheCreationUsdPerM = 3.75, cacheReadUsdPerM = 0.30),
        "claude-3-5-haiku" to Rate(0.80, 4.0, cacheCreationUsdPerM = 1.0, cacheReadUsdPerM = 0.08),
        "claude-3-haiku" to Rate(0.25, 1.25, cacheCreationUsdPerM = 0.30, cacheReadUsdPerM = 0.03),
        "claude-3-opus" to Rate(15.0, 75.0, cacheCreationUsdPerM = 18.75, cacheReadUsdPerM = 1.50),
    )

    private val xai = mapOf(
        "grok-4" to Rate(3.0, 15.0),
        "grok-3" to Rate(3.0, 15.0),
        "grok-2" to Rate(2.0, 10.0),
    )

    private val google = mapOf(
        "gemini-2.5-pro" to Rate(1.25, 10.0),
        "gemini-2.5-flash" to Rate(0.30, 2.50),
        "gemini-2.0-flash" to Rate(0.10, 0.40),
        "gemini-1.5-pro" to Rate(1.25, 5.0),
        "gemini-1.5-flash" to Rate(0.075, 0.30),
    )

    private val moonshot = mapOf(
        "moonshot-v1-128k" to Rate(2.0, 2.0),
        "moonshot-v1-32k" to Rate(1.0, 1.0),
        "moonshot-v1-8k" to Rate(0.50, 0.50),
        "kimi-k2" to Rate(0.60, 2.50),
    )

    private val alibaba = mapOf(
        "qwen-max" to Rate(1.60, 6.40),
        "qwen-plus" to Rate(0.40, 1.20),
        "qwen-turbo" to Rate(0.05, 0.20),
        "qwen3" to Rate(0.30, 1.20),
    )

    fun rateFor(model: AiModel): Rate? = rateFor(model.providerId.name, model.id)

    fun rateFor(providerId: String, modelId: String): Rate? {
        val provider = providerId.lowercase()
        val model = modelId.lowercase()
        val table = when (provider) {
            AiProviderId.OPENAI.name.lowercase(), "openai" -> openAi
            AiProviderId.ANTHROPIC.name.lowercase(), "anthropic" -> anthropic
            AiProviderId.XAI.name.lowercase(), "xai" -> xai
            AiProviderId.GOOGLE.name.lowercase(), "google" -> google
            AiProviderId.MOONSHOT.name.lowercase(), "moonshot" -> moonshot
            AiProviderId.ALIBABA.name.lowercase(), "alibaba" -> alibaba
            AiProviderId.OPENROUTER.name.lowercase(), "openrouter" -> openRouterRate(model)
            else -> return null
        }
        return longestPrefix(table, model)
    }

    private fun openRouterRate(model: String): Map<String, Rate> = when {
        model.startsWith("openai/") -> openAi.mapKeys { "openai/${it.key}" }
        model.startsWith("anthropic/") -> anthropic.mapKeys { "anthropic/${it.key}" }
        model.startsWith("google/") -> google.mapKeys { "google/${it.key}" }
        model.startsWith("x-ai/") || model.startsWith("xai/") -> xai
        else -> openAi + anthropic + google + xai + moonshot + alibaba
    }

    private fun longestPrefix(table: Map<String, Rate>, id: String): Rate? =
        table.entries
            .filter { id.startsWith(it.key) || id.contains(it.key) }
            .maxByOrNull { it.key.length }
            ?.value

    fun estimateTokens(chars: Int): Int = (chars + 3) / 4

    fun estimateTokens(providerId: String, modelId: String, chars: Int): Int =
        TokenizerRegistry.forProvider(providerId, modelId).countTokens("x".repeat(chars.coerceAtLeast(0)))

    fun estimateTextTokens(providerId: String, modelId: String, text: String): Int =
        TokenizerRegistry.forProvider(providerId, modelId).countTokens(text)

    fun estimateCostUsd(rate: Rate, inputChars: Int, outputChars: Int): Double =
        estimateCostUsdFromTokens(rate, estimateTokens(inputChars), estimateTokens(outputChars))

    fun estimateCostUsdFromTokens(rate: Rate, inputTokens: Int, outputTokens: Int): Double =
        (inputTokens * rate.inputUsdPerM + outputTokens * rate.outputUsdPerM) / 1_000_000.0

    fun calculateCostUsd(rate: Rate, usage: AiTokenUsage): Double {
        var cost = estimateCostUsdFromTokens(rate, usage.inputTokens, usage.outputTokens)
        if (usage.cacheCreationTokens > 0 && rate.cacheCreationUsdPerM != null) {
            cost += usage.cacheCreationTokens * rate.cacheCreationUsdPerM / 1_000_000.0
        }
        if (usage.cacheReadTokens > 0 && rate.cacheReadUsdPerM != null) {
            cost += usage.cacheReadTokens * rate.cacheReadUsdPerM / 1_000_000.0
        }
        return cost
    }
}
