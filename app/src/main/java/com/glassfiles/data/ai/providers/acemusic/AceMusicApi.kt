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
    ): ResponseBody

    @POST("release_task")
    suspend fun releaseTask(
        @Body request: AceMusicGenerationRequest,
    ): AceMusicEnvelope<AceMusicReleaseTaskData>

    @POST("query_result")
    suspend fun queryResult(
        @Body request: AceMusicQueryResultRequest,
    ): AceMusicEnvelope<List<AceMusicTaskRecord>>

    @GET("v1/models")
    suspend fun listModelsRaw(): ResponseBody
}

data class AceMusicCompletionRequest(
    @SerializedName("model") val model: String,
    @SerializedName("messages") val messages: List<AceMusicChatMessage>,
    @SerializedName("stream") val stream: Boolean = false,
    @SerializedName("thinking") val thinking: Boolean? = true,
    @SerializedName("use_format") val useFormat: Boolean? = null,
    @SerializedName("sample_mode") val sampleMode: Boolean? = null,
    @SerializedName("use_cot_caption") val useCotCaption: Boolean? = null,
    @SerializedName("use_cot_language") val useCotLanguage: Boolean? = null,
    @SerializedName("audio_config") val audioConfig: AceMusicCompletionAudioConfig? = null,
    @SerializedName("guidance_scale") val guidanceScale: Float? = null,
    @SerializedName("seed") val seed: Int? = null,
    @SerializedName("batch_size") val batchSize: Int? = null,
)

data class AceMusicChatMessage(
    @SerializedName("role") val role: String,
    @SerializedName("content") val content: String,
)

data class AceMusicCompletionAudioConfig(
    @SerializedName("format") val format: String = "mp3",
    @SerializedName("vocal_language") val vocalLanguage: String = "en",
    @SerializedName("duration") val duration: Int? = null,
    @SerializedName("bpm") val bpm: Int? = null,
)

data class AceMusicGenerationRequest(
    @SerializedName("prompt") val prompt: String,
    @SerializedName("model") val model: String = "acestep-v15-turbo",
    @SerializedName("audio_duration") val audioDurationSec: Int = 30,
    @SerializedName("lyrics") val lyrics: String = "",
    @SerializedName("thinking") val thinking: Boolean = false,
    @SerializedName("vocal_language") val vocalLanguage: String = "en",
    @SerializedName("audio_format") val audioFormat: String = "mp3",
    @SerializedName("sample_mode") val sampleMode: Boolean = false,
    @SerializedName("sample_query") val sampleQuery: String = "",
    @SerializedName("use_format") val useFormat: Boolean = false,
    @SerializedName("bpm") val bpm: Int? = null,
    @SerializedName("key_scale") val keyScale: String = "",
    @SerializedName("time_signature") val timeSignature: String = "",
    @SerializedName("inference_steps") val inferenceSteps: Int = 8,
    @SerializedName("guidance_scale") val guidanceScale: Float = 7f,
    @SerializedName("use_random_seed") val useRandomSeed: Boolean = true,
    @SerializedName("seed") val seed: Int = -1,
    @SerializedName("batch_size") val batchSize: Int = 1,
    @SerializedName("shift") val shift: Float = 3f,
    @SerializedName("infer_method") val inferMethod: String = "ode",
    @SerializedName("timesteps") val timesteps: String? = null,
    @SerializedName("use_adg") val useAdg: Boolean = false,
    @SerializedName("cfg_interval_start") val cfgIntervalStart: Float? = null,
    @SerializedName("cfg_interval_end") val cfgIntervalEnd: Float? = null,
    @SerializedName("use_cot_caption") val useCotCaption: Boolean = true,
    @SerializedName("use_cot_language") val useCotLanguage: Boolean = false,
    @SerializedName("constrained_decoding") val constrainedDecoding: Boolean = true,
    @SerializedName("allow_lm_batch") val allowLmBatch: Boolean = true,
    @SerializedName("lm_temperature") val lmTemperature: Float? = null,
    @SerializedName("lm_cfg_scale") val lmCfgScale: Float? = null,
    @SerializedName("lm_negative_prompt") val lmNegativePrompt: String? = null,
    @SerializedName("lm_top_k") val lmTopK: Int? = null,
    @SerializedName("lm_top_p") val lmTopP: Float? = null,
    @SerializedName("lm_repetition_penalty") val lmRepetitionPenalty: Float? = null,
    @SerializedName("task_type") val taskType: String = "text2music",
)

data class AceMusicQueryResultRequest(
    @SerializedName("task_id_list") val taskIdList: List<String>,
)

data class AceMusicEnvelope<T>(
    @SerializedName("data") val data: T? = null,
    @SerializedName("code") val code: Int? = null,
    @SerializedName("error") val error: JsonElement? = null,
    @SerializedName("timestamp") val timestamp: Long? = null,
    @SerializedName("extra") val extra: JsonElement? = null,
)

data class AceMusicReleaseTaskData(
    @SerializedName("task_id") val taskId: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("queue_position") val queuePosition: Int? = null,
)

data class AceMusicTaskRecord(
    @SerializedName("task_id") val taskId: String? = null,
    @SerializedName("status") val status: Int? = null,
    @SerializedName("result") val result: String? = null,
    @SerializedName("error") val error: JsonElement? = null,
)
