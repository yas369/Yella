package com.smsledger.ui.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.smsledger.core.money.formatRupees
import com.smsledger.core.sms.Account
import com.smsledger.core.sms.Direction
import com.smsledger.core.sms.FoundAccount
import com.smsledger.data.LedgerStore
import com.smsledger.sms.SmsAccess
import com.smsledger.sms.SmsInbox
import com.smsledger.ui.components.BigChoice
import com.smsledger.ui.components.ConnectedChoice
import com.smsledger.ui.components.Emphasis
import com.smsledger.ui.components.RowDivider
import com.smsledger.ui.model.dayLabel
import com.smsledger.ui.theme.LedgerTheme
import com.smsledger.ui.theme.Space
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class CaptureStep { Explain, Reading, Confirm }

/**
 * Turning on automatic capture (design.md §7 First run 1–2): the disclosure Play requires before
 * the system SMS prompt, then one read of the inbox, then Mine / Not mine for each account found.
 * [onFinished] reports whether automatic mode ended up on; declining leaves manual mode working.
 */
@Composable
fun CaptureSetupFlow(store: LedgerStore, onFinished: (automatic: Boolean) -> Unit) {
    val context = LocalContext.current
    var step by rememberSaveable { mutableStateOf(if (store.discovery != null) CaptureStep.Confirm else CaptureStep.Explain) }
    var newest by rememberSaveable { mutableLongStateOf(0L) }
    var declined by rememberSaveable { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.all { it }) step = CaptureStep.Reading else declined = true
    }

    when (step) {
        CaptureStep.Explain -> ExplainStep(
            declined = declined,
            onAllow = { if (SmsAccess.granted(context)) step = CaptureStep.Reading else ask.launch(SmsAccess.PERMISSIONS) },
            onManual = { onFinished(false) },
        )
        CaptureStep.Reading -> {
            LaunchedEffect(Unit) {
                val read = withContext(Dispatchers.IO) { SmsInbox.read(context, 0) }
                newest = read.newestMillis
                store.startDiscovery(read.messages)
                step = CaptureStep.Confirm
            }
            ReadingStep()
        }
        CaptureStep.Confirm -> {
            val found = store.discovery?.accounts.orEmpty()
            ConfirmStep(
                found = found,
                // Turning capture on again keeps earlier answers, so "Not mine" is never asked again (§4).
                initial = { account -> store.accounts.firstOrNull { it.account == account }?.mine ?: true },
                onDone = { answers ->
                    store.confirmAccounts(answers, newest)
                    onFinished(true)
                },
                onManual = {
                    store.cancelDiscovery()
                    onFinished(false)
                },
            )
        }
    }
}

/** Prominent disclosure (Play policy, spec §1): what is read, what is kept, and that manual mode works without it. */
@Composable
private fun ExplainStep(declined: Boolean, onAllow: () -> Unit, onManual: () -> Unit) {
    val c = LedgerTheme.colors
    OnboardingFrame(
        actions = {
            BigChoice("Allow SMS access", onAllow, emphasis = Emphasis.Primary)
            BigChoice("Add expenses myself", onManual, emphasis = Emphasis.Quiet)
        },
    ) {
        Text("Read bank SMS on this phone", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(Space.M))
        Promise("What is read", "Your SMS inbox, to find messages from your banks and wallets and turn them into transactions.")
        Promise("What is kept", "Only messages from the accounts you confirm. Everything else is skipped and not saved.")
        Promise("Where it goes", "Nowhere. Everything stays on this phone. The app has no internet access.")
        Promise("Without it", "Manual mode works the same: you add expenses yourself. You can turn this on later in Settings.")
        if (declined) {
            Spacer(Modifier.height(Space.M))
            Text(
                "SMS access wasn't allowed. If Android no longer asks, allow it for SMS Ledger in the phone's Settings, Apps.",
                style = MaterialTheme.typography.bodyMedium,
                color = c.textSecondary,
            )
        }
    }
}

@Composable
private fun Promise(title: String, body: String) {
    Column(Modifier.padding(vertical = Space.S)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodyLarge, color = LedgerTheme.colors.textSecondary)
    }
}

@Composable
private fun ReadingStep() {
    OnboardingFrame(actions = {}) {
        Text("Reading your messages", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(Space.S))
        Text(
            "Finding your banks and wallets. Nothing leaves this phone.",
            style = MaterialTheme.typography.bodyLarge,
            color = LedgerTheme.colors.textSecondary,
        )
        Spacer(Modifier.height(Space.L))
        LinearProgressIndicator(Modifier.fillMaxWidth(), color = LedgerTheme.colors.accent, trackColor = LedgerTheme.colors.divider)
    }
}

/** §4 / design.md §7: discovered accounts as rows with Mine / Not mine, one Done. */
@Composable
private fun ConfirmStep(
    found: List<FoundAccount>,
    initial: (Account) -> Boolean,
    onDone: (Map<Account, Boolean>) -> Unit,
    onManual: () -> Unit,
) {
    val c = LedgerTheme.colors
    val answers = remember(found) { mutableStateMapOf<Account, Boolean>().apply { found.forEach { put(it.account, initial(it.account)) } } }
    if (found.isEmpty()) {
        OnboardingFrame(actions = { BigChoice("Use manual mode", onManual, emphasis = Emphasis.Primary) }) {
            Text("No bank messages found", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(Space.S))
            Text(
                "None of the SMS on this phone look like bank or wallet messages. You can add expenses yourself, and turn capture on again later in Settings.",
                style = MaterialTheme.typography.bodyLarge,
                color = c.textSecondary,
            )
        }
        return
    }
    OnboardingFrame(actions = { BigChoice("Done", { onDone(answers.toMap()) }, emphasis = Emphasis.Primary) }) {
        Text("Which accounts are yours?", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(Space.S))
        Text(
            "Only accounts marked Mine are counted. A family member's account on your number? Mark it Not mine and it's never asked again.",
            style = MaterialTheme.typography.bodyLarge,
            color = c.textSecondary,
        )
        Spacer(Modifier.height(Space.M))
        LazyColumn(Modifier.weight(1f)) {
            itemsIndexed(found, key = { _, f -> f.account.key }) { index, f ->
                if (index > 0) RowDivider()
                Column(Modifier.padding(vertical = Space.M), verticalArrangement = Arrangement.spacedBy(Space.S)) {
                    Row { Text(f.account.label, style = MaterialTheme.typography.titleMedium) }
                    Text(evidence(f), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    ConnectedChoice(
                        options = listOf("Mine", "Not mine"),
                        selectedIndex = if (answers[f.account] != false) 0 else 1,
                        onSelect = { answers[f.account] = it == 0 },
                    )
                }
            }
        }
    }
}

private fun evidence(f: FoundAccount): String {
    val amount = f.lastAmountPaise ?: return "Last message ${dayLabel(f.lastAt.toLocalDate())}"
    val what = when (f.lastDirection) {
        Direction.Debit -> "debited"
        Direction.Credit -> "credited"
        null -> "in last message"
    }
    return "Last: ${formatRupees(amount)} $what · ${dayLabel(f.lastAt.toLocalDate())}"
}
