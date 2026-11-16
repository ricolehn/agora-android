package org.agora.app

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.agora.app.data.AgoraRepository
import org.agora.app.data.BookingEdit
import org.agora.app.data.mediaStatus
import org.agora.app.data.prayers
import org.agora.app.data.songbook
import org.agora.app.data.model.FEE_STATUSES
import org.agora.app.data.model.Person
import org.agora.app.data.model.Transaction
import org.agora.app.data.model.User
import org.agora.app.data.remote.AgoraApi
import org.agora.app.util.Dates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import kotlin.math.roundToLong

/**
 * The app's own calls against a real server - a CALDERA instance whose API v2 offers everything the app uses - signed
 * in as a real account. Only runs when asked for:
 *
 * ```
 * AGORA_LIVE_EMAIL=... AGORA_LIVE_PASSWORD=... ./gradlew testDebugUnitTest --tests '*LiveServerTest*' -PliveServer=https://beta.example.org
 * ```
 *
 * What it checks depends on the account's rights. Every account: its own requests and its own fee account through v2.
 * With "view finances": every member's fee account, the history, the report and the cash balance through v2 are what
 * the old API shows. With "manage finances" it writes to the books - only against a test instance: a donation, and a
 * payment and a standing order (starting next month: nothing is booked for it) of the account's own member record,
 * each changed and removed again.
 */
