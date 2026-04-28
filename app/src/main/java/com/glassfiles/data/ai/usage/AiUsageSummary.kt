package com.glassfiles.data.ai.usage

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
    val estimatedRecordCount: Int,
    val toolCallsCount: Long,
    val filesReadCount: Long,
    val filesWrittenCount: Long,
    val byProvider: List<AiUsageBucket>,
    val byModel: List<AiUsageBucket>,
    val byMode: List<AiUsageBucket>,
) {
    val hasAnyRealUsage: Boolean
        get() = recordCount > estimatedRecordCount
}

/** Single grouping row inside an [AiUsageSummary]. */
data class AiUsageBucket(
    val key: String,
    val recordCount: Int,
    val totalTokens: Long,
    val totalChars: Long,
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
            estimatedRecordCount = 0,
            toolCallsCount = 0L,
            filesReadCount = 0L,
            filesWrittenCount = 0L,
            byProvider = emptyList(),
            byModel = emptyList(),
            byMode = emptyList(),
        )
    }
    val totalTokens = inWindow.sumOf { it.totalTokens.toLong() }
    val totalChars = inWindow.sumOf {
        (it.estimatedInputChars + it.estimatedOutputChars).toLong()
    }
    val estimated = inWindow.count { it.estimated }
    val toolCalls = inWindow.sumOf { it.toolCallsCount.toLong() }
    val read = inWindow.sumOf { it.filesReadCount.toLong() }
    val written = inWindow.sumOf { it.filesWrittenCount.toLong() }

    fun bucket(group: (AiUsageRecord) -> String): List<AiUsageBucket> =
        inWindow.groupBy(group).map { (k, rs) ->
            AiUsageBucket(
                key = k,
                recordCount = rs.size,
                totalTokens = rs.sumOf { it.totalTokens.toLong() },
                totalChars = rs.sumOf {
                    (it.estimatedInputChars + it.estimatedOutputChars).toLong()
                },
            )
        }.sortedByDescending { it.totalTokens.takeIf { t -> t > 0 } ?: it.totalChars }

    return AiUsageSummary(
        recordCount = inWindow.size,
        totalTokens = totalTokens,
        totalChars = totalChars,
        estimatedRecordCount = estimated,
        toolCallsCount = toolCalls,
        filesReadCount = read,
        filesWrittenCount = written,
        byProvider = bucket { it.providerId },
        byModel = bucket { it.modelId },
        byMode = bucket { it.mode.name },
    )
}
