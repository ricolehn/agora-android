package org.agora.app.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.agora.app.data.model.Transaction
import org.agora.app.data.model.TransactionPage
import org.agora.app.data.model.V2Booking
import org.agora.app.data.model.V2Donation
import org.agora.app.data.model.V2Expense
import org.agora.app.data.model.V2LedgerEntry
import org.agora.app.data.model.V2Member
import org.agora.app.data.model.V2Payment
import org.agora.app.data.model.V2Request
import org.agora.app.data.model.V2StandingOrder
import org.agora.app.data.model.V2Summary
import org.agora.app.data.model.V2Upload
import org.agora.app.data.model.fingerprint
import org.agora.app.data.model.toTransaction
import org.agora.app.data.remote.AgoraApi
import org.agora.app.data.remote.ApiException
import org.agora.app.data.remote.ApiV2

/**
 * The books through API v2 (server CALDERA): members with their fee accounts, payments, standing orders, status
 * changes, donations, expenses, receipts, requests, the history and the money summary.
 *
 * Every change names the version of the data it is based on - a payment, a standing order or a status change its
 * member's version, a donation or an expense the version of its list - and is refused (409) when that data changed
 * meanwhile. Then the data is read again and the change repeated, but only while it still is the change that was
 * asked: a new booking always (nothing it could overwrite), a change or deletion only while the entry is still what
 * the treasurer opened ([V2Booking.fingerprint]); otherwise nothing is written and [Bookings.GoneException] tells the
 * history to show what is stored now.
 */
class BooksV2(private val v2: ApiV2, private val api: AgoraApi) {

    // --- Members --------------------------------------------------------------------------------------------------

    /** The members as last read, by record id (the names of the history, the ids for a booking). */
    @Volatile private var directory: Map<String, V2Member>? = null

    /**
     * Every member the person may see, by record id, with their fee accounts - those with finance rights all of them,
     * deleted ones included (their payments stay in the books), everyone else their own (and a record no account is
     * linked to that bears their name). [fresh] false: the members as last read, if any.
     */
    suspend fun members(fresh: Boolean = true): Map<String, V2Member> {
        if (!fresh) directory?.let { return it }
        return v2.all("/members", V2Member.serializer(), mapOf("includeDeleted" to "1")).items
            .associateBy { it.id }.also { directory = it }
    }

    /** The member the old API knows by [key] (the app's member records keep it as their id), or null. */
    suspend fun memberByKey(key: String): V2Member? =
        members(fresh = false).values.firstOrNull { it.key == key } ?: members().values.firstOrNull { it.key == key }

    /** One member with its payments, standing orders and status periods. */
    suspend fun member(id: String): V2Member = v2.call("GET", "/members/$id", V2Member.serializer())

    /** The member [id], or null when it is gone. */
    private suspend fun memberOrNull(id: String): V2Member? = try {
        member(id)
    } catch (e: ApiException) {
        if (e.status == 404) null else throw e
    }

    /** The version the books of member [memberId] have now. */
    suspend fun memberVersion(memberId: String): String = member(memberId).version

    /**
     * A change of member [memberId]'s books that adds or sets something ([change] gets the version): a newer version
     * is read again and the change made on it - it overwrites nothing someone else did.
     */
    private suspend fun <T> onMember(memberId: String, version: String?, change: suspend (version: String) -> T): T {
        var current = version?.takeIf { it.isNotEmpty() } ?: memberVersion(memberId)
        repeat(ATTEMPTS) { attempt ->
            try {
                return change(current)
            } catch (e: ApiException) {
                if (e.status != 409 || attempt == ATTEMPTS - 1) throw e
            }
            current = memberVersion(memberId)
        }
        throw IllegalStateException("unreachable")
    }

    // --- Payments -------------------------------------------------------------------------------------------------

    /**
     * Books a payment for member [memberId] ([version]: the version of its books as read, null: read it now). With
     * [requestId] it is the booking of that request: booked once, however often it is sent.
     */
    suspend fun addPayment(memberId: String, version: String?, amountCents: Long, date: String, description: String, requestId: String? = null): V2Payment =
        onMember(memberId, version) { current ->
            v2.call("POST", "/payments", V2Payment.serializer(), buildJsonObject {
                put("memberId", memberId)
                put("memberVersion", current)
                put("amountCents", amountCents)
                put("date", date)
                put("description", description)
                requestId?.let { put("requestId", it) }
            })
        }

    /** The payment [recordId] of member [memberId] as it is stored now (what it holds, the version), or null when it is gone. */
    private suspend fun storedPayment(memberId: String, recordId: String): Pair<String, String>? =
        memberOrNull(memberId)?.let { member -> member.payments.firstOrNull { it.id == recordId }?.let { it.fingerprint to member.version } }

