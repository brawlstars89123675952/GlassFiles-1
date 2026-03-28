package com.glassfiles.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.glassfiles.ui.theme.*
import com.glassfiles.data.Strings
import com.glassfiles.ui.liquid.DampedDragAnimation
import com.glassfiles.ui.liquid.InteractiveHighlight
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

data class TabItem(val icon: ImageVector, val label: String, val imageUrl: String? = null)

private val BarHeight = 76.dp
private val CapsuleHeight = 66.dp

// \u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550
// Glass Bottom Tab Bar (blur=2, \u0431\u043e\u043b\u044c\u0448\u0435 lens)
// \u0420\u0435\u043d\u0434\u0435\u0440\u0438\u0442\u0441\u044f \u0421\u041d\u0410\u0420\u0423\u0416\u0418 layerBackdrop \u2192 \u043d\u0430\u0441\u0442\u043e\u044f\u0449\u0435\u0435 \u0441\u0442\u0435\u043a\u043b\u043e
// \u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550

@Composable
fun GlassBottomTabBar(
    backdrop: LayerBackdrop,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    tabs: List<TabItem>,
    modifier: Modifier = Modifier
) {
    val isLightTheme = !isSystemInDarkTheme()
    val accentColor = Blue
    val containerColor = if (isLightTheme) Color(0x22F8F8FA) else Color(0x22000000)
    val tabsBackdrop = rememberLayerBackdrop()

    Box(modifier.fillMaxWidth().padding(bottom = 24.dp), contentAlignment = Alignment.BottomCenter) {
        BoxWithConstraints(Modifier.fillMaxWidth(0.88f), contentAlignment = Alignment.CenterStart) {
            val density = LocalDensity.current
            val tabWidth = with(density) { (constraints.maxWidth.toFloat() - 8f.dp.toPx()) / tabs.size }
            val offsetAnimation = remember { Animatable(0f) }
            val panelOffset by remember(density) {
                derivedStateOf {
                    val fraction = (offsetAnimation.value / constraints.maxWidth).fastCoerceIn(-1f, 1f)
                    with(density) { 4f.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction)) }
                }
            }

            val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
            val animationScope = rememberCoroutineScope()
            var currentIndex by remember(selectedTab) { mutableIntStateOf(selectedTab) }

            val dampedDragAnimation = remember(animationScope) {
                DampedDragAnimation(
                    animationScope = animationScope,
                    initialValue = selectedTab.toFloat(),
                    valueRange = 0f..(tabs.size - 1).toFloat(),
                    visibilityThreshold = 0.001f,
                    initialScale = 1f, pressedScale = 78f / 56f,
                    onDragStarted = {},
                    onDragStopped = {
                        val target = targetValue.fastRoundToInt().fastCoerceIn(0, tabs.size - 1)
                        currentIndex = target
                        animateToValue(target.toFloat())
                        animationScope.launch { offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f)) }
                    },
                    onDrag = { _, dragAmount ->
                        updateValue((targetValue + dragAmount.x / tabWidth * if (isLtr) 1f else -1f).fastCoerceIn(0f, (tabs.size - 1).toFloat()))
                        animationScope.launch { offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x) }
                    }
                )
            }

            LaunchedEffect(selectedTab) { snapshotFlow { selectedTab }.collectLatest { currentIndex = it } }
            LaunchedEffect(dampedDragAnimation) {
                snapshotFlow { currentIndex }.drop(1).collectLatest {
                    dampedDragAnimation.animateToValue(it.toFloat())
                    onTabSelected(it)
                }
            }

            val interactiveHighlight = remember(animationScope) {
                InteractiveHighlight(animationScope) { size, _ ->
                    Offset(
                        if (isLtr) (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset
                        else size.width - (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset,
                        size.height / 2f
                    )
                }
            }

            // Layer 1: Glass bar \u2014 blur=2, lens \u0443\u0441\u0438\u043b\u0435\u043d
            Row(
                Modifier.graphicsLayer { translationX = panelOffset }
                    .drawBackdrop(backdrop = backdrop, shape = { Capsule() },
                        effects = { vibrancy(); blur(2f.dp.toPx()); lens(32f.dp.toPx(), 48f.dp.toPx(), chromaticAberration = true) },
                        highlight = { Highlight.Ambient },
                        shadow = { Shadow(radius = 10.dp, color = Color.Black.copy(alpha = 0.25f)) },
                        innerShadow = { InnerShadow(radius = 6.dp, alpha = 0.35f) },
                        layerBlock = { val p = dampedDragAnimation.pressProgress; val s = lerp(1f, 1f + 16f.dp.toPx() / size.width, p); scaleX = s; scaleY = s },
                        onDrawSurface = { drawRect(containerColor); drawRect(Color.White.copy(alpha = 0.20f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())) }
                    ).then(interactiveHighlight.modifier).height(BarHeight).fillMaxWidth().padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) { tabs.forEachIndexed { i, tab -> LiquidBottomTab { NavItemContent(tab, i == selectedTab) } } }

            // Layer 2: Hidden accent
            Row(
                Modifier.clearAndSetSemantics {}.alpha(0f).layerBackdrop(tabsBackdrop)
                    .graphicsLayer { translationX = panelOffset }
                    .drawBackdrop(backdrop = backdrop, shape = { Capsule() },
                        effects = { val p = dampedDragAnimation.pressProgress; vibrancy(); blur(2f.dp.toPx()); lens(32f.dp.toPx() * p, 32f.dp.toPx() * p) },
                        highlight = { Highlight.Default.copy(alpha = dampedDragAnimation.pressProgress) },
                        onDrawSurface = { drawRect(containerColor) }
                    ).then(interactiveHighlight.modifier).height(CapsuleHeight).fillMaxWidth().padding(horizontal = 4.dp)
                    .graphicsLayer(colorFilter = ColorFilter.tint(accentColor)),
                verticalAlignment = Alignment.CenterVertically
            ) { tabs.forEachIndexed { i, tab -> LiquidBottomTab { NavItemContent(tab, i == selectedTab) } } }

            // Tap detection
            Box(Modifier.height(BarHeight).fillMaxWidth().pointerInput(tabs.size) {
                detectTapGestures { offset -> currentIndex = ((offset.x) / (size.width.toFloat() / tabs.size)).toInt().fastCoerceIn(0, tabs.size - 1) }
            })

            // Layer 3: Capsule \u2014 lens \u0443\u0441\u0438\u043b\u0435\u043d
            Box(
                Modifier.padding(horizontal = 4.dp)
                    .graphicsLayer {
                        translationX = if (isLtr) dampedDragAnimation.value * tabWidth + panelOffset
                        else size.width - (dampedDragAnimation.value + 1f) * tabWidth + panelOffset
                    }.then(interactiveHighlight.gestureModifier).then(dampedDragAnimation.modifier)
                    .drawBackdrop(backdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop), shape = { Capsule() },
                        effects = { val p = dampedDragAnimation.pressProgress; lens(14f.dp.toPx() * p, 18f.dp.toPx() * p, chromaticAberration = true) },
                        highlight = { Highlight.Default.copy(alpha = dampedDragAnimation.pressProgress) },
                        shadow = { Shadow(alpha = dampedDragAnimation.pressProgress) },
                        innerShadow = { val p = dampedDragAnimation.pressProgress; InnerShadow(radius = 8f.dp * p, alpha = p) },
                        layerBlock = {
                            scaleX = dampedDragAnimation.scaleX; scaleY = dampedDragAnimation.scaleY
                            val v = dampedDragAnimation.velocity / 10f
                            scaleX /= 1f - (v * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                            scaleY *= 1f - (v * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                        },
                        onDrawSurface = { val p = dampedDragAnimation.pressProgress
                            drawRect(if (isLightTheme) Color.Black.copy(0.1f) else Color.White.copy(0.1f), alpha = 1f - p)
                            drawRect(Color.Black.copy(alpha = 0.03f * p)) }
                    ).height(CapsuleHeight).fillMaxWidth(1f / tabs.size)
            )
        }
    }
}

