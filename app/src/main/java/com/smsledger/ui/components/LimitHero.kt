package com.smsledger.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
        if (over) LedgerTheme.colors.over else MaterialTheme.colorScheme.onSurface, label = "heroColor",
    )
    val spokenAmount = formatRupeesWhole(amount).removePrefix("₹").replace(",", "")

    Column(modifier.clearAndSetSemantics { contentDescription = "$spokenAmount rupees $label" }) {
        Text(heroAmount(amount, MaterialTheme.colorScheme.onSurfaceVariant), style = MoneyStyles.Hero, color = amountColor)
        Text(
            label,
            style = MaterialTheme.typography.titleLarge,
            color = if (over) LedgerTheme.colors.over else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Space.XL))
        DayMeter(status.fraction, status.level)
        Spacer(Modifier.height(Space.S))
        Row(Modifier.fillMaxWidth()) {
            val pct = (spentPaise * 100 / limitPaise)
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
    withStyle(SpanStyle(fontSize = 40.sp, color = symbolColor, letterSpacing = 0.sp)) { append("₹") }
    append(formatRupeesWhole(paise).removePrefix("₹"))
}

/** Thick track with a notch at 80%; fill shifts green → amber → red. */
@Composable
fun DayMeter(fraction: Float, level: LimitLevel, modifier: Modifier = Modifier) {
    val fill by animateColorAsState(levelColor(level), label = "meterColor")
    val progress by animateFloatAsState(fraction, tween(600), label = "meterFill")
    val track = LedgerTheme.colors.meterTrack
    val notch = MaterialTheme.colorScheme.surface

    Canvas(modifier.fillMaxWidth().height(14.dp)) {
        val radius = CornerRadius(size.height / 2)
        drawRoundRect(track, cornerRadius = radius)
        if (progress > 0f) {
            val width = (size.width * progress).coerceAtLeast(size.height)
            drawRoundRect(fill, size = Size(width, size.height), cornerRadius = radius)
        }
        val x = size.width * WARNING_FRACTION.toFloat()
        drawLine(notch, Offset(x, 0f), Offset(x, size.height), strokeWidth = 3.dp.toPx())
    }
}
