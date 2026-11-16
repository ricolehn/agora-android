package org.agora.app.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant

// The books as API v2 hands them out (server CALDERA, docs/API_V2.md in the Agora repository): record ids, money in
// whole cents, days as 'YYYY-MM-DD', moments in ISO 8601 (UTC). Only the fields the app reads; v2 only grows, so
// anything else is ignored. Amounts are null where no whole number of cents can hold them (an absurd old value).

/** The fresh start a fee account counts from, with what happened on its day. */
@Serializable
data class V2FreshStart(val day: String = "", val carriedOverCents: Long = 0, val writtenOffCents: Long = 0)

/**
 * A member of the books (`GET /api/v2/members`, one of them with its rows: `/members/:id`): its record [id], the
 * [key] the old API knows it by, its [version] (a change of its books names it) and the fee account as the server
 * works it out. [feeState]: `overdue`, `standingOrder` (paid by a running standing order) or `ok` - anything else
 * counts as in order.
 */
@Serializable
data class V2Member(
    val id: String = "",
    val key: String = "",
    val userId: String? = null,
    val name: String = "",
    val status: String? = null,
    val memberSince: String? = null,
    val originalMemberSince: String? = null,
    val pays: Boolean = true,
    val isDeleted: Boolean = false,
    val totalPaidCents: Long? = null,
    val balanceCents: Long? = null,
    val overdueCents: Long? = null,
    val monthlyFeeCents: Long? = null,
    val paidUntil: String? = null,
    val isOverdue: Boolean = false,
    val currentStatus: String? = null,
    val feeState: String? = null,
    val isDueSoon: Boolean = false,
    val hasActiveStandingOrder: Boolean = false,
    val anticipatedCents: Long? = null,
    val freshStart: V2FreshStart? = null,
    val version: String = "",
    // Only one member with its rows
    val payments: List<V2Payment> = emptyList(),
    val statusPeriods: List<V2StatusPeriod> = emptyList(),
    val standingOrders: List<V2StandingOrder> = emptyList()
)

/**
 * A member's payment. [memberVersion]: the version of the member's books, which a change of it names. [source]:
 * `manual`, `request` or `standingOrder` (anything else counts as booked by hand). [clientId]: the id the entry has in
 * the member's record (a number or a text; null for old entries without one).
 */
@Serializable
data class V2Payment(
    val id: String = "",
    val memberId: String = "",
    val amountCents: Long? = null,
    val date: String? = null,
    val description: String = "",
    val receipt: String? = null,
    val receipts: List<String> = emptyList(),
    val source: String = "manual",
    val clientId: JsonElement? = null,
    val memberVersion: String? = null
)

@Serializable
data class V2StatusPeriod(val id: String = "", val status: String? = null, val startDate: String? = null, val endDate: String? = null)

/** A standing order; [lastBookedOn]: the day of the month it booked last. */
@Serializable
data class V2StandingOrder(
    val id: String = "",
    val memberId: String = "",
    val amountCents: Long? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val note: String = "",
    val lastBookedOn: String? = null,
    val memberVersion: String? = null
)

@Serializable
data class V2Donation(
    val id: String = "",
    val amountCents: Long? = null,
    val date: String? = null,
    val donorName: String = "",
    val description: String = "",
    val listVersion: String? = null
)

/** An expense; [receipts]: the names of its receipts. */
@Serializable
data class V2Expense(
    val id: String = "",
    val amountCents: Long? = null,
    val date: String? = null,
    val issuer: String = "",
    val description: String = "",
    val receipts: List<String> = emptyList(),
    val listVersion: String? = null
)

/**
 * A booking of the history (`GET /api/v2/finance/ledger`): [kind] `memberPayment`, `donation` or `expense`; [who] the
 * member, donor or issuer. Changed through its own resource, on the version read for it then.
 */
@Serializable
data class V2LedgerEntry(
    val kind: String = "",
    val id: String = "",
    val amountCents: Long? = null,
    val date: String? = null,
    val who: String = "",
    val memberId: String? = null,
    val description: String = "",
    val receipts: List<String> = emptyList(),
    val source: String? = null
)

