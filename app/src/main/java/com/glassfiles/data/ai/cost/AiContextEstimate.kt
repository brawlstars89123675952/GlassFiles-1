package com.glassfiles.data.ai.cost

/**
 * Mutable accumulator that tracks how many characters of context a
 * single agent task has burned through. Created fresh per task by
 * the agent loop and incremented as input messages, tool outputs,
 * and assistant text accumulate.
 *
 * Why characters and not tokens: real token counts depend on the
 * tokeniser of the active provider (OpenAI cl100k vs Anthropic vs
 * Gemini), and we do not want to pull a tokeniser dependency just
 * for a cost cap. Using characters with a published 4-chars/token
 * heuristic is good enough for a guard rail; users see real usage
 * after the fact in the usage screen (PR-COST-C).
 *
 * Concurrency: not thread-safe. The agent loop is single-coroutine
 * by construction, so callers do not need locks.
 */
class AiContextEstimate(
    val limits: AiAgentLimits,
) {
    var totalChars: Int = 0
        private set
    var filesTouched: Int = 0
        private set
    var toolCallsExecuted: Int = 0
        private set
    var writeProposals: Int = 0
        private set

    /** True when totalChars is within [warnThreshold]% of the cap. */
    val warnThreshold: Double = 0.8

    fun addInput(chars: Int) { totalChars += chars }
    fun addOutput(chars: Int) { totalChars += chars }
    fun bumpFile() { filesTouched += 1 }
    fun bumpToolCall() { toolCallsExecuted += 1 }
    fun bumpWriteProposal() { writeProposals += 1 }

    /** Hit the file-count cap? */
    val filesExhausted: Boolean
        get() = filesTouched >= limits.maxFilesPerTask
    /** Hit the tool-call cap? */
    val toolCallsExhausted: Boolean
        get() = toolCallsExecuted >= limits.maxToolCalls
    /** Burned through the context-character cap? */
    val contextExhausted: Boolean
        get() = totalChars >= limits.maxTotalContextChars
    /** Hit the write-proposal cap? */
    val writeProposalsExhausted: Boolean
        get() = writeProposals >= limits.maxWriteProposals
    /** Within 80% of the context cap — caller may want to warn the user. */
    val nearContextCap: Boolean
        get() = totalChars >= (limits.maxTotalContextChars * warnThreshold).toInt()

    /**
     * Truncates [text] so its length plus the current running total
     * does not exceed [AiAgentLimits.maxTotalContextChars]. Returns a
     * pair of (possiblyShortenedText, wasTruncated).
     */
    fun fitToBudget(text: String): Pair<String, Boolean> {
        val remaining = (limits.maxTotalContextChars - totalChars).coerceAtLeast(0)
        return if (text.length <= remaining) text to false
        else (text.take(remaining) + "\n[…cost-policy: truncated to fit context budget]") to true
    }

    /**
     * Truncates [text] so it does not exceed [AiAgentLimits.maxFileSizeBytes].
     * Used by tools that return a single file's contents — the
     * running total is *not* consulted here, only the per-file cap.
     */
    fun fitFileToBudget(text: String): Pair<String, Boolean> {
        val cap = limits.maxFileSizeBytes
        return if (text.length <= cap) text to false
        else (text.take(cap) + "\n[…cost-policy: file truncated, ${text.length - cap} bytes withheld]") to true
    }
}
