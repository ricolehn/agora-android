package org.agora.app.ui.finance

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.TextPaint
import android.text.TextUtils
import java.io.OutputStream

/*
 * The financial report as PDF, drawn like the web's A4 page (beta19): colour band, logo and app name, kind and
 * title with the period, three total cards, the bookings as a table with coloured kind pills and a balance row (or,
 * compact, one line per kind), a footer line. Measures are the web's CSS pixels of the 794px wide page; the
 * canvas is scaled to A4 points.
 */

/** One booking row of the report. */
data class ReportRow(
    val date: String,
    val kind: String,          // "pay", "don", "exp"
    val kindLabel: String,
    val title: String,
    val subtitle: String?,
    val amount: String,
    val income: Boolean,
    val note: String? = null,
    val receipt: Boolean = false
)

data class ReportStat(val label: String, val value: String, val note: String?, val color: Int, val valueColor: Int, val textValue: Boolean = false)

data class ReportKindLine(val kind: String, val label: String, val count: String, val amount: String, val income: Boolean)

/** Everything the page shows, already formatted and translated. */
data class ReportContent(
    val appName: String,
    val logo: Bitmap?,
    val createdLabel: String,
    val createdDate: String,
    val kicker: String,
    val title: String,
    val period: String,
    val stats: List<ReportStat>,
    val sectionTitle: String,
    val compact: Boolean,
    val detailed: Boolean,
    val headers: List<String>,          // date, kind, description, amount (compact: kind, count, amount)
    val rows: List<ReportRow>,
    val kinds: List<ReportKindLine>,
    val balanceLabel: String,
    val balance: String,
    val balancePositive: Boolean,
    val receiptLabel: String,
    val footerLeft: String,
    val footerRight: String
)

private const val PAGE_W = 794f
private const val PAGE_H = 1123f
private const val SIDE = 48f
private const val BOTTOM = 60f
private val PT_PER_PX = 595f / PAGE_W

private fun color(hex: Long) = hex.toInt()
private val INK = color(0xFF0F172A)
private val TEXT = color(0xFF334155)
private val MUTED = color(0xFF64748B)
private val FAINT = color(0xFF94A3B8)
private val LINE = color(0xFFE2E8F0)
private val SOFT_LINE = color(0xFFEEF2F6)
private val SURFACE = color(0xFFF8FAFC)
private val HEAD_BG = color(0xFFF1F5F9)
private val ZEBRA = color(0xFFFBFDFF)
private val CYAN = color(0xFF06B6D4)
private val EMERALD = color(0xFF10B981)

val REPORT_GREEN = color(0xFF059669)
val REPORT_RED = color(0xFFDC2626)
val REPORT_NAVY = color(0xFF0F172A)
val REPORT_STRIPE_INCOME = color(0xFF10B981)
val REPORT_STRIPE_EXPENSE = color(0xFFEF4444)
val REPORT_STRIPE_BALANCE = color(0xFF0891B2)
val REPORT_VALUE_INCOME = color(0xFF10B981)
val REPORT_VALUE_EXPENSE = color(0xFFEF4444)

/** Pill colours per kind: text, background, border (like the web). */
private fun pillColors(kind: String): Triple<Int, Int, Int> = when (kind) {
    "pay" -> Triple(color(0xFF059669), color(0xFFECFDF5), color(0xFFA7F3D0))
    "don" -> Triple(color(0xFF7C3AED), color(0xFFF5F3FF), color(0xFFDDD6FE))
    else -> Triple(color(0xFFDC2626), color(0xFFFEF2F2), color(0xFFFECACA))
}

private fun paint(size: Float, color: Int, bold: Boolean = false, semibold: Boolean = false, spacing: Float = 0f) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
    textSize = size
    this.color = color
    // Font weights exist from Android 9; older devices get plain bold
    typeface = when {
        !bold && !semibold -> Typeface.DEFAULT
        android.os.Build.VERSION.SDK_INT >= 28 -> Typeface.create(Typeface.DEFAULT, if (bold) 800 else 600, false)
        else -> Typeface.DEFAULT_BOLD
    }
    letterSpacing = spacing
}

