package org.agora.app.ui.finance

import androidx.compose.material.icons.automirrored.outlined.Send
import org.agora.app.ui.components.rememberSheetController
import org.agora.app.ui.components.SheetFileRow
import org.agora.app.ui.components.SheetToggleRow
import org.agora.app.ui.components.AgoraSheet
import org.agora.app.ui.components.PrimaryButton
import org.agora.app.ui.components.SecondaryButton
import org.agora.app.ui.components.AgoraTextField
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.agora.app.R
import org.agora.app.data.model.FinanceRequest
import org.agora.app.data.model.Person
import org.agora.app.data.model.User
import org.agora.app.data.model.parseAmount
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import org.agora.app.ui.components.ButtonLabel
import org.agora.app.ui.components.IconTile
import org.agora.app.ui.components.PageTitle
import org.agora.app.ui.components.PillTabs
import org.agora.app.ui.components.AgoraCard
import org.agora.app.ui.components.AmountField
import org.agora.app.ui.components.ButtonProgress
import org.agora.app.ui.components.DateField
import org.agora.app.ui.components.DropdownField
import org.agora.app.ui.components.EmptyState
import org.agora.app.ui.components.LocalContainer
import org.agora.app.ui.components.PickedFile
import org.agora.app.ui.components.Pill
import org.agora.app.ui.components.SectionTitle
import org.agora.app.ui.components.readPickedFile
import org.agora.app.ui.components.rememberActionRunner
import org.agora.app.ui.components.todayIso
import org.agora.app.ui.theme.Agora
import org.agora.app.util.Dates
import org.agora.app.util.Money
import org.agora.app.ui.components.isWide
import org.agora.app.ui.components.pagePadding
import org.agora.app.ui.components.TwoPane
import org.agora.app.data.model.StandingOrder
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemberFinanceScreen(user: User, contentPadding: PaddingValues) {
    val store = LocalContainer.current.store
    val data by store.data.collectAsStateWithLifecycle()
    val refreshing by store.refreshing.collectAsStateWithLifecycle()
    var requestType by rememberSaveable { mutableStateOf<String?>(null) }
    var openRequest by remember { mutableStateOf<FinanceRequest?>(null) }
    val person = data.ownPerson

    val status: @Composable () -> Unit = { FinanceStatusCard(user, person, data.fees, showDetails = true, onStatusClick = { requestType = "status" }) }
    // What can be submitted (one tile per kind), then the own requests with their state (web beta18)
    val actions: @Composable () -> Unit = { RequestActions { requestType = it } }
    val myRequests: @Composable () -> Unit = { MyRequestsCard(data.ownRequests) { openRequest = it } }
    val standingOrder: @Composable (StandingOrder) -> Unit = { so ->
        AgoraCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Autorenew, null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(so.note.ifBlank { stringResource(R.string.standing_order) }, style = MaterialTheme.typography.titleSmall)
                    val locale = Dates.locale(LocalContext.current)
                    Text(
                        stringResource(R.string.since_date, Dates.short(so.startDate, locale)) + (so.endDate?.let { " – " + Dates.short(it, locale) } ?: ""),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(Money.format(so.amount), style = MaterialTheme.typography.titleSmall)
            }
        }
    }
    // "Verlauf" like the PWA: payments and status changes in one timeline
    val history: @Composable (Person) -> Unit = { p ->
        AgoraCard {
            Text(stringResource(R.string.history_label), style = MaterialTheme.typography.titleLarge, color = Agora.colors.heading,
                modifier = Modifier.padding(bottom = 14.dp))
            FinanceTimeline(p)
        }
    }
    val wide = isWide()

    PullToRefreshBox(isRefreshing = refreshing, onRefresh = { store.refreshInBackground(true) }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = pagePadding(contentPadding.calculateTopPadding() + 4.dp, contentPadding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (wide) item(key = "two-pane") {
                // Tablets: status and requests on the left, the history on the right (like the web)
                TwoPane(true, gap = 20.dp, leftWeight = 1.15f, left = {
                    status()
                    actions()
                    myRequests()
                    if (person != null && person.standingOrders.isNotEmpty()) {
                        SectionTitle(stringResource(R.string.standing_orders))
                        person.standingOrders.forEach { standingOrder(it) }
                    }
                }, right = { if (person != null) history(person) })
            } else {
                item { status() }
                item(key = "actions") { actions() }
                item(key = "my-requests") { myRequests() }
                if (person != null && person.standingOrders.isNotEmpty()) {
                    item { SectionTitle(stringResource(R.string.standing_orders)) }
                    items(person.standingOrders, key = { "so-${it.id}" }) { standingOrder(it) }
                }
                if (person != null) item { history(person) }
            }
        }
    }

    requestType?.let { type ->
        NewRequestSheet(user, person, type, onDismiss = { requestType = null })
    }
    openRequest?.let { request ->
        RequestDetailSheet(request, canDecide = false, onDismiss = { openRequest = null }, onDecided = {})
    }
}

@Composable
fun requestTypeInfo(type: String): Pair<ImageVector, String> = when (type) {
    "status" -> Icons.Outlined.SwapHoriz to stringResource(R.string.user_req_type_status)
    "expense" -> Icons.AutoMirrored.Outlined.ReceiptLong to stringResource(R.string.user_req_type_expense)
    "standing_order" -> Icons.Outlined.Autorenew to stringResource(R.string.standing_order)
    else -> Icons.Outlined.Payments to stringResource(R.string.user_req_type_payment)
}

