package com.salestracker.app.data

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.salestracker.app.R
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.time.LocalDate
import java.time.ZoneId

/**
 * Builds the manager report as a PDF, entirely on the phone. Uses Android's own PDF writer (so every
 * language and script prints correctly), then optionally locks the file with an AES-256 password.
 */
object ReportPdf {
    data class Options(
        val period: GoalPeriod,
        /** 0 = the current period, 1 = the one before, and so on. */
        val back: Int = 0,
        /** Show each sale's job / work order #. Client names, phones, emails and notes are never put in a report. */
        val includeJobRefs: Boolean,
        val includeCommission: Boolean,
        val password: String?,
        /** The salesperson's own name for the top of the report; blank leaves it off. */
        val preparedBy: String = "",
    )

    private const val W = 595 // A4 in points
    private const val H = 842
    private const val M = 44f

    /** How a report period is named: "Oct 5 – 11, 2026", "October 2026", "Q4 2026", "2026". */
    fun periodName(context: Context, period: GoalPeriod, start: LocalDate, end: LocalDate): String = when (period) {
        GoalPeriod.WEEK -> "${start.format(localizedFormatter("MMMd"))} – ${end.minusDays(1).format(localizedFormatter("yMMMd"))}"
        GoalPeriod.MONTH -> start.format(localizedFormatter("yMMMM"))
        GoalPeriod.QUARTER -> context.getString(R.string.report_quarter, (start.monthValue - 1) / 3 + 1, start.year)
        GoalPeriod.YEAR -> start.year.toString()
    }

