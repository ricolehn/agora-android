package org.agora.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

// ---------- Auth & user ----------

/**
 * `GET /api/status`. [ready]: the server answers everything; until then (null on servers before CALDERA, which say
 * nothing) a configured instance is starting or upgrading its database ([updating]) and asks the apps to try again in
 * a moment. [apiVersions]: the versions of the API it answers - `[1, 2]` from CALDERA on (API v2), none before.
 */
@Serializable
data class StatusResponse(
    val setupMode: Boolean = false,
    val ready: Boolean? = null,
    val updating: Boolean = false,
    val apiVersions: List<Int> = emptyList()
) {
    val answersV2: Boolean get() = 2 in apiVersions
}

@Serializable
data class AuthResponse(val token: String? = null, val user: User? = null)

@Serializable
data class NotificationSettings(
    // Flat keys: the push choice in the format of older servers / app versions
    val duties: Boolean = true,
    val events: Boolean = true,
    val messages: Boolean = true,
    val finances: Boolean = true,
    val requests: Boolean = true,
    val reports: Boolean = true,
    // Since server v3.0.0: master switches per channel and the kinds per channel
    val channels: NotificationChannels? = null,
    val push: NotificationKinds? = null,
    val email: NotificationKinds? = null
)

@Serializable
data class NotificationChannels(val push: Boolean = true, val email: Boolean = false)

/** Which kinds of messages arrive over one channel. */
@Serializable
data class NotificationKinds(
    val duties: Boolean = true,
    val events: Boolean = true,
    val messages: Boolean = true,
    val requests: Boolean = true,
    val finances: Boolean = true,
    val reports: Boolean = true
) {
    operator fun get(kind: String): Boolean = when (kind) {
        "duties" -> duties
        "events" -> events
        "messages" -> messages
        "requests" -> requests
        "finances" -> finances
        else -> reports
    }

    fun with(kind: String, on: Boolean): NotificationKinds = when (kind) {
        "duties" -> copy(duties = on)
        "events" -> copy(events = on)
        "messages" -> copy(messages = on)
        "requests" -> copy(requests = on)
        "finances" -> copy(finances = on)
        else -> copy(reports = on)
    }

    companion object {
        val NONE = NotificationKinds(false, false, false, false, false, false)
    }
}

/** A user's notification choice with all defaults applied (push with everything, e-mail off). */
data class NotificationPrefs(val channels: NotificationChannels, val push: NotificationKinds, val email: NotificationKinds)

@Serializable
data class User(
    val uid: String = "",
    val id: String = "",
    val email: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val name: String = "",
    val admin: Boolean = false,
    val owner: Boolean = false,
    val superAdmin: Boolean = false,
    val pays: Boolean = true,
    val groups: List<String> = emptyList(),
    val permissions: List<String> = emptyList(),
    val emailNotifications: Boolean = true,
    val notificationSettings: NotificationSettings? = null,
    val isClaimed: Boolean = true,
    val calendarToken: String = "",
    /** Time zone the app last reported (duty reminders are timed in it). */
    val timeZone: String = "",
    val canManageFinances: Boolean = false,
    val canViewFinances: Boolean = false,
    val canManageRegistrationCode: Boolean = false,
    val canAccessAi: Boolean = false,
    val canParticipateMentoring: Boolean = true,
    val canManageMentoring: Boolean = false,
    val canManageEvents: Boolean = false,
    val mentorStatus: String? = null,
    val isApprovedMentor: Boolean = false
) {
    val userId: String get() = uid.ifBlank { id }
    val fullName: String get() = name.ifBlank { "$firstName $lastName".trim() }.ifBlank { email }
    val isAdmin: Boolean get() = admin || owner || superAdmin
    val managesEvents: Boolean get() = canManageEvents || "manage_events" in permissions
    val managesMentoring: Boolean get() = canManageMentoring || "manage_mentoring" in permissions
    val accessesAi: Boolean get() = canAccessAi || "access_ai" in permissions
    /** Channels and kinds like the server reads them (older records: flat keys = push, no record + emailNotifications off = nothing). */
    val notificationPrefs: NotificationPrefs
        get() {
            val stored = notificationSettings
            val legacyAllOff = stored == null && !emailNotifications
            val flat = stored?.let { NotificationKinds(it.duties, it.events, it.messages, it.requests, it.finances, it.reports) } ?: NotificationKinds()
            return NotificationPrefs(
                channels = stored?.channels ?: NotificationChannels(push = !legacyAllOff, email = false),
                push = stored?.push ?: if (legacyAllOff) NotificationKinds.NONE else flat,
                email = stored?.email ?: NotificationKinds()
            )
        }

    /** What may show up as notification on this device (the push channel). */
    val effectiveNotifications: NotificationKinds
        get() = notificationPrefs.let { if (it.channels.push) it.push else NotificationKinds.NONE }
}

