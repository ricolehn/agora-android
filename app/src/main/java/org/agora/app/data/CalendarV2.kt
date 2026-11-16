package org.agora.app.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.agora.app.data.model.AgoraEvent
import org.agora.app.data.model.Attendees
import org.agora.app.data.model.CalendarFeed
import org.agora.app.data.model.Candidate
import org.agora.app.data.model.Candidates
import org.agora.app.data.model.DutyRequest
import org.agora.app.data.model.Group
import org.agora.app.data.model.V2DutyRequest
import org.agora.app.data.model.V2Event
import org.agora.app.data.model.V2Feed
import org.agora.app.data.model.V2Group
import org.agora.app.data.model.V2Image
import org.agora.app.data.model.V2Participant
import org.agora.app.data.model.V2Person
import org.agora.app.data.model.V2Program
import org.agora.app.data.model.toAttendees
import org.agora.app.data.model.toDutyRequest
import org.agora.app.data.model.toEvent
import org.agora.app.data.model.toProgram
import org.agora.app.data.remote.AgoraApi
import org.agora.app.data.remote.ApiV2

// The calendar through API v2 (docs/API_V2.md, "The calendar"): what the old API's /api/events did, in v2's shapes -
// every value checked by the server and refused (400 invalid) instead of cut, every list in pages.

private fun enc(id: String): String = ApiV2.segment(id)

/** What the calendar takes (docs/API_V2.md): the server refuses longer texts, so the forms stop there. */
object CalendarLimits {
    const val TITLE = 200
    const val LOCATION = 300
    const val DESCRIPTION = 10_000
    /** At most 99,999 places (the server takes up to 100,000). */
    const val PLACES_DIGITS = 5
    /** An entry of the plan is also its duty's name (120) and who does it in the order of service (80). */
    const val PLAN_NAME = 80
}

/** Every event the person sees, in the order they take place, with the roster for those who see it. */
suspend fun ApiV2.events(): List<AgoraEvent> = all("/events", V2Event.serializer()).items.map { it.toEvent() }

/** The duties the person was asked to do and has not answered yet, the soonest first. */
suspend fun ApiV2.dutyRequests(): List<DutyRequest> = all("/duty-requests", V2DutyRequest.serializer()).items.map { it.toDutyRequest() }

/** Who comes and who waits, in the order they signed up. */
suspend fun ApiV2.participants(eventId: String): Attendees =
    all("/events/${enc(eventId)}/participants", V2Participant.serializer()).items.toAttendees()

/** The groups, by name. */
suspend fun ApiV2.groups(): List<Group> = all("/groups", V2Group.serializer()).items.map { Group(it.id, it.name) }

/** The people a duty can go to and the groups, as the duty roster offers them. */
suspend fun ApiV2.candidates(): Candidates = Candidates(
    candidates = all("/people", V2Person.serializer()).items.map { Candidate(it.id, it.name, it.email) },
    groups = groups()
)

/** Signs up (a place, or the waiting list when it is full) or cancels. */
suspend fun ApiV2.register(eventId: String, register: Boolean) {
    call(if (register) "PUT" else "DELETE", "/events/${enc(eventId)}/registration", JsonObject.serializer())
}

/** [who]: a member's account id, or a guest as `guest:<guestId>`. */
suspend fun ApiV2.removeParticipant(eventId: String, who: String) {
    call("DELETE", "/events/${enc(eventId)}/participants/${enc(who)}", JsonObject.serializer())
}

suspend fun ApiV2.changeEvent(eventId: String, changes: JsonObject) {
    call("PATCH", "/events/${enc(eventId)}", JsonObject.serializer(), changes)
}

suspend fun ApiV2.answerDuty(dutyId: String, accept: Boolean) {
    call("POST", "/duties/${enc(dutyId)}/answer", JsonObject.serializer(), buildJsonObject { put("answer", if (accept) "accept" else "decline") })
}

/** Takes an open duty (or one asked of oneself) / gives it back. */
suspend fun ApiV2.claimDuty(dutyId: String, claim: Boolean) {
    call(if (claim) "PUT" else "DELETE", "/duties/${enc(dutyId)}/claim", JsonObject.serializer())
}

suspend fun ApiV2.withdrawDutyRequest(dutyId: String) {
    call("DELETE", "/duties/${enc(dutyId)}/request", JsonObject.serializer())
}

