package org.agora.app.data

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import org.agora.app.data.model.ChatMessage
import org.agora.app.data.model.Mentor
import org.agora.app.data.model.MentoringThread
import org.agora.app.data.model.MyMentorProfile
import org.agora.app.data.model.V2Mentor
import org.agora.app.data.model.V2MentorProfileAnswer
import org.agora.app.data.model.V2Message
import org.agora.app.data.model.V2Prayer
import org.agora.app.data.model.V2Song
import org.agora.app.data.model.V2Thread
import org.agora.app.data.model.toChatMessage
import org.agora.app.data.model.toMentor
import org.agora.app.data.model.toMyProfile
import org.agora.app.data.model.toPrayer
import org.agora.app.data.model.toSong
import org.agora.app.data.model.toThread
import org.agora.app.data.remote.ApiV2

// The songbook, prayer requests and mentoring through API v2 (docs/API_V2.md, "Community"). The server checks every
// value: texts longer than it takes are refused (400 invalid) instead of cut, as the old API did.

private fun enc(id: String): String = ApiV2.segment(id)

/** What the signed-in person may do, as v2 says it (the server checks every call itself). */
@Serializable
private data class V2Can(val manageSongbook: Boolean = false)

@Serializable
private data class V2Me(val can: V2Can = V2Can())

/** What mentoring takes (docs/API_V2.md): longer is refused, so the forms stop there. */
object MentoringLimits {
    const val BIO = 2000
    const val MESSAGE = 10_000
    const val MOST_MENTEES = 50
}

/** What the songbook takes (docs/API_V2.md): longer is refused. */
object SongLimits {
    const val TITLE = 120
    const val ARTIST = 120
    const val COPYRIGHT = 300
    const val KEY = 4
    const val CCLI = 12
    const val CONTENT = 20_000
}

// --- The songbook ---------------------------------------------------------------------------------------------------

/** Every song by title, with the church's CCLI licence number, and whether the person manages the songbook. */
suspend fun ApiV2.songbook(): Songbook = coroutineScope {
    val me = async { call("GET", "/me", V2Me.serializer()) }
    val songs = collect("/songs", V2Song.serializer())
    Songbook(
        songs = songs.items.map { it.toSong() },
        ccliLicense = (songs.first["ccliLicense"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
        canManage = me.await().can.manageSongbook
    )
}

/** A new song ([id] null) or a change of one; answers the song as it is kept. */
suspend fun ApiV2.saveSong(id: String?, input: SongInput): Song {
    val body = buildJsonObject {
        put("title", input.title)
        put("artist", input.artist)
        put("key", input.key.trim())
        put("ccli", input.ccli.trim())
        put("copyright", input.copyright)
        put("content", input.content)
        put("capo", input.capo?.let(::JsonPrimitive) ?: JsonNull)
        put("tempo", input.tempo?.let(::JsonPrimitive) ?: JsonNull)
    }
    val song = if (id == null) call("POST", "/songs", V2Song.serializer(), body) else call("PATCH", "/songs/${enc(id)}", V2Song.serializer(), body)
    return song.toSong()
}

suspend fun ApiV2.deleteSong(id: String) {
    call("DELETE", "/songs/${enc(id)}", JsonObject.serializer())
}

// --- Prayer requests ------------------------------------------------------------------------------------------------

/** The running requests, newest first, and the person's own that ran out; whether they moderate the requests. */
suspend fun ApiV2.prayers(): PrayerList {
    val list = collect("/prayers", V2Prayer.serializer())
    return PrayerList(list.items.map { it.toPrayer() }, (list.first["canModerate"] as? JsonPrimitive)?.booleanOrNull == true)
}

suspend fun ApiV2.sharePrayer(text: String, duration: PrayerDuration, anonymous: Boolean): Prayer =
    call("POST", "/prayers", V2Prayer.serializer(), buildJsonObject {
        put("text", text)
        put("duration", duration.key)
        put("anonymous", anonymous)
    }).toPrayer()

suspend fun ApiV2.extendPrayer(id: String, duration: PrayerDuration): Prayer =
    call("PATCH", "/prayers/${enc(id)}", V2Prayer.serializer(), buildJsonObject { put("extend", duration.key) }).toPrayer()

suspend fun ApiV2.deletePrayer(id: String) {
    call("DELETE", "/prayers/${enc(id)}", JsonObject.serializer())
}

// --- Mentoring ------------------------------------------------------------------------------------------------------

/** The person's conversations, the latest first. */
suspend fun ApiV2.threads(): List<MentoringThread> = all("/mentoring/threads", V2Thread.serializer()).items.map { it.toThread() }

/**
 * The messages of a conversation, the oldest first - and what the other one wrote is read, as when the old API handed
 * them out (the conversation's answer says which part the reader has in it).
 */
suspend fun ApiV2.messages(threadId: String): List<ChatMessage> {
    val thread = call("POST", "/mentoring/threads/${enc(threadId)}/read", V2Thread.serializer())
    return all("/mentoring/threads/${enc(threadId)}/messages", V2Message.serializer()).items
        .map { it.toChatMessage(threadId, thread.role) }
        .reversed()
}

suspend fun ApiV2.sendMessage(threadId: String, text: String) {
    call("POST", "/mentoring/threads/${enc(threadId)}/messages", JsonObject.serializer(), buildJsonObject { put("text", text) })
}

/** active, closed or blocked (only who blocked a conversation opens it again). */
suspend fun ApiV2.setThreadStatus(threadId: String, status: String) {
    call("PATCH", "/mentoring/threads/${enc(threadId)}", JsonObject.serializer(), buildJsonObject { put("status", status) })
}

/** The approved mentors by name - or, for those who manage mentoring, those of [status] (pending, rejected, all). */
suspend fun ApiV2.mentors(status: String?): List<Mentor> =
    all("/mentoring/mentors", V2Mentor.serializer(), status?.let { mapOf("status" to it) } ?: emptyMap()).items.map { it.toMentor() }

suspend fun ApiV2.myMentorProfile(): MyMentorProfile = call("GET", "/mentoring/profile", V2MentorProfileAnswer.serializer()).toMyProfile()

/** Asks a mentor ([mentorUserId]: their account), with a first message; answers the new conversation's id. */
suspend fun ApiV2.contactMentor(mentorUserId: String, message: String): String =
    call("POST", "/mentoring/threads", V2Thread.serializer(), buildJsonObject {
        put("mentorUserId", mentorUserId)
        put("message", message)
    }).id

/** Applies as a mentor ([isNew]) or changes the own profile. */
suspend fun ApiV2.saveMentorProfile(isNew: Boolean, bio: String, maxMentees: Int, isAccepting: Boolean) {
    val body = buildJsonObject {
        put("bio", bio.trim())
        put("maxMentees", maxMentees)
        if (!isNew) put("isAccepting", isAccepting)
    }
    if (isNew) call("POST", "/mentoring/application", JsonObject.serializer(), body)
    else call("PATCH", "/mentoring/profile", JsonObject.serializer(), body)
}

/** approved, rejected or pending - those who manage mentoring. */
suspend fun ApiV2.setMentorStatus(mentorRecordId: String, status: String) {
    call("POST", "/mentoring/mentors/${enc(mentorRecordId)}/status", JsonObject.serializer(), buildJsonObject { put("status", status) })
}
