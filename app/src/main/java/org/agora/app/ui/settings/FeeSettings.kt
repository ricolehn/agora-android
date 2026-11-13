package org.agora.app.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.agora.app.R
import org.agora.app.data.model.FEE_STATUSES
import org.agora.app.data.model.FeeChange
import org.agora.app.data.model.FeeChangeImpact
import org.agora.app.data.model.FeeEntry
import org.agora.app.data.model.FeeImpactSide
import org.agora.app.data.model.FeePlan
import org.agora.app.data.model.FeeResetImpact
import org.agora.app.data.model.FeeResets
import org.agora.app.data.model.FeeStretch
import org.agora.app.data.model.User
import org.agora.app.data.model.parseTypedAmount
import org.agora.app.data.remote.ApiException
import org.agora.app.ui.components.AgoraCard
import org.agora.app.ui.components.AgoraSheet
import org.agora.app.ui.components.AgoraTextField
import org.agora.app.ui.components.AmountField
import org.agora.app.ui.components.ButtonProgress
import org.agora.app.ui.components.CapsLabel
import org.agora.app.ui.components.DateField
import org.agora.app.ui.components.DropdownField
import org.agora.app.ui.components.IconTile
import org.agora.app.ui.components.LoadingBox
import org.agora.app.ui.components.LocalContainer
import org.agora.app.ui.components.PrimaryButton
import org.agora.app.ui.components.SecondaryButton
import org.agora.app.ui.components.SheetActions
import org.agora.app.ui.components.rememberActionRunner
import org.agora.app.ui.components.rememberSheetController
import org.agora.app.ui.finance.FeeLogic
import org.agora.app.ui.finance.FeeResetIcon
import org.agora.app.ui.finance.statusName
import org.agora.app.ui.theme.Agora
import org.agora.app.util.Dates
import org.agora.app.util.Money
import java.time.Month
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle

// Settings -> Monthly fees (server CALDERA): the fees over time and the fresh start of the fee accounts, like the web
// app's fees page. Everyone who may open the page reads them; only system admins change them, always in two steps:
// first what it does (the server works the numbers out for everyone), then saving it.

/** What the page knows: still loading, a server from before the fees had a history, or what it answered. */
private sealed interface FeePage {
    data object Loading : FeePage
    data object Legacy : FeePage
    data class Failed(val message: String) : FeePage
    data class Ready(val plan: FeePlan, val resets: FeeResets?) : FeePage
}

/** The dialog for the fees: new fees, an entry edited ([entry]), or a change taken back (with its [impact] already). */
private data class FeeDialog(val adding: Boolean, val entry: FeeEntry? = null, val remove: String? = null, val impact: FeeChangeImpact? = null)

/**
 * The fees page. [legacy]: what a server from before the fees over time gets - the fee settings as before.
 */
