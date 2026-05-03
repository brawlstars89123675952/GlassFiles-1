package com.glassfiles.data.ai.models

/**
 * Coarse capability buckets that describe what a model can do.
 *
 * A model usually has multiple capabilities (e.g. a vision-LLM has TEXT + VISION,
 * a coder model has TEXT + CODING, a flagship has TEXT + VISION + CODING + REASONING).
 *
 * Filtering / model-picking in the UI is done by this set, not by name patterns.
 */
enum class AiCapability {
    TEXT,        // standard chat/completion
    VISION,      // accepts images as input
    IMAGE_GEN,   // generates images
    VIDEO_GEN,   // generates videos
    MUSIC_GEN,   // generates music / songs
    CODING,      // tuned for code (or top-tier general models that handle code well)
    REASONING,   // explicit chain-of-thought / "thinking" models (o1, R1, QwQ, ...)
    EMBEDDING,   // produces embedding vectors (not used in chat UI, but listed)
    AUDIO,       // speech-to-text or text-to-speech
}
