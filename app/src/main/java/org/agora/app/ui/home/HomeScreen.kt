package org.agora.app.ui.home

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import org.agora.app.ui.components.UserAvatar
import org.agora.app.ui.components.CapsLabel
import org.agora.app.ui.components.IconTile
import org.agora.app.ui.components.containerTransform
import org.agora.app.ui.components.openFrom
import org.agora.app.data.model.MentoringThread
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.agora.app.R
import org.agora.app.data.model.User
import org.agora.app.ui.components.AgoraCard
import org.agora.app.ui.components.GradientCard
import org.agora.app.ui.components.LocalContainer
import org.agora.app.ui.components.Pill
import org.agora.app.ui.components.SectionTitle
import org.agora.app.ui.components.rememberActionRunner
import org.agora.app.ui.events.DutyRequestCard
import org.agora.app.ui.events.EventRow
import org.agora.app.ui.events.myConfirmedDuty
import org.agora.app.ui.events.myGroupDuty
import org.agora.app.ui.finance.FinanceStatusCard
import org.agora.app.ui.theme.Agora
import org.agora.app.util.Dates
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.ui.unit.Dp
import org.agora.app.ui.components.isTablet
import org.agora.app.ui.components.columnCount
import org.agora.app.ui.components.isWide
import org.agora.app.ui.components.pageGutter
import org.agora.app.ui.components.screenWidthDp
import org.agora.app.ui.components.TwoPane
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.PriorityHigh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material.icons.outlined.Check
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import org.agora.app.data.model.AgoraEvent
import org.agora.app.data.model.Person
import org.agora.app.ui.components.fullBleed
import org.agora.app.ui.events.colorMesh
import org.agora.app.ui.events.highlightIndigo
import org.agora.app.ui.events.isPast
import org.agora.app.ui.finance.statusMetaText
import org.agora.app.ui.finance.paidUntilText
import org.agora.app.util.Money
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Start page for members like the PWA (beta16): short greeting, an overdue payment right below it, duty requests,
 * "Als Nächstes" (the next five appointments and events as a swipeable row), own duties, new messages and, when a
 * payment is due soon, a slim line at the end. While everything is paid the payment state is not shown at all.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    user: User,
    contentPadding: PaddingValues,
    onOpenFinances: () -> Unit,
    onOpenEvent: (String) -> Unit,
    onOpenThread: (String) -> Unit,
    onOpenMentoring: () -> Unit = {},
    onOpenTermine: () -> Unit = {}
) {
    val store = LocalContainer.current.store
    val repo = LocalContainer.current.repo
    val data by store.data.collectAsStateWithLifecycle()
    val refreshing by store.refreshing.collectAsStateWithLifecycle()
    val runner = rememberActionRunner()
    val today = Dates.todayIso()
    val myDutyEvents = data.events
        .filter { it.lastDay >= today && it.status != "cancelled" && (it.myConfirmedDuty(user) != null || it.myGroupDuty(user) != null) }
        .sortedWith(compareBy({ it.date }, { it.startTime }))
    val startOf = { e: AgoraEvent -> if (e.date < today) today else e.date }
    val upcoming = data.events
        .filter { it.date.isNotBlank() && it.status != "cancelled" && !it.isPast(today) }
        .sortedWith(compareBy({ startOf(it) }, { it.startTime }))
        .take(HOME_UPCOMING_COUNT)
    val person = data.ownPerson
    val payState = when {
        !user.pays || person == null -> null
        person.statusMeta.isOverdue -> PayState.Overdue
        person.statusMeta.isSoonDue -> PayState.Soon
        else -> null
    }
    val acceptedMsg = stringResource(R.string.duty_accepted)
    val declinedMsg = stringResource(R.string.duty_declined)

    val gutter = if (isTablet()) pageGutter() else 16.dp
    val wide = isWide()
    val unread = data.unreadThreads
    val dutyColumns = columnCount(420.dp)
    val duties: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            HomeSectionHead(stringResource(R.string.your_duties))
            // Tablets: several duty cards per row (one column next to the messages)
            val columns = if (wide && unread.isNotEmpty()) 1 else dutyColumns
            myDutyEvents.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { event ->
                        EventRow(event, user, modifier = Modifier.weight(1f).fillMaxHeight().containerTransform("duty-${event.id}"),
                            onClick = openFrom("duty-${event.id}") { onOpenEvent(event.id) })
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }

    PullToRefreshBox(isRefreshing = refreshing, onRefresh = { store.refreshInBackground(showIndicator = true) }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(
                start = gutter, end = gutter,
                top = contentPadding.calculateTopPadding() + 4.dp,
                bottom = contentPadding.calculateBottomPadding() + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            item(key = "greeting") { HomeGreeting(user) }
            if (payState == PayState.Overdue && person != null) item(key = "pay-overdue") { HomePayment(person, payState, onOpenFinances) }
            if (data.dutyRequests.isNotEmpty()) item(key = "duty-requests") {
                DutyRequestCard(
                    data.dutyRequests, runner.busy,
                    onRespond = { req, accept ->
                        runner.run(if (accept) acceptedMsg else declinedMsg) {
                            repo.respondToDuty(req.id, accept)
                            store.updateData { d -> d.copy(dutyRequests = d.dutyRequests.filterNot { it.id == req.id }) }
                            store.refreshAll()
                        }
                    },
                    onOpenEvent = onOpenEvent
                )
            }
            if (data.loaded || upcoming.isNotEmpty()) item(key = "upcoming") { UpcomingRow(upcoming, user, today, gutter, onOpenEvent, onOpenTermine) }
            // Tablets: own duties and new messages side by side
            if (wide && myDutyEvents.isNotEmpty() && unread.isNotEmpty()) item(key = "duties-messages") {
                TwoPane(true, gap = 22.dp, left = { duties() }, right = { NewMessagesCard(unread, onOpenThread, onOpenMentoring) })
            } else {
                if (myDutyEvents.isNotEmpty()) item(key = "my-duties") { duties() }
                if (unread.isNotEmpty()) item(key = "mentoring") { NewMessagesCard(unread, onOpenThread, onOpenMentoring) }
            }
            if (payState == PayState.Soon && person != null) item(key = "pay-soon") { HomePayment(person, payState, onOpenFinances) }
        }
    }
}

