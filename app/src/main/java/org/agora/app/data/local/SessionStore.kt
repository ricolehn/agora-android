package org.agora.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import org.agora.app.data.model.User

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "agora_prefs")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class Session(val baseUrl: String, val token: String?, val user: User?)

/** Persistent app state: server, login session and local preferences. */
class SessionStore(private val context: Context, private val json: Json) {

    private object Keys {
        val baseUrl = stringPreferencesKey("instance_url")
        val token = stringPreferencesKey("auth_token")
        val user = stringPreferencesKey("user_data")
        val appName = stringPreferencesKey("app_name")
        val theme = stringPreferencesKey("theme")
        val pushToken = stringPreferencesKey("fcm_token")
        val seenDutyRequests = stringSetPreferencesKey("seen_duty_requests")
        val seenEvents = stringSetPreferencesKey("seen_events")
        val seenMessages = stringSetPreferencesKey("seen_message_state")
    }

    private val data = context.dataStore.data

    val session: Flow<Session> = data.map { prefs ->
        Session(
            baseUrl = prefs[Keys.baseUrl].orEmpty(),
            token = prefs[Keys.token],
            user = prefs[Keys.user]?.let { runCatching { json.decodeFromString<User>(it) }.getOrNull() }
        )
    }

    val theme: Flow<ThemeMode> = data.map { prefs ->
        prefs[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM
    }

    val appName: Flow<String?> = data.map { it[Keys.appName] }

    suspend fun current(): Session = session.first()

    suspend fun setBaseUrl(url: String) = context.dataStore.edit { it[Keys.baseUrl] = url.trimEnd('/') }

    suspend fun setAppName(name: String) = context.dataStore.edit { it[Keys.appName] = name }

    suspend fun setSession(token: String, user: User) = context.dataStore.edit {
        it[Keys.token] = token
        it[Keys.user] = json.encodeToString(User.serializer(), user)
    }

    suspend fun updateUser(user: User) = context.dataStore.edit { it[Keys.user] = json.encodeToString(User.serializer(), user) }

    suspend fun clearSession() = context.dataStore.edit {
        it.remove(Keys.token)
        it.remove(Keys.user)
        it.remove(Keys.pushToken)
        it.remove(Keys.seenDutyRequests)
        it.remove(Keys.seenEvents)
        it.remove(Keys.seenMessages)
    }

    suspend fun setTheme(mode: ThemeMode) = context.dataStore.edit { it[Keys.theme] = mode.name }

    /** FCM token registered with the server, null while push is not active. */
    suspend fun pushToken(): String? = data.first()[Keys.pushToken]
    suspend fun setPushToken(token: String?) = context.dataStore.edit {
        if (token == null) it.remove(Keys.pushToken) else it[Keys.pushToken] = token
    }

    /** State of the background poller (fallback while FCM push is not active). */
    data class SeenState(val dutyRequests: Set<String>?, val events: Set<String>?, val messages: Set<String>?)

    suspend fun seenState(): SeenState = data.first().let {
        SeenState(it[Keys.seenDutyRequests], it[Keys.seenEvents], it[Keys.seenMessages])
    }

    suspend fun setSeenState(state: SeenState) = context.dataStore.edit { prefs ->
        state.dutyRequests?.let { prefs[Keys.seenDutyRequests] = it }
        state.events?.let { prefs[Keys.seenEvents] = it }
        state.messages?.let { prefs[Keys.seenMessages] = it }
    }
}
