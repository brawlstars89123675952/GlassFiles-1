package com.glassfiles.data.ai.providers

import android.content.Context
import com.glassfiles.data.ai.CapabilityClassifier
import com.glassfiles.data.ai.SystemPrompts
import com.glassfiles.data.ai.agent.AiTool
import com.glassfiles.data.ai.agent.AiToolCall
import com.glassfiles.data.ai.models.AiMessage
import com.glassfiles.data.ai.models.AiModel
import com.glassfiles.data.ai.models.AiProviderId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Anthropic Claude provider.
 *
 * Uses `/v1/models` for listing and `/v1/messages` with SSE for streaming.
 * Anthropic-specific headers: `anthropic-version`, `x-api-key` (no `Authorization`).
 *
 * Anthropic does NOT support image generation or video generation.
 */
object AnthropicProvider : AiProvider {
    override val id: AiProviderId = AiProviderId.ANTHROPIC

    private const val BASE = "https://api.anthropic.com/v1"
    private const val ANTHROPIC_VERSION = "2023-06-01"

    private fun authHeaders(apiKey: String): Map<String, String> = mapOf(
        "x-api-key" to apiKey,
        "anthropic-version" to ANTHROPIC_VERSION,
    )

    override suspend fun listModels(context: Context, apiKey: String): List<AiModel> = withContext(Dispatchers.IO) {
        val conn = Http.open("$BASE/models", "GET", authHeaders(apiKey))
        Http.ensureOk(conn, id.displayName)
        val raw = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()

        val data = JSONObject(raw).optJSONArray("data") ?: return@withContext emptyList()
        (0 until data.length()).mapNotNull { i ->
            val obj = data.optJSONObject(i) ?: return@mapNotNull null
            val rawId = obj.optString("id", "")
            if (rawId.isBlank()) return@mapNotNull null
            AiModel(
                providerId = id,
                id = rawId,
                displayName = obj.optString("display_name", rawId),
                capabilities = CapabilityClassifier.classify(id, rawId),
            )
        }
    }

    override suspend fun chat(
        context: Context,
        modelId: String,
        messages: List<AiMessage>,
        apiKey: String,
        onChunk: (String) -> Unit,
    ): String = withContext(Dispatchers.IO) {
        val msgs = JSONArray()
        messages.forEach { msg ->
            val role = if (msg.role == "system") "user" else msg.role  // Anthropic doesn't accept "system" inside messages
            val content = JSONArray()
            if (msg.imageBase64 != null) {
                content.put(
                    JSONObject()
                        .put("type", "image")
                        .put(
                            "source",
                            JSONObject()
                                .put("type", "base64")
                                .put("media_type", "image/jpeg")
                                .put("data", msg.imageBase64),
                        ),
                )
            }
            val textBlock = if (msg.fileContent != null) {
                if (msg.content.isNotBlank()) "${msg.content}\n\n--- File content ---\n${msg.fileContent}" else msg.fileContent
            } else msg.content
            if (textBlock.isNotBlank()) content.put(JSONObject().put("type", "text").put("text", textBlock))

            msgs.put(JSONObject().put("role", role).put("content", content))
        }

        val body = JSONObject()
            .put("model", modelId)
            .put("messages", msgs)
            .put("system", SystemPrompts.DEFAULT)
            .put("max_tokens", 4096)
            .put("stream", true)
            .toString()

        val conn = Http.postJson("$BASE/messages", body, authHeaders(apiKey))
        Http.ensureOk(conn, id.displayName)

        val sb = StringBuilder()
        Http.iterateSse(conn) { data ->
            try {
                val event = JSONObject(data)
                if (event.optString("type") == "content_block_delta") {
                    val delta = event.optJSONObject("delta") ?: return@iterateSse
                    val text = delta.optString("text", "")
                    if (text.isNotEmpty()) {
                        sb.append(text)
                        onChunk(text)
                    }
                }
            } catch (_: Exception) { /* keep-alive frame */ }
        }
        conn.disconnect()
        sb.toString()
    }

