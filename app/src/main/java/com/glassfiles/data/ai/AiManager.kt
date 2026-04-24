package com.glassfiles.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipFile

// ═══════════════════════════════════
// Provider + Model Catalog Foundation
// ═══════════════════════════════════

enum class AiVendor(val label: String) {
    GOOGLE("Google"),
    ALIBABA("Alibaba Cloud"),
    OPENAI("OpenAI"),
    XAI("xAI"),
    MOONSHOT("Moonshot AI")
}

enum class AiProviderType(
    val storageKey: String,
    val label: String,
    val vendor: AiVendor,
    val defaultBaseUrl: String,
    val supportsProxy: Boolean = false,
    val supportsRegion: Boolean = false
) {
    GEMINI("gemini", "Gemini", AiVendor.GOOGLE, "https://generativelanguage.googleapis.com/v1beta/models", supportsProxy = true),
    QWEN("qwen", "Qwen", AiVendor.ALIBABA, "https://dashscope-intl.aliyuncs.com/compatible-mode/v1", supportsRegion = true),
    OPENAI("openai", "OpenAI / ChatGPT", AiVendor.OPENAI, "https://api.openai.com/v1"),
    XAI("xai", "xAI / Grok", AiVendor.XAI, "https://api.x.ai/v1"),
    KIMI("kimi", "Kimi", AiVendor.MOONSHOT, "https://api.moonshot.ai/v1")
}

enum class AiCapability(val storageKey: String) {
    CHAT("chat"),
    CODING("coding"),
    IMAGE_INPUT("image_input"),
    IMAGE_OUTPUT("image_output"),
    VIDEO_INPUT("video_input"),
    VIDEO_OUTPUT("video_output"),
    FILES("files"),
    REASONING("reasoning"),
    LONG_CONTEXT("long_context")
}

data class AiModelSpec(
    val key: String,
    val providerType: AiProviderType,
    val modelId: String,
    val label: String,
    val description: String,
    val category: String,
    val capabilities: Set<AiCapability> = setOf(AiCapability.CHAT),
    val enabledByDefault: Boolean = true
) {
    val supportsVision: Boolean get() = AiCapability.IMAGE_INPUT in capabilities || AiCapability.VIDEO_INPUT in capabilities
    val supportsFiles: Boolean get() = AiCapability.FILES in capabilities
    val supportsCoding: Boolean get() = AiCapability.CODING in capabilities
    val supportsImageGeneration: Boolean get() = AiCapability.IMAGE_OUTPUT in capabilities
    val supportsVideoGeneration: Boolean get() = AiCapability.VIDEO_OUTPUT in capabilities
}

