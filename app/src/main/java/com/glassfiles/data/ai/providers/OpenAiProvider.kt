package com.glassfiles.data.ai.providers

import android.content.Context
import com.glassfiles.data.ai.SystemPrompts
import com.glassfiles.data.ai.models.AiProviderId

object OpenAiProvider : OpenAiCompatProvider(
    id = AiProviderId.OPENAI,
    systemPrompt = SystemPrompts.DEFAULT,
) {
    override fun baseUrl(context: Context): String = "https://api.openai.com/v1"

    override fun acceptModelId(rawId: String): Boolean {
        // Drop org-internal / fine-tune ids and obvious non-chat tools we can't route.
        if (rawId.startsWith("ft:")) return false
        if (rawId.startsWith("ftjob")) return false
        return rawId.isNotBlank()
    }

    override fun displayName(rawId: String): String = rawId

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
        fileTag = "openai",
        modelId = modelId,
        prompt = prompt,
        apiKey = apiKey,
        size = size,
        n = n,
    )
}
