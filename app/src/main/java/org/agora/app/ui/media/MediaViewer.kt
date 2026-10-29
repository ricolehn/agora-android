package org.agora.app.ui.media

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.graphics.createBitmap
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import org.agora.app.R
import org.agora.app.data.MediaFile
import org.agora.app.data.mediaFileUrl
import org.agora.app.data.mediaPreviewUrl
import org.agora.app.ui.components.LocalContainer

private const val MAX_ZOOM = 6f
private const val DOUBLE_TAP_ZOOM = 2.5f

/**
 * Zoom of a picture or a PDF: scaled around the middle of its box and moved by [offset]; it can only be moved as
 * far as it is larger than the box. Not zoomed, it sits in the middle and swipes go to the pager below.
 */
@Stable
class ZoomState {
    var scale by mutableFloatStateOf(1f)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set
    var box = IntSize.Zero

    val zoomed: Boolean get() = scale > 1.01f

    fun reset() {
        scale = 1f
        offset = Offset.Zero
    }

    /** Scaled to [target] with the spot under [point] (measured in the box) staying where it is. */
    fun zoomTo(target: Float, point: Offset, pan: Offset = Offset.Zero) {
        val next = target.coerceIn(1f, MAX_ZOOM)
        val middle = Offset(box.width / 2f, box.height / 2f)
        // A point p of the picture shows at middle + (p - middle) * scale + offset
        val spot = (point - middle - offset) / scale
        offset = clamp(point - middle - spot * next + pan, next)
        scale = next
        if (!zoomed) reset()
    }

    private fun clamp(value: Offset, scale: Float): Offset {
        val maxX = box.width * (scale - 1) / 2
        val maxY = box.height * (scale - 1) / 2
        return Offset(value.x.coerceIn(-maxX, maxX), value.y.coerceIn(-maxY, maxY))
    }
}

/**
 * Pinch with two fingers, move with one while zoomed in, double tap for 2.5x and back. One finger on a picture
 * that is not zoomed in is left alone (the pager turns the page, a list scrolls).
 */
private fun Modifier.zoomable(state: ZoomState, onTap: () -> Unit): Modifier = this
    .onSizeChanged { state.box = it }
    .pointerInput(state) {
        detectTapGestures(
            onTap = { onTap() },
            onDoubleTap = { point -> if (state.zoomed) state.reset() else state.zoomTo(DOUBLE_TAP_ZOOM, point) }
        )
    }
    .pointerInput(state) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            do {
                val event = awaitPointerEvent()
                val fingers = event.changes.count { it.pressed }
                if (fingers >= 2 || state.zoomed) {
                    val zoom = event.calculateZoom()
                    val pan = event.calculatePan()
                    state.zoomTo(state.scale * zoom, event.calculateCentroid(useCurrent = false), pan)
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                }
            } while (event.changes.any { it.pressed })
        }
    }

private fun Modifier.zoomed(state: ZoomState): Modifier = graphicsLayer {
    scaleX = state.scale
    scaleY = state.scale
    translationX = state.offset.x
    translationY = state.offset.y
}

/** A full-screen window on black with light status bar icons, closed by back. */
@Composable
private fun DarkViewer(onClose: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val view = LocalView.current
        LaunchedEffect(view) {
            (view.parent as? DialogWindowProvider)?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = false
                    isAppearanceLightNavigationBars = false
                }
            }
        }
        Box(Modifier.fillMaxSize().background(Color.Black), content = content)
    }
}

/** Name, what it is and the actions over the top of the viewer (on a soft shade, so it reads on bright pictures). */
@Composable
private fun ViewerBar(title: String, subtitle: String?, onClose: () -> Unit, actions: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.65f), Color.Transparent)))
            .statusBarsPadding()
            .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, stringResource(R.string.close), tint = Color.White) }
        Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f), maxLines = 1)
        }
        actions()
    }
}

@Composable
private fun ViewerMessage(text: String, modifier: Modifier = Modifier, action: @Composable () -> Unit = {}) {
    Column(modifier.padding(32.dp).widthIn(max = 360.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(Icons.Outlined.BrokenImage, null, Modifier.size(40.dp), tint = Color.White.copy(alpha = 0.6f))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.85f), textAlign = TextAlign.Center)
        action()
    }
}

@Composable
private fun LightButton(text: String, onClick: () -> Unit) =
    OutlinedButton(onClick = onClick) { Text(text, color = Color.White) }

/**
 * The pictures of the folder, full screen: Nextcloud's largest preview (never the original, which may be DNG, RAW
 * or HEIC); swipe for the next one, tap to hide the bar.
 */