enum class AiProvider(
    val spec: AiModelSpec
) {
    GEMINI_FLASH(
        AiModelSpec(
            key = "gemini_flash",
            providerType = AiProviderType.GEMINI,
            modelId = "gemini-2.5-flash",
            label = "Gemini 2.5 Flash",
            description = "Fast, efficient",
            category = "Gemini",
            capabilities = setOf(AiCapability.CHAT, AiCapability.CODING, AiCapability.IMAGE_INPUT, AiCapability.FILES)
        )
    ),
    GEMINI_PRO(
        AiModelSpec(
            key = "gemini_pro",
            providerType = AiProviderType.GEMINI,
            modelId = "gemini-2.5-pro",
            label = "Gemini 2.5 Pro",
            description = "Most capable",
            category = "Gemini",
            capabilities = setOf(AiCapability.CHAT, AiCapability.CODING, AiCapability.IMAGE_INPUT, AiCapability.FILES, AiCapability.REASONING, AiCapability.LONG_CONTEXT)
        )
    ),
    GEMINI_FLASH_LITE(
        AiModelSpec(
            key = "gemini_flash_lite",
            providerType = AiProviderType.GEMINI,
            modelId = "gemini-2.5-flash-lite",
            label = "Gemini 2.5 Flash-Lite",
            description = "Lightweight",
            category = "Gemini",
            capabilities = setOf(AiCapability.CHAT, AiCapability.IMAGE_INPUT)
        )
    ),
    GEMINI_3_FLASH(
        AiModelSpec(
            key = "gemini_3_flash",
            providerType = AiProviderType.GEMINI,
            modelId = "gemini-3-flash-preview",
            label = "Gemini 3 Flash",
            description = "Next-gen fast",
            category = "Gemini",
            capabilities = setOf(AiCapability.CHAT, AiCapability.CODING, AiCapability.IMAGE_INPUT, AiCapability.FILES)
        )
    ),
    GEMINI_31_PRO(
        AiModelSpec(
            key = "gemini_31_pro",
            providerType = AiProviderType.GEMINI,
            modelId = "gemini-3.1-pro-preview",
            label = "Gemini 3.1 Pro",
            description = "Next-gen pro",
            category = "Gemini",
            capabilities = setOf(AiCapability.CHAT, AiCapability.CODING, AiCapability.IMAGE_INPUT, AiCapability.FILES, AiCapability.REASONING, AiCapability.LONG_CONTEXT)
        )
    ),
    QWEN3_MAX(
        AiModelSpec(
            key = "qwen3_max",
            providerType = AiProviderType.QWEN,
            modelId = "qwen3-max",
            label = "Qwen3 Max",
            description = "Best Qwen3, complex tasks",
            category = "Qwen Commercial",
            capabilities = setOf(AiCapability.CHAT, AiCapability.CODING, AiCapability.FILES, AiCapability.REASONING, AiCapability.LONG_CONTEXT)
        )
    ),
    QWEN35_PLUS(
        AiModelSpec(
            key = "qwen35_plus",
            providerType = AiProviderType.QWEN,
            modelId = "qwen3.5-plus",
            label = "Qwen3.5 Plus",
            description = "Text + Vision, balanced",
            category = "Qwen Commercial",
            capabilities = setOf(AiCapability.CHAT, AiCapability.CODING, AiCapability.IMAGE_INPUT, AiCapability.FILES)
        )
    ),
    QWEN_PLUS(
        AiModelSpec(
            key = "qwen_plus",
            providerType = AiProviderType.QWEN,
            modelId = "qwen-plus",
            label = "Qwen Plus",
            description = "Smart, balanced",
            category = "Qwen Commercial",
            capabilities = setOf(AiCapability.CHAT, AiCapability.CODING, AiCapability.FILES)
        )
    ),
    QWEN35_FLASH(
        AiModelSpec(
            key = "qwen35_flash",
            providerType = AiProviderType.QWEN,
            modelId = "qwen3.5-flash",
            label = "Qwen3.5 Flash",
            description = "Fast, multimodal",
            category = "Qwen Commercial",
            capabilities = setOf(AiCapability.CHAT, AiCapability.CODING, AiCapability.IMAGE_INPUT, AiCapability.FILES)
        )
    ),
    QWEN_FLASH(
        AiModelSpec(
            key = "qwen_flash",
            providerType = AiProviderType.QWEN,
            modelId = "qwen-flash",
            label = "Qwen Flash",
            description = "Fastest, cheapest",
            category = "Qwen Commercial",
            capabilities = setOf(AiCapability.CHAT, AiCapability.FILES)
        )
    ),
    QWEN_TURBO(
        AiModelSpec(
            key = "qwen_turbo",
            providerType = AiProviderType.QWEN,
            modelId = "qwen-turbo",
            label = "Qwen Turbo",
            description = "Fast, cost-effective",
            category = "Qwen Commercial",
            capabilities = setOf(AiCapability.CHAT, AiCapability.FILES)
        )
    ),
    QWEN_LONG(
        AiModelSpec(
            key = "qwen_long",
            providerType = AiProviderType.QWEN,
            modelId = "qwen-long",
            label = "Qwen Long",
            description = "10M context window",
            category = "Qwen Commercial",
            capabilities = setOf(AiCapability.CHAT, AiCapability.FILES, AiCapability.LONG_CONTEXT)
        )
    ),
    QWEN3_CODER_PLUS(
        AiModelSpec(
            key = "qwen3_coder_plus",
            providerType = AiProviderType.QWEN,
            modelId = "qwen3-coder-plus",
            label = "Qwen3 Coder Plus",
            description = "Code + tool calling",
            category = "Qwen Coder",
            capabilities = setOf(AiCapability.CHAT, AiCapability.CODING, AiCapability.FILES, AiCapability.REASONING)
        )
    ),
    QWEN3_CODER_FLASH(
        AiModelSpec(
            key = "qwen3_coder_flash",
            providerType = AiProviderType.QWEN,
            modelId = "qwen3-coder-flash",
            label = "Qwen3 Coder Flash",
            description = "Fast code generation",
            category = "Qwen Coder",
            capabilities = setOf(AiCapability.CHAT, AiCapability.CODING, AiCapability.FILES)
        )
    ),
    QWEN3_VL_PLUS(
        AiModelSpec(
            key = "qwen3_vl_plus",
            providerType = AiProviderType.QWEN,
            modelId = "qwen3-vl-plus",
            label = "Qwen3 VL Plus",
            description = "High-res vision",
            category = "Qwen Vision",
            capabilities = setOf(AiCapability.CHAT, AiCapability.IMAGE_INPUT, AiCapability.FILES)
        )
    ),
    QWEN3_VL_FLASH(
        AiModelSpec(
            key = "qwen3_vl_flash",
            providerType = AiProviderType.QWEN,
            modelId = "qwen3-vl-flash",
            label = "Qwen3 VL Flash",
            description = "Fast vision",
            category = "Qwen Vision",
            capabilities = setOf(AiCapability.CHAT, AiCapability.IMAGE_INPUT, AiCapability.FILES)
        )
    ),
    QWEN_VL_PLUS(
        AiModelSpec(
            key = "qwen_vl_plus",
            providerType = AiProviderType.QWEN,
            modelId = "qwen-vl-plus",
            label = "Qwen VL Plus",
            description = "Vision + long text",
            category = "Qwen Vision",
            capabilities = setOf(AiCapability.CHAT, AiCapability.IMAGE_INPUT, AiCapability.FILES, AiCapability.LONG_CONTEXT)
        )
    ),
    QWEN_VL_MAX(
        AiModelSpec(
            key = "qwen_vl_max",
            providerType = AiProviderType.QWEN,
            modelId = "qwen-vl-max",
            label = "Qwen VL Max",
            description = "Best vision model",
            category = "Qwen Vision",
            capabilities = setOf(AiCapability.CHAT, AiCapability.IMAGE_INPUT, AiCapability.FILES, AiCapability.REASONING)
        )
    ),
    QWEN_OCR(
        AiModelSpec(
            key = "qwen_ocr",
            providerType = AiProviderType.QWEN,
            modelId = "qwen-ocr-latest",
            label = "Qwen OCR",
            description = "Text extraction from images",
            category = "Qwen Vision",
            capabilities = setOf(AiCapability.CHAT, AiCapability.IMAGE_INPUT, AiCapability.FILES)
        )
    ),
    QWQ_PLUS(
        AiModelSpec(
            key = "qwq_plus",
            providerType = AiProviderType.QWEN,
            modelId = "qwq-plus",
            label = "QwQ Plus",
            description = "Deep reasoning",
            category = "Qwen Reasoning",
            capabilities = setOf(AiCapability.CHAT, AiCapability.FILES, AiCapability.REASONING)
        )
    ),
    QWEN_MATH_PLUS(
        AiModelSpec(
            key = "qwen_math_plus",
            providerType = AiProviderType.QWEN,
            modelId = "qwen-math-plus",
            label = "Qwen Math Plus",
            description = "Math problem solving",
            category = "Qwen Reasoning",
            capabilities = setOf(AiCapability.CHAT, AiCapability.FILES, AiCapability.REASONING)
        )
    ),
    QWEN_MATH_TURBO(
        AiModelSpec(
            key = "qwen_math_turbo",
            providerType = AiProviderType.QWEN,
            modelId = "qwen-math-turbo",
            label = "Qwen Math Turbo",
            description = "Fast math",
            category = "Qwen Reasoning",
            capabilities = setOf(AiCapability.CHAT, AiCapability.FILES, AiCapability.REASONING)
        )
    ),
    QWEN3_235B(
        AiModelSpec(
            key = "qwen3_235b",
            providerType = AiProviderType.QWEN,
            modelId = "qwen3-235b-a22b",
            label = "Qwen3 235B-A22B",
            description = "Largest open MoE",
            category = "Qwen Open-Source",
            capabilities = setOf(AiCapability.CHAT, AiCapability.CODING, AiCapability.FILES)
        )
    ),
    QWEN3_32B(
        AiModelSpec(
            key = "qwen3_32b",
            providerType = AiProviderType.QWEN,
            modelId = "qwen3-32b",
            label = "Qwen3 32B",
            description = "Dense 32B",
            category = "Qwen Open-Source",
            capabilities = setOf(AiCapability.CHAT, AiCapability.CODING, AiCapability.FILES)
        )
    ),
    QWEN3_14B(
        AiModelSpec(
            key = "qwen3_14b",
            providerType = AiProviderType.QWEN,
            modelId = "qwen3-14b",
            label = "Qwen3 14B",
            description = "Dense 14B",
            category = "Qwen Open-Source",
            capabilities = setOf(AiCapability.CHAT, AiCapability.CODING, AiCapability.FILES)
        )
    ),
    QWEN3_8B(
        AiModelSpec(
            key = "qwen3_8b",
            providerType = AiProviderType.QWEN,
            modelId = "qwen3-8b",
            label = "Qwen3 8B",
            description = "Dense 8B",
            category = "Qwen Open-Source",
            capabilities = setOf(AiCapability.CHAT, AiCapability.CODING, AiCapability.FILES)
        )
    ),
    QWEN3_4B(
        AiModelSpec(
            key = "qwen3_4b",
            providerType = AiProviderType.QWEN,
            modelId = "qwen3-4b",
            label = "Qwen3 4B",
            description = "Dense 4B",
            category = "Qwen Open-Source",
            capabilities = setOf(AiCapability.CHAT, AiCapability.CODING, AiCapability.FILES)
        )
    ),
    QWEN25_72B(
        AiModelSpec(
            key = "qwen25_72b",
            providerType = AiProviderType.QWEN,
            modelId = "qwen2.5-72b-instruct",
            label = "Qwen2.5 72B",
            description = "Large dense",
            category = "Qwen Open-Source",
            capabilities = setOf(AiCapability.CHAT, AiCapability.CODING, AiCapability.FILES, AiCapability.LONG_CONTEXT)
        )
    ),
    QWEN25_32B(
        AiModelSpec(
            key = "qwen25_32b",
            providerType = AiProviderType.QWEN,
            modelId = "qwen2.5-32b-instruct",
            label = "Qwen2.5 32B",
            description = "Medium dense",
            category = "Qwen Open-Source",
            capabilities = setOf(AiCapability.CHAT, AiCapability.CODING, AiCapability.FILES)
        )
    );

    val label: String get() = spec.label
    val modelId: String get() = spec.modelId
    val supportsVision: Boolean get() = spec.supportsVision
    val desc: String get() = spec.description
    val supportsFiles: Boolean get() = spec.supportsFiles
    val category: String get() = spec.category
    val vendor: AiVendor get() = spec.providerType.vendor
    val providerType: AiProviderType get() = spec.providerType
    val isGemini: Boolean get() = providerType == AiProviderType.GEMINI
    val isQwen: Boolean get() = providerType == AiProviderType.QWEN
    val supportsCoding: Boolean get() = spec.supportsCoding
    val supportsImageGeneration: Boolean get() = spec.supportsImageGeneration
    val supportsVideoGeneration: Boolean get() = spec.supportsVideoGeneration

    companion object {
        fun fromStoredValue(value: String?): AiProvider? {
            if (value.isNullOrBlank()) return null
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                ?: entries.firstOrNull { it.modelId.equals(value, ignoreCase = true) }
                ?: entries.firstOrNull { it.spec.key.equals(value, ignoreCase = true) }
        }
    }
}

