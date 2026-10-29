package org.agora.app

import kotlinx.serialization.json.Json
import org.agora.app.data.MediaChoice
import org.agora.app.data.MediaFile
import org.agora.app.data.MediaFolder
import org.agora.app.data.MediaFolderRules
import org.agora.app.data.MediaInherited
import org.agora.app.data.MediaListing
import org.agora.app.data.MediaRules
import org.agora.app.ui.ai.splitThinkingFull
import org.agora.app.ui.media.ALL_MEMBERS
import org.agora.app.ui.media.MediaSort
import org.agora.app.ui.media.MediaTransfer
import org.agora.app.ui.media.RightsRow
import org.agora.app.ui.media.addableTargets
import org.agora.app.ui.media.breadcrumb
import org.agora.app.ui.media.canDeleteSelection
import org.agora.app.ui.media.compareNames
import org.agora.app.ui.media.formatSize
import org.agora.app.ui.media.parentPath
import org.agora.app.ui.media.removableRow
import org.agora.app.ui.media.rightsPayload
import org.agora.app.ui.media.rightsRows
import org.agora.app.ui.media.sortFiles
import org.agora.app.ui.media.sortFolders
import org.agora.app.ui.media.thumbSize
import org.agora.app.ui.media.typeLabel
import org.agora.app.ui.media.viewerSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/** The media library's helpers, ported from the web app (assets/media.js, tests/media.test.js), and its answers. */
class MediaLogicTest {
    @Test
    fun `paths and the breadcrumb`() {
        assertEquals(listOf("Photos" to "Photos", "2026" to "Photos/2026"), breadcrumb("Photos/2026"))
        assertEquals(emptyList<Pair<String, String>>(), breadcrumb(""))
        assertEquals("Photos", parentPath("Photos/2026"))
        assertEquals("", parentPath("Photos"))
        assertEquals("", parentPath(""))
    }

    @Test
    fun `previews come in the server's sizes`() {
        assertEquals(256, thumbSize(180))
        assertEquals(512, thumbSize(2000))
        assertEquals(2048, viewerSize(1440, 3120))
        assertEquals(1024, viewerSize(720, 1000))
    }

    @Test
    fun `names sort with numbers counted, newest first by date`() {
        val names = listOf("Bild 10", "bild 2", "Bild 1", "Album")
        assertEquals(listOf("Album", "Bild 1", "bild 2", "Bild 10"), names.sortedWith { a, b -> compareNames(a, b) })
        val files = listOf(MediaFile(name = "a.jpg", modified = "2026-01-01T00:00:00.000Z"), MediaFile(name = "b.jpg", modified = "2026-10-01T00:00:00.000Z"))
        assertEquals("b.jpg", sortFiles(files, MediaSort.NEWEST).first().name)
        assertEquals("a.jpg", sortFiles(files, MediaSort.NAME).first().name)
        val folders = listOf(MediaFolder(name = "2025", modified = "2025-12-01T00:00:00.000Z"), MediaFolder(name = "2026", modified = "2026-03-01T00:00:00.000Z"))
        assertEquals("2026", sortFolders(folders, MediaSort.NEWEST).first().name)
    }

    @Test
    fun `sizes and type labels`() {
        assertEquals("512 B", formatSize(512, Locale.ENGLISH))
        assertEquals("1.4 MB", formatSize(1_468_006, Locale.ENGLISH))
        assertEquals("1,4 MB", formatSize(1_468_006, Locale.GERMAN))
        assertEquals("12 GB", formatSize(12L * 1024 * 1024 * 1024, Locale.ENGLISH))
        assertEquals("", formatSize(null))
        assertEquals("DOCX", typeLabel("Plan.docx"))
        assertEquals("JPG", typeLabel("IMG_1.jpg"))
        assertEquals("", typeLabel("README"))
        assertEquals("", typeLabel("archive.backup"))
    }

    @Test
    fun `a selection is deleted only with write access and rights in every folder`() {
        val folders = listOf(MediaFolder(name = "Open", canDelete = true), MediaFolder(name = "Locked", canDelete = false))
        assertTrue(canDeleteSelection("write", folders, setOf("Open", "x.jpg")))
        assertFalse(canDeleteSelection("write", folders, setOf("Locked")))
        assertFalse(canDeleteSelection("read", folders, setOf("x.jpg")))
        assertFalse(canDeleteSelection("write", folders, emptySet()))
    }

