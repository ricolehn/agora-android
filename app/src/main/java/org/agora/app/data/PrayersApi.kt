package org.agora.app.data

import android.net.Uri
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// Prayer requests (Extras) - server: backend/prayers.js + routes/prayers.js of the web app (v3.1).
// The server never says who shared an anonymous request; [Prayer.isMine] and [Prayer.canDelete] come from it.

@Serializable
data class PrayerAuthor(val uid: String? = null, val name: String? = null)

@Serializable
data class Prayer(
    val id: String = "",
    val text: String = "",
    val anonymous: Boolean = false,
    val author: PrayerAuthor? = null,
    val createdAt: Long = 0,
    val expiresAt: Long = 0,
    val expired: Boolean = false,
    val isMine: Boolean = false,
    val canDelete: Boolean = false
)

@Serializable
data class PrayerList(val prayers: List<Prayer> = emptyList(), val canModerate: Boolean = false)

/** How long a request is shown: the server's DURATIONS keys. */
enum class PrayerDuration(val key: String) { WEEK("week"), MONTH("month"), QUARTER("quarter") }

/** The running requests (newest first) plus the own expired ones. */
suspend fun AgoraRepository.prayers(): PrayerList = api.get("/api/prayers")

suspend fun AgoraRepository.sharePrayer(text: String, duration: PrayerDuration, anonymous: Boolean): Prayer =
    api.post("/api/prayers", buildJsonObject {
        put("text", text)
        put("duration", duration.key)
        put("anonymous", anonymous)
    })

/** The owner adds a month (from the later of now and the old end). */
suspend fun AgoraRepository.extendPrayer(id: String, duration: PrayerDuration = PrayerDuration.MONTH): Prayer =
    api.patch("/api/prayers/${Uri.encode(id)}", buildJsonObject { put("extend", duration.key) })

/** Done (owner) or removed by a member with the manage_prayers permission. */
suspend fun AgoraRepository.deletePrayer(id: String) {
    api.delete<JsonObject>("/api/prayers/${Uri.encode(id)}")
}
