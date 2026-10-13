package org.agora.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import org.agora.app.ui.theme.Agora

/**
 * Frosted glass like the web app (`--frost: saturate(150%) blur(14px)` over 88 % of the page colour): the content
 * runs on under a bar and shows through softly - text and pictures alike are blurred, never just dimmed.
 *
 * Haze only blurs what it recorded as a source, so every page has the same shape ([FrostScaffold], TabChrome):
 * the scrolling content is the source ([frostSource]), everything floating above it ([frosted]) lies OUTSIDE the
 * source - a frosted element inside its own source would only draw the tint. The source needs an opaque
 * background (the page colour), or text lying straight on the page stays sharp under the glass. On Android 12+ Haze blurs for real;
 * older devices get a slightly denser translucent fill.
 */
val LocalFrost = staticCompositionLocalOf<HazeState?> { null }

/** Marks the content that frosted bars of the same screen blur. */
fun Modifier.frostSource(state: HazeState): Modifier = hazeSource(state)

/** Web `saturate(150%)`: colours behind the glass keep their life instead of turning grey. */
private val Saturate = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1.5f) })

/** Glass in the card colour (web `--frost-surface`): bottom navigation, chat bars, floating buttons. */
@Composable
fun frostSurface(): Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f)

/**
 * Frosted fill: blurs the source content under it, tinted with [tint] (the page or card colour). Without a source
 * on the page it falls back to the solid colour, so a bar never turns see-through without blur.
 */
@Composable
fun Modifier.frosted(state: HazeState? = LocalFrost.current, tint: Color = Agora.colors.frost, alpha: Float = 1f): Modifier {
    if (state == null) return background(tint.copy(alpha = alpha))
    return hazeBlur(
        input = HazeInput.Sources(state),
        style = HazeBlurStyle {
            blurRadius(14.dp)
            noiseFactor(0f)
            alpha(alpha)
            colorEffects(listOf(HazeColorEffect.colorFilter(Saturate), HazeColorEffect.tint(tint)))
            fallbackColorEffect(HazeColorEffect.tint(tint.copy(alpha = (tint.alpha + 0.08f).coerceAtMost(0.97f))))
        }
    )
}

/**
 * Glass behind the status bar: content scrolling up under the clock is blurred like under the header, instead of
 * the plain fade (StatusBarScrim) that let text show through sharply. Hidden while a picture runs up there.
 */
@Composable
fun FrostStatusBar(visible: Boolean = true, state: HazeState? = LocalFrost.current, modifier: Modifier = Modifier) {
    val shown by animateFloatAsState(if (visible) 1f else 0f, tween(200), label = "frostStatusBar")
    if (shown == 0f) return
    Box(modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).frosted(state, alpha = shown))
}

/**
 * Scaffold of a sub page with frosted glass: the content is the source, header, bottom bar and [overlay]
 * (floating buttons) blur it. The status bar gets the same glass ([statusBarFrost]) instead of the global fade.
 */
@Composable
fun FrostScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    statusBarFrost: Boolean = true,
    overlay: @Composable BoxScope.(PaddingValues) -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    val frost = rememberHazeState()
    ClearStatusBar(true)
    CompositionLocalProvider(LocalFrost provides frost) {
        Scaffold(
            modifier,
            topBar = topBar,
            bottomBar = bottomBar,
            snackbarHost = snackbarHost,
            floatingActionButton = floatingActionButton,
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            Box(Modifier.fillMaxSize()) {
                // Page colour inside the source: the blurred copy then covers the sharp original under the glass -
                // with a transparent source, text lying straight on the page showed through sharply
                Box(Modifier.fillMaxSize().frostSource(frost).background(MaterialTheme.colorScheme.background)) { content(padding) }
                overlay(padding)
                FrostStatusBar(statusBarFrost, frost)
            }
        }
    }
}
