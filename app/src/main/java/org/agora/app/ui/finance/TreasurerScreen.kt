package org.agora.app.ui.finance

import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Description
import org.agora.app.ui.components.pagePadding
import org.agora.app.data.model.StandingOrder
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Euro
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.runtime.LaunchedEffect
import org.agora.app.ui.components.AgoraSheet
import org.agora.app.ui.components.CapsLabel
import org.agora.app.ui.components.PageTitle
import org.agora.app.ui.components.SecondaryButton
import org.agora.app.ui.components.AgoraTextField
import org.agora.app.ui.components.ButtonLabel
import org.agora.app.ui.components.AgoraFabMenu
import org.agora.app.ui.components.FabAction
import org.agora.app.ui.components.IconTile
import org.agora.app.ui.components.PillTabs
import org.agora.app.ui.components.PrimaryButton
import org.agora.app.ui.components.SearchField
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.agora.app.AppContainer
import org.agora.app.R
import org.agora.app.data.model.FinanceRequest
import org.agora.app.data.model.FinanceStats
import org.agora.app.data.model.Person
import org.agora.app.data.model.Transaction
import org.agora.app.data.model.User
import org.agora.app.ui.components.AgoraCard
import org.agora.app.ui.components.EmptyState
import org.agora.app.ui.components.GradientCard
import org.agora.app.ui.components.LoadingBox
import org.agora.app.ui.components.LocalContainer
import org.agora.app.ui.components.Pill
import org.agora.app.ui.components.SectionTitle
import org.agora.app.ui.components.TextInputSheet
import org.agora.app.ui.components.UserAvatar
import org.agora.app.ui.components.containerViewModel
import org.agora.app.ui.components.rememberActionRunner
import org.agora.app.ui.theme.Agora
import org.agora.app.util.Dates
import org.agora.app.util.Money

data class TreasurerState(
    val loading: Boolean = true,
    val stats: FinanceStats = FinanceStats(),
    val pending: List<FinanceRequest> = emptyList(),
    val people: List<Person> = emptyList(),
    val transactions: List<Transaction> = emptyList(),
    val page: Int = 1,
    val totalPages: Int = 1,
    val loadingMore: Boolean = false,
    val error: String? = null
)

