package com.smsledger.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

internal val LightColors = lightColorScheme(
    primary = Color(0xFF1E5B4A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCDE6DA),
    onPrimaryContainer = Color(0xFF0A3327),
    secondary = Color(0xFF5C6460),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE3E5DF),
    onSecondaryContainer = Color(0xFF1A1F1C),
    tertiary = Color(0xFF8A5A12),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF6E0BC),
    onTertiaryContainer = Color(0xFF2D1B00),
    error = Color(0xFFB3392B),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF8DCD6),
    onErrorContainer = Color(0xFF410B04),
    background = Color(0xFFF7F5F0),
    onBackground = Color(0xFF1C1B18),
    surface = Color(0xFFF7F5F0),
    onSurface = Color(0xFF1C1B18),
    surfaceVariant = Color(0xFFE6E2D9),
    onSurfaceVariant = Color(0xFF5F5D56),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1EEE7),
    surfaceContainer = Color(0xFFECE8E0),
    surfaceContainerHigh = Color(0xFFE6E2D9),
    surfaceContainerHighest = Color(0xFFE0DBD1),
    outline = Color(0xFF8B887F),
    outlineVariant = Color(0xFFD8D3C8),
    inverseSurface = Color(0xFF31302C),
    inverseOnSurface = Color(0xFFF3F0E9),
    inversePrimary = Color(0xFF8ED1B5),
)

internal val DarkColors = darkColorScheme(
    primary = Color(0xFF8ED1B5),
    onPrimary = Color(0xFF003828),
    primaryContainer = Color(0xFF1C4A3C),
    onPrimaryContainer = Color(0xFFBDEBD6),
    secondary = Color(0xFFC1C8C3),
    onSecondary = Color(0xFF2B322E),
    secondaryContainer = Color(0xFF3A413D),
    onSecondaryContainer = Color(0xFFDDE4DF),
    tertiary = Color(0xFFF0C27A),
    onTertiary = Color(0xFF452B00),
    tertiaryContainer = Color(0xFF5A3E0E),
    onTertiaryContainer = Color(0xFFFFDDB0),
    error = Color(0xFFF1A497),
    onError = Color(0xFF5C1A11),
    errorContainer = Color(0xFF5C1A11),
    onErrorContainer = Color(0xFFFFDAD3),
    background = Color(0xFF131412),
    onBackground = Color(0xFFE6E3DC),
    surface = Color(0xFF131412),
    onSurface = Color(0xFFE6E3DC),
    surfaceVariant = Color(0xFF333633),
    onSurfaceVariant = Color(0xFFB0ADA5),
    surfaceContainerLowest = Color(0xFF0E0F0D),
    surfaceContainerLow = Color(0xFF1A1C19),
    surfaceContainer = Color(0xFF1F211E),
    surfaceContainerHigh = Color(0xFF292B28),
    surfaceContainerHighest = Color(0xFF343633),
    outline = Color(0xFF8A8780),
    outlineVariant = Color(0xFF3F423E),
    inverseSurface = Color(0xFFE6E3DC),
    inverseOnSurface = Color(0xFF31302C),
    inversePrimary = Color(0xFF1E5B4A),
)

/** Meaning-carrying colours that Material's scheme has no slot for. */
@Immutable
data class LedgerColors(
    val credit: Color,
    val warning: Color,
    val over: Color,
    val meterTrack: Color,
)

internal val LightLedgerColors = LedgerColors(
    credit = Color(0xFF237A5A),
    warning = Color(0xFFB86E00),
    over = Color(0xFFB3392B),
    meterTrack = Color(0xFFE3DED3),
)

internal val DarkLedgerColors = LedgerColors(
    credit = Color(0xFF8ED1B5),
    warning = Color(0xFFF0B45A),
    over = Color(0xFFF1A497),
    meterTrack = Color(0xFF2E312D),
)

val LocalLedgerColors = staticCompositionLocalOf { LightLedgerColors }
