package org.agora.app.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.agora.app.ui.ai.softForm

/**
 * A small picture in the manner of an emoji (the Extras hub, the media library): one thing each, a little
 * three-dimensional through soft gradients and a little light, without a shadow. Drawn here, so it is sharp at every
 * size and needs no image files. [seed]: where the still forms of a [PaintedField] behind it lie.
 */
enum class Motif(val seed: Float) { Picture(0.7f), Notes(2.6f), Heart(5.1f), Folder(1.6f), Document(3.8f), Film(4.4f) }

/** A motif standing alone, [size] square; [accent] colours a document's folded corner. Decorative: text says what it is. */
@Composable
fun MotifIcon(motif: Motif, size: Dp, modifier: Modifier = Modifier, accent: Color = Color(0xFF8A837A)) {
    Spacer(modifier.size(size).drawWithCache {
        val draw = when (motif) {
            Motif.Picture -> picture()
            Motif.Notes -> notes()
            Motif.Heart -> heart()
            Motif.Folder -> folder()
            Motif.Document -> document(accent)
            Motif.Film -> film()
        }
        onDrawBehind(draw)
    })
}

/** The colours of a painted field: its [light] tone, its [deep] one (the dark theme) and a neighbouring [hint]. */
@Immutable
data class FieldColors(val light: Color, val deep: Color, val hint: Color = light)

/**
 * A field painted in [colors]: a soft fall of the light tone, lighter up left, a breath of the [FieldColors.hint] down
 * right - deep and quiet in the dark theme - with two still soft forms in the language of the AI's sign ([seed] places
 * them), cut to [shape] with a fine light edge. What it holds lies in the middle unless placed otherwise.
 */
@Composable
fun PaintedField(colors: FieldColors, seed: Float, shape: Shape, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit = {}) {
    val surface = MaterialTheme.colorScheme.surface
    val dark = surface.luminance() < 0.5f
    Box(
        modifier
            .clip(shape)
            .drawWithCache {
                val w = size.width
                val h = size.height
                val reach = maxOf(w, h)
                val fall = Brush.linearGradient(
                    if (dark) listOf(lerp(surface, colors.deep, 0.38f), lerp(surface, colors.deep, 0.16f))
                    else listOf(lerp(colors.light, Color.White, 0.50f), lerp(colors.light, Color.White, 0.08f)),
                    start = Offset.Zero, end = Offset(w, h)
                )
                val hint = colors.hint.copy(alpha = if (dark) 0.16f else 0.60f)
                val hintLight = Brush.radialGradient(listOf(hint, hint.copy(alpha = 0f)), center = Offset(w * 0.95f, h), radius = reach * 0.62f)
                val glow = if (dark) colors.light.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.80f)
                val glowLight = Brush.radialGradient(listOf(glow, glow.copy(alpha = 0f)), center = Offset(w * 0.08f, 0f), radius = reach * 0.55f)
                val r = minOf(w, h) * 0.62f
                val upper = Path().also { softForm(it, Offset(w - r * 0.30f, r * 0.10f), r, 9f + seed, 0.11f, 2.2f + seed) }
                val lower = Path().also { softForm(it, Offset(r * 0.15f, h + r * 0.25f), r * 0.8f, 9f + seed, 0.11f, 5.4f + seed) }
                val upperColor = Color.White.copy(alpha = if (dark) 0.035f else 0.30f)
                val lowerColor = (if (dark) colors.light else colors.deep).copy(alpha = if (dark) 0.06f else 0.07f)
                onDrawBehind {
                    drawRect(fall)
                    drawRect(hintLight)
                    drawRect(glowLight)
                    drawPath(upper, upperColor)
                    drawPath(lower, lowerColor)
                }
            }
            .border(1.dp, Color.White.copy(alpha = if (dark) 0.05f else 0.6f), shape),
        contentAlignment = Alignment.Center,
        content = content
    )
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

/** A light that falls from the top edge of a part (from [top] down to [bottom]), drawn inside its [path]. */
private fun DrawScope.shine(path: Path, top: Float, bottom: Float, alpha: Float) = clipPath(path) {
    drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = alpha), Color.White.copy(alpha = 0f)), startY = top, endY = bottom))
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

