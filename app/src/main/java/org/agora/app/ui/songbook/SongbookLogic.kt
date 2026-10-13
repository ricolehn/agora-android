package org.agora.app.ui.songbook

import java.text.Normalizer

// Songbook text and chords, ported from the web app (nova assets/songbook.js; pure functions, SongbookLogicTest).
//
// Songs are written ChordPro style: "[G]Amazing [D/F#]grace" puts a chord above the syllable it stands in front
// of, "# Chorus" (or {c: Chorus}, {start_of_chorus}) starts a section, "> twice" is a note for the musicians.
// Only real chords are transposed: "[Bridge]" or "[N.C.]" stay as they are. German songbooks write H for B and B
// for Bb; a song that uses H keeps that notation.

private val SHARPS = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
private val FLATS = listOf("C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B")
private val NATURAL = mapOf('C' to 0, 'D' to 2, 'E' to 4, 'F' to 5, 'G' to 7, 'A' to 9, 'B' to 11, 'H' to 11)

// Major keys written with flats (index of the tonic): F, Bb, Eb, Ab, Db; Gb/F# follows the original spelling
private val FLAT_MAJOR_KEYS = setOf(5, 10, 3, 8, 1)

// Root, accidental, quality / extensions, optional bass note. The quality only allows chord vocabulary, so words
// starting with A-H ("Bridge", "Chorus", "End") are not chords.
private val CHORD = Regex("^([A-H])(#|b)?((?:maj|min|dim|aug|sus|add|alt|no|m|M|Δ|°|ø|\\+|-|[0-9]|[#b](?=[0-9])|\\(|\\)|,|\\.)*)(?:/([A-H])(#|b)?)?$")
private val BRACKETED = Regex("\\[([^\\]]+)\\]")
private val DIRECTIVE_NAME = Regex("^[a-z_]{1,24}$")

data class ChordNote(val root: Char, val accidental: String)

/** The parts of a chord like "F#m7/C#". */
data class Chord(val root: Char, val accidental: String, val quality: String, val bass: ChordNote?)

/** The parts of a chord, or null if [text] is not a chord. */
fun parseChord(text: String?): Chord? {
    val value = text.orEmpty().trim()
    if (value.isEmpty() || value.length > 24) return null
    val match = CHORD.find(value) ?: return null
    val g = match.groupValues
    return Chord(
        root = g[1][0],
        accidental = g[2],
        quality = g[3],
        bass = if (g[4].isNotEmpty()) ChordNote(g[4][0], g[5]) else null
    )
}

/** Pitch class 0-11 of a note; in German notation B is Bb and H is B. */
private fun noteIndex(root: Char, accidental: String, german: Boolean): Int {
    val base = if (german && root == 'B') 10 else NATURAL.getValue(root)
    return (base + (if (accidental == "#") 1 else if (accidental == "b") -1 else 0) + 12) % 12
}

private fun noteName(index: Int, flats: Boolean, german: Boolean): String {
    val name = (if (flats) FLATS else SHARPS)[((index % 12) + 12) % 12]
    if (!german) return name
    // German: Bb is written B, B is written H
    if (name == "Bb" || name == "A#") return "B"
    if (name == "B") return "H"
    return name
}

/** Whether a song is written in German notation (it uses H). */
private fun isGerman(chords: List<String>): Boolean = chords.any { it.startsWith("H") || it.contains("/H") }

private data class SongKey(val index: Int, val minor: Boolean)

/** The key of a song: its "key" field, else the first chord (minor if it is one). */
private fun songKey(declared: String?, chords: List<String>, german: Boolean): SongKey? {
    val source = parseChord(declared) ?: chords.firstNotNullOfOrNull { parseChord(it) } ?: return null
    return SongKey(noteIndex(source.root, source.accidental, german), source.quality.startsWith("m") && !source.quality.startsWith("maj"))
}

/** Flats or sharps in the target key (minor keys follow their relative major). */
private fun prefersFlats(key: SongKey?, steps: Int, originalFlats: Boolean): Boolean {
    if (key == null) return originalFlats
    val tonic = (key.index + steps + 120) % 12
    val major = if (key.minor) (tonic + 3) % 12 else tonic
    if (major == 6) return originalFlats // F# / Gb: keep what the song used
    return major in FLAT_MAJOR_KEYS
}

/** How chords of one song are written at a given transposition. */
data class Spelling(val flats: Boolean = false, val german: Boolean = false)

