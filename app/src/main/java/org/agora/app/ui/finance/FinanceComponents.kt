package org.agora.app.ui.finance

import androidx.compose.foundation.background
import org.agora.app.data.model.Payment
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
        else -> {
            // Centred status card of the PWA (`.user-status-card`): heavy coloured title, inner amount box, stat tiles
            val color = personStatusColor(person)
            val meta = person.statusMeta
            TintedCard(color, onClick = onClick) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        statusMetaText(meta.text.ifBlank { "Alles in Ordnung" }),
                        style = MaterialTheme.typography.headlineMedium, color = color, textAlign = TextAlign.Center
                    )
                    Text(
                        buildAnnotatedString {
                            if (meta.isActiveStandingOrder && !meta.isOverdue) append(stringResource(R.string.user_standing_order_active))
                            else {
                                append(stringResource(R.string.user_paid_until) + " ")
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Agora.colors.heading)) { append(paidUntilText(person)) }
                            }
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                if (meta.isOverdue && person.overdueAmount > 0) {
                    Spacer(Modifier.height(16.dp))
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(color.copy(alpha = 0.08f))
                            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                            .padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CapsLabel(stringResource(R.string.user_open_amount), color = color)
                        Text(Money.format(person.overdueAmount), color = color, style = MaterialTheme.typography.headlineLarge.copy(fontSize = 30.sp),
                            modifier = Modifier.padding(top = 4.dp))
                    }
                }
                if (showDetails) {
                    Spacer(Modifier.height(16.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surface)
                    ) {
                        StatTile(stringResource(R.string.user_monthly_rate), Money.format(fees.rateFor(person.effectiveStatus)), Modifier.weight(1f))
                        Box(Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant))
                        StatTile(stringResource(R.string.user_current_status), statusLabel(person.effectiveStatus), Modifier.weight(1f), onStatusClick)
                    }
                }
            }
        }
    }
}

/** Centred label/value tile (`.user-stat`). */
@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Column(
        modifier
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CapsLabel(label)
        Text(value, style = MaterialTheme.typography.titleLarge, color = Agora.colors.heading, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp))
    }
}

private data class TimelineEntry(val date: String, val payment: Payment? = null, val status: String? = null)

/**
 * "Verlauf" like the PWA's generateTimelineHTML: payments and status changes in one list, newest first.
 * Status entries come from the history plus the current status since the last change (or since joining).
 */
@Composable
fun FinanceTimeline(person: Person, modifier: Modifier = Modifier) {
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
                    if (index < entries.lastIndex) Box(Modifier.padding(top = 4.dp).width(2.dp).weight(1f).background(MaterialTheme.colorScheme.outlineVariant))
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
                        Text(stringResource(R.string.timeline_status, statusLabel(entry.status)), style = MaterialTheme.typography.titleSmall, color = Agora.colors.heading)
                        Text(stringResource(R.string.timeline_valid_from, date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}