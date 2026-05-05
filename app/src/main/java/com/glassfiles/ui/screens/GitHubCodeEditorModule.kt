package com.glassfiles.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FindInPage
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Preview
import androidx.compose.material.icons.rounded.Redo
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.WrapText
import androidx.compose.material.icons.rounded.ZoomOutMap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.glassfiles.data.Strings
import com.glassfiles.data.github.GHContent
import com.glassfiles.data.github.GitHubManager
import com.glassfiles.ui.components.AiModuleAlertDialog
import com.glassfiles.ui.components.AiModuleGlyph
import com.glassfiles.ui.components.AiModuleGlyphAction
import com.glassfiles.ui.components.AiModuleIcon as Icon
import com.glassfiles.ui.components.AiModuleIconButton as IconButton
import com.glassfiles.ui.components.AiModulePageBar
import com.glassfiles.ui.components.AiModuleSearchField
import com.glassfiles.ui.components.AiModuleSpinner
import com.glassfiles.ui.components.AiModuleText as Text
import com.glassfiles.ui.components.AiModuleTextAction
import com.glassfiles.ui.components.AiModuleTextField
import com.glassfiles.ui.theme.AiModuleSurface
import com.glassfiles.ui.theme.AiModuleTheme
import com.glassfiles.ui.theme.Blue
import com.glassfiles.ui.theme.JetBrainsMono
import com.glassfiles.ui.theme.SeparatorColor
import com.glassfiles.ui.theme.TextSecondary
import com.glassfiles.ui.theme.TextTertiary
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private enum class GitHubEditorMode { EDIT, READ, PREVIEW }

private data class EditorSearchMatch(val start: Int, val end: Int, val line: Int)

private data class EditorSymbol(val name: String, val kind: String, val line: Int)

