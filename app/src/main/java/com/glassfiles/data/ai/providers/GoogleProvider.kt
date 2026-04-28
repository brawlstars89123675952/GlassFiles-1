package com.glassfiles.data.ai.providers

import android.content.Context
import android.util.Base64
import com.glassfiles.data.ai.AiKeyStore
import com.glassfiles.data.ai.CapabilityClassifier
import com.glassfiles.data.ai.SystemPrompts
import com.glassfiles.data.ai.models.AiMessage
import com.glassfiles.data.ai.models.AiModel
import com.glassfiles.data.ai.models.AiProviderId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URL

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

    /**
     * Generates [n] images via the Gemini API's `:predict` endpoint, which
     * exposes the Imagen family on the same `generativelanguage.googleapis.com`
     * host that hosts `gemini-*` chat. Body and response shapes match the
     * documented Imagen REST format:
     *
     * Request:  `{"instances":[{"prompt":"…"}], "parameters":{"sampleCount": N, "aspectRatio": "1:1"}}`
     * Response: `{"predictions":[{"bytesBase64Encoded":"…","mimeType":"image/png"}]}`
     *
     * Maps the requested [size] (e.g. "1024x1024") to an aspectRatio Imagen
     * accepts. The user's plain Gemini key works for AI Studio Imagen — Vertex
     * AI is only required for enterprise / paid-tier Imagen, which is a
     * separate code path we don't expose here.
     */
    override suspend fun generateImage(
        context: Context,
        modelId: String,
        prompt: String,
        apiKey: String,
        size: String,
        n: Int,
    ): List<String> = withContext(Dispatchers.IO) {
        val url = "${base(context)}/models/$modelId:predict?key=$apiKey"
        val body = JSONObject()
            .put("instances", JSONArray().put(JSONObject().put("prompt", prompt)))
            .put(
                "parameters",
                JSONObject()
                    .put("sampleCount", n.coerceIn(1, 4))
                    .put("aspectRatio", aspectRatioForSize(size)),
            )
            .toString()

        val conn = Http.postJson(url, body, emptyMap())
        Http.ensureOk(conn, id.displayName)
        val raw = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()

        val preds = JSONObject(raw).optJSONArray("predictions") ?: JSONArray()
        val outDir = File(context.cacheDir, "ai_images").apply { mkdirs() }
        val results = mutableListOf<String>()
        val now = System.currentTimeMillis()
        for (i in 0 until preds.length()) {
            val item = preds.getJSONObject(i)
            val b64 = item.optString("bytesBase64Encoded", "")
            if (b64.isBlank()) continue
            val mime = item.optString("mimeType", "image/png")
            val ext = if (mime.contains("jpeg")) "jpg" else "png"
            val target = File(outDir, "google_${now}_$i.$ext")
            target.writeBytes(Base64.decode(b64, Base64.DEFAULT))
            results += target.absolutePath
        }
        if (results.isEmpty()) {
            throw RuntimeException("${id.displayName} returned no image data")
        }
        results
    }

    /**
     * Veo video generation via the Gemini API's long-running operation
     * pattern.
     *
     * 1. `POST /models/{modelId}:predictLongRunning?key=KEY` with body
     *    `{"instances":[{"prompt":"..."}], "parameters":{"aspectRatio":"16:9","durationSeconds":N,"personGeneration":"allow"}}`.
     *    Returns `{"name": "models/.../operations/<op_id>"}`.
     * 2. Poll `GET /<op_name>?key=KEY` every 5s until `done == true`, then
     *    download `response.generateVideoResponse.generatedSamples[0].video.uri`.
     *
     * The signed `video.uri` requires the `?key=KEY` query param to download.
     */
    override suspend fun generateVideo(
        context: Context,
        modelId: String,
        prompt: String,
        apiKey: String,
        durationSec: Int,
        aspectRatio: String,
        onProgress: (String) -> Unit,
    ): String = withContext(Dispatchers.IO) {
        val createUrl = "${base(context)}/models/$modelId:predictLongRunning?key=$apiKey"
        val body = JSONObject()
            .put("instances", JSONArray().put(JSONObject().put("prompt", prompt)))
            .put(
                "parameters",
                JSONObject()
                    .put("aspectRatio", aspectRatio)
                    .put("durationSeconds", durationSec.coerceIn(2, 8))
                    .put("personGeneration", "allow"),
            )
            .toString()

        onProgress("queue")
        val createConn = Http.postJson(createUrl, body, mapOf("Content-Type" to "application/json"))
        Http.ensureOk(createConn, id.displayName)
        val createRaw = createConn.inputStream.bufferedReader().use { it.readText() }
        createConn.disconnect()
        val opName = JSONObject(createRaw).optString("name", "")
        if (opName.isBlank()) throw RuntimeException("${id.displayName}: empty operation name")

        val opUrl = "${base(context)}/$opName?key=$apiKey"
        val deadline = System.currentTimeMillis() + 15 * 60_000L
        var videoUri = ""
        while (System.currentTimeMillis() < deadline) {
            val pollConn = Http.open(opUrl, "GET")
            Http.ensureOk(pollConn, id.displayName)
            val raw = pollConn.inputStream.bufferedReader().use { it.readText() }
            pollConn.disconnect()
            val obj = JSONObject(raw)
            val done = obj.optBoolean("done", false)
            onProgress(if (done) "ready" else "running")
            if (done) {
                val err = obj.optJSONObject("error")
                if (err != null) {
                    throw RuntimeException("${id.displayName}: ${err.optString("message", "operation failed")}")
                }
                val samples = obj.optJSONObject("response")
                    ?.optJSONObject("generateVideoResponse")
                    ?.optJSONArray("generatedSamples")
                    ?: JSONArray()
                if (samples.length() == 0) throw RuntimeException("${id.displayName}: no samples in response")
                videoUri = samples.optJSONObject(0)?.optJSONObject("video")?.optString("uri", "").orEmpty()
                break
            }
            delay(5_000)
        }
        if (videoUri.isBlank()) throw RuntimeException("${id.displayName}: video didn't finish in 15min")

        val outDir = File(context.cacheDir, "ai_videos").apply { mkdirs() }
        val target = File(outDir, "google_${System.currentTimeMillis()}.mp4")
        val signedUrl = if (videoUri.contains("?")) "$videoUri&key=$apiKey" else "$videoUri?key=$apiKey"
        URL(signedUrl).openStream().use { input -> target.outputStream().use { input.copyTo(it) } }
        target.absolutePath
    }

    private fun aspectRatioForSize(size: String): String {
        val parts = size.lowercase().split("x")
        if (parts.size != 2) return "1:1"
        val w = parts[0].toIntOrNull() ?: return "1:1"
        val h = parts[1].toIntOrNull() ?: return "1:1"
        return when {
            w == h -> "1:1"
            w > h && w.toFloat() / h >= 1.7f -> "16:9"
            w > h -> "4:3"
            h.toFloat() / w >= 1.7f -> "9:16"
            else -> "3:4"
        }
    }
}
