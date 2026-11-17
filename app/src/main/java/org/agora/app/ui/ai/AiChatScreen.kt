package org.agora.app.ui.ai

import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.EaseIn
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Euro
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.NorthEast
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Poll
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
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
import org.agora.app.ui.components.screenWidthDp
import org.agora.app.ui.components.softShadow
import org.agora.app.ui.theme.Agora

/**
 * One message. A reply records when it started and, once the first words of the answer came, how long the model
 * thought ([thoughtMs]); [errorText] is the app's own message for a failed reply (else the server's in [content]).
 * [steps]: its work in order - thoughts, notes on the way and, from server CALDERA on, the lookups the server made for
 * it ([lookups] of them); [content] is the text after the last lookup, the answer.
 */
data class AiEntry(
    val role: String,
    val content: String,
    val steps: List<AiStep> = emptyList(),
    val lookups: Int = 0,
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
            val work = AiReplyWork()
            var thoughtMs: Long? = null
            var drawn = 0L
            // Long thoughts come in many small pieces: the reply is redrawn at most about once per frame
            fun draw(streaming: Boolean) {
                if (thoughtMs == null) {
                    val split = splitThinkingFull(work.content)
                    if (split.answer.isNotEmpty() && !split.open) thoughtMs = System.currentTimeMillis() - started
                }
                _entries.update { list ->
                    val last = list.lastOrNull() ?: return@update list
                    list.dropLast(1) + last.copy(content = work.content, steps = work.steps, lookups = work.lookups, streaming = streaming, thoughtMs = thoughtMs)
                }
                drawn = System.currentTimeMillis()
            }
            try {
                // The model gets the answers without their thoughts (like the web app)
                c.repo.aiChat(history.map { AiMessage(it.role, if (it.role == "user") it.content else splitThinking(it.content).first) }).collect { chunk ->
                    work.add(chunk)
                    // A lookup (server CALDERA): what was written before it was a note on the way, the AI works on
                    if (chunk is AiChunk.Lookup) {
                        thoughtMs = null
                        draw(streaming = true)
                    } else if (System.currentTimeMillis() - drawn >= FRAME_MS) draw(streaming = true)
                }
                // A model that only thought (or a connection that ended early) gave no answer: say so
                if (splitThinkingFull(work.content).answer.isBlank()) {
                    throw EmptyReply(if (work.steps.isNotEmpty() || splitThinking(work.content).second.isNotBlank()) R.string.ai_only_thought else R.string.ai_no_answer)
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

/**
 * Fades and lifts in once, [delayMs] after it first shows (the web's aiWelcomeUp / aiMsgIn); with [enabled] false
 * it is simply there (a message scrolled back into view).
 */
@Composable
private fun Modifier.riseIn(delayMs: Int = 0, distance: Dp = 10.dp, enabled: Boolean = true): Modifier {
    val progress = riseProgress(delayMs, enabled)
    return graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * distance.toPx()
    }
}

/** How far [riseIn] has come (0 to 1), for elements that fade in their shadow themselves (see [IdeaCard]). */
@Composable
private fun riseProgress(delayMs: Int = 0, enabled: Boolean = true): State<Float> {
    val progress = remember { Animatable(if (enabled) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (progress.value >= 1f) return@LaunchedEffect
        delay(delayMs.toLong())
        progress.animateTo(1f, tween(450, easing = FastOutSlowInEasing))
    }
    return progress.asState()
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
    val lastLength = entries.lastOrNull()?.let { entry -> entry.content.length + entry.steps.sumOf { (it as? AiStep.Thought)?.text?.length ?: 0 } } ?: 0
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
        // The small sign before the title, "New chat" as a quiet M3 button (web: .ai-chat-title, .ai-new-chat-btn)
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
                        AiBubble(entry, seed = index * 7.3f, onReport = if (reportable) ({ reporting = index }) else null)
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

/** Colours of the three suggestion cards (web: .ai-chat-idea:nth-child): the sign's lagoon and sand, and stone. */
private val IdeaColors = listOf(Color(0xFF2D6A62), Color(0xFFA8743F), Color(0xFF6D6861))

/**
 * A new chat (web: .ai-chat-welcome): the sign, "Hi Enrico, how can I help?", what can be asked and three suggestion
 * cards; everything rises in one after another.
 */
@Composable
private fun AiWelcome(firstName: String, hint: String, ideas: List<Pair<ImageVector, Int>>, enabled: Boolean, onAsk: (String) -> Unit) {
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
                stringResource(R.string.ai_chat_hello), style = MaterialTheme.typography.headlineMedium,
                color = Agora.colors.heading, textAlign = TextAlign.Center
            )
        }
        Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp).widthIn(max = 360.dp).riseIn(140))
        Column(Modifier.padding(top = 26.dp).widthIn(max = 420.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ideas.forEachIndexed { index, (icon, id) ->
                val idea = stringResource(id)
                val rise = riseProgress(220 + index * 70)
                IdeaCard(icon, idea, IdeaColors[index % IdeaColors.size], enabled, rise = { rise.value }) { onAsk(idea) }
            }
        }
    }
}

