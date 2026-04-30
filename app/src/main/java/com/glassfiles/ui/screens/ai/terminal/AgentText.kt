package com.glassfiles.ui.screens.ai.terminal

/**
 * Defensive last-mile filter for any text that lands in the rendered
 * transcript. The real fix is at the provider boundary
 * ([com.glassfiles.data.ai.providers.Http.optStringOrEmpty]) — this is
 * a tripwire so a regression in a new provider can't sneak the literal
 * "null" back into the UI.
 *
 * Strips whole-line `null`s and a trailing `null` token. Does not touch
 * the word "null" inside running prose (e.g. "returned null on
 * empty"), only the leaked-token shapes we've seen in the wild.
 */
fun cleanAgentText(raw: String?): String {
    if (raw.isNullOrEmpty()) return ""
    return raw
        .replace(Regex("(?m)^\\s*null\\s*$"), "")
        .replace(Regex("\\bnull\\b(?=\\s*$)"), "")
        .trimEnd()
}
