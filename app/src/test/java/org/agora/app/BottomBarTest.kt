package org.agora.app

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.agora.app.data.local.ThemeMode
import org.agora.app.ui.components.AgoraBottomBar
import org.agora.app.ui.components.BottomBarState
import org.agora.app.ui.components.BottomNavItem
import org.agora.app.ui.components.rememberBottomBarState
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
 * The bottom bar's pill (BottomBar.kt) slides from tab to tab instead of jumping - also in the bar of the new tab, as
 * every tab screen has a bar of its own - and ends round on the picked tab, which is picked once.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35], qualifiers = "w360dp-h780dp-xhdpi")
class BottomBarTest {
    private companion object {
        val TABS = listOf("Home", "Finances", "Events", "Mentoring", "Extras")
        /** The pill's colour in the light theme (navPill). */
        val PILL = Color(0xFFDCF1EC)
    }

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var state: BottomBarState
    /** Like switching tab screens: every pick shows a new bar. */
    private var bar by mutableIntStateOf(0)
    private val picked = mutableListOf<Int>()

    @Test
    fun pillSlidesOnInTheNewTabsBar() {
        show()
        assertTrue("the pill is not on the start tab", pillAt(0))
        compose.mainClock.autoAdvance = false
        tab(3).performClick()
        compose.mainClock.advanceTimeBy(80)
        assertFalse("the pill jumped to the new tab", pillAt(3))
        assertTrue("the pill is not on its way", pillAt(1) || pillAt(2))
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertTrue("the pill did not end on the picked tab", pillAt(3))
        assertFalse("the pill stayed stretched", pillAt(2) || pillAt(4))
        assertFalse("the pill stayed on the old tab", pillAt(0))
        assertEquals(listOf(3), picked)
        tab(3).assertIsSelected()
    }

    @Test
    fun afterAPageThePillIsSimplyThere() {
        show()
        // Coming back from a page that covered the bar: no slide
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { state.sync(2, animate = false) }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        assertTrue("the pill is not on the tab at once", pillAt(2))
        assertFalse("the pill is still on the old tab", pillAt(0) || pillAt(1))
        assertEquals(emptyList<Int>(), picked)
    }

    private fun show() {
        compose.setContent {
            AgoraTheme(ThemeMode.LIGHT) {
                state = rememberBottomBarState(0)
                Box(Modifier.fillMaxSize()) {
                    key(bar) {
                        Box(Modifier.align(Alignment.BottomCenter)) {
                            AgoraBottomBar(TABS.map { BottomNavItem(it, Icons.Outlined.Home, Icons.Outlined.Home) }, state) {
                                picked += it
                                bar++
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun tab(index: Int) =
        compose.onNode(hasText(TABS[index]) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))

    private fun px(dp: Dp): Float = dp.value * compose.density.density

    /**
     * Whether the pill lies under tab [index]: inside its 56 dp pill place (12 to 44 dp from the tab's top), right of
     * the icon.
     */
    private fun pillAt(index: Int): Boolean {
        val bounds = tab(index).getUnclippedBoundsInRoot()
        val point = Offset(px((bounds.left + bounds.right) / 2 + 20.dp), px(bounds.top + 28.dp))
        val pixel = compose.onRoot().captureToImage().toPixelMap()[point.x.toInt(), point.y.toInt()]
        return abs(pixel.red - PILL.red) < 0.03f && abs(pixel.green - PILL.green) < 0.03f && abs(pixel.blue - PILL.blue) < 0.03f
    }
}
