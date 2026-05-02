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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.glassfiles.ui.theme.AiModuleTheme
import com.glassfiles.ui.theme.JetBrainsMono

/**
 * Terminal-style modal dialog rendered with the AiModule palette.
 * The frame is a 1px hairline on `surface`, the title is prefixed
 * with `> `, and confirm/dismiss actions use AiModule pill buttons.
 */
@Composable
fun AiModuleAlertDialog(
    onDismissRequest: () -> Unit,
    title: String? = null,
    confirmButton: (@Composable () -> Unit)? = null,
    dismissButton: (@Composable () -> Unit)? = null,
    properties: DialogProperties = DialogProperties(),
    content: (@Composable () -> Unit)? = null,
) {
    val palette = AiModuleTheme.colors
    Dialog(onDismissRequest = onDismissRequest, properties = properties) {
        Column(
            Modifier
                .widthIn(min = 280.dp, max = 480.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(palette.surface)
                .border(1.dp, palette.border, RoundedCornerShape(10.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            if (!title.isNullOrBlank()) {
                AiModuleText(
                    text = "> $title",
                    color = palette.textPrimary,
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                    lineHeight = 1.3.em,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(10.dp))
            }
            if (content != null) {
                content()
            }
            if (confirmButton != null || dismissButton != null) {
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (dismissButton != null) {
                        dismissButton()
                        Spacer(Modifier.width(8.dp))
                    }
                    if (confirmButton != null) {
                        confirmButton()
                    }
                }
            }
        }
    }
}

/**
 * AiModule text input. Replaces `OutlinedTextField` / `TextField` while
 * keeping the field cheap with no decorated text-field chrome.
 *
 * Renders a 1px hairline frame on [AiModuleTheme.colors.surface], with
 * a leading slot for prompt glyph (e.g. `?`, `/`, `>`) and a trailing
 * slot for actions like clear / submit.
 */
@Composable
fun AiModuleTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    label: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = 1,
    singleLine: Boolean = maxLines == 1,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val palette = AiModuleTheme.colors
    Column(modifier.fillMaxWidth()) {
        if (!label.isNullOrBlank()) {
            AiModuleText(
                text = label,
                color = palette.textSecondary,
                fontFamily = JetBrainsMono,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 2.dp, bottom = 4.dp),
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 38.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(palette.surface)
                .border(1.dp, palette.border, RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(8.dp))
            }
            Box(Modifier.weight(1f)) {
                if (value.isEmpty() && !placeholder.isNullOrBlank()) {
                    AiModuleText(
                        text = placeholder,
                        color = palette.textMuted,
                        fontFamily = JetBrainsMono,
                        fontSize = 13.sp,
                        lineHeight = 1.3.em,
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = enabled,
                    singleLine = singleLine,
                    minLines = minLines,
                    maxLines = maxLines,
                    visualTransformation = visualTransformation,
                    keyboardOptions = keyboardOptions,
                    keyboardActions = keyboardActions,
                    cursorBrush = SolidColor(palette.accent),
                    textStyle = TextStyle(
                        color = palette.textPrimary,
                        fontFamily = JetBrainsMono,
                        fontSize = 13.sp,
                        lineHeight = 1.3.em,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (trailing != null) {
                Spacer(Modifier.width(6.dp))
                trailing()
            }
        }
    }
}

/**
 * Search-flavoured [AiModuleTextField] — leading `?` glyph, optional
 * clear button when non-empty.
 */
@Composable
fun AiModuleSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "search…",
    enabled: Boolean = true,
    onSubmit: (() -> Unit)? = null,
) {
    val palette = AiModuleTheme.colors
    AiModuleTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        placeholder = placeholder,
        enabled = enabled,
        leading = { AiModuleGlyph("?", tint = palette.textMuted, fontSize = 14.sp) },
        trailing = if (value.isNotEmpty()) {
            { AiModuleGlyphAction(glyph = "\u00d7", onClick = { onValueChange("") }, fontSize = 14.sp) }
        } else null,
        keyboardActions = if (onSubmit != null) {
            KeyboardActions(onSearch = { onSubmit() }, onDone = { onSubmit() })
        } else KeyboardActions.Default,
        keyboardOptions = if (onSubmit != null) {
            KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search)
        } else KeyboardOptions.Default,
    )
}

/**
 * Borderless text-only AiModule action button.
 * Use as `dismissButton` / inline secondary actions where a bordered
 * pill would be visually heavy.
 */
@Composable
fun AiModuleTextAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = AiModuleTheme.colors.accent,
) {
    val palette = AiModuleTheme.colors
    val effective = if (enabled) tint else palette.textMuted
    Box(
        modifier
            .clip(RoundedCornerShape(4.dp))
            .let { if (enabled) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        AiModuleText(
            text = label,
            color = effective,
            fontFamily = JetBrainsMono,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            lineHeight = 1.em,
        )
    }
}
