package com.smsledger.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.smsledger.core.money.AmountInput
import com.smsledger.core.money.formatRupees
import com.smsledger.ui.components.AmountReadout
import com.smsledger.ui.components.BigChoice
import com.smsledger.ui.components.Emphasis
import com.smsledger.ui.components.LedgerChip
import com.smsledger.ui.components.NumberPad
import com.smsledger.ui.theme.LedgerTheme
import com.smsledger.ui.theme.Space

private val QuickLimits = listOf(30_000L, 50_000L, 100_000L, 200_000L)

/** First run (§7): one numeric field, one button. */
@Composable
internal fun LimitStep(initialPaise: Long, onContinue: (Long) -> Unit) {
    var text by rememberSaveable { mutableStateOf((initialPaise / 100).toString()) }
    val paise = AmountInput.toPaise(text)
    OnboardingFrame(actions = { BigChoice("Done", { onContinue(paise) }, emphasis = Emphasis.Primary, enabled = paise > 0) }) {
        Text("Daily limit", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(Space.S))
        Text(
            "How much you mean to spend in a day. Home counts down from it. Rent, bills and EMI don't count toward it. Everything stays on this phone.",
            style = MaterialTheme.typography.bodyLarge,
            color = LedgerTheme.colors.textSecondary,
        )
        Spacer(Modifier.weight(1f))
        AmountReadout(text)
        Spacer(Modifier.height(Space.L))
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.S, Alignment.CenterHorizontally)) {
            QuickLimits.forEach { value -> LedgerChip(formatRupees(value), paise == value) { text = (value / 100).toString() } }
        }
        Spacer(Modifier.weight(1f))
        NumberPad(onKey = { text = AmountInput.press(text, it) }, onBackspace = { text = AmountInput.backspace(text) })
        Spacer(Modifier.height(Space.L))
    }
}
