package org.agora.app.ui.prayers

import org.agora.app.ui.components.FrostScaffold
import android.text.format.DateUtils
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.agora.app.AppContainer
import org.agora.app.R
import org.agora.app.data.Prayer
import org.agora.app.data.PrayerDuration
import org.agora.app.data.deletePrayer
import org.agora.app.data.extendPrayer
import org.agora.app.data.prayers
import org.agora.app.data.remote.ApiException
import org.agora.app.data.model.User
import org.agora.app.data.sharePrayer
import org.agora.app.ui.components.AgoraFabMenu
import org.agora.app.ui.components.AgoraSheet
import org.agora.app.ui.components.AgoraSnackbar
import org.agora.app.ui.components.AgoraTextField
import org.agora.app.ui.components.ButtonLabel
import org.agora.app.ui.components.ConfirmSheet
import org.agora.app.ui.components.EmptyState
import org.agora.app.ui.components.FabAction
import org.agora.app.ui.components.LoadingBox
import org.agora.app.ui.components.LocalContainer
import org.agora.app.ui.components.LocalSnackbar
import org.agora.app.ui.components.PillTabs
import org.agora.app.ui.components.PrimaryButton
import org.agora.app.ui.components.SecondaryButton
import org.agora.app.ui.components.SheetActions
import org.agora.app.ui.components.SheetToggleRow
import org.agora.app.ui.components.SubPageHeader
import org.agora.app.ui.components.UserAvatar
import org.agora.app.ui.components.containerViewModel
import org.agora.app.ui.components.rememberActionRunner
import org.agora.app.ui.components.rememberSheetController
import org.agora.app.ui.components.readingPadding
import org.agora.app.ui.components.softShadow
import org.agora.app.ui.theme.Agora
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Rose of the prayer page (web: #e11d48 in .polls-note and the empty state). */
private val Rose = Color(0xFFE11D48)

/** The list of the prayer page; reloaded when the page opens and after every own change. */
class PrayersViewModel(private val c: AppContainer) : ViewModel() {
    private val _prayers = MutableStateFlow<List<Prayer>>(emptyList())
    val prayers = _prayers.asStateFlow()
    private val _loaded = MutableStateFlow(false)
    val loaded = _loaded.asStateFlow()
    /** Only shown while nothing has been loaded yet; later failures go to the snackbar of the action. */
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun load() {
        viewModelScope.launch {
            try {
                _prayers.value = c.repo.prayers().prayers
                _loaded.value = true
                _error.value = null
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!_loaded.value) _error.value = (e as? ApiException)?.message?.takeIf { it.isNotBlank() } ?: ""
            }
        }
    }

    suspend fun share(text: String, duration: PrayerDuration, anonymous: Boolean) {
        c.repo.sharePrayer(text, duration, anonymous)
        _prayers.value = c.repo.prayers().prayers
    }

    suspend fun extend(id: String) {
        val updated = c.repo.extendPrayer(id)
        _prayers.update { list -> list.map { if (it.id == updated.id) updated else it } }
    }

    suspend fun delete(id: String) {
        c.repo.deletePrayer(id)
        _prayers.update { list -> list.filterNot { it.id == id } }
    }
}

/**
 * Prayer requests (Extras) like the web app v3.1: a calm intro, All / Mine, every request as its own quiet card
 * (the words first, who and when small above, long ones folded to four lines), "+" to share one. Own requests
 * (and every request for moderators) open their options with a tap.
 */
