package org.agora.app

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.agora.app.data.StatusHistory
import org.agora.app.data.model.AgoraEvent
import org.agora.app.data.model.Duty
import org.agora.app.data.model.Person
import org.agora.app.data.model.Registration
import org.agora.app.data.model.User
import org.agora.app.data.model.parseAmount
import org.agora.app.ui.ai.splitThinking
import org.agora.app.ui.events.terminFilter
import org.agora.app.ui.finance.receiptFiles
import org.agora.app.util.Dates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

class LogicTest {
    @Test
    fun receiptFieldsOfAllVersionsAreRead() {
        // current format: JSON array as text; bookings from older versions: plain name or comma list
        assertEquals(listOf("A-2026-05-30-1.jpg", "A-2026-05-30-2.jpg"), receiptFiles("[\"A-2026-05-30-1.jpg\",\"A-2026-05-30-2.jpg\"]"))
        assertEquals(listOf("Angelina_Fott-2026-03-13-1.jpg"), receiptFiles("Angelina_Fott-2026-03-13-1.jpg"))
        assertEquals(listOf("a.jpg", "b.jpg"), receiptFiles(" a.jpg, b.jpg "))
        assertEquals(emptyList<String>(), receiptFiles(null))
        assertEquals(emptyList<String>(), receiptFiles("  "))
        assertEquals(emptyList<String>(), receiptFiles("[]"))
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true; explicitNulls = false }

    private val base = json.parseToJsonElement(
        """{"status":"vollverdiener","memberSince":"2024-01-01","originalMemberSince":"2024-01-01",
            "statusHistory":[{"status":"vollverdiener","startDate":"2024-01-01","endDate":"2025-06-01"},
                             {"status":"geringverdiener","startDate":"2025-06-01"}]}"""
    ).jsonObject

    /** Expected values were produced by running the PWA's applyStatusChangeToHistory (assets/app.js). */
    private fun history(obj: JsonObject) = obj.let { it["status"].toString() + " " + it["statusHistory"].toString() }

    @Test fun statusChangeInFuture() = assertEquals(
        "\"pausiert\" [{\"status\":\"vollverdiener\",\"startDate\":\"2024-01-01\",\"endDate\":\"2025-06-01\"},{\"status\":\"geringverdiener\",\"startDate\":\"2025-06-01\",\"endDate\":\"2026-10-01\"},{\"status\":\"pausiert\",\"startDate\":\"2026-10-01\"}]",
        history(StatusHistory.apply(base, "pausiert", "2026-10-01"))
    )

    @Test fun statusChangeRetroactive() = assertEquals(
        "\"keinverdiener\" [{\"status\":\"vollverdiener\",\"startDate\":\"2024-01-01\",\"endDate\":\"2025-01-01\"},{\"status\":\"keinverdiener\",\"startDate\":\"2025-01-01\"}]",
        history(StatusHistory.apply(base, "keinverdiener", "2025-01-01"))
    )

    @Test fun statusChangeAtMembershipStart() = assertEquals(
        "\"pausiert\" [{\"status\":\"pausiert\",\"startDate\":\"2024-01-01\"}]",
        history(StatusHistory.apply(base, "pausiert", "2024-01-01"))
    )

    @Test fun statusChangeWithoutHistory() {
        val person = json.parseToJsonElement("""{"status":"vollverdiener","memberSince":"2024-01-01","statusHistory":[]}""").jsonObject
        assertEquals(
            "\"geringverdiener\" [{\"status\":\"vollverdiener\",\"startDate\":\"2024-01-01\",\"endDate\":\"2024-05-01\"},{\"status\":\"geringverdiener\",\"startDate\":\"2024-05-01\"}]",
            history(StatusHistory.apply(person, "geringverdiener", "2024-05-01"))
        )
    }

    @Test(expected = Exception::class) fun statusChangeBeforeMembershipFails() {
        StatusHistory.apply(base, "pausiert", "2023-12-31")
    }

    @Test fun amounts() {
        assertEquals(1234.56, parseAmount("1.234,56")!!, 0.001)
        assertEquals(25.5, parseAmount("25,50")!!, 0.001)
        assertEquals(25.5, parseAmount("25.50")!!, 0.001)
        assertEquals(50.0, parseAmount("50 €")!!, 0.001)
        assertNull(parseAmount(""))
        assertNull(parseAmount("abc"))
    }

    @Test fun personWithLegacyValues() {
        val person = json.decodeFromString(Person.serializer(), """{"id":1717000000000,"name":"Anna","totalPaid":"1.234,50",
            "payments":[{"id":1717,"amount":"50,00","date":"2024-02-01"},{"id":"auto_1_2024-03-01","amount":50,"date":"2024-03-01","isAuto":true}],
            "_paidUntil":"2026-10-30T22:00:00.000Z","_statusMeta":{"text":"Alles in Ordnung"}}""")
        assertEquals("1717000000000", person.id)
        assertEquals(1234.5, person.totalPaid, 0.001)
        assertEquals(50.0, person.payments.first().amount, 0.001)
        assertEquals("1717", person.payments.first().id)
        // local midnight of 31 Oct in UTC+2 must still be October
        assertEquals(YearMonth.of(2026, 10), Dates.paidUntilMonth(person.paidUntil))
    }

    @Test fun thinkingBlocks() {
        val (visible, thinking) = splitThinking("<think>plan the answer</think>Hallo **Welt**")
        assertEquals("Hallo **Welt**", visible)
        assertEquals("plan the answer", thinking)
        val (openVisible, openThinking) = splitThinking("<think>still thinking")
        assertEquals("", openVisible)
        assertEquals("still thinking", openThinking)
    }

