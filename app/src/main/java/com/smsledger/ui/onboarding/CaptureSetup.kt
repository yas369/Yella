package com.smsledger.ui.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.mutableFloatStateOf
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
import com.smsledger.core.sms.AccountGroups
import com.smsledger.core.sms.AccountKind
import com.smsledger.core.sms.AccountPair
import com.smsledger.core.sms.Direction
import com.smsledger.core.sms.FoundAccount
import com.smsledger.core.sms.SmsDiscovery
import com.smsledger.data.LedgerStore
import com.smsledger.sms.SmsAccess
import com.smsledger.sms.SmsInbox
import com.smsledger.ui.components.BigChoice
import com.smsledger.ui.components.ConnectedChoice
import com.smsledger.ui.components.Emphasis
import com.smsledger.ui.model.dayLabel
import com.smsledger.ui.theme.LedgerTheme
import com.smsledger.ui.theme.Radius
import com.smsledger.ui.theme.Space
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class CaptureStep { Explain, Reading, Same, Confirm, Importing }

/**
 * Turning on automatic capture (design.md §7 First run): the disclosure Play requires before the
 * system SMS prompt, one read of the inbox, "Same account?" for numbers whose last digits match,
 * then Mine / Not mine for each account. Nothing is the user's until they say so.
 * [onFinished] reports whether automatic mode ended up on; declining leaves manual mode working.
 */
@Composable
fun CaptureSetupFlow(store: LedgerStore, onFinished: (automatic: Boolean) -> Unit) {
    val context = LocalContext.current
    var step by rememberSaveable { mutableStateOf(if (store.discovery != null) CaptureStep.Confirm else CaptureStep.Explain) }
    var newest by rememberSaveable { mutableLongStateOf(0L) }
    var declined by rememberSaveable { mutableStateOf(false) }
    var pending by remember { mutableStateOf<Map<Account, Boolean>?>(null) }
    // "Same account?" answers, started from what the user said on an earlier run.
    val pairs = store.discoveryPairs
    val same = remember(store.discovery) {
        mutableStateMapOf<AccountPair, Boolean>().apply { pairs.forEach { pair -> store.pairAnswer(pair)?.let { put(pair, it) } } }
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.all { it }) step = CaptureStep.Reading else declined = true
    }
    val cancel = {
        store.cancelDiscovery()
        onFinished(false)
    }

    when (step) {
        CaptureStep.Explain -> ExplainStep(
            declined = declined,
            onAllow = { if (SmsAccess.granted(context)) step = CaptureStep.Reading else ask.launch(SmsAccess.PERMISSIONS) },
            onManual = { onFinished(false) },
        )
        CaptureStep.Reading -> {
            LaunchedEffect(Unit) {
                // Reading and sorting thousands of messages takes seconds on a phone: never on the main thread.
                val read = withContext(Dispatchers.IO) { SmsInbox.read(context, 0) }
                val result = withContext(Dispatchers.Default) { SmsDiscovery.discover(read.messages) }
                newest = read.newestMillis
                store.startDiscovery(read.messages, result)
                step = if (store.discoveryPairs.isEmpty()) CaptureStep.Confirm else CaptureStep.Same
            }
            ReadingStep("Reading your messages", "Finding your banks and wallets. Nothing leaves this phone.")
        }
        CaptureStep.Same -> {
            val found = store.discovery?.accounts.orEmpty()
            SameStep(
                pairs = pairs,
                found = found,
                answers = same,
                onAnswer = { pair, isSame -> same[pair] = isSame },
                onDone = { step = CaptureStep.Confirm },
            )
        }
        CaptureStep.Confirm -> {
            val found = store.discovery?.accounts.orEmpty()
            // Accounts the user said are the same are shown, and answered, as one.
            val merged = remember(found, same.toMap()) { AccountGroups.merge(found, same.filterValues { it }.keys).accounts }
            ConfirmStep(
                found = merged,
                // Turning capture on again keeps earlier answers, so "Not mine" is never asked again (§4).
                initial = { account -> store.accounts.firstOrNull { it.account == account }?.mine },
                onDone = { answers ->
                    pending = answers
                    step = CaptureStep.Importing
                },
                onManual = cancel,
            )
        }
        CaptureStep.Importing -> {
            val answers = pending
            var progress by remember { mutableFloatStateOf(0f) }
            LaunchedEffect(answers) {
                // The answers are gone after the screen was rebuilt (rotation): ask again. Nothing is read twice.
                if (answers == null) { step = CaptureStep.Confirm; return@LaunchedEffect }
                store.confirmAccountsInSteps(answers, newest, same.toMap()) { progress = it }
                onFinished(true)
            }
            ReadingStep("Adding your transactions", "Reading past messages from your accounts. This can take a minute.", progress)
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
private fun ReadingStep(title: String, body: String, progress: Float? = null) {
    OnboardingFrame(actions = {}) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(Space.S))
        Text(body, style = MaterialTheme.typography.bodyLarge, color = LedgerTheme.colors.textSecondary)
        Spacer(Modifier.height(Space.L))
        val c = LedgerTheme.colors
        if (progress == null) {
            LinearProgressIndicator(Modifier.fillMaxWidth(), color = c.accent, trackColor = c.fill)
        } else {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = c.accent, trackColor = c.fill)
        }
    }
}

