package com.smsledger.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.smsledger.R

/**
 * Inter (SIL Open Font License), bundled so it renders the same offline (design.md §3). One
 * variable font file; each weight is an instance of it. Apple's own typeface is licensed for
 * Apple devices only, so it is not used.
 */
@OptIn(ExperimentalTextApi::class)
private fun inter(weight: FontWeight) =
    Font(R.font.inter, weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))

val AppSans = FontFamily(inter(FontWeight.Normal), inter(FontWeight.Medium), inter(FontWeight.SemiBold), inter(FontWeight.Bold))

/** Raw SMS excerpts only. */
val AppMono = FontFamily(Font(R.font.ibm_plex_mono_regular, FontWeight.Normal))

private fun text(size: TextUnit, weight: FontWeight, lineHeight: TextUnit, tracking: TextUnit = 0.sp) =
    TextStyle(fontFamily = AppSans, fontSize = size, fontWeight = weight, lineHeight = lineHeight, letterSpacing = tracking)

// design.md §3 scale. Large sizes are set slightly tight, as the reference style does.
private val Hero = text(48.sp, FontWeight.Bold, 56.sp, (-0.8).sp)
private val LargeTitle = text(34.sp, FontWeight.Bold, 41.sp, (-0.4).sp)
private val Title = text(22.sp, FontWeight.Bold, 28.sp, (-0.2).sp)
private val Headline = text(17.sp, FontWeight.SemiBold, 22.sp)
private val Body = text(17.sp, FontWeight.Normal, 22.sp)
private val Subhead = text(15.sp, FontWeight.Normal, 20.sp)
private val SubheadStrong = text(15.sp, FontWeight.SemiBold, 20.sp)
private val Footnote = text(13.sp, FontWeight.Normal, 18.sp)
private val Caption = text(12.sp, FontWeight.Normal, 16.sp)
private val TabLabel = text(11.sp, FontWeight.Medium, 13.sp)

/** The scale mapped onto Material roles, so stock components (sheets, dialogs, fields) follow it. */
internal val LedgerTypography = Typography(
    displayLarge = Hero,
    displayMedium = LargeTitle,
    displaySmall = LargeTitle,
    headlineLarge = LargeTitle,
    headlineMedium = Title,
    headlineSmall = Title,
    titleLarge = Title,
    titleMedium = Headline,
    titleSmall = SubheadStrong,
    bodyLarge = Body,
    bodyMedium = Subhead,
    bodySmall = Footnote,
    labelLarge = Subhead,
    labelMedium = Caption,
    labelSmall = TabLabel,
)

/** Tabular figures so amounts line up in columns and don't shift width as they change. */
private const val TABULAR = "tnum"

object MoneyStyles {
    /** "₹160 left today": one per screen at most. */
    val Hero: TextStyle = LedgerTypography.displayLarge.copy(fontFeatureSettings = TABULAR)
    /** Amounts in rows. */
    val Row: TextStyle = Body.copy(fontFeatureSettings = TABULAR)
    /** Amount as a heading (decision cards, edit sheet). */
    val Title: TextStyle = LedgerTypography.titleLarge.copy(fontFeatureSettings = TABULAR)
    /** Secondary amounts in running text ("₹Y of ₹Z spent"). */
    val Label: TextStyle = LedgerTypography.bodyMedium.copy(fontFeatureSettings = TABULAR)
}

/** Screen title at the top of a tab ("Today", "History", "Settings"). */
val LargeTitleStyle: TextStyle = LargeTitle

/** Small capitals-style header above a grouped section. */
val SectionStyle: TextStyle = Footnote

/** Primary button label. */
val ButtonStyle: TextStyle = Headline

/** SMS excerpt style (mono, Caption). */
val SmsStyle: TextStyle = Caption.copy(fontFamily = AppMono, lineHeight = 18.sp)
