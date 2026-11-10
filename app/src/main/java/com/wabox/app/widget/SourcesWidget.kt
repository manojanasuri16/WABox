package com.wabox.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
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
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.Text
import com.wabox.app.R
import com.wabox.app.core.model.AppKind
import com.wabox.app.core.model.SourceHealth

class SourcesReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = SourcesWidget()
}

/**
 * Account switcher. Lists every distinguishable status folder; tapping one sets the app-wide
 * active source, which Home and "follow app" Recent widgets pick up immediately.
 */
class SourcesWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(DpSize(110.dp, 110.dp), DpSize(180.dp, 110.dp), DpSize(250.dp, 180.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { Content() }
    }

    private data class Entry(val id: Long, val label: String, val kind: AppKind?, val detail: String)

    @Composable
    private fun Content() {
        val context = LocalContext.current
        val c = context.container
        val folders by c.sourceRepository.folders.collectAsState(initial = null)
        val active by c.settings.activeFolderId.collectAsState(initial = 0L)
        val size = LocalSize.current
        val compact = size.width < 170.dp

        Column(GlanceModifier.fillMaxSize().widgetSurface().padding(10.dp)) {
            Row(
                GlanceModifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 6.dp).clickable(actionStartActivity(openAppIntent(context))),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Sources", style = WText.title, modifier = GlanceModifier.defaultWeight())
                Glyph(R.drawable.ic_w_chevron, 18.dp, WColors.secondary)
            }
            val list = folders
            if (list == null) return@Column
            if (list.isEmpty()) {
                Box(GlanceModifier.fillMaxSize().clickable(actionStartActivity(openAppIntent(context))), contentAlignment = Alignment.Center) {
                    Text("Open WABox to connect WhatsApp", style = WText.secondary, maxLines = 2)
                }
                return@Column
            }
            val entries = buildList {
                if (list.size > 1) add(Entry(0, "All sources", null, "${list.sumOf { it.statusCount }}"))
                list.forEach {
                    add(Entry(it.id, it.label, it.appKind, if (it.sourceHealth == SourceHealth.ACCESS_LOST) "!" else "${it.statusCount}"))
                }
            }
            val validActive = if (list.any { it.id == active }) active else 0L
            val rowHeight = 38.dp
            val maxRows = ((size.height - 50.dp) / rowHeight).toInt().coerceAtLeast(1)
            entries.take(maxRows).forEachIndexed { i, e ->
                if (i > 0) Spacer(GlanceModifier.height(2.dp))
                val selected = e.id == validActive || (list.size == 1 && e.id == list.first().id)
                Row(
                    GlanceModifier.fillMaxWidth().height(rowHeight)
                        .let { if (selected) it.roundedFill(R.drawable.widget_selected_bg, 12.dp, androidx.glance.unit.ColorProvider(R.color.widget_selected)) else it }
                        .padding(horizontal = 8.dp)
                        .clickable(actionRunCallback<SelectSourceAction>(folderParams(e.id)))
                        .semantics { contentDescription = if (selected) "${e.label}, selected" else "Switch to ${e.label}" },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    KindBadge(e.kind, 22.dp)
                    Spacer(GlanceModifier.width(10.dp))
                    Text(e.label, style = if (selected) WText.body.copy(color = WColors.accent) else WText.body, maxLines = 1, modifier = GlanceModifier.defaultWeight())
                    if (!compact) Text(e.detail, style = WText.secondary)
                    if (selected) {
                        Spacer(GlanceModifier.width(6.dp))
                        Glyph(R.drawable.ic_w_check, 16.dp, WColors.accent)
                    }
                }
            }
        }
    }
}
