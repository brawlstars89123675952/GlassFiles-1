package com.glassfiles.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.glassfiles.data.Strings
import com.glassfiles.data.github.GHContent
import com.glassfiles.data.github.GitHubManager
import com.glassfiles.ui.theme.Blue
import com.glassfiles.ui.theme.SeparatorColor
import com.glassfiles.ui.theme.SurfaceLight
import com.glassfiles.ui.theme.SurfaceWhite
import com.glassfiles.ui.theme.TextPrimary
import com.glassfiles.ui.theme.TextSecondary
import com.glassfiles.ui.theme.TextTertiary
import kotlinx.coroutines.launch

private enum class EditorMode { CODE, PREVIEW }

@Composable
fun CodeEditorScreen(
    repoOwner: String,
    repoName: String,
    file: GHContent,
    branch: String,
    initialContent: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    var editorValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initialContent))
    }
    var lastSavedContent by rememberSaveable(file.path) { mutableStateOf(initialContent) }
    var currentFileSha by rememberSaveable(file.path) { mutableStateOf(file.sha) }
    var isEditing by rememberSaveable(file.path) { mutableStateOf(true) }
    var editorMode by rememberSaveable(file.path) { mutableStateOf(EditorMode.CODE) }
    var isSaving by rememberSaveable(file.path) { mutableStateOf(false) }
    var showCommitDialog by rememberSaveable(file.path) { mutableStateOf(false) }
    var commitMessage by rememberSaveable(file.path) { mutableStateOf("Update ${file.name}") }
    var lineNumbers by rememberSaveable(file.path) { mutableStateOf(true) }
    var wrapLines by rememberSaveable(file.path) { mutableStateOf(false) }
    var showSearch by rememberSaveable(file.path) { mutableStateOf(false) }
    var searchQuery by rememberSaveable(file.path, stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var replaceQuery by rememberSaveable(file.path, stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var currentSearchIndex by rememberSaveable(file.path) { mutableIntStateOf(0) }
    var showGoToLine by rememberSaveable(file.path) { mutableStateOf(false) }
    var goToLineText by rememberSaveable(file.path) { mutableStateOf("") }

    val ext = remember(file.name) { file.name.substringAfterLast(".", "").lowercase() }
    val isMarkdown = ext in listOf("md", "markdown")
    val isImage = ext in listOf("png", "jpg", "jpeg", "gif", "webp", "svg", "ico")
    val content = editorValue.text
    val lines = remember(content) { content.lines() }
    val hasChanges = content != lastSavedContent
    val currentLine = remember(editorValue.selection, content) {
        content.take(editorValue.selection.start.coerceIn(0, content.length)).count { it == '\n' } + 1
    }
    val commentPrefix = remember(ext) { commentPrefixForExtension(ext) }

    val undoStack = remember(file.path) { mutableStateListOf<TextFieldValue>() }
    val redoStack = remember(file.path) { mutableStateListOf<TextFieldValue>() }

    val matches = remember(content, searchQuery.text) {
        buildSearchMatches(lines, searchQuery.text)
    }

    fun snapshot() {
        if (undoStack.lastOrNull()?.text != editorValue.text || undoStack.lastOrNull()?.selection != editorValue.selection) {
            undoStack.add(editorValue.copy())
            if (undoStack.size > 120) undoStack.removeAt(0)
        }
    }

    fun applyNewValue(newValue: TextFieldValue) {
        if (newValue != editorValue) {
            snapshot()
            redoStack.clear()
            editorValue = newValue
        }
    }

    fun insertAtCursor(text: String) {
        val sel = editorValue.selection
        val start = minOf(sel.start, sel.end).coerceAtLeast(0)
        val end = maxOf(sel.start, sel.end).coerceAtLeast(0)
        val newText = editorValue.text.replaceRange(start, end, text)
        val cursor = (start + text.length).coerceIn(0, newText.length)
        applyNewValue(TextFieldValue(newText, TextRange(cursor)))
    }

    fun insertPair(open: String, close: String) {
        val sel = editorValue.selection
        val start = minOf(sel.start, sel.end).coerceAtLeast(0)
        val end = maxOf(sel.start, sel.end).coerceAtLeast(0)
        val selected = editorValue.text.substring(start, end.coerceAtMost(editorValue.text.length))
        val inserted = open + selected + close
        val newText = editorValue.text.replaceRange(start, end, inserted)
        val newSelection = if (selected.isEmpty()) {
            TextRange((start + open.length).coerceAtMost(newText.length))
        } else {
            TextRange(start + open.length, start + open.length + selected.length)
        }
        applyNewValue(TextFieldValue(newText, newSelection))
    }

    fun replaceCurrentOccurrence() {
        val search = searchQuery.text
        if (search.isBlank() || matches.isEmpty()) return
        val targetLine = matches.getOrNull(currentSearchIndex) ?: return
        val offsetToLine = lines.take(targetLine).sumOf { it.length + 1 }
        val lineText = lines[targetLine]
        val localIndex = lineText.indexOf(search, ignoreCase = true)
        if (localIndex < 0) return
        val globalStart = offsetToLine + localIndex
        val globalEnd = globalStart + search.length
        val newText = editorValue.text.replaceRange(globalStart, globalEnd, replaceQuery.text)
        val cursor = globalStart + replaceQuery.text.length
        applyNewValue(TextFieldValue(newText, TextRange(cursor)))
    }

    fun replaceAllOccurrences() {
        val search = searchQuery.text
        if (search.isBlank()) return
        val newText = editorValue.text.replace(search, replaceQuery.text, ignoreCase = true)
        applyNewValue(TextFieldValue(newText, TextRange(editorValue.selection.start.coerceAtMost(newText.length))))
    }

    fun goToLine(lineNumber: Int) {
        val safeLine = lineNumber.coerceIn(1, lines.size.coerceAtLeast(1))
        val offset = lines.take(safeLine - 1).sumOf { it.length + 1 }.coerceAtMost(content.length)
        editorValue = editorValue.copy(selection = TextRange(offset))
    }

    fun duplicateCurrentLine() {
        val lineIndex = (currentLine - 1).coerceIn(0, lines.lastIndex.coerceAtLeast(0))
        val lineText = lines.getOrElse(lineIndex) { "" }
        val before = lines.take(lineIndex + 1).joinToString("\n")
        val after = lines.drop(lineIndex + 1).joinToString("\n")
        val newText = buildString {
            append(before)
            append("\n")
            append(lineText)
            if (after.isNotBlank()) {
                append("\n")
                append(after)
            }
        }
        val newOffset = lines.take(lineIndex + 1).sumOf { it.length + 1 }.coerceAtMost(newText.length)
        applyNewValue(TextFieldValue(newText, TextRange(newOffset)))
    }

    fun toggleComment() {
        val prefix = commentPrefix ?: return
        val selection = editorValue.selection
        val startLine = editorValue.text.take(selection.start.coerceAtLeast(0)).count { it == '\n' }
        val endLineBase = editorValue.text.take(selection.end.coerceAtLeast(0)).count { it == '\n' }
        val endLine = if (selection.length == 0) startLine else endLineBase
        val mutableLines = lines.toMutableList()
        val targetRange = startLine..endLine.coerceAtMost(mutableLines.lastIndex.coerceAtLeast(0))
        val shouldUncomment = targetRange.all { idx -> mutableLines[idx].trimStart().startsWith(prefix) }
        targetRange.forEach { idx ->
            val original = mutableLines[idx]
            val indent = original.takeWhile { it == ' ' || it == '\t' }
            val trimmed = original.removePrefix(indent)
            mutableLines[idx] = if (shouldUncomment) {
                if (trimmed.startsWith(prefix)) indent + trimmed.removePrefix(prefix).removePrefix(" ") else original
            } else {
                indent + prefix + if (trimmed.isNotBlank()) " $trimmed" else ""
            }
        }
        val newText = mutableLines.joinToString("\n")
        applyNewValue(TextFieldValue(newText, selection.coerceIn(newText.length)))
    }

    LaunchedEffect(matches) {
        if (matches.isEmpty()) currentSearchIndex = 0
        else if (currentSearchIndex !in matches.indices) currentSearchIndex = 0
    }

    if (showGoToLine) {
        AlertDialog(
            onDismissRequest = { showGoToLine = false },
            title = { Text("Go to line") },
            text = {
                OutlinedTextField(
                    value = goToLineText,
                    onValueChange = { goToLineText = it.filter(Char::isDigit) },
                    label = { Text("Line number") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    goToLine(goToLineText.toIntOrNull() ?: 1)
                    showGoToLine = false
                }) { Text("Go") }
            },
            dismissButton = {
                TextButton(onClick = { showGoToLine = false }) { Text(Strings.cancel) }
            }
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(if (isEditing || isImage) Color(0xFF0D1117) else SurfaceLight)
    ) {
        GHTopBar(
            title = file.name,
            subtitle = buildEditorSubtitle(ext = ext, lines = lines.size, chars = content.length, hasChanges = hasChanges),
            onBack = onBack,
            actions = {
                if (!isImage) {
                    IconButton(onClick = { showSearch = !showSearch }) {
                        Icon(Icons.Rounded.Search, null, Modifier.size(20.dp), tint = if (showSearch) Blue else TextSecondary)
                    }
                    IconButton(onClick = { showGoToLine = true }) {
                        Icon(Icons.Rounded.Tag, null, Modifier.size(20.dp), tint = TextSecondary)
                    }
                    IconButton(onClick = {
                        clipboard.setText(AnnotatedString(content))
                        Toast.makeText(context, Strings.copied, Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Rounded.ContentCopy, null, Modifier.size(20.dp), tint = TextSecondary)
                    }
                    IconButton(onClick = { duplicateCurrentLine() }) {
                        Icon(Icons.Rounded.ContentPaste, null, Modifier.size(20.dp), tint = TextSecondary)
                    }
                    if (commentPrefix != null) {
                        IconButton(onClick = { toggleComment() }) {
                            Icon(Icons.Rounded.Code, null, Modifier.size(20.dp), tint = TextSecondary)
                        }
                    }
                    IconButton(onClick = { lineNumbers = !lineNumbers }) {
                        Icon(Icons.Rounded.FindInPage, null, Modifier.size(20.dp), tint = if (lineNumbers) Blue else TextSecondary)
                    }
                    IconButton(onClick = { wrapLines = !wrapLines }) {
                        Icon(Icons.Rounded.WrapText, null, Modifier.size(20.dp), tint = if (wrapLines) Blue else TextSecondary)
                    }
                    if (isMarkdown) {
                        IconButton(onClick = { editorMode = if (editorMode == EditorMode.CODE) EditorMode.PREVIEW else EditorMode.CODE }) {
                            Icon(
                                if (editorMode == EditorMode.CODE) Icons.Rounded.MenuBook else Icons.Rounded.Preview,
                                null,
                                Modifier.size(20.dp),
                                tint = Blue
                            )
                        }
                    }
                    if (isEditing) {
                        IconButton(onClick = {
                            if (undoStack.isNotEmpty()) {
                                redoStack.add(editorValue.copy())
                                editorValue = undoStack.removeLast()
                            }
                        }, enabled = undoStack.isNotEmpty()) {
                            Icon(Icons.Rounded.Undo, null, Modifier.size(20.dp), tint = if (undoStack.isNotEmpty()) Blue else TextTertiary)
                        }
                        IconButton(onClick = {
                            if (redoStack.isNotEmpty()) {
                                undoStack.add(editorValue.copy())
                                editorValue = redoStack.removeLast()
                            }
                        }, enabled = redoStack.isNotEmpty()) {
                            Icon(Icons.Rounded.Redo, null, Modifier.size(20.dp), tint = if (redoStack.isNotEmpty()) Blue else TextTertiary)
                        }
                    }
                    IconButton(onClick = { isEditing = !isEditing }) {
                        Icon(
                            if (isEditing) Icons.Rounded.Visibility else Icons.Rounded.Edit,
                            null,
                            Modifier.size(20.dp),
                            tint = Blue
                        )
                    }
                    if (hasChanges) {
                        IconButton(onClick = { showCommitDialog = true }) {
                            Icon(Icons.Rounded.Save, null, Modifier.size(20.dp), tint = Color(0xFF34C759))
                        }
                    }
                } else {
                    IconButton(onClick = { openUrl(context, file.downloadUrl) }) {
                        Icon(Icons.Rounded.Download, null, Modifier.size(20.dp), tint = Blue)
                    }
                }
            }
        )

        if (!isImage) {
            EditorHeaderRow(
                ext = ext,
                isEditing = isEditing,
                editorMode = editorMode,
                isMarkdown = isMarkdown,
                matchCount = matches.size,
                currentMatchNumber = if (matches.isEmpty()) 0 else currentSearchIndex + 1,
                hasChanges = hasChanges,
                currentLine = currentLine,
                selectionLength = editorValue.selection.length,
                wrapLines = wrapLines
            )
        }

        AnimatedVisibility(showSearch && !isImage) {
            EditorSearchReplaceBar(
                searchValue = searchQuery,
                replaceValue = replaceQuery,
                onSearchValueChange = { searchQuery = it },
                onReplaceValueChange = { replaceQuery = it },
                matchCount = matches.size,
                currentMatchNumber = if (matches.isEmpty()) 0 else currentSearchIndex + 1,
                onPrev = {
                    if (matches.isNotEmpty()) {
                        currentSearchIndex = (currentSearchIndex - 1).floorMod(matches.size)
                        goToLine(matches[currentSearchIndex] + 1)
                    }
                },
                onNext = {
                    if (matches.isNotEmpty()) {
                        currentSearchIndex = (currentSearchIndex + 1).floorMod(matches.size)
                        goToLine(matches[currentSearchIndex] + 1)
                    }
                },
                onReplaceOne = { replaceCurrentOccurrence() },
                onReplaceAll = { replaceAllOccurrences() },
                onClose = { showSearch = false }
            )
        }

        if (!isImage && isEditing) {
            QuickInsertToolbar(
                onInsert = { insertAtCursor(it) },
                onInsertPair = { open, close -> insertPair(open, close) },
                onToggleComment = if (commentPrefix != null) ({ toggleComment() }) else null,
                onDuplicateLine = { duplicateCurrentLine() }
            )
        }

        when {
            isImage -> GitHubImagePreview(file = file)
            isMarkdown && editorMode == EditorMode.PREVIEW && !isEditing -> MarkdownPreviewPane(lines)
            isMarkdown && editorMode == EditorMode.PREVIEW -> SplitMarkdownEditor(
                editorValue = editorValue,
                lines = lines,
                lineNumbers = lineNumbers,
                wrapLines = wrapLines,
                currentHighlightedLine = matches.getOrNull(currentSearchIndex),
                ext = ext,
                onValueChange = { applyNewValue(it) }
            )
            isEditing -> ModernEditableCodePane(
                editorValue = editorValue,
                ext = ext,
                lineNumbers = lineNumbers,
                wrapLines = wrapLines,
                currentHighlightedLine = matches.getOrNull(currentSearchIndex),
                onValueChange = { applyNewValue(it) }
            )
            else -> ReadOnlyCodePane(
                lines = lines,
                ext = ext,
                lineNumbers = lineNumbers,
                wrapLines = wrapLines,
                currentHighlightedLine = matches.getOrNull(currentSearchIndex)
            )
        }
    }

    if (showCommitDialog) {
        AlertDialog(
            onDismissRequest = { showCommitDialog = false },
            title = { Text("Commit changes") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(file.path, fontSize = 12.sp, color = TextSecondary)
                    OutlinedTextField(
                        value = commitMessage,
                        onValueChange = { commitMessage = it },
                        label = { Text("Commit message") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    if (hasChanges) {
                        LinearProgressIndicator(
                            progress = {
                                (content.length.coerceAtLeast(1).toFloat() /
                                    lastSavedContent.length.coerceAtLeast(1).toFloat())
                                    .coerceIn(0.1f, 1.5f) / 1.5f
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (commitMessage.isBlank()) {
                            Toast.makeText(context, "Commit message required", Toast.LENGTH_SHORT).show()
                            return@TextButton
                        }
                        isSaving = true
                        scope.launch {
                            val success = GitHubManager.uploadFile(
                                context = context,
                                owner = repoOwner,
                                repo = repoName,
                                path = file.path,
                                content = content.toByteArray(),
                                message = commitMessage,
                                branch = branch,
                                sha = currentFileSha
                            )
                            isSaving = false
                            showCommitDialog = false
                            if (success) {
                                lastSavedContent = content
                                currentFileSha = ""
                                undoStack.clear()
                                redoStack.clear()
                                Toast.makeText(context, "Committed successfully", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Failed to commit", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    enabled = !isSaving
                ) {
                    if (isSaving) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Text("Commit")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCommitDialog = false }) {
                    Text(Strings.cancel)
                }
            }
        )
    }
}

@Composable
private fun EditorHeaderRow(
    ext: String,
    isEditing: Boolean,
    editorMode: EditorMode,
    isMarkdown: Boolean,
    matchCount: Int,
    currentMatchNumber: Int,
    hasChanges: Boolean,
    currentLine: Int,
    selectionLength: Int,
    wrapLines: Boolean
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFF161B22))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MiniBadge(ext.uppercase().ifBlank { "TEXT" }, Blue)
        MiniBadge(if (isEditing) "EDIT" else "VIEW", if (isEditing) Color(0xFFFFB020) else Color(0xFF34C759))
        MiniBadge("Ln $currentLine", Color(0xFF58A6FF))
        if (selectionLength > 0) MiniBadge("$selectionLength sel", Color(0xFF34C759))
        if (wrapLines) MiniBadge("WRAP", Color(0xFF8B949E))
        if (isMarkdown) MiniBadge(if (editorMode == EditorMode.PREVIEW) "PREVIEW" else "SOURCE", Color(0xFFBF5AF2))
        if (matchCount > 0) MiniBadge("$currentMatchNumber/$matchCount", Color(0xFF58A6FF))
        if (hasChanges) MiniBadge("MODIFIED", Color(0xFFFF6B6B))
    }
}

@Composable
private fun EditorSearchReplaceBar(
    searchValue: TextFieldValue,
    replaceValue: TextFieldValue,
    onSearchValueChange: (TextFieldValue) -> Unit,
    onReplaceValueChange: (TextFieldValue) -> Unit,
    matchCount: Int,
    currentMatchNumber: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onReplaceOne: () -> Unit,
    onReplaceAll: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(SurfaceWhite)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SearchField(searchValue, onSearchValueChange, "Find in file", Modifier.weight(1f))
            Text(
                if (matchCount == 0) "0" else "$currentMatchNumber/$matchCount",
                fontSize = 12.sp,
                color = TextSecondary
            )
            IconButton(onClick = onPrev, enabled = matchCount > 0) {
                Icon(Icons.Rounded.Undo, null, tint = if (matchCount > 0) Blue else TextTertiary)
            }
            IconButton(onClick = onNext, enabled = matchCount > 0) {
                Icon(Icons.Rounded.Redo, null, tint = if (matchCount > 0) Blue else TextTertiary)
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.Close, null, tint = TextSecondary)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SearchField(replaceValue, onReplaceValueChange, "Replace", Modifier.weight(1f))
            TextButton(onClick = onReplaceOne) { Text("1") }
            TextButton(onClick = onReplaceAll) { Text("All") }
        }
    }
}

@Composable
private fun SearchField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceLight)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        if (value.text.isEmpty()) {
            Text(placeholder, color = TextTertiary, fontSize = 14.sp)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun QuickInsertToolbar(
    onInsert: (String) -> Unit,
    onInsertPair: (String, String) -> Unit,
    onToggleComment: (() -> Unit)?,
    onDuplicateLine: () -> Unit
) {
    val inserts = listOf(
        QuickInsertItem("TAB", text = "\t"),
        QuickInsertItem("{}", open = "{", close = "}"),
        QuickInsertItem("()", open = "(", close = ")"),
        QuickInsertItem("[]", open = "[", close = "]"),
        QuickInsertItem("\"\"", open = "\"", close = "\""),
        QuickInsertItem("''", open = "'", close = "'"),
        QuickInsertItem("<>", open = "<", close = ">"),
        QuickInsertItem("//", text = "// "),
        QuickInsertItem("/* */", open = "/* ", close = " */"),
        QuickInsertItem("=>", text = " => "),
        QuickInsertItem("->", text = " -> "),
        QuickInsertItem("::", text = "::"),
        QuickInsertItem(";", text = ";"),
        QuickInsertItem(",", text = ", "),
        QuickInsertItem("fun", text = "fun "),
        QuickInsertItem("val", text = "val "),
        QuickInsertItem("var", text = "var ")
    )
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFF0D1117))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (onToggleComment != null) {
            QuickActionChip("Comment") { onToggleComment() }
        }
        QuickActionChip("Duplicate") { onDuplicateLine() }
        inserts.forEach { item ->
            QuickActionChip(item.label) {
                if (item.open != null && item.close != null) onInsertPair(item.open, item.close)
                else onInsert(item.text)
            }
        }
    }
}

@Composable
private fun QuickActionChip(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF21262D))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) {
        Text(label, fontSize = 11.sp, color = Color(0xFF8B949E), fontFamily = FontFamily.Monospace)
    }
}