@Composable
fun CodeEditorScreen(
    repoOwner: String,
    repoName: String,
    file: GHContent,
    branch: String,
    initialContent: String,
    onBack: () -> Unit,
    onAskAi: ((prompt: String?) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    var textState by rememberSaveable(file.path, branch, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initialContent))
    }
    var savedContent by rememberSaveable(file.path, branch) { mutableStateOf(initialContent) }
    var savedSha by rememberSaveable(file.path, branch) { mutableStateOf(file.sha) }
    var mode by rememberSaveable(file.path, branch) { mutableStateOf(GitHubEditorMode.EDIT) }
    var lineNumbers by rememberSaveable(file.path, branch) { mutableStateOf(true) }
    var wrapLines by rememberSaveable(file.path, branch) { mutableStateOf(false) }
    var showSearch by rememberSaveable(file.path, branch) { mutableStateOf(false) }
    var searchState by rememberSaveable(file.path, branch, stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var replaceState by rememberSaveable(file.path, branch, stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var showGoToLine by rememberSaveable(file.path, branch) { mutableStateOf(false) }
    var showOutline by rememberSaveable(file.path, branch) { mutableStateOf(false) }
    var showDiscardDialog by rememberSaveable(file.path, branch) { mutableStateOf(false) }
    var goToLineText by rememberSaveable(file.path, branch) { mutableStateOf("") }
    var commitMessage by rememberSaveable(file.path, branch) { mutableStateOf("Update ${file.name}") }
    var showCommitDialog by rememberSaveable(file.path, branch) { mutableStateOf(false) }
    var saving by rememberSaveable(file.path, branch) { mutableStateOf(false) }
    var currentMatchIndex by rememberSaveable(file.path, branch) { mutableIntStateOf(0) }
    var fontSize by rememberSaveable(file.path, branch) { mutableIntStateOf(13) }

    val ext = remember(file.name) { file.name.substringAfterLast(".", "").lowercase() }
    val isImage = ext in listOf("png", "jpg", "jpeg", "gif", "webp", "svg", "ico")
    val isMarkdown = ext in listOf("md", "markdown")
    val text = textState.text
    val lines = remember(text) { text.lines() }
    val hasChanges = text != savedContent
    val commentPrefix = remember(ext) { commentPrefixForExtension(ext) }
    val currentLine = remember(textState.selection, text) {
        text.take(textState.selection.start.coerceIn(0, text.length)).count { it == '\n' } + 1
    }
    val currentColumn = remember(textState.selection, text) {
        val cursor = textState.selection.start.coerceIn(0, text.length)
        if (cursor == 0) 1 else cursor - text.lastIndexOf('\n', cursor - 1).let { if (it < 0) -1 else it }
    }
    val matches = remember(text, searchState.text) { buildSearchMatches(text, searchState.text) }
    val currentMatch = matches.getOrNull(currentMatchIndex)
    val symbols = remember(lines, ext) { buildEditorSymbols(lines, ext) }

    val undoStack = remember(file.path) { mutableStateListOf<TextFieldValue>() }
    val redoStack = remember(file.path) { mutableStateListOf<TextFieldValue>() }

    fun snapshot() {
        if (undoStack.lastOrNull()?.text != textState.text || undoStack.lastOrNull()?.selection != textState.selection) {
            undoStack.add(textState.copy())
            if (undoStack.size > 120) undoStack.removeAt(0)
        }
    }

    fun handleEditorBack() {
        when {
            showCommitDialog -> showCommitDialog = false
            showDiscardDialog -> showDiscardDialog = false
            showGoToLine -> showGoToLine = false
            showOutline -> showOutline = false
            showSearch -> showSearch = false
            hasChanges && !isImage -> showDiscardDialog = true
            else -> onBack()
        }
    }

    fun applyState(newState: TextFieldValue) {
        if (newState != textState) {
            snapshot()
            redoStack.clear()
            textState = newState
        }
    }

    fun applyEditorInput(newState: TextFieldValue) {
        val old = textState
        val insertedNewLine = newState.text.length == old.text.length + 1 &&
            newState.selection.start == newState.selection.end &&
            newState.selection.start > 0 &&
            newState.text.getOrNull(newState.selection.start - 1) == '\n'

        if (!insertedNewLine) {
            applyState(newState)
            return
        }

        val cursorAfterBreak = newState.selection.start
        val previousLineEnd = (cursorAfterBreak - 2).coerceAtLeast(0)
        val previousLineStart = old.text.lastIndexOf('\n', previousLineEnd).let { if (it < 0) 0 else it + 1 }
        val previousLine = old.text.substring(previousLineStart, (previousLineEnd + 1).coerceAtMost(old.text.length))
        val indent = previousLine.takeWhile { it == ' ' || it == '\t' }
        val extra = if (previousLine.trimEnd().endsWith("{") || previousLine.trimEnd().endsWith("[") || previousLine.trimEnd().endsWith("(")) "    " else ""
        val insert = indent + extra
        if (insert.isEmpty()) {
            applyState(newState)
            return
        }
        val adjusted = newState.text.substring(0, cursorAfterBreak) + insert + newState.text.substring(cursorAfterBreak)
        applyState(TextFieldValue(adjusted, TextRange(cursorAfterBreak + insert.length)))
    }

    fun insertText(value: String) {
        val start = minOf(textState.selection.start, textState.selection.end).coerceAtLeast(0)
        val end = maxOf(textState.selection.start, textState.selection.end).coerceAtLeast(0)
        val newText = textState.text.replaceRange(start, end, value)
        val cursor = (start + value.length).coerceAtMost(newText.length)
        applyState(TextFieldValue(newText, TextRange(cursor)))
    }

    fun insertPair(open: String, close: String) {
        val start = minOf(textState.selection.start, textState.selection.end).coerceAtLeast(0)
        val end = maxOf(textState.selection.start, textState.selection.end).coerceAtLeast(0)
        val selected = textState.text.substring(start, end.coerceAtMost(textState.text.length))
        val newText = textState.text.replaceRange(start, end, open + selected + close)
        val selection = if (selected.isEmpty()) TextRange((start + open.length).coerceAtMost(newText.length))
        else TextRange(start + open.length, start + open.length + selected.length)
        applyState(TextFieldValue(newText, selection))
    }

    fun duplicateLine() {
        val lineIndex = (currentLine - 1).coerceIn(0, lines.lastIndex.coerceAtLeast(0))
        val target = lines.getOrElse(lineIndex) { "" }
        val before = lines.take(lineIndex + 1).joinToString("\n")
        val after = lines.drop(lineIndex + 1).joinToString("\n")
        val newText = buildString {
            append(before)
            append("\n")
            append(target)
            if (after.isNotBlank()) append("\n$after")
        }
        val newOffset = lines.take(lineIndex + 1).sumOf { it.length + 1 }.coerceAtMost(newText.length)
        applyState(TextFieldValue(newText, TextRange(newOffset)))
    }

    fun toggleComment() {
        val prefix = commentPrefix ?: return
        val selection = textState.selection
        val startOffset = minOf(selection.start, selection.end).coerceIn(0, text.length)
        val endOffset = maxOf(selection.start, selection.end).coerceIn(0, text.length)
        val startLine = text.take(startOffset).count { it == '\n' }
        val endLine = text.take(endOffset).count { it == '\n' }
        val mutableLines = lines.toMutableList()
        val range = startLine..endLine.coerceAtMost(mutableLines.lastIndex.coerceAtLeast(0))
        val uncomment = range.all { idx -> mutableLines[idx].trimStart().startsWith(prefix) }
        range.forEach { idx ->
            val original = mutableLines[idx]
            val indent = original.takeWhile { it == ' ' || it == '\t' }
            val trimmed = original.removePrefix(indent)
            mutableLines[idx] = if (uncomment) {
                if (trimmed.startsWith(prefix)) indent + trimmed.removePrefix(prefix).removePrefix(" ") else original
            } else {
                indent + prefix + if (trimmed.isNotBlank()) " $trimmed" else ""
            }
        }
        val newText = mutableLines.joinToString("\n")
        applyState(TextFieldValue(newText, selection.coerceIn(newText.length)))
    }

    fun goToLine(lineNumber: Int) {
        val safe = lineNumber.coerceIn(1, lines.size.coerceAtLeast(1))
        val offset = lines.take(safe - 1).sumOf { it.length + 1 }.coerceAtMost(text.length)
        textState = textState.copy(selection = TextRange(offset))
    }

    fun goToOffset(offset: Int) {
        textState = textState.copy(selection = TextRange(offset.coerceIn(0, text.length)))
    }

    fun selectedLineRange(): IntRange {
        val start = minOf(textState.selection.start, textState.selection.end).coerceIn(0, text.length)
        val end = maxOf(textState.selection.start, textState.selection.end).coerceIn(0, text.length)
        val startLine = text.take(start).count { it == '\n' }
        val endLine = text.take(end).count { it == '\n' }.coerceAtMost(lines.lastIndex.coerceAtLeast(0))
        return startLine..endLine
    }

    fun transformSelectedLines(transform: (String) -> String) {
        val range = selectedLineRange()
        val mutableLines = lines.toMutableList()
        range.forEach { idx ->
            if (idx in mutableLines.indices) mutableLines[idx] = transform(mutableLines[idx])
        }
        val newText = mutableLines.joinToString("\n")
        applyState(TextFieldValue(newText, textState.selection.coerceIn(newText.length)))
    }

    fun selectAll() {
        textState = textState.copy(selection = TextRange(0, text.length))
    }

    fun formatJsonDocument() {
        if (ext != "json") return
        val formatted = try {
            val trimmed = text.trim()
            when {
                trimmed.startsWith("[") -> JSONArray(trimmed).toString(2)
                trimmed.startsWith("{") -> JSONObject(trimmed).toString(2)
                else -> null
            }
        } catch (_: Exception) {
            null
        }
        if (formatted == null) {
            Toast.makeText(context, "Invalid JSON", Toast.LENGTH_SHORT).show()
            return
        }
        applyState(TextFieldValue(formatted, TextRange(0)))
    }

    fun replaceCurrent() {
        val query = searchState.text
        if (query.isBlank() || matches.isEmpty()) return
        val target = matches.getOrNull(currentMatchIndex) ?: return
        val newText = text.replaceRange(target.start, target.end, replaceState.text)
        val start = target.start
        applyState(TextFieldValue(newText, TextRange((start + replaceState.text.length).coerceAtMost(newText.length))))
    }

    fun replaceAll() {
        val query = searchState.text
        if (query.isBlank()) return
        val newText = text.replace(query, replaceState.text, ignoreCase = true)
        applyState(TextFieldValue(newText, textState.selection.coerceIn(newText.length)))
    }

    LaunchedEffect(matches) {
        if (matches.isEmpty()) currentMatchIndex = 0
        else if (currentMatchIndex !in matches.indices) currentMatchIndex = 0
    }

    if (showGoToLine) {
        AiModuleAlertDialog(
            onDismissRequest = { showGoToLine = false },
            title = "go to line",
            content = {
                AiModuleTextField(
                    value = goToLineText,
                    onValueChange = { goToLineText = it.filter(Char::isDigit) },
                    label = "Line",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                AiModuleTextAction(label = "go", onClick = {
                    goToLine(goToLineText.toIntOrNull() ?: 1)
                    showGoToLine = false
                })
            },
            dismissButton = { AiModuleTextAction(label = Strings.cancel.lowercase(), onClick = { showGoToLine = false }, tint = AiModuleTheme.colors.textSecondary) }
        )
    }

    if (showOutline) {
        AiModuleAlertDialog(
            onDismissRequest = { showOutline = false },
            title = "outline",
            content = {
                if (symbols.isEmpty()) {
                    Text("No symbols found", color = TextSecondary, fontSize = 13.sp)
                } else {
                    LazyColumn(
                        Modifier.fillMaxWidth().height(340.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        itemsIndexed(symbols) { _, symbol ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF111B2E))
                                    .clickable {
                                        goToLine(symbol.line + 1)
                                        showOutline = false
                                    }
                                    .padding(horizontal = 10.dp, vertical = 9.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                MetaPill(symbol.kind, Blue)
                                Text(symbol.name, color = Color(0xFFE5E7EB), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Text("${symbol.line + 1}", color = TextTertiary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
            },
            confirmButton = { AiModuleTextAction(label = "close", onClick = { showOutline = false }) }
        )
    }

    if (showDiscardDialog) {
        AiModuleAlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = "discard changes?",
            content = { Text("This file has uncommitted edits.", color = TextSecondary, fontSize = 13.sp, fontFamily = JetBrainsMono) },
            confirmButton = {
                AiModuleTextAction(label = "discard", tint = AiModuleTheme.colors.error, onClick = {
                    showDiscardDialog = false
                    onBack()
                })
            },
            dismissButton = { AiModuleTextAction(label = Strings.cancel.lowercase(), onClick = { showDiscardDialog = false }, tint = AiModuleTheme.colors.textSecondary) }
        )
    }

    AiModuleSurface {
    val palette = AiModuleTheme.colors
    Column(Modifier.fillMaxSize().background(palette.background)) {
        GitHubEditorTopBar(
            fileName = file.name,
            subtitle = buildEditorSubtitle(ext, lines.size, text.length, hasChanges),
            isImage = isImage,
            isMarkdown = isMarkdown,
            mode = mode,
            showSearch = showSearch,
            lineNumbers = lineNumbers,
            wrapLines = wrapLines,
            hasChanges = hasChanges,
            canUndo = undoStack.isNotEmpty(),
            canRedo = redoStack.isNotEmpty(),
            hasOutline = symbols.isNotEmpty(),
            onBack = ::handleEditorBack,
            onToggleSearch = { showSearch = !showSearch },
            onGoTo = { showGoToLine = true },
            onOutline = { showOutline = true },
            onCopy = {
                clipboard.setText(AnnotatedString(text))
                Toast.makeText(context, Strings.copied, Toast.LENGTH_SHORT).show()
            },
            onToggleLineNumbers = { lineNumbers = !lineNumbers },
            onToggleWrap = { wrapLines = !wrapLines },
            onUndo = {
                if (undoStack.isNotEmpty()) {
                    redoStack.add(textState.copy())
                    textState = undoStack.removeLast()
                }
            },
            onRedo = {
                if (redoStack.isNotEmpty()) {
                    undoStack.add(textState.copy())
                    textState = redoStack.removeLast()
                }
            },
            onCycleMode = {
                mode = when (mode) {
                    GitHubEditorMode.EDIT -> if (isMarkdown) GitHubEditorMode.PREVIEW else GitHubEditorMode.READ
                    GitHubEditorMode.PREVIEW -> GitHubEditorMode.READ
                    GitHubEditorMode.READ -> GitHubEditorMode.EDIT
                }
            },
            onSave = { showCommitDialog = true },
            onOpenImage = { openUrl(context, file.downloadUrl) },
            onAskAi = onAskAi
        )

        if (!isImage) {
            EditorInfoStrip(
                mode = mode,
                currentLine = currentLine,
                currentColumn = currentColumn,
                selectionLength = textState.selection.length,
                matchCount = matches.size,
                currentMatchNumber = if (matches.isEmpty()) 0 else currentMatchIndex + 1,
                onSetMode = { mode = it },
                isMarkdown = isMarkdown,
                hasChanges = hasChanges
            )
            if (onAskAi != null) {
                // C2 — when the user has highlighted a region, expose a
                // dedicated "Send selection" chip that ships the
                // selected snippet to the agent inline. Falls through
                // to the standard quick-action prompts when there is
                // no selection.
                val selectedRange = textState.selection
                val selectedText = run {
                    val src = textState.text
                    val start = minOf(selectedRange.start, selectedRange.end)
                        .coerceIn(0, src.length)
                    val end = maxOf(selectedRange.start, selectedRange.end)
                        .coerceIn(0, src.length)
                    if (end > start) src.substring(start, end) else ""
                }
                AiQuickActionsRow(
                    filePath = file.path,
                    branch = branch,
                    selectedText = selectedText,
                    onSendPrompt = { onAskAi(it) },
                )
            }
        }

        AnimatedVisibility(showSearch && !isImage) {
            SearchReplaceCard(
                searchState = searchState,
                replaceState = replaceState,
                matchCount = matches.size,
                currentMatch = if (matches.isEmpty()) 0 else currentMatchIndex + 1,
                onSearchChange = { searchState = it },
                onReplaceChange = { replaceState = it },
                onPrev = {
                    if (matches.isNotEmpty()) {
                        currentMatchIndex = (currentMatchIndex - 1).floorMod(matches.size)
                        goToOffset(matches[currentMatchIndex].start)
                    }
                },
                onNext = {
                    if (matches.isNotEmpty()) {
                        currentMatchIndex = (currentMatchIndex + 1).floorMod(matches.size)
                        goToOffset(matches[currentMatchIndex].start)
                    }
                },
                onReplaceOne = { replaceCurrent() },
                onReplaceAll = { replaceAll() }
            )
        }

        if (!isImage && mode == GitHubEditorMode.EDIT) {
            EditorActionRibbon(
                onInsert = { insertText(it) },
                onInsertPair = { open, close -> insertPair(open, close) },
                onDuplicate = { duplicateLine() },
                onComment = if (commentPrefix != null) ({ toggleComment() }) else null,
                onIndent = { transformSelectedLines { "    $it" } },
                onOutdent = { transformSelectedLines { line -> if (line.startsWith("    ")) line.drop(4) else if (line.startsWith("\t")) line.drop(1) else line } },
                onTrim = { transformSelectedLines { it.trimEnd() } },
                onSelectAll = { selectAll() },
                onFormatJson = if (ext == "json") ({ formatJsonDocument() }) else null,
                onFontSmaller = { fontSize = (fontSize - 1).coerceAtLeast(11) },
                onFontLarger = { fontSize = (fontSize + 1).coerceAtMost(20) }
            )
        }

        Box(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xFF0F172A))
        ) {
            when {
                isImage -> ModernImageCanvas(file)
                isMarkdown && mode == GitHubEditorMode.PREVIEW -> ModernMarkdownCanvas(lines)
                mode == GitHubEditorMode.READ -> ModernReadCanvas(lines, ext, lineNumbers, wrapLines, currentMatch?.line)
                else -> ModernEditCanvas(
                    textState = textState,
                    lines = lines,
                    lineNumbers = lineNumbers,
                    wrapLines = wrapLines,
                    ext = ext,
                    fontSize = fontSize,
                    searchQuery = searchState.text,
                    currentHighlightedLine = currentMatch?.line,
                    currentMatchRange = currentMatch?.let { it.start until it.end },
                    onValueChange = { applyEditorInput(it) }
                )
            }
        }
    }
    }

    if (showCommitDialog) {
        var aiSuggesting by remember { mutableStateOf(false) }
        var aiSuggestError by remember { mutableStateOf<String?>(null) }
        AiModuleAlertDialog(
            onDismissRequest = { showCommitDialog = false },
            title = "commit changes",
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(file.path, fontSize = 12.sp, color = TextSecondary, fontFamily = JetBrainsMono)
                    AiModuleTextField(
                        value = commitMessage,
                        onValueChange = { commitMessage = it },
                        label = "Commit message",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AiModuleTextAction(
                            label = if (aiSuggesting) Strings.aiCommitMsgGenerating else Strings.aiCommitMsgGenerate,
                            enabled = !aiSuggesting,
                            onClick = {
                                aiSuggesting = true
                                aiSuggestError = null
                                scope.launch {
                                    try {
                                        commitMessage = generateCommitMessage(
                                            context = context,
                                            path = file.path,
                                            oldText = savedContent,
                                            newText = text,
                                        )
                                    } catch (e: Exception) {
                                        aiSuggestError = e.message ?: e.javaClass.simpleName
                                    } finally {
                                        aiSuggesting = false
                                    }
                                }
                            },
                        )
                        aiSuggestError?.let { err ->
                            Spacer(Modifier.width(8.dp))
                            Text(
                                err.take(60),
                                fontSize = 11.sp,
                                color = AiModuleTheme.colors.error,
                                fontFamily = JetBrainsMono,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                AiModuleTextAction(label = if (saving) "committing" else "commit", enabled = !saving, onClick = {
                    if (commitMessage.isBlank()) {
                        Toast.makeText(context, "Commit message required", Toast.LENGTH_SHORT).show()
                        return@AiModuleTextAction
                    }
                    saving = true
                    scope.launch {
                        val result = GitHubManager.uploadFileWithResult(
                            context = context,
                            owner = repoOwner,
                            repo = repoName,
                            path = file.path,
                            content = text.toByteArray(),
                            message = commitMessage,
                            branch = branch,
                            sha = savedSha
                        )
                        saving = false
                        showCommitDialog = false
                        if (result.success) {
                            savedContent = text
                            if (result.sha.isNotBlank()) savedSha = result.sha
                            undoStack.clear()
                            redoStack.clear()
                            Toast.makeText(context, "Committed successfully", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Failed to commit", Toast.LENGTH_SHORT).show()
                        }
                    }
                })
            },
            dismissButton = { AiModuleTextAction(label = Strings.cancel.lowercase(), onClick = { showCommitDialog = false }, tint = AiModuleTheme.colors.textSecondary) }
        )
    }
}

@Composable
private fun GitHubEditorTopBar(
    fileName: String,
    subtitle: String,
    isImage: Boolean,
    isMarkdown: Boolean,
    mode: GitHubEditorMode,
    showSearch: Boolean,
    lineNumbers: Boolean,
    wrapLines: Boolean,
    hasChanges: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    hasOutline: Boolean,
    onBack: () -> Unit,
    onToggleSearch: () -> Unit,
    onGoTo: () -> Unit,
    onOutline: () -> Unit,
    onCopy: () -> Unit,
    onToggleLineNumbers: () -> Unit,
    onToggleWrap: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onCycleMode: () -> Unit,
    onSave: () -> Unit,
    onOpenImage: () -> Unit,
    onAskAi: ((prompt: String?) -> Unit)? = null
) {
    val palette = AiModuleTheme.colors
    GitHubPageBar(
        title = "> ${fileName}",
        subtitle = subtitle,
        onBack = onBack,
        trailing = {
            if (onAskAi != null) {
                AiModuleGlyphAction(
                    glyph = GhGlyphs.AI,
                    onClick = { onAskAi(null) },
                    tint = palette.accent,
                    contentDescription = "ask ai",
                )
            }
            if (!isImage) {
                AiModuleGlyphAction(
                    glyph = GhGlyphs.SEARCH,
                    onClick = onToggleSearch,
                    tint = if (showSearch) palette.accent else palette.textSecondary,
                    contentDescription = "search",
                )
                AiModuleGlyphAction(
                    glyph = GhGlyphs.HASH,
                    onClick = onGoTo,
                    tint = palette.textSecondary,
                    contentDescription = "go to line",
                )
                if (hasOutline) {
                    AiModuleGlyphAction(
                        glyph = GhGlyphs.OUTLINE,
                        onClick = onOutline,
                        tint = palette.textSecondary,
                        contentDescription = "outline",
                    )
                }
                AiModuleGlyphAction(
                    glyph = GhGlyphs.COPY,
                    onClick = onCopy,
                    tint = palette.textSecondary,
                    fontSize = 12.sp,
                    contentDescription = "copy",
                )
                AiModuleGlyphAction(
                    glyph = GhGlyphs.LINES,
                    onClick = onToggleLineNumbers,
                    tint = if (lineNumbers) palette.accent else palette.textSecondary,
                    fontSize = 12.sp,
                    contentDescription = "toggle line numbers",
                )
                AiModuleGlyphAction(
                    glyph = GhGlyphs.WRAP,
                    onClick = onToggleWrap,
                    tint = if (wrapLines) palette.accent else palette.textSecondary,
                    contentDescription = "wrap",
                )
                if (canUndo) {
                    AiModuleGlyphAction(
                        glyph = GhGlyphs.UNDO,
                        onClick = onUndo,
                        tint = palette.accent,
                        contentDescription = "undo",
                    )
                }
                if (canRedo) {
                    AiModuleGlyphAction(
                        glyph = GhGlyphs.REDO,
                        onClick = onRedo,
                        tint = palette.accent,
                        contentDescription = "redo",
                    )
                }
                AiModuleGlyphAction(
                    glyph = GhGlyphs.CYCLE,
                    onClick = onCycleMode,
                    tint = palette.accent,
                    contentDescription = "cycle mode",
                )
                if (hasChanges) {
                    AiModuleGlyphAction(
                        glyph = GhGlyphs.SAVE,
                        onClick = onSave,
                        tint = GitHubSuccessGreen,
                        contentDescription = "save",
                    )
                }
            } else {
                AiModuleGlyphAction(
                    glyph = GhGlyphs.DOWNLOAD,
                    onClick = onOpenImage,
                    tint = palette.accent,
                    fontSize = 12.sp,
                    contentDescription = "download",
                )
            }
        },
    )
}

@Composable
private fun EditorInfoStrip(
    mode: GitHubEditorMode,
    currentLine: Int,
    currentColumn: Int,
    selectionLength: Int,
    matchCount: Int,
    currentMatchNumber: Int,
    onSetMode: (GitHubEditorMode) -> Unit,
    isMarkdown: Boolean,
    hasChanges: Boolean
) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ModePill("Edit", mode == GitHubEditorMode.EDIT) { onSetMode(GitHubEditorMode.EDIT) }
        ModePill("Read", mode == GitHubEditorMode.READ) { onSetMode(GitHubEditorMode.READ) }
        if (isMarkdown) ModePill("Preview", mode == GitHubEditorMode.PREVIEW) { onSetMode(GitHubEditorMode.PREVIEW) }
        MetaPill("Ln $currentLine", Blue)
        MetaPill("Col $currentColumn", TextSecondary)
        if (selectionLength > 0) MetaPill("$selectionLength sel", Color(0xFF34C759))
        if (matchCount > 0) MetaPill("$currentMatchNumber/$matchCount", Color(0xFF58A6FF))
        if (hasChanges) MetaPill("Modified", Color(0xFFFF6B6B))
    }
}

@Composable
private fun ModePill(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Blue.copy(alpha = 0.16f) else Color(0xFF182235))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(label, color = if (selected) Blue else TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun MetaPill(text: String, color: Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) {
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SearchReplaceCard(
    searchState: TextFieldValue,
    replaceState: TextFieldValue,
    matchCount: Int,
    currentMatch: Int,
    onSearchChange: (TextFieldValue) -> Unit,
    onReplaceChange: (TextFieldValue) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onReplaceOne: () -> Unit,
    onReplaceAll: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF111B2E))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            SearchBox(searchState, onSearchChange, "Find", Modifier.weight(1f))
            Text(if (matchCount == 0) "0" else "$currentMatch/$matchCount", color = TextSecondary, fontSize = 12.sp)
            IconButton(onClick = onPrev, enabled = matchCount > 0) { Icon(Icons.Rounded.ArrowUpward, null, tint = if (matchCount > 0) Blue else TextTertiary) }
            IconButton(onClick = onNext, enabled = matchCount > 0) { Icon(Icons.Rounded.ArrowDownward, null, tint = if (matchCount > 0) Blue else TextTertiary) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            SearchBox(replaceState, onReplaceChange, "Replace", Modifier.weight(1f))
            SmallPillButton("1", onReplaceOne)
            SmallPillButton("All", onReplaceAll)
        }
    }
}

@Composable
private fun SearchBox(value: TextFieldValue, onValueChange: (TextFieldValue) -> Unit, hint: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1B2740))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        if (value.text.isEmpty()) Text(hint, color = TextTertiary, fontSize = 13.sp)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = Color(0xFFE5E7EB), fontSize = 13.sp),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SmallPillButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Blue.copy(alpha = 0.16f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(label, color = Blue, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun EditorActionRibbon(
    onInsert: (String) -> Unit,
    onInsertPair: (String, String) -> Unit,
    onDuplicate: () -> Unit,
    onComment: (() -> Unit)?,
    onIndent: () -> Unit,
    onOutdent: () -> Unit,
    onTrim: () -> Unit,
    onSelectAll: () -> Unit,
    onFormatJson: (() -> Unit)?,
    onFontSmaller: () -> Unit,
    onFontLarger: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (onComment != null) EditorActionChip("Comment") { onComment() }
        EditorActionChip("Duplicate") { onDuplicate() }
        EditorActionChip("Indent") { onIndent() }
        EditorActionChip("Outdent") { onOutdent() }
        EditorActionChip("Trim") { onTrim() }
        EditorActionChip("All") { onSelectAll() }
        if (onFormatJson != null) EditorActionChip("Format JSON") { onFormatJson() }
        EditorActionChip("A-") { onFontSmaller() }
        EditorActionChip("A+") { onFontLarger() }
        EditorActionChip("{}") { onInsertPair("{", "}") }
        EditorActionChip("()") { onInsertPair("(", ")") }
        EditorActionChip("[]") { onInsertPair("[", "]") }
        EditorActionChip("\"\"") { onInsertPair("\"", "\"") }
        EditorActionChip("''") { onInsertPair("'", "'") }
        EditorActionChip("/* */") { onInsertPair("/* ", " */") }
        EditorActionChip("TAB") { onInsert("\t") }
        EditorActionChip("fun") { onInsert("fun ") }
        EditorActionChip("val") { onInsert("val ") }
        EditorActionChip("var") { onInsert("var ") }
        EditorActionChip("=>") { onInsert(" => ") }
        EditorActionChip("->") { onInsert(" -> ") }
        EditorActionChip("//") { onInsert("// ") }
    }
}

@Composable
private fun EditorActionChip(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF182235))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Text(text, color = Color(0xFFCBD5E1), fontFamily = FontFamily.Monospace, fontSize = 11.sp)
    }
}

