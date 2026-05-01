package com.glassfiles.data.ai.usage

import android.content.Context
import com.glassfiles.data.ai.ModelPricing
import com.glassfiles.data.ai.models.AiMessage

data class AiUsageEstimate(
    val inputChars: Int,
    val outputChars: Int,
    val inputTokens: Int,
    val outputTokens: Int,
    val totalTokens: Int,
    val costUsd: Double?,
)

object AiUsageAccounting {
    fun estimate(
        providerId: String,
        modelId: String,
        inputChars: Int,
        outputChars: Int,
    ): AiUsageEstimate {
        val inputTokens = ModelPricing.estimateTokens(providerId, modelId, inputChars)
        val outputTokens = ModelPricing.estimateTokens(providerId, modelId, outputChars)
        val cost = ModelPricing.rateFor(providerId, modelId)?.let { rate ->
            ModelPricing.estimateCostUsdFromTokens(rate, inputTokens, outputTokens)
        }
        return AiUsageEstimate(
            inputChars = inputChars,
            outputChars = outputChars,
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            totalTokens = inputTokens + outputTokens,
            costUsd = cost,
        )
    }

    fun messageChars(messages: List<AiMessage>): Int =
        messages.sumOf { message ->
            message.content.length +
                (message.fileContent?.length ?: 0) +
                ((message.imageBase64?.length ?: 0) / IMAGE_TOKEN_CHAR_RATIO) +
                (message.toolCalls?.sumOf { it.name.length + it.argsJson.length } ?: 0)
        }

    fun estimate(
        providerId: String,
        modelId: String,
        messages: List<AiMessage>,
        output: String,
    ): AiUsageEstimate =
        estimate(
            providerId = providerId,
            modelId = modelId,
            inputChars = messageChars(messages),
            outputChars = output.length,
        )

    fun appendEstimated(
        context: Context,
        providerId: String,
        modelId: String,
        mode: AiUsageMode,
        messages: List<AiMessage>,
        output: String,
    ): AiUsageEstimate {
        val estimate = estimate(providerId, modelId, messages, output)
        runCatching {
            AiUsageStore.append(
                context,
                AiUsageRecord(
                    providerId = providerId,
                    modelId = modelId,
                    mode = mode,
                    inputTokens = estimate.inputTokens,
                    outputTokens = estimate.outputTokens,
                    totalTokens = estimate.totalTokens,
                    estimatedInputChars = estimate.inputChars,
                    estimatedOutputChars = estimate.outputChars,
                    costUsd = estimate.costUsd,
                    estimated = true,
                ),
            )
        }
        return estimate
    }

    fun formatTokens(tokens: Int): String =
        when {
            tokens >= 1_000_000 -> String.format(java.util.Locale.US, "%.2fm", tokens / 1_000_000.0)
            tokens >= 1_000 -> String.format(java.util.Locale.US, "%.1fk", tokens / 1_000.0)
            else -> tokens.toString()
        }

    fun formatUsd(value: Double): String =
        when {
            value <= 0.0 -> "\$0.00"
            value < 0.01 -> "<\$0.01"
            value < 1.0 -> String.format(java.util.Locale.US, "\$%.3f", value)
            else -> String.format(java.util.Locale.US, "\$%.2f", value)
        }

    private const val IMAGE_TOKEN_CHAR_RATIO = 8
}
