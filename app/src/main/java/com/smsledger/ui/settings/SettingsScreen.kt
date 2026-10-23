package com.smsledger.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Rule
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.outlined.SyncAlt
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.TextSnippet
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.smsledger.core.money.formatRupees
import com.smsledger.ui.components.SectionHeader
import com.smsledger.ui.demo.Scenario
import com.smsledger.ui.theme.Space

data class SettingsState(
    val limitPaise: Long,
    val automatic: Boolean,
    val limitAlerts: Boolean,
    val lentReminder: Boolean,
    val scenario: Scenario,
    val darkOverride: Boolean?,
)

/** Settings (flows.md §11), plus a design-preview section for reviewing states. */
@Composable
fun SettingsScreen(
    state: SettingsState,
    onEditLimit: () -> Unit,
    onToggleMode: () -> Unit,
    onLimitAlerts: (Boolean) -> Unit,
    onLentReminder: (Boolean) -> Unit,
    onOpenOwed: () -> Unit,
    onScenario: (Scenario) -> Unit,
    onDarkOverride: (Boolean?) -> Unit,
    onReplayOnboarding: () -> Unit,
    onNotBuilt: (String) -> Unit,
    contentPadding: PaddingValues,
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item {
            Text(
                "Settings",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(start = Space.Gutter, end = Space.Gutter, top = Space.XL),
            )
        }
        item { SectionHeader("Spending") }
        item { SettingRow(Icons.Outlined.Speed, "Daily limit", formatRupees(state.limitPaise), onClick = onEditLimit) }
        item { SettingRow(Icons.Outlined.Category, "Categories", "Choose which count toward the daily limit") { onNotBuilt("Categories") } }
        item { SettingRow(Icons.Outlined.Handshake, "Owed to you", "Money you've lent", onClick = onOpenOwed) }

        item { SectionHeader("Messages") }
        item {
            SettingRow(
                Icons.Outlined.SyncAlt, "Mode",
                if (state.automatic) "Automatic: reads bank SMS" else "Manual: you add expenses",
                onClick = onToggleMode,
            )
        }
        item { SettingRow(Icons.Outlined.AccountBalance, "Accounts & wallets", "4 found · 3 yours") { onNotBuilt("Accounts") } }
        item { SettingRow(Icons.Outlined.TextSnippet, "Learned messages", "2 message formats you taught") { onNotBuilt("Learned messages") } }
        item { SettingRow(Icons.Outlined.Rule, "Payee rules", "MYNTRA → Shopping, and 5 more") { onNotBuilt("Payee rules") } }

        item { SectionHeader("Alerts") }
        item {
            SettingRow(
                Icons.Outlined.Notifications, "Limit alerts", "At 80% and 100%, once a day",
                trailing = { Switch(checked = state.limitAlerts, onCheckedChange = onLimitAlerts) },
            ) { onLimitAlerts(!state.limitAlerts) }
        }
        item {
            SettingRow(
                Icons.Outlined.Handshake, "Lent reminder", "On the 1st of each month",
                trailing = { Switch(checked = state.lentReminder, onCheckedChange = onLentReminder) },
            ) { onLentReminder(!state.lentReminder) }
        }
        item { SettingRow(Icons.Outlined.BatteryAlert, "Battery optimisation", "Keep totals live in the background") { onNotBuilt("Battery") } }

        item { SectionHeader("Privacy") }
        item { SettingRow(Icons.Outlined.Policy, "Privacy policy", "Everything stays on this phone") { onNotBuilt("Privacy policy") } }
        item {
            SettingRow(
                Icons.Outlined.DeleteForever, "Delete all data", "Erase the ledger and start over",
                tint = MaterialTheme.colorScheme.error,
            ) { onNotBuilt("Delete all data") }
        }

        item { SectionHeader("Design preview") }
        item { PreviewChips("Today state", Scenario.entries.map { it.label to (it == state.scenario) }) { onScenario(Scenario.entries[it]) } }
        item {
            val options = listOf("System" to null, "Light" to false, "Dark" to true)
            PreviewChips("Theme", options.map { it.first to (it.second == state.darkOverride) }) { onDarkOverride(options[it].second) }
        }
        item { SettingRow(Icons.Outlined.Replay, "Replay onboarding", "See the first-launch flow again", onClick = onReplayOnboarding) }
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    value: String,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title, color = if (tint == MaterialTheme.colorScheme.error) tint else Color.Unspecified) },
        supportingContent = { Text(value) },
        leadingContent = { Icon(icon, contentDescription = null, tint = tint) },
        trailingContent = trailing,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = Space.XS),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PreviewChips(label: String, options: List<Pair<String, Boolean>>, onSelect: (Int) -> Unit) {
    ListItem(
        leadingContent = { Icon(Icons.Outlined.Palette, contentDescription = null) },
        headlineContent = { Text(label) },
        supportingContent = {
            // FlowRow, not LazyRow: ListItem measures intrinsics, which lazy layouts can't provide.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.S)) {
                options.forEachIndexed { i, (label, selected) ->
                    FilterChip(selected = selected, onClick = { onSelect(i) }, label = { Text(label) }, shape = MaterialTheme.shapes.small)
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.padding(horizontal = Space.XS),
    )
}
