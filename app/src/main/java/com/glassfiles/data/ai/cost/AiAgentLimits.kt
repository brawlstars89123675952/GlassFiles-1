package com.glassfiles.data.ai.cost

/**
 * Hard caps applied to a single AI Agent task. The agent loop and
 * tool executor consult these to refuse / truncate operations that
 * would otherwise burn excessive tokens or send too much repo content
 * to an external provider.
 *
 * All numbers are *per-task* unless noted. A "task" is one full
 * `runAgentLoop` invocation — typically the chain of turns triggered
 * by a single user message.
 *
 * @property maxFilesPerTask        cap on distinct file reads + writes
 *                                  the agent may perform in one task.
 * @property maxFileSizeBytes       cap on the size of any single file
 *                                  the agent reads. Larger files are
 *                                  truncated and the model is told.
 * @property maxTotalContextChars   cap on the running sum of context
 *                                  (input + tool outputs + assistant
 *                                  output) sent to the provider. When
 *                                  exceeded the loop stops gracefully.
 * @property maxToolCalls           cap on tool-call iterations.
 *                                  Independent of MAX_ITERATIONS in the
 *                                  agent loop — whichever is smaller wins.
 * @property maxLogLines            cap on lines retained from a workflow
 *                                  run's failed-job logs.
 * @property maxDiffChars           cap on the size of a diff returned by
 *                                  compare_refs / read_pr-style tools.
 * @property maxWriteProposals      cap on write_file / edit_file / commit
 *                                  proposals approved per task. Beyond
 *                                  this the agent is forced to stop and
 *                                  report.
 * @property warnDiffChars          soft threshold — above this we warn
 *                                  the user before submitting; below it
 *                                  we do not.
 */
data class AiAgentLimits(
    val maxFilesPerTask: Int,
    val maxFileSizeBytes: Int,
    val maxTotalContextChars: Int,
    val maxToolCalls: Int,
    val maxLogLines: Int,
    val maxDiffChars: Int,
    val maxWriteProposals: Int,
    val warnDiffChars: Int = maxDiffChars / 2,
)
