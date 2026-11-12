package com.smsledger.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import com.smsledger.core.money.formatRupees
import com.smsledger.ui.components.Chevron
import com.smsledger.ui.components.ConnectedChoice
import com.smsledger.ui.components.Emphasis
import com.smsledger.ui.components.GroupedSection
import com.smsledger.ui.components.LedgerButton
import com.smsledger.ui.components.RowDivider
import com.smsledger.ui.components.SectionHeader
import com.smsledger.ui.theme.LargeTitleStyle
import com.smsledger.ui.theme.LedgerTheme
import com.smsledger.ui.theme.Size
import com.smsledger.ui.theme.Space

data class SettingsState(
    val limitPaise: Long,
    val darkOverride: Boolean?,
    val automatic: Boolean = false,
    /** False in the offline build, which declares no SMS permissions. */
    val smsBuild: Boolean = true,
    val accountCount: Int = 0,
    val templateCount: Int = 0,
    val limitAlerts: Boolean = true,
    val lentReminder: Boolean = true,
    /** False when Android notifications are off for the app (design.md §9: a row here, never on Home). */
    val notificationsAllowed: Boolean = true,
    /** A report from the last unexpected close is waiting to be copied. */
    val hasCrashReport: Boolean = false,
)

/** Settings (§7): grouped sections of rows under small headers. Rows that open a screen end in a chevron. */
@Composable
fun SettingsScreen(
    state: SettingsState,
    onEditLimit: () -> Unit,
    onOpenOwed: () -> Unit,
    onDarkOverride: (Boolean?) -> Unit,
    onAddWidget: () -> Unit,
    onDeleteAll: () -> Unit,
    contentPadding: PaddingValues,
    onTurnOnCapture: () -> Unit = {},
    onTurnOffCapture: () -> Unit = {},
    onOpenAccounts: () -> Unit = {},
    onOpenTemplates: () -> Unit = {},
    onLimitAlerts: (Boolean) -> Unit = {},
    onLentReminder: (Boolean) -> Unit = {},
    onOpenCategories: () -> Unit = {},
    onOpenPrivacy: () -> Unit = {},
    onCopyCrash: () -> Unit = {},
) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var confirmManual by rememberSaveable { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + Space.XL)) {
        item { Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars)) }
        item {
            Text("Settings", style = LargeTitleStyle, modifier = Modifier.padding(start = Space.Gutter, end = Space.Gutter, top = Space.XL))
        }
        item {
            Group("Spending") {
                row { Setting("Daily limit", formatRupees(state.limitPaise), chevron = true, onClick = onEditLimit) }
                row { Setting("Categories", "Which ones count toward the daily limit", chevron = true, onClick = onOpenCategories) }
                row { Setting("Owed to you", "Money you've lent", chevron = true, onClick = onOpenOwed) }
            }
        }
        item {
            Group("SMS capture") {
                if (!state.smsBuild) {
                    row { Setting("Mode", "Manual only. This build doesn't read SMS; nothing is collected.", onClick = null) }
                    return@Group
                }
                row {
                    SwitchSetting(
                        "Read bank SMS",
                        if (state.automatic) "Automatic: transactions are added from bank SMS on this phone" else "Manual: you add expenses yourself",
                        checked = state.automatic,
                    ) { on -> if (on) onTurnOnCapture() else confirmManual = true }
                }
                row { Setting("Accounts", if (state.accountCount == 0) "None yet" else "${state.accountCount} found · Mine or Not mine", chevron = true, onClick = onOpenAccounts) }
                row { Setting("Learned messages", if (state.templateCount == 0) "None yet" else "${state.templateCount} message formats", chevron = true, onClick = onOpenTemplates) }
            }
        }
        item {
            Group("Notifications") {
                row {
                    SwitchSetting(
                        "Limit alerts",
                        if (state.limitAlerts && !state.notificationsAllowed) "Off in the phone's settings · tap to allow" else "At 80% and 100% of the daily limit, once a day",
                        checked = state.limitAlerts && state.notificationsAllowed,
                        onChange = onLimitAlerts,
                    )
                }
                row {
                    SwitchSetting(
                        "Lent reminder",
                        if (state.lentReminder && !state.notificationsAllowed) "Off in the phone's settings · tap to allow" else "On the 1st of each month, when someone owes you",
                        checked = state.lentReminder && state.notificationsAllowed,
                        onChange = onLentReminder,
                    )
                }
            }
        }
        item {
            Group("Appearance") {
                row {
                    val options = listOf(null, false, true)
                    Column(Modifier.padding(horizontal = Space.L, vertical = Space.M)) {
                        Text("Theme", style = MaterialTheme.typography.bodyLarge)
                        ConnectedChoice(
                            options = listOf("System", "Light", "Dark"),
                            selectedIndex = options.indexOf(state.darkOverride),
                            onSelect = { onDarkOverride(options[it]) },
                            modifier = Modifier.padding(top = Space.S),
                        )
                    }
                }
                row { Setting("Home screen widget", "What's left today, without opening the app", chevron = true, onClick = onAddWidget) }
            }
        }
        item {
            Group("Your data") {
                row { Setting("Stored on this phone only", "No account, no servers. The app has no internet access.", onClick = null) }
                row { Setting("Privacy policy", "What the app reads, keeps and never sends", chevron = true, onClick = onOpenPrivacy) }
                if (state.hasCrashReport) {
                    row { Setting("Last crash report", "The app closed unexpectedly. Tap to copy the report.", onClick = onCopyCrash) }
                }
                row { Setting("Delete all data", "Erase every entry and setting and start over", danger = true) { confirmDelete = true } }
            }
        }
    }
    if (confirmManual) {
        AlertDialog(
            onDismissRequest = { confirmManual = false },
            title = { Text("Stop reading SMS?") },
            text = { Text("Transactions already captured stay. New bank SMS won't be read until you turn this on again. To remove SMS access entirely, turn it off for SMS Ledger in the phone's Settings, Apps.") },
            confirmButton = { LedgerButton("Stop", { confirmManual = false; onTurnOffCapture() }, emphasis = Emphasis.Text) },
            dismissButton = { LedgerButton("Cancel", { confirmManual = false }, emphasis = Emphasis.Quiet) },
        )
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

/** Collects a section's rows so [Group] can put separators between them. */
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
        GroupedSection {
            rows.forEachIndexed { index, content ->
                if (index > 0) RowDivider()
                content()
            }
        }
    }
}

@Composable
private fun Setting(title: String, value: String, danger: Boolean = false, chevron: Boolean = false, onClick: (() -> Unit)?) {
    val c = LedgerTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .defaultMinSize(minHeight = Size.Row)
            .padding(horizontal = Space.L, vertical = Space.M),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = if (danger) c.over else c.text)
            Text(value, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        }
        if (chevron) {
            Spacer(Modifier.width(Space.S))
            Chevron()
        }
    }
}

/** A row that is an on/off choice: the whole row toggles, and the switch shows the state. */
@Composable
private fun SwitchSetting(title: String, value: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = LedgerTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .defaultMinSize(minHeight = Size.Row)
            .padding(horizontal = Space.L, vertical = Space.M),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = c.text)
            Text(value, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        }
        Spacer(Modifier.width(Space.M))
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = c.on,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = c.fill,
                uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}
