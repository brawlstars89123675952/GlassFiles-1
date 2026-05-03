package com.glassfiles.data.ai.providers

import android.content.Context
import android.util.Base64
import com.glassfiles.data.ai.providers.acemusic.AceMusicAudioConfig
import com.glassfiles.data.ai.providers.acemusic.AceMusicChatMessage
import com.glassfiles.data.ai.providers.acemusic.AceMusicCompletionRequest
import com.glassfiles.data.ai.providers.acemusic.AceMusicNetwork
import com.glassfiles.data.ai.providers.acemusic.AceMusicRepository
import com.google.gson.Gson
import com.glassfiles.data.ai.models.AiCapability
import com.glassfiles.data.ai.models.AiModel
import com.glassfiles.data.ai.models.AiProviderId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
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

    private const val DEFAULT_BASE_URL = "https://api.acemusic.ai"
    private const val CLOUD_MODEL_ALIAS = "ACE Steps"
    private const val CLOUD_MODEL_ALIAS_SINGULAR = "ACE Step"
    private val gson = Gson()
    private val fallbackModels = listOf(
        "acestep/acestep-v15-turbo",
        "acestep/acestep-v15-turbo-shift3",
    )
    private val fallbackModelNames = mapOf(
        "acestep/acestep-v15-turbo" to "ACE-Step v1.5 Turbo",
        "acestep/acestep-v15-turbo-shift3" to "ACE-Step v1.5 Turbo Shift3",
    )

    override suspend fun listModels(context: Context, apiKey: String): List<AiModel> = withContext(Dispatchers.IO) {
        runCatching {
            val auth = parseAuth(apiKey)
            val raw = repository(auth).listModelsRawOrThrow()
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
        val auth = parseAuth(apiKey)
        records.mapIndexed { index, payload ->
            val ext = extensionForPayload(payload, request.audioFormat)
            val target = File(outDir, "acemusic_${System.currentTimeMillis()}_$index.$ext")
            saveAudioPayload(payload, auth, target)
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

    private suspend fun createMusicCompletion(
        modelId: String,
        request: AiMusicRequest,
        apiKey: String,
        onProgress: (String) -> Unit,
    ): JSONObject {
        val auth = parseAuth(apiKey)
        val repo = repository(auth)
        val attempts = completionAttempts(modelId)
        var lastRetryable: Throwable? = null
        var lastAttempt: CompletionAttempt? = null

        attempts.forEachIndexed { index, attempt ->
            if (index > 0) onProgress("retry:${attempt.mode.statusName}")
            try {
                return postCompletion(attempt.modelId, request, auth, repo, attempt.mode, onProgress)
            } catch (e: Exception) {
                if (!e.isRetryableCloudError()) throw e
                lastRetryable = e
                lastAttempt = attempt
            }
        }

        val attemptLabel = lastAttempt?.let { "${it.modelId}/${it.mode.statusName}" } ?: "unknown"
        throw RuntimeException(
            "${id.displayName}: generation failed after ${attempts.size} ACEMusic payload variants. " +
                "Last attempt=$attemptLabel. Last error: ${lastRetryable?.message ?: "unknown error"}",
            lastRetryable,
        )
    }

    private suspend fun postCompletion(
        modelId: String,
        request: AiMusicRequest,
        auth: AceMusicAuth,
        repo: AceMusicRepository,
        mode: CompletionPayloadMode,
        onProgress: (String) -> Unit,
    ): JSONObject {
        val body = completionRequest(modelId, request, auth, mode)
        onProgress("generating:${mode.statusName}")
        val response = repo.createCompletionOrThrow(body)
        return JSONObject(gson.toJson(response)).also { ensureCompletionOk(it) }
    }

    private fun downloadAudio(fileValue: String, auth: AceMusicAuth, target: File) {
        val url = when {
            fileValue.startsWith("http://") || fileValue.startsWith("https://") -> fileValue
            fileValue.startsWith("/v1/audio") -> "${auth.baseUrl}$fileValue"
            fileValue.startsWith("/") -> "${auth.baseUrl}/v1/audio?path=${URLEncoder.encode(fileValue, "UTF-8")}"
            else -> "${auth.baseUrl}/v1/audio?path=${URLEncoder.encode(fileValue, "UTF-8")}"
        }
        val client = AceMusicNetwork.createOkHttpClient(apiKeyProvider = { auth.apiKey })
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val raw = response.body?.string()?.take(800) ?: "error ${response.code}"
                throw RuntimeException("${id.displayName} HTTP ${response.code}: $raw")
            }
            val body = response.body ?: throw RuntimeException("${id.displayName}: empty audio response")
            body.byteStream().use { input -> target.outputStream().use { input.copyTo(it) } }
        }
    }

    private fun saveAudioPayload(payload: AudioPayload, auth: AceMusicAuth, target: File) {
        if (!payload.inline) {
            downloadAudio(payload.value, auth, target)
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
            val rawId = when (item) {
                is JSONObject -> firstPresentString(item, "id", "model", "name")
                is String -> item
                else -> ""
            }
            val modelId = normalizeModelId(rawId)
            if (modelId.isBlank()) return@mapNotNull null
            val label = (item as? JSONObject)?.let { firstPresentString(it, "name", "display_name", "label") }.orEmpty()
            val isDefault = item is JSONObject && item.optBoolean("is_default", false) || modelId == normalizeModelId(defaultModel)
            AiModel(
                providerId = id,
                id = modelId,
                displayName = displayNameForModel(modelId, label, isDefault),
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
            displayName = displayNameForModel(name, fallbackModelNames[name].orEmpty(), isDefault = name == fallbackModels.first()),
            capabilities = setOf(AiCapability.MUSIC_GEN),
        )
    }

    private fun completionModelId(modelId: String): String {
        val clean = normalizeModelId(modelId).ifBlank { fallbackModels.first() }
        return if ('/' in clean || clean.any(Char::isWhitespace)) clean else "acestep/$clean"
    }

    private fun normalizeModelId(raw: String): String {
        return raw.trim()
    }

    private fun displayNameForModel(modelId: String, label: String, isDefault: Boolean): String {
        val shortId = modelId.substringAfterLast('/')
        val cleanLabel = label.trim()
        val base = when {
            cleanLabel.isBlank() -> fallbackModelNames[modelId] ?: shortId
            cleanLabel == modelId || cleanLabel == shortId -> shortId
            shortId in cleanLabel -> cleanLabel
            else -> "$cleanLabel · $shortId"
        }
        return if (isDefault && "default" !in base.lowercase()) "$base · default" else base
    }

    private fun completionMessage(request: AiMusicRequest): String {
        if (request.sampleMode) return request.sampleQuery.ifBlank { request.prompt }
        if (request.lyrics.isBlank()) return "<prompt>${request.prompt}</prompt>"
        return buildString {
            if (request.prompt.isNotBlank()) append("<prompt>${request.prompt}</prompt>")
            if (isNotEmpty()) append("\n")
            append("<lyrics>${request.lyrics}</lyrics>")
        }
    }

    private fun completionRequest(
        modelId: String,
        request: AiMusicRequest,
        auth: AceMusicAuth,
        mode: CompletionPayloadMode,
    ): AceMusicCompletionRequest {
        val full = mode == CompletionPayloadMode.FULL
        val chatOnly = mode == CompletionPayloadMode.CHAT_ONLY
        val audioConfig = if (chatOnly) {
            null
        } else {
            AceMusicAudioConfig(
                durationSec = request.durationSec?.takeIf { full && it > 0f }?.coerceIn(10f, 600f),
                format = request.audioFormat,
                bpm = request.bpm?.takeIf { full && it > 0 }?.coerceIn(30, 300),
                keyScale = request.keyScale.takeIf { full && it.isNotBlank() },
                timeSignature = request.timeSignature.toAceTimeSignature().takeIf { full && it.isNotBlank() },
                vocalLanguage = request.vocalLanguage,
                instrumental = true.takeIf { full && !request.sampleMode && request.lyrics.isBlank() },
            )
        }

        return AceMusicCompletionRequest(
            model = modelId,
            messages = listOf(AceMusicChatMessage(role = "user", content = completionMessage(request))),
            stream = false,
            modalities = listOf("audio", "text").takeIf { !chatOnly },
            audioConfig = audioConfig,
            taskType = "text2music".takeIf { !chatOnly },
            sampleMode = request.sampleMode.takeIf { !chatOnly },
            useFormat = (if (full) request.useFormat else false).takeIf { !chatOnly },
            useCotCaption = (if (full) request.useCotCaption else false).takeIf { !chatOnly },
            useCotLanguage = (if (full) request.useCotLanguage else false).takeIf { !chatOnly },
            thinking = (if (full) request.thinking else false).takeIf { !chatOnly },
            lyrics = request.lyrics.takeIf { !chatOnly && !request.sampleMode && it.isNotBlank() },
            guidanceScale = request.guidanceScale.takeIf { full && it != 7f },
            batchSize = request.batchSize.takeIf { full && it > 1 }?.coerceIn(2, 8),
            seed = request.seed?.takeIf { full && !request.useRandomSeed },
            aiToken = auth.apiKey.takeIf { mode == CompletionPayloadMode.TOKEN_BODY },
        )
    }

    private fun repository(auth: AceMusicAuth): AceMusicRepository =
        AceMusicRepository(
            AceMusicNetwork.createApi(
                baseUrl = auth.baseUrl,
                apiKeyProvider = { auth.apiKey },
            ),
        )

    private fun completionAttempts(modelId: String): List<CompletionAttempt> {
        val models = buildList {
            val primary = completionModelId(modelId)
            add(primary)
            if (primary.equals(CLOUD_MODEL_ALIAS, ignoreCase = true)) add(CLOUD_MODEL_ALIAS_SINGULAR)
            if (primary.equals(CLOUD_MODEL_ALIAS_SINGULAR, ignoreCase = true)) add(CLOUD_MODEL_ALIAS)
            fallbackModels.forEach(::add)
        }.distinct()
        val attempts = mutableListOf<CompletionAttempt>()
        models.forEachIndexed { index, candidate ->
            if (index == 0) {
                attempts += CompletionAttempt(candidate, CompletionPayloadMode.FULL)
                attempts += CompletionAttempt(candidate, CompletionPayloadMode.COMPACT)
                attempts += CompletionAttempt(candidate, CompletionPayloadMode.TOKEN_BODY)
                attempts += CompletionAttempt(candidate, CompletionPayloadMode.CHAT_ONLY)
            } else {
                attempts += CompletionAttempt(candidate, CompletionPayloadMode.COMPACT)
                attempts += CompletionAttempt(candidate, CompletionPayloadMode.TOKEN_BODY)
                attempts += CompletionAttempt(candidate, CompletionPayloadMode.CHAT_ONLY)
            }
        }
        return attempts
    }

    private fun parseAuth(raw: String): AceMusicAuth {
        val clean = raw.trim()
        var key = clean
        var baseUrl = DEFAULT_BASE_URL

        fun acceptBase(value: String) {
            val v = value.unquote().trim().trimEnd('/')
            if (v.startsWith("http://") || v.startsWith("https://")) baseUrl = v
        }

        fun acceptKey(value: String) {
            val v = value.unquote().trim()
            if (v.isNotBlank()) key = v
        }

        runCatching {
            if (clean.startsWith("{")) {
                val obj = JSONTokener(clean).nextValue() as? JSONObject ?: return@runCatching
                firstPresentString(obj, "base_url", "baseUrl", "api_url", "apiUrl", "url").takeIf { it.isNotBlank() }?.let(::acceptBase)
                firstPresentString(obj, "api_key", "apiKey", "key", "token", "ai_token", "aiToken").takeIf { it.isNotBlank() }?.let(::acceptKey)
            }
        }

        clean.lines().forEach { line ->
            val normalized = line.trim()
            val separator = when {
                "=" in normalized -> "="
                ":" in normalized -> ":"
                else -> return@forEach
            }
            val name = normalized.substringBefore(separator).trim().lowercase().replace("-", "_").replace(" ", "_")
            val value = normalized.substringAfter(separator).trim()
            when (name) {
                "base_url", "baseurl", "api_url", "apiurl", "url" -> acceptBase(value)
                "api_key", "apikey", "key", "token", "ai_token", "aitoken" -> acceptKey(value)
            }
        }

        val parts = clean.split('|', limit = 2).map { it.trim() }
        if (parts.size == 2) {
            if (parts[0].startsWith("http://") || parts[0].startsWith("https://")) {
                acceptBase(parts[0])
                acceptKey(parts[1])
            } else if (parts[1].startsWith("http://") || parts[1].startsWith("https://")) {
                acceptKey(parts[0])
                acceptBase(parts[1])
            }
        }

        return AceMusicAuth(apiKey = key, baseUrl = baseUrl)
    }

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

    private fun Throwable.isRetryableCloudError(): Boolean {
        val text = message.orEmpty().lowercase()
        return "http 500" in text || "http 502" in text || "http 503" in text || "internal server" in text
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

    private fun String.toAceTimeSignature(): String = when (trim()) {
        "", "4" -> ""
        "2" -> "2/4"
        "3" -> "3/4"
        "6" -> "6/8"
        else -> trim()
    }

    private fun String.unquote(): String =
        trim().removeSurrounding("\"").removeSurrounding("'")

    private data class AceMusicAuth(
        val apiKey: String,
        val baseUrl: String,
    )

    private data class CompletionAttempt(
        val modelId: String,
        val mode: CompletionPayloadMode,
    )

    private enum class CompletionPayloadMode(val statusName: String) {
        FULL("full"),
        COMPACT("compact"),
        CHAT_ONLY("chat"),
        TOKEN_BODY("token"),
    }

    private data class AudioPayload(
        val value: String,
        val inline: Boolean,
        val format: String,
    )
}
