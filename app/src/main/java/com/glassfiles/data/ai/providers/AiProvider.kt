package com.glassfiles.data.ai.providers

import android.content.Context
import com.glassfiles.data.ai.agent.AiTool
import com.glassfiles.data.ai.agent.AiToolCall
import com.glassfiles.data.ai.models.AiMessage
import com.glassfiles.data.ai.models.AiModel
import com.glassfiles.data.ai.models.AiProviderId

/**
 * One entry per provider (OpenAI, Anthropic, Google, ...).
 *
 * Implementations live in this package and are registered in [AiProviders].
 * The dispatcher ([com.glassfiles.data.ai.AiManager]) selects an implementation
 * by [AiProviderId] and forwards calls.
 */
interface AiProvider {
    val id: AiProviderId

    /**
     * Fetches the live model catalog from the provider's `/models`-style endpoint.
     *
     * Implementations should NOT cache — that's [com.glassfiles.data.ai.ModelRegistry]'s
     * job. Callers pass a non-blank API key. Errors should propagate as exceptions
     * with a message safe to show in a Toast.
     */
    suspend fun listModels(context: Context, apiKey: String): List<AiModel>

    /**
     * Streams a chat completion. [onChunk] is called on the IO dispatcher with
     * each delta. Returns the full assembled response string.
     *
     * Throws on HTTP error / auth failure.
     */
    suspend fun chat(
        context: Context,
        modelId: String,
        messages: List<AiMessage>,
        apiKey: String,
        onChunk: (String) -> Unit,
    ): String

    /**
     * Optional: generates one or more images. Returns local file paths in
     * [Context.cacheDir] / `ai_images/`. Implementations that don't support image
     * generation throw [UnsupportedOperationException] (default below).
     */
    suspend fun generateImage(
        context: Context,
        modelId: String,
        prompt: String,
        apiKey: String,
        size: String = "1024x1024",
        n: Int = 1,
    ): List<String> = throw UnsupportedOperationException("${id.displayName} doesn't support image generation in this build")

    /**
     * Optional: generates a video. Returns the local file path of the produced
     * mp4. Implementations may poll a job-status endpoint internally.
     */
    suspend fun generateVideo(
        context: Context,
        modelId: String,
        prompt: String,
        apiKey: String,
        durationSec: Int = 5,
        aspectRatio: String = "16:9",
        onProgress: (String) -> Unit = {},
    ): String = throw UnsupportedOperationException("${id.displayName} doesn't support video generation in this build")

    /**
     * Optional: generates one or more music/audio files. This is intentionally
     * separate from chat/image/video flows so music providers stay isolated.
     */
    suspend fun generateMusic(
        context: Context,
        modelId: String,
        request: AiMusicRequest,
        apiKey: String,
        onProgress: (String) -> Unit = {},
    ): List<AiMusicResult> = throw UnsupportedOperationException("${id.displayName} doesn't support music generation in this build")

    /**
     * Optional: non-streaming chat that surfaces *tool calls* the model wants
     * to make. Drives the agent loop:
     *
     *   1. UI calls [chatWithTools] with the current transcript and tool defs.
     *   2. Provider returns either text only ([AiToolTurn.assistantText] +
     *      empty [AiToolTurn.toolCalls]) → conversation ends until the user
     *      types again,
     *   3. or text + tool_calls → UI executes / approves them, appends a
     *      `role = "tool"` message per call result, and re-invokes
     *      [chatWithTools].
     *
     * Default impl falls back to plain [chat] (no tool calls available).
     * Implementations that don't actually support tools should keep this
     * default — the agent screen surfaces "tools unavailable" if every call
     * comes back with empty [AiToolTurn.toolCalls].
     */
    suspend fun chatWithTools(
        context: Context,
        modelId: String,
        messages: List<AiMessage>,
        tools: List<AiTool>,
        apiKey: String,
    ): AiToolTurn {
        val text = chat(context, modelId, messages, apiKey) {}
        return AiToolTurn(assistantText = text, toolCalls = emptyList())
    }

    /**
     * Streaming variant of [chatWithTools]. As the provider streams the
     * model's response, [onTextDelta] is fired for each free-text chunk,
     * giving the UI a chance to render the assistant message live. Tool
     * calls are only delivered as a complete batch in the returned
     * [AiToolTurn]: providers that emit them as partial JSON deltas (e.g.
     * Anthropic, OpenAI compat) accumulate the partials internally so the
     * caller always receives a fully-parsed [AiToolCall] list.
     *
     * Default implementation falls back to non-streaming [chatWithTools]
     * and emits the entire assistant text in a single delta. Streaming
     * providers should override.
     */
    suspend fun chatWithToolsStreaming(
        context: Context,
        modelId: String,
        messages: List<AiMessage>,
        tools: List<AiTool>,
        apiKey: String,
        onTextDelta: (String) -> Unit,
    ): AiToolTurn {
        val turn = chatWithTools(context, modelId, messages, tools, apiKey)
        if (turn.assistantText.isNotEmpty()) onTextDelta(turn.assistantText)
        return turn
    }
}

/** One non-streaming agent turn: free text + zero-or-more tool calls. */
data class AiToolTurn(
    val assistantText: String,
    val toolCalls: List<AiToolCall>,
    val usage: AiTokenUsage? = null,
)

data class AiTokenUsage(
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val cacheCreationTokens: Int = 0,
    val cacheReadTokens: Int = 0,
    val isComplete: Boolean = true,
) {
    val totalTokens: Int get() = inputTokens + outputTokens + cacheCreationTokens + cacheReadTokens
    val hasCacheHit: Boolean get() = cacheCreationTokens > 0 || cacheReadTokens > 0
}

data class AiMusicRequest(
    val prompt: String = "",
    val lyrics: String = "",
    val sampleMode: Boolean = false,
    val sampleQuery: String = "",
    val useFormat: Boolean = false,
    val thinking: Boolean = true,
    val vocalLanguage: String = "en",
    val audioFormat: String = "mp3",
    val durationSec: Float? = 60f,
    val bpm: Int? = null,
    val keyScale: String = "",
    val timeSignature: String = "4",
    val inferenceSteps: Int = 8,
    val guidanceScale: Float = 7f,
    val useRandomSeed: Boolean = true,
    val seed: Int? = null,
    val batchSize: Int = 1,
    val shift: Float = 3f,
    val inferMethod: String = "ode",
    val timesteps: String = "",
    val useAdg: Boolean = false,
    val cfgIntervalStart: Float? = null,
    val cfgIntervalEnd: Float? = null,
    val useCotCaption: Boolean = true,
    val useCotLanguage: Boolean = false,
    val constrainedDecoding: Boolean = true,
    val allowLmBatch: Boolean = true,
    val lmTemperature: Float? = null,
    val lmCfgScale: Float? = null,
    val lmNegativePrompt: String = "",
    val lmTopK: Int? = null,
    val lmTopP: Float? = null,
    val lmRepetitionPenalty: Float? = null,
)

data class AiMusicResult(
    val filePath: String,
    val prompt: String = "",
    val lyrics: String = "",
    val bpm: Int? = null,
    val keyScale: String = "",
    val timeSignature: String = "",
    val durationSec: Float? = null,
    val seed: String = "",
    val taskId: String = "",
)
