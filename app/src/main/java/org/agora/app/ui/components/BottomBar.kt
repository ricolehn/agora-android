package org.agora.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.agora.app.ui.theme.Agora
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * Bottom navigation whose pill moves like a drop of liquid, the iOS way: picked, the pill springs over to the new tab
 * and draws out behind itself the faster it goes, so it leaves stretched, arrives round and wobbles a little as it
 * settles. Icons take the selected colour while the pill passes under them, the labels of the old and the new tab
 * crossfade with it. The tapped icon gives way under the finger and pops when it is selected; a badge pops when
 * something new arrives.
 *
 * Every tab screen has its own bar (TabChrome; between tabs they are one shared element), so the selection lives
 * above them in [BottomBarState]: the bar of the new tab carries on with the slide the old one started. Everything
 * moves in the draw phase only.
 */

data class BottomNavItem(val label: String, val icon: ImageVector, val selectedIcon: ImageVector, val badge: Int = 0)

/** The selected tab and the pill on its way there; kept above the tab screens (see above). */
@Stable
class BottomBarState internal constructor(initial: Int, private val scope: CoroutineScope) {
    var selected by mutableIntStateOf(initial)
        private set
    /** Where the pill is, in tabs (fractions on the way between two). */
    internal val position = Animatable(initial.toFloat(), THRESHOLD)
    /** The tab the pill came from: its label fades out as the new one fades in. */
    private var from by mutableIntStateOf(initial)
    /** Where the current slide began: the pill never stretches back over it. */
    private var origin = initial.toFloat()
    /** The icon that gives way under the finger and pops once selected. */
    internal var popped by mutableIntStateOf(-1)
        private set
    internal val pop = Animatable(1f)
    private var pressing = false

    internal fun press(index: Int) {
        popped = index
        pressing = true
        scope.launch { pop.animateTo(PRESSED_SCALE, PressSpring) }
    }

    /** The finger left without picking it (it slid off): the icon comes back. */
    internal fun release(index: Int) {
        if (!pressing || popped != index) return
        pressing = false
        scope.launch { pop.animateTo(1f, ReleaseSpring) }
    }

    /** Picked in the bar; picked again, the icon only pops. */
    internal fun tap(index: Int) {
        popped = index
        pressing = false
        scope.launch {
            pop.animateTo(POPPED_SCALE, PopUp)
            pop.animateTo(1f, PopSettle)
        }
        slideTo(index)
    }

    /** Follows a tab change that did not come from the bar (back to the start tab, a notification). */
    fun sync(index: Int, animate: Boolean) {
        if (animate) {
            slideTo(index)
            return
        }
        if (index == selected) return
        selected = index
        from = index
        origin = index.toFloat()
        scope.launch { position.snapTo(index.toFloat()) }
    }

    private fun slideTo(index: Int) {
        if (index == selected) return
        // Picked again on the way: of the two labels, the one that still shows more fades out
        if (abs(position.value - selected) < abs(position.value - from)) from = selected
        selected = index
        origin = position.value
        scope.launch { position.animateTo(index.toFloat(), Slide) }
    }

    /**
     * The pill's ends, in tabs: drawn out by its speed like a drop, mostly behind it, but never back over where it
     * started (pulled away, the front leads and the back lets go). One spring and its speed, so the shape never depends
     * on how two animations happen to line up.
     */
    internal fun span(): ClosedFloatingPointRange<Float> {
        val at = position.value
        val speed = position.velocity
        val stretch = MAX_STRETCH * (1f - exp(-abs(speed) / STRETCH_SPEED))
        val behind = min(stretch * TAIL, abs(at - origin))
        return if (speed >= 0f) (at - behind)..(at + stretch - behind) else (at - stretch + behind)..(at + behind)
    }

    /** How far tab [index] lies under the pill (1 = fully): its icon takes the selected colour. */
    internal fun under(index: Int): Float {
        val span = span()
        val distance = maxOf(span.start - index, index - span.endInclusive, 0f)
        return (1f - distance).coerceIn(0f, 1f)
    }

    /** How much tab [index]'s label shows as selected: the old and the new tab's crossfade as the pill travels. */
    internal fun label(index: Int): Float =
        if (index == selected || index == from) (1f - abs(position.value - index)).coerceIn(0f, 1f) else 0f

    internal fun scale(index: Int): Float = if (index == popped) pop.value else 1f
}

@Composable
fun rememberBottomBarState(initial: Int): BottomBarState {
    val scope = rememberCoroutineScope()
    return remember(scope) { BottomBarState(initial, scope) }
}

private const val THRESHOLD = 0.001f
// There in about a third of a second, settling from a small overshoot (3 % of the way)
private val Slide = spring(dampingRatio = 0.74f, stiffness = 380f, visibilityThreshold = THRESHOLD)
/** Longest stretch, in tabs, approached at high speed (tabs per second around [STRETCH_SPEED] and up). */
private const val MAX_STRETCH = 1.1f
private const val STRETCH_SPEED = 16f
/** Share of the stretch behind the pill. */
private const val TAIL = 0.7f
private const val PRESSED_SCALE = 0.86f
private const val POPPED_SCALE = 1.14f
private val PressSpring = spring(dampingRatio = 1f, stiffness = 1200f, visibilityThreshold = THRESHOLD)
private val ReleaseSpring = spring(dampingRatio = 0.6f, stiffness = 600f, visibilityThreshold = THRESHOLD)
private val PopUp = tween<Float>(110, easing = FastOutSlowInEasing)
private val PopSettle = spring(dampingRatio = 0.45f, stiffness = 500f, visibilityThreshold = THRESHOLD)

