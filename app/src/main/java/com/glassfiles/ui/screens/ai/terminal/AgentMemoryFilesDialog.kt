package com.glassfiles.ui.screens.ai.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.window.Dialog
import com.glassfiles.data.ai.AiAgentMemoryStore
import com.glassfiles.ui.components.terminal.TerminalTabsRow
import com.glassfiles.ui.theme.JetBrainsMono

@Composable
fun AgentMemoryFilesDialog(
    files: List<AiAgentMemoryStore.MemoryFile>,
    index: AiAgentMemoryStore.MemoryIndexSnapshot,
    onSearch: (String) -> Unit,
    onRebuildIndex: () -> Unit,
    onSave: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (files.isEmpty()) return
    val colors = AgentTerminal.colors
    var selectedKey by remember(files) { mutableStateOf(files.first().key) }
    val selectedFile = files.firstOrNull { it.key == selectedKey } ?: files.first()
    var text by remember(selectedFile.key, selectedFile.content) { mutableStateOf(selectedFile.content) }
    var searchQuery by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(colors.surfaceElevated)
                .border(1.dp, colors.warning, RoundedCornerShape(8.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "MEMORY FILES",
                color = colors.warning,
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.Bold,
                fontSize = AgentTerminal.type.message,
                lineHeight = 1.3.em,
            )
            TerminalTabsRow(
                spacing = 8.dp,
                fadeColor = colors.surfaceElevated,
                chevronColor = colors.textMuted,
            ) {
                files.forEach { file ->
                    val selected = file.key == selectedKey
                    Text(
                        text = if (selected) "[\u25A3 ${file.label}]" else "[ ${file.label} ]",
                        color = if (selected) colors.accent else colors.textSecondary,
                        fontFamily = JetBrainsMono,
                        fontSize = AgentTerminal.type.toolCall,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable {
                                selectedKey = file.key
                                text = file.content
                            }
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                    )
                }
                Text(
                    text = if (selectedKey == "facts") "[▣ facts]" else "[ facts ]",
                    color = if (selectedKey == "facts") colors.accent else colors.textSecondary,
                    fontFamily = JetBrainsMono,
                    fontSize = AgentTerminal.type.toolCall,
                    modifier = Modifier
                        .clickable { selectedKey = "facts" }
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                )
                Text(
                    text = if (selectedKey == "search") "[▣ search]" else "[ search ]",
                    color = if (selectedKey == "search") colors.accent else colors.textSecondary,
                    fontFamily = JetBrainsMono,
                    fontSize = AgentTerminal.type.toolCall,
                    modifier = Modifier
                        .clickable { selectedKey = "search" }
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                )
            }
            when (selectedKey) {
                "facts" -> MemoryFactsPanel(index)
                "search" -> MemorySearchPanel(
                    query = searchQuery,
                    onQueryChange = {
                        searchQuery = it
                        onSearch(it)
                    },
                    index = index,
                )
                else -> {
                    Text(
                        text = selectedFile.path,
                        color = colors.textMuted,
                        fontFamily = JetBrainsMono,
                        fontSize = AgentTerminal.type.label,
                    )
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it },
                        textStyle = TextStyle(
                            color = colors.textPrimary,
                            fontFamily = JetBrainsMono,
                            fontSize = AgentTerminal.type.toolCall,
                            lineHeight = 1.35.em,
                        ),
                        cursorBrush = SolidColor(colors.accent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 280.dp, max = 520.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.surface)
                            .border(1.dp, colors.border, RoundedCornerShape(6.dp))
                            .padding(10.dp),
                    )
                }
            }
            TerminalTabsRow(
                spacing = 12.dp,
                fadeColor = colors.surfaceElevated,
                chevronColor = colors.textMuted,
            ) {
                if (selectedKey !in setOf("facts", "search")) {
                    AgentTextButton(
                        label = "[ save ]",
                        color = colors.accent,
                        enabled = true,
                        onClick = { onSave(selectedFile.key, text) },
                    )
                }
                AgentTextButton(
                    label = "[ rebuild index ]",
                    color = colors.textSecondary,
                    enabled = true,
                    onClick = onRebuildIndex,
                )
                AgentTextButton(
                    label = "[ done ]",
                    color = colors.textSecondary,
                    enabled = true,
                    onClick = onDismiss,
                )
            }
        }
    }
}

@Composable
private fun MemoryFactsPanel(index: AiAgentMemoryStore.MemoryIndexSnapshot) {
    val colors = AgentTerminal.colors
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 280.dp, max = 520.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(6.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "STRUCTURED FACTS",
            color = colors.accent,
            fontFamily = JetBrainsMono,
            fontWeight = FontWeight.Bold,
            fontSize = AgentTerminal.type.toolCall,
        )
        if (index.facts.isEmpty()) {
            Text("// no facts indexed yet", color = colors.textMuted, fontFamily = JetBrainsMono, fontSize = AgentTerminal.type.toolCall)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(index.facts) { fact ->
                    Column {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("[${fact.type}]", color = colors.warning, fontFamily = JetBrainsMono, fontSize = AgentTerminal.type.label)
                            Text(fact.sourcePath, color = colors.textMuted, fontFamily = JetBrainsMono, fontSize = AgentTerminal.type.label)
                        }
                        Text(fact.text, color = colors.textPrimary, fontFamily = JetBrainsMono, fontSize = AgentTerminal.type.toolCall, lineHeight = 1.35.em)
                    }
                }
            }
        }
    }
}

@Composable
private fun MemorySearchPanel(
    query: String,
    onQueryChange: (String) -> Unit,
    index: AiAgentMemoryStore.MemoryIndexSnapshot,
) {
    val colors = AgentTerminal.colors
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 280.dp, max = 520.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(6.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = TextStyle(color = colors.textPrimary, fontFamily = JetBrainsMono, fontSize = AgentTerminal.type.toolCall),
            cursorBrush = SolidColor(colors.accent),
            decorationBox = { inner ->
                Row {
                    Text("$ ", color = colors.accent, fontFamily = JetBrainsMono, fontSize = AgentTerminal.type.toolCall)
                    if (query.isBlank()) {
                        Text("search indexed memory", color = colors.textMuted, fontFamily = JetBrainsMono, fontSize = AgentTerminal.type.toolCall)
                    }
                    inner()
                }
            },
            modifier = Modifier.fillMaxWidth().border(1.dp, colors.border).padding(8.dp),
        )
        Spacer(Modifier.height(2.dp))
        if (query.isBlank()) {
            Text("// type to search project.md, preferences, decisions, summaries and full chats", color = colors.textMuted, fontFamily = JetBrainsMono, fontSize = AgentTerminal.type.label)
        } else if (index.searchResults.isEmpty()) {
            Text("// no matches", color = colors.textMuted, fontFamily = JetBrainsMono, fontSize = AgentTerminal.type.toolCall)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(index.searchResults) { result ->
                    Column {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("[${result.kind}]", color = colors.warning, fontFamily = JetBrainsMono, fontSize = AgentTerminal.type.label)
                            Text(result.path, color = colors.textMuted, fontFamily = JetBrainsMono, fontSize = AgentTerminal.type.label)
                        }
                        Text(result.snippet, color = colors.textPrimary, fontFamily = JetBrainsMono, fontSize = AgentTerminal.type.toolCall, lineHeight = 1.35.em)
                    }
                }
            }
        }
    }
}
