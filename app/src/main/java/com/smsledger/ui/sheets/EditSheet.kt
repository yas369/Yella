package com.smsledger.ui.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.smsledger.core.money.formatRupees
import com.smsledger.ui.components.BigChoice
import com.smsledger.ui.components.CategoryChips
import com.smsledger.ui.components.Emphasis
import com.smsledger.ui.components.amountLabel
import com.smsledger.ui.model.Category
import com.smsledger.ui.model.Tx
import com.smsledger.ui.model.TxKind
import com.smsledger.ui.needs.MessageBubble
import com.smsledger.ui.theme.MoneyStyles
import com.smsledger.ui.theme.Space

/** Tap a row → this sheet (flows.md §8). Big actions first, details below, scope asked only when it matters. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditSheet(
    tx: Tx,
    categories: List<Category>,
    similarCount: Int,
    onDismiss: () -> Unit,
    onChange: (updated: Tx, summary: String) -> Unit,
    onNotTransaction: () -> Unit,
) {
    var pendingCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var showSource by rememberSaveable { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Space.Gutter).navigationBarsPadding().padding(bottom = Space.L),
            verticalArrangement = Arrangement.spacedBy(Space.L),
        ) {
            Column {
                Text(amountLabel(tx), style = MoneyStyles.Large)
                Text(tx.payee, style = MaterialTheme.typography.titleLarge)
                Text("${tx.account} · ${tx.time}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (tx.kind == TxKind.Spend || tx.kind == TxKind.Lent || tx.kind == TxKind.Transfer) {
                // Connected toggle group (Expressive): Lent and Self-transfer toggle; Not a payment acts.
                Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
                    QuickAction(Icons.Outlined.Handshake, "Lent", tx.kind == TxKind.Lent, 0, Modifier.weight(1f)) {
                        val lent = tx.kind != TxKind.Lent
                        onChange(tx.copy(kind = if (lent) TxKind.Lent else TxKind.Spend), if (lent) "Marked as lent · not counted" else "No longer lent")
                    }
                    QuickAction(Icons.Outlined.SwapHoriz, "Self-transfer", tx.kind == TxKind.Transfer, 1, Modifier.weight(1f)) {
                        val transfer = tx.kind != TxKind.Transfer
                        onChange(tx.copy(kind = if (transfer) TxKind.Transfer else TxKind.Spend), if (transfer) "Marked as self-transfer" else "No longer a transfer")
                    }
                    QuickAction(Icons.Outlined.Block, "Not a payment", false, 2, Modifier.weight(1f), onNotTransaction)
                }
            }
            if (tx.kind == TxKind.Spend) {
                Column(verticalArrangement = Arrangement.spacedBy(Space.S)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Category, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        Text("  Category", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    CategoryChips(categories, pendingCategory ?: tx.categoryId, { pendingCategory = it.id })
                }
                val pending = categories.firstOrNull { it.id == pendingCategory && it.id != tx.categoryId }
                if (pending != null) {
                    ScopePrompt(tx.payee, pending, similarCount, onApply = { all ->
                        val note = if (pending.countsDaily) "" else " · not counted toward daily limit"
                        val scope = if (all) "all $similarCount ${tx.payee} payments" else tx.payee
                        onChange(tx.copy(categoryId = pending.id), "${pending.name} for $scope$note")
                    })
                }
            }
            TextButton(onClick = { showSource = !showSource }) { Text(if (showSource) "Hide source message" else "Show source message") }
            if (showSource) {
                MessageBubble(
                    tx.account.substringBefore(' ').uppercase(),
                    tx.sms ?: "${formatRupees(tx.amountPaise).replace("₹", "Rs.")} debited from ${tx.account} to ${tx.payee.uppercase()}. UPI Ref 629114830271",
                )
            }
        }
    }
}

@Composable
private fun ScopePrompt(payee: String, category: Category, similarCount: Int, onApply: (all: Boolean) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(Space.L), verticalArrangement = Arrangement.spacedBy(Space.S)) {
            Text("Tag as ${category.name}", style = MaterialTheme.typography.titleMedium)
            if (similarCount > 1) {
                BigChoice("All $payee payments ($similarCount)", { onApply(true) }, emphasis = Emphasis.Primary)
            }
            BigChoice("Just this one", { onApply(false) }, emphasis = if (similarCount > 1) Emphasis.Outline else Emphasis.Primary)
        }
    }
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, selected: Boolean, index: Int, modifier: Modifier, onClick: () -> Unit) {
    ToggleButton(
        checked = selected,
        onCheckedChange = { onClick() },
        modifier = modifier.height(80.dp),
        shapes = when (index) {
            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
            2 -> ButtonGroupDefaults.connectedTrailingButtonShapes()
            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
        },
        colors = ToggleButtonDefaults.colors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        contentPadding = PaddingValues(horizontal = Space.XS),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, contentDescription = null)
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, modifier = Modifier.padding(top = Space.XS))
        }
    }
}
