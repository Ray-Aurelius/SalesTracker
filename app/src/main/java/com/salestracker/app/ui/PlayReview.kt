package com.salestracker.app.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.google.android.play.core.review.ReviewManagerFactory
import com.salestracker.app.R

/**
 * Ratings on Google Play. Nothing here sends any of the user's data: the rating window and the Play Store page
 * belong to the Google Play Store app, which handles the review itself. The app never learns whether, or how,
 * someone rated it.
 */
object PlayReview {
    private const val STORE_APP = "com.android.vending"

    /** Opens Quota Vault's page in the Play Store app (or in the browser if the Play Store isn't there). */
    fun openListing(context: Context) {
        val id = context.packageName
        val store = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$id"))
            .setPackage(STORE_APP).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$id"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(store)
        } catch (e: ActivityNotFoundException) {
            try {
                context.startActivity(web)
            } catch (e2: ActivityNotFoundException) {
                Toast.makeText(context, R.string.rate_no_store, Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Asks Google Play to show its rating window over the app. Google decides whether it actually appears
     * (it limits how often), and nothing is reported back, so this never waits on or reacts to the outcome.
     */
    fun requestInAppReview(activity: Activity) {
        try {
            val manager = ReviewManagerFactory.create(activity)
            manager.requestReviewFlow().addOnCompleteListener { task ->
                if (task.isSuccessful && !activity.isFinishing) manager.launchReviewFlow(activity, task.result)
            }
        } catch (e: Exception) {
            // No Play Store on this phone, or it couldn't be reached: nothing to show.
        }
    }
}
