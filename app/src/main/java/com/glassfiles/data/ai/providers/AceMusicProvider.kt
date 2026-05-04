package com.glassfiles.data.ai.providers

import android.content.Context
import android.util.Base64
import com.glassfiles.data.ai.models.AiCapability
import com.glassfiles.data.ai.models.AiModel
import com.glassfiles.data.ai.models.AiProviderId
import com.glassfiles.data.ai.providers.acemusic.AceMusicAuthMode
import com.glassfiles.data.ai.providers.acemusic.AceMusicHttpDebugException
import com.glassfiles.data.ai.providers.acemusic.AceMusicNetwork
import com.glassfiles.data.ai.providers.acemusic.AceMusicModelData
import com.glassfiles.data.ai.providers.acemusic.AceMusicRepository
import com.glassfiles.data.ai.providers.acemusic.AceMusicTaskRecord
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.net.URLEncoder
import java.util.UUID

/**
 * ACEMusic web-engine flow.
 *
 * Music generation is a separate flow from chat/image/video.
 * The current ACEMusic engine endpoint is form-urlencoded:
 * `acem-api /user/ai/token` returns data.token, engine `/release_task`
 * starts a task, and engine `/query_result` is polled until audio is returned.
 */
object AceMusicProvider : AiProvider {
    override val id: AiProviderId = AiProviderId.ACEMUSIC

    private const val DEFAULT_BASE_URL = "https://ai-api.acemusic.ai/engine/api"
    private const val POLL_INTERVAL_MS = 3_000L
    private const val GENERATION_TIMEOUT_MS = 11 * 60_000L
    private val fallbackModels = listOf(
        "acestep-v15-turbo",
        "acestep-v15-turbo-shift3",
    )
    private val fallbackModelNames = mapOf(
        "acestep-v15-turbo" to "ACE-Step v1.5 Turbo",
        "acestep-v15-turbo-shift3" to "ACE-Step v1.5 Turbo Shift3",
    )

