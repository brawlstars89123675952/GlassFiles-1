package com.glassfiles.data.ai
import com.glassfiles.data.Strings

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class ChatSession(
    val id: String,
    val title: String,
    val provider: String,
    val messages: List<ChatMessage>,
    val createdAt: Long,
    val updatedAt: Long
)

class ChatHistoryManager(context: Context) {
    private val dir = File(context.filesDir, "ai_chats").apply { mkdirs() }

    fun getSessions(): List<ChatSession> {
        return dir.listFiles()?.filter { it.extension == "json" }
            ?.mapNotNull { loadSession(it) }
            ?.sortedByDescending { it.updatedAt }
            ?: emptyList()
    }

    fun getSession(id: String): ChatSession? {
        val file = File(dir, "$id.json")
        return if (file.exists()) loadSession(file) else null
    }

    fun saveSession(session: ChatSession) {
        val json = JSONObject().apply {
            put("id", session.id)
            put("title", session.title)
            put("provider", session.provider)
            put("createdAt", session.createdAt)
            put("updatedAt", session.updatedAt)
            put("messages", JSONArray().apply {
                session.messages.forEach { msg ->
                    put(JSONObject().put("role", msg.role).put("content", msg.content))
                }
            })
        }
        File(dir, "${session.id}.json").writeText(json.toString(2))
    }

    fun deleteSession(id: String) {
        File(dir, "$id.json").delete()
    }

    fun deleteAll() {
        dir.listFiles()?.forEach { it.delete() }
    }

    fun createSession(provider: String = "AUTO"): ChatSession {
        val id = System.currentTimeMillis().toString()
        return ChatSession(
            id = id,
            title = Strings.newChat,
            provider = provider,
            messages = emptyList(),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
    }

    fun createSession(provider: AiProvider): ChatSession = createSession(provider.name)

    /** Auto-generate title from first user message */
    fun generateTitle(messages: List<ChatMessage>): String {
        val first = messages.firstOrNull { it.role == "user" }?.content ?: return Strings.newChat
        return first.take(40).let { if (first.length > 40) "$it..." else it }
    }

    private fun loadSession(file: File): ChatSession? {
        return try {
            val json = JSONObject(file.readText())
            val msgs = json.getJSONArray("messages")
            ChatSession(
                id = json.getString("id"),
                title = json.optString("title", "Chat"),
                provider = json.optString("provider", "AUTO"),
                messages = (0 until msgs.length()).map { i ->
                    val m = msgs.getJSONObject(i)
                    ChatMessage(m.getString("role"), m.getString("content"))
                },
                createdAt = json.optLong("createdAt", 0),
                updatedAt = json.optLong("updatedAt", 0)
            )
        } catch (_: Exception) { null }
    }
}
