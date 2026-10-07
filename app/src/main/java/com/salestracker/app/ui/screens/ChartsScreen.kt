package com.salestracker.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import com.salestracker.app.ui.AppViewModel
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.salestracker.app.R
import com.salestracker.app.data.AppData
import com.salestracker.app.data.ChartBucket
import com.salestracker.app.data.ChartData
import com.salestracker.app.data.ChartSpan
import com.salestracker.app.data.GoalPeriod
import com.salestracker.app.data.SalesStats
import com.salestracker.app.data.appLocale
import com.salestracker.app.data.formatDuration
import com.salestracker.app.data.formatMoney
import com.salestracker.app.data.formatPercent
import com.salestracker.app.data.localizedFormatter
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle

/** Time range for the "Per sale" averages. */
private enum class AvgRange(val period: GoalPeriod?) { WEEK(GoalPeriod.WEEK), MONTH(GoalPeriod.MONTH), QUARTER(GoalPeriod.QUARTER), YEAR(GoalPeriod.YEAR), ALL(null) }

/**
 * Charts: commission for each period, per-sale averages, and 12-week / 12-month trends.
 * Everything is drawn on the phone from the user's own data; nothing leaves the device.
 */
@Composable
fun ChartsScreen(vm: AppViewModel, data: AppData) {
    Box(Modifier.fillMaxSize()) {
        ChartsContent(data)
        QuickAdd(vm, data)
    }
}

