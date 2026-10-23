package com.smsledger.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListState
import com.smsledger.BuildConfig
import com.smsledger.ui.theme.supportsDynamicColor
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.smsledger.widget.LedgerWidgetReceiver
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.smsledger.core.limit.limitStatus
import com.smsledger.core.money.formatRupees
import com.smsledger.ui.demo.DemoStore
import com.smsledger.ui.history.HistoryScreen
import com.smsledger.ui.model.Category
import com.smsledger.ui.model.NeedsItem
import com.smsledger.ui.needs.NeedsActions
import com.smsledger.ui.needs.NeedsYouScreen
import com.smsledger.ui.needs.TeachSheet
import com.smsledger.ui.owed.OwedScreen
import com.smsledger.ui.settings.SettingsScreen
import com.smsledger.ui.settings.SettingsState
import com.smsledger.ui.sheets.AddExpenseSheet
import com.smsledger.ui.sheets.EditSheet
import com.smsledger.ui.sheets.LimitSheet
import com.smsledger.ui.today.TodayScreen
import com.smsledger.ui.today.TodayState

/** Which sheet is open. Sheets float over every route, so their state lives above them. */
@Stable
class SheetState {
    var adding by mutableStateOf(false)
    var editingLimit by mutableStateOf(false)
    var editingTxId by mutableStateOf<Long?>(null)
    var teachingId by mutableStateOf<Long?>(null)
}

@Composable
internal fun TodayRoute(
    store: DemoStore,
    notifier: Notifier,
    sheets: SheetState,
    listState: LazyListState,
    padding: PaddingValues,
    onOpen: (Overlay) -> Unit,
    onHistory: () -> Unit,
) {
    val spent = store.spentTodayPaise
    val status = limitStatus(spent, store.limitPaise)
    TodayScreen(
        state = TodayState(
            status = status,
            spentPaise = spent,
            limitPaise = store.limitPaise,
            transactions = store.todayTransactions,
            needsCount = store.needs.size,
            needsSummary = needsSummary(store.needs),
            owedPaise = store.owedPaise,
            owedPeople = store.debtors.size,
            monthSpentPaise = store.monthSpentPaise,
            showAlertsCard = store.alertsCardVisible && !store.limitAlerts && status.fraction >= 0.5f,
            manualMode = !store.automatic,
        ),
        categoryOf = store::category,
        onOpenNeeds = { onOpen(Overlay.Needs) },
        onOpenOwed = { onOpen(Overlay.Owed) },
        onOpenHistory = onHistory,
        onAlertsTurnOn = {
            store.limitAlerts = true
            notifier.show("Alerts on at 80% and 100%")
        },
        onAlertsDismiss = {
            store.alertsCardVisible = false
            notifier.show("We'll ask again in a week") { store.alertsCardVisible = true }
        },
        onTxClick = { sheets.editingTxId = it.id },
        listState = listState,
        contentPadding = padding,
    )
}

private fun needsSummary(items: List<NeedsItem>): String {
    val unread = items.count { it is NeedsItem.Unreadable }
    val tag = items.count { it is NeedsItem.NewPayee || it is NeedsItem.Bulk }
    val other = items.size - unread - tag
    return listOfNotNull(
        unread.takeIf { it > 0 }?.let { "$it couldn't read" },
        tag.takeIf { it > 0 }?.let { "$it to tag" },
        other.takeIf { it > 0 }?.let { "$it to check" },
    ).joinToString(" · ")
}

@Composable
internal fun HistoryRoute(store: DemoStore, sheets: SheetState, padding: PaddingValues) {
    HistoryScreen(store.transactions, store.categories, store.monthSpentPaise, onTxClick = { sheets.editingTxId = it.id }, contentPadding = padding)
}

@Composable
internal fun SettingsRoute(store: DemoStore, notifier: Notifier, sheets: SheetState, padding: PaddingValues, onOpenOwed: () -> Unit) {
    val context = LocalContext.current
    SettingsScreen(
        state = SettingsState(
            limitPaise = store.limitPaise,
            automatic = store.automatic,
            limitAlerts = store.limitAlerts,
            lentReminder = store.lentReminder,
            scenario = store.scenario,
            darkOverride = store.darkOverride,
            dynamicColor = store.dynamicColor,
            supportsDynamicColor = supportsDynamicColor,
            showDesignPreview = BuildConfig.DEBUG,
        ),
        onEditLimit = { sheets.editingLimit = true },
        onToggleMode = {
            store.automatic = !store.automatic
            notifier.show(if (store.automatic) "Automatic mode: reading bank SMS" else "Manual mode: captured transactions stay")
        },
        onLimitAlerts = { store.limitAlerts = it },
        onLentReminder = { store.lentReminder = it },
        onOpenOwed = onOpenOwed,
        onScenario = store::applyScenario,
        onDarkOverride = { store.darkOverride = it },
        onDynamicColor = { store.dynamicColor = it },
        onReplayOnboarding = { store.onboarded = false },
        onAddWidget = { if (!requestPinWidget(context)) notifier.show("Your launcher can't add widgets this way. Long-press the home screen → Widgets.") },
        onNotBuilt = { notifier.show("$it isn't in this preview yet") },
        contentPadding = padding,
    )
}

