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
}

// --- What the line above a reply says while the AI works (web: assets/aiReply.js, tested alike) ---

/** Interjections a thought often starts with ("Okay, ...", "Gut, ..."): they say nothing about what is being done. */
private val FILLER = Regex("^(?:(?:ok(?:ay)?|so|alright|all right|well|hmm+|right|also|gut|also gut|alles klar|na gut)[,.!:]\\s*)+", RegexOption.IGNORE_CASE)
private val MARKS = Regex("[*_`#>]+")
private val SPACES = Regex("(?U)\\s+")
/** A list sign before a line ("- ", "• ", "2. "). */
private val LIST_SIGN = Regex("^(?:[-•–]|\\d+[.)])\\s+")
private val TITLE_END = Regex("[\\s.:;,!?…。-]+$")
private val CUT_END = Regex("[\\s,;:.-]+$")
/** A title says something from this many characters on ("Hmm." and "Yes." do not). */
private const val MIN_TITLE = 8
/**
 * Where a sentence ends: with its line, or with . ! ? … before the next one, which begins with something else than a
 * small letter ("e.g. the" and "3.50" end nothing).
 */
private val SENTENCE_END = Regex("(?U)[.!?…]+[\"”“’')\\]]*(?=\\s+[^a-zäöüßàâçéèêëîïôûùÿñæœ\\s])|[。！？]+|\\n+")

/** Headings of the thoughts ("**Calculating the monthly expenses**", "## ..."), once their line is over - or the text. */
private fun headings(lineEnd: String) = Regex("(?:^|\\n)[ \\t]*(?:#{1,6}[ \\t]+([^\\n]+)|\\*\\*([^*\\n]{3,120})\\*\\*[ \\t]*)$lineEnd")
private val HEADINGS = headings("(?=\\n|$)")
private val HEADINGS_GROWING = headings("(?=\\n)")

private fun plain(text: String) = text.replace(MARKS, "").replace(SPACES, " ").trim()

private fun titleOf(text: String) = plain(text).replace(LIST_SIGN, "").replace(FILLER, "").replace(TITLE_END, "")

/** [text] cut at a word to at most [max] characters. */
fun shorten(text: String, max: Int = 72): String {
    if (text.length <= max) return text
    val cut = text.substring(0, max - 1)
    val space = cut.lastIndexOf(' ')
    return (if (space > max * 0.6) cut.substring(0, space) else cut).replace(CUT_END, "") + "…"
}

/**
 * The finished sentences of [text]. [settled]: nothing more comes, so what stands at the end is finished too -
 * otherwise it may still be growing and is left out.
 */
private fun finishedSentences(text: String, settled: Boolean): List<String> {
    val sentences = mutableListOf<String>()
    var start = 0
    for (end in SENTENCE_END.findAll(text)) {
        sentences += text.substring(start, end.range.last + 1)
        start = end.range.last + 1
    }
    if (settled) sentences += text.substring(start)
    return sentences
}

/**
 * What the model is thinking about, as a short title from its thoughts so far: the newest heading (summarized thoughts
 * put one above every step, "**Calculating the monthly expenses**"), else its newest sentence. Only what is finished
 * counts - a heading whose line is over, a sentence that has ended - so the title never writes itself letter by letter
 * in front of the reader. [settled]: the thoughts are complete, their last line counts as well (false while they are
 * still coming in). "" when there is nothing to say yet.
 */
fun liveTitle(thinking: String, max: Int = 72, settled: Boolean = true): String {
    val text = thinking.takeLast(3_000)
    if (text.isBlank()) return ""
    var title = ""
    for (match in (if (settled) HEADINGS else HEADINGS_GROWING).findAll(text)) title = titleOf(match.groupValues[1].ifEmpty { match.groupValues[2] })
    if (title.isEmpty()) {
        for (sentence in finishedSentences(text, settled).asReversed()) {
            val candidate = titleOf(sentence)
            if (candidate.length >= MIN_TITLE) {
                title = candidate
                break
            }
        }
    }
    if (title.isEmpty()) return ""
    return shorten(title.replaceFirstChar { it.uppercase() }, max)
}

