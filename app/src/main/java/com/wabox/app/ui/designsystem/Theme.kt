package com.wabox.app.ui.designsystem

import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.wabox.app.core.model.ThemeMode

object Wa {
    val colors: WaColors @Composable @ReadOnlyComposable get() = LocalWaColors.current
    val type: WaTypography get() = WaType
    val reducedMotion: Boolean @Composable @ReadOnlyComposable get() = LocalReducedMotion.current
}

@Composable
fun isDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/**
 * WABox theme. WaColors/WaType are the source of truth; they're mapped onto Material 3 so stock
 * components (sheets, switches, dialogs, menus) inherit the same look.
 */
@Composable
fun WaTheme(dark: Boolean, content: @Composable () -> Unit) {
    val colors = if (dark) DarkColors else LightColors
    val context = LocalContext.current
    val reducedMotion = remember {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
    CompositionLocalProvider(LocalWaColors provides colors, LocalReducedMotion provides reducedMotion) {
        MaterialTheme(colorScheme = colors.toMaterial(), typography = materialTypography, content = content)
    }
}

private fun WaColors.toMaterial(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accentSoft,
        onPrimaryContainer = accent,
        secondary = accent,
        onSecondary = onAccent,
        secondaryContainer = accentSoft,
        onSecondaryContainer = label,
        background = background,
        onBackground = label,
        surface = surface,
        onSurface = label,
        surfaceVariant = surfaceSecondary,
        onSurfaceVariant = secondaryLabel,
        surfaceContainerLowest = surface,
        surfaceContainerLow = surface,
        surfaceContainer = surface,
        surfaceContainerHigh = if (isDark) androidx.compose.ui.graphics.Color(0xFF242426) else surface,
        surfaceContainerHighest = surfaceSecondary,
        outline = separator,
        outlineVariant = separator,
        error = destructive,
        onError = onAccent,
        scrim = scrim,
    )
}

private val materialTypography = Typography(
    displaySmall = WaType.largeTitle,
    headlineLarge = WaType.largeTitle,
    headlineMedium = WaType.title1,
    headlineSmall = WaType.title2,
    titleLarge = WaType.title2,
    titleMedium = WaType.headline,
    titleSmall = WaType.subhead,
    bodyLarge = WaType.body,
    bodyMedium = WaType.callout,
    bodySmall = WaType.footnote,
    labelLarge = WaType.headline,
    labelMedium = WaType.caption,
    labelSmall = WaType.caption2,
)
