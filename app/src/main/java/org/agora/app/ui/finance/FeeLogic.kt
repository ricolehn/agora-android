package org.agora.app.ui.finance

import org.agora.app.data.model.FEE_STATUSES
import org.agora.app.data.model.FeeEntry
import org.agora.app.data.model.FeePlan
import org.agora.app.data.model.Payment
import org.agora.app.data.model.Person
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * The rules of the monthly fees over time and the fresh start (CALDERA) the app needs itself, the same as the web app
 * (assets/finance.js, assets/app.js). Everything that is calculated about the accounts comes from the server; these
 * only decide what is shown and when. Pure, so the unit tests check them.
 */
object FeeLogic {
    private val DAY = Regex("""^(\d{4})-(\d{2})-(\d{2})$""")
    private val SHOWN_DAY = Regex("""^(\d{1,2})\s*[./]\s*(\d{1,2})\s*[./]\s*(\d{4})$""")

    /** How long the member card names a fresh start: a notice, not a fixture - the history keeps the day for good. */
    const val NEWS_DAYS = 60L

    private fun day(value: String?): LocalDate? =
        value?.takeIf { DAY.matches(it) }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    /**
     * True when what the admin typed is the day [iso] - as the dialog shows it ("01.01.2027", "01/01/2027", also
     * "1.1.2027") or as "2027-01-01". Typing the day is the confirmation of a fresh start: a tap alone never starts one.
     */
    fun typedDayMatches(typed: String?, iso: String?): Boolean {
        val match = DAY.matchEntire(iso.orEmpty()) ?: return false
        val (year, month, dayOfMonth) = match.destructured
        val text = typed.orEmpty().trim()
        SHOWN_DAY.matchEntire(text)?.let { shown ->
            val (d, m, y) = shown.destructured
            return d.toInt() == dayOfMonth.toInt() && m.toInt() == month.toInt() && y == year
        }
        return text == iso
    }

    /** The day as the confirmation asks for it: "01.11.2026" (German) or "01/11/2026", like the web app. */
    fun typedDayText(iso: String, german: Boolean): String {
        val match = DAY.matchEntire(iso) ?: return iso
        val (year, month, dayOfMonth) = match.destructured
        return listOf(dayOfMonth, month, year).joinToString(if (german) "." else "/")
    }

    /**
     * True while the fresh start on [resetDay] is news on [today]: from its day on, for [NEWS_DAYS] days. Members only
     * learn of a fresh start once its day has come, so a day still to come only shows on a device whose clock is
     * behind - and counts as news.
     */
    fun isNews(resetDay: String?, today: LocalDate): Boolean {
        val reset = day(resetDay) ?: return false
        return ChronoUnit.DAYS.between(reset, today) < NEWS_DAYS
    }

    /**
     * The open fees a payment dated [paymentDay] is set against before it becomes credit, or 0. When the fee account
     * was started afresh, the open fees of the time before were dropped: a payment dated before that day belongs to
     * that time, so it first makes up for what was dropped - only what goes beyond it is carried over as credit.
     */
    fun droppedFeesBefore(person: Person?, paymentDay: String?): Double {
        val reset = person?.feeReset?.takeIf { DAY.matches(it) } ?: return 0.0
        if (paymentDay == null || !DAY.matches(paymentDay) || paymentDay >= reset) return 0.0
        return person.feeWrittenOff.coerceAtLeast(0.0)
    }

    /** What happened to a member's fee account on its fresh start. */
    enum class ResetOutcome { CARRIED_OVER, DROPPED, EVEN }

    fun resetOutcome(person: Person): ResetOutcome = when {
        person.feeCarriedOver > 0 -> ResetOutcome.CARRIED_OVER
        person.feeWrittenOff > 0 -> ResetOutcome.DROPPED
        else -> ResetOutcome.EVEN
    }

    // ---------- the fees over time ----------

    fun month(value: String?): YearMonth? = value?.let { runCatching { YearMonth.parse(it.take(7)) }.getOrNull() }