@Composable
private fun IdeaCard(
    icon: ImageVector, text: String, color: Color, enabled: Boolean, modifier: Modifier = Modifier, rise: () -> Float = { 1f }, onClick: () -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    val surface = MaterialTheme.colorScheme.surface
    val dark = surface.luminance() < 0.5f
    Row(
        modifier.fillMaxWidth()
            // Rises in like riseIn, but the shadow fades in on its own, outside of the alpha layer: that one is drawn
            // offscreen and cut to the card's bounds, so the shadow would only pop up once the card is there
            .graphicsLayer { translationY = (1f - rise()) * 10.dp.toPx() }
            .softShadow(Agora.colors.shadow.copy(alpha = 0.07f), blur = 8.dp, shape = shape, offsetY = 2.dp, alpha = rise)
            .graphicsLayer { alpha = rise() }
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
private fun AiBubble(entry: AiEntry, seed: Float = 0f, onReport: (() -> Unit)? = null) {
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
    // The reply: plain text beside the small sign, which moves livelier while the AI works and comes to rest once
    // the reply is complete
    val split = splitThinkingFull(entry.content)
    val line = rememberLine(entry, split.thinking)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        AiOrb(28.dp, thinking = line.live, resting = !entry.streaming, seed = seed)
        Column(Modifier.weight(1f).widthIn(max = 640.dp).padding(start = 10.dp, top = 2.dp).animateContentSize()) {
            ThinkingLine(entry, split.thinking, line)
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

// --- The line above a reply and the steps below it (web: createAiReply in app.js, .ai-thinking in polish.css) ---

/** The app's easing (web: --ease). */
private val AiEase = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/** What the line says: "Thinking", what the AI is on ([LiveLabel]), or "Thought" once the answer runs. */
private sealed interface LineTitle {
    data object Thinking : LineTitle
    data class Live(val label: LiveLabel) : LineTitle
    data class Done(val lookups: Int) : LineTitle
}

/** The title on the line and whether the AI still works ([live]: the title shimmers and ends in "…", the sign moves livelier). */
@Stable
private class LineState(first: LineTitle, live: Boolean, scope: CoroutineScope) {
    var title by mutableStateOf(first)
        private set
    var live by mutableStateOf(live)
        private set
    val pacer = TitlePacer<LineTitle>(
        show = { title, tag ->
            this.title = title
            this.live = tag
        },
        later = { ms, run -> scope.launch { delay(ms); run() } },
        cancel = { (it as Job).cancel() },
        // The time since boot: a clock set back meanwhile does not hold a title for an hour
        now = SystemClock::uptimeMillis
    ).apply { want(first, live) }
}

/**
 * Paces what the line above [entry] says, as the web's does: "Thinking", then what the AI is on - the title of its
 * newest thought or the lookup it runs - each long enough to be read; "Thought" once the answer has run for a moment
 * (text the AI writes right before a lookup is only a note on the way), at once when the reply is complete.
 */
@Composable
private fun rememberLine(entry: AiEntry, inlineThinking: String): LineState {
    val answering = entry.thoughtMs != null
    val working = entry.streaming && !answering
    val wanted = if (!working) LineTitle.Done(entry.lookups)
        else liveLabel(entry.steps, entry.content, inlineThinking)?.let { LineTitle.Live(it) } ?: LineTitle.Thinking
    val scope = rememberCoroutineScope()
    val line = remember { LineState(wanted, working, scope) }
    val shown = lineTitle(line.title)
    val hold = when {
        !entry.streaming -> 0L
        answering -> null
        // A "Thought" that came too early makes room at once, "Thinking" soon for the first real title
        !line.live -> 0L
        line.title == LineTitle.Thinking -> 400L
        else -> readingTime(shown)
    }
    val grace = if (entry.streaming && answering) 700L else 0L
    LaunchedEffect(wanted, working, hold, grace) { line.pacer.want(wanted, working, hold, grace) }
    return line
}

@Composable
private fun lineTitle(title: LineTitle): String = when (title) {
    LineTitle.Thinking -> stringResource(R.string.ai_thinking)
    is LineTitle.Live -> when (val label = title.label) {
        is LiveLabel.Thought -> label.title
        is LiveLabel.Lookup -> lookupTitle(label.lookup)
    }
    is LineTitle.Done -> if (title.lookups > 0) stringResource(R.string.ai_thought_lookups, title.lookups) else stringResource(R.string.ai_thought)
}

/**
 * One quiet line above a reply (web: .ai-thinking): what the AI is doing right now - "Thinking", the title of its
 * newest thought ("Calculating the monthly expenses"), or, from server CALDERA on, the lookup the server makes for it,
 * in the model's own words where it gave some - with the seconds; then "Thought · 12 s" ("Thought · looked up 2×").
 * It is one line high whatever it says, and its words change with a short fade while its width glides along. A tap
 * opens the steps below it ([ThinkingSteps]). A reply without thoughts or lookups only needed the line while it
 * waited: once the answer runs, it folds away. [inlineThinking]: a `<think>` block of the answer.
 */
@Composable
private fun ThinkingLine(entry: AiEntry, inlineThinking: String, line: LineState) {
    var open by rememberSaveable { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val working = entry.streaming && entry.thoughtMs == null
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(working) {
        while (working) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val millis = entry.thoughtMs ?: (now - entry.startedAt)
    val seconds = ((millis + 500) / 1_000).coerceAtLeast(1).toInt()
    val steps = entry.steps + listOfNotNull(inlineThinking.takeIf { it.isNotBlank() }?.let { AiStep.Thought(it) })
    // Something to open: a lookup, a note or a thought with words
    val empty = steps.none { it !is AiStep.Thought || it.text.isNotBlank() }
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    val pill = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    AnimatedVisibility(
        visible = line.live || !empty,
        enter = EnterTransition.None,
        exit = shrinkVertically(tween(240, easing = AiEase)) + fadeOut(tween(240, easing = AiEase))
    ) {
        Column {
            Row(
                Modifier.padding(bottom = 10.dp).bringIntoViewRequester(pill)
                    .clip(CircleShape).border(1.dp, Agora.colors.hairline, CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable(enabled = !empty) {
                        open = !open
                        if (!open) expanded = false
                    }
                    .padding(start = 11.dp, end = 10.dp, top = 4.dp, bottom = 4.dp)
                    .heightIn(min = 22.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // One line high whatever it says: a long title is cut, the seconds and the arrow stay
                LineLabel(lineTitle(line.title), line.live, Modifier.weight(1f, fill = false))
                // The seconds glide in after the first one
                AnimatedVisibility(!working || now - entry.startedAt >= 1_000, enter = expandHorizontally(tween(260, easing = AiEase)) + fadeIn(tween(260, easing = AiEase))) {
                    Text(" · " + stringResource(R.string.ai_thinking_seconds, seconds), style = MaterialTheme.typography.labelMedium, color = secondary.copy(alpha = 0.85f), maxLines = 1)
                }
                // The arrow once there is something to open
                AnimatedVisibility(!empty, enter = expandHorizontally(tween(250, easing = AiEase)) + fadeIn(tween(250, easing = AiEase))) {
                    val turn by animateFloatAsState(if (open) 180f else 0f, tween(250, easing = AiEase), label = "arrow")
                    Icon(Icons.Outlined.ExpandMore, null, Modifier.padding(start = 2.dp).size(16.dp).graphicsLayer { rotationZ = turn }, tint = secondary)
                }
            }
            if (open && !empty) ThinkingSteps(steps, line.live, expanded) { more ->
                expanded = more
                // "Show less" far down in long steps: back to the line
                if (!more) scope.launch {
                    withFrameNanos {}
                    pill.bringIntoView()
                }
            }
        }
    }
}

/** What the line says. The words change inside it: the old ones leave upwards, the new ones come from below (web: showTitle). */
@Composable
private fun LineLabel(title: String, live: Boolean, modifier: Modifier = Modifier) {
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    val style = if (live) shimmer(MaterialTheme.typography.labelMedium, secondary) else MaterialTheme.typography.labelMedium.copy(color = secondary)
    AnimatedContent(
        targetState = title,
        transitionSpec = {
            (slideInVertically(tween(260, 70, AiEase)) { it * 3 / 5 } + fadeIn(tween(260, 70, AiEase)))
                .togetherWith(slideOutVertically(tween(170, easing = EaseIn)) { -it * 3 / 5 } + fadeOut(tween(170, easing = EaseIn)))
                .using(SizeTransform { _, _ -> tween(300, easing = AiEase) })
        },
        modifier = modifier.clipToBounds(),
        label = "title"
    ) { words ->
        // While the AI works the title ends in "…" (unless it was cut and has one)
        Text(if (live && !words.endsWith("…")) "$words…" else words, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** [base] in [color] with a soft light running through it while the AI works (web: aiShimmer). */
@Composable
private fun shimmer(base: TextStyle, color: Color): TextStyle {
    val light = aiColors().shimmer
    val x by rememberInfiniteTransition(label = "shimmer").animateFloat(1f, -1.5f, infiniteRepeatable(tween(1_800, easing = LinearEasing)), label = "x")
    return base.merge(
        TextStyle(brush = Brush.linearGradient(
            0f to color, 0.35f to color, 0.5f to light, 0.65f to color, 1f to color,
            start = Offset(x * 300f, 0f), end = Offset(x * 300f + 300f, 0f)
        ))
    )
}

/**
 * The steps below the line (web: .ai-steps): the thoughts and the notes written on the way as markdown, every lookup
 * with what it read and found, in order. They open as a window of a few lines that follows the newest step while the
 * AI works - what is hidden above or below fades out, a finger scrolls it; "Show more" opens them in full.
 */
@Composable
private fun ThinkingSteps(steps: List<AiStep>, live: Boolean, expanded: Boolean, onExpand: (Boolean) -> Unit) {
    val scroll = rememberScrollState()
    val edge = with(LocalDensity.current) { 24.dp.toPx() }
    // Whoever reads the newest step keeps seeing it while the AI works; whoever scrolled back stays there
    var follow by remember { mutableStateOf(live) }
    val working by rememberUpdatedState(live)
    LaunchedEffect(scroll) {
        snapshotFlow { scroll.isScrollInProgress }.drop(1).collect { moving -> if (!moving) follow = scroll.maxValue - scroll.value < edge }
    }
    LaunchedEffect(scroll) {
        snapshotFlow { scroll.maxValue }.collect { if (working && follow) scroll.scrollTo(it) }
    }
    val scope = rememberCoroutineScope()
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    val rail = aiColors().rail
    Column(Modifier.padding(bottom = 10.dp)) {
        Column(
            Modifier
                .padding(start = 6.dp)
                .then(if (expanded) Modifier else Modifier.heightIn(max = if (screenWidthDp() >= 768) 320.dp else 232.dp).fadeEdges(scroll))
                // A thin line in the sign's sand on the left
                .drawBehind { drawRect(rail, size = Size(2.dp.toPx(), size.height)) }
                .padding(start = 14.dp)
                .then(if (expanded) Modifier else Modifier.verticalScroll(scroll))
                .padding(vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            steps.forEach { step ->
                when (step) {
                    is AiStep.Thought -> if (step.text.isNotBlank()) StepText(step.text, secondary)
                    is AiStep.Note -> StepText(step.text, Agora.colors.heading)
                    is AiStep.Lookup -> LookupStep(step, live)
                }
            }
        }
        // "Show more" while something is hidden, "Show less" once it is all there
        if (expanded || scroll.maxValue in 3 until Int.MAX_VALUE) Row(
            Modifier.padding(start = 18.dp, top = 6.dp).clip(RoundedCornerShape(6.dp))
                .clickable {
                    if (expanded) {
                        follow = false
                        scope.launch { scroll.scrollTo(0) }
                    }
                    onExpand(!expanded)
                }
                .padding(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(if (expanded) R.string.ai_steps_less else R.string.ai_steps_more), style = MaterialTheme.typography.labelMedium, color = secondary)
            val turn by animateFloatAsState(if (expanded) 180f else 0f, tween(250, easing = AiEase), label = "more")
            Icon(Icons.Outlined.ExpandMore, null, Modifier.padding(start = 3.dp).size(14.dp).graphicsLayer { rotationZ = turn }, tint = secondary.copy(alpha = 0.7f))
        }
    }
}

/** What is hidden above or below the window fades out (web: .ai-steps.has-above / .has-below). */
private fun Modifier.fadeEdges(scroll: ScrollState): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithContent {
        drawContent()
        val fade = 26.dp.toPx()
        if (scroll.value > 2) drawRect(
            Brush.verticalGradient(listOf(Color.Transparent, Color.Black), endY = fade),
            size = Size(size.width, fade), blendMode = BlendMode.DstIn
        )
        if (scroll.maxValue - scroll.value in 3 until Int.MAX_VALUE) drawRect(
            Brush.verticalGradient(listOf(Color.Black, Color.Transparent), startY = size.height - fade, endY = size.height),
            topLeft = Offset(0f, size.height - fade), size = Size(size.width, fade), blendMode = BlendMode.DstIn
        )
    }

/** The size of the steps (web: .ai-thinking-content). */
@Composable
private fun stepStyle() = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp, lineHeight = 21.sp)

/** A thought or a note as markdown (summaries put a bold title above every step), in [color]. */
@Composable
private fun StepText(text: String, color: Color) =
    org.agora.app.ui.components.AgoraSmallMarkdown(throttled(text.trim()), stepStyle(), color)

/** [text] as it was at most [ms] ago: a growing thought is laid out anew about ten times a second at most (web: AI_STEP_TEXT_MS). */
@Composable
private fun throttled(text: String, ms: Long = 90): String {
    val latest by rememberUpdatedState(text)
    var shown by remember { mutableStateOf(text) }
    LaunchedEffect(Unit) {
        snapshotFlow { latest }.collect {
            shown = it
            delay(ms)
        }
    }
    return shown
}

/** A lookup: its icon, the model's words (shimmering while it runs) and below them what it read and what it found. */
@Composable
private fun LookupStep(step: AiStep.Lookup, live: Boolean) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val colors = aiColors()
    val heading = Agora.colors.heading
    val style = stepStyle().copy(fontWeight = FontWeight.Medium)
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier.padding(top = 1.dp).size(22.dp).clip(RoundedCornerShape(7.dp))
                .background(colors.tint.copy(alpha = if (dark) 0.26f else 0.12f).compositeOver(MaterialTheme.colorScheme.surface)),
            contentAlignment = Alignment.Center
        ) { Icon(lookupIcon(step.lookup.tool), null, Modifier.size(13.dp), tint = colors.accent) }
        Column(Modifier.padding(start = 9.dp)) {
            Text(lookupTitle(step.lookup), style = if (live && step.done == null) shimmer(style, heading) else style.copy(color = heading))
            // What it read, when the model named it in its own words above, and what it found
            val detail = listOfNotNull(lookupWhat(step.lookup).takeIf { lookupStep(step.lookup) != null }, lookupFound(step.done)).joinToString(" · ")
            if (detail.isNotEmpty()) Text(
                detail, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp, lineHeight = 17.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 1.dp)
            )
        }
    }
}

/** The icon of each lookup (web: AI_LOOKUP_ICONS). */
private fun lookupIcon(tool: String): ImageVector = when (tool) {
    "payments", "money" -> Icons.Outlined.Payments
    "expenses" -> Icons.Outlined.Receipt
    "donations" -> Icons.Outlined.FavoriteBorder
    "members" -> Icons.Outlined.Group
    "events" -> Icons.Outlined.CalendarMonth
    "money_totals" -> Icons.Outlined.Poll
    "finance_changes" -> Icons.Outlined.History
    else -> Icons.Outlined.Search
}

/** A lookup's line: the model's own words for what it does, when it gave some - else what is looked up. */
@Composable
private fun lookupTitle(lookup: AiChunk.Lookup): String = lookupStep(lookup) ?: stringResource(R.string.ai_looking_up, lookupWhat(lookup))

/** What a lookup reads: "expenses 2019–2024 “Christmas”" (web: lookupWhat). */
@Composable
private fun lookupWhat(lookup: AiChunk.Lookup): String {
    val parts = mutableListOf(stringResource(when (lookup.tool.takeIf { it in LOOKUP_TOOLS }) {
        "payments" -> R.string.ai_lookup_payments
        "expenses" -> R.string.ai_lookup_expenses
        "donations" -> R.string.ai_lookup_donations
        "money" -> R.string.ai_lookup_money
        "members" -> R.string.ai_lookup_members
        "events" -> R.string.ai_lookup_events
        "money_totals" -> R.string.ai_lookup_money_totals
        "finance_changes" -> R.string.ai_lookup_finance_changes
        else -> R.string.ai_lookup_unknown
    }))
    val (from, to) = lookup.from to lookup.to
    when {
        from != null && to != null -> parts += if (from == to) from else "$from–$to"
        from != null -> parts += stringResource(R.string.ai_lookup_since, from)
        to != null -> parts += stringResource(R.string.ai_lookup_until, to)
    }
    lookup.about?.let { parts += stringResource(R.string.ai_lookup_about, it) }
    return parts.joinToString(" ")
}

/** What a finished lookup found: "12 entries · €345.50" - or that it was not allowed or failed; null while it runs. */
@Composable
private fun lookupFound(done: AiChunk.LookupDone?): String? {
    done ?: return null
    done.error?.let { return stringResource(if (it == "forbidden") R.string.ai_found_forbidden else R.string.ai_found_failed) }
    val count = done.count ?: return null
    val unit = when (done.unit.takeIf { it in LOOKUP_UNITS }) {
        "entries" -> R.plurals.ai_found_entries
        "events" -> R.plurals.ai_found_events
        "changes" -> R.plurals.ai_found_changes
        "members" -> R.plurals.ai_found_members
        "periods" -> R.plurals.ai_found_periods
        else -> return null
    }
    return buildList {
        add(pluralStringResource(unit, count, count))
        done.eur?.let { add(org.agora.app.util.Money.format(it)) }
        if (done.cut) add(stringResource(R.string.ai_found_cut))
    }.joinToString(" · ")
}
