package org.agora.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.agora.app.R
import org.agora.app.ui.theme.Agora

private val SheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

/** Closes a sheet with its slide-down animation before removing it (close button, finished actions). */
@OptIn(ExperimentalMaterial3Api::class)
class SheetController internal constructor(val state: SheetState, private val close: (after: () -> Unit) -> Unit) {
    fun dismiss(after: () -> Unit = {}) = close(after)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberSheetController(onDismiss: () -> Unit): SheetController {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    return remember(state) {
        SheetController(state) { after ->
            scope.launch { state.hide() }.invokeOnCompletion { after(); onDismiss() }
        }
    }
}

/**
 * Pull-up sheet in the Agora style (replaces the PWA's centred modals on Android): drag handle, tinted icon tile,
 * heavy title with subtitle and a round close button, a scrolling body and actions pinned to the bottom.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgoraSheet(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    controller: SheetController = rememberSheetController(onDismiss),
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = controller.state,
        shape = SheetShape,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 4.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
            )
        },
        modifier = modifier
    ) {
        Column(Modifier.fillMaxWidth().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 14.dp, top = 8.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) IconTile(icon, iconTint, Modifier.padding(end = 12.dp), size = 42.dp)
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, color = Agora.colors.heading, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (subtitle != null) Text(
                        subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Surface(
                    onClick = { controller.dismiss() },
                    shape = CircleShape,
                    color = Agora.colors.surfaceAlt,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.padding(start = 8.dp).size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Close, stringResource(R.string.close), Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(top = 16.dp, bottom = if (actions == null) 8.dp else 16.dp)
                    .then(if (actions == null) Modifier.navigationBarsPadding() else Modifier),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content
            )
            if (actions != null) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                Row(
                    Modifier.fillMaxWidth().background(Agora.colors.surfaceAlt).navigationBarsPadding().padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    content = actions
                )
            }
        }
    }
}

/** Cancel + confirm buttons of a sheet footer, both half width. */
@Composable
fun RowScope.SheetActions(
    confirmLabel: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    enabled: Boolean = true,
    busy: Boolean = false,
    destructive: Boolean = false,
    confirmIcon: ImageVector? = null
) {
    SecondaryButton(onClick = onCancel, modifier = Modifier.weight(1f).height(46.dp)) { Text(stringResource(R.string.cancel)) }
    PrimaryButton(onClick = onConfirm, enabled = enabled && !busy, danger = destructive, modifier = Modifier.weight(1f).height(46.dp)) {
        if (busy) ButtonProgress() else ButtonLabel(confirmLabel, confirmIcon)
    }
}

/** Yes/no question as a pull-up sheet (delete event, delete task, reset feed …). */
@Composable
fun ConfirmSheet(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
    icon: ImageVector = if (destructive) Icons.Outlined.DeleteOutline else Icons.Outlined.HelpOutline
) {
    val controller = rememberSheetController(onDismiss)
    AgoraSheet(
        title = title,
        onDismiss = onDismiss,
        icon = icon,
        iconTint = if (destructive) Agora.colors.danger else MaterialTheme.colorScheme.primary,
        controller = controller,
        actions = {
            SheetActions(confirmLabel, onConfirm = { controller.dismiss(onConfirm) }, onCancel = { controller.dismiss() }, destructive = destructive)
        }
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Sheet with a single text input (e.g. rejection reason). */
@Composable
fun TextInputSheet(
    title: String,
    label: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    initial: String = "",
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    destructive: Boolean = false
) {
    val controller = rememberSheetController(onDismiss)
    var value by remember { mutableStateOf(initial) }
    AgoraSheet(
        title = title,
        onDismiss = onDismiss,
        subtitle = subtitle,
        icon = icon,
        iconTint = iconTint,
        controller = controller,
        actions = {
            SheetActions(confirmLabel, onConfirm = { controller.dismiss { onConfirm(value) } }, onCancel = { controller.dismiss() }, destructive = destructive)
        }
    ) {
        AgoraTextField(value, { value = it }, label = { Text(label) }, minLines = 3, modifier = Modifier.fillMaxWidth())
    }
}

/** Switch row on a tinted tile for sheet forms (standing order, accepting mentees …). */
@Composable
fun SheetToggleRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, subtitle: String? = null, icon: ImageVector? = null) {
    Surface(
        onClick = { onCheckedChange(!checked) },
        shape = FieldShape,
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) IconTile(icon, MaterialTheme.colorScheme.primary, Modifier.padding(end = 12.dp), size = 34.dp)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = Agora.colors.heading)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AgoraSwitch(checked, onCheckedChange, Modifier.padding(start = 8.dp))
        }
    }
}

/** Picked attachments as removable rows (receipts). */
@Composable
fun SheetFileRow(name: String, onRemove: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(FieldShape).background(Agora.colors.surfaceAlt).padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.AttachFile, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 8.dp))
        androidx.compose.material3.IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Outlined.Close, stringResource(R.string.delete), Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
