package com.wabox.app.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FolderShared
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wabox.app.BuildConfig
import com.wabox.app.core.model.BackgroundRefresh
import com.wabox.app.core.model.SaveLocation
import com.wabox.app.core.model.SourceHealth
import com.wabox.app.core.model.ThemeMode
import com.wabox.app.ui.LocalBottomBarHeight
import com.wabox.app.ui.designsystem.AppKindBadge
import com.wabox.app.ui.designsystem.CompactTopBar
import com.wabox.app.ui.designsystem.GroupedRow
import com.wabox.app.ui.designsystem.GroupedSection
import com.wabox.app.ui.designsystem.LargeTitle
import com.wabox.app.ui.designsystem.RowDivider
import com.wabox.app.ui.designsystem.RowIcon
import com.wabox.app.ui.designsystem.SegmentedControl
import com.wabox.app.ui.designsystem.Wa
import com.wabox.app.ui.designsystem.WaSpace
import com.wabox.app.ui.statusBarTop
import com.wabox.app.ui.waViewModel

@Composable
fun SettingsScreen(onSources: () -> Unit, onAbout: () -> Unit) {
    val vm = waViewModel { c, _ -> SettingsViewModel(c) }
    val state by vm.state.collectAsStateWithLifecycle()
    val s = state.settings
    val c = Wa.colors
    val context = LocalContext.current
    val scroll = rememberScrollState()
    val collapsed by remember { derivedStateOf { scroll.value > 60 } }

    Box(Modifier.fillMaxSize().background(c.groupedBackground)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(scroll)
                .padding(top = statusBarTop() + 48.dp, bottom = LocalBottomBarHeight.current + WaSpace.xxxl)
        ) {
            LargeTitle("Settings")

            GroupedSection(header = "Sources", footer = "WABox only reads the folders you grant through Android's file picker.") {
                val problems = state.sources.count { it.health != SourceHealth.OK }
                state.folders.forEachIndexed { i, f ->
                    if (i > 0) RowDivider()
                    GroupedRow(
                        title = f.label,
                        subtitle = when (f.sourceHealth) {
                            SourceHealth.OK -> "${f.statusCount} statuses"
                            SourceHealth.NO_STATUS_FOLDER -> "Waiting for statuses"
                            SourceHealth.ACCESS_LOST -> "Access lost"
                        },
                        leading = { AppKindBadge(f.appKind, 30.dp) },
                        showChevron = true,
                        onClick = onSources,
                    )
                }
                if (state.folders.isNotEmpty()) RowDivider()
                GroupedRow(
                    title = if (state.sources.isEmpty()) "Connect a source" else "Manage sources",
                    subtitle = if (problems > 0) "$problems need${if (problems == 1) "s" else ""} attention" else null,
                    leading = { RowIcon(Icons.Rounded.FolderShared, c.accent) },
                    showChevron = true,
                    onClick = onSources,
                )
            }

            GroupedSection(header = "Appearance") {
                Column(Modifier.padding(WaSpace.lg)) {
                    Text("Theme", style = Wa.type.body, color = c.label)
                    Spacer(Modifier.height(WaSpace.md))
                    SegmentedControl(
                        options = listOf("System", "Light", "Dark"),
                        selectedIndex = s.themeMode.ordinal,
                        onSelect = { vm.setTheme(ThemeMode.entries[it]) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            GroupedSection(
                header = "Save location",
                footer = "Saved copies are ordinary gallery files. They remain after a status expires or WABox is uninstalled.",
            ) {
                SaveLocation.entries.forEachIndexed { i, loc ->
                    if (i > 0) RowDivider()
                    GroupedRow(
                        title = if (loc == SaveLocation.PICTURES) "Pictures & Movies" else "Camera folder (DCIM)",
                        subtitle = if (loc.imagesPath == loc.videosPath) loc.imagesPath else "${loc.imagesPath} · ${loc.videosPath}",
                        leading = { RowIcon(Icons.Rounded.PhotoLibrary, if (loc == SaveLocation.PICTURES) Color(0xFFFF9500) else Color(0xFF8E8E93)) },
                        trailing = { if (s.saveLocation == loc) Icon(Icons.Rounded.Check, "Selected", Modifier.size(22.dp), tint = c.accent) },
                        onClick = { vm.setSaveLocation(loc) },
                    )
                }
            }

            GroupedSection(
                header = "Refresh",
                footer = "Background refresh runs only when Android allows it and may be delayed to save battery. Widgets update after every refresh. WABox never polls in real time.",
            ) {
                ToggleRow("Refresh when opened", "Scan for new statuses each time you open WABox", Icons.Rounded.Autorenew, Color(0xFF34C759), s.refreshOnOpen, vm::setRefreshOnOpen)
                RowDivider()
                ToggleRow("Live updates", "Show new statuses instantly while WABox is open", Icons.Rounded.Bolt, Color(0xFFFFB300), s.liveUpdates, vm::setLiveUpdates)
                RowDivider()
                Column(Modifier.padding(WaSpace.lg)) {
                    Text("Background refresh", style = Wa.type.body, color = c.label)
                    Spacer(Modifier.height(WaSpace.md))
                    SegmentedControl(
                        options = listOf("Off", "1 h", "6 h", "12 h", "Daily"),
                        selectedIndex = s.backgroundRefresh.ordinal,
                        onSelect = { vm.setBackgroundRefresh(BackgroundRefresh.entries[it]) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                RowDivider()
                GroupedRow(title = "Scan now", leading = { RowIcon(Icons.Rounded.Schedule, Color(0xFF5856D6)) }, titleColor = c.accent, onClick = vm::rescan)
            }

            GroupedSection(header = "Permissions", footer = "WABox requests no storage, media, or internet permissions. Folder access is managed in Sources.") {
                GroupedRow(
                    title = "Folder access",
                    value = "${state.sources.size} granted",
                    leading = { RowIcon(Icons.Rounded.FolderShared, Color(0xFF007AFF)) },
                    showChevron = true,
                    onClick = onSources,
                )
                RowDivider()
                GroupedRow(
                    title = "App info",
                    subtitle = "Android settings for WABox",
                    leading = { RowIcon(Icons.Rounded.Shield, Color(0xFF8E8E93)) },
                    showChevron = true,
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    },
                )
            }

            GroupedSection(header = "About") {
                GroupedRow(title = "About WABox", leading = { RowIcon(Icons.Rounded.Info, c.accent) }, showChevron = true, onClick = onAbout)
                RowDivider()
                GroupedRow(title = "Version", value = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", leading = { RowIcon(Icons.Rounded.Palette, Color(0xFFAF52DE)) })
            }
        }
        CompactTopBar(title = "Settings", collapsed = collapsed)
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    GroupedRow(
        title = title,
        subtitle = subtitle,
        leading = { RowIcon(icon, tint) },
        trailing = {
            Switch(
                checked = checked, onCheckedChange = onChange,
                colors = SwitchDefaults.colors(checkedTrackColor = Wa.colors.success, checkedThumbColor = Color.White, checkedBorderColor = Color.Transparent),
            )
        },
        onClick = { onChange(!checked) },
    )
}
