package com.glassfiles.data.ai.cost

import android.content.Context

/**
 * SharedPrefs-backed persistence for the user's selected
 * [AiCostMode] plus per-(repo, provider) "remember once" flags used
 * by the expensive-action warning dialog (PR-COST-B).
 *
 * The dialog is intentionally NOT data-class-stored here: only the
 * boolean dismissals. The decision logic itself lives next to the UI.
 */
object AiCostModeStore {
    private const val PREFS = "ai_cost_mode"
    private const val KEY_MODE = "mode"
    private const val KEY_REMEMBERED_PREFIX = "remembered_"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Returns the user's selected mode, or [AiCostMode.DEFAULT] when unset. */
    fun getMode(context: Context): AiCostMode =
        AiCostMode.parse(prefs(context).getString(KEY_MODE, null))

    fun setMode(context: Context, mode: AiCostMode) {
        prefs(context).edit().putString(KEY_MODE, mode.name).apply()
    }

    /**
     * Has the user clicked "remember for this repo+provider" on the
     * expensive-action warning dialog for [repoFullName] / [providerId]?
     */
    fun isRemembered(context: Context, repoFullName: String, providerId: String): Boolean =
        prefs(context).getBoolean(rememberKey(repoFullName, providerId), false)

    fun setRemembered(
        context: Context,
        repoFullName: String,
        providerId: String,
        remembered: Boolean,
    ) {
        prefs(context).edit().putBoolean(rememberKey(repoFullName, providerId), remembered).apply()
    }

    /**
     * Wipes every "remembered" dismissal — used by the privacy-minded
     * "reset all warnings" button in settings (PR-COST-B).
     */
    fun clearAllRemembered(context: Context) {
        val p = prefs(context)
        val edit = p.edit()
        p.all.keys.filter { it.startsWith(KEY_REMEMBERED_PREFIX) }.forEach { edit.remove(it) }
        edit.apply()
    }

    private fun rememberKey(repoFullName: String, providerId: String): String =
        "$KEY_REMEMBERED_PREFIX${repoFullName}__$providerId"
}
