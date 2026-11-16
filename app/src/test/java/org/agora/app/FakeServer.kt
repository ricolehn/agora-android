package org.agora.app

import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.agora.app.data.AgoraRepository
import org.agora.app.data.remote.AgoraApi
import java.net.InetSocketAddress
import java.net.URLDecoder

/** One call the app made to the [FakeServer]. */
internal class Call(val method: String, val path: String, val query: Map<String, String>, val headers: Map<String, String>, val body: String) {
    fun json(): JsonObject = Json.parseToJsonElement(body).jsonObject
}

/** A server for the tests: answers by method and path ([on]) and records every call. */
internal class FakeServer : AutoCloseable {
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
