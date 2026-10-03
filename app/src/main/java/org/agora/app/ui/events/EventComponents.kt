package org.agora.app.ui.events

import org.agora.app.ui.components.softShadow
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.agora.app.R
import org.agora.app.data.model.AgoraEvent
import org.agora.app.data.model.Group
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.luminance
import org.agora.app.data.model.DutyRequest
import org.agora.app.data.model.User
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.text.style.TextAlign
import org.agora.app.ui.components.GlassChip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import org.agora.app.ui.components.AgoraCard
import org.agora.app.ui.components.ButtonLabel
import org.agora.app.ui.components.IconTile
import org.agora.app.ui.components.InfoRow
import org.agora.app.ui.components.LocalContainer
import org.agora.app.ui.components.Pill
import org.agora.app.ui.components.SecondaryButton
import org.agora.app.ui.components.SecondaryStyle
import org.agora.app.ui.components.TintedCard
import org.agora.app.ui.theme.Agora
import org.agora.app.util.Dates

// ---------- duty helpers (same rules as the PWA) ----------

fun AgoraEvent.myConfirmedDuty(user: User) = duties.firstOrNull { it.assignedUser == user.userId && it.status == "confirmed" }
fun AgoraEvent.myGroupDuty(user: User) = duties.firstOrNull { it.assignedGroup.isNotBlank() && it.assignedGroup in user.groups && it.status == "assigned" }
fun AgoraEvent.myOpenRequest(user: User) = duties.firstOrNull { it.requestedUser == user.userId && it.status == "requested" }
fun AgoraEvent.hasDutyForMe(user: User) = myConfirmedDuty(user) != null || myGroupDuty(user) != null || myOpenRequest(user) != null
fun AgoraEvent.openDutySlots() = duties.count { it.status == "open" || it.status == "declined" }
fun AgoraEvent.isPast(today: String = Dates.todayIso()) = lastDay < today

@Composable
fun DutyRequestCard(requests: List<DutyRequest>, busy: Boolean, onRespond: (DutyRequest, Boolean) -> Unit, onOpenEvent: (String) -> Unit) {
    if (requests.isEmpty()) return
    val duty = Agora.colors.duty
    val locale = Dates.locale(LocalContext.current)
    // `.duty-requests-card`: indigo-tinted card, amber "feedback requested" badge, white request tiles
    TintedCard(duty, contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Outlined.Inbox, duty, size = 36.dp)
            Text(
                stringResource(R.string.duty_requests_title, requests.size),
                style = MaterialTheme.typography.titleMedium,
                color = Agora.colors.heading,
                modifier = Modifier.padding(start = 10.dp).weight(1f)
            )
        }
        Text(
            stringResource(R.string.duty_feedback_requested).uppercase(),
            color = Color.White,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 0.8.sp),
            modifier = Modifier.padding(top = 10.dp).clip(CircleShape).background(Agora.colors.warning).padding(horizontal = 12.dp, vertical = 4.dp)
        )
        requests.forEach { req ->
            Spacer(Modifier.height(12.dp))
            Surface(
                onClick = { onOpenEvent(req.eventId) },
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().softShadow(Color(0xFF0F172A).copy(alpha = 0.10f), blur = 6.dp, shape = RoundedCornerShape(16.dp), offsetY = 2.dp)
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(req.eventTitle, style = MaterialTheme.typography.titleMedium, color = Agora.colors.heading)
                    val date = Dates.parse(req.eventDate)?.let { Dates.long(it, locale) } ?: req.eventDate
                    InfoRow(Icons.Outlined.CalendarMonth, if (req.eventStartTime.isNotBlank()) "$date · ${req.eventStartTime} ${stringResource(R.string.time_suffix)}" else date)
                    val requestedBy = if (req.requestedByName.isNotBlank()) stringResource(R.string.requested_by, req.requestedByName) else null
                    Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Handyman, null, Modifier.size(16.dp), tint = duty)
                        Text(
                            buildAnnotatedString {
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Agora.colors.heading)) { append(req.roleName) }
                                req.section.takeIf { it.isNotBlank() && it != "Allgemein" }?.let { append(" · $it") }
                                requestedBy?.let { append(" ($it)") }
                            },
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                    if (req.notes.isNotBlank()) Text(req.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SecondaryButton(onClick = { onRespond(req, true) }, enabled = !busy, style = SecondaryStyle.Success, modifier = Modifier.weight(1f)) {
                            ButtonLabel(stringResource(R.string.duty_accept), Icons.Outlined.Check)
                        }
                        SecondaryButton(onClick = { onRespond(req, false) }, enabled = !busy, style = SecondaryStyle.Danger, modifier = Modifier.weight(1f)) {
                            ButtonLabel(stringResource(R.string.duty_decline), Icons.Outlined.Close)
                        }
                    }
                }
            }
        }
    }
}

