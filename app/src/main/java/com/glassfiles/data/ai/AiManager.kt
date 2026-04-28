package com.glassfiles.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.glassfiles.data.ai.models.AiMessage
import com.glassfiles.data.ai.models.AiProviderId
import com.glassfiles.data.ai.providers.AiProviders
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipFile

// ═══════════════════════════════════
// Legacy enum kept for backwards compatibility
// ═══════════════════════════════════
//
// The old hardcoded `AiProvider` enum is the sole option-set the existing UI
// (AiChatScreen) compiles against. We keep it as-is so this PR is a pure
// data-layer refactor — the dispatcher now routes every entry through the new
// per-provider implementations rather than running its own HTTP code.
//
// Subsequent PRs replace this enum with [ModelRegistry] + [AiModel] in the UI.

enum class AiProvider(
    val label: String, val modelId: String, val supportsVision: Boolean,
    val desc: String, val isGemini: Boolean = false, val isQwen: Boolean = false,
    val supportsFiles: Boolean = false, val category: String = ""
) {
    // ── Gemini ──
    GEMINI_FLASH("Gemini 2.5 Flash", "gemini-2.5-flash", true, "Fast, efficient", isGemini = true, category = "Gemini"),
    GEMINI_PRO("Gemini 2.5 Pro", "gemini-2.5-pro", true, "Most capable", isGemini = true, category = "Gemini"),
    GEMINI_FLASH_LITE("Gemini 2.5 Flash-Lite", "gemini-2.5-flash-lite", true, "Lightweight", isGemini = true, category = "Gemini"),
    GEMINI_3_FLASH("Gemini 3 Flash", "gemini-3-flash-preview", true, "Next-gen fast", isGemini = true, category = "Gemini"),
    GEMINI_31_PRO("Gemini 3.1 Pro", "gemini-3.1-pro-preview", true, "Next-gen pro", isGemini = true, category = "Gemini"),

    // ── Qwen Commercial ──
    QWEN3_MAX("Qwen3 Max", "qwen3-max", false, "Best Qwen3, complex tasks", isQwen = true, supportsFiles = true, category = "Qwen Commercial"),
    QWEN35_PLUS("Qwen3.5 Plus", "qwen3.5-plus", true, "Text + Vision, balanced", isQwen = true, supportsFiles = true, category = "Qwen Commercial"),
    QWEN_PLUS("Qwen Plus", "qwen-plus", false, "Smart, balanced", isQwen = true, supportsFiles = true, category = "Qwen Commercial"),
    QWEN35_FLASH("Qwen3.5 Flash", "qwen3.5-flash", true, "Fast, multimodal", isQwen = true, supportsFiles = true, category = "Qwen Commercial"),
    QWEN_FLASH("Qwen Flash", "qwen-flash", false, "Fastest, cheapest", isQwen = true, supportsFiles = true, category = "Qwen Commercial"),
    QWEN_TURBO("Qwen Turbo", "qwen-turbo", false, "Fast, cost-effective", isQwen = true, supportsFiles = true, category = "Qwen Commercial"),
    QWEN_LONG("Qwen Long", "qwen-long", false, "10M context window", isQwen = true, supportsFiles = true, category = "Qwen Commercial"),

    // ── Qwen Coder ──
    QWEN3_CODER_PLUS("Qwen3 Coder Plus", "qwen3-coder-plus", false, "Code + tool calling", isQwen = true, supportsFiles = true, category = "Qwen Coder"),
    QWEN3_CODER_FLASH("Qwen3 Coder Flash", "qwen3-coder-flash", false, "Fast code generation", isQwen = true, supportsFiles = true, category = "Qwen Coder"),

    // ── Qwen Vision ──
    QWEN3_VL_PLUS("Qwen3 VL Plus", "qwen3-vl-plus", true, "High-res vision", isQwen = true, supportsFiles = true, category = "Qwen Vision"),
    QWEN3_VL_FLASH("Qwen3 VL Flash", "qwen3-vl-flash", true, "Fast vision", isQwen = true, supportsFiles = true, category = "Qwen Vision"),
    QWEN_VL_PLUS("Qwen VL Plus", "qwen-vl-plus", true, "Vision + long text", isQwen = true, supportsFiles = true, category = "Qwen Vision"),
    QWEN_VL_MAX("Qwen VL Max", "qwen-vl-max", true, "Best vision model", isQwen = true, supportsFiles = true, category = "Qwen Vision"),
    QWEN_OCR("Qwen OCR", "qwen-ocr-latest", true, "Text extraction from images", isQwen = true, supportsFiles = true, category = "Qwen Vision"),

    // ── Qwen Reasoning ──
    QWQ_PLUS("QwQ Plus", "qwq-plus", false, "Deep reasoning", isQwen = true, supportsFiles = true, category = "Qwen Reasoning"),
    QWEN_MATH_PLUS("Qwen Math Plus", "qwen-math-plus", false, "Math problem solving", isQwen = true, supportsFiles = true, category = "Qwen Reasoning"),
    QWEN_MATH_TURBO("Qwen Math Turbo", "qwen-math-turbo", false, "Fast math", isQwen = true, supportsFiles = true, category = "Qwen Reasoning"),

    // ── Qwen Open-Source ──
    QWEN3_235B("Qwen3 235B-A22B", "qwen3-235b-a22b", false, "Largest open MoE", isQwen = true, supportsFiles = true, category = "Qwen Open-Source"),
    QWEN3_32B("Qwen3 32B", "qwen3-32b", false, "Dense 32B", isQwen = true, supportsFiles = true, category = "Qwen Open-Source"),
    QWEN3_14B("Qwen3 14B", "qwen3-14b", false, "Dense 14B", isQwen = true, supportsFiles = true, category = "Qwen Open-Source"),
    QWEN3_8B("Qwen3 8B", "qwen3-8b", false, "Dense 8B", isQwen = true, supportsFiles = true, category = "Qwen Open-Source"),
    QWEN3_4B("Qwen3 4B", "qwen3-4b", false, "Dense 4B", isQwen = true, supportsFiles = true, category = "Qwen Open-Source"),
    QWEN25_72B("Qwen2.5 72B", "qwen2.5-72b-instruct", false, "Large dense", isQwen = true, supportsFiles = true, category = "Qwen Open-Source"),
    QWEN25_32B("Qwen2.5 32B", "qwen2.5-32b-instruct", false, "Medium dense", isQwen = true, supportsFiles = true, category = "Qwen Open-Source"),
}

