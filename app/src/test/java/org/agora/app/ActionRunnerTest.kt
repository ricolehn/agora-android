package org.agora.app

import android.app.Application
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import org.agora.app.data.remote.ApiException
import org.agora.app.ui.components.ActionRunner
import org.agora.app.ui.components.LocalMessageScope
import org.agora.app.ui.components.LocalSnackbar
import org.agora.app.ui.components.rememberActionRunner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Most actions close their sheet when they are done ("Payment booked", "Request approved"): the message about it has
 * to stay when the sheet goes. It used to be shown from the sheet's own coroutines and vanished with the sheet.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class ActionRunnerTest {
    @get:Rule val compose = createComposeRule()

    private val snackbar = SnackbarHostState()
    private var sheetOpen by mutableStateOf(true)
    private var runner: ActionRunner? = null

    /** The app's window (snackbar and the scope of its messages) with a sheet that runs an action. */
    private fun showSheet() = compose.setContent {
        CompositionLocalProvider(LocalSnackbar provides snackbar, LocalMessageScope provides rememberCoroutineScope()) {
            if (sheetOpen) runner = rememberActionRunner()
        }
    }

    private val message get() = snackbar.currentSnackbarData?.visuals?.message

    @Test
    fun `the message of an action stays when the action closes its sheet`() {
        showSheet()
        compose.runOnIdle { runner!!.run("Payment booked") { sheetOpen = false } }
        compose.waitForIdle()
        assertEquals("Payment booked", message)
        assertFalse("done once the action is, not when the message is gone", runner!!.busy)
    }

    @Test
    fun `a message the sheet decides itself stays as well`() {
        showSheet()
        compose.runOnIdle { runner!!.run { sheetOpen = false; runner!!.show("This request was already decided.") } }
        compose.waitForIdle()
        assertEquals("This request was already decided.", message)
    }

    @Test
    fun `an error is shown and the action can be tried again right away`() {
        showSheet()
        var reported: String? = null
        compose.runOnIdle { runner!!.run(onError = { reported = it }) { throw ApiException(409, "Conflict") } }
        compose.waitForIdle()
        assertEquals("Conflict", message)
        assertEquals("Conflict", reported)
        assertFalse(runner!!.busy)
    }
}
