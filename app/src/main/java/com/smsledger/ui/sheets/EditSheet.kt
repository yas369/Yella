package com.smsledger.ui.sheets

import com.smsledger.ui.components.NumberPad
import com.smsledger.ui.components.ConnectedChoice
import com.smsledger.ui.components.AmountReadout
import com.smsledger.core.money.AmountInput
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
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

/** Tap a row → this sheet (flows.md §9). Big actions first, details below, scope asked only when it matters. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditSheet(
    tx: Tx,
    categories: List<Category>,
    similarCount: Int,
    onDismiss: () -> Unit,
    onKind: (TxKind) -> Unit,
    onCategory: (categoryId: String, allLikeThis: Boolean) -> Unit,
    onExcluded: (Boolean) -> Unit,
    onDetails: (amountPaise: Long, kind: TxKind, payee: String) -> Unit,
) {
    var pendingCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var showSource by rememberSaveable { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.Gutter)
                .navigationBarsPadding()
                .padding(bottom = Space.L),
            verticalArrangement = Arrangement.spacedBy(Space.L),
        ) {
            Column {
                Text(amountLabel(tx), style = MoneyStyles.Large)
                Text(tx.payee, style = MaterialTheme.typography.titleLarge)
                Text("${tx.account} · ${tx.time}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (tx.excluded) {
                    Text("Not a transaction · not counted", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
                }
            }
            // Connected toggle group (Expressive): Lent, Self-transfer and Not a payment each toggle on and off.
            Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
                val canRetype = tx.kind != TxKind.Income && !tx.excluded
                QuickAction(Icons.Outlined.Handshake, "Lent", tx.kind == TxKind.Lent, 0, Modifier.weight(1f), enabled = canRetype) {
                    onKind(if (tx.kind == TxKind.Lent) TxKind.Spend else TxKind.Lent)
                }
                QuickAction(Icons.Outlined.SwapHoriz, "Self-transfer", tx.kind == TxKind.Transfer, 1, Modifier.weight(1f), enabled = canRetype) {
                    onKind(if (tx.kind == TxKind.Transfer) TxKind.Spend else TxKind.Transfer)
                }
                QuickAction(Icons.Outlined.Block, "Not a payment", tx.excluded, 2, Modifier.weight(1f)) { onExcluded(!tx.excluded) }
            }
            if (tx.kind == TxKind.Spend && !tx.excluded) {
                Column(verticalArrangement = Arrangement.spacedBy(Space.S)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Category, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        Text("  Category", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    CategoryChips(categories, pendingCategory ?: tx.categoryId, { pendingCategory = it.id })
                }
                val pending = categories.firstOrNull { it.id == pendingCategory && it.id != tx.categoryId }
                if (pending != null) {
                    ScopePrompt(tx.payee, pending, similarCount, onApply = { all -> onCategory(pending.id, all) })
                }
            }
            DetailsEditor(tx, onDetails)
            TextButton(onClick = { showSource = !showSource }) { Text(if (showSource) "Hide source message" else "Show source message") }
            if (showSource) {
                MessageBubble(
                    tx.account.substringBefore(' ').uppercase(),
                    tx.sms ?: if (tx.account == "Manual") "Added by you" else "${formatRupees(tx.amountPaise).replace("₹", "Rs.")} debited from ${tx.account} to ${tx.payee.uppercase()}. UPI Ref 629114830271",
                )
            }
        }
    }
}

/** §10: amount, direction and payee can always be corrected. Collapsed until asked for. */
@Composable
private fun DetailsEditor(tx: Tx, onDetails: (amountPaise: Long, kind: TxKind, payee: String) -> Unit) {
    var open by rememberSaveable(tx.id) { mutableStateOf(false) }
    if (!open) {
        TextButton(onClick = { open = true }) { Text("Edit amount, direction or payee") }
        return
    }
    var amountText by rememberSaveable(tx.id) { mutableStateOf(rupeesText(tx.amountPaise)) }
    var received by rememberSaveable(tx.id) { mutableStateOf(tx.kind == TxKind.Income) }
    var payee by rememberSaveable(tx.id) { mutableStateOf(tx.payee) }
    val paise = AmountInput.toPaise(amountText)
    val directional = tx.kind == TxKind.Spend || tx.kind == TxKind.Income
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.padding(Space.L), verticalArrangement = Arrangement.spacedBy(Space.M)) {
            Text("Details", style = MaterialTheme.typography.titleSmallEmphasized)
            if (directional) {
                ConnectedChoice(listOf("Spent", "Received"), if (received) 1 else 0, { received = it == 1 })
            }
            OutlinedTextField(
                value = payee,
                onValueChange = { payee = it },
                label = { Text("Payee") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            AmountReadout(amountText)
            NumberPad({ amountText = AmountInput.press(amountText, it) }, { amountText = AmountInput.backspace(amountText) })
            BigChoice(
                "Save changes",
                {
                    val kind = if (directional) (if (received) TxKind.Income else TxKind.Spend) else tx.kind
                    onDetails(paise, kind, payee)
                },
                emphasis = Emphasis.Primary,
                enabled = paise > 0,
            )
        }
    }
}

private fun rupeesText(paise: Long): String =
    if (paise % 100 == 0L) "${paise / 100}" else "${paise / 100}.${(paise % 100).toString().padStart(2, '0')}"

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
private fun QuickAction(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    index: Int,
    modifier: Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    ToggleButton(
        checked = selected,
        onCheckedChange = { onClick() },
        modifier = modifier.height(80.dp),
        enabled = enabled,
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
