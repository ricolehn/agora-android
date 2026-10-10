package org.agora.app.ui.mentoring

import org.agora.app.ui.components.containerTransform
import org.agora.app.ui.components.pagePadding
import org.agora.app.ui.components.openFrom
import org.agora.app.ui.components.AvatarRings
import org.agora.app.ui.components.AgoraSheet
import org.agora.app.ui.components.SheetActions
import org.agora.app.ui.components.SheetToggleRow
import org.agora.app.ui.components.rememberSheetController
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.School
import org.agora.app.ui.components.PrimaryButton
import org.agora.app.ui.components.SecondaryButton
import org.agora.app.ui.components.AgoraTextField
import org.agora.app.ui.components.AgoraSwitch
import org.agora.app.ui.components.ButtonLabel
import org.agora.app.ui.components.PillTabs
import org.agora.app.ui.components.SearchField
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.agora.app.R
import org.agora.app.data.model.Mentor
import org.agora.app.data.model.User
import org.agora.app.ui.components.AgoraCard
import org.agora.app.ui.components.ButtonProgress
import org.agora.app.ui.components.DropdownField
import org.agora.app.ui.components.EmptyState
import org.agora.app.ui.components.LoadingBox
import org.agora.app.ui.components.LocalContainer
import org.agora.app.ui.components.Pill
import org.agora.app.ui.components.UserAvatar
import org.agora.app.ui.components.rememberActionRunner
import org.agora.app.ui.theme.Agora
import org.agora.app.util.Dates

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MentoringScreen(user: User, contentPadding: PaddingValues, onOpenThread: (String) -> Unit) {
    val container = LocalContainer.current
    val store = container.store
    val data by store.data.collectAsStateWithLifecycle()
    val refreshing by store.refreshing.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var profileSheet by rememberSaveable { mutableStateOf(false) }
    var mentors by remember { mutableStateOf<List<Mentor>?>(null) }
    var mentorsKey by remember { mutableIntStateOf(0) }
    var reviewFilter by rememberSaveable { mutableStateOf("pending") }
    var review by remember { mutableStateOf<List<Mentor>?>(null) }
    var contactMentor by remember { mutableStateOf<Mentor?>(null) }
    var filter by rememberSaveable { mutableStateOf("") }
    val profile = data.mentorProfile
    val isManager = user.managesMentoring
    val locale = Dates.locale(LocalContext.current)

    LaunchedEffect(tab, mentorsKey) {
        if (tab == 1) mentors = runCatching { container.repo.mentors() }.getOrDefault(emptyList())
    }
    LaunchedEffect(tab, reviewFilter, mentorsKey) {
        if (tab == 2) review = runCatching { container.repo.mentors(reviewFilter) }.getOrDefault(emptyList())
    }
    LaunchedEffect(Unit) { store.serverChanges.collect { areas -> if ("all" in areas || "mentoring" in areas) mentorsKey++ } }

    PullToRefreshBox(isRefreshing = refreshing, onRefresh = { store.refreshInBackground(true); mentorsKey++ }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = pagePadding(contentPadding.calculateTopPadding() + 4.dp, contentPadding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // `.mentoring-header-card`: centred heading, subtitle and one call to action
            item {
                AgoraCard(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp)) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.mentoring_title), style = MaterialTheme.typography.headlineMedium, color = Agora.colors.heading)
                        Text(stringResource(R.string.mentoring_subtitle), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
                        Spacer(Modifier.height(18.dp))
                        val status = profile.mentor?.status
                        val wide = Modifier.fillMaxWidth(0.85f)
                        when {
                            profile.exists && status == "pending" -> SecondaryButton(onClick = { profileSheet = true }, modifier = wide, shape = CircleShape) {
                                ButtonLabel(stringResource(R.string.mentoring_application_pending), Icons.Outlined.HourglassTop)
                            }
                            profile.exists && status == "approved" -> SecondaryButton(onClick = { profileSheet = true }, modifier = wide, shape = CircleShape) {
                                ButtonLabel(stringResource(R.string.mentoring_my_profile), Icons.Outlined.Verified)
                            }
                            else -> PrimaryButton(onClick = { profileSheet = true }, modifier = wide, shape = CircleShape) {
                                ButtonLabel(stringResource(R.string.mentoring_apply_btn))
                            }
                        }
                    }
                }
            }
            item {
                val unread = data.unreadThreads.size
                PillTabs(
                    buildList {
                        add((stringResource(R.string.mentoring_tab_chats) + if (unread > 0) " ($unread)" else "") to null)
                        add(stringResource(R.string.mentoring_tab_find) to null)
                        if (isManager) add(stringResource(R.string.mentoring_tab_review) to null)
                    },
                    selected = tab, onSelect = { tab = it }
                )
            }
            when (tab) {
                0 -> {
                    item { SearchField(filter, { filter = it }, stringResource(R.string.filter_contacts)) }
                    val threads = data.threads.filter { filter.isBlank() || it.partnerName.contains(filter.trim(), ignoreCase = true) }
                    if (threads.isEmpty()) item {
                        EmptyState(Icons.Outlined.Forum, stringResource(R.string.mentoring_no_threads), stringResource(R.string.mentoring_no_threads_hint))
                    }
                    items(threads, key = { "th-${it.id}" }) { thread ->
                        AgoraCard(Modifier.containerTransform("chat-${thread.id}"), onClick = openFrom("chat-${thread.id}") { onOpenThread(thread.id) }, contentPadding = PaddingValues(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                UserAvatar(if (thread.myRole == "mentee") thread.mentor else null, thread.partnerName, 44.dp)
                                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(thread.partnerName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            fontWeight = if (thread.unreadCount > 0) FontWeight.ExtraBold else FontWeight.SemiBold, modifier = Modifier.weight(1f, fill = false))
                                        if (thread.isClosed) Icon(Icons.Outlined.Lock, null, Modifier.padding(start = 4.dp).size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text(
                                        thread.lastMessage?.text?.ifBlank { null } ?: stringResource(if (thread.myRole == "mentor") R.string.mentoring_role_seeker else R.string.mentoring_role_mentor),
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(Dates.chatTime(thread.lastMessage?.created ?: thread.updated, locale), style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (thread.unreadCount > 0) Badge(containerColor = Agora.colors.success) { Text("${thread.unreadCount}") }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    val list = mentors
                    if (list == null) item { LoadingBox() }
                    else if (list.isEmpty()) item { EmptyState(Icons.Outlined.PersonSearch, stringResource(R.string.mentoring_no_mentors)) }
                    else items(list, key = { "m-${it.id}" }) { mentor ->
                        val existingThread = data.threads.firstOrNull { it.mentor == mentor.user && it.myRole == "mentee" }
                        MentorCard(
                            mentor, user,
                            showCapacity = mentor.user == user.userId || isManager,
                            action = {
                                when {
                                    mentor.user == user.userId -> SecondaryButton(onClick = { profileSheet = true }) { Text(stringResource(R.string.mentoring_edit_profile)) }
                                    existingThread != null -> SecondaryButton(onClick = { onOpenThread(existingThread.id) }) {
                                        Icon(Icons.Outlined.ChatBubbleOutline, null, Modifier.size(18.dp))
                                        Text(" " + stringResource(R.string.mentoring_to_chat))
                                    }
                                    mentor.isFull || !mentor.isAccepting -> SecondaryButton(onClick = {}, enabled = false) { Text(stringResource(R.string.mentoring_full)) }
                                    else -> PrimaryButton(onClick = { contactMentor = mentor }) { Text(stringResource(R.string.mentoring_contact_anonymous)) }
                                }
                            }
                        )
                    }
                }
                2 -> {
                    item {
                        DropdownField(stringResource(R.string.filter), listOf("pending", "approved", "rejected", "all"), reviewFilter, {
                            stringResource(when (it) {
                                "approved" -> R.string.mentor_status_approved
                                "rejected" -> R.string.mentor_status_rejected
                                "all" -> R.string.all
                                else -> R.string.mentor_status_pending
                            })
                        }, { reviewFilter = it })
                    }
                    val list = review
                    if (list == null) item { LoadingBox() }
                    else if (list.isEmpty()) item { EmptyState(Icons.Outlined.PersonSearch, stringResource(R.string.mentoring_no_applications)) }
                    else items(list, key = { "r-${it.id}" }) { mentor -> ReviewCard(mentor, user) { mentorsKey++ } }
                }
            }
        }
    }

    if (profileSheet) MentorProfileSheet(onDismiss = { profileSheet = false })
    contactMentor?.let { mentor ->
        ContactMentorSheet(mentor, onDismiss = { contactMentor = null }, onCreated = { threadId ->
            contactMentor = null
            onOpenThread(threadId)
        })
    }
}

@Composable
private fun MentorCard(mentor: Mentor, user: User, showCapacity: Boolean, action: @Composable () -> Unit) {
    AgoraCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            UserAvatar(mentor.user, mentor.displayName, 52.dp, ring = if (mentor.status == "approved") AvatarRings.Mentor else null)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(mentor.displayName, style = MaterialTheme.typography.titleMedium)
                if (mentor.status == "approved") Pill(stringResource(R.string.mentoring_verified), Agora.colors.success, icon = Icons.Outlined.Verified)
            }
        }
        if (mentor.bio.isNotBlank()) Text(mentor.bio, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 10.dp))
        if (showCapacity) Text(
            stringResource(R.string.mentoring_capacity, mentor.activeMentees, mentor.maxMentees),
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp)
        )
        Spacer(Modifier.height(10.dp))
        action()
    }
}