object AiModelCatalog {
    val allModels: List<AiModelSpec> = AiProvider.entries.map { it.spec } + listOf(
        AiModelSpec(
            key = "openai_gpt5",
            providerType = AiProviderType.OPENAI,
            modelId = "gpt-5",
            label = "GPT-5",
            description = "General-purpose flagship",
            category = "OpenAI",
            capabilities = setOf(AiCapability.CHAT, AiCapability.CODING, AiCapability.IMAGE_INPUT, AiCapability.FILES, AiCapability.REASONING)
        ),
        AiModelSpec(
            key = "openai_gpt4o",
            providerType = AiProviderType.OPENAI,
            modelId = "gpt-4o",
            label = "GPT-4o",
            description = "Multimodal flagship",
            category = "OpenAI",
            capabilities = setOf(AiCapability.CHAT, AiCapability.CODING, AiCapability.IMAGE_INPUT, AiCapability.FILES)
        ),
        AiModelSpec(
            key = "xai_grok_2",
            providerType = AiProviderType.XAI,
            modelId = "grok-2-latest",
            label = "Grok 2",
            description = "xAI assistant model",
            category = "xAI",
            capabilities = setOf(AiCapability.CHAT, AiCapability.CODING, AiCapability.IMAGE_INPUT, AiCapability.FILES)
        ),
        AiModelSpec(
            key = "kimi_k2",
            providerType = AiProviderType.KIMI,
            modelId = "kimi-k2-0905-preview",
            label = "Kimi K2",
            description = "Long-context Moonshot model",
            category = "Kimi",
            capabilities = setOf(AiCapability.CHAT, AiCapability.CODING, AiCapability.FILES, AiCapability.LONG_CONTEXT)
        )
    )

