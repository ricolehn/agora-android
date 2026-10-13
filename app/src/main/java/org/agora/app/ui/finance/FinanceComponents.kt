package org.agora.app.ui.finance

import org.agora.app.ui.components.softShadow
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Check
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.background
import org.agora.app.data.model.Payment
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import org.agora.app.ui.components.CapsLabel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.agora.app.R
import org.agora.app.data.model.FeeSettings
import org.agora.app.data.model.Person
import org.agora.app.data.model.User
import org.agora.app.ui.components.AgoraCard
import org.agora.app.ui.components.TintedCard
import org.agora.app.ui.theme.Agora
import org.agora.app.util.Dates
import org.agora.app.util.Money

val MEMBER_STATUSES = listOf("vollverdiener", "geringverdiener", "keinverdiener", "pausiert")

@Composable
@ReadOnlyComposable
fun statusLabel(status: String?): String = when (status) {
    "vollverdiener" -> stringResource(R.string.member_status_full)
    "geringverdiener" -> stringResource(R.string.member_status_low)
    "keinverdiener" -> stringResource(R.string.member_status_none)
    "pausiert" -> stringResource(R.string.member_status_paused)
    null, "" -> "–"
    else -> status
}

/** Status name without its emoji (fee list, tiles, request amounts). */
@Composable
@ReadOnlyComposable
fun statusName(status: String?): String = statusLabel(status).dropWhile { !it.isLetter() }.trim()

/** The server sends German status texts; translate the known ones. */
@Composable
@ReadOnlyComposable
fun statusMetaText(text: String): String = when (text) {
    "Alles in Ordnung" -> stringResource(R.string.status_all_ok)
    "Dauerauftrag läuft" -> stringResource(R.string.status_standing_order_active)
    "Zahlung überfällig" -> stringResource(R.string.status_payment_overdue)
    else -> text
}

@Composable
fun personStatusColor(person: Person): Color = when {
    person.statusMeta.isOverdue -> Agora.colors.danger
    person.statusMeta.isSoonDue -> Agora.colors.warning
    else -> Agora.colors.success
}

@Composable
fun paidUntilText(person: Person): String {
    val locale = Dates.locale(LocalContext.current)
    return Dates.paidUntilMonth(person.paidUntil)?.let { Dates.monthYear(it, locale) } ?: stringResource(R.string.never_paid)
}

/** Member fee status: "Alles in Ordnung / Bezahlt bis Oktober 2026" in green/amber/red. */
@Composable
fun FinanceStatusCard(user: User, person: Person?, fees: FeeSettings, onClick: (() -> Unit)? = null, showDetails: Boolean = false, onStatusClick: (() -> Unit)? = null) {
    when {
        !user.pays -> AgoraCard(onClick = onClick) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.AccountCircle, null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.padding(start = 12.dp)) {
                    Text(stringResource(R.string.user_account_non_paying_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.user_account_non_paying_desc), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        person == null -> AgoraCard(onClick = onClick) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Info, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.no_member_record), Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyMedium)
            }
        }
        else -> PersonStatusHero(person, fees, onClick, showDetails, onStatusClick)
    }
}

/**
 * The fee state of one member (web: .user-finance-hero-card / .member-hero): a round symbol, the state in plain words,
 * "Bezahlt bis", the open amount when overdue and - with [showDetails] - fee and status as two chips side by side.
 * [compact]: the smaller version inside an opened card of the fee list (always shows "Bezahlt bis").
 */