/**
 * logic.md §8.4: two account numbers whose last digits match may be one account written two ways,
 * or seen under two sender names. The user decides each pair; nothing is merged on a guess.
 */
@Composable
private fun SameStep(
    pairs: List<AccountPair>,
    found: List<FoundAccount>,
    answers: Map<AccountPair, Boolean>,
    onAnswer: (AccountPair, Boolean) -> Unit,
    onDone: () -> Unit,
) {
    val c = LedgerTheme.colors
    val byAccount = found.associateBy { it.account }
    OnboardingFrame(
        actions = { BigChoice("Continue", onDone, emphasis = Emphasis.Primary, enabled = pairs.all { it in answers }) },
    ) {
        Text("Are these the same account?", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(Space.S))
        Text(
            "Banks write one account in different ways, and sometimes text from two sender names. These numbers end in the same digits. Choose Same if they are one account.",
            style = MaterialTheme.typography.bodyLarge,
            color = c.textSecondary,
        )
        Spacer(Modifier.height(Space.M))
        LazyColumn(Modifier.weight(1f)) {
            itemsIndexed(pairs, key = { _, pair -> pair.key }) { index, pair ->
                if (index > 0) Spacer(Modifier.height(Space.M))
                Column(
                    Modifier.fillMaxWidth().background(c.surface, Radius.Card).padding(Space.L),
                    verticalArrangement = Arrangement.spacedBy(Space.S),
                ) {
                    listOf(pair.a, pair.b).forEach { account ->
                        Column {
                            Text(account.label, style = MaterialTheme.typography.titleMedium)
                            Text(spelling(account, byAccount[account]), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                        }
                    }
                    ConnectedChoice(
                        options = listOf("Same account", "Different"),
                        selectedIndex = answers[pair]?.let { if (it) 0 else 1 },
                        onSelect = { onAnswer(pair, it == 0) },
                    )
                }
            }
        }
    }
}

/** What tells two look-alike accounts apart without showing more than the last four digits: sender, how the number was written, how often. */
private fun spelling(account: Account, found: FoundAccount?): String {
    val written = if (account.digits.length > 4) "full number, ${account.digits.length} digits" else "last ${account.digits.length} digits"
    val seen = found?.let { if (it.messages == 1) " · in 1 message" else " · in ${it.messages} messages" }.orEmpty()
    return "From ${account.brand} · $written$seen"
}

/** §4 / design.md §7: discovered accounts with Mine / Not mine, nothing chosen in advance, one Done. */
@Composable
private fun ConfirmStep(
    found: List<FoundAccount>,
    initial: (Account) -> Boolean?,
    onDone: (Map<Account, Boolean>) -> Unit,
    onManual: () -> Unit,
) {
    val c = LedgerTheme.colors
    val answers = remember(found) {
        mutableStateMapOf<Account, Boolean>().apply { found.forEach { f -> initial(f.account)?.let { put(f.account, it) } } }
    }
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
    val allAnswered = found.all { it.account in answers }
    OnboardingFrame(
        actions = {
            if (!allAnswered) {
                Text("Choose Mine or Not mine for each account to continue.", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            }
            BigChoice("Done", { onDone(answers.toMap()) }, emphasis = Emphasis.Primary, enabled = allAnswered)
        },
    ) {
        Text("Which accounts are yours?", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(Space.S))
        Text(
            "These are the accounts your messages are about. Only the ones you mark Mine are counted. A family member's account on your number? Mark it Not mine and it's never asked again.",
            style = MaterialTheme.typography.bodyLarge,
            color = c.textSecondary,
        )
        Spacer(Modifier.height(Space.M))
        LazyColumn(Modifier.weight(1f)) {
            itemsIndexed(found, key = { _, f -> f.account.key }) { index, f ->
                if (index > 0) Spacer(Modifier.height(Space.M))
                Column(Modifier.fillMaxWidth().background(c.surface, Radius.Card).padding(Space.L), verticalArrangement = Arrangement.spacedBy(Space.S)) {
                    Column {
                        Text(kindLabel(f.kind), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                        Text(f.account.label, style = MaterialTheme.typography.titleMedium)
                    }
                    Text(evidence(f), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    ConnectedChoice(
                        options = listOf("Mine", "Not mine"),
                        selectedIndex = answers[f.account]?.let { if (it) 0 else 1 },
                        onSelect = { answers[f.account] = it == 0 },
                    )
                }
            }
        }
    }
}

private fun kindLabel(kind: AccountKind): String = when (kind) {
    AccountKind.Bank -> "Bank account"
    AccountKind.Card -> "Card"
    AccountKind.Wallet -> "Wallet"
}

private fun evidence(f: FoundAccount): String = when (f.messages) {
    0 -> lastSeen(f)
    1 -> "In 1 message · ${lastSeen(f)}"
    else -> "In ${f.messages} messages · ${lastSeen(f)}"
}

private fun lastSeen(f: FoundAccount): String {
    val amount = f.lastAmountPaise ?: return "Last message ${dayLabel(f.lastAt.toLocalDate())}"
    val what = when (f.lastDirection) {
        Direction.Debit -> "debited"
        Direction.Credit -> "credited"
        null -> "in last message"
    }
    return "Last: ${formatRupees(amount)} $what · ${dayLabel(f.lastAt.toLocalDate())}"
}
