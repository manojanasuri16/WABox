package com.wabox.app.ui.lock

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.wabox.app.R
import com.wabox.app.security.Biometrics
import com.wabox.app.ui.designsystem.Wa
import com.wabox.app.ui.designsystem.WaButton
import com.wabox.app.ui.designsystem.WaSpace
import com.wabox.app.ui.viewer.findActivity

/**
 * Opaque lock screen drawn over the app. The app underneath stays composed so in-flight results
 * (folder picker, delete consent) aren't lost, but it receives no input and nothing shows through.
 */
@Composable
fun LockScreen(onUnlocked: () -> Unit, onNoScreenLock: () -> Unit) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() as? FragmentActivity }
    val c = Wa.colors
    var error by remember { mutableStateOf<String?>(null) }
    var prompting by remember { mutableStateOf(false) }

    fun prompt() {
        val a = activity ?: return
        if (prompting) return
        if (!Biometrics.canAuthenticate(a)) {
            // The device screen lock was removed after App Lock was enabled. There is nothing to
            // verify against, so turn App Lock off explicitly (and say so) rather than trap the user.
            onNoScreenLock()
            return
        }
        prompting = true
        error = null
        Biometrics.authenticate(
            a, title = "Unlock WABox", subtitle = null,
            onSuccess = { prompting = false; onUnlocked() },
            onError = { msg -> prompting = false; error = msg },
        )
    }

    // Ask as soon as the lock screen is visible, and again whenever the user returns to it.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { prompt() }
    BackHandler { activity?.moveTaskToBack(true) }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.background)
            // Swallow every touch so nothing beneath can be reached.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .navigationBarsPadding()
            .padding(horizontal = WaSpace.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Image(painterResource(R.drawable.app_mark), null, Modifier.size(84.dp).clip(RoundedCornerShape(21.dp)))
        Spacer(Modifier.height(WaSpace.xl))
        Text("WABox is locked", style = Wa.type.title1, color = c.label)
        Spacer(Modifier.height(WaSpace.sm))
        Text(
            "Use your fingerprint, face, or screen lock to continue.",
            style = Wa.type.body, color = c.secondaryLabel, textAlign = TextAlign.Center,
        )
        if (error != null) {
            Spacer(Modifier.height(WaSpace.md))
            Text(
                error!!, style = Wa.type.footnote, color = c.destructive, textAlign = TextAlign.Center,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        Spacer(Modifier.weight(1f))
        WaButton(
            "Unlock", { prompt() },
            Modifier.fillMaxWidth().widthIn(max = 480.dp).padding(bottom = WaSpace.xxl),
            icon = Icons.Rounded.Fingerprint,
        )
    }
}
