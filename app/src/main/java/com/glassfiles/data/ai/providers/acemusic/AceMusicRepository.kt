package com.glassfiles.data.ai.providers.acemusic

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException

private const val REPOSITORY_LOG_TAG = "ACEMusicRepository"

class AceMusicRepository(
    private val api: AceMusicApi,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun createCompletionRawOrThrow(
        request: AceMusicCompletionRequest,
    ): String = withContext(ioDispatcher) {
        callOrThrow("chat_completions") { api.createCompletion(request).string() }
    }

    suspend fun releaseTaskOrThrow(
        request: AceMusicGenerationRequest,
    ): AceMusicReleaseTaskData = withContext(ioDispatcher) {
        val envelope = callOrThrow("release_task") { api.releaseTask(request) }
        envelope.requireOk("release_task").data ?: throw RuntimeException("ACEMusic release_task: empty data")
    }

    suspend fun queryResultOrThrow(
        taskIds: List<String>,
    ): List<AceMusicTaskRecord> = withContext(ioDispatcher) {
        val envelope = callOrThrow("query_result") {
            api.queryResult(AceMusicQueryResultRequest(taskIds))
        }
        envelope.requireOk("query_result").data.orEmpty()
    }

    suspend fun listModelsRawOrThrow(): String = withContext(ioDispatcher) {
        callOrThrow("models") { api.listModelsRaw().string() }
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
}

class AceMusicHttpDebugException(
    val statusCode: Int,
    val rawErrorBody: String,
    cause: Throwable?,
) : RuntimeException(
    "ACEMusic HTTP $statusCode: ${rawErrorBody.ifBlank { "empty error body" }.take(800)}",
    cause,
)
