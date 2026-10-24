package com.smsledger.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp

/** design.md §5 spacing scale: 4, 8, 12, 16, 24, 32. No other values. */
object Space {
    val XS = 4.dp
    val S = 8.dp
    val M = 12.dp
    val L = 16.dp
    val Gutter = 16.dp
    val XL = 24.dp
    val XXL = 32.dp
}

/** Corner radii (§5): cards 12dp; buttons, chips and inputs 8dp. Rows have none. */
object Radius {
    val Card = RoundedCornerShape(12.dp)
    val Control = RoundedCornerShape(8.dp)
}

/** Touch targets and row heights (§5). */
object Size {
    val Touch = 48.dp
    val Row = 56.dp
    val Bar = 4.dp
}

private val LedgerShapes = Shapes(
    extraSmall = Radius.Control,
    small = Radius.Control,
    medium = Radius.Card,
    large = Radius.Card,
    extraLarge = Radius.Card,
)

/**
 * §6 Motion: 150–200ms, standard easing, no springs. Replaces Material's spring-based schemes so
 * stock components (sheets, chips, snackbars, nav bar) move the same way as our own code.
 */
object LedgerMotion : MotionScheme {
    const val FAST = 150
    const val DEFAULT = 200
    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = tween(DEFAULT, easing = FastOutSlowInEasing)
    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = tween(FAST, easing = FastOutSlowInEasing)
    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = tween(DEFAULT, easing = FastOutSlowInEasing)
    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = tween(DEFAULT, easing = FastOutSlowInEasing)
    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = tween(FAST, easing = FastOutSlowInEasing)
    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = tween(DEFAULT, easing = FastOutSlowInEasing)
}

/** Fixed palette, light or dark from the system (§4). Material You dynamic colour is never used. */
@Composable
fun SmsLedgerTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkLedgerColors else LightLedgerColors
    CompositionLocalProvider(LocalLedgerColors provides colors) {
        MaterialTheme(
            colorScheme = colors.toScheme(darkTheme),
            motionScheme = LedgerMotion,
            shapes = LedgerShapes,
            typography = LedgerTypography,
            content = content,
        )
    }
}

/** Shorthand for the design tokens: `LedgerTheme.colors.near`. */
object LedgerTheme {
    val colors: LedgerColors
        @Composable get() = LocalLedgerColors.current
}
