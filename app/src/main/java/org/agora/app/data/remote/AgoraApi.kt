package org.agora.app.data.remote

import java.io.File
import okhttp3.Cache
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.serializer
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Error returned by the Agora backend (message is usually German and user-presentable). */
class ApiException(val status: Int, message: String) : IOException(message)

sealed interface AiChunk {
    data class Content(val text: String) : AiChunk
    data class Reasoning(val text: String) : AiChunk
}

/**
 * Thin OkHttp wrapper around the Agora REST API. Auth is a long-lived PocketBase JWT sent as Bearer token.
 */
@OptIn(InternalCoroutinesApi::class)
class AgoraApi(val json: Json, userAgent: String, cacheDir: File? = null) {

    @Volatile var baseUrl: String = ""
    @Volatile var token: String? = null

    private val unauthorizedEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** Fires when the server rejects our token (password changed elsewhere, account deleted...). */
    val unauthorized: SharedFlow<Unit> = unauthorizedEvents

    val http: OkHttpClient = OkHttpClient.Builder()
        // Private HTTP cache: unchanged API answers come back as "304 Not Modified" (validated by ETag), and
        // profile pictures (or "no picture") are reused for the few minutes the server allows
        .apply { if (cacheDir != null) cache(Cache(File(cacheDir, "http"), HTTP_CACHE_BYTES)) }
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request()
            val builder = request.newBuilder().header("User-Agent", userAgent)
            val currentToken = token
            // Only send the token to our own server (event covers may be external URLs)
            if (currentToken != null && request.header("Authorization") == null && isOwnHost(request.url)) {
                builder.header("Authorization", "Bearer $currentToken")
            }
            chain.proceed(builder.build())
        }
        .build()

    /** No read timeout: live-update stream and AI answers stay open for a long time. */
    private val streamHttp: OkHttpClient = http.newBuilder().cache(null).readTimeout(0, TimeUnit.MILLISECONDS).build()

    /** Drops cached answers, e.g. when another user signs in on this device. */
    fun clearHttpCache() {
        runCatching { http.cache?.evictAll() }
    }

    private fun isOwnHost(url: HttpUrl): Boolean {
        val base = baseUrl.toHttpUrlOrNull() ?: return false
        return base.host == url.host && base.port == url.port
    }

    fun url(path: String, query: Map<String, String> = emptyMap()): HttpUrl {
        val base = (baseUrl.trimEnd('/') + path).toHttpUrlOrNull() ?: throw ApiException(0, "Ungültige Server-Adresse")
        return base.newBuilder().apply { query.forEach { (k, v) -> addQueryParameter(k, v) } }.build()
    }

    /** Resolves relative asset paths like `/api/events/images/x.jpg` against the server. */
    fun absolute(pathOrUrl: String): String =
        if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")) pathOrUrl else baseUrl.trimEnd('/') + "/" + pathOrUrl.trimStart('/')

    // ---------- plain JSON calls ----------

    suspend fun <T> call(
        method: String,
        path: String,
        deserializer: DeserializationStrategy<T>,
        body: JsonElement? = null,
        query: Map<String, String> = emptyMap(),
        reportUnauthorized: Boolean = true
    ): T {
        val requestBody = body?.toString()?.toRequestBody(JSON_MEDIA)
            ?: if (method == "GET" || method == "DELETE") null else "{}".toRequestBody(JSON_MEDIA)
        val request = Request.Builder().url(url(path, query)).method(method, requestBody).build()
        // Reading the body and decoding JSON must not run on the caller's thread: screens call this from the main
        // thread, where it blocked frames (and can throw NetworkOnMainThreadException on plain HTTP/1.1)
        return withContext(Dispatchers.IO) { decode(deserializer, execute(request, reportUnauthorized)) }
    }

    suspend inline fun <reified T> get(path: String, query: Map<String, String> = emptyMap()): T =
        call("GET", path, serializer<T>(), query = query)

    suspend inline fun <reified T> post(path: String, body: JsonElement? = null): T = call("POST", path, serializer<T>(), body)
    suspend inline fun <reified T> put(path: String, body: JsonElement): T = call("PUT", path, serializer<T>(), body)
    suspend inline fun <reified T> patch(path: String, body: JsonElement): T = call("PATCH", path, serializer<T>(), body)
    suspend inline fun <reified T> delete(path: String, body: JsonElement? = null): T = call("DELETE", path, serializer<T>(), body)

    suspend fun getText(path: String): String =
        withContext(Dispatchers.IO) { execute(Request.Builder().url(url(path)).get().build(), reportUnauthorized = true) }

    // ---------- uploads ----------

    suspend fun <T> upload(path: String, deserializer: DeserializationStrategy<T>, build: MultipartBody.Builder.() -> Unit): T {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM).apply(build).build()
        val request = Request.Builder().url(url(path)).post(body).build()
        return decode(deserializer, execute(request, reportUnauthorized = true))
    }

    // ---------- streams ----------

    /**
     * Emits the changed area ("all", "events", "mentoring") per `data_update` server event; reconnects with
     * backoff until the collector stops. Servers before 3.0.0-beta15 send no scope: that counts as "all".
     */
    fun liveUpdates(): Flow<String> = flow {
        var backoff = 2_000L
        while (currentCoroutineContext().isActive) {
            try {
                val request = Request.Builder().url(url("/api/stream")).header("Accept", "text/event-stream").build()
                val call = streamHttp.newCall(request)
                // Blocking reads ignore coroutine cancellation: cancel the call itself
                val cancelHandle = currentCoroutineContext().job.invokeOnCompletion(onCancelling = true, invokeImmediately = true) { call.cancel() }
                try { call.awaitResponse().use { response ->
                    if (response.code == 401) {
                        unauthorizedEvents.tryEmit(Unit)
                        return@flow
                    }
                    if (!response.isSuccessful) throw ApiException(response.code, "stream ${response.code}")
                    backoff = 2_000L
                    val source = response.body?.source() ?: return@use
                    var isUpdate = false
                    while (currentCoroutineContext().isActive) {
                        val line = source.readUtf8Line() ?: break
                        when {
                            line.startsWith("event:") -> isUpdate = line.contains("data_update")
                            line.startsWith("data:") && isUpdate -> {
                                emit(updateScope(line.removePrefix("data:")))
                                isUpdate = false
                            }
                        }
                    }
                } } finally {
                    cancelHandle.dispose()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // network hiccup or proxy closing the idle connection: retry below
            }
            delay(backoff)
            backoff = (backoff * 2).coerceAtMost(60_000L)
        }
    }.flowOn(Dispatchers.IO)

    /** Streams an AI answer (`data: {"content"|"reasoning"}` lines ending with `data: [DONE]`). */
    fun aiChat(body: JsonElement): Flow<AiChunk> = flow {
        val request = Request.Builder().url(url("/api/ai/chat"))
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .header("Accept", "text/event-stream")
            .build()
        val call = streamHttp.newCall(request)
        val cancelHandle = currentCoroutineContext().job.invokeOnCompletion(onCancelling = true, invokeImmediately = true) { call.cancel() }
        try { call.awaitResponse().use { response ->
            if (!response.isSuccessful) throw errorFrom(response.code, response.body?.string().orEmpty())
            val source = response.body?.source() ?: return@use
            while (true) {
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith("data:")) continue
                val payload = line.removePrefix("data:").trim()
                if (payload == "[DONE]") break
                val obj = runCatching { json.parseToJsonElement(payload).jsonObject }.getOrNull() ?: continue
                (obj["content"] as? JsonPrimitive)?.contentOrNull?.let { emit(AiChunk.Content(it)) }
                (obj["reasoning"] as? JsonPrimitive)?.contentOrNull?.let { emit(AiChunk.Reasoning(it)) }
                (obj["error"] as? JsonPrimitive)?.contentOrNull?.let { throw ApiException(response.code, it) }
            }
        } } finally {
            cancelHandle.dispose()
        }
    }.flowOn(Dispatchers.IO)

    // ---------- internals ----------

    private fun updateScope(payload: String): String = runCatching {
        ((json.parseToJsonElement(payload.trim()) as? JsonObject)?.get("scope") as? JsonPrimitive)?.contentOrNull
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: "all"

    private suspend fun execute(request: Request, reportUnauthorized: Boolean): String = withContext(Dispatchers.IO) {
        val response = try {
            http.newCall(request).awaitResponse()
        } catch (e: IOException) {
            throw ApiException(0, e.message ?: "Netzwerkfehler")
        }
        response.use {
            val text = try {
                it.body?.string().orEmpty()
            } catch (e: IOException) {
                throw ApiException(0, e.message ?: "Netzwerkfehler")
            }
            if (!it.isSuccessful) {
                if (it.code == 401 && reportUnauthorized) unauthorizedEvents.tryEmit(Unit)
                throw errorFrom(it.code, text)
            }
            text
        }
    }

    private fun <T> decode(deserializer: DeserializationStrategy<T>, text: String): T =
        json.decodeFromString(deserializer, text.ifBlank { "{}" })

    /** Route handlers answer `{error}` JSON, the auth middleware answers plain text. */
    private fun errorFrom(code: Int, text: String): ApiException {
        val fromJson = runCatching { (json.parseToJsonElement(text) as? JsonObject)?.get("error") as? JsonPrimitive }
            .getOrNull()?.contentOrNull
        val message = fromJson ?: text.trim().take(200).takeIf { it.isNotEmpty() && !it.startsWith("<") } ?: "HTTP $code"
        return ApiException(code, message)
    }

    companion object {
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        private const val HTTP_CACHE_BYTES = 20L * 1024 * 1024

        fun fileBody(bytes: ByteArray, mime: String): RequestBody = bytes.toRequestBody(mime.toMediaType())
    }
}

suspend fun Call.awaitResponse(): Response = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (!cont.isCancelled) cont.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            cont.resume(response) { response.close() }
        }
    })
    cont.invokeOnCancellation { runCatching { cancel() } }
}
