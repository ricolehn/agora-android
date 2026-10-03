package org.agora.app.ui.finance

import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.height
import org.agora.app.util.Dates
import org.agora.app.util.Money
import org.agora.app.ui.components.SecondaryStyle
import org.agora.app.ui.components.ConfirmSheet
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.DeleteOutline
import org.agora.app.data.model.StandingOrder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Euro
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch
import org.agora.app.R
import org.agora.app.data.model.Person
import org.agora.app.data.model.parseAmount
import org.agora.app.ui.components.AgoraSheet
import org.agora.app.ui.components.AgoraTextField
import org.agora.app.ui.components.AmountField
import org.agora.app.ui.components.ButtonLabel
import org.agora.app.ui.components.DateField
import org.agora.app.ui.components.DropdownField
import org.agora.app.ui.components.LocalContainer
import org.agora.app.ui.components.PickedFile
import org.agora.app.ui.components.SecondaryButton
import org.agora.app.ui.components.SheetActions
import org.agora.app.ui.components.SheetFileRow
import org.agora.app.ui.components.SheetToggleRow
import org.agora.app.ui.components.readPickedFile
import org.agora.app.ui.components.rememberActionRunner
import org.agora.app.ui.components.rememberSheetController
import org.agora.app.ui.components.todayIso
import org.agora.app.ui.theme.Agora

@Composable
internal fun BookPaymentSheet(person: Person, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val container = LocalContainer.current
    val sheet = rememberSheetController(onDismiss)
    val runner = rememberActionRunner()
    var amount by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf(todayIso()) }
    var note by rememberSaveable { mutableStateOf("") }
    var standingOrder by rememberSaveable { mutableStateOf(false) }
    val bookedMsg = stringResource(R.string.toast_payment_booked)
    AgoraSheet(
        title = stringResource(R.string.book_payment),
        subtitle = person.name,
        icon = Icons.Outlined.Payments,
        iconTint = Agora.colors.success,
        onDismiss = onDismiss,
        controller = sheet,
        actions = {
            SheetActions(
                confirmLabel = stringResource(R.string.book),
                onConfirm = {
                    parseAmount(amount)?.let { value ->
                        runner.run(bookedMsg) {
                            container.repo.bookPayment(person.id, value, date, note.trim(), standingOrder)
                            onSaved()
                            sheet.dismiss()
                        }
                    }
                },
                onCancel = { sheet.dismiss() },
                enabled = (parseAmount(amount) ?: 0.0) > 0 && date.isNotBlank(),
                busy = runner.busy
            )
        }
    ) {
        AmountField(stringResource(R.string.amount), amount, { amount = it })
        DateField(stringResource(if (standingOrder) R.string.start_date else R.string.date), date, { date = it })
        AgoraTextField(note, { note = it }, label = { Text(stringResource(R.string.note)) }, modifier = Modifier.fillMaxWidth())
        SheetToggleRow(stringResource(R.string.is_standing_order), standingOrder, { standingOrder = it }, icon = Icons.Outlined.Autorenew)
    }
}

