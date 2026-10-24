package com.smsledger.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import com.smsledger.core.limit.LimitLevel
import com.smsledger.core.limit.LimitStatus
import com.smsledger.core.money.formatRupees
import com.smsledger.ui.theme.LedgerTheme
import com.smsledger.ui.theme.MoneyStyles
import com.smsledger.ui.theme.Size as Dims
import com.smsledger.ui.theme.Space

/** Hero and bar colour by state (§4): `text` normally, `near` at 80%+, `over` past the limit. */
@Composable
fun levelColor(level: LimitLevel): Color = when (level) {
    LimitLevel.Ok -> LedgerTheme.colors.text
    LimitLevel.Warning -> LedgerTheme.colors.near
    LimitLevel.Over -> LedgerTheme.colors.over
}

/**
 * Home's answer (§7): "₹160 left today", then "₹340 of ₹500 spent", then the limit bar.
 * Past the limit it reads "₹120 over limit", never a negative number, and the word carries the state.
 */
@Composable
fun LimitHero(status: LimitStatus, spentPaise: Long, limitPaise: Long, modifier: Modifier = Modifier) {
    val over = status.level == LimitLevel.Over
    val amount = if (over) status.overPaise else status.remainingPaise
    val phrase = if (over) "over limit" else "left today"
    val color by animateColorAsState(levelColor(status.level), MaterialTheme.motionScheme.defaultEffectsSpec(), label = "heroColor")
    val spent = "${formatRupees(spentPaise)} of ${formatRupees(limitPaise)} spent"

    Column(modifier.clearAndSetSemantics { contentDescription = "${formatRupees(amount)} $phrase. $spent" }) {
        // Amount and phrase share a baseline; at large font sizes the phrase wraps below.
        FlowRow(itemVerticalAlignment = Alignment.Bottom) {
            Text(formatRupees(amount), style = MoneyStyles.Hero, color = color, modifier = Modifier.alignByBaseline())
            Text(
                " $phrase",
                style = MaterialTheme.typography.titleLarge,
                color = if (over) color else LedgerTheme.colors.textSecondary,
                modifier = Modifier.alignByBaseline(),
            )
        }
        Spacer(Modifier.height(Space.XS))
        Text(spent, style = MoneyStyles.Label, color = LedgerTheme.colors.textSecondary)
        LimitBar(status.fraction, status.level, Modifier.padding(top = Space.M))
    }
}

/** §6 Progress: thin 4dp bar, `divider` track, fill by state. No rings, no rounded caps. */
@Composable
fun LimitBar(fraction: Float, level: LimitLevel, modifier: Modifier = Modifier) {
    val fill by animateColorAsState(levelColor(level), MaterialTheme.motionScheme.defaultEffectsSpec(), label = "barColor")
    val progress by animateFloatAsState(fraction, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "barFill")
    val track = LedgerTheme.colors.divider
    Canvas(modifier.fillMaxWidth().height(Dims.Bar)) {
        drawRect(track)
        drawRect(fill, Offset.Zero, Size(size.width * progress.coerceIn(0f, 1f), size.height))
    }
}
