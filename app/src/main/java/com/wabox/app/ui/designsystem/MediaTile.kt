package com.wabox.app.ui.designsystem

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Precision
import coil3.video.videoFrameMillis
import com.wabox.app.core.model.AppKind
import com.wabox.app.core.util.Formats

enum class TileSaveState { Unsaved, Saving, Saved }

/**
 * Square media tile. Thumbnails are decoded at tile size by Coil (memory + disk cached);
 * video tiles get a frame at 1 s and a duration chip. The save control is a separate hit target.
 */
@Composable
fun MediaTile(
    uri: String,
    isVideo: Boolean,
    durationMs: Long?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    saveState: TileSaveState? = null,
    onSave: (() -> Unit)? = null,
    sourceKind: AppKind? = null,
    selected: Boolean? = null,
    contentDescription: String,
) {
    val c = Wa.colors
    val context = LocalContext.current
    var failed by remember(uri) { mutableStateOf(false) }
    val selectionScale by animateFloatAsState(if (selected == true) 0.9f else 1f, WaMotion.standard(), label = "selScale")
    Box(
        modifier
            .aspectRatio(1f)
            .pressable(pressedScale = 0.96f, onLongClick = onLongClick, onClick = onClick)
            .semantics {
                this.contentDescription = contentDescription
                if (saveState == TileSaveState.Saved) stateDescription = "Saved"
                if (selected == true) stateDescription = "Selected"
            }
    ) {
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer { scaleX = selectionScale; scaleY = selectionScale }
                .clip(RoundedCornerShape(WaRadius.tile))
                .background(c.fill)
        ) {
            if (failed) {
                Icon(Icons.Rounded.BrokenImage, null, Modifier.align(Alignment.Center).size(28.dp), tint = c.tertiaryLabel)
            } else {
                AsyncImage(
                    model = remember(uri, isVideo) {
                        ImageRequest.Builder(context)
                            .data(uri)
                            .precision(Precision.INEXACT)
                            .apply { if (isVideo) videoFrameMillis(1000) }
                            .build()
                    },
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    onError = { failed = true },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (isVideo) {
                Box(
                    Modifier.fillMaxWidth().height(40.dp).align(Alignment.BottomCenter)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f))))
                )
                Row(Modifier.align(Alignment.BottomStart).padding(start = 7.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.PlayArrow, null, Modifier.size(15.dp), tint = Color.White)
                    if (durationMs != null && durationMs > 0) {
                        Spacer(Modifier.width(1.dp))
                        Text(Formats.duration(durationMs), style = Wa.type.caption2, color = Color.White)
                    }
                }
            }
            if (sourceKind != null) {
                AppKindBadge(sourceKind, 16.dp, Modifier.align(Alignment.TopStart).padding(6.dp))
            }
        }
        if (saveState != null && onSave != null && selected == null) {
            SaveBadge(saveState, onSave, Modifier.align(Alignment.BottomEnd).padding(5.dp))
        }
        if (selected != null) {
            SelectionMark(selected, Modifier.align(Alignment.TopEnd).padding(7.dp))
        }
    }
}

@Composable
private fun SaveBadge(state: TileSaveState, onSave: () -> Unit, modifier: Modifier) {
    val c = Wa.colors
    val bg = if (state == TileSaveState.Saved) c.success else Color.Black.copy(alpha = 0.42f)
    Box(
        modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(bg)
            .pressable(enabled = state == TileSaveState.Unsaved, pressedScale = 0.86f, onClick = onSave)
            .semantics { contentDescription = when (state) { TileSaveState.Unsaved -> "Save"; TileSaveState.Saving -> "Saving"; TileSaveState.Saved -> "Saved" } },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = state,
            transitionSpec = { (fadeIn(WaMotion.snappy()) + scaleIn(WaMotion.momentum(), 0.6f)) togetherWith (fadeOut(WaMotion.snappy()) + scaleOut(WaMotion.snappy(), 0.6f)) },
            label = "saveBadge",
        ) { s ->
            when (s) {
                TileSaveState.Unsaved -> Icon(Icons.Rounded.Download, null, Modifier.size(17.dp), tint = Color.White)
                TileSaveState.Saving -> CircularProgressIndicator(Modifier.size(15.dp), color = Color.White, strokeWidth = 2.dp)
                TileSaveState.Saved -> Icon(Icons.Rounded.Check, null, Modifier.size(17.dp), tint = Color.White)
            }
        }
    }
}

@Composable
private fun SelectionMark(selected: Boolean, modifier: Modifier) {
    val c = Wa.colors
    Box(
        modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(if (selected) c.accent else Color.Black.copy(alpha = 0.2f))
            .border(1.5.dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Rounded.Check, null, Modifier.size(15.dp), tint = Color.White)
    }
}
