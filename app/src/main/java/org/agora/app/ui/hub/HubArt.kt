package org.agora.app.ui.hub

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.agora.app.ui.ai.softForm

/**
 * The motif of an app of the hub: a small picture in the manner of an emoji, one thing each - a picture, two notes, a
 * heart -, a little three-dimensional through soft gradients and a little light, without a shadow. Drawn here, so it
 * is sharp at every size and needs no image files. [seed]: where the still forms of its painted field lie.
 */
enum class HubMotif(internal val seed: Float) { Picture(0.7f), Notes(2.6f), Heart(5.1f) }

/** A motif standing alone, [size] square. Decorative: the tile's text says what it is. */
@Composable
fun HubMotifIcon(motif: HubMotif, size: Dp, modifier: Modifier = Modifier) {
    Spacer(modifier.size(size).drawWithCache {
        val draw = when (motif) {
            HubMotif.Picture -> picture()
            HubMotif.Notes -> notes()
            HubMotif.Heart -> heart()
        }
        onDrawBehind(draw)
    })
}

/**
 * An app's [motif] in its painted field, [fieldSize] square: a soft fall of the app's light colour, lighter up left, a
 * breath of a neighbouring hue ([HubApp.hint]) down right - deep and quiet in the dark theme - with two still soft forms
 * in the language of the AI's sign, set into the tile with round corners and a fine light edge.
 */
@Composable
fun HubMotifField(app: HubApp, motif: HubMotif, modifier: Modifier = Modifier, fieldSize: Dp = 76.dp) {
    val surface = MaterialTheme.colorScheme.surface
    val dark = surface.luminance() < 0.5f
    val shape = RoundedCornerShape(fieldSize * 0.2f)
    Box(
        modifier
            .size(fieldSize)
            .clip(shape)
            .drawWithCache {
                val w = size.width
                val h = size.height
                val reach = maxOf(w, h)
                val fall = Brush.linearGradient(
                    if (dark) listOf(lerp(surface, app.deep, 0.38f), lerp(surface, app.deep, 0.16f))
                    else listOf(lerp(app.light, Color.White, 0.50f), lerp(app.light, Color.White, 0.08f)),
                    start = Offset.Zero, end = Offset(w, h)
                )
                val hint = app.hint.copy(alpha = if (dark) 0.16f else 0.60f)
                val hintLight = Brush.radialGradient(listOf(hint, hint.copy(alpha = 0f)), center = Offset(w * 0.95f, h), radius = reach * 0.62f)
                val glow = if (dark) app.light.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.80f)
                val glowLight = Brush.radialGradient(listOf(glow, glow.copy(alpha = 0f)), center = Offset(w * 0.08f, 0f), radius = reach * 0.55f)
                val r = minOf(w, h) * 0.62f
                val upper = Path().also { softForm(it, Offset(w - r * 0.30f, r * 0.10f), r, 9f + motif.seed, 0.11f, 2.2f + motif.seed) }
                val lower = Path().also { softForm(it, Offset(r * 0.15f, h + r * 0.25f), r * 0.8f, 9f + motif.seed, 0.11f, 5.4f + motif.seed) }
                val upperColor = Color.White.copy(alpha = if (dark) 0.035f else 0.30f)
                val lowerColor = (if (dark) app.light else app.deep).copy(alpha = if (dark) 0.06f else 0.07f)
                onDrawBehind {
                    drawRect(fall)
                    drawRect(hintLight)
                    drawRect(glowLight)
                    drawPath(upper, upperColor)
                    drawPath(lower, lowerColor)
                }
            }
            .border(1.dp, Color.White.copy(alpha = if (dark) 0.05f else 0.6f), shape),
        contentAlignment = Alignment.Center
    ) { HubMotifIcon(motif, fieldSize * 0.6f) }
}

// The motifs, each drawn into a square: the shapes are made once per size, the drawing only paints them

