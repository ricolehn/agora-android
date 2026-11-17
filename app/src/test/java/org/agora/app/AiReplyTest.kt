package org.agora.app

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.agora.app.data.remote.AiChunk
import org.agora.app.ui.ai.AiReplyWork
import org.agora.app.ui.ai.AiStep
import org.agora.app.ui.ai.LiveLabel
import org.agora.app.ui.ai.TitlePacer
import org.agora.app.ui.ai.liveLabel
import org.agora.app.ui.ai.liveTitle
import org.agora.app.ui.ai.lookupStep
import org.agora.app.ui.ai.readingTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * One AI reply as the server streams it: thoughts, text and - from server CALDERA on - the lookups the server makes for
 * the AI ({ lookup } / { lookupDone }, backend/routes/ai.js), as the web app keeps them (assets/aiReply.js).
 */
class AiReplyTest {
    private fun chunks(line: String) = AiChunk.of(Json.parseToJsonElement(line).jsonObject)

    @Test
    fun `the stream's lines become text, thoughts, lookups and what they found`() {
        assertEquals(listOf(AiChunk.Content("Hallo")), chunks("""{"content":"Hallo"}"""))
        assertEquals(listOf(AiChunk.Reasoning("Hm")), chunks("""{"reasoning":"Hm"}"""))
        assertEquals(
            listOf(AiChunk.Lookup(1, "money", "Finding the expenses for flowers", "2019", "2024", "Blumen")),
            chunks("""{"lookup":{"id":1,"tool":"money","step":"Finding the expenses for flowers","from":"2019","to":"2024","about":"Blumen"}}""")
        )
        assertEquals(listOf(AiChunk.Lookup(2, "events")), chunks("""{"lookup":{"id":2,"tool":"events"}}"""))
        assertEquals(listOf(AiChunk.LookupDone(1, 12, "entries", 345.5, cut = true)), chunks("""{"lookupDone":{"id":1,"count":12,"unit":"entries","eur":345.5,"cut":true}}"""))
        assertEquals(listOf(AiChunk.LookupDone(3, error = "forbidden")), chunks("""{"lookupDone":{"id":3,"error":"forbidden"}}"""))
        // Whatever else may come one day is left alone
        assertEquals(emptyList<AiChunk>(), chunks("""{"somethingNew":{"a":1}}"""))
    }

    @Test
    fun `text written before a lookup is a note on the way - the answer is what comes after the last lookup`() {
        val work = AiReplyWork()
        work.add(AiChunk.Reasoning("I should look "))
        work.add(AiChunk.Reasoning("this up."))
        work.add(AiChunk.Content("<think>maybe flowers</think>Let me check the expenses."))
        work.add(AiChunk.Lookup(1, "expenses", from = "2019", to = "2024"))
        assertEquals(1, work.lookups)
        assertEquals("", work.content)
        assertEquals("the AI is on the lookup", LiveLabel.Lookup(AiChunk.Lookup(1, "expenses", from = "2019", to = "2024")), liveLabel(work.steps, work.content))
        work.add(AiChunk.LookupDone(1, 4, "entries", 120.0))
        assertEquals("still on it while it reads what it found", LiveLabel.Lookup(AiChunk.Lookup(1, "expenses", from = "2019", to = "2024")), liveLabel(work.steps, work.content))
        work.add(AiChunk.Content("You spent **120 €** on flowers."))
        assertEquals("You spent **120 €** on flowers.", work.content)
        assertEquals(
            listOf(
                AiStep.Thought("I should look this up."),
                AiStep.Thought("maybe flowers"),
                AiStep.Note("Let me check the expenses."),
                AiStep.Lookup(AiChunk.Lookup(1, "expenses", from = "2019", to = "2024"), AiChunk.LookupDone(1, 4, "entries", 120.0))
            ),
            work.steps
        )
    }

    @Test
    fun `what a lookup found goes to that lookup, also when two ran one after the other`() {
        val work = AiReplyWork()
        work.add(AiChunk.Lookup(1, "members"))
        work.add(AiChunk.Lookup(2, "payments"))
        work.add(AiChunk.LookupDone(1, 3, "members"))
        work.add(AiChunk.LookupDone(2, error = "failed"))
        val (first, second) = work.steps.map { it as AiStep.Lookup }
        assertEquals(3, first.done?.count)
        assertEquals("failed", second.done?.error)
        assertEquals(2, work.lookups)
    }

    @Test
    fun `a reply without lookups is as before - thoughts apart, the text the answer`() {
        val work = AiReplyWork()
        work.add(AiChunk.Reasoning("Easy."))
        work.add(AiChunk.Content("42"))
        assertEquals(listOf(AiStep.Thought("Easy.")), work.steps)
        assertEquals("42", work.content)
        assertEquals(0, work.lookups)
    }

    // The line above a reply says what the AI is on, as the web app's does: the same cases as its tests/frontend.test.js

