package org.agora.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// Songbook (server: nova backend/routes/songbook.js). Everyone reads; members with the manage_songbook permission
// add, edit and delete songs - the server says so in `canManage`.

@Serializable
data class Song(
    val id: String = "",
    val title: String = "",
    val artist: String = "",
    val key: String = "",
    // No longer shown or edited, kept as they are when a song is saved again (like the web app)
    val capo: Int? = null,
    val tempo: Int? = null,
    val ccli: String = "",
    val copyright: String = "",
    val content: String = "",
    val createdAt: Long = 0,
    val updatedAt: Long = 0
)

@Serializable
data class Songbook(
    val songs: List<Song> = emptyList(),
    /** The church's CCLI licence number, shown with every song */
    val ccliLicense: String = "",
    val canManage: Boolean = false
)

/** What the editor sends; capo and tempo come from the edited song (null for a new one). */
data class SongInput(
    val title: String,
    val artist: String,
    val key: String,
    val ccli: String,
    val copyright: String,
    val content: String,
    val capo: Int? = null,
    val tempo: Int? = null
)

suspend fun AgoraRepository.songbook(): Songbook = api.get("/api/songs")

/** Creates a song ([id] null) or saves an existing one; returns the song as stored. */
suspend fun AgoraRepository.saveSong(id: String?, input: SongInput): Song {
    val body = buildJsonObject {
        put("title", input.title)
        put("artist", input.artist)
        put("key", input.key.trim())
        put("ccli", input.ccli.trim())
        put("copyright", input.copyright)
        put("content", input.content)
        put("capo", input.capo)
        put("tempo", input.tempo)
    }
    return if (id == null) api.post("/api/songs", body) else api.put("/api/songs/$id", body)
}

suspend fun AgoraRepository.deleteSong(id: String) {
    api.delete<JsonObject>("/api/songs/$id")
}
