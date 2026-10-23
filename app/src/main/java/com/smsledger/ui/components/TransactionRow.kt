package com.smsledger.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CallReceived
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.smsledger.core.money.formatRupees
import com.smsledger.ui.model.Category
import com.smsledger.ui.model.Tx
import com.smsledger.ui.model.TxKind
import com.smsledger.ui.theme.LedgerTheme
import com.smsledger.ui.theme.MoneyStyles
import com.smsledger.ui.theme.Space

/** One ledger row. Tap opens the edit sheet (§10). Lent rows are grey *and* labelled, never colour-only. */
@Composable
fun TransactionRow(tx: Tx, category: Category?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val muted = tx.kind == TxKind.Lent || tx.kind == TxKind.Transfer
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Space.Gutter, vertical = Space.M)
            .alpha(if (tx.kind == TxKind.Lent || tx.excluded) 0.6f else 1f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TxAvatar(tx)
        Spacer(Modifier.width(Space.M))
        Column(Modifier.weight(1f)) {
            Text(
                tx.payee,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${tx.account} · ${tx.time}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(Space.M))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                amountLabel(tx),
                style = MoneyStyles.Row,
                color = when {
                    tx.kind == TxKind.Income -> LedgerTheme.colors.credit
                    muted -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
            TxTag(tx, category)
        }
    }
}

fun amountLabel(tx: Tx): String =
    if (tx.kind == TxKind.Income) "+${formatRupees(tx.amountPaise)}" else formatRupees(tx.amountPaise)

@Composable
private fun TxTag(tx: Tx, category: Category?) {
    val (text, color) = when {
        tx.excluded -> "Not counted" to MaterialTheme.colorScheme.onSurfaceVariant
        tx.kind == TxKind.Lent -> "Lent" to MaterialTheme.colorScheme.onSurfaceVariant
        tx.kind == TxKind.Transfer -> "Transfer" to MaterialTheme.colorScheme.onSurfaceVariant
        tx.kind == TxKind.Income -> (tx.note.ifBlank { "Received" }) to MaterialTheme.colorScheme.onSurfaceVariant
        category != null -> category.name to MaterialTheme.colorScheme.onSurfaceVariant
        else -> "Untagged" to MaterialTheme.colorScheme.tertiary
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.XS)) {
        if (category == null && tx.kind == TxKind.Spend && !tx.excluded) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiary))
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
private fun TxAvatar(tx: Tx) {
    val container: Color
    val content: Color
    when (tx.kind) {
        TxKind.Income -> {
            container = MaterialTheme.colorScheme.primaryContainer; content = MaterialTheme.colorScheme.onPrimaryContainer
        }
        else -> {
            container = MaterialTheme.colorScheme.surfaceContainerHigh; content = MaterialTheme.colorScheme.onSurfaceVariant
        }
    }
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(container),
        contentAlignment = Alignment.Center,
    ) {
        when (tx.kind) {
            TxKind.Transfer -> Icon(Icons.Outlined.SwapHoriz, contentDescription = null, tint = content)
            TxKind.Income -> Icon(Icons.AutoMirrored.Outlined.CallReceived, contentDescription = null, tint = content)
            else -> Text(
                tx.payee.first { it.isLetterOrDigit() }.uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = content,
            )
        }
    }
}
