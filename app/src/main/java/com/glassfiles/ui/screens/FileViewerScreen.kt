package com.glassfiles.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.glassfiles.data.*
import java.io.File

@Composable
fun FileViewerScreen(filePath: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val file = remember { File(filePath) }
    val ext = file.extension.lowercase()
    val fileType = remember { getFileType(ext) }

    when (fileType) {
        FileType.IMAGE -> ImageViewer(file, onBack)
        FileType.TEXT, FileType.CODE -> TextViewer(file, onBack)
        FileType.VIDEO, FileType.AUDIO -> MediaPlayerScreen(filePath = filePath, onBack = onBack)
        else -> {
            // Try to open externally
            LaunchedEffect(Unit) { openFileExternal(context, file); onBack() }
        }
    }
}

// ═══════════════════════════════════
// Image Viewer — pinch to zoom
// ═══════════════════════════════════
@Composable
private fun ImageViewer(file: File, onBack: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val state = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(0.5f, 5f)
        offset += panChange
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AsyncImage(
            model = file,
            contentDescription = file.name,
            modifier = Modifier.fillMaxSize()
                .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y)
                .transformable(state)
        )
        // Top bar
        Row(Modifier.fillMaxWidth().background(Color.Black.copy(0.5f)).padding(top = 40.dp, start = 4.dp, end = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, Modifier.size(20.dp), tint = Color.White) }
            Text(file.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            IconButton(onClick = { scale = 1f; offset = Offset.Zero }) {
                Icon(Icons.Rounded.CropFree, null, Modifier.size(22.dp), tint = Color.White)
            }
        }
    }
}

