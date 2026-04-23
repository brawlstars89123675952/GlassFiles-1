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
import com.glassfiles.ui.theme.TextSecondary
import com.glassfiles.ui.theme.TextTertiary
import kotlinx.coroutines.launch

private enum class GitHubEditorMode { EDIT, READ, PREVIEW }

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

    var textState by rememberSaveable(file.path, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initialContent))
    }
    var savedContent by rememberSaveable(file.path) { mutableStateOf(initialContent) }
    var savedSha by rememberSaveable(file.path) { mutableStateOf(file.sha) }
    var mode by rememberSaveable(file.path) { mutableStateOf(GitHubEditorMode.EDIT) }
    var lineNumbers by rememberSaveable(file.path) { mutableStateOf(true) }
    var wrapLines by rememberSaveable(file.path) { mutableStateOf(false) }
    var showSearch by rememberSaveable(file.path) { mutableStateOf(false) }
    var searchState by rememberSaveable(file.path, stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var replaceState by rememberSaveable(file.path, stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var showGoToLine by rememberSaveable(file.path) { mutableStateOf(false) }
    var goToLineText by rememberSaveable(file.path) { mutableStateOf("") }
    var commitMessage by rememberSaveable(file.path) { mutableStateOf("Update ${file.name}") }
    var showCommitDialog by rememberSaveable(file.path) { mutableStateOf(false) }
    var saving by rememberSaveable(file.path) { mutableStateOf(false) }
    var currentMatchIndex by rememberSaveable(file.path) { mutableIntStateOf(0) }

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
    val matches = remember(text, searchState.text) { buildSearchMatches(lines, searchState.text) }

    val undoStack = remember(file.path) { mutableStateListOf<TextFieldValue>() }
    val redoStack = remember(file.path) { mutableStateListOf<TextFieldValue>() }

    fun snapshot() {
        if (undoStack.lastOrNull()?.text != textState.text || undoStack.lastOrNull()?.selection != textState.selection) {
            undoStack.add(textState.copy())
            if (undoStack.size > 120) undoStack.removeAt(0)
        }
    }

    fun applyState(newState: TextFieldValue) {
        if (newState != textState) {
            snapshot()
            redoStack.clear()
            textState = newState
        }
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
        val startLine = text.take(selection.start.coerceAtLeast(0)).count { it == '\n' }
        val endLine = text.take(selection.end.coerceAtLeast(0)).count { it == '\n' }
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

    fun replaceCurrent() {
        val query = searchState.text
        if (query.isBlank() || matches.isEmpty()) return
        val targetLine = matches.getOrNull(currentMatchIndex) ?: return
        val offsetToLine = lines.take(targetLine).sumOf { it.length + 1 }
        val local = lines[targetLine].indexOf(query, ignoreCase = true)
        if (local < 0) return
        val start = offsetToLine + local
        val end = start + query.length
        val newText = text.replaceRange(start, end, replaceState.text)
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
        AlertDialog(
            onDismissRequest = { showGoToLine = false },
            title = { Text("Go to line") },
            text = {
                OutlinedTextField(
                    value = goToLineText,
                    onValueChange = { goToLineText = it.filter(Char::isDigit) },
                    label = { Text("Line") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    goToLine(goToLineText.toIntOrNull() ?: 1)
                    showGoToLine = false
                }) { Text("Go") }
            },
            dismissButton = { TextButton(onClick = { showGoToLine = false }) { Text(Strings.cancel) } }
        )
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF09111F))) {
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
            onBack = onBack,
            onToggleSearch = { showSearch = !showSearch },
            onGoTo = { showGoToLine = true },
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
            onOpenImage = { openUrl(context, file.downloadUrl) }
        )

        if (!isImage) {
            EditorInfoStrip(
                mode = mode,
                currentLine = currentLine,
                selectionLength = textState.selection.length,
                matchCount = matches.size,
                currentMatchNumber = if (matches.isEmpty()) 0 else currentMatchIndex + 1,
                onSetMode = { mode = it },
                isMarkdown = isMarkdown,
                hasChanges = hasChanges
            )
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
                        goToLine(matches[currentMatchIndex] + 1)
                    }
                },
                onNext = {
                    if (matches.isNotEmpty()) {
                        currentMatchIndex = (currentMatchIndex + 1).floorMod(matches.size)
                        goToLine(matches[currentMatchIndex] + 1)
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
                onComment = if (commentPrefix != null) ({ toggleComment() }) else null
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
                mode == GitHubEditorMode.READ -> ModernReadCanvas(lines, ext, lineNumbers, wrapLines, matches.getOrNull(currentMatchIndex))
                else -> ModernEditCanvas(textState, lines, lineNumbers, wrapLines, ext, matches.getOrNull(currentMatchIndex), onValueChange = { applyState(it) })
            }
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
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (commitMessage.isBlank()) {
                        Toast.makeText(context, "Commit message required", Toast.LENGTH_SHORT).show()
                        return@TextButton
                    }
                    saving = true
                    scope.launch {
                        val success = GitHubManager.uploadFile(
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
                        if (success) {
                            savedContent = text
                            savedSha = ""
                            undoStack.clear()
                            redoStack.clear()
                            Toast.makeText(context, "Committed successfully", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Failed to commit", Toast.LENGTH_SHORT).show()
                        }
                    }
                }, enabled = !saving) {
                    if (saving) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Text("Commit")
                }
            },
            dismissButton = { TextButton(onClick = { showCommitDialog = false }) { Text(Strings.cancel) } }
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
    onBack: () -> Unit,
    onToggleSearch: () -> Unit,
    onGoTo: () -> Unit,
    onCopy: () -> Unit,
    onToggleLineNumbers: () -> Unit,
    onToggleWrap: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onCycleMode: () -> Unit,
    onSave: () -> Unit,
    onOpenImage: () -> Unit
) {
    GHTopBar(fileName, subtitle = subtitle, onBack = onBack) {
        if (!isImage) {
            IconButton(onClick = onToggleSearch) { Icon(Icons.Rounded.Search, null, tint = if (showSearch) Blue else TextSecondary) }
            IconButton(onClick = onGoTo) { Icon(Icons.Rounded.Tag, null, tint = TextSecondary) }
            IconButton(onClick = onCopy) { Icon(Icons.Rounded.ContentCopy, null, tint = TextSecondary) }
            IconButton(onClick = onToggleLineNumbers) { Icon(Icons.Rounded.FindInPage, null, tint = if (lineNumbers) Blue else TextSecondary) }
            IconButton(onClick = onToggleWrap) { Icon(Icons.Rounded.WrapText, null, tint = if (wrapLines) Blue else TextSecondary) }
            if (canUndo) IconButton(onClick = onUndo) { Icon(Icons.Rounded.Undo, null, tint = Blue) }
            if (canRedo) IconButton(onClick = onRedo) { Icon(Icons.Rounded.Redo, null, tint = Blue) }
            IconButton(onClick = onCycleMode) {
                Icon(
                    when (mode) {
                        GitHubEditorMode.EDIT -> if (isMarkdown) Icons.Rounded.MenuBook else Icons.Rounded.Visibility
                        GitHubEditorMode.READ -> Icons.Rounded.Edit
                        GitHubEditorMode.PREVIEW -> Icons.Rounded.Visibility
                    },
                    null,
                    tint = Blue
                )
            }
            if (hasChanges) IconButton(onClick = onSave) { Icon(Icons.Rounded.Save, null, tint = Color(0xFF34C759)) }
        } else {
            IconButton(onClick = onOpenImage) { Icon(Icons.Rounded.Download, null, tint = Blue) }
        }
    }
}

