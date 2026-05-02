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
    private const val MEMORY_INDEX_FILE = "MEMORY.md"
    private const val TOPICS_DIR = "topics"
    /** Per-repo working memory file (BUGS_FIX.md Section 3). */
    const val WORKING_MEMORY_FILE: String = "working_memory.md"
    /** Hard cap on the working-memory blob we paste into the system prompt. */
    private const val WORKING_MEMORY_PROMPT_BUDGET = 2_000
    private const val MAX_CONTEXT_CHARS = 18_000
    private const val MAX_SUMMARY_CHATS = 5

    data class MemoryFile(
        val key: String,
        val label: String,
        val path: String,
        val content: String,
    )

    data class ChatRecord(
        val id: String,
        val title: String,
        val preview: String,
        val updatedAt: Long,
        val messages: Int,
        val corrupted: Boolean = false,
        val error: String = "",
    )

    data class ChatSearchResult(
        val chatId: String,
        val title: String,
        val snippet: String,
        val updatedAt: Long,
    )

    data class LoadedChat(
        val chatId: String,
        val repoFullName: String,
        val branch: String,
        val providerId: String,
        val modelId: String,
        val createdAt: Long,
        val updatedAt: Long,
        val messages: List<AiChatSessionStore.Message>,
    )

    data class MemoryIndexSnapshot(
        val facts: List<AiAgentMemoryIndex.Fact>,
        val searchResults: List<AiAgentMemoryIndex.SearchResult> = emptyList(),
    )

    fun buildMemoryPrompt(context: Context, repoFullName: String): String {
        if (repoFullName.isBlank()) return ""
        ensureDefaults(context, repoFullName)
        AiAgentMemoryIndex.rebuildIfStale(context, repoFullName)
        val blocks = mutableListOf<String>()
        readMemoryIndex(context, repoFullName).takeIf { it.isNotBlank() }?.let {
            blocks += "## Memory index\n$it"
        }
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
                blocks += "## Recent conversations\n" + summaries.joinToString("\n\n")
            }
        }
        structuredMemoryFacts(context, repoFullName).takeIf { it.isNotEmpty() }?.let { facts ->
            blocks += "## Structured memory facts\n" + facts.joinToString("\n")
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
            MemoryFile("working", WORKING_MEMORY_FILE, File(repo, WORKING_MEMORY_FILE).absolutePath, readWorkingMemory(context, repoFullName)),
        )
    }

    fun saveEditableFile(context: Context, repoFullName: String, key: String, content: String) {
        ensureDefaults(context, repoFullName)
        val file = when (key) {
            "project" -> File(repoDir(context, repoFullName), PROJECT_FILE)
            "preferences" -> globalPreferencesFile(context)
            "decisions" -> File(repoDir(context, repoFullName), DECISIONS_FILE)
            "working" -> File(repoDir(context, repoFullName), WORKING_MEMORY_FILE)
            else -> return
        }
        file.parentFile?.mkdirs()
        file.writeText(content)
        AiAgentMemoryIndex.markDirtyAndRebuild(context, repoFullName)
    }

    // region Working memory (BUGS_FIX.md Section 3)
    /**
     * Read the per-repo working_memory.md verbatim, returning empty string if
     * the file does not exist. We don't [ensureDefaults] here because callers
     * that just want to display the blob don't care whether the agent has
     * written anything yet.
     */
    fun readWorkingMemory(context: Context, repoFullName: String): String {
        if (repoFullName.isBlank()) return ""
        val file = File(repoDir(context, repoFullName), WORKING_MEMORY_FILE)
        return if (file.isFile) file.readText() else ""
    }

    /**
     * Overwrite working_memory.md with [content]. Used by the agent
     * (memory_write tool, normally) and by the manual "clear working memory"
     * chat command.
     */
    fun writeWorkingMemory(context: Context, repoFullName: String, content: String) {
        if (repoFullName.isBlank()) return
        ensureDefaults(context, repoFullName)
        val file = File(repoDir(context, repoFullName), WORKING_MEMORY_FILE)
        file.parentFile?.mkdirs()
        file.writeText(content)
        AiAgentMemoryIndex.markDirtyAndRebuild(context, repoFullName)
    }

    /**
     * Delete working_memory.md. No-op if it doesn't exist. The memory index
     * is rebuilt regardless so any cached snippets disappear.
     */
    fun clearWorkingMemory(context: Context, repoFullName: String) {
        if (repoFullName.isBlank()) return
        val file = File(repoDir(context, repoFullName), WORKING_MEMORY_FILE)
        if (file.isFile) file.delete()
        AiAgentMemoryIndex.markDirtyAndRebuild(context, repoFullName)
    }

    /**
     * Build the snippet that gets prepended to the system prompt. Empty if
     * the file is missing OR contains only whitespace OR working memory is
     * disabled in prefs (caller checks the toggle).
     *
     * If the file is larger than [WORKING_MEMORY_PROMPT_BUDGET] chars we
     * keep the head (active task / currently-editing entries land at the
     * top) and tail-trim the body. We never paste the whole 4-5 KB blob
     * into the system prompt — see BUGS_FIX.md "Не подмешивать ВСЁ working
     * memory если оно >2KB — обрезать до самых свежих entries".
     */
    fun workingMemoryPrompt(context: Context, repoFullName: String): String {
        val raw = readWorkingMemory(context, repoFullName).trim()
        if (raw.isEmpty()) return ""
        val trimmed = if (raw.length <= WORKING_MEMORY_PROMPT_BUDGET) raw
        else raw.substring(0, WORKING_MEMORY_PROMPT_BUDGET).trimEnd() + "\n…"
        return buildString {
            appendLine("## Working memory")
            append(trimmed)
        }
    }

    /**
     * Count "Currently editing" entries in working_memory.md by counting
     * level-3 (`### `) markdown headings inside the section. Used by the
     * topbar `▸ N files` indicator (BUGS_FIX.md "$0.50 / 50k tok · ▸ 3
     * files"). Returns 0 if the file is missing or the section is absent.
     */
    fun workingMemoryActiveFileCount(context: Context, repoFullName: String): Int {
        val raw = readWorkingMemory(context, repoFullName)
        if (raw.isBlank()) return 0
        val lines = raw.lines()
        var inSection = false
        var count = 0
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("## ")) {
                inSection = trimmed.equals("## Currently editing", ignoreCase = true)
                continue
            }
            if (inSection && trimmed.startsWith("### ")) count++
        }
        return count
    }
    // endregion

    fun clearAll(context: Context) {
        memoryRoot(context).deleteRecursively()
    }

    fun rebuildIndex(context: Context, repoFullName: String) {
        ensureDefaults(context, repoFullName)
        refreshMemoryIndex(context, repoFullName)
        AiAgentMemoryIndex.rebuildRepo(context, repoFullName)
    }

    fun memoryIndexSnapshot(context: Context, repoFullName: String, query: String = ""): MemoryIndexSnapshot {
        if (repoFullName.isBlank()) return MemoryIndexSnapshot(emptyList())
        ensureDefaults(context, repoFullName)
        AiAgentMemoryIndex.rebuildIfStale(context, repoFullName)
        return MemoryIndexSnapshot(
            facts = AiAgentMemoryIndex.facts(context, repoFullName),
            searchResults = if (query.isBlank()) emptyList() else AiAgentMemoryIndex.search(context, repoFullName, query),
        )
    }

    fun toolRead(context: Context, repoFullName: String, path: String): String {
        ensureDefaults(context, repoFullName)
        val file = resolveToolPath(context, repoFullName, path, mustExist = true)
        if (!file.isFile) throw IllegalArgumentException("memory_read: file does not exist: $path")
        val content = file.readText()
        return buildString {
            appendLine(memoryToolOutputHeader("memory_read", normalizeToolInput(path)))
            appendLine("  → ${formatBytes(content.toByteArray().size)}, ${content.lineCount()} lines")
            appendLine()
            append(content)
        }
    }

    fun toolWrite(context: Context, repoFullName: String, path: String, content: String): String {
        ensureDefaults(context, repoFullName)
        val file = resolveToolPath(context, repoFullName, path, mustExist = false)
        file.parentFile?.mkdirs()
        file.writeText(content)
        AiAgentMemoryIndex.markDirtyAndRebuild(context, repoFullName)
        return buildString {
            appendLine(memoryToolOutputHeader("memory_write", normalizeToolInput(path)))
            append("  → wrote ${formatBytes(content.toByteArray().size)}, ${content.lineCount()} lines")
        }
    }

    fun toolAppend(context: Context, repoFullName: String, path: String, content: String): String {
        ensureDefaults(context, repoFullName)
        val file = resolveToolPath(context, repoFullName, path, mustExist = false)
        file.parentFile?.mkdirs()
        file.appendText(content)
        AiAgentMemoryIndex.markDirtyAndRebuild(context, repoFullName)
        return buildString {
            appendLine(memoryToolOutputHeader("memory_append", normalizeToolInput(path)))
            append("  → appended ${formatBytes(content.toByteArray().size)}, ${content.lineCount()} lines")
        }
    }

    fun toolList(context: Context, repoFullName: String, directory: String): String {
        ensureDefaults(context, repoFullName)
        val dir = if (directory.isBlank()) {
            repoDir(context, repoFullName)
        } else {
            resolveToolPath(context, repoFullName, directory, mustExist = true)
        }
        if (!dir.isDirectory) throw IllegalArgumentException("memory_list: not a directory: $directory")
        val items = dir.listFiles()?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() }).orEmpty()
        return buildString {
            appendLine(memoryToolOutputHeader("memory_list", normalizeToolInput(directory)))
            appendLine("  → ${items.size} item(s)")
            if (items.isNotEmpty()) {
                items.forEach { file ->
                    val tag = if (file.isDirectory) "[dir]" else "[file]"
                    appendLine("$tag ${displayToolPath(context, repoFullName, file)}")
                }
            }
        }.trimEnd()
    }

    fun isMemoryTool(toolName: String): Boolean =
        toolName.startsWith("memory_")

    fun memoryToolOutputHeader(toolName: String, pathOrQuery: String): String {
        val target = pathOrQuery.trim()
        return if (target.isBlank()) "\$ $toolName" else "\$ $toolName $target"
    }

    fun formatToolResult(toolName: String, target: String, detail: String, body: String = ""): String =
        buildString {
            appendLine(memoryToolOutputHeader(toolName, target))
            appendLine("  → $detail")
            if (body.isNotBlank()) {
                appendLine(body)
            }
        }

    fun toolSearch(context: Context, repoFullName: String, query: String): String {
        ensureDefaults(context, repoFullName)
        val needle = query.trim()
        if (needle.isBlank()) throw IllegalArgumentException("memory_search: query must not be blank")
        val roots = listOf(repoDir(context, repoFullName), globalPreferencesFile(context))
        val results = mutableListOf<JSONObject>()
        roots.forEach { root ->
            val files = if (root.isFile) listOf(root) else root.walkTopDown().filter { it.isFile && it.extension == "md" }.toList()
            files.forEach { file ->
                file.readLines().forEachIndexed { index, line ->
                    if (line.contains(needle, ignoreCase = true)) {
                        results += JSONObject()
                            .put("path", displayToolPath(context, repoFullName, file))
                            .put("line", index + 1)
                            .put("snippet", line.trim().take(240))
                    }
                }
            }
        }
        val arr = JSONArray()
        results.forEach { arr.put(it) }
        return formatToolResult(
            toolName = "memory_search",
            target = "\"$needle\"",
            detail = "${results.size} match(es)",
            body = arr.toString(2),
        )
    }

    fun toolDelete(context: Context, repoFullName: String, path: String): String {
        ensureDefaults(context, repoFullName)
        val file = resolveToolPath(context, repoFullName, path, mustExist = true)
        if (file.isDirectory) throw IllegalArgumentException("memory_delete: refusing to delete directory: $path")
        val ok = file.delete()
        if (!ok) throw IllegalStateException("memory_delete: failed to delete $path")
        AiAgentMemoryIndex.markDirtyAndRebuild(context, repoFullName)
        return formatToolResult(
            toolName = "memory_delete",
            target = normalizeToolInput(path),
            detail = "deleted",
        )
    }

    fun saveChatFull(
        context: Context,
        repoFullName: String,
        chatId: String,
        messages: List<AiChatSessionStore.Message>,
        branch: String = "",
        providerId: String = "",
        modelId: String = "",
        createdAt: Long = 0L,
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
            .put("branch", branch)
            .put("providerId", providerId)
            .put("modelId", modelId)
            .put("createdAt", createdAt.takeIf { it > 0L } ?: System.currentTimeMillis())
            .put("updatedAt", System.currentTimeMillis())
            .put("messages", arr)
            .let { File(chatDir, "full.json").writeText(it.toString(2)) }
        AiAgentMemoryIndex.markDirtyAndRebuild(context, repoFullName)
    }

    fun listChats(context: Context, repoFullName: String): List<ChatRecord> {
        if (repoFullName.isBlank()) return emptyList()
        ensureDefaults(context, repoFullName)
        val chatsDir = File(repoDir(context, repoFullName), "chats")
        return chatsDir.listFiles()
            ?.asSequence()
            ?.filter { it.isDirectory }
            ?.map { dir -> chatRecordFromDir(dir) }
            ?.sortedByDescending { it.updatedAt }
            ?.toList()
            ?: emptyList()
    }

    fun loadChat(context: Context, repoFullName: String, chatId: String): LoadedChat {
        if (repoFullName.isBlank() || chatId.isBlank()) throw IllegalArgumentException("chat id is blank")
        ensureDefaults(context, repoFullName)
        val file = File(File(File(repoDir(context, repoFullName), "chats"), chatId), "full.json")
        if (!file.isFile) throw IllegalArgumentException("full.json not found for $chatId")
        return parseLoadedChat(file.readText(), repoFullName, chatId, file.lastModified())
    }

    fun renameChat(context: Context, repoFullName: String, chatId: String, title: String) {
        if (repoFullName.isBlank() || chatId.isBlank()) return
        ensureDefaults(context, repoFullName)
        val clean = title.trim().take(120)
        if (clean.isBlank()) return
        val chatDir = File(File(repoDir(context, repoFullName), "chats"), chatId).apply { mkdirs() }
        File(chatDir, "title.md").writeText(clean)
        val full = File(chatDir, "full.json")
        if (full.isFile) {
            runCatching {
                val obj = JSONObject(full.readText())
                obj.put("title", clean)
                obj.put("updatedAt", System.currentTimeMillis())
                full.writeText(obj.toString(2))
            }
        }
        AiAgentMemoryIndex.markDirtyAndRebuild(context, repoFullName)
    }

    fun deleteChat(context: Context, repoFullName: String, chatId: String): Boolean {
        if (repoFullName.isBlank() || chatId.isBlank()) return false
        ensureDefaults(context, repoFullName)
        val chatDir = File(File(repoDir(context, repoFullName), "chats"), chatId)
        val deleted = chatDir.exists() && chatDir.deleteRecursively()
        if (deleted) AiAgentMemoryIndex.markDirtyAndRebuild(context, repoFullName)
        return deleted
    }

    fun searchChats(context: Context, repoFullName: String, query: String): List<ChatSearchResult> {
        val needle = query.trim()
        if (repoFullName.isBlank() || needle.isBlank()) return emptyList()
        ensureDefaults(context, repoFullName)
        val chatsDir = File(repoDir(context, repoFullName), "chats")
        return chatsDir.listFiles()
            ?.asSequence()
            ?.filter { it.isDirectory }
            ?.mapNotNull { dir ->
                val record = chatRecordFromDir(dir)
                val files = listOf(File(dir, "summary.md"), File(dir, "full.json"))
                files.asSequence()
                    .filter { it.isFile }
                    .flatMap { file -> file.readLines().asSequence() }
                    .firstOrNull { it.contains(needle, ignoreCase = true) }
                    ?.trim()
                    ?.take(160)
                    ?.let { snippet ->
                        ChatSearchResult(
                            chatId = dir.name,
                            title = record.title,
                            snippet = snippet,
                            updatedAt = record.updatedAt,
                        )
                    }
            }
            ?.sortedByDescending { it.updatedAt }
            ?.toList()
            ?: emptyList()
    }

    suspend fun summarizeAndUpdate(
        context: Context,
        repoFullName: String,
        chatId: String,
        messages: List<AiChatSessionStore.Message>,
        provider: AiProvider?,
        modelId: String,
        apiKey: String,
        branch: String = "",
        providerId: String = "",
        createdAt: Long = 0L,
    ) = withContext(Dispatchers.IO) {
        if (repoFullName.isBlank() || chatId.isBlank() || messages.isEmpty()) return@withContext
        ensureDefaults(context, repoFullName)
        saveChatFull(context, repoFullName, chatId, messages, branch, providerId, modelId, createdAt)
        if (!AiAgentMemoryPrefs.getChatSummaries(context)) {
            return@withContext
        }
        val summary = generateSummary(context, messages, provider, modelId, apiKey)
        val chatDir = File(File(repoDir(context, repoFullName), "chats"), chatId).apply { mkdirs() }
        File(chatDir, "summary.md").writeText(summary)
        if (AiAgentMemoryPrefs.getProjectKnowledge(context)) {
            updateProjectKnowledge(context, repoFullName, summary)
        }
        AiAgentMemoryIndex.markDirtyAndRebuild(context, repoFullName)
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

    private fun structuredMemoryFacts(context: Context, repoFullName: String): List<String> {
        val allowPreferences = AiAgentMemoryPrefs.getUserPreferences(context)
        val allowProject = AiAgentMemoryPrefs.getProjectKnowledge(context)
        val allowChats = AiAgentMemoryPrefs.getChatSummaries(context)
        if (!allowPreferences && !allowProject && !allowChats) return emptyList()
        return AiAgentMemoryIndex.facts(context, repoFullName, limit = 32)
            .asSequence()
            .filter { fact ->
                when {
                    fact.sourcePath == PREFERENCES_FILE -> allowPreferences
                    fact.sourcePath.startsWith("chats/") -> allowChats
                    else -> allowProject
                }
            }
            .map { fact -> "- [${fact.type}] ${fact.text} (${fact.sourcePath})" }
            .toList()
    }

    private fun chatRecordFromDir(dir: File): ChatRecord {
        val summary = File(dir, "summary.md").takeIf { it.isFile }?.readText().orEmpty()
        val full = File(dir, "full.json")
        val customTitle = File(dir, "title.md").takeIf { it.isFile }?.readText()?.trim().orEmpty()
        return runCatching {
            val loaded = if (full.isFile) parseLoadedChat(full.readText(), "", dir.name, full.lastModified()) else null
            ChatRecord(
                id = dir.name,
                title = customTitle.ifBlank { summaryTitle(summary).ifBlank { firstUserTitle(loaded?.messages.orEmpty()) } },
                preview = summaryPreview(summary, loaded?.messages.orEmpty()),
                updatedAt = listOf(dir.lastModified(), full.lastModified(), File(dir, "summary.md").lastModified()).maxOrNull() ?: dir.lastModified(),
                messages = loaded?.messages?.size ?: 0,
            )
        }.getOrElse { error ->
            ChatRecord(
                id = dir.name,
                title = customTitle.ifBlank { dir.name },
                preview = error.message ?: "corrupted full.json",
                updatedAt = dir.lastModified(),
                messages = 0,
                corrupted = true,
                error = error.message ?: error.javaClass.simpleName,
            )
        }
    }

    private fun parseLoadedChat(raw: String, fallbackRepoFullName: String, fallbackChatId: String, fallbackUpdatedAt: Long): LoadedChat {
        val obj = JSONObject(raw)
        val messagesArr = obj.optJSONArray("messages") ?: JSONArray()
        val messages = buildList<AiChatSessionStore.Message> {
            for (i in 0 until messagesArr.length()) {
                val item = messagesArr.optJSONObject(i) ?: continue
                add(
                    AiChatSessionStore.Message(
                        role = item.optString("role", "assistant"),
                        content = item.optString("content", ""),
                        imageBase64 = item.optString("imageBase64", "").takeIf { it.isNotBlank() },
                        isError = item.optBoolean("isError", false),
                    ),
                )
            }
        }
        return LoadedChat(
            chatId = obj.optString("chatId", fallbackChatId).ifBlank { fallbackChatId },
            repoFullName = obj.optString("repoFullName", fallbackRepoFullName).ifBlank { fallbackRepoFullName },
            branch = obj.optString("branch", ""),
            providerId = obj.optString("providerId", ""),
            modelId = obj.optString("modelId", ""),
            createdAt = obj.optLong("createdAt", 0L).takeIf { it > 0L } ?: fallbackUpdatedAt,
            updatedAt = obj.optLong("updatedAt", 0L).takeIf { it > 0L } ?: fallbackUpdatedAt,
            messages = messages,
        )
    }

    private fun summaryTitle(summary: String): String {
        val lines = summary.lineSequence().map { it.trim() }.toList()
        val mainTaskIndex = lines.indexOfFirst { it.equals("## Main task", ignoreCase = true) || it.equals("## Main task/topic", ignoreCase = true) }
        if (mainTaskIndex >= 0) {
            lines.drop(mainTaskIndex + 1).firstOrNull { it.isNotBlank() }?.let { return it.removePrefix("- ").take(80) }
        }
        return lines.firstOrNull { it.isNotBlank() }?.removePrefix("- ")?.take(80).orEmpty()
    }

    private fun summaryPreview(summary: String, messages: List<AiChatSessionStore.Message>): String {
        return summary.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() && !it.startsWith("#") }
            ?.take(80)
            ?: messages.firstOrNull { it.role == "user" }?.content?.replace(Regex("\\s+"), " ")?.take(80)
            ?: ""
    }

    private fun firstUserTitle(messages: List<AiChatSessionStore.Message>): String =
        messages.firstOrNull { it.role == "user" }
            ?.content
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.take(60)
            ?.ifBlank { null }
            ?: "Untitled chat"

    private fun ensureDefaults(context: Context, repoFullName: String) {
        memoryRoot(context).mkdirs()
        val repo = repoDir(context, repoFullName).apply { mkdirs() }
        File(repo, "chats").mkdirs()
        File(repo, TOPICS_DIR).mkdirs()
        val project = File(repo, PROJECT_FILE)
        if (!project.exists()) {
            project.writeText(defaultProject(repoFullName.substringAfter('/').ifBlank { repoFullName }))
        }
        val decisions = File(repo, DECISIONS_FILE)
        if (!decisions.exists()) {
            decisions.writeText("# Decisions\n\n")
        }
        // Seed an empty working memory skeleton so the agent and the
        // memory-files dialog never have to handle a non-existent file —
        // they just see a template with empty sections (BUGS_FIX.md
        // Section 3 "Хранение" — exact structure).
        val workingMemory = File(repo, WORKING_MEMORY_FILE)
        if (!workingMemory.exists()) {
            workingMemory.writeText(defaultWorkingMemory())
        }
        val preferences = globalPreferencesFile(context)
        if (!preferences.exists()) {
            preferences.parentFile?.mkdirs()
            preferences.writeText(defaultPreferences())
        }
        refreshMemoryIndex(context, repoFullName)
    }

    private fun readMemoryIndex(context: Context, repoFullName: String): String =
        File(repoDir(context, repoFullName), MEMORY_INDEX_FILE)
            .takeIf { it.exists() }
            ?.readText()
            ?.take(2_400)
            .orEmpty()

    private fun readProjectKnowledge(context: Context, repoFullName: String): String =
        File(repoDir(context, repoFullName), PROJECT_FILE).takeIf { it.exists() }?.readText().orEmpty()

    private fun readGlobalPreferences(context: Context): String =
        globalPreferencesFile(context).takeIf { it.exists() }?.readText().orEmpty()

    private fun readDecisions(context: Context, repoFullName: String): String =
        File(repoDir(context, repoFullName), DECISIONS_FILE).takeIf { it.exists() }?.readText().orEmpty()

    fun repoMemoryDir(context: Context, repoFullName: String): File =
        repoDir(context, repoFullName)

    fun globalPreferencesMemoryFile(context: Context): File =
        globalPreferencesFile(context)

    fun memoryRootDir(context: Context): File =
        memoryRoot(context)

    fun refreshMemoryIndex(context: Context, repoFullName: String) {
        if (repoFullName.isBlank()) return
        val repo = repoDir(context, repoFullName).apply { mkdirs() }
        val topics = File(repo, TOPICS_DIR).apply { mkdirs() }
        val files = listOf(
            File(repo, PROJECT_FILE),
            globalPreferencesFile(context),
            File(repo, DECISIONS_FILE),
            File(repo, WORKING_MEMORY_FILE),
        )
        val topicFiles = topics.listFiles()
            ?.asSequence()
            ?.filter { it.isFile && it.extension.equals("md", ignoreCase = true) }
            ?.sortedBy { it.name.lowercase(Locale.US) }
            ?.toList()
            .orEmpty()
        val content = buildString {
            appendLine("# Memory Index")
            appendLine()
            appendLine("Scope: `$repoFullName`")
            appendLine()
            appendLine("This generated index is intentionally short. Detailed memory belongs in project.md, preferences.md, decisions.md, working_memory.md, and topics/*.md.")
            appendLine()
            appendLine("## Core files")
            files.forEach { file ->
                appendLine("- `${displayMemoryIndexPath(context, repoFullName, file)}` - ${memoryFileSummary(file)}")
            }
            appendLine()
            appendLine("## Topics")
            if (topicFiles.isEmpty()) {
                appendLine("- No topic files yet. Create `topics/<topic>.md` for focused long-term notes.")
            } else {
                topicFiles.take(40).forEach { file ->
                    appendLine("- `${displayMemoryIndexPath(context, repoFullName, file)}` - ${memoryFileSummary(file)}")
                }
                if (topicFiles.size > 40) appendLine("- ... ${topicFiles.size - 40} more topic files")
            }
        }.trimEnd() + "\n"
        val index = File(repo, MEMORY_INDEX_FILE)
        index.parentFile?.mkdirs()
        if (!index.isFile || index.readText() != content) {
            index.writeText(content)
        }
    }

    private fun repoDir(context: Context, repoFullName: String): File {
        val safe = repoFullName.replace("/", "__").replace(Regex("[^A-Za-z0-9_.-]"), "_")
        return File(memoryRoot(context), safe)
    }

    private fun resolveToolPath(
        context: Context,
        repoFullName: String,
        path: String,
        mustExist: Boolean,
    ): File {
        val clean = path.trim().replace('\\', '/').trim('/')
        if (clean.isBlank()) throw IllegalArgumentException("memory path must not be blank")
        if (File(clean).isAbsolute || clean.split('/').any { it == ".." }) {
            throw IllegalArgumentException("memory path must be relative and must not contain '..'")
        }
        val repo = repoDir(context, repoFullName)
        val repoName = repo.name
        val candidate = when {
            clean == PREFERENCES_FILE -> globalPreferencesFile(context)
            clean.startsWith("$repoName/") -> File(memoryRoot(context), clean)
            else -> File(repo, clean)
        }
        if (mustExist && !candidate.exists()) {
            throw IllegalArgumentException("memory file does not exist: $path")
        }
        val canonical = if (candidate.exists()) candidate.canonicalFile else candidate.absoluteFile.canonicalFile
        val repoCanonical = repo.canonicalFile
        val prefsCanonical = globalPreferencesFile(context).canonicalFile
        val insideRepo = canonical.path == repoCanonical.path || canonical.path.startsWith(repoCanonical.path + File.separator)
        val isPrefs = canonical.path == prefsCanonical.path
        if (!insideRepo && !isPrefs) {
            throw IllegalArgumentException("memory path escapes allowed memory roots: $path")
        }
        if (candidate.exists() && java.nio.file.Files.isSymbolicLink(candidate.toPath())) {
            throw IllegalArgumentException("memory path must not be a symbolic link: $path")
        }
        return canonical
    }

    private fun displayToolPath(context: Context, repoFullName: String, file: File): String {
        val root = memoryRoot(context).canonicalFile
        return file.canonicalFile.relativeTo(root).path.replace('\\', '/')
    }

    private fun displayMemoryIndexPath(context: Context, repoFullName: String, file: File): String {
        val repo = repoDir(context, repoFullName).canonicalFile
        val canonical = file.canonicalFile
        return if (canonical.path == repo.path || canonical.path.startsWith(repo.path + File.separator)) {
            canonical.relativeTo(repo).path.replace('\\', '/')
        } else {
            canonical.relativeTo(memoryRoot(context).canonicalFile).path.replace('\\', '/')
        }
    }

    private fun memoryFileSummary(file: File): String =
        if (file.isFile) {
            val size = file.length().coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            "${formatBytes(size)}, ${file.readText().lineCount()} lines"
        } else {
            "missing"
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

    /**
     * Skeleton working_memory.md as specified in BUGS_FIX.md Section 3
     * "Хранение". Sections are intentionally empty — the agent fills them
     * in via memory_write/memory_append as it edits files.
     */
    private fun defaultWorkingMemory(): String = """
        # Working Memory

        ## Active task

        ## Currently editing

        ## Completed in this task

        ## Open questions

    """.trimIndent()

    private fun String.lineCount(): Int = if (isEmpty()) 0 else lineSequence().count()

    private fun formatBytes(bytes: Int): String = when {
        bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1fMB", bytes / (1024.0 * 1024.0))
        bytes >= 1024 -> String.format(Locale.US, "%.1fKB", bytes / 1024.0)
        else -> "${bytes}B"
    }

    private fun normalizeToolInput(value: String): String =
        value.trim().replace('\\', '/').trim('/')
}