private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }
private fun stroke(color: Int, width: Float = 1f) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.STROKE; strokeWidth = width }

private fun Canvas.text(text: String, x: Float, baseline: Float, paint: TextPaint, maxWidth: Float? = null, alignRight: Boolean = false) {
    val shown = if (maxWidth != null) TextUtils.ellipsize(text, paint, maxWidth, TextUtils.TruncateAt.END).toString() else text
    val width = paint.measureText(shown)
    drawText(shown, if (alignRight) x - width else x, baseline, paint)
}

/** Where pages go: PDF pages, or bitmaps for the preview. [start] returns a canvas in CSS pixels of the web page. */
private class PageWriter(private val start: () -> Canvas, private val end: () -> Unit) {
    private var open = false
    lateinit var canvas: Canvas
    var y = 0f

    fun newPage() {
        if (open) end()
        canvas = start()
        open = true
        y = SIDE
    }

    fun finish() { if (open) end(); open = false }
    fun fits(height: Float) = y + height <= PAGE_H - BOTTOM
}

/** Writes the report as PDF (A4) to [out]. */
fun writeReportPdf(content: ReportContent, out: OutputStream) {
    val doc = PdfDocument()
    try {
        var page: PdfDocument.Page? = null
        drawReport(content, PageWriter(
            start = {
                val next = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, doc.pages.size + 1).create())
                page = next
                next.canvas.apply { scale(PT_PER_PX, PT_PER_PX) }
            },
            end = { page?.let { doc.finishPage(it) }; page = null }
        ))
        doc.writeTo(out)
    } finally {
        doc.close()
    }
}

/** The same pages as bitmaps [width] pixels wide (preview in the app, screenshot tests). */
fun renderReportPages(content: ReportContent, width: Int = 1000): List<Bitmap> {
    val pages = mutableListOf<Bitmap>()
    val scale = width / PAGE_W
    drawReport(content, PageWriter(
        start = {
            val bitmap = Bitmap.createBitmap(width, (PAGE_H * scale).toInt(), Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.WHITE)
            pages += bitmap
            Canvas(bitmap).apply { scale(scale, scale) }
        },
        end = {}
    ))
    return pages
}

/** Preview: page 1 as bitmap, the other pages only measured (no bitmaps); returns (page 1, page count). */
fun renderReportPreview(content: ReportContent, width: Int = 1000): Pair<Bitmap, Int> {
    val scale = width / PAGE_W
    var first: Bitmap? = null
    var count = 0
    var picture: android.graphics.Picture? = null
    drawReport(content, PageWriter(
        start = {
            count++
            if (first == null) {
                val bitmap = Bitmap.createBitmap(width, (PAGE_H * scale).toInt(), Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.WHITE)
                first = bitmap
                Canvas(bitmap).apply { scale(scale, scale) }
            } else {
                android.graphics.Picture().also { picture = it }.beginRecording(PAGE_W.toInt(), PAGE_H.toInt())
            }
        },
        end = { picture?.endRecording(); picture = null }
    ))
    return first!! to count
}

