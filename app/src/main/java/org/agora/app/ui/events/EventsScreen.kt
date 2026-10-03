package org.agora.app.ui.events

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.agora.app.R
import org.agora.app.data.model.AgoraEvent
import org.agora.app.data.model.User
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.ui.text.font.FontWeight
import org.agora.app.ui.components.GradientFab
import org.agora.app.ui.components.IconTile
import org.agora.app.ui.components.PageTitle
import org.agora.app.ui.components.PillTabs
import org.agora.app.ui.components.SearchField
import org.agora.app.ui.theme.Agora
import org.agora.app.ui.components.EmptyState
import org.agora.app.ui.components.LocalContainer
import org.agora.app.ui.components.SectionTitle
import org.agora.app.ui.components.containerTransform
import org.agora.app.ui.components.openFrom
import org.agora.app.ui.components.rememberActionRunner
import org.agora.app.util.Dates
import java.time.YearMonth

/** Search over title, location, description and duty names (like the PWA). */
private fun AgoraEvent.matches(query: String): Boolean {
    if (query.isBlank()) return true
    val q = query.trim().lowercase()
    return title.lowercase().contains(q) || location.lowercase().contains(q) || description.lowercase().contains(q) ||
        duties.any { it.roleName.lowercase().contains(q) }
}

/** Termine tab: upcoming; registration events only if I'm involved; highlights only with a duty for me. */
/** (event, day) pairs: one per day of a multi-day event from today on, otherwise its start day. */
fun terminDays(events: List<AgoraEvent>, today: String): List<Pair<AgoraEvent, String>> = events.flatMap { event ->
    val start = Dates.parse(event.date)
    val end = Dates.parse(event.lastDay)
    if (start == null || end == null || !end.isAfter(start)) return@flatMap listOf(event to event.date)
    val first = maxOf(start, Dates.parse(today) ?: start)
    generateSequence(first) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.take(366).map { event to it.toString() }.toList()
}

/** Lets the start page's "Alle" link open the events tab on the Termine list. */
object EventsTabRequest {
    var termine by mutableStateOf(false)
}