    fun byProvider(providerType: AiProviderType): List<AiModelSpec> = allModels.filter { it.providerType == providerType }
    fun findById(modelId: String?): AiModelSpec? = allModels.firstOrNull { it.modelId == modelId }
}

data class AiProviderConfig(
    val providerType: AiProviderType,
    val apiKey: String = "",
    val baseUrl: String = "",
    val region: String = "",
    val enabled: Boolean = false,
    val defaultChatModelId: String = "",
    val defaultCodingModelId: String = "",
    val defaultImageModelId: String = "",
    val defaultVideoModelId: String = "",
    val extra: Map<String, String> = emptyMap()
) {
    val resolvedBaseUrl: String get() = baseUrl.ifBlank { providerType.defaultBaseUrl }
}

object AiConfigStore {
    private const val PREFS = "ai_provider_prefs"
    private const val LEGACY_PREFS = "gemini_prefs"
    private const val LEGACY_KEY_GEMINI = "api_key"
    private const val LEGACY_KEY_PROXY = "proxy_url"
    private const val LEGACY_KEY_QWEN = "qwen_api_key"
    private const val LEGACY_KEY_QWEN_REGION = "qwen_region"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun legacyPrefs(context: Context) = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
    private fun keyPrefix(providerType: AiProviderType) = "provider_${providerType.storageKey}_"

    private fun defaultRegion(providerType: AiProviderType): String = when (providerType) {
        AiProviderType.QWEN -> "intl"
        else -> ""
    }

    private fun legacyConfig(context: Context, providerType: AiProviderType): AiProviderConfig = when (providerType) {
        AiProviderType.GEMINI -> AiProviderConfig(
            providerType = providerType,
            apiKey = legacyPrefs(context).getString(LEGACY_KEY_GEMINI, "") ?: "",
            baseUrl = legacyPrefs(context).getString(LEGACY_KEY_PROXY, "") ?: "",
            enabled = (legacyPrefs(context).getString(LEGACY_KEY_GEMINI, "") ?: "").isNotBlank()
        )
        AiProviderType.QWEN -> AiProviderConfig(
            providerType = providerType,
            apiKey = legacyPrefs(context).getString(LEGACY_KEY_QWEN, "") ?: "",
            region = legacyPrefs(context).getString(LEGACY_KEY_QWEN_REGION, "intl") ?: "intl",
            enabled = (legacyPrefs(context).getString(LEGACY_KEY_QWEN, "") ?: "").isNotBlank()
        )
        else -> AiProviderConfig(providerType = providerType)
    }

    fun getConfig(context: Context, providerType: AiProviderType): AiProviderConfig {
        val pref = prefs(context)
        val prefix = keyPrefix(providerType)
        val storedKey = pref.getString(prefix + "api_key", null)
        val storedBaseUrl = pref.getString(prefix + "base_url", null)
        val storedRegion = pref.getString(prefix + "region", null)
        val enabled = pref.getBoolean(prefix + "enabled", false)
        if (storedKey == null && storedBaseUrl == null && storedRegion == null && !pref.contains(prefix + "enabled")) {
            return legacyConfig(context, providerType)
        }
        return AiProviderConfig(
            providerType = providerType,
            apiKey = storedKey ?: "",
            baseUrl = storedBaseUrl ?: "",
            region = storedRegion ?: defaultRegion(providerType),
            enabled = enabled || (storedKey?.isNotBlank() == true),
            defaultChatModelId = pref.getString(prefix + "default_chat_model", "") ?: "",
            defaultCodingModelId = pref.getString(prefix + "default_coding_model", "") ?: "",
            defaultImageModelId = pref.getString(prefix + "default_image_model", "") ?: "",
            defaultVideoModelId = pref.getString(prefix + "default_video_model", "") ?: ""
        )
    }

    fun saveConfig(context: Context, config: AiProviderConfig) {
        val prefix = keyPrefix(config.providerType)
        prefs(context).edit()
            .putString(prefix + "api_key", config.apiKey.trim())
            .putString(prefix + "base_url", config.baseUrl.trim().trimEnd('/'))
            .putString(prefix + "region", config.region.ifBlank { defaultRegion(config.providerType) })
            .putBoolean(prefix + "enabled", config.enabled || config.apiKey.isNotBlank())
            .putString(prefix + "default_chat_model", config.defaultChatModelId)
            .putString(prefix + "default_coding_model", config.defaultCodingModelId)
            .putString(prefix + "default_image_model", config.defaultImageModelId)
            .putString(prefix + "default_video_model", config.defaultVideoModelId)
            .apply()
    }

    fun hasApiKey(context: Context, providerType: AiProviderType): Boolean = getConfig(context, providerType).apiKey.isNotBlank()
    fun getApiKey(context: Context, providerType: AiProviderType): String = getConfig(context, providerType).apiKey
    fun saveApiKey(context: Context, providerType: AiProviderType, apiKey: String) = saveConfig(context, getConfig(context, providerType).copy(apiKey = apiKey.trim(), enabled = apiKey.isNotBlank()))
    fun getBaseUrl(context: Context, providerType: AiProviderType): String = getConfig(context, providerType).baseUrl
    fun saveBaseUrl(context: Context, providerType: AiProviderType, baseUrl: String) = saveConfig(context, getConfig(context, providerType).copy(baseUrl = baseUrl.trim().trimEnd('/')))
    fun getRegion(context: Context, providerType: AiProviderType): String = getConfig(context, providerType).region.ifBlank { defaultRegion(providerType) }
    fun saveRegion(context: Context, providerType: AiProviderType, region: String) = saveConfig(context, getConfig(context, providerType).copy(region = region))
    fun saveDefaultChatModel(context: Context, providerType: AiProviderType, modelId: String) = saveConfig(context, getConfig(context, providerType).copy(defaultChatModelId = modelId))
    fun saveDefaultCodingModel(context: Context, providerType: AiProviderType, modelId: String) = saveConfig(context, getConfig(context, providerType).copy(defaultCodingModelId = modelId))
    fun saveDefaultImageModel(context: Context, providerType: AiProviderType, modelId: String) = saveConfig(context, getConfig(context, providerType).copy(defaultImageModelId = modelId))
    fun saveDefaultVideoModel(context: Context, providerType: AiProviderType, modelId: String) = saveConfig(context, getConfig(context, providerType).copy(defaultVideoModelId = modelId))
}

