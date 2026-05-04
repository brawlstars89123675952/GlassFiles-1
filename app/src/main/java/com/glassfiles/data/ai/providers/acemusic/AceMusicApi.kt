package com.glassfiles.data.ai.providers.acemusic

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST

interface AceMusicApi {
    @FormUrlEncoded
    @POST("release_task")
    suspend fun releaseTask(
        @Field("ai_token") aiToken: String,
        @Field("task_id_list") taskIdList: String,
        @Field("app") app: String = "studio-web",
    ): JsonElement

    @GET("v1/models")
    suspend fun listModelsRaw(): AceMusicEnvelope<AceMusicModelData>
}

data class AceMusicModelData(
    @SerializedName("models") val models: List<AceMusicModelRecord>? = null,
    @SerializedName("default_model") val defaultModel: String? = null,
)

data class AceMusicModelRecord(
    @SerializedName("name") val name: String? = null,
    @SerializedName("id") val id: String? = null,
    @SerializedName("model") val model: String? = null,
    @SerializedName("is_default") val isDefault: Boolean? = null,
)

data class AceMusicEnvelope<T>(
    @SerializedName("data") val data: T? = null,
    @SerializedName("code") val code: Int? = null,
    @SerializedName("error") val error: JsonElement? = null,
    @SerializedName("timestamp") val timestamp: Long? = null,
    @SerializedName("extra") val extra: JsonElement? = null,
)
