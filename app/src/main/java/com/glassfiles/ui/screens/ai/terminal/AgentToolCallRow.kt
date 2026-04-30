package com.glassfiles.ui.screens.ai.terminal

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.glassfiles.data.ai.agent.AiToolCall
import com.glassfiles.ui.theme.JetBrainsMono
import com.glassfiles.data.ai.agent.AiToolResult
import org.json.JSONArray
import org.json.JSONObject

/**
 * Tool-call status. Maps to a single-glyph indicator rendered in the
 * gutter just below the call line.
 */
enum class AgentToolStatus { PENDING, SUCCESS, ERROR, CANCELLED, RUNNING }

/**
 * One inline tool-call entry, terminal-style:
 *
 *     $ read_file("AiAgentScreen.kt")
 *       ✓ 847 lines, 24.3kb
 *
 * Tap toggles a "details" view with the full args JSON and result body
 * (no horizontal scroll truncation). The collapsed line truncates args
 * to ~80 visual chars so it stays glanceable in the transcript.
 */
@Composable
fun AgentToolCallRow(
    call: AiToolCall,
    status: AgentToolStatus,
    statusLine: String?,
    result: AiToolResult? = null,
    modifier: Modifier = Modifier,
) {
    val colors = AgentTerminal.colors
    var expanded by remember(call.id) { mutableStateOf(false) }
    val argsLine = remember(call.argsJson) { formatArgs(call.argsJson) }
    val (glyph, glyphColor) = when (status) {
        AgentToolStatus.SUCCESS -> "\u2713" to colors.accent             // ✓
        AgentToolStatus.ERROR -> "\u2717" to colors.error                // ✗
        AgentToolStatus.PENDING -> "\u29B7" to colors.warning           // ⦷ (pending approval)
        AgentToolStatus.CANCELLED -> "\u2298" to colors.textMuted        // ⊘
        AgentToolStatus.RUNNING -> "\u22EF" to colors.warning            // ⋯
    }

    AgentMessageRow(role = AgentRole.TOOL, modifier = modifier) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                text = "${call.name}($argsLine)",
                color = colors.textPrimary,
                fontFamily = JetBrainsMono,
                fontSize = AgentTerminal.type.toolCall,
                lineHeight = 1.4.em,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { expanded = !expanded },
            )
            if (!statusLine.isNullOrBlank()) {
                Row(
                    Modifier.padding(start = 0.dp, top = 2.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = "  $glyph ",
                        color = glyphColor,
                        fontFamily = JetBrainsMono,
                        fontSize = AgentTerminal.type.toolCall,
                        lineHeight = 1.4.em,
                    )
                    Text(
                        text = statusLine,
                        color = colors.textSecondary,
                        fontFamily = JetBrainsMono,
                        fontSize = AgentTerminal.type.toolCall,
                        lineHeight = 1.4.em,
                    )
                }
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                AgentToolDetails(call = call, result = result)
            }
        }
    }
}

@Composable
private fun AgentToolDetails(call: AiToolCall, result: AiToolResult?) {
    val colors = AgentTerminal.colors
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(colors.surface)
            .padding(PaddingValues(horizontal = 12.dp, vertical = 8.dp)),
    ) {
        AgentDetailLabel(text = "args", color = colors.textMuted)
        Text(
            text = prettyJson(call.argsJson),
            color = colors.textSecondary,
            fontFamily = JetBrainsMono,
            fontSize = AgentTerminal.type.toolCall,
            lineHeight = 1.3.em,
        )
        if (result != null) {
            AgentDetailLabel(text = if (result.isError) "error" else "result", color = colors.textMuted)
            Text(
                text = cleanAgentText(result.output),
                color = if (result.isError) colors.error else colors.textPrimary,
                fontFamily = JetBrainsMono,
                fontSize = AgentTerminal.type.toolCall,
                lineHeight = 1.3.em,
            )
        }
    }
}

@Composable
private fun AgentDetailLabel(text: String, color: Color) {
    Text(
        text = text.uppercase(),
        color = color,
        fontFamily = JetBrainsMono,
        fontSize = AgentTerminal.type.label,
        lineHeight = 1.4.em,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
    )
}

/**
 * Compact one-line argument list: `"a", 42, "very/long/path…"`. Strings
 * are kept quoted, numbers and bools as-is. The full string fits inside
 * ~80 visible chars; any tail is replaced with an ellipsis. Falls back
 * to the raw JSON when the input isn't a JSON object (some providers
 * stream a positional list).
 */
internal fun formatArgs(argsJson: String, max: Int = 80): String {
    if (argsJson.isBlank()) return ""
    val rendered = runCatching {
        val obj = JSONObject(argsJson)
        val keys = obj.keys().asSequence().toList()
        keys.joinToString(", ") { k ->
            val v = obj.opt(k)
            "$k=" + renderJsonValue(v)
        }
    }.getOrElse { argsJson.replace('\n', ' ') }
    return if (rendered.length > max) rendered.take(max - 1) + "\u2026" else rendered
}

private fun renderJsonValue(v: Any?): String = when (v) {
    null, JSONObject.NULL -> "null"
    is String -> "\"" + v.replace("\"", "\\\"").take(40) + "\""
    is Number, is Boolean -> v.toString()
    is JSONArray -> "[…${v.length()}]"
    is JSONObject -> "{…${v.length()}}"
    else -> v.toString().take(40)
}

internal fun prettyJson(argsJson: String): String =
    runCatching { JSONObject(argsJson).toString(2) }.getOrDefault(argsJson)
