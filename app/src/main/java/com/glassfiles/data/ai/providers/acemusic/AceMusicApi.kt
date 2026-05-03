package com.glassfiles.data.ai.providers.acemusic

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface AceMusicApi {
    @POST("v1/chat/completions")
    suspend fun createCompletion(
        @Body request: AceMusicCompletionRequest,
    ): AceMusicCompletionResponse

    @GET("v1/models")
    suspend fun listModelsRaw(): ResponseBody
}

data class AceMusicCompletionRequest(
    @SerializedName("model") val model: String,
    @SerializedName("messages") val messages: List<AceMusicChatMessage>,
    @SerializedName("stream") val stream: Boolean = false,
    @SerializedName("modalities") val modalities: List<String>? = null,
    @SerializedName("audio_config") val audioConfig: AceMusicAudioConfig? = null,
    @SerializedName("task_type") val taskType: String? = null,
    @SerializedName("sample_mode") val sampleMode: Boolean? = null,
    @SerializedName("use_format") val useFormat: Boolean? = null,
    @SerializedName("use_cot_caption") val useCotCaption: Boolean? = null,
    @SerializedName("use_cot_language") val useCotLanguage: Boolean? = null,
    @SerializedName("thinking") val thinking: Boolean? = null,
    @SerializedName("lyrics") val lyrics: String? = null,
    @SerializedName("guidance_scale") val guidanceScale: Float? = null,
    @SerializedName("batch_size") val batchSize: Int? = null,
    @SerializedName("seed") val seed: Int? = null,
    @SerializedName("ai_token") val aiToken: String? = null,
)

data class AceMusicChatMessage(
    @SerializedName("role") val role: String,
    @SerializedName("content") val content: String,
)

data class AceMusicAudioConfig(
    @SerializedName("duration") val durationSec: Float? = null,
    @SerializedName("format") val format: String = "mp3",
    @SerializedName("bpm") val bpm: Int? = null,
    @SerializedName("key_scale") val keyScale: String? = null,
    @SerializedName("time_signature") val timeSignature: String? = null,
    @SerializedName("vocal_language") val vocalLanguage: String? = null,
    @SerializedName("instrumental") val instrumental: Boolean? = null,
)

data class AceMusicCompletionResponse(
    @SerializedName("id") val id: String? = null,
    @SerializedName("object") val objectType: String? = null,
    @SerializedName("created") val created: Long? = null,
    @SerializedName("model") val model: String? = null,
    @SerializedName("choices") val choices: List<AceMusicChoice> = emptyList(),
    @SerializedName("usage") val usage: AceMusicUsage? = null,
    @SerializedName("audio") val audio: JsonElement? = null,
    @SerializedName("audio_url") val audioUrl: JsonElement? = null,
    @SerializedName("result") val result: JsonElement? = null,
    @SerializedName("error") val error: JsonElement? = null,
)

data class AceMusicChoice(
    @SerializedName("index") val index: Int? = null,
    @SerializedName("message") val message: AceMusicAssistantMessage? = null,
    @SerializedName("finish_reason") val finishReason: String? = null,
)

data class AceMusicAssistantMessage(
    @SerializedName("role") val role: String? = null,
    @SerializedName("content") val content: String? = null,
    @SerializedName("audio") val audio: JsonElement? = null,
    @SerializedName("audio_url") val audioUrl: JsonElement? = null,
    @SerializedName("audios") val audios: JsonElement? = null,
)

data class AceMusicUsage(
    @SerializedName("prompt_tokens") val promptTokens: Int? = null,
    @SerializedName("completion_tokens") val completionTokens: Int? = null,
    @SerializedName("total_tokens") val totalTokens: Int? = null,
)
