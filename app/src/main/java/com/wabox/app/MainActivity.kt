package com.wabox.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wabox.app.ui.LocalAppContainer
import com.wabox.app.ui.designsystem.WaTheme
import com.wabox.app.ui.designsystem.isDarkTheme
import com.wabox.app.ui.navigation.WaNavHost
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
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
            val s = settings ?: return@setContent
            ready = true
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
                    WaNavHost(startWithOnboarding, pendingStatusId, onConsumedStatus = { pendingStatusId.value = null })
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
