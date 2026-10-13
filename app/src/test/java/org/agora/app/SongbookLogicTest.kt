package org.agora.app

import org.agora.app.ui.songbook.Segment
import org.agora.app.ui.songbook.SongLine
import org.agora.app.ui.songbook.keySteps
import org.agora.app.ui.songbook.normalizeSearch
import org.agora.app.ui.songbook.parseChord
import org.agora.app.ui.songbook.parseSong
import org.agora.app.ui.songbook.searchText
import org.agora.app.ui.songbook.spellingFor
import org.agora.app.ui.songbook.stepBy
import org.agora.app.ui.songbook.transposeChord
import org.agora.app.ui.songbook.transposedKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The songbook logic ported from the web app (nova assets/songbook.js, tests/frontend.test.js). */
class SongbookLogicTest {
    @Test
    fun `chords are recognised, words are not`() {
        listOf("G", "F#m7", "Bbmaj7", "D/F#", "Csus4", "E7(b9)", "Am/G", "H7", "C°", "Gadd9").forEach { assertNotNull(it, parseChord(it)) }
        listOf("Bridge", "Chorus", "End", "N.C.", "", "X").forEach { assertNull(it, parseChord(it)) }
        val chord = parseChord("F#m7/C#")!!
        assertEquals('F', chord.root)
        assertEquals("#", chord.accidental)
        assertEquals("m7", chord.quality)
        assertEquals('C', chord.bass!!.root)
    }

    @Test
    fun `transposing keeps quality and bass and spells by key`() {
        assertEquals("A", transposeChord("G", 2))
        assertEquals("Am7/E", transposeChord("Gm7/D", 2))
        assertEquals("G", transposeChord("G", 0))
        assertEquals("Bridge", transposeChord("Bridge", 3))
        // F major is written with flats
        val song = "[F]Amazing [Bb]grace"
        assertEquals("Bb", transposeChord("F", 5, spellingFor(song, "F", 5)))
        assertEquals("Eb", transposeChord("Bb", 5, spellingFor(song, "F", 5)))
        // German notation keeps H and writes Bb as B
        val german = "[H]Großer [E]Gott"
        assertEquals("C", transposeChord("H", 1, spellingFor(german, "H", 1)))
        assertEquals("H", transposeChord("B", 1, spellingFor(german, "", 1)))
    }

    @Test
    fun `transposed key and steps`() {
        assertEquals("A", transposedKey("G", "[G]Amazing grace", 2))
        assertEquals("", transposedKey("", "[G]x", 2))
        assertEquals(2, keySteps("G", "A"))
        assertEquals(-2, keySteps("A", "G"))
        assertEquals(-6, keySteps("C", "F#"))
        assertEquals(5, keySteps("C", "F"))
        assertNull(keySteps("C", "Bridge"))
        assertEquals(-6, stepBy(5, 1))
        assertEquals(5, stepBy(-6, -1))
        assertEquals(1, stepBy(0, 1))
    }

    @Test
    fun `a song becomes sections, notes, gaps and chord lines`() {
        val song = "# Verse 1\n[G]Amazing [D/F#]grace\n\n\n[Bridge]\n{capo: 2}\n{c: Chorus}\n{soc}\n> twice\n[G] [D]\nOnly text"
        val lines = parseSong(song)
        assertEquals(SongLine.Section("Verse 1"), lines[0])
        val chords = lines[1] as SongLine.Chords
        assertEquals(listOf(listOf(Segment("G", "Amazing ")), listOf(Segment("D/F#", "grace"))), chords.words)
        assertEquals(SongLine.Gap, lines[2])
        assertEquals(SongLine.Section("Bridge"), lines[3])
        assertEquals(SongLine.Section("Chorus"), lines[4])
        assertEquals(SongLine.Section("", "chorus"), lines[5])
        assertEquals(SongLine.Note("twice"), lines[6])
        assertTrue((lines[7] as SongLine.Chords).chordsOnly)
        assertEquals(SongLine.Text("Only text"), lines[8])
        assertEquals(9, lines.size)
    }

    @Test
    fun `chords can be hidden and transposed while parsing`() {
        val song = "[G]Amazing [D]grace\n[G] [D]"
        assertEquals(listOf(SongLine.Text("Amazing grace")), parseSong(song, chords = false))
        val words = (parseSong(song, steps = 2)[0] as SongLine.Chords).words
        assertEquals("A", words[0][0].chord)
        assertEquals("E", words[1][0].chord)
    }

    @Test
    fun `a word that continues across a chord stays one word`() {
        val words = (parseSong("Ama[G]zing grace")[0] as SongLine.Chords).words
        assertEquals(listOf(Segment(null, "Ama"), Segment("G", "zing ")), words[0])
        assertEquals(listOf(Segment(null, "grace")), words[1])
    }

    @Test
    fun `search ignores case, accents and chords`() {
        val text = searchText("Großer Gott", "Ignaz Franz", "123", "", "# Refrain\n[G]Wir loben [D]dich")
        assertTrue(text.contains("wir loben dich"))
        assertTrue(text.contains("123"))
        assertFalse(text.contains("[g]"))
        assertFalse(text.contains("refrain"))
        assertEquals("creme brulee", normalizeSearch("Crème  Brûlée"))
    }
}