    @Test
    fun `the live title - the newest heading of the thoughts, else the newest sentence, without filler words`() {
        assertEquals("Calculating monthly expenses", liveTitle("**Checking the expenses**\n\nI look at December.\n\n**Calculating monthly expenses**\n\nThe totals per"))
        assertEquals("Finding Nick in the members", liveTitle("## Finding Nick in the members\nHe joined"))
        assertEquals("Let me check the December expenses", liveTitle("Okay, the user asks about Christmas. Let me check the December expenses."))
        assertEquals("Jetzt rechne ich die Monatsausgaben zusammen", liveTitle("Gut, jetzt rechne ich die Monatsausgaben zusammen."))
        assertEquals("Check the totals of every year", liveTitle("- Check the expenses of December\n- Check the totals of every year"))
        assertEquals("", liveTitle("   \n "))
        // A sentence that says nothing gives no title: the one before it stays
        assertEquals("I check the books first", liveTitle("I check the books first. Hmm. Yes."))
        val long = liveTitle("Now I compare ${"every single payment of every member ".repeat(4)}since 2019.")
        assertTrue(long, long.length <= 72 && long.endsWith("…"))
        assertTrue(liveTitle("Now I compare every single payment of every member since 2019.", max = 30).length <= 30)
    }

    @Test
    fun `while the thoughts come in, only what is finished gives a title - it never writes itself letter by letter`() {
        val live = { text: String -> liveTitle(text, settled = false) }
        // A heading counts when its line is over
        assertEquals("", live("**Und"))
        assertEquals("", live("**Understanding the question**"))
        assertEquals("Understanding the question", live("**Understanding the question**\n"))
        assertEquals("Understanding the question", live("**Understanding the question**\n\nThe person wants to know what the services cost. I should"))
        assertEquals("Checking the expenses", live("**Checking the expenses**\n\nI look at December.\n\n**Calculating monthly"))
        assertEquals("", live("## Finding Nick in the mem"))
        // Bold words at the start of a line are no heading
        assertEquals("I look at the members first", live("I look at the members first.\n**Important** is the"))
        // A sentence counts when the next one has begun
        assertEquals("", live("I need the yearly"))
        assertEquals("", live("I need the yearly totals."))
        assertEquals("I need the yearly totals", live("I need the yearly totals. Then I"))
        assertEquals("I need the yearly totals", live("I need the yearly totals. Then I compare them with the budget"))
        assertEquals("Then I compare them with the budget", live("I need the yearly totals. Then I compare them with the budget. Af"))
        assertEquals("I need the yearly totals", live("I need the yearly totals.\n"))
        // A full stop inside a number or an abbreviation ends nothing
        assertEquals("", live("The candles cost 3."))
        assertEquals("The candles cost 3.50 euros, e.g. the red ones", live("The candles cost 3.50 euros, e.g. the red ones. Next"))
        // The same text as it grows: the title only changes from one finished title to the next
        val thought = "Okay, the user asks about Christmas. Let me check the December expenses. Then I add them up per year."
        val titles = LinkedHashSet<String>()
        for (length in 1..thought.length) titles += live(thought.take(length))
        assertEquals(listOf("", "The user asks about Christmas", "Let me check the December expenses"), titles.toList())
        assertEquals("complete, the last sentence counts as well", "Then I add them up per year", liveTitle(thought))
    }

    @Test
    fun `the line names the lookup it is on, then the newest thought once it has a finished title`() {
        assertEquals("Finding Nick in the members", lookupStep(AiChunk.Lookup(2, "expenses", step = "  finding   Nick in the members ")))
        assertNull("no words of the model: the screen names what it reads", lookupStep(AiChunk.Lookup(2, "expenses", from = "2019")))

        val work = AiReplyWork()
        val label = { inline: String -> liveLabel(work.steps, work.content, inline) }
        assertNull(label(""))
        work.add(AiChunk.Reasoning("**Reading the question**"))
        assertNull("the heading may still grow", label(""))
        work.add(AiChunk.Reasoning("\n\nThe person asks"))
        assertEquals(LiveLabel.Thought("Reading the question"), label(""))
        val lookup = AiChunk.Lookup(2, "expenses", "Suche die Weihnachtskosten", "2019", "2024", "Christmas")
        work.add(lookup)
        assertEquals(LiveLabel.Lookup(lookup), label(""))
        work.add(AiChunk.LookupDone(2, 3, "entries"))
        assertEquals(LiveLabel.Lookup(lookup), label(""))
        work.add(AiChunk.Reasoning("Jetzt rechne ich die Summe pro Jahr aus."))
        assertEquals("the lookup stays until the new thought has a title", LiveLabel.Lookup(lookup), label(""))
        work.add(AiChunk.Reasoning(" Dann vergleiche"))
        assertEquals(LiveLabel.Thought("Jetzt rechne ich die Summe pro Jahr aus"), label(""))
        assertEquals(LiveLabel.Thought("Prüfe die Mitglieder"), label("Prüfe die Mitglieder. Dann"))
        assertEquals("a <think> block without a finished sentence yet", LiveLabel.Thought("Jetzt rechne ich die Summe pro Jahr aus"), label("Prüfe die Mitgl"))

        // Thoughts that are over count in full: a lookup or the text of the round follows them
        val over = AiReplyWork()
        over.add(AiChunk.Reasoning("**Reading the question**"))
        over.add(AiChunk.Content("Moment."))
        assertEquals(LiveLabel.Thought("Reading the question"), liveLabel(over.steps, over.content))
        over.add(AiChunk.Lookup(1, "members"))
        over.add(AiChunk.Reasoning("Nick ist seit 2019 dabei."))
        over.add(AiChunk.Content("Nick"))
        assertEquals(LiveLabel.Thought("Nick ist seit 2019 dabei"), liveLabel(over.steps, over.content))
    }