@Composable
private fun RowScope.LiquidBottomTab(content: @Composable () -> Unit) {
    Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { content() }
}

@Composable
private fun NavItemContent(item: TabItem, isSelected: Boolean) {
    val color by animateColorAsState(if (isSelected) Blue else Color(0xFF999999), tween(200), label = "nc")
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        if (item.imageUrl != null) {
            coil.compose.AsyncImage(
                item.imageUrl, item.label,
                modifier = Modifier.size(26.dp)
                    .clip(CircleShape)
                    .border(if (isSelected) 1.5.dp else 0.dp, if (isSelected) Blue else Color.Transparent, CircleShape)
            )
        } else {
            Icon(item.icon, null, Modifier.size(26.dp), tint = color)
        }
        Spacer(Modifier.height(2.dp))
        Text(item.label, color = color, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal)
    }
}

// \u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550
// Glass FAB \u2014 \u043d\u0430\u0441\u0442\u043e\u044f\u0449\u0435\u0435 \u0441\u0442\u0435\u043a\u043b\u043e (\u0441\u043d\u0430\u0440\u0443\u0436\u0438 layerBackdrop)
// \u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550

@Composable
fun GlassFab(
    backdrop: LayerBackdrop?,
    icon: ImageVector,
    iconTint: Color = Color.White,
    tintColor: Color = Color(0x44000000),
    size: Dp = 48.dp,
    modifier: Modifier = Modifier
) {
    if (backdrop != null) {
        Box(
            modifier.size(size)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { CircleShape },
                    effects = { vibrancy(); blur(6f.dp.toPx()); lens(8f.dp.toPx(), 12f.dp.toPx(), chromaticAberration = true) },
                    highlight = { Highlight.Ambient },
                    shadow = { Shadow(radius = 8.dp, color = Color.Black.copy(alpha = 0.18f)) },
                    innerShadow = { InnerShadow(radius = 4.dp, alpha = 0.25f) },
                    onDrawSurface = {
                        drawRect(tintColor)
                        drawRect(Color.White.copy(alpha = 0.15f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 0.5.dp.toPx()))
                    }
                ),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, Modifier.size(24.dp), tint = iconTint) }
    } else {
        Box(modifier.size(size).background(tintColor, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(24.dp), tint = iconTint)
        }
    }
}

