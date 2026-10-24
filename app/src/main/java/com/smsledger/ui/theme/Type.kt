package com.smsledger.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.smsledger.R

/** Bundled, so it renders the same offline (design.md §3). Two weights only. */
val PlexSans = FontFamily(
    Font(R.font.ibm_plex_sans_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_sans_semibold, FontWeight.SemiBold),
)

/** Raw SMS excerpts only. */
val PlexMono = FontFamily(Font(R.font.ibm_plex_mono_regular, FontWeight.Normal))

private fun plex(size: TextUnit, weight: FontWeight, lineHeight: TextUnit) =
    TextStyle(fontFamily = PlexSans, fontSize = size, fontWeight = weight, lineHeight = lineHeight, letterSpacing = 0.sp)

private val Hero = plex(48.sp, FontWeight.SemiBold, 56.sp)
private val Title = plex(20.sp, FontWeight.SemiBold, 28.sp)
private val Body = plex(16.sp, FontWeight.Normal, 24.sp)
private val BodyStrong = plex(16.sp, FontWeight.SemiBold, 24.sp)
private val Label = plex(14.sp, FontWeight.Normal, 20.sp)
private val LabelStrong = plex(14.sp, FontWeight.SemiBold, 20.sp)
private val Caption = plex(12.sp, FontWeight.Normal, 16.sp)

/**
 * The six-step scale from design.md §3 mapped onto Material roles. Display and headline roles
 * collapse to Title so no stock component can produce a display-size screen title.
 */
internal val LedgerTypography = Typography(
    displayLarge = Hero,
    displayMedium = Title,
    displaySmall = Title,
    headlineLarge = Title,
    headlineMedium = Title,
    headlineSmall = Title,
    titleLarge = Title,
    titleMedium = BodyStrong,
    titleSmall = LabelStrong,
    bodyLarge = Body,
    bodyMedium = Label,
    bodySmall = Caption,
    labelLarge = Label,
    labelMedium = Caption,
    labelSmall = Caption,
)

/** Tabular figures so amounts line up in columns and don't shift width as they change. */
private const val TABULAR = "tnum"

object MoneyStyles {
    /** "₹160 left today": one per screen at most. */
    val Hero: TextStyle = LedgerTypography.displayLarge.copy(fontFeatureSettings = TABULAR)
    /** Amounts in rows. */
    val Row: TextStyle = LedgerTypography.titleMedium.copy(fontFeatureSettings = TABULAR)
    /** Amount as a heading (decision cards, edit sheet). */
    val Title: TextStyle = LedgerTypography.titleLarge.copy(fontFeatureSettings = TABULAR)
    /** Secondary amounts in running text ("₹Y of ₹Z spent"). */
    val Label: TextStyle = LedgerTypography.bodyMedium.copy(fontFeatureSettings = TABULAR)
}

/** SMS excerpt style (Plex Mono, Caption). */
val SmsStyle: TextStyle = Caption.copy(fontFamily = PlexMono, lineHeight = 18.sp)
