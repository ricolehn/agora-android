package org.agora.app

import android.app.Application
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.agora.app.data.local.ThemeMode
import org.agora.app.data.model.User
import org.agora.app.ui.ai.AiChatScreen
import org.agora.app.ui.components.ProvideAppLocals
import org.agora.app.ui.theme.AgoraTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Sending a message in the AI chat: the reply streams in with its thoughts, a lookup of the server and the answer, the
 * line above it says what the AI did, its steps open, and the next question goes to the server with the answer before
 * it. (What closed the app in 1.4.0-beta3 - a pattern Android's regular expressions refuse - only shows on Android,
 * not here on the JVM: [AndroidRegexTest] looks for it.)
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35], qualifiers = "w360dp-h780dp-xhdpi")
class AiChatTest {
    @get:Rule val compose = createComposeRule()

    private val context: Application get() = ApplicationProvider.getApplicationContext()

    /** One reply as server CALDERA streams it: a thought, a note on the way, a lookup and what it found, the answer. */
    private val reply = listOf(
        """{"reasoning":"**Reading the question**\n\nThe person wants to know what they paid. I look up the payments."}""",
        """{"content":"Let me look that up."}""",
        """{"lookup":{"id":1,"tool":"payments","step":"Finding the payments of 2026","from":"2026","to":"2026"}}""",
        """{"lookupDone":{"id":1,"count":3,"unit":"entries","eur":45}}""",
        """{"content":"You paid **45 €** in 2026."}"""
    ).joinToString("") { "data: $it\n\n" } + ": ping\n\ndata: [DONE]\n\n"

    private fun ask(text: String) {
        compose.onNode(hasSetTextAction()).performTextInput(text)
        compose.onNodeWithContentDescription(context.getString(R.string.send)).performClick()
    }

    private fun waitForText(text: String) = compose.waitUntil(10_000) {
        compose.onAllNodes(hasText(text, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun `a message gets its streamed reply - with its thoughts, the lookup and the answer`() = FakeServer().use { server ->
        server.on("POST", "/api/ai/chat") { 200 to reply }
        val container = AppContainer(context).apply {
            api.baseUrl = server.base
            api.token = "token"
        }
        compose.setContent {
            AgoraTheme(ThemeMode.LIGHT) {
                ProvideAppLocals(container, SnackbarHostState()) {
                    AiChatScreen(User(uid = "u1", firstName = "Anna", canAccessAi = true), PaddingValues())
                }
            }
        }

        ask("What did I pay in 2026?")
        waitForText("in 2026.")
        compose.waitForIdle()
        // The reply is complete: the line says what the AI did, and the reply and its sign are drawn
        compose.onNodeWithText(context.getString(R.string.ai_thought_lookups, 1), useUnmergedTree = true).assertExists()
        compose.onRoot().captureToImage()

        // Its steps open: the thought, the note on the way and the lookup with what it found
        compose.onNodeWithText(context.getString(R.string.ai_thought_lookups, 1), useUnmergedTree = true).performClick()
        waitForText("Finding the payments of 2026")
        waitForText("Let me look that up.")
        compose.onRoot().captureToImage()

        // The next question goes with the conversation: the answer without its thoughts
        ask("And in 2025?")
        compose.waitUntil(10_000) { server.calls("POST", "/api/ai/chat").size == 2 }
        waitForText("And in 2025?")
        compose.waitForIdle()
        val messages = server.calls("POST", "/api/ai/chat").last().json()["messages"]!!.jsonArray.map {
            it.jsonObject["role"]!!.jsonPrimitive.content to it.jsonObject["content"]!!.jsonPrimitive.content
        }
        assertEquals(
            listOf("user" to "What did I pay in 2026?", "assistant" to "You paid **45 €** in 2026.", "user" to "And in 2025?"),
            messages
        )
    }
}
