package org.agora.app.ui.songbook

import org.agora.app.ui.components.FrostScaffold
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.agora.app.R
import org.agora.app.data.Song
import org.agora.app.ui.components.AgoraSnackbar
import org.agora.app.ui.components.LocalSnackbar
import org.agora.app.ui.components.SubPageHeader
import org.agora.app.ui.components.isTablet
import org.agora.app.ui.components.readingPadding
import org.agora.app.ui.components.softShadow
import org.agora.app.ui.theme.Agora

/** Labels of ChordPro sections without their own text ({start_of_chorus} …). */
@Composable
internal fun sectionLabel(label: String?): String = when (label) {
    "chorus" -> stringResource(R.string.songbook_label_chorus)
    "verse" -> stringResource(R.string.songbook_label_verse)
    "bridge" -> stringResource(R.string.songbook_label_bridge)
    "tab" -> stringResource(R.string.songbook_label_tab)
    else -> ""
}

/** Text size of a song at a reading step -2..5 (web: --song-size, 12 % per step; larger base on tablets). */
@Composable
internal fun songFontSize(step: Int): TextUnit = ((if (isTablet()) 19f else 16f) * (1f + step * 0.12f)).sp

/**
 * The song page of the web app: title and songwriter on top, the key as a chip (it shows the transposed key too),
 * the song with its chords above the syllables, CCLI notes after the last line, and a floating control pill:
 * semitone down / key (tap: back to the original key) / semitone up, chords on/off and text size.
 * The key starts as written whenever a song opens; chords and size are remembered.
 */
@Composable
fun SongPage(song: Song, license: String, canManage: Boolean, onBack: () -> Unit, onEdit: () -> Unit) {
    val context = LocalContext.current
    var steps by remember(song.id) { mutableIntStateOf(0) }
    var chords by remember { mutableStateOf(SongPrefs.chords(context)) }
    var size by remember { mutableIntStateOf(SongPrefs.size(context)) }
    fun store(newChords: Boolean = chords, newSize: Int = size) {
        chords = newChords
        size = newSize
        SongPrefs.save(context, newChords, newSize)
    }
    val key = transposedKey(song.key, song.content, steps)
    val shift = (if (steps > 0) "+" else "") + steps
    val lines = remember(song.content, song.key, steps, chords) { parseSong(song.content, steps, song.key, chords) }
    val ccli = listOfNotNull(
        license.takeIf { it.isNotBlank() }?.let { stringResource(R.string.songbook_license, it) },
        song.ccli.takeIf { it.isNotBlank() }?.let { stringResource(R.string.songbook_ccli_song_short, it) },
        song.copyright.takeIf { it.isNotBlank() }?.let { "© " + it.removePrefix("©").trim() }
    )

    FrostScaffold(
        topBar = {
            SubPageHeader(song.title, onBack, subtitle = song.artist.takeIf { it.isNotBlank() }) {
                if (canManage) IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, stringResource(R.string.songbook_edit), tint = Agora.colors.heading) }
            }
        },
        snackbarHost = { SnackbarHost(LocalSnackbar.current) { AgoraSnackbar(it) } }
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = readingPadding(padding.calculateTopPadding() + 4.dp, padding.calculateBottomPadding() + 110.dp)
            ) {
                // The key at a glance; a transposed key stays visible here too
                val fact = when {
                    steps != 0 -> if (key.isNotEmpty()) "KEY_SHIFTED" else "TRANSPOSED"
                    song.key.isNotBlank() -> "KEY"
                    else -> null
                }
                if (fact != null) item(key = "fact") {
                    val text = when (fact) {
                        "KEY_SHIFTED" -> stringResource(R.string.songbook_key_shifted, key, shift)
                        "TRANSPOSED" -> stringResource(R.string.songbook_transposed, shift)
                        else -> stringResource(R.string.songbook_key_short, song.key)
                    }
                    KeyChip(text, shifted = steps != 0, modifier = Modifier.padding(bottom = 14.dp))
                }
                itemsIndexed(lines) { index, line -> SongLineView(line, songFontSize(size), first = index == 0) }
                if (ccli.isNotEmpty()) item(key = "ccli") {
                    Column(Modifier.fillMaxWidth().padding(top = 28.dp)) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        ccli.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp)) }
                    }
                }
            }
            SongControls(
                keyLabel = key.ifEmpty { if (steps != 0) shift else "♪" },
                shifted = steps != 0,
                chords = chords,
                size = size,
                onStep = { steps = stepBy(steps, it) },
                onReset = { steps = 0 },
                onChords = { store(newChords = !chords) },
                onSize = { store(newSize = (size + it).coerceIn(-2, 5)) },
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 16.dp)
            )
        }
    }
}

