package com.smsledger.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.smsledger.data.AccountState
import com.smsledger.data.TemplateRecord
import com.smsledger.core.sms.Account
import com.smsledger.ui.components.ConnectedChoice
import com.smsledger.ui.components.Emphasis
import com.smsledger.ui.components.LedgerButton
import com.smsledger.ui.components.RowDivider
import com.smsledger.ui.model.Category
import com.smsledger.ui.needs.SmsExcerpt
import com.smsledger.ui.theme.LedgerTheme
import com.smsledger.ui.theme.Space

/** Settings → Accounts (§4): every discovered account with its answer, changeable any time. */
@Composable
fun AccountsScreen(accounts: List<AccountState>, onBack: () -> Unit, onSet: (Account, Boolean) -> Unit) {
    SubScreen("Accounts", onBack) {
        if (accounts.isEmpty()) {
            item { Empty("No accounts yet. They're found when automatic capture is on.") }
        }
        itemsIndexed(accounts, key = { _, a -> a.account.key }) { index, state ->
            if (index > 0) RowDivider()
            Column(Modifier.padding(horizontal = Space.Gutter, vertical = Space.M), verticalArrangement = Arrangement.spacedBy(Space.S)) {
                Text(state.account.label, style = MaterialTheme.typography.titleMedium)
                ConnectedChoice(
                    options = listOf("Mine", "Not mine"),
                    selectedIndex = if (state.mine) 0 else 1,
                    onSelect = { onSet(state.account, it == 0) },
                )
            }
        }
    }
}

/** Settings → Learned messages (§7): every learned format, viewable and undoable. */
@Composable
fun TemplatesScreen(templates: List<TemplateRecord>, onBack: () -> Unit, onForget: (String) -> Unit) {
    SubScreen("Learned messages", onBack) {
        if (templates.isEmpty()) {
            item { Empty("Nothing learned yet. Messages you teach from To review appear here.") }
        }
        itemsIndexed(templates, key = { _, t -> t.template.key }) { index, record ->
            if (index > 0) RowDivider()
            Column(Modifier.padding(horizontal = Space.Gutter, vertical = Space.M), verticalArrangement = Arrangement.spacedBy(Space.S)) {
                Text(
                    when {
                        !record.template.isTransaction -> "Not a transaction · ignored"
                        record.template.moneyIn -> "Money in"
                        else -> "Money out"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                SmsExcerpt(record.example, maxLines = 3)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    LedgerButton("Forget", { onForget(record.template.key) }, emphasis = Emphasis.Quiet)
                }
            }
        }
    }
}

/** Settings → Categories (design.md §7): each category with its "counts toward the daily limit" answer. */
@Composable
fun CategoriesScreen(categories: List<Category>, onBack: () -> Unit, onSet: (String, Boolean) -> Unit) {
    SubScreen("Categories", onBack) {
        item {
            Text(
                "Spending in a category marked Not daily still shows in History and the month total. It just doesn't use up the daily limit.",
                style = MaterialTheme.typography.bodyMedium,
                color = LedgerTheme.colors.textSecondary,
                modifier = Modifier.padding(horizontal = Space.Gutter, vertical = Space.L),
            )
        }
        itemsIndexed(categories, key = { _, category -> category.id }) { _, category ->
            RowDivider()
            Column(Modifier.padding(horizontal = Space.Gutter, vertical = Space.M), verticalArrangement = Arrangement.spacedBy(Space.S)) {
                Text(category.name, style = MaterialTheme.typography.titleMedium)
                ConnectedChoice(
                    options = listOf("Daily", "Not daily"),
                    selectedIndex = if (category.countsDaily) 0 else 1,
                    onSelect = { onSet(category.id, it == 0) },
                )
            }
        }
    }
}

/** Settings → Privacy policy (design.md §7). Plain statements of what the code does. */
@Composable
fun PrivacyScreen(smsBuild: Boolean, onBack: () -> Unit) {
    val sections = buildList {
        add("Where your data lives" to "Only on this phone, in the app's private storage. There is no account and no server.")
        add("Internet" to "The app has no internet permission. It cannot send or receive anything online.")
        if (smsBuild) {
            add("SMS" to "Only if you allow it, the app reads your SMS inbox to find messages from banks and wallets. Messages from accounts you mark Mine are kept with their transactions. Everything else is skipped and not saved.")
            add("Turning it off" to "Switch Mode to Manual in Settings, or remove SMS access for the app in the phone's Settings, Apps. Transactions already captured stay.")
        } else {
            add("SMS" to "This build cannot read SMS. Every entry is one you typed.")
        }
        add("Backups" to "The app is left out of Android's cloud backup, so your ledger is not copied to another device or to the cloud.")
        add("Notifications" to "Limit alerts and the lent reminder are made on the phone. They can be turned off in Settings.")
        add("Deleting" to "Delete all data in Settings erases every entry and setting. Uninstalling the app does the same.")
    }
    SubScreen("Privacy policy", onBack) {
        itemsIndexed(sections) { index, section ->
            if (index > 0) RowDivider()
            Column(Modifier.padding(horizontal = Space.Gutter, vertical = Space.M)) {
                Text(section.first, style = MaterialTheme.typography.titleMedium)
                Text(section.second, style = MaterialTheme.typography.bodyLarge, color = LedgerTheme.colors.textSecondary)
            }
        }
    }
}

@Composable
private fun SubScreen(title: String, onBack: () -> Unit, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    val c = LedgerTheme.colors
    Surface(Modifier.fillMaxSize(), color = c.bg) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
            Row(Modifier.padding(horizontal = Space.XS, vertical = Space.S), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
                Text(title, style = MaterialTheme.typography.titleLarge)
            }
            RowDivider()
            LazyColumn(Modifier.fillMaxSize().navigationBarsPadding(), content = content)
        }
    }
}

@Composable
private fun Empty(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = LedgerTheme.colors.textSecondary,
        modifier = Modifier.padding(horizontal = Space.Gutter, vertical = Space.L),
    )
}
