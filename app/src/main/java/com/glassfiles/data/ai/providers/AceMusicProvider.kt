package com.glassfiles.data.ai.providers

import android.content.Context
import android.util.Base64
import com.glassfiles.data.ai.models.AiCapability
import com.glassfiles.data.ai.models.AiModel
import com.glassfiles.data.ai.models.AiProviderId
import kotlinx.coroutines.Dispatchers
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
 * the public hosted service uses the OpenAI-compatible
 * `/v1/chat/completions` endpoint and returns audio inline.
 */
object AceMusicProvider : AiProvider {
    override val id: AiProviderId = AiProviderId.ACEMUSIC

    private const val BASE_URL = "https://api.acemusic.ai"
    private val fallbackModels = listOf(
        "acemusic/acestep-v15-turbo",
        "acemusic/acestep-v15-turbo-shift3",
    )

    override suspend fun listModels(context: Context, apiKey: String): List<AiModel> = withContext(Dispatchers.IO) {
        runCatching {
            val conn = Http.open("$BASE_URL/v1/models", headers = authHeaders(apiKey))
            Http.ensureOk(conn, id.displayName)
            val raw = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            parseModels(raw).ifEmpty { fallbackModelList() }
        }.getOrElse {
            fallbackModelList()
        }
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
        val completion = createMusicCompletion(modelId, request, apiKey, onProgress)
        val taskId = completion.optString("id", "acemusic-${System.currentTimeMillis()}")
        val records = parseCompletionAudio(completion, request)
        val outDir = File(context.cacheDir, "ai_music").apply { mkdirs() }
        records.mapIndexed { index, payload ->
            val ext = extensionForPayload(payload, request.audioFormat)
            val target = File(outDir, "acemusic_${System.currentTimeMillis()}_$index.$ext")
            saveAudioPayload(payload, apiKey, target)
            AiMusicResult(
                filePath = target.absolutePath,
                prompt = request.prompt.ifBlank { request.sampleQuery },
                lyrics = request.lyrics,
                bpm = request.bpm,
                keyScale = request.keyScale,
                timeSignature = request.timeSignature,
                durationSec = request.durationSec,
                seed = request.seed?.toString().orEmpty(),
                taskId = taskId,
            )
        }.ifEmpty {
            throw RuntimeException("${id.displayName}: no audio files in result")
        }
    }

