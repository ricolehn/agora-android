package org.agora.app.ui

import android.annotation.SuppressLint
import org.agora.app.ui.components.FrostStatusBar
import org.agora.app.ui.components.ClearStatusBar
import org.agora.app.ui.components.SoftIcons
import org.agora.app.ui.songbook.SongbookScreen
import org.agora.app.ui.prayers.PrayersScreen
import org.agora.app.ui.media.MediaScreen
import androidx.compose.material.icons.outlined.GridView
import org.agora.app.ui.hub.hubApps
import org.agora.app.ui.hub.HubScreen
import org.agora.app.ui.components.ContainerOrigin
import org.agora.app.ui.components.LocalContainerOrigin
import org.agora.app.ui.components.LocalNavAnimation
import org.agora.app.ui.components.LocalSharedTransition
import org.agora.app.ui.components.containerTransform
import org.agora.app.ui.components.sharedChrome
import org.agora.app.ui.components.LocalFrost
import org.agora.app.ui.components.frosted
import org.agora.app.ui.components.frostSource
import dev.chrisbanes.haze.rememberHazeState
import org.agora.app.ui.components.StatusBarScrim
import org.agora.app.ui.components.SystemBarAppearance
import org.agora.app.ui.components.rememberCollapsingHeader
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.background
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation.NavBackStackEntry
import org.agora.app.ui.components.AvatarRings
import org.agora.app.ui.components.highFrameRateWhileTouching
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.activity.BackEventCompat
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Euro
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.agora.app.AppContainer
import org.agora.app.R
import org.agora.app.data.AuthState
import org.agora.app.data.local.ThemeMode
import org.agora.app.data.model.User
import org.agora.app.push.Notifier
import org.agora.app.ui.ai.AiChatScreen
import org.agora.app.ui.auth.ChurchLogo
import org.agora.app.ui.auth.LoginScreen
import org.agora.app.ui.auth.ServerScreen
import org.agora.app.ui.components.AgoraBottomBar
import org.agora.app.ui.components.AgoraHeader
import org.agora.app.ui.components.AgoraSnackbar
import org.agora.app.ui.components.BottomNavItem
import org.agora.app.ui.components.LocalContainer
import org.agora.app.ui.components.LocalPictureVersions
import org.agora.app.ui.components.MorphMenu
import org.agora.app.ui.components.MorphMenuButton
import org.agora.app.ui.components.MorphMenuItem
import org.agora.app.ui.components.rememberMorphMenuState
import org.agora.app.ui.components.ProvideAppLocals
import org.agora.app.ui.components.UserAvatar
import org.agora.app.ui.components.rememberBottomBarState
import org.agora.app.ui.events.EventDetailScreen
import org.agora.app.ui.events.EventEditScreen
import org.agora.app.ui.events.EventsScreen
import org.agora.app.ui.events.EventsTabRequest
import org.agora.app.ui.finance.MemberFinanceScreen
import org.agora.app.ui.finance.TreasurerScreen
import org.agora.app.ui.home.HomeScreen
import org.agora.app.ui.mentoring.ChatScreen
import org.agora.app.ui.mentoring.MentoringScreen
import org.agora.app.ui.settings.SettingsScreen
import org.agora.app.ui.settings.openInBrowser
import org.agora.app.ui.theme.Agora
import org.agora.app.ui.theme.AgoraTheme

/** A deep link from a notification: top-level route plus an optional event. */
data class DeepLink(val route: String, val eventId: String? = null, val nonce: Long = System.nanoTime())

// The PWA uses line icons for both states and only recolours the active tab
private enum class Tab(val route: String, val label: Int, val icon: ImageVector, val selectedIcon: ImageVector) {
    HOME("home", R.string.nav_start, SoftIcons.Home, SoftIcons.Home),
    FINANCES("finances", R.string.nav_finances, SoftIcons.Finances, SoftIcons.Finances),
    EVENTS("events", R.string.nav_events, SoftIcons.Events, SoftIcons.Events),
    MENTORING("mentoring", R.string.nav_mentoring, SoftIcons.Mentoring, SoftIcons.Mentoring),
    EXTRAS("extras", R.string.nav_hub, SoftIcons.Extras, SoftIcons.Extras)
}