// \u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550
// \u0421\u0442\u0435\u043a\u043b\u044f\u043d\u043d\u044b\u0435 \u043a\u043e\u043c\u043f\u043e\u043d\u0435\u043d\u0442\u044b \u0411\u0415\u0417 drawBackdrop
// \u0414\u043b\u044f \u044d\u043b\u0435\u043c\u0435\u043d\u0442\u043e\u0432 \u0412\u041d\u0423\u0422\u0420\u0418 layerBackdrop \u2014 \u0438\u0441\u043f\u043e\u043b\u044c\u0437\u0443\u044e\u0442
// \u043f\u043e\u043b\u0443\u043f\u0440\u043e\u0437\u0440\u0430\u0447\u043d\u043e\u0441\u0442\u044c + \u0433\u0440\u0430\u0434\u0438\u0435\u043d\u0442\u044b + border \u0434\u043b\u044f glass-\u0432\u0438\u0434\u0430
// \u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550

private val GlassBg = Color(0xBBFFFFFF)
private val GlassBorder = Color(0x33FFFFFF)
private val GlassHighBorder = Color(0x55FFFFFF)

@Composable
fun GlassSearchBar(query: String, onQueryChange: (String) -> Unit, placeholder: String = Strings.search, modifier: Modifier = Modifier) {
    val bg = if (ThemeState.isDark) Color(0xFF2C2C2E) else Color(0xFFE9E9EB)
    Box(
        modifier.fillMaxWidth().height(36.dp)
            .background(bg, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Rounded.Search, null, Modifier.size(18.dp), tint = TextSecondary)
            Text(if (query.isEmpty()) placeholder else query, style = MaterialTheme.typography.bodyMedium, color = if (query.isEmpty()) TextSecondary else TextPrimary)
        }
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val bg = if (ThemeState.isDark) Color(0xFF2C2C2E) else Color(0xFFFFFFFF)
    Column(
        modifier.fillMaxWidth()
            .background(bg, RoundedCornerShape(cornerRadius))
    ) { content() }
}

@Composable
fun GlassTopBar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val bg = if (ThemeState.isDark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)
    Row(
        modifier.fillMaxWidth()
            .background(bg)
            .padding(top = 52.dp, start = 4.dp, end = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
fun GlassToolbar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val bg = if (ThemeState.isDark) Color(0xFF2C2C2E) else Color(0xFFF2F2F7)
    Row(
        modifier.fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .background(bg, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        content = content
    )
}


@Composable
fun IosStyleDivider(startPadding: Int = 72) {
    Box(Modifier.fillMaxWidth().padding(start = startPadding.dp).height(0.5.dp).background(SeparatorColor))
}