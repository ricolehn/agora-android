package org.agora.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import java.time.LocalDate
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.agora.app.data.model.AgoraEvent
import org.agora.app.data.remote.AiChunk
import org.agora.app.data.model.AiMessage
import org.agora.app.data.model.AiStatus
import org.agora.app.data.model.Attendees
import org.agora.app.data.model.AuthResponse
import org.agora.app.data.model.CalendarFeed
import org.agora.app.data.model.Candidates
import org.agora.app.data.model.ChatMessage
import org.agora.app.data.model.CreateThreadResponse
import org.agora.app.data.model.DutyRequest
import org.agora.app.data.model.EventSettings
import org.agora.app.data.model.FcmStatus
import org.agora.app.data.model.FeeSettings
import org.agora.app.data.model.FinanceRequest
import org.agora.app.data.model.FinanceStats
import org.agora.app.data.model.Group
import org.agora.app.data.model.Mentor
import org.agora.app.data.model.MentoringThread
import org.agora.app.data.model.MyMentorProfile
import org.agora.app.data.model.NotificationKinds
import org.agora.app.data.model.NotificationPrefs
import org.agora.app.data.model.Person
import org.agora.app.data.model.StatusResponse
import org.agora.app.data.model.TransactionPage
import org.agora.app.data.model.UploadResponse
import org.agora.app.data.model.User
import org.agora.app.data.remote.AgoraApi
import org.agora.app.data.remote.ApiException
import org.agora.app.data.model.parseAmount

/** Event form content sent to POST/PATCH /api/events. */
data class EventInput(
    val title: String,
    val date: String,
    val endDate: String = "",
    val startTime: String = "",
    val endTime: String = "",
    val location: String = "",
    val description: String = "",
    val eventType: String = "event",
    val isPinned: Boolean = false,
    val requiresRegistration: Boolean = false,
    val minParticipants: Int = 0,
    val maxParticipants: Int = 0,
    val targetGroups: List<String> = emptyList(),
    val imageUrl: String = "",
    val isRecurring: Boolean = false,
    val recurringRule: String = "weekly",
    val recurringCount: Int = 4,
    val duties: List<String> = emptyList()
)

class AgoraRepository(val api: AgoraApi) {
    private val json get() = api.json

    // ---------- server & auth ----------

    suspend fun status(baseUrl: String): StatusResponse {
        api.baseUrl = baseUrl
        return api.call("GET", "/api/status", StatusResponse.serializer(), reportUnauthorized = false)
    }

