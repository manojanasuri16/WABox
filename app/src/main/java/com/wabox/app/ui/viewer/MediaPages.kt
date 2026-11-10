package com.wabox.app.ui.viewer

import android.view.ViewGroup
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.HideImage
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.video.videoFrameMillis
import com.wabox.app.ui.designsystem.Wa
import com.wabox.app.ui.designsystem.WaMotion
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * Pinch / double-tap zoomable image. Single-finger drags pass through to the pager and the
 * dismiss gesture unless the image is zoomed, so swiping between statuses never fights zoom.
 */
@Composable
fun ZoomableImage(
    uri: String,
    onTap: () -> Unit,
    onZoomChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scale = remember { Animatable(1f) }
    val offset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var failed by remember(uri) { mutableStateOf(false) }

    fun clamp(o: Offset, s: Float): Offset {
        val maxX = max(0f, (size.width * s - size.width) / 2f)
        val maxY = max(0f, (size.height * s - size.height) / 2f)
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }

    LaunchedEffect(scale.value > 1.01f) { onZoomChanged(scale.value > 1.01f) }

    if (failed) {
        MissingMedia(isVideo = false, modifier)
        return
    }

    Box(
        modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .pointerInput(uri) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { tap ->
                        scope.launch {
                            if (scale.value > 1.01f) {
                                launch { scale.animateTo(1f, WaMotion.standard()) }
                                offset.animateTo(Offset.Zero, WaMotion.standard())
                            } else {
                                val target = 2.5f
                                val center = Offset(size.width / 2f, size.height / 2f)
                                val dest = clamp((center - tap) * (target - 1f), target)
                                launch { scale.animateTo(target, WaMotion.standard()) }
                                offset.animateTo(dest, WaMotion.standard())
                            }
                        }
                    },
                )
            }
            .pointerInput(uri) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val multi = event.changes.count { it.pressed } > 1
                        if (multi || scale.value > 1.01f) {
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            val newScale = (scale.value * zoom).coerceIn(0.85f, 5f)
                            scope.launch {
                                scale.snapTo(newScale)
                                offset.snapTo(clamp(offset.value + pan, newScale))
                            }
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                    if (scale.value < 1f) scope.launch {
                        launch { scale.animateTo(1f, WaMotion.standard()) }
                        offset.animateTo(Offset.Zero, WaMotion.standard())
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = remember(uri) { ImageRequest.Builder(context).data(uri).build() },
            contentDescription = null,
            contentScale = ContentScale.Fit,
            onError = { failed = true },
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale.value; scaleY = scale.value
                translationX = offset.value.x; translationY = offset.value.y
            },
        )
    }
}

/**
 * One shared ExoPlayer is attached only to the settled page; neighbours show a still frame.
 * Media3 streams the content:// URI, so large files are never loaded into memory.
 */
@Composable
fun VideoPage(
    uri: String,
    active: Boolean,
    player: ExoPlayer,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var firstFrame by remember(uri, active) { mutableStateOf(false) }
    var error by remember(uri) { mutableStateOf(false) }

    if (active) {
        DisposableEffect(uri) {
            val listener = object : Player.Listener {
                override fun onRenderedFirstFrame() { firstFrame = true }
                override fun onPlayerError(e: PlaybackException) { error = true }
            }
            player.addListener(listener)
            player.setMediaItem(MediaItem.fromUri(uri))
            player.prepare()
            player.playWhenReady = true
            onDispose {
                player.removeListener(listener)
                player.stop()
                player.clearMediaItems()
            }
        }
    }

    Box(modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) }, contentAlignment = Alignment.Center) {
        if (error) {
            MissingMedia(isVideo = true)
            return@Box
        }
        if (active) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                        setKeepContentOnPlayerReset(true)
                        this.player = player
                    }
                },
                update = { it.player = player },
                onRelease = { it.player = null },
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (!active || !firstFrame) {
            AsyncImage(
                model = remember(uri) { ImageRequest.Builder(context).data(uri).videoFrameMillis(0).build() },
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun MissingMedia(isVideo: Boolean, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
        Icon(if (isVideo) Icons.Rounded.VideocamOff else Icons.Rounded.HideImage, null, Modifier.size(40.dp), tint = Color.White.copy(alpha = 0.6f))
        Spacer(Modifier.height(12.dp))
        Text("No longer available", style = Wa.type.headline, color = Color.White)
        Spacer(Modifier.height(4.dp))
        Text(
            "WhatsApp removed this ${if (isVideo) "video" else "photo"} from the device,\nor it can't be opened.",
            style = Wa.type.footnote, color = Color.White.copy(alpha = 0.6f), textAlign = TextAlign.Center,
        )
    }
}
