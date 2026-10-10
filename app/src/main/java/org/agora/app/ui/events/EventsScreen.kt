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
import androidx.compose.material.icons.automirrored.outlined.EventNote
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
import org.agora.app.ui.components.AgoraFabMenu
import org.agora.app.ui.components.FabAction
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
import org.agora.app.ui.components.columnCount
import org.agora.app.ui.components.gridItems
import org.agora.app.ui.components.pagePadding
import java.time.YearMonth
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.agora.app.ui.components.isWide
import org.agora.app.ui.components.pageGutter
import org.agora.app.ui.components.screenWidthDp

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
    val runner = rememberActionRunner()
    val locale = Dates.locale(LocalContext.current)
    val today = Dates.todayIso()
    val acceptedMsg = stringResource(R.string.duty_accepted)
    val declinedMsg = stringResource(R.string.duty_declined)
    // Admins too need the event permission (like the server)
    val canCreate = data.eventSettings.allowMemberCreation || user.managesEvents
    LaunchedEffect(EventsTabRequest.termine) {
        if (EventsTabRequest.termine) {
            tab = 0
            EventsTabRequest.termine = false
        }
    }

    val searched = remember(data.events, query) { data.events.filter { it.matches(query) } }
    val coverColumns = columnCount(300.dp, 16.dp)
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Termine: one list below each other; on wide tablets a month calendar on the left third jumps to a day (web beta18)
    val termine = remember(searched, user, today) {
        terminDays(searched.filter { terminFilter(it, user, today) }, today).sortedWith(compareBy({ it.second }, { it.first.startTime }))
    }
    val termineByMonth = remember(termine) { termine.groupBy { Dates.parse(it.second)?.let(YearMonth::from) } }
    val showCalendar = isWide() && tab == 0 && termine.isNotEmpty()
    val gutter = pageGutter()
    val calendarGap = 28.dp
    val calendarWidth = ((screenWidthDp().dp - gutter * 2 - calendarGap) / 3).coerceAtLeast(280.dp)
    val listStart = if (showCalendar) calendarWidth + calendarGap else 0.dp
    var calendarMonth by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var selectedDay by rememberSaveable { mutableStateOf<String?>(null) }
    var jumpTick by remember { mutableStateOf(0) }
    val calendarDays = remember(termine) {
        termine.groupBy({ it.second }, { terminCategory(it.first) }).mapValues { it.value.toSet() }
    }
    // Index of the first list item of each day: title, tabs, search, (duty requests), anchor, then month headers + days
    val headerItems = 3 + (if (data.dutyRequests.isNotEmpty()) 1 else 0) + 1
    val dayIndex = remember(termineByMonth, headerItems) {
        buildMap {
            var index = headerItems
            termineByMonth.forEach { (month, days) ->
                if (month != null) index++
                days.forEach { (_, day) -> if (day !in this) put(day, index); index++ }
            }
        }
    }
    // The day the list jumped to: the chosen one, or the next one with entries
    val jumpTarget = selectedDay?.let { chosen -> dayIndex.keys.sorted().firstOrNull { it >= chosen } }
    val jumpTo: (String) -> Unit = { day ->
        selectedDay = day
        dayIndex.keys.sorted().firstOrNull { it >= day }?.let { target ->
            jumpTick++
            scope.launch { listState.animateScrollToItem(dayIndex.getValue(target)) }
        }
    }

    Box(Modifier.fillMaxSize()) {
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = { store.refreshInBackground(true) }, modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                contentPadding = pagePadding(contentPadding.calculateTopPadding() + 4.dp, contentPadding.calculateBottomPadding() + 96.dp),
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
                    // Multi-day events appear on every remaining day, purely by day, one card below the other
                    item(key = "termine-anchor") { Spacer(Modifier.fillMaxWidth()) }
                    if (termine.isEmpty()) item { EmptyState(Icons.Outlined.CalendarMonth, stringResource(R.string.events_empty_termine)) }
                    termineByMonth.forEach { (month, days) ->
                        if (month != null) item(key = "m-$month") {
                            SectionTitle(Dates.monthYear(month, locale), Modifier.padding(start = listStart), count = days.size)
                        }
                        items(days, key = { (event, day) -> "t-${event.id}-$day" }) { (event, day) ->
                            // The cards of the day chosen in the calendar light up briefly
                            val glow = remember { Animatable(0f) }
                            LaunchedEffect(jumpTick) {
                                if (jumpTick > 0 && jumpTarget == day) {
                                    glow.snapTo(1f); delay(500); glow.animateTo(0f, tween(1100))
                                }
                            }
                            EventRow(event, user, day = day, showRegistered = false,
                                Modifier.padding(start = listStart)
                                    .border(3.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f * glow.value), MaterialTheme.shapes.medium)
                                    .containerTransform("termin-${event.id}-$day"),
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
                        gridItems(highlights, coverColumns, key = { "h-${it.id}" }) { event, cell ->
                            EventCoverCard(event, user, cell.containerTransform("hl-${event.id}"), openFrom("hl-${event.id}") { onOpenEvent(event.id) })
                        }
                    }
                    upcoming.filterNot { it.isPinned }.groupBy { Dates.parse(it.date)?.let(YearMonth::from) }.forEach { (month, events) ->
                        if (month != null) item(key = "em-$month") { SectionTitle(Dates.monthYear(month, locale)) }
                        gridItems(events, coverColumns, key = { "e-${it.id}" }) { event, cell ->
                            EventCoverCard(event, user, cell.containerTransform("ev-${event.id}"), openFrom("ev-${event.id}") { onOpenEvent(event.id) })
                        }
                    }
                    if (past.isNotEmpty()) {
                        item(key = "past-toggle") {
                            TextButton(onClick = { showPast = !showPast }, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(if (showPast) R.string.events_hide_past else R.string.events_show_past, past.size))
                            }
                        }
                        if (showPast) gridItems(past, coverColumns, key = { "p-${it.id}" }) { event, cell ->
                            EventCoverCard(event, user, cell.containerTransform("past-${event.id}"), openFrom("past-${event.id}") { onOpenEvent(event.id) })
                        }
                    }
                }
            }
        }
        if (showCalendar) {
            // Sits next to the list from the first entry on and stays at the top while the list scrolls under it
            val top by remember(listState, headerItems) {
                derivedStateOf {
                    val info = listState.layoutInfo
                    val anchor = info.visibleItemsInfo.firstOrNull { it.key == "termine-anchor" }
                    val pinned = info.beforeContentPadding
                    when {
                        anchor != null -> maxOf(anchor.offset + info.beforeContentPadding, pinned)
                        listState.firstVisibleItemIndex >= headerItems -> pinned
                        else -> null
                    }
                }
            }
            top?.let { y ->
                TermineCalendar(
                    month = YearMonth.parse(calendarMonth), onMonth = { calendarMonth = it.toString() },
                    days = calendarDays, selected = selectedDay, today = today, onDay = jumpTo,
                    modifier = Modifier.padding(start = gutter).width(calendarWidth).offset { IntOffset(0, y) }
                )
            }
        }
        if (canCreate) {
            // Native M3 "+": event managers pick appointment or event, everyone else creates an event directly
            val newEvent = FabAction(stringResource(R.string.new_event), Icons.Outlined.Celebration) { onCreate("event") }
            val actions = if (user.managesEvents)
                listOf(FabAction(stringResource(R.string.new_termin), Icons.AutoMirrored.Outlined.EventNote) { onCreate("termin") }, newEvent)
            else listOf(newEvent)
            AgoraFabMenu(actions, stringResource(R.string.create),
                Modifier.align(Alignment.BottomEnd).padding(end = 4.dp, bottom = contentPadding.calculateBottomPadding()))
        }
    }
}
