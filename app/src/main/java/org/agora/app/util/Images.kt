package org.agora.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** Image preparation matching the PWA (centre crop, fixed output size, JPEG). */
object Images {

    // ImageDecoder (EXIF rotation, HEIF) with BitmapFactory as fallback for formats or environments it rejects
    private fun decode(context: Context, uri: Uri, maxSide: Int): Bitmap? =
        (if (Build.VERSION.SDK_INT >= 28) runCatching { decodeModern(context, uri, maxSide) }.getOrNull() else null)
            ?: decodeLegacy(context, uri, maxSide)

    @androidx.annotation.RequiresApi(28)
    private fun decodeModern(context: Context, uri: Uri, maxSide: Int): Bitmap =
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            val longest = maxOf(info.size.width, info.size.height)
            if (longest > maxSide * 2) {
                val scale = (maxSide * 2).toFloat() / longest
                decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }

    private fun decodeLegacy(context: Context, uri: Uri, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide * 2) sample *= 2
        return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
    }

    /** Downscaled bitmap for the crop screen (longest side about [maxSide]). */
    suspend fun preview(context: Context, uri: Uri, maxSide: Int = 2048): Bitmap? = withContext(Dispatchers.IO) {
        runCatching { decode(context, uri, maxSide / 2) }.getOrNull()
    }

    /**
     * Crops to [width]x[height] and encodes as JPEG. [region] is the part the user picked on the crop screen,
     * as fractions (0..1) of the image; without it the image is centre-cropped.
     */
    suspend fun cropJpeg(context: Context, uri: Uri, width: Int, height: Int, quality: Int, region: RectF? = null): ByteArray? = withContext(Dispatchers.Default) {
        runCatching {
            val source = decode(context, uri, maxOf(width, height)) ?: return@runCatching null
            val targetRatio = width.toFloat() / height
            val sourceRatio = source.width.toFloat() / source.height
            val crop = if (region != null) {
                Rect(
                    (region.left * source.width).toInt().coerceIn(0, source.width - 1),
                    (region.top * source.height).toInt().coerceIn(0, source.height - 1),
                    (region.right * source.width).toInt().coerceIn(1, source.width),
                    (region.bottom * source.height).toInt().coerceIn(1, source.height)
                )
            } else if (sourceRatio > targetRatio) {
                val w = (source.height * targetRatio).toInt()
                Rect((source.width - w) / 2, 0, (source.width + w) / 2, source.height)
            } else {
                val h = (source.width / targetRatio).toInt()
                Rect(0, (source.height - h) / 2, source.width, (source.height + h) / 2)
            }
            val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            android.graphics.Canvas(output).apply {
                drawColor(android.graphics.Color.WHITE)
                drawBitmap(source, crop, RectF(0f, 0f, width.toFloat(), height.toFloat()), android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
            }
            ByteArrayOutputStream().use { out ->
                output.compress(Bitmap.CompressFormat.JPEG, quality, out)
                out.toByteArray()
            }.also {
                source.recycle()
                output.recycle()
            }
        }.getOrNull()
    }

    suspend fun profilePicture(context: Context, uri: Uri, region: RectF? = null) = cropJpeg(context, uri, 256, 256, 85, region)
    suspend fun eventCover(context: Context, uri: Uri, region: RectF? = null) = cropJpeg(context, uri, 1280, 720, 82, region)
}
