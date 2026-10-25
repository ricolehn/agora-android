package org.agora.app.ui.components

import android.graphics.BlurMaskFilter
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.FrameRateCategory
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.preferredFrameRate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.agora.app.ui.theme.Agora
import kotlin.math.roundToInt

/*
 * A menu that grows out of the button it belongs to, like the menus of iOS: tapped, the round picture stretches into
 * the menu's glass panel on a soft spring (the width leads, the height follows a moment later, both settle with a
 * slight overshoot), the picture itself rides the panel's top start corner into its place next to the name, and the
 * entries fade in as the panel's lower edge passes them. Closing plays it backwards: the entries go first, then the
 * panel folds up and back into the picture.
 *
 * The button draws its picture into a graphics layer. While the menu is shown the menu draws that layer instead, so
 * the picture that travels is the very same drawing: no second image request, nothing flashes. Everything moves in
 * the draw phase only (no recomposition or layout per frame). The menu is an overlay above header, page and bottom
 * bar (TabChrome) and is only composed while it is shown.
 */

/** Open or closed, and how far the button has turned into the menu (width and height spring on their own). */
@Stable
class MorphMenuState internal constructor(private val scope: CoroutineScope) {
    /** Whether the menu is open (also while it is still growing). */
    var expanded by mutableStateOf(false)
        private set
    internal val width = Animatable(0f, THRESHOLD)
    internal val height = Animatable(0f, THRESHOLD)
    internal val content = Animatable(0f, THRESHOLD)

    /** The button it grows out of: the one tapped last. */
    internal var anchor by mutableStateOf<MorphAnchor?>(null)
        private set

    /** On screen while open and while it folds back into the button. */
    internal val visible by derivedStateOf { expanded || width.value > 0f || height.value > 0f }

    internal fun open(from: MorphAnchor) {
        anchor = from
        expanded = true
        scope.launch { width.animateTo(1f, OpenWidth) }
        scope.launch { height.animateTo(1f, OpenHeight) }
        scope.launch { content.animateTo(1f, OpenContent) }
    }

    /** Folds the menu back into its button. */
    fun close() {
        if (!expanded) return
        expanded = false
        scope.launch { content.animateTo(0f, CloseContent) }
        scope.launch { width.animateTo(0f, CloseWidth) }
        scope.launch { height.animateTo(0f, CloseHeight) }
    }

    /** Gone at once: its button left the screen. */
    internal fun dismissNow() {
        expanded = false
        anchor = null
        scope.launch { width.snapTo(0f) }
        scope.launch { height.snapTo(0f) }
        scope.launch { content.snapTo(0f) }
    }
}

@Composable
fun rememberMorphMenuState(): MorphMenuState {
    val scope = rememberCoroutineScope()
    return remember(scope) { MorphMenuState(scope) }
}

private const val THRESHOLD = 0.001f
// Opening overshoots a little (about 4 % at 0.3 s), so the panel settles like liquid; the width leads, the height
// follows a moment later and softer. All in place after about half a second
private val OpenWidth = spring(dampingRatio = 0.7f, stiffness = 220f, visibilityThreshold = THRESHOLD)
private val OpenHeight = spring(dampingRatio = 0.76f, stiffness = 160f, visibilityThreshold = THRESHOLD)
private val OpenContent = tween<Float>(300, delayMillis = 80, easing = LinearOutSlowInEasing)
// Closing lands without a bounce (a panel smaller than the picture would show): first up, then back into the button
private val CloseHeight = spring(dampingRatio = 1f, stiffness = 500f, visibilityThreshold = THRESHOLD)
private val CloseWidth = spring(dampingRatio = 1f, stiffness = 380f, visibilityThreshold = THRESHOLD)
private val CloseContent = tween<Float>(120, easing = FastOutLinearInEasing)

private const val PRESSED_SCALE = 0.9f
/** Room around the panel for the spring's overshoot (the glass is drawn inside the panel's own bounds). */
private val PanelMargin = 24.dp
/** Concentric with the picture: its radius plus the 8 dp it sits from the panel's edge. */
private val PanelCorner = 28.dp
private val EntryShape = RoundedCornerShape(PanelCorner - 8.dp)
/** The panel hangs this far over the button's top end corner. */
private val AnchorOutset = 4.dp
private val ScreenEdge = 12.dp
private val RevealShift = 6.dp
// Shadow of the panel like the web's `0 8px 28px`
private val ShadowBlur = 28.dp
private val ShadowOffset = 8.dp

/** The button's picture (drawn into [layer]) and where it sits. */
internal class MorphAnchor(val layer: GraphicsLayer) {
    var coordinates: LayoutCoordinates? = null
    /** Pressed, the picture gives way a little: the panel starts as large as the picture looks. */
    var scale: State<Float>? = null
}