private const val HOME_UPCOMING_COUNT = 5

private enum class PayState { Soon, Overdue }

/** "SAMSTAG, 3. OKTOBER" over "Hallo, Max 👋" (morning / day / evening). */
@Composable
private fun HomeGreeting(user: User) {
    val locale = Dates.locale(LocalContext.current)
    val now = LocalDateTime.now()
    val hello = stringResource(when {
        now.hour < 11 -> R.string.home_greeting_morning
        now.hour < 18 -> R.string.home_greeting_day
        else -> R.string.home_greeting_evening
    })
    val first = user.firstName.ifBlank { user.fullName }.trim().substringBefore(' ')
    Column(Modifier.padding(start = 2.dp, top = 4.dp)) {
        Text(
            now.format(DateTimeFormatter.ofPattern(if (locale.language == "de") "EEEE, d. MMMM" else "EEEE, MMMM d", locale)).uppercase(locale),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            if (first.isNotBlank()) "$hello, $first 👋" else "$hello 👋",
            style = MaterialTheme.typography.headlineMedium.copy(fontSize = 27.sp, lineHeight = 32.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.4).sp),
            color = Agora.colors.heading,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

/** Section heading of the start page with an optional link on the right ("Alle >"). */
@Composable
private fun HomeSectionHead(title: String, link: String? = null, onLink: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.2).sp),
            color = Agora.colors.heading, modifier = Modifier.weight(1f))
        if (link != null) TextButton(onClick = onLink) {
            Text(link, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(18.dp))
        }
    }
}

