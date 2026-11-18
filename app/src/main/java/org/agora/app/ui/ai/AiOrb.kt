package org.agora.app.ui.ai

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * The assistant's sign (web: .ai-orb): two soft forms in the app's colours - lagoon in front, sand behind - whose
 * outlines slowly change. Every point of an outline moves in and out on its own slow path and the forms drift a little
 * against each other, so the sign never turns and never looks the same twice. [thinking]: it moves faster and livelier,
 * with a soft glow; [resting]: it slows down and stops (a finished reply). [seed] starts it at another moment of its
 * motion, so the signs of several replies differ a little. With animations turned off in the system it stays still.
 */
@Composable
fun AiOrb(size: Dp, thinking: Boolean = false, resting: Boolean = false, glow: Boolean = size >= 48.dp, seed: Float = 0f) {
    // Pace and liveliness ease into each other, so the motion never jumps when the AI starts or stops working
    val speed = animateFloatAsState(if (thinking) 2.4f else if (resting) 0f else 1f, tween(1_400, easing = FastOutSlowInEasing), label = "pace")
    val energy = animateFloatAsState(if (thinking) 1f else 0f, tween(900, easing = FastOutSlowInEasing), label = "energy")
    val time = remember { mutableFloatStateOf(seed) }
    LaunchedEffect(Unit) {
        if (coroutineContext[MotionDurationScale]?.scaleFactor == 0f) return@LaunchedEffect
        var last = -1L
        while (true) {
            // At rest: no frames until it moves again
            if (speed.value == 0f) {
                snapshotFlow { speed.value }.first { it > 0f }
                last = -1L
            }
            withInfiniteAnimationFrameNanos { now ->
                if (last >= 0) time.floatValue += (now - last) / 1_000_000_000f * speed.value
                last = now
            }
        }
    }
    AiOrbFrame(size, time = { time.floatValue }, energy = { energy.value }, glow = glow)
}

/** One moment of the sign: [time] in seconds of its motion, [energy] from 0 (calm) to 1 (thinking). */
@Composable
fun AiOrbFrame(size: Dp, time: () -> Float, energy: () -> Float = { 0f }, glow: Boolean = size >= 48.dp, modifier: Modifier = Modifier) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val forms = remember { Pair(Path(), Path()) }
    Spacer(modifier.size(size).drawBehind { drawSign(time(), energy(), dark, glow, forms.first, forms.second) })
}

/**
 * The few colours of the chat around the sign: [accent] for the icons of the AI's work (a lookup's) and [tint] for
 * the squares behind them, [shimmer] the light running through the words while the AI works, [rail] the thin line
 * beside the opened steps.
 */
@Immutable
class AiColors(val accent: Color, val tint: Color, val shimmer: Color, val rail: Color)

private val LightAiColors = AiColors(
    accent = Color(0xFF2D625B), tint = Color(0xFF2D6A62), shimmer = Color(0xFF2D625B), rail = Color(0xFFD2A46E).copy(alpha = 0.6f)
)
private val DarkAiColors = AiColors(
    accent = Color(0xFF8FC2B9), tint = Color(0xFF2D6A62), shimmer = Color(0xFFB5DDD5), rail = Color(0xFFD9BC8F).copy(alpha = 0.5f)
)

@Composable
fun aiColors(): AiColors = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) DarkAiColors else LightAiColors