    private val choices = listOf(
        MediaChoice(ALL_MEMBERS, "", "all"),
        MediaChoice("g1", "Jugend", "group"),
        MediaChoice("g2", "Technik", "group"),
        MediaChoice("user:u1", "Zoë Müller", "person")
    )

    @Test
    fun `rights rows show all members, own rights and rights from above`() {
        val folder = MediaFolderRules(
            path = "Fotos/2026",
            own = mapOf("g2" to "write"),
            inherited = mapOf(ALL_MEMBERS to MediaInherited("read", "Fotos"), "g1" to MediaInherited("hide", "Fotos"))
        )
        val rows = rightsRows(choices, folder)
        assertEquals(listOf(ALL_MEMBERS, "g1", "g2"), rows.map { it.id })
        assertEquals("", rows[0].level)
        assertEquals("read", rows[0].inherited?.level)
        assertFalse(removableRow(rows[0]))
        assertFalse(removableRow(rows[1]))
        assertTrue(removableRow(rows[2]))
        assertEquals(mapOf("g2" to "write"), rightsPayload(rows))
        // Without any rule: all members only
        assertEquals(listOf(ALL_MEMBERS), rightsRows(choices, null).map { it.id })
    }

    @Test
    fun `adding searches the others, accents and case aside, groups first`() {
        val rows = listOf(RightsRow(ALL_MEMBERS, "", "all", "", null), RightsRow("g1", "Jugend", "group", "read", null))
        assertEquals(listOf("g2", "user:u1"), addableTargets(choices, rows, "").map { it.id })
        assertEquals(listOf("user:u1"), addableTargets(choices, rows, "zoe mull").map { it.id })
        assertEquals(emptyList<String>(), addableTargets(choices, rows, "jugend").map { it.id })
    }

    @Test
    fun `the server's answers are read`() {
        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true; explicitNulls = false }
        val listing = json.decodeFromString(MediaListing.serializer(), """
            {"path":"Fotos","name":"Fotos","access":"write","canManage":true,
             "folders":[{"name":"2026","size":2048,"modified":"2026-10-01T10:00:00.000Z","path":"Fotos/2026","access":"read","canDelete":false,"hasRules":true,"hiddenFor":["g1"]}],
             "files":[{"name":"a.jpg","size":12345,"modified":null,"contentType":"image/jpeg","etag":"abc","hasPreview":true,"path":"Fotos/a.jpg","kind":"image"}]}
        """.trimIndent())
        assertEquals("write", listing.access)
        assertTrue(listing.folders.single().hasRules)
        assertEquals(listOf("g1"), listing.folders.single().hiddenFor)
        assertNull(listing.files.single().modified)
        assertEquals("image", listing.files.single().kind)
        val rules = json.decodeFromString(MediaRules.serializer(), """
            {"choices":[{"id":"*","name":"","kind":"all"}],"rules":[{"path":"","groups":{"*":"read"}}],
             "folder":{"path":"","own":{"*":"read"},"inherited":{}}}
        """.trimIndent())
        assertEquals("read", rules.folder?.own?.get(ALL_MEMBERS))
    }

    @Test
    fun `transfers say how far they are`() {
        assertNull(MediaTransfer(upload = false).percent)
        assertEquals(50, MediaTransfer(upload = true, done = 5, total = 10).percent)
        assertFalse(MediaTransfer(upload = true, done = 5, total = 10).storing)
        assertTrue(MediaTransfer(upload = true, done = 10, total = 10).storing)
        assertFalse(MediaTransfer(upload = false, done = 10, total = 10).storing)
    }

    @Test
    fun `thoughts are split from the answer while it streams`() {
        val streaming = splitThinkingFull("<think>plan", "")
        assertTrue(streaming.open)
        assertEquals("", streaming.answer)
        val partial = splitThinkingFull("Hallo <thi")
        assertEquals("Hallo", partial.answer)
        val done = splitThinkingFull("<think>plan</think>Antwort", "first")
        assertFalse(done.open)
        assertEquals("Antwort", done.answer)
        assertEquals("first\n\nplan", done.thinking)
    }
}
