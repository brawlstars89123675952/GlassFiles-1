package com.glassfiles.data.ai.providers

import android.content.Context
import com.glassfiles.data.ai.models.AiCapability
import com.glassfiles.data.ai.models.AiModel
import com.glassfiles.data.ai.models.AiProviderId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.net.URLEncoder

/**
 * Official ACEMusic / ACE-Step cloud API.
 *
 * Music generation is a separate flow from chat/image/video:
 * `/release_task` submits work, `/query_result` polls it, and `/v1/audio`
 * downloads the generated file.
 */
object AceMusicProvider : AiProvider {
    override val id: AiProviderId = AiProviderId.ACEMUSIC

    private const val BASE_URL = "https://api.acemusic.ai"
    private val fallbackModels = listOf(
        "acestep-v15-turbo",
        "acestep-v15-turbo-shift3",
    )

    override suspend fun listModels(context: Context, apiKey: String): List<AiModel> = withContext(Dispatchers.IO) {
        val conn = Http.open("$BASE_URL/v1/models", headers = authHeaders(apiKey))
        Http.ensureOk(conn, id.displayName)
        val raw = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()
        parseModels(raw).ifEmpty { fallbackModelList() }
    }

    override suspend fun chat(
        context: Context,
        modelId: String,
        messages: List<com.glassfiles.data.ai.models.AiMessage>,
        apiKey: String,
        onChunk: (String) -> Unit,
    ): String = throw UnsupportedOperationException("${id.displayName} is a music-generation provider only")

    override suspend fun generateMusic(
        context: Context,
        modelId: String,
        request: AiMusicRequest,
        apiKey: String,
        onProgress: (String) -> Unit,
    ): List<AiMusicResult> = withContext(Dispatchers.IO) {
        onProgress("submit")
        val taskId = releaseTask(modelId, request, apiKey)
        onProgress("queued:$taskId")
        val records = pollTask(taskId, apiKey, onProgress)
        val outDir = File(context.cacheDir, "ai_music").apply { mkdirs() }
        records.mapIndexedNotNull { index, record ->
            val fileUrl = record.optString("file", "").ifBlank { record.optString("audio_url", "") }
            if (fileUrl.isBlank()) return@mapIndexedNotNull null
            val ext = extensionFor(request.audioFormat, fileUrl)
            val target = File(outDir, "acemusic_${System.currentTimeMillis()}_$index.$ext")
            downloadAudio(fileUrl, apiKey, target)
            val metas = record.optJSONObject("metas")
            AiMusicResult(
                filePath = target.absolutePath,
                prompt = record.optString("prompt", request.prompt),
                lyrics = record.optString("lyrics", request.lyrics),
                bpm = metas?.optInt("bpm")?.takeIf { it > 0 },
                keyScale = metas?.optString("keyscale", "") ?: metas?.optString("key_scale", "").orEmpty(),
                timeSignature = metas?.optString("timesignature", "") ?: metas?.optString("time_signature", "").orEmpty(),
                durationSec = metas?.optDouble("duration")?.takeIf { it > 0.0 }?.toFloat(),
                seed = record.optString("seed_value", ""),
                taskId = taskId,
            )
        }.ifEmpty {
            throw RuntimeException("${id.displayName}: no audio files in result")
        }
    }

    private fun releaseTask(modelId: String, request: AiMusicRequest, apiKey: String): String {
        val body = JSONObject().apply {
            put("model", modelId)
            put("thinking", request.thinking)
            put("use_format", request.useFormat)
            put("vocal_language", request.vocalLanguage)
            put("audio_format", request.audioFormat)
            put("sample_mode", request.sampleMode)
            put("inference_steps", request.inferenceSteps.coerceIn(1, 200))
            put("guidance_scale", request.guidanceScale)
            put("use_random_seed", request.useRandomSeed)
            put("batch_size", request.batchSize.coerceIn(1, 8))
            put("shift", request.shift)
            put("infer_method", request.inferMethod)
            put("use_adg", request.useAdg)
            put("use_cot_caption", request.useCotCaption)
            put("use_cot_language", request.useCotLanguage)
            put("constrained_decoding", request.constrainedDecoding)
            put("allow_lm_batch", request.allowLmBatch)
            if (request.prompt.isNotBlank()) put("prompt", request.prompt)
            if (request.lyrics.isNotBlank()) put("lyrics", request.lyrics)
            if (request.sampleQuery.isNotBlank()) put("sample_query", request.sampleQuery)
            if (request.timesteps.isNotBlank()) put("timesteps", request.timesteps)
            request.durationSec?.takeIf { it > 0f }?.let { put("audio_duration", it.coerceIn(10f, 600f)) }
            request.bpm?.takeIf { it > 0 }?.let { put("bpm", it.coerceIn(30, 300)) }
            if (request.keyScale.isNotBlank()) put("key_scale", request.keyScale)
            if (request.timeSignature.isNotBlank()) put("time_signature", request.timeSignature)
            if (!request.useRandomSeed) put("seed", request.seed ?: -1)
            request.cfgIntervalStart?.let { put("cfg_interval_start", it.coerceIn(0f, 1f)) }
            request.cfgIntervalEnd?.let { put("cfg_interval_end", it.coerceIn(0f, 1f)) }
            request.lmTemperature?.let { put("lm_temperature", it.coerceIn(0f, 2f)) }
            request.lmCfgScale?.let { put("lm_cfg_scale", it.coerceIn(0f, 20f)) }
            if (request.lmNegativePrompt.isNotBlank()) put("lm_negative_prompt", request.lmNegativePrompt)
            request.lmTopK?.takeIf { it > 0 }?.let { put("lm_top_k", it.coerceAtMost(500)) }
            request.lmTopP?.let { put("lm_top_p", it.coerceIn(0f, 1f)) }
            request.lmRepetitionPenalty?.let { put("lm_repetition_penalty", it.coerceIn(0f, 5f)) }
        }.toString()
        val conn = Http.postJson("$BASE_URL/release_task", body, authHeaders(apiKey))
        Http.ensureOk(conn, id.displayName)
        val raw = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()
        val root = JSONObject(raw).also { ensureApiOk(it) }
        val data = root.optJSONObject("data") ?: root
        val taskId = data.optString("task_id", data.optString("job_id", ""))
        if (taskId.isBlank()) throw RuntimeException("${id.displayName}: no task_id in response")
        return taskId
    }

