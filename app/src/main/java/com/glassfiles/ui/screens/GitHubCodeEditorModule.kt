package com.glassfiles.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glassfiles.data.Strings
import com.glassfiles.data.github.GHContent
import com.glassfiles.data.github.GitHubManager
import com.glassfiles.ui.theme.*
import kotlinx.coroutines.launch

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
    var content by remember { mutableStateOf(initialContent) }
    var isEditing by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var showCommitDialog by remember { mutableStateOf(false) }
    var commitMessage by remember { mutableStateOf("") }
    var hasChanges by remember { mutableStateOf(false) }
    var lineNumbers by remember { mutableStateOf(true) }
    var wrapLines by remember { mutableStateOf(false) }

    val lines = remember(content) { content.lines() }
    val ext = file.name.substringAfterLast(".", "").lowercase()
    val isMarkdown = ext in listOf("md", "markdown")
    val isImage = ext in listOf("png", "jpg", "jpeg", "gif", "webp", "svg", "ico")

    Column(Modifier.fillMaxSize().background(if (isEditing) Color(0xFF1E1E1E) else SurfaceLight)) {
        // Top Bar
        GHTopBar(
            title = file.name,
            subtitle = if (hasChanges) "● Modified" else null,
            onBack = onBack,
            actions = {
                if (!isImage) {
                    IconButton(onClick = { lineNumbers = !lineNumbers }) {
                        Icon(Icons.Rounded.FormatListNumbered, null, Modifier.size(20.dp), tint = if (lineNumbers) Blue else TextSecondary)
                    }
                    IconButton(onClick = { wrapLines = !wrapLines }) {
                        Icon(Icons.Rounded.WrapText, null, Modifier.size(20.dp), tint = if (wrapLines) Blue else TextSecondary)
                    }
                    if (isEditing) {
                        IconButton(onClick = { isEditing = false }) {
                            Icon(Icons.Rounded.Visibility, null, Modifier.size(20.dp), tint = Blue)
                        }
                    } else {
                        IconButton(onClick = { isEditing = true }) {
                            Icon(Icons.Rounded.Edit, null, Modifier.size(20.dp), tint = Blue)
                        }
                    }
                    if (hasChanges) {
                        IconButton(onClick = { showCommitDialog = true }) {
                            Icon(Icons.Rounded.Save, null, Modifier.size(20.dp), tint = Color(0xFF34C759))
                        }
                    }
                }
            }
        )

        when {
            isImage -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Image viewer not available in editor", color = TextTertiary, fontSize = 14.sp)
                }
            }
            isEditing -> {
                // Editor mode
                Column(Modifier.fillMaxSize().padding(8.dp)) {
                    BasicTextField(
                        value = content,
                        onValueChange = { 
                            content = it
                            hasChanges = it != initialContent
                        },
                        modifier = Modifier.fillMaxSize(),
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            color = Color(0xFFD4D4D4),
                            lineHeight = 20.sp
                        ),
                        decorationBox = { innerTextField ->
                            Box(Modifier.fillMaxSize()) {
                                if (content.isEmpty()) {
                                    Text("Start typing...", color = Color(0xFF6E7681), fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                                }
                                innerTextField()
                            }
                        }
                    )
                }
            }
            else -> {
                // View mode with syntax highlighting simulation
                SelectionContainer {
                    LazyColumn(
                        Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(lines.size) { index ->
                            val line = lines.getOrNull(index) ?: ""
                            Row(Modifier.fillMaxWidth()) {
                                if (lineNumbers) {
                                    Text(
                                        "${index + 1}",
                                        modifier = Modifier.width(40.dp).padding(end = 8.dp),
                                        color = Color(0xFF6E7681),
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.End
                                    )
                                }
                                Text(
                                    line.ifEmpty { " " },
                                    modifier = Modifier.weight(1f),
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace,
                                    lineHeight = 20.sp,
                                    softWrap = wrapLines
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Commit Dialog
    if (showCommitDialog) {
        AlertDialog(
            onDismissRequest = { showCommitDialog = false },
            title = { Text("Commit Changes") },
            text = {
                Column {
                    Text("File: ${file.path}", fontSize = 12.sp, color = TextSecondary)
                    Spacer(Modifier.height(8.dp))
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
                                sha = file.sha
                            )
                            isSaving = false
                            showCommitDialog = false
                            if (success) {
                                hasChanges = false
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
                TextButton(onClick = { showCommitDialog = false }) { Text("Cancel") }
            }
        )
    }
}
