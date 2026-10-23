package com.smsledger.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.smsledger.ui.demo.DemoStore
import com.smsledger.ui.demo.SampleData
import com.smsledger.ui.onboarding.OnboardingFlow
import kotlinx.coroutines.launch

enum class Tab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    Today("Today", Icons.Outlined.Today, Icons.Filled.Today),
    History("History", Icons.AutoMirrored.Outlined.ReceiptLong, Icons.AutoMirrored.Filled.ReceiptLong),
    Settings("Settings", Icons.Outlined.Settings, Icons.Filled.Settings),
}

enum class Overlay { None, Needs, Owed }

/** Shows a message with an optional Undo; one snackbar at a time. */
class Notifier(private val host: SnackbarHostState, private val launch: (suspend () -> Unit) -> Unit) {
    fun show(message: String, undo: (() -> Unit)? = null) = launch {
        host.currentSnackbarData?.dismiss()
        val result = host.showSnackbar(message, actionLabel = if (undo != null) "Undo" else null, duration = SnackbarDuration.Short)
        if (result == SnackbarResult.ActionPerformed) undo?.invoke()
    }
}

/** App map (flows.md §1): onboarding once, then three tabs with Needs you / Owed as full screens. */
@Composable
fun SmsLedgerApp(store: DemoStore) {
    if (!store.onboarded) {
        OnboardingFlow(
            limitPaise = store.limitPaise,
            onLimitChange = { store.limitPaise = it },
            accounts = SampleData.accounts,
            onFinish = { automatic -> store.automatic = automatic; store.onboarded = true },
        )
        return
    }

    var tab by rememberSaveable { mutableStateOf(Tab.Today) }
    var overlay by rememberSaveable { mutableStateOf(Overlay.None) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val notifier = remember { Notifier(snackbar) { block -> scope.launch { block() } } }
    val sheets = remember { SheetState() }

    BackHandler(enabled = overlay != Overlay.None) { overlay = Overlay.None }
    BackHandler(enabled = overlay == Overlay.None && tab != Tab.Today) { tab = Tab.Today }

    Box(Modifier.fillMaxSize()) {
        when (overlay) {
            Overlay.Needs -> NeedsRoute(store, notifier, sheets, onBack = { overlay = Overlay.None })
            Overlay.Owed -> OwedRoute(store, notifier, onBack = { overlay = Overlay.None })
            Overlay.None -> Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Tab.entries.forEach { item ->
                            NavigationBarItem(
                                selected = tab == item,
                                onClick = { tab = item },
                                label = { Text(item.label) },
                                icon = {
                                    BadgedBox(badge = {
                                        if (item == Tab.Today && store.needs.isNotEmpty()) Badge(containerColor = MaterialTheme.colorScheme.tertiary) { Text("${store.needs.size}") }
                                    }) { Icon(if (tab == item) item.selectedIcon else item.icon, contentDescription = null) }
                                },
                            )
                        }
                    }
                },
                floatingActionButton = {
                    if (tab == Tab.Today) {
                        FloatingActionButton(
                            onClick = { sheets.adding = true },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ) { Icon(Icons.Outlined.Add, contentDescription = "Add expense") }
                    }
                },
            ) { padding ->
                when (tab) {
                    Tab.Today -> TodayRoute(store, notifier, sheets, padding, onOpen = { overlay = it }, onHistory = { tab = Tab.History })
                    Tab.History -> HistoryRoute(store, sheets, padding)
                    Tab.Settings -> SettingsRoute(store, notifier, sheets, padding, onOpenOwed = { overlay = Overlay.Owed })
                }
            }
        }
        SnackbarHost(
            snackbar,
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = if (overlay == Overlay.None) 80.dp else 0.dp),
        )
    }

    AppSheets(store, notifier, sheets)
}
