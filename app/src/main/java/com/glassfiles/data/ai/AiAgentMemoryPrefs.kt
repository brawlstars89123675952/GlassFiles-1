package com.glassfiles.data.ai

import android.content.Context

object AiAgentMemoryPrefs {
    private const val PREFS = "ai_agent_memory_prefs"
    private const val KEY_PROJECT_KNOWLEDGE = "project_knowledge"
    private const val KEY_USER_PREFERENCES = "user_preferences"
    private const val KEY_CHAT_SUMMARIES = "chat_summaries"
    private const val KEY_SEMANTIC_SEARCH = "semantic_search"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getProjectKnowledge(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PROJECT_KNOWLEDGE, false)

    fun setProjectKnowledge(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_PROJECT_KNOWLEDGE, enabled).apply()
    }

    fun getUserPreferences(context: Context): Boolean =
        prefs(context).getBoolean(KEY_USER_PREFERENCES, false)

    fun setUserPreferences(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_USER_PREFERENCES, enabled).apply()
    }

    fun getChatSummaries(context: Context): Boolean =
        prefs(context).getBoolean(KEY_CHAT_SUMMARIES, false)

    fun setChatSummaries(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_CHAT_SUMMARIES, enabled).apply()
    }

    fun getSemanticSearch(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SEMANTIC_SEARCH, false)

    fun setSemanticSearch(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SEMANTIC_SEARCH, enabled).apply()
    }
}