/** Runs a store action and offers Undo, which restores everything the action changed. */
internal fun DemoStore.act(notifier: Notifier, action: DemoStore.() -> String) {
    val before = snapshot()
    val message = action()
    if (message.isNotEmpty()) notifier.show(message) { restore(before) }
}

@Composable
internal fun NeedsRoute(store: DemoStore, notifier: Notifier, sheets: SheetState, onBack: () -> Unit) {
    NeedsYouScreen(
        items = store.needs,
        categories = store.categories,
        onBack = onBack,
        actions = object : NeedsActions {
            override fun teach(item: NeedsItem.Unreadable) { sheets.teachingId = item.id }
            override fun notATransaction(item: NeedsItem) = store.act(notifier) { notATransaction(item) }
            override fun pickAccount(item: NeedsItem.WhichAccount, account: String?) = store.act(notifier) { pickAccount(item, account) }
            override fun transfer(item: NeedsItem.PossibleTransfer, isTransfer: Boolean) = store.act(notifier) { resolveTransfer(item, isTransfer) }
            override fun tag(item: NeedsItem, category: Category) = store.act(notifier) { tagPayee(item, category.id) }
            override fun lent(item: NeedsItem.NewPayee) = store.act(notifier) { lentFromNeeds(item) }
            override fun skip(item: NeedsItem) = store.act(notifier) { skip(item) }
        },
        untaggedCount = store::untaggedCount,
    )
}

@Composable
internal fun OwedRoute(store: DemoStore, notifier: Notifier, onBack: () -> Unit) {
    OwedScreen(
        debtors = store.debtors,
        onBack = onBack,
        onRepaid = { debtor -> store.act(notifier) { markRepaid(debtor) } },
        onWriteOff = { debtor -> store.act(notifier) { writeOff(debtor) } },
    )
}

@Composable
internal fun AppSheets(store: DemoStore, notifier: Notifier, sheets: SheetState) {
    if (sheets.adding) {
        AddExpenseSheet(
            categories = store.categories,
            onDismiss = { sheets.adding = false },
            onSave = { paise, kind, categoryId, payee ->
                sheets.adding = false
                store.act(notifier) { addManual(paise, kind, categoryId, payee) }
            },
        )
    }
    if (sheets.editingLimit) {
        LimitSheet(store.limitPaise, onDismiss = { sheets.editingLimit = false }) { paise ->
            val previous = store.limitPaise
            store.limitPaise = paise
            sheets.editingLimit = false
            notifier.show("Daily limit is now ${formatRupees(paise)}") { store.limitPaise = previous }
        }
    }
    store.transactions.firstOrNull { it.id == sheets.editingTxId }?.let { tx ->
        fun close(action: DemoStore.() -> String) {
            sheets.editingTxId = null
            store.act(notifier, action)
        }
        EditSheet(
            tx = tx,
            categories = store.categories,
            similarCount = store.similarCount(tx),
            onDismiss = { sheets.editingTxId = null },
            onKind = { kind -> close { setKind(tx, kind) } },
            onCategory = { categoryId, all -> close { setCategory(tx, categoryId, all) } },
            onExcluded = { excluded -> close { setExcluded(tx, excluded) } },
            onDetails = { amount, kind, payee -> close { editDetails(tx, amount, kind, payee) } },
        )
    }
    store.needs.filterIsInstance<NeedsItem.Unreadable>().firstOrNull { it.id == sheets.teachingId }?.let { item ->
        TeachSheet(
            item = item,
            onDismiss = { sheets.teachingId = null },
            onLearned = { amount, moneyIn ->
                sheets.teachingId = null
                store.act(notifier) { learn(item, amount, moneyIn) }
            },
            onIgnoreLikeThis = {
                sheets.teachingId = null
                store.act(notifier) { ignoreLikeThis(item) }
            },
        )
    }
}

/** Asks the launcher to pin the home-screen widget (Android 8+). Returns false if the launcher can't. */
private fun requestPinWidget(context: Context): Boolean {
    val manager = AppWidgetManager.getInstance(context)
    if (!manager.isRequestPinAppWidgetSupported) return false
    return manager.requestPinAppWidget(ComponentName(context, LedgerWidgetReceiver::class.java), null, null)
}
