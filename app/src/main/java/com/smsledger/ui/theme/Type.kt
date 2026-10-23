package com.smsledger.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Material 3 Expressive type scale (includes the *Emphasized styles). */
internal val LedgerTypography = Typography()

/** Tabular figures so amounts don't shift width as they change. */
private const val TABULAR = "tnum"

/** Money styles derived from the expressive scale, always with tabular figures. */
object MoneyStyles {
    val Hero = LedgerTypography.displayLargeEmphasized.copy(
        fontSize = 80.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 84.sp,
        letterSpacing = (-2).sp,
        fontFeatureSettings = TABULAR,
    )
    val Large = LedgerTypography.displaySmallEmphasized.copy(fontFeatureSettings = TABULAR)
    val Row: TextStyle = LedgerTypography.titleMediumEmphasized.copy(fontFeatureSettings = TABULAR)
}
