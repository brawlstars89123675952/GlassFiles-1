package com.glassfiles.ui.components

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.glassfiles.ui.theme.AiModuleSurface
import com.glassfiles.ui.theme.AiModuleTheme
import com.glassfiles.ui.theme.JetBrainsMono

@Composable
fun AiModulePageBar(
    title: String,
    onBack: () -> Unit,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = AiModuleTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.background)
            .statusBarsPadding()
            .padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // Back glyph — JetBrainsMono "←" instead of Material icon to match the rest of the bar.
            AiModuleGlyphAction(
                glyph = "\u2190",
                onClick = onBack,
                contentDescription = "back",
                tint = colors.textPrimary,
            )
            Spacer(Modifier.width(2.dp))
            // Title takes whatever space is left after the trailing actions
            // and ellipsizes — fixes the long-name overflow that pushed
            // icons off-screen for repos like "GKI_KernelSU_SUSFS-main".
            Text(
                text = title,
                color = colors.textPrimary,
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.Medium,
                fontSize = AiModuleTheme.type.topBarTitle,
                lineHeight = 1.25.em,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (trailing != null) {
                Spacer(Modifier.width(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) { trailing() }
            }
        }
        if (!subtitle.isNullOrBlank()) {
            Row(Modifier.fillMaxWidth().padding(start = 44.dp, top = 1.dp)) {
                Text(
                    text = subtitle,
                    color = colors.textMuted,
                    fontFamily = JetBrainsMono,
                    fontSize = 11.sp,
                    lineHeight = 1.2.em,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
    }
}

/**
 * Square 36dp clickable cell rendering a single JetBrainsMono glyph.
 * Use this in place of `IconButton { Icon(Icons.Rounded.X, …) }` so the
 * AiModule chrome stays purely typographic and never bleeds in
 * Material-icon visuals.
 */
@Composable
fun AiModuleGlyphAction(
    glyph: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = AiModuleTheme.colors.textSecondary,
    fontSize: androidx.compose.ui.unit.TextUnit = 16.sp,
    contentDescription: String? = null,
) {
    val effective = if (enabled) tint else tint.copy(alpha = 0.35f)
    Box(
        modifier
            .size(36.dp)
            .clip(RoundedCornerShape(6.dp))
            .let { if (enabled) it.clickable(onClick = onClick) else it },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = glyph,
            color = effective,
            fontFamily = JetBrainsMono,
            fontWeight = FontWeight.Medium,
            fontSize = fontSize,
            lineHeight = 1.em,
        )
    }
}

/**
 * Inline JetBrainsMono glyph with no click target — for status badges
 * in lists / cards where Material `Icon(...)` would be used otherwise.
 */
@Composable
fun AiModuleGlyph(
    glyph: String,
    modifier: Modifier = Modifier,
    tint: Color = AiModuleTheme.colors.textSecondary,
    fontSize: androidx.compose.ui.unit.TextUnit = 14.sp,
) {
    Text(
        text = glyph,
        color = tint,
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight.Medium,
        fontSize = fontSize,
        lineHeight = 1.em,
        modifier = modifier,
    )
}

@Composable
fun AiModuleSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    val colors = AiModuleTheme.colors
    Text(
        text = text.uppercase(),
        color = colors.textSecondary,
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight.Medium,
        fontSize = AiModuleTheme.type.label,
        letterSpacing = 0.6.sp,
        modifier = modifier,
    )
}

@Composable
fun AiModuleHairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(AiModuleTheme.colors.border))
}

@Composable
fun AiModuleCard(
    modifier: Modifier = Modifier,
    elevated: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = AiModuleTheme.colors
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

@Composable
fun AiModulePillButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
    accent: Boolean = true,
    leadingIcon: ImageVector? = null,
) {
    val colors = AiModuleTheme.colors
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

@Composable
fun AiModuleCheckRow(
    label: String,
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    val colors = AiModuleTheme.colors
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

@Composable
fun AiModuleSegmented(
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
            AiModulePillButton(
                label = label,
                onClick = { onSelect(index) },
                accent = index == selectedIndex,
            )
        }
    }
}

