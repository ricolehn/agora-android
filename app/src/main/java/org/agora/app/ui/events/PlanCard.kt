package org.agora.app.ui.events

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.agora.app.R
import org.agora.app.data.PlanSong
import org.agora.app.data.ProgramItem
import org.agora.app.data.program
import org.agora.app.data.planSongs
import org.agora.app.data.model.AgoraEvent
import org.agora.app.data.model.Duty
import org.agora.app.data.model.User
import org.agora.app.ui.components.AgoraCard
import org.agora.app.ui.components.CapsLabel
import org.agora.app.ui.components.IconTile
import org.agora.app.ui.components.LocalContainer
import org.agora.app.ui.components.PrimaryButton
import org.agora.app.ui.components.SecondaryButton
import org.agora.app.ui.components.UserAvatar
import org.agora.app.ui.components.rememberActionRunner
import org.agora.app.ui.theme.Agora

// --- One plan per event (web v3.1): duty roster and order of service merged. Entries in the order get their time
// from the start and the durations, the others follow as tasks "during the whole event". People are always asked
// through the duty roster, so requests, answers and reminders keep working.

private val TIME = Regex("^\\d{1,2}:\\d{2}$")

internal fun planAddMinutes(hhmm: String, minutes: Int): String {
    val (h, m) = hhmm.split(":").map { it.toInt() }
    val total = (((h * 60 + m + minutes) % 1440) + 1440) % 1440
    return "%02d:%02d".format(total / 60, total % 60)
}

/** Start time of every item (empty without an event start time). */
internal fun planTimes(items: List<ProgramItem>, start: String): List<String> {
    if (!TIME.matches(start)) return items.map { "" }
    var offset = 0
    return items.map { item -> planAddMinutes(start, offset).also { offset += item.minutes ?: 0 } }
}

internal fun planTotal(items: List<ProgramItem>): Int = items.sumOf { it.minutes ?: 0 }

internal fun planSameName(a: String?, b: String?): Boolean = a.orEmpty().trim().equals(b.orEmpty().trim(), ignoreCase = true)

/** Title of an item: its own, else the song's title, else "song (missing)". */
internal fun planItemTitle(item: ProgramItem, songs: List<PlanSong>, missingSong: String): String =
    item.title.ifBlank { songs.firstOrNull { it.id == item.songId }?.title ?: if (item.songId.isNotBlank()) missingSong else "" }

/** The roster task of an item: the one named under "who", else the one with the item's title. */
internal fun planItemTask(item: ProgramItem, roles: List<String>, songs: List<PlanSong>, missingSong: String): String? =
    roles.firstOrNull { planSameName(it, item.who) } ?: roles.firstOrNull { planSameName(it, planItemTitle(item, songs, missingSong)) }

internal fun Duty.planHasAssignee() = assignedUser.isNotBlank() || requestedUser.isNotBlank() || assignedGroup.isNotBlank() || assignedGroupName.isNotBlank()
internal fun Duty.planIsGroup() = status == "assigned" && (assignedGroupName.isNotBlank() || assignedGroup.isNotBlank())

/** Confirmed people and assigned groups count as filled. */
internal fun planFilled(duties: List<Duty>) = duties.count { it.status == "confirmed" || it.planIsGroup() }

internal fun planRoleNames(event: AgoraEvent): List<String> = event.duties.map { it.roleName.trim() }.filter { it.isNotBlank() }.distinct()

/**
 * The plan card of the event detail, drawn like the web's (renderDutyPlan): head with "x von y besetzt · 10:00 – 10:24",
 * then the order of service as a timeline (a line through blue times with a ring at every item, place number +
 * minutes without a time) and the tasks without a time in their own calm box below. Editors tap a row to open its
 * entry; "+ Eintrag hinzufügen" adds a task or a song.
 */
