package com.glassfiles.data.ai

import android.content.Context

/**
 * SharedPrefs-backed storage for **per-repo** AI Agent preferences.
 *
 * Currently stores:
 *  - last selected model id for each repo (so re-opening that repo
 *    auto-restores the model the user worked with last time)
 *  - optional system-prompt override per repo (an extra paragraph
 *    appended to the agent's base system prompt — useful when the
 *    user wants the agent to follow a specific style guide for one
 *    project without polluting the global prompt)
 *  - optional plan-then-execute toggle per repo
 *
 * All values are scoped by [repoFullName] (e.g. `owner/repo`) so
 * preferences from one repo never leak to another.
 */
object AiAgentPrefs {
    private const val PREFS = "ai_agent_prefs"
    private const val KEY_LAST_MODEL_PREFIX = "last_model__"
    private const val KEY_SYSTEM_PROMPT_PREFIX = "system_prompt__"
    private const val KEY_PLAN_THEN_EXECUTE_PREFIX = "plan_first__"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getLastModel(context: Context, repoFullName: String): String? =
        prefs(context).getString(KEY_LAST_MODEL_PREFIX + repoFullName, null)

    fun setLastModel(context: Context, repoFullName: String, modelId: String) {
        prefs(context).edit().putString(KEY_LAST_MODEL_PREFIX + repoFullName, modelId).apply()
    }

    fun getSystemPromptOverride(context: Context, repoFullName: String): String? =
        prefs(context).getString(KEY_SYSTEM_PROMPT_PREFIX + repoFullName, null)
            ?.takeIf { it.isNotBlank() }

    fun setSystemPromptOverride(context: Context, repoFullName: String, prompt: String) {
        prefs(context).edit().putString(KEY_SYSTEM_PROMPT_PREFIX + repoFullName, prompt).apply()
    }

    fun clearSystemPromptOverride(context: Context, repoFullName: String) {
        prefs(context).edit().remove(KEY_SYSTEM_PROMPT_PREFIX + repoFullName).apply()
    }

    fun getPlanThenExecute(context: Context, repoFullName: String): Boolean =
        prefs(context).getBoolean(KEY_PLAN_THEN_EXECUTE_PREFIX + repoFullName, false)

    fun setPlanThenExecute(context: Context, repoFullName: String, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_PLAN_THEN_EXECUTE_PREFIX + repoFullName, enabled).apply()
    }
}
