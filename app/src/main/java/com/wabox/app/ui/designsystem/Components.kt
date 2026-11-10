package com.wabox.app.ui.designsystem

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.BusinessCenter
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wabox.app.core.model.AppKind

// ───────────────────────── Interaction ─────────────────────────

/**
 * Press feedback on touch-down (not release): a critically damped spring scale.
 * Interruptible by construction: animateFloatAsState retargets from the current value.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.pressable(
    enabled: Boolean = true,
    pressedScale: Float = 0.97f,
    role: Role? = Role.Button,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val reduced = Wa.reducedMotion
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled && !reduced) pressedScale else 1f,
        animationSpec = WaMotion.snappy(),
        label = "pressScale",
    )
    val alpha by animateFloatAsState(if (pressed && enabled) 0.82f else 1f, WaMotion.snappy(), label = "pressAlpha")
    this
        .graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }
        .combinedClickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            role = role,
            onLongClick = onLongClick,
            onClick = onClick,
        )
}

/** Light haptic tick for meaningful commits (save, selection change). */
@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics(view) }
}

class Haptics(private val view: android.view.View) {
    fun tick() { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
    fun confirm() {
        val c = if (android.os.Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CONTEXT_CLICK
        view.performHapticFeedback(c)
    }
    fun reject() {
        val c = if (android.os.Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
        view.performHapticFeedback(c)
    }
}

// ───────────────────────── Buttons ─────────────────────────

enum class ButtonStyle { Filled, Tinted, Plain }

@Composable
fun WaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ButtonStyle = ButtonStyle.Filled,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    compact: Boolean = false,
    tint: Color = Wa.colors.accent,
) {
    val c = Wa.colors
    val container = when (style) {
        ButtonStyle.Filled -> tint
        ButtonStyle.Tinted -> tint.copy(alpha = if (c.isDark) 0.18f else 0.11f)
        ButtonStyle.Plain -> Color.Transparent
    }
    val content = when (style) {
        ButtonStyle.Filled -> c.onAccent
        else -> tint
    }
    Row(
        modifier = modifier
            .heightIn(min = if (compact) 36.dp else 50.dp)
            .clip(RoundedCornerShape(if (compact) 18.dp else WaRadius.md))
            .background(container.copy(alpha = if (enabled) container.alpha else container.alpha * 0.4f))
            .pressable(enabled = enabled && !loading, onClick = onClick)
            .padding(horizontal = if (compact) 14.dp else WaSpace.xl),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(18.dp), color = content, strokeWidth = 2.dp)
        } else {
            if (icon != null) {
                Icon(icon, null, Modifier.size(if (compact) 17.dp else 19.dp), tint = content.copy(alpha = if (enabled) 1f else 0.5f))
                Spacer(Modifier.width(if (compact) 6.dp else 8.dp))
            }
            Text(
                text,
                style = if (compact) Wa.type.subhead.copy(fontWeight = FontWeight.SemiBold) else Wa.type.headline,
                color = content.copy(alpha = if (enabled) 1f else 0.5f),
                maxLines = 1,
            )
        }
    }
}

/** Round icon button, used in toolbars and over media. */
@Composable
fun IconCircleButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    container: Color = Wa.colors.fill,
    tint: Color = Wa.colors.label,
    enabled: Boolean = true,
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(container)
            .pressable(enabled = enabled, pressedScale = 0.9f, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(size * 0.52f), tint = tint.copy(alpha = if (enabled) 1f else 0.4f))
    }
}

// ───────────────────────── Segmented control ─────────────────────────

/** iOS-style segmented control. The thumb slides on a critically damped spring. */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Wa.colors
    val haptics = rememberHaptics()
    BoxWithConstraints(
        modifier
            .height(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(c.fill)
            .padding(2.dp)
    ) {
        val segment = maxWidth / options.size
        val offset by animateDpAsState(segment * selectedIndex, if (Wa.reducedMotion) WaMotion.gentle() else WaMotion.standard(), label = "segOffset")
        Box(
            Modifier
                .offset(x = offset)
                .width(segment)
                .height(30.dp)
                .graphicsLayer { shadowElevation = 3.dp.toPx(); shape = RoundedCornerShape(8.dp); clip = true }
                .background(if (c.isDark) Color(0xFF636366) else Color.White, RoundedCornerShape(8.dp))
        )
        Row(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, label ->
                val selected = i == selectedIndex
                val color by animateColorAsState(if (selected) c.label else c.label.copy(alpha = 0.72f), label = "segText")
                Box(
                    Modifier
                        .width(segment)
                        .height(30.dp)
                        .semantics { this.selected = selected; role = Role.Tab }
                        .combinedClickableNoIndication {
                            if (!selected) { haptics.tick(); onSelect(i) }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = Wa.type.subhead.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
                        color = color,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableNoIndication(onClick: () -> Unit): Modifier = composed {
    combinedClickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
}

// ───────────────────────── Badges ─────────────────────────

fun appKindIcon(kind: AppKind): ImageVector = when (kind) {
    AppKind.WHATSAPP -> Icons.Rounded.ChatBubble
    AppKind.BUSINESS -> Icons.Rounded.BusinessCenter
    AppKind.OTHER -> Icons.Rounded.Folder
}

/** Small tinted glyph identifying the source app. Uses WABox's own glyphs, not WhatsApp's marks. */
@Composable
fun AppKindBadge(kind: AppKind, size: Dp = 22.dp, modifier: Modifier = Modifier) {
    val tint = Wa.colors.tint(kind)
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(tint),
        contentAlignment = Alignment.Center,
    ) {
        Icon(appKindIcon(kind), null, Modifier.size(size * 0.56f), tint = Color.White)
    }
}

// ───────────────────────── Empty state ─────────────────────────

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actions: @Composable ColumnScope.() -> Unit = {},
) {
    val c = Wa.colors
    Column(
        modifier.fillMaxWidth().padding(horizontal = WaSpace.xxxl, vertical = WaSpace.huge),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(c.fill),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, Modifier.size(30.dp), tint = c.secondaryLabel)
        }
        Spacer(Modifier.height(WaSpace.xl))
        Text(title, style = Wa.type.title3, color = c.label, textAlign = TextAlign.Center)
        Spacer(Modifier.height(WaSpace.sm))
        Text(message, style = Wa.type.callout, color = c.secondaryLabel, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 320.dp))
        Spacer(Modifier.height(WaSpace.xxl))
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(WaSpace.sm), content = actions)
    }
}

