package org.agora.app.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.FrameRateCategory
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.preferredFrameRate
import kotlinx.coroutines.delay

/** How long flings and settle animations keep the high refresh rate after the finger is lifted. */
private const val FLING_HOLD_MS = 1500L

/**
 * Phones with adaptive refresh (e.g. Samsung on Android 15+) keep apps at a low rate unless the app asks for more.
 * Views do that by themselves while scrolling; Compose does not, so the whole app asks for the high rate while a
 * finger is on the screen and briefly after it is lifted, and leaves the choice to the system otherwise (battery).
 * Only observes touches (initial pass, nothing consumed), so children handle their gestures as before.
 */
@Composable
fun Modifier.highFrameRateWhileTouching(): Modifier {
    var touching by remember { mutableStateOf(false) }
    var holding by remember { mutableStateOf(false) }
    var releases by remember { mutableIntStateOf(0) }
    LaunchedEffect(releases) {
        if (releases == 0) return@LaunchedEffect
        holding = true
        delay(FLING_HOLD_MS)
        holding = false
    }
    return this
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                touching = true
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                } while (event.changes.any { it.pressed })
                touching = false
                releases++
            }
        }
        .preferredFrameRate(if (touching || holding) FrameRateCategory.High else FrameRateCategory.Default)
}