/**
 * Button that turns into the [MorphMenu] of [state], with [content] as its picture (the avatar), on a 48 dp touch
 * target. Pressed it gives way a little like an iOS button; while the menu is shown, the menu draws the picture.
 */
@Composable
fun MorphMenuButton(state: MorphMenuState, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val layer = rememberGraphicsLayer()
    val anchor = remember(layer) { MorphAnchor(layer) }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    anchor.scale = animateFloatAsState(if (pressed) PRESSED_SCALE else 1f, spring(dampingRatio = 0.6f, stiffness = 800f), label = "press")
    val handedOver by remember(state, anchor) { derivedStateOf { state.visible && state.anchor === anchor } }
    // Leaving the screen while its menu is shown (another tab opened from a notification): the menu goes with it
    DisposableEffect(state, anchor) { onDispose { if (state.anchor === anchor) state.dismissNow() } }
    Box(
        modifier
            .size(48.dp)
            .clickable(interaction, indication = null, role = Role.Button) { state.open(anchor) },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .onPlaced { anchor.coordinates = it }
                .drawWithContent {
                    layer.record { this@drawWithContent.drawContent() }
                    if (!handedOver) drawLayer(layer)
                }
                .graphicsLayer {
                    val scale = anchor.scale?.value ?: 1f
                    scaleX = scale
                    scaleY = scale
                }
        ) { content() }
    }
}

/**
 * The menu of [state], above the whole screen: the button's picture travels into its top start corner with [title]
 * (bold) and [subtitle] next to it, the [entries] ([MorphMenuItem]) follow under a divider. A tap outside and back
 * close it; [paneTitle] is announced by screen readers when it opens.
 */
@Composable
fun MorphMenu(state: MorphMenuState, title: String, subtitle: String?, paneTitle: String, entries: @Composable ColumnScope.() -> Unit) {
    if (!state.visible) return
    val anchor = state.anchor ?: return
    BackHandler(state.expanded) { state.close() }
    val statusBar = WindowInsets.statusBars.getTop(LocalDensity.current)
    val scrim = Agora.colors.shadow.copy(alpha = if (MaterialTheme.colorScheme.background.luminance() < 0.2f) 0.42f else 0.16f)
    val geometry = remember(state, anchor) { MorphGeometry(state, anchor) }
    val moving = state.width.isRunning || state.height.isRunning
    Layout(
        content = { MorphPanel(geometry, title, subtitle, paneTitle, entries) },
        modifier = Modifier
            .fillMaxSize()
            // High refresh rate while it moves, also when back closes it without a finger on the screen (FrameRate.kt)
            .preferredFrameRate(if (moving) FrameRateCategory.High else FrameRateCategory.Default)
            .drawBehind { drawRect(scrim, alpha = state.width.value.coerceIn(0f, 1f)) }
            // While it folds back, taps already reach the page again
            .then(if (state.expanded) Modifier.pointerInput(state) { detectTapGestures { state.close() } } else Modifier)
    ) { measurables, constraints ->
        val edge = ScreenEdge.roundToPx()
        val margin = PanelMargin.roundToPx()
        val panel = measurables.single().measure(
            Constraints(maxWidth = (constraints.maxWidth - 2 * edge).coerceAtLeast(0) + 2 * margin, maxHeight = constraints.maxHeight + 2 * margin)
        )
        layout(constraints.maxWidth, constraints.maxHeight) {
            val button = anchor.coordinates?.takeIf { it.isAttached }
            val box = coordinates?.let { own -> button?.let { own.localBoundingBoxOf(it, clipBounds = false) } }
            val gap = AnchorOutset.roundToPx()
            val width = panel.width - 2 * margin
            // The panel hangs from the button's top end corner, a little over it, and stays on the screen
            val start = if (layoutDirection == LayoutDirection.Ltr) (box?.right?.roundToInt() ?: (constraints.maxWidth - edge)) + gap - width
                else (box?.left?.roundToInt() ?: edge) - gap
            val left = start.coerceIn(edge, (constraints.maxWidth - edge - width).coerceAtLeast(edge))
            val top = ((box?.top?.roundToInt() ?: statusBar) - gap).coerceAtLeast(statusBar + gap)
            panel.place(left - margin, top - margin)
        }
    }
}