@Composable
fun PlanCard(event: AgoraEvent, user: User) {
    val container = LocalContainer.current
    val runner = rememberActionRunner()
    var items by remember(event.id) { mutableStateOf<List<ProgramItem>>(emptyList()) }
    var songs by remember { mutableStateOf<List<PlanSong>>(emptyList()) }
    var expanded by rememberSaveable(event.id) { mutableStateOf(false) }
    var entry by remember { mutableStateOf<PlanEntryTarget?>(null) }
    var reload by remember { mutableStateOf(0) }
    val missingSong = stringResource(R.string.plan_song_missing_item)

    // The order comes on its own; live updates of the event (duties) also bring a fresh order
    LaunchedEffect(event.id, event.duties, reload) {
        val program = runCatching { container.repo.program(event.id) }.getOrNull() ?: return@LaunchedEffect
        items = program.items
        if (program.items.any { it.songId.isNotBlank() } && songs.isEmpty()) songs = runCatching { container.repo.planSongs() }.getOrDefault(emptyList())
    }

    val duties = event.duties
    val roles = planRoleNames(event)
    val filled = planFilled(duties)
    val total = planTotal(items)
    val times = planTimes(items, event.startTime)
    val filledText = if (duties.isNotEmpty()) stringResource(R.string.duty_filled, filled, duties.size) else ""
    val meta = if (items.isNotEmpty()) {
        val span = when {
            total == 0 -> ""
            times.firstOrNull().orEmpty().isNotBlank() -> "${event.startTime} – ${planAddMinutes(event.startTime, total)}"
            else -> stringResource(R.string.plan_minutes, total)
        }
        listOf(filledText, span).filter { it.isNotBlank() }.joinToString(" · ").ifBlank {
            if (items.size == 1) stringResource(R.string.plan_count_one) else stringResource(R.string.plan_count_many, items.size)
        }
    } else if (duties.isEmpty()) stringResource(R.string.duty_none_yet)
    else filledText + " · " + (if (roles.size == 1) stringResource(R.string.duty_tasks_one) else stringResource(R.string.duty_tasks_many, roles.size))

    AgoraCard {
        Row(
            Modifier.clip(RoundedCornerShape(12.dp)).clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconTile(Icons.AutoMirrored.Outlined.Article, Agora.colors.duty, size = 38.dp)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(stringResource(if (items.isNotEmpty()) R.string.plan_title_both else R.string.duty_plan),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold), color = Agora.colors.heading)
                Text(meta, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (duties.isNotEmpty()) PlanProgress(filled.toFloat() / duties.size, filled == duties.size, Modifier.padding(top = 12.dp))
        AnimatedVisibility(expanded) {
            Column(Modifier.padding(top = 6.dp)) {
                val grouped = duties.groupBy { it.roleName.trim() }.filterKeys { it.isNotBlank() }
                val used = mutableSetOf<String>()
                val answer: (List<Duty>) -> Duty? = { list -> list.firstOrNull { it.status == "requested" && it.requestedUser == user.userId } }
                val itemRows = items.mapIndexed { i, item ->
                    val role = planItemTask(item, roles, songs, missingSong)
                    if (role != null) used += role
                    i to (item to role)
                }
                val tasks = grouped.filterKeys { it !in used }
                if (itemRows.isEmpty() && tasks.isEmpty()) Text(
                    stringResource(if (event.canEdit) R.string.plan_empty_manager else R.string.duty_empty_member),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                )
                val both = itemRows.isNotEmpty() && tasks.isNotEmpty()
                // "ABLAUF" only when the tasks box follows; the timeline then starts right under its clock
                if (both) Row(Modifier.padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Schedule, null, Modifier.size(15.dp), tint = planSky())
                    CapsLabel(stringResource(R.string.plan_group_program), Modifier.padding(start = 8.dp), color = planSky())
                }
                itemRows.forEachIndexed { index, (i, pair) ->
                    val (item, role) = pair
                    val song = songs.firstOrNull { it.id == item.songId }
                    val key = item.songKey.ifBlank { song?.key.orEmpty() }
                    val minutes = item.minutes ?: 0
                    PlanRow(
                        time = if (minutes > 0 && times[i].isNotBlank()) times[i] else "${i + 1}.",
                        minutes = minutes.takeIf { it > 0 },
                        timeline = TimelineEnds(fromTop = both || index > 0, toBottom = index < itemRows.lastIndex),
                        title = planItemTitle(item, songs, missingSong),
                        songKey = key.takeIf { item.songId.isNotBlank() && it.isNotBlank() },
                        people = role?.let { grouped[it] },
                        textWho = item.who.takeIf { role == null && it.isNotBlank() && !planSameName(it, planItemTitle(item, songs, missingSong)) },
                        note = item.note,
                        request = role?.let { answer(grouped[it].orEmpty()) },
                        user = user,
                        busy = runner.busy,
                        onRespond = { duty, accept -> runner.run { container.repo.respondToDuty(duty.id, accept); container.store.refreshAll() } },
                        onOpen = if (event.canEdit) ({ entry = PlanEntryTarget(itemId = item.id, role = role) }) else null
                    )
                }
                // Tasks without a time: their own borderless box (no heading - the box sets them apart)
                if (tasks.isNotEmpty()) Column(
                    if (both) Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Agora.colors.surfaceAlt)
                        .padding(horizontal = 10.dp, vertical = 2.dp)
                    else Modifier
                ) {
                    tasks.entries.forEachIndexed { index, (role, roleDuties) ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        PlanRow(
                            time = null, minutes = null, timeline = null, title = role, songKey = null,
                            people = roleDuties, textWho = null,
                            note = roleDuties.firstOrNull { it.notes.isNotBlank() }?.notes.orEmpty(),
                            request = answer(roleDuties),
                            user = user,
                            busy = runner.busy,
                            onRespond = { duty, accept -> runner.run { container.repo.respondToDuty(duty.id, accept); container.store.refreshAll() } },
                            onOpen = if (event.canEdit) ({ entry = PlanEntryTarget(itemId = null, role = role) }) else null
                        )
                    }
                }
                if (event.canEdit) PlanAddButton(
                    onClick = { entry = PlanEntryTarget(itemId = null, role = null, new = true) },
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }

    entry?.let { target ->
        PlanEntrySheet(
            event = event,
            user = user,
            items = items,
            target = target,
            onItemsChanged = { items = it },
            onDismiss = { entry = null; reload++ }
        )
    }
}

/** What the entry sheet opens: a new entry, an order item (and its task) or a task without a time. */
internal data class PlanEntryTarget(val itemId: String?, val role: String?, val new: Boolean = false)

@Composable
private fun PlanProgress(fraction: Float, complete: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(Agora.colors.surfaceAlt)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
    ) {
        Box(
            Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().clip(CircleShape)
                .background(if (complete) Agora.colors.brandGradient else androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color(0xFF818CF8), Color(0xFF6366F1))))
        )
    }
}

