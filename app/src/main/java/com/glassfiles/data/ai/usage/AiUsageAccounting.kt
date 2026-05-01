package com.glassfiles.data.ai.usage

import android.content.Context
import com.glassfiles.data.ai.ModelPricing
import com.glassfiles.data.ai.models.AiMessage
import com.glassfiles.data.ai.providers.AiTokenUsage
import java.util.UUID

data class AiUsageEstimate(
    val inputChars: Int,
    val outputChars: Int,
    val inputTokens: Int,
    val outputTokens: Int,
    val totalTokens: Int,
    val costUsd: Double?,
    val estimated: Boolean = true,
)

object AiUsageAccounting {
    fun estimate(
        providerId: String,
        modelId: String,
        inputChars: Int,
        outputChars: Int,
        context: Context? = null,
    ): AiUsageEstimate {
        val tokenizer = TokenizerRegistry.forProvider(providerId, modelId)
        val rawInputTokens = tokenizer.countTokens("x".repeat(inputChars.coerceAtLeast(0)))
        val rawOutputTokens = tokenizer.countTokens("x".repeat(outputChars.coerceAtLeast(0)))
        val inputTokens = context?.let { AiUsageDatabase.get(it).calibratedTokenCount(providerId, modelId, rawInputTokens) }
            ?: rawInputTokens
        val outputTokens = context?.let { AiUsageDatabase.get(it).calibratedTokenCount(providerId, modelId, rawOutputTokens) }
            ?: rawOutputTokens
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
            estimated = true,
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
        context: Context? = null,
    ): AiUsageEstimate =
        estimate(
            providerId = providerId,
            modelId = modelId,
            inputChars = messageChars(messages),
            outputChars = output.length,
            context = context,
        )

    fun appendEstimated(
        context: Context,
        providerId: String,
        modelId: String,
        mode: AiUsageMode,
        messages: List<AiMessage>,
        output: String,
    ): AiUsageEstimate {
        val estimate = estimate(providerId, modelId, messages, output, context)
        appendUsage(
            context = context,
            providerId = providerId,
            modelId = modelId,
            mode = mode,
            estimated = estimate,
            reported = null,
        )
        return estimate
    }

    fun appendReportedOrEstimated(
        context: Context,
        providerId: String,
        modelId: String,
        mode: AiUsageMode,
        messages: List<AiMessage>,
        output: String,
        reported: AiTokenUsage?,
        chatId: String = "default",
        messageId: String = UUID.randomUUID().toString(),
    ): AiUsageEstimate {
        val estimate = estimate(providerId, modelId, messages, output, context)
        appendUsage(
            context = context,
            providerId = providerId,
            modelId = modelId,
            mode = mode,
            estimated = estimate,
            reported = reported,
            chatId = chatId,
            messageId = messageId,
        )
        return if (reported != null && reported.totalTokens > 0) {
            estimate.copy(
                inputTokens = reported.inputTokens + reported.cacheCreationTokens + reported.cacheReadTokens,
                outputTokens = reported.outputTokens,
                totalTokens = reported.totalTokens,
                costUsd = ModelPricing.rateFor(providerId, modelId)?.let { ModelPricing.calculateCostUsd(it, reported) },
                estimated = false,
            )
        } else estimate
    }

    fun appendUsage(
        context: Context,
        providerId: String,
        modelId: String,
        mode: AiUsageMode,
        estimated: AiUsageEstimate,
        reported: AiTokenUsage?,
        chatId: String = "default",
        messageId: String = UUID.randomUUID().toString(),
    ) {
        val rate = ModelPricing.rateFor(providerId, modelId)
        val reportedCost = reported?.let { usage -> rate?.let { ModelPricing.calculateCostUsd(it, usage) } }
        val isEstimated = reported == null || reported.totalTokens <= 0 || !reported.isComplete
        val effectiveInput = if (isEstimated) estimated.inputTokens else reported!!.inputTokens + reported.cacheCreationTokens + reported.cacheReadTokens
        val effectiveOutput = if (isEstimated) estimated.outputTokens else reported!!.outputTokens
        val effectiveCost = if (isEstimated) estimated.costUsd else reportedCost

        runCatching {
            AiUsageStore.append(
                context,
                AiUsageRecord(
                    providerId = providerId,
                    modelId = modelId,
                    mode = mode,
                    inputTokens = effectiveInput,
                    outputTokens = effectiveOutput,
                    totalTokens = effectiveInput + effectiveOutput,
                    estimatedInputChars = estimated.inputChars,
                    estimatedOutputChars = estimated.outputChars,
                    costUsd = effectiveCost,
                    estimated = isEstimated,
                ),
            )
            AiUsageDatabase.get(context).insertUsage(
                MessageUsageRecord(
                    messageId = messageId,
                    chatId = chatId,
                    provider = providerId,
                    model = modelId,
                    estimatedInputTokens = estimated.inputTokens,
                    estimatedOutputTokens = estimated.outputTokens,
                    estimatedCost = estimated.costUsd,
                    reportedInputTokens = reported?.let { it.inputTokens + it.cacheCreationTokens + it.cacheReadTokens },
                    reportedOutputTokens = reported?.outputTokens,
                    reportedCost = reportedCost,
                    isEstimated = isEstimated,
                    hasCacheHit = reported?.hasCacheHit == true,
                ),
            )
            if (reported != null && reported.totalTokens > 0) {
                AiUsageDatabase.get(context).upsertCalibration(
                    provider = providerId,
                    model = modelId,
                    estimatedTotal = estimated.totalTokens,
                    actualTotal = reported.totalTokens,
                )
            }
        }
    }

    fun formatTokens(tokens: Int, estimated: Boolean = false): String {
        val body = when {
            tokens >= 1_000_000 -> String.format(java.util.Locale.US, "%.2fm", tokens / 1_000_000.0)
            tokens >= 1_000 -> String.format(java.util.Locale.US, "%.1fk", tokens / 1_000.0)
            else -> tokens.toString()
        }
        return if (estimated) "~$body" else body
    }

    fun formatUsd(value: Double, estimated: Boolean = false): String {
        val body = when {
            value <= 0.0 -> "\$0.00"
            value < 0.01 -> "<\$0.01"
            value < 1.0 -> String.format(java.util.Locale.US, "\$%.3f", value)
            else -> String.format(java.util.Locale.US, "\$%.2f", value)
        }
        return if (estimated) "~$body" else body
    }

    private const val IMAGE_TOKEN_CHAR_RATIO = 8
}
