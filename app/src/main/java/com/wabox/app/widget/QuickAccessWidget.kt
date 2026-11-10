package com.wabox.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.CircularProgressIndicator
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
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
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import com.wabox.app.R
import com.wabox.app.core.util.Formats
import com.wabox.app.data.db.StatusCounts
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged

class QuickAccessReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = QuickAccessWidget()
}

/** Compact counts + manual refresh. Counts follow the app's active source. */
class QuickAccessWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(DpSize(110.dp, 50.dp), DpSize(110.dp, 110.dp), DpSize(250.dp, 110.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { Content() }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Composable
    private fun Content() {
        val context = LocalContext.current
        val c = context.container
        val settings by c.settings.settings.collectAsState(initial = null)
        val folders by c.sourceRepository.folders.collectAsState(initial = null)
        val counts by remember {
            c.settings.settings.map { it.activeFolderId to it.lastSeenAt }.distinctUntilChanged()
                .flatMapLatest { (folder, seen) -> c.statusRepository.observeCounts(folder, if (seen == 0L) Long.MAX_VALUE else seen) }
        }.collectAsState(initial = null)
        val s = settings
        val size = LocalSize.current
        val active = folders?.firstOrNull { it.id == s?.activeFolderId }
        val label = active?.label ?: "All sources"
        val refreshing = s?.widgetRefreshing == true
        val n = counts ?: StatusCounts(0, 0, 0)

        Box(GlanceModifier.fillMaxSize().widgetSurface().clickable(actionStartActivity(openAppIntent(context)))) {
            if (size.height < 100.dp) {
                Row(GlanceModifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${n.total}", style = WText.title.copy(fontSize = 24.sp))
                    Spacer(GlanceModifier.width(6.dp))
                    Column(GlanceModifier.defaultWeight()) {
                        Text("statuses", style = WText.secondary, maxLines = 1)
                        if (n.fresh > 0) Text("${n.fresh} new", style = WText.caption.copy(color = WColors.accent), maxLines = 1)
                    }
                    RefreshGlyph(refreshing)
                }
            } else {
                Column(GlanceModifier.fillMaxSize().padding(14.dp)) {
                    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        KindBadge(active?.appKind, 18.dp)
                        Spacer(GlanceModifier.width(6.dp))
                        Text(label, style = WText.secondary.copy(fontWeight = FontWeight.Bold), maxLines = 1, modifier = GlanceModifier.defaultWeight())
                        RefreshGlyph(refreshing)
                    }
                    Spacer(GlanceModifier.defaultWeight())
                    Text("${n.total}", style = WText.title.copy(fontSize = 38.sp))
                    Text(
                        when {
                            folders?.isEmpty() == true -> "Tap to connect"
                            n.fresh > 0 -> "statuses · ${n.fresh} new"
                            else -> "statuses"
                        },
                        style = if (n.fresh > 0) WText.secondary.copy(color = WColors.accent) else WText.secondary, maxLines = 1,
                    )
                    if (size.width >= 200.dp) {
                        Spacer(GlanceModifier.height(4.dp))
                        Text(
                            "${n.total - n.videos} photos · ${n.videos} videos" + (s?.lastRefreshAt?.takeIf { it > 0 }?.let { " · updated ${Formats.relative(it).lowercase()}" } ?: ""),
                            style = WText.caption, maxLines = 1,
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun RefreshGlyph(refreshing: Boolean) {
        Box(
            GlanceModifier.size(30.dp).clickable(actionRunCallback<RefreshAction>()).semantics { contentDescription = if (refreshing) "Refreshing" else "Refresh" },
            contentAlignment = Alignment.Center,
        ) {
            if (refreshing) CircularProgressIndicator(GlanceModifier.size(18.dp), color = WColors.accent)
            else Glyph(R.drawable.ic_w_refresh, 20.dp, WColors.accent)
        }
    }
}