// Colours of the web's plan: the order of service in sky blue, open requests in amber. The dark theme takes the app's
// dimmed tones instead of the web's bright ones
@Composable
private fun planSky(): Color = if (isDark()) Color(0xFF5AB4DC) else Color(0xFF0284C7)

@Composable
private fun planAmber(): Color = if (isDark()) Agora.colors.warning else Color(0xFFD97706)

/** Where the line of a timeline row runs: from its top (an item or the heading above) and on to its bottom (an item below). */
private data class TimelineEnds(val fromTop: Boolean, val toBottom: Boolean)

/** The timeline of the order of service: a thin line through the times and a ring on it at every item. */
private fun Modifier.timeline(ends: TimelineEnds, color: Color, fill: Color) = drawBehind {
    val x = if (layoutDirection == LayoutDirection.Rtl) size.width - 5.dp.toPx() else 5.dp.toPx()
    val ring = Offset(x, 17.dp.toPx())
    val top = if (ends.fromTop) 0f else 16.dp.toPx()
    val bottom = if (ends.toBottom) size.height else 16.dp.toPx()
    if (bottom > top) drawLine(color.copy(alpha = 0.18f), Offset(x, top), Offset(x, bottom), strokeWidth = 2.dp.toPx())
    drawCircle(fill, radius = 5.dp.toPx(), center = ring)
    drawCircle(color, radius = 4.dp.toPx(), center = ring, style = Stroke(2.dp.toPx()))
}

/** One row of the plan: time column on the timeline (order items only), title, people right under it, note, answer buttons. */
@Composable
private fun PlanRow(
    time: String?,
    minutes: Int?,
    timeline: TimelineEnds?,
    title: String,
    songKey: String?,
    people: List<Duty>?,
    textWho: String?,
    note: String,
    request: Duty?,
    user: User,
    busy: Boolean,
    onRespond: (Duty, Boolean) -> Unit,
    onOpen: (() -> Unit)?
) {
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth()
            .then(if (timeline != null) Modifier.timeline(timeline, planSky(), MaterialTheme.colorScheme.surface) else Modifier)
            .clip(RoundedCornerShape(10.dp))
            .then(if (onOpen != null) Modifier.clickable(onClick = onOpen) else Modifier)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        if (time != null) Column(Modifier.width(62.dp).padding(start = 16.dp, top = 1.dp)) {
            Text(time, style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.8.sp, lineHeight = 18.6.sp, fontWeight = FontWeight.ExtraBold,
                fontFeatureSettings = "tnum"), color = if (time.contains(':')) planSky() else secondary)
            if (minutes != null) Text(stringResource(R.string.plan_minutes, minutes), maxLines = 1,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.2.sp, lineHeight = 15.sp, fontWeight = FontWeight.SemiBold), color = secondary)
        }
        Column(Modifier.weight(1f).padding(start = if (time != null) 6.dp else 0.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.2.sp, lineHeight = 19.sp, fontWeight = FontWeight.Bold),
                    color = Agora.colors.heading, modifier = Modifier.weight(1f, fill = false))
                if (songKey != null) PlanKeyChip(songKey, Modifier.padding(start = 6.dp))
            }
            when {
                people != null -> PlanPeopleShort(people, user)
                textWho != null -> Text(textWho, style = nameStyle(), color = secondary)
                else -> OpenLabel()
            }
            if (note.isNotBlank()) Text(note, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.8.sp, fontStyle = FontStyle.Italic), color = secondary)
            if (request != null) PlanAnswer(request, busy, onRespond)
        }
    }
}