@Composable
private fun ModernEditCanvas(
    textState: TextFieldValue,
    lines: List<String>,
    lineNumbers: Boolean,
    wrapLines: Boolean,
    ext: String,
    fontSize: Int,
    searchQuery: String,
    currentHighlightedLine: Int?,
    currentMatchRange: IntRange?,
    onValueChange: (TextFieldValue) -> Unit
) {
    val vertical = rememberScrollState()
    Row(Modifier.fillMaxSize().background(Color(0xFF0F172A))) {
        if (lineNumbers) {
            Column(
                Modifier
                    .width(56.dp)
                    .fillMaxSize()
                    .background(Color(0xFF111827))
                    .padding(top = 12.dp, end = 8.dp)
                    .verticalScroll(vertical)
            ) {
                lines.forEachIndexed { index, _ ->
                    val active = currentHighlightedLine == index
                    Text(
                        text = "${index + 1}",
                        color = if (active) Blue else Color(0xFF64748B),
                        fontSize = (fontSize - 1).coerceAtLeast(10).sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.End,
                        lineHeight = (fontSize + 7).sp,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)
                    )
                }
            }
        }
        Box(Modifier.width(1.dp).fillMaxSize().background(SeparatorColor))
        val baseModifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp)
        val scrollModifier = if (wrapLines) baseModifier.verticalScroll(vertical) else baseModifier.verticalScroll(vertical).horizontalScroll(rememberScrollState())
        Row(Modifier.weight(1f).fillMaxSize()) {
            BasicTextField(
                value = textState,
                onValueChange = onValueChange,
                modifier = scrollModifier.weight(1f),
                textStyle = TextStyle(
                    color = Color(0xFFE5E7EB),
                    fontFamily = FontFamily.Monospace,
                    fontSize = fontSize.sp,
                    lineHeight = (fontSize + 7).sp
                ),
                visualTransformation = EditorSyntaxTransformation(ext, searchQuery, currentMatchRange),
                cursorBrush = SolidColor(Blue),
                decorationBox = { inner ->
                    Box(Modifier.fillMaxSize()) {
                        if (textState.text.isEmpty()) {
                            Text("Start typing...", color = Color(0xFF64748B), fontSize = fontSize.sp, fontFamily = FontFamily.Monospace)
                        }
                        inner()
                    }
                }
            )
            EditorMiniMap(lines, currentHighlightedLine)
        }
    }
}