    /**
     * Anthropic tool-use: non-streaming `/v1/messages` with `tools` set.
     * Tool calls come back as `tool_use` content blocks in the assistant turn;
     * tool results are sent next time as `tool_result` blocks on a `user` turn.
     *
     * Spec: https://docs.anthropic.com/en/docs/build-with-claude/tool-use
     */
    override suspend fun chatWithTools(
        context: Context,
        modelId: String,
        messages: List<AiMessage>,
        tools: List<AiTool>,
        apiKey: String,
    ): AiToolTurn = withContext(Dispatchers.IO) {
        val msgs = JSONArray()
        messages.forEach { m -> msgs.put(toolMessageToJson(m)) }

        val toolsJson = JSONArray()
        tools.forEach { t ->
            toolsJson.put(
                JSONObject()
                    .put("name", t.name)
                    .put("description", t.description)
                    .put("input_schema", t.parameters),
            )
        }

        val body = JSONObject()
            .put("model", modelId)
            .put("messages", msgs)
            .put("system", SystemPrompts.DEFAULT)
            .put("max_tokens", 4096)
            .put("tools", toolsJson)
            .toString()

        val conn = Http.postJson("$BASE/messages", body, authHeaders(apiKey))
        Http.ensureOk(conn, id.displayName)
        val raw = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()

        val content = JSONObject(raw).optJSONArray("content") ?: JSONArray()
        val text = StringBuilder()
        val calls = mutableListOf<AiToolCall>()
        for (i in 0 until content.length()) {
            val block = content.optJSONObject(i) ?: continue
            when (block.optString("type")) {
                "text" -> text.append(block.optString("text", ""))
                "tool_use" -> {
                    val input = block.optJSONObject("input") ?: JSONObject()
                    calls += AiToolCall(
                        id = block.optString("id", "call_$i"),
                        name = block.optString("name", ""),
                        argsJson = input.toString(),
                    )
                }
            }
        }
        AiToolTurn(assistantText = text.toString(), toolCalls = calls)
    }

