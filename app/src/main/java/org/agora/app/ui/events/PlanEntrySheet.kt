package org.agora.app.ui.events

import androidx.compose.ui.platform.LocalResources
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.agora.app.R
import org.agora.app.data.PlanSong
import org.agora.app.data.ProgramItem
import org.agora.app.data.addEmptyTask
import org.agora.app.data.planSongs
import org.agora.app.data.saveProgram
import org.agora.app.data.model.AgoraEvent
import org.agora.app.data.model.Candidates
import org.agora.app.data.model.Duty
import org.agora.app.ui.components.AgoraSheet
import org.agora.app.ui.components.AgoraTextField
import org.agora.app.ui.components.ButtonLabel
import org.agora.app.ui.components.ConfirmSheet
import org.agora.app.ui.components.IconTile
import org.agora.app.ui.components.LoadingBox
import org.agora.app.ui.components.LocalContainer
import org.agora.app.ui.components.LocalSnackbar
import org.agora.app.ui.components.PillTabs
import org.agora.app.ui.components.SearchField
import org.agora.app.ui.components.SecondaryButton
import org.agora.app.ui.components.SheetActions
import org.agora.app.ui.components.SheetToggleRow
import org.agora.app.ui.components.UserAvatar
import org.agora.app.ui.components.rememberActionRunner
import org.agora.app.ui.components.rememberSheetController
import org.agora.app.ui.theme.Agora
import org.agora.app.util.Dates
import org.agora.app.data.model.User

private val START = Regex("^\\d{1,2}:\\d{2}$")

/** A person or group chosen for a new entry; asked when the entry is saved. */
private data class PlanPick(val userId: String?, val groupId: String?, val label: String)

/**
 * One entry of the plan as a pull-up sheet (web: #plan-entry-modal). New: a task or a song, people to ask (several),
 * "In the order of service" with position, duration, key and note. Existing: people are asked / removed right away,
 * the rest is saved with "Save".
 */
