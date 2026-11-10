package com.wabox.app.ui.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wabox.app.core.model.AppKind
import com.wabox.app.core.model.MediaFilter
import com.wabox.app.core.model.MediaKind
import com.wabox.app.core.model.SaveResult
import com.wabox.app.core.model.SourceHealth
import com.wabox.app.core.model.StatusItem
import com.wabox.app.core.util.Formats
import com.wabox.app.ui.LocalAppContainer
import com.wabox.app.ui.LocalBottomBarHeight
import com.wabox.app.ui.appName
import com.wabox.app.ui.designsystem.ButtonStyle
import com.wabox.app.ui.designsystem.CompactTopBar
import com.wabox.app.ui.designsystem.EmptyState
import com.wabox.app.ui.designsystem.IconCircleButton
import com.wabox.app.ui.designsystem.InlineBanner
import com.wabox.app.ui.designsystem.LargeTitle
import com.wabox.app.ui.designsystem.MediaTile
import com.wabox.app.ui.designsystem.SegmentedControl
import com.wabox.app.ui.designsystem.SourcePill
import com.wabox.app.ui.designsystem.TileSaveState
import com.wabox.app.ui.designsystem.Wa
import com.wabox.app.ui.designsystem.WaButton
import com.wabox.app.ui.designsystem.WaRadius
import com.wabox.app.ui.designsystem.WaSpace
import com.wabox.app.ui.designsystem.rememberHaptics
import com.wabox.app.ui.rememberAppLauncher
import com.wabox.app.ui.rememberSourcePicker
import com.wabox.app.ui.statusBarTop
import com.wabox.app.ui.waViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenStatus: (StatusItem, folderId: Long, filter: MediaFilter) -> Unit,
    onManageSources: () -> Unit,
) {
    val vm = waViewModel { c, _ -> HomeViewModel(c) }
    val state by vm.state.collectAsStateWithLifecycle()
    val container = LocalAppContainer.current
    val picker = rememberSourcePicker()
    val launchApp = rememberAppLauncher()
    val haptics = rememberHaptics()
    val c = Wa.colors
    var showSheet by rememberSaveable { mutableStateOf(false) }
    val grid = rememberLazyGridState()
    val collapsed by remember { derivedStateOf { grid.firstVisibleItemIndex > 0 || grid.firstVisibleItemScrollOffset > 40 } }
    val top = statusBarTop() + 48.dp
    val pullState = rememberPullToRefreshState()

    Box(Modifier.fillMaxSize().background(c.background)) {
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = vm::refresh,
            state = pullState,
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pullState, isRefreshing = state.refreshing,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = top),
                    containerColor = c.surface, color = c.accent,
                )
            },
        ) {
            LazyVerticalGrid(
                state = grid,
                columns = GridCells.Adaptive(minSize = 110.dp),
                contentPadding = PaddingValues(
                    start = WaSpace.lg, end = WaSpace.lg,
                    top = top, bottom = LocalBottomBarHeight.current + WaSpace.xxl,
                ),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(WaSpace.gridGap),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(WaSpace.gridGap),
                modifier = Modifier.fillMaxSize(),
            ) {
                fullWidth("title") {
                    LargeTitle("Statuses", horizontalPadding = WaSpace.xs)
                }
                fullWidth("controls") {
                    Column(Modifier.padding(horizontal = WaSpace.xs).padding(bottom = WaSpace.md)) {
                        SourcePill(
                            label = state.activeFolder?.label ?: "All sources",
                            kind = state.activeFolder?.appKind,
                            onClick = { showSheet = true },
                            trailingIcon = Icons.Rounded.ExpandMore,
                        )
                        Spacer(Modifier.height(WaSpace.md))
                        SegmentedControl(
                            options = listOf("All", "Photos", "Videos"),
                            selectedIndex = state.filter.ordinal,
                            onSelect = { vm.setFilter(MediaFilter.entries[it]) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                state.problems.forEach { source ->
                    fullWidth("problem_${source.id}") {
                        val title = appName(source.appKind)
                        Box(Modifier.padding(horizontal = WaSpace.xs).padding(bottom = WaSpace.md)) {
                            when (source.health) {
                                SourceHealth.ACCESS_LOST -> InlineBanner(
                                    title = "$title: access lost",
                                    message = "The folder was moved, deleted, or permission was revoked.",
                                    actionText = "Reconnect", onAction = { picker.reconnect(source) },
                                    tint = c.destructive,
                                )
                                else -> InlineBanner(
                                    title = "$title: no statuses folder yet",
                                    message = "Open ${title}, view a status, then pull to refresh.",
                                    actionText = if (source.appKind != AppKind.OTHER) "Open" else null,
                                    onAction = { launchApp(source.appKind) },
                                )
                            }
                        }
                    }
                }

                when {
                    !state.loaded -> skeleton()
                    !state.hasSources -> fullWidth("empty") {
                        EmptyState(
                            icon = Icons.Rounded.FolderOpen,
                            title = "Connect WhatsApp",
                            message = "WABox needs your permission to read the folder where WhatsApp keeps the statuses you've viewed.",
                        ) {
                            WaButton("Connect WhatsApp", { picker.connect(AppKind.WHATSAPP) })
                            if (container.grants.isInstalled(AppKind.BUSINESS)) {
                                WaButton("Connect WhatsApp Business", { picker.connect(AppKind.BUSINESS) }, style = ButtonStyle.Plain)
                            }
                        }
                    }
                    state.isEmpty -> fullWidth("empty") {
                        val filtered = state.filter != MediaFilter.ALL
                        EmptyState(
                            icon = if (filtered) Icons.Rounded.PhotoLibrary else Icons.Rounded.HourglassEmpty,
                            title = when (state.filter) {
                                MediaFilter.ALL -> "No statuses yet"
                                MediaFilter.IMAGES -> "No photos"
                                MediaFilter.VIDEOS -> "No videos"
                            },
                            message = if (filtered) "Nothing of this type in ${state.activeFolder?.label ?: "your sources"} right now."
                            else "Statuses appear here after you view them in WhatsApp. They usually expire after 24 hours, so save the ones you want to keep.",
                        ) {
                            if (!filtered) {
                                val kind = state.activeFolder?.appKind?.takeIf { it != AppKind.OTHER } ?: AppKind.WHATSAPP
                                WaButton("Open ${appName(kind)}", { launchApp(kind) }, style = ButtonStyle.Tinted)
                            }
                        }
                    }
                    else -> {
                        if (state.fresh.isNotEmpty()) {
                            header("h_new", "New", state.fresh.size)
                            tiles(state.fresh, state, onOpenStatus) { item -> vm.save(item) { if (it is SaveResult.Saved) haptics.confirm() else if (it !is SaveResult.AlreadySaved) haptics.reject() } }
                            header("h_earlier", "Earlier", state.earlier.size)
                        } else {
                            header("h_recent", "Recent", state.earlier.size)
                        }
                        tiles(state.earlier, state, onOpenStatus) { item -> vm.save(item) { if (it is SaveResult.Saved) haptics.confirm() else if (it !is SaveResult.AlreadySaved) haptics.reject() } }
                    }
                }
            }
        }

        CompactTopBar(
            title = state.activeFolder?.label ?: "Statuses",
            collapsed = collapsed,
            trailing = {
                RefreshButton(state.refreshing, enabled = state.hasSources, onClick = vm::refresh)
            },
        )
    }

    if (showSheet) {
        SourceSheet(
            folders = state.folders,
            sources = state.sources,
            activeFolderId = state.activeFolderId,
            installed = container.grants::isInstalled,
            onSelect = { vm.selectFolder(it); showSheet = false },
            onConnect = { picker.connect(it); showSheet = false },
            onManage = { showSheet = false; onManageSources() },
            onDismiss = { showSheet = false },
        )
    }
}

@Composable
private fun RefreshButton(refreshing: Boolean, enabled: Boolean, onClick: () -> Unit) {
    // The infinite transition only exists while refreshing, so an idle Home renders no frames.
    val rotation = if (refreshing) {
        val transition = rememberInfiniteTransition(label = "spin")
        transition.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "angle").value
    } else 0f
    IconCircleButton(
        icon = Icons.Rounded.Refresh,
        contentDescription = if (refreshing) "Refreshing" else "Refresh",
        onClick = onClick,
        enabled = enabled && !refreshing,
        tint = Wa.colors.accent,
        modifier = Modifier.graphicsLayer { rotationZ = rotation },
    )
}

private fun LazyGridScope.fullWidth(key: String, content: @Composable () -> Unit) =
    item(key = key, span = { GridItemSpan(maxLineSpan) }, contentType = "full") { content() }

private fun LazyGridScope.header(key: String, title: String, count: Int) = fullWidth(key) {
    Row(Modifier.padding(start = WaSpace.xs, end = WaSpace.xs, top = WaSpace.md, bottom = WaSpace.xs), verticalAlignment = Alignment.Bottom) {
        Text(title, style = Wa.type.title3, color = Wa.colors.label)
        Spacer(Modifier.padding(start = WaSpace.sm))
        Text("$count", style = Wa.type.subhead.copy(fontWeight = FontWeight.Medium), color = Wa.colors.secondaryLabel, modifier = Modifier.padding(bottom = 1.dp))
    }
}

private fun LazyGridScope.tiles(
    items: List<StatusItem>,
    state: HomeUiState,
    onOpen: (StatusItem, Long, MediaFilter) -> Unit,
    onSave: (StatusItem) -> Unit,
) = items(items, key = { it.id }, contentType = { "tile" }) { item ->
    MediaTile(
        uri = item.documentUri,
        isVideo = item.kind == MediaKind.VIDEO,
        durationMs = item.durationMs,
        onClick = { onOpen(item, state.activeFolderId, state.filter) },
        saveState = when {
            item.isSaved -> TileSaveState.Saved
            item.id in state.saving -> TileSaveState.Saving
            else -> TileSaveState.Unsaved
        },
        onSave = { onSave(item) },
        sourceKind = if (state.showSourceBadges) item.appKind else null,
        contentDescription = "${if (item.kind == MediaKind.VIDEO) "Video" else "Photo"} from ${item.folderLabel}, ${Formats.relative(item.lastModified)}",
        modifier = Modifier.animateItem(),
    )
}

private fun LazyGridScope.skeleton() = items(9, key = { "sk$it" }) {
    Box(Modifier.aspectRatio(1f).clip(RoundedCornerShape(WaRadius.tile)).background(Wa.colors.fill))
}
