package com.glassfiles.ui.screens.ai.terminal

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
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.glassfiles.ui.components.highlightCode

/**
 * Terminal-themed wrapper around [com.glassfiles.ui.components.AiCodeBlock]'s
 * idea of a code fence. Swaps the shared "Claude-style" rounded card for a
 * darker, square-shouldered terminal block:
 *  - Header: language label + optional file path subtitle, copy + expand
 *  - Body: monospace, horizontal scroll, dense lineHeight (1.25em)
 *  - Expand: full-screen takeover with the same body
 *
 * Lives in the agent-terminal package so the global
 * [androidx.compose.material3.MaterialTheme] is not consulted — palette
 * comes from [AgentTerminal.colors] only.
 */
@Composable
fun AgentTerminalCodeBlock(
    text: String,
    lang: String,
    filePath: String? = null,
    context: Context,
) {
    var fullscreen by remember { mutableStateOf(false) }
    if (fullscreen) {
        AgentFullscreenCodeView(
            text = text,
            lang = lang,
            filePath = filePath,
            context = context,
            onClose = { fullscreen = false },
        )
        return
    }
    val colors = AgentTerminal.colors
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
        AgentCodeHeader(
            lang = lang,
            filePath = filePath,
            onCopy = { copyToClipboard(context, text) },
            onExpand = { fullscreen = true },
        )
        Box(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                text = highlighted,
                fontSize = AgentTerminal.type.code,
                fontFamily = JetBrainsMono,
                color = colors.textPrimary,
                lineHeight = 1.25.em,
            )
        }
    }
}

@Composable
private fun AgentCodeHeader(
    lang: String,
    filePath: String?,
    onCopy: () -> Unit,
    onExpand: () -> Unit,
) {
    val colors = AgentTerminal.colors
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
        Text(
            display,
            fontSize = AgentTerminal.type.label,
            color = colors.textSecondary,
            fontFamily = JetBrainsMono,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp),
        )
        IconButton(onClick = onCopy, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Rounded.ContentCopy,
                contentDescription = "copy",
                modifier = Modifier.size(16.dp),
                tint = colors.textSecondary,
            )
        }
        IconButton(onClick = onExpand, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Rounded.OpenInFull,
                contentDescription = "expand",
                modifier = Modifier.size(16.dp),
                tint = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun AgentFullscreenCodeView(
    text: String,
    lang: String,
    filePath: String?,
    context: Context,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val colors = AgentTerminal.colors
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
            Text(
                buildString {
                    append(lang.ifBlank { "code" }.replaceFirstChar { it.uppercase() })
                    if (!filePath.isNullOrBlank()) {
                        append("  \u00B7  "); append(filePath)
                    }
                },
                fontSize = AgentTerminal.type.label,
                color = colors.textSecondary,
                fontFamily = JetBrainsMono,
                modifier = Modifier.weight(1f).padding(vertical = 10.dp),
            )
            IconButton(onClick = { copyToClipboard(context, text) }, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Rounded.ContentCopy,
                    contentDescription = "copy",
                    modifier = Modifier.size(18.dp),
                    tint = colors.textSecondary,
                )
            }
            IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
                Icon(
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
            Text(
                text = highlighted,
                fontSize = AgentTerminal.type.code,
                fontFamily = JetBrainsMono,
                color = colors.textPrimary,
                lineHeight = 1.25.em,
            )
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("code", text))
}

@Composable
fun AgentInlineDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .height(1.dp)
            .background(AgentTerminal.colors.border),
    )
}
