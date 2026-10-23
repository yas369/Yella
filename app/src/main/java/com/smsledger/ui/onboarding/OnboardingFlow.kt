package com.smsledger.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.smsledger.ui.model.DiscoveredAccount
import com.smsledger.ui.theme.Space

private enum class Step { Welcome, Limit, Disclosure, Importing, Confirm, Manual }

/** First launch (flows.md §2 and §3): value first, then the limit, then the SMS ask. */
@Composable
fun OnboardingFlow(
    limitPaise: Long,
    onLimitChange: (Long) -> Unit,
    accounts: List<DiscoveredAccount>,
    onFinish: (automatic: Boolean) -> Unit,
) {
    var step by rememberSaveable { mutableStateOf(Step.Welcome) }
    val previous = when (step) {
        Step.Limit -> Step.Welcome
        Step.Disclosure -> Step.Limit
        Step.Manual -> Step.Disclosure
        else -> null
    }
    BackHandler(enabled = previous != null) { previous?.let { step = it } }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                val forward = targetState.ordinal > initialState.ordinal
                (slideInHorizontally { if (forward) it / 4 else -it / 4 } + fadeIn()) togetherWith
                    (slideOutHorizontally { if (forward) -it / 4 else it / 4 } + fadeOut())
            },
            label = "onboarding",
        ) { current ->
            when (current) {
                Step.Welcome -> WelcomeStep(onStart = { step = Step.Limit })
                Step.Limit -> LimitStep(limitPaise, onContinue = { onLimitChange(it); step = Step.Disclosure })
                Step.Disclosure -> DisclosureStep(
                    onAllow = { step = Step.Importing },
                    onManual = { step = Step.Manual },
                )
                Step.Importing -> ImportingStep(accounts, onDone = { step = Step.Confirm })
                Step.Confirm -> ConfirmAccountsStep(accounts, onDone = { onFinish(true) })
                Step.Manual -> ManualStep(onContinue = { onFinish(false) }, onAllow = { step = Step.Importing })
            }
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