@Composable
fun AiModuleListRow(
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
    val colors = AiModuleTheme.colors
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

@Composable
fun AiModuleChip(
    label: String,
    modifier: Modifier = Modifier,
    color: Color? = null,
    filled: Boolean = false,
) {
    val colors = AiModuleTheme.colors
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

@Composable
fun AiModuleKeyValueRow(
    key: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color? = null,
) {
    val colors = AiModuleTheme.colors
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

@Composable
fun AiModuleScreenScaffold(
    title: String,
    onBack: () -> Unit,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    bottomBar: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    AiModuleSurface {
        Column(Modifier.fillMaxWidth().background(AiModuleTheme.colors.background)) {
            AiModulePageBar(
                title = title,
                onBack = onBack,
                subtitle = subtitle,
                trailing = trailing,
            )
            Box(Modifier.fillMaxWidth().weight(1f)) {
                content()
            }
            if (bottomBar != null) {
                Box(Modifier.fillMaxWidth().navigationBarsPadding().imePadding()) {
                    bottomBar()
                }
            }
        }
    }
}

private val AiModuleSpinnerFrames = listOf(
    "\u280B", "\u2819", "\u2839", "\u2838", "\u283C",
    "\u2834", "\u2826", "\u2827", "\u2807", "\u280F",
)

/**
 * Braille-cell spinner rendered in [JetBrainsMono]. Cycles one frame
 * every 80 ms. Optionally renders a trailing mono label like
 * `loading runs…`.
 */
@Composable
fun AiModuleSpinner(
    label: String? = null,
    modifier: Modifier = Modifier,
) {
    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(80L)
            index = (index + 1) % AiModuleSpinnerFrames.size
        }
    }
    val colors = AiModuleTheme.colors
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = AiModuleSpinnerFrames[index],
            color = colors.accent,
            fontFamily = JetBrainsMono,
            fontSize = AiModuleTheme.type.label,
        )
        if (!label.isNullOrBlank()) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                color = colors.textMuted,
                fontFamily = JetBrainsMono,
                fontSize = AiModuleTheme.type.label,
            )
        }
    }
}

private enum class AiModuleButtonKind { PRIMARY, SECONDARY, DESTRUCTIVE }

@Composable
private fun AiModuleButtonImpl(
    label: String,
    onClick: () -> Unit,
    kind: AiModuleButtonKind,
    modifier: Modifier,
    enabled: Boolean,
) {
    val colors = AiModuleTheme.colors
    val (bg, fg, borderColor) = when {
        !enabled -> Triple(colors.surface, colors.textMuted, colors.border)
        kind == AiModuleButtonKind.PRIMARY -> Triple(colors.accent, colors.background, colors.accent)
        kind == AiModuleButtonKind.SECONDARY -> Triple(Color.Transparent, colors.accent, colors.accent)
        else /* DESTRUCTIVE */ -> Triple(Color.Transparent, colors.error, colors.error)
    }
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier
            .heightIn(min = 36.dp)
            .clip(shape)
            .background(bg)
            .border(1.dp, borderColor, shape)
            .let { if (enabled) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = fg,
            fontFamily = JetBrainsMono,
            fontSize = AiModuleTheme.type.label,
            fontWeight = FontWeight.Medium,
        )
    }
}

/**
 * Filled accent button. Use for the single primary action of a screen
 * (e.g. `[ ⏵ run workflow → ]`, `[ view full logs → ]`).
 */
@Composable
fun AiModulePrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) = AiModuleButtonImpl(label, onClick, AiModuleButtonKind.PRIMARY, modifier, enabled)

/**
 * Outlined accent button. Use for the second-tier action that lives next
 * to a primary one (e.g. `[ refresh ]`, `[ rerun → ]`).
 */
@Composable
fun AiModuleSecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) = AiModuleButtonImpl(label, onClick, AiModuleButtonKind.SECONDARY, modifier, enabled)

/**
 * Outlined error-colored button for destructive actions
 * (e.g. `[ cancel ]`, `[ delete logs ]`).
 */
@Composable
fun AiModuleDestructiveButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) = AiModuleButtonImpl(label, onClick, AiModuleButtonKind.DESTRUCTIVE, modifier, enabled)
