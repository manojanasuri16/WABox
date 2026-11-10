package com.wabox.app.ui.saved

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wabox.app.core.model.MediaFilter
import com.wabox.app.core.model.MediaKind
import com.wabox.app.core.model.SavedItem
import com.wabox.app.core.model.SortOrder
import com.wabox.app.core.util.Formats
import com.wabox.app.ui.LocalBottomBarHeight
import com.wabox.app.ui.designsystem.CompactTopBar
import com.wabox.app.ui.designsystem.EmptyState
import com.wabox.app.ui.designsystem.IconCircleButton
import com.wabox.app.ui.designsystem.LargeTitle
import com.wabox.app.ui.designsystem.MediaTile
import com.wabox.app.ui.designsystem.SegmentedControl
import com.wabox.app.ui.designsystem.Wa
import com.wabox.app.ui.designsystem.WaMotion
import com.wabox.app.ui.designsystem.WaSpace
import com.wabox.app.ui.designsystem.pressable
import com.wabox.app.ui.designsystem.rememberHaptics
import com.wabox.app.ui.statusBarTop
import com.wabox.app.ui.waViewModel

@Composable
fun SavedScreen(onOpen: (SavedItem, MediaFilter) -> Unit) {
    val vm = waViewModel { c, _ -> SavedViewModel(c) }
    val state by vm.state.collectAsStateWithLifecycle()
    val consent by vm.consent.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val c = Wa.colors
    val grid = rememberLazyGridState()
    val collapsed by remember { derivedStateOf { grid.firstVisibleItemIndex > 0 || grid.firstVisibleItemScrollOffset > 40 } }
    var sortMenu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val top = statusBarTop() + 48.dp

    val consentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        vm.onConsentResult(it.resultCode == Activity.RESULT_OK)
    }
    LaunchedEffect(consent) { consent?.let { consentLauncher.launch(IntentSenderRequest.Builder(it.first).build()) } }
    BackHandler(enabled = state.selecting) { vm.endSelection() }

    Box(Modifier.fillMaxSize().background(c.background)) {
        LazyVerticalGrid(
            state = grid,
            columns = GridCells.Adaptive(110.dp),
            contentPadding = PaddingValues(start = WaSpace.lg, end = WaSpace.lg, top = top, bottom = LocalBottomBarHeight.current + 96.dp),
            horizontalArrangement = Arrangement.spacedBy(WaSpace.gridGap),
            verticalArrangement = Arrangement.spacedBy(WaSpace.gridGap),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "title", span = { GridItemSpan(maxLineSpan) }) {
                LargeTitle(
                    "Saved",
                    horizontalPadding = WaSpace.xs,
                    subtitle = if (state.totalSaved > 0) "${state.totalSaved} item${if (state.totalSaved == 1) "" else "s"} · ${state.location.imagesPath}" else null,
                )
            }
            item(key = "controls", span = { GridItemSpan(maxLineSpan) }) {
                SegmentedControl(
                    options = listOf("All", "Photos", "Videos"),
                    selectedIndex = state.filter.ordinal,
                    onSelect = { vm.setFilter(MediaFilter.entries[it]) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = WaSpace.xs).padding(bottom = WaSpace.sm),
                )
            }
            if (state.loaded && state.items.isEmpty()) {
                item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(
                        icon = Icons.Rounded.SaveAlt,
                        title = if (state.filter == MediaFilter.ALL) "Nothing saved yet" else "No saved ${if (state.filter == MediaFilter.IMAGES) "photos" else "videos"}",
                        message = "Tap the save button on any status. Saved copies live in your gallery and stay after the status expires.",
                    )
                }
            }
            state.sections.forEach { section ->
                item(key = "sec_${section.title}", span = { GridItemSpan(maxLineSpan) }, contentType = "header") {
                    Text(
                        section.title, style = Wa.type.title3, color = c.label,
                        modifier = Modifier.padding(start = WaSpace.xs, top = WaSpace.md, bottom = WaSpace.xs),
                    )
                }
                items(section.items, key = { it.id }, contentType = { "tile" }) { item ->
                    val selected = state.selection?.contains(item.id)
                    MediaTile(
                        uri = item.mediaUri,
                        isVideo = item.kind == MediaKind.VIDEO,
                        durationMs = item.durationMs,
                        selected = selected,
                        onClick = {
                            if (state.selecting) { haptics.tick(); vm.toggle(item.id) } else onOpen(item, state.filter)
                        },
                        onLongClick = { if (!state.selecting) { haptics.confirm(); vm.startSelection(item.id) } },
                        contentDescription = "Saved ${if (item.kind == MediaKind.VIDEO) "video" else "photo"} from ${item.originLabel}, ${Formats.relative(item.savedAt)}",
                        modifier = Modifier.animateItem(fadeInSpec = null),
                    )
                }
            }
        }

        CompactTopBar(
            title = if (state.selecting) "${state.selection?.size ?: 0} selected" else "Saved",
            collapsed = collapsed || state.selecting,
            leading = {
                if (state.selecting) TextAction("Cancel") { vm.endSelection() }
            },
            trailing = {
                if (state.selecting) {
                    TextAction("Select all") { vm.selectAll() }
                } else if (state.items.isNotEmpty()) {
                    TextAction("Select") { vm.startSelection() }
                    Box {
                        IconCircleButton(Icons.Rounded.SwapVert, "Sort", { sortMenu = true }, tint = c.accent)
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }, containerColor = c.surface, shape = RoundedCornerShape(14.dp)) {
                            SortOrder.entries.forEach { order ->
                                DropdownMenuItem(
                                    text = { Text(if (order == SortOrder.NEWEST) "Newest first" else "Oldest first", style = Wa.type.body) },
                                    trailingIcon = { if (state.sort == order) Icon(Icons.Rounded.Check, null, tint = c.accent) },
                                    onClick = { vm.setSort(order); sortMenu = false },
                                )
                            }
                        }
                    }
                }
            },
        )

        AnimatedVisibility(
            visible = state.selecting,
            enter = fadeIn(WaMotion.standard()) + slideInVertically(WaMotion.standard()) { it },
            exit = fadeOut(WaMotion.standard()) + slideOutVertically(WaMotion.standard()) { it },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = LocalBottomBarHeight.current + WaSpace.md),
        ) {
            val count = state.selection?.size ?: 0
            Row(
                Modifier.padding(horizontal = WaSpace.gutter).fillMaxWidth()
                    .shadow(16.dp, RoundedCornerShape(20.dp), ambientColor = c.scrim, spotColor = c.scrim)
                    .clip(RoundedCornerShape(20.dp)).background(c.surface).padding(WaSpace.sm),
                horizontalArrangement = Arrangement.spacedBy(WaSpace.sm),
            ) {
                SelectionAction("Share", Icons.Rounded.IosShare, c.accent, count > 0, Modifier.weight(1f)) { vm.shareSelected(context) }
                SelectionAction("Delete", Icons.Rounded.Delete, c.destructive, count > 0, Modifier.weight(1f)) { confirmDelete = true }
            }
        }
    }

    if (confirmDelete) {
        val n = state.selection?.size ?: 0
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(if (n == 1) "Delete 1 item?" else "Delete $n items?") },
            text = { Text("Saved copies are removed from your device. This can't be undone.") },
            confirmButton = { TextButton({ confirmDelete = false; vm.deleteSelected() }) { Text("Delete", color = c.destructive, fontWeight = FontWeight.SemiBold) } },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancel") } },
            containerColor = c.surface,
        )
    }
}

@Composable
private fun TextAction(text: String, onClick: () -> Unit) {
    Text(
        text, style = Wa.type.body.copy(fontWeight = FontWeight.Medium), color = Wa.colors.accent,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).pressable(onClick = onClick).padding(horizontal = 6.dp, vertical = 6.dp),
    )
}

@Composable
private fun SelectionAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).pressable(enabled = enabled, onClick = onClick).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = tint.copy(alpha = if (enabled) 1f else 0.35f))
        Spacer(Modifier.height(3.dp))
        Text(label, style = Wa.type.caption, color = tint.copy(alpha = if (enabled) 1f else 0.35f))
    }
}

