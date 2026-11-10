package com.wabox.app.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wabox.app.R
import com.wabox.app.core.model.AppKind
import com.wabox.app.core.model.SourceHealth
import com.wabox.app.ui.LocalAppContainer
import com.wabox.app.ui.appName
import com.wabox.app.ui.designsystem.AppKindBadge
import com.wabox.app.ui.designsystem.ButtonStyle
import com.wabox.app.ui.designsystem.Wa
import com.wabox.app.ui.designsystem.WaButton
import com.wabox.app.ui.designsystem.WaMotion
import com.wabox.app.ui.designsystem.WaRadius
import com.wabox.app.ui.designsystem.WaSpace
import com.wabox.app.ui.rememberSourcePicker
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    val reduced = Wa.reducedMotion
    Box(Modifier.fillMaxSize().background(Wa.colors.background)) {
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                if (reduced) fadeIn() togetherWith fadeOut()
                else (slideInHorizontally(WaMotion.standard()) { it / 3 } + fadeIn(WaMotion.standard())) togetherWith
                    (slideOutHorizontally(WaMotion.standard()) { -it / 3 } + fadeOut(WaMotion.standard()))
            },
            label = "onboarding",
        ) { s ->
            if (s == 0) Welcome(onContinue = { step = 1 }) else Connect(onDone)
        }
    }
}

@Composable
private fun Welcome(onContinue: () -> Unit) {
    val c = Wa.colors
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = WaSpace.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(56.dp))
            Image(painterResource(R.drawable.app_mark), null, Modifier.size(88.dp).clip(RoundedCornerShape(22.dp)))
            Spacer(Modifier.height(WaSpace.xxl))
            Text("Every status.\nEvery account.\nOne place.", style = Wa.type.largeTitle.copy(fontSize = Wa.type.largeTitle.fontSize * 1.06f), color = c.label, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(WaSpace.md))
            Text(
                "WABox keeps the statuses you've viewed in WhatsApp and WhatsApp Business in one place, so you can save the ones that matter before they expire.",
                style = Wa.type.body, color = c.secondaryLabel, modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(WaSpace.xxxl))
            Feature(Icons.Rounded.CloudOff, "Private by design", "No internet access. Everything stays on this phone.")
            Feature(Icons.Rounded.FolderOpen, "Only what you allow", "You pick the folder in Android's own file picker. WABox reads nothing else.")
            Feature(Icons.Rounded.Inventory2, "Saves that last", "Saved statuses go to your gallery and stay after they disappear from WhatsApp.")
        }
        WaButton("Get started", onContinue, Modifier.fillMaxWidth().widthIn(max = 480.dp).padding(vertical = WaSpace.lg))
    }
}

@Composable
private fun Feature(icon: ImageVector, title: String, body: String) {
    val c = Wa.colors
    Row(Modifier.fillMaxWidth().padding(vertical = WaSpace.md), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(c.accentSoft), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(22.dp), tint = c.accent)
        }
        Spacer(Modifier.width(WaSpace.lg))
        Column(Modifier.weight(1f)) {
            Text(title, style = Wa.type.headline, color = c.label)
            Spacer(Modifier.height(2.dp))
            Text(body, style = Wa.type.subhead, color = c.secondaryLabel)
        }
    }
}

@Composable
private fun Connect(onDone: () -> Unit) {
    val c = Wa.colors
    val container = LocalAppContainer.current
    val sources by container.sourceRepository.sources.collectAsStateWithLifecycle(initialValue = emptyList())
    val picker = rememberSourcePicker()
    val scope = rememberCoroutineScope()
    fun finish() = scope.launch { container.settings.setOnboardingDone(true); onDone() }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = WaSpace.xxl),
    ) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(48.dp))
            Text("Connect your apps", style = Wa.type.largeTitle, color = c.label)
            Spacer(Modifier.height(WaSpace.sm))
            Text(
                buildAnnotatedString {
                    append("Android will open its file picker in the right folder. Tap ")
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = c.label)) { append("Use this folder") }
                    append(", then ")
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = c.label)) { append("Allow") }
                    append(".")
                },
                style = Wa.type.body, color = c.secondaryLabel,
            )
            Spacer(Modifier.height(WaSpace.xxl))
            listOf(AppKind.WHATSAPP, AppKind.BUSINESS).forEach { kind ->
                val source = sources.firstOrNull { it.appKind == kind }
                val installed = container.grants.isInstalled(kind)
                AppCard(
                    kind = kind,
                    status = when {
                        source == null && !installed -> "Not installed"
                        source == null -> "Not connected"
                        source.health == SourceHealth.OK -> "Connected"
                        source.health == SourceHealth.NO_STATUS_FOLDER -> "Connected · no statuses yet"
                        else -> "Access lost"
                    },
                    connected = source != null && source.health != SourceHealth.ACCESS_LOST,
                    enabled = installed || source != null,
                    onConnect = { picker.connect(kind) },
                )
                Spacer(Modifier.height(WaSpace.md))
            }
            WaButton("Add another folder", { picker.addOther() }, style = ButtonStyle.Plain, compact = true)
            Spacer(Modifier.height(WaSpace.lg))
            Text(
                "Using two accounts in one WhatsApp? WhatsApp doesn't reveal which account a status came from. WABox shows each status " +
                    "folder it finds, and you can name them later.",
                style = Wa.type.footnote, color = c.secondaryLabel,
            )
        }
        Column(Modifier.padding(vertical = WaSpace.lg), verticalArrangement = Arrangement.spacedBy(WaSpace.sm)) {
            WaButton("Continue", { finish() }, Modifier.fillMaxWidth(), enabled = sources.isNotEmpty())
            if (sources.isEmpty()) WaButton("Not now", { finish() }, Modifier.fillMaxWidth(), style = ButtonStyle.Plain)
        }
    }
}

@Composable
private fun AppCard(kind: AppKind, status: String, connected: Boolean, enabled: Boolean, onConnect: () -> Unit) {
    val c = Wa.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(WaRadius.lg)).background(c.surfaceSecondary).padding(WaSpace.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppKindBadge(kind, 44.dp)
        Spacer(Modifier.width(WaSpace.lg))
        Column(Modifier.weight(1f)) {
            Text(appName(kind), style = Wa.type.headline, color = c.label)
            Text(status, style = Wa.type.footnote, color = if (connected) c.success else c.secondaryLabel)
        }
        if (connected) {
            Icon(Icons.Rounded.CheckCircle, "Connected", Modifier.size(28.dp), tint = c.success)
        } else {
            WaButton("Connect", onConnect, style = ButtonStyle.Filled, compact = true, enabled = enabled)
        }
    }
}
