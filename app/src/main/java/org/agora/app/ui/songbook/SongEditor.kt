package org.agora.app.ui.songbook

import org.agora.app.ui.components.frosted
import org.agora.app.ui.components.FrostScaffold
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.agora.app.R
import org.agora.app.data.Song
import org.agora.app.data.SongInput
import org.agora.app.data.deleteSong
import org.agora.app.data.saveSong
import org.agora.app.ui.components.AgoraCard
import org.agora.app.ui.components.AgoraSnackbar
import org.agora.app.ui.components.AgoraTextField
import org.agora.app.ui.components.ButtonLabel
import org.agora.app.ui.components.ButtonProgress
import org.agora.app.ui.components.CapsLabel
import org.agora.app.ui.components.ConfirmSheet
import org.agora.app.ui.components.LocalContainer
import org.agora.app.ui.components.LocalSnackbar
import org.agora.app.ui.components.PrimaryButton
import org.agora.app.ui.components.SubPageHeader
import org.agora.app.ui.components.readingPadding
import org.agora.app.ui.components.rememberActionRunner
import org.agora.app.ui.theme.Agora

/**
 * Editor of the web app (a page like "New poll"): title, songwriter and key, the text with chords and a live preview,
 * CCLI number and copyright. Capo and tempo are no longer edited; a song keeps the values it already has.
 * Leaving with changes asks first; deleting asks too.
 */
@Composable
fun SongEditor(song: Song?, licenseMissing: Boolean, onClose: () -> Unit, onSaved: (Song) -> Unit, onDeleted: (String) -> Unit) {
    val container = LocalContainer.current
    val runner = rememberActionRunner()
    var title by rememberSaveable { mutableStateOf(song?.title.orEmpty()) }
    var artist by rememberSaveable { mutableStateOf(song?.artist.orEmpty()) }
    var key by rememberSaveable { mutableStateOf(song?.key.orEmpty()) }
    var content by rememberSaveable { mutableStateOf(song?.content.orEmpty()) }
    var ccli by rememberSaveable { mutableStateOf(song?.ccli.orEmpty()) }
    var copyright by rememberSaveable { mutableStateOf(song?.copyright.orEmpty()) }
    val original = remember(song?.id) { listOf(song?.title.orEmpty(), song?.artist.orEmpty(), song?.key.orEmpty(), song?.content.orEmpty(), song?.ccli.orEmpty(), song?.copyright.orEmpty()) }
    val changed = listOf(title, artist, key, content, ccli, copyright) != original
    var confirmDiscard by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val leave: () -> Unit = { if (changed) { confirmDiscard = true } else onClose() }
    BackHandler { leave() }

    val savedMsg = stringResource(R.string.songbook_saved)
    val deletedMsg = stringResource(R.string.songbook_deleted)
    val preview = remember(content, key) { parseSong(content, 0, key, true) }

    FrostScaffold(
        topBar = {
            SubPageHeader(stringResource(if (song == null) R.string.songbook_new else R.string.songbook_edit), onBack = leave) {
                if (song != null) IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.delete), tint = Agora.colors.danger)
                }
            }
        },
        bottomBar = {
            Surface(color = androidx.compose.ui.graphics.Color.Transparent, modifier = Modifier.frosted()) {
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    PrimaryButton(
                        onClick = {
                            runner.run(savedMsg) {
                                val saved = container.repo.saveSong(
                                    song?.id,
                                    SongInput(title, artist, key, ccli, copyright, content, capo = song?.capo, tempo = song?.tempo)
                                )
                                onSaved(saved)
                            }
                        },
                        enabled = title.isNotBlank() && content.isNotBlank() && !runner.busy,
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 20.dp, vertical = 12.dp).height(48.dp)
                    ) {
                        if (runner.busy) ButtonProgress() else ButtonLabel(stringResource(R.string.save), Icons.Outlined.Check)
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(LocalSnackbar.current) { AgoraSnackbar(it) } }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(readingPadding(padding.calculateTopPadding() + 4.dp, padding.calculateBottomPadding() + 24.dp)),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CapsLabel(stringResource(R.string.songbook_song))
            AgoraTextField(title, { title = it }, label = { Text(stringResource(R.string.songbook_title_ph)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AgoraTextField(artist, { artist = it }, label = { Text(stringResource(R.string.songbook_artist_ph)) }, singleLine = true, modifier = Modifier.weight(1f))
                AgoraTextField(
                    key, { key = it.take(4) }, label = { Text(stringResource(R.string.songbook_key_ph)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.width(132.dp)
                )
            }

            CapsLabel(stringResource(R.string.songbook_text), Modifier.padding(top = 8.dp))
            AgoraTextField(
                content, { content = it }, minLines = 10, modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 14.sp)
            )
            Text(stringResource(R.string.songbook_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            CapsLabel(stringResource(R.string.songbook_preview), Modifier.padding(top = 8.dp))
            AgoraCard(elevation = 0.dp) {
                if (preview.isEmpty()) Text(stringResource(R.string.songbook_preview_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else preview.forEachIndexed { index, line -> SongLineView(line, 15.sp, first = index == 0) }
            }

            CapsLabel(stringResource(R.string.songbook_rights), Modifier.padding(top = 8.dp))
            AgoraTextField(
                ccli, { ccli = it.filter(Char::isDigit).take(12) }, label = { Text(stringResource(R.string.songbook_ccli_song)) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth()
            )
            AgoraTextField(copyright, { copyright = it }, label = { Text(stringResource(R.string.songbook_copyright)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            // A CCLI song number only counts together with the church's licence number
            if (licenseMissing) Text(stringResource(R.string.songbook_license_missing), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (confirmDiscard) ConfirmSheet(
        title = stringResource(R.string.songbook_discard),
        text = stringResource(R.string.songbook_discard_text),
        confirmLabel = stringResource(R.string.songbook_discard_confirm),
        onConfirm = onClose,
        onDismiss = { confirmDiscard = false },
        destructive = true
    )
    if (confirmDelete && song != null) ConfirmSheet(
        title = stringResource(R.string.songbook_delete_title),
        text = stringResource(R.string.songbook_delete_confirm),
        confirmLabel = stringResource(R.string.delete),
        onConfirm = { runner.run(deletedMsg) { container.repo.deleteSong(song.id); onDeleted(song.id) } },
        onDismiss = { confirmDelete = false },
        destructive = true
    )
}
