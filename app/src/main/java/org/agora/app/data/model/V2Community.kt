package org.agora.app.data.model

import kotlinx.serialization.Serializable
import org.agora.app.data.Prayer
import org.agora.app.data.PrayerAuthor
import org.agora.app.data.Song

// The community through API v2 (docs/API_V2.md, "Community"): the songbook, prayer requests and mentoring, read into
// the app's own models. Moments come as ISO 8601; a time the old API kept as it was stored passes through as it is.

/** A song of the songbook (ChordPro text). */
@Serializable
data class V2Song(
    val id: String = "",
    val title: String = "",
    val artist: String = "",
    val key: String? = null,
    val capo: Int? = null,
    val tempo: Int? = null,
    val ccli: String? = null,
    val copyright: String = "",
    val content: String = "",
    val createdAt: String? = null,
    val updatedAt: String? = null
)

/** A prayer request: shared anonymously, it names nobody - not even to the leaders. */
@Serializable
data class V2Prayer(
    val id: String = "",
    val text: String = "",
    val isAnonymous: Boolean = false,
    val authorUserId: String? = null,
    val authorName: String? = null,
    val createdAt: String? = null,
    val expiresAt: String? = null,
    val isExpired: Boolean = false,
    val isMine: Boolean = false,
    val canDelete: Boolean = false
)

/** A mentor (those who manage mentoring also see the applications and the e-mail addresses). */
@Serializable
data class V2Mentor(
    val id: String = "",
    val userId: String = "",
    val name: String = "",
    val bio: String = "",
    val status: String? = null,
    val maxMentees: Int = 0,
    val isAccepting: Boolean = true,
    val activeMentees: Int = 0,
    val isFull: Boolean = false,
    val email: String? = null
)

@Serializable
data class V2MentorProfile(
    val id: String = "",
    val status: String? = null,
    val bio: String = "",
    val maxMentees: Int = 3,
    val isAccepting: Boolean = true
)

/** The person's own profile as a mentor; null: they never applied. */
@Serializable
data class V2MentorProfileAnswer(val profile: V2MentorProfile? = null)

/** A conversation as the reader sees it: the mentee stays anonymous to the mentor (an alias). */
@Serializable
data class V2Thread(
    val id: String = "",
    /** mentor or mentee: the reader's part in it */
    val role: String = "mentee",
    val mentorUserId: String = "",
    val menteeAlias: String? = null,
    val status: String = "active",
    val lastMessage: String = "",
    val updatedAt: String? = null,
    val title: String = "",
    val mentorName: String = "",
    val isClosed: Boolean = false,
    val isBlocked: Boolean = false,
    val isBlockedByMe: Boolean = false,
    val unreadCount: Int = 0,
    val lastMessageAt: String? = null
)

/** A message: [from] mentor or mentee. */
@Serializable
data class V2Message(
    val id: String = "",
    val from: String = "",
    val text: String = "",
    val isRead: Boolean = false,
    val createdAt: String? = null
)

fun V2Song.toSong(): Song = Song(
    id = id,
    title = title,
    artist = artist,
    key = key.orEmpty(),
    capo = capo,
    tempo = tempo,
    ccli = ccli.orEmpty(),
    copyright = copyright,
    content = content,
    createdAt = millisOf(createdAt),
    updatedAt = millisOf(updatedAt)
)

fun V2Prayer.toPrayer(): Prayer = Prayer(
    id = id,
    text = text,
    anonymous = isAnonymous,
    author = if (authorUserId == null && authorName == null) null else PrayerAuthor(authorUserId, authorName),
    createdAt = millisOf(createdAt),
    expiresAt = millisOf(expiresAt),
    expired = isExpired,
    isMine = isMine,
    canDelete = canDelete
)

fun V2Mentor.toMentor(): Mentor = Mentor(
    id = id,
    user = userId,
    name = name,
    status = status.orEmpty(),
    bio = bio,
    maxMentees = maxMentees,
    activeMentees = activeMentees,
    isFull = isFull,
    isAccepting = isAccepting,
    userEmail = email
)

fun V2MentorProfileAnswer.toMyProfile(): MyMentorProfile = MyMentorProfile(
    exists = profile != null,
    mentor = profile?.let { MentorProfile(it.id, it.status.orEmpty(), it.bio, it.maxMentees, it.isAccepting) }
)

fun V2Thread.toThread(): MentoringThread = MentoringThread(
    id = id,
    mentor = mentorUserId,
    status = status,
    updated = updatedAt.orEmpty(),
    unreadCount = unreadCount,
    // The last message as the old API named it; none yet: the list shows that it is new
    lastMessage = if (lastMessage.isBlank() && lastMessageAt == null) null else LastMessage(lastMessage, lastMessageAt.orEmpty()),
    myRole = role,
    mentorName = mentorName,
    menteeAlias = menteeAlias.orEmpty(),
    title = title,
    blocked = isBlocked,
    blockedByMe = isBlockedByMe
)

/** A message of the conversation [threadId] read by one in the part [myRole]: theirs, or the partner's. */
fun V2Message.toChatMessage(threadId: String, myRole: String): ChatMessage = ChatMessage(
    id = id,
    thread = threadId,
    senderRole = from,
    sender = if (from == myRole) "me" else "partner",
    text = text,
    read = isRead,
    created = createdAt.orEmpty()
)
