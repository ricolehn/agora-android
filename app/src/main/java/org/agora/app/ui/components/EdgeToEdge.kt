package org.agora.app.ui.components

import android.app.Activity
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

/*
 * Edge to edge (enforced since Android 15/16): the app draws behind the system bars. Content scrolls up under the
 * status bar, where a soft fade in the page colour keeps clock and icons readable.
 */

/** Pages with a picture right under the status bar register here: no fade there, light icons on the picture. */
object StatusBarOverlay {
    internal var immersive by mutableIntStateOf(0)
}

/** While [enabled] (e.g. the event cover is under the status bar), the status bar shows the page as it is. */
@Composable
fun ImmersiveStatusBar(enabled: Boolean) {
    if (!enabled) return
    DisposableEffect(Unit) {
        StatusBarOverlay.immersive++
        onDispose { StatusBarOverlay.immersive-- }
    }
}

/** Fade behind the status bar, drawn above all content; it takes no touches. */
@Composable
fun StatusBarScrim(modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val inset = WindowInsets.statusBars.getTop(density)
    val visible by animateFloatAsState(if (StatusBarOverlay.immersive > 0) 0f else 1f, tween(200), label = "scrim")
    if (inset == 0 || visible == 0f) return
    val color = MaterialTheme.colorScheme.background.copy(alpha = visible)
    val fade = with(density) { 12.dp.toPx() }
    Spacer(
        modifier
            .fillMaxWidth()
            .height(with(density) { (inset + fade).toDp() })
            .drawBehind {
                val bar = inset / size.height
                drawRect(
                    Brush.verticalGradient(
                        0f to color.copy(alpha = 0.92f * visible),
                        bar to color.copy(alpha = 0.72f * visible),
                        1f to color.copy(alpha = 0f)
                    )
                )
            }
    )
}

/** Dark or light system bar icons by the app's theme (it can differ from the system's). */
@Composable
fun SystemBarAppearance() {
    val view = LocalView.current
    val light = MaterialTheme.colorScheme.background.luminance() > 0.5f
    // Over a cover picture the icons are white (the picture is darkened at the top)
    val immersive = StatusBarOverlay.immersive > 0
    if (view.isInEditMode) return
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = light && !immersive
            isAppearanceLightNavigationBars = light
        }
    }
}

/**
 * Header that scrolls away with the content and comes back on the first scroll up. It only moves by what the
 * content really scrolled (short pages keep it), reappears before the content moves back, and settles fully in or
 * out after a fling. Read [offset] in the draw phase (graphicsLayer) so scrolling causes no relayout.
 */
class CollapsingHeader {
    var offset by mutableFloatStateOf(0f)
        private set
    /** Negative header height: how far it can slide out. */
    var limit = 0f

    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (available.y > 0f && offset < 0f) offset = (offset + available.y).coerceAtMost(0f)
            return Offset.Zero
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (consumed.y < 0f) offset = (offset + consumed.y).coerceIn(limit, 0f)
            return Offset.Zero
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            if (offset < 0f && offset > limit) {
                val target = if (offset < limit / 2) limit else 0f
                animate(offset, target, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { value, _ -> offset = value }
            }
            return Velocity.Zero
        }
    }
}

@Composable
fun rememberCollapsingHeader(): CollapsingHeader = remember { CollapsingHeader() }
