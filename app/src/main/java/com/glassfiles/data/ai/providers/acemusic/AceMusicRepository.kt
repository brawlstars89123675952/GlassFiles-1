package com.glassfiles.data.ai.providers.acemusic

import android.util.Log
import com.google.gson.JsonElement
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import retrofit2.HttpException
import java.io.IOException

private const val REPOSITORY_LOG_TAG = "ACEMusicRepository"
private const val ACEMUSIC_TOKEN_URL = "https://acem-api.acemusic.ai/api/acem/user/ai/token"

class AceMusicRepository(
    private val api: AceMusicApi,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun fetchAiTokenOrThrow(): String = withContext(ioDispatcher) {
        val raw = callOrThrow("token:acem-api/user/ai/token") {
            api.fetchTokenRaw(ACEMUSIC_TOKEN_URL)
        }
        val parsed = raw.toJsonValue()
        (parsed as? JSONObject)?.requireEngineOk("token")
        findString(parsed, "token").ifBlank {
            throw RuntimeException("ACEMusic token: response data.token is empty")
        }
    }

    suspend fun releaseTaskOrThrow(
        aiToken: String,
        taskIds: List<String>,
        generationFields: Map<String, String> = emptyMap(),
    ): String = withContext(ioDispatcher) {
        val requestedTaskId = taskIds.firstOrNull().orEmpty()
        val fields = linkedMapOf(
            "ai_token" to aiToken,
            "task_id_list" to JSONArray(taskIds).toString(),
            "app" to "studio-web",
        ).apply {
            generationFields.forEach { (key, value) ->
                if (key.isNotBlank() && value.isNotBlank() && key !in keys) {
                    put(key, value)
                }
            }
        }
        val raw = callOrThrow("release_task") {
            api.releaseTask(fields)
        }
        val parsed = raw.toJsonValue()
        (parsed as? JSONObject)?.requireEngineOk("release_task")
        findString(parsed, "task_id", "taskId", "id").ifBlank { requestedTaskId }
    }

    suspend fun queryResultOrThrow(
        aiToken: String,
        taskIds: List<String>,
    ): List<AceMusicTaskRecord> = withContext(ioDispatcher) {
        val raw = callOrThrow("query_result") {
            api.queryResult(
                aiToken = aiToken,
                taskIdList = JSONArray(taskIds).toString(),
            )
        }
        val parsed = raw.toJsonValue()
        (parsed as? JSONObject)?.requireEngineOk("query_result")
        val records = mutableListOf<AceMusicTaskRecord>()
        collectTaskRecords(
            value = if (parsed is JSONObject) firstJsonValue(parsed, "data", "result", "tasks", "task_list", "taskList") ?: parsed else parsed,
            fallbackTaskIds = taskIds,
            target = records,
        )
        records
    }

    suspend fun listModelsOrThrow(): AceMusicModelData = withContext(ioDispatcher) {
        val envelope = callOrThrow("models") { api.listModelsRaw() }
        envelope.requireOk("models").data ?: AceMusicModelData()
    }

    private suspend fun <T> callOrThrow(
        label: String,
        block: suspend () -> T,
    ): T {
        return try {
            block()
        } catch (e: HttpException) {
            val rawError = e.response()?.errorBody()?.string().orEmpty()
            Log.e(REPOSITORY_LOG_TAG, "ACEMusic $label HTTP ${e.code()} raw error: $rawError", e)
            throw AceMusicHttpDebugException(
                label = label,
                statusCode = e.code(),
                rawErrorBody = rawError,
                cause = e,
            )
        } catch (e: IOException) {
            Log.e(REPOSITORY_LOG_TAG, "ACEMusic $label network error: ${e.message}", e)
            throw RuntimeException("ACEMusic network error: ${e.message}", e)
        } catch (e: Exception) {
            Log.e(REPOSITORY_LOG_TAG, "ACEMusic $label unexpected error: ${e.message}", e)
            throw e
        }
    }

    private fun <T> AceMusicEnvelope<T>.requireOk(label: String): AceMusicEnvelope<T> {
        val apiError = error?.takeIf { !it.isJsonNull }?.toString().orEmpty()
        if (code != null && code !in listOf(0, 200)) {
            throw RuntimeException("ACEMusic $label API code $code: ${apiError.ifBlank { "unknown error" }}")
        }
        if (apiError.isNotBlank()) {
            throw RuntimeException("ACEMusic $label API error: $apiError")
        }
        return this
    }

    private fun JsonElement.toJsonValue(): Any =
        JSONTokener(toString()).nextValue()

    private fun JSONObject.requireEngineOk(label: String) {
        val codeValue = opt("code")
        val code = when (codeValue) {
            is Number -> codeValue.toInt()
            is String -> codeValue.toIntOrNull()
            else -> null
        }
        val errorValue = opt("error")
        val apiFailed = has("success") && !optBoolean("success", true)
        val message = errorText(errorValue).ifBlank {
            if (apiFailed) firstPresentString(this, "message", "msg", "detail") else ""
        }
        if (code != null && code != 0 && code !in 200..299) {
            val detail = message.ifBlank { firstPresentString(this, "message", "msg", "detail") }
            throw RuntimeException("ACEMusic $label API code $code: ${detail.ifBlank { "unknown error" }}")
        }
        if (message.isNotBlank()) {
            throw RuntimeException("ACEMusic $label API error: $message")
        }
    }

    private fun errorText(value: Any?): String = when (value) {
        null, JSONObject.NULL -> ""
        is Boolean -> if (value) "true" else ""
        is JSONObject -> if (value.length() == 0) "" else firstPresentString(value, "message", "msg", "detail", "type").ifBlank { value.toString() }
        is JSONArray -> value.toString().takeIf { it != "[]" }.orEmpty()
        else -> value.toString().takeIf { it.isNotBlank() && it != "0" && it != "false" }.orEmpty()
    }

    private fun collectTaskRecords(value: Any?, fallbackTaskIds: List<String>, target: MutableList<AceMusicTaskRecord>) {
        when (value) {
            is JSONArray -> {
                for (i in 0 until value.length()) {
                    collectTaskRecords(value.opt(i), fallbackTaskIds.drop(i).ifEmpty { fallbackTaskIds }, target)
                }
            }
            is JSONObject -> {
                if (looksLikeTaskRecord(value)) {
                    target += parseTaskRecord(value, fallbackTaskIds.getOrNull(target.size).orEmpty())
                    return
                }
                val nested = firstJsonValue(value, "data", "result", "tasks", "task_list", "taskList")
                if (nested != null && nested !== value) {
                    collectTaskRecords(nested, fallbackTaskIds, target)
                    return
                }
                val keys = value.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val child = value.opt(key)
                    if (child is JSONObject) {
                        val withTaskId = JSONObject(child.toString())
                        if (firstPresentString(withTaskId, "task_id", "taskId", "id").isBlank()) {
                            withTaskId.put("task_id", key)
                        }
                        collectTaskRecords(withTaskId, listOf(key), target)
                    }
                }
            }
            is String -> if (value.isNotBlank()) {
                target += AceMusicTaskRecord(
                    taskId = fallbackTaskIds.firstOrNull(),
                    status = if (value.startsWith("http", ignoreCase = true)) 1 else 0,
                    result = value,
                    audioUrl = value.takeIf { it.startsWith("http", ignoreCase = true) },
                    error = null,
                )
            }
        }
    }

    private fun parseTaskRecord(j: JSONObject, fallbackTaskId: String): AceMusicTaskRecord {
        val directAudioUrl = firstPresentString(j, "audio_url", "audioUrl", "url", "file", "path")
        val resultValue = if (j.has("audio_paths") || j.has("first_audio_path")) {
            j
        } else {
            firstJsonValue(j, "result", "audio", "output", "outputs") ?: directAudioUrl
        }
        val resultStatus = statusCodeFromResultValue(resultValue)
        val audioUrl = directAudioUrl.ifBlank { audioUrlFromResultValue(resultValue) }
        val resultText = when (resultValue) {
            null, JSONObject.NULL -> j.toString()
            is String -> resultValue
            else -> resultValue.toString()
        }
        val directStatus = if (j.has("status")) statusCode(j.opt("status")) else null
        val status = when {
            audioUrl.isNotBlank() -> 1
            directStatus != null && directStatus != 0 -> directStatus
            resultStatus != null -> resultStatus
            directStatus != null -> directStatus
            resultValue != null && resultValue != JSONObject.NULL -> 1
            else -> 0
        }
        return AceMusicTaskRecord(
            taskId = firstPresentString(j, "task_id", "taskId", "id").ifBlank { fallbackTaskId },
            status = status,
            result = resultText,
            audioUrl = audioUrl,
            error = null,
        )
    }

    private fun audioUrlFromResultValue(value: Any?): String =
        firstResultObject(value)?.let { firstPresentString(it, "audio_url", "audioUrl", "url", "file", "path") }.orEmpty()

    private fun statusCodeFromResultValue(value: Any?): Int? =
        firstResultObject(value)?.takeIf { it.has("status") }?.let { statusCode(it.opt("status")) }

    private fun firstResultObject(value: Any?): JSONObject? {
        val parsed = when (value) {
            is JSONObject -> value
            is JSONArray -> value
            is String -> value.takeIf { it.isNotBlank() }?.let {
                runCatching { JSONTokener(it).nextValue() }.getOrNull()
            }
            else -> null
        }
        return when (parsed) {
            is JSONObject -> parsed
            is JSONArray -> {
                for (i in 0 until parsed.length()) {
                    val child = parsed.optJSONObject(i)
                    if (child != null) return child
                }
                null
            }
            else -> null
        }
    }

    private fun statusCode(value: Any?): Int = when (value) {
        is Number -> value.toInt()
        is String -> value.toIntOrNull() ?: when (value.lowercase()) {
            "success", "succeeded", "done", "complete", "completed", "finished" -> 1
            "failed", "fail", "error", "errored", "canceled", "cancelled" -> 2
            else -> 0
        }
        else -> 0
    }

    private fun looksLikeTaskRecord(j: JSONObject): Boolean =
        listOf(
            "task_id", "taskId", "id", "status", "result", "audio", "audio_url",
            "audioUrl", "audio_paths", "first_audio_path", "file", "url", "path", "output", "outputs",
        ).any { j.has(it) }

    private fun firstJsonValue(obj: JSONObject, vararg keys: String): Any? {
        keys.forEach { key ->
            if (obj.has(key) && !obj.isNull(key)) return obj.opt(key)
        }
        return null
    }

    private fun findString(value: Any?, vararg keys: String): String {
        when (value) {
            is JSONObject -> {
                firstPresentString(value, *keys).takeIf { it.isNotBlank() }?.let { return it }
                val iterator = value.keys()
                while (iterator.hasNext()) {
                    findString(value.opt(iterator.next()), *keys).takeIf { it.isNotBlank() }?.let { return it }
                }
            }
            is JSONArray -> for (i in 0 until value.length()) {
                findString(value.opt(i), *keys).takeIf { it.isNotBlank() }?.let { return it }
            }
        }
        return ""
    }

    private fun firstPresentString(obj: JSONObject, vararg keys: String): String {
        keys.forEach { key ->
            if (obj.has(key) && !obj.isNull(key)) {
                val value = obj.optString(key, "")
                if (value.isNotBlank() && value != "null") return value
            }
        }
        return ""
    }
}

class AceMusicHttpDebugException(
    val label: String,
    val statusCode: Int,
    val rawErrorBody: String,
    cause: Throwable?,
) : RuntimeException(
    "ACEMusic $label HTTP $statusCode: ${rawErrorBody.ifBlank { "empty error body" }.take(800)}",
    cause,
)