@Composable
fun PrayersScreen(user: User, onBack: () -> Unit) {
    val vm = containerViewModel { PrayersViewModel(it) }
    val prayers by vm.prayers.collectAsStateWithLifecycle()
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    var mineOnly by rememberSaveable { mutableStateOf(false) }
    var sharing by rememberSaveable { mutableStateOf(false) }
    var optionsFor by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { vm.load() }
    // Shared, extended or removed on another device (live update "prayers"): the list follows
    val store = LocalContainer.current.store
    LaunchedEffect(Unit) { store.serverChanges.collect { areas -> if ("prayers" in areas || "all" in areas) vm.load() } }

    val list = prayers.filter { if (mineOnly) it.isMine else !it.expired }

    FrostScaffold(
        topBar = { SubPageHeader(stringResource(R.string.prayers_title), onBack = onBack) },
        snackbarHost = { SnackbarHost(LocalSnackbar.current) { AgoraSnackbar(it) } }
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = readingPadding(padding.calculateTopPadding() + 4.dp, padding.calculateBottomPadding() + 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item(key = "note") { IntroNote() }
                item(key = "filter") {
                    PillTabs(
                        listOf(stringResource(R.string.prayers_filter_all) to null, stringResource(R.string.prayers_filter_mine) to null),
                        selected = if (mineOnly) 1 else 0,
                        onSelect = { mineOnly = it == 1 },
                        modifier = Modifier.widthIn(max = 240.dp).padding(top = 4.dp, bottom = 4.dp)
                    )
                }
                when {
                    !loaded && error != null -> item(key = "error") {
                        EmptyState(
                            Icons.Outlined.FavoriteBorder,
                            error?.takeIf { it.isNotBlank() } ?: stringResource(R.string.prayers_load_failed),
                            modifier = Modifier.fillMaxWidth().padding(top = 24.dp)
                        ) {
                            SecondaryButton(onClick = vm::load) { Text(stringResource(R.string.prayers_retry)) }
                        }
                    }
                    !loaded -> item(key = "loading") { LoadingBox(Modifier.fillMaxWidth().padding(top = 32.dp)) }
                    list.isEmpty() -> item(key = "empty") { EmptyCard(mineOnly) }
                    else -> items(list, key = { it.id }) { prayer ->
                        PrayerCard(prayer, onOptions = { optionsFor = prayer.id })
                    }
                }
            }
            AgoraFabMenu(
                listOf(FabAction(stringResource(R.string.prayers_new), Icons.Outlined.FavoriteBorder) { sharing = true }),
                stringResource(R.string.prayers_new),
                Modifier.align(Alignment.BottomEnd).padding(end = 4.dp, bottom = padding.calculateBottomPadding())
            )
        }
    }

    if (sharing) ShareSheet(
        onDismiss = { sharing = false },
        onShare = { text, duration, anonymous -> vm.share(text, duration, anonymous) },
        onShared = { mineOnly = false }
    )
    optionsFor?.let { id ->
        val prayer = prayers.firstOrNull { it.id == id }
        if (prayer == null) optionsFor = null
        else OptionsSheet(prayer, onDismiss = { optionsFor = null }, onExtend = { vm.extend(id) }, onDelete = { vm.delete(id) })
    }
}

/** The rose intro card of the web app (`#prayers .polls-note`). */
@Composable
private fun IntroNote() {
    val shape = RoundedCornerShape(16.dp)
    val surface = MaterialTheme.colorScheme.surface
    Row(
        Modifier.fillMaxWidth().clip(shape)
            .background(Rose.copy(alpha = 0.05f).compositeOver(surface))
            .border(1.dp, Rose.copy(alpha = 0.14f), shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(Icons.Outlined.FavoriteBorder, null, Modifier.padding(top = 1.dp).size(18.dp), tint = Rose.copy(alpha = 0.85f))
        Text(
            stringResource(R.string.prayers_note), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 10.dp)
        )
    }
}

