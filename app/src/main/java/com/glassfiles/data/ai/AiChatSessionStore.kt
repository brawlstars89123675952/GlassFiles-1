package com.glassfiles.data.ai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Multi-session chat history for the AI module.
 *
 * Whereas [ChatHistoryStore] keeps a single transcript per mode (good
 * enough for the legacy `AiChatScreen` while the rewrite is in flight),
 * this store keeps an ordered list of sessions per mode so the user can
 * keep multiple long-running coding / chat / image-prompt conversations
 * around without losing context.
 *
 * Storage: SharedPreferences, one JSON array per mode key.
 *
 * Capacity: [MAX_SESSIONS_PER_MODE] sessions per mode (FIFO eviction
 * once exceeded). Inside each session [MAX_MESSAGES_PER_SESSION] entries
 * are kept; old messages drop off the front.
 */
object AiChatSessionStore {
    private const val PREFS = "ai_chat_sessions"
    private const val MAX_SESSIONS_PER_MODE = 50
    private const val MAX_MESSAGES_PER_SESSION = 500

    const val MODE_CODING = "coding"

    data class Message(
        val role: String,
        val content: String,
        val imageBase64: String? = null,
        val fileContent: String? = null,
        val generatedFiles: List<GeneratedFile> = emptyList(),
        val isError: Boolean = false,
    )

    data class GeneratedFile(
        val name: String,
        val language: String,
        val content: String,
    )

    data class Session(
        val id: String,
        val mode: String,
        val title: String,
        val providerId: String,
        val modelId: String,
        val messages: List<Message>,
        val createdAt: Long,
        val updatedAt: Long,
        /**
         * Optional context fields. Used by the agent mode to remember the
         * `owner/name` of the repo and the active branch a chat was scoped
         * to so reopening the session restores everything (otherwise the
         * user has to repick the repo, which used to wipe the transcript).
         * Empty for non-agent modes.
         */
        val repoFullName: String = "",
        val branch: String = "",
    )

    fun list(context: Context, mode: String): List<Session> {
        val raw = prefs(context).getString(mode, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    add(parseSession(obj, mode))
                }
            }
        }.getOrDefault(emptyList())
    }

    fun get(context: Context, mode: String, id: String): Session? =
        list(context, mode).firstOrNull { it.id == id }

    fun upsert(context: Context, session: Session) {
        val current = list(context, session.mode).toMutableList()
        val idx = current.indexOfFirst { it.id == session.id }
        val trimmedMessages = if (session.messages.size > MAX_MESSAGES_PER_SESSION) {
            session.messages.takeLast(MAX_MESSAGES_PER_SESSION)
        } else {
            session.messages
        }
        val toWrite = session.copy(messages = trimmedMessages)
        if (idx >= 0) {
            current[idx] = toWrite
        } else {
            current.add(0, toWrite)
        }
        // Sort newest-first by updatedAt, evict overflow from the tail.
        current.sortByDescending { it.updatedAt }
        while (current.size > MAX_SESSIONS_PER_MODE) current.removeAt(current.lastIndex)
        write(context, session.mode, current)
    }

    fun delete(context: Context, mode: String, id: String) {
        val current = list(context, mode).filter { it.id != id }
        write(context, mode, current)
    }

    fun clear(context: Context, mode: String) {
        prefs(context).edit().remove(mode).apply()
    }

    /** Generates a 32-char title for the session from the first user message. */
    fun deriveTitle(messages: List<Message>): String {
        val firstUser = messages.firstOrNull { it.role == "user" }?.content?.trim().orEmpty()
        if (firstUser.isBlank()) return "New chat"
        val oneLine = firstUser.replace(Regex("\\s+"), " ")
        return if (oneLine.length <= 48) oneLine else oneLine.take(45) + "…"
    }

    private fun parseSession(obj: JSONObject, mode: String): Session {
        val msgsArr = obj.optJSONArray("messages") ?: JSONArray()
        val messages = buildList<Message>(msgsArr.length()) {
            for (i in 0 until msgsArr.length()) {
                val m = msgsArr.optJSONObject(i) ?: continue
                add(
                    Message(
                        role = m.optString("role", "assistant"),
                        content = m.optString("content", ""),
                        imageBase64 = m.optString("imageBase64", "").takeIf { it.isNotBlank() },
                        fileContent = m.optString("fileContent", "").takeIf { it.isNotBlank() },
                        generatedFiles = parseGeneratedFiles(m.optJSONArray("generatedFiles")),
                        isError = m.optBoolean("isError", false),
                    ),
                )
            }
        }
        return Session(
            id = obj.optString("id"),
            mode = mode,
            title = obj.optString("title", "Untitled"),
            providerId = obj.optString("providerId", ""),
            modelId = obj.optString("modelId", ""),
            messages = messages,
            createdAt = obj.optLong("createdAt", 0L),
            updatedAt = obj.optLong("updatedAt", 0L),
            repoFullName = obj.optString("repoFullName", ""),
            branch = obj.optString("branch", ""),
        )
    }

    private fun write(context: Context, mode: String, sessions: List<Session>) {
        val arr = JSONArray()
        sessions.forEach { s ->
            val msgsArr = JSONArray()
            s.messages.forEach { m ->
                val mObj = JSONObject()
                    .put("role", m.role)
                    .put("content", m.content)
                    .put("isError", m.isError)
                if (m.imageBase64 != null) mObj.put("imageBase64", m.imageBase64)
                if (m.fileContent != null) mObj.put("fileContent", m.fileContent)
                if (m.generatedFiles.isNotEmpty()) {
                    mObj.put(
                        "generatedFiles",
                        JSONArray().apply {
                            m.generatedFiles.forEach { file ->
                                put(
                                    JSONObject()
                                        .put("name", file.name)
                                        .put("language", file.language)
                                        .put("content", file.content),
                                )
                            }
                        },
                    )
                }
                msgsArr.put(mObj)
            }
            arr.put(
                JSONObject()
                    .put("id", s.id)
                    .put("title", s.title)
                    .put("providerId", s.providerId)
                    .put("modelId", s.modelId)
                    .put("messages", msgsArr)
                    .put("createdAt", s.createdAt)
                    .put("updatedAt", s.updatedAt)
                    .put("repoFullName", s.repoFullName)
                    .put("branch", s.branch),
            )
        }
        prefs(context).edit().putString(mode, arr.toString()).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun parseGeneratedFiles(arr: JSONArray?): List<GeneratedFile> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val name = obj.optString("name", "").takeIf { it.isNotBlank() } ?: continue
                add(
                    GeneratedFile(
                        name = name,
                        language = obj.optString("language", ""),
                        content = obj.optString("content", ""),
                    ),
                )
            }
        }
    }
}
