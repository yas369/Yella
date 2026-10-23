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
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CallMade
import androidx.compose.material.icons.automirrored.outlined.CallReceived
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SuggestionChip
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
import com.smsledger.ui.components.Emphasis
import com.smsledger.ui.model.NeedsItem
import com.smsledger.ui.theme.MoneyStyles
import com.smsledger.ui.theme.Space

/** Teach a message (flows.md §7a): direction → tap the amount → done. Back steps back, keeping answers. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TeachSheet(
    item: NeedsItem.Unreadable,
    onDismiss: () -> Unit,
    onLearned: (summary: String) -> Unit,
    onIgnoreLikeThis: () -> Unit,
) {
    var direction by rememberSaveable { mutableStateOf<String?>(null) }
    var amount by rememberSaveable { mutableStateOf<String?>(null) }
    val candidates = amountCandidates(item.body)
    BackHandler(enabled = direction != null && amount == null) { direction = null }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
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
                Text("Step ${step + 1} of 2", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
                MessageBubble(item.sender, item.body)
            }
            AnimatedContent(step, label = "teachStep") { current ->
                Column(verticalArrangement = Arrangement.spacedBy(Space.S)) {
                    when (current) {
                        0 -> {
                            Text("Is this money going out or coming in?", style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.height(Space.XS))
                            BigChoice("Money out", { direction = "out" }, icon = Icons.AutoMirrored.Outlined.CallMade)
                            BigChoice("Money in", { direction = "in" }, icon = Icons.AutoMirrored.Outlined.CallReceived)
                            BigChoice("Neither, ignore messages like this", onIgnoreLikeThis, emphasis = Emphasis.Outline, icon = Icons.Outlined.Block)
                        }
                        1 -> {
                            Text("Which number is the amount?", style = MaterialTheme.typography.titleLarge)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.S)) {
                                candidates.forEach { value ->
                                    SuggestionChip(
                                        onClick = { amount = value },
                                        label = { Text(value, style = MoneyStyles.Row) },
                                        modifier = Modifier.height(48.dp),
                                    )
                                }
                            }
                        }
                        else -> {
                            Icon(Icons.Outlined.TaskAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                            Text("Got it", style = MaterialTheme.typography.headlineSmall)
                            Text(
                                "Messages like this will be read automatically from now on. You can undo this in Settings → Learned messages.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(Space.S))
                            BigChoice("Done", {
                                val paise = AmountInput.toPaise(amount.orEmpty().replace(",", ""))
                                onLearned("Learned · ${formatRupees(paise)} ${if (direction == "in") "received" else "spent"}")
                            }, emphasis = Emphasis.Primary)
                        }
                    }
                }
            }
        }
    }
}
