package org.agora.app.ui.finance

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import org.agora.app.R
import org.agora.app.data.model.FinanceRequest
import org.agora.app.data.model.parseAmount
import org.agora.app.ui.components.AgoraCard
import org.agora.app.ui.components.AgoraSheet
import org.agora.app.ui.components.AgoraTextField
import org.agora.app.ui.components.ButtonLabel
import org.agora.app.ui.components.ButtonProgress
import org.agora.app.ui.components.CapsLabel
import org.agora.app.ui.components.IconTile
import org.agora.app.ui.components.LocalContainer
import org.agora.app.ui.components.PrimaryButton
import org.agora.app.ui.components.SecondaryButton
import org.agora.app.ui.components.SecondaryStyle
import org.agora.app.ui.components.UserAvatar
import org.agora.app.ui.components.rememberActionRunner
import org.agora.app.ui.components.rememberSheetController
import org.agora.app.ui.theme.Agora
import org.agora.app.util.Dates
import org.agora.app.util.Money

/*
 * Requests like the web (beta18): open requests as a calm list in the style of "Neue Nachrichten" (avatar, name,
 * amount, coloured type, text), a tap opens the request in a sheet with all details and the decision. Members get
 * three action tiles and "Meine Anfragen" with the state of each request.
 */

/** Colour of a request type: payment green, expense red, status indigo, standing order cyan. */
fun requestColor(type: String): Color = when (type) {
    "expense" -> Color(0xFFEF4444)
    "status" -> Color(0xFF6366F1)
    "standing_order" -> Color(0xFF06B6D4)
    else -> Color(0xFF10B981)
}

private val RequestAmber = Color(0xFFF59E0B)

/** Amount (or the new status) of a request, "/ month" for standing orders. */
@Composable
fun requestAmount(request: FinanceRequest): String = when (request.type) {
    "status" -> statusName(request.field("newStatus"))
    else -> {
        val amount = Money.format(request.field("amount")?.let(::parseAmount) ?: 0.0)
        if (request.type == "standing_order") "$amount ${stringResource(R.string.per_month)}" else amount
    }
}

/** "am 3. Okt. 2026" / "ab 1. Nov. 2026" (status changes and standing orders start on the date). */
@Composable
fun requestDateLabel(request: FinanceRequest): String {
    val date = request.field("date")?.takeIf { it.isNotBlank() } ?: return ""
    val text = Dates.short(date, Dates.locale(LocalContext.current))
    return stringResource(if (request.type == "status" || request.type == "standing_order") R.string.request_from_date else R.string.request_on_date, text)
}

private fun FinanceRequest.text(): String? = (if (type == "expense") field("description") else field("note"))?.takeIf { it.isNotBlank() }
/**
 * File names in a stored receipt field, read like the web's parseReceipts(): a JSON array kept as text (current
 * format) or - in bookings from older versions - a single file name or a comma separated list.
 */
fun receiptFiles(raw: String?): List<String> {
    val text = raw?.trim().orEmpty()
    if (text.startsWith("[") && text.endsWith("]")) {
        runCatching { return Json.parseToJsonElement(text).jsonArray.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotEmpty) } }
    }
    return text.split(',').map { it.trim() }.filter { it.isNotEmpty() }
}

fun FinanceRequest.receipts(): List<String> = receiptFiles(field("receipt"))

private const val MY_REQUESTS_SHOWN = 5

/** Receipt photos at screen size, not the camera's full resolution (keeps sheets light while they are dragged). */
@Composable
fun receiptImageRequest(url: String) = coil3.request.ImageRequest.Builder(LocalContext.current).data(url).size(1200).build()

