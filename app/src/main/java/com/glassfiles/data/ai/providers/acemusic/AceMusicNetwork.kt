package com.glassfiles.data.ai.providers.acemusic

import android.util.Log
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

private const val DEFAULT_ACEMUSIC_BASE_URL = "https://api.acemusic.ai/"
private const val HTTP_LOG_TAG = "ACEMusicHttp"

enum class AceMusicAuthMode {
    BEARER,
    X_API_KEY,
}

class AceMusicAuthInterceptor(
    private val apiKeyProvider: () -> String,
    private val authMode: AceMusicAuthMode = AceMusicAuthMode.BEARER,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val apiKey = apiKeyProvider().trim()
        val builder = chain.request().newBuilder()
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")

        if (apiKey.isNotBlank()) {
            when (authMode) {
                AceMusicAuthMode.BEARER -> builder.header("Authorization", "Bearer $apiKey")
                AceMusicAuthMode.X_API_KEY -> builder.header("x-api-key", apiKey)
            }
        }

        return chain.proceed(builder.build())
    }
}

object AceMusicNetwork {
    fun createOkHttpClient(
        apiKeyProvider: () -> String,
        authMode: AceMusicAuthMode = AceMusicAuthMode.BEARER,
        logBodies: Boolean = true,
    ): OkHttpClient {
        val logging = HttpLoggingInterceptor { message -> Log.d(HTTP_LOG_TAG, message) }.apply {
            level = if (logBodies) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.BASIC
            }
            redactHeader("Authorization")
            redactHeader("x-api-key")
        }

        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(2, TimeUnit.MINUTES)
            .readTimeout(11, TimeUnit.MINUTES)
            .addInterceptor(AceMusicAuthInterceptor(apiKeyProvider, authMode))
            .addInterceptor(logging)
            .build()
    }

    fun createApi(
        baseUrl: String,
        apiKeyProvider: () -> String,
        authMode: AceMusicAuthMode = AceMusicAuthMode.BEARER,
        logBodies: Boolean = true,
    ): AceMusicApi {
        return Retrofit.Builder()
            .baseUrl(baseUrl.normalizedRetrofitBaseUrl())
            .client(createOkHttpClient(apiKeyProvider, authMode, logBodies))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AceMusicApi::class.java)
    }

    private fun String.normalizedRetrofitBaseUrl(): String {
        val clean = trim().ifBlank { DEFAULT_ACEMUSIC_BASE_URL }.trimEnd('/')
        return "$clean/"
    }
}
