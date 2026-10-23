package com.smsledger.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp

/** 4-pt spacing scale. Screen gutter is [Space.Gutter]. */
object Space {
    val XS = 4.dp
    val S = 8.dp
    val M = 12.dp
    val L = 16.dp
    val Gutter = 20.dp
    val XL = 24.dp
    val XXL = 32.dp
    val XXXL = 48.dp
}

private val LedgerShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun SmsLedgerTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLedgerColors provides if (darkTheme) DarkLedgerColors else LightLedgerColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = LedgerTypography,
            shapes = LedgerShapes,
            content = content,
        )
    }
}

/** Shorthand for the ledger-specific colours: `LedgerTheme.colors.warning`. */
object LedgerTheme {
    val colors: LedgerColors
        @Composable get() = LocalLedgerColors.current
}
