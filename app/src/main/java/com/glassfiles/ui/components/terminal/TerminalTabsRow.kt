package com.glassfiles.ui.components.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A horizontally-scrollable row of terminal-styled buttons / chips that
 * never clips the last child by character or wraps a button name onto a
 * second line. Used wherever the screen has 3+ adjacent buttons in a
 * `Row` and the combined intrinsic width can exceed the screen width
 * — file browser action buttons, AI Agent chat actions, Memory Files
 * modal tabs, PR detail bottom bar, build run detail tabs, etc.
 *
 * The bug it replaces:
 *
 *   Row { TerminalButton(...); TerminalButton(...); TerminalButton(...) }
 *
 * `wrapContentWidth` buttons inside a non-scrolling `Row` overflow the
 * available width on narrow screens (320dp+). The last button gets
 * truncated and its label wraps one character per line vertically.
 *
 * The fix:
 *
 *   TerminalTabsRow { TerminalButton(...); TerminalButton(...); TerminalButton(...) }
 *
 * Uses [horizontalScroll] so the row scales gracefully. When the row
 * actually overflows we paint a 24dp horizontal-gradient fade on the
 * trailing edge plus a small `›` chevron to advertise the hidden
 * content. When the row fits — or the user has scrolled to the end
 * — both indicators disappear and the row looks like a plain `Row`.
 *
 * Defaults to MaterialTheme tokens so it can be dropped into any
 * surface in the app. Callers inside the AI Agent / Coding screens
 * (which use the bespoke [com.glassfiles.ui.screens.ai.terminal.AgentTerminalColors]
 * palette) should pass their own [fadeColor] and [chevronColor] so the
 * gradient blends with the terminal-style background instead of the
 * Material surface.
 *
 * @param modifier Outer [Modifier] applied to the wrapping [Box].
 * @param spacing Gap between adjacent children (defaults to 8dp,
 *  matching the spacing the migrated call sites already used).
 * @param fadeColor Solid color the trailing-edge gradient fades INTO.
 *  Should match the parent surface so the transition is invisible.
 *  Defaults to [androidx.compose.material3.ColorScheme.background].
 * @param chevronColor Color used for the `›` overflow indicator.
 *  Defaults to [androidx.compose.material3.ColorScheme.onSurfaceVariant].
 * @param content Row of button-like composables. Receives a
 *  [RowScope] so callers can use `.weight(1f)` etc. when desired.
 */
@Composable
fun TerminalTabsRow(
    modifier: Modifier = Modifier,
    spacing: Dp = 8.dp,
    fadeColor: Color = MaterialTheme.colorScheme.background,
    chevronColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    content: @Composable RowScope.() -> Unit,
) {
    val scrollState = rememberScrollState()
    // canScrollForward only fires after the inner Row has measured at
    // least once, which means on the very first frame (scrollState
    // un-initialised, maxValue == 0) we render without the fade and
    // the next frame paints it in if the row really does overflow.
    // That one-frame flicker is preferable to baking in a fixed
    // padding-end that would shift every short-row layout 24dp left.
    val canScrollForward = scrollState.canScrollForward
    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .horizontalScroll(scrollState)
                .padding(end = if (canScrollForward) 24.dp else 0.dp),
            horizontalArrangement = Arrangement.spacedBy(spacing),
            content = content,
        )
        if (canScrollForward) {
            // Gradient first, chevron on top — that way the chevron
            // text stays crisp instead of getting blended into the
            // fade and going invisible against an exact-match
            // background.
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(24.dp)
                    .fillMaxHeight()
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color.Transparent, fadeColor),
                        ),
                    ),
            )
            Text(
                text = "\u203A",
                color = chevronColor,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 4.dp),
            )
        }
    }
}