    private fun createMusicCompletion(
        modelId: String,
        request: AiMusicRequest,
        apiKey: String,
        onProgress: (String) -> Unit,
    ): JSONObject {
        val body = JSONObject().apply {
            put("model", completionModelId(modelId))
            put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", completionPrompt(request))))
            put("stream", false)
            put("thinking", request.thinking)
            put("use_format", request.useFormat)
            put("sample_mode", request.sampleMode)
            put("use_cot_caption", request.useCotCaption)
            put("use_cot_language", request.useCotLanguage)
            put(
                "audio_config",
                JSONObject()
                    .put("format", request.audioFormat)
                    .put("vocal_language", request.vocalLanguage),
            )
            put("guidance_scale", request.guidanceScale)
            put("use_random_seed", request.useRandomSeed)
            put("batch_size", request.batchSize.coerceIn(1, 8))
            request.durationSec?.takeIf { it > 0f }?.let { put("audio_duration", it.coerceIn(10f, 600f)) }
            request.bpm?.takeIf { it > 0 }?.let { put("bpm", it.coerceIn(30, 300)) }
            if (request.keyScale.isNotBlank()) put("key_scale", request.keyScale)
            if (request.timeSignature.isNotBlank()) put("time_signature", request.timeSignature)
            if (!request.useRandomSeed) put("seed", request.seed ?: -1)
        }.toString()
        val conn = Http.postJson("$BASE_URL/v1/chat/completions", body, authHeaders(apiKey)).apply {
            readTimeout = 11 * 60_000
        }
        onProgress("generating")
        Http.ensureOk(conn, id.displayName)
        val raw = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()
        return JSONObject(raw).also { ensureCompletionOk(it) }
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

    private fun saveAudioPayload(payload: AudioPayload, apiKey: String, target: File) {
        if (!payload.inline) {
            downloadAudio(payload.value, apiKey, target)
            return
        }
        val raw = payload.value.trim().substringAfter("base64,", payload.value.trim())
        val bytes = Base64.decode(raw, Base64.DEFAULT)
        target.outputStream().use { it.write(bytes) }
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
            is JSONObject -> recordsFromResultObject(value)
            is String -> {
                if (value.isBlank()) JSONArray() else runCatching {
                    when (val token = JSONTokener(value).nextValue()) {
                        is JSONArray -> token
                        is JSONObject -> recordsFromResultObject(token)
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

    private fun recordsFromResultObject(result: JSONObject): JSONArray {
        val audioPaths = result.optJSONArray("audio_paths")
        if (audioPaths != null && audioPaths.length() > 0) {
            val metas = result.optJSONObject("metas") ?: JSONObject().apply {
                put("bpm", result.optInt("bpm", 0))
                put("duration", result.optDouble("duration", 0.0))
                put("keyscale", result.optString("keyscale", ""))
                put("timesignature", result.optString("timesignature", ""))
            }
            return JSONArray().apply {
                for (i in 0 until audioPaths.length()) {
                    val file = audioPaths.optString(i, "")
                    if (file.isNotBlank()) {
                        put(
                            JSONObject()
                                .put("file", file)
                                .put("prompt", metas.optString("caption", ""))
                                .put("metas", metas)
                                .put("seed_value", result.optString("seed_value", "")),
                        )
                    }
                }
            }
        }
        val first = result.optString("first_audio_path", "")
        if (first.isNotBlank()) {
            return JSONArray().put(JSONObject().put("file", first).put("metas", result.optJSONObject("metas") ?: JSONObject()))
        }
        return JSONArray().put(result)
    }

    private fun parseCompletionAudio(root: JSONObject, request: AiMusicRequest): List<AudioPayload> {
        val payloads = mutableListOf<AudioPayload>()
        val choices = root.optJSONArray("choices") ?: JSONArray()
        for (i in 0 until choices.length()) {
            val message = choices.optJSONObject(i)?.optJSONObject("message") ?: continue
            collectAudioPayloads(message.opt("audio"), payloads)
            collectAudioPayloads(message.opt("audio_url"), payloads)
            collectAudioPayloads(message.opt("audios"), payloads)
        }
        collectAudioPayloads(root.opt("audio"), payloads)
        collectAudioPayloads(root.opt("audio_url"), payloads)
        return payloads.ifEmpty {
            parseResultRecords(root.opt("result")).mapNotNull { record ->
                val file = record.optString("file", record.optString("audio_url", ""))
                file.takeIf { it.isNotBlank() }?.let { AudioPayload(value = it, inline = false, format = request.audioFormat) }
            }
        }
    }

    private fun collectAudioPayloads(value: Any?, target: MutableList<AudioPayload>) {
        when (value) {
            is JSONArray -> for (i in 0 until value.length()) collectAudioPayloads(value.opt(i), target)
            is JSONObject -> {
                collectAudioPayloads(value.opt("audio_url"), target)
                val direct = firstPresentString(value, "url", "data", "b64_json", "base64", "content")
                if (direct.isNotBlank()) {
                    target += AudioPayload(
                        value = direct,
                        inline = !direct.startsWith("http://") && !direct.startsWith("https://"),
                        format = firstPresentString(value, "format", "mime_type"),
                    )
                }
            }
            is String -> {
                if (value.isNotBlank()) {
                    target += AudioPayload(
                        value = value,
                        inline = !value.startsWith("http://") && !value.startsWith("https://"),
                        format = "",
                    )
                }
            }
        }
    }

    private fun firstPresentString(obj: JSONObject, vararg keys: String): String {
        keys.forEach { key ->
            if (!obj.isNull(key)) {
                val value = obj.optString(key, "")
                if (value.isNotBlank()) return value
            }
        }
        return ""
    }

    private fun fallbackModelList(): List<AiModel> = fallbackModels.map { name ->
        AiModel(
            providerId = id,
            id = name,
            displayName = name,
            capabilities = setOf(AiCapability.MUSIC_GEN),
        )
    }

    private fun completionModelId(modelId: String): String {
        val clean = modelId.trim().ifBlank { fallbackModels.first() }
        return if ('/' in clean) clean else "acemusic/$clean"
    }

    private fun completionPrompt(request: AiMusicRequest): String {
        if (request.sampleMode) return request.sampleQuery.ifBlank { request.prompt }
        return buildString {
            if (request.prompt.isNotBlank()) append(request.prompt)
            if (request.lyrics.isNotBlank()) {
                if (isNotEmpty()) append("\n\n")
                append("Lyrics:\n")
                append(request.lyrics)
            }
        }
    }

    private fun authHeaders(apiKey: String): Map<String, String> =
        mapOf("Authorization" to "Bearer ${apiKey.trim()}")

    private fun ensureApiOk(root: JSONObject) {
        val hasCode = root.has("code") && !root.isNull("code")
        val code = root.optInt("code", 200)
        val error = if (root.isNull("error")) "" else root.optString("error", "")
        if ((hasCode && code != 0 && code !in 200..299) || error.isNotBlank()) {
            throw RuntimeException("${id.displayName}: ${error.ifBlank { "API code $code" }}")
        }
    }

    private fun ensureCompletionOk(root: JSONObject) {
        if (root.has("error") && !root.isNull("error")) {
            val error = root.opt("error")
            val message = if (error is JSONObject) error.optString("message", error.toString()) else error.toString()
            throw RuntimeException("${id.displayName}: $message")
        }
        val choices = root.optJSONArray("choices") ?: return
        val first = choices.optJSONObject(0) ?: return
        if (first.optString("finish_reason") == "error") {
            val message = first.optJSONObject("message")?.optString("content", "").orEmpty()
            throw RuntimeException("${id.displayName}: ${message.ifBlank { "generation failed" }}")
        }
    }

    private fun extensionFor(format: String, fileUrl: String): String {
        val cleanFormat = format.lowercase().trim().takeIf { it in audioExtensions }
        if (cleanFormat != null) return cleanFormat.fileExtension()
        val fromUrl = fileUrl.substringBefore('?').substringAfterLast('.', "")
            .lowercase()
            .takeIf { it in audioExtensions }
        return fromUrl?.fileExtension() ?: "mp3"
    }

    private fun extensionForPayload(payload: AudioPayload, requestedFormat: String): String {
        val fromFormat = payload.format.lowercase()
            .substringAfter("audio/", payload.format.lowercase())
            .substringBefore(";")
            .takeIf { it.isNotBlank() }
            ?.normalizeAudioExtension()
        if (fromFormat != null) return fromFormat
        if (payload.value.startsWith("data:audio/")) {
            val fromDataUrl = payload.value.substringAfter("data:audio/")
                .substringBefore(";")
                .normalizeAudioExtension()
            if (fromDataUrl in audioExtensions.map { it.fileExtension() }) return fromDataUrl
        }
        return extensionFor(requestedFormat, payload.value)
    }

    private val audioExtensions = setOf("mp3", "wav", "wav32", "flac", "opus", "aac")

    private fun String.fileExtension(): String =
        if (this == "wav32") "wav" else this

    private fun String.normalizeAudioExtension(): String = when (lowercase()) {
        "mpeg", "mpg", "mpga" -> "mp3"
        "x-wav", "wave" -> "wav"
        "x-flac" -> "flac"
        else -> fileExtension()
    }

    private data class AudioPayload(
        val value: String,
        val inline: Boolean,
        val format: String,
    )
}