private fun CacheDrawScope.at(x: Float, y: Float) = Offset(x * size.width, y * size.height)
private fun Path.start(s: Size, x: Float, y: Float) = moveTo(x * s.width, y * s.height)
private fun Path.curve(s: Size, x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) =
    cubicTo(x1 * s.width, y1 * s.height, x2 * s.width, y2 * s.height, x3 * s.width, y3 * s.height)

/** A soft light on a rounded surface: a small oval of white that fades out, turned by [angle]. */
private fun DrawScope.gloss(center: Offset, rx: Float, ry: Float, angle: Float, alpha: Float) {
    withTransform({ rotate(angle, center) }) {
        drawOval(
            Brush.radialGradient(listOf(Color.White.copy(alpha = alpha), Color.White.copy(alpha = 0f)), center = center, radius = rx),
            topLeft = Offset(center.x - rx, center.y - ry), size = Size(2 * rx, 2 * ry)
        )
    }
}

/** A picture: a light frame, a little turned, around a warm sky with its sun and two hills. */
private fun CacheDrawScope.picture(): DrawScope.() -> Unit {
    val w = size.width
    val h = size.height
    val frame = RoundRect(w * 0.10f, h * 0.17f, w * 0.90f, h * 0.81f, CornerRadius(w * 0.13f))
    val framePath = Path().apply { addRoundRect(frame) }
    val inset = w * 0.075f
    val photo = Path().apply {
        addRoundRect(RoundRect(frame.left + inset, frame.top + inset, frame.right - inset, frame.bottom - inset, CornerRadius(w * 0.07f)))
    }
    val frameFill = Brush.linearGradient(listOf(Color.White, Color(0xFFF3E7DC)), start = at(0.1f, 0.15f), end = at(0.9f, 0.85f))
    val shade = Color(0xFFB98A66)
    val frameShade = Brush.verticalGradient(listOf(shade.copy(alpha = 0f), shade.copy(alpha = 0.25f)), startY = h * 0.55f, endY = h * 0.82f)
    val sky = Brush.verticalGradient(listOf(Color(0xFFFFE3C4), Color(0xFFF8B385)), startY = frame.top, endY = frame.bottom)
    val sun = at(0.66f, 0.38f)
    val sunLight = Color(0xFFFFF6DC)
    val sunGlow = Brush.radialGradient(listOf(sunLight.copy(alpha = 0.9f), sunLight.copy(alpha = 0f)), center = sun, radius = w * 0.17f)
    val backHill = Path().apply { start(size, 0.35f, 0.85f); curve(size, 0.50f, 0.52f, 0.60f, 0.50f, 0.95f, 0.85f); close() }
    val frontHill = Path().apply { start(size, 0f, 0.85f); curve(size, 0.22f, 0.44f, 0.36f, 0.46f, 0.70f, 0.85f); close() }
    val back = Brush.verticalGradient(listOf(Color(0xFFF29A62), Color(0xFFE07B42)), startY = h * 0.5f, endY = h * 0.85f)
    val front = Brush.verticalGradient(listOf(Color(0xFFE5814A), Color(0xFFC25A26)), startY = h * 0.45f, endY = h * 0.85f)
    val light = at(0.24f, 0.24f)
    return {
        withTransform({ rotate(-7f, center) }) {
            drawPath(framePath, frameFill)
            drawPath(framePath, frameShade)
            clipPath(photo) {
                drawRect(sky)
                drawCircle(sunGlow, w * 0.17f, sun)
                drawCircle(Color(0xFFFFF4D2), w * 0.075f, sun)
                drawPath(backHill, back)
                drawPath(frontHill, front)
            }
            gloss(light, w * 0.10f, h * 0.03f, 0f, 0.45f)
        }
    }
}