@Composable
private fun EditorMiniMap(lines: List<String>, currentHighlightedLine: Int?) {
    Column(
        Modifier
            .width(12.dp)
            .fillMaxSize()
            .background(Color(0xFF0B1220))
            .padding(vertical = 12.dp, horizontal = 3.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        val sampled = remember(lines) {
            if (lines.size <= 80) lines.mapIndexed { index, line -> index to line }
            else lines.indices.step((lines.size / 80).coerceAtLeast(1)).map { it to lines[it] }.take(80)
        }
        sampled.forEach { (index, line) ->
            val active = currentHighlightedLine == index
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(
                        when {
                            active -> Blue
                            line.trimStart().startsWith("//") || line.trimStart().startsWith("#") -> Color(0xFF6A9955).copy(alpha = 0.75f)
                            line.isBlank() -> Color.Transparent
                            else -> Color(0xFF334155)
                        }
                    )
            )
        }
    }
}

@Composable
private fun ModernReadCanvas(
    lines: List<String>,
    ext: String,
    lineNumbers: Boolean,
    wrapLines: Boolean,
    currentHighlightedLine: Int?
) {
    SelectionContainer {
        LazyColumn(
            Modifier.fillMaxSize().background(Color(0xFF0F172A)).padding(horizontal = 12.dp, vertical = 12.dp),
            contentPadding = PaddingValues(bottom = 20.dp)
        ) {
            itemsIndexed(lines) { index, line ->
                val active = currentHighlightedLine == index
                Row(
                    Modifier.fillMaxWidth().background(if (active) Blue.copy(alpha = 0.10f) else Color.Transparent).padding(vertical = 1.dp)
                ) {
                    if (lineNumbers) {
                        Text(
                            text = "${index + 1}",
                            color = if (active) Blue else Color(0xFF64748B),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(44.dp).padding(end = 8.dp)
                        )
                    }
                    Text(
                        text = highlightLine(line.ifEmpty { " " }, ext),
                        color = Color(0xFFE5E7EB),
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 20.sp,
                        softWrap = wrapLines,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ModernMarkdownCanvas(lines: List<String>) {
    LazyColumn(
        Modifier.fillMaxSize().background(Color(0xFF0F172A)).padding(horizontal = 14.dp, vertical = 14.dp),
        contentPadding = PaddingValues(bottom = 18.dp)
    ) {
        itemsIndexed(lines) { _, line -> MarkdownLine(line) }
    }
}

@Composable
private fun ModernImageCanvas(file: GHContent) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val state = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(0.75f, 6f)
        offset += panChange
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (file.downloadUrl.isBlank()) {
            Column(
                Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Rounded.Image, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(34.dp))
                Text("Image preview unavailable", color = Color.White.copy(alpha = 0.85f))
            }
        } else {
            AsyncImage(
                model = file.downloadUrl,
                contentDescription = file.name,
                modifier = Modifier.fillMaxSize().graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                ).transformable(state)
            )
        }

        Row(
            Modifier.align(Alignment.TopCenter).padding(top = 12.dp).clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MetaPill("IMAGE", Color(0xFF58A6FF))
            Text(file.name, color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Rounded.ZoomOutMap, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(18.dp))
        }
    }
}

private fun buildEditorSubtitle(ext: String, lines: Int, chars: Int, changed: Boolean): String {
    val type = ext.uppercase().ifBlank { "TEXT" }
    val suffix = if (changed) " • Modified" else ""
    return "$type • $lines lines • $chars chars$suffix"
}

private class EditorSyntaxTransformation(
    private val ext: String,
    private val searchQuery: String,
    private val currentMatchRange: IntRange?
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        return TransformedText(
            buildEditorAnnotatedText(text.text, ext, searchQuery, currentMatchRange),
            OffsetMapping.Identity
        )
    }
}

private fun buildEditorAnnotatedText(
    text: String,
    ext: String,
    searchQuery: String,
    currentMatchRange: IntRange?
): AnnotatedString {
    val syntax = buildAnnotatedString {
        if (text.length > 180_000) {
            pushStyle(SpanStyle(color = Color(0xFFE5E7EB)))
            append(text)
            pop()
            return@buildAnnotatedString
        }

        var start = 0
        while (start <= text.length) {
            val nextBreak = text.indexOf('\n', start)
            val end = if (nextBreak < 0) text.length else nextBreak
            val line = text.substring(start, end)
            if (line.length > 1_200) {
                pushStyle(SpanStyle(color = Color(0xFFE5E7EB)))
                append(line)
                pop()
            } else {
                append(doHighlightLine(line, ext))
            }
            if (nextBreak < 0) break
            append('\n')
            start = nextBreak + 1
        }
    }

    return buildAnnotatedString {
        append(syntax)
        buildSearchMatches(text, searchQuery).forEach { match ->
            addStyle(
                SpanStyle(background = Color(0xFFFFD166).copy(alpha = 0.20f)),
                match.start,
                match.end
            )
        }
        currentMatchRange?.let { range ->
            val start = range.first.coerceIn(0, text.length)
            val end = (range.last + 1).coerceIn(start, text.length)
            if (end > start) {
                addStyle(SpanStyle(background = Color(0xFF58A6FF).copy(alpha = 0.34f)), start, end)
            }
        }
    }
}

private fun buildSearchMatches(text: String, query: String): List<EditorSearchMatch> {
    val q = query.trim()
    if (q.isBlank()) return emptyList()
    val matches = mutableListOf<EditorSearchMatch>()
    var from = 0
    while (from <= text.length - q.length && matches.size < 2_000) {
        val idx = text.indexOf(q, from, ignoreCase = true)
        if (idx < 0) break
        val line = text.take(idx).count { it == '\n' }
        matches += EditorSearchMatch(idx, idx + q.length, line)
        from = idx + q.length.coerceAtLeast(1)
    }
    return matches
}

private fun buildEditorSymbols(lines: List<String>, ext: String): List<EditorSymbol> {
    val symbols = mutableListOf<EditorSymbol>()
    val codeRegex = Regex("""^\s*(?:export\s+|public\s+|private\s+|protected\s+|internal\s+|open\s+|override\s+|suspend\s+|data\s+|sealed\s+|abstract\s+|final\s+)*(class|object|interface|enum|fun|function|def|fn|struct|trait|impl|const|let|var|val)\s+([A-Za-z_$][\w$]*)""")
    val markdownRegex = Regex("""^(#{1,6})\s+(.+)$""")
    val cssRegex = Regex("""^\s*([.#]?[A-Za-z0-9_-][^{]+)\s*\{""")
    val yamlRegex = Regex("""^([A-Za-z0-9_-][A-Za-z0-9_.-]*)\s*:""")

    lines.forEachIndexed { index, raw ->
        if (symbols.size >= 240) return@forEachIndexed
        val line = raw.trimEnd()
        when {
            ext in listOf("md", "markdown") -> {
                val match = markdownRegex.find(line) ?: return@forEachIndexed
                symbols += EditorSymbol(match.groupValues[2].trim().take(80), "H${match.groupValues[1].length}", index)
            }
            ext in listOf("css", "scss", "sass", "less") -> {
                val match = cssRegex.find(line) ?: return@forEachIndexed
                symbols += EditorSymbol(match.groupValues[1].trim().take(80), "CSS", index)
            }
            ext in listOf("yaml", "yml", "toml") && !raw.startsWith(" ") && !raw.startsWith("\t") -> {
                val match = yamlRegex.find(line) ?: return@forEachIndexed
                symbols += EditorSymbol(match.groupValues[1].trim().take(80), "KEY", index)
            }
            else -> {
                val match = codeRegex.find(line) ?: return@forEachIndexed
                val kind = match.groupValues[1].uppercase()
                symbols += EditorSymbol(match.groupValues[2].trim().take(80), kind, index)
            }
        }
    }
    return symbols
}

private fun commentPrefixForExtension(ext: String): String? = when (ext) {
    "kt", "java", "js", "ts", "tsx", "jsx", "c", "cpp", "h", "hpp", "cs", "swift", "go", "rs", "scala", "gradle" -> "//"
    "py", "sh", "bash", "yaml", "yml", "toml", "ini", "properties", "rb", "pl" -> "#"
    "sql", "lua" -> "--"
    else -> null
}

private fun TextRange.coerceIn(length: Int): TextRange {
    val safeStart = start.coerceIn(0, length)
    val safeEnd = end.coerceIn(0, length)
    return TextRange(safeStart, safeEnd)
}

private fun Int.floorMod(other: Int): Int = if (other == 0) 0 else ((this % other) + other) % other

private fun openUrl(context: Context, url: String) {
    if (url.isBlank()) return
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
    } catch (_: Exception) {
        Toast.makeText(context, Strings.error, Toast.LENGTH_SHORT).show()
    }
}