@Composable
private fun EditorInfoStrip(
    mode: GitHubEditorMode,
    currentLine: Int,
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
    onComment: (() -> Unit)?
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
    currentHighlightedLine: Int?,
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
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.End,
                        lineHeight = 20.sp,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)
                    )
                }
            }
        }
        Box(Modifier.width(1.dp).fillMaxSize().background(SeparatorColor))
        val baseModifier = Modifier.weight(1f).fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp)
        val scrollModifier = if (wrapLines) baseModifier.verticalScroll(vertical) else baseModifier.verticalScroll(vertical).horizontalScroll(rememberScrollState())
        BasicTextField(
            value = textState,
            onValueChange = onValueChange,
            modifier = scrollModifier,
            textStyle = TextStyle(
                color = Color(0xFFE5E7EB),
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 20.sp
            ),
            decorationBox = { inner ->
                Box(Modifier.fillMaxSize()) {
                    if (textState.text.isEmpty()) {
                        Text("Start typing…", color = Color(0xFF64748B), fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                    }
                    inner()
                }
            }
        )
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

private fun buildSearchMatches(lines: List<String>, query: String): List<Int> {
    val q = query.trim()
    if (q.isBlank()) return emptyList()
    return lines.mapIndexedNotNull { index, line -> if (line.contains(q, ignoreCase = true)) index else null }
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