private data class QuickInsertItem(
    val label: String,
    val text: String = "",
    val open: String? = null,
    val close: String? = null
)

@Composable
private fun ModernEditableCodePane(
    editorValue: TextFieldValue,
    ext: String,
    lineNumbers: Boolean,
    wrapLines: Boolean,
    currentHighlightedLine: Int?,
    onValueChange: (TextFieldValue) -> Unit
) {
    val lines = remember(editorValue.text) { editorValue.text.lines() }
    val scrollState = rememberScrollState()
    Row(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0D1117))
    ) {
        if (lineNumbers) {
            Column(
                Modifier
                    .width(52.dp)
                    .fillMaxSize()
                    .background(Color(0xFF161B22))
                    .padding(top = 10.dp, end = 6.dp)
                    .verticalScroll(scrollState)
            ) {
                lines.forEachIndexed { index, _ ->
                    val isActive = currentHighlightedLine == index
                    Text(
                        text = "${index + 1}",
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (isActive) Blue.copy(alpha = 0.10f) else Color.Transparent)
                            .padding(vertical = 1.dp),
                        color = if (isActive) Blue else Color(0xFF6E7681),
                        fontSize = 12.sp,
                        textAlign = TextAlign.End,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 20.sp
                    )
                }
            }
        }
        Box(Modifier.width(1.dp).fillMaxSize().background(SeparatorColor))
        val editorModifier = Modifier
            .weight(1f)
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 10.dp)
        val scrollingModifier = if (wrapLines) editorModifier.verticalScroll(scrollState)
        else editorModifier.verticalScroll(scrollState).horizontalScroll(rememberScrollState())
        BasicTextField(
            value = editorValue,
            onValueChange = onValueChange,
            modifier = scrollingModifier,
            textStyle = TextStyle(
                color = Color(0xFFD4D4D4),
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 20.sp
            ),
            decorationBox = { innerTextField ->
                Box(Modifier.fillMaxSize()) {
                    if (editorValue.text.isEmpty()) {
                        Text(
                            "Start typing…",
                            color = Color(0xFF6E7681),
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    EditorSyntaxPreview(lines = lines, ext = ext, wrapLines = wrapLines, currentHighlightedLine = currentHighlightedLine)
                    innerTextField()
                }
            }
        )
    }
}

