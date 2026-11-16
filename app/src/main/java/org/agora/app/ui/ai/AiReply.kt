package org.agora.app.ui.ai

import org.agora.app.data.remote.AiChunk

/** A step of the AI's work on one reply, as the thinking field lists them (web: assets/aiReply.js). */
sealed interface AiStep {
    /** What the model thought (reasoning sent apart, or a `<think>` block of text before a lookup). */
    data class Thought(val text: String) : AiStep

    /** What the model wrote before it looked something up: a note on the way, not the answer. */
    data class Note(val text: String) : AiStep

    /** A lookup of the server (server CALDERA) and, once it ran, what it found ([done]). */
    data class Lookup(val lookup: AiChunk.Lookup, val done: AiChunk.LookupDone? = null) : AiStep
}

/**
 * The steps of one reply, built up from what the server streams (web: createReplyWork): thoughts, text and - from
 * server CALDERA on - the lookups the server makes for the AI and what they found. Text written before a lookup was a
 * note on the way: when the lookup comes it moves into the steps (with any `<think>` block in it), and [content] is
 * the text of the round after the last lookup - the answer.
 */
class AiReplyWork {
    private val _steps = mutableListOf<AiStep>()
    val steps: List<AiStep> get() = _steps.toList()
    private val text = StringBuilder()
    val content: String get() = text.toString()
    var lookups = 0
        private set

    fun add(chunk: AiChunk) {
        when (chunk) {
            is AiChunk.Reasoning -> if (chunk.text.isNotEmpty()) {
                val last = _steps.lastOrNull()
                if (last is AiStep.Thought) _steps[_steps.lastIndex] = AiStep.Thought(last.text + chunk.text)
                else _steps += AiStep.Thought(chunk.text)
            }
            is AiChunk.Content -> text.append(chunk.text)
            is AiChunk.Lookup -> {
                val split = splitThinkingFull(text.toString())
                if (split.thinking.isNotBlank()) _steps += AiStep.Thought(split.thinking)
                if (split.answer.isNotBlank()) _steps += AiStep.Note(split.answer)
                text.setLength(0)
                lookups += 1
                _steps += AiStep.Lookup(chunk)
            }
            is AiChunk.LookupDone -> {
                // The newest lookup with this id (or without one) that has not found anything yet
                val at = _steps.indexOfLast { it is AiStep.Lookup && it.done == null && (chunk.id == null || it.lookup.id == chunk.id) }
                if (at >= 0) _steps[at] = (_steps[at] as AiStep.Lookup).copy(done = chunk)
            }
        }
    }

    /** The lookup the AI is on right now - running, or the last one while the model reads what it found - else null. */
    val currentLookup: AiStep.Lookup?
        get() = if (text.isNotBlank()) null else _steps.lastOrNull() as? AiStep.Lookup
}

/** What the server looks up, as the web names its lookups (assets/aiReply.js LOOKUP_NAMES). */
val LOOKUP_TOOLS = setOf("payments", "expenses", "donations", "money", "members", "events", "money_totals", "finance_changes")

/** What a lookup found, in the units the server counts ([AiChunk.LookupDone.unit]). */
val LOOKUP_UNITS = setOf("entries", "events", "changes", "members", "periods")
