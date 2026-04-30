package com.glassfiles.ui.screens

/**
 * Single source of truth for the JetBrainsMono glyphs used across the
 * GitHub module. Every Material-icon usage in the GitHub UI maps to one
 * of these tokens so the chrome reads as a unified terminal interface
 * (no emoji, no Material vector iconography).
 *
 * Keep glyphs short (1–3 chars) and prefer plain ASCII / unicode
 * arrows that render uniformly in JetBrainsMono on every Android
 * version we ship to.
 */
internal object GhGlyphs {
    // Navigation
    const val BACK = "\u2190"           // ←
    const val OPEN_NEW = "\u2197"       // ↗
    const val EXTERNAL = "\u2197"       // ↗
    const val REFRESH = "\u21BA"        // ↺
    const val MORE = "\u22EF"           // ⋯
    const val CLOSE = "\u00D7"          // ×
    const val EXPAND = "\u25BE"         // ▾
    const val COLLAPSE = "\u25B8"       // ▸
    const val UP = "\u25B4"             // ▴
    const val DOWN = "\u25BE"           // ▾
    const val PIP = "_"

    // Search / filters
    const val SEARCH = "?"
    const val SLASH = "/"
    const val FILTER = "\u2261"         // ≡
    const val TUNE = "\u2261"           // ≡
    const val CLEAR = "\u00D7"          // ×

    // AI / sparkle
    const val AI = "*"
    const val SPARKLE = "*"
    const val PSYCHOLOGY = "*"

    // GitHub object glyphs (from AiModuleStatusGlyph but inline-friendly)
    const val PR = "\u26A1"             // ⚡  not used; keep PR_OPEN/etc.
    const val PR_OPEN = "PR"
    const val ISSUE_OPEN = "ISS"
    const val COMMIT = "C"
    const val BRANCH = "\u2387"         // ⎇
    const val FORK = "\u2387"           // ⎇
    const val TAG = "#"
    const val HASH = "#"
    const val MERGE = "<>"
    const val COMPARE = "<>"
    const val CODE = "{}"
    const val FILE = "f"
    const val FOLDER = "/"
    const val LIST = "\u2630"           // ☰
    const val TIMELINE = "||"
    const val OUTLINE = "\u2630"        // ☰

    // States
    const val STAR_ON = "\u2605"        // ★
    const val STAR_OFF = "\u2606"       // ☆
    const val FAV_ON = "\u2605"
    const val FAV_OFF = "\u2606"
    const val LOCK = "L"
    const val UNLOCK = "l"
    const val PIN = "@"
    const val ARCHIVE = "A"
    const val SAVE = "s"
    const val EDIT = "e"
    const val DELETE = "d"
    const val DUPLICATE = "d+"

    // Meta
    const val SETTINGS = "\u2261"       // ≡
    const val ASSIGN = "@"
    const val LABEL = "#"
    const val MILESTONE = "M"
    const val CALENDAR = "\u25A3"       // ▣
    const val PROJECT = "P"
    const val WORKFLOW = "w"
    const val PLAY = "\u25B6"           // ▶
    const val STOP = "\u25A0"           // ■
    const val PAUSE = "||"

    // Status indicators (text glyphs that pair with semantic colors)
    const val OK = "\u2713"             // ✓
    const val FAIL = "\u00D7"           // ×
    const val PENDING = "\u00B7"        // ·
    const val INFO = "i"
    const val WARN = "!"
    const val SKIPPED = "~"

    // Editor / view
    const val COPY = "cp"
    const val DOWNLOAD = "dl"
    const val UPLOAD = "ul"
    const val SHARE = "sh"
    const val LINES = "Ln"
    const val WRAP = "\u21B5"           // ↵
    const val UNDO = "\u21B6"           // ↶
    const val REDO = "\u21B7"           // ↷
    const val CYCLE = "~"
    const val FONT = "F"
    const val MAP = "m"

    // Reactions / discussion
    const val REACT = ":)"
    const val LAUGH = ":D"
    const val HEART = "<3"
    const val UPVOTE = "+1"
    const val DOWNVOTE = "-1"
    const val EYES = "[o]"

    // Network / users
    const val USER = "@"
    const val GROUP = "@@"
    const val ORG = "[@]"
    const val NOTIFY = "*"
    const val CHECK = "\u2713"
    const val CIRCLE_FILL = "\u25CF"    // ●
    const val CIRCLE_EMPTY = "\u25CB"   // ○
    const val DIAMOND = "\u25C6"        // ◆
    const val SQUARE = "\u25A0"         // ■
    const val ARROW_RIGHT = "\u2192"    // →
    const val ARROW_LEFT = "\u2190"     // ←
    const val ARROW_DOWN = "\u2193"     // ↓
    const val ARROW_UP = "\u2191"       // ↑
}