    /** 'YYYY-MM' with [count] months added. */
    fun addMonths(month: String, count: Long): String = (month(month) ?: YearMonth.now()).plusMonths(count).toString()

    /** The fees that count in [month] as the plan knows them (its entries come the earliest first). */
    fun feesOfMonth(plan: FeePlan, month: String): Map<String, Double> =
        plan.entries.lastOrNull { it.from == null || it.from <= month }?.fees.orEmpty()

    /** The statuses whose fee [entry] changed against the entry before it ([previous]); none for the first. */
    fun changedStatuses(previous: FeeEntry?, entry: FeeEntry): List<String> =
        if (previous == null) emptyList()
        else FEE_STATUSES.filter { (previous.fees[it] ?: 0.0) != (entry.fees[it] ?: 0.0) }

    /** What choosing a month means, said under it in the dialog. */
    sealed interface MonthHint {
        /** The fees from the beginning: until the first change ([firstChange]), or for every month. */
        data class FromStart(val firstChange: String?) : MonthHint
        /** There is a change from that month already: the new fees replace it. */
        data class Taken(val month: String) : MonthHint
        /** A month still to come: nothing moves before it. */
        data class Later(val month: String) : MonthHint
        data object Now : MonthHint
        /** A month that has passed: the change reaches back (said as a warning). */
        data class Back(val month: String) : MonthHint
    }

    /**
     * The hint for fees counting from [chosen] on: [base] for the fees from the beginning, [replaces] the month of
     * the change that is edited.
     */
    fun monthHint(plan: FeePlan, chosen: String, base: Boolean, replaces: String?): MonthHint = when {
        base -> MonthHint.FromStart(plan.entries.firstOrNull { it.from != null }?.from)
        chosen != replaces && plan.entries.any { it.from == chosen } -> MonthHint.Taken(chosen)
        chosen > plan.month -> MonthHint.Later(chosen)
        chosen == plan.month -> MonthHint.Now
        else -> MonthHint.Back(chosen)
    }

    /** The years the month of a change can be chosen from: fifteen back, ten ahead - and the year of [month]. */
    fun selectableYears(thisMonth: String, month: String?): List<Int> {
        val thisYear = month(thisMonth)?.year ?: LocalDate.now().year
        return ((thisYear - 15..thisYear + 10) + listOfNotNull(month(month)?.year)).distinct().sorted()
    }

    // ---------- the history of a member ----------

    /** One line of a member's history: a payment, a status taking effect, or the fresh start of the fee account. */
    data class TimelineItem(
        val date: String,
        val payment: Payment? = null,
        val status: String? = null,
        val reset: Boolean = false,
        /** Below the fresh start: the settled time before it, shown quieter. */
        val settled: Boolean = false
    )

    /**
     * Payments and status changes in one list, newest first - like the web app's history. A fresh start is a marked
     * point on the line; on its day it comes below that day's entries (they belong to the new account), and the
     * entries below it are the settled time before it.
     */
    fun timeline(person: Person): List<TimelineItem> {
        val items = buildList {
            person.statusHistory.forEach { add(TimelineItem(it.startDate, status = it.status)) }
            val currentStart = if (person.statusHistory.isNotEmpty()) person.statusHistory.last().endDate else person.originalMemberSince.ifBlank { person.memberSince }
            if (!currentStart.isNullOrBlank() && person.status.isNotBlank()) add(TimelineItem(currentStart, status = person.status))
            person.payments.forEach { add(TimelineItem(it.date, payment = it)) }
        }.filter { it.date.isNotBlank() }
        val reset = person.feeReset?.takeIf { DAY.matches(it) }
        // A history without entries stays empty (the screen says so), as on the web
        if (reset == null || items.isEmpty()) return items.sortedByDescending { it.date }
        val sorted = (items + TimelineItem(reset, reset = true))
            .sortedWith(compareByDescending<TimelineItem> { it.date }.thenBy { it.reset })
        val at = sorted.indexOfFirst { it.reset }
        return sorted.mapIndexed { i, item -> if (i > at) item.copy(settled = true) else item }
    }
}
