package org.agora.app

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.agora.app.data.local.ThemeMode
import org.agora.app.ui.hub.HubApp
import org.agora.app.ui.hub.HubScreen
import org.agora.app.ui.hub.hubApps
import org.agora.app.ui.theme.AgoraTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Extras hub (HubScreen.kt): every tile opens its app, Media only with access to the library, and the hand-drawn
 * motifs and painted fields (HubArt.kt) draw in the light and the dark theme, on phones and on tablets (two columns).
 * With -PscreenshotsOut=<dir> the drawings are also saved there as pictures.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35], qualifiers = "w390dp-h844dp-xxhdpi")
class HubScreenTest {
    @get:Rule val compose = createComposeRule()

    private val out = System.getProperty("agora.screenshots.out").orEmpty()
    private var theme by mutableStateOf(ThemeMode.LIGHT)

    private fun show(apps: List<HubApp>, onOpen: (String) -> Unit = {}) {
        compose.setContent {
            AgoraTheme(theme) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    HubScreen(apps, PaddingValues(top = 24.dp), onOpen)
                }
            }
        }
        compose.waitForIdle()
    }

    /** Draws the screen, and saves it as [name] with -PscreenshotsOut. */
    private fun draw(name: String) {
        if (out.isBlank()) compose.onRoot().captureToImage() else compose.onRoot().captureRoboImage("$out/$name.png")
    }

    @Test
    fun everyTileOpensItsApp() {
        val opened = mutableListOf<String>()
        show(hubApps(aiAvailable = true, mediaAvailable = true)) { opened += it }
        listOf("AI Support", "Media", "Songbook", "Prayer requests").forEach { compose.onNodeWithText(it).performClick() }
        assertEquals(listOf("ai", "media", "songbook", "prayers"), opened)
    }

    @Test
    fun mediaOnlyWithAccess() {
        show(hubApps(aiAvailable = false, mediaAvailable = false))
        compose.onNodeWithText("Media").assertDoesNotExist()
        compose.onNodeWithText("Songbook").assertIsDisplayed()
        compose.onNodeWithText("Prayer requests").assertIsDisplayed()
    }

    @Test
    fun drawsLightAndDark() {
        show(hubApps(aiAvailable = true, mediaAvailable = true))
        draw("hub")
        compose.runOnIdle { theme = ThemeMode.DARK }
        compose.waitForIdle()
        draw("hub-dark")
    }

    @Test
    @Config(qualifiers = "w800dp-h1280dp-xhdpi")
    fun drawsOnTablets() {
        show(hubApps(aiAvailable = true, mediaAvailable = true))
        compose.onNodeWithText("Start chat").assertIsDisplayed()
        draw("hub-tablet")
    }
}