    /** App name from the PWA's config module (`appName: "..."`). */
    suspend fun appName(): String? = runCatching {
        Regex("appName:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(api.getText("/assets/config.js"))
            ?.groupValues?.get(1)?.let { json.decodeFromString(String.serializer(), "\"$it\"") }
    }.getOrNull()

    suspend fun login(email: String, password: String): AuthResponse =
        api.call("POST", "/api/auth/login", AuthResponse.serializer(), buildJsonObject {
            put("email", email.trim())
            put("password", password)
        }, reportUnauthorized = false)

    suspend fun register(code: String, email: String, firstName: String, lastName: String, password: String): AuthResponse =
        api.call("POST", "/api/auth/register", AuthResponse.serializer(), buildJsonObject {
            put("inviteCode", code.trim())
            put("email", email.trim())
            put("firstName", firstName.trim())
            put("lastName", lastName.trim())
            put("password", password)
        }, reportUnauthorized = false)

    suspend fun me(): User = api.get<AuthResponse>("/api/auth/me").user ?: throw ApiException(401, "Unauthorized")

    suspend fun logout() {
        runCatching { api.post<JsonObject>("/api/auth/logout") }
    }

    suspend fun changePassword(oldPassword: String, newPassword: String) {
        api.post<JsonObject>("/api/auth/password", buildJsonObject {
            put("oldPassword", oldPassword)
            put("password", newPassword)
        })
    }

    /** Deletes the own account and its personal data (Google Play account deletion policy). */
    suspend fun deleteAccount(password: String) {
        api.post<JsonObject>("/api/auth/delete-account", buildJsonObject { put("password", password) })
    }

    /** Reports an AI reply ("ai") or a chat conversation ("chat") to the admins. */
    suspend fun report(type: String, content: String, reason: String, prompt: String = "", threadId: String = "") {
        api.post<JsonObject>("/api/reports", buildJsonObject {
            put("type", type)
            put("content", content)
            put("reason", reason)
            if (prompt.isNotBlank()) put("prompt", prompt)
            if (threadId.isNotBlank()) put("threadId", threadId)
        })
    }

    // ---------- profile & settings ----------

    suspend fun uploadProfilePicture(jpeg: ByteArray) {
        api.upload("/api/profile/picture", JsonObject.serializer()) {
            addFormDataPart("picture", "profile.jpg", AgoraApi.fileBody(jpeg, "image/jpeg"))
        }
    }

    fun profilePictureUrl(uid: String): String = api.absolute("/api/profile/picture/$uid")

    /** Saves channels and kinds like the web (flat keys = push choice, for older servers). */
    suspend fun saveNotificationSettings(uid: String, prefs: NotificationPrefs) {
        fun kinds(k: NotificationKinds) = buildJsonObject {
            put("duties", k.duties); put("events", k.events); put("messages", k.messages)
            put("requests", k.requests); put("finances", k.finances); put("reports", k.reports)
        }
        api.patch<JsonObject>("/api/db", buildJsonObject {
            put("path", "users/$uid")
            putJsonObject("value") {
                put("notificationSettings", buildJsonObject {
                    kinds(prefs.push).forEach { (key, value) -> put(key, value) }
                    putJsonObject("channels") { put("push", prefs.channels.push); put("email", prefs.channels.email) }
                    put("push", kinds(prefs.push))
                    put("email", kinds(prefs.email))
                })
                put("emailNotifications", prefs.channels.push || prefs.channels.email)
            }
        })
    }

    /** Reports the device time zone, so the server times duty reminders in local time (only when it changed). */
    suspend fun syncTimeZone(user: User) {
        val zone = java.time.ZoneId.systemDefault().id
        if (zone.isBlank() || zone == user.timeZone) return
        api.patch<JsonObject>("/api/db", buildJsonObject {
            put("path", "users/${user.userId}")
            putJsonObject("value") { put("timeZone", zone) }
        })
    }

    suspend fun calendarFeed(): CalendarFeed = api.get("/api/user/calendar-feed")
    suspend fun resetCalendarFeed(): CalendarFeed = api.post("/api/user/calendar-feed/reset")

    suspend fun inviteCode(): String? = runCatching { api.get<String>("/api/db", mapOf("path" to "system/inviteCode")) }.getOrNull()

    suspend fun setInviteCode(code: String) {
        api.put<JsonObject>("/api/db", buildJsonObject {
            put("path", "system/inviteCode")
            put("value", code)
        })
    }

    suspend fun feeSettings(): FeeSettings = FeeSettings.from(runCatching { api.get<JsonObject>("/api/db", mapOf("path" to "settings")) }.getOrNull())

    suspend fun saveFeeSettings(rates: Map<String, Double>) {
        val current = api.get<JsonObject>("/api/db", mapOf("path" to "settings"))
        val merged = JsonObject(current + rates.mapValues { JsonPrimitive(it.value) })
        api.put<JsonObject>("/api/db", buildJsonObject {
            put("path", "settings")
            put("value", merged)
        })
    }

    suspend fun groups(): List<Group> = runCatching { api.get<List<Group>>("/api/groups") }.getOrDefault(emptyList())

    // ---------- finances (member) ----------

    private val peopleSerializer = MapSerializer(String.serializer(), Person.serializer())
    private val requestsSerializer = MapSerializer(String.serializer(), FinanceRequest.serializer())

    suspend fun ownPeople(uid: String): List<Person> =
        api.call("GET", "/api/db", peopleSerializer, query = mapOf("path" to "people", "orderByChild" to "uid", "equalTo" to uid))
            .values.filterNot { it.isDeleted }

    suspend fun allPeople(): List<Person> =
        api.call("GET", "/api/db", peopleSerializer, query = mapOf("path" to "people")).values.filterNot { it.isDeleted }

    suspend fun person(personId: String): Person? =
        api.call("GET", "/api/db", Person.serializer().nullable, query = mapOf("path" to "people/$personId"))

    suspend fun allRequests(): List<FinanceRequest> =
        api.call("GET", "/api/db", requestsSerializer, query = mapOf("path" to "requests")).values.sortedByDescending { it.timestamp }

    suspend fun uploadReceipt(ownerName: String, date: String, fileName: String, mime: String, bytes: ByteArray): String =
        api.upload("/api/upload", UploadResponse.serializer()) {
            // name/date must precede the file: the server builds the stored filename from them
            addFormDataPart("name", ownerName)
            addFormDataPart("date", date)
            addFormDataPart("receipt", fileName, AgoraApi.fileBody(bytes, mime))
        }.filename

    /** Receipt names come from request data members write: only a plain file name, encoded, stays in the path. */
    fun receiptUrl(filename: String): String {
        val name = filename.substringAfterLast('/').substringAfterLast('\\').takeUnless { it == "." || it == ".." }.orEmpty()
        return api.absolute("/api/receipts/" + android.net.Uri.encode(name))
    }

    /** Member â†’ treasurer application; the client has to notify the admins itself afterwards. */
    suspend fun submitRequest(user: User, person: Person?, type: String, data: JsonObject) {
        val id = System.currentTimeMillis().toString()
        val personName = person?.name ?: user.fullName
        api.put<JsonObject>("/api/db", buildJsonObject {
            put("path", "requests/$id")
            putJsonObject("value") {
                put("id", id)
                put("type", type)
                put("userId", user.userId)
                put("personId", person?.id ?: user.userId)
                put("personName", personName)
                put("data", data)
                put("status", "pending")
                put("timestamp", id.toLong())
            }
        })
        runCatching {
            api.post<JsonObject>("/api/notify-admins", buildJsonObject {
                put("reqType", type)
                put("personName", personName)
            })
        }
    }

    // ---------- finances (treasurer) ----------

    suspend fun stats(): FinanceStats = api.get("/api/stats")

    suspend fun transactions(page: Int, search: String): TransactionPage =
        api.get("/api/transactions", mapOf("page" to page.toString(), "perPage" to "50", "search" to search))

    /** Every booking (all pages), for the financial report. */
    suspend fun allTransactions(): List<org.agora.app.data.model.Transaction> {
        val all = mutableListOf<org.agora.app.data.model.Transaction>()
        var page = 1
        do {
            val result = api.get<TransactionPage>("/api/transactions", mapOf("page" to page.toString(), "perPage" to "500"))
            all += result.items
            page++
        } while (page <= result.totalPages)
        return all
    }

    /** Optimistic-locking update of a person record, like the PWA's runTransaction (3 attempts). */
    suspend fun mutatePerson(personId: String, mutate: (JsonObject) -> JsonObject) {
        repeat(3) { attempt ->
            val raw = api.get<JsonObject>("/api/db", mapOf("path" to "people/$personId", "raw" to "1"))
            val current = raw["value"] as? JsonObject ?: throw ApiException(404, "Person nicht gefunden")
            val normalized = JsonObject(current + mapOf(
                "payments" to (current["payments"] as? JsonArray ?: JsonArray(emptyList())),
                "statusHistory" to (current["statusHistory"] as? JsonArray ?: JsonArray(emptyList()))
            ))
            try {
                api.post<JsonObject>("/api/db/transaction", buildJsonObject {
                    put("path", "people/$personId")
                    put("currentVersion", raw["version"] ?: JsonNull)
                    put("value", mutate(normalized))
                })
                return
            } catch (e: ApiException) {
                if (e.status != 409 || attempt == 2) throw e
            }
        }
    }

    /** Donations/expenses are stored as whole lists: read, change, write back. */
    private suspend fun mutateCollection(name: String, change: (List<JsonElement>) -> List<JsonElement>) {
        val current = api.get<JsonElement>("/api/db", mapOf("path" to name))
        val list = when (current) {
            is JsonArray -> current.toList()
            is JsonObject -> current.values.toList()
            else -> emptyList()
        }.filterNot { it is JsonNull }
        api.put<JsonObject>("/api/db", buildJsonObject {
            put("path", name)
            put("value", JsonArray(change(list)))
        })
    }

    suspend fun bookPayment(personId: String, amount: Double, date: String, note: String, standingOrder: Boolean) =
        mutatePerson(personId) { person ->
            if (standingOrder) person.appendTo("standingOrders", buildJsonObject {
                put("id", System.currentTimeMillis().toString())
                put("amount", amount)
                put("startDate", date)
                put("note", note)
                put("lastAutoPayment", JsonNull)
            })
            else person.appendTo("payments", buildJsonObject {
                put("amount", amount)
                put("date", date)
                put("description", note)
                put("id", System.currentTimeMillis())
            }).withTotal(amount)
        }

    /** Ends a standing order on [endDate] (also retroactively), see [StandingOrders.end]. */
    suspend fun endStandingOrder(personId: String, orderId: String, endDate: String) =
        mutatePerson(personId) { StandingOrders.end(it, orderId, endDate, LocalDate.now()) }

    /** Removes the standing order entry; payments it already booked stay. */
    suspend fun deleteStandingOrder(personId: String, orderId: String) =
        mutatePerson(personId) { StandingOrders.remove(it, orderId) }

    suspend fun changeStatus(personId: String, newStatus: String, date: String) =
        mutatePerson(personId) { StatusHistory.apply(it, newStatus, date) }

    suspend fun addDonation(amount: Double, name: String, date: String, description: String) =
        mutateCollection("donations") { it + buildJsonObject {
            put("amount", amount)
            put("name", name)
            put("date", date)
            put("description", description.trim())
            put("id", System.currentTimeMillis())
        } }

    suspend fun addExpense(amount: Double, issuer: String, date: String, description: String, receipts: List<String>) =
        mutateCollection("expenses") { it + buildJsonObject {
            put("amount", amount)
            put("issuer", issuer)
            put("description", description)
            put("date", date)
            put("id", System.currentTimeMillis())
            if (receipts.isEmpty()) put("receipt", JsonNull)
            else put("receipt", json.encodeToString(ListSerializer(String.serializer()), receipts))
        } }

    suspend fun approveRequest(request: FinanceRequest) {
        val amount = request.field("amount")?.let(::parseAmount) ?: 0.0
        val date = request.field("date").orEmpty()
        when (request.type) {
            "expense" -> mutateCollection("expenses") { it + buildJsonObject {
                put("id", System.currentTimeMillis().toString())
                put("amount", amount)
                // The requester is the issuer of the expense, like for expenses booked by hand (web beta18)
                put("description", request.field("description").orEmpty())
                put("issuer", request.personName)
                put("date", date)
                put("receipt", request.data["receipt"] ?: JsonNull)
            } }
            "payment" -> mutatePerson(request.personId) { person ->
                person.appendTo("payments", buildJsonObject {
                    put("id", System.currentTimeMillis().toString())
                    put("amount", amount)
                    put("date", date)
                    put("description", request.field("note")?.takeIf { it.isNotBlank() } ?: "Zahlung (Genehmigt)")
                }).withTotal(amount)
            }
            "standing_order" -> mutatePerson(request.personId) { person ->
                person.appendTo("standingOrders", buildJsonObject {
                    put("id", System.currentTimeMillis().toString())
                    put("amount", amount)
                    put("startDate", date)
                    put("note", request.field("note")?.takeIf { it.isNotBlank() } ?: "Dauerauftrag (Genehmigt)")
                    put("lastAutoPayment", JsonNull)
                })
            }
            // Same history rewrite as a treasurer status change (the PWA request applier drops the new entry)
            "status" -> mutatePerson(request.personId) { StatusHistory.apply(it, request.field("newStatus").orEmpty(), date) }
        }
        patchRequest(request.id, buildJsonObject { put("status", "approved") })
    }

    suspend fun rejectRequest(requestId: String, reason: String) =
        patchRequest(requestId, buildJsonObject {
            put("status", "rejected")
            put("rejectionReason", reason.ifBlank { "Kein Grund angegeben" })
        })

    private suspend fun patchRequest(id: String, value: JsonObject) {
        api.patch<JsonObject>("/api/db", buildJsonObject {
            put("path", "requests/$id")
            put("value", value)
        })
    }

    // ---------- events ----------

    suspend fun events(): List<AgoraEvent> = api.get("/api/events")
    suspend fun myDutyRequests(): List<DutyRequest> = api.get("/api/events/my-requests")
    suspend fun eventSettings(): EventSettings = runCatching { api.get<EventSettings>("/api/events/settings") }.getOrDefault(EventSettings())
    suspend fun attendees(eventId: String): Attendees = api.get("/api/events/$eventId/attendees")
    suspend fun candidates(): Candidates = api.get("/api/events/candidates")

    suspend fun register(eventId: String, register: Boolean) {
        api.post<JsonObject>("/api/events/$eventId/register", buildJsonObject { put("action", if (register) "register" else "cancel") })
    }

    suspend fun removeAttendee(eventId: String, userId: String) {
        api.delete<JsonObject>("/api/events/$eventId/attendees/$userId")
    }

    suspend fun respondToDuty(dutyId: String, accept: Boolean) {
        api.post<JsonObject>("/api/events/duties/$dutyId/respond", buildJsonObject { put("action", if (accept) "accept" else "decline") })
    }

    suspend fun claimDuty(dutyId: String, claim: Boolean) {
        api.post<JsonObject>("/api/events/duties/$dutyId/claim", if (claim) buildJsonObject {} else buildJsonObject { put("action", "unclaim") })
    }

    suspend fun cancelDutyRequest(dutyId: String) {
        api.post<JsonObject>("/api/events/duties/$dutyId/cancel-request")
    }

    suspend fun addDuty(eventId: String, roleName: String, section: String, userId: String?, groupId: String?) {
        api.post<JsonObject>("/api/events/$eventId/duties", buildJsonObject {
            put("roleName", roleName)
            if (section.isNotBlank()) put("section", section)
            userId?.let { put("targetUserId", it) }
            groupId?.let { put("targetGroupId", it) }
            put("sendEmail", true)
        })
    }

    suspend fun assignDuty(dutyId: String, userId: String?, groupId: String?) {
        api.post<JsonObject>("/api/events/duties/$dutyId/assign", buildJsonObject {
            userId?.let { put("targetUserId", it) }
            groupId?.let { put("targetGroupId", it) }
            put("sendEmail", true)
        })
    }

    suspend fun updateDutyNotes(dutyId: String, notes: String) {
        api.patch<JsonObject>("/api/events/duties/$dutyId", buildJsonObject { put("notes", notes) })
    }

    suspend fun deleteDuty(dutyId: String) {
        api.delete<JsonObject>("/api/events/duties/$dutyId")
    }

    suspend fun uploadEventImage(jpeg: ByteArray): String =
        api.upload("/api/events/upload-image", UploadResponse.serializer()) {
            addFormDataPart("image", "cover.jpg", AgoraApi.fileBody(jpeg, "image/jpeg"))
        }.url

    suspend fun saveEvent(existingId: String?, input: EventInput) {
        val body = buildJsonObject {
            put("title", input.title.trim())
            put("date", input.date)
            put("endDate", input.endDate)
            put("startTime", input.startTime)
            put("endTime", input.endTime)
            put("location", input.location.trim())
            put("description", input.description.trim())
            put("eventType", input.eventType)
            put("isPinned", input.isPinned)
            put("requiresRegistration", input.requiresRegistration)
            put("minParticipants", input.minParticipants)
            put("maxParticipants", input.maxParticipants)
            putJsonArray("targetGroups") { input.targetGroups.forEach { add(JsonPrimitive(it)) } }
            put("imageUrl", input.imageUrl)
            if (existingId == null) {
                put("isRecurring", input.isRecurring)
                if (input.isRecurring) {
                    put("recurringRule", input.recurringRule)
                    put("recurringCount", input.recurringCount)
                }
                if (input.duties.isNotEmpty()) put("duties", buildJsonArray {
                    input.duties.forEach { add(buildJsonObject { put("roleName", it) }) }
                })
            }
        }
        if (existingId == null) api.post<JsonObject>("/api/events", body) else api.patch<JsonObject>("/api/events/$existingId", body)
    }

    suspend fun deleteEvent(eventId: String) {
        api.delete<JsonObject>("/api/events/$eventId")
    }

    // ---------- mentoring ----------

    suspend fun threads(): List<MentoringThread> = api.get("/api/mentoring/threads")
    suspend fun messages(threadId: String): List<ChatMessage> = api.get("/api/mentoring/threads/$threadId/messages")

    suspend fun sendMessage(threadId: String, text: String) {
        api.post<JsonObject>("/api/mentoring/threads/$threadId/messages", buildJsonObject { put("text", text) })
    }

    suspend fun setThreadStatus(threadId: String, status: String) {
        api.patch<JsonObject>("/api/mentoring/threads/$threadId/status", buildJsonObject { put("status", status) })
    }

    suspend fun mentors(status: String? = null): List<Mentor> =
        api.get("/api/mentoring/mentors", status?.let { mapOf("status" to it) } ?: emptyMap())

    suspend fun myMentorProfile(): MyMentorProfile = runCatching { api.get<MyMentorProfile>("/api/mentoring/my-profile") }.getOrDefault(MyMentorProfile())

    suspend fun contactMentor(mentorUserId: String, message: String): String =
        api.post<CreateThreadResponse>("/api/mentoring/threads", buildJsonObject {
            put("mentorId", mentorUserId)
            put("initialMessage", message)
        }).threadId

    suspend fun saveMentorProfile(isNew: Boolean, bio: String, maxMentees: Int, isAccepting: Boolean) {
        val body = buildJsonObject {
            put("bio", bio.trim())
            put("max_mentees", maxMentees)
            if (!isNew) put("isAccepting", isAccepting)
        }
        if (isNew) api.post<JsonObject>("/api/mentoring/apply", body) else api.put<JsonObject>("/api/mentoring/my-profile", body)
    }

    suspend fun setMentorStatus(mentorRecordId: String, status: String) {
        api.post<JsonObject>("/api/mentoring/manage/$mentorRecordId/status", buildJsonObject { put("status", status) })
    }

    // ---------- AI ----------

    suspend fun aiEnabled(): Boolean = runCatching { api.get<AiStatus>("/api/admin/ai-status").enabled }.getOrDefault(false)

    fun aiChat(history: List<AiMessage>): Flow<AiChunk> = api.aiChat(buildJsonObject {
        put("messages", JsonArray(history.map { buildJsonObject { put("role", it.role); put("content", it.content) } }))
    })

    // ---------- push ----------

    /** The server's FCM setup incl. its Firebase client config; null when it could not be asked (offline, older server). */
    suspend fun fcmStatus(): FcmStatus? = runCatching { api.get<FcmStatus>("/api/push/fcm") }.getOrNull()

    suspend fun subscribeFcm(token: String) {
        api.post<JsonObject>("/api/push/fcm/subscribe", buildJsonObject {
            put("token", token)
            put("platform", "android")
        })
    }

    suspend fun unsubscribeFcm(token: String) {
        runCatching { api.post<JsonObject>("/api/push/fcm/unsubscribe", buildJsonObject { put("token", token) }) }
    }

    suspend fun testPush() {
        api.post<JsonObject>("/api/push/test")
    }
}

// ---------- JSON helpers for person records ----------

private fun JsonObject.appendTo(key: String, item: JsonElement): JsonObject {
    val list = (this[key] as? JsonArray)?.toList().orEmpty()
    return JsonObject(this + (key to JsonArray(list + item)))
}

private fun JsonObject.withTotal(added: Double): JsonObject {
    val total = (this["totalPaid"] as? JsonPrimitive)?.contentOrNull?.let(::parseAmount) ?: 0.0
    return JsonObject(this + ("totalPaid" to JsonPrimitive(total + added)))
}

/** Ending / removing standing orders, ported from the PWA (saveStandingOrderEnd) so both clients write identical records. */
object StandingOrders {
    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
    private fun day(value: String?): LocalDate? = value?.takeIf { it.length >= 10 }?.let { runCatching { LocalDate.parse(it.substring(0, 10)) }.getOrNull() }
    private fun list(person: JsonObject, key: String): List<JsonElement> = (person[key] as? JsonArray)?.toList().orEmpty()

