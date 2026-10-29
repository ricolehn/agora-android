package org.agora.app.data

import android.content.ContentResolver
import android.net.Uri
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import org.agora.app.data.remote.ApiException

// Media library (Extras) - server: backend/media.js, nextcloud.js and routes/media.js of the web app (3.3 BA SING SE).
// A folder of the community's Nextcloud; paths are relative to its base folder ("" is the base folder). The server
// checks every request against the folder rights - the app only shows what it sends.

@Serializable
data class MediaStatus(val enabled: Boolean = false, val canManage: Boolean = false, val hasAccess: Boolean = false)

/** A folder in a listing; [access] is "read", "write" or "none" (only passed through to a folder below). */
@Serializable
data class MediaFolder(
    val name: String = "",
    val path: String = "",
    val size: Long? = null,
    val modified: String? = null,
    val access: String = "none",
    val canDelete: Boolean = false,
    /** Only for members with extended media rights: the folder sets rights of its own / has "no access" rows. */
    val hasRules: Boolean = false,
    val hiddenFor: List<String> = emptyList()
)

/** A file; [kind] is "image", "pdf", "video", "audio" or "other" (the server decides what may open in the app). */
@Serializable
data class MediaFile(
    val name: String = "",
    val path: String = "",
    val size: Long? = null,
    val modified: String? = null,
    val contentType: String = "",
    val etag: String = "",
    val hasPreview: Boolean = false,
    val kind: String = "other"
)

@Serializable
data class MediaListing(
    val path: String = "",
    val name: String = "",
    val access: String = "none",
    val canManage: Boolean = false,
    val folders: List<MediaFolder> = emptyList(),
    val files: List<MediaFile> = emptyList()
)

@Serializable
data class MediaFailure(val name: String = "", val error: String = "")

@Serializable
data class MediaDeleteResult(val deleted: List<String> = emptyList(), val failed: List<MediaFailure> = emptyList())

@Serializable
data class MediaDownloadTicket(val id: String = "", val url: String = "", val files: Int = 0)

@Serializable
data class MediaStored(val name: String = "", val path: String = "")

@Serializable
data class MediaUploadResult(val files: List<MediaStored> = emptyList())

/** Who a right can be for: all members ("*"), a group (its id) or a person ("user:<id>"); [kind] all / group / person. */
@Serializable
data class MediaChoice(val id: String = "", val name: String = "", val kind: String = "group")

@Serializable
data class MediaInherited(val level: String = "", val from: String = "")

/** The rights of one folder: its own ("read", "write", "hide") and what comes from a folder above. */
@Serializable
data class MediaFolderRules(
    val path: String = "",
    val own: Map<String, String> = emptyMap(),
    val inherited: Map<String, MediaInherited> = emptyMap()
)

@Serializable
data class MediaRuleEntry(val path: String = "", val groups: Map<String, String> = emptyMap())

@Serializable
data class MediaRules(
    val choices: List<MediaChoice> = emptyList(),
    val rules: List<MediaRuleEntry> = emptyList(),
    val folder: MediaFolderRules? = null
)

@Serializable
private data class MediaRulesSaved(val folder: MediaFolderRules = MediaFolderRules())

/** Whether to show the Media tile; servers before 3.3 have no media library. */
suspend fun AgoraRepository.mediaStatus(): MediaStatus = try {
    api.get("/api/media/status")
} catch (e: ApiException) {
    if (e.status == 404) MediaStatus() else throw e
}

suspend fun AgoraRepository.mediaList(path: String): MediaListing = api.get("/api/media/list", mapOf("path" to path))

/** A preview in one of the server's sizes; with the file's version (etag) the address is cached for good. */
fun AgoraRepository.mediaPreviewUrl(file: MediaFile, size: Int, crop: Boolean = false): String =
    api.url("/api/media/preview", buildMap {
        put("path", file.path)
        put("size", size.toString())
        if (crop) put("crop", "1")
        if (file.etag.isNotBlank()) put("v", file.etag)
    }).toString()

