package org.agora.app.data.model

import kotlinx.serialization.Serializable

// CALDERA (server CALDERA): the monthly fees over time and the fresh start of the fee accounts. The server works out
// every number; the app shows them, and system admins change them in two steps (what it does, then save).

/** The statuses whose fees an admin sets, in the order of the fee settings ("pausiert" costs nothing). */
val FEE_STATUSES = listOf("vollverdiener", "geringverdiener", "keinverdiener")

/** `GET /api/fees`: what a month costs now, since when, the change still to come and every entry over time. */
@Serializable
data class FeePlan(
    val today: String = "",
    /** This month, 'YYYY-MM'. */
    val month: String = "",
    val current: Map<String, Double> = emptyMap(),
    /** The month the current fees count from; null: from the beginning. */
    val since: String? = null,
    val next: FeeChangeAhead? = null,
    /** The fees from the beginning (from null) first, then every change, the earliest first. */
    val entries: List<FeeEntry> = emptyList(),
    /** Finance or admin rights only. */
    val log: List<FeeLogEntry> = emptyList(),
    val version: String? = null
)

@Serializable
data class FeeChangeAhead(val from: String = "", val fees: Map<String, Double> = emptyMap())

/** One entry of the fees over time; [state]: current, planned or past. */
@Serializable
data class FeeEntry(
    val from: String? = null,
    val fees: Map<String, Double> = emptyMap(),
    val state: String = "past",
    val setAt: Long? = null,
    val setByName: String = ""
)

@Serializable
data class FeeLogEntry(
    /** set or removed */
    val action: String = "",
    val from: String? = null,
    val fees: Map<String, Double>? = null,
    val movedFrom: String? = null,
    val at: Long? = null,
    val byName: String = ""
)

/** `GET /api/fee-reset`: the days the fee accounts were (or will be) started afresh. */
@Serializable
data class FeeResets(
    val today: String = "",
    val resets: List<FeeReset> = emptyList(),
    /** Finance or admin rights only. */
    val log: List<FeeResetLogEntry> = emptyList()
)

/** One fresh start; [state]: active (counted from), scheduled or replaced. */
@Serializable
data class FeeReset(
    val date: String = "",
    val state: String = "",
    val feesBefore: Map<String, Double>? = null,
    val setAt: Long? = null,
    val setByName: String = ""
)

@Serializable
data class FeeResetLogEntry(val action: String = "", val date: String = "", val at: Long? = null, val byName: String = "")

/** How many members, and how much in total. */
@Serializable
data class FeeImpactSide(val count: Int = 0, val total: Double = 0.0)

/** `GET /api/admin/fee-reset/preview`: what setting (or taking back) a fresh start changes. */
@Serializable
data class FeeResetImpact(
    val date: String = "",
    val inEffect: Boolean = false,
    val firstMonth: String = "",
    val members: Int = 0,
    val behind: FeeImpactSide = FeeImpactSide(),
    val ahead: FeeImpactSide = FeeImpactSide(),
    val owesMore: FeeImpactSide = FeeImpactSide()
)

/** A stretch of months that costs something else: first null from the beginning, last null open end. */
@Serializable
data class FeeStretch(
    val first: String? = null,
    val last: String? = null,
    val before: Map<String, Double> = emptyMap(),
    val after: Map<String, Double> = emptyMap()
)

/** `POST /api/admin/fees/preview`: what a change of the fees does, before anything is saved. */
@Serializable
data class FeeChangeImpact(
    val from: String? = null,
    val remove: Boolean = false,
    val movedFrom: String? = null,
    val stretches: List<FeeStretch> = emptyList(),
    val inEffect: Boolean = false,
    val members: Int = 0,
    val more: FeeImpactSide = FeeImpactSide(),
    val less: FeeImpactSide = FeeImpactSide(),
    /** The fresh start the change reaches back before, else null. */
    val freshStart: String? = null,
    val version: String = ""
)

/**
 * What an admin wants to change: new fees counting from [from] ('YYYY-MM'; null: the fees from the beginning), with
 * [replaces] the month of the change that is edited or moved - or, with [remove], the change of [from] taken back.
 */
data class FeeChange(
    val from: String?,
    val fees: Map<String, Double> = emptyMap(),
    val replaces: String? = null,
    val remove: Boolean = false
)