    private suspend fun pollTask(
        taskId: String,
        apiKey: String,
        onProgress: (String) -> Unit,
    ): List<JSONObject> {
        val deadline = System.currentTimeMillis() + 20 * 60_000L
        while (System.currentTimeMillis() < deadline) {
            val body = JSONObject().put("task_id_list", JSONArray().put(taskId)).toString()
            val conn = Http.postJson("$BASE_URL/query_result", body, authHeaders(apiKey))
            Http.ensureOk(conn, id.displayName)
            val raw = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            val root = JSONObject(raw).also { ensureApiOk(it) }
            val task = root.optJSONArray("data")?.optJSONObject(0)
                ?: throw RuntimeException("${id.displayName}: missing task status")
            val status = task.optInt("status", 0)
            onProgress(statusLabel(status))
            when (status) {
                1 -> return parseResultRecords(task.opt("result"))
                2 -> throw RuntimeException("${id.displayName}: task failed")
                else -> delay(4_000)
            }
        }
        throw RuntimeException("${id.displayName}: task did not finish in 20min")
    }

    private fun downloadAudio(fileValue: String, apiKey: String, target: File) {
        val url = when {
            fileValue.startsWith("http://") || fileValue.startsWith("https://") -> fileValue
            fileValue.startsWith("/v1/audio") -> "$BASE_URL$fileValue"
            fileValue.startsWith("/") -> "$BASE_URL/v1/audio?path=${URLEncoder.encode(fileValue, "UTF-8")}"
            else -> "$BASE_URL/v1/audio?path=${URLEncoder.encode(fileValue, "UTF-8")}"
        }
        val conn = Http.open(url, headers = authHeaders(apiKey))
        Http.ensureOk(conn, id.displayName)
        conn.inputStream.use { input -> target.outputStream().use { input.copyTo(it) } }
        conn.disconnect()
    }

    private fun parseModels(raw: String): List<AiModel> {
        val root = JSONObject(raw).also { ensureApiOk(it) }
        val data = root.opt("data")
        val models = when (data) {
            is JSONObject -> data.optJSONArray("models")
            is JSONArray -> data
            else -> root.optJSONArray("models")
        } ?: return emptyList()
        val defaultModel = (data as? JSONObject)?.optString("default_model", "").orEmpty()
        return (0 until models.length()).mapNotNull { i ->
            val item = models.opt(i)
            val name = when (item) {
                is JSONObject -> item.optString("name", item.optString("id", ""))
                is String -> item
                else -> ""
            }
            if (name.isBlank()) return@mapNotNull null
            AiModel(
                providerId = id,
                id = name,
                displayName = if (name == defaultModel) "$name · default" else name,
                capabilities = setOf(AiCapability.MUSIC_GEN),
            )
        }
    }

    private fun parseResultRecords(value: Any?): List<JSONObject> {
        val parsed = when (value) {
            is JSONArray -> value
            is JSONObject -> JSONArray().put(value)
            is String -> {
                if (value.isBlank()) JSONArray() else runCatching {
                    when (val token = JSONTokener(value).nextValue()) {
                        is JSONArray -> token
                        is JSONObject -> JSONArray().put(token)
                        else -> JSONArray()
                    }
                }.getOrElse {
                    JSONArray().put(JSONObject().put("file", value))
                }
            }
            else -> JSONArray()
        }
        return (0 until parsed.length()).mapNotNull { parsed.optJSONObject(it) }
    }

    private fun fallbackModelList(): List<AiModel> = fallbackModels.map { name ->
        AiModel(
            providerId = id,
            id = name,
            displayName = name,
            capabilities = setOf(AiCapability.MUSIC_GEN),
        )
    }

    private fun authHeaders(apiKey: String): Map<String, String> =
        mapOf("Authorization" to "Bearer $apiKey")

    private fun ensureApiOk(root: JSONObject) {
        val code = root.optInt("code", 200)
        val error = if (root.isNull("error")) "" else root.optString("error", "")
        if (code !in 200..299 || error.isNotBlank()) {
            throw RuntimeException("${id.displayName}: ${error.ifBlank { "API code $code" }}")
        }
    }

    private fun statusLabel(status: Int): String = when (status) {
        0 -> "running"
        1 -> "succeeded"
        2 -> "failed"
        else -> "status:$status"
    }

    private fun extensionFor(format: String, fileUrl: String): String {
        val cleanFormat = format.lowercase().trim().takeIf { it in audioExtensions }
        if (cleanFormat != null) return cleanFormat.fileExtension()
        val fromUrl = fileUrl.substringBefore('?').substringAfterLast('.', "")
            .lowercase()
            .takeIf { it in audioExtensions }
        return fromUrl?.fileExtension() ?: "mp3"
    }

    private val audioExtensions = setOf("mp3", "wav", "wav32", "flac", "opus", "aac")

    private fun String.fileExtension(): String =
        if (this == "wav32") "wav" else this
}
