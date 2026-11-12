package org.agora.app

import kotlinx.serialization.json.Json
import org.agora.app.data.model.FeeChangeImpact
import org.agora.app.data.model.FeeEntry
import org.agora.app.data.model.FeePlan
import org.agora.app.data.model.FeeResetImpact
import org.agora.app.data.model.FeeResets
import org.agora.app.data.model.FeeSettings
import org.agora.app.data.model.Payment
import org.agora.app.data.model.Person
import org.agora.app.data.model.StatusHistoryEntry
import org.agora.app.ui.finance.FeeLogic
import org.agora.app.ui.finance.FeeLogic.MonthHint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The monthly fees over time and the fresh start (server CALDERA): the rules the app needs itself, with the cases of
 * the web app's tests (tests/finance.test.js), and the server's answers as the app reads them.
 */
class FeeLogicTest {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true; explicitNulls = false }
    private val day = "2027-01-01"

    @Test
    fun `confirming needs the very day typed again - as shown, never another one`() {
        for (typed in listOf("01.01.2027", "1.1.2027", "01/01/2027", " 01.01.2027 ", "1. 1. 2027", "2027-01-01")) {
            assertTrue(typed, FeeLogic.typedDayMatches(typed, day))
        }
        for (typed in listOf("", "01.01.2026", "02.01.2027", "01.02.2027", "1.1.27", "2027-1-1", "01012027", "2027-01-01x", "ja", null)) {
            assertFalse(typed.toString(), FeeLogic.typedDayMatches(typed, day))
        }
        // the day comes first, as the dialog shows it: 3 November is not 11 March
        assertTrue(FeeLogic.typedDayMatches("03.11.2026", "2026-11-03"))
        assertFalse(FeeLogic.typedDayMatches("11.03.2026", "2026-11-03"))
        assertFalse(FeeLogic.typedDayMatches("01.01.2027", "nonsense"))
        // what the dialog asks for is what it takes
        assertEquals("03.11.2026", FeeLogic.typedDayText("2026-11-03", german = true))
        assertEquals("03/11/2026", FeeLogic.typedDayText("2026-11-03", german = false))
        for (german in listOf(true, false)) assertTrue(FeeLogic.typedDayMatches(FeeLogic.typedDayText("2026-11-03", german), "2026-11-03"))
    }

    @Test
    fun `the member card names a fresh start as a notice - from its day on, for 60 days`() {
        assertTrue("on its day", FeeLogic.isNews("2026-11-01", LocalDate.parse("2026-11-01")))
        assertTrue("day 59", FeeLogic.isNews("2026-11-01", LocalDate.parse("2026-12-30")))
        assertFalse("after 60 days it is old news", FeeLogic.isNews("2026-11-01", LocalDate.parse("2026-12-31")))
        assertFalse(FeeLogic.isNews("2026-11-01", LocalDate.parse("2027-06-01")))
        // the server sends the day once it has come: a device whose clock is behind still shows it
        assertTrue(FeeLogic.isNews("2026-11-01", LocalDate.parse("2026-10-31")))
        for (value in listOf(null, "", "nonsense", "2026-11", "2026-02-30")) assertFalse(value.toString(), FeeLogic.isNews(value, LocalDate.parse("2026-11-01")))
    }

    @Test
    fun `a payment dated before the fresh start first pays off what was dropped then`() {
        val behind = Person(feeReset = day, feeWrittenOff = 300.0)
        assertEquals(300.0, FeeLogic.droppedFeesBefore(behind, "2026-12-20"), 0.0)
        assertEquals("from the day on a payment is credit", 0.0, FeeLogic.droppedFeesBefore(behind, day), 0.0)
        assertEquals("nothing was dropped", 0.0, FeeLogic.droppedFeesBefore(Person(feeReset = day, feeCarriedOver = 40.0), "2026-12-20"), 0.0)
        assertEquals("no fresh start", 0.0, FeeLogic.droppedFeesBefore(Person(feeWrittenOff = 300.0), "2026-12-20"), 0.0)
        assertEquals(0.0, FeeLogic.droppedFeesBefore(behind, ""), 0.0)
        assertEquals(0.0, FeeLogic.droppedFeesBefore(behind, null), 0.0)
        assertEquals(0.0, FeeLogic.droppedFeesBefore(null, "2026-12-20"), 0.0)
    }

    @Test
    fun `what happened on the day of the fresh start`() {
        assertEquals(FeeLogic.ResetOutcome.CARRIED_OVER, FeeLogic.resetOutcome(Person(feeReset = day, feeCarriedOver = 25.0)))
        assertEquals(FeeLogic.ResetOutcome.DROPPED, FeeLogic.resetOutcome(Person(feeReset = day, feeWrittenOff = 75.0)))
        assertEquals(FeeLogic.ResetOutcome.EVEN, FeeLogic.resetOutcome(Person(feeReset = day)))
    }

    @Test
    fun `the history marks the fresh start, the time before it is settled`() {
        val person = Person(
            status = "vollverdiener", memberSince = "2025-03-01", feeReset = day,
            statusHistory = listOf(StatusHistoryEntry("geringverdiener", "2025-03-01", "2026-06-01")),
            payments = listOf(Payment(amount = 50.0, date = "2026-12-20"), Payment(amount = 60.0, date = day), Payment(amount = 70.0, date = "2027-02-01"))
        )
        val lines = FeeLogic.timeline(person)
        assertEquals(listOf("2027-02-01", day, day, "2026-12-20", "2026-06-01", "2025-03-01"), lines.map { it.date })
        // on its day the fresh start comes below that day's entries: they belong to the new account
        assertEquals(60.0, lines[1].payment!!.amount, 0.0)
        assertTrue(lines[2].reset)
        assertEquals(listOf(false, false, false, true, true, true), lines.map { it.settled })
        // without a fresh start: the history as before
        val plain = FeeLogic.timeline(person.copy(feeReset = null))
        assertTrue(plain.none { it.reset || it.settled })
        assertEquals(5, plain.size)
        // nothing booked: nothing to mark either
        assertTrue(FeeLogic.timeline(Person(feeReset = day)).isEmpty())
    }

    @Test
    fun `the fee of the month comes from the server, older servers from the fee settings`() {
        val settings = FeeSettings(mapOf("vollverdiener" to 50.0, "geringverdiener" to 25.0))
        assertEquals(60.0, Person(status = "vollverdiener", monthlyFee = 60.0).monthlyFee(settings), 0.0)
        assertEquals(0.0, Person(status = "vollverdiener", monthlyFee = 0.0).monthlyFee(settings), 0.0)
        assertEquals(25.0, Person(status = "vollverdiener", currentStatus = "geringverdiener").monthlyFee(settings), 0.0)
    }

    private val plan = FeePlan(
        today = "2026-11-12", month = "2026-11",
        entries = listOf(
            FeeEntry(null, mapOf("vollverdiener" to 50.0, "geringverdiener" to 25.0, "keinverdiener" to 10.0), "past"),
            FeeEntry("2026-03", mapOf("vollverdiener" to 55.0, "geringverdiener" to 25.0, "keinverdiener" to 10.0), "current"),
            FeeEntry("2027-01", mapOf("vollverdiener" to 60.0, "geringverdiener" to 30.0, "keinverdiener" to 10.0), "planned")
        )
    )

    @Test
    fun `the fees of a month and what a change changed`() {
        assertEquals(50.0, FeeLogic.feesOfMonth(plan, "2026-02")["vollverdiener"]!!, 0.0)
        assertEquals(55.0, FeeLogic.feesOfMonth(plan, "2026-03")["vollverdiener"]!!, 0.0)
        assertEquals(55.0, FeeLogic.feesOfMonth(plan, "2026-12")["vollverdiener"]!!, 0.0)
        assertEquals(60.0, FeeLogic.feesOfMonth(plan, "2030-05")["vollverdiener"]!!, 0.0)
        assertEquals(emptyList<String>(), FeeLogic.changedStatuses(null, plan.entries[0]))
        assertEquals(listOf("vollverdiener"), FeeLogic.changedStatuses(plan.entries[0], plan.entries[1]))
        assertEquals(listOf("vollverdiener", "geringverdiener"), FeeLogic.changedStatuses(plan.entries[1], plan.entries[2]))
        assertEquals("2026-12", FeeLogic.addMonths("2026-11", 1))
        assertEquals("2027-01", FeeLogic.addMonths("2026-12", 1))
        assertEquals("2025-12", FeeLogic.addMonths("2026-01", -1))
    }

    @Test
    fun `under the month - what choosing it means`() {
        assertEquals(MonthHint.FromStart("2026-03"), FeeLogic.monthHint(plan, "2026-11", base = true, replaces = null))
        assertEquals(MonthHint.FromStart(null), FeeLogic.monthHint(plan.copy(entries = plan.entries.take(1)), "2026-11", base = true, replaces = null))
        assertEquals(MonthHint.Taken("2027-01"), FeeLogic.monthHint(plan, "2027-01", base = false, replaces = null))
        // the change that is edited may keep its month
        assertEquals(MonthHint.Later("2027-01"), FeeLogic.monthHint(plan, "2027-01", base = false, replaces = "2027-01"))
        assertEquals(MonthHint.Later("2026-12"), FeeLogic.monthHint(plan, "2026-12", base = false, replaces = null))
        assertEquals(MonthHint.Now, FeeLogic.monthHint(plan, "2026-11", base = false, replaces = null))
        assertEquals(MonthHint.Back("2026-05"), FeeLogic.monthHint(plan, "2026-05", base = false, replaces = null))
    }

    @Test
    fun `the years a change can be chosen from`() {
        val years = FeeLogic.selectableYears("2026-11", null)
        assertEquals(2011, years.first())
        assertEquals(2036, years.last())
        assertEquals(26, years.size)
        assertEquals(2001, FeeLogic.selectableYears("2026-11", "2001-04").first())
    }

    @Test
    fun `the server's answers as the app reads them`() {
        val fees = json.decodeFromString(FeePlan.serializer(), """
            {"success":true,"today":"2026-11-12","month":"2026-11","current":{"vollverdiener":55,"geringverdiener":25,"keinverdiener":10},
             "since":"2026-03","next":{"from":"2027-01","fees":{"vollverdiener":60,"geringverdiener":30,"keinverdiener":10}},
             "entries":[{"from":null,"fees":{"vollverdiener":50,"geringverdiener":25,"keinverdiener":10},"state":"past"},
                        {"from":"2026-03","fees":{"vollverdiener":55,"geringverdiener":25,"keinverdiener":10},"state":"current","setAt":1773000000000,"setByName":"Anna Admin"},
                        {"from":"2027-01","fees":{"vollverdiener":60,"geringverdiener":30,"keinverdiener":10},"state":"planned","setAt":null,"setByName":""}],
             "log":[{"action":"set","from":"2026-03","fees":{"vollverdiener":55},"movedFrom":null,"at":1773000000000,"by":"u1","byName":"Anna Admin"},
                    {"action":"removed","from":"2026-05","fees":null,"movedFrom":null,"at":1774000000000,"by":"u1","byName":"Anna Admin"}],
             "version":"0123456789abcdef"}
        """)
        assertEquals("2026-03", fees.since)
        assertEquals(60.0, fees.next!!.fees["vollverdiener"]!!, 0.0)
        assertNull(fees.entries[0].from)
        assertEquals("Anna Admin", fees.entries[1].setByName)
        assertNull(fees.entries[2].setAt)
        assertNull(fees.log[1].fees)
        assertEquals("0123456789abcdef", fees.version)
        // a member reads fewer fields
        val member = json.decodeFromString(FeePlan.serializer(), """{"today":"2026-11-12","month":"2026-11","current":{"vollverdiener":55},"since":null,"next":null,"entries":[{"from":null,"fees":{"vollverdiener":55},"state":"current"}]}""")
        assertTrue(member.log.isEmpty())
        assertNull(member.version)

        val resets = json.decodeFromString(FeeResets.serializer(), """
            {"today":"2026-11-12","resets":[{"date":"2026-11-01","state":"active","feesBefore":{"vollverdiener":50},"setAt":1762000000000,"setByName":"Anna Admin"},
                                            {"date":"2027-01-01","state":"scheduled","feesBefore":null,"setAt":null,"setByName":""}],
             "log":[{"action":"set","date":"2026-11-01","at":1762000000000,"by":"u1","byName":"Anna Admin"}]}
        """)
        assertEquals(listOf("active", "scheduled"), resets.resets.map { it.state })
        assertNull(resets.resets[1].feesBefore)

        val resetImpact = json.decodeFromString(FeeResetImpact.serializer(), """
            {"date":"2027-01-01","inEffect":false,"firstMonth":"2027-01","members":12,"behind":{"count":3,"total":250.5},"ahead":{"count":2,"total":40},
             "owesMore":{"count":0,"total":0},"feesBefore":{"vollverdiener":50},"feesWithout":{"vollverdiener":50},"repriced":false}
        """)
        assertEquals(3, resetImpact.behind.count)
        assertEquals(250.5, resetImpact.behind.total, 0.0)
        assertFalse(resetImpact.inEffect)

        val changeImpact = json.decodeFromString(FeeChangeImpact.serializer(), """
            {"from":"2026-05","remove":false,"movedFrom":null,"stretches":[{"first":"2026-05","last":null,"before":{"vollverdiener":55},"after":{"vollverdiener":60}},
                                                                     {"first":null,"last":"2026-02","before":{"vollverdiener":50},"after":{"vollverdiener":45}}],
             "inEffect":true,"members":12,"more":{"count":4,"total":120},"less":{"count":0,"total":0},"freshStart":"2026-11-01","version":"abc"}
        """)
        assertNull(changeImpact.stretches[0].last)
        assertNull(changeImpact.stretches[1].first)
        assertEquals("2026-11-01", changeImpact.freshStart)
        assertEquals("abc", changeImpact.version)

        // person records with what the server worked out, and as older servers send them
        val person = json.decodeFromString(Person.serializer(), """{"name":"Ben","_feeReset":"2026-11-01","_feeCarriedOver":0,"_feeWrittenOff":"120.5","_monthlyFee":55}""")
        assertEquals("2026-11-01", person.feeReset)
        assertEquals(120.5, person.feeWrittenOff, 0.0)
        assertEquals(55.0, person.monthlyFee!!, 0.0)
        val older = json.decodeFromString(Person.serializer(), """{"name":"Ben"}""")
        assertNull(older.feeReset)
        assertNull(older.monthlyFee)
        assertNull(json.decodeFromString(Person.serializer(), """{"_feeReset":null}""").feeReset)
    }
}