@Composable
private fun EmptyCard(mineOnly: Boolean) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier.fillMaxWidth().padding(top = 4.dp)
            .softShadow(Agora.colors.shadow.copy(alpha = 0.06f), blur = 6.dp, shape = shape, offsetY = 1.dp)
            .clip(shape).background(MaterialTheme.colorScheme.surface).border(1.dp, Agora.colors.hairline, shape)
            .padding(horizontal = 20.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(52.dp).clip(CircleShape).background(Rose.copy(alpha = 0.10f).compositeOver(MaterialTheme.colorScheme.surface)),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Outlined.FavoriteBorder, null, Modifier.size(26.dp), tint = Rose) }
        Text(
            stringResource(if (mineOnly) R.string.prayers_empty_mine else R.string.prayers_empty_open),
            style = MaterialTheme.typography.titleMedium, color = Agora.colors.heading, modifier = Modifier.padding(top = 12.dp)
        )
        Text(
            stringResource(R.string.prayers_empty_hint), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun prayerName(p: Prayer): String {
    val author = p.author ?: return stringResource(R.string.prayers_anonymous_name)
    return author.name?.takeIf { it.isNotBlank() } ?: stringResource(R.string.prayers_member_fallback)
}

/** "vor 2 Stunden", "gestern"; older than a week as a date (web: timeAgo). */
private fun timeAgo(ms: Long, now: Long = System.currentTimeMillis()): String =
    if (now - ms > 7 * DateUtils.DAY_IN_MILLIS) prayerDate(ms)
    else DateUtils.getRelativeTimeSpanString(ms, now, DateUtils.MINUTE_IN_MILLIS).toString()

private fun prayerDate(ms: Long): String =
    DateTimeFormatter.ofPattern("d. MMM", Locale.getDefault()).format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

/** One request: picture and name, when; the words below, folded to four lines with "Read more" in place. */
@Composable
private fun PrayerCard(prayer: Prayer, onOptions: () -> Unit) {
    val editable = prayer.isMine || prayer.canDelete
    val name = prayerName(prayer)
    val meta = listOfNotNull(
        timeAgo(prayer.createdAt),
        if (!prayer.isMine) null
        else if (prayer.expired) stringResource(R.string.prayers_expired)
        else stringResource(R.string.prayers_until_short, prayerDate(prayer.expiresAt))
    ).joinToString(" · ")
    var expanded by rememberSaveable(prayer.id) { mutableStateOf(false) }
    var overflows by remember(prayer.id) { mutableStateOf(false) }
    val shape = RoundedCornerShape(20.dp)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, Agora.colors.hairline),
        modifier = Modifier.fillMaxWidth()
            .softShadow(Agora.colors.shadow.copy(alpha = 0.06f), blur = 6.dp, shape = shape, offsetY = 1.dp)
    ) {
        Row(
            Modifier
                .then(if (editable) Modifier.clickable(onClick = onOptions) else Modifier)
                .padding(start = 14.dp, end = 8.dp, top = 14.dp, bottom = 16.dp),
            verticalAlignment = Alignment.Top
        ) {
            val dim = if (prayer.expired) 0.6f else 1f
            val author = prayer.author
            if (author != null) UserAvatar(author.uid, name, 36.dp)
            else Box(
                Modifier.size(36.dp).clip(CircleShape).background(Agora.colors.surfaceAlt),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Outlined.VisibilityOff, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            Column(Modifier.weight(1f).padding(start = 12.dp, end = 4.dp).animateContentSize()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        name, style = MaterialTheme.typography.labelLarge, color = Agora.colors.heading.copy(alpha = dim),
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)
                    )
                    Text(
                        " · $meta", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = dim),
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    prayer.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = dim),
                    maxLines = if (expanded) Int.MAX_VALUE else 4,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow },
                    modifier = Modifier.padding(top = 6.dp)
                )
                if (overflows || expanded) Text(
                    stringResource(if (expanded) R.string.prayers_read_less else R.string.prayers_read_more),
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(8.dp))
                        .clickable { expanded = !expanded }.padding(vertical = 4.dp, horizontal = 2.dp)
                )
            }
            if (editable) Icon(
                Icons.Outlined.MoreHoriz, stringResource(R.string.prayers_options),
                Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Share a request (web: #prayer-edit-modal): text with counter, how long it is shown, anonymous or not. */
@Composable
private fun ShareSheet(onDismiss: () -> Unit, onShare: suspend (String, PrayerDuration, Boolean) -> Unit, onShared: () -> Unit) {
    val controller = rememberSheetController(onDismiss)
    val runner = rememberActionRunner()
    var text by rememberSaveable { mutableStateOf("") }
    var duration by rememberSaveable { mutableStateOf(PrayerDuration.MONTH) }
    var anonymous by rememberSaveable { mutableStateOf(false) }
    val shared = stringResource(R.string.prayers_shared)
    AgoraSheet(
        title = stringResource(R.string.prayers_new),
        onDismiss = onDismiss,
        icon = Icons.Outlined.FavoriteBorder,
        iconTint = Rose,
        controller = controller,
        actions = {
            SheetActions(
                stringResource(R.string.prayers_share),
                onConfirm = {
                    runner.run(shared) {
                        onShare(text.trim(), duration, anonymous)
                        controller.dismiss(onShared)
                    }
                },
                onCancel = { controller.dismiss() },
                enabled = text.trim().length >= 3,
                busy = runner.busy
            )
        }
    ) {
        AgoraTextField(
            value = text,
            onValueChange = { text = it.take(MAX_TEXT) },
            label = { Text(stringResource(R.string.prayers_your_request)) },
            placeholder = { Text(stringResource(R.string.prayers_text_ph)) },
            minLines = 5,
            supportingText = { Text("${text.length} / $MAX_TEXT", modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.End) },
            modifier = Modifier.fillMaxWidth()
        )
        Text(stringResource(R.string.prayers_how_long_short), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                PrayerDuration.WEEK to R.string.prayers_week,
                PrayerDuration.MONTH to R.string.prayers_month,
                PrayerDuration.QUARTER to R.string.prayers_quarter
            ).forEach { (value, label) ->
                FilterChip(
                    selected = duration == value,
                    onClick = { duration = value },
                    label = { Text(stringResource(label)) },
                    shape = CircleShape,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Agora.colors.navPill,
                        selectedLabelColor = Agora.colors.navPillContent
                    )
                )
            }
        }
        SheetToggleRow(
            title = stringResource(R.string.prayers_anonymous),
            subtitle = stringResource(R.string.prayers_anonymous_desc),
            checked = anonymous,
            onCheckedChange = { anonymous = it },
            icon = Icons.Outlined.VisibilityOff
        )
    }
}