private fun DrawScope.drawSign(t: Float, e: Float, dark: Boolean, glow: Boolean, back: Path, front: Path) {
    val r = size.minDimension / 2f
    val c = center
    halo(listOf(Color(0xFFE2BE8A).copy(alpha = if (dark) 0.14f else 0.26f), Color(0xFF8CC7BB).copy(alpha = if (dark) 0.05f else 0.1f)),
        (if (glow) 0.25f else 0f) + 0.95f * e, t, e)
    val amp = 1f + e
    // The sand form behind, up and right; the lagoon form in front, down and left - the two overlap in the middle
    val backAt = c + Offset(r * (0.2f + 0.06f * wander(t, 0.46f, 0.4f)), r * (-0.17f + 0.06f * wander(t, 0.36f, 1.7f)))
    val frontAt = c + Offset(r * (-0.17f + 0.06f * wander(t, 0.39f, 2.9f)), r * (0.16f + 0.06f * wander(t, 0.54f, 0.8f)))
    softForm(back, backAt, r * 0.74f, t, 0.09f * amp, seed = 1.3f)
    softForm(front, frontAt, r * 0.76f, t, 0.085f * amp, seed = 4.1f)
    // Each form a gentle fall of its colour, whose direction sways a little
    val sway = 0.35f * wander(t, 0.29f, 1.1f)
    fun across(angle: Float) = Offset(cos(angle), sin(angle)) * r
    val backAngle = -2.4f + sway
    drawPath(back, Brush.linearGradient(
        if (dark) listOf(Color(0xFFE3C99F), Color(0xFFBF9868)) else listOf(Color(0xFFF0DEC2), Color(0xFFDAB98D)),
        start = backAt + across(backAngle), end = backAt - across(backAngle)
    ))
    val frontAngle = -2.0f - sway
    drawPath(front, Brush.linearGradient(
        if (dark) listOf(Color(0xFF86C2B6), Color(0xFF4C8C82)) else listOf(Color(0xFF4FA597), Color(0xFF1F6A62)),
        start = frontAt + across(frontAngle), end = frontAt - across(frontAngle)
    ), alpha = 0.86f)
}

/** A slow wander around 0: two sines of unrelated periods, so it never repeats within the time anyone looks. */
private fun wander(t: Float, speed: Float, phase: Float) = 0.65f * sin(t * speed + phase) + 0.35f * sin(t * speed * 1.618f + phase * 2.3f)

/** A soft light around the sign that breathes; stronger while the AI thinks. */
private fun DrawScope.halo(colors: List<Color>, strength: Float, t: Float, e: Float) {
    if (strength <= 0f) return
    val r = size.minDimension / 2f
    val breathe = 1f + (0.03f + 0.05f * e) * sin(t * (1.1f + 1.4f * e))
    val outer = r * (1.55f + 0.1f * e) * breathe
    val inner = r / outer
    drawCircle(
        Brush.radialGradient(
            0f to colors[0].copy(alpha = 0f),
            inner * 0.86f to colors[0].copy(alpha = colors[0].alpha * strength),
            (inner + 1f) / 2f to colors[1].copy(alpha = colors[1].alpha * strength),
            1f to colors[1].copy(alpha = 0f),
            center = center, radius = outer
        ),
        outer, center
    )
}

/**
 * A closed soft outline through seven points around [at]: the points keep their directions and only move in and out,
 * each on its own slow path - the form changes, it never turns. At a fixed [t] it is still: the quiet forms in the
 * painted fields of the Extras hub (HubArt.kt).
 */
internal fun softForm(path: Path, at: Offset, radius: Float, t: Float, amp: Float, seed: Float) {
    val n = FORM_POINTS
    val points = Array(n) { i ->
        val angle = (2 * PI * i / n - PI / 2).toFloat()
        val reach = radius * (1f + amp * wander(t, 0.42f + 0.11f * ((i * 5 + 3) % n), seed + i * 1.9f))
        at + Offset(cos(angle), sin(angle)) * reach
    }
    path.reset()
    path.moveTo(points[0].x, points[0].y)
    for (i in 0 until n) {
        val p0 = points[(i - 1 + n) % n]
        val p1 = points[i]
        val p2 = points[(i + 1) % n]
        val p3 = points[(i + 2) % n]
        val c1 = p1 + (p2 - p0) * (FORM_HANDLE / 6f)
        val c2 = p2 - (p3 - p1) * (FORM_HANDLE / 6f)
        path.cubicTo(c1.x, c1.y, c2.x, c2.y, p2.x, p2.y)
    }
    path.close()
}

private const val FORM_POINTS = 7
// Catmull-Rom handles stretched so that seven points at rest make a true circle
private val FORM_HANDLE = (4 * tan(PI / (2 * FORM_POINTS)) / sin(2 * PI / FORM_POINTS)).toFloat()
