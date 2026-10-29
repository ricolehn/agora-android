package org.agora.app.ui.ai

import androidx.annotation.StringRes
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Euro
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.NorthEast
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.agora.app.AppContainer
import org.agora.app.R
import org.agora.app.data.model.AiMessage
import org.agora.app.data.model.User
import org.agora.app.data.remote.AiChunk
import org.agora.app.data.remote.AiTimeoutException
import org.agora.app.ui.components.AgoraTextField
import org.agora.app.ui.components.LocalContainer
import org.agora.app.ui.components.containerViewModel
import org.agora.app.ui.components.frostSource
import org.agora.app.ui.components.frostSurface
import org.agora.app.ui.components.frosted
import org.agora.app.ui.components.rememberActionRunner
import org.agora.app.ui.components.softShadow
import org.agora.app.ui.theme.Agora

/**
 * One message. A reply records when it started and, once the first words of the answer came, how long the model
 * thought ([thoughtMs]); [errorText] is the app's own message for a failed reply (else the server's in [content]).
 */
data class AiEntry(
    val role: String,
    val content: String,
    val reasoning: String = "",
    val streaming: Boolean = false,
    val error: Boolean = false,
    val startedAt: Long = 0,
    val thoughtMs: Long? = null,
    @param:StringRes val errorText: Int? = null
)

/** A reply split into the answer and the thought process; [open]: a `<think>` block is still running. */
data class ThinkingSplit(val answer: String, val thinking: String, val open: Boolean)

private val THINK_BLOCK = Regex("<(think|thought)>([\\s\\S]*?)(</\\1>|$)", RegexOption.IGNORE_CASE)
/** The start of a tag cut off at the end of what has come so far ("<thi"), not shown while it streams. */
private val PARTIAL_TAG = Regex("</?(?:think|thought)?$|</?(?:t|th|thi|thin|tho|thou|thoug|though)$", RegexOption.IGNORE_CASE)

/** Separates inline `<think>…</think>` / `<thought>…</thought>` blocks (and reasoning sent apart) from the answer. */
fun splitThinkingFull(content: String, reasoning: String = ""): ThinkingSplit {
    var open = false
    val inline = StringBuilder()
    val answer = THINK_BLOCK.replace(content) { match ->
        inline.append(match.groupValues[2].trim()).append('\n')
        if (match.groupValues[3].isEmpty()) open = true
        ""
    }.replace(PARTIAL_TAG, "").trim()
    val thinking = listOf(reasoning.trim(), inline.toString().trim()).filter { it.isNotBlank() }.joinToString("\n\n")
    return ThinkingSplit(answer, thinking, open)
}

