package org.agora.app.data

import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.filter
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.agora.app.data.local.SessionStore
import org.agora.app.data.local.DataCache
import kotlinx.serialization.Serializable
import org.agora.app.data.model.AgoraEvent
import org.agora.app.data.model.DutyRequest
import org.agora.app.data.model.EventSettings
import org.agora.app.data.model.FeeSettings
import org.agora.app.data.model.FinanceRequest
import org.agora.app.data.model.Group
import org.agora.app.data.model.MentoringThread
import org.agora.app.data.model.MyMentorProfile
import org.agora.app.data.model.Person
import org.agora.app.data.model.User
import org.agora.app.data.remote.ApiException

sealed interface AuthState {
    data object Loading : AuthState
    data object NeedsServer : AuthState
    data class NeedsLogin(val baseUrl: String) : AuthState
    data class LoggedIn(val user: User) : AuthState
}

/** Everything the member-facing screens show; refreshed as a whole on live updates, kept on the device (DataCache). */
@Serializable
data class AppData(
    val loaded: Boolean = false,
    val fees: FeeSettings = FeeSettings(),
    val ownPerson: Person? = null,
    val ownRequests: List<FinanceRequest> = emptyList(),
    val events: List<AgoraEvent> = emptyList(),
    val dutyRequests: List<DutyRequest> = emptyList(),
    val threads: List<MentoringThread> = emptyList(),
    val mentorProfile: MyMentorProfile = MyMentorProfile(),
    val eventSettings: EventSettings = EventSettings(),
    val groups: List<Group> = emptyList(),
    val aiEnabled: Boolean = false,
    val error: String? = null
) {
    val unreadThreads: List<MentoringThread> get() = threads.filter { it.unreadCount > 0 }
}