/** A folder: its back with the tab in amber, a sheet of paper peeking out, the lighter front with a light along its top. */
private fun CacheDrawScope.folder(): DrawScope.() -> Unit {
    val w = size.width
    val h = size.height
    val back = roundedPolygon(listOf(at(0.07f, 0.19f), at(0.38f, 0.19f), at(0.47f, 0.285f), at(0.93f, 0.285f), at(0.93f, 0.83f), at(0.07f, 0.83f)), w * 0.07f)
    val backFill = Brush.verticalGradient(listOf(Color(0xFFF0A955), Color(0xFFD7833B)), startY = h * 0.19f, endY = h * 0.83f)
    val paper = Path().apply { addRoundRect(RoundRect(w * 0.15f, h * 0.27f, w * 0.85f, h * 0.70f, CornerRadius(w * 0.04f))) }
    val paperFill = Brush.verticalGradient(listOf(Color.White, Color(0xFFF2EBE3)), startY = h * 0.27f, endY = h * 0.46f)
    val front = roundedPolygon(listOf(at(0.07f, 0.385f), at(0.93f, 0.385f), at(0.93f, 0.83f), at(0.07f, 0.83f)), w * 0.07f)
    val frontFill = Brush.verticalGradient(listOf(Color(0xFFFFD58F), Color(0xFFF3A24D)), startY = h * 0.385f, endY = h * 0.83f)
    val rimColor = Color(0xFFB8642A)
    val rim = Brush.verticalGradient(listOf(rimColor.copy(alpha = 0f), rimColor.copy(alpha = 0.22f)), startY = h * 0.62f, endY = h * 0.83f)
    val tilt = Offset(w * 0.5f, h * 0.5f)
    return {
        drawPath(back, backFill)
        withTransform({ rotate(-4f, tilt) }) { drawPath(paper, paperFill) }
        drawPath(front, frontFill)
        drawPath(front, rim)
        shine(front, h * 0.385f, h * 0.52f, 0.45f)
    }
}

/** A sheet of paper with a folded corner in [accent] and a few lines in its tone. */
private fun CacheDrawScope.document(accent: Color): DrawScope.() -> Unit {
    val w = size.width
    val h = size.height
    val sheet = roundedPolygon(listOf(at(0.20f, 0.10f), at(0.60f, 0.10f), at(0.80f, 0.30f), at(0.80f, 0.90f), at(0.20f, 0.90f)), w * 0.06f)
    val paper = Brush.linearGradient(listOf(Color.White, Color(0xFFEEE8E1)), start = at(0.2f, 0.1f), end = at(0.8f, 0.9f))
    val shadeColor = Color(0xFFB9AC9E)
    val shade = Brush.verticalGradient(listOf(shadeColor.copy(alpha = 0f), shadeColor.copy(alpha = 0.18f)), startY = h * 0.6f, endY = h * 0.9f)
    val fold = roundedPolygon(listOf(at(0.60f, 0.10f), at(0.80f, 0.30f), at(0.60f, 0.30f)), w * 0.025f)
    val foldFill = Brush.linearGradient(listOf(lerp(accent, Color.White, 0.30f), accent), start = at(0.60f, 0.30f), end = at(0.74f, 0.13f))
    val edge = Stroke(w * 0.012f)
    val lineColor = lerp(accent, Color.White, 0.62f)
    val lines = listOf(0.62f to 0.46f, 0.66f to 0.58f, 0.52f to 0.70f).map { (right, y) ->
        Path().apply { addRoundRect(RoundRect(w * 0.30f, h * y, w * right, h * (y + 0.05f), CornerRadius(w * 0.025f))) }
    }
    val title = Path().apply { addRoundRect(RoundRect(w * 0.30f, h * 0.33f, w * 0.50f, h * 0.385f, CornerRadius(w * 0.025f))) }
    return {
        drawPath(sheet, paper)
        drawPath(sheet, shade)
        drawPath(sheet, Color.Black.copy(alpha = 0.07f), style = edge)
        drawPath(title, accent.copy(alpha = 0.85f))
        lines.forEach { drawPath(it, lineColor) }
        drawPath(fold, foldFill)
    }
}

/** A screen in lilac with a rounded play sign in the middle, a light along its top. */
private fun CacheDrawScope.film(): DrawScope.() -> Unit {
    val h = size.height
    val screen = Path().apply { addRoundRect(RoundRect(size.width * 0.10f, h * 0.20f, size.width * 0.90f, h * 0.80f, CornerRadius(size.width * 0.13f))) }
    val fill = Brush.linearGradient(listOf(Color(0xFFA79CF0), Color(0xFF6A5BD0), Color(0xFF4F42B0)), start = at(0.1f, 0.2f), end = at(0.9f, 0.8f))
    val play = roundedPolygon(listOf(at(0.42f, 0.355f), at(0.665f, 0.50f), at(0.42f, 0.645f)), size.width * 0.04f)
    return {
        drawPath(screen, fill)
        shine(screen, h * 0.20f, h * 0.42f, 0.30f)
        drawPath(play, Color.White.copy(alpha = 0.95f))
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
        // Never more than half an edge, so short edges (a tab's slope) stay whole
        val r = minOf(radius, (corner - previous).getDistance() / 2f, (next - corner).getDistance() / 2f)
        val from = corner - into * r
        val to = corner + out * r
        if (i == 0) moveTo(from.x, from.y) else lineTo(from.x, from.y)
        quadraticTo(corner.x, corner.y, to.x, to.y)
    }
    close()
}
