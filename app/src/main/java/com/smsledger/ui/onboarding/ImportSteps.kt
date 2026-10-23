package com.smsledger.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.smsledger.ui.components.BigChoice
import com.smsledger.ui.components.Emphasis
import com.smsledger.ui.model.DiscoveredAccount
import com.smsledger.ui.theme.Space
import kotlinx.coroutines.delay

/** Import progress. Design preview simulates progress; T10 drives it from the WorkManager import. */
@Composable
internal fun ImportingStep(accounts: List<DiscoveredAccount>, onDone: () -> Unit) {
    var progress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (progress < 1f) {
            delay(40)
            progress = (progress + 0.012f).coerceAtMost(1f)
        }
        delay(500)
        onDone()
    }
    val checked = (progress * 18_420).toInt()
    OnboardingFrame(actions = {}) {
        Spacer(Modifier.height(Space.XXXL))
        Text("Reading your messages", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(Space.S))
        Text(
            "Finding your banks and wallets. This takes a minute the first time.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Space.XXL))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(8.dp),
            strokeCap = StrokeCap.Round,
        )
        Spacer(Modifier.height(Space.S))
        Text(
            "%,d messages checked".format(checked),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Space.XL))
        accounts.forEachIndexed { index, account ->
            AnimatedVisibility(progress > (index + 1f) / (accounts.size + 1), enter = fadeIn() + slideInVertically { it / 2 }) {
                Row(Modifier.padding(vertical = Space.S), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(Space.M))
                    Text("Found ${account.name}", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.width(Space.S))
                    Text(account.detail, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** §4: every discovered account is answered once: Mine / Not mine. Unanswered ones go to Needs you. */
@Composable
internal fun ConfirmAccountsStep(accounts: List<DiscoveredAccount>, onDone: () -> Unit) {
    val answers = remember { mutableStateMapOf<String, Boolean>() }
    val left = accounts.count { it.id !in answers }
    OnboardingFrame(
        actions = {
            if (left > 0) {
                Text(
                    "$left left. You can answer ${if (left == 1) "it" else "them"} later in Needs you.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(bottom = Space.XS),
                )
            }
            BigChoice("Continue", onDone, emphasis = Emphasis.Primary)
        },
    ) {
        Spacer(Modifier.height(Space.L))
        Text("Which of these are yours?", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(Space.S))
        Text(
            "Only accounts marked Mine are counted. A family member's account on your number? Mark it Not mine.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(
            onClick = { accounts.forEach { answers[it.id] = true } },
            modifier = Modifier.align(Alignment.End),
        ) { Text("All mine") }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(Space.S), contentPadding = PaddingValues(bottom = Space.L)) {
            items(accounts, key = { it.id }) { account ->
                AccountCard(account, answers[account.id]) { answers[account.id] = it }
            }
        }
    }
}

@Composable
private fun AccountCard(account: DiscoveredAccount, mine: Boolean?, onAnswer: (Boolean) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(horizontal = Space.L, vertical = Space.M)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (account.isWallet) Icons.Outlined.AccountBalanceWallet else Icons.Outlined.AccountBalance,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(Space.M))
                Column(Modifier.weight(1f)) {
                    Text("${account.name}  ${account.detail}", style = MaterialTheme.typography.titleMedium)
                    Text(account.evidence, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(Space.S))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(true to "Mine", false to "Not mine").forEachIndexed { index, (value, label) ->
                    SegmentedButton(
                        selected = mine == value,
                        onClick = { onAnswer(value) },
                        shape = SegmentedButtonDefaults.itemShape(index, 2),
                    ) { Text(label) }
                }
            }
        }
    }
}
