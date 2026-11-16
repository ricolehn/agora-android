package org.agora.app

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.agora.app.data.AgoraRepository
import org.agora.app.data.BookingEdit
import org.agora.app.data.Bookings
import org.agora.app.data.PagedBookingHistory
import org.agora.app.data.Receipts
import org.agora.app.data.RequestDecision
import org.agora.app.data.model.Person
import org.agora.app.data.model.StandingOrder
import org.agora.app.data.model.Transaction
import org.agora.app.data.model.User
import org.agora.app.data.model.V2Member
import org.agora.app.data.model.toPerson
import org.agora.app.data.remote.AgoraApi
import org.agora.app.data.remote.ApiException
import org.agora.app.util.Dates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.time.YearMonth

/**
 * The books through API v2 (server CALDERA, 3.4.0-beta6 on): what the app asks and sends, what it makes of the answers
 * - in the shapes such a server answers - and that it goes the old way where the server answers no v2, or not all of
 * the books' resources.
 */
class ApiV2Test {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true; explicitNulls = false }

    // ---------- errors and what the server answers ----------

    @Test
    fun `errors are read in the shape of API v2, of the old API, of a starting server and as plain text`() {
        val v2 = AgoraApi.errorOf(json, 400, """{"error":{"code":"invalid","message":"Der Betrag dieser Anfrage kann so nicht gebucht werden.","field":"details.amount"}}""")
        assertEquals(400, v2.status)
        assertEquals("invalid", v2.code)
        assertEquals("details.amount", v2.field)
        assertEquals("Der Betrag dieser Anfrage kann so nicht gebucht werden.", v2.message)
        assertFalse(v2.isStarting)

        val old = AgoraApi.errorOf(json, 403, """{"error":"Finance access rights required"}""")
        assertEquals("Finance access rights required", old.message)
        assertNull(old.code)
        assertNull(old.field)

        // A server that starts or upgrades its database (CALDERA): the old API says so, v2 by its code
        assertTrue(AgoraApi.errorOf(json, 503, """{"error":"The server is starting. Please try again in a moment.","starting":true}""").isStarting)
        assertTrue(AgoraApi.errorOf(json, 503, """{"error":{"code":"unavailable","message":"The server is starting."}}""").isStarting)
        assertFalse(AgoraApi.errorOf(json, 503, """{"error":"App is in setup mode. Please configure first."}""").isStarting)

        assertEquals("Unauthorized", AgoraApi.errorOf(json, 401, "Unauthorized").message)
        // Express' own page of an unknown route: only its status, and no v2 code (an older server knows no /api/v2)
        val page = AgoraApi.errorOf(json, 404, "<!DOCTYPE html><html><body><pre>Cannot GET /api/v2/members</pre></body></html>")
        assertEquals("HTTP 404", page.message)
        assertNull(page.code)
        assertEquals("HTTP 500", AgoraApi.errorOf(json, 500, """{"unexpected":true}""").message)
    }

    @Test
    fun `the books go through API v2 only where the server offers all of their resources`() = FakeServer().use { server ->
        val repo = server.repository(json, signedIn = true)
        server.on("GET", "/api/status") { 200 to """{"setupMode":false,"ready":true,"updating":false,"apiVersions":[1,2]}""" }
        server.on("GET", "/api/v2") { 200 to """{"apiVersion":2,"resources":["me","members","payments","standing-orders","status-changes","donations","expenses","receipts","requests","finance/ledger","finance/summary","events"]}""" }
        assertTrue(runBlocking { repo.checkServer() }!!.answersV2)
        assertTrue(repo.v2.available)
        // A beta before 3.4.0-beta6: v2, but not all of the books
        server.on("GET", "/api/v2") { 200 to """{"apiVersion":2,"resources":["me","members","payments","donations","expenses","requests","finance/summary"]}""" }
        runBlocking { repo.checkServer() }
        assertFalse(repo.v2.available)
        // An older server says nothing about versions; another server is asked anew
        server.on("GET", "/api/status") { 200 to """{"setupMode":false}""" }
        runBlocking { repo.checkServer() }
        assertFalse(repo.v2.available)
        repo.api.apiV2 = true
        repo.api.baseUrl = "http://127.0.0.1:1"
        assertFalse("another server: not known yet", repo.v2.available)
    }

    @Test
    fun `the app asks for its messages in its language`() = FakeServer().use { server ->
        val api = AgoraApi(json, "test", null) { "de-DE,en-US" }.apply { baseUrl = server.base }
        server.on("GET", "/api/status") { 200 to "{}" }
        runBlocking { AgoraRepository(api).status(server.base) }
        assertEquals("de-DE,en-US", server.calls("GET", "/api/status").single().headers.entries.first { it.key.equals("Accept-Language", ignoreCase = true) }.value)
    }

    // ---------- members and their fee accounts ----------

    @Test
    fun `a member read through API v2 is the member record the screens show, fee account and all`() {
        val member = json.decodeFromString(V2Member.serializer(), MEMBER_DETAIL)
        val person = member.toPerson()
        assertEquals(listOf("anna-key", "m-anna", "v-anna", "u-anna"), listOf(person.id, person.memberId, person.version, person.uid))
        assertEquals(listOf("geringverdiener", "geringverdiener"), listOf(person.status, person.effectiveStatus))
        assertTrue(person.statusMeta.isOverdue)
        assertEquals("Zahlung überfällig", person.statusMeta.text)
        assertFalse(person.statusMeta.isActiveStandingOrder)
        assertEquals(24.5, person.overdueAmount, 0.0)
        assertEquals(-24.5, person.currentBalance, 0.0)
        assertEquals(6.0, person.monthlyFee!!, 0.0)
        assertEquals(YearMonth.of(2026, 9), Dates.paidUntilMonth(person.paidUntil))
        assertEquals(listOf("2026-01-01", "15.0", "0.0"), listOf(person.feeReset, person.feeCarriedOver.toString(), person.feeWrittenOff.toString()))
        // Payments, standing orders (by their records: a change of one names it) and the status history, oldest first
        assertEquals(listOf("pay1"), person.payments.map { it.id })
        assertEquals(80.0, person.payments.single().amount, 0.0)
        assertEquals(StandingOrder(id = "so1", amount = 15.0, startDate = "2026-07-14", endDate = null, note = "Beitrag", lastAutoPayment = "2026-11-14"), person.standingOrders.single())
        assertEquals(listOf("vollverdiener", "geringverdiener"), person.statusHistory.map { it.status })

        val ok = json.decodeFromString(V2Member.serializer(), """{"id":"m","key":"k","feeState":"standingOrder","hasActiveStandingOrder":true,"isDueSoon":true}""").toPerson()
        assertEquals("Dauerauftrag läuft", ok.statusMeta.text)
        assertTrue(ok.statusMeta.isSoonDue && ok.statusMeta.isActiveStandingOrder && !ok.statusMeta.isOverdue)
        assertNull("no fresh start", ok.feeReset)
        // A fee state the app does not know counts as in order
        assertEquals("Alles in Ordnung", json.decodeFromString(V2Member.serializer(), """{"id":"m","feeState":"somethingNew"}""").toPerson().statusMeta.text)
    }

    @Test
    fun `the member's own record and every member come through API v2`() = FakeServer().use { server ->
        val repo = server.v2Repository(json)
        server.on("GET", "/api/v2/members") { 200 to MEMBERS }
        server.on("GET", "/api/v2/members/m-anna") { 200 to MEMBER_DETAIL }
        val own = runBlocking { repo.ownPeople("u-anna") }.single()
        assertEquals(listOf("anna-key", "m-anna"), listOf(own.id, own.memberId))
        assertEquals(1, own.standingOrders.size)
        // Deleted members are no members (their payments stay in the books)
        assertEquals(listOf("Anna Alt"), runBlocking { repo.allPeople() }.map { it.name })
        assertEquals("m-anna", runBlocking { repo.person("anna-key") }!!.memberId)
    }

    // ---------- requests ----------

    @Test
    fun `requests come through API v2 with the member's key, the amount as the server reads it and the moment they were made`() = FakeServer().use { server ->
        val repo = server.v2Repository(json)
        server.on("GET", "/api/v2/members") { 200 to MEMBERS }
        server.on("GET", "/api/v2/requests") { query ->
            if (query["cursor"] == null) 200 to """{"items":[$PENDING_PAYMENT],"nextCursor":"c1"}"""
            else 200 to """{"items":[{"id":"1792238558869","type":"status","status":"approved","userId":"u-ben","memberId":"m-ben","memberName":"Ben","amountCents":null,
                "details":{"newStatus":"keinverdiener","date":"2026-03-01"},"createdAt":"2026-03-01T12:02:38.869Z","decidedAt":"2026-03-02T12:03:09.344Z","rejectionReason":null}],"nextCursor":null}"""
        }
        val requests = runBlocking { repo.allRequests() }
        assertEquals(listOf("r2", "1792238558869"), requests.map { it.id })
        val anna = requests[0]
        assertTrue(anna.v2)
        assertEquals("the key of the member's record (the app's members are the old API's)", "anna-key", anna.personId)
        assertEquals("m-anna", anna.memberId)
        assertEquals("Anna Alt", anna.personName)
        assertEquals(12.5, anna.amount!!, 0.0)
        assertEquals(java.time.Instant.parse("2026-05-09T08:30:00Z").toEpochMilli(), anna.timestamp)
        assertNull(requests[1].amount)
        assertEquals(listOf(null, "c1"), server.calls("GET", "/api/v2/requests").map { it.query["cursor"] })
        assertTrue(server.calls("GET", "/api/v2/requests").all { it.query["limit"] == "500" })
        assertEquals("1", server.calls("GET", "/api/v2/members").single().query["includeDeleted"])
    }

    @Test
    fun `a server that answers no API v2 any more is asked the old way, and remembered`() = FakeServer().use { server ->
        val repo = server.v2Repository(json)
        server.on("GET", "/api/v2/members") { 404 to "<!DOCTYPE html><pre>Cannot GET /api/v2/members</pre>" }
        server.on("GET", "/api/v2/requests") { 404 to "<!DOCTYPE html><pre>Cannot GET /api/v2/requests</pre>" }
        server.on("GET", "/api/db") { 200 to """{"1760000000000":{"id":"1760000000000","type":"payment","userId":"u1","personId":"p1","personName":"Paula","data":{"amount":"50,00","date":"2026-01-05"},"status":"pending","timestamp":1760000000000}}""" }
        val requests = runBlocking { repo.allRequests() }
        assertEquals(listOf("1760000000000"), requests.map { it.id })
        assertFalse(requests.single().v2)
        assertEquals(50.0, requests.single().amount!!, 0.0)
        assertFalse(repo.v2.available)
    }

    @Test
    fun `a member asks through API v2 - the server decides whose record it is and tells the treasury`() = FakeServer().use { server ->
        val repo = server.v2Repository(json)
        server.on("POST", "/api/v2/requests") { 201 to """{"id":"new","type":"payment","status":"pending","userId":"u1","memberId":"m1","memberName":"Paula","amountCents":5000,"details":{}}""" }
        runBlocking { repo.submitRequest(User(uid = "u1", name = "Paula"), null, "payment", buildJsonObject { put("amount", "50.00"); put("date", "2026-05-05"); put("note", "Mai") }) }
        val sent = server.calls("POST", "/api/v2/requests").single().json()
        assertEquals("payment", sent["type"]!!.jsonPrimitive.content)
        assertEquals("50.00", sent["details"]!!.jsonObject["amount"]!!.jsonPrimitive.content)
        assertEquals("true", sent["notify"]!!.jsonPrimitive.content)
        assertNull("nobody else's record: the server takes the member's own", sent["memberId"])
        assertTrue("told by the server itself", server.calls("POST", "/api/notify-admins").isEmpty())
    }

    @Test
    fun `approving decides and books in one step - once`() = FakeServer().use { server ->
        val repo = server.v2Repository(json)
        server.on("GET", "/api/v2/members") { 200 to MEMBERS }
        server.on("GET", "/api/v2/requests") { 200 to """{"items":[$PENDING_PAYMENT],"nextCursor":null}""" }
        server.on("POST", "/api/v2/requests/r2/decision") { 200 to """{"id":"r2","status":"approved"}""" }
        val request = runBlocking { repo.allRequests() }.single()
        assertEquals(RequestDecision.DONE, runBlocking { repo.approveRequest(request) })
        val decision = server.calls("POST", "/api/v2/requests/r2/decision").single().json()
        assertEquals(listOf("approved", "true"), listOf("status", "book").map { decision[it]!!.jsonPrimitive.content })
        assertTrue("no booking of its own before", server.calls("POST", "/api/v2/payments").isEmpty())

        // Decided by someone else meanwhile: the server keeps an approval's booking and takes back its own after a rejection
        server.on("POST", "/api/v2/requests/r2/decision") { 409 to """{"error":{"code":"conflict","message":"Die Anfrage wurde schon entschieden."}}""" }
        assertEquals(RequestDecision.ALREADY_DECIDED, runBlocking { repo.approveRequest(request) })
        // What cannot be booked as it is stays open for the treasury
        server.on("POST", "/api/v2/requests/r2/decision") { 400 to """{"error":{"code":"invalid","message":"…","field":"details.amount"}}""" }
        assertEquals(RequestDecision.UNREADABLE_AMOUNT, runBlocking { repo.approveRequest(request) })
        server.on("POST", "/api/v2/requests/r2/decision") { 400 to """{"error":{"code":"invalid","message":"Der Tag dieser Anfrage kann so nicht gebucht werden: bitte von Hand buchen.","field":"details.date"}}""" }
        val error = assertThrows(ApiException::class.java) { runBlocking { repo.approveRequest(request) } }
        assertEquals("Der Tag dieser Anfrage kann so nicht gebucht werden: bitte von Hand buchen.", error.message)
    }

    @Test
    fun `rejecting decides once, without a reason none is sent`() = FakeServer().use { server ->
        val repo = server.v2Repository(json)
        server.on("GET", "/api/v2/members") { 200 to MEMBERS }
        server.on("GET", "/api/v2/requests") { 200 to """{"items":[$PENDING_PAYMENT],"nextCursor":null}""" }
        val request = runBlocking { repo.allRequests() }.single()
        server.on("POST", "/api/v2/requests/r2/decision") { 200 to """{"id":"r2","status":"rejected"}""" }
        assertEquals(RequestDecision.DONE, runBlocking { repo.rejectRequest(request, " ") })
        val decision = server.calls("POST", "/api/v2/requests/r2/decision").single().json()
        assertEquals("rejected", decision["status"]!!.jsonPrimitive.content)
        assertNull("the server says it in each reader's language", decision["rejectionReason"])
        assertNull(decision["book"])
        server.on("POST", "/api/v2/requests/r2/decision") { 409 to """{"error":{"code":"conflict","message":"The request was decided already."}}""" }
        assertEquals(RequestDecision.ALREADY_DECIDED, runBlocking { repo.rejectRequest(request, "Doppelt") })
        assertEquals("Doppelt", server.calls("POST", "/api/v2/requests/r2/decision").last().json()["rejectionReason"]!!.jsonPrimitive.content)
    }

    // ---------- bookings of a member ----------

    private fun v2Person() = json.decodeFromString(V2Member.serializer(), MEMBER_DETAIL).toPerson()

    @Test
    fun `a payment is booked in whole cents on the member's version - a newer one is read and the booking goes on`() = FakeServer().use { server ->
        val repo = server.v2Repository(json)
        var tries = 0
        server.on("POST", "/api/v2/payments") {
            if (++tries == 1) 409 to """{"error":{"code":"conflict","message":"The data changed meanwhile: read it again and repeat the change."}}"""
            else 201 to """{"id":"p9","memberId":"m-anna","amountCents":2550,"date":"2026-05-05","memberVersion":"v-anna-3"}"""
        }
        server.on("GET", "/api/v2/members/m-anna") { 200 to MEMBER_DETAIL.replace("\"v-anna\"", "\"v-anna-2\"") }
        runBlocking { repo.bookPayment(v2Person(), 25.5, "2026-05-05", "Beitrag Mai", standingOrder = false) }
        val (first, second) = server.calls("POST", "/api/v2/payments").map { it.json() }
        assertEquals("v-anna", first["memberVersion"]!!.jsonPrimitive.content)
        assertEquals("v-anna-2", second["memberVersion"]!!.jsonPrimitive.content)
        assertEquals(listOf("m-anna", "2550", "2026-05-05", "Beitrag Mai"), listOf("memberId", "amountCents", "date", "description").map { second[it]!!.jsonPrimitive.content })
        assertTrue("nothing through the member's record", server.calls("POST", "/api/db/transaction").isEmpty())
    }

    @Test
    fun `standing orders and status changes go through API v2, the server books and rewrites as the web app does`() = FakeServer().use { server ->
        val repo = server.v2Repository(json)
        server.on("POST", "/api/v2/standing-orders") { 201 to """{"id":"so2","memberId":"m-anna","amountCents":1500,"startDate":"2026-11-01","memberVersion":"v-anna-2"}""" }
        server.on("PATCH", "/api/v2/standing-orders/so1") { 200 to """{"id":"so1"}""" }
        server.on("DELETE", "/api/v2/standing-orders/so1") { 204 to "" }
        server.on("POST", "/api/v2/status-changes") { 200 to MEMBER_DETAIL }
        val anna = v2Person()
        runBlocking {
            repo.bookPayment(anna, 15.0, "2026-11-01", "Beitrag", standingOrder = true)
            repo.endStandingOrder(anna, anna.standingOrders.single(), "2026-10-31")
            repo.deleteStandingOrder(anna, anna.standingOrders.single())
            repo.changeStatus(anna, "keinverdiener", "2026-12-01")
        }
        val order = server.calls("POST", "/api/v2/standing-orders").single().json()
        assertEquals(listOf("m-anna", "v-anna", "1500", "2026-11-01", "Beitrag"), listOf("memberId", "memberVersion", "amountCents", "startDate", "note").map { order[it]!!.jsonPrimitive.content })
        assertEquals(listOf("v-anna", "2026-10-31"), server.calls("PATCH", "/api/v2/standing-orders/so1").single().json().let { listOf(it["memberVersion"]!!.jsonPrimitive.content, it["endDate"]!!.jsonPrimitive.content) })
        assertEquals("v-anna", server.calls("DELETE", "/api/v2/standing-orders/so1").single().query["memberVersion"])
        val change = server.calls("POST", "/api/v2/status-changes").single().json()
        assertEquals(listOf("m-anna", "v-anna", "keinverdiener", "2026-12-01"), listOf("memberId", "memberVersion", "status", "date").map { change[it]!!.jsonPrimitive.content })
        assertTrue("nothing through the member's record", server.calls("POST", "/api/db/transaction").isEmpty())
    }

    @Test
    fun `a standing order that went meanwhile is left alone`() = FakeServer().use { server ->
        val repo = server.v2Repository(json)
        server.on("PATCH", "/api/v2/standing-orders/so1") { 409 to """{"error":{"code":"conflict","message":"changed"}}""" }
        server.on("GET", "/api/v2/members/m-anna") { 200 to MEMBER_DETAIL.replace("\"standingOrders\":[", "\"standingOrders\":[],\"was\":[") }
        val anna = v2Person()
        assertThrows(Bookings.GoneException::class.java) { runBlocking { repo.endStandingOrder(anna, anna.standingOrders.single(), "2026-10-31") } }
        assertEquals(1, server.calls("PATCH", "/api/v2/standing-orders/so1").size)
    }

    @Test
    fun `a member read through the old API is still changed through its record`() = FakeServer().use { server ->
        val repo = server.v2Repository(json)
        server.on("GET", "/api/db") { 200 to """{"value":{"id":"p1","status":"vollverdiener","memberSince":"2025-01-01","payments":[],"statusHistory":[]},"version":"1"}""" }
        server.on("POST", "/api/db/transaction") { 200 to """{"value":{},"version":"2"}""" }
        runBlocking { repo.changeStatus(Person(id = "p1"), "geringverdiener", "2026-01-01") }
        assertEquals(1, server.calls("POST", "/api/db/transaction").size)
        assertTrue(server.calls("POST", "/api/v2/status-changes").isEmpty())
    }

    // ---------- donations, expenses, receipts ----------

    @Test
    fun `donations and expenses are added with the version of their list, an expense's receipts as a list`() = FakeServer().use { server ->
        val repo = server.v2Repository(json)
        server.on("GET", "/api/v2/donations") { 200 to """{"items":[],"nextCursor":null,"listVersion":"d1"}""" }
        server.on("POST", "/api/v2/donations") { 201 to """{"id":"d","amountCents":4200,"date":"2026-05-08","donorName":"Anna","description":"","listVersion":"d2"}""" }
        server.on("GET", "/api/v2/expenses") { 200 to """{"items":[],"nextCursor":null,"listVersion":"e1"}""" }
        server.on("POST", "/api/v2/expenses") { 201 to """{"id":"e","amountCents":1999,"date":"2026-05-08","issuer":"Ben","description":"Blumen","receipts":["a.jpg","b.pdf"],"listVersion":"e2"}""" }
        runBlocking {
            repo.addDonation(42.0, "Anna", "2026-05-08", " Kollekte ")
            repo.addExpense(19.99, "Ben", "2026-05-08", "Blumen", listOf("a.jpg", "b.pdf"))
        }
        val donation = server.calls("POST", "/api/v2/donations").single().json()
        assertEquals(listOf("d1", "4200", "Anna", "Kollekte"), listOf("listVersion", "amountCents", "donorName", "description").map { donation[it]!!.jsonPrimitive.content })
        val expense = server.calls("POST", "/api/v2/expenses").single().json()
        assertEquals(listOf("e1", "1999", "Ben"), listOf("listVersion", "amountCents", "issuer").map { expense[it]!!.jsonPrimitive.content })
        assertEquals(listOf("a.jpg", "b.pdf"), expense["receipts"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertNull(expense["receipt"])
        assertEquals("the first page names the list's version", "1", server.calls("GET", "/api/v2/expenses").single().query["limit"])
    }

    @Test
    fun `receipts are uploaded and read through API v2`() = FakeServer().use { server ->
        val repo = server.v2Repository(json)
        server.on("POST", "/api/v2/receipts") { 201 to """{"name":"0f8fad5b-d9cb-469f-a165-70867728950e.pdf"}""" }
        assertEquals("0f8fad5b-d9cb-469f-a165-70867728950e.pdf", runBlocking { repo.uploadReceipt("Ben", "2026-05-08", "Rechnung.pdf", "application/pdf", byteArrayOf(1, 2, 3)) })
        assertTrue(server.calls("POST", "/api/v2/receipts").single().body.contains("name=\"receipt\"; filename=\"Rechnung.pdf\""))
        assertTrue(server.calls("POST", "/api/upload").isEmpty())
        assertTrue(Receipts.fitV2(20))
        assertFalse(Receipts.fitV2(21))
    }

    // ---------- the history ----------

    @Test
    fun `the history comes from the ledger a page at a time, searched by the server`() = FakeServer().use { server ->
        val repo = server.v2Repository(json)
        server.on("GET", "/api/v2/members") { 200 to MEMBERS }
        server.on("GET", "/api/v2/finance/ledger") { query ->
            if (query["cursor"] == null) 200 to """{"items":[$LEDGER_PAYMENT,
                {"kind":"expense","id":"x1","amountCents":1500,"date":"2026-04-01","who":"","description":"Kerzen (Von: Ben Bauer)","receipts":["k.jpg"],"memberId":null},
                {"kind":"donation","id":"d1","amountCents":5000,"date":"2026-03-01","who":"","description":"","receipts":[],"memberId":null}],"nextCursor":"n1"}"""
            else 200 to """{"items":[{"kind":"somethingNew","id":"z","amountCents":1,"date":"2026-01-01","who":"?"}],"nextCursor":null}"""
        }
        val history = runBlocking { repo.bookingHistory("  Kerzen 2026 ").also { it.loadMore() } }
        assertEquals(listOf("pay", "exp", "don"), history.items.map { it.type })
        val (payment, expense, donation) = history.items
        assertEquals(listOf("Anna Alt", "anna-key", "u-anna", "2026-10-16"), listOf(payment.who, payment.personId, payment.personUid, payment.date))
        assertEquals(80.0, payment.amount, 0.0)
        // Old expenses that kept the requester in the description: shown apart, as the web app does
        assertEquals(listOf("Ben Bauer", "Kerzen", """["k.jpg"]"""), listOf(expense.who, expense.description, expense.receipt))
        assertEquals("", donation.who)
        assertTrue(history.hasMore)
        runBlocking { history.loadMore() }
        assertEquals("a kind of booking the app does not know is left out", 3, history.items.size)
        assertFalse(history.hasMore)
        assertEquals(listOf("Kerzen 2026", "Kerzen 2026"), server.calls("GET", "/api/v2/finance/ledger").map { it.query["q"] })
        assertEquals(listOf(null, "n1"), server.calls("GET", "/api/v2/finance/ledger").map { it.query["cursor"] })
    }

    @Test
    fun `a booking of the history is changed by its record, on the version read for it - only while it is unchanged`() = FakeServer().use { server ->
        val repo = server.v2Repository(json)
        server.on("GET", "/api/v2/members") { 200 to MEMBERS }
        server.on("GET", "/api/v2/finance/ledger") { 200 to """{"items":[$LEDGER_PAYMENT],"nextCursor":null}""" }
        val tx = runBlocking { repo.bookingHistory("").also { it.loadMore() }.items.single() }
        // The history names no version: it is read first, with the payment as it is now
        server.on("GET", "/api/v2/members/m-anna") { 200 to MEMBER_DETAIL }
        var tries = 0
        server.on("PATCH", "/api/v2/payments/pay1") {
            if (++tries == 1) 409 to """{"error":{"code":"conflict","message":"changed"}}"""
            else 200 to """{"id":"pay1"}"""
        }
        runBlocking { repo.editBooking(tx, BookingEdit(80.0, "2026-10-17", "Oktober")) }
        val (first, second) = server.calls("PATCH", "/api/v2/payments/pay1").map { it.json() }
        assertEquals(listOf("v-anna", "v-anna"), listOf(first, second).map { it["memberVersion"]!!.jsonPrimitive.content })
        assertNull("the amount stayed: it is not sent again", second["amountCents"])
        assertEquals(listOf("2026-10-17", "Oktober"), listOf("date", "description").map { second[it]!!.jsonPrimitive.content })

        // Changed by someone else meanwhile: nothing is written, the history shows what is stored now
        server.on("GET", "/api/v2/members/m-anna") { 200 to MEMBER_DETAIL.replace("\"amountCents\":8000", "\"amountCents\":9000") }
        val patches = server.calls("PATCH", "/api/v2/payments/pay1").size
        assertThrows(Bookings.GoneException::class.java) { runBlocking { repo.editBooking(tx, BookingEdit(70.0, "2026-10-17", "Oktober")) } }
        assertEquals("nothing sent", patches, server.calls("PATCH", "/api/v2/payments/pay1").size)
        // Deleted meanwhile
        server.on("GET", "/api/v2/members/m-anna") { 200 to MEMBER_DETAIL }
        server.on("DELETE", "/api/v2/payments/pay1") { 404 to """{"error":{"code":"not_found","message":"There is no such payment."}}""" }
        assertThrows(Bookings.GoneException::class.java) { runBlocking { repo.deleteBooking(tx) } }
        assertEquals("v-anna", server.calls("DELETE", "/api/v2/payments/pay1").single().query["memberVersion"])
    }

    @Test
    fun `an expense of the history keeps its receipts unless they change, none as an empty list`() = FakeServer().use { server ->
        val repo = server.v2Repository(json)
        server.on("GET", "/api/v2/members") { 200 to MEMBERS }
        server.on("GET", "/api/v2/finance/ledger") { 200 to """{"items":[{"kind":"expense","id":"x1","amountCents":1500,"date":"2026-04-01","who":"Ben Bauer","description":"Kerzen","receipts":["k.jpg"]}],"nextCursor":null}""" }
        server.on("GET", "/api/v2/expenses") { 200 to """{"items":[{"id":"x1","amountCents":1500,"date":"2026-04-01","issuer":"Ben Bauer","description":"Kerzen","receipts":["k.jpg"]}],"nextCursor":null,"listVersion":"e7"}""" }
        server.on("PATCH", "/api/v2/expenses/x1") { 200 to """{"id":"x1"}""" }
        val tx = runBlocking { repo.bookingHistory("").also { it.loadMore() }.items.single() }
        runBlocking {
            repo.editBooking(tx, BookingEdit(15.0, "2026-04-01", "Kerzen", name = "Ben Bauer"))
            repo.editBooking(tx, BookingEdit(15.0, "2026-04-01", "Kerzen", name = "Ben Bauer", receipts = emptyList()))
        }
        val (kept, removed) = server.calls("PATCH", "/api/v2/expenses/x1").map { it.json() }
        assertNull(kept["receipts"])
        assertEquals(listOf("e7", "Ben Bauer", "Kerzen"), listOf("listVersion", "issuer", "description").map { kept[it]!!.jsonPrimitive.content })
        assertEquals(0, removed["receipts"]!!.jsonArray.size)
    }

    @Test
    fun `the history pages on while there is a cursor`() = runBlocking {
        val pages = mapOf(null to (listOf(Transaction(id = "a"), Transaction(id = "b")) to "1"), "1" to (listOf(Transaction(id = "c")) to null))
        val read = mutableListOf<String?>()
        val history = PagedBookingHistory { cursor -> read += cursor; pages.getValue(cursor) }
        assertTrue(history.hasMore)
        history.loadMore()
        history.loadMore()
        history.loadMore()
        assertEquals(listOf("a", "b", "c"), history.items.map { it.id })
        assertEquals(listOf(null, "1"), read)
        assertFalse(history.hasMore)
    }

    @Test
    fun `the cash balance is the summary's balance and the report reads the whole ledger`() = FakeServer().use { server ->
        val repo = server.v2Repository(json)
        server.on("GET", "/api/v2/finance/summary") { 200 to """{"today":"2026-11-16","totalsCents":{"memberPayments":120000,"donations":5050,"expenses":30001,"balance":95049},"months":[]}""" }
        assertEquals(950.49, runBlocking { repo.stats() }.totalBalance, 0.0)
        server.on("GET", "/api/v2/members") { 200 to MEMBERS }
        server.on("GET", "/api/v2/finance/ledger") { 200 to """{"items":[$LEDGER_PAYMENT,{"kind":"expense","id":"e1","amountCents":-200,"date":"2026-10-20","who":"Ben","description":"Korrektur"}],"nextCursor":null}""" }
        val all = runBlocking { repo.allTransactions() }
        assertEquals(listOf("pay1", "e1"), all.map { it.id })
        assertEquals(-2.0, all[1].amount, 0.0)
        assertTrue(server.calls("GET", "/api/v2/finance/ledger").all { it.query["limit"] == "500" })
    }

    // ---------- a stand-in for a CALDERA server ----------

    private companion object {
        val MEMBERS = """{"items":[
            {"id":"m-anna","key":"anna-key","userId":"u-anna","name":"Anna Alt","isDeleted":false,"version":"v-anna"},
            {"id":"m-ben","key":"ben-key","userId":null,"name":"Ben","isDeleted":true,"version":"v-ben"}],"nextCursor":null}"""
        const val MEMBER_DETAIL = """{"id":"m-anna","key":"anna-key","userId":"u-anna","name":"Anna Alt","status":"geringverdiener","memberSince":"2025-01-01",
            "originalMemberSince":"2025-01-01","pays":true,"isDeleted":false,"totalPaidCents":8000,"balanceCents":-2450,"overdueCents":2450,"monthlyFeeCents":600,
            "paidUntil":"2026-09-30","isOverdue":true,"currentStatus":"geringverdiener","feeState":"overdue","isDueSoon":false,"hasActiveStandingOrder":false,
            "anticipatedCents":0,"freshStart":{"day":"2026-01-01","carriedOverCents":1500,"writtenOffCents":0},"version":"v-anna",
            "payments":[{"id":"pay1","memberId":"m-anna","amountCents":8000,"date":"2026-10-16","description":"Zahlung (Genehmigt)","receipt":null,"receipts":[],"source":"manual","clientId":"1792157573926","memberVersion":"v-anna"}],
            "statusPeriods":[{"id":"sp2","status":"geringverdiener","startDate":"2026-03-01","endDate":null},{"id":"sp1","status":"vollverdiener","startDate":"2025-01-01","endDate":"2026-03-01"}],
            "standingOrders":[{"id":"so1","memberId":"m-anna","amountCents":1500,"startDate":"2026-07-14","endDate":null,"note":"Beitrag","lastBookedOn":"2026-11-14","clientId":"1786568210954","memberVersion":"v-anna"}]}"""
        const val PENDING_PAYMENT = """{"id":"r2","type":"payment","status":"pending","userId":"u-anna","memberId":"m-anna","memberName":"Anna Alt","amountCents":1250,
            "details":{"amount":"12.5","date":"2026-05-09","note":"v2"},"receipts":[],"createdAt":"2026-05-09T08:30:00.000Z"}"""
        const val LEDGER_PAYMENT = """{"kind":"memberPayment","id":"pay1","amountCents":8000,"date":"2026-10-16","who":"Anna Alt","memberId":"m-anna",
            "description":"Zahlung (Genehmigt)","receipts":[],"clientId":"1792157573926","source":"manual"}"""
    }

    private class Call(val method: String, val path: String, val query: Map<String, String>, val headers: Map<String, String>, val body: String) {
        fun json(): JsonObject = Json.parseToJsonElement(body).jsonObject
    }

    /** Answers by method and path ([on]) and records every call. */
    private class FakeServer : AutoCloseable {
        private val handlers = mutableMapOf<String, (Map<String, String>) -> Pair<Int, String>>()
        private val recorded = mutableListOf<Call>()
        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                val uri = exchange.requestURI
                val query = uri.rawQuery.orEmpty().split('&').filter { it.isNotEmpty() }.associate {
                    URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('=', ""), "UTF-8")
                }
                val headers = exchange.requestHeaders.mapValues { it.value.joinToString(",") }
                val call = Call(exchange.requestMethod, uri.path, query, headers, exchange.requestBody.readBytes().decodeToString())
                val (code, text) = synchronized(recorded) {
                    recorded += call
                    handlers["${call.method} ${call.path}"]
                }?.invoke(query) ?: (500 to """{"error":"unexpected call ${call.method} ${call.path}"}""")
                val bytes = text.toByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(code, if (code == 204) -1 else bytes.size.toLong())
                if (code != 204) exchange.responseBody.use { it.write(bytes) } else exchange.close()
            }
            start()
        }
        val base = "http://127.0.0.1:${server.address.port}"

        fun on(method: String, path: String, answer: (Map<String, String>) -> Pair<Int, String>) = synchronized(recorded) { handlers["$method $path"] = answer }
        fun calls(method: String, path: String): List<Call> = synchronized(recorded) { recorded.filter { it.method == method && it.path == path } }

        fun repository(json: Json, signedIn: Boolean = false) =
            AgoraRepository(AgoraApi(json, "test", null).apply { baseUrl = base; if (signedIn) token = "token" })
        /** A repository for a signed-in person on a server known to answer API v2. */
        fun v2Repository(json: Json) = repository(json, signedIn = true).also { it.api.apiV2 = true }

        override fun close() = server.stop(0)
    }
}
