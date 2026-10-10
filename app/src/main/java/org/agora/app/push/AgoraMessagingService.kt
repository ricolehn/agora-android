package org.agora.app.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.launch
import org.agora.app.AgoraApplication

/** Receives the server's FCM data messages (same fields as the Web Push payload) and shows them. */
class AgoraMessagingService : FirebaseMessagingService() {

    private val container get() = (application as AgoraApplication).container

    override fun onNewToken(token: String) {
        container.appScope.launch { runCatching { container.push.onNewToken(token) } }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        // Nobody signed in on this device: the message belongs to a previous account
        if (!PushManager.isSignedIn(this)) return
        val data = message.data
        val title = data["title"]?.takeIf { it.isNotBlank() } ?: return
        val body = data["body"].orEmpty()
        val url = data["url"]?.takeIf { it.isNotBlank() }
        val tag = data["tag"]?.takeIf { it.startsWith("agora-event-") }
        Notifier.show(this, NotificationTarget.fromUrl(url, title), title, body, tag, data["eventId"]?.takeIf { it.isNotBlank() })
        // Keep an open app in sync right away
        container.store.refreshInBackground()
    }
}
