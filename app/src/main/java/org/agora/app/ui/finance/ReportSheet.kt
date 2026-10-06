package org.agora.app.ui.finance

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.agora.app.R
import org.agora.app.data.model.Person
import org.agora.app.data.model.Transaction
import org.agora.app.ui.components.AgoraSheet
import org.agora.app.ui.components.ButtonLabel
import org.agora.app.ui.components.CapsLabel
import org.agora.app.ui.components.DateField
import org.agora.app.ui.components.DropdownField
import org.agora.app.ui.components.LocalContainer
import org.agora.app.ui.components.LocalSnackbar
import org.agora.app.ui.components.PrimaryButton
import org.agora.app.ui.components.SecondaryButton
import org.agora.app.ui.components.rememberSheetController
import org.agora.app.ui.theme.Agora
import org.agora.app.util.Dates
import org.agora.app.util.Money
import java.io.File
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class ReportType(val icon: ImageVector, val title: Int, val desc: Int) {
    Annual(Icons.Outlined.CalendarMonth, R.string.report_type_annual, R.string.report_type_annual_desc),
    Custom(Icons.Outlined.Schedule, R.string.report_type_custom, R.string.report_type_custom_desc),
    Person(Icons.Outlined.Person, R.string.report_type_person, R.string.report_type_person_desc),
    Manual(Icons.Outlined.Checklist, R.string.report_type_manual, R.string.report_type_manual_desc)
}

private enum class ReportTier(val label: Int, val hint: Int) {
    Compact(R.string.report_tier_compact, R.string.report_tier_compact_hint),
    Standard(R.string.report_tier_standard, R.string.report_tier_standard_hint),
    Detailed(R.string.report_tier_detailed, R.string.report_tier_detailed_hint)
}

private fun Transaction.key() = "$type-$id-$date"
private fun Transaction.isExpense() = type == "exp"

/** dd.MM.yyyy (German) / dd/MM/yyyy (otherwise), like the web report. */
private fun reportDate(iso: String, locale: Locale): String =
    Dates.parse(iso)?.format(DateTimeFormatter.ofPattern(if (locale.language == "de") "dd.MM.yyyy" else "dd/MM/yyyy")) ?: iso

private fun signed(amount: Double, income: Boolean) = (if (income) "+" else "-") + Money.format(kotlin.math.abs(amount))

