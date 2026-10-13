package org.agora.app.ui.components

import androidx.compose.ui.draw.clipToBounds
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.luminance
import androidx.compose.material3.MaterialTheme
import org.agora.app.ui.theme.Agora

/**
 * Events without a picture (web: `.home-next-fallback`, `.ct-event-card-fallback-cover`, the band on the detail
 * page): large glass spheres on a soft pastel wash, like Microsoft wallpapers - hair-thin rims, an almost clear
 * inside, a breath of refraction (cool on one side, warm on the other) and a small reflection. They drift a few
 * pixels over 18 s. Sizes are fractions of the width, so the motif looks the same on every card.
 */
@Composable
fun GlassSpheres(modifier: Modifier = Modifier, wash: Color = Agora.colors.brand, content: @Composable BoxScope.() -> Unit = {}) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.2f
    val surface = MaterialTheme.colorScheme.surface
    val surfaceAlt = Agora.colors.surfaceAlt
    val drift by rememberInfiniteTransition(label = "spheres").animateFloat(
        0f, 1f, infiniteRepeatable(tween(18_000, easing = LinearEasing), RepeatMode.Reverse), label = "drift"
    )
    Box(
        modifier.clipToBounds().drawBehind {
            drawWash(wash, surface, surfaceAlt, dark)
            val w = size.width
            translate(left = -0.015f * w * drift, top = 0.01f * w * drift) {
                scale(1f + 0.03f * drift, pivot = Offset(w / 2, size.height / 2)) {
                    SPHERES.forEach { (x, y, r) -> drawSphere(Offset(x * w, y * w), r * w, dark) }
                }
            }
        },
        contentAlignment = Alignment.Center,
        content = content
    )
}

// x, y and radius as fractions of the width: a big one low left, a large one cut off top right, a small one between
private val SPHERES = listOf(Triple(0.12f, 0.50f, 0.40f), Triple(0.92f, 0.02f, 0.30f), Triple(0.66f, 0.42f, 0.10f))

private fun DrawScope.drawWash(wash: Color, surface: Color, surfaceAlt: Color, dark: Boolean) {
    drawRect(Brush.linearGradient(listOf(lerp(wash, surface, if (dark) 0.92f else 0.90f), surfaceAlt), Offset.Zero, Offset(size.width, size.height)))
    drawRect(Brush.radialGradient(listOf(wash.copy(alpha = if (dark) 0.22f else 0.26f), Color.Transparent),
        center = Offset(0f, size.height), radius = size.maxDimension * 0.7f))
    drawRect(Brush.radialGradient(listOf(Color(0xFFA78BFA).copy(alpha = if (dark) 0.12f else 0.18f), Color.Transparent),
        center = Offset(size.width, 0f), radius = size.maxDimension * 0.6f))
    if (!dark) drawRect(Brush.radialGradient(listOf(Color(0xFF7DD3C0).copy(alpha = 0.16f), Color.Transparent),
        center = Offset(size.width * 0.7f, size.height), radius = size.maxDimension * 0.5f))
}

private fun DrawScope.drawSphere(center: Offset, radius: Float, dark: Boolean) {
    val rim = if (dark) 0.32f else 0.75f
    val body = if (dark) 0.05f else 0.10f
    val glint = if (dark) 0.12f else 0.30f
    // almost clear inside
    drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = body), Color.White.copy(alpha = body / 3), Color.Transparent),
        center = center, radius = radius), radius, center)
    // a breath of refraction along the inner edge
    drawCircle(Color(0xFF96C8FF).copy(alpha = if (dark) 0.14f else 0.22f), radius * 0.97f, center + Offset(radius * 0.05f, -radius * 0.04f),
        style = Stroke(width = radius * 0.05f))
    drawCircle(Color(0xFFFFC4E2).copy(alpha = if (dark) 0.10f else 0.16f), radius * 0.97f, center + Offset(-radius * 0.05f, radius * 0.05f),
        style = Stroke(width = radius * 0.04f))
    // hair-thin rim
    drawCircle(Color.White.copy(alpha = rim), radius - 0.7f, center, style = Stroke(width = 1.4f))
    // soft reflection top left
    val glintCenter = center + Offset(-radius * 0.30f, -radius * 0.48f)
    val glintSize = Size(radius * 1.1f, radius * 0.64f)
    scale(1f, glintSize.height / glintSize.width, pivot = glintCenter) {
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = glint), Color.Transparent), center = glintCenter, radius = glintSize.width / 2),
            glintSize.width / 2, glintCenter)
    }
}

private fun lerp(a: Color, b: Color, t: Float) = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = 1f
)
