package com.smsledger.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.smsledger.ui.components.Emphasis
import com.smsledger.ui.components.LedgerButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.smsledger.core.money.formatRupees
import com.smsledger.ui.components.ConnectedChoice
import com.smsledger.ui.components.RowDivider
import com.smsledger.ui.components.SectionHeader
import com.smsledger.ui.theme.LedgerTheme
import com.smsledger.ui.theme.Size
import com.smsledger.ui.theme.Space

data class SettingsState(
    val limitPaise: Long,
    val darkOverride: Boolean?,
)

/** Settings (§7): plain rows separated by dividers, grouped under section titles. No icons. */
@Composable
fun SettingsScreen(
    state: SettingsState,
    onEditLimit: () -> Unit,
    onOpenOwed: () -> Unit,
    onDarkOverride: (Boolean?) -> Unit,
    onAddWidget: () -> Unit,
    onDeleteAll: () -> Unit,
    contentPadding: PaddingValues,
) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + Space.XL)) {
        item { Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars)) }
        item {
            Text("Settings", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = Space.Gutter, end = Space.Gutter, top = Space.L))
        }
        item {
            Group("Spending") {
                row { Setting("Daily limit", formatRupees(state.limitPaise), onClick = onEditLimit) }
                row { Setting("Owed to you", "Money you've lent", onClick = onOpenOwed) }
            }
        }
        item {
            Group("Appearance") {
                row {
                    val options = listOf(null, false, true)
                    Column(Modifier.padding(horizontal = Space.Gutter, vertical = Space.M)) {
                        Text("Theme", style = MaterialTheme.typography.bodyLarge)
                        ConnectedChoice(
                            options = listOf("System", "Light", "Dark"),
                            selectedIndex = options.indexOf(state.darkOverride),
                            onSelect = { onDarkOverride(options[it]) },
                            modifier = Modifier.padding(top = Space.S),
                        )
                    }
                }
                row { Setting("Home screen widget", "What's left today, without opening the app", onClick = onAddWidget) }
            }
        }
        item {
            Group("Your data") {
                row { Setting("Stored on this phone only", "No account, no servers. The app has no internet access.", onClick = null) }
                row { Setting("Delete all data", "Erase every entry and setting and start over", danger = true) { confirmDelete = true } }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete all data?") },
            text = { Text("Every transaction, lent entry and setting is erased from this phone. This can't be undone.") },
            confirmButton = { LedgerButton("Delete", { confirmDelete = false; onDeleteAll() }, emphasis = Emphasis.Text) },
            dismissButton = { LedgerButton("Cancel", { confirmDelete = false }, emphasis = Emphasis.Quiet) },
        )
    }
}

/** Collects a section's rows so [Group] can put dividers between them. */
private class GroupScope {
    val rows = mutableListOf<@Composable () -> Unit>()
    fun row(content: @Composable () -> Unit) {
        rows += content
    }
}

@Composable
private fun Group(title: String, build: GroupScope.() -> Unit) {
    val rows = GroupScope().apply(build).rows
    Column {
        SectionHeader(title)
        rows.forEachIndexed { index, content ->
            if (index > 0) RowDivider()
            content()
        }
    }
}

@Composable
private fun Setting(title: String, value: String, danger: Boolean = false, onClick: (() -> Unit)?) {
    val c = LedgerTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .defaultMinSize(minHeight = Size.Row)
            .padding(horizontal = Space.Gutter, vertical = Space.M),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = if (danger) c.over else c.text)
        Text(value, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
    }
}
