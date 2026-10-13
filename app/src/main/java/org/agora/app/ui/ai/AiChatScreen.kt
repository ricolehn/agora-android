package org.agora.app.ui.ai

import androidx.compose.ui.layout.onSizeChanged
import org.agora.app.ui.components.frostSurface
import org.agora.app.ui.components.frostSource
import dev.chrisbanes.haze.rememberHazeState
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.IconButton
import org.agora.app.ui.components.frosted
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedButton
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.WindowInsets
import org.agora.app.ui.components.softShadow
import org.agora.app.ui.components.rememberActionRunner
import org.agora.app.ui.components.LocalContainer
import androidx.compose.runtime.remember
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.outlined.Flag
import org.agora.app.ui.components.SecondaryButton
import org.agora.app.ui.components.AgoraTextField
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.foundation.background
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mikepenz.markdown.m3.Markdown
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.agora.app.AppContainer
import org.agora.app.R
import org.agora.app.data.model.AiMessage
import org.agora.app.data.model.User
import org.agora.app.data.remote.AiChunk
import org.agora.app.ui.components.EmptyState
import org.agora.app.ui.components.containerViewModel
import org.agora.app.ui.theme.Agora

data class AiEntry(val role: String, val content: String, val reasoning: String = "", val streaming: Boolean = false, val error: Boolean = false)

/** Separates inline `<think>…</think>` / `<thought>…</thought>` blocks from the visible answer. */
fun splitThinking(content: String): Pair<String, String> {
    val pattern = Regex("<(think|thought)>([\\s\\S]*?)(</\\1>|$)")
    val thinking = pattern.findAll(content).joinToString("\n") { it.groupValues[2].trim() }
    return content.replace(pattern, "").trim() to thinking
}

/** Conversation lives only in memory (like the PWA); the server is stateless. */
class AiChatViewModel(private val c: AppContainer) : ViewModel() {
    private val _entries = MutableStateFlow<List<AiEntry>>(emptyList())
    val entries = _entries.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private var job: Job? = null

