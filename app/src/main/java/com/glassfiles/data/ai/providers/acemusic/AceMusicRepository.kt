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
    suspend fun createCompletion(
        request: AceMusicCompletionRequest,
    ): AceMusicNetworkResult<AceMusicCompletionResponse> = withContext(ioDispatcher) {
        try {
            AceMusicNetworkResult.Success(api.createCompletion(request))
        } catch (e: HttpException) {
            val rawError = e.response()?.errorBody()?.string().orEmpty()
            Log.e(REPOSITORY_LOG_TAG, "ACEMusic HTTP ${e.code()} raw error: $rawError", e)
            AceMusicNetworkResult.HttpError(
                code = e.code(),
                rawErrorBody = rawError,
                message = e.message(),
                cause = e,
            )
        } catch (e: IOException) {
            Log.e(REPOSITORY_LOG_TAG, "ACEMusic network error: ${e.message}", e)
            AceMusicNetworkResult.NetworkError(e)
        } catch (e: Exception) {
            Log.e(REPOSITORY_LOG_TAG, "ACEMusic unexpected error: ${e.message}", e)
            AceMusicNetworkResult.UnexpectedError(e)
        }
    }

    suspend fun createCompletionOrThrow(
        request: AceMusicCompletionRequest,
    ): AceMusicCompletionResponse {
        return when (val result = createCompletion(request)) {
            is AceMusicNetworkResult.Success -> result.value
            is AceMusicNetworkResult.HttpError -> throw AceMusicHttpDebugException(
                statusCode = result.code,
                rawErrorBody = result.rawErrorBody,
                cause = result.cause,
            )
            is AceMusicNetworkResult.NetworkError -> throw RuntimeException("ACEMusic network error: ${result.cause.message}", result.cause)
            is AceMusicNetworkResult.UnexpectedError -> throw RuntimeException("ACEMusic error: ${result.cause.message}", result.cause)
        }
    }

    suspend fun listModelsRawOrThrow(): String = withContext(ioDispatcher) {
        try {
            api.listModelsRaw().string()
        } catch (e: HttpException) {
            val rawError = e.response()?.errorBody()?.string().orEmpty()
            Log.e(REPOSITORY_LOG_TAG, "ACEMusic models HTTP ${e.code()} raw error: $rawError", e)
            throw AceMusicHttpDebugException(
                statusCode = e.code(),
                rawErrorBody = rawError,
                cause = e,
            )
        }
    }
}

sealed class AceMusicNetworkResult<out T> {
    data class Success<T>(val value: T) : AceMusicNetworkResult<T>()

    data class HttpError(
        val code: Int,
        val rawErrorBody: String,
        val message: String?,
        val cause: HttpException,
    ) : AceMusicNetworkResult<Nothing>()

    data class NetworkError(val cause: IOException) : AceMusicNetworkResult<Nothing>()

    data class UnexpectedError(val cause: Exception) : AceMusicNetworkResult<Nothing>()
}

class AceMusicHttpDebugException(
    val statusCode: Int,
    val rawErrorBody: String,
    cause: Throwable?,
) : RuntimeException(
    "ACEMusic HTTP $statusCode: ${rawErrorBody.ifBlank { "empty error body" }.take(800)}",
    cause,
)