@Composable
internal fun FeesPage(user: User, legacy: @Composable () -> Unit) {
    val container = LocalContainer.current
    val repo = container.repo
    val admin = user.isAdmin
    val loadFailed = stringResource(R.string.error_generic)
    var page by remember { mutableStateOf<FeePage>(FeePage.Loading) }
    val scope = rememberCoroutineScope()
    val runner = rememberActionRunner()

    suspend fun load() {
        page = try {
            coroutineScope {
                val plan = async { repo.feePlan() }
                val resets = async { runCatching { repo.feeResets() }.getOrNull() }
                plan.await()?.let { FeePage.Ready(it, resets.await()) } ?: FeePage.Legacy
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // What is shown stays; only a page that never loaded says it failed
            (page as? FeePage.Ready) ?: FeePage.Failed((e as? ApiException)?.message ?: loadFailed)
        }
    }
    LaunchedEffect(Unit) { load() }
    // Fees or fresh starts changed on another device (the server says "all")
    LaunchedEffect(Unit) { container.store.serverChanges.collect { areas -> if ("all" in areas) load() } }

    var dialog by remember { mutableStateOf<FeeDialog?>(null) }
    var resetReview by remember { mutableStateOf<Pair<FeeResetImpact, Boolean>?>(null) }
    // After a change: the page and every account (what a month costs and what is owed are the server's numbers)
    val afterChange: () -> Unit = { scope.launch { load(); container.store.refreshAll() } }

    when (val current = page) {
        FeePage.Loading -> LoadingBox()
        FeePage.Legacy -> legacy()
        is FeePage.Failed -> AgoraCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Info, null, tint = MaterialTheme.colorScheme.error)
                Text(current.message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(start = 10.dp))
                SecondaryButton(onClick = { page = FeePage.Loading; scope.launch { load() } }) { Text(stringResource(R.string.fee_retry)) }
            }
        }
        is FeePage.Ready -> Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            FeePlanCard(
                current.plan, admin,
                onAdd = { dialog = FeeDialog(adding = true) },
                onEdit = { entry -> dialog = FeeDialog(adding = false, entry = entry) },
                onRemove = { from ->
                    runner.run { dialog = FeeDialog(adding = false, remove = from, impact = repo.previewFeeChange(FeeChange(from, remove = true))) }
                }
            )
            val resets = current.resets
            // Treasurers see the days their lists count from; only system admins get the controls
            if (resets != null && (admin || resets.resets.isNotEmpty())) FeeResetCard(resets, admin, runner.busy) { date, remove ->
                runner.run { resetReview = repo.previewFeeReset(date, remove) to remove }
            }
        }
    }

    val plan = (page as? FeePage.Ready)?.plan
    dialog?.let { open ->
        if (plan != null) FeeChangeSheet(plan, open, onDismiss = { dialog = null }, onSaved = afterChange, onOutdated = { scope.launch { load() } })
    }
    resetReview?.let { (impact, remove) ->
        FeeResetSheet(impact, remove, onDismiss = { resetReview = null }, onSaved = afterChange)
    }
}

// ---------- shared pieces ----------

@Composable
private fun monthText(month: String?): String {
    val locale = Dates.locale(LocalContext.current)
    return FeeLogic.month(month)?.let { Dates.monthYear(it, locale) } ?: month.orEmpty()
}

/** "1. November 2026" / "November 1, 2026". */
@Composable
private fun longDateText(day: String): String {
    val locale = Dates.locale(LocalContext.current)
    return Dates.parse(day)?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale)) ?: day
}

/** "50,00 € / 25,00 € / 10,00 €": the fees in the order of the fee settings. */
private fun feeListText(fees: Map<String, Double>?): String =
    FEE_STATUSES.mapNotNull { fees?.get(it) }.joinToString(" / ") { Money.format(it) }

/** "Full earner 50,00 € → 60,00 €" for every status whose fee differs between [before] and [after]. */
@Composable
private fun feeDifferences(before: Map<String, Double>, after: Map<String, Double>): List<Pair<String, String>> =
    FEE_STATUSES.filter { (before[it] ?: 0.0) != (after[it] ?: 0.0) }
        .map { statusName(it) to "${Money.format(before[it] ?: 0.0)} → ${Money.format(after[it] ?: 0.0)}" }

/** A fresh start and a change of the fees say what they are today with the same three words. */
@Composable
private fun stateLabel(state: String): Pair<String, Color> = when (state) {
    "active", "current" -> stringResource(R.string.fee_state_active) to Agora.colors.success
    "scheduled", "planned" -> stringResource(R.string.fee_state_planned) to MaterialTheme.colorScheme.primary
    else -> stringResource(R.string.fee_state_replaced) to MaterialTheme.colorScheme.onSurfaceVariant
}

private fun ended(state: String) = state !in setOf("active", "current", "scheduled", "planned")

@Composable
private fun StatePill(state: String) {
    val (text, color) = stateLabel(state)
    Text(text, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1,
        modifier = Modifier.clip(CircleShape).background(color.copy(alpha = 0.12f)).padding(horizontal = 9.dp, vertical = 2.dp))
}