fun splitThinking(content: String): Pair<String, String> = splitThinkingFull(content).let { it.answer to it.thinking }

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
        val started = System.currentTimeMillis()
        _entries.value = history + AiEntry("assistant", "", streaming = true, startedAt = started)
        _busy.value = true
        job = viewModelScope.launch {
            val content = StringBuilder()
            val reasoning = StringBuilder()
            var thoughtMs: Long? = null
            var drawn = 0L
            // Long thoughts come in many small pieces: the reply is redrawn at most about once per frame
            fun draw(streaming: Boolean) {
                if (thoughtMs == null) {
                    val split = splitThinkingFull(content.toString())
                    if (split.answer.isNotEmpty() && !split.open) thoughtMs = System.currentTimeMillis() - started
                }
                _entries.update { list ->
                    val last = list.lastOrNull() ?: return@update list
                    list.dropLast(1) + last.copy(content = content.toString(), reasoning = reasoning.toString(), streaming = streaming, thoughtMs = thoughtMs)
                }
                drawn = System.currentTimeMillis()
            }
            try {
                // The model gets the answers without their thoughts (like the web app)
                c.repo.aiChat(history.map { AiMessage(it.role, if (it.role == "user") it.content else splitThinking(it.content).first) }).collect { chunk ->
                    when (chunk) {
                        is AiChunk.Content -> content.append(chunk.text)
                        is AiChunk.Reasoning -> reasoning.append(chunk.text)
                    }
                    if (System.currentTimeMillis() - drawn >= FRAME_MS) draw(streaming = true)
                }
                // A model that only thought (or a connection that ended early) gave no answer: say so
                if (splitThinkingFull(content.toString()).answer.isBlank()) {
                    throw EmptyReply(if (reasoning.isNotBlank() || splitThinking(content.toString()).second.isNotBlank()) R.string.ai_only_thought else R.string.ai_no_answer)
                }
                draw(streaming = false)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // Drop the failed question (it goes back into the input) and show the error
                val own = when (e) {
                    is EmptyReply -> e.text
                    is AiTimeoutException -> R.string.ai_timeout
                    else -> null
                }
                _entries.update { list -> list.dropLast(2) + AiEntry("assistant", e.message.orEmpty(), error = true, errorText = own) }
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

    private class EmptyReply(@param:StringRes val text: Int) : Exception()

    private companion object {
        const val FRAME_MS = 16L
    }
}

// --- The orb: the assistant's sign (web: .ai-orb) - soft colours slowly turning, faster while it thinks ---

private val OrbColors = listOf(Color(0xFF7C6FD6), Color(0xFF9B8CF0), Color(0xFF5FA8D3), Color(0xFF5DBFAA), Color(0xFF9B8CF0), Color(0xFF7C6FD6))

@Composable
fun AiOrb(size: Dp, thinking: Boolean = false, glow: Boolean = size >= 48.dp) {
    val transition = rememberInfiniteTransition(label = "orb")
    val turn by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(if (thinking) 2_200 else 12_000, easing = LinearEasing)), label = "turn")
    val breathe by transition.animateFloat(0.92f, 1.06f, infiniteRepeatable(tween(if (thinking) 800 else 2_250, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathe")
    val twinkle by transition.animateFloat(1f, if (thinking) 0.82f else 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "twinkle")
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        if (glow || thinking) Box(
            Modifier.size(size * 1.5f).graphicsLayer { scaleX = breathe; scaleY = breathe; alpha = if (glow) 0.7f else 0.5f }
                .background(Brush.radialGradient(listOf(Color(0xFF9B8CF0).copy(alpha = 0.45f), Color(0xFF5DBFAA).copy(alpha = 0.18f), Color.Transparent)), CircleShape)
        )
        Canvas(Modifier.size(size).clip(CircleShape)) {
            rotate(turn) { drawCircle(Brush.sweepGradient(OrbColors), radius = this.size.minDimension) }
            // Depth: light from the upper left, a little shade below
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.42f), Color.Transparent), center = Offset(this.size.width * 0.32f, this.size.height * 0.24f), radius = this.size.minDimension * 0.62f))
            drawCircle(Brush.radialGradient(listOf(Color(0xFF281E5A).copy(alpha = 0.28f), Color.Transparent), center = Offset(this.size.width * 0.6f, this.size.height * 1.05f), radius = this.size.minDimension * 0.6f))
        }
        Box(Modifier.size(size).border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape))
        Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(size * 0.4f).graphicsLayer { scaleX = twinkle; scaleY = twinkle; rotationZ = (1f - twinkle) * 110f }, tint = Color.White)
    }
}

/**
 * Fades and lifts in once, [delayMs] after it first shows (the web's aiWelcomeUp / aiMsgIn); with [enabled] false
 * it is simply there (a message scrolled back into view).
 */
