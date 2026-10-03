package org.agora.app.push

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.agora.app.AgoraApplication
import org.agora.app.R
import org.agora.app.data.local.SessionStore
import org.agora.app.util.Dates
import java.util.concurrent.TimeUnit

/**
 * Fallback while FCM push is not active (server without FCM, no Google services): checks every ~15 minutes for new duty requests,
 * mentoring messages and events and shows local notifications for anything not seen before.
 */
class PollWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as AgoraApplication).container
        val sessionStore = container.sessionStore
        val session = sessionStore.current()
        val user = session.user ?: return Result.success()
        if (session.token == null || session.baseUrl.isBlank()) return Result.success()
        if (sessionStore.pushToken() != null) return Result.success() // FCM push is active
        if (container.api.token == null) {
            container.api.baseUrl = session.baseUrl
            container.api.token = session.token
        }
        val repo = container.repo
        val prefs = user.effectiveNotifications
        val seen = sessionStore.seenState()
        val ctx = applicationContext

        return try {
            val duties = repo.myDutyRequests()
            val dutyIds = duties.map { it.id }.toSet()
            if (seen.dutyRequests != null && prefs.duties) {
                duties.filter { it.id !in seen.dutyRequests }.forEach {
                    Notifier.show(ctx, NotificationTarget.DUTIES,
                        ctx.getString(R.string.push_duty_title, it.roleName),
                        ctx.getString(R.string.push_duty_body, it.requestedByName, it.roleName, it.eventTitle),
                        tag = "duty-${it.id}")
                }
            }

            val threads = repo.threads()
            val unreadKeys = threads.filter { it.unreadCount > 0 }.map { "${it.id}@${it.lastMessage?.created ?: it.updated}" }.toSet()
            if (seen.messages != null && prefs.messages) {
                threads.filter { it.unreadCount > 0 && "${it.id}@${it.lastMessage?.created ?: it.updated}" !in seen.messages }.forEach {
                    Notifier.show(ctx, NotificationTarget.MESSAGES,
                        ctx.getString(R.string.push_message_title, it.partnerName),
                        it.lastMessage?.text.orEmpty(),
                        tag = "thread-${it.id}")
                }
            }

            val today = Dates.todayIso()
            val events = repo.events().filter { it.lastDay >= today }
            val eventIds = events.map { it.id }.toSet()
            if (seen.events != null && prefs.events) {
                events.filter { it.id !in seen.events && it.createdBy != user.userId }.take(3).forEach {
                    val title = if (it.isTermin) ctx.getString(R.string.push_new_termin, it.title) else ctx.getString(R.string.push_new_event, it.title)
                    Notifier.show(ctx, NotificationTarget.EVENTS, title, Dates.eventWhen(ctx, it), tag = "agora-event-${it.id}", eventId = it.id)
                }
            }

            sessionStore.setSeenState(SessionStore.SeenState(dutyIds, eventIds, unreadKeys))
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val NAME = "agora-poll"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<PollWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }
    }
}
