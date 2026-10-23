package org.agora.app.ui.events
import androidx.compose.ui.platform.LocalResources
import org.agora.app.ui.components.FrostScaffold
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.WindowInsets
import org.agora.app.ui.components.GlassSpheres
import org.agora.app.ui.components.InfoGroup
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material.icons.outlined.Assignment
import org.agora.app.ui.components.rememberSheetController
import org.agora.app.ui.components.AgoraSheet
import androidx.compose.ui.draw.clipToBounds
import com.mikepenz.markdown.m3.Markdown

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import org.agora.app.ui.components.PrimaryButton
import org.agora.app.ui.components.SecondaryButton
import org.agora.app.ui.components.AgoraTextField
import org.agora.app.ui.components.AgoraSnackbar
import org.agora.app.ui.components.ButtonLabel
import org.agora.app.ui.components.CapsLabel
import org.agora.app.ui.components.IconTile
import org.agora.app.ui.components.InfoTile
import org.agora.app.ui.components.PillTabs
import org.agora.app.ui.components.SecondaryStyle
import org.agora.app.ui.components.SubPageHeader
import org.agora.app.ui.components.GlassBackButton
import androidx.compose.material.icons.outlined.Share
import org.agora.app.data.model.Attendee
import org.agora.app.ui.components.GlassChip
import org.agora.app.ui.components.ImmersiveStatusBar
import org.agora.app.ui.components.isTablet
import org.agora.app.ui.components.readingPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.derivedStateOf
import org.agora.app.ui.components.AgoraMarkdown
import org.agora.app.ui.components.fullBleed
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import org.agora.app.R
import org.agora.app.data.model.AgoraEvent
import org.agora.app.data.model.Attendees
import org.agora.app.data.model.Candidates
import org.agora.app.data.model.Duty
import org.agora.app.data.model.User
import org.agora.app.ui.components.AgoraCard
import org.agora.app.ui.components.ConfirmSheet
import org.agora.app.ui.components.EmptyState
import org.agora.app.ui.components.InfoRow
import org.agora.app.ui.components.LoadingBox
import org.agora.app.ui.components.LocalContainer
import org.agora.app.ui.components.LocalSnackbar
import org.agora.app.ui.components.Pill
import org.agora.app.ui.components.UserAvatar
import org.agora.app.ui.components.rememberActionRunner
import org.agora.app.ui.theme.Agora
import org.agora.app.util.Dates

