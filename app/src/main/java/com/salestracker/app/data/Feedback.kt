package com.salestracker.app.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.annotation.StringRes
import com.salestracker.app.R

/**
 * Feedback by email: ideas, problems, questions or praise. Like crash reports, nothing is sent by the app: the
 * user's own email app opens with the message addressed to [CrashLog.SUPPORT_EMAIL], and they press Send there.
 * The message holds only what they typed and, if they leave it ticked, the app version and phone model.
 */
object Feedback {
    enum class Kind(val tag: String, @StringRes val label: Int) {
        IDEA("idea", R.string.fb_kind_idea),
        PROBLEM("problem", R.string.fb_kind_problem),
        QUESTION("question", R.string.fb_kind_question),
        LOVE("love", R.string.fb_kind_love),
    }

    /** What the "include app version and phone model" box adds, shown to the user word for word. */
    fun details(context: Context): String =
        "Quota Vault ${CrashLog.appVersion(context)} · Android ${Build.VERSION.RELEASE} · ${CrashLog.phoneModel()}"

    /** The email's text: their message, then a short footer (the kind as an English tag, so any language can be sorted). */
    fun body(message: String, kind: Kind, details: String?): String = buildString {
        append(message.trim())
        append("\n\n\n----\n")
        append("[").append(kind.tag).append("]")
        if (details != null) append("\n").append(details)
    }

    /** Opens the email app with the message filled in. False if there's no email app. */
    fun email(context: Context, subject: String, body: String): Boolean {
        val send = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_EMAIL, arrayOf(CrashLog.SUPPORT_EMAIL))
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
