package org.agora.app.data.local

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.agora.app.data.AppData
import java.io.File

/**
 * Last loaded member data on the device, so the app opens with it at once and refreshes in the background.
 * Bound to server and account; app-private and excluded from backups (it holds finance data); cleared on logout.
 */
class DataCache(context: Context, private val json: Json) {
    @Serializable
    private data class Snapshot(val baseUrl: String, val userId: String, val data: AppData)

    private val file = File(context.noBackupFilesDir, "app-data.json")

    suspend fun load(baseUrl: String, userId: String): AppData? = withContext(Dispatchers.IO) {
        runCatching { json.decodeFromString<Snapshot>(file.readText()) }.getOrNull()
            ?.takeIf { it.baseUrl == baseUrl && it.userId == userId }
            ?.data
    }

    suspend fun save(baseUrl: String, userId: String, data: AppData) = withContext(Dispatchers.IO) {
        runCatching {
            // Write aside and swap, so a crash mid-write never leaves a broken file
            val tmp = File(file.path + ".tmp")
            tmp.writeText(json.encodeToString(Snapshot.serializer(), Snapshot(baseUrl, userId, data)))
            if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        file.delete()
    }
}