    override suspend fun listModels(context: Context, apiKey: String): List<AiModel> = withContext(Dispatchers.IO) {
        runCatching {
            val auth = parseAuth(apiKey)
            val data = repository(auth).listModelsOrThrow()
            parseModels(data).ifEmpty { fallbackModelList() }
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
        onProgress("token")
        val taskResult = createMusicTaskResult(apiKey, modelId, request, onProgress)
        val taskId = taskResult.optString("id", "acemusic-${System.currentTimeMillis()}")
        val records = parseTaskAudio(taskResult, request)
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

    private suspend fun createMusicTaskResult(
        apiKey: String,
        modelId: String,
        request: AiMusicRequest,
        onProgress: (String) -> Unit,
    ): JSONObject {
        val auth = parseAuth(apiKey)
        val repo = repository(auth)
        val taskId = UUID.randomUUID().toString()
        val aiToken = auth.aiToken.ifBlank { repo.fetchAiTokenOrThrow() }
        val generationFields = releaseGenerationFields(modelId, request)
        val formDebug = buildString {
            append("ai_token=<hidden>&task_id_list=${JSONArray(listOf(taskId))}&app=studio-web")
            generationFields.forEach { (key, value) ->
                append('&')
                append(key)
                append('=')
                append(value.take(160))
            }
        }
        onProgress("release_task")
        val release = try {
            repo.releaseTaskOrThrow(aiToken, listOf(taskId), generationFields)
        } catch (e: AceMusicHttpDebugException) {
            throw RuntimeException(
                buildString {
                    append(e.message ?: "${id.displayName} HTTP ${e.statusCode}")
                    if (e.statusCode == 404 && auth.baseUrl == DEFAULT_BASE_URL) {
                        append("\n${id.displayName}: ${e.label} endpoint returned 404 for the default ACEMusic base URL.")
                    }
                    append("\nendpoint=/release_task")
                    append("\nbase_url=")
                    append(auth.baseUrl)
                    append("\ncontent_type=application/x-www-form-urlencoded")
                    append("\nform=")
                    append(formDebug.take(4_000))
                },
                e,
            )
        }
        val releasedTaskId = release.ifBlank { taskId }
        if (releasedTaskId.isBlank()) throw RuntimeException("${id.displayName}: release_task returned empty task_id")
        onProgress("queued")
        return pollTaskResult(repo, aiToken, releasedTaskId, onProgress)
    }

    private fun releaseGenerationFields(modelId: String, request: AiMusicRequest): Map<String, String> {
        val fields = linkedMapOf<String, String>()
        val promptText = request.prompt.ifBlank { request.sampleQuery }.trim()

        fun putString(key: String, value: String) {
            val clean = value.trim()
            if (clean.isNotBlank()) fields[key] = clean
        }

        fun putNumber(key: String, value: Number?) {
            if (value != null) fields[key] = value.toString()
        }

        fun putBool(key: String, value: Boolean) {
            fields[key] = value.toString()
        }

        putString("model", modelId)
        putString("model_name", modelId)
        putString("task_type", "text2music")
        putString("prompt", promptText)
        putString("caption", promptText)
        putString("lyrics", request.lyrics)
        if (request.sampleMode) {
            putString("sample_query", promptText)
            putString("description", promptText)
        }
        putBool("use_format", request.useFormat)
        putBool("useFormat", request.useFormat)
        putBool("thinking", request.thinking)
        putString("vocal_language", request.vocalLanguage)
        putString("language", request.vocalLanguage)
        putString("format", request.audioFormat)
        putNumber("audio_duration", request.durationSec)
        putNumber("duration", request.durationSec)
        putNumber("bpm", request.bpm)
        putString("key_scale", request.keyScale)
        putString("keyscale", request.keyScale)
        putString("time_signature", request.timeSignature)
        putString("timesignature", request.timeSignature)
        putNumber("inference_steps", request.inferenceSteps)
        putNumber("guidance_scale", request.guidanceScale)
        putBool("use_random_seed", request.useRandomSeed)
        if (!request.useRandomSeed) putNumber("seed", request.seed)
        putNumber("batch_size", request.batchSize)
        putNumber("shift", request.shift)
        putString("infer_method", request.inferMethod)
        putString("timesteps", request.timesteps)
        putBool("use_adg", request.useAdg)
        putNumber("cfg_interval_start", request.cfgIntervalStart)
        putNumber("cfg_interval_end", request.cfgIntervalEnd)
        putBool("use_cot_caption", request.useCotCaption)
        putBool("use_cot_language", request.useCotLanguage)
        putBool("constrained_decoding", request.constrainedDecoding)
        putBool("allow_lm_batch", request.allowLmBatch)
        putNumber("lm_temperature", request.lmTemperature)
        putNumber("lm_cfg_scale", request.lmCfgScale)
        putString("lm_negative_prompt", request.lmNegativePrompt)
        putNumber("lm_top_k", request.lmTopK)
        putNumber("lm_top_p", request.lmTopP)
        putNumber("lm_repetition_penalty", request.lmRepetitionPenalty)

        return fields
    }

    private suspend fun pollTaskResult(
        repo: AceMusicRepository,
        aiToken: String,
        taskId: String,
        onProgress: (String) -> Unit,
    ): JSONObject {
        val deadline = System.currentTimeMillis() + GENERATION_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            delay(POLL_INTERVAL_MS)
            val records = repo.queryResultOrThrow(aiToken, listOf(taskId))
            val record = records.firstOrNull { it.taskId == taskId } ?: records.firstOrNull()
            if (record == null) {
                onProgress("poll:empty")
                continue
            }
            val audioUrl = record.audioUrl.orEmpty()
            when {
                audioUrl.isNotBlank() -> return taskRecordToJson(taskId, record)
                record.status == 1 -> return taskRecordToJson(taskId, record)
                record.status == 2 -> throw RuntimeException("${id.displayName}: task failed: ${taskErrorText(record)}")
                else -> onProgress("poll:${record.status ?: 0}")
            }
        }
        throw RuntimeException("${id.displayName}: generation timed out for task $taskId")
    }

    private fun taskRecordToJson(taskId: String, record: AceMusicTaskRecord): JSONObject =
        JSONObject()
            .put("id", taskId)
            .put("audio_url", record.audioUrl.orEmpty())
            .put("result", record.result.orEmpty().ifBlank { record.audioUrl.orEmpty() })

    private fun taskErrorText(record: AceMusicTaskRecord): String =
        record.error?.takeIf { !it.isJsonNull }?.toString().orEmpty()
            .ifBlank { record.result.orEmpty() }
            .ifBlank { "unknown error" }

    private fun downloadAudio(fileValue: String, auth: AceMusicAuth, target: File) {
        val url = when {
            fileValue.startsWith("http://") || fileValue.startsWith("https://") -> fileValue
            fileValue.startsWith("/v1/audio") -> "${auth.baseUrl}$fileValue"
            fileValue.startsWith("/") -> "${auth.baseUrl}/v1/audio?path=${URLEncoder.encode(fileValue, "UTF-8")}"
            else -> "${auth.baseUrl}/v1/audio?path=${URLEncoder.encode(fileValue, "UTF-8")}"
        }
        val client = AceMusicNetwork.createOkHttpClient(
            apiKeyProvider = { auth.apiKey },
            authMode = auth.authMode,
            extraHeadersProvider = { auth.extraHeaders },
        )
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

    private fun parseModels(data: AceMusicModelData): List<AiModel> {
        val defaultModel = data.defaultModel.orEmpty()
        return data.models.orEmpty().mapNotNull { item ->
            val modelId = item.id.orEmpty().ifBlank { item.model.orEmpty() }.ifBlank { item.name.orEmpty() }.trim()
            if (modelId.isBlank()) return@mapNotNull null
            val label = item.name.orEmpty().takeIf { it.isNotBlank() && it != modelId }.orEmpty()
            val isDefault = item.isDefault == true ||
                defaultModel.isNotBlank() && modelId.equals(defaultModel, ignoreCase = true)
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

    private fun parseTaskAudio(root: JSONObject, request: AiMusicRequest): List<AudioPayload> {
        val payloads = mutableListOf<AudioPayload>()
        collectAudioPayloads(root.opt("audio"), payloads)
        collectAudioPayloads(root.opt("audio_url"), payloads)
        collectAudioPayloads(root.opt("file"), payloads)
        collectAudioPayloads(root.opt("url"), payloads)
        collectAudioPayloads(root.opt("result"), payloads)
        return payloads.ifEmpty {
            parseResultRecords(root.opt("result")).mapNotNull { record ->
                val file = firstPresentString(record, "file", "audio_url", "url", "path")
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
                        inline = isInlineAudioValue(direct),
                        format = firstPresentString(value, "format", "mime_type"),
                    )
                }
            }
            is String -> {
                val clean = value.trim()
                if (clean.startsWith("{") || clean.startsWith("[")) {
                    runCatching { JSONTokener(clean).nextValue() }
                        .onSuccess { collectAudioPayloads(it, target) }
                        .onFailure {
                            target += AudioPayload(value = value, inline = isInlineAudioValue(value), format = "")
                        }
                } else if (value.isNotBlank()) {
                    target += AudioPayload(
                        value = value,
                        inline = isInlineAudioValue(value),
                        format = "",
                    )
                }
            }
        }
    }

    private fun isInlineAudioValue(value: String): Boolean {
        val clean = value.trim()
        if (clean.startsWith("data:audio/", ignoreCase = true)) return true
        if (clean.startsWith("http://", ignoreCase = true) || clean.startsWith("https://", ignoreCase = true)) return false
        if (clean.startsWith("/") || "?" in clean || "." in clean.substringBefore('?')) return false
        return clean.length > 256 && clean.all { it.isLetterOrDigit() || it in "+/=\r\n" }
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

    private fun repository(auth: AceMusicAuth): AceMusicRepository =
        AceMusicRepository(
            AceMusicNetwork.createApi(
                baseUrl = auth.baseUrl,
                apiKeyProvider = { auth.apiKey },
                authMode = auth.authMode,
                extraHeadersProvider = { auth.extraHeaders },
            ),
        )

    private fun parseAuth(raw: String): AceMusicAuth {
        val clean = raw.trim()
        var key = clean
        var aiToken = ""
        var baseUrl = DEFAULT_BASE_URL
        var authMode = AceMusicAuthMode.BEARER
        val extraHeaders = linkedMapOf<String, String>()
        var sawStructuredConfig = false
        var keyWasSet = false

        fun acceptBase(value: String) {
            sawStructuredConfig = true
            val v = value.unquote().trim().trimEnd('/')
            if (v.startsWith("http://") || v.startsWith("https://")) baseUrl = v
        }

        fun acceptKey(value: String) {
            sawStructuredConfig = true
            val v = value.unquote().trim()
            if (v.isNotBlank()) {
                key = v.removePrefix("Bearer ").removePrefix("bearer ").trim()
                keyWasSet = true
            }
        }

        fun acceptAiToken(value: String) {
            sawStructuredConfig = true
            val v = value.unquote().trim()
            if (v.isNotBlank()) aiToken = v.removePrefix("Bearer ").removePrefix("bearer ").trim()
        }

        fun acceptAuthMode(value: String) {
            sawStructuredConfig = true
            val v = value.unquote().trim().lowercase().replace("-", "_")
            authMode = when (v) {
                "x_api_key", "x-api-key", "xapikey", "api_key", "apikey" -> AceMusicAuthMode.X_API_KEY
                else -> AceMusicAuthMode.BEARER
            }
        }

        fun acceptHeader(name: String, value: String) {
            val headerName = name.trim().split('-', '_', ' ')
                .filter { it.isNotBlank() }
                .joinToString("-") { part ->
                    part.lowercase().replaceFirstChar { ch -> ch.uppercase() }
                }
            val headerValue = value.unquote().trim()
            if (headerName.isBlank() || headerValue.isBlank()) return
            if (!headerName.all { it.isLetterOrDigit() || it == '-' }) return
            if (headerName.lowercase() in setOf("host", "content-length", "connection")) return
            extraHeaders[headerName] = headerValue
            sawStructuredConfig = true
        }

        fun acceptCookie(value: String) {
            acceptHeader("Cookie", value)
        }

        fun JSONObject.readHeadersObject() {
            optJSONObject("headers")?.let { headers ->
                val keys = headers.keys()
                while (keys.hasNext()) {
                    val name = keys.next()
                    if (!headers.isNull(name)) acceptHeader(name, headers.optString(name, ""))
                }
            }
        }

        runCatching {
            if (clean.startsWith("{")) {
                val obj = JSONTokener(clean).nextValue() as? JSONObject ?: return@runCatching
                sawStructuredConfig = true
                firstPresentString(obj, "base_url", "baseUrl", "api_url", "apiUrl", "url").takeIf { it.isNotBlank() }?.let(::acceptBase)
                firstPresentString(obj, "api_key", "apiKey", "key", "token").takeIf { it.isNotBlank() }?.let(::acceptKey)
                firstPresentString(obj, "ai_token", "aiToken", "Ai_token", "jwt").takeIf { it.isNotBlank() }?.let(::acceptAiToken)
                firstPresentString(obj, "cookie", "Cookie", "session_cookie", "sessionCookie").takeIf { it.isNotBlank() }?.let(::acceptCookie)
                firstPresentString(obj, "authorization", "Authorization").takeIf { it.isNotBlank() }?.let {
                    acceptHeader("Authorization", it)
                }
                firstPresentString(obj, "x_api_key", "x-api-key", "xApiKey").takeIf { it.isNotBlank() }?.let {
                    acceptKey(it)
                    authMode = AceMusicAuthMode.X_API_KEY
                }
                firstPresentString(obj, "auth_mode", "authMode", "auth_header", "authHeader", "header").takeIf { it.isNotBlank() }?.let(::acceptAuthMode)
                obj.readHeadersObject()
            }
        }

        if (!clean.startsWith("{")) {
            clean.lines().forEach { line ->
                val normalized = line.trim()
                val separator = when {
                    "=" in normalized -> "="
                    ":" in normalized -> ":"
                    else -> return@forEach
                }
                val rawName = normalized.substringBefore(separator).trim()
                val name = rawName.lowercase().replace("-", "_").replace(" ", "_")
                val value = normalized.substringAfter(separator).trim()
                when (name) {
                    "base_url", "baseurl", "api_url", "apiurl", "url" -> acceptBase(value)
                    "api_key", "apikey", "key", "token" -> acceptKey(value)
                    "ai_token", "aitoken", "jwt" -> acceptAiToken(value)
                    "cookie", "session_cookie", "sessioncookie" -> acceptCookie(value)
                    "authorization" -> acceptHeader("Authorization", value)
                    "x_api_key", "xapikey" -> {
                        acceptKey(value)
                        authMode = AceMusicAuthMode.X_API_KEY
                    }
                    "auth_mode", "authmode", "auth_header", "authheader", "header" -> acceptAuthMode(value)
                    else -> if (separator == ":" && rawName.all { it.isLetterOrDigit() || it == '-' }) {
                        acceptHeader(rawName, value)
                    }
                }
            }
        }

        if (!clean.startsWith("{") && '\n' !in clean) {
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
            val threeParts = clean.split('|', limit = 3).map { it.trim() }
            if (threeParts.size == 3) {
                when {
                    threeParts[0].startsWith("http://") || threeParts[0].startsWith("https://") -> {
                        acceptBase(threeParts[0])
                        acceptKey(threeParts[1])
                        acceptAiToken(threeParts[2])
                    }
                    threeParts[1].startsWith("http://") || threeParts[1].startsWith("https://") -> {
                        acceptKey(threeParts[0])
                        acceptBase(threeParts[1])
                        acceptAiToken(threeParts[2])
                    }
                    else -> {
                        acceptKey(threeParts[0])
                        acceptAiToken(threeParts[1])
                        acceptBase(threeParts[2])
                    }
                }
            }
        }
        if (sawStructuredConfig && !keyWasSet) {
            key = ""
        }

        return AceMusicAuth(
            apiKey = key.removePrefix("Bearer ").removePrefix("bearer ").trim(),
            aiToken = aiToken.removePrefix("Bearer ").removePrefix("bearer ").trim(),
            baseUrl = baseUrl,
            authMode = authMode,
            extraHeaders = extraHeaders.toMap(),
        )
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

    private fun String.unquote(): String =
        trim().removeSurrounding("\"").removeSurrounding("'")

    private data class AceMusicAuth(
        val apiKey: String,
        val aiToken: String,
        val baseUrl: String,
        val authMode: AceMusicAuthMode,
        val extraHeaders: Map<String, String>,
    )

    private data class AudioPayload(
        val value: String,
        val inline: Boolean,
        val format: String,
    )
}