@Composable
fun PersonStatusHero(
    person: Person, fees: FeeSettings, onClick: (() -> Unit)? = null, showDetails: Boolean = false,
    onStatusClick: (() -> Unit)? = null, compact: Boolean = false
) {
    // One glance like the web app (.user-finance-hero-card): a round symbol, the state in plain words,
    // fee and status as two chips side by side
    val color = personStatusColor(person)
    val meta = person.statusMeta
    val shape = RoundedCornerShape(if (compact) 20.dp else 26.dp)
    val symbol = when {
        meta.isOverdue -> Icons.Outlined.ErrorOutline
        meta.isSoonDue -> Icons.Outlined.Schedule
        else -> Icons.Outlined.Check
    }
    Column(
        Modifier
            .fillMaxWidth()
            .softShadow(Agora.colors.shadow.copy(alpha = 0.06f), blur = 6.dp, shape = shape, offsetY = 1.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .background(Brush.radialGradient(listOf(color.copy(alpha = 0.13f), Color.Transparent), center = Offset(Float.POSITIVE_INFINITY, 0f), radius = 900f))
            .border(1.dp, color.copy(alpha = 0.26f), shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = if (compact) 16.dp else 18.dp, end = if (compact) 16.dp else 18.dp, top = if (compact) 16.dp else 18.dp, bottom = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(if (compact) 42.dp else 50.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
                Icon(symbol, null, Modifier.size(if (compact) 22.dp else 28.dp), tint = if (MaterialTheme.colorScheme.background.luminance() < 0.2f) MaterialTheme.colorScheme.background else Color.White)
            }
            Column(Modifier.padding(start = 14.dp)) {
                Text(statusMetaText(meta.text.ifBlank { "Alles in Ordnung" }), style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall, color = color)
                Text(
                    buildAnnotatedString {
                        if (!compact && meta.isActiveStandingOrder && !meta.isOverdue) append(stringResource(R.string.user_standing_order_active))
                        else {
                            append(stringResource(R.string.user_paid_until) + " ")
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Agora.colors.heading)) { append(paidUntilText(person)) }
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (meta.isOverdue && person.overdueAmount > 0) {
            Row(
                Modifier.padding(top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(color.copy(alpha = 0.09f)).padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.user_open_amount), color = color, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                Text(Money.format(person.overdueAmount), color = color, style = MaterialTheme.typography.titleLarge)
            }
        }
        if (showDetails) {
            Row(Modifier.padding(top = 14.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FeeChip(Modifier.weight(1f)) {
                    Text(
                        buildAnnotatedString {
                            val amount = Money.format(fees.rateFor(person.effectiveStatus))
                            val text = stringResource(R.string.fee_per_month, amount)
                            val at = text.indexOf(amount)
                            append(text)
                            if (at >= 0) addStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Agora.colors.heading), at, at + amount.length)
                        },
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                // Plain status name: the emoji of the label made it too wide
                FeeChip(Modifier.weight(1f), onClick = onStatusClick) {
                    Text(statusName(person.effectiveStatus), style = MaterialTheme.typography.labelLarge, color = Agora.colors.heading,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (onStatusClick != null) Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** A chip of the status card: fee per month or the status (tap to request a change). */
@Composable
private fun FeeChip(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier.clip(RoundedCornerShape(14.dp)).background(Agora.colors.surfaceAlt).border(1.dp, Agora.colors.hairline, RoundedCornerShape(14.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically, content = content
    )
}

/** Centred label/value tile (`.user-stat`). */
@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Column(
        modifier
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 10.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CapsLabel(label)
        Text(value, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Agora.colors.heading,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 2.dp))
    }
}

private data class TimelineEntry(val date: String, val payment: Payment? = null, val status: String? = null)

/**
 * "Verlauf" like the PWA's generateTimelineHTML: payments and status changes in one list, newest first.
 * Status entries come from the history plus the current status since the last change (or since joining).
 */
@Composable
fun FinanceTimeline(person: Person, modifier: Modifier = Modifier, plainStatus: Boolean = false) {
    val locale = Dates.locale(LocalContext.current)
    val entries = buildList {
        person.statusHistory.forEach { add(TimelineEntry(it.startDate, status = it.status)) }
        val currentStart = if (person.statusHistory.isNotEmpty()) person.statusHistory.last().endDate else person.originalMemberSince.ifBlank { person.memberSince }
        if (!currentStart.isNullOrBlank() && person.status.isNotBlank()) add(TimelineEntry(currentStart, status = person.status))
        person.payments.forEach { add(TimelineEntry(it.date, payment = it)) }
    }.filter { it.date.isNotBlank() }.sortedByDescending { it.date }
    Column(modifier) {
        if (entries.isEmpty()) Text(stringResource(R.string.timeline_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        entries.forEachIndexed { index, entry ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                // Dot with a connecting line to the next entry
                Column(Modifier.width(20.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.padding(top = 6.dp).size(10.dp).clip(CircleShape).background(if (entry.payment != null) MaterialTheme.colorScheme.primary else Agora.colors.duty))
                    if (index < entries.lastIndex) Box(Modifier.padding(top = 4.dp).width(2.dp).weight(1f).background(Agora.colors.hairline))
                }
                Column(Modifier.weight(1f).padding(start = 10.dp, bottom = if (index < entries.lastIndex) 14.dp else 0.dp)) {
                    val date = Dates.short(entry.date, locale)
                    if (entry.payment != null) {
                        Text(stringResource(R.string.timeline_payment, Money.format(entry.payment.amount)), style = MaterialTheme.typography.titleSmall, color = Agora.colors.heading)
                        Text(
                            "${entry.payment.description.ifBlank { stringResource(R.string.timeline_no_note) }} · $date",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(stringResource(R.string.timeline_status, if (plainStatus) statusName(entry.status) else statusLabel(entry.status)), style = MaterialTheme.typography.titleSmall, color = Agora.colors.heading)
                        Text(stringResource(R.string.timeline_valid_from, date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}