package org.agora.app.data.model

import kotlinx.serialization.Serializable
import org.agora.app.data.Program
import org.agora.app.data.ProgramItem

// The calendar of API v2 (docs/API_V2.md, "The calendar"): events with their duty roster, sign-ups, the order of
// service, the duties asked of the person and the people a duty can go to. Read into the app's own models, so the
// screens show the same whichever API the server answers.

/** A duty of the roster, with the names of whom it names ('' when the account is gone). */
@Serializable
data class V2Duty(
    val id: String = "",
    val eventId: String? = null,
    val section: String = "",
    val roleName: String = "",
    val assignedGroupId: String? = null,
    val assignedUserId: String? = null,
    val requestedUserId: String? = null,
    val requestedById: String? = null,
    /** open, requested, declined, assigned, confirmed - one the app does not know is shown as it comes */
    val status: String = "open",
    val notes: String = "",
    val assignedGroupName: String = "",
    val assignedUserName: String = "",
    val requestedUserName: String = "",
    val requestedByName: String = ""
)

/** The order of service of an event: its items and when it was last changed. */
@Serializable
data class V2Program(val items: List<ProgramItem> = emptyList(), val updatedAt: String? = null)

/**
 * An event as the person sees it: [duties] is the roster for those who see it (null for everyone else), [program] the
 * order of service (only when one event is read).
 */
@Serializable
data class V2Event(
    val id: String = "",
    val title: String = "",
    val date: String = "",
    val endDate: String? = null,
    val startTime: String? = null,
    val endTime: String? = null,
    val location: String = "",
    val description: String = "",
    /** event or termin (an appointment) - one the app does not know counts as an event */
    val kind: String = "event",
    val status: String = "scheduled",
    val isPinned: Boolean = false,
    val requiresRegistration: Boolean = false,
    val minParticipants: Int = 0,
    val maxParticipants: Int = 0,
    val targetGroups: List<String> = emptyList(),
    val createdBy: String? = null,
    val createdByName: String = "",
    val imageUrl: String? = null,
    val isPublic: Boolean = false,
    val shareToken: String? = null,
    val registeredCount: Int = 0,
    val waitlistCount: Int = 0,
    val isFull: Boolean = false,
    val myRegistration: Registration? = null,
    val canEdit: Boolean = false,
    val duties: List<V2Duty>? = null,
    val program: V2Program? = null
)

/** A duty the person was asked to do and has not answered yet. */
@Serializable
data class V2DutyRequest(
    val dutyId: String = "",
    val eventId: String = "",
    val eventTitle: String = "",
    val eventDate: String? = null,
    val eventStartTime: String? = null,
    val eventEndTime: String? = null,
    val eventLocation: String = "",
    val section: String = "",
    val roleName: String = "",
    val requestedById: String? = null,
    val requestedByName: String = "",
    val notes: String = ""
)

/** Who comes or waits: a member ([userId]) or a guest without an account ([guestId]). */
@Serializable
data class V2Participant(
    val id: String = "",
    val userId: String? = null,
    val guestId: String? = null,
    val isGuest: Boolean = false,
    val name: String = "",
    /** registered or waitlist */
    val status: String = "",
    val email: String? = null,
    val age: Int? = null
)

/** One the duty roster can name, by name (the e-mail address only for those who organise events). */
@Serializable
data class V2Person(val id: String = "", val name: String = "", val email: String = "")

@Serializable
data class V2Group(val id: String = "", val name: String = "")

@Serializable
data class V2Image(val name: String = "", val url: String = "")

@Serializable
data class V2Feed(val feedUrl: String = "", val webcalUrl: String = "")

/** [canEdit]: whoever may edit the event manages its duties and their notes - as the old API said of every duty. */
fun V2Duty.toDuty(canEdit: Boolean): Duty = Duty(
    id = id,
    event = eventId.orEmpty(),
    section = section,
    roleName = roleName,
    assignedGroup = assignedGroupId.orEmpty(),
    assignedGroupName = assignedGroupName,
    assignedUser = assignedUserId.orEmpty(),
    assignedUserName = assignedUserName,
    requestedUser = requestedUserId.orEmpty(),
    requestedUserName = requestedUserName,
    requestedBy = requestedById.orEmpty(),
    requestedByName = requestedByName,
    notes = notes,
    status = status,
    canEditNotes = canEdit,
    canManageDuty = canEdit
)

fun V2Event.toEvent(): AgoraEvent = AgoraEvent(
    id = id,
    title = title,
    date = date,
    endDate = endDate.orEmpty(),
    startTime = startTime.orEmpty(),
    endTime = endTime.orEmpty(),
    location = location,
    description = description,
    eventType = kind,
    isPinned = isPinned,
    status = status,
    requiresRegistration = requiresRegistration,
    minParticipants = minParticipants,
    maxParticipants = maxParticipants,
    targetGroups = targetGroups,
    createdBy = createdBy.orEmpty(),
    createdByName = createdByName,
    imageUrl = imageUrl.orEmpty(),
    duties = duties.orEmpty().map { it.toDuty(canEdit) },
    registeredCount = registeredCount,
    waitlistCount = waitlistCount,
    myRegistration = myRegistration,
    isFull = isFull,
    canEdit = canEdit,
    canAccessDutyPlan = duties != null,
    isPublic = isPublic,
    publicToken = shareToken.orEmpty()
)

/** The order of service of this event as the plan shows it (empty: none yet, or the person does not see the roster). */
fun V2Event.toProgram(): Program = Program(program?.items.orEmpty(), program?.updatedAt?.let(::millisOf)?.takeIf { it > 0 }, canEdit)

fun V2Program.toProgram(canEdit: Boolean): Program = Program(items, updatedAt?.let(::millisOf)?.takeIf { it > 0 }, canEdit)

fun V2DutyRequest.toDutyRequest(): DutyRequest = DutyRequest(
    id = dutyId,
    eventId = eventId,
    eventTitle = eventTitle,
    eventDate = eventDate.orEmpty(),
    eventStartTime = eventStartTime.orEmpty(),
    eventEndTime = eventEndTime.orEmpty(),
    eventLocation = eventLocation,
    section = section,
    roleName = roleName,
    requestedBy = requestedById.orEmpty(),
    requestedByName = requestedByName,
    notes = notes
)

/** A participant as the old API named them: a guest by `guest:<guestId>` - the name a removal takes, too. */
fun V2Participant.toAttendee(): Attendee = Attendee(
    id = id,
    userId = if (isGuest) "guest:${guestId.orEmpty()}" else userId.orEmpty(),
    name = name,
    status = status,
    guest = isGuest,
    email = email.orEmpty(),
    age = age
)

/** The participants in the order they signed up: who comes, who waits. */
fun List<V2Participant>.toAttendees(): Attendees = Attendees(
    registered = filter { it.status == "registered" }.map { it.toAttendee() },
    waitlist = filter { it.status == "waitlist" }.map { it.toAttendee() }
)
