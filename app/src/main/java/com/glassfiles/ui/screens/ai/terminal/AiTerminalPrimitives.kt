package com.glassfiles.ui.screens.ai.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Reusable terminal-mode UI primitives shared by every AI-module
 * screen. The agent screen has its own bespoke composables in this
 * package; for everything else (Hub, Keys, Models, Usage, Settings,
 * Chat, Coding, ImageGen, VideoGen) prefer these primitives so the
 * screens stay visually consistent.
 *
 * All primitives expect [AgentTerminalSurface] in the ancestor tree
 * (i.e. they read the palette via [AgentTerminal.colors]).
 */

/**
 * Generic "page header" topbar with optional trailing action slot.
 * Mirrors [AgentTopBar] but trims it down to the bits non-agent screens
 * actually need: back button, title, optional subtitle, optional
 * trailing actions (icons / buttons / chips).
 */
@Composable
fun TerminalPageBar(
    title: String,
    onBack: () -> Unit,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = AgentTerminal.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.background)
            .padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "back",
                    modifier = Modifier.size(18.dp),
                    tint = colors.textPrimary,
                )
            }
            Spacer(Modifier.width(4.dp))
            Text(
                text = title,
                color = colors.textPrimary,
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.Medium,
                fontSize = AgentTerminal.type.topBarTitle,
                lineHeight = 1.25.em,
            )
            Spacer(Modifier.weight(1f))
            if (trailing != null) trailing()
        }
        if (!subtitle.isNullOrBlank()) {
            Row(Modifier.fillMaxWidth().padding(start = 44.dp, top = 1.dp)) {
                Text(
                    text = subtitle,
                    color = colors.textMuted,
                    fontFamily = JetBrainsMono,
                    fontSize = 11.sp,
                    lineHeight = 1.2.em,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
    }
}

/** Uppercase muted label, like `> SECTION` in a man page. */
@Composable
fun TerminalSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    val colors = AgentTerminal.colors
    Text(
        text = text.uppercase(),
        color = colors.textSecondary,
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight.Medium,
        fontSize = AgentTerminal.type.label,
        letterSpacing = 0.6.sp,
        modifier = modifier,
    )
}

/** Hairline horizontal divider in the border color. */
@Composable
fun TerminalHairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(AgentTerminal.colors.border))
}

/**
 * Bordered surface that groups related rows. Pure container — pad and
 * lay out children at the call site so each screen can mix list items
 * and form fields without nested padding.
 */
@Composable
fun TerminalCard(
    modifier: Modifier = Modifier,
    elevated: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = AgentTerminal.colors
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (elevated) colors.surfaceElevated else colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(8.dp)),
    ) {
        content()
    }
}

/**
 * Compact mono "[ label ]" button used for secondary actions. Renders
 * with a 1dp accent border by default; the [destructive] variant uses
 * the warning color and is meant for destructive actions like clearing
 * a key. Disabled buttons fade to muted.
 */
@Composable
fun TerminalPillButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
    accent: Boolean = true,
    leadingIcon: ImageVector? = null,
) {
    val colors = AgentTerminal.colors
    val tint = when {
        !enabled -> colors.textMuted
        destructive -> colors.warning
        accent -> colors.accent
        else -> colors.textSecondary
    }
    val border = when {
        !enabled -> colors.border
        destructive -> colors.warning
        accent -> colors.accent
        else -> colors.border
    }
    Row(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, border, RoundedCornerShape(6.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Icon(leadingIcon, null, Modifier.size(14.dp), tint = tint)
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = "[ $label ]",
            color = tint,
            fontFamily = JetBrainsMono,
            fontSize = 13.sp,
            lineHeight = 1.25.em,
        )
    }
}

/**
 * ASCII-style toggle row: `[✓] label` or `[ ] label`. Click anywhere on
 * the row to flip. Matches the agent settings sheet aesthetic.
 */
@Composable
fun TerminalCheckRow(
    label: String,
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    val colors = AgentTerminal.colors
    Row(
        modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (checked) "[✓]" else "[ ]",
            color = if (checked) colors.accent else colors.textSecondary,
            fontFamily = JetBrainsMono,
            fontSize = 14.sp,
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                color = colors.textPrimary,
                fontFamily = JetBrainsMono,
                fontSize = 14.sp,
                lineHeight = 1.3.em,
            )
            if (!description.isNullOrBlank()) {
                Text(
                    text = description,
                    color = colors.textMuted,
                    fontFamily = JetBrainsMono,
                    fontSize = 11.sp,
                    lineHeight = 1.3.em,
                )
            }
        }
    }
}