/** Card head like "Neue Nachrichten": icon tile, title, count badge and an optional "Alle" link. */
@Composable
private fun RequestsHead(title: String, count: Int, badgeColor: Color, onAll: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 10.dp).heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
        IconTile(Icons.Outlined.Description, RequestAmber, size = 34.dp)
        Text(title, style = MaterialTheme.typography.titleMedium, color = Agora.colors.heading, modifier = Modifier.padding(start = 10.dp))
        CountBadge(count, badgeColor, Modifier.padding(start = 8.dp))
        Spacer(Modifier.weight(1f))
        if (onAll != null) TextButton(onClick = onAll) {
            Text(stringResource(R.string.home_messages_all), style = MaterialTheme.typography.labelLarge)
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(18.dp))
        }
    }
}

@Composable
private fun CountBadge(count: Int, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.heightIn(min = 22.dp).widthIn(min = 22.dp).clip(CircleShape).background(color).padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
        Text(if (count > 99) "99+" else "$count", color = Color.White, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold))
    }
}

/** Coloured dot + caps text (request type, or the state of an own request). */
@Composable
private fun DotLabel(text: String, color: Color, withReceipt: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        CapsLabel(text, Modifier.padding(start = 6.dp))
        if (withReceipt) Icon(Icons.Outlined.AttachFile, null, Modifier.padding(start = 4.dp).size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * "Offene Anfragen": on the finances tab all of them, on the start page of treasurers the three newest with "Alle".
 * A tap opens [RequestDetailSheet].
 */
@Composable
fun OpenRequestsCard(requests: List<FinanceRequest>, onOpen: (FinanceRequest) -> Unit, onAll: (() -> Unit)? = null, limit: Int = Int.MAX_VALUE) {
    AgoraCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
        RequestsHead(stringResource(R.string.pending_requests_short), requests.size, RequestAmber, onAll)
        requests.take(limit).forEach { request ->
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                Modifier.fillMaxWidth().clickable { onOpen(request) }.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val name = request.personName.ifBlank { "–" }
                UserAvatar(request.userId.ifBlank { null }, name, 44.dp)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(name, style = MaterialTheme.typography.titleSmall, color = Agora.colors.heading, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(end = 8.dp))
                        Text(requestAmount(request), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold), color = Agora.colors.heading)
                    }
                    DotLabel(requestTypeInfo(request.type).second, requestColor(request.type), request.receipts().isNotEmpty())
                    Text(request.text() ?: requestDateLabel(request), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                }
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            }
        }
        if (requests.size > limit && onAll != null) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                stringResource(R.string.requests_more, requests.size - limit),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onAll).padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
private fun requestState(status: String): Pair<String, Color> = when (status) {
    "approved" -> stringResource(R.string.req_status_approved) to Color(0xFF10B981)
    "rejected" -> stringResource(R.string.req_status_rejected) to Color(0xFFEF4444)
    else -> stringResource(R.string.req_status_pending) to RequestAmber
}

/** Member finances: what can be submitted, as three tiles with a short explanation each. */
@Composable
fun RequestActions(onRequest: (type: String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.req_actions_title), style = MaterialTheme.typography.titleMedium, color = Agora.colors.heading,
            modifier = Modifier.padding(start = 4.dp, top = 6.dp))
        AgoraCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
            listOf(
                Triple("payment", R.string.req_action_payment_title, R.string.req_action_payment_desc),
                Triple("expense", R.string.req_action_expense_title, R.string.req_action_expense_desc),
                Triple("status", R.string.req_action_status_title, R.string.req_action_status_desc)
            ).forEachIndexed { index, (type, title, desc) ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth().clickable { onRequest(type) }.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconTile(requestTypeInfo(type).first, requestColor(type), size = 40.dp)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(stringResource(title), style = MaterialTheme.typography.titleSmall, color = Agora.colors.heading)
                        Text(stringResource(desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                }
            }
        }
    }
}

