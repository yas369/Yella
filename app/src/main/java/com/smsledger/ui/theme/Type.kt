package com.smsledger.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Base = Typography()

internal val LedgerTypography = Typography(
    displayLarge = Base.displayLarge.copy(fontWeight = FontWeight.Medium),
    headlineLarge = Base.headlineLarge.copy(fontWeight = FontWeight.Medium, letterSpacing = (-0.5).sp),
    headlineMedium = Base.headlineMedium.copy(fontWeight = FontWeight.Medium, letterSpacing = (-0.25).sp),
    headlineSmall = Base.headlineSmall.copy(fontWeight = FontWeight.Medium),
    titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.Medium),
    titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = Base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = Base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

/** Tabular figures so amounts don't shift width as they change. */
private const val TABULAR = "tnum"

object MoneyStyles {
    val Hero = TextStyle(
        fontSize = 72.sp,
        lineHeight = 76.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = (-2).sp,
        fontFeatureSettings = TABULAR,
    )
    val Large = TextStyle(
        fontSize = 40.sp,
        lineHeight = 44.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = (-1).sp,
        fontFeatureSettings = TABULAR,
    )
    val Row = TextStyle(
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Medium,
        fontFeatureSettings = TABULAR,
    )
}
