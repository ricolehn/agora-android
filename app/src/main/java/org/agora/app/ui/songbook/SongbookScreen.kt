package org.agora.app.ui.songbook

import org.agora.app.ui.components.FrostScaffold
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.agora.app.AppContainer
import org.agora.app.R
import org.agora.app.data.Song
import org.agora.app.data.remote.ApiException
import org.agora.app.data.songbook
import org.agora.app.ui.components.AgoraFabMenu
import org.agora.app.ui.components.AgoraSnackbar
import org.agora.app.ui.components.EmptyState
import org.agora.app.ui.components.FabAction
import org.agora.app.ui.components.IconTile
import org.agora.app.ui.components.LoadingBox
import org.agora.app.ui.components.LocalSnackbar
import org.agora.app.ui.components.SearchField
import org.agora.app.ui.components.SecondaryButton
import org.agora.app.ui.components.SubPageHeader
import org.agora.app.ui.components.containerViewModel
import org.agora.app.ui.components.readingPadding
import org.agora.app.ui.components.softShadow
import org.agora.app.ui.theme.Agora
import java.text.Collator

/** A song with its prepared search text (title, writers, CCLI, lyrics; lower case, no accents). */
data class IndexedSong(val song: Song, val search: String)

private fun Song.indexed() = IndexedSong(this, searchText(title, artist, ccli, copyright, content))

data class SongbookState(
    val loaded: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    val songs: List<IndexedSong> = emptyList(),
    val license: String = "",
    val canManage: Boolean = false
)

/** The songbook in memory; loaded when the page opens, kept up to date after saving or deleting. */
class SongbookViewModel(private val c: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(SongbookState())
    val state = _state.asStateFlow()
    private val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }

    fun load(fallbackError: String) {
        if (_state.value.loading) return
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            try {
                val book = c.repo.songbook()
                _state.value = SongbookState(loaded = true, songs = book.songs.map { it.indexed() }, license = book.ccliLicense, canManage = book.canManage)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = (e as? ApiException)?.message?.takeIf { m -> m.isNotBlank() } ?: fallbackError) }
            }
        }
    }

    fun song(id: String?): Song? = _state.value.songs.firstOrNull { it.song.id == id }?.song

    fun upsert(song: Song) = _state.update { s ->
        s.copy(songs = (s.songs.filter { it.song.id != song.id } + song.indexed()).sortedWith { a, b -> collator.compare(a.song.title, b.song.title) })
    }

    fun remove(id: String) = _state.update { s -> s.copy(songs = s.songs.filter { it.song.id != id }) }
}

/** Which page of the songbook is on screen: the list, a song, or the editor (song id or [NEW_SONG]). */
private sealed interface SongbookPage {
    data object List : SongbookPage
    data class Song(val id: String) : SongbookPage
    data class Edit(val id: String) : SongbookPage
}

private const val NEW_SONG = "new"

/**
 * The songbook of the web app (Extras → Liederbuch): searchable alphabetical list, a song page with chords,
 * transposing and text size, and the editor for members who may manage the songbook. Everything below this one
 * entry point navigates internally (back goes editor → song → list → [onBack]).
 */
