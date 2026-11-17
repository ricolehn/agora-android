package org.agora.app

import android.app.Application
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.agora.app.data.local.ThemeMode
import org.agora.app.data.model.Person
import org.agora.app.data.model.StandingOrder
import org.agora.app.ui.components.LocalContainer
import org.agora.app.ui.components.LocalMessageScope
import org.agora.app.ui.components.LocalSnackbar
import org.agora.app.ui.finance.ManageStandingOrderSheet
import org.agora.app.ui.theme.AgoraTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Ending a standing order from a member card: a card read through API v2 holds v2's ids. When the app went over to
 * the old API meanwhile (a server that answers no v2 any more), ending the order changed nothing and still said
 * "Standing order updated" - the order went on booking. Now the card is read anew and the sheet says why nothing
 * changed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w360dp-h780dp-xhdpi")
class StandingOrderSheetTest {
    @get:Rule val compose = createComposeRule()

    private val context: Application get() = ApplicationProvider.getApplicationContext()
    private val snackbar = SnackbarHostState()
    private var saved = 0

    /** Anna as a card read through API v2 shows her: her record's v2 id and version, and her standing order's v2 id. */
    private val anna = Person(
        id = "-NannaKey", uid = "u-anna", name = "Anna Muster", memberId = "m-anna", version = "v-anna",
        standingOrders = listOf(StandingOrder(id = "so1", amount = 15.0, startDate = "2026-01-01", note = "Beitrag"))
    )

    private fun endOrder(server: FakeServer, apiV2: Boolean) {
        val container = AppContainer(context).apply {
            api.baseUrl = server.base
            api.token = "token"
            api.apiV2 = apiV2
        }
        compose.setContent {
            AgoraTheme(ThemeMode.LIGHT) {
                CompositionLocalProvider(LocalContainer provides container, LocalSnackbar provides snackbar, LocalMessageScope provides rememberCoroutineScope()) {
                    ManageStandingOrderSheet(anna, anna.standingOrders.single(), onDismiss = {}, onSaved = { saved++ })
                }
            }
        }
        compose.onNodeWithText(context.getString(R.string.so_end_save)).performClick()
        // The call goes over the network: done when the sheet says how it went
        compose.waitUntil(10_000) { snackbar.currentSnackbarData != null }
        compose.waitForIdle()
    }

    private val message get() = snackbar.currentSnackbarData?.visuals?.message

    @Test
    fun `a card read through API v2 after the app went over to the old API is read anew - nothing is said to be saved`() = FakeServer().use { server ->
        endOrder(server, apiV2 = false)
        assertEquals(context.getString(R.string.so_gone), message)
        assertEquals("the card is read anew", 1, saved)
        assertTrue("nothing written through the member's record", server.calls("GET", "/api/db").isEmpty() && server.calls("POST", "/api/db/transaction").isEmpty())
    }

    @Test
    fun `through API v2 the order is ended there and the sheet says so`() = FakeServer().use { server ->
        server.on("PATCH", "/api/v2/standing-orders/so1") { 200 to """{"id":"so1"}""" }
        endOrder(server, apiV2 = true)
        assertEquals(context.getString(R.string.toast_so_updated), message)
        assertEquals(1, saved)
        assertEquals("v-anna", server.calls("PATCH", "/api/v2/standing-orders/so1").single().json()["memberVersion"].toString().trim('"'))
    }
}
