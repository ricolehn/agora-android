package org.agora.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Order of service ("Ablauf") of an event (web v3.1, backend/program.js): items in their order, each with an
 * optional duration. Seen by those who see the duty roster, edited by those who may edit the event.
 */
@Serializable
data class ProgramItem(
    val id: String = "",
    val title: String = "",
    val minutes: Int? = null,
    val who: String = "",
    val note: String = "",
    val songId: String = "",
    val songKey: String = ""
)

@Serializable
data class Program(val items: List<ProgramItem> = emptyList(), val updatedAt: Long? = null, val canEdit: Boolean = false)

/** The few song fields the plan needs (song picker, key chip); the songbook has its own full model. */
@Serializable
data class PlanSong(val id: String = "", val title: String = "", val artist: String = "", val key: String = "")

@Serializable
data class PlanSongs(val songs: List<PlanSong> = emptyList())

suspend fun AgoraRepository.program(eventId: String): Program = api.get("/api/events/$eventId/program")

suspend fun AgoraRepository.saveProgram(eventId: String, items: List<ProgramItem>): Program =
    api.put("/api/events/$eventId/program", buildJsonObject {
        put("items", buildJsonArray {
            items.forEach { item ->
                add(buildJsonObject {
                    if (item.id.isNotBlank()) put("id", item.id)
                    put("title", item.title)
                    item.minutes?.let { put("minutes", it) }
                    put("who", item.who)
                    put("note", item.note)
                    put("songId", item.songId)
                    put("songKey", item.songKey)
                })
            }
        })
    })

suspend fun AgoraRepository.planSongs(): List<PlanSong> = api.get<PlanSongs>("/api/songs").songs

/** Adds an empty task (no person, no e-mail), like the web's `{ roleName }`. */
suspend fun AgoraRepository.addEmptyTask(eventId: String, roleName: String) {
    api.post<JsonObject>("/api/events/$eventId/duties", buildJsonObject { put("roleName", roleName) })
}
