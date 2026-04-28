package com.glassfiles.data.ai.agent

/**
 * One tool invocation emitted by the model. The agent UI shows each call as
 * a card; the [GitHubToolExecutor] consumes [name] + [argsJson] and produces
 * a [AiToolResult].
 *
 * [id] is the provider's opaque tool-call id (OpenAI's `tool_call_id`,
 * Anthropic's `id` on a `tool_use` block). The follow-up tool-result message
 * must reference the same id so the model can correlate.
 */
data class AiToolCall(
    val id: String,
    val name: String,
    /** Raw JSON-string arguments as the model emitted them. */
    val argsJson: String,
)

/** Result of executing a single [AiToolCall]. */
data class AiToolResult(
    val callId: String,
    val name: String,
    /** Plain-text payload returned to the model on the next turn. */
    val output: String,
    val isError: Boolean = false,
)

/**
 * One step in an agent conversation: either a regular text turn or a batch
 * of tool calls awaiting execution + a paired result list.
 */
sealed class AgentTurn {
    data class Text(val role: String, val content: String) : AgentTurn()
    data class ToolUse(
        val assistantText: String,
        val calls: List<AiToolCall>,
    ) : AgentTurn()
    data class ToolResults(val results: List<AiToolResult>) : AgentTurn()
}