/** A duty asked of a person ([userId]: by push and e-mail), given to a group ([groupId]) or open. */
suspend fun ApiV2.addDuty(eventId: String, roleName: String, section: String, userId: String?, groupId: String?, sendEmail: Boolean = true) {
    call("POST", "/events/${enc(eventId)}/duties", JsonObject.serializer(), buildJsonObject {
        put("roleName", roleName)
        if (section.isNotBlank()) put("section", section)
        userId?.let { put("requestedUserId", it) }
        groupId?.let { put("assignedGroupId", it) }
        put("sendEmail", sendEmail)
    })
}

/** Gives a duty to a group or asks a person. */
suspend fun ApiV2.assignDuty(dutyId: String, userId: String?, groupId: String?) {
    call("POST", "/duties/${enc(dutyId)}/assignment", JsonObject.serializer(), buildJsonObject {
        userId?.let { put("userId", it) }
        groupId?.let { put("groupId", it) }
        put("sendEmail", true)
    })
}

suspend fun ApiV2.changeDutyNotes(dutyId: String, notes: String) {
    call("PATCH", "/duties/${enc(dutyId)}", JsonObject.serializer(), buildJsonObject { put("notes", notes) })
}

suspend fun ApiV2.deleteDuty(dutyId: String) {
    call("DELETE", "/duties/${enc(dutyId)}", JsonObject.serializer())
}

/** A picture for an event: its address goes into the event's imageUrl. */
suspend fun ApiV2.uploadEventImage(jpeg: ByteArray): String =
    upload("POST", "/events/images", V2Image.serializer()) {
        addFormDataPart("image", "cover.jpg", AgoraApi.fileBody(jpeg, "image/jpeg"))
    }.url

/**
 * A new event ([existingId] null: with its duties - every date of a series gets them) or a change of one. Appointments,
 * pinned and repeated events are for those who manage events: the form offers them only to them.
 */
suspend fun ApiV2.saveEvent(existingId: String?, input: EventInput) {
    val body = buildJsonObject {
        put("title", input.title.trim())
        put("date", input.date)
        put("endDate", input.endDate)
        put("startTime", input.startTime)
        put("endTime", input.endTime)
        put("location", input.location.trim())
        put("description", input.description.trim())
        if (input.managed) {
            put("kind", input.eventType)
            put("isPinned", input.isPinned)
        }
        put("requiresRegistration", input.requiresRegistration)
        put("minParticipants", input.minParticipants)
        put("maxParticipants", input.maxParticipants)
        putJsonArray("targetGroups") { input.targetGroups.forEach { add(JsonPrimitive(it)) } }
        put("imageUrl", input.imageUrl)
        if (existingId == null) {
            if (input.isRecurring) putJsonObject("recurrence") {
                put("rule", input.recurringRule)
                put("count", input.recurringCount)
            }
            if (input.duties.isNotEmpty()) put("duties", JsonArray(input.duties.map { buildJsonObject { put("roleName", it) } }))
        }
    }
    if (existingId == null) call("POST", "/events", JsonObject.serializer(), body)
    else call("PATCH", "/events/${enc(existingId)}", JsonObject.serializer(), body)
}

suspend fun ApiV2.deleteEvent(eventId: String) {
    call("DELETE", "/events/${enc(eventId)}", JsonObject.serializer())
}

/** The order of service of an event (empty for those who do not see the roster). */
suspend fun ApiV2.program(eventId: String): Program = call("GET", "/events/${enc(eventId)}", V2Event.serializer()).toProgram()

/** Saves the order of service as a whole; [canEdit]: the answer is for one who may edit it. */
suspend fun ApiV2.saveProgram(eventId: String, items: List<ProgramItem>): Program =
    call("PUT", "/events/${enc(eventId)}/program", V2Program.serializer(), buildJsonObject {
        put("items", buildJsonArray {
            items.forEach { item ->
                add(buildJsonObject {
                    if (item.id.isNotBlank()) put("id", item.id)
                    put("title", item.title)
                    item.minutes?.let { put("minutes", it) }
                    put("who", item.who)
                    put("note", item.note)
                    if (item.songId.isNotBlank()) {
                        put("songId", item.songId)
                        put("songKey", item.songKey)
                    }
                })
            }
        })
    }).toProgram(canEdit = true)

/** The address of the person's own calendar for calendar apps; [reset]: a new one (the one before stops working). */
suspend fun ApiV2.calendarFeed(reset: Boolean = false): CalendarFeed =
    call(if (reset) "POST" else "GET", if (reset) "/calendar-feed/reset" else "/calendar-feed", V2Feed.serializer())
        .let { CalendarFeed(feedUrl = it.feedUrl, webcalUrl = it.webcalUrl) }
