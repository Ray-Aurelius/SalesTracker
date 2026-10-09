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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Builds the manager report as a PDF, entirely on the phone. Uses Android's own PDF writer (so every
 * language and script prints correctly), then optionally locks the file with an AES-256 password.
 */
object ReportPdf {
    data class Options(
        val period: GoalPeriod,
        val includeNames: Boolean,
        val includeCommission: Boolean,
        val password: String?,
    )

    private const val W = 595 // A4 in points
    private const val H = 842
    private const val M = 48f

    fun create(context: Context, data: AppData, terms: TradeTerms, opt: Options): ByteArray {
        val zone = ZoneId.systemDefault()
        val (start, end) = opt.period.range(LocalDate.now(zone))
        val from = start.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = end.atStartOfDay(zone).toInstant().toEpochMilli()
        val sales = data.sales.filter { it.timestamp in from until to }.sortedBy { it.timestamp }
        val stats = SalesStats.of(sales, data.commissionPlan)
        val clients = data.clients.associateBy { it.id }
        val s = { id: Int, args: Array<out Any> -> context.getString(id, *args) }
        fun str(id: Int, vararg args: Any) = s(id, args)
        val dateFmt = localizedFormatter("yMMMd")

        val doc = PdfDocument()
        var pageNo = 0
        var page: PdfDocument.Page? = null
        var y = 0f

        val title = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 22f; typeface = Typeface.DEFAULT_BOLD; color = Color.rgb(58, 63, 69) }
        val h2 = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 14f; typeface = Typeface.DEFAULT_BOLD; color = Color.rgb(188, 75, 10) }
        val body = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10.5f; color = Color.rgb(30, 33, 38) }
        val muted = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9f; color = Color.rgb(90, 96, 104) }
        val rule = Paint().apply { color = Color.rgb(220, 222, 226); strokeWidth = 0.8f }

        fun newPage() {
            page?.let { doc.finishPage(it) }
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
            y = M
        }
        fun ensure(space: Float) { if (page == null || y + space > H - M) newPage() }
        fun text(t: String, paint: TextPaint, x: Float = M, width: Float = W - 2 * M, gapAfter: Float = 4f) {
            val layout = StaticLayout.Builder.obtain(t, 0, t.length, paint, width.toInt()).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
            ensure(layout.height.toFloat())
            val c = page!!.canvas
            c.save(); c.translate(x, y); layout.draw(c); c.restore()
            y += layout.height + gapAfter
        }
        fun row(cols: List<String>, widths: List<Float>, paint: TextPaint) {
            ensure(16f)
            var x = M
            cols.forEachIndexed { i, t ->
                val clipped = android.text.TextUtils.ellipsize(t, paint, widths[i] - 6f, android.text.TextUtils.TruncateAt.END).toString()
                page!!.canvas.drawText(clipped, x, y + 11f, paint)
                x += widths[i]
            }
            y += 16f
        }
        fun divider() { ensure(8f); page!!.canvas.drawLine(M, y + 3f, W - M, y + 3f, rule); y += 8f }

        newPage()
        text(str(R.string.report_title), title, gapAfter = 2f)
        text(str(R.string.report_period, context.getString(opt.period.label), start.format(dateFmt), end.minusDays(1).format(dateFmt)), body)
        text(str(R.string.report_generated, LocalDate.now(zone).format(dateFmt)), muted, gapAfter = 12f)

        // Summary
        text(str(R.string.report_summary), h2, gapAfter = 6f)
        val summary = mutableListOf(
            context.getString(R.string.close_rate) to "${formatPercent(stats.closeRate)}  (${str(R.string.close_rate_detail, stats.closed, stats.opportunities)})",
            context.getString(R.string.revenue) to formatMoneyForFile(stats.revenue),
            context.getString(R.string.upsell_revenue) to formatMoneyForFile(stats.upsellRevenue),
            context.getString(R.string.upsell_rate) to formatPercent(stats.upsellRate),
            context.getString(R.string.avg_sale) to formatMoneyForFile(stats.averageSale),
            context.getString(R.string.avg_time) to formatDuration(stats.averageSeconds),
        )
        if (opt.includeCommission) {
            summary += context.getString(R.string.commission_earned) to formatMoneyForFile(stats.commission)
            summary += context.getString(R.string.commission_paid_label) to formatMoneyForFile(stats.commissionPaid)
            summary += context.getString(R.string.commission_owed_label) to formatMoneyForFile(stats.commissionOwed)
        }
        summary.forEach { (k, v) -> row(listOf(k, v), listOf(180f, W - 2 * M - 180f), body) }
        y += 10f

        // Goals
        if (data.goals.isNotEmpty()) {
            text(context.getString(R.string.tab_goals), h2, gapAfter = 6f)
            data.goals.forEach { g ->
                val p = GoalProgress.of(g, data)
                row(
                    listOf(g.name, "${(p.fraction * 100).toInt()}%", str(R.string.goal_progress_of, formatMoneyForFile(p.current), formatMoneyForFile(p.target))),
                    listOf(220f, 60f, W - 2 * M - 280f), body,
                )
            }
            y += 10f
        }

        // Pipeline
        if (data.clients.isNotEmpty()) {
            text(str(R.string.report_pipeline), h2, gapAfter = 6f)
            text(ClientStage.entries.joinToString("   ·   ") { "${context.getString(it.label)}: ${data.clients.count { c -> c.stage == it }}" }, body, gapAfter = 12f)
        }

        // Sales list
        text(str(R.string.report_sales), h2, gapAfter = 6f)
        if (sales.isEmpty()) {
            text(str(R.string.report_no_sales), body)
        } else {
            val widths = if (opt.includeNames) listOf(110f, 170f, 110f, W - 2 * M - 390f) else listOf(140f, 0f, 140f, W - 2 * M - 280f)
            fun cols(a: String, b: String, c: String, d: String) = if (opt.includeNames) listOf(a, b, c, d) else listOf(a, c, d)
            fun ws() = if (opt.includeNames) widths else widths.filter { it > 0f }
            row(cols(str(R.string.col_date), context.getString(terms.clients), str(R.string.col_amount), str(R.string.col_status)), ws(), muted)
            divider()
            sales.forEach { sale ->
                val date = Instant.ofEpochMilli(sale.timestamp).atZone(zone).toLocalDate().format(dateFmt)
                val name = sale.clientId?.let { clients[it]?.label(context) } ?: "—"
                val status = buildString {
                    append(context.getString(if (sale.closed) R.string.tag_closed else R.string.tag_not_closed))
                    if (sale.upsellAccepted) append(" · ").append(context.getString(R.string.tag_upsell))
                }
                row(cols(date, name, formatMoneyForFile(sale.amount + if (sale.upsellAccepted) sale.upsellAmount else 0.0), status), ws(), body)
            }
        }
        y += 14f
        text(str(R.string.report_footer), muted)

        page?.let { doc.finishPage(it) }
        val out = ByteArrayOutputStream()
        doc.writeTo(out)
        doc.close()
        val pdf = out.toByteArray()
        return if (opt.password.isNullOrEmpty()) pdf else protect(context, pdf, opt.password)
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
