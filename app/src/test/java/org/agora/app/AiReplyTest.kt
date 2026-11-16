package org.agora.app

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.agora.app.data.remote.AiChunk
import org.agora.app.ui.ai.AiReplyWork
import org.agora.app.ui.ai.AiStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        assertEquals("the AI is on the lookup", 1, work.currentLookup?.lookup?.id)
        work.add(AiChunk.LookupDone(1, 4, "entries", 120.0))
        assertEquals("still on it while it reads what it found", 4, work.currentLookup?.done?.count)
        work.add(AiChunk.Content("You spent **120 €** on flowers."))
        assertNull("the answer runs", work.currentLookup)
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
        assertNull(work.currentLookup)
    }
}