/** Apps inside "Extras": they keep its nav entry highlighted (web: HUB_TABS). */
private val HUB_ROUTES = setOf("ai", "media", "songbook", "prayers")
private fun tabOf(route: String?): String? = if (route in HUB_ROUTES) "extras" else route

@Composable
fun AgoraApp(container: AppContainer, deepLink: DeepLink?) {
    val store = container.store
    val auth by store.auth.collectAsStateWithLifecycle()
    val theme by container.sessionStore.theme.collectAsStateWithLifecycle(ThemeMode.SYSTEM)
    val appName by store.appName.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    AgoraTheme(theme) {
        SystemBarAppearance()
        ProvideAppLocals(container, snackbar) {
            // High refresh rate while scrolling on adaptive-refresh phones (see highFrameRateWhileTouching)
            Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize().highFrameRateWhileTouching()) {
              Box(Modifier.fillMaxSize()) {
                when (val state = auth) {
                    AuthState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    AuthState.NeedsServer -> ServerScreen(onConnect = { store.connect(it) }, initialUrl = container.api.baseUrl)
                    is AuthState.NeedsLogin -> LoginScreen(
                        appName = appName,
                        serverHost = state.baseUrl.toHttpUrlOrNull()?.host ?: state.baseUrl,
                        onLogin = { email, pw -> store.login(email, pw) },
                        onRegister = { code, email, first, last, pw -> store.register(code, email, first, last, pw) },
                        onChangeServer = store::changeServer
                    )
                    is AuthState.LoggedIn -> MainShell(state.user, appName, deepLink, snackbar)
                }
                // Content scrolls up under the status bar; the fade keeps it readable
                StatusBarScrim(Modifier.align(Alignment.TopCenter))
              }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
// The shell's Scaffold only hosts the snackbar: each destination handles the insets itself (TabChrome, pages)
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
private fun MainShell(user: User, appName: String, deepLink: DeepLink?, snackbar: SnackbarHostState) {
    val container = LocalContainer.current
    val store = container.store
    val context = LocalContext.current
    val data by store.data.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val scope = rememberCoroutineScope()
    val menu = rememberMorphMenuState()
    var askedPermission by rememberSaveable { mutableStateOf(false) }

    val tabs = Tab.entries.toList()
    val tabIndex = tabs.indexOfFirst { it.route == tabOf(route) }
    val isTopLevel = tabIndex >= 0
    // Above the tab screens: every tab has its own bottom bar, the pill slides on from one to the next (BottomBar.kt)
    val tabBar = rememberBottomBarState(tabIndex.coerceAtLeast(0))

    // Live updates only while the app is visible; refresh when coming back
    LifecycleStartEffect(user.userId) {
        store.startLiveUpdates()
        if (data.loaded) store.refreshInBackground()
        onStopOrDispose { store.stopLiveUpdates() }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && !askedPermission && !Notifier.canNotify(context)) {
            askedPermission = true
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    LaunchedEffect(deepLink) {
        val link = deepLink ?: return@LaunchedEffect
        // The NavHost sits in the Scaffold's subcomposition: wait until its graph is set (cold start from a notification)
        nav.currentBackStackEntryFlow.first()
        // Intent extras come from outside (MainActivity is exported): only visible tabs and plain event ids
        if (tabs.any { it.route == link.route }) nav.navigateTab(link.route)
        else if (link.route in HUB_ROUTES) { nav.navigateTab("extras"); nav.navigate(link.route) }
        else if (link.route == "settings") nav.navigate("settings")
        link.eventId?.takeIf { EVENT_ID.matches(it) }?.let { nav.navigate("event/$it") }
    }
    // The pill also follows tab changes that don't come from the bar (back to the start tab, a notification); after a
    // page, which covered the bar, it is simply there
    var shownTab by remember { mutableIntStateOf(tabIndex) }
    LaunchedEffect(tabIndex) {
        if (tabIndex >= 0) tabBar.sync(tabIndex, animate = shownTab >= 0)
        shownTab = tabIndex
    }

    // Header and bottom bar are part of every tab screen (TabChrome): a page opened from a card grows over them and
    // they leave together with the tab, so they never vanish early or flash at the end of the animation
    val header: @Composable () -> Unit = {
        AgoraHeader(appName, logo = { ChurchLogo(36.dp) }) {
            if (data.error != null) Icon(Icons.Outlined.CloudOff, stringResource(R.string.offline), Modifier.padding(end = 8.dp), tint = MaterialTheme.colorScheme.error)
            // Tapped, the picture turns into the profile menu (MorphMenu.kt)
            MorphMenuButton(menu) {
                UserAvatar(user.userId, user.fullName, 40.dp, ring = AvatarRings.of(user), glow = false)
            }
        }
    }
    // Profile menu of the PWA (`.profile-dropdown`): name and e-mail next to the picture, then the entries
    val profileMenu: @Composable () -> Unit = {
        MorphMenu(menu, title = user.fullName, subtitle = user.email.takeIf { it.isNotBlank() }, paneTitle = stringResource(R.string.profile_menu)) {
            MorphMenuItem(stringResource(R.string.nav_settings), Icons.Outlined.Settings, onClick = { nav.navigate("settings") })
            if (user.isAdmin) MorphMenuItem(
                stringResource(R.string.nav_superadmin_settings), Icons.Outlined.Shield, trailingIcon = Icons.AutoMirrored.Outlined.OpenInNew,
                onClick = { openInBrowser(context, container.api.baseUrl + "/#super-admin-settings") }
            )
            MorphMenuItem(stringResource(R.string.logout), Icons.AutoMirrored.Outlined.Logout, color = Agora.colors.danger, onClick = { scope.launch { store.logout() } })
        }
    }
    val bottomBar: @Composable () -> Unit = {
        AgoraBottomBar(
            items = tabs.map { tab ->
                BottomNavItem(
                    stringResource(tab.label), tab.icon, tab.selectedIcon,
                    badge = when (tab) {
                        Tab.MENTORING -> data.unreadThreads.size
                        Tab.EVENTS -> data.dutyRequests.size
                        else -> 0
                    }
                )
            },
            state = tabBar,
            onSelect = { nav.navigateTab(tabs[it].route) }
        )
    }

    SharedTransitionLayout {
    CompositionLocalProvider(LocalSharedTransition provides this, LocalPictureVersions provides data.pictureVersions) {
    Scaffold(
        // Above the bottom bar on tabs, like before
        snackbarHost = { SnackbarHost(snackbar, Modifier.padding(bottom = if (isTopLevel) 80.dp else 0.dp)) { AgoraSnackbar(it) } },
        containerColor = MaterialTheme.colorScheme.background
    ) { _ ->
        AgoraNavHost(nav, user) { content -> TabChrome(header, bottomBar, profileMenu, content) }
    }
    }
    }
}

/**
 * Header and bottom bar of a tab screen. Between tabs they are one shared element, so they stay put while the
 * screens fade; a page opened from a card covers them and they go with the tab.
 * The header slides away with the content and comes back on the first scroll up (CollapsingHeader); only its
 * drawing moves, so the list keeps its layout while scrolling and runs up under the status bar.
 * [overlay] lies above all of it (the profile menu); a page opened from the tab covers it like the rest.
 */
@Composable
private fun TabChrome(
    header: @Composable () -> Unit,
    bottomBar: @Composable () -> Unit,
    overlay: @Composable () -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    val headerScroll = rememberCollapsingHeader()
    // Frosted glass: the page is the source, header and bottom bar blur what runs on under them (Frost.kt)
    val frost = rememberHazeState()
    ClearStatusBar(true)
    CompositionLocalProvider(LocalFrost provides frost) {
      Box(Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.nestedScroll(headerScroll.connection),
            topBar = {
                Box(
                    Modifier
                        .sharedChrome("header")
                        .onSizeChanged { headerScroll.limit = -it.height.toFloat() }
                        .graphicsLayer { translationY = headerScroll.offset }
                        .frosted(frost)
                ) { header() }
            },
            bottomBar = { Box(Modifier.sharedChrome("bottom-bar")) { bottomBar() } },
            containerColor = Color.Transparent
        ) { padding ->
            Box(Modifier.fillMaxSize()) {
                // Page colour inside the source, so the glass hides the sharp original (Frost.kt)
                Box(Modifier.fillMaxSize().frostSource(frost).background(MaterialTheme.colorScheme.background)) { content(padding) }
                // Under the header while it is shown; once it slid away the content blurs under the clock
                FrostStatusBar(state = frost)
            }
        }
        overlay()
      }
    }
}

private fun NavHostController.navigateTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

/** Opened from a card with the container transform (see ContainerTransform.kt). */
private val NavBackStackEntry.fromCard: Boolean get() = arguments?.getString("ct") != null

private val EVENT_ID = Regex("[A-Za-z0-9_-]{1,64}")
private val TAB_ROUTES = setOf("home", "finances", "events", "mentoring", "extras")

/** Switching between the bottom bar tabs (also "back" to the start tab) only fades. */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.betweenTabs(): Boolean =
    initialState.destination.route in TAB_ROUTES && targetState.destination.route in TAB_ROUTES

/**
 * Pages (settings, editor, opened without a card) slide in from the right and out to the right again. With
 * predictive back the page follows the finger while the page below comes in from a quarter to the left, like a
 * stack of sheets, instead of shrinking and vanishing.
 */
private val pageSpec = tween<androidx.compose.ui.unit.IntOffset>(350, easing = FastOutSlowInEasing)
private val pageFade = tween<Float>(350, easing = FastOutSlowInEasing)

/** Hands the destination's animation scope to the cards and pages inside (container transform). */
@Composable
private fun AnimatedVisibilityScope.Shared(content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalNavAnimation provides this, content = content)

@Composable
private fun AgoraNavHost(
    nav: NavHostController,
    user: User,
    chrome: @Composable (content: @Composable (PaddingValues) -> Unit) -> Unit
) {
    val origin = remember { ContainerOrigin() }
    // A page opened from a card carries the card's key, so it can grow out of it and shrink back into it
    val openEvent: (String) -> Unit = { id -> nav.navigate(origin.take()?.let { "event/$id?ct=$it" } ?: "event/$id") }
    val openThread: (String) -> Unit = { id -> nav.navigate(origin.take()?.let { "chat/$id?ct=$it" } ?: "chat/$id") }
    val ctArg = navArgument("ct") { type = NavType.StringType; nullable = true; defaultValue = null }
    CompositionLocalProvider(LocalContainerOrigin provides origin) {
    NavHost(
        navController = nav,
        startDestination = "home",
        // The container transform animates on its own: the screen below stays put, the page needs no fade
        enterTransition = {
            when {
                targetState.fromCard -> EnterTransition.None
                betweenTabs() -> fadeIn()
                else -> slideInHorizontally(pageSpec) { it }
            }
        },
        exitTransition = {
            when {
                targetState.fromCard -> ExitTransition.KeepUntilTransitionsFinished
                betweenTabs() -> fadeOut()
                else -> slideOutHorizontally(pageSpec) { -it / 4 } + fadeOut(pageFade, targetAlpha = 0.7f)
            }
        },
        popEnterTransition = {
            when {
                initialState.fromCard -> EnterTransition.None
                betweenTabs() -> fadeIn()
                else -> slideInHorizontally(pageSpec) { -it / 4 } + fadeIn(pageFade, initialAlpha = 0.7f)
            }
        },
        popExitTransition = {
            when {
                initialState.fromCard -> ExitTransition.KeepUntilTransitionsFinished
                betweenTabs() -> fadeOut()
                else -> slideOutHorizontally(pageSpec) { it } + fadeOut(pageFade)
            }
        },
        // The back gesture has its own transitions (the default shrinks the page and drops it): the page follows
        // the finger away from the edge it was swiped from and fades out softly, the page below fades in behind it
        predictivePopEnterTransition = { edge ->
            when {
                initialState.fromCard -> EnterTransition.None
                betweenTabs() -> fadeIn()
                else -> slideInHorizontally(pageSpec) { if (edge == BackEventCompat.EDGE_RIGHT) it / 4 else -it / 4 } +
                    fadeIn(pageFade, initialAlpha = 0.6f)
            }
        },
        predictivePopExitTransition = { edge ->
            when {
                initialState.fromCard -> ExitTransition.KeepUntilTransitionsFinished
                betweenTabs() -> fadeOut()
                else -> slideOutHorizontally(pageSpec) { if (edge == BackEventCompat.EDGE_RIGHT) -it else it } + fadeOut(pageFade)
            }
        }
    ) {
        composable("home") { Shared { chrome { padding ->
            HomeScreen(user, padding, onOpenFinances = { nav.navigateTab("finances") }, onOpenEvent = openEvent, onOpenThread = openThread,
                onOpenMentoring = { nav.navigateTab("mentoring") },
                onOpenTermine = { EventsTabRequest.termine = true; nav.navigateTab("events") })
        } } }
        composable("finances") { Shared { chrome { padding ->
            if (user.canViewFinances) TreasurerScreen(user, padding)
            else MemberFinanceScreen(user, padding)
        } } }
        composable("events") { Shared { chrome { padding ->
            EventsScreen(user, padding, onOpenEvent = openEvent, onCreate = { type -> nav.navigate("event-edit?type=$type") })
        } } }
        composable("mentoring") { Shared { chrome { padding -> MentoringScreen(user, padding, onOpenThread = openThread) } } }
        composable("extras") { Shared { chrome { padding ->
            val data by LocalContainer.current.store.data.collectAsStateWithLifecycle()
            HubScreen(hubApps(user.accessesAi && data.aiEnabled, data.media.hasAccess), padding, onOpen = { nav.navigate(it) })
        } } }
        // A page of its own like the songbook and the prayers: it slides in and out like every other page
        composable("ai") { Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding -> AiChatScreen(user, padding, onBack = { nav.popBackStack() }) } }
        composable("media") { MediaScreen(onBack = { nav.popBackStack() }) }
        composable("songbook") { SongbookScreen(onBack = { nav.popBackStack() }) }
        composable("prayers") { PrayersScreen(user, onBack = { nav.popBackStack() }) }
        composable("settings") { SettingsScreen(user, onBack = { nav.popBackStack() }) }
        composable("event/{id}?ct={ct}", arguments = listOf(navArgument("id") { type = NavType.StringType }, ctArg)) { entry -> Shared {
            Box(Modifier.fillMaxSize().containerTransform(entry.arguments?.getString("ct"), page = true).background(MaterialTheme.colorScheme.background)) {
                EventDetailScreen(entry.arguments?.getString("id").orEmpty(), user, onBack = { nav.popBackStack() },
                    onEdit = { nav.navigate("event-edit?id=$it") })
            }
        } }
        composable(
            "event-edit?id={id}&type={type}",
            arguments = listOf(
                navArgument("id") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("type") { type = NavType.StringType; defaultValue = "event" }
            )
        ) { entry ->
            EventEditScreen(
                eventId = entry.arguments?.getString("id"),
                initialType = entry.arguments?.getString("type") ?: "event",
                user = user,
                onBack = { nav.popBackStack() },
                onSaved = { nav.popBackStack() }
            )
        }
        composable("chat/{id}?ct={ct}", arguments = listOf(navArgument("id") { type = NavType.StringType }, ctArg)) { entry -> Shared {
            Box(Modifier.fillMaxSize().containerTransform(entry.arguments?.getString("ct"), page = true).background(MaterialTheme.colorScheme.background)) {
                ChatScreen(entry.arguments?.getString("id").orEmpty(), onBack = { nav.popBackStack() })
            }
        } }
    }
    }
}
