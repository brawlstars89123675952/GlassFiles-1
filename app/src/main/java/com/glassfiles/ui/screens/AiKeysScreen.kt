package com.glassfiles.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.glassfiles.data.Strings
import com.glassfiles.data.ai.AiKeyStore
import com.glassfiles.data.ai.models.AiProviderId
import com.glassfiles.ui.components.AiModuleHairline
import com.glassfiles.ui.components.AiModulePillButton
import com.glassfiles.ui.components.AiModuleScreenScaffold
import com.glassfiles.ui.theme.AiModuleTheme
import com.glassfiles.ui.theme.JetBrainsMono

/**
 * Screen for entering / managing the API key for each [AiProviderId].
 *
 * Layout: a man-page list — each provider is a `> name [status] masked`
 * line. Tapping the row drops down an inline editor (input + reveal
 * toggle + Save / Remove / Get-key link). All seven providers fit
 * above the fold on a typical phone without scrolling.
 */
@Composable
fun AiKeysScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    val keyValues = remember { mutableStateMapOf<AiProviderId, String>() }
    val showKey = remember { mutableStateMapOf<AiProviderId, Boolean>() }
    val expanded = remember { mutableStateMapOf<AiProviderId, Boolean>() }
    val savedFlash = remember { mutableStateMapOf<AiProviderId, Boolean>() }
    var savedRefreshTick by remember { mutableStateOf(0) }

    LaunchedEffect(savedRefreshTick) {
        AiProviderId.entries.forEach { keyValues[it] = AiKeyStore.getKey(context, it) }
    }

    AiModuleScreenScaffold(
        title = Strings.aiKeys,
        onBack = onBack,
        subtitle = "providers · " + AiProviderId.entries.size + " endpoints",
    ) {
        LazyColumn(
            contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            items(AiProviderId.entries.toList(), key = { it.name }) { provider ->
                val value = keyValues[provider].orEmpty()
                ProviderRow(
                    provider = provider,
                    value = value,
                    revealed = showKey[provider] == true,
                    expanded = expanded[provider] == true,
                    saved = savedFlash[provider] == true,
                    onValueChange = {
                        keyValues[provider] = it
                        savedFlash[provider] = false
                    },
                    onToggleReveal = { showKey[provider] = !(showKey[provider] ?: false) },
                    onToggleExpanded = {
                        expanded[provider] = !(expanded[provider] ?: false)
                    },
                    onSave = {
                        AiKeyStore.saveKey(context, provider, value)
                        savedFlash[provider] = true
                        savedRefreshTick++
                    },
                    onClear = {
                        AiKeyStore.saveKey(context, provider, "")
                        keyValues[provider] = ""
                        savedFlash[provider] = false
                    },
                    onOpenConsole = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(provider.consoleUrl))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    },
                )
                AiModuleHairline()
            }
        }
    }
}

@Composable
private fun ProviderRow(
    provider: AiProviderId,
    value: String,
    revealed: Boolean,
    expanded: Boolean,
    saved: Boolean,
    onValueChange: (String) -> Unit,
    onToggleReveal: () -> Unit,
    onToggleExpanded: () -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit,
    onOpenConsole: () -> Unit,
) {
    val colors = AgentTerminal.colors
    val hasKey = value.isNotBlank()

    Column(Modifier.fillMaxWidth()) {
        // Collapsed/header row — always visible.
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { onToggleExpanded() }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Status indicator: filled accent dot when key set, hollow border otherwise.
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (hasKey) colors.accent else colors.background)
                    .border(
                        width = 1.dp,
                        color = if (hasKey) colors.accent else colors.border,
                        shape = CircleShape,
                    ),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                provider.displayName,
                fontSize = 14.sp,
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.Medium,
                color = colors.textPrimary,
                lineHeight = 1.3.em,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (hasKey) maskKey(value) else "—",
                fontSize = 12.sp,
                fontFamily = JetBrainsMono,
                color = colors.textMuted,
                maxLines = 1,
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                null,
                Modifier.size(16.dp),
                tint = colors.textSecondary,
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp)
                    .padding(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Mono-styled input. Caret matches accent green; placeholder muted.
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(colors.surface)
                        .border(1.dp, colors.border, RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "$",
                        color = colors.accentDim,
                        fontFamily = JetBrainsMono,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.width(8.dp))
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = LocalTextStyle.current.merge(
                            TextStyle(
                                color = colors.textPrimary,
                                fontSize = 13.sp,
                                fontFamily = JetBrainsMono,
                            ),
                        ),
                        cursorBrush = SolidColor(colors.accent),
                        visualTransformation =
                            if (revealed) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        decorationBox = { inner ->
                            if (value.isEmpty()) {
                                Text(
                                    Strings.aiKeyHint,
                                    fontSize = 13.sp,
                                    fontFamily = JetBrainsMono,
                                    color = colors.textMuted,
                                )
                            }
                            inner()
                        },
                    )
                    if (value.isNotEmpty()) {
                        IconButton(
                            onClick = onToggleReveal,
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                if (revealed) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                null,
                                Modifier.size(16.dp),
                                tint = colors.textSecondary,
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    GetKeyLink(onClick = onOpenConsole)
                    Spacer(Modifier.weight(1f))
                    if (hasKey) {
                        TerminalPillButton(
                            label = Strings.aiKeyClear.lowercase(),
                            onClick = onClear,
                            destructive = true,
                            accent = false,
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    TerminalPillButton(
                        label = if (saved) "${Strings.aiKeySaved.lowercase()} ✓" else Strings.aiKeySave.lowercase(),
                        onClick = onSave,
                        enabled = value.isNotBlank() && !saved,
                        accent = !saved,
                    )
                }
            }
        }
    }
}

@Composable
private fun GetKeyLink(onClick: () -> Unit) {
    val colors = AgentTerminal.colors
    Row(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.AutoMirrored.Rounded.OpenInNew,
            null,
            Modifier.size(12.dp),
            tint = colors.accent,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            Strings.aiKeyGetHere.lowercase(),
            fontSize = 12.sp,
            fontFamily = JetBrainsMono,
            color = colors.accent,
        )
    }
}

/**
 * Mask a secret key for display: show first 3 and last 4 characters with a
 * fixed-width middle (avoids leaking length). Falls back to bullets only
 * when the value is too short to safely show parts.
 */
private fun maskKey(value: String): String {
    val s = value.trim()
    if (s.length <= 8) return "••••••••"
    val head = s.take(3)
    val tail = s.takeLast(4)
    return "$head••••$tail"
}