/** One chord moved by [steps] semitones (unchanged for non-chords and steps 0). */
fun transposeChord(text: String, steps: Int, spelling: Spelling = Spelling()): String {
    if (steps == 0) return text
    val chord = parseChord(text) ?: return text
    val root = noteName(noteIndex(chord.root, chord.accidental, spelling.german) + steps, spelling.flats, spelling.german)
    val bass = chord.bass?.let { "/" + noteName(noteIndex(it.root, it.accidental, spelling.german) + steps, spelling.flats, spelling.german) } ?: ""
    return root + chord.quality + bass
}

private val FLAT_START = Regex("^[A-G]b")

/** Spelling rules for one song at [steps]. */
fun spellingFor(content: String?, declaredKey: String?, steps: Int): Spelling {
    val chords = BRACKETED.findAll(content.orEmpty()).map { it.groupValues[1] }.filter { parseChord(it) != null }.toList()
    val key = declaredKey.orEmpty()
    val german = isGerman(chords) || key.startsWith("H")
    val originalFlats = chords.any { FLAT_START.containsMatchIn(it) } || FLAT_START.containsMatchIn(key)
    return Spelling(flats = prefersFlats(songKey(declaredKey, chords, german), steps, originalFlats), german = german)
}

/** The key label shown above the song, transposed like its chords. */
fun transposedKey(declaredKey: String?, content: String?, steps: Int): String {
    if (declaredKey.isNullOrEmpty()) return ""
    return transposeChord(declaredKey, steps, spellingFor(content, declaredKey, steps))
}

/** A transposition step -6..5 after moving [steps] by [delta] semitones (like the song page of the web app). */
fun stepBy(steps: Int, delta: Int): Int = Math.floorMod(steps + delta + 6, 12) - 6

/** Semitones from one key to another (-6..5, like the song page), e.g. G → A = 2; null if one is not a key. */
fun keySteps(from: String?, to: String?): Int? {
    val a = parseChord(from) ?: return null
    val b = parseChord(to) ?: return null
    val diff = noteIndex(b.root, b.accidental, false) - noteIndex(a.root, a.accidental, false)
    return ((((diff + 18) % 12) + 12) % 12) - 6
}

// ---------- the song as lines ----------

/** One chord with the text that follows it; [chord] is null where the text continues without a new chord. */
data class Segment(val chord: String?, val text: String)

/** A line of a song. */
sealed interface SongLine {
    data object Gap : SongLine
    /** A section heading; [label] names a ChordPro section without its own text: chorus, verse, bridge, tab. */
    data class Section(val text: String, val label: String? = null) : SongLine
    data class Note(val text: String) : SongLine
    data class Text(val text: String) : SongLine
    /** Lyrics with chords: one inner list per word, so a line can wrap between words. */
    data class Chords(val words: List<List<Segment>>) : SongLine {
        /** A line of chords alone ("[G] [D] [Em]") has no lyric row. */
        val chordsOnly: Boolean get() = words.all { word -> word.all { it.text.isBlank() } }
    }
}

private data class Directive(val name: String, val value: String)

/** A ChordPro directive "{name: value}" without regular expressions on the whole line. */
private fun directive(line: String): Directive? {
    if (!line.startsWith("{") || !line.endsWith("}") || line.length < 2) return null
    val inner = line.substring(1, line.length - 1)
    val colon = inner.indexOf(':')
    val name = (if (colon < 0) inner else inner.substring(0, colon)).trim().lowercase()
    if (!DIRECTIVE_NAME.matches(name)) return null
    return Directive(name, if (colon < 0) "" else inner.substring(colon + 1).trim())
}

private val SECTION_DIRECTIVES = mapOf(
    "start_of_chorus" to "chorus", "soc" to "chorus",
    "start_of_verse" to "verse", "sov" to "verse",
    "start_of_bridge" to "bridge", "sob" to "bridge",
    "start_of_tab" to "tab", "sot" to "tab"
)
private val COMMENT_DIRECTIVES = setOf("c", "comment", "ci", "comment_italic")
private val SECTION_HASH = Regex("^#+\\s*")
private val NOTE_MARK = Regex("^>\\s*")
private val ONLY_BRACKET = Regex("^\\[([^\\]]+)\\]$")
private val ANY_BRACKET = Regex("\\[[^\\]]*\\]")
private val HAS_CHORD = Regex("\\[[^\\]]+\\]")
private val AFTER_SPACE = Regex("(?<=\\s)")

