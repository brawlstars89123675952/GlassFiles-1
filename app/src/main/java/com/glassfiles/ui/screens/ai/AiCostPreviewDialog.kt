package com.glassfiles.ui.screens.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.glassfiles.data.ai.usage.AiUsageAccounting
import com.glassfiles.ui.components.AiModuleAlertDialog
import com.glassfiles.ui.components.AiModulePillButton
import com.glassfiles.ui.theme.AiModuleTheme
import com.glassfiles.ui.theme.JetBrainsMono

/**
 * Pre-request cost preview confirmation dialog. Shown when the
 * estimated cost of the next AI request exceeds the user's threshold
 * (see [com.glassfiles.data.ai.AiCostPreviewPrefs]). Lets the user
 * decide whether to proceed or cancel before any tokens are spent.
 *
 * The dialog is intentionally minimal — just an estimated-cost number,
 * an estimated-tokens number, and two buttons. We never claim the cost
 * is exact; the displayed value carries the same ~ prefix the rest of
 * the AI module uses for estimated values.
 *
 * @param estimatedCostUsd Best-effort cost estimate for the upcoming
 *   request. Always treated as estimated (rendered with `~`); the real
 *   reported cost is only known after the API responds.
 * @param estimatedTokens Best-effort token total (input + output).
 * @param thresholdUsd User's currently-configured threshold, shown in
 *   the body so the user can recall what triggered the dialog.
 * @param onConfirm Called when the user accepts. The caller should
 *   then proceed to send the request.
 * @param onDismiss Called for cancel / back / outside-tap. The request
 *   should not be sent.
 */
@Composable
fun AiCostPreviewDialog(
    estimatedCostUsd: Double,
    estimatedTokens: Int,
    thresholdUsd: Double,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = AiModuleTheme.colors
    AiModuleAlertDialog(
        onDismissRequest = onDismiss,
        title = "estimated cost above threshold",
        confirmButton = {
            AiModulePillButton(
                label = "y · send anyway",
                onClick = onConfirm,
                accent = true,
            )
        },
        dismissButton = {
            AiModulePillButton(
                label = "n · cancel",
                onClick = onDismiss,
                accent = false,
            )
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "this request is expected to cost",
                fontFamily = JetBrainsMono,
                fontSize = 12.sp,
                color = colors.textMuted,
                lineHeight = 1.4.em,
            )
            Text(
                text = AiUsageAccounting.formatUsd(estimatedCostUsd, estimated = true) +
                    " · " +
                    AiUsageAccounting.formatTokens(estimatedTokens, estimated = true) +
                    " tok",
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                color = colors.warning,
            )
            Text(
                text = "your threshold: " +
                    AiUsageAccounting.formatUsd(thresholdUsd) +
                    "  ·  set in Settings → AI Module",
                fontFamily = JetBrainsMono,
                fontSize = 11.sp,
                color = colors.textMuted,
                lineHeight = 1.4.em,
                modifier = Modifier,
            )
        }
    }
}
