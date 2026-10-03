package org.agora.app.push

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import org.agora.app.data.AgoraRepository
import org.agora.app.data.local.SessionStore
import org.agora.app.data.model.FcmClientConfig

data class PushStatus(
    /** null = not checked yet */
    val serverEnabled: Boolean? = null,
    val registered: Boolean = false,
    val error: String? = null
)

/**
 * Push through Firebase Cloud Messaging. There is no google-services.json in the app: every Agora server hands out
 * the public client config of its own Firebase project (`GET /api/push/fcm`), the app initialises Firebase with it
 * and registers its token; the server sends the same notifications as Web Push as FCM data messages
 * ([AgoraMessagingService]). Without FCM (server not configured, no Google services) [PollWorker] keeps checking.
 */
class PushManager(
    private val context: Context,
    private val repo: AgoraRepository,
    private val sessionStore: SessionStore
) {
    private val _status = MutableStateFlow(PushStatus())
    val status: StateFlow<PushStatus> = _status.asStateFlow()

    // Kept outside DataStore so Firebase can be set up synchronously when a push wakes the process
    private val prefs = context.getSharedPreferences("agora_fcm", Context.MODE_PRIVATE)

    /** Called from Application.onCreate: sets Firebase up with the last known server config. */
    fun restore() {
        val config = FcmClientConfig(
            projectId = prefs.getString("projectId", null) ?: return,
            appId = prefs.getString("appId", null) ?: return,
            apiKey = prefs.getString("apiKey", null) ?: return,
            senderId = prefs.getString("senderId", null) ?: return,
            storageBucket = prefs.getString("storageBucket", null)
        )
        runCatching { ensureFirebase(config) }.onFailure { Log.w("Agora", "Firebase restore failed", it) }
    }

    /** (Re)initialises the default FirebaseApp when the server's project differs from the current one. */
    private fun ensureFirebase(config: FcmClientConfig) {
        val options = FirebaseOptions.Builder()
            .setProjectId(config.projectId)
            .setApplicationId(config.appId)
            .setApiKey(config.apiKey)
            .setGcmSenderId(config.senderId)
            .apply { config.storageBucket?.let { setStorageBucket(it) } }
            .build()
        val current = FirebaseApp.getApps(context).firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }
        if (current?.options == options) return
        current?.delete()
        FirebaseApp.initializeApp(context, options)
        prefs.edit()
            .putString("projectId", config.projectId)
            .putString("appId", config.appId)
            .putString("apiKey", config.apiKey)
            .putString("senderId", config.senderId)
            .putString("storageBucket", config.storageBucket)
            .apply()
    }

    private fun firebaseReady() = FirebaseApp.getApps(context).any { it.name == FirebaseApp.DEFAULT_APP_NAME }

    suspend fun refreshStatus() {
        _status.value = _status.value.copy(
            serverEnabled = repo.fcmStatus()?.let { it.enabled && it.config != null } ?: _status.value.serverEnabled,
            registered = sessionStore.pushToken() != null
        )
    }

    /** Called after login / app start and from settings: registers (or refreshes) this device's token. */
    suspend fun sync() {
        val server = repo.fcmStatus()
        val config = server?.config?.takeIf { server.enabled }
        if (config == null) {
            _status.value = _status.value.copy(serverEnabled = if (server != null) false else _status.value.serverEnabled)
            return
        }
        try {
            ensureFirebase(config)
            val token = FirebaseMessaging.getInstance().token.await()
            onNewToken(token)
            _status.value = PushStatus(serverEnabled = true, registered = true)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            _status.value = PushStatus(serverEnabled = true, registered = false, error = e.message ?: e.javaClass.simpleName)
        }
    }

    /** Stores the token on the server; FCM rotates tokens now and then. */
    suspend fun onNewToken(token: String) {
        if (sessionStore.current().token == null) return
        val previous = sessionStore.pushToken()
        if (previous != null && previous != token) repo.unsubscribeFcm(previous)
        repo.subscribeFcm(token)
        sessionStore.setPushToken(token)
    }

    /** Stops push for this device (logout). */
    suspend fun disable() {
        sessionStore.pushToken()?.let { repo.unsubscribeFcm(it) }
        sessionStore.setPushToken(null)
        if (firebaseReady()) runCatching { FirebaseMessaging.getInstance().deleteToken().await() }
        _status.value = PushStatus(serverEnabled = _status.value.serverEnabled)
    }
}
