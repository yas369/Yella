package com.smsledger.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smsledger.core.limit.LimitLevel
import com.smsledger.core.limit.LimitStatus
import com.smsledger.core.limit.WARNING_FRACTION
import com.smsledger.core.money.formatRupeesWhole
import com.smsledger.ui.theme.LedgerTheme
import com.smsledger.ui.theme.MoneyStyles
import com.smsledger.ui.theme.Space

@Composable
fun levelColor(level: LimitLevel): Color = when (level) {
    LimitLevel.Ok -> MaterialTheme.colorScheme.primary
    LimitLevel.Warning -> LedgerTheme.colors.warning
    LimitLevel.Over -> LedgerTheme.colors.over
}

/** The Today answer: "₹340 left today" or "₹120 over limit", never a negative number (§12). */
@Composable
fun LimitHero(status: LimitStatus, spentPaise: Long, limitPaise: Long, modifier: Modifier = Modifier) {
    val over = status.level == LimitLevel.Over
    val amount = if (over) status.overPaise else status.remainingPaise
    val label = if (over) "over limit" else "left today"
    val amountColor by animateColorAsState(
        if (over) LedgerTheme.colors.over else MaterialTheme.colorScheme.onSurface,
        MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "heroColor",
    )
    val spokenAmount = formatRupeesWhole(amount).removePrefix("₹").replace(",", "")

    Column(modifier.clearAndSetSemantics { contentDescription = "$spokenAmount rupees $label" }) {
        Text(heroAmount(amount, MaterialTheme.colorScheme.onSurfaceVariant), style = MoneyStyles.Hero, color = amountColor)
        Text(
            label,
            style = MaterialTheme.typography.titleLargeEmphasized,
            color = if (over) LedgerTheme.colors.over else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Space.XL))
        DayMeter(status.fraction, status.level)
        Spacer(Modifier.height(Space.S))
        Row(Modifier.fillMaxWidth()) {
            val pct = spentPaise * 100 / limitPaise
            Text(
                if (status.level == LimitLevel.Ok) "${formatRupeesWhole(spentPaise)} spent" else "${formatRupeesWhole(spentPaise)} spent · $pct%",
                style = MaterialTheme.typography.bodyMedium,
                color = if (status.level == LimitLevel.Ok) MaterialTheme.colorScheme.onSurfaceVariant else levelColor(status.level),
                modifier = Modifier.weight(1f),
            )
            Text(
                "Limit ${formatRupeesWhole(limitPaise)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** "₹" set smaller and quieter than the digits, so the number itself carries the weight. */
private fun heroAmount(paise: Long, symbolColor: Color) = buildAnnotatedString {
    withStyle(SpanStyle(fontSize = 44.sp, color = symbolColor, letterSpacing = 0.sp)) { append("₹") }
    append(formatRupeesWhole(paise).removePrefix("₹"))
}

/** One drawn piece of the meter, in fractions of its width. */
data class MeterSegment(val start: Float, val end: Float, val active: Boolean)

/**
 * Splits the meter into Expressive-style pieces: the fill, a [gap], the remaining track, and a [gap]
 * at the [notch] (80%) cutting through whichever piece crosses it. Empty pieces are dropped.
 */
fun meterSegments(progress: Float, notch: Float, gap: Float): List<MeterSegment> {
    val p = progress.coerceIn(0f, 1f)
    fun split(start: Float, end: Float, active: Boolean): List<MeterSegment> =
        if (notch > start && notch < end) {
            listOf(MeterSegment(start, notch - gap / 2, active), MeterSegment(notch + gap / 2, end, active))
        } else {
            listOf(MeterSegment(start, end, active))
        }
    val fill = if (p > 0f) split(0f, p, active = true) else emptyList()
    val trackStart = if (p > 0f) p + gap else 0f
    val track = if (p < 1f) split(trackStart, 1f, active = false) else emptyList()
    return (fill + track).filter { it.end > it.start }
}

/** Thick track, gap between fill and track, a gap at 80%, and a stop dot; fill shifts green → amber → red. */
@Composable
fun DayMeter(fraction: Float, level: LimitLevel, modifier: Modifier = Modifier) {
    val fill by animateColorAsState(levelColor(level), MaterialTheme.motionScheme.defaultEffectsSpec(), label = "meterColor")
    val progress by animateFloatAsState(fraction, MaterialTheme.motionScheme.slowSpatialSpec(), label = "meterFill")
    val track = MaterialTheme.colorScheme.secondaryContainer

    Canvas(modifier.fillMaxWidth().height(16.dp)) {
        val gapPx = 4.dp.toPx()
        val radius = CornerRadius(size.height / 2)
        meterSegments(progress, WARNING_FRACTION.toFloat(), gapPx / size.width).forEach { segment ->
            val x = size.width * segment.start
            val width = (size.width * (segment.end - segment.start)).coerceAtLeast(if (segment.active) size.height else 1f)
            drawRoundRect(if (segment.active) fill else track, Offset(x, 0f), Size(width, size.height), radius)
        }
        if (progress < 1f) {
            val dot = size.height / 4
            drawCircle(fill, dot, Offset(size.width - size.height / 2, size.height / 2))
        }
    }
}