/**
 * A request (`GET /api/v2/requests`). [memberId]: the member it is about (null for someone without a record of the
 * books); [details]: what was asked, as the app sent it; [rejectionReason] null: none given.
 */
@Serializable
data class V2Request(
    val id: String = "",
    val type: String? = null,
    val status: String? = null,
    val userId: String? = null,
    val memberId: String? = null,
    val memberName: String = "",
    val amountCents: Long? = null,
    val details: JsonElement? = null,
    val receipts: List<String> = emptyList(),
    val createdAt: String? = null,
    val decidedAt: String? = null,
    val rejectionReason: String? = null
)

/** The money as the app's statistics count it (`GET /api/v2/finance/summary`): booked up to today. */
@Serializable
data class V2Summary(val today: String = "", val totalsCents: V2Totals = V2Totals())

@Serializable
data class V2Totals(val memberPayments: Long = 0, val donations: Long = 0, val expenses: Long = 0, val balance: Long = 0)

/** What the server names an uploaded receipt (`POST /api/v2/receipts`). */
@Serializable
data class V2Upload(val name: String = "")

// --- The app's models from them -------------------------------------------------------------------------------------

/** Euros of whole [cents]: 0 for an amount nobody can read (as the old API's statistics count it). */
fun euros(cents: Long?): Double = (cents ?: 0L) / 100.0

/** Whole cents of [amount] in euros (rounded to the cent, as the server reads an amount). */
fun cents(amount: Double): Long = Math.round(amount * 100.0)

/** Milliseconds of an ISO moment, 0 without one. */
internal fun millisOf(moment: String?): Long = moment?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0L

/** The receipts as the app keeps them in one field: the web app's list as JSON text; none: null. */
fun receiptField(names: List<String>): String? =
    if (names.isEmpty()) null else kotlinx.serialization.json.JsonArray(names.map(::JsonPrimitive)).toString()

// What an entry holds, as a change of it compares it - the same for an entry of the history and the entry read again
fun paymentPrint(amountCents: Long?, date: String?, description: String, receipts: List<String>) = listOf(amountCents, date.orEmpty(), description, receipts).joinToString("\u0000")
fun donationPrint(amountCents: Long?, date: String?, donorName: String, description: String) = listOf(amountCents, date.orEmpty(), donorName, description).joinToString("\u0000")
fun expensePrint(amountCents: Long?, date: String?, issuer: String, description: String, receipts: List<String>) = listOf(amountCents, date.orEmpty(), issuer, description, receipts).joinToString("\u0000")

val V2Payment.fingerprint: String get() = paymentPrint(amountCents, date, description, receipts)
val V2Donation.fingerprint: String get() = donationPrint(amountCents, date, donorName, description)
val V2Expense.fingerprint: String get() = expensePrint(amountCents, date, issuer, description, receipts)

/** The text the old API's fee account says (`_statusMeta.text`, a fixed protocol value the screens translate). */
private fun feeStateText(state: String?) = when (state) {
    "overdue" -> "Zahlung überfällig"
    "standingOrder" -> "Dauerauftrag läuft"
    else -> "Alles in Ordnung"
}

/**
 * The member as the app's screens show a member record: the fee account as the server worked it out, and - for one
 * read with its rows - its payments, standing orders and status history. [Person.id] stays the key the old API knows
 * the member by; [Person.memberId] and [Person.version] are what a change of its books through v2 names.
 */