/**
 * The options of a request (web: openPrayerOptions): the owner extends it by a month or removes it when it is
 * done; a moderator removes it after a confirmation.
 */
@Composable
private fun OptionsSheet(prayer: Prayer, onDismiss: () -> Unit, onExtend: suspend () -> Unit, onDelete: suspend () -> Unit) {
    val controller = rememberSheetController(onDismiss)
    val runner = rememberActionRunner()
    var confirming by remember { mutableStateOf(false) }
    val extended = stringResource(R.string.prayers_extended)
    val doneToast = stringResource(R.string.prayers_done_toast)
    val deleted = stringResource(R.string.prayers_deleted)
    AgoraSheet(
        title = if (prayer.isMine) stringResource(R.string.prayers_yours) else prayerName(prayer),
        subtitle = when {
            !prayer.isMine -> timeAgo(prayer.createdAt)
            prayer.expired -> stringResource(R.string.prayers_expired_hint)
            else -> stringResource(R.string.prayers_until, prayerDate(prayer.expiresAt))
        },
        onDismiss = onDismiss,
        icon = Icons.Outlined.FavoriteBorder,
        iconTint = Rose,
        controller = controller,
        actions = {
            if (prayer.isMine) {
                SecondaryButton(onClick = { runner.run(extended) { onExtend(); controller.dismiss() } }, modifier = Modifier.weight(1f)) {
                    ButtonLabel(stringResource(R.string.prayers_extend_month), Icons.Outlined.Schedule)
                }
                PrimaryButton(onClick = { runner.run(doneToast) { onDelete(); controller.dismiss() } }, enabled = !runner.busy, modifier = Modifier.weight(1f)) {
                    ButtonLabel(stringResource(R.string.prayers_done), Icons.Outlined.Check)
                }
            } else {
                PrimaryButton(onClick = { confirming = true }, danger = true, enabled = !runner.busy, modifier = Modifier.weight(1f)) {
                    ButtonLabel(stringResource(R.string.prayers_remove), Icons.Outlined.DeleteOutline)
                }
            }
        }
    ) {
        Text(prayer.text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
    if (confirming) ConfirmSheet(
        title = stringResource(R.string.prayers_remove),
        text = stringResource(R.string.prayers_remove_confirm),
        confirmLabel = stringResource(R.string.prayers_remove),
        destructive = true,
        onConfirm = { runner.run(deleted) { onDelete(); controller.dismiss() } },
        onDismiss = { confirming = false }
    )
}

private const val MAX_TEXT = 1000
