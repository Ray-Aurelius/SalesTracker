package com.salestracker.app.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Opt-in crash reports.
 *
 * When the app crashes, a short technical report is saved in the app's private storage on this phone.
 * Nothing is ever sent by the app (it has no internet access). On the next launch the user is asked
 * whether to email it to [SUPPORT_EMAIL], and can read every line first; their own email app does the sending.
 *
 * A report holds ONLY: the app version, the Android version, the phone's make and model, the time (UTC),
 * and the error's type and the lines of the app's code it passed through. Error messages are always
 * dropped, because a message can quote text the user typed (a client's name, a phone number, an amount).
 * No sales, clients, notes, settings, file paths or account details are ever included.
 */
object CrashLog {
    const val SUPPORT_EMAIL = "quotavaultsupport@gmail.com"

    private const val DIR = "crash-reports"
    private const val SEEN = ".seen"
    /** Present when the user has turned crash reports off. Kept outside the reports folder so "Delete all" leaves the choice alone. */
    private const val OFF = "crash-reports-off"
    private const val KEEP = 5
    private const val MAX_FRAMES = 40
    private const val MAX_CAUSES = 6
    private val MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000

    data class Report(val file: File, val at: Long, val text: String)

    /** Saves a report for any crash, then hands the crash on so Android still closes the app as usual. */
    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            if (isEnabled(app)) runCatching { save(app, thread, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    fun save(context: Context, thread: Thread, error: Throwable, now: Long = System.currentTimeMillis()) {
        val dir = dir(context)
        File(dir, "crash-$now.txt").writeText(format(error, appVersion(context), now, isMainThread = thread.name == "main"))
        prune(context, now)
    }

    /** The report, built only from technical facts. Error messages are never included. */
    fun format(error: Throwable, appVersion: String, at: Long, isMainThread: Boolean = true): String = buildString {
        appendLine("Quota Vault crash report")
        appendLine("App: $appVersion")
        appendLine("Android: ${Build.VERSION.RELEASE ?: "?"} (API ${Build.VERSION.SDK_INT})")
        appendLine("Phone: ${phoneModel()}")
        appendLine("When: ${DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC).format(Instant.ofEpochMilli(at))} UTC")
        appendLine("Thread: ${if (isMainThread) "main" else "background"}")
        appendLine()
        var t: Throwable? = error
        var depth = 0
        val seen = HashSet<Throwable>()
        while (t != null && depth <= MAX_CAUSES && seen.add(t)) {
            appendLine((if (depth == 0) "" else "Caused by: ") + t.javaClass.name)
            val frames = t.stackTrace
            frames.take(MAX_FRAMES).forEach { appendLine("    at ${frame(it)}") }
            if (frames.size > MAX_FRAMES) appendLine("    … ${frames.size - MAX_FRAMES} more")
            t = t.cause
            depth++
        }
        appendLine()
        append("Error messages are left out because they can contain text you typed.")
    }

    /** Class, method, file and line: code locations only, never values. */
    private fun frame(e: StackTraceElement): String {
        val where = when {
            e.isNativeMethod -> "native"
            e.fileName != null && e.lineNumber >= 0 -> "${e.fileName}:${e.lineNumber}"
            e.fileName != null -> e.fileName
            else -> "unknown"
        }
        return "${e.className}.${e.methodName}($where)"
    }

    private fun phoneModel(): String {
        val maker = Build.MANUFACTURER.orEmpty().replaceFirstChar { it.uppercase() }
        val model = Build.MODEL.orEmpty()
        return if (model.startsWith(maker, ignoreCase = true)) model else "$maker $model".trim()
    }

    fun appVersion(context: Context): String = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
        "${info.versionName} ($code)"
    }.getOrDefault("?")

    private fun dir(context: Context) = File(context.noBackupFilesDir, DIR).apply { mkdirs() }

    /** Saved reports, newest first. Reports older than 30 days are removed. */
    fun reports(context: Context, now: Long = System.currentTimeMillis()): List<Report> {
        prune(context, now)
        return files(context).mapNotNull { f -> runCatching { Report(f, stamp(f), f.readText()) }.getOrNull() }
    }

    private fun files(context: Context) =
        dir(context).listFiles { f -> f.name.startsWith("crash-") && f.name.endsWith(".txt") }.orEmpty()
            .sortedByDescending { stamp(it) }

    private fun stamp(f: File) = f.name.removePrefix("crash-").removeSuffix(".txt").toLongOrNull() ?: 0L

    private fun prune(context: Context, now: Long) {
        files(context).forEachIndexed { i, f -> if (i >= KEEP || now - stamp(f) > MAX_AGE_MS) f.delete() }
    }

    /** True once per new crash: the prompt on the next launch. */
    fun hasUnseen(context: Context): Boolean {
        val newest = files(context).firstOrNull()?.let(::stamp) ?: return false
        val seen = File(dir(context), SEEN).takeIf { it.exists() }?.readText()?.toLongOrNull() ?: 0L
        return newest > seen
    }

    fun markSeen(context: Context) {
        val newest = files(context).firstOrNull()?.let(::stamp) ?: return
        File(dir(context), SEEN).writeText(newest.toString())
    }

    fun delete(report: Report) { report.file.delete() }

    fun deleteAll(context: Context) { File(context.noBackupFilesDir, DIR).deleteRecursively() }

    /** Whether a report is saved when the app crashes. On by default; the user can turn it off at any time. */
    fun isEnabled(context: Context) = !File(context.noBackupFilesDir, OFF).exists()

    /** Turning reports off also deletes every saved one. */
    fun setEnabled(context: Context, on: Boolean) {
        val flag = File(context.noBackupFilesDir, OFF)
        if (on) flag.delete() else { flag.createNewFile(); deleteAll(context) }
    }

    /** "Erase all data": every report and the on/off choice go back to the start. */
    fun eraseAll(context: Context) {
        deleteAll(context)
        File(context.noBackupFilesDir, OFF).delete()
    }

    /**
     * Opens the user's email app with the report filled in, addressed to [SUPPORT_EMAIL].
     * They see the whole message and choose whether to send it. Returns false with no email app.
     */
    fun email(context: Context, report: Report, subject: String, intro: String): Boolean {
        val body = "$intro\n\n\n----\n${report.text}"
        val send = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_EMAIL, arrayOf(SUPPORT_EMAIL))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            // Only email apps: the selector limits the choice to apps that handle mailto: links.
            selector = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(send)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }
}