    @Test fun terminFilterRules() {
        val me = User(uid = "me", groups = listOf("g1"))
        val today = "2026-09-28"
        fun ev(vararg changes: (AgoraEvent) -> AgoraEvent) = changes.fold(AgoraEvent(id = "e", date = "2026-10-04", eventType = "event")) { e, f -> f(e) }
        assertTrue(terminFilter(ev({ it.copy(eventType = "termin") }), me, today))
        assertFalse(terminFilter(ev({ it.copy(eventType = "termin", date = "2026-09-01") }), me, today))
        assertTrue(terminFilter(ev(), me, today))
        assertFalse(terminFilter(ev({ it.copy(requiresRegistration = true) }), me, today))
        assertTrue(terminFilter(ev({ it.copy(requiresRegistration = true, myRegistration = Registration("r", "registered")) }), me, today))
        assertFalse(terminFilter(ev({ it.copy(isPinned = true) }), me, today))
        assertTrue(terminFilter(ev({ it.copy(isPinned = true, duties = listOf(Duty(id = "d", assignedUser = "me", status = "confirmed"))) }), me, today))
        assertTrue(terminFilter(ev({ it.copy(isPinned = true, duties = listOf(Duty(id = "d", assignedGroup = "g1", status = "assigned"))) }), me, today))
        // Registered highlights appear in Termine like in the PWA
        assertTrue(terminFilter(ev({ it.copy(isPinned = true, requiresRegistration = true, myRegistration = Registration("r", "registered")) }), me, today))
    }

    @Test
    fun serversMustUseHttps() {
        assertEquals("https://agora.example.org", org.agora.app.data.AppStore.normalizeUrl(" agora.example.org/ "))
        org.agora.app.data.AppStore.requireSecure("https://agora.example.org")
        org.agora.app.data.AppStore.requireSecure("http://127.0.0.1:4001")
        org.agora.app.data.AppStore.requireSecure("http://10.0.2.2:3000/")
        val error = runCatching { org.agora.app.data.AppStore.requireSecure("http://agora.example.org") }.exceptionOrNull()
        assertEquals(org.agora.app.data.AppStore.HTTPS_REQUIRED, (error as? org.agora.app.data.remote.ApiException)?.status)
    }

    private fun personWithStandingOrder() = Json.parseToJsonElement("""
        {
          "name": "Max",
          "totalPaid": 250,
          "payments": [
            {"id": 1700000000000, "amount": 100, "date": "2026-05-02", "description": "Bar"},
            {"id": "auto_so1_2026-07-01", "amount": 50, "date": "2026-07-01", "isAuto": true},
            {"id": "auto_so1_2026-08-01", "amount": 50, "date": "2026-08-03", "isAuto": true},
            {"id": "auto_so1_2026-09-01", "amount": 50, "date": "2026-09-01", "isAuto": true},
            {"id": "auto_so2_2026-09-01", "amount": 20, "date": "2026-09-01", "isAuto": true}
          ],
          "standingOrders": [
            {"id": "so1", "amount": 50, "startDate": "2026-07-01", "note": "Beitrag", "lastAutoPayment": "2026-09-01"},
            {"id": "so2", "amount": 20, "startDate": "2026-09-01", "note": ""}
          ]
        }
    """).jsonObject

    private fun JsonObject.ids(key: String) = (this[key] as kotlinx.serialization.json.JsonArray).map { (it.jsonObject["id"] as kotlinx.serialization.json.JsonPrimitive).content }

    @Test
    fun standingOrderEndsInTheFuture() {
        val today = java.time.LocalDate.of(2026, 9, 30)
        val result = org.agora.app.data.StandingOrders.end(personWithStandingOrder(), "so1", "2026-12-31", today)
        // The order stays with its end date, nothing is taken back
        assertEquals(listOf("so1", "so2"), result.ids("standingOrders"))
        val order = (result["standingOrders"] as kotlinx.serialization.json.JsonArray)[0].jsonObject
        assertEquals("2026-12-31", (order["endDate"] as kotlinx.serialization.json.JsonPrimitive).content)
        assertEquals(5, result.ids("payments").size)
        assertEquals(270.0, (result["totalPaid"] as kotlinx.serialization.json.JsonPrimitive).content.toDouble(), 0.001)
        // Ending today keeps it for today as well
        assertEquals(listOf("so1", "so2"), org.agora.app.data.StandingOrders.end(personWithStandingOrder(), "so1", "2026-09-30", today).ids("standingOrders"))
    }

    @Test
    fun standingOrderEndsRetroactively() {
        val today = java.time.LocalDate.of(2026, 9, 30)
        val result = org.agora.app.data.StandingOrders.end(personWithStandingOrder(), "so1", "2026-08-03", today)
        // Ended in the past: the order is gone, its automatic payment after the end date is taken back
        assertEquals(listOf("so2"), result.ids("standingOrders"))
        assertEquals(listOf("1700000000000", "auto_so1_2026-07-01", "auto_so1_2026-08-01", "auto_so2_2026-09-01"), result.ids("payments"))
        assertEquals(220.0, (result["totalPaid"] as kotlinx.serialization.json.JsonPrimitive).content.toDouble(), 0.001)
        assertEquals("Max", (result["name"] as kotlinx.serialization.json.JsonPrimitive).content)
        // An invalid date changes nothing
        assertEquals(personWithStandingOrder(), org.agora.app.data.StandingOrders.end(personWithStandingOrder(), "so1", "", today))
    }

    @Test
    fun standingOrderRemoveKeepsPayments() {
        val result = org.agora.app.data.StandingOrders.remove(personWithStandingOrder(), "so1")
        assertEquals(listOf("so2"), result.ids("standingOrders"))
        assertEquals(5, result.ids("payments").size)
    }
}