private val ONLINE_HINTS = listOf("online", "zoom", "teams", "meet.google", "http://", "https://", "discord", "jitsi")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EventDetailScreen(eventId: String, user: User, onBack: () -> Unit, onEdit: (String) -> Unit) {
    val container = LocalContainer.current
    val store = container.store
    val data by store.data.collectAsStateWithLifecycle()
    val event = data.events.firstOrNull { it.id == eventId }
    val context = LocalContext.current
    val resources = LocalResources.current
    val locale = Dates.locale(context)
    val runner = rememberActionRunner()
    var confirmDelete by remember { mutableStateOf(false) }
    var sharing by rememberSaveable { mutableStateOf(false) }
    var descriptionExpanded by rememberSaveable { mutableStateOf(false) }

    val hasPicture = event?.imageUrl?.isNotBlank() == true
    // Phones: the cover runs edge to edge under the status bar. Tablets: a readable column with the normal header,
    // the cover rounded inside it
    val tablet = isTablet()
    val hasCover = hasPicture && !tablet
    // Without a picture the page starts with a band of glass spheres (web: .event-detail-sphere-band)
    val hasBand = !hasPicture && !tablet
    val listState = rememberLazyListState()
    // While the cover is under the status bar: no fade, white icons (the cover is darkened at the top)
    val coverUnderStatusBar by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
    ImmersiveStatusBar(hasCover && coverUnderStatusBar)
    FrostScaffold(
        // With a cover image the back button floats on the image (PWA), otherwise the plain sub-page header
        topBar = { if (!hasCover && !hasBand) SubPageHeader(stringResource(if (event?.isTermin == true) R.string.event_badge_termin else R.string.event_label_event), onBack) },
        snackbarHost = { SnackbarHost(LocalSnackbar.current) { AgoraSnackbar(it) } },
        // Picture or sphere band up under the status bar: no glass there, it runs on like a photo
        statusBarFrost = !((hasCover || hasBand) && coverUnderStatusBar),
        // Pinned over the list so it stays reachable while scrolling (the tags scroll away with the cover)
        overlay = { if (hasCover || hasBand) GlassBackButton(onBack, Modifier.statusBarsPadding().padding(start = 14.dp, top = 10.dp)) }
    ) { padding ->
        if (event == null) {
            if (!data.loaded) LoadingBox(Modifier.padding(padding))
            else EmptyState(Icons.Outlined.Event, stringResource(R.string.event_not_found), modifier = Modifier.padding(padding))
            return@FrostScaffold
        }
        val typeLabel = stringResource(when {
            event.isPinned -> R.string.event_badge_highlight
            event.isTermin -> R.string.event_badge_termin
            else -> R.string.event_label_event
        })
        val groupNames = event.targetGroupNames(data.groups)
        val cancelled = stringResource(R.string.event_cancelled)
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            contentPadding = readingPadding(if (hasCover || hasBand) 0.dp else padding.calculateTopPadding() + 4.dp, padding.calculateBottomPadding() + 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (hasPicture && tablet) item {
                Box(Modifier.fillMaxWidth().aspectRatio(2f).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainer)) {
                    AsyncImage(
                        model = container.api.absolute(event.imageUrl), contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            if (hasCover) item {
                // Full-bleed 16:9 cover with frosted tags on it; the back button floats above the list (see below)
                Box(Modifier.fullBleed(20.dp).aspectRatio(16f / 9f).background(MaterialTheme.colorScheme.surfaceContainer)) {
                    AsyncImage(
                        model = container.api.absolute(event.imageUrl), contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    // Darker top so the back button stays readable, then the picture runs softly into the page (web)
                    Box(Modifier.fillMaxWidth().height(90.dp).background(Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.55f), 0.55f to Color.Black.copy(alpha = 0.2f), 1f to Color.Transparent)))
                    Box(Modifier.fillMaxWidth().fillMaxHeight(0.34f).align(Alignment.BottomCenter).background(Brush.verticalGradient(
                        0f to Color.Transparent, 0.7f to MaterialTheme.colorScheme.background.copy(alpha = 0.7f), 1f to MaterialTheme.colorScheme.background)))
                    HeadBadges(event.isPinned, event.status == "cancelled", typeLabel, cancelled)
                }
            }
            if (hasBand) item {
                val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                GlassSpheres(Modifier.fullBleed(20.dp).height(150.dp + statusBar), wash = if (event.isPinned) highlightIndigo() else Agora.colors.brand) {
                    Box(Modifier.fillMaxWidth().fillMaxHeight(0.4f).align(Alignment.BottomCenter)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.background))))
                    HeadBadges(event.isPinned, event.status == "cancelled", typeLabel, cancelled)
                }
            }
            // Badges, title and organiser like the PWA's detail modal
            item {
                // Only highlights and cancelled events get a badge (web); the band and the cover carry theirs
                if (!hasCover && !hasBand && (event.isPinned || event.status == "cancelled")) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (event.isPinned) Pill(typeLabel, Agora.colors.warning, icon = Icons.Outlined.StarOutline, caps = true)
                    if (event.status == "cancelled") Pill(cancelled, Agora.colors.danger, caps = true)
                }
                // Title and organiser; on their right the share icon, no button around it (web: .event-detail-title-row)
                Row(Modifier.padding(top = if (hasCover || hasBand) 4.dp else 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(event.title, style = MaterialTheme.typography.headlineMedium, color = Agora.colors.heading)
                        if (event.createdByName.isNotBlank()) Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Person, null, Modifier.size(18.dp), tint = Agora.colors.warning)
                            Text(
                                buildAnnotatedString {
                                    append(stringResource(R.string.organizer_label) + " ")
                                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Agora.colors.heading)) { append(event.createdByName) }
                                },
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 6.dp)
                            )
                        }
                    }
                    // The icon ends where the cards below end; its touch target reaches into the page margin
                    IconButton(onClick = { sharing = true }, modifier = Modifier.offset(x = 12.dp)) {
                        Icon(Icons.Outlined.Share, stringResource(R.string.share_event), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                // Target groups as a line under the organiser ("Event für die Gruppe „Technik“") instead of tags on the picture
                if (groupNames.isNotEmpty()) Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Group, null, Modifier.size(18.dp), tint = highlightIndigo())
                    val quoted = groupNames.map { "„$it“" }
                    val and = stringResource(R.string.detail_groups_and)
                    val list = if (quoted.size > 1) quoted.dropLast(1).joinToString(", ") + " $and " + quoted.last() else quoted.first()
                    val template = stringResource(when {
                        event.isTermin && quoted.size == 1 -> R.string.detail_groups_termin_one
                        event.isTermin -> R.string.detail_groups_termin_many
                        quoted.size == 1 -> R.string.detail_groups_event_one
                        else -> R.string.detail_groups_event_many
                    }, "{groups}")
                    val (before, after) = template.split("{groups}").let { it[0] to it.getOrElse(1) { "" } }
                    Text(
                        buildAnnotatedString {
                            append(before)
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Agora.colors.heading)) { append(list) }
                            append(after)
                        },
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
            }
            item {
                InfoGroup {
                    InfoTile(Icons.Outlined.CalendarMonth, Color(0xFF8B5CF6), stringResource(R.string.event_badge_termin), Dates.eventWhen(context, event),
                        grouped = true, oneLine = true)
                    if (event.location.isNotBlank()) {
                        val openMap = ONLINE_HINTS.none { event.location.lowercase().contains(it) }
                        val mapAction = {
                            // No browser / maps app installed: nothing to open
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=" + Uri.encode(event.location)))) }
                            Unit
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        InfoTile(Icons.Outlined.Place, Agora.colors.success, stringResource(R.string.location_label), event.location,
                            onClick = if (openMap) mapAction else null, grouped = true, oneLine = true) {
                            if (openMap) IconTile(Icons.AutoMirrored.Outlined.OpenInNew, Agora.colors.success, size = 36.dp)
                        }
                    }
                }
            }
            if (event.description.isNotBlank()) item {
                AgoraCard(onClick = { descriptionExpanded = !descriptionExpanded }, elevation = 0.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Outlined.Notes, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        CapsLabel(stringResource(if (event.isTermin) R.string.about_termin else R.string.about_event), Modifier.padding(start = 10.dp))
                    }
                    // Markdown like the PWA (headings, bold, lists); long texts are clipped until "Mehr anzeigen"
                    var fullHeight by remember(event.description) { mutableIntStateOf(0) }
                    val collapsedMax = 106.dp
                    val overflows = fullHeight > with(LocalDensity.current) { collapsedMax.roundToPx() }
                    Box(
                        Modifier
                            .animateContentSize()
                            .padding(top = 12.dp)
                            .then(if (descriptionExpanded) Modifier else Modifier.heightIn(max = collapsedMax).clipToBounds())
                    ) {
                        // Measured without the height limit so we know whether "show more" is needed; placed from the top
                        AgoraMarkdown(event.description, Modifier.layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints.copy(maxHeight = Constraints.Infinity))
                            fullHeight = placeable.height
                            layout(placeable.width, minOf(placeable.height, constraints.maxHeight)) { placeable.place(0, 0) }
                        })
                        if (!descriptionExpanded && overflows) Box(
                            Modifier.matchParentSize().background(
                                Brush.verticalGradient(0.62f to Color.Transparent, 1f to MaterialTheme.colorScheme.surface)
                            )
                        )
                    }
                    if (!descriptionExpanded && overflows) {
                        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.show_more), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                            Icon(Icons.Outlined.ExpandMore, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            if (event.requiresRegistration) item { RegistrationCard(event, user) }
            if (event.canAccessDutyPlan) item { PlanCard(event, user) }
            // `.detail-admin-section`: edit / delete for organisers
            if (event.canEdit) item {
                Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Settings, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.management), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 6.dp))
                    }
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SecondaryButton(onClick = { onEdit(event.id) }, modifier = Modifier.weight(1f)) { ButtonLabel(stringResource(R.string.edit), Icons.Outlined.Edit) }
                        SecondaryButton(onClick = { confirmDelete = true }, style = SecondaryStyle.Danger, modifier = Modifier.weight(1f)) {
                            ButtonLabel(stringResource(R.string.delete), Icons.Outlined.Delete)
                        }
                    }
                }
            }
        }
    }

    if (sharing && event != null) EventShareSheet(event, data.events, onDismiss = { sharing = false })

    if (confirmDelete && event != null) ConfirmSheet(
        title = stringResource(R.string.delete_event),
        text = stringResource(R.string.events_delete_confirm),
        confirmLabel = stringResource(R.string.delete),
        destructive = true,
        onConfirm = {
            runner.run(resources.getString(R.string.events_deleted_success)) {
                container.repo.deleteEvent(event.id)
                store.refreshAll()
                onBack()
            }
        },
        onDismiss = { confirmDelete = false }
    )
}

