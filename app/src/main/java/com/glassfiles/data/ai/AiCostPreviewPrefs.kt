package com.glassfiles.data.ai

import android.content.Context

/**
 * SharedPreferences-backed storage for the "pre-request cost preview"
 * feature. When enabled, the AI screens show a confirmation dialog
 * before sending any request whose [com.glassfiles.data.ai.usage.AiUsageEstimate.costUsd]
 * exceeds [getThresholdUsd].
 *
 * The defaults follow the spec in BUGS_FIX.md "Pre-request cost preview":
 * the feature is on, with a $0.10 threshold. Both can be overridden by
 * the user from the AI Module settings screen — this object exposes
 * setters so the settings UI can persist changes synchronously
 * without round-tripping through DataStore.
 *
 * Mirrors the style of [AiAgentApprovalPrefs] (plain SharedPreferences,
 * no DataStore Flow) so the call-site can read the threshold inline
 * during a click handler without launching a coroutine.
 */
object AiCostPreviewPrefs {
    /**
     * Default threshold the spec recommends ($0.10).
     */
    const val DEFAULT_THRESHOLD_USD: Double = 0.10

    private const val PREFS = "ai_cost_preview_prefs"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_THRESHOLD_USD_X1000 = "threshold_usd_x1000"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Whether the pre-request cost preview dialog is shown at all.
     * Defaults to `true`. Users can disable it from Settings → AI Module
     * to suppress the dialog entirely (e.g. for trusted automation).
     */
    fun getEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    /**
     * Threshold in USD above which the preview dialog should be shown.
     * Stored as an integer (millicents) so we don't have to deal with
     * float precision in SharedPreferences. The spec defaults to
     * $0.10 — i.e. a smallish nudge before any non-trivial spend.
     */
    fun getThresholdUsd(context: Context): Double {
        val raw = prefs(context).getInt(
            KEY_THRESHOLD_USD_X1000,
            (DEFAULT_THRESHOLD_USD * 1000.0).toInt(),
        )
        return raw / 1000.0
    }

    fun setThresholdUsd(context: Context, threshold: Double) {
        val clamped = threshold.coerceIn(0.0, 100.0)
        prefs(context).edit()
            .putInt(KEY_THRESHOLD_USD_X1000, (clamped * 1000.0).toInt())
            .apply()
    }

    /**
     * Convenience: should we actually show the dialog for a request
     * whose estimated cost is [estimatedCostUsd]? Centralised here so
     * the threshold/enabled semantics are defined in one place.
     *
     * Returns `false` when the feature is disabled, when the cost is
     * unknown, or when the estimate is at-or-below threshold.
     */
    fun shouldPreview(context: Context, estimatedCostUsd: Double?): Boolean {
        if (!getEnabled(context)) return false
        val cost = estimatedCostUsd ?: return false
        return cost > getThresholdUsd(context)
    }
}