@Composable
private fun Modifier.riseIn(delayMs: Int = 0, distance: Dp = 10.dp, enabled: Boolean = true): Modifier {
    val progress = remember { Animatable(if (enabled) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (progress.value >= 1f) return@LaunchedEffect
        delay(delayMs.toLong())
        progress.animateTo(1f, tween(450, easing = FastOutSlowInEasing))
    }
    return graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * distance.toPx()
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
    // Only new messages glide in, not those scrolled back into view (the count shown so far)
    val shown = remember { intArrayOf(0) }

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
        // The small orb before the title, "New chat" as a quiet M3 button (web: .ai-chat-title, .ai-new-chat-btn)
        Row(Modifier.fillMaxWidth().padding(start = if (onBack != null) 8.dp else 20.dp, end = 16.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            // Back to Extras (the chat is one of its apps)
            if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back), tint = Agora.colors.heading) }
            AiOrb(28.dp, thinking = busy)
            Text(stringResource(R.string.nav_ai), style = MaterialTheme.typography.titleLarge, color = Agora.colors.heading, modifier = Modifier.weight(1f).padding(start = 10.dp))
            OutlinedButton(
                onClick = { vm.clear(); shown[0] = 0 }, enabled = entries.isNotEmpty(),
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
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 8.dp + inputHeight),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(entries) { index, entry ->
                    val reportable = entry.role != "user" && !entry.error && !entry.streaming && entry.content.isNotBlank()
                    // Messages glide in; right after the start page the first ones wait until it has faded
                    val fresh = index >= shown[0]
                    if (fresh) shown[0] = index + 1
                    Box(Modifier.riseIn(delayMs = if (index < 2) 140 + index * 80 else 0, enabled = fresh)) {
                        AiBubble(entry, onReport = if (reportable) ({ reporting = index }) else null)
                    }
                }
            }
            // The start page fades upwards for the first message and makes room
            androidx.compose.animation.AnimatedVisibility(
                visible = entries.isEmpty(),
                exit = fadeOut(tween(340)) + slideOutVertically(tween(340)) { -it / 12 } + scaleOut(tween(340), targetScale = 0.97f),
                enter = androidx.compose.animation.fadeIn(tween(250)),
                modifier = Modifier.fillMaxSize().padding(bottom = inputHeight)
            ) {
                AiWelcome(
                    firstName = user.firstName.ifBlank { user.fullName }.trim().substringBefore(' '),
                    hint = stringResource(if (user.canViewFinances) R.string.ai_chat_hint_finance_v2 else R.string.ai_chat_hint_member_v2),
                    ideas = if (user.canViewFinances) listOf(
                        Icons.Outlined.Euro to R.string.ai_suggest_admin_1,
                        Icons.Outlined.CalendarMonth to R.string.ai_suggest_admin_2,
                        Icons.Outlined.TaskAlt to R.string.ai_suggest_admin_3
                    ) else listOf(
                        Icons.Outlined.Euro to R.string.ai_suggest_member_1,
                        Icons.Outlined.CalendarMonth to R.string.ai_suggest_member_2,
                        Icons.Outlined.PersonOutline to R.string.ai_suggest_member_3
                    ),
                    enabled = !busy
                ) { idea -> vm.send(idea) { failed -> input = failed } }
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
            iconTint = Agora.colors.danger,
            onDismiss = { reporting = null },
            onConfirm = { reason ->
                val prompt = entries.take(index).lastOrNull { it.role == "user" }?.content.orEmpty()
                val answer = splitThinking(entry.content).first
                runner.run(reportedMsg) { container.repo.report("ai", answer, reason.trim(), prompt = prompt) }
            }
        )
    }
}

/** Colours of the three suggestion cards (web: .ai-chat-idea:nth-child). */
private val IdeaColors = listOf(Color(0xFF7C6FD6), Color(0xFF4A98C4), Color(0xFF479F82))

/**
 * A new chat (web: .ai-chat-welcome): the orb, "Hi Enrico, how can I help?" with the question in the orb's
 * colours, what can be asked and three suggestion cards; everything rises in one after another.
 */
@Composable
private fun AiWelcome(firstName: String, hint: String, ideas: List<Pair<ImageVector, Int>>, enabled: Boolean, onAsk: (String) -> Unit) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(Modifier.riseIn(distance = 0.dp)) { AiOrb(76.dp) }
        Column(Modifier.padding(top = 22.dp).riseIn(80), horizontalAlignment = Alignment.CenterHorizontally) {
            if (firstName.isNotBlank()) Text(
                stringResource(R.string.ai_chat_hi_name, firstName), style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center
            )
            Text(
                stringResource(R.string.ai_chat_hello),
                style = MaterialTheme.typography.headlineMedium.merge(
                    TextStyle(
                        brush = Brush.linearGradient(
                            if (dark) listOf(Color(0xFFA99CF5), Color(0xFF9D90F2), Color(0xFF6CC3D8), Color(0xFF63C7B2))
                            else listOf(Color(0xFF6C5FD0), Color(0xFF7D6FE0), Color(0xFF3F9FB8), Color(0xFF2F9E8C))
                        )
                    )
                ),
                textAlign = TextAlign.Center
            )
        }
        Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp).widthIn(max = 360.dp).riseIn(140))
        Column(Modifier.padding(top = 26.dp).widthIn(max = 420.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ideas.forEachIndexed { index, (icon, id) ->
                val idea = stringResource(id)
                IdeaCard(icon, idea, IdeaColors[index % IdeaColors.size], enabled, Modifier.riseIn(220 + index * 70)) { onAsk(idea) }
            }
        }
    }
}

