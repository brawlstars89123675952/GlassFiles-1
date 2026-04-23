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
    val updatedAt: Long,
    val providerType: String? = null,
    val vendor: String? = null,
    val modelId: String? = null,
    val modelLabel: String? = null,
    val schemaVersion: Int = 2
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
        val resolvedProvider = AiProvider.fromStoredValue(session.provider)
        val json = JSONObject().apply {
            put("schemaVersion", 2)
            put("id", session.id)
            put("title", session.title)
            put("provider", session.provider)
            putOpt("providerType", session.providerType ?: resolvedProvider?.providerType?.storageKey)
            putOpt("vendor", session.vendor ?: resolvedProvider?.vendor?.label)
            putOpt("modelId", session.modelId ?: resolvedProvider?.modelId)
            putOpt("modelLabel", session.modelLabel ?: resolvedProvider?.label)
            put("createdAt", session.createdAt)
            put("updatedAt", session.updatedAt)
            put("messages", JSONArray().apply {
                session.messages.forEach { msg -> put(msg.toJson()) }
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

    fun createSession(provider: AiProvider): ChatSession {
        val now = System.currentTimeMillis()
        val id = now.toString()
        return ChatSession(
            id = id,
            title = Strings.newChat,
            provider = provider.name,
            messages = emptyList(),
            createdAt = now,
            updatedAt = now,
            providerType = provider.providerType.storageKey,
            vendor = provider.vendor.label,
            modelId = provider.modelId,
            modelLabel = provider.label
        )
    }

    fun generateTitle(messages: List<ChatMessage>): String {
        val first = messages.firstOrNull { it.role == "user" }?.content ?: return Strings.newChat
        return first.take(40).let { if (first.length > 40) "$it..." else it }
    }

    private fun loadSession(file: File): ChatSession? {
        return try {
            val json = JSONObject(file.readText())
            val msgs = json.getJSONArray("messages")
            val providerValue = json.optString("provider", "AUTO")
            val provider = AiProvider.fromStoredValue(providerValue)
            ChatSession(
                id = json.getString("id"),
                title = json.optString("title", "Chat"),
                provider = providerValue,
                messages = (0 until msgs.length()).map { i -> parseMessage(msgs.getJSONObject(i), provider) },
                createdAt = json.optLong("createdAt", 0),
                updatedAt = json.optLong("updatedAt", 0),
                providerType = json.optString("providerType").takeIf { it.isNotBlank() } ?: provider?.providerType?.storageKey,
                vendor = json.optString("vendor").takeIf { it.isNotBlank() } ?: provider?.vendor?.label,
                modelId = json.optString("modelId").takeIf { it.isNotBlank() } ?: provider?.modelId,
                modelLabel = json.optString("modelLabel").takeIf { it.isNotBlank() } ?: provider?.label,
                schemaVersion = json.optInt("schemaVersion", 1)
            )
        } catch (_: Exception) { null }
    }

    private fun parseMessage(json: JSONObject, sessionProvider: AiProvider?): ChatMessage {
        val attachments = json.optJSONArray("attachments")?.let { parseAttachments(it) } ?: buildLegacyAttachments(json)
        return ChatMessage(
            role = json.optString("role", "user"),
            content = json.optString("content", ""),
            attachments = attachments,
            providerType = json.optString("providerType").takeIf { it.isNotBlank() } ?: sessionProvider?.providerType?.storageKey,
            vendor = json.optString("vendor").takeIf { it.isNotBlank() } ?: sessionProvider?.vendor?.label,
            modelId = json.optString("modelId").takeIf { it.isNotBlank() } ?: sessionProvider?.modelId,
            modelLabel = json.optString("modelLabel").takeIf { it.isNotBlank() } ?: sessionProvider?.label,
            createdAt = json.optLong("createdAt").takeIf { it > 0 }
        )
    }

    private fun parseAttachments(array: JSONArray): List<MessageAttachment> =
        (0 until array.length()).mapNotNull { idx ->
            array.optJSONObject(idx)?.let { MessageAttachment.fromJson(it) }
        }

    private fun buildLegacyAttachments(json: JSONObject): List<MessageAttachment> {
        val attachments = mutableListOf<MessageAttachment>()
        json.optString("imageBase64").takeIf { it.isNotBlank() }?.let {
            attachments += MessageAttachment(kind = "image", mimeType = "image/jpeg", base64Data = it)
        }
        json.optString("fileContent").takeIf { it.isNotBlank() }?.let {
            attachments += MessageAttachment(kind = "file", textContent = it)
        }
        return attachments
    }
}
