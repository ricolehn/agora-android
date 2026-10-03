package org.agora.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

// ---------- Auth & user ----------

@Serializable
data class StatusResponse(val setupMode: Boolean = false)

@Serializable
data class AuthResponse(val token: String? = null, val user: User? = null)

@Serializable
data class NotificationSettings(
    val duties: Boolean = true,
    val events: Boolean = true,
    val messages: Boolean = true,
    val finances: Boolean = true
)

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
    val effectiveNotifications: NotificationSettings
        get() = notificationSettings ?: if (emailNotifications) NotificationSettings()
        else NotificationSettings(duties = false, events = false, messages = false, finances = false)
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
    @SerialName("_currentBalance") @Serializable(FlexDoubleSerializer::class) val currentBalance: Double = 0.0
) {
    val effectiveStatus: String get() = currentStatus ?: status
}

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
    val timestamp: Long = 0
) {
    fun field(key: String): String? = (data[key] as? JsonPrimitive)?.contentOrNull
}

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
    val isAuto: Boolean = false
) {
    val isIncome: Boolean get() = type != "exp"
}

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
    val canAccessDutyPlan: Boolean = false
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
data class Attendee(val id: String = "", val userId: String = "", val name: String = "", val status: String = "")

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
