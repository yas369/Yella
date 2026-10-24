package com.smsledger.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.smsledger.ui.theme.LedgerTheme
import com.smsledger.ui.theme.Space

/**
 * First run. Manual mode only for now: the one question is the daily limit. The SMS permission
 * explanation and account confirmation (design.md §7) arrive with SMS capture.
 */
@Composable
fun OnboardingFlow(initialLimitPaise: Long, onFinish: (limitPaise: Long) -> Unit) {
    Surface(Modifier.fillMaxSize(), color = LedgerTheme.colors.bg) {
        LimitStep(initialLimitPaise, onContinue = onFinish)
    }
}

/** Shared frame: content area plus a bottom action area, inside system bars. */
@Composable
internal fun OnboardingFrame(
    actions: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = Space.Gutter, vertical = Space.L),
    ) {
        Column(Modifier.weight(1f), content = content)
        Column(verticalArrangement = Arrangement.spacedBy(Space.S), content = actions)
    }
}