@Composable
fun PictureViewer(pictures: List<MediaFile>, start: Int, onClose: () -> Unit, onDownload: (MediaFile) -> Unit, onShare: (MediaFile) -> Unit) {
    DarkViewer(onClose) {
        val pager = rememberPagerState(initialPage = start) { pictures.size }
        val zooms = remember { mutableMapOf<Int, ZoomState>() }
        var bar by remember { mutableStateOf(true) }
        val current = zooms.getOrPut(pager.currentPage) { ZoomState() }
        HorizontalPager(
            pager,
            userScrollEnabled = !current.zoomed,
            beyondViewportPageCount = 1,
            key = { pictures[it].path },
            modifier = Modifier.fillMaxSize()
        ) { index ->
            val zoom = zooms.getOrPut(index) { ZoomState() }
            // Back to the whole picture when it is swiped away
            LaunchedEffect(pager.currentPage) { if (pager.currentPage != index) zoom.reset() }
            Picture(pictures[index], zoom, onTap = { bar = !bar })
        }
        AnimatedVisibility(bar, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
            val file = pictures.getOrNull(pager.currentPage)
            ViewerBar(file?.name.orEmpty(), if (pictures.size > 1) "${pager.currentPage + 1} / ${pictures.size}" else null, onClose) {
                if (file != null) {
                    IconButton(onClick = { onShare(file) }) { Icon(Icons.Outlined.Share, stringResource(R.string.media_share), tint = Color.White) }
                    IconButton(onClick = { onDownload(file) }) { Icon(Icons.Outlined.Download, stringResource(R.string.media_download), tint = Color.White) }
                }
            }
        }
    }
}

/** Formats every Android phone shows as they are (only these may be opened as the original without a preview). */
private val SHOWN_AS_IS = Regex("^image/(jpeg|png|gif|webp|bmp)$", RegexOption.IGNORE_CASE)

@Composable
private fun Picture(file: MediaFile, zoom: ZoomState, onTap: () -> Unit) {
    val context = LocalContext.current
    val repo = LocalContainer.current.repo
    // Nextcloud's largest preview, like its own viewer (asked for more, the server hands out its largest)
    val url = when {
        file.hasPreview -> repo.mediaPreviewUrl(file, LARGEST_PREVIEW_SIZE)
        SHOWN_AS_IS.matches(file.contentType) -> repo.mediaFileUrl(file).toString()
        else -> null
    }
    var state by remember(url) { mutableStateOf<AsyncImagePainter.State?>(null) }
    Box(Modifier.fillMaxSize().zoomable(zoom, onTap), contentAlignment = Alignment.Center) {
        if (url == null || state is AsyncImagePainter.State.Error) {
            ViewerMessage(stringResource(R.string.media_no_preview))
            return@Box
        }
        AsyncImage(
            model = remember(url) { ImageRequest.Builder(context).data(url).crossfade(true).build() },
            contentDescription = file.name,
            contentScale = ContentScale.Fit,
            onState = { state = it },
            modifier = Modifier.fillMaxSize().zoomed(zoom)
        )
        // Coil decodes the picture above only as large as the screen (light on memory while swiping), which would be
        // soft zoomed in. So while zoomed in the same picture lies over it once more at its own size, from the cache
        // without a second download; over it and never in its place, so the picture stays whatever becomes of this one.
        if (zoom.zoomed && state is AsyncImagePainter.State.Success) AsyncImage(
            model = remember(url) { ImageRequest.Builder(context).data(url).size(LARGEST_PREVIEW_SIZE).build() },
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().zoomed(zoom)
        )
        if (state == null || state is AsyncImagePainter.State.Loading) CircularProgressIndicator(color = Color.White.copy(alpha = 0.8f), strokeWidth = 3.dp)
    }
}

/** One page at a time: PdfRenderer is not made for several threads. */
private val pdfLock = Mutex()

/**
 * A PDF page by page, right in the app (fetched once to the device, drawn by Android's PdfRenderer as wide as the
 * screen); pinch or double tap to zoom. Protected or broken files are offered to another app.
 */
