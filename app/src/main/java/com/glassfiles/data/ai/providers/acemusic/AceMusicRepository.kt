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
    suspend fun releaseTaskResultOrThrow(
        aiToken: String,
        taskIds: List<String>,
    ): JSONObject = withContext(ioDispatcher) {
        val requestedTaskId = taskIds.firstOrNull().orEmpty()
        val raw = callOrThrow("release_task") {
            api.releaseTask(
                aiToken = aiToken,
                taskIdList = JSONArray(taskIds).toString(),
            )
        }
        val parsed = raw.toJsonValue()
        (parsed as? JSONObject)?.requireEngineOk("release_task")
        val root = when (parsed) {
            is JSONObject -> parsed
            is JSONArray -> JSONObject().put("result", parsed)
            is String -> JSONObject().put("result", parsed)
            else -> JSONObject().put("result", parsed.toString())
        }
        if (!root.has("id")) root.put("id", findString(root, "task_id", "taskId", "id").ifBlank { requestedTaskId })
        root
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