class LiveServerTest {
    private val server = System.getProperty("agora.live").orEmpty().trimEnd('/')
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true; explicitNulls = false }
    private lateinit var repo: AgoraRepository
    private lateinit var user: User

    @Before
    fun signIn() {
        assumeTrue("only with -PliveServer=<server url>", server.isNotBlank())
        val email = System.getenv("AGORA_LIVE_EMAIL").orEmpty()
        val password = System.getenv("AGORA_LIVE_PASSWORD").orEmpty()
        assumeTrue("AGORA_LIVE_EMAIL and AGORA_LIVE_PASSWORD", email.isNotBlank() && password.isNotBlank())
        repo = AgoraRepository(AgoraApi(json, "AgoraAndroid/live-test", null) { "de" })
        runBlocking {
            repo.status(server)
            repo.api.token = repo.login(email, password).token
            user = repo.me()
            assertTrue("the server answers everything the app uses through API v2", repo.checkServer()!!.answersV2 && repo.v2.available)
        }
    }

    /** [block] through the old API (the same server), then back to v2. */
    private suspend fun <T> viaOldApi(block: suspend () -> T): T {
        repo.api.apiV2 = false
        return try {
            block()
        } finally {
            repo.api.apiV2 = true
        }
    }

    @Test
    fun `the calendar, the community, the media library and the fee settings through API v2 are what the old API shows`() = runBlocking {
        // Only reads: messages are not read (that would mark them read)
        val events = repo.events()
        val oldEvents = viaOldApi { repo.events() }
        assertEquals(oldEvents.map { listOf(it.id, it.title, it.date, it.startTime, it.eventType, it.status) }, events.map { listOf(it.id, it.title, it.date, it.startTime, it.eventType, it.status) })
        assertEquals(oldEvents.map { listOf(it.registeredCount, it.waitlistCount, it.myRegistration?.status, it.isFull, it.canEdit) },
            events.map { listOf(it.registeredCount, it.waitlistCount, it.myRegistration?.status, it.isFull, it.canEdit) })
        assertEquals("who sees the roster", oldEvents.map { it.canAccessDutyPlan }, events.map { it.canAccessDutyPlan })
        assertEquals(oldEvents.map { event -> event.duties.map { listOf(it.id, it.roleName, it.status, it.assignedUser, it.assignedGroup, it.requestedUser) } },
            events.map { event -> event.duties.map { listOf(it.id, it.roleName, it.status, it.assignedUser, it.assignedGroup, it.requestedUser) } })
        assertEquals("the names on the roster", oldEvents.map { event -> event.duties.map { it.assignedUserName.ifBlank { null } } },
            events.map { event -> event.duties.map { it.assignedUserName.ifBlank { null } } })
        assertEquals(oldEvents.map { it.publicToken }, events.map { it.publicToken })
        assertEquals(viaOldApi { repo.myDutyRequests() }.map { it.id to it.eventId }, repo.myDutyRequests().map { it.id to it.eventId })
        assertEquals(viaOldApi { repo.groups() }.map { it.id to it.name }.sortedBy { it.first }, repo.groups().map { it.id to it.name }.sortedBy { it.first })
        assertEquals(viaOldApi { repo.calendarFeed() }.feedUrl, repo.calendarFeed().feedUrl)

        assertEquals(viaOldApi { repo.threads() }.map { it.id to it.unreadCount }.sortedBy { it.first }, repo.threads().map { it.id to it.unreadCount }.sortedBy { it.first })
        assertEquals(viaOldApi { repo.mentors() }.map { it.id to it.user }.sortedBy { it.first }, repo.mentors().map { it.id to it.user }.sortedBy { it.first })
        assertEquals(viaOldApi { repo.myMentorProfile() }.mentor?.status, repo.myMentorProfile().mentor?.status)
        val book = repo.songbook()
        val oldBook = viaOldApi { repo.songbook() }
        assertEquals(oldBook.songs.map { it.id to it.title }.sortedBy { it.first }, book.songs.map { it.id to it.title }.sortedBy { it.first })
        assertEquals(listOf(oldBook.ccliLicense, oldBook.canManage), listOf(book.ccliLicense, book.canManage))
        val prayers = repo.prayers()
        val oldPrayers = viaOldApi { repo.prayers() }
        assertEquals(oldPrayers.prayers.map { listOf(it.id, it.isMine, it.anonymous, it.expiresAt) }, prayers.prayers.map { listOf(it.id, it.isMine, it.anonymous, it.expiresAt) })
        assertEquals(oldPrayers.canModerate, prayers.canModerate)
        assertEquals(viaOldApi { repo.mediaStatus() }, repo.mediaStatus())
        assertEquals(viaOldApi { repo.pictureVersions() }, repo.pictureVersions())

        val plan = repo.feePlan()
        val oldPlan = viaOldApi { repo.feePlan() }
        assertEquals(oldPlan?.current, plan?.current)
        assertEquals(oldPlan?.entries?.map { listOf(it.from, it.fees, it.state) }, plan?.entries?.map { listOf(it.from, it.fees, it.state) })
        assertEquals(viaOldApi { repo.feeResets() }?.resets?.map { it.date to it.state }, repo.feeResets()?.resets?.map { it.date to it.state })
        assertEquals(viaOldApi { repo.feeSettings() }.rates.filterKeys { it in FEE_STATUSES }, repo.feeSettings().rates.filterKeys { it in FEE_STATUSES })
    }

    @Test
    fun `own requests come through API v2 and name the member by its key`() = runBlocking {
        val requests = repo.allRequests()
        assertTrue(requests.all { it.v2 })
        val own = repo.ownPeople(user.userId).firstOrNull()
        if (own != null) assertTrue(requests.filter { it.memberId != null && it.userId == user.userId }.all { it.personId == own.id })
        assertEquals(requests.sortedByDescending { it.timestamp }.map { it.id }, requests.map { it.id })
        println("Live: ${requests.size} request(s) of ${user.fullName} through API v2")
    }

    private fun cents(amount: Double) = (amount * 100).roundToLong()

    /** What a fee account shows, as the screens read it - the same through both APIs. */
    private fun Person.account() = listOf(
        name, effectiveStatus, statusMeta.text, statusMeta.isOverdue, statusMeta.isSoonDue, statusMeta.isActiveStandingOrder,
        Dates.paidUntilMonth(paidUntil), cents(overdueAmount), cents(currentBalance), monthlyFee?.let(::cents),
        feeReset, cents(feeCarriedOver), cents(feeWrittenOff), pays
    )

    /** A member's history as the screens show it: payments, standing orders and status periods. */
    private fun Person.rows() = listOf(
        payments.map { listOf(it.date, cents(it.amount), it.description, it.isAuto) }.sortedBy { it.toString() },
        standingOrders.map { listOf(it.startDate, it.endDate.orEmpty(), cents(it.amount), it.note, it.lastAutoPayment.orEmpty()) }.sortedBy { it.toString() },
        statusHistory.map { listOf(it.status, it.startDate, it.endDate.orEmpty()) }.sortedBy { it.toString() }
    )

    @Test
    fun `the own fee account through API v2 is the one the old API shows`() = runBlocking {
        val viaV2 = repo.ownPeople(user.userId)
        val old = viaOldApi { repo.ownPeople(user.userId) }
        assertEquals(old.map { it.account() }, viaV2.map { it.account() })
        assertEquals(old.map { it.rows() }, viaV2.map { it.rows() })
        println("Live: own fee account ${viaV2.firstOrNull()?.account()}")
    }

    @Test
    fun `every member's fee account through API v2 is the one the old API shows`() = runBlocking {
        assumeTrue("needs the right to view the finances", user.canViewFinances)
        val viaV2 = repo.allPeople().associateBy { it.id }
        val old = viaOldApi { repo.allPeople() }.associateBy { it.id }
        assertEquals("the same members", old.keys.sorted(), viaV2.keys.sorted())
        val differing = old.keys.filter { old.getValue(it).account() != viaV2.getValue(it).account() }
            .map { "${old.getValue(it).account()}\n  v2: ${viaV2.getValue(it).account()}" }
        assertTrue("fee accounts that differ:\n" + differing.joinToString("\n"), differing.isEmpty())
        // A member read whole: payments, standing orders and status history alike
        old.keys.take(5).forEach { key ->
            assertEquals(key, viaOldApi { repo.person(key) }!!.rows(), repo.person(key)!!.rows())
        }
        println("Live: ${viaV2.size} fee accounts the same through both APIs")
    }

    /** What the old API's history and API v2's say of the same booking (v2's ids are the records', the old API's the entries'). */
    private fun Transaction.fact() = listOf(type, date.take(10), cents(amount), description.trim())

    @Test
    fun `the history, the report and the cash balance through API v2 are the books the old API shows`() = runBlocking {
        assumeTrue("needs the right to view the finances", user.canViewFinances)
        val viaV2 = repo.allTransactions()
        val stats = repo.stats()
        val viaOld = viaOldApi { repo.allTransactions() }
        val oldStats = viaOldApi { repo.stats() }
        assertEquals("as many bookings", viaOld.size, viaV2.size)
        assertEquals("the same bookings", viaOld.map { it.fact() }.sortedBy { it.toString() }, viaV2.map { it.fact() }.sortedBy { it.toString() })
        assertEquals("newest first", viaOld.map { it.date.take(10) }, viaV2.map { it.date.take(10) })
        assertEquals("the same cash balance", oldStats.totalBalance, stats.totalBalance, 0.005)
        // The first page of the history as the treasury sees it, and a search the server makes
        val history = repo.bookingHistory("").also { it.loadMore() }
        assertEquals(viaV2.take(history.items.size).map { it.fact() }, history.items.map { it.fact() })
        // As the server compares: case and accents aside ("Dück" finds "Duck")
        fun plain(text: String) = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").lowercase().replace("ß", "ss")
        val word = viaV2.firstOrNull { it.who.split(' ').first().length >= 3 }?.who?.split(' ')?.first()
        if (word != null) {
            val found = repo.bookingHistory(word).also { it.loadMore() }.items
            assertTrue("the search finds what it looks for", found.isNotEmpty() && found.all { tx -> plain(tx.who + " " + tx.description).contains(plain(word)) })
        }
        println("Live: ${viaV2.size} bookings, cash balance ${stats.totalBalance}")
    }

    @Test
    fun `a donation is booked, changed and deleted through API v2`() = runBlocking {
        assumeTrue("needs the right to manage the finances", user.canManageFinances)
        val marker = "Android live test ${System.currentTimeMillis()}"
        repo.addDonation(1.23, "Live-Test", "2026-01-02", marker)
        try {
            val booked = repo.bookingHistory(marker).also { it.loadMore() }.items.single()
            assertNotNull("read with its record", booked.booking)
            assertEquals(1.23, booked.amount, 0.0)
            repo.editBooking(booked, BookingEdit(2.34, "2026-01-03", marker, name = "Live-Test"))
            val changed = repo.bookingHistory(marker).also { it.loadMore() }.items.single()
            assertEquals(listOf("2026-01-03", 234L), listOf(changed.date, cents(changed.amount)))
            repo.deleteBooking(changed)
        } finally {
            // Whatever failed: the test's donation does not stay in the books
            repo.bookingHistory(marker).also { it.loadMore() }.items.forEach { runCatching { repo.deleteBooking(it) } }
        }
        assertTrue(repo.bookingHistory(marker).also { it.loadMore() }.items.isEmpty())
    }

    @Test
    fun `a payment and a standing order of the own member record are booked, changed and removed through API v2`() = runBlocking {
        assumeTrue("needs the right to manage the finances", user.canManageFinances)
        val key = repo.ownPeople(user.userId).firstOrNull()?.id
        assumeTrue("the account has a member record", key != null)
        suspend fun me() = repo.person(key!!)!!
        val before = me()
        val marker = "Android live test ${System.currentTimeMillis()}"
        val nextMonth = LocalDate.now().plusMonths(1).withDayOfMonth(1).toString()
        try {
            repo.bookPayment(me(), 1.11, "2026-01-02", marker, standingOrder = false)
            val payment = repo.bookingHistory(marker).also { it.loadMore() }.items.single()
            assertEquals(listOf("pay", key, 111L), listOf(payment.type, payment.personId, cents(payment.amount)))
            repo.editBooking(payment, BookingEdit(2.22, "2026-01-03", marker))
            assertEquals(222L, cents(repo.bookingHistory(marker).also { it.loadMore() }.items.single().amount))
            repo.deleteBooking(repo.bookingHistory(marker).also { it.loadMore() }.items.single())

            // Starting next month: nothing is booked for it now
            repo.bookPayment(me(), 3.33, nextMonth, marker, standingOrder = true)
            val order = me().standingOrders.single { it.note == marker }
            assertEquals(listOf(nextMonth, 333L), listOf(order.startDate, cents(order.amount)))
            val end = LocalDate.now().plusMonths(3).toString()
            repo.endStandingOrder(me(), order, end)
            assertEquals(end, me().standingOrders.single { it.note == marker }.endDate)
            repo.deleteStandingOrder(me(), me().standingOrders.single { it.note == marker })
        } finally {
            repo.bookingHistory(marker).also { it.loadMore() }.items.forEach { runCatching { repo.deleteBooking(it) } }
            me().standingOrders.filter { it.note == marker }.forEach { runCatching { repo.deleteStandingOrder(me(), it) } }
        }
        // As it was: no payment and no standing order of the test, the fee account unchanged
        val after = me()
        assertTrue(after.standingOrders.none { it.note == marker } && repo.bookingHistory(marker).also { it.loadMore() }.items.isEmpty())
        assertEquals(before.account(), after.account())
        assertEquals(before.rows(), after.rows())
    }
}