@OptIn(FlowPreview::class)
class TreasurerViewModel(private val c: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(TreasurerState())
    val state = _state.asStateFlow()
    val search = MutableStateFlow("")
    private var loadJob: Job? = null

    init {
        reload()
        // Finance data only changes with "all" updates (events and chat messages have their own areas)
        viewModelScope.launch { c.store.serverChanges.collect { areas -> if ("all" in areas) reload() } }
        viewModelScope.launch { search.drop(1).debounce(350).distinctUntilChanged().collect { reloadTransactions() } }
    }

    fun reload() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            try {
                coroutineScope {
                    val stats = async { c.repo.stats() }
                    val requests = async { c.repo.allRequests().filter { it.status == "pending" } }
                    val people = async { c.repo.allPeople().sortedBy { it.name.lowercase() } }
                    val tx = async { c.repo.transactions(1, search.value) }
                    val page = tx.await()
                    _state.value = TreasurerState(
                        loading = false, stats = stats.await(), pending = requests.await(), people = people.await(),
                        transactions = page.items, page = page.page, totalPages = page.totalPages
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message) }
            }
        }
    }

    private suspend fun reloadTransactions() {
        runCatching { c.repo.transactions(1, search.value) }.onSuccess { page ->
            _state.update { it.copy(transactions = page.items, page = page.page, totalPages = page.totalPages) }
        }
    }

    fun loadMore() {
        val s = _state.value
        if (s.loadingMore || s.page >= s.totalPages) return
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            val next = runCatching { c.repo.transactions(s.page + 1, search.value) }.getOrNull()
            _state.update {
                if (next == null) it.copy(loadingMore = false)
                else it.copy(transactions = it.transactions + next.items, page = next.page, totalPages = next.totalPages, loadingMore = false)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TreasurerScreen(user: User, contentPadding: PaddingValues) {
    val vm = containerViewModel { TreasurerViewModel(it) }
    val locale = Dates.locale(LocalContext.current)
    val state by vm.state.collectAsStateWithLifecycle()
    val query by vm.search.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(0) }
    var peopleFilter by rememberSaveable { mutableStateOf("") }
    var selectedTx by remember { mutableStateOf<Transaction?>(null) }
    var openRequest by remember { mutableStateOf<FinanceRequest?>(null) }
    var reportOpen by rememberSaveable { mutableStateOf(false) }
    val store = LocalContainer.current.store
    // Expenses show the issuer's picture: their uid from the member records by name (like the web)
    val uidByName = remember(state.people) { state.people.filter { it.uid.isNotBlank() }.associate { it.name.trim().lowercase() to it.uid } }
    var entryKind by rememberSaveable { mutableStateOf<String?>(null) }
    val canManage = user.canManageFinances || user.owner

    Box(Modifier.fillMaxSize()) {
        PullToRefreshBox(isRefreshing = state.loading && state.transactions.isNotEmpty(), onRefresh = vm::reload, modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                contentPadding = pagePadding(contentPadding.calculateTopPadding() + 4.dp, contentPadding.calculateBottomPadding() + 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    PillTabs(
                        listOf(
                            stringResource(R.string.finances_sub_history) to Icons.Outlined.Schedule,
                            stringResource(R.string.finances_sub_members) to Icons.Outlined.People
                        ),
                        selected = tab, onSelect = { tab = it }
                    )
                }
                if (state.loading && state.transactions.isEmpty() && state.people.isEmpty()) item { LoadingBox() }
                else if (tab == 0) {
                    // Same order as the PWA: open requests, balance, history
                    if (state.pending.isNotEmpty()) item(key = "requests") { OpenRequestsCard(state.pending, onOpen = { openRequest = it }) }
                    item {
                        GradientCard(Agora.colors.heroGradient) {
                            Text(
                                stringResource(R.string.total_balance).uppercase(), color = Color.White,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
                                modifier = Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.15f)).padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                            Text(Money.format(state.stats.totalBalance), color = Color.White, style = MaterialTheme.typography.displaySmall,
                                modifier = Modifier.padding(top = 10.dp))
                        }
                    }
                    item {
                        // Financial report as PDF (like the web's "Bericht erstellen")
                        PageTitle(stringResource(R.string.nav_history), Modifier.padding(top = 8.dp)) {
                            SecondaryButton(onClick = { reportOpen = true }, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
                                ButtonLabel(stringResource(R.string.report_create), Icons.Outlined.Description)
                            }
                        }
                    }
                    item { SearchField(query, { vm.search.value = it }, stringResource(R.string.search_transactions)) }
                    if (state.transactions.isEmpty()) item { EmptyState(Icons.Outlined.Receipt, stringResource(R.string.no_transactions)) }
                    // Grouped by day like the PWA's history
                    state.transactions.groupBy { it.date.take(10) }.forEach { (day, txs) ->
                        item(key = "day-$day") {
                            Text(Dates.short(day, locale), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 4.dp, top = 6.dp))
                        }
                        items(txs, key = { "tx-${it.type}-${it.id}" }) { tx ->
                            val avatarUid = when (tx.type) { "pay" -> tx.personUid; "exp" -> uidByName[tx.who.trim().lowercase()]; else -> null }
                            TransactionRow(tx, avatarUid) { selectedTx = tx }
                        }
                    }
                    if (state.page < state.totalPages) item {
                        TextButton(onClick = vm::loadMore, enabled = !state.loadingMore, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.load_more))
                        }
                    }
                } else {
                    // Paying members only, overdue first; cards expand in place like the PWA
                    item { PageTitle(stringResource(R.string.people_title)) }
                    item { SearchField(peopleFilter, { peopleFilter = it }, stringResource(R.string.search_members)) }
                    val paying = state.people.filter { it.pays && (peopleFilter.isBlank() || it.name.contains(peopleFilter.trim(), ignoreCase = true)) }
                        .sortedBy { it.name.lowercase() }
                    val overdue = paying.filter { it.statusMeta.isOverdue }
                    val current = paying.filterNot { it.statusMeta.isOverdue }
                    if (paying.isEmpty()) item { EmptyState(Icons.Outlined.People, stringResource(R.string.no_members)) }
                    if (overdue.isNotEmpty()) {
                        item(key = "h-overdue") { SectionTitle(stringResource(R.string.overdue_header), count = overdue.size, color = Agora.colors.danger) }
                        items(overdue, key = { "person-${it.id}" }) { person -> ExpandablePersonCard(person, canManage, onChanged = vm::reload) }
                    }
                    if (current.isNotEmpty()) {
                        item(key = "h-current") { SectionTitle(stringResource(R.string.current_members_header), count = current.size, color = Agora.colors.success) }
                        items(current, key = { "person-${it.id}" }) { person -> ExpandablePersonCard(person, canManage, onChanged = vm::reload) }
                    }
                }
            }
        }
        // Native M3 "+" menu: record a donation or book an expense
        if (canManage) AgoraFabMenu(
            listOf(
                FabAction(stringResource(R.string.action_capture_donation), Icons.Outlined.VolunteerActivism) { entryKind = "donation" },
                FabAction(stringResource(R.string.action_book_expense), Icons.Outlined.Receipt) { entryKind = "expense" }
            ),
            stringResource(R.string.add),
            Modifier.align(Alignment.BottomEnd).padding(end = 4.dp, bottom = contentPadding.calculateBottomPadding())
        )
    }

    selectedTx?.let { TransactionSheet(it) { selectedTx = null } }
    if (reportOpen) ReportSheet(state.people, onDismiss = { reportOpen = false })
    openRequest?.let { request ->
        RequestDetailSheet(request, canDecide = canManage, onDismiss = { openRequest = null }, onDecided = { vm.reload(); store.refreshInBackground() })
    }
    entryKind?.let { kind -> FinanceEntrySheet(kind, onDismiss = { entryKind = null }, onSaved = vm::reload) }
}