/**
 * [ChatMessage] is now an alias of [AiMessage] — both struct-types are
 * structurally identical. New code should reach for [AiMessage] directly.
 */
typealias ChatMessage = AiMessage

// ═══════════════════════════════════
// Legacy API-key store (delegates to AiKeyStore)
// ═══════════════════════════════════

object GeminiKeyStore {
    fun getKey(context: Context): String = AiKeyStore.getKey(context, AiProviderId.GOOGLE)
    fun saveKey(context: Context, key: String) = AiKeyStore.saveKey(context, AiProviderId.GOOGLE, key)
    fun hasKey(context: Context): Boolean = AiKeyStore.hasKey(context, AiProviderId.GOOGLE)

    fun getProxy(context: Context): String = AiKeyStore.getGeminiProxy(context)
    fun saveProxy(context: Context, url: String) = AiKeyStore.saveGeminiProxy(context, url)

    fun getQwenKey(context: Context): String = AiKeyStore.getKey(context, AiProviderId.ALIBABA)
    fun saveQwenKey(context: Context, key: String) = AiKeyStore.saveKey(context, AiProviderId.ALIBABA, key)
    fun hasQwenKey(context: Context): Boolean = AiKeyStore.hasKey(context, AiProviderId.ALIBABA)

    fun getQwenRegion(context: Context): String = AiKeyStore.getQwenRegion(context)
    fun saveQwenRegion(context: Context, region: String) = AiKeyStore.saveQwenRegion(context, region)
}

// ═══════════════════════════════════
// AI Manager — facade that dispatches to per-provider implementations
// ═══════════════════════════════════

object AiManager {

    /**
     * Streaming chat. Routes [provider] to the matching [AiProvider] in the
     * `providers/` package. The legacy `openRouterKey` parameter is accepted but
     * unused by the current providers; OpenRouter is exposed via [AiProviderId.OPENROUTER]
     * with its own key stored in [AiKeyStore].
     */
    suspend fun chat(
        provider: AiProvider,
        messages: List<ChatMessage>,
        geminiKey: String = "",
        @Suppress("UNUSED_PARAMETER") openRouterKey: String = "",
        @Suppress("UNUSED_PARAMETER") proxyUrl: String = "",
        qwenKey: String = "",
        @Suppress("UNUSED_PARAMETER") qwenRegion: String = "intl",
        onChunk: (String) -> Unit
    ): String {
        // Map legacy enum → new provider id + raw model id.
        val (providerId, apiKey) = when {
            provider.isGemini -> AiProviderId.GOOGLE to geminiKey
            provider.isQwen -> AiProviderId.ALIBABA to qwenKey
            else -> throw IllegalArgumentException("Unknown legacy provider $provider")
        }
        if (apiKey.isBlank()) {
            throw IllegalStateException("Enter the ${providerId.displayName} API key in AI settings")
        }
        // We need a Context to resolve provider settings (Qwen region, Gemini proxy).
        // Callers (the UI) already pre-read those values into the parameters above,
        // but the new providers reach into AiKeyStore directly. The first call from
        // the UI passes the same SharedPreferences — context is implicit there.
        val context = LegacyContextHolder.context
            ?: throw IllegalStateException("AiManager not initialised: call AiManager.init(context) first")

        // The legacy enum carries a per-model `supportsVision` flag. The old code
        // silently dropped image attachments when sending to non-vision Qwen models;
        // preserve that behaviour so we don't surface DashScope errors back to the
        // user when they attach an image to e.g. Qwen-Plus.
        val safeMessages = if (provider.supportsVision) messages else messages.map { msg ->
            if (msg.imageBase64 != null) msg.copy(imageBase64 = null) else msg
        }

        return AiProviders.get(providerId)
            .chat(context, provider.modelId, safeMessages, apiKey, onChunk)
    }

    /**
     * One-time initialisation so the legacy [chat] entry point can reach a
     * Context. Application code calls this from `onCreate`.
     */
    fun init(context: Context) {
        LegacyContextHolder.context = context.applicationContext
    }

    // ═══════════════════════════════════
    // ZIP / file utilities (unchanged)
    // ═══════════════════════════════════

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

private object LegacyContextHolder {
    @Volatile
    var context: Context? = null
}
