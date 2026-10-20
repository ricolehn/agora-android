package org.agora.app.util

import org.agora.app.data.model.AgoraEvent
import java.net.URLEncoder
import java.text.Normalizer

/**
 * Addresses of events, made like the web app makes them (assets/app.js: slugify, itemSlug, eventShareUrl), so a link
 * shared from the app opens the same page: /events/<title>, the date or the id added when titles repeat, and the
 * share key (?share=) while people without an account may open the event.
 */
object EventLinks {
    private val NOT_LETTER_OR_DIGIT = Regex("[^\\p{L}\\p{N}]+")

    fun slugify(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFC).trim().replace(NOT_LETTER_OR_DIGIT, "_").trim('_').take(80)

    private fun titleSlug(event: AgoraEvent) = slugify(event.title).ifEmpty { event.id }

    /** The readable name of [event] among [events]: its title; when titles repeat, the date or the id tells them apart. */
    fun slug(event: AgoraEvent, events: List<AgoraEvent>): String {
        val base = titleSlug(event)
        val twins = events.filter { titleSlug(it) == base }
        if (twins.size <= 1) return base
        if (event.date.isNotBlank() && twins.count { it.date == event.date } == 1) return "${base}_${event.date}"
        return "$base~${event.id}"
    }

    /** The link to share: the members' address, with the share key while the switch is on. */
    fun shareUrl(baseUrl: String, event: AgoraEvent, events: List<AgoraEvent>): String {
        val url = "${baseUrl.trimEnd('/')}/events/${encode(slug(event, events))}"
        return if (event.isPublic == true && event.publicToken.isNotBlank()) "$url?share=${encode(event.publicToken)}" else url
    }

    // Like encodeURIComponent: letters, digits and - _ . ! ~ * ' ( ) stay, everything else is UTF-8 percent-encoded
    private fun encode(text: String): String = URLEncoder.encode(text, "UTF-8")
        .replace("+", "%20").replace("%21", "!").replace("%27", "'").replace("%28", "(").replace("%29", ")").replace("%7E", "~")
}