// ───────────────────────── Grouped list ─────────────────────────

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = Wa.type.caption,
        color = Wa.colors.secondaryLabel,
        modifier = modifier.padding(start = WaSpace.gutter + WaSpace.lg, end = WaSpace.gutter, top = WaSpace.xxl, bottom = WaSpace.sm),
    )
}

@Composable
fun SectionFooter(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = Wa.type.footnote,
        color = Wa.colors.secondaryLabel,
        modifier = modifier.padding(start = WaSpace.gutter + WaSpace.lg, end = WaSpace.gutter + WaSpace.lg, top = WaSpace.sm),
    )
}

/** Inset grouped container (iOS Settings idiom). Rows inside are separated by inset hairlines. */
@Composable
fun GroupedSection(
    modifier: Modifier = Modifier,
    header: String? = null,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        if (header != null) SectionHeader(header)
        Column(
            Modifier
                .padding(horizontal = WaSpace.gutter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(WaRadius.md))
                .background(Wa.colors.surface),
            content = content,
        )
        if (footer != null) SectionFooter(footer)
    }
}

@Composable
fun RowDivider(inset: Dp = 56.dp) {
    HorizontalDivider(Modifier.padding(start = inset), thickness = 0.5.dp, color = Wa.colors.separator)
}

/** Colored rounded-square icon tile for grouped rows. */
@Composable
fun RowIcon(icon: ImageVector, tint: Color) {
    Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(tint), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(18.dp), tint = Color.White)
    }
}

@Composable
fun GroupedRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    titleColor: Color = Wa.colors.label,
    showChevron: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val c = Wa.colors
    val base = modifier.fillMaxWidth().heightIn(min = 52.dp)
    Row(
        (if (onClick != null) base.rowClickable(onClick) else base)
            .padding(horizontal = WaSpace.lg, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(WaSpace.md))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = Wa.type.body, color = titleColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = Wa.type.footnote, color = c.secondaryLabel, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
        if (value != null) {
            Spacer(Modifier.width(WaSpace.sm))
            Text(value, style = Wa.type.body, color = c.secondaryLabel, maxLines = 1)
        }
        if (trailing != null) {
            Spacer(Modifier.width(WaSpace.sm))
            trailing()
        }
        if (showChevron) {
            Spacer(Modifier.width(WaSpace.xs))
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.size(22.dp), tint = c.tertiaryLabel)
        }
    }
}

/** Rows use the platform ripple: native Android feedback inside list containers. */
@Composable
private fun Modifier.rowClickable(onClick: () -> Unit): Modifier =
    this.then(Modifier.combinedClickableRipple(onClick))

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Modifier.combinedClickableRipple(onClick: () -> Unit): Modifier =
    combinedClickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = androidx.compose.material3.ripple(),
        onClick = onClick,
    )

// ───────────────────────── Banner ─────────────────────────

@Composable
fun InlineBanner(
    title: String,
    message: String,
    actionText: String?,
    onAction: (() -> Unit)?,
    modifier: Modifier = Modifier,
    tint: Color = Wa.colors.warning,
    icon: ImageVector = Icons.Rounded.Warning,
) {
    val c = Wa.colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(WaRadius.md))
            .background(tint.copy(alpha = if (c.isDark) 0.16f else 0.1f))
            .padding(WaSpace.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = tint)
        Spacer(Modifier.width(WaSpace.md))
        Column(Modifier.weight(1f)) {
            Text(title, style = Wa.type.subhead.copy(fontWeight = FontWeight.SemiBold), color = c.label)
            Text(message, style = Wa.type.footnote, color = c.secondaryLabel)
        }
        if (actionText != null && onAction != null) {
            Spacer(Modifier.width(WaSpace.sm))
            WaButton(actionText, onAction, style = ButtonStyle.Tinted, compact = true, tint = tint)
        }
    }
}

/** Pill showing what's being browsed; opens the source switcher. */
@Composable
fun SourcePill(
    label: String,
    kind: AppKind?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingIcon: ImageVector,
) {
    val c = Wa.colors
    Row(
        modifier
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(c.fill)
            .pressable(onClick = onClick)
            .padding(start = 7.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (kind != null) AppKindBadge(kind, 22.dp) else StackedBadges()
        Spacer(Modifier.width(8.dp))
        Text(label, style = Wa.type.subhead.copy(fontWeight = FontWeight.SemiBold), color = c.label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 180.dp))
        Spacer(Modifier.width(4.dp))
        Icon(trailingIcon, null, Modifier.size(18.dp), tint = c.secondaryLabel)
    }
}

/** "All sources" mark: two overlapping app badges. */
@Composable
fun StackedBadges(size: Dp = 22.dp) {
    Box(Modifier.width(size + 8.dp).height(size)) {
        AppKindBadge(AppKind.BUSINESS, size, Modifier.offset(x = 8.dp))
        Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(Wa.colors.background).padding(1.5.dp)) {
            AppKindBadge(AppKind.WHATSAPP, size - 3.dp)
        }
    }
}
