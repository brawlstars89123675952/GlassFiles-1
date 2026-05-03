package com.glassfiles.data.ai.usage

import com.glassfiles.data.ai.ModelPricing
import java.util.Calendar

/**
 * Aggregated view of a list of [AiUsageRecord]s. Shown in the Usage
 * screen for "Today / This week / This month" tabs and grouped by
 * provider / model / mode.
 *
 * @property recordCount  Total records in the window.
 * @property totalTokens  Sum of [AiUsageRecord.totalTokens].
 * @property estimatedRecordCount Number of records with `estimated=true`.
 * @property toolCallsCount Sum of agent tool calls.
 * @property filesReadCount Sum of files read across agent runs.
 * @property filesWrittenCount Sum of files written.
 * @property byProvider   Per-provider buckets keyed by `providerId`.
 * @property byModel      Per-model buckets keyed by `modelId`.
 * @property byMode       Per-mode buckets keyed by [AiUsageMode.name].
 */
data class AiUsageSummary(
    val recordCount: Int,
    val totalTokens: Long,
    val totalChars: Long,
    val totalCostUsd: Double?,
    val estimatedRecordCount: Int,
    val estimatedTokens: Long,
    val reportedTokens: Long,
    val estimatedCostUsd: Double?,
    val reportedCostUsd: Double?,
    val toolCallsCount: Long,
    val filesReadCount: Long,
    val filesWrittenCount: Long,
    val byProvider: List<AiUsageBucket>,
    val byModel: List<AiUsageBucket>,
    val byMode: List<AiUsageBucket>,
) {
    val hasAnyRealUsage: Boolean
        get() = recordCount > estimatedRecordCount

    val reportedPercent: Int
        get() = if (recordCount == 0) 0 else ((recordCount - estimatedRecordCount) * 100 / recordCount)
}

/** Single grouping row inside an [AiUsageSummary]. */
data class AiUsageBucket(
    val key: String,
    val recordCount: Int,
    val totalTokens: Long,
    val totalChars: Long,
    val totalCostUsd: Double?,
)

/** Time-window selectors for the Usage screen tabs. */
enum class AiUsageWindow {
    TODAY,
    WEEK,
    MONTH;

    /**
     * Inclusive start-of-window in millis since epoch, anchored to
     * the device's local timezone (so "Today" lines up with the
     * user's clock, not UTC).
     */
    fun startMillis(now: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        when (this) {
            TODAY -> { /* already at start of day */ }
            WEEK -> cal.add(Calendar.DAY_OF_YEAR, -6)
            MONTH -> cal.add(Calendar.DAY_OF_YEAR, -29)
        }
        return cal.timeInMillis
    }
}

/**
 * Compute an [AiUsageSummary] for [records] within [window]. Records
 * outside the window are dropped. Buckets are sorted by token total
 * descending so the heaviest provider / model surfaces first.
 */
fun summarise(records: List<AiUsageRecord>, window: AiUsageWindow): AiUsageSummary {
    val cutoff = window.startMillis()
    val inWindow = records.filter { it.createdAt >= cutoff }
    if (inWindow.isEmpty()) {
        return AiUsageSummary(
            recordCount = 0,
            totalTokens = 0L,
            totalChars = 0L,
            totalCostUsd = null,
            estimatedRecordCount = 0,
            estimatedTokens = 0L,
            reportedTokens = 0L,
            estimatedCostUsd = null,
            reportedCostUsd = null,
            toolCallsCount = 0L,
            filesReadCount = 0L,
            filesWrittenCount = 0L,
            byProvider = emptyList(),
            byModel = emptyList(),
            byMode = emptyList(),
        )
    }
    val totalTokens = inWindow.sumOf { it.effectiveTotalTokens().toLong() }
    val totalChars = inWindow.sumOf {
        (it.estimatedInputChars + it.estimatedOutputChars).toLong()
    }
    val costs = inWindow.mapNotNull { it.effectiveCostUsd() }
    val totalCostUsd = costs.takeIf { it.isNotEmpty() }?.sum()
    val estimated = inWindow.count { it.estimated }
    val estimatedTokens = inWindow.filter { it.estimated }.sumOf { it.effectiveTotalTokens().toLong() }
    val reportedTokens = inWindow.filterNot { it.estimated }.sumOf { it.effectiveTotalTokens().toLong() }
    val estimatedCosts = inWindow.filter { it.estimated }.mapNotNull { it.effectiveCostUsd() }
    val reportedCosts = inWindow.filterNot { it.estimated }.mapNotNull { it.effectiveCostUsd() }
    val toolCalls = inWindow.sumOf { it.toolCallsCount.toLong() }
    val read = inWindow.sumOf { it.filesReadCount.toLong() }
    val written = inWindow.sumOf { it.filesWrittenCount.toLong() }

    fun bucket(group: (AiUsageRecord) -> String): List<AiUsageBucket> =
        inWindow.groupBy(group).map { (k, rs) ->
            AiUsageBucket(
                key = k,
                recordCount = rs.size,
                totalTokens = rs.sumOf { it.effectiveTotalTokens().toLong() },
                totalChars = rs.sumOf {
                    (it.estimatedInputChars + it.estimatedOutputChars).toLong()
                },
                totalCostUsd = rs.mapNotNull { it.effectiveCostUsd() }.takeIf { it.isNotEmpty() }?.sum(),
            )
        }.sortedByDescending { it.totalTokens.takeIf { t -> t > 0 } ?: it.totalChars }

    return AiUsageSummary(
        recordCount = inWindow.size,
        totalTokens = totalTokens,
        totalChars = totalChars,
        totalCostUsd = totalCostUsd,
        estimatedRecordCount = estimated,
        estimatedTokens = estimatedTokens,
        reportedTokens = reportedTokens,
        estimatedCostUsd = estimatedCosts.takeIf { it.isNotEmpty() }?.sum(),
        reportedCostUsd = reportedCosts.takeIf { it.isNotEmpty() }?.sum(),
        toolCallsCount = toolCalls,
        filesReadCount = read,
        filesWrittenCount = written,
        byProvider = bucket { it.providerId },
        byModel = bucket { it.modelId },
        byMode = bucket { it.mode.displayLabel() },
    )
}

private fun AiUsageRecord.effectiveInputTokens(): Int =
    inputTokens.takeIf { it > 0 }
        ?: ModelPricing.estimateTokens(providerId, modelId, estimatedInputChars)

private fun AiUsageRecord.effectiveOutputTokens(): Int =
    outputTokens.takeIf { it > 0 }
        ?: ModelPricing.estimateTokens(providerId, modelId, estimatedOutputChars)

private fun AiUsageRecord.effectiveTotalTokens(): Int =
    totalTokens.takeIf { it > 0 }
        ?: (effectiveInputTokens() + effectiveOutputTokens())

private fun AiUsageRecord.effectiveCostUsd(): Double? {
    costUsd?.let { return it }
    val rate = ModelPricing.rateFor(providerId, modelId) ?: return null
    return ModelPricing.estimateCostUsdFromTokens(
        rate,
        effectiveInputTokens(),
        effectiveOutputTokens(),
    )
}

private fun AiUsageMode.displayLabel(): String = when (this) {
    AiUsageMode.CHAT -> "chat"
    AiUsageMode.CODING -> "coding"
    AiUsageMode.IMAGE -> "image"
    AiUsageMode.VIDEO -> "video"
    AiUsageMode.MUSIC -> "music"
    AiUsageMode.GITHUB_AGENT -> "github agent"
    AiUsageMode.SKILL_AUTO_DETECTION -> "skill auto-detection"
}
