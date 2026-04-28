package com.glassfiles.ui.screens.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glassfiles.data.Strings

/**
 * Information block surfaced to the user before a potentially-expensive
 * AI Agent run is allowed to start. Built in `submit` from the active
 * picker selection plus the seed conversation length.
 *
 * @property repoFullName     `owner/repo` label.
 * @property branch            active branch.
 * @property providerLabel     human-readable provider name (e.g. "OpenAI").
 * @property modelLabel        human-readable model name.
 * @property approxFiles       approximate count of files this task may touch.
 *                             Reserved for future tool-aware estimates;
 *                             today we surface the seed transcript size only.
 * @property approxContextChars rough character count of the existing
 *                             conversation that will be re-sent.
 * @property isPrivate         whether the repo is private (changes the
 *                             tone of the warning copy).
 * @property reason            why the warning fired — used to colour the
 *                             title and help the user decide.
 */
data class ExpensiveActionWarning(
    val repoFullName: String,
    val branch: String,
    val providerLabel: String,
    val modelLabel: String,
    val approxFiles: Int,
    val approxContextChars: Int,
    val isPrivate: Boolean,
    val reason: ExpensiveActionReason,
)

enum class ExpensiveActionReason {
    /** Repo is private and we have no remembered "OK" for this provider. */
    PrivateRepo,
    /** Cost mode is MaxQuality — always warn so the user knows. */
    MaxQualityMode,
    /** Seed conversation is past the soft warning threshold. */
    LargeContext,
}

/**
 * Confirmation dialog that gates the start of a potentially-expensive
 * AI Agent run. Three actions:
 *
 *  - Cancel             — reject the run; user message stays in input.
 *  - Continue once      — allow this one run only.
 *  - Continue & remember — allow this run AND skip the dialog for the
 *                          same `(repo, provider)` next time. Hidden when
 *                          [allowRemember] is false (private repo with
 *                          no remembered exception, etc).
 */
@Composable
fun ExpensiveActionWarningDialog(
    warning: ExpensiveActionWarning,
    allowRemember: Boolean,
    onCancel: () -> Unit,
    onContinueOnce: () -> Unit,
    onContinueAndRemember: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var rememberChecked by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Warning,
                    null,
                    Modifier.size(18.dp),
                    tint = colors.tertiary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    Strings.aiCostWarningTitle,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    when (warning.reason) {
                        ExpensiveActionReason.PrivateRepo -> Strings.aiCostWarningReasonPrivate
                        ExpensiveActionReason.MaxQualityMode -> Strings.aiCostWarningReasonMaxMode
                        ExpensiveActionReason.LargeContext -> Strings.aiCostWarningReasonLarge
                    },
                    fontSize = 13.sp,
                    color = colors.onSurface,
                )
                Spacer(Modifier.size(2.dp))
                LabeledRow(Strings.aiCostWarningRepo, warning.repoFullName + if (warning.isPrivate) " · ${Strings.aiCostWarningPrivate}" else "")
                LabeledRow(Strings.aiCostWarningBranch, warning.branch)
                LabeledRow(Strings.aiCostWarningProvider, warning.providerLabel)
                LabeledRow(Strings.aiCostWarningModel, warning.modelLabel)
                if (warning.approxFiles > 0) {
                    LabeledRow(Strings.aiCostWarningFiles, warning.approxFiles.toString())
                }
                LabeledRow(
                    Strings.aiCostWarningContext,
                    Strings.aiCostWarningChars.replace("{n}", "~${(warning.approxContextChars / 1000)}k"),
                )
                Spacer(Modifier.size(2.dp))
                Text(
                    Strings.aiCostWarningTransmitNote,
                    fontSize = 11.sp,
                    color = colors.onSurfaceVariant,
                )
                if (allowRemember) {
                    Spacer(Modifier.size(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = rememberChecked,
                            onCheckedChange = { rememberChecked = it },
                        )
                        Text(
                            Strings.aiCostWarningRememberLabel,
                            fontSize = 12.sp,
                            color = colors.onSurface,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (allowRemember && rememberChecked) onContinueAndRemember()
                else onContinueOnce()
            }) {
                Text(
                    if (allowRemember && rememberChecked) Strings.aiCostWarningContinueRemember
                    else Strings.aiCostWarningContinueOnce,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(Strings.cancel)
            }
        },
    )
}

@Composable
private fun LabeledRow(label: String, value: String) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.Top) {
        Text(
            label,
            fontSize = 12.sp,
            color = colors.onSurfaceVariant,
            modifier = Modifier.width(96.dp),
        )
        Text(
            value,
            fontSize = 12.sp,
            color = colors.onSurface,
            fontWeight = FontWeight.Medium,
        )
    }
}
