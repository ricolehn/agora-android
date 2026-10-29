package org.agora.app.ui.media

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import org.agora.app.R
import org.agora.app.data.MediaChoice
import org.agora.app.data.MediaRules
import org.agora.app.data.mediaRules
import org.agora.app.data.remote.ApiException
import org.agora.app.data.saveMediaRules
import org.agora.app.ui.components.AgoraSheet
import org.agora.app.ui.components.LoadingBox
import org.agora.app.ui.components.LocalContainer
import org.agora.app.ui.components.SearchField
import org.agora.app.ui.components.SheetActions
import org.agora.app.ui.components.rememberActionRunner
import org.agora.app.ui.components.rememberSheetController
import org.agora.app.ui.theme.Agora

/** Blue of the shield of a folder with rights of its own (web: .media-folder-rights.is-set). */
private val RightsBlue = Color(0xFF4A98C4)

@Composable
private fun folderLabel(path: String) = if (path.isEmpty()) stringResource(R.string.media_base_folder) else "/$path"

@Composable
private fun levelLabel(level: String) = stringResource(
    when (level) {
        "read" -> R.string.media_level_read
        "write" -> R.string.media_level_write
        else -> R.string.media_level_hide
    }
)

@Composable
private fun choiceName(id: String, name: String) = if (id == ALL_MEMBERS) stringResource(R.string.media_all_members) else name

private fun targetIcon(kind: String): ImageVector = if (kind == "person") Icons.Outlined.Person else Icons.Outlined.Groups

/** Loads what the sheets need; null while loading, an error message (maybe blank) when it failed. */
@Composable
private fun rememberRules(path: String?): Pair<MediaRules?, String?> {
    val repo = LocalContainer.current.repo
    var rules by remember(path) { mutableStateOf<MediaRules?>(null) }
    var error by remember(path) { mutableStateOf<String?>(null) }
    LaunchedEffect(path) {
        try {
            rules = repo.mediaRules(path)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = (e as? ApiException)?.message.orEmpty()
        }
    }
    return rules to error
}

/**
 * The rights of one folder (extended media rights, web: #media-rights-modal): all members always, then the groups
 * and people with a right here or from above; each gets the folder's own right or follows what comes from above.
 * "Add group or person" searches the others.
 */
