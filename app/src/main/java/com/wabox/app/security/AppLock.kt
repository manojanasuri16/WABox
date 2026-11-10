package com.wabox.app.security

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.wabox.app.data.prefs.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Optional App Lock. WABox locks on every cold start and after it has been in the background
 * longer than the chosen timeout. The lock is a UI gate: it hides content behind an opaque
 * screen until the user authenticates with a biometric or the device screen lock.
 */
class AppLock(private val settings: SettingsRepository, private val scope: CoroutineScope) : DefaultLifecycleObserver {
    /** null until settings are read; the splash screen stays up until then. */
    private val _locked = MutableStateFlow<Boolean?>(null)
    val locked: StateFlow<Boolean?> = _locked.asStateFlow()

    @Volatile private var backgroundAt = 0L

    init {
        scope.launch { _locked.value = settings.current().appLock }
    }

    override fun onStop(owner: LifecycleOwner) {
        backgroundAt = System.currentTimeMillis()
    }

    override fun onStart(owner: LifecycleOwner) {
        val awayFrom = backgroundAt
        if (awayFrom == 0L) return // cold start: the initial state already applies
        scope.launch {
            val s = settings.current()
            if (s.appLock && System.currentTimeMillis() - awayFrom >= s.lockTimeout.millis) _locked.value = true
        }
    }

    fun unlock() { _locked.value = false }

    /** Called after the user turns the setting off (already authenticated). */
    fun disabled() { _locked.value = false }
}

/** BiometricPrompt wrapper. Device credential is always allowed, so anyone with a screen lock can use App Lock. */
object Biometrics {
    /** BIOMETRIC_STRONG + DEVICE_CREDENTIAL is unsupported on API 29, so WEAK is used there. */
    val authenticators: Int
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) BIOMETRIC_STRONG or DEVICE_CREDENTIAL
        else BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    fun canAuthenticate(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS

    fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String?,
        onSuccess: () -> Unit,
        onError: (String?) -> Unit = {},
    ) {
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                val userDismissed = errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON || errorCode == BiometricPrompt.ERROR_CANCELED
                onError(if (userDismissed) null else errString.toString())
            }
        }
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .apply { if (subtitle != null) setSubtitle(subtitle) }
            .setAllowedAuthenticators(authenticators)
            .build()
        BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback).authenticate(info)
    }
}
