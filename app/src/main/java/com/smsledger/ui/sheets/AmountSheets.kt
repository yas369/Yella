package com.smsledger.ui.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.smsledger.core.money.AmountInput
import com.smsledger.core.money.formatRupees
import com.smsledger.ui.components.AmountReadout
import com.smsledger.ui.components.BigChoice
import com.smsledger.ui.components.ConnectedChoice
import com.smsledger.ui.components.Emphasis
import com.smsledger.ui.components.NumberPad
import com.smsledger.ui.model.Category
import com.smsledger.ui.model.TxKind
import com.smsledger.ui.theme.Space

/** Manual entry (flows.md §6): one sheet, keypad first, everything else optional. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseSheet(
    categories: List<Category>,
    onDismiss: () -> Unit,
    onSave: (amountPaise: Long, kind: TxKind, categoryId: String?, payee: String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    var received by rememberSaveable { mutableStateOf(false) }
    var categoryId by rememberSaveable { mutableStateOf<String?>(null) }
    var payee by rememberSaveable { mutableStateOf("") }
    var askDiscard by rememberSaveable { mutableStateOf(false) }
    val currentText by rememberUpdatedState(text)
    val paise = AmountInput.toPaise(text)

    // Swiping away with an amount typed asks first instead of silently losing it.
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { value ->
            if (value == SheetValue.Hidden && currentText.isNotEmpty()) {
                askDiscard = true; false
            } else {
                true
            }
        },
    )

    ModalBottomSheet(onDismissRequest = { if (text.isEmpty()) onDismiss() else askDiscard = true }, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = Space.L),
            verticalArrangement = Arrangement.spacedBy(Space.M),
        ) {
            ConnectedChoice(
                options = listOf("Spent", "Received"),
                selectedIndex = if (received) 1 else 0,
                onSelect = { received = it == 1 },
                modifier = Modifier.padding(horizontal = Space.Gutter),
            )
            AmountReadout(text, Modifier.padding(vertical = Space.S))
            OutlinedTextField(
                value = payee,
                onValueChange = { payee = it },
                placeholder = { Text(if (received) "From whom? (optional)" else "Where? (optional)") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Space.Gutter),
            )
            if (!received) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = Space.Gutter),
                    horizontalArrangement = Arrangement.spacedBy(Space.S),
                ) {
                    items(categories, key = { it.id }) { category ->
                        FilterChip(
                            selected = categoryId == category.id,
                            onClick = { categoryId = if (categoryId == category.id) null else category.id },
                            label = { Text(category.name) },
                            shape = MaterialTheme.shapes.small,
                        )
                    }
                }
            }
            NumberPad(
                onKey = { text = AmountInput.press(text, it) },
                onBackspace = { text = AmountInput.backspace(text) },
                modifier = Modifier.padding(horizontal = Space.L),
            )
            BigChoice(
                if (paise > 0) "Save ${formatRupees(paise)}" else "Save",
                { onSave(paise, if (received) TxKind.Income else TxKind.Spend, categoryId.takeUnless { received }, payee.trim()) },
                Modifier.padding(horizontal = Space.Gutter),
                Emphasis.Primary,
                enabled = paise > 0,
            )
        }
    }

    if (askDiscard) {
        AlertDialog(
            onDismissRequest = { askDiscard = false },
            title = { Text("Discard ${formatRupees(paise)}?") },
            text = { Text("This entry hasn't been saved.") },
            confirmButton = { TextButton(onClick = { askDiscard = false; onDismiss() }) { Text("Discard") } },
            dismissButton = { TextButton(onClick = { askDiscard = false }) { Text("Keep editing") } },
        )
    }
}

private val QuickLimits = listOf(30_000L, 50_000L, 100_000L, 200_000L)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LimitSheet(currentPaise: Long, onDismiss: () -> Unit, onSave: (Long) -> Unit) {
    var text by rememberSaveable { mutableStateOf((currentPaise / 100).toString()) }
    val paise = AmountInput.toPaise(text)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = Space.L),
            verticalArrangement = Arrangement.spacedBy(Space.M),
        ) {
            Text("Daily limit", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = Space.Gutter))
            AmountReadout(text)
            LazyRow(contentPadding = PaddingValues(horizontal = Space.Gutter), horizontalArrangement = Arrangement.spacedBy(Space.S)) {
                items(QuickLimits) { value ->
                    FilterChip(
                        selected = paise == value,
                        onClick = { text = (value / 100).toString() },
                        label = { Text(formatRupees(value)) },
                        shape = MaterialTheme.shapes.small,
                    )
                }
            }
            NumberPad({ text = AmountInput.press(text, it) }, { text = AmountInput.backspace(text) }, Modifier.padding(horizontal = Space.L))
            BigChoice("Save", { onSave(paise) }, Modifier.padding(horizontal = Space.Gutter), Emphasis.Primary, enabled = paise > 0)
        }
    }
}
