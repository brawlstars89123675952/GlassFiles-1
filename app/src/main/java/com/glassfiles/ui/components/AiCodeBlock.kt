package com.glassfiles.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glassfiles.data.Strings
import com.glassfiles.data.ai.AiSyntaxTheme

/**
 * Claude-style code block.
 *
 * - Rounded card with a darker header strip on top.
 * - Header shows the language label on the left, copy + expand actions on the
 *   right.
 * - Body uses a fixed [AiSyntaxTheme] palette so the colour theme of the app
 *   does not bleed into the syntax highlighter (the user explicitly asked for
 *   this — picking a strong primary in the app theme used to tint keywords
 *   and strings into a single colour).
 * - Tapping "Expand" opens [FullscreenCodeView] which fills the screen and
 *   handles the predictive-back gesture.
 */
@Composable
fun AiCodeBlock(
    text: String,
    lang: String,
    theme: AiSyntaxTheme,
    fontSizeSp: Int,
    context: Context,
) {
    var fullscreen by remember { mutableStateOf(false) }

    if (fullscreen) {
        FullscreenCodeView(
            text = text,
            lang = lang,
            theme = theme,
            fontSizeSp = fontSizeSp,
            context = context,
            onClose = { fullscreen = false },
        )
        return
    }

    val highlighted: AnnotatedString = remember(text, lang, theme) {
        highlightCode(text, lang, theme.toCodeColors())
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(theme.bgColor),
    ) {
        CodeHeader(
            lang = lang,
            theme = theme,
            onCopy = {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("code", text))
            },
            onExpand = { fullscreen = true },
        )
        Box(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(
                text = highlighted,
                fontSize = fontSizeSp.sp,
                fontFamily = FontFamily.Monospace,
                color = theme.plain,
            )
        }
    }
}

@Composable
private fun CodeHeader(
    lang: String,
    theme: AiSyntaxTheme,
    onCopy: () -> Unit,
    onExpand: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(theme.headerColor)
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            lang.ifBlank { "code" },
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.4.sp,
            color = theme.labelColor,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f).padding(vertical = 6.dp),
        )
        IconButton(
            onClick = onCopy,
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                Icons.Rounded.ContentCopy,
                contentDescription = Strings.aiCodingCopy,
                modifier = Modifier.size(14.dp),
                tint = theme.labelColor,
            )
        }
        IconButton(
            onClick = onExpand,
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                Icons.Rounded.OpenInFull,
                contentDescription = Strings.aiSettingsExpandCode,
                modifier = Modifier.size(14.dp),
                tint = theme.labelColor,
            )
        }
    }
}

@Composable
private fun FullscreenCodeView(
    text: String,
    lang: String,
    theme: AiSyntaxTheme,
    fontSizeSp: Int,
    context: Context,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)

    val highlighted: AnnotatedString = remember(text, lang, theme) {
        highlightCode(text, lang, theme.toCodeColors())
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(theme.bgColor)
            .statusBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(theme.headerColor)
                .padding(start = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = Strings.aiCodingDropdownClose,
                    modifier = Modifier.size(20.dp),
                    tint = theme.labelColor,
                )
            }
            Text(
                lang.ifBlank { "code" },
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = theme.labelColor,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("code", text))
                },
            ) {
                Icon(
                    Icons.Rounded.ContentCopy,
                    contentDescription = Strings.aiCodingCopy,
                    modifier = Modifier.size(18.dp),
                    tint = theme.labelColor,
                )
            }
            IconButton(onClick = onClose) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = Strings.aiCodingDropdownClose,
                    modifier = Modifier.size(20.dp),
                    tint = theme.labelColor,
                )
            }
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
        ) {
            Text(
                text = highlighted,
                fontSize = fontSizeSp.sp,
                fontFamily = FontFamily.Monospace,
                color = theme.plain,
            )
        }
    }
}
