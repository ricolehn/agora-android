package org.agora.app.ui.media

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.annotation.StringRes
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import org.agora.app.AppContainer
import org.agora.app.R
import org.agora.app.data.MediaDeleteResult
import org.agora.app.data.MediaFile
import org.agora.app.data.MediaListing
import org.agora.app.data.UploadSource
import org.agora.app.data.createMediaFolder
import org.agora.app.data.deleteMedia
import org.agora.app.data.mediaFileUrl
import org.agora.app.data.mediaList
import org.agora.app.data.prepareMediaDownload
import org.agora.app.data.remote.ApiException
import org.agora.app.data.uploadMedia
import java.io.File
import java.security.MessageDigest

/** The open folder: its listing (null until the first answer), or the error shown instead. */
data class MediaPage(val path: String = "", val listing: MediaListing? = null, val error: String? = null)

/** An upload or download in progress; [total] < 0 while the length is unknown. */
data class MediaTransfer(val upload: Boolean, val done: Long = 0, val total: Long = -1) {
    /** All bytes sent, the server now stores them in the cloud. */
    val storing: Boolean get() = upload && total > 0 && done >= total
    val percent: Int? get() = if (total > 0) (done * 100 / total).toInt().coerceIn(0, 100) else null
}

/**
 * A message for the snackbar: a text of the app (with arguments) or the server's own words; [renamed]: after an
 * upload, names already taken got a number.
 */
data class MediaNotice(@StringRes val text: Int = 0, val args: List<Any> = emptyList(), val message: String? = null, val renamed: Boolean = false)

/**
 * The media library in memory: the open folder, the last [CACHE_SIZE] folders seen (they show at once while the
 * server is asked again; the page only changes when its answer differs), the selection and the running transfer.
 */
class MediaViewModel(private val c: AppContainer, context: Context) : ViewModel() {
    private val prefs = context.applicationContext.getSharedPreferences("agora_media", Context.MODE_PRIVATE)
    private val cacheDir = File(context.applicationContext.cacheDir, "media")

    private val _page = MutableStateFlow(MediaPage())
    val page = _page.asStateFlow()
    private val _selected = MutableStateFlow<Set<String>>(emptySet())
    val selected = _selected.asStateFlow()
    private val _sort = MutableStateFlow(if (prefs.getString("sort", null) == "newest") MediaSort.NEWEST else MediaSort.NAME)
    val sort = _sort.asStateFlow()
    private val _transfer = MutableStateFlow<MediaTransfer?>(null)
    val transfer = _transfer.asStateFlow()
    private val _notices = MutableSharedFlow<MediaNotice>(extraBufferCapacity = 4)
    val notices: SharedFlow<MediaNotice> = _notices

