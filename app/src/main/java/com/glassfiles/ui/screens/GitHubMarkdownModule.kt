package com.glassfiles.ui.screens

import android.os.Environment
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.glassfiles.data.Strings
import com.glassfiles.data.github.*
import com.glassfiles.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File

// Compact mode — propagates through all sub-screens automatically

@Composable
internal fun MarkdownLine(line: String) {
    val trimmed = line.trimStart()
    when {
        trimmed.startsWith("# ") -> Text(trimmed.removePrefix("# "), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE6EDF3), modifier = Modifier.padding(vertical = 6.dp))
        trimmed.startsWith("## ") -> Text(trimmed.removePrefix("## "), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE6EDF3), modifier = Modifier.padding(vertical = 4.dp))
        trimmed.startsWith("### ") -> Text(trimmed.removePrefix("### "), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFE6EDF3), modifier = Modifier.padding(vertical = 3.dp))
        trimmed.startsWith("```") -> Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF30363D)))
        trimmed.startsWith("- ") || trimmed.startsWith("* ") -> Row(Modifier.padding(vertical = 1.dp)) {
            Text("  \u2022  ", fontSize = 13.sp, color = Color(0xFF8B949E))
            Text(trimmed.drop(2), fontSize = 13.sp, color = Color(0xFFC9D1D9), lineHeight = 18.sp)
        }
        trimmed.startsWith("> ") -> Row(Modifier.padding(vertical = 1.dp)) {
            Box(Modifier.width(3.dp).height(18.dp).background(Color(0xFF30363D)))
            Text(trimmed.drop(2), fontSize = 13.sp, color = Color(0xFF8B949E), modifier = Modifier.padding(start = 8.dp))
        }
        trimmed.startsWith("---") || trimmed.startsWith("***") -> Box(Modifier.fillMaxWidth().padding(vertical = 8.dp).height(1.dp).background(Color(0xFF30363D)))
        trimmed.isEmpty() -> Spacer(Modifier.height(8.dp))
        else -> {
            // Inline formatting: **bold**, *italic*, `code`, [link](url)
            Text(buildMdAnnotated(trimmed), fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

internal fun buildMdAnnotated(text: String): androidx.compose.ui.text.AnnotatedString {
    return androidx.compose.ui.text.buildAnnotatedString {
        var i = 0
        val len = text.length
        val defColor = Color(0xFFC9D1D9)
        val codeColor = Color(0xFFE6EDF3)
        val codeBg = Color(0xFF161B22)
        val boldColor = Color(0xFFE6EDF3)
        val linkColor = Color(0xFF58A6FF)

        while (i < len) {
            when {
                // `code`
                i < len && text[i] == '`' -> {
                    val end = text.indexOf('`', i + 1)
                    if (end > i) {
                        pushStyle(androidx.compose.ui.text.SpanStyle(color = codeColor, fontFamily = FontFamily.Monospace, background = codeBg))
                        append(text.substring(i + 1, end))
                        pop(); i = end + 1
                    } else { pushStyle(androidx.compose.ui.text.SpanStyle(color = defColor)); append(text[i]); pop(); i++ }
                }
                // **bold**
                i + 1 < len && text[i] == '*' && text[i + 1] == '*' -> {
                    val end = text.indexOf("**", i + 2)
                    if (end > i) {
                        pushStyle(androidx.compose.ui.text.SpanStyle(color = boldColor, fontWeight = FontWeight.Bold))
                        append(text.substring(i + 2, end))
                        pop(); i = end + 2
                    } else { pushStyle(androidx.compose.ui.text.SpanStyle(color = defColor)); append(text[i]); pop(); i++ }
                }
                // [text](url)
                text[i] == '[' -> {
                    val closeBracket = text.indexOf(']', i)
                    val openParen = if (closeBracket > 0 && closeBracket + 1 < len) { if (text[closeBracket + 1] == '(') closeBracket + 1 else -1 } else -1
                    val closeParen = if (openParen > 0) text.indexOf(')', openParen) else -1
                    if (closeParen > 0) {
                        pushStyle(androidx.compose.ui.text.SpanStyle(color = linkColor))
                        append(text.substring(i + 1, closeBracket))
                        pop(); i = closeParen + 1
                    } else { pushStyle(androidx.compose.ui.text.SpanStyle(color = defColor)); append(text[i]); pop(); i++ }
                }
                else -> { pushStyle(androidx.compose.ui.text.SpanStyle(color = defColor)); append(text[i]); pop(); i++ }
            }
        }
    }
}

// ═══════════════════════════════════
// Syntax Highlighting (fast, safe)
// ═══════════════════════════════════

internal val defaultKeywords = setOf(
    "fun", "val", "var", "class", "object", "interface", "enum", "data", "sealed", "abstract",
    "override", "private", "public", "protected", "internal", "open", "final", "companion",
    "import", "package", "return", "if", "else", "when", "for", "while", "do", "break", "continue",
    "try", "catch", "finally", "throw", "is", "as", "in", "by", "init", "constructor", "suspend",
    "function", "const", "let", "def", "self", "this", "super", "new", "delete", "typeof", "instanceof",
    "static", "void", "int", "long", "float", "double", "boolean", "char", "string", "byte",
    "true", "false", "null", "nil", "None", "True", "False",
    "struct", "impl", "trait", "pub", "fn", "mut", "use", "mod", "crate", "extern",
    "from", "with", "yield", "async", "await", "lambda", "raise", "except", "pass",
    "switch", "case", "default", "goto", "volatile", "register", "typedef", "sizeof"
)

internal val htmlKeywords = setOf(
    "div", "span", "html", "head", "body", "script", "style", "link", "meta", "title",
    "p", "a", "img", "input", "button", "form", "table", "tr", "td", "th", "ul", "ol", "li",
    "h1", "h2", "h3", "h4", "h5", "h6", "br", "hr", "section", "header", "footer", "nav",
    "class", "id", "src", "href", "type", "value", "name", "content", "rel", "width", "height"
)

internal fun highlightLine(line: String, ext: String): androidx.compose.ui.text.AnnotatedString {
    val defColor = Color(0xFFD4D4D4)

    // Safety: very long lines → no highlighting (prevents OOM on minified files)
    if (line.length > 500) {
        return androidx.compose.ui.text.buildAnnotatedString {
            pushStyle(androidx.compose.ui.text.SpanStyle(color = defColor))
            append(line.take(500) + "...")
            pop()
        }
    }

    return try {
        doHighlightLine(line, ext)
    } catch (_: Exception) {
        // Fallback: plain text if highlighting crashes
        androidx.compose.ui.text.buildAnnotatedString {
            pushStyle(androidx.compose.ui.text.SpanStyle(color = defColor)); append(line); pop()
        }
    }
}

internal fun doHighlightLine(line: String, ext: String): androidx.compose.ui.text.AnnotatedString {
    val kwColor = Color(0xFFC586C0)
    val strColor = Color(0xFFCE9178)
    val commentColor = Color(0xFF6A9955)
    val numColor = Color(0xFFB5CEA8)
    val typeColor = Color(0xFF4EC9B0)
    val tagColor = Color(0xFF569CD6)
    val attrColor = Color(0xFF9CDCFE)
    val defColor = Color(0xFFD4D4D4)

    val isHtml = ext in listOf("html", "xml", "svg", "xaml", "xhtml")
    val isCss = ext in listOf("css", "scss", "sass", "less")
    val isJson = ext in listOf("json")
    val isYaml = ext in listOf("yaml", "yml", "toml")
    val isPy = ext in listOf("py")

    return androidx.compose.ui.text.buildAnnotatedString {
        var i = 0; val len = line.length
        if (len == 0) return@buildAnnotatedString

        // JSON/YAML
        if (isJson || isYaml) {
            val colonIdx = line.indexOf(':')
            if (colonIdx > 0 && (line.trimStart().startsWith("\"") || isYaml)) {
                pushStyle(androidx.compose.ui.text.SpanStyle(color = attrColor)); append(line.substring(0, colonIdx)); pop()
                pushStyle(androidx.compose.ui.text.SpanStyle(color = defColor)); append(":"); pop()
                val rest = line.substring(colonIdx + 1)
                val trimRest = rest.trimStart()
                val c = when {
                    trimRest.startsWith("\"") -> strColor
                    trimRest.firstOrNull()?.isDigit() == true || trimRest.startsWith("-") -> numColor
                    trimRest.startsWith("true") || trimRest.startsWith("false") || trimRest.startsWith("null") -> kwColor
                    else -> defColor
                }
                pushStyle(androidx.compose.ui.text.SpanStyle(color = c)); append(rest); pop()
                return@buildAnnotatedString
            }
        }

        // HTML/XML
        if (isHtml) {
            while (i < len) {
                when {
                    i + 3 < len && line[i] == '<' && line[i + 1] == '!' && line[i + 2] == '-' && line[i + 3] == '-' -> {
                        val end = line.indexOf("-->", i)
                        val to = if (end >= 0) minOf(end + 3, len) else len
                        pushStyle(androidx.compose.ui.text.SpanStyle(color = commentColor)); append(line.substring(i, to)); pop(); i = to
                    }
                    line[i] == '<' -> {
                        val end = line.indexOf('>', i)
                        val to = if (end >= 0) minOf(end + 1, len) else len
                        pushStyle(androidx.compose.ui.text.SpanStyle(color = tagColor)); append(line.substring(i, to)); pop(); i = to
                    }
                    line[i] == '"' || line[i] == '\'' -> {
                        val q = line[i]; val start = i; i++
                        while (i < len && line[i] != q) i++
                        if (i < len) i++
                        pushStyle(androidx.compose.ui.text.SpanStyle(color = strColor)); append(line.substring(start, minOf(i, len))); pop()
                    }
                    else -> { pushStyle(androidx.compose.ui.text.SpanStyle(color = defColor)); append(line[i]); pop(); i++ }
                }
            }
            return@buildAnnotatedString
        }

        // CSS
        if (isCss) {
            val trimmed = line.trimStart()
            when {
                trimmed.startsWith("/*") || trimmed.startsWith("*") -> {
                    pushStyle(androidx.compose.ui.text.SpanStyle(color = commentColor)); append(line); pop()
                }
                trimmed.startsWith("//") -> {
                    pushStyle(androidx.compose.ui.text.SpanStyle(color = commentColor)); append(line); pop()
                }
                trimmed.contains(":") && trimmed.contains(";") -> {
                    val colon = line.indexOf(':')
                    if (colon in 0 until len) {
                        pushStyle(androidx.compose.ui.text.SpanStyle(color = attrColor)); append(line.substring(0, colon)); pop()
                        pushStyle(androidx.compose.ui.text.SpanStyle(color = defColor)); append(":"); pop()
                        pushStyle(androidx.compose.ui.text.SpanStyle(color = numColor)); append(line.substring(colon + 1)); pop()
                    } else { pushStyle(androidx.compose.ui.text.SpanStyle(color = defColor)); append(line); pop() }
                }
                else -> { pushStyle(androidx.compose.ui.text.SpanStyle(color = tagColor)); append(line); pop() }
            }
            return@buildAnnotatedString
        }

        // General code
        val commentStart = findSafeCommentStart(line, isPy)

        while (i < len) {
            if (commentStart >= 0 && i >= commentStart) {
                pushStyle(androidx.compose.ui.text.SpanStyle(color = commentColor)); append(line.substring(i, len)); pop(); break
            }
            when {
                line[i] == '"' || line[i] == '\'' -> {
                    val q = line[i]; val start = i; i++
                    while (i < len && line[i] != q) {
                        if (line[i] == '\\' && i + 1 < len) i++ // skip escaped char safely
                        i++
                    }
                    if (i < len) i++ // closing quote
                    pushStyle(androidx.compose.ui.text.SpanStyle(color = strColor)); append(line.substring(start, minOf(i, len))); pop()
                }
                line[i].isDigit() && (i == 0 || !line[i - 1].isLetterOrDigit()) -> {
                    val start = i
                    while (i < len && (line[i].isDigit() || line[i] == '.' || line[i] == 'x' || line[i] == 'f' || line[i] == 'L')) i++
                    pushStyle(androidx.compose.ui.text.SpanStyle(color = numColor)); append(line.substring(start, minOf(i, len))); pop()
                }
                line[i].isLetter() || line[i] == '_' -> {
                    val start = i
                    while (i < len && (line[i].isLetterOrDigit() || line[i] == '_')) i++
                    val word = line.substring(start, minOf(i, len))
                    val color = when {
                        word in defaultKeywords -> kwColor
                        word.firstOrNull()?.isUpperCase() == true && word.length > 1 -> typeColor
                        else -> defColor
                    }
                    pushStyle(androidx.compose.ui.text.SpanStyle(color = color)); append(word); pop()
                }
                line[i] == '@' -> {
                    val start = i; i++
                    while (i < len && (line[i].isLetterOrDigit() || line[i] == '_')) i++
                    pushStyle(androidx.compose.ui.text.SpanStyle(color = Color(0xFFDCDCAA))); append(line.substring(start, minOf(i, len))); pop()
                }
                else -> { pushStyle(androidx.compose.ui.text.SpanStyle(color = defColor)); append(line[i]); pop(); i++ }
            }
        }
    }
}

internal fun findSafeCommentStart(line: String, isPython: Boolean): Int {
    var i = 0; var inStr = false; var q = ' '
    val len = line.length
    while (i < len) {
        val c = line[i]
        if (!inStr && (c == '"' || c == '\'')) { inStr = true; q = c }
        else if (inStr && c == q && (i == 0 || line[i - 1] != '\\')) inStr = false
        else if (!inStr) {
            if (i + 1 < len && c == '/' && line[i + 1] == '/') return i
            if (isPython && c == '#') return i
        }
        i++
    }
    return -1
}