    /** Returns the text to put back into the input if sending failed. */
    fun send(text: String, onFailed: (String) -> Unit) {
        if (_busy.value || text.isBlank()) return
        val history = _entries.value.filter { !it.error } + AiEntry("user", text.trim())
        _entries.value = history + AiEntry("assistant", "", streaming = true)
        _busy.value = true
        job = viewModelScope.launch {
            try {
                c.repo.aiChat(history.map { AiMessage(it.role, it.content) }).collect { chunk ->
                    _entries.update { list ->
                        val last = list.lastOrNull() ?: return@update list
                        list.dropLast(1) + when (chunk) {
                            is AiChunk.Content -> last.copy(content = last.content + chunk.text)
                            is AiChunk.Reasoning -> last.copy(reasoning = last.reasoning + chunk.text)
                        }
                    }
                }
                _entries.update { list -> if (list.isEmpty()) list else list.dropLast(1) + list.last().copy(streaming = false) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // Drop the failed question (it goes back into the input) and show the error
                _entries.update { list -> list.dropLast(2) + AiEntry("assistant", e.message ?: "", error = true) }
                onFailed(text)
            } finally {
                _busy.value = false
            }
        }
    }

    fun clear() {
        job?.cancel()
        _entries.value = emptyList()
        _busy.value = false
    }
}

@Composable
fun AiChatScreen(user: User, contentPadding: PaddingValues, onBack: (() -> Unit)? = null) {
    val vm = containerViewModel { AiChatViewModel(it) }
    val entries by vm.entries.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val container = LocalContainer.current
    val runner = rememberActionRunner()
    var reporting by remember { mutableStateOf<Int?>(null) }
    val reportedMsg = stringResource(R.string.report_sent)
    val lastLength = entries.lastOrNull()?.let { it.content.length + it.reasoning.length } ?: 0

    LaunchedEffect(entries.size, lastLength / 200) {
        if (entries.isNotEmpty()) listState.animateScrollToItem(entries.size - 1)
    }

    // The keyboard covers the bottom navigation, so the input needs the larger of the two heights - not their
    // sum, which left it floating above the keyboard by the height of the navigation bar
    val density = LocalDensity.current
    val keyboard = with(density) { WindowInsets.ime.getBottom(density).toDp() }
    Column(
        Modifier
            .fillMaxSize()
            .padding(top = contentPadding.calculateTopPadding(), bottom = maxOf(contentPadding.calculateBottomPadding(), keyboard))
    ) {
        // Title and "New chat" as a quiet M3 button (web: .ai-chat-header-styled, .ai-new-chat-btn)
        Row(Modifier.fillMaxWidth().padding(start = if (onBack != null) 8.dp else 20.dp, end = 16.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            // Back to Extras (the chat is one of its apps)
            if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back), tint = Agora.colors.heading) }
            Text(stringResource(R.string.nav_ai), style = MaterialTheme.typography.titleLarge, color = Agora.colors.heading, modifier = Modifier.weight(1f))
            OutlinedButton(
                onClick = vm::clear, enabled = entries.isNotEmpty(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                border = BorderStroke(1.dp, Agora.colors.hairline)
            ) {
                Icon(Icons.Outlined.Edit, null, Modifier.size(16.dp))
                Text(stringResource(R.string.ai_new_chat), modifier = Modifier.padding(start = 6.dp))
            }
        }
        // The messages run on under the input bar, which blurs them (web: frosted .ai-chat-input-styled)
        val frost = rememberHazeState()
        var inputHeight by remember { mutableStateOf(0.dp) }
        Box(Modifier.weight(1f)) {
          Box(Modifier.fillMaxSize().frostSource(frost).background(MaterialTheme.colorScheme.background)) {
            if (entries.isEmpty()) AiWelcome(
                hint = stringResource(if (user.canViewFinances) R.string.ai_chat_hint_finance_v2 else R.string.ai_chat_hint_member_v2),
                ideas = if (user.canViewFinances) listOf(R.string.ai_suggest_admin_1, R.string.ai_suggest_admin_2, R.string.ai_suggest_admin_3)
                else listOf(R.string.ai_suggest_member_1, R.string.ai_suggest_member_2, R.string.ai_suggest_member_3),
                enabled = !busy,
                modifier = Modifier.align(Alignment.Center).padding(bottom = inputHeight)
            ) { idea -> vm.send(idea) { failed -> input = failed } }
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 8.dp + inputHeight),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(entries) { index, entry ->
                    val reportable = entry.role != "user" && !entry.error && !entry.streaming && entry.content.isNotBlank()
                    AiBubble(entry, onReport = if (reportable) ({ reporting = index }) else null)
                }
            }
          }
        Surface(
            color = Color.Transparent,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .onSizeChanged { inputHeight = with(density) { it.height.toDp() } }
                .frosted(frost, tint = frostSurface())
        ) {
          Column {
            // Transparency for AI-generated content (Google Play AI policy)
            Text(
                stringResource(R.string.ai_disclaimer), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 6.dp)
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                AgoraTextField(
                    value = input, onValueChange = { input = it },
                    placeholder = { Text(stringResource(R.string.ai_input_placeholder)) },
                    maxLines = 6, shape = RoundedCornerShape(24.dp), modifier = Modifier.weight(1f)
                )
                org.agora.app.ui.components.SendButton(enabled = input.isNotBlank() && !busy, modifier = Modifier.padding(start = 8.dp)) {
                    val text = input
                    input = ""
                    vm.send(text) { failed -> input = failed }
                }
            }
          }
        }
        }
    }

    reporting?.let { index ->
        val entry = entries.getOrNull(index)
        if (entry == null) reporting = null
        else org.agora.app.ui.components.TextInputSheet(
            title = stringResource(R.string.ai_report),
            subtitle = stringResource(R.string.ai_report_hint),
            label = stringResource(R.string.report_reason_optional),
            confirmLabel = stringResource(R.string.report_send),
            icon = Icons.Outlined.Flag,
            iconTint = org.agora.app.ui.theme.Agora.colors.danger,
            onDismiss = { reporting = null },
            onConfirm = { reason ->
                val prompt = entries.take(index).lastOrNull { it.role == "user" }?.content.orEmpty()
                val answer = splitThinking(entry.content).first
                runner.run(reportedMsg) { container.repo.report("ai", answer, reason.trim(), prompt = prompt) }
            }
        )
    }
}

