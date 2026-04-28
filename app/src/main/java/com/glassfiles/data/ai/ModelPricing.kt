package com.glassfiles.data.ai

import com.glassfiles.data.ai.models.AiModel
import com.glassfiles.data.ai.models.AiProviderId

/**
 * Best-effort USD pricing for chat completions. Numbers are public list
 * prices in USD per **one million tokens** as of late 2025 — they will
 * drift over time, but the order of magnitude is what matters for the
 * agent's session cost meter, not exact billing.
 *
 * Providers that we don't have a price table for (OpenRouter forwards
 * many models, Alibaba's Qwen is region-priced, etc.) fall through to
 * [Fallback]. The cost meter degrades gracefully — when the rate is
 * `null` it just shows token count without the dollar number.
 *
 * Pricing is matched against [AiModel.id] using a longest-prefix match
 * so versioned snapshots (`gpt-4o-2024-08-06`) hit the same rate as the
 * unversioned id (`gpt-4o`).
 */
object ModelPricing {
    /** USD per 1 000 000 input / output tokens. */
    data class Rate(val inputUsdPerM: Double, val outputUsdPerM: Double)

    private val Fallback = Rate(0.0, 0.0)

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
        "claude-opus-4" to Rate(15.0, 75.0),
        "claude-sonnet-4" to Rate(3.0, 15.0),
        "claude-3-7-sonnet" to Rate(3.0, 15.0),
        "claude-3-5-sonnet" to Rate(3.0, 15.0),
        "claude-3-5-haiku" to Rate(0.80, 4.0),
        "claude-3-haiku" to Rate(0.25, 1.25),
        "claude-3-opus" to Rate(15.0, 75.0),
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

    /**
     * @return rate in USD per million tokens, or null if we have no data
     *   for that model. Caller should treat null as "unknown — show
     *   token count without dollars".
     */
    fun rateFor(model: AiModel): Rate? {
        val table = when (model.providerId) {
            AiProviderId.OPENAI -> openAi
            AiProviderId.ANTHROPIC -> anthropic
            AiProviderId.XAI -> xai
            AiProviderId.GOOGLE -> google
            AiProviderId.MOONSHOT -> moonshot
            else -> return null
        }
        return longestPrefix(table, model.id.lowercase())
    }

    private fun longestPrefix(table: Map<String, Rate>, id: String): Rate? {
        return table.entries
            .filter { id.startsWith(it.key) || id.contains(it.key) }
            .maxByOrNull { it.key.length }
            ?.value
    }

    /**
     * Rough char-count → token estimate. 4 chars/token is the published
     * heuristic for English+code; we use the same on input and output
     * since the agent's transcript mixes both. Off by maybe 20% — fine
     * for a UI meter, not for billing.
     */
    fun estimateTokens(chars: Int): Int = (chars + 3) / 4

    fun estimateCostUsd(rate: Rate, inputChars: Int, outputChars: Int): Double {
        val inTok = estimateTokens(inputChars)
        val outTok = estimateTokens(outputChars)
        return (inTok * rate.inputUsdPerM + outTok * rate.outputUsdPerM) / 1_000_000.0
    }
}
