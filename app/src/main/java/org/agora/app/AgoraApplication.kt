package org.agora.app

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.svg.SvgDecoder
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import org.agora.app.data.AgoraRepository
import org.agora.app.data.AppStore
import org.agora.app.data.local.SessionStore
import org.agora.app.data.local.DataCache
import org.agora.app.data.remote.AgoraApi
import org.agora.app.push.Notifier
import org.agora.app.push.PollWorker
import org.agora.app.push.PushManager

/** Manual dependency container; one instance per process. */
class AppContainer(context: Context) {
    /** Background work must never crash the app; failures are logged and the UI keeps its last state. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e -> Log.w("Agora", "background task failed", e) })
    val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        explicitNulls = false
    }
    val api = AgoraApi(json, "AgoraAndroid/${BuildConfig.VERSION_NAME}", context.cacheDir)
    val sessionStore = SessionStore(context, json)
    val repo = AgoraRepository(api)
    val store = AppStore(repo, sessionStore, appScope, DataCache(context, json))
    val push = PushManager(context, repo, sessionStore)
}

class AgoraApplication : Application(), SingletonImageLoader.Factory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        container = AppContainer(this)
        Notifier.createChannels(this)
        // Firebase has no bundled config: set it up with the server's last known project before a push arrives
        container.push.restore()
        // After a crash (debug builds) MainActivity only shows the report: start nothing in the background
        if (CrashLog.pending(this) != null) return
        container.store.onLoggedIn = {
            PollWorker.schedule(this)
            runCatching { container.push.sync() }
        }
        container.store.onLoggingOut = {
            PollWorker.cancel(this)
            container.push.disable()
        }
    }

    /** Coil shares the authenticated OkHttp client so profile pictures and receipts load with the token. */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { container.api.http }))
                add(SvgDecoder.Factory())
            }
            .build()
}