/** The file itself (ranges work, so videos can be played and wound); [download]: always as a download. */
fun AgoraRepository.mediaFileUrl(file: MediaFile, download: Boolean = false): HttpUrl =
    api.url("/api/media/file", buildMap {
        put("path", file.path)
        if (download) put("download", "1")
        if (file.etag.isNotBlank()) put("v", file.etag)
    })

suspend fun AgoraRepository.createMediaFolder(path: String, name: String): MediaStored =
    api.post("/api/media/folder", buildJsonObject { put("path", path); put("name", name) })

private fun selection(path: String, names: Collection<String>) = buildJsonObject {
    put("path", path)
    put("names", JsonArray(names.map { JsonPrimitive(it) }))
}

/** Deletes [names] in the folder [path] (into Nextcloud's trash bin); what could not be deleted comes back in failed. */
suspend fun AgoraRepository.deleteMedia(path: String, names: Collection<String>): MediaDeleteResult =
    api.post("/api/media/delete", selection(path, names))

/** Prepares one ZIP of [names] in [path]; its address works for a few minutes and only for this member. */
suspend fun AgoraRepository.prepareMediaDownload(path: String, names: Collection<String>): MediaDownloadTicket =
    api.post("/api/media/download", selection(path, names))

/** A file the person picked, streamed from the device (never read into memory as a whole). */
data class UploadSource(val uri: Uri, val name: String, val size: Long, val mime: String?)

/** Sends [files] into the folder [path]; [progress] gets the bytes sent and the bytes of all files. */
suspend fun AgoraRepository.uploadMedia(
    resolver: ContentResolver,
    path: String,
    files: List<UploadSource>,
    progress: (sent: Long, total: Long) -> Unit
): MediaUploadResult {
    val total = files.sumOf { it.size.coerceAtLeast(0) }
    var sent = 0L
    val body = MultipartBody.Builder().setType(MultipartBody.FORM).apply {
        files.forEach { file ->
            addFormDataPart("files", file.name, UriBody(resolver, file, file.mime?.toMediaTypeOrNull()) { bytes ->
                sent += bytes
                progress(sent, total)
            })
        }
    }.build()
    return api.transfer(api.url("/api/media/upload", mapOf("path" to path)), body, MediaUploadResult.serializer())
}

/** A part that reads its file from the content resolver while it is sent. */
private class UriBody(
    private val resolver: ContentResolver,
    private val file: UploadSource,
    private val type: MediaType?,
    private val onSent: (Long) -> Unit
) : RequestBody() {
    override fun contentType() = type
    override fun contentLength() = if (file.size >= 0) file.size else -1
    // OkHttp may send a body again (a retry after a broken connection): the counted bytes must not double then
    override fun isOneShot() = true

    override fun writeTo(sink: BufferedSink) {
        val input = resolver.openInputStream(file.uri) ?: throw java.io.FileNotFoundException(file.name)
        input.source().use { source ->
            while (true) {
                val read = source.read(sink.buffer, 64 * 1024)
                if (read < 0) break
                sink.emit()
                onSent(read)
            }
        }
    }
}

/** All rights (and, with [path], those of that folder) plus whom they can be for - extended media rights only. */
suspend fun AgoraRepository.mediaRules(path: String? = null): MediaRules =
    api.get("/api/media/rules", if (path != null) mapOf("path" to path) else emptyMap())

/** Sets the own rights of [path] ({ "*" | groupId | "user:<id>": read / write / hide }; empty removes them). */
suspend fun AgoraRepository.saveMediaRules(path: String, groups: Map<String, String>): MediaFolderRules =
    api.call("PUT", "/api/media/rules", MediaRulesSaved.serializer(), buildJsonObject {
        put("path", path)
        put("groups", JsonObject(groups.mapValues { JsonPrimitive(it.value) }))
    }).folder
