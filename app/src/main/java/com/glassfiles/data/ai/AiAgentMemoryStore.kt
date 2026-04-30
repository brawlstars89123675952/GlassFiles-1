package com.glassfiles.data.ai

import android.content.Context
import com.glassfiles.data.ai.models.AiMessage
import com.glassfiles.data.ai.providers.AiProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AiAgentMemoryStore {
    private const val MEMORY_DIR = "memory"
    private const val PROJECT_FILE = "project.md"
    private const val PREFERENCES_FILE = "preferences.md"
    private const val DECISIONS_FILE = "decisions.md"
    private const val MAX_CONTEXT_CHARS = 18_000
    private const val MAX_SUMMARY_CHATS = 10

    data class MemoryFile(
        val key: String,
        val label: String,
        val path: String,
        val content: String,
    )

    fun buildMemoryPrompt(context: Context, repoFullName: String): String {
        if (repoFullName.isBlank()) return ""
        ensureDefaults(context, repoFullName)
        val blocks = mutableListOf<String>()
        if (AiAgentMemoryPrefs.getUserPreferences(context)) {
            readGlobalPreferences(context).takeIf { it.isNotBlank() }?.let {
                blocks += "## User preferences\n$it"
            }
        }
        if (AiAgentMemoryPrefs.getProjectKnowledge(context)) {
            readProjectKnowledge(context, repoFullName).takeIf { it.isNotBlank() }?.let {
                blocks += "## Project knowledge\n$it"
            }
        }
        if (AiAgentMemoryPrefs.getChatSummaries(context)) {
            recentChatSummaries(context, repoFullName).takeIf { it.isNotEmpty() }?.let { summaries ->
                blocks += "## Recent chat summaries\n" + summaries.joinToString("\n\n")
            }
        }
        if (AiAgentMemoryPrefs.getSemanticSearch(context)) {
            blocks += "## Semantic memory\nSemantic search index is not available yet; no related snippets were loaded."
        }
        if (blocks.isEmpty()) return ""
        return buildString {
            appendLine("AI Agent local memory is enabled. Use it as context, but do not expose it unless the user asks.")
            append(blocks.joinToString("\n\n").take(MAX_CONTEXT_CHARS))
        }
    }

    fun editableFiles(context: Context, repoFullName: String): List<MemoryFile> {
        ensureDefaults(context, repoFullName)
        val repo = repoDir(context, repoFullName)
        return listOf(
            MemoryFile("project", "project.md", File(repo, PROJECT_FILE).absolutePath, readProjectKnowledge(context, repoFullName)),
            MemoryFile("preferences", "preferences.md", globalPreferencesFile(context).absolutePath, readGlobalPreferences(context)),
            MemoryFile("decisions", "decisions.md", File(repo, DECISIONS_FILE).absolutePath, readDecisions(context, repoFullName)),
        )
    }

    fun saveEditableFile(context: Context, repoFullName: String, key: String, content: String) {
        ensureDefaults(context, repoFullName)
        val file = when (key) {
            "project" -> File(repoDir(context, repoFullName), PROJECT_FILE)
            "preferences" -> globalPreferencesFile(context)
            "decisions" -> File(repoDir(context, repoFullName), DECISIONS_FILE)
            else -> return
        }
        file.parentFile?.mkdirs()
        file.writeText(content)
    }

    fun clearAll(context: Context) {
        memoryRoot(context).deleteRecursively()
    }

    fun saveChatFull(
        context: Context,
        repoFullName: String,
        chatId: String,
        messages: List<AiChatSessionStore.Message>,
    ) {
        if (repoFullName.isBlank() || chatId.isBlank()) return
        ensureDefaults(context, repoFullName)
        val chatDir = File(File(repoDir(context, repoFullName), "chats"), chatId).apply { mkdirs() }
        val arr = JSONArray()
        messages.forEach { message ->
            arr.put(
                JSONObject()
                    .put("role", message.role)
                    .put("content", message.content)
                    .put("isError", message.isError)
                    .apply {
                        if (message.imageBase64 != null) put("imageBase64", message.imageBase64)
                    },
            )
        }
        JSONObject()
            .put("repoFullName", repoFullName)
            .put("chatId", chatId)
            .put("updatedAt", System.currentTimeMillis())
            .put("messages", arr)
            .let { File(chatDir, "full.json").writeText(it.toString(2)) }
    }

    suspend fun summarizeAndUpdate(
        context: Context,
        repoFullName: String,
        chatId: String,
        messages: List<AiChatSessionStore.Message>,
        provider: AiProvider?,
        modelId: String,
        apiKey: String,
    ) = withContext(Dispatchers.IO) {
        if (repoFullName.isBlank() || chatId.isBlank() || messages.isEmpty()) return@withContext
        ensureDefaults(context, repoFullName)
        saveChatFull(context, repoFullName, chatId, messages)
        if (!AiAgentMemoryPrefs.getChatSummaries(context) && !AiAgentMemoryPrefs.getProjectKnowledge(context)) {
            return@withContext
        }
        val summary = generateSummary(context, messages, provider, modelId, apiKey)
        val chatDir = File(File(repoDir(context, repoFullName), "chats"), chatId).apply { mkdirs() }
        if (AiAgentMemoryPrefs.getChatSummaries(context)) {
            File(chatDir, "summary.md").writeText(summary)
        }
        if (AiAgentMemoryPrefs.getProjectKnowledge(context)) {
            updateProjectKnowledge(context, repoFullName, summary)
        }
    }

    private suspend fun generateSummary(
        context: Context,
        messages: List<AiChatSessionStore.Message>,
        provider: AiProvider?,
        modelId: String,
        apiKey: String,
    ): String {
        val prompt = buildString {
            appendLine("Summarize this conversation in 5-10 lines. Focus on:")
            appendLine("1. What was the main task/topic")
            appendLine("2. What was decided or implemented")
            appendLine("3. Open questions or unresolved issues")
            appendLine("4. Key context that future sessions should know")
            appendLine()
            messages.takeLast(80).forEach { message ->
                appendLine("--- ${message.role}")
                appendLine(message.content.take(3_000))
            }
        }
        if (provider != null && apiKey.isNotBlank() && modelId.isNotBlank()) {
            runCatching {
                buildString {
                    provider.chat(
                        context = context,
                        modelId = modelId,
                        messages = listOf(AiMessage(role = "user", content = prompt.take(24_000))),
                        apiKey = apiKey,
                        onChunk = { append(it) },
                    )
                }.trim()
            }.getOrNull()?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return fallbackSummary(messages)
    }

    private fun fallbackSummary(messages: List<AiChatSessionStore.Message>): String {
        val firstTask = messages.firstOrNull { it.role == "user" }?.content?.lineSequence()?.firstOrNull().orEmpty()
        val lastAssistant = messages.lastOrNull { it.role == "assistant" }?.content?.lineSequence()?.firstOrNull().orEmpty()
        return buildString {
            appendLine("- Main task: ${firstTask.take(180).ifBlank { "Unspecified agent task." }}")
            if (lastAssistant.isNotBlank()) appendLine("- Latest outcome: ${lastAssistant.take(180)}")
            appendLine("- Conversation had ${messages.count { it.role == "user" }} user turns and ${messages.count { it.role == "assistant" }} assistant turns.")
            appendLine("- Review the full.json transcript for exact implementation details.")
        }
    }

    private fun updateProjectKnowledge(context: Context, repoFullName: String, summary: String) {
        val file = File(repoDir(context, repoFullName), PROJECT_FILE)
        val current = file.readText()
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val bullet = "- $today: ${summary.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().removePrefix("- ").take(180)}"
        if (current.contains(bullet)) return
        val updated = current.replace(
            "## Recent changes (last 30 days)\n",
            "## Recent changes (last 30 days)\n$bullet\n",
        )
        file.writeText(updated)
    }

    private fun recentChatSummaries(context: Context, repoFullName: String): List<String> {
        val chatsDir = File(repoDir(context, repoFullName), "chats")
        if (!chatsDir.exists()) return emptyList()
        return chatsDir.listFiles()
            ?.asSequence()
            ?.mapNotNull { dir -> File(dir, "summary.md").takeIf { it.exists() } }
            ?.sortedByDescending { it.lastModified() }
            ?.take(MAX_SUMMARY_CHATS)
            ?.map { file -> "### ${file.parentFile?.name.orEmpty()}\n${file.readText().take(2_000)}" }
            ?.toList()
            ?: emptyList()
    }

    private fun ensureDefaults(context: Context, repoFullName: String) {
        memoryRoot(context).mkdirs()
        val repo = repoDir(context, repoFullName).apply { mkdirs() }
        File(repo, "chats").mkdirs()
        val project = File(repo, PROJECT_FILE)
        if (!project.exists()) {
            project.writeText(defaultProject(repoFullName.substringAfter('/').ifBlank { repoFullName }))
        }
        val decisions = File(repo, DECISIONS_FILE)
        if (!decisions.exists()) {
            decisions.writeText("# Decisions\n\n")
        }
        val preferences = globalPreferencesFile(context)
        if (!preferences.exists()) {
            preferences.parentFile?.mkdirs()
            preferences.writeText(defaultPreferences())
        }
    }

    private fun readProjectKnowledge(context: Context, repoFullName: String): String =
        File(repoDir(context, repoFullName), PROJECT_FILE).takeIf { it.exists() }?.readText().orEmpty()

    private fun readGlobalPreferences(context: Context): String =
        globalPreferencesFile(context).takeIf { it.exists() }?.readText().orEmpty()

    private fun readDecisions(context: Context, repoFullName: String): String =
        File(repoDir(context, repoFullName), DECISIONS_FILE).takeIf { it.exists() }?.readText().orEmpty()

    private fun repoDir(context: Context, repoFullName: String): File {
        val safe = repoFullName.replace("/", "__").replace(Regex("[^A-Za-z0-9_.-]"), "_")
        return File(memoryRoot(context), safe)
    }

    private fun globalPreferencesFile(context: Context): File =
        File(memoryRoot(context), PREFERENCES_FILE)

    private fun memoryRoot(context: Context): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, MEMORY_DIR)

    private fun defaultProject(name: String): String = """
        # Project: $name

        ## Architecture
        Not documented yet.

        ## Conventions
        Not documented yet.

        ## Recent changes (last 30 days)

        ## Known issues

        ## Open tasks

    """.trimIndent()

    private fun defaultPreferences(): String = """
        # User preferences

        ## Communication style

        ## Code style

        ## Workflow

        ## Tech preferences

    """.trimIndent()
}