/** Head of the detail cards (PWA `.event-card-head`): tinted icon tile, title, one-line summary, optional chevron. */
@Composable
private fun EventCardHead(icon: ImageVector, tint: Color, title: String, meta: String, expanded: Boolean? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconTile(icon, tint, size = 38.dp)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold), color = Agora.colors.heading)
            if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (expanded != null) Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Thin rounded progress bar (capacity / filled duties). */
@Composable
private fun ThinProgress(fraction: Float, brush: Brush, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(CircleShape)
            .background(Agora.colors.surfaceAlt)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
    ) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().clip(CircleShape).background(brush))
    }
}

/** Tinted band with a round status icon, e.g. "Du bist angemeldet · Abmelden". */
@Composable
private fun StatusBand(color: Color, icon: ImageVector, title: String, sub: String, action: String? = null, enabled: Boolean = true, onAction: () -> Unit = {}) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(color.copy(alpha = 0.08f))
            .border(1.dp, color.copy(alpha = 0.3f), shape)
            .padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(30.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(17.dp), tint = Color.White)
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Agora.colors.heading)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (action != null) TextButton(onClick = onAction, enabled = enabled) {
            Text(action, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
        }
    }
}

/** Registration card like the PWA: capacity line + bar, my status (or the register button), attendee stack and list. */
@Composable
private fun RegistrationCard(event: AgoraEvent, user: User) {
    val container = LocalContainer.current
    val runner = rememberActionRunner()
    val context = LocalContext.current
    val resources = LocalResources.current
    var attendees by remember { mutableStateOf<Attendees?>(null) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var reload by remember { mutableStateOf(0) }
    val past = event.isPast()
    LaunchedEffect(reload, event.registeredCount, event.waitlistCount) {
        attendees = runCatching { container.repo.attendees(event.id) }.getOrNull()
    }
    val max = event.maxParticipants
    val count = event.registeredCount
    val free = (max - count).coerceAtLeast(0)
    val meta = listOfNotNull(
        if (max > 0) stringResource(R.string.reg_places_taken, count, max) else stringResource(R.string.reg_count, count),
        if (max > 0) (if (free > 0) stringResource(R.string.reg_places_free, free) else stringResource(R.string.reg_booked_out)) else null,
        if (event.minParticipants > 0) stringResource(R.string.reg_min, event.minParticipants) else null
    ).joinToString(" · ")
    val leave = {
        runner.run(resources.getString(R.string.events_unregistered_success)) { container.repo.register(event.id, false); container.store.refreshAll() }
    }

    AgoraCard {
        EventCardHead(Icons.Outlined.Groups, MaterialTheme.colorScheme.primary, stringResource(R.string.registration), meta)
        if (max > 0) ThinProgress(
            count.toFloat() / max,
            if (free == 0) Brush.horizontalGradient(listOf(Agora.colors.warning, Agora.colors.danger)) else Agora.colors.brandGradient,
            Modifier.padding(top = 12.dp)
        )
        Spacer(Modifier.height(14.dp))
        when {
            past -> StatusBand(Color(0xFF94A3B8), if (event.isRegistered) Icons.Outlined.Check else Icons.Outlined.Schedule,
                stringResource(if (event.isRegistered) R.string.reg_status_past_registered else R.string.reg_status_past), stringResource(R.string.reg_status_past_sub))
            event.isRegistered -> StatusBand(Agora.colors.success, Icons.Outlined.Check, stringResource(R.string.reg_status_registered),
                stringResource(R.string.reg_status_registered_sub), stringResource(R.string.reg_leave), !runner.busy, leave)
            event.isWaitlisted -> StatusBand(Agora.colors.warning, Icons.Outlined.Schedule, stringResource(R.string.reg_status_waitlist),
                stringResource(R.string.reg_status_waitlist_sub), stringResource(R.string.reg_waitlist_leave), !runner.busy, leave)
            else -> {
                val register = {
                    runner.run(resources.getString(if (event.isFull) R.string.events_waitlist_success else R.string.events_registered_success)) {
                        container.repo.register(event.id, true)
                        container.store.refreshAll()
                    }
                }
                if (event.isFull) SecondaryButton(onClick = register, enabled = !runner.busy, modifier = Modifier.fillMaxWidth()) {
                    ButtonLabel(stringResource(R.string.event_join_waitlist), Icons.Outlined.Schedule)
                }
                else PrimaryButton(onClick = register, enabled = !runner.busy, modifier = Modifier.fillMaxWidth()) {
                    ButtonLabel(stringResource(R.string.event_register), Icons.Outlined.Check)
                }
            }
        }
        // Attendees: overlapping avatars + count, list on tap
        val registered = attendees?.registered.orEmpty()
        val waitlist = attendees?.waitlist.orEmpty()
        HorizontalDivider(Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { expanded = !expanded }.padding(top = 12.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (registered.isNotEmpty()) {
                AvatarStack(registered.map { (if (it.guest) "" else it.userId) to it.name })
                Spacer(Modifier.width(10.dp))
            }
            Text(
                stringResource(if (registered.isEmpty()) R.string.reg_no_attendees else R.string.attendees),
                style = MaterialTheme.typography.titleSmall, color = Agora.colors.heading
            )
            if (registered.isNotEmpty()) Pill("${registered.size}", MaterialTheme.colorScheme.onSurfaceVariant, Modifier.padding(start = 8.dp))
            Spacer(Modifier.weight(1f))
            Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AnimatedVisibility(expanded) {
            Column(Modifier.padding(top = 8.dp)) {
                if (attendees == null) LoadingBox()
                else if (registered.isEmpty() && waitlist.isEmpty()) Text(stringResource(R.string.reg_attendees_empty), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 6.dp))
                registered.forEach { AttendeeRow(event, it, false) { reload++ } }
                if (waitlist.isNotEmpty()) {
                    CapsLabel(stringResource(R.string.reg_waitlist_header, waitlist.size), Modifier.padding(top = 10.dp, bottom = 2.dp), color = Agora.colors.warning)
                    waitlist.forEach { AttendeeRow(event, it, true) { reload++ } }
                }
            }
        }
    }
}

/** Overlapping avatars of the first attendees ("+N" for the rest). */
@Composable
private fun AvatarStack(people: List<Pair<String, String>>, max: Int = 5) {
    val shown = people.take(max)
    val rest = people.size - shown.size
    val size = 30.dp
    val step = 22.dp
    // Fixed width with absolute positions, so the overlap doesn't leave a gap after the stack
    Box(Modifier.width(size + step * (shown.size - 1) + if (rest > 0) step + 12.dp else 0.dp).height(size)) {
        shown.forEachIndexed { i, (id, name) ->
            Box(Modifier.offset(x = step * i).size(size).clip(CircleShape).background(MaterialTheme.colorScheme.surface).padding(2.dp)) {
                UserAvatar(id, name, 26.dp)
            }
        }
        if (rest > 0) Box(
            Modifier.offset(x = step * shown.size).height(size).clip(CircleShape).background(MaterialTheme.colorScheme.surface).padding(2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "+$rest", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clip(CircleShape).background(Agora.colors.surfaceAlt).padding(horizontal = 7.dp, vertical = 6.dp)
            )
        }
    }
}

// Guests (signed up without an account through the shared link) are marked; editors see how to reach them
@Composable
private fun AttendeeRow(event: AgoraEvent, attendee: Attendee, waitlist: Boolean, onRemoved: () -> Unit) {
    val container = LocalContainer.current
    val context = LocalContext.current
    val runner = rememberActionRunner()
    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        UserAvatar(attendee.userId.takeUnless { attendee.guest }, attendee.name, 32.dp)
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(attendee.name, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                    color = if (waitlist) MaterialTheme.colorScheme.onSurfaceVariant else Agora.colors.heading, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (attendee.guest) Pill(stringResource(R.string.attendee_guest), MaterialTheme.colorScheme.primary, Modifier.padding(start = 6.dp), caps = true)
            }
            if (attendee.guest && attendee.email.isNotBlank()) Text(
                listOfNotNull(attendee.email, attendee.age?.let { stringResource(R.string.attendee_age, it) }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable {
                    runCatching { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + attendee.email))) }
                }
            )
        }
        if (event.canEdit) IconButton(onClick = {
            runner.run { container.repo.removeAttendee(event.id, attendee.userId); container.store.refreshAll(); onRemoved() }
        }, enabled = !runner.busy, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Outlined.Close, stringResource(R.string.remove), Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Badges at the top right of the cover picture or the sphere band (web: .event-detail-pre-title): highlight, cancelled. */
@Composable
private fun BoxScope.HeadBadges(pinned: Boolean, cancelled: Boolean, typeLabel: String, cancelledLabel: String) {
    if (!pinned && !cancelled) return
    Row(
        // Centred on the row of the floating back button
        Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(start = 14.dp, end = 14.dp, top = 10.dp).height(40.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        if (pinned) GlassChip(typeLabel, icon = Icons.Outlined.StarOutline, highlight = true)
        if (cancelled) GlassChip(cancelledLabel)
    }
}
