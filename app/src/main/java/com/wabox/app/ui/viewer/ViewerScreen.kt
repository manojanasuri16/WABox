package com.wabox.app.ui.viewer

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.wabox.app.core.model.SaveResult
import com.wabox.app.core.util.Formats
import com.wabox.app.ui.designsystem.IconCircleButton
import com.wabox.app.ui.designsystem.Wa
import com.wabox.app.ui.designsystem.WaMotion
import com.wabox.app.ui.designsystem.WaSpace
import com.wabox.app.ui.designsystem.pressable
import com.wabox.app.ui.designsystem.rememberHaptics
import com.wabox.app.ui.waViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(onClose: () -> Unit) {
    val vm = waViewModel { c, h -> ViewerViewModel(c, h) }
    val state by vm.state.collectAsStateWithLifecycle()
    val info by vm.info.collectAsStateWithLifecycle()
    val consent by vm.consent.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()

    var chrome by rememberSaveable { mutableStateOf(true) }
    var zoomed by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<ViewerItem?>(null) }
    val dismiss = remember { Animatable(0f) }

    ImmersiveSystemBars(chromeVisible = chrome)
    BackHandler(onBack = onClose)

    val player = remember {
        ExoPlayer.Builder(context).build().apply { repeatMode = Player.REPEAT_MODE_ONE }
    }
    DisposableEffect(Unit) { onDispose { player.release() } }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { player.pause() }

    val consentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        vm.onConsentResult(r.resultCode == Activity.RESULT_OK) { if (state.items.size <= 1) onClose() }
    }
    LaunchedEffect(consent) {
        consent?.let { consentLauncher.launch(IntentSenderRequest.Builder(it.first).build()) }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (!state.loaded) return@Box
        if (state.items.isEmpty()) {
            MissingMedia(isVideo = false)
            CloseOnly(onClose)
            return@Box
        }
        val pager = rememberPagerState(initialPage = state.startIndex.coerceIn(0, state.items.lastIndex)) { state.items.size }
        val current = state.items.getOrNull(pager.currentPage.coerceAtMost(state.items.lastIndex)) ?: return@Box

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val height = with(LocalDensity.current) { maxHeight.toPx() }
            val progress = (abs(dismiss.value) / height).coerceIn(0f, 1f)
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 1f - progress * 1.4f)))

            HorizontalPager(
                state = pager,
                beyondViewportPageCount = 1,
                userScrollEnabled = !zoomed,
                key = { state.items[it].key },
                pageSpacing = 12.dp,
                modifier = Modifier
                    .fillMaxSize()
                    .draggable(
                        orientation = Orientation.Vertical,
                        enabled = !zoomed,
                        state = rememberDraggableState { delta ->
                            scope.launch {
                                val next = dismiss.value + delta
                                // Upward drags meet rubber-band resistance; downward follows the finger 1:1.
                                dismiss.snapTo(if (next < 0) -WaMotion.rubberBand(-next, height) else next)
                            }
                        },
                        onDragStopped = { velocity ->
                            val projected = dismiss.value + WaMotion.project(velocity)
                            if (projected > height * 0.28f) {
                                dismiss.animateTo(height, WaMotion.momentum(), initialVelocity = velocity)
                                onClose()
                            } else {
                                dismiss.animateTo(0f, WaMotion.momentum(), initialVelocity = velocity)
                            }
                        },
                    )
                    .graphicsLayer {
                        translationY = dismiss.value
                        val s = 1f - progress * 0.18f
                        scaleX = s; scaleY = s
                    },
            ) { page ->
                val item = state.items[page]
                when {
                    state.isMissing(item) -> MissingMedia(item.isVideo)
                    item.isVideo -> VideoPage(item.uri, active = page == pager.settledPage, player = player, onTap = { chrome = !chrome })
                    else -> ZoomableImage(item.uri, onTap = { chrome = !chrome }, onZoomChanged = { if (page == pager.currentPage) zoomed = it })
                }
            }

            AnimatedVisibility(chrome && progress < 0.05f, enter = fadeIn(WaMotion.standard()), exit = fadeOut(WaMotion.standard()), modifier = Modifier.align(Alignment.TopCenter)) {
                TopChrome(current, pager.currentPage, state.items.size, onClose, onInfo = { vm.loadInfo(context, current) })
            }
            AnimatedVisibility(chrome && progress < 0.05f, enter = fadeIn(WaMotion.standard()), exit = fadeOut(WaMotion.standard()), modifier = Modifier.align(Alignment.BottomCenter)) {
                Column(
                    Modifier.fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))))
                        .navigationBarsPadding()
                        .padding(horizontal = WaSpace.lg, vertical = WaSpace.md)
                ) {
                    if (current.isVideo && pager.settledPage == pager.currentPage && !state.isMissing(current)) VideoControls(player)
                    BottomActions(
                        item = current,
                        saved = current.savedId != null || current.statusId in state.savedStatusIds,
                        saving = current.statusId in state.saving,
                        missing = state.isMissing(current),
                        onSave = { vm.save(current) { r -> if (r is SaveResult.Saved) haptics.confirm() else if (r !is SaveResult.AlreadySaved) haptics.reject() } },
                        onShare = { vm.share(context, current) },
                        onDelete = { confirmDelete = current },
                    )
                }
            }
        }
    }

    info?.let { rows ->
        ModalBottomSheet(onDismissRequest = vm::dismissInfo, containerColor = Wa.colors.surface) {
            Column(Modifier.navigationBarsPadding().padding(horizontal = WaSpace.gutter).padding(bottom = WaSpace.xxl)) {
                Text("Details", style = Wa.type.title3, color = Wa.colors.label)
                Spacer(Modifier.height(WaSpace.md))
                rows.forEachIndexed { i, row ->
                    if (i > 0) HorizontalDivider(thickness = 0.5.dp, color = Wa.colors.separator)
                    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                        Text(row.label, style = Wa.type.body, color = Wa.colors.secondaryLabel, modifier = Modifier.width(110.dp))
                        Text(row.value, style = Wa.type.body, color = Wa.colors.label, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }

    confirmDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete from gallery?") },
            text = { Text("This removes the saved copy from your device. It can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    vm.delete(item) { if (state.items.size <= 1) onClose() }
                }) { Text("Delete", color = Wa.colors.destructive, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
            containerColor = Wa.colors.surface,
        )
    }
}

@Composable
private fun TopChrome(item: ViewerItem, index: Int, count: Int, onClose: () -> Unit, onInfo: () -> Unit) {
    Box(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)))
            .statusBarsPadding()
            .padding(horizontal = WaSpace.md, vertical = WaSpace.sm)
    ) {
        IconCircleButton(Icons.Rounded.Close, "Close", onClose, Modifier.align(Alignment.CenterStart), container = Color.White.copy(alpha = 0.16f), tint = Color.White)
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(item.sourceLabel, style = Wa.type.headline, color = Color.White, maxLines = 1)
            Text("${Formats.relative(item.timestamp)} · ${index + 1} of $count", style = Wa.type.caption, color = Color.White.copy(alpha = 0.7f))
        }
        IconCircleButton(Icons.Rounded.Info, "Details", onInfo, Modifier.align(Alignment.CenterEnd), container = Color.White.copy(alpha = 0.16f), tint = Color.White)
    }
}