/**
 * Segmented control rendered as `[ a ][ b ][ c ]` inline. The selected
 * segment uses an accent border and accent text, the rest fade to
 * muted. Wrap on small screens by setting [horizontalArrangement].
 */
@Composable
fun TerminalSegmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEachIndexed { index, label ->
            TerminalPillButton(
                label = label,
                onClick = { onSelect(index) },
                accent = index == selectedIndex,
            )
        }
    }
}

/**
 * Generic list row: `prefix  title          trailing`. Optional
 * subtitle below the title, optional leading icon, click handler. Used
 * for AiHub routes, models lists, key rows etc.
 */
@Composable
fun TerminalListRow(
    title: String,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    prefix: String? = null,
    prefixColor: Color? = null,
    subtitle: String? = null,
    leadingIcon: ImageVector? = null,
    leadingTint: Color? = null,
    trailing: (@Composable () -> Unit)? = null,
    titleColor: Color? = null,
    paddingVertical: Dp = 12.dp,
) {
    val colors = AgentTerminal.colors
    val rowMod = if (onClick != null) modifier.clickable { onClick() } else modifier
    Row(
        rowMod
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = paddingVertical),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (prefix != null) {
            Text(
                text = prefix,
                color = prefixColor ?: colors.accent,
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
            )
            Spacer(Modifier.width(8.dp))
        }
        if (leadingIcon != null) {
            Icon(
                leadingIcon,
                null,
                Modifier.size(16.dp),
                tint = leadingTint ?: colors.textSecondary,
            )
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = titleColor ?: colors.textPrimary,
                fontFamily = JetBrainsMono,
                fontSize = 14.sp,
                lineHeight = 1.3.em,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    color = colors.textMuted,
                    fontFamily = JetBrainsMono,
                    fontSize = 11.sp,
                    lineHeight = 1.3.em,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

/**
 * Status pill / chip — short uppercase label rendered with an accent
 * border. Common usage: provider badges, capability tags, mode
 * indicators.
 */
@Composable
fun TerminalChip(
    label: String,
    modifier: Modifier = Modifier,
    color: Color? = null,
    filled: Boolean = false,
) {
    val colors = AgentTerminal.colors
    val tint = color ?: colors.textSecondary
    Box(
        modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (filled) tint else Color.Transparent)
            .border(1.dp, tint, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Text(
            text = label,
            color = if (filled) colors.background else tint,
            fontFamily = JetBrainsMono,
            fontWeight = FontWeight.Medium,
            fontSize = 10.sp,
            letterSpacing = 0.4.sp,
            lineHeight = 1.2.em,
        )
    }
}

/**
 * Single-line key/value row: `> key  ··········  value`. The label is
 * fixed-width muted, the value primary. Commonly used in usage / model
 * detail tables.
 */
@Composable
fun TerminalKeyValueRow(
    key: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color? = null,
) {
    val colors = AgentTerminal.colors
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 24.dp)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = key,
            color = colors.textSecondary,
            fontFamily = JetBrainsMono,
            fontSize = 13.sp,
            lineHeight = 1.3.em,
            modifier = Modifier.weight(0.4f),
        )
        Text(
            text = value,
            color = valueColor ?: colors.textPrimary,
            fontFamily = JetBrainsMono,
            fontSize = 13.sp,
            lineHeight = 1.3.em,
            modifier = Modifier.weight(0.6f),
        )
    }
}

/** Wraps content in [AgentTerminalSurface] + a top page bar in one go. */
@Composable
fun TerminalScreenScaffold(
    title: String,
    onBack: () -> Unit,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    bottomBar: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    AgentTerminalSurface {
        Column(Modifier.fillMaxWidth().background(AgentTerminal.colors.background)) {
            TerminalPageBar(
                title = title,
                onBack = onBack,
                subtitle = subtitle,
                trailing = trailing,
            )
            Box(Modifier.fillMaxWidth().weight(1f)) {
                content()
            }
            if (bottomBar != null) bottomBar()
        }
    }
}
