package org.agora.app.ui.events

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import org.agora.app.R
import org.agora.app.data.EventInput
import org.agora.app.data.model.User
import org.agora.app.ui.components.AgoraCard
import org.agora.app.ui.components.AgoraSnackbar
import org.agora.app.ui.components.AgoraSwitch
import org.agora.app.ui.components.AgoraTextField
import org.agora.app.ui.components.ButtonLabel
import org.agora.app.ui.components.ButtonProgress
import org.agora.app.ui.components.DateField
import org.agora.app.ui.components.DropdownField
import org.agora.app.ui.components.IconTile
import org.agora.app.ui.components.ImageCropDialog
import org.agora.app.ui.components.LocalContainer
import org.agora.app.ui.components.LocalSnackbar
import org.agora.app.ui.components.PillTabs
import org.agora.app.ui.components.PrimaryButton
import org.agora.app.ui.components.SecondaryButton
import org.agora.app.ui.components.SubPageHeader
import org.agora.app.ui.components.TimeField
import org.agora.app.ui.components.rememberActionRunner
import org.agora.app.ui.components.todayIso
import org.agora.app.ui.theme.Agora
import org.agora.app.util.Images

private val SectionShape = RoundedCornerShape(18.dp)

/** One section of the editor like the PWA (beta16): the same card everywhere with an icon tile and a plain heading. */
@Composable
private fun EditorSection(
    icon: ImageVector,
    tint: Color,
    title: String,
    required: Boolean = false,
    trailing: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    AgoraCard(shape = SectionShape, elevation = 4.dp, contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 34.dp)) {
            IconTile(icon, tint, size = 34.dp)
            Text(
                if (required) "$title *" else title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Agora.colors.heading,
                modifier = Modifier.padding(start = 10.dp).weight(1f)
            )
            trailing()
        }
        Spacer(Modifier.height(12.dp))
        content()
    }
}

