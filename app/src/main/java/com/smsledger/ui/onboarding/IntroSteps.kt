package com.smsledger.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MarkChatRead
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.smsledger.core.limit.limitStatus
import com.smsledger.core.money.AmountInput
import com.smsledger.core.money.formatRupees
import com.smsledger.ui.components.AmountReadout
import com.smsledger.ui.components.BigChoice
import com.smsledger.ui.components.Emphasis
import com.smsledger.ui.components.LimitHero
import com.smsledger.ui.components.NumberPad
import com.smsledger.ui.theme.Space

@Composable
internal fun WelcomeStep(onStart: () -> Unit) {
    OnboardingFrame(actions = { BigChoice("Get started", onStart, emphasis = Emphasis.Primary) }) {
        Text("SMS Ledger", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.weight(1f))
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, shape = MaterialTheme.shapes.extraLarge) {
            LimitHero(limitStatus(16_000, 50_000), 16_000, 50_000, Modifier.padding(Space.XL))
        }
        Spacer(Modifier.height(Space.XXL))
        Text("Know what you can spend today", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(Space.M))
        Text(
            "SMS Ledger reads your bank messages and keeps the ledger for you. Nothing leaves this phone.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
    }
}

private val QuickLimits = listOf(30_000L, 50_000L, 100_000L, 200_000L)

@Composable
internal fun LimitStep(initialPaise: Long, onContinue: (Long) -> Unit) {
    var text by rememberSaveable { mutableStateOf((initialPaise / 100).toString()) }
    val paise = AmountInput.toPaise(text)
    OnboardingFrame(actions = { BigChoice("Continue", { onContinue(paise) }, emphasis = Emphasis.Primary, enabled = paise > 0) }) {
        Text("Set a daily limit", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(Space.S))
        Text(
            "Each day counts down from this. Rent and bills can be left out later.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        AmountReadout(text)
        Spacer(Modifier.height(Space.L))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.S, Alignment.CenterHorizontally)) {
            QuickLimits.forEach { value ->
                FilterChip(
                    selected = paise == value,
                    onClick = { text = (value / 100).toString() },
                    label = { Text(formatRupees(value)) },
                    shape = MaterialTheme.shapes.small,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        NumberPad(onKey = { text = AmountInput.press(text, it) }, onBackspace = { text = AmountInput.backspace(text) })
        Spacer(Modifier.height(Space.L))
    }
}

/** Play "prominent disclosure" shown before the system SMS dialog (§1). Copy must match real behaviour. */
@Composable
internal fun DisclosureStep(onAllow: () -> Unit, onManual: () -> Unit) {
    OnboardingFrame(
        actions = {
            BigChoice("Allow SMS access", onAllow, emphasis = Emphasis.Primary)
            BigChoice("Add expenses myself", onManual, emphasis = Emphasis.Outline)
        },
    ) {
        Spacer(Modifier.height(Space.XXL))
        IconBadge(Icons.Outlined.Sms)
        Spacer(Modifier.height(Space.XL))
        Text("Let SMS Ledger read your bank messages", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(Space.S))
        Text(
            "This is how spending gets tracked automatically.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Space.XL))
        Column(verticalArrangement = Arrangement.spacedBy(Space.L)) {
            Promise(Icons.Outlined.MarkChatRead, "Only money messages are kept", "Bank and wallet SMS are read. Everything else is skipped and not saved.")
            Promise(Icons.Outlined.Lock, "Stays on this phone", "No account, no servers. The app can't use the internet.")
            Promise(Icons.Outlined.Tune, "You're in control", "Switch to manual entry any time in Settings.")
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = {}, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Read the privacy policy") }
    }
}

@Composable
internal fun ManualStep(onContinue: () -> Unit, onAllow: () -> Unit) {
    OnboardingFrame(
        actions = {
            BigChoice("Go to Today", onContinue, emphasis = Emphasis.Primary)
            TextButton(onClick = onAllow, modifier = Modifier.fillMaxWidth()) { Text("Allow SMS access instead") }
        },
    ) {
        Spacer(Modifier.weight(1f))
        IconBadge(Icons.Outlined.EditNote)
        Spacer(Modifier.height(Space.XL))
        Text("You'll add expenses yourself", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(Space.S))
        Text(
            "Your daily limit, history and categories all work the same. Turn on automatic reading any time in Settings.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1.4f))
    }
}

@Composable
internal fun IconBadge(icon: ImageVector) {
    Box(
        Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun Promise(icon: ImageVector, title: String, body: String) {
    Row {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 2.dp))
        Spacer(Modifier.width(Space.L))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