/** Overdue: red card with the open amount and "Ansehen"; due soon: a slim amber line. Tapping opens the finances. */
@Composable
private fun HomePayment(person: Person, state: PayState, onClick: () -> Unit) {
    val overdue = state == PayState.Overdue
    val color = if (overdue) Agora.colors.danger else Agora.colors.warning
    val shape = RoundedCornerShape(18.dp)
    val meta = person.statusMeta
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (overdue) color.copy(alpha = 0.08f).compositeOver(MaterialTheme.colorScheme.surface) else MaterialTheme.colorScheme.surface)
            .border(if (overdue) 1.5.dp else 1.dp, color.copy(alpha = 0.35f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = if (overdue) 16.dp else 14.dp, vertical = if (overdue) 14.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.PriorityHigh, null, Modifier.size(18.dp), tint = Color.White)
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            val title = when {
                overdue -> statusMetaText(meta.text.ifBlank { "Zahlung überfällig" })
                meta.isActiveStandingOrder -> stringResource(R.string.user_standing_order_active)
                else -> stringResource(R.string.user_paid_until) + " " + paidUntilText(person)
            }
            Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = if (overdue) color else Agora.colors.heading, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (overdue) stringResource(R.string.home_fee_open, Money.format(person.overdueAmount)) else statusMetaText(meta.text),
                style = if (overdue) MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.bodySmall,
                color = if (overdue) Agora.colors.heading else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (overdue) Text(
            stringResource(R.string.home_fee_details), color = Color.White,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(start = 10.dp).clip(CircleShape).background(color).padding(horizontal = 14.dp, vertical = 7.dp)
        ) else Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** "Als Nächstes": the next appointments and events as a swipeable row of small cards. */
@Composable
private fun UpcomingRow(upcoming: List<AgoraEvent>, user: User, today: String, gutter: Dp, onOpenEvent: (String) -> Unit, onOpenTermine: () -> Unit) {
    Column {
        HomeSectionHead(stringResource(R.string.home_next), link = if (upcoming.isNotEmpty()) stringResource(R.string.home_messages_all) else null, onLink = onOpenTermine)
        Spacer(Modifier.height(10.dp))
        if (upcoming.isEmpty()) {
            Text(
                stringResource(R.string.home_next_empty), textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp)).padding(18.dp)
            )
            return@Column
        }
        // Cards side by side over the full width; a fling always stops with a card at the left edge (snapping, like
        // the web and iOS). Tablets: five cards share the width, the picture grows in width, not in height
        val gap = 12.dp
        val cardWidth = if (isTablet()) maxOf(236.dp, (screenWidthDp().dp - gutter * 2 - gap * 4) / 5) else 236.dp
        val coverHeight = minOf((cardWidth - 12.dp) * 9f / 16f, 200.dp)
        val listState = rememberLazyListState()
        LazyRow(
            Modifier.fullBleed(gutter),
            state = listState,
            contentPadding = PaddingValues(start = gutter, end = gutter, top = 2.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(gap),
            flingBehavior = rememberSnapFlingBehavior(listState, SnapPosition.Start)
        ) {
            items(upcoming, key = { it.id }) { event ->
                // Fixed height: all cards equally tall, the chip sits at the bottom
                NextCard(event, user, today, Modifier.width(cardWidth).height(coverHeight + 12.dp + 136.dp), coverHeight, onOpenEvent)
            }
        }
    }
}

