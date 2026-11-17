package org.agora.app

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.agora.app.data.EventInput
import org.agora.app.data.MediaFile
import org.agora.app.data.PrayerDuration
import org.agora.app.data.ProgramItem
import org.agora.app.data.SongInput
import org.agora.app.data.addEmptyTask
import org.agora.app.data.createMediaFolder
import org.agora.app.data.mediaFileUrl
import org.agora.app.data.mediaList
import org.agora.app.data.mediaPreviewUrl
import org.agora.app.data.mediaStatus
import org.agora.app.data.planSongs
import org.agora.app.data.prayers
import org.agora.app.data.program
import org.agora.app.data.saveProgram
import org.agora.app.data.saveSong
import org.agora.app.data.sharePrayer
import org.agora.app.data.songbook
import org.agora.app.data.model.FeeChange
import org.agora.app.data.model.NotificationChannels
import org.agora.app.data.model.NotificationKinds
import org.agora.app.data.model.NotificationPrefs
import org.agora.app.data.model.User
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Everything besides the books through API v2 (server CALDERA): the calendar with its duty roster, the own account,
 * the songbook, prayer requests, mentoring, the media library and the fee settings - what the app sends, what it makes
 * of the answers (the screens' own models, whichever API answered) and that it goes the old way on a server that
 * turns out to answer no v2.
 */
class ApiV2AreasTest {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true; explicitNulls = false }

    // ---------- the calendar ----------

    @Test
    fun `the events come page after page, with the roster and its names for those who see it`() = FakeServer().use { server ->
        val repo = server.v2Repository(json)
        server.on("GET", "/api/v2/events") { query ->
            assertEquals("500", query["limit"])
            if (query["cursor"] == null) 200 to """{"items":[$EVENT],"nextCursor":"c1"}"""
            else 200 to """{"items":[{"id":"e2","title":"Andacht","date":"2026-12-02","kind":"somethingNew","duties":null,"isPublic":false,"shareToken":null}],"nextCursor":null}"""
        }
        val (first, second) = runBlocking { repo.events() }
        assertEquals(listOf("e1", "Grillfest", "2026-12-01", "", "18:00", "termin"), listOf(first.id, first.title, first.date, first.endDate, first.startTime, first.eventType))
        assertTrue(first.isTermin)
        assertTrue("every place taken", first.isFull)
        assertEquals(listOf("Max Muster", "u-max"), listOf(first.createdByName, first.createdBy))
        assertEquals("registered", first.myRegistration?.status)
        assertTrue(first.canAccessDutyPlan && first.canEdit)
        assertEquals("tok123", first.publicToken)
        assertEquals(true, first.isPublic)
        val duty = first.duties.single()
        assertEquals(listOf("d1", "e1", "Grill", "requested", "u-mia", "Mia Muster", "u-max", "Max Muster", "", ""),
            listOf(duty.id, duty.event, duty.roleName, duty.status, duty.requestedUser, duty.requestedUserName, duty.requestedBy, duty.requestedByName, duty.assignedUser, duty.assignedGroup))
        assertTrue("whoever may edit the event manages its duties", duty.canManageDuty && duty.canEditNotes)
        // No roster for this reader; a kind the app does not know is no appointment
        assertFalse(second.canAccessDutyPlan)
        assertFalse(second.isTermin)
        assertEquals("", second.publicToken)
        assertEquals(2, server.calls("GET", "/api/v2/events").size)
    }

    @Test
    fun `a server that turns out to answer no v2 is asked the old way - and from then on`() = FakeServer().use { server ->
        val repo = server.v2Repository(json)
        // An older server's page for a route it does not know: no v2 error code
        server.on("GET", "/api/v2/events") { 404 to "<!DOCTYPE html><pre>Cannot GET /api/v2/events</pre>" }
        server.on("GET", "/api/events") { 200 to """[{"id":"e1","title":"Alt","date":"2026-01-01","canAccessDutyPlan":true,"duties":[]}]""" }
        assertEquals("Alt", runBlocking { repo.events() }.single().title)
        assertFalse(repo.v2.available)
        server.on("GET", "/api/events/my-requests") { 200 to "[]" }
        runBlocking { repo.myDutyRequests() }
        assertTrue("not asked through v2 again", server.calls("GET", "/api/v2/duty-requests").isEmpty())
    }

    @Test
    fun `the duties asked of the person, the participants and the people of the roster`() = FakeServer().use { server ->
        val repo = server.v2Repository(json)
        server.on("GET", "/api/v2/duty-requests") {
            200 to """{"items":[{"dutyId":"d1","id":"d1","eventId":"e1","eventTitle":"Grillfest","eventDate":"2026-12-01","eventStartTime":null,"eventEndTime":null,
                "eventLocation":"Garten","section":"","roleName":"Grill","requestedById":"u-max","requestedByName":"Max Muster","notes":"Kohle mitbringen"}],"nextCursor":null}"""
        }
        val request = runBlocking { repo.myDutyRequests() }.single()
        assertEquals(listOf("d1", "e1", "Grillfest", "2026-12-01", "", "u-max", "Max Muster", "Kohle mitbringen"),
            listOf(request.id, request.eventId, request.eventTitle, request.eventDate, request.eventStartTime, request.requestedBy, request.requestedByName, request.notes))

        server.on("GET", "/api/v2/events/e1/participants") {
            200 to """{"items":[{"id":"r1","userId":"u-mia","guestId":null,"isGuest":false,"name":"Mia","status":"registered","signedUpAt":"2026-11-01T10:00:00.000Z"},
                {"id":"g1","userId":null,"guestId":"g1","isGuest":true,"name":"Gast","status":"waitlist","email":"gast@example.org","age":17}],"nextCursor":null}"""
        }
        val attendees = runBlocking { repo.attendees("e1") }
        assertEquals(listOf("u-mia"), attendees.registered.map { it.userId })
        val guest = attendees.waitlist.single()
        assertEquals(listOf("guest:g1", "gast@example.org"), listOf(guest.userId, guest.email))
        assertTrue(guest.guest)
        assertEquals(17, guest.age)
        // A guest is removed by the name the old API gave them
        server.on("DELETE", "/api/v2/events/e1/participants/guest:g1") { 204 to "" }
        runBlocking { repo.removeAttendee("e1", guest.userId) }
        assertEquals(1, server.calls("DELETE", "/api/v2/events/e1/participants/guest:g1").size)

        server.on("GET", "/api/v2/people") { 200 to """{"items":[{"id":"u-mia","name":"Mia","email":"","groups":["gr1"]}],"nextCursor":null}""" }
        server.on("GET", "/api/v2/groups") { 200 to """{"items":[{"id":"gr1","name":"Technik","permissions":[]}],"nextCursor":null}""" }
        val candidates = runBlocking { repo.candidates() }
        assertEquals(listOf("Mia"), candidates.candidates.map { it.name })
        assertEquals(listOf("Technik"), candidates.groups.map { it.name })
        assertEquals(listOf("gr1"), runBlocking { repo.groups() }.map { it.id })
        assertTrue("every member may create events", runBlocking { repo.eventSettings() }.allowMemberCreation)
    }

    @Test
    fun `an event is saved in v2's words - a new one with its series and duties`() = FakeServer().use { server ->
        val repo = server.v2Repository(json)
        server.on("POST", "/api/v2/events") { 201 to EVENT }
        server.on("PATCH", "/api/v2/events/e1") { 200 to EVENT }
        val input = EventInput(title = " Grillfest ", date = "2026-12-01", startTime = "18:00", eventType = "termin", isRecurring = true, recurringRule = "biweekly",
            recurringCount = 6, duties = listOf("Aufbau", "Abbau"), targetGroups = listOf("Jugend"))
        runBlocking { repo.saveEvent(null, input) }
        val created = server.calls("POST", "/api/v2/events").single().json()
        assertEquals("Grillfest", created["title"]!!.jsonPrimitive.content)
        assertEquals("termin", created["kind"]!!.jsonPrimitive.content)
        assertNull("v1's name stays away", created["eventType"])
        assertEquals(listOf("biweekly", "6"), listOf(created["recurrence"]!!.jsonObject["rule"]!!.jsonPrimitive.content, created["recurrence"]!!.jsonObject["count"]!!.jsonPrimitive.content))
        assertEquals(listOf("Aufbau", "Abbau"), created["duties"]!!.jsonArray.map { it.jsonObject["roleName"]!!.jsonPrimitive.content })
        assertEquals(listOf("Jugend"), created["targetGroups"]!!.jsonArray.map { it.jsonPrimitive.content })
        // A change: no series, no duties
        runBlocking { repo.saveEvent("e1", input) }
        val changed = server.calls("PATCH", "/api/v2/events/e1").single().json()
        assertNull(changed["recurrence"])
        assertNull(changed["duties"])
        // One who does not manage events sends neither kind nor pinning (v2 would refuse them): their appointment stays one
        runBlocking { repo.saveEvent("e1", input.copy(managed = false)) }
        val byCreator = server.calls("PATCH", "/api/v2/events/e1").last().json()
        assertNull(byCreator["kind"])
        assertNull(byCreator["isPinned"])
        // Shared with people without an account
        runBlocking { repo.setEventPublic("e1", true) }
        assertTrue(server.calls("PATCH", "/api/v2/events/e1").last().json()["isPublic"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun `signing up, the duty roster's steps and the order of service go to v2's resources`() = FakeServer().use { server ->
        val repo = server.v2Repository(json)
        val duty = """{"id":"d1","eventId":"e1","roleName":"Grill","status":"open"}"""
        server.on("PUT", "/api/v2/events/e1/registration") { 200 to EVENT }
        server.on("DELETE", "/api/v2/events/e1/registration") { 200 to EVENT }
        server.on("POST", "/api/v2/duties/d1/answer") { 200 to duty }
        server.on("PUT", "/api/v2/duties/d1/claim") { 200 to duty }
        server.on("DELETE", "/api/v2/duties/d1/claim") { 200 to duty }
        server.on("DELETE", "/api/v2/duties/d1/request") { 200 to duty }
        server.on("POST", "/api/v2/duties/d1/assignment") { 200 to duty }
        server.on("PATCH", "/api/v2/duties/d1") { 200 to duty }
        server.on("DELETE", "/api/v2/duties/d1") { 204 to "" }
        server.on("POST", "/api/v2/events/e1/duties") { 201 to duty }
        runBlocking {
            repo.register("e1", true)
            repo.register("e1", false)
            repo.respondToDuty("d1", accept = false)
            repo.claimDuty("d1", true)
            repo.claimDuty("d1", false)
            repo.cancelDutyRequest("d1")
            repo.assignDuty("d1", null, "gr1")
            repo.updateDutyNotes("d1", "Kohle")
            repo.deleteDuty("d1")
            repo.addDuty("e1", "Grill", "Essen", "u-mia", null)
            repo.addEmptyTask("e1", "Deko")
        }
        assertEquals(1, server.calls("PUT", "/api/v2/events/e1/registration").size)
        assertEquals(1, server.calls("DELETE", "/api/v2/events/e1/registration").size)
        assertEquals("decline", server.calls("POST", "/api/v2/duties/d1/answer").single().json()["answer"]!!.jsonPrimitive.content)
        assertEquals("gr1", server.calls("POST", "/api/v2/duties/d1/assignment").single().json()["groupId"]!!.jsonPrimitive.content)
        assertEquals("Kohle", server.calls("PATCH", "/api/v2/duties/d1").single().json()["notes"]!!.jsonPrimitive.content)
        val (asked, task) = server.calls("POST", "/api/v2/events/e1/duties").map { it.json() }
        assertEquals(listOf("Grill", "Essen", "u-mia", "true"), listOf(asked["roleName"], asked["section"], asked["requestedUserId"], asked["sendEmail"]).map { it!!.jsonPrimitive.content })
        assertEquals(listOf("Deko", "false"), listOf(task["roleName"], task["sendEmail"]).map { it!!.jsonPrimitive.content })
        assertNull(task["requestedUserId"])

        // The order of service: read with its event, saved as a whole - a song's key only with the song
        server.on("GET", "/api/v2/events/e1") {
            200 to EVENT.replace("\"program\":null", """"program":{"items":[{"id":"p1","title":"Begrüßung","minutes":5,"who":"","note":"","songId":null,"songKey":null}],"updatedAt":"2026-11-02T09:00:00.000Z"}""")
        }
        val program = runBlocking { repo.program("e1") }
        assertEquals(listOf("Begrüßung"), program.items.map { it.title })
        assertEquals("", program.items.single().songId)
        assertTrue(program.canEdit)
        assertEquals(java.time.Instant.parse("2026-11-02T09:00:00.000Z").toEpochMilli(), program.updatedAt)
        server.on("PUT", "/api/v2/events/e1/program") { 200 to """{"items":[{"id":"p1","title":"Begrüßung","minutes":5},{"id":"p2","title":"","songId":"s1","songKey":"G"}],"updatedAt":"2026-11-03T09:00:00.000Z"}""" }
        val saved = runBlocking { repo.saveProgram("e1", listOf(ProgramItem(id = "p1", title = "Begrüßung", minutes = 5), ProgramItem(songId = "s1", songKey = "G"))) }
        assertEquals(listOf("p1", "p2"), saved.items.map { it.id })
        val (plain, song) = server.calls("PUT", "/api/v2/events/e1/program").single().json()["items"]!!.jsonArray.map { it.jsonObject }
        assertNull(plain["songId"])
        assertNull(plain["songKey"])
        assertEquals(listOf("s1", "G"), listOf(song["songId"], song["songKey"]).map { it!!.jsonPrimitive.content })
        assertNull("a new item gets its id from the server", song["id"])

        server.on("GET", "/api/v2/songs") { 200 to """{"items":[{"id":"s1","title":"Amazing Grace","artist":"","key":null,"content":"[G]Amazing"}],"nextCursor":null,"ccliLicense":"123"}""" }
        assertEquals(listOf("s1" to ""), runBlocking { repo.planSongs() }.map { it.id to it.key })
        server.on("GET", "/api/v2/calendar-feed") { 200 to """{"feedUrl":"https://a/api/events/calendar.ics?user=u&token=t","webcalUrl":"webcal://a/x"}""" }
        assertEquals("webcal://a/x", runBlocking { repo.calendarFeed() }.webcalUrl)
    }

    // ---------- the own account ----------

    @Test
    fun `the own settings, the registration code, pictures and reports go through v2`() = FakeServer().use { server ->
        val repo = server.v2Repository(json)
        server.on("PATCH", "/api/v2/me") { 200 to "{}" }
        val prefs = NotificationPrefs(NotificationChannels(push = true, email = true), NotificationKinds(), NotificationKinds(finances = false))
        runBlocking { repo.saveNotificationSettings("u1", prefs) }
        val settings = server.calls("PATCH", "/api/v2/me").single().json()["notificationSettings"]!!.jsonObject
        assertTrue(settings["channels"]!!.jsonObject["email"]!!.jsonPrimitive.boolean)
        assertFalse(settings["email"]!!.jsonObject["finances"]!!.jsonPrimitive.boolean)
        assertTrue(settings["push"]!!.jsonObject["finances"]!!.jsonPrimitive.boolean)
        assertNull("no flat keys of the old API", settings["duties"])
        // The time zone only when it changed
        runBlocking { repo.syncTimeZone(User(uid = "u1", timeZone = java.time.ZoneId.systemDefault().id)) }
        assertEquals(1, server.calls("PATCH", "/api/v2/me").size)
        runBlocking { repo.syncTimeZone(User(uid = "u1", timeZone = "Nowhere/Else")) }
        assertEquals(java.time.ZoneId.systemDefault().id, server.calls("PATCH", "/api/v2/me").last().json()["timeZone"]!!.jsonPrimitive.content)

        server.on("GET", "/api/v2/registration-code") { 200 to """{"code":"123456"}""" }
        server.on("POST", "/api/v2/registration-code") { 201 to """{"code":"654321"}""" }
        assertEquals("123456", runBlocking { repo.inviteCode() })
        assertEquals("the server makes the new one", "654321", runBlocking { repo.newInviteCode() })

        server.on("GET", "/api/v2/pictures") { 200 to """{"items":[{"id":"u1","userId":"u1","version":1730000000000}],"nextCursor":null}""" }
        assertEquals(mapOf("u1" to 1730000000000L), runBlocking { repo.pictureVersions() })
        assertEquals("${server.base}/api/v2/pictures/u1?v=1730000000000", repo.profilePictureUrl("u1", 1730000000000L))
        server.on("PUT", "/api/v2/me/picture") { 200 to """{"userId":"u1","version":1730000000001}""" }
        assertEquals(1730000000001L, runBlocking { repo.uploadProfilePicture(byteArrayOf(1, 2, 3)) })
        assertTrue(server.calls("PUT", "/api/v2/me/picture").single().headers.entries.first { it.key.equals("Content-Type", ignoreCase = true) }.value.startsWith("multipart/form-data"))

        // A report: what the server takes - longer is cut here, as the old API did
        server.on("POST", "/api/v2/reports") { 204 to "" }
        runBlocking { repo.report("ai", "x".repeat(5000), "Falsch", prompt = "Frage") }
        runBlocking { repo.report("chat", "Hallo", "Beleidigend", threadId = "t1") }
        val (ai, chat) = server.calls("POST", "/api/v2/reports").map { it.json() }
        assertEquals(4000, ai["content"]!!.jsonPrimitive.content.length)
        assertEquals(listOf("Frage", null), listOf(ai["prompt"]?.jsonPrimitive?.content, ai["threadId"]))
        assertEquals(listOf("chat", "t1"), listOf(chat["type"], chat["threadId"]).map { it!!.jsonPrimitive.content })
    }

    // ---------- the community ----------

    @Test
    fun `the songbook and prayer requests come through v2 in the app's models`() = FakeServer().use { server ->
        val repo = server.v2Repository(json)
        server.on("GET", "/api/v2/songs") {
            200 to """{"items":[{"id":"s1","title":"Amazing Grace","artist":"Newton","key":"G","capo":null,"tempo":72,"ccli":null,"copyright":"",
                "content":"[G]Amazing","createdAt":"2026-01-01T00:00:00.000Z","updatedAt":"2026-02-01T00:00:00.000Z"}],"nextCursor":null,"ccliLicense":"987654"}"""
        }
        server.on("GET", "/api/v2/me") { 200 to """{"id":"u1","can":{"manageSongbook":true}}""" }
        val book = runBlocking { repo.songbook() }
        assertEquals("987654", book.ccliLicense)
        assertTrue(book.canManage)
        val song = book.songs.single()
        assertEquals(listOf("Amazing Grace", "G", "", "72"), listOf(song.title, song.key, song.ccli, song.tempo.toString()))
        assertEquals(java.time.Instant.parse("2026-02-01T00:00:00.000Z").toEpochMilli(), song.updatedAt)
        server.on("PATCH", "/api/v2/songs/s1") { 200 to """{"id":"s1","title":"Amazing Grace","content":"[G]Amazing grace"}""" }
        runBlocking { repo.saveSong("s1", SongInput("Amazing Grace", "Newton", "G", "", "", "[G]Amazing grace", capo = null, tempo = 72)) }
        val sent = server.calls("PATCH", "/api/v2/songs/s1").single().json()
        assertEquals(JsonNull, sent["capo"])
        assertEquals(72, sent["tempo"]!!.jsonPrimitive.int)

        server.on("GET", "/api/v2/prayers") {
            200 to """{"items":[{"id":"p1","text":"Für meine Familie","isAnonymous":true,"authorUserId":null,"authorName":null,"createdAt":"2026-11-01T08:00:00.000Z",
                "expiresAt":"2026-12-01T08:00:00.000Z","isExpired":false,"isMine":true,"canDelete":true}],"nextCursor":null,"canModerate":true}"""
        }
        val list = runBlocking { repo.prayers() }
        assertTrue(list.canModerate)
        val prayer = list.prayers.single()
        assertTrue(prayer.anonymous && prayer.isMine && prayer.canDelete)
        assertNull("nobody is named", prayer.author)
        assertEquals(java.time.Instant.parse("2026-12-01T08:00:00.000Z").toEpochMilli(), prayer.expiresAt)
        server.on("POST", "/api/v2/prayers") { 201 to """{"id":"p2","text":"Neu","isAnonymous":false,"authorUserId":"u1","authorName":"Ich","isMine":true}""" }
        val shared = runBlocking { repo.sharePrayer("Neu", PrayerDuration.WEEK, anonymous = false) }
        assertEquals(listOf("u1", "Ich"), listOf(shared.author?.uid, shared.author?.name))
        assertEquals("week", server.calls("POST", "/api/v2/prayers").single().json()["duration"]!!.jsonPrimitive.content)
    }

    @Test
    fun `mentoring through v2 - the conversation is read when its messages are, the oldest first`() = FakeServer().use { server ->
        val repo = server.v2Repository(json)
        server.on("GET", "/api/v2/mentoring/threads") {
            200 to """{"items":[{"id":"t1","role":"mentor","mentorUserId":"u-me","menteeAlias":"Sucher 7","status":"blocked_mentor","lastMessage":"Danke!","updatedAt":"2026-11-02 10:00:00.000Z",
                "title":"Sucher 7","mentorName":"Ich","isClosed":true,"isBlocked":true,"isBlockedByMe":true,"unreadCount":2,"lastMessageAt":"2026-11-02 10:00:00.000Z"},
                {"id":"t2","role":"mentee","mentorUserId":"u-m","status":"active","lastMessage":"","updatedAt":"2026-11-01 10:00:00.000Z","title":"Mara","mentorName":"Mara","unreadCount":0,"lastMessageAt":null}],"nextCursor":null}"""
        }
        val (blocked, fresh) = runBlocking { repo.threads() }
        assertEquals(listOf("mentor", "Sucher 7", "Danke!", "2026-11-02 10:00:00.000Z"), listOf(blocked.myRole, blocked.partnerName, blocked.lastMessage?.text, blocked.lastMessage?.created))
        assertTrue(blocked.blocked && blocked.blockedByMe)
        assertEquals(2, blocked.unreadCount)
        assertNull("no message yet", fresh.lastMessage)
        assertEquals("Mara", fresh.partnerName)

        server.on("POST", "/api/v2/mentoring/threads/t1/read") { 200 to """{"id":"t1","role":"mentor","status":"active"}""" }
        server.on("GET", "/api/v2/mentoring/threads/t1/messages") { query ->
            if (query["cursor"] == null) 200 to """{"items":[{"id":"m3","from":"mentee","text":"Danke!","isRead":true,"createdAt":"2026-11-02 10:00:00.000Z"},
                {"id":"m2","from":"mentor","text":"Gern","isRead":true,"createdAt":"2026-11-02 09:00:00.000Z"}],"nextCursor":"older"}"""
            else 200 to """{"items":[{"id":"m1","from":"mentee","text":"Hallo","isRead":true,"createdAt":"2026-11-02 08:00:00.000Z"}],"nextCursor":null}"""
        }
        val messages = runBlocking { repo.messages("t1") }
        assertEquals(listOf("m1", "m2", "m3"), messages.map { it.id })
        assertEquals(listOf(false, true, false), messages.map { it.isMine })
        assertEquals(1, server.calls("POST", "/api/v2/mentoring/threads/t1/read").size)

        server.on("POST", "/api/v2/mentoring/threads") { 201 to """{"id":"t9","role":"mentee"}""" }
        assertEquals("t9", runBlocking { repo.contactMentor("u-m", "Hallo") })
        assertEquals(listOf("u-m", "Hallo"), server.calls("POST", "/api/v2/mentoring/threads").single().json().let { listOf(it["mentorUserId"], it["message"]) }.map { it!!.jsonPrimitive.content })
        server.on("PATCH", "/api/v2/mentoring/threads/t1") { 200 to """{"id":"t1"}""" }
        runBlocking { repo.setThreadStatus("t1", "active") }
        assertEquals("active", server.calls("PATCH", "/api/v2/mentoring/threads/t1").single().json()["status"]!!.jsonPrimitive.content)

        server.on("GET", "/api/v2/mentoring/mentors") { query ->
            assertEquals("pending", query["status"])
            200 to """{"items":[{"id":"mr1","userId":"u-p","name":"Paula","bio":"Ich höre zu.","status":"pending","maxMentees":2,"isAccepting":true,"activeMentees":0,"isFull":false,"email":"p@example.org"}],"nextCursor":null}"""
        }
        val mentor = runBlocking { repo.mentors("pending") }.single()
        assertEquals(listOf("mr1", "u-p", "Paula", "pending", "p@example.org"), listOf(mentor.id, mentor.user, mentor.displayName, mentor.status, mentor.userEmail))
        server.on("GET", "/api/v2/mentoring/profile") { 200 to """{"profile":null}""" }
        assertFalse(runBlocking { repo.myMentorProfile() }.exists)
        server.on("POST", "/api/v2/mentoring/application") { 201 to """{"profile":{"id":"mr2","status":"pending"}}""" }
        runBlocking { repo.saveMentorProfile(isNew = true, bio = " Gern ", maxMentees = 2, isAccepting = true) }
        val applied = server.calls("POST", "/api/v2/mentoring/application").single().json()
        assertEquals(listOf("Gern", "2"), listOf(applied["bio"], applied["maxMentees"]).map { it!!.jsonPrimitive.content })
    }

    // ---------- the media library ----------

    @Test
    fun `a folder of the media library through v2 - and the addresses of its files`() = FakeServer().use { server ->
        val repo = server.v2Repository(json)
        server.on("GET", "/api/v2/media") { 200 to """{"enabled":true,"canManage":false,"hasAccess":true}""" }
        assertTrue(runBlocking { repo.mediaStatus() }.hasAccess)
        server.on("GET", "/api/v2/media/items") { query ->
            assertEquals("Fotos", query["path"])
            200 to """{"folder":{"path":"Fotos","name":"Fotos","access":"write","canManage":false},"items":[
                {"name":"Sommer","path":"Fotos/Sommer","access":"read","id":"Fotos/Sommer","isFolder":true,"kind":"folder"},
                {"name":"a.jpg","path":"Fotos/a.jpg","size":12,"contentType":"image/jpeg","etag":"e1","hasPreview":true,"kind":"image","id":"Fotos/a.jpg","isFolder":false}],"nextCursor":null}"""
        }
        val listing = runBlocking { repo.mediaList("Fotos") }
        assertEquals(listOf("Fotos", "write"), listOf(listing.path, listing.access))
        assertEquals(listOf("Sommer"), listing.folders.map { it.name })
        assertEquals("read", listing.folders.single().access)
        val file = listing.files.single()
        assertEquals(listOf("a.jpg", "image", "e1"), listOf(file.name, file.kind, file.etag))
        assertEquals("${server.base}/api/v2/media/preview?path=Fotos%2Fa.jpg&size=256&crop=1&v=e1", repo.mediaPreviewUrl(file, 256, crop = true))
        assertEquals("/api/v2/media/file", repo.mediaFileUrl(MediaFile(path = "x.pdf"), download = true).encodedPath)
        server.on("POST", "/api/v2/media/folders") { 201 to """{"name":"Neu","path":"Fotos/Neu"}""" }
        assertEquals("Fotos/Neu", runBlocking { repo.createMediaFolder("Fotos", "Neu") }.path)
    }

    // ---------- the fee settings ----------

    @Test
    fun `the fees over time and the fresh starts through v2 - in cents there, in euros here`() = FakeServer().use { server ->
        val repo = server.v2Repository(json)
        server.on("GET", "/api/v2/fees") { 200 to FEES }
        val plan = runBlocking { repo.feePlan() }!!
        assertEquals(listOf("2026-11", "2026-03"), listOf(plan.month, plan.since))
        assertEquals(12.5, plan.current["vollverdiener"]!!, 0.0)
        assertEquals(listOf(null, "2026-03"), plan.entries.map { it.from })
        assertEquals(listOf("past", "current"), plan.entries.map { it.state })
        assertEquals(10.0, plan.entries.first().fees["vollverdiener"]!!, 0.0)
        assertEquals("2027-01", plan.next?.from)
        assertEquals(java.time.Instant.parse("2026-02-20T10:00:00.000Z").toEpochMilli(), plan.entries.last().setAt)
        assertEquals(listOf("set" to null), plan.log.map { it.action to it.from })
        assertEquals("v7", plan.version)

        server.on("POST", "/api/v2/fees/preview") {
            200 to """{"fromMonth":"2027-01","remove":false,"movedFrom":null,"stretches":[{"first":"2027-01","last":null,"beforeCents":{"vollverdiener":1250},"afterCents":{"vollverdiener":1500}}],
                "inEffect":false,"members":12,"more":{"count":10,"totalCents":2500},"less":{"count":0,"totalCents":0},"freshStart":null,"version":"v7"}"""
        }
        val impact = runBlocking { repo.previewFeeChange(FeeChange(from = "2027-01", fees = mapOf("vollverdiener" to 15.0, "geringverdiener" to 7.5))) }
        assertEquals(15.0, impact.stretches.single().after["vollverdiener"]!!, 0.0)
        assertEquals(25.0, impact.more.total, 0.0)
        val asked = server.calls("POST", "/api/v2/fees/preview").single().json()
        assertEquals(listOf(1500L, 750L), listOf(asked["feesCents"]!!.jsonObject["vollverdiener"]!!.jsonPrimitive.long, asked["feesCents"]!!.jsonObject["geringverdiener"]!!.jsonPrimitive.long))
        server.on("POST", "/api/v2/fees/remove") { 200 to FEES }
        runBlocking { repo.saveFeeChange(FeeChange(from = null, remove = true), "v7") }
        val removal = server.calls("POST", "/api/v2/fees/remove").single().json()
        assertEquals(JsonNull, removal["fromMonth"])
        assertEquals("v7", removal["version"]!!.jsonPrimitive.content)

        server.on("GET", "/api/v2/fresh-starts") {
            200 to """{"today":"2026-11-16","items":[{"day":"2026-01-01","state":"active","feesBeforeCents":{"vollverdiener":1000},"setAt":"2025-12-20T10:00:00.000Z","setByName":"Olga"}],
                "nextCursor":null,"log":[{"action":"set","day":"2026-01-01","at":"2025-12-20T10:00:00.000Z","byName":"Olga"}]}"""
        }
        val resets = runBlocking { repo.feeResets() }!!
        assertEquals(listOf("2026-01-01" to "active"), resets.resets.map { it.date to it.state })
        assertEquals(10.0, resets.resets.single().feesBefore!!["vollverdiener"]!!, 0.0)
        assertEquals("Olga", resets.log.single().byName)
        server.on("GET", "/api/v2/fresh-starts/preview") { query ->
            assertEquals(listOf("2027-01-01", "1"), listOf(query["day"], query["remove"]))
            200 to """{"day":"2027-01-01","inEffect":false,"firstMonth":"2027-01","members":12,"behind":{"count":3,"totalCents":4500},"ahead":{"count":1,"totalCents":1000},"owesMore":{"count":0,"totalCents":0}}"""
        }
        val freshImpact = runBlocking { repo.previewFeeReset("2027-01-01", remove = true) }
        assertEquals(45.0, freshImpact.behind.total, 0.0)
        assertEquals(3, freshImpact.behind.count)
        server.on("POST", "/api/v2/fresh-starts") { 200 to """{"today":"2026-11-16","items":[],"nextCursor":null}""" }
        runBlocking { repo.saveFeeReset("2027-01-01", remove = false, password = "geheim") }
        val set = server.calls("POST", "/api/v2/fresh-starts").single().json()
        assertEquals(listOf("2027-01-01", "2027-01-01", "geheim"), listOf(set["day"], set["confirmDay"], set["password"]).map { it!!.jsonPrimitive.content })
    }

    @Test
    fun `a server that answers v2 shows the fees over time also before a change of them was saved - never the fee settings`() = FakeServer().use { server ->
        // A server that listed only the changes saved - none on an instance updated from a version before the fees over
        // time: the app took it for a server without them and showed the old fee settings, which save new fees for
        // this month on without showing what they do
        val repo = server.v2Repository(json)
        server.on("GET", "/api/v2/fees") {
            200 to """{"today":"2026-11-16","month":"2026-11","currentCents":{"vollverdiener":5000,"geringverdiener":2500,"keinverdiener":1000,"pausiert":0},
                "since":null,"next":null,"feeChanges":[],"freshStarts":[],"version":"v1","log":[]}"""
        }
        val plan = checkNotNull(runBlocking { repo.feePlan() }) { "the fees over time, not the fee settings" }
        assertEquals(listOf(null to "current"), plan.entries.map { it.from to it.state })
        assertEquals(plan.current, plan.entries.single().fees)
        assertEquals(50.0, plan.entries.single().fees["vollverdiener"]!!, 0.0)
        // As a server lists them now: the fees from the beginning, as they come
        server.on("GET", "/api/v2/fees") {
            200 to """{"today":"2026-11-16","month":"2026-11","currentCents":{"vollverdiener":5000},"since":null,"next":null,
                "feeChanges":[{"fromMonth":"","feesCents":{"vollverdiener":5000},"state":"current","setAt":null,"setByName":""}],"freshStarts":[],"version":"v1","log":[]}"""
        }
        assertEquals(listOf(null to 50.0), runBlocking { repo.feePlan() }!!.entries.map { it.from to it.fees["vollverdiener"] })
        assertTrue("nothing asked the old way", server.calls("GET", "/api/fees").isEmpty())

        // A server from before the fees over time (the old API, which does not know them): the fee settings
        val old = server.repository(json, signedIn = true)
        server.on("GET", "/api/fees") { 404 to """{"error":"Not found"}""" }
        assertNull(runBlocking { old.feePlan() })
    }

    private companion object {
        const val EVENT = """{"id":"e1","title":"Grillfest","date":"2026-12-01","endDate":null,"startTime":"18:00","endTime":null,"location":"Garten","description":"",
            "kind":"termin","status":"scheduled","isPinned":false,"requiresRegistration":true,"minParticipants":0,"maxParticipants":1,"targetGroups":[],
            "createdBy":"u-max","createdByName":"Max Muster","imageUrl":null,"isPublic":true,"shareToken":"tok123","registeredCount":1,"waitlistCount":0,
            "minimumReached":null,"isFull":true,"myRegistration":{"id":"r1","status":"registered"},"canEdit":true,
            "duties":[{"id":"d1","eventId":"e1","section":"Essen","roleName":"Grill","assignedGroupId":null,"assignedUserId":null,"requestedUserId":"u-mia",
                "requestedById":"u-max","status":"requested","notes":"","assignedGroupName":"","assignedUserName":"","requestedUserName":"Mia Muster","requestedByName":"Max Muster"}],
            "program":null}"""
        const val FEES = """{"today":"2026-11-16","month":"2026-11","currentCents":{"vollverdiener":1250,"geringverdiener":600,"keinverdiener":0},"since":"2026-03",
            "next":{"fromMonth":"2027-01","feesCents":{"vollverdiener":1500}},
            "feeChanges":[{"fromMonth":"","feesCents":{"vollverdiener":1000},"state":"past","setAt":null,"setByName":""},
                {"fromMonth":"2026-03","feesCents":{"vollverdiener":1250},"state":"current","setAt":"2026-02-20T10:00:00.000Z","setByName":"Olga"}],
            "freshStarts":[],"version":"v7","log":[{"action":"set","fromMonth":null,"feesCents":{"vollverdiener":1000},"movedFrom":null,"at":"2026-01-01T10:00:00.000Z","by":"u-o","byName":"Olga"}]}"""
    }
}