/** Entry of a [MorphMenu]: icon under the picture, bold label in line with the name. Picking it closes the menu. */
@Composable
fun MorphMenuItem(text: String, icon: ImageVector, onClick: () -> Unit, color: Color = Agora.colors.heading, trailingIcon: ImageVector? = null) {
    val menu = LocalMorphMenu.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // Lights up under the finger like an iOS menu row, no ripple
    val highlight by animateColorAsState(
        if (pressed) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f) else Color.Transparent,
        tween(if (pressed) 60 else 250), label = "highlight"
    )
    Row(
        Modifier
            .fillMaxWidth()
            .morphReveal()
            .heightIn(min = 48.dp)
            .clip(EntryShape)
            .drawBehind { drawRect(highlight) }
            .clickable(interaction, indication = null, enabled = menu?.state?.expanded != false, role = Role.Button) {
                menu?.state?.close()
                onClick()
            }
            .padding(start = 8.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, Modifier.size(24.dp), tint = color)
        Text(
            text, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.weight(1f).padding(start = 20.dp)
        )
        if (trailingIcon != null) Icon(trailingIcon, null, Modifier.padding(start = 8.dp).size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private val LocalMorphMenu = staticCompositionLocalOf<MorphGeometry?> { null }

/**
 * Laid out once at its final place and size; the morph only clips it to the panel's current outline, draws the
 * glass, rim and shadow for that outline and the travelling picture on top.
 */
@Composable
private fun MorphPanel(geometry: MorphGeometry, title: String, subtitle: String?, paneTitle: String, entries: @Composable ColumnScope.() -> Unit) {
    val state = geometry.state
    val density = LocalDensity.current
    val picture = geometry.anchor.coordinates?.size?.let { with(density) { DpSize(it.width.toDp(), it.height.toDp()) } } ?: DpSize(40.dp, 40.dp)
    val shadow = remember { PanelShadow() }
    val shadowColor = Agora.colors.shadow.copy(alpha = 0.2f)
    val rim = Agora.colors.hairline
    // Glass, rim and entries go through a layer clipped to the panel's current outline: smooth edges on every version
    val clipped = rememberGraphicsLayer()
    val name = remember { Placed() }
    CompositionLocalProvider(LocalMorphMenu provides geometry) {
        Column(
            Modifier
                .onPlaced { geometry.panel = it }
                .drawWithContent {
                    val frame = geometry.frame(this) ?: return@drawWithContent
                    with(shadow) { draw(frame, shadowColor.copy(alpha = shadowColor.alpha * state.width.value.coerceIn(0f, 1f))) }
                    clipped.clip = true
                    clipped.setRoundRectOutline(frame.rect.topLeft, frame.rect.size, frame.radius)
                    clipped.record { this@drawWithContent.drawContent() }
                    drawLayer(clipped)
                    // The button's picture rides the panel's top start corner into its place next to the name
                    if (!geometry.anchor.layer.isReleased) translate(frame.picture.x, frame.picture.y) { drawLayer(geometry.anchor.layer) }
                }
                // Glass like the bottom bar: the page under it shows through softly (Frost.kt)
                .frosted(tint = frostSurface())
                .drawWithContent {
                    drawContent()
                    val frame = geometry.frame(this) ?: return@drawWithContent
                    val stroke = 1.dp.toPx()
                    val half = stroke / 2
                    drawRoundRect(
                        rim, frame.rect.topLeft + Offset(half, half), Size(frame.rect.width - stroke, frame.rect.height - stroke),
                        CornerRadius(frame.radius - half), style = Stroke(stroke)
                    )
                }
                .padding(PanelMargin)
                // Taps on the panel itself don't close it
                .then(if (state.expanded) Modifier.pointerInput(Unit) { detectTapGestures { } } else Modifier)
                .semantics { this.paneTitle = paneTitle }
                .widthIn(min = 240.dp, max = 300.dp)
                .width(IntrinsicSize.Max)
                .padding(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The picture lands here (drawn by the panel, see above)
                Spacer(Modifier.size(picture).onPlaced { geometry.slot = it })
                Column(
                    Modifier
                        .weight(1f)
                        .padding(start = 12.dp, end = 8.dp)
                        .onPlaced { name.coordinates = it }
                        .graphicsLayer { alpha = state.content.value }
                        // The picture draws the name out behind it while it slides past
                        .drawWithContent {
                            val behind = geometry.behindPicture(name.coordinates, this)
                            if (layoutDirection == LayoutDirection.Ltr) clipRect(left = behind) { this@drawWithContent.drawContent() }
                            else clipRect(right = behind) { this@drawWithContent.drawContent() }
                        }
                ) {
                    Text(title, style = MaterialTheme.typography.titleSmall, color = Agora.colors.heading, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (subtitle != null) Text(
                        subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
            HorizontalDivider(Modifier.padding(top = 8.dp, bottom = 4.dp).morphReveal(), color = MaterialTheme.colorScheme.outlineVariant)
            entries()
        }
    }
}

/** Where the morph is right now, in the panel's coordinates. */
private class MorphFrame(val rect: Rect, val radius: Float, val picture: Offset)

/** Follows the button, the panel and the picture's place in it; [frame] is read in the draw phase. */
private class MorphGeometry(val state: MorphMenuState, val anchor: MorphAnchor) {
    var panel: LayoutCoordinates? = null
    var slot: LayoutCoordinates? = null

    fun frame(density: Density): MorphFrame? {
        // The animation first: a reader asking before everything is placed still follows it from then on
        val w = state.width.value.coerceAtLeast(0f)
        val h = state.height.value.coerceAtLeast(0f)
        val pressed = anchor.scale?.value ?: 1f
        val panel = panel?.takeIf { it.isAttached } ?: return null
        val button = anchor.coordinates?.takeIf { it.isAttached } ?: return null
        val slot = slot?.takeIf { it.isAttached } ?: return null
        val margin = with(density) { PanelMargin.toPx() }
        val from = panel.localBoundingBoxOf(button, clipBounds = false)
        val to = Rect(margin, margin, panel.size.width - margin, panel.size.height - margin)
        val place = panel.localPositionOf(slot, Offset.Zero)
        // Pressed, the picture is a little smaller: the panel starts as large as it looks
        val shrink = (1f - pressed) / 2
        val start = Rect(from.left + from.width * shrink, from.top + from.height * shrink, from.right - from.width * shrink, from.bottom - from.height * shrink)
        val rect = Rect(lerp(start.left, to.left, w), lerp(start.top, to.top, h), lerp(start.right, to.right, w), lerp(start.bottom, to.bottom, h))
        val radius = lerp(start.minDimension / 2, with(density) { PanelCorner.toPx() }, h.coerceAtMost(1f)).coerceAtMost(rect.minDimension / 2)
        return MorphFrame(rect, radius, Offset(lerp(from.left, place.x, w), lerp(from.top, place.y, h)))
    }

    /** Where the travelling picture ends (its trailing edge plus a gap), in the coordinates of [node]. */
    fun behindPicture(node: LayoutCoordinates?, scope: DrawScope): Float {
        val frame = frame(scope) ?: return 0f
        val panel = panel ?: return 0f
        if (node == null || !node.isAttached) return 0f
        val start = panel.localPositionOf(node, Offset.Zero).x
        val width = anchor.coordinates?.size?.width ?: 0
        val gap = with(scope) { 4.dp.toPx() }
        return if (scope.layoutDirection == LayoutDirection.Ltr) frame.picture.x + width + gap - start else frame.picture.x - gap - start
    }

    /** How far an entry is in: once the panel's lower edge has passed it, and only while the content is shown. */
    fun reveal(node: LayoutCoordinates?, density: Density): Float {
        val content = state.content.value
        val panel = panel?.takeIf { it.isAttached } ?: return content
        if (node == null || !node.isAttached) return content
        val frame = frame(density) ?: return content
        val top = panel.localPositionOf(node, Offset.Zero).y
        return content * ((frame.rect.bottom - top) / node.size.height.coerceAtLeast(1)).coerceIn(0f, 1f)
    }
}

private class Placed {
    var coordinates: LayoutCoordinates? = null
}

/** Fades and slides an entry in as the growing panel uncovers it. */
@Composable
private fun Modifier.morphReveal(): Modifier {
    val geometry = LocalMorphMenu.current ?: return this
    val node = remember { Placed() }
    return onPlaced { node.coordinates = it }.graphicsLayer {
        val shown = geometry.reveal(node.coordinates, this)
        alpha = shown
        translationY = (shown - 1f) * RevealShift.toPx()
    }
}

/**
 * Soft shadow of the panel's current outline, like [softShadow]: drawn only outside the outline, so it never shows
 * through the glass. Blur mask filters need Android 9; older versions go without (the rim still sets it off).
 */
private class PanelShadow {
    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private var blur = 0f

    fun DrawScope.draw(frame: MorphFrame, color: Color) {
        if (Build.VERSION.SDK_INT < 28 || color.alpha == 0f) return
        val radius = ShadowBlur.toPx() / 2
        if (blur != radius) {
            paint.maskFilter = BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)
            blur = radius
        }
        paint.color = color.toArgb()
        val dy = ShadowOffset.toPx()
        val r = frame.rect
        path.reset()
        path.addRoundRect(RoundRect(r, CornerRadius(frame.radius)))
        clipPath(path, ClipOp.Difference) {
            drawIntoCanvas { it.nativeCanvas.drawRoundRect(r.left, r.top + dy, r.right, r.bottom + dy, frame.radius, frame.radius, paint) }
        }
    }
}