@Composable
fun MediaRightsSheet(path: String, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val repo = LocalContainer.current.repo
    val controller = rememberSheetController(onDismiss)
    val runner = rememberActionRunner()
    val (rules, error) = rememberRules(path)
    val rows = remember(rules) { mutableStateListOf<RightsRow>().apply { if (rules != null) addAll(rightsRows(rules.choices, rules.folder)) } }
    var adding by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var hint by remember { mutableStateOf(false) }
    val saved = stringResource(R.string.media_rights_saved)
    AgoraSheet(
        title = stringResource(R.string.media_rights_title),
        subtitle = folderLabel(path),
        onDismiss = onDismiss,
        icon = Icons.Outlined.Shield,
        iconTint = RightsBlue,
        controller = controller,
        actions = {
            SheetActions(
                stringResource(R.string.media_rights_save),
                onConfirm = {
                    runner.run(saved) {
                        repo.saveMediaRules(path, rightsPayload(rows))
                        onSaved()
                        controller.dismiss()
                    }
                },
                onCancel = { controller.dismiss() },
                enabled = rules != null,
                busy = runner.busy
            )
        }
    ) {
        Column(Modifier.animateContentSize()) {
            TextButton(onClick = { hint = !hint }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 0.dp)) {
                Icon(Icons.Outlined.Info, null, Modifier.size(16.dp))
                Text(stringResource(R.string.media_rights_more), modifier = Modifier.padding(start = 6.dp))
            }
            if (hint) Text(stringResource(R.string.media_rights_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        when {
            error != null -> Text(error.ifBlank { stringResource(R.string.media_rights_load_failed) }, color = Agora.colors.danger)
            rules == null -> LoadingBox(Modifier.fillMaxWidth().padding(vertical = 24.dp))
            else -> {
                rows.forEachIndexed { index, row ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    RightsRowView(
                        row,
                        onLevel = { rows[index] = row.copy(level = it) },
                        onRemove = if (removableRow(row)) ({ rows.removeAt(index) }) else null
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                if (!adding) TextButton(onClick = { adding = true; query = "" }) {
                    Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
                    Text(stringResource(R.string.media_rights_add), modifier = Modifier.padding(start = 6.dp))
                } else {
                    SearchField(query, { query = it }, stringResource(R.string.media_rights_search), Modifier.fillMaxWidth())
                    val found = addableTargets(rules.choices, rows, query)
                    if (found.isEmpty()) Text(
                        stringResource(if (query.isNotBlank()) R.string.media_rights_no_match else R.string.media_rights_all_added),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    found.forEach { choice ->
                        AddRow(choice) {
                            // New in the sheet: read, the most common wish; one tap changes it
                            rows.add(RightsRow(choice.id, choice.name, choice.kind, "read", null))
                            query = ""
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RightsRowView(row: RightsRow, onLevel: (String) -> Unit, onRemove: (() -> Unit)?) {
    val name = choiceName(row.id, row.name)
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(targetIcon(row.kind), null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                Text(name, style = MaterialTheme.typography.titleSmall, color = Agora.colors.heading, maxLines = 1, overflow = TextOverflow.Ellipsis)
                row.inherited?.let { from ->
                    Text(
                        stringResource(R.string.media_from_above, folderLabel(from.from), levelLabel(from.level)),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2
                    )
                }
            }
            if (onRemove != null) IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Outlined.Close, stringResource(R.string.media_rights_remove, name), Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        // Without a right of its own a row follows: the folder above, else for a person their groups, for a group
        // all members, for all members nothing ("no access" is then already the first choice, not offered twice)
        val follow = stringResource(
            when {
                row.inherited != null -> R.string.media_level_inherit
                row.kind == "person" -> R.string.media_level_groups
                row.kind == "group" -> R.string.media_level_all
                else -> R.string.media_level_none
            }
        )
        val options = buildList {
            add("" to follow)
            add("read" to stringResource(R.string.media_level_read))
            add("write" to stringResource(R.string.media_level_write))
            if (row.inherited != null || row.kind != "all" || row.level == "hide") add("hide" to stringResource(R.string.media_level_hide))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { (level, label) ->
                FilterChip(
                    selected = row.level == level,
                    onClick = { onLevel(level) },
                    label = { Text(label) },
                    shape = CircleShape,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = if (level == "hide") Agora.colors.danger.copy(alpha = 0.14f) else Agora.colors.navPill,
                        selectedLabelColor = if (level == "hide") Agora.colors.danger else Agora.colors.navPillContent
                    )
                )
            }
        }
    }
}

@Composable
private fun AddRow(choice: MediaChoice, onAdd: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onAdd).padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(targetIcon(choice.kind), null, Modifier.size(18.dp), tint = if (choice.kind == "person") MediaAccent else RightsBlue)
        Text(choice.name, style = MaterialTheme.typography.bodyLarge, color = Agora.colors.heading, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 10.dp))
        Text(
            stringResource(if (choice.kind == "person") R.string.media_kind_person else R.string.media_kind_group),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clip(CircleShape).background(Agora.colors.surfaceAlt).padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

/** Every folder that has rights of its own, with what they say; a tap opens the folder, the shield its rights. */
@Composable
fun MediaRulesOverview(currentPath: String, onDismiss: () -> Unit, onRights: (String) -> Unit, onOpen: (String) -> Unit) {
    val controller = rememberSheetController(onDismiss)
    val (rules, error) = rememberRules(null)
    AgoraSheet(
        title = stringResource(R.string.media_rules_overview),
        onDismiss = onDismiss,
        icon = Icons.Outlined.Shield,
        iconTint = RightsBlue,
        controller = controller
    ) {
        when {
            error != null -> Text(error.ifBlank { stringResource(R.string.media_rights_load_failed) }, color = Agora.colors.danger)
            rules == null -> LoadingBox(Modifier.fillMaxWidth().padding(vertical = 24.dp))
            rules.rules.isEmpty() -> Text(stringResource(R.string.media_rules_none), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> rules.rules.forEach { entry ->
                val names = rules.choices.associateBy { it.id }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .border(1.dp, if (entry.path == currentPath) RightsBlue.copy(alpha = 0.5f) else Agora.colors.hairline, RoundedCornerShape(14.dp))
                        .clickable { controller.dismiss { onOpen(entry.path) } }
                        .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Folder, null, Modifier.size(20.dp), tint = MediaAccent)
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        Text(folderLabel(entry.path), style = MaterialTheme.typography.titleSmall, color = Agora.colors.heading, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        val allMembers = stringResource(R.string.media_all_members)
                        val labels = mapOf("read" to levelLabel("read"), "write" to levelLabel("write"), "hide" to levelLabel("hide"))
                        Text(
                            entry.groups.entries.joinToString(" · ") { (id, level) ->
                                "${if (id == ALL_MEMBERS) allMembers else names[id]?.name ?: id}: ${labels[level] ?: level}"
                            },
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { controller.dismiss { onRights(entry.path) } }) {
                        Icon(Icons.Outlined.Shield, stringResource(R.string.media_rights_title), Modifier.size(20.dp), tint = RightsBlue)
                    }
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                }
            }
        }
    }
}
