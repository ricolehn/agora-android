package org.agora.app.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.agora.app.data.remote.AgoraApi
import java.io.File
import java.security.MessageDigest

/**
 * The church logo of the server, kept on the device per server: the app opens with the last known logo (no flash
 * of the bundled knot), then checks the server and takes the new one, or goes back to the bundled knot when the
 * server shows its default logo again. Logos are public, so they stay on logout; offline the kept one stays.
 * The SVG is drawn into a picture once, so the first frame can show it without decoding anything.
 */
class LogoStore(context: Context, private val api: AgoraApi) {
    private val dir = File(context.noBackupFilesDir, "server-logo")
    private val mutex = Mutex()
    private val _logo = MutableStateFlow<ImageBitmap?>(null)
    /** The logo of the current server; null shows the bundled Agora knot. */
    val logo: StateFlow<ImageBitmap?> = _logo.asStateFlow()
    @Volatile private var primedFor: String? = null
    private var checkedFor: String? = null
    private var checkedAt = 0L

    /** Reads the kept logo of [baseUrl] once (a small PNG), so the screen opening right now already shows it. */
    fun prime(baseUrl: String) {
        if (primedFor == baseUrl) return
        primedFor = baseUrl
        _logo.value = if (baseUrl.isBlank()) null else runCatching {
            BitmapFactory.decodeFile(pictureFile(baseUrl).path)?.asImageBitmap()
        }.getOrNull()
    }

    /**
     * Asks the server for its logo (at most once a minute per server). A changed logo is drawn and kept, the default
     * logo removes the kept one; without an answer (offline, server down) everything stays as it is.
     */
    suspend fun refresh(context: Context): Unit = mutex.withLock {
        val base = api.baseUrl
        if (base.isBlank()) return@withLock
        val now = System.currentTimeMillis()
        if (checkedFor == base && now - checkedAt < CHECK_INTERVAL_MS) return@withLock
        val svg = runCatching { api.getText(LOGO_PATH) }.getOrNull()?.takeIf { it.contains("<svg") } ?: return@withLock
        checkedFor = base
        checkedAt = now
        if (isDefaultLogo(svg)) {
            withContext(Dispatchers.IO) { pictureFile(base).delete(); hashFile(base).delete() }
            if (api.baseUrl == base) _logo.value = null
            return@withLock
        }
        val hash = sha256(svg)
        val kept = withContext(Dispatchers.IO) { runCatching { hashFile(base).readText() }.getOrNull() }
        if (hash == kept && _logo.value != null) return@withLock
        val picture = draw(context, svg) ?: return@withLock
        withContext(Dispatchers.IO) {
            runCatching {
                dir.mkdirs()
                // Write aside and swap, so a crash mid-write never leaves a broken picture
                val tmp = File(pictureFile(base).path + ".tmp")
                tmp.outputStream().use { picture.compress(Bitmap.CompressFormat.PNG, 100, it) }
                if (!tmp.renameTo(pictureFile(base))) { pictureFile(base).delete(); tmp.renameTo(pictureFile(base)) }
                hashFile(base).writeText(hash)
            }
        }
        if (api.baseUrl == base) _logo.value = picture.asImageBitmap()
    }

    /** The SVG as a picture (Coil's SVG decoder, like everywhere else in the app); never cached by Coil itself. */
    private suspend fun draw(context: Context, svg: String): Bitmap? = runCatching {
        val request = ImageRequest.Builder(context).data(svg.toByteArray()).size(PICTURE_PX).allowHardware(false)
            .memoryCachePolicy(CachePolicy.DISABLED).diskCachePolicy(CachePolicy.DISABLED).build()
        SingletonImageLoader.get(context).execute(request).image?.toBitmap()
    }.getOrNull()

    private fun key(baseUrl: String) = sha256(baseUrl.trimEnd('/')).take(24)
    private fun pictureFile(baseUrl: String) = File(dir, key(baseUrl) + ".png")
    private fun hashFile(baseUrl: String) = File(dir, key(baseUrl) + ".sha256")

    companion object {
        const val LOGO_PATH = "/assets/church-logo.svg"
        private const val CHECK_INTERVAL_MS = 60_000L
        private const val PICTURE_PX = 384

        /**
         * The server's own default logo (the Agora knot of web v3.0+, assets/church-logo.svg): the church has not
         * uploaded one. The app then shows its bundled knot, which also follows the dark theme.
         */
        fun isDefaultLogo(svg: String): Boolean =
            svg.contains("viewBox=\"-82 -82 2884 2884\"") && svg.contains("#204E44", ignoreCase = true)

        private fun sha256(text: String): String =
            MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