private fun drawReport(content: ReportContent, w: PageWriter) {
    run {
        w.newPage()
        val c0 = w.canvas
        val right = PAGE_W - SIDE
        // Colour band across the top
        c0.drawRect(0f, 0f, PAGE_W, 8f, Paint().apply { shader = LinearGradient(0f, 0f, PAGE_W, 0f, CYAN, EMERALD, Shader.TileMode.CLAMP) })
        // Header: logo + app name, creation date on the right
        var y = 42f
        var nameX = SIDE
        content.logo?.let { logo ->
            val size = 40f
            val scale = minOf(size / logo.width, size / logo.height)
            val lw = logo.width * scale
            val lh = logo.height * scale
            c0.drawBitmap(logo, null, RectF(SIDE, y + (size - lh) / 2, SIDE + lw, y + (size - lh) / 2 + lh), Paint(Paint.FILTER_BITMAP_FLAG))
            nameX = SIDE + size + 12f
        }
        c0.text(content.appName, nameX, y + 27f, paint(20f, color(0xFF1E3A8A), bold = true), maxWidth = 420f)
        c0.text(content.createdLabel.uppercase(), right, y + 12f, paint(10f, FAINT, bold = true, spacing = 0.08f), alignRight = true)
        c0.text(content.createdDate, right, y + 30f, paint(13f, TEXT, bold = true), alignRight = true)
        y += 58f
        c0.drawLine(SIDE, y, right, y, stroke(LINE))
        y += 34f
        // Kind, title, period
        c0.text(content.kicker.uppercase(), SIDE, y, paint(11f, color(0xFF0891B2), bold = true, spacing = 0.1f))
        y += 38f
        c0.text(content.title, SIDE, y, paint(30f, INK, bold = true, spacing = -0.02f))
        y += 24f
        c0.text(content.period, SIDE, y, paint(14f, MUTED, semibold = true), maxWidth = right - SIDE)
        y += 30f
        // Three total cards
        val gap = 14f
        val cardW = (right - SIDE - gap * 2) / 3
        val cardH = 104f
        content.stats.take(3).forEachIndexed { i, stat ->
            val x = SIDE + i * (cardW + gap)
            val rect = RectF(x, y, x + cardW, y + cardH)
            c0.drawRoundRect(rect, 14f, 14f, fill(SURFACE))
            // Coloured stripe on the left edge, cut to the card's rounded corners
            c0.save(); c0.clipRect(x, y, x + 4f, y + cardH); c0.drawRoundRect(rect, 14f, 14f, fill(stat.color)); c0.restore()
            c0.drawRoundRect(rect, 14f, 14f, stroke(LINE))
            c0.text(stat.label.uppercase(), x + 18f, y + 30f, paint(10f, MUTED, bold = true, spacing = 0.08f), maxWidth = cardW - 30f)
            c0.text(stat.value, x + 18f, y + 62f, paint(if (stat.textValue) 17f else 22f, stat.valueColor, bold = true), maxWidth = cardW - 30f)
            stat.note?.let { c0.text(it, x + 18f, y + 84f, paint(11f, FAINT, semibold = true), maxWidth = cardW - 30f) }
        }
        y += cardH + 30f
        c0.text(content.sectionTitle.uppercase(), SIDE, y, paint(11f, MUTED, bold = true, spacing = 0.1f))
        y += 12f
        w.y = y

        // Table: columns like the web (date, kind, description, amount)
        val tableW = right - SIDE
        val cols = if (content.compact) floatArrayOf(SIDE + 14f, SIDE + tableW * 0.34f) else floatArrayOf(SIDE + 14f, SIDE + 126f, SIDE + 242f)
        val headerH = 36f
        var tableTop = w.y
        fun drawHeader() {
            val c = w.canvas
            tableTop = w.y
            c.drawRect(SIDE, w.y, right, w.y + headerH, fill(HEAD_BG))
            c.drawLine(SIDE, w.y + headerH, right, w.y + headerH, stroke(LINE))
            val hp = paint(10f, MUTED, bold = true, spacing = 0.08f)
            content.headers.dropLast(1).forEachIndexed { i, h -> c.text(h.uppercase(), cols[i], w.y + 23f, hp) }
            c.text(content.headers.last().uppercase(), right - 14f, w.y + 23f, hp, alignRight = true)
            w.y += headerH
        }
        fun closeTable() {
            // Frame around the part of the table on this page
            w.canvas.drawRoundRect(RectF(SIDE, tableTop, right, w.y), 12f, 12f, stroke(LINE))
        }
        fun pill(c: Canvas, kind: String, label: String, x: Float, top: Float) {
            val (fg, bg, border) = pillColors(kind)
            val p = paint(11f, fg, bold = true)
            val width = p.measureText(label) + 18f
            val rect = RectF(x, top, x + width, top + 20f)
            c.drawRoundRect(rect, 10f, 10f, fill(bg))
            c.drawRoundRect(rect, 10f, 10f, stroke(border))
            c.drawText(label, x + 9f, top + 14f, p)
        }
        drawHeader()
        if (content.compact) {
            content.kinds.forEachIndexed { i, line ->
                val h = 44f
                if (!w.fits(h)) { closeTable(); w.newPage(); drawHeader() }
                val c = w.canvas
                if (i % 2 == 1) c.drawRect(SIDE, w.y, right, w.y + h, fill(ZEBRA))
                pill(c, line.kind, line.label, cols[0], w.y + 12f)
                c.text(line.count, cols[1], w.y + 27f, paint(12.5f, MUTED))
                c.text(line.amount, right - 14f, w.y + 27f, paint(12.5f, if (line.income) REPORT_GREEN else REPORT_RED, bold = true), alignRight = true)
                if (i < content.kinds.lastIndex) c.drawLine(SIDE, w.y + h, right, w.y + h, stroke(SOFT_LINE))
                w.y += h
            }
            closeTable()
        } else {
            val descW = right - 14f - cols[2] - 110f
            content.rows.forEachIndexed { i, row ->
                val details = content.detailed && (row.note != null || row.receipt)
                val h = (if (row.subtitle != null) 56f else 44f) + (if (details) (if (row.note != null) 18f else 0f) + (if (row.receipt) 18f else 0f) + 6f else 0f)
                if (!w.fits(h + 44f)) { closeTable(); w.newPage(); drawHeader() }
                val c = w.canvas
                if (i % 2 == 1) c.drawRect(SIDE, w.y, right, w.y + h, fill(ZEBRA))
                c.text(row.date, cols[0], w.y + 25f, paint(12.5f, MUTED))
                pill(c, row.kind, row.kindLabel, cols[1], w.y + 10f)
                c.text(row.title, cols[2], w.y + 25f, paint(12.5f, INK, semibold = true), maxWidth = descW)
                var lineY = w.y + 25f
                row.subtitle?.let { lineY += 16f; c.text(it, cols[2], lineY, paint(11f, FAINT), maxWidth = descW) }
                c.text(row.amount, right - 14f, w.y + 25f, paint(12.5f, if (row.income) REPORT_GREEN else REPORT_RED, bold = true), alignRight = true)
                if (details) {
                    row.note?.let { lineY += 18f; c.text("„$it“", cols[1], lineY, paint(11.5f, MUTED), maxWidth = right - 14f - cols[1]) }
                    if (row.receipt) { lineY += 18f; c.text("📎 ${content.receiptLabel}", cols[1], lineY, paint(11f, REPORT_STRIPE_BALANCE, semibold = true)) }
                }
                c.drawLine(SIDE, w.y + h, right, w.y + h, stroke(SOFT_LINE))
                w.y += h
            }
            // Balance row
            val c = w.canvas
            c.drawRect(SIDE, w.y, right, w.y + 44f, fill(SURFACE))
            c.drawLine(SIDE, w.y, right, w.y, stroke(LINE, 1.5f))
            c.text(content.balanceLabel, cols[0], w.y + 27f, paint(13f, INK, bold = true))
            c.text(content.balance, right - 14f, w.y + 27f, paint(13f, if (content.balancePositive) REPORT_GREEN else REPORT_RED, bold = true), alignRight = true)
            w.y += 44f
            closeTable()
        }
        // Footer line
        if (!w.fits(52f)) w.newPage()
        w.y += 28f
        w.canvas.drawLine(SIDE, w.y, right, w.y, stroke(LINE))
        w.canvas.text(content.footerLeft, SIDE, w.y + 22f, paint(10.5f, FAINT), maxWidth = tableW / 2)
        w.canvas.text(content.footerRight, right, w.y + 22f, paint(10.5f, FAINT), alignRight = true, maxWidth = tableW / 2)
        w.finish()
    }
}
