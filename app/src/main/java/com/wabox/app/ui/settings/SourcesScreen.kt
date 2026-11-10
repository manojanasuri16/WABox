package com.wabox.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wabox.app.core.model.AppKind
import com.wabox.app.core.model.Source
import com.wabox.app.core.model.SourceHealth
import com.wabox.app.core.model.StatusFolder
import com.wabox.app.ui.LocalAppContainer
import com.wabox.app.ui.appName
import com.wabox.app.ui.designsystem.AppKindBadge
import com.wabox.app.ui.designsystem.CompactTopBar
import com.wabox.app.ui.designsystem.GroupedRow
import com.wabox.app.ui.designsystem.GroupedSection
import com.wabox.app.ui.designsystem.IconCircleButton
import com.wabox.app.ui.designsystem.LargeTitle
import com.wabox.app.ui.designsystem.RowDivider
import com.wabox.app.ui.designsystem.RowIcon
import com.wabox.app.ui.designsystem.Wa
import com.wabox.app.ui.designsystem.WaSpace
import com.wabox.app.ui.home.folderSubtitle
import com.wabox.app.ui.navBarBottom
import com.wabox.app.ui.rememberSourcePicker
import com.wabox.app.ui.statusBarTop
import com.wabox.app.ui.waViewModel

@Composable
fun SourcesScreen(onBack: () -> Unit) {
    val vm = waViewModel { c, _ -> SettingsViewModel(c) }
    val state by vm.state.collectAsStateWithLifecycle()
    val container = LocalAppContainer.current
    val picker = rememberSourcePicker()
    val c = Wa.colors
    val scroll = rememberScrollState()
    val collapsed by remember { derivedStateOf { scroll.value > 60 } }
    var renaming by remember { mutableStateOf<StatusFolder?>(null) }
    var removing by remember { mutableStateOf<Source?>(null) }

    Box(Modifier.fillMaxSize().background(c.groupedBackground)) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(top = statusBarTop() + 48.dp, bottom = navBarBottom() + WaSpace.xxxl)) {
            LargeTitle("Sources", subtitle = "Folders WABox can read")

            state.sources.forEach { source ->
                val folders = state.folders.filter { it.sourceId == source.id }
                GroupedSection(header = appName(source.appKind), footer = source.displayPath) {
                    if (source.health == SourceHealth.ACCESS_LOST) {
                        GroupedRow(
                            title = "Access lost",
                            subtitle = "The folder was moved or deleted, or permission was revoked. Reconnect to resume.",
                            titleColor = c.destructive,
                        )
                        RowDivider(16.dp)
                        GroupedRow(title = "Reconnect…", titleColor = c.accent, onClick = { picker.reconnect(source) })
                        RowDivider(16.dp)
                    }
                    if (folders.isEmpty() && source.health == SourceHealth.NO_STATUS_FOLDER) {
                        GroupedRow(
                            title = "No statuses folder yet",
                            subtitle = "View a status in ${appName(source.appKind)}; WABox will find the folder on the next refresh.",
                        )
                        RowDivider(16.dp)
                    }
                    folders.forEach { f ->
                        GroupedRow(
                            title = f.label,
                            subtitle = folderSubtitle(f) + if (f.relativePath.isNotEmpty()) "\n${f.relativePath}" else "",
                            leading = { AppKindBadge(f.appKind, 30.dp) },
                            trailing = { IconCircleButton(Icons.Rounded.Edit, "Rename ${f.label}", { renaming = f }, size = 32.dp, tint = c.accent) },
                            onClick = { renaming = f },
                        )
                        RowDivider()
                    }
                    GroupedRow(title = "Disconnect", titleColor = c.destructive, onClick = { removing = source })
                }
            }

            val missing = listOf(AppKind.WHATSAPP, AppKind.BUSINESS).filter { kind -> state.sources.none { it.appKind == kind } }
            GroupedSection(
                header = "Add",
                footer = "Use “Add another folder” for a legacy /WhatsApp folder, or for a clone app whose folder your phone shows in the file picker.",
            ) {
                missing.forEachIndexed { i, kind ->
                    if (i > 0) RowDivider()
                    val installed = container.grants.isInstalled(kind)
                    GroupedRow(
                        title = "Connect ${appName(kind)}",
                        subtitle = if (installed) "Opens the picker at Android › media › ${if (kind == AppKind.WHATSAPP) "com.whatsapp" else "com.whatsapp.w4b"}" else "Not detected on this device. You can still choose its folder.",
                        leading = { RowIcon(Icons.Rounded.Add, c.tint(kind)) },
                        titleColor = c.accent,
                        onClick = { picker.connect(kind) },
                    )
                }
                if (missing.isNotEmpty()) RowDivider()
                GroupedRow(
                    title = "Add another folder",
                    subtitle = "Any folder that contains a .Statuses folder",
                    leading = { RowIcon(Icons.Rounded.CreateNewFolder, c.otherSource) },
                    titleColor = c.accent,
                    onClick = { picker.addOther() },
                )
            }

            GroupedSection(header = "About accounts") {
                Text(
                    "WhatsApp and WhatsApp Business always keep separate folders, so WABox can tell them apart.\n\n" +
                        "Inside one app, WhatsApp doesn't reveal which account a status came from. That information is in WhatsApp's private, " +
                        "encrypted storage, which no other app can read. WABox shows every status folder it finds. If a second account " +
                        "uses its own folder, it appears here as a separate source you can rename, for example “Work”. If two accounts share a folder, " +
                        "their statuses appear together.",
                    style = Wa.type.footnote, color = c.secondaryLabel, modifier = Modifier.padding(WaSpace.lg),
                )
            }
        }
        CompactTopBar(
            title = "Sources",
            collapsed = collapsed,
            leading = { IconCircleButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack, container = androidx.compose.ui.graphics.Color.Transparent, tint = c.accent) },
        )
    }

    renaming?.let { folder ->
        var text by remember(folder.id) { mutableStateOf(folder.label) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename source") },
            text = {
                Column {
                    Text("Give this folder a name you'll recognise, like “Personal” or “Work”.", style = Wa.type.footnote, color = c.secondaryLabel)
                    OutlinedTextField(text, { text = it.take(40) }, singleLine = true, modifier = Modifier.padding(top = WaSpace.md))
                }
            },
            confirmButton = {
                TextButton(enabled = text.isNotBlank(), onClick = { vm.rename(folder.id, text); renaming = null }) { Text("Save", fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = { TextButton({ renaming = null }) { Text("Cancel") } },
            containerColor = c.surface,
        )
    }

    removing?.let { source ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Disconnect ${appName(source.appKind)}?") },
            text = { Text("WABox will release its access to this folder. Statuses from it disappear from Home. Your saved copies stay.") },
            confirmButton = { TextButton({ vm.remove(source); removing = null }) { Text("Disconnect", color = c.destructive, fontWeight = FontWeight.SemiBold) } },
            dismissButton = { TextButton({ removing = null }) { Text("Cancel") } },
            containerColor = c.surface,
        )
    }
}

