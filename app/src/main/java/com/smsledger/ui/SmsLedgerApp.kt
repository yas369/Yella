package com.smsledger.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import com.smsledger.ui.theme.LedgerMotion
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.smsledger.ui.theme.LedgerTheme
import com.smsledger.ui.theme.Radius
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import com.smsledger.core.limit.limitStatus
import com.smsledger.data.LedgerStore
import com.smsledger.ui.onboarding.CaptureSetupFlow
import com.smsledger.ui.onboarding.OnboardingFlow
import com.smsledger.ui.settings.AccountsScreen
import com.smsledger.ui.settings.TemplatesScreen
import com.smsledger.widget.WidgetSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class Tab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    Today("Today", Icons.Outlined.Today, Icons.Filled.Today),
    History("History", Icons.AutoMirrored.Outlined.ReceiptLong, Icons.AutoMirrored.Filled.ReceiptLong),
    Settings("Settings", Icons.Outlined.Settings, Icons.Filled.Settings),
}

enum class Overlay { None, Needs, Owed, Capture, Accounts, Templates }

/** How long Undo stays on screen after a decision or edit (design.md §6). */
private const val UNDO_MS = 5_000L

/** Shows a message with an optional Undo for 5 seconds; one snackbar at a time. */
class Notifier(private val host: SnackbarHostState, private val scope: CoroutineScope) {
    private var timer: Job? = null

    fun show(message: String, undo: (() -> Unit)? = null) {
        host.currentSnackbarData?.dismiss()
        timer?.cancel()
        scope.launch {
            val result = host.showSnackbar(message, actionLabel = if (undo != null) "Undo" else null, duration = SnackbarDuration.Indefinite)
            if (result == SnackbarResult.ActionPerformed) undo?.invoke()
        }
        timer = scope.launch {
            delay(UNDO_MS)
            host.currentSnackbarData?.dismiss()
        }
    }
}

/** App map (flows.md §2): onboarding once, then a 3-item navigation bar; Needs you / Owed as full screens. */
@Composable
fun SmsLedgerApp(store: LedgerStore) {
    if (!store.onboarded) {
        OnboardingFlow(store, onFinish = store::finishOnboarding)
        return
    }

    var tab by rememberSaveable { mutableStateOf(Tab.Today) }
    var overlay by rememberSaveable { mutableStateOf(Overlay.None) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val notifier = remember { Notifier(snackbar, scope) }
    val sheets = remember { SheetState() }
    val todayList = rememberLazyListState()
    val context = LocalContext.current

    // Launcher shortcut "Add expense" (and the widget's +) open the Add sheet directly.
    LaunchedEffect(store.pendingAdd) {
        if (store.pendingAdd) {
            overlay = Overlay.None
            tab = Tab.Today
            sheets.adding = true
            store.pendingAdd = false
        }
    }
    // Keep the home-screen widget in step with what Today shows.
    val spent = store.spentTodayPaise
    LaunchedEffect(spent, store.limitPaise) {
        WidgetSnapshot.publish(context, limitStatus(spent, store.limitPaise), spent, store.limitPaise)
    }

    // A new account confirmed as mine: read its past messages now (§4).
    LaunchedEffect(store.rescanFromMillis) {
        if (store.rescanFromMillis != null) (context.applicationContext as com.smsledger.SmsLedgerApplication).catchUp()
    }

    BackHandler(enabled = overlay != Overlay.None) { overlay = Overlay.None }
    BackHandler(enabled = overlay == Overlay.None && tab != Tab.Today) { tab = Tab.Today }

    // Full screens slide in over the tabs (200ms, standard easing) and back out on Back.
    AnimatedContent(
        targetState = overlay,
        transitionSpec = {
            val forward = targetState != Overlay.None
            (slideInHorizontally(tween(LedgerMotion.DEFAULT, easing = FastOutSlowInEasing)) { if (forward) it / 4 else -it / 4 } +
                fadeIn(tween(LedgerMotion.DEFAULT))) togetherWith fadeOut(tween(LedgerMotion.FAST))
        },
        label = "overlay",
    ) { shown -> when (shown) {
        Overlay.Capture -> CaptureSetupFlow(store, onFinished = { overlay = Overlay.None })
        Overlay.Accounts -> Box(Modifier.fillMaxSize()) {
            AccountsScreen(store.accounts, onBack = { overlay = Overlay.None }) { account, mine -> store.act(notifier) { setAccountMine(account, mine) } }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
        }
        Overlay.Templates -> Box(Modifier.fillMaxSize()) {
            TemplatesScreen(store.templates.values.toList(), onBack = { overlay = Overlay.None }) { key -> store.act(notifier) { forgetTemplate(key) } }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
        }
        Overlay.Needs, Overlay.Owed -> Box(Modifier.fillMaxSize()) {
            if (shown == Overlay.Needs) {
                NeedsRoute(store, notifier, sheets, onBack = { overlay = Overlay.None })
            } else {
                OwedRoute(store, notifier, onBack = { overlay = Overlay.None })
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
        }
        Overlay.None -> Scaffold(
            containerColor = LedgerTheme.colors.bg,
            // Screens own their top insets.
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = { LedgerNavBar(tab) { tab = it } },
            floatingActionButton = {
                if (tab == Tab.Today) {
                    // The screen's one filled action: add a transaction by hand.
                    FloatingActionButton(
                        onClick = { sheets.adding = true },
                        shape = Radius.Card,
                        containerColor = LedgerTheme.colors.accent,
                        contentColor = LedgerTheme.colors.onAccent,
                        elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
                    ) { Icon(Icons.Outlined.Add, contentDescription = "Add transaction") }
                }
            },
        ) { padding ->
            when (tab) {
                Tab.Today -> TodayRoute(store, sheets, todayList, padding, onOpen = { overlay = it }, onHistory = { tab = Tab.History })
                Tab.History -> HistoryRoute(store, sheets, padding)
                Tab.Settings -> SettingsRoute(store, notifier, sheets, padding, onOpen = { overlay = it })
            }
        }
    } }

    AppSheets(store, notifier, sheets)
}

/** Bottom navigation: `surface` with a divider on top, selected tab in accent, no pill indicator. */
@Composable
private fun LedgerNavBar(selected: Tab, onSelect: (Tab) -> Unit) {
    val c = LedgerTheme.colors
    Column {
        HorizontalDivider(thickness = 1.dp, color = c.divider)
        NavigationBar(containerColor = c.surface, tonalElevation = 0.dp) {
            Tab.entries.forEach { item ->
                NavigationBarItem(
                    selected = selected == item,
                    onClick = { onSelect(item) },
                    label = { Text(item.label, style = MaterialTheme.typography.bodySmall) },
                    icon = { Icon(if (selected == item) item.selectedIcon else item.icon, contentDescription = null) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = c.accent,
                        selectedTextColor = c.accent,
                        indicatorColor = Color.Transparent,
                        unselectedIconColor = c.textSecondary,
                        unselectedTextColor = c.textSecondary,
                    ),
                )
            }
        }
    }
}
