package com.glassfiles.data.ai.usage

/**
 * Local-only record of one AI request. Written by the agent loop and
 * by every one-shot AI call (chat completion, image gen, video gen, music gen).
 *
 * Privacy notes (see PR-COST-C spec):
 *  - prompt content is NEVER stored
 *  - file contents / tool result bodies are NEVER stored
 *  - API keys / secrets are NEVER stored
 *  - raw provider JSON is NEVER stored
 *
 * Only the small label fields ([providerId], [modelId], [repoName],
 * [branchName]) and numeric counters survive. The user can inspect
 * everything in the Usage screen and delete the on-disk log if they
 * want.
 *
 * @property providerId    [com.glassfiles.data.ai.models.AiProviderId.name].
 * @property modelId       Stable model identifier.
 * @property mode          High-level capability bucket.
 * @property inputTokens   Real input tokens reported by provider, or 0
 *                         if [estimated] is true.
 * @property outputTokens  Real output tokens, or 0 if estimated.
 * @property totalTokens   `inputTokens + outputTokens` (kept separate
 *                         so summaries don't have to recompute it).
 * @property estimatedInputChars  Char-count estimate, used when the
 *                         provider does not return token usage.
 * @property estimatedOutputChars Same for output.
 * @property toolCallsCount Number of tool calls emitted for this
 *                         request. 0 for chat / image / video.
 * @property filesReadCount Number of `read_file*` / `list_dir`-class
 *                         tool calls in this request.
 * @property filesWrittenCount Number of `write_file` / `edit_file` /
 *                         `commit*` tool calls in this request.
 * @property writeProposalsCount Number of non-readOnly tool calls
 *                         submitted for approval.
 * @property repoName      Optional `owner/repo` label.
 * @property branchName    Optional branch label.
 * @property isPrivateRepo `null` when not applicable / unknown.
 * @property costMode      Optional active cost mode for the run.
 *                         `null` for non-agent calls.
 * @property estimated     `true` when token counts are derived from
 *                         char-count heuristics, `false` when the
 *                         provider returned real usage.
 * @property createdAt     Wall-clock timestamp in millis.
 */
data class AiUsageRecord(
    val providerId: String,
    val modelId: String,
    val mode: AiUsageMode,
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val totalTokens: Int = inputTokens + outputTokens,
    val estimatedInputChars: Int = 0,
    val estimatedOutputChars: Int = 0,
    val toolCallsCount: Int = 0,
    val filesReadCount: Int = 0,
    val filesWrittenCount: Int = 0,
    val writeProposalsCount: Int = 0,
    val repoName: String? = null,
    val branchName: String? = null,
    val isPrivateRepo: Boolean? = null,
    val costMode: String? = null,
    val costUsd: Double? = null,
    val estimated: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
)

enum class AiUsageMode {
    /** Plain text chat (no tools). */
    CHAT,

    /** Coding-focused one-shot (PR summary, commit-message gen, etc). */
    CODING,

    /** Image generation. */
    IMAGE,

    /** Video generation. */
    VIDEO,

    /** Music / song generation. */
    MUSIC,

    /** AI Agent run against a GitHub repo. */
    GITHUB_AGENT,

    /** Lightweight side query that selects relevant skills by `when_to_use`. */
    SKILL_AUTO_DETECTION,
}
