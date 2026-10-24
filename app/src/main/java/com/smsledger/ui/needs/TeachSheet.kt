package com.smsledger.ui.needs

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CallMade
import androidx.compose.material.icons.automirrored.outlined.CallReceived
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.smsledger.core.money.AmountInput
import com.smsledger.core.money.formatRupees
import com.smsledger.ui.components.BigChoice
import com.smsledger.ui.components.ConnectedChoice
import com.smsledger.ui.components.Emphasis
import com.smsledger.ui.model.NeedsItem
import com.smsledger.ui.theme.MoneyStyles
import com.smsledger.ui.theme.Space
import com.smsledger.ui.theme.LedgerTheme
import com.smsledger.ui.components.LedgerChip

/** Teach a message (flows.md §7a): direction → tap the amount → done. Back steps back, keeping answers. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TeachSheet(
    item: NeedsItem.Unreadable,
    onDismiss: () -> Unit,
    onLearned: (amountPaise: Long, moneyIn: Boolean, allLikeThis: Boolean) -> Unit,
    onIgnoreLikeThis: () -> Unit,
) {
    var direction by rememberSaveable { mutableStateOf<String?>(null) }
    var amount by rememberSaveable { mutableStateOf<String?>(null) }
    val candidates = amountCandidates(item.body)
    BackHandler(enabled = direction != null && amount == null) { direction = null }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = LedgerTheme.colors.surface, tonalElevation = 0.dp) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Space.Gutter).navigationBarsPadding().padding(bottom = Space.L),
            verticalArrangement = Arrangement.spacedBy(Space.L),
        ) {
            val step = when {
                direction == null -> 0
                amount == null -> 1
                else -> 2
            }
            if (step < 2) {
                Text("Step ${step + 1} of 2", style = MaterialTheme.typography.bodySmall, color = LedgerTheme.colors.textSecondary)
                MessageBubble(item.sender, item.body)
            }
            AnimatedContent(step, label = "teachStep") { current ->
                Column(verticalArrangement = Arrangement.spacedBy(Space.S)) {
                    when (current) {
                        0 -> {
                            Text("Is this money going out or coming in?", style = MaterialTheme.typography.titleLarge)
                            ConnectedChoice(
                                options = listOf("Money out", "Money in"),
                                selectedIndex = null,
                                onSelect = { direction = if (it == 0) "out" else "in" },
                                icons = listOf(Icons.AutoMirrored.Outlined.CallMade, Icons.AutoMirrored.Outlined.CallReceived),
                            )
                            BigChoice("Not a transaction. Ignore messages like this", onIgnoreLikeThis, emphasis = Emphasis.Quiet)
                        }
                        1 -> {
                            Text("Which number is the amount?", style = MaterialTheme.typography.titleLarge)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.S)) {
                                candidates.forEach { value ->
                                    LedgerChip("₹$value", selected = false) { amount = value }
                                }
                            }
                        }
                        else -> {
                            Text("₹${amount.orEmpty()} ${if (direction == "in") "in" else "out"}", style = MoneyStyles.Title)
                            Text(
                                "All like this: messages with the same wording are read automatically from now on. You can undo it in Settings, Learned messages.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = LedgerTheme.colors.textSecondary,
                            )
                            Spacer(Modifier.height(Space.S))
                            val paise = AmountInput.toPaise(amount.orEmpty().replace(",", ""))
                            BigChoice("All like this", { onLearned(paise, direction == "in", true) }, emphasis = Emphasis.Primary)
                            BigChoice("Just this one", { onLearned(paise, direction == "in", false) })
                        }
                    }
                }
            }
        }
    }
}
