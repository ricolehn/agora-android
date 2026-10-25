package org.agora.app

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import org.agora.app.data.local.ThemeMode
import org.agora.app.ui.components.MorphMenu
import org.agora.app.ui.components.MorphMenuButton
import org.agora.app.ui.components.MorphMenuItem
import org.agora.app.ui.components.rememberMorphMenuState
import org.agora.app.ui.theme.AgoraTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * The profile menu grows out of the avatar (MorphMenu.kt): tapped, the picture travels into the menu next to the
 * name and comes back when the menu closes - by a tap outside, back or an entry, whose action runs once. Taps
 * outside never reach the page while the menu is open, and do again once it closes.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35], qualifiers = "w360dp-h780dp-xhdpi")
class MorphMenuTest {
    private companion object {
        const val AVATAR = "avatar"
        const val MENU = "Profile menu"
        const val NAME = "Enrico Lehn"
        const val EMAIL = "enrico.lehn@example.org"
        const val SETTINGS = "Settings"
        const val LOGOUT = "Sign out"
        const val PAGE = "Page button"
        val PICTURE = Color(0xFFFF00FF)
    }

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private var settings = 0
    private var logouts = 0
    private var pageTaps = 0

    @Test
    fun tapOnThePictureOpensTheMenu() {
        show()
        open()
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, MENU)).assertExists()
        compose.onNodeWithText(NAME).assertExists()
        compose.onNodeWithText(EMAIL).assertExists()
        // It hangs from the picture's corner and stays on the screen
        val avatar = compose.onNodeWithTag(AVATAR, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val entry = compose.onNodeWithText(LOGOUT).getUnclippedBoundsInRoot()
        val screen = compose.onRoot().getUnclippedBoundsInRoot()
        assertTrue("the menu is below the picture's top", entry.top > avatar.top)
        assertTrue("the menu runs off the screen", entry.left >= screen.left && entry.right <= screen.right)
    }

    @Test
    fun pictureTravelsIntoTheMenuAndBack() {
        show()
        val home = compose.onNodeWithTag(AVATAR, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("the picture is not drawn in the header", pictureAt(center(home)))
        open()
        // Next to the name: 12 dp gap, then the 40 dp picture, centred on the name and e-mail
        val name = compose.onNodeWithText(NAME).getUnclippedBoundsInRoot()
        val email = compose.onNodeWithText(EMAIL).getUnclippedBoundsInRoot()
        val inMenu = Offset(px(name.left - 32.dp), px((name.top + email.bottom) / 2))
        assertTrue("the picture did not travel into the menu", pictureAt(inMenu))
        assertFalse("the menu does not hang over the picture's place in the header", pictureAt(center(home)))
        compose.onRoot().performTouchInput { click(Offset(width * 0.2f, height * 0.6f)) }
        compose.waitForIdle()
        assertTrue("the picture did not come back", pictureAt(center(home)))
        assertFalse("the picture stayed in the menu", pictureAt(inMenu))
    }

    @Test
    fun tapOutsideClosesWithoutReachingThePage() {
        show()
        open()
        compose.onNodeWithText(PAGE).performClick()
        compose.waitForIdle()
        assertEquals("the tap outside reached the page", 0, pageTaps)
        compose.onNodeWithText(SETTINGS).assertDoesNotExist()
        compose.onNodeWithText(PAGE).performClick()
        compose.runOnIdle { assertEquals("the page did not get the tap after the menu closed", 1, pageTaps) }
    }

    @Test
    fun backClosesTheMenu() {
        show()
        open()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithText(SETTINGS).assertDoesNotExist()
        assertEquals("back ran an entry", 0, settings + logouts)
    }

    @Test
    fun entryRunsOnceAndClosesTheMenu() {
        show()
        open()
        compose.onNodeWithText(SETTINGS).performClick()
        compose.waitForIdle()
        assertEquals(1, settings)
        assertEquals(0, logouts)
        compose.onNodeWithText(SETTINGS).assertDoesNotExist()
    }

    private fun show() {
        compose.setContent {
            AgoraTheme(ThemeMode.LIGHT) {
                val menu = rememberMorphMenuState()
                Box(Modifier.fillMaxSize()) {
                    Button(onClick = { pageTaps++ }, modifier = Modifier.align(Alignment.Center)) { Text(PAGE) }
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp, end = 12.dp), horizontalArrangement = Arrangement.End) {
                        MorphMenuButton(menu) {
                            Box(Modifier.size(40.dp).clip(CircleShape).background(PICTURE).testTag(AVATAR))
                        }
                    }
                    MorphMenu(menu, title = NAME, subtitle = EMAIL, paneTitle = MENU) {
                        MorphMenuItem(SETTINGS, Icons.Outlined.Settings, onClick = { settings++ })
                        MorphMenuItem(LOGOUT, Icons.AutoMirrored.Outlined.Logout, onClick = { logouts++ })
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun open() {
        compose.onNodeWithTag(AVATAR, useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(SETTINGS).assertExists()
    }

    private fun px(dp: Dp): Float = dp.value * compose.density.density

    private fun center(bounds: DpRect) = Offset(px((bounds.left + bounds.right) / 2), px((bounds.top + bounds.bottom) / 2))

    /** Whether the picture's colour is drawn at [point] (in pixels). */
    private fun pictureAt(point: Offset): Boolean {
        val pixel = compose.onRoot().captureToImage().toPixelMap()[point.x.toInt(), point.y.toInt()]
        return abs(pixel.red - PICTURE.red) < 0.1f && abs(pixel.green - PICTURE.green) < 0.1f && abs(pixel.blue - PICTURE.blue) < 0.1f
    }
}
