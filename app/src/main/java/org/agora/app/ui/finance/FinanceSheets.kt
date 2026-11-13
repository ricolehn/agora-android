package org.agora.app.ui.finance

import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import org.agora.app.data.BookingEdit
import org.agora.app.data.Bookings
import org.agora.app.data.model.Transaction
import org.agora.app.ui.components.CapsLabel
import org.agora.app.ui.components.FieldShape
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
import org.agora.app.data.model.parseTypedAmount
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
                    parseTypedAmount(amount)?.let { value ->
                        runner.run(bookedMsg) {
                            container.repo.bookPayment(person.id, value, date, note.trim(), standingOrder)
                            onSaved()
                            sheet.dismiss()
                        }
                    }
                },
                onCancel = { sheet.dismiss() },
                enabled = (parseTypedAmount(amount) ?: 0.0) > 0 && date.isNotBlank(),
                busy = runner.busy
            )
        }
    ) {
        AmountField(stringResource(R.string.amount), amount, { amount = it })
        DateField(stringResource(if (standingOrder) R.string.start_date else R.string.date), date, { date = it })
        // Dated before the member's fresh start: it first pays off the open fees dropped then (CALDERA)
        FeeResetNote(if (standingOrder) null else feeResetPaymentHint(person, date))
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
        // Opened from the fee list: plain status names like the list itself
        DropdownField(stringResource(R.string.new_status), MEMBER_STATUSES, status, { statusName(it) }, { status = it })
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
    val value = parseTypedAmount(amount)
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
 * Treasurer: change a booked entry of the history - amount, date, note or description, the donor of a donation or who
 * issued an expense and its receipts - or delete it, like the web app's edit dialog. [person]: the member of a
 * payment, for what a date before their fresh start does. [onChanged]: saved or deleted (or found changed meanwhile),
 * with what to tell the treasurer - by the screen, as this sheet closes (a message shown from here would go with it).
 */
@Composable
internal fun EditBookingSheet(tx: Transaction, person: Person?, onDismiss: () -> Unit, onChanged: (message: String) -> Unit) {
    val container = LocalContainer.current
    val context = LocalContext.current
    val locale = Dates.locale(context)
    val scope = rememberCoroutineScope()
    val sheet = rememberSheetController(onDismiss)
    val runner = rememberActionRunner()
    val isPayment = tx.type == "pay"
    val isExpense = tx.type == "exp"
    val initialAmount = remember(tx) { Money.typed(tx.amount, locale) }
    var amount by rememberSaveable { mutableStateOf(initialAmount) }
    var name by rememberSaveable { mutableStateOf(tx.who) }
    var date by rememberSaveable { mutableStateOf(tx.date.take(10)) }
    var description by rememberSaveable { mutableStateOf(tx.description) }
    // Expenses: the stored receipts (one removed here goes when saving) and the pictures picked to add
    val storedReceipts = remember { mutableStateListOf(*receiptFiles(tx.receipt).toTypedArray()) }
    val newReceipts = remember { mutableStateListOf<PickedFile>() }
    var confirmDelete by remember { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(5)) { uris ->
        scope.launch { uris.forEach { uri -> readPickedFile(context, uri)?.let { newReceipts += it } } }
    }
    val (icon, tint) = txTypeStyle(tx)
    val updatedMsg = stringResource(when (tx.type) { "don" -> R.string.toast_donation_updated; "exp" -> R.string.toast_expense_updated; else -> R.string.toast_payment_updated })
    val deletedMsg = stringResource(when (tx.type) { "don" -> R.string.toast_donation_deleted; "exp" -> R.string.toast_expense_deleted; else -> R.string.toast_payment_deleted })
    val goneMsg = stringResource(R.string.booking_gone)
    // A new amount has to be one that can be booked; an old entry's amount left as it is (also 0 or less) may stay
    val value = parseTypedAmount(amount)
    val valid = value != null && (value > 0 || amount == initialAmount) && Dates.parse(date) != null && (isPayment || name.isNotBlank())

    // Also when the entry was changed or deleted meanwhile (nothing is written then): the history is reloaded and says so.
    // Other errors keep the sheet open with the message.
    fun submit(doneMsg: String, action: suspend () -> Unit) = runner.run {
        val message = try {
            action()
            doneMsg
        } catch (e: Bookings.GoneException) {
            goneMsg
        }
        onChanged(message)
        sheet.dismiss()
    }

    AgoraSheet(
        title = stringResource(when (tx.type) { "don" -> R.string.booking_edit_donation; "exp" -> R.string.booking_edit_expense; else -> R.string.booking_edit_payment }),
        subtitle = tx.who.ifBlank { null },
        icon = icon,
        iconTint = tint,
        onDismiss = onDismiss,
        controller = sheet,
        actions = {
            SheetActions(
                confirmLabel = stringResource(R.string.save),
                onConfirm = {
                    parseTypedAmount(amount)?.let { newAmount ->
                        submit(updatedMsg) {
                            // The receipts are only written when they changed; new ones are uploaded first
                            val receipts = if (!isExpense) null else {
                                val added = newReceipts.map { container.repo.uploadReceipt(name.trim(), date, it.name, it.mime, it.bytes) }
                                (storedReceipts + added).takeIf { added.isNotEmpty() || storedReceipts.toList() != receiptFiles(tx.receipt) }
                            }
                            container.repo.editBooking(tx, BookingEdit(newAmount, date, description.trim(), name.trim().takeUnless { isPayment }, receipts))
                        }
                    }
                },
                onCancel = { sheet.dismiss() },
                enabled = valid,
                busy = runner.busy
            )
        }
    ) {
        AmountField(stringResource(R.string.amount), amount, { amount = it })
        if (!isPayment) AgoraTextField(name, { name = it }, label = { Text(stringResource(if (isExpense) R.string.issuer else R.string.donor_name)) },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        DateField(stringResource(R.string.date), date, { date = it })
        // Dated before the member's fresh start: it first pays off the open fees dropped then (CALDERA)
        if (isPayment) FeeResetNote(feeResetPaymentHint(person, date))
        AgoraTextField(description, { description = it }, label = { Text(stringResource(if (isPayment) R.string.note else R.string.description)) },
            modifier = Modifier.fillMaxWidth())
        if (isExpense) {
            CapsLabel(stringResource(R.string.receipts), Modifier.padding(top = 4.dp))
            storedReceipts.toList().forEach { file -> StoredReceiptRow(container.repo.receiptUrl(file), file) { storedReceipts.remove(file) } }
            newReceipts.toList().forEach { file -> SheetFileRow(file.name) { newReceipts.remove(file) } }
            SecondaryButton(onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.fillMaxWidth()) {
                ButtonLabel(stringResource(R.string.add_receipts), Icons.Outlined.AttachFile)
            }
        }
        SecondaryButton(onClick = { confirmDelete = true }, enabled = !runner.busy, style = SecondaryStyle.Danger, modifier = Modifier.fillMaxWidth()) {
            ButtonLabel(stringResource(R.string.booking_delete), Icons.Outlined.DeleteOutline)
        }
    }
    if (confirmDelete) ConfirmSheet(
        title = stringResource(R.string.booking_delete),
        text = stringResource(R.string.booking_delete_confirm),
        confirmLabel = stringResource(R.string.delete),
        destructive = true,
        onConfirm = { submit(deletedMsg) { container.repo.deleteBooking(tx) } },
        onDismiss = { confirmDelete = false }
    )
}

/** A stored receipt of the expense being edited: its picture, the file name and remove (takes effect on saving). */
@Composable
private fun StoredReceiptRow(url: String, name: String, onRemove: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(FieldShape).background(Agora.colors.surfaceAlt).padding(start = 6.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = receiptImageRequest(url), contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surface)
        )
        Text(name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 10.dp))
        IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Outlined.Close, stringResource(R.string.delete), Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