    /**
     * Streaming variant of [chatWithTools]. Sets `stream: true` on
     * `/v1/messages` and walks the SSE event stream, dispatching
     * `text_delta` events to [onTextDelta] in real time and accumulating
     * `input_json_delta` events into per-block buffers. Buffers are
     * finalised on `content_block_stop` and surfaced as fully-parsed
     * [AiToolCall]s in the returned [AiToolTurn].
     *
     * Spec: https://docs.anthropic.com/en/docs/build-with-claude/streaming
     */
    override suspend fun chatWithToolsStreaming(
        context: Context,
        modelId: String,
        messages: List<AiMessage>,
        tools: List<AiTool>,
        apiKey: String,
        onTextDelta: (String) -> Unit,
    ): AiToolTurn = withContext(Dispatchers.IO) {
        val msgs = JSONArray()
        messages.forEach { m -> msgs.put(toolMessageToJson(m)) }

        val toolsJson = JSONArray()
        tools.forEach { t ->
            toolsJson.put(
                JSONObject()
                    .put("name", t.name)
                    .put("description", t.description)
                    .put("input_schema", t.parameters),
            )
        }

        val body = JSONObject()
            .put("model", modelId)
            .put("messages", msgs)
            .put("system", SystemPrompts.DEFAULT)
            .put("max_tokens", 4096)
            .put("tools", toolsJson)
            .put("stream", true)
            .toString()

        val conn = Http.postJson("$BASE/messages", body, authHeaders(apiKey))
        Http.ensureOk(conn, id.displayName)

        val text = StringBuilder()
        // Per-block scratch state: index → (kind, id, name, json buffer)
        val toolBuffers = mutableMapOf<Int, ToolBuffer>()
        val finishedCalls = mutableListOf<AiToolCall>()

        Http.iterateSse(conn) { data ->
            val event = runCatching { JSONObject(data) }.getOrNull() ?: return@iterateSse
            when (event.optString("type")) {
                "content_block_start" -> {
                    val idx = event.optInt("index", -1)
                    val block = event.optJSONObject("content_block") ?: return@iterateSse
                    if (block.optString("type") == "tool_use") {
                        toolBuffers[idx] = ToolBuffer(
                            id = block.optString("id", "call_$idx"),
                            name = block.optString("name", ""),
                        )
                    }
                }
                "content_block_delta" -> {
                    val idx = event.optInt("index", -1)
                    val delta = event.optJSONObject("delta") ?: return@iterateSse
                    when (delta.optString("type")) {
                        "text_delta" -> {
                            val chunk = delta.optString("text", "")
                            if (chunk.isNotEmpty()) {
                                text.append(chunk)
                                onTextDelta(chunk)
                            }
                        }
                        "input_json_delta" -> {
                            val partial = delta.optString("partial_json", "")
                            toolBuffers[idx]?.argsJson?.append(partial)
                        }
                    }
                }
                "content_block_stop" -> {
                    val idx = event.optInt("index", -1)
                    toolBuffers.remove(idx)?.let { tb ->
                        val argsString = tb.argsJson.toString().ifBlank { "{}" }
                        finishedCalls += AiToolCall(
                            id = tb.id,
                            name = tb.name,
                            argsJson = argsString,
                        )
                    }
                }
                // message_start / message_delta / message_stop / ping —
                // no per-event work needed beyond what Http.iterateSse
                // already filters.
            }
        }
        conn.disconnect()

        AiToolTurn(assistantText = text.toString(), toolCalls = finishedCalls)
    }

    /** Mutable scratch buffer used while a `tool_use` block streams in. */
    private data class ToolBuffer(
        val id: String,
        val name: String,
        val argsJson: StringBuilder = StringBuilder(),
    )

    private fun toolMessageToJson(msg: AiMessage): JSONObject {
        // Anthropic only accepts user/assistant in `messages`; map "system" → "user".
        val role = when (msg.role) {
            "tool" -> "user"  // tool results ride on a user turn as content blocks
            "system" -> "user"
            else -> msg.role
        }
        val blocks = JSONArray()

        when {
            msg.role == "tool" -> {
                blocks.put(
                    JSONObject()
                        .put("type", "tool_result")
                        .put("tool_use_id", msg.toolCallId.orEmpty())
                        .put("content", msg.content),
                )
            }
            msg.toolCalls != null -> {
                if (msg.content.isNotBlank()) {
                    blocks.put(JSONObject().put("type", "text").put("text", msg.content))
                }
                msg.toolCalls.forEach { tc ->
                    val input = runCatching { JSONObject(tc.argsJson) }.getOrElse { JSONObject() }
                    blocks.put(
                        JSONObject()
                            .put("type", "tool_use")
                            .put("id", tc.id)
                            .put("name", tc.name)
                            .put("input", input),
                    )
                }
            }
            msg.imageBase64 != null -> {
                blocks.put(
                    JSONObject()
                        .put("type", "image")
                        .put(
                            "source",
                            JSONObject()
                                .put("type", "base64")
                                .put("media_type", "image/jpeg")
                                .put("data", msg.imageBase64),
                        ),
                )
                if (msg.content.isNotBlank()) blocks.put(JSONObject().put("type", "text").put("text", msg.content))
            }
            else -> {
                val full = if (msg.fileContent != null) {
                    if (msg.content.isNotBlank()) "${msg.content}\n\n--- File content ---\n${msg.fileContent}" else msg.fileContent
                } else msg.content
                if (full.isNotBlank()) blocks.put(JSONObject().put("type", "text").put("text", full))
            }
        }

        return JSONObject().put("role", role).put("content", blocks)
    }
}