/** Two eighth notes under one beam, in the blue of the songbook, lighter at the top, a soft light on each head. */
private fun CacheDrawScope.notes(): DrawScope.() -> Unit {
    val w = size.width
    val h = size.height
    val body = Brush.linearGradient(listOf(Color(0xFF92D5F8), Color(0xFF4FA2D8), Color(0xFF2B6DA6)), start = at(0.48f, 0.08f), end = at(0.48f, 0.95f))
    fun stem(left: Float, top: Float, bottom: Float) = Path().apply {
        addRoundRect(RoundRect(left * w, top * h, (left + 0.07f) * w, bottom * h, CornerRadius(w * 0.035f)))
    }
    val stems = listOf(stem(0.368f, 0.25f, 0.76f), stem(0.808f, 0.17f, 0.68f))
    val beam = roundedPolygon(listOf(at(0.368f, 0.205f), at(0.878f, 0.115f), at(0.878f, 0.245f), at(0.368f, 0.335f)), w * 0.035f)
    // The heads lie a little uphill, their right ends at the stems
    val rx = w * 0.165f
    val ry = h * 0.12f
    val heads = listOf(at(0.28f, 0.78f), at(0.72f, 0.70f))
    val deep = Color(0xFF1B4C78)
    val rims = heads.map { Brush.verticalGradient(listOf(deep.copy(alpha = 0f), deep.copy(alpha = 0.28f)), startY = it.y, endY = it.y + ry) }
    return {
        stems.forEach { drawPath(it, body) }
        drawPath(beam, body)
        heads.forEachIndexed { i, c ->
            withTransform({ rotate(-24f, c) }) {
                val topLeft = Offset(c.x - rx, c.y - ry)
                val oval = Size(2 * rx, 2 * ry)
                drawOval(body, topLeft, oval)
                drawOval(rims[i], topLeft, oval)
                gloss(Offset(c.x - rx * 0.35f, c.y - ry * 0.42f), rx * 0.45f, ry * 0.30f, 0f, 0.32f)
            }
        }
    }
}

/** A heart: rose to a deeper red, a little darker towards its point, a soft light up left. */
private fun CacheDrawScope.heart(): DrawScope.() -> Unit {
    val path = Path().apply {
        start(size, 0.50f, 0.86f)
        curve(size, 0.17f, 0.66f, 0.05f, 0.47f, 0.09f, 0.31f)
        curve(size, 0.13f, 0.14f, 0.36f, 0.08f, 0.50f, 0.26f)
        curve(size, 0.64f, 0.08f, 0.87f, 0.14f, 0.91f, 0.31f)
        curve(size, 0.95f, 0.47f, 0.83f, 0.66f, 0.50f, 0.86f)
        close()
    }
    val body = Brush.radialGradient(listOf(Color(0xFFFFA3B5), Color(0xFFEE5F7C), Color(0xFFC93A5A)), center = at(0.34f, 0.30f), radius = size.width * 0.78f)
    val deep = Color(0xFF9E2443)
    val rim = Brush.verticalGradient(listOf(deep.copy(alpha = 0f), deep.copy(alpha = 0.35f)), startY = size.height * 0.5f, endY = size.height * 0.88f)
    val light = at(0.30f, 0.32f)
    return {
        drawPath(path, body)
        drawPath(path, rim)
        gloss(light, size.width * 0.10f, size.height * 0.06f, -38f, 0.32f)
    }
}

/** A closed outline through [points] whose corners are rounded with [radius]. */
private fun roundedPolygon(points: List<Offset>, radius: Float): Path = Path().apply {
    val n = points.size
    for (i in 0 until n) {
        val previous = points[(i - 1 + n) % n]
        val corner = points[i]
        val next = points[(i + 1) % n]
        val into = (corner - previous) / (corner - previous).getDistance()
        val out = (next - corner) / (next - corner).getDistance()
        val from = corner - into * radius
        val to = corner + out * radius
        if (i == 0) moveTo(from.x, from.y) else lineTo(from.x, from.y)
        quadraticTo(corner.x, corner.y, to.x, to.y)
    }
    close()
}
