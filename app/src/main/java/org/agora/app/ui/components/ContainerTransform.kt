@file:OptIn(ExperimentalSharedTransitionApi::class)

package org.agora.app.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/*
 * Material 3 container transform: the tapped card grows into the page it opens (event details, chats) and
 * shrinks back into it on return. The NavHost sits in a SharedTransitionLayout, every destination provides its
 * animation scope, and card and page share a key that travels with the route (`?ct=`).
 */

val LocalSharedTransition = staticCompositionLocalOf<SharedTransitionScope?> { null }
val LocalNavAnimation = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** The card that was tapped last; the navigation takes its key and hands it to the opened page. */
class ContainerOrigin {
    private var pending: String? = null
    fun mark(key: String) { pending = key }
    fun take(): String? = pending.also { pending = null }
}

val LocalContainerOrigin = staticCompositionLocalOf { ContainerOrigin() }

private val emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private const val DURATION = 450
private val cardCorner = 20.dp
private val bounds = BoundsTransform { _, _ -> tween(DURATION, easing = emphasized) }
private val FadeIn = tween<Float>(DURATION - 150, delayMillis = 120, easing = LinearOutSlowInEasing)
private val FadeOut = tween<Float>(150, easing = FastOutLinearInEasing)
/** More than the softest card shadow reaches (AgoraCard: blur up to 16 dp, 4 dp down). */
private val ShadowRoom = 24.dp
/** No clip in the overlay: a card keeps its own shape (and its shadow). */
private val Unclipped = object : SharedTransitionScope.OverlayClip {
    override fun getClipPath(
        sharedContentState: SharedTransitionScope.SharedContentState, bounds: Rect, layoutDirection: LayoutDirection, density: Density
    ): Path? = null
}

/** Header and bottom bar: one element across tab screens, so they do not fade while switching tabs. */
@Composable
fun Modifier.sharedChrome(key: String): Modifier {
    val shared = LocalSharedTransition.current
    val scope = LocalNavAnimation.current
    if (shared == null || scope == null) return this
    return with(shared) { this@sharedChrome.sharedElement(rememberSharedContentState("chrome-$key"), scope) }
}

/** Click handler of a card that opens its page with the container transform under [key]. */
@Composable
fun openFrom(key: String, open: () -> Unit): () -> Unit {
    val origin = LocalContainerOrigin.current
    return { origin.mark(key); open() }
}

/**
 * Shared container under [key] (null = no transform). Cards keep their rounded shape and their shadow; a [page]
 * starts with the card's corners and squares them off while it grows. Content fades through: the old one out early,
 * the new one in.
 */
@Composable
fun Modifier.containerTransform(key: String?, page: Boolean = false): Modifier {
    val shared = LocalSharedTransition.current
    val scope = LocalNavAnimation.current
    if (key == null || shared == null || scope == null) return this
    val state = with(shared) { rememberSharedContentState("ct-$key") }
    val resize = SharedTransitionScope.ResizeMode.scaleToBounds(ContentScale.FillWidth, Alignment.TopCenter)
    if (page) {
        val corner by scope.transition.animateDp({ tween(DURATION, easing = emphasized) }, label = "corner") {
            if (it == EnterExitState.Visible) 0.dp else cardCorner
        }
        return with(shared) {
            this@containerTransform.sharedBounds(
                state, scope, enter = fadeIn(FadeIn), exit = fadeOut(FadeOut), boundsTransform = bounds, resizeMode = resize,
                clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(corner))
            )
        }
    }
    // A card draws its own rounded shape and a soft shadow outside of it, and keeps it the whole way: not clipped in
    // the overlay, and faded with room for the shadow (see fadeWithShadow). Otherwise the shadow was cut off once the
    // card filled its bounds and vanished for the last frames of the way back, until the card was drawn in place
    val alpha = scope.transition.animateFloat({ if (targetState == EnterExitState.Visible) FadeIn else FadeOut }, label = "fade") {
        if (it == EnterExitState.Visible) 1f else 0f
    }
    return with(shared) {
        this@containerTransform
            .sharedBounds(
                state, scope, enter = EnterTransition.None, exit = ExitTransition.None, boundsTransform = bounds, resizeMode = resize,
                clipInOverlayDuringTransition = Unclipped
            )
            // Only while it turns into its page: a tab coming in does not fade its cards
            .fadeWithShadow { if (state.isMatchFound) alpha.value else 1f }
    }
}

/**
 * Fades a card as a whole without cutting off its shadow. A layer that fades is drawn off screen and cut to its own
 * bounds, which took the card's soft shadow along with it; this layer leaves room for the shadow around the card.
 */
private fun Modifier.fadeWithShadow(alpha: () -> Float): Modifier = drawWithCache {
    val layer = obtainGraphicsLayer()
    val room = ShadowRoom.roundToPx()
    onDrawWithContent {
        val faded = alpha()
        if (faded >= 1f) {
            drawContent()
        } else {
            layer.record(IntSize(size.width.roundToInt() + 2 * room, size.height.roundToInt() + 2 * room)) {
                translate(room.toFloat(), room.toFloat()) { this@onDrawWithContent.drawContent() }
            }
            layer.topLeft = IntOffset(-room, -room)
            layer.alpha = faded
            drawLayer(layer)
        }
    }
}