@Composable
private fun EditorSyntaxPreview(
    lines: List<String>,
    ext: String,
    wrapLines: Boolean,
    currentHighlightedLine: Int?
) {
    Column(Modifier.fillMaxSize()) {
        lines.forEachIndexed { index, line ->
            val isActive = currentHighlightedLine == index
            Text(
                text = highlightLine(line.ifEmpty { " " }, ext),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (isActive) Blue.copy(alpha = 0.08f) else Color.Transparent),
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 20.sp,
                softWrap = wrapLines,
                color = Color.Transparent
            )
        }
    }
}

@Composable
private fun ReadOnlyCodePane(
    lines: List<String>,
    ext: String,
    lineNumbers: Boolean,
    wrapLines: Boolean,
    currentHighlightedLine: Int?
) {
    SelectionContainer {
        LazyColumn(
            Modifier
                .fillMaxSize()
                .background(Color(0xFF0D1117))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            itemsIndexed(lines) { index, line ->
                val isMatch = currentHighlightedLine == index
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(if (isMatch) Blue.copy(alpha = 0.10f) else Color.Transparent)
                        .padding(vertical = 1.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    if (lineNumbers) {
                        Text(
                            "${index + 1}",
                            modifier = Modifier.width(44.dp).padding(end = 8.dp),
                            color = if (isMatch) Blue else Color(0xFF6E7681),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.End
                        )
                    }
                    Text(
                        text = highlightLine(line.ifEmpty { " " }, ext),
                        modifier = Modifier.weight(1f),
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 20.sp,
                        softWrap = wrapLines,
                        color = TextPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun MarkdownPreviewPane(lines: List<String>) {
    LazyColumn(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0D1117))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        itemsIndexed(lines) { _, line ->
            MarkdownLine(line)
        }
    }
}

@Composable
private fun SplitMarkdownEditor(
    editorValue: TextFieldValue,
    lines: List<String>,
    lineNumbers: Boolean,
    wrapLines: Boolean,
    currentHighlightedLine: Int?,
    ext: String,
    onValueChange: (TextFieldValue) -> Unit
) {
    Column(Modifier.fillMaxSize().background(Color(0xFF0D1117))) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(Color(0xFF161B22))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text("Markdown live preview", color = TextSecondary, fontSize = 12.sp)
        }
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                ModernEditableCodePane(
                    editorValue = editorValue,
                    ext = ext,
                    lineNumbers = lineNumbers,
                    wrapLines = wrapLines,
                    currentHighlightedLine = currentHighlightedLine,
                    onValueChange = onValueChange
                )
            }
            Box(
                Modifier
                    .width(1.dp)
                    .fillMaxSize()
                    .background(SeparatorColor)
            )
            Box(Modifier.weight(1f)) {
                MarkdownPreviewPane(lines)
            }
        }
    }
}

