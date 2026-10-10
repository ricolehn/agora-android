package org.agora.app.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import org.agora.app.MainActivity
import org.agora.app.R

/** Where a notification should lead when tapped (mirrors the PWA's `data.url` hash routes). */
enum class NotificationTarget(val channelId: String, val route: String) {
    DUTIES("duties", "events"),
    EVENTS("events", "events"),
    MESSAGES("messages", "mentoring"),
    FINANCES("finances", "finances"),
    GENERAL("general", "home");

    companion object {
        /** Maps a Web Push payload (`data.url` like "/#events") to a channel. */
        fun fromUrl(url: String?, title: String): NotificationTarget = when {
            url == null -> GENERAL
            url.contains("#mentoring") -> MESSAGES
            url.contains("#requests") -> FINANCES
            url.contains("#events") && (title.startsWith("Neuer Termin") || title.startsWith("Neues Event")) -> EVENTS
            url.contains("#events") -> DUTIES
            else -> GENERAL
        }
    }
}

object Notifier {
    const val EXTRA_ROUTE = "agora_route"
    const val EXTRA_EVENT_ID = "agora_event_id"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val channels = listOf(
            NotificationChannel("duties", context.getString(R.string.notif_duties_title), NotificationManager.IMPORTANCE_HIGH)
                .apply { description = context.getString(R.string.notif_duties_desc) },
            NotificationChannel("events", context.getString(R.string.notif_events_title), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = context.getString(R.string.notif_events_desc) },
            NotificationChannel("messages", context.getString(R.string.notif_messages_title), NotificationManager.IMPORTANCE_HIGH)
                .apply { description = context.getString(R.string.notif_messages_desc) },
            NotificationChannel("finances", context.getString(R.string.notif_finances_title), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = context.getString(R.string.notif_finances_desc) },
            NotificationChannel("general", context.getString(R.string.notif_general_title), NotificationManager.IMPORTANCE_DEFAULT)
        )
        manager.createNotificationChannels(channels)
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Shows a notification; the same `tag` replaces an earlier one (like the service worker does). */
    fun show(context: Context, target: NotificationTarget, title: String, body: String, tag: String? = null, eventId: String? = null) {
        if (!canNotify(context)) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_ROUTE, target.route)
            eventId?.let { putExtra(EXTRA_EVENT_ID, it) }
        }
        val id = (tag ?: "$title|$body").hashCode()
        val pending = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, target.channelId)
            .setSmallIcon(R.drawable.ic_stat_agora)
            .setColor(ContextCompat.getColor(context, R.color.brand_primary))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setCategory(if (target == NotificationTarget.MESSAGES) NotificationCompat.CATEGORY_MESSAGE else NotificationCompat.CATEGORY_EVENT)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // permission revoked between check and notify
        }
    }
}