/** The song as lines (chords transposed by [steps]; without [chords] only the lyrics). */
fun parseSong(content: String?, steps: Int = 0, declaredKey: String? = "", chords: Boolean = true): List<SongLine> {
    val spelling = spellingFor(content, declaredKey, steps)
    val lines = mutableListOf<SongLine>()
    for (raw in content.orEmpty().split('\n')) {
        val line = raw.trimEnd()
        val trimmed = line.trim()
        if (trimmed.isEmpty()) {
            // At most one gap in a row
            if (lines.isNotEmpty() && lines.last() !is SongLine.Gap) lines += SongLine.Gap
            continue
        }
        if (trimmed.startsWith("#")) {
            lines += SongLine.Section(trimmed.replaceFirst(SECTION_HASH, ""))
            continue
        }
        val command = directive(trimmed)
        if (command != null) {
            when {
                command.name in COMMENT_DIRECTIVES -> lines += SongLine.Section(command.value)
                command.name in SECTION_DIRECTIVES -> lines += SongLine.Section(command.value, SECTION_DIRECTIVES.getValue(command.name))
            }
            // Everything else ({title}, {key}, {capo}, {end_of_chorus}, …) is not shown
            continue
        }
        if (trimmed.startsWith(">")) {
            lines += SongLine.Note(trimmed.replaceFirst(NOTE_MARK, ""))
            continue
        }
        // "[Bridge]" alone on a line is a section name, not a chord
        val bracketed = ONLY_BRACKET.find(trimmed)
        if (bracketed != null && parseChord(bracketed.groupValues[1]) == null) {
            lines += SongLine.Section(bracketed.groupValues[1].trim())
            continue
        }
        val hasChords = HAS_CHORD.containsMatchIn(line)
        val lyrics = line.replace(ANY_BRACKET, "")
        if (!hasChords || !chords) {
            // A line of chords only says nothing without its chords
            if (lyrics.isBlank()) continue
            lines += SongLine.Text(lyrics)
            continue
        }
        lines += SongLine.Chords(splitWords(line, steps, spelling))
    }
    while (lines.isNotEmpty() && lines.last() is SongLine.Gap) lines.removeAt(lines.lastIndex)
    return lines
}

/** "[G]Amazing [D]grace" → [[G "Amazing "], [D "grace"]]. */
private fun splitWords(line: String, steps: Int, spelling: Spelling): List<List<Segment>> {
    // The line in parts: chord markers and the text between them
    val parts = mutableListOf<Pair<Boolean, String>>()
    var last = 0
    for (match in HAS_CHORD.findAll(line)) {
        if (match.range.first > last) parts += false to line.substring(last, match.range.first)
        parts += true to match.value.substring(1, match.value.length - 1)
        last = match.range.last + 1
    }
    if (last < line.length) parts += false to line.substring(last)

    val segments = mutableListOf<Segment>()
    var pending: String? = null
    for ((isChord, value) in parts) {
        if (isChord) {
            if (pending != null) segments += Segment(pending, "")
            pending = transposeChord(value, steps, spelling)
        } else {
            segments += Segment(pending, value)
            pending = null
        }
    }
    if (pending != null) segments += Segment(pending, "")

    // Break segments after spaces; a word that continues across a chord ("Ama[G]zing") stays one word
    val words = mutableListOf<List<Segment>>()
    var word = mutableListOf<Segment>()
    for (segment in segments) {
        if (segment.text.isEmpty()) {
            word += Segment(segment.chord, "")
            continue
        }
        segment.text.split(AFTER_SPACE).filter { it.isNotEmpty() }.forEachIndexed { i, piece ->
            word += Segment(if (i == 0) segment.chord else null, piece)
            if (piece.last().isWhitespace()) {
                words += word
                word = mutableListOf()
            }
        }
    }
    if (word.isNotEmpty()) words += word
    return words
}

// ---------- search ----------

private val COMBINING = Regex("[\\u0300-\\u036f]")
private val SPACES = Regex("\\s+")
private val META_LINES = Regex("(?m)^\\s*[#>{].*$")

/** Lower case, no accents, single spaces. */
fun normalizeSearch(text: String?): String =
    Normalizer.normalize(text.orEmpty(), Normalizer.Form.NFD).replace(COMBINING, "").replace(SPACES, " ").lowercase()

/** Text to search in: title, writers, CCLI number and the lyrics without chords; lower case, no accents. */
fun searchText(title: String?, artist: String?, ccli: String?, copyright: String?, content: String?): String {
    val lyrics = content.orEmpty().replace(ANY_BRACKET, "").replace(META_LINES, " ")
    return normalizeSearch(listOf(title, artist, ccli, copyright, lyrics).filter { !it.isNullOrEmpty() }.joinToString(" "))
}