/** "Meine Anfragen": type, amount, state (pending / approved / rejected with reason), five shown + "Alle anzeigen". */
@Composable
fun MyRequestsCard(requests: List<FinanceRequest>, onOpen: (FinanceRequest) -> Unit) {
    val locale = Dates.locale(LocalContext.current)
    var showAll by rememberSaveable { mutableStateOf(false) }
    if (requests.isEmpty()) {
        AgoraCard {
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.Description, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.no_requests), style = MaterialTheme.typography.titleSmall, color = Agora.colors.heading, modifier = Modifier.padding(top = 6.dp))
                Text(stringResource(R.string.user_no_requests_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center, modifier = Modifier.padding(top = 2.dp))
            }
        }
        return
    }
    AgoraCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
        RequestsHead(stringResource(R.string.user_requests_title), requests.size, MaterialTheme.colorScheme.primary, null)
        (if (showAll) requests else requests.take(MY_REQUESTS_SHOWN)).forEach { request ->
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            val (stateText, stateColor) = requestState(request.status)
            val typeColor = requestColor(request.type)
            Row(Modifier.fillMaxWidth().clickable { onOpen(request) }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(typeColor.copy(alpha = 0.13f)), contentAlignment = Alignment.Center) {
                    Icon(requestTypeInfo(request.type).first, null, Modifier.size(20.dp), tint = typeColor)
                }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(requestTypeInfo(request.type).second, style = MaterialTheme.typography.titleSmall, color = Agora.colors.heading,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(end = 8.dp))
                        Text(requestAmount(request), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold), color = Agora.colors.heading)
                    }
                    val sent = if (request.timestamp > 0) " · " + Dates.short(java.time.Instant.ofEpochMilli(request.timestamp).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString(), locale) else ""
                    DotLabel(stateText + sent, stateColor, request.receipts().isNotEmpty())
                    val rejected = request.status == "rejected"
                    Text(
                        if (rejected) stringResource(R.string.rejection_reason, request.rejectionReason?.takeIf { it.isNotBlank() } ?: "–")
                        else request.text() ?: requestDateLabel(request),
                        style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = if (rejected) Agora.colors.danger else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            }
        }
        if (requests.size > MY_REQUESTS_SHOWN) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                if (showAll) stringResource(R.string.user_requests_show_less) else stringResource(R.string.user_requests_show_all, requests.size),
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth().clickable { showAll = !showAll }.padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
    }
}

/**
 * One request with all details: amount hero, sender, purpose / note, date of receipt, state, receipts. Treasurers
 * decide on pending requests here (approve, or reject with an optional reason the member sees).
 */
