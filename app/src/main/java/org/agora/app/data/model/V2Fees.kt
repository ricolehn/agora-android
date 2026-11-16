package org.agora.app.data.model

import kotlinx.serialization.Serializable

// The fees over time and the fresh starts through API v2 (docs/API_V2.md, "The fees and fresh starts"): money in whole
// cents, moments as ISO 8601. Read into the app's own models (euros, milliseconds), so the fee settings show the same
// whichever API the server answers.

/** A fee change: the fees in cents of each income status from [fromMonth] on ('' or null: from the beginning). */
@Serializable
data class V2FeeChange(
    val fromMonth: String? = null,
    val feesCents: Map<String, Long?> = emptyMap(),
    /** current, planned or past */
    val state: String? = null,
    val setAt: String? = null,
    val setByName: String = ""
)

@Serializable
data class V2FeeAhead(val fromMonth: String = "", val feesCents: Map<String, Long?> = emptyMap())

@Serializable
data class V2FeeLogEntry(
    val action: String = "",
    val fromMonth: String? = null,
    val feesCents: Map<String, Long?>? = null,
    val movedFrom: String? = null,
    val at: String? = null,
    val byName: String = ""
)

/** `GET /fees`: what a month costs now, since when, the change still to come and every change over time. */
@Serializable
data class V2Fees(
    val today: String = "",
    val month: String = "",
    val currentCents: Map<String, Long?> = emptyMap(),
    val since: String? = null,
    val next: V2FeeAhead? = null,
    val feeChanges: List<V2FeeChange> = emptyList(),
    val version: String? = null,
    val log: List<V2FeeLogEntry> = emptyList()
)

/** How many members, and how much in total (cents). */
@Serializable
data class V2Side(val count: Int = 0, val totalCents: Long? = null)

@Serializable
data class V2Stretch(
    val first: String? = null,
    val last: String? = null,
    val beforeCents: Map<String, Long?>? = null,
    val afterCents: Map<String, Long?>? = null
)

/** `POST /fees/preview`: what a change of the fees would do before anything is saved. */
@Serializable
data class V2FeePreview(
    val fromMonth: String? = null,
    val remove: Boolean = false,
    val movedFrom: String? = null,
    val stretches: List<V2Stretch> = emptyList(),
    val inEffect: Boolean = false,
    val members: Int = 0,
    val more: V2Side? = null,
    val less: V2Side? = null,
    val freshStart: String? = null,
    val version: String = ""
)

@Serializable
data class V2FreshStartItem(
    val day: String = "",
    /** active, scheduled or replaced */
    val state: String = "",
    val feesBeforeCents: Map<String, Long?>? = null,
    val setAt: String? = null,
    val setByName: String = ""
)

@Serializable
data class V2FreshStartLogEntry(val action: String = "", val day: String = "", val at: String? = null, val byName: String = "")

/** `GET /fresh-starts`: the fresh starts of all fee accounts (who set them only for insiders). */
@Serializable
data class V2FreshStarts(
    val today: String = "",
    val items: List<V2FreshStartItem> = emptyList(),
    val log: List<V2FreshStartLogEntry> = emptyList()
)

/** `GET /fresh-starts/preview`: what a fresh start on [day] (or taking it back) would change. */
@Serializable
data class V2FreshStartPreview(
    val day: String = "",
    val inEffect: Boolean = false,
    val firstMonth: String = "",
    val members: Int = 0,
    val behind: V2Side? = null,
    val ahead: V2Side? = null,
    val owesMore: V2Side? = null
)

private fun eurosOf(fees: Map<String, Long?>?): Map<String, Double> =
    fees.orEmpty().mapNotNull { (status, cents) -> cents?.let { status to euros(it) } }.toMap()

private fun momentOf(iso: String?): Long? = iso?.let(::millisOf)?.takeIf { it > 0 }

private fun V2Side?.toSide(): FeeImpactSide = FeeImpactSide(this?.count ?: 0, euros(this?.totalCents))

/** The fees as the settings show them; none at all (a server without fee changes) reads as an empty plan. */
fun V2Fees.toPlan(): FeePlan = FeePlan(
    today = today,
    month = month,
    current = eurosOf(currentCents),
    since = since?.ifBlank { null },
    next = next?.let { FeeChangeAhead(it.fromMonth, eurosOf(it.feesCents)) },
    entries = feeChanges.map {
        FeeEntry(
            from = it.fromMonth?.ifBlank { null },
            fees = eurosOf(it.feesCents),
            state = it.state ?: "past",
            setAt = momentOf(it.setAt),
            setByName = it.setByName
        )
    },
    log = log.map { FeeLogEntry(it.action, it.fromMonth?.ifBlank { null }, it.feesCents?.let(::eurosOf), it.movedFrom, momentOf(it.at), it.byName) },
    version = version
)

fun V2FeePreview.toImpact(): FeeChangeImpact = FeeChangeImpact(
    from = fromMonth?.ifBlank { null },
    remove = remove,
    movedFrom = movedFrom,
    stretches = stretches.map { FeeStretch(it.first, it.last, eurosOf(it.beforeCents), eurosOf(it.afterCents)) },
    inEffect = inEffect,
    members = members,
    more = more.toSide(),
    less = less.toSide(),
    freshStart = freshStart,
    version = version
)

fun V2FreshStarts.toResets(): FeeResets = FeeResets(
    today = today,
    resets = items.map { FeeReset(it.day, it.state, it.feesBeforeCents?.let(::eurosOf), momentOf(it.setAt), it.setByName) },
    log = log.map { FeeResetLogEntry(it.action, it.day, momentOf(it.at), it.byName) }
)

fun V2FreshStartPreview.toImpact(): FeeResetImpact = FeeResetImpact(
    date = day,
    inEffect = inEffect,
    firstMonth = firstMonth,
    members = members,
    behind = behind.toSide(),
    ahead = ahead.toSide(),
    owesMore = owesMore.toSide()
)
