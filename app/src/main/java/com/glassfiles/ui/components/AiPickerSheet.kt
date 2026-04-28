package com.glassfiles.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.ExperimentalMaterial3Api

/**
 * Modern Material3 picker that replaces the legacy `DropdownMenu`.
 *
 * Renders as a bottom sheet with:
 *  - Optional search field (filters by [optionLabel] case-insensitively).
 *  - One row per option with a check mark on the currently-selected one.
 *  - Spacing rhythm 8/12dp, rounded 16dp top corners (sheet default).
 *
 * Useful for long lists (e.g. Qwen exposes 100+ models) since the search
 * collapses the list down to a manageable size.
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
    val colors = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf(TextFieldValue("")) }

    val filtered = remember(options, query.text) {
        if (query.text.isBlank()) options
        else options.filter { optionLabel(it).contains(query.text, ignoreCase = true) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
        contentColor = colors.onSurface,
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, null, Modifier.size(18.dp), tint = colors.onSurfaceVariant)
                }
            }

            if (searchEnabled && options.size > 6) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Search, null, Modifier.size(16.dp), tint = colors.onSurfaceVariant)
                    Spacer(Modifier.size(8.dp))
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = LocalTextStyle.current.merge(
                            TextStyle(color = colors.onSurface, fontSize = 14.sp),
                        ),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.primary),
                        decorationBox = { inner ->
                            if (query.text.isEmpty()) {
                                Text(
                                    "Search…",
                                    fontSize = 14.sp,
                                    color = colors.onSurfaceVariant,
                                )
                            }
                            inner()
                        },
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
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
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                            if (isSelected) {
                                Icon(
                                    Icons.Rounded.Check,
                                    null,
                                    Modifier.size(16.dp),
                                    tint = colors.primary,
                                )
                            }
                        }
                        Spacer(Modifier.size(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                optionLabel(item),
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = colors.onSurface,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                            )
                            optionSubtitle(item)?.let { sub ->
                                Text(
                                    sub,
                                    fontSize = 11.sp,
                                    color = colors.onSurfaceVariant,
                                    maxLines = 1,
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
                                "Nothing matches «${query.text}»",
                                fontSize = 12.sp,
                                color = colors.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
