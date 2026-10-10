@file:OptIn(ExperimentalSharedTransitionApi::class)

package org.agora.app.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDp
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

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
 * Shared container under [key] (null = no transform). Cards keep their rounded shape; a [page] starts with the
 * card's corners and squares them off while it grows. Content fades through: the old one out early, the new one in.
 */
@Composable
fun Modifier.containerTransform(key: String?, page: Boolean = false): Modifier {
    val shared = LocalSharedTransition.current
    val scope = LocalNavAnimation.current
    if (key == null || shared == null || scope == null) return this
    val corner = if (page) {
        val animated by scope.transition.animateDp({ tween(DURATION, easing = emphasized) }, label = "corner") {
            if (it == EnterExitState.Visible) 0.dp else cardCorner
        }
        animated
    } else cardCorner
    return with(shared) {
        this@containerTransform.sharedBounds(
            rememberSharedContentState("ct-$key"),
            scope,
            enter = fadeIn(tween(DURATION - 150, delayMillis = 120, easing = LinearOutSlowInEasing)),
            exit = fadeOut(tween(150, easing = FastOutLinearInEasing)),
            boundsTransform = bounds,
            resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(ContentScale.FillWidth, Alignment.TopCenter),
            clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(corner))
        )
    }
}