/** The key as a chip (web: .song-fact); filled with the brand colour while transposed. */
@Composable
private fun KeyChip(text: String, shifted: Boolean, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
        color = if (shifted) Color.White else MaterialTheme.colorScheme.primary,
        modifier = modifier.clip(CircleShape)
            .then(if (shifted) Modifier.background(Agora.colors.brandGradient) else Modifier.background(Agora.colors.brand.copy(alpha = 0.12f)))
            .padding(horizontal = 12.dp, vertical = 4.dp)
    )
}

/** Floating control pill at the bottom of the song page. */
@Composable
private fun SongControls(
    keyLabel: String,
    shifted: Boolean,
    chords: Boolean,
    size: Int,
    onStep: (Int) -> Unit,
    onReset: () -> Unit,
    onChords: () -> Unit,
    onSize: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, Agora.colors.hairline),
        modifier = modifier.softShadow(Agora.colors.shadow.copy(alpha = 0.16f), blur = 14.dp, shape = CircleShape, offsetY = 4.dp)
    ) {
        Row(Modifier.padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onStep(-1) }) { Icon(Icons.Outlined.Remove, stringResource(R.string.songbook_down), tint = Agora.colors.heading) }
            // The key; while transposed a tap goes back to the original key
            TextButton(onClick = onReset, enabled = shifted) {
                Text(
                    keyLabel,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (shifted) MaterialTheme.colorScheme.primary else Agora.colors.heading,
                    modifier = Modifier.width(40.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
            IconButton(onClick = { onStep(1) }) { Icon(Icons.Outlined.Add, stringResource(R.string.songbook_up), tint = Agora.colors.heading) }
            Box(Modifier.padding(horizontal = 4.dp).size(width = 1.dp, height = 24.dp).background(MaterialTheme.colorScheme.outlineVariant))
            IconToggleButton(checked = chords, onCheckedChange = { onChords() }) {
                Icon(
                    Icons.Outlined.MusicNote, stringResource(R.string.songbook_show_chords),
                    tint = if (chords) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
            TextButton(onClick = { onSize(-1) }, enabled = size > -2) {
                Text("A−", style = MaterialTheme.typography.labelLarge, color = Agora.colors.heading)
            }
            TextButton(onClick = { onSize(1) }, enabled = size < 5) {
                Text("A+", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Agora.colors.heading)
            }
        }
    }
}

/**
 * One line of a song like the web app (song-line / song-seg): chords sit above the syllable they stand in front of,
 * a line wraps between words, sections are small capitals in the brand colour, notes in italics.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SongLineView(line: SongLine, fontSize: TextUnit, first: Boolean = false) {
    val lyric = TextStyle(fontSize = fontSize, lineHeight = fontSize * 1.5f, color = Agora.colors.heading, fontFamily = MaterialTheme.typography.bodyLarge.fontFamily)
    when (line) {
        SongLine.Gap -> Spacer(Modifier.height(with(LocalDensity.current) { (fontSize * 0.9f).toDp() }))
        is SongLine.Section -> Text(
            line.text.ifBlank { sectionLabel(line.label) }.uppercase(),
            style = TextStyle(fontSize = fontSize * 0.78f, fontWeight = FontWeight.Bold, letterSpacing = 0.08.em, fontFamily = lyric.fontFamily),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = if (first) 0.dp else 18.dp, bottom = 6.dp)
        )
        is SongLine.Note -> Text(line.text, style = lyric.copy(fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant), modifier = Modifier.padding(vertical = 4.dp))
        is SongLine.Text -> Text(line.text, style = lyric)
        is SongLine.Chords -> {
            val chordStyle = TextStyle(fontSize = fontSize * 0.86f, lineHeight = fontSize * 1.2f, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary, fontFamily = lyric.fontFamily)
            val wordGap = with(LocalDensity.current) { (fontSize * 0.3f).toDp() }
            val chordGap = with(LocalDensity.current) { (fontSize * 0.3f).toDp() }
            FlowRow(
                Modifier.fillMaxWidth().padding(top = with(LocalDensity.current) { (fontSize * 0.15f).toDp() }),
                horizontalArrangement = Arrangement.spacedBy(if (line.chordsOnly) wordGap * 2.5f else wordGap),
                itemVerticalAlignment = Alignment.Bottom
            ) {
                line.words.forEach { word ->
                    Row(verticalAlignment = Alignment.Bottom) {
                        word.forEachIndexed { i, seg ->
                            Column {
                                Text(seg.chord.orEmpty(), style = chordStyle, softWrap = false, maxLines = 1,
                                    modifier = Modifier.padding(end = if (seg.chord != null) chordGap else 0.dp))
                                // Trailing spaces end a word; the gap between words comes from the row
                                if (!line.chordsOnly) Text(if (i == word.lastIndex) seg.text.trimEnd() else seg.text, style = lyric, softWrap = false, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}
