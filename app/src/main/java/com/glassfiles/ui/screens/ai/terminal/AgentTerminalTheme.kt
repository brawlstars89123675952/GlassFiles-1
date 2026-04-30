package com.glassfiles.ui.screens.ai.terminal

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.glassfiles.ui.components.CodeColors
import com.glassfiles.ui.theme.JetBrainsMono

/**
 * Terminal-mode palette and typography for the AI Agent and Coding
 * screens only.
 *
 * Namespace boundary (do not violate):
 *  - This package, `com.glassfiles.ui.screens.ai.terminal`, is reserved
 *    for [com.glassfiles.ui.screens.AiAgentScreen] and
 *    [com.glassfiles.ui.screens.AiCodingScreen].
 *  - Every other AI-module screen (Chat, Hub, Models, Keys, Usage,
 *    Settings, ImageGen, VideoGen) must read its palette from
 *    [com.glassfiles.ui.theme.AiModuleTheme] / `AiModuleSurface`
 *    instead — that theme intentionally duplicates the same HEX values
 *    so the visuals stay aligned without cross-package imports.
 *  - Adding any `import com.glassfiles.ui.screens.ai.terminal.*` to a
 *    screen outside Agent/Coding is a regression. Use the
 *    `AiModule*` twins under `com.glassfiles.ui.components` /
 *    `com.glassfiles.ui.theme` (e.g. [com.glassfiles.ui.components.AiModuleBlinkingCursor],
 *    [com.glassfiles.ui.components.AiModuleCodeBlock]).
 *
 * The palette is intentionally hand-picked (not derived from
 * [androidx.compose.material3.MaterialTheme.colorScheme]) so the AI
 * surfaces stay a visually obscured "engineering surface" regardless of
 * the user's chosen accent color elsewhere in the app. Outside the AI
 * module the global Material theme still applies — this palette must
 * not leak into Browse / Files / Settings / Terminal / GitHub etc.
 */
@Immutable
data class AgentTerminalColors(
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val border: Color,
    val accent: Color,
    val accentDim: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val warning: Color,
    val error: Color,
    val syntaxKeyword: Color,
    val syntaxFlag: Color,
    val syntaxString: Color,
    val syntaxArg: Color,
    val syntaxComment: Color,
    val syntaxNumber: Color,
)

val AgentTerminalDarkColors = AgentTerminalColors(
    background = Color(0xFF000000),
    surface = Color(0xFF0A0A0A),
    surfaceElevated = Color(0xFF141414),
    border = Color(0xFF1F1F1F),
    accent = Color(0xFFA8D982),
    accentDim = Color(0xFF6B8C54),
    textPrimary = Color(0xFFE0E0E0),
    textSecondary = Color(0xFF999999),
    textMuted = Color(0xFF5C5C5C),
    warning = Color(0xFFE5C07B),
    error = Color(0xFFE06C75),
    syntaxKeyword = Color(0xFFA8D982),
    syntaxFlag = Color(0xFFE5C07B),
    syntaxString = Color(0xFF98C379),
    syntaxArg = Color(0xFFABB2BF),
    syntaxComment = Color(0xFF5C6370),
    syntaxNumber = Color(0xFFD19A66),
)

@Immutable
data class AgentTerminalTypography(
    val topBarTitle: TextUnit = 16.sp,
    val message: TextUnit = 14.sp,
    val code: TextUnit = 13.sp,
    val input: TextUnit = 14.sp,
    val toolCall: TextUnit = 13.sp,
    val costChip: TextUnit = 12.sp,
    val label: TextUnit = 12.sp,
)

val AgentTerminalDefaultTypography = AgentTerminalTypography()

val LocalAgentTerminalColors = compositionLocalOf<AgentTerminalColors> {
    error("AgentTerminalColors not provided — wrap your composable in AgentTerminalSurface.")
}

val LocalAgentTerminalTypography = compositionLocalOf { AgentTerminalDefaultTypography }

/** Convenience accessor: `AgentTerminal.colors` / `AgentTerminal.type`. */
object AgentTerminal {
    val colors: AgentTerminalColors
        @androidx.compose.runtime.Composable
        @androidx.compose.runtime.ReadOnlyComposable
        get() = LocalAgentTerminalColors.current

    val type: AgentTerminalTypography
        @androidx.compose.runtime.Composable
        @androidx.compose.runtime.ReadOnlyComposable
        get() = LocalAgentTerminalTypography.current
}

fun AgentTerminalColors.toCodeColors(): CodeColors = CodeColors(
    plain = textPrimary,
    keyword = syntaxKeyword,
    string = syntaxString,
    number = syntaxNumber,
    comment = syntaxComment,
    annotation = syntaxFlag,
)