/** A new chat: a friendly start with three questions the assistant can answer (web: .ai-chat-welcome). */
@Composable
private fun AiWelcome(hint: String, ideas: List<Int>, enabled: Boolean, modifier: Modifier = Modifier, onAsk: (String) -> Unit) {
    Column(modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val tile = RoundedCornerShape(22.dp)
        Box(
            Modifier.size(64.dp)
                .softShadow(Agora.colors.shadow.copy(alpha = 0.08f), blur = 6.dp, shape = tile, offsetY = 2.dp)
                .clip(tile)
                .background(MaterialTheme.colorScheme.surface)
                .background(Brush.linearGradient(listOf(Color(0xFFA78BFA).copy(alpha = 0.20f), Color.Transparent, Agora.colors.brand.copy(alpha = 0.22f))))
                .border(1.dp, Agora.colors.hairline, tile),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary) }
        Text(stringResource(R.string.ai_chat_hello), style = MaterialTheme.typography.headlineMedium, color = Agora.colors.heading,
            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 14.dp))
        Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp).widthIn(max = 340.dp))
        Column(Modifier.padding(top = 18.dp).widthIn(max = 400.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ideas.forEach { id ->
                val idea = stringResource(id)
                OutlinedCard(
                    onClick = { onAsk(idea) }, enabled = enabled, shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, Agora.colors.hairline), modifier = Modifier.fillMaxWidth()
                ) { Text(idea, style = MaterialTheme.typography.bodyMedium, color = Agora.colors.heading, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) }
            }
        }
    }
}

@Composable
private fun AiBubble(entry: AiEntry, onReport: (() -> Unit)? = null) {
    val mine = entry.role == "user"
    if (mine || entry.error) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
            val shape = RoundedCornerShape(20.dp, 20.dp, if (mine) 6.dp else 20.dp, 20.dp)
            Surface(
                color = if (entry.error) MaterialTheme.colorScheme.errorContainer else Agora.colors.brand.copy(alpha = 0.13f).compositeOver(MaterialTheme.colorScheme.surface),
                contentColor = if (entry.error) MaterialTheme.colorScheme.onErrorContainer else Agora.colors.heading,
                shape = shape,
                border = if (entry.error) null else BorderStroke(1.dp, Agora.colors.brand.copy(alpha = 0.16f)),
                modifier = Modifier.widthIn(max = 320.dp)
            ) {
                Text(
                    if (entry.error) stringResource(R.string.ai_error, entry.content) else entry.content,
                    style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 15.dp, vertical = 10.dp)
                )
            }
        }
        return
    }
    // The reply: plain text beside a small sparkle, like modern assistants
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.padding(top = 1.dp).size(28.dp).clip(CircleShape).background(Agora.colors.brand.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary) }
        Column(Modifier.weight(1f).widthIn(max = 640.dp).padding(start = 10.dp, top = 3.dp).animateContentSize()) {
            val (visible, inlineThinking) = splitThinking(entry.content)
            val thinking = listOf(entry.reasoning.trim(), inlineThinking).filter { it.isNotBlank() }.joinToString("\n\n")
            if (thinking.isNotBlank()) ThinkingSection(thinking)
            if (visible.isBlank() && entry.streaming) LinearProgressIndicator(Modifier.widthIn(min = 60.dp, max = 120.dp).padding(vertical = 8.dp))
            else SelectionContainer { org.agora.app.ui.components.AgoraMarkdown(visible) }
            if (onReport != null) Row(
                Modifier.padding(top = 8.dp).clip(RoundedCornerShape(50)).border(1.dp, Agora.colors.hairline, RoundedCornerShape(50))
                    .clickable(onClick = onReport).padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Flag, null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.ai_report), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
            }
        }
    }
}

@Composable
private fun ThinkingSection(text: String) {
    var open by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { open = !open }, contentPadding = PaddingValues(0.dp)) {
        Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, Modifier.size(18.dp))
        Text(" " + stringResource(if (open) R.string.ai_hide_thinking else R.string.ai_show_thinking), style = MaterialTheme.typography.labelLarge)
    }
    if (open) Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
}
