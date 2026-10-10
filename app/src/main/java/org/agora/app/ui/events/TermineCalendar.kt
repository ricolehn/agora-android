package org.agora.app.ui.events

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.agora.app.R
import org.agora.app.data.model.AgoraEvent
import org.agora.app.ui.components.AgoraCard
import org.agora.app.ui.theme.Agora
import org.agora.app.util.Dates
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle

/** Category of an entry in the Termine calendar, in the order of its dots (same colours as the card stripes). */
enum class TerminCategory { Pinned, Event, Termin }

fun terminCategory(event: AgoraEvent): TerminCategory = when {
    event.isPinned -> TerminCategory.Pinned
    !event.isTermin -> TerminCategory.Event
    else -> TerminCategory.Termin
}

@Composable
private fun TerminCategory.color(): Color = when (this) {
    TerminCategory.Pinned -> Agora.colors.duty
    TerminCategory.Event -> MaterialTheme.colorScheme.primary
    TerminCategory.Termin -> Color(0xFF94A3B8)
}

/**
 * Month calendar next to the Termine list on tablets (like the web from 1024px): a dot per category on days with
 * entries, today outlined, the chosen day filled; a tap on a day lets the list scroll there. Past days are faded.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TermineCalendar(
    month: YearMonth,
    onMonth: (YearMonth) -> Unit,
    days: Map<String, Set<TerminCategory>>,
    selected: String?,
    today: String,
    onDay: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val locale = Dates.locale(LocalContext.current)
    val currentMonth = Dates.parse(today)?.let(YearMonth::from) ?: month
    AgoraCard(modifier, contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            NavButton(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, stringResource(R.string.calendar_prev_month), enabled = month > currentMonth) { onMonth(month.minusMonths(1)) }
            Text(
                Dates.monthYear(month, locale).replaceFirstChar { it.titlecase(locale) },
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold), color = Agora.colors.heading,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.weight(1f)
            )
            NavButton(Icons.AutoMirrored.Outlined.KeyboardArrowRight, stringResource(R.string.calendar_next_month), enabled = true) { onMonth(month.plusMonths(1)) }
        }
        // Weekdays from Monday
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            DayOfWeek.entries.forEach { day ->
                Text(
                    day.getDisplayName(TextStyle.SHORT, locale).removeSuffix(".").uppercase(locale),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 0.4.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.weight(1f)
                )
            }
        }
        val lead = month.atDay(1).dayOfWeek.value - 1
        val cells: List<LocalDate?> = List(lead) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { date ->
                    Box(Modifier.weight(1f).aspectRatio(1f)) {
                        if (date != null) DayCell(date.toString(), date.dayOfMonth, days[date.toString()], selected, today, onDay)
                    }
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        HorizontalDivider(Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
        FlowRow(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                TerminCategory.Termin to R.string.event_badge_termin,
                TerminCategory.Event to R.string.event_label_event,
                TerminCategory.Pinned to R.string.event_label_highlight
            ).forEach { (category, label) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(category.color()))
                    Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
    }
}

@Composable
private fun NavButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick, enabled = enabled, shape = RoundedCornerShape(11.dp), color = Agora.colors.surfaceAlt,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.size(36.dp).alpha(if (enabled) 1f else 0.35f)
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, label, Modifier.size(20.dp), tint = Agora.colors.heading) }
    }
}

@Composable
private fun DayCell(day: String, number: Int, categories: Set<TerminCategory>?, selected: String?, today: String, onDay: (String) -> Unit) {
    val isPast = day < today
    val isSelected = day == selected
    val isToday = day == today
    val primary = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(shape)
            .then(if (isSelected) Modifier.background(primary) else Modifier)
            .then(if (isToday && !isSelected) Modifier.border(2.dp, primary, shape) else Modifier)
            .clickable(enabled = !isPast) { onDay(day) }
            .alpha(if (isPast) 0.45f else 1f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "$number",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (categories != null || isToday) FontWeight.ExtraBold else FontWeight.SemiBold),
            color = when {
                isSelected -> Color.White
                isToday -> primary
                isPast -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> Agora.colors.heading
            }
        )
        Row(Modifier.padding(top = 3.dp).height(6.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            TerminCategory.entries.filter { categories?.contains(it) == true }.forEach { category ->
                Box(Modifier.size(6.dp).clip(CircleShape).background(if (isSelected) Color.White else category.color()))
            }
        }
    }
}
