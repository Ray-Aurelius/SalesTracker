package com.salestracker.app.data

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.annotation.StringRes
import com.salestracker.app.R
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.ZoneId

/** Date ranges for the expense report, including the ones tax time needs (last month, last year). */
enum class ReportRange(@StringRes val label: Int) {
    THIS_MONTH(R.string.period_month),
    LAST_MONTH(R.string.range_last_month),
    THIS_QUARTER(R.string.period_quarter),
    Q1(R.string.period_q1),
    Q2(R.string.period_q2),
    Q3(R.string.period_q3),
    Q4(R.string.period_q4),
    THIS_YEAR(R.string.period_year),
    LAST_YEAR(R.string.range_last_year),
    ALL(R.string.period_all);

    /** First day and the day after the last; null for all time. */
    fun range(today: LocalDate = LocalDate.now()): Pair<LocalDate, LocalDate>? = when (this) {
        THIS_MONTH -> today.withDayOfMonth(1).let { it to it.plusMonths(1) }
        LAST_MONTH -> today.withDayOfMonth(1).minusMonths(1).let { it to it.plusMonths(1) }
        THIS_QUARTER -> LocalDate.of(today.year, (today.monthValue - 1) / 3 * 3 + 1, 1).let { it to it.plusMonths(3) }
        Q1, Q2, Q3, Q4 -> LocalDate.of(today.year, (ordinal - Q1.ordinal) * 3 + 1, 1).let { it to it.plusMonths(3) }
        THIS_YEAR -> today.withDayOfYear(1).let { it to it.plusYears(1) }
        LAST_YEAR -> today.withDayOfYear(1).minusYears(1).let { it to it.plusYears(1) }
        ALL -> null
    }

    fun <T> filter(list: List<T>, day: (T) -> LocalDate, today: LocalDate = LocalDate.now()): List<T> {
        val (from, to) = range(today) ?: return list
        return list.filter { val d = day(it); !d.isBefore(from) && d.isBefore(to) }
    }
}

/** Simple A4 page writer shared by the PDF reports: headings, wrapped text, table rows, rules. */
internal class PdfPages {
    private val doc = PdfDocument()
    private var pageNo = 0
    private var page: PdfDocument.Page? = null
    var y = 0f

    val title = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 22f; typeface = Typeface.DEFAULT_BOLD; color = Color.rgb(58, 63, 69) }
    val h2 = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 14f; typeface = Typeface.DEFAULT_BOLD; color = Color.rgb(188, 75, 10) }
    val body = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10.5f; color = Color.rgb(30, 33, 38) }
    val bold = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10.5f; typeface = Typeface.DEFAULT_BOLD; color = Color.rgb(30, 33, 38) }
    val muted = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9f; color = Color.rgb(90, 96, 104) }
    private val rule = Paint().apply { color = Color.rgb(220, 222, 226); strokeWidth = 0.8f }

    val contentWidth: Float get() = W - 2 * M

    private fun newPage() {
        page?.let { doc.finishPage(it) }
        pageNo++
        page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
        y = M
    }

    private fun ensure(space: Float) { if (page == null || y + space > H - M) newPage() }

    fun text(t: String, paint: TextPaint, gapAfter: Float = 4f) {
        val layout = StaticLayout.Builder.obtain(t, 0, t.length, paint, contentWidth.toInt()).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
        ensure(layout.height.toFloat())
        val c = page!!.canvas
        c.save(); c.translate(M, y); layout.draw(c); c.restore()
        y += layout.height + gapAfter
    }

    /** One table row; [rightAlign] columns are right-aligned within their width (for amounts). */
    fun row(cols: List<String>, widths: List<Float>, paint: TextPaint, rightAlign: Set<Int> = emptySet()) {
        ensure(16f)
        var x = M
        cols.forEachIndexed { i, t ->
            val w = widths[i]
            val clipped = android.text.TextUtils.ellipsize(t, paint, w - 6f, android.text.TextUtils.TruncateAt.END).toString()
            val dx = if (i in rightAlign) w - 6f - paint.measureText(clipped) else 0f
            page!!.canvas.drawText(clipped, x + dx, y + 11f, paint)
            x += w
        }
        y += 16f
    }

    fun divider() { ensure(8f); page!!.canvas.drawLine(M, y + 3f, W - M, y + 3f, rule); y += 8f }

    fun gap(space: Float) { y += space }

    fun start() = newPage()

    fun bytes(): ByteArray {
        page?.let { doc.finishPage(it) }
        val out = ByteArrayOutputStream()
        doc.writeTo(out)
        doc.close()
        return out.toByteArray()
    }

    companion object {
        const val W = 595 // A4 in points
        const val H = 842
        const val M = 48f
    }
}

/**
 * The expense and mileage report as a PDF, built entirely on the phone. Client names are never included;
 * job / work order numbers, notes and income are left out unless the user turns them on; the file can be locked with an AES-256 password.
 */
object ExpenseReportPdf {
    data class Options(
        val range: ReportRange,
        /** Show each expense's job / work order #. Client names are never put in this report. */
        val includeJobRefs: Boolean,
        val includeNotes: Boolean,
        val includeIncome: Boolean,
        val password: String?,
    )

