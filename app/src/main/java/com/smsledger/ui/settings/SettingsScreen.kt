package com.smsledger.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material.icons.outlined.Colorize
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Rule
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.SyncAlt
import androidx.compose.material.icons.automirrored.outlined.TextSnippet
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.smsledger.core.money.formatRupees
import com.smsledger.ui.components.ConnectedChoice
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
    val dynamicColor: Boolean,
    val supportsDynamicColor: Boolean,
    val showDesignPreview: Boolean,
)

/** Settings (flows.md §13): large flexible app bar, grouped sections like Android's own Settings. */
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
    onDynamicColor: (Boolean) -> Unit,
    onReplayOnboarding: () -> Unit,
    onAddWidget: () -> Unit,
    onNotBuilt: (String) -> Unit,
    contentPadding: PaddingValues,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { LargeFlexibleTopAppBar(title = { Text("Settings") }, scrollBehavior = scrollBehavior) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { inner ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = inner.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding() + Space.XL),
        ) {
            item {
                Group("Spending") {
                    row { Setting(Icons.Outlined.Speed, "Daily limit", formatRupees(state.limitPaise), onClick = onEditLimit) }
                    row { Setting(Icons.Outlined.Category, "Categories", "Choose which count toward the daily limit") { onNotBuilt("Categories") } }
                    row { Setting(Icons.Outlined.Handshake, "Owed to you", "Money you've lent", onClick = onOpenOwed) }
                }
            }
            item {
                Group("Messages") {
                    row {
                        Setting(Icons.Outlined.SyncAlt, "Mode", if (state.automatic) "Automatic: reads bank SMS" else "Manual: you add expenses", onClick = onToggleMode)
                    }
                    row { Setting(Icons.Outlined.AccountBalance, "Accounts & wallets", "4 found · 3 yours") { onNotBuilt("Accounts") } }
                    row { Setting(Icons.AutoMirrored.Outlined.TextSnippet, "Learned messages", "2 message formats you taught") { onNotBuilt("Learned messages") } }
                    row { Setting(Icons.Outlined.Rule, "Payee rules", "MYNTRA → Shopping, and 5 more") { onNotBuilt("Payee rules") } }
                }
            }
            item {
                Group("Alerts") {
                    row {
                        Setting(
                            Icons.Outlined.Notifications, "Limit alerts", "At 80% and 100%, once a day",
                            trailing = { Switch(checked = state.limitAlerts, onCheckedChange = onLimitAlerts) },
                        ) { onLimitAlerts(!state.limitAlerts) }
                    }
                    row {
                        Setting(
                            Icons.Outlined.Handshake, "Lent reminder", "On the 1st of each month",
                            trailing = { Switch(checked = state.lentReminder, onCheckedChange = onLentReminder) },
                        ) { onLentReminder(!state.lentReminder) }
                    }
                    row { Setting(Icons.Outlined.BatteryAlert, "Battery optimisation", "Keep totals live in the background") { onNotBuilt("Battery") } }
                }
            }
            item {
                Group("Home screen") {
                    row { Setting(Icons.Outlined.Widgets, "Add widget to home screen", "See what's left today without opening the app", onClick = onAddWidget) }
                }
            }
            item {
                Group("Appearance") {
                    if (state.supportsDynamicColor) {
                        row {
                            Setting(
                                Icons.Outlined.Colorize, "Dynamic colour", "Use colours from your wallpaper",
                                trailing = { Switch(checked = state.dynamicColor, onCheckedChange = onDynamicColor) },
                            ) { onDynamicColor(!state.dynamicColor) }
                        }
                    }
                    row {
                        val options = listOf(null, false, true)
                        ListItem(
                            leadingContent = { Icon(Icons.Outlined.DarkMode, contentDescription = null) },
                            headlineContent = { Text("Theme") },
                            supportingContent = {
                                ConnectedChoice(
                                    options = listOf("System", "Light", "Dark"),
                                    selectedIndex = options.indexOf(state.darkOverride),
                                    onSelect = { onDarkOverride(options[it]) },
                                    modifier = Modifier.padding(top = Space.S),
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                }
            }
            item {
                Group("Privacy") {
                    row { Setting(Icons.Outlined.Policy, "Privacy policy", "Everything stays on this phone") { onNotBuilt("Privacy policy") } }
                    row {
                        Setting(
                            Icons.Outlined.DeleteForever, "Delete all data", "Erase the ledger and start over",
                            tint = MaterialTheme.colorScheme.error,
                        ) { onNotBuilt("Delete all data") }
                    }
                }
            }
            if (state.showDesignPreview) {
                item {
                    Group("Design preview · debug only") {
                        row {
                            ListItem(
                                leadingContent = { Icon(Icons.Outlined.Palette, contentDescription = null) },
                                headlineContent = { Text("Today state") },
                                // FlowRow, not LazyRow: ListItem measures intrinsics, which lazy layouts can't provide.
                                supportingContent = {
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.S)) {
                                        Scenario.entries.forEach { s ->
                                            FilterChip(selected = s == state.scenario, onClick = { onScenario(s) }, label = { Text(s.label) })
                                        }
                                    }
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            )
                        }
                        row { Setting(Icons.Outlined.Replay, "Replay onboarding", "See the first-launch flow again", onClick = onReplayOnboarding) }
                    }
                }
            }
        }
    }
}

/** Collects a section's rows so [Group] can give the first and last ones rounded outer corners. */
private class GroupScope {
    val rows = mutableListOf<@Composable () -> Unit>()
    fun row(content: @Composable () -> Unit) {
        rows += content
    }
}

/** A titled section of rows in rounded containers separated by small gaps (Android 16 Settings style). */
@Composable
private fun Group(title: String, build: GroupScope.() -> Unit) {
    val rows = GroupScope().apply(build).rows
    Column {
        SectionHeader(title)
        Column(Modifier.padding(horizontal = Space.Gutter), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            rows.forEachIndexed { index, content ->
                val outer = 24.dp
                val inner = 4.dp
                val shape = RoundedCornerShape(
                    topStart = if (index == 0) outer else inner,
                    topEnd = if (index == 0) outer else inner,
                    bottomStart = if (index == rows.lastIndex) outer else inner,
                    bottomEnd = if (index == rows.lastIndex) outer else inner,
                )
                Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = shape) { content() }
            }
        }
    }
}

@Composable
private fun Setting(
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
        modifier = Modifier.clickable(onClick = onClick),
    )
}
