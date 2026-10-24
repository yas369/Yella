package com.smsledger.ui.sheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.smsledger.core.money.AmountInput
import com.smsledger.ui.components.AmountReadout
import com.smsledger.ui.components.BigChoice
import com.smsledger.ui.components.CategoryChips
import com.smsledger.ui.components.ConnectedChoice
import com.smsledger.ui.components.Emphasis
import com.smsledger.ui.components.LedgerButton
import com.smsledger.ui.components.NumberPad
import com.smsledger.ui.components.SpecialChip
import com.smsledger.ui.components.amountLabel
import com.smsledger.ui.model.Category
import com.smsledger.ui.model.Tx
import com.smsledger.ui.model.TxKind
import com.smsledger.ui.needs.SmsExcerpt
import com.smsledger.ui.theme.LedgerTheme
import com.smsledger.ui.theme.MoneyStyles
import com.smsledger.ui.theme.Radius
import com.smsledger.ui.theme.Space

/**
 * Tap a row → this sheet. The transaction and its SMS first, then category chips with Lent and
 * Self-transfer set apart, then the rarer corrections. Scope is asked only when it matters.
 */
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
    val c = LedgerTheme.colors
    var pendingCategory by rememberSaveable { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surface,
        tonalElevation = 0.dp,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.Gutter)
                .navigationBarsPadding()
                .padding(bottom = Space.L),
            verticalArrangement = Arrangement.spacedBy(Space.L),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(tx.payee, style = MaterialTheme.typography.titleLarge, color = c.text)
                    Text("${tx.account} · ${tx.time}", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                }
                Spacer(Modifier.width(Space.M))
                Text(amountLabel(tx), style = MoneyStyles.Title, color = if (tx.kind == TxKind.Income) c.credit else c.text)
            }
            if (tx.excluded) {
                Text("Not a transaction. Not counted anywhere.", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
            }
            SmsExcerpt(sourceText(tx), maxLines = 4)

            if (!tx.excluded && tx.kind != TxKind.Income) {
                CategoryChips(
                    categories,
                    if (tx.kind == TxKind.Spend) pendingCategory ?: tx.categoryId else null,
                    { pendingCategory = it.id },
                    special = listOf(
                        SpecialChip("Lent", tx.kind == TxKind.Lent) { onKind(if (tx.kind == TxKind.Lent) TxKind.Spend else TxKind.Lent) },
                        SpecialChip("Self-transfer", tx.kind == TxKind.Transfer) {
                            onKind(if (tx.kind == TxKind.Transfer) TxKind.Spend else TxKind.Transfer)
                        },
                    ),
                )
                val pending = categories.firstOrNull { it.id == pendingCategory && (it.id != tx.categoryId || tx.kind != TxKind.Spend) }
                if (pending != null) {
                    ScopePrompt(tx.payee, pending, similarCount, onApply = { all -> onCategory(pending.id, all) })
                }
            }
            DetailsEditor(tx, onDetails)
            LedgerButton(
                if (tx.excluded) "Count this transaction again" else "Not a transaction",
                { onExcluded(!tx.excluded) },
                emphasis = Emphasis.Quiet,
            )
        }
    }
}

private fun sourceText(tx: Tx): String = tx.sms ?: if (tx.account == "Manual") "Added by you" else "No message stored for this transaction"

/** Amount, direction and payee can always be corrected. Collapsed until asked for. */
@Composable
private fun DetailsEditor(tx: Tx, onDetails: (amountPaise: Long, kind: TxKind, payee: String) -> Unit) {
    var open by rememberSaveable(tx.id) { mutableStateOf(false) }
    if (!open) {
        LedgerButton("Edit amount, direction or payee", { open = true }, emphasis = Emphasis.Text)
        return
    }
    var amountText by rememberSaveable(tx.id) { mutableStateOf(rupeesText(tx.amountPaise)) }
    var received by rememberSaveable(tx.id) { mutableStateOf(tx.kind == TxKind.Income) }
    var payee by rememberSaveable(tx.id) { mutableStateOf(tx.payee) }
    val paise = AmountInput.toPaise(amountText)
    val directional = tx.kind == TxKind.Spend || tx.kind == TxKind.Income
    Column(verticalArrangement = Arrangement.spacedBy(Space.M)) {
        if (directional) {
            ConnectedChoice(listOf("Spent", "Received"), if (received) 1 else 0, { received = it == 1 })
        }
        OutlinedTextField(
            value = payee,
            onValueChange = { payee = it },
            label = { Text("Payee") },
            singleLine = true,
            shape = Radius.Control,
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

private fun rupeesText(paise: Long): String =
    if (paise % 100 == 0L) "${paise / 100}" else "${paise / 100}.${(paise % 100).toString().padStart(2, '0')}"

/** "Just this one / All like this", asked once, when a category change could apply more widely. */
@Composable
private fun ScopePrompt(payee: String, category: Category, similarCount: Int, onApply: (all: Boolean) -> Unit) {
    val c = LedgerTheme.colors
    Surface(color = c.surface, shape = Radius.Card, border = BorderStroke(1.dp, c.divider)) {
        Column(Modifier.padding(Space.L), verticalArrangement = Arrangement.spacedBy(Space.S)) {
            Text("Tag as ${category.name}", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            if (similarCount > 1) {
                // Equal choices: stacked, neither filled.
                BigChoice("All $payee payments ($similarCount)", { onApply(true) })
                BigChoice("Just this one", { onApply(false) })
            } else {
                BigChoice("Save", { onApply(false) }, emphasis = Emphasis.Primary)
            }
        }
    }
}