@Composable
private fun IdeaCard(icon: ImageVector, text: String, color: Color, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    val surface = MaterialTheme.colorScheme.surface
    val dark = surface.luminance() < 0.5f
    Row(
        modifier.fillMaxWidth()
            .softShadow(Agora.colors.shadow.copy(alpha = 0.07f), blur = 8.dp, shape = shape, offsetY = 2.dp)
            .clip(shape).background(surface).border(1.dp, Agora.colors.hairline, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(start = 12.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = if (dark) 0.22f else 0.13f).compositeOver(surface)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, Modifier.size(18.dp), tint = if (dark) color.copy(alpha = 0.7f).compositeOver(Color.White) else color) }
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Agora.colors.heading, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
        Icon(Icons.Outlined.NorthEast, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
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
                val message = entry.errorText?.let { stringResource(it) } ?: entry.content
                Text(
                    if (entry.error) stringResource(R.string.ai_error, message) else entry.content,
                    style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 15.dp, vertical = 10.dp)
                )
            }
        }
        return
    }
    // The reply: plain text beside the small orb, which turns faster while the model thinks
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        AiOrb(28.dp, thinking = entry.streaming && entry.thoughtMs == null)
        Column(Modifier.weight(1f).widthIn(max = 640.dp).padding(start = 10.dp, top = 2.dp).animateContentSize()) {
            val split = splitThinkingFull(entry.content, entry.reasoning)
            if (entry.streaming || split.thinking.isNotBlank() || entry.thoughtMs != null) ThinkingLine(entry, split.thinking)
            if (split.answer.isNotBlank()) SelectionContainer { org.agora.app.ui.components.AgoraMarkdown(split.answer) }
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

/**
 * One quiet line (web: .ai-thinking): "Thinking · 4 s" with a shimmer while the model thinks, then "Thought · 12 s";
 * a tap shows the thoughts once there are some.
 */
@Composable
private fun ThinkingLine(entry: AiEntry, thinking: String) {
    var open by rememberSaveable { mutableStateOf(false) }
    val live = entry.streaming && entry.thoughtMs == null
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(live) {
        while (live) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val millis = entry.thoughtMs ?: (now - entry.startedAt)
    val seconds = (millis / 1000).coerceAtLeast(1).toInt()
    val canOpen = thinking.isNotBlank()
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = if (live) {
        val shimmer by rememberInfiniteTransition(label = "shimmer").animateFloat(1f, -1.5f, infiniteRepeatable(tween(1_800, easing = LinearEasing)), label = "x")
        MaterialTheme.typography.labelMedium.merge(
            TextStyle(brush = Brush.linearGradient(
                0f to secondary, 0.35f to secondary, 0.5f to Color(0xFF8F84E6), 0.65f to secondary, 1f to secondary,
                start = Offset(shimmer * 300f, 0f), end = Offset(shimmer * 300f + 300f, 0f)
            ))
        )
    } else MaterialTheme.typography.labelMedium.copy(color = secondary)
    Row(
        Modifier.padding(bottom = 10.dp).clip(CircleShape).border(1.dp, Agora.colors.hairline, CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(enabled = canOpen) { open = !open }
            .padding(start = 11.dp, end = 10.dp, top = 4.dp, bottom = 4.dp)
            .heightIn(min = 22.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(stringResource(if (live) R.string.ai_thinking else R.string.ai_thought) + if (live) "…" else "", style = labelStyle)
        Text(" · " + stringResource(R.string.ai_thinking_seconds, seconds), style = MaterialTheme.typography.labelMedium, color = secondary.copy(alpha = 0.85f))
        if (canOpen) Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, Modifier.padding(start = 2.dp).size(16.dp), tint = secondary)
    }
    if (open && canOpen) Text(
        thinking, style = MaterialTheme.typography.bodySmall, color = secondary,
        modifier = Modifier.padding(start = 6.dp, bottom = 10.dp)
            // A thin line in the orb's violet on the left (web: .ai-thinking-content)
            .drawBehind { drawRect(Color(0xFF7C6FD6).copy(alpha = 0.3f), size = Size(2.dp.toPx(), size.height)) }
            .padding(start = 14.dp)
            .heightIn(max = 260.dp)
            .verticalScroll(rememberScrollState())
    )
}
