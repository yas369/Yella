package com.smsledger.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.smsledger.data.LedgerStore
import com.smsledger.ui.theme.LedgerTheme
import com.smsledger.ui.theme.Space

/**
 * First run (design.md §7): 1. SMS explanation before the system prompt, 2. account confirmation,
 * 3. daily limit. Declining SMS access skips straight to the limit; manual mode works the same.
 */
@Composable
fun OnboardingFlow(store: LedgerStore, onFinish: (limitPaise: Long) -> Unit) {
    var captureDone by rememberSaveable { mutableStateOf(false) }
    Surface(Modifier.fillMaxSize(), color = LedgerTheme.colors.bg) {
        if (!captureDone) {
            CaptureSetupFlow(store, onFinished = { captureDone = true })
        } else {
            LimitStep(store.limitPaise, onContinue = onFinish)
        }
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