@Serializable
data class Group(val id: String = "", val name: String = "")

// ---------- Finances ----------

/** Monthly fee per member status (EUR). */
@Serializable
data class FeeSettings(val rates: Map<String, Double> = emptyMap()) {
    fun rateFor(status: String?): Double = rates[status ?: ""] ?: 0.0

    companion object {
        fun from(json: JsonObject?): FeeSettings = FeeSettings(
            json?.entries
                ?.mapNotNull { (key, value) -> (value as? JsonPrimitive)?.contentOrNull?.let(::parseAmount)?.let { key to it } }
                ?.toMap()
                .orEmpty()
        )
    }
}

@Serializable
data class Payment(
    @Serializable(FlexStringSerializer::class) val id: String = "",
    @Serializable(FlexDoubleSerializer::class) val amount: Double = 0.0,
    val date: String = "",
    val description: String = "",
    val isAuto: Boolean = false
)

@Serializable
data class StandingOrder(
    @Serializable(FlexStringSerializer::class) val id: String = "",
    @Serializable(FlexDoubleSerializer::class) val amount: Double = 0.0,
    val startDate: String = "",
    val endDate: String? = null,
    val note: String = "",
    val lastAutoPayment: String? = null
)

@Serializable
data class StatusHistoryEntry(
    val status: String = "",
    val startDate: String = "",
    val endDate: String? = null
)

@Serializable
data class StatusMeta(
    val text: String = "",
    val isOverdue: Boolean = false,
    val isSoonDue: Boolean = false,
    val isActiveStandingOrder: Boolean = false
)

@Serializable
data class Person(
    @Serializable(FlexStringSerializer::class) val id: String = "",
    val uid: String = "",
    val name: String = "",
    val status: String = "",
    val memberSince: String = "",
    val originalMemberSince: String = "",
    val pays: Boolean = true,
    @Serializable(FlexDoubleSerializer::class) val totalPaid: Double = 0.0,
    val payments: List<Payment> = emptyList(),
    val standingOrders: List<StandingOrder> = emptyList(),
    val statusHistory: List<StatusHistoryEntry> = emptyList(),
    val isDeleted: Boolean = false,
    @SerialName("_paidUntil") val paidUntil: String? = null,
    @SerialName("_statusMeta") val statusMeta: StatusMeta = StatusMeta(),
    @SerialName("_overdueAmount") @Serializable(FlexDoubleSerializer::class) val overdueAmount: Double = 0.0,
    @SerialName("_currentStatus") val currentStatus: String? = null,
    @SerialName("_currentBalance") @Serializable(FlexDoubleSerializer::class) val currentBalance: Double = 0.0,
    // CALDERA: the fresh start the fee account counts from ('YYYY-MM-DD', else null) and what happened on that day -
    // the credit taken along or the open fees dropped; "paid until" and the open amount look at the time since then
    @SerialName("_feeReset") val feeReset: String? = null,
    @SerialName("_feeCarriedOver") @Serializable(FlexDoubleSerializer::class) val feeCarriedOver: Double = 0.0,
    @SerialName("_feeWrittenOff") @Serializable(FlexDoubleSerializer::class) val feeWrittenOff: Double = 0.0,
    /** What this month costs at the member's status (CALDERA); null on older servers: the fee settings say it. */
    @SerialName("_monthlyFee") val monthlyFee: Double? = null,
    /**
     * Read through API v2: the member's record id and the version of its books, which a change of them names (its
     * standing orders then carry their record ids). Null: read through the old API ([id] is the key either way).
     */
    val memberId: String? = null,
    val version: String? = null
) {
    val effectiveStatus: String get() = currentStatus ?: status

    /** The fee of this month: the server's, else the one of the fee settings (servers before CALDERA). */
    fun monthlyFee(fees: FeeSettings): Double = monthlyFee ?: fees.rateFor(effectiveStatus)
}

/**
 * A request of a member to the treasury. Read through the old API, [id] is the request's key and [personId] the key of
 * the member's record. Read through API v2 ([v2]), [id] is the request's record id - the one its decision and the
 * booking for it name - [memberId] the member's record id and [personId] still the member's key (the app's members
 * are the old API's records); [amountCents] is the amount as the server reads it.
 */