@Composable
private fun BottomActions(
    item: ViewerItem,
    saved: Boolean,
    saving: Boolean,
    missing: Boolean,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    val glass = Color.White.copy(alpha = 0.16f)
    Row(Modifier.fillMaxWidth().padding(top = WaSpace.sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(WaSpace.md)) {
        IconCircleButton(Icons.Rounded.IosShare, "Share", onShare, size = 48.dp, container = glass, tint = Color.White, enabled = !missing)
        if (item.savedId == null) {
            val bg = if (saved) Wa.colors.success else Color.White
            val fg = if (saved) Color.White else Color.Black
            Row(
                Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(24.dp)).background(bg.copy(alpha = if (missing) 0.4f else 1f))
                    .pressable(enabled = !saved && !saving && !missing, onClick = onSave),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                if (saving) {
                    androidx.compose.material3.CircularProgressIndicator(Modifier.size(18.dp), color = fg, strokeWidth = 2.dp)
                } else {
                    androidx.compose.material3.Icon(if (saved) Icons.Rounded.Check else Icons.Rounded.Download, null, Modifier.size(20.dp), tint = fg)
                    Spacer(Modifier.width(8.dp))
                    Text(if (saved) "Saved" else "Save", style = Wa.type.headline, color = fg)
                }
            }
        } else {
            Spacer(Modifier.weight(1f))
            IconCircleButton(Icons.Rounded.Delete, "Delete", onDelete, size = 48.dp, container = glass, tint = Color.White)
        }
    }
}

