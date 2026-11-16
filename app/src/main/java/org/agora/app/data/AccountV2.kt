package org.agora.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.agora.app.data.model.NotificationKinds
import org.agora.app.data.model.NotificationPrefs
import org.agora.app.data.remote.AgoraApi
import org.agora.app.data.remote.ApiV2

// The own account through API v2 (docs/API_V2.md, "Who is signed in"): what is the person's own to choose, the
// registration code, the profile pictures and reports to the admins. Signing in itself stays with the old API.

@Serializable
private data class V2Code(val code: String = "")

@Serializable
private data class V2PictureVersion(val userId: String = "", val version: Long = 0)

@Serializable
private data class V2Picture(val userId: String = "", val version: Long? = null)

/** What a report may hold: the server refuses longer texts instead of cutting them (as the old API did). */
object ReportLimits {
    const val CONTENT = 4000
    const val REASON = 500
}

/** Which messages reach the person, and how: both channels and the kinds of message for each. */
suspend fun ApiV2.saveNotificationSettings(prefs: NotificationPrefs) {
    fun kindsOf(kinds: NotificationKinds) = buildJsonObject {
        put("duties", kinds.duties); put("events", kinds.events); put("messages", kinds.messages)
        put("requests", kinds.requests); put("finances", kinds.finances); put("reports", kinds.reports)
    }
    call("PATCH", "/me", JsonObject.serializer(), buildJsonObject {
        putJsonObject("notificationSettings") {
            putJsonObject("channels") { put("push", prefs.channels.push); put("email", prefs.channels.email) }
            put("push", kindsOf(prefs.push))
            put("email", kindsOf(prefs.email))
        }
    })
}

/** The device's time zone, so duty reminders come in local time. */
suspend fun ApiV2.saveTimeZone(zone: String) {
    call("PATCH", "/me", JsonObject.serializer(), buildJsonObject { put("timeZone", zone) })
}

/** The code people register with (those who manage it). */
suspend fun ApiV2.registrationCode(): String = call("GET", "/registration-code", V2Code.serializer()).code

/** A new registration code, made by the server: the one before stops working. */
suspend fun ApiV2.newRegistrationCode(): String = call("POST", "/registration-code", V2Code.serializer()).code

/** Who has a profile picture, with its version (lists ask only for those). */
suspend fun ApiV2.pictureVersions(): Map<String, Long> =
    all("/pictures", V2PictureVersion.serializer()).items.associate { it.userId to it.version }

/** The address of a profile picture; with [version] the same address always shows the same picture (kept for good). */
fun ApiV2.pictureUrl(uid: String, version: Long?): String =
    url("/pictures/${ApiV2.segment(uid)}", version?.let { mapOf("v" to it.toString()) } ?: emptyMap()).toString()

/** The own picture (a JPEG); returns its new version. */
suspend fun ApiV2.uploadPicture(jpeg: ByteArray): Long? =
    upload("PUT", "/me/picture", V2Picture.serializer()) {
        addFormDataPart("picture", "profile.jpg", AgoraApi.fileBody(jpeg, "image/jpeg"))
    }.version

/** Reports an AI reply ("ai", with the [prompt]) or a message of a conversation ("chat", with its [threadId]). */
suspend fun ApiV2.report(type: String, content: String, reason: String, prompt: String, threadId: String) {
    call("POST", "/reports", JsonObject.serializer(), buildJsonObject {
        put("type", type)
        put("content", content.take(ReportLimits.CONTENT))
        put("reason", reason.take(ReportLimits.REASON))
        if (type == "ai" && prompt.isNotBlank()) put("prompt", prompt.take(ReportLimits.CONTENT))
        if (type == "chat") put("threadId", threadId)
    })
}
