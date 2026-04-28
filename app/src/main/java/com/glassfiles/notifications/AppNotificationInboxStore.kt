package com.glassfiles.notifications

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object AppNotificationInboxStore {
    private const val PREFS = "glassfiles_notification_inbox"
    private const val KEY_EVENTS = "events_json"
    private const val MAX_EVENTS = 300

    data class InboxItem(
        val id: String,
        val source: String,
        val type: String,
        val title: String,
        val body: String,
        val createdAt: Long,
        val read: Boolean,
        val destination: String,
        val path: String,
        val extra: String
    )

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun add(context: Context, event: AppNotificationEvent): InboxItem {
        val id = stableId(event)
        val current = list(context).filterNot { it.id == id }.toMutableList()
        val item = InboxItem(
            id = id,
            source = event.source,
            type = event.type,
            title = event.title,
            body = event.body,
            createdAt = event.createdAt,
            read = false,
            destination = event.target.destination,
            path = event.target.path,
            extra = event.target.extra
        )
        current.add(0, item)
        save(context, current.take(MAX_EVENTS))
        return item
    }

    fun list(context: Context): List<InboxItem> {
        val raw = prefs(context).getString(KEY_EVENTS, "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { index ->
                arr.optJSONObject(index)?.let { obj ->
                    InboxItem(
                        id = obj.optString("id"),
                        source = obj.optString("source"),
                        type = obj.optString("type"),
                        title = obj.optString("title"),
                        body = obj.optString("body"),
                        createdAt = obj.optLong("created_at"),
                        read = obj.optBoolean("read"),
                        destination = obj.optString("destination"),
                        path = obj.optString("path"),
                        extra = obj.optString("extra")
                    )
                }
            }.filter { it.id.isNotBlank() }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun markRead(context: Context, id: String) {
        save(context, list(context).map { if (it.id == id) it.copy(read = true) else it })
    }

    fun markAllRead(context: Context) {
        save(context, list(context).map { it.copy(read = true) })
    }

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY_EVENTS).apply()
    }

    private fun save(context: Context, items: List<InboxItem>) {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(JSONObject().apply {
                put("id", item.id)
                put("source", item.source)
                put("type", item.type)
                put("title", item.title)
                put("body", item.body)
                put("created_at", item.createdAt)
                put("read", item.read)
                put("destination", item.destination)
                put("path", item.path)
                put("extra", item.extra)
            })
        }
        prefs(context).edit().putString(KEY_EVENTS, arr.toString()).apply()
    }

    private fun stableId(event: AppNotificationEvent): String {
        val external = event.externalId.ifBlank {
            "${event.source}:${event.type}:${event.title}:${event.body}:${event.createdAt / 60_000L}"
        }
        return "${event.source}:$external"
    }
}
