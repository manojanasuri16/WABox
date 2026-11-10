package com.wabox.app.ui.designsystem

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.wabox.app.core.model.AppKind
import kotlin.math.PI
import kotlin.math.pow

// ───────────────────────── Color ─────────────────────────

/** Semantic color roles. Screens never reference raw hex values. */
@Immutable
data class WaColors(
    val isDark: Boolean,
    val background: Color,
    val groupedBackground: Color,
    val surface: Color,
    val surfaceSecondary: Color,
    val fill: Color,
    val label: Color,
    val secondaryLabel: Color,
    val tertiaryLabel: Color,
    val separator: Color,
    val accent: Color,
    val onAccent: Color,
    val accentSoft: Color,
    val success: Color,
    val warning: Color,
    val destructive: Color,
    val scrim: Color,
    val chrome: Color,
    val whatsapp: Color,
    val business: Color,
    val otherSource: Color,
) {
    fun tint(kind: AppKind): Color = when (kind) {
        AppKind.WHATSAPP -> whatsapp
        AppKind.BUSINESS -> business
        AppKind.OTHER -> otherSource
    }
}

val LightColors = WaColors(
    isDark = false,
    background = Color(0xFFFFFFFF),
    groupedBackground = Color(0xFFF2F2F7),
    surface = Color(0xFFFFFFFF),
    surfaceSecondary = Color(0xFFF2F2F5),
    fill = Color(0x1F767680),
    label = Color(0xFF0B0B0F),
    secondaryLabel = Color(0xFF6E6E73),
    tertiaryLabel = Color(0xFFAEAEB2),
    separator = Color(0x2E3C3C43),
    accent = Color(0xFF3159F5),
    onAccent = Color(0xFFFFFFFF),
    accentSoft = Color(0x1A3159F5),
    success = Color(0xFF28A745),
    warning = Color(0xFFE08600),
    destructive = Color(0xFFE5342B),
    scrim = Color(0x66000000),
    chrome = Color(0xEBFFFFFF),
    whatsapp = Color(0xFF1FA855),
    business = Color(0xFF138A9E),
    otherSource = Color(0xFF8E8E93),
)

val DarkColors = WaColors(
    isDark = true,
    background = Color(0xFF000000),
    groupedBackground = Color(0xFF000000),
    surface = Color(0xFF1C1C1E),
    surfaceSecondary = Color(0xFF1C1C1E),
    fill = Color(0x3D767680),
    label = Color(0xFFF5F5F7),
    secondaryLabel = Color(0xFF98989F),
    tertiaryLabel = Color(0xFF5C5C61),
    separator = Color(0x5254545A),
    accent = Color(0xFF6E8BFF),
    onAccent = Color(0xFFFFFFFF),
    accentSoft = Color(0x296E8BFF),
    success = Color(0xFF32D158),
    warning = Color(0xFFFFA514),
    destructive = Color(0xFFFF4A40),
    scrim = Color(0x99000000),
    chrome = Color(0xE0121214),
    whatsapp = Color(0xFF2FC46A),
    business = Color(0xFF29A9BE),
    otherSource = Color(0xFF8E8E93),
)

// ───────────────────────── Type ─────────────────────────

/**
 * Apple-style scale on the platform font. Tracking is size-specific: negative for display sizes,
 * ~0 for body, slightly positive for captions. Leading tightens as size grows.
 */
@Immutable
data class WaTypography(
    val largeTitle: TextStyle,
    val title1: TextStyle,
    val title2: TextStyle,
    val title3: TextStyle,
    val headline: TextStyle,
    val body: TextStyle,
    val callout: TextStyle,
    val subhead: TextStyle,
    val footnote: TextStyle,
    val caption: TextStyle,
    val caption2: TextStyle,
)

private val baseStyle = TextStyle(
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

val WaType = WaTypography(
    largeTitle = baseStyle.copy(fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.022).em),
    title1 = baseStyle.copy(fontSize = 27.sp, lineHeight = 33.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.018).em),
    title2 = baseStyle.copy(fontSize = 21.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.012).em),
    title3 = baseStyle.copy(fontSize = 18.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.006).em),
    headline = baseStyle.copy(fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.002).em),
    body = baseStyle.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.em),
    callout = baseStyle.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.em),
    subhead = baseStyle.copy(fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.004.em),
    footnote = baseStyle.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.008.em),
    caption = baseStyle.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.016.em),
    caption2 = baseStyle.copy(fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.03.em),
)

// ───────────────────────── Space & shape ─────────────────────────

object WaSpace {
    val xxs: Dp = 2.dp
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 20.dp
    val xxl: Dp = 24.dp
    val xxxl: Dp = 32.dp
    val huge: Dp = 48.dp
    /** Horizontal screen gutter. */
    val gutter: Dp = 20.dp
    /** Spacing between grid tiles. */
    val gridGap: Dp = 4.dp
}

object WaRadius {
    val xs: Dp = 6.dp
    val sm: Dp = 10.dp
    val md: Dp = 14.dp
    val lg: Dp = 18.dp
    val xl: Dp = 24.dp
    val sheet: Dp = 28.dp
    /** Media tiles in grids. */
    val tile: Dp = 10.dp
}

// ───────────────────────── Motion ─────────────────────────

/**
 * Springs defined the way Apple designers think about them: damping ratio + response (seconds),
 * converted to Compose stiffness with `(2π / response)²` for unit mass.
 */
object WaMotion {
    fun stiffness(response: Float): Float = (2 * PI / response).pow(2).toFloat()

    fun <T> standard(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = stiffness(0.38f))
    fun <T> snappy(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = stiffness(0.24f))
    /** Use only when the gesture carried momentum (flick, throw). */
    fun <T> momentum(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.82f, stiffness = stiffness(0.36f))
    /** Reduced-motion replacement: short cross-fade timing. */
    fun <T> gentle(): FiniteAnimationSpec<T> = tween(durationMillis = 180)

    /** Apple's exponential-decay momentum projection (px, px/s). */
    fun project(velocity: Float, decelerationRate: Float = 0.998f): Float =
        (velocity / 1000f) * decelerationRate / (1f - decelerationRate)

    /** Progressive resistance past a boundary. */
    fun rubberBand(overshoot: Float, dimension: Float, constant: Float = 0.55f): Float =
        (overshoot * dimension * constant) / (dimension + constant * kotlin.math.abs(overshoot))
}

val LocalWaColors = staticCompositionLocalOf { LightColors }
val LocalReducedMotion = staticCompositionLocalOf { false }