/** Date box of the Termine list (`.termin-date-box`): outlined for Termine, cyan for events, indigo for highlights. */
@Composable
fun DateBox(event: AgoraEvent, modifier: Modifier = Modifier, floating: Boolean = false, day: String = event.date) {
    val locale = Dates.locale(LocalContext.current)
    val date = Dates.parse(day)
    val shape = RoundedCornerShape(12.dp)
    // Tear-off calendar leaf like the PWA (`.event-cal`): month strip in the category colour, big day, weekday
    Column(
        modifier
            .width(50.dp)
            .softShadow(Color(0xFF0F172A).copy(alpha = if (floating) 0.28f else 0.10f), blur = if (floating) 10.dp else 4.dp, shape = shape, offsetY = if (floating) 3.dp else 1.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .then(if (floating) Modifier else Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            date?.let { Dates.monthShort(it, locale) } ?: "",
            color = Color.White,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 0.8.sp, fontSize = 10.sp),
            modifier = Modifier.fillMaxWidth().background(categoryColor(event)).padding(top = 3.dp, bottom = 2.dp)
        )
        Text(
            date?.dayOfMonth?.toString() ?: "?",
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 21.sp, lineHeight = 22.sp),
            fontWeight = FontWeight.ExtraBold, color = Agora.colors.heading,
            modifier = Modifier.padding(top = 3.dp)
        )
        Text(
            date?.let { Dates.weekdayShort(it, locale) } ?: "",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 5.dp)
        )
    }
}

/** Category colour of the PWA: slate for Termine, cyan for events, indigo for highlights. */
@Composable
fun categoryColor(event: AgoraEvent): Color = when {
    event.isPinned -> Agora.colors.duty
    !event.isTermin -> MaterialTheme.colorScheme.primary
    else -> Color(0xFF64748B)
}

/** [showRegistered] = false where the list only contains own registrations anyway (Termine). */
@Composable
fun EventStatusBadge(event: AgoraEvent, user: User, showRegistered: Boolean = true) {
    val duty = Agora.colors.duty
    val confirmed = event.myConfirmedDuty(user)
    when {
        confirmed != null -> Pill(confirmed.roleName, duty, icon = Icons.Outlined.Handyman)
        event.myGroupDuty(user) != null -> Pill(event.myGroupDuty(user)!!.roleName, duty, icon = Icons.Outlined.Handyman)
        event.myOpenRequest(user) != null -> Pill(stringResource(R.string.duty_request_open), duty)
        event.isWaitlisted -> Pill(stringResource(R.string.event_waitlist), Agora.colors.warning)
        event.isRegistered -> if (showRegistered) Pill(stringResource(R.string.event_registered), Agora.colors.success)
        event.requiresRegistration && event.isFull -> Pill(stringResource(R.string.event_full), Agora.colors.danger)
        event.canAccessDutyPlan && event.openDutySlots() > 0 ->
            Pill(pluralStringResource(R.plurals.duties_free, event.openDutySlots(), event.openDutySlots()), MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Card of the Termine list and the home screen, like the PWA's `.event-card`: calendar leaf, title, one clear
 * "when" line, location, at most one status badge; the category colour shows as leaf strip and left stripe.
 */
@Composable
fun EventRow(event: AgoraEvent, user: User, day: String = event.date, showRegistered: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val context = LocalContext.current
    val locale = Dates.locale(context)
    val today = Dates.todayIso()
    // A later day of a multi-day event (Termine list) is "today" only on that day
    val isToday = if (day != event.date) day == today else event.date <= today && event.lastDay >= today
    val category = categoryColor(event)
    AgoraCard(
        modifier = modifier,
        onClick = onClick,
        contentPadding = PaddingValues(0.dp),
        border = if (isToday) BorderStroke(2.dp, category) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            // Thin category stripe on the left edge
            Box(Modifier.width(4.dp).fillMaxHeight().background(category.copy(alpha = if (event.isTermin) 0.45f else 0.85f)))
            Row(Modifier.weight(1f).padding(start = 12.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                DateBox(event, day = day)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(event.title, style = MaterialTheme.typography.titleMedium, color = Agora.colors.heading, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isToday) Text(
                            context.getString(R.string.today).uppercase(),
                            color = if (event.isTermin) MaterialTheme.colorScheme.primary else category,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp),
                            modifier = Modifier.padding(end = 6.dp).clip(RoundedCornerShape(6.dp))
                                .background((if (event.isTermin) MaterialTheme.colorScheme.primary else category).copy(alpha = 0.12f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                        RowLine(
                            if (event.isMultiDay) Icons.Outlined.CalendarMonth else Icons.Outlined.Schedule,
                            when {
                                event.isMultiDay -> Dates.spanShort(event.date, event.endDate, locale)
                                event.startTime.isBlank() -> context.getString(R.string.all_day)
                                else -> "${Dates.timeRange(event.startTime, event.endTime)} ${context.getString(R.string.time_suffix)}".trim()
                            },
                            Agora.colors.heading, bold = true
                        )
                    }
                    if (event.location.isNotBlank()) RowLine(Icons.Outlined.Place, event.location)
                }
                EventStatusBadge(event, user, showRegistered)
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
private fun RowLine(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant, bold: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = color, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 6.dp))
    }
}

/** Target group names of an event: ids resolved to names, names (saved by the web editor) kept as they are. */
fun AgoraEvent.targetGroupNames(groups: List<Group>): List<String> =
    targetGroups.filter { it.isNotBlank() }.map { g -> groups.firstOrNull { it.id == g || it.name == g }?.name ?: g }.distinct()

/** Soft colour mesh where an event has no picture (`.ct-event-card-fallback-cover`): radial spots over a base colour. */
fun Modifier.colorMesh(base: Color, vararg spots: Triple<Float, Float, Color>): Modifier = drawBehind {
    drawRect(base)
    spots.forEach { (x, y, color) ->
        drawRect(Brush.radialGradient(listOf(color, Color.Transparent), center = Offset(size.width * x, size.height * y), radius = size.maxDimension * 0.55f))
    }
}

@Composable
private fun isDark(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.2f

/** Indigo of highlights (`#6366f1`, lighter in the dark theme). */
@Composable
fun highlightIndigo(): Color = if (isDark()) Color(0xFF818CF8) else Color(0xFF6366F1)

private val desaturated = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0.3f) })

/** Frosted label on the cover ("EVENT", "GROSSEVENT"): translucent slate, indigo for highlights. */
@Composable
private fun CoverLabel(text: String, pinned: Boolean) {
    val shape = CircleShape
    Text(
        text.uppercase(), color = Color.White, maxLines = 1,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 0.6.sp),
        modifier = Modifier
            .softShadow(Color.Black.copy(alpha = 0.25f), blur = 8.dp, shape = shape, offsetY = 2.dp)
            .clip(shape)
            .background(if (pinned) Color(0xB34F46E5) else Color(0x8C0F172A))
            .border(1.dp, Color.White.copy(alpha = if (pinned) 0.45f else 0.35f), shape)
            .padding(horizontal = 11.dp, vertical = 5.dp)
    )
}

/** Light status chip on the cover (own duty, open request, waiting list, full, over). */
@Composable
private fun CoverStatus(text: String, color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    val shape = CircleShape
    Row(
        Modifier
            .softShadow(Color.Black.copy(alpha = 0.2f), blur = 8.dp, shape = shape, offsetY = 2.dp)
            .clip(shape)
            .background(if (isDark()) Color(0xE60F172A) else Color.White.copy(alpha = 0.92f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(13.dp), tint = color)
            Spacer(Modifier.width(4.dp))
        }
        Text(text, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold))
    }
}

