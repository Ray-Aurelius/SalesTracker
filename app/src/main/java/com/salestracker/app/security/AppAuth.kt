package com.salestracker.app.security

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.salestracker.app.R

/**
 * Uses the phone's own lock: fingerprint or face if set up, otherwise the phone's PIN, pattern or password.
 * The app never sees or stores any of these; Android just tells it "yes, that's the owner".
 */
object AppAuth {
    private const val ALLOWED = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    /** False if the phone has no screen lock at all, so there'd be nothing to unlock with. */
    fun isAvailable(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(ALLOWED) == BiometricManager.BIOMETRIC_SUCCESS

    /** [onStart] runs before the prompt appears and [onEnd] when it closes either way (success, cancel or error). */
    fun authenticate(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onStart: () -> Unit = {},
        onEnd: () -> Unit = {},
    ) {
        onStart()
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onEnd()
                    onSuccess()
                }
                // Cancelled or locked out: stay as we are; the user can tap Unlock to try again.
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onEnd()
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(activity.getString(R.string.unlock_prompt_title))
                .setSubtitle(activity.getString(R.string.unlock_prompt_subtitle))
                .setAllowedAuthenticators(ALLOWED)
                .build()
        )
    }
}

fun Context.findActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
