package org.agora.app.ui.components

import android.graphics.RectF
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.agora.app.R
import org.agora.app.util.Images
import kotlin.math.max
import kotlin.math.min

private const val MAX_ZOOM = 5f

/**
 * Pan / zoom state of the crop screen. The image always covers the frame: zoom 1 is the smallest scale that
 * fills it, the offset (image centre relative to the frame centre, in px) is clamped to the frame's edges.
 */
private class CropState(val imageSize: IntSize, val frame: Size) {
    val baseScale = max(frame.width / imageSize.width, frame.height / imageSize.height)
    var zoom by mutableFloatStateOf(1f)
    var offset by mutableStateOf(Offset.Zero)
    val scale get() = baseScale * zoom

    private fun clamp(o: Offset): Offset {
        val maxX = (imageSize.width * scale - frame.width) / 2f
        val maxY = (imageSize.height * scale - frame.height) / 2f
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }

    /** Pinch around [focus] (relative to the frame centre) and move by [pan]. */
    fun transform(focus: Offset, pan: Offset, zoomChange: Float) {
        val newZoom = (zoom * zoomChange).coerceIn(1f, MAX_ZOOM)
        val factor = newZoom / zoom
        zoom = newZoom
        offset = clamp((offset - focus) * factor + focus + pan)
    }

    fun reset() {
        zoom = 1f
        offset = Offset.Zero
    }

    /** Frame position as fractions of the image. */
    fun region(): RectF {
        val w = imageSize.width * scale
        val h = imageSize.height * scale
        val left = (w - frame.width) / 2f - offset.x
        val top = (h - frame.height) / 2f - offset.y
        return RectF(left / w, top / h, (left + frame.width) / w, (top + frame.height) / h)
    }
}

/**
 * Full-screen crop step after picking an image (like the PWA's cropper): drag to move, pinch or double-tap to zoom;
 * [aspect] is width / height of the result, [circle] shows a round mask for profile pictures.
 */
@Composable
fun ImageCropDialog(uri: Uri, aspect: Float, onDismiss: () -> Unit, onCrop: (RectF) -> Unit, circle: Boolean = false) {
    val context = LocalContext.current
    var image by remember(uri) { mutableStateOf<ImageBitmap?>(null) }
    var state by remember(uri) { mutableStateOf<CropState?>(null) }
    LaunchedEffect(uri) {
        val bitmap = Images.preview(context, uri)
        if (bitmap == null) onDismiss() else image = bitmap.asImageBitmap()
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(Color(0xFF0B1120))) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CropRoundButton(Icons.Outlined.Close, stringResource(R.string.cancel), onDismiss)
                Text(
                    stringResource(R.string.crop_title), style = MaterialTheme.typography.titleMedium, color = Color.White,
                    modifier = Modifier.weight(1f).padding(horizontal = 14.dp)
                )
                PrimaryButton(onClick = { state?.let { onCrop(it.region()) } }, enabled = state != null) {
                    ButtonLabel(stringResource(R.string.crop_apply), Icons.Outlined.Check)
                }
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val img = image
                if (img == null) {
                    CircularProgressIndicator(color = Color.White)
                    return@BoxWithConstraints
                }
                val density = LocalDensity.current
                val margin = with(density) { 20.dp.toPx() }
                val viewW = constraints.maxWidth.toFloat()
                val viewH = constraints.maxHeight.toFloat()
                val frameW = min(viewW - 2 * margin, (viewH - 2 * margin) * aspect)
                val frame = Size(frameW, frameW / aspect)
                val crop = remember(img, frame) { CropState(IntSize(img.width, img.height), frame).also { state = it } }
                val center = Offset(viewW / 2f, viewH / 2f)
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(crop) {
                            detectTransformGestures { centroid, pan, zoom, _ -> crop.transform(centroid - center, pan, zoom) }
                        }
                        .pointerInput(crop) {
                            detectTapGestures(onDoubleTap = { tap ->
                                if (crop.zoom > 1.01f) crop.reset() else crop.transform(tap - center, Offset.Zero, 2.5f)
                            })
                        }
                ) {
                    val scale = crop.scale
                    val imgSize = Size(img.width * scale, img.height * scale)
                    val topLeft = center + crop.offset - Offset(imgSize.width / 2f, imgSize.height / 2f)
                    withTransform({ translate(topLeft.x, topLeft.y) }) {
                        drawImage(
                            img,
                            dstSize = IntSize(imgSize.width.toInt(), imgSize.height.toInt()),
                            dstOffset = IntOffset.Zero
                        )
                    }
                    // Dim everything outside the frame
                    val frameRect = Rect(center - Offset(frame.width / 2f, frame.height / 2f), frame)
                    val mask = Path().apply {
                        fillType = PathFillType.EvenOdd
                        addRect(Rect(Offset.Zero, size))
                        if (circle) addOval(frameRect) else addRoundRect(androidx.compose.ui.geometry.RoundRect(frameRect, CornerRadius(12.dp.toPx())))
                    }
                    drawPath(mask, Color.Black.copy(alpha = 0.62f))
                    // Rule-of-thirds guides inside the frame
                    clipRect(frameRect.left, frameRect.top, frameRect.right, frameRect.bottom) {
                        for (i in 1..2) {
                            val x = frameRect.left + frameRect.width * i / 3f
                            val y = frameRect.top + frameRect.height * i / 3f
                            drawLine(Color.White.copy(alpha = 0.35f), Offset(x, frameRect.top), Offset(x, frameRect.bottom), 1.dp.toPx())
                            drawLine(Color.White.copy(alpha = 0.35f), Offset(frameRect.left, y), Offset(frameRect.right, y), 1.dp.toPx())
                        }
                    }
                    val border = Stroke(2.dp.toPx())
                    if (circle) drawOval(Color.White, frameRect.topLeft, frameRect.size, style = border)
                    else drawRoundRect(Color.White, frameRect.topLeft, frameRect.size, CornerRadius(12.dp.toPx()), style = border)
                }
            }
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    stringResource(R.string.crop_hint), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.75f),
                    modifier = Modifier.weight(1f)
                )
                CropRoundButton(Icons.Outlined.RestartAlt, stringResource(R.string.crop_reset)) { state?.reset() }
            }
        }
    }
}

@Composable
private fun CropRoundButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, color = Color.White.copy(alpha = 0.12f), modifier = Modifier.size(42.dp)) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, description, Modifier.size(20.dp), tint = Color.White) }
    }
}
