package com.wabox.app.ui.settings

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wabox.app.BuildConfig
import com.wabox.app.R
import com.wabox.app.ui.designsystem.CompactTopBar
import com.wabox.app.ui.designsystem.GroupedSection
import com.wabox.app.ui.designsystem.IconCircleButton
import com.wabox.app.ui.designsystem.Wa
import com.wabox.app.ui.designsystem.WaSpace
import com.wabox.app.ui.navBarBottom
import com.wabox.app.ui.statusBarTop

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val c = Wa.colors
    val scroll = rememberScrollState()
    val collapsed by remember { derivedStateOf { scroll.value > 120 } }
    Box(Modifier.fillMaxSize().background(c.groupedBackground)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(scroll).padding(top = statusBarTop() + 48.dp, bottom = navBarBottom() + WaSpace.xxxl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(WaSpace.lg))
            Image(painterResource(R.drawable.app_mark), null, Modifier.size(96.dp).clip(RoundedCornerShape(24.dp)))
            Spacer(Modifier.height(WaSpace.lg))
            Text("WABox", style = Wa.type.title1, color = c.label)
            Text("Every status. Every account. One place.", style = Wa.type.callout, color = c.secondaryLabel, textAlign = TextAlign.Center)
            Spacer(Modifier.height(WaSpace.xs))
            Text("Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", style = Wa.type.footnote, color = c.tertiaryLabel)

            Paragraphs(
                "How it works",
                "When you view a status, WhatsApp downloads it into a hidden .Statuses folder on your phone. With your permission, WABox " +
                    "reads that folder through Android's file picker. Nothing is copied unless you tap Save. Saved copies go to your gallery " +
                    "and stay after the status expires.",
            )
            Paragraphs(
                "Accounts",
                "WhatsApp and WhatsApp Business are always shown separately. Within one app, WABox can only separate accounts that " +
                    "WhatsApp stores in different folders. Which account a status came from is kept in WhatsApp's private, encrypted data, " +
                    "and WABox never tries to access it.",
            )
            Paragraphs(
                "Privacy",
                "WABox has no internet permission, no analytics, and no ads. It reads only the folders you grant, and you can revoke " +
                    "access at any time in Settings › Sources.",
            )
            Paragraphs(
                "Respect",
                "Statuses belong to the people who posted them. Save and share only what you have permission to keep.",
            )
            Paragraphs(
                "Not affiliated",
                "WABox is an independent app. It isn't affiliated with, endorsed by, or sponsored by WhatsApp or Meta. " +
                    "“WhatsApp” is a trademark of its owner and is used here only to name compatible apps.",
            )
        }
        CompactTopBar(
            title = "About",
            collapsed = collapsed,
            leading = { IconCircleButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack, container = Color.Transparent, tint = c.accent) },
        )
    }
}

@Composable
private fun Paragraphs(title: String, body: String) {
    GroupedSection(header = title, modifier = Modifier.fillMaxWidth()) {
        Text(body, style = Wa.type.subhead, color = Wa.colors.label, modifier = Modifier.padding(WaSpace.lg))
    }
}