    /** Changes the payment [booking] to [amountCents] (null: the amount stays), [date] and [description]. */
    suspend fun editPayment(booking: V2Booking, amountCents: Long?, date: String, description: String) {
        val memberId = booking.memberId ?: throw Bookings.GoneException()
        changeEntry(booking, { storedPayment(memberId, booking.recordId) }) { version ->
            v2.call("PATCH", "/payments/${booking.recordId}", JsonObject.serializer(), buildJsonObject {
                put("memberVersion", version)
                amountCents?.let { put("amountCents", it) }
                put("date", date)
                put("description", description)
            })
        }
    }

    suspend fun deletePayment(booking: V2Booking) {
        val memberId = booking.memberId ?: throw Bookings.GoneException()
        changeEntry(booking, { storedPayment(memberId, booking.recordId) }) { version ->
            v2.call("DELETE", "/payments/${booking.recordId}", JsonObject.serializer(), query = mapOf("memberVersion" to version))
        }
    }

    // --- Standing orders and status changes ---------------------------------------------------------------------

    /** A standing order for member [memberId]: what it owes from its start up to today is booked at once. */
    suspend fun addStandingOrder(memberId: String, version: String?, amountCents: Long, startDate: String, note: String): V2StandingOrder =
        onMember(memberId, version) { current ->
            v2.call("POST", "/standing-orders", V2StandingOrder.serializer(), buildJsonObject {
                put("memberId", memberId)
                put("memberVersion", current)
                put("amountCents", amountCents)
                put("startDate", startDate)
                put("note", note)
            })
        }

    /** The standing order [orderId] of member [memberId] as it is stored now, or null when it is gone. */
    private suspend fun storedOrder(memberId: String, orderId: String): Pair<V2StandingOrder, String>? =
        memberOrNull(memberId)?.let { member -> member.standingOrders.firstOrNull { it.id == orderId }?.let { it to member.version } }

    /**
     * Ends the standing order [orderId] on [endDate] (also a day that has passed: what it booked after it is taken back)
     * - on a newer version of the member's books again, as long as the order is still there.
     */
    suspend fun endStandingOrder(memberId: String, version: String?, orderId: String, endDate: String) =
        changeOrder(memberId, version, orderId) { current ->
            v2.call("PATCH", "/standing-orders/$orderId", JsonObject.serializer(), buildJsonObject {
                put("memberVersion", current)
                put("endDate", endDate)
            })
        }

    /** Removes the standing order [orderId]; the payments it booked stay. */
    suspend fun deleteStandingOrder(memberId: String, version: String?, orderId: String) =
        changeOrder(memberId, version, orderId) { current ->
            v2.call("DELETE", "/standing-orders/$orderId", JsonObject.serializer(), query = mapOf("memberVersion" to current))
        }

    private suspend fun changeOrder(memberId: String, version: String?, orderId: String, change: suspend (String) -> Unit) {
        var current = version?.takeIf { it.isNotEmpty() } ?: (storedOrder(memberId, orderId) ?: throw Bookings.GoneException()).second
        repeat(ATTEMPTS) { attempt ->
            try {
                change(current)
                return
            } catch (e: ApiException) {
                if (e.status == 404) throw Bookings.GoneException()
                if (e.status != 409 || attempt == ATTEMPTS - 1) throw e
            }
            current = (storedOrder(memberId, orderId) ?: throw Bookings.GoneException()).second
        }
    }

    /** Member [memberId]'s income status from [date] on, as the web app changes it (the server rewrites the history). */
    suspend fun changeStatus(memberId: String, version: String?, status: String, date: String) {
        onMember(memberId, version) { current ->
            v2.call("POST", "/status-changes", JsonObject.serializer(), buildJsonObject {
                put("memberId", memberId)
                put("memberVersion", current)
                put("status", status)
                put("date", date)
            })
        }
    }

    // --- Donations and expenses: lists with a version -----------------------------------------------------------

    suspend fun donations(): Pair<List<V2Donation>, String?> = v2.all("/donations", V2Donation.serializer()).let { it.items to it.listVersion }
    suspend fun expenses(): Pair<List<V2Expense>, String?> = v2.all("/expenses", V2Expense.serializer()).let { it.items to it.listVersion }

    /** The version a list ("donations", "expenses") has now (its first page names it). */
    private suspend fun listVersion(list: String): String =
        v2.page("/$list", JsonObject.serializer(), limit = 1).listVersion.orEmpty()

    suspend fun addDonation(amountCents: Long, date: String, donorName: String, description: String): V2Donation =
        addToList("donations", V2Donation.serializer()) { version ->
            buildJsonObject {
                put("listVersion", version)
                put("amountCents", amountCents)
                put("date", date)
                put("donorName", donorName)
                put("description", description)
            }
        }

