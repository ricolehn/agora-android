package org.agora.app.ui.media

import org.agora.app.data.MediaChoice
import org.agora.app.data.MediaFile
import org.agora.app.data.MediaFolder
import org.agora.app.data.MediaFolderRules
import org.agora.app.data.MediaInherited
import java.text.Collator
import java.text.Normalizer
import java.text.NumberFormat
import java.util.Locale

// The media library's paths, sizes and rights rows (pure functions like the web's assets/media.js, tested in
// MediaLogicTest). Paths are relative to the base folder: "" is the base folder, "Photos/2026" a folder in it.

/** "Photos/2026" → [("Photos", "Photos"), ("2026", "Photos/2026")]. */
fun breadcrumb(path: String): List<Pair<String, String>> {
    val parts = path.split('/').filter { it.isNotEmpty() }
    return parts.mapIndexed { index, name -> name to parts.take(index + 1).joinToString("/") }
}

fun parentPath(path: String): String = path.split('/').filter { it.isNotEmpty() }.dropLast(1).joinToString("/")

fun folderName(path: String): String = path.substringAfterLast('/')

/** The server's preview sizes: asking for exactly these lets Nextcloud and the caches reuse what they made. */
val PREVIEW_SIZES = listOf(64, 128, 256, 512, 1024, 2048, 4096)
/** The viewer asks for this and gets the server's largest preview (Nextcloud's preview_max_x/y caps it). */
const val LARGEST_PREVIEW_SIZE = 4096

fun previewBucket(pixels: Int): Int = PREVIEW_SIZES.firstOrNull { it >= pixels } ?: PREVIEW_SIZES.last()

/** A tile of [pixels] device pixels: sharp, never more than 512. */
fun thumbSize(pixels: Int): Int = minOf(512, previewBucket(pixels))

enum class MediaSort { NAME, NEWEST }

private val collator: Collator get() = Collator.getInstance().apply { strength = Collator.PRIMARY }

/** Compares names with numbers counted ("Bild 2" before "Bild 10"), case and accents aside. */
fun compareNames(a: String, b: String): Int {
    val left = NAME_PARTS.findAll(a).map { it.value }.toList()
    val right = NAME_PARTS.findAll(b).map { it.value }.toList()
    val c = collator
    for (i in 0 until minOf(left.size, right.size)) {
        val x = left[i]
        val y = right[i]
        val bothNumbers = x[0].isDigit() && y[0].isDigit()
        val result = if (bothNumbers) x.trimStart('0').length.compareTo(y.trimStart('0').length).takeIf { it != 0 }
            ?: x.trimStart('0').compareTo(y.trimStart('0'))
        else c.compare(x, y)
        if (result != 0) return result
    }
    return left.size.compareTo(right.size)
}

private val NAME_PARTS = Regex("\\d+|\\D+")

/** Folders by name or, like files, newest first (the same name order breaks ties). */
fun sortFolders(folders: List<MediaFolder>, sort: MediaSort): List<MediaFolder> = when (sort) {
    MediaSort.NAME -> folders.sortedWith { a, b -> compareNames(a.name, b.name) }
    MediaSort.NEWEST -> folders.sortedWith(compareByDescending<MediaFolder> { it.modified.orEmpty() }.thenComparator { a, b -> compareNames(a.name, b.name) })
}

fun sortFiles(files: List<MediaFile>, sort: MediaSort): List<MediaFile> = when (sort) {
    MediaSort.NAME -> files.sortedWith { a, b -> compareNames(a.name, b.name) }
    MediaSort.NEWEST -> files.sortedWith(compareByDescending<MediaFile> { it.modified.orEmpty() }.thenComparator { a, b -> compareNames(a.name, b.name) })
}

/** "1.4 MB" style sizes; empty for an unknown size. */
fun formatSize(bytes: Long?, locale: Locale = Locale.getDefault()): String {
    if (bytes == null || bytes < 0) return ""
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var size = bytes.toDouble()
    var unit = 0
    while (size >= 1024 && unit < units.size - 1) {
        size /= 1024
        unit++
    }
    val format = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = if (unit == 0 || size >= 10) 0 else 1 }
    return "${format.format(size)} ${units[unit]}"
}

/** The type shown on a tile: the file's extension ("DOCX"), at most 4 letters, or nothing. */
fun typeLabel(name: String): String = Regex("\\.([A-Za-z0-9]{1,4})$").find(name)?.groupValues?.get(1)?.uppercase().orEmpty()

/** What the app shows itself: pictures in the viewer, PDFs page by page, videos and music in the player. */
fun opensInViewer(file: MediaFile) = file.kind == "image"
fun opensAsPdf(file: MediaFile) = file.kind == "pdf"
fun opensInPlayer(file: MediaFile) = file.kind == "video" || file.kind == "audio"

/** A selection of [names] may be deleted with write access; a folder only when the server says so (rights everywhere in it). */
fun canDeleteSelection(access: String, folders: List<MediaFolder>, names: Set<String>): Boolean =
    access == "write" && names.isNotEmpty() && folders.none { it.name in names && !it.canDelete }

// --- Folder rights ---

const val ALL_MEMBERS = "*"

/** A row of the rights sheet: [level] is the folder's own right ("" = follows what comes from above). */
data class RightsRow(val id: String, val name: String, val kind: String, val level: String, val inherited: MediaInherited?)

/** All members always, then every group and person with a right here or from above (in the server's order). */
fun rightsRows(choices: List<MediaChoice>, folder: MediaFolderRules?): List<RightsRow> {
    val own = folder?.own.orEmpty()
    val inherited = folder?.inherited.orEmpty()
    return choices.filter { it.id == ALL_MEMBERS || it.id in own || it.id in inherited }
        .map { RightsRow(it.id, it.name, it.kind, own[it.id].orEmpty(), inherited[it.id]) }
}

/** A row can be taken out when nothing comes from above for it (all members always stay). */
fun removableRow(row: RightsRow) = row.id != ALL_MEMBERS && row.inherited == null

private fun searchable(text: String) = Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()

/** Groups and people not in the sheet yet that match [query] (case and accents ignored), groups first. */
fun addableTargets(choices: List<MediaChoice>, rows: List<RightsRow>, query: String, limit: Int = 8): List<MediaChoice> {
    val shown = rows.map { it.id }.toSet()
    val words = searchable(query).split(Regex("\\s+")).filter { it.isNotEmpty() }
    return choices.asSequence()
        .filter { it.id != ALL_MEMBERS && it.id !in shown }
        .filter { choice -> words.all { searchable(choice.name).contains(it) } }
        .sortedBy { if (it.kind == "group") 0 else 1 }
        .take(limit)
        .toList()
}

/** The rights to save: only rows with a level of their own. */
fun rightsPayload(rows: List<RightsRow>): Map<String, String> = rows.filter { it.level.isNotEmpty() }.associate { it.id to it.level }
