package com.wabox.app.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.CircularProgressIndicator
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.Text
import com.wabox.app.R
import com.wabox.app.core.model.MediaKind
import com.wabox.app.core.model.StatusFolder
import com.wabox.app.core.model.StatusItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlin.math.floor

class RecentStatusesReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = RecentStatusesWidget()
}

/**
 * Newest statuses for one source. Reads Room + the widget thumbnail cache only, never SAF,
 * so rendering is cheap and works while the app process is cold.
 */
class RecentStatusesWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { Content() }
    }

    @Composable
    private fun Content() {
        val context = LocalContext.current
        val c = context.container
        val prefs = currentState<Preferences>()
        val configured = prefs[WidgetFolderKey] ?: FOLLOW_APP
        val active by c.settings.activeFolderId.collectAsState(initial = 0L)
        val folders by c.sourceRepository.folders.collectAsState(initial = null)
        val saving by c.savedRepository.saving.collectAsState()
        val appLock by remember { c.settings.settings.map { it.appLock } }.collectAsState(initial = false)
        val requested = if (configured == FOLLOW_APP) active else configured
        val folder = folders?.firstOrNull { it.id == requested }
        val folderId = folder?.id ?: 0L

        val size = LocalSize.current
        val gap = 6.dp
        val padding = 12.dp
        // Fit ~72 dp tiles to the real widget size (SizeMode.Exact), in both directions.
        val innerW = size.width - padding * 2
        val innerH = (size.height - padding * 2 - 30.dp).coerceAtLeast(40.dp)
        var columns = floor(((innerW + gap) / (72.dp + gap))).toInt().coerceIn(2, 6)
        var tile = (innerW - gap * (columns - 1)) / columns
        if (tile > innerH) {
            tile = innerH
            columns = floor(((innerW + gap) / (tile + gap))).toInt().coerceIn(1, 8)
        }
        val rows = floor(((innerH + gap) / (tile + gap))).toInt().coerceIn(1, 4)
        val limit = columns * rows

        val tiles by remember(folderId, limit) {
            c.statusRepository.observeLatest(folderId, limit)
                .map { list -> list.map { it to c.thumbnails.load(it.id) } }
                .flowOn(Dispatchers.IO)
        }.collectAsState(initial = null)

        Column(GlanceModifier.fillMaxSize().widgetSurface().padding(padding)) {
            Header(folder, folders, total = tiles?.size)
            Spacer(GlanceModifier.height(8.dp))
            val list = tiles
            when {
                folders == null || list == null -> Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = WColors.accent)
                }
                appLock -> Message("Protected by App Lock", "Open WABox to view your statuses.")
                folders.orEmpty().isEmpty() -> Message("Connect WhatsApp", "Open WABox to grant folder access.")
                list.isEmpty() -> Message("No statuses yet", "View statuses in WhatsApp, then refresh.", showRefresh = true)
                else -> Grid(list, columns, rows, tile, gap, saving)
            }
        }
    }

    @Composable
    private fun Header(folder: StatusFolder?, folders: List<StatusFolder>?, total: Int?) {
        val context = LocalContext.current
        Row(
            GlanceModifier.fillMaxWidth().height(22.dp).clickable(actionStartActivity(openAppIntent(context))),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KindBadge(folder?.appKind, 20.dp)
            Spacer(GlanceModifier.width(8.dp))
            Text(folder?.label ?: if ((folders?.size ?: 0) > 1) "All sources" else folders?.firstOrNull()?.label ?: "WABox", style = WText.title, maxLines = 1, modifier = GlanceModifier.defaultWeight())
            Glyph(R.drawable.ic_w_refresh, 18.dp, WColors.accent, GlanceModifier.clickable(actionRunCallback<RefreshAction>()).semantics { contentDescription = "Refresh" })
        }
    }

    @Composable
    private fun Grid(items: List<Pair<StatusItem, Bitmap?>>, columns: Int, rows: Int, tile: androidx.compose.ui.unit.Dp, gap: androidx.compose.ui.unit.Dp, saving: Set<String>) {
        Column {
            items.chunked(columns).take(rows).forEachIndexed { r, row ->
                if (r > 0) Spacer(GlanceModifier.height(gap))
                Row {
                    row.forEachIndexed { i, (item, bmp) ->
                        if (i > 0) Spacer(GlanceModifier.width(gap))
                        Tile(item, bmp, tile, item.id in saving)
                    }
                }
            }
        }
    }

    @Composable
    private fun Tile(item: StatusItem, bitmap: Bitmap?, size: androidx.compose.ui.unit.Dp, saving: Boolean) {
        val context = LocalContext.current
        val isVideo = item.kind == MediaKind.VIDEO
        Box(
            GlanceModifier.size(size).roundedFill(R.drawable.widget_tile_bg, 12.dp, WColors.tile)
                .clickable(actionStartActivity(openStatusIntent(context, item.id)))
                .semantics { contentDescription = "${if (isVideo) "Video" else "Photo"} from ${item.folderLabel}" },
        ) {
            if (bitmap != null) {
                Image(ImageProvider(bitmap), null, GlanceModifier.fillMaxSize().cornerRadius(12.dp), contentScale = ContentScale.Crop)
            } else {
                Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Glyph(if (isVideo) R.drawable.ic_w_video else R.drawable.ic_w_image, 22.dp, WColors.secondary)
                }
            }
            if (isVideo) {
                Box(GlanceModifier.fillMaxSize().padding(5.dp), contentAlignment = Alignment.BottomStart) {
                    Box(GlanceModifier.size(20.dp).background(ImageProvider(R.drawable.widget_badge_dark)), contentAlignment = Alignment.Center) {
                        Glyph(R.drawable.ic_w_play, 13.dp, WColors.white)
                    }
                }
            }
            Box(GlanceModifier.fillMaxSize().padding(4.dp), contentAlignment = Alignment.BottomEnd) {
                when {
                    item.isSaved -> Box(GlanceModifier.size(22.dp).background(ImageProvider(R.drawable.widget_badge_saved)), contentAlignment = Alignment.Center) {
                        Glyph(R.drawable.ic_w_check, 14.dp, WColors.white)
                    }
                    saving -> Box(GlanceModifier.size(22.dp).background(ImageProvider(R.drawable.widget_badge_dark)), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(GlanceModifier.size(14.dp), color = WColors.white)
                    }
                    size >= 44.dp -> Box(
                        GlanceModifier.size(26.dp).background(ImageProvider(R.drawable.widget_badge_dark))
                            .clickable(actionRunCallback<SaveStatusAction>(saveParams(item.id)))
                            .semantics { contentDescription = "Save" },
                        contentAlignment = Alignment.Center,
                    ) { Glyph(R.drawable.ic_w_download, 15.dp, WColors.white) }
                }
            }
        }
    }

    @Composable
    private fun Message(title: String, body: String, showRefresh: Boolean = false) {
        val context = LocalContext.current
        Column(
            GlanceModifier.fillMaxSize().clickable(actionStartActivity(openAppIntent(context))),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, style = WText.body, maxLines = 1)
            Spacer(GlanceModifier.height(2.dp))
            Text(body, style = WText.caption, maxLines = 2)
            if (showRefresh) {
                Spacer(GlanceModifier.height(8.dp))
                Row(
                    GlanceModifier.roundedFill(R.drawable.widget_pill_bg, 16.dp, WColors.tile).padding(horizontal = 12.dp, vertical = 6.dp)
                        .clickable(actionRunCallback<RefreshAction>()),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Glyph(R.drawable.ic_w_refresh, 14.dp, WColors.accent)
                    Spacer(GlanceModifier.width(6.dp))
                    Text("Refresh", style = WText.secondary.copy(color = WColors.accent))
                }
            }
        }
    }
}