    @Test
    fun `the titles of the line are paced - each stays long enough to be read, of those in between only the newest shows`() {
        // A short title stays about a second and a half, a long sentence longer - but none for ever
        assertEquals(1_400L, readingTime("Adding up each year"))
        assertTrue(readingTime("I will look up the expenses from 2021 to 2025 with those words") > 2_000)
        assertEquals(2_600L, readingTime("x".repeat(500)))
        // A clock of its own: [advance] lets the time pass and runs what is due
        class Timer(val at: Long, val run: () -> Unit)
        var time = 0L
        val timers = mutableListOf<Timer>()
        fun advance(ms: Long) {
            val until = time + ms
            while (true) {
                val due = timers.filter { it.at <= until }.minByOrNull { it.at } ?: break
                timers.remove(due)
                time = due.at
                due.run()
            }
            time = until
        }
        val shown = mutableListOf<Triple<Long, String, Boolean>>()
        val pacer = TitlePacer<String>(
            show = { title, tag -> shown += Triple(time, title, tag) },
            later = { ms, run -> Timer(time + ms, run).also { timers += it } },
            cancel = { timers.remove(it) },
            now = { time },
            minShown = 1_000
        )

        assertNull(pacer.title)
        pacer.want("Thinking", tag = true)
        assertEquals("the first title shows at once", listOf(Triple(0L, "Thinking", true)), shown)
        assertEquals("Thinking", pacer.title)
        assertEquals(true, pacer.tag)

        // Titles that come faster than they can be read: the first waits its turn, the ones in between are left out
        advance(300)
        pacer.want("Reading the question", tag = true)
        advance(300)
        pacer.want("Checking the expenses", tag = true)
        pacer.want("Checking the expenses", tag = true)
        advance(300)
        pacer.want("Adding up each year", tag = true)
        assertEquals(1, shown.size)
        advance(100)
        assertEquals(Triple(1_000L, "Adding up each year", true), shown[1])
        assertEquals(0, timers.size)

        // After its time a title is replaced at once; asking for the one that shows changes nothing
        advance(2_500)
        pacer.want("Adding up each year", tag = true)
        pacer.want("Writing the answer", tag = true)
        assertEquals(Triple(3_500L, "Writing the answer", true), shown[2])
        assertEquals(3, shown.size)

        // [hold]: how long the title before stays for this one
        advance(100)
        pacer.want("Finding Nick", tag = true, hold = 400)
        advance(299)
        assertEquals(3, shown.size)
        advance(1)
        assertEquals(Triple(3_900L, "Finding Nick", true), shown[3])

        // [delay]: "Thought" has to be wanted for a while - text before a lookup is no answer, and then it never shows
        advance(2_000)
        pacer.want("Thought", tag = false, delay = 700)
        advance(500)
        pacer.want("Thought", tag = false, delay = 700)
        pacer.want("Looking up the members", tag = true)
        advance(1_000)
        assertEquals(Triple(6_400L, "Looking up the members", true), shown[4])
        assertEquals(5, shown.size)
        advance(1_000)
        pacer.want("Thought", tag = false, delay = 700)
        advance(699)
        pacer.want("Thought", tag = false, delay = 700)
        assertEquals("asked again, the wait does not start anew", 5, shown.size)
        advance(1)
        assertEquals(Triple(9_100L, "Thought", false), shown[5])
        assertEquals(false, pacer.tag)

        // The same words with another tag are another title; without a hold it shows at once (the reply is complete)
        pacer.want("Thought", tag = true, hold = 0)
        assertEquals(Triple(9_100L, "Thought", true), shown[6])

        // A title that waits is forgotten when the one that shows is wanted again, or when the pacer stops
        pacer.want("Something else", tag = true)
        pacer.want("Thought", tag = true)
        advance(5_000)
        pacer.want("Too late", tag = true)
        assertEquals(8, shown.size)
        pacer.want("Never", tag = true)
        pacer.stop()
        advance(5_000)
        assertEquals(8, shown.size)
        assertEquals(0, timers.size)
    }
}