/**
 * History row like the PWA: payments and expenses show the person's picture (initials without one; expenses with a small
 * red badge), donations the purple tile; who, description, signed amount.
 */
@Composable
private fun TransactionRow(tx: Transaction, avatarUid: String?, onClick: () -> Unit) {
    val (icon, tint) = txTypeStyle(tx)
    AgoraCard(onClick = onClick, contentPadding = PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (tx.type != "don" && tx.who.isNotBlank()) Box(Modifier.size(40.dp)) {
                UserAvatar(avatarUid, tx.who, 40.dp)
                if (tx.type == "exp") Box(
                    Modifier.align(Alignment.BottomEnd).offset(3.dp, 3.dp).size(18.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface).padding(2.dp)
                        .clip(CircleShape).background(Agora.colors.danger),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Outlined.Euro, null, Modifier.size(10.dp), tint = Color.White) }
            } else Box(Modifier.size(40.dp).clip(CircleShape).background(tint.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(20.dp), tint = tint)
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(tx.who, style = MaterialTheme.typography.titleSmall, color = Agora.colors.heading, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (tx.description.isNotBlank() || !tx.receipt.isNullOrBlank()) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tx.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (!tx.receipt.isNullOrBlank()) Icon(Icons.Outlined.AttachFile, null, Modifier.padding(start = 4.dp).size(14.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
            Text(
                Money.signed(tx.amount, tx.isIncome),
                style = MaterialTheme.typography.titleSmall,
                color = if (tx.isIncome) Agora.colors.success else Agora.colors.danger
            )
        }
    }
}

@Composable
private fun TransactionSheet(tx: Transaction, onDismiss: () -> Unit) {
    val container = LocalContainer.current
    val locale = Dates.locale(LocalContext.current)
    val receipts = receiptFiles(tx.receipt)
    val (icon, tint) = txTypeStyle(tx)
    val typeLabel = stringResource(when (tx.type) { "exp" -> R.string.tx_type_expense; "don" -> R.string.tx_type_donation; else -> R.string.tx_type_payment })
    val amountColor = if (tx.isIncome) Agora.colors.success else Agora.colors.danger
    AgoraSheet(title = tx.who, subtitle = typeLabel, icon = icon, iconTint = tint, onDismiss = onDismiss) {
        // Amount hero tinted like the transaction type, date and standing-order flag below it
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(amountColor.copy(alpha = 0.08f)).padding(vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(Money.signed(tx.amount, tx.isIncome), style = MaterialTheme.typography.headlineMedium, color = amountColor)
            Text(Dates.short(tx.date, locale), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp))
            if (tx.isAuto) Pill(stringResource(R.string.standing_order), MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
        }
        if (tx.description.isNotBlank()) {
            CapsLabel(stringResource(R.string.description))
            Text(tx.description, style = MaterialTheme.typography.bodyLarge, color = Agora.colors.heading)
        }
        if (receipts.isNotEmpty()) CapsLabel(stringResource(R.string.receipts))
        receipts.forEach { file ->
            AsyncImage(
                model = receiptImageRequest(container.repo.receiptUrl(file)), contentDescription = file, contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
            )
        }
    }
}

/** Type tile of a transaction: payment green, donation purple, expense red. */
@Composable
private fun txTypeStyle(tx: Transaction): Pair<androidx.compose.ui.graphics.vector.ImageVector, Color> = when (tx.type) {
    "exp" -> Icons.Outlined.Euro to Agora.colors.danger
    "don" -> Icons.Outlined.FavoriteBorder to Color(0xFF8B5CF6)
    else -> Icons.Outlined.Person to Agora.colors.success
}

/**
 * Member card of the Beitragsliste like the PWA's `.person-item`: name, status and paid-until pill; tap expands
 * the summary (status, paid until, open amount), standing orders, actions and the combined history in place.
 */
@Composable
private fun ExpandablePersonCard(person: Person, canManage: Boolean, onChanged: () -> Unit) {
    val container = LocalContainer.current
    val locale = Dates.locale(LocalContext.current)
    var expanded by rememberSaveable(person.id) { mutableStateOf(false) }
    var booking by rememberSaveable { mutableStateOf(false) }
    var changingStatus by rememberSaveable { mutableStateOf(false) }
    var managingOrder by remember { mutableStateOf<StandingOrder?>(null) }
    var detail by remember { mutableStateOf<Person?>(null) }
    var reload by remember { mutableStateOf(0) }
    // Load the full record (payments, history) when opened, like the PWA's lazy "Lade Verlauf…"
    LaunchedEffect(expanded, reload, person) {
        if (expanded) detail = runCatching { container.repo.person(person.id) }.getOrNull() ?: person
    }
    val meta = person.statusMeta
    val color = personStatusColor(person)
    val standingOrderCovers = meta.isActiveStandingOrder && !meta.isOverdue

    AgoraCard(contentPadding = PaddingValues(0.dp)) {
        Row(
            Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(person.name, style = MaterialTheme.typography.titleMedium, color = Agora.colors.heading, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false))
                    Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, Modifier.padding(start = 4.dp).size(20.dp),
                        tint = if (expanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                CapsLabel(statusName(person.effectiveStatus), Modifier.padding(top = 2.dp))
            }
            Column(horizontalAlignment = Alignment.End) {
                if (!standingOrderCovers) Text(
                    paidUntilText(person), color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.clip(CircleShape).background(color).padding(horizontal = 10.dp, vertical = 3.dp)
                )
                Text(statusMetaText(meta.text), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 3.dp))
            }
        }
        AnimatedVisibility(expanded) {
            val p = detail ?: person
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // Summary: status, paid until, open amount
                    val summaryShape = RoundedCornerShape(16.dp)
                    Column(
                        Modifier.fillMaxWidth().clip(summaryShape).background(color.copy(alpha = 0.06f))
                            .border(1.dp, color.copy(alpha = 0.25f), summaryShape).padding(14.dp)
                    ) {
                        Row {
                            Column(Modifier.weight(1f)) {
                                CapsLabel(stringResource(R.string.status_label))
                                Text(statusName(p.effectiveStatus), style = MaterialTheme.typography.titleSmall, color = Agora.colors.heading,
                                    modifier = Modifier.padding(top = 6.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surface)
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 4.dp))
                            }
                            Column(Modifier.weight(1f)) {
                                CapsLabel(stringResource(R.string.paid_until_label))
                                if (standingOrderCovers) Pill(stringResource(R.string.status_standing_order_active), MaterialTheme.colorScheme.primary,
                                    Modifier.padding(top = 6.dp), icon = Icons.Outlined.Autorenew)
                                else Text(paidUntilText(p), color = Color.White, style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.padding(top = 6.dp).clip(RoundedCornerShape(8.dp)).background(color).padding(horizontal = 10.dp, vertical = 4.dp))
                            }
                        }
                        if (meta.isOverdue) Row(
                            Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Agora.colors.danger.copy(alpha = 0.08f))
                                .border(1.dp, Agora.colors.danger.copy(alpha = 0.3f), RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.ErrorOutline, null, Modifier.size(16.dp), tint = Agora.colors.danger)
                            Text(stringResource(R.string.overdue_amount_label), style = MaterialTheme.typography.labelLarge, color = Agora.colors.danger,
                                modifier = Modifier.weight(1f).padding(start = 8.dp))
                            Text(Money.format(p.overdueAmount), style = MaterialTheme.typography.titleMedium, color = Agora.colors.danger)
                        }
                    }
                    // Standing orders
                    p.standingOrders.forEach { so ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Agora.colors.surfaceAlt)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp)).padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.Autorenew, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                Text("${Money.format(so.amount)} ${stringResource(R.string.per_month)}", style = MaterialTheme.typography.titleSmall, color = Agora.colors.heading)
                                Text(
                                    stringResource(R.string.since_date, Dates.short(so.startDate, locale)) + (so.endDate?.let { " – " + Dates.short(it, locale) } ?: "") +
                                        (so.note.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            // End (also retroactively) or remove the order, like the PWA's "Verwalten"
                            if (canManage) SecondaryButton(onClick = { managingOrder = so }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                                ButtonLabel(stringResource(R.string.so_manage), Icons.Outlined.Edit)
                            }
                        }
                    }
                    if (canManage) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PrimaryButton(onClick = { booking = true }, modifier = Modifier.fillMaxWidth()) { ButtonLabel(stringResource(R.string.record_payment_btn), Icons.Outlined.Payments) }
                        SecondaryButton(onClick = { changingStatus = true }, modifier = Modifier.fillMaxWidth()) { ButtonLabel(stringResource(R.string.status_btn), Icons.Outlined.SwapHoriz) }
                    }
                    SectionTitle(stringResource(R.string.history_label))
                    if (detail == null) LoadingBox() else FinanceTimeline(p, plainStatus = true)
                }
            }
        }
    }
    val p = detail ?: person
    if (booking) BookPaymentSheet(p, onDismiss = { booking = false }, onSaved = { reload++; onChanged() })
    if (changingStatus) ChangeStatusSheet(p, onDismiss = { changingStatus = false }, onSaved = { reload++; onChanged() })
    managingOrder?.let { order ->
        ManageStandingOrderSheet(p, order, onDismiss = { managingOrder = null }, onSaved = { reload++; onChanged() })
    }
}
