package com.wabox.app.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.wabox.app.AppContainer
import com.wabox.app.core.model.AddSourceResult
import com.wabox.app.core.model.AppKind
import com.wabox.app.core.model.Source
import kotlinx.coroutines.launch

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }

/** Bottom inset reserved by the floating bottom bar (0 outside the tab shell). */
val LocalBottomBarHeight = staticCompositionLocalOf { 0.dp }

@Composable
inline fun <reified VM : ViewModel> waViewModel(
    key: String? = null,
    crossinline create: (AppContainer, SavedStateHandle) -> VM,
): VM {
    val container = LocalAppContainer.current
    return viewModel(key = key, factory = viewModelFactory { initializer { create(container, createSavedStateHandle()) } })
}

@Composable
fun statusBarTop(): Dp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

@Composable
fun navBarBottom(): Dp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

/** Launches the system folder picker pre-pointed at the right WhatsApp folder. */
class SourcePicker internal constructor(private val launchImpl: (AppKind?, Source?) -> Unit) {
    fun connect(kind: AppKind) = launchImpl(kind, null)
    fun reconnect(source: Source) = launchImpl(null, source)
    fun addOther() = launchImpl(AppKind.OTHER, null)
}

@Composable
fun rememberSourcePicker(onResult: (AddSourceResult) -> Unit = {}): SourcePicker {
    val container = LocalAppContainer.current
    var pendingReconnect by rememberSaveable { mutableStateOf<Long?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val reconnectId = pendingReconnect
        pendingReconnect = null
        if (uri == null) return@rememberLauncherForActivityResult // user backed out: their choice
        // App scope: the flow must finish even if this screen leaves composition.
        container.appScope.launch {
            val result = container.connectSource(uri, reconnectId)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onResult(result) }
        }
    }
    return remember(launcher) {
        SourcePicker { kind, reconnect ->
            pendingReconnect = reconnect?.id
            val initial = when {
                reconnect != null -> container.grants.initialUriForDocument(reconnect.rootDocumentId)
                kind != null -> container.grants.initialUriFor(kind)
                else -> null
            }
            try {
                launcher.launch(initial)
            } catch (e: ActivityNotFoundException) {
                container.messages.post("This device has no system file picker, so WABox can't request folder access.", MessageTone.ERROR)
            }
        }
    }
}

@Composable
fun rememberAppLauncher(): (AppKind) -> Unit {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    return remember {
        { kind ->
            val intent = container.grants.launchIntent(kind)
            if (intent == null) {
                container.messages.post("${appName(kind)} isn't installed", MessageTone.ERROR)
            } else {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }
}