/** Small toggle pill in a section header ("Mehrtägig", "Serie"). */
@Composable
private fun TogglePill(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Row(
        Modifier
            .clip(CircleShape)
            .background(if (checked) primary.copy(alpha = 0.12f) else Agora.colors.surfaceAlt)
            .border(1.5.dp, if (checked) primary else MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .clickable { onChange(!checked) }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (checked) {
            Icon(Icons.Outlined.Check, null, Modifier.size(14.dp), tint = primary)
            Spacer(Modifier.width(4.dp))
        }
        Text(label, color = if (checked) primary else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
    }
}

/** Selectable group chip with a check mark when chosen. */
@Composable
private fun GroupChip(name: String, selected: Boolean, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Row(
        Modifier
            .heightIn(min = 38.dp)
            .clip(CircleShape)
            .background(if (selected) primary.copy(alpha = 0.12f) else Agora.colors.surfaceAlt)
            .border(1.5.dp, if (selected) primary else MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selected) {
            Icon(Icons.Outlined.Check, null, Modifier.size(15.dp), tint = primary)
            Spacer(Modifier.width(6.dp))
        }
        Text(name, color = if (selected) primary else Agora.colors.heading, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
    }
}

/** Frosted button on the cover picture ("Bild ändern", "Entfernen"). */
@Composable
private fun CoverButton(label: String, icon: ImageVector, danger: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(if (danger) Color(0xD9DC2626) else Color(0xB30F172A))
            .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, Modifier.size(16.dp), tint = Color.White)
        Spacer(Modifier.width(6.dp))
        Text(label, color = Color.White, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EventEditScreen(eventId: String?, initialType: String, user: User, onBack: () -> Unit, onSaved: () -> Unit) {
    val container = LocalContainer.current
    val data by container.store.data.collectAsStateWithLifecycle()
    val existing = eventId?.let { id -> data.events.firstOrNull { it.id == id } }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val runner = rememberActionRunner()
    val isManager = user.managesEvents || user.isAdmin

    var type by rememberSaveable { mutableStateOf(existing?.eventType ?: if (isManager) initialType else "event") }
    var title by rememberSaveable { mutableStateOf(existing?.title ?: "") }
    var date by rememberSaveable { mutableStateOf(existing?.date ?: todayIso()) }
    var multiDay by rememberSaveable { mutableStateOf(existing?.isMultiDay ?: false) }
    var endDate by rememberSaveable { mutableStateOf(existing?.endDate ?: "") }
    var startTime by rememberSaveable { mutableStateOf(existing?.startTime ?: "") }
    var endTime by rememberSaveable { mutableStateOf(existing?.endTime ?: "") }
    var location by rememberSaveable { mutableStateOf(existing?.location ?: "") }
    var description by rememberSaveable { mutableStateOf(existing?.description ?: "") }
    var imageUrl by rememberSaveable { mutableStateOf(existing?.imageUrl ?: "") }
    var pinned by rememberSaveable { mutableStateOf(existing?.isPinned ?: false) }
    var registration by rememberSaveable { mutableStateOf(existing?.requiresRegistration ?: false) }
    var minP by rememberSaveable { mutableStateOf(existing?.minParticipants?.takeIf { it > 0 }?.toString() ?: "") }
    var maxP by rememberSaveable { mutableStateOf(existing?.maxParticipants?.takeIf { it > 0 }?.toString() ?: "") }
    var groups by rememberSaveable { mutableStateOf(existing?.targetGroups ?: emptyList()) }
    var recurring by rememberSaveable { mutableStateOf(false) }
    var rule by rememberSaveable { mutableStateOf("weekly") }
    var count by rememberSaveable { mutableStateOf("4") }
    var uploading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val uploadFailed = stringResource(R.string.error_upload)
    // A picked image goes through the 16:9 crop step (like the cover) before it is uploaded
    var cropUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val pickCover = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> cropUri = uri }
    cropUri?.let { uri ->
        ImageCropDialog(uri, aspect = 16f / 9f, onDismiss = { cropUri = null }, onCrop = { region ->
            cropUri = null
            scope.launch {
                uploading = true
                try {
                    val jpeg = Images.eventCover(context, uri, region) ?: throw IllegalStateException()
                    imageUrl = container.repo.uploadEventImage(jpeg)
                } catch (e: Exception) {
                    error = uploadFailed
                }
                uploading = false
            }
        })
    }
    val pickImage = { pickCover.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    val savedMsg = stringResource(R.string.event_saved)
    val titleRequired = stringResource(R.string.error_title_required)
    val isTermin = type == "termin"

    val save: () -> Unit = save@{
        if (title.isBlank()) { error = titleRequired; return@save }
        error = null
        val input = EventInput(
            title = title, date = date,
            endDate = if (multiDay) endDate else "",
            startTime = if (multiDay) "" else startTime,
            endTime = if (multiDay) "" else endTime,
            location = location, description = description,
            eventType = type,
            isPinned = isManager && type == "event" && pinned,
            requiresRegistration = registration,
            minParticipants = if (registration) minP.toIntOrNull() ?: 0 else 0,
            maxParticipants = if (registration) maxP.toIntOrNull() ?: 0 else 0,
            targetGroups = groups,
            imageUrl = imageUrl,
            isRecurring = recurring && existing == null && type == "termin",
            recurringRule = rule,
            recurringCount = (count.toIntOrNull() ?: 4).coerceIn(2, 52)
        )
        runner.run(savedMsg) {
            container.repo.saveEvent(existing?.id, input)
            container.store.refreshAll()
            onSaved()
        }
    }

    Scaffold(
        topBar = {
            SubPageHeader(
                stringResource(when {
                    existing != null && isTermin -> R.string.edit_termin
                    existing != null -> R.string.edit_event
                    isTermin -> R.string.new_termin
                    else -> R.string.new_event
                }), onBack
            )
        },
        // Cancel / save stay at the bottom while scrolling through the form
        bottomBar = {
            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background.copy(alpha = 0.96f)).navigationBarsPadding().imePadding()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp))
                }
                Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton(onClick = onBack, modifier = Modifier.weight(1f).heightIn(min = 50.dp), shape = RoundedCornerShape(15.dp)) {
                        ButtonLabel(stringResource(R.string.cancel), Icons.Outlined.Close)
                    }
                    PrimaryButton(
                        onClick = save,
                        enabled = !runner.busy && !uploading,
                        shape = RoundedCornerShape(15.dp),
                        modifier = Modifier.weight(1.6f).heightIn(min = 50.dp)
                    ) {
                        if (runner.busy) ButtonProgress()
                        else ButtonLabel(stringResource(if (existing != null) R.string.save else R.string.editor_publish), Icons.Outlined.Check)
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(LocalSnackbar.current) { AgoraSnackbar(it) } },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Cover: the picture with frosted buttons at its bottom right, or a drop zone
            if (imageUrl.isNotBlank()) {
                Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(SectionShape).background(MaterialTheme.colorScheme.surfaceContainer)) {
                    AsyncImage(container.api.absolute(imageUrl), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    if (uploading) CircularProgressIndicator(Modifier.align(Alignment.Center))
                    Row(Modifier.align(Alignment.BottomEnd).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CoverButton(stringResource(R.string.change_image), Icons.Outlined.PhotoCamera, onClick = { if (!uploading) pickImage() })
                        CoverButton(stringResource(R.string.remove), Icons.Outlined.Close, danger = true, onClick = { imageUrl = "" })
                    }
                }
            } else {
                val dash = MaterialTheme.colorScheme.outline
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 132.dp)
                        .clip(SectionShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .drawBehind {
                            drawRoundRect(dash, cornerRadius = CornerRadius(18.dp.toPx()),
                                style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))))
                        }
                        .clickable(enabled = !uploading) { pickImage() }
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (uploading) CircularProgressIndicator()
                    else {
                        IconTile(Icons.Outlined.Image, MaterialTheme.colorScheme.primary, size = 46.dp)
                        Text(stringResource(R.string.editor_cover_add), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = Agora.colors.heading, modifier = Modifier.padding(top = 10.dp))
                        Text(stringResource(R.string.editor_cover_sub), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    }
                }
            }

            // Termin / Event (managers only) with a quiet explanation and the highlight toggle for events
            if (isManager) AgoraCard(shape = SectionShape, elevation = 4.dp, contentPadding = PaddingValues(16.dp)) {
                PillTabs(
                    listOf(stringResource(R.string.event_badge_termin) to Icons.Outlined.CalendarMonth, stringResource(R.string.event_label_event) to Icons.Outlined.AutoAwesome),
                    selected = if (isTermin) 0 else 1, onSelect = { type = if (it == 0) "termin" else "event" }
                )
                Row(Modifier.padding(top = 12.dp, start = 4.dp, end = 4.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Outlined.Info, null, Modifier.padding(top = 2.dp).size(15.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(if (isTermin) R.string.editor_type_termin_desc else R.string.editor_type_event_desc),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp))
                }
                if (!isTermin) {
                    val amber = Agora.colors.warning
                    Row(
                        Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (pinned) amber.copy(alpha = 0.09f) else Agora.colors.surfaceAlt)
                            .border(1.5.dp, if (pinned) amber.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                            .clickable { pinned = !pinned }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconTile(Icons.Outlined.StarOutline, amber, size = 34.dp)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(stringResource(R.string.pin_event), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (pinned) amber else Agora.colors.heading)
                            Text(stringResource(R.string.pin_event_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        AgoraSwitch(pinned, { pinned = it })
                    }
                }
            }

            EditorSection(Icons.Outlined.Edit, Color(0xFF0891B2), stringResource(R.string.editor_title_label), required = true) {
                AgoraTextField(
                    title, { title = it; if (it.isNotBlank()) error = null },
                    placeholder = { Text(stringResource(R.string.editor_title_hint)) },
                    textStyle = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                    singleLine = true, isError = error == titleRequired, modifier = Modifier.fillMaxWidth()
                )
            }

            EditorSection(Icons.Outlined.CalendarMonth, Color(0xFF8B5CF6), stringResource(R.string.editor_when), trailing = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TogglePill(stringResource(R.string.multi_day), multiDay) {
                        multiDay = it
                        if (it && endDate.isBlank()) endDate = date
                    }
                    if (existing == null && isManager && isTermin) TogglePill(stringResource(R.string.recurring), recurring) { recurring = it }
                }
            }) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (multiDay) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            DateField(stringResource(R.string.start_date), date, { date = it }, Modifier.weight(1f))
                            DateField(stringResource(R.string.end_date), endDate, { endDate = it }, Modifier.weight(1f))
                        }
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.Info, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(stringResource(R.string.editor_multiday_hint), color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp))
                        }
                    } else {
                        DateField(stringResource(R.string.date), date, { date = it })
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            TimeField(stringResource(R.string.start_time), startTime, { startTime = it }, Modifier.weight(1f))
                            TimeField(stringResource(R.string.end_time), endTime, { endTime = it }, Modifier.weight(1f))
                        }
                    }
                    if (recurring && existing == null && isManager && isTermin) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        DropdownField(stringResource(R.string.recurring_rule), listOf("weekly", "biweekly", "monthly"), rule, {
                            stringResource(when (it) { "biweekly" -> R.string.rule_biweekly; "monthly" -> R.string.rule_monthly; else -> R.string.rule_weekly })
                        }, { rule = it }, Modifier.weight(1f))
                        AgoraTextField(count, { count = it.filter(Char::isDigit).take(2) }, label = { Text(stringResource(R.string.recurring_count)) },
                            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    }
                }
            }

            EditorSection(Icons.Outlined.Place, Agora.colors.success, stringResource(R.string.editor_where)) {
                AgoraTextField(location, { location = it }, placeholder = { Text(stringResource(R.string.editor_where_hint)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
            }

            EditorSection(Icons.AutoMirrored.Outlined.Notes, Color(0xFFD97706), stringResource(R.string.description)) {
                AgoraTextField(description, { description = it }, placeholder = { Text(stringResource(R.string.editor_description_hint)) },
                    minLines = 5, modifier = Modifier.fillMaxWidth())
            }

            if (data.groups.isNotEmpty()) EditorSection(Icons.Outlined.Group, highlightIndigo(), stringResource(R.string.target_groups)) {
                Text(stringResource(R.string.editor_groups_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(
                    Modifier.padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    data.groups.forEach { group ->
                        // The web editor saves group names, the app ids; both count as chosen
                        val selected = group.id in groups || group.name in groups
                        GroupChip(group.name, selected) {
                            groups = if (selected) groups - group.id - group.name else groups + group.id
                        }
                    }
                }
            }

            AgoraCard(
                shape = SectionShape, elevation = 4.dp, contentPadding = PaddingValues(16.dp),
                border = BorderStroke(1.dp, if (registration) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(Modifier.clickable { registration = !registration }, verticalAlignment = Alignment.CenterVertically) {
                    IconTile(Icons.Outlined.Group, MaterialTheme.colorScheme.primary, size = 34.dp)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(stringResource(R.string.requires_registration), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Agora.colors.heading, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(stringResource(R.string.editor_registration_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    AgoraSwitch(registration, { registration = it })
                }
                if (registration) {
                    HorizontalDivider(Modifier.padding(vertical = 14.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AgoraTextField(maxP, { maxP = it.filter(Char::isDigit) }, label = { Text(stringResource(R.string.max_participants_label)) },
                            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                        AgoraTextField(minP, { minP = it.filter(Char::isDigit) }, label = { Text(stringResource(R.string.min_participants_label)) },
                            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