@Composable
fun requestChips(request: FinanceRequest): List<String> {
    val locale = Dates.locale(LocalContext.current)
    val chips = mutableListOf<String>()
    request.field("amount")?.let(::parseAmount)?.let { chips += Money.format(it) }
    request.field("date")?.takeIf { it.isNotBlank() }?.let { chips += Dates.short(it, locale) }
    request.field("newStatus")?.let { chips += statusLabel(it) }
    request.field("note")?.takeIf { it.isNotBlank() }?.let { chips += it }
    request.field("description")?.takeIf { it.isNotBlank() }?.let { chips += it }
    val receiptCount = request.receipts().size
    if (receiptCount > 0) chips += stringResource(R.string.receipts_count, receiptCount)
    return chips
}

/** Member request form: payment / standing order, status change or expense with receipts. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewRequestSheet(user: User, person: Person?, initialType: String, onDismiss: () -> Unit) {
    val container = LocalContainer.current
    val context = LocalContext.current
    val sheet = rememberSheetController(onDismiss)
    val scope = rememberCoroutineScope()
    val runner = rememberActionRunner()
    var type by rememberSaveable { mutableStateOf(initialType.takeIf { it in listOf("payment", "status", "expense") } ?: "payment") }
    var amount by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf(todayIso()) }
    var note by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var standingOrder by rememberSaveable { mutableStateOf(false) }
    var newStatus by rememberSaveable { mutableStateOf(person?.effectiveStatus?.takeIf { it in MEMBER_STATUSES } ?: "vollverdiener") }
    val receipts = remember { mutableStateListOf<PickedFile>() }
    var error by remember { mutableStateOf<String?>(null) }
    val sentMsg = stringResource(R.string.toast_request_sent)
    val fillAll = stringResource(R.string.error_fill_all)
    val invalidAmount = stringResource(R.string.error_invalid_amount)
    val pickReceipts = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(5)) { uris ->
        scope.launch { uris.forEach { uri -> readPickedFile(context, uri)?.let { receipts += it } } }
    }

    val types = listOf("payment", "status", "expense")
    AgoraSheet(
        title = stringResource(R.string.user_new_request),
        subtitle = person?.name,
        icon = requestTypeInfo(type).first,
        onDismiss = onDismiss,
        controller = sheet,
        actions = {
            PrimaryButton(
                onClick = {
                    error = null
                    val value = parseAmount(amount)
                    if ((type != "status" && amount.isBlank()) || date.isBlank() || (type == "expense" && description.isBlank())) {
                        error = fillAll
                        return@PrimaryButton
                    }
                    if (type != "status" && (value == null || value <= 0)) {
                        error = invalidAmount
                        return@PrimaryButton
                    }
                    val amountText = value?.let { String.format(Locale.US, "%.2f", it) }.orEmpty()
                    runner.run(sentMsg) {
                        val requestType = if (type == "payment" && standingOrder) "standing_order" else type
                        val payload = when (type) {
                            "status" -> buildJsonObject { put("newStatus", newStatus); put("date", date) }
                            "expense" -> {
                                val name = person?.name ?: user.fullName
                                val files = receipts.map { container.repo.uploadReceipt(name, date, it.name, it.mime, it.bytes) }
                                buildJsonObject {
                                    put("amount", amountText)
                                    put("description", description.trim())
                                    put("date", date)
                                    if (files.isNotEmpty()) put("receipt", container.json.encodeToString(ListSerializer(String.serializer()), files))
                                }
                            }
                            else -> buildJsonObject { put("amount", amountText); put("date", date); put("note", note.trim()) }
                        }
                        container.repo.submitRequest(user, person, requestType, payload)
                        container.store.refreshAll()
                        sheet.dismiss()
                    }
                },
                enabled = !runner.busy,
                modifier = Modifier.weight(1f).height(46.dp)
            ) { if (runner.busy) ButtonProgress() else ButtonLabel(stringResource(R.string.send_request), Icons.AutoMirrored.Outlined.Send) }
        }
    ) {
        PillTabs(types.map { (if (it == "status") stringResource(R.string.status_btn) else requestTypeInfo(it).second) to requestTypeInfo(it).first }, selected = types.indexOf(type), onSelect = { type = types[it]; error = null })
        if (type == "status") DropdownField(stringResource(R.string.new_status), MEMBER_STATUSES, newStatus, { statusLabel(it) }, { newStatus = it })
        else AmountField(stringResource(R.string.amount), amount, { amount = it })
        DateField(stringResource(if (type == "payment" && standingOrder) R.string.start_date else R.string.date), date, { date = it })
        if (type == "payment") {
            AgoraTextField(note, { note = it }, label = { Text(stringResource(R.string.note)) }, modifier = Modifier.fillMaxWidth())
            SheetToggleRow(stringResource(R.string.is_standing_order), standingOrder, { standingOrder = it }, icon = Icons.Outlined.Autorenew)
        }
        if (type == "expense") {
            AgoraTextField(description, { description = it }, label = { Text(stringResource(R.string.description)) }, modifier = Modifier.fillMaxWidth())
            receipts.toList().forEach { file -> SheetFileRow(file.name) { receipts.remove(file) } }
            SecondaryButton(onClick = { pickReceipts.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.fillMaxWidth()) {
                ButtonLabel(stringResource(R.string.add_receipts), Icons.Outlined.AttachFile)
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
    }
}