/** Horizontal row of one-tap prompt presets shown under the editor's
 * info strip. Each chip launches the AI Agent with a prompt template
 * scoped to the active file + branch. */
@Composable
private fun AiQuickActionsRow(
    filePath: String,
    branch: String,
    selectedText: String,
    onSendPrompt: (String) -> Unit,
) {
    val colors = AiModuleTheme.colors
    val actions = listOf(
        Strings.aiAgentQuickExplain to "Read `$filePath` on branch `$branch` and explain what it does, including the key types and side effects.",
        Strings.aiAgentQuickAddTests to "Add unit tests for `$filePath` (branch `$branch`). Match the project's existing test framework and naming style. Read the file first to understand the scope.",
        Strings.aiAgentQuickFixLint to "Read `$filePath` on branch `$branch` and fix any lint, formatting, or unused-import issues. Use edit_file for surgical changes; preserve behaviour.",
        Strings.aiAgentQuickRefactor to "Read `$filePath` on branch `$branch` and propose a focused refactor that improves readability without changing behaviour. Apply with edit_file once approved.",
        Strings.aiAgentQuickGenerateDocs to "Read `$filePath` on branch `$branch` and add KDoc/Javadoc to public types, functions, and properties that are missing it. Keep comments concise.",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.AutoAwesome,
            null,
            Modifier.size(14.dp),
            tint = colors.accent,
        )
        Spacer(Modifier.width(2.dp))
        // C2 — selection-first chip. We cap the snippet at ~4 KB to
        // avoid blowing the agent's input box if the user
        // accidentally selected the whole file. The agent already has
        // file-read tools to fetch surrounding context if needed.
        if (selectedText.isNotBlank()) {
            val snippet = if (selectedText.length > 4_000) {
                selectedText.take(4_000) + "\n…(truncated)"
            } else {
                selectedText
            }
            val prompt = buildString {
                append(Strings.aiAgentSendSelectionPromptPrefix)
                append(" `")
                append(filePath)
                append("` (branch `")
                append(branch)
                append("`):\n\n```\n")
                append(snippet)
                append("\n```\n")
            }
            Box(
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.accent.copy(alpha = 0.14f))
                    .border(
                        0.5.dp,
                        colors.border,
                        RoundedCornerShape(14.dp),
                    )
                    .clickable { onSendPrompt(prompt) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    Strings.aiAgentSendSelectionChip,
                    fontSize = 11.sp,
                    color = colors.accent,
                    fontFamily = JetBrainsMono,
                )
            }
        }
        actions.forEach { (label, prompt) ->
            Box(
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.surface)
                    .border(0.5.dp, colors.border, RoundedCornerShape(14.dp))
                    .clickable { onSendPrompt(prompt) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    label,
                    fontSize = 11.sp,
                    color = colors.textSecondary,
                    fontFamily = JetBrainsMono,
                )
            }
        }
    }
}

