package org.agora.app

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import kotlinx.coroutines.runBlocking
import org.agora.app.data.local.ThemeMode
import org.agora.app.ui.AgoraApp
import org.agora.app.ui.DeepLink
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the real app against a running Agora test server (no emulator needed), e.g. to compare it with the PWA:
 * `./gradlew testDebugUnitTest --tests '*ScreenshotTest*' -Pscreenshots=http://127.0.0.1:4001 -PscreenshotsOut=<dir>`
 * Skipped in normal test runs. Logs in as the seeded test users (password test1234).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35], qualifiers = "w390dp-h844dp-xhdpi")
class ScreenshotTest {
    private companion object {
        const val MEMBERS_TAB = "Member list"
    }

    @get:Rule val compose = createComposeRule()

    private val server = System.getProperty("agora.screenshots").orEmpty()
    private val outDir = System.getProperty("agora.screenshots.out").orEmpty().ifBlank { "build/screenshots" }

    private fun shoot(
        name: String, user: String?, route: String? = null, dark: Boolean = false, eventIndex: Int? = null, cover: Boolean = false,
        tap: List<String> = emptyList(), scroll: Boolean = false, sheet: Boolean = false,
        scrollTo: String? = null, prepare: (suspend (AppContainer) -> Unit)? = null,
        longDescription: Boolean = false
    ) {
        assumeTrue("set -Pscreenshots=<server url>", server.isNotBlank())
        val container = AppContainer(ApplicationProvider.getApplicationContext())
        var link: DeepLink? = null
        runBlocking {
            container.sessionStore.clearSession()
            container.sessionStore.setTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT)
            container.store.connect(server)
            if (user != null) {
                container.store.login("$user@test.de", "test1234")
                prepare?.invoke(container)
                container.store.refreshAll()
                val eventId = eventIndex?.let { i ->
                    container.store.data.value.events.sortedWith(compareBy({ it.date }, { it.startTime })).getOrNull(i)?.id
                }
                if (route != null || eventId != null) link = DeepLink(route ?: "events", eventId)
            }
        }
        compose.setContent { AgoraApp(container, link) }
        // Let screen-level loads (view models, images) finish on their real network threads
        repeat(16) {
            Thread.sleep(250)
            compose.waitForIdle()
        }
        // Bring an item of a long list on screen (only composed items can be tapped)
        scrollTo?.let { scrollToText(it) }
        // Open collapsed sections (e.g. duty roster) before the capture
        tap.forEach { entry ->
            // ">Label": bring the label on screen first (items further down a list)
            val text = entry.removePrefix(">")
            if (entry.startsWith(">")) scrollToText(text)
            compose.onAllNodesWithText(text).onFirst().performClick()
            repeat(4) {
                Thread.sleep(250)
                compose.waitForIdle()
            }
        }
        if (longDescription) {
            val text = (1..12).joinToString("\n\n") { "Absatz $it:Wir treffen uns im Gemeindehaus, bringen Essen mit und planen den Ablauf gemeinsam." }
            container.store.updateData { d -> d.copy(events = d.events.map { it.copy(description = text) }) }
            repeat(6) {
                Thread.sleep(250)
                compose.waitForIdle()
            }
        }
        if (cover) {
            // The seed has no cover images: borrow the server's app icon once the app's start-up refresh is done
            container.store.updateData { d -> d.copy(events = d.events.map { it.copy(imageUrl = "/assets/icon.png") }) }
            repeat(8) {
                Thread.sleep(250)
                compose.waitForIdle()
            }
        }
        if (scroll) {
            compose.onRoot().performTouchInput { swipeUp(startY = bottom * 0.8f, endY = top + bottom * 0.2f) }
            repeat(4) {
                Thread.sleep(250)
                compose.waitForIdle()
            }
        }
        // Sheets live in their own window, so capture the whole screen for them
        if (sheet) captureScreenRoboImage(File(outDir, "$name.png").path)
        else compose.onRoot().captureRoboImage(File(outDir, "$name.png").path)
        runBlocking { runCatching { container.store.logout() } }
    }

    private fun scrollToText(text: String) {
        val lists = compose.onAllNodes(hasScrollToNodeAction())
        val count = lists.fetchSemanticsNodes().size
        check((0 until count).any { i -> runCatching { lists[i].performScrollToNode(hasText(text)) }.isSuccess }) { "No list contains $text" }
        compose.waitForIdle()
    }

    @Test fun login() = shoot("login", null)
    @Test fun serverScreen() {
        // Start screen (server address)
        assumeTrue("set -Pscreenshots=<server url>", server.isNotBlank())
        val container = AppContainer(ApplicationProvider.getApplicationContext())
        runBlocking {
            container.sessionStore.clearSession()
            container.sessionStore.setTheme(ThemeMode.LIGHT)
            container.store.connect(server)
            container.store.changeServer()
        }
        compose.setContent { AgoraApp(container, null) }
        repeat(8) {
            Thread.sleep(250)
            compose.waitForIdle()
        }
        compose.onRoot().captureRoboImage(File(outDir, "server-screen.png").path)
    }
    @Test fun loginDark() = shoot("login-dark", null, dark = true)

    @Test fun ownerHome() = shoot("owner-home", "owner")
    @Test fun ownerFinances() = shoot("owner-finances", "owner", "finances")
    @Test fun ownerEvents() = shoot("owner-events", "owner", "events")
    @Test fun ownerEventsDark() = shoot("owner-events-dark", "owner", "events", dark = true)
    @Test fun ownerEventDetail() = shoot("owner-event-detail", "owner", eventIndex = 1)
    @Test fun ownerEventDetailCover() = shoot("owner-event-detail-cover", "owner", eventIndex = 2, cover = true)
    @Test fun ownerDutyRoster() = shoot("owner-duty-roster", "owner", eventIndex = 1, tap = listOf("Duty roster"))
    @Test fun ownerRegistration() = shoot("owner-registration", "owner", eventIndex = 3, tap = listOf("Attendees"))
    @Test fun maxDutyRequest() = shoot("max-duty-request", "max", eventIndex = 1, tap = listOf("Duty roster"))
    @Test fun ownerMentoring() = shoot("owner-mentoring", "owner", "mentoring")
    @Test fun ownerAi() = shoot("owner-ai", "owner", "ai")
    @Test fun ownerSettings() = shoot("owner-settings", "owner", "settings")

    @Test fun maxHome() = shoot("max-home", "max")
    @Test fun miaHome() = shoot("mia-home", "mia")
    @Test fun maxHomeMessages() = shoot("max-home-messages", "max", scroll = true)
    @Test fun maxMentorsFind() = shoot("max-mentors-find", "max", "mentoring", tap = listOf("Find mentors"))
    @Test fun maxHomeDark() = shoot("max-home-dark", "max", dark = true)
    @Test fun maxFinances() = shoot("max-finances", "max", "finances")
    @Test fun ownerFinanceMembers() = shoot("owner-finance-members", "owner", "finances", tap = listOf(MEMBERS_TAB, "Max Mitglied"))
    @Test fun maxEventDetail() = shoot("max-event-detail", "max", eventIndex = 0)
    @Test fun ownerEventDetailCoverScrolled() = shoot("owner-event-detail-cover-scrolled", "owner", eventIndex = 2, cover = true, scroll = true)

    // Pull-up sheets
    @Test fun maxNewRequestSheet() = shoot("sheet-new-request", "max", "finances", tap = listOf("New request"), sheet = true)
    @Test fun ownerBookPaymentSheet() = shoot("sheet-book-payment", "owner", "finances", tap = listOf(MEMBERS_TAB, "Max Mitglied", "Record payment"), sheet = true)
    @Test fun ownerChangeStatusSheet() = shoot("sheet-change-status", "owner", "finances", tap = listOf(MEMBERS_TAB, "Max Mitglied", "Status"), sheet = true)
    @Test fun maxDeleteAccountSheet() = shoot("sheet-delete-account", "max", "settings", scrollTo = "Delete account", tap = listOf("Delete account"), sheet = true)
    @Test fun ownerStandingOrderSheet() = shoot(
        "sheet-standing-order", "owner", "finances", tap = listOf(MEMBERS_TAB, "Max Mitglied", ">Manage"), sheet = true,
        prepare = { c -> c.repo.bookPayment(c.repo.allPeople().first { it.name == "Max Mitglied" }.id, 25.0, "2026-07-01", "Beitrag", standingOrder = true) }
    )

    /** Standing orders against the real server: end in the future, end retroactively, delete. */
    @Test fun standingOrderEndToEnd() {
        assumeTrue("set -Pscreenshots=<server url>", server.isNotBlank())
        val container = AppContainer(ApplicationProvider.getApplicationContext())
        runBlocking {
            container.sessionStore.clearSession()
            container.store.connect(server)
            container.store.login("owner@test.de", "test1234")
            val repo = container.repo
            val id = repo.allPeople().first { it.name == "Mia Mentorin" }.id
            suspend fun orders() = repo.person(id)!!.standingOrders
            val before = orders().size
            repo.bookPayment(id, 12.5, "2026-08-01", "E2E", standingOrder = true)
            val order = orders().last { it.note == "E2E" }
            org.junit.Assert.assertEquals(before + 1, orders().size)

            val future = java.time.LocalDate.now().plusMonths(2).toString()
            repo.endStandingOrder(id, order.id, future)
            org.junit.Assert.assertEquals(future, orders().first { it.id == order.id }.endDate)

            repo.endStandingOrder(id, order.id, java.time.LocalDate.now().minusDays(3).toString())
            org.junit.Assert.assertTrue("ended in the past -> removed", orders().none { it.id == order.id })

            repo.bookPayment(id, 9.0, "2026-09-01", "E2E delete", standingOrder = true)
            val second = orders().last { it.note == "E2E delete" }
            val paymentsBefore = repo.person(id)!!.payments.size
            repo.deleteStandingOrder(id, second.id)
            org.junit.Assert.assertTrue(orders().none { it.id == second.id })
            org.junit.Assert.assertEquals(paymentsBefore, repo.person(id)!!.payments.size)
            runCatching { container.store.logout() }
        }
    }
    @Test fun ownerEventDescriptionCollapsed() = shoot("owner-event-description", "owner", eventIndex = 1, longDescription = true)
    @Test fun ownerDeleteEventSheet() = shoot("sheet-delete-event", "owner", eventIndex = 1, tap = listOf("Delete"), sheet = true)
    @Test fun ownerNewRequestSheetDark() = shoot("sheet-new-request-dark", "max", "finances", dark = true, tap = listOf("New request"), sheet = true)

    // Crop step with a generated test picture (no server needed)
    // beta16 parity: cover cards, editor, start page
    @Test fun ownerEventsCovers() = shoot("owner-events-covers", "owner", "events", tap = listOf("Events"))
    @Test fun ownerEventsCoversImage() = shoot("owner-events-covers-image", "owner", "events", tap = listOf("Events"), cover = true)
    @Test fun ownerEventEdit() = shoot("owner-event-edit", "owner", eventIndex = 1, tap = listOf("Edit"))
    @Test fun ownerEventEditScrolled() = shoot("owner-event-edit-scrolled", "owner", eventIndex = 1, tap = listOf("Edit"), scroll = true)
    @Test fun ownerEventEditDark() = shoot("owner-event-edit-dark", "owner", eventIndex = 1, tap = listOf("Edit"), dark = true)

    @Test fun cropCover() = shootCrop("crop-cover", 16f / 9f, circle = false)
    @Test fun cropAvatar() = shootCrop("crop-avatar", 1f, circle = true)

    private fun shootCrop(name: String, aspect: Float, circle: Boolean) {
        assumeTrue("set -Pscreenshots=<server url>", server.isNotBlank())
        val context = ApplicationProvider.getApplicationContext<Application>()
        val bitmap = android.graphics.Bitmap.createBitmap(1200, 900, android.graphics.Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(bitmap).apply {
            drawColor(android.graphics.Color.rgb(14, 165, 233))
            val paint = android.graphics.Paint().apply { color = android.graphics.Color.rgb(16, 185, 129) }
            drawCircle(420f, 460f, 260f, paint)
            paint.color = android.graphics.Color.rgb(250, 204, 21)
            drawRect(760f, 120f, 1100f, 420f, paint)
        }
        val file = File(context.cacheDir, "crop-test.png")
        file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        compose.setContent {
            org.agora.app.ui.theme.AgoraTheme(ThemeMode.LIGHT) {
                org.agora.app.ui.components.ImageCropDialog(android.net.Uri.fromFile(file), aspect, onDismiss = {}, onCrop = {}, circle = circle)
            }
        }
        repeat(8) {
            Thread.sleep(250)
            compose.waitForIdle()
        }
        captureScreenRoboImage(File(outDir, "$name.png").path)
    }
}
