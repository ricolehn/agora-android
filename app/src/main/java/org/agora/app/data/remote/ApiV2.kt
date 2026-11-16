package org.agora.app.data.remote

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import java.io.IOException

/**
 * One page of a list of API v2: the [items] in the list's fixed order and where the next page starts ([nextCursor],
 * null on the last one). Donations and expenses also name the version of the whole list ([listVersion]), which a
 * change of them is based on.
 */
@Serializable
data class V2Page<T>(val items: List<T> = emptyList(), val nextCursor: String? = null, val listVersion: String? = null)

/** What `GET /api/v2` names: the resources the server's v2 offers. */
@Serializable
data class V2Index(val apiVersion: Int = 2, val resources: List<String> = emptyList())

/** The server answers no API v2 (any more): what was asked is done through the old API instead. */
class V2UnavailableException : IOException("API v2 is not available")

/**
 * API v2 of the server (`/api/v2`, server CALDERA; docs/API_V2.md in the Agora repository): record ids, money in whole
 * cents, days as `YYYY-MM-DD`, every list in pages with a cursor, every error as `{ error: { code, message } }`, and
 * changes that name the version of the data they are based on - refused with 409 (`conflict`) instead of overwriting
 * what someone else changed meanwhile. Signed in as for the old API (the same token).
 *
 * v2 is stable: it only grows. Fields the app does not know are ignored (the app's [kotlinx.serialization.json.Json]),
 * and a value of a fixed choice the app does not know counts as "other" where it is read.
 */
class ApiV2(private val api: AgoraApi) {

    /** Whether the server answers v2 (as far as the app knows: see [AgoraApi.apiV2]). */
    val available: Boolean get() = api.apiV2

    /** The resources the server's v2 offers (`GET /api/v2`, signed in). */
    suspend fun resources(): List<String> = api.call("GET", BASE, V2Index.serializer()).resources

    /** Whether the server's v2 offers every resource of the books the app goes through ([BOOKS]: from 3.4.0-beta6 on). */
    suspend fun offersBooks(): Boolean = resources().containsAll(BOOKS)

    /**
     * One call of v2. Throws [V2UnavailableException] when the server answers no v2 - known before, or found out now: a
     * route of v2 the server does not know (404 without v2's error shape; v2 itself answers every unknown path with
     * `not_found`). Nothing was done then, so the caller can do it the old way.
     */
    suspend fun <T> call(
        method: String,
        path: String,
        deserializer: DeserializationStrategy<T>,
        body: JsonElement? = null,
        query: Map<String, String> = emptyMap()
    ): T {
        if (!api.apiV2) throw V2UnavailableException()
        try {
            return api.call(method, BASE + path, deserializer, body, query)
        } catch (e: ApiException) {
            if (e.status == 404 && e.code == null) {
                api.apiV2 = false
                throw V2UnavailableException()
            }
            throw e
        }
    }

    /** One page of the list at [path]: the first one, or the one after [cursor]. */
    suspend fun <T> page(path: String, item: KSerializer<T>, query: Map<String, String> = emptyMap(), cursor: String? = null, limit: Int = MAX_LIMIT): V2Page<T> =
        call("GET", path, V2Page.serializer(item), query = query + buildMap {
            put("limit", limit.toString())
            if (cursor != null) put("cursor", cursor)
        })

    /** The whole list at [path], page after page; with the version of the list as the last page names it. */
    suspend fun <T> all(path: String, item: KSerializer<T>, query: Map<String, String> = emptyMap()): V2Page<T> {
        val items = mutableListOf<T>()
        var cursor: String? = null
        var version: String?
        var pages = 0
        do {
            val page = page(path, item, query, cursor)
            items += page.items
            version = page.listVersion
            cursor = page.nextCursor
            // A cursor that does not move on would ask for ever
            if (++pages > MAX_PAGES) throw ApiException(0, "Too many pages")
        } while (cursor != null)
        return V2Page(items, null, version)
    }

    companion object {
        const val BASE = "/api/v2"
        /**
         * The resources of the books the app reads and changes through v2. The betas of CALDERA before 3.4.0-beta6
         * offered only some of them (no standing orders, status changes, receipts or history): they are asked the
         * old way, which they answer as every server does.
         */
        val BOOKS = setOf(
            "members", "payments", "standing-orders", "status-changes", "donations", "expenses", "receipts", "requests",
            "finance/ledger", "finance/summary"
        )
        /** The largest page v2 hands out (more counts as 500). */
        const val MAX_LIMIT = 500
        /** 500 x 2000 entries: far beyond any community's books. */
        private const val MAX_PAGES = 2_000
    }
}