data class MessageAttachment(
    val kind: String,
    val name: String? = null,
    val mimeType: String? = null,
    val textContent: String? = null,
    val base64Data: String? = null,
    val uri: String? = null,
    val metadata: Map<String, String> = emptyMap()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("kind", kind)
        putOpt("name", name)
        putOpt("mimeType", mimeType)
        putOpt("textContent", textContent)
        putOpt("base64Data", base64Data)
        putOpt("uri", uri)
        if (metadata.isNotEmpty()) {
            put("metadata", JSONObject().apply {
                metadata.forEach { (k, v) -> put(k, v) }
            })
        }
    }

    companion object {
        fun fromJson(json: JSONObject): MessageAttachment = MessageAttachment(
            kind = json.optString("kind", "file"),
            name = json.optString("name").takeIf { it.isNotBlank() },
            mimeType = json.optString("mimeType").takeIf { it.isNotBlank() },
            textContent = json.optString("textContent").takeIf { it.isNotBlank() },
            base64Data = json.optString("base64Data").takeIf { it.isNotBlank() },
            uri = json.optString("uri").takeIf { it.isNotBlank() },
            metadata = json.optJSONObject("metadata")?.let { meta ->
                meta.keys().asSequence().associateWith { key -> meta.optString(key, "") }
            } ?: emptyMap()
        )
    }
}

data class ChatMessage(
    val role: String,
    val content: String,
    val attachments: List<MessageAttachment> = emptyList(),
    val providerType: String? = null,
    val vendor: String? = null,
    val modelId: String? = null,
    val modelLabel: String? = null,
    val createdAt: Long? = null,
    val imageBase64: String? = attachments.firstOrNull { it.kind == "image" }?.base64Data,
    val fileContent: String? = attachments.firstOrNull { it.kind == "file" || it.kind == "archive" }?.textContent
) {
    companion object {
        fun legacy(
            role: String,
            content: String,
            imageBase64: String? = null,
            fileContent: String? = null,
            providerType: String? = null,
            vendor: String? = null,
            modelId: String? = null,
            modelLabel: String? = null,
            createdAt: Long? = null
        ): ChatMessage {
            val attachments = buildList {
                if (!imageBase64.isNullOrBlank()) add(MessageAttachment(kind = "image", mimeType = "image/jpeg", base64Data = imageBase64))
                if (!fileContent.isNullOrBlank()) add(MessageAttachment(kind = "file", textContent = fileContent))
            }
            return ChatMessage(role, content, attachments, providerType, vendor, modelId, modelLabel, createdAt)
        }
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("role", role)
        put("content", content)
        putOpt("providerType", providerType)
        putOpt("vendor", vendor)
        putOpt("modelId", modelId)
        putOpt("modelLabel", modelLabel)
        if (createdAt != null) put("createdAt", createdAt)
        if (attachments.isNotEmpty()) {
            put("attachments", JSONArray().apply { attachments.forEach { put(it.toJson()) } })
        }
        if (imageBase64 != null) put("imageBase64", imageBase64)
        if (fileContent != null) put("fileContent", fileContent)
    }
}

// ═══════════════════════════════════
// Legacy compatibility facade
// ═══════════════════════════════════

object GeminiKeyStore {
    fun getKey(context: Context): String = AiConfigStore.getApiKey(context, AiProviderType.GEMINI)
    fun saveKey(context: Context, key: String) = AiConfigStore.saveApiKey(context, AiProviderType.GEMINI, key)
    fun hasKey(context: Context): Boolean = AiConfigStore.hasApiKey(context, AiProviderType.GEMINI)
    fun getProxy(context: Context): String = AiConfigStore.getBaseUrl(context, AiProviderType.GEMINI)
    fun saveProxy(context: Context, url: String) = AiConfigStore.saveBaseUrl(context, AiProviderType.GEMINI, url)
    fun getQwenKey(context: Context): String = AiConfigStore.getApiKey(context, AiProviderType.QWEN)
    fun saveQwenKey(context: Context, key: String) = AiConfigStore.saveApiKey(context, AiProviderType.QWEN, key)
    fun hasQwenKey(context: Context): Boolean = AiConfigStore.hasApiKey(context, AiProviderType.QWEN)
    fun getQwenRegion(context: Context): String = AiConfigStore.getRegion(context, AiProviderType.QWEN)
    fun saveQwenRegion(context: Context, region: String) = AiConfigStore.saveRegion(context, AiProviderType.QWEN, region)
}

// ═══════════════════════════════════
// AI Manager
// ═══════════════════════════════════

object AiManager {
    private const val GEMINI_BASE = "https://generativelanguage.googleapis.com/v1beta/models"
    private const val SYSTEM_PROMPT = "You are a helpful AI assistant in the GlassFiles app — a file manager for Android. You can analyze files, code, images, and archives. Respond in the same language as the user."

    private fun qwenBaseUrl(region: String): String = when (region) {
        "cn" -> "https://dashscope.aliyuncs.com/compatible-mode/v1"
        "us" -> "https://dashscope-us.aliyuncs.com/compatible-mode/v1"
        "hk" -> "https://cn-hongkong.dashscope.aliyuncs.com/compatible-mode/v1"
        else -> "https://dashscope-intl.aliyuncs.com/compatible-mode/v1"
    }

