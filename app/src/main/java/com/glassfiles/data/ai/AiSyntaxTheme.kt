package com.glassfiles.data.ai

import androidx.compose.ui.graphics.Color
import com.glassfiles.ui.components.CodeColors

/**
 * Fixed colour palettes for code-block syntax highlighting.
 *
 * The syntax highlighter intentionally ignores [androidx.compose.material3.MaterialTheme]
 * because the user's accent / Material You colour theme would otherwise tint
 * keywords, strings and numbers — turning code into a mono-tone blob whenever
 * the theme picker is set to a strong primary (e.g. red, magenta).
 *
 * Each entry below is hand-picked from a recognisable editor theme and stays
 * legible against [bgColor] in both AMOLED black and a regular dark grey
 * background. Default light / dark themes follow the surface contrast of the
 * rest of the AI module — they're meant to feel consistent with the chat
 * bubbles. The "designer" palettes (Dracula, Monokai, GitHub Dark, One Dark,
 * Solarised) are dark-on-dark and override the chat surface for the code
 * block only.
 */
enum class AiSyntaxTheme(
    val displayName: String,
    val bgColor: Color,
    val headerColor: Color,
    val labelColor: Color,
    val plain: Color,
    val keyword: Color,
    val string: Color,
    val number: Color,
    val comment: Color,
    val annotation: Color,
) {
    DEFAULT_DARK(
        displayName = "Default · Dark",
        bgColor = Color(0xFF111114),
        headerColor = Color(0xFF1B1B20),
        labelColor = Color(0xFF9A9AA3),
        plain = Color(0xFFE5E5EA),
        keyword = Color(0xFF7DA3FF),
        string = Color(0xFF9DD58D),
        number = Color(0xFFE6B86A),
        comment = Color(0xFF6F6F77),
        annotation = Color(0xFFD79CFF),
    ),
    DEFAULT_LIGHT(
        displayName = "Default · Light",
        bgColor = Color(0xFFF6F6F8),
        headerColor = Color(0xFFEBEBEF),
        labelColor = Color(0xFF6F6F77),
        plain = Color(0xFF24242B),
        keyword = Color(0xFF1F4FE0),
        string = Color(0xFF1F7A3A),
        number = Color(0xFFB36500),
        comment = Color(0xFF8C8C95),
        annotation = Color(0xFF7B2FB8),
    ),
    DRACULA(
        displayName = "Dracula",
        bgColor = Color(0xFF282A36),
        headerColor = Color(0xFF1F2029),
        labelColor = Color(0xFF6272A4),
        plain = Color(0xFFF8F8F2),
        keyword = Color(0xFFFF79C6),
        string = Color(0xFFF1FA8C),
        number = Color(0xFFBD93F9),
        comment = Color(0xFF6272A4),
        annotation = Color(0xFF50FA7B),
    ),
    MONOKAI(
        displayName = "Monokai",
        bgColor = Color(0xFF272822),
        headerColor = Color(0xFF1F201A),
        labelColor = Color(0xFF75715E),
        plain = Color(0xFFF8F8F2),
        keyword = Color(0xFFF92672),
        string = Color(0xFFE6DB74),
        number = Color(0xFFAE81FF),
        comment = Color(0xFF75715E),
        annotation = Color(0xFFA6E22E),
    ),
    GITHUB_DARK(
        displayName = "GitHub Dark",
        bgColor = Color(0xFF0D1117),
        headerColor = Color(0xFF161B22),
        labelColor = Color(0xFF8B949E),
        plain = Color(0xFFC9D1D9),
        keyword = Color(0xFFFF7B72),
        string = Color(0xFFA5D6FF),
        number = Color(0xFF79C0FF),
        comment = Color(0xFF8B949E),
        annotation = Color(0xFFD2A8FF),
    ),
    ONE_DARK(
        displayName = "One Dark",
        bgColor = Color(0xFF282C34),
        headerColor = Color(0xFF21252B),
        labelColor = Color(0xFF5C6370),
        plain = Color(0xFFABB2BF),
        keyword = Color(0xFFC678DD),
        string = Color(0xFF98C379),
        number = Color(0xFFD19A66),
        comment = Color(0xFF5C6370),
        annotation = Color(0xFF61AFEF),
    ),
    SOLARIZED_DARK(
        displayName = "Solarized Dark",
        bgColor = Color(0xFF002B36),
        headerColor = Color(0xFF073642),
        labelColor = Color(0xFF586E75),
        plain = Color(0xFF93A1A1),
        keyword = Color(0xFFCB4B16),
        string = Color(0xFF859900),
        number = Color(0xFFD33682),
        comment = Color(0xFF586E75),
        annotation = Color(0xFF268BD2),
    ),
    ;

    fun toCodeColors(): CodeColors = CodeColors(
        plain = plain,
        keyword = keyword,
        string = string,
        number = number,
        comment = comment,
        annotation = annotation,
    )
}