@Serializable
data class FinanceRequest(
    @Serializable(FlexStringSerializer::class) val id: String = "",
    val type: String = "",
    val userId: String = "",
    @Serializable(FlexStringSerializer::class) val personId: String = "",
    val personName: String = "",
    val data: JsonObject = JsonObject(emptyMap()),
    val status: String = "pending",
    val rejectionReason: String? = null,
    val timestamp: Long = 0,
    val v2: Boolean = false,
    val memberId: String? = null,
    val amountCents: Long? = null
) {
    fun field(key: String): String? = (data[key] as? JsonPrimitive)?.contentOrNull

    /** The amount asked for: as the server read it (API v2), else as written in the request; null when unreadable. */
    val amount: Double? get() = amountCents?.let { it / 100.0 } ?: field("amount")?.let(::parseAmount)
}

/**
 * A booking of the history: a member's payment ("pay"), a donation ("don") or an expense ("exp"). [personId]: the key
 * of the member's record (payments). Read through API v2 the entry carries what a change of it names ([booking]).
 */
@Serializable
data class Transaction(
    @Serializable(FlexStringSerializer::class) val id: String = "",
    val type: String = "",
    val who: String = "",
    val date: String = "",
    @Serializable(FlexDoubleSerializer::class) val amount: Double = 0.0,
    val description: String = "",
    val personUid: String? = null,
    @Serializable(FlexStringSerializer::class) val personId: String = "",
    val receipt: String? = null,
    val isAuto: Boolean = false,
    /** A payment's place in the member's list: finds old payments without an id again when one is edited. */
    @Serializable(FlexIntSerializer::class) val paymentIndex: Int = -1,
    /** The entry as it is stored (amounts and dates as written), to edit or delete exactly this one. */
    val payment: JsonElement? = null,
    /** Read through API v2: the record and the version a change of it is based on; null through the old API. */
    val booking: V2Booking? = null
) {
    val isIncome: Boolean get() = type != "exp"
}

/**
 * What API v2 needs to change or delete a booking: its [recordId], the [version] it was read with (a payment: its
 * member's version; a donation or an expense: the version of the list), the member of a payment ([memberId]), the
 * amount in whole cents (null: one no whole number of cents can hold) and what the entry held when it was read
 * ([fingerprint]: a change is only repeated on a newer version while the entry itself is still the same).
 */
@Serializable
data class V2Booking(
    val recordId: String,
    val version: String,
    val memberId: String? = null,
    val amountCents: Long? = null,
    val fingerprint: String = ""
)

@Serializable
data class TransactionPage(
    val items: List<Transaction> = emptyList(),
    val totalItems: Int = 0,
    val page: Int = 1,
    val perPage: Int = 150,
    val totalPages: Int = 1
)

@Serializable
data class FinanceStats(
    @Serializable(FlexDoubleSerializer::class) val totalBalance: Double = 0.0,
    @Serializable(FlexDoubleSerializer::class) val totalIncome: Double = 0.0,
    @Serializable(FlexDoubleSerializer::class) val totalExpenses: Double = 0.0
)

@Serializable
data class UploadResponse(val filename: String = "", val url: String = "")

// ---------- Events ----------

@Serializable
data class Registration(val id: String = "", val status: String = "")

@Serializable
data class Duty(
    val id: String = "",
    val event: String = "",
    val section: String = "",
    val roleName: String = "",
    val assignedGroup: String = "",
    val assignedGroupName: String = "",
    val assignedUser: String = "",
    val assignedUserName: String = "",
    val requestedUser: String = "",
    val requestedUserName: String = "",
    val requestedBy: String = "",
    val requestedByName: String = "",
    val notes: String = "",
    val status: String = "open",
    val canEditNotes: Boolean = false,
    val canManageDuty: Boolean = false
)

@Serializable
data class AgoraEvent(
    val id: String = "",
    val title: String = "",
    val date: String = "",
    val endDate: String = "",
    val startTime: String = "",
    val endTime: String = "",
    val location: String = "",
    val description: String = "",
    val eventType: String = "event",
    val isPinned: Boolean = false,
    val isRecurring: Boolean = false,
    val recurringRule: String = "",
    val status: String = "",
    val requiresRegistration: Boolean = false,
    @Serializable(FlexIntSerializer::class) val minParticipants: Int = 0,
    @Serializable(FlexIntSerializer::class) val maxParticipants: Int = 0,
    val targetGroups: List<String> = emptyList(),
    val createdBy: String = "",
    val createdByName: String = "",
    val imageUrl: String = "",
    val duties: List<Duty> = emptyList(),
    @Serializable(FlexIntSerializer::class) val registeredCount: Int = 0,
    @Serializable(FlexIntSerializer::class) val waitlistCount: Int = 0,
    val myRegistration: Registration? = null,
    val isFull: Boolean = false,
    val canEdit: Boolean = false,
    val canAccessDutyPlan: Boolean = false,
    // Share switch (server v3.2+, null on older ones): people without an account open the link with publicToken
    val isPublic: Boolean? = null,
    val publicToken: String = ""
) {
    val isTermin: Boolean get() = eventType == "termin"
    val lastDay: String get() = endDate.ifBlank { date }
    val isMultiDay: Boolean get() = endDate.isNotBlank() && endDate != date
    val isRegistered: Boolean get() = myRegistration?.status == "registered"
    val isWaitlisted: Boolean get() = myRegistration?.status == "waitlist"
}

