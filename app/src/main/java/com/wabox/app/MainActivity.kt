package com.wabox.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import com.wabox.app.ui.MessageTone
import com.wabox.app.ui.lock.LockScreen
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wabox.app.ui.LocalAppContainer
import com.wabox.app.ui.designsystem.WaTheme
import com.wabox.app.ui.designsystem.isDarkTheme
import com.wabox.app.ui.navigation.WaNavHost
import kotlinx.coroutines.flow.MutableStateFlow

/** FragmentActivity (a ComponentActivity) because BiometricPrompt needs one. */
class MainActivity : FragmentActivity() {
    private val pendingStatusId = MutableStateFlow<String?>(null)
    @Volatile private var ready = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { !ready }
        enableEdgeToEdge()
        handleIntent(intent)
        val container = (application as WABoxApp).container

        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
            val locked by container.appLock.locked.collectAsStateWithLifecycle()
            val s = settings ?: return@setContent
            val isLocked = locked ?: return@setContent
            ready = true
            val scope = rememberCoroutineScope()
            // With App Lock on, keep app content out of the recents thumbnail (API 33+).
            LaunchedEffect(s.appLock) {
                if (android.os.Build.VERSION.SDK_INT >= 33) setRecentsScreenshotEnabled(!s.appLock)
            }
            val startWithOnboarding = remember { !s.onboardingDone }
            val dark = isDarkTheme(s.themeMode)
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                )
                onDispose {}
            }
            WaTheme(dark = dark) {
                CompositionLocalProvider(LocalAppContainer provides container) {
                    Box(Modifier.fillMaxSize()) {
                        WaNavHost(startWithOnboarding, pendingStatusId, onConsumedStatus = { pendingStatusId.value = null })
                        if (isLocked && s.appLock) {
                            LockScreen(
                                onUnlocked = container.appLock::unlock,
                                onNoScreenLock = {
                                    scope.launch {
                                        container.settings.setAppLock(false)
                                        container.appLock.disabled()
                                        container.messages.post("App Lock turned off: this device no longer has a screen lock", MessageTone.ERROR)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent?.getStringExtra(EXTRA_STATUS_ID)?.let { pendingStatusId.value = it }
    }

    companion object {
        const val EXTRA_STATUS_ID = "com.wabox.app.extra.STATUS_ID"
    }
}