    suspend fun chat(
        provider: AiProvider, messages: List<ChatMessage>,
        geminiKey: String = "", openRouterKey: String = "", proxyUrl: String = "",
        qwenKey: String = "", qwenRegion: String = "intl",
        onChunk: (String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        when (provider.providerType) {
            AiProviderType.GEMINI -> {
                if (geminiKey.isBlank()) throw Exception("Enter Gemini API key in AI settings")
                doChatGemini(provider.modelId, messages, geminiKey, proxyUrl, onChunk)
            }
            AiProviderType.QWEN -> {
                if (qwenKey.isBlank()) throw Exception("Enter Qwen API key in AI settings")
                doChatQwen(provider.modelId, messages, provider.supportsVision, qwenKey, qwenRegion, onChunk)
            }
            AiProviderType.OPENAI, AiProviderType.XAI, AiProviderType.KIMI -> {
                val apiKey = openRouterKey.ifBlank {
                    when (provider.providerType) {
                        AiProviderType.OPENAI -> geminiKey
                        AiProviderType.XAI -> qwenKey
                        AiProviderType.KIMI -> proxyUrl
                        else -> ""
                    }
                }
                if (apiKey.isBlank()) throw Exception("Enter ${provider.providerType.label} API key in AI settings")
                doChatOpenAiCompatible(provider, messages, apiKey, onChunk)
            }
        }
    }

    fun createGeneratedImageAttachment(prompt: String, providerType: AiProviderType, modelId: String): MessageAttachment {
        val svg = """
            <svg xmlns="http://www.w3.org/2000/svg" width="1024" height="1024" viewBox="0 0 1024 1024">
              <defs>
                <linearGradient id="bg" x1="0" y1="0" x2="1" y2="1">
                  <stop offset="0%" stop-color="#111827"/>
                  <stop offset="100%" stop-color="#1f2937"/>
                </linearGradient>
              </defs>
              <rect width="1024" height="1024" rx="36" fill="url(#bg)"/>
              <circle cx="280" cy="260" r="120" fill="#22c55e" fill-opacity="0.18"/>
              <circle cx="760" cy="760" r="170" fill="#3b82f6" fill-opacity="0.18"/>
              <text x="80" y="150" fill="#f8fafc" font-size="48" font-family="sans-serif" font-weight="700">Generated image preview</text>
              <text x="80" y="230" fill="#94a3b8" font-size="28" font-family="sans-serif">Provider: ${escapeSvg(providerType.label)}</text>
              <text x="80" y="275" fill="#94a3b8" font-size="28" font-family="sans-serif">Model: ${escapeSvg(modelId)}</text>
              <foreignObject x="80" y="340" width="864" height="540">
                <div xmlns="http://www.w3.org/1999/xhtml" style="color:#e2e8f0;font-size:34px;line-height:1.35;font-family:sans-serif;white-space:pre-wrap;">${escapeSvg(prompt)}</div>
              </foreignObject>
            </svg>
        """.trimIndent()
        val base64 = Base64.encodeToString(svg.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        return MessageAttachment(
            kind = "generated_image",
            name = "generated-${System.currentTimeMillis()}.svg",
            mimeType = "image/svg+xml",
            base64Data = base64,
            metadata = mapOf("prompt" to prompt, "providerType" to providerType.storageKey, "modelId" to modelId)
        )
    }

    fun createGeneratedVideoAttachment(prompt: String, providerType: AiProviderType, modelId: String): MessageAttachment {
        return MessageAttachment(
            kind = "generated_video",
            name = "video-request-${System.currentTimeMillis()}",
            mimeType = "video/mp4",
            textContent = "Video request prepared for: $prompt",
            metadata = mapOf("prompt" to prompt, "providerType" to providerType.storageKey, "modelId" to modelId, "status" to "placeholder")
        )
    }

    private fun doChatQwen(
        modelId: String, messages: List<ChatMessage>, supportsVision: Boolean,
        apiKey: String, region: String, onChunk: (String) -> Unit
    ): String {
        val url = "${qwenBaseUrl(region)}/chat/completions"
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $apiKey")
            doOutput = true; connectTimeout = 30000; readTimeout = 120000
        }

        val msgs = JSONArray()
        msgs.put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))

        messages.forEach { msg ->
            when {
                msg.imageBase64 != null && supportsVision -> {
                    val content = JSONArray()
                    if (msg.content.isNotBlank()) content.put(JSONObject().put("type", "text").put("text", msg.content))
                    msg.attachments.filter { it.kind == "image" && !it.base64Data.isNullOrBlank() }.forEach { attachment ->
                        content.put(
                            JSONObject().put("type", "image_url").put(
                                "image_url",
                                JSONObject().put("url", "data:${attachment.mimeType ?: "image/jpeg"};base64,${attachment.base64Data}")
                            )
                        )
                    }
                    msgs.put(JSONObject().put("role", msg.role).put("content", content))
                }
                msg.fileContent != null -> {
                    val fullText = if (msg.content.isNotBlank()) "${msg.content}\n\n--- File content ---\n${msg.fileContent}" else msg.fileContent
                    msgs.put(JSONObject().put("role", msg.role).put("content", fullText))
                }
                else -> msgs.put(JSONObject().put("role", msg.role).put("content", msg.content))
            }
        }

        val body = JSONObject().put("model", modelId).put("messages", msgs).put("stream", true).toString()
        conn.outputStream.use { it.write(body.toByteArray()) }

        val code = conn.responseCode
        if (code !in 200..299) {
            val err = (if (code >= 400) conn.errorStream else conn.inputStream)?.bufferedReader()?.readText()?.take(500) ?: "error $code"
            conn.disconnect()
            val detail = try { JSONObject(err).optJSONObject("error")?.optString("message", "") ?: err.take(200) } catch (_: Exception) { err.take(200) }
            throw Exception("Qwen $code: $detail")
        }