/**
 * Build a unified-style diff between [oldText] and [newText] for a
 * one-shot AI commit-message suggestion. Doesn't do real LCS — for a
 * commit-message hint a coarse "removed/added line lists" is plenty
 * and avoids a full diff-engine dependency.
 */
private fun coarseDiff(oldText: String, newText: String, maxLines: Int = 60): String {
    val oldLines = oldText.lineSequence().toList()
    val newLines = newText.lineSequence().toList()
    val oldSet = oldLines.toMutableSet()
    val newSet = newLines.toMutableSet()
    val removed = oldLines.filter { it !in newSet }
    val added = newLines.filter { it !in oldSet }
    return buildString {
        appendLine("(coarse diff: ${added.size} added line(s), ${removed.size} removed line(s))")
        if (removed.isNotEmpty()) {
            appendLine()
            appendLine("--- removed:")
            removed.take(maxLines).forEach { appendLine("- $it") }
            if (removed.size > maxLines) appendLine("  […${removed.size - maxLines} more]")
        }
        if (added.isNotEmpty()) {
            appendLine()
            appendLine("+++ added:")
            added.take(maxLines).forEach { appendLine("+ $it") }
            if (added.size > maxLines) appendLine("  […${added.size - maxLines} more]")
        }
    }
}

/**
 * One-shot AI suggestion for the commit message of the current
 * single-file edit. Sends the (coarse) diff to the picked model and
 * asks for a Conventional-Commit-style one-liner. Used by the "AI
 * suggest" button in the commit dialog.
 */
private suspend fun generateCommitMessage(
    context: android.content.Context,
    path: String,
    oldText: String,
    newText: String,
): String {
    val systemPrompt =
        "You are a Conventional Commits assistant. Given the path of a file and a diff " +
        "of an edit, produce a single-line commit message in Conventional Commits style " +
        "(e.g. \"fix(api): handle empty list\", \"docs: clarify install\"). " +
        "Keep it under 72 characters. Output ONLY the message, no explanation, no quotes."
    val userPrompt = buildString {
        appendLine("Path: $path")
        appendLine()
        append(coarseDiff(oldText, newText))
    }
    val raw = com.glassfiles.data.ai.AiOneShot.complete(
        context = context,
        systemPrompt = systemPrompt,
        userPrompt = userPrompt,
    )
    // Strip surrounding quotes / trailing punctuation that smaller
    // models love to emit despite the explicit instruction.
    return raw
        .lineSequence()
        .firstOrNull { it.isNotBlank() }
        .orEmpty()
        .trim()
        .trim('"', '\'', '`')
        .take(120)
}
