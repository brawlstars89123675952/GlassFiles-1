package com.glassfiles.data.ai

import com.glassfiles.data.ai.models.AiCapability
import com.glassfiles.data.ai.models.AiCapability.AUDIO
import com.glassfiles.data.ai.models.AiCapability.CODING
import com.glassfiles.data.ai.models.AiCapability.EMBEDDING
import com.glassfiles.data.ai.models.AiCapability.IMAGE_GEN
import com.glassfiles.data.ai.models.AiCapability.REASONING
import com.glassfiles.data.ai.models.AiCapability.TEXT
import com.glassfiles.data.ai.models.AiCapability.VIDEO_GEN
import com.glassfiles.data.ai.models.AiCapability.VISION
import com.glassfiles.data.ai.models.AiProviderId

/**
 * Maps a model id to a [Set] of [AiCapability] using simple name patterns.
 *
 * Providers don't expose capabilities in any standard way (some do, most don't),
 * so we infer them. Patterns are intentionally loose — false positives are fine,
 * the picker just shows extra options. False negatives (missing CODING/VISION on
 * a model that actually supports it) hurt UX, so when in doubt we err on the
 * side of including the capability.
 */
object CapabilityClassifier {

    fun classify(providerId: AiProviderId, modelId: String): Set<AiCapability> {
        val id = modelId.lowercase()
        val caps = mutableSetOf<AiCapability>()

        // ─── Image generation ───────────────────────────────────────────────
        if (matchesAny(id,
                "gpt-image", "dall-e", "dalle",
                "imagen",
                "wanx", "wan-",                  // Alibaba image
                "grok-2-image", "grok-image", "imagine",
                "flux", "stable-diffusion", "sdxl", "sd-",
            )
        ) caps += IMAGE_GEN

        // ─── Video generation ───────────────────────────────────────────────
        if (matchesAny(id,
                "veo", "video-",
                "wan-video", "wanx-video", "cogvideox",
                "grok-video", "imagine-video", "grok-imagine-video",
            )
        ) caps += VIDEO_GEN

        // `grok-imagine-video` matches both `imagine` (IMAGE_GEN) and
        // `imagine-video` (VIDEO_GEN). Video wins.
        if (VIDEO_GEN in caps) caps -= IMAGE_GEN

        // ─── Audio (TTS / STT) ──────────────────────────────────────────────
        if (matchesAny(id, "whisper", "tts-", "tts1", "audio")) caps += AUDIO

        // ─── Embeddings ─────────────────────────────────────────────────────
        if (matchesAny(id, "embedding", "embed-", "text-embedding")) caps += EMBEDDING

        // ─── Vision (multimodal text+image input) ───────────────────────────
        if (matchesAny(id,
                "gpt-4o", "gpt-4-vision", "gpt-4-turbo", "gpt-5", "o1", "o3", "o4",
                "claude-3", "claude-4", "claude-opus", "claude-sonnet", "claude-haiku",
                "gemini",
                "grok-2", "grok-3", "grok-4", "grok-vision",
                "qwen-vl", "qwen2-vl", "qwen3-vl", "qwen-omni",
                "moonshot-v1-vision", "kimi-vl",
                "vision",
            ) && IMAGE_GEN !in caps && VIDEO_GEN !in caps
        ) caps += VISION

        // ─── Reasoning / "thinking" models ──────────────────────────────────
        if (matchesAny(id,
                "o1-", "o3-", "o4-",
                "deepseek-r1", "r1-",
                "qwq-", "qwen-math", "qwen3-math",
                "claude-opus", "thinking",
                "reasoning",
            )
        ) caps += REASONING

        // ─── Coding ────────────────────────────────────────────────────────
        if (matchesAny(id,
                "code", "coder", "codex",
                "claude-opus", "claude-sonnet", "claude-3-5-sonnet", "claude-4",
                "gpt-4o", "gpt-5", "o1-", "o3-",
                "gemini-2.5-pro", "gemini-3",
                "grok-code", "grok-2", "grok-3", "grok-4",
                "qwen3-coder", "qwen2.5-coder",
                "kimi-k2",
            )
        ) caps += CODING

        // ─── Text (the default) ─────────────────────────────────────────────
        // Anything that isn't pure image/video/audio/embedding gets TEXT.
        if (IMAGE_GEN !in caps && VIDEO_GEN !in caps && EMBEDDING !in caps && AUDIO !in caps) {
            caps += TEXT
        }

        // ─── Provider-specific overrides ────────────────────────────────────
        when (providerId) {
            AiProviderId.ANTHROPIC -> {
                // All current Claude models accept images.
                if (TEXT in caps) caps += VISION
            }
            AiProviderId.MOONSHOT -> {
                // Kimi K2 family is coding-strong; all moonshot-v1-* are text.
                if (id.contains("k2")) caps += CODING
            }
            AiProviderId.OPENROUTER -> {
                // OpenRouter ids contain the upstream slug ("anthropic/claude-3.5-sonnet").
                // Re-classify against the upstream id portion.
                val upstream = id.substringAfter('/')
                if (upstream != id) {
                    return classify(providerId, upstream)
                }
            }
            else -> Unit
        }

        return caps
    }

    private fun matchesAny(id: String, vararg needles: String): Boolean =
        needles.any { id.contains(it) }
}
