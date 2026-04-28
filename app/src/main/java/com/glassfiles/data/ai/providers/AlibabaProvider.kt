package com.glassfiles.data.ai.providers

import android.content.Context
import com.glassfiles.data.ai.AiKeyStore
import com.glassfiles.data.ai.SystemPrompts
import com.glassfiles.data.ai.models.AiProviderId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URL

/**
 * Alibaba DashScope (Qwen + Wanx).
 *
 * Chat goes through DashScope's OpenAI-compatible endpoint
 * (`/compatible-mode/v1`). Image and video generation use the native async
 * "task" API on `/api/v1/services/aigc/...`: submit job, poll
 * `/api/v1/tasks/{taskId}` every 5s, then download the produced asset.
 */
object AlibabaProvider : OpenAiCompatProvider(
    id = AiProviderId.ALIBABA,
    systemPrompt = SystemPrompts.DEFAULT,
) {
    override fun baseUrl(context: Context): String {
        val region = AiKeyStore.getQwenRegion(context)
        return when (region) {
            "cn" -> "https://dashscope.aliyuncs.com/compatible-mode/v1"
            "us" -> "https://dashscope-us.aliyuncs.com/compatible-mode/v1"
            "hk" -> "https://cn-hongkong.dashscope.aliyuncs.com/compatible-mode/v1"
            else -> "https://dashscope-intl.aliyuncs.com/compatible-mode/v1"
        }
    }

    /** Native (non-compatible) DashScope base for async aigc tasks. */
    private fun nativeBase(context: Context): String {
        val region = AiKeyStore.getQwenRegion(context)
        return when (region) {
            "cn" -> "https://dashscope.aliyuncs.com/api/v1"
            "us" -> "https://dashscope-us.aliyuncs.com/api/v1"
            "hk" -> "https://cn-hongkong.dashscope.aliyuncs.com/api/v1"
            else -> "https://dashscope-intl.aliyuncs.com/api/v1"
        }
    }

    /**
     * Wanx text-to-image via the async task API.
     *
     * `wanx-v1` and friends accept size like `1024*1024` (with `*`).
     * We accept the standard `WIDTHxHEIGHT` shape from the UI and translate.
     */
    override suspend fun generateImage(
        context: Context,
        modelId: String,
        prompt: String,
        apiKey: String,
        size: String,
        n: Int,
    ): List<String> = withContext(Dispatchers.IO) {
        val createUrl = "${nativeBase(context)}/services/aigc/text2image/image-synthesis"
        val body = JSONObject()
            .put("model", modelId)
            .put(
                "input",
                JSONObject().put("prompt", prompt),
            )
            .put(
                "parameters",
                JSONObject()
                    .put("size", size.replace("x", "*").replace("X", "*"))
                    .put("n", n.coerceIn(1, 4)),
            )
            .toString()
        val createConn = Http.postJson(
            createUrl,
            body,
            mapOf(
                "Authorization" to "Bearer $apiKey",
                "X-DashScope-Async" to "enable",
            ),
        )
        Http.ensureOk(createConn, id.displayName)
        val taskId = JSONObject(
            createConn.inputStream.bufferedReader().use { it.readText() },
        ).optJSONObject("output")?.optString("task_id", "").orEmpty()
        createConn.disconnect()
        if (taskId.isBlank()) throw RuntimeException("${id.displayName}: no task_id in response")

        val outputObj = pollTask(context, taskId, apiKey, timeoutMs = 5 * 60_000L) {}
        val results = outputObj.optJSONArray("results") ?: JSONArray()
        val out = mutableListOf<String>()
        val outDir = File(context.cacheDir, "ai_images").apply { mkdirs() }
        for (i in 0 until results.length()) {
            val url = results.optJSONObject(i)?.optString("url", "").orEmpty()
            if (url.isBlank()) continue
            val target = File(outDir, "alibaba_${System.currentTimeMillis()}_$i.png")
            URL(url).openStream().use { input -> target.outputStream().use { input.copyTo(it) } }
            out += target.absolutePath
        }
        if (out.isEmpty()) throw RuntimeException("${id.displayName}: no images in result")
        out
    }

    /**
     * wan-video text-to-video via the async task API. Same pattern as wanx
     * images: submit, poll, download.
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
        val createUrl = "${nativeBase(context)}/services/aigc/video-generation/video-synthesis"
        val body = JSONObject()
            .put("model", modelId)
            .put(
                "input",
                JSONObject().put("prompt", prompt),
            )
            .put(
                "parameters",
                JSONObject()
                    .put("size", videoSizeFor(aspectRatio))
                    .put("duration", durationSec.coerceIn(2, 10)),
            )
            .toString()
        onProgress("queue")
        val createConn = Http.postJson(
            createUrl,
            body,
            mapOf(
                "Authorization" to "Bearer $apiKey",
                "X-DashScope-Async" to "enable",
            ),
        )
        Http.ensureOk(createConn, id.displayName)
        val taskId = JSONObject(
            createConn.inputStream.bufferedReader().use { it.readText() },
        ).optJSONObject("output")?.optString("task_id", "").orEmpty()
        createConn.disconnect()
        if (taskId.isBlank()) throw RuntimeException("${id.displayName}: no task_id in response")

        val outputObj = pollTask(context, taskId, apiKey, timeoutMs = 15 * 60_000L, onProgress)
        val videoUrl = outputObj.optString("video_url", "")
        if (videoUrl.isBlank()) throw RuntimeException("${id.displayName}: no video_url in result")

        val outDir = File(context.cacheDir, "ai_videos").apply { mkdirs() }
        val target = File(outDir, "alibaba_${System.currentTimeMillis()}.mp4")
        URL(videoUrl).openStream().use { input -> target.outputStream().use { input.copyTo(it) } }
        target.absolutePath
    }

    /**
     * Polls `/tasks/{taskId}` every 5s until the job leaves PENDING/RUNNING.
     * Surfaces intermediate states via [onProgress] ("pending", "running",
     * "succeeded"). Returns the `output` JSONObject on success; throws on
     * FAILED / UNKNOWN / CANCELED / timeout.
     */
    private suspend fun pollTask(
        context: Context,
        taskId: String,
        apiKey: String,
        timeoutMs: Long,
        onProgress: (String) -> Unit,
    ): JSONObject {
        val pollUrl = "${nativeBase(context)}/tasks/$taskId"
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val conn = Http.open(
                pollUrl,
                "GET",
                mapOf("Authorization" to "Bearer $apiKey"),
            )
            Http.ensureOk(conn, id.displayName)
            val raw = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            val obj = JSONObject(raw)
            val output = obj.optJSONObject("output") ?: throw RuntimeException("${id.displayName}: missing output")
            val status = output.optString("task_status", "UNKNOWN").uppercase()
            onProgress(status.lowercase())
            when (status) {
                "SUCCEEDED" -> return output
                "PENDING", "RUNNING" -> {}
                else -> throw RuntimeException("${id.displayName}: task $status")
            }
            delay(5_000)
        }
        throw RuntimeException("${id.displayName}: task didn't finish in ${timeoutMs / 60_000}min")
    }

    private fun videoSizeFor(aspectRatio: String): String = when (aspectRatio) {
        "16:9" -> "1280*720"
        "9:16" -> "720*1280"
        "1:1" -> "960*960"
        "4:3" -> "1024*768"
        "3:4" -> "768*1024"
        else -> "1280*720"
    }
}