/**
 * Financial report (beta19): kind of report as four cards, period / member / bookings, level of detail; the preview
 * is the real first page of the PDF, which can be saved or shared.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReportSheet(people: List<Person>, onDismiss: () -> Unit) {
    val container = LocalContainer.current
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val sheet = rememberSheetController(onDismiss)
    val locale = Dates.locale(context)
    val appName by container.store.appName.collectAsStateWithLifecycle()
    val today = Dates.todayIso()

    var transactions by remember { mutableStateOf<List<Transaction>?>(null) }
    var loadError by remember { mutableStateOf(false) }
    var logo by remember { mutableStateOf<Bitmap?>(null) }
    var type by rememberSaveable { mutableStateOf(ReportType.Annual) }
    var tier by rememberSaveable { mutableStateOf(ReportTier.Standard) }
    var year by rememberSaveable { mutableStateOf(today.take(4)) }
    var from by rememberSaveable { mutableStateOf("${today.take(4)}-01-01") }
    var to by rememberSaveable { mutableStateOf(today) }
    val sortedPeople = remember(people) { people.sortedBy { it.name.lowercase() } }
    var personId by rememberSaveable { mutableStateOf(sortedPeople.firstOrNull()?.id.orEmpty()) }
    // Ticked bookings survive rotation
    val selected = rememberSaveable(saver = listSaver(save = { it.toList() }, restore = { it.toMutableStateList() })) { mutableStateListOf<String>() }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var pages by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        runCatching { container.repo.allTransactions() }.onSuccess { transactions = it }.onFailure { loadError = true }
        logo = loadReportLogo(context, container.api.absolute("/assets/church-logo.svg"))
    }

    val all = transactions.orEmpty()
    val years = remember(all) { (all.mapNotNull { it.date.take(4).takeIf { y -> y.length == 4 } } + today.take(4)).distinct().sortedDescending() }
    val person = sortedPeople.firstOrNull { it.id == personId }
    val filtered = when (type) {
        ReportType.Annual -> all.filter { it.date.startsWith(year) }
        ReportType.Custom -> all.filter { (from.isBlank() || it.date.take(10) >= from) && (to.isBlank() || it.date.take(10) <= to) }
        ReportType.Manual -> all.filter { it.key() in selected }
        ReportType.Person -> all.filter { tx ->
            val byId = person != null && (tx.personId == person.id || (!tx.personUid.isNullOrBlank() && tx.personUid == person.uid))
            val byName = person != null && tx.who.trim().equals(person.name.trim(), ignoreCase = true)
            (byId || byName) && (from.isBlank() || tx.date.take(10) >= from) && (to.isBlank() || tx.date.take(10) <= to)
        }
    }.sortedBy { it.date }

    // Texts of the page (translated here, drawn by writeReportPdf)
    val period = when (type) {
        ReportType.Annual -> stringResource(R.string.report_year_label, year)
        ReportType.Manual -> stringResource(R.string.report_manual_selection)
        ReportType.Custom -> if (from.isBlank() && to.isBlank()) stringResource(R.string.report_all) else "${reportDate(from, locale)} - ${reportDate(to, locale)}"
        ReportType.Person -> "${person?.name.orEmpty()}: ${reportDate(from, locale)} - ${reportDate(to, locale)}"
    }
    val income = filtered.filterNot { it.isExpense() }
    val expenses = filtered.filter { it.isExpense() }
    val net = income.sumOf { it.amount } - expenses.sumOf { it.amount }
    val bookings = @Composable { n: Int -> pluralStringResource(R.plurals.report_bookings, n, n) }
    val kindLabels = mapOf("pay" to stringResource(R.string.report_kind_pay), "don" to stringResource(R.string.report_kind_don), "exp" to stringResource(R.string.report_kind_exp))
    val overdue = person?.overdueAmount ?: 0.0
    val stats = listOf(
        ReportStat(stringResource(R.string.report_income), "+${Money.format(income.sumOf { it.amount })}", bookings(income.size), REPORT_STRIPE_INCOME, REPORT_VALUE_INCOME),
        ReportStat(stringResource(R.string.report_expenses), "-${Money.format(expenses.sumOf { it.amount })}", bookings(expenses.size), REPORT_STRIPE_EXPENSE, REPORT_VALUE_EXPENSE),
        when {
            type != ReportType.Person -> ReportStat(stringResource(R.string.report_balance), signed(net, net >= 0), bookings(filtered.size), REPORT_STRIPE_BALANCE, REPORT_NAVY)
            overdue > 0 -> ReportStat(stringResource(R.string.report_outstanding), Money.format(overdue), null, REPORT_STRIPE_EXPENSE, REPORT_VALUE_EXPENSE)
            else -> ReportStat(stringResource(R.string.report_status), stringResource(R.string.report_status_good), null, REPORT_STRIPE_INCOME, REPORT_VALUE_INCOME, textValue = true)
        }
    )
    val kindCounts = listOf("pay", "don", "exp").mapNotNull { kind ->
        val items = filtered.filter { (if (it.isExpense()) "exp" else it.type) == kind }
        if (items.isEmpty()) null else kind to items
    }.map { (kind, items) -> ReportKindLine(kind, kindLabels.getValue(kind), bookings(items.size), signed(items.sumOf { it.amount }, kind != "exp"), kind != "exp") }
    val content = ReportContent(
        appName = appName,
        logo = logo,
        createdLabel = stringResource(R.string.report_created_on),
        createdDate = reportDate(today, locale),
        kicker = stringResource(when (type) {
            ReportType.Annual -> R.string.report_kind_annual
            ReportType.Custom -> R.string.report_kind_custom
            ReportType.Manual -> R.string.report_kind_manual
            ReportType.Person -> R.string.report_kind_person
        }),
        title = stringResource(R.string.report_financial_report),
        period = period,
        stats = stats,
        sectionTitle = stringResource(if (tier == ReportTier.Compact) R.string.report_section_by_kind else R.string.report_section_bookings),
        compact = tier == ReportTier.Compact,
        detailed = tier == ReportTier.Detailed,
        headers = if (tier == ReportTier.Compact) listOf(stringResource(R.string.report_head_kind), stringResource(R.string.report_head_count), stringResource(R.string.report_head_amount))
            else listOf(stringResource(R.string.report_head_date), stringResource(R.string.report_head_kind), stringResource(R.string.report_head_desc), stringResource(R.string.report_head_amount)),
        rows = filtered.map { tx ->
            val kind = if (tx.isExpense()) "exp" else tx.type.ifBlank { "pay" }
            val label = kindLabels[kind] ?: kindLabels.getValue("exp")
            val title = when (kind) {
                "pay" -> tx.who
                "don" -> tx.who.ifBlank { label }
                else -> tx.description.ifBlank { tx.who.ifBlank { label } }
            }
            ReportRow(
                date = reportDate(tx.date, locale), kind = kind, kindLabel = label, title = title,
                subtitle = tx.who.takeIf { kind == "exp" && tx.description.isNotBlank() && it.isNotBlank() && it != tx.description },
                amount = signed(tx.amount, !tx.isExpense()), income = !tx.isExpense(),
                note = tx.description.takeIf { kind != "exp" && it.isNotBlank() }, receipt = !tx.receipt.isNullOrBlank()
            )
        },
        kinds = kindCounts,
        balanceLabel = stringResource(R.string.report_balance),
        balance = signed(net, net >= 0),
        balancePositive = net >= 0,
        receiptLabel = stringResource(R.string.report_receipt),
        footerLeft = "$appName · ${stringResource(R.string.report_financial_report)}",
        footerRight = period
    )
    val fileName = "${appName.replace(Regex("[^A-Za-z0-9]"), "_")}_${stringResource(R.string.report_file_name)}_$today.pdf"

    // The preview is the real first page of the PDF
    LaunchedEffect(content, transactions) {
        if (transactions == null || filtered.isEmpty()) { preview = null; pages = 0; return@LaunchedEffect }
        delay(200)
        val result = withContext(Dispatchers.IO) { runCatching { renderFirstPage(content) }.getOrNull() }
        preview = result?.first
        pages = result?.second ?: 0
    }

    val savedMsg = stringResource(R.string.report_saved)
    val errorMsg = stringResource(R.string.error_generic)
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openOutputStream(uri)?.use { writeReportPdf(content, it) } != null }.getOrDefault(false)
            }
            snackbar.showSnackbar(if (ok) savedMsg else errorMsg)
        }
    }
    val shareTitle = stringResource(R.string.report_share)

    AgoraSheet(
        title = stringResource(R.string.report_sheet_title),
        subtitle = if (filtered.isNotEmpty()) "${bookings(filtered.size)} · ${stringResource(R.string.report_balance)} ${signed(net, net >= 0)}" else null,
        icon = Icons.Outlined.Description,
        onDismiss = onDismiss,
        controller = sheet,
        actions = {
            SecondaryButton(onClick = {
                scope.launch {
                    val file = withContext(Dispatchers.IO) {
                        runCatching {
                            File(context.cacheDir, "reports").apply { mkdirs(); listFiles()?.forEach { old -> old.delete() } }.resolve(fileName).also { f -> f.outputStream().use { writeReportPdf(content, it) } }
                        }.getOrNull()
                    }
                    if (file == null) snackbar.showSnackbar(errorMsg)
                    else shareReport(context, file, shareTitle)
                }
            }, enabled = filtered.isNotEmpty(), modifier = Modifier.weight(1f).height(46.dp)) { ButtonLabel(shareTitle, Icons.Outlined.Share) }
            PrimaryButton(onClick = { save.launch(fileName) }, enabled = filtered.isNotEmpty(), modifier = Modifier.weight(1.4f).height(46.dp)) {
                ButtonLabel(stringResource(R.string.report_save), Icons.Outlined.Download)
            }
        }
    ) {
        // Kind of report
        CapsLabel(stringResource(R.string.report_type))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ReportType.entries.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { option -> TypeCard(option, option == type, Modifier.weight(1f)) { type = option } }
                }
            }
        }
        // What goes in
        when (type) {
            ReportType.Annual -> {
                CapsLabel(stringResource(R.string.report_year), Modifier.padding(top = 6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    years.forEach { y -> Chip(y, y == year) { year = y } }
                }
            }
            ReportType.Custom -> DateRange(from, to, { from = it }, { to = it })
            ReportType.Person -> {
                if (sortedPeople.isNotEmpty() && person != null) DropdownField(stringResource(R.string.report_person), sortedPeople, person, { it.name }, { personId = it.id })
                DateRange(from, to, { from = it }, { to = it })
            }
            ReportType.Manual -> {
                CapsLabel(stringResource(R.string.report_pick), Modifier.padding(top = 6.dp))
                val shape = RoundedCornerShape(14.dp)
                Column(Modifier.fillMaxWidth().clip(shape).border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)) {
                    all.sortedByDescending { it.date }.forEachIndexed { i, tx ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        val key = tx.key()
                        val checked = key in selected
                        Row(
                            Modifier.fillMaxWidth().clickable { if (checked) selected.remove(key) else selected.add(key) }.padding(start = 4.dp, end = 14.dp, top = 2.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked, { if (it) selected.add(key) else selected.remove(key) })
                            Column(Modifier.weight(1f)) {
                                Text(tx.who.ifBlank { tx.description }, style = MaterialTheme.typography.titleSmall, color = Agora.colors.heading, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(reportDate(tx.date, locale), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(signed(tx.amount, !tx.isExpense()), style = MaterialTheme.typography.titleSmall,
                                color = if (tx.isExpense()) Agora.colors.danger else Agora.colors.success)
                        }
                    }
                }
            }
        }
        // Level of detail
        CapsLabel(stringResource(R.string.report_tier), Modifier.padding(top = 6.dp))
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Agora.colors.surfaceAlt).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)).padding(3.dp)) {
            ReportTier.entries.forEach { option ->
                val active = option == tier
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(9.dp)).background(if (active) MaterialTheme.colorScheme.surface else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable { tier = option }.padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(stringResource(option.label), style = MaterialTheme.typography.labelLarge,
                        color = if (active) Agora.colors.heading else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }
        Text(stringResource(tier.hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        // Preview: first page of the PDF
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            CapsLabel(stringResource(R.string.report_preview), Modifier.weight(1f))
            if (pages > 0) Text(pluralStringResource(R.plurals.report_pages, pages, pages), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val previewShape = RoundedCornerShape(10.dp)
        Box(
            Modifier.fillMaxWidth().aspectRatio(595f / 842f).clip(previewShape).background(androidx.compose.ui.graphics.Color.White)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, previewShape),
            contentAlignment = Alignment.Center
        ) {
            val bitmap = preview
            when {
                transactions == null && !loadError -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                    Text(stringResource(R.string.report_loading), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
                }
                filtered.isEmpty() -> Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.Description, null, Modifier.size(36.dp), tint = androidx.compose.ui.graphics.Color(0xFFCBD5E1))
                    Text(
                        stringResource(if (type == ReportType.Manual) R.string.report_pick_bookings else if (loadError) R.string.error_generic else R.string.report_no_data),
                        style = MaterialTheme.typography.bodyMedium, color = androidx.compose.ui.graphics.Color(0xFF94A3B8), textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                bitmap != null -> Image(bitmap.asImageBitmap(), null, Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
                else -> CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
            }
        }
    }
}

@Composable
private fun TypeCard(option: ReportType, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (active) primary.copy(alpha = 0.07f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (active) 1.5.dp else 1.dp, if (active) primary else MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
    ) {
        Column(Modifier.padding(12.dp)) {
            Box(
                Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(if (active) primary else Agora.colors.surfaceAlt),
                contentAlignment = Alignment.Center
            ) { Icon(option.icon, null, Modifier.size(18.dp), tint = if (active) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurfaceVariant) }
            Text(stringResource(option.title), style = MaterialTheme.typography.titleSmall, color = Agora.colors.heading, modifier = Modifier.padding(top = 8.dp),
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(stringResource(option.desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
    }
}

@Composable
private fun Chip(label: String, active: Boolean, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Text(
        label,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
        color = if (active) androidx.compose.ui.graphics.Color.White else Agora.colors.heading,
        modifier = Modifier.clip(CircleShape).background(if (active) primary else Agora.colors.surfaceAlt)
            .border(1.dp, if (active) primary else MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun DateRange(from: String, to: String, onFrom: (String) -> Unit, onTo: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        DateField(stringResource(R.string.report_from), from, onFrom, Modifier.weight(1f))
        DateField(stringResource(R.string.report_to), to, onTo, Modifier.weight(1f))
    }
}

/** The church logo of the server (SVG via Coil), else the app's own logo. */
private suspend fun loadReportLogo(context: Context, url: String): Bitmap? {
    val remote = runCatching {
        val request = ImageRequest.Builder(context).data(url).size(160).allowHardware(false).build()
        SingletonImageLoader.get(context).execute(request).image?.toBitmap()
    }.getOrNull()
    return remote ?: withContext(Dispatchers.IO) { BitmapFactory.decodeResource(context.resources, R.drawable.agora_logo) }
}

/** First page of the report for the preview, and the number of pages. */
private fun renderFirstPage(content: ReportContent): Pair<Bitmap, Int> = renderReportPreview(content)

private fun shareReport(context: Context, file: File, title: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.reports", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