    /**
     * Sets the end date of order [orderId]. Automatic payments of this order booked after that day are removed
     * again (ending retroactively), the paid total follows, and an order whose end already lies in the past is
     * dropped from the list - exactly what the PWA does.
     */
    fun end(person: JsonObject, orderId: String, endDate: String, today: LocalDate): JsonObject {
        val end = day(endDate) ?: return person
        val autoPrefix = "auto_${orderId}_"
        val payments = list(person, "payments").filterNot { item ->
            val payment = item as? JsonObject ?: return@filterNot false
            val isAuto = (payment["isAuto"] as? JsonPrimitive)?.booleanOrNull == true
            val paidOn = day(payment.text("date"))
            isAuto && payment.text("id").orEmpty().startsWith(autoPrefix) && paidOn != null && paidOn.isAfter(end)
        }
        val orders = list(person, "standingOrders").mapNotNull { item ->
            val order = item as? JsonObject ?: return@mapNotNull item
            when {
                order.text("id") != orderId -> order
                end.isBefore(today) -> null
                else -> JsonObject(order + ("endDate" to JsonPrimitive(endDate)))
            }
        }
        val total = payments.sumOf { ((it as? JsonObject)?.get("amount") as? JsonPrimitive)?.contentOrNull?.let(::parseAmount) ?: 0.0 }
        return JsonObject(person + mapOf(
            "payments" to JsonArray(payments),
            "standingOrders" to JsonArray(orders),
            "totalPaid" to JsonPrimitive(total)
        ))
    }