@Composable
private fun VideoControls(player: ExoPlayer) {
    var playing by remember { mutableStateOf(player.isPlaying) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrub by remember { mutableFloatStateOf(0f) }
    var muted by remember { mutableStateOf(player.volume == 0f) }
    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
        }
        player.addListener(l)
        onDispose { player.removeListener(l) }
    }
    LaunchedEffect(player) {
        while (true) {
            if (!scrubbing) position = player.currentPosition
            duration = player.duration.takeIf { it > 0 } ?: 0L
            delay(200)
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        IconCircleButton(
            if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            if (playing) "Pause" else "Play",
            { if (player.isPlaying) player.pause() else player.play() },
            container = Color.Transparent, tint = Color.White, size = 40.dp,
        )
        Text(Formats.duration(if (scrubbing) (scrub * duration).toLong() else position), style = Wa.type.caption, color = Color.White, modifier = Modifier.width(40.dp))
        Slider(
            value = if (scrubbing) scrub else if (duration > 0) position.toFloat() / duration else 0f,
            onValueChange = { scrubbing = true; scrub = it },
            onValueChangeFinished = { player.seekTo((scrub * duration).toLong()); scrubbing = false },
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.White.copy(alpha = 0.3f)),
            modifier = Modifier.weight(1f).height(32.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(Formats.duration(duration), style = Wa.type.caption, color = Color.White.copy(alpha = 0.7f))
        IconCircleButton(
            if (muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
            if (muted) "Unmute" else "Mute",
            { muted = !muted; player.volume = if (muted) 0f else 1f },
            container = Color.Transparent, tint = Color.White, size = 40.dp,
        )
    }
}

@Composable
private fun CloseOnly(onClose: () -> Unit) {
    Box(Modifier.fillMaxSize().statusBarsPadding().padding(WaSpace.md)) {
        IconCircleButton(Icons.Rounded.Close, "Close", onClose, container = Color.White.copy(alpha = 0.16f), tint = Color.White)
    }
}

/** Light system-bar icons over black; bars hide with the chrome and come back on swipe. */
@Composable
private fun ImmersiveSystemBars(chromeVisible: Boolean) {
    val view = LocalView.current
    val window = remember(view) { view.context.findActivity()?.window }
    DisposableEffect(window) {
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val lightStatus = controller?.isAppearanceLightStatusBars
        val lightNav = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            if (lightStatus != null) controller.isAppearanceLightStatusBars = lightStatus
            if (lightNav != null) controller.isAppearanceLightNavigationBars = lightNav
        }
    }
    LaunchedEffect(chromeVisible, window) {
        val controller = window?.let { WindowCompat.getInsetsController(it, view) } ?: return@LaunchedEffect
        if (chromeVisible) controller.show(WindowInsetsCompat.Type.systemBars()) else controller.hide(WindowInsetsCompat.Type.systemBars())
    }
}

fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