// ═══════════════════════════════════
// Text/Code Viewer with syntax highlighting
// ═══════════════════════════════════
@Composable
private fun TextViewer(file: File, onBack: () -> Unit) {
    val content = remember { try { file.readText().take(100_000) } catch (_: Exception) { "Error reading file" } }
    var isEditing by remember { mutableStateOf(false) }
    var editText by remember { mutableStateOf(content) }
    val context = LocalContext.current
    val ext = file.extension.lowercase()
    val highlighter = remember(ext) { SyntaxHighlighter.forExtension(ext) }

    Column(Modifier.fillMaxSize().background(Color(0xFF1E1E1E))) {
        Row(Modifier.fillMaxWidth().background(Color(0xFF252526)).padding(top = 40.dp, start = 4.dp, end = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, Modifier.size(20.dp), tint = Color.White) }
            Text(file.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            if (highlighter != SyntaxHighlighter.PLAIN) {
                Text(ext.uppercase(), fontSize = 10.sp, color = Color(0xFF569CD6), fontWeight = FontWeight.Bold,
                    modifier = Modifier.background(Color(0xFF569CD6).copy(0.15f), androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp))
                Spacer(Modifier.width(4.dp))
            }
            IconButton(onClick = { isEditing = !isEditing }) {
                Icon(if (isEditing) Icons.Rounded.Visibility else Icons.Rounded.Edit, null, Modifier.size(20.dp), tint = Color(0xFF569CD6))
            }
            if (isEditing) {
                IconButton(onClick = {
                    try { file.writeText(editText); Toast.makeText(context, Strings.done, Toast.LENGTH_SHORT).show() }
                    catch (e: Exception) { Toast.makeText(context, "${Strings.error}: ${e.message}", Toast.LENGTH_SHORT).show() }
                }) { Icon(Icons.Rounded.Save, null, Modifier.size(20.dp), tint = Color(0xFF4EC9B0)) }
            }
        }

        // Line numbers + content
        Row(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState())) {
            val lines = (if (isEditing) editText else content).lines()
            // Line numbers
            Column(Modifier.background(Color(0xFF1E1E1E)).padding(horizontal = 8.dp, vertical = 8.dp)) {
                lines.forEachIndexed { i, _ ->
                    Text("${i + 1}", color = Color(0xFF858585), fontSize = 13.sp, fontFamily = FontFamily.Monospace, lineHeight = 20.sp)
                }
            }
            Box(Modifier.width(1.dp).fillMaxHeight().background(Color(0xFF333333)))
            // Content
            if (isEditing) {
                BasicTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    textStyle = TextStyle(color = Color(0xFFD4D4D4), fontSize = 13.sp, fontFamily = FontFamily.Monospace, lineHeight = 20.sp),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(Color(0xFF569CD6))
                )
            } else {
                // Syntax-highlighted view
                Column(Modifier.padding(8.dp)) {
                    lines.forEach { line ->
                        Text(
                            highlighter.highlight(line),
                            fontSize = 13.sp, fontFamily = FontFamily.Monospace, lineHeight = 20.sp
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════
// Syntax Highlighting Engine
// ═══════════════════════════════════

enum class SyntaxHighlighter {
    PLAIN, JSON, XML, CODE;

    companion object {
        fun forExtension(ext: String): SyntaxHighlighter = when (ext) {
            "json" -> JSON
            "xml", "html", "xhtml", "svg", "plist" -> XML
            "kt", "java", "py", "js", "ts", "rs", "go", "c", "cpp", "h", "swift", "rb", "php",
            "css", "scss", "less", "sh", "bash", "yaml", "yml", "toml", "gradle", "sql" -> CODE
            else -> PLAIN
        }
    }

    fun highlight(line: String): androidx.compose.ui.text.AnnotatedString {
        return when (this) {
            JSON -> highlightJson(line)
            XML -> highlightXml(line)
            CODE -> highlightCode(line)
            PLAIN -> androidx.compose.ui.text.buildAnnotatedString {
                withStyle(androidx.compose.ui.text.SpanStyle(color = Color(0xFFD4D4D4))) { append(line) }
            }
        }
    }
}

private val JsonKeyColor = Color(0xFF9CDCFE)     // light blue — keys
private val JsonStringColor = Color(0xFFCE9178)   // orange — string values
private val JsonNumberColor = Color(0xFFB5CEA8)   // green — numbers
private val JsonBoolColor = Color(0xFF569CD6)     // blue — true/false/null
private val JsonBracketColor = Color(0xFFFFD700)  // gold — brackets
private val JsonPunctColor = Color(0xFFD4D4D4)    // default — colon, comma

private fun highlightJson(line: String): androidx.compose.ui.text.AnnotatedString {
    return androidx.compose.ui.text.buildAnnotatedString {
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '{' || c == '}' || c == '[' || c == ']' -> {
                    withStyle(androidx.compose.ui.text.SpanStyle(color = JsonBracketColor)) { append(c) }; i++
                }
                c == '"' -> {
                    val end = line.indexOf('"', i + 1)
                    if (end > i) {
                        val str = line.substring(i, end + 1)
                        // Check if this is a key (followed by ':')
                        val afterQuote = line.substring(end + 1).trimStart()
                        val isKey = afterQuote.startsWith(":")
                        withStyle(androidx.compose.ui.text.SpanStyle(color = if (isKey) JsonKeyColor else JsonStringColor)) { append(str) }
                        i = end + 1
                    } else { withStyle(androidx.compose.ui.text.SpanStyle(color = JsonStringColor)) { append(line.substring(i)) }; i = line.length }
                }
                c.isDigit() || (c == '-' && i + 1 < line.length && line[i + 1].isDigit()) -> {
                    val start = i
                    if (c == '-') i++
                    while (i < line.length && (line[i].isDigit() || line[i] == '.' || line[i] == 'e' || line[i] == 'E' || line[i] == '+' || line[i] == '-')) i++
                    withStyle(androidx.compose.ui.text.SpanStyle(color = JsonNumberColor)) { append(line.substring(start, i)) }
                }
                line.startsWith("true", i) || line.startsWith("false", i) || line.startsWith("null", i) -> {
                    val word = when { line.startsWith("true", i) -> "true"; line.startsWith("false", i) -> "false"; else -> "null" }
                    withStyle(androidx.compose.ui.text.SpanStyle(color = JsonBoolColor, fontWeight = FontWeight.SemiBold)) { append(word) }
                    i += word.length
                }
                else -> { withStyle(androidx.compose.ui.text.SpanStyle(color = JsonPunctColor)) { append(c) }; i++ }
            }
        }
    }
}

private val XmlTagColor = Color(0xFF569CD6)       // blue — tag names
private val XmlAttrColor = Color(0xFF9CDCFE)      // light blue — attributes
private val XmlStringColor = Color(0xFFCE9178)    // orange — attribute values
private val XmlBracketColor = Color(0xFF808080)   // gray — < > / =
private val XmlTextColor = Color(0xFFD4D4D4)      // default — text content
private val XmlCommentColor = Color(0xFF6A9955)   // green — comments

private fun highlightXml(line: String): androidx.compose.ui.text.AnnotatedString {
    return androidx.compose.ui.text.buildAnnotatedString {
        var i = 0
        while (i < line.length) {
            when {
                line.startsWith("<!--", i) -> {
                    val end = line.indexOf("-->", i)
                    val comment = if (end >= 0) line.substring(i, end + 3) else line.substring(i)
                    withStyle(androidx.compose.ui.text.SpanStyle(color = XmlCommentColor)) { append(comment) }
                    i += comment.length
                }
                line[i] == '<' -> {
                    // Find end of tag
                    val end = line.indexOf('>', i)
                    if (end > i) {
                        val tag = line.substring(i, end + 1)
                        highlightXmlTag(this, tag)
                        i = end + 1
                    } else { withStyle(androidx.compose.ui.text.SpanStyle(color = XmlBracketColor)) { append(line.substring(i)) }; i = line.length }
                }
                else -> {
                    val next = line.indexOf('<', i)
                    val text = if (next >= 0) line.substring(i, next) else line.substring(i)
                    withStyle(androidx.compose.ui.text.SpanStyle(color = XmlTextColor)) { append(text) }
                    i += text.length
                }
            }
        }
    }
}

private fun highlightXmlTag(builder: androidx.compose.ui.text.AnnotatedString.Builder, tag: String) {
    builder.apply {
        var i = 0
        // Opening bracket(s)
        if (tag.startsWith("</")) { withStyle(androidx.compose.ui.text.SpanStyle(color = XmlBracketColor)) { append("</") }; i = 2 }
        else if (tag.startsWith("<?")) { withStyle(androidx.compose.ui.text.SpanStyle(color = XmlBracketColor)) { append("<?") }; i = 2 }
        else { withStyle(androidx.compose.ui.text.SpanStyle(color = XmlBracketColor)) { append("<") }; i = 1 }

        // Tag name
        val nameEnd = tag.indexOfFirst { it == ' ' || it == '>' || it == '/' || it == '?' }.let { if (it < 0) tag.length else it }
        if (i < nameEnd) {
            withStyle(androidx.compose.ui.text.SpanStyle(color = XmlTagColor)) { append(tag.substring(i, nameEnd)) }
            i = nameEnd
        }

        // Attributes
        while (i < tag.length) {
            val c = tag[i]
            when {
                c == '>' || (c == '/' && i + 1 < tag.length && tag[i + 1] == '>') || (c == '?' && i + 1 < tag.length && tag[i + 1] == '>') -> {
                    withStyle(androidx.compose.ui.text.SpanStyle(color = XmlBracketColor)) { append(tag.substring(i)) }; return
                }
                c == '"' || c == '\'' -> {
                    val end = tag.indexOf(c, i + 1)
                    val str = if (end >= 0) tag.substring(i, end + 1) else tag.substring(i)
                    withStyle(androidx.compose.ui.text.SpanStyle(color = XmlStringColor)) { append(str) }
                    i += str.length
                }
                c == '=' -> { withStyle(androidx.compose.ui.text.SpanStyle(color = XmlBracketColor)) { append("=") }; i++ }
                c == ' ' -> { append(" "); i++ }
                else -> {
                    // Attribute name
                    val attrEnd = tag.indexOfAny(charArrayOf('=', ' ', '>', '/'), i).let { if (it < 0) tag.length else it }
                    withStyle(androidx.compose.ui.text.SpanStyle(color = XmlAttrColor)) { append(tag.substring(i, attrEnd)) }
                    i = attrEnd
                }
            }
        }
    }
}

private val CodeKeywordColor = Color(0xFF569CD6)
private val CodeStringColor = Color(0xFFCE9178)
private val CodeCommentColor = Color(0xFF6A9955)
private val CodeNumberColor = Color(0xFFB5CEA8)
private val CodeDefaultColor = Color(0xFFD4D4D4)

private val codeKeywords = setOf(
    "fun", "val", "var", "class", "object", "if", "else", "when", "for", "while", "return", "import", "package",
    "private", "public", "protected", "internal", "override", "suspend", "data", "sealed", "enum", "interface",
    "abstract", "companion", "const", "lateinit", "by", "lazy", "null", "true", "false", "this", "super", "is", "as", "in",
    "def", "self", "lambda", "try", "catch", "finally", "throw", "new", "static", "final", "void", "int", "long",
    "double", "float", "boolean", "char", "string", "let", "const", "function", "async", "await", "export",
    "default", "from", "extends", "implements", "break", "continue", "do", "switch", "case", "typeof", "instanceof"
)

private fun highlightCode(line: String): androidx.compose.ui.text.AnnotatedString {
    return androidx.compose.ui.text.buildAnnotatedString {
        // Check for line comment
        val commentIdx = line.indexOf("//")
        val hashComment = if (!line.trimStart().startsWith("#!")) line.indexOf("#").takeIf { it >= 0 && !line.substring(0, it).contains('"') } else null
        val activeComment = listOfNotNull(commentIdx.takeIf { it >= 0 }, hashComment).minOrNull()

        val codePart = if (activeComment != null) line.substring(0, activeComment) else line
        val commentPart = if (activeComment != null) line.substring(activeComment) else ""

        // Tokenize code part
        var i = 0
        while (i < codePart.length) {
            val c = codePart[i]
            when {
                c == '"' || c == '\'' -> {
                    val end = codePart.indexOf(c, i + 1)
                    val str = if (end >= 0) codePart.substring(i, end + 1) else codePart.substring(i)
                    withStyle(androidx.compose.ui.text.SpanStyle(color = CodeStringColor)) { append(str) }
                    i += str.length
                }
                c.isDigit() -> {
                    val start = i
                    while (i < codePart.length && (codePart[i].isDigit() || codePart[i] == '.' || codePart[i] == 'f' || codePart[i] == 'L' || codePart[i] == 'x')) i++
                    withStyle(androidx.compose.ui.text.SpanStyle(color = CodeNumberColor)) { append(codePart.substring(start, i)) }
                }
                c.isLetter() || c == '_' -> {
                    val start = i
                    while (i < codePart.length && (codePart[i].isLetterOrDigit() || codePart[i] == '_')) i++
                    val word = codePart.substring(start, i)
                    val color = if (word in codeKeywords) CodeKeywordColor else CodeDefaultColor
                    val weight = if (word in codeKeywords) FontWeight.SemiBold else FontWeight.Normal
                    withStyle(androidx.compose.ui.text.SpanStyle(color = color, fontWeight = weight)) { append(word) }
                }
                else -> { withStyle(androidx.compose.ui.text.SpanStyle(color = CodeDefaultColor)) { append(c) }; i++ }
            }
        }

        // Comment part
        if (commentPart.isNotEmpty()) {
            withStyle(androidx.compose.ui.text.SpanStyle(color = CodeCommentColor)) { append(commentPart) }
        }
    }
}

// ═══════════════════════════════════
// Open file externally
// ═══════════════════════════════════
fun openFileExternal(context: Context, file: File) {
    try {
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension) ?: "*/*"
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Open"))
    } catch (_: Exception) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.fromFile(file), "*/*")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "No app to open this file", Toast.LENGTH_SHORT).show()
        }
    }
}

fun installApk(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "${Strings.error}: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
