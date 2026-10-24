package com.smsledger.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListState
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
import com.smsledger.BuildConfig
import com.smsledger.data.LedgerStore
import com.smsledger.sms.SmsAccess
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.remember
import com.smsledger.notify.LedgerNotifications
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
    store: LedgerStore,
    sheets: SheetState,
    listState: LazyListState,
    padding: PaddingValues,
    onOpen: (Overlay) -> Unit,
    onHistory: () -> Unit,
) {
    val spent = store.spentTodayPaise
    TodayScreen(
        state = TodayState(
            status = limitStatus(spent, store.limitPaise),
            spentPaise = spent,
            limitPaise = store.limitPaise,
            recent = store.transactions,
            needsCount = store.needs.size,
            monthSpentPaise = store.monthSpentPaise,
            // Only the sms build can turn capture on, so only it offers the row.
            captureOff = BuildConfig.SMS_CAPTURE && (!store.automatic || !SmsAccess.granted(LocalContext.current)),
        ),
        categoryOf = store::category,
        onOpenNeeds = { onOpen(Overlay.Needs) },
        onOpenHistory = onHistory,
        onTxClick = { sheets.editingTxId = it.id },
        listState = listState,
        contentPadding = padding,
        onTurnOnCapture = { onOpen(Overlay.Capture) },
    )
}

@Composable
internal fun HistoryRoute(store: LedgerStore, sheets: SheetState, padding: PaddingValues) {
    HistoryScreen(store.transactions, store.categories, onTxClick = { sheets.editingTxId = it.id }, contentPadding = padding)
}

@Composable
internal fun SettingsRoute(store: LedgerStore, notifier: Notifier, sheets: SheetState, padding: PaddingValues, onOpen: (Overlay) -> Unit) {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(LedgerNotifications.permitted(context)) }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> allowed = granted }
    // Turning an alert on is the moment to ask for notifications (Android 13+), never before.
    fun enable(turnOn: Boolean, set: (Boolean) -> Unit) {
        set(turnOn)
        if (turnOn && !allowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    SettingsScreen(
        state = SettingsState(
            limitPaise = store.limitPaise,
            darkOverride = store.darkOverride,
            automatic = store.automatic && SmsAccess.granted(context),
            smsBuild = BuildConfig.SMS_CAPTURE,
            accountCount = store.accounts.size,
            templateCount = store.templates.size,
            limitAlerts = store.limitAlerts,
            lentReminder = store.lentReminder,
            notificationsAllowed = allowed,
        ),
        onEditLimit = { sheets.editingLimit = true },
        onOpenOwed = { onOpen(Overlay.Owed) },
        onDarkOverride = store::changeTheme,
        onAddWidget = { if (!requestPinWidget(context)) notifier.show("Your launcher can't add widgets this way. Long-press the home screen → Widgets.") },
        onDeleteAll = store::deleteAll,
        contentPadding = padding,
        onTurnOnCapture = { onOpen(Overlay.Capture) },
        onTurnOffCapture = store::turnOffAutomatic,
        onOpenAccounts = { onOpen(Overlay.Accounts) },
        onOpenTemplates = { onOpen(Overlay.Templates) },
        onLimitAlerts = { enable(it, store::changeLimitAlerts) },
        onLentReminder = { enable(it, store::changeLentReminder) },
    )
}

/** Runs a store action and offers Undo, which restores everything the action changed. */
internal fun LedgerStore.act(notifier: Notifier, action: LedgerStore.() -> String) {
    val before = snapshot()
    val message = action()
    if (message.isNotEmpty()) notifier.show(message) { restore(before) }
}

@Composable
internal fun NeedsRoute(store: LedgerStore, notifier: Notifier, sheets: SheetState, onBack: () -> Unit) {
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
            override fun newAccount(item: NeedsItem.NewAccount, mine: Boolean) = store.act(notifier) { answerNewAccount(item, mine) }
        },
        untaggedCount = store::untaggedCount,
    )
}

@Composable
internal fun OwedRoute(store: LedgerStore, notifier: Notifier, onBack: () -> Unit) {
    OwedScreen(
        debtors = store.debtors,
        onBack = onBack,
        onRepaid = { debtor -> store.act(notifier) { markRepaid(debtor) } },
        onWriteOff = { debtor -> store.act(notifier) { writeOff(debtor) } },
    )
}

@Composable
internal fun AppSheets(store: LedgerStore, notifier: Notifier, sheets: SheetState) {
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
            store.changeLimit(paise)
            sheets.editingLimit = false
            notifier.show("Daily limit is now ${formatRupees(paise)}") { store.changeLimit(previous) }
        }
    }
    store.transactions.firstOrNull { it.id == sheets.editingTxId }?.let { tx ->
        fun close(action: LedgerStore.() -> String) {
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
            onLearned = { amount, moneyIn, allLikeThis ->
                sheets.teachingId = null
                store.act(notifier) { learn(item, amount, moneyIn, allLikeThis) }
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