@Composable
internal fun PlanEntrySheet(
    event: AgoraEvent,
    user: User,
    items: List<ProgramItem>,
    target: PlanEntryTarget,
    onItemsChanged: (List<ProgramItem>) -> Unit,
    onDismiss: () -> Unit
) {
    val container = LocalContainer.current
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbar = LocalSnackbar.current
    val runner = rememberActionRunner()
    val scope = rememberCoroutineScope()
    val sheet = rememberSheetController(onDismiss)
    val missingSong = stringResource(R.string.plan_song_missing_item)
    var songs by remember { mutableStateOf<List<PlanSong>?>(null) }
    LaunchedEffect(Unit) { songs = runCatching { container.repo.planSongs() }.getOrDefault(emptyList()) }
    val songList = songs.orEmpty()

    val editing = !target.new
    val item = target.itemId?.let { id -> items.firstOrNull { it.id == id } }
        ?: target.role?.let { role -> items.firstOrNull { planItemTask(it, listOf(role), songList, missingSong) != null } }
    // The task of an existing entry: its own, else the one the item points to
    var role by remember { mutableStateOf(target.role ?: item?.let { planItemTask(it, planRoleNames(event), songList, missingSong) }) }
    var kind by rememberSaveable { mutableStateOf(if (item?.songId?.isNotBlank() == true) "song" else "task") }
    var name by rememberSaveable { mutableStateOf(item?.title ?: target.role.orEmpty()) }
    var songId by rememberSaveable { mutableStateOf(item?.songId.orEmpty()) }
    var songQuery by rememberSaveable { mutableStateOf("") }
    var inProgram by rememberSaveable { mutableStateOf(item != null) }
    val others = items.filter { it.id != item?.id }
    var position by rememberSaveable { mutableStateOf(item?.let { items.indexOf(it) }?.takeIf { it >= 0 } ?: others.size) }
    var minutes by rememberSaveable { mutableStateOf(item?.minutes?.toString().orEmpty()) }
    var key by rememberSaveable { mutableStateOf(item?.songKey.orEmpty()) }
    var note by rememberSaveable { mutableStateOf(item?.note.orEmpty()) }
    val picks = remember { mutableStateListOf<PlanPick>() }
    var picking by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<Duty?>(null) }

    val entryName = if (editing) role ?: item?.let { planItemTitle(it, songList, missingSong) }.orEmpty() else ""
    val people = if (editing) event.duties.filter { d -> role != null && planSameName(d.roleName, role) && d.planHasAssignee() } else emptyList()
    val refresh: suspend () -> Unit = { container.store.refreshAll() }

    // Existing entry without a task yet: the item's title becomes its task (and the item points to it)
    suspend fun ensureRole(): String? {
        role?.let { return it }
        val current = item ?: return null
        val newRole = planItemTitle(current, songList, missingSong).trim()
        if (!planSameName(current.who, newRole)) {
            val saved = container.repo.saveProgram(event.id, items.map { if (it.id == current.id) it.copy(who = newRole) else it })
            onItemsChanged(saved.items)
        }
        role = newRole
        return newRole
    }

    fun askNow(pick: PlanPick) = runner.run(resources.getString(if (pick.groupId != null) R.string.duty_group_assigned else R.string.duty_request_sent)) {
        val r = ensureRole() ?: return@run
        val slot = event.duties.firstOrNull { planSameName(it.roleName, r) && it.status == "open" }
        if (slot != null) container.repo.assignDuty(slot.id, pick.userId, pick.groupId)
        else container.repo.addDuty(event.id, r, "", pick.userId, pick.groupId)
        refresh()
    }

    fun save() {
        val song = if (kind == "song") songList.firstOrNull { it.id == songId } else null
        if (kind == "song" && song == null && !editing) { scope.launch { snackbar.showSnackbar(resources.getString(R.string.plan_song_missing)) }; return }
        val entry = if (editing) role ?: item?.let { planItemTitle(it, songList, missingSong) }.orEmpty() else song?.title ?: name.trim()
        if (entry.isBlank()) { scope.launch { snackbar.showSnackbar(resources.getString(R.string.plan_name_missing)) }; return }
        val duration = minutes.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()?.coerceIn(0, 600)
        val songKey = if (song != null || item?.songId?.isNotBlank() == true) key.trim() else ""
        runner.run(resources.getString(if (picks.isNotEmpty()) R.string.duty_request_sent else R.string.plan_saved)) {
            val roles = planRoleNames(event)
            val roleName = role ?: roles.firstOrNull { planSameName(it, entry) } ?: entry
            val hasTask = roles.any { planSameName(it, roleName) }
            // People are asked through the duty roster (requests, reminders, apps)
            if (picks.isNotEmpty()) picks.forEach { container.repo.addDuty(event.id, roleName, "", it.userId, it.groupId) }
            // A task without a moment always stays on the roster (also when it leaves the order)
            else if (!hasTask && (!inProgram || editing)) container.repo.addEmptyTask(event.id, roleName)
            var next = items.toMutableList()
            val linked = item ?: next.firstOrNull { planItemTask(it, listOf(roleName), songList, missingSong) != null }
            if (inProgram) {
                val updated = linked?.copy(minutes = duration, note = note.trim(), songKey = songKey) ?: ProgramItem(
                    title = if (song != null) "" else entry,
                    who = if (picks.isNotEmpty() || hasTask || editing) roleName else "",
                    songId = song?.id.orEmpty(), minutes = duration, note = note.trim(), songKey = songKey
                )
                next = next.filter { it.id != linked?.id || linked == null }.toMutableList()
                next.add(position.coerceIn(0, next.size), updated)
            } else if (linked != null) next = next.filter { it.id != linked.id }.toMutableList()
            if (next != items) onItemsChanged(container.repo.saveProgram(event.id, next).items)
            refresh()
            sheet.dismiss()
        }
    }

    AgoraSheet(
        title = if (editing) entryName.ifBlank { stringResource(R.string.plan_edit_entry) } else stringResource(R.string.plan_new_entry),
        subtitle = listOf(event.title, Dates.eventWhen(context, event)).filter { it.isNotBlank() }.joinToString(" · "),
        icon = Icons.Outlined.Handyman,
        iconTint = Agora.colors.duty,
        onDismiss = onDismiss,
        controller = sheet,
        actions = {
            if (editing) IconButton(onClick = { confirmDelete = true }, enabled = !runner.busy) {
                Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.plan_delete_entry), tint = Agora.colors.danger)
            }
            SheetActions(stringResource(R.string.save), onConfirm = { save() }, onCancel = { sheet.dismiss() }, busy = runner.busy)
        }
    ) {
        if (!editing) {
            PillTabs(
                listOf(stringResource(R.string.plan_kind_task) to Icons.Outlined.Handyman, stringResource(R.string.plan_kind_song) to Icons.Outlined.MusicNote),
                selected = if (kind == "song") 1 else 0, onSelect = { kind = if (it == 1) "song" else "task" }
            )
            if (kind == "task") AgoraTextField(name, { name = it }, label = { Text(stringResource(R.string.plan_name)) },
                placeholder = { Text(stringResource(R.string.plan_name_ph)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            else SongPicker(songs, songId, songQuery, { songQuery = it }) { picked ->
                songId = picked.id
                if (key.isBlank()) key = picked.key
                songQuery = ""
            }
        }

        // Who? New entries collect the people (asked on save), existing ones ask / remove right away
        Text(stringResource(R.string.plan_who), style = MaterialTheme.typography.titleSmall, color = Agora.colors.heading)
        if (editing) {
            if (people.isEmpty()) Text(stringResource(R.string.plan_open), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            people.forEach { duty ->
                PersonRow(onRemove = { removing = duty }) { PlanName(duty, user, short = false) }
            }
        } else picks.forEachIndexed { index, pick ->
            PersonRow(onRemove = { picks.removeAt(index) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (pick.groupId != null) Box(Modifier.size(24.dp).clip(CircleShape).background(Agora.colors.duty.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Groups, null, Modifier.size(14.dp), tint = Agora.colors.duty)
                    } else UserAvatar(pick.userId, pick.label, 24.dp)
                    Text(pick.label, style = MaterialTheme.typography.bodyMedium, color = Agora.colors.heading, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
        val asked = if (editing) people.isNotEmpty() else picks.isNotEmpty()
        SecondaryButton(onClick = { picking = true }, enabled = !runner.busy, modifier = Modifier.fillMaxWidth()) {
            ButtonLabel(stringResource(if (asked) R.string.plan_ask_more else R.string.plan_ask_person), Icons.Outlined.PersonAdd)
        }

        SheetToggleRow(stringResource(R.string.plan_in_program), inProgram, { inProgram = it })
        if (inProgram) {
            PositionField(others.map { planItemTitle(it, songList, missingSong) }, position.coerceAtMost(others.size)) { position = it }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AgoraTextField(minutes, { value -> minutes = value.filter { it.isDigit() }.take(3) }, label = { Text(stringResource(R.string.plan_duration)) },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                if (kind == "song" || item?.songId?.isNotBlank() == true) AgoraTextField(key, { key = it.take(4) }, label = { Text(stringResource(R.string.plan_key)) },
                    singleLine = true, modifier = Modifier.weight(1f))
            }
            val start = event.startTime
            if (START.matches(start)) {
                val place = position.coerceIn(0, others.size)
                val time = planAddMinutes(start, planTotal(others.take(place)))
                val duration = minutes.toIntOrNull() ?: 0
                Text(
                    if (duration > 0) stringResource(R.string.plan_starts_hint, time, planAddMinutes(time, duration)) else stringResource(R.string.plan_order_only),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AgoraTextField(note, { note = it.take(300) }, placeholder = { Text(stringResource(R.string.plan_note_ph)) }, modifier = Modifier.fillMaxWidth())
        }
    }

    if (picking) PlanPickerSheet(
        title = entryName.ifBlank { if (kind == "song") songList.firstOrNull { it.id == songId }?.title.orEmpty() else name.trim() },
        onDismiss = { picking = false }
    ) { pick ->
        picking = false
        if (editing) askNow(pick)
        else if (picks.none { (pick.userId != null && it.userId == pick.userId) || (pick.groupId != null && it.groupId == pick.groupId) }) picks += pick
    }

    if (confirmDelete) ConfirmSheet(
        title = stringResource(R.string.plan_delete_entry),
        text = stringResource(R.string.plan_delete_confirm, entryName),
        confirmLabel = stringResource(R.string.delete),
        destructive = true,
        onConfirm = {
            runner.run(resources.getString(R.string.plan_deleted)) {
                val r = role
                event.duties.filter { r != null && planSameName(it.roleName, r) }.forEach { container.repo.deleteDuty(it.id) }
                val next = items.filter { it.id != item?.id && !(r != null && planItemTask(it, listOf(r), songList, missingSong) != null) }
                if (next.size != items.size) onItemsChanged(container.repo.saveProgram(event.id, next).items)
                refresh()
                sheet.dismiss()
            }
        },
        onDismiss = { confirmDelete = false }
    )

    removing?.let { duty ->
        ConfirmSheet(
            title = stringResource(R.string.plan_remove_person),
            text = stringResource(R.string.plan_remove_person_confirm),
            confirmLabel = stringResource(R.string.remove),
            destructive = true,
            onConfirm = {
                runner.run {
                    container.repo.deleteDuty(duty.id)
                    // The task stays (empty) when its last person goes
                    if (event.duties.count { planSameName(it.roleName, duty.roleName) } == 1) container.repo.addEmptyTask(event.id, duty.roleName)
                    refresh()
                }
            },
            onDismiss = { removing = null }
        )
    }
}

@Composable
private fun PersonRow(onRemove: () -> Unit, content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Agora.colors.surfaceAlt)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)).padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.weight(1f)) { content() }
        IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Outlined.Close, stringResource(R.string.remove), Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** "Am Anfang" / "Nach „…“" as an M3 dropdown on a field-like surface. */
@Composable
private fun PositionField(titles: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val options = listOf(stringResource(R.string.plan_at_start)) + titles.map { stringResource(R.string.plan_after, it) }
    Column {
        Text(stringResource(R.string.plan_position_label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
        Box {
            Surface(
                onClick = { open = true }, shape = RoundedCornerShape(14.dp), color = Agora.colors.surfaceAlt,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(options.getOrElse(selected) { options.last() }, style = MaterialTheme.typography.bodyLarge, color = Agora.colors.heading,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Icon(Icons.Outlined.ExpandMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            DropdownMenu(open, { open = false }) {
                options.forEachIndexed { index, label ->
                    DropdownMenuItem(text = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }, onClick = { onSelect(index); open = false })
                }
            }
        }
    }
}

/** Songbook search for a new song entry; the chosen song shows as a row with its key. */
@Composable
private fun SongPicker(songs: List<PlanSong>?, chosenId: String, query: String, onQuery: (String) -> Unit, onPick: (PlanSong) -> Unit) {
    val chosen = songs?.firstOrNull { it.id == chosenId }
    if (chosen != null) Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.MusicNote, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        Text(chosen.title, style = MaterialTheme.typography.titleSmall, color = Agora.colors.heading, modifier = Modifier.weight(1f).padding(start = 8.dp))
        if (chosen.key.isNotBlank()) PlanKeyChip(chosen.key)
    }
    SearchField(query, onQuery, stringResource(R.string.plan_song_search))
    when {
        songs == null -> LoadingBox()
        chosen != null && query.isBlank() -> Unit
        else -> {
            val q = query.trim().lowercase()
            val found = songs.filter { q.isBlank() || it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) }.take(30)
            if (found.isEmpty()) Text(stringResource(R.string.plan_song_none), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                found.forEach { song ->
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onPick(song) }
                            .background(if (song.id == chosenId) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else androidx.compose.ui.graphics.Color.Transparent)
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Text(song.title, style = MaterialTheme.typography.titleSmall, color = Agora.colors.heading)
                        val meta = listOf(song.artist, song.key).filter { it.isNotBlank() }.joinToString(" · ")
                        if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** "Person oder Gruppe anfragen": search members (asked) or groups (assigned). */
@Composable
private fun PlanPickerSheet(title: String, onDismiss: () -> Unit, onPick: (PlanPick) -> Unit) {
    val container = LocalContainer.current
    val sheet = rememberSheetController(onDismiss)
    var candidates by remember { mutableStateOf<Candidates?>(null) }
    var tab by rememberSaveable { mutableStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) { candidates = runCatching { container.repo.candidates() }.getOrDefault(Candidates()) }
    AgoraSheet(
        title = stringResource(R.string.plan_ask_person),
        subtitle = title.ifBlank { null },
        icon = Icons.Outlined.PersonAdd,
        iconTint = Agora.colors.duty,
        onDismiss = onDismiss,
        controller = sheet
    ) {
        PillTabs(
            listOf(stringResource(R.string.person) to Icons.Outlined.Person, stringResource(R.string.group) to Icons.Outlined.Groups),
            selected = tab, onSelect = { tab = it }
        )
        SearchField(query, { query = it }, stringResource(if (tab == 0) R.string.search_person else R.string.search_group))
        val c = candidates
        if (c == null) LoadingBox()
        else Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val q = query.trim().lowercase()
            if (tab == 0) c.candidates.filter { q.isBlank() || it.name.lowercase().contains(q) || it.email.lowercase().contains(q) }.forEach { person ->
                PickRow(person.name.ifBlank { person.email }, person.email.takeIf { it.isNotBlank() }, { UserAvatar(person.id, person.name, 38.dp) },
                    stringResource(R.string.duty_request)) { sheet.dismiss { onPick(PlanPick(person.id, null, person.name.ifBlank { person.email })) } }
            } else c.groups.filter { q.isBlank() || it.name.lowercase().contains(q) }.forEach { group ->
                PickRow(group.name, stringResource(R.string.duty_assign_group_sub), { IconTile(Icons.Outlined.Groups, Agora.colors.duty, size = 38.dp) },
                    stringResource(R.string.duty_assign)) { sheet.dismiss { onPick(PlanPick(null, group.id, group.name)) } }
            }
        }
    }
}

@Composable
private fun PickRow(title: String, subtitle: String?, leading: @Composable () -> Unit, actionLabel: String, onAction: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Agora.colors.surfaceAlt)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leading()
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Agora.colors.heading, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        SecondaryButton(onClick = onAction, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) { Text(actionLabel) }
    }
}
