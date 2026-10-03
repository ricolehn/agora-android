package org.agora.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.mutableStateOf
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.agora.app.data.AuthState
import org.agora.app.push.Notifier
import org.agora.app.ui.AgoraApp
import org.agora.app.ui.CrashScreen
import org.agora.app.ui.DeepLink

class MainActivity : AppCompatActivity() {

    private val deepLink = mutableStateOf<DeepLink?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // AppCompatActivity sets up the view tree owners on its own and misses the NavigationEvent owner that
        // Navigation 2.10 needs for back handling (NavHost crashed without it): let ComponentActivity set all of them
        initializeViewTreeOwners()
        val container = (application as AgoraApplication).container
        CrashLog.pending(this)?.let { report ->
            setContent { CrashScreen(report, onClose = { CrashLog.clear(this); finishAndRemoveTask() }) }
            return
        }

        // Keep the splash until the saved session has been read (no login-screen flash)
        splash.setKeepOnScreenCondition { container.store.auth.value == AuthState.Loading }
        if (container.store.auth.value == AuthState.Loading) lifecycleScope.launch { container.store.restore() }
        if (savedInstanceState == null) handleIntent(intent)
        preferHighestRefreshRate()

        setContent { AgoraApp(container, deepLink.value) }
    }

    /**
     * Some phones keep apps at 60 Hz (or lower) unless the window asks for more: pick the display mode with the
     * highest refresh rate at the current resolution. The system still lowers it in battery saver.
     */
    private fun preferHighestRefreshRate() {
        val display = if (Build.VERSION.SDK_INT >= 30) display else @Suppress("DEPRECATION") windowManager.defaultDisplay
        display ?: return
        val current = display.mode
        val best = display.supportedModes
            .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
            .maxByOrNull { it.refreshRate } ?: return
        if (best.refreshRate <= current.refreshRate + 0.5f && window.attributes.preferredDisplayModeId == best.modeId) return
        window.attributes = window.attributes.also { it.preferredDisplayModeId = best.modeId }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val route = intent?.getStringExtra(Notifier.EXTRA_ROUTE) ?: return
        deepLink.value = DeepLink(route, intent.getStringExtra(Notifier.EXTRA_EVENT_ID))
    }
}
