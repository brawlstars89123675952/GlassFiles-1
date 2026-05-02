package com.glassfiles.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.glassfiles.ui.theme.AiModuleTheme
import com.glassfiles.ui.theme.JetBrainsMono
import com.glassfiles.ui.theme.toCodeColors

/**
 * AI-module twin of
 * [com.glassfiles.ui.screens.ai.terminal.AgentTerminalCodeBlock]: the
 * darker, square-shouldered terminal-style code fence used by chat /
 * image / video / usage / settings screens. Mirrors the agent-terminal
 * implementation visually but reads palette/typography from
 * [AiModuleTheme] so it does not bridge into the agent-terminal
 * namespace.
 */
@Composable
fun AiModuleCodeBlock(
    text: String,
    lang: String,
    filePath: String? = null,
    context: Context,
) {
    var fullscreen by remember { mutableStateOf(false) }
    if (fullscreen) {
        AiModuleFullscreenCodeView(
            text = text,
            lang = lang,
            filePath = filePath,
            context = context,
            onClose = { fullscreen = false },
        )
        return
    }
    val colors = AiModuleTheme.colors
    val highlighted: AnnotatedString = remember(text, lang, colors) {
        highlightCode(text, lang, colors.toCodeColors())
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(12.dp)),
    ) {
        AiModuleCodeHeader(
            lang = lang,
            filePath = filePath,
            onCopy = { copyCodeToClipboard(context, text) },
            onExpand = { fullscreen = true },
        )
        Box(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            AiModuleText(
                text = highlighted,
                fontSize = AiModuleTheme.type.code,
                fontFamily = JetBrainsMono,
                color = colors.textPrimary,
                lineHeight = 1.25.em,
            )
        }
    }
}

@Composable
private fun AiModuleCodeHeader(
    lang: String,
    filePath: String?,
    onCopy: () -> Unit,
    onExpand: () -> Unit,
) {
    val colors = AiModuleTheme.colors
    val display = buildString {
        append(lang.ifBlank { "code" }.replaceFirstChar { it.uppercase() })
        if (!filePath.isNullOrBlank()) {
            append("  \u00B7  ") // ·
            append(filePath)
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.surfaceElevated)
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AiModuleText(
            display,
            fontSize = AiModuleTheme.type.label,
            color = colors.textSecondary,
            fontFamily = JetBrainsMono,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp),
        )
        AiModuleIconButton(onClick = onCopy, modifier = Modifier.size(36.dp)) {
            AiModuleIcon(
                Icons.Rounded.ContentCopy,
                contentDescription = "copy",
                modifier = Modifier.size(16.dp),
                tint = colors.textSecondary,
            )
        }
        AiModuleIconButton(onClick = onExpand, modifier = Modifier.size(36.dp)) {
            AiModuleIcon(
                Icons.Rounded.OpenInFull,
                contentDescription = "expand",
                modifier = Modifier.size(16.dp),
                tint = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun AiModuleFullscreenCodeView(
    text: String,
    lang: String,
    filePath: String?,
    context: Context,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val colors = AiModuleTheme.colors
    val highlighted: AnnotatedString = remember(text, lang, colors) {
        highlightCode(text, lang, colors.toCodeColors())
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(colors.surfaceElevated)
                .padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AiModuleText(
                buildString {
                    append(lang.ifBlank { "code" }.replaceFirstChar { it.uppercase() })
                    if (!filePath.isNullOrBlank()) {
                        append("  \u00B7  "); append(filePath)
                    }
                },
                fontSize = AiModuleTheme.type.label,
                color = colors.textSecondary,
                fontFamily = JetBrainsMono,
                modifier = Modifier.weight(1f).padding(vertical = 10.dp),
            )
            AiModuleIconButton(onClick = { copyCodeToClipboard(context, text) }, modifier = Modifier.size(40.dp)) {
                AiModuleIcon(
                    Icons.Rounded.ContentCopy,
                    contentDescription = "copy",
                    modifier = Modifier.size(18.dp),
                    tint = colors.textSecondary,
                )
            }
            AiModuleIconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
                AiModuleIcon(
                    Icons.Rounded.Close,
                    contentDescription = "close",
                    modifier = Modifier.size(18.dp),
                    tint = colors.textSecondary,
                )
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            AiModuleText(
                text = highlighted,
                fontSize = AiModuleTheme.type.code,
                fontFamily = JetBrainsMono,
                color = colors.textPrimary,
                lineHeight = 1.25.em,
            )
        }
    }
}

private fun copyCodeToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("code", text))
}