@Composable
fun SongbookScreen(onBack: () -> Unit) {
    val vm = containerViewModel { SongbookViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val loadFailed = stringResource(R.string.songbook_load_failed)
    LaunchedEffect(Unit) { if (!state.loaded) vm.load(loadFailed) }

    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var editId by rememberSaveable { mutableStateOf<String?>(null) }
    val page: SongbookPage = when {
        editId != null -> SongbookPage.Edit(editId!!)
        openId != null && vm.song(openId) != null -> SongbookPage.Song(openId!!)
        else -> SongbookPage.List
    }
    BackHandler(page is SongbookPage.Song) { openId = null }

    AnimatedContent(
        targetState = page,
        transitionSpec = {
            val deeper = when (initialState) {
                SongbookPage.List -> targetState != SongbookPage.List
                is SongbookPage.Song -> targetState is SongbookPage.Edit
                is SongbookPage.Edit -> false
            }
            if (deeper) (slideInHorizontally { it } + fadeIn()) togetherWith (slideOutHorizontally { -it / 4 } + fadeOut())
            else (slideInHorizontally { -it / 4 } + fadeIn()) togetherWith (slideOutHorizontally { it } + fadeOut())
        },
        contentKey = { it::class },
        label = "songbook-page"
    ) { current ->
        when (current) {
            SongbookPage.List -> SongList(
                state = state,
                onBack = onBack,
                onRetry = { vm.load(loadFailed) },
                onOpen = { openId = it },
                onAdd = { editId = NEW_SONG }
            )
            is SongbookPage.Song -> vm.song(current.id)?.let { song ->
                SongPage(
                    song = song,
                    license = state.license,
                    canManage = state.canManage,
                    onBack = { openId = null },
                    onEdit = { editId = song.id }
                )
            }
            is SongbookPage.Edit -> SongEditor(
                song = if (current.id == NEW_SONG) null else vm.song(current.id),
                licenseMissing = state.license.isBlank(),
                onClose = { editId = null },
                onSaved = { saved -> vm.upsert(saved); editId = null },
                onDeleted = { id -> vm.remove(id); editId = null; if (openId == id) openId = null }
            )
        }
    }
}

@Composable
private fun SongList(state: SongbookState, onBack: () -> Unit, onRetry: () -> Unit, onOpen: (String) -> Unit, onAdd: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val needle = remember(query) { normalizeSearch(query.trim()) }
    val list = remember(state.songs, needle) { if (needle.isEmpty()) state.songs else state.songs.filter { it.search.contains(needle) } }
    FrostScaffold(
        topBar = { SubPageHeader(stringResource(R.string.songbook_title), onBack) },
        snackbarHost = { SnackbarHost(LocalSnackbar.current) { AgoraSnackbar(it) } },
        floatingActionButton = {
            if (state.canManage) AgoraFabMenu(listOf(FabAction(stringResource(R.string.songbook_add), Icons.Outlined.MusicNote, onAdd)), stringResource(R.string.songbook_add))
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = readingPadding(padding.calculateTopPadding() + 4.dp, padding.calculateBottomPadding() + 96.dp)
        ) {
            item(key = "search") {
                SearchField(query, { query = it }, stringResource(R.string.songbook_search), Modifier.padding(bottom = 14.dp))
            }
            when {
                !state.loaded && state.error != null -> item(key = "error") {
                    EmptyState(Icons.Outlined.MusicNote, state.error.orEmpty(), modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
                        SecondaryButton(onClick = onRetry, modifier = Modifier.padding(top = 12.dp)) { Text(stringResource(R.string.songbook_retry)) }
                    }
                }
                !state.loaded -> item(key = "loading") { LoadingBox(Modifier.fillMaxWidth().height(200.dp)) }
                list.isEmpty() -> item(key = "empty") {
                    EmptyState(
                        Icons.Outlined.MusicNote,
                        stringResource(if (needle.isNotEmpty()) R.string.songbook_no_match else R.string.songbook_empty),
                        if (state.canManage && needle.isEmpty()) stringResource(R.string.songbook_empty_hint) else null,
                        modifier = Modifier.fillMaxWidth().padding(top = 24.dp)
                    )
                }
                else -> {
                    // Alphabetical list in one card, a letter above each new initial (only without a search)
                    val rows = buildList {
                        var letter: Char? = null
                        list.forEach { item ->
                            val initial = item.song.title.firstOrNull()?.uppercaseChar() ?: '#'
                            if (needle.isEmpty() && initial != letter) add(SongRow.Letter(initial.toString()))
                            letter = initial
                            add(SongRow.Entry(item.song))
                        }
                    }
                    itemsIndexed(rows, key = { _, row -> row.key }) { index, row ->
                        val shape = groupShape(first = index == 0, last = index == rows.lastIndex)
                        Column(
                            Modifier.fillMaxWidth()
                                .then(if (index == 0) Modifier.softShadow(Agora.colors.shadow.copy(alpha = 0.05f), blur = 4.dp, shape = shape, offsetY = 1.dp) else Modifier)
                                .clip(shape)
                                .background(MaterialTheme.colorScheme.surface)
                        ) {
                            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            when (row) {
                                is SongRow.Letter -> Text(
                                    row.letter,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.fillMaxWidth().background(Agora.colors.surfaceAlt).padding(start = 16.dp, end = 16.dp, top = 9.dp, bottom = 4.dp)
                                )
                                is SongRow.Entry -> SongListRow(row.song) { onOpen(row.song.id) }
                            }
                        }
                    }
                    item(key = "license") {
                        if (state.license.isNotBlank()) Text(
                            stringResource(R.string.songbook_license, state.license),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = 14.dp)
                        ) else if (state.canManage) Text(
                            stringResource(R.string.songbook_license_missing),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = 14.dp, start = 8.dp, end = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

private sealed interface SongRow {
    val key: String
    data class Letter(val letter: String) : SongRow { override val key get() = "letter-$letter" }
    data class Entry(val song: Song) : SongRow { override val key get() = "song-${song.id}" }
}

/** Rows of one card: the first rounds the top, the last the bottom. */
private fun groupShape(first: Boolean, last: Boolean): Shape = RoundedCornerShape(
    topStart = if (first) 18.dp else 0.dp, topEnd = if (first) 18.dp else 0.dp,
    bottomStart = if (last) 18.dp else 0.dp, bottomEnd = if (last) 18.dp else 0.dp
)

@Composable
private fun SongListRow(song: Song, onClick: () -> Unit) {
    val keyLabel = if (song.key.isNotBlank()) stringResource(R.string.songbook_key_short, song.key) else ""
    val meta = listOf(song.artist, keyLabel).filter { it.isNotBlank() }.joinToString(" · ")
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconTile(Icons.Outlined.MusicNote, Agora.colors.brand, size = 38.dp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(song.title, style = MaterialTheme.typography.titleSmall, color = Agora.colors.heading, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
    }
}

/** Remembered reading settings: chords on/off and text size stay as the reader left them (the key does not). */
internal object SongPrefs {
    private const val FILE = "songbook_view"
    fun chords(context: android.content.Context) = context.getSharedPreferences(FILE, android.content.Context.MODE_PRIVATE).getBoolean("chords", true)
    fun size(context: android.content.Context) = context.getSharedPreferences(FILE, android.content.Context.MODE_PRIVATE).getInt("size", 0)
    fun save(context: android.content.Context, chords: Boolean, size: Int) =
        context.getSharedPreferences(FILE, android.content.Context.MODE_PRIVATE).edit().putBoolean("chords", chords).putInt("size", size).apply()
}
