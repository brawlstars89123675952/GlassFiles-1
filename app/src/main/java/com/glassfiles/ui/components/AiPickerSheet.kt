package com.glassfiles.ui.components

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.glassfiles.ui.theme.AiModuleDarkColors
import com.glassfiles.ui.theme.JetBrainsMono

/**
 * Terminal-themed picker rendered as a bottom sheet for the AI module.
 * Replaces the legacy Material picker — uses the shared agent palette
 * (OLED black / salad-green accent) and JetBrains Mono so every dropdown
 * across Hub, Chat, Coding, ImageGen, VideoGen, Models, Settings reads
 * the same as the agent screen.
 *
 * The palette is read from [AgentTerminalDarkColors] directly rather
 * than the [AgentTerminal] CompositionLocal — `ModalBottomSheet` opens
 * in a popup window that does not always propagate parent
 * CompositionLocals reliably across versions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> AiPickerSheet(
    title: String,
    options: List<T>,
    optionLabel: (T) -> String,
    optionSubtitle: (T) -> String? = { null },
    selected: T? = null,
    searchEnabled: Boolean = true,
    onDismiss: () -> Unit,
    onSelect: (T) -> Unit,
) {
    val colors = AgentTerminalDarkColors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf(TextFieldValue("")) }

    val filtered = remember(options, query.text) {
        if (query.text.isBlank()) options
        else options.filter { optionLabel(it).contains(query.text, ignoreCase = true) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surfaceElevated,
        contentColor = colors.textPrimary,
        scrimColor = Color.Black.copy(alpha = 0.6f),
        dragHandle = {
            Spacer(
                Modifier
                    .padding(top = 8.dp, bottom = 4.dp)
                    .height(3.dp)
                    .width(36.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.border),
            )
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 8.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "> ${title.lowercase()}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.accent,
                    fontFamily = JetBrainsMono,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.Close, null, Modifier.size(16.dp), tint = colors.textSecondary)
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))

            if (searchEnabled && options.size > 6) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(colors.surface)
                        .border(1.dp, colors.border, RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Search, null, Modifier.size(14.dp), tint = colors.textMuted)
                    Spacer(Modifier.size(8.dp))
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(
                            color = colors.textPrimary,
                            fontFamily = JetBrainsMono,
                            fontSize = 13.sp,
                        ),
                        cursorBrush = SolidColor(colors.accent),
                        decorationBox = { inner ->
                            if (query.text.isEmpty()) {
                                Text(
                                    "search…",
                                    fontSize = 13.sp,
                                    color = colors.textMuted,
                                    fontFamily = JetBrainsMono,
                                )
                            }
                            inner()
                        },
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                items(filtered) { item ->
                    val isSelected = selected != null && selected == item
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelect(item)
                                onDismiss()
                            }
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (isSelected) "▣" else "▸",
                            color = if (isSelected) colors.accent else colors.textMuted,
                            fontFamily = JetBrainsMono,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            modifier = Modifier.width(20.dp),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                optionLabel(item),
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                                color = if (isSelected) colors.accent else colors.textPrimary,
                                fontFamily = JetBrainsMono,
                                maxLines = 1,
                                lineHeight = 1.3.em,
                            )
                            optionSubtitle(item)?.let { sub ->
                                Text(
                                    sub,
                                    fontSize = 11.sp,
                                    color = colors.textMuted,
                                    fontFamily = JetBrainsMono,
                                    maxLines = 1,
                                    lineHeight = 1.3.em,
                                )
                            }
                        }
                    }
                }
                if (filtered.isEmpty()) {
                    item {
                        Box(
                            Modifier.fillMaxWidth().padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "no matches for «${query.text}»",
                                fontSize = 12.sp,
                                color = colors.textMuted,
                                fontFamily = JetBrainsMono,
                            )
                        }
                    }
                }
            }
        }
    }
}