fun V2Member.toPerson(): Person = Person(
    id = key.ifEmpty { id },
    uid = userId.orEmpty(),
    name = name,
    status = status.orEmpty(),
    memberSince = memberSince.orEmpty(),
    originalMemberSince = originalMemberSince.orEmpty(),
    pays = pays,
    totalPaid = euros(totalPaidCents),
    payments = payments.map { Payment(id = it.id, amount = euros(it.amountCents), date = it.date.orEmpty(), description = it.description, isAuto = it.source == "standingOrder") },
    standingOrders = standingOrders.map {
        StandingOrder(id = it.id, amount = euros(it.amountCents), startDate = it.startDate.orEmpty(), endDate = it.endDate, note = it.note, lastAutoPayment = it.lastBookedOn)
    },
    statusHistory = statusPeriods.sortedBy { it.startDate.orEmpty() }.map { StatusHistoryEntry(it.status.orEmpty(), it.startDate.orEmpty(), it.endDate) },
    isDeleted = isDeleted,
    paidUntil = paidUntil,
    statusMeta = StatusMeta(
        text = feeStateText(feeState),
        isOverdue = feeState == "overdue" || isOverdue,
        isSoonDue = isDueSoon,
        isActiveStandingOrder = hasActiveStandingOrder
    ),
    overdueAmount = euros(overdueCents),
    currentStatus = currentStatus,
    currentBalance = euros(balanceCents),
    feeReset = freshStart?.day?.takeIf { it.isNotEmpty() },
    feeCarriedOver = euros(freshStart?.carriedOverCents),
    feeWrittenOff = euros(freshStart?.writtenOffCents),
    monthlyFee = monthlyFeeCents?.let(::euros),
    memberId = id,
    version = version
)

/**
 * A booking of the history; [members]: the members by record id (the account of a payment's member, its key). Null for
 * a kind of booking the app does not know (v2 may grow one): whether it is income or an expense cannot be told.
 */
fun V2LedgerEntry.toTransaction(members: Map<String, V2Member>): Transaction? {
    val member = memberId?.let(members::get)
    val type = when (kind) {
        "memberPayment" -> "pay"
        "donation" -> "don"
        "expense" -> "exp"
        else -> return null
    }
    val print = when (type) {
        "don" -> donationPrint(amountCents, date, who, description)
        "exp" -> expensePrint(amountCents, date, who, description, receipts)
        else -> paymentPrint(amountCents, date, description, receipts)
    }
    // Approved expense requests of older versions kept the requester only as "(Von: Name)" in the description
    val legacy = if (type == "exp" && who.isBlank()) FROM_IN_DESCRIPTION.matchEntire(description) else null
    return Transaction(
        id = id,
        type = type,
        who = legacy?.groupValues?.get(2)?.trim() ?: who,
        date = date.orEmpty(),
        amount = euros(amountCents),
        description = legacy?.groupValues?.get(1)?.trim() ?: description,
        personUid = member?.userId,
        personId = member?.key.orEmpty(),
        receipt = receiptField(receipts),
        isAuto = source == "standingOrder",
        // The version is read when it is changed: the history names none
        booking = V2Booking(id, "", memberId, amountCents, print)
    )
}

/** Approved expense requests of older versions kept the requester only as "(Von: Name)" in the description. */
private val FROM_IN_DESCRIPTION = Regex("""^(.*?)\s*\(Von: ([^)]+)\)\s*$""", RegexOption.DOT_MATCHES_ALL)

/** The request as the app shows it; [members]: the members by record id (for the key of the member it is about). */
fun V2Request.toFinanceRequest(members: Map<String, V2Member>): FinanceRequest {
    val member = memberId?.let(members::get)
    return FinanceRequest(
        id = id,
        type = type.orEmpty(),
        userId = userId.orEmpty(),
        personId = member?.key ?: memberId.orEmpty(),
        personName = memberName.ifBlank { member?.name.orEmpty() },
        data = details as? JsonObject ?: JsonObject(emptyMap()),
        status = status ?: "pending",
        rejectionReason = rejectionReason,
        timestamp = millisOf(createdAt),
        v2 = true,
        memberId = memberId,
        amountCents = amountCents
    )
}

/** The cash balance as the statistics count it, and all income and expenses booked up to today. */
fun V2Summary.toStats(): FinanceStats = FinanceStats(
    totalBalance = euros(totalsCents.balance),
    totalIncome = euros(totalsCents.memberPayments + totalsCents.donations),
    totalExpenses = euros(totalsCents.expenses)
)

/** The text of a JSON id (a number or a text), null for none. */
internal fun JsonElement?.idText(): String? = (this as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content?.takeIf { it.isNotEmpty() }