    fun create(context: Context, data: AppData, terms: TradeTerms, opt: Options): ByteArray {
        val zone = ZoneId.systemDefault()
        val r = SalesReport.build(data, opt.period, opt.back, opt.includeJobRefs, LocalDate.now(zone), zone)
        val stats = r.stats
        fun str(id: Int, vararg args: Any) = context.getString(id, *args)
        val dateFmt = localizedFormatter("yMMMd")
        val shortDate = localizedFormatter("MMMd")

        val doc = PdfDocument()
        var pageNo = 0
        var page: PdfDocument.Page? = null
        var y = 0f

        val ink = Color.rgb(30, 33, 38)
        val accent = Color.rgb(188, 75, 10)
        val title = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 22f; typeface = Typeface.DEFAULT_BOLD; color = Color.rgb(46, 49, 53) }
        val h2 = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 13f; typeface = Typeface.DEFAULT_BOLD; color = accent }
        val body = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f; color = ink }
        val bold = TextPaint(body).apply { typeface = Typeface.DEFAULT_BOLD }
        val muted = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 8.5f; color = Color.rgb(90, 96, 104) }
        val kpiValue = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 16f; typeface = Typeface.DEFAULT_BOLD; color = ink }
        val rule = Paint().apply { color = Color.rgb(220, 222, 226); strokeWidth = 0.8f }
        val tile = Paint().apply { color = Color.rgb(246, 241, 237) }
        val band = Paint().apply { color = Color.rgb(247, 248, 249) }

        fun footer() {
            val c = page!!.canvas
            c.drawText(str(R.string.report_footer), M, H - 24f, muted)
            val n = pageNo.toString()
            c.drawText(n, W - M - muted.measureText(n), H - 24f, muted)
        }
        fun newPage() {
            page?.let { footer(); doc.finishPage(it) }
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
            y = M
        }
        fun ensure(space: Float) { if (page == null || y + space > H - M - 16f) newPage() }
        fun text(t: String, paint: TextPaint, x: Float = M, width: Float = W - 2 * M, gapAfter: Float = 4f) {
            val layout = StaticLayout.Builder.obtain(t, 0, t.length, paint, width.toInt()).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
            ensure(layout.height.toFloat())
            val c = page!!.canvas
            c.save(); c.translate(x, y); layout.draw(c); c.restore()
            y += layout.height + gapAfter
        }
        /** One table row; columns listed in [right] are right-aligned (amounts). */
        fun row(cols: List<String>, widths: List<Float>, paint: TextPaint, right: Set<Int> = emptySet(), shade: Boolean = false) {
            ensure(16f)
            val c = page!!.canvas
            if (shade) c.drawRect(M, y, W - M, y + 16f, band)
            var x = M
            cols.forEachIndexed { i, t ->
                val w = widths[i]
                val clipped = android.text.TextUtils.ellipsize(t, paint, w - 8f, android.text.TextUtils.TruncateAt.END).toString()
                val tx = if (i in right) x + w - 4f - paint.measureText(clipped) else x + 2f
                c.drawText(clipped, tx, y + 11.5f, paint)
                x += w
            }
            y += 16f
        }
        fun divider() { ensure(8f); page!!.canvas.drawLine(M, y + 3f, W - M, y + 3f, rule); y += 8f }
        fun section(name: String) { y += 6f; ensure(40f); text(name, h2, gapAfter = 4f) }

        // ---- Header ----
        newPage()
        text(str(R.string.report_title), title, gapAfter = 2f)
        if (opt.preparedBy.isNotBlank()) text(str(R.string.report_prepared_for, opt.preparedBy.trim()), bold, gapAfter = 2f)
        text(
            str(R.string.report_period, context.getString(opt.period.label), periodName(context, opt.period, r.start, r.end)) +
                "  (" + r.start.format(dateFmt) + " – " + r.end.minusDays(1).format(dateFmt) + ")",
            body, gapAfter = 2f,
        )
        text(str(R.string.report_generated, LocalDate.now(zone).format(dateFmt)), muted, gapAfter = 10f)

        // ---- Key numbers, as tiles ----
        val tiles = listOf(
            str(R.string.report_total_revenue) to formatMoneyForFile(stats.revenue),
            str(R.string.report_closed_sales) to "${stats.closed}",
            str(R.string.close_rate) to formatPercent(stats.closeRate),
            str(R.string.upsell_revenue) to formatMoneyForFile(stats.upsellRevenue),
        )
        ensure(56f)
        run {
            val gap = 8f
            val tw = (W - 2 * M - gap * 3) / 4
            val c = page!!.canvas
            tiles.forEachIndexed { i, (label, value) ->
                val x = M + i * (tw + gap)
                c.drawRoundRect(x, y, x + tw, y + 50f, 6f, 6f, tile)
                val v = android.text.TextUtils.ellipsize(value, kpiValue, tw - 16f, android.text.TextUtils.TruncateAt.END).toString()
                c.drawText(v, x + 8f, y + 24f, kpiValue)
                val l = android.text.TextUtils.ellipsize(label, muted, tw - 16f, android.text.TextUtils.TruncateAt.END).toString()
                c.drawText(l, x + 8f, y + 40f, muted)
            }
            y += 58f
        }

        // ---- Summary ----
        section(str(R.string.report_summary))
        val kv = listOf(200f, W - 2 * M - 200f)
        val summary = mutableListOf(
            str(R.string.report_total_revenue) to formatMoneyForFile(stats.revenue),
            str(R.string.report_sale_revenue) to formatMoneyForFile(r.saleRevenue),
            str(R.string.upsell_revenue) to formatMoneyForFile(stats.upsellRevenue),
            str(R.string.report_closed_sales) to str(R.string.close_rate_detail, stats.closed, stats.opportunities),
            str(R.string.close_rate) to formatPercent(stats.closeRate),
            str(R.string.report_upsells) to str(R.string.report_upsells_detail, stats.upsellsAccepted, stats.upsellsOffered, formatPercent(stats.upsellAcceptance)),
            str(R.string.upsell_rate) to formatPercent(stats.upsellRate),
            str(R.string.avg_sale) to formatMoneyForFile(stats.averageSale),
            str(R.string.report_largest) to formatMoneyForFile(r.largestSale),
            str(R.string.avg_time) to formatDuration(stats.averageSeconds),
        )
        r.best?.let { b ->
            val label = when (opt.period) {
                GoalPeriod.WEEK -> str(R.string.report_best_day)
                GoalPeriod.MONTH -> str(R.string.report_best_week)
                else -> str(R.string.report_best_month)
            }
            summary += label to "${bucketName(opt.period, b, shortDate)}: ${formatMoneyForFile(b.stats.revenue)}"
        }
        summary += str(R.string.report_vs_previous) to (r.change?.let { ch ->
            val sign = if (ch >= 0) "+" else ""
            str(R.string.report_change, sign + formatPercent(ch), formatMoneyForFile(r.previous.revenue))
        } ?: str(R.string.report_no_previous))
        if (opt.includeCommission) {
            summary += str(R.string.commission_earned) to formatMoneyForFile(stats.commission)
            summary += str(R.string.commission_paid_label) to formatMoneyForFile(stats.commissionPaid)
            summary += str(R.string.commission_owed_label) to formatMoneyForFile(stats.commissionOwed)
        }
        summary.forEachIndexed { i, (k, v) -> row(listOf(k, v), kv, if (i == 0) bold else body, shade = i % 2 == 1) }

        // ---- Breakdown by day / week / month ----
        section(str(R.string.report_breakdown))
        val bw = listOf(150f, 70f, 70f, 105f, W - 2 * M - 395f)
        row(listOf(str(R.string.col_period), str(R.string.col_closed), str(R.string.close_rate), str(R.string.upsell_revenue), str(R.string.col_revenue)), bw, muted, right = setOf(1, 2, 3, 4))
        divider()
        r.buckets.forEachIndexed { i, b ->
            row(
                listOf(
                    bucketName(opt.period, b, shortDate), "${b.stats.closed}/${b.stats.opportunities}",
                    if (b.stats.opportunities == 0) "—" else formatPercent(b.stats.closeRate),
                    formatMoneyForFile(b.stats.upsellRevenue), formatMoneyForFile(b.stats.revenue),
                ),
                bw, body, right = setOf(1, 2, 3, 4), shade = i % 2 == 1,
            )
        }
        divider()
        row(listOf(str(R.string.report_totals), "${stats.closed}/${stats.opportunities}", formatPercent(stats.closeRate),
            formatMoneyForFile(stats.upsellRevenue), formatMoneyForFile(stats.revenue)), bw, bold, right = setOf(1, 2, 3, 4))

        // ---- Goals ----
        if (data.goals.isNotEmpty()) {
            section(context.getString(R.string.tab_goals))
            data.goals.forEachIndexed { i, g ->
                val p = GoalProgress.of(g, data)
                row(
                    listOf(g.name, "${(p.fraction * 100).toInt()}%", str(R.string.goal_progress_of, formatMoneyForFile(p.current), formatMoneyForFile(p.target))),
                    listOf(220f, 60f, W - 2 * M - 280f), body, shade = i % 2 == 1,
                )
            }
        }

        // ---- Pipeline (counts only) ----
        if (data.clients.isNotEmpty()) {
            section(str(R.string.report_pipeline))
            text(ClientStage.entries.joinToString("   ·   ") { "${context.getString(it.label)}: ${data.clients.count { c -> c.stage == it }}" }, body)
        }

        // ---- Every sale, by date and job / work order # ----
        section(str(R.string.report_sales))
        if (r.lines.isEmpty()) {
            text(str(R.string.report_no_sales), body)
        } else {
            val withJob = opt.includeJobRefs
            val withComm = opt.includeCommission
            val cols = mutableListOf(str(R.string.col_date))
            val widths = mutableListOf(72f)
            if (withJob) { cols += str(R.string.client_ref); widths += 92f }
            cols += listOf(str(R.string.col_status), str(R.string.col_sale), str(R.string.col_upsell), str(R.string.col_revenue))
            widths += listOf(0f, 72f, 62f, 74f)
            if (withComm) { cols += str(R.string.commission_earned); widths += 68f }
            widths[cols.indexOf(str(R.string.col_status))] = W - 2 * M - widths.sum()
            val money = (cols.size - (if (withComm) 4 else 3) until cols.size).toSet()
            row(cols, widths, muted, right = money)
            divider()
            r.lines.forEachIndexed { i, l ->
                val status = buildString {
                    append(context.getString(if (l.closed) R.string.tag_closed else R.string.tag_not_closed))
                    if (l.upsellAccepted) append(" · ").append(context.getString(R.string.tag_upsell))
                    else if (l.upsellOffered && l.closed) append(" · ").append(context.getString(R.string.report_upsell_declined))
                }
                val cells = mutableListOf(l.date.format(dateFmt))
                if (withJob) cells += l.jobRef ?: "—"
                cells += listOf(
                    status, formatMoneyForFile(l.sale),
                    if (l.upsell > 0) formatMoneyForFile(l.upsell) else "—",
                    if (l.closed) formatMoneyForFile(l.revenue) else "—",
                )
                if (withComm) cells += if (l.closed) formatMoneyForFile(l.commission) else "—"
                row(cells, widths, if (l.closed) body else muted, right = money, shade = i % 2 == 1)
            }
            divider()
            val totals = mutableListOf(str(R.string.report_totals))
            if (withJob) totals += ""
            totals += listOf(str(R.string.close_rate_detail, stats.closed, stats.opportunities),
                formatMoneyForFile(r.saleRevenue), formatMoneyForFile(stats.upsellRevenue), formatMoneyForFile(stats.revenue))
            if (withComm) totals += formatMoneyForFile(stats.commission)
            row(totals, widths, bold, right = money)
        }
        y += 10f
        text(str(if (opt.includeJobRefs) R.string.report_verification else R.string.report_verification_no_jobs), muted)

        page?.let { footer(); doc.finishPage(it) }
        val out = ByteArrayOutputStream()
        doc.writeTo(out)
        doc.close()
        val pdf = out.toByteArray()
        return if (opt.password.isNullOrEmpty()) pdf else protect(context, pdf, opt.password)
    }

    private fun bucketName(period: GoalPeriod, b: SalesReport.Bucket, shortDate: java.time.format.DateTimeFormatter): String = when (period) {
        GoalPeriod.WEEK -> b.start.format(localizedFormatter("EEEMMMd"))
        GoalPeriod.MONTH -> "${b.start.format(shortDate)} – ${b.end.minusDays(1).format(shortDate)}"
        else -> b.start.format(localizedFormatter("MMMM"))
    }

    /** Locks the PDF: AES-256, opening needs the password; the owner password is random and never shown. */
    internal fun protect(context: Context, pdf: ByteArray, password: String): ByteArray {
        PDFBoxResourceLoader.init(context.applicationContext)
        PDDocument.load(pdf).use { doc ->
            val owner = ByteArray(24).also(SecureRandom()::nextBytes).joinToString("") { "%02x".format(it) }
            val policy = StandardProtectionPolicy(owner, password, AccessPermission())
            policy.encryptionKeyLength = 256
            doc.protect(policy)
            val out = ByteArrayOutputStream()
            doc.save(out)
            return out.toByteArray()
        }
    }
}