class AppStore(
    private val repo: AgoraRepository,
    private val sessionStore: SessionStore,
    private val scope: CoroutineScope,
    private val cache: DataCache? = null
) {
    private val _auth = MutableStateFlow<AuthState>(AuthState.Loading)
    val auth: StateFlow<AuthState> = _auth.asStateFlow()

    private val _data = MutableStateFlow(AppData())
    val data: StateFlow<AppData> = _data.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _appName = MutableStateFlow("Agora")
    val appName: StateFlow<String> = _appName.asStateFlow()

    /**
     * Emits the changed areas ("all", "events", "mentoring") after server-side changes (SSE) so open screens can
     * reload their own details when their area is affected.
     */
    private val _serverChanges = MutableSharedFlow<Set<String>>(extraBufferCapacity = 1)
    val serverChanges: SharedFlow<Set<String>> = _serverChanges

    /** Callbacks for session lifecycle (push registration etc.). */
    var onLoggedIn: suspend (User) -> Unit = {}
    var onLoggingOut: suspend () -> Unit = {}

    private val refreshMutex = Mutex()
    private var liveJob: Job? = null

    val user: User? get() = (auth.value as? AuthState.LoggedIn)?.user
    val baseUrl: String get() = repo.api.baseUrl

    init {
        scope.launch { repo.api.unauthorized.collect { if (auth.value is AuthState.LoggedIn) logout(remote = false) } }
        keepDataOnDevice()
    }

    /** Saves loaded data a moment after it settles, so the next start opens with it. */
    @OptIn(FlowPreview::class)
    private fun keepDataOnDevice() {
        val cache = cache ?: return
        scope.launch {
            _data.debounce(1_000).collect { data ->
                val user = user ?: return@collect
                if (data.loaded) cache.save(repo.api.baseUrl, user.userId, data.copy(error = null))
            }
        }
    }

    /** Restores the saved session; shows cached user immediately and validates it in the background. */
    private var restoreStarted = false

    suspend fun restore() {
        if (restoreStarted) return
        restoreStarted = true
        sessionStore.appName.first()?.let { _appName.value = it }
        val saved = sessionStore.current()
        if (saved.baseUrl.isBlank()) {
            _auth.value = AuthState.NeedsServer
            return
        }
        repo.api.baseUrl = saved.baseUrl
        val token = saved.token
        if (token == null || saved.user == null) {
            _auth.value = AuthState.NeedsLogin(saved.baseUrl)
            return
        }
        repo.api.token = token
        // Open with the last data right away (read while the splash is still up); fresh data follows
        cache?.load(saved.baseUrl, saved.user.userId)?.let { _data.value = it }
        _auth.value = AuthState.LoggedIn(saved.user)
        // Session check and data refresh side by side: one round trip less before the start page updates
        scope.launch { refreshAll() }
        scope.launch {
            try {
                val fresh = repo.me()
                sessionStore.updateUser(fresh)
                _auth.value = AuthState.LoggedIn(fresh)
                onLoggedIn(fresh)
            } catch (e: Exception) {
                // Offline or server hiccup: keep the cached session. A real 401 triggers logout via `unauthorized`.
            }
        }
    }

    suspend fun connect(url: String) {
        val normalized = normalizeUrl(url)
        requireSecure(normalized)
        val status = repo.status(normalized)
        if (status.setupMode) throw ApiException(503, "setup")
        sessionStore.setBaseUrl(normalized)
        repo.appName()?.let {
            _appName.value = it
            sessionStore.setAppName(it)
        }
        _auth.value = AuthState.NeedsLogin(normalized)
    }

    fun changeServer() {
        _auth.value = AuthState.NeedsServer
    }

    suspend fun login(email: String, password: String) = completeLogin(repo.login(email, password).token)

    suspend fun register(code: String, email: String, firstName: String, lastName: String, password: String) =
        completeLogin(repo.register(code, email, firstName, lastName, password).token)

    private suspend fun completeLogin(token: String?) {
        if (token.isNullOrBlank()) throw ApiException(500, "Keine Sitzung erhalten")
        repo.api.token = token
        val user = repo.me()
        sessionStore.setSession(token, user)
        repo.appName()?.let {
            _appName.value = it
            sessionStore.setAppName(it)
        }
        _data.value = AppData()
        _auth.value = AuthState.LoggedIn(user)
        scope.launch {
            onLoggedIn(user)
            refreshAll()
        }
    }

    suspend fun logout(remote: Boolean = true) {
        stopLiveUpdates()
        if (remote) {
            runCatching { onLoggingOut() }
            repo.logout()
        }
        repo.api.token = null
        // Cached answers belong to this account
        repo.api.clearHttpCache()
        sessionStore.clearSession()
        cache?.clear()
        _data.value = AppData()
        _auth.value = AuthState.NeedsLogin(repo.api.baseUrl)
    }

    suspend fun reloadUser() {
        val fresh = repo.me()
        sessionStore.updateUser(fresh)
        _auth.value = AuthState.LoggedIn(fresh)
    }

    /** Loads all member data in parallel. Individual failures keep the previous value. */
    suspend fun refreshAll(showIndicator: Boolean = false) = refreshMutex.withLock { loadAll(showIndicator) }

    private suspend fun loadAll(showIndicator: Boolean) {
        val user = user ?: return
        if (showIndicator) _refreshing.value = true
        try {
            coroutineScope {
                val old = _data.value
                val fees = async { runCatching { repo.feeSettings() }.getOrDefault(old.fees) }
                val person = async { runCatching { repo.ownPeople(user.userId).firstOrNull() }.getOrElse { old.ownPerson } }
                val requests = async { runCatching { repo.ownRequests(user.userId) }.getOrDefault(old.ownRequests) }
                val events = async { runCatching { repo.events() }.onFailure { if (it is ApiException) lastError = it.message } .getOrDefault(old.events) }
                val duties = async { runCatching { repo.myDutyRequests() }.getOrDefault(old.dutyRequests) }
                val threads = async { runCatching { repo.threads() }.getOrDefault(old.threads) }
                val mentorProfile = async { repo.myMentorProfile() }
                val eventSettings = async { repo.eventSettings() }
                val groups = async { repo.groups() }
                val ai = async { if (user.accessesAi) repo.aiEnabled() else false }
                _data.value = AppData(
                    loaded = true,
                    fees = fees.await(),
                    ownPerson = person.await(),
                    ownRequests = requests.await(),
                    events = events.await(),
                    dutyRequests = duties.await(),
                    threads = threads.await(),
                    mentorProfile = mentorProfile.await(),
                    eventSettings = eventSettings.await(),
                    groups = groups.await(),
                    aiEnabled = ai.await(),
                    error = lastError.also { lastError = null }
                )
            }
        } finally {
            _refreshing.value = false
        }
    }

    @Volatile private var lastError: String? = null

    /** Only the areas a live update named: event data and / or the mentoring threads. */
    private suspend fun refreshAreas(events: Boolean, mentoring: Boolean) = refreshMutex.withLock {
        if (user == null) return@withLock
        coroutineScope {
            val newEvents = if (events) async { runCatching { repo.events() }.getOrNull() } else null
            val newDuties = if (events) async { runCatching { repo.myDutyRequests() }.getOrNull() } else null
            val newThreads = if (mentoring) async { runCatching { repo.threads() }.getOrNull() } else null
            val loadedEvents = newEvents?.await()
            val loadedDuties = newDuties?.await()
            val loadedThreads = newThreads?.await()
            _data.update {
                it.copy(events = loadedEvents ?: it.events, dutyRequests = loadedDuties ?: it.dutyRequests, threads = loadedThreads ?: it.threads)
            }
        }
    }

    private val backgroundRefreshQueued = AtomicBoolean(false)

    /**
     * Refreshes without making the caller wait. Triggers that arrive while a refresh is still waiting for its turn
     * (an action, the chat poll, pull-to-refresh) share that one refresh instead of queueing another ten requests each.
     */
    fun refreshInBackground(showIndicator: Boolean = false) {
        if (showIndicator) _refreshing.value = true
        if (!backgroundRefreshQueued.compareAndSet(false, true)) return
        scope.launch {
            refreshMutex.withLock {
                backgroundRefreshQueued.set(false)
                loadAll(showIndicator)
            }
        }
    }

    /** Applies an optimistic local change, e.g. after answering a duty request. */
    fun updateData(change: (AppData) -> AppData) = _data.update(change)

    @OptIn(FlowPreview::class)
    fun startLiveUpdates() {
        if (liveJob?.isActive == true || user == null) return
        liveJob = scope.launch {
            // The server names the area that changed. Updates arriving in a burst are collected for a moment and
            // answered with one refresh of just those areas (a chat message no longer reloads everything).
            val pending = MutableStateFlow<Set<String>>(emptySet())
            launch { repo.api.liveUpdates().collect { area -> pending.update { it + area } } }
            pending.filter { it.isNotEmpty() }.debounce(LIVE_UPDATE_WINDOW_MS).collect {
                val areas = pending.getAndUpdate { emptySet() }
                if (areas.isEmpty()) return@collect
                _serverChanges.tryEmit(areas)
                if (areas.any { it !in PARTIAL_AREAS }) refreshAll()
                else refreshAreas(events = "events" in areas, mentoring = "mentoring" in areas)
            }
        }
    }

    fun stopLiveUpdates() {
        liveJob?.cancel()
        liveJob = null
    }

    companion object {
        private const val LIVE_UPDATE_WINDOW_MS = 600L
        /** Live-update areas that can be refreshed on their own; anything else reloads everything. */
        private val PARTIAL_AREAS = setOf("events", "mentoring")

        fun normalizeUrl(input: String): String {
            val trimmed = input.trim().trimEnd('/')
            return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
        }

        /** Hosts that may use plain HTTP (same list as network_security_config.xml: local development only). */
        private val LOCAL_HOSTS = setOf("localhost", "127.0.0.1", "10.0.2.2")

        /** Servers must use HTTPS; the network security config would block plain HTTP anyway, this gives a clear message. */
        fun requireSecure(url: String) {
            if (!url.startsWith("http://")) return
            val host = url.removePrefix("http://").substringBefore("/").substringBefore(":")
            if (host !in LOCAL_HOSTS) throw ApiException(HTTPS_REQUIRED, "https")
        }

        const val HTTPS_REQUIRED = 426
    }
}
