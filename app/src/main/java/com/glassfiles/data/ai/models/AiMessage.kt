package com.glassfiles.data.ai.models

import com.glassfiles.data.ai.agent.AiToolCall

/**
 * Chat-style message that flows between the UI and a provider.
 *
 * For backwards compatibility with the older `ChatMessage` type, [imageBase64]
 * and [fileContent] are kept as flat optional fields; richer multimodal payloads
 * (e.g. multiple attachments) can be modelled later via a dedicated parts list.
 *
 * [role] follows OpenAI conventions: `"user"`, `"assistant"`, `"system"`,
 * and — for agent / tool-use flows — `"tool"`.
 *
 * Tool-use fields (only non-null in agent flows):
 *  - [toolCalls] — set on assistant turns where the model emitted one or more
 *    tool invocations alongside (optional) free text in [content].
 *  - [toolCallId] / [toolName] — set on `role = "tool"` messages carrying the
 *    output of a previously-emitted tool call back to the model.
 */
data class AiMessage(
    val role: String,
    val content: String,
    val imageBase64: String? = null,
    val fileContent: String? = null,
    val toolCalls: List<AiToolCall>? = null,
    val toolCallId: String? = null,
    val toolName: String? = null,
)
