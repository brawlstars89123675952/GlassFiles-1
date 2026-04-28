package com.glassfiles.data.ai.providers

import android.content.Context
import com.glassfiles.data.ai.SystemPrompts
import com.glassfiles.data.ai.models.AiProviderId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.URL

object XaiProvider : OpenAiCompatProvider(
    id = AiProviderId.XAI,
    systemPrompt = SystemPrompts.DEFAULT,
) {
    override fun baseUrl(context: Context): String = "https://api.x.ai/v1"

    /**
     * xAI exposes `POST /v1/images/generations` for `grok-2-image*` etc., with
     * the same shape as OpenAI's images API — share the helper.
     */
    override suspend fun generateImage(
        context: Context,
        modelId: String,
        prompt: String,
        apiKey: String,
        size: String,
        n: Int,
    ): List<String> = OpenAiCompatImageGen.generate(
        baseUrl = baseUrl(context),
        cacheDir = context.cacheDir,
        providerLabel = id.displayName,
        fileTag = "xai",
        modelId = modelId,
        prompt = prompt,
        apiKey = apiKey,
        size = size,
        n = n,
    )

    /**
     * `grok-imagine-video` text-to-video.
     *
     *  POST /v1/videos/generations  → { "request_id": ... }
     *  GET  /v1/videos/{request_id} → { "status": "pending|done|expired", "video": { "url": ... } }
     *
     * Polled every 5s; on `done` we download the resulting mp4 to cacheDir.
     * Spec: https://docs.x.ai/developers/rest-api-reference/inference/videos
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
        val createUrl = "${baseUrl(context)}/videos/generations"
        val body = JSONObject()
            .put("model", modelId)
            .put("prompt", prompt)
            .put("duration", durationSec.coerceIn(1, 15))
            .put("aspect_ratio", aspectRatio.takeIf { it in supportedAspectRatios } ?: "16:9")
            .put("resolution", "720p")
            .toString()
        onProgress("queue")
        val createConn = Http.postJson(
            createUrl,
            body,
            mapOf("Authorization" to "Bearer $apiKey"),
        )
        Http.ensureOk(createConn, id.displayName)
        val requestId = JSONObject(
            createConn.inputStream.bufferedReader().use { it.readText() },
        ).optString("request_id", "")
        createConn.disconnect()
        if (requestId.isBlank()) throw RuntimeException("${id.displayName}: no request_id in response")

        val pollUrl = "${baseUrl(context)}/videos/$requestId"
        val deadline = System.currentTimeMillis() + 15 * 60_000L
        while (System.currentTimeMillis() < deadline) {
            val conn = Http.open(pollUrl, "GET", mapOf("Authorization" to "Bearer $apiKey"))
            Http.ensureOk(conn, id.displayName)
            val raw = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            val obj = JSONObject(raw)
            val status = obj.optString("status", "unknown").lowercase()
            onProgress(status)
            when (status) {
                "done", "succeeded" -> {
                    val videoUrl = obj.optJSONObject("video")?.optString("url", "").orEmpty()
                    if (videoUrl.isBlank()) throw RuntimeException("${id.displayName}: no video.url in result")
                    val outDir = File(context.cacheDir, "ai_videos").apply { mkdirs() }
                    val target = File(outDir, "xai_${System.currentTimeMillis()}.mp4")
                    URL(videoUrl).openStream().use { input -> target.outputStream().use { input.copyTo(it) } }
                    return@withContext target.absolutePath
                }
                "expired", "failed", "error" -> throw RuntimeException("${id.displayName}: task $status")
                else -> {} // pending / running / queue
            }
            delay(5_000)
        }
        throw RuntimeException("${id.displayName}: task didn't finish in 15min")
    }

    private val supportedAspectRatios = setOf("1:1", "16:9", "9:16", "4:3", "3:4", "3:2", "2:3")
}