        val sb = StringBuilder()
        val reader = BufferedReader(InputStreamReader(conn.inputStream))
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            val l = line ?: continue
            if (!l.startsWith("data: ")) continue
            val data = l.removePrefix("data: ").trim()
            if (data == "[DONE]") break
            try {
                val chunk = JSONObject(data).getJSONArray("choices")
                    .getJSONObject(0).getJSONObject("delta").optString("content", "")
                if (chunk.isNotEmpty()) { sb.append(chunk); onChunk(chunk) }
            } catch (_: Exception) {}
        }
        reader.close(); conn.disconnect()
        return sb.toString()
    }

    private fun doChatGemini(
        modelId: String, messages: List<ChatMessage>,
        apiKey: String, proxyUrl: String, onChunk: (String) -> Unit
    ): String {
        val base = proxyUrl.ifBlank { GEMINI_BASE }
        val url = "$base/$modelId:streamGenerateContent?alt=sse&key=$apiKey"

        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; setRequestProperty("Content-Type", "application/json")
            doOutput = true; connectTimeout = 30000; readTimeout = 120000
        }

        val contents = JSONArray()
        messages.forEach { msg ->
            val role = if (msg.role == "assistant") "model" else "user"
            val parts = JSONArray()
            if (msg.content.isNotBlank()) parts.put(JSONObject().put("text", msg.content))
            msg.attachments.filter { it.kind == "file" || it.kind == "archive" }.forEach { attachment ->
                if (!attachment.textContent.isNullOrBlank()) {
                    val label = attachment.name?.let { "\n--- ${it} ---\n" } ?: "\n--- File content ---\n"
                    parts.put(JSONObject().put("text", "$label${attachment.textContent}"))
                }
            }
            msg.attachments.filter { it.kind == "image" && !it.base64Data.isNullOrBlank() }.forEach { attachment ->
                parts.put(
                    JSONObject().put(
                        "inlineData",
                        JSONObject().put("mimeType", attachment.mimeType ?: "image/jpeg").put("data", attachment.base64Data)
                    )
                )
            }
            contents.put(JSONObject().put("role", role).put("parts", parts))
        }

        val body = JSONObject()
            .put("contents", contents)
            .put("systemInstruction", JSONObject().put("parts",
                JSONArray().put(JSONObject().put("text", SYSTEM_PROMPT))))
            .toString()

        conn.outputStream.use { it.write(body.toByteArray()) }

        val code = conn.responseCode
        if (code !in 200..299) {
            val err = (if (code >= 400) conn.errorStream else conn.inputStream)?.bufferedReader()?.readText()?.take(800) ?: "error $code"
            conn.disconnect()
            val googleMsg = try { JSONObject(err).optJSONObject("error")?.optString("message", "") ?: "" } catch (_: Exception) { "" }
            val detail = googleMsg.ifBlank { err.take(200) }
            when (code) {
                400 -> throw Exception("400: $detail")
                403 -> throw Exception("403 Access denied: $detail")
                404 -> throw Exception("Model $modelId not found")
                429 -> throw Exception("429 Rate limit: $detail")
                else -> throw Exception("$code: $detail")
            }
        }

        val sb = StringBuilder()
        val reader = BufferedReader(InputStreamReader(conn.inputStream))
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            val l = line ?: continue
            if (!l.startsWith("data: ")) continue
            val data = l.removePrefix("data: ").trim()
            if (data == "[DONE]" || data.isEmpty()) continue
            try {
                val json = JSONObject(data)
                val candidates = json.optJSONArray("candidates") ?: continue
                if (candidates.length() == 0) continue
                val parts = candidates.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts") ?: continue
                for (i in 0 until parts.length()) {
                    val text = parts.getJSONObject(i).optString("text", "")
                    if (text.isNotEmpty()) { sb.append(text); onChunk(text) }
                }
            } catch (_: Exception) {}
        }
        reader.close(); conn.disconnect()
        return sb.toString()
    }

    private fun doChatOpenAiCompatible(
        provider: AiProvider,
        messages: List<ChatMessage>,
        apiKey: String,
        onChunk: (String) -> Unit
    ): String {
        val baseUrl = provider.providerType.defaultBaseUrl.trimEnd('/')
        val conn = (URL("$baseUrl/chat/completions").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $apiKey")
            doOutput = true
            connectTimeout = 30000
            readTimeout = 120000
        }

        val msgs = JSONArray().put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
        messages.forEach { msg ->
            val imageAttachments = msg.attachments.filter { (it.kind == "image" || it.kind == "generated_image") && !it.base64Data.isNullOrBlank() }
            val fileAttachments = msg.attachments.filter { it.kind == "file" || it.kind == "archive" }
            if (imageAttachments.isNotEmpty()) {
                val content = JSONArray()
                if (msg.content.isNotBlank()) content.put(JSONObject().put("type", "text").put("text", msg.content))
                imageAttachments.forEach { attachment ->
                    content.put(
                        JSONObject().put("type", "image_url").put(
                            "image_url",
                            JSONObject().put("url", "data:${attachment.mimeType ?: "image/jpeg"};base64,${attachment.base64Data}")
                        )
                    )
                }
                if (fileAttachments.isNotEmpty()) {
                    val combined = fileAttachments.joinToString("\n\n") { attachment ->
                        val label = attachment.name ?: "Attached file"
                        "$label\n${attachment.textContent.orEmpty()}"
                    }
                    content.put(JSONObject().put("type", "text").put("text", combined))
                }
                msgs.put(JSONObject().put("role", msg.role).put("content", content))
            } else {
                val fileText = fileAttachments.joinToString("\n\n") { attachment ->
                    val label = attachment.name ?: "Attached file"
                    "$label\n${attachment.textContent.orEmpty()}"
                }
                val mergedText = listOf(msg.content.takeIf { it.isNotBlank() }, fileText.takeIf { it.isNotBlank() }).filterNotNull().joinToString("\n\n")
                msgs.put(JSONObject().put("role", msg.role).put("content", mergedText))
            }
        }

        val body = JSONObject().put("model", provider.modelId).put("messages", msgs).put("stream", true).toString()
        conn.outputStream.use { it.write(body.toByteArray()) }

        val code = conn.responseCode
        if (code !in 200..299) {
            val err = (if (code >= 400) conn.errorStream else conn.inputStream)?.bufferedReader()?.readText()?.take(700) ?: "error $code"
            conn.disconnect()
            throw Exception("${provider.providerType.label} $code: ${err.take(220)}")
        }

        val sb = StringBuilder()
        val reader = BufferedReader(InputStreamReader(conn.inputStream))
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            val l = line ?: continue
            if (!l.startsWith("data: ")) continue
            val data = l.removePrefix("data: ").trim()
            if (data == "[DONE]" || data.isBlank()) continue
            try {
                val chunk = JSONObject(data).optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("delta")?.optString("content", "").orEmpty()
                if (chunk.isNotBlank()) {
                    sb.append(chunk)
                    onChunk(chunk)
                }
            } catch (_: Exception) {}
        }
        reader.close()
        conn.disconnect()
        return sb.toString()
    }

    private fun escapeSvg(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")

    private val TEXT_EXTENSIONS = setOf(
        "txt", "md", "json", "xml", "html", "css", "js", "ts", "kt", "java",
        "py", "c", "cpp", "h", "hpp", "cs", "swift", "go", "rs", "rb", "php",
        "sh", "bash", "zsh", "fish", "lua", "r", "scala", "dart", "jsx", "tsx",
        "vue", "svelte", "yml", "yaml", "toml", "ini", "cfg", "conf", "properties",
        "gradle", "csv", "sql", "graphql", "proto", "makefile", "dockerfile",
        "log", "env", "gitignore", "editorconfig", "prettierrc", "eslintrc",
        "tf", "hcl", "nix", "cmake", "bat", "ps1", "asm", "s", "v", "vhd",
        "tex", "bib", "rst", "adoc", "org", "wiki", "diff", "patch"
    )

    private val SKIP_EXTENSIONS = setOf("exe", "dll", "so", "bin", "apk", "aab", "dex", "class", "o", "a", "lib", "pyc", "pyd")

    fun extractZipForAi(zipPath: String, context: Context, maxFiles: Int = 60, maxCharsPerFile: Int = 6000): List<Pair<String, String>> {
        val results = mutableListOf<Pair<String, String>>()
        val tempDir = File(context.cacheDir, "ai_zip_${System.currentTimeMillis()}")
        try {
            tempDir.mkdirs()
            val zip = ZipFile(File(zipPath))
            zip.entries().asSequence()
                .filter { !it.isDirectory }
                .filter { val ext = it.name.substringAfterLast(".", "").lowercase(); ext !in SKIP_EXTENSIONS }
                .take(maxFiles)
                .forEach { entry ->
                    val ext = entry.name.substringAfterLast(".", "").lowercase()
                    val isText = ext in TEXT_EXTENSIONS || entry.name.lowercase().let { n ->
                        n.endsWith("makefile") || n.endsWith("dockerfile") || n.endsWith("jenkinsfile") || n.endsWith("vagrantfile")
                    }
                    val isImage = ext in listOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "svg")

                    when {
                        isText && entry.size < 500_000 -> {
                            try {
                                val text = zip.getInputStream(entry).bufferedReader().readText()
                                val truncated = if (text.length > maxCharsPerFile) text.take(maxCharsPerFile) + "\n...[truncated]" else text
                                results.add(Pair(entry.name, truncated))
                            } catch (_: Exception) { results.add(Pair(entry.name, "[read error]")) }
                        }
                        isImage -> results.add(Pair(entry.name, "[image: ${entry.size / 1024}KB]"))
                        else -> results.add(Pair(entry.name, "[binary: ${ext.uppercase()}, ${entry.size / 1024}KB]"))
                    }
                }
            val totalEntries = ZipFile(File(zipPath)).use { z -> z.entries().asSequence().filter { !it.isDirectory }.count() }
            if (totalEntries > maxFiles) results.add(Pair("...", "[${totalEntries - maxFiles} more files not shown]"))
            zip.close()
        } catch (e: Exception) { results.add(Pair("error", "Failed to read ZIP: ${e.message}")) }
        finally { tempDir.deleteRecursively() }
        return results
    }

    fun formatZipContents(entries: List<Pair<String, String>>): String {
        val sb = StringBuilder("Archive contents (${entries.size} files):\n\n")
        entries.forEach { (name, content) -> sb.append("=== $name ===\n$content\n\n") }
        return sb.toString()
    }

    fun encodeImage(file: File): String? = try {
        val bmp = BitmapFactory.decodeFile(file.absolutePath) ?: return null
        val maxSide = 1024
        val scaled = if (bmp.width > maxSide || bmp.height > maxSide) {
            val ratio = minOf(maxSide.toFloat() / bmp.width, maxSide.toFloat() / bmp.height)
            Bitmap.createScaledBitmap(bmp, (bmp.width * ratio).toInt(), (bmp.height * ratio).toInt(), true)
        } else bmp
        val baos = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 85, baos)
        Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
    } catch (_: Exception) { null }

    fun readFileForAi(file: File, maxChars: Int = 8000): String? = try {
        if (file.length() > 500_000) "[File too large: ${file.length() / 1024}KB]"
        else {
            val text = file.readText()
            if (text.length > maxChars) text.take(maxChars) + "\n...[truncated, ${text.length} chars]" else text
        }
    } catch (_: Exception) { null }

    fun readAnyFile(file: File, context: Context): FileReadResult {
        val ext = file.extension.lowercase()
        return when {
            ext in listOf("zip", "jar") -> {
                val entries = extractZipForAi(file.absolutePath, context)
                FileReadResult(type = "zip", textContent = formatZipContents(entries), fileName = file.name)
            }
            ext in listOf("png", "jpg", "jpeg", "gif", "webp", "bmp") -> {
                FileReadResult(type = "image", imageBase64 = encodeImage(file), fileName = file.name)
            }
            ext in SKIP_EXTENSIONS -> {
                FileReadResult(type = "binary", textContent = "[Unsupported binary: ${ext.uppercase()}, ${file.length() / 1024}KB]", fileName = file.name)
            }
            else -> FileReadResult(type = "text", textContent = readFileForAi(file), fileName = file.name)
        }
    }
}

data class FileReadResult(
    val type: String,
    val textContent: String? = null,
    val imageBase64: String? = null,
    val fileName: String = ""
)
