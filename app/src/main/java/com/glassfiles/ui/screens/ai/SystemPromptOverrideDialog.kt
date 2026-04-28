package com.glassfiles.ui.screens.ai

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glassfiles.data.Strings

/**
 * Dialog for editing the per-repo system-prompt override. Empty submit
 * is treated as "clear" so the user can wipe a prior override without
 * a separate menu item. The dialog has no visual chrome beyond the
 * default `AlertDialog` so it inherits Light / Dark / AMOLED styling
 * from `MaterialTheme.colorScheme.*` automatically.
 *
 * @param repoFullName  shown in the title — we want the user to know
 *                      which repo they're configuring.
 * @param initialPrompt prefilled value pulled from `AiAgentPrefs`.
 * @param onSave        called with the trimmed text (blank = clear).
 * @param onDismiss     close without persisting.
 */
@Composable
fun SystemPromptOverrideDialog(
    repoFullName: String,
    initialPrompt: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var text by remember { mutableStateOf(initialPrompt) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    Strings.aiAgentSystemPromptTitle,
                    fontSize = 16.sp,
                    color = colors.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    repoFullName,
                    fontSize = 12.sp,
                    color = colors.onSurfaceVariant,
                )
            }
        },
        text = {
            Column {
                Text(
                    Strings.aiAgentSystemPromptHint,
                    fontSize = 13.sp,
                    color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = {
                        Text(
                            Strings.aiAgentSystemPromptPlaceholder,
                            fontSize = 14.sp,
                            color = colors.onSurfaceVariant,
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 240.dp),
                    minLines = 5,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(text.trim()) }) {
                Text(Strings.aiAgentSystemPromptSave, color = colors.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(Strings.aiAgentSystemPromptCancel, color = colors.onSurfaceVariant)
            }
        },
    )
}
