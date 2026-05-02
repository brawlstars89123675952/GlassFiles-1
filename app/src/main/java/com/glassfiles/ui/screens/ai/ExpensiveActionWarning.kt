package com.glassfiles.ui.screens.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.glassfiles.data.Strings
import com.glassfiles.ui.components.AiModuleAlertDialog
import com.glassfiles.ui.components.AiModulePillButton
import com.glassfiles.ui.components.AiModuleText
import com.glassfiles.ui.theme.AiModuleDarkColors
import com.glassfiles.ui.theme.JetBrainsMono

/**
 * Information block surfaced to the user before a potentially-expensive
 * AI Agent run is allowed to start.
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
    PrivateRepo,
    MaxQualityMode,
    LargeContext,
}

/**
 * Terminal-themed confirmation dialog gating an expensive AI run.
 */
@Composable
fun ExpensiveActionWarningDialog(
    warning: ExpensiveActionWarning,
    allowRemember: Boolean,
    onCancel: () -> Unit,
    onContinueOnce: () -> Unit,
    onContinueAndRemember: () -> Unit,
) {
    val colors = AiModuleDarkColors
    var rememberChecked by remember { mutableStateOf(false) }

    AiModuleAlertDialog(
        onDismissRequest = onCancel,
        title = "! ${Strings.aiCostWarningTitle.lowercase()}",
        confirmButton = {
            AiModulePillButton(
                label = if (allowRemember && rememberChecked) {
                    Strings.aiCostWarningContinueRemember.lowercase()
                } else {
                    Strings.aiCostWarningContinueOnce.lowercase()
                },
                onClick = {
                    if (allowRemember && rememberChecked) onContinueAndRemember()
                    else onContinueOnce()
                },
                accent = true,
            )
        },
        dismissButton = {
            AiModulePillButton(
                label = Strings.cancel.lowercase(),
                onClick = onCancel,
                accent = false,
            )
        },
    ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AiModuleText(
                    when (warning.reason) {
                        ExpensiveActionReason.PrivateRepo -> Strings.aiCostWarningReasonPrivate
                        ExpensiveActionReason.MaxQualityMode -> Strings.aiCostWarningReasonMaxMode
                        ExpensiveActionReason.LargeContext -> Strings.aiCostWarningReasonLarge
                    },
                    fontSize = 13.sp,
                    color = colors.textPrimary,
                    fontFamily = JetBrainsMono,
                    lineHeight = 1.4.em,
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
                AiModuleText(
                    Strings.aiCostWarningTransmitNote,
                    fontSize = 11.sp,
                    color = colors.textMuted,
                    fontFamily = JetBrainsMono,
                )
                if (allowRemember) {
                    Spacer(Modifier.size(4.dp))
                    Row(
                        Modifier.clickable { rememberChecked = !rememberChecked },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AiModuleText(
                            text = if (rememberChecked) "[✓]" else "[ ]",
                            color = if (rememberChecked) colors.accent else colors.textSecondary,
                            fontFamily = JetBrainsMono,
                            fontSize = 14.sp,
                        )
                        Spacer(Modifier.width(8.dp))
                        AiModuleText(
                            Strings.aiCostWarningRememberLabel,
                            fontSize = 12.sp,
                            color = colors.textPrimary,
                            fontFamily = JetBrainsMono,
                        )
                    }
                }
            }
    }
}

@Composable
private fun LabeledRow(label: String, value: String) {
    val colors = AiModuleDarkColors
    Row(verticalAlignment = Alignment.Top) {
        AiModuleText(
            label,
            fontSize = 12.sp,
            color = colors.textMuted,
            fontFamily = JetBrainsMono,
            modifier = Modifier.width(96.dp),
        )
        AiModuleText(
            value,
            fontSize = 12.sp,
            color = colors.textPrimary,
            fontWeight = FontWeight.Medium,
            fontFamily = JetBrainsMono,
        )
    }
}
