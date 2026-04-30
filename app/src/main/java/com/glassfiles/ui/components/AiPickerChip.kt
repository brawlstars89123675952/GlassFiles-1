package com.glassfiles.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import com.glassfiles.ui.screens.ai.terminal.AgentTerminalDarkColors
import com.glassfiles.ui.screens.ai.terminal.JetBrainsMono

/**
 * Compact terminal-style picker chip used across the AI module.
 * Renders as a bordered mono row showing `LABEL  value  ▾`. Tapping
 * opens [AiPickerSheet] with search + scroll list. Single source of
 * truth so coding / image-gen / video-gen / models / settings screens
 * all share the same affordance and look.
 */
@Composable
fun <T> AiPickerChip(
    label: String,
    value: String,
    title: String,
    options: List<T>,
    optionLabel: (T) -> String,
    optionSubtitle: (T) -> String? = { null },
    selected: T?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = AgentTerminalDarkColors
    var open by remember { mutableStateOf(false) }

    Row(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(6.dp))
            .clickable(enabled = enabled && options.isNotEmpty()) { open = true }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                label.uppercase(),
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.6.sp,
                color = colors.textMuted,
                fontFamily = JetBrainsMono,
                lineHeight = 1.2.em,
            )
            Text(
                value,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = if (enabled) colors.textPrimary else colors.textMuted,
                fontFamily = JetBrainsMono,
                maxLines = 1,
                lineHeight = 1.3.em,
            )
        }
        Icon(
            Icons.Rounded.KeyboardArrowDown,
            null,
            Modifier.size(14.dp),
            tint = colors.textMuted,
        )
    }

    if (open) {
        AiPickerSheet(
            title = title,
            options = options,
            optionLabel = optionLabel,
            optionSubtitle = optionSubtitle,
            selected = selected,
            onDismiss = { open = false },
            onSelect = onSelect,
        )
    }
}
