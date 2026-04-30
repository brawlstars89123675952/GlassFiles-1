package com.glassfiles.ui.screens.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.glassfiles.data.Strings
import com.glassfiles.ui.theme.AiModuleDarkColors
import com.glassfiles.ui.theme.JetBrainsMono

/**
 * Terminal-themed dialog for editing the per-repo system-prompt
 * override and the plan-then-execute toggle. Both options scoped to
 * the same repo and persisted via `AiAgentPrefs`.
 */
@Composable
fun SystemPromptOverrideDialog(
    repoFullName: String,
    initialPrompt: String,
    initialPlanFirst: Boolean,
    onSave: (prompt: String, planFirst: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = AiModuleDarkColors
    var text by remember { mutableStateOf(initialPrompt) }
    var planFirst by remember { mutableStateOf(initialPlanFirst) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceElevated,
        title = {
            Column {
                Text(
                    "> ${Strings.aiAgentSystemPromptTitle.lowercase()}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.accent,
                    fontFamily = JetBrainsMono,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    repoFullName,
                    fontSize = 11.sp,
                    color = colors.textMuted,
                    fontFamily = JetBrainsMono,
                )
            }
        },
        text = {
            Column {
                Text(
                    Strings.aiAgentSystemPromptHint,
                    fontSize = 12.sp,
                    color = colors.textSecondary,
                    fontFamily = JetBrainsMono,
                    lineHeight = 1.4.em,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = {
                        Text(
                            Strings.aiAgentSystemPromptPlaceholder,
                            fontSize = 13.sp,
                            color = colors.textMuted,
                            fontFamily = JetBrainsMono,
                        )
                    },
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontFamily = JetBrainsMono,
                        fontSize = 13.sp,
                        color = colors.textPrimary,
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.accent,
                        unfocusedBorderColor = colors.border,
                        focusedContainerColor = colors.surface,
                        unfocusedContainerColor = colors.surface,
                        cursorColor = colors.accent,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 240.dp),
                    minLines = 5,
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { planFirst = !planFirst }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            Strings.aiAgentPlanFirstLabel,
                            fontSize = 13.sp,
                            color = colors.textPrimary,
                            fontFamily = JetBrainsMono,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            Strings.aiAgentPlanFirstHint,
                            fontSize = 11.sp,
                            color = colors.textMuted,
                            fontFamily = JetBrainsMono,
                            lineHeight = 1.3.em,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = if (planFirst) "[on]" else "[off]",
                        color = if (planFirst) colors.accent else colors.textSecondary,
                        fontFamily = JetBrainsMono,
                        fontSize = 12.sp,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(text.trim(), planFirst) }) {
                Text(
                    "[ ${Strings.aiAgentSystemPromptSave.lowercase()} ]",
                    color = colors.accent,
                    fontFamily = JetBrainsMono,
                    fontSize = 13.sp,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    "[ ${Strings.aiAgentSystemPromptCancel.lowercase()} ]",
                    color = colors.textSecondary,
                    fontFamily = JetBrainsMono,
                    fontSize = 13.sp,
                )
            }
        },
    )
}
