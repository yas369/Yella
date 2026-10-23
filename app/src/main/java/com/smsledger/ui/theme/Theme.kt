package com.smsledger.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** 4-pt spacing scale. Screen gutter is [Space.Gutter]. */
object Space {
    val XS = 4.dp
    val S = 8.dp
    val M = 12.dp
    val L = 16.dp
    val Gutter = 16.dp
    val XL = 24.dp
    val XXL = 32.dp
    val XXXL = 48.dp
}

/** Dynamic colour (Material You) exists from Android 12. */
val supportsDynamicColor: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * Material 3 Expressive: wallpaper-based colour on Android 12+ (brand "ledger" scheme below that or
 * when switched off), expressive spring motion, and the expressive shape and type scales.
 */
@Composable
fun SmsLedgerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && supportsDynamicColor -> if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    CompositionLocalProvider(LocalLedgerColors provides if (darkTheme) DarkLedgerColors else LightLedgerColors) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            motionScheme = MotionScheme.expressive(),
            typography = LedgerTypography,
            content = content,
        )
    }
}

/** Shorthand for the ledger-specific colours: `LedgerTheme.colors.warning`. */
object LedgerTheme {
    val colors: LedgerColors
        @Composable get() = LocalLedgerColors.current
}
