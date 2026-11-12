package com.smsledger.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** The design.md §4 tokens. Colour means state, never decoration; no dynamic colour. */
@Immutable
data class LedgerColors(
    /** Screen background behind grouped sections. */
    val bg: Color,
    /** Grouped sections, cards and sheets. */
    val surface: Color,
    val text: Color,
    val textSecondary: Color,
    /** Hairline separators between rows. */
    val divider: Color,
    /** Neutral fill: secondary buttons, chips, the search field, segmented-control track, bar tracks. */
    val fill: Color,
    /** The selected segment of a segmented control, raised above [fill]. */
    val raised: Color,
    val accent: Color,
    val onAccent: Color,
    val over: Color,
    val near: Color,
    val credit: Color,
    val lent: Color,
    /** Switches that are on. */
    val on: Color,
)

// Text colours are the darker "accessible" variants where the standard tint would fall below 4.5:1.
internal val LightLedgerColors = LedgerColors(
    bg = Color(0xFFF2F2F7),
    surface = Color(0xFFFFFFFF),
    text = Color(0xFF000000),
    textSecondary = Color(0xFF6C6C70),
    divider = Color(0xFFC6C6C8),
    fill = Color(0xFFE5E5EA),
    raised = Color(0xFFFFFFFF),
    accent = Color(0xFF007AFF),
    onAccent = Color(0xFFFFFFFF),
    over = Color(0xFFD70015),
    near = Color(0xFFB25000),
    credit = Color(0xFF248A3D),
    lent = Color(0xFF8E8E93),
    on = Color(0xFF34C759),
)

internal val DarkLedgerColors = LedgerColors(
    bg = Color(0xFF000000),
    surface = Color(0xFF1C1C1E),
    text = Color(0xFFFFFFFF),
    textSecondary = Color(0xFF98989F),
    divider = Color(0xFF38383A),
    fill = Color(0xFF2C2C2E),
    raised = Color(0xFF636366),
    accent = Color(0xFF0A84FF),
    onAccent = Color(0xFFFFFFFF),
    over = Color(0xFFFF453A),
    near = Color(0xFFFF9F0A),
    credit = Color(0xFF30D158),
    lent = Color(0xFF8E8E93),
    on = Color(0xFF30D158),
)

/**
 * Maps the tokens onto Material's scheme so stock components (sheets, dialogs, fields, snackbars)
 * follow the brief without per-call colours. Screens sit on `bg`; every container is `surface`;
 * there are no tinted containers, so the *Container roles are the neutral fill.
 */
internal fun LedgerColors.toScheme(dark: Boolean) = if (dark) {
    darkColorScheme(
        primary = accent, onPrimary = onAccent,
        primaryContainer = fill, onPrimaryContainer = text,
        secondary = textSecondary, onSecondary = bg,
        secondaryContainer = fill, onSecondaryContainer = text,
        tertiary = near, onTertiary = bg,
        tertiaryContainer = fill, onTertiaryContainer = text,
        error = over, onError = bg, errorContainer = fill, onErrorContainer = text,
        background = bg, onBackground = text,
        surface = bg, onSurface = text,
        surfaceVariant = fill, onSurfaceVariant = textSecondary,
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
        primaryContainer = fill, onPrimaryContainer = text,
        secondary = textSecondary, onSecondary = surface,
        secondaryContainer = fill, onSecondaryContainer = text,
        tertiary = near, onTertiary = surface,
        tertiaryContainer = fill, onTertiaryContainer = text,
        error = over, onError = surface, errorContainer = fill, onErrorContainer = text,
        background = bg, onBackground = text,
        surface = bg, onSurface = text,
        surfaceVariant = fill, onSurfaceVariant = textSecondary,
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