@Composable
fun RequestDetailSheet(request: FinanceRequest, canDecide: Boolean, onDismiss: () -> Unit, onDecided: () -> Unit) {
    val container = LocalContainer.current
    val locale = Dates.locale(LocalContext.current)
    val sheet = rememberSheetController(onDismiss)
    val runner = rememberActionRunner()
    var rejecting by remember { mutableStateOf(false) }
    var reason by rememberSaveable { mutableStateOf("") }
    val color = requestColor(request.type)
    val (icon, typeLabel) = requestTypeInfo(request.type)
    val (stateText, stateColor) = requestState(request.status)
    val approvedMsg = stringResource(R.string.toast_request_approved)
    val rejectedMsg = stringResource(R.string.toast_request_rejected)
    val decide = canDecide && request.status == "pending"
    val name = request.personName.ifBlank { "–" }

    AgoraSheet(
        title = typeLabel,
        subtitle = if (decide) name else stateText,
        icon = icon,
        iconTint = color,
        onDismiss = onDismiss,
        controller = sheet,
        actions = if (!decide) null else ({
            if (!rejecting) {
                SecondaryButton(onClick = { rejecting = true }, enabled = !runner.busy, style = SecondaryStyle.Danger, modifier = Modifier.weight(1f).height(46.dp)) {
                    ButtonLabel(stringResource(R.string.reject), Icons.Outlined.Close)
                }
                PrimaryButton(onClick = {
                    runner.run(approvedMsg) { container.repo.approveRequest(request); onDecided(); sheet.dismiss() }
                }, enabled = !runner.busy, modifier = Modifier.weight(1.4f).height(46.dp)) {
                    if (runner.busy) ButtonProgress() else ButtonLabel(stringResource(R.string.approve), Icons.Outlined.Check)
                }
            } else {
                SecondaryButton(onClick = { rejecting = false }, enabled = !runner.busy, modifier = Modifier.weight(1f).height(46.dp)) {
                    Text(stringResource(R.string.cancel))
                }
                PrimaryButton(onClick = {
                    runner.run(rejectedMsg) { container.repo.rejectRequest(request.id, reason.trim()); onDecided(); sheet.dismiss() }
                }, enabled = !runner.busy, danger = true, modifier = Modifier.weight(1.4f).height(46.dp)) {
                    if (runner.busy) ButtonProgress() else ButtonLabel(stringResource(R.string.request_reject_confirm), Icons.Outlined.Close)
                }
            }
        })
    ) {
        // Amount hero tinted in the colour of the request type
        val heroShape = RoundedCornerShape(18.dp)
        Column(
            Modifier.fillMaxWidth().clip(heroShape).background(color.copy(alpha = 0.08f)).border(1.dp, color.copy(alpha = 0.22f), heroShape).padding(vertical = 18.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(requestAmount(request), style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold, fontSize = 28.sp),
                color = Agora.colors.heading, textAlign = TextAlign.Center)
            val date = requestDateLabel(request)
            if (date.isNotBlank()) Text(date, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
        }
        // Detail rows: label left, value right
        val rows = buildList<Triple<ImageVector, String, String>> {
            add(Triple(Icons.Outlined.Person, stringResource(R.string.request_detail_person), name))
            request.text()?.let {
                add(Triple(if (request.type == "expense") Icons.AutoMirrored.Outlined.ReceiptLong else Icons.AutoMirrored.Outlined.StickyNote2,
                    stringResource(if (request.type == "expense") R.string.request_detail_purpose else R.string.note), it))
            }
            if (request.timestamp > 0) add(Triple(Icons.Outlined.Schedule, stringResource(R.string.request_detail_received), Dates.dateTime(request.timestamp, locale)))
            if (!decide) add(Triple(Icons.Outlined.Info, stringResource(R.string.status_label), stateText))
            if (request.status == "rejected") add(Triple(Icons.Outlined.Close, stringResource(R.string.request_detail_reason), request.rejectionReason?.takeIf { it.isNotBlank() } ?: "–"))
        }
        val rowsShape = RoundedCornerShape(14.dp)
        Column(Modifier.fillMaxWidth().clip(rowsShape).border(1.dp, MaterialTheme.colorScheme.outlineVariant, rowsShape)) {
            rows.forEachIndexed { index, (rowIcon, label, value) ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.Top) {
                    Icon(rowIcon, null, Modifier.padding(top = 1.dp).size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(label, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 10.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), textAlign = TextAlign.End,
                        color = when {
                            label == stringResource(R.string.status_label) -> stateColor
                            request.status == "rejected" && rowIcon == Icons.Outlined.Close -> Agora.colors.danger
                            else -> Agora.colors.heading
                        },
                        modifier = Modifier.weight(1f))
                }
            }
        }
        val receipts = request.receipts()
        if (receipts.isNotEmpty()) CapsLabel(stringResource(R.string.receipts))
        receipts.forEach { file ->
            AsyncImage(
                model = receiptImageRequest(container.repo.receiptUrl(file)), contentDescription = file, contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
            )
        }
        AnimatedVisibility(rejecting) {
            AgoraTextField(reason, { reason = it }, label = { Text(stringResource(R.string.rejection_reason_label)) },
                placeholder = { Text(stringResource(R.string.request_reject_placeholder)) }, modifier = Modifier.fillMaxWidth())
        }
    }
}
