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
