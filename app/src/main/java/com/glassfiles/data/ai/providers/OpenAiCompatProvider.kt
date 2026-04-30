package com.glassfiles.data.ai.providers

import android.content.Context
import com.glassfiles.data.ai.CapabilityClassifier
import com.glassfiles.data.ai.agent.AiTool
import com.glassfiles.data.ai.agent.AiToolCall
import com.glassfiles.data.ai.models.AiMessage
import com.glassfiles.data.ai.models.AiModel
import com.glassfiles.data.ai.models.AiProviderId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.glassfiles.data.ai.providers.Http.optStringOrEmpty
import org.json.JSONArray
import org.json.JSONObject

/**
 * Base class for any provider that exposes the OpenAI REST shape:
 * `GET {base}/models` and `POST {base}/chat/completions` with SSE streaming.
 *
 * Concrete providers (OpenAI, xAI, Moonshot, Alibaba's compat mode, OpenRouter)
 * just supply the base URL and any extra request headers.
 */
abstract class OpenAiCompatProvider(
    override val id: AiProviderId,
    private val systemPrompt: String,
) : AiProvider {

    /** Endpoint root, e.g. `https://api.openai.com/v1`. No trailing slash. */
    protected abstract fun baseUrl(context: Context): String

    /** Optional extra headers (User-Agent, x-portkey-..., HTTP-Referer for OpenRouter, etc.). */
    protected open fun extraHeaders(context: Context): Map<String, String> = emptyMap()

    /** Filter raw model ids before building [AiModel]s. Override to drop fine-tunes/internal ids. */
    protected open fun acceptModelId(rawId: String): Boolean = rawId.isNotBlank()

    /** Pretty-print a raw model id. */
    protected open fun displayName(rawId: String): String = rawId

    override suspend fun listModels(context: Context, apiKey: String): List<AiModel> = withContext(Dispatchers.IO) {
        val conn = Http.open(
            "${baseUrl(context)}/models",
            "GET",
            mapOf("Authorization" to "Bearer $apiKey") + extraHeaders(context),
        )
        Http.ensureOk(conn, id.displayName)
        val raw = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()
        val data = JSONObject(raw).optJSONArray("data") ?: return@withContext emptyList()
        (0 until data.length()).mapNotNull { i ->
            val obj = data.optJSONObject(i) ?: return@mapNotNull null
            val rawId = obj.optString("id", "")
            if (!acceptModelId(rawId)) return@mapNotNull null
            AiModel(
                providerId = id,
                id = rawId,
                displayName = displayName(rawId),
                capabilities = CapabilityClassifier.classify(id, rawId),
                contextWindow = obj.optInt("context_length").takeIf { it > 0 }
                    ?: obj.optInt("context_window").takeIf { it > 0 },
                deprecated = obj.optBoolean("deprecated", false),
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
        val conn = Http.postJson(
            "${baseUrl(context)}/chat/completions",
            buildChatBody(modelId, messages),
            mapOf("Authorization" to "Bearer $apiKey") + extraHeaders(context),
        )
        Http.ensureOk(conn, id.displayName)

        val sb = StringBuilder()
        Http.iterateSse(conn) { data ->
            try {
                val choices = JSONObject(data).optJSONArray("choices") ?: return@iterateSse
                if (choices.length() == 0) return@iterateSse
                val delta = choices.getJSONObject(0).optJSONObject("delta") ?: return@iterateSse
                val text = delta.optStringOrEmpty("content")
                if (text.isNotEmpty()) {
                    sb.append(text)
                    onChunk(text)
                }
            } catch (_: Exception) {
                // ignore parse errors on individual chunks; some providers send keep-alive frames
            }
        }
        conn.disconnect()
        sb.toString()
    }

    /** Builds the `chat/completions` payload. Vision models accept an array `content`. */
    protected open fun buildChatBody(modelId: String, messages: List<AiMessage>): String {
        val msgs = JSONArray()
        if (systemPrompt.isNotBlank()) {
            msgs.put(JSONObject().put("role", "system").put("content", systemPrompt))
        }
        messages.forEach { msg ->
            when {
                msg.imageBase64 != null -> {
                    val parts = JSONArray()
                    parts.put(JSONObject().put("type", "text").put("text", msg.content.ifBlank { "(image)" }))
                    parts.put(
                        JSONObject().put("type", "image_url").put(
                            "image_url",
                            JSONObject().put("url", "data:image/jpeg;base64,${msg.imageBase64}"),
                        ),
                    )
                    msgs.put(JSONObject().put("role", msg.role).put("content", parts))
                }
                msg.fileContent != null -> {
                    val full = if (msg.content.isNotBlank()) {
                        "${msg.content}\n\n--- File content ---\n${msg.fileContent}"
                    } else {
                        msg.fileContent
                    }
                    msgs.put(JSONObject().put("role", msg.role).put("content", full))
                }
                else -> msgs.put(JSONObject().put("role", msg.role).put("content", msg.content))
            }
        }
        return JSONObject()
            .put("model", modelId)
            .put("messages", msgs)
            .put("stream", true)
            .toString()
    }

    /**
     * OpenAI-style function-calling: non-streaming `chat/completions` with
     * `tools` set. Inherited by xAI / Moonshot / OpenRouter so all of them
     * automatically gain agent support.
     *
     * Spec: https://platform.openai.com/docs/guides/function-calling
     */
    override suspend fun chatWithTools(
        context: Context,
        modelId: String,
        messages: List<AiMessage>,
        tools: List<AiTool>,
        apiKey: String,
    ): AiToolTurn = withContext(Dispatchers.IO) {
        val msgs = JSONArray()
        if (systemPrompt.isNotBlank()) {
            msgs.put(JSONObject().put("role", "system").put("content", systemPrompt))
        }
        messages.forEach { m -> msgs.put(toolMessageToJson(m)) }

        val toolsJson = JSONArray()
        tools.forEach { t ->
            toolsJson.put(
                JSONObject()
                    .put("type", "function")
                    .put(
                        "function",
                        JSONObject()
                            .put("name", t.name)
                            .put("description", t.description)
                            .put("parameters", t.parameters),
                    ),
            )
        }

        val body = JSONObject()
            .put("model", modelId)
            .put("messages", msgs)
            .put("tools", toolsJson)
            .put("tool_choice", "auto")
            .toString()

        val conn = Http.postJson(
            "${baseUrl(context)}/chat/completions",
            body,
            mapOf("Authorization" to "Bearer $apiKey") + extraHeaders(context),
        )
        Http.ensureOk(conn, id.displayName)
        val raw = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()

        val choice = JSONObject(raw).optJSONArray("choices")?.optJSONObject(0)
        val message = choice?.optJSONObject("message") ?: return@withContext AiToolTurn("", emptyList())
        val text = message.optStringOrEmpty("content")
        val toolCallsArr = message.optJSONArray("tool_calls") ?: JSONArray()
        val calls = (0 until toolCallsArr.length()).mapNotNull { i ->
            val tc = toolCallsArr.optJSONObject(i) ?: return@mapNotNull null
            val fn = tc.optJSONObject("function") ?: return@mapNotNull null
            AiToolCall(
                id = tc.optString("id", "call_$i"),
                name = fn.optString("name", ""),
                argsJson = fn.optString("arguments", "{}"),
            )
        }
        AiToolTurn(assistantText = text, toolCalls = calls)
    }

    /**
     * Streaming variant of [chatWithTools]. Sets `stream: true` and walks
     * the SSE deltas, forwarding `delta.content` chunks to [onTextDelta]
     * and accumulating `delta.tool_calls[*].function.{name, arguments}`
     * fragments into per-index buffers. The final `AiToolCall` list is
     * returned in the [AiToolTurn].
     *
     * Spec: https://platform.openai.com/docs/guides/function-calling#streaming
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
        if (systemPrompt.isNotBlank()) {
            msgs.put(JSONObject().put("role", "system").put("content", systemPrompt))
        }
        messages.forEach { m -> msgs.put(toolMessageToJson(m)) }

        val toolsJson = JSONArray()
        tools.forEach { t ->
            toolsJson.put(
                JSONObject()
                    .put("type", "function")
                    .put(
                        "function",
                        JSONObject()
                            .put("name", t.name)
                            .put("description", t.description)
                            .put("parameters", t.parameters),
                    ),
            )
        }

        val body = JSONObject()
            .put("model", modelId)
            .put("messages", msgs)
            .put("tools", toolsJson)
            .put("tool_choice", "auto")
            .put("stream", true)
            .toString()

        val conn = Http.postJson(
            "${baseUrl(context)}/chat/completions",
            body,
            mapOf("Authorization" to "Bearer $apiKey") + extraHeaders(context),
        )
        Http.ensureOk(conn, id.displayName)

        val text = StringBuilder()
        val toolBuffers = sortedMapOf<Int, ToolBuffer>()

        Http.iterateSse(conn) { data ->
            val choices = runCatching { JSONObject(data).optJSONArray("choices") }.getOrNull()
                ?: return@iterateSse
            if (choices.length() == 0) return@iterateSse
            val delta = choices.optJSONObject(0)?.optJSONObject("delta") ?: return@iterateSse

            val content = delta.optStringOrEmpty("content")
            if (content.isNotEmpty()) {
                text.append(content)
                onTextDelta(content)
            }

            val tcArr = delta.optJSONArray("tool_calls") ?: return@iterateSse
            for (i in 0 until tcArr.length()) {
                val tc = tcArr.optJSONObject(i) ?: continue
                val idx = tc.optInt("index", i)
                val buf = toolBuffers.getOrPut(idx) { ToolBuffer() }
                tc.optString("id", "").takeIf { it.isNotEmpty() }?.let { buf.id = it }
                val fn = tc.optJSONObject("function")
                if (fn != null) {
                    fn.optString("name", "").takeIf { it.isNotEmpty() }?.let { buf.name = it }
                    val argsFragment = fn.optString("arguments", "")
                    if (argsFragment.isNotEmpty()) buf.argsJson.append(argsFragment)
                }
            }
        }
        conn.disconnect()

        val calls = toolBuffers.values.mapIndexed { i, buf ->
            AiToolCall(
                id = buf.id ?: "call_$i",
                name = buf.name.orEmpty(),
                argsJson = buf.argsJson.toString().ifBlank { "{}" },
            )
        }
        AiToolTurn(assistantText = text.toString(), toolCalls = calls)
    }

    /** Mutable scratch buffer used while OpenAI streams a tool call's
     * `function.name` and `function.arguments` across multiple deltas. */
    private data class ToolBuffer(
        var id: String? = null,
        var name: String? = null,
        val argsJson: StringBuilder = StringBuilder(),
    )

    /**
     * Serialises an [AiMessage] for the function-calling endpoint. Tool-result
     * messages get a `tool_call_id`; assistant turns that emitted tool calls
     * carry them back as `tool_calls` for context continuity.
     */
    private fun toolMessageToJson(msg: AiMessage): JSONObject {
        val o = JSONObject().put("role", msg.role)
        when {
            msg.role == "tool" -> {
                o.put("tool_call_id", msg.toolCallId.orEmpty())
                if (msg.toolName != null) o.put("name", msg.toolName)
                o.put("content", msg.content)
            }
            msg.toolCalls != null -> {
                o.put("content", msg.content.ifBlank { JSONObject.NULL })
                val arr = JSONArray()
                msg.toolCalls.forEach { tc ->
                    arr.put(
                        JSONObject()
                            .put("id", tc.id)
                            .put("type", "function")
                            .put(
                                "function",
                                JSONObject().put("name", tc.name).put("arguments", tc.argsJson),
                            ),
                    )
                }
                o.put("tool_calls", arr)
            }
            msg.imageBase64 != null -> {
                val parts = JSONArray()
                parts.put(JSONObject().put("type", "text").put("text", msg.content.ifBlank { "(image)" }))
                parts.put(
                    JSONObject().put("type", "image_url").put(
                        "image_url",
                        JSONObject().put("url", "data:image/jpeg;base64,${msg.imageBase64}"),
                    ),
                )
                o.put("content", parts)
            }
            else -> o.put("content", msg.content)
        }
        return o
    }
}