/** Icon, title and one line under it, at the top of both cards (web: .fee-reset-head). */
@Composable
private fun CardHead(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, title: String, description: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconTile(icon, tint, size = 40.dp)
        Column(Modifier.padding(start = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Agora.colors.heading)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Hint(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) =
    Text(text, style = MaterialTheme.typography.bodySmall, color = color, modifier = modifier)

/** One row of a list in the cards: text on the left, then the state and the admin's buttons. */
@Composable
private fun EntryRow(
    title: String, state: String, facts: List<String>,
    onEdit: (() -> Unit)? = null, onRemove: (() -> Unit)? = null, enabled: Boolean = true
) {
    Row(Modifier.fillMaxWidth().alpha(if (ended(state)) 0.62f else 1f).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = Agora.colors.heading, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp))
                StatePill(state)
            }
            facts.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp)) }
        }
        if (onEdit != null) IconButton(onClick = onEdit, enabled = enabled) {
            Icon(Icons.Outlined.Edit, stringResource(R.string.edit), Modifier.size(19.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (onRemove != null) IconButton(onClick = onRemove, enabled = enabled) {
            Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.fee_take_back), Modifier.size(19.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Who set or took back what, the newest first (at most ten), folded away under "Changes". */
@Composable
private fun ChangeLog(lines: List<Pair<String, Long?>>) {
    if (lines.isEmpty()) return
    val locale = Dates.locale(LocalContext.current)
    var open by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.padding(top = 6.dp)) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { open = !open }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.fee_log_title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AnimatedVisibility(open) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                lines.forEach { (text, at) ->
                    Column {
                        Text(text, style = MaterialTheme.typography.bodySmall, color = Agora.colors.heading)
                        if (at != null) Text(Dates.dateTime(at, locale), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** The fees of the three statuses as tiles (web: .fee-plan-now). */
@Composable
internal fun FeeTiles(fees: Map<String, Double>, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FEE_STATUSES.forEach { status ->
            Column(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(14.dp)).background(Agora.colors.surfaceAlt)
                    .border(1.dp, Agora.colors.hairline, RoundedCornerShape(14.dp)).padding(horizontal = 8.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(statusName(status), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center, maxLines = 2)
                Text(Money.format(fees[status] ?: 0.0), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Agora.colors.heading, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

/** The numbers of a dialog as tiles: the amount, what it is, how many members. One without members stays grey. */
@Composable
private fun ImpactTile(side: FeeImpactSide, label: String, tone: Color, modifier: Modifier = Modifier) {
    val color = if (side.count > 0) tone else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = if (side.count > 0) 0.1f else 0.06f))
            .border(1.dp, color.copy(alpha = 0.25f), RoundedCornerShape(14.dp)).padding(horizontal = 10.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(Money.format(side.total), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = color, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelMedium, color = Agora.colors.heading, textAlign = TextAlign.Center)
        Text(if (side.count == 0) stringResource(R.string.fee_members_none) else pluralStringResource(R.plurals.fee_members, side.count, side.count),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ImpactTiles(tiles: List<@Composable (Modifier) -> Unit>) {
    if (tiles.isEmpty()) return
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        tiles.forEach { it(Modifier.weight(1f).fillMaxHeight()) }
    }
}

/** A few short lines under the numbers. */
@Composable
private fun Facts(facts: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        facts.filter { it.isNotBlank() }.forEach { fact ->
            Row {
                Text("•", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 8.dp))
                Text(fact, style = MaterialTheme.typography.bodyMedium, color = Agora.colors.heading)
            }
        }
    }
}

// ---------- the fees over time ----------

@Composable
private fun FeePlanCard(plan: FeePlan, admin: Boolean, onAdd: () -> Unit, onEdit: (FeeEntry) -> Unit, onRemove: (String) -> Unit) {
    AgoraCard {
        CardHead(Icons.Outlined.Payments, Agora.colors.success, stringResource(R.string.monthly_fees), stringResource(R.string.fees_desc))
        // What a month costs now, and since when
        CapsLabel(plan.since?.let { stringResource(R.string.fees_since, monthText(it)) } ?: stringResource(R.string.fees_now), Modifier.padding(top = 16.dp, bottom = 8.dp))
        FeeTiles(plan.current)
        // The change still to come: only the fees that will differ
        plan.next?.let { next ->
            val differing = FEE_STATUSES.filter { (plan.current[it] ?: 0.0) != (next.fees[it] ?: 0.0) }
            val fees = if (differing.isEmpty()) feeListText(next.fees) else differing.map { "${statusName(it)} ${Money.format(next.fees[it] ?: 0.0)}" }.joinToString(", ")
            Row(
                Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.fees_next, monthText(next.from), fees), style = MaterialTheme.typography.bodySmall,
                    color = Agora.colors.heading, modifier = Modifier.padding(start = 8.dp))
            }
        }
        if (admin) {
            SecondaryButton(onClick = onAdd, modifier = Modifier.padding(top = 14.dp).fillMaxWidth()) { Text(stringResource(R.string.fees_change_btn)) }
            Hint(stringResource(R.string.fees_change_hint), Modifier.padding(top = 6.dp))
        } else Hint(stringResource(R.string.fee_readonly), Modifier.padding(top = 12.dp))

        // Over time, the newest first: the month a change counts from and what it changed - the fees from the
        // beginning with all three amounts. One fee for all time and nobody to change it: the tiles say it all
        if (admin || plan.entries.size > 1) {
            CapsLabel(stringResource(R.string.fees_history_title), Modifier.padding(top = 18.dp, bottom = 2.dp))
            val rows = plan.entries.mapIndexed { i, entry -> Triple(i, entry, plan.entries.getOrNull(i - 1)) }.reversed()
            rows.forEachIndexed { index, (_, entry, previous) ->
                if (index > 0) HorizontalDivider(color = Agora.colors.hairline)
                val changed = FeeLogic.changedStatuses(previous, entry)
                val facts = buildList {
                    if (changed.isEmpty()) add(feeListText(entry.fees))
                    else changed.forEach { add("${statusName(it)} ${Money.format(previous?.fees?.get(it) ?: 0.0)} → ${Money.format(entry.fees[it] ?: 0.0)}") }
                    if (entry.setByName.isNotBlank()) add(stringResource(R.string.fee_set_by, entry.setByName))
                }
                EntryRow(
                    title = entry.from?.let { monthText(it) } ?: stringResource(R.string.fees_from_start),
                    state = entry.state, facts = facts,
                    onEdit = if (admin) ({ onEdit(entry) }) else null,
                    onRemove = if (admin && entry.from != null) ({ onRemove(entry.from) }) else null
                )
            }
        }
        val unknown = stringResource(R.string.fee_unknown_person)
        ChangeLog(plan.log.reversed().take(10).map { entry ->
            val name = entry.byName.ifBlank { unknown }
            when {
                entry.action == "removed" -> stringResource(R.string.fees_log_removed, name, monthText(entry.from))
                entry.from != null -> stringResource(R.string.fees_log_set, name, monthText(entry.from), feeListText(entry.fees))
                else -> stringResource(R.string.fees_log_set_start, name, feeListText(entry.fees))
            } to entry.at
        })
    }
}

/**
 * Change the fees in two steps: the fees and the month they count from, then what that does in numbers (which
 * months cost something else, who owes more or less today) - only then it is saved. Taking a change back opens the
 * second step right away.
 */
@Composable
private fun FeeChangeSheet(plan: FeePlan, dialog: FeeDialog, onDismiss: () -> Unit, onSaved: () -> Unit, onOutdated: () -> Unit) {
    val container = LocalContainer.current
    val locale = Dates.locale(LocalContext.current)
    val sheet = rememberSheetController(onDismiss)
    val runner = rememberActionRunner()
    val base = !dialog.adding && dialog.remove == null && dialog.entry?.from == null
    val replaces = dialog.entry?.from
    // New fees count from next month unless the admin says otherwise: nobody is behind for a month that has begun
    var month by rememberSaveable { mutableStateOf(dialog.entry?.from ?: FeeLogic.addMonths(plan.month, 1)) }
    val startFees = dialog.entry?.fees ?: FeeLogic.feesOfMonth(plan, month)
    val amounts = FEE_STATUSES.map { status -> rememberSaveable(status) { mutableStateOf(Money.typed(startFees[status] ?: 0.0, locale)) } }
    // Amounts the admin typed stay when another month is chosen; the others take that month's fees along
    val touched = remember { mutableStateOf(setOf<String>()) }
    var impact by remember { mutableStateOf(dialog.impact) }
    var change by remember { mutableStateOf(dialog.remove?.let { FeeChange(it, remove = true) }) }

    val savedMsg = stringResource(if (dialog.remove != null) R.string.fees_removed else R.string.fees_saved)
    fun chooseMonth(next: String) {
        month = next
        if (dialog.adding) {
            val fees = FeeLogic.feesOfMonth(plan, next)
            FEE_STATUSES.forEachIndexed { i, status -> if (status !in touched.value) amounts[i].value = Money.typed(fees[status] ?: 0.0, locale) }
        }
    }
    val typed = FEE_STATUSES.indices.map { parseTypedAmount(amounts[it].value) }
    val valid = typed.all { it != null && it >= 0 }

    val review = impact
    // The month it is about: none while new fees are entered, "From the beginning" for those fees
    val about = when {
        dialog.remove != null -> dialog.remove
        review != null -> change?.from
        else -> replaces
    }
    val subtitle = if (review == null && dialog.adding) null
        else about?.let { stringResource(R.string.fees_stretch_from, monthText(it)) } ?: stringResource(R.string.fees_from_start)
    AgoraSheet(
        title = stringResource(when {
            dialog.remove != null -> R.string.fees_remove_title
            dialog.adding -> R.string.fees_change_title
            else -> R.string.fees_edit_title
        }),
        subtitle = subtitle,
        icon = Icons.Outlined.Payments,
        iconTint = Agora.colors.success,
        onDismiss = onDismiss,
        controller = sheet,
        actions = {
            if (review == null) SheetActions(
                confirmLabel = stringResource(R.string.fees_review_btn),
                onConfirm = {
                    val wanted = FeeChange(
                        from = if (base) null else month,
                        fees = FEE_STATUSES.zip(typed).associate { (status, amount) -> status to (amount ?: 0.0) },
                        replaces = replaces
                    )
                    runner.run { impact = container.repo.previewFeeChange(wanted); change = wanted }
                },
                onCancel = { sheet.dismiss() },
                enabled = valid, busy = runner.busy
            )
            else {
                // Taking a change back has no first step to go back to
                SecondaryButton(onClick = { if (dialog.remove != null) sheet.dismiss() else impact = null }, enabled = !runner.busy,
                    modifier = Modifier.weight(1f).height(46.dp)) { Text(stringResource(if (dialog.remove != null) R.string.cancel else R.string.back)) }
                PrimaryButton(onClick = {
                    val wanted = change ?: return@PrimaryButton
                    runner.run(savedMsg) {
                        try {
                            container.repo.saveFeeChange(wanted, review.version)
                        } catch (e: ApiException) {
                            // Someone else changed the fees since the numbers were shown: nothing was saved (the server
                            // says so), the page shows what is there now - and this dialog checks the fees again
                            if (e.status == 409) {
                                onOutdated()
                                if (dialog.remove != null) sheet.dismiss() else impact = null
                            }
                            throw e
                        }
                        onSaved()
                        sheet.dismiss()
                    }
                }, enabled = !runner.busy, danger = dialog.remove != null, modifier = Modifier.weight(1f).height(46.dp)) {
                    if (runner.busy) ButtonProgress() else Text(stringResource(if (dialog.remove != null) R.string.fee_take_back else R.string.save))
                }
            }
        }
    ) {
        if (review == null) {
            CapsLabel(stringResource(R.string.fees_form_amounts))
            FEE_STATUSES.forEachIndexed { i, status ->
                AmountField(statusName(status), amounts[i].value, { amounts[i].value = it; touched.value = touched.value + status })
            }
            if (!base) {
                CapsLabel(stringResource(R.string.fees_form_from), Modifier.padding(top = 6.dp))
                val chosen = YearMonth.parse(month)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DropdownField(stringResource(R.string.fees_form_month), Month.entries.toList(), chosen.month,
                        { it.getDisplayName(TextStyle.FULL_STANDALONE, locale).replaceFirstChar { c -> c.titlecase(locale) } },
                        { chooseMonth(chosen.withMonth(it.value).toString()) }, Modifier.weight(1.4f))
                    DropdownField(stringResource(R.string.fees_form_year), FeeLogic.selectableYears(plan.month, month), chosen.year,
                        { it.toString() }, { chooseMonth(chosen.withYear(it).toString()) }, Modifier.weight(1f))
                }
            }
            // Under the month: what choosing it means - a month still to come moves nothing yet, one that has passed reaches back
            val hint = FeeLogic.monthHint(plan, month, base, replaces)
            Hint(
                when (hint) {
                    is FeeLogic.MonthHint.FromStart -> hint.firstChange?.let { stringResource(R.string.fees_hint_start_until, monthText(it)) } ?: stringResource(R.string.fees_hint_start)
                    is FeeLogic.MonthHint.Taken -> stringResource(R.string.fees_hint_taken, monthText(hint.month))
                    is FeeLogic.MonthHint.Later -> stringResource(R.string.fees_hint_later, monthText(hint.month))
                    FeeLogic.MonthHint.Now -> stringResource(R.string.fees_hint_now)
                    is FeeLogic.MonthHint.Back -> stringResource(R.string.fees_hint_back, monthText(hint.month))
                },
                color = if (hint is FeeLogic.MonthHint.Back) Agora.colors.warning else MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else FeeChangeReview(review)
    }
}

/** The second step: which months cost something else, who owes more or less today (as tiles), and a few short lines. */
@Composable
private fun FeeChangeReview(impact: FeeChangeImpact) {
    CapsLabel(stringResource(R.string.fees_effects))
    impact.stretches.forEach { stretch -> StretchCard(stretch) }
    // A change still to come moves no account today: no tiles, its line says so
    ImpactTiles(buildList {
        if (impact.more.count > 0) add { m: Modifier -> ImpactTile(impact.more, stringResource(R.string.fee_tile_more), Agora.colors.danger, m) }
        if (impact.less.count > 0) add { m: Modifier -> ImpactTile(impact.less, stringResource(R.string.fee_tile_less), Agora.colors.success, m) }
    })
    val firstMonth = impact.stretches.firstOrNull()?.first
    val locale = Dates.locale(LocalContext.current)
    Facts(listOf(
        when {
            !impact.inEffect && firstMonth != null -> stringResource(R.string.fees_hint_later, monthText(firstMonth))
            impact.more.count > 0 || impact.less.count > 0 -> stringResource(R.string.fees_fact_now)
            else -> stringResource(R.string.fees_fact_now_none)
        },
        impact.freshStart?.let { stringResource(R.string.fees_fact_fresh_start, Dates.short(it, locale)) }.orEmpty(),
        stringResource(R.string.fee_reset_fact_untouched)
    ))
}

/** One stretch of months with every fee that differs on a line of its own: "Full earner   50,00 € → 60,00 €". */
@Composable
private fun StretchCard(stretch: FeeStretch) {
    val title = when {
        stretch.first == null && stretch.last == null -> stringResource(R.string.fees_stretch_all)
        stretch.first == null -> stringResource(R.string.fees_stretch_until, monthText(stretch.last))
        stretch.last == null -> stringResource(R.string.fees_stretch_from, monthText(stretch.first))
        stretch.first == stretch.last -> monthText(stretch.first)
        else -> stringResource(R.string.fees_stretch_range, monthText(stretch.first), monthText(stretch.last))
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Agora.colors.surfaceAlt)
            .border(1.dp, Agora.colors.hairline, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = Agora.colors.heading)
        feeDifferences(stretch.before, stretch.after).forEach { (label, change) ->
            Row(Modifier.fillMaxWidth().padding(top = 3.dp)) {
                Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text(change, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold), color = Agora.colors.heading)
            }
        }
    }
}

// ---------- the fresh start ----------

@Composable
private fun FeeResetCard(resets: FeeResets, admin: Boolean, busy: Boolean, onReview: (date: String, remove: Boolean) -> Unit) {
    // Emptied once a fresh start was set or taken back
    var day by rememberSaveable(resets.log.size, resets.resets.size) { mutableStateOf("") }
    AgoraCard {
        CardHead(FeeResetIcon, Agora.colors.warning, stringResource(R.string.fee_reset_title), stringResource(R.string.fee_reset_desc))
        // The newest first: the day and who set it (what the months cost is the card above's business)
        val rows = resets.resets.sortedByDescending { it.date }
        if (rows.isEmpty()) Hint(stringResource(R.string.fee_reset_none), Modifier.padding(top = 14.dp))
        else Column(Modifier.padding(top = 8.dp)) {
            rows.forEachIndexed { index, reset ->
                if (index > 0) HorizontalDivider(color = Agora.colors.hairline)
                EntryRow(
                    title = longDateText(reset.date), state = reset.state,
                    facts = listOfNotNull(reset.setByName.takeIf { it.isNotBlank() }?.let { stringResource(R.string.fee_set_by, it) }),
                    onRemove = if (admin) ({ onReview(reset.date, true) }) else null, enabled = !busy
                )
            }
        }
        if (admin) {
            DateField(stringResource(R.string.fee_reset_date_label), day, { day = it }, Modifier.padding(top = 12.dp).fillMaxWidth())
            Hint(stringResource(R.string.fee_reset_form_hint), Modifier.padding(top = 6.dp))
            SecondaryButton(onClick = { onReview(day, false) }, enabled = day.isNotBlank() && !busy, modifier = Modifier.padding(top = 10.dp).fillMaxWidth()) {
                Text(stringResource(R.string.fee_reset_review_btn))
            }
        } else Hint(stringResource(R.string.fee_readonly), Modifier.padding(top = 12.dp))
        val locale = Dates.locale(LocalContext.current)
        val unknown = stringResource(R.string.fee_unknown_person)
        ChangeLog(resets.log.reversed().take(10).map { entry ->
            val name = entry.byName.ifBlank { unknown }
            val date = Dates.short(entry.date, locale)
            stringResource(if (entry.action == "set") R.string.fee_reset_log_set else R.string.fee_reset_log_removed, name, date) to entry.at
        })
    }
}

/**
 * Confirming a fresh start (or taking one back): the numbers the server worked out as tiles, a few short lines, and
 * the button only works once the day is typed again and the password is entered. Closing it forgets both.
 */
@Composable
private fun FeeResetSheet(impact: FeeResetImpact, remove: Boolean, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val container = LocalContainer.current
    val context = LocalContext.current
    val locale = Dates.locale(context)
    val sheet = rememberSheetController(onDismiss)
    val runner = rememberActionRunner()
    var typed by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val shownDay = FeeLogic.typedDayText(impact.date, locale.language == "de")
    val matches = FeeLogic.typedDayMatches(typed, impact.date)
    val savedMsg = stringResource(if (remove) R.string.fee_reset_removed else R.string.fee_reset_saved)

    AgoraSheet(
        title = stringResource(if (remove) R.string.fee_reset_remove_title else R.string.fee_reset_set_title),
        subtitle = Dates.parse(impact.date)?.let { Dates.long(it, locale) } ?: impact.date,
        icon = FeeResetIcon,
        iconTint = Agora.colors.warning,
        onDismiss = onDismiss,
        controller = sheet,
        actions = {
            SheetActions(
                confirmLabel = stringResource(if (remove) R.string.fee_take_back else R.string.fee_reset_set_btn),
                onConfirm = {
                    runner.run(savedMsg) {
                        // The day goes twice: the server takes nothing that was not confirmed
                        container.repo.saveFeeReset(impact.date, remove, password)
                        onSaved()
                        sheet.dismiss()
                    }
                },
                onCancel = { sheet.dismiss() },
                enabled = matches && password.isNotEmpty(), busy = runner.busy, destructive = true
            )
        }
    ) {
        CapsLabel(stringResource(R.string.fees_effects))
        ImpactTiles(buildList {
            if (remove) {
                // A planned one that changes nothing needs no "nobody" tile: its line says so
                if (impact.inEffect || impact.behind.count > 0) add { m: Modifier -> ImpactTile(impact.behind, stringResource(R.string.fee_tile_more), Agora.colors.danger, m) }
            } else {
                add { m: Modifier -> ImpactTile(impact.behind, stringResource(R.string.fee_tile_dropped), Agora.colors.warning, m) }
                add { m: Modifier -> ImpactTile(impact.ahead, stringResource(R.string.fee_tile_carried), Agora.colors.success, m) }
            }
        })
        Facts(
            if (remove) listOf(
                stringResource(if (impact.inEffect) R.string.fee_reset_undo_fact_all else R.string.fee_reset_undo_fact_planned),
                // Every month costs the same either way, so taking it back takes no credit away
                if (impact.inEffect && impact.ahead.count > 0) stringResource(R.string.fee_reset_undo_fact_ahead, Money.format(impact.ahead.total)) else "",
                stringResource(R.string.fee_reset_fact_untouched)
            ) else listOf(
                // A day still to come: the numbers count the payments booked so far
                if (impact.inEffect) stringResource(R.string.fee_reset_fact_now) else stringResource(R.string.fee_reset_fact_later, longDateText(impact.date)),
                stringResource(R.string.fee_reset_fact_fees, monthText(impact.firstMonth)),
                stringResource(R.string.fee_reset_fact_untouched)
            )
        )
        CapsLabel(stringResource(R.string.fee_reset_confirm_section), Modifier.padding(top = 6.dp))
        AgoraTextField(
            typed, { typed = it }, label = { Text(stringResource(R.string.fee_reset_type_label, shownDay)) }, singleLine = true,
            placeholder = { Text(shownDay) },
            isError = typed.length >= shownDay.length && !matches,
            supportingText = if (typed.length >= shownDay.length && !matches) ({ Text(stringResource(R.string.fee_reset_type_day)) }) else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth()
        )
        AgoraTextField(
            password, { password = it }, label = { Text(stringResource(R.string.fee_reset_password_label)) }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** A server from before the fees over time, for those who may not change the fees: what they are, as tiles. */
@Composable
internal fun LegacyFeeTiles(rates: Map<String, Double>) {
    AgoraCard {
        CardHead(Icons.Outlined.Payments, Agora.colors.success, stringResource(R.string.monthly_fees), stringResource(R.string.fees_desc))
        FeeTiles(rates, Modifier.padding(top = 14.dp))
        Hint(stringResource(R.string.fee_readonly), Modifier.padding(top = 12.dp))
    }
}
