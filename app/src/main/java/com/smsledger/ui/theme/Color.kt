package com.smsledger.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** The design.md §4 tokens. Colour means state, never decoration; no dynamic colour. */
@Immutable
data class LedgerColors(
    val bg: Color,
    val surface: Color,
    val text: Color,
    val textSecondary: Color,
    val divider: Color,
    val accent: Color,
    val onAccent: Color,
    val over: Color,
    val near: Color,
    val credit: Color,
    val lent: Color,
)

internal val LightLedgerColors = LedgerColors(
    bg = Color(0xFFF7F5F0),
    surface = Color(0xFFFFFFFF),
    text = Color(0xFF1B1C1E),
    textSecondary = Color(0xFF5E6166),
    divider = Color(0xFFE3E0D8),
    accent = Color(0xFF2F45B5),
    onAccent = Color(0xFFFFFFFF),
    over = Color(0xFFC2410C),
    near = Color(0xFFB7791F),
    credit = Color(0xFF2E7D4F),
    lent = Color(0xFF8A8D91),
)

internal val DarkLedgerColors = LedgerColors(
    bg = Color(0xFF121417),
    surface = Color(0xFF1B1E22),
    text = Color(0xFFE8E6E1),
    textSecondary = Color(0xFF9A9C9F),
    divider = Color(0xFF2A2E33),
    accent = Color(0xFF8FA2F2),
    onAccent = Color(0xFF0E1430),
    over = Color(0xFFF08A5D),
    near = Color(0xFFE2B04A),
    credit = Color(0xFF6CC48F),
    lent = Color(0xFF6E7176),
)

/**
 * Maps the tokens onto Material's scheme so stock components (sheets, dialogs, fields, snackbars)
 * follow the brief without per-call colours. Screens sit on `bg`; every container is `surface`;
 * there are no tinted containers, so the *Container roles are neutral.
 */
internal fun LedgerColors.toScheme(dark: Boolean) = if (dark) {
    darkColorScheme(
        primary = accent, onPrimary = onAccent,
        primaryContainer = divider, onPrimaryContainer = text,
        secondary = textSecondary, onSecondary = bg,
        secondaryContainer = divider, onSecondaryContainer = text,
        tertiary = near, onTertiary = bg,
        tertiaryContainer = divider, onTertiaryContainer = text,
        error = over, onError = bg, errorContainer = divider, onErrorContainer = text,
        background = bg, onBackground = text,
        surface = bg, onSurface = text,
        surfaceVariant = divider, onSurfaceVariant = textSecondary,
        surfaceTint = Color.Transparent,
        surfaceBright = surface, surfaceDim = bg,
        surfaceContainerLowest = surface, surfaceContainerLow = surface, surfaceContainer = surface,
        surfaceContainerHigh = surface, surfaceContainerHighest = surface,
        outline = textSecondary, outlineVariant = divider,
        inverseSurface = text, inverseOnSurface = bg, inversePrimary = LightLedgerColors.accent,
        scrim = Color.Black,
    )
} else {
    lightColorScheme(
        primary = accent, onPrimary = onAccent,
        primaryContainer = divider, onPrimaryContainer = text,
        secondary = textSecondary, onSecondary = surface,
        secondaryContainer = divider, onSecondaryContainer = text,
        tertiary = near, onTertiary = surface,
        tertiaryContainer = divider, onTertiaryContainer = text,
        error = over, onError = surface, errorContainer = divider, onErrorContainer = text,
        background = bg, onBackground = text,
        surface = bg, onSurface = text,
        surfaceVariant = divider, onSurfaceVariant = textSecondary,
        surfaceTint = Color.Transparent,
        surfaceBright = surface, surfaceDim = bg,
        surfaceContainerLowest = surface, surfaceContainerLow = surface, surfaceContainer = surface,
        surfaceContainerHigh = surface, surfaceContainerHighest = surface,
        outline = textSecondary, outlineVariant = divider,
        inverseSurface = text, inverseOnSurface = bg, inversePrimary = DarkLedgerColors.accent,
        scrim = Color.Black,
    )
}

val LocalLedgerColors = staticCompositionLocalOf { LightLedgerColors }
