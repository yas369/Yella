package com.smsledger.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import com.smsledger.core.money.formatRupees
import com.smsledger.ui.model.Category
import com.smsledger.ui.model.Tx
import com.smsledger.ui.model.TxKind
import com.smsledger.ui.model.dayLabel
import com.smsledger.ui.theme.LedgerTheme
import com.smsledger.ui.theme.MoneyStyles
import com.smsledger.ui.theme.Size
import com.smsledger.ui.theme.Space

/** Above this font scale the amount drops below the payee instead of squeezing it (§10). */
private const val STACK_FONT_SCALE = 1.5f

/**
 * One ledger row (§6): payee over "account · time" on the left, amount right-aligned on the right.
 * Tap opens edit; no chevrons, avatars or per-row icons. Lent rows are grey *and* say "Lent".
 * [showDay] adds the day to the caption for lists that aren't grouped by day.
 */
@Composable
fun TransactionRow(tx: Tx, category: Category?, onClick: () -> Unit, modifier: Modifier = Modifier, showDay: Boolean = false) {
    val c = LedgerTheme.colors
    val greyed = tx.kind == TxKind.Lent || tx.excluded
    val primary = if (greyed) c.lent else c.text
    val amountColor = when {
        greyed -> c.lent
        tx.kind == TxKind.Income -> c.credit
        tx.kind == TxKind.Transfer -> c.textSecondary
        else -> c.text
    }
    val stacked = LocalDensity.current.fontScale >= STACK_FONT_SCALE
    val payee: @Composable (Modifier) -> Unit = { m ->
        Column(m) {
            Text(tx.payee, style = MaterialTheme.typography.bodyLarge, color = primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${tx.account} · ${if (showDay && tx.daysAgo != 0) "${dayLabel(tx.date)}, " else ""}${tx.time}",
                style = MaterialTheme.typography.bodySmall,
                color = c.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    val amount: @Composable (Alignment.Horizontal) -> Unit = { align ->
        Column(horizontalAlignment = align) {
            Text(amountLabel(tx), style = MoneyStyles.Row, color = amountColor)
            tagText(tx, category)?.let { (text, color) ->
                Text(text, style = MaterialTheme.typography.bodySmall, color = if (color == Color.Unspecified) c.textSecondary else color)
            }
        }
    }
    val base = modifier
        .fillMaxWidth()
        .clickable(onClick = onClick)
        .defaultMinSize(minHeight = Size.Row)
        .padding(horizontal = Space.Gutter, vertical = Space.M)
    if (stacked) {
        Column(base) {
            payee(Modifier)
            amount(Alignment.Start)
        }
    } else {
        Row(base, verticalAlignment = Alignment.CenterVertically) {
            payee(Modifier.weight(1f))
            Spacer(Modifier.width(Space.M))
            amount(Alignment.End)
        }
    }
}

/** Debits plain, credits "+₹…". Never a minus sign for debits (§3). */
fun amountLabel(tx: Tx): String =
    if (tx.kind == TxKind.Income) "+${formatRupees(tx.amountPaise)}" else formatRupees(tx.amountPaise)

/** Caption under the amount: what kind of money this is, in words, so colour is never the only signal. */
@Composable
private fun tagText(tx: Tx, category: Category?): Pair<String, Color>? = when {
    tx.excluded -> "Not counted" to LedgerTheme.colors.lent
    tx.kind == TxKind.Lent -> "Lent" to LedgerTheme.colors.lent
    tx.kind == TxKind.Transfer -> "Self-transfer" to Color.Unspecified
    tx.kind == TxKind.Income -> tx.note.ifBlank { null }?.let { it to Color.Unspecified }
    category != null -> category.name to Color.Unspecified
    else -> null
}
