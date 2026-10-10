package org.agora.app.ui.components

import android.graphics.BlurMaskFilter
import android.os.Build
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Soft drop shadow with a fixed size, like the PWA's `box-shadow: 0 <offsetY> <blur> <color>`.
 *
 * Elevation shadows ([shadow]) follow Android's light source: they get longer the lower an element sits on the
 * screen, so they reached over the next element (and were cut off by it), and they show through translucent
 * elements as a hard-edged dark shape. This shadow always has the same extent and is only drawn outside of the
 * element's own [shape], so it stays in the gap to its neighbours and works for frosted / translucent surfaces.
 *
 * Keep [blur] + [offsetY] within the spacing to the next element.
 */
fun Modifier.softShadow(color: Color, blur: Dp, shape: Shape, offsetY: Dp = 0.dp): Modifier {
    if (blur <= 0.dp || color.alpha == 0f) return this
    // Blur mask filters need hardware support that exists from Android 9 on; older versions keep a small elevation shadow
    if (Build.VERSION.SDK_INT < 28) return shadow(blur / 3, shape, clip = false, ambientColor = color, spotColor = color)
    return drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val path = Path().apply { addOutline(outline) }
        val paint = Paint().apply {
            asFrameworkPaint().apply {
                isAntiAlias = true
                this.color = color.toArgb()
                maskFilter = BlurMaskFilter(blur.toPx() / 2f, BlurMaskFilter.Blur.NORMAL)
            }
        }
        val dy = offsetY.toPx()
        onDrawBehind {
            // Nothing under the element itself: translucent elements must not show their own shadow
            clipPath(path, ClipOp.Difference) {
                translate(top = dy) {
                    drawIntoCanvas { it.drawOutline(outline, paint) }
                }
            }
        }
    }
}