@Composable
fun PdfViewer(file: MediaFile, vm: MediaViewModel, onClose: () -> Unit, onDownload: () -> Unit, onOpenElsewhere: () -> Unit) {
    DarkViewer(onClose) {
        var progress by remember { mutableFloatStateOf(-1f) }
        var failed by remember { mutableStateOf(false) }
        var renderer by remember { mutableStateOf<PdfRenderer?>(null) }
        LaunchedEffect(file.path, file.etag) {
            try {
                val local = vm.localCopy(file) { done, total -> if (total > 0) progress = done.toFloat() / total }
                renderer = withContext(Dispatchers.IO) { PdfRenderer(ParcelFileDescriptor.open(local, ParcelFileDescriptor.MODE_READ_ONLY)) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                failed = true
            }
        }
        // Closed once no page is being drawn any more (off the main thread: a page may just be drawn)
        val appScope = LocalContainer.current.appScope
        DisposableEffect(Unit) { onDispose { renderer?.let { r -> appScope.launch { pdfLock.withLock { r.close() } } } } }
        val zoom = remember { ZoomState() }
        var bar by remember { mutableStateOf(true) }
        val pdf = renderer
        when {
            failed -> ViewerMessage(stringResource(R.string.media_pdf_failed), Modifier.align(Alignment.Center)) {
                LightButton(stringResource(R.string.media_open_with), onOpenElsewhere)
            }
            pdf == null -> Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.media_pdf_loading), color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyMedium)
                if (progress >= 0) LinearProgressIndicator(progress = { progress }, modifier = Modifier.padding(top = 12.dp).widthIn(max = 220.dp))
                else LinearProgressIndicator(Modifier.padding(top = 12.dp).widthIn(max = 220.dp))
            }
            else -> {
                val width = with(LocalDensity.current) { minOf(2048, LocalConfiguration.current.screenWidthDp.dp.roundToPx()) }
                Box(Modifier.fillMaxSize().zoomable(zoom) { bar = !bar }) {
                    LazyColumn(
                        Modifier.fillMaxSize().zoomed(zoom),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 72.dp, bottom = 24.dp),
                        userScrollEnabled = !zoom.zoomed
                    ) {
                        items(pdf.pageCount) { index -> PdfPage(pdf, index, width) }
                    }
                }
            }
        }
        AnimatedVisibility(bar, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
            val pages = pdf?.pageCount?.let { pluralStringResource(R.plurals.media_pdf_pages, it, it) }
            ViewerBar(file.name, pages, onClose) {
                IconButton(onClick = onOpenElsewhere) { Icon(Icons.AutoMirrored.Outlined.OpenInNew, stringResource(R.string.media_open_with), tint = Color.White) }
                IconButton(onClick = onDownload) { Icon(Icons.Outlined.Download, stringResource(R.string.media_download), tint = Color.White) }
            }
        }
    }
}

@Composable
private fun PdfPage(pdf: PdfRenderer, index: Int, width: Int) {
    val bitmap by produceState<Bitmap?>(null, pdf, index, width) {
        value = runCatching {
            withContext(Dispatchers.IO) {
                pdfLock.withLock {
                    pdf.openPage(index).use { page ->
                        val height = (width.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
                        createBitmap(width, height).also {
                            it.eraseColor(android.graphics.Color.WHITE)
                            page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        }
                    }
                }
            }
        }.getOrNull()
    }
    val image = bitmap
    if (image == null) Box(Modifier.fillMaxWidth().aspectRatio(1f / 1.414f).background(Color.White.copy(alpha = 0.06f)))
    else Image(image.asImageBitmap(), null, Modifier.fillMaxWidth().aspectRatio(image.width.toFloat() / image.height), contentScale = ContentScale.FillWidth)
}

/**
 * Videos and music in the app's own player (Media3): the file streams with the member's token, ranges let it be
 * wound; a format the phone cannot play is offered as a download.
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerViewer(file: MediaFile, url: HttpUrl, onClose: () -> Unit, onDownload: () -> Unit) {
    DarkViewer(onClose) {
        val context = LocalContext.current
        val http = LocalContainer.current.api.transferHttp
        var failed by remember { mutableStateOf(false) }
        val player = remember(url) {
            ExoPlayer.Builder(context)
                .setMediaSourceFactory(DefaultMediaSourceFactory(OkHttpDataSource.Factory(http)))
                .build()
                .apply {
                    setMediaItem(MediaItem.fromUri(url.toString()))
                    prepare()
                    playWhenReady = true
                }
        }
        DisposableEffect(player) {
            val listener = object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) { failed = true }
            }
            player.addListener(listener)
            onDispose {
                player.removeListener(listener)
                player.release()
            }
        }
        LifecycleEventEffect(Lifecycle.Event.ON_STOP) { player.pause() }
        val audio = file.kind == "audio"
        if (failed) ViewerMessage(stringResource(R.string.media_play_failed), Modifier.align(Alignment.Center)) {
            LightButton(stringResource(R.string.media_download), onDownload)
        } else {
            if (audio) Column(Modifier.align(Alignment.Center).padding(bottom = 96.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(120.dp).background(kindColor("audio").copy(alpha = 0.25f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.MusicNote, null, Modifier.size(56.dp), tint = Color.White)
                }
                Text(file.name, color = Color.White, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 20.dp, start = 32.dp, end = 32.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        setShowNextButton(false)
                        setShowPreviousButton(false)
                        setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                        if (audio) {
                            // Music: the controls stay on screen
                            controllerShowTimeoutMs = 0
                            controllerHideOnTouch = false
                            defaultArtwork = null
                        }
                    }
                },
                modifier = Modifier.fillMaxSize().navigationBarsPadding()
            )
        }
        ViewerBar(file.name, null, onClose) {
            IconButton(onClick = onDownload) { Icon(Icons.Outlined.Download, stringResource(R.string.media_download), tint = Color.White) }
        }
    }
}