/** One card of the "Als Nächstes" row: picture or colour mesh with the day on it, time, title, place, one chip. */
@Composable
private fun NextCard(event: AgoraEvent, user: User, today: String, modifier: Modifier, coverHeight: Dp, onOpenEvent: (String) -> Unit) {
    val container = LocalContainer.current
    val context = LocalContext.current
    val locale = Dates.locale(context)
    val cat = when {
        event.isPinned && !event.isTermin -> highlightIndigo()
        !event.isTermin -> MaterialTheme.colorScheme.primary
        else -> Color(0xFF64748B)
    }
    val start = if (event.date < today) today else event.date
    val dayLabel = homeDayLabel(start, today, locale)
    val isToday = start <= today
    val key = "next-${event.id}"
    AgoraCard(
        modifier.containerTransform(key),
        onClick = openFrom(key) { onOpenEvent(event.id) },
        contentPadding = PaddingValues(6.dp),
        shape = RoundedCornerShape(20.dp),
        elevation = 5.dp
    ) {
        Box(Modifier.fillMaxWidth().height(coverHeight).clip(RoundedCornerShape(15.dp)).background(Agora.colors.surfaceAlt)) {
            if (event.imageUrl.isNotBlank()) AsyncImage(container.api.absolute(event.imageUrl), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else Box(
                Modifier.fillMaxSize().colorMesh(
                    Agora.colors.surfaceAlt,
                    Triple(0.2f, 0.25f, cat.copy(alpha = 0.35f)),
                    Triple(0.85f, 0.35f, Color(0xFF6366F1).copy(alpha = 0.22f)),
                    Triple(0.5f, 1f, Color(0xFF10B981).copy(alpha = 0.22f))
                ),
                contentAlignment = Alignment.Center
            ) { Icon(if (event.isTermin) Icons.Outlined.Schedule else Icons.Outlined.CalendarMonth, null, Modifier.size(24.dp), tint = cat) }
            Text(
                dayLabel, color = Color.White, maxLines = 1,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                modifier = Modifier.padding(8.dp).clip(CircleShape)
                    .then(if (isToday) Modifier.background(Agora.colors.brandGradient) else Modifier.background(Color(0x800F172A)))
                    .border(1.dp, Color.White.copy(alpha = if (isToday) 0.45f else 0.3f), CircleShape)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
        Column(Modifier.weight(1f).padding(start = 8.dp, end = 8.dp, top = 10.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            val time = when {
                event.isMultiDay -> Dates.spanShort(event.date, event.endDate, locale)
                event.startTime.isNotBlank() -> event.startTime
                else -> stringResource(R.string.all_day)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (event.isMultiDay) Icons.Outlined.CalendarMonth else Icons.Outlined.Schedule, null, Modifier.size(14.dp), tint = cat)
                Text(time, color = cat, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 5.dp))
            }
            Text(event.title, color = Agora.colors.heading, maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.ExtraBold))
            if (event.location.isNotBlank()) Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Place, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(event.location, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 5.dp))
            }
            Spacer(Modifier.weight(1f))
            val duty = event.myConfirmedDuty(user) ?: event.myGroupDuty(user)
            when {
                duty != null -> Pill(duty.roleName, Agora.colors.duty, icon = Icons.Outlined.Handyman)
                event.isRegistered -> Pill(stringResource(R.string.event_registered), Agora.colors.success, icon = Icons.Outlined.Check)
                event.isWaitlisted -> Pill(stringResource(R.string.event_waitlist), Agora.colors.warning)
                event.requiresRegistration && !event.isFull -> Pill(stringResource(R.string.home_register_open), MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/** "Heute", "Morgen", the weekday within a week, else "Sa., 17. Okt.". */
@Composable
private fun homeDayLabel(dateIso: String, todayIso: String, locale: java.util.Locale): String {
    if (dateIso <= todayIso) return stringResource(R.string.today)
    val date = Dates.parse(dateIso) ?: return dateIso
    val today = Dates.parse(todayIso) ?: return dateIso
    val days = java.time.temporal.ChronoUnit.DAYS.between(today, date)
    return when {
        days == 1L -> stringResource(R.string.home_tomorrow)
        days < 7 -> date.format(DateTimeFormatter.ofPattern("EEEE", locale))
        else -> date.format(DateTimeFormatter.ofPattern(if (locale.language == "de") "EEE, d. MMM" else "EEE, MMM d", locale))
    }
}

private const val HOME_MESSAGE_ROWS = 3

/**
 * "Neue Nachrichten" on the start page, like the PWA: header with the total unread count and a link to all
 * conversations, then up to three unread chats as rows (picture or initials, name, role, preview, time, count).
 */
@Composable
private fun NewMessagesCard(unread: List<MentoringThread>, onOpenThread: (String) -> Unit, onOpenAll: () -> Unit) {
    val locale = Dates.locale(LocalContext.current)
    val purple = Color(0xFF7C3AED)
    AgoraCard(contentPadding = PaddingValues(0.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Outlined.ChatBubbleOutline, purple, size = 34.dp)
            Text(stringResource(R.string.home_new_messages), style = MaterialTheme.typography.titleMedium, color = Agora.colors.heading,
                modifier = Modifier.padding(start = 10.dp))
            CountBadge(unread.sumOf { it.unreadCount }, purple, Modifier.padding(start = 8.dp))
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onOpenAll) {
                Text(stringResource(R.string.home_messages_all), style = MaterialTheme.typography.labelLarge)
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(18.dp))
            }
        }
        unread.take(HOME_MESSAGE_ROWS).forEach { thread ->
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                Modifier.fillMaxWidth().containerTransform("home-chat-${thread.id}").background(MaterialTheme.colorScheme.surface)
                    .clickable(onClick = openFrom("home-chat-${thread.id}") { onOpenThread(thread.id) }).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Seekers see their mentor's picture, mentors only know the anonymous alias
                UserAvatar(if (thread.myRole == "mentee") thread.mentor else null, thread.partnerName, 44.dp)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(thread.partnerName, style = MaterialTheme.typography.titleSmall, color = Agora.colors.heading,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(Dates.chatTime(thread.lastMessage?.created ?: thread.updated, locale), style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp))
                    }
                    CapsLabel(stringResource(if (thread.myRole == "mentor") R.string.mentoring_role_seeker else R.string.mentoring_role_mentor))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                        Text(thread.lastMessage?.text?.takeIf { it.isNotBlank() } ?: stringResource(R.string.home_new_message_fallback),
                            style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        CountBadge(thread.unreadCount, MaterialTheme.colorScheme.primary, Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
        if (unread.size > HOME_MESSAGE_ROWS) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                pluralStringResource(R.plurals.more_unread_threads, unread.size - HOME_MESSAGE_ROWS, unread.size - HOME_MESSAGE_ROWS),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }
    }
}

/** Round count badge with the number centred, capped at 99+ (like the PWA's badges). */
@Composable
private fun CountBadge(count: Int, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier.heightIn(min = 22.dp).widthIn(min = 22.dp).clip(CircleShape).background(color).padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(if (count > 99) "99+" else "$count", color = Color.White, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold))
    }
}