@Composable
private fun GitHubImagePreview(file: GHContent) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val state = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(0.75f, 6f)
        offset += panChange
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (file.downloadUrl.isBlank()) {
            Column(
                Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Rounded.Image, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(34.dp))
                Text("Image preview unavailable", color = Color.White.copy(alpha = 0.8f))
            }
        } else {
            AsyncImage(
                model = file.downloadUrl,
                contentDescription = file.name,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    )
                    .transformable(state)
            )
        }

        Row(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 12.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MiniBadge("IMAGE", Color(0xFF58A6FF))
            Text(file.name, color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Rounded.ZoomOutMap, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun MiniBadge(text: String, color: Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun buildEditorSubtitle(ext: String, lines: Int, chars: Int, hasChanges: Boolean): String {
    val type = ext.uppercase().ifBlank { "TEXT" }
    val modified = if (hasChanges) " • Modified" else ""
    return "$type • $lines lines • $chars chars$modified"
}

private fun buildSearchMatches(lines: List<String>, query: String): List<Int> {
    val q = query.trim()
    if (q.isBlank()) return emptyList()
    return lines.mapIndexedNotNull { index, line ->
        if (line.contains(q, ignoreCase = true)) index else null
    }
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
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    } catch (_: Exception) {
        Toast.makeText(context, Strings.error, Toast.LENGTH_SHORT).show()
    }
}