@Serializable
data class DutyRequest(
    val id: String = "",
    val eventId: String = "",
    val eventTitle: String = "",
    val eventDate: String = "",
    val eventStartTime: String = "",
    val eventEndTime: String = "",
    val eventLocation: String = "",
    val section: String = "",
    val roleName: String = "",
    val requestedBy: String = "",
    val requestedByName: String = "",
    val notes: String = ""
)

@Serializable
data class Attendee(
    val id: String = "",
    val userId: String = "",
    val name: String = "",
    val status: String = "",
    // Signed up without an account through the shared link (server v3.2+); e-mail and age only reach the editors
    val guest: Boolean = false,
    val email: String = "",
    val age: Int? = null
)

@Serializable
data class Attendees(
    val registered: List<Attendee> = emptyList(),
    val waitlist: List<Attendee> = emptyList(),
    @Serializable(FlexIntSerializer::class) val maxParticipants: Int = 0
)

@Serializable
data class Candidate(val id: String = "", val name: String = "", val email: String = "")

@Serializable
data class Candidates(val candidates: List<Candidate> = emptyList(), val groups: List<Group> = emptyList())

@Serializable
data class EventSettings(val allowMemberCreation: Boolean = true, val defaultDuties: List<String> = emptyList())

@Serializable
data class CalendarFeed(val feedUrl: String = "", val webcalUrl: String = "", val calendarToken: String = "")

// ---------- Mentoring ----------

@Serializable
data class Mentor(
    val id: String = "",
    val user: String = "",
    val name: String = "",
    val mentorName: String = "",
    val status: String = "",
    val bio: String = "",
    @Serializable(FlexIntSerializer::class) val maxMentees: Int = 0,
    @Serializable(FlexIntSerializer::class) val activeMentees: Int = 0,
    val isFull: Boolean = false,
    val isAccepting: Boolean = true,
    val userEmail: String? = null
) {
    val displayName: String get() = mentorName.ifBlank { name }
}

@Serializable
data class MentorProfile(
    val id: String = "",
    val status: String = "",
    val bio: String = "",
    @Serializable(FlexIntSerializer::class) val maxMentees: Int = 3,
    val isAccepting: Boolean = true
)

@Serializable
data class MyMentorProfile(val exists: Boolean = false, val mentor: MentorProfile? = null)

@Serializable
data class LastMessage(val text: String = "", val created: String = "", val senderRole: String = "")

@Serializable
data class MentoringThread(
    val id: String = "",
    val mentor: String = "",
    val mentee: String? = null,
    val status: String = "active",
    val created: String = "",
    val updated: String = "",
    @Serializable(FlexIntSerializer::class) val unreadCount: Int = 0,
    val lastMessage: LastMessage? = null,
    val myRole: String = "mentee",
    val mentorName: String = "",
    val menteeAlias: String = "",
    val title: String = "",
    /** Closed by a block; only the one who blocked ([blockedByMe]) can reopen it. */
    val blocked: Boolean = false,
    val blockedByMe: Boolean = false
) {
    val isClosed: Boolean get() = status == "closed"
    val partnerName: String get() = title.ifBlank { if (myRole == "mentor") menteeAlias else mentorName }
}

@Serializable
data class ChatMessage(
    val id: String = "",
    val thread: String = "",
    val senderRole: String = "",
    val sender: String = "",
    val text: String = "",
    val read: Boolean = false,
    val created: String = ""
) {
    val isMine: Boolean get() = sender != "partner"
}

@Serializable
data class CreateThreadResponse(val threadId: String = "")

// ---------- Push ----------

/** Public Firebase client config of the server's own project (from its google-services.json). */
@Serializable
data class FcmClientConfig(
    val projectId: String,
    val appId: String,
    val apiKey: String,
    val senderId: String,
    val storageBucket: String? = null
)

@Serializable
data class FcmStatus(val enabled: Boolean = false, val config: FcmClientConfig? = null)

// ---------- AI ----------

@Serializable
data class AiStatus(val enabled: Boolean = false)

@Serializable
data class AiMessage(val role: String, val content: String)