@Composable
private fun ReviewCard(mentor: Mentor, user: User, onChanged: () -> Unit) {
    val container = LocalContainer.current
    val runner = rememberActionRunner()
    AgoraCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            UserAvatar(mentor.user, mentor.displayName, 44.dp)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(mentor.displayName, style = MaterialTheme.typography.titleSmall)
                mentor.userEmail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Pill(stringResource(when (mentor.status) {
                "approved" -> R.string.mentor_status_approved
                "rejected" -> R.string.mentor_status_rejected
                else -> R.string.mentor_status_pending
            }), when (mentor.status) { "approved" -> Agora.colors.success; "rejected" -> Agora.colors.danger; else -> Agora.colors.warning })
        }
        if (mentor.bio.isNotBlank()) Text(mentor.bio, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (mentor.status != "approved") SecondaryButton(onClick = {
                runner.run { container.repo.setMentorStatus(mentor.id, "approved"); onChanged() }
            }, enabled = !runner.busy, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.approve)) }
            if (mentor.status != "rejected") SecondaryButton(onClick = {
                runner.run { container.repo.setMentorStatus(mentor.id, "rejected"); onChanged() }
            }, enabled = !runner.busy, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.reject)) }
        }
    }
}

@Composable
private fun ContactMentorSheet(mentor: Mentor, onDismiss: () -> Unit, onCreated: (String) -> Unit) {
    val container = LocalContainer.current
    val runner = rememberActionRunner()
    val sheet = rememberSheetController(onDismiss)
    var message by rememberSaveable { mutableStateOf("") }
    AgoraSheet(
        title = stringResource(R.string.mentoring_contact_title),
        subtitle = mentor.displayName,
        icon = Icons.AutoMirrored.Outlined.Chat,
        onDismiss = onDismiss,
        controller = sheet,
        actions = {
            SheetActions(
                confirmLabel = stringResource(R.string.send),
                confirmIcon = Icons.AutoMirrored.Outlined.Send,
                onConfirm = {
                    runner.run {
                        val threadId = container.repo.contactMentor(mentor.user, message.trim())
                        container.store.refreshAll()
                        onCreated(threadId)
                    }
                },
                onCancel = { sheet.dismiss() },
                enabled = message.isNotBlank(),
                busy = runner.busy
            )
        }
    ) {
        Text(stringResource(R.string.mentoring_contact_hint, mentor.displayName), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        AgoraTextField(message, { message = it }, label = { Text(stringResource(R.string.mentoring_first_message)) }, minLines = 4, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun MentorProfileSheet(onDismiss: () -> Unit) {
    val container = LocalContainer.current
    val data by container.store.data.collectAsStateWithLifecycle()
    val existing = data.mentorProfile.mentor.takeIf { data.mentorProfile.exists }
    val runner = rememberActionRunner()
    val sheet = rememberSheetController(onDismiss)
    var bio by rememberSaveable { mutableStateOf(existing?.bio ?: "") }
    var max by rememberSaveable { mutableStateOf((existing?.maxMentees ?: 3).toString()) }
    var accepting by rememberSaveable { mutableStateOf(existing?.isAccepting ?: true) }
    val savedMsg = stringResource(if (existing == null) R.string.mentoring_application_sent else R.string.saved)
    AgoraSheet(
        title = stringResource(if (existing == null) R.string.mentoring_apply_btn else R.string.mentoring_my_profile),
        icon = Icons.Outlined.School,
        onDismiss = onDismiss,
        controller = sheet,
        actions = {
            SheetActions(
                confirmLabel = stringResource(if (existing == null) R.string.mentoring_submit_application else R.string.save),
                onConfirm = {
                    runner.run(savedMsg) {
                        container.repo.saveMentorProfile(existing == null, bio, (max.toIntOrNull() ?: 3).coerceAtLeast(1), accepting)
                        container.store.refreshAll()
                        sheet.dismiss()
                    }
                },
                onCancel = { sheet.dismiss() },
                enabled = bio.isNotBlank(),
                busy = runner.busy
            )
        }
    ) {
        AgoraTextField(bio, { bio = it }, label = { Text(stringResource(R.string.mentoring_bio)) }, minLines = 4, modifier = Modifier.fillMaxWidth())
        AgoraTextField(max, { max = it.filter(Char::isDigit).take(2) }, label = { Text(stringResource(R.string.mentoring_capacity_label)) },
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
        if (existing != null) SheetToggleRow(stringResource(R.string.mentoring_accepting), accepting, { accepting = it })
    }
}
