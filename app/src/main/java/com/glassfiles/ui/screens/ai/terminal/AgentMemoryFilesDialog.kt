package com.glassfiles.ui.screens.ai.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import com.glassfiles.ui.theme.JetBrainsMono

@Composable
fun AgentMemoryFilesDialog(
    files: List<AiAgentMemoryStore.MemoryFile>,
    onSave: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (files.isEmpty()) return
    val colors = AgentTerminal.colors
    var selectedKey by remember(files) { mutableStateOf(files.first().key) }
    val selectedFile = files.firstOrNull { it.key == selectedKey } ?: files.first()
    var text by remember(selectedFile.key, selectedFile.content) { mutableStateOf(selectedFile.content) }
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
            }
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
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AgentTextButton(
                    label = "[ save ]",
                    color = colors.accent,
                    enabled = true,
                    onClick = { onSave(selectedFile.key, text) },
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
