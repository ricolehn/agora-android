package org.agora.app.ui.mentoring

import androidx.compose.ui.graphics.RectangleShape
import org.agora.app.ui.components.softShadow
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Flag
import org.agora.app.ui.components.SecondaryButton
import org.agora.app.ui.components.AgoraTextField
import org.agora.app.ui.components.AgoraSnackbar
import org.agora.app.ui.components.SendButton
import org.agora.app.ui.components.SubPageHeader
import org.agora.app.ui.theme.Agora
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import org.agora.app.R
import org.agora.app.data.model.ChatMessage
import org.agora.app.ui.components.LoadingBox
import org.agora.app.ui.components.LocalContainer
import org.agora.app.ui.components.LocalSnackbar
import org.agora.app.ui.components.UserAvatar
import org.agora.app.ui.components.rememberActionRunner
import org.agora.app.util.Dates

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(threadId: String, onBack: () -> Unit) {
    val container = LocalContainer.current
    val store = container.store
    val data by store.data.collectAsStateWithLifecycle()
    val thread = data.threads.firstOrNull { it.id == threadId }
    var messages by remember { mutableStateOf<List<ChatMessage>?>(null) }
    var text by rememberSaveable { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var reporting by remember { mutableStateOf(false) }
    var blocking by remember { mutableStateOf(false) }
    val reportedMsg = stringResource(R.string.report_sent)
    val blockedMsg = stringResource(R.string.mentoring_toast_blocked)
    var polling by remember { mutableStateOf(false) }
    val runner = rememberActionRunner()
    val listState = rememberLazyListState()
    val locale = Dates.locale(LocalContext.current)

    // Poll while visible, like the PWA's open chat (3.5 s); opening marks messages as read
    LifecycleResumeEffect(threadId) {
        polling = true
        onPauseOrDispose { polling = false }
    }
    LaunchedEffect(threadId, polling) {
        var first = true
        while (polling) {
            val fresh = runCatching { container.repo.messages(threadId) }.getOrNull()
            if (fresh != null) {
                val changed = fresh.size != messages?.size
                messages = fresh
                if (first || changed) store.refreshInBackground()
            }
            first = false
            delay(3_500)
        }
    }
    LaunchedEffect(messages?.size) {
        val count = messages?.size ?: 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    Scaffold(
        topBar = {
            SubPageHeader(
                title = thread?.partnerName ?: "",
                onBack = onBack,
                subtitle = stringResource(if (thread?.myRole == "mentor") R.string.mentoring_role_seeker else R.string.mentoring_role_mentor),
                leading = { UserAvatar(if (thread?.myRole == "mentee") thread.mentor else null, thread?.partnerName ?: "", 40.dp) }
            ) {
                if (thread != null) Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, null) }
                    DropdownMenu(menu, { menu = false }, shape = RoundedCornerShape(16.dp), containerColor = MaterialTheme.colorScheme.surface) {
                        // Report and block (Google Play user-generated content policy)
                        DropdownMenuItem(text = { Text(stringResource(R.string.mentoring_chat_report)) },
                            leadingIcon = { Icon(Icons.Outlined.Flag, null) },
                            onClick = {
                                menu = false
                                reporting = true
                            })
                        if (!thread.blocked) DropdownMenuItem(text = { Text(stringResource(R.string.mentoring_chat_block), color = Agora.colors.danger) },
                            leadingIcon = { Icon(Icons.Outlined.Block, null, tint = Agora.colors.danger) },
                            onClick = {
                                menu = false
                                blocking = true
                            })
                        if (!thread.isClosed) DropdownMenuItem(text = { Text(stringResource(R.string.mentoring_chat_end), color = Agora.colors.danger, fontWeight = FontWeight.Bold) },
                            leadingIcon = { Icon(Icons.Outlined.Lock, null, tint = Agora.colors.danger) },
                            onClick = {
                                menu = false
                                runner.run { container.repo.setThreadStatus(threadId, "closed"); store.refreshAll() }
                            })
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(LocalSnackbar.current) { AgoraSnackbar(it) } },
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.softShadow(Color.Black.copy(alpha = 0.07f), blur = 16.dp, shape = RectangleShape, offsetY = (-4).dp)) {
                if (thread?.isClosed == true) Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(if (thread.blocked) Icons.Outlined.Block else Icons.Outlined.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        stringResource(when {
                            thread.blockedByMe -> R.string.mentoring_chat_blocked_by_me
                            thread.blocked -> R.string.mentoring_chat_blocked
                            else -> R.string.mentoring_chat_closed
                        }),
                        Modifier.weight(1f).padding(horizontal = 10.dp), color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // A block can only be lifted by the person who blocked
                    if (!thread.blocked || thread.blockedByMe) SecondaryButton(onClick = { runner.run { container.repo.setThreadStatus(threadId, "active"); store.refreshAll() } }, enabled = !runner.busy) {
                        Text(stringResource(R.string.mentoring_chat_reopen))
                    }
                } else Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AgoraTextField(
                        value = text, onValueChange = { text = it },
                        placeholder = { Text(stringResource(R.string.mentoring_input_placeholder)) },
                        maxLines = 5, shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.weight(1f)
                    )
                    SendButton(
                        enabled = text.isNotBlank() && !runner.busy,
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        val body = text.trim()
                        if (body.isNotEmpty()) runner.run {
                            container.repo.sendMessage(threadId, body)
                            text = ""
                            messages = container.repo.messages(threadId)
                            store.refreshInBackground()
                        }
                    }
                }
            }
        }
    ) { padding ->
        val list = messages
        if (list == null) LoadingBox(Modifier.padding(padding))
        else LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(12.dp, padding.calculateTopPadding() + 8.dp, 12.dp, padding.calculateBottomPadding() + 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (list.isEmpty()) item {
                Text(stringResource(R.string.mentoring_chat_empty), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp))
            }
            items(list, key = { it.id }) { message ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.isMine) Arrangement.End else Arrangement.Start) {
                    // Own messages in the brand gradient, partner messages as white bubbles (PWA chat)
                    val shape = RoundedCornerShape(18.dp, 18.dp, if (message.isMine) 4.dp else 18.dp, if (message.isMine) 18.dp else 4.dp)
                    Column(
                        Modifier
                            .widthIn(max = 300.dp)
                            .softShadow(Color(0xFF0F172A).copy(alpha = 0.10f), blur = 4.dp, shape = shape, offsetY = 1.dp)
                            .clip(shape)
                            .then(if (message.isMine) Modifier.background(Agora.colors.brandGradient) else Modifier.background(MaterialTheme.colorScheme.surface))
                            .padding(horizontal = 14.dp, vertical = 9.dp)
                    ) {
                        Text(message.text, style = MaterialTheme.typography.bodyLarge, color = if (message.isMine) Color.White else MaterialTheme.colorScheme.onSurface)
                        Text(
                            Dates.chatTime(message.created, locale),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.align(Alignment.End),
                            color = if (message.isMine) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (reporting && thread != null) org.agora.app.ui.components.TextInputSheet(
        title = stringResource(R.string.mentoring_chat_report),
        subtitle = stringResource(R.string.mentoring_report_hint),
        label = stringResource(R.string.report_reason_optional),
        confirmLabel = stringResource(R.string.report_send),
        icon = Icons.Outlined.Flag,
        iconTint = Agora.colors.danger,
        onDismiss = { reporting = false },
        onConfirm = { reason ->
            // The partner's latest messages go to the admins as evidence
            val partner = messages.orEmpty().filter { !it.isMine }.takeLast(10).joinToString("\n---\n") { it.text }.ifBlank { "(keine Nachrichten)" }
            runner.run(reportedMsg) { container.repo.report("chat", partner, reason.trim(), threadId = threadId) }
        }
    )
    if (blocking) org.agora.app.ui.components.ConfirmSheet(
        title = stringResource(R.string.mentoring_chat_block),
        text = stringResource(R.string.mentoring_confirm_block),
        confirmLabel = stringResource(R.string.mentoring_chat_block),
        destructive = true,
        icon = Icons.Outlined.Block,
        onConfirm = { runner.run(blockedMsg) { container.repo.setThreadStatus(threadId, "blocked"); store.refreshAll() } },
        onDismiss = { blocking = false }
    )
}
