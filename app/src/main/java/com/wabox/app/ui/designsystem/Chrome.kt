package com.wabox.app.ui.designsystem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wabox.app.ui.MessageTone
import com.wabox.app.ui.UserMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow

/**
 * Compact navigation bar that floats over scrolling content. Transparent at rest; when content
 * scrolls beneath it ([collapsed]), it materialises as a translucent layer with a hairline and the
 * small title fades in, like iOS large-title navigation.
 */
@Composable
fun CompactTopBar(
    title: String,
    collapsed: Boolean,
    modifier: Modifier = Modifier,
    leading: @Composable RowScope.() -> Unit = {},
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val c = Wa.colors
    val progress by animateFloatAsState(if (collapsed) 1f else 0f, WaMotion.standard(), label = "barCollapse")
    Column(
        modifier
            .fillMaxWidth()
            .graphicsLayer { }
            .background(c.chrome.copy(alpha = c.chrome.alpha * progress))
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Box(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = WaSpace.md)) {
            Row(Modifier.align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically, content = leading)
            Text(
                title,
                style = Wa.type.headline,
                color = c.label,
                modifier = Modifier.align(Alignment.Center).graphicsLayer { alpha = progress; translationY = (1f - progress) * 6.dp.toPx() },
                maxLines = 1,
            )
            Row(
                Modifier.align(Alignment.CenterEnd),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(WaSpace.sm),
                content = trailing,
            )
        }
        HorizontalDivider(thickness = 0.5.dp, color = c.separator.copy(alpha = c.separator.alpha * progress))
    }
}

/** The large title that scrolls with content, underneath the compact bar. */
@Composable
fun LargeTitle(title: String, modifier: Modifier = Modifier, subtitle: String? = null) {
    Column(modifier.padding(horizontal = WaSpace.gutter).padding(top = WaSpace.xs, bottom = WaSpace.md)) {
        Text(title, style = Wa.type.largeTitle, color = Wa.colors.label)
        if (subtitle != null) {
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = Wa.type.subhead, color = Wa.colors.secondaryLabel)
        }
    }
}

data class BarItem(val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

/** Bottom navigation: translucent material over content, hairline top edge, three items. */
@Composable
fun WaBottomBar(items: List<BarItem>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = Wa.colors
    val haptics = rememberHaptics()
    Column(modifier.fillMaxWidth().background(c.chrome)) {
        HorizontalDivider(thickness = 0.5.dp, color = c.separator)
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).height(58.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { i, item ->
                val isSel = i == selected
                val tint = if (isSel) c.accent else c.secondaryLabel
                Column(
                    Modifier.weight(1f).pressable(pressedScale = 0.9f, role = androidx.compose.ui.semantics.Role.Tab) {
                        if (!isSel) haptics.tick()
                        onSelect(i)
                    }.padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(if (isSel) item.selectedIcon else item.icon, null, Modifier.size(25.dp), tint = tint)
                    Spacer(Modifier.height(3.dp))
                    Text(item.label, style = Wa.type.caption2.copy(fontWeight = FontWeight.Medium), color = tint)
                }
            }
        }
    }
}

/**
 * Transient feedback pill. Rises from the bottom edge and leaves the same way (spatial consistency),
 * announced to accessibility services as a polite live region.
 */
@Composable
fun ToastHost(messages: Flow<UserMessage>, modifier: Modifier = Modifier) {
    var current by remember { mutableStateOf<UserMessage?>(null) }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(messages) {
        messages.collect { msg ->
            current = msg
            visible = true
        }
    }
    LaunchedEffect(current?.id) {
        if (current != null) {
            delay(if ((current?.text?.length ?: 0) > 50) 3600 else 2400)
            visible = false
        }
    }
    val reduced = Wa.reducedMotion
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = visible && current != null,
            enter = if (reduced) fadeIn() else fadeIn(WaMotion.standard()) + slideInVertically(WaMotion.standard()) { it / 2 } + scaleIn(WaMotion.standard(), initialScale = 0.92f),
            exit = if (reduced) fadeOut() else fadeOut(WaMotion.standard()) + slideOutVertically(WaMotion.standard()) { it / 2 } + scaleOut(WaMotion.standard(), targetScale = 0.92f),
        ) {
            val msg = current ?: return@AnimatedVisibility
            ToastPill(msg)
        }
    }
}

@Composable
private fun ToastPill(msg: UserMessage) {
    val c = Wa.colors
    val (icon, tint) = when (msg.tone) {
        MessageTone.SUCCESS -> Icons.Rounded.CheckCircle to c.success
        MessageTone.INFO -> Icons.Rounded.Info to c.accent
        MessageTone.ERROR -> Icons.Rounded.Error to c.destructive
    }
    Row(
        Modifier
            .padding(horizontal = WaSpace.gutter, vertical = WaSpace.md)
            .widthIn(max = 460.dp)
            .shadow(18.dp, RoundedCornerShape(22.dp), ambientColor = c.scrim, spotColor = c.scrim)
            .clip(RoundedCornerShape(22.dp))
            .background(if (c.isDark) androidx.compose.ui.graphics.Color(0xFF2C2C2E) else c.surface)
            .padding(horizontal = WaSpace.lg, vertical = WaSpace.md)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = tint)
        Spacer(Modifier.width(WaSpace.sm))
        Text(msg.text, style = Wa.type.subhead.copy(fontWeight = FontWeight.Medium), color = c.label)
    }
}
