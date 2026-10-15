package com.salestracker.app.data

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Reads clients or sales from a CSV spreadsheet the user picked (Excel, Google Sheets, another CRM, or this
 * app's own sales report). Pure Kotlin so every rule is unit-tested; the screen only reads the one file.
 *
 * Nothing here ever writes a file or touches the network. Imports only add records: rows that match something
 * already in the app are skipped, so importing the same file twice does nothing the second time.
 */
object CsvImport {
    const val MAX_BYTES = 5 * 1024 * 1024
    const val MAX_ROWS = 20_000
    private const val MAX_CELL = 2_000

    enum class Kind { CLIENTS, SALES }

    enum class Field(val kind: Kind, vararg val names: String) {
        FULL_NAME(Kind.CLIENTS, "name", "full name", "client", "client name", "customer", "customer name", "contact", "contact name"),
        FIRST_NAME(Kind.CLIENTS, "first name", "first", "firstname", "given name", "forename"),
        LAST_NAME(Kind.CLIENTS, "last name", "last", "lastname", "surname", "family name"),
        PHONE(Kind.CLIENTS, "phone", "phone number", "mobile", "cell", "cell phone", "telephone", "tel", "mobile phone", "primary phone"),
        EMAIL(Kind.CLIENTS, "email", "e-mail", "email address", "mail"),
        REFERENCE(Kind.CLIENTS, "job #", "job", "job number", "job no", "work order", "work order #", "wo", "wo #", "order #", "order number", "account", "account #", "account number", "reference", "ref", "job / work order #"),
        OCCUPATION(Kind.CLIENTS, "occupation", "job title", "title", "profession", "industry", "company", "business"),
        NOTES(Kind.CLIENTS, "notes", "note", "comments", "comment", "description", "details"),
        STAGE(Kind.CLIENTS, "stage", "status", "pipeline", "pipeline stage", "lead status", "deal stage"),

        DATE(Kind.SALES, "date", "sale date", "sold", "sold on", "date sold", "close date", "closed date", "order date", "created", "day"),
        AMOUNT(Kind.SALES, "sale", "amount", "sale amount", "price", "total", "value", "deal value", "revenue", "order total", "sales"),
        UPSELL(Kind.SALES, "upsell", "upsell amount", "add-on", "add on", "addon", "add-ons", "extras"),
        CLOSED(Kind.SALES, "status", "closed", "won", "result", "outcome", "sold?", "closed?"),
        COMMISSION(Kind.SALES, "commission %", "commission rate", "rate", "commission percent", "comm %"),
        JOB(Kind.SALES, "job #", "job", "job number", "work order", "work order #", "wo", "wo #", "order #", "order number", "invoice", "invoice #", "reference", "ref", "job / work order #"),
        CLIENT(Kind.SALES, "client", "client name", "customer", "customer name", "name", "contact", "account name"),
        NOTES_SALE(Kind.SALES, "notes", "note", "comments", "comment", "description", "details");

        companion object {
            fun of(kind: Kind) = entries.filter { it.kind == kind }
        }
    }

    class Table(val header: List<String>, val rows: List<List<String>>) {
        fun cell(row: List<String>, column: Int?): String = column?.let { row.getOrNull(it) }.orEmpty().trim()
        /** The first value in a column, to show next to its name while matching columns. */
        fun sample(column: Int): String = rows.asSequence().map { cell(it, column) }.firstOrNull { it.isNotEmpty() }.orEmpty()
    }

    class NotACsvException : Exception()

    /** RFC 4180 CSV: quoted cells, "" inside quotes, line breaks inside quotes; comma, semicolon or tab separated. */
    fun parse(input: String): Table {
        val text = input.removePrefix("﻿")
        if (text.isBlank() || text.any { it == '\u0000' }) throw NotACsvException()
        val sep = delimiter(text)
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        fun endCell() { row += clean(cell.toString()); cell.setLength(0) }
        fun endRow() {
            endCell()
            if (row.any { it.isNotBlank() }) rows += row
            row = ArrayList()
            if (rows.size > MAX_ROWS + 1) throw TooManyRowsException()
        }
        while (i < text.length) {
            val c = text[i]
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') { cell.append('"'); i++ } else quoted = false
                } else if (cell.length < MAX_CELL) cell.append(c)
            } else when (c) {
                '"' -> if (cell.isBlank()) { cell.setLength(0); quoted = true } else cell.append(c)
                sep -> endCell()
                '\r' -> { endRow(); if (i + 1 < text.length && text[i + 1] == '\n') i++ }
                '\n' -> endRow()
                else -> if (cell.length < MAX_CELL) cell.append(c)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) endRow()
        if (rows.size < 2) throw NotACsvException()
        val header = rows.first().map { it.trim() }
        val width = rows.maxOf { it.size }
        if (width < 1) throw NotACsvException()
        return Table(header + List((width - header.size).coerceAtLeast(0)) { "" }, rows.drop(1))
    }

    class TooManyRowsException : Exception()

    private fun delimiter(text: String): Char {
        val firstLine = text.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
        val counts = listOf(',', ';', '\t').associateWith { d ->
            var inQ = false
            firstLine.count { ch -> if (ch == '"') inQ = !inQ; !inQ && ch == d }
        }
        return counts.maxByOrNull { it.value }?.takeIf { it.value > 0 }?.key ?: ','
    }

    /** Spreadsheets guard text cells against formulas with a leading apostrophe (this app does too); drop it. */
    private fun clean(s: String): String {
        val t = s.trim()
        return if (t.length > 1 && t[0] == '\'' && t[1] in "=+-@") t.substring(1) else t
    }

    private fun key(s: String) = s.lowercase(Locale.ROOT)
        .replace(Regex("\\(.*?\\)"), " ")       // "Sale (USD)" → "sale"
        .replace(Regex("[^\\p{L}\\p{N}#%?+-]+"), " ")
        .trim()

    /**
     * Best guess at which column holds each field, by header name. [extraNames] adds the app's own labels in the
     * user's language (so a report exported from this app in Spanish maps itself). Each column is used once.
     */
    fun guess(kind: Kind, header: List<String>, extraNames: Map<Field, List<String>> = emptyMap()): Map<Field, Int> {
        val keys = header.map(::key)
        val result = LinkedHashMap<Field, Int>()
        val used = HashSet<Int>()
        val fields = Field.of(kind)
        fun names(f: Field) = (extraNames[f].orEmpty() + f.names).map(::key).filter { it.isNotEmpty() }
        // Exact names first, in field order, then names a header starts with ("Phone (mobile)").
        for (pass in 0..1) for (f in fields) {
            if (f in result) continue
            val col = keys.indices.firstOrNull { c ->
                c !in used && keys[c].isNotEmpty() && names(f).any { n -> if (pass == 0) keys[c] == n else keys[c].startsWith("$n ") }
            } ?: continue
            result[f] = col
            used += col
        }
        // A single "Name" column holds the whole name; don't also treat it as first or last name.
        if (kind == Kind.CLIENTS && Field.FIRST_NAME in result && Field.LAST_NAME in result) result.remove(Field.FULL_NAME)
        return result
    }

    // ---- values -------------------------------------------------------------------------------------------

    /** "$1,234.56", "1.234,56 €", "(45.00)", "12,5" → numbers; null when it isn't one. */
    fun money(raw: String): Double? {
        var s = raw.trim()
        if (s.isEmpty()) return null
        val negative = s.startsWith("(") && s.endsWith(")") || s.startsWith("-") || s.endsWith("-")
        s = s.filter { it.isDigit() || it == ',' || it == '.' }
        if (s.isEmpty() || s.none { it.isDigit() }) return null
        val lastComma = s.lastIndexOf(',')
        val lastDot = s.lastIndexOf('.')
        s = when {
            lastComma >= 0 && lastDot >= 0 ->
                if (lastComma > lastDot) s.replace(".", "").replace(',', '.') else s.replace(",", "")
            lastComma >= 0 ->
                if (s.count { it == ',' } == 1 && s.length - lastComma - 1 != 3) s.replace(',', '.') else s.replace(",", "")
            s.count { it == '.' } > 1 -> s.replace(".", "")
            else -> s
        }
        val v = s.toDoubleOrNull() ?: return null
        if (v.isNaN() || v.isInfinite() || v > 1e12) return null
        return if (negative) -v else v
    }

    /** "7.5%", "7.5", "0.075" → 7.5. */
    fun percent(raw: String): Double? {
        val v = money(raw) ?: return null
        val p = if (!raw.contains('%') && v > 0 && v < 1) v * 100 else v
        return p.takeIf { it in 0.0..100.0 }
    }

    /** Yes / no in the words spreadsheets and CRMs use. Null when it says neither. */
    fun yesNo(raw: String): Boolean? {
        val s = raw.trim().lowercase(Locale.ROOT)
        if (s.isEmpty()) return null
        val no = listOf("not", "no", "lost", "open", "false", "pending", "declined", "cancel", "n", "0", "✗", "✘")
        val yes = listOf("yes", "y", "true", "1", "closed", "won", "sold", "done", "paid", "complete", "x", "✓", "✔", "☑")
        if (no.any { s == it || s.startsWith("$it ") || s.startsWith("$it-") }) return false
        if (yes.any { s == it || s.startsWith("$it ") || s.startsWith("$it-") || s.startsWith("$it +") }) return true
        return null
    }

    fun stage(raw: String, labels: Map<ClientStage, String> = emptyMap()): ClientStage? {
        val s = raw.trim().lowercase(Locale.ROOT)
        if (s.isEmpty()) return null
        ClientStage.entries.firstOrNull { it.name.lowercase(Locale.ROOT) == s || labels[it]?.lowercase() == s }?.let { return it }
        return when {
            s.startsWith("won") || s.contains("closed won") || s == "customer" || s == "sold" -> ClientStage.WON
            s.startsWith("lost") || s.contains("closed lost") || s.contains("dead") -> ClientStage.LOST
            s.contains("negotiat") -> ClientStage.NEGOTIATING
            s.contains("proposal") || s.contains("quote") || s.contains("quoted") -> ClientStage.PROPOSAL
            s.contains("contact") || s.contains("qualified") -> ClientStage.CONTACTED
            s.contains("lead") || s == "new" || s.contains("prospect") -> ClientStage.LEAD
            else -> null
        }
    }

    private val monthNames = DateTimeFormatter.ofPattern("[MMMM d, yyyy][MMM d, yyyy][d MMMM yyyy][d MMM yyyy][MMM d yyyy][d-MMM-yyyy][d-MMM-yy]", Locale.ENGLISH)

    /**
     * Reads a column of dates. Whether 03/04/2026 is March 4 or April 3 is decided for the whole column: any first
     * number above 12 means day-first, any second number above 12 means month-first, otherwise [monthFirst]
     * (true in the US). Also ISO dates, "Mar 4, 2026", and Excel's day numbers.
     */
    fun dates(values: List<String>, monthFirst: Boolean): List<LocalDate?> {
        val slash = Regex("""^(\d{1,2})[/.\-](\d{1,2})[/.\-](\d{2}|\d{4})\b""")
        var dayFirst = !monthFirst
        val parts = values.mapNotNull { slash.find(it.trim())?.groupValues }
        if (parts.any { it[1].toInt() > 12 }) dayFirst = true
        else if (parts.any { it[2].toInt() > 12 }) dayFirst = false
        return values.map { v -> date(v.trim(), dayFirst, slash) }
    }

    private fun date(s: String, dayFirst: Boolean, slash: Regex): LocalDate? {
        if (s.isEmpty()) return null
        Regex("""^(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})""").find(s)?.groupValues?.let { g ->
            return runCatching { LocalDate.of(g[1].toInt(), g[2].toInt(), g[3].toInt()) }.getOrNull()
        }
        slash.find(s)?.groupValues?.let { g ->
            val a = g[1].toInt(); val b = g[2].toInt()
            var y = g[3].toInt()
            if (g[3].length == 2) y += if (y < 70) 2000 else 1900
            return runCatching { if (dayFirst) LocalDate.of(y, b, a) else LocalDate.of(y, a, b) }.getOrNull()
        }
        // Excel stores dates as days since 1899-12-30; a sheet saved without formatting shows that number.
        s.toDoubleOrNull()?.let { n ->
            return if (n in 20_000.0..80_000.0) LocalDate.of(1899, 12, 30).plusDays(n.toLong()) else null
        }
        val words = s.replace(Regex("(\\d)(st|nd|rd|th)\\b"), "$1").replace(Regex("\\s+"), " ").substringBefore(" at ")
        return runCatching { LocalDate.parse(words, monthNames) }.getOrNull()
            ?: runCatching { LocalDate.parse(words.substringBeforeLast(" ").trim().trimEnd(','), monthNames) }.getOrNull()
    }

    // ---- building records ---------------------------------------------------------------------------------

    class Outcome(
        val clients: List<Client>,
        val sales: List<Sale>,
        /** Rows that matched something already in the app (or earlier in the same file). */
        val duplicates: Int,
        /** Rows missing what's needed (no name or contact for a client; no valid date or amount for a sale). */
        val unusable: Int,
    )

    private fun norm(s: String) = s.trim().lowercase().replace(Regex("\\s+"), " ")
    private fun digits(s: String) = s.filter(Char::isDigit).takeLast(10)

    private class ClientIndex(clients: List<Client>) {
        val byEmail = HashMap<String, Client>()
        val byPhone = HashMap<String, Client>()
        val byName = HashMap<String, Client>()
        val byRef = HashMap<String, Client>()
        init { clients.forEach(::add) }
        fun add(c: Client) {
            norm(c.email).takeIf { it.contains('@') }?.let { byEmail.putIfAbsent(it, c) }
            digits(c.phone).takeIf { it.length >= 7 }?.let { byPhone.putIfAbsent(it, c) }
            norm(c.fullName).takeIf { it.isNotEmpty() }?.let { byName.putIfAbsent(it, c) }
            norm(c.reference).takeIf { it.isNotEmpty() }?.let { byRef.putIfAbsent(it, c) }
        }
        /** The same person: same email, same phone, or same name with no conflicting job #. */
        fun match(c: Client): Client? {
            norm(c.email).takeIf { it.contains('@') }?.let { e -> byEmail[e]?.let { return it } }
            digits(c.phone).takeIf { it.length >= 7 }?.let { p -> byPhone[p]?.let { return it } }
            norm(c.reference).takeIf { it.isNotEmpty() }?.let { r -> byRef[r]?.let { return it } }
            val name = norm(c.fullName)
            if (name.isNotEmpty()) byName[name]?.let { other ->
                if (c.reference.isBlank() || other.reference.isBlank()) return other
            }
            return null
        }
    }

    private fun clientFrom(t: Table, row: List<String>, m: Map<Field, Int>, id: Long, stageLabels: Map<ClientStage, String>): Client {
        var first = t.cell(row, m[Field.FIRST_NAME])
        var last = t.cell(row, m[Field.LAST_NAME])
        val full = t.cell(row, m[Field.FULL_NAME])
        if (first.isEmpty() && last.isEmpty() && full.isNotEmpty()) splitName(full).let { first = it.first; last = it.second }
        return Client(
            id = id,
            firstName = first.take(100),
            lastName = last.take(100),
            phone = t.cell(row, m[Field.PHONE]).take(40),
            email = t.cell(row, m[Field.EMAIL]).take(200),
            notes = t.cell(row, m[Field.NOTES]),
            reference = t.cell(row, m[Field.REFERENCE]).take(100),
            occupation = t.cell(row, m[Field.OCCUPATION]).take(100),
            stage = stage(t.cell(row, m[Field.STAGE]), stageLabels) ?: ClientStage.LEAD,
        )
    }

    /** "Alex Morgan" → (Alex, Morgan); "Morgan, Alex" → (Alex, Morgan); "Cher" → (Cher, ""). */
    fun splitName(full: String): Pair<String, String> {
        val f = full.trim().replace(Regex("\\s+"), " ")
        return when {
            f.contains(',') -> f.substringAfter(',').trim() to f.substringBefore(',').trim()
            f.contains(' ') -> f.substringBeforeLast(' ') to f.substringAfterLast(' ')
            else -> f to ""
        }
    }

    private fun Client.isEmpty() = fullName.isBlank() && phone.isBlank() && email.isBlank() && reference.isBlank()

    fun clients(t: Table, m: Map<Field, Int>, existing: List<Client>, newId: () -> Long, stageLabels: Map<ClientStage, String> = emptyMap()): Outcome {
        val index = ClientIndex(existing)
        val added = ArrayList<Client>()
        var dupes = 0
        var bad = 0
        for (row in t.rows) {
            val c = clientFrom(t, row, m, 0, stageLabels)
            when {
                c.isEmpty() -> bad++
                index.match(c) != null -> dupes++
                else -> { val n = c.copy(id = newId()); added += n; index.add(n) }
            }
        }
        return Outcome(added, emptyList(), dupes, bad)
    }

    /**
     * Sales, linked to clients by name or job #. With [addClients], a name that isn't in the app yet becomes a new
     * client (Won when the sale closed). Sales already in the app — same day, amount and client or job # — are skipped.
     */
    fun sales(
        t: Table,
        m: Map<Field, Int>,
        data: AppData,
        newId: () -> Long,
        addClients: Boolean,
        monthFirst: Boolean,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Outcome {
        val index = ClientIndex(data.clients)
        val newClients = ArrayList<Client>()
        val sales = ArrayList<Sale>()
        val days = dates(t.rows.map { t.cell(it, m[Field.DATE]) }, monthFirst)
        fun dayOf(ms: Long) = java.time.Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
        val seen = HashSet<String>()
        data.sales.forEach { s ->
            seen += "${dayOf(s.timestamp)}|${"%.2f".format(Locale.ROOT, s.amount)}|${s.clientId ?: ""}|${norm(s.jobRef)}"
        }
        var dupes = 0
        var bad = 0
        t.rows.forEachIndexed { i, row ->
            val day = days[i]
            val amount = money(t.cell(row, m[Field.AMOUNT]))
            if (day == null || amount == null || amount < 0) { bad++; return@forEachIndexed }
            val upsell = money(t.cell(row, m[Field.UPSELL]))?.takeIf { it > 0 } ?: 0.0
            val job = t.cell(row, m[Field.JOB]).take(100)
            val name = t.cell(row, m[Field.CLIENT])
            val closed = yesNo(t.cell(row, m[Field.CLOSED])) ?: (amount > 0 || upsell > 0)
            val (first, last) = splitName(name)
            val probe = Client(0, first.take(100), last.take(100), "", "")
            var client = (if (name.isNotEmpty()) index.match(probe) else null)
                ?: job.takeIf { it.isNotEmpty() }?.let { index.byRef[norm(it)] }
            if (client == null && name.isNotEmpty() && addClients) {
                client = probe.copy(id = newId(), reference = job, stage = if (closed) ClientStage.WON else ClientStage.PROPOSAL)
                newClients += client
                index.add(client)
            }
            val jobRef = job.ifEmpty { client?.reference.orEmpty() }
            val key = "$day|${"%.2f".format(Locale.ROOT, amount)}|${client?.id ?: ""}|${norm(jobRef)}"
            if (!seen.add(key)) { dupes++; return@forEachIndexed }
            sales += Sale(
                id = newId(),
                clientId = client?.id,
                timestamp = day.atTime(LocalTime.NOON).atZone(zone).toInstant().toEpochMilli(),
                closed = closed,
                upsellOffered = upsell > 0,
                upsellAccepted = upsell > 0,
                amount = amount,
                upsellAmount = upsell,
                durationSeconds = 0,
                notes = t.cell(row, m[Field.NOTES_SALE]),
                commissionPercent = percent(t.cell(row, m[Field.COMMISSION]))
                    ?: if (data.tierSchedule != null) null else data.defaultCommissionPercent, // tiered plans rate it
                commissionOnUpsellOnly = data.defaultCommissionUpsellOnly,
                jobRef = jobRef,
            )
        }
        return Outcome(newClients, sales, dupes, bad)
    }

    /** A blank sheet with the columns the importer recognizes, for people starting from scratch. */
    fun template(kind: Kind): String = when (kind) {
        Kind.CLIENTS -> "First name,Last name,Phone,Email,Job #,Occupation,Stage,Notes\r\n"
        Kind.SALES -> "Date,Client,Job #,Sale,Upsell,Status,Commission %,Notes\r\n"
    }
}
