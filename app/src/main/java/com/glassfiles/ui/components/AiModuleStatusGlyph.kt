package com.glassfiles.ui.components

import androidx.compose.ui.graphics.Color
import com.glassfiles.ui.theme.AiModuleColors

/**
 * Resolved [AiModuleTheme]-aware representation of a CI status: a single
 * monospace glyph (✓ ✗ ⊘ ⏳ ◷) plus a palette color. Used by the
 * Actions screens to render dense run/job rows in terminal style:
 *
 * ```
 * ✓  #42  build      2m14s  main   a1b2c3d  taufik   2h
 * ⏳ #41  build      running  …
 * ✗  #40  release    4m02s  …
 * ```
 */
data class AiModuleStatusBadge(
    val glyph: String,
    val color: Color,
    val alpha: Float = 1f,
)

private const val SUCCESS_GLYPH = "\u2713" // ✓
private const val FAILURE_GLYPH = "\u2717" // ✗
private const val CANCEL_GLYPH = "\u2298"  // ⊘
private const val RUNNING_GLYPH = "\u23F3" // ⏳
private const val QUEUED_GLYPH = "\u25F7"  // ◷

/**
 * Map a GitHub Actions `status` / `conclusion` pair to a status badge.
 *
 * Logic mirrors `displayRunStatus` / `displayJobStatus` in
 * [com.glassfiles.ui.screens.GitHubActionsModule] but the visual mapping
 * (color/glyph) is intentionally local so the Actions screens don't
 * have to repeat it inline. Inputs are matched case-insensitively and
 * tolerate the GitHub `_`/space inconsistency
 * (e.g. `in_progress` vs `in progress`).
 */
fun aiModuleStatusBadge(
    status: String?,
    conclusion: String? = null,
    colors: AiModuleColors,
): AiModuleStatusBadge {
    val s = status?.trim()?.lowercase()?.replace(' ', '_').orEmpty()
    val c = conclusion?.trim()?.lowercase()?.replace(' ', '_').orEmpty()
    return when {
        c == "success" -> AiModuleStatusBadge(SUCCESS_GLYPH, colors.accent)
        c == "failure" || c == "timed_out" || c == "action_required" || c == "startup_failure" ->
            AiModuleStatusBadge(FAILURE_GLYPH, colors.error)
        c == "cancelled" -> AiModuleStatusBadge(CANCEL_GLYPH, colors.textMuted)
        c == "skipped" -> AiModuleStatusBadge(CANCEL_GLYPH, colors.textMuted, alpha = 0.7f)
        c == "neutral" -> AiModuleStatusBadge(CANCEL_GLYPH, colors.textSecondary)
        s == "in_progress" || s == "running" -> AiModuleStatusBadge(RUNNING_GLYPH, colors.warning)
        s == "queued" || s == "pending" || s == "waiting" || s == "requested" ->
            AiModuleStatusBadge(QUEUED_GLYPH, colors.textSecondary)
        else -> AiModuleStatusBadge(QUEUED_GLYPH, colors.textMuted)
    }
}

/**
 * Short single-token textual status like `success`, `failure`, `cancel`,
 * `running`, `queued`, `skipped`. Mirrors the columns that appear next
 * to the glyph in the runs / jobs tables.
 */
fun aiModuleStatusLabel(status: String?, conclusion: String? = null): String {
    val s = status?.trim()?.lowercase()?.replace(' ', '_').orEmpty()
    val c = conclusion?.trim()?.lowercase()?.replace(' ', '_').orEmpty()
    return when {
        c == "success" -> "success"
        c == "failure" -> "failure"
        c == "timed_out" -> "timed out"
        c == "action_required" -> "needs review"
        c == "startup_failure" -> "startup fail"
        c == "cancelled" -> "cancelled"
        c == "skipped" -> "skipped"
        c == "neutral" -> "neutral"
        s == "in_progress" || s == "running" -> "running"
        s == "queued" -> "queued"
        s == "pending" -> "pending"
        s == "waiting" -> "waiting"
        s == "requested" -> "requested"
        else -> s.ifBlank { "unknown" }
    }
}
