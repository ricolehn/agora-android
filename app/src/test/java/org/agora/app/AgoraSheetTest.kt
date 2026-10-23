package org.agora.app

import android.app.Application
import android.view.View
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.agora.app.data.local.ThemeMode
import org.agora.app.ui.components.AgoraSheet
import org.agora.app.ui.components.SheetController
import org.agora.app.ui.components.rememberSheetController
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

/**
 * Pull-up sheets about as tall as the screen (e.g. a new duty roster entry "In the order of service"): dragged down they
 * follow the finger, and after a drag that is given up everything in them still reacts to taps.
 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35], qualifiers = "w360dp-h780dp-xhdpi")
class AgoraSheetTest {
    private companion object {
        const val TITLE = "Sheet title"
        const val TAP = "Tap me"
        const val BODY = "body"
        val STATUS_BAR = 40.dp
        val NAVIGATION_BAR = 24.dp
    }

    @get:Rule val compose = createComposeRule()

    private var body by mutableStateOf(0.dp)
    private var taps = 0
    private var dismissed = 0
    private lateinit var controller: SheetController
    private lateinit var sheetView: View

    @Test
    fun dragFromContentMovesTallSheet() {
        showTallSheet()
        val start = titleTop()
        compose.onNodeWithTag(BODY).performTouchInput {
            down(center)
            repeat(20) { moveBy(Offset(0f, 10.dp.toPx())) }
        }
        val moved = titleTop() - start
        compose.onNodeWithTag(BODY).performTouchInput { up() }
        assertTrue("the sheet moved $moved for a 200 dp drag", moved > 150.dp)
    }

    @Test
    fun tallSheetStaysUsableAfterHandleDragIsGivenUp() {
        showTallSheet()
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.Dismiss)).performTouchInput {
            down(center)
            repeat(20) { moveBy(Offset(0f, 10.dp.toPx())) }
            repeat(20) { moveBy(Offset(0f, -10.dp.toPx())) }
            up()
        }
        compose.waitForIdle()
        assertEquals("the sheet was closed", 0, dismissed)
        assertFalse("the sheet still counts as animating", controller.state.isAnimationRunning)
        compose.onNodeWithText(TAP).performClick()
        compose.runOnIdle { assertEquals("the tap did not reach the button", 1, taps) }
    }

    private fun titleTop() = compose.onNodeWithText(TITLE).getUnclippedBoundsInRoot().top

    /**
     * Opens the sheet under a status and a navigation bar (the test window has none), with a body that makes it half a
     * status bar shorter than the screen, like a new roster entry "In the order of service" on a phone.
     */
    private fun showTallSheet() {
        compose.setContent {
            AgoraTheme(ThemeMode.LIGHT) {
                controller = rememberSheetController { dismissed++ }
                AgoraSheet(title = TITLE, onDismiss = { dismissed++ }, controller = controller) {
                    sheetView = LocalView.current
                    Button(onClick = { taps++ }) { Text(TAP) }
                    Spacer(Modifier.fillMaxWidth().height(body).testTag(BODY))
                }
            }
        }
        compose.waitForIdle()
        val density = compose.density.density
        compose.runOnIdle {
            val bars = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, (STATUS_BAR.value * density).toInt(), 0, 0))
                .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, (NAVIGATION_BAR.value * density).toInt()))
                .build()
            ViewCompat.dispatchApplyWindowInsets(sheetView, bars)
        }
        compose.waitForIdle()
        // Without a body the sheet is only its fixed parts
        val screen = (sheetView.rootView.height / density).dp
        val fixed = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.PaneTitle)).getUnclippedBoundsInRoot().height
        body = screen - STATUS_BAR / 2 - fixed
        compose.waitForIdle()
    }
}