/** A request to the viewer: the answer stays right in the row, with small round buttons like the web's. */
@Composable
private fun PlanAnswer(request: Duty, busy: Boolean, onRespond: (Duty, Boolean) -> Unit) {
    Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.duty_you_were_asked), style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.8.sp, fontWeight = FontWeight.Bold),
            color = if (isDark()) Agora.colors.warning else Color(0xFFB45309))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val padding = PaddingValues(horizontal = 12.dp)
            PrimaryButton(onClick = { onRespond(request, true) }, modifier = Modifier.height(32.dp), enabled = !busy, shape = CircleShape, contentPadding = padding) {
                Icon(Icons.Outlined.Check, null, Modifier.size(15.dp))
                Text(stringResource(R.string.duty_accept), fontSize = 13.sp, maxLines = 1, modifier = Modifier.padding(start = 5.dp))
            }
            SecondaryButton(onClick = { onRespond(request, false) }, modifier = Modifier.height(32.dp), enabled = !busy, shape = CircleShape, contentPadding = padding) {
                Text(stringResource(R.string.duty_decline), fontSize = 13.sp, maxLines = 1)
            }
        }
    }
}

/** "+ Eintrag hinzufügen" as a quiet dashed outline under the plan. */
@Composable
private fun PlanAddButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val dash = MaterialTheme.colorScheme.outline
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .drawBehind {
                val stroke = 1.5.dp.toPx()
                drawRoundRect(
                    dash, topLeft = Offset(stroke / 2, stroke / 2), size = Size(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius(12.dp.toPx() - stroke / 2),
                    style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())))
                )
            }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.Add, null, Modifier.size(16.dp), tint = secondary)
        Text(stringResource(R.string.plan_add_entry), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = secondary,
            modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
internal fun PlanKeyChip(key: String, modifier: Modifier = Modifier) {
    Text(
        key, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold), color = MaterialTheme.colorScheme.primary,
        modifier = modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 1.dp)
    )
}

/** Names in the rows: small and semibold. */
@Composable
private fun nameStyle() = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold)

@Composable
private fun OpenLabel() = Text(stringResource(R.string.plan_open_short), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.8.sp),
    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))

/** The people of a row: small picture, first name and "angefragt" / "abgelehnt", wrapping like the web; more than three as "+N". */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlanPeopleShort(roleDuties: List<Duty>, user: User) {
    val people = roleDuties.filter { it.planHasAssignee() }
    if (people.isEmpty()) return OpenLabel()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp), itemVerticalAlignment = Alignment.CenterVertically) {
        val shown = if (people.size > 3) people.take(2) else people
        shown.forEach { PlanName(it, user, short = true) }
        if (people.size > 3) Text("+${people.size - 2}", style = nameStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** One person or group of a task: picture (or group icon), name and its state; a declined name is struck through. */
@Composable
internal fun PlanName(duty: Duty, user: User, short: Boolean, modifier: Modifier = Modifier) {
    val group = duty.planIsGroup()
    val asked = duty.status == "requested" || duty.status == "declined"
    val declined = duty.status == "declined"
    val uid = if (asked) duty.requestedUser else duty.assignedUser
    val full = when {
        group -> duty.assignedGroupName.ifBlank { duty.assignedGroup }
        uid == user.userId -> stringResource(R.string.you)
        else -> (if (asked) duty.requestedUserName else duty.assignedUserName).ifBlank { stringResource(R.string.person) }
    }
    val name = if (short && !group) full.trim().split(Regex("\\s+")).first() else full
    val state = when (duty.status) {
        "requested" -> stringResource(R.string.plan_state_requested)
        "declined" -> stringResource(R.string.plan_state_declined)
        else -> null
    }
    // The rows use the web's smaller pictures, the entry sheet its list size
    val picture = if (short) 18.dp else 20.dp
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (group) Box(Modifier.size(picture).clip(CircleShape).background(Agora.colors.duty.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Group, null, Modifier.size(11.dp), tint = Agora.colors.duty)
        } else UserAvatar(uid, full, picture)
        Text(name, style = nameStyle(), color = if (declined) MaterialTheme.colorScheme.onSurfaceVariant else Agora.colors.heading,
            textDecoration = if (declined) TextDecoration.LineThrough else null,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = if (short) 5.dp else 6.dp).widthIn(max = 140.dp))
        if (state != null) Text(state, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.8.sp, fontWeight = FontWeight.Bold),
            color = if (declined) Agora.colors.danger else planAmber(), modifier = Modifier.padding(start = 5.dp))
    }
}
