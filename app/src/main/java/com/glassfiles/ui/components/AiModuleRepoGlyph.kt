package com.glassfiles.ui.components

import androidx.compose.ui.graphics.Color
import com.glassfiles.ui.theme.AiModuleColors

/**
 * Resolved [AiModuleTheme]-aware representation of a repository state:
 * a single monospace glyph plus a palette color. Used by repo list /
 * repo detail screens to render dense rows in terminal style:
 *
 * ```
 * ▣  GlassFiles-1   kotlin   ★3   2025-04
 * ●  secret-repo    python   ★0   2025-03   (private)
 * ⑂  forked-thing   js       ★1   2025-03   (fork)
 * ⊘  archived-x     java     ★8   2024-09   (archived)
 * ▤  template-y     ts       ★2   2024-12   (template)
 * ```
 *
 * The mapping is a pure function of [com.glassfiles.data.github.GHRepo]
 * boolean flags; we keep the visual mapping local instead of inline so
 * the list/detail screens don't repeat it.
 */
data class AiModuleRepoBadge(
    val glyph: String,
    val color: Color,
)

private const val PUBLIC_GLYPH = "\u25A3"   // ▣
private const val PRIVATE_GLYPH = "\u25CF"  // ●
private const val FORK_GLYPH = "\u2442"     // ⑂ (Z notation join)
private const val ARCHIVED_GLYPH = "\u2298" // ⊘
private const val TEMPLATE_GLYPH = "\u25A4" // ▤

/**
 * Map repo state booleans to a badge. Precedence matches the icon
 * resolution in `RepoCard` (archived > private > fork > template >
 * public) so the visual story stays consistent with the existing
 * Material rendering even though the glyph set is different.
 */
fun aiModuleRepoBadge(
    isArchived: Boolean,
    isPrivate: Boolean,
    isFork: Boolean,
    isTemplate: Boolean,
    colors: AiModuleColors,
): AiModuleRepoBadge = when {
    isArchived -> AiModuleRepoBadge(ARCHIVED_GLYPH, colors.textMuted)
    isPrivate -> AiModuleRepoBadge(PRIVATE_GLYPH, colors.warning)
    isFork -> AiModuleRepoBadge(FORK_GLYPH, colors.textSecondary)
    isTemplate -> AiModuleRepoBadge(TEMPLATE_GLYPH, colors.accent)
    else -> AiModuleRepoBadge(PUBLIC_GLYPH, colors.accent)
}

/** Short single-token textual state (`public` / `private` / `fork` / `archived` / `template`). */
fun aiModuleRepoLabel(
    isArchived: Boolean,
    isPrivate: Boolean,
    isFork: Boolean,
    isTemplate: Boolean,
): String = when {
    isArchived -> "archived"
    isPrivate -> "private"
    isFork -> "fork"
    isTemplate -> "template"
    else -> "public"
}