    fun remove(person: JsonObject, orderId: String): JsonObject =
        JsonObject(person + ("standingOrders" to JsonArray(list(person, "standingOrders").filterNot { (it as? JsonObject)?.text("id") == orderId })))
}

/** Status history rewriting, ported from the PWA so both clients produce identical records. */
object StatusHistory {
    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
    private fun entries(person: JsonObject): List<JsonObject> =
        (person["statusHistory"] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()

    private fun entry(status: String, start: String, end: String?) = buildJsonObject {
        put("status", status)
        put("startDate", start)
        if (end != null) put("endDate", end)
    }

    /** Treasurer status change (retroactive or future), `applyStatusChangeToHistory` in the PWA. */
    fun apply(person: JsonObject, newStatus: String, changeDate: String): JsonObject {
        val memberSince = person.str("originalMemberSince") ?: person.str("memberSince") ?: changeDate
        if (changeDate < memberSince) throw ApiException(400, "Ã„nderungsdatum liegt vor Beginn der Mitgliedschaft.")
        if (changeDate <= memberSince) {
            return JsonObject(person + mapOf("status" to JsonPrimitive(newStatus), "statusHistory" to JsonArray(listOf(entry(newStatus, memberSince, null)))))
        }
        val history = entries(person)
            .filter { (it.str("startDate") ?: memberSince) < changeDate }
            .map { e ->
                val end = e.str("endDate")
                if (end != null && end <= changeDate) e else JsonObject(e + ("endDate" to JsonPrimitive(changeDate)))
            }.toMutableList()
        val lastEnd = history.lastOrNull()?.str("endDate")
        if (history.isEmpty() || (lastEnd != null && lastEnd < changeDate)) {
            val priorStart = lastEnd ?: memberSince
            if (priorStart < changeDate) history += entry(person.str("status") ?: "vollverdiener", priorStart, changeDate)
        }
        history += entry(newStatus, changeDate, null)
        return JsonObject(person + mapOf("status" to JsonPrimitive(newStatus), "statusHistory" to JsonArray(history)))
    }

}
