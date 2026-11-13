package org.agora.app

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.agora.app.data.AgoraRepository
import org.agora.app.data.BookingEdit
import org.agora.app.data.Bookings
import org.agora.app.data.model.Transaction
import org.agora.app.data.model.TransactionPage
import org.agora.app.data.model.parseTypedAmount
import org.agora.app.data.remote.AgoraApi
import org.agora.app.util.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress
import java.util.Locale

/**
 * Editing and deleting a booked entry of the history (treasurers): the rules ported from the web app with the cases of
 * its tests (tests/finance.test.js), and what the app sends to the server for it.
 */
class BookingsTest {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true; explicitNulls = false }

    private fun obj(text: String) = json.parseToJsonElement(text).jsonObject
    private fun list(text: String) = json.parseToJsonElement(text).jsonArray.toList()
    private fun JsonElement.field(key: String) = jsonObject[key]?.jsonPrimitive?.content

    @Test
    fun `editing or deleting hits the payment that was opened, also when the list changed meanwhile`() {
        // The treasurer opened the March payment (second in the list) ...
        val opened = obj("""{"id":"mar","amount":50,"date":"2026-03-05"}""")
        // ... and before saving, a backdated February payment was booked for the same member
        val now = list("""[{"id":"jan","amount":50,"date":"2026-01-05"},{"id":"feb","amount":40,"date":"2026-02-10"},{"id":"mar","amount":50,"date":"2026-03-05"}]""")
        assertEquals("not the February payment that is second now", 2, Bookings.findPaymentIndex(now, "mar", 1, opened))
        assertEquals("deleted meanwhile: no other payment is touched", -1, Bookings.findPaymentIndex(now.take(2), "mar", 1, opened))
        // Old payments without an id: by position, only while it is still the payment that was opened
        val legacy = list("""[{"amount":50,"date":"2026-01-05"},{"amount":50,"date":"2026-03-05"}]""")
        assertEquals(1, Bookings.findPaymentIndex(legacy, "pay_p1_1", 1, legacy[1].jsonObject))
        assertEquals(-1, Bookings.findPaymentIndex(listOf(legacy[0], obj("""{"amount":40,"date":"2026-02-10"}"""), legacy[1]), "pay_p1_1", 1, legacy[1].jsonObject))
        // ... also when the stored amount is old text like "50,00"
        val text = list("""[{"amount":"50,00","date":"2026-01-05"}]""")
        assertEquals(0, Bookings.findPaymentIndex(text, "pay_p1_0", 0, text[0].jsonObject))
        // Ids of older versions are numbers (timestamps): the history sends them as text
        assertEquals(0, Bookings.findPaymentIndex(list("""[{"id":1760000000000,"amount":50,"date":"2026-01-05"}]"""), "1760000000000", 3, null))
    }

    @Test
    fun `a payment is changed in place, the rest of the record stays and the paid total follows`() {
        val person = obj(
            """{"id":"p1","name":"Paula","status":"vollverdiener","totalPaid":100,
                "payments":[{"id":"jan","amount":50,"date":"2026-01-05","description":"Januar"},
                            {"id":"auto_so1_2026-02-05","amount":"50,00","date":"2026-02-05","description":"Dauerauftrag (Auto)","isAuto":true}]}"""
        )
        val opened = person["payments"]!!.jsonArray[1].jsonObject
        val edited = Bookings.editPayment(person, "auto_so1_2026-02-05", 1, opened, 60.0, "2026-02-06", "Februar")
        val payment = edited["payments"]!!.jsonArray[1]
        assertEquals("60.0", payment.field("amount"))
        assertEquals("2026-02-06", payment.field("date"))
        assertEquals("Februar", payment.field("description"))
        // what the edit dialog does not show stays as it was: the id and that a standing order booked it
        assertEquals("auto_so1_2026-02-05", payment.field("id"))
        assertEquals("true", payment.field("isAuto"))
        assertEquals(person["payments"]!!.jsonArray[0], edited["payments"]!!.jsonArray[0])
        assertEquals(110.0, edited["totalPaid"]!!.jsonPrimitive.content.toDouble(), 0.001)
        assertEquals("Paula", edited.field("name"))

        val removed = Bookings.removePayment(person, "jan", 0, person["payments"]!!.jsonArray[0].jsonObject)
        assertEquals(listOf("auto_so1_2026-02-05"), removed["payments"]!!.jsonArray.map { it.field("id") })
        assertEquals(50.0, removed["totalPaid"]!!.jsonPrimitive.content.toDouble(), 0.001)
    }

    @Test
    fun `a payment that was deleted meanwhile is neither changed nor deleted`() {
        val person = obj("""{"payments":[{"id":"jan","amount":50,"date":"2026-01-05"}],"totalPaid":50}""")
        val opened = obj("""{"id":"feb","amount":50,"date":"2026-02-05"}""")
        assertThrows(Bookings.GoneException::class.java) { Bookings.editPayment(person, "feb", 1, opened, 40.0, "2026-02-05", "") }
        assertThrows(Bookings.GoneException::class.java) { Bookings.removePayment(person, "feb", 1, opened) }
    }

    @Test
    fun `donations and expenses are found by their id, old ones without an id only while they are as they were opened`() {
        val donations = list(
            """[{"id":1760000000001,"amount":20,"name":"Anna","date":"2026-01-01","description":"Kollekte"},
                {"amount":30,"name":"Ben","date":"2026-01-02"},
                {"id":"1760000000003","amount":5,"name":"Cleo","date":"2026-01-03","description":""}]"""
        )
        assertEquals(0, Bookings.findEntryIndex(donations, "1760000000001", donations[0].jsonObject))
        assertEquals(2, Bookings.findEntryIndex(donations, "1760000000003", null))
        // without an id (the history names it "don_1"): the entry that is still exactly the one that was opened
        assertEquals(1, Bookings.findEntryIndex(donations, "don_1", donations[1].jsonObject))
        assertEquals(-1, Bookings.findEntryIndex(donations, "don_1", obj("""{"amount":35,"name":"Ben","date":"2026-01-02"}""")))
        assertEquals(-1, Bookings.findEntryIndex(donations, "1760000000009", obj("""{"id":"1760000000009","amount":5}""")))

        val fields = mapOf("amount" to JsonPrimitive(25.0), "date" to JsonPrimitive("2026-01-05"), "description" to JsonPrimitive("Januar"), "name" to JsonPrimitive("Anna B."))
        val edited = Bookings.editEntry(donations, "1760000000001", null, fields)
        assertEquals(listOf("25.0", "2026-01-05", "Januar", "Anna B."), listOf("amount", "date", "description", "name").map { edited[0].field(it) })
        // the id keeps its type (a number stays a number: the server tells entries apart by it)
        assertFalse(edited[0].jsonObject["id"]!!.jsonPrimitive.isString)
        assertEquals(donations.drop(1), edited.drop(1))

        assertEquals(listOf(donations[0], donations[2]), Bookings.removeEntry(donations, "don_1", donations[1].jsonObject))
        assertThrows(Bookings.GoneException::class.java) { Bookings.removeEntry(donations, "1760000000009", null) }
    }

    @Test
    fun `the history's answer carries the stored entry and a payment's place`() {
        val page = json.decodeFromString(
            TransactionPage.serializer(),
            """{"items":[
                {"amount":50,"date":"2026-03-05","description":"März","id":"mar","paymentId":"mar","who":"Paula","type":"pay","personId":"p1",
                 "personUid":"u1","paymentIndex":2,"personName":"Paula","payment":{"amount":50,"date":"2026-03-05","description":"März","id":"mar"}},
                {"amount":12.5,"date":"2026-03-01","name":"Anna","id":"don_0","who":"Anna","type":"don","paymentId":"don_0","payment":{"amount":12.5,"date":"2026-03-01","name":"Anna"}}
               ],"totalItems":2,"page":1,"perPage":50,"totalPages":1}"""
        )
        val (payment, donation) = page.items
        assertEquals(2, payment.paymentIndex)
        assertEquals("mar", (payment.payment as JsonObject).field("id"))
        assertEquals(-1, donation.paymentIndex)
        assertNull((donation.payment as JsonObject)["id"])
        // older answers without them still read
        val plain = json.decodeFromString(Transaction.serializer(), """{"id":"x","type":"exp","who":"Ben","amount":"12,50","date":"2026-01-01"}""")
        assertEquals(-1, plain.paymentIndex)
        assertNull(plain.payment)
    }

    @Test
    fun `the amount to edit is written so that it reads back as the same amount`() {
        val german = Locale.GERMANY
        assertEquals("45", Money.typed(45.0, german))
        assertEquals("45,5", Money.typed(45.5, german))
        assertEquals("45.5", Money.typed(45.5, Locale.UK))
        assertEquals("1234,56", Money.typed(1234.56, german))
        for (amount in listOf(0.5, 1.0, 12.34, 50.0, 1234.56, 1500.0, 99999.99)) {
            for (locale in listOf(german, Locale.UK)) assertEquals("$amount $locale", amount, parseTypedAmount(Money.typed(amount, locale))!!, 0.0001)
        }
    }

    // ---------- what the app sends (a stand-in for the server's /api/db) ----------

    /** Answers the app's calls in turn and records them: method, path with query, body. */
    private class FakeServer : AutoCloseable {
        private val recorded = mutableListOf<Triple<String, String, String>>()
        private val answers = ArrayDeque<Pair<Int, String>>()
        val calls: List<Triple<String, String, String>> get() = synchronized(answers) { recorded.toList() }
        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                val body = exchange.requestBody.readBytes().decodeToString()
                val (code, text) = synchronized(answers) {
                    recorded += Triple(exchange.requestMethod, exchange.requestURI.toString(), body)
                    answers.removeFirstOrNull()
                } ?: (500 to """{"error":"unexpected call"}""")
                val bytes = text.toByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(code, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            start()
        }

        fun answer(code: Int, text: String) = synchronized(answers) { answers += code to text }

        fun repository(json: Json) = AgoraRepository(AgoraApi(json, "test", null).apply { baseUrl = "http://127.0.0.1:${server.address.port}" })

        override fun close() = server.stop(0)
    }

    private fun sentBody(call: Triple<String, String, String>) = obj(call.third)

    @Test
    fun `an edited donation is saved with the version it was read with, and read and changed again when the list changed`() = FakeServer().use { server ->
        val anna = """{"id":1760000000001,"amount":20,"name":"Anna","date":"2026-01-01","description":"Kollekte"}"""
        val ben = """{"id":1760000000002,"amount":30,"name":"Ben","date":"2026-01-02","description":""}"""
        val cleo = """{"id":1760000000003,"amount":5,"name":"Cleo","date":"2026-01-03","description":""}"""
        server.answer(200, """{"value":[$anna,$ben],"version":"v1"}""")
        // someone else saved the donations in between: refused, read again
        server.answer(409, """{"error":"Conflict"}""")
        server.answer(200, """{"value":[$anna,$ben,$cleo],"version":"v2"}""")
        server.answer(200, """{"success":true}""")
        val tx = Transaction(id = "1760000000001", type = "don", who = "Anna", amount = 20.0, date = "2026-01-01", payment = obj(anna))

        runBlocking { server.repository(json).editBooking(tx, BookingEdit(25.0, "2026-01-05", "Kollekte Januar", name = "Anna B.")) }

        assertEquals(listOf("GET", "PUT", "GET", "PUT"), server.calls.map { it.first })
        assertEquals("/api/db?path=donations&raw=1", server.calls[0].second)
        assertEquals("v1", sentBody(server.calls[1])["version"]!!.jsonPrimitive.content)
        val saved = sentBody(server.calls[3])
        assertEquals("donations", saved.field("path"))
        assertEquals("v2", saved.field("version"))
        val value = saved["value"]!!.jsonArray
        assertEquals("the donation added meanwhile stays", 3, value.size)
        assertEquals(listOf("25.0", "2026-01-05", "Kollekte Januar", "Anna B."), listOf("amount", "date", "description", "name").map { value[0].field(it) })
        assertEquals(listOf(obj(ben), obj(cleo)), value.drop(1))
    }

    @Test
    fun `a deleted expense goes from the list - with the version, so the server does not keep it`() = FakeServer().use { server ->
        // The server hands out the expenses by their record key
        server.answer(200, """{"value":{"k1":{"id":"e1","amount":12,"issuer":"Ben","date":"2026-01-02","description":"Blumen","receipt":""},
                                       "k2":{"id":"e2","amount":40,"issuer":"Cleo","date":"2026-01-03","description":"Miete","receipt":"[\"a.jpg\"]"}},
                              "version":"v7"}""")
        server.answer(200, """{"success":true}""")
        val tx = Transaction(id = "e2", type = "exp", who = "Cleo", amount = 40.0, date = "2026-01-03")

        runBlocking { server.repository(json).deleteBooking(tx) }

        val saved = sentBody(server.calls[1])
        assertEquals(listOf("e1"), saved["value"]!!.jsonArray.map { it.field("id") })
        assertEquals("v7", saved.field("version"))
    }

    @Test
    fun `an edited expense writes its receipts as the web app does - a server without versions gets none`() = FakeServer().use { server ->
        val expense = """{"id":"e2","amount":40,"issuer":"Cleo","date":"2026-01-03","description":"Miete","receipt":"a.jpg, b.jpg"}"""
        server.answer(200, """{"value":[$expense],"version":null}""")
        server.answer(200, """{"success":true}""")
        server.answer(200, """{"value":[$expense],"version":null}""")
        server.answer(200, """{"success":true}""")
        val tx = Transaction(id = "e2", type = "exp", who = "Cleo", amount = 40.0, date = "2026-01-03", receipt = "a.jpg, b.jpg", payment = obj(expense))
        val repo = server.repository(json)

        runBlocking { repo.editBooking(tx, BookingEdit(45.0, "2026-01-04", "Miete Januar", name = "Cleo M.", receipts = listOf("b.jpg", "c.jpg"))) }
        val saved = sentBody(server.calls[1])
        assertFalse(saved.containsKey("version"))
        val entry = saved["value"]!!.jsonArray[0]
        assertEquals("Cleo M.", entry.field("issuer"))
        assertEquals("""["b.jpg","c.jpg"]""", entry.field("receipt"))

        // receipts left as they are stay exactly as stored
        runBlocking { repo.editBooking(tx, BookingEdit(45.0, "2026-01-04", "Miete Januar", name = "Cleo M.")) }
        assertEquals("a.jpg, b.jpg", sentBody(server.calls[3])["value"]!!.jsonArray[0].field("receipt"))
    }

    @Test
    fun `an edited payment is saved in the member's record against the version it was read with`() = FakeServer().use { server ->
        server.answer(200, """{"value":{"id":"p1","name":"Paula","totalPaid":100,"payments":[{"id":"jan","amount":50,"date":"2026-01-05","description":""},
                                                                                         {"id":"feb","amount":50,"date":"2026-02-05","description":""}]},
                              "version":"2026-02-05 10:00:00.000Z"}""")
        server.answer(200, """{"value":{},"version":"next"}""")
        val tx = Transaction(id = "feb", type = "pay", who = "Paula", personId = "p1", amount = 50.0, date = "2026-02-05", paymentIndex = 1,
            payment = obj("""{"id":"feb","amount":50,"date":"2026-02-05","description":""}"""))

        runBlocking { server.repository(json).editBooking(tx, BookingEdit(55.0, "2026-02-01", "Februar")) }

        assertEquals("/api/db?path=people%2Fp1&raw=1", server.calls[0].second)
        assertEquals("POST" to "/api/db/transaction", server.calls[1].first to server.calls[1].second)
        val saved = sentBody(server.calls[1])
        assertEquals("2026-02-05 10:00:00.000Z", saved.field("currentVersion"))
        val value = saved["value"]!!.jsonObject
        assertEquals(listOf("55.0", "2026-02-01", "Februar"), listOf("amount", "date", "description").map { value["payments"]!!.jsonArray[1].field(it) })
        assertEquals(105.0, value["totalPaid"]!!.jsonPrimitive.content.toDouble(), 0.001)
    }

    @Test
    fun `an entry deleted meanwhile is reported and nothing is written`() = FakeServer().use { server ->
        server.answer(200, """{"value":[{"id":"e1","amount":12,"issuer":"Ben","date":"2026-01-02"}],"version":"v3"}""")
        val tx = Transaction(id = "e2", type = "exp", who = "Cleo", amount = 40.0, date = "2026-01-03")

        assertThrows(Bookings.GoneException::class.java) { runBlocking { server.repository(json).editBooking(tx, BookingEdit(45.0, "2026-01-04", "", name = "Cleo")) } }
        assertEquals(listOf("GET"), server.calls.map { it.first })
        assertTrue(server.calls.none { it.first == "PUT" })
    }

}
