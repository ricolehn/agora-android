package org.agora.app.util

import android.content.Context
import androidx.core.os.ConfigurationCompat
import org.agora.app.R
import org.agora.app.data.model.AgoraEvent
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

object Money {
    private val euro = NumberFormat.getCurrencyInstance(Locale.GERMANY)
    fun format(amount: Double): String = euro.format(amount)
    fun signed(amount: Double, income: Boolean): String = (if (income) "+ " else "- ") + euro.format(kotlin.math.abs(amount))
}

object Dates {
    fun locale(context: Context): Locale = ConfigurationCompat.getLocales(context.resources.configuration)[0] ?: Locale.getDefault()

    fun todayIso(): String = LocalDate.now().toString()

    fun parse(iso: String?): LocalDate? = iso?.takeIf { it.length >= 10 }?.let { runCatching { LocalDate.parse(it.substring(0, 10)) }.getOrNull() }

    /** `_paidUntil` is an ISO instant of a server-local midnight; only its month matters. */
    fun paidUntilMonth(iso: String?): YearMonth? = iso?.let {
        runCatching { YearMonth.from(Instant.parse(it).plusSeconds(12 * 3600).atOffset(ZoneOffset.UTC)) }.getOrNull()
    }

    fun monthYear(month: YearMonth, locale: Locale): String =
        month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale).replaceFirstChar { it.titlecase(locale) } + " " + month.year

    fun long(date: LocalDate, locale: Locale): String = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale))

    fun medium(date: LocalDate, locale: Locale): String = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))

    fun short(iso: String, locale: Locale): String = parse(iso)?.let {
        it.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(locale))
    } ?: iso

    /** "OKT" for the calendar leaf. */
    fun monthShort(date: LocalDate, locale: Locale): String =
        date.format(DateTimeFormatter.ofPattern("MMM", locale)).trimEnd('.').uppercase(locale)

    /** Compact span for list cards (the year is in the month header): "17.–19. Okt." / "Oct 17–19". */
    fun spanShort(startIso: String, endIso: String, locale: Locale): String {
        val start = parse(startIso) ?: return startIso
        val end = parse(endIso) ?: return medium(start, locale)
        val month = DateTimeFormatter.ofPattern("MMM", locale)
        val sameMonth = start.month == end.month && start.year == end.year
        return if (locale.language == "de") {
            if (sameMonth) "${start.dayOfMonth}.–${end.dayOfMonth}. ${start.format(month)}"
            else "${start.dayOfMonth}. ${start.format(month)} – ${end.dayOfMonth}. ${end.format(month)}"
        } else {
            if (sameMonth) "${start.format(month)} ${start.dayOfMonth}–${end.dayOfMonth}"
            else "${start.format(month)} ${start.dayOfMonth} – ${end.format(month)} ${end.dayOfMonth}"
        }
    }

    fun weekdayShort(date: LocalDate, locale: Locale): String =
        date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale).trimEnd('.').replaceFirstChar { it.titlecase(locale) }

    fun timeRange(start: String, end: String): String = when {
        start.isBlank() -> ""
        end.isBlank() -> start
        else -> "$start – $end"
    }

    /** "Mi., 30. Sept. 2026" / "Wed, Sep 30, 2026" like the PWA's detail view (short weekday and month). */
    fun compact(date: LocalDate, locale: Locale): String =
        date.format(DateTimeFormatter.ofPattern(if (locale.language == "de") "EEE, d. MMM yyyy" else "EEE, MMM d, yyyy", locale))

    /** Multi-day span with the year once: "17. – 19. Okt. 2026", "30. Sept. – 2. Okt. 2026". */
    fun compactSpan(start: LocalDate, end: LocalDate, locale: Locale): String {
        val de = locale.language == "de"
        val month = DateTimeFormatter.ofPattern("MMM", locale)
        return when {
            start.year != end.year -> "${medium(start, locale)} – ${medium(end, locale)}"
            start.month == end.month ->
                if (de) "${start.dayOfMonth}. – ${end.dayOfMonth}. ${start.format(month)} ${start.year}"
                else "${start.format(month)} ${start.dayOfMonth} – ${end.dayOfMonth}, ${start.year}"
            else ->
                if (de) "${start.dayOfMonth}. ${start.format(month)} – ${end.dayOfMonth}. ${end.format(month)} ${start.year}"
                else "${start.format(month)} ${start.dayOfMonth} – ${end.format(month)} ${end.dayOfMonth}, ${start.year}"
        }
    }

    /** "Mi., 30. Sept. 2026 · 10:00 – 11:30 Uhr" or a span for multi-day events (PWA detail view). */
    fun eventWhen(context: Context, event: AgoraEvent): String {
        val locale = locale(context)
        val start = parse(event.date) ?: return event.date
        if (event.isMultiDay) {
            val end = parse(event.endDate)
            return if (end != null && end != start) compactSpan(start, end, locale) else compact(start, locale)
        }
        val time = timeRange(event.startTime, event.endTime)
        return if (time.isBlank()) "${compact(start, locale)} · ${context.getString(R.string.all_day)}"
        else "${compact(start, locale)} · $time ${context.getString(R.string.time_suffix)}".trim()
    }

    /** "21.09.2026, 16:16" for request timestamps (epoch millis). */
    fun dateTime(epochMillis: Long, locale: Locale): String =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault())
            .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(locale))

    /** Chat timestamps: time for today, otherwise the date. */
    fun chatTime(iso: String, locale: Locale): String {
        val instant = runCatching { Instant.parse(iso.replace(' ', 'T').let { if (it.endsWith("Z")) it else it + "Z" }) }.getOrNull() ?: return ""
        val local = LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
        return if (local.toLocalDate() == LocalDate.now()) local.format(DateTimeFormatter.ofPattern("HH:mm"))
        else local.toLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(locale))
    }
}
