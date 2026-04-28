package com.glassfiles.data.ai.providers

import android.content.Context
import com.glassfiles.data.ai.AiKeyStore
import com.glassfiles.data.ai.CapabilityClassifier
import com.glassfiles.data.ai.SystemPrompts
import com.glassfiles.data.ai.models.AiMessage
import com.glassfiles.data.ai.models.AiModel
import com.glassfiles.data.ai.models.AiProviderId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Google AI Studio (Gemini) provider.
 *
 * Uses `generativelanguage.googleapis.com/v1beta/models` for listing and
 * `models/{id}:streamGenerateContent` (SSE) for chat.
 *
 * Imagen / Veo image+video generation are exposed via separate Vertex AI APIs
 * that need a different auth scheme — those will be wired up in the dedicated
 * image/video PRs and will surface a clear error if the user only has a plain
 * Gemini key.
 */
object GoogleProvider : AiProvider {
    override val id: AiProviderId = AiProviderId.GOOGLE

    private const val DEFAULT_BASE = "https://generativelanguage.googleapis.com/v1beta"

    /**
     * Returns the API root WITHOUT a trailing `/models` segment.
     *
     * Pre-existing users may have a proxy URL stored as
     * `https://example.com/v1beta/models` (older builds appended
     * `/{modelId}:streamGenerateContent` directly to that). We tolerate both
     * forms by stripping a trailing `/models` and any trailing slashes.
     */
    private fun base(context: Context): String =
        AiKeyStore.getGeminiProxy(context)
            .trimEnd('/')
            .removeSuffix("/models")
            .ifBlank { DEFAULT_BASE }

    override suspend fun listModels(context: Context, apiKey: String): List<AiModel> = withContext(Dispatchers.IO) {
        // Google uses ?key=<apiKey> rather than Authorization header
        val conn = Http.open("${base(context)}/models?key=$apiKey", "GET")
        Http.ensureOk(conn, id.displayName)
        val raw = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()

        val arr = JSONObject(raw).optJSONArray("models") ?: return@withContext emptyList()
        (0 until arr.length()).mapNotNull { i ->
            val obj = arr.optJSONObject(i) ?: return@mapNotNull null
            val name = obj.optString("name", "") // "models/gemini-2.5-pro"
            val rawId = name.removePrefix("models/")
            if (rawId.isBlank()) return@mapNotNull null

            // Google publishes supportedGenerationMethods — use that as a hard signal
            // alongside our pattern-based classifier.
            val supported = obj.optJSONArray("supportedGenerationMethods")?.let { sg ->
                (0 until sg.length()).map { sg.optString(it).orEmpty() }
            }.orEmpty()
            val isChatish = supported.any { it.contains("generateContent", ignoreCase = true) }
            if (!isChatish && supported.isNotEmpty()) return@mapNotNull null

            AiModel(
                providerId = id,
                id = rawId,
                displayName = obj.optString("displayName", rawId),
                capabilities = CapabilityClassifier.classify(id, rawId),
                contextWindow = obj.optInt("inputTokenLimit").takeIf { it > 0 },
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
        val url = "${base(context)}/models/${modelId}:streamGenerateContent?alt=sse&key=$apiKey"

        val contents = JSONArray()
        messages.forEach { msg ->
            val role = if (msg.role == "assistant") "model" else "user"
            val parts = JSONArray()
            if (msg.content.isNotBlank()) parts.put(JSONObject().put("text", msg.content))
            if (msg.fileContent != null) {
                parts.put(JSONObject().put("text", "\n--- File content ---\n${msg.fileContent}"))
            }
            if (msg.imageBase64 != null) {
                parts.put(
                    JSONObject().put(
                        "inlineData",
                        JSONObject().put("mimeType", "image/jpeg").put("data", msg.imageBase64),
                    ),
                )
            }
            contents.put(JSONObject().put("role", role).put("parts", parts))
        }

        val body = JSONObject()
            .put("contents", contents)
            .put(
                "systemInstruction",
                JSONObject().put(
                    "parts",
                    JSONArray().put(JSONObject().put("text", SystemPrompts.DEFAULT)),
                ),
            )
            .toString()

        val conn = Http.postJson(url, body, emptyMap())
        Http.ensureOk(conn, id.displayName)

        val sb = StringBuilder()
        Http.iterateSse(conn) { data ->
            try {
                val json = JSONObject(data)
                val candidates = json.optJSONArray("candidates") ?: return@iterateSse
                if (candidates.length() == 0) return@iterateSse
                val parts = candidates.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts")
                    ?: return@iterateSse
                for (i in 0 until parts.length()) {
                    val text = parts.getJSONObject(i).optString("text", "")
                    if (text.isNotEmpty()) {
                        sb.append(text)
                        onChunk(text)
                    }
                }
            } catch (_: Exception) { /* keep-alive */ }
        }
        conn.disconnect()
        sb.toString()
    }
}