@Composable
private fun ChartsContent(data: AppData) {
    val pct = data.defaultCommissionPercent
    val sales = data.sales
    val today = LocalDate.now()

    if (sales.isEmpty()) {
        Text(
            stringResource(R.string.charts_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(32.dp),
        )
        return
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ---- Commission earned this week / month / quarter / year ----
        SectionTitle(stringResource(R.string.commission_earned))
        val periods = listOf(GoalPeriod.WEEK, GoalPeriod.MONTH, GoalPeriod.QUARTER, GoalPeriod.YEAR)
        val byPeriod = periods.associateWith { ChartData.forPeriod(sales, pct, it, today) }
        periods.chunked(2).forEach { (p1, p2) ->
            @Composable
            fun periodTile(p: GoalPeriod, m: Modifier) {
                val s = byPeriod.getValue(p)
                Tile(
                    label = stringResource(p.label),
                    value = formatMoney(s.commission),
                    detail = pluralStringResource(R.plurals.charts_closed_sales, s.closed, s.closed),
                    modifier = m,
                )
            }
            PairRow({ periodTile(p1, it) }, { periodTile(p2, it) })
        }
        ChartData.monthPace(sales, pct, today)?.let {
            Text(stringResource(R.string.charts_pace, formatMoney(it)), style = MaterialTheme.typography.bodyMedium)
        }
        ChartData.bestMonth(sales, pct)?.let { (month, amount) ->
            Text(
                stringResource(R.string.charts_best_month, month.atDay(1).format(localizedFormatter("yMMMM")), formatMoney(amount)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // ---- Per-sale averages for a chosen range ----
        Spacer(Modifier.height(4.dp))
        SectionTitle(stringResource(R.string.charts_per_sale))
        var range by rememberSaveable { mutableStateOf(AvgRange.MONTH) }
        DropdownPicker(
            options = AvgRange.entries,
            selected = range,
            label = { stringResource(it.period?.label ?: R.string.period_all) },
            onSelect = { range = it },
        )
        val st = range.period?.let { byPeriod.getValue(it) } ?: SalesStats.of(sales, pct)
        val inRange = range.period?.let { p -> p.range(today).let { (a, b) -> ChartData.between(sales, a, b) } } ?: sales
        val biggest = inRange.filter { it.closed }.maxOfOrNull { it.revenue }
        val dash = "—"
        PairRow(
            { Tile(stringResource(R.string.charts_avg_time), if (st.opportunities == 0) dash else formatDuration(st.averageSeconds), modifier = it) },
            { Tile(stringResource(R.string.charts_avg_sale), if (st.closed == 0) dash else formatMoney(st.averageSale), modifier = it) },
        )
        PairRow(
            { Tile(stringResource(R.string.charts_avg_commission), if (st.closed == 0) dash else formatMoney(st.commission / st.closed), modifier = it) },
            { Tile(stringResource(R.string.close_rate), if (st.opportunities == 0) dash else formatPercent(st.closeRate), modifier = it) },
        )
        PairRow(
            { Tile(stringResource(R.string.charts_biggest), biggest?.let(::formatMoney) ?: dash, modifier = it) },
            { Tile(stringResource(R.string.revenue), formatMoney(st.revenue), modifier = it) },
        )

        // ---- Trends ----
        Spacer(Modifier.height(4.dp))
        SectionTitle(stringResource(R.string.charts_trends))
        var span by rememberSaveable { mutableStateOf(ChartSpan.WEEKS) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = span == ChartSpan.WEEKS, onClick = { span = ChartSpan.WEEKS }, label = { Text(stringResource(R.string.charts_weekly)) })
            FilterChip(selected = span == ChartSpan.MONTHS, onClick = { span = ChartSpan.MONTHS }, label = { Text(stringResource(R.string.charts_monthly)) })
        }
        val buckets = remember(sales, pct, span, today) { ChartData.buckets(sales, pct, span, today) }
        val shortFmt = localizedFormatter(if (span == ChartSpan.WEEKS) "MMMd" else "MMM")
        val longFmt = localizedFormatter(if (span == ChartSpan.WEEKS) "yMMMd" else "yMMMM")
        val weekOf = stringResource(R.string.charts_week_of, "%s")
        fun longLabel(b: ChartBucket) = b.start.format(longFmt).let { if (span == ChartSpan.WEEKS) weekOf.replace("%s", it) else it }
        val axis = buckets.map { it.start.format(shortFmt) }

        TrendCard(
            title = stringResource(R.string.metric_commission_short),
            values = buckets.map { it.stats.commission },
            axisLabels = axis, longLabels = buckets.map(::longLabel),
            format = ::formatMoney, line = false,
        )
        TrendCard(
            title = stringResource(R.string.revenue),
            values = buckets.map { it.stats.revenue },
            axisLabels = axis, longLabels = buckets.map(::longLabel),
            format = ::formatMoney, line = false,
        )
        // Rates and averages only mean something where there were sales: those points are left out.
        TrendCard(
            title = stringResource(R.string.close_rate),
            values = buckets.map { if (it.stats.opportunities == 0) Double.NaN else it.stats.closeRate },
            axisLabels = axis, longLabels = buckets.map(::longLabel),
            format = ::formatPercent, line = true, maxValue = 1.0,
        )
        TrendCard(
            title = stringResource(R.string.charts_avg_time),
            values = buckets.map { if (it.stats.opportunities == 0) Double.NaN else it.stats.averageSeconds.toDouble() },
            axisLabels = axis, longLabels = buckets.map(::longLabel),
            format = { formatDuration(it.toLong()) }, line = true,
        )

        // ---- Best days ----
        val counts = remember(sales) { ChartData.closedByWeekday(sales) }
        val locale = appLocale()
        val days = DayOfWeek.entries.map { it.getDisplayName(TextStyle.SHORT, locale) }
        val daysNarrow = DayOfWeek.entries.map { it.getDisplayName(TextStyle.NARROW, locale) }
        val daysLong = DayOfWeek.entries.map { it.getDisplayName(TextStyle.FULL, locale) }
        TrendCard(
            title = stringResource(R.string.charts_best_days),
            values = counts.map { it.toDouble() },
            axisLabels = days, longLabels = daysLong,
            format = { it.toInt().toString() }, line = false, labelEvery = 1, narrowLabels = daysNarrow,
            initialSelection = counts.indices.maxByOrNull { counts[it] } ?: 0,
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
}

@Composable
private fun Tile(label: String, value: String, modifier: Modifier = Modifier, detail: String? = null) {
    Card(modifier.semantics(mergeDescendants = true) {}) {
        Column(Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FitText(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * One chart: a title, a readout of the selected point ("Week of Sep 28 · $1,240"), and bars or a line.
 * Tap anywhere over a point to select it. A screen reader hears every value in order instead.
 * NaN values are gaps (no sales that week), drawn as nothing.
 */
@Composable
private fun TrendCard(
    title: String,
    values: List<Double>,
    axisLabels: List<String>,
    longLabels: List<String>,
    format: (Double) -> String,
    line: Boolean,
    maxValue: Double? = null,
    labelEvery: Int = 0,
    narrowLabels: List<String>? = null,
    initialSelection: Int = -1,
) {
    val n = values.size
    var selected by remember(n, initialSelection) {
        mutableIntStateOf(if (initialSelection >= 0) initialSelection else values.indexOfLast { !it.isNaN() }.takeIf { it >= 0 } ?: (n - 1))
    }
    val primary = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
    val grid = MaterialTheme.colorScheme.outlineVariant
    val surface = MaterialTheme.colorScheme.surfaceVariant
    val density = LocalDensity.current
    val top = values.filter { !it.isNaN() }.maxOrNull() ?: 0.0
    val scaleMax = (maxValue ?: top).takeIf { it > 0.0 } ?: 1.0
    val spoken = values.indices.joinToString("; ") { i ->
        "${longLabels[i]}: ${if (values[i].isNaN()) "—" else format(values[i])}"
    }
    // Show about 4 axis labels so they never collide; every label when asked (days of the week).
    val every = if (labelEvery > 0) labelEvery else maxOf(1, (n + 3) / 4)

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            val v = values.getOrNull(selected)
            Text(
                "${longLabels.getOrNull(selected) ?: ""} · ${if (v == null || v.isNaN()) "—" else format(v)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .semantics { contentDescription = "$title. $spoken" }
                    .pointerInput(n) {
                        detectTapGestures { pos ->
                            selected = (pos.x / (size.width.toFloat() / n)).toInt().coerceIn(0, n - 1)
                        }
                    },
            ) {
                val w = size.width
                val h = size.height
                val slot = w / n
                val stroke = with(density) { 1.dp.toPx() }
                // Recessive baseline and a faint mid line.
                drawLine(grid, Offset(0f, h), Offset(w, h), strokeWidth = stroke)
                drawLine(grid.copy(alpha = 0.5f), Offset(0f, h / 2), Offset(w, h / 2), strokeWidth = stroke)
                fun y(value: Double) = (h - (value / scaleMax * h * 0.92)).toFloat()
                if (!line) {
                    val barW = slot * 0.6f
                    val radius = with(density) { 4.dp.toPx() }
                    values.forEachIndexed { i, value ->
                        if (value.isNaN() || value <= 0.0) return@forEachIndexed
                        val x = i * slot + (slot - barW) / 2
                        val yTop = y(value)
                        val color = if (i == selected) primary else muted
                        // One shape, rounded only at the top, sitting on the baseline.
                        val r = CornerRadius(minOf(radius, (h - yTop) / 2, barW / 2))
                        drawPath(
                            Path().apply {
                                addRoundRect(RoundRect(Rect(x, yTop, x + barW, h), topLeft = r, topRight = r, bottomRight = CornerRadius.Zero, bottomLeft = CornerRadius.Zero))
                            },
                            color,
                        )
                    }
                } else {
                    val lineW = with(density) { 2.dp.toPx() }
                    val dot = with(density) { 4.dp.toPx() }
                    // Connect neighbouring points that both have data.
                    var path: Path? = null
                    values.forEachIndexed { i, value ->
                        val cx = i * slot + slot / 2
                        if (value.isNaN()) {
                            path?.let { drawPath(it, primary, style = Stroke(lineW, cap = StrokeCap.Round)) }
                            path = null
                        } else {
                            val p = path ?: Path().also { it.moveTo(cx, y(value)); path = it }
                            p.lineTo(cx, y(value))
                        }
                    }
                    path?.let { drawPath(it, primary, style = Stroke(lineW, cap = StrokeCap.Round)) }
                    values.forEachIndexed { i, value ->
                        if (value.isNaN()) return@forEachIndexed
                        val c = Offset(i * slot + slot / 2, y(value))
                        if (i == selected) {
                            drawCircle(surface, dot + lineW, c)
                            drawCircle(primary, dot, c)
                        } else {
                            drawCircle(primary, dot * 0.6f, c)
                        }
                    }
                }
                // Selected slot marker under the baseline.
                drawLine(primary, Offset(selected * slot + slot * 0.2f, h + stroke * 2), Offset(selected * slot + slot * 0.8f, h + stroke * 2), strokeWidth = stroke * 2)
            }
            Spacer(Modifier.height(6.dp))
            // Axis labels centred under their points, nudged inward at the edges so none is cut off.
            // As many as fit without touching: every label when there's room, otherwise every 2nd, 3rd…
            // (days of the week switch to one letter instead of skipping).
            val labelStyle = MaterialTheme.typography.labelSmall
            val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
            val measurer = rememberTextMeasurer()
            val pageWidthPx = with(density) { (LocalContentWidth.current - 64.dp).toPx() }
            fun widest(ls: List<String>) = ls.maxOfOrNull { measurer.measure(it, labelStyle, maxLines = 1).size.width } ?: 0
            fun stepFor(ls: List<String>) = maxOf(1, kotlin.math.ceil(widest(ls) * 1.25 / (pageWidthPx / n)).toInt())
            val fullStep = stepFor(axisLabels)
            val useNarrow = narrowLabels != null && fullStep > 1
            val labelsToShow = if (useNarrow) narrowLabels!! else axisLabels
            val step = if (useNarrow) 1 else if (labelEvery > 0) maxOf(labelEvery, fullStep) else maxOf(every, fullStep)
            val shown = labelsToShow.indices.filter { it % step == (n - 1) % step }
            Layout(
                content = { shown.forEach { Text(labelsToShow[it], style = labelStyle, color = labelColor, maxLines = 1, softWrap = false) } },
                modifier = Modifier.fillMaxWidth().clearAndSetSemantics {},
            ) { measurables, constraints ->
                val width = constraints.maxWidth
                val placeables = measurables.map { it.measure(Constraints()) }
                val height = placeables.maxOfOrNull { it.height } ?: 0
                layout(width, height) {
                    val slot = width.toFloat() / n
                    placeables.forEachIndexed { k, pl ->
                        val center = shown[k] * slot + slot / 2
                        val x = (center - pl.width / 2f).toInt().coerceIn(0, maxOf(0, width - pl.width))
                        pl.place(x, 0)
                    }
                }
            }
        }
    }
}