/** A lookup's line in the model's own words, cleaned and cut - null when it gave none (the screen names what it reads). */
fun lookupStep(lookup: AiChunk.Lookup): String? =
    lookup.step?.let(::plain)?.takeIf { it.isNotEmpty() }?.let { shorten(it.replaceFirstChar { first -> first.uppercase() }, 90) }

/** What the line says while the AI works: the title of what it thinks, or the lookup it is on. */
sealed interface LiveLabel {
    data class Thought(val title: String) : LiveLabel
    data class Lookup(val lookup: AiChunk.Lookup) : LiveLabel
}

/**
 * What the reply's top line says while the AI works: the newest step it is on - a lookup (running, or the last one
 * while the model reads its results), or the title of what it is thinking - null when nothing is known yet.
 * [content]: the text of the current round; [inlineThinking]: a `<think>` block in it that is still being written.
 */
fun liveLabel(steps: List<AiStep>, content: String, inlineThinking: String = ""): LiveLabel? {
    liveTitle(inlineThinking, settled = false).takeIf { it.isNotEmpty() }?.let { return LiveLabel.Thought(it) }
    // Only the newest thoughts can still be growing - and not even they once the text of this round has begun
    val growing = if (content.isNotEmpty()) -1 else steps.lastIndex
    for (index in steps.indices.reversed()) {
        val title = when (val step = steps[index]) {
            is AiStep.Lookup -> return LiveLabel.Lookup(step.lookup)
            is AiStep.Thought -> liveTitle(step.text, settled = index != growing)
            is AiStep.Note -> liveTitle(step.text)
        }
        if (title.isNotEmpty()) return LiveLabel.Thought(title)
    }
    return null
}

/** How long a title stays at least before the next one may take its place (ms): the longer it is, the longer it takes to read. */
fun readingTime(title: String): Long = (700L + title.length * 26L).coerceIn(1_400L, 2_600L)

/**
 * Paces what the top line says: a title stays for at least [minShown] ms before the next one takes its place, and of
 * the titles that come in meanwhile only the newest is shown - a line that changes several times a second cannot be
 * read and makes the page restless. [show] puts a title on the line with its tag (whether the AI still works);
 * [now], [later] and [cancel] are the clock (the tests bring their own).
 */
class TitlePacer<T>(
    private val show: (title: T, tag: Boolean) -> Unit,
    private val later: (ms: Long, run: () -> Unit) -> Any,
    private val cancel: (timer: Any) -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
    private val minShown: Long = 1_400
) {
    private class Shown<T>(val title: T, val tag: Boolean, val at: Long)
    private class Next<T>(val title: T, val tag: Boolean, val since: Long, val due: Long, val timer: Any)

    private var shown: Shown<T>? = null
    private var next: Next<T>? = null

    /** The title on the line (null: none yet) and its tag. */
    val title: T? get() = shown?.title
    val tag: Boolean? get() = shown?.tag

    private fun drop() {
        next?.let { cancel(it.timer) }
        next = null
    }

    private fun put(title: T, tag: Boolean) {
        drop()
        shown = Shown(title, tag, now())
        show(title, tag)
    }

    /**
     * The line should say [title] with [tag]. [hold]: how long the title before it stays at least (null: [minShown]);
     * [delay]: how long this one has to be wanted before it shows - a title that is replaced within that time never
     * appears.
     */
    fun want(title: T, tag: Boolean, hold: Long? = null, delay: Long = 0) {
        val current = shown
        if (current != null && current.title == title && current.tag == tag) return drop()
        val pending = next?.takeIf { it.title == title && it.tag == tag }
        val since = pending?.since ?: now()
        val due = maxOf(if (current != null) current.at + (hold ?: minShown) else 0, since + delay)
        if (due <= now()) return put(title, tag)
        if (pending != null && pending.due == due) return
        drop()
        next = Next(title, tag, since, due, later(due - now()) { put(title, tag) })
    }

    /** Nothing more is shown (the reply is gone). */
    fun stop() = drop()
}

/** What the server looks up, as the web names its lookups (assets/aiReply.js LOOKUP_NAMES). */
val LOOKUP_TOOLS = setOf("payments", "expenses", "donations", "money", "members", "events", "money_totals", "finance_changes")

/** What a lookup found, in the units the server counts ([AiChunk.LookupDone.unit]). */
val LOOKUP_UNITS = setOf("entries", "events", "changes", "members", "periods")
