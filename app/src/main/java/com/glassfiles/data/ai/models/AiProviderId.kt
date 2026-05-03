package com.glassfiles.data.ai.models

/**
 * Stable identifier for an AI provider. Used as a key everywhere (preferences,
 * registry cache, chat sessions, etc.) — never expose the enum's `name` to UI;
 * use [displayName] instead.
 *
 * @property consoleUrl Public URL where the user can sign up / generate an API
 *   key for this provider. Surfaced from the Keys screen as a "Get a key" link.
 */
enum class AiProviderId(
    val displayName: String,
    val keyPrefsKey: String,
    val consoleUrl: String,
) {
    OPENAI("OpenAI", "ai_key_openai", "https://platform.openai.com/api-keys"),
    ANTHROPIC("Anthropic", "ai_key_anthropic", "https://console.anthropic.com/settings/keys"),
    GOOGLE("Google", "ai_key_google", "https://aistudio.google.com/apikey"),
    XAI("xAI (Grok)", "ai_key_xai", "https://console.x.ai/"),
    MOONSHOT("Moonshot (Kimi)", "ai_key_moonshot", "https://platform.moonshot.ai/console/api-keys"),
    ALIBABA("Alibaba (Qwen)", "ai_key_alibaba", "https://dashscope.console.aliyun.com/apiKey"),
    OPENROUTER("OpenRouter", "ai_key_openrouter", "https://openrouter.ai/keys"),
    ACEMUSIC("ACEMusic", "ai_key_acemusic", "https://acemusic.ai/playground/api-key"),
}