    fun create(context: Context, data: AppData, opt: Options, today: LocalDate = LocalDate.now()): ByteArray {
        val zone = ZoneId.systemDefault()
        val list = opt.range.filter(data.expenses, { it.day(zone) }, today).sortedBy { it.timestamp }
        val stats = ExpenseStats.of(list)
        val clients = data.clients.associateBy { it.id }
        val dateFmt = localizedFormatter("yMMMd")
        fun str(id: Int, vararg args: Any) = context.getString(id, *args)
        fun unitShort(u: DistanceUnit) = context.getString(u.short)
        fun distance(v: Double, u: DistanceUnit) = "${formatAmountInput(v)} ${unitShort(u)}"
        fun clientOf(e: Expense) = if (opt.includeJobRefs) e.exportJobRef(clients)?.let { str(R.string.client_ref_display, it) }.orEmpty() else ""

        val p = PdfPages()
        p.start()
        p.text(str(R.string.exp_report_title), p.title, gapAfter = 2f)
        val range = opt.range.range(today)
        p.text(
            if (range == null) context.getString(opt.range.label)
            else str(R.string.report_period, context.getString(opt.range.label), range.first.format(dateFmt), range.second.minusDays(1).format(dateFmt)),
            p.body,
        )
        p.text(str(R.string.report_generated, today.format(dateFmt)), p.muted, gapAfter = 12f)

        // Summary
        p.text(str(R.string.report_summary), p.h2, gapAfter = 6f)
        val two = listOf(220f, p.contentWidth - 220f)
        p.row(listOf(str(R.string.exp_total_expenses), formatMoneyForFile(stats.expenses)), two, p.body)
        val dist = stats.distance.entries.joinToString(" + ") { (u, v) -> distance(v, u) }
        p.row(listOf(str(R.string.exp_total_mileage), if (dist.isEmpty()) formatMoneyForFile(0.0) else "${formatMoneyForFile(stats.mileageValue)}  ($dist)"), two, p.body)
        p.row(listOf(str(R.string.exp_total_all), formatMoneyForFile(stats.total)), two, p.bold)
        if (opt.includeIncome) {
            val sales = opt.range.filter(data.sales, { java.time.Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }, today)
            val earned = SalesStats.of(sales, data.commissionPlan).commission
            p.row(listOf(str(R.string.commission_earned), formatMoneyForFile(earned)), two, p.body)
            p.row(listOf(str(R.string.net_earnings), formatMoneyForFile(earned - stats.total)), two, p.bold)
        }
        p.gap(10f)

        // By category
        if (stats.byCategory.isNotEmpty()) {
            p.text(str(R.string.exp_by_category), p.h2, gapAfter = 6f)
            stats.byCategory.forEach { (cat, amount) -> p.row(listOf(context.getString(cat.label), formatMoneyForFile(amount)), two, p.body) }
            p.gap(10f)
        }

        // Mileage log
        val trips = list.filter { it.kind == ExpenseKind.MILEAGE }
        p.text(str(R.string.exp_mileage_log), p.h2, gapAfter = 6f)
        if (trips.isEmpty()) {
            p.text(str(R.string.exp_none_in_range), p.body)
        } else {
            val w = listOf(78f, 70f, 62f, 70f, p.contentWidth - 280f)
            p.row(listOf(str(R.string.col_date), str(R.string.exp_col_distance), str(R.string.exp_col_rate), str(R.string.col_amount), str(R.string.exp_col_details)), w, p.muted, setOf(1, 2, 3))
            p.divider()
            trips.forEach { e ->
                val details = listOf(clientOf(e), if (opt.includeNotes) e.note else "").filter { it.isNotBlank() }.joinToString(" · ")
                p.row(
                    listOf(e.day(zone).format(dateFmt), distance(e.distance, e.unit), formatMoneyForFile(e.ratePerUnit), formatMoneyForFile(e.total), details),
                    w, p.body, setOf(1, 2, 3),
                )
            }
        }
        p.gap(10f)

        // Expense log
        val costs = list.filter { it.kind == ExpenseKind.EXPENSE }
        p.text(str(R.string.exp_expense_log), p.h2, gapAfter = 6f)
        if (costs.isEmpty()) {
            p.text(str(R.string.exp_none_in_range), p.body)
        } else {
            val w = listOf(78f, 100f, 70f, p.contentWidth - 248f)
            p.row(listOf(str(R.string.col_date), str(R.string.exp_col_category), str(R.string.col_amount), str(R.string.exp_col_details)), w, p.muted, setOf(2))
            p.divider()
            costs.forEach { e ->
                val details = listOf(clientOf(e), if (opt.includeNotes) e.note else "").filter { it.isNotBlank() }.joinToString(" · ")
                p.row(listOf(e.day(zone).format(dateFmt), context.getString(e.category.label), formatMoneyForFile(e.total), details), w, p.body, setOf(2))
            }
        }
        p.gap(14f)
        p.text(str(R.string.exp_report_tax_note), p.muted)
        p.text(str(R.string.report_footer), p.muted)

        val pdf = p.bytes()
        return if (opt.password.isNullOrEmpty()) pdf else ReportPdf.protect(context, pdf, opt.password)
    }
}