    /** [receipts]: the names of its receipts ([uploadReceipt]). */
    suspend fun addExpense(amountCents: Long, date: String, issuer: String, description: String, receipts: List<String>): V2Expense =
        addToList("expenses", V2Expense.serializer()) { version ->
            buildJsonObject {
                put("listVersion", version)
                put("amountCents", amountCents)
                put("date", date)
                put("issuer", issuer)
                put("description", description)
                put("receipts", JsonArray(receipts.map(::JsonPrimitive)))
            }
        }

    /** A new entry of [list]: as a new payment, it overwrites nothing - a newer list is read again and it goes on. */
    private suspend fun <T> addToList(list: String, serializer: KSerializer<T>, body: (version: String) -> JsonObject): T {
        var version = listVersion(list)
        repeat(ATTEMPTS) { attempt ->
            try {
                return v2.call("POST", "/$list", serializer, body(version))
            } catch (e: ApiException) {
                if (e.status != 409 || attempt == ATTEMPTS - 1) throw e
            }
            version = listVersion(list)
        }
        throw IllegalStateException("unreachable")
    }

    private suspend fun storedDonation(recordId: String) = donations().let { (items, version) -> items.firstOrNull { it.id == recordId }?.let { it.fingerprint to version.orEmpty() } }
    private suspend fun storedExpense(recordId: String) = expenses().let { (items, version) -> items.firstOrNull { it.id == recordId }?.let { it.fingerprint to version.orEmpty() } }

    /** Changes the donation [booking]; [donorName] null: who gave it stays. */
    suspend fun editDonation(booking: V2Booking, amountCents: Long?, date: String, donorName: String?, description: String) =
        changeEntry(booking, { storedDonation(booking.recordId) }) { version ->
            v2.call("PATCH", "/donations/${booking.recordId}", JsonObject.serializer(), buildJsonObject {
                put("listVersion", version)
                amountCents?.let { put("amountCents", it) }
                put("date", date)
                donorName?.let { put("donorName", it) }
                put("description", description)
            })
        }

    /** Changes the expense [booking]; [issuer] null: who issued it stays; [receipts] null: they stay (empty: none). */
    suspend fun editExpense(booking: V2Booking, amountCents: Long?, date: String, issuer: String?, description: String, receipts: List<String>?) =
        changeEntry(booking, { storedExpense(booking.recordId) }) { version ->
            v2.call("PATCH", "/expenses/${booking.recordId}", JsonObject.serializer(), buildJsonObject {
                put("listVersion", version)
                amountCents?.let { put("amountCents", it) }
                put("date", date)
                issuer?.let { put("issuer", it) }
                put("description", description)
                receipts?.let { files -> put("receipts", JsonArray(files.map(::JsonPrimitive))) }
            })
        }

    suspend fun deleteDonation(booking: V2Booking) = changeEntry(booking, { storedDonation(booking.recordId) }) { version ->
        v2.call("DELETE", "/donations/${booking.recordId}", JsonObject.serializer(), query = mapOf("listVersion" to version))
    }

    suspend fun deleteExpense(booking: V2Booking) = changeEntry(booking, { storedExpense(booking.recordId) }) { version ->
        v2.call("DELETE", "/expenses/${booking.recordId}", JsonObject.serializer(), query = mapOf("listVersion" to version))
    }

    /**
     * Runs [change] on the entry [booking] with the version it was read with - read now when it was read without one
     * (the history names none), while the entry is still what was opened. Refused because the data changed meanwhile
     * (409), the entry is read again ([stored]: what it holds now and the version, null when it is gone): still as it
     * was opened, the change is repeated on the new version; changed or gone, nothing is written
     * ([Bookings.GoneException]). Gone already (404): the same.
     */
    private suspend fun changeEntry(booking: V2Booking, stored: suspend () -> Pair<String, String>?, change: suspend (version: String) -> Unit) {
        suspend fun current(): String {
            val (fingerprint, version) = stored() ?: throw Bookings.GoneException()
            if (fingerprint != booking.fingerprint || version.isEmpty()) throw Bookings.GoneException()
            return version
        }
        var version = booking.version.ifEmpty { current() }
        repeat(ATTEMPTS) { attempt ->
            try {
                change(version)
                return
            } catch (e: ApiException) {
                if (e.status == 404) throw Bookings.GoneException()
                if (e.status != 409 || attempt == ATTEMPTS - 1) throw e
            }
            version = current()
        }
    }

    // --- Receipts -------------------------------------------------------------------------------------------------

    /** Uploads a receipt (a picture or a PDF); answers the name an entry or a request names it by. */
    suspend fun uploadReceipt(fileName: String, mime: String, bytes: ByteArray): String =
        api.upload(ApiV2.BASE + "/receipts", V2Upload.serializer()) {
            addFormDataPart("receipt", fileName, AgoraApi.fileBody(bytes, mime))
        }.name

