package com.glassfiles.data.ai.providers

import android.content.Context
import com.glassfiles.data.ai.SystemPrompts
import com.glassfiles.data.ai.models.AiProviderId

object XaiProvider : OpenAiCompatProvider(
    id = AiProviderId.XAI,
    systemPrompt = SystemPrompts.DEFAULT,
) {
    override fun baseUrl(context: Context): String = "https://api.x.ai/v1"

    /**
     * xAI exposes `POST /v1/images/generations` for `grok-2-image*` etc., with
     * the same shape as OpenAI's images API — share the helper.
     */
    override suspend fun generateImage(
        context: Context,
        modelId: String,
        prompt: String,
        apiKey: String,
        size: String,
        n: Int,
    ): List<String> = OpenAiCompatImageGen.generate(
        baseUrl = baseUrl(context),
        cacheDir = context.cacheDir,
        providerLabel = id.displayName,
        fileTag = "xai",
        modelId = modelId,
        prompt = prompt,
        apiKey = apiKey,
        size = size,
        n = n,
    )
}