fun terminFilter(event: AgoraEvent, user: User, today: String): Boolean {
    if (event.isPast(today)) return false
    if (event.isTermin) return true
    // Same order as the PWA: registration events show for participants (also highlights), other highlights only with a duty
    if (event.requiresRegistration) return event.isRegistered || event.isWaitlisted || event.hasDutyForMe(user)
    if (event.isPinned) return event.hasDutyForMe(user)
    return true
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventsScreen(user: User, contentPadding: PaddingValues, onOpenEvent: (String) -> Unit, onCreate: (type: String) -> Unit) {
    val container = LocalContainer.current
    val store = container.store
    val data by store.data.collectAsStateWithLifecycle()
    val refreshing by store.refreshing.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var showPast by rememberSaveable { mutableStateOf(false) }
    var createMenu by remember { mutableStateOf(false) }
    val runner = rememberActionRunner()
    val locale = Dates.locale(LocalContext.current)
    val today = Dates.todayIso()
    val acceptedMsg = stringResource(R.string.duty_accepted)
    val declinedMsg = stringResource(R.string.duty_declined)
    val canCreate = data.eventSettings.allowMemberCreation || user.managesEvents || user.isAdmin
    LaunchedEffect(EventsTabRequest.termine) {
        if (EventsTabRequest.termine) {
            tab = 0
            EventsTabRequest.termine = false
        }
    }

    val searched = data.events.filter { it.matches(query) }

    Box(Modifier.fillMaxSize()) {
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = { store.refreshInBackground(true) }, modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                contentPadding = PaddingValues(20.dp, contentPadding.calculateTopPadding() + 4.dp, 20.dp, contentPadding.calculateBottomPadding() + 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { PageTitle(stringResource(R.string.events_page_title)) }
                item {
                    PillTabs(
                        listOf(
                            stringResource(R.string.events_tab_termine) to Icons.Outlined.CalendarMonth,
                            stringResource(R.string.events_tab_events) to Icons.Outlined.AutoAwesome
                        ),
                        selected = tab, onSelect = { tab = it }
                    )
                }
                item { SearchField(query, { query = it }, stringResource(R.string.events_search), Modifier.padding(top = 8.dp)) }
                if (data.dutyRequests.isNotEmpty()) item(key = "requests") {
                    DutyRequestCard(data.dutyRequests, runner.busy, onRespond = { req, accept ->
                        runner.run(if (accept) acceptedMsg else declinedMsg) {
                            container.repo.respondToDuty(req.id, accept)
                            store.updateData { d -> d.copy(dutyRequests = d.dutyRequests.filterNot { it.id == req.id }) }
                            store.refreshAll()
                        }
                    }, onOpenEvent = onOpenEvent)
                }

                if (tab == 0) {
                    // Multi-day events appear on every remaining day, purely by day
                    val termine = terminDays(searched.filter { terminFilter(it, user, today) }, today)
                        .sortedWith(compareBy({ it.second }, { it.first.startTime }))
                    if (termine.isEmpty()) item { EmptyState(Icons.Outlined.CalendarMonth, stringResource(R.string.events_empty_termine)) }
                    termine.groupBy { Dates.parse(it.second)?.let(YearMonth::from) }.forEach { (month, days) ->
                        if (month != null) item(key = "m-$month") {
                            SectionTitle(Dates.monthYear(month, locale), count = days.size)
                        }
                        items(days, key = { (event, day) -> "t-${event.id}-$day" }) { (event, day) ->
                            EventRow(event, user, day = day, showRegistered = false, Modifier.containerTransform("termin-${event.id}-$day"),
                                openFrom("termin-${event.id}-$day") { onOpenEvent(event.id) })
                        }
                    }
                } else {
                    val all = searched.filter { !it.isTermin || it.isPinned }
                    val upcoming = all.filter { !it.isPast(today) }.sortedWith(compareBy({ it.date }, { it.startTime }))
                    val past = all.filter { it.isPast(today) }.sortedByDescending { it.date }
                    val highlights = upcoming.filter { it.isPinned }
                    if (upcoming.isEmpty()) item { EmptyState(Icons.Outlined.Celebration, stringResource(R.string.events_empty_events)) }
                    if (highlights.isNotEmpty()) {
                        item(key = "hl") { SectionTitle(stringResource(R.string.events_highlights)) }
                        items(highlights, key = { "h-${it.id}" }) { event ->
                            EventCoverCard(event, user, Modifier.containerTransform("hl-${event.id}"), openFrom("hl-${event.id}") { onOpenEvent(event.id) })
                        }
                    }
                    upcoming.filterNot { it.isPinned }.groupBy { Dates.parse(it.date)?.let(YearMonth::from) }.forEach { (month, events) ->
                        if (month != null) item(key = "em-$month") { SectionTitle(Dates.monthYear(month, locale)) }
                        items(events, key = { "e-${it.id}" }) { event ->
                            EventCoverCard(event, user, Modifier.containerTransform("ev-${event.id}"), openFrom("ev-${event.id}") { onOpenEvent(event.id) })
                        }
                    }
                    if (past.isNotEmpty()) {
                        item(key = "past-toggle") {
                            TextButton(onClick = { showPast = !showPast }, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(if (showPast) R.string.events_hide_past else R.string.events_show_past, past.size))
                            }
                        }
                        if (showPast) items(past, key = { "p-${it.id}" }) { event ->
                            EventCoverCard(event, user, Modifier.containerTransform("past-${event.id}"), openFrom("past-${event.id}") { onOpenEvent(event.id) })
                        }
                    }
                }
            }
        }
        if (canCreate) Box(Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = contentPadding.calculateBottomPadding() + 16.dp)) {
            GradientFab(Icons.Outlined.Add, stringResource(R.string.create),
                onClick = { if (user.managesEvents || user.isAdmin) createMenu = true else onCreate("event") })
            // `.fab-menu`: rounded card with icon tiles
            DropdownMenu(
                createMenu, { createMenu = false },
                shape = RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shadowElevation = 12.dp
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.new_termin), fontWeight = FontWeight.Bold) },
                    leadingIcon = { IconTile(Icons.Outlined.EventNote, MaterialTheme.colorScheme.primary) },
                    onClick = { createMenu = false; onCreate("termin") }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.new_event), fontWeight = FontWeight.Bold) },
                    leadingIcon = { IconTile(Icons.Outlined.Celebration, Agora.colors.duty) },
                    onClick = { createMenu = false; onCreate("event") }
                )
            }
        }
    }
}
