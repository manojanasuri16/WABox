package com.wabox.app.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.SyncAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.wabox.app.WABoxApp
import com.wabox.app.ui.designsystem.AppKindBadge
import com.wabox.app.ui.designsystem.GroupedRow
import com.wabox.app.ui.designsystem.GroupedSection
import com.wabox.app.ui.designsystem.LargeTitle
import com.wabox.app.ui.designsystem.RowDivider
import com.wabox.app.ui.designsystem.RowIcon
import com.wabox.app.ui.designsystem.StackedBadges
import com.wabox.app.ui.designsystem.Wa
import com.wabox.app.ui.designsystem.WaButton
import com.wabox.app.ui.designsystem.WaSpace
import com.wabox.app.ui.designsystem.WaTheme
import com.wabox.app.ui.designsystem.isDarkTheme
import com.wabox.app.ui.home.folderSubtitle
import kotlinx.coroutines.launch

/** Per-widget source choice for the Recent Statuses widget. Shown at placement and on reconfigure (API 31+). */
class RecentWidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val widgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Backing out must not leave a half-configured widget.
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }

        val container = (application as WABoxApp).container
        val manager = GlanceAppWidgetManager(this)
        var current by mutableLongStateOf(FOLLOW_APP)
        lifecycleScope.launch {
            runCatching {
                val glanceId = manager.getGlanceIdBy(widgetId)
                current = getAppWidgetState(this@RecentWidgetConfigActivity, PreferencesGlanceStateDefinition, glanceId)[WidgetFolderKey] ?: FOLLOW_APP
            }
        }

        fun choose(folderId: Long) = lifecycleScope.launch {
            val glanceId = manager.getGlanceIdBy(widgetId)
            updateAppWidgetState(this@RecentWidgetConfigActivity, glanceId) { it[WidgetFolderKey] = folderId }
            RecentStatusesWidget().update(this@RecentWidgetConfigActivity, glanceId)
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
            finish()
        }

        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
            val folders by container.sourceRepository.folders.collectAsStateWithLifecycle(initialValue = emptyList())
            val dark = isDarkTheme(settings?.themeMode ?: com.wabox.app.core.model.ThemeMode.SYSTEM)
            WaTheme(dark) {
                val c = Wa.colors
                Column(
                    Modifier.fillMaxSize().background(c.groupedBackground).statusBarsPadding().navigationBarsPadding()
                        .verticalScroll(rememberScrollState()).padding(bottom = WaSpace.xxl)
                ) {
                    Spacer(Modifier.height(WaSpace.xl))
                    LargeTitle("Widget source", subtitle = "Choose what this widget shows")
                    GroupedSection {
                        GroupedRow(
                            title = "Follow WABox",
                            subtitle = "Matches the source selected in the app or the switcher widget",
                            leading = { RowIcon(Icons.Rounded.SyncAlt, c.accent) },
                            trailing = { if (current == FOLLOW_APP) Icon(Icons.Rounded.Check, "Selected", Modifier.size(22.dp), tint = c.accent) },
                            onClick = { choose(FOLLOW_APP) },
                        )
                        RowDivider()
                        GroupedRow(
                            title = "All sources",
                            leading = { StackedBadges(26.dp) },
                            trailing = { if (current == 0L) Icon(Icons.Rounded.Check, "Selected", Modifier.size(22.dp), tint = c.accent) },
                            onClick = { choose(0L) },
                        )
                    }
                    if (folders.isNotEmpty()) {
                        GroupedSection(header = "Status folders") {
                            folders.forEachIndexed { i, f ->
                                if (i > 0) RowDivider()
                                GroupedRow(
                                    title = f.label,
                                    subtitle = folderSubtitle(f),
                                    leading = { AppKindBadge(f.appKind, 30.dp) },
                                    trailing = { if (current == f.id) Icon(Icons.Rounded.Check, "Selected", Modifier.size(22.dp), tint = c.accent) },
                                    onClick = { choose(f.id) },
                                )
                            }
                        }
                    } else {
                        Text(
                            "No sources connected yet. The widget will show statuses as soon as you connect WhatsApp in WABox.",
                            style = Wa.type.footnote, color = c.secondaryLabel,
                            modifier = Modifier.padding(horizontal = WaSpace.gutter + WaSpace.lg, vertical = WaSpace.lg),
                        )
                    }
                    Spacer(Modifier.height(WaSpace.xl))
                    WaButton("Done", { choose(current) }, Modifier.padding(horizontal = WaSpace.gutter).fillMaxWidth())
                }
            }
        }
    }
}