/**
 * Cover card of the Events tab like the PWA (beta16): the 16:9 picture sits inset with its own rounded corners,
 * frosted labels on it, a colour mesh without a picture, bold title with the own registration next to it,
 * coloured meta icons, capacity and a round arrow. Highlights get a gradient frame, past pictures lose colour.
 */
@Composable
fun EventCoverCard(event: AgoraEvent, user: User, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val container = LocalContainer.current
    val context = LocalContext.current
    val locale = Dates.locale(context)
    val past = event.isPast()
    val dark = isDark()
    val accent = if (event.isPinned) highlightIndigo() else MaterialTheme.colorScheme.primary
    val surfaceAlt = Agora.colors.surfaceAlt
    AgoraCard(
        modifier,
        onClick = onClick,
        contentPadding = PaddingValues(8.dp),
        shape = RoundedCornerShape(24.dp),
        elevation = 8.dp,
        border = if (event.isPinned) BorderStroke(1.5.dp, Brush.linearGradient(listOf(Color(0xFF6366F1), Color(0xFF06B6D4), Color(0xFF10B981))))
        else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(18.dp)).background(surfaceAlt)) {
            if (event.imageUrl.isNotBlank()) {
                AsyncImage(
                    model = container.api.absolute(event.imageUrl),
                    contentDescription = event.title,
                    contentScale = ContentScale.Crop,
                    colorFilter = if (past) desaturated else null,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                val mesh = if (event.isPinned) Modifier.colorMesh(
                    surfaceAlt,
                    Triple(0.2f, 0.25f, Color(0xFF6366F1).copy(alpha = 0.42f)),
                    Triple(0.85f, 0.3f, Color(0xFF06B6D4).copy(alpha = 0.32f)),
                    Triple(0.5f, 1f, Color(0xFFA855F7).copy(alpha = 0.25f))
                ) else Modifier.colorMesh(
                    surfaceAlt,
                    Triple(0.18f, 0.22f, Color(0xFF06B6D4).copy(alpha = 0.38f)),
                    Triple(0.82f, 0.28f, Color(0xFF6366F1).copy(alpha = 0.32f)),
                    Triple(0.55f, 1f, Color(0xFF10B981).copy(alpha = 0.28f))
                )
                Box(Modifier.fillMaxSize().then(mesh), contentAlignment = Alignment.Center) {
                    val tile = RoundedCornerShape(18.dp)
                    Box(
                        Modifier.size(56.dp)
                            .softShadow(Color(0xFF0F172A).copy(alpha = 0.22f), blur = 14.dp, shape = tile, offsetY = 6.dp)
                            .clip(tile)
                            .background(if (dark) Color(0x730F172A) else Color.White.copy(alpha = 0.55f))
                            .border(1.dp, Color.White.copy(alpha = if (dark) 0.18f else 0.7f), tile),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(24.dp), tint = accent) }
                }
            }
            // Darker top and bottom edge so the labels stay readable on bright pictures
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(
                0f to Color(0x470F172A), 0.34f to Color.Transparent, 0.7f to Color.Transparent, 1f to Color(0x2E0F172A)
            )))
            DateBox(event, Modifier.padding(10.dp).align(Alignment.TopStart), floating = true)
            Row(Modifier.align(Alignment.TopEnd).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                CoverLabel(stringResource(if (event.isPinned) R.string.event_label_highlight else R.string.event_label_event), event.isPinned)
                val duty = Agora.colors.duty
                val myDuty = event.myConfirmedDuty(user) ?: event.myGroupDuty(user)
                when {
                    past -> CoverStatus(stringResource(R.string.event_past), MaterialTheme.colorScheme.onSurfaceVariant)
                    myDuty != null -> CoverStatus(myDuty.roleName, duty, Icons.Outlined.Handyman)
                    event.myOpenRequest(user) != null -> CoverStatus(stringResource(R.string.duty_request_open), duty, Icons.Outlined.Schedule)
                    event.isWaitlisted -> CoverStatus(stringResource(R.string.event_waitlist), Agora.colors.warning)
                    event.requiresRegistration && event.isFull && event.myRegistration == null -> CoverStatus(stringResource(R.string.event_full), Agora.colors.danger)
                }
            }
        }
        Column(Modifier.padding(start = 8.dp, end = 8.dp, top = 14.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    event.title, color = Agora.colors.heading, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp, lineHeight = 23.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.2).sp),
                    modifier = Modifier.weight(1f)
                )
                // Own registration next to the title, independent of the status on the picture
                if (event.isRegistered && !past) Pill(stringResource(R.string.event_registered), Agora.colors.success,
                    Modifier.padding(start = 10.dp), icon = Icons.Outlined.Check)
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (event.isMultiDay) CoverMeta(Icons.Outlined.CalendarMonth, Dates.spanShort(event.date, event.endDate, locale), accent,
                    textColor = if (event.isPinned) accent else Agora.colors.heading, bold = true)
                else if (Dates.parse(event.date) != null) CoverMeta(
                    Icons.Outlined.Schedule,
                    listOf(Dates.medium(Dates.parse(event.date)!!, locale), Dates.timeRange(event.startTime, event.endTime)).filter { it.isNotBlank() }.joinToString(" · "),
                    accent
                )
                if (event.location.isNotBlank()) CoverMeta(Icons.Outlined.Place, event.location, accent)
            }
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    when {
                        !event.requiresRegistration -> FooterPill(stringResource(R.string.event_no_registration))
                        event.maxParticipants > 0 -> Column {
                            Text(stringResource(R.string.event_places, event.registeredCount, event.maxParticipants),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = Agora.colors.heading)
                            val fraction = (event.registeredCount.toFloat() / event.maxParticipants).coerceIn(0f, 1f)
                            Box(Modifier.padding(top = 5.dp).width(120.dp).height(6.dp).clip(CircleShape).background(surfaceAlt)) {
                                Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).clip(CircleShape).background(Agora.colors.brandGradient))
                            }
                        }
                        else -> FooterPill(pluralStringResource(R.plurals.event_registered_count, event.registeredCount, event.registeredCount))
                    }
                }
                // Round arrow instead of a "Details" chip
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(surfaceAlt).border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(22.dp), tint = Agora.colors.heading) }
            }
        }
    }
}

@Composable
private fun CoverMeta(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, iconColor: Color, textColor: Color = MaterialTheme.colorScheme.onSurfaceVariant, bold: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(16.dp), tint = iconColor)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = textColor, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun FooterPill(text: String) {
    Text(
        text, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
        modifier = Modifier.clip(CircleShape).background(Agora.colors.surfaceAlt)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape).padding(horizontal = 11.dp, vertical = 5.dp)
    )
}