    // --- Requests -------------------------------------------------------------------------------------------------

    /** The requests the person may see, newest first. */
    suspend fun requests(): List<V2Request> = v2.all("/requests", V2Request.serializer()).items

    /**
     * Asks the treasury: [type] with what is asked ([details]), about the asking member's own record; the treasurers
     * are told (push and e-mail, as they chose).
     */
    suspend fun ask(type: String, details: JsonObject): V2Request =
        v2.call("POST", "/requests", V2Request.serializer(), buildJsonObject {
            put("type", type)
            put("details", details)
            put("notify", true)
        })

    /**
     * Decides request [id] once ([status] approved or rejected): 409 when it was decided already. With [book] an
     * approval books what the request asks for in the same step - once, as the web app books it.
     */
    suspend fun decide(id: String, status: String, rejectionReason: String? = null, book: Boolean = false): V2Request =
        v2.call("POST", "/requests/$id/decision", V2Request.serializer(), buildJsonObject {
            put("status", status)
            rejectionReason?.let { put("rejectionReason", it) }
            if (book) put("book", true)
        })

    // --- The money and the history --------------------------------------------------------------------------------

    suspend fun summary(): V2Summary = v2.call("GET", "/finance/summary", V2Summary.serializer())

    /** The history newest first, a page at a time; with [search] only the entries it finds (the server searches). */
    suspend fun history(search: String, pageSize: Int = HISTORY_PAGE): BookingHistory {
        val members = members()
        val query = search.trim().takeIf { it.isNotEmpty() }?.let { mapOf("q" to it.take(SEARCH_LIMIT)) }.orEmpty()
        return PagedBookingHistory { cursor ->
            v2.page("/finance/ledger", V2LedgerEntry.serializer(), query, cursor, pageSize).let { page ->
                page.items.mapNotNull { it.toTransaction(members) } to page.nextCursor
            }
        }
    }

    /** Every booking, newest first (the financial report). */
    suspend fun allBookings(): List<Transaction> {
        val members = members()
        return v2.all("/finance/ledger", V2LedgerEntry.serializer()).items.mapNotNull { it.toTransaction(members) }
    }

    companion object {
        /** A change refused for a newer version is read again and repeated this often at most. */
        const val ATTEMPTS = 3
        /** Entries of the history shown at a time. */
        const val HISTORY_PAGE = 50
        /** The longest search v2 takes. */
        private const val SEARCH_LIMIT = 200
    }
}

/**
 * The longest texts the books of API v2 take - longer ones are refused, never cut, so the forms take no more: a
 * description or a note [TEXT] (as long as a request's note may be: it is booked as it is), the reason of a rejection
 * [REASON], the name of a donor or of who issued an expense [NAME].
 */
object TextLimits {
    const val TEXT = 5000
    const val REASON = 500
    const val NAME = 200
}

/** The receipts of one entry: API v2 keeps at most [V2_MOST] of them. */
object Receipts {
    const val V2_MOST = 20

    /** Whether [count] receipts fit into one entry (through the old API there is no limit). */
    fun fitV2(count: Int): Boolean = count <= V2_MOST
}

// --- The history: payments, donations and expenses as one list, newest first --------------------------------------

/** The history of the treasury as far as it is read: newest first, more on request. */
interface BookingHistory {
    /** The entries read so far, newest first. */
    val items: List<Transaction>
    /** Whether there are more ([loadMore]). */
    val hasMore: Boolean
    /** Reads the next entries (the first ones on the first call). */
    suspend fun loadMore()
}

/** The history in pages a cursor leads through ([page]: the page after a cursor - null: the first - and the next cursor). */
class PagedBookingHistory(private val page: suspend (cursor: String?) -> Pair<List<Transaction>, String?>) : BookingHistory {
    override var items: List<Transaction> = emptyList()
        private set
    private var cursor: String? = null
    private var started = false
    override val hasMore: Boolean get() = !started || cursor != null

    override suspend fun loadMore() {
        if (!hasMore) return
        val (next, after) = page(cursor)
        items = items + next
        cursor = after
        started = true
    }
}

/** The history through the old API: its pages, searched by the server. */
class OldBookingHistory(private val page: suspend (Int) -> TransactionPage) : BookingHistory {
    override var items: List<Transaction> = emptyList()
        private set
    private var loaded = 0
    private var pages = 1
    override val hasMore: Boolean get() = loaded < pages

    override suspend fun loadMore() {
        if (!hasMore) return
        val next = page(loaded + 1)
        items = items + next.items
        loaded = next.page.coerceAtLeast(loaded + 1)
        pages = next.totalPages
    }
}
