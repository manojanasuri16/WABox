package com.wabox.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wabox.app.core.model.AppKind
import com.wabox.app.core.model.Source
import com.wabox.app.core.model.SourceHealth
import com.wabox.app.core.model.StatusFolder
import com.wabox.app.ui.appName
import com.wabox.app.ui.designsystem.AppKindBadge
import com.wabox.app.ui.designsystem.GroupedRow
import com.wabox.app.ui.designsystem.RowDivider
import com.wabox.app.ui.designsystem.StackedBadges
import com.wabox.app.ui.designsystem.Wa
import com.wabox.app.ui.designsystem.WaRadius
import com.wabox.app.ui.designsystem.WaSpace
import com.wabox.app.ui.designsystem.pressable

fun folderSubtitle(folder: StatusFolder): String = when (folder.sourceHealth) {
    SourceHealth.ACCESS_LOST -> "Access lost · reconnect in Settings"
    SourceHealth.NO_STATUS_FOLDER -> "No statuses folder yet"
    SourceHealth.OK -> when (folder.statusCount) {
        0 -> "No statuses"
        1 -> "1 status"
        else -> "${folder.statusCount} statuses"
    } + if (folder.videoCount > 0) " · ${folder.videoCount} video${if (folder.videoCount == 1) "" else "s"}" else ""
}

/** Account/source switcher. Rises from the bottom, dismisses down. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceSheet(
    folders: List<StatusFolder>,
    sources: List<Source>,
    activeFolderId: Long,
    installed: (AppKind) -> Boolean,
    onSelect: (Long) -> Unit,
    onConnect: (AppKind) -> Unit,
    onManage: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = Wa.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = c.groupedBackground.takeIf { !c.isDark } ?: c.surface,
        shape = RoundedCornerShape(topStart = WaRadius.sheet, topEnd = WaRadius.sheet),
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = WaSpace.lg)) {
            Row(Modifier.padding(horizontal = WaSpace.gutter), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Sources", style = Wa.type.title2, color = c.label)
                    Text("Choose what Home and widgets show", style = Wa.type.footnote, color = c.secondaryLabel)
                }
                Row(
                    Modifier.clip(RoundedCornerShape(16.dp)).background(c.fill).padding(horizontal = 12.dp, vertical = 7.dp)
                        .then(Modifier.clickableNoRipple(onManage)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Tune, null, Modifier.size(16.dp), tint = c.accent)
                    Spacer(Modifier.width(5.dp))
                    Text("Manage", style = Wa.type.subhead.copy(fontWeight = FontWeight.SemiBold), color = c.accent)
                }
            }
            Spacer(Modifier.height(WaSpace.lg))
            Container {
                val total = folders.sumOf { it.statusCount }
                GroupedRow(
                    title = "All sources",
                    subtitle = if (folders.isEmpty()) "Nothing connected yet" else "$total statuses across ${folders.size} folder${if (folders.size == 1) "" else "s"}",
                    leading = { StackedBadges(26.dp) },
                    trailing = { if (activeFolderId == 0L) Check() },
                    onClick = { onSelect(0) },
                )
            }
            AppKind.entries.forEach { kind ->
                val group = folders.filter { it.appKind == kind }
                val connected = sources.any { it.appKind == kind }
                if (group.isEmpty() && (kind == AppKind.OTHER || !installed(kind))) return@forEach
                Text(
                    (if (kind == AppKind.OTHER) "Other folders" else appName(kind)).uppercase(),
                    style = Wa.type.caption, color = c.secondaryLabel,
                    modifier = Modifier.padding(start = WaSpace.gutter + WaSpace.lg, top = WaSpace.xl, bottom = WaSpace.sm),
                )
                Container {
                    group.forEachIndexed { i, f ->
                        if (i > 0) RowDivider(66.dp)
                        GroupedRow(
                            title = f.label,
                            subtitle = folderSubtitle(f),
                            leading = { AppKindBadge(f.appKind, 32.dp) },
                            trailing = { if (activeFolderId == f.id) Check() },
                            onClick = { onSelect(f.id) },
                        )
                    }
                    if (group.isEmpty() && !connected) {
                        GroupedRow(
                            title = "Connect ${appName(kind)}",
                            subtitle = "Grant access to its status folder",
                            titleColor = c.accent,
                            leading = {
                                Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(c.accentSoft), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.Add, null, Modifier.size(20.dp), tint = c.accent)
                                }
                            },
                            onClick = { onConnect(kind) },
                        )
                    } else if (group.isEmpty()) {
                        GroupedRow(title = "Waiting for statuses", subtitle = "View a status in ${appName(kind)}, then refresh.")
                    }
                }
            }
            Text(
                "WhatsApp doesn't reveal which account a status belongs to. WABox lists every status folder it can see. " +
                    "If two accounts share a folder, their statuses appear together. Rename folders in Settings › Sources.",
                style = Wa.type.footnote, color = c.secondaryLabel,
                modifier = Modifier.padding(horizontal = WaSpace.gutter + WaSpace.lg, vertical = WaSpace.lg),
            )
        }
    }
}

@Composable
private fun Container(content: @Composable () -> Unit) {
    Column(
        Modifier.padding(horizontal = WaSpace.gutter).fillMaxWidth().clip(RoundedCornerShape(WaRadius.md)).background(
            if (Wa.colors.isDark) androidx.compose.ui.graphics.Color(0xFF2C2C2E) else Wa.colors.surface
        ),
        verticalArrangement = Arrangement.Top,
    ) { content() }
}

@Composable
private fun Check() = Icon(Icons.Rounded.Check, "Selected", Modifier.size(22.dp), tint = Wa.colors.accent)

private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = pressable(onClick = onClick)