    private val cache = object : LinkedHashMap<String, MediaListing>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, MediaListing>?) = size > CACHE_SIZE
    }
    private var loadJob: Job? = null
    private var transferJob: Job? = null

    /** Opens [path]: the folder seen before shows at once, the server is asked in any case. */
    fun open(path: String) {
        if (path != _page.value.path) _selected.value = emptySet()
        _page.value = MediaPage(path, cache[path])
        load()
    }

    /** Asks the server for the open folder; [silent]: a failure keeps what is shown (live updates, coming back). */
    fun load(silent: Boolean = false) {
        val path = _page.value.path
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            try {
                val listing = c.repo.mediaList(path)
                cache[path] = listing
                if (_page.value.path != path) return@launch
                // Unchanged: nothing redraws (the same data class compares equal)
                _page.update { if (it.listing == listing && it.error == null) it else it.copy(listing = listing, error = null) }
                val present = (listing.folders.map { it.name } + listing.files.map { it.name }).toSet()
                _selected.update { it intersect present }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (_page.value.path != path) return@launch
                val status = (e as? ApiException)?.status
                when {
                    // Deleted or no longer visible: one level up, with a word why
                    status == 404 && path.isNotEmpty() -> {
                        cache.remove(path)
                        _notices.tryEmit(MediaNotice(R.string.media_folder_gone))
                        open(parentPath(path))
                    }
                    silent && _page.value.listing != null -> Unit
                    else -> {
                        cache.remove(path)
                        _page.update {
                            it.copy(listing = null, error = if (status == 404 || status == 503) UNAVAILABLE else (e as? ApiException)?.message.orEmpty())
                        }
                    }
                }
            }
        }
    }

    fun setSort(sort: MediaSort) {
        _sort.value = sort
        prefs.edit { putString("sort", if (sort == MediaSort.NEWEST) "newest" else "name") }
    }

    // --- Selection (a long press starts it, taps then add and remove) ---

    fun toggle(name: String) = _selected.update { if (name in it) it - name else it + name }
    fun selectAll(names: Collection<String>) { _selected.value = names.toSet() }
    fun clearSelection() { _selected.value = emptySet() }

    // --- Changes (the server checks the rights again) ---

    suspend fun createFolder(name: String) {
        val path = _page.value.path
        c.repo.createMediaFolder(path, name.trim())
        if (_page.value.path == path) load(silent = true)
    }

    /** Deletes [names] of the open folder; the page drops them right away, the server's answer follows. */
    suspend fun delete(names: Collection<String>): MediaDeleteResult {
        val path = _page.value.path
        val result = c.repo.deleteMedia(path, names)
        val gone = result.deleted.toSet()
        _page.update { page ->
            if (page.path != path) page
            else page.copy(listing = page.listing?.let { l -> l.copy(folders = l.folders.filter { it.name !in gone }, files = l.files.filter { it.name !in gone }) })
        }
        _selected.update { it - gone }
        cache.remove(path)
        load(silent = true)
        return result
    }

    /** Sends [files] into the open folder, one transfer at a time. */
    fun upload(resolver: ContentResolver, files: List<UploadSource>) {
        if (_transfer.value != null || files.isEmpty()) return
        val path = _page.value.path
        transferJob = viewModelScope.launch {
            _transfer.value = MediaTransfer(upload = true, total = files.sumOf { it.size.coerceAtLeast(0) })
            try {
                val result = c.repo.uploadMedia(resolver, path, files) { sent, total -> _transfer.value = MediaTransfer(true, sent, total) }
                val renamed = result.files.map { it.name } != files.map { it.name }
                val count = result.files.size
                _notices.tryEmit(
                    if (count == 1) MediaNotice(R.string.media_uploaded_one, renamed = renamed)
                    else MediaNotice(R.string.media_uploaded, listOf(count), renamed = renamed)
                )
                cache.remove(path)
                if (_page.value.path == path) load(silent = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _notices.tryEmit(failure(e, R.string.media_upload_failed))
                if (_page.value.path == path) load(silent = true)
            } finally {
                _transfer.value = null
            }
        }
    }

    /** A ZIP of [names] in the open folder: the server prepares it, then it can be fetched for a few minutes. */
    suspend fun prepareZip(names: Collection<String>): HttpUrl {
        val ticket = c.repo.prepareMediaDownload(_page.value.path, names)
        return c.api.url(ticket.url)
    }

    fun fileUrl(file: MediaFile, download: Boolean = true): HttpUrl = c.repo.mediaFileUrl(file, download)

    /** Streams [url] into the document [target] the person picked; a broken download leaves no half file behind. */
    fun downloadTo(resolver: ContentResolver, target: Uri, url: HttpUrl) {
        if (_transfer.value != null) return
        transferJob = viewModelScope.launch {
            _transfer.value = MediaTransfer(upload = false)
            try {
                withContext(Dispatchers.IO) {
                    val sink = resolver.openOutputStream(target) ?: throw ApiException(0, "")
                    sink.use { c.api.download(url, it) { done, total -> _transfer.value = MediaTransfer(false, done, total) } }
                }
                _notices.tryEmit(MediaNotice(R.string.media_downloaded))
            } catch (e: Throwable) {
                withContext(Dispatchers.IO) { runCatching { DocumentsContract.deleteDocument(resolver, target) } }
                if (e is CancellationException) throw e
                _notices.tryEmit(failure(e, R.string.media_download_failed))
            } finally {
                _transfer.value = null
            }
        }
    }

    fun cancelTransfer() {
        transferJob?.cancel()
    }

    /**
     * The file on the device (cache/media), fetched once per version: to open it in another app, to share it or
     * to read a PDF page by page. Older copies of other files are cleared after a day.
     */
    suspend fun localCopy(file: MediaFile, progress: (Long, Long) -> Unit = { _, _ -> }): File = withContext(Dispatchers.IO) {
        cacheDir.mkdirs()
        val now = System.currentTimeMillis()
        cacheDir.listFiles()?.forEach { if (now - it.lastModified() > DAY_MS) it.deleteRecursively() }
        val key = MessageDigest.getInstance("SHA-1").digest("${file.path}|${file.etag}".toByteArray()).joinToString("") { "%02x".format(it) }.take(16)
        val folder = File(cacheDir, key)
        val target = File(folder, file.name.replace('/', '_').ifBlank { "file" })
        if (target.exists() && file.size != null && target.length() == file.size) return@withContext target
        folder.mkdirs()
        val part = File(folder, ".part")
        try {
            part.outputStream().use { c.api.download(c.repo.mediaFileUrl(file, download = true), it, progress) }
            if (!part.renameTo(target)) throw ApiException(0, "")
        } finally {
            part.delete()
        }
        target
    }

    private fun failure(e: Throwable, @StringRes fallback: Int) =
        (e as? ApiException)?.message?.takeIf { it.isNotBlank() && e.status != 0 }?.let { MediaNotice(message = it) } ?: MediaNotice(fallback)

    companion object {
        const val CACHE_SIZE = 40
        /** Marks [MediaPage.error] "nothing to see yet" (not set up, or the base folder is gone). */
        const val UNAVAILABLE = "\u0000unavailable"
        private const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}
