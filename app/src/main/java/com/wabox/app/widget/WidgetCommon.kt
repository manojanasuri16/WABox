package com.wabox.app.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Box
import androidx.glance.layout.ContentScale
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.wabox.app.MainActivity
import com.wabox.app.R
import com.wabox.app.WABoxApp
import com.wabox.app.core.model.AppKind

/** Day/night colors come from resources (values / values-night), so widgets follow the system theme. */
object WColors {
    val bg = ColorProvider(R.color.widget_bg)
    val tile = ColorProvider(R.color.widget_tile)
    val label = ColorProvider(R.color.widget_label)
    val secondary = ColorProvider(R.color.widget_secondary)
    val accent = ColorProvider(R.color.widget_accent)
    val white = ColorProvider(androidx.compose.ui.graphics.Color.White)
}

object WText {
    val title = TextStyle(color = WColors.label, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    val body = TextStyle(color = WColors.label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    val secondary = TextStyle(color = WColors.secondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    val caption = TextStyle(color = WColors.secondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
}

/** Rounded widget container: system radius on API 31+, a rounded drawable below. */
fun GlanceModifier.widgetSurface(): GlanceModifier =
    if (Build.VERSION.SDK_INT >= 31) {
        appWidgetBackground().background(WColors.bg).cornerRadius(android.R.dimen.system_app_widget_background_radius)
    } else {
        appWidgetBackground().background(ImageProvider(R.drawable.widget_bg))
    }

fun GlanceModifier.roundedFill(drawable: Int, radius: androidx.compose.ui.unit.Dp, color: ColorProvider): GlanceModifier =
    if (Build.VERSION.SDK_INT >= 31) background(color).cornerRadius(radius) else background(ImageProvider(drawable))

@Composable
fun KindBadge(kind: AppKind?, size: androidx.compose.ui.unit.Dp = 20.dp) {
    val (bg, glyph) = when (kind) {
        AppKind.WHATSAPP -> R.drawable.widget_kind_whatsapp to R.drawable.ic_w_chat
        AppKind.BUSINESS -> R.drawable.widget_kind_business to R.drawable.ic_w_business
        AppKind.OTHER -> R.drawable.widget_kind_other to R.drawable.ic_w_folder
        null -> R.drawable.widget_kind_other to R.drawable.ic_w_layers
    }
    Box(GlanceModifier.size(size).background(ImageProvider(bg)), contentAlignment = androidx.glance.layout.Alignment.Center) {
        Image(
            ImageProvider(glyph), null,
            GlanceModifier.size(size * 0.6f),
            colorFilter = ColorFilter.tint(WColors.white),
        )
    }
}

@Composable
fun Glyph(res: Int, size: androidx.compose.ui.unit.Dp, tint: ColorProvider, modifier: GlanceModifier = GlanceModifier) {
    Image(ImageProvider(res), null, modifier.size(size), colorFilter = ColorFilter.tint(tint), contentScale = ContentScale.Fit)
}

fun openAppIntent(context: Context): Intent =
    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

fun openStatusIntent(context: Context, statusId: String): Intent =
    Intent(context, MainActivity::class.java)
        .setData(Uri.parse("wabox://status/$statusId")) // distinct data → distinct PendingIntent per tile
        .putExtra(MainActivity.EXTRA_STATUS_ID, statusId)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

val Context.container get() = (applicationContext as WABoxApp).container

/** Per-widget preference: which folder the Recent widget shows. -1 = follow the app's selection. */
val WidgetFolderKey = longPreferencesKey("widget_folder")
const val FOLLOW_APP = -1L

val StatusIdParam = ActionParameters.Key<String>("status_id")
val FolderIdParam = ActionParameters.Key<Long>("folder_id")

/** Widget quick save: hand off to WorkManager (a copy can outlive a broadcast's time budget). */
class SaveStatusAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[StatusIdParam] ?: return
        context.container.scheduler.saveNow(id)
    }
}

class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val c = context.container
        c.settings.setWidgetRefreshing(true)
        WidgetUpdater.updateAll(context)
        c.scheduler.refreshNow()
    }
}

class SelectSourceAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[FolderIdParam] ?: return
        context.container.settings.setActiveFolder(id)
        WidgetUpdater.updateAll(context)
    }
}

fun saveParams(id: String) = actionParametersOf(StatusIdParam to id)
fun folderParams(id: Long) = actionParametersOf(FolderIdParam to id)

object WidgetUpdater {
    /** Called after every discovery, save, delete, rename and source switch. Cheap when no widgets are placed. */
    suspend fun updateAll(context: Context) {
        RecentStatusesWidget().updateAll(context)
        SourcesWidget().updateAll(context)
        QuickAccessWidget().updateAll(context)
    }
}

