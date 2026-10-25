package org.agora.app

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import org.agora.app.data.local.ThemeMode
import org.agora.app.ui.components.AgoraCard
import org.agora.app.ui.components.LocalNavAnimation
import org.agora.app.ui.components.LocalSharedTransition
import org.agora.app.ui.components.containerTransform
import org.agora.app.ui.theme.AgoraTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A page that grew out of a card shrinks back into it (ContainerTransform.kt): the card's shadow comes along and stays
 * until the card is back in its place. It used to vanish for the last frames of the way back and pop in again.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35], qualifiers = "w360dp-h780dp-xhdpi")
class ContainerTransformTest {
    private companion object {
        const val CARD = "card"
        /** How much darker (of 255) the shadow makes the page under the card, at least. */
        const val SHADOW = 3f
    }

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private var open by mutableStateOf(false)

    @Test
    fun cardKeepsItsShadowToTheEndOfTheWayBack() {
        show()
        // Where the card rests (and lands again): the shadow is checked just under it
        val bounds = compose.onNodeWithTag(CARD, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val x = px(((bounds.left + bounds.right) / 2).value)
        val y = px(bounds.bottom.value + 3f)
        val background = brightness(px(4f), px(700f))
        val resting = brightness(x, y)
        assertTrue("no shadow under the card ($resting on $background)", resting < background - SHADOW)
        compose.runOnIdle { open = true }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { open = false }
        val frames = (1..45).map {
            compose.mainClock.advanceTimeBy(16)
            brightness(x, y)
        }
        val first = frames.indexOfFirst { it < background - SHADOW }
        assertTrue("the shadow never came back: $frames", first >= 0)
        frames.drop(first).forEachIndexed { i, shade ->
            assertTrue("the shadow vanished ${(first + i + 1) * 16} ms into the way back: $frames", shade < background - 1f)
        }
        assertEquals("the card did not end as it was", resting, frames.last(), 0.5f)
    }

    private fun show() {
        compose.setContent {
            AgoraTheme(ThemeMode.LIGHT) {
                SharedTransitionLayout {
                    CompositionLocalProvider(LocalSharedTransition provides this) {
                        // Like the NavHost for a page opened from a card: the list stays, the page grows over it
                        AnimatedContent(open, transitionSpec = { EnterTransition.None togetherWith ExitTransition.KeepUntilTransitionsFinished }) { page ->
                            CompositionLocalProvider(LocalNavAnimation provides this) {
                                if (page) Box(Modifier.fillMaxSize().containerTransform(CARD, page = true).background(MaterialTheme.colorScheme.background)) {
                                    Text("Page")
                                }
                                else Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                                    AgoraCard(Modifier.padding(24.dp).testTag(CARD).containerTransform(CARD), onClick = {}) { Text("Card") }
                                }
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun px(dp: Float) = dp * compose.density.density

    private fun brightness(x: Float, y: Float): Float {
        val pixel = compose.onRoot().captureToImage().toPixelMap()[x.toInt(), y.toInt()]
        return (pixel.red + pixel.green + pixel.blue) / 3f * 255f
    }
}
