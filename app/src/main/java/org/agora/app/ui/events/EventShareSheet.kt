package org.agora.app.ui.events

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.agora.app.R
import org.agora.app.data.model.AgoraEvent
import org.agora.app.ui.components.AgoraSheet
import org.agora.app.ui.components.AgoraTextField
import org.agora.app.ui.components.ButtonLabel
import org.agora.app.ui.components.LocalContainer
import org.agora.app.ui.components.PrimaryButton
import org.agora.app.ui.components.SecondaryButton
import org.agora.app.ui.components.SheetToggleRow
import org.agora.app.ui.components.rememberActionRunner
import org.agora.app.ui.components.rememberSheetController
import org.agora.app.ui.theme.Agora
import org.agora.app.util.Dates
import org.agora.app.util.EventLinks

/**
 * Share an event (web: #event-share-modal): its link to copy or to hand to Android's share sheet. Whoever may edit the
 * event decides with the switch whether people without an account can open it and sign up (servers from v3.2 on;
 * older ones get no switch and the members' link).
 */
@Composable
fun EventShareSheet(event: AgoraEvent, events: List<AgoraEvent>, onDismiss: () -> Unit) {
    val container = LocalContainer.current
    val context = LocalContext.current
    val runner = rememberActionRunner()
    val controller = rememberSheetController(onDismiss)
    // The switch shows the new state while the server saves it; the event (and the link) follow on the refresh
    var pending by remember { mutableStateOf<Boolean?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(2000)
            copied = false
        }
    }
    val isPublic = pending ?: (event.isPublic == true)
    val link = EventLinks.shareUrl(container.api.baseUrl, event, events)

    AgoraSheet(
        title = stringResource(R.string.share_event),
        subtitle = listOf(event.title, Dates.eventWhen(context, event)).filter { it.isNotBlank() }.joinToString(" · "),
        icon = Icons.Outlined.Share,
        onDismiss = onDismiss,
        controller = controller,
        actions = {
            SecondaryButton(onClick = { copyLink(context, link); copied = true }, modifier = Modifier.weight(1f).height(46.dp)) {
                ButtonLabel(stringResource(if (copied) R.string.share_copied else R.string.share_copy), if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy)
            }
            PrimaryButton(onClick = { shareLink(context, event.title, link) }, modifier = Modifier.weight(1f).height(46.dp)) {
                ButtonLabel(stringResource(R.string.share_send), Icons.Outlined.Share)
            }
        }
    ) {
        if (event.canEdit && event.isPublic != null) {
            SheetToggleRow(
                title = stringResource(R.string.share_public),
                subtitle = stringResource(if (event.requiresRegistration) R.string.share_public_desc_reg else R.string.share_public_desc),
                checked = isPublic,
                onCheckedChange = { on ->
                    if (!runner.busy) {
                        pending = on
                        error = null
                        runner.run(onError = { pending = null; error = it }) {
                            container.repo.setEventPublic(event.id, on)
                            container.store.refreshAll()
                            pending = null
                        }
                    }
                },
                icon = Icons.Outlined.Public
            )
            error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Agora.colors.danger) }
        }
        AgoraTextField(link, {}, readOnly = true, singleLine = true, label = { Text(stringResource(R.string.share_link)) }, modifier = Modifier.fillMaxWidth())
        // Who can open the link
        val hintColor = if (isPublic) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (isPublic) Icons.Outlined.Visibility else Icons.Outlined.Lock, null, Modifier.size(16.dp), tint = hintColor)
            Text(
                stringResource(if (isPublic) R.string.share_hint_public else R.string.share_hint_members),
                style = MaterialTheme.typography.bodySmall, color = hintColor, modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

private fun copyLink(context: Context, link: String) {
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Agora", link))
}

// The link alone (messengers show the event's title and picture from it); the title for e-mail and the share sheet
private fun shareLink(context: Context, title: String, link: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, link)
        .putExtra(Intent.EXTRA_SUBJECT, title)
        .putExtra(Intent.EXTRA_TITLE, title)
    runCatching { context.startActivity(Intent.createChooser(send, null)) }
}