// Sizes of Material's navigation bar, so the bar looks as before
private val BarHeight = 80.dp
private val PillWidth = 56.dp
private val PillHeight = 32.dp
private val IconSize = 24.dp
private val LabelGap = 4.dp

/**
 * Bottom navigation on frosted glass: the selected tab in a tinted pill behind its icon, like the web app, and the
 * pill slides to the tab you pick. Badges for unread chats and open duty requests.
 */
@Composable
fun AgoraBottomBar(items: List<BottomNavItem>, state: BottomBarState, onSelect: (Int) -> Unit) {
    val geometry = remember(items.size) { BarGeometry(items.size) }
    val pill = Agora.colors.navPill
    Column(Modifier.frosted(tint = frostSurface())) {
        HorizontalDivider(color = Agora.colors.hairline)
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(NavigationBarDefaults.windowInsets)
                .defaultMinSize(minHeight = BarHeight)
                .selectableGroup()
                .onPlaced { geometry.bar = it }
                .drawBehind { geometry.pill(state)?.let { drawRoundRect(pill, it.topLeft, it.size, CornerRadius(it.height / 2)) } },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { i, item -> TabItem(i, item, state, geometry) { onSelect(i) } }
        }
    }
}

@Composable
private fun RowScope.TabItem(index: Int, item: BottomNavItem, state: BottomBarState, geometry: BarGeometry, onSelect: () -> Unit) {
    val selected = index == state.selected
    val interaction = remember { MutableInteractionSource() }
    LaunchedEffect(interaction) {
        interaction.interactions.collect {
            when (it) {
                is PressInteraction.Press -> state.press(index)
                is PressInteraction.Release, is PressInteraction.Cancel -> state.release(index)
            }
        }
    }
    val inactive = Agora.colors.navInactive
    val onPill = Agora.colors.navPillContent
    val heading = Agora.colors.heading
    val painter = rememberVectorPainter(if (selected) item.selectedIcon else item.icon)
    Layout(
        content = {
            // The pill's place (it is drawn by the bar, so it can slide)
            Box(Modifier.size(PillWidth, PillHeight).onPlaced { geometry.slots[index] = it }, contentAlignment = Alignment.Center) {
                BadgedBox(badge = { TabBadge(item.badge) }) {
                    Box(
                        Modifier
                            .size(IconSize)
                            .graphicsLayer {
                                scaleX = state.scale(index)
                                scaleY = scaleX
                            }
                            .drawBehind { with(painter) { draw(size, colorFilter = ColorFilter.tint(lerp(inactive, onPill, state.under(index)))) } }
                    )
                }
            }
            BasicText(
                item.label,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
                color = { lerp(inactive, heading, state.label(index)) },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        modifier = Modifier
            .selectable(selected, interaction, indication = null, role = Role.Tab) {
                state.tap(index)
                onSelect()
            }
            .defaultMinSize(minHeight = BarHeight)
            .weight(1f)
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val slot = measurables[0].measure(loose)
        val label = measurables[1].measure(loose)
        // Like Material's item: icon and label centred as a block, the icon 4 dp inside the pill, the label 4 dp under it
        val inset = (slot.height - IconSize.roundToPx()) / 2
        val block = IconSize.roundToPx() + inset + LabelGap.roundToPx() + label.height
        val iconTop = ((constraints.minHeight - block) / 2).coerceAtLeast(inset)
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else slot.width
        layout(width, block + 2 * iconTop) {
            slot.place((width - slot.width) / 2, iconTop - inset)
            label.place((width - label.width) / 2, iconTop + IconSize.roundToPx() + inset + LabelGap.roundToPx())
        }
    }
}

/** Badge in the danger colour; it pops when the count goes up while the bar is shown (not for the count a tab opens with). */
@Composable
private fun TabBadge(count: Int) {
    val pop = remember { Animatable(1f) }
    var shown by remember { mutableIntStateOf(count) }
    LaunchedEffect(count) {
        if (count > shown) {
            pop.snapTo(0.4f)
            pop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 700f, visibilityThreshold = THRESHOLD))
        }
        shown = count
    }
    if (count > 0) Badge(
        Modifier.graphicsLayer {
            scaleX = pop.value
            scaleY = scaleX
        },
        containerColor = Agora.colors.danger,
        contentColor = Color.White
    ) { Text("$count") }
}

/** Where the bar and each tab's pill place are; [pill] is read in the draw phase. */
private class BarGeometry(count: Int) {
    var bar: LayoutCoordinates? = null
    val slots = arrayOfNulls<LayoutCoordinates>(count)

    /** The pill between its two ends, in the bar's coordinates: stretched, it gets a little thinner like a drop. */
    fun pill(state: BottomBarState): Rect? {
        val span = state.span()
        val bar = bar?.takeIf { it.isAttached } ?: return null
        val a = at(bar, span.start) ?: return null
        val b = at(bar, span.endInclusive) ?: return null
        val squeeze = a.height * 0.08f * (span.endInclusive - span.start)
        return Rect(min(a.left, b.left), min(a.top, b.top) + squeeze, max(a.right, b.right), max(a.bottom, b.bottom) - squeeze)
    }

    /** The pill's place at [position] tabs, between two tabs' places (and a little beyond the last ones on a bounce). */
    private fun at(bar: LayoutCoordinates, position: Float): Rect? {
        if (slots.size == 1) return slot(bar, 0)
        val i = floor(position).toInt().coerceIn(0, slots.size - 2)
        val a = slot(bar, i) ?: return null
        val b = slot(bar, i + 1) ?: return null
        return lerp(a, b, position - i)
    }

    private fun slot(bar: LayoutCoordinates, index: Int): Rect? =
        slots[index]?.takeIf { it.isAttached }?.let { bar.localBoundingBoxOf(it, clipBounds = false) }
}
