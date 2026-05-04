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

class AceMusicRepository(
    private val api: AceMusicApi,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun fetchAiTokenOrThrow(): String = withContext(ioDispatcher) {
        val raw = callOrThrow("token") { api.fetchTokenRaw() }
        extractAiToken(raw).ifBlank {
            throw RuntimeException("ACEMusic token: response does not contain Ai_token")
        }
    }

    suspend fun releaseTaskOrThrow(
        aiToken: String,
        taskIds: List<String>,
    ): AceMusicReleaseTaskData = withContext(ioDispatcher) {
        val requestedTaskId = taskIds.firstOrNull().orEmpty()
        val raw = callOrThrow("release_task") {
            api.releaseTask(
                aiToken = aiToken,
                taskIdList = JSONArray(taskIds).toString(),
            )
        }
        val parsed = raw.toJsonValue()
        (parsed as? JSONObject)?.requireEngineOk("release_task")
        val returnedTaskId = findString(parsed, "task_id", "taskId", "id").ifBlank { requestedTaskId }
        AceMusicReleaseTaskData(taskId = returnedTaskId, status = findString(parsed, "status").ifBlank { "submitted" })
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
        val recordsRoot = if (parsed is JSONObject && looksLikeTaskRecord(parsed)) {
            parsed
        } else if (parsed is JSONObject) {
            firstJsonValue(parsed, "data", "result", "tasks", "task_list", "taskList") ?: parsed
        } else {
            parsed
        }
        val records = mutableListOf<AceMusicTaskRecord>()
        collectTaskRecords(
            value = recordsRoot,
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

    private fun extractAiToken(raw: JsonElement): String {
        if (raw.isJsonPrimitive) return raw.asString.orEmpty()
        return runCatching {
            extractAiToken(raw.toJsonValue())
        }.getOrDefault("")
    }

    private fun extractAiToken(value: Any?): String {
        return when (value) {
            is JSONObject -> {
                listOf("Ai_token", "ai_token", "aiToken", "token", "jwt", "access_token").firstNotNullOfOrNull { key ->
                    value.optString(key, "").takeIf { it.isNotBlank() }
                } ?: run {
                    val data = value.opt("data")
                    if (data != null && data != JSONObject.NULL) extractAiToken(data) else ""
                }
            }
            is JSONArray -> {
                for (i in 0 until value.length()) {
                    val token = extractAiToken(value.opt(i))
                    if (token.isNotBlank()) return token
                }
                ""
            }
            is String -> value.takeIf { it.count { ch -> ch == '.' } >= 2 || it.length > 64 }.orEmpty()
            else -> ""
        }
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
                    status = 0,
                    result = value,
                    error = null,
                )
            }
        }
    }

    private fun parseTaskRecord(j: JSONObject, fallbackTaskId: String): AceMusicTaskRecord {
        val resultValue = if (j.has("audio_paths") || j.has("first_audio_path")) {
            j
        } else {
            firstJsonValue(j, "result", "audio", "audio_url", "file", "url", "path", "output", "outputs")
        }
        val resultText = when (resultValue) {
            null, JSONObject.NULL -> j.toString()
            is String -> resultValue
            else -> resultValue.toString()
        }
        return AceMusicTaskRecord(
            taskId = firstPresentString(j, "task_id", "taskId", "id").ifBlank { fallbackTaskId },
            status = if (j.has("status")) statusCode(j.opt("status")) else if (resultValue != null && resultValue != JSONObject.NULL) 1 else 0,
            result = resultText,
            error = null,
        )
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
            "audio_paths", "first_audio_path", "file", "url", "path", "output", "outputs",
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
    val statusCode: Int,
    val rawErrorBody: String,
    cause: Throwable?,
) : RuntimeException(
    "ACEMusic HTTP $statusCode: ${rawErrorBody.ifBlank { "empty error body" }.take(800)}",
    cause,
)