@Composable
internal fun ChangeStatusSheet(person: Person, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val container = LocalContainer.current
    val sheet = rememberSheetController(onDismiss)
    val runner = rememberActionRunner()
    var status by rememberSaveable { mutableStateOf(person.status.takeIf { it in MEMBER_STATUSES } ?: "vollverdiener") }
    var date by rememberSaveable { mutableStateOf(todayIso()) }
    val doneMsg = stringResource(R.string.toast_status_changed)
    AgoraSheet(
        title = stringResource(R.string.change_status),
        subtitle = person.name,
        icon = Icons.Outlined.SwapHoriz,
        onDismiss = onDismiss,
        controller = sheet,
        actions = {
            SheetActions(
                confirmLabel = stringResource(R.string.save),
                onConfirm = {
                    runner.run(doneMsg) {
                        container.repo.changeStatus(person.id, status, date)
                        onSaved()
                        sheet.dismiss()
                    }
                },
                onCancel = { sheet.dismiss() },
                busy = runner.busy
            )
        }
    ) {
        DropdownField(stringResource(R.string.new_status), MEMBER_STATUSES, status, { statusLabel(it) }, { status = it })
        DateField(stringResource(R.string.change_status_date), date, { date = it })
        Text(stringResource(R.string.change_status_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Treasurer: record a donation or an expense (with receipts). */
@Composable
fun FinanceEntrySheet(kind: String, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val container = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheet = rememberSheetController(onDismiss)
    val runner = rememberActionRunner()
    val isExpense = kind == "expense"
    var amount by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf(todayIso()) }
    var description by rememberSaveable { mutableStateOf("") }
    val receipts = remember { mutableStateListOf<PickedFile>() }
    val savedMsg = stringResource(if (isExpense) R.string.toast_expense_saved else R.string.toast_donation_saved)
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(5)) { uris ->
        scope.launch { uris.forEach { uri -> readPickedFile(context, uri)?.let { receipts += it } } }
    }
    val value = parseAmount(amount)
    val valid = value != null && value > 0 && name.isNotBlank() && date.isNotBlank() && (!isExpense || description.isNotBlank())
    AgoraSheet(
        title = stringResource(if (isExpense) R.string.action_book_expense else R.string.action_capture_donation),
        icon = if (isExpense) Icons.Outlined.Euro else Icons.Outlined.FavoriteBorder,
        iconTint = if (isExpense) Agora.colors.danger else Color(0xFF8B5CF6),
        onDismiss = onDismiss,
        controller = sheet,
        actions = {
            SheetActions(
                confirmLabel = stringResource(R.string.save),
                onConfirm = {
                    runner.run(savedMsg) {
                        if (isExpense) {
                            val files = receipts.map { container.repo.uploadReceipt(name.trim(), date, it.name, it.mime, it.bytes) }
                            container.repo.addExpense(value!!, name.trim(), date, description.trim(), files)
                        } else container.repo.addDonation(value!!, name.trim(), date, description)
                        onSaved()
                        sheet.dismiss()
                    }
                },
                onCancel = { sheet.dismiss() },
                enabled = valid,
                busy = runner.busy
            )
        }
    ) {
        AmountField(stringResource(R.string.amount), amount, { amount = it })
        AgoraTextField(name, { name = it }, label = { Text(stringResource(if (isExpense) R.string.issuer else R.string.donor_name)) },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        DateField(stringResource(R.string.date), date, { date = it })
        AgoraTextField(description, { description = it }, label = { Text(stringResource(R.string.description)) }, modifier = Modifier.fillMaxWidth())
        if (isExpense) {
            receipts.toList().forEach { file -> SheetFileRow(file.name) { receipts.remove(file) } }
            SecondaryButton(onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.fillMaxWidth()) {
                ButtonLabel(stringResource(R.string.add_receipts), Icons.Outlined.AttachFile)
            }
        }
    }
}

/**
 * Treasurer: end a standing order on a chosen day or remove it, like the PWA's "Dauerauftrag verwalten".
 * An end date in the past ends it retroactively (later automatic payments are taken back).
 */
@Composable
internal fun ManageStandingOrderSheet(person: Person, order: StandingOrder, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val container = LocalContainer.current
    val sheet = rememberSheetController(onDismiss)
    val runner = rememberActionRunner()
    val locale = Dates.locale(LocalContext.current)
    var endDate by rememberSaveable { mutableStateOf(order.endDate?.takeIf { it.isNotBlank() } ?: todayIso()) }
    var confirmDelete by remember { mutableStateOf(false) }
    val updatedMsg = stringResource(R.string.toast_so_updated)
    val deletedMsg = stringResource(R.string.toast_so_deleted)
    val retroactive = Dates.parse(endDate)?.isBefore(java.time.LocalDate.now()) == true
    AgoraSheet(
        title = stringResource(R.string.so_manage_title),
        subtitle = "${person.name} · ${Money.format(order.amount)} ${stringResource(R.string.per_month)}",
        icon = Icons.Outlined.Autorenew,
        onDismiss = onDismiss,
        controller = sheet,
        actions = {
            SheetActions(
                confirmLabel = stringResource(R.string.so_end_save),
                confirmIcon = Icons.Outlined.EventBusy,
                onConfirm = {
                    runner.run(updatedMsg) {
                        container.repo.endStandingOrder(person.id, order.id, endDate)
                        onSaved()
                        sheet.dismiss()
                    }
                },
                onCancel = { sheet.dismiss() },
                enabled = endDate.isNotBlank(),
                busy = runner.busy
            )
        }
    ) {
        Text(
            stringResource(R.string.since_date, Dates.short(order.startDate, locale)) + (order.note.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        DateField(stringResource(R.string.end_date), endDate, { endDate = it })
        Text(
            stringResource(if (retroactive) R.string.so_end_hint_past else R.string.so_end_hint),
            style = MaterialTheme.typography.bodySmall,
            color = if (retroactive) Agora.colors.warning else MaterialTheme.colorScheme.onSurfaceVariant
        )
        SecondaryButton(onClick = { confirmDelete = true }, enabled = !runner.busy, style = SecondaryStyle.Danger, modifier = Modifier.fillMaxWidth()) {
            ButtonLabel(stringResource(R.string.so_delete), Icons.Outlined.DeleteOutline)
        }
    }
    if (confirmDelete) ConfirmSheet(
        title = stringResource(R.string.so_delete),
        text = stringResource(R.string.so_delete_confirm),
        confirmLabel = stringResource(R.string.delete),
        destructive = true,
        onConfirm = {
            runner.run(deletedMsg) {
                container.repo.deleteStandingOrder(person.id, order.id)
                onSaved()
                sheet.dismiss()
            }
        },
        onDismiss = { confirmDelete = false }
    )
}
